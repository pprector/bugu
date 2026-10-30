package com.bugu.bond.api

import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc

/**
 * 绑定码确认防爆破:同一确认方窗口内连续失败 5 次锁定,
 * 锁内连正确码也 429;确认成功后失败计数清零。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ConfirmThrottleIntegrationTest @Autowired constructor(
    mockMvc: MockMvc,
    smsSender: ConsoleSmsSender,
) {

    private val client = AuthTestClient(mockMvc, smsSender)

    @Test
    fun `连续失败达上限后锁定_锁内正确码也拒绝`() {
        val requester = client.register()
        val confirmer = client.register()
        val bindCode = client.initiateAndGetCode(requester.token)

        // 连续 5 次错误码:前 4 次 400,第 5 次 400 并写入锁
        repeat(5) {
            client.confirm(confirmer.token, "000000").andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("BOND_INVALID") }
            }
        }

        // 第 6 次:即使正确码也被限流拒绝
        client.confirm(confirmer.token, bindCode).andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("BOND_INVALID") }
        }
    }

    @Test
    fun `确认成功后失败计数清零`() {
        val requester = client.register()
        val confirmer = client.register()

        // 先失败 3 次(未达上限)
        repeat(3) {
            client.confirm(confirmer.token, "000000").andExpect { status { isBadRequest() } }
        }

        // 确认成功:计数清零
        client.confirm(confirmer.token, client.initiateAndGetCode(requester.token)).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ACTIVE") }
        }

        // 已有 ACTIVE 后用无效码 confirm 仍计次(码查找先于上限检查,返回 400):
        // 若计数未清零,2 次失败即锁;清零后需重新累计 5 次
        repeat(5) {
            client.confirm(confirmer.token, "000000").andExpect { status { isBadRequest() } }
        }
        client.confirm(confirmer.token, "000000").andExpect { status { isTooManyRequests() } }
    }
}
