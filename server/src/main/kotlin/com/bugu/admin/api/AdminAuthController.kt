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
    fun login(@RequestBody request: AdminLoginRequest): ApiResponse<AdminLoginResponse> {
        val (user, token) = service.login(request.username, request.password)
        return ApiResponse.ok(AdminLoginResponse(token = token, user = user.toView()))
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): ApiResponse<AdminUserView> {
        val adminId = request.getAttribute(AdminAuthFilter.ATTR_ADMIN_ID) as? Long
            ?: throw IllegalStateException("AdminAuthFilter 未注入 adminId")
        return ApiResponse.ok(service.me(adminId).toView())
    }
}
