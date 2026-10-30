package com.bugu.push.app

import com.bugu.reminder.app.AckRevokedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 误触撤销通知监听(E6-S2):reminder → push 经 Spring 领域事件解耦。
 * AFTER_COMMIT:撤销事务提交后才通知双方,避免通知先于状态回滚。
 */
@Component
class AckRevokedListener(
    private val pushService: PushService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: AckRevokedEvent) {
        runCatching {
            pushService.pushRevoked(
                fireId = event.fireId,
                creatorId = event.creatorId,
                receiverId = event.receiverId,
                title = event.title,
            )
        }.onFailure { log.error("撤销通知失败 reminder={} fire={}", event.reminderId, event.fireId, it) }
    }
}
