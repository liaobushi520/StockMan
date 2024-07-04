package com.liaobusi.stockman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.api.FPRequest
import com.liaobusi.stockman.api.StockService
import com.liaobusi.stockman.databinding.ActivityHomeBinding
import com.liaobusi.stockman.db.marketCode
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.db.specialBK
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.*

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
            val uri: Uri = Uri.parse(s)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
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

        binding.initBtn.multiClick(3) {
            binding.ll.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                StockRepo.getRealTimeStocks()
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

            val d = Injector.appDatabase.stockDao()
            val h = Injector.appDatabase.historyStockDao()
            val bkStockDao = Injector.appDatabase.bkStockDao()
            val bkDao = Injector.appDatabase.bkDao()


            // StockRepo.getRealTimeStockData("0.300059")
            StockRepo.getRealTimeStocks()
            StockRepo.getRealTimeBKs()

            val sp = getSharedPreferences("app", Context.MODE_PRIVATE)
            if (System.currentTimeMillis() - sp.getLong(
                    "fetch_bk_stocks_time",
                    0
                ) > 1 * 12 * 60 * 60 * 1000
            ) {
                StockRepo.getBKStocks()
                sp.edit().putLong("fetch_bk_stocks_time", System.currentTimeMillis()).apply()
            }

            if (System.currentTimeMillis() - sp.getLong(
                    "fetch_gdrs_time",
                    0
                ) > 5 * 24 * 60 * 60 * 1000
            ) {
                StockRepo.getHistoryGDRS()
                sp.edit().putLong("fetch_gdrs_time", System.currentTimeMillis()).apply()
            }


//            bkStockDao.getStocksByBKCode("BK0438").forEach {
//                Log.e("XXX", it.toString())
//            }


//            val p=Injector.appDatabase.bkDao().getBKByCode("BK1036")
//

//            StockRepo.getBKStocks()
//            bkStockDao.getAll().forEach {
//                Log.e("XXXY",it.toString())
//            }
//            bkStockDao.getStocksByBKCode("BK0420").forEach {
//                Log.e("XXX",it.toString())
//            }


//            h.getHistoryBefore2("600975",20220812,1000).forEach {
//                Log.e("DDD",it.toString())
//            }

//            d.getAllStock().forEach {
//                if(it.marketCode()==1){
//                    StockRepo.fixData(it.code,20221102)
//                }
//
//            }


//            StockRepo.fixData("002279",20221102)

//            h.getHistoryAfter("601136",today)
//              .forEach {
//                Log.e("XX",it.toString())
//            }
//            h.getHistoryByDate(20221102,20221102) .forEach {
//                Log.e("XX",it.toString())
//            }


//            val bkDao=Injector.appDatabase.bkDao()
//            val historyBKDao=Injector.appDatabase.historyBKDao()
//             historyBKDao.getHistoryByDate("20221102").forEach {
//                Log.e("yy",it.toString())
//            }


//            val l= mutableListOf<HistoryStock>()
//            h.getHistoryByDate(20220201,today).forEach {
//                Log.e("XX",it.toString())
//                if(it.closePrice<0.6f){
//                    Log.e("XX",it.toString())
//                }
//            }


//            d.getAllStockByMarketTime(20220201).forEach {
//                val ll = h.getHistoryByDate2(it.code, 20220201,today)
//                ll.forEach {
//                    if(it.closePrice<0.6f){
//                        Log.e("XX",it.toString())
//                    }
//                    l.add(it)
//                }
//            }
//
//            h.deleteHistory(l)


            //修复数据
            //   StockRepo.getHistoryStocks( 20230926,20230926)




        }


        Injector.startAutoRefresh()


    }
}


