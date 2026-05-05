package com.liaobusi.stockman
import com.liaobusi.stockman5.R

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liaobusi.stockman5.databinding.ActivityYdHistoryBinding
import com.liaobusi.stockman5.databinding.ItemYdHistoryBinding
import com.liaobusi.stockman.db.PopularityRank
import com.liaobusi.stockman.db.UnusualActionHistory
import com.liaobusi.stockman.db.ZT
import com.liaobusi.stockman.db.ZTReplayBean
import com.liaobusi.stockman.db.expoundV
import com.liaobusi.stockman.db.groupNameV
import com.liaobusi.stockman.db.reasonV
import com.liaobusi.stockman.db.source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class YDHistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "code"

        fun start(context: Context, code: String) {
            val i = Intent(context, YDHistoryActivity::class.java).putExtra(EXTRA_CODE, code)
            context.startActivity(i)
        }
    }

    private lateinit var binding: ActivityYdHistoryBinding
    private var stockCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYdHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        stockCode = intent.getStringExtra(EXTRA_CODE).orEmpty()
        // 不用 setSupportActionBar：否则部分机型/主题下 Window 标题会覆盖 Toolbar.title，导致标题不显示
        binding.toolbar.title =
            if (stockCode.isNotEmpty()) "异动历史 · $stockCode" else "异动历史"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val adapter = YDHistoryAdapter(stockCode)
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        if (stockCode.isEmpty()) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val displayName =
                runCatching {
                    Injector.appDatabase.stockDao().getStockByCode(stockCode).name.trim()
                }.getOrNull()?.takeIf { it.isNotEmpty() } ?: stockCode
            withContext(Dispatchers.Main) {
                binding.toolbar.title = "异动历史 · $displayName"
                adapter.setHighlightStockName(displayName)
            }

            val replayList =
                Injector.appDatabase.ztReplayDao().listByCodeOrderByDateDesc(stockCode)
            val unusualAll =
                Injector.appDatabase.unusualActionHistoryDao().listContainingStockCode(stockCode)
            val unusualByDate = unusualAll.groupBy { epochSecToYyyyMmDd(it.time) }

            val ztDates: Set<Int> = if (replayList.isEmpty()) {
                emptySet()
            } else {
                val minDate = replayList.minOf { it.date }
                val maxDate = replayList.maxOf { it.date }
                Injector.appDatabase.historyStockDao()
                    .getHistoryRange(stockCode, minDate, maxDate)
                    .asSequence().filter { it.ZT }.map { it.date }.toSet()
            }

            val replayByDate = replayList.associateBy { it.date }
            val allDates =
                (replayByDate.keys + unusualByDate.keys).distinct().sortedDescending()
            val heatByDate: Map<Int, Int> =
                if (allDates.isEmpty()) {
                    emptyMap()
                } else {
                    val minD = allDates.min()
                    val maxD = allDates.max()
                    Injector.appDatabase.popularityRankDao()
                        .getByCodeAndDateRange(stockCode, minD, maxD)
                        .mapNotNull { pr -> popularityHeatValue(pr)?.let { pr.date to it } }
                        .toMap()
                }
            val rows = allDates.map { dateKey ->
                val replay = replayByDate[dateKey]
                val unusualSameDay = unusualByDate[dateKey].orEmpty()
                YdHistoryRow(
                    dateKey = dateKey,
                    replay = replay,
                    isLimitUpDay = replay != null && ztDates.contains(replay.date),
                    unusualSameDay = unusualSameDay,
                    popularityHeat = heatByDate[dateKey],
                )
            }
            withContext(Dispatchers.Main) {
                adapter.submitList(rows)
            }
        }
    }

    private data class YdHistoryRow(
        val dateKey: Int,
        val replay: ZTReplayBean?,
        val isLimitUpDay: Boolean,
        val unusualSameDay: List<UnusualActionHistory>,
        /** 东财 rank 与同花顺 thsRank 均为正数时四舍五入后的热度均值，否则为 null */
        val popularityHeat: Int?,
    )

    private class YdDiff : DiffUtil.ItemCallback<YdHistoryRow>() {
        override fun areItemsTheSame(oldItem: YdHistoryRow, newItem: YdHistoryRow): Boolean {
            return oldItem.dateKey == newItem.dateKey
        }

        override fun areContentsTheSame(oldItem: YdHistoryRow, newItem: YdHistoryRow): Boolean {
            return oldItem == newItem
        }
    }

    private inner class YDHistoryAdapter(private val code: String) :
        ListAdapter<YdHistoryRow, YDHistoryAdapter.VH>(YdDiff()) {

        private val expandedKeys = mutableSetOf<String>()
        private var highlightStockName: String = ""

        private fun rowKey(row: YdHistoryRow) = "${code}_${row.dateKey}"

        fun setHighlightStockName(name: String) {
            val n = name.trim()
            if (highlightStockName == n) return
            highlightStockName = n
            notifyDataSetChanged()
        }

        inner class VH(val itemBinding: ItemYdHistoryBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val holder =
                VH(ItemYdHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            holder.itemBinding.expandToggle.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val row = getItem(pos)
                val key = rowKey(row)
                if (!expandedKeys.remove(key)) {
                    expandedKeys.add(key)
                }
                notifyItemChanged(pos)
            }
            return holder
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = getItem(position)
            val item = row.replay
            val key = rowKey(row)
            val expanded = expandedKeys.contains(key)

            holder.itemBinding.ztWatermark.isVisible = row.isLimitUpDay
            holder.itemBinding.dateTv.text = formatStockDate(row.dateKey)

            val heat = row.popularityHeat
            holder.itemBinding.popularityHeatContainer.isVisible = heat != null
            if (heat != null) {
                applyPopularityHeatContainerSize(holder.itemBinding.popularityHeatContainer, heat)
                bindStyledPopularityHeat(holder.itemBinding.popularityHeatTv, heat)
                holder.itemBinding.popularityHeatContainer.contentDescription =
                    holder.itemView.context.getString(R.string.yd_popularity_heat, heat.toString())
            } else {
                applyPopularityHeatContainerSize(holder.itemBinding.popularityHeatContainer, null)
            }

            val timeStr: String?
            val showTime: Boolean
            if (item != null) {
                val t = item.time
                showTime = t.isNotBlank() && t != "--:--:--"
                timeStr = if (showTime) t else null
            } else {
                val u = row.unusualSameDay
                if (u.isEmpty()) {
                    showTime = false
                    timeStr = null
                } else if (u.size == 1) {
                    showTime = true
                    timeStr = timePartFromEpochSec(u[0].time)
                } else {
                    showTime = true
                    val latest = u.maxOf { it.time }
                    timeStr =
                        "${timePartFromEpochSec(latest)}（${u.size}${holder.itemView.context.getString(R.string.yd_unusual_count_suffix)}）"
                }
            }

            holder.itemBinding.timeTv.isVisible = showTime && timeStr != null
            if (showTime && timeStr != null) {
                holder.itemBinding.timeTv.text = timeStr
            }

            if (item != null) {
                val group = item.groupNameV
                holder.itemBinding.groupSection.isVisible = group.isNotBlank()
                if (group.isNotBlank()) {
                    holder.itemBinding.groupTv.text = group
                }
                val reason = item.reasonV
                holder.itemBinding.reasonSection.isVisible = reason.isNotBlank()
                if (reason.isNotBlank()) {
                    holder.itemBinding.reasonTv.text = reason
                }
                val expound = item.expoundV
                holder.itemBinding.expoundSection.isVisible = expound.isNotBlank()
                if (expound.isNotBlank()) {
                    holder.itemBinding.expoundTv.text = expound
                }
            } else {
                holder.itemBinding.groupSection.isVisible = false
                holder.itemBinding.reasonSection.isVisible = false
                holder.itemBinding.expoundSection.isVisible = false
            }

            val unusualText = formatAggregatedUnusual(row.unusualSameDay)
            holder.itemBinding.unusualSection.isVisible = unusualText.isNotBlank()
            if (unusualText.isNotBlank()) {
                holder.itemBinding.unusualTv.text = highlightMatches(
                    text = unusualText,
                    keyword = highlightStockName,
                    color = ContextCompat.getColor(holder.itemView.context, R.color.yd_accent),
                )
            }

            val hasReplayDetails = item != null && (
                item.groupNameV.isNotBlank() || item.reasonV.isNotBlank() ||
                    item.expoundV.isNotBlank()
                )
            val hasDetails = hasReplayDetails || unusualText.isNotBlank()
            holder.itemBinding.ydDetails.isVisible = hasDetails
            holder.itemBinding.expandToggle.isVisible = hasDetails
            if (hasDetails) {
                applyYdDetailFoldState(holder.itemBinding, expanded)
                holder.itemBinding.expandLabel.text = holder.itemView.context.getString(
                    if (expanded) R.string.yd_label_collapse else R.string.yd_label_expand,
                )
                holder.itemBinding.expandIcon.rotation = if (expanded) 180f else 0f
            }
        }
    }
}

