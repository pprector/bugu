package com.bugu.reminder.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * reminder_ack 行:接收人一次响应记录(结构见 V1__baseline.sql)。
 * uk_ack_fire 保证同一 fire 仅一条 ack,作为幂等键。
 */
@Table("reminder_ack")
data class AckRow(
    @Id
    val id: Long? = null,
    val fireId: Long,
    val action: String,
    val reasonCode: String? = null,
    val snoozeMinutes: Int? = null,
    val ackedAt: LocalDateTime,
)

@Repository
interface AckRepository : CrudRepository<AckRow, Long> {
    fun existsByFireId(fireId: Long): Boolean
    fun findByFireId(fireId: Long): AckRow?
}
