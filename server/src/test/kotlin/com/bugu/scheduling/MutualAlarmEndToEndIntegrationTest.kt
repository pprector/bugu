package com.bugu.scheduling

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.identity.infra.DeviceTokenRepository
import com.bugu.identity.infra.DeviceTokenRow
import com.bugu.push.Platform
import com.bugu.push.PushChannel
import com.bugu.push.PushMessage
import com.bugu.push.PushResult
import com.bugu.push.infra.CopyTemplateRepository
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 互叫闹钟端到端(E5-S4):三条主链路各跑一次。
 * 双 ack 闭环(创建→各自 DONE→互见均 UP→reminder ACKED);
 * 单方未起升级(T1 推本人→T2 通知另一方→24h MISSED→对方查得 NOT_UP);
 * 叫醒语断网关(默认 provider=none 无网关)走模板降级且同日幂等命中缓存。
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
class MutualAlarmEndToEndIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val scanner: EscalationScanner,
    private val reminderRepository: ReminderRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val copyTemplateRepository: CopyTemplateRepository,
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

    private fun ack(token: String, fireId: Long) {
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "DONE"}"""
        }.andExpect { status { isOk() } }
    }

    private fun mutualStatus(token: String, reminderId: Long) =
        mockMvc.get("/api/v1/reminder/$reminderId/mutual-status") {
            header("Authorization", "Bearer $token")
        }

    private fun wakeupCopy(token: String, reminderId: Long) =
        mockMvc.get("/api/v1/reminder/$reminderId/wakeup-copy") {
            header("Authorization", "Bearer $token")
        }

    @Test
    fun `双ack闭环_互见均已起_reminder收口ACKED`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)

        ack(a.token, fires.first { it.targetUserId == a.id }.id!!)
        ack(b.token, fires.first { it.targetUserId == b.id }.id!!)

        mutualStatus(a.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.self") { value("UP") }
            jsonPath("$.data.peer") { value("UP") }
        }
        assertEquals("ACKED", reminderRepository.findById(reminderId).get().status)
    }

    @Test
    fun `单方未起_T1推本人_T2通知另一方_24h收口对方查得未起`() {
        val (a, b) = client.boundPair()
        val tokenA = registerHarmonyToken(a.id)
        val tokenB = registerHarmonyToken(b.id)
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        val fireA = fires.first { it.targetUserId == a.id }.id!!

        // b 已起;a 的 fire 到期 31min,T1+T2 同轮触发
        ack(b.token, fires.first { it.targetUserId == b.id }.id!!)
        reminderFireRepository.save(reminderFireRepository.findById(fireA).get().copy(fireAt = now().minusMinutes(31)))
        recordingChannel.reset()

        scanner.doScan()

        val t1 = recordingChannel.messages.filter { it.extras["stage"] == "T1" }
        val t2 = recordingChannel.messages.filter { it.extras["stage"] == "T2" }
        assertEquals(listOf(tokenA), t1.map { it.token })
        assertEquals(listOf(tokenB), t2.map { it.token })

        // 推进到 24h 未起:reminder MISSED,对方查得 peer=NOT_UP
        reminderFireRepository.save(reminderFireRepository.findById(fireA).get().copy(fireAt = now().minusHours(25)))
        scanner.doScan()

        assertEquals("MISSED", reminderRepository.findById(reminderId).get().status)
        assertEquals(1, escalationLogRepository.findByFireIdOrderByCreatedAt(fireA).count { it.stage == "CLOSE" })
        mutualStatus(b.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.self") { value("UP") }
            jsonPath("$.data.peer") { value("NOT_UP") }
        }
    }

    @Test
    fun `叫醒语断网关走模板降级_同日再取命中缓存`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")

        val body = wakeupCopy(a.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.degraded") { value(true) }
        }.andReturn().response.contentAsString
        val content = Regex(""""content"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
        val templates = copyTemplateRepository.findByToneAndSceneAndEnabledTrue("GENTLE", "WAKEUP").map { it.content }
        assertTrue(content in templates, "断网关应走 WAKEUP 模板池")

        // 同日再取:命中缓存,内容稳定
        wakeupCopy(b.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.content") { value(content) }
            jsonPath("$.data.degraded") { value(true) }
        }
    }
}
