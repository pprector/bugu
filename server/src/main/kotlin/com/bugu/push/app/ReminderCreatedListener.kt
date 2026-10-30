package com.bugu.push.app

import com.bugu.reminder.app.ReminderCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 创建推送监听(reminder → push 经 Spring 领域事件解耦,项目约定)。
 * AFTER_COMMIT:创建事务提交后才推,避免推送先于落库导致接收端查不到。
 */
@Component
class ReminderCreatedListener(
    private val pushService: PushService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ReminderCreatedEvent) {
        runCatching {
            pushService.pushCreated(
                fireId = event.fireId,
                receiverId = event.receiverId,
                title = event.title,
                tone = event.tone,
            )
        }.onFailure { log.error("创建推送失败 reminder={} fire={}", event.reminderId, event.fireId, it) }
    }
}
