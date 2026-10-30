package com.bugu.identity.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * device_token 行:设备与推送令牌(结构见 V1__baseline.sql)。
 * platform=ANDROID/IOS/HARMONY;push_enabled 控制是否推送。
 */
@Table("device_token")
data class DeviceTokenRow(
    @Id
    val id: Long? = null,
    val userId: Long,
    val platform: String,
    val vendor: String? = null,
    val pushToken: String,
    val pushEnabled: Boolean = true,
    val lastActiveAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface DeviceTokenRepository : CrudRepository<DeviceTokenRow, Long> {
    fun findByUserIdAndPushEnabledTrue(userId: Long): List<DeviceTokenRow>

    /** 按 push_token 唯一键查(上报 upsert 用,见 uk_push_token)。 */
    fun findByPushToken(pushToken: String): DeviceTokenRow?
}
