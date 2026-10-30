package com.bugu.bond.api

import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.api.ReminderTestClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 长辈极简模式标记与 API 访问控制(E6-S1):
 * 子女标记对方 elder_mode 后即时生效,长辈仅可访问 current/ack/me/bind-phone,其余 403;取消后恢复。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ElderModeIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun setElderMode(token: String, peerId: Long, enabled: Boolean) =
        mockMvc.post("/api/v1/bond/elder-mode") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"peerId": $peerId, "enabled": $enabled}"""
        }

    @Test
    fun `标记长辈模式后对方仅可访问白名单端点`() {
        val (a, b) = client.boundPair()
        setElderMode(a.token, b.id, enabled = true).andExpect {
            status { isOk() }
            jsonPath("$.data.elderMode") { value(true) }
        }

        // 长辈访问列表/设置被拒 403
        mockMvc.get("/api/v1/reminder/mine") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/reminder/incoming") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/reminder/mute") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect { status { isForbidden() } }

        // 白名单端点可达
        mockMvc.get("/api/v1/reminder/current") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.elderMode") { value(true) }
        }

        // 子女端不受影响
        mockMvc.get("/api/v1/reminder/mine") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `取消长辈模式后恢复全量访问`() {
        val (a, b) = client.boundPair()
        setElderMode(a.token, b.id, enabled = true)
        setElderMode(a.token, b.id, enabled = false).andExpect {
            status { isOk() }
            jsonPath("$.data.elderMode") { value(false) }
        }
        mockMvc.get("/api/v1/reminder/mine") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `非绑定对方不能标记_自己不能标记自己`() {
        val (a, b) = client.boundPair()
        val outsider = client.register()

        // 无绑定关系标记被拒
        setElderMode(outsider.token, b.id, enabled = true).andExpect { status { isForbidden() } }
        // 标记自己被拒
        setElderMode(a.token, a.id, enabled = true).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `current返回最早待处理提醒_无则null`() {
        val (a, b) = client.boundPair()
        setElderMode(a.token, b.id, enabled = true)

        // 无提醒时 current 返回 null(explicitNulls=false,data 字段省略)
        mockMvc.get("/api/v1/reminder/current") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { doesNotExist() }
        }

        // 建两条提醒,current 返回最早一条
        val earlierBody = reminders.create(a.token, b.id, "吃药", fireAt = reminders.futureFireAt(2))
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        val earlier = Regex(""""id"\s*:\s*(\d+)""").find(earlierBody)!!.groupValues[1].toLong()
        reminders.create(a.token, b.id, "喝水", fireAt = reminders.futureFireAt(5))
            .andExpect { status { isOk() } }
        mockMvc.get("/api/v1/reminder/current") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(earlier) }
            jsonPath("$.data.title") { value("吃药") }
        }
    }
}
