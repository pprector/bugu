package com.bugu.schedule

import android.content.Context
import com.bugu.PushReporter
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.ReminderView
import java.time.LocalDateTime

/**
 * 提醒 → 端侧闹钟 的中央同步:拉「我接收的 + 自惦记」,
 * 持久化为开机重排源数据(lastIncoming),再全量重排精确闹钟。
 * 后端 /incoming 按契约不含自惦记(creatorId == receiverId),但自惦记同样要响,
 * 故从 /mine 补入;其余「我发起、对方接收」的提醒不在本机注册闹钟。
 * 幂等可重入:登录后、创建后、列表刷新后、开机(走 BootReceiver 持久化数据)均安全。
 */
object ReminderSync {

    /**
     * SNOOZE 的新 fireAt:优先取服务端 ack 返回的 nextFireAt。
     * 仅当服务端尚未部署新契约(无 nextFireAt)时,才走 [legacySnoozeHint] 的本地 +15 分钟回退。
     */
    data class SnoozeHint(val reminderId: Long, val fireAtIso: String)

    fun snoozeHint(reminderId: Long, fireAtIso: String): SnoozeHint =
        SnoozeHint(reminderId = reminderId, fireAtIso = fireAtIso)

    /**
     * 兼容旧服务端回退:旧版 ack 不返回 nextFireAt,端侧只能按本地确认时间 +15 分钟近似。
     * 服务端 V17 部署后应走 [snoozeHint];保留此路径避免旧环境 snooze 排期断裂。
     */
    @Deprecated("服务端 ack 已返回 nextFireAt;仅在未部署新契约时回退使用")
    fun legacySnoozeHint(reminderId: Long, minutes: Long = 15): SnoozeHint =
        SnoozeHint(
            reminderId = reminderId,
            fireAtIso = LocalDateTime.now().plusMinutes(minutes).withNano(0).toString(),
        )

    fun schedulePeriodic(context: Context) = ReminderSyncWorker.schedule(context)

    fun cancelPeriodic(context: Context) = ReminderSyncWorker.cancel(context)

    fun removeBond(context: Context, bondId: Long) {
        val stores = PrefsStores(context)
        val remaining = stores.lastIncoming.load().filterNot { it.bondId == bondId }
        stores.lastIncoming.save(remaining)
        AlarmScheduler(context, stores.scheduledAlarms).reschedule(
            ReminderPlanner.plan(remaining, System.currentTimeMillis()),
        )
    }

    /** 取消成功后立即移除对应 PendingIntent,并把剩余提醒重新排程。 */
    fun removeReminder(context: Context, reminderId: Long, fireId: Long?) {
        val stores = PrefsStores(context)
        fireId?.let { AlarmScheduler(context, stores.scheduledAlarms).cancel(it) }
        stores.snoozeOverrides.save(stores.snoozeOverrides.load() - reminderId)
        val remaining = stores.lastIncoming.load().filterNot { it.id == reminderId }
        stores.lastIncoming.save(remaining)
        AlarmScheduler(context, stores.scheduledAlarms).reschedule(
            ReminderPlanner.plan(remaining, System.currentTimeMillis()),
        )
    }

    /**
     * 登出/401 的统一清理入口:
     * 取消全部闹钟 → 清本机调度/队列缓存 → 停止补报任务 → 移除闹钟通知 → 停止推送接收。
     */
    fun clearLocalState(context: Context) {
        val stores = PrefsStores(context)
        AlarmScheduler(context, stores.scheduledAlarms).cancelAll()
        stores.clearAll()
        ReportWorker.cancelAll(context)
        cancelPeriodic(context)
        AlarmNotifications.cancelAll(context)
        PushReporter.stop(context)
    }

    /**
     * 唯一的端侧重排入口:
     * - SNOOZE 通过 [SnoozeHint] 覆盖服务端未返回的新 fireAt(服务端新契约下该值来自 ack.nextFireAt);
     * - REPEAT 直接使用服务端推进后的 reminder.fireAt(与 ack.nextFireAt 同源);
     * - ACKED/CANCELED/MISSED 不会进入 Planner(WAITING 过滤),周期同步不会复活。
     */
    suspend fun sync(
        context: Context,
        api: GeitaApi,
        snoozeHint: SnoozeHint? = null,
    ): List<ReminderView> {
        val stores = PrefsStores(context)
        // 先持久化 snooze hint:即使本次网络同步失败,后续周期同步也能按新 fireAt 补排。
        val overrides = stores.snoozeOverrides.load().toMutableMap()
        snoozeHint?.let { overrides[it.reminderId] = it.fireAtIso }
        val incoming = api.incomingReminders()
        // /mine 失败不应拖垮接收提醒的闹钟注册,自惦记仅作补充
        val selfReminders = runCatching { api.myReminders() }.getOrDefault(emptyList())
            .filter { it.creatorId == it.receiverId }
        val base = (incoming + selfReminders).distinctBy { it.id }

        val schedulable = base.map { reminder ->
            if (reminder.status == "SNOOZED") {
                val snoozeAt = overrides[reminder.id]
                if (snoozeAt != null) reminder.copy(fireAt = snoozeAt) else reminder
            } else {
                overrides.remove(reminder.id)
                reminder
            }
        }
        // fireId 是端侧注册的唯一身份;只保留仍处于 SNOOZED 且有可操作 fire 的覆盖。
        overrides.keys.retainAll(
            schedulable
                .filter { it.status == "SNOOZED" && it.fireId != null }
                .map { it.id }
                .toSet(),
        )
        stores.snoozeOverrides.save(overrides)
        stores.lastIncoming.save(schedulable)
        // 响铃页要显示「来自 X 的提醒 / X 的留言」,但提醒 DTO 不带昵称;
        // 这里顺手把亲友昵称(备注优先)缓存到本机,离线响铃也能取到(R2/R3)。
        runCatching { api.myBonds() }.onSuccess { bonds ->
            val names = org.json.JSONObject()
            for (bond in bonds) {
                val peerId = bond.counterpartId ?: continue
                if (bond.status != "ACTIVE") continue
                val name = bond.remark?.takeIf { it.isNotBlank() }
                    ?: bond.counterpartNickname?.takeIf { it.isNotBlank() }
                    ?: bond.counterpartRelationTag?.takeIf { it.isNotBlank() }
                    ?: "亲友"
                names.put(peerId.toString(), name)
            }
            context.getSharedPreferences("bugu_main", Context.MODE_PRIVATE)
                .edit().putString("bond_names_json", names.toString()).apply()
        }
        val planned = ReminderPlanner.plan(schedulable, System.currentTimeMillis())
        AlarmScheduler(context, stores.scheduledAlarms).reschedule(planned)
        return incoming
    }
}
