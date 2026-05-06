package com.liaobusi.stockman
import com.liaobusi.stockman5.R

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.liaobusi.stockman5.databinding.FragmentFollowPanelBinding
import com.liaobusi.stockman5.databinding.ItemFollowPanelBinding
import com.liaobusi.stockman5.databinding.LayoutFollowPanelPopupBinding
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.openWeb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FollowPanelFragment : Fragment() {

    private var _binding: FragmentFollowPanelBinding? = null
    private val binding: FragmentFollowPanelBinding get() = _binding!!

    private var refreshJob: Job? = null
    private val orderSpKey = "follow_panel_order_v1"
    private val sortSpKey = "follow_panel_sort_mode"

    private enum class FollowSortMode(val spValue: Int) {
        MANUAL(0),
        BY_CHG_DESC(1),
        BY_COLOR_THEN_CHG(2),
        ;

        companion object {
            fun fromSp(v: Int): FollowSortMode =
                FollowSortMode.entries.firstOrNull { it.spValue == v } ?: MANUAL
        }
    }

    private fun PanelRow.quoteChg(): Float = stock?.chg ?: bk!!.chg

    /**
     * 自选行数据：附带对应 Follow（含 stickyOnTop、color）
     */
    data class PanelRow(
        val follow: Follow,
        val stock: Stock? = null,
        val bk: BK? = null,
    ) {
        init {
            require((stock != null) xor (bk != null))
        }

        fun panelKey(): String = "${follow.type}:${follow.code}"

        fun openWeb(context: android.content.Context) {
            stock?.openWeb(context) ?: bk!!.openWeb(context)
        }

        companion object {
            fun fromStock(stock: Stock, f: Follow) = PanelRow(follow = f, stock = stock)
            fun fromBk(bkVal: BK, f: Follow) = PanelRow(follow = f, bk = bkVal)
        }
    }

    companion object {
        /** 十色标记（ARGB） */
        val PALETTE_COLORS: IntArray = intArrayOf(
            Color.parseColor("#FFD32F2F"),
            Color.parseColor("#FFFB8C00"),
            Color.parseColor("#FFFBC02D"),
            Color.parseColor("#FF1976D2"),
            Color.parseColor("#FF6A1B9A"),
            Color.parseColor("#FF43A047"),
            Color.parseColor("#FFE91E63"),
            Color.parseColor("#FF00897B"),
            Color.parseColor("#FF795548"),
            Color.parseColor("#FF546E7A"),
        )

        fun circleBg(colorArgb: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorArgb)
                setStroke(4, Color.argb(80, 255, 255, 255))
            }
    }

    fun isShowing(): Boolean = _binding?.root?.visibility == View.VISIBLE

    fun toggle() {
        if (isShowing()) hide() else show()
    }

    fun show() {
        if (_binding == null) return
        if (binding.root.visibility == View.VISIBLE) return
        refreshSortChipUi()
        binding.root.visibility = View.VISIBLE
        binding.container.post {
            val h = binding.container.height.toFloat().takeIf { it > 0 } ?: 0f
            binding.mask.alpha = 0f
            binding.container.translationY = -h
            binding.mask.animate().alpha(1f).setDuration(180).start()
            binding.container.animate().translationY(0f).setDuration(220).start()
        }
        loadOnce()
        startDbRefreshLoop()
    }

    fun hide() {
        if (_binding == null) return
        if (binding.root.visibility != View.VISIBLE) return
        refreshJob?.cancel()
        refreshJob = null
        val h = binding.container.height.toFloat()
        binding.mask.animate().alpha(0f).setDuration(160).start()
        binding.container.animate()
            .translationY(-h)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.container.animate().setListener(null)
                    binding.root.visibility = View.GONE
                }
            })
            .start()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFollowPanelBinding.inflate(inflater, container, false)

        val adapter = FollowAdapter(fragment = this)

        binding.rv.apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            this.adapter = adapter
        }

        binding.sortManualBtn.setOnClickListener { applySortChoice(FollowSortMode.MANUAL) }
        binding.sortChgBtn.setOnClickListener { applySortChoice(FollowSortMode.BY_CHG_DESC) }
        binding.sortColorBtn.setOnClickListener { applySortChoice(FollowSortMode.BY_COLOR_THEN_CHG) }
        refreshSortChipUi()

        binding.mask.setOnClickListener { hide() }
        binding.root.setOnClickListener { hide() }
        binding.container.setOnClickListener { }

        return binding.root
    }

    override fun onDestroyView() {
        refreshJob?.cancel()
        refreshJob = null
        _binding = null
        super.onDestroyView()
    }

    private fun loadOnce() {
        if (_binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { loadFollowList() }
            if (!isAdded || _binding == null) return@launch
            (binding.rv.adapter as? FollowAdapter)?.setData(list)
        }
    }

    /** 面板打开期间定时从本地 Room 读自选与行情快照，不写网络。 */
    private fun startDbRefreshLoop() {

        if (!isTradingTime()) return
        refreshJob?.cancel()
        refreshJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (!isTradingDay()) return@launch
            while (isActive) {
                if (!isShowing()) {
                    delay(8000)
                    continue
                }
                val list = loadFollowList()
                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext
                    (binding.rv.adapter as? FollowAdapter)?.updateQuotes(list)
                }
                delay(1000)
            }
        }
    }

    fun keyOf(type: Int, code: String): String = "${type}:${code}"

    private fun loadFollowList(): List<PanelRow> {
        val follows = Injector.appDatabase.followDao().getFollows()
        val stockCodes = follows.filter { it.type == 1 }.map { it.code }
        val bkCodes = follows.filter { it.type == 2 }.map { it.code }

        val stockMap =
            if (stockCodes.isNotEmpty()) Injector.appDatabase.stockDao().getStockByCodes(stockCodes)
                .associateBy { it.code }
            else emptyMap()

        val bkMap =
            if (bkCodes.isNotEmpty()) Injector.appDatabase.bkDao().getAllBK()
                .filter { it.code in bkCodes }
                .associateBy { it.code }
            else emptyMap()

        val itemsByKey = mutableMapOf<String, PanelRow>()
        for (f in follows) {
            val pk = keyOf(f.type, f.code)
            when (f.type) {
                1 -> stockMap[f.code]?.let { itemsByKey[pk] = PanelRow.fromStock(it, f) }
                2 -> bkMap[f.code]?.let { itemsByKey[pk] = PanelRow.fromBk(it, f) }
            }
        }

        val order = readOrder()
        val result = mutableListOf<PanelRow>()
        val used = mutableSetOf<String>()
        for (k in order) {
            val row = itemsByKey[k] ?: continue
            result.add(row)
            used.add(k)
        }
        for ((k, v) in itemsByKey) {
            if (k !in used) result.add(v)
        }
        return applySortMode(result)
    }

    fun readOrder(): List<String> {
        val raw = Injector.sp.getString(orderSpKey, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.contains(":") && it.length > 3 }
    }

    fun saveOrder(list: List<PanelRow>) {
        val keys = list.map { it.panelKey() }
        Injector.sp.edit().putString(orderSpKey, keys.joinToString(",")).apply()
    }

    private fun readSortMode(): FollowSortMode =
        FollowSortMode.fromSp(Injector.sp.getInt(sortSpKey, FollowSortMode.MANUAL.spValue))

    private fun writeSortMode(mode: FollowSortMode) {
        Injector.sp.edit().putInt(sortSpKey, mode.spValue).apply()
    }

    private fun applySortChoice(mode: FollowSortMode) {
        if (readSortMode() == mode) return
        writeSortMode(mode)
        refreshSortChipUi()
        loadOnce()
    }

    private fun refreshSortChipUi() {
        if (_binding == null) return
        val ctx = binding.root.context
        val idle = ContextCompat.getColor(ctx, R.color.yd_secondary)
        fun styleChip(tv: TextView, selected: Boolean) {
            tv.setBackgroundResource(
                if (selected) R.drawable.bg_follow_sort_seg_selected else R.drawable.bg_follow_sort_seg_idle,
            )
            tv.setTextColor(if (selected) Color.WHITE else idle)
            tv.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }

        val mode = readSortMode()
        styleChip(binding.sortManualBtn, mode == FollowSortMode.MANUAL)
        styleChip(binding.sortChgBtn, mode == FollowSortMode.BY_CHG_DESC)
        styleChip(binding.sortColorBtn, mode == FollowSortMode.BY_COLOR_THEN_CHG)
    }

    private fun applySortMode(manualOrdered: List<PanelRow>): List<PanelRow> {
        return when (readSortMode()) {
            FollowSortMode.MANUAL -> manualOrdered
            FollowSortMode.BY_CHG_DESC ->
                manualOrdered.sortedWith(
                    compareByDescending<PanelRow> { it.quoteChg() }.thenBy { it.panelKey() },
                )

            FollowSortMode.BY_COLOR_THEN_CHG ->
                manualOrdered.sortedWith(
                    compareByDescending<PanelRow> { it.follow.color != 0 }
                        .thenBy { it.follow.color }
                        .thenByDescending { it.quoteChg() }
                        .thenBy { it.panelKey() },
                )
        }
    }

    private fun forceManualSort() {
        writeSortMode(FollowSortMode.MANUAL)
        refreshSortChipUi()
    }

    fun canPersistManualRowOrder(): Boolean = readSortMode() == FollowSortMode.MANUAL

    private inner class FollowAdapter(
        private val fragment: FollowPanelFragment,
    ) : RecyclerView.Adapter<FollowAdapter.VH>() {

        inner class VH(val binding: ItemFollowPanelBinding) : RecyclerView.ViewHolder(binding.root)

        private val data = mutableListOf<PanelRow>()

        fun setData(list: List<PanelRow>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
            if (fragment.canPersistManualRowOrder()) fragment.saveOrder(data.toList())
        }

        fun updateQuotes(latest: List<PanelRow>) {
            data.clear()
            data.addAll(latest)
            notifyDataSetChanged()
            if (fragment.canPersistManualRowOrder()) fragment.saveOrder(data.toList())
        }

        fun pinTop(pos: Int) {
            if (pos !in data.indices) return
            fragment.forceManualSort()
            val item = data.removeAt(pos)
            data.add(0, item)
            notifyItemMoved(pos, 0)
            fragment.saveOrder(data.toList())
        }

        fun pinBottom(pos: Int) {
            if (pos !in data.indices) return
            fragment.forceManualSort()
            val item = data.removeAt(pos)
            data.add(item)
            notifyItemMoved(pos, data.size - 1)
            fragment.saveOrder(data.toList())
        }

        fun applyMarkerColor(adapterPosition: Int, colorArgb: Int) {
            if (adapterPosition !in data.indices) return
            val row = data[adapterPosition]
            fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                Injector.appDatabase.followDao().insertFollow(row.follow.copy(color = colorArgb))
                val list = fragment.loadFollowList()
                withContext(Dispatchers.Main) {
                    if (!fragment.isAdded) return@withContext
                    setData(list)
                }
            }
        }

        fun applyMarkerColor(target: Follow, colorArgb: Int) {
            fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                Injector.appDatabase.followDao().insertFollow(target.copy(color = colorArgb))
                val list = fragment.loadFollowList()
                withContext(Dispatchers.Main) {
                    if (!fragment.isAdded) return@withContext
                    setData(list)
                }
            }
        }

        override fun getItemCount(): Int = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(
                ItemFollowPanelBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
            )
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = data[position]
            val b = holder.binding

            val defaultStripe =
                androidx.core.content.ContextCompat.getColor(
                    fragment.requireContext(),
                    R.color.gray_400
                )

            when {
                row.stock != null -> {
                    val s = row.stock!!
                    b.name.text = s.name
                    b.chgTv.text = "${s.chg}%"
                    b.chgTv.setTextColor(Color.BLACK)
                    if (s.chg > 0) b.chgTv.setTextColor(Color.RED)
                    else if (s.chg < 0) b.chgTv.setTextColor(Color.parseColor("#ff00ad43"))
                }

                row.bk != null -> {
                    val k = row.bk!!
                    b.name.text = k.name
                    b.chgTv.text = "${k.chg}%"
                    b.chgTv.setTextColor(Color.BLACK)
                    if (k.chg > 0) b.chgTv.setTextColor(Color.RED)
                    else if (k.chg < 0) b.chgTv.setTextColor(Color.parseColor("#ff00ad43"))
                }
            }

            if (row.follow.color == 0) {
                b.colorStripe.setBackgroundColor(defaultStripe)
            } else {
                b.colorStripe.setBackgroundColor(row.follow.color)
            }

            b.root.setOnClickListener { row.openWeb(it.context) }

            var ev: MotionEvent? = null
            b.root.setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) ev = motionEvent
                false
            }

            b.root.setOnLongClickListener { anchor ->
                // 刷新线程会频繁 notifyDataSetChanged，导致 adapterPosition 抖动/变 NO_POSITION；
                // 弹窗里所有操作都用本次 bind 的 follow 锁定目标，避免“点了没反应”
                val targetFollow = row.follow
                val pop = LayoutFollowPanelPopupBinding.inflate(LayoutInflater.from(anchor.context))
                val pw = PopupWindow(
                    pop.root,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true,
                )

                // 预先测量，避免 showAsDropDown + 固定 offset 触发系统二次修正导致“跳动”
                pop.root.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                val popupW = pop.root.measuredWidth
                val popupH = pop.root.measuredHeight

                val visibleFrame = Rect()
                anchor.getWindowVisibleDisplayFrame(visibleFrame)

                val anchorLoc = IntArray(2)
                anchor.getLocationOnScreen(anchorLoc)
                val touchX = (ev?.x ?: 0f).toInt()

                var x = anchorLoc[0] + touchX
                x = x.coerceIn(
                    visibleFrame.left,
                    (visibleFrame.right - popupW).coerceAtLeast(visibleFrame.left)
                )

                val yAbove = anchorLoc[1] - popupH
                val yBelow = anchorLoc[1] + anchor.height
                val y = if (yAbove >= visibleFrame.top) yAbove else yBelow

                pop.pinTopBtn.setOnClickListener {
                    pw.dismiss()
                    val p = holder.adapterPosition
                    if (p != RecyclerView.NO_POSITION) pinTop(p)
                }

                pop.pinBottomBtn.setOnClickListener {
                    pw.dismiss()
                    val p = holder.adapterPosition
                    if (p != RecyclerView.NO_POSITION) pinBottom(p)
                }

                pop.markColorBtn.setOnClickListener {
                    pop.colorPaletteRow.visibility =
                        if (pop.colorPaletteRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    // 内容高度变化后保持锚点位置不抖动
                    pop.root.post { if (pw.isShowing) pw.update(x, y, -1, -1) }
                }

                val picks = arrayOf(
                    pop.palettePick1,
                    pop.palettePick2,
                    pop.palettePick3,
                    pop.palettePick4,
                    pop.palettePick5,
                    pop.palettePick6,
                    pop.palettePick7,
                    pop.palettePick8,
                    pop.palettePick9,
                    pop.palettePick10,
                )
                PALETTE_COLORS.forEachIndexed { ix, argb ->
                    picks[ix].background = FollowPanelFragment.circleBg(argb)
                    picks[ix].setOnClickListener {
                        pw.dismiss()
                        applyMarkerColor(targetFollow, PALETTE_COLORS[ix])
                    }
                }

                pop.unfollowBtn.setOnClickListener {
                    val p = holder.adapterPosition
                    val r = data.getOrNull(p) ?: return@setOnClickListener
                    pw.dismiss()
                    fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        Injector.appDatabase.followDao()
                            .deleteFollow(Follow(r.follow.code, r.follow.type))
                        val list = fragment.loadFollowList()
                        withContext(Dispatchers.Main) {
                            if (!fragment.isAdded) return@withContext
                            setData(list)
                        }
                    }
                }

                pw.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, x, y)
                true
            }
        }

    }

}
