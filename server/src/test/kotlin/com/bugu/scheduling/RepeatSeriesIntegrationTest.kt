package com.bugu.scheduling

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.api.ReminderTestClient
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
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
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * 重复提醒续期(E10 决策 2026-10-27:确认后与错过收口后都续期)。
 *
 * 覆盖三条主路径:DAILY 确认后续到次日且可继续确认;WEEKLY 下一次落在允许星期;
 * 24h 收口 MISSED 后依然续期,保证「每天提醒」系列不因漏一天而终止。
 */
@SpringBootTest(
    properties = [
        "bugu.sms.provider=console",
        "spring.task.scheduling.enabled=false",
        "bugu.escalation.scan-delay=3600000",
        "bugu.escalation.lock-at-least=PT0S",
    ],
)
@AutoConfigureMockMvc
class RepeatSeriesIntegrationTest @Autowired constructor(
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

    /** 清掉历史遗留的待处理 fire(都会进扫描候选),避免干扰本轮扫描。 */
    @BeforeEach
    fun cleanStaleFires() {
        val ids = (reminderFireRepository.findByState("PENDING") + reminderFireRepository.findByState("FIRED"))
            .mapNotNull { it.id }
        if (ids.isNotEmpty()) {
            escalationLogRepository.deleteByFireIdIn(ids)
            reminderFireRepository.deleteAllById(ids)
        }
        jdbc.update("DELETE FROM shedlock WHERE name = 'escalation-scan'")
    }

    private fun ack(token: String, fireId: Long) = mockMvc.post("/api/v1/reminder/ack") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"fireId": $fireId, "action": "DONE"}"""
    }

    @Test
    fun `DAILY确认后续期到次日_系列可继续确认`() {
        val (a, b) = client.boundPair()
        val fireAt = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0)
        val reminderId = reminders.createAndGetId(
            a.token, b.id, title = "每天吃降压药", kind = "REPEAT",
            fireAt = fireAt.toString(), repeatRule = "DAILY",
        )
        val firstFire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()

        ack(b.token, firstFire.id!!).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ACKED") }
        }

        // 本次已完成,但系列回到待触发:fire_at 前推一天
        val row = reminderRepository.findById(reminderId).get()
        assertEquals("PENDING", row.status)
        assertEquals(fireAt.plusDays(1), row.fireAt)

        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        assertEquals(2, fires.size)
        assertEquals("RESPONDED", fires[0].state)
        assertEquals("PENDING", fires[1].state)
        assertEquals(fireAt.plusDays(1), fires[1].fireAt)
        assertEquals("$reminderId:${fireAt.plusDays(1)}", fires[1].idempotencyKey)

        // 下一次仍可确认,并继续续期
        ack(b.token, fires[1].id!!).andExpect { status { isOk() } }
        assertEquals(3, reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).size)
    }

    @Test
    fun `WEEKLY确认后下一次落在允许的星期`() {
        val (a, b) = client.boundPair()
        val fireAt = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)
        val reminderId = reminders.createAndGetId(
            a.token, b.id, title = "周一三五锻炼", kind = "REPEAT",
            fireAt = fireAt.toString(), repeatRule = "WEEKLY:MON,WED,FRI",
        )
        val firstFire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()

        ack(b.token, firstFire.id!!).andExpect { status { isOk() } }

        val row = reminderRepository.findById(reminderId).get()
        assertEquals("PENDING", row.status)
        assertTrue(row.fireAt.isAfter(fireAt))
        assertTrue(
            row.fireAt.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            "下一次应落在允许的星期,实际=${row.fireAt.dayOfWeek}",
        )
    }

    @Test
    fun `24h收口MISSED后仍续期_系列不断`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(
            a.token, b.id, title = "每天量血压", kind = "REPEAT", repeatRule = "DAILY",
        )
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()

        // 模拟服务停机 25 小时:提醒与 fire 都停在过去
        val past = LocalDateTime.now().minusHours(25).withNano(0)
        jdbc.update("UPDATE reminder SET fire_at = ? WHERE id = ?", Timestamp.valueOf(past), reminderId)
        reminderFireRepository.save(fire.copy(fireAt = past))

        scanner.doScan()

        assertEquals("CLOSED", reminderFireRepository.findById(fire.id!!).get().state)
        val row = reminderRepository.findById(reminderId).get()
        assertEquals("PENDING", row.status)
        assertTrue(row.fireAt.isAfter(LocalDateTime.now()))

        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        assertEquals(2, fires.size)
        assertEquals("PENDING", fires.last().state)
        assertEquals(row.fireAt, fires.last().fireAt)
    }
}
