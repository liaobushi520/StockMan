package com.liaobusi.stockman
import com.liaobusi.stockman5.R

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman5.databinding.ActivityStockPairFitBinding
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

/**
 * 测试页：两股区间日 K 对齐，对数收益相关性与拟合度指标。
 */
class StockPairFitActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockPairFitBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockPairFitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.stock_pair_fit_title)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val end = today()
        binding.endDateEt.setText(end.toString())
        binding.startDateEt.setText(Date().after(-120).toString())
        binding.codeAEt.setText("600519")
        binding.codeBEt.setText("000858")

        binding.runBtn.setOnClickListener { runFit() }
    }

    private fun runFit() {
        val codeA = binding.codeAEt.text?.toString()?.trim()?.uppercase(Locale.getDefault()).orEmpty()
        val codeB = binding.codeBEt.text?.toString()?.trim()?.uppercase(Locale.getDefault()).orEmpty()
        val start = binding.startDateEt.text?.toString()?.trim()?.toIntOrNull()
        val end = binding.endDateEt.text?.toString()?.trim()?.toIntOrNull()
        val useNet = binding.networkCb.isChecked

        if (codeA.isEmpty() || codeB.isEmpty()) {
            Toast.makeText(this, "请填写两只股票代码", Toast.LENGTH_SHORT).show()
            return
        }
        if (start == null || end == null || start > end) {
            Toast.makeText(this, "日期为 8 位整数，且开始≤结束", Toast.LENGTH_SHORT).show()
            return
        }

        binding.runBtn.isEnabled = false
        binding.resultTv.text = getString(R.string.stock_pair_fit_computing)

        lifecycleScope.launch {
            try {
                val resultText = withContext(Dispatchers.IO) {
                    val dao = Injector.appDatabase.historyStockDao()
                    val hA = if (useNet) {
                        val net = StockRepo.fetchStockDayKlineRange(codeA, start, end)
                        if (net.isNotEmpty()) dao.insertHistory(net)
                        net.ifEmpty { dao.getHistoryRange(codeA, start, end).sortedBy { it.date } }
                    } else {
                        dao.getHistoryRange(codeA, start, end).sortedBy { it.date }
                    }
                    val hB = if (useNet) {
                        val net = StockRepo.fetchStockDayKlineRange(codeB, start, end)
                        if (net.isNotEmpty()) dao.insertHistory(net)
                        net.ifEmpty { dao.getHistoryRange(codeB, start, end).sortedBy { it.date } }
                    } else {
                        dao.getHistoryRange(codeB, start, end).sortedBy { it.date }
                    }
                    val result = StockReturnCorrelation.compute(hA, hB)
                    formatResult(codeA, codeB, start, end, useNet, hA.size, hB.size, result)
                }
                binding.resultTv.text = resultText
            } catch (e: Throwable) {
                binding.resultTv.text = "出错：${e.message}"
                e.printStackTrace()
            } finally {
                binding.runBtn.isEnabled = true
            }
        }
    }

    private fun formatResult(
        codeA: String,
        codeB: String,
        start: Int,
        end: Int,
        useNet: Boolean,
        nA: Int,
        nB: Int,
        r: StockReturnCorrelation.FitResult,
    ): String {
        val sb = StringBuilder()
        sb.append("区间 ").append(start).append(" — ").append(end).append('\n')
        sb.append("A=").append(codeA).append(" 本地条数=").append(nA).append('\n')
        sb.append("B=").append(codeB).append(" 本地条数=").append(nB).append('\n')
        sb.append("数据来源=").append(if (useNet) "网络优先并写入库" else "仅本地库").append('\n')
        sb.append("对齐交易日数=").append(r.tradingDaysAligned).append('\n')
        sb.append("对数收益样本=").append(r.returnSamples).append('\n')
        sb.append("— — —\n")
        sb.append("Pearson ρ=").append(r.pearson?.let { String.format(Locale.US, "%.4f", it) } ?: "—").append('\n')
        sb.append("Spearman ρ=").append(r.spearman?.let { String.format(Locale.US, "%.4f", it) } ?: "—").append('\n')
        sb.append("涨跌符号一致率=").append(
            r.signAgreement?.let { String.format(Locale.US, "%.2f%%", it * 100) } ?: "—",
        ).append('\n')
        sb.append("OLS β(A~B)=").append(r.betaYOnX?.let { String.format(Locale.US, "%.4f", it) } ?: "—")
            .append("  R²=").append(r.rSquared?.let { String.format(Locale.US, "%.4f", it) } ?: "—").append('\n')
        sb.append("区间简单涨跌 A=")
            .append(r.totalReturnA?.let { String.format(Locale.US, "%.2f%%", it * 100) } ?: "—")
        sb.append("  B=")
            .append(r.totalReturnB?.let { String.format(Locale.US, "%.2f%%", it * 100) } ?: "—")
        sb.append('\n')
        sb.append("结论：").append(r.fitLabel)
        return sb.toString()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, StockPairFitActivity::class.java))
        }
    }
}
