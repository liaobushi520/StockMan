package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.liaobusi.stockman.databinding.ActivitySettingBinding
import com.liaobusi.stockman.db.FPResponse
import com.liaobusi.stockman.db.ZTReplayBean
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


fun isShowHiddenStockAndBK(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("show_hidden_stock_bk", false)
}

fun isShowST(context: Context): Boolean {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getBoolean("show_ST", false)
}

fun howDayShowZTFlag(context: Context): Int {
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    return sp.getInt("how_day_show_zt_flag", 1)
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
        supportActionBar?.title = "设置"

        val sp = getSharedPreferences("app", Context.MODE_PRIVATE)

        val show = sp.getBoolean("show_hidden_stock_bk", false)
        binding.hideSwitch.isChecked = show
        binding.hideSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("show_hidden_stock_bk", isChecked).apply()
        }



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



        binding.autoRefresh.isChecked = sp.getBoolean("auto_refresh", false)
        binding.autoRefresh.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("auto_refresh", isChecked).apply()
            Injector.autoRefresh(isChecked)
        }


        binding.showCurrentChg.isChecked = sp.getBoolean("show_current_chg", false)
        binding.showCurrentChg.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("show_current_chg", isChecked).apply()
        }


        binding.showNextChg.isChecked = sp.getBoolean("show_next_chg", false)
        binding.showNextChg.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("show_next_chg", isChecked).apply()
        }




        binding.showLianbanCount.isChecked = sp.getBoolean("show_lianban_count_flag", true)
        binding.showLianbanCount.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("show_lianban_count_flag", isChecked).apply()
        }


        val day = sp.getInt("how_day_show_zt_flag", 1)
        binding.howDayShowZTFlagEt.setText(day.toString())
        binding.howDayShowZTFlagConfirmBtn.setOnClickListener {
            val d = binding.howDayShowZTFlagEt.editableText.toString().toIntOrNull()
            sp.edit().putInt("how_day_show_zt_flag", d ?: 1).apply()

        }


        binding.jiexiBtn.setOnClickListener {
            val s = binding.fpEt.editableText.toString()
            if (s.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val rsp = Gson().fromJson(s, FPResponse::class.java)
                    val list = mutableListOf<ZTReplayBean>()
                    rsp.data.forEach {
                        val name = it.name
                        val reason = it.reason
                        val date = it.date.replace("-", "").toInt()
                        it.list?.forEach {
                            if (it == null) return@forEach
                            val code = it.code.removeSurrounding("\"", "\"").removePrefix("sz")
                                .removePrefix("sh")
                            val expound = it.article.action_info.expound
                            list.add(
                                ZTReplayBean(
                                    date,
                                    code,
                                    reason,
                                    name,
                                    expound,
                                    if (it.article.action_info.time.contains(":")) it.article.action_info.time else "--:--:--"
                                )
                            )
                        }
                    }
                    Injector.appDatabase.ztReplayDao().insertAll(list)
                }
            }

        }
    }
}