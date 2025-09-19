package com.liaobusi.stockman

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.liaobusi.stockman.BKStrategyActivity
import com.liaobusi.stockman.Strategy4Activity.Companion.openJXQSStrategy
import com.liaobusi.stockman.databinding.ActivityStrategy4Binding
import com.liaobusi.stockman.databinding.FragmentDiyBkBinding
import com.liaobusi.stockman.databinding.FragmentStockInfoBinding
import com.liaobusi.stockman.databinding.ItemDiyBkBinding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.databinding.ItemStockInfoBkBinding
import com.liaobusi.stockman.databinding.LayoutPopupWindow2Binding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.*
import com.liaobusi.stockman.repo.*
import kotlinx.coroutines.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.min

val colors = listOf(
    Color.parseColor("#1565c0"), Color.parseColor("#ad1457"),
    Color.parseColor("#283593"), Color.parseColor("#b71c1c"), Color.parseColor("#009688"),
    Color.parseColor("#795548"), Color.parseColor("#e65100")
)

/**
 * 均线强势
 */
class Strategy4Activity : AppCompatActivity() {

    companion object {

        fun openJXQSStrategy(context: Context, bkCode: String, endTime: String) {
            val i = Intent(
                context,
                Strategy4Activity::class.java
            ).apply {
                putExtra("bk", bkCode)
                putExtra("endTime", endTime)
            }
            context.startActivity(i)
        }

        fun openJXQSStrategy2(context: Context, diyBk: DIYBk, endTime: String) {
            val i = Intent(
                context,
                Strategy4Activity::class.java
            ).apply {
                putExtra("diyBk", Gson().toJson(diyBk))
                putExtra("endTime", endTime)
            }
            context.startActivity(i)
        }
    }

    private lateinit var binding: ActivityStrategy4Binding

