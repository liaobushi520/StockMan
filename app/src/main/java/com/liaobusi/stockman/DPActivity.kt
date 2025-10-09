package com.liaobusi.stockman

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.liaobusi.stockman.FPActivity
import com.liaobusi.stockman.databinding.ActivityDpactivityBinding
import com.liaobusi.stockman.databinding.FragmentDpSettingBinding
import com.liaobusi.stockman.databinding.ItemStock2Binding
import com.liaobusi.stockman.databinding.ItemStock3Binding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.DIYBk
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.color
import com.liaobusi.stockman.db.isYiZIBan
import com.liaobusi.stockman.db.openDragonTigerRank
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import com.liaobusi.stockman.repo.StockResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.TimeSource


class DPActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDpactivityBinding

    private lateinit var headerListView: List<TextView>

    private lateinit var contentListView: List<RecyclerView>

    private lateinit var containerListView: List<View>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDpactivityBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }





        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val lp = binding.line.layoutParams as ConstraintLayout.LayoutParams
                lp.verticalBias = progress.toFloat() / 100f
                binding.line.layoutParams = lp
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })

        headerListView = mutableListOf<TextView>(
            binding.header0,
            binding.header1,
            binding.header2,
            binding.header3,
            binding.header4,
            binding.header5,
            binding.header6,
            binding.header7
        )

        contentListView = mutableListOf<RecyclerView>(
            binding.rv0,
            binding.rv1,
            binding.rv2,
            binding.rv3,
            binding.rv4,
            binding.rv5,
            binding.rv6,
            binding.rv7
        )
        contentListView.forEach {
            it.adapter = StockAdapter()
            it.layoutManager = LinearLayoutManager(this@DPActivity)
        }

        containerListView = mutableListOf<View>(
            binding.c0,
            binding.c1,
            binding.c2,
            binding.c3,
            binding.c4,
            binding.c5,
            binding.c6,
            binding.c7
        )


        binding.settingBtn.setOnClickListener {
            DPSettingFragment({ sortType, line, list ->
                reload(sortType, list, line)
            }).show(
                this.supportFragmentManager,
                "diy_bk"
            )
        }



        lifecycleScope.launch(Dispatchers.IO) {
            var sortType = Injector.sp.getInt("sort_type", 1)
            var diybkStr = Injector.sp.getString("diy_bks", "")
            var line = Injector.sp.getInt("line", -1)
            if (diybkStr?.isEmpty()==true){
                return@launch
            }
            val list = Gson().fromJson<List<DIYBk>>(
                diybkStr,
                object : TypeToken<List<DIYBk>>() {}.getType()
            ).ifEmpty { Injector.appDatabase.diyBkDao().getDIYBks() }
            val newList = Injector.appDatabase.diyBkDao().getDIYBksByCodes(list.map { it.code })
            reload(sortType, newList, line)
        }

    }

    fun reload(sortType: Int, list: List<DIYBk>, line: Int) {
        lifecycleScope.launch(Dispatchers.IO) {

            list.subList(0, min(8, list.size)).forEachIndexed { index, item ->
                val bkCodeList = item.bkCodes.split(",")
                val codeList = item.stockCodes.split(",")
                val l1 = Injector.appDatabase.stockDao().getStockByCodes(codeList)

                val result = if (line == -1) {
                    StockRepo.strategy4(bkList = bkCodeList, stockList = l1, endTime = today())
                } else {
                    StockRepo.strategy4(
                        bkList = bkCodeList,
                        stockList = l1,
                        endTime = today(),
                        allowBelowCount = 0,
                        averageDay = line,
                        range = line,
                        divergeRate = 0.0
                    )
                }

                launch(Dispatchers.Main) {

                    val rv = contentListView[index]
                    val headerView = headerListView[index]
                    containerListView[index].visibility = View.VISIBLE
                    headerView.text = item.name
                    val result = when (sortType) {
                        5 -> result.stockResults.sortedByDescending { it.stock.chg }
                        1 -> result.stockResults.filter { it.popularity != null && it.popularity!!.rank > 0 }
                            .sortedBy { it.popularity?.rank ?: 1000 }

                        2 -> result.stockResults.filter { it.popularity != null && it.popularity!!.thsRank > 0 }
                            .sortedBy { it.popularity?.thsRank ?: 1000 }

                        4 -> result.stockResults.filter { it.popularity != null && it.popularity!!.dzhRank > 0 }
                            .sortedBy { it.popularity?.dzhRank ?: 1000 }

                        0 -> result.stockResults.sortedByDescending { it.activeRate ?: 0f }
                        else -> result.stockResults.filter { it.popularity != null && it.popularity!!.rank > 0 }
                            .sortedBy { it.popularity?.rank ?: 1000 }
                    }
                    (rv.adapter as StockAdapter).setData(
                        result.toMutableList(),
                        if (sortType == 5) 1 else sortType
                    )
                }
            }

            launch (Dispatchers.Main){
                for (index in min(8, list.size) until 8) {
                    containerListView[index].visibility = View.INVISIBLE
                }
            }



        }


    }


    inner class StockAdapter() :
        RecyclerView.Adapter<StockAdapter.VH>() {

        private val data = mutableListOf<StockResult>()

        private var popularitySort: Int = 0


        fun setData(data: MutableList<StockResult>, popularitySort: Int = 0) {
            this.data.clear()
            this.data.addAll(refocusList(data))
            this.popularitySort = popularitySort
            notifyDataSetChanged()
        }


        private var job: Job? = null

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            job?.cancel()
        }


        private fun refocusList(d: List<StockResult>): List<StockResult> {
            if (d.size < 4) {
                return d
            }
            val originList = mutableListOf<StockResult>().apply {
                addAll(d)
            }
            val list = originList.sortedByDescending { it.stock.chg }
            val newList = mutableListOf<StockResult>()
            val highList3 = list.subList(0, 2)
            val lowList3 = list.subList(list.size - 2, list.size)
            newList.addAll(highList3)
            newList.addAll(lowList3)
            newList.addAll(originList.filter {
                (!highList3.contains(it) && !lowList3.contains(
                    it
                ))
            })

            return newList
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            if (Injector.isTradingTime()) {
                job = lifecycleScope.launch(Dispatchers.IO) {
                    while (true) {
                        delay(1000)
                        if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE || !this@DPActivity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            continue
                        }
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val firstPos = lm.findFirstVisibleItemPosition()
                        val lastPos = lm.findLastVisibleItemPosition()
                        if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) {
                            continue
                        }
                        if (data.isNotEmpty()) {
                            for (i in firstPos until lastPos + 1) {
                                val result = data[i]
                                if (result.currentDayHistory != null && !result.isGroupHeader) {
                                    val s =
                                        Injector.appDatabase.stockDao()
                                            .getStockByCode(result.stock.code)
                                    val h =
                                        Injector.appDatabase.historyStockDao().getHistoryByDate3(
                                            result.stock.code,
                                            result.currentDayHistory!!.date
                                        )
                                    val n =
                                        if (result.nextDayHistory != null) Injector.appDatabase.historyStockDao()
                                            .getHistoryByDate3(
                                                result.stock.code,
                                                result.nextDayHistory!!.date
                                            ) else null
                                    if (h.chg != result.currentDayHistory!!.chg || n?.chg != result.nextDayHistory?.chg) {
                                        val changeRate =
                                            if (h.chg != result.currentDayHistory!!.chg) (h.chg - result.currentDayHistory!!.chg)
                                            else (if (n == null || result.nextDayHistory == null) 0f
                                            else n.chg - result.nextDayHistory!!.chg)
                                        data[i] = result.copy(
                                            stock = s,
                                            currentDayHistory = h,
                                            nextDayHistory = n,
                                            changeRate = changeRate
                                        )
                                        launch(Dispatchers.Main) {
                                            notifyItemChanged(i)
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
                lifecycleScope.launch {
                    while (true) {
                        delay(10000)
                        if (data.size > 6) {
                            this@StockAdapter.setData(mutableListOf<StockResult>().apply {
                                addAll(this@StockAdapter.data)
                            }, popularitySort)
                        }

                    }


                }


            }
        }


        inner class VH(val binding: ItemStock3Binding) : RecyclerView.ViewHolder(binding.root) {

            var anim: ValueAnimator? = null

            @RequiresApi(Build.VERSION_CODES.O)
            fun bind(result: StockResult, position: Int) {


                binding.contentLL.visibility = View.VISIBLE
                binding.stockName.setOnClickListener(null)
                binding.stockName.isClickable = false
                binding.stockName.setTextColor(Color.BLACK)


                val stock = result.stock
                binding.apply {
                    if (result.follow != null) {
                        this.root.setBackgroundColor(0x33333333)
                    } else {
                        this.root.setBackgroundColor(0xffffffff.toInt())
                    }



                    if (result.changeRate != 0f) {
                        colorView.visibility = View.VISIBLE
                        if (colorView.tag != result.stock.code) {
                            anim?.cancel()
                        }

                        anim =
                            ValueAnimator.ofFloat(0f, min(abs(result.changeRate), 0.9f), 0f).apply {
                                duration = 1700
                                startDelay = (1700 * (1 - (anim?.animatedFraction ?: 1f))).toLong()

                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationStart(animation: Animator) {
                                        super.onAnimationStart(animation)
                                        colorView.alpha = 1f
                                        if (result.changeRate > 0) {
                                            colorView.setBackgroundColor(Color.RED)
                                        } else {
                                            colorView.setBackgroundColor(STOCK_GREEN)
                                        }
                                    }

                                    override fun onAnimationEnd(animation: Animator) {
                                        super.onAnimationEnd(animation)
                                        colorView.alpha = 0f
                                        colorView.setBackgroundColor(Color.TRANSPARENT)
                                        result.changeRate = 0f
                                    }
                                })
                                this.addUpdateListener {
                                    colorView.alpha = it.animatedValue as Float
                                }
                                start()
                            }
                        colorView.tag = stock.code
                    } else {
                        colorView.visibility = View.GONE
                    }

                    if (result.ztReplay != null && result.expandReason) {
                        this.expoundTv.visibility = View.VISIBLE
                        this.expoundTv.text =
                            "${result.ztReplay!!.time}\n${result.ztReplay!!.expound}"
                    } else {
                        this.expoundTv.visibility = View.GONE
                    }

                    if (result.expandPOPReason && result.popularity != null) {
                        this.popReasonTv.visibility = View.VISIBLE
                        this.popReasonTv.text = "${result.popularity?.explain}"
                    } else {
                        this.popReasonTv.visibility = View.GONE
                    }


                    //一字板
                    if (result.ztReplay != null && result.ztReplay!!.isYiZIBan) {
                        binding.yizibanView.visibility = View.VISIBLE
                    } else {
                        binding.yizibanView.visibility = View.GONE
                    }

                    if (isShowLianBanFlag(binding.root.context)) {
                        if (result.lianbanCount > 0) {
                            binding.lianbanCountFlagTv.setBackgroundColor(
                                Color.valueOf(
                                    1f,
                                    0f,
                                    0f,
                                    result.lianbanCount / 15f
                                ).toArgb()
                            )
                            binding.lianbanCountFlagTv.visibility = View.VISIBLE
                            binding.lianbanCountFlagTv.text = result.lianbanCount.toString()
                        } else {
                            binding.lianbanCountFlagTv.visibility = View.GONE
                        }
                    } else {
                        binding.lianbanCountFlagTv.visibility = View.GONE
                    }

                    this.stockName.text = stock.name
                    this.stockName.setTextColor(result.groupColor)
                    binding.divider.setBackgroundColor(DIVIDER_COLOR)

                    if (position < 4) {
                        if (result.stock.chg > 0) {
                            binding.stockName.setTextColor(Color.RED)
                            binding.divider.setBackgroundColor(STOCK_RED2)
                        } else {
                            binding.stockName.setTextColor(STOCK_GREEN)
                            binding.divider.setBackgroundColor(STOCK_GREEN2)
                        }
                    } else {
                        binding.stockName.setTypeface(null, Typeface.NORMAL)
                    }


                    if (result.dargonTigerRank != null) {
                        this.dragonFlagIv.visibility = View.VISIBLE
                        this.stockName.setOnClickListener {
                            result.stock.openDragonTigerRank(this@DPActivity)
                        }
                    } else {
                        this.dragonFlagIv.visibility = View.GONE
                    }

                    this.dragonFlagIv.setOnClickListener {
                        result.stock.openDragonTigerRank(this@DPActivity)
                    }


                    if (result.currentDayHistory != null) {
                        currentChg.setTextColor(result.currentDayHistory!!.color)
                        currentChg.text = result.currentDayHistory!!.chg.toString()
                        if (isShowCurrentChg(binding.root.context)) {
                            currentChg.visibility = View.VISIBLE
                        } else {
                            currentChg.visibility = View.GONE
                        }
                    } else {
                        currentChg.visibility = View.GONE
                    }

                    if (result.nextDayHistory != null) {
                        nextDayChg.setTextColor(result.nextDayHistory!!.color)
                        nextDayChg.text = result.nextDayHistory!!.chg.toString()
                        if (isShowNextChg(binding.root.context)) {
                            nextDayChg.visibility = View.VISIBLE
                        } else {
                            nextDayChg.visibility = View.GONE
                        }

                    } else {
                        nextDayChg.visibility = View.GONE
                    }


                    if (popularitySort != 0) {
                        if (result.popularity != null) {
                            this.activeLabelTv.visibility = View.VISIBLE
                            this.activeLabelTv.text =
                                if (popularitySort == 1) result.popularity?.rank.toString() else if (popularitySort == 2) result.popularity?.thsRank.toString() else if (popularitySort == 4) result.popularity?.dzhRank.toString() else result.popularity?.tgbRank.toString()
                        } else {
                            this.activeLabelTv.visibility = View.INVISIBLE
                        }
                    } else {
                        if (result.activeRate > 2) {
                            this.activeLabelTv.visibility = View.VISIBLE
                            this.activeLabelTv.text = result.activeRate.toInt().toString()
                        } else {
                            this.activeLabelTv.visibility = View.INVISIBLE
                        }
                    }




                    this.nextDayIv.visibility =
                        if (result.nextDayZT || result.nextDayCry) View.VISIBLE else View.GONE
                    if (result.nextDayCry) {
                        this.nextDayIv.setImageResource(R.drawable.ic_cry)
                    }
                    if (result.nextDayZT) {
                        this.nextDayIv.setImageResource(R.drawable.ic_thumb_up)
                    }

                    root.setOnClickListener {
                        stock.openWeb(this@DPActivity)
                    }

                    var ev: MotionEvent? = null
                    root.setOnTouchListener { view, motionEvent ->
                        if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                            ev = motionEvent
                        }
                        return@setOnTouchListener false
                    }

                    root.setOnLongClickListener {
                        val b =
                            LayoutStockPopupWindowBinding.inflate(LayoutInflater.from(it.context))

                        if (result.follow != null) {
                            b.followBtn.text = "取消关注"
                        }

                        if (result.follow?.stickyOnTop == 1) {
                            b.stickyOnTopBtn.text = "取消置顶"
                        }

                        if (result.expandReason) {
                            b.expandReasonBtn.text = "折叠涨停原因"
                        }

                        if (!result.zt) {
                            b.expandReasonBtn.visibility = View.GONE
                        } else {
                            b.expandReasonBtn.visibility = View.VISIBLE
                        }


                        if (result.popularity == null) {
                            b.expandPOPReasonBtn.visibility = View.GONE
                        } else {
                            b.expandPOPReasonBtn.visibility = View.VISIBLE
                        }

                        if (result.expandPOPReason) {
                            b.expandPOPReasonBtn.text = "折叠热度原因"
                        }


                        val pw = PopupWindow(
                            b.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            true
                        )

                        b.expandReasonBtn.setOnClickListener {
                            val p = data.indexOf(result)
                            result.expandReason = !result.expandReason
                            notifyItemChanged(p)
                            pw.dismiss()
                        }

                        b.joinBtn.setOnClickListener {
                            DIYBKDialogFragment2(result.stock).show(
                                this@DPActivity.supportFragmentManager,
                                "diy_bk"
                            )
                            pw.dismiss()
                        }

                        b.expandPOPReasonBtn.setOnClickListener {
                            val p = data.indexOf(result)
                            result.expandPOPReason = !result.expandPOPReason
                            notifyItemChanged(p)
                            pw.dismiss()
                        }

                        b.relatedConceptBtn.setOnClickListener {
                            pw.dismiss()
                            StockInfoFragment(
                                result.stock,
                                today().toString()
                            ).show(
                                this@DPActivity.supportFragmentManager,
                                "stock_info"
                            )
                        }
                        b.dragonTigerRankBtn.setOnClickListener {
                            pw.dismiss()
                            result.stock.openDragonTigerRank(this@DPActivity)
                        }

                        //仅关注不置顶
                        b.followBtn.setOnClickListener {
                            pw.dismiss()
                            lifecycleScope.launch(Dispatchers.IO) {
                                val p = data.indexOf(result)
                                if (result.follow != null) {
                                    Injector.appDatabase.followDao()
                                        .deleteFollow(Follow(result.stock.code, 1))

                                    result.follow = null

                                    lifecycleScope.launch(Dispatchers.Main) {
                                        notifyItemChanged(p)
                                    }

                                } else {
                                    result.follow = Follow(result.stock.code, 1, 0)
                                    Injector.appDatabase.followDao()
                                        .insertFollow(result.follow!!)
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        notifyItemChanged(p)
                                    }

                                }


                            }
                        }

                        b.stickyOnTopBtn.setOnClickListener {
                            pw.dismiss()
                            lifecycleScope.launch(Dispatchers.IO) {
                                val p = data.indexOf(result)
                                if (result.follow?.stickyOnTop == 1) {
                                    val n = Follow(result.stock.code, 1, 0)
                                    Injector.appDatabase.followDao().insertFollow(n)
                                    result.follow = n

                                    lifecycleScope.launch(Dispatchers.Main) {
                                        data.remove(result)
                                        notifyItemRemoved(p)
                                        delay(300)
                                        data.add(itemCount - 1, result)
                                        notifyItemInserted(itemCount - 1)
                                    }

                                } else {
                                    val n = Follow(result.stock.code, 1, 1)
                                    result.follow = n
                                    Injector.appDatabase.followDao()
                                        .insertFollow(n)
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        data.remove(result)
                                        notifyItemRemoved(p)
                                        delay(300)
                                        data.add(0, result)
                                        notifyItemInserted(0)
                                    }

                                }


                            }

                        }

                        val arr = IntArray(2)
                        binding.root.getLocationInWindow(arr)
                        pw.showAsDropDown(
                            it,
                            (ev?.x ?: 0f).toInt(),
                            -500 - (binding.root.height - (ev!!.y - arr[1])).toInt()
                        )
                        return@setOnLongClickListener true
                    }

                }
            }

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemStock3Binding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(data[position], position)
        }


    }
}

