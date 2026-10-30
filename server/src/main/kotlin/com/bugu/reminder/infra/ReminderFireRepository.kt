package com.bugu.reminder.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * reminder_fire 行:一次触发的记录,升级链唯一事实源(结构见 V1__baseline.sql)。
 * E2 创建提醒时预生成首条(state=PENDING,idempotency_key=reminderId:fireAt);
 * E5 互叫闹钟双方各一条(targetUserId 各为双方,幂等键 reminderId:fireAt:userId)。
 */
@Table("reminder_fire")
data class ReminderFireRow(
    @Id
    val id: Long? = null,
    val reminderId: Long,
    val targetUserId: Long,
    val fireAt: LocalDateTime,
    val reportedAt: LocalDateTime? = null,
    val source: String? = null,
    val state: String = "PENDING",
    val idempotencyKey: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface ReminderFireRepository : CrudRepository<ReminderFireRow, Long> {
    fun findByReminderIdOrderByFireAt(reminderId: Long): List<ReminderFireRow>

    /** 按状态查询(测试清理用)。 */
    fun findByState(state: String): List<ReminderFireRow>

    /** 长辈模式:取目标用户最早一条待处理(PENDING/FIRED)触发记录,无则 null。 */
    fun findFirstByTargetUserIdAndStateInOrderByFireAt(targetUserId: Long, states: List<String>): ReminderFireRow?
}
