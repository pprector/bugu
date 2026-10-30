package com.bugu.admin.infra

import com.bugu.admin.app.AdminSeedProperties
import com.bugu.identity.infra.JwtTokenService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * admin 模块装配(E9):AdminAuthFilter 注册 order=3
 * (JwtAuthFilter=1、ElderModeAccessFilter=2 之后)。
 */
@Configuration
@EnableConfigurationProperties(AdminSeedProperties::class)
class AdminConfig {

    @Bean
    fun adminAuthFilterRegistration(jwt: JwtTokenService, repository: AdminUserRepository): FilterRegistrationBean<AdminAuthFilter> {
        val registration = FilterRegistrationBean(AdminAuthFilter(jwt, repository))
        registration.addUrlPatterns("/api/v1/admin/*")
        registration.order = 3
        return registration
    }
}
