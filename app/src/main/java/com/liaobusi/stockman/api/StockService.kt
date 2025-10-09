package com.liaobusi.stockman.api

import com.google.gson.annotations.SerializedName
import com.liaobusi.stockman.db.AllTabListResponse
import com.liaobusi.stockman.db.FPResponse
import com.liaobusi.stockman.db.THSFPResponse
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


//const val FILED =
//    "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f26,f22,f11,f62,f128,f136,f115,f152,f297,f350,f351,f352,f383"

const val FILED =
    "f2,f3,f7,f8,f12,f14,f15,f16,f17,f18,f21,f26,f297,f350,f351,f352,f383"

const val FS="m:1+t:2,m:0+t:6,m:0+t:13,m:0+t:80,m:1+t:23,t:81+s:2048"

const val SZ_BZ_FS="m:0+t:6,m:0+t:13,m:0+t:80,m:0+t:81+s:2048"

interface StockService {

//    m:1+t:2,m:1+t:23 上证A股主板
//    m:1+t:23 科创板
//    m:0+t:80 创业板
//    m:0+t:6,m:0+t:13,m:0+t:80 深圳A股 主板 中小板 创业板
//    m:0+t:81+s:2048 北证A股

//    m:0 t:7,m:1 t:3 B股
//    b:MK0021,b:MK0022,b:MK0023,b:MK0024 ETF

    @GET("http://20.push2.eastmoney.com/api/qt/clist/get?pz=200&np=1&fid=f3&fields=${FILED}")
    suspend fun getRealTimeStocks(@Query("pn") pn: Int,@Query("fs") fs: String=FS): EMResponse







//    @Streaming
//    @GET("http://72.push2.eastmoney.com/api/qt/stock/details/sse?fields1=${FILED}&fields2=f51,f52,f53,f54,f55&mpi=1000&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&pos=-11&wbp2u=8678395952121844|0|1|0|web")
//    suspend fun getRealTimeStockData(@Query("secid") secid:String ):ResponseBody

    @GET("https://finance.pae.baidu.com/selfselect/getmarketrank")
    suspend fun getRealTimeStocks2(
        @Query("pn") pn: Int,
        @Query("rn") rn: Int = 200,
        @Query("group") group: String = "ranklist",
        @Query("type") type: String = "ab",
        @Query("finClientType") finClientType: String = "pc"
    ): BDStockResponse


    @GET("https://yunhq.sse.com.cn:32042/v1/sh1/list/exchange/equity?select=code%2Cname%2Copen%2Chigh%2Clow%2Clast%2Cprev_close%2Cchg_rate%2Cvolume%2Camount%2Ctradephase%2Cchange%2Camp_rate%2Ccpxxsubtype%2Ccpxxprodusta%2C&order=&begin=0&end=2500&_=1746766742886")
    suspend fun getRealTimeStocks3(): StockResponse


    @Streaming
    @GET("https://53.push2.eastmoney.com/api/qt/stock/trends2/sse?fields1=${FILED}&fields2=f51,f52,f53,f54,f55,f56,f57,f58&mpi=1000&ut=fa5fd1943c7b386f172d6893dbfba10b&ndays=1&iscr=0&iscca=0&wbp2u=8678395952121844|0|1|0|web")
    suspend fun getRealTimeStockData(@Query("secid") secid: String): ResponseBody


    //90.Bk0974
    @GET("https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=1.000001&klt=101&fqt=1&lmt=66&end=20500000&iscca=1&fields1=f1,f2,f3,f4,f5,f6,f7,f8&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64&forcect=1")
    suspend fun getSHHistory(): DPDayKLineResponse


    @GET
    suspend fun getBKHistory(@Url url: String): DPDayKLineResponse

    @GET
    suspend fun getStockListForBK(@Url url: String): EMResponse

    @GET("http://q.stock.sohu.com/hisHq")
    suspend fun getStockHistory(
        @Query("code") code: String,
        @Query("start") start: String,
        @Query("end") end: String
    ): List<HistoryBean>

    //行业m:90+t:2+f:!50  概念 m:90+t:3+f:!50,
    @GET("https://43.push2.eastmoney.com/api/qt/clist/get?pn=1&pz=10000&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&wbp2u=|0|0|0|web&fid=f3&fs=m:90+t:2+f:!50,m:90+t:3+f:!50&fields=f1,f2,f3,f4,f5,f6,f7,f8,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f24,f25,f22,f33,f11,f62,f152,f124,f107,f104,f105,f297")
    suspend fun getRealTimeBK(): EMResponse

    @GET("https://43.push2.eastmoney.com/api/qt/clist/get?pn=1&pz=10000&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&wbp2u=|0|0|0|web&fid=f3&fs=m:90+t:2+f:!50&fields=f2,f3,f7,f8,f12,f14,f15,f16,f17,f18,f20,f21,f297")
    suspend fun getRealTimeTradeBK(): EMResponse


    @GET("https://43.push2.eastmoney.com/api/qt/clist/get?pz=200&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&wbp2u=|0|0|0|web&fid=f3&fs=m:90+t:3+f:!50&fields=f2,f3,f7,f8,f12,f14,f15,f16,f17,f18,f20,f21,f297")
    suspend fun getRealTimeConceptBK(@Query("pn") pn: Int = 1): EMResponse

