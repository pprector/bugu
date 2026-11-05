package com.bugu.identity.infra

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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.data.repository.findByIdOrNull
import com.bugu.shared.api.ApiResponse
import com.bugu.shared.api.ErrorCode

/**
 * JWT 鉴权过滤器:拦截 /api/v1 下的业务路径,放行注册/登录/系统自检与文档路径。
 * 校验通过后把 userId 放入 request attribute,由 [CurrentUserResolver] 注入控制器参数。
 * 每次请求回查账号:不存在、非 ACTIVE 或 token 版本不一致一律 401(停用/吊销即时生效)。
 */
class JwtAuthFilter(
    private val jwt: JwtTokenService,
    private val repository: UserRepository,
) : Filter {

    companion object {
        const val ATTR_USER_ID = "bugu.userId"

        /** 精确放行的公开接口(注意 /me 受保护,不能整前缀放行)。 */
        private val PUBLIC_PATHS = setOf(
            "/api/v1/auth/send-code",
            "/api/v1/auth/login",
            "/api/v1/auth/elder-register",
        )

        /** 前缀放行清单:系统自检与文档/运维路径;admin 前缀由 AdminAuthFilter(order=3)自行鉴权(E9)。 */
        private val PUBLIC_PREFIXES = listOf(
            "/api/v1/system/",
            "/api/v1/admin/",
            "/actuator",
            "/docs",
            "/api-docs",
            "/v3/api-docs",
            "/swagger-ui",
        )

        private val json = Json { encodeDefaults = true }
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val res = response as HttpServletResponse

        val isPublic = req.requestURI in PUBLIC_PATHS || PUBLIC_PREFIXES.any { req.requestURI.startsWith(it) }
        if (isPublic) {
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

        val subject = try {
            jwt.parseUserSubject(token)
        } catch (e: JwtException) {
            respondUnauthorized(res)
            return
        }

        val user = repository.findByIdOrNull(subject.id)
        if (user == null || user.accountState != "ACTIVE" || user.tokenVersion != subject.tokenVersion) {
            respondUnauthorized(res)
            return
        }

        req.setAttribute(ATTR_USER_ID, subject.id)
        chain.doFilter(request, response)
    }

    private fun respondUnauthorized(res: HttpServletResponse) {
        res.status = HttpStatus.UNAUTHORIZED.value()
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.characterEncoding = Charsets.UTF_8.name()
        val body = json.encodeToString(
            ApiResponse.serializer(String.serializer()),
            ApiResponse.fail(ErrorCode.UNAUTHORIZED),
        )
        res.writer.write(body)
    }
}
