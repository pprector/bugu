package com.bugu.bond.api

import com.bugu.bond.app.BondService
import com.bugu.identity.api.CurrentUser
import com.bugu.shared.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 1v1 绑定接口(epic Requirements R3/R5):绑定码发起、确认、双方查询。
 */
@RestController
@RequestMapping("/api/v1/bond")
class BondController(private val bondService: BondService) {

    @PostMapping("/initiate")
    fun initiate(currentUser: CurrentUser): ApiResponse<InitiateResponse> =
        ApiResponse.ok(bondService.initiate(currentUser.id))

    @PostMapping("/confirm")
    fun confirm(currentUser: CurrentUser, @RequestBody request: ConfirmRequest): ApiResponse<BondDto> =
        ApiResponse.ok(bondService.confirm(currentUser.id, request.code))

    @GetMapping("/by-code/{code}")
    fun invitation(@PathVariable code: String): ApiResponse<InvitationDto> =
        ApiResponse.ok(bondService.invitation(code))

    @GetMapping
    fun mine(currentUser: CurrentUser): ApiResponse<List<BondDto>> =
        ApiResponse.ok(bondService.listMine(currentUser.id))

    @PostMapping("/{id}/unbind")
    fun unbind(currentUser: CurrentUser, @PathVariable id: Long): ApiResponse<BondDto> =
        ApiResponse.ok(bondService.unbind(currentUser.id, id))
}
