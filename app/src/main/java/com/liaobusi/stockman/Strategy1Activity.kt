package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.Injector.context
import com.liaobusi.stockman.databinding.ActivityStrategy1Binding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.BKResult
import com.liaobusi.stockman.repo.StockRepo
import com.liaobusi.stockman.repo.StockResult
import com.liaobusi.stockman.repo.Strategy1Param
import com.liaobusi.stockman.repo.StrategyResult
import com.liaobusi.stockman.repo.toFormatText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


class Strategy1Activity : AppCompatActivity() {


    companion object{
        fun openZTXPStrategy(context: Context, bkCode:String, endTime: String){
            val i = Intent(
                context,
                Strategy1Activity::class.java
            ).apply {
                putExtra("bk", bkCode)
                putExtra("endTime",endTime)
            }
            context.startActivity(i)
        }
    }


    private lateinit var binding: ActivityStrategy1Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategy1Binding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        var fromBKStrategyActivity=false
        if(intent.hasExtra("bk")){
            val bk=  intent.getStringExtra("bk")
            binding.conceptAndBKTv.setText(bk)
            binding.lowMarketValue.setText("0.0")
            binding.highMarketValue.setText("1000000.0")
            binding.endMarketTime.setText(today().toString())
            fromBKStrategyActivity=true
        }

