package com.liaobusi.stockman
import com.liaobusi.stockman5.R

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liaobusi.stockman5.databinding.ActivityRelatedStocksBinding
import com.liaobusi.stockman5.databinding.ItemRelatedStockBinding
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.db.source
import com.liaobusi.stockman.db.unusualActionHistoryEpochRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RelatedStocksActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_END_DATE = "endDate" // yyyyMMdd，可选

        fun start(context: Context, code: String, endDateYmd: Int? = null) {
            val i = Intent(context, RelatedStocksActivity::class.java).putExtra(EXTRA_CODE, code)
            if (endDateYmd != null) i.putExtra(EXTRA_END_DATE, endDateYmd)
            context.startActivity(i)
        }
    }

    private lateinit var binding: ActivityRelatedStocksBinding
    private var stockCode: String = ""
    private var stockDisplayName: String = ""
    private var endDateYmd: Int = today()
    private var daysBack: Int = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRelatedStocksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 让 root 能抢到焦点，便于按钮点击后让输入框失焦（停止光标闪烁）
        binding.root.isFocusableInTouchMode = true

        stockCode = intent.getStringExtra(EXTRA_CODE).orEmpty().trim()
        endDateYmd = intent.getIntExtra(EXTRA_END_DATE, today())
        daysBack = 90

        binding.toolbar.title = if (stockCode.isNotEmpty()) "关联股票 · $stockCode" else "关联股票"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val adapter = RelatedStocksAdapter(
            host = this,
            baseCode = { stockCode },
            baseName = { stockDisplayName },
            range = { resolveRangeEpochSec(endDateYmd, daysBack) },
        )
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        if (stockCode.isEmpty()) return

        binding.endDateEt.setText(endDateYmd.toString())
        binding.daysBackEt.setText(daysBack.toString())
        binding.endDateEt.isCursorVisible = false
        binding.daysBackEt.isCursorVisible = false

        val cursorToggle = View.OnFocusChangeListener { v, hasFocus ->
            when (v) {
                binding.endDateEt -> binding.endDateEt.isCursorVisible = hasFocus
                binding.daysBackEt -> binding.daysBackEt.isCursorVisible = hasFocus
            }
        }
        binding.endDateEt.onFocusChangeListener = cursorToggle
        binding.daysBackEt.onFocusChangeListener = cursorToggle

        binding.queryBtn.setOnClickListener {
            val ymd = binding.endDateEt.editableText.toString().toIntOrNull()
            val back = binding.daysBackEt.editableText.toString().toIntOrNull()
            clearInputsFocus()
            if (ymd == null || !ymd.toString().isAfter20220101()) {
                Toast.makeText(this, "截止日期不合法", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ymd > today()) {
                Toast.makeText(this, "截止日期不能超过今天", Toast.LENGTH_SHORT).show()
                binding.endDateEt.setText(today().toString())
                endDateYmd = today()
                return@setOnClickListener
            }
            if (back == null || back <= 0 || back > 365) {
                Toast.makeText(this, "回溯天数不合法（1-365）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            endDateYmd = ymd
            daysBack = back
            adapter.resetExpanded()
            load(adapter)
        }

        binding.prevDayBtn.setOnClickListener {
            clearInputsFocus()
            lifecycleScope.launch(Dispatchers.IO) {
                endDateYmd = preTradingDay(endDateYmd)//shiftYmd(endDateYmd, -1)
                launch(Dispatchers.Main) {
                    binding.endDateEt.setText(endDateYmd.toString())
                    adapter.resetExpanded()
                    load(adapter)
                }

            }
        }

        binding.nextDayBtn.setOnClickListener {
            clearInputsFocus()
            lifecycleScope.launch(Dispatchers.IO) {
                endDateYmd = nextTradingDay(endDateYmd)//shiftYmd(endDateYmd, +1)
                launch(Dispatchers.Main) {
                    val t = today()
                    if (endDateYmd > t) {
                        endDateYmd = t
                        Toast.makeText(
                            this@RelatedStocksActivity,
                            "截止日期不能超过今天",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    binding.endDateEt.setText(endDateYmd.toString())
                    adapter.resetExpanded()
                    load(adapter)
                }

            }

        }

        load(adapter)
    }

    private fun clearInputsFocus() {
        binding.endDateEt.isCursorVisible = false
        binding.daysBackEt.isCursorVisible = false
        binding.endDateEt.clearFocus()
        binding.daysBackEt.clearFocus()
        binding.root.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun load(adapter: RelatedStocksAdapter) {
        lifecycleScope.launch(Dispatchers.IO) {
            stockDisplayName =
                runCatching {
                    Injector.appDatabase.stockDao().getStockByCode(stockCode).name.trim()
                }.getOrNull()?.takeIf { it.isNotEmpty() } ?: stockCode

            val (startSec, endSec) = resolveRangeEpochSec(endDateYmd, daysBack = daysBack)
            val histories = Injector.appDatabase.unusualActionHistoryDao()
                .listContainingStockCodeInRange(stockCode, startSec, endSec)
                .filter { epochSecondsInCnTradingSession(it.time) }

            val counts = mutableMapOf<String, Int>()
            histories.forEach { h ->
                val codes = parseStocksCsv(h.stocks)
                codes.forEach { c ->
                    if (c == stockCode) return@forEach
                    counts[c] = (counts[c] ?: 0) + 1
                }
            }

            val ordered = counts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })

            val relatedCodes = ordered.map { it.key }
            val nameByCode = if (relatedCodes.isEmpty()) {
                emptyMap()
            } else {
                runCatching {
                    Injector.appDatabase.stockDao().getStockByCodes(relatedCodes)
                        .associate { it.code to it.name.trim() }
                }.getOrDefault(emptyMap())
            }

            val historyDao = Injector.appDatabase.historyStockDao()
            val histByCode = relatedCodes.associateWith { code ->
                historyDao.getHistoryBefore3(code, endDateYmd, limit = 20)
                    .firstOrNull()
            }

            val rows = ordered.map { (code, score) ->
                val h = histByCode[code]
                RelatedRow(
                    code = code,
                    name = nameByCode[code].orEmpty(),
                    score = score,
                    chgPct = h?.chg,
                )
            }

            withContext(Dispatchers.Main) {
                binding.toolbar.title = "关联股票 · $stockDisplayName"
                binding.toolbar.subtitle = "近${daysBack}天（至 ${formatYmd(endDateYmd)}）"
                adapter.submitList(rows)
                val empty = rows.isEmpty()
                binding.emptyTip.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rv.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }
    }

    private data class RelatedRow(
        val code: String,
        val name: String,
        val score: Int,
        val chgPct: Float?,
    )

    private class RelatedDiff : DiffUtil.ItemCallback<RelatedRow>() {
        override fun areItemsTheSame(oldItem: RelatedRow, newItem: RelatedRow): Boolean {
            return oldItem.code == newItem.code
        }

        override fun areContentsTheSame(oldItem: RelatedRow, newItem: RelatedRow): Boolean {
            return oldItem == newItem
        }
    }

    private class RelatedStocksAdapter(
        private val host: RelatedStocksActivity,
        private val baseCode: () -> String,
        private val baseName: () -> String,
        private val range: () -> Pair<Long, Long>,
    ) : ListAdapter<RelatedRow, RelatedStocksAdapter.VH>(RelatedDiff()) {

        private val expanded = mutableSetOf<String>()
        private val details = mutableMapOf<String, CharSequence>()

        inner class VH(val itemBinding: ItemRelatedStockBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        fun resetExpanded() {
            expanded.clear()
            details.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val holder =
                VH(
                    ItemRelatedStockBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            holder.itemBinding.root.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val row = getItem(pos)
                toggleExpand(row.code, row.name)
            }
            return holder
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            onBindViewHolder(holder, position, mutableListOf())
        }

        override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
            val row = getItem(position)
            when {
                payloads.isNotEmpty() && payloads.all { it == PAYLOAD_DETAILS_TOGGLE } -> {
                    bindDetailsAnimated(holder, row)
                }
                payloads.isNotEmpty() && payloads.all { it == PAYLOAD_DETAILS_TEXT } -> {
                    if (expanded.contains(row.code)) {
                        holder.itemBinding.detailsTv.text = details[row.code] ?: ""
                    }
                }
                else -> bindFull(holder, row)
            }
        }

        private fun bindFull(holder: VH, row: RelatedRow) {
            val detailsTv = holder.itemBinding.detailsTv
            detailsTv.animate().cancel()
            detailsTv.alpha = 1f
            detailsTv.translationY = 0f

            holder.itemBinding.codeTv.text = row.code
            holder.itemBinding.nameTv.text = row.name.ifBlank { "--" }
            holder.itemBinding.scoreTv.text = row.score.toString()
            val pct = row.chgPct
            if (pct != null) {
                holder.itemBinding.chgTv.text =
                    String.format(Locale.getDefault(), "%+.2f%%", pct.toDouble())
                holder.itemBinding.chgTv.setTextColor(
                    when {
                        pct > 0f -> STOCK_RED
                        pct < 0f -> STOCK_GREEN
                        else -> Color.BLACK
                    },
                )
            } else {
                holder.itemBinding.chgTv.text = "--"
                holder.itemBinding.chgTv.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.gray_600),
                )
            }
            holder.itemBinding.nameCodeClickArea.setOnClickListener {
                relatedStockDeepLink(row.code, row.name).openWeb(holder.itemView.context)
            }

            bindDetailsStatic(holder, row)
        }

        private fun bindDetailsStatic(holder: VH, row: RelatedRow) {
            val tv = holder.itemBinding.detailsTv
            val isExpanded = expanded.contains(row.code)
            tv.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                tv.text = details[row.code] ?: "加载中…"
                tv.ellipsize = null
                tv.maxLines = Int.MAX_VALUE
            } else {
                tv.text = ""
                tv.ellipsize = TextUtils.TruncateAt.END
                tv.maxLines = 1
            }
        }

        private fun dp(holder: VH, dp: Float): Float =
            dp * holder.itemView.resources.displayMetrics.density

        private fun bindDetailsAnimated(holder: VH, row: RelatedRow) {
            val tv = holder.itemBinding.detailsTv
            tv.animate().cancel()
            val code = row.code
            if (expanded.contains(code)) {
                tv.text = details[code] ?: "加载中…"
                tv.ellipsize = null
                tv.maxLines = Int.MAX_VALUE
                tv.visibility = View.VISIBLE
                val slide = dp(holder, 10f)
                tv.alpha = 0f
                tv.translationY = -slide
                tv.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                if (tv.visibility == View.VISIBLE) {
                    tv.animate()
                        .alpha(0f)
                        .translationY(-dp(holder, 6f))
                        .setDuration(160L)
                        .setInterpolator(AccelerateInterpolator())
                        .withEndAction {
                            if (expanded.contains(code)) return@withEndAction
                            tv.text = ""
                            tv.ellipsize = TextUtils.TruncateAt.END
                            tv.maxLines = 1
                            tv.visibility = View.GONE
                            tv.alpha = 1f
                            tv.translationY = 0f
                        }
                        .start()
                } else {
                    tv.text = ""
                    tv.ellipsize = TextUtils.TruncateAt.END
                    tv.maxLines = 1
                    tv.visibility = View.GONE
                }
            }
        }

        private fun notifyDetailsToggle(position: Int) {
            if (position < 0 || position >= currentList.size) return
            notifyItemChanged(position, PAYLOAD_DETAILS_TOGGLE)
        }

        private fun toggleExpand(relatedCode: String, relatedName: String) {
            if (!expanded.remove(relatedCode)) {
                expanded.add(relatedCode)
                if (!details.containsKey(relatedCode)) {
                    details[relatedCode] = "加载中…"
                    notifyDetailsToggle(currentList.indexOfFirst { it.code == relatedCode })
                    host.lifecycleScope.launch(Dispatchers.IO) {
                        val (startSec, endSec) = range()
                        val items = Injector.appDatabase.unusualActionHistoryDao()
                            .listContainingTwoStockCodesInRange(
                                baseCode(),
                                relatedCode,
                                startSec,
                                endSec,
                            ).filter { epochSecondsInCnTradingSession(it.time) }

                        val nameMap = runCatching {
                            Injector.appDatabase.stockDao()
                                .getStockByCodes(listOf(baseCode(), relatedCode).distinct())
                                .associate { it.code to it.name.trim() }
                        }.getOrDefault(emptyMap())
                        val n1 = baseName().ifBlank { nameMap[baseCode()].orEmpty() }
                        val n2 = relatedName.ifBlank { nameMap[relatedCode].orEmpty() }

                        val raw = formatAggregatedUnusual(items)
                        val highlighted = highlightMatchesTwo(
                            text = raw,
                            keywordA = n1,
                            keywordB = n2,
                            color = ContextCompat.getColor(host, R.color.yd_accent),
                        )
                        withContext(Dispatchers.Main) {
                            details[relatedCode] =
                                if (raw.isBlank()) "该时间段内暂无共同异动" else highlighted
                            val idx = currentList.indexOfFirst { it.code == relatedCode }
                            if (idx >= 0 && expanded.contains(relatedCode)) {
                                notifyItemChanged(idx, PAYLOAD_DETAILS_TEXT)
                            }
                        }
                    }
                } else {
                    notifyDetailsToggle(currentList.indexOfFirst { it.code == relatedCode })
                }
            } else {
                notifyDetailsToggle(currentList.indexOfFirst { it.code == relatedCode })
            }
        }

        companion object {
            private const val PAYLOAD_DETAILS_TOGGLE = "related_details_toggle"
            private const val PAYLOAD_DETAILS_TEXT = "related_details_text"
        }
    }
}

