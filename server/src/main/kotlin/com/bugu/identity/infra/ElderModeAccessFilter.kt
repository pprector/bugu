package com.bugu.identity.infra

import com.bugu.shared.api.ApiResponse
import com.bugu.shared.api.ErrorCode
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
 * 长辈极简模式访问控制(FR-06):elder_mode=true 的账号只暴露「当前提醒 + 完成」能力,
 * 列表/设置/创建等业务端点一律 403。注册在 [JwtAuthFilter] 之后(order=2),
 * 未鉴权请求(无 userId attribute)放行交由后续链路处理。
 */
class ElderModeAccessFilter(private val userRepository: UserRepository) : Filter {

    companion object {
        /** 长辈模式允许访问的端点(方法 + 路径)。 */
        private val ALLOWED = setOf(
            "GET:/api/v1/reminder/current",
            "POST:/api/v1/reminder/ack",
            "GET:/api/v1/auth/me",
            "POST:/api/v1/auth/bind-phone",
            "POST:/api/v1/devices/token",
            "GET:/api/v1/feedback",
            "POST:/api/v1/feedback",
        )

        /** 长辈模式允许的带参端点(方法 + 路径正则):E7 触发上报,长辈端响铃后登记触发事实。 */
        private val ALLOWED_PATTERNS = listOf(
            "POST" to Regex("^/api/v1/reminder/fire/\\d+/report$"),
        )

        private val json = Json { encodeDefaults = true }
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val userId = req.getAttribute(JwtAuthFilter.ATTR_USER_ID) as? Long
        if (userId != null) {
            val user = userRepository.findByIdOrNull(userId)
            if (user?.elderMode == true) {
                val key = "${req.method}:${req.requestURI}"
                val allowed = key in ALLOWED ||
                    ALLOWED_PATTERNS.any { (method, pattern) -> method == req.method && pattern.matches(req.requestURI) }
                if (!allowed) {
                    respondForbidden(response as HttpServletResponse)
                    return
                }
            }
        }
        chain.doFilter(request, response)
    }

    private fun respondForbidden(res: HttpServletResponse) {
        res.status = HttpStatus.FORBIDDEN.value()
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.characterEncoding = Charsets.UTF_8.name()
        res.writer.write(
            json.encodeToString(
                ApiResponse.serializer(String.serializer()),
                ApiResponse.fail(ErrorCode.FORBIDDEN),
            ),
        )
    }
}
