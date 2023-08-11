package com.liaobusi.stockman

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.databinding.ActivityBkstrategyBinding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.databinding.LayoutPopupWindowBinding
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Hide
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

class BKStrategyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBkstrategyBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBkstrategyBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        supportActionBar?.title = "板块强势"
        binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))

        binding.line5Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@BKStrategyActivity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val param = Strategy7Param(
                range = 5,
                endTime = endTime,
                averageDay = 5,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 5 else 0,
                divergeRate = 0.0 / 100,
            )
            updateUI(param)
            outputResult(param)
        }

        binding.line10Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@BKStrategyActivity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val param = Strategy7Param(
                range = 10,
                endTime = endTime,
                averageDay = 10,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 10 else 0,
                divergeRate = 0.0 / 100
            )
            updateUI(param)
            outputResult(param)
        }


        binding.line20Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@BKStrategyActivity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val param = Strategy7Param(
                range = 20,
                endTime = endTime,
                averageDay = 20,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 20 else 0,
                divergeRate = 0.0 / 100
            )
            updateUI(param)
            outputResult(param)
        }


        binding.line30Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@BKStrategyActivity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val param = Strategy7Param(
                range = 30,
                endTime = endTime,
                averageDay = 30,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 30 else 0,
                divergeRate = 0.0 / 100
            )
            updateUI(param)
            outputResult(param)
        }

        binding.line60Btn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@BKStrategyActivity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val param = Strategy7Param(
                range = 60,
                endTime = endTime,
                averageDay = 60,
                allowBelowCount = if (binding.onlyActiveRateCb.isChecked) 60 else 0,
                divergeRate = 0.0 / 100
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


        binding.chooseStockBtn.setOnClickListener {
            binding.root.requestFocus()

            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@BKStrategyActivity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val timeRange = binding.timeRangeTv.editableText.toString().toIntOrNull()
            if (timeRange == null) {
                Toast.makeText(this@BKStrategyActivity, "查找区间不合法", Toast.LENGTH_LONG).show()
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
                    this@BKStrategyActivity,
                    "允许均线下方运行次数不合法",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val averageDay =
                binding.averageDayTv.editableText.toString().toIntOrNull()
            if (averageDay == null) {
                Toast.makeText(this@BKStrategyActivity, "均线取值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val divergeRate =
                binding.divergeRateTv.editableText.toString().toDoubleOrNull()
            if (divergeRate == null) {
                Toast.makeText(
                    this@BKStrategyActivity,
                    "收盘价与均线偏差率取值不合法",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val list = StockRepo.strategy7(
                    range = timeRange,
                    endTime = endTime,
                    allowBelowCount = allowBelowCount,
                    averageDay = averageDay,
                    divergeRate = divergeRate / 100,
                )
                output(list)
            }

        }


    }

    private fun output(list: List<BKResult>) {
        lifecycleScope.launch(Dispatchers.Main) {

//            binding.kdCb.setOnCheckedChangeListener { compoundButton, b ->
//                output(list)
//            }

            binding.activeRateCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.conceptCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.tradeCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.resultLL.removeAllViews()
            var r = list
            r = mutableListOf<BKResult>().apply { addAll(r) }

//            if (binding.kdCb.isChecked) {
//                r = r.sortedBy {
//                    it.kd
//                }
//            }

            if (binding.activeRateCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.activeRate.compareTo(v0.activeRate)
                })
            }

            if (!binding.conceptCb.isChecked) {
                r = r.filter { it.bk.type != 1 }
            }

            if (!binding.tradeCb.isChecked) {
                r = r.filter { it.bk.type != 0 }
            }


            val newList = mutableListOf<BKResult>()
            r.forEach {

                if (it.follow) {
                    newList.add(0, it)
                } else {
                    newList.add(it)
                }

            }

            r = newList

            var t = 0
            if (binding.conceptCb.isChecked) {
                t += Injector.conceptBks.size
            }
            if (binding.tradeCb.isChecked) {
                t += Injector.tradeBks.size
            }


            binding.resultCount.text = "板块结果(${r.size}/${t})"

            val bkCodes = StringBuilder()
            r.forEach { result ->
                if (result.bk.code.startsWith("BK")) {
                    bkCodes.append(result.bk.code + ",")
                }

            }
            if (bkCodes.endsWith(",")) {
                bkCodes.deleteAt(bkCodes.length - 1)
            }



            r.forEach { result ->


                val itemBinding =
                    ItemStockBinding.inflate(LayoutInflater.from(Injector.context)).apply {

                        if (result.follow) {
                            this.root.setBackgroundColor(0x33333333)
                        }

                        if(result.hide){
                            this.root.setBackgroundColor(0xffB0E0E6.toInt())
                        }


                        this.stockName.text = result.bk.name
                        val formatText = result.toFormatText()
                        if (formatText.isNotEmpty()) {
                            this.labelTv.visibility = View.VISIBLE
                            this.labelTv.text = formatText
                        } else {
                            this.labelTv.visibility = View.INVISIBLE
                        }

                        if (result.activeRate > 1) {
                            this.activeLabelTv.visibility = View.VISIBLE
                            this.activeLabelTv.text = result.activeRate.toInt().toString()
                        } else {
                            this.activeLabelTv.visibility = View.INVISIBLE
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
                                LayoutPopupWindowBinding.inflate(LayoutInflater.from(it.context))


                            val pw = PopupWindow(
                                b.root,
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.WRAP_CONTENT,
                                true
                            )

                            b.apply {
                                if (result.follow) {
                                    followBtn.text = "取消关注"
                                }
                                if(result.hide){
                                    hideBtn.text="取消隐藏"
                                }



                                val withAllBks = binding.withAllBksCb.isChecked
                                val codes = if (withAllBks) bkCodes.toString() else result.bk.code

                                ztrcBtn.setOnClickListener {
                                    Strategy2Activity.openZTRCStrategy(
                                        this@BKStrategyActivity,
                                        codes,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                ztxpBtn.setOnClickListener {
                                    Strategy1Activity.openZTXPStrategy(
                                        this@BKStrategyActivity,
                                        codes,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                jxqsBtn.setOnClickListener {
                                    Strategy4Activity.openJXQSStrategy(
                                        this@BKStrategyActivity,
                                        codes,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                dfcfBtn.setOnClickListener {
                                    result.bk.openWeb(this@BKStrategyActivity)
                                }


                                dbhpBtn.setOnClickListener {
                                    Strategy6Activity.openDBHPStrategy(
                                        this@BKStrategyActivity,
                                        codes,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                ztqsBtn.setOnClickListener {
                                    Strategy7Activity.openZTQSStrategy(
                                        this@BKStrategyActivity,
                                        codes,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                followBtn.setOnClickListener {
                                    pw.dismiss()
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        if (result.follow) {
                                            result.follow = false
                                            Injector.appDatabase.followDao()
                                                .deleteFollow(Follow(result.bk.code, 2))
                                            output(list)
                                        } else {
                                            result.follow = true
                                            Injector.appDatabase.followDao()
                                                .insertFollow(Follow(result.bk.code, 2))
                                            output(list)
                                        }
                                    }
                                }

                                hideBtn.setOnClickListener {
                                    pw.dismiss()
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        if(result.hide){
                                            result.hide = false
                                            Injector.appDatabase.hideDao()
                                                .deleteHide(Hide(result.bk.code, 2))
                                        }else{
                                            result.hide = true
                                            Injector.appDatabase.hideDao()
                                                .insertHide(Hide(result.bk.code, 2))
                                        }
                                        output(list)
                                    }
                                }
                            }

                            pw.showAsDropDown(it, (ev?.x ?: 0f).toInt(), -1000)
                            return@setOnLongClickListener true
                        }

                        root.setOnClickListener {
                            val cbId = binding.cbGroup.checkedRadioButtonId

                            when (cbId) {
                                R.id.s1Cb -> {
                                    Strategy1Activity.openZTXPStrategy(
                                        this@BKStrategyActivity,
                                        result.bk.code,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                R.id.s2Cb -> {
                                    Strategy2Activity.openZTRCStrategy(
                                        this@BKStrategyActivity,
                                        result.bk.code,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                R.id.s3Cb -> {
                                    Strategy4Activity.openJXQSStrategy(
                                        this@BKStrategyActivity,
                                        result.bk.code,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                R.id.s6Cb -> {
                                    val i = Intent(
                                        this@BKStrategyActivity,
                                        Strategy6Activity::class.java
                                    ).apply {
                                        putExtra("bk", result.bk.code)
                                    }
                                    startActivity(i)
                                }

                                R.id.s8Cb -> {
                                    Strategy7Activity.openZTQSStrategy(
                                        this@BKStrategyActivity,
                                        result.bk.code,
                                        binding.endTimeTv.text.toString()
                                    )
                                }

                                else -> {
                                    result.bk.openWeb(this@BKStrategyActivity)
                                }

                            }

                        }
                    }
                binding.resultLL.addView(itemBinding.root)
            }
        }
    }

    private fun updateUI(param: Strategy7Param) {
        binding.apply {
            this.endTimeTv.setText(param.endTime.toString())
            this.timeRangeTv.setText(param.range.toString())
            this.allowBelowCountTv.setText(param.allowBelowCount.toString())
            this.averageDayTv.setText(param.averageDay.toString())
            this.divergeRateTv.setText((param.divergeRate * 100).toString())
        }
    }

    private fun outputResult(strictParam: Strategy7Param) {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = StockRepo.strategy7(
                endTime = strictParam.endTime,
                range = strictParam.range,
                allowBelowCount = strictParam.allowBelowCount,
                averageDay = strictParam.averageDay,
                divergeRate = strictParam.divergeRate,
            )
            output(list)
        }
    }


}