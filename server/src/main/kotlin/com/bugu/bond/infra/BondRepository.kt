package com.bugu.bond.infra

import com.bugu.bond.domain.Bond
import com.bugu.bond.domain.BondStatus
import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * bond 行。V3 后:receiver_id 在 PENDING_OUTBOUND 期间为 NULL;bind_code 确认后清空。
 */
@Table("bond")
data class BondRow(
    @Id
    val id: Long? = null,
    val requesterId: Long,
    val receiverId: Long? = null,
    val status: String = "PENDING_OUTBOUND",
    val bindCode: String? = null,
    val codeExpiresAt: LocalDateTime? = null,
    val confirmedAt: LocalDateTime? = null,
    val unboundAt: LocalDateTime? = null,
    val relationTag: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

/** bond 行与聚合的映射,bond 模块内共享。 */
object BondMapper {

    fun toDomain(row: BondRow): Bond = Bond(
        id = row.id,
        requesterId = row.requesterId,
        receiverId = row.receiverId,
        status = BondStatus.valueOf(row.status),
        bindCode = row.bindCode,
        codeExpiresAt = row.codeExpiresAt,
        confirmedAt = row.confirmedAt,
        unboundAt = row.unboundAt,
        relationTag = row.relationTag,
        createdAt = row.createdAt,
    )

    fun toRow(domain: Bond): BondRow = BondRow(
        id = domain.id,
        requesterId = domain.requesterId,
        receiverId = domain.receiverId,
        status = domain.status.name,
        bindCode = domain.bindCode,
        codeExpiresAt = domain.codeExpiresAt,
        confirmedAt = domain.confirmedAt,
        unboundAt = domain.unboundAt,
        relationTag = domain.relationTag,
        createdAt = domain.createdAt,
        updatedAt = LocalDateTime.now(),
    )
}

@Repository
interface BondRepository : CrudRepository<BondRow, Long> {
    fun findFirstByRequesterIdAndStatus(requesterId: Long, status: String): BondRow?
    fun findFirstByBindCodeAndStatus(bindCode: String, status: String): BondRow?
    fun existsByBindCode(bindCode: String): Boolean
    fun existsByRequesterIdAndStatus(requesterId: Long, status: String): Boolean
    fun existsByReceiverIdAndStatus(receiverId: Long, status: String): Boolean
    fun findByRequesterIdOrReceiverId(requesterId: Long, receiverId: Long): List<BondRow>
    fun findFirstByRequesterIdAndReceiverIdAndStatus(requesterId: Long, receiverId: Long, status: String): BondRow?
    fun findFirstByReceiverIdAndRequesterIdAndStatus(receiverId: Long, requesterId: Long, status: String): BondRow?
}

/** 该用户是否已有 ACTIVE 绑定(任一角色)。绑定上限校验共用,模块内复用。 */
fun BondRepository.hasActiveBond(userId: Long): Boolean =
    existsByRequesterIdAndStatus(userId, BondStatus.ACTIVE.name) ||
        existsByReceiverIdAndStatus(userId, BondStatus.ACTIVE.name)
