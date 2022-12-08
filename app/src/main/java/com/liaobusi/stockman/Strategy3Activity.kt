package com.liaobusi.stockman

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.databinding.ActivityStrategy2Binding
import com.liaobusi.stockman.databinding.ActivityStrategy3Binding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import com.liaobusi.stockman.repo.Strategy2Param
import com.liaobusi.stockman.repo.Strategy3Param
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class Strategy3Activity : AppCompatActivity() {
    private lateinit var binding: ActivityStrategy3Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategy3Binding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))

        binding.softStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy3Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val param = Strategy3Param(
                startMarketTime = 19920101,
                endMarketTime = 20150101,
                lowMarketValue = 1000000000.0,
                highMarketValue = 10000000000.0,
                range = 7,
                endTime = endTime,
                decreaseTurnoverRateHigh = 2.0,
                ztNextTurnoverRateLow = 2.5,
                allowedZTBeforeZT = 1,
                allowedZTRangeBeforeZT = 10
            )
            updateUI(param)
            outputResult(param)
        }
        binding.strictStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy3Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val param = Strategy3Param(
                startMarketTime = 19920101,
                endMarketTime = 20150101,
                lowMarketValue = 1000000000.0,
                highMarketValue = 10000000000.0,
                range = 7,
                endTime = endTime,
                decreaseTurnoverRateHigh = 1.3,
                ztNextTurnoverRateLow = 3.0,
                allowedZTBeforeZT = 0,
                allowedZTRangeBeforeZT = 20
            )
            updateUI(param)
            outputResult(param)
        }
        binding.chooseStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val startMarketTime = binding.startMarketTime.editableText.toString().toIntOrNull()
            if (startMarketTime == null) {
                Toast.makeText(this@Strategy3Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val endMarketTime = binding.endMarketTime.editableText.toString().toIntOrNull()
            if (endMarketTime == null) {
                Toast.makeText(this@Strategy3Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lowMarketValue = binding.lowMarketValue.editableText.toString().toDoubleOrNull()

            val highMarketValue = binding.highMarketValue.editableText.toString().toDoubleOrNull()

            if (lowMarketValue == null || highMarketValue == null || lowMarketValue > highMarketValue) {
                Toast.makeText(this@Strategy3Activity, "市值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val timeRange = binding.timeRangeTv.editableText.toString().toIntOrNull()
            if (timeRange == null) {
                Toast.makeText(this@Strategy3Activity, "查找区间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy3Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val decreaseTurnoverRateHigh =
                binding.decreaseTurnoverRateHighTv.editableText.toString().toDoubleOrNull()
            if (decreaseTurnoverRateHigh == null) {
                Toast.makeText(this@Strategy3Activity, "涨停缩量倍数不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val ztNextTurnoverRateLow =
                binding.ztNextTurnoverRateLowTv.editableText.toString().toDoubleOrNull()
            if (ztNextTurnoverRateLow == null) {
                Toast.makeText(this@Strategy3Activity, "涨停后一天放量倍数不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val allowedZTBeforeZT =
                binding.allowedZTBeforeZTTv.editableText.toString().toIntOrNull()
            if (allowedZTBeforeZT == null) {
                Toast.makeText(this@Strategy3Activity, "涨停前允许的涨停次数不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val allowedZTRangeBeforeZT =
                binding.allowedZTRangeBeforeZTTv.editableText.toString().toIntOrNull()
            if (allowedZTRangeBeforeZT == null) {
                Toast.makeText(this@Strategy3Activity, "涨停前允许的涨停次数不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }


            lifecycleScope.launch(Dispatchers.IO) {
                val list = StockRepo.strategy3(
                    startMarketTime = startMarketTime,
                    endMarketTime = endMarketTime,
                    lowMarketValue = lowMarketValue * 100000000,
                    highMarketValue = highMarketValue * 100000000,
                    range = timeRange,
                    endTime = endTime,
                    decreaseTurnoverRateHigh = decreaseTurnoverRateHigh,
                    ztNextTurnoverRateLow = ztNextTurnoverRateLow,
                    allowedZTBeforeZT = allowedZTBeforeZT,
                    allowedZTRangeBeforeZT = allowedZTRangeBeforeZT
                )
                output(list)
            }
        }
    }

    private fun updateUI(param: Strategy3Param) {
        binding.apply {
            this.endTimeTv.setText(param.endTime.toString())
            this.startMarketTime.setText(param.startMarketTime.toString())
            this.endMarketTime.setText(param.endMarketTime.toString())
            this.lowMarketValue.setText((param.lowMarketValue / 100000000).toString())
            this.highMarketValue.setText((param.highMarketValue / 100000000).toString())
            this.timeRangeTv.setText(param.range.toString())
            this.decreaseTurnoverRateHighTv.setText(param.decreaseTurnoverRateHigh.toString())
            this.ztNextTurnoverRateLowTv.setText(param.ztNextTurnoverRateLow.toString())
            this.allowedZTBeforeZTTv.setText(param.allowedZTBeforeZT.toString())
            this.allowedZTRangeBeforeZTTv.setText(param.allowedZTRangeBeforeZT.toString())
        }
    }

    private fun output(list: List<Stock>) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.resultLL.removeAllViews()
            binding.resultCount.text="选股结果(${list.size})"
            list.forEach { stock ->
                val itemBinding =
                    ItemStockBinding.inflate(LayoutInflater.from(Injector.context)).apply {
                        this.stockName.text = stock.name
                        root.setOnClickListener {
                            stock.openWeb(this@Strategy3Activity)
                        }
                    }
                binding.resultLL.addView(itemBinding.root)
            }
        }
    }


    private fun outputResult(param: Strategy3Param) {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = StockRepo.strategy3(
                startMarketTime = param.startMarketTime,
                endMarketTime = param.endMarketTime,
                lowMarketValue = param.lowMarketValue,
                highMarketValue = param.highMarketValue,
                range = param.range,
                endTime = param.endTime,
                decreaseTurnoverRateHigh = param.decreaseTurnoverRateHigh,
                ztNextTurnoverRateLow = param.ztNextTurnoverRateLow,
                allowedZTRangeBeforeZT = param.allowedZTRangeBeforeZT,
                allowedZTBeforeZT = param.allowedZTBeforeZT
            )
            output(list)

        }
    }
}