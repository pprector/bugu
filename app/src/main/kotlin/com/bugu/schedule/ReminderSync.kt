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
     * SNOOZE 的端侧新 fireAt。服务端 ack 的 ReminderView 仍返回原 reminder.fireAt,
     * 不含新 snooze fire 的时刻,故端侧以 ack 本地时间 +15min 近似;服务端实际时间可能差几秒。
     */
    data class SnoozeHint(val reminderId: Long, val fireAtIso: String)

    fun snoozeHint(reminderId: Long, minutes: Long = 15): SnoozeHint =
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
     * - SNOOZE 通过 [snoozeHint] 覆盖服务端未返回的新 fireAt;
     * - REPEAT 直接使用服务端推进后的 reminder.fireAt;
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
        val planned = ReminderPlanner.plan(schedulable, System.currentTimeMillis())
        AlarmScheduler(context, stores.scheduledAlarms).reschedule(planned)
        return incoming
    }
}