private fun relatedStockDeepLink(code: String, name: String): Stock =
    Stock(
        code = code,
        name = name.ifBlank { "--" },
        price = 0f,
        chg = 0f,
        amplitude = 0f,
        turnoverRate = 0f,
        highest = 0f,
        lowest = 0f,
        circulationMarketValue = 0f,
        toMarketTime = 0,
        openPrice = 0f,
        yesterdayClosePrice = 0f,
        ztPrice = -1f,
        dtPrice = -1f,
        averagePrice = -1f,
        bk = "",
    )

private fun parseStocksCsv(csv: String): List<String> {
    return csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/**
 * UnusualActionHistory.time 单位为秒；与 [com.liaobusi.stockman.db.unusualActionHistoryEpochRange] 一致。
 */
private fun resolveRangeEpochSec(endDateYmd: Int, daysBack: Int): Pair<Long, Long> =
    unusualActionHistoryEpochRange(endDateYmd, daysBack)

private fun formatYmd(ymd: Int): String {
    val s = ymd.toString()
    return if (s.length == 8) "${s.substring(0, 4)}-${s.substring(4, 6)}-${
        s.substring(
            6,
            8
        )
    }" else s
}

private fun shiftYmd(ymd: Int, deltaDays: Int): Int {
    val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val date = runCatching { sdf.parse(ymd.toString()) }.getOrNull() ?: Date()
    val cal = Calendar.getInstance().apply { time = date }
    cal.add(Calendar.DATE, deltaDays)
    return sdf.format(cal.time).toInt()
}

private fun formatAggregatedUnusual(items: List<com.liaobusi.stockman.db.UnusualActionHistory>): String {
    if (items.isEmpty()) return ""
    return items.sortedByDescending { it.time }.joinToString("\n\n") { u ->
        buildString {
            append(u.source.trim())
            if (u.source.isNotBlank()) append(' ')
            append(u.time.toDateTimeString())
            append('\n')
            append(u.comment.trim())
        }
    }
}

private fun highlightMatchesTwo(
    text: String,
    keywordA: String,
    keywordB: String,
    color: Int,
): CharSequence {
    val ss = SpannableString(text)
    fun apply(keyword: String) {
        val k = keyword.trim()
        if (k.isBlank()) return
        var start = text.indexOf(k, startIndex = 0, ignoreCase = false)
        while (start >= 0) {
            val end = (start + k.length).coerceAtMost(text.length)
            ss.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            ss.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            start = text.indexOf(k, startIndex = end, ignoreCase = false)
        }
    }
    apply(keywordA)
    apply(keywordB)
    return ss
}