        supportActionBar?.title = "涨停洗盘"

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
                Toast.makeText(this@Strategy1Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy1Param(
                startMarketTime = 19910101,
                endMarketTime =if(fromBKStrategyActivity)  today()  else  20180101,
                lowMarketValue =if(fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue =if(fromBKStrategyActivity) 100000000000000.0  else 100000000000000.0,
                ztRange = 40,
                adjustTimeAfterZT = 5,
                afterZTStockPriceLowRate = 0.95,
                afterZTStockPriceHighRate = 1.10,
                allowedZTBeforeZT = 3,
                newHighestRangeBeforeZT = 60,
                endTime = endTime,
                amplitudeAfterZT = 25.0,
                bkList = bkList
            )
            updateUI(param)
            outputResult(param)
        }

        binding.midStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy1Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy1Param(
                startMarketTime = 19910101,
                endMarketTime =if(fromBKStrategyActivity)  today()  else  20180101,
                lowMarketValue =if(fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue =if(fromBKStrategyActivity) 100000000000000.0  else 100000000000000.0,
                ztRange = 100,
                adjustTimeAfterZT = 20,
                afterZTStockPriceLowRate = 0.90,
                afterZTStockPriceHighRate = 1.05,
                allowedZTBeforeZT = 3,
                newHighestRangeBeforeZT = 60,
                endTime = endTime,
                amplitudeAfterZT = 25.0,
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
                Toast.makeText(this@Strategy1Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy1Param(
                startMarketTime = 19910101,
                endMarketTime =if(fromBKStrategyActivity)  today()  else  20180101,
                lowMarketValue =if(fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue =if(fromBKStrategyActivity) 100000000000000.0  else 100000000000000.0,
                ztRange = 20,
                adjustTimeAfterZT = 3,
                afterZTStockPriceLowRate = 0.95,
                afterZTStockPriceHighRate = 1.05,
                allowedZTBeforeZT = 3,
                newHighestRangeBeforeZT = 60,
                endTime = endTime,
                amplitudeAfterZT = 20.0,
                bkList = bkList
            )

            updateUI(param)
            outputResult(param)
        }


        binding.chooseStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val startMarketTime = binding.startMarketTime.editableText.toString().toIntOrNull()
            if (startMarketTime == null) {
                Toast.makeText(this@Strategy1Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val endMarketTime = binding.endMarketTime.editableText.toString().toIntOrNull()
            if (endMarketTime == null) {
                Toast.makeText(this@Strategy1Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lowMarketValue = binding.lowMarketValue.editableText.toString().toDoubleOrNull()

            val highMarketValue = binding.highMarketValue.editableText.toString().toDoubleOrNull()

            if (lowMarketValue == null || highMarketValue == null || lowMarketValue > highMarketValue) {
                Toast.makeText(this@Strategy1Activity, "市值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val ztAdjustTime = binding.ztAdjustTimeTv.editableText.toString().toIntOrNull()
            if (ztAdjustTime == null) {
                Toast.makeText(this@Strategy1Activity, "涨停调整时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val ztRange = binding.ztRangeTv.editableText.toString().toIntOrNull()
            if (ztRange == null) {
                Toast.makeText(this@Strategy1Activity, "涨停调整时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val afterZTStockPriceLowRate =
                binding.afterZTStockPriceLowRateTv.editableText.toString().toFloatOrNull()
            if (afterZTStockPriceLowRate == null) {
                Toast.makeText(this@Strategy1Activity, "调整区间低值涨幅不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val afterZTStockPriceHighRate =
                binding.afterZTStockPriceHighRateTv.editableText.toString().toFloatOrNull()
            if (afterZTStockPriceHighRate == null) {
                Toast.makeText(this@Strategy1Activity, "调整区间涨幅高值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (afterZTStockPriceLowRate > afterZTStockPriceHighRate) {
                Toast.makeText(this@Strategy1Activity, "调整区间涨幅高值小于低值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val allowedZTBeforeZT =
                binding.allowedZTBeforeZTTv.editableText.toString().toIntOrNull()
            if (allowedZTBeforeZT == null) {
                Toast.makeText(this@Strategy1Activity, "涨停前允许的涨停次数不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val newHighestRangeBeforeZT =
                binding.newHighestRangeBeforeZTTv.editableText.toString().toIntOrNull()
            if (newHighestRangeBeforeZT == null) {
                Toast.makeText(this@Strategy1Activity, "涨停前新高比较范围不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy1Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val amplitudeAfterZT =
                binding.amplitudeAfterZTTv.editableText.toString().toDoubleOrNull()
            if (amplitudeAfterZT == null) {
                Toast.makeText(this@Strategy1Activity, "涨停后振幅值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener

            lifecycleScope.launch(Dispatchers.IO) {
                val list = StockRepo.strategy1(
                    startMarketTime = startMarketTime,
                    endMarketTime = endMarketTime,
                    lowMarketValue = lowMarketValue * 100000000,
                    highMarketValue = highMarketValue * 100000000,
                    ztRange = ztRange,
                    adjustTimeAfterZT = ztAdjustTime,
                    afterZTStockPriceLowRate = (1 + afterZTStockPriceLowRate / 100).toDouble(),
                    afterZTStockPriceHighRate = (1 + afterZTStockPriceHighRate / 100).toDouble(),
                    allowedZTBeforeZT = allowedZTBeforeZT,
                    newHighestRangeBeforeZT = newHighestRangeBeforeZT,
                    amplitudeAfterZT = amplitudeAfterZT / 100,
                    endTime = endTime,
                    bkList = bkList
                )
                output(list)
            }


        }

    }

    private fun updateUI(param: Strategy1Param) {
        binding.apply {
            this.endTimeTv.setText(param.endTime.toString())
            this.startMarketTime.setText(param.startMarketTime.toString())
            this.endMarketTime.setText(param.endMarketTime.toString())
            this.lowMarketValue.setText((param.lowMarketValue / 100000000).toString())
            this.highMarketValue.setText((param.highMarketValue / 100000000).toString())
            this.afterZTStockPriceLowRateTv.setText(((param.afterZTStockPriceLowRate * 100 - 100)).toString())
            this.afterZTStockPriceHighRateTv.setText(((param.afterZTStockPriceHighRate - 1.0) * 100).toString())
            this.ztRangeTv.setText(param.ztRange.toString())
            this.ztAdjustTimeTv.setText(param.adjustTimeAfterZT.toString())
            this.allowedZTBeforeZTTv.setText(param.allowedZTBeforeZT.toString())
            this.newHighestRangeBeforeZTTv.setText(param.newHighestRangeBeforeZT.toString())
            this.amplitudeAfterZTTv.setText(param.amplitudeAfterZT.toString())
        }
    }


    private fun outputResult(strictParam: Strategy1Param) {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = StockRepo.strategy1(
                startMarketTime = strictParam.startMarketTime,
                endMarketTime = strictParam.endMarketTime,
                lowMarketValue = strictParam.lowMarketValue,
                highMarketValue = strictParam.highMarketValue,
                ztRange = strictParam.ztRange,
                adjustTimeAfterZT = strictParam.adjustTimeAfterZT,
                afterZTStockPriceLowRate = strictParam.afterZTStockPriceLowRate,
                afterZTStockPriceHighRate = strictParam.afterZTStockPriceHighRate,
                allowedZTBeforeZT = strictParam.allowedZTBeforeZT,
                newHighestRangeBeforeZT = strictParam.newHighestRangeBeforeZT,
                endTime = strictParam.endTime,
                amplitudeAfterZT = strictParam.amplitudeAfterZT / 100,
                bkList = strictParam.bkList,

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
            Toast.makeText(this@Strategy1Activity, "板块不合法,BK开头,逗号分割", Toast.LENGTH_LONG).show()
            return null

        }
        return l
    }

    private fun output(list: List<StockResult>) {
        lifecycleScope.launch(Dispatchers.Main) {

            binding.changyebanCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.cowBackCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.kdCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }



            binding.resultLL.removeAllViews()
            var r = list
            if (!binding.changyebanCb.isChecked) {
                r = list.filter { return@filter !it.stock.code.startsWith("300") }
            }

            if (binding.cowBackCb.isChecked) {
                r = r.filter { return@filter it.cowBack }
            }

            if(binding.kdCb.isChecked){
                r = r.sortedBy {
                    it.kd
                }
            }
            val ztCount=list.count { it.nextDayZT }
            binding.resultCount.text = "选股结果($ztCount/${r.size})"


            val newList = mutableListOf<StockResult>()
            r.forEach {
                if (it.follow) {
                    newList.add(0, it)
                } else {
                    newList.add(it)
                }

            }
            r = newList


            r.forEach { result ->
                val stock = result.stock
                val itemBinding =
                    ItemStockBinding.inflate(LayoutInflater.from(context)).apply {

                        if (result.follow) {
                            this.root.setBackgroundColor(0x33333333)
                        }

                        this.stockName.text = stock.name
                        val formatText = result.toFormatText()
                        if (formatText.isNotEmpty()) {
                            this.labelTv.visibility = View.VISIBLE
                            this.labelTv.text = formatText
                        } else {
                            this.labelTv.visibility = View.INVISIBLE
                        }
                        this.goodIv.visibility =
                            if (result.nextDayZT) View.VISIBLE else View.GONE

                        root.setOnClickListener {
                            stock.openWeb(this@Strategy1Activity)
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
                                    if (result.follow) {
                                        result.follow = false
                                        Injector.appDatabase.followDao()
                                            .deleteFollow(Follow(result.stock.code, 1))
                                        output(list)
                                    } else {
                                        result.follow = true
                                        Injector.appDatabase.followDao()
                                            .insertFollow(Follow(result.stock.code, 1))
                                        output(list)
                                    }

                                }


                            }
                            pw.showAsDropDown(it, (ev?.x ?: 0f).toInt(), -300)
                            return@setOnLongClickListener true
                        }
                    }
                binding.resultLL.addView(itemBinding.root)
            }
        }
    }
}