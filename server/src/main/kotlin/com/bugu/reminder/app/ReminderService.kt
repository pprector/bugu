package com.bugu.reminder.app

import com.bugu.ai.app.WakeupCopyService
import com.bugu.ai.domain.CopyContext
import com.bugu.ai.domain.DayPart
import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.ai.infra.AiGenLogRow
import com.bugu.bond.domain.BondStatus
import com.bugu.bond.infra.BondRepository
import com.bugu.bond.infra.BondRow
import com.bugu.reminder.api.CreateReminderRequest
import com.bugu.reminder.api.FireReportRequest
import com.bugu.reminder.api.FireReportView
import com.bugu.reminder.api.MutualStatusView
import com.bugu.reminder.api.ParseDraft
import com.bugu.reminder.api.ParseRequest
import com.bugu.reminder.api.ReminderView
import com.bugu.reminder.api.UpdateReminderRequest
import com.bugu.reminder.api.WakeupCopyView
import com.bugu.reminder.domain.AckAction
import com.bugu.reminder.domain.DeclineReason
import com.bugu.reminder.domain.Reminder
import com.bugu.reminder.domain.ReminderKind
import com.bugu.reminder.domain.ReminderStatus
import com.bugu.reminder.domain.RepeatRulePolicy
import com.bugu.reminder.domain.ToneLevel
import com.bugu.reminder.infra.AckRepository
import com.bugu.reminder.infra.AckRow
import com.bugu.reminder.infra.MuteSettingRepository
import com.bugu.reminder.infra.MuteSettingRow
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderFireRow
import com.bugu.reminder.infra.ReminderMapper
import com.bugu.reminder.infra.ReminderRepository
import com.bugu.reminder.infra.ReminderRow
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 提醒创建与查询应用服务(PRD FR-02)。
 * 创建:字段校验(用户可见错误转 BizException,聚合 init 兜底不变式)→ ACTIVE bond 前置
 * → 落库 PENDING,同事务预生成首条 reminder_fire(idempotency_key=reminderId:fireAt)。
 * 查询:双方列表按 ACTIVE bond 过滤,解绑即时隔离(复用 E1 语义)。
 */