    private var diyBk: DIYBk? = null


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.page_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    StockRepo.refreshData()
                    val endTime = binding.endTimeTv.editableText.toString().toIntOrNull() ?: today()
                    StockRepo.fetchZTReplay2(date = endTime)
                    StockRepo.fetchDragonTigerRank(endTime)
                    StockRepo.getRealTimeIndexByCode("2.932000")
                    Injector.refreshPopularityRanking()
                    launch(Dispatchers.Main) {
                        delay(2000)
                        binding.chooseStockBtn.callOnClick()
                    }

                }
                return true
            }

        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategy4Binding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.rv.layoutManager = LinearLayoutManager(this@Strategy4Activity)
        binding.rv.adapter = ResultAdapter()
        supportActionBar?.title = "均线强势"


        var fromBKStrategyActivity = false
        if (intent.hasExtra("bk")) {
            val bk = intent.getStringExtra("bk")?.ifEmpty { "ALL" }
            binding.conceptAndBKTv.setText(bk)
            binding.lowMarketValue.setText("0.0")
            binding.highMarketValue.setText("1000000.0")
            binding.endMarketTime.setText(today().toString())
            fromBKStrategyActivity = true
        }

        if (intent.hasExtra("diyBk")) {
            val s = intent.getStringExtra("diyBk")?.ifEmpty { "ALL" }
            diyBk = Gson().fromJson<DIYBk>(s, DIYBk::class.java)
            binding.conceptAndBKTv.setText(diyBk?.bkCodes ?: "ALL")
            binding.lowMarketValue.setText("0.0")
            binding.highMarketValue.setText("1000000.0")
            binding.endMarketTime.setText(today().toString())
            fromBKStrategyActivity = true
        }

        if (intent.hasExtra("endTime")) {
            binding.endTimeTv.setText(intent.getStringExtra("endTime"))
        } else {
            binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))
            lifecycleScope.launch(Dispatchers.IO) {
                StockRepo.dpAnalysis(today())
            }
        }



        binding.line5Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy4Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val bkList = checkBKInput() ?: return@setOnClickListener


            val param = Strategy4Param(
                startMarketTime = 19900101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 100000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 100000000000000.0,
                range = 5,
                endTime = endTime,
                averageDay = 5,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 5 else 0,
                divergeRate = 0.00,
                abnormalRange = 5,
                abnormalRate = 2.0,
                bkList = bkList,
                stockList = Injector.getSnapshot()
            )
            updateUI(param)
            outputResult(param)
        }

        binding.line10Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy4Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val bkList = checkBKInput() ?: return@setOnClickListener

            val param = Strategy4Param(
                startMarketTime = 19900101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 100000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 100000000000000.0,
                range = 10,
                endTime = endTime,
                averageDay = 10,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 10 else 0,
                divergeRate = 0.00,
                abnormalRange = 10,
                abnormalRate = 2.0,
                bkList = bkList,
                stockList = Injector.getSnapshot()

            )
            updateUI(param)
            outputResult(param)
        }
        binding.line20Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy4Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy4Param(
                startMarketTime = 19910101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 100000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 100000000000000.0,
                range = 20,
                endTime = endTime,
                averageDay = 20,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 20 else 0,
                divergeRate = 0.00,
                abnormalRange = 15,
                abnormalRate = 2.0,
                bkList = bkList,
                stockList = Injector.getSnapshot()

            )
            updateUI(param)
            outputResult(param)
        }
        binding.line30Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy4Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy4Param(
                startMarketTime = 19910101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 100000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 100000000000000.0,
                range = 30,
                endTime = endTime,
                averageDay = 30,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 30 else 0,
                divergeRate = 0.00,
                abnormalRange = 15,
                abnormalRate = 2.0,
                bkList = bkList,
                stockList = Injector.getSnapshot()

            )
            updateUI(param)
            outputResult(param)
        }
        binding.line60Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy4Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy4Param(
                startMarketTime = 19910101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 100000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 100000000000000.0,
                range = 60,
                endTime = endTime,
                averageDay = 60,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 60 else 0,
                divergeRate = 0.00,
                abnormalRange = 20,
                abnormalRate = 2.0,
                bkList = bkList,
                stockList = Injector.getSnapshot()

            )
            updateUI(param)
            outputResult(param)
        }

        binding.preBtn.setOnClickListener {
            val c = binding.endTimeTv.editableText.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                val pre = preTradingDay(c.toInt()).toString()
                launch(Dispatchers.Main) {
                    binding.endTimeTv.setText(pre)
                    binding.chooseStockBtn.callOnClick()
                }
            }
        }


        binding.postBtn.setOnClickListener {
            val c = binding.endTimeTv.editableText.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                val next = nextTradingDay(c.toInt()).toString()
                launch(Dispatchers.Main) {
                    binding.endTimeTv.setText(next)
                    binding.chooseStockBtn.callOnClick()
                }
            }
        }

        var job: Job? = null

        binding.chooseStockBtn.setOnClickListener {
            job?.cancel()
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy4Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val startMarketTime = binding.startMarketTime.editableText.toString().toIntOrNull()
            if (startMarketTime == null) {
                Toast.makeText(this@Strategy4Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val endMarketTime = binding.endMarketTime.editableText.toString().toIntOrNull()
            if (endMarketTime == null) {
                Toast.makeText(this@Strategy4Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lowMarketValue = binding.lowMarketValue.editableText.toString().toDoubleOrNull()

            val highMarketValue = binding.highMarketValue.editableText.toString().toDoubleOrNull()

            if (lowMarketValue == null || highMarketValue == null || lowMarketValue > highMarketValue) {
                Toast.makeText(this@Strategy4Activity, "市值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val timeRange = binding.timeRangeTv.editableText.toString().toIntOrNull()
            if (timeRange == null) {
                Toast.makeText(this@Strategy4Activity, "查找区间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (binding.onlyActiveRateCb.isChecked) {
                binding.divergeRateTv.setText("0.0")
                binding.allowBelowCountTv.setText(timeRange.toString())
            }

//            else {
//                binding.divergeRateTv.setText("0.0")
//                binding.allowBelowCountTv.setText("0")
//            }

            val allowBelowCount =
                binding.allowBelowCountTv.editableText.toString().toIntOrNull()
            if (allowBelowCount == null) {
                Toast.makeText(
                    this@Strategy4Activity,
                    "允许均线下方运行次数不合法",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val averageDay =
                binding.averageDayTv.editableText.toString().toIntOrNull()
            if (averageDay == null) {
                Toast.makeText(this@Strategy4Activity, "均线取值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val divergeRate =
                binding.divergeRateTv.editableText.toString().toDoubleOrNull()
            if (divergeRate == null) {
                Toast.makeText(
                    this@Strategy4Activity,
                    "收盘价与均线偏差率取值不合法",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val abnormalRange =
                binding.abnormalRangeTv.editableText.toString().toIntOrNull()
            if (abnormalRange == null) {
                Toast.makeText(this@Strategy4Activity, "异常放量查找区间不合法", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            val abnormalRate =
                binding.abnormalRateTv.editableText.toString().toDoubleOrNull()
            if (abnormalRate == null) {
                Toast.makeText(this@Strategy4Activity, "异常放量倍数不合法", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            job = lifecycleScope.launch(Dispatchers.IO) {
                var stockList = mutableListOf<Stock>()
                if (diyBk != null && diyBk?.stockCodes?.isNotEmpty() == true) {
                    val list =
                        Injector.appDatabase.stockDao()
                            .getStockByCodes(diyBk!!.stockCodes.split(","))
                    stockList.addAll(list)
                }

                if (Injector.getSnapshot().isNotEmpty() == true) {
                    stockList.addAll(Injector.getSnapshot())
                }


                val list = StockRepo.strategy4(
                    startMarketTime = startMarketTime,
                    endMarketTime = endMarketTime,
                    lowMarketValue = lowMarketValue * 100000000,
                    highMarketValue = highMarketValue * 100000000,
                    range = timeRange,
                    endTime = endTime,
                    allowBelowCount = allowBelowCount,
                    averageDay = averageDay,
                    divergeRate = divergeRate / 100,
                    abnormalRange = abnormalRange,
                    abnormalRate = abnormalRate,
                    bkList = bkList,
                    stockList = stockList
                )
                if (!isActive) return@launch

                list.zz2000 =
                    Injector.appDatabase.historyBKDao().getHistoryByDate3("932000", date = endTime)
                output(list)
            }

        }


        binding.onlyActiveRateCb.setOnCheckedChangeListener { buttonView, isChecked ->
            binding.chooseStockBtn.callOnClick()
        }

        if (Injector.getSnapshot().isNotEmpty()) {
            binding.snapshotBtn.text = "取消快照"
        } else {
            binding.snapshotBtn.text = "保存当前快照"
        }

        binding.snapshotBtn.setOnClickListener {
            if (binding.snapshotBtn.text.toString() == "保存当前快照") {
                Injector.takeSnapshot(
                    (binding.rv.adapter as? ResultAdapter)?.getStockList() ?: listOf()
                )
                binding.snapshotBtn.text = "取消快照"
            } else {
                Injector.deleteSnapshot()
                binding.snapshotBtn.text = "保存当前快照"
            }

        }

        binding.stCb.isChecked = isShowST(this)




        binding.startBtn.setOnClickListener {

            binding.root.requestFocus()

            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy4Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val abnormalDay =
                binding.abnormalDayTv.editableText.toString().toIntOrNull()
            if (abnormalDay == null) {
                Toast.makeText(this@Strategy4Activity, "统计区间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val rankCount =
                binding.rankCountTv.editableText.toString().toIntOrNull()
            if (rankCount == null) {
                Toast.makeText(this@Strategy4Activity, "数据值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val startMarketTime = binding.startMarketTime.editableText.toString().toIntOrNull()
            if (startMarketTime == null) {
                Toast.makeText(this@Strategy4Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val endMarketTime = binding.endMarketTime.editableText.toString().toIntOrNull()
            if (endMarketTime == null) {
                Toast.makeText(this@Strategy4Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lowMarketValue = binding.lowMarketValue.editableText.toString().toDoubleOrNull()

            val highMarketValue = binding.highMarketValue.editableText.toString().toDoubleOrNull()

            if (lowMarketValue == null || highMarketValue == null || lowMarketValue > highMarketValue) {
                Toast.makeText(this@Strategy4Activity, "市值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val timeRange = binding.timeRangeTv.editableText.toString().toIntOrNull()
            if (timeRange == null) {
                Toast.makeText(this@Strategy4Activity, "查找区间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val allowBelowCount =
                binding.allowBelowCountTv.editableText.toString().toIntOrNull()
            if (allowBelowCount == null) {
                Toast.makeText(
                    this@Strategy4Activity,
                    "允许均线下方运行次数不合法",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val averageDay =
                binding.averageDayTv.editableText.toString().toIntOrNull()
            if (averageDay == null) {
                Toast.makeText(this@Strategy4Activity, "均线取值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val divergeRate =
                binding.divergeRateTv.editableText.toString().toDoubleOrNull()
            if (divergeRate == null) {
                Toast.makeText(
                    this@Strategy4Activity,
                    "收盘价与均线偏差率取值不合法",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val abnormalRange =
                binding.abnormalRangeTv.editableText.toString().toIntOrNull()
            if (abnormalRange == null) {
                Toast.makeText(this@Strategy4Activity, "异常放量查找区间不合法", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            val abnormalRate =
                binding.abnormalRateTv.editableText.toString().toDoubleOrNull()
            if (abnormalRate == null) {
                Toast.makeText(this@Strategy4Activity, "异常放量倍数不合法", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener

            val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
            val jobList = mutableListOf<Deferred<List<StockResult>>>()
            lifecycleScope.launch(Dispatchers.IO) {
                val map = mutableMapOf<String, StockResult>()
                for (day in 0 until abnormalDay) {
                    val time = endDay.before(day)
                    val j = async {
                        val r = StockRepo.strategy4(
                            startMarketTime = startMarketTime,
                            endMarketTime = endMarketTime,
                            lowMarketValue = lowMarketValue * 100000000,
                            highMarketValue = highMarketValue * 100000000,
                            range = timeRange,
                            endTime = time,
                            allowBelowCount = allowBelowCount,
                            averageDay = averageDay,
                            divergeRate = divergeRate / 100,
                            abnormalRange = abnormalRange,
                            abnormalRate = abnormalRate,
                            bkList = bkList,
                            stockList = Injector.getSnapshot()
                        )
                        return@async mutableListOf<StockResult>().apply {
                            addAll(r.stockResults)
                        }
                    }
                    jobList.add(j)
                }
                val totalList = jobList.awaitAll()
                totalList.flatten().forEach {
                    if (map.containsKey(it.stock.code)) {
                        val item = map[it.stock.code]
                        item!!.activeCount += it.signalCount
                    } else {
                        it.activeCount = it.signalCount
                        map[it.stock.code] = it
                    }
                }
                val l = map.values.toList()
                Collections.sort(l, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.activeCount - v0.activeCount
                })

                val ll = l.subList(0, min(l.size, rankCount))
                output(StrategyResult(ll, -1))
            }


        }

        binding.followBkCb.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val followBKs = Injector.appDatabase.bkDao().getFollowedBKS()
                    val sb = StringBuilder()
                    followBKs.forEach {
                        sb.append(it.code + ",")
                    }
                    var s = sb.dropLastWhile { it == ',' }
                    if (s.isNullOrEmpty()) {
                        s = "ALL"
                    }
                    launch(Dispatchers.Main) {
                        binding.conceptAndBKTv.setText(s.toString())
                        binding.chooseStockBtn.callOnClick()
                    }
                }
            } else {
                binding.conceptAndBKTv.setText("ALL")
                binding.chooseStockBtn.callOnClick()
            }

        }

        binding.line5Btn.callOnClick()


    }


    private fun updateUI(param: Strategy4Param) {
        binding.apply {
            this.endTimeTv.setText(param.endTime.toString())
            this.startMarketTime.setText(param.startMarketTime.toString())
            this.endMarketTime.setText(param.endMarketTime.toString())
            this.lowMarketValue.setText((param.lowMarketValue / 100000000).toString())
            this.highMarketValue.setText((param.highMarketValue / 100000000).toString())
            this.timeRangeTv.setText(param.range.toString())
            this.allowBelowCountTv.setText(param.allowBelowCount.toString())
            this.averageDayTv.setText(param.averageDay.toString())
            this.divergeRateTv.setText((param.divergeRate * 100).toString())
            this.abnormalRangeTv.setText(param.abnormalRange.toString())
            this.abnormalRateTv.setText(param.abnormalRate.toString())
        }
    }

    private fun outputResult(strictParam: Strategy4Param) {
        lifecycleScope.launch(Dispatchers.IO) {
            var stockList = mutableListOf<Stock>()
            if (diyBk != null && diyBk?.stockCodes?.isNotEmpty() == true) {
                val list =
                    Injector.appDatabase.stockDao().getStockByCodes(diyBk!!.stockCodes.split(","))
                stockList.addAll(list)
            }

            if (strictParam.stockList?.isNotEmpty() == true) {
                stockList.addAll(strictParam.stockList)
            }


            val list = StockRepo.strategy4(
                startMarketTime = strictParam.startMarketTime,
                endMarketTime = strictParam.endMarketTime,
                lowMarketValue = strictParam.lowMarketValue,
                highMarketValue = strictParam.highMarketValue,
                endTime = strictParam.endTime,
                range = strictParam.range,
                allowBelowCount = strictParam.allowBelowCount,
                averageDay = strictParam.averageDay,
                divergeRate = strictParam.divergeRate,
                abnormalRate = strictParam.abnormalRate,
                abnormalRange = strictParam.abnormalRange,
                bkList = strictParam.bkList,
                stockList = stockList
            )
            list.zz2000 =
                Injector.appDatabase.historyBKDao()
                    .getHistoryByDate3("932000", date = strictParam.endTime)
            output(list)
        }
    }

    private fun checkBKInput(): List<String>? {
        val conceptAndBK =
            binding.conceptAndBKTv.editableText.toString()
        if (conceptAndBK == "ALL") {
            return listOf()
        }

        val l = conceptAndBK.split(",").map {
            it.trim()
        }

        var bkInputError = false
        run run@{
            l.forEach {
                if (!it.startsWith("BK")) {
                    bkInputError = true
                }
            }
        }
        if (bkInputError) {
            Toast.makeText(this@Strategy4Activity, "板块不合法,BK开头,逗号分割", Toast.LENGTH_LONG)
                .show()
            return null

        }
        return l
    }

    private fun output(strategyResult: StrategyResult) {
        val list = strategyResult.stockResults

        lifecycleScope.launch(Dispatchers.Main) {

            binding.mainBoardCb.setOnCheckedChangeListener { compoundButton, b ->
                output(strategyResult)
            }

            binding.changyebanCb.setOnCheckedChangeListener { compoundButton, b ->
                output(strategyResult)
            }

            binding.bjsCb.setOnCheckedChangeListener { compoundButton, b ->
                output(strategyResult)
            }

            binding.starCb.setOnCheckedChangeListener { compoundButton, b ->
                output(strategyResult)
            }

            binding.stCb.setOnCheckedChangeListener { buttonView, isChecked ->
                output(strategyResult)
            }


            //活跃度
            binding.activityLevelCb.setOnCheckedChangeListener { compoundButton, b ->
                if (b) {
                    binding.zfSortCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                }
                output(strategyResult)
            }

            //东热
            binding.popularitySortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zfSortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                }
                output(strategyResult)
            }


            //大智慧热度
            binding.dzhPopularitySortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zfSortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                }
                output(strategyResult)
            }

            //淘股吧热
            binding.tgbPopularitySortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zfSortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                }
                output(strategyResult)
            }

            //同花顺人气
            binding.thsPopularitySortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zfSortCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                }
                output(strategyResult)
            }

            //涨幅
            binding.zfSortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                }
                output(strategyResult)
            }


            //仅涨停
            binding.onlyZTCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.onlyDTCb.isChecked = false
                }
                output(strategyResult)
            }

            //仅跌停
            binding.onlyDTCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.onlyZTCb.isChecked = false
                }
                output(strategyResult)
            }

            //龙虎榜
            binding.dragonTigerCb.setOnCheckedChangeListener { buttonView, isChecked ->
                output(strategyResult)
            }


            binding.zhongjunCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.xiaopiaoCb.isChecked = false
                }
                output(strategyResult)

            }

            binding.xiaopiaoCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.zhongjunCb.isChecked = false
                }
                output(strategyResult)
            }

            binding.ztPromotionCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.groupCb.isChecked = false
                }
                output(strategyResult)
            }

            binding.groupCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.ztPromotionCb.isChecked = false
                }
                output(strategyResult)
            }

            binding.cowBackCb.setOnCheckedChangeListener { compoundButton, b ->
                output(strategyResult)
            }

            binding.gdrsCb.setOnCheckedChangeListener { buttonView, isChecked ->
                output(strategyResult)
            }

            binding.noZTInRangeCb.setOnCheckedChangeListener { buttonView, isChecked ->
                output(strategyResult)
            }


            var r = list
            r = mutableListOf<StockResult>().apply { addAll(r) }


            //-----------股票市场过滤------------

            if (!binding.mainBoardCb.isChecked) {
                r = r.filter { return@filter !it.stock.isMainBoard() }.toMutableList()
            }


            if (!binding.changyebanCb.isChecked) {
                r = r.filter { return@filter !it.stock.isChiNext() }.toMutableList()
            }

            if (!binding.bjsCb.isChecked) {
                r = r.filter { return@filter !it.stock.isBJStockExchange() }.toMutableList()
            }

            if (!binding.starCb.isChecked) {
                r = r.filter { return@filter !it.stock.isSTARMarket() }.toMutableList()
            }

            if (!binding.stCb.isChecked) {
                r = r.filter {
                    return@filter !it.stock.isST()
                }
            }


            //-----------股票市场过滤------------

            if (binding.onlyZTCb.isChecked) {
                r = r.filter { return@filter it.zt }
            }

            if (binding.onlyDTCb.isChecked) {
                r = r.filter { return@filter it.dt }
            }

            if (binding.dragonTigerCb.isChecked) {
                r = r.filter { return@filter it.dargonTigerRank != null }
            }




            if (binding.cowBackCb.isChecked) {
                r = r.filter { return@filter it.cowBack }
            }

            if (binding.noZTInRangeCb.isChecked) {
                r = r.filter { return@filter it.ztCountInRange == 0 }
            }

            if (binding.gdrsCb.isChecked) {
                val c = binding.gdrsCountTv.text.toString().toIntOrNull() ?: 5
                r = StockRepo.filterStockByGDRS(r, c)
            }

            if (binding.ztPromotionCb.isChecked) {
                r = r.filter { it.zt }.sortedByDescending { it.lianbanCount }
            }

            if (binding.zhongjunCb.isChecked) {
                r = r.filter { it.stock.circulationMarketValue >= 8000000000 }
            }

            if (binding.xiaopiaoCb.isChecked) {
                r = r.filter { it.stock.circulationMarketValue < 8000000000 }
            }


            if (binding.activityLevelCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }.forEach {
                        newList.addAll(it.second.sortedByDescending { it.activeRate })
                    }
                    r = newList
                } else {
                    Collections.sort(r, kotlin.Comparator { v0, v1 ->
                        return@Comparator v1.activeRate.compareTo(v0.activeRate)
                    })
                }
            }

            if (binding.zfSortCb.isChecked) {

                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedByDescending { it.stock.chg ?: -1000f })
                        }
                    r = newList
                } else {
                    Collections.sort(r, kotlin.Comparator { v0, v1 ->
                        return@Comparator v1.currentDayHistory!!.chg.compareTo(v0.currentDayHistory!!.chg)
                    })
                }


            }

            if (binding.popularitySortCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.filter { it.popularity != null && it.popularity!!.rank > 0 }
                        .groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedBy { it.popularity?.rank ?: 1000 })
                        }
                    r = newList
                } else {
                    r = r.filter { it.popularity != null && it.popularity!!.rank > 0 }
                        .sortedBy { it.popularity?.rank ?: 1000 }
                }
            }

            if (binding.thsPopularitySortCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.filter { it.popularity != null && it.popularity!!.thsRank > 0 }
                        .groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedBy { it.popularity?.thsRank ?: 1000 })
                        }
                    r = newList
                } else {
                    r = r.filter { it.popularity != null && it.popularity!!.thsRank > 0 }
                        .sortedBy { it.popularity?.thsRank ?: 1000 }
                }
            }

            if (binding.tgbPopularitySortCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.filter { it.popularity != null && it.popularity!!.tgbRank > 0 }
                        .groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedBy { it.popularity?.tgbRank ?: 1000 })
                        }
                    r = newList
                } else {
                    r = r.filter { it.popularity != null && it.popularity!!.tgbRank > 0 }
                        .sortedBy { it.popularity?.tgbRank ?: 1000 }
                }
            }

            if (binding.dzhPopularitySortCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.filter { it.popularity != null && it.popularity!!.dzhRank > 0 }
                        .groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedBy { it.popularity?.dzhRank ?: 1000 })
                        }
                    r = newList
                } else {
                    r = r.filter { it.popularity != null && it.popularity!!.dzhRank > 0 }
                        .sortedBy { it.popularity?.dzhRank ?: 1000 }
                }
            }


            val ll = mutableListOf<StockResult>()
            var groupHeaderCount = 0
            if (binding.groupCb.isChecked) {
                r = r.filter { it.zt }
                var i = 0
                val listPair =
                    r.groupBy { it.ztReplay?.groupNameV ?: "" }.values.toMutableList().map {
                        var h = 1
                        it.forEach {
                            if (it.lianbanCount > h) {
                                h = it.lianbanCount
                            }
                        }
                        Pair(h * 100 + it.size, it)
                    }
                Collections.sort(listPair, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.first.compareTo(
                        v0.first
                    )
                })


                listPair.forEach { pair ->
                    val value = pair.second


                    Collections.sort(value, kotlin.Comparator { v0, v1 ->
                        val x1 =
                            if (binding.tgbPopularitySortCb.isChecked) (100f - (v1.popularity?.tgbRank
                                ?: 100))
                            else if (binding.popularitySortCb.isChecked) (300f - (v1.popularity?.rank
                                ?: 300))
                            else if (binding.dzhPopularitySortCb.isChecked) (100f - (v1.popularity?.dzhRank
                                ?: 100))
                            else if (binding.thsPopularitySortCb.isChecked) (100f - (v1.popularity?.thsRank
                                ?: 100))
                            else if (binding.activityLevelCb.isChecked) v1.activeRate
                            else if (binding.zfSortCb.isChecked) v1.stock.chg
                            else v1.ztTimeDigitization

                        val x0 =
                            if (binding.tgbPopularitySortCb.isChecked) (100f - (v0.popularity?.tgbRank
                                ?: 100))
                            else if (binding.popularitySortCb.isChecked) (300f - (v0.popularity?.rank
                                ?: 300))
                            else if (binding.dzhPopularitySortCb.isChecked) (100f - (v0.popularity?.dzhRank
                                ?: 100))
                            else if (binding.thsPopularitySortCb.isChecked) (100f - (v0.popularity?.thsRank
                                ?: 100))
                            else if (binding.activityLevelCb.isChecked) v0.activeRate
                            else if (binding.zfSortCb.isChecked) v0.stock.chg
                            else v0.ztTimeDigitization
                        return@Comparator (v1.lianbanCount * 1000 + x1).compareTo(
                            v0.lianbanCount * 1000 + x0
                        )
                    })
                    val color = colors[i % colors.size]
                    value.forEach {
                        it.groupColor = color
                    }
                    val fakeItem = value.first()
                    ll.add(
                        StockResult(
                            isGroupHeader = true,
                            groupColor = color,
                            stock = fakeItem.stock,
                            ztReplay = fakeItem.ztReplay ?: ZTReplayBean(
                                fakeItem.currentDayHistory!!.date,
                                fakeItem.stock.code,
                                "无",
                                "未分组",
                                "无",
                                "--:--:--"
                            )
                        )
                    )
                    groupHeaderCount++
                    ll.addAll(value)
                    i++
                }

                r = ll
            } else {
                r.forEach {
                    it.groupColor = Color.BLACK
                }
            }


            val newList = mutableListOf<StockResult>()
            r.forEach {
                if (it.follow?.stickyOnTop == 1) {
                    newList.add(0, it)
                } else {
                    newList.add(it)
                }

            }
            r = newList


            val strategyResult2 = StrategyResult(r, strategyResult.total)
            val s = if (strategyResult2.total > 0 && r.isNotEmpty()) {
                "拟合度${DecimalFormat("#.0").format((r.size - groupHeaderCount) * 100f / strategyResult2.total)}%"
            } else ""

            val resultText =
                SpannableStringBuilder().append("结果(${r.count { it.nextDayZT && !it.isGroupHeader }}/${r.size - groupHeaderCount}) ${s} ")
                    .append("涨跌停")
                    .append(
                        "" + r.count { it.zt && !it.isGroupHeader },
                        ForegroundColorSpan(Color.RED),
                        SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    .append(":")
                    .append(
                        "" + r.count { it.dt && !it.isGroupHeader },
                        ForegroundColorSpan(STOCK_GREEN),
                        SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE
                    ).append(
                        ("   " + strategyResult.zz2000?.chg?.toString()), ForegroundColorSpan(
                            strategyResult.zz2000?.color ?: Color.TRANSPARENT
                        ),
                        SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE
                    )
            binding.resultCount.text = resultText

            (binding.rv.adapter as ResultAdapter).setData(
                strategyResult2.stockResults.toMutableList(),
                if (binding.popularitySortCb.isChecked) 1 else if (binding.thsPopularitySortCb.isChecked) 2 else if (binding.tgbPopularitySortCb.isChecked) 3 else if (binding.dzhPopularitySortCb.isChecked) 4 else 0
            )
        }
    }


    inner class ResultAdapter() :
        RecyclerView.Adapter<ResultAdapter.VH>() {

        private val data = mutableListOf<StockResult>()

        private var popularitySort: Int = 0

        fun setData(data: MutableList<StockResult>, popularitySort: Int = 0) {
            this.data.clear()
            this.data.addAll(data)
            this.popularitySort = popularitySort
            notifyDataSetChanged()
        }


        fun getStockList(): List<Stock> {
            return data.map { it.stock }
        }


        private var job: Job? = null

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            job?.cancel()
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            job = lifecycleScope.launch(Dispatchers.IO) {
                while (true) {
                    delay(1200)
                    if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE || !Injector.activityActive) {
                        continue
                    }
                    val lm = recyclerView.layoutManager as LinearLayoutManager
                    val firstPos = lm.findFirstVisibleItemPosition()
                    val lastPos = lm.findLastVisibleItemPosition()
                    if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) {
                        continue
                    }


                    if (data.size > 0) {
                        for (i in firstPos until lastPos + 1) {

                            val result = data[i]
                            if (result.currentDayHistory != null && !result.isGroupHeader) {
                                val s =
                                    Injector.appDatabase.stockDao()
                                        .getStockByCode(result.stock.code)
                                val h = Injector.appDatabase.historyStockDao().getHistoryByDate3(
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
        }


        inner class VH(val binding: ItemStockBinding) : RecyclerView.ViewHolder(binding.root) {

            var anim: ValueAnimator? = null

            @RequiresApi(Build.VERSION_CODES.O)
            fun bind(result: StockResult, position: Int) {

                if (result.isGroupHeader) {
                    binding.root.setOnClickListener(null)
                    binding.root.setBackgroundColor(0xffffffff.toInt())
                    binding.colorView.visibility = View.GONE
                    binding.groupHeaderLL.visibility = View.VISIBLE
                    binding.dtTagsLL.visibility = View.GONE
                    binding.contentLL.visibility = View.GONE
                    binding.expoundTv.visibility = View.GONE
                    binding.dragonFlagIv.visibility = View.GONE
                    binding.popReasonTv.visibility = View.GONE
                    binding.stockName.setOnClickListener(null)
                    binding.groupHeaderTv.setTextColor(result.groupColor)
                    binding.groupHeaderTv2.setTextColor(result.groupColor)
                    if (result.ztReplay != null && result.ztReplay!!.groupNameV.isNotEmpty()) {
                        binding.groupHeaderTv.text = result.ztReplay!!.groupNameV
                        binding.groupHeaderTv.visibility = View.VISIBLE
                    } else {
                        binding.groupHeaderTv.visibility = View.GONE
                    }
                    if (result.ztReplay != null && result.ztReplay!!.reasonV.length > 1) {
                        binding.groupHeaderTv2.visibility = View.VISIBLE
                        binding.groupHeaderTv2.text = result.ztReplay!!.reasonV
                    } else {
                        binding.groupHeaderTv2.visibility = View.GONE
                    }

                    binding.root.setOnLongClickListener {
                        return@setOnLongClickListener true
                    }

                    return
                }



                binding.groupHeaderLL.visibility = View.GONE
                binding.contentLL.visibility = View.VISIBLE
                binding.stockName.setOnClickListener(null)
                binding.stockName.isClickable = false


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
                            "${result.ztReplay!!.time}\n${result.ztReplay!!.expoundV}"
                    } else {
                        this.expoundTv.visibility = View.GONE
                    }

                    //一字板
                    if (result.ztReplay != null && result.ztReplay!!.isYiZIBan) {
                        binding.yizibanView.visibility = View.VISIBLE
                    } else {
                        binding.yizibanView.visibility = View.GONE
                    }

                    binding.dtTagsLL.children.toList().forEach {
                        it.visibility = View.GONE
                    }
                    //龙虎榜游资机构买卖标签
                    if (result.dargonTigerRank?.tags?.isNotEmpty() == true) {
                        binding.dtTagsLL.visibility = View.VISIBLE
                        binding.tv0.visibility = View.GONE
                        val tags = result.dargonTigerRank?.tags
                        val tagList = tags!!.split(":")

                        tagList.forEachIndexed { index, item ->
                            if (index > 6) return@forEachIndexed
                            val child = binding.dtTagsLL.getChildAt(index) as TextView
                            child.text = item
                            if (item.contains("买")) {
                                child.setTextColor(Color.RED)
                            } else if (item.contains("卖")) {
                                child.setTextColor(STOCK_GREEN)
                            }
                            child.visibility = View.VISIBLE
                        }
                    } else {
                        binding.dtTagsLL.visibility = View.GONE
                    }


                    if (result.expandPOPReason && result.popularity != null) {
                        this.popReasonTv.visibility = View.VISIBLE
                        this.popReasonTv.text = "${result.popularity?.explain}"
                    } else {
                        this.popReasonTv.visibility = View.GONE
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


                    if (result.dargonTigerRank != null) {
                        this.dragonFlagIv.visibility = View.VISIBLE
                        this.stockName.setOnClickListener {
                            result.stock.openDragonTigerRank(this@Strategy4Activity)
                        }
                    } else {
                        this.dragonFlagIv.visibility = View.GONE
                    }

                    this.dragonFlagIv.setOnClickListener {
                        result.stock.openDragonTigerRank(this@Strategy4Activity)
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


                    val formatText = result.toFormatText()
                    if (formatText.isNotEmpty()) {
                        this.labelTv.visibility = View.VISIBLE
                        this.labelTv.text = formatText
                    } else {
                        this.labelTv.visibility = View.INVISIBLE
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
                        stock.openWeb(this@Strategy4Activity)
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
                                this@Strategy4Activity.supportFragmentManager,
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
                                this@Strategy4Activity.binding.endTimeTv.editableText.toString()
                            ).show(
                                this@Strategy4Activity.supportFragmentManager,
                                "stock_info"
                            )
                        }
                        b.dragonTigerRankBtn.setOnClickListener {
                            pw.dismiss()
                            result.stock.openDragonTigerRank(this@Strategy4Activity)
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
            return VH(ItemStockBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(data[position], position)
        }


    }

}


class StockInfoFragment(private val stock: Stock, private val date: String) : DialogFragment() {


    private lateinit var binding: FragmentStockInfoBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentStockInfoBinding.inflate(inflater)


        val resultList = mutableListOf<String>()

        lifecycleScope.launch(Dispatchers.IO) {
            val list = stock.bk.split(",").map {
                Pair(
                    Injector.appDatabase.bkDao().getBKByCode(it),
                    Injector.appDatabase.historyBKDao().getHistoryByDate3(it, date.toInt())
                )
            }.filter { it.first != null && !it.first!!.specialBK }
                .sortedByDescending { it.second.chg }
            launch(Dispatchers.Main) {
                list.forEach { pair ->
                    val bk = pair.first
                    val historyBK = pair.second
                    val b = ItemStockInfoBkBinding.inflate(inflater).apply {
                        bkCodeName.text = "${bk!!.code}-${bk.name}"
                        chgTv.text = historyBK.chg.toString()
                        chgTv.setTextColor(historyBK.color)
                        bkCodeName.setOnClickListener {
                            bk.openWeb(requireContext())
                        }
                        bkCodeName.setOnLongClickListener {
                            this@StockInfoFragment.parentFragmentManager
                            DIYBKDialogFragment(bk).show(
                                this@StockInfoFragment.parentFragmentManager,
                                "diy_bk"
                            )
                            return@setOnLongClickListener true
                        }
                        bkCb.setOnCheckedChangeListener { buttonView, isChecked ->
                            if (isChecked) {
                                if (!resultList.contains(bk.code)) {
                                    resultList.add(bk.code)
                                }
                            } else {
                                resultList.remove(bk.code)
                            }

                        }
                    }
                    binding.bkLL.addView(b.root)

                }
            }

        }




        binding.cancelBtn.setOnClickListener {
            dismiss()
        }


        binding.jumpBtn.setOnClickListener {
            val sb = StringBuilder()
            resultList.forEach {
                sb.append(it).append(',')
            }
            openJXQSStrategy(
                this.requireContext(),
                sb.removeSuffix(",").toString(),
                date
            )
        }


        return binding.root
    }


}


class DIYBKDialogFragment2(private val stock: Stock) : DialogFragment() {

    private lateinit var binding: FragmentDiyBkBinding


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDiyBkBinding.inflate(inflater)
        binding.rv.layoutManager = LinearLayoutManager(binding.rv.context)

        lifecycleScope.launch(Dispatchers.IO) {
            val list = Injector.appDatabase.diyBkDao().getDIYBks()
            launch(Dispatchers.Main) {
                binding.rv.adapter = DIYBKAdapter2(list.map {
                    SelectableItem(it, it.stockCodes.contains(stock.code, true))
                }.toMutableList())
            }

        }

        binding.cancelBtn.setOnClickListener {
            dismiss()
        }

        binding.okBtn.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                (binding.rv.adapter as DIYBKAdapter2).save(stock)
                dismiss()
            }

        }

        binding.bkCodeName.text = stock.code + "-" + stock.name

        binding.createBkBtn.setOnClickListener {
            val name = binding.diyBkName.editableText.toString()
            if (name.isEmpty()) {
                Toast.makeText(this.context, "请输入板块名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val codes = stock.code
            val code = Injector.sp.getInt("diy_bk_code", 10000) + 1
            val dsp = stock.code + "(${stock.name})"
            binding.diyBkName.setText("")
            val item = DIYBk("BK$code", name, "", dsp, codes)

            lifecycleScope.launch(Dispatchers.IO) {
                Injector.appDatabase.diyBkDao().insert(item)
                Injector.sp.edit().putInt("diy_bk_code", code).apply()
                launch(Dispatchers.Main) {
                    (binding.rv.adapter as DIYBKAdapter2).add(SelectableItem(item, true))
                }

            }

        }

        return binding.root
    }


    inner class DIYBKAdapter2(private val list: MutableList<SelectableItem<DIYBk>>) :
        RecyclerView.Adapter<DIYBKAdapter2.VH>() {

        fun add(item: SelectableItem<DIYBk>) {
            list.add(item)
            notifyItemInserted(list.size - 1)
        }


        fun save(stock: Stock) {
            list.forEach {
                if (it.selected) {
                    if (!it.data.stockCodes.contains(stock.code)) {
                        val newStockCodes = it.data.stockCodes + ",${stock.code}"
                        val newDsp = it.data.dsp + ",${stock.code}(${stock.name})"
                        val newBean = it.data.copy(
                            stockCodes = newStockCodes.removePrefix(","),
                            dsp = newDsp.removePrefix(",")
                        )
                        Injector.appDatabase.diyBkDao().insert(newBean)
                    }
                } else {
                    if (it.data.stockCodes.contains(stock.code)) {
                        val l = it.data.stockCodes.split(",").toMutableList()
                        val newCodes = kotlin.text.StringBuilder()
                        l.filter { it != stock.code }.forEach {
                            newCodes.append(it).append(",")
                        }
                        val newDsp = kotlin.text.StringBuilder()
                        val dspList = it.data.dsp.split(",").toMutableList()
                        dspList.filter { !it.contains(stock.code) }.forEach {
                            newDsp.append(it).append(",")
                        }
                        val newBean = it.data.copy(
                            stockCodes = newCodes.removeSuffix(",").toString(),
                            dsp = newDsp.removeSuffix(",").toString()
                        )
                        Injector.appDatabase.diyBkDao().insert(newBean)
                    }
                }
            }
        }

        inner class VH(private val itemBinding: ItemDiyBkBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {
            @SuppressLint("ClickableViewAccessibility")
            fun bind(item: SelectableItem<DIYBk>, position: Int) {
                itemBinding.apply {
                    cb.setOnCheckedChangeListener(null)
                    codeNameTv.text = item.data.code + "-" + item.data.name
                    this.root.setOnClickListener {
                        Strategy4Activity.openJXQSStrategy2(
                            context!!,
                            item.data,
                            today().toString()
                        )
                    }

                    var ev: MotionEvent? = null
                    root.setOnTouchListener { view, motionEvent ->
                        if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                            ev = motionEvent
                        }
                        return@setOnTouchListener false
                    }

                    this.root.setOnLongClickListener {

                        val b =
                            LayoutPopupWindow2Binding.inflate(LayoutInflater.from(it.context))
                        val pw = PopupWindow(
                            b.root,
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT,
                            true
                        )

                        b.deleteBtn.setOnClickListener {
                            lifecycleScope.launch(Dispatchers.IO) {
                                Injector.appDatabase.diyBkDao().delete(item.data)
                                launch(Dispatchers.Main) {
                                    list.removeAt(position)
                                    notifyItemRemoved(position)
                                    Toast.makeText(
                                        context,
                                        "删除${item.data.name}板块",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    pw.dismiss()
                                }
                            }
                        }
                        pw.showAsDropDown(it, (ev?.x ?: 0f).toInt() + 50, -150)

                        return@setOnLongClickListener true
                    }


                    cb.isChecked = item.selected
                    codes.text = item.data.dsp
                    cb.setOnCheckedChangeListener { _, isChecked ->
                        item.selected = isChecked
                    }

                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(
                ItemDiyBkBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun getItemCount(): Int {
            return list.size
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(list[position], position)
        }
    }


}








