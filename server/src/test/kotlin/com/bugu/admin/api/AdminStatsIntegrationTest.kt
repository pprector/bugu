package com.bugu.admin.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * 运营总览集成测试:六项计数与直查 SQL 一致、新注册用户计入 userTotal、OPS 可读。
 * 用增量断言而非绝对值,避免受库内既有数据影响。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class AdminStatsIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val jdbc: JdbcTemplate,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }

    private fun stats(token: String): AdminStatsView {
        val body = mockMvc.get("/api/v1/admin/stats") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return AdminStatsView(
            userTotal = num(body, "userTotal"),
            userElder = num(body, "userElder"),
            bondActive = num(body, "bondActive"),
            reminderPending = num(body, "reminderPending"),
            fireToday = num(body, "fireToday"),
            aiGenToday = num(body, "aiGenToday"),
        )
    }

    private fun num(source: String, field: String): Long =
        Regex(""""$field"\s*:\s*(\d+)""").find(source)!!.groupValues[1].toLong()

    private fun directCounts(): AdminStatsView = AdminStatsView(
        userTotal = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long::class.java)!!,
        userElder = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE elder_mode = TRUE", Long::class.java)!!,
        bondActive = jdbc.queryForObject("SELECT COUNT(*) FROM bond WHERE status = 'ACTIVE'", Long::class.java)!!,
        reminderPending = jdbc.queryForObject("SELECT COUNT(*) FROM reminder WHERE status = 'PENDING'", Long::class.java)!!,
        fireToday = jdbc.queryForObject("SELECT COUNT(*) FROM reminder_fire WHERE fire_at::date = CURRENT_DATE", Long::class.java)!!,
        aiGenToday = jdbc.queryForObject("SELECT COUNT(*) FROM ai_gen_log WHERE created_at::date = CURRENT_DATE", Long::class.java)!!,
    )

    private fun adminLogin(username: String, password: String): String {
        val body = mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username": "$username", "password": "$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""token"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
    }

    private fun superToken(): String = adminLogin("super", "bugu-admin-2026")

    private fun opsToken(): String {
        val username = "ops-${System.nanoTime()}"
        jdbc.update(
            "INSERT INTO admin_user (username, password_hash, nickname, role, enabled, created_at) VALUES (?, ?, ?, 'OPS', TRUE, ?)",
            username, com.bugu.admin.infra.AdminPasswordHasher.hash("ops-pass-123"), "运营专员", LocalDateTime.now(),
        )
        return adminLogin(username, "ops-pass-123")
    }

    @Test
    fun `stats与直查一致_新用户计入userTotal`() {
        val token = superToken()
        val before = stats(token)

        assertEquals(directCounts(), before, "接口计数应与直查 SQL 一致")

        // 造数据:新注册一个 App 账号 → userTotal +1
        client.register()
        val after = stats(token)
        assertEquals(before.userTotal + 1, after.userTotal, "新注册用户应计入 userTotal")
    }

    @Test
    fun `OPS可读stats`() {
        val ops = opsToken()
        mockMvc.get("/api/v1/admin/stats") {
            header("Authorization", "Bearer $ops")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.userTotal") { exists() }
        }
    }
}
