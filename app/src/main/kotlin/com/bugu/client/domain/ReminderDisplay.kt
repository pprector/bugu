package com.bugu.client.domain

/**
 * 提醒端侧展示态推导:server status 字符串 → UI 可直接消费的四态。
 * 纯函数,不依赖平台 API;未知状态(服务端后续新增)按 WAITING 处理——
 * 宁可多展示一个可操作条目,由服务端 ack 校验拒绝,也不静默吞掉。
 */
enum class ReminderDisplayState {
    /** 待响应:可确认/稍后/拒绝(含被撤销后重新待响应)。 */
    WAITING,

    /** 已完成确认。 */
    DONE,

    /** 未完成:拒绝或超时未响应。 */
    FAILED,

    /** 发起人取消。 */
    CANCELED,
}

object ReminderDisplay {

    private val STATE_BY_STATUS: Map<String, ReminderDisplayState> = mapOf(
        "PENDING" to ReminderDisplayState.WAITING,
        "FIRED" to ReminderDisplayState.WAITING,
        "SNOOZED" to ReminderDisplayState.WAITING,
        "ACKED" to ReminderDisplayState.DONE,
        "DECLINED" to ReminderDisplayState.FAILED,
        "MISSED" to ReminderDisplayState.FAILED,
        "CANCELED" to ReminderDisplayState.CANCELED,
    )

    /** server reminder.status → 展示态;互叫单方确认时 status 仍为 FIRED,展示 WAITING 与服务端语义一致。 */
    fun stateOf(status: String): ReminderDisplayState =
        STATE_BY_STATUS[status] ?: ReminderDisplayState.WAITING

    /** 是否显示「完成了/稍后/无法完成」三出口。 */
    fun actionable(status: String): Boolean = stateOf(status) == ReminderDisplayState.WAITING

    /** 子女端是否可撤销误触完成(E6-S2:仅 ACKED 可撤)。 */
    fun revocable(status: String): Boolean = status == "ACKED"
}
