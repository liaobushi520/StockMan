package com.liaobusi.stockman.api

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


private var okHttpClientBuilder: OkHttpClient.Builder? = null
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

    //启用Log日志
   addInterceptor(logging)

}

