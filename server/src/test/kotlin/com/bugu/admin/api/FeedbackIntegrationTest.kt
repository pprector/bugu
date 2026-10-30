package com.bugu.admin.api

import com.bugu.admin.infra.AdminPasswordHasher
import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Assertions.assertFalse
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

@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.push.provider=console"])
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class FeedbackIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val jdbc: JdbcTemplate,
) {
    private val client by lazy { AuthTestClient(mockMvc, smsSender) }

    private fun adminToken(): String {
        val username = "feedback-${System.nanoTime()}"
        jdbc.update(
            "INSERT INTO admin_user (username, password_hash, nickname, role, enabled, created_at) VALUES (?, ?, ?, 'SUPER', TRUE, ?)",
            username, AdminPasswordHasher.hash("feedback-pass"), "feedback", LocalDateTime.now(),
        )
        val response = mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"feedback-pass"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""token"\s*:\s*"([^"]+)"""").find(response)!!.groupValues[1]
    }

    @Test
    fun `用户可创建并仅查询自己的反馈_长辈模式也可用`() {
        val user = client.register()
        jdbc.update("UPDATE app_user SET elder_mode = TRUE WHERE id = ?", user.id)
        mockMvc.post("/api/v1/feedback") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"SUGGESTION","content":"  希望增加语音提醒功能  ","appVersion":"1.2.3"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.category") { value("SUGGESTION") }
            jsonPath("$.data.content") { value("希望增加语音提醒功能") }
            jsonPath("$.data.status") { value("OPEN") }
        }
        mockMvc.get("/api/v1/feedback") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
        }
    }

    @Test
    fun `管理员分页处理反馈且用户端不暴露手机号`() {
        val user = client.register()
        val previousTotal = jdbc.queryForObject("SELECT COUNT(*) FROM feedback WHERE status = 'OPEN'", Long::class.java)!!
        mockMvc.post("/api/v1/feedback") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"ACCOUNT","content":"账号登录遇到问题无法完成验证"}"""
        }.andExpect { status { isOk() } }
        val admin = adminToken()
        val listed = mockMvc.get("/api/v1/admin/feedback") {
            header("Authorization", "Bearer $admin")
            param("page", "1")
            param("size", "20")
            param("status", "OPEN")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.total") { value(previousTotal + 1) }
            jsonPath("$.data.items[0].userId") { value(user.id) }
        }.andReturn().response.contentAsString
        val id = Regex(""""id"\s*:\s*(\d+)""").find(listed)!!.groupValues[1]
        mockMvc.patch("/api/v1/admin/feedback/$id") {
            header("Authorization", "Bearer $admin")
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"RESOLVED","reply":"已协助处理"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("RESOLVED") }
            jsonPath("$.data.reply") { value("已协助处理") }
        }
        val mine = mockMvc.get("/api/v1/feedback") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertFalse(user.phone in mine)
        assertTrue("updatedAt" in mine)
    }

    @Test
    fun `反馈认证与管理员只读权限受过滤器保护`() {
        mockMvc.get("/api/v1/feedback").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/feedback") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"OTHER","content":"这是一个有效反馈内容"}"""
        }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/admin/feedback").andExpect { status { isUnauthorized() } }
        val user = client.register()
        mockMvc.get("/api/v1/admin/feedback") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect { status { isUnauthorized() } }
        val admin = adminToken()
        jdbc.update("UPDATE admin_user SET role = 'OPS' WHERE username LIKE 'feedback-%'")
        mockMvc.get("/api/v1/admin/feedback") {
            header("Authorization", "Bearer $admin")
        }.andExpect { status { isOk() } }
        mockMvc.patch("/api/v1/admin/feedback/1") {
            header("Authorization", "Bearer $admin")
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"RESOLVED","reply":"已处理"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `用户最近50条倒序与验证边界`() {
        val user = client.register()
        for (index in 1..51) {
            mockMvc.post("/api/v1/feedback") {
                header("Authorization", "Bearer ${user.token}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"category":"REMINDER","content":"这是一个有效反馈内容$index"}"""
            }.andExpect { status { isOk() } }
        }
        mockMvc.get("/api/v1/feedback") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(50) }
            jsonPath("$.data[0].content") { value("这是一个有效反馈内容51") }
        }
        for (body in listOf(
            """{"category":"OTHER","content":" short "}""",
            """{"category":"OTHER","content":"${"字".repeat(2001)}"}""",
            """{"category":"OTHER","content":"这是一个有效反馈内容","appVersion":"${"v".repeat(65)}"}""",
        )) {
            mockMvc.post("/api/v1/feedback") {
                header("Authorization", "Bearer ${user.token}")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isBadRequest() } }
        }
        val admin = adminToken()
        for (body in listOf(
            """{"status":"RESOLVED","reply":"  "}""",
            """{"status":"INVALID"}""",
            """{"status":"PROCESSING","reply":"${"字".repeat(2001)}"}""",
        )) {
            mockMvc.patch("/api/v1/admin/feedback/1") {
                header("Authorization", "Bearer $admin")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isBadRequest() } }
        }
        mockMvc.get("/api/v1/admin/feedback") {
            header("Authorization", "Bearer $admin")
            param("page", "0")
        }.andExpect { status { isBadRequest() } }
        mockMvc.patch("/api/v1/admin/feedback/9223372036854775807") {
            header("Authorization", "Bearer $admin")
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"PROCESSING"}"""
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `反馈校验和按用户隔离`() {
        val first = client.register()
        val second = client.register()
        mockMvc.post("/api/v1/feedback") {
            header("Authorization", "Bearer ${first.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"UNKNOWN","content":"short"}"""
        }.andExpect { status { isBadRequest() } }
        mockMvc.post("/api/v1/feedback") {
            header("Authorization", "Bearer ${first.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"OTHER","content":"这是一个有效反馈内容"}"""
        }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/feedback") {
            header("Authorization", "Bearer ${second.token}")
        }.andExpect { status { isOk() }; jsonPath("$.data.length()") { value(0) } }
    }
}
