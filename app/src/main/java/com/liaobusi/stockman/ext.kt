package com.liaobusi.stockman

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.RequiresPermission
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

fun <T> List<T>.split(groupCount: Int = 3): List<List<T>> {
    val result = mutableListOf<List<T>>()
    var start = 0
    val groupSize = (this.size / groupCount)
    for (i in 0 until groupCount) {
        val end = if (i == groupCount - 1) {
            this.size
        } else {
            start + groupSize
        }
        val l = this.subList(start, end)
        result.add(l)
        start = end

    }

    return result.toList()
}

fun broadcastReceiverFlow(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    intentFilter: IntentFilter
): Flow<Intent?> {
    return callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                launch(Dispatchers.Main) {
                    Log.i("broadcastReceiverFlow", "接收intent${intent}")
                    send(intent)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
                Log.i("broadcastReceiverFlow", "注册广播")
                launch(Dispatchers.IO) {
                    context.registerReceiver(receiver, intentFilter)
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                Log.i("broadcastReceiverFlow", "解绑广播")
                close()
            }
        })
        awaitClose { launch(Dispatchers.IO) { context.unregisterReceiver(receiver) } }
    }

}

suspend fun <T, R> List<T>.compute(
    count: Int = 6,
    f: suspend (s: T) -> R?
): List<R> {
    val jobList = mutableListOf<Deferred<List<R>>>()
    this.split(count).forEach {
        val job = GlobalScope.async {
            handle(it, f)
        }
        jobList.add(job)
    }
    return jobList.awaitAll().flatten().toList()
}

suspend fun <T, R> handle(input: List<T>, f: suspend (s: T) -> R?): List<R> {
    val list = mutableListOf<R>()
    input.forEach inputList@{
        val r = f(it)
        if (r != null) {
            list.add(r)
        }
    }
    return list
}


fun Date.before(before: Int): Int {
    val sdf = SimpleDateFormat("yyyyMMdd");

    val c = Calendar.getInstance()
    c.time = this
    c.add(Calendar.DATE, -before);
    val time = c.time;
    val preDay = sdf.format(time);
    return preDay.toInt()
}


fun today(): Int {
    return SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())).toInt()
}

fun View.multiClick(count: Int, callback: () -> Unit) {

    this.setTag(R.id.view_tag_count, 0)

    var job: Job? = null
    this.setOnClickListener {
        var c = this.getTag(R.id.view_tag_count) as Int
        job?.cancel()
        c += 1
        if (c == count) {
            this.setTag(R.id.view_tag_count, 0)
            callback()
        } else {
            this.setTag(R.id.view_tag_count, c)
            job = GlobalScope.launch {
                delay(300)
                setTag(R.id.view_tag_count, 0)
            }
        }


    }

}


fun String.removeSurroundingWhenExist(prefix: CharSequence, suffix: CharSequence): String {
    return if (startsWith(prefix)) {
        if (endsWith(suffix)) {
            substring(prefix.length, length - suffix.length)
        } else {
            substring(prefix.length, length)
        }
    } else {
        if (endsWith(suffix)) {
            substring(0, length - suffix.length)
        } else {
            this
        }
    }
}


@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun getNetworkType(context: Context): NetworkType {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    if (cm == null) {
        return NetworkType.UNKNOWN
    }

    // 处理Android 6.0（API 23）及以上版本
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = cm.getActiveNetwork()
        if (network == null) {
            return NetworkType.UNKNOWN
        }
        val nc = cm.getNetworkCapabilities(network)
        if (nc == null) {
            return NetworkType.UNKNOWN
        }

        // 检测传输类型
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return NetworkType.WIFI
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return NetworkType.CELLULAR
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return NetworkType.ETHERNET
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return NetworkType.VPN
        } else {
            return NetworkType.UNKNOWN
        }
    } else {
        // 兼容旧版本（API 23以下）
        val activeNetwork = cm.getActiveNetworkInfo()
        if (activeNetwork == null || !activeNetwork.isConnected()) {
            return NetworkType.UNKNOWN
        }
        val type = activeNetwork.getType()
        if (type == ConnectivityManager.TYPE_WIFI) {
            return NetworkType.WIFI
        } else if (type == ConnectivityManager.TYPE_MOBILE) {
            return NetworkType.CELLULAR
        } else if (type == ConnectivityManager.TYPE_ETHERNET) {
            return NetworkType.ETHERNET
        } else {
            return NetworkType.UNKNOWN
        }
    }
}

enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    UNKNOWN
}






