package com.bugu.reminder.domain

/**
 * 提醒生命周期,对应 PRD FR-03。
 *
 * PENDING  待触发
 * FIRED    已触发(端侧到点响铃并上报)
 * ACKED    接收人确认完成
 * SNOOZED  接收人选择稍后(不通知发起人)
 * DECLINED 接收人无法完成(带原因归类)
 * MISSED   24h 无任何响应,收口为未完成
 * CANCELED 发起人取消(S4 新增终态)
 */
enum class ReminderStatus {
    PENDING, FIRED, ACKED, SNOOZED, DECLINED, MISSED, CANCELED;

    fun canTransitTo(next: ReminderStatus): Boolean = when (this) {
        PENDING -> next == FIRED || next == MISSED || next == CANCELED
        FIRED -> next == ACKED || next == SNOOZED || next == DECLINED || next == MISSED || next == CANCELED
        SNOOZED -> next == FIRED || next == MISSED
        ACKED -> next == FIRED
        DECLINED, MISSED, CANCELED -> false
    }
}

/** 提醒类型:单次 / 重复 / 互叫闹钟(PRD FR-04)。 */
enum class ReminderKind { ONCE, REPEAT, MUTUAL_ALARM }

/** 语气档位(PRD FR-05):温柔 / 唠叨 / 严格。 */
enum class ToneLevel { GENTLE, NAG, STRICT }

/** 接收人的三个出口(PRD FR-03)。 */
enum class AckAction { DONE, SNOOZE, DECLINE }

/** 无法完成的原因四类归类(PRD FR-03),用于 AI 文案要素与运营统计。 */
enum class DeclineReason { BUSY, CANNOT, FORGOT, OTHER }
