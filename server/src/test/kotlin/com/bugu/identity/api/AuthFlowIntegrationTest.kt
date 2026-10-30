package com.bugu.identity.api

import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import kotlin.random.Random
import kotlin.test.assertTrue

/**
 * 认证全链路集成测试:发码(假实现)→ 登录 → 携 JWT 访问 /me,以及限流与鉴权失败路径。
 * 手机号随机生成,避免残留 sms_code 限流状态影响重跑。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class AuthFlowIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val jdbcClient: JdbcClient,
) {

    private fun randomPhone(): String {
        val second = Random.nextInt(3, 10)
        val rest = (1..9).map { Random.nextInt(10) }.joinToString("")
        return "1$second$rest"
    }

    private fun sendCode(phone: String) = mockMvc.post("/api/v1/auth/send-code") {
        contentType = org.springframework.http.MediaType.APPLICATION_JSON
        content = """{"phone": "$phone"}"""
    }

    private fun login(phone: String, code: String) = mockMvc.post("/api/v1/auth/login") {
        contentType = org.springframework.http.MediaType.APPLICATION_JSON
        content = """{"phone": "$phone", "code": "$code"}"""
    }

    @Test
    fun `发码登录me全链路`() {
        val phone = randomPhone()

        sendCode(phone).andExpect {
            status { isOk() }
            jsonPath("$.code") { value("OK") }
        }
        val code = requireNotNull(smsSender.lastCode) { "假实现应记录验证码" }

        login(phone, "000000").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }

        val token = login(phone, code).andExpect {
            status { isOk() }
            jsonPath("$.data.user.phone") { value(phone) }
        }.andReturn().response.contentAsString.let {
            Regex(""""token"\s*:\s*"([^"]+)"""").find(it)!!.groupValues[1]
        }

        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.phone") { value(phone) }
        }

        // 验证码一次性:同一验证码第二次登录应失败
        login(phone, code).andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `无token访问me返回401`() {
        mockMvc.get("/api/v1/auth/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `60秒内第6次发码返回SMS_RATE_LIMITED且锁落库`() {
        val phone = randomPhone()
        repeat(5) {
            sendCode(phone).andExpect { status { isOk() } }
        }
        sendCode(phone).andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("SMS_RATE_LIMITED") }
        }
        // 锁必须落库:否则 60s 窗口过期后计数清零,限流即被绕过
        val locked = jdbcClient
            .sql("SELECT locked_until FROM sms_code WHERE phone = :phone")
            .param("phone", phone)
            .query { rs, _ -> rs.getTimestamp("locked_until") != null }
            .single()
        assertTrue(locked, "第 6 次发码后 locked_until 必须写入")
    }
}
