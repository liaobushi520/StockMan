package com.liaobusi.stockman

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.databinding.ActivityStrategy1Binding
import com.liaobusi.stockman.databinding.ActivityStrategy5Binding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import com.liaobusi.stockman.repo.StockResult
import com.liaobusi.stockman.repo.toFormatText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class Strategy5Activity : AppCompatActivity() {
    private lateinit var binding: ActivityStrategy5Binding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategy5Binding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))
        binding.chooseStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val startMarketTime = binding.startMarketTime.editableText.toString().toIntOrNull()
            if (startMarketTime == null) {
                Toast.makeText(this@Strategy5Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val endMarketTime = binding.endMarketTime.editableText.toString().toIntOrNull()
            if (endMarketTime == null) {
                Toast.makeText(this@Strategy5Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lowMarketValue = binding.lowMarketValue.editableText.toString().toDoubleOrNull()

            val highMarketValue = binding.highMarketValue.editableText.toString().toDoubleOrNull()

            if (lowMarketValue == null || highMarketValue == null || lowMarketValue > highMarketValue) {
                Toast.makeText(this@Strategy5Activity, "市值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val timeRange = binding.timeRangeTv.editableText.toString().toIntOrNull()
            if (timeRange == null) {
                Toast.makeText(this@Strategy5Activity, "查找区间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy5Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }


            val abnormalRate =
                binding.abnormalRateTv.editableText.toString().toDoubleOrNull()
            if (abnormalRate == null) {
                Toast.makeText(this@Strategy5Activity, "异常放量倍数不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val bkList = checkBKInput() ?: return@setOnClickListener

            lifecycleScope.launch(Dispatchers.IO) {
                val list = StockRepo.strategy5(
                    startMarketTime = startMarketTime,
                    endMarketTime = endMarketTime,
                    lowMarketValue = lowMarketValue * 100000000,
                    highMarketValue = highMarketValue * 100000000,
                    range = timeRange,
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
                        val formatText = result.toFormatText()
                        if (formatText.isNotEmpty()) {
                            this.labelTv.visibility = View.VISIBLE
                            this.labelTv.text = formatText
                        } else {
                            this.labelTv.visibility = View.INVISIBLE
                        }
                        root.setOnClickListener {
                            stock.openWeb(this@Strategy5Activity)
                        }
                    }
                binding.resultLL.addView(itemBinding.root)
            }
        }
    }

    fun checkBKInput(): List<String>? {
        val conceptAndBK =
            binding.conceptAndBKTv.editableText.toString()
        if(conceptAndBK=="ALL"){
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
            Toast.makeText(this@Strategy5Activity, "板块不合法,BK开头,逗号分割", Toast.LENGTH_LONG).show()
            return null

        }
        return l
    }
}