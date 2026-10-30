package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import com.bugu.scheduling.EscalationScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * 升级链端到端集成测试(S4):创建 → ack 三出口(DONE/SNOOZE/DECLINE)→
 * SNOOZE 新 fire → T1 再推 → T2 升级 → 24h 收口,固定时钟推进。
 */
@SpringBootTest(
    properties = [
        "bugu.sms.provider=console",
        "spring.main.allow-bean-definition-overriding=true",
        "bugu.escalation.scan-delay=3600000",
        "bugu.escalation.lock-at-least=PT0S",
    ],
)
@AutoConfigureMockMvc
class EscalationE2EIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val scanner: EscalationScanner,
    private val reminderRepository: ReminderRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val jdbc: JdbcTemplate,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @BeforeEach
    fun cleanStaleFires() {
        val ids = reminderFireRepository.findByState("PENDING").mapNotNull { it.id }
        if (ids.isNotEmpty()) {
            escalationLogRepository.deleteByFireIdIn(ids)
            reminderFireRepository.deleteAllById(ids)
        }
        jdbc.update("DELETE FROM shedlock WHERE name = 'escalation-scan'")
    }

    private fun firstFireId(reminderId: Long): Long =
        reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!

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

    private fun setFireAt(fireId: Long, fireAt: java.time.LocalDateTime) {
        val fire = reminderFireRepository.findById(fireId).get()
        reminderFireRepository.save(fire.copy(fireAt = fireAt))
    }

    @Test
    fun `ackDONE_终态不升级`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        ack(b.token, fireId, "DONE").andExpect { status { isOk() } }

        // 推进到 T2,扫描不应产生 escalation_log
        setFireAt(fireId, java.time.LocalDateTime.now().minusMinutes(31))
        scanner.doScan()

        assertEquals("ACKED", reminderRepository.findById(reminderId).get().status)
        assertTrue(escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).isEmpty())
    }

    @Test
    fun `ackDECLINE_终态不升级`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        // DECLINE 缺 reasonCode → 400
        ack(b.token, fireId, "DECLINE").andExpect { status { isBadRequest() } }

        // 带 reasonCode 的 DECLINE
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer ${b.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "DECLINE", "reasonCode": "BUSY"}"""
        }.andExpect { status { isOk() } }

        setFireAt(fireId, java.time.LocalDateTime.now().minusMinutes(31))
        scanner.doScan()

        assertEquals("DECLINED", reminderRepository.findById(reminderId).get().status)
        assertTrue(escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).isEmpty())
    }

    @Test
    fun `ackSNOOZE_新fire经T1T2到24h收口`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        // SNOOZE 10 分钟:原 fire RESPONDED,新建 fire(fireAt 更早,排前)
        ack(b.token, fireId, "SNOOZE", snoozeMinutes = 10).andExpect { status { isOk() } }
        assertEquals("SNOOZED", reminderRepository.findById(reminderId).get().status)
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        assertEquals(2, fires.size)
        val snoozeFireId = fires[0].id!! // SNOOZE fire fireAt=now+10min,排前
        assertEquals("PENDING", fires[0].state)
        assertEquals("RESPONDED", fires[1].state) // 原 fire

        // 推进 SNOOZE fire 到 T1 窗口
        setFireAt(snoozeFireId, java.time.LocalDateTime.now().minusMinutes(16))
        scanner.doScan()
        var stages = escalationLogRepository.findByFireIdOrderByCreatedAt(snoozeFireId).map { it.stage }
        assertEquals(listOf("T1"), stages)

        // 推进到 T2 窗口
        setFireAt(snoozeFireId, java.time.LocalDateTime.now().minusMinutes(31))
        scanner.doScan()
        stages = escalationLogRepository.findByFireIdOrderByCreatedAt(snoozeFireId).map { it.stage }
        assertEquals(listOf("T1", "T2"), stages)

        // 推进到 24h 收口
        setFireAt(snoozeFireId, java.time.LocalDateTime.now().minusHours(25))
        scanner.doScan()
        assertEquals("MISSED", reminderRepository.findById(reminderId).get().status)
        assertEquals("CLOSED", reminderFireRepository.findById(snoozeFireId).get().state)
        stages = escalationLogRepository.findByFireIdOrderByCreatedAt(snoozeFireId).map { it.stage }
        assertEquals(listOf("T1", "T2", "CLOSE"), stages)
    }

    @Test
    fun `未ack_fire经T1T2到24h收口`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        setFireAt(fireId, java.time.LocalDateTime.now().minusMinutes(16))
        scanner.doScan()
        assertEquals(listOf("T1"), escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).map { it.stage })

        setFireAt(fireId, java.time.LocalDateTime.now().minusMinutes(31))
        scanner.doScan()
        assertEquals(listOf("T1", "T2"), escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).map { it.stage })

        setFireAt(fireId, java.time.LocalDateTime.now().minusHours(25))
        scanner.doScan()
        assertEquals("MISSED", reminderRepository.findById(reminderId).get().status)
        assertEquals("CLOSED", reminderFireRepository.findById(fireId).get().state)
        assertEquals(
            listOf("T1", "T2", "CLOSE"),
            escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).map { it.stage },
        )
    }
}
