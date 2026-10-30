package com.bugu.push

import com.bugu.push.infra.ConsolePushChannel
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** ConsolePushChannel 单元测试:返回 Accepted 且 channelMessageId 非空。 */
class ConsolePushChannelTest {

    private val channel = ConsolePushChannel()

    @Test
    fun `send返回Accepted且带消息id`() {
        val result = channel.send(
            PushMessage(token = "test-token", platform = Platform.ANDROID, title = "t", body = "b"),
        )
        assertInstanceOf(PushResult.Accepted::class.java, result)
        assertNotNull((result as PushResult.Accepted).channelMessageId)
    }
}
