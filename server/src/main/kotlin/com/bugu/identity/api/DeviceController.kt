package com.bugu.identity.api

import com.bugu.identity.infra.DeviceTokenRepository
import com.bugu.identity.infra.DeviceTokenRow
import com.bugu.push.Platform
import com.bugu.shared.api.ApiResponse
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/** 设备令牌上报请求(E8-S5):极光 registrationID 或后续厂商 token。 */
@Serializable
data class DeviceTokenRequest(
    val pushToken: String,
    val platform: String,
    val pushEnabled: Boolean = true,
    val vendor: String = "JIGUANG",
)

/** 上报回执视图。 */
@Serializable
data class DeviceTokenView(
    val id: Long,
    val platform: String,
    val vendor: String,
    val pushEnabled: Boolean,
)

/**
 * 设备令牌上报(E8-S5):推送 SDK 初始化拿到 registrationID 后登记,升级链推送按 user_id→token 定向。
 * push_token 唯一(uk_push_token),重复上报 upsert(换账号登录时 token 归属随最新登录者)。
 * 长辈模式可上报( ElderModeAccessFilter 白名单)——长辈端同样要收升级链推送。
 */
@RestController
@RequestMapping("/api/v1/devices")
class DeviceController(private val deviceTokenRepository: DeviceTokenRepository) {

    @PostMapping("/token")
    fun reportToken(currentUser: CurrentUser, @RequestBody request: DeviceTokenRequest): ApiResponse<DeviceTokenView> {
        val token = request.pushToken.trim()
        if (token.isEmpty() || token.length > 512) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, message = "pushToken 非法")
        }
        if (runCatching { Platform.valueOf(request.platform) }.isFailure) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, message = "platform 非法")
        }
        val now = LocalDateTime.now()
        val existing = deviceTokenRepository.findByPushToken(token)
        val row = existing?.copy(
            userId = currentUser.id,
            platform = request.platform,
            pushEnabled = request.pushEnabled,
            lastActiveAt = now,
            updatedAt = now,
        ) ?: DeviceTokenRow(
            userId = currentUser.id,
            platform = request.platform,
            vendor = request.vendor,
            pushToken = token,
            pushEnabled = request.pushEnabled,
            lastActiveAt = now,
        )
        val saved = deviceTokenRepository.save(row)
        return ApiResponse.ok(
            DeviceTokenView(
                id = requireNotNull(saved.id) { "device token id 不应为空" },
                platform = saved.platform,
                vendor = saved.vendor ?: "JIGUANG",
                pushEnabled = saved.pushEnabled,
            ),
        )
    }
}
