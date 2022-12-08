package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.databinding.ActivityStrategy6Binding
import com.liaobusi.stockman.databinding.ActivityStrategy7Binding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import com.liaobusi.stockman.repo.StockResult
import com.liaobusi.stockman.repo.toFormatText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class Strategy7Activity : AppCompatActivity() {

    companion object{

        fun openJXQSStrategy(context: Context, bkCode:String, endTime: String){
            val i = Intent(
                context,
                Strategy7Activity::class.java
            ).apply {
                putExtra("bk", bkCode)
                putExtra("endTime",endTime)
            }
            context.startActivity(i)
        }
    }


    private lateinit var binding: ActivityStrategy7Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategy7Binding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        var fromBKStrategyActivity=false
        if (intent.hasExtra("bk")) {
            val bk = intent.getStringExtra("bk")
            binding.conceptAndBKTv.setText(bk)
            binding.lowMarketValue.setText("0.0")
            binding.highMarketValue.setText("1000000.0")
            fromBKStrategyActivity=true
        }
        if(intent.hasExtra("endTime")){
            binding.endTimeTv.setText(intent.getStringExtra("endTime"))
        }else{
            binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))
        }

        binding.chooseStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val startMarketTime = binding.startMarketTime.editableText.toString().toIntOrNull()
            if (startMarketTime == null) {
                Toast.makeText(this@Strategy7Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val endMarketTime = binding.endMarketTime.editableText.toString().toIntOrNull()
            if (endMarketTime == null) {
                Toast.makeText(this@Strategy7Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lowMarketValue = binding.lowMarketValue.editableText.toString().toDoubleOrNull()

            val highMarketValue = binding.highMarketValue.editableText.toString().toDoubleOrNull()

            if (lowMarketValue == null || highMarketValue == null || lowMarketValue > highMarketValue) {
                Toast.makeText(this@Strategy7Activity, "市值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val ztAdjustTime = binding.ztAdjustTimeTv.editableText.toString().toIntOrNull()
            if (ztAdjustTime == null) {
                Toast.makeText(this@Strategy7Activity, "涨停调整时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val ztRange = binding.ztRangeTv.editableText.toString().toIntOrNull()
            if (ztRange == null) {
                Toast.makeText(this@Strategy7Activity, "涨停调整时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val allowBelowCount =
                binding.allowBelowCountTv.editableText.toString().toIntOrNull()
            if (allowBelowCount == null) {
                Toast.makeText(this@Strategy7Activity, "允许均线下方运行次数不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val averageDay =
                binding.averageDayTv.editableText.toString().toIntOrNull()
            if (averageDay == null) {
                Toast.makeText(this@Strategy7Activity, "均线取值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val divergeRate =
                binding.divergeRateTv.editableText.toString().toDoubleOrNull()
            if (divergeRate == null) {
                Toast.makeText(this@Strategy7Activity, "收盘价与均线偏差率取值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy7Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }


            val bkList = checkBKInput() ?: return@setOnClickListener

            lifecycleScope.launch(Dispatchers.IO) {
                val list = StockRepo.strategy8(
                    startMarketTime = startMarketTime,
                    endMarketTime = endMarketTime,
                    lowMarketValue =if(fromBKStrategyActivity) 0.0 else  lowMarketValue * 100000000,
                    highMarketValue =if(fromBKStrategyActivity) 100000000000000.0  else  highMarketValue * 100000000,
                    ztRange = ztRange,
                    averageDay=averageDay,
                    allowBelowCount=allowBelowCount,
                    divergeRate=divergeRate/100,
                    adjustTimeAfterZT = ztAdjustTime,
                    endTime = endTime,
                    bkList = bkList
                )
                output(list)
            }


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
                val stock = result.stock
                val itemBinding =
                    ItemStockBinding.inflate(LayoutInflater.from(Injector.context)).apply {
                        this.stockName.text = stock.name
                        if(result.nextDayZT){
                            this.goodIv.visibility= View.VISIBLE
                        }else{
                            this.goodIv.visibility= View.GONE
                        }
                        val formatText = result.toFormatText()
                        if (formatText.isNotEmpty()) {
                            this.labelTv.visibility = View.VISIBLE
                            this.labelTv.text = formatText
                        } else {
                            this.labelTv.visibility = View.INVISIBLE
                        }

                        root.setOnClickListener {
                            stock.openWeb(this@Strategy7Activity)
                        }
                    }
                binding.resultLL.addView(itemBinding.root)
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
            Toast.makeText(this@Strategy7Activity, "板块不合法,BK开头,逗号分割", Toast.LENGTH_LONG).show()
            return null

        }
        return l
    }

}