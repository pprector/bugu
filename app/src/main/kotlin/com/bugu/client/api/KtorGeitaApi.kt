package com.bugu.client.api

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * GeitaApi 的 Ktor client 实现(common,可跑安卓/CIO 等任意引擎)。
 * 引擎与 ContentNegotiation(ApiJson)由调用方装配;本类只负责:
 * JWT 注入、信封解包(code != OK 抛 [GeitaApiException])、data 缺省语义(current 无提醒返回 null)。
 * 非 2xx 同样按信封解码——服务端错误也是 JSON 信封(GlobalExceptionHandler)。
 */
class KtorGeitaApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String? = { null },
    /** 任意接口返回 UNAUTHORIZED 时触发(全局登录态失效,由调用方清 token 踢回登录页)。 */
    private val onUnauthorized: suspend () -> Unit = {},
) : GeitaApi {

    // ---- 信封解包 ----

    private suspend fun <T> decode(response: HttpResponse, serializer: KSerializer<T>): ApiEnvelope<T> {
        val text = response.bodyAsText()
        return ApiJson.json.decodeFromString(ApiEnvelope.serializer(serializer), text)
    }

    /** 统一失败出口:401 先触发全局回调再抛出,其余原样抛 [GeitaApiException]。 */
    private suspend fun fail(code: String, message: String?): Nothing {
        if (code == "UNAUTHORIZED") runCatching { onUnauthorized() }
        throw GeitaApiException(code, message)
    }

    /** 要求 data 在场的端点;缺失视为协议错误。 */
    private suspend fun <T : Any> callForData(
        serializer: KSerializer<T>,
        method: HttpMethod,
        path: List<String>,
        query: List<Pair<String, String?>> = emptyList(),
        body: Any? = null,
    ): T {
        val response = execute(method, path, query, body)
        val envelope = decode(response, serializer)
        if (!envelope.isOk) fail(envelope.code, envelope.message)
        return envelope.data ?: fail("EMPTY_DATA", "服务端返回缺少 data")
    }

    /** data 可空的端点(current 无待处理、Unit 响应)。 */
    private suspend fun <T : Any> callForNullableData(
        serializer: KSerializer<T>,
        method: HttpMethod,
        path: List<String>,
        query: List<Pair<String, String?>> = emptyList(),
        body: Any? = null,
    ): T? {
        val response = execute(method, path, query, body)
        val envelope = decode(response, serializer.nullable)
        if (!envelope.isOk) fail(envelope.code, envelope.message)
        return envelope.data
    }

    /** 仅校验 code 的动作端点(data 形状各异或不重要:send-code、mute、unmute)。 */
    private suspend fun callAction(method: HttpMethod, path: List<String>, body: Any? = null) {
        val response = execute(method, path, emptyList(), body)
        val obj = ApiJson.json.parseToJsonElement(response.bodyAsText()).jsonObject
        val code = (obj["code"] as? JsonPrimitive)?.content ?: fail("MALFORMED", "响应缺少 code")
        if (code != "OK") {
            fail(code, (obj["message"] as? JsonPrimitive)?.content)
        }
    }

    private suspend fun execute(
        method: HttpMethod,
        path: List<String>,
        query: List<Pair<String, String?>>,
        body: Any?,
    ): HttpResponse {
        val url = URLBuilder(baseUrl).appendPathSegments(path).apply {
            query.forEach { (name, value) -> value?.let { parameters.append(name, it) } }
        }.build()
        val token = tokenProvider()
        return when (method) {
            HttpMethod.Get -> httpClient.get(url) { authorize(token) }
            HttpMethod.Delete -> httpClient.delete(url) { authorize(token) }
            HttpMethod.Patch -> httpClient.patch(url) { jsonBody(token, body) }
            HttpMethod.Put -> httpClient.put(url) { jsonBody(token, body) }
            else -> httpClient.post(url) { jsonBody(token, body) }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.jsonBody(token: String?, body: Any?) {
        authorize(token)
        // 显式 contentType 才会触发 ContentNegotiation 对请求体做 kotlinx-serialization
        contentType(ContentType.Application.Json)
        body?.let { setBody(it) }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize(token: String?) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    // ---- 身份(identity) ----

    override suspend fun sendCode(phone: String) {
        callAction(HttpMethod.Post, listOf("api", "v1", "auth", "send-code"), body = SendCodeRequest(phone))
    }

    override suspend fun login(phone: String, code: String): LoginResponse =
        callForData(LoginResponse.serializer(), HttpMethod.Post, listOf("api", "v1", "auth", "login"), body = LoginRequest(phone, code))

    override suspend fun me(): UserDto =
        callForData(UserDto.serializer(), HttpMethod.Get, listOf("api", "v1", "auth", "me"))

    override suspend fun updateNickname(nickname: String): UserDto =
        callForData(UserDto.serializer(), HttpMethod.Patch, listOf("api", "v1", "auth", "me"), body = UpdateProfileRequest(nickname))

    override suspend fun submitFeedback(request: CreateFeedbackRequest): FeedbackDto =
        callForData(FeedbackDto.serializer(), HttpMethod.Post, listOf("api", "v1", "feedback"), body = request)

    override suspend fun myFeedback(): List<FeedbackDto> =
        callForData(ListSerializer(FeedbackDto.serializer()), HttpMethod.Get, listOf("api", "v1", "feedback"))

    override suspend fun avatarUploadTicket(contentType: String): AvatarUploadTicket =
        callForData(
            AvatarUploadTicket.serializer(), HttpMethod.Post, listOf("api", "v1", "auth", "me", "avatar", "token"),
            body = AvatarTokenRequest(contentType),
        )

    override suspend fun uploadAvatarObject(ticket: AvatarUploadTicket, contentType: String, bytes: ByteArray) {
        // COS PostObject:签名表单字段在前,file 必须最后;成功语义取 success_action_status=200
        val response = httpClient.post(ticket.uploadUrl) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        ticket.formFields.forEach { (name, value) -> append(name, value) }
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, contentType)
                                append(HttpHeaders.ContentDisposition, "filename=\"avatar\"")
                            },
                        )
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw GeitaApiException("COS_UPLOAD_FAILED", "头像上传到对象存储失败(${response.status.value})")
        }
    }

    override suspend fun confirmAvatarUpload(objectKey: String): UserDto =
        callForData(
            UserDto.serializer(), HttpMethod.Post, listOf("api", "v1", "auth", "me", "avatar", "confirm"),
            body = AvatarConfirmRequest(objectKey),
        )

    override suspend fun downloadStatic(path: String): ByteArray {
        // 头像已迁对象存储:绝对 URL 公有读直接取;保留相对路径分支兼容历史数据
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return httpClient.get(path).bodyAsBytes()
        }
        val segments = path.trim('/').split('/')
        val url = URLBuilder(baseUrl).appendPathSegments(segments).build()
        return httpClient.get(url) { authorize(tokenProvider()) }.bodyAsBytes()
    }

    override suspend fun bindPhone(phone: String, code: String): UserDto =
        callForData(UserDto.serializer(), HttpMethod.Post, listOf("api", "v1", "auth", "bind-phone"), body = BindPhoneRequest(phone, code))

    override suspend fun registerElder(deviceUuid: String, deviceModel: String, inviteCode: String): ElderRegisterResponse =
        callForData(
            ElderRegisterResponse.serializer(), HttpMethod.Post, listOf("api", "v1", "auth", "elder-register"),
            body = ElderRegisterRequest(deviceUuid, deviceModel, inviteCode),
        )

    override suspend fun reportDeviceToken(pushToken: String, platform: String): DeviceTokenView =
        callForData(
            DeviceTokenView.serializer(), HttpMethod.Post, listOf("api", "v1", "devices", "token"),
            body = DeviceTokenReport(pushToken, platform),
        )

    // ---- 绑定(bond) ----

    override suspend fun initiateBond(): InitiateResponse =
        callForData(InitiateResponse.serializer(), HttpMethod.Post, listOf("api", "v1", "bond", "initiate"))

    override suspend fun confirmBond(code: String): BondDto =
        callForData(BondDto.serializer(), HttpMethod.Post, listOf("api", "v1", "bond", "confirm"), body = ConfirmRequest(code))

    override suspend fun invitation(code: String): InvitationDto =
        callForData(InvitationDto.serializer(), HttpMethod.Get, listOf("api", "v1", "bond", "by-code", code))

    override suspend fun myBonds(): List<BondDto> =
        callForData(ListSerializer(BondDto.serializer()), HttpMethod.Get, listOf("api", "v1", "bond"))

    override suspend fun unbind(bondId: Long): BondDto =
        callForData(BondDto.serializer(), HttpMethod.Post, listOf("api", "v1", "bond", bondId.toString(), "unbind"))

    override suspend fun updateBondRemark(bondId: Long, remark: String?): BondDto =
        callForData(BondDto.serializer(), HttpMethod.Put, listOf("api", "v1", "bond", bondId.toString(), "remark"), body = BondRemarkRequest(remark).toJson())

    override suspend fun setElderMode(peerId: Long, enabled: Boolean): UserDto =
        callForData(UserDto.serializer(), HttpMethod.Post, listOf("api", "v1", "bond", "elder-mode"), body = ElderModeRequest(peerId, enabled))

    override suspend fun updateElderProfile(elderUserId: Long, nickname: String?, avatarUrl: String?, relationTag: String?): ElderProfileDto =
        callForData(
            ElderProfileDto.serializer(), HttpMethod.Post, listOf("api", "v1", "bond", "elder-profile"),
            body = ElderProfileRequest(elderUserId, nickname, avatarUrl, relationTag),
        )

    // ---- 提醒(reminder) ----

    override suspend fun createReminder(request: CreateReminderRequest): ReminderView =
        callForData(ReminderView.serializer(), HttpMethod.Post, listOf("api", "v1", "reminder"), body = request)

    override suspend fun createRemindersBatch(request: BatchCreateReminderRequest): List<ReminderView> =
        callForData(
            ListSerializer(ReminderView.serializer()),
            HttpMethod.Post,
            listOf("api", "v1", "reminder", "batch"),
            body = request,
        )

    override suspend fun parse(text: String): ParseDraft =
        callForData(ParseDraft.serializer(), HttpMethod.Post, listOf("api", "v1", "reminder", "parse"), body = ParseRequest(text))

    override suspend fun myReminders(): List<ReminderView> =
        callForData(ListSerializer(ReminderView.serializer()), HttpMethod.Get, listOf("api", "v1", "reminder", "mine"))

    override suspend fun incomingReminders(): List<ReminderView> =
        callForData(ListSerializer(ReminderView.serializer()), HttpMethod.Get, listOf("api", "v1", "reminder", "incoming"))

    override suspend fun currentReminder(): ReminderView? =
        callForNullableData(ReminderView.serializer(), HttpMethod.Get, listOf("api", "v1", "reminder", "current"))

    override suspend fun updateReminder(id: Long, request: UpdateReminderRequest): ReminderView =
        callForData(ReminderView.serializer(), HttpMethod.Patch, listOf("api", "v1", "reminder", id.toString()), body = request)

    override suspend fun cancelReminder(id: Long): ReminderView =
        callForData(ReminderView.serializer(), HttpMethod.Delete, listOf("api", "v1", "reminder", id.toString()))

    override suspend fun ack(request: AckRequest): ReminderView =
        callForData(ReminderView.serializer(), HttpMethod.Post, listOf("api", "v1", "reminder", "ack"), body = request)

    override suspend fun revokeAck(ackId: Long): ReminderView =
        callForData(ReminderView.serializer(), HttpMethod.Post, listOf("api", "v1", "reminder", "ack", ackId.toString(), "revoke"))

    override suspend fun mutualStatus(reminderId: Long): MutualStatusView =
        callForData(MutualStatusView.serializer(), HttpMethod.Get, listOf("api", "v1", "reminder", reminderId.toString(), "mutual-status"))

    override suspend fun wakeupCopy(reminderId: Long, date: String?): WakeupCopyView =
        callForData(
            WakeupCopyView.serializer(), HttpMethod.Get, listOf("api", "v1", "reminder", reminderId.toString(), "wakeup-copy"),
            query = listOf("date" to date),
        )

    override suspend fun reportFire(fireId: Long, idempotencyKey: String, source: String): FireReportView =
        callForData(
            FireReportView.serializer(), HttpMethod.Post, listOf("api", "v1", "reminder", "fire", fireId.toString(), "report"),
            body = FireReportRequest(idempotencyKey, source),
        )

    // ---- 静音(mute) ----

    override suspend fun mute(creatorId: Long, kind: String) {
        callAction(HttpMethod.Put, listOf("api", "v1", "reminder", "mute"), body = MuteRequest(creatorId, kind))
    }

    override suspend fun unmute(creatorId: Long, kind: String) {
        callAction(HttpMethod.Delete, listOf("api", "v1", "reminder", "mute"), body = MuteRequest(creatorId, kind))
    }

    override suspend fun mutedList(): List<MuteSettingView> =
        callForData(ListSerializer(MuteSettingView.serializer()), HttpMethod.Get, listOf("api", "v1", "reminder", "mute"))
}