private fun highlightMatches(text: String, keyword: String, color: Int): CharSequence {
    val k = keyword.trim()
    if (text.isBlank() || k.isBlank()) return text

    var start = text.indexOf(k, startIndex = 0, ignoreCase = false)
    if (start < 0) return text

    val ss = SpannableString(text)
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
    return ss
}

/**
 * 排名数值越小，球形区域越大（与 [popularityHeatFade01] 同一套过渡）；边长区间固定为 22dp。
 * 宽高强制一致，避免多位数把横向撑开、纵向仍按最小值测量导致「瘪」成椭圆。
 */
private fun applyPopularityHeatContainerSize(container: View, rank: Int?) {
    val lp = container.layoutParams ?: return
    if (rank == null) {
        container.minimumWidth = 0
        container.minimumHeight = 0
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        container.layoutParams = lp
        return
    }
    val density = container.resources.displayMetrics.density
    val fade = popularityHeatFade01(rank)
    val maxDp = 46f
    val minDp = maxDp - 22f
    val baseDp = maxDp - fade * (maxDp - minDp)
    val digitCount = rank.toString().length
    val floorDp = 26f + (digitCount - 1).coerceAtLeast(0) * 3f
    val sideDp = maxOf(baseDp, floorDp).coerceAtMost(maxDp)
    val px = (sideDp * density).toInt().coerceAtLeast(1)
    lp.width = px
    lp.height = px
    container.minimumWidth = px
    container.minimumHeight = px
    container.layoutParams = lp
}

