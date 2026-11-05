package com.bugu.admin.api

import com.bugu.admin.app.AdminRoles
import com.bugu.admin.app.AdminAuthService
import com.bugu.admin.infra.AdminAuthFilter
import com.bugu.admin.infra.AdminUserRow
import com.bugu.shared.api.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 后台登录请求。 */
@Serializable
data class AdminLoginRequest(
    val username: String,
    val password: String,
)

/** 后台账号视图(不含口令)。 */
@Serializable
data class AdminUserView(
    val id: Long,
    val username: String,
    val nickname: String,
    val role: String,
    val perms: List<String>,
)

/** 后台登录响应。 */
@Serializable
data class AdminLoginResponse(
    val token: String,
    val user: AdminUserView,
)

fun AdminUserRow.toView() = AdminUserView(
    id = requireNotNull(id) { "admin id 不应为空" },
    username = username,
    nickname = nickname,
    role = role,
    perms = AdminRoles.permsOf(role).toList(),
)

/**
 * 后台鉴权接口(E9)。login 公开;me 需 admin token。
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
class AdminAuthController(private val service: AdminAuthService) {

    @PostMapping("/login")
    fun login(request: HttpServletRequest, @RequestBody body: AdminLoginRequest): ApiResponse<AdminLoginResponse> {
        val clientKey = clientIp(request) + "|" + body.username
        val (user, token) = service.login(body.username, body.password, clientKey)
        return ApiResponse.ok(AdminLoginResponse(token = token, user = user.toView()))
    }

    /**
     * 取真实客户端 IP:生产 8081 仅绑 127.0.0.1,经 Nginx 转发,此时优先 X-Forwarded-For 首段。
     * 仅当请求来自本机代理时才信任该头,避免外部直接伪造 XFF 绕过 IP+账号限流。
     */
    private fun clientIp(request: HttpServletRequest): String {
        val remote = request.remoteAddr
        val fromLocalProxy = remote == "127.0.0.1" || remote == "::1" || remote == "0:0:0:0:0:0:0:1"
        if (fromLocalProxy) {
            request.getHeader("X-Forwarded-For")
                ?.split(',')
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return remote ?: "unknown"
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): ApiResponse<AdminUserView> {
        val adminId = request.getAttribute(AdminAuthFilter.ATTR_ADMIN_ID) as? Long
            ?: throw IllegalStateException("AdminAuthFilter 未注入 adminId")
        return ApiResponse.ok(service.me(adminId).toView())
    }
}
