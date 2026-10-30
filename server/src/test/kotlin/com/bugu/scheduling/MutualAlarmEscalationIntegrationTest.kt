package com.bugu.scheduling

import com.bugu.bond.api.AuthTestClient
import com.bugu.bond.api.TestUser
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.identity.infra.DeviceTokenRepository
import com.bugu.identity.infra.DeviceTokenRow
import com.bugu.push.Platform
import com.bugu.push.PushChannel
import com.bugu.push.PushMessage
import com.bugu.push.PushResult
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 互叫升级链集成测试(E5-S2):T1 再推未起方本人、T2 通知另一方、24h 双方未起幂等收口。
 * 推送目标经 HARMONY 录制通道的 token 断言(避免与 ConsolePushChannel 的 ANDROID 冲突)。
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
class MutualAlarmEscalationIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val scanner: EscalationScanner,
    private val reminderRepository: ReminderRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val recordingChannel: RecordingChannel,
    private val clock: MutableClock,
    private val jdbc: JdbcTemplate,
) {

    @TestConfiguration
    class TestBeans {
        @Bean
        @Primary
        fun clock(): MutableClock = MutableClock()

        @Bean
        fun recordingChannel(): RecordingChannel = RecordingChannel()
    }

    class RecordingChannel : PushChannel {
        override val platform: Platform = Platform.HARMONY
        val messages = mutableListOf<PushMessage>()

        override fun send(message: PushMessage): PushResult {
            messages.add(message)
            return PushResult.Accepted("fake-${messages.size}")
        }

        fun reset() = messages.clear()
    }

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
        recordingChannel.reset()
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun registerHarmonyToken(userId: Long): String {
        val token = "hm-token-${Random.nextLong()}"
        deviceTokenRepository.save(DeviceTokenRow(userId = userId, platform = "HARMONY", pushToken = token))
        return token
    }

    /** 创建互叫闹钟并把双 fire 改到 now - offsetMin;返回 (reminderId, fireIdOfA, fireIdOfB)。 */
    private fun seedMutual(a: TestUser, b: TestUser, offsetMin: Long): Triple<Long, Long, Long> {
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        val newFireAt = now().minusMinutes(offsetMin)
        fires.forEach { reminderFireRepository.save(it.copy(fireAt = newFireAt)) }
        return Triple(
            reminderId,
            fires.first { it.targetUserId == a.id }.id!!,
            fires.first { it.targetUserId == b.id }.id!!,
        )
    }

    private fun ack(token: String, fireId: Long) {
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "DONE"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `互叫一方未起_T1再推本人_T2通知另一方`() {
        val (a, b) = client.boundPair()
        val tokenA = registerHarmonyToken(a.id)
        val tokenB = registerHarmonyToken(b.id)
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        val fireA = fires.first { it.targetUserId == a.id }.id!!
        val fireB = fires.first { it.targetUserId == b.id }.id!!
        // b 已起;a 的 fire 到期 31min(T1+T2 均到期)
        ack(b.token, fireB)
        reminderFireRepository.save(reminderFireRepository.findById(fireA).get().copy(fireAt = now().minusMinutes(31)))
        recordingChannel.reset()

        scanner.doScan()

        // T1 推未起的 a 本人,T2 通知另一方 b
        val t1 = recordingChannel.messages.filter { it.extras["stage"] == "T1" }
        val t2 = recordingChannel.messages.filter { it.extras["stage"] == "T2" }
        assertEquals(1, t1.size)
        assertEquals(tokenA, t1[0].token)
        assertEquals(1, t2.size)
        assertEquals(tokenB, t2[0].token)
        assertEquals(setOf("T1", "T2"), escalationLogRepository.findByFireIdOrderByCreatedAt(fireA).map { it.stage }.toSet())
        assertTrue(escalationLogRepository.findByFireIdOrderByCreatedAt(fireB).isEmpty())
    }

    @Test
    fun `互叫双方都未起_24h幂等收口MISSED`() {
        val (a, b) = client.boundPair()
        val (reminderId, fireA, fireB) = seedMutual(a, b, 25 * 60)

        scanner.doScan()

        assertEquals("MISSED", reminderRepository.findById(reminderId).get().status)
        assertEquals("CLOSED", reminderFireRepository.findById(fireA).get().state)
        assertEquals("CLOSED", reminderFireRepository.findById(fireB).get().state)
        assertEquals(1, escalationLogRepository.findByFireIdOrderByCreatedAt(fireA).count { it.stage == "CLOSE" })
        assertEquals(1, escalationLogRepository.findByFireIdOrderByCreatedAt(fireB).count { it.stage == "CLOSE" })

        // 再扫不重复、不抛错
        scanner.doScan()
        assertEquals(1, escalationLogRepository.findByFireIdOrderByCreatedAt(fireA).size)
        assertEquals(1, escalationLogRepository.findByFireIdOrderByCreatedAt(fireB).size)
    }

    @Test
    fun `互叫双方各收T1再推`() {
        val (a, b) = client.boundPair()
        val tokenA = registerHarmonyToken(a.id)
        val tokenB = registerHarmonyToken(b.id)
        seedMutual(a, b, 16)
        recordingChannel.reset()

        scanner.doScan()

        val t1Tokens = recordingChannel.messages.filter { it.extras["stage"] == "T1" }.map { it.token }.toSet()
        assertEquals(setOf(tokenA, tokenB), t1Tokens)
    }
}
