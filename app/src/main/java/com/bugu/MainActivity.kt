package com.bugu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bugu.schedule.ReportWorker
import com.bugu.ui.AppRoot
import com.bugu.ui.theme.BuguTheme

/**
 * 唯一入口 Activity:冷启动先补发离线上报队列(幂等),再进 UI。
 * 推送令牌上报由 AppRoot 登录态就绪后触发(PushReporter)。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReportWorker.flush(this)
        setContent {
            BuguTheme {
                AppRoot()
            }
        }
    }
}
