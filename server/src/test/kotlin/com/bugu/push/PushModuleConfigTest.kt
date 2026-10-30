package com.bugu.push

import com.bugu.push.infra.ConsolePushChannel
import com.bugu.push.infra.JiguangProperties
import com.bugu.push.infra.JiguangPushChannel
import com.bugu.push.infra.PushModuleConfig
import com.bugu.push.infra.PushProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** 通道装配测试(E8-S5):provider 决定 console / jiguang,App token 体系不受影响。 */
class PushModuleConfigTest {

    @Test
    fun `默认装配console通道`() {
        val channels = PushModuleConfig().pushChannels(PushProperties())
        assertEquals(1, channels.size)
        assertInstanceOf(ConsolePushChannel::class.java, channels[0])
    }

    @Test
    fun `jiguang装配极光通道`() {
        val props = PushProperties(provider = "jiguang", jiguang = JiguangProperties(appKey = "k", masterSecret = "s"))
        val channels = PushModuleConfig().pushChannels(props)
        assertEquals(1, channels.size)
        assertInstanceOf(JiguangPushChannel::class.java, channels[0])
    }
}
