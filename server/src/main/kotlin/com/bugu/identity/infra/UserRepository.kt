package com.bugu.identity.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * app_user 行。Spring Data JDBC 驼峰自动映射下划线列。
 */
@Table("app_user")
data class UserRow(
    @Id
    val id: Long? = null,
    val nickname: String,
    val avatarUrl: String? = null,
    val phone: String? = null,
    val deviceUuid: String? = null,
    val elderMode: Boolean = false,
    val accountType: String = "NORMAL",
    val accountState: String = "ACTIVE",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface UserRepository : CrudRepository<UserRow, Long> {
    fun findByPhone(phone: String): UserRow?
    fun existsByPhone(phone: String): Boolean
    fun findByDeviceUuid(deviceUuid: String): UserRow?

    @Query("SELECT id FROM app_user WHERE id = :id FOR UPDATE")
    fun lockUser(id: Long): Long?
}
