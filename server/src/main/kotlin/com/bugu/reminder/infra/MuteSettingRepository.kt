package com.bugu.reminder.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** receiver_mute_setting 行:存在即静音,删除即恢复。结构见 V7。 */
@Table("receiver_mute_setting")
data class MuteSettingRow(
    @Id
    val id: Long? = null,
    val receiverId: Long,
    val creatorId: Long,
    val kind: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface MuteSettingRepository : CrudRepository<MuteSettingRow, Long> {
    fun existsByReceiverIdAndCreatorIdAndKind(receiverId: Long, creatorId: Long, kind: String): Boolean
    fun findByReceiverId(receiverId: Long): List<MuteSettingRow>
    fun deleteByReceiverIdAndCreatorIdAndKind(receiverId: Long, creatorId: Long, kind: String)
}
