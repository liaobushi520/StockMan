package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.databinding.ActivityStrategy6Binding
import com.liaobusi.stockman.databinding.ActivityStrategy7Binding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

class Strategy7Activity : AppCompatActivity() {

    companion object {

        fun openZTQSStrategy(context: Context, bkCode: String, endTime: String) {
            val i = Intent(
                context,
                Strategy7Activity::class.java
            ).apply {
                putExtra("bk", bkCode)
                putExtra("endTime", endTime)
            }
            context.startActivity(i)
        }
    }


    private lateinit var binding: ActivityStrategy7Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategy7Binding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        var fromBKStrategyActivity = false
        if (intent.hasExtra("bk")) {
            val bk = intent.getStringExtra("bk")
            binding.conceptAndBKTv.setText(bk)
            binding.lowMarketValue.setText("0.0")
            binding.highMarketValue.setText("1000000.0")
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
                Toast.makeText(this@Strategy7Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy8Param(
                startMarketTime = 19900101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 30000000000000.0,
                endTime = endTime,
                bkList = bkList,
                ztRange = 10,
                adjustTimeAfterZT = 2,
                averageDay = 5,
                divergeRate =0.00,
                allowBelowCount = 0
            )
            updateUI(param)
            outputResult(param)
        }

        binding.line10Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy7Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy8Param(
                startMarketTime = 19900101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 30000000000000.0,
                endTime = endTime,
                bkList = bkList,
                ztRange = 15,
                adjustTimeAfterZT = 5,
                averageDay = 10,
                divergeRate =0.00,
                allowBelowCount = 0
            )
            updateUI(param)
            outputResult(param)
        }



        binding.line20Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy7Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy8Param(
                startMarketTime = 19900101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 30000000000000.0,
                endTime = endTime,
                bkList = bkList,
                ztRange = 25,
                adjustTimeAfterZT = 10,
                averageDay = 20,
                divergeRate =0.00,
                allowBelowCount = 0
            )
            updateUI(param)
            outputResult(param)
        }



        binding.line30Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy7Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy8Param(
                startMarketTime = 19900101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 30000000000000.0,
                endTime = endTime,
                bkList = bkList,
                ztRange = 40,
                adjustTimeAfterZT = 15,
                averageDay = 30,
                divergeRate =0.00,
                allowBelowCount = 0
            )
            updateUI(param)
            outputResult(param)
        }


        binding.line60Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy7Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val bkList = checkBKInput() ?: return@setOnClickListener
            val param = Strategy8Param(
                startMarketTime = 19900101,
                endMarketTime = if (fromBKStrategyActivity) today() else today(),
                lowMarketValue = if (fromBKStrategyActivity) 0.0 else 1000000000.0,
                highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else 30000000000000.0,
                endTime = endTime,
                bkList = bkList,
                ztRange = 60,
                adjustTimeAfterZT = 30,
                averageDay = 60,
                divergeRate =0.00,
                allowBelowCount = 0
            )
            updateUI(param)
            outputResult(param)
        }


        binding.followBkCb.setOnCheckedChangeListener { buttonView, isChecked ->
            if(isChecked){
                lifecycleScope.launch(Dispatchers.IO) {
                    val followBKs = Injector.appDatabase.bkDao().getFollowedBKS()
                    val sb = StringBuilder()
                    followBKs.forEach {
                        sb.append(it.code + ",")
                    }
                    var s = sb.dropLastWhile { it == ',' }
                    if(s.isNullOrEmpty()){
                        s="ALL"
                    }
                    launch(Dispatchers.Main) {
                        binding.conceptAndBKTv.setText(s.toString())
                        binding.chooseStockBtn.callOnClick()
                    }
                }
            }else{
                binding.conceptAndBKTv.setText("ALL")
                binding.chooseStockBtn.callOnClick()
            }

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
                    lowMarketValue = if (fromBKStrategyActivity) 0.0 else lowMarketValue * 100000000,
                    highMarketValue = if (fromBKStrategyActivity) 100000000000000.0 else highMarketValue * 100000000,
                    ztRange = ztRange,
                    averageDay = averageDay,
                    allowBelowCount = allowBelowCount,
                    divergeRate = divergeRate / 100,
                    adjustTimeAfterZT = ztAdjustTime,
                    endTime = endTime,
                    bkList = bkList
                )
                output(list)
            }


        }
    }

    private fun outputResult(strictParam: Strategy8Param) {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = StockRepo.strategy8(
                startMarketTime = strictParam.startMarketTime,
                endMarketTime = strictParam.endMarketTime,
                lowMarketValue = strictParam.lowMarketValue,
                highMarketValue = strictParam.highMarketValue,
                ztRange = strictParam.ztRange,
                adjustTimeAfterZT = strictParam.adjustTimeAfterZT,
                endTime = strictParam.endTime,
                bkList = strictParam.bkList,
                averageDay = strictParam.averageDay,
                divergeRate = strictParam.divergeRate,
                allowBelowCount = strictParam.allowBelowCount
            )
            output(list)
        }
    }

    private fun output(list: List<StockResult>) {
        lifecycleScope.launch(Dispatchers.Main) {

            binding.changyebanCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.firstZTCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }


            binding.resultLL.removeAllViews()
            var r = list
            if (!binding.changyebanCb.isChecked) {
                r = list.filter { return@filter !it.stock.code.startsWith("300") }
            }

            if(binding.firstZTCb.isChecked){
                r=list.filter { return@filter it.ztCountInRange==1 }
            }


            val ztCount = list.count { it.nextDayZT }
            binding.resultCount.text = "选股结果(${ztCount}/${r.size})"



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
                    ItemStockBinding.inflate(LayoutInflater.from(Injector.context)).apply {
                        this.stockName.text = stock.name

                        if (result.follow) {
                            this.root.setBackgroundColor(0x33333333)
                        }

                        if (result.nextDayZT) {
                            this.goodIv.visibility = View.VISIBLE
                        } else {
                            this.goodIv.visibility = View.GONE
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
                                        Injector.appDatabase.followDao()
                                            .deleteFollow(Follow(result.stock.code, 1))
                                        result.follow = false
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

    private fun updateUI(param: Strategy8Param) {
        binding.apply {
            this.endTimeTv.setText(param.endTime.toString())
            this.startMarketTime.setText(param.startMarketTime.toString())
            this.endMarketTime.setText(param.endMarketTime.toString())
            this.lowMarketValue.setText((param.lowMarketValue / 100000000).toString())
            this.highMarketValue.setText((param.highMarketValue / 100000000).toString())

            this.ztRangeTv.setText(param.ztRange.toString())
            this.ztAdjustTimeTv.setText(param.adjustTimeAfterZT.toString())
            this.allowBelowCountTv.setText(param.allowBelowCount.toString())
            this.averageDayTv.setText(param.averageDay.toString())
            this.divergeRateTv.setText((param.divergeRate*100).toString())
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