package com.bugu.scheduling

import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.api.ReminderTestClient
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import java.time.LocalDateTime

/**
 * 降级端到端(story 4.4,PRD FR-08):provider=none 全程无网关,
 * 创建 → T1 → T2 照走(文案全部模板降级),ai_gen_log 逐条 degraded=true——
 * 拔 AI 后同一流程可用,降级如实记录。
 */
@SpringBootTest(
    properties = [
        "bugu.sms.provider=console",
        "bugu.llm.provider=none",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "bugu.escalation.scan-delay=3600000",
        "bugu.escalation.lock-at-least=PT0S",
    ],
)
@AutoConfigureMockMvc
class AiDegradedE2EIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val scanner: EscalationScanner,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val aiGenLogRepository: AiGenLogRepository,
    private val clock: MutableClock,
    private val jdbc: JdbcTemplate,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @BeforeEach
    fun clean() {
        val ids = reminderFireRepository.findByState("PENDING").mapNotNull { it.id }
        if (ids.isNotEmpty()) {
            escalationLogRepository.deleteByFireIdIn(ids)
            reminderFireRepository.deleteAllById(ids)
        }
        jdbc.update("DELETE FROM shedlock WHERE name = 'escalation-scan'")
        aiGenLogRepository.deleteAll(aiGenLogRepository.findByScene("COPY"))
    }

    @Test
    fun `创建到T1T2全程无网关走模板且降级如实记录`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val fireId = fire.id!!

        // 创建推送已走一次 compose(模板降级)
        assertEquals(1, aiGenLogRepository.findByScene("COPY").size)

        // fireAt 改到 31min 前:同一轮扫描 T1+T2 均到期
        reminderFireRepository.save(fire.copy(fireAt = LocalDateTime.now(clock).minusMinutes(31)))
        scanner.doScan()

        val logs = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId)
        assertEquals(listOf("T1", "T2"), logs.map { it.stage })
        // 无设备令牌:Skipped 如实落库,不伪造成功
        assertTrue(logs.all { it.pushResult == "SKIPPED" })

        // 创建 1 + T1 1 + T2 1,全部模板降级
        val aiLogs = aiGenLogRepository.findByScene("COPY")
        assertEquals(3, aiLogs.size)
        assertTrue(aiLogs.all { it.degraded }, "存在未降级记录: $aiLogs")
    }

    @TestConfiguration
    class ClockConfig {
        @Bean
        @Primary
        fun clock(): MutableClock = MutableClock()
    }
}
