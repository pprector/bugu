package com.bugu.ai.infra

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** LLM 网关开关:openai=OpenAI 兼容真实通道;none(默认)=不注册,全部走模板降级。 */
@ConfigurationProperties(prefix = "bugu.llm")
data class LlmProperties(
    val provider: String = "none",
)

/** ai 模块装配:LLM 网关按 provider 注册,缺省无网关(AI 是增强项不是依赖项)。 */
@Configuration
@EnableConfigurationProperties(LlmProperties::class, OpenAiLlmProperties::class)
class AiConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bugu.llm", name = ["provider"], havingValue = "openai")
    fun llmGateway(props: OpenAiLlmProperties): LlmGateway = OpenAiCompatibleLlmGateway(props)
}
