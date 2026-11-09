package com.bugu.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bugu.ui.RingActivity

/**
 * 闹钟广播(E8-S4):到点启动全屏响铃 Activity(通知权限关闭时同样全屏,FR-07 降级);
 * 开机完成按持久化的 incoming 列表重排精确闹钟(进程被杀/重启恢复)。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val stores = PrefsStores(context)
                val planned = ReminderPlanner.plan(
                    incoming = stores.lastIncoming.load(),
                    nowEpochMillis = System.currentTimeMillis(),
                )
                AlarmScheduler(context, stores.scheduledAlarms).reschedule(planned)
            }

            AlarmScheduler.ACTION_FIRE -> {
                val fireId = intent.getLongExtra(AlarmScheduler.EXTRA_FIRE_ID, -1)
                val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1)
                val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: ""
                val fireAt = intent.getStringExtra(AlarmScheduler.EXTRA_FIRE_AT) ?: ""
                val idempotencyKey = intent.getStringExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY) ?: ""
                val voiceEnabled = intent.getBooleanExtra(AlarmScheduler.EXTRA_VOICE, false)
                val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: "ONCE"
                val selfReminder = intent.getBooleanExtra(AlarmScheduler.EXTRA_SELF, false)
                val creatorId = intent.getLongExtra(AlarmScheduler.EXTRA_CREATOR_ID, 0L)

                // 先发 CATEGORY_ALARM 高优先级通知:即使 startActivity 被 BAL_BLOCK,
                // setFullScreenIntent 仍可拉起 RingActivity;全屏不可用时至少可点通知进入。
                AlarmNotifications.show(
                    context = context,
                    fireId = fireId,
                    reminderId = reminderId,
                    title = title,
                    fireAt = fireAt,
                    idempotencyKey = idempotencyKey,
                    voiceEnabled = voiceEnabled,
                    kind = kind,
                    selfReminder = selfReminder,
                    creatorId = creatorId,
                )
                val ring = Intent(context, RingActivity::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_FIRE_ID, fireId)
                    putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
                    putExtra(AlarmScheduler.EXTRA_TITLE, title)
                    putExtra(AlarmScheduler.EXTRA_FIRE_AT, fireAt)
                    putExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY, idempotencyKey)
                    putExtra(AlarmScheduler.EXTRA_VOICE, voiceEnabled)
                    putExtra(AlarmScheduler.EXTRA_KIND, kind)
                    putExtra(AlarmScheduler.EXTRA_SELF, selfReminder)
                    putExtra(AlarmScheduler.EXTRA_CREATOR_ID, creatorId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                // 后台直接启动受系统限制;只尝试一次,不循环重试,失败交给通知兜底。
                runCatching { context.startActivity(ring) }
                    .onFailure { Log.w("BuguAlarm", "startActivity blocked for fire=$fireId; notification fallback used", it) }
            }
        }
    }
}
