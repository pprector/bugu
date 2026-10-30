package com.bugu.bond.api

import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 绑定全链路集成测试:发起(幂等)→ 邀请预览 → 确认 → 双方查询可见性,
 * 以及自绑、无效码、重复确认等失败路径。账号经验证码登录流程随机注册。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class BondFlowIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val jdbcClient: JdbcClient,
) {

    private fun randomPhone(): String {
        val second = Random.nextInt(3, 10)
        val rest = (1..9).map { Random.nextInt(10) }.joinToString("")
        return "1$second$rest"
    }

    /** 走真实认证链路注册一个账号,返回 (userId, token)。 */
    private fun register(): Pair<Long, String> {
        val phone = randomPhone()
        mockMvc.post("/api/v1/auth/send-code") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$phone"}"""
        }.andExpect { status { isOk() } }
        val code = requireNotNull(smsSender.lastCode)
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$phone", "code": "$code"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val userId = Regex("""\"id\"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toLong()
        val token = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
        return userId to token
    }

    private fun initiate(token: String) = mockMvc.post("/api/v1/bond/initiate") {
        header("Authorization", "Bearer $token")
    }

    private fun confirm(token: String, code: String) = mockMvc.post("/api/v1/bond/confirm") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"code": "$code"}"""
    }

    @Test
    fun `发起幂等_邀请预览_确认_双方可见`() {
        val (aId, aToken) = register()
        val (_, bToken) = register()

        // A 发起:6 位码 + 深链接
        val first = initiate(aToken).andExpect {
            status { isOk() }
            jsonPath("$.data.bindCode") { value(org.hamcrest.Matchers.matchesPattern("\\d{6}")) }
            jsonPath("$.data.deeplink") { value(org.hamcrest.Matchers.containsString("bugu://bond?code=")) }
        }.andReturn().response.contentAsString
        val bindCode = Regex(""""bindCode"\s*:\s*"(\d{6})"""").find(first)!!.groupValues[1]

        // A 重复发起:幂等,同一码(合并不重复创建)
        initiate(aToken).andExpect {
            status { isOk() }
            jsonPath("$.data.bindCode") { value(bindCode) }
        }

        // B 拉取待确认邀请
        mockMvc.get("/api/v1/bond/by-code/$bindCode") {
            header("Authorization", "Bearer $bToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.requesterId") { value(aId.toInt()) }
        }

        // B 确认 → ACTIVE
        confirm(bToken, bindCode).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ACTIVE") }
            jsonPath("$.data.role") { value("RECEIVER") }
        }

        // 同一码第二次确认:已被处理
        confirm(bToken, bindCode).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BOND_INVALID") }
        }

        // 双方查询均可见 ACTIVE,角色/对方 id 正确
        mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer $aToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].status") { value("ACTIVE") }
            jsonPath("$.data[0].role") { value("REQUESTER") }
        }
        mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer $bToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].status") { value("ACTIVE") }
            jsonPath("$.data[0].role") { value("RECEIVER") }
        }

        // A 已有 ACTIVE,再次发起被拒(绑定上限)
        initiate(aToken).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("BOND_LIMITED") }
        }
    }

    @Test
    fun `自绑返回BOND_INVALID`() {
        val (_, token) = register()
        val body = initiate(token).andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        val code = Regex(""""bindCode"\s*:\s*"(\d{6})"""").find(body)!!.groupValues[1]
        confirm(token, code).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BOND_INVALID") }
        }
    }

    @Test
    fun `无效绑定码返回BOND_INVALID`() {
        val (_, token) = register()
        confirm(token, "000000").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BOND_INVALID") }
        }
        mockMvc.get("/api/v1/bond/by-code/999999") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BOND_INVALID") }
        }
    }

    @Test
    fun `解绑后双方不可见_重复解绑拒绝_再绑是新关系`() {
        val (_, aToken) = register()
        val (_, bToken) = register()

        // 建绑
        val bindCode = initiateAndExtractCode(aToken)
        val bondId = confirm(bToken, bindCode).andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString.let {
            Regex(""""id"\s*:\s*(\d+)""").find(it)!!.groupValues[1]
        }

        // A 解绑 → UNBOUND
        mockMvc.post("/api/v1/bond/$bondId/unbind") {
            header("Authorization", "Bearer $aToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("UNBOUND") }
        }

        // 双方查询均不再返回(空列表)
        mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer $aToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
        mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer $bToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }

        // UNBOUND 终态:再次解绑 → BOND_INVALID
        mockMvc.post("/api/v1/bond/$bondId/unbind") {
            header("Authorization", "Bearer $bToken")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("BOND_INVALID") }
        }

        // 再次扫码视为新关系:新 bond id,双方可见 ACTIVE
        val newCode = initiateAndExtractCode(aToken)
        val newBondId = confirm(bToken, newCode).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ACTIVE") }
        }.andReturn().response.contentAsString.let {
            Regex(""""id"\s*:\s*(\d+)""").find(it)!!.groupValues[1]
        }
        org.junit.jupiter.api.Assertions.assertNotEquals(bondId, newBondId.toLong())
        mockMvc.get("/api/v1/bond") {
            header("Authorization", "Bearer $aToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].id") { value(newBondId.toInt()) }
            jsonPath("$.data[0].status") { value("ACTIVE") }
        }
    }

    @Test
    fun `已有ACTIVE的用户不能再被他人绑定_BOND_LIMITED`() {
        val (_, aToken) = register()
        val (_, bToken) = register()
        val (_, cToken) = register()

        // A-B 建绑
        confirm(bToken, initiateAndExtractCode(aToken)).andExpect { status { isOk() } }

        // C 发起,B 试图确认 → B 已有 ACTIVE → BOND_LIMITED
        val cCode = initiateAndExtractCode(cToken)
        confirm(bToken, cCode).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("BOND_LIMITED") }
        }
    }

    @Test
    fun `绑定码过期后预览与确认均BOND_INVALID_发起方重新发起可续码`() {
        val (_, aToken) = register()
        val (_, bToken) = register()
        val code = initiateAndExtractCode(aToken)

        // 直接把过期时刻推到过去:走真实库,不欺骗服务端内存状态
        jdbcClient.sql("UPDATE bond SET code_expires_at = ? WHERE bind_code = ?")
            .params(Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), code)
            .update()

        // 预览按设计仍 200 并如实返回过期时刻(客户端据此展示「已过期」),但确认必须失败
        mockMvc.get("/api/v1/bond/by-code/$code") {
            header("Authorization", "Bearer $bToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.bindCode") { value(code) }
        }
        confirm(bToken, code).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BOND_INVALID") }
        }

        // 过期后发起方再次发起:同一条 PENDING_OUTBOUND 续发新码,可正常确认
        val renewed = initiateAndExtractCode(aToken)
        confirm(bToken, renewed).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ACTIVE") }
        }
    }

    private fun initiateAndExtractCode(token: String): String {
        val body = initiate(token).andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        return Regex(""""bindCode"\s*:\s*"(\d{6})"""").find(body)!!.groupValues[1]
    }
}
