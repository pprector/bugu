package com.bugu.push.infra

import com.bugu.push.Platform
import com.bugu.push.PushChannel
import com.bugu.push.PushMessage
import com.bugu.push.PushResult
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * 控制台推送通道(MVP 真实通道占位,日志可验)。
 * 由 [PushModuleConfig] 按 bugu.push.provider 装配(默认 console);jiguang 启用时由极光通道替代。
 */
class ConsolePushChannel : PushChannel {

    override val platform: Platform = Platform.ANDROID

    private val log = LoggerFactory.getLogger(javaClass)
    private val seq = AtomicLong(0)

    override fun send(message: PushMessage): PushResult {
        log.info(
            "[PUSH-CONSOLE] platform={} token={} title={} body={} extras={}",
            platform, message.token.take(8) + "***", message.title, message.body, message.extras,
        )
        return PushResult.Accepted("console-${seq.incrementAndGet()}")
    }
}
