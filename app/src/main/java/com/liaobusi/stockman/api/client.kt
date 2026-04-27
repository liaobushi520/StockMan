package com.liaobusi.stockman.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

const val DEBUG = true

@get:Throws(Exception::class)
val sSLSocketFactory: SSLSocketFactory by lazy {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
        null,
        arrayOf<TrustManager>(x509TrustManager),
        SecureRandom()
    )
    // Create an ssl socket factory with our all-trusting manager
    sslContext.socketFactory
}

private val x509TrustManager = object : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String
    ) {
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String
    ) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate?> {
        return arrayOfNulls(0)
    }
}


fun getOkHttpClientBuilder() = OkHttpClient.Builder().apply {
    val logging =
        HttpLoggingInterceptor()
    if (DEBUG) {
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
    } else {
        logging.setLevel(HttpLoggingInterceptor.Level.NONE)
    }
    //设置请求超时时长
    connectTimeout(
        10,
        TimeUnit.SECONDS
    )
    readTimeout(
        10,
        TimeUnit.SECONDS
    )
    //设置SSL
    try {
        sslSocketFactory(
            sSLSocketFactory,
            x509TrustManager
        )
            .hostnameVerifier(org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    addNetworkInterceptor { chain ->

        val request = chain.request().newBuilder().also {
            if (chain.request().url.host == "www.cls.cn" ||
                "finance.pae.baidu.com" == chain.request().url.host ||
                chain.request().url.host.endsWith("finance.sina.com.cn")
            ) {
                it.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
                )
                it.header("Acs-Token","1776924005702_1777000636050_4o3VJYYsyA4qN8l+stYo0jjWvAK+X1y3U3cKUoqEyhmLSz2bV/9rQMOK1mI4zbgkxgSPLfoQuRxDsCHf+Qv2fccfTyDRMLlDjQ1SssttIgrTV3ZNJvjOJg7biD/Hc/bJMg1tLdj2nMw7mRAs7rEb/2hGTkUvJIXHNeTnAyf9VXu9vnllNMUgs6txbweqiNSnosdk3rwmp8mNdmt9L64heXK9jBACDuk5ubRve4xeiF2a9B6jeBK1WP9akUUtH9+77/OaRxDP7PnwFAN/NsjjSyv+m1FJ7q3IaoCvaj1eQeCl7fa+0jijw8MVZOq6P6jtqvw4NQeatvccR6czVizBlqAtp+Bug1apjvw6nhkzP91iVQtVxbNVkX6286yMDRuL")
            }
        }.build()
        chain.proceed(request)
    }
    //启用Log日志
    addInterceptor(logging)

}

