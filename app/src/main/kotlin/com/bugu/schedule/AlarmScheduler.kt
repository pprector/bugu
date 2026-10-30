package com.bugu.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 已注册闹钟的 fireId 清单:全量重排前逐一 cancel(PendingIntent 按 requestCode+意图匹配)。 */
interface ScheduledAlarmStore {
    fun load(): List<Long>
    fun save(fireIds: List<Long>)
}

/**
 * AlarmManager 精确闹钟调度(E8-S4 / FR-07):
 * 闹钟类目申请 USE_EXACT_ALARM(Android 14 对闹钟类应用默认授予);系统拒绝时回落 setWindow 并引导。
 * 全量重排策略:cancel 已注册清单 → set 计划器输出 → 保存新清单。
 */
class AlarmScheduler(
    private val context: Context,
    private val scheduledStore: ScheduledAlarmStore,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** 精确闹钟可用性(ROM 拒绝时回落近似触发)。 */
    fun exactAlarmUsable(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun reschedule(planned: List<PlannedAlarm>) {
        scheduledStore.load().forEach { fireId ->
            alarmManager.cancel(firePendingIntent(fireId, null))
        }
        planned.forEach { alarm ->
            val operation = firePendingIntent(alarm.fireId, alarm)
            if (exactAlarmUsable()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, operation)
            } else {
                // FR-07 降级:近似时刻触发,可能偏移;引导见 ExactAlarmGuideActivity
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, 60_000L, operation)
            }
        }
        scheduledStore.save(planned.map { it.fireId })
    }

    /** filterEquals 忽略 extras:取消路径传 null 即可命中同一 PendingIntent。 */
    private fun firePendingIntent(fireId: Long, alarm: PlannedAlarm?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            alarm?.let {
                putExtra(EXTRA_FIRE_ID, it.fireId)
                putExtra(EXTRA_REMINDER_ID, it.reminderId)
                putExtra(EXTRA_TITLE, it.title)
                putExtra(EXTRA_FIRE_AT, it.fireAtIso)
                putExtra(EXTRA_IDEMPOTENCY_KEY, it.idempotencyKey())
                putExtra(EXTRA_VOICE, it.voiceEnabled)
                putExtra(EXTRA_KIND, it.kind)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            fireId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.bugu.ACTION_FIRE"
        const val EXTRA_FIRE_ID = "fireId"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FIRE_AT = "fireAt"
        const val EXTRA_IDEMPOTENCY_KEY = "idempotencyKey"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_KIND = "kind"
    }
}
