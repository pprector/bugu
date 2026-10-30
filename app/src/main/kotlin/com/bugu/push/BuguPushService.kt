package com.bugu.push

import cn.jpush.android.service.JCommonService

/**
 * 极光推送必需的自定义服务(E8-S6 QA 发现):AAR 不自带 JCommonService,
 * 缺失时 SDK 持续告警且长连接/推送通道无法建立。
 */
class BuguPushService : JCommonService()