class DPSettingFragment(private val callback: (sortType: Int, line: Int, bks: List<DIYBk>) -> Unit) :
    DialogFragment() {


    private lateinit var binding: FragmentDpSettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL,R.style.MyDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDpSettingBinding.inflate(inflater)
        binding.rv.layoutManager = LinearLayoutManager(binding.rv.context)

        var sortType = Injector.sp.getInt("sort_type", 1)
        var diybkStr = Injector.sp.getString("diy_bks", "")
        var line = Injector.sp.getInt("line", -1)


        lifecycleScope.launch(Dispatchers.IO) {
            val list = Injector.appDatabase.diyBkDao().getDIYBks()
            launch(Dispatchers.Main) {
                binding.rv.adapter = DIYBKAdapter(list.map {
                    SelectableItem(it, diybkStr?.contains(it.code) == true)
                }.toMutableList())
            }

        }

        when (line) {

            5 -> {
                binding.line5Cb.isChecked = true
            }

            10 -> {
                binding.line10Cb.isChecked = true
            }

            else -> {
                binding.line10Cb.isChecked = false
                binding.line5Cb.isChecked = false
            }

        }



        when (sortType) {
            5 -> binding.zfSortCb.isChecked = true
            1 -> binding.popularitySortCb.isChecked = true
            2 -> binding.thsPopularitySortCb.isChecked = true
            4 -> binding.dzhPopularitySortCb.isChecked = true
            0 -> binding.activityLevelCb.isChecked = true
        }

        binding.okBtn.setOnClickListener {
            val list = (binding.rv.adapter as DIYBKAdapter).getSelectedList()
            callback.invoke(sortType, line, list)
            val s = Gson().toJson(list)
            Injector.sp.edit().putInt("sort_type", sortType).putString("diy_bks", s)
                .putInt("line", line).apply()
            dismiss()
        }

        binding.line5Cb.setOnCheckedChangeListener { v, isChecked ->
            if (isChecked) {
                line = 5
                binding.line10Cb.isChecked = false
            } else {
                line = -1
            }

        }

        binding.line10Cb.setOnCheckedChangeListener { v, isChecked ->
            if (isChecked) {
                line = 10
                binding.line5Cb.isChecked = false
            } else {
                line = -1
            }

        }

        binding.zfSortCb.setOnCheckedChangeListener { v, isChecked ->

            if (isChecked) {
                sortType = 5
                binding.activityLevelCb.isChecked = false
                binding.dzhPopularitySortCb.isChecked = false
                binding.thsPopularitySortCb.isChecked = false
                binding.popularitySortCb.isChecked = false
            }
        }


        binding.popularitySortCb.setOnCheckedChangeListener { v, isChecked ->

            if (isChecked) {
                sortType = 1
                binding.activityLevelCb.isChecked = false
                binding.dzhPopularitySortCb.isChecked = false
                binding.thsPopularitySortCb.isChecked = false
                binding.zfSortCb.isChecked = false
            }
        }


        binding.thsPopularitySortCb.setOnCheckedChangeListener { v, isChecked ->

            if (isChecked) {
                sortType = 2
                binding.activityLevelCb.isChecked = false
                binding.dzhPopularitySortCb.isChecked = false
                binding.popularitySortCb.isChecked = false
                binding.zfSortCb.isChecked = false
            }
        }


        binding.dzhPopularitySortCb.setOnCheckedChangeListener { v, isChecked ->

            if (isChecked) {
                sortType = 4
                binding.activityLevelCb.isChecked = false
                binding.thsPopularitySortCb.isChecked = false
                binding.popularitySortCb.isChecked = false
                binding.zfSortCb.isChecked = false
            }
        }

        binding.activityLevelCb.setOnCheckedChangeListener { v, isChecked ->

            if (isChecked) {
                sortType = 0
                binding.dzhPopularitySortCb.isChecked = false
                binding.thsPopularitySortCb.isChecked = false
                binding.popularitySortCb.isChecked = false
                binding.zfSortCb.isChecked = false
            }
        }



        binding.cancelBtn.setOnClickListener {
            dismiss()
        }
        return binding.root

    }
}


