package com.liaobusi.stockman.monitor

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.Proxy
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private val serverLogger = LoggerFactory.getLogger("StockManServer")
private val okHttpLogger = LoggerFactory.getLogger("OkHttp")
private val monitorSourceClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

fun main() {
    serverLogger.info(
        "Starting StockMan monitor server: cwd={}, dbPath={}, logDir={}",
        java.nio.file.Path.of("").toAbsolutePath().normalize(),
        System.getProperty("stockman.db.path") ?: System.getenv("STOCKMAN_DB_PATH") ?: "<default>",
        System.getProperty("stockman.log.dir") ?: System.getenv("STOCKMAN_LOG_DIR") ?: "<default>"
    )
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        monitorModule()
    }.start(wait = true)
}

fun Application.monitorModule() {
    val engine = MarketEngine()

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CORS) {
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHost("localhost:8081")
        allowHost("127.0.0.1:8081")
    }
    install(WebSockets) {
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respond(
                mapOf(
                    "name" to "StockMan Compose Monitor",
                    "status" to "running",
                    "webSocket" to "ws://localhost:8080/ws",
                    "database" to engine.database.path(),
                    "dbViewer" to "http://localhost:8080/db"
                )
            )
        }
        get("/db") {
            call.respondText(dbViewerHtml(), ContentType.Text.Html)
        }
        get("/api/snapshot") {
            call.respond(engine.snapshot())
        }
        get("/api/sync/status") {
            call.respond(engine.syncStatus())
        }
        get("/api/sync/history/status") {
            call.respond(engine.historySyncStatus())
        }
        get("/api/sync/history/progress") {
            call.respond(engine.historyStocksSyncProgress())
        }
        post("/api/sync/history/start") {
            val codes = call.request.queryParameters.getAll("code").orEmpty() +
                call.request.queryParameters["codes"].orEmpty().split(',').filter { it.isNotBlank() }
            val full = call.request.queryParameters["full"] == "true"
            serverLogger.info("HTTP POST /api/sync/history/start codes={} full={}", codes.size, full)
            call.respond(engine.startHistoryStocksSync(codes = codes.map { it.trim() }.distinct(), full = full))
        }
        post("/api/sync/history/stop") {
            serverLogger.info("HTTP POST /api/sync/history/stop")
            call.respond(engine.stopHistoryStocksSync())
        }
        get("/api/debug/eastmoney/history") {
            val code = call.request.queryParameters["code"]?.trim().orEmpty().ifBlank { "300059" }
            val direct = call.request.queryParameters["direct"] == "true"
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 210) ?: 120
            val end = call.request.queryParameters["end"]?.trim().orEmpty().ifBlank { "20500000" }
            serverLogger.info("HTTP GET /api/debug/eastmoney/history code={} direct={} limit={} end={}", code, direct, limit, end)
            call.respond(debugEastMoneyHistoryRequest(code, direct, limit, end))
        }
        get("/api/monitor/source/{id}") {
            val id = call.parameters["id"].orEmpty()
            runCatching {
                fetchMonitorSourcePayload(id)
            }.onSuccess {
                call.respondText(it, ContentType.Application.Json)
            }.onFailure {
                serverLogger.warn("HTTP GET /api/monitor/source/{} failed: {}", id, it.message)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "source request failed")))
            }
        }
        get("/api/replay/kpl-live") {
            runCatching {
                fetchKplLivePayload()
            }.onSuccess {
                call.respondText(it, ContentType.Application.Json)
            }.onFailure {
                serverLogger.warn("HTTP GET /api/replay/kpl-live failed: {}", it.message)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "kpl live request failed")))
            }
        }
        post("/api/sync/stocks") {
            val source = call.request.queryParameters["source"]?.trim().orEmpty().ifBlank { "sina" }
            serverLogger.info("HTTP POST /api/sync/stocks source={}", source)
            runCatching {
                engine.refreshRealtimeStocksNow(source)
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                serverLogger.warn("HTTP POST /api/sync/stocks failed: {}", it.message)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (it.message ?: "sync failed")))
            }
        }
        post("/api/sync/history") {
            val codes = call.request.queryParameters.getAll("code").orEmpty() +
                call.request.queryParameters["codes"].orEmpty().split(',').filter { it.isNotBlank() }
            serverLogger.info("HTTP POST /api/sync/history codes={}", codes.size)
            runCatching {
                engine.syncHistoryStocksNow(codes = codes.map { it.trim() }.distinct())
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                serverLogger.warn("HTTP POST /api/sync/history failed: {}", it.message)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (it.message ?: "history sync failed")))
            }
        }
        get("/api/db/tables") {
            call.respond(DbTables(database = engine.database.path(), tables = engine.database.tableNames()))
        }
        get("/api/db/table/{name}") {
            val name = call.parameters["name"].orEmpty()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            runCatching {
                engine.database.queryTable(name, limit, offset)
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "bad request")))
            }
        }
        get("/api/history/{code}") {
            val code = call.parameters["code"].orEmpty()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceAtLeast(1) ?: 210
            runCatching {
                engine.database.dailyLines(code, limit)
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "bad request")))
            }
        }
        post("/api/tick") {
            val request = call.receive<ManualTickRequest>()
            val tick = engine.manualTick(request)
            if (tick == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unknown code or empty tick"))
            } else {
                call.respond(tick)
            }
        }
        webSocket("/ws") {
            engine.connect(this)
        }
    }
}

