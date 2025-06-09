package com.liaobusi.stockman

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.liaobusi.stockman.api.FPRequest
import com.liaobusi.stockman.databinding.ActivityWebviewBinding
import com.liaobusi.stockman.db.FPResponse
import com.liaobusi.stockman.db.ZTReplayBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import androidx.core.net.toUri


class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewBinding

    override fun onSupportNavigateUp(): Boolean {
        this.finish()
        return true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val url = intent.getStringExtra("url") ?: "https://www.example.com"

        binding.customWebView.apply {
            setProgressListener { progress ->
                // 更新进度条
                binding.progressBar.progress = progress
                if (progress == 100) {
                    binding.progressBar.visibility = View.GONE
                } else {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }

            setErrorListener { error ->
                // 显示错误信息
                Toast.makeText(context, "加载错误: $error", Toast.LENGTH_SHORT).show()
            }

            setPageLoadedListener {
                // 页面加载完成
                Toast.makeText(context, "页面加载完成", Toast.LENGTH_SHORT).show()
            }

            safeLoadUrl(url)
        }
    }

    override fun onBackPressed() {
        if (binding.customWebView.canGoBack()) {
            binding.customWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.customWebView.cleanUp()
        super.onDestroy()
    }
}


class CustomWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private var progressListener: ((Int) -> Unit)? = null
    private var errorListener: ((String?) -> Unit)? = null
    private var pageLoadedListener: (() -> Unit)? = null

    init {
        setupWebView()
    }



    private fun interceptResponse(request: WebResourceRequest) {


        Injector.scope.launch(Dispatchers.IO) {
            try {


                val pageUrl = async(Dispatchers.Main) {
                    this@CustomWebView.url
                }.await()

                val date = pageUrl?.toUri()?.pathSegments?.lastOrNull()
                if (date == null || date.contains("-")==false) {
                    return@launch
                }


                val url = "https://app.jiuyangongshe.com/jystock-app/api/v1/action/field"//request.url.toString()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = request.method
                request.requestHeaders.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }
                val cookie = CookieManager.getInstance().getCookie("https://www.jiuyangongshe.com")
                connection.setRequestProperty("Cookie", "SESSION=YzE2MjVjYzAtYjI3Ni00MzdjLTk0ZDctZDZlM2MxMTI5NjMw; Hm_lvt_58aa18061df7855800f2a1b32d6da7f4=1744000777; Hm_lpvt_58aa18061df7855800f2a1b32d6da7f4=1744196078")
                connection.setRequestProperty("Sec-Fetch-Site","same-site")
                connection.setRequestProperty("sec-Fetch-mode","cors")
                connection.setRequestProperty("sec-Fetch-dest","empty")
                connection.setRequestProperty("priority","u=1, i")
                connection.setRequestProperty("accept-encoding","gzip, deflate, br, zstd")
                connection.setRequestProperty("accept-language","zh-CN,zh;q=0.9,en;q=0.8")

                connection.setRequestProperty("timestamp","1744196090522")
                connection.setRequestProperty("token","162a548372a030495d4bd677b1d675f5")

                connection.requestProperties.forEach {
                    Log.e("XXXXXX",it.key+" "+it.value)
                }

                connection.setDoOutput(true);
                connection.connectTimeout = 5000
                connection.readTimeout = 8000
                Log.e("股票超人","从韭研获取复盘信息${date}")
                val jsonInputString = Gson().toJson(FPRequest(date))
                connection.outputStream.use { os ->
                    val input =
                        jsonInputString.toByteArray(StandardCharsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                val inputStream = connection.inputStream
                val content = inputStream.bufferedReader().use { it.readText() }
                val rsp = Gson().fromJson<FPResponse>(content, FPResponse::class.java)
                val list = mutableListOf<ZTReplayBean>()
                Log.e("XXXXXXX",content)

                rsp.data.forEach {
                    Log.e("XXXXXX", "!!!${it}")
                }
                rsp.data.forEach {

                    if (it.date==null)
                        return@forEach

                    val groupName = it.name
                    val reason = it.reason
                    val date = it.date.replace("-", "").toInt()
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
            } catch (e: Throwable) {
                e.printStackTrace()
            }

        }


    }

    var count = 0
    private fun setupWebView() {
        // 基础设置
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // WebViewClient 处理页面加载
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageLoadedListener?.invoke()
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {

                if (request?.url.toString().contains("jystock-app/api/v1/action/field")) {

                    if (count % 2 == 1) {
                        interceptResponse(request!!)
                    }

                    count++


                }


                return super.shouldInterceptRequest(view, request)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)


            }


            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                errorListener?.invoke(error?.description?.toString())
            }
        }

        // WebChromeClient 处理进度等
        webChromeClient = object : WebChromeClient() {


            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressListener?.invoke(newProgress)
            }
        }
    }

    // 设置进度监听
    fun setProgressListener(listener: (Int) -> Unit) {
        this.progressListener = listener
    }

    // 设置错误监听
    fun setErrorListener(listener: (String?) -> Unit) {
        this.errorListener = listener
    }

    // 设置页面加载完成监听
    fun setPageLoadedListener(listener: () -> Unit) {
        this.pageLoadedListener = listener
    }

    // 安全加载URL
    fun safeLoadUrl(url: String) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            loadUrl(url)
        } else {
            errorListener?.invoke("不支持的URL协议")
        }
    }

    // 清理WebView资源
    fun cleanUp() {
        progressListener = null
        errorListener = null
        pageLoadedListener = null
        webChromeClient = null
    }
}