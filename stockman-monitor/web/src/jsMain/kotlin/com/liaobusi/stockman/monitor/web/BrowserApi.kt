package com.liaobusi.stockman.monitor.web

import kotlinx.browser.window

external class Notification(title: String, options: dynamic = definedExternally) {
    companion object {
        val permission: String
        fun requestPermission(callback: (String) -> Unit = definedExternally): dynamic
    }
}

fun requestNotificationPermission(onResult: (String) -> Unit) {
    runCatching {
        Notification.requestPermission { permission ->
            onResult(permission)
        }
    }.onFailure {
        onResult("unsupported")
    }
}

fun showNotification(alert: AlertEvent) {
    if (runCatching { Notification.permission }.getOrNull() != "granted") return

    val options = js("({})")
    options.body = alert.content
    options.tag = "${alert.code}-${alert.time}"
    options.renotify = true
    Notification(alert.title, options)

    runCatching {
        val navigator = window.navigator.asDynamic()
        navigator.vibrate?.invoke(arrayOf(120, 80, 120))
    }
}
