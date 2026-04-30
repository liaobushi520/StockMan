package com.liaobusi.stockman.monitor.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieManager
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object MarketApiFactory {
    private val httpLogger = LoggerFactory.getLogger("OkHttp")

    fun eastMoneyRealtimeApi(): EastMoneyApi {
        return retrofit(
            baseUrl = "http://20.push2.eastmoney.com/",
            client = trustAllOkHttpClient()
        ).create(EastMoneyApi::class.java)
    }

    fun eastMoneyHistoryApi(): EastMoneyApi {
        return retrofit(
            baseUrl = "http://push2his.eastmoney.com/",
            client = defaultOkHttpClient()
        ).create(EastMoneyApi::class.java)
    }

    fun sinaApi(): SinaApi {
        return retrofit(
            baseUrl = "https://vip.stock.finance.sina.com.cn/",
            client = defaultOkHttpClient()
        ).create(SinaApi::class.java)
    }

    fun sseApi(): SseApi {
        return retrofit(
            baseUrl = "https://yunhq.sse.com.cn:32042/",
            client = trustAllOkHttpClient()
        ).create(SseApi::class.java)
    }

    fun sohuApi(): SohuApi {
        return retrofit(
            baseUrl = "https://q.stock.sohu.com/",
            client = defaultOkHttpClient()
        ).create(SohuApi::class.java)
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun defaultOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun trustAllOkHttpClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor())
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun loggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message -> httpLogger.info(message) }
            .setLevel(HttpLoggingInterceptor.Level.BODY)
    }
}
