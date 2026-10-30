package com.bugu.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * 全局时钟:P0 仅中国大陆单时区(C6)。domain 纯函数与测试注入此 Clock。
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Shanghai"))
}
