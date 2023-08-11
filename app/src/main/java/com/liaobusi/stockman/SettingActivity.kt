package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import com.liaobusi.stockman.databinding.ActivityBkstrategyBinding
import com.liaobusi.stockman.databinding.ActivitySettingBinding


fun isShowHiddenStockAndBK(context: Context):Boolean{
    val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    val show=sp.getBoolean("show_hidden_stock_bk",false)
    return show
}

class SettingActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingBinding
    companion object{
        fun startSettingActivity(context: Context){

            val i= Intent(context,SettingActivity::class.java)
            context.startActivity(i)


        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        supportActionBar?.title = "板块强势"

        val sp = getSharedPreferences("app", Context.MODE_PRIVATE)
        val show=sp.getBoolean("show_hidden_stock_bk",false)
        binding.hideSwitch.isChecked=show

        binding.hideSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            sp.edit().putBoolean("show_hidden_stock_bk",isChecked).apply()
        }
    }
}