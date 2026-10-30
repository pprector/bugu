package com.bugu.reminder.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * escalation_log 行:升级链动作记录(结构见 V1__baseline.sql)。
 * stage=T1/T2/CLOSE;action=REPUSH_RECEIVER/NOTIFY_CREATOR/MARK_MISSED;push_result=ACCEPTED/FAILED/SKIPPED。
 */
@Table("escalation_log")
data class EscalationLogRow(
    @Id
    val id: Long? = null,
    val fireId: Long,
    val stage: String,
    val action: String,
    val pushResult: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface EscalationLogRepository : CrudRepository<EscalationLogRow, Long> {
    fun findByFireIdOrderByCreatedAt(fireId: Long): List<EscalationLogRow>
    fun existsByFireIdAndStage(fireId: Long, stage: String): Boolean

    /** 按 fire_id 批量删除(测试清理用)。 */
    fun deleteByFireIdIn(fireIds: List<Long>)
}
