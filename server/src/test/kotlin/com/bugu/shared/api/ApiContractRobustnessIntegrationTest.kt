package com.bugu.shared.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 接口契约健壮性(黑盒联验发现的 500 归属错位):
 * 调用方错误一律返回 ApiResponse + 语义化 ErrorCode —— 畸形体/类型不符/空体 400 INVALID_ARGUMENT,
 * 未匹配路径 404 NOT_FOUND;不得落到兜底 500 INTERNAL(端侧据此会把参数问题误判为服务端故障)。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiContractRobustnessIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    smsSender: ConsoleSmsSender,
) {

    private val client = AuthTestClient(mockMvc, smsSender)

    private fun postRaw(path: String, body: String) = mockMvc.post(path) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `畸形JSON返回400_INVALID_ARGUMENT而非500`() {
        postRaw("/api/v1/auth/login", """{"phone": "13800000000", "code": """).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }
    }

    @Test
    fun `字段类型不符返回400_INVALID_ARGUMENT`() {
        postRaw("/api/v1/auth/login", """{"phone": 13800000000, "code": "123456"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }
    }

    @Test
    fun `空请求体返回400_INVALID_ARGUMENT`() {
        postRaw("/api/v1/auth/send-code", "").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }
    }

    @Test
    fun `路径变量类型不符返回400_INVALID_ARGUMENT`() {
        val user = client.register()
        mockMvc.get("/api/v1/reminder/abc/wakeup-copy") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }
    }

    @Test
    fun `未知路径返回404_NOT_FOUND而非500`() {
        val user = client.register()
        mockMvc.get("/api/v1/not-exist") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }
}
