package com.bugu.ai.infra

import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * OpenAI 兼容协议配置(经 Spring AI 2.0 OpenAiChatModel + OpenAI 官方 Java SDK 访问),api-key 不入库:本地放 server/application-local.yml,生产经环境变量注入。
 * baseUrl 示例:DeepSeek https://api.deepseek.com、通义 https://dashscope.aliyuncs.com/compatible-mode/v1、Kimi https://api.moonshot.cn/v1。
 */
@ConfigurationProperties(prefix = "bugu.llm.openai")
data class OpenAiLlmProperties(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

/**
 * OpenAI 兼容协议 LLM 网关(LlmGateway 实现),底层 Spring AI 2.0 OpenAiChatModel(OpenAI 官方 Java SDK)。
 * POST {baseUrl}/chat/completions,单请求 3s 超时预算(epic Decision:LLM 生成超时预算 3s),
 * maxRetries=0 关闭 SDK 内置重试(预算内单次尝试)。
 * 失败(超时/非 2xx/解析失败)一律抛 LlmUnavailableException,由调用方降级。
 */
class OpenAiCompatibleLlmGateway(
    props: OpenAiLlmProperties,
) : LlmGateway {

    private val chatModel: OpenAiChatModel = OpenAiChatModel.builder()
        // baseUrl/apiKey/timeout/maxRetries 集中在 options,Spring AI 据此构建 sync/async client;
        // maxTokens/temperature 按场景在每次请求时传入,模型与连接参数保持默认
        .options(
            OpenAiChatOptions.builder()
                .model(props.model)
                .baseUrl(props.baseUrl)
                .apiKey(props.apiKey)
                .timeout(REQUEST_TIMEOUT)
                .maxRetries(0)
                .build(),
        )
        .build()

    override fun generateText(systemPrompt: String, userPrompt: String, maxTokens: Int, temperature: Double): LlmCopyResult {
        val response = try {
            chatModel.call(
                Prompt(
                    listOf(SystemMessage(systemPrompt), UserMessage(userPrompt)),
                    OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .temperature(temperature)
                        .build(),
                ),
            )
        } catch (e: Exception) {
            throw LlmUnavailableException("LLM 请求失败: ${e.message}", e)
        }
        val content = response.results.firstOrNull()?.output?.text
            ?: throw LlmUnavailableException("LLM 响应缺少 content")
        val usage = response.metadata.usage
        return LlmCopyResult(
            content = content,
            promptTokens = usage.promptTokens,
            outputTokens = usage.completionTokens,
        )
    }

    companion object {
        /** 生成超时预算(epic Assumption:3s,超时落模板库);SDK callTimeout 覆盖整个请求。 */
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
