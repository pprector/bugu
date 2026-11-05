package com.bugu.admin.app

import com.bugu.admin.infra.AdminPasswordHasher
import com.bugu.admin.infra.AdminUserRepository
import com.bugu.admin.infra.AdminUserRow
import com.bugu.identity.infra.JwtTokenService
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.LocalDateTime

/** 后台账号服务(E9):登录校验与 me 视图。 */
@Service
class AdminAuthService(
    private val repository: AdminUserRepository,
    private val jwt: JwtTokenService,
    private val audit: AdminAuditService,
    private val throttle: AdminLoginThrottle,
) {

    /**
     * 登录校验。clientKey 为「IP|用户名」,用于跨账号/IP 的失败限流;
     * 连续失败 5 次锁定 15 分钟。
     */
    fun login(username: String, password: String, clientKey: String): Pair<AdminUserRow, String> {
        throttle.check(clientKey)
        val user = repository.findByUsername(username)
        if (user == null || !user.enabled || !AdminPasswordHasher.verify(password, user.passwordHash)) {
            throttle.onFailure(clientKey)
            throw BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "用户名或密码错误")
        }
        throttle.onSuccess(clientKey)
        val adminId = requireNotNull(user.id) { "admin id 不应为空" }
        val token = jwt.issueAdmin(adminId, user.tokenVersion)
        audit.record(adminId, action = "LOGIN", detail = "username=$username")
        return user to token
    }

    /** 退出登录:自增 token_version,该账号此前签发的全部 token 立即失效。 */
    fun logout(adminUserId: Long) {
        val user = repository.findByIdOrNull(adminUserId)
            ?: throw BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
        repository.save(user.copy(tokenVersion = user.tokenVersion + 1))
        audit.record(adminUserId, action = "LOGOUT")
    }

    fun me(adminUserId: Long): AdminUserRow =
        repository.findByIdOrNull(adminUserId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "后台账号不存在")
}

/** 后台种子配置(E9):口令不进仓库,留空则首次启动随机生成并输出一次。 */
@ConfigurationProperties(prefix = "bugu.admin")
data class AdminSeedProperties(
    val seedUsername: String = "super",
    val seedPassword: String = "",
)

/**
 * 启动种子(E9):无 SUPER 用户则按配置创建,幂等。
 * 未配置 seed-password 时随机生成一次性口令,仅在创建当次启动日志中输出。
 */
@Component
class AdminSeeder(
    private val repository: AdminUserRepository,
    private val properties: AdminSeedProperties,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (repository.existsByRole(AdminRoles.SUPER)) return
        val configured = properties.seedPassword.trim().takeIf { it.isNotEmpty() }
        val password = configured ?: generatePassword()
        val saved = repository.save(
            AdminUserRow(
                username = properties.seedUsername,
                passwordHash = AdminPasswordHasher.hash(password),
                nickname = "超级管理员",
                role = AdminRoles.SUPER,
                enabled = true,
                createdAt = LocalDateTime.now(),
            ),
        )
        if (configured != null) {
            log.info("admin 种子账号已创建 id={} username={}(初始密码来自 bugu.admin.seed-password)", saved.id, properties.seedUsername)
        } else {
            log.warn(
                "admin 种子账号已创建 id={} username={};未配置 bugu.admin.seed-password,已随机生成初始密码: {}" +
                    "(仅本次输出,请立即保存;后续启动不会重复输出)",
                saved.id,
                properties.seedUsername,
                password,
            )
        }
    }

    /** 去掉易混淆字符(0/O/1/I/l),16 位足够本地起步使用。 */
    private fun generatePassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val random = SecureRandom()
        return (1..16).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }
}
