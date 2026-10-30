package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.ReminderFireRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * 端侧触发上报集成测试(E7-S1):
 * 幂等(reportedAt 不覆盖)、幂等键不匹配 400、CLOSED 已收口 409、非相关人 403、
 * RESPONDED 可补报、发起人与触发目标均可上报、长辈模式账号可上报(过滤白名单)。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class FireReportIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val reminderFireRepository: ReminderFireRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun firstFire(reminderId: Long) =
        reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first()

    private fun report(token: String, fireId: Long, key: String, source: String = "LOCAL_ALARM") =
        mockMvc.post("/api/v1/reminder/fire/$fireId/report") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"idempotencyKey": "$key", "source": "$source"}"""
        }

    @Test
    fun `首次上报_OK_登记reportedAt与source`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = firstFire(reminderId)

        report(b.token, fire.id!!, fire.idempotencyKey).andExpect {
            status { isOk() }
            jsonPath("$.data.fireId") { value(fire.id) }
            jsonPath("$.data.state") { value("PENDING") }
            jsonPath("$.data.source") { value("LOCAL_ALARM") }
            jsonPath("$.data.reportedAt") { exists() }
        }

        val saved = reminderFireRepository.findById(fire.id).get()
        assertNotNull(saved.reportedAt)
        assertEquals("LOCAL_ALARM", saved.source)
    }

    @Test
    fun `重复上报_幂等_reportedAt与source不覆盖`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = firstFire(reminderId)

        report(b.token, fire.id!!, fire.idempotencyKey, source = "LOCAL_ALARM").andExpect { status { isOk() } }
        val first = reminderFireRepository.findById(fire.id).get()

        // 离线补报重放:同键再报,reportedAt/source 不变
        report(b.token, fire.id, fire.idempotencyKey, source = "PUSH").andExpect {
            status { isOk() }
            jsonPath("$.data.reportedAt") { value(first.reportedAt.toString()) }
            jsonPath("$.data.source") { value("LOCAL_ALARM") }
        }
        val second = reminderFireRepository.findById(fire.id).get()
        assertEquals(first.reportedAt, second.reportedAt)
        assertEquals("LOCAL_ALARM", second.source)
    }

    @Test
    fun `幂等键不匹配_返回400`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = firstFire(reminderId)

        report(b.token, fire.id!!, "999:bad-key").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `非法source_返回400`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = firstFire(reminderId)

        report(b.token, fire.id!!, fire.idempotencyKey, source = "SMS").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `CLOSED已收口_上报返回409`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = firstFire(reminderId)
        reminderFireRepository.save(fire.copy(state = "CLOSED"))

        report(b.token, fire.id!!, fire.idempotencyKey).andExpect { status { isConflict() } }
    }

    @Test
    fun `RESPONDED后补报_OK_状态不变`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = firstFire(reminderId)

        // 先确认后补报(离线队列回放顺序颠倒的场景)
        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer ${b.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": ${fire.id}, "action": "DONE"}"""
        }.andExpect { status { isOk() } }

        report(b.token, fire.id!!, fire.idempotencyKey).andExpect {
            status { isOk() }
            jsonPath("$.data.state") { value("RESPONDED") }
            jsonPath("$.data.reportedAt") { exists() }
        }
    }

    @Test
    fun `非相关人上报_返回403_发起人与目标均可`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = firstFire(reminderId)

        // 无关第三方 403
        val outsider = client.register()
        report(outsider.token, fire.id!!, fire.idempotencyKey).andExpect { status { isForbidden() } }

        // 触发目标(接收人)可上报
        report(b.token, fire.id, fire.idempotencyKey).andExpect { status { isOk() } }

        // 发起人亦可上报另一条(互叫双方各一条 fire)
        val reminderId2 = reminders.createAndGetId(a.token, b.id, kind = "MUTUAL_ALARM")
        val creatorFire = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId2)
            .first { it.targetUserId == a.id }
        report(a.token, creatorFire.id!!, creatorFire.idempotencyKey).andExpect { status { isOk() } }
    }

    @Test
    fun `长辈模式账号可上报_过滤白名单放行`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fire = firstFire(reminderId)

        // a 标记 b 为长辈模式
        mockMvc.post("/api/v1/bond/elder-mode") {
            header("Authorization", "Bearer ${a.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"peerId": ${b.id}, "enabled": true}"""
        }.andExpect { status { isOk() } }

        // 长辈端响铃后上报:带参端点正则放行,不 403
        report(b.token, fire.id!!, fire.idempotencyKey).andExpect { status { isOk() } }
    }
}
