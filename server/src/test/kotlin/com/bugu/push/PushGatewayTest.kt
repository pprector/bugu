package com.bugu.push

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** PushGateway 单元测试:无通道时返回 Skipped,不伪造成功。 */
class PushGatewayTest {

    @Test
    fun `无注册通道时返回Skipped`() {
        val gateway = PushGateway(emptyList())
        val result = gateway.send(
            PushMessage(token = "x", platform = Platform.ANDROID, title = "t", body = "b"),
        )
        assertInstanceOf(PushResult.Skipped::class.java, result)
    }
}
