package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.ReminderFireRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 互叫状态互见(E5-S2):仅双方可见、仅「已起/未起」二元状态;
 * 单方 ack 后对方查得 peer=UP;非互叫类型 400、第三人 403。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class MutualStatusIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val reminderFireRepository: ReminderFireRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun mutualStatus(token: String, reminderId: Long) = mockMvc.get("/api/v1/reminder/$reminderId/mutual-status") {
        header("Authorization", "Bearer $token")
    }

    private fun ack(token: String, fireId: Long) = mockMvc.post("/api/v1/reminder/ack") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"fireId": $fireId, "action": "DONE"}"""
    }

    @Test
    fun `单方确认后互见状态为已起与未起`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fireB = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first { it.targetUserId == b.id }.id!!

        // 初始:双方均未起
        mutualStatus(a.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.self") { value("NOT_UP") }
            jsonPath("$.data.peer") { value("NOT_UP") }
        }

        ack(b.token, fireB).andExpect { status { isOk() } }

        // a 视角:自己未起,对方已起;b 视角相反
        mutualStatus(a.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.self") { value("NOT_UP") }
            jsonPath("$.data.peer") { value("UP") }
        }
        mutualStatus(b.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.self") { value("UP") }
            jsonPath("$.data.peer") { value("NOT_UP") }
        }
    }

    @Test
    fun `非互叫类型与第三人均被拒`() {
        val (a, b) = client.boundPair()
        val onceId = reminders.createAndGetId(a.token, b.id)
        mutualStatus(a.token, onceId).andExpect { status { isBadRequest() } }

        val mutualId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val outsider = client.register()
        mutualStatus(outsider.token, mutualId).andExpect { status { isForbidden() } }
        mutualStatus(outsider.token, 999999).andExpect { status { isNotFound() } }
    }
}
