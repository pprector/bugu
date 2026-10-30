package com.bugu.scheduling

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.api.ReminderTestClient
import com.bugu.reminder.infra.AckRepository
import com.bugu.reminder.infra.AckRow
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderFireRow
import org.junit.jupiter.api.Assertions.assertEquals
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
 * 节奏自适应集成测试(story 4.3):秒回接收人 T1 提前 5min、慢响应放宽 5min、
 * 无记录按固定 15min、24h 收口不受偏移影响。
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
class EscalationScannerPaceIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val scanner: EscalationScanner,
    private val reminderFireRepository: ReminderFireRepository,
    private val ackRepository: AckRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val clock: MutableClock,
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

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    /** 新建一对绑定用户+提醒,把首条 fire 改到 now - offsetMin。返回 (fireId, receiverId)。 */
    private fun seedFire(offsetMin: Long, historyDelaysSeconds: List<Long> = emptyList()): Long {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        reminderFireRepository.save(fire.copy(fireAt = now().minusMinutes(offsetMin)))

        // 为接收人补历史响应记录(同一 reminder 多条历史 fire + ack)
        historyDelaysSeconds.forEachIndexed { i, delay ->
            val pastFireAt = now().minusDays(2).minusMinutes(i.toLong())
            val pastFire = reminderFireRepository.save(
                ReminderFireRow(
                    reminderId = reminderId,
                    targetUserId = b.id,
                    fireAt = pastFireAt,
                    state = "RESPONDED",
                    idempotencyKey = "$reminderId:$pastFireAt",
                ),
            )
            ackRepository.save(
                AckRow(
                    fireId = pastFire.id!!,
                    action = "DONE",
                    ackedAt = pastFireAt.plusSeconds(delay),
                ),
            )
        }
        return fire.id!!
    }

    private fun stages(fireId: Long): Set<String> =
        escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).map { it.stage }.toSet()

    @Test
    fun `秒回接收人T1提前到10分钟`() {
        val fastHistory = List(5) { 60L }
        val fireId = seedFire(11, fastHistory) // 11min:固定 T1(15)未到,提前后(10)已到

        scanner.doScan()

        assertEquals(setOf("T1"), stages(fireId))
    }

    @Test
    fun `无响应记录T1仍按15分钟`() {
        val notYet = seedFire(11)
        val due = seedFire(16)

        scanner.doScan()

        assertEquals(emptySet<String>(), stages(notYet))
        assertEquals(setOf("T1"), stages(due))
    }

    @Test
    fun `慢响应接收人T1放宽到20分钟`() {
        val slowHistory = List(5) { 900L }
        val notYet = seedFire(19, slowHistory) // 19min:固定 T1 已过,放宽后(20)未到
        val due = seedFire(21, slowHistory)

        scanner.doScan()

        assertEquals(emptySet<String>(), stages(notYet))
        assertEquals(setOf("T1"), stages(due))
    }

    @Test
    fun `慢响应24h收口不被推迟`() {
        val slowHistory = List(5) { 900L }
        val fireId = seedFire(25 * 60, slowHistory)

        scanner.doScan()

        // CLOSE 为终态:命中即收口,不再补推 T1/T2
        assertEquals(setOf("CLOSE"), stages(fireId))
    }

    @TestConfiguration
    class ClockConfig {
        @Bean
        @Primary
        fun clock(): MutableClock = MutableClock()
    }
}
