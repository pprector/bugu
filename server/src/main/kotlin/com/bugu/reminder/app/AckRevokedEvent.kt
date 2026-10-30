package com.bugu.reminder.app

/**
 * 误触撤销事件(E6-S2):ReminderService.revokeAck 事务内发布,push 模块 AFTER_COMMIT 监听通知双方。
 * 长辈端告知「刚才的完成已被家人撤销」,子女端确认撤销成功。
 */
data class AckRevokedEvent(
    val reminderId: Long,
    val fireId: Long,
    val creatorId: Long,
    val receiverId: Long,
    val title: String,
)
