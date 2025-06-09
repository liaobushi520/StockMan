//package com.liaobusi.stockman
//
//import android.annotation.SuppressLint
//import android.os.Bundle
//import android.util.Log
//import android.view.LayoutInflater
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import com.liaobusi.stockman.Injector.context
//import com.liaobusi.stockman.databinding.ActivityGeckoBinding
//import org.json.JSONObject
//import org.mozilla.geckoview.GeckoResult
//import org.mozilla.geckoview.GeckoRuntime
//import org.mozilla.geckoview.GeckoSession
//import org.mozilla.geckoview.WebExtension
//
//class GeckoActivity : AppCompatActivity() {
//
//
//   private val TAG="Gecko"
//
//    private lateinit var binding: ActivityGeckoBinding
//    private lateinit var runtime: GeckoRuntime
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        binding= ActivityGeckoBinding.inflate(LayoutInflater.from(this))
//        setContentView(binding.root)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        val session= GeckoSession()
//
//        runtime=GeckoRuntime.create(this).apply {
//            settings.consoleOutputEnabled=true
//        }
//        session.open(runtime)
//        binding.geckoView.setSession(session)
//
//        setupExtension()
//
//        val url = intent.getStringExtra("url") ?: "https://www.baidu.com"
//
//        session.loadUri(url)
//    }
//
//
//
//    @SuppressLint("WrongThread")
//    private fun setupExtension() {
//
//         runtime.webExtensionController
//            .installBuiltIn("resource://android/assets/web-extensions/network_interceptor/")
//            .accept(
//                { ext ->
//                    Log.e(TAG,"install${Thread.currentThread()}")
//                    ext?.setMessageDelegate(object : WebExtension.MessageDelegate {
//                        override fun onMessage(
//                            nativeApp: String,
//                            message: Any,
//                            sender: WebExtension.MessageSender
//                        ): GeckoResult<Any>? {
//                            Log.e("XXXXXX","XXXXX")
//                            if (message is JSONObject) {
//                                handleResponse(message)
//                            }
//                            return null
//                        }
//                    }, "response_logger")
//                },
//                { ex -> Log.e(TAG, "Extension install failed", ex) }
//            )
//    }
//
//    private fun handleResponse(data: JSONObject) {
//        val url = data.getString("url")
//        val status = data.getInt("status")
//        val body = data.getString("body")
//
//
//    }
//
//
//}