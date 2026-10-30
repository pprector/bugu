package com.bugu.push

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 客户端平台。 */
enum class Platform { ANDROID, IOS, HARMONY }

data class PushMessage(
    val token: String,
    val platform: Platform,
    val title: String,
    val body: String,
    val extras: Map<String, String> = emptyMap(),
)

sealed interface PushResult {
    data class Accepted(val channelMessageId: String) : PushResult
    data class Failed(val reason: String, val retryable: Boolean) : PushResult
    data object Skipped : PushResult
}

/**
 * 通道抽象(技术架构 AD-04)。聚合 SDK / APNs / Push Kit 各自实现一个,业务只依赖本接口。
 */
interface PushChannel {
    val platform: Platform
    fun send(message: PushMessage): PushResult
}

/**
 * 推送网关:按平台路由到已注册的通道。当前没有任何通道实现时返回 Skipped,不伪造成功。
 */
@Service
class PushGateway(channels: List<PushChannel>) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val byPlatform: Map<Platform, PushChannel> = channels.associateBy { it.platform }

    fun send(message: PushMessage): PushResult {
        val channel = byPlatform[message.platform]
        if (channel == null) {
            log.warn("no push channel registered for platform={}, message skipped", message.platform)
            return PushResult.Skipped
        }
        return channel.send(message)
    }
}
