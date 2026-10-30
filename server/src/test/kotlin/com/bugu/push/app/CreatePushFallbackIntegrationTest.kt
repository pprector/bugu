package com.bugu.push.app

import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.api.ReminderTestClient
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc

/**
 * 降级路径(provider=none):创建后 T0 推送照常执行——文案走模板,
 * 无设备令牌时 Skipped 如实落库;ai_gen_log scene=COPY degraded=true。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.llm.provider=none"])
@AutoConfigureMockMvc
class CreatePushFallbackIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val aiGenLogRepository: AiGenLogRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @BeforeEach
    fun clean() {
        aiGenLogRepository.deleteAll(aiGenLogRepository.findByScene("COPY"))
    }

    @Test
    fun `无网关创建推送走模板且落degraded日志`() {
        val (a, b) = client.boundPair()

        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!

        // 创建通知不属于升级链,不落 escalation_log
        assertEquals(0, escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).size)

        // 文案经模板降级,ai_gen_log 如实记录
        val aiLogs = aiGenLogRepository.findByScene("COPY")
        assertEquals(1, aiLogs.size)
        assertEquals(true, aiLogs[0].degraded)
        assertEquals("GENTLE", aiLogs[0].tone)
    }
}
