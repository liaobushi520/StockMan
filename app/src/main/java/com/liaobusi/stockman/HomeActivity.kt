package com.liaobusi.stockman

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.api.CallWarnParam
import com.liaobusi.stockman.api.Filter
import com.liaobusi.stockman.databinding.ActivityHomeBinding
import com.liaobusi.stockman.db.UnusualActionHistory
import com.liaobusi.stockman.db.WARN_TYPE_MAP
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

fun setStatusBarColor(activity: Activity, color: Int) {
    val window: Window = activity.window
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.setStatusBarColor(color)
}

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding


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

        binding = ActivityHomeBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)


        binding.passwordBtn.setOnClickListener {
            binding.root.requestFocus()
            val p = binding.passwordEt.editableText.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                val d = Injector.appDatabase.stockDao()
                val stock = d.getStockByCode("000601")
                if (p == stock.yesterdayClosePrice.toString()) {
                    launch(Dispatchers.Main) {
                        binding.passwordFL.visibility = View.GONE
                    }
                }
            }
        }

        binding.fp.setOnClickListener {
            val i = Intent(this, FPActivity::class.java)
            startActivity(i)
        }


        binding.analysisBtn.setOnClickListener {
            val i = Intent(this, AnalysisActivity::class.java)
            startActivity(i)
        }

        binding.s1.setOnClickListener {
            val i = Intent(this, Strategy1Activity::class.java)
            startActivity(i)
        }
        binding.s2.setOnClickListener {
            val i = Intent(this, Strategy2Activity::class.java)
            startActivity(i)
        }

        binding.s3.setOnClickListener {
            val i = Intent(this, Strategy3Activity::class.java)
            startActivity(i)
        }

        binding.s4.setOnClickListener {
            val i = Intent(this, Strategy4Activity::class.java)
            startActivity(i)
        }

        binding.fpBtn.setOnClickListener {
            val s = "https://www.jiuyangongshe.com/action"
//            val uri: Uri = Uri.parse(s)
//            val intent = Intent(Intent.ACTION_VIEW, uri)

            WebViewActivity.start(this, s)

        }

        binding.s5.setOnClickListener {
            val i = Intent(this, Strategy5Activity::class.java)
            startActivity(i)
        }
        binding.s6.setOnClickListener {
            val i = Intent(this, Strategy6Activity::class.java)
            startActivity(i)
        }

        binding.s7.setOnClickListener {
            val i = Intent(this, BKStrategyActivity::class.java)
            startActivity(i)
        }

        binding.s8.setOnClickListener {
            val i = Intent(this, Strategy7Activity::class.java)
            startActivity(i)
        }

        binding.s9.setOnClickListener {
            val i = Intent(this, Strategy9Activity::class.java)
            startActivity(i)
        }

        binding.dp.setOnClickListener {
            val i = Intent(this, DPActivity::class.java)
            startActivity(i)
        }

        binding.initBtn.multiClick(3) {
            binding.ll.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                StockRepo.getRealTimeStocksDFCF()
                StockRepo.getRealTimeBKs()
                StockRepo.getHistoryBks()
                StockRepo.getBKStocks()
                StockRepo.getHistoryGDRS()
                val r = StockRepo.getHistoryStocks(
                    Date(System.currentTimeMillis()).before(180),
                    today()
                )
                launch(Dispatchers.Main) {
                    if (!r) {
                        Toast.makeText(
                            this@HomeActivity,
                            "拉取数据失败，请稍后重试",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@HomeActivity, "初始化成功", Toast.LENGTH_LONG).show()
                    }
                    binding.ll.visibility = View.GONE
                }

            }
        }

        lifecycleScope.launch(Dispatchers.IO) {


        }

    }
}






