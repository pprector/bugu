package com.bugu.identity.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch

/**
 * 个人资料编辑集成测试:改昵称成功、两字段全空拒绝、超长拒绝。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ProfileUpdateIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }

    @Test
    fun `改昵称成功且持久化`() {
        val user = client.register()

        mockMvc.patch("/api/v1/auth/me") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname": "新昵称"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.nickname") { value("新昵称") }
        }

        // 重新读取确认已落库
        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.nickname") { value("新昵称") }
        }
    }

    @Test
    fun `两字段全空拒绝`() {
        val user = client.register()

        mockMvc.patch("/api/v1/auth/me") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }
    }

    @Test
    fun `昵称超长拒绝`() {
        val user = client.register()

        mockMvc.patch("/api/v1/auth/me") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname": "${"长".repeat(33)}"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }
    }
}
