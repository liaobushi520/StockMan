package com.liaobusi.stockman

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast

import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.liaobusi.stockman.databinding.ActivityFpactivityBinding
import com.liaobusi.stockman.databinding.ActivityHomeBinding
import com.liaobusi.stockman.databinding.ItemBkBinding
import com.liaobusi.stockman.databinding.ItemStock2Binding
import com.liaobusi.stockman.databinding.ItemStockBinding
import com.liaobusi.stockman.databinding.LayoutPopupWindow2Binding
import com.liaobusi.stockman.databinding.LayoutPopupWindowBinding
import com.liaobusi.stockman.databinding.LayoutStockPopupWindowBinding
import com.liaobusi.stockman.db.Follow
import com.liaobusi.stockman.db.Hide
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.ZTReplayBean
import com.liaobusi.stockman.db.color
import com.liaobusi.stockman.db.isBJStockExchange
import com.liaobusi.stockman.db.isChiNext
import com.liaobusi.stockman.db.isMainBoard
import com.liaobusi.stockman.db.isST
import com.liaobusi.stockman.db.isSTARMarket
import com.liaobusi.stockman.db.openDragonTigerRank
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.repo.BKResult
import com.liaobusi.stockman.repo.StockRepo
import com.liaobusi.stockman.repo.StockResult
import com.liaobusi.stockman.repo.Strategy4Param
import com.liaobusi.stockman.repo.Strategy7Param
import com.liaobusi.stockman.repo.StrategyResult
import com.liaobusi.stockman.repo.toFormatText

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import kotlin.math.abs
import kotlin.math.min

class FPActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFpactivityBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFpactivityBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.endTimeTv.setText(SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())))

        binding.chooseStockBtn.setOnClickListener {
            binding.root.requestFocus()
            val endTime =
                binding.endTimeTv.editableText.toString().toIntOrNull()
            if (endTime == null) {
                Toast.makeText(this, "截止时间不合法", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val param = Strategy7Param(
                range = 5,
                endTime = endTime,
                averageDay = 5,
                allowBelowCount = 5,
                divergeRate = 0.0 / 100,
            )
            outputResultBK(param)
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



        binding.bksRV.layoutManager = LinearLayoutManager(this)

        binding.stockRv.layoutManager = LinearLayoutManager(this)

        binding.stockRv.adapter = StockAdapter()
        binding.bksRV.adapter = BKsAdapter()
    }

    fun selectBK(bk: String) {
        binding.root.requestFocus()
        val endTime =
            binding.endTimeTv.editableText.toString().toIntOrNull()
        if (endTime == null) {
            Toast.makeText(this, "截止时间不合法", Toast.LENGTH_LONG).show()
            return
        }


        val param = Strategy4Param(
            startMarketTime = 19900101,
            endMarketTime = today(),
            lowMarketValue = 0.0,
            highMarketValue = 100000000000000.0,
            range = 5,
            endTime = endTime,
            averageDay = 5,
            allowBelowCount = 5,
            divergeRate = 0.00,
            abnormalRange = 5,
            abnormalRate = 2.0,
            bkList = listOf(bk),
            stockList = Injector.getSnapshot()
        )

        outputResultStock(param)
    }

    private fun outputResultStock(strictParam: Strategy4Param) {
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
            outputStock(list)
        }
    }

    private fun outputStock(strategyResult: StrategyResult) {
        val list = strategyResult.stockResults

        lifecycleScope.launch(Dispatchers.Main) {


            //活跃度
            binding.activityLevelCb.setOnCheckedChangeListener { compoundButton, b ->
                if (b) {
                    binding.zfSortCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                }
                outputStock(strategyResult)
            }

            //东热
            binding.popularitySortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zfSortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                }
                outputStock(strategyResult)
            }


            //大智慧热度
            binding.dzhPopularitySortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zfSortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                }
                outputStock(strategyResult)
            }

            //淘股吧热
            binding.tgbPopularitySortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zfSortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                }
                outputStock(strategyResult)
            }

            //同花顺人气
            binding.thsPopularitySortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.zfSortCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                }
                outputStock(strategyResult)
            }

            //涨幅
            binding.zfSortCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activityLevelCb.isChecked = false
                    binding.popularitySortCb.isChecked = false
                    binding.thsPopularitySortCb.isChecked = false
                    binding.tgbPopularitySortCb.isChecked = false
                    binding.dzhPopularitySortCb.isChecked = false
                }
                outputStock(strategyResult)
            }


            binding.ztPromotionCb.setOnCheckedChangeListener { buttonView, isChecked ->
                outputStock(strategyResult)
            }


            var r = list
            r = mutableListOf<StockResult>().apply { addAll(r) }

            if (binding.activityLevelCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }.forEach {
                        newList.addAll(it.second.sortedByDescending { it.activeRate })
                    }
                    r = newList
                } else {
                    Collections.sort(r, kotlin.Comparator { v0, v1 ->
                        return@Comparator v1.activeRate.compareTo(v0.activeRate)
                    })
                }
            }


            if (binding.zfSortCb.isChecked) {

                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedByDescending { it.stock.chg })
                        }
                    r = newList
                } else {
                    Collections.sort(r, kotlin.Comparator { v0, v1 ->
                        return@Comparator v1.currentDayHistory!!.chg.compareTo(v0.currentDayHistory!!.chg)
                    })
                }


            }

            if (binding.popularitySortCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.filter { it.popularity != null && it.popularity!!.rank > 0 }
                        .groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedBy { it.popularity?.rank ?: 1000 })
                        }
                    r = newList
                } else {
                    r = r.filter { it.popularity != null && it.popularity!!.rank > 0 }
                        .sortedBy { it.popularity?.rank ?: 1000 }
                }
            }

            if (binding.thsPopularitySortCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.filter { it.popularity != null && it.popularity!!.thsRank > 0 }
                        .groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedBy { it.popularity?.thsRank ?: 1000 })
                        }
                    r = newList
                } else {
                    r = r.filter { it.popularity != null && it.popularity!!.thsRank > 0 }
                        .sortedBy { it.popularity?.thsRank ?: 1000 }
                }
            }

            if (binding.tgbPopularitySortCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.filter { it.popularity != null && it.popularity!!.tgbRank > 0 }
                        .groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedBy { it.popularity?.tgbRank ?: 1000 })
                        }
                    r = newList
                } else {
                    r = r.filter { it.popularity != null && it.popularity!!.tgbRank > 0 }
                        .sortedBy { it.popularity?.tgbRank ?: 1000 }
                }
            }

            if (binding.dzhPopularitySortCb.isChecked) {
                if (binding.ztPromotionCb.isChecked) {
                    val newList = mutableListOf<StockResult>()
                    r.filter { it.popularity != null && it.popularity!!.dzhRank > 0 }
                        .groupBy { it.lianbanCount }.toList().sortedByDescending { it.first }
                        .forEach {
                            newList.addAll(it.second.sortedBy { it.popularity?.dzhRank ?: 1000 })
                        }
                    r = newList
                } else {
                    r = r.filter { it.popularity != null && it.popularity!!.dzhRank > 0 }
                        .sortedBy { it.popularity?.dzhRank ?: 1000 }
                }
            }

            (binding.stockRv.adapter as StockAdapter).setData(
                r.toMutableList(),
                if (binding.popularitySortCb.isChecked) 1 else if (binding.thsPopularitySortCb.isChecked) 2 else if (binding.tgbPopularitySortCb.isChecked) 3 else if (binding.dzhPopularitySortCb.isChecked) 4 else 0
            )
        }
    }

    var job: Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun outputResultBK(strictParam: Strategy7Param) {
        job?.cancel()
        job = lifecycleScope.launch(Dispatchers.IO) {
            val list = StockRepo.strategy7(
                endTime = strictParam.endTime,
                range = strictParam.range,
                allowBelowCount = strictParam.allowBelowCount,
                averageDay = strictParam.averageDay,
                divergeRate = strictParam.divergeRate,
            )
            outputBk(list)
        }
    }

    private fun outputBk(list: List<BKResult>) {
        lifecycleScope.launch(Dispatchers.Main) {

            binding.ztModeCb.setOnCheckedChangeListener { buttonView, isChecked ->
                outputBk(list)
            }


            binding.conceptCb.setOnCheckedChangeListener { compoundButton, b ->
                outputBk(list)
            }

            binding.tradeCb.setOnCheckedChangeListener { compoundButton, b ->
                outputBk(list)
            }


            binding.zfBkCb.setOnCheckedChangeListener { compoundButton, b ->
                if (b) {
                    binding.activeRateBkCb.isChecked = false
                    binding.zgbCb.isChecked = false
                    binding.ztsCb.isChecked = false
                }
                outputBk(list)
            }

            binding.activeRateBkCb.setOnCheckedChangeListener { compoundButton, b ->
                if (b) {
                    binding.zfBkCb.isChecked = false
                    binding.zgbCb.isChecked = false
                    binding.ztsCb.isChecked = false
                }
                outputBk(list)
            }



            binding.zgbCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activeRateBkCb.isChecked = false
                    binding.zfBkCb.isChecked = false
                    binding.ztsCb.isChecked = false
                }
                outputBk(list)
            }

            binding.ztsCb.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    binding.activeRateBkCb.isChecked = false
                    binding.zfBkCb.isChecked = false
                    binding.zgbCb.isChecked = false
                }
                outputBk(list)
            }

            var r = list
            r = mutableListOf<BKResult>().apply { addAll(r) }

            if (!binding.conceptCb.isChecked) {
                r = r.filter { it.bk.type != 1 }
            }

            if (!binding.tradeCb.isChecked) {
                r = r.filter { it.bk.type != 0 }
            }



            if (binding.zfBkCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator (v1.currentDayHistory?.chg
                        ?: -1000f).compareTo((v0.currentDayHistory?.chg ?: -1000f))
                })
            }

            if (binding.ztsCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.ztCount.compareTo(v0.ztCount)
                })
            }

            if (binding.zgbCb.isChecked) {
                Collections.sort(r, kotlin.Comparator { v0, v1 ->
                    return@Comparator v1.highestLianBanCount.compareTo(v0.highestLianBanCount)
                })
            }

            (binding.bksRV.adapter as BKsAdapter).setData(r.toMutableList())
        }
    }

    inner class BKsAdapter : RecyclerView.Adapter<BKsAdapter.VH>() {

        private val data = mutableListOf<BKResult>()

        fun setData(data: MutableList<BKResult>) {

            this.data.clear()
            this.data.addAll(data)
            notifyDataSetChanged()
            selectedItem = data.firstOrNull()
            if (selectedItem != null)
                selectBK(selectedItem!!.bk.code)

        }

        private var selectedItem: BKResult? = null
        private var job: Job? = null

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            job?.cancel()
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            job = lifecycleScope.launch(Dispatchers.IO) {
                while (true) {
                    delay(1000)
                    if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE||recyclerView.isLayoutRequested) {
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

        inner class VH(private val itemBinding: ItemBkBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            @RequiresApi(Build.VERSION_CODES.O)
            fun bind(result: BKResult, position: Int) {
                itemBinding.apply {


                    if (selectedItem?.bk?.code == result.bk.code) {
                        this.root.setBackgroundColor(0xffffff00.toInt())
                    } else {
                        this.root.setBackgroundColor(0xffffffff.toInt())
                    }




                    if (result.follow) {
                        this.root.setBackgroundColor(0x33333333)
                    }

                    if (result.hide) {
                        this.root.setBackgroundColor(0xffB0E0E6.toInt())
                    }

                    this.bkName.text = result.bk.name


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
                            nextDayChg.visibility = View.VISIBLE
                        }
                    }else{
                        nextDayChg.visibility = View.GONE
                    }


                    flagTv.visibility = View.INVISIBLE
                     if (binding.ztModeCb.isChecked||binding.ztsCb.isChecked)  {
                        if (result.ztCount > 0) {
                            flagTv.setBackgroundColor(
                                Color.valueOf(
                                    1f,
                                    0f,
                                    0f,
                                    result.ztCount / 15f
                                ).toArgb()
                            )
                            flagTv.visibility = View.VISIBLE
                            flagTv.text = result.ztCount.toString()
                        }
                    } else {
                        if (result.highestLianBanCount > 0) {
                            flagTv.setBackgroundColor(
                                Color.valueOf(
                                    1f,
                                    0f,
                                    0f,
                                    result.highestLianBanCount / 15f
                                ).toArgb()
                            )
                            flagTv.visibility = View.VISIBLE
                            flagTv.text = result.highestLianBanCount.toString()
                        }
                    }




                    root.setOnLongClickListener {

                        val b =
                            LayoutPopupWindowBinding.inflate(LayoutInflater.from(it.context))


                        val pw = PopupWindow(
                            b.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            true
                        )

                        b.apply {
                            if (result.follow) {
                                followBtn.text = "取消关注"
                            }
                            if (result.hide) {
                                hideBtn.text = "取消隐藏"
                            }


                            val codes = result.bk.code

                            ztrcBtn.setOnClickListener {
                                Strategy2Activity.openZTRCStrategy(
                                    this@FPActivity,
                                    codes,
                                    binding.endTimeTv.text.toString()
                                )
                            }

                            ztxpBtn.setOnClickListener {
                                Strategy1Activity.openZTXPStrategy(
                                    this@FPActivity,
                                    codes,
                                    binding.endTimeTv.text.toString()
                                )
                            }

                            jxqsBtn.setOnClickListener {
                                Strategy4Activity.openJXQSStrategy(
                                    this@FPActivity,
                                    codes,
                                    binding.endTimeTv.text.toString()
                                )
                            }

                            dfcfBtn.setOnClickListener {
                                result.bk.openWeb(this@FPActivity)
                            }


                            dbhpBtn.setOnClickListener {
                                Strategy6Activity.openDBHPStrategy(
                                    this@FPActivity,
                                    codes,
                                    binding.endTimeTv.text.toString()
                                )
                            }

                            ztqsBtn.setOnClickListener {
                                Strategy7Activity.openZTQSStrategy(
                                    this@FPActivity,
                                    codes,
                                    binding.endTimeTv.text.toString()
                                )
                            }

                            addToBtn.setOnClickListener {

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
                        if (selectedItem != null) {
                            val i = data.indexOf(selectedItem)
                            notifyItemChanged(i)
                        }

                        selectedItem = result
                        val index = data.indexOf(selectedItem)
                        notifyItemChanged(index)
                        selectBK(result.bk.code)

                    }
                }


            }

        }

        override fun onCreateViewHolder(parent: ViewGroup, p1: Int): VH {
            return VH(ItemBkBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(data[position], position)
        }
    }


    inner class StockAdapter(

    ) :
        RecyclerView.Adapter<StockAdapter.VH>() {

        private val data = mutableListOf<StockResult>()

        private var popularitySort: Int = 0


        fun setData(data: MutableList<StockResult>, popularitySort: Int = 0) {
            this.data.clear()
            this.data.addAll(data)
            this.popularitySort = popularitySort
            notifyDataSetChanged()
        }


        override fun onViewDetachedFromWindow(holder: VH) {
            super.onViewDetachedFromWindow(holder)


        }


        private var job: Job? = null

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            job?.cancel()
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            job = lifecycleScope.launch(Dispatchers.IO) {
                while (true) {
                    delay(1200)
                    if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE || !Injector.activityActive) {
                        continue
                    }
                    val lm = recyclerView.layoutManager as LinearLayoutManager
                    val firstPos = lm.findFirstVisibleItemPosition()
                    val lastPos = lm.findLastVisibleItemPosition()
                    if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) {
                        continue
                    }


                    if (data.size > 0) {
                        for (i in firstPos until lastPos + 1) {

                            val result = data[i]
                            if (result.currentDayHistory != null && !result.isGroupHeader) {
                                val s =
                                    Injector.appDatabase.stockDao()
                                        .getStockByCode(result.stock.code)
                                val h = Injector.appDatabase.historyStockDao().getHistoryByDate3(
                                    result.stock.code,
                                    result.currentDayHistory!!.date
                                )
                                val n =
                                    if (result.nextDayHistory != null) Injector.appDatabase.historyStockDao()
                                        .getHistoryByDate3(
                                            result.stock.code,
                                            result.nextDayHistory!!.date
                                        ) else null
                                if (h.chg != result.currentDayHistory!!.chg || n?.chg != result.nextDayHistory?.chg) {
                                    val changeRate =
                                        if (h.chg != result.currentDayHistory!!.chg) (h.chg - result.currentDayHistory!!.chg)
                                        else (if (n == null || result.nextDayHistory == null) 0f
                                        else n.chg - result.nextDayHistory!!.chg)
                                    data[i] = result.copy(
                                        stock = s,
                                        currentDayHistory = h,
                                        nextDayHistory = n,
                                        changeRate = changeRate
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
        }


        inner class VH(val binding: ItemStock2Binding) : RecyclerView.ViewHolder(binding.root) {

            var anim: ValueAnimator? = null

            @RequiresApi(Build.VERSION_CODES.O)
            fun bind(result: StockResult, position: Int) {


                binding.contentLL.visibility = View.VISIBLE
                binding.stockName.setOnClickListener(null)
                binding.stockName.isClickable = false


                val stock = result.stock
                binding.apply {
                    if (result.follow != null) {
                        this.root.setBackgroundColor(0x33333333)
                    } else {
                        this.root.setBackgroundColor(0xffffffff.toInt())
                    }

                    if (result.changeRate != 0f) {
                        colorView.visibility = View.VISIBLE
                        if (colorView.tag != result.stock.code) {
                            anim?.cancel()
                        }

                        anim =
                            ValueAnimator.ofFloat(0f, min(abs(result.changeRate), 0.9f), 0f).apply {
                                duration = 1700
                                startDelay = (1700 * (1 - (anim?.animatedFraction ?: 1f))).toLong()

                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationStart(animation: Animator) {
                                        super.onAnimationStart(animation)
                                        colorView.alpha = 1f
                                        if (result.changeRate > 0) {
                                            colorView.setBackgroundColor(Color.RED)
                                        } else {
                                            colorView.setBackgroundColor(STOCK_GREEN)
                                        }
                                    }

                                    override fun onAnimationEnd(animation: Animator) {
                                        super.onAnimationEnd(animation)
                                        colorView.alpha = 0f
                                        colorView.setBackgroundColor(Color.TRANSPARENT)
                                        result.changeRate = 0f
                                    }
                                })
                                this.addUpdateListener {
                                    colorView.alpha = it.animatedValue as Float
                                }
                                start()
                            }
                        colorView.tag = stock.code
                    } else {
                        colorView.visibility = View.GONE
                    }

                    if (result.ztReplay != null && result.expandReason) {
                        this.expoundTv.visibility = View.VISIBLE
                        this.expoundTv.text =
                            "${result.ztReplay!!.time}\n${result.ztReplay!!.expound}"
                    } else {
                        this.expoundTv.visibility = View.GONE
                    }

                    if (result.expandPOPReason && result.popularity != null) {
                        this.popReasonTv.visibility = View.VISIBLE
                        this.popReasonTv.text = "${result.popularity?.explain}"
                    } else {
                        this.popReasonTv.visibility = View.GONE
                    }

                    if (isShowLianBanFlag(binding.root.context)) {
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

                    this.stockName.text = stock.name
                    this.stockName.setTextColor(result.groupColor)


                    if (result.dargonTigerRank != null) {
                        this.dragonFlagIv.visibility = View.VISIBLE
                        this.stockName.setOnClickListener {
                            result.stock.openDragonTigerRank(this@FPActivity)
                        }
                    } else {
                        this.dragonFlagIv.visibility = View.GONE
                    }

                    this.dragonFlagIv.setOnClickListener {
                        result.stock.openDragonTigerRank(this@FPActivity)
                    }


                    if (result.currentDayHistory != null) {
                        currentChg.setTextColor(result.currentDayHistory!!.color)
                        currentChg.text = result.currentDayHistory!!.chg.toString()
                        if (isShowCurrentChg(binding.root.context)) {
                            currentChg.visibility = View.VISIBLE
                        } else {
                            currentChg.visibility = View.GONE
                        }
                    } else {
                        currentChg.visibility = View.GONE
                    }

                    if (result.nextDayHistory != null) {
                        nextDayChg.setTextColor(result.nextDayHistory!!.color)
                        nextDayChg.text = result.nextDayHistory!!.chg.toString()
                        if (isShowNextChg(binding.root.context)) {
                            nextDayChg.visibility = View.VISIBLE
                        } else {
                            nextDayChg.visibility = View.GONE
                        }

                    } else {
                        nextDayChg.visibility = View.GONE
                    }


                    if (popularitySort != 0) {
                        if (result.popularity != null) {
                            this.activeLabelTv.visibility = View.VISIBLE
                            this.activeLabelTv.text =
                                if (popularitySort == 1) result.popularity?.rank.toString() else if (popularitySort == 2) result.popularity?.thsRank.toString() else if (popularitySort == 4) result.popularity?.dzhRank.toString() else result.popularity?.tgbRank.toString()
                        } else {
                            this.activeLabelTv.visibility = View.INVISIBLE
                        }
                    } else {
                        if (result.activeRate > 2) {
                            this.activeLabelTv.visibility = View.VISIBLE
                            this.activeLabelTv.text = result.activeRate.toInt().toString()
                        } else {
                            this.activeLabelTv.visibility = View.INVISIBLE
                        }
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
                        stock.openWeb(this@FPActivity)
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

                        if (result.follow != null) {
                            b.followBtn.text = "取消关注"
                        }

                        if (result.follow?.stickyOnTop == 1) {
                            b.stickyOnTopBtn.text = "取消置顶"
                        }

                        if (result.expandReason) {
                            b.expandReasonBtn.text = "折叠涨停原因"
                        }

                        if (!result.zt) {
                            b.expandReasonBtn.visibility = View.GONE
                        } else {
                            b.expandReasonBtn.visibility = View.VISIBLE
                        }


                        if (result.popularity == null) {
                            b.expandPOPReasonBtn.visibility = View.GONE
                        } else {
                            b.expandPOPReasonBtn.visibility = View.VISIBLE
                        }

                        if (result.expandPOPReason) {
                            b.expandPOPReasonBtn.text = "折叠热度原因"
                        }


                        val pw = PopupWindow(
                            b.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            true
                        )

                        b.expandReasonBtn.setOnClickListener {
                            val p = data.indexOf(result)
                            result.expandReason = !result.expandReason
                            notifyItemChanged(p)
                            pw.dismiss()
                        }

                        b.expandPOPReasonBtn.setOnClickListener {
                            val p = data.indexOf(result)
                            result.expandPOPReason = !result.expandPOPReason
                            notifyItemChanged(p)
                            pw.dismiss()
                        }

                        b.relatedConceptBtn.setOnClickListener {
                            pw.dismiss()
                            StockInfoFragment(
                                result.stock,
                                this@FPActivity.binding.endTimeTv.editableText.toString()
                            ).show(
                                this@FPActivity.supportFragmentManager,
                                "stock_info"
                            )
                        }
                        b.dragonTigerRankBtn.setOnClickListener {
                            pw.dismiss()
                            result.stock.openDragonTigerRank(this@FPActivity)
                        }

                        //仅关注不置顶
                        b.followBtn.setOnClickListener {
                            pw.dismiss()
                            lifecycleScope.launch(Dispatchers.IO) {
                                val p = data.indexOf(result)
                                if (result.follow != null) {
                                    Injector.appDatabase.followDao()
                                        .deleteFollow(Follow(result.stock.code, 1))

                                    result.follow = null

                                    lifecycleScope.launch(Dispatchers.Main) {
                                        notifyItemChanged(p)
                                    }

                                } else {
                                    result.follow = Follow(result.stock.code, 1, 0)
                                    Injector.appDatabase.followDao()
                                        .insertFollow(result.follow!!)
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        notifyItemChanged(p)
                                    }

                                }


                            }
                        }

                        b.stickyOnTopBtn.setOnClickListener {
                            pw.dismiss()
                            lifecycleScope.launch(Dispatchers.IO) {
                                val p = data.indexOf(result)
                                if (result.follow?.stickyOnTop == 1) {
                                    val n = Follow(result.stock.code, 1, 0)
                                    Injector.appDatabase.followDao().insertFollow(n)
                                    result.follow = n

                                    lifecycleScope.launch(Dispatchers.Main) {
                                        data.remove(result)
                                        notifyItemRemoved(p)
                                        delay(300)
                                        data.add(itemCount - 1, result)
                                        notifyItemInserted(itemCount - 1)
                                    }

                                } else {
                                    val n = Follow(result.stock.code, 1, 1)
                                    result.follow = n
                                    Injector.appDatabase.followDao()
                                        .insertFollow(n)
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

                        val arr = IntArray(2)
                        binding.root.getLocationInWindow(arr)
                        pw.showAsDropDown(
                            it,
                            (ev?.x ?: 0f).toInt(),
                            -500 - (binding.root.height - (ev!!.y - arr[1])).toInt()
                        )
                        return@setOnLongClickListener true
                    }

                }
            }

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemStock2Binding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(data[position], position)
        }


    }


}