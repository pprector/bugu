package com.bugu.identity.app

import com.bugu.identity.api.UserDto
import com.bugu.identity.domain.UserAccount
import com.bugu.identity.infra.UserRepository
import com.bugu.identity.infra.UserRow
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * 用户应用服务:app_user 写路径统一收口(建长辈号/代填资料/绑手机号升级)。
 * 读侧供其他模块只读消费(bond → identity 单向依赖)。
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    fun findById(id: Long): UserRow? = userRepository.findByIdOrNull(id)

    fun findByDeviceUuid(deviceUuid: String): UserRow? = userRepository.findByDeviceUuid(deviceUuid)

    /** 创建长辈免注册账号(调用方已用 deviceUuid 做幂等)。 */
    fun createElder(deviceUuid: String, deviceModel: String): UserRow =
        userRepository.save(
            UserRow(
                nickname = "家人",
                deviceUuid = deviceUuid,
                elderMode = true,
                accountType = "ELDER",
            ),
        )

    /** 代填资料:仅更新非空字段,走聚合方法。 */
    fun updateProfile(userId: Long, nickname: String?, avatarUrl: String?): UserRow =
        transactionTemplate.execute {
            val row = userRepository.findByIdOrNull(userId)
                ?: throw IllegalArgumentException("用户不存在: $userId")
            val domain = row.toDomain()
            domain.updateProfile(nickname, avatarUrl)
            userRepository.save(domain.toRow(row.createdAt))
        }

    /** 长辈账号绑手机号升级 NORMAL,走聚合方法。 */
    fun bindPhone(userId: Long, phone: String): UserRow =
        transactionTemplate.execute {
            val row = userRepository.findByIdOrNull(userId)
                ?: throw IllegalArgumentException("用户不存在: $userId")
            val domain = row.toDomain()
            domain.upgradeToNormal(phone)
            userRepository.save(domain.toRow(row.createdAt))
        }

    /** 子女端标记/取消长辈模式(FR-06):即时生效。 */
    fun setElderMode(userId: Long, enabled: Boolean): UserRow =
        transactionTemplate.execute {
            val row = userRepository.findByIdOrNull(userId)
                ?: throw IllegalArgumentException("用户不存在: $userId")
            val domain = row.toDomain()
            domain.setElderMode(enabled)
            userRepository.save(domain.toRow(row.createdAt))
        }

    /** 后台运营查询用(E9):全量用户行,个人运营规模。 */
    fun listAllRaw(): List<UserRow> = userRepository.findAll().toList()

    fun toDto(row: UserRow): UserDto = UserDto(
        id = row.id!!,
        nickname = row.nickname,
        phone = row.phone,
        accountType = row.accountType,
        elderMode = row.elderMode,
        avatarUrl = row.avatarUrl,
    )

    private fun UserRow.toDomain() = UserAccount(
        id = id,
        nickname = nickname,
        avatarUrl = avatarUrl,
        phone = phone,
        deviceUuid = deviceUuid,
        accountType = accountType,
        elderMode = elderMode,
        accountState = accountState,
        createdAt = createdAt,
    )

    private fun UserAccount.toRow(createdAt: LocalDateTime) = UserRow(
        id = id,
        nickname = nickname,
        avatarUrl = avatarUrl,
        phone = phone,
        deviceUuid = deviceUuid,
        elderMode = elderMode,
        accountType = accountType,
        accountState = accountState,
        createdAt = createdAt,
        updatedAt = LocalDateTime.now(),
    )
}
