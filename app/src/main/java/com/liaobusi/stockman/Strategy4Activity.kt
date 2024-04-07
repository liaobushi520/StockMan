package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.liaobusi.stockman.databinding.ActivityStrategy4Binding
import com.liaobusi.stockman.databinding.ItemFollowBinding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.*
import kotlinx.coroutines.*
import java.lang.StringBuilder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

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
    }

    private lateinit var binding: ActivityStrategy4Binding


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.page_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                lifecycleScope.launch {
                    StockRepo.refreshData()
                    launch(Dispatchers.Main) {
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

        supportActionBar?.title = "均线强势"


        var fromBKStrategyActivity = false
        if (intent.hasExtra("bk")) {
            val bk = intent.getStringExtra("bk")
            binding.conceptAndBKTv.setText(bk)
            binding.lowMarketValue.setText("0.0")
            binding.highMarketValue.setText("1000000.0")
            binding.endMarketTime.setText(today().toString())
            fromBKStrategyActivity = true
        }

        if (intent.hasExtra("endTime")) {
            binding.endTimeTv.setText(intent.getStringExtra("endTime"))
        } else {
            binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))
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
                startMarketTime = 19910101,
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
                startMarketTime = 19910101,
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
            val d = SimpleDateFormat("yyyyMMdd").parse(c)

            val cal = Calendar.getInstance()
            cal.apply { timeInMillis = d.time }
                .add(Calendar.DAY_OF_MONTH, -1)

            val s = SimpleDateFormat("yyyyMMdd").format(cal.time)
            binding.endTimeTv.setText(s)
            binding.chooseStockBtn.callOnClick()

        }


        binding.postBtn.setOnClickListener {
            val c = binding.endTimeTv.editableText.toString()
            val d = SimpleDateFormat("yyyyMMdd").parse(c)

            val cal = Calendar.getInstance()
            cal.apply { timeInMillis = d.time }
                .add(Calendar.DAY_OF_MONTH, 1)
            val s = SimpleDateFormat("yyyyMMdd").format(cal.time)
            binding.endTimeTv.setText(s)
            binding.chooseStockBtn.callOnClick()
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
                    stockList = Injector.getSnapshot()

                )
                output(list)
            }

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
                stockList = strictParam.stockList

            )
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


            binding.changyebanCb.setOnCheckedChangeListener { compoundButton, b ->
                output(strategyResult)
            }

            binding.bjsCb.setOnCheckedChangeListener { compoundButton, b ->
                output(strategyResult)
            }

            binding.activityLevelCb.setOnCheckedChangeListener { compoundButton, b ->
                if (b) {
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


            binding.onlyZTCb.setOnCheckedChangeListener { buttonView, isChecked ->
                output(strategyResult)
            }

            binding.ztPromotionCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zhongjunCb.isChecked = false
                    binding.onlyActiveRateCb.isChecked = true
                }
                output(strategyResult)
            }

            binding.stCb.setOnCheckedChangeListener { buttonView, isChecked ->
                output(strategyResult)
            }


            binding.zhongjunCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.ztPromotionCb.isChecked = false
                    binding.activityLevelCb.isChecked = true
                    binding.onlyActiveRateCb.isChecked = false
                }
                output(strategyResult)

            }

            var r = list
            r = mutableListOf<StockResult>().apply { addAll(r) }


            if (!binding.changyebanCb.isChecked) {
                r = r.filter { return@filter !it.stock.code.startsWith("300") }.toMutableList()
            }

            if (!binding.bjsCb.isChecked) {
                r = r.filter { return@filter !it.stock.code.startsWith("831") }.toMutableList()
            }

            if (!binding.stCb.isChecked) {
                r = r.filter { return@filter !it.stock.name.startsWith("ST")||!it.stock.name.startsWith("*") }
            }

            if (binding.onlyZTCb.isChecked) {
                r = r.filter { return@filter it.zt }
            }

            if (binding.cowBackCb.isChecked) {
                r = r.filter { return@filter it.cowBack }
            }

            if (binding.noZTInRangeCb.isChecked) {
                r = r.filter { return@filter it.ztCountInRange == 0 }
            }

            if (binding.activityLevelCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.activeRate.compareTo(v0.activeRate)
                })
            }

            if (binding.gdrsCb.isChecked) {
                val c = binding.gdrsCountTv.text.toString().toIntOrNull() ?: 5
                r = StockRepo.filterStockByGDRS(r, c)
            }

            if (binding.ztPromotionCb.isChecked) {
                r = r.filter { it.zt }.sortedByDescending { it.lianbanCount }
            }

            if (binding.zhongjunCb.isChecked) {
                r = r.filter { it.stock.circulationMarketValue > 8000000000 }
            }


            val newList = mutableListOf<StockResult>()
            r.forEach {
                if (it.follow) {
                    newList.add(0, it)
                } else {
                    newList.add(it)
                }

            }
            r = newList

            val strategyResult2 = StrategyResult(r, strategyResult.total)


            val ztCount = r.count { it.nextDayZT }
            val s = if (strategyResult2.total > 0 && r.isNotEmpty()) {
                "拟合度${DecimalFormat("#.0").format(r.size * 100f / strategyResult2.total)}%"
            } else ""
            binding.resultCount.text = "选股结果(${ztCount}/${r.size})  ${s}"

            binding.rv.layoutManager = LinearLayoutManager(this@Strategy4Activity)
            binding.rv.adapter = ResultAdapter(
                strategyResult2.stockResults.toMutableList(),
                binding.ztPromotionCb.isChecked
            )
        }
    }


    inner class ResultAdapter(
        private val data: MutableList<StockResult>,
        private val ztPromotion: Boolean = false
    ) :
        RecyclerView.Adapter<ResultAdapter.VH>() {


        fun getStockList(): List<Stock> {
            return data.map { it.stock }
        }


        inner class VH(val binding: ItemStockBinding) : RecyclerView.ViewHolder(binding.root) {

            @RequiresApi(Build.VERSION_CODES.O)
            fun bind(result: StockResult, position: Int) {
                val stock = result.stock
                binding.apply {

                    if (result.follow) {
                        this.root.setBackgroundColor(0x33333333)
                    } else {
                        this.root.setBackgroundColor(0xffffffff.toInt())
                    }


                    if (ztPromotion || isShowLianBanFlag(binding.root.context)) {
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


                    if (result.chg > 0) {
                        currentChg.setTextColor(Color.RED)
                    } else if (result.chg < 0) {
                        currentChg.setTextColor(Color.GREEN)
                    } else {
                        currentChg.setTextColor(Color.GRAY)
                    }
                    currentChg.text = result.chg.toString()
                    if (isShowCurrentChg(binding.root.context)) {
                        currentChg.visibility = View.VISIBLE
                    } else {
                        currentChg.visibility = View.GONE
                    }



                    this.stockName.text = stock.name
                    val formatText = result.toFormatText()
                    if (formatText.isNotEmpty()) {
                        this.labelTv.visibility = View.VISIBLE
                        this.labelTv.text = formatText
                    } else {
                        this.labelTv.visibility = View.INVISIBLE
                    }

                    if (result.activeRate > 2) {
                        this.activeLabelTv.visibility = View.VISIBLE
                        this.activeLabelTv.text = result.activeRate.toInt().toString()
                    } else {
                        this.activeLabelTv.visibility = View.INVISIBLE
                    }


                    this.nextDayIv.visibility =
                        if (result.nextDayZT || result.nextDayCry) View.VISIBLE else View.INVISIBLE
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

                        if (result.follow) {
                            b.followBtn.text = "取消关注"
                        }


                        val pw = PopupWindow(
                            b.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            true
                        )

                        b.followBtn.setOnClickListener {
                            pw.dismiss()
                            lifecycleScope.launch(Dispatchers.IO) {

                                val p = data.indexOf(result)
                                if (result.follow) {
                                    Injector.appDatabase.followDao()
                                        .deleteFollow(Follow(result.stock.code, 1))

                                    result.follow = false

                                    lifecycleScope.launch(Dispatchers.Main) {
                                        data.remove(result)
                                        notifyItemRemoved(p)
                                        delay(300)
                                        data.add(itemCount - 1, result)
                                        notifyItemInserted(itemCount - 1)
                                    }
                                } else {
                                    result.follow = true
                                    Injector.appDatabase.followDao()
                                        .insertFollow(Follow(result.stock.code, 1))
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
                        pw.showAsDropDown(it, (ev?.x ?: 0f).toInt(), -300)
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