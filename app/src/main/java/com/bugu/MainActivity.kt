package com.bugu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.bugu.deeplink.InviteDeepLink
import com.bugu.kuikly.BuguKuiklyHost
import com.bugu.schedule.ReportWorker

/**
 * 唯一入口 Activity(段一:UI 由 Kuikly 壳承载,Compose 页面文件本轮保留)。
 * 冷启动先补发离线上报队列(幂等),再解析深链,最后交给 [BuguKuiklyHost] 分流登录/看板。
 * 响铃仍由原生 RingActivity 独立承载,不经此入口。
 */
class MainActivity : ComponentActivity() {

    private lateinit var host: BuguKuiklyHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 通知权限是后台全屏兜底的前提;首次启动在系统层引导一次,拒绝后仍可在权限页重新开启。
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        ReportWorker.flush(this)
        // 冷启动深链:解析后落 pending_invite_code,由 Kuikly 登录页/亲友页回填
        InviteDeepLink.handle(this, intent)

        val container = FrameLayout(this)
        setContentView(container)
        host = BuguKuiklyHost(this, container)
        host.onCreate(
            initialPage = if (BuildConfig.DEBUG) intent.getStringExtra("bugu_page") else null,
            initialParams = intent.getStringExtra("bugu_params") ?: "{}",
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 热启动深链:App 已在前台/已登录,直接进亲友页
        InviteDeepLink.handle(this, intent)
        host.onNewIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        deviceId: Int,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        host.onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onResume() {
        super.onResume()
        host.onResume()
    }

    override fun onPause() {
        super.onPause()
        host.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        host.onDestroy()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 4_101
    }
}
