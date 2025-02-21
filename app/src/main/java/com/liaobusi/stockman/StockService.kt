package com.liaobusi.stockman


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class StockService : Service() {

    companion object {
        const val CHANNEL_ID = "my_service_channel"
    }


    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        // 创建通知
        val notification = buildNotification()
        // 启动前台服务
        startForeground(1, notification)

        Injector.autoRefreshPopularityRanking()
        Injector.startAutoRefresh()
        Injector.startTracking()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "股票超人",
            "异动",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Channel description"

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // 构建通知
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // 设置通知小图标
            .setContentTitle("股票超人") // 设置通知标题
            .setContentText("持续跟踪异动...") // 设置通知内容
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 设置通知优先级
            // 设置点击通知后的行为，例如打开Activity
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, HomeActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


}