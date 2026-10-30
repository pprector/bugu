package com.bugu.push

import android.content.Context
import cn.jpush.android.service.JPushMessageReceiver

/**
 * 极光推送必需的自定义接收器(E8-S6 QA 发现):AAR 不自带,缺失时 SDK 报
 * "missing required receiver: please custom receiver extends JPushMessageReceiver",
 * 且自定义消息/通知点击回调无宿主接收。
 * P0 仅透传通知(SDK 默认展示),故不覆写回调;后续要做「点通知直达提醒」时在此扩展。
 */
class BuguPushMessageReceiver : JPushMessageReceiver() {

    override fun onConnected(context: Context?, debugCode: Boolean) {
        // 长连接建立;registrationID 由 PushReporter 轮询上报,这里不重复处理
    }
}
