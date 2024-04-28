package com.liaobusi.stockman

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.liaobusi.stockman.databinding.ActivityAnalysisBinding
import com.liaobusi.stockman.databinding.ActivityBkstrategyBinding
import com.liaobusi.stockman.databinding.ActivitySettingBinding
import com.liaobusi.stockman.databinding.ItemAnalysisBinding
import com.liaobusi.stockman.db.AnalysisBean
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date


fun isTradingDay(date: Int): Boolean {
    val dao = Injector.appDatabase.historyBKDao()
    return dao.getHistoryByDate2("000001", date) != null
}

fun nextTradingDay(date: Int): Int {
    var date2 = date
    while (true) {
        val d = SimpleDateFormat("yyyyMMdd").parse(date2.toString())
        val cal = Calendar.getInstance()
        cal.apply { timeInMillis = d.time }
            .add(Calendar.DAY_OF_MONTH, 1)
        val s = SimpleDateFormat("yyyyMMdd").format(cal.time)
        if (isTradingDay(s.toInt())) {
            return s.toInt()
        }
        date2 = s.toInt()
    }
}


fun preTradingDay(date: Int): Int {
    var date2 = date
    val today = today()
    while (true) {
        val d = SimpleDateFormat("yyyyMMdd").parse(date2.toString())
        val cal = Calendar.getInstance()
        cal.apply { timeInMillis = d.time }
            .add(Calendar.DAY_OF_MONTH, -1)
        val s = SimpleDateFormat("yyyyMMdd").format(cal.time)
        if (isTradingDay(s.toInt())) {
            return s.toInt()
        }
        date2 = s.toInt()
        if (date2 >= today) {
            return today
        }

    }
}

class AnalysisActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalysisBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        supportActionBar?.title = "大盘分析"

        binding.dateTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))

        binding.postBtn.setOnClickListener {
            val c = binding.dateTv.editableText.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                val next = nextTradingDay(c.toInt()).toString()
                launch(Dispatchers.Main) {
                    binding.dateTv.setText(next)
                    binding.confirmBtn.callOnClick()
                }
            }
        }

        binding.preBtn.setOnClickListener {

            val c = binding.dateTv.editableText.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                val pre = preTradingDay(c.toInt()).toString()
                launch(Dispatchers.Main) {
                    binding.dateTv.setText(pre)
                    binding.confirmBtn.callOnClick()
                }
            }

        }

        var job: Job? = null
        binding.confirmBtn.setOnClickListener {
            val date = binding.dateTv.editableText.toString().toIntOrNull()
            if (date != null) {
                job?.cancel()
                job = lifecycleScope.launch(Dispatchers.IO) {
                    val r = StockRepo.dpAnalysis(date)
                    launch(Dispatchers.Main) {
                        binding.resultLL.removeAllViews()
                        r.forEach { analysisResult ->
                            val itemAnalysisBinding =
                                ItemAnalysisBinding.inflate(LayoutInflater.from(this@AnalysisActivity))
                                    .apply {
                                        tv.text = analysisResult.content
                                        root.setOnClickListener {
                                            analysisResult.callback.invoke(this@AnalysisActivity)
                                        }
                                    }
                            binding.resultLL.addView(itemAnalysisBinding.root)
                        }
                    }
                }

            }

        }



        binding.chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            this.granularity = 1f
            this.setDrawGridLines(false)
            axisMinimum=0f
        }

        binding.chart.axisLeft.apply {
            this.setDrawGridLines(false)
            this.granularity = 1f
            this.setDrawZeroLine(true)
            this.axisMinimum = 0f
        }

        binding.chart.description.isEnabled = false
        binding.chart.axisRight.isEnabled = false





        lifecycleScope.launch(Dispatchers.IO) {
            val r = Injector.appDatabase.analysisBeanDao().getAnalysisBeans()
                .filter { isTradingDay(it.date) }
            outputChart(r)
        }


    }

    private fun outputChart(r: List<AnalysisBean>) {
        val ztEntries = mutableListOf<Entry>()
        val dtEntries = mutableListOf<Entry>()
        val highEntries = mutableListOf<Entry>()

        lifecycleScope.launch(Dispatchers.Main) {

            binding.ztCb.setOnCheckedChangeListener { buttonView, isChecked ->
                outputChart(r)
            }

            binding.dtCb.setOnCheckedChangeListener { buttonView, isChecked ->
                outputChart(r)
            }
            binding.highestZTCb.setOnCheckedChangeListener { buttonView, isChecked ->
                outputChart(r)
            }


            binding.chart.xAxis.apply {
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        if(value.toInt()>r.size){
                            return ""
                        }
                        return r[value.toInt()].date.toString()
                    }
                }
            }
            binding.chart.viewPortHandler.setMinimumScaleX(3.5f)

            r.forEachIndexed { index, item ->
                ztEntries.add(Entry(index.toFloat(), item.ztCount.toFloat()))
                dtEntries.add(Entry(index.toFloat(), item.dtCount.toFloat()))
                highEntries.add(Entry(index.toFloat(), item.highestLianBanCount.toFloat()))
            }

            val dataSet = LineDataSet(ztEntries, "涨停数")
            dataSet.color = Color.RED

            val dataSetDT = LineDataSet(dtEntries, "跌停数")
            dataSetDT.color = STOCK_GREEN

            val dataSetHigh = LineDataSet(highEntries, "最高板")
            dataSetHigh.color = Color.BLACK

            val l = mutableListOf<ILineDataSet>()
            if (binding.ztCb.isChecked) {
                l.add(dataSet)
            }

            if (binding.dtCb.isChecked) {
                l.add(dataSetDT)
            }

            if (binding.highestZTCb.isChecked) {
                l.add(dataSetHigh)
            }
            val lineData = LineData(l)
            binding.chart.data = lineData
         //   binding.chart.viewPortHandler.setZoom(4f,8f)
            binding.chart.invalidate()



        }


    }


}

data class ChartBean(val date: Int, val count: Int)