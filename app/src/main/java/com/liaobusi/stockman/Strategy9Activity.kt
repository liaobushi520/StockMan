package com.liaobusi.stockman

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
import com.liaobusi.stockman.databinding.ActivityStrategy9Binding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.StockRepo
import com.liaobusi.stockman.repo.StockResult
import com.liaobusi.stockman.repo.toFormatText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class Strategy9Activity : AppCompatActivity() {

    private lateinit var binding: ActivityStrategy9Binding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategy9Binding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        supportActionBar?.title = "底部超跌横盘"
        if (intent.hasExtra("bk")) {
            val bk = intent.getStringExtra("bk")
            binding.conceptAndBKTv.setText(bk)
            binding.lowMarketValue.setText("0.0")
            binding.highMarketValue.setText("1000000.0")
        }

        if (intent.hasExtra("endTime")) {
            binding.endTimeTv.setText(intent.getStringExtra("endTime"))
        } else {
            binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))
        }

        binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))

        binding.preBtn.setOnClickListener {
            val c = binding.endTimeTv.editableText.toString()
            val d = SimpleDateFormat("yyyyMMdd").parse(c)

            val cal= Calendar.getInstance()
            cal.apply { timeInMillis = d.time }
                .add(Calendar.DAY_OF_MONTH, -1)

            val s = SimpleDateFormat("yyyyMMdd").format(cal.time)
            binding.endTimeTv.setText(s)
            binding.chooseStockBtn.callOnClick()

        }


        binding.postBtn.setOnClickListener {
            val c = binding.endTimeTv.editableText.toString()
            val d = SimpleDateFormat("yyyyMMdd").parse(c)

            val cal= Calendar.getInstance()
            cal.apply { timeInMillis = d.time }
                .add(Calendar.DAY_OF_MONTH, 1)
            val s = SimpleDateFormat("yyyyMMdd").format(cal.time)
            binding.endTimeTv.setText(s)
            binding.chooseStockBtn.callOnClick()
        }

        binding.chooseStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val startMarketTime = binding.startMarketTime.editableText.toString().toIntOrNull()
            if (startMarketTime == null) {
                Toast.makeText(this@Strategy9Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val endMarketTime = binding.endMarketTime.editableText.toString().toIntOrNull()
            if (endMarketTime == null) {
                Toast.makeText(this@Strategy9Activity, "上市时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lowMarketValue = binding.lowMarketValue.editableText.toString().toDoubleOrNull()

            val highMarketValue = binding.highMarketValue.editableText.toString().toDoubleOrNull()

            if (lowMarketValue == null || highMarketValue == null || lowMarketValue > highMarketValue) {
                Toast.makeText(this@Strategy9Activity, "市值不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val timeRange = binding.timeRangeTv.editableText.toString().toIntOrNull()
            if (timeRange == null) {
                Toast.makeText(this@Strategy9Activity, "查找区间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this@Strategy9Activity, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }


            val explosionDays =
                binding.explosionDaysTv.editableText.toString().toIntOrNull()
            if (explosionDays == null) {
                Toast.makeText(this@Strategy9Activity, "起爆维持天数不合法", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            val explosionTurnoverRateRadio =
                binding.explosionTurnoverRateRadioTv.editableText.toString().toFloatOrNull()
            if (explosionTurnoverRateRadio == null) {
                Toast.makeText(this@Strategy9Activity, "起爆换手率倍数不合法", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            val lowestTurnoverRadio =
                binding.lowestTurnoverRadioTv.editableText.toString().toFloatOrNull()
            if (lowestTurnoverRadio == null) {
                Toast.makeText(this@Strategy9Activity, "最低换手率不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }


            val maxIncrease =
                binding.maxIncreaseTv.editableText.toString().toFloatOrNull()
            if (maxIncrease == null) {
                Toast.makeText(this@Strategy9Activity, "最低涨幅不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }


            val bkList = checkBKInput() ?: return@setOnClickListener

            lifecycleScope.launch(Dispatchers.IO) {
                val list = StockRepo.strategy9(
                    startMarketTime = startMarketTime,
                    endMarketTime = endMarketTime,
                    lowMarketValue = lowMarketValue * 100000000,
                    highMarketValue = highMarketValue * 100000000,
                    range = timeRange,
                    endTime = endTime,
                    maxIncreaseAfterExplosion = maxIncrease / 100,
                    explosionDays = explosionDays,
                    explosionTurnoverRateRadio = explosionTurnoverRateRadio,
                    lowestTurnoverRadio = lowestTurnoverRadio,
                    bkList = bkList
                )
                output(list.stockResults)
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

            val newList = mutableListOf<StockResult>()
            r.forEach {
                if (it.follow) {
                    newList.add(0, it)
                } else {
                    newList.add(it)
                }

            }
            r = newList




            val ztCount = list.count { it.nextDayZT }
            binding.resultCount.text = "选股结果(${ztCount}/${r.size})"

            r.forEach { result ->
                val stock = result.stock
                val itemBinding =
                    ItemStockBinding.inflate(LayoutInflater.from(Injector.context)).apply {
                        this.stockName.text = stock.name
                        val formatText = result.toFormatText()
                        if (result.nextDayZT) {
                            this.goodIv.visibility = View.VISIBLE
                        } else {
                            this.goodIv.visibility = View.GONE
                        }


                        if (result.follow) {
                            this.root.setBackgroundColor(0x33333333)
                        }


                        if (formatText.isNotEmpty()) {
                            this.labelTv.visibility = View.VISIBLE
                            this.labelTv.text = formatText
                        } else {
                            this.labelTv.visibility = View.INVISIBLE
                        }
                        root.setOnClickListener {
                            stock.openWeb(this@Strategy9Activity)
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


    fun checkBKInput(): List<String>? {
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
            Toast.makeText(this@Strategy9Activity, "板块不合法,BK开头,逗号分割", Toast.LENGTH_LONG)
                .show()
            return null

        }
        return l
    }
}