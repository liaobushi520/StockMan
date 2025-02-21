package com.liaobusi.stockman

import android.view.View
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

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

fun routeRemainDisStr(routeRemainDis: Int): String {
    if (routeRemainDis == null) {
        return ""
    }
    val km = (routeRemainDis / 1000)
    val m = (routeRemainDis - km * 1000)

    if (km > 999) {
        return "$km km"
    }

    if (km in 10..999) {
        return "$km km"
    }

    if (km in 1..9) {
        return String.format("%.1f", (routeRemainDis / 100f).toInt() / 10f) + " km"
    }

    if (routeRemainDis in 100..999) {
        return "${(routeRemainDis / 50) * 50} m"
    }

    if (routeRemainDis in 0..99) {
        return "${(routeRemainDis / 10) * 10} m"
    }

    val sb = StringBuilder()

    if (km > 0) {
        sb.append("$km km")
    } else {
        if (m > 0) {
            sb.append("$m m")
        }
    }


    return sb.toString()
}

fun routeRemainTimeStr(routeRemainTime: Int): String {
    if (routeRemainTime == null) {
        return ""
    }

    if (routeRemainTime in 0..59) {
        return "1 min"
    }

    if (routeRemainTime in 60..3599) {
        if (routeRemainTime % 60 <= 30) {
            return "${floor(routeRemainTime / 60f).toInt()} min"
        }
        return "${ceil(routeRemainTime / 60f).toInt()} min"
    }

    if (routeRemainTime > 359940) {
        return ">100 h"
    }

    val hour = (routeRemainTime / 3600)
    val min = (routeRemainTime - hour * 3600)

    val sb = StringBuilder()

    if (hour > 0) {
        sb.append("$hour h")
    }

    if (min > 0) {
        if (sb.isNotEmpty()) sb.append(" ")
        if (min % 60 <= 30) {
            sb.append("${floor(min / 60f).toInt()} min")
        }else{
            sb.append("${ceil(min / 60f).toInt()} min")
        }

    }
    return sb.toString()
}