package com.liaobusi.stockman
import com.liaobusi.stockman5.R

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.liaobusi.stockman5.databinding.ActivityWebviewBinding
import com.liaobusi.stockman.db.FPResponse
import com.liaobusi.stockman.db.ZTReplayBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.text.removeSurrounding

/**
 * 内置 WebView，通过注入脚本拦截页面内 **XMLHttpRequest** 与 **fetch** 返回的 JSON 文本，
 * 在 [onJsonIntercepted] 中处理（默认打日志）。
 *
 * 启动：[start] 传入初始 URL；仅用于你信任的页面，避免向不可信站点暴露 JS Bridge。
 */
open class WebViewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebViewActivity"
        const val EXTRA_URL = "url"
        const val EXTRA_DESKTOP_UA = "desktop_ua"

        fun start(context: Context, url: String, desktopUa: Boolean = false) {
            context.startActivity(
                Intent(context, WebViewActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_DESKTOP_UA, desktopUa),
            )
        }

        fun startDesktop(context: Context, url: String) = start(context, url, desktopUa = true)

        /**
         * 仅安装一次 hook；JSON 经 Base64 传给 Native，避免转义问题。
         * 依赖 `JsonBridge.onJsonBase64`。
         */
        private val JSON_HOOK_SCRIPT = """
            (function(){
              if(window.__SM_JSON_HOOK__)return;
              window.__SM_JSON_HOOK__=true;
              var B=window.JsonBridge;
              if(!B||!B.onJsonBase64)return;
              function absUrl(u){
                try{return new URL(u,document.baseURI).href;}catch(e){return u||'';}
              }
              function looksJson(t){
                if(!t||typeof t!=='string')return false;
                var s=t.trim();
                if(s.length<2)return false;
                var c0=s.charAt(0),c1=s.charAt(s.length-1);
                if(!((c0==='{'&&c1==='}')||(c0==='['&&c1===']')))return false;
                try{JSON.parse(s);return true;}catch(e){return false;}
              }
              function notify(url,text){
                if(!looksJson(text))return;
                try{
                  var b=btoa(unescape(encodeURIComponent(text)));
                  B.onJsonBase64(url,b);
                }catch(e){}
              }
              var XPO=XMLHttpRequest.prototype;
              var oOpen=XPO.open;
              var oSend=XPO.send;
              XPO.open=function(method,url){
                this.__sm_url=absUrl(url);
                return oOpen.apply(this,arguments);
              };
              XPO.send=function(body){
                this.addEventListener('load',function(){
                  var u=this.__sm_url||'';
                  var ct=(this.getResponseHeader('Content-Type')||'').toLowerCase();
                  if(ct.indexOf('json')>=0||ct.indexOf('javascript')>=0){
                    notify(u,this.responseText);
                  }else if(this.responseText){
                    notify(u,this.responseText);
                  }
                });
                return oSend.apply(this,arguments);
              };
              if(window.fetch){
                var nf=window.fetch;
                window.fetch=function(input,init){
                  var raw=typeof input==='string'?input:(input&&input.url);
                  var u=absUrl(raw||'');
                  return nf.apply(this,arguments).then(function(resp){
                    try{
                      var ct=(resp.headers.get('content-type')||'').toLowerCase();
                      if(ct.indexOf('json')>=0){
                        return resp.clone().text().then(function(txt){
                          notify(u,txt);
                          return resp;
                        });
                      }
                    }catch(e){}
                    return resp;
                  });
                };
              }
            })();
        """.trimIndent()
    }

    private lateinit var binding: ActivityWebviewBinding

    private val jsonBridge = JsonBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.webview_activity_title)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.customWebView.canGoBack()) {
                        binding.customWebView.goBack()
                    } else {
                        finish()
                    }
                }
            },
        )

        binding.customWebView.apply {
            val desktopUa = intent.getBooleanExtra(EXTRA_DESKTOP_UA, false)
            if (desktopUa) {
                // Some sites serve "mobile app download" page to WebView/mobile UA.
                // Force desktop UA to request the web version.
                settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView,
                    url: String?,
                    favicon: android.graphics.Bitmap?,
                ) {
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    injectJsonInterceptScript(view)
                    // 基础连通性探针：确保 evaluateJavascript 可执行
                    view.evaluateJavascript("(function(){return 'js_ok:' + String(!!window.JsonBridge);})()", null)
                }
            }
            addJavascriptInterface(jsonBridge, "JsonBridge")
        }

        val url = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        if (url.isNotEmpty()) {
            binding.customWebView.loadUrl(url)
        } else {
            binding.customWebView.loadUrl("about:blank")
        }
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.customWebView.apply {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("JsonBridge")
                destroy()
            }
        }
        super.onDestroy()
    }

    /**
     * 当页面脚本识别到合法 JSON 字符串（`{…}` / `[…]` 且可被 `JSON.parse`）时回调；运行在 UI 线程。
     */
    protected open fun onJsonIntercepted(url: String, json: String) {
        val shouldLog = url.startsWith("https://app.jiuyangongshe.com/jystock-app/api/v1/action/field") ||
            url.startsWith("https://x-quote.cls.cn/quote/index/up_down_analysis")
        if (shouldLog) {
            Log.d(TAG, "JSON url=$url len=${json.length} preview=${json.take(200)}")
        }

        if (url == "https://app.jiuyangongshe.com/jystock-app/api/v1/action/field") {
            lifecycleScope.launch(Dispatchers.IO) {
                val rsp = Gson().fromJson(json, FPResponse::class.java)
                val list = mutableListOf<ZTReplayBean>()
                rsp.data.forEach {
                    val groupName = it.name
                    val reason = it.reason ?: ""
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
                launch(Dispatchers.Main) {
                    Toast.makeText(this@WebViewActivity, "解析完成并保存", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 财联社：涨停池 up_pool（从页面 XHR/fetch 拦截 JSON）
        if (url.startsWith("https://x-quote.cls.cn/quote/index/up_down_analysis")) {
            val type = runCatching { Uri.parse(url).getQueryParameter("type") }.getOrNull()
            if (type == "up_pool") {
                lifecycleScope.launch(Dispatchers.IO) {
                    val inserted = runCatching {
                        val rsp = Gson().fromJson(json, ClsUpPoolResponse::class.java)
                        val items = rsp.data.orEmpty()
                        if (items.isEmpty()) return@runCatching 0

                        val list = mutableListOf<ZTReplayBean>()
                        items.forEach { it ->
                            val code = it.secuCode.orEmpty()
                                .removePrefix("sz")
                                .removePrefix("sh")
                                .removePrefix("bj")
                                .takeLast(6)
                            if (code.length != 6) return@forEach

                            val date = parseClsDate(it.time) ?: today()
                            val limitUpTime = parseClsTime(it.time) ?: "--:--:--"
                            val expound3 = buildString {
                                append(it.secuName.orEmpty())
                                if ((it.limitUpDays ?: 0) > 0) append(" 连板:${it.limitUpDays}")
                                if (!it.upReason.isNullOrBlank()) append(" 原因:${it.upReason}")
                            }.trim().take(2000)

                            val old = Injector.appDatabase.ztReplayDao().getZTReplay(date, code)
                            val bean = old?.copy(
                                expound3 = expound3,
                                time = if (old.time == "--:--:--") limitUpTime else old.time,
                            )
                                ?: ZTReplayBean(
                                    date = date,
                                    code = code,
                                    reason = "",
                                    groupName = "",
                                    expound = "",
                                    time = limitUpTime,
                                    expound3 = expound3,
                                )
                            list.add(bean)
                        }
                        if (list.isNotEmpty()) {
                            Injector.appDatabase.ztReplayDao().insertAll(list)
                        }
                        list.size
                    }.getOrElse { e ->
                        Log.e(TAG, "CLS up_pool parse failed", e)
                        0
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@WebViewActivity, "财联社 up_pool 入库：$inserted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    }

    private data class ClsUpPoolResponse(
        val code: Int? = null,
        val msg: String? = null,
        val data: List<ClsUpPoolItem>? = null,
    )

    private data class ClsUpPoolItem(
        val secu_code: String? = null,
        val secu_name: String? = null,
        val up_reason: String? = null,
        val time: String? = null,
        val limit_up_days: Int? = null,
    ) {
        val secuCode: String? get() = secu_code
        val secuName: String? get() = secu_name
        val upReason: String? get() = up_reason
        val limitUpDays: Int? get() = limit_up_days
    }

    private fun parseClsDate(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        // "2026-04-30 10:27:27"
        val datePart = time.trim().take(10)
        val s = datePart.replace("-", "")
        return s.toIntOrNull()
            ?: runCatching {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val d = sdf.parse(time) ?: return@runCatching null
                SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(d).toInt()
            }.getOrNull()
    }

    private fun parseClsTime(time: String?): String? {
        if (time.isNullOrBlank()) return null
        // "2026-04-30 10:27:27" -> "10:27:27"
        val t = time.trim()
        val idx = t.indexOf(' ')
        if (idx >= 0 && idx + 1 < t.length) {
            val s = t.substring(idx + 1).trim()
            if (s.length >= 8 && s[2] == ':' && s[5] == ':') return s.take(8)
        }
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val d = sdf.parse(time) ?: return@runCatching null
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(d)
        }.getOrNull()
    }

    private fun injectJsonInterceptScript(wv: WebView) {
        wv.evaluateJavascript(JSON_HOOK_SCRIPT, null)
    }

    @SuppressLint("JavascriptInterface")
    private inner class JsonBridge {
        @JavascriptInterface
        fun onJsonBase64(url: String, b64: String) {
            if (b64.isEmpty()) return
            val json = runCatching {
                String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull() ?: return
            runOnUiThread { onJsonIntercepted(url, json) }
        }
    }
}
