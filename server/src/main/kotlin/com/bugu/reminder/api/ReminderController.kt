package com.bugu.reminder.api

import com.bugu.identity.api.CurrentUser
import com.bugu.reminder.app.ReminderService
import com.bugu.shared.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 提醒接口(PRD FR-02):手动创建、双方列表、修改与取消。
 * 一句话解析见 `/api/v1/reminder/parse`;静音见 `/api/v1/reminder/mute`。
 */
@RestController
@RequestMapping("/api/v1/reminder")
class ReminderController(private val reminderService: ReminderService) {

    @PostMapping
    fun create(currentUser: CurrentUser, @RequestBody request: CreateReminderRequest): ApiResponse<ReminderView> =
        ApiResponse.ok(reminderService.create(currentUser.id, request))

    @PostMapping("/parse")
    fun parse(currentUser: CurrentUser, @RequestBody request: ParseRequest): ApiResponse<ParseDraft> =
        ApiResponse.ok(reminderService.parse(request))

    @GetMapping("/mine")
    fun mine(currentUser: CurrentUser): ApiResponse<List<ReminderView>> =
        ApiResponse.ok(reminderService.listMine(currentUser.id))

    /** 长辈模式当前提醒(FR-06):返回最早一条待处理提醒,无则 null。 */
    @GetMapping("/current")
    fun current(currentUser: CurrentUser): ApiResponse<ReminderView?> =
        ApiResponse.ok(reminderService.currentReminder(currentUser.id))

    @GetMapping("/incoming")
    fun incoming(currentUser: CurrentUser): ApiResponse<List<ReminderView>> =
        ApiResponse.ok(reminderService.listIncoming(currentUser.id))

    @PatchMapping("/{id}")
    fun update(
        currentUser: CurrentUser,
        @PathVariable id: Long,
        @RequestBody request: UpdateReminderRequest,
    ): ApiResponse<ReminderView> = ApiResponse.ok(reminderService.update(currentUser.id, id, request))

    @DeleteMapping("/{id}")
    fun cancel(currentUser: CurrentUser, @PathVariable id: Long): ApiResponse<ReminderView> =
        ApiResponse.ok(reminderService.cancel(currentUser.id, id))

    @PostMapping("/ack")
    fun ack(currentUser: CurrentUser, @RequestBody request: AckRequest): ApiResponse<ReminderView> =
        ApiResponse.ok(reminderService.ack(currentUser.id, request.fireId, request.action, request.reasonCode, request.snoozeMinutes))

    /** 端侧触发上报(E7-S1):设备到点响铃后登记触发事实,幂等可重放。 */
    @PostMapping("/fire/{fireId}/report")
    fun reportFire(currentUser: CurrentUser, @PathVariable fireId: Long, @RequestBody request: FireReportRequest): ApiResponse<FireReportView> =
        ApiResponse.ok(reminderService.reportFire(currentUser.id, fireId, request))

    /** 误触撤销(E6-S2):仅发起人可调用,回滚一次 DONE 确认。 */
    @PostMapping("/ack/{ackId}/revoke")
    fun revokeAck(currentUser: CurrentUser, @PathVariable ackId: Long): ApiResponse<ReminderView> =
        ApiResponse.ok(reminderService.revokeAck(currentUser.id, ackId))

    @GetMapping("/{id}/mutual-status")
    fun mutualStatus(currentUser: CurrentUser, @PathVariable id: Long): ApiResponse<MutualStatusView> =
        ApiResponse.ok(reminderService.mutualStatus(currentUser.id, id))

    @GetMapping("/{id}/wakeup-copy")
    fun wakeupCopy(
        currentUser: CurrentUser,
        @PathVariable id: Long,
        @RequestParam(required = false) date: String?,
    ): ApiResponse<WakeupCopyView> = ApiResponse.ok(reminderService.wakeupCopy(currentUser.id, id, date))

    @PutMapping("/mute")
    fun mute(currentUser: CurrentUser, @RequestBody request: MuteRequest): ApiResponse<Map<String, Boolean>> {
        reminderService.mute(currentUser.id, request.creatorId, request.kind)
        return ApiResponse.ok(mapOf("muted" to true))
    }

    @DeleteMapping("/mute")
    fun unmute(currentUser: CurrentUser, @RequestBody request: MuteRequest): ApiResponse<Map<String, Boolean>> {
        reminderService.unmute(currentUser.id, request.creatorId, request.kind)
        return ApiResponse.ok(mapOf("muted" to false))
    }

    @GetMapping("/mute")
    fun listMuted(currentUser: CurrentUser): ApiResponse<List<MuteSettingView>> =
        ApiResponse.ok(
            reminderService.listMuted(currentUser.id).map {
                MuteSettingView(creatorId = it.creatorId, kind = it.kind, createdAt = it.createdAt.toString())
            },
        )
}
