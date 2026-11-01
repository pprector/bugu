package com.bugu.schedule

import android.content.Context
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.ReminderView

/**
 * 提醒 → 端侧闹钟 的中央同步:拉「我接收的 + 自惦记」,
 * 持久化为开机重排源数据(lastIncoming),再全量重排精确闹钟。
 * 后端 /incoming 按契约不含自惦记(creatorId == receiverId),但自惦记同样要响,
 * 故从 /mine 补入;其余「我发起、对方接收」的提醒不在本机注册闹钟。
 * 幂等可重入:登录后、创建后、列表刷新后、开机(走 BootReceiver 持久化数据)均安全。
 */
object ReminderSync {

    fun removeBond(context: Context, bondId: Long) {
        val stores = PrefsStores(context)
        val remaining = stores.lastIncoming.load().filterNot { it.bondId == bondId }
        stores.lastIncoming.save(remaining)
        AlarmScheduler(context, stores.scheduledAlarms).reschedule(
            ReminderPlanner.plan(remaining, System.currentTimeMillis()),
        )
    }

    suspend fun sync(context: Context, api: GeitaApi): List<ReminderView> {
        val incoming = api.incomingReminders()
        // /mine 失败不应拖垮接收提醒的闹钟注册,自惦记仅作补充
        val selfReminders = runCatching { api.myReminders() }.getOrDefault(emptyList())
            .filter { it.creatorId == it.receiverId }
        val schedulable = (incoming + selfReminders).distinctBy { it.id }
        val stores = PrefsStores(context)
        stores.lastIncoming.save(schedulable)
        val planned = ReminderPlanner.plan(schedulable, System.currentTimeMillis())
        AlarmScheduler(context, stores.scheduledAlarms).reschedule(planned)
        return incoming
    }
}
