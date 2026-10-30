package com.bugu.admin.api

import com.bugu.admin.app.AdminAccountService
import com.bugu.admin.app.AdminAuditService
import com.bugu.admin.infra.AdminAuthFilter
import com.bugu.admin.infra.AdminUserRow
import com.bugu.shared.api.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 后台账号列表项(不含口令)。 */
@Serializable
data class AdminAccountView(
    val id: Long,
    val username: String,
    val nickname: String,
    val role: String,
    val enabled: Boolean,
    val createdAt: String,
)

/** 创建后台账号请求。 */
@Serializable
data class AdminAccountCreateRequest(
    val username: String,
    val password: String,
    val nickname: String,
    val role: String,
)

/** 启停后台账号请求。 */
@Serializable
data class AdminAccountEnabledRequest(
    val enabled: Boolean,
)

fun AdminUserRow.toAccountView() = AdminAccountView(
    id = requireNotNull(id) { "admin id 不应为空" },
    username = username,
    nickname = nickname,
    role = role,
    enabled = enabled,
    createdAt = createdAt.toString(),
)

/**
 * 后台账号管理:列表 OPS/SUPER 均可读;创建与启停仅 SUPER
 * (AdminAuthFilter 对非 GET 要求 admin:write),写操作统一落审计。
 */
@RestController
@RequestMapping("/api/v1/admin/admin-users")
class AdminAccountController(
    private val service: AdminAccountService,
    private val audit: AdminAuditService,
) {

    @GetMapping
    fun list(): ApiResponse<List<AdminAccountView>> =
        ApiResponse.ok(service.list().map { it.toAccountView() })

    @PostMapping
    fun create(
        request: HttpServletRequest,
        @RequestBody body: AdminAccountCreateRequest,
    ): ApiResponse<AdminAccountView> {
        val adminId = adminId(request)
        val row = service.create(body.username, body.password, body.nickname, body.role)
        audit.record(
            adminId,
            action = "ADMIN_ACCOUNT_CREATE",
            targetType = "admin_user",
            targetId = row.id,
            detail = "username=${row.username} role=${row.role}",
        )
        return ApiResponse.ok(row.toAccountView())
    }

    @PatchMapping("/{id}/enabled")
    fun setEnabled(
        request: HttpServletRequest,
        @PathVariable id: Long,
        @RequestBody body: AdminAccountEnabledRequest,
    ): ApiResponse<AdminAccountView> {
        val adminId = adminId(request)
        val row = service.setEnabled(adminId, id, body.enabled)
        audit.record(
            adminId,
            action = "ADMIN_ACCOUNT_TOGGLE",
            targetType = "admin_user",
            targetId = id,
            detail = "enabled=${body.enabled}",
        )
        return ApiResponse.ok(row.toAccountView())
    }

    private fun adminId(request: HttpServletRequest): Long =
        request.getAttribute(AdminAuthFilter.ATTR_ADMIN_ID) as? Long
            ?: throw IllegalStateException("AdminAuthFilter 未注入 adminId")
}
