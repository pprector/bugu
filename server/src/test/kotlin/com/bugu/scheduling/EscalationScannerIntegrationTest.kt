package com.bugu.scheduling

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.api.ReminderTestClient
import com.bugu.reminder.infra.AckRepository
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
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
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * EscalationScanner 集成测试(S3 + E7-S2):固定时钟验证 T1 再推 / T2 升级 / 24h 收口、
 * 已 ack 不升级、重复扫描幂等;E7 三路径:未上报超 2h 宽限直接收口、已上报照常升级、
 * 撤销后 FIRED 的 fire 重新被扫描。
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
class EscalationScannerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val scanner: EscalationScanner,
    private val reminderRepository: ReminderRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val ackRepository: AckRepository,
    private val clock: MutableClock,
    private val jdbc: JdbcTemplate,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    /** 清理历史遗留的待处理 fire(PENDING/FIRED 均为扫描候选)、escalation_log 及 shedlock,避免干扰当前测试。 */
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

    /** 创建一对绑定用户+提醒,把首条 fire 的 fireAt 改到 now - offsetMin,返回 fireId。 */
    private fun seedFire(offsetMin: Long): Long {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val newFireAt = java.time.LocalDateTime.now(clock).minusMinutes(offsetMin)
        reminderFireRepository.save(fire.copy(fireAt = newFireAt))
        return fire.id!!
    }

    @Test
    fun `T1到期_推接收人且落T1日志`() {
        val fireId = seedFire(16) // past T1(15), before T2(30)

        scanner.doScan()

        val logs = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId)
        assertEquals(1, logs.size)
        assertEquals("T1", logs[0].stage)
        assertEquals("REPUSH_RECEIVER", logs[0].action)
    }

    @Test
    fun `T2到期_T1与T2均落库`() {
        val fireId = seedFire(31) // past T2(30)

        scanner.doScan()

        val stages = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).map { it.stage }.toSet()
        assertEquals(setOf("T1", "T2"), stages)
    }

    @Test
    fun `未上报超2h宽限_直接收口MISSED不发T1T2`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val fireId = fire.id!!
        // 距 fire_at 3h:超 2h 宽限但未到 24h,证明走的是宽限收口而非 24h CLOSE
        reminderFireRepository.save(fire.copy(fireAt = java.time.LocalDateTime.now(clock).minusHours(3)))

        scanner.doScan()

        val reminderRow = reminderRepository.findById(reminderId).get()
        assertEquals("MISSED", reminderRow.status)
        val fireRow = reminderFireRepository.findById(fireId).get()
        assertEquals("CLOSED", fireRow.state)
        val logs = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId)
        assertEquals(1, logs.size)
        assertEquals("CLOSE", logs[0].stage)
        assertEquals("MARK_MISSED", logs[0].action)

        // 重复扫描幂等:fire 已 CLOSED 不再进候选,日志不重复
        scanner.doScan()
        assertEquals(1, escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).size)
    }

    @Test
    fun `已上报超24h未响应_收口MISSED`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val fireId = fire.id!!
        val now = java.time.LocalDateTime.now(clock)
        reminderFireRepository.save(
            fire.copy(
                fireAt = now.minusHours(25),
                reportedAt = now.minusHours(25),
                source = "LOCAL_ALARM",
            ),
        )

        scanner.doScan()

        assertEquals("MISSED", reminderRepository.findById(reminderId).get().status)
        assertEquals("CLOSED", reminderFireRepository.findById(fireId).get().state)
        val logs = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId)
        assertEquals(1, logs.size)
        assertEquals("CLOSE", logs[0].stage)
    }

    @Test
    fun `已上报到期_走真实上报端点后照常T1`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val fireId = fire.id!!
        reminderFireRepository.save(fire.copy(fireAt = java.time.LocalDateTime.now(clock).minusMinutes(16)))

        // 端侧上报走真实端点(S1),之后扫描器按路径 A 升级
        mockMvc.post("/api/v1/reminder/fire/$fireId/report") {
            header("Authorization", "Bearer ${b.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"idempotencyKey": "${fire.idempotencyKey}", "source": "LOCAL_ALARM"}"""
        }.andExpect { status { isOk() } }

        scanner.doScan()

        val logs = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId)
        assertEquals(1, logs.size)
        assertEquals("T1", logs[0].stage)
        assertEquals("REPUSH_RECEIVER", logs[0].action)
    }

    @Test
    fun `撤销后FIRED的fire_重新被扫描升级`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val fireId = fire.id!!
        reminderFireRepository.save(fire.copy(fireAt = java.time.LocalDateTime.now(clock).minusMinutes(31)))

        // 长辈确认完成 → 子女撤销:fire 回 FIRED、ack 行删除(E6-S2)
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer ${b.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "DONE"}"""
        }.andExpect { status { isOk() } }
        val ackId = ackRepository.findByFireId(fireId)!!.id!!
        mockMvc.post("/api/v1/reminder/ack/$ackId/revoke") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect { status { isOk() } }

        // 撤销后 fire=FIRED 仍在候选,距 fire_at 31min:T1 与 T2 同轮推进(修复 E6 缺口)
        scanner.doScan()

        val stages = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).map { it.stage }.toSet()
        assertEquals(setOf("T1", "T2"), stages)
    }

    @Test
    fun `已ack的fire不升级`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        val fireId = fire.id!!
        reminderFireRepository.save(fire.copy(fireAt = java.time.LocalDateTime.now(clock).minusMinutes(20)))

        // 接收人 ack DONE,fire 变 RESPONDED
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/reminder/ack")
                .header("Authorization", "Bearer ${b.token}")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"fireId": $fireId, "action": "DONE"}"""),
        )

        scanner.doScan()

        assertTrue(escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).isEmpty())
    }

    @Test
    fun `重复扫描不重复落escalation_log`() {
        val fireId = seedFire(16)

        scanner.doScan()
        val first = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).size
        scanner.doScan()
        val second = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).size

        assertEquals(first, second)
        assertEquals(1, second)
    }

    @TestConfiguration
    class ClockConfig {
        @Bean
        @Primary
        fun clock(): MutableClock = MutableClock()
    }
}

/** 测试用可变时钟:默认系统时钟,可固定到指定 instant。 */
class MutableClock : Clock() {
    @Volatile
    private var delegate: Clock = Clock.system(ZoneId.of("Asia/Shanghai"))

    override fun getZone(): ZoneId = delegate.zone
    override fun withZone(zone: ZoneId): Clock = delegate.withZone(zone)
    override fun instant(): Instant = delegate.instant()

    fun setFixed(instant: Instant) {
        delegate = Clock.fixed(instant, zone)
    }
}
