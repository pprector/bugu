package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.AckRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * 误触撤销集成测试(E6-S2):
 * 仅发起人可撤销 DONE 确认,回滚 fire RESPONDED→FIRED、删除 ack 行、reminder ACKED→FIRED。
 * 幂等:ack 不存在 404,fire 非 RESPONDED 409;非 creator 403;互叫单方 DONE 时 reminder 保持 FIRED。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class RevokeAckIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val ackRepository: AckRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val reminderRepository: ReminderRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun firstFireId(reminderId: Long): Long =
        reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!

    private fun ack(token: String, fireId: Long, action: String) =
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "$action"}"""
        }

    private fun revoke(token: String, ackId: Long) =
        mockMvc.post("/api/v1/reminder/ack/$ackId/revoke") {
            header("Authorization", "Bearer $token")
        }

    @Test
    fun `子女撤销DONE_回滚fire与reminder且删除ack`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        // 长辈 b 确认完成
        ack(b.token, fireId, "DONE").andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ACKED") }
        }
        assertEquals("RESPONDED", reminderFireRepository.findById(fireId).get().state)
        val ackRow = ackRepository.findByFireId(fireId)!!

        // 子女 a 撤销
        revoke(a.token, ackRow.id!!).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("FIRED") }
        }

        // fire 回 FIRED、ack 行删除、reminder 回 FIRED
        assertEquals("FIRED", reminderFireRepository.findById(fireId).get().state)
        assertFalse(ackRepository.existsByFireId(fireId))
        assertEquals("FIRED", reminderRepository.findById(reminderId).get().status)
    }

    @Test
    fun `重复撤销返回409`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        ack(b.token, fireId, "DONE").andExpect { status { isOk() } }
        val ackId = ackRepository.findByFireId(fireId)!!.id!!

        revoke(a.token, ackId).andExpect { status { isOk() } }
        // 再次撤销同一 ackId:ack 行已删 → 404
        revoke(a.token, ackId).andExpect { status { isNotFound() } }
    }

    @Test
    fun `非发起人撤销返回403`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = firstFireId(reminderId)

        ack(b.token, fireId, "DONE").andExpect { status { isOk() } }
        val ackId = ackRepository.findByFireId(fireId)!!.id!!

        // 接收人 b 不能撤销
        revoke(b.token, ackId).andExpect { status { isForbidden() } }

        // 第三方也不能撤销
        val outsider = client.register()
        revoke(outsider.token, ackId).andExpect { status { isForbidden() } }
    }

    @Test
    fun `互叫单方DONE后撤销_reminder保持FIRED`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val fireA = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
            .first { it.targetUserId == a.id }.id!!
        val fireB = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
            .first { it.targetUserId == b.id }.id!!

        // b 单方确认:reminder 仍 FIRED
        ack(b.token, fireB, "DONE").andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("FIRED") }
        }
        val ackB = ackRepository.findByFireId(fireB)!!

        // 发起人 a 撤销 b 的确认
        revoke(a.token, ackB.id!!).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("FIRED") }
        }

        // fireB 回 FIRED、ack 删除、reminder 保持 FIRED(未变 ACKED 过)
        assertEquals("FIRED", reminderFireRepository.findById(fireB).get().state)
        assertFalse(ackRepository.existsByFireId(fireB))
        assertEquals("FIRED", reminderRepository.findById(reminderId).get().status)
        // fireA 未受影响
        assertEquals("PENDING", reminderFireRepository.findById(fireA).get().state)
    }
}
