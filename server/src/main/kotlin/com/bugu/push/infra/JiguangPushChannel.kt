package com.bugu.push.infra

import com.bugu.push.Platform
import com.bugu.push.PushChannel
import com.bugu.push.PushMessage
import com.bugu.push.PushResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * 极光 JPush 真实通道(E8-S5,D4 定案)。
 * REST v3/push:POST {baseUri}/v3/push,HTTP Basic base64(appKey:masterSecret),registration_id 定向。
 * 服务端零 SDK 手写照 [com.bugu.identity.infra.VolcSmsSender] 先例;凭据经 [JiguangProperties] 环境注入。
 *
 * 结果语义:2xx→Accepted(msg_id);极光业务错误(4xx)→Failed(不可重试,429 限流除外);
 * 网络/5xx→Failed(可重试)。不抛异常——推送失败不影响升级链主流程,如实落 escalation_log。
 */
class JiguangPushChannel(
    private val props: JiguangProperties,
    private val baseUri: String = "https://api.jpush.cn",
) : PushChannel {

    override val platform: Platform = Platform.ANDROID

    private val log = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }
    private val basicToken: String = Base64.getEncoder()
        .encodeToString("${props.appKey}:${props.masterSecret}".toByteArray(Charsets.UTF_8))
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .apply {
            if (props.proxyHost.isNotBlank() && props.proxyPort > 0) {
                proxy(ProxySelector.of(InetSocketAddress(props.proxyHost, props.proxyPort)))
            }
        }
        .build()

    override fun send(message: PushMessage): PushResult {
        val payload = buildJsonObject {
            put("platform", "all")
            putJsonObject("audience") {
                putJsonArray("registration_id") {
                    add(message.token)
                }
            }
            putJsonObject("notification") {
                putJsonObject("android") {
                    // 通知栏标题固定应用名(产品定名「布谷」),alert 用正文——PushService 的 title 与 body 同为全文
                    put("alert", message.body)
                    put("title", APP_NAME)
                    putJsonObject("extras") {
                        message.extras.forEach { (k, v) -> put(k, v) }
                    }
                }
            }
        }.toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUri/v3/push"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic $basicToken")
            .POST(HttpRequest.BodyPublishers.ofString(payload, Charsets.UTF_8))
            .build()

        val response: HttpResponse<String> = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            log.error("极光推送请求失败 token=***{}", message.token.takeLast(4), e)
            return PushResult.Failed(reason = "network: ${e.javaClass.simpleName}", retryable = true)
        }

        if (response.statusCode() in 200..299) {
            val msgId = runCatching {
                json.parseToJsonElement(response.body()).jsonObject["msg_id"]?.jsonPrimitive?.content
            }.getOrNull() ?: "unknown"
            log.info("极光推送已提交 token=***{} msg_id={}", message.token.takeLast(4), msgId)
            return PushResult.Accepted(msgId)
        }

        val error = runCatching {
            json.parseToJsonElement(response.body()).jsonObject["error"]?.jsonObject
        }.getOrNull()
        val code = error?.get("code")?.jsonPrimitive?.content ?: "${response.statusCode()}"
        val desc = error?.get("message")?.jsonPrimitive?.content ?: response.body().take(200)
        // 429 限流与 5xx 可重试,其余 4xx(凭据错/无效 regid 等)不可重试
        val retryable = response.statusCode() == 429 || response.statusCode() >= 500
        log.error("极光推送被拒 token=***{} code={} message={}", message.token.takeLast(4), code, desc)
        return PushResult.Failed(reason = "jiguang:$code $desc", retryable = retryable)
    }

    companion object {
        /** 应用名(通知栏标题)。 */
        const val APP_NAME = "布谷"
    }
}
