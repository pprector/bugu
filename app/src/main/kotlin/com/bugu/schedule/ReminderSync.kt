package com.bugu.schedule

import android.content.Context
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.ReminderView

/**
 * 提醒 → 端侧闹钟 的中央同步:拉「我接收的」列表,
 * 持久化为开机重排源数据(lastIncoming),再全量重排精确闹钟。
 * 幂等可重入:登录后、列表刷新后、开机(走 BootReceiver 持久化数据)均安全。
 */
object ReminderSync {

    suspend fun sync(context: Context, api: GeitaApi): List<ReminderView> {
        val incoming = api.incomingReminders()
        val stores = PrefsStores(context)
        stores.lastIncoming.save(incoming)
        val planned = ReminderPlanner.plan(incoming, System.currentTimeMillis())
        AlarmScheduler(context, stores.scheduledAlarms).reschedule(planned)
        return incoming
    }
}
