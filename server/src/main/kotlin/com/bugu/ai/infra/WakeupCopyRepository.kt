package com.bugu.ai.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

/** wakeup_copy 行:互叫闹钟叫醒语按日缓存(E5-S3),结构见 V12__wakeup_copy.sql。 */
@Table("wakeup_copy")
data class WakeupCopyRow(
    @Id
    val id: Long? = null,
    val reminderId: Long,
    val copyDate: LocalDate,
    val content: String,
    val degraded: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface WakeupCopyRepository : CrudRepository<WakeupCopyRow, Long> {
    fun findByReminderIdAndCopyDate(reminderId: Long, copyDate: LocalDate): WakeupCopyRow?
}
