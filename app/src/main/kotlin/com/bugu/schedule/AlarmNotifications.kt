package com.bugu.schedule

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.bugu.R
import com.bugu.ui.RingActivity

/**
 * 闹钟通知兜底:startActivity 被 Android 后台启动限制拦截时,
 * 依赖 CATEGORY_ALARM + setFullScreenIntent 的高优先级通知拉起 RingActivity;
 * 全屏仍不可用时,用户至少能点击通知进入响铃页。
 */
object AlarmNotifications {

    const val CHANNEL_ID = "bugu_alarm_ring_v2"
    private const val LEGACY_CHANNEL_ID = "bugu_alarm_ring"
    private const val CHANNEL_NAME = "布谷闹钟"
    private const val NOTIFICATION_ID_BASE = 100_000
    private const val TAG = "BuguAlarmNotification"

    /**
     * 应用启动时创建;渠道属性(声音/震动/重要性)创建后不可改。
     * v2 渠道本身静音:声音由 RingActivity 用 alarm_notice + alarm_loop 接管,
     * 避免系统闹铃与页面循环叠成双重响。旧渠道创建后无法改属性,只能删除后换新 id。
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "到点全屏响铃与确认通知;声音由响铃页统一播放"
            setSound(null, null)
            // 震动由 RingActivity 统一负责,渠道震动关闭,避免两份震动叠加。
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        // 迁移收尾:旧渠道声音/震动设置不可改,删除后不再使用。
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    /** 返回 false 表示通知权限缺失/系统拒绝,由调用方决定是否只保留 startActivity 路径。 */
    fun show(
        context: Context,
        fireId: Long,
        reminderId: Long,
        title: String,
        fireAt: String,
        idempotencyKey: String,
        voiceEnabled: Boolean,
        kind: String,
        selfReminder: Boolean,
    ): Boolean {
        if (!canPost(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS unavailable; full-screen fallback skipped fire=$fireId")
            return false
        }
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val ringIntent = Intent(context, RingActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_FIRE_ID, fireId)
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmScheduler.EXTRA_TITLE, title)
            putExtra(AlarmScheduler.EXTRA_FIRE_AT, fireAt)
            putExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY, idempotencyKey)
            putExtra(AlarmScheduler.EXTRA_VOICE, voiceEnabled)
            putExtra(AlarmScheduler.EXTRA_KIND, kind)
            putExtra(AlarmScheduler.EXTRA_SELF, selfReminder)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val notificationId = notificationId(fireId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val displayTitle = title.ifBlank { "到点了" }
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("布谷来叫你啦")
            .setContentText(displayTitle)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        return try {
            manager.notify(notificationId, builder.build())
            Log.i(TAG, "alarm notification posted fire=$fireId id=$notificationId")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "notify failed without permission fire=$fireId", e)
            false
        }
    }

    fun cancel(context: Context, fireId: Long) {
        if (fireId < 0) return
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(fireId))
    }

    fun cancelAll(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    private fun notificationId(fireId: Long): Int =
        NOTIFICATION_ID_BASE + (fireId.hashCode() and 0xFFFFF)
}
