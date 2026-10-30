package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.AckRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * 三出口确认集成测试(S1):DONE→ACKED、SNOOZE→SNOOZED+新建 fire、DECLINE→DECLINED,
 * 幂等、仅接收人可操作、参数校验。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class AckFlowIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val ackRepository: AckRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val reminderRepository: ReminderRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun firstFireId(reminderId: Long): Long =
        reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!

    private fun ack(token: String, fireId: Long, action: String, reasonCode: String? = null, snoozeMinutes: Int? = null) =
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = buildString {
                append("""{"fireId": $fireId, "action": "$action"""")
                if (reasonCode != null) append(""", "reasonCode": "$reasonCode"""")
                if (snoozeMinutes != null) append(""", "snoozeMinutes": $snoozeMinutes""")
                append("}")
            }
        }

    @Test
    fun `DONE确认_状态变ACKED且fire与ack落库`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        val body = ack(b.token, fireId, "DONE")
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue(body.contains(""""status":"ACKED""""))
        assertTrue(reminders.mine(a.token).contains(""""status":"ACKED""""))

        assertEquals("RESPONDED", reminderFireRepository.findById(fireId).get().state)
        val ackRow = ackRepository.findByFireId(fireId)
        assertNotNull(ackRow)
        assertEquals("DONE", ackRow!!.action)
    }

    @Test
    fun `SNOOZE确认_状态变SNOOZED且新建fire`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)
        val before = LocalDateTime.now()

        val body = ack(b.token, fireId, "SNOOZE", snoozeMinutes = 10)
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue(body.contains(""""status":"SNOOZED""""))

        assertEquals("RESPONDED", reminderFireRepository.findById(fireId).get().state)
        val ackRow = ackRepository.findByFireId(fireId)
        assertEquals(10, ackRow!!.snoozeMinutes)

        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        assertEquals(2, fires.size)
        val newFire = fires.first { it.state == "PENDING" }
        assertTrue(newFire.fireAt.isAfter(before.plusMinutes(9)))
        assertTrue(newFire.fireAt.isBefore(before.plusMinutes(11)))
        assertEquals("$reminderId:${newFire.fireAt}", newFire.idempotencyKey)
    }

    @Test
    fun `SNOOZE后新fire再DONE_闭环ACKED且两条ack互不覆盖`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val firstFire = firstFireId(reminderId)

        ack(b.token, firstFire, "SNOOZE", snoozeMinutes = 10).andExpect { status { isOk() } }
        assertEquals("SNOOZED", reminderRepository.findById(reminderId).get().status)

        val newFire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
            .first { it.state == "PENDING" }

        ack(b.token, newFire.id!!, "DONE")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.status") { value("ACKED") } }

        assertEquals("SNOOZE", ackRepository.findByFireId(firstFire)!!.action)
        assertEquals("DONE", ackRepository.findByFireId(newFire.id!!)!!.action)
        assertEquals("RESPONDED", reminderFireRepository.findById(newFire.id!!).get().state)
        assertEquals("ACKED", reminderRepository.findById(reminderId).get().status)
        assertTrue(reminders.mine(a.token).contains(""""status":"ACKED""""))
    }

    @Test
    fun `DECLINE确认_状态变DECLINED且带原因`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        ack(b.token, fireId, "DECLINE", reasonCode = "BUSY")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.status") { value("DECLINED") } }

        assertEquals("BUSY", ackRepository.findByFireId(fireId)!!.reasonCode)
        assertEquals("DECLINED", reminderRepository.findById(reminderId).get().status)
    }

    @Test
    fun `同一fire重复ack幂等`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        repeat(3) { ack(b.token, fireId, "DONE").andExpect { status { isOk() } } }

        val ackCount = ackRepository.count()
        // 整个测试库可能有其他 ack,验证该 fire 只有一条
        assertEquals(1, ackRepository.findByFireId(fireId)?.let { 1 } ?: 0)
        assertEquals("ACKED", reminderRepository.findById(reminderId).get().status)
    }

    @Test
    fun `非接收人ack返回403`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        ack(a.token, fireId, "DONE").andExpect { status { isForbidden() } }
    }

    @Test
    fun `确认参数校验失败均400`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        ack(b.token, fireId, "UNKNOWN").andExpect { status { isBadRequest() } }
        ack(b.token, fireId, "DECLINE").andExpect { status { isBadRequest() } }
        ack(b.token, fireId, "DECLINE", reasonCode = "WRONG").andExpect { status { isBadRequest() } }
        ack(b.token, fireId, "SNOOZE").andExpect { status { isBadRequest() } }
        ack(b.token, fireId, "SNOOZE", snoozeMinutes = 0).andExpect { status { isBadRequest() } }
        ack(b.token, fireId, "SNOOZE", snoozeMinutes = 99999).andExpect { status { isBadRequest() } }
    }
}
