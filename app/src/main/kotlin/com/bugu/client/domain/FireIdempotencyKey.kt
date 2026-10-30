package com.bugu.client.domain

/**
 * fire 幂等键构造(E7-S1 上报用):与 server ReminderService.saveFires 规则一致。
 * ONCE/REPEAT:reminderId:fireAt;MUTUAL_ALARM:reminderId:fireAt:targetUserId。
 * fireAt 必须用与服务端相同的 ISO 归一化(ReminderView.fireAt 原样回填即可,勿重排格式)。
 */
object FireIdempotencyKey {

    const val SOURCE_LOCAL_ALARM = "LOCAL_ALARM"

    const val SOURCE_PUSH = "PUSH"

    fun build(reminderId: Long, fireAtIso: String, targetUserId: Long? = null, mutual: Boolean): String =
        if (mutual) {
            "$reminderId:$fireAtIso:${requireNotNull(targetUserId) { "互叫闹钟幂等键须带 targetUserId" }}"
        } else {
            "$reminderId:$fireAtIso"
        }
}
