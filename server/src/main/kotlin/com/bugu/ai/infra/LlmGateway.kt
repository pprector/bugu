package com.bugu.ai.infra

/** LLM 网关返回的生成结果,tokens 供成本观测(落 ai_gen_log)。 */
data class LlmCopyResult(
    val content: String,
    val promptTokens: Int? = null,
    val outputTokens: Int? = null,
)

/** 网关不可用(超时/非 2xx/解析失败),调用方一律降级,不阻断提醒链路。 */
class LlmUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * LLM 网关抽象(项目约定允许接口抽象的两处之一,另一为 PushChannel)。
 * 供应商可替换:当前实现为 OpenAI 兼容协议(DeepSeek/通义/Kimi 均兼容)。
 * bugu.llm.provider=none(默认)时不注册 bean,全部走降级路径——拔 AI 即改配置。
 */
interface LlmGateway {

    /**
     * 通用生成:system/user 提示词 + 输出上限 + 温度,3s 超时预算,
     * 任何失败(超时/非 2xx/解析失败)抛 LlmUnavailableException,由调用方降级。
     */
    fun generateText(systemPrompt: String, userPrompt: String, maxTokens: Int, temperature: Double): LlmCopyResult

    /** 生成文案(场景化便捷入口,沿用文案场景默认参数)。 */
    fun generateCopy(prompt: String): LlmCopyResult =
        generateText(COPY_SYSTEM_PROMPT, prompt, COPY_MAX_OUTPUT_TOKENS, COPY_TEMPERATURE)

    companion object {
        /** 文案场景默认 system prompt(行为与原 OpenAiCompatibleLlmGateway 硬编码一致)。 */
        const val COPY_SYSTEM_PROMPT =
            "你是一位贴心的共享提醒文案助手,为家人、恋人、朋友之间的提醒生成简短文案。"

        /** 文案 ≤40 字,80 token 足够覆盖。 */
        const val COPY_MAX_OUTPUT_TOKENS = 80

        /** 每次生成内容不同(PRD FR-05)。 */
        const val COPY_TEMPERATURE = 0.9
    }
}
