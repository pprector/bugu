package com.bugu.push.app

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.identity.infra.DeviceTokenRepository
import com.bugu.identity.infra.DeviceTokenRow
import com.bugu.push.PushResult
import com.bugu.reminder.api.ReminderTestClient
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import kotlin.random.Random

/**
 * PushService 集成测试:有模板有令牌 → Accepted 落 escalation_log;
 * 无令牌 → Skipped 落库。copy_template 由 V9 迁移种子。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.push.provider=console"])
@AutoConfigureMockMvc
class PushServiceIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val pushService: PushService,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val reminderFireRepository: ReminderFireRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun firstFireId(reminderId: Long): Long =
        reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!

    @Test
    fun `有令牌时Accepted并落escalation_log`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        deviceTokenRepository.save(
            DeviceTokenRow(userId = b.id, platform = "ANDROID", pushToken = "console-token-${Random.nextLong()}"),
        )

        val result = pushService.push(
            fireId = fireId, stage = "T1", action = "REPUSH_RECEIVER",
            userId = b.id, tone = "GENTLE", scene = "REPUSH",
        )

        assertInstanceOf(PushResult.Accepted::class.java, result)
        val logs = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId)
        assertEquals(1, logs.size)
        assertEquals("ACCEPTED", logs[0].pushResult)
        assertEquals("T1", logs[0].stage)
    }

    @Test
    fun `无令牌时Skipped并落库`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        val result = pushService.push(
            fireId = fireId, stage = "T2", action = "NOTIFY_CREATOR",
            userId = b.id, tone = "GENTLE", scene = "REPUSH",
        )

        assertInstanceOf(PushResult.Skipped::class.java, result)
        val logs = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId)
        assertEquals(1, logs.size)
        assertEquals("SKIPPED", logs[0].pushResult)
    }
}
