package com.bugu.identity.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.domain.AvatarPolicy
import com.bugu.identity.infra.ConsoleSmsSender
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.Base64

/**
 * 头像 COS 签名直传集成测试:凭证下发(policy 结构与对象 key)、确认落库回读、
 * 类型白名单拒绝、越权/非法对象拒绝、未登录拒绝。
 * 测试凭证为假值:签名是纯本地计算,无需真实 COS 即可闭环。
 */
@SpringBootTest(
    properties = [
        "bugu.sms.provider=console",
        "bugu.cos.secret-id=test-id",
        "bugu.cos.secret-key=test-secret",
        "bugu.cos.bucket=test-1250000000",
        "bugu.cos.region=ap-guangzhou",
    ],
)
@AutoConfigureMockMvc
class AvatarUploadIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }

    @Test
    fun `凭证下发与确认落库回读头像地址`() {
        val user = client.register()

        val body = mockMvc.post("/api/v1/auth/me/avatar/token") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/png"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("OK") }
            jsonPath("$.data.uploadUrl") { value("https://test-1250000000.cos.ap-guangzhou.myqcloud.com/") }
            jsonPath("$.data.objectKey") { value(matchesPattern("avatar/${user.id}/\\d+\\.png")) }
            jsonPath("$.data.publicUrl") {
                value(matchesPattern("https://test-1250000000\\.cos\\.ap-guangzhou\\.myqcloud\\.com/avatar/${user.id}/\\d+\\.png"))
            }
            jsonPath("$.data.maxBytes") { value(AvatarPolicy.MAX_BYTES.toInt()) }
            jsonPath("$.data.formFields['q-signature']") { exists() }
            jsonPath("$.data.formFields['q-ak']") { value("test-id") }
        }.andReturn().response.contentAsString

        // policy 结构:绑桶 + eq 绑 key + content-length-range 上限
        val data = Json.parseToJsonElement(body).jsonObject.getValue("data").jsonObject
        val formFields = data.getValue("formFields").jsonObject
        val policy = Json.parseToJsonElement(
            Base64.getDecoder().decode(formFields.getValue("policy").jsonPrimitive.content).decodeToString(),
        ).jsonObject
        val conditions = policy.getValue("conditions").jsonArray
        assertEquals("test-1250000000", conditions[0].jsonObject.getValue("bucket").jsonPrimitive.content)
        // COS 强制要求:q-sign-algorithm / q-ak / q-sign-time 三条件缺一不可
        assertEquals("sha1", conditions[1].jsonObject.getValue("q-sign-algorithm").jsonPrimitive.content)
        assertEquals("test-id", conditions[2].jsonObject.getValue("q-ak").jsonPrimitive.content)
        assertEquals(formFields.getValue("q-key-time").jsonPrimitive.content, conditions[3].jsonObject.getValue("q-sign-time").jsonPrimitive.content)
        assertEquals(listOf("eq", "\$key", data.getValue("objectKey").jsonPrimitive.content), conditions[4].jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("content-length-range", "1", "${AvatarPolicy.MAX_BYTES}"), conditions[5].jsonArray.map { it.jsonPrimitive.content })

        // 确认落库:avatarUrl 为 COS 公网地址,/me 回读一致
        val objectKey = data.getValue("objectKey").jsonPrimitive.content
        val publicUrl = "https://test-1250000000.cos.ap-guangzhou.myqcloud.com/$objectKey"
        mockMvc.post("/api/v1/auth/me/avatar/confirm") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"objectKey":"$objectKey"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.avatarUrl") { value(publicUrl) }
        }
        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.avatarUrl") { value(publicUrl) }
        }
    }

    @Test
    fun `类型白名单外拒绝`() {
        val user = client.register()

        mockMvc.post("/api/v1/auth/me/avatar/token") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/gif"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
            jsonPath("$.message") { value("仅支持 JPG/PNG/WebP 图片") }
        }
    }

    @Test
    fun `确认越权与非法对象拒绝`() {
        val other = client.register()
        val user = client.register()

        // 形状合法但属于他人 → 403
        mockMvc.post("/api/v1/auth/me/avatar/confirm") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"objectKey":"avatar/${other.id}/1.png"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }

        // 路径穿越/任意 URL 注入 → 400
        for (bad in listOf("avatar/${user.id}/../../x.png", "https://evil.example.com/x.png", "avatar/${user.id}/x.gif")) {
            mockMvc.post("/api/v1/auth/me/avatar/confirm") {
                header("Authorization", "Bearer ${user.token}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"objectKey":"$bad"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_ARGUMENT") }
            }
        }
    }

    @Test
    fun `未登录拒绝`() {
        mockMvc.post("/api/v1/auth/me/avatar/token") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/png"}"""
        }.andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/v1/auth/me/avatar/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"objectKey":"avatar/1/1.png"}"""
        }.andExpect { status { isUnauthorized() } }
    }
}