@Service
class ReminderService(
    private val reminderRepository: ReminderRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val bondRepository: BondRepository,
    private val llmReminderParser: LlmReminderParser,
    private val aiGenLogRepository: AiGenLogRepository,
    private val muteSettingRepository: MuteSettingRepository,
    private val ackRepository: AckRepository,
    private val wakeupCopyService: WakeupCopyService,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
    private val eventPublisher: ApplicationEventPublisher,
    private val repeatScheduler: RepeatScheduler,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 一句话解析(PRD FR-02):LLM 优先、正则兜底,degraded 如实落库;解析不抛错(创建不被阻塞)。 */
    fun parse(request: ParseRequest): ParseDraft {
        val start = System.nanoTime()
        val parsed = llmReminderParser.parse(request.text.take(MAX_PARSE_TEXT), LocalDateTime.now(clock))
        val draft = parsed.draft
        val latencyMs = ((System.nanoTime() - start) / 1_000_000).toInt()
        aiGenLogRepository.save(
            AiGenLogRow(
                scene = "PARSE",
                degraded = parsed.degraded,
                latencyMs = latencyMs,
                createdAt = LocalDateTime.now(clock),
            ),
        )
        log.info("parse scene=PARSE degraded={} needsManual={} latencyMs={}", parsed.degraded, draft.needsManual, latencyMs)
        return ParseDraft(
            title = draft.title,
            fireAt = draft.fireAt?.toString(),
            kind = draft.kind.name,
            repeatRule = draft.repeatRule,
            note = draft.note,
            needsManual = draft.needsManual,
            degraded = parsed.degraded,
        )
    }

    fun create(creatorId: Long, request: CreateReminderRequest): ReminderView {
        val now = LocalDateTime.now(clock)
        val validated = validate(request, now)
        val bond = findActiveBond(creatorId, request.receiverId)
            ?: throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "与对方无有效绑定关系")
        if (muteSettingRepository.existsByReceiverIdAndCreatorIdAndKind(request.receiverId, creatorId, validated.kind.name)) {
            throw BizException(ErrorCode.REMINDER_MUTED, HttpStatus.BAD_REQUEST)
        }
        val silent = isOverDailyLimit(creatorId, request.receiverId, now)

        return transactionTemplate.execute {
            val saved = reminderRepository.save(
                ReminderMapper.toRow(
                    Reminder(
                        bondId = bond.id!!,
                        creatorId = creatorId,
                        receiverId = request.receiverId,
                        title = validated.title,
                        kind = validated.kind,
                        tone = validated.tone,
                        fireAt = validated.fireAt,
                        repeatRule = validated.repeatRule,
                        voiceEnabled = request.voiceEnabled,
                        note = request.note,
                        silent = silent,
                        createdAt = now,
                    ),
                ),
            )
            // 互叫闹钟双方各一条 fire(各自确认只关自己);其余类型单 fire 目标=接收人
            val savedId = requireNotNull(saved.id) { "保存后 reminder id 不应为空" }
            val fires = saveFires(savedId, validated.kind, saved.fireAt, creatorId, request.receiverId)
            // 创建推送经事件解耦到 push 模块(AFTER_COMMIT):静默超上限也发,是否到达由 push 侧如实记录
            eventPublisher.publishEvent(
                ReminderCreatedEvent(
                    reminderId = savedId,
                    fireId = requireNotNull(fires.first().id) { "保存后 fire id 不应为空" },
                    creatorId = creatorId,
                    receiverId = request.receiverId,
                    title = validated.title,
                    tone = validated.tone.name,
                ),
            )
            if (silent) {
                log.info("reminder 当日超上限静默落库 id={} creator={} receiver={}", saved.id, creatorId, request.receiverId)
            }
            log.info("reminder 创建 id={} creator={} receiver={} kind={}", saved.id, creatorId, request.receiverId, saved.kind)
            toView(saved)
        }
    }

    /**
     * 修改提醒(S4):仅发起人、仅 PENDING、仅标题与 fireAt(为 null 保持不变,两字段全空 400)。
     * 改 fireAt 时同事务删除 PENDING 状态 fire 并按新时刻重生成首条(幂等键同步更新)。
     */
    fun update(userId: Long, reminderId: Long, request: UpdateReminderRequest): ReminderView {
        val row = reminderRepository.findByIdOrNull(reminderId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "提醒不存在")
        if (row.creatorId != userId) {
            throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "仅发起人可修改")
        }
        if (row.status != ReminderStatus.PENDING.name) {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "仅待触发提醒可修改")
        }
        if (request.title == null && request.fireAt == null) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "无可修改字段")
        }
        val newTitle = request.title?.trim()
        if (newTitle != null && (newTitle.isEmpty() || newTitle.length > Reminder.MAX_TITLE)) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "标题必填且不超过 20 字")
        }
        val newFireAt = request.fireAt?.let {
            runCatching { LocalDateTime.parse(it) }.getOrElse {
                throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "触发时间格式非法,须为 ISO 本地时刻")
            }
        }
        val now = LocalDateTime.now(clock)
        if (newFireAt != null && !newFireAt.isAfter(now)) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "触发时间须为未来时刻")
        }

        return transactionTemplate.execute {
            val domain = ReminderMapper.toDomain(row)
            domain.revise(newTitle, newFireAt, now)
            val saved = reminderRepository.save(ReminderMapper.toRow(domain))
            if (newFireAt != null) {
                val pendingFires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
                    .filter { it.state == "PENDING" }
                reminderFireRepository.deleteAll(pendingFires)
                saveFires(reminderId, domain.kind, saved.fireAt, domain.creatorId, domain.receiverId)
            }
            log.info("reminder 修改 id={} by={} titleChanged={} fireAtChanged={}", reminderId, userId, newTitle != null, newFireAt != null)
            toView(saved)
        }
    }

    /** 取消提醒(S4):仅发起人;PENDING/FIRED → CANCELED,其余状态 409。 */
    fun cancel(userId: Long, reminderId: Long): ReminderView {
        val row = reminderRepository.findByIdOrNull(reminderId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "提醒不存在")
        if (row.creatorId != userId) {
            throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "仅发起人可取消")
        }
        if (!ReminderStatus.valueOf(row.status).canTransitTo(ReminderStatus.CANCELED)) {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "当前状态不可取消")
        }
        return transactionTemplate.execute {
            val domain = ReminderMapper.toDomain(row)
            domain.cancel()
            val saved = reminderRepository.save(ReminderMapper.toRow(domain))
            log.info("reminder 取消 id={} by={}", reminderId, userId)
            toView(saved)
        }
    }

    /**
     * 接收人确认(S1):DONE→ACKED、SNOOZE→SNOOZED+新建 fire、DECLINE→DECLINED。
     * 互叫闹钟(E5):仅 DONE 出口(确认即「已起」),所有权按 fire.targetUserId 校验,
     * 单方 DONE 只关自己 fire(reminder 保持 FIRED),双方当次 fire 都响应后 reminder 才 ACKED。
     * 幂等(uk_ack_fire);PENDING 状态先隐式 markFired。
     */
    fun ack(receiverId: Long, fireId: Long, actionRaw: String, reasonCodeRaw: String?, snoozeMinutes: Int?): ReminderView {
        val action = runCatching { AckAction.valueOf(actionRaw) }.getOrElse {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "未知确认动作")
        }
        val reason = if (action == AckAction.DECLINE) {
            runCatching { DeclineReason.valueOf(reasonCodeRaw ?: "") }.getOrElse {
                throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "无法完成须带合法原因(BUSY/CANNOT/FORGOT/OTHER)")
            }
        } else {
            null
        }
        if (action == AckAction.SNOOZE) {
            val m = snoozeMinutes ?: throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "稍后须带间隔分钟数")
            if (m < 1 || m > MAX_SNOOZE_MINUTES) {
                throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "稍后间隔须在 1~$MAX_SNOOZE_MINUTES 分钟")
            }
        }

        val fire = reminderFireRepository.findByIdOrNull(fireId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "触发记录不存在")

        val reminderRow = reminderRepository.findByIdOrNull(fire.reminderId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "提醒不存在")
        if (fire.targetUserId != receiverId) {
            throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "仅触发目标本人可确认")
        }
        val isMutual = reminderRow.kind == ReminderKind.MUTUAL_ALARM.name
        if (isMutual && action != AckAction.DONE) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "互叫闹钟仅支持确认已起")
        }

        // 幂等:同一 fire 已 ack 直接返回当前提醒视图(所有权已校验)
        if (ackRepository.existsByFireId(fireId)) {
            return toView(reminderRow)
        }

        val reminderStatus = ReminderStatus.valueOf(reminderRow.status)
        if (reminderStatus != ReminderStatus.PENDING && reminderStatus != ReminderStatus.FIRED && reminderStatus != ReminderStatus.SNOOZED) {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "当前状态不可确认")
        }

        val now = LocalDateTime.now(clock)
        return transactionTemplate.execute {
            val domain = ReminderMapper.toDomain(reminderRow)
            if (domain.status == ReminderStatus.PENDING || domain.status == ReminderStatus.SNOOZED) {
                domain.markFired()
            }

            // 原 fire 标记已响应
            reminderFireRepository.save(fire.copy(state = "RESPONDED"))

            // 落 ack 行
            ackRepository.save(
                AckRow(
                    fireId = fireId,
                    action = action.name,
                    reasonCode = reason?.name,
                    snoozeMinutes = if (action == AckAction.SNOOZE) snoozeMinutes else null,
                    ackedAt = now,
                ),
            )

            when (action) {
                AckAction.DONE -> {
                    // 互叫:双方当次 fire 都响应才算完成;其余类型 DONE 即完成
                    val allUp = !isMutual || reminderFireRepository.findByReminderIdOrderByFireAt(fire.reminderId)
                        .filter { it.fireAt == fire.fireAt }
                        .all { it.state == "RESPONDED" }
                    if (allUp) {
                        domain.ackDone()
                    }
                }
                AckAction.SNOOZE -> {
                    domain.ackSnooze()
                    // SNOOZE:新建一条 fire,idempotencyKey 避开 uk_fire_idem;
                    // 截断到毫秒,与 DB DATETIME(3) 存储精度一致,保证 key 与 fire_at 回读一致
                    val snoozeFireAt = now.plusMinutes(snoozeMinutes!!.toLong())
                        .let { it.withNano((it.nano / 1_000_000) * 1_000_000) }
                    reminderFireRepository.save(
                        ReminderFireRow(
                            reminderId = fire.reminderId,
                            targetUserId = fire.targetUserId,
                            fireAt = snoozeFireAt,
                            idempotencyKey = "${fire.reminderId}:$snoozeFireAt",
                        ),
                    )
                }
                AckAction.DECLINE -> domain.ackDecline()
            }
            val saved = reminderRepository.save(ReminderMapper.toRow(domain))

            log.info("reminder ack id={} fire={} action={} by={}", fire.reminderId, fireId, action, receiverId)
            val view = toView(saved)
            // REPEAT:本次已完成即排下一次,系列不因单次确认而终止(E10 决策)
            if (action == AckAction.DONE) repeatScheduler.scheduleNext(saved, now)
            view
        }
    }

    /**
     * 端侧触发事实上报(E7-S1):设备到点响铃后调用,登记 reported_at 与来源。
     * 幂等:已上报(reportedAt 非空)直接返回当前记录不覆盖,离线补报重放安全;
     * RESPONDED 仍可补报(先确认后补报的离线队列回放),CLOSED 已收口 409(端侧据此展示「已错过」)。
     * fire.state 不变——reported_at 是调度扫描器区分「已触发/关机错过」的唯一信号,升级链照常推进。
     */
    fun reportFire(userId: Long, fireId: Long, request: FireReportRequest): FireReportView {
        val fire = reminderFireRepository.findByIdOrNull(fireId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "触发记录不存在")
        if (fire.idempotencyKey != request.idempotencyKey) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "幂等键与触发记录不匹配")
        }
        if (request.source !in FIRE_SOURCES) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "来源非法,须为 LOCAL_ALARM 或 PUSH")
        }
        val reminderRow = reminderRepository.findByIdOrNull(fire.reminderId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "提醒不存在")
        if (reminderRow.creatorId != userId && fire.targetUserId != userId) {
            throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "仅发起人或触发目标可上报")
        }
        if (fire.state == "CLOSED") {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "该触发已收口,无法补报")
        }
        if (fire.reportedAt != null) {
            return toFireView(fire)
        }
        val saved = reminderFireRepository.save(
            fire.copy(reportedAt = LocalDateTime.now(clock), source = request.source),
        )
        log.info("fire 上报 fire={} reminder={} by={} source={}", fireId, fire.reminderId, userId, request.source)
        return toFireView(saved)
    }

    /**
     * 误触撤销(E6-S2):仅 reminder.creator 可调用,回滚一次 DONE 确认。
     * fire RESPONDED→FIRED、删除 ack 行、reminder ACKED→FIRED(互叫单方 DONE 时 reminder 本就 FIRED,跳过)。
     * 幂等:ack 不存在 404,fire 非 RESPONDED 409。
     */
    fun revokeAck(userId: Long, ackId: Long): ReminderView {
        val ack = ackRepository.findByIdOrNull(ackId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "确认记录不存在")
        val fire = reminderFireRepository.findByIdOrNull(ack.fireId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "触发记录不存在")
        val reminderRow = reminderRepository.findByIdOrNull(fire.reminderId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "提醒不存在")
        if (reminderRow.creatorId != userId) {
            throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "仅发起人可撤销误触")
        }
        if (fire.state != "RESPONDED") {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "该确认已撤销或不可撤销")
        }

        return transactionTemplate.execute {
            // fire 回 FIRED
            reminderFireRepository.save(fire.copy(state = "FIRED"))
            // 删除 ack 行
            ackRepository.delete(ack)

            val domain = ReminderMapper.toDomain(reminderRow)
            // 互叫单方 DONE 时 reminder 本就 FIRED,无需回滚;双方都 DONE 时 ACKED→FIRED
            if (domain.status == ReminderStatus.ACKED) {
                domain.revokeDone()
            }
            val saved = reminderRepository.save(ReminderMapper.toRow(domain))

            eventPublisher.publishEvent(
                AckRevokedEvent(
                    reminderId = requireNotNull(saved.id) { "保存后 reminder id 不应为空" },
                    fireId = requireNotNull(fire.id) { "fire id 不应为空" },
                    creatorId = saved.creatorId,
                    receiverId = saved.receiverId,
                    title = saved.title,
                ),
            )
            log.info("reminder ack 撤销 id={} ack={} by={}", fire.reminderId, ackId, userId)
            toView(saved)
        }
    }

    /** 我发起的提醒:仅返回 ACTIVE bond 下的(解绑即时隔离)。 */
    fun listMine(userId: Long): List<ReminderView> {
        val activeBondIds = activeBondIds(userId)
        return reminderRepository.findByCreatorIdOrderByFireAt(userId)
            .filter { it.bondId in activeBondIds }
            .map { toView(it, userId) }
    }

    /** 接收人静音某发起人的某类提醒:仅可对 ACTIVE bond 对方操作;重复静音幂等。 */
    fun mute(receiverId: Long, creatorId: Long, kindRaw: String) {
        val kind = parseKind(kindRaw)
        findActiveBond(receiverId, creatorId)
            ?: throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "与对方无有效绑定关系")
        if (!muteSettingRepository.existsByReceiverIdAndCreatorIdAndKind(receiverId, creatorId, kind.name)) {
            muteSettingRepository.save(MuteSettingRow(receiverId = receiverId, creatorId = creatorId, kind = kind.name))
            log.info("静音设置 receiver={} creator={} kind={}", receiverId, creatorId, kind)
        }
    }

    /** 恢复某发起人的某类提醒(删除静音行,不存在则幂等)。 */
    fun unmute(receiverId: Long, creatorId: Long, kindRaw: String) {
        val kind = parseKind(kindRaw)
        transactionTemplate.execute {
            muteSettingRepository.deleteByReceiverIdAndCreatorIdAndKind(receiverId, creatorId, kind.name)
        }
    }

    /** 我的静音列表。 */
    fun listMuted(receiverId: Long): List<MuteSettingRow> = muteSettingRepository.findByReceiverId(receiverId)

    /**
     * 互叫状态互见(E5-S2):仅 MUTUAL_ALARM、仅双方可见;
     * 已起=该方当次最新 fire 已 RESPONDED,只暴露 UP/NOT_UP 二元状态。
     */
    fun mutualStatus(userId: Long, reminderId: Long): MutualStatusView {
        val row = reminderRepository.findByIdOrNull(reminderId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "提醒不存在")
        if (row.kind != ReminderKind.MUTUAL_ALARM.name) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "仅互叫闹钟支持状态互见")
        }
        if (row.creatorId != userId && row.receiverId != userId) {
            throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "仅互叫双方可查看")
        }
        val peerId = if (userId == row.creatorId) row.receiverId else row.creatorId
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        fun isUp(targetId: Long): Boolean =
            fires.filter { it.targetUserId == targetId }.maxByOrNull { it.fireAt }?.state == "RESPONDED"
        return MutualStatusView(
            self = if (isUp(userId)) "UP" else "NOT_UP",
            peer = if (isUp(peerId)) "UP" else "NOT_UP",
        )
    }

    /**
     * 叫醒语拉取(E5-S3):仅 MUTUAL_ALARM、仅双方;date 缺省当日,支持次日预取。
     * 生成与按日缓存委托 ai 模块 WakeupCopyService(降级结果同样落缓存,当日内容稳定)。
     */
    fun wakeupCopy(userId: Long, reminderId: Long, dateRaw: String?): WakeupCopyView {
        val row = reminderRepository.findByIdOrNull(reminderId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "提醒不存在")
        if (row.kind != ReminderKind.MUTUAL_ALARM.name) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "仅互叫闹钟支持叫醒语")
        }
        if (row.creatorId != userId && row.receiverId != userId) {
            throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "仅互叫双方可查看")
        }
        val now = LocalDateTime.now(clock)
        val date = dateRaw?.let {
            runCatching { LocalDate.parse(it) }.getOrElse {
                throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "日期格式非法,须为 yyyy-MM-dd")
            }
        } ?: now.toLocalDate()
        val context = CopyContext(
            title = row.title,
            scene = WakeupCopyService.SCENE,
            tone = row.tone,
            attempt = 1,
            dayPart = DayPart.ofHour(now.hour),
        )
        val copy = wakeupCopyService.get(reminderId, date, context)
        return WakeupCopyView(date = copy.copyDate.toString(), content = copy.content, degraded = copy.degraded)
    }

    /**
     * 长辈模式「当前提醒」(FR-06):返回目标用户最早一条待处理(PENDING/FIRED)fire 对应提醒,
     * 无则 null。长辈端仅展示这一条 + 「完成了」按钮;fireId 随视图返回供 ack 定位。
     */
    fun currentReminder(userId: Long): ReminderView? {
        val fire = reminderFireRepository.findFirstByTargetUserIdAndStateInOrderByFireAt(
            userId, listOf("PENDING", "FIRED"),
        ) ?: return null
        val row = reminderRepository.findByIdOrNull(fire.reminderId) ?: return null
        return toView(row, userId)
    }

    private fun parseKind(raw: String): ReminderKind =
        runCatching { ReminderKind.valueOf(raw) }.getOrElse {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "未知提醒类型")
        }

    /** 我接收的提醒:仅返回 ACTIVE bond 下的(解绑即时隔离)。 */
    fun listIncoming(userId: Long): List<ReminderView> {
        val activeBondIds = activeBondIds(userId)
        return reminderRepository.findByReceiverIdOrderByFireAt(userId)
            .filter { it.bondId in activeBondIds }
            .map { toView(it, userId) }
    }

    /** 用户可见校验;聚合 init 做不变式兜底。 */
    private fun validate(request: CreateReminderRequest, now: LocalDateTime): ValidatedCreate {
        val title = request.title.trim()
        if (title.isEmpty() || title.length > Reminder.MAX_TITLE) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "标题必填且不超过 20 字")
        }
        if (request.note != null && request.note.length > Reminder.MAX_NOTE) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "备注不超过 50 字")
        }
        val kind = runCatching { ReminderKind.valueOf(request.kind) }.getOrElse {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "未知提醒类型")
        }
        val tone = runCatching { ToneLevel.valueOf(request.tone) }.getOrElse {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "未知语气档位")
        }
        val fireAt = runCatching { LocalDateTime.parse(request.fireAt) }.getOrElse {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "触发时间格式非法,须为 ISO 本地时刻")
        }
        if (!fireAt.isAfter(now)) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "触发时间须为未来时刻")
        }
        val repeatRule = if (kind == ReminderKind.REPEAT) {
            val rule = request.repeatRule
                ?: throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "重复类型须带重复规则")
            if (!RepeatRulePolicy.isValid(rule)) {
                throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "重复规则非法,支持 DAILY 或 WEEKLY:MON,WED")
            }
            rule
        } else {
            if (request.repeatRule != null) {
                throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "非重复类型不带重复规则")
            }
            null
        }
        if (request.voiceEnabled && kind != ReminderKind.MUTUAL_ALARM) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "语音叫醒仅互叫闹钟可开")
        }
        return ValidatedCreate(title, kind, tone, fireAt, repeatRule)
    }

    /** creator 与 receiver 之间的 ACTIVE bond(任一方向)。 */
    private fun findActiveBond(creatorId: Long, receiverId: Long): BondRow? =
        bondRepository.findFirstByRequesterIdAndReceiverIdAndStatus(creatorId, receiverId, BondStatus.ACTIVE.name)
            ?: bondRepository.findFirstByRequesterIdAndReceiverIdAndStatus(receiverId, creatorId, BondStatus.ACTIVE.name)

    /**
     * 落当次触发的 fire:互叫闹钟双方各一条(接收人在前,创建事件取其 fire),
     * 幂等键 MUTUAL 用 `reminderId:fireAt:userId`,其余类型维持 `reminderId:fireAt`。
     */
    private fun saveFires(
        reminderId: Long,
        kind: ReminderKind,
        fireAt: LocalDateTime,
        creatorId: Long,
        receiverId: Long,
    ): List<ReminderFireRow> {
        val mutual = kind == ReminderKind.MUTUAL_ALARM
        val targets = if (mutual) listOf(receiverId, creatorId) else listOf(receiverId)
        return targets.map { target ->
            reminderFireRepository.save(
                ReminderFireRow(
                    reminderId = reminderId,
                    targetUserId = target,
                    fireAt = fireAt,
                    idempotencyKey = if (mutual) "$reminderId:$fireAt:$target" else "$reminderId:$fireAt",
                ),
            )
        }
    }

    /** 当日同一对接收/发起人已创建数达到上限(超限静默落库,不打断创建)。 */
    private fun isOverDailyLimit(creatorId: Long, receiverId: Long, now: LocalDateTime): Boolean {
        val dayStart = now.toLocalDate().atStartOfDay()
        return reminderRepository.countByCreatorIdAndReceiverIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            creatorId, receiverId, dayStart, dayStart.plusDays(1),
        ) >= MAX_DAILY_PER_PAIR
    }

    private fun activeBondIds(userId: Long): Set<Long> =
        bondRepository.findByRequesterIdOrReceiverId(userId, userId)
            .filter { it.status == BondStatus.ACTIVE.name }
            .mapTo(HashSet()) { it.id!! }

    private fun toView(row: ReminderRow) = ReminderView(
        id = row.id!!,
        bondId = row.bondId,
        creatorId = row.creatorId,
        receiverId = row.receiverId,
        title = row.title,
        kind = row.kind,
        tone = row.tone,
        fireAt = row.fireAt.toString(),
        repeatRule = row.repeatRule,
        voiceEnabled = row.voiceEnabled,
        note = row.note,
        status = row.status,
        silent = row.silent,
        createdAt = row.createdAt.toString(),
    )

    /**
     * 列表/当前视图:附当前查看者可操作的待响应 fireId(最早一条 PENDING/FIRED 且 target=viewer);
     * 无则 null(如发起人看 ONCE 提醒——fire 目标是接收人,发起人无从 ack/report)。
     * 已 ACKED 的提醒附最早一次 DONE 确认 ackId,供发起人撤销误触。
     * 逐条查 fire/ack 属个人规模 N+1,量级 ≤10 条/人,不另设批查(E8 契约补充时如实记录)。
     */
    private fun toView(row: ReminderRow, viewerId: Long): ReminderView {
        val reminderId = requireNotNull(row.id) { "reminder id 不应为空" }
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        val actionableFireId = fires
            .firstOrNull { it.targetUserId == viewerId && (it.state == "PENDING" || it.state == "FIRED") }
            ?.id
        val doneAckId = if (row.status == ReminderStatus.ACKED.name) {
            fires.firstNotNullOfOrNull { ackRepository.findByFireId(it.id!!) }?.id
        } else {
            null
        }
        return toView(row).copy(fireId = actionableFireId, ackId = doneAckId)
    }

    private fun toFireView(fire: ReminderFireRow) = FireReportView(
        fireId = requireNotNull(fire.id) { "fire id 不应为空" },
        reminderId = fire.reminderId,
        targetUserId = fire.targetUserId,
        fireAt = fire.fireAt.toString(),
        reportedAt = fire.reportedAt?.toString(),
        source = fire.source,
        state = fire.state,
    )

    private data class ValidatedCreate(
        val title: String,
        val kind: ReminderKind,
        val tone: ToneLevel,
        val fireAt: LocalDateTime,
        val repeatRule: String?,
    )

    private companion object {
        /** 一句话解析入参上限(字),超出截断。 */
        const val MAX_PARSE_TEXT = 200

        /** 每日上限(S4):同一对接收/发起人当日创建达到该条数后静默落库。 */
        const val MAX_DAILY_PER_PAIR = 10

        /** SNOOZE 间隔上限(分钟),避免无限延后。 */
        const val MAX_SNOOZE_MINUTES = 1440

        /** 端侧触发上报来源(E7-S1):本地闹钟响铃 / 推送到达后响铃。 */
        val FIRE_SOURCES = setOf("LOCAL_ALARM", "PUSH")
    }
}
