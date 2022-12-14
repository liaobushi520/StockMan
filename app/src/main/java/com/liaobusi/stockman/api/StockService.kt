package com.liaobusi.stockman.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


interface StockService {

//    m:1 t:2 上证A股主板
//    m:0 t:6,m:0 t:13,m:0 t:80 深圳A股 主板 中小板 创业板
//    s: m:0 t:81 s:2048 北证A股
//    m:1 t:23 科创板
//    m:0 t:7,m:1 t:3 B股
//    b:MK0021,b:MK0022,b:MK0023,b:MK0024 ETF

    @GET("http://20.push2.eastmoney.com/api/qt/clist/get?pn=1&pz=10000&np=1&fid=f3&fields=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f26,f22,f11,f62,f128,f136,f115,f152,f297,f350,f351,f352,f383&fs=m:1+t:2,m:0+t:6,m:0+t:13,m:0+t:80,m:1+t:23")
    suspend fun getRealTimeStocks():EMResponse

    //90.Bk0974
    @GET("https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=1.000001&klt=101&fqt=1&lmt=66&end=20500000&iscca=1&fields1=f1,f2,f3,f4,f5,f6,f7,f8&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64&forcect=1")
    suspend fun getSHHistory():DPDayKLineResponse


    @GET
    suspend fun getBKHistory(@Url url:String):DPDayKLineResponse

    @GET
    suspend fun getStockListForBK(@Url url:String):EMResponse

    @GET("http://q.stock.sohu.com/hisHq")
    suspend fun getStockHistory(@Query("code") code:String,@Query("start") start:String,@Query("end") end:String):List<HistoryBean>

    //行业m:90+t:2+f:!50  概念 m:90+t:3+f:!50,
    @GET("https://43.push2.eastmoney.com/api/qt/clist/get?pn=1&pz=10000&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&wbp2u=|0|0|0|web&fid=f3&fs=m:90+t:2+f:!50,m:90+t:3+f:!50&fields=f1,f2,f3,f4,f5,f6,f7,f8,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f24,f25,f22,f33,f11,f62,f152,f124,f107,f104,f105,f297")
    suspend fun getRealTimeBK():EMResponse

    @GET("https://43.push2.eastmoney.com/api/qt/clist/get?pn=1&pz=10000&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&wbp2u=|0|0|0|web&fid=f3&fs=m:90+t:2+f:!50&fields=f1,f2,f3,f4,f5,f6,f7,f8,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f24,f25,f22,f33,f11,f62,f152,f124,f107,f104,f105,f106,f297")
    suspend fun getRealTimeTradeBK():EMResponse


    @GET("https://43.push2.eastmoney.com/api/qt/clist/get?pn=1&pz=10000&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&wbp2u=|0|0|0|web&fid=f3&fs=m:90+t:3+f:!50&fields=f1,f2,f3,f4,f5,f6,f7,f8,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f24,f25,f22,f33,f11,f62,f152,f124,f107,f104,f105,f106,f297")
    suspend fun getRealTimeConceptBK():EMResponse

    //https://push2.eastmoney.com/api/qt/clist/get?np=1&fltt=1&invt=2&fs=b:BK0482&fields=f14,f12,f13,f1,f2,f4,f3,f152,f128,f140,f141,f62,f184,f66,f69,f72,f75,f78,f81,f84,f87,f109,f160,f164,f165,f166,f167,f168,f169,f170,f171,f172,f173,f174,f175,f176,f177,f178,f179,f180,f181,f182,f183&fid=f62&pn=1&pz=8&po=1&ut=fa5fd1943c7b386f172d6893dbfba10b&wbp2u=|0|0|0|web&_=1670382926497





    @GET("https://52.push2.eastmoney.com/api/qt/clist/get?pn=1&pz=10000&po=1&np=1&fields=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f22,f11,f62,f128,f136,f115,f152,f45")
    suspend fun getBKStocks(@Query("fs") fs:String):EMResponse

}

