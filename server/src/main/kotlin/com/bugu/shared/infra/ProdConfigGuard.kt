package com.bugu.shared.infra

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Profiles

/**
 * 生产 profile 启动防呆:JWT 密钥与后台初始口令必须经环境变量注入,
 * 缺失在上下文创建前就拒绝启动并报出变量名,避免把仓库内的开发默认值带进生产。
 *
 * 不用 `${VAR}` 占位符做失败的原因:@ConfigurationProperties 绑定会忽略无法解析的占位符
 * (实测会把 `${BUGU_JWT_SECRET}` 原样当作密钥),无法可靠 fail-fast。
 */
class ProdConfigGuard : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) return

        val jwtSecret = System.getenv("BUGU_JWT_SECRET")
        val adminSeedPassword = System.getenv("BUGU_ADMIN_SEED_PASSWORD")
        val missing = buildList {
            if (jwtSecret.isNullOrBlank()) add("BUGU_JWT_SECRET")
            if (adminSeedPassword.isNullOrBlank()) add("BUGU_ADMIN_SEED_PASSWORD")
        }
        check(missing.isEmpty()) {
            "prod profile 缺少必需环境变量: ${missing.joinToString(", ")};" +
                "请参考 docker-compose.prod.yml 注入后重试"
        }
        check(jwtSecret!!.length >= 32) {
            "环境变量 BUGU_JWT_SECRET 仅 ${jwtSecret.length} 字节,不足 32 字节;请用 openssl rand -base64 48 生成"
        }
    }
}
