package com.liaobusi.stockman

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.liaobusi.stockman.databinding.ActivityBkstrategyBinding
import com.liaobusi.stockman.databinding.FragmentDiyBkBinding
import com.liaobusi.stockman.databinding.ItemDiyBkBinding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.databinding.LayoutPopupWindow2Binding
import com.liaobusi.stockman.databinding.LayoutPopupWindowBinding
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.DIYBk
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Hide
import com.liaobusi.stockman.db.color
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.BKResult
import com.liaobusi.stockman.repo.StockRepo

import com.liaobusi.stockman.repo.Strategy7Param
import com.liaobusi.stockman.repo.toFormatText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Integer.min
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date

class BKStrategyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBkstrategyBinding
    private var job: Job? = null

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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBkstrategyBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        supportActionBar?.title = "板块强势"

        lifecycleScope.launch {
            StockRepo.refreshData()
        }

        Injector.bkZTCountMap.clear()
        Injector.stockLianBanCountMap.clear()

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


        binding.chooseStockBtn.setOnClickListener {
            job?.cancel()
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

            val param = Strategy7Param(
                range = timeRange,
                endTime = endTime,
                allowBelowCount = allowBelowCount,
                averageDay = averageDay,
                divergeRate = divergeRate / 100,
            )

            outputResult(param)


        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun output(list: List<BKResult>) {
        lifecycleScope.launch(Dispatchers.Main) {

//            binding.kdCb.setOnCheckedChangeListener { compoundButton, b ->
//                output(list)
//            }


            binding.conceptCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.tradeCb.setOnCheckedChangeListener { compoundButton, b ->
                output(list)
            }

            binding.zfCb.setOnCheckedChangeListener { compoundButton, b ->
                if (b) {
                    binding.activeRateCb.isChecked = false
                    binding.zgbCb.isChecked = false
                    binding.ztsCb.isChecked = false
                }
                output(list)
            }

            binding.ztModeCb.setOnCheckedChangeListener { buttonView, isChecked ->
                output(list)
            }


            binding.activeRateCb.setOnCheckedChangeListener { compoundButton, b ->
                if (b) {
                    binding.zfCb.isChecked = false
                    binding.zgbCb.isChecked = false
                    binding.ztsCb.isChecked = false
                }
                output(list)
            }


            binding.zgbCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activeRateCb.isChecked = false
                    binding.zfCb.isChecked = false
                    binding.ztsCb.isChecked = false
                }
                output(list)
            }

            binding.ztsCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activeRateCb.isChecked = false
                    binding.zfCb.isChecked = false
                    binding.zgbCb.isChecked = false
                }
                output(list)
            }




            binding.nextChgCb.setOnCheckedChangeListener { buttonView, isChecked ->
                output(list)
            }

            var r = list
            r = mutableListOf<BKResult>().apply { addAll(r) }

