package com.bugu.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
                val ring = Intent(context, RingActivity::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_FIRE_ID, intent.getLongExtra(AlarmScheduler.EXTRA_FIRE_ID, -1))
                    putExtra(AlarmScheduler.EXTRA_REMINDER_ID, intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1))
                    putExtra(AlarmScheduler.EXTRA_TITLE, intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "")
                    putExtra(AlarmScheduler.EXTRA_FIRE_AT, intent.getStringExtra(AlarmScheduler.EXTRA_FIRE_AT) ?: "")
                    putExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY, intent.getStringExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY) ?: "")
                    putExtra(AlarmScheduler.EXTRA_VOICE, intent.getBooleanExtra(AlarmScheduler.EXTRA_VOICE, false))
                    putExtra(AlarmScheduler.EXTRA_KIND, intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: "ONCE")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(ring)
            }
        }
    }
}
