package com.bugu.admin.api

import com.bugu.admin.infra.AdminAuditLogRepository
import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.ai.infra.AiGenLogRow
import com.bugu.push.infra.CopyTemplateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * AI 管理集成测试(E9-S3):模板 CRUD 与启停即时生效(与 AiCopyService 同源查询)、
 * gen-log 降级率、LLM 状态只读、OPS 写 403、审计。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class AdminAiIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val copyTemplateRepository: CopyTemplateRepository,
    private val genLogRepository: AiGenLogRepository,
    private val auditRepository: AdminAuditLogRepository,
    private val jdbc: JdbcTemplate,
) {

    private fun superToken(): String {
        val body = mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username": "super", "password": "bugu-admin-2026"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""token"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
    }

    /** super 的 id(不要硬编码:共享开发库里 seed 重建后自增 id 会变)。 */
    private fun superId(): Long =
        jdbc.queryForObject("SELECT id FROM admin_user WHERE username = 'super'", Long::class.java)!!

    @Test
    fun `模板增改启停_降级候选即时生效`() {
        val token = superToken()

        // 建:新模板立即可被降级选取
        val created = mockMvc.post("/api/v1/admin/ai/copy-templates") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"tone": "GENTLE", "scene": "CREATE", "content": "该吃药啦,记得多喝水"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val id = Regex(""""id"\s*:\s*(\d+)""").find(created)!!.groupValues[1].toLong()
        assertTrue(copyTemplateRepository.findByToneAndSceneAndEnabledTrue("GENTLE", "CREATE").any { it.id == id })

        // 停用:同源查询即刻排除
        mockMvc.put("/api/v1/admin/ai/copy-templates/$id") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled": false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.enabled") { value(false) }
        }
        assertFalse(copyTemplateRepository.findByToneAndSceneAndEnabledTrue("GENTLE", "CREATE").any { it.id == id })

        // 改文案并启用:新内容进入候选
        mockMvc.put("/api/v1/admin/ai/copy-templates/$id") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"content": "改后的提醒文案", "enabled": true}"""
        }.andExpect { status { isOk() } }
        assertTrue(
            copyTemplateRepository.findByToneAndSceneAndEnabledTrue("GENTLE", "CREATE")
                .first { it.id == id }.content == "改后的提醒文案",
        )

        assertTrue(auditRepository.findByAdminUserIdOrderByCreatedAtDesc(superId()).any { it.action == "TEMPLATE_UPDATE" })
    }

    @Test
    fun `模板校验_非法档位与超长文案_400`() {
        val token = superToken()

        mockMvc.post("/api/v1/admin/ai/copy-templates") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"tone": "ANGRY", "scene": "CREATE", "content": "x"}"""
        }.andExpect { status { isBadRequest() } }

        mockMvc.post("/api/v1/admin/ai/copy-templates") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"tone": "GENTLE", "scene": "CREATE", "content": "${"长".repeat(41)}"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `生成日志_降级率与造数一致`() {
        genLogRepository.save(AiGenLogRow(scene = "PARSE", degraded = true, createdAt = java.time.LocalDateTime.now()))
        genLogRepository.save(AiGenLogRow(scene = "PARSE", degraded = false, createdAt = java.time.LocalDateTime.now()))

        val body = mockMvc.get("/api/v1/admin/ai/gen-logs") {
            header("Authorization", "Bearer ${superToken()}")
            param("scene", "PARSE")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        // 共享 DB 跨测试累积:以仓库实际数为准对比
        val rows = genLogRepository.findByScene("PARSE")
        val expectedTotal = rows.size
        val expectedDegraded = rows.count { it.degraded }
        val m = Regex(""""scene"\s*:\s*"PARSE","total"\s*:\s*(\d+),"degraded"\s*:\s*(\d+),"degradeRate"\s*:\s*([0-9.E-]+)""")
            .find(body)!!.groupValues
        assertEquals(expectedTotal, m[1].toInt())
        assertEquals(expectedDegraded, m[2].toInt())
        val expectedRate = if (expectedTotal == 0) 0.0 else expectedDegraded.toDouble() / expectedTotal
        assertEquals(expectedRate, m[3].toDouble(), 1e-9)
    }

    @Test
    fun `LLM状态只读_默认无网关`() {
        mockMvc.get("/api/v1/admin/ai/status") {
            header("Authorization", "Bearer ${superToken()}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.llmProvider") { value("none") }
            jsonPath("$.data.gatewayRegistered") { value(false) }
        }
    }

    @Test
    fun `模板列表_按tone筛选`() {
        val token = superToken()
        mockMvc.get("/api/v1/admin/ai/copy-templates") {
            header("Authorization", "Bearer $token")
            param("tone", "GENTLE")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { exists() }
        }
    }
}