/**
 * 球形底上白字：名次越靠前字号越大，名次越靠后略小。
 */
private fun bindStyledPopularityHeat(textView: TextView, value: Int) {
    val fade = popularityHeatFade01(value)
    textView.setTextColor(ContextCompat.getColor(textView.context, R.color.white))
    val sizeSp = 22f - fade * (22f - 13f)
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    textView.setTypeface(null, Typeface.BOLD)
    textView.text = value.toString()
}

/** 排名 1 → 0（最醒目），约 100 及以上 → 1（最淡），中间线性过渡 */
private fun popularityHeatFade01(rank: Int): Float {
    val r = rank.coerceAtLeast(1)
    return ((r - 1).coerceIn(0, 99)) / 99f
}

/** 当 rank、thsRank 均为正数时返回 round((rank+thsRank)/2)，否则 null */
private fun popularityHeatValue(pr: PopularityRank): Int? {
    if (pr.rank > 0 && pr.thsRank > 0) {
        return kotlin.math.round((pr.rank + pr.thsRank) / 2.0).toInt()
    }
    return null
}

/** UnusualActionHistory.time 为秒（与 ext.toDateTimeString 一致） */
private fun epochSecToYyyyMmDd(epochSec: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochSec * 1000
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return y * 10000 + m * 100 + d
}

private fun timePartFromEpochSec(epochSec: Long): String {
    val s = epochSec.toDateTimeString()
    return if (s.length >= 19) s.substring(11) else s
}

private fun formatAggregatedUnusual(items: List<UnusualActionHistory>): String {
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

private fun applyYdDetailFoldState(binding: ItemYdHistoryBinding, expanded: Boolean) {
    if (expanded) {
        binding.groupTv.maxLines = Int.MAX_VALUE
        binding.groupTv.ellipsize = null
        binding.reasonTv.maxLines = Int.MAX_VALUE
        binding.reasonTv.ellipsize = null
        binding.expoundTv.maxLines = Int.MAX_VALUE
        binding.expoundTv.ellipsize = null
        binding.unusualTv.maxLines = Int.MAX_VALUE
        binding.unusualTv.ellipsize = null
    } else {
        binding.groupTv.maxLines = 2
        binding.groupTv.ellipsize = TextUtils.TruncateAt.END
        binding.reasonTv.maxLines = 3
        binding.reasonTv.ellipsize = TextUtils.TruncateAt.END
        binding.expoundTv.maxLines = 3
        binding.expoundTv.ellipsize = TextUtils.TruncateAt.END
        binding.unusualTv.maxLines = 4
        binding.unusualTv.ellipsize = TextUtils.TruncateAt.END
    }
}

private fun formatStockDate(date: Int): String {
    val s = date.toString()
    return if (s.length == 8) {
        "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
    } else {
        s
    }
}
