package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.ReminderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc

/**
 * 静音与创建拦截集成测试:静音 ONCE → 创建被 REMINDER_MUTED 拦截且不落库;
 * 他类不受影响;恢复后正常;非 bond 对方 403;重复静音幂等。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class MuteFlowIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val reminderRepository: ReminderRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @Test
    fun `静音后创建被拦截且不落库_恢复后正常`() {
        val (a, b) = client.boundPair()
        val before = reminderRepository.count()

        reminders.mute(b.token, a.id, "ONCE").andExpect { status { isOk() } }

        reminders.create(a.token, b.id, "吃药", "ONCE")
            .andExpect { status { isBadRequest() } }
            .andExpect { jsonPath("$.code") { value("REMINDER_MUTED") } }
        assertEquals(before, reminderRepository.count())

        reminders.create(a.token, b.id, "吃药", "MUTUAL_ALARM").andExpect { status { isOk() } }

        assert(reminders.mutedList(b.token).contains(""""creatorId":${a.id}"""))

        reminders.unmute(b.token, a.id, "ONCE").andExpect { status { isOk() } }
        reminders.create(a.token, b.id, "吃药", "ONCE").andExpect { status { isOk() } }
    }

    @Test
    fun `非bond对方静音返回403_重复静音幂等`() {
        val (a, b) = client.boundPair()
        val c = client.register()

        reminders.mute(b.token, c.id, "ONCE").andExpect { status { isForbidden() } }

        repeat(2) {
            reminders.mute(b.token, a.id, "ONCE").andExpect { status { isOk() } }
        }
        assertEquals(1, Regex(""""creatorId":${a.id}""").findAll(reminders.mutedList(b.token)).count())
    }
}
