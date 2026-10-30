package com.bugu.scheduling

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.api.ReminderTestClient
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 可信调度端到端集成测试(E7-S3):
 * 链路一:创建→端侧上报→T1 再推→T2 升级→确认完成,上报与升级链全链贯通,已响应 fire 退出候选。
 * 链路二:创建→未上报超 2h 宽限→收口 MISSED→设备恢复后补报 409(端侧展示「已错过」)→current 为空。
 */
@SpringBootTest(
    properties = [
        "bugu.sms.provider=console",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "bugu.escalation.scan-delay=3600000",
        "bugu.escalation.lock-at-least=PT0S",
    ],
)
@AutoConfigureMockMvc
class ReliableSchedulingE2EIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val scanner: EscalationScanner,
    private val reminderRepository: ReminderRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val clock: MutableClock,
    private val jdbc: JdbcTemplate,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

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

    private fun report(token: String, fireId: Long, key: String) =
        mockMvc.post("/api/v1/reminder/fire/$fireId/report") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"idempotencyKey": "$key", "source": "LOCAL_ALARM"}"""
        }

    private fun ackDone(token: String, fireId: Long) =
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "DONE"}"""
        }

    private fun stages(fireId: Long) =
        escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).map { it.stage }

    private fun fireAtPast(minutes: Long) = java.time.LocalDateTime.now(clock).minusMinutes(minutes)

    @Test
    fun `响铃上报_升级链T1T2_确认完成_全链路`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val fireId = fire.id!!
        reminderFireRepository.save(fire.copy(fireAt = fireAtPast(16)))

        // 端侧到点响铃,上报触发事实(reported_at 落库,state 不变)
        report(b.token, fireId, fire.idempotencyKey).andExpect { status { isOk() } }
        assertEquals("PENDING", reminderFireRepository.findById(fireId).get().state)

        // T1:无响应再推接收人
        scanner.doScan()
        assertEquals(listOf("T1"), stages(fireId))

        // T2:升级通知发起人
        reminderFireRepository.save(
            reminderFireRepository.findById(fireId).get().copy(fireAt = fireAtPast(31)),
        )
        scanner.doScan()
        assertEquals(listOf("T1", "T2"), stages(fireId))

        // 接收人确认完成;RESPONDED 的 fire 退出候选,不再产生新日志
        ackDone(b.token, fireId).andExpect { status { isOk() } }
        scanner.doScan()
        assertEquals(listOf("T1", "T2"), stages(fireId))
        assertEquals("ACKED", reminderRepository.findById(reminderId).get().status)
        assertEquals("RESPONDED", reminderFireRepository.findById(fireId).get().state)
    }

    @Test
    fun `关机错过_超宽限收口MISSED_补报409_current为空`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val fireId = fire.id!!
        // 距 fire_at 3h:超 2h 宽限但未到 24h,证明走的是宽限收口而非 24h CLOSE
        reminderFireRepository.save(fire.copy(fireAt = fireAtPast(180)))

        scanner.doScan()

        // 超宽限直接收口:reminder MISSED、fire CLOSED、仅 CLOSE 日志(无 T1/T2)
        assertEquals("MISSED", reminderRepository.findById(reminderId).get().status)
        assertEquals("CLOSED", reminderFireRepository.findById(fireId).get().state)
        assertEquals(listOf("CLOSE"), stages(fireId))

        // 设备恢复上线补报:已收口 409,端侧据此展示「已错过」
        report(b.token, fireId, fire.idempotencyKey).andExpect { status { isConflict() } }

        // 长辈端 current 无待处理提醒(explicitNulls=false,data 字段省略)
        mockMvc.get("/api/v1/reminder/current") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { doesNotExist() }
        }
    }

    @TestConfiguration
    class ClockConfig {
        @Bean
        @Primary
        fun clock(): MutableClock = MutableClock()
    }
}
