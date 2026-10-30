package com.bugu.ai.infra

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/** OpenAiCompatibleLlmGateway(Spring AI 2.0 + OpenAI SDK)对 JDK 内置 HttpServer:正常解析、3s 超时、非 2xx。 */
class OpenAiCompatibleLlmGatewayTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    private fun startServer(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/chat/completions") { ex -> handler(ex) }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun gateway(baseUrl: String) = OpenAiCompatibleLlmGateway(
        OpenAiLlmProperties(baseUrl = baseUrl, apiKey = "test-key", model = "test-model"),
    )

    @Test
    fun `正常响应解析content与tokens`() {
        val baseUrl = startServer { ex ->
            val body = """{"id":"chatcmpl-test","object":"chat.completion","created":1700000000,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"记得吃药哦~"},"finish_reason":"stop"}],"usage":{"prompt_tokens":42,"completion_tokens":9,"total_tokens":51}}"""
            ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        val result = gateway(baseUrl).generateCopy("测试提示词")
        assertEquals("记得吃药哦~", result.content)
        assertEquals(42, result.promptTokens)
        assertEquals(9, result.outputTokens)
    }

    @Test
    fun `响应慢于3s抛LlmUnavailableException`() {
        val baseUrl = startServer { ex ->
            Thread.sleep(5_000)
            val body = """{"choices":[{"message":{"content":"迟到"}}]}"""
            runCatching {
                ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
                ex.responseBody.use { it.write(body.toByteArray()) }
            }
        }
        val start = System.nanoTime()
        assertThrows(LlmUnavailableException::class.java) {
            gateway(baseUrl).generateCopy("测试提示词")
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(elapsedMs < 4_500, "超时应远早于服务端 5s 响应,实际 ${elapsedMs}ms")
    }

    @Test
    fun `HTTP500抛LlmUnavailableException`() {
        val baseUrl = startServer { ex ->
            val body = """{"error":"boom"}"""
            ex.sendResponseHeaders(500, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        assertThrows(LlmUnavailableException::class.java) {
            gateway(baseUrl).generateCopy("测试提示词")
        }
    }

    @Test
    fun `响应缺少content抛LlmUnavailableException`() {
        val baseUrl = startServer { ex ->
            val body = """{"choices":[],"usage":{"prompt_tokens":1,"completion_tokens":0,"total_tokens":1}}"""
            ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        assertThrows(LlmUnavailableException::class.java) {
            gateway(baseUrl).generateCopy("测试提示词")
        }
    }
}
