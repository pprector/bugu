package com.bugu.admin.api

import com.bugu.admin.infra.AdminAuditLogRepository
import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 后台鉴权集成测试(E9-S1):种子登录、错误口令、App/后台 token 互斥、me 视图、登录审计。
 * 种子密码来自默认配置 bugu.admin.seed-password(bugu-admin-2026)。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class AdminAuthIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val auditRepository: AdminAuditLogRepository,
    private val jdbc: JdbcTemplate,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }

    /** super 的 id(不要硬编码:共享开发库里 seed 重建后自增 id 会变)。 */
    private fun superId(): Long =
        jdbc.queryForObject("SELECT id FROM admin_user WHERE username = 'super'", Long::class.java)!!

    private fun adminLogin(username: String = "super", password: String = "bugu-admin-2026") =
        mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username": "$username", "password": "$password"}"""
        }

    private fun adminToken(): String {
        val body = adminLogin().andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""token"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
    }

    @Test
    fun `种子登录_OK_返回SUPER与权限点`() {
        adminLogin().andExpect {
            status { isOk() }
            jsonPath("$.data.user.role") { value("SUPER") }
            jsonPath("$.data.user.perms") { value(org.hamcrest.Matchers.hasItem("admin:write")) }
            jsonPath("$.data.user.username") { value("super") }
        }
    }

    @Test
    fun `错误口令_401`() {
        adminLogin(password = "wrong-password").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `me_需admin_token_返回账号视图`() {
        val token = adminToken()
        mockMvc.get("/api/v1/admin/auth/me") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.role") { value("SUPER") }
        }
    }

    @Test
    fun `App_token访问后台_401_后台token访问App端点_401_互斥`() {
        val adminToken = adminToken()
        val (a, _) = client.boundPair()

        // App token 访问后台
        mockMvc.get("/api/v1/admin/auth/me") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect { status { isUnauthorized() } }

        // 后台 token 访问 App 端点
        mockMvc.get("/api/v1/reminder/mine") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `无token访问后台_401`() {
        mockMvc.get("/api/v1/admin/auth/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `登录落审计`() {
        adminLogin().andExpect { status { isOk() } }
        val logs = auditRepository.findByAdminUserIdOrderByCreatedAtDesc(superId())
        assertTrue(logs.any { it.action == "LOGIN" }, "应有 LOGIN 审计记录")
        assertEquals("username=super", logs.first { it.action == "LOGIN" }.detail)
    }
}
