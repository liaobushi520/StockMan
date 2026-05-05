package com.liaobusi.stockman

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.databinding.ActivityHomeBinding
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

fun setStatusBarColor(activity: Activity, color: Int) {
    val window: Window = activity.window
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.setStatusBarColor(color)
}

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private inline fun <reified T : Activity> startActivityCls() {
        startActivity(Intent(this, T::class.java))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_follow_list -> {
                FollowListActivity.startFollowListActivity(this)
                return true
            }

            R.id.action_setting -> {
                SettingActivity.startSettingActivity(this)
                return true
            }

            R.id.action_will_zt -> {
                WillZTActivity.startWillZTActivity(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.easy_menu, menu)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.passwordBtn.setOnClickListener {
            binding.root.requestFocus()
            val pwd = binding.passwordEt.text?.toString().orEmpty()
            if (pwd.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                val match = withContext(Dispatchers.IO) {
                    val stock = Injector.appDatabase.stockDao().getStockByCode("000601")
                    pwd == stock?.yesterdayClosePrice?.toString()
                }
                if (!isFinishing && match) {
                    binding.passwordFL.visibility = View.GONE
                }
            }
        }

        binding.fp.setOnClickListener { startActivityCls<FPActivity>() }
        binding.analysisBtn.setOnClickListener { startActivityCls<AnalysisActivity>() }
        binding.s1.setOnClickListener { startActivityCls<Strategy1Activity>() }
        binding.s2.setOnClickListener { startActivityCls<Strategy2Activity>() }
        binding.s3.setOnClickListener { startActivityCls<Strategy3Activity>() }
        binding.s4.setOnClickListener { startActivityCls<Strategy4Activity>() }
        binding.fpBtn.setOnClickListener {
            WebViewActivity.start(this, "https://www.jiuyangongshe.com/action")
        }
        binding.clsBtn.setOnClickListener {
            val url = "https://www.cls.cn/finance"
            WebViewActivity.startDesktop(this, url)
        }
        binding.s5.setOnClickListener { startActivityCls<Strategy5Activity>() }
        binding.s6.setOnClickListener { startActivityCls<Strategy6Activity>() }
        binding.s7.setOnClickListener { startActivityCls<BKStrategyActivity>() }
        binding.s8.setOnClickListener { startActivityCls<Strategy7Activity>() }
        binding.s9.setOnClickListener { startActivityCls<Strategy9Activity>() }
        binding.dp.setOnClickListener { startActivityCls<DPActivity>() }

        binding.initBtn.multiClick(3) {
            binding.ll.visibility = View.VISIBLE
            lifecycleScope.launch {
                val success = runCatching {
                    withContext(Dispatchers.IO) {
                        StockRepo.getRealTimeStocksDFCF()
                        StockRepo.getRealTimeBKs()
                        StockRepo.getHistoryBks()
                        StockRepo.getBKStocks()
                        StockRepo.getHistoryGDRS()
                        StockRepo.getHistoryStocks(
                            Date(System.currentTimeMillis()).before(180),
                            today()
                        )
                    }
                }.getOrElse { e ->
                    Log.e("HomeActivity", "初始化数据拉取失败", e)
                    false
                }
                binding.ll.visibility = View.GONE
                Toast.makeText(
                    this@HomeActivity,
                    if (success) "初始化成功" else "拉取数据失败，请稍后重试",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
