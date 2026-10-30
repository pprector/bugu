package com.bugu.admin.infra

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** 后台账号(E9):与 App 账号 app_user 完全隔离。 */
@Table("admin_user")
data class AdminUserRow(
    @Id
    val id: Long? = null,
    val username: String,
    val passwordHash: String,
    val nickname: String,
    val role: String = "OPS",
    val enabled: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface AdminUserRepository : CrudRepository<AdminUserRow, Long> {
    fun findByUsername(username: String): AdminUserRow?
    fun existsByRole(role: String): Boolean
}

/** 后台操作审计(E9)。 */
@Table("admin_audit_log")
data class AdminAuditLogRow(
    @Id
    val id: Long? = null,
    val adminUserId: Long,
    val action: String,
    val targetType: String? = null,
    val targetId: String? = null,
    val detail: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface AdminAuditLogRepository : CrudRepository<AdminAuditLogRow, Long> {
    fun findByAdminUserIdOrderByCreatedAtDesc(adminUserId: Long): List<AdminAuditLogRow>
}
