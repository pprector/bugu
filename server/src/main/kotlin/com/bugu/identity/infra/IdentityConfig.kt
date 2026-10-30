package com.bugu.identity.infra

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import com.bugu.identity.api.CurrentUserResolver

/**
 * 短信通道配置:console=控制台假实现(开发/测试),volc=火山引擎真实通道。
 */
@ConfigurationProperties(prefix = "bugu.sms")
data class SmsProperties(
    val provider: String = "console",
    val volc: VolcSmsProperties = VolcSmsProperties(),
)

/**
 * identity 模块装配:短信通道选择、JWT 过滤器注册与 CurrentUser 参数解析。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties::class, SmsProperties::class, VolcSmsProperties::class)
class IdentityConfig {

    @Bean
    fun smsSender(props: SmsProperties): SmsSender = when (props.provider.lowercase()) {
        "volc" -> {
            // 启动期防呆:真实通道缺任一必需配置直接拒绝启动,避免运行时才报 RE:0009 之类晦涩错误
            require(
                props.volc.ak.isNotBlank() && props.volc.sk.isNotBlank() &&
                    props.volc.smsAccount.isNotBlank() && props.volc.sign.isNotBlank() &&
                    props.volc.templateId.isNotBlank()
            ) {
                "火山短信通道已启用但 AK/SK/消息组ID/签名/模板ID 存在空值:请检查 application.yml 与环境变量 BUGU_SMS_VOLC_*"
            }
            VolcSmsSender(props.volc)
        }
        else -> ConsoleSmsSender()
    }

    @Bean
    fun jwtAuthFilterRegistration(jwt: JwtTokenService): FilterRegistrationBean<JwtAuthFilter> {
        val registration = FilterRegistrationBean(JwtAuthFilter(jwt))
        registration.addUrlPatterns("/api/v1/*")
        registration.order = 1
        return registration
    }

    /** 长辈模式访问控制:order=2,在 JWT 鉴权之后。 */
    @Bean
    fun elderModeAccessFilterRegistration(userRepository: UserRepository): FilterRegistrationBean<ElderModeAccessFilter> {
        val registration = FilterRegistrationBean(ElderModeAccessFilter(userRepository))
        registration.addUrlPatterns("/api/v1/*")
        registration.order = 2
        return registration
    }
}

@Configuration
class IdentityWebConfig(private val resolver: CurrentUserResolver) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<org.springframework.web.method.support.HandlerMethodArgumentResolver>) {
        resolvers.add(resolver)
    }
}
