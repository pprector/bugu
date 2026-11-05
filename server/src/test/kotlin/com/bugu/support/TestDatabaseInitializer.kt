package com.bugu.support

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 集成测试数据源隔离:整个测试 JVM 共用一个一次性 PostgreSQL 容器。
 * 经 META-INF/spring.factories 注册,对所有 @SpringBootTest 生效,
 * 从而覆盖 application.yml 里的 dev 数据源——测试永远不会写本地开发库,更不可能指向生产。
 */
class TestDatabaseInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(context: ConfigurableApplicationContext) {
        val container = CONTAINER
        val overrides = mapOf(
            "spring.datasource.url" to container.jdbcUrl,
            "spring.datasource.username" to container.username,
            "spring.datasource.password" to container.password,
            "spring.datasource.driver-class-name" to "org.postgresql.Driver",
            // 多个测试上下文会各自缓存连接池:压到 2 条,避免容器 max_connections 被打满
            "spring.datasource.hikari.maximum-pool-size" to "2",
            "spring.datasource.hikari.minimum-idle" to "1",
            // 测试库全新无 SUPER:注入固定口令,保证各集成测试可稳定登录
            "bugu.admin.seed-password" to TEST_SEED_PASSWORD,
        )
        context.environment.propertySources.addFirst(MapPropertySource("testDatabase", overrides))
    }

    private companion object {
        /** 与测试代码里的 super 登录口令保持一致(仅测试环境,不进入任何运行时配置)。 */
        const val TEST_SEED_PASSWORD = "bugu-admin-2026"

        val CONTAINER: PostgreSQLContainer by lazy {
            PostgreSQLContainer("postgres:16-alpine").apply {
                // 测试上下文多(每个缓存一个连接池),放宽连接上限
                withCommand("postgres", "-c", "max_connections=300")
                start()
            }
        }
    }
}
