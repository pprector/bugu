package com.bugu.shared.api

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * kotlinx-serialization 输出约定:默认值也要输出(code/message 不能因与默认值相同而被省略),
 * null 字段省略,未知字段忽略。
 */
@Configuration
class SerializationConfig {

    @Bean
    fun kotlinxJson(): Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
}
