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
import java.time.LocalDateTime

/** 后台账号服务(E9):登录校验与 me 视图。 */
@Service
class AdminAuthService(
    private val repository: AdminUserRepository,
    private val jwt: JwtTokenService,
    private val audit: AdminAuditService,
) {

    fun login(username: String, password: String): Pair<AdminUserRow, String> {
        val user = repository.findByUsername(username)
        if (user == null || !user.enabled || !AdminPasswordHasher.verify(password, user.passwordHash)) {
            throw BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "用户名或密码错误")
        }
        val adminId = requireNotNull(user.id) { "admin id 不应为空" }
        val token = jwt.issueAdmin(adminId)
        audit.record(adminId, action = "LOGIN", detail = "username=$username")
        return user to token
    }

    fun me(adminUserId: Long): AdminUserRow =
        repository.findByIdOrNull(adminUserId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "后台账号不存在")
}

/** 后台种子配置(E9):密码不进 git。 */
@ConfigurationProperties(prefix = "bugu.admin")
data class AdminSeedProperties(
    val seedUsername: String = "super",
    val seedPassword: String = "bugu-admin-2026",
)

/**
 * 启动种子(E9):无 SUPER 用户则按配置创建,密码不进 git;幂等。
 */
@Component
class AdminSeeder(
    private val repository: AdminUserRepository,
    private val properties: AdminSeedProperties,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (repository.existsByRole(AdminRoles.SUPER)) return
        val saved = repository.save(
            AdminUserRow(
                username = properties.seedUsername,
                passwordHash = AdminPasswordHasher.hash(properties.seedPassword),
                nickname = "超级管理员",
                role = AdminRoles.SUPER,
                enabled = true,
                createdAt = LocalDateTime.now(),
            ),
        )
        log.info("admin 种子账号已创建 id={} username={}(初始密码来自 bugu.admin.seed-password)", saved.id, properties.seedUsername)
    }
}
