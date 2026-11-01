package com.bugu.identity.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 短信通道装配:console 默认、volc 缺配置启动期拒绝、volc 配置齐全返回真实实现。 */
class IdentityConfigTest {

    private val config = IdentityConfig()

    @Test
    fun `console通道返回控制台实现`() {
        assertTrue(config.smsSender(SmsProperties()) is ConsoleSmsSender)
        assertTrue(config.smsSender(SmsProperties(provider = "CONSOLE")) is ConsoleSmsSender)
    }

    @Test
    fun `volc缺配置拒绝启动`() {
        val props = SmsProperties(provider = "volc", volc = VolcSmsProperties())
        val error = assertThrows(IllegalArgumentException::class.java) { config.smsSender(props) }
        assertTrue(error.message.orEmpty().contains("BUGU_SMS_VOLC_"))
    }

    @Test
    fun `volc配置齐全返回火山实现`() {
        val volc = VolcSmsProperties(
            ak = "ak",
            sk = "sk",
            sign = "签名",
            templateId = "SPT_test",
            smsAccount = "account",
        )
        val sender = config.smsSender(SmsProperties(provider = "volc", volc = volc))
        assertTrue(sender is VolcSmsSender)
        assertEquals("ak", volc.ak)
    }
}
