package com.bugu.admin.api

import com.bugu.admin.infra.AdminStatsQuery
import com.bugu.shared.api.ApiResponse
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 后台运营总览视图:六项关键计数。 */
@Serializable
data class AdminStatsView(
    val userTotal: Long,
    val userElder: Long,
    val bondActive: Long,
    val reminderPending: Long,
    val fireToday: Long,
    val aiGenToday: Long,
)

/**
 * 运营总览:纯只读,OPS/SUPER 均可(AdminAuthFilter 对 GET 不要求 admin:write)。
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminStatsController(private val query: AdminStatsQuery) {

    @GetMapping("/stats")
    fun stats(): ApiResponse<AdminStatsView> = ApiResponse.ok(query.stats())
}
