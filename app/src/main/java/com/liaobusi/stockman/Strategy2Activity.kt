package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope

import com.liaobusi.stockman.databinding.ActivityStrategy2Binding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import com.liaobusi.stockman.repo.StockResult
import com.liaobusi.stockman.repo.Strategy1Param
import com.liaobusi.stockman.repo.Strategy2Param
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class Strategy2Activity : AppCompatActivity() {

    companion object{

        fun openZTRCStrategy(context: Context,bkCode:String, endTime: String){
            val i = Intent(
                context,
                Strategy2Activity::class.java
            ).apply {
                putExtra("bk", bkCode)
                putExtra("endTime",endTime)
            }
            context.startActivity(i)
        }
    }

    private lateinit var binding: ActivityStrategy2Binding


    private fun updateUI(param: Strategy2Param) {
        binding.apply {
            this.endTimeTv.setText(param.endTime.toString())
            this.startMarketTime.setText(param.startMarketTime.toString())
            this.endMarketTime.setText(param.endMarketTime.toString())
            this.lowMarketValue.setText((param.lowMarketValue / 100000000).toString())
            this.highMarketValue.setText((param.highMarketValue / 100000000).toString())
            this.timeRangeTv.setText(param.range.toString())
            this.adjustTimeTv.setText(param.adjustTimeAfterZT.toString())
            this.minAdjustTimeTv.setText(param.minAdjustTimeAfterZT.toString())
            this.increaseLowTv.setText((param.increaseLow * 100).toString())
            this.increaseHighTv.setText((param.increaseHigh * 100).toString())
            this.ztCountTv.setText(param.ztCount.toString())
        }
    }

    private fun output(list: List<StockResult>) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.changyebanCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.resultLL.removeAllViews()
            var r = list
            if (!binding.changyebanCb.isChecked) {
                r = list.filter { return@filter !it.stock.code.startsWith("300") }
            }
            binding.resultCount.text = "选股结果(${r.size})"

            r.forEach { result ->
                val itemBinding =
                    ItemStockBinding.inflate(LayoutInflater.from(Injector.context)).apply {
                        this.stockName.text = result.stock.name

                        this.goodIv.visibility =
                            if (result.nextDayZT) View.VISIBLE else View.GONE

                        root.setOnClickListener {
                            result.stock.openWeb(this@Strategy2Activity)
                        }
                    }
                binding.resultLL.addView(itemBinding.root)
            }
        }
    }

    private fun outputResult(strictParam: Strategy2Param) {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = StockRepo.strategy2(
                startMarketTime = strictParam.startMarketTime,
                endMarketTime = strictParam.endMarketTime,
                lowMarketValue = strictParam.lowMarketValue,
                highMarketValue = strictParam.highMarketValue,
                range = strictParam.range,
                increaseLow = strictParam.increaseLow,
                increaseHigh = strictParam.increaseHigh,
                endTime = strictParam.endTime,
                adjustTimeAfterZT = strictParam.adjustTimeAfterZT,
                ztCount = strictParam.ztCount,
                zbEnable = strictParam.zbEnable,
                minAdjustTimeAfterZT = strictParam.minAdjustTimeAfterZT,
                bkList = strictParam.bkList
            )
            output(list)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategy2Binding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        supportActionBar?.title = "涨停揉搓"

        var fromBKStrategyActivity=false
        if (intent.hasExtra("bk")) {
            val bk = intent.getStringExtra("bk")
            binding.conceptAndBKTv.setText(bk)
            binding.lowMarketValue.setText("0.0")
            binding.highMarketValue.setText("1000000.0")
            binding.endMarketTime.setText(today().toString())
            fromBKStrategyActivity=true
        }

        if(intent.hasExtra("endTime")){
            binding.endTimeTv.setText(intent.getStringExtra("endTime"))
        }else{
            binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))
        }


        binding.softStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy2Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val zbEnable = binding.zbCb.isChecked
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy2Param(
                startMarketTime = 19910101,
                endMarketTime =if(fromBKStrategyActivity)  today()  else  20180101,
                lowMarketValue =if(fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue =if(fromBKStrategyActivity) 100000000000000.0  else 10000000000000.0,
                range = 5,
                increaseLow = -0.10,
                increaseHigh = -0.05,
                endTime = endTime,
                adjustTimeAfterZT = 4,
                ztCount = 1,
                zbEnable = zbEnable,
                minAdjustTimeAfterZT = 2,
                bkList = bkList
            )
            updateUI(param)
            outputResult(param)
        }
        binding.strictStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()

            if (endTime == null) {
                Toast.makeText(this@Strategy2Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val zbEnable = binding.zbCb.isChecked
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy2Param(
                startMarketTime = 19910101,
                endMarketTime =if(fromBKStrategyActivity)  today()  else  20180101,
                lowMarketValue =if(fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue =if(fromBKStrategyActivity) 100000000000000.0  else 10000000000000.0,
                range = 12,
                increaseLow = -0.10,
                increaseHigh = 0.00,
                endTime = endTime,
                adjustTimeAfterZT = 5,
                ztCount = 2,
                zbEnable = zbEnable,
                minAdjustTimeAfterZT = 1,
                bkList = bkList
            )
            updateUI(param)
            outputResult(param)
        }
        binding.zbCb.setOnCheckedChangeListener { compoundButton, b ->
            binding.chooseStockBtn.callOnClick()
        }
        binding.chooseStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val startMarketTime = binding.startMarketTime.editableText.toString().toIntOrNull()
            if (startMarketTime == null) {
                Toast.makeText(this@Strategy2Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val endMarketTime = binding.endMarketTime.editableText.toString().toIntOrNull()
            if (endMarketTime == null) {
                Toast.makeText(this@Strategy2Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lowMarketValue = binding.lowMarketValue.editableText.toString().toDoubleOrNull()

            val highMarketValue = binding.highMarketValue.editableText.toString().toDoubleOrNull()

            if (lowMarketValue == null || highMarketValue == null || lowMarketValue > highMarketValue) {
                Toast.makeText(this@Strategy2Activity, "市值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val timeRange = binding.timeRangeTv.editableText.toString().toIntOrNull()
            if (timeRange == null) {
                Toast.makeText(this@Strategy2Activity, "查找区间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val adjustTime = binding.adjustTimeTv.editableText.toString().toIntOrNull()
            if (adjustTime == null) {
                Toast.makeText(this@Strategy2Activity, "涨停调整时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val minAdjustTime = binding.minAdjustTimeTv.editableText.toString().toIntOrNull()
            if (minAdjustTime == null) {
                Toast.makeText(this@Strategy2Activity, "涨停调整最少时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val increaseHigh =
                binding.increaseHighTv.editableText.toString().toDoubleOrNull()
            if (increaseHigh == null) {
                Toast.makeText(this@Strategy2Activity, "调整区间高值涨幅不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val increaseLow =
                binding.increaseLowTv.editableText.toString().toDoubleOrNull()
            if (increaseLow == null) {
                Toast.makeText(this@Strategy2Activity, "调整区间涨幅低值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (increaseLow > increaseHigh) {
                Toast.makeText(this@Strategy2Activity, "调整区间涨幅高值小于低值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy2Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val ztCount =
                binding.ztCountTv.editableText.toString().toIntOrNull()
            if (ztCount == null) {
                Toast.makeText(this@Strategy2Activity, "涨停次数不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val zbEnable = binding.zbCb.isChecked
            val bkList = checkBKInput() ?: return@setOnClickListener


            lifecycleScope.launch(Dispatchers.IO) {
                val list = StockRepo.strategy2(
                    startMarketTime = startMarketTime,
                    endMarketTime = endMarketTime,
                    lowMarketValue = lowMarketValue * 100000000,
                    highMarketValue = highMarketValue * 100000000,
                    range = timeRange,
                    endTime = endTime,
                    increaseLow = increaseLow / 100,
                    increaseHigh = increaseHigh / 100,
                    adjustTimeAfterZT = adjustTime,
                    ztCount = ztCount,
                    zbEnable = zbEnable,
                    minAdjustTimeAfterZT = minAdjustTime,
                    bkList = bkList
                )
                output(list)
            }


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
            Toast.makeText(this@Strategy2Activity, "板块不合法,BK开头,逗号分割", Toast.LENGTH_LONG).show()
            return null

        }
        return l
    }
}