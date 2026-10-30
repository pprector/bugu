package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.AckRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * 互叫闹钟触发模型与各自确认(E5-S1):
 * 创建 MUTUAL_ALARM 双方各一条 fire;ack 仅 DONE 出口、按 fire 目标校验;双方 DONE 后 reminder 才 ACKED。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class MutualAlarmIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val ackRepository: AckRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val reminderRepository: ReminderRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun fires(reminderId: Long) = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)

    private fun ack(token: String, fireId: Long, action: String, snoozeMinutes: Int? = null) =
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = buildString {
                append("""{"fireId": $fireId, "action": "$action"""")
                if (snoozeMinutes != null) append(""", "snoozeMinutes": $snoozeMinutes""")
                append("}")
            }
        }

    @Test
    fun `创建互叫闹钟_双方各一条fire且幂等键带用户`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM", voiceEnabled = true)

        val fires = fires(reminderId)
        assertEquals(2, fires.size)
        assertEquals(setOf(a.id, b.id), fires.map { it.targetUserId }.toSet())
        assertNotEquals(fires[0].idempotencyKey, fires[1].idempotencyKey)
        assertTrue(fires.all { it.idempotencyKey.startsWith("$reminderId:") })
        assertTrue(fires.all { it.state == "PENDING" })
    }

    @Test
    fun `单方确认只关自己_双方确认后reminder才ACKED`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fireA = fires(reminderId).first { it.targetUserId == a.id }.id!!
        val fireB = fires(reminderId).first { it.targetUserId == b.id }.id!!

        // b 先确认:只关自己,reminder 仍 FIRED
        ack(b.token, fireB, "DONE").andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.status") { value("FIRED") } }
        assertEquals("RESPONDED", reminderFireRepository.findById(fireB).get().state)
        assertEquals("PENDING", reminderFireRepository.findById(fireA).get().state)
        assertEquals("FIRED", reminderRepository.findById(reminderId).get().status)

        // a 确认后双方都起,reminder ACKED
        ack(a.token, fireA, "DONE").andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.status") { value("ACKED") } }
        assertEquals("ACKED", reminderRepository.findById(reminderId).get().status)
        assertEquals(2, fires(reminderId).count { ackRepository.existsByFireId(it.id!!) })
    }

    @Test
    fun `互叫闹钟无稍后与无法完成出口`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fireB = fires(reminderId).first { it.targetUserId == b.id }.id!!

        ack(b.token, fireB, "SNOOZE", snoozeMinutes = 10).andExpect { status { isBadRequest() } }
        ack(b.token, fireB, "DECLINE").andExpect { status { isBadRequest() } }
        assertEquals("PENDING", reminderFireRepository.findById(fireB).get().state)
    }

    @Test
    fun `只能确认自己的fire_重复确认幂等`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fireA = fires(reminderId).first { it.targetUserId == a.id }.id!!

        // b 不能确认 a 的 fire
        ack(b.token, fireA, "DONE").andExpect { status { isForbidden() } }

        repeat(2) { ack(a.token, fireA, "DONE").andExpect { status { isOk() } } }
        assertEquals(1, fires(reminderId).count { ackRepository.existsByFireId(it.id!!) })
    }

    @Test
    fun `修改触发时刻重建双fire`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val newFireAt = reminders.futureFireAt(5)

        reminders.patch(a.token, reminderId, """{"fireAt": "$newFireAt"}""")
            .andExpect { status { isOk() } }

        val fires = fires(reminderId)
        assertEquals(2, fires.size)
        assertEquals(setOf(a.id, b.id), fires.map { it.targetUserId }.toSet())
        assertTrue(fires.all { it.state == "PENDING" })
    }
}
