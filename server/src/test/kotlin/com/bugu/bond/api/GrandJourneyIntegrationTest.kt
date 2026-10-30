package com.bugu.bond.api

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
 * E1 端到端:一条链路串起 R1-R6——
 * 子女验证码注册登录(R1/R2)→ 发起绑定码(R3)→ 长辈设备凭码免注册接入直建 ACTIVE(R6)
 * → 子女代填长辈资料(R6)→ 长辈绑手机号升级正式账号(R6)→ 任一方解绑即时隔离(R4/R5)。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class GrandJourneyIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    smsSender: ConsoleSmsSender,
) {

    private val client = AuthTestClient(mockMvc, smsSender)

    @Test
    fun `子女注册到长辈接入再到解绑的完整旅程`() {
        // R1/R2:子女手机号验证码注册登录
        val child = client.register()
        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${child.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accountType") { value("NORMAL") }
        }

        // R3:发起绑定,得 6 位码 + 深链接
        val inviteCode = client.initiateAndGetCode(child.token)

        // R6:长辈设备凭邀请码免注册接入,直建 ACTIVE bond
        val elderBody = mockMvc.post("/api/v1/auth/elder-register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"deviceUuid": "journey-${client.randomPhone()}", "deviceModel": "Redmi-12C", "inviteCode": "$inviteCode"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.user.accountType") { value("ELDER") }
        }.andReturn().response.contentAsString
        val elderId = Regex("""\"id\"\s*:\s*(\d+)""").find(elderBody)!!.groupValues[1].toLong()
        val elderToken = Regex(""""token"\s*:\s*"([^"]+)"""").find(elderBody)!!.groupValues[1]

        // R6:子女代填长辈昵称与关系标签
        mockMvc.post("/api/v1/bond/elder-profile") {
            header("Authorization", "Bearer ${child.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"elderUserId": $elderId, "nickname": "妈妈", "relationTag": "妈妈"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.user.nickname") { value("妈妈") }
            jsonPath("$.data.relationTag") { value("妈妈") }
        }

        // R6:长辈绑手机号升级 NORMAL
        val elderPhone = client.randomPhone()
        client.sendCode(elderPhone)
        mockMvc.post("/api/v1/auth/bind-phone") {
            header("Authorization", "Bearer $elderToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$elderPhone", "code": "${client.lastCode()}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accountType") { value("NORMAL") }
            jsonPath("$.data.phone") { value(elderPhone) }
        }

        // R4/R5:任一方解绑,双方即时互相不可见
        val bondId = mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer ${child.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].status") { value("ACTIVE") }
        }.andReturn().response.contentAsString.let {
            Regex(""""id"\s*:\s*(\d+)""").find(it)!!.groupValues[1]
        }
        mockMvc.post("/api/v1/bond/$bondId/unbind") {
            header("Authorization", "Bearer $elderToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("UNBOUND") }
        }
        mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer ${child.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
        mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer $elderToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
    }
}
