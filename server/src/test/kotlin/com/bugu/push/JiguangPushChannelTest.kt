package com.bugu.push

import com.bugu.push.infra.JiguangProperties
import com.bugu.push.infra.JiguangPushChannel
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * JiguangPushChannel 单元测试(E8-S5):内嵌 HttpServer 模拟极光 v3/push 三态——
 * 2xx→Accepted(msg_id);4xx error→Failed 不可重试;5xx/网络异常→Failed 可重试。
 * 同时断言请求组装:HTTP Basic 头与 registration_id 定向。
 */
class JiguangPushChannelTest {

    private lateinit var server: HttpServer
    private val responseBody = AtomicReference("""{"sendno":"0","msg_id":"1828256768"}""")
    private val responseStatus = AtomicReference(200)
    private val lastAuth = AtomicReference("")
    private val lastBody = AtomicReference("")

    private val baseProps = JiguangProperties(appKey = "test-appkey", masterSecret = "test-secret")

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/v3/push") { exchange ->
            lastAuth.set(exchange.requestHeaders.getFirst("Authorization") ?: "")
            lastBody.set(exchange.requestBody.readAllBytes().toString(Charsets.UTF_8))
            val bytes = responseBody.get().toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(responseStatus.get(), bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    private fun channel(): JiguangPushChannel =
        JiguangPushChannel(baseProps, baseUri = "http://localhost:${server.address.port}")

    private fun message(token: String = "1a0018970a2b3c4d5e6f") =
        PushMessage(
            token = token,
            platform = Platform.ANDROID,
            title = "记得吃药",
            body = "记得吃药",
            extras = mapOf("fireId" to "1544", "stage" to "T1"),
        )

    @Test
    fun `2xx返回Accepted携带msg_id`() {
        val result = channel().send(message())
        assertTrue(result is PushResult.Accepted)
        assertEquals("1828256768", (result as PushResult.Accepted).channelMessageId)
    }

    @Test
    fun `请求组装为Basic认证与registration_id定向`() {
        channel().send(message())
        // base64("test-appkey:test-secret")
        assertEquals("Basic ${java.util.Base64.getEncoder().encodeToString("test-appkey:test-secret".toByteArray())}", lastAuth.get())
        assertTrue(lastBody.get().contains("\"registration_id\":[\"1a0018970a2b3c4d5e6f\"]"))
        assertTrue(lastBody.get().contains("\"fireId\":\"1544\""))
        assertTrue(lastBody.get().contains("\"alert\":\"记得吃药\""))
        // 通知栏标题固定应用名「布谷」
        assertTrue(lastBody.get().contains("\"title\":\"布谷\""))
    }

    @Test
    fun `极光业务错误4xx为Failed且不可重试`() {
        responseStatus.set(400)
        responseBody.set("""{"error":{"code":1003,"message":"audience is invalid"}}""")
        val result = channel().send(message())
        assertTrue(result is PushResult.Failed)
        assertFalse((result as PushResult.Failed).retryable)
        assertTrue(result.reason.contains("1003"))
    }

    @Test
    fun `429限流为Failed但可重试`() {
        responseStatus.set(429)
        responseBody.set("""{"error":{"code":2002,"message":"too many requests"}}""")
        val result = channel().send(message())
        assertTrue(result is PushResult.Failed)
        assertTrue((result as PushResult.Failed).retryable)
    }

    @Test
    fun `5xx为Failed且可重试`() {
        responseStatus.set(500)
        responseBody.set("internal error")
        val result = channel().send(message())
        assertTrue(result is PushResult.Failed)
        assertTrue((result as PushResult.Failed).retryable)
    }

    @Test
    fun `网络异常为Failed且可重试`() {
        // 连接一个必然拒绝的端口模拟网络故障
        val deadPort = 1
        val broken = JiguangPushChannel(baseProps, baseUri = "http://localhost:$deadPort")
        val result = broken.send(message())
        assertTrue(result is PushResult.Failed)
        assertTrue((result as PushResult.Failed).retryable)
    }
}
