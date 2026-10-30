package com.bugu.push.app

import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.ai.infra.LlmCopyResult
import com.bugu.ai.infra.LlmGateway
import com.bugu.bond.api.AuthTestClient
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import kotlin.random.Random

/**
 * 创建与再推接入 AI 文案(story 4.2):假网关 + HARMONY 录制通道(避免与 ConsolePushChannel 的 ANDROID 冲突)。
 * 创建后 T0 推送用生成文案;T1 再推 attempt=2;prompt 不含原始备注。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.llm.provider=fake"])
@AutoConfigureMockMvc
class AiCopyPushIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val pushService: PushService,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val aiGenLogRepository: AiGenLogRepository,
    private val fakeGateway: FakeGateway,
    private val recordingChannel: RecordingChannel,
) {

    @TestConfiguration
    class TestBeans {
        @Bean
        fun fakeGateway(): FakeGateway = FakeGateway()

        @Bean
        fun recordingChannel(): RecordingChannel = RecordingChannel()
    }

    class FakeGateway : LlmGateway {
        var lastPrompt: String? = null
        var reply: String = "宝贝,到点记得吃药哦~"

        override fun generateText(systemPrompt: String, userPrompt: String, maxTokens: Int, temperature: Double): LlmCopyResult =
            generateCopy(userPrompt)

        override fun generateCopy(prompt: String): LlmCopyResult {
            lastPrompt = prompt
            return LlmCopyResult(reply, 20, 8)
        }

        fun reset() {
            lastPrompt = null
            reply = "宝贝,到点记得吃药哦~"
        }
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
        fakeGateway.reset()
        recordingChannel.reset()
        aiGenLogRepository.deleteAll(aiGenLogRepository.findByScene("COPY"))
    }

    private fun registerHarmonyToken(userId: Long) {
        deviceTokenRepository.save(
            DeviceTokenRow(userId = userId, platform = "HARMONY", pushToken = "hm-token-${Random.nextLong()}"),
        )
    }

    @Test
    fun `创建后T0推送用生成文案且落日志`() {
        val (a, b) = client.boundPair()
        registerHarmonyToken(b.id)

        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!

        assertEquals(1, recordingChannel.messages.size)
        assertEquals("宝贝,到点记得吃药哦~", recordingChannel.messages[0].body)
        assertEquals("T0", recordingChannel.messages[0].extras["stage"])

        // 创建通知不属于升级链,不落 escalation_log
        assertEquals(0, escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).size)

        val aiLogs = aiGenLogRepository.findByScene("COPY")
        assertEquals(1, aiLogs.size)
        assertEquals(false, aiLogs[0].degraded)
        assertEquals(20, aiLogs[0].promptTokens)
    }

    @Test
    fun `T1再推经compose且attempt为2`() {
        val (a, b) = client.boundPair()
        registerHarmonyToken(b.id)
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!
        recordingChannel.reset()

        pushService.push(fireId = fireId, stage = "T1", action = "REPUSH_RECEIVER", userId = b.id, tone = "GENTLE", scene = "REPUSH")

        assertEquals(1, recordingChannel.messages.size)
        assertEquals("宝贝,到点记得吃药哦~", recordingChannel.messages[0].body)
        assertTrue(fakeGateway.lastPrompt!!.contains("第 2 次提醒"))
        assertTrue(fakeGateway.lastPrompt!!.contains("吃降压药"))
    }

    @Test
    fun `prompt不含原始备注`() {
        val (a, b) = client.boundPair()
        registerHarmonyToken(b.id)

        reminders.create(a.token, b.id, note = "他上周又忘记吃私密药").andExpect { status { isOk() } }

        assertFalse(fakeGateway.lastPrompt!!.contains("私密"), "prompt 泄露了备注: ${fakeGateway.lastPrompt}")
    }
}
