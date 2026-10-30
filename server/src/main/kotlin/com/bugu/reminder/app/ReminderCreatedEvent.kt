package com.bugu.reminder.app

/**
 * 提醒创建事件:ReminderService.create 事务内发布,push 模块 AFTER_COMMIT 监听推 scene=CREATE。
 * 模块间经 Spring 领域事件解耦(项目约定)。
 */
data class ReminderCreatedEvent(
    val reminderId: Long,
    val fireId: Long,
    val creatorId: Long,
    val receiverId: Long,
    val title: String,
    val tone: String,
)
