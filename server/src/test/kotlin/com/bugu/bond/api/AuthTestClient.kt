package com.bugu.bond.api

import com.bugu.identity.infra.ConsoleSmsSender
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.random.Random

/** 集成测试共用客户端:经验证码登录链路注册账号,绑定链路发起/确认。 */
data class TestUser(val id: Long, val token: String, val phone: String)

class AuthTestClient(private val mockMvc: MockMvc, private val smsSender: ConsoleSmsSender) {

    fun randomPhone(): String {
        val second = Random.nextInt(3, 10)
        val rest = (1..9).map { Random.nextInt(10) }.joinToString("")
        return "1$second$rest"
    }

    /** 走真实认证链路注册一个账号。 */
    fun register(): TestUser {
        val phone = randomPhone()
        sendCodeExpectOk(phone)
        val code = requireNotNull(smsSender.lastCode) { "假实现应记录验证码" }
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$phone", "code": "$code"}"""
        }.andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        val userId = Regex("""\"id\"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toLong()
        val token = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
        return TestUser(userId, token, phone)
    }

    /** 对任意手机号走一次发码(供 bind-phone 等场景取码)。 */
    fun sendCode(phone: String) = sendCodeExpectOk(phone)

    fun lastCode(): String = requireNotNull(smsSender.lastCode) { "假实现应记录验证码" }

    /** 注册一对账号并完成绑定,返回 (发起者, 接收者)。 */
    fun boundPair(): Pair<TestUser, TestUser> {
        val a = register()
        val b = register()
        val code = initiateAndGetCode(a.token)
        confirm(b.token, code).andExpect { status { isOk() } }
        return a to b
    }

    fun initiate(token: String) = mockMvc.post("/api/v1/bond/initiate") {
        header("Authorization", "Bearer $token")
    }

    fun initiateAndGetCode(token: String): String {
        val body = initiate(token).andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        return Regex(""""bindCode"\s*:\s*"(\d{6})"""").find(body)!!.groupValues[1]
    }

    fun confirm(token: String, code: String) = mockMvc.post("/api/v1/bond/confirm") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"code": "$code"}"""
    }

    private fun sendCodeExpectOk(phone: String) {
        mockMvc.post("/api/v1/auth/send-code") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$phone"}"""
        }.andExpect { status { isOk() } }
    }
}
