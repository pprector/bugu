package com.bugu

import android.app.Application
import com.bugu.schedule.AlarmNotifications

/**
 * 进程入口:极光 SDK 由 [PushReporter] 在登录态就绪后懒初始化,
 * 此处挂载闹钟通知渠道(渠道属性必须在响铃前创建)。
 */
class BuguApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.ensureChannel(this)
    }
}
