package com.bugu.schedule

import com.bugu.client.api.ReminderView
import com.bugu.client.domain.FireIdempotencyKey
import com.bugu.client.domain.ReminderDisplay
import com.bugu.client.domain.ReminderDisplayState
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 端侧闹钟计划器(E8-S4,纯函数):从「我接收的提醒」推导应注册的精确闹钟。
 * 只取 WAITING、fireId 非空、fireAt 在未来的提醒;同一 fire 去重。
 * fireAt 为 ISO 本地时刻,按设备默认时区换算触发毫秒;与 Android API 解耦,便于 JVM 单测。
 */
data class PlannedAlarm(
    val fireId: Long,
    val reminderId: Long,
    val title: String,
    /** ISO 本地时刻,闹钟触发点 */
    val fireAtIso: String,
    /** epoch 毫秒(AlarmManager 触发时刻) */
    val triggerAtMillis: Long,
    val voiceEnabled: Boolean,
    val kind: String,
    /** 互叫闹钟的幂等键 target = 本端用户(即 incoming 视图的 receiverId) */
    val targetUserId: Long,
) {
    /** E7-S1 上报幂等键(与服务端 saveFires 规则一致,MUTUAL 带 target)。 */
    fun idempotencyKey(): String =
        FireIdempotencyKey.build(
            reminderId = reminderId,
            fireAtIso = fireAtIso,
            targetUserId = targetUserId,
            mutual = kind == "MUTUAL_ALARM",
        )
}

object ReminderPlanner {

    fun plan(incoming: List<ReminderView>, nowEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<PlannedAlarm> =
        incoming.asSequence()
            .filter { ReminderDisplay.stateOf(it.status) == ReminderDisplayState.WAITING }
            .filter { it.fireId != null }
            .mapNotNull { reminder ->
                val triggerAt = runCatching {
                    LocalDateTime.parse(reminder.fireAt).atZone(zone).toInstant().toEpochMilli()
                }.getOrNull() ?: return@mapNotNull null
                if (triggerAt <= nowEpochMillis) return@mapNotNull null
                PlannedAlarm(
                    fireId = reminder.fireId!!,
                    reminderId = reminder.id,
                    title = reminder.title,
                    fireAtIso = reminder.fireAt,
                    triggerAtMillis = triggerAt,
                    voiceEnabled = reminder.voiceEnabled,
                    kind = reminder.kind,
                    targetUserId = reminder.receiverId,
                )
            }
            .distinctBy { it.fireId }
            .sortedBy { it.triggerAtMillis }
            .toList()
}
