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
 * 长辈免注册接入全链路:设备注册(幂等)→ 直建 ACTIVE bond → 代填资料 → 绑手机号升级,
 * 以及无效邀请码、手机号被占用等失败路径。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ElderFlowIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    smsSender: ConsoleSmsSender,
) {

    private val client = AuthTestClient(mockMvc, smsSender)

    private fun elderRegister(deviceUuid: String, deviceModel: String, inviteCode: String) =
        mockMvc.post("/api/v1/auth/elder-register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"deviceUuid": "$deviceUuid", "deviceModel": "$deviceModel", "inviteCode": "$inviteCode"}"""
        }

    private fun elderProfile(token: String, elderUserId: Long, nickname: String?, relationTag: String?) =
        mockMvc.post("/api/v1/bond/elder-profile") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"elderUserId": $elderUserId, "nickname": ${nickname?.let { "\"$it\"" }}, "relationTag": ${relationTag?.let { "\"$it\"" }}}"""
        }

    private fun bindPhone(token: String, phone: String, code: String) =
        mockMvc.post("/api/v1/auth/bind-phone") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$phone", "code": "$code"}"""
        }

    @Test
    fun `注册绑定代填升级全链路`() {
        val child = client.register()
        val inviteCode = client.initiateAndGetCode(child.token)

        // 长辈设备注册:免登录、免验证码,直建 ACTIVE bond(全站约定 explicitNulls=false,null 字段省略)
        val deviceUuid = "device-${client.randomPhone()}"
        val elderBody = elderRegister(deviceUuid, "Redmi-12C", inviteCode).andExpect {
            status { isOk() }
            jsonPath("$.data.user.accountType") { value("ELDER") }
            jsonPath("$.data.user.nickname") { value("家人") }
            jsonPath("$.data.user.phone") { doesNotExist() }
        }.andReturn().response.contentAsString
        val elderId = Regex("""\"id\"\s*:\s*(\d+)""").find(elderBody)!!.groupValues[1].toLong()
        val elderToken = Regex(""""token"\s*:\s*"([^"]+)"""").find(elderBody)!!.groupValues[1]

        // 发起方查询可见 ACTIVE,对方是长辈
        mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer ${child.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].status") { value("ACTIVE") }
            jsonPath("$.data[0].counterpartId") { value(elderId.toInt()) }
        }

        // 同设备重复注册:幂等直登,同一账号
        elderRegister(deviceUuid, "Redmi-12C", "000000").andExpect {
            status { isOk() }
            jsonPath("$.data.user.id") { value(elderId.toInt()) }
        }

        // 子女端代填:昵称 + 关系标签
        elderProfile(child.token, elderId, "妈妈", "妈妈").andExpect {
            status { isOk() }
            jsonPath("$.data.user.nickname") { value("妈妈") }
            jsonPath("$.data.relationTag") { value("妈妈") }
        }
        // 长辈侧 /me 可见新昵称
        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer $elderToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.nickname") { value("妈妈") }
        }

        // 长辈绑手机号升级 NORMAL
        val newPhone = client.randomPhone()
        client.sendCode(newPhone)
        bindPhone(elderToken, newPhone, client.lastCode()).andExpect {
            status { isOk() }
            jsonPath("$.data.accountType") { value("NORMAL") }
            jsonPath("$.data.phone") { value(newPhone) }
        }
        // 验证码一次性
        bindPhone(elderToken, newPhone, client.lastCode()).andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `无效邀请码拒绝注册`() {
        elderRegister("device-invalid-${client.randomPhone()}", "Model-X", "000000").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BOND_INVALID") }
        }
    }

    @Test
    fun `绑定的手机号被占用返回CONFLICT`() {
        val child = client.register()
        val inviteCode = client.initiateAndGetCode(child.token)
        val elderBody = elderRegister("device-taken-${client.randomPhone()}", "Model-Y", inviteCode)
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        val elderToken = Regex(""""token"\s*:\s*"([^"]+)"""").find(elderBody)!!.groupValues[1]

        // 试图绑子女已占用的手机号
        client.sendCode(child.phone)
        bindPhone(elderToken, child.phone, client.lastCode()).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONFLICT") }
        }
    }

    @Test
    fun `无绑定关系不能代填资料`() {
        val stranger = client.register()
        val other = client.register()
        elderProfile(stranger.token, other.id, "妈妈", null).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }
}
