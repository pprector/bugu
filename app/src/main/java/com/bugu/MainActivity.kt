package com.bugu

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
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
        // 通知权限是后台全屏兜底的前提;首次启动在系统层引导一次,拒绝后仍可在权限页重新开启。
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        ReportWorker.flush(this)
        setContent {
            BuguTheme {
                AppRoot()
            }
        }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 4_101
    }
}
