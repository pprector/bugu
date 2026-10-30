package com.bugu.push.infra

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 推送通道配置(E8-S5,D4 定案极光 JPush):
 * console=控制台假实现(开发/测试);jiguang=极光真实通道。
 */
@ConfigurationProperties(prefix = "bugu.push")
data class PushProperties(
    val provider: String = "console",
    val jiguang: JiguangProperties = JiguangProperties(),
)

/**
 * 极光 JPush 配置。AppKey/MasterSecret 不入库:本地放 server/application-local.yml(gitignore),生产经 compose 环境变量覆盖。
 * proxyHost/proxyPort:本地开发经代理出网用(如 Clash 7897);留空则直连(生产)。
 */
@ConfigurationProperties(prefix = "bugu.push.jiguang")
data class JiguangProperties(
    val appKey: String = "",
    val masterSecret: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
)
