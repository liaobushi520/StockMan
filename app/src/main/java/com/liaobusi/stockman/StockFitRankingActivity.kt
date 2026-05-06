package com.liaobusi.stockman
import com.liaobusi.stockman5.R

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liaobusi.stockman5.databinding.ActivityStockFitRankingBinding
import com.liaobusi.stockman5.databinding.ItemStockFitRankBinding
import com.liaobusi.stockman.db.HistoryStock
import com.liaobusi.stockman.db.isST
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/**
 * 指定股票（[EXTRA_CODE]，未传时默认 600519）与本地库中同期有 K 线的其他股票逐只计算走势拟合度，按相关强度排序展示。
 */
class StockFitRankingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockFitRankingBinding
    private lateinit var anchorCode: String

    private val adapter = FitRankAdapter(
        onItemClick = { row ->
            lifecycleScope.launch {
                val stock = withContext(Dispatchers.IO) {
                    Injector.appDatabase.stockDao().getStockByCode(row.code)
                }
                if (stock != null) {
                    stock.openWeb(this@StockFitRankingActivity)
                } else {
                    Toast.makeText(
                        this@StockFitRankingActivity,
                        "本地无 ${row.code} 基本信息",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockFitRankingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        anchorCode = intent.getStringExtra(EXTRA_CODE)?.trim()?.uppercase(Locale.getDefault()).orEmpty()
            .ifEmpty { "600519" }

        val end = today()
        binding.endDateEt.setText(end.toString())
        binding.lookbackDaysEt.setText(DEFAULT_LOOKBACK_DAYS.toString())

        refreshToolbarAnchorLabel()

        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        binding.endDatePrevDayBtn.setOnClickListener {
            clearRankingInputsFocus()
            val raw = binding.endDateEt.text?.toString()?.trim()?.toIntOrNull()
            val base = raw?.takeIf { it in MIN_END_YMD..MAX_END_YMD } ?: today()
            lifecycleScope.launch(Dispatchers.IO) {
                val newEnd = preTradingDay(base)
                withContext(Dispatchers.Main) {
                    if (!isFinishing) binding.endDateEt.setText(newEnd.toString())
                }
            }
        }

        binding.endDateNextDayBtn.setOnClickListener {
            clearRankingInputsFocus()
            val raw = binding.endDateEt.text?.toString()?.trim()?.toIntOrNull()
            val base = raw?.takeIf { it in MIN_END_YMD..MAX_END_YMD } ?: today()
            lifecycleScope.launch(Dispatchers.IO) {
                var newEnd = nextTradingDay(base)
                withContext(Dispatchers.Main) {
                    if (isFinishing) return@withContext
                    val t = today()
                    if (newEnd > t) {
                        newEnd = t
                        Toast.makeText(
                            this@StockFitRankingActivity,
                            "截止日期不能超过今天",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    binding.endDateEt.setText(newEnd.toString())
                }
            }
        }

        binding.runBtn.setOnClickListener { runRanking() }
    }

    private fun clearRankingInputsFocus() {
        binding.lookbackDaysEt.clearFocus()
        binding.endDateEt.clearFocus()
        binding.root.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun refreshToolbarAnchorLabel() {
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                Injector.appDatabase.stockDao().getStockByCode(anchorCode)?.name?.trim().orEmpty()
            }
            if (isFinishing) return@launch
            binding.toolbar.subtitle = if (name.isNotEmpty()) {
                getString(R.string.stock_fit_rank_toolbar_sub, name, anchorCode)
            } else {
                anchorCode
            }
        }
    }

    private fun runRanking() {
        clearRankingInputsFocus()

        val lookbackDays = binding.lookbackDaysEt.text?.toString()?.trim()?.toIntOrNull()
        val end = binding.endDateEt.text?.toString()?.trim()?.toIntOrNull()
        val useNet = binding.networkCb.isChecked

        if (end == null || end !in MIN_END_YMD..MAX_END_YMD) {
            Toast.makeText(this, "结束日为 8 位 yyyyMMdd 整数", Toast.LENGTH_SHORT).show()
            return
        }
        if (lookbackDays == null || lookbackDays !in 1..MAX_LOOKBACK_DAYS) {
            Toast.makeText(
                this,
                "回溯天数须为 1～$MAX_LOOKBACK_DAYS 的整数",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val start = try {
            end.toLong().toDate().after(-lookbackDays)
        } catch (_: Throwable) {
            Toast.makeText(this, "结束日无法解析为有效日期", Toast.LENGTH_SHORT).show()
            return
        }

        binding.runBtn.isEnabled = false
        binding.endDatePrevDayBtn.isEnabled = false
        binding.endDateNextDayBtn.isEnabled = false
        binding.queryBtnBorderLoading.setLoading(true)
        binding.statusTv.text = getString(R.string.stock_fit_rank_working)
        adapter.submitList(emptyList())
        refreshToolbarAnchorLabel()

        lifecycleScope.launch {
            try {
                val (rows, statusMsg) = withContext(Dispatchers.IO) {
                    buildRanking(anchorCode, start, end, useNet)
                }
                adapter.submitList(rows)
                binding.statusTv.text = statusMsg
            } catch (e: Throwable) {
                binding.statusTv.text = getString(R.string.stock_fit_rank_error, e.message ?: "")
                e.printStackTrace()
            } finally {
                binding.queryBtnBorderLoading.setLoading(false)
                binding.runBtn.isEnabled = true
                binding.endDatePrevDayBtn.isEnabled = true
                binding.endDateNextDayBtn.isEnabled = true
            }
        }
    }

    /**
     * 在 IO 线程：读库、可选补齐目标股、多协程并行计算；返回列表已排序。
     */
    private suspend fun buildRanking(
        anchor: String,
        start: Int,
        end: Int,
        useNet: Boolean,
    ): Pair<List<FitRankRow>, String> {
        val t0 = System.currentTimeMillis()
        val dao = Injector.appDatabase.historyStockDao()
        val stockDao = Injector.appDatabase.stockDao()

        val allStocks = try {
            stockDao.getAllStock()
        } catch (_: Throwable) {
            emptyList()
        }
        if (allStocks.isEmpty()) {
            return emptyList<FitRankRow>() to getString(R.string.stock_fit_rank_empty_stock_master)
        }

        val names = allStocks.associate { it.code to it.name }
        val nonStCodes = allStocks.filter { !it.isST() }.map { it.code }.toMutableSet()
        val codesToQuery = nonStCodes.toMutableSet()
        codesToQuery.add(anchor)

        val allRows = buildList {
            for (chunk in codesToQuery.chunked(HISTORY_CODES_CHUNK)) {
                if (chunk.isNotEmpty()) {
                    addAll(dao.getHistoryByDateForCodes(start, end, chunk))
                }
            }
        }
        if (allRows.isEmpty()) {
            return emptyList<FitRankRow>() to getString(R.string.stock_fit_rank_empty_range)
        }

        val byCode = allRows.groupBy { it.code }
            .mapValues { (_, v) -> v.sortedBy { it.date } }
            .toMutableMap()

        var anchorHist = byCode[anchor].orEmpty()
        if (useNet && anchorHist.size < MIN_ANCHOR_ROWS) {
            val net = StockRepo.fetchStockDayKlineRange(anchor, start, end)
            if (net.isNotEmpty()) {
                dao.insertHistory(net)
                anchorHist = net.sortedBy { it.date }
                byCode[anchor] = anchorHist
            }
        }

        if (anchorHist.size < MIN_ANCHOR_ROWS) {
            return emptyList<FitRankRow>() to getString(
                R.string.stock_fit_rank_anchor_short,
                anchorHist.size,
                MIN_ANCHOR_ROWS,
            )
        }

        val anchorByDate = anchorHist.associateBy { it.date }

        val others = byCode.keys.filter { it != anchor && it in nonStCodes }

        val pairs = parallelCompare(anchorByDate, byCode, others)


        val sortedPairs = pairs.sortedWith(
            compareByDescending<Pair<String, StockReturnCorrelation.FitResult>> { (_, r) ->
                fitSortScore(r)
            }.thenBy { it.first },
        )
        val topPairs = sortedPairs.take(DISPLAY_LIMIT)
        val displayed = topPairs.mapIndexed { index, (code, r) ->
            FitRankRow(
                rank = index + 1,
                code = code,
                name = names[code].orEmpty().ifEmpty { code },
                result = r,
            )
        }

        val elapsedMs = System.currentTimeMillis() - t0
        val msg = getString(
            R.string.stock_fit_rank_done,
            others.size,
            sortedPairs.size,
            displayed.size,
            elapsedMs,
        )
        return displayed to msg
    }

    private suspend fun parallelCompare(
        anchorByDate: Map<Int, HistoryStock>,
        byCode: Map<String, List<HistoryStock>>,
        others: List<String>,
    ): List<Pair<String, StockReturnCorrelation.FitResult>> =
        withContext(Dispatchers.Default) {
            if (others.isEmpty()) return@withContext emptyList()
            val parallelism = 8.coerceAtMost((others.size + 31) / 32)
            val chunkSize = (others.size + parallelism - 1) / parallelism
            coroutineScope {
                others.chunked(chunkSize.coerceAtLeast(1)).map { chunk ->
                    async {
                        chunk.mapNotNull { code ->
                            val hist = byCode[code] ?: return@mapNotNull null
                            if (hist.size < 5) return@mapNotNull null
                            code to StockReturnCorrelation.computeWithAnchorByDate(
                                anchorByDate,
                                hist,
                            )
                        }
                    }
                }.awaitAll().flatten()
            }
        }

    private fun fitSortScore(r: StockReturnCorrelation.FitResult): Double {
        r.pearson?.let { return abs(it) }
        r.spearman?.let { return abs(it) }
        return -1.0
    }

    data class FitRankRow(
        val rank: Int,
        val code: String,
        val name: String,
        val result: StockReturnCorrelation.FitResult,
    )

    private class FitRankAdapter(
        private val onItemClick: (FitRankRow) -> Unit,
    ) : ListAdapter<FitRankRow, FitRankAdapter.VH>(DIFF) {

        object DIFF : DiffUtil.ItemCallback<FitRankRow>() {
            override fun areItemsTheSame(a: FitRankRow, b: FitRankRow) = a.code == b.code
            override fun areContentsTheSame(a: FitRankRow, b: FitRankRow) = a == b
        }

        class VH(val binding: ItemStockFitRankBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemStockFitRankBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            val r = item.result
            val ctx = holder.itemView.context
            holder.binding.rankTv.text = item.rank.toString()
            when (item.rank) {
                1, 2, 3 -> {
                    holder.binding.rankTv.setBackgroundResource(R.drawable.bg_fit_rank_badge_top)
                    holder.binding.rankTv.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                }
                else -> {
                    holder.binding.rankTv.setBackgroundResource(R.drawable.bg_fit_rank_badge)
                    holder.binding.rankTv.setTextColor(ContextCompat.getColor(ctx, R.color.yd_body))
                }
            }
            holder.binding.nameTv.text = item.name
            holder.binding.codeTv.text = item.code
            val rho = r.pearson ?: r.spearman
            holder.binding.rhoTv.text = rho?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
            holder.binding.rhoTv.setTextColor(
                when {
                    rho == null -> ContextCompat.getColor(ctx, R.color.fit_rho_muted)
                    abs(rho) >= 0.7 -> ContextCompat.getColor(ctx, R.color.fit_rho_strong)
                    abs(rho) >= 0.4 -> ContextCompat.getColor(ctx, R.color.light_blue_600)
                    else -> ContextCompat.getColor(ctx, R.color.fit_rho_muted)
                },
            )
            val pStr = r.pearson?.let { String.format(Locale.US, "%.3f", it) } ?: "—"
            val sStr = r.spearman?.let { String.format(Locale.US, "%.3f", it) } ?: "—"
            val agree = r.signAgreement?.let { String.format(Locale.US, "%.0f%%", it * 100) } ?: "—"
            holder.binding.detailTv.text = buildString {
                append("Pearson ").append(pStr)
                append(" · Spearman ").append(sStr)
                append(" · 同向 ").append(agree)
                append(" · 对齐 ").append(r.tradingDaysAligned).append(" 日")
                append(" · ").append(r.fitLabel)
            }
            holder.binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        const val EXTRA_CODE = "code"
        private const val DEFAULT_LOOKBACK_DAYS = 90
        /** 粗略约束 8 位 yyyyMMdd，避免误输入 */
        private const val MIN_END_YMD = 1990_01_01
        private const val MAX_END_YMD = 2100_12_31
        private const val MAX_LOOKBACK_DAYS = 365 * 20
        private const val MIN_ANCHOR_ROWS = 12
        /** Room/SQLite 单次 IN 列表安全长度 */
        private const val HISTORY_CODES_CHUNK = 450
        /** 列表最多展示条数 */
        private const val DISPLAY_LIMIT = 500

        fun start(context: Context, code: String? = null) {
            val i = Intent(context, StockFitRankingActivity::class.java)
            if (!code.isNullOrBlank()) i.putExtra(EXTRA_CODE, code.trim())
            context.startActivity(i)
        }
    }
}