private fun fetchKplLivePayload(): String {
    val body = FormBody.Builder()
        .add("a", "ZhiBoContent")
        .add("apiv", "w42")
        .add("c", "ConceptionPoint")
        .add("PhoneOSNew", "1")
        .add("DeviceID", "598d905194133b9e")
        .add("VerSion", "5.21.0.2")
        .add("index", "0")
        .build()
    val request = Request.Builder()
        .url("https://apphwhq.longhuvip.com/w1/api/index.php")
        .post(body)
        .build()
    monitorSourceClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(120)}")
        if (!text.trimStart().startsWith("{") && !text.trimStart().startsWith("[")) {
            error("非 JSON 响应: ${text.take(120)}")
        }
        return text
    }
}

private fun fetchMonitorSourcePayload(id: String): String {
    val url = when (id) {
        "em-popularity" -> "https://data.eastmoney.com/dataapi/xuangu/list?st=POPULARITY_RANK&sr=1&ps=300&p=1&sty=SECUCODE%2CSECURITY_CODE%2CSECURITY_NAME_ABBR%2CPOPULARITY_RANK&filter=(POPULARITY_RANK%3E0)(POPULARITY_RANK%3C%3D1000)&source=SELECT_SECURITIES&client=WEB"
        "ths-hot" -> "https://dq.10jqka.com.cn/fuyao/hot_list_data/out/hot_list/v1/stock?stock_type=a&type=hour&list_type=normal"
        "dzh-hot" -> "https://imsearch.dzh.com.cn/stock/top?size=300&type=0&time=h"
        "cls-hot" -> "https://api3.cls.cn/v1/hot_stock?app=cailianpress&os=android&sv=850&sign=e4d8f886f269874b1578fec645b258fa"
        "tgb-hot" -> "https://www.tgb.cn/new/nrnt/getNoticeStock?type=D"
        "em-lhb" -> {
            val date = LocalDate.now().toString()
            "https://datacenter-web.eastmoney.com/api/data/v1/get?sortColumns=SECURITY_CODE%2CTRADE_DATE&sortTypes=1%2C-1&pageSize=100&pageNumber=1&reportName=RPT_DAILYBILLBOARD_DETAILSNEW&columns=SECURITY_CODE%2CSECUCODE%2CSECURITY_NAME_ABBR%2CTRADE_DATE%2CEXPLAIN%2CCLOSE_PRICE%2CCHANGE_RATE%2CBILLBOARD_NET_AMT%2CBILLBOARD_BUY_AMT%2CBILLBOARD_SELL_AMT%2CBILLBOARD_DEAL_AMT%2CACCUM_AMOUNT%2CDEAL_NET_RATIO%2CDEAL_AMOUNT_RATIO%2CTURNOVERRATE%2CFREE_MARKET_CAP%2CEXPLANATION%2CD1_CLOSE_ADJCHRATE%2CD2_CLOSE_ADJCHRATE%2CD5_CLOSE_ADJCHRATE%2CD10_CLOSE_ADJCHRATE%2CSECURITY_TYPE_CODE&source=WEB&client=WEB&filter=(TRADE_DATE%3C%3D%27$date%27)(TRADE_DATE%3E%3D%27$date%27)"
        }
        "ths-lhb" -> {
            val date = LocalDate.now().toString()
            "https://data.10jqka.com.cn/dataapi/transaction/stock/v1/list?order_field=hot_rank&order_type=asc&date=$date&filter=&page=1&size=50&module=all&order_null_greater=1"
        }
        else -> error("unknown monitor source: $id")
    }
    return if (id == "tgb-hot") fetchTgbPayload(url) else fetchMonitorSourcePayloadOnce(url, null)
}

private fun fetchTgbPayload(url: String): String {
    val firstRequest = monitorSourceRequest(url, null)
    monitorSourceClient.newCall(firstRequest).execute().use { firstResponse ->
        val firstText = firstResponse.body?.string().orEmpty()
        if (firstResponse.isSuccessful && firstText.trimStart().startsWith("{")) return firstText
        val cookie = firstResponse.headers("Set-Cookie")
            .firstOrNull { it.startsWith("acw_tc=") }
            ?.substringBefore(';')
        if (cookie.isNullOrBlank()) {
            error("淘股吧接口失败: HTTP ${firstResponse.code}")
        }
        return fetchMonitorSourcePayloadOnce(url, cookie)
    }
}

private fun fetchMonitorSourcePayloadOnce(url: String, cookie: String?): String {
    val request = monitorSourceRequest(url, cookie)
    monitorSourceClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(120)}")
        if (!text.trimStart().startsWith("{") && !text.trimStart().startsWith("[")) {
            error("非 JSON 响应: ${text.take(120)}")
        }
        return text
    }
}

private fun monitorSourceRequest(url: String, cookie: String?): Request {
    val builder = Request.Builder()
        .url(url)
        .get()
        .header("User-Agent", "Mozilla/5.0")
        .header("Accept", "application/json,text/plain,*/*")
        .header("Referer", refererForMonitorSource(url))
    if (!cookie.isNullOrBlank()) {
        builder.header("Cookie", cookie)
    }
    return builder.build()
}

