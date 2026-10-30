package com.bugu.scheduling

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * 调度锁配置:升级链扫描任务在多副本部署下只允许一个实例执行(技术架构 2.3)。
 *
 * 底座阶段用 JDBC(MySQL)实现,避免本地开发强依赖 Redis;
 * 后续接入缓存层时可平滑替换为 RedisLockProvider,业务代码无需改动。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
class SchedulingConfig {

    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        )
}
