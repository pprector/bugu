package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.AckRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 长辈极简模式端到端集成测试(E6-S3):
 * 标记 → 限流访问 → current 可见 → ack DONE → revoke 回滚,全链路跑通。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ElderModeE2EIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val ackRepository: AckRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val reminderRepository: ReminderRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun setElderMode(token: String, peerId: Long, enabled: Boolean) =
        mockMvc.post("/api/v1/bond/elder-mode") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"peerId": $peerId, "enabled": $enabled}"""
        }

    private fun ack(token: String, fireId: Long, action: String) =
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "$action"}"""
        }

    @Test
    fun `标记_限流_current_ack_revoke 全链路`() {
        val (a, b) = client.elderPair()

        // 1. 子女 a 标记 b 为长辈模式
        setElderMode(a.token, b.id, enabled = true).andExpect {
            status { isOk() }
            jsonPath("$.data.elderMode") { value(true) }
        }

        // 2. 长辈 b 访问列表被拒 403
        mockMvc.get("/api/v1/reminder/mine") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect { status { isForbidden() } }

        // 3. 子女 a 创建提醒,长辈 b 的 current 可见
        val reminderId = reminders.createAndGetId(a.token, b.id, "吃降压药")
        val fireId = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!
        mockMvc.get("/api/v1/reminder/current") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(reminderId) }
            jsonPath("$.data.title") { value("吃降压药") }
        }

        // 4. 长辈 b ack DONE → reminder ACKED
        ack(b.token, fireId, "DONE").andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ACKED") }
        }
        assertEquals("RESPONDED", reminderFireRepository.findById(fireId).get().state)

        // 5. 子女 a revoke → reminder FIRED,fire FIRED,ack 删除
        val ackId = ackRepository.findByFireId(fireId)!!.id!!
        mockMvc.post("/api/v1/reminder/ack/$ackId/revoke") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("FIRED") }
        }

        assertEquals("FIRED", reminderFireRepository.findById(fireId).get().state)
        assertFalse(ackRepository.existsByFireId(fireId))
        assertEquals("FIRED", reminderRepository.findById(reminderId).get().status)

        // 6. 撤销后长辈 current 仍可见该提醒(回 FIRED 后重新待处理)
        mockMvc.get("/api/v1/reminder/current") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(reminderId) }
        }
    }
}
