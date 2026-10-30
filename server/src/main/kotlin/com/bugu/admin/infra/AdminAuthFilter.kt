package com.bugu.admin.infra

import com.bugu.shared.api.ApiResponse
import com.bugu.shared.api.ErrorCode
import io.jsonwebtoken.JwtException
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

/**
 * 后台鉴权过滤器(E9):守 /api/v1/admin 下的全部路径(login 公开),校验 typ=admin token 与账号 enabled;
 * 注入 ATTR_ADMIN_ID / ATTR_ADMIN_ROLE;变更类方法要求 admin:write 权限点(SUPER)。
 * 注册 order=3,与 JwtAuthFilter(1)/ElderModeAccessFilter(2)并列。
 */
class AdminAuthFilter(
    private val jwt: com.bugu.identity.infra.JwtTokenService,
    private val repository: AdminUserRepository,
) : Filter {

    companion object {
        const val ATTR_ADMIN_ID = "bugu.adminId"
        const val ATTR_ADMIN_ROLE = "bugu.adminRole"
        const val LOGIN_PATH = "/api/v1/admin/auth/login"
        private const val PERM_ADMIN_WRITE = com.bugu.admin.app.AdminRoles.PERM_ADMIN_WRITE
        private val json = Json { encodeDefaults = true }
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val res = response as HttpServletResponse

        if (req.requestURI == LOGIN_PATH) {
            chain.doFilter(request, response)
            return
        }

        val token = req.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)
            ?.trim()
        if (token.isNullOrEmpty()) {
            respondUnauthorized(res)
            return
        }

        val adminId = try {
            jwt.parseAdminId(token)
        } catch (e: JwtException) {
            respondUnauthorized(res)
            return
        }

        val user = repository.findByIdOrNull(adminId)
        if (user == null || !user.enabled) {
            respondUnauthorized(res)
            return
        }

        val mutating = req.method != "GET"
        if (mutating && PERM_ADMIN_WRITE !in com.bugu.admin.app.AdminRoles.permsOf(user.role)) {
            respond(res, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN)
            return
        }

        req.setAttribute(ATTR_ADMIN_ID, adminId)
        req.setAttribute(ATTR_ADMIN_ROLE, user.role)
        chain.doFilter(request, response)
    }

    private fun respondUnauthorized(res: HttpServletResponse) =
        respond(res, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED)

    private fun respond(res: HttpServletResponse, status: HttpStatus, code: ErrorCode) {
        res.status = status.value()
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.characterEncoding = Charsets.UTF_8.name()
        res.writer.write(
            json.encodeToString(
                ApiResponse.serializer(String.serializer()),
                ApiResponse.fail(code),
            ),
        )
    }
}
