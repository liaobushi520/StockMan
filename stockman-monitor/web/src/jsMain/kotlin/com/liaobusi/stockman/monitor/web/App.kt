package com.liaobusi.stockman.monitor.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.InputType
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
    var connected by remember { mutableStateOf(false) }
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
                alerts.add(0, alert)
                if (alerts.size > 30) alerts.removeLast()
                showNotification(alert)
            }
        )
    }

    Div({ classes(AppStyles.page) }) {
        Div({ classes(AppStyles.shell) }) {
            Header(
                connected = connected,
                notificationState = notificationState,
                onRequestNotification = {
                    requestNotificationPermission { notificationState = it }
                }
            )
            Div({ classes(AppStyles.grid) }) {
                Div({ classes(AppStyles.panel, AppStyles.marketPanel) }) {
                    SectionTitle("实时监控", "${stocks.size} 只标的")
                    StockTable(stocks)
                }
                Div({ classes(AppStyles.panel) }) {
                    SectionTitle("异动通知", "${alerts.size} 条")
                    AlertList(alerts)
                }
            }
            ManualTickPanel()
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
    onRequestNotification: () -> Unit
) {
    Div({ classes(AppStyles.header) }) {
        Div {
            H1 { Text("StockMan Monitor") }
            P { Text("本地行情监控 · Compose Web") }
        }
        Div({ classes(AppStyles.actions) }) {
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
fun SectionTitle(title: String, meta: String) {
    Div({ classes(AppStyles.sectionTitle) }) {
        H2 { Text(title) }
        Span { Text(meta) }
    }
}

@Composable
fun StockTable(stocks: List<StockTick>) {
    Table({ classes(AppStyles.table) }) {
        Thead {
            Tr {
                Th { Text("代码") }
                Th { Text("名称") }
                Th { Text("现价") }
                Th { Text("涨跌幅") }
                Th { Text("涨停") }
                Th { Text("跌停") }
            }
        }
        Tbody {
            stocks.forEach { stock ->
                Tr {
                    Td { Text(stock.code) }
                    Td { Text(stock.name) }
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
fun ManualTickPanel() {
    var code by remember { mutableStateOf("600519") }
    var chg by remember { mutableStateOf("4.2") }

    Div({ classes(AppStyles.manual) }) {
        Div {
            H2 { Text("手动测试") }
            P { Text("推一个涨跌幅到本地服务，方便立刻验证通知链路。") }
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
                }
            }) { Text("推送") }
        }
    }
}

private fun formatTime(time: Long): String {
    val date = js("new Date(time)")
    return date.toLocaleTimeString("zh-CN") as String
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

    val form by style {
        display(DisplayStyle.Flex)
        gap(10.px)
        alignItems(AlignItems.Center)
        property("flex-wrap", "wrap")
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
}
