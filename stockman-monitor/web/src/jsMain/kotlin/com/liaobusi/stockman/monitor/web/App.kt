package com.liaobusi.stockman.monitor.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.css.StyleScope
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.background
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flex
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontFamily
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.gridTemplateColumns
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.maxWidth
import org.jetbrains.compose.web.css.minHeight
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Iframe
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event

private val json = Json { ignoreUnknownKeys = true }
private const val MONITOR_REFRESH_MIN_SECONDS = 2
private const val MONITOR_REFRESH_MAX_SECONDS = 10

fun main() {
    renderComposable(rootElementId = "root") {
        Style(AppStyles)
        MonitorApp()
    }
}

@Composable
fun MonitorApp() {
    val stocks = remember { mutableStateListOf<StockTick>() }
    val alerts = remember { mutableStateListOf<AlertEvent>() }
    val selectedSources = remember { mutableStateListOf<String>().also { list -> list.addAll(monitorSources.map { it.id }) } }
    var connected by remember { mutableStateOf(false) }
    var activePage by remember { mutableStateOf("monitor") }
    var monitorSourcesByCode by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var monitorSourceStatus by remember { mutableStateOf("来源加载中") }
    var customCodes by remember { mutableStateOf("") }
    var monitoringEnabled by remember { mutableStateOf(false) }
    var monitorRefreshSeconds by remember { mutableStateOf(5) }
    var tradingTime by remember { mutableStateOf(isTradingTime()) }
    var notificationState by remember {
        mutableStateOf(runCatching { Notification.permission }.getOrDefault("default"))
    }

    LaunchedEffect(Unit) {
        connectWebSocket(
            onOpen = { connected = true },
            onClose = { connected = false },
            onStocks = { next ->
                stocks.clear()
                stocks.addAll(next.sortedByDescending { it.chg })
            },
            onAlert = { alert ->
                if (monitoringEnabled && isTradingTime()) {
                    alerts.add(0, alert)
                    if (alerts.size > 30) alerts.removeLast()
                    showNotification(alert)
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            tradingTime = isTradingTime()
            if (!tradingTime && monitoringEnabled) {
                monitoringEnabled = false
            }
            delay(30_000)
        }
    }

    LaunchedEffect(selectedSources.toList(), customCodes, monitorRefreshSeconds) {
        while (true) {
            val result = loadMonitorSourceMap(selectedSources.toList(), customCodes)
            monitorSourcesByCode = result.sourcesByCode
            monitorSourceStatus = result.message
            delay(monitorRefreshSeconds.coerceIn(MONITOR_REFRESH_MIN_SECONDS, MONITOR_REFRESH_MAX_SECONDS) * 1_000L)
        }
    }

    Div({ classes(AppStyles.page) }) {
        Div({ classes(AppStyles.shell) }) {
            Header(
                connected = connected,
                notificationState = notificationState,
                activePage = activePage,
                onPageChange = { activePage = it },
                onRequestNotification = {
                    requestNotificationPermission { notificationState = it }
                }
            )
            when (activePage) {
                "monitor" -> {
                MonitorSourcePanel(
                    selectedSources = selectedSources,
                    customCodes = customCodes,
                    status = monitorSourceStatus,
                    onToggleSource = { id ->
                        if (selectedSources.contains(id)) selectedSources.remove(id) else selectedSources.add(id)
                    },
                    onCustomCodesChange = { customCodes = it },
                    onRefresh = {
                        loadMonitorSourceMapAsync(selectedSources.toList(), customCodes) { result ->
                            monitorSourcesByCode = result.sourcesByCode
                            monitorSourceStatus = result.message
                        }
                    }
                )
                val monitorStocks = stocks
                    .mapNotNull { stock ->
                        val source = monitorSourcesByCode[stock.code]
                        if (source == null) null else MonitorStock(stock, source)
                    }
                    .sortedByDescending { it.tick.chg }
                Div({ classes(AppStyles.grid) }) {
                    Div({ classes(AppStyles.panel, AppStyles.marketPanel) }) {
                        MonitorSectionTitle(
                            enabled = monitoringEnabled,
                            tradingTime = tradingTime,
                            refreshSeconds = monitorRefreshSeconds,
                            meta = "${monitorStocks.size} / ${stocks.size} 只标的",
                            onRefreshSecondsChange = { monitorRefreshSeconds = it.coerceIn(MONITOR_REFRESH_MIN_SECONDS, MONITOR_REFRESH_MAX_SECONDS) },
                            onStart = {
                                if (isTradingTime()) {
                                    monitoringEnabled = true
                                }
                            },
                            onStop = { monitoringEnabled = false }
                        )
                        StockTable(monitorStocks)
                    }
                    Div({ classes(AppStyles.panel) }) {
                        SectionTitle("异动通知", if (monitoringEnabled) "${alerts.size} 条" else "未开始监控")
                        AlertList(alerts)
                    }
                }
                ManualTickPanel { alert ->
                    alerts.add(0, alert)
                    if (alerts.size > 30) alerts.removeLast()
                    showNotification(alert)
                }
                EastMoneyDebugPanel()
                }
                "kpl-live" -> KplLivePage()
                "jiuyang" -> JiuyangPage()
            }
        }
    }
}

private fun connectWebSocket(
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onStocks: (List<StockTick>) -> Unit,
    onAlert: (AlertEvent) -> Unit
) {
    val protocol = if (window.location.protocol == "https:") "wss" else "ws"
    val host = window.location.hostname.ifBlank { "localhost" }
    val socket = WebSocket("$protocol://$host:8080/ws")
    socket.onopen = { _: Event -> onOpen() }
    socket.onclose = { _: Event -> onClose() }
    socket.onerror = { _: Event -> onClose() }
    socket.onmessage = { event ->
        val message = json.decodeFromString<ClientMessage>(event.asDynamic().data.toString())
        if (message.stocks.isNotEmpty()) {
            onStocks(message.stocks)
        }
        message.alert?.let(onAlert)
    }
}

@Composable
fun Header(
    connected: Boolean,
    notificationState: String,
    activePage: String,
    onPageChange: (String) -> Unit,
    onRequestNotification: () -> Unit
) {
    Div({ classes(AppStyles.header) }) {
        Div {
            H1 { Text("StockMan Monitor") }
            P { Text("本地行情监控 · Compose Web") }
        }
        Div({ classes(AppStyles.actions) }) {
            Button(attrs = {
                classes(if (activePage == "monitor") AppStyles.primaryButton else AppStyles.secondaryButton)
                onClick { onPageChange("monitor") }
            }) { Text("监控") }
            Button(attrs = {
                classes(if (activePage == "kpl-live") AppStyles.primaryButton else AppStyles.secondaryButton)
                onClick { onPageChange("kpl-live") }
            }) { Text("开盘啦 异动直播") }
            Button(attrs = {
                classes(if (activePage == "jiuyang") AppStyles.primaryButton else AppStyles.secondaryButton)
                onClick { onPageChange("jiuyang") }
            }) { Text("韭阳公社异动") }
            Span({ classes(if (connected) AppStyles.okPill else AppStyles.warnPill) }) {
                Text(if (connected) "已连接" else "未连接")
            }
            Button(attrs = {
                classes(AppStyles.primaryButton)
                onClick { onRequestNotification() }
            }) {
                Text(
                    when (notificationState) {
                        "granted" -> "通知已开启"
                        "denied" -> "通知被拒绝"
                        "unsupported" -> "不支持通知"
                        else -> "开启通知"
                    }
                )
            }
        }
    }
}

@Composable
fun KplLivePage() {
    val items = remember { mutableStateListOf<ReplayLiveItem>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var lastRefresh by remember { mutableStateOf("-") }
    var stopped by remember { mutableStateOf(false) }

    suspend fun refresh() {
        runCatching {
            val formData = js("new URLSearchParams()")
            formData.append("a", "ZhiBoContent")
            formData.append("apiv", "w42")
            formData.append("c", "ConceptionPoint")
            formData.append("PhoneOSNew", "1")
            formData.append("DeviceID", "598d905194133b9e")
            formData.append("VerSion", "5.21.0.2")
            formData.append("index", "0")
            val options = js("({})")
            options.method = "POST"
            options.headers = js("({'Content-Type':'application/x-www-form-urlencoded'})")
            options.body = formData
            val text = fetchKplLiveText(options)
            json.decodeFromString<ReplayLiveResponse>(text)
        }.onSuccess { payload ->
            items.clear()
            items.addAll(payload.list)
            error = ""
            lastRefresh = formatTime((js("Date.now()") as Double).toLong())
        }.onFailure {
            error = it.message ?: it.toString()
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        while (!isAfterKplLiveClose()) {
            refresh()
            delay(10_000)
        }
        refresh()
        stopped = true
    }

    Div({ classes(AppStyles.replayLayout) }) {
        Div({ classes(AppStyles.panel) }) {
            SectionTitle(
                "开盘啦 异动直播",
                if (loading) {
                    "加载中"
                } else if (stopped) {
                    "直播已结束 · $lastRefresh"
                } else {
                    "10 秒自动刷新 · $lastRefresh"
                }
            )
            if (error.isNotBlank()) {
                Div({ classes(AppStyles.errorBox) }) { Text(error) }
            }
            if (!loading && items.isEmpty() && error.isBlank()) {
                Div({ classes(AppStyles.emptyState) }) { Text("暂无复盘内容") }
            }
            Div({ classes(AppStyles.replayList) }) {
                items.forEach { item ->
                    ReplayCard(item)
                }
            }
        }
    }
}

private suspend fun fetchKplLiveText(options: dynamic): String {
    return runCatching {
        val response = window.fetch("https://apphwhq.longhuvip.com/w1/api/index.php", options).await()
        val text = response.text().await()
        if (!response.ok) kotlin.error("HTTP ${response.status.toInt()}: $text")
        if (!text.trimStart().startsWith("{") && !text.trimStart().startsWith("[")) {
            kotlin.error("non json response")
        }
        val payload = JSON.parse<dynamic>(text)
        val length = payload.List?.length as? Int ?: 0
        if (length <= 0) kotlin.error("empty kpl live list")
        text
    }.getOrElse {
        val response = window.fetch("http://localhost:8080/api/replay/kpl-live").await()
        val text = response.text().await()
        if (!response.ok) kotlin.error("proxy HTTP ${response.status.toInt()}: $text")
        text
    }
}

@Composable
fun JiuyangPage() {
    var date by remember { mutableStateOf(todayDate()) }
    val url = "https://www.jiuyangongshe.com/action/$date"

    Div({ classes(AppStyles.replayLayout) }) {
        Div({ classes(AppStyles.panel) }) {
            Div({ classes(AppStyles.sectionTitle) }) {
                H2 { Text("韭阳公社异动") }
                Div({ classes(AppStyles.form) }) {
                    Input(type = InputType.Date) {
                        value(date)
                        onInput { date = it.value }
                    }
                    Button(attrs = {
                        classes(AppStyles.secondaryButton)
                        onClick { window.open(url, "_blank") }
                    }) { Text("新窗口") }
                }
            }
            Iframe(attrs = {
                attr("src", url)
                classes(AppStyles.webFrame)
            })
        }
    }
}

private data class ReplayStockChip(
    val code: String,
    val name: String,
    val change: Double
)

private fun List<kotlinx.serialization.json.JsonElement>.toReplayStockChip(): ReplayStockChip? {
    val code = getOrNull(0)?.jsonPrimitive?.content ?: return null
    val name = getOrNull(1)?.jsonPrimitive?.content ?: return null
    val change = getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: 0.0
    return ReplayStockChip(code = code, name = name, change = change)
}

@Composable
fun ReplayCard(item: ReplayLiveItem) {
    Div({ classes(AppStyles.replayCard) }) {
        Div({ classes(AppStyles.replayMeta) }) {
            Span({ classes(AppStyles.timeText) }) { Text(formatSeconds(item.time)) }
            if (item.plateName.isNotBlank()) {
                Span({ classes(AppStyles.platePill) }) {
                    Text(item.plateName + if (item.plateChange.isNotBlank()) " ${item.plateChange}%" else "")
                }
            }
        }
        Div({ classes(AppStyles.replayBody) }) {
            if (item.image.isNotBlank()) {
                Img(src = item.image, attrs = { classes(AppStyles.avatar) })
            }
            Div({ classes(AppStyles.replayContent) }) {
                P { Text(item.comment) }
                val stocks = item.stock.mapNotNull { it.toReplayStockChip() }.take(8)
                if (stocks.isNotEmpty()) {
                    Div({ classes(AppStyles.stockChips) }) {
                        stocks.forEach { stock ->
                            Span({ classes(if (stock.change >= 0.0) AppStyles.stockChipUp else AppStyles.stockChipDown) }) {
                                Text("${stock.code} ${stock.name} ${stock.change.fmt()}%")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, meta: String) {
    Div({ classes(AppStyles.sectionTitle) }) {
        H2 { Text(title) }
        Span { Text(meta) }
    }
}

@Composable
fun MonitorSectionTitle(
    enabled: Boolean,
    tradingTime: Boolean,
    refreshSeconds: Int,
    meta: String,
    onRefreshSecondsChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Div({ classes(AppStyles.sectionTitle) }) {
        H2 { Text("实时监控") }
        Div({ classes(AppStyles.monitorActions) }) {
            Span({ classes(if (enabled) AppStyles.okPill else AppStyles.warnPill) }) {
                Text(
                    when {
                        enabled -> "监控中"
                        tradingTime -> "未开始"
                        else -> "非交易时间"
                    }
                )
            }
            Span { Text(meta) }
            Span({ classes(AppStyles.monitorIntervalControl) }) {
                Text("间隔")
                Input(type = InputType.Number) {
                    value(refreshSeconds.toString())
                    attr("min", MONITOR_REFRESH_MIN_SECONDS.toString())
                    attr("max", MONITOR_REFRESH_MAX_SECONDS.toString())
                    attr("step", "1")
                    onInput { event ->
                        event.value.toString().toIntOrNull()?.let { onRefreshSecondsChange(it) }
                    }
                }
                Text("秒")
            }
            Button(attrs = {
                classes(if (enabled) AppStyles.secondaryButton else AppStyles.primaryButton)
                if (!tradingTime && !enabled) disabled()
                onClick {
                    if (enabled) onStop() else onStart()
                }
            }) {
                Text(if (enabled) "停止监控" else "开始监控")
            }
        }
    }
}

@Composable
fun MonitorSourcePanel(
    selectedSources: List<String>,
    customCodes: String,
    status: String,
    onToggleSource: (String) -> Unit,
    onCustomCodesChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Div({ classes(AppStyles.sourcePanel) }) {
        Div({ classes(AppStyles.sourceHeader) }) {
            Div {
                H2 { Text("监控来源") }
                P { Text(status) }
            }
            Button(attrs = {
                classes(AppStyles.secondaryButton)
                onClick { onRefresh() }
            }) { Text("刷新来源") }
        }
        Div({ classes(AppStyles.sourceButtons) }) {
            SourceGroupRow("热榜", monitorSources.filter { it.group == "hot" }, selectedSources, onToggleSource)
            SourceGroupRow("龙虎榜", monitorSources.filter { it.group == "lhb" }, selectedSources, onToggleSource)
        }
        Div({ classes(AppStyles.sourceLegend) }) {
            monitorSources.forEach { source ->
                SourceIcon(source.name)
            }
        }
        Div({ classes(AppStyles.customCodesRow) }) {
            Span { Text("自定义") }
            Input(type = InputType.Text) {
                value(customCodes)
                placeholder("多个代码用空格隔开，如 300059 600519")
                onInput { onCustomCodesChange(it.value) }
            }
        }
    }
}

@Composable
private fun SourceGroupRow(
    title: String,
    sources: List<MonitorSource>,
    selectedSources: List<String>,
    onToggleSource: (String) -> Unit
) {
    Div({ classes(AppStyles.sourceGroupRow) }) {
        Span({ classes(AppStyles.sourceGroupTitle) }) { Text(title) }
        Div({ classes(AppStyles.sourceGroupButtons) }) {
            sources.forEach { source ->
                Button(attrs = {
                    classes(if (selectedSources.contains(source.id)) AppStyles.sourceButtonOn else AppStyles.sourceButtonOff)
                    onClick { onToggleSource(source.id) }
                }) {
                    Text(source.name)
                    SourceIcon(source.name)
                }
            }
        }
    }
}

@Composable
fun SourceIcon(sourceName: String) {
    val source = monitorSources.firstOrNull { it.name == sourceName }
    val icon = source?.icon ?: if (sourceName == "自定义") "自" else sourceName.take(1)
    Span({
        classes(sourceIconClass(sourceName))
        attr("title", sourceName)
    }) {
        Text(icon)
    }
}

@Composable
fun SourceIconSet(sourceLabel: String) {
    Div({ classes(AppStyles.sourceIconSet) }) {
        sourceLabel.split(" / ").filter { it.isNotBlank() }.forEach { name ->
            SourceIcon(name)
        }
    }
}

@Composable
private fun StockTable(stocks: List<MonitorStock>) {
    val pageSize = 50
    var page by remember { mutableStateOf(1) }
    val totalPages = ((stocks.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val currentPage = page.coerceIn(1, totalPages)
    val fromIndex = ((currentPage - 1) * pageSize).coerceAtMost(stocks.size)
    val toIndex = (fromIndex + pageSize).coerceAtMost(stocks.size)
    val pageStocks = stocks.subList(fromIndex, toIndex)

    LaunchedEffect(stocks.size, totalPages) {
        if (page != currentPage) page = currentPage
    }

    Div {
        Table({ classes(AppStyles.table) }) {
            Thead {
                Tr {
                    Th { Text("代码") }
                    Th { Text("名称") }
                    Th { Text("来源") }
                    Th { Text("现价") }
                    Th { Text("涨跌幅") }
                    Th { Text("涨停") }
                    Th { Text("跌停") }
                }
            }
            Tbody {
                pageStocks.forEach { item ->
                    val stock = item.tick
                    Tr {
                        Td { Text(stock.code) }
                        Td { Text(stock.name) }
                        Td { SourceIconSet(item.source) }
                        Td { Text(stock.price.fmt()) }
                        Td({ classes(if (stock.chg >= 0) AppStyles.upText else AppStyles.downText) }) {
                            Text("${stock.chg.fmt()}%")
                        }
                        Td { Text(stock.ztPrice.fmt()) }
                        Td { Text(stock.dtPrice.fmt()) }
                    }
                }
            }
        }
        Div({ classes(AppStyles.pagination) }) {
            Span {
                val start = if (stocks.isEmpty()) 0 else fromIndex + 1
                Text("第 $currentPage / $totalPages 页 · $start-$toIndex / ${stocks.size}")
            }
            Div({ classes(AppStyles.pageButtons) }) {
                Button(attrs = {
                    classes(AppStyles.secondaryButton)
                    if (currentPage <= 1) disabled()
                    onClick { page = (currentPage - 1).coerceAtLeast(1) }
                }) { Text("上一页") }
                Button(attrs = {
                    classes(AppStyles.secondaryButton)
                    if (currentPage >= totalPages) disabled()
                    onClick { page = (currentPage + 1).coerceAtMost(totalPages) }
                }) { Text("下一页") }
            }
        }
    }
}

@Composable
fun AlertList(alerts: List<AlertEvent>) {
    if (alerts.isEmpty()) {
        Div({ classes(AppStyles.emptyState) }) {
            Text("等待异动信号")
        }
        return
    }
    Div({ classes(AppStyles.alertList) }) {
        alerts.forEach { alert ->
            Div({ classes(AppStyles.alertItem) }) {
                Div({ classes(AppStyles.alertTop) }) {
                    Span({ classes(AppStyles.alertTitle) }) { Text("${alert.code} ${alert.name}") }
                    Span({ classes(if (alert.chg >= 0) AppStyles.upText else AppStyles.downText) }) {
                        Text("${alert.chg.fmt()}%")
                    }
                }
                P { Text(alert.content) }
                Span({ classes(AppStyles.timeText) }) { Text(formatTime(alert.time)) }
            }
        }
    }
}

@Composable
fun ManualTickPanel(onManualAlert: (AlertEvent) -> Unit) {
    var code by remember { mutableStateOf("600519") }
    var chg by remember { mutableStateOf("4.2") }
    var status by remember { mutableStateOf("") }

    Div({ classes(AppStyles.manual) }) {
        Div {
            H2 { Text("手动测试") }
            P { Text("推一个涨跌幅到本地服务，并直接唤醒一条浏览器通知。") }
        }
        Div({ classes(AppStyles.form) }) {
            Input(type = InputType.Text) {
                value(code)
                placeholder("代码")
                onInput { code = it.value }
            }
            Input(type = InputType.Text) {
                value(chg)
                placeholder("涨跌幅")
                onInput { chg = it.value }
            }
            Button(attrs = {
                classes(AppStyles.secondaryButton)
                onClick {
                    val body = """{"code":"$code","chg":${chg.toDoubleOrNull() ?: 0.0}}"""
                    val options = js("({})")
                    options.method = "POST"
                    options.headers = js("""({"Content-Type": "application/json"})""")
                    options.body = body
                    window.fetch("http://localhost:8080/api/tick", options)
                        .then { response -> response.text() }
                        .then { text ->
                            runCatching {
                                val tick = json.decodeFromString<StockTick>(text.toString())
                                val now = (js("Date.now()") as Double).toLong()
                                val alert = AlertEvent(
                                    code = tick.code,
                                    name = tick.name,
                                    title = "${tick.code}${tick.name}手动测试",
                                    content = "手动测试通知，涨跌幅 ${tick.chg.fmt()}%，现价 ${tick.price.fmt()}，${formatTime(now)}",
                                    chg = tick.chg,
                                    price = tick.price,
                                    time = now
                                )
                                onManualAlert(alert)
                                status = if (runCatching { Notification.permission }.getOrNull() == "granted") {
                                    "已推送测试通知"
                                } else {
                                    "已生成测试事件，但浏览器通知未授权"
                                }
                            }.onFailure {
                                status = "推送失败: ${it.message ?: it.toString()}"
                            }
                            null
                        }
                        .catch { error ->
                            status = "推送失败: ${error.toString()}"
                            null
                        }
                }
            }) { Text("推送") }
            if (status.isNotBlank()) {
                Span({ classes(AppStyles.timeText) }) { Text(status) }
            }
        }
    }
}

@Composable
fun EastMoneyDebugPanel() {
    var code by remember { mutableStateOf("300059") }
    var result by remember { mutableStateOf("关闭或切换代理后，点击按钮查看 OkHttp 请求/响应日志。") }

    Div({ classes(AppStyles.manual) }) {
        Div {
            H2 { Text("东财接口测试") }
            P { Text("临时诊断服务端到 push2his.eastmoney.com 的真实网络链路。") }
        }
        Div({ classes(AppStyles.form) }) {
            Input(type = InputType.Text) {
                value(code)
                placeholder("代码")
                onInput { code = it.value }
            }
            Button(attrs = {
                classes(AppStyles.secondaryButton)
                onClick {
                    result = "测试中..."
                    fetchEastMoneyDebug(code, direct = false) { result = it }
                }
            }) { Text("测试东财") }
            Button(attrs = {
                classes(AppStyles.secondaryButton)
                onClick {
                    result = "直连测试中..."
                    fetchEastMoneyDebug(code, direct = true) { result = it }
                }
            }) { Text("强制直连") }
            Div({ classes(AppStyles.debugOutput) }) {
                Text(result)
            }
        }
    }
}

private fun fetchEastMoneyDebug(code: String, direct: Boolean, onResult: (String) -> Unit) {
    val url = "http://localhost:8080/api/debug/eastmoney/history?code=$code&direct=$direct"
    window.fetch(url)
        .then { response -> response.text() }
        .then { text ->
            onResult(debugLogFromResponse(text.toString()))
            null
        }
        .catch { error ->
            onResult("请求失败: ${error.toString()}")
            null
        }
}

private fun debugLogFromResponse(text: String): String {
    return runCatching {
        val payload = JSON.parse<dynamic>(text)
        val log = payload.okHttpLog?.toString().orEmpty()
        log.ifBlank { text }
    }.getOrElse {
        text
    }
}

private data class MonitorStock(
    val tick: StockTick,
    val source: String
)

private data class MonitorSource(
    val id: String,
    val name: String,
    val icon: String,
    val group: String,
    val url: () -> String,
    val parser: (dynamic) -> List<String>
)

private data class MonitorSourceLoadResult(
    val sourcesByCode: Map<String, String>,
    val message: String
)

private val monitorSources = listOf(
    MonitorSource(
        id = "em-popularity",
        name = "东方财富热榜",
        icon = "东",
        group = "hot",
        url = { "https://data.eastmoney.com/dataapi/xuangu/list?st=POPULARITY_RANK&sr=1&ps=300&p=1&sty=SECUCODE%2CSECURITY_CODE%2CSECURITY_NAME_ABBR%2CPOPULARITY_RANK&filter=(POPULARITY_RANK%3E0)(POPULARITY_RANK%3C%3D1000)&source=SELECT_SECURITIES&client=WEB" },
        parser = { payload ->
            dynamicArray(payload.result?.data ?: payload.data ?: payload.list).mapNotNull { item ->
                codeFromDynamic(item.SECURITY_CODE ?: item.SECUCODE ?: item.securityCode ?: item.secuCode ?: item.code)
            }
        }
    ),
    MonitorSource(
        id = "ths-hot",
        name = "同花顺热榜",
        icon = "同",
        group = "hot",
        url = { "https://dq.10jqka.com.cn/fuyao/hot_list_data/out/hot_list/v1/stock?stock_type=a&type=hour&list_type=normal" },
        parser = { payload -> dynamicArray(payload.data?.stock_list ?: payload.data?.list ?: payload.list ?: payload.data).mapNotNull { codeFromDynamic(it.code ?: it.stock_code ?: it.stockCode) } }
    ),
    MonitorSource(
        id = "dzh-hot",
        name = "大智慧热榜",
        icon = "智",
        group = "hot",
        url = { "https://imsearch.dzh.com.cn/stock/top?size=300&type=0&time=h" },
        parser = { payload ->
            dynamicArray(payload.result ?: payload.data ?: payload.list).flatMap { item ->
                dynamicObjectKeys(item).mapNotNull { key -> codeFromDynamic(key) }
            }
        }
    ),
    MonitorSource(
        id = "cls-hot",
        name = "财联社热榜",
        icon = "财",
        group = "hot",
        url = { "https://api3.cls.cn/v1/hot_stock?app=cailianpress&os=android&sv=850&sign=e4d8f886f269874b1578fec645b258fa" },
        parser = { payload ->
            dynamicArray(payload.data ?: payload.list).mapNotNull { item ->
                codeFromDynamic(item.stock?.StockID ?: item.title ?: item.code ?: item.stock_code ?: item.secu_code)
            }
        }
    ),
    MonitorSource(
        id = "tgb-hot",
        name = "淘股吧热榜",
        icon = "淘",
        group = "hot",
        url = { "https://www.taoguba.com.cn/new/nrnt/getNoticeStock?type=D" },
        parser = { payload ->
            dynamicArray(payload.dto ?: payload.data ?: payload.list ?: payload.result).mapNotNull { item ->
                codeFromDynamic(item.fullCode ?: item.implied?.stockCode ?: item.code ?: item.stockCode ?: item.stock_code)
            }
        }
    ),
    MonitorSource(
        id = "em-lhb",
        name = "东方财富龙虎榜",
        icon = "龙",
        group = "lhb",
        url = {
            val date = todayDate()
            "https://datacenter-web.eastmoney.com/api/data/v1/get?sortColumns=SECURITY_CODE%2CTRADE_DATE&sortTypes=1%2C-1&pageSize=100&pageNumber=1&reportName=RPT_DAILYBILLBOARD_DETAILSNEW&columns=SECURITY_CODE%2CSECUCODE%2CSECURITY_NAME_ABBR%2CTRADE_DATE%2CEXPLAIN%2CCLOSE_PRICE%2CCHANGE_RATE%2CBILLBOARD_NET_AMT%2CBILLBOARD_BUY_AMT%2CBILLBOARD_SELL_AMT%2CBILLBOARD_DEAL_AMT%2CACCUM_AMOUNT%2CDEAL_NET_RATIO%2CDEAL_AMOUNT_RATIO%2CTURNOVERRATE%2CFREE_MARKET_CAP%2CEXPLANATION%2CD1_CLOSE_ADJCHRATE%2CD2_CLOSE_ADJCHRATE%2CD5_CLOSE_ADJCHRATE%2CD10_CLOSE_ADJCHRATE%2CSECURITY_TYPE_CODE&source=WEB&client=WEB&filter=(TRADE_DATE%3C%3D%27$date%27)(TRADE_DATE%3E%3D%27$date%27)"
        },
        parser = { payload -> dynamicArray(payload.result?.data ?: payload.data ?: payload.list).mapNotNull { codeFromDynamic(it.SECURITY_CODE ?: it.securityCode ?: it.code) } }
    ),
    MonitorSource(
        id = "ths-lhb",
        name = "同花顺龙虎榜",
        icon = "龙",
        group = "lhb",
        url = {
            val date = todayDate()
            "https://data.10jqka.com.cn/dataapi/transaction/stock/v1/list?order_field=hot_rank&order_type=asc&date=$date&filter=&page=1&size=50&module=all&order_null_greater=1"
        },
        parser = { payload ->
            dynamicArray(payload.data?.items ?: payload.data?.list ?: payload.data ?: payload.list).mapNotNull { item ->
                codeFromDynamic(item.stock_code ?: item.stockCode ?: item.code ?: item.market_code)
            }
        }
    )
)

private suspend fun loadMonitorSourceMap(selectedSourceIds: List<String>, customCodes: String): MonitorSourceLoadResult {
    val sourcesByCode = linkedMapOf<String, MutableList<String>>()
    val errors = mutableListOf<String>()
    selectedSourceIds.mapNotNull { id -> monitorSources.firstOrNull { it.id == id } }.forEach { source ->
        runCatching {
            val text = fetchMonitorSourceText(source)
            val payload = JSON.parse<dynamic>(text)
            val codes = source.parser(payload).distinct()
            codes.forEach { code -> sourcesByCode.getOrPut(code) { mutableListOf() }.add(source.name) }
            codes.size
        }.onFailure {
            errors += "${source.name}失败"
        }
    }
    customCodes.split(Regex("\\s+"))
        .mapNotNull { normalizeStockCode(it) }
        .distinct()
        .forEach { code -> sourcesByCode.getOrPut(code) { mutableListOf() }.add("自定义") }
    val labelMap = sourcesByCode.mapValues { (_, names) -> names.distinct().joinToString(" / ") }
    val message = buildString {
        append("已选 ${selectedSourceIds.size} 个来源，命中 ${labelMap.size} 只")
        if (errors.isNotEmpty()) append("；").append(errors.joinToString("、"))
    }
    return MonitorSourceLoadResult(labelMap, message)
}

private suspend fun fetchMonitorSourceText(source: MonitorSource): String {
    return runCatching {
        val response = window.fetch(source.url()).await()
        val text = response.text().await()
        if (!response.ok) kotlin.error("HTTP ${response.status.toInt()}")
        if (!text.trimStart().startsWith("{") && !text.trimStart().startsWith("[")) {
            kotlin.error("non json response")
        }
        text
    }.getOrElse {
        val response = window.fetch("http://localhost:8080/api/monitor/source/${source.id}").await()
        val text = response.text().await()
        if (!response.ok) kotlin.error("proxy HTTP ${response.status.toInt()}: $text")
        text
    }
}

private fun loadMonitorSourceMapAsync(
    selectedSourceIds: List<String>,
    customCodes: String,
    onResult: (MonitorSourceLoadResult) -> Unit
) {
    kotlinx.coroutines.GlobalScope.promise {
        loadMonitorSourceMap(selectedSourceIds, customCodes)
    }.then {
        onResult(it)
        null
    }
}

private fun dynamicArray(value: dynamic): List<dynamic> {
    if (value == null) return emptyList()
    val length = value.length as? Int ?: return emptyList()
    return (0 until length).map { index -> value[index] }
}

private fun dynamicObjectKeys(value: dynamic): List<String> {
    if (value == null) return emptyList()
    val keys = js("Object.keys(value)")
    val length = keys.length as? Int ?: return emptyList()
    return (0 until length).map { index -> keys[index].toString() }
}

private fun codeFromDynamic(value: dynamic): String? {
    val text = value?.toString() ?: return null
    return normalizeStockCode(text)
}

private fun normalizeStockCode(value: String): String? {
    val digits = Regex("\\d{6}").find(value)?.value ?: return null
    return digits
}

private fun sourceIconClass(sourceName: String): String {
    return when {
        sourceName.contains("东方财富") -> AppStyles.eastMoneyIcon
        sourceName.contains("同花顺") -> AppStyles.thsIcon
        sourceName.contains("大智慧") -> AppStyles.dzhIcon
        sourceName.contains("财联社") -> AppStyles.clsIcon
        sourceName.contains("淘股吧") -> AppStyles.tgbIcon
        sourceName == "自定义" -> AppStyles.customIcon
        else -> AppStyles.sourceIcon
    }
}

private fun formatTime(time: Long): String {
    val date = js("new Date(time)")
    return date.toLocaleTimeString("zh-CN") as String
}

private fun formatSeconds(time: Long): String {
    val date = js("new Date(time * 1000)")
    return date.toLocaleTimeString("zh-CN") as String
}

private fun todayDate(): String {
    val date = js("new Date()")
    val year = date.getFullYear()
    val month = (date.getMonth() + 1).toString().padStart(2, '0')
    val day = date.getDate().toString().padStart(2, '0')
    return "$year-$month-$day"
}

private fun isAfterKplLiveClose(): Boolean {
    val date = js("new Date()")
    val hour = date.getHours() as Int
    val minute = date.getMinutes() as Int
    return hour > 15 || (hour == 15 && minute >= 0)
}

private fun isTradingTime(): Boolean {
    val date = js("new Date()")
    val day = date.getDay() as Int
    if (day == 0 || day == 6) return false
    val hour = date.getHours() as Int
    val minute = date.getMinutes() as Int
    val minutes = hour * 60 + minute
    return minutes in (9 * 60 + 25)..(11 * 60 + 30) ||
        minutes in (13 * 60)..(15 * 60)
}

private fun Double.fmt(): String = asDynamic().toFixed(2) as String

object AppStyles : StyleSheet() {
    val page by style {
        minHeight(100.percent)
        background("#f4f6f8")
        color(Color("#20242a"))
        fontFamily("Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif")
    }

    val shell by style {
        maxWidth(1180.px)
        property("margin", "0 auto")
        padding(24.px)
        boxSizing("border-box")
    }

    val header by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        gap(16.px)
        margin(0.px, 0.px, 20.px, 0.px)
    }

    val actions by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(10.px)
    }

    val grid by style {
        display(DisplayStyle.Grid)
        gridTemplateColumns("repeat(auto-fit, minmax(320px, 1fr))")
        gap(16.px)
    }

    val panel by style {
        background("#ffffff")
        borderRadius(8.px)
        border(1.px, LineStyle.Solid, Color("#dfe5ec"))
        padding(16.px)
        boxSizing("border-box")
    }

    val marketPanel by style {
        property("overflow-x", "auto")
    }

    val sectionTitle by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        margin(0.px, 0.px, 12.px, 0.px)
    }

    val table by style {
        width(100.percent)
        property("border-collapse", "collapse")
        fontSize(14.px)
    }

    val pagination by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        gap(10.px)
        margin(12.px, 0.px, 0.px, 0.px)
        color(Color("#66707c"))
        fontSize(13.px)
        property("flex-wrap", "wrap")
    }

    val pageButtons by style {
        display(DisplayStyle.Flex)
        gap(8.px)
    }

    val monitorActions by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.FlexEnd)
        gap(10.px)
        property("flex-wrap", "wrap")
    }

    val monitorIntervalControl by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(6.px)
        color(Color("#66707c"))
        fontSize(13.px)
    }

    val upText by style {
        color(Color("#c43c3c"))
        fontWeight("700")
    }

    val downText by style {
        color(Color("#12834a"))
        fontWeight("700")
    }

    val okPill by style {
        pill("#e2f5ea", "#127540")
    }

    val warnPill by style {
        pill("#fff3dc", "#9a6200")
    }

    val primaryButton by style {
        button("#20242a", "#ffffff")
    }

    val secondaryButton by style {
        button("#eceff3", "#20242a")
    }

    val alertList by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(10.px)
    }

    val alertItem by style {
        border(1.px, LineStyle.Solid, Color("#e3e7ec"))
        borderRadius(8.px)
        padding(12.px)
        background("#fbfcfd")
    }

    val alertTop by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        gap(8.px)
    }

    val alertTitle by style {
        fontWeight("700")
    }

    val timeText by style {
        color(Color("#737d89"))
        fontSize(12.px)
    }

    val emptyState by style {
        height(180.px)
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        color(Color("#7b8590"))
    }

    val manual by style {
        margin(16.px, 0.px, 0.px, 0.px)
        background("#ffffff")
        borderRadius(8.px)
        border(1.px, LineStyle.Solid, Color("#dfe5ec"))
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        gap(16.px)
        alignItems(AlignItems.Center)
        property("flex-wrap", "wrap")
    }

    val sourcePanel by style {
        margin(0.px, 0.px, 16.px, 0.px)
        background("#ffffff")
        borderRadius(8.px)
        border(1.px, LineStyle.Solid, Color("#dfe5ec"))
        padding(16.px)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(12.px)
        boxSizing("border-box")
    }

    val sourceHeader by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        gap(12.px)
        property("flex-wrap", "wrap")
    }

    val sourceButtons by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(8.px)
    }

    val sourceGroupRow by style {
        display(DisplayStyle.Grid)
        property("grid-template-columns", "64px 1fr")
        alignItems(AlignItems.Center)
        gap(10.px)
    }

    val sourceGroupTitle by style {
        color(Color("#66707c"))
        fontSize(13.px)
        fontWeight("700")
    }

    val sourceGroupButtons by style {
        display(DisplayStyle.Flex)
        gap(8.px)
        property("flex-wrap", "wrap")
    }

    val sourceLegend by style {
        display(DisplayStyle.Flex)
        gap(6.px)
        property("flex-wrap", "wrap")
    }

    val sourceButtonOn by style {
        sourceButtonBase()
        background("#20242a")
        color(Color.white)
        border(1.px, LineStyle.Solid, Color("#20242a"))
    }

    val sourceButtonOff by style {
        sourceButtonBase()
        background("#ffffff")
        color(Color("#20242a"))
        border(1.px, LineStyle.Solid, Color("#cfd6df"))
    }

    val sourceIconSet by style {
        display(DisplayStyle.Flex)
        gap(4.px)
        alignItems(AlignItems.Center)
        property("flex-wrap", "wrap")
    }

    val sourceIcon by style {
        iconBase("#edf2f7", "#344054")
    }

    val eastMoneyIcon by style {
        iconBase("#e31b23", "#ffffff")
    }

    val thsIcon by style {
        iconBase("#f04438", "#ffffff")
    }

    val dzhIcon by style {
        iconBase("#0b63ce", "#ffffff")
    }

    val clsIcon by style {
        iconBase("#d92d20", "#ffffff")
    }

    val tgbIcon by style {
        iconBase("#f79009", "#ffffff")
    }

    val customIcon by style {
        iconBase("#475467", "#ffffff")
    }

    val customCodesRow by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(10.px)
        property("flex-wrap", "wrap")
    }

    val form by style {
        display(DisplayStyle.Flex)
        gap(10.px)
        alignItems(AlignItems.Center)
        property("flex-wrap", "wrap")
    }

    val debugOutput by style {
        width(100.percent)
        background("#f7f9fb")
        border(1.px, LineStyle.Solid, Color("#dfe5ec"))
        borderRadius(8.px)
        padding(12.px)
        color(Color("#334155"))
        fontSize(13.px)
        property("white-space", "pre-wrap")
        property("word-break", "break-word")
        boxSizing("border-box")
    }

    val replayLayout by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(16.px)
    }

    val replayList by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(12.px)
    }

    val replayCard by style {
        border(1.px, LineStyle.Solid, Color("#e3e7ec"))
        borderRadius(8.px)
        padding(14.px)
        background("#fbfcfd")
    }

    val replayMeta by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        gap(10.px)
        margin(0.px, 0.px, 10.px, 0.px)
        property("flex-wrap", "wrap")
    }

    val platePill by style {
        background("#eef4ff")
        color(Color("#1d4ed8"))
        borderRadius(999.px)
        padding(5.px, 9.px)
        fontSize(12.px)
        fontWeight("700")
    }

    val replayBody by style {
        display(DisplayStyle.Flex)
        gap(12.px)
        alignItems(AlignItems.FlexStart)
    }

    val avatar by style {
        width(36.px)
        height(36.px)
        borderRadius(8.px)
        property("object-fit", "cover")
        property("flex", "0 0 auto")
    }

    val replayContent by style {
        flex(1)
        property("min-width", "0")
    }

    val stockChips by style {
        display(DisplayStyle.Flex)
        gap(8.px)
        margin(10.px, 0.px, 0.px, 0.px)
        property("flex-wrap", "wrap")
    }

    val stockChipUp by style {
        stockChip("#fff1f1", "#c43c3c")
    }

    val stockChipDown by style {
        stockChip("#ecfdf3", "#12834a")
    }

    val errorBox by style {
        background("#fff3f3")
        color(Color("#b42318"))
        border(1.px, LineStyle.Solid, Color("#ffd6d6"))
        borderRadius(8.px)
        padding(12.px)
        margin(0.px, 0.px, 12.px, 0.px)
        fontSize(13.px)
    }

    val webFrame by style {
        width(100.percent)
        height(760.px)
        border(1.px, LineStyle.Solid, Color("#dfe5ec"))
        borderRadius(8.px)
        background("#ffffff")
    }

    init {
        "body" style {
            margin(0.px)
        }
        "h1" style {
            margin(0.px)
            fontSize(28.px)
        }
        "h2" style {
            margin(0.px)
            fontSize(17.px)
        }
        "p" style {
            margin(6.px, 0.px, 0.px, 0.px)
            color(Color("#66707c"))
        }
        "th, td" style {
            padding(12.px, 10.px)
            border(0.px)
            property("border-bottom", "1px solid #edf0f4")
            property("text-align", "left")
            property("white-space", "nowrap")
        }
        "th" style {
            color(Color("#66707c"))
            fontWeight("700")
            background("#f7f9fb")
        }
        "input" style {
            padding(10.px, 11.px)
            border(1.px, LineStyle.Solid, Color("#cfd6df"))
            borderRadius(8.px)
            fontSize(14.px)
            width(110.px)
            boxSizing("border-box")
        }
        ".$monitorIntervalControl input" style {
            width(58.px)
            height(32.px)
            padding(6.px)
        }
    }

    private fun StyleScope.pill(background: String, foreground: String) {
        background(background)
        color(Color(foreground))
        borderRadius(999.px)
        padding(8.px, 10.px)
        fontSize(13.px)
        fontWeight("700")
    }

    private fun StyleScope.button(background: String, foreground: String) {
        background(background)
        color(Color(foreground))
        border(0.px)
        borderRadius(8.px)
        padding(10.px, 13.px)
        fontSize(14.px)
        fontWeight("700")
        property("cursor", "pointer")
    }

    private fun StyleScope.stockChip(background: String, foreground: String) {
        background(background)
        color(Color(foreground))
        borderRadius(6.px)
        padding(5.px, 8.px)
        fontSize(12.px)
        fontWeight("700")
    }

    private fun StyleScope.sourceButtonBase() {
        borderRadius(8.px)
        padding(7.px, 10.px)
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(6.px)
        fontSize(13.px)
        fontWeight("700")
        property("cursor", "pointer")
    }

    private fun StyleScope.iconBase(backgroundColor: String, foregroundColor: String) {
        property("display", "inline-flex")
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        width(20.px)
        height(20.px)
        borderRadius(6.px)
        background(backgroundColor)
        color(Color(foregroundColor))
        fontSize(11.px)
        fontWeight("800")
        property("line-height", "20px")
        property("flex", "0 0 auto")
    }
}
