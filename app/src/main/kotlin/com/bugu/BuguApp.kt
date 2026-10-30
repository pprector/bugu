package com.bugu

import android.app.Application

/**
 * 进程入口:极光 SDK 由 [PushReporter] 在登录态就绪后懒初始化,
 * 此处仅保留应用级挂载点(WorkManager 默认初始化即可)。
 */
class BuguApp : Application()
