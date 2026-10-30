package com.bugu.reminder.infra

import com.bugu.reminder.domain.Reminder
import com.bugu.reminder.domain.ReminderKind
import com.bugu.reminder.domain.ReminderStatus
import com.bugu.reminder.domain.ToneLevel
import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** reminder 行。结构见 V1__baseline.sql;V8 起追加 silent 列(S4)。 */
@Table("reminder")
data class ReminderRow(
    @Id
    val id: Long? = null,
    val bondId: Long,
    val creatorId: Long,
    val receiverId: Long,
    val title: String,
    val kind: String,
    val tone: String = "GENTLE",
    val fireAt: LocalDateTime,
    val repeatRule: String? = null,
    val voiceEnabled: Boolean = false,
    val note: String? = null,
    val status: String = "PENDING",
    val silent: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

/** reminder 行与聚合的映射,reminder 模块内共享。 */
object ReminderMapper {

    fun toDomain(row: ReminderRow): Reminder = Reminder(
        id = row.id,
        bondId = row.bondId,
        creatorId = row.creatorId,
        receiverId = row.receiverId,
        title = row.title,
        kind = ReminderKind.valueOf(row.kind),
        tone = ToneLevel.valueOf(row.tone),
        fireAt = row.fireAt,
        repeatRule = row.repeatRule,
        voiceEnabled = row.voiceEnabled,
        note = row.note,
        silent = row.silent,
        status = ReminderStatus.valueOf(row.status),
        createdAt = row.createdAt,
    )

    fun toRow(domain: Reminder): ReminderRow = ReminderRow(
        id = domain.id,
        bondId = domain.bondId,
        creatorId = domain.creatorId,
        receiverId = domain.receiverId,
        title = domain.title,
        kind = domain.kind.name,
        tone = domain.tone.name,
        fireAt = domain.fireAt,
        repeatRule = domain.repeatRule,
        voiceEnabled = domain.voiceEnabled,
        note = domain.note,
        status = domain.status.name,
        silent = domain.silent,
        createdAt = domain.createdAt,
        updatedAt = LocalDateTime.now(),
    )
}

@Repository
interface ReminderRepository : CrudRepository<ReminderRow, Long> {
    fun findByCreatorIdOrderByFireAt(creatorId: Long): List<ReminderRow>
    fun findByReceiverIdOrderByFireAt(receiverId: Long): List<ReminderRow>

    /** 当日同一对接收/发起人已创建条数(每日上限计数,含已取消,不返还额度)。 */
    fun countByCreatorIdAndReceiverIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        creatorId: Long,
        receiverId: Long,
        dayStart: LocalDateTime,
        nextDayStart: LocalDateTime,
    ): Long
}
