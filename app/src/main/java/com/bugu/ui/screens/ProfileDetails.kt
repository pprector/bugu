package com.bugu.ui.screens

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bugu.BuildConfig
import com.bugu.client.api.*
import com.bugu.diagnostics.LocalNotificationTest
import com.bugu.ui.theme.H
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ProfileDetailPage(page: String, api: GeitaApi, onBack: () -> Unit, onSnack: (String) -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(H.Canvas).statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("返回我的", color = H.CoralInk) }
        Text(page, style = MaterialTheme.typography.headlineSmall, color = H.Ink)
        when (page) {
            "权限与提醒诊断" -> PermissionDiagnostics(onSnack)
            "帮助与反馈" -> FeedbackContent(api)
            "关于布谷" -> AboutContent()
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun PermissionDiagnostics(onSnack: (String) -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(NotificationManager::class.java) }
    val alarms = remember { context.getSystemService(AlarmManager::class.java) }
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        LocalNotificationTest.prepare(context)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val notifications = remember(revision) { manager.areNotificationsEnabled() }
    val exact = remember(revision) { Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms() }
    val fullScreen = remember(revision) { Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent() }
    val channels = remember(revision) { manager.notificationChannels }
    fun open(intent: Intent) {
        try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
            catch (_: android.content.ActivityNotFoundException) { onSnack("系统未提供此设置入口，请在系统设置中搜索布谷") }
        }
    }
    DetailCard {
        Text("通知权限：${if (notifications) "已开启" else "已关闭"}", color = H.Ink)
        TextButton(onClick = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("通知设置") }
    }
    channels.forEach { channel ->
        DetailCard {
            Text("通知渠道：${channel.name}", color = H.Ink)
            Text(if (channel.importance == NotificationManager.IMPORTANCE_NONE) "已关闭" else "已开启（重要性 ${channel.importance}）", color = H.Ink2)
            if (channel.id == LocalNotificationTest.CHANNEL) Text("仅用于本机测试，不代表正式提醒渠道", color = H.Ink2)
            TextButton(onClick = { open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)) }) { Text("设置此通知渠道") }
        }
    }
    DetailCard {
        Text("精确闹钟：${if (exact) "可用" else "未授权"}", color = H.Ink)
        Text("用于本机准点调度，不保证服务器推送或绕过勿扰模式。", color = H.Ink2)
        if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = { open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())) }) { Text("精确闹钟设置") }
    }
    DetailCard {
        Text("全屏提醒：${if (Build.VERSION.SDK_INT < 34) "此版本无需单独授权" else if (fullScreen) "已允许" else "未允许"}", color = H.Ink)
        if (Build.VERSION.SDK_INT >= 34) TextButton(onClick = { open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())) }) { Text("全屏提醒设置") }
    }
    DetailCard {
        Text("本地测试提醒", style = MaterialTheme.typography.titleMedium, color = H.Ink)
        Text("点击后约5秒发送真实 Android 通知，可以退到桌面观察。这仅测试本机调度和通知，不测试服务器推送，也不创建、确认或修改正式提醒。声音与显示方式仍由系统设置决定。", color = H.Ink2)
        Button(onClick = {
            try { LocalNotificationTest.schedule(context); onSnack("已安排5秒后的本地测试，请观察通知栏") }
            catch (e: IllegalStateException) { onSnack(e.message ?: "无法安排测试") }
            catch (_: SecurityException) { onSnack("权限已变化，请检查系统设置后重试") }
        }) { Text("5秒后发送本地测试通知") }
    }
}

@Composable
private fun FeedbackContent(api: GeitaApi) {
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf("REMINDER") }
    var content by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<FeedbackDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var submitMessage by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        loading = true
        loadError = null
        try { items = api.myFeedback().take(50) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { loadError = "反馈记录加载失败，请重试" }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }
    DetailCard {
        Text("常见问题", style = MaterialTheme.typography.titleMedium, color = H.Ink)
        Text("收不到提醒？\n请查看「权限与提醒诊断」，确认通知、通知渠道和精确闹钟状态，并检查系统省电与后台运行限制。", color = H.Ink2)
        Text("为什么没有全屏或声音？\n全屏权限、通知渠道、音量与勿扰模式都可能影响呈现。本机测试成功不代表服务器推送一定成功。", color = H.Ink2)
        Text("如何修改资料？\n在「我的」点击修改昵称，点击头像可从系统相册选择图片。", color = H.Ink2)
    }
    DetailCard {
        Text("提交反馈", style = MaterialTheme.typography.titleMedium, color = H.Ink)
        feedbackCategories.forEach { (key, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = key == category, onClick = { if (!submitting) category = key }, enabled = !submitting,
                    modifier = Modifier.semantics { contentDescription = label })
                Text(label, color = H.Ink)
            }
        }
        OutlinedTextField(value = content, onValueChange = { content = it; submitMessage = null }, enabled = !submitting,
            label = { Text("反馈内容（10至2000字）") }, supportingText = { Text("${content.trim().length}/2000，请勿填写验证码等敏感信息") },
            modifier = Modifier.fillMaxWidth(), minLines = 4, isError = content.trim().length > 2000)
        submitMessage?.let { Text(it, color = H.Ink2) }
        Button(enabled = !loading && !submitting && content.trim().length in 10..2000, onClick = {
            scope.launch {
                submitting = true
                submitMessage = null
                try {
                    val created = api.submitFeedback(CreateFeedbackRequest.validated(category, content, BuildConfig.VERSION_NAME))
                    items = (listOf(created) + items.filterNot { it.id == created.id }).take(50)
                    content = ""
                    submitMessage = "反馈已提交，可以在下方查看处理状态和回复"
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { submitMessage = "提交未成功确认，内容已保留。请先刷新记录确认是否收到，再决定重试。" }
                finally { submitting = false }
            }
        }) { Text(if (submitting) "提交中…" else "提交反馈") }
    }
    Text("我的反馈 · 最近50条", style = MaterialTheme.typography.titleMedium, color = H.Ink)
    TextButton(enabled = !loading && !submitting, onClick = { scope.launch { load() } }) { Text(if (loading) "加载中…" else "刷新记录") }
    loadError?.let { Text(it, color = H.Rose) }
    if (!loading && loadError == null && items.isEmpty()) Text("还没有反馈记录", color = H.Ink2)
    items.forEach { item ->
        DetailCard {
            Text("${feedbackCategories[item.category] ?: item.category} · ${feedbackStatusLabel(item.status)}", color = H.Ink)
            Text(item.content, color = H.Ink2)
            Text("提交：${item.createdAt.replace('T', ' ')}", style = MaterialTheme.typography.bodySmall, color = H.Ink2)
            Text("回复：${item.reply?.takeIf { it.isNotBlank() } ?: "暂未回复"}", color = H.Ink)
            Text("更新：${item.updatedAt.replace('T', ' ')}", style = MaterialTheme.typography.bodySmall, color = H.Ink2)
        }
    }
}

@Composable
private fun AboutContent() {
    var agreement by rememberSaveable { mutableStateOf<String?>(null) }
    DetailCard {
        Text("布谷 · 让每一份惦记都有回音", style = MaterialTheme.typography.titleMedium, color = H.Ink)
        Text("版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", color = H.Ink2)
        TextButton(onClick = { agreement = "user" }) { Text("用户协议") }
        TextButton(onClick = { agreement = "privacy" }) { Text("隐私政策") }
    }
    agreement?.let { AgreementDialog(it) { agreement = null } }
}