private fun refererForMonitorSource(url: String): String {
    return when {
        "eastmoney.com" in url -> "https://data.eastmoney.com/"
        "10jqka.com.cn" in url -> "https://data.10jqka.com.cn/"
        "cls.cn" in url -> "https://www.cls.cn/"
        "tgb.cn" in url || "taoguba.com.cn" in url -> "https://www.tgb.cn/"
        "dzh.com.cn" in url -> "https://www.dzh.com.cn/"
        else -> "https://localhost/"
    }
}

private fun debugEastMoneyHistoryRequest(
    code: String,
    direct: Boolean,
    limit: Int,
    end: String
): EastMoneyDebugResult {
    val secId = "${marketId(code)}.$code"
    val url = "http://push2his.eastmoney.com/api/qt/stock/kline/get" +
        "?secid=$secId" +
        "&klt=101" +
        "&fqt=1" +
        "&lmt=$limit" +
        "&end=$end" +
        "&iscca=1" +
        "&fields1=f1%2Cf2%2Cf3%2Cf4%2Cf5%2Cf6%2Cf7%2Cf8" +
        "&fields2=f51%2Cf52%2Cf53%2Cf54%2Cf55%2Cf56%2Cf57%2Cf58%2Cf59%2Cf60%2Cf61%2Cf62%2Cf63%2Cf64" +
        "&forcect=1"
    val hosts = runCatching {
        InetAddress.getAllByName("push2his.eastmoney.com").map { it.hostAddress }
    }.getOrElse { listOf("DNS failed: ${it.message ?: it::class.simpleName}") }
    val okHttpMessages = mutableListOf<String>()
    val client = eastMoneyDebugClient(direct, okHttpMessages)
    val startedAt = System.currentTimeMillis()
    return runCatching {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .header("Accept-Language", "en,zh;q=0.9,zh-CN;q=0.8")
            .header("Referer", "https://quote.eastmoney.com/")
            .header("Cookie", "st_nvi=WkClJtdsBAc4v9Iv-o1Vqcd0f; qgqp_b_id=0f78fd1076cc3135031c17e0f4166b39; nid18=0f7dba095cf921233ab46a9996b57d04; nid18_create_time=1777287090132; gviem=o73csaPU-xf80kF99diWT2fa9; gviem_create_time=1777287090132; websitepoptg_api_time=1777435139298; quote_lt=1; st_pvi=56099489576103; st_sp=2025-08-14%2010%3A02%3A05; st_inirUrl=https%3A%2F%2Fwww.baidu.com%2Flink")
            .header("DNT", "1")
            .header("Proxy-Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
            .build()
        val response = client.newCall(request)
            .execute()
        response.use {
            val body = it.body?.string().orEmpty()
            EastMoneyDebugResult(
                code = code,
                secId = secId,
                direct = direct,
                url = url,
                resolvedHosts = hosts,
                httpCode = it.code,
                success = it.isSuccessful && body.contains("\"klines\""),
                elapsedMs = System.currentTimeMillis() - startedAt,
                contentLength = body.length.toLong(),
                bodyPreview = body.take(600),
                okHttpLog = okHttpMessages.joinToString("\n")
            )
        }
    }.getOrElse {
        EastMoneyDebugResult(
            code = code,
            secId = secId,
            direct = direct,
            url = url,
            resolvedHosts = hosts,
            success = false,
            elapsedMs = System.currentTimeMillis() - startedAt,
            okHttpLog = okHttpMessages.joinToString("\n"),
            error = "${it::class.qualifiedName}: ${it.message}"
        )
    }
}

private fun marketId(code: String): Int {
    return when {
        code.startsWith("000") || code.startsWith("001") || code.startsWith("002") || code.startsWith("003") -> 0
        code.startsWith("30") -> 0
        code.startsWith("43") || code.startsWith("82") || code.startsWith("83") ||
            code.startsWith("87") || code.startsWith("88") || code.startsWith("92") -> 0
        else -> 1
    }
}

private fun eastMoneyDebugClient(direct: Boolean, messages: MutableList<String>): OkHttpClient {
    val loggingInterceptor = HttpLoggingInterceptor { message ->
        messages += message
        okHttpLogger.info(message)
    }.setLevel(HttpLoggingInterceptor.Level.BODY)
    return OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .apply {
            if (direct) proxy(Proxy.NO_PROXY)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

private fun dbViewerHtml(): String = """
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>StockMan DB Viewer</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      color: #20242a;
      background: #f4f6f8;
    }
    main { max-width: 1180px; margin: 0 auto; padding: 24px; }
    header {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      align-items: center;
      flex-wrap: wrap;
      margin-bottom: 16px;
    }
    h1 { margin: 0; font-size: 28px; }
    p { margin: 6px 0 0; color: #66707c; }
    .tabs { display: flex; gap: 8px; flex-wrap: wrap; margin: 16px 0; }
    .chart {
      background: #fff;
      border: 1px solid #dfe5ec;
      border-radius: 8px;
      margin-bottom: 16px;
      overflow: hidden;
    }
    .chartBody {
      margin: 0 16px 16px;
      padding: 12px;
      border: 1px solid #edf0f4;
      border-radius: 8px;
      background: #fbfcfd;
    }
    .chart svg { width: 100%; height: 280px; display: block; }
    .chartTip {
      min-height: 30px;
      margin: 0 16px 10px;
      padding: 7px 10px;
      border-radius: 8px;
      background: #f7f9fb;
      color: #334155;
      font-size: 13px;
      font-weight: 650;
    }
    .hoverDot { opacity: 0; pointer-events: none; }
    .hit { fill: transparent; cursor: crosshair; }
    .hit:hover + .hoverDot { opacity: 1; }
    .toolbar {
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
      padding: 12px 16px;
      border-bottom: 1px solid #edf0f4;
      background: #fff;
    }
    .chartToolbar {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      gap: 16px;
      padding: 16px;
      border-bottom: 1px solid #edf0f4;
      background: #fff;
      flex-wrap: wrap;
    }
    .chartToolbarTitle {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 180px;
    }
    .chartToolbarTitle strong {
      font-size: 16px;
    }
    .chartToolbarTitle span {
      color: #66707c;
      font-size: 13px;
    }
    .chartControls {
      display: flex;
      align-items: flex-end;
      gap: 10px;
      flex-wrap: wrap;
    }
    .chartControls label {
      display: flex;
      flex-direction: column;
      gap: 5px;
      color: #66707c;
      font-size: 12px;
      font-weight: 700;
    }
    .chartControls input {
      width: 118px;
    }
    .chartTitle {
      min-height: 36px;
      display: flex;
      align-items: center;
      padding: 0 10px;
      border: 1px solid #edf0f4;
      border-radius: 8px;
      background: #fbfcfd;
      color: #334155;
      font-size: 13px;
      font-weight: 700;
    }
    button {
      border: 0;
      border-radius: 8px;
      padding: 10px 13px;
      font-weight: 700;
      cursor: pointer;
      color: #20242a;
      background: #e7ebf0;
    }
    button:disabled { cursor: not-allowed; opacity: .45; }
    button.active { color: #fff; background: #20242a; }
    select, input {
      height: 36px;
      border: 1px solid #d5dce5;
      border-radius: 8px;
      padding: 0 10px;
      background: #fff;
      color: #20242a;
    }
    input { width: 88px; }
    .panel {
      background: #fff;
      border: 1px solid #dfe5ec;
      border-radius: 8px;
      overflow: auto;
    }
    .meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-bottom: 1px solid #edf0f4;
      color: #66707c;
      flex-wrap: wrap;
    }
    .tableTitleBlock {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 260px;
    }
    .tableTitleActions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    #tableName {
      color: #20242a;
      font-size: 16px;
    }
    .tableStatus,
    .syncSub {
      color: #66707c;
      font-size: 13px;
      line-height: 1.45;
    }
    .stockRefreshPanel {
      display: flex;
      align-items: flex-end;
      gap: 10px;
      flex-wrap: wrap;
      margin-top: 8px;
    }
    .stockRefreshPanel label {
      display: flex;
      flex-direction: column;
      gap: 5px;
      color: #66707c;
      font-size: 12px;
      font-weight: 700;
    }
    .stockRefreshPanel select,
    .stockRefreshPanel input {
      width: 118px;
    }
    .refreshState {
      min-height: 36px;
      display: flex;
      align-items: center;
      padding: 0 10px;
      border: 1px solid #edf0f4;
      border-radius: 8px;
      background: #fbfcfd;
      color: #334155;
      font-size: 13px;
      font-weight: 700;
    }
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th, td {
      padding: 11px 10px;
      border-bottom: 1px solid #edf0f4;
      text-align: left;
      white-space: nowrap;
    }
    th { background: #f7f9fb; color: #66707c; }
    .error { color: #b42318; padding: 16px; }
    code { color: #334155; }
    .syncPanel {
      padding: 12px 16px;
      border-bottom: 1px solid #edf0f4;
      background: #fbfcfd;
    }
    .syncStats {
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;
      color: #334155;
      font-size: 14px;
      margin-bottom: 10px;
    }
    .syncHeader {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
      flex-wrap: wrap;
      width: 100%;
    }
    .syncTitleBlock {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 260px;
    }
    .syncActions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .progressTrack {
      height: 8px;
      background: #e7ebf0;
      border-radius: 999px;
      overflow: hidden;
      width: min(420px, 100%);
    }
    .progressBar {
      height: 100%;
      width: 0%;
      background: #2563eb;
    }
    .logWindow {
      height: 132px;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-word;
      border: 1px solid #dfe5ec;
      border-radius: 8px;
      background: #0f172a;
      color: #dbeafe;
      padding: 10px;
      font: 12px/1.45 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    }
  </style>
</head>
<body>
<main>
  <header>
    <div>
      <h1>StockMan DB Viewer</h1>
      <p id="dbPath">正在连接数据库...</p>
    </div>
  </header>
  <div class="tabs" id="tabs"></div>
  <section class="chart" id="chartSection" hidden>
    <div class="syncPanel">
      <div class="syncStats">
        <div class="syncHeader">
          <div class="syncTitleBlock">
            <strong>历史同步</strong>
            <span class="syncSub" id="historySyncSummary">正在读取历史同步状态...</span>
          </div>
          <div class="syncActions">
            <span id="historyPendingCount">待同步 -</span>
            <button id="historyIncrementalSyncButton" hidden>增量同步</button>
            <button id="historyFullSyncButton" hidden>全量同步</button>
            <button id="stopHistorySyncButton" hidden>停止</button>
          </div>
        </div>
        <span id="historyProgressText">未开始</span>
        <div class="progressTrack"><div class="progressBar" id="historyProgressBar"></div></div>
      </div>
      <div class="logWindow" id="historyLogWindow">等待同步日志...</div>
    </div>
    <div class="chartToolbar">
      <div class="chartToolbarTitle">
        <strong>日线查询</strong>
        <span>查看历史开盘/收盘走势</span>
      </div>
      <div class="chartControls">
        <label>代码 <input id="chartCode" value="688233" maxlength="6"></label>
        <label>条数 <input id="chartLimit" type="number" min="5" max="500" value="210"></label>
        <button id="loadChart">查询</button>
        <span class="chartTitle" id="chartTitle">等待查询</span>
      </div>
    </div>
    <div class="chartTip" id="chartTip"></div>
    <div class="chartBody" id="chartBody"></div>
  </section>
  <section class="panel">
    <div class="meta">
      <div class="tableTitleBlock">
        <div class="tableTitleActions">
          <strong id="tableName">-</strong>
        </div>
        <span class="tableStatus" id="tableSyncSummary">正在读取表状态...</span>
        <div class="stockRefreshPanel" id="stockRefreshPanel" hidden>
          <label>数据源
            <select id="stockRefreshSource">
              <option value="sina" selected>新浪</option>
              <option value="eastmoney">东方财富</option>
            </select>
          </label>
          <label>模式
            <select id="stockRefreshMode">
              <option value="manual" selected>手动</option>
              <option value="auto">自动</option>
            </select>
          </label>
          <label>间隔(秒)
            <input id="stockRefreshInterval" type="number" min="1" max="3600" value="10">
          </label>
          <button id="stockRefreshButton">刷新</button>
          <span class="refreshState" id="stockRefreshState">空闲</span>
        </div>
      </div>
      <span id="tableTotal">-</span>
    </div>
    <div class="toolbar">
      <button id="prevPage">上一页</button>
      <span id="pageInfo">第 - 页</span>
      <button id="nextPage">下一页</button>
      <label>每页
        <select id="pageSize">
          <option value="50">50</option>
          <option value="100">100</option>
          <option value="200" selected>200</option>
          <option value="500">500</option>
        </select>
      </label>
      <label>跳转
        <input id="pageInput" type="number" min="1" value="1">
      </label>
      <button id="jumpPage">Go</button>
    </div>
    <div id="content"></div>
  </section>
</main>
<script>
let activeTable = "";
let currentPage = 1;
let currentTotal = 0;
let historyLogLines = [];
let lastHistoryLogMessage = "";
let lastHistoryProgressKey = "";
let latestSyncStatus = null;
let latestHistorySyncStatus = null;
let stockRefreshTimer = null;
let stockRefreshRunning = false;

async function loadTables() {
  const rsp = await fetch("/api/db/tables");
  const data = await rsp.json();
  document.getElementById("dbPath").innerHTML = "SQLite: <code>" + data.database + "</code>";
  await loadSyncStatus();
  const tabs = document.getElementById("tabs");
  tabs.innerHTML = "";
  const visibleTables = data.tables.filter(name => name !== "history_sync_result");
  visibleTables.forEach((name, index) => {
    const button = document.createElement("button");
    button.textContent = name;
    button.dataset.tableName = name;
    button.onclick = () => loadTable(name, 1);
    tabs.appendChild(button);
    if (index === 0) loadTable(name, 1);
  });
}

function pageSize() {
  return Number(document.getElementById("pageSize").value || 200);
}

function maxPage() {
  return Math.max(1, Math.ceil(currentTotal / pageSize()));
}

function updatePager() {
  const pages = maxPage();
  document.getElementById("pageInfo").textContent = "第 " + currentPage + " / " + pages + " 页";
  document.getElementById("pageInput").value = currentPage;
  document.getElementById("prevPage").disabled = currentPage <= 1;
  document.getElementById("nextPage").disabled = currentPage >= pages;
}

async function loadTable(name, page = currentPage) {
  activeTable = name;
  currentPage = Math.max(1, page);
  const previewTable = name === "historystock" ? "history_sync_result" : name;
  document.getElementById("chartSection").hidden = name !== "historystock";
  document.getElementById("stockRefreshPanel").hidden = name !== "stock";
  document.getElementById("historyIncrementalSyncButton").hidden = name !== "historystock";
  document.getElementById("historyFullSyncButton").hidden = name !== "historystock";
  document.getElementById("stopHistorySyncButton").hidden = name !== "historystock";
  updateStockAutoRefresh();
  document.querySelectorAll("#tabs button").forEach(btn => {
    btn.classList.toggle("active", btn.dataset.tableName === name);
  });
  document.getElementById("tableName").textContent = name === "historystock" ? "history_sync_result" : name;
  updateTableSyncSummary();
  document.getElementById("tableTotal").textContent = "加载中";
  const content = document.getElementById("content");
  content.innerHTML = "";
  try {
    const limit = pageSize();
    const offset = (currentPage - 1) * limit;
    const rsp = await fetch("/api/db/table/" + encodeURIComponent(previewTable) + "?limit=" + limit + "&offset=" + offset);
    const data = await rsp.json();
    currentTotal = data.total;
    const from = data.total === 0 ? 0 : offset + 1;
    const to = Math.min(offset + data.rows.length, data.total);
    document.getElementById("tableTotal").textContent = "共 " + data.total + " 行，当前 " + from + "-" + to;
    updatePager();
    const table = document.createElement("table");
    const thead = document.createElement("thead");
    const headerRow = document.createElement("tr");
    data.columns.forEach(column => {
      const th = document.createElement("th");
      th.textContent = column;
      headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);
    table.appendChild(thead);
    const tbody = document.createElement("tbody");
    data.rows.forEach(row => {
      const tr = document.createElement("tr");
      data.columns.forEach(column => {
        const td = document.createElement("td");
        td.textContent = row[column] ?? "";
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    content.appendChild(table);
    if (name === "historystock") loadDailyChart().catch(() => {});
    if (name === "historystock") loadHistorySyncStatus().catch(() => {});
  } catch (error) {
    content.innerHTML = '<div class="error">' + error + '</div>';
    updatePager();
  }
}

async function loadSyncStatus() {
  const rsp = await fetch("/api/sync/status");
  latestSyncStatus = await rsp.json();
  updateTableSyncSummary();
}

function updateTableSyncSummary() {
  const tableSummary = document.getElementById("tableSyncSummary");
  if (!tableSummary) return;
  if (activeTable === "stock" && latestSyncStatus) {
    const date = formatTimestamp(latestSyncStatus.lastStockSyncTime);
    const count = latestSyncStatus.lastStockSyncCount || "-";
    const source = latestSyncStatus.lastStockSyncSource || "-";
    tableSummary.textContent = "stock " + latestSyncStatus.stockCount + " 行 · 上次实时同步 " + date + " / " + count + " 只 / " + source;
    return;
  }
  if (activeTable === "historystock" && latestHistorySyncStatus) {
    const date = latestHistorySyncStatus.lastHistorySyncDate || "-";
    const count = latestHistorySyncStatus.lastHistorySyncCount || "-";
    const stocks = latestHistorySyncStatus.lastHistorySyncStockCount || "-";
    const source = latestHistorySyncStatus.lastHistorySyncSource || "-";
    tableSummary.textContent = "预览 history_sync_result · historystock " + latestHistorySyncStatus.historyCount + " 行 · 上次历史同步 " + date + " / " + stocks + " 只 / " + count + " 行 / " + source;
    return;
  }
  tableSummary.textContent = activeTable ? "当前表预览" : "正在读取表状态...";
}

function formatTimestamp(value) {
  if (!value) return "-";
  const date = new Date(Number(value));
  if (Number.isNaN(date.getTime())) return "-";
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  });
}

async function loadHistorySyncStatus() {
  const rsp = await fetch("/api/sync/history/status");
  if (!rsp.ok) return;
  const data = await rsp.json();
  latestHistorySyncStatus = data;
  document.getElementById("historyPendingCount").textContent = "待同步 " + (data.pendingHistorySyncCount || 0);
  const date = data.lastHistorySyncDate || "-";
  const count = data.lastHistorySyncCount || "-";
  const stocks = data.lastHistorySyncStockCount || "-";
  const source = data.lastHistorySyncSource || "-";
  document.getElementById("historySyncSummary").textContent =
    "historystock " + data.historyCount + " 行 · 上次历史同步 " + date + " / " + stocks + " 只 / " + count + " 行 / " + source;
  updateTableSyncSummary();
}

function appendHistoryLog(message) {
  if (!message) return;
  if (message === lastHistoryLogMessage) return;
  lastHistoryLogMessage = message;
  const time = new Date().toLocaleTimeString("zh-CN");
  const line = "[" + time + "] " + message;
  historyLogLines.push(line);
  if (historyLogLines.length > 120) historyLogLines = historyLogLines.slice(historyLogLines.length - 120);
  const log = document.getElementById("historyLogWindow");
  log.textContent = historyLogLines.join("\\n");
  log.scrollTop = log.scrollHeight;
}

async function loadHistoryProgress() {
  const rsp = await fetch("/api/sync/history/progress");
  if (!rsp.ok) return;
  const data = await rsp.json();
  const total = data.total || 0;
  const completed = data.completed || 0;
  const running = data.running === true;
  const pct = total > 0 ? Math.floor(completed * 100 / total) : 0;
  document.getElementById("historyProgressText").textContent =
    (running ? "同步中" : (data.stopRequested ? "已停止" : "空闲")) +
    " · " + completed + "/" + total +
    " · 成功 " + (data.success || 0) +
    " · 失败 " + (data.failed || 0) +
    (running && data.currentCode ? " · 当前 " + data.currentCode + " " + (data.currentName || "") : "");
  document.getElementById("historyProgressBar").style.width = pct + "%";
  document.getElementById("historyIncrementalSyncButton").disabled = running;
  document.getElementById("historyFullSyncButton").disabled = running;
  document.getElementById("stopHistorySyncButton").disabled = !running || data.stopRequested;
  const progressKey = [
    data.lastMessage || "",
    data.running || false,
    data.completed || 0,
    data.success || 0,
    data.failed || 0,
    data.endedAt || ""
  ].join("|");
  if (data.lastMessage && progressKey !== lastHistoryProgressKey) {
    lastHistoryProgressKey = progressKey;
    appendHistoryLog(data.lastMessage);
  }
  if (!running && activeTable === "historystock") {
    loadHistorySyncStatus().catch(() => {});
  }
}

function renderDailyChart(data) {
  const body = document.getElementById("chartBody");
  const lines = data.lines || [];
  document.getElementById("chartTitle").textContent = data.code + " " + (data.name || "") + "，" + lines.length + " 条";
  document.getElementById("chartTip").textContent = "悬停曲线查看开盘/收盘价格";
  if (lines.length === 0) {
    body.innerHTML = '<div class="error">没有日线数据</div>';
    return;
  }
  const width = 1000;
  const height = 260;
  const pad = 34;
  const values = lines.map(item => item.closePrice);
  const min = Math.min.apply(null, values);
  const max = Math.max.apply(null, values);
  const span = Math.max(0.01, max - min);
  const x = index => pad + index * ((width - pad * 2) / Math.max(1, lines.length - 1));
  const y = value => height - pad - ((value - min) / span) * (height - pad * 2);
  const points = lines.map((item, index) => x(index).toFixed(1) + "," + y(item.closePrice).toFixed(1)).join(" ");
  const last = lines[lines.length - 1];
  const first = lines[0];
  const hitWidth = Math.max(6, (width - pad * 2) / Math.max(1, lines.length - 1));
  const hovers = lines.map((item, index) => {
    const cx = x(index);
    const cy = y(item.closePrice);
    const label = item.date + ' 开 ' + item.openPrice.toFixed(2) + ' / 收 ' + item.closePrice.toFixed(2) + ' / ' + item.chg.toFixed(2) + '%';
    return '<rect class="hit" x="' + (cx - hitWidth / 2).toFixed(1) + '" y="' + pad + '" width="' + hitWidth.toFixed(1) + '" height="' + (height - pad * 2) + '" data-label="' + label + '"></rect>' +
      '<circle class="hoverDot" cx="' + cx.toFixed(1) + '" cy="' + cy.toFixed(1) + '" r="5" fill="#ef4444"></circle>';
  }).join("");
  body.innerHTML =
    '<svg id="dailySvg" viewBox="0 0 ' + width + ' ' + height + '" role="img">' +
    '<line x1="' + pad + '" y1="' + pad + '" x2="' + pad + '" y2="' + (height - pad) + '" stroke="#d5dce5"/>' +
    '<line x1="' + pad + '" y1="' + (height - pad) + '" x2="' + (width - pad) + '" y2="' + (height - pad) + '" stroke="#d5dce5"/>' +
    '<text x="' + pad + '" y="22" fill="#66707c" font-size="13">高 ' + max.toFixed(2) + '</text>' +
    '<text x="' + pad + '" y="' + (height - 10) + '" fill="#66707c" font-size="13">低 ' + min.toFixed(2) + '</text>' +
    '<text x="' + (width - 180) + '" y="22" fill="#20242a" font-size="14">收 ' + last.closePrice.toFixed(2) + ' / ' + last.chg.toFixed(2) + '%</text>' +
    '<polyline points="' + points + '" fill="none" stroke="#2563eb" stroke-width="2.5"/>' +
    '<circle cx="' + x(lines.length - 1).toFixed(1) + '" cy="' + y(last.closePrice).toFixed(1) + '" r="4" fill="#2563eb"/>' +
    '<text x="' + pad + '" y="' + (height - 5) + '" fill="#66707c" font-size="12">' + first.date + '</text>' +
    '<text x="' + (width - 90) + '" y="' + (height - 5) + '" fill="#66707c" font-size="12">' + last.date + '</text>' +
    hovers +
    '</svg>';
  const tip = document.getElementById("chartTip");
  document.querySelectorAll("#dailySvg .hit").forEach(node => {
    node.addEventListener("mouseenter", () => { tip.textContent = node.dataset.label || ""; });
    node.addEventListener("mousemove", () => { tip.textContent = node.dataset.label || ""; });
    node.addEventListener("mouseleave", () => { tip.textContent = "悬停曲线查看开盘/收盘价格"; });
  });
}

async function loadDailyChart() {
  const code = document.getElementById("chartCode").value.trim();
  const limit = Number(document.getElementById("chartLimit").value || 210);
  if (!code) return;
  const rsp = await fetch("/api/history/" + encodeURIComponent(code) + "?limit=" + limit);
  if (!rsp.ok) throw new Error(await rsp.text());
  renderDailyChart(await rsp.json());
}

async function refreshStockTable(trigger) {
  if (stockRefreshRunning) return;
  const button = document.getElementById("stockRefreshButton");
  const source = document.getElementById("stockRefreshSource").value || "sina";
  const state = document.getElementById("stockRefreshState");
  stockRefreshRunning = true;
  button.disabled = true;
  button.textContent = trigger === "auto" ? "自动刷新中..." : "刷新中...";
  state.textContent = (trigger === "auto" ? "自动" : "手动") + "刷新中 · " + source;
  try {
    const rsp = await fetch("/api/sync/stocks?source=" + encodeURIComponent(source), { method: "POST" });
    if (!rsp.ok) throw new Error(await rsp.text());
    latestSyncStatus = await rsp.json();
    await loadSyncStatus();
    if (activeTable) await loadTable(activeTable);
    state.textContent = "刷新成功 · " + formatTimestamp(latestSyncStatus.lastStockSyncTime);
  } catch (error) {
    state.textContent = "刷新失败 · " + error;
    if (trigger !== "auto") alert(error);
  } finally {
    stockRefreshRunning = false;
    button.disabled = false;
    button.textContent = "刷新";
  }
}

function updateStockAutoRefresh() {
  if (stockRefreshTimer != null) {
    clearInterval(stockRefreshTimer);
    stockRefreshTimer = null;
  }
  const state = document.getElementById("stockRefreshState");
  if (!state) return;
  const mode = document.getElementById("stockRefreshMode").value;
  const seconds = Math.max(1, Math.min(3600, Number(document.getElementById("stockRefreshInterval").value || 10)));
  document.getElementById("stockRefreshInterval").value = seconds;
  if (activeTable !== "stock" || mode !== "auto") {
    if (activeTable === "stock") state.textContent = "空闲";
    return;
  }
  state.textContent = "自动刷新已开启 · 每 " + seconds + " 秒";
  stockRefreshTimer = setInterval(() => {
    if (activeTable === "stock") refreshStockTable("auto");
  }, seconds * 1000);
}

document.getElementById("stockRefreshButton").onclick = () => refreshStockTable("manual");
document.getElementById("stockRefreshMode").onchange = updateStockAutoRefresh;
document.getElementById("stockRefreshInterval").onchange = updateStockAutoRefresh;
document.getElementById("stockRefreshSource").onchange = () => {
  const state = document.getElementById("stockRefreshState");
  state.textContent = "数据源已切换 · " + document.getElementById("stockRefreshSource").value;
  updateStockAutoRefresh();
};

async function startHistorySync(full) {
  const incrementalButton = document.getElementById("historyIncrementalSyncButton");
  const fullButton = document.getElementById("historyFullSyncButton");
  incrementalButton.disabled = true;
  fullButton.disabled = true;
  const button = full ? fullButton : incrementalButton;
  const originalText = button.textContent;
  button.textContent = full ? "全量同步中..." : "增量同步中...";
  try {
    const rsp = await fetch("/api/sync/history/start?full=" + (full ? "true" : "false"), { method: "POST" });
    if (!rsp.ok) throw new Error(await rsp.text());
    await rsp.json();
    lastHistoryLogMessage = "";
    lastHistoryProgressKey = "";
    appendHistoryLog(full ? "后台历史全量同步已启动" : "后台历史增量同步已启动");
    await loadHistoryProgress();
    await loadHistorySyncStatus();
  } catch (error) {
    alert(error);
    incrementalButton.disabled = false;
    fullButton.disabled = false;
  } finally {
    button.textContent = originalText;
  }
}

document.getElementById("historyIncrementalSyncButton").onclick = () => startHistorySync(false);
document.getElementById("historyFullSyncButton").onclick = () => startHistorySync(true);

document.getElementById("stopHistorySyncButton").onclick = async () => {
  const button = document.getElementById("stopHistorySyncButton");
  button.disabled = true;
  try {
    const rsp = await fetch("/api/sync/history/stop", { method: "POST" });
    if (!rsp.ok) throw new Error(await rsp.text());
    appendHistoryLog("已发送停止请求");
    await loadHistoryProgress();
  } catch (error) {
    alert(error);
  }
};

document.getElementById("loadChart").onclick = () => {
  loadDailyChart().catch(error => {
    document.getElementById("chartBody").innerHTML = '<div class="error">' + error + '</div>';
  });
};

document.getElementById("prevPage").onclick = () => {
  if (activeTable && currentPage > 1) loadTable(activeTable, currentPage - 1);
};

document.getElementById("nextPage").onclick = () => {
  if (activeTable && currentPage < maxPage()) loadTable(activeTable, currentPage + 1);
};

document.getElementById("jumpPage").onclick = () => {
  if (!activeTable) return;
  const target = Number(document.getElementById("pageInput").value || 1);
  loadTable(activeTable, Math.min(Math.max(1, target), maxPage()));
};

document.getElementById("pageSize").onchange = () => {
  if (activeTable) loadTable(activeTable, 1);
};

setInterval(() => {
  loadHistoryProgress().catch(() => {});
  if (activeTable === "historystock") loadSyncStatus().catch(() => {});
}, 1000);

loadTables();
loadHistoryProgress().catch(() => {});
loadHistorySyncStatus().catch(() => {});
</script>
</body>
</html>
""".trimIndent()