    //https://push2.eastmoney.com/api/qt/clist/get?np=1&fltt=1&invt=2&fs=b:BK0482&fields=f14,f12,f13,f1,f2,f4,f3,f152,f128,f140,f141,f62,f184,f66,f69,f72,f75,f78,f81,f84,f87,f109,f160,f164,f165,f166,f167,f168,f169,f170,f171,f172,f173,f174,f175,f176,f177,f178,f179,f180,f181,f182,f183&fid=f62&pn=1&pz=8&po=1&ut=fa5fd1943c7b386f172d6893dbfba10b&wbp2u=|0|0|0|web&_=1670382926497


    @GET("https://52.push2.eastmoney.com/api/qt/clist/get?pn=1&pz=10000&po=1&np=1&fields=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f22,f11,f62,f128,f136,f115,f152,f45")
    suspend fun getBKStocks(@Query("fs") fs: String): EMResponse


    @GET("https://datacenter.eastmoney.com/securities/api/data/v1/get?reportName=RPT_F10_EH_HOLDERNUM&columns=A_MARK%2CB_MARK%2CH_MARK%2CSECUCODE%2CSECURITY_CODE%2CORG_CODE%2CEND_DATE%2CHOLDER_TOTAL_NUM%2CTOTAL_NUM_RATIO%2CHOLDER_A_NUM%2CHOLDER_ANUM_RATIO%2CAVG_FREE_SHARES%2CAVG_FREESHARES_RATIO%2CPRICE%2CAVG_HOLD_AMT%2CHOLD_FOCUS%2CHOLD_RATIO_TOTAL%2CFREEHOLD_RATIO_TOTAL%2CHOLDER_B_NUM%2CHOLDER_H_NUM%2CHOLDER_BNUM_RATIO%2CHOLDER_HNUM_RATIO&client=APP&source=SECURITIES&pageNumber=1&pageSize=200&sr=-1&st=END_DATE&v=05145907342118392")
    suspend fun getGDRS(@Query("filter") filter: String): GDRSResponse


    @GET()
    suspend fun getZTReplay2(@Url url: String): ResponseBody

    @POST("https://app.jiuyangongshe.com/jystock-app/api/v1/action/field")
    @Headers(
        "Cookie:SESSION=YzE2MjVjYzAtYjI3Ni00MzdjLTk0ZDctZDZlM2MxMTI5NjMw; Hm_lvt_58aa18061df7855800f2a1b32d6da7f4=1744000777; Hm_lpvt_58aa18061df7855800f2a1b32d6da7f4=1744000861",
        "token:7cdef6a73f168b5b7f3443bedc03bab7"
    )
    suspend fun getZTReplay(@Body data: FPRequest): FPResponse

    @GET("https://ozone.10jqka.com.cn/open/api/draw_lots/v1/rank/all_tab_data")
    suspend fun getAllTabList(@Query("date") date: Int): AllTabListResponse


    @POST("https://dataq.10jqka.com.cn/fetch-data-server/fetch/v1/specific_data")
    suspend fun getSpecificData(@Body data: SpecificDataBean): SpecificDataResponse


    @GET("https://data.10jqka.com.cn/dataapi/limit_up/block_top?filter=HS%2CGEM2STAR")
    suspend fun getZTReplay2(@Query("date") date: Int): THSFPResponse


    @GET("https://data.eastmoney.com/dataapi/xuangu/list?st=POPULARITY_RANK&sr=1&ps=300&p=1&sty=SECUCODE%2CSECURITY_CODE%2CSECURITY_NAME_ABBR%2CPOPULARITY_RANK&filter=(POPULARITY_RANK%3E0)(POPULARITY_RANK%3C%3D1000)&source=SELECT_SECURITIES&client=WEB")
    suspend fun getPopularityRanking(): PopularityRankingResponse

    @GET("https://dq.10jqka.com.cn/fuyao/hot_list_data/out/hot_list/v1/stock?stock_type=a&type=hour&list_type=normal")
    suspend fun getTHSHotRanking(): THSHotRankingResponse


    @GET("https://imsearch.dzh.com.cn/stock/top?size=300&type=0&time=h")
    suspend fun getDZHHotRanking(): DZHRankResponse

    @GET("https://api3.cls.cn/v1/hot_stock?app=cailianpress&os=android&sv=850&sign=e4d8f886f269874b1578fec645b258fa")
    suspend fun getCLSHotRanking(): CLSRankResponse

    @GET
    suspend fun getDragonTigerRank(@Url url: String): DragonTigerRankResponse

    @GET("https://www.taoguba.com.cn/new/nrnt/getNoticeStock?type=D")
    suspend fun getTGBRanking(): TGBResponse

    @GET
    suspend fun getDragonTigerRank2(@Url url: String): DraganTigerDataResponse

    @POST("https://emcfgdata.eastmoney.com/api/themeInvest/getExpectHot")
    suspend fun getExpectHot(@Body param: ExpectHotParam): ExpectHotResponse


}

