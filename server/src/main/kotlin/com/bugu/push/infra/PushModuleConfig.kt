package com.bugu.push.infra

import com.bugu.push.PushChannel
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * push 模块装配(E8-S5):通道选择经 bugu.push.provider。
 * console=控制台假实现(开发/测试);jiguang=极光真实通道(AppKey/MasterSecret 写在 application.yml)。
 */
@Configuration
@EnableConfigurationProperties(PushProperties::class, JiguangProperties::class)
class PushModuleConfig {

    @Bean
    fun pushChannels(props: PushProperties): List<PushChannel> = when (props.provider.lowercase()) {
        "jiguang" -> {
            // 启动期防呆:真实通道缺凭证直接拒绝启动(极光侧表现为无厘头的 Authen failed)
            require(props.jiguang.appKey.isNotBlank() && props.jiguang.masterSecret.isNotBlank()) {
                "极光推送通道已启用但 AppKey/MasterSecret 为空:生产请设置环境变量 BUGU_PUSH_JIGUANG_APP_KEY / BUGU_PUSH_JIGUANG_MASTER_SECRET"
            }
            listOf(JiguangPushChannel(props.jiguang))
        }
        else -> listOf(ConsolePushChannel())
    }
}
