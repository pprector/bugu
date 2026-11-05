package com.bugu.admin.app

import com.bugu.admin.api.AdminAppUserView
import com.bugu.admin.api.AdminAuditLogPage
import com.bugu.admin.api.AdminAuditLogView
import com.bugu.admin.api.AdminBondPage
import com.bugu.admin.api.AdminBondView
import com.bugu.admin.api.AdminEscalationAction
import com.bugu.admin.api.AdminEscalationView
import com.bugu.admin.api.AdminPushStatusView
import com.bugu.admin.api.AdminReminderPage
import com.bugu.admin.api.AdminReminderView
import com.bugu.admin.api.AdminUserPage
import com.bugu.admin.infra.AdminAuditLogRepository
import com.bugu.bond.infra.BondMapper
import com.bugu.bond.infra.BondRepository
import com.bugu.identity.app.UserService
import com.bugu.push.PushChannel
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * 运营查询与处理(E9-S2):个人运营规模,findAll + 内存筛选后按 page/size 切片返回
 * (千级数据够用,避免引入额外分页查询;数据量继续增长时再下推到 SQL)。
 * 写操作经聚合方法,落审计由控制器负责。
 */
@Service
class AdminOpsService(
    private val userService: UserService,
    private val bondRepository: BondRepository,
    private val bondLifecycle: com.bugu.bond.app.BondLifecycle,
    private val reminderRepository: ReminderRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val auditRepository: AdminAuditLogRepository,
    private val pushChannels: List<PushChannel>,
    private val transactionTemplate: TransactionTemplate,
) {

    // ---- 用户 ----

    fun users(query: String?, page: Int, size: Int): AdminUserPage {
        val all = userService.listAllRaw()
            .filter { matches(query, it.nickname, it.phone, it.id) }
            .sortedByDescending { it.id }
        val (rows, total) = slice(all, page, size)
        return AdminUserPage(
            items = rows.map {
                AdminAppUserView(
                    id = requireNotNull(it.id) { "user id 不应为空" },
                    nickname = it.nickname,
                    phone = it.phone,
                    accountType = it.accountType,
                    elderMode = it.elderMode,
                )
            },
            total = total,
            page = page,
            size = size,
        )
    }

    /** admin 直设长辈模式:越过 bond 校验(管理员语义),落审计由控制器负责。 */
    fun setElderMode(userId: Long, enabled: Boolean): AdminAppUserView {
        val updated = try {
            userService.setElderMode(userId, enabled)
        } catch (e: IllegalArgumentException) {
            throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "用户不存在")
        }
        return AdminAppUserView(
            id = userId,
            nickname = updated.nickname,
            phone = updated.phone,
            accountType = updated.accountType,
            elderMode = updated.elderMode,
        )
    }

    // ---- 绑定 ----

    fun bonds(query: String?, page: Int, size: Int): AdminBondPage {
        val users = userService.listAllRaw().associateBy { it.id }
        val all = bondRepository.findAll()
            .filter { matches(query, it.bindCode, it.id, it.requesterId, it.receiverId) }
            .sortedByDescending { it.id }
        val (rows, total) = slice(all, page, size)
        return AdminBondPage(
            items = rows.map { bond ->
                val receiver = bond.receiverId?.let { users[it] }
                AdminBondView(
                    id = requireNotNull(bond.id) { "bond id 不应为空" },
                    status = bond.status,
                    requesterId = bond.requesterId,
                    requesterName = users[bond.requesterId]?.nickname ?: "?",
                    receiverId = bond.receiverId,
                    receiverName = receiver?.nickname,
                    createdAt = bond.createdAt.toString(),
                )
            },
            total = total,
            page = page,
            size = size,
        )
    }

    /** 管理员解绑:聚合 adminDissolve,即时隔离。 */
    fun unbind(bondId: Long) {
        transactionTemplate.execute {
            val row = bondRepository.lockById(bondId)
                ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "绑定不存在")
            val domain = BondMapper.toDomain(row)
            domain.adminDissolve(LocalDateTime.now())
            bondRepository.save(BondMapper.toRow(domain))
            bondLifecycle.deactivate(bondId)
        }
    }

    // ---- 提醒与升级链 ----

    fun reminders(status: String?, kind: String?, query: String?, page: Int, size: Int): AdminReminderPage {
        val users = userService.listAllRaw().associateBy { it.id }
        val all = reminderRepository.findAll()
            .filter { status == null || it.status == status }
            .filter { kind == null || it.kind == kind }
            .filter { matches(query, it.title, it.id) }
            .sortedByDescending { it.id }
        val (rows, total) = slice(all, page, size)
        return AdminReminderPage(
            items = rows.map { row ->
                AdminReminderView(
                    id = requireNotNull(row.id) { "reminder id 不应为空" },
                    title = row.title,
                    kind = row.kind,
                    status = row.status,
                    fireAt = row.fireAt.toString(),
                    creatorId = row.creatorId,
                    creatorName = users[row.creatorId]?.nickname ?: "?",
                    receiverId = row.receiverId,
                    receiverName = users[row.receiverId]?.nickname ?: "?",
                    createdAt = row.createdAt.toString(),
                )
            },
            total = total,
            page = page,
            size = size,
        )
    }

    fun escalations(reminderId: Long): List<AdminEscalationView> {
        reminderRepository.findByIdOrNull(reminderId)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "提醒不存在")
        return reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).map { fire ->
            val fireId = requireNotNull(fire.id) { "fire id 不应为空" }
            AdminEscalationView(
                fireId = fireId,
                fireAt = fire.fireAt.toString(),
                state = fire.state,
                actions = escalationLogRepository.findByFireIdOrderByCreatedAt(fireId)
                    .map { AdminEscalationAction(stage = it.stage, action = it.action, createdAt = it.createdAt.toString()) },
            )
        }
    }

    // ---- 推送状态(只读) ----

    fun pushStatus(): AdminPushStatusView {
        val names = pushChannels.map { it.javaClass.simpleName }.distinct()
        return AdminPushStatusView(
            channel = names.joinToString(","),
            description = "D4 已定案极光 JPush(E8-S5);bugu.push.provider=jiguang 时经 JiguangPushChannel 发真实推送;推送动作记录见提醒监控的升级链日志",
        )
    }

    // ---- 审计(E9) ----

    fun auditLogs(adminUserId: Long?, page: Int, size: Int): AdminAuditLogPage {
        val rows = if (adminUserId == null) {
            auditRepository.findAll().sortedByDescending { it.id }
        } else {
            auditRepository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId)
        }
        val (sliced, total) = slice(rows, page, size)
        return AdminAuditLogPage(
            items = sliced.map {
                AdminAuditLogView(
                    id = requireNotNull(it.id) { "audit id 不应为空" },
                    adminUserId = it.adminUserId,
                    action = it.action,
                    targetType = it.targetType,
                    targetId = it.targetId,
                    detail = it.detail,
                    createdAt = it.createdAt.toString(),
                )
            },
            total = total,
            page = page,
            size = size,
        )
    }

    /** 内存分页切片:page 从 1 起,size 1-100,越界页返回空列表(与反馈管理同口径)。 */
    private fun <T> slice(all: List<T>, page: Int, size: Int): Pair<List<T>, Long> {
        require(page >= 1 && size in 1..100) { "分页参数非法，page须大于0，size须为1至100" }
        val total = all.size.toLong()
        val from = (page - 1) * size
        val items = if (from >= all.size) emptyList() else all.subList(from, minOf(from + size, all.size))
        return items to total
    }

    private fun matches(query: String?, vararg fields: Any?): Boolean {
        if (query.isNullOrBlank()) return true
        val q = query.trim()
        return fields.any { it?.toString()?.contains(q) == true }
    }
}
