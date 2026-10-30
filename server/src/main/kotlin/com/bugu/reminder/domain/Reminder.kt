package com.bugu.reminder.domain

import java.time.LocalDateTime

/**
 * 提醒聚合根(PRD FR-02)。创建期不变式在此兜底;用户可见校验在 ReminderService 转 BizException。
 * 状态变更与修改只走聚合方法(revise / cancel),外部禁止直改 status。
 * bondId=null 表示自惦记(送给自己,无互伴载体)。
 */
class Reminder(
    val id: Long? = null,
    val bondId: Long?,
    val creatorId: Long,
    val receiverId: Long,
    title: String,
    val kind: ReminderKind,
    val tone: ToneLevel,
    fireAt: LocalDateTime,
    val repeatRule: String? = null,
    val voiceEnabled: Boolean = false,
    val note: String? = null,
    val silent: Boolean = false,
    status: ReminderStatus = ReminderStatus.PENDING,
    val createdAt: LocalDateTime,
) {
    var title: String = title
        private set
    var fireAt: LocalDateTime = fireAt
        private set
    var status: ReminderStatus = status
        private set

    init {
        require(title.isNotBlank() && title.length <= MAX_TITLE) { "标题必填且不超过 $MAX_TITLE 字" }
        require(note == null || note.length <= MAX_NOTE) { "备注不超过 $MAX_NOTE 字" }
        if (kind == ReminderKind.REPEAT) {
            require(repeatRule != null && RepeatRulePolicy.isValid(repeatRule)) { "重复类型须带合法重复规则" }
        } else {
            require(repeatRule == null) { "非重复类型不带重复规则" }
        }
        require(!voiceEnabled || kind == ReminderKind.MUTUAL_ALARM) { "语音叫醒仅互叫闹钟可开" }
    }

    /** 修改标题与触发时刻(S4):仅 PENDING 可改;为 null 的字段保持不变。 */
    fun revise(newTitle: String?, newFireAt: LocalDateTime?, now: LocalDateTime) {
        require(status == ReminderStatus.PENDING) { "仅待触发提醒可修改" }
        if (newTitle != null) {
            val trimmed = newTitle.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= MAX_TITLE) { "标题必填且不超过 $MAX_TITLE 字" }
            title = trimmed
        }
        if (newFireAt != null) {
            require(newFireAt.isAfter(now)) { "触发时间须为未来时刻" }
            fireAt = newFireAt
        }
    }

    /** 取消(S4):PENDING/FIRED → CANCELED 终态,不可逆。 */
    fun cancel() {
        require(status.canTransitTo(ReminderStatus.CANCELED)) { "当前状态($status)不可取消" }
        status = ReminderStatus.CANCELED
    }

    /** 端侧到点响铃(隐式,ack 时若仍 PENDING 则先触发)。 */
    fun markFired() {
        require(status.canTransitTo(ReminderStatus.FIRED)) { "当前状态($status)不可标记已触发" }
        status = ReminderStatus.FIRED
    }

    /** 接收人确认完成:FIRED → ACKED。 */
    fun ackDone() {
        require(status == ReminderStatus.FIRED) { "仅已触发提醒可确认完成,当前=$status" }
        status = ReminderStatus.ACKED
    }

    /** 误触撤销(E6-S2):ACKED → FIRED,由子女端标记误触后回滚,长辈端不暴露撤销入口。 */
    fun revokeDone() {
        require(status == ReminderStatus.ACKED) { "仅已完成提醒可撤销,当前=$status" }
        status = ReminderStatus.FIRED
    }

    /** 接收人稍后提醒:FIRED → SNOOZED(不通知发起人,由应用层新建 fire)。 */
    fun ackSnooze() {
        require(status == ReminderStatus.FIRED) { "仅已触发提醒可稍后,当前=$status" }
        status = ReminderStatus.SNOOZED
    }

    /** 接收人无法完成:FIRED → DECLINED。 */
    fun ackDecline() {
        require(status == ReminderStatus.FIRED) { "仅已触发提醒可拒绝,当前=$status" }
        status = ReminderStatus.DECLINED
    }

    /** 24h 无响应收口(S3):PENDING/FIRED/SNOOZED → MISSED 终态。 */
    fun markMissed() {
        require(status.canTransitTo(ReminderStatus.MISSED)) { "当前状态($status)不可收口为未完成" }
        status = ReminderStatus.MISSED
    }

    /**
     * 重复提醒续期(E10 决策:确认后与错过收口后均续期):ACKED/MISSED → PENDING 并前推触发时刻。
     * 系列语义下「本次已完成/已错过」不等于「整条提醒结束」,故不走 [ReminderStatus.canTransitTo]。
     */
    fun rescheduleNext(nextFireAt: LocalDateTime) {
        require(kind == ReminderKind.REPEAT) { "仅重复提醒可续期" }
        require(status == ReminderStatus.ACKED || status == ReminderStatus.MISSED) { "当前状态($status)不可续期" }
        require(nextFireAt.isAfter(fireAt)) { "下一次触发须晚于本次" }
        fireAt = nextFireAt
        status = ReminderStatus.PENDING
    }

    companion object {
        const val MAX_TITLE = 20
        const val MAX_NOTE = 50
    }
}