//            if (binding.kdCb.isChecked) {
//                r = r.sortedBy {
//                    it.kd
//                }
//            }


            if (!binding.conceptCb.isChecked) {
                r = r.filter { it.bk.type != 1 }
            }

            if (!binding.tradeCb.isChecked) {
                r = r.filter { it.bk.type != 0 }
            }

            if (binding.zgbCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.highestLianBanCount.compareTo(v0.highestLianBanCount)
                })
            }

            if (binding.ztsCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.ztCount.compareTo(v0.ztCount)
                })
            }

            if (binding.activeRateCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.activeRate.compareTo(v0.activeRate)
                })
            }

            if (binding.zfCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator (v1.currentDayHistory?.chg
                        ?: -1000f).compareTo((v0.currentDayHistory?.chg ?: -1000f))
                })
            }

            val bkCodes = StringBuilder()
            binding.active20Cb.setOnCheckedChangeListener { compoundButton, b ->
                bkCodes.clear()
                var l = r
                if (binding.active20Cb.isChecked) {
                    l = r.subList(0, min(r.size, 20))
                }
                l.forEach { result ->
                    if (result.bk.code.startsWith("BK")) {
                        bkCodes.append(result.bk.code + ",")
                    }
                }
                if (bkCodes.endsWith(",")) {
                    bkCodes.deleteAt(bkCodes.length - 1)
                }
            }


            var l = r
            if (binding.active20Cb.isChecked) {
                l = r.subList(0, min(r.size, 20))
            }
            l.forEach { result ->
                if (result.bk.code.startsWith("BK")) {
                    bkCodes.append(result.bk.code + ",")
                }
            }
            if (bkCodes.endsWith(",")) {
                bkCodes.deleteAt(bkCodes.length - 1)
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
            binding.rv.layoutManager = LinearLayoutManager(this@BKStrategyActivity)
            binding.rv.adapter = ResultAdapter(r, bkCodes.toString(), binding.nextChgCb.isChecked)

        }
    }

    inner class ResultAdapter(
        private val data: MutableList<BKResult>,
        private val bkCodes: String,
        private val showNextChg: Boolean = false
    ) : RecyclerView.Adapter<ResultAdapter.VH>() {

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            job?.cancel()
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            job = lifecycleScope.launch(Dispatchers.IO) {
                while (true) {
                    delay(1000)
                    if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                        continue
                    }
                    val lm = recyclerView.layoutManager as LinearLayoutManager
                    val firstPos = lm.findFirstVisibleItemPosition()
                    val lastPos = lm.findLastVisibleItemPosition()
                    if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) {
                        continue
                    }

                    for (i in firstPos until lastPos + 1) {
                        val result = data[i]
                        if (result.currentDayHistory != null) {
                            val s =
                                Injector.appDatabase.bkDao().getBKByCode(result.bk.code)
                            val cur = Injector.appDatabase.historyBKDao().getHistoryByDate3(
                                result.bk.code,
                                result.currentDayHistory!!.date
                            )
                            val next =
                                if (result.nextDayHistory != null) Injector.appDatabase.historyBKDao()
                                    .getHistoryByDate3(
                                        result.bk.code,
                                        result.nextDayHistory!!.date
                                    ) else null
                            if (cur.chg != result.currentDayHistory!!.chg || next?.chg != result.nextDayHistory?.chg) {
                                data[i] = result.copy(
                                    bk = s!!,
                                    currentDayHistory = cur,
                                    nextDayHistory = next
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


        inner class VH(private val itemBinding: ItemStockBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            @RequiresApi(Build.VERSION_CODES.O)
            fun bind(result: BKResult, position: Int) {
                itemBinding.apply {
                    this.root.setBackgroundColor(0xffffffff.toInt())
                    if (result.diyBk != null) {
                        this.currentChg.visibility = View.GONE
                        this.stockName.text = result.diyBk!!.name
                        this.activeLabelTv.visibility = View.GONE
                        this.labelTv.visibility = View.GONE
                        this.root.setOnClickListener {
                            Strategy4Activity.openJXQSStrategy(
                                this.root.context,
                                result.diyBk!!.bkCodes,
                                binding.endTimeTv.text.toString()
                            )
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
                                LayoutPopupWindow2Binding.inflate(LayoutInflater.from(it.context))
                            val pw = PopupWindow(
                                b.root,
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.WRAP_CONTENT,
                                true
                            )


                            b.deleteBtn.setOnClickListener {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    Injector.appDatabase.diyBkDao().delete(result.diyBk!!)
                                    launch(Dispatchers.Main) {
                                        data.removeAt(position)
                                        notifyItemRemoved(position)
                                        Toast.makeText(
                                            this@BKStrategyActivity,
                                            "删除${result.diyBk!!.name}板块",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        pw.dismiss()
                                    }
                                }
                            }
                            pw.showAsDropDown(it, (ev?.x ?: 0f).toInt() + 50, -150)
                            return@setOnLongClickListener true
                        }
                        return@apply
                    }

                    if (result.follow) {
                        this.root.setBackgroundColor(0x33333333)
                    }

                    if (result.hide) {
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

                    if (result.currentDayHistory != null) {
                        result.currentDayHistory!!.apply {
                            currentChg.setTextColor(color)
                            currentChg.text = chg.toString()
                            if (isShowCurrentChg(binding.root.context)) {
                                currentChg.visibility = View.VISIBLE
                            } else {
                                currentChg.visibility = View.GONE
                            }
                        }
                    }

                    if (result.nextDayHistory != null) {
                        result.nextDayHistory!!.apply {
                            nextDayChg.setTextColor(color)
                            nextDayChg.text = chg.toString()
                            if (showNextChg) {
                                nextDayChg.visibility = View.VISIBLE
                            } else {
                                nextDayChg.visibility = View.GONE
                            }
                        }
                    }


                    lianbanCountFlagTv.visibility = View.GONE
                    if (binding.ztModeCb.isChecked) {
                        if (result.ztCount > 0) {
                            lianbanCountFlagTv.setBackgroundColor(
                                Color.valueOf(
                                    1f,
                                    0f,
                                    0f,
                                    result.ztCount / 15f
                                ).toArgb()
                            )
                            lianbanCountFlagTv.visibility = View.VISIBLE
                            lianbanCountFlagTv.text = result.ztCount.toString()
                        }
                    } else {
                        if (result.highestLianBanCount > 0) {
                            lianbanCountFlagTv.setBackgroundColor(
                                Color.valueOf(
                                    1f,
                                    0f,
                                    0f,
                                    result.highestLianBanCount / 15f
                                ).toArgb()
                            )
                            lianbanCountFlagTv.visibility = View.VISIBLE
                            lianbanCountFlagTv.text = result.highestLianBanCount.toString()
                        }
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
                            if (result.hide) {
                                hideBtn.text = "取消隐藏"
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

                            addToBtn.setOnClickListener {

                                pw.dismiss()

                                DIYBKDialogFragment(result.bk).show(
                                    this@BKStrategyActivity.supportFragmentManager,
                                    "diy_bk"
                                )

                            }

                            followBtn.setOnClickListener {
                                pw.dismiss()
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val p = data.indexOf(result)
                                    if (result.follow) {
                                        result.follow = false
                                        Injector.appDatabase.followDao()
                                            .deleteFollow(Follow(result.bk.code, 2))

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
                                            .insertFollow(Follow(result.bk.code, 2))

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

                            hideBtn.setOnClickListener {
                                pw.dismiss()
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val p = data.indexOf(result)
                                    if (result.hide) {
                                        result.hide = false
                                        Injector.appDatabase.hideDao()
                                            .deleteHide(Hide(result.bk.code, 2))
                                    } else {
                                        result.hide = true
                                        Injector.appDatabase.hideDao()
                                            .insertHide(Hide(result.bk.code, 2))
                                    }
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        notifyItemChanged(p)
                                    }

                                }
                            }
                        }

                        pw.showAsDropDown(it, (ev?.x ?: 0f).toInt(), -1100)
                        return@setOnLongClickListener true
                    }

                    root.setOnClickListener {
                        result.bk.openWeb(this@BKStrategyActivity)
                    }
                }


            }

        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultAdapter.VH {
            return VH(ItemStockBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onBindViewHolder(holder: ResultAdapter.VH, position: Int) {
            holder.bind(data[position], position)
        }

        override fun getItemCount(): Int {
            return data.size
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun outputResult(strictParam: Strategy7Param) {
        job?.cancel()
        lifecycleScope.launch(Dispatchers.IO) {
            val list = StockRepo.strategy7(
                endTime = strictParam.endTime,
                range = strictParam.range,
                allowBelowCount = strictParam.allowBelowCount,
                averageDay = strictParam.averageDay,
                divergeRate = strictParam.divergeRate,
            )
            val list2 = StockRepo.getDIYBks()
            output(list2 + list)
        }
    }

    data class SelectableItem<T>(val data: T, var selected: Boolean = false)
    class DIYBKDialogFragment(private val bk: BK) : DialogFragment() {

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
                    binding.rv.adapter = DIYBKAdapter(list.map {
                        SelectableItem(it, it.bkCodes.contains(bk.code, true))
                    }.toMutableList())
                }

            }

            binding.cancelBtn.setOnClickListener {
                dismiss()
            }

            binding.okBtn.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {

                    (binding.rv.adapter as DIYBKAdapter).save(bk)
                    dismiss()


                }

            }

            binding.bkCodeName.text = bk.code + "-" + bk.name

            binding.createBkBtn.setOnClickListener {

                val name = binding.diyBkName.editableText.toString()
                val codes = bk.code
                val code = Injector.sp.getInt("diy_bk_code", 10000) + 1
                val dsp = bk.code + "(${bk.name})"
                binding.diyBkName.setText("")
                val item = DIYBk("BK$code", name, codes, dsp)

                lifecycleScope.launch(Dispatchers.IO) {
                    Injector.appDatabase.diyBkDao().insert(item)
                    Injector.sp.edit().putInt("diy_bk_code", code).apply()
                    launch(Dispatchers.Main) {
                        (binding.rv.adapter as DIYBKAdapter).add(SelectableItem(item, true))
                    }

                }

            }

            return binding.root
        }


        inner class DIYBKAdapter(private val data: MutableList<SelectableItem<DIYBk>>) :
            RecyclerView.Adapter<DIYBKAdapter.VH>() {

            fun add(item: SelectableItem<DIYBk>) {
                data.add(item)
                notifyItemInserted(data.size - 1)
            }


            fun save(bk: BK) {
                data.forEach {
                    if (it.selected) {
                        if (!it.data.bkCodes.contains(bk.code)) {
                            val newBkCodes = it.data.bkCodes + ",${bk.code}"
                            newBkCodes.removePrefix(",")
                            val newDsp = it.data.dsp + ",${bk.code}(${bk.name})"
                            newDsp.removePrefix(",")
                            val newBean = it.data.copy(bkCodes = newBkCodes, dsp = newDsp)
                            Injector.appDatabase.diyBkDao().insert(newBean)
                        }
                    } else {
                        if (it.data.bkCodes.contains(bk.code)) {
                            val newCodes = if (it.data.bkCodes.startsWith(bk.code)) {
                                it.data.bkCodes.replaceFirst(bk.code, "").removePrefix(",")
                                    .replace("," + bk.code, "").removePrefix(",")
                            } else {
                                it.data.bkCodes.replace("," + bk.code, "").removePrefix(",")
                            }

                            val newDsp = if (it.data.dsp.startsWith("${bk.code}(${bk.name})")) {
                                it.data.bkCodes.replaceFirst("${bk.code}(${bk.name})", "")
                                    .removePrefix(",")
                            } else {
                                it.data.bkCodes.replace(",${bk.code}(${bk.name})", "")
                                    .replace(",${bk.code}", "").removePrefix(",")
                            }
                            val newBean = it.data.copy(bkCodes = newCodes, dsp = newDsp)
                            Injector.appDatabase.diyBkDao().insert(newBean)
                        }
                    }
                }
            }

            inner class VH(private val itemBinding: ItemDiyBkBinding) :
                RecyclerView.ViewHolder(itemBinding.root) {
                fun bind(item: SelectableItem<DIYBk>, position: Int) {
                    itemBinding.apply {
                        codeNameTv.text = item.data.code + "-" + item.data.name

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
                return data.size
            }

            override fun onBindViewHolder(holder: VH, position: Int) {
                holder.bind(data[position], position)
            }
        }


    }


}