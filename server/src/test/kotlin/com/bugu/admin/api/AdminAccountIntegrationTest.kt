package com.bugu.admin.api

import com.bugu.admin.infra.AdminAuditLogRepository
import com.bugu.admin.infra.AdminPasswordHasher
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * 后台账号管理集成测试:SUPER 创建成功、OPS 创建被拒、重名拒绝、停用后登录被拒、
 * 不能停用自己;写操作落审计。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class AdminAccountIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val auditRepository: AdminAuditLogRepository,
    private val jdbc: JdbcTemplate,
) {

    private fun adminLogin(username: String, password: String) =
        mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username": "$username", "password": "$password"}"""
        }

    private fun superToken(): String =
        adminLogin("super", "bugu-admin-2026")
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
            .let { Regex(""""token"\s*:\s*"([^"]+)"""").find(it)!!.groupValues[1] }

    private fun superId(): Long =
        jdbc.queryForObject("SELECT id FROM admin_user WHERE username = 'super'", Long::class.java)!!

    /** 造一个可登录的 OPS 账号(真 PBKDF2 hash)。 */
    private fun opsToken(): String {
        val username = "ops-${System.nanoTime()}"
        jdbc.update(
            "INSERT INTO admin_user (username, password_hash, nickname, role, enabled, created_at) VALUES (?, ?, ?, 'OPS', TRUE, ?)",
            username, AdminPasswordHasher.hash("ops-pass-123"), "运营专员", LocalDateTime.now(),
        )
        return adminLogin(username, "ops-pass-123")
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
            .let { Regex(""""token"\s*:\s*"([^"]+)"""").find(it)!!.groupValues[1] }
    }

    private fun createBody(username: String, password: String = "pass-12345", role: String = "OPS") =
        """{"username": "$username", "password": "$password", "nickname": "运营经理", "role": "$role"}"""

    @Test
    fun `SUPER创建成功_新账号可登录_落审计`() {
        val token = superToken()
        val username = "mgr-${System.nanoTime()}"

        mockMvc.post("/api/v1/admin/admin-users") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = createBody(username)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.username") { value(username) }
            jsonPath("$.data.role") { value("OPS") }
            jsonPath("$.data.enabled") { value(true) }
        }

        // 新账号用创建时的口令可登录
        adminLogin(username, "pass-12345").andExpect { status { isOk() } }

        assertTrue(
            auditRepository.findByAdminUserIdOrderByCreatedAtDesc(superId())
                .any { it.action == "ADMIN_ACCOUNT_CREATE" && it.detail?.contains(username) == true },
            "应有 ADMIN_ACCOUNT_CREATE 审计记录",
        )
    }

    @Test
    fun `OPS可读列表_创建被拒`() {
        val ops = opsToken()

        mockMvc.get("/api/v1/admin/admin-users") {
            header("Authorization", "Bearer $ops")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].username") { exists() }
        }

        mockMvc.post("/api/v1/admin/admin-users") {
            header("Authorization", "Bearer $ops")
            contentType = MediaType.APPLICATION_JSON
            content = createBody("nope-${System.nanoTime()}")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `重名拒绝`() {
        val token = superToken()

        mockMvc.post("/api/v1/admin/admin-users") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = createBody("super")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONFLICT") }
        }
    }

    @Test
    fun `停用后登录被拒_不能停用自己`() {
        val token = superToken()
        val username = "victim-${System.nanoTime()}"
        jdbc.update(
            "INSERT INTO admin_user (username, password_hash, nickname, role, enabled, created_at) VALUES (?, ?, ?, 'OPS', TRUE, ?)",
            username, AdminPasswordHasher.hash("victim-pass-1"), "待停用", LocalDateTime.now(),
        )
        val id = jdbc.queryForObject("SELECT id FROM admin_user WHERE username = ?", Long::class.java, username)!!

        mockMvc.patch("/api/v1/admin/admin-users/$id/enabled") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled": false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.enabled") { value(false) }
        }

        // 停用后登录被拒
        adminLogin(username, "victim-pass-1").andExpect { status { isUnauthorized() } }

        // 不能停用自己
        mockMvc.patch("/api/v1/admin/admin-users/${superId()}/enabled") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled": false}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONFLICT") }
        }
    }
}
