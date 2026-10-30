package com.bugu.admin.api

import com.bugu.admin.infra.AdminAuditLogRepository
import com.bugu.admin.infra.AdminPasswordHasher
import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.api.ReminderTestClient
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
 * 运营查询与处理集成测试(E9-S2):用户筛选与长辈标记、绑定双方信息与解绑、
 * 提醒筛选与升级链日志、推送状态;OPS 写 403;写操作落审计。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.push.provider=console"])
@AutoConfigureMockMvc
class AdminOpsIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val auditRepository: AdminAuditLogRepository,
    private val jdbc: JdbcTemplate,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun adminLogin(username: String, password: String): String {
        val body = mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username": "$username", "password": "$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""token"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
    }

    private fun superToken(): String = adminLogin("super", "bugu-admin-2026")

    /** super 的 id(不要硬编码:共享开发库里 seed 重建后自增 id 会变)。 */
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
    }

    @Test
    fun `用户列表_筛选_长辈标记即生效且落审计`() {
        val (a, _) = client.boundPair()
        val token = superToken()

        mockMvc.get("/api/v1/admin/users") {
            header("Authorization", "Bearer $token")
            param("query", a.id.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].id") { value(a.id) }
            jsonPath("$.data[0].elderMode") { value(false) }
        }

        mockMvc.patch("/api/v1/admin/users/${a.id}/elder-mode") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled": true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.elderMode") { value(true) }
        }

        // 长辈模式下 App 端立即受限(E6 联动)
        mockMvc.get("/api/v1/reminder/mine") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect { status { isForbidden() } }

        assertTrue(
            auditRepository.findByAdminUserIdOrderByCreatedAtDesc(superId())
                .any { it.action == "USER_ELDER_MODE" && it.targetId == a.id.toString() },
        )
    }

    @Test
    fun `绑定列表带双方信息_admin解绑即时隔离`() {
        val (a, b) = client.boundPair()
        val token = superToken()

        val body = mockMvc.get("/api/v1/admin/bonds") {
            header("Authorization", "Bearer $token")
            param("query", a.id.toString())
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue("\"requesterName\"" in body)
        assertTrue("\"receiverName\"" in body)

        val bondId = Regex(""""id"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toLong()
        mockMvc.post("/api/v1/admin/bonds/$bondId/unbind") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }

        // 列表可见 UNBOUND
        val after = mockMvc.get("/api/v1/admin/bonds") {
            header("Authorization", "Bearer $token")
            param("query", a.id.toString())
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue("\"UNBOUND\"" in after)
    }

    @Test
    fun `提醒筛选_升级链日志_推送状态`() {
        val (a, b) = client.boundPair()
        val token = superToken()
        val reminderId = reminders.createAndGetId(a.token, b.id, "吃药")

        mockMvc.get("/api/v1/admin/reminders") {
            header("Authorization", "Bearer $token")
            param("kind", "ONCE")
            param("query", "吃药")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].id") { value(reminderId) }
            jsonPath("$.data[0].creatorId") { value(a.id) }
        }

        val escalations = mockMvc.get("/api/v1/admin/reminders/$reminderId/escalations") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue("\"fireId\"" in escalations)

        mockMvc.get("/api/v1/admin/push/status") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.channel") { value("ConsolePushChannel") }
        }
    }

    @Test
    fun `OPS只读_写操作403_SUPER可写`() {
        val (a, _) = client.boundPair()
        val ops = opsToken()
        val supers = superToken()

        // OPS 可读
        mockMvc.get("/api/v1/admin/users") {
            header("Authorization", "Bearer $ops")
        }.andExpect { status { isOk() } }

        // OPS 写 403
        mockMvc.patch("/api/v1/admin/users/${a.id}/elder-mode") {
            header("Authorization", "Bearer $ops")
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled": true}"""
        }.andExpect { status { isForbidden() } }

        // SUPER 写 OK
        mockMvc.patch("/api/v1/admin/users/${a.id}/elder-mode") {
            header("Authorization", "Bearer $supers")
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled": false}"""
        }.andExpect { status { isOk() } }
    }
}
