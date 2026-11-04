package com.bugu.push

import android.content.Context
import android.content.Intent
import android.util.Log
import cn.jpush.android.api.NotificationMessage
import cn.jpush.android.service.JPushMessageReceiver
import com.bugu.MainActivity
import com.bugu.schedule.AlarmScheduler
import com.bugu.ui.RingActivity

/**
 * 极光推送接收器(E8-S6):必须由宿主提供,否则 SDK 告警。
 * 通知点击回调(E8-S7):FIRE_AT 推送带 fireId/reminderId/title/fireAt/idempotencyKey 等 extras,
 * 直接拉起 RingActivity;RingActivity 的 singleTask + fireId 去重会忽略本地闹钟已打开的同一条。
 * 其余通知(创建/升级链/旧服务端无新字段)回退 MainActivity,避免点击无响应。
 */
class BuguPushMessageReceiver : JPushMessageReceiver() {

    override fun onConnected(context: Context?, debugCode: Boolean) {
        // 长连接建立;registrationID 由 PushReporter 轮询上报,这里不重复处理
    }

    override fun onNotifyMessageOpened(context: Context?, message: NotificationMessage?) {
        val ctx = context ?: return
        val target = PushRingTarget.parse(message?.notificationExtras, message?.deeplink)
        val intent = if (target != null) {
            Intent(ctx, RingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmScheduler.EXTRA_FIRE_ID, target.fireId)
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, target.reminderId)
                putExtra(AlarmScheduler.EXTRA_TITLE, target.title)
                putExtra(AlarmScheduler.EXTRA_FIRE_AT, target.fireAt)
                putExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY, target.idempotencyKey)
                putExtra(AlarmScheduler.EXTRA_VOICE, target.voiceEnabled)
                putExtra(AlarmScheduler.EXTRA_KIND, target.kind)
                putExtra(AlarmScheduler.EXTRA_SELF, target.selfReminder)
            }
        } else {
            Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { ctx.startActivity(intent) }
            .onFailure { Log.w(TAG, "push click startActivity failed fire=${target?.fireId}", it) }
        Log.i(TAG, "push click fire=${target?.fireId} ring=${target != null}")
    }

    private companion object {
        const val TAG = "BuguPushClick"
    }
}
