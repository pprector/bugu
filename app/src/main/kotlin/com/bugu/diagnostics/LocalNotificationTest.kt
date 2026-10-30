package com.bugu.diagnostics

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bugu.MainActivity
import com.bugu.R

object LocalNotificationTest {
    const val CHANNEL = "bugu_local_test"
    private const val ID = 190710

    fun prepare(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "本地测试提醒", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun schedule(context: Context) {
        prepare(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        check(notifications.areNotificationsEnabled()) { "请先开启通知权限" }
        check(notifications.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE) { "请先开启本地测试提醒通知渠道" }
        val alarms = context.getSystemService(AlarmManager::class.java)
        check(Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) { "请先开启精确闹钟权限，再进行5秒测试" }
        val operation = PendingIntent.getBroadcast(context, ID, Intent(context, LocalTestReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, operation)
    }

    fun show(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (!notifications.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify("local_test", ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("布谷 · 本地测试提醒")
            .setContentText("这是本机通知测试，不代表服务器推送已送达。")
            .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(60000).build())
    }
}

class LocalTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LocalNotificationTest.show(context)
    }
}
