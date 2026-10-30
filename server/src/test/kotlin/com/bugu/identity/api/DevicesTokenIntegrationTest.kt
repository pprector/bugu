package com.bugu.identity.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.app.UserService
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.identity.infra.DeviceTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.random.Random

/**
 * devices/token 集成测试(E8-S5):上报落库、同 token upsert(换账号归属随最新登录者)、
 * 未登录 401、长辈模式可上报(升级链推送对长辈端同样生效)。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class DevicesTokenIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val userService: UserService,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }

    private fun report(token: String, pushToken: String, platform: String = "ANDROID") = mockMvc.post("/api/v1/devices/token") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"pushToken": "$pushToken", "platform": "$platform"}"""
    }

    @Test
    fun `上报落库带vendor与platform`() {
        val user = client.register()
        val pushToken = "reg-${Random.nextLong(1000000, 9999999)}"

        report(user.token, pushToken).andExpect { status { isOk() } }

        val row = requireNotNull(deviceTokenRepository.findByPushToken(pushToken))
        assertEquals(user.id, row.userId)
        assertEquals("ANDROID", row.platform)
        assertEquals("JIGUANG", row.vendor)
        assertTrue(row.pushEnabled)
    }

    @Test
    fun `同token重复上报为upsert且归属随最新登录者`() {
        val a = client.register()
        val b = client.register()
        val pushToken = "reg-${Random.nextLong(1000000, 9999999)}"

        report(a.token, pushToken).andExpect { status { isOk() } }
        report(b.token, pushToken).andExpect { status { isOk() } }

        val rows = deviceTokenRepository.findAll().toList().filter { it.pushToken == pushToken }
        assertEquals(1, rows.size)
        assertEquals(b.id, rows[0].userId)
    }

    @Test
    fun `未登录上报401`() {
        val pushToken = "reg-${Random.nextLong(1000000, 9999999)}"
        mockMvc.post("/api/v1/devices/token") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"pushToken": "$pushToken", "platform": "ANDROID"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `长辈模式可上报`() {
        val user = client.register()
        userService.setElderMode(user.id, true)
        val pushToken = "reg-${Random.nextLong(1000000, 9999999)}"

        report(user.token, pushToken).andExpect { status { isOk() } }
        val row = requireNotNull(deviceTokenRepository.findByPushToken(pushToken))
        assertEquals(user.id, row.userId)
    }
}
