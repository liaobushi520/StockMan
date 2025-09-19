package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.liaobusi.stockman.databinding.ActivitySettingBinding
import com.liaobusi.stockman.db.DIYBk
import com.liaobusi.stockman.db.FPResponse
import com.liaobusi.stockman.db.ZTReplayBean
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import androidx.core.content.edit


fun isShowHiddenStockAndBK(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("show_hidden_stock_bk", false)
}

fun isShowST(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("show_ST", false)
}

//true 同花顺 false 韭研
fun isFPSource(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("fp_source", true)
}


//true 东方财富 false 百度
fun isRealTimeDataSource(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("realtime_data_source", true)
}




fun howDayShowZTFlag(context: Context): Int {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getInt("how_day_show_zt_flag", 1)
}


fun trackingType(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("trackingType", false)
}

fun isFocusLB(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("focusLB", false)
}

fun isShowLianBanFlag(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("show_lianban_count_flag", true)
}

fun isShowCurrentChg(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("show_current_chg", false)
}

fun isShowNextChg(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("show_next_chg", false)
}

fun isActiveRateWithPopularity(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("active_rate_with_popularity", false)
}


class SettingActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingBinding

    companion object {
        fun startSettingActivity(context: Context) {

            val i = Intent(context, SettingActivity::class.java)
            context.startActivity(i)


        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "设置"

        val sp = getSharedPreferences("app", Context.MODE_PRIVATE)

        val show = sp.getBoolean("show_hidden_stock_bk", false)
        binding.hideSwitch.isChecked = show
        binding.hideSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("show_hidden_stock_bk", isChecked).apply()
        }



        binding.fixedDateEt.setText(today().toString())
        binding.fixedDate2Et.setText(today().toString())

        binding.fixDataBtn.setOnClickListener {
            val date = binding.fixedDateEt.editableText?.toString()?.toIntOrNull()
            val date2 = binding.fixedDate2Et.editableText?.toString()?.toIntOrNull()
            if (date != null && date2 != null && date2 >= date) {
                GlobalScope.launch {
                    StockRepo.getHistoryStocks(date, date2)
                    StockRepo.getHistoryBks(360, date2)
                }
            }

        }

        val showST = sp.getBoolean("show_ST", false)
        binding.showSTSwitch.isChecked = showST
        binding.showSTSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("show_ST", isChecked).apply()
        }

        val fpSource = sp.getBoolean("fp_source", true)
        binding.fpSwitch.isChecked =fpSource
        binding.fpSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("fp_source", isChecked).apply()
        }


        val realDataSource = sp.getBoolean("realtime_data_source", true)
        binding.realTimeSource.isChecked =realDataSource
        binding.realTimeSource.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("realtime_data_source", isChecked).apply()
        }






        val focusLB = sp.getBoolean("focusLB", false)
        binding.focusLB.isChecked = focusLB
        binding.focusLB.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("focusLB", isChecked).apply()
            Injector.startTracking()
        }


        val trackingType = sp.getBoolean("trackingType", false)
        binding.trackingType.isChecked = trackingType
        binding.trackingType.setOnCheckedChangeListener { _, isChecked ->
            sp.edit() { putBoolean("trackingType", isChecked) }
            Injector.trackerType = isChecked
            Injector.startTracking()
        }


        binding.autoRefresh.isChecked = sp.getBoolean("auto_refresh", false)
        binding.autoRefresh.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("auto_refresh", isChecked).apply()
            Injector.autoRefresh(isChecked)
        }


        binding.showCurrentChg.isChecked = sp.getBoolean("show_current_chg", false)
        binding.showCurrentChg.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit() { putBoolean("show_current_chg", isChecked) }
        }


        binding.showNextChg.isChecked = sp.getBoolean("show_next_chg", false)
        binding.showNextChg.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit() { putBoolean("show_next_chg", isChecked) }
        }

        binding.activeRateWithPopularity.isChecked =
            sp.getBoolean("active_rate_with_popularity", false)
        binding.activeRateWithPopularity.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("active_rate_with_popularity", isChecked).apply()
        }



        binding.showLianbanCount.isChecked = sp.getBoolean("show_lianban_count_flag", true)
        binding.showLianbanCount.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit() { putBoolean("show_lianban_count_flag", isChecked) }
        }


        val day = sp.getInt("how_day_show_zt_flag", 1)
        binding.howDayShowZTFlagEt.setText(day.toString())
        binding.howDayShowZTFlagConfirmBtn.setOnClickListener {
            val d = binding.howDayShowZTFlagEt.editableText.toString().toIntOrNull()
            sp.edit() { putInt("how_day_show_zt_flag", d ?: 1) }

        }


        binding.jiexiBtn.setOnClickListener {
            val s = binding.fpEt.editableText.toString()
            if (s.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val rsp = Gson().fromJson(s, FPResponse::class.java)
                    val list = mutableListOf<ZTReplayBean>()
                    rsp.data.forEach {
                        val groupName = it.name
                        val reason = it.reason?:""
                        val date = it.date!!.replace("-", "").toInt()
                        it.list?.forEach {
                            val code = it.code.removeSurrounding("\"", "\"").removePrefix("sz")
                                .removePrefix("sh")
                            val expound = it.article.action_info.expound
                            val time =
                                if (it.article.action_info.time?.contains(":") == true) it.article.action_info.time else "--:--:--"
                            val bean = Injector.appDatabase.ztReplayDao().getZTReplay(date, code)

                            val newBean = bean?.copy(
                                time = if (time == "--:--:--") bean.time else time,
                                groupName2 = groupName,
                                reason2 = reason,
                                expound2 = expound
                            ) ?: ZTReplayBean(
                                date,
                                code,
                                reason,
                                groupName,
                                expound,
                                time,
                                groupName2 = groupName,
                                reason2 = reason,
                                expound2 = expound
                            )


                            list.add(newBean)
                        }
                    }
                    Injector.appDatabase.ztReplayDao().insertAll(list)
                    launch(Dispatchers.Main){
                        Toast.makeText(this@SettingActivity,"解析完成并保存", Toast.LENGTH_LONG).show()
                    }
                }
            }

        }



        binding.bksOkBtn.setOnClickListener {
            val codes = binding.bksEt.editableText.toString()
            val name = binding.bkNameEt.editableText.toString()
            val code = sp.getInt("diy_bk_code", 10000) + 1

            lifecycleScope.launch(Dispatchers.IO) {
                Injector.appDatabase.diyBkDao().insert(DIYBk("BK$code", name, codes, ""))
                sp.edit() { putInt("diy_bk_code", code) }
            }


        }


    }
}