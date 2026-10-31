package com.bugu.client.api

/**
 * 服务端 API 契约(E1~E7 已交付端点)。
 * 实现方(S3 Ktor client)负责:JWT 注入、ApiEnvelope 解包(code != OK 抛 [GeitaApiException])、
 * data 为空的 200 语义(current 无待处理返回 null)。
 * 路径前缀均为 /api/v1;长辈模式账号仅可调 current/ack/fire report/me/bind-phone(服务端过滤白名单)。
 */
interface GeitaApi {

    // ---- 身份(identity) ----

    /** POST /auth/send-code:发送短信验证码(60s 限流,服务端 SMS_RATE_LIMITED)。 */
    suspend fun sendCode(phone: String)

    /** POST /auth/login:手机号 + 验证码登录,返回 JWT。 */
    suspend fun login(phone: String, code: String): LoginResponse

    /** GET /auth/me:当前用户(含 elderMode,客户端据此路由长辈极简界面)。 */
    suspend fun me(): UserDto

    /** PATCH /auth/me:更新当前用户昵称。 */
    suspend fun updateNickname(nickname: String): UserDto

    /** POST /feedback 与 GET /feedback:提交反馈并查看本人最近记录。 */
    suspend fun submitFeedback(request: CreateFeedbackRequest): FeedbackDto
    suspend fun myFeedback(): List<FeedbackDto>

    /** POST /auth/me/avatar/token:申请头像 COS 签名直传凭证(类型白名单 JPG/PNG/WebP)。 */
    suspend fun avatarUploadTicket(contentType: String): AvatarUploadTicket

    /** 直传 COS:formFields 原样作表单字段、file 字段最后;凭证按 key 精确绑定,改传其他对象会被拒收。 */
    suspend fun uploadAvatarObject(ticket: AvatarUploadTicket, contentType: String, bytes: ByteArray)

    /** POST /auth/me/avatar/confirm:直传成功后确认落库,返回更新后的资料(含 avatarUrl)。 */
    suspend fun confirmAvatarUpload(objectKey: String): UserDto

    /** GET 头像等静态资源:绝对 URL(对象存储公有读)直接读取;相对路径拼 baseUrl 后带 JWT 取字节。 */
    suspend fun downloadStatic(path: String): ByteArray

    /** POST /auth/bind-phone:长辈设备标识账号补绑手机号(升级 NORMAL)。 */
    suspend fun bindPhone(phone: String, code: String): UserDto

    /** POST /auth/elder-register:长辈免注册接入,设备标识 + 邀请码换 token。 */
    suspend fun registerElder(deviceUuid: String, deviceModel: String, inviteCode: String): ElderRegisterResponse

    /** POST /devices/token:推送令牌上报(E8-S5),极光 SDK 初始化拿到 registrationID 后调用;长辈模式可报。 */
    suspend fun reportDeviceToken(pushToken: String, platform: String = "ANDROID"): DeviceTokenView

    // ---- 绑定(bond) ----

    /** POST /bond/initiate:生成绑定码与深链。 */
    suspend fun initiateBond(): InitiateResponse

    /** POST /bond/confirm:输入对方绑定码确认,双方 ACTIVE。 */
    suspend fun confirmBond(code: String): BondDto

    /** GET /bond/by-code/{code}:扫码进入时查邀请方。 */
    suspend fun invitation(code: String): InvitationDto

    /** GET /bond:我的绑定列表。 */
    suspend fun myBonds(): List<BondDto>

    /** POST /bond/{id}/unbind:解绑,即时隔离双方提醒。 */
    suspend fun unbind(bondId: Long): BondDto

    /** PUT /bond/{id}/remark:仅当前用户可见的亲友备注。 */
    suspend fun updateBondRemark(bondId: Long, remark: String?): BondDto

    /** POST /bond/elder-mode:子女端标记/取消对方长辈模式,即时生效。 */
    suspend fun setElderMode(peerId: Long, enabled: Boolean): UserDto

    /** POST /bond/elder-profile:长辈资料维护。 */
    suspend fun updateElderProfile(elderUserId: Long, nickname: String?, avatarUrl: String?, relationTag: String?): ElderProfileDto

    // ---- 提醒(reminder) ----

    /** POST /reminder:创建提醒,预生成首条 fire。 */
    suspend fun createReminder(request: CreateReminderRequest): ReminderView

    /** POST /reminder/batch:为多个接收人分别创建相同提醒。 */
    suspend fun createRemindersBatch(request: BatchCreateReminderRequest): List<ReminderView>

    /** POST /reminder/parse:一句话解析草稿(降级桩或 LLM),needsManual 回退手动表单。 */
    suspend fun parse(text: String): ParseDraft

    /** GET /reminder/mine:我发起的(仅 ACTIVE bond)。 */
    suspend fun myReminders(): List<ReminderView>

    /** GET /reminder/incoming:我接收的(仅 ACTIVE bond)。 */
    suspend fun incomingReminders(): List<ReminderView>

    /** GET /reminder/current:长辈模式当前提醒,无则 null。 */
    suspend fun currentReminder(): ReminderView?

    /** PATCH /reminder/{id}:修改标题/触发时刻(仅发起人、仅 PENDING)。 */
    suspend fun updateReminder(id: Long, request: UpdateReminderRequest): ReminderView

    /** DELETE /reminder/{id}:取消(仅发起人)。 */
    suspend fun cancelReminder(id: Long): ReminderView

    /** POST /reminder/ack:三出口确认(DONE/SNOOZE/DECLINE)。 */
    suspend fun ack(request: AckRequest): ReminderView

    /** POST /reminder/ack/{ackId}/revoke:子女端撤销长辈误触完成(E6-S2)。 */
    suspend fun revokeAck(ackId: Long): ReminderView

    /** GET /reminder/{id}/mutual-status:互叫状态互见。 */
    suspend fun mutualStatus(reminderId: Long): MutualStatusView

    /** GET /reminder/{id}/wakeup-copy:叫醒语(date 缺省当日,支持次日预取)。 */
    suspend fun wakeupCopy(reminderId: Long, date: String?): WakeupCopyView

    /** POST /reminder/fire/{fireId}/report:端侧到点上报(E7),409 = 已错过。 */
    suspend fun reportFire(fireId: Long, idempotencyKey: String, source: String): FireReportView

    // ---- 静音(mute) ----

    /** PUT /reminder/mute:接收人对某发起人某类提醒静音。 */
    suspend fun mute(creatorId: Long, kind: String)

    /** DELETE /reminder/mute:恢复。 */
    suspend fun unmute(creatorId: Long, kind: String)

    /** GET /reminder/mute:我的静音列表。 */
    suspend fun mutedList(): List<MuteSettingView>
}
