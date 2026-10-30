package com.bugu.bond.api

import com.bugu.bond.app.ElderBondService
import com.bugu.identity.api.CurrentUser
import com.bugu.identity.api.UserDto
import com.bugu.shared.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 长辈免注册接入接口(epic Requirements R6)。
 * elder-register 路径挂在 /api/v1/auth 前缀下但归 bond 模块(建号+建 bond 复合动作,bond 到 identity 单向依赖)。
 */
@RestController
class ElderController(private val elderBondService: ElderBondService) {

    @PostMapping("/api/v1/auth/elder-register")
    fun elderRegister(@RequestBody request: ElderRegisterRequest): ApiResponse<ElderRegisterResponse> =
        ApiResponse.ok(elderBondService.register(request.deviceUuid, request.deviceModel, request.inviteCode))

    @PostMapping("/api/v1/bond/elder-profile")
    fun elderProfile(currentUser: CurrentUser, @RequestBody request: ElderProfileRequest): ApiResponse<ElderProfileDto> =
        ApiResponse.ok(elderBondService.updateElderProfile(currentUser.id, request))

    /** 子女端标记/取消 bond 对方为长辈模式(FR-06):即时生效。 */
    @PostMapping("/api/v1/bond/elder-mode")
    fun setElderMode(currentUser: CurrentUser, @RequestBody request: ElderModeRequest): ApiResponse<UserDto> =
        ApiResponse.ok(elderBondService.setElderMode(currentUser.id, request.peerId, request.enabled))
}
