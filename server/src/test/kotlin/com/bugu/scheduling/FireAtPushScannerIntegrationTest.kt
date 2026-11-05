package com.bugu.scheduling

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.identity.infra.DeviceTokenRepository
import com.bugu.identity.infra.DeviceTokenRow
import com.bugu.identity.infra.UserRepository
import com.bugu.push.Platform
import com.bugu.push.PushChannel
import com.bugu.push.PushMessage
import com.bugu.push.PushResult
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * FireAtPushScanner 集成测试:
 * 到点触达(标题/正文含提醒人与内容、extras 带 deeplink)、重复扫描幂等、
 * 过期 fire 不推、已上报/已取消/静默不推、无令牌 Skipped 如实落库。
 *
 * 使用 HARMONY 录制通道避免与 ConsolePushChannel 的 ANDROID 通道冲突;
 * 集成测试直连本地 PostgreSQL(application.yml 默认 127.0.0.1:5432/bugu)。
 */
@SpringBootTest(
    properties = [
        "bugu.sms.provider=console",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "bugu.fire-at.scan-delay=3600000",
        "bugu.fire-at.lock-at-least=PT0S",
    ],
)
@AutoConfigureMockMvc
class FireAtPushScannerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val scanner: FireAtPushScanner,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val userRepository: UserRepository,
    private val recordingChannel: RecordingChannel,
    private val jdbc: JdbcTemplate,
) {

    @TestConfiguration
    class TestBeans {
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

    data class Seed(
        val creatorToken: String,
        val creatorName: String,
        val receiverToken: String?,
        val reminderId: Long,
        val fireId: Long,
    )

    @BeforeEach
    fun clean() {
        val ids = (reminderFireRepository.findByState("PENDING") + reminderFireRepository.findByState("FIRED"))
            .mapNotNull { it.id }
        if (ids.isNotEmpty()) {
            escalationLogRepository.deleteByFireIdIn(ids)
            reminderFireRepository.deleteAllById(ids)
        }
        jdbc.update("DELETE FROM shedlock WHERE name = 'fire-at-push-scan'")
        recordingChannel.reset()
    }

    private fun now(): LocalDateTime = LocalDateTime.now()

    /**
     * 建一对绑定用户 + 提醒,把首条 fire 移到 now + seconds(负数=过去);返回本轮断言所需数据。
     * @param registerToken 为接收人注册 HARMONY 令牌;false 模拟无令牌
     * @param reportedAt 模拟端侧已上报
     * @param silent 模拟超每日上限静默
     */
    private fun seed(
        seconds: Long,
        registerToken: Boolean = true,
        reportedAt: LocalDateTime? = null,
        silent: Boolean = false,
        title: String = "吃降压药",
    ): Seed {
        val (a, b) = client.boundPair()
        val token = if (registerToken) {
            val t = "hm-token-${Random.nextLong()}"
            deviceTokenRepository.save(DeviceTokenRow(userId = b.id, platform = "HARMONY", pushToken = t))
            t
        } else {
            null
        }
        val reminderId = reminders.createAndGetId(a.token, b.id, title = title)
        val fire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()
        reminderFireRepository.save(
            fire.copy(
                fireAt = now().plusSeconds(seconds),
                reportedAt = reportedAt,
                source = if (reportedAt != null) "LOCAL_ALARM" else null,
            ),
        )
        if (silent) {
            jdbc.update("UPDATE reminder SET silent = TRUE WHERE id = ?", reminderId)
        }
        // 创建 T0 推送已发生,清空录制,只观察 FIRE_AT
        recordingChannel.reset()
        val creatorName = requireNotNull(userRepository.findById(a.id).orElse(null)).nickname
        return Seed(a.token, creatorName, token, reminderId, fire.id!!)
    }

    private fun fireAtLogs(fireId: Long) =
        escalationLogRepository.findByFireIdOrderByCreatedAt(fireId).filter { it.stage == "FIRE_AT" }

    @Test
    fun `到点fire推送接收人_标题正文含提醒人与内容且带deeplink`() {
        val seed = seed(seconds = -30)

        scanner.doScan()

        assertEquals(1, recordingChannel.messages.size)
        val message = recordingChannel.messages.single()
        assertEquals(seed.receiverToken, message.token)
        assertEquals("FIRE_AT", message.extras["stage"])
        assertEquals(seed.fireId.toString(), message.extras["fireId"])
        assertEquals(seed.reminderId.toString(), message.extras["reminderId"])
        assertEquals(
            "bugu://ring?fireId=${seed.fireId}&reminderId=${seed.reminderId}",
            message.extras["deeplink"],
        )
        // 点击直达响铃页所需的入参随 extras 一起下发
        assertEquals("吃降压药", message.extras["title"])
        assertEquals("ONCE", message.extras["kind"])
        assertEquals("false", message.extras["selfReminder"])
        assertTrue(message.extras["fireAt"]!!.isNotBlank())
        assertTrue(message.extras["idempotencyKey"]!!.isNotBlank())
        // 标题/正文都含提醒人昵称与提醒内容,用户不打开 App 也能看懂
        assertTrue(message.title.contains(seed.creatorName), "标题缺提醒人: ${message.title}")
        assertTrue(message.title.contains("吃降压药"), "标题缺内容: ${message.title}")
        // 实际通知栏标题也必须带提醒人与内容(常规提醒仍由通道回退到应用名「布谷」)
        assertEquals(message.title, message.notificationTitle)
        assertTrue(message.body.contains(seed.creatorName), "正文缺提醒人: ${message.body}")
        assertTrue(message.body.contains("吃降压药"), "正文缺内容: ${message.body}")

        val log = fireAtLogs(seed.fireId).single()
        assertEquals("RING_RECEIVER", log.action)
        assertEquals("ACCEPTED", log.pushResult)
    }

    @Test
    fun `重复扫描幂等_只推一次`() {
        val seed = seed(seconds = -30)

        scanner.doScan()
        scanner.doScan()
        scanner.doScan()

        assertEquals(1, recordingChannel.messages.size)
        assertEquals(1, fireAtLogs(seed.fireId).size)
    }

    @Test
    fun `过期fire不推送`() {
        val seed = seed(seconds = -30 * 60)

        scanner.doScan()

        assertTrue(recordingChannel.messages.isEmpty())
        assertTrue(fireAtLogs(seed.fireId).isEmpty())
    }

    @Test
    fun `未来fire不提前推送`() {
        val seed = seed(seconds = 30)

        scanner.doScan()

        assertTrue(recordingChannel.messages.isEmpty())
        assertTrue(fireAtLogs(seed.fireId).isEmpty())
    }

    @Test
    fun `端侧已上报的fire不推送`() {
        val seed = seed(seconds = -60, reportedAt = now())

        scanner.doScan()

        assertTrue(recordingChannel.messages.isEmpty())
        assertTrue(fireAtLogs(seed.fireId).isEmpty())
    }

    @Test
    fun `已取消fire不推送`() {
        val seed = seed(seconds = -30)

        reminders.cancel(seed.creatorToken, seed.reminderId).andExpect { status { isOk() } }

        scanner.doScan()

        assertTrue(recordingChannel.messages.isEmpty())
        assertTrue(fireAtLogs(seed.fireId).isEmpty())
    }

    @Test
    fun `无设备令牌Skipped落库且不重推`() {
        val seed = seed(seconds = -30, registerToken = false)

        scanner.doScan()
        scanner.doScan()

        assertTrue(recordingChannel.messages.isEmpty())
        val log = fireAtLogs(seed.fireId).single()
        assertEquals("SKIPPED", log.pushResult)
    }

    @Test
    fun `静默提醒不推送`() {
        val seed = seed(seconds = -30, silent = true)

        scanner.doScan()

        assertTrue(recordingChannel.messages.isEmpty())
        assertTrue(fireAtLogs(seed.fireId).isEmpty())
    }
}
