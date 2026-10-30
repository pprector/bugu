package com.bugu.identity.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * COS 未配置(强制要求 OSS):凭证申请直接报 INTERNAL 与明确提示,不发半可用凭证。
 */
// 显式置空 COS 配置模拟「未配置」环境,不依赖 application.yml 的真实状态
@SpringBootTest(
    properties = [
        "bugu.sms.provider=console",
        "bugu.cos.secret-id=",
        "bugu.cos.secret-key=",
        "bugu.cos.bucket=",
    ],
)
@AutoConfigureMockMvc
class AvatarUploadUnconfiguredIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }

    @Test
    fun `COS 未配置时凭证申请报内部错误`() {
        val user = client.register()

        mockMvc.post("/api/v1/auth/me/avatar/token") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/png"}"""
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL") }
            jsonPath("$.message") { value("对象存储(COS)未配置,请在服务端 bugu.cos 填入凭证") }
        }
    }
}
