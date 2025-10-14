package com.liaobusi.stockman

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liaobusi.stockman.databinding.ActivityHomeBinding
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import androidx.core.graphics.toColorInt
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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
            val intent = Intent(this, WebViewActivity::class.java).apply {
                putExtra("url", s)
            }
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

        binding.dp.setOnClickListener {
            val i = Intent(this, DPActivity::class.java)
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

    }


}


//
//  fun fetch(){
//
//    try {
//        // 1. 创建 URL 对象
//        val url = URL("https://app.jiuyangongshe.com/jystock-app/api/v1/action/field")
//        val connection = url.openConnection() as HttpURLConnection
//
//        // 2. 设置请求方法为 POST
//        connection.setRequestMethod("POST")
//        connection.setDoOutput(true)
//
//        // 3. 设置请求头
//        connection.setRequestProperty("accept", "application/json, text/plain, */*")
//        connection.setRequestProperty("accept-language", "zh-CN,zh;q=0.9,en;q=0.8")
//        connection.setRequestProperty("content-type", "application/json")
//        connection.setRequestProperty("origin", "https://www.jiuyangongshe.com")
//        connection.setRequestProperty("platform", "3")
//        connection.setRequestProperty("priority", "u=1, i")
//        connection.setRequestProperty("referer", "https://www.jiuyangongshe.com/")
//        connection.setRequestProperty("timestamp", "1744196090522")
//        connection.setRequestProperty("token", "162a548372a030495d4bd677b1d675f5")
//        connection.setRequestProperty(
//            "user-agent",
//            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
//        )
//
//
//        // 设置 Cookie（注意分号后的空格要去掉）
//        val cookies =
//            "SESSION=YzE2MjVjYzAtYjI3Ni00MzdjLTk0ZDctZDZlM2MxMTI5Nj30;Hm_lvt_58aa18061df7855800f2a1b32d6da7f4=1744000777;Hm_lpvt_58aa18061df7855800f2a1b32d6da7f4=1744196078"
//        connection.setRequestProperty("Cookie", cookies)
//
//        // 4. 写入 JSON 请求体
//        val jsonBody = "{\"date\":\"2025-04-08\",\"pc\":1}"
//        connection.getOutputStream().use { os ->
//            val input = jsonBody.toByteArray(StandardCharsets.UTF_8)
//            os.write(input, 0, input.size)
//        }
//        // 5. 获取响应状态码（可选）
//        val responseCode = connection.getResponseCode()
//        val inputStream = connection.inputStream
//        val content = inputStream.bufferedReader().use { it.readText() }
//        println("Response Code: " + responseCode+" "+content)
//
//        // 6. 关闭连接
//        connection.disconnect()
//    } catch (e: Exception) {
//        e.printStackTrace()
//    }
//
//
//
//}



