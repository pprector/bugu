package com.bugu.ui.screens

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bugu.BuildConfig
import com.bugu.client.api.CreateFeedbackRequest
import com.bugu.client.api.FeedbackDto
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.feedbackCategories
import com.bugu.client.api.feedbackStatusLabel
import com.bugu.diagnostics.LocalNotificationTest
import com.bugu.ui.common.BuguButton
import com.bugu.ui.common.BuguButtonStyle
import com.bugu.ui.common.BuguCard
import com.bugu.ui.common.BuguTextField
import com.bugu.ui.common.EmptyState
import com.bugu.ui.common.GuguBird
import com.bugu.ui.common.GuguMood
import com.bugu.ui.common.RiseIn
import com.bugu.ui.common.TagChip
import com.bugu.ui.common.TopBar
import com.bugu.ui.theme.H
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ProfileDetailPage(page: String, api: GeitaApi, onBack: () -> Unit, onSnack: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val mood = when (page) {
        "权限与送达诊断" -> GuguMood.SLEEP
        "帮助与反馈" -> GuguMood.VOICE
        else -> GuguMood.CHEER
    }
    val subtitle = when (page) {
        "权限与送达诊断" -> "咕咕帮你看看，呼唤能不能准时到达"
        "帮助与反馈" -> "有问题就告诉咕咕，它会记下来"
        else -> "让每一份惦记，都有回音"
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .navigationBarsPadding(),
    ) {
        TopBar(title = page, onBack = onBack, guguMood = mood)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
            when (page) {
                "权限与送达诊断" -> PermissionDiagnostics(onSnack)
                "帮助与反馈" -> FeedbackContent(api)
                "关于布谷" -> AboutContent()
            }
        }
    }
}

@Composable
private fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BuguCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    status: String,
    statusTint: androidx.compose.ui.graphics.Color,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
            }
            TagChip(text = status, tint = statusTint)
        }
        if (action != null && onAction != null) {
            BuguButton(
                text = action,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                style = BuguButtonStyle.Secondary,
                height = 44.dp,
            )
        }
    }
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
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                )
            } catch (_: android.content.ActivityNotFoundException) {
                onSnack("系统未提供此设置入口，请在系统设置中搜索布谷")
            }
        }
    }

    RiseIn {
        PermissionCard(
            title = "通知权限",
            description = if (notifications) "呼唤已经可以抵达这台设备" else "没有通知权限，咕咕的呼唤会被系统拦下",
            status = if (notifications) "已开启" else "已关闭",
            statusTint = if (notifications) H.Mint else H.Rose,
            action = "去通知设置",
            onAction = {
                open(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
    }

    channels.forEachIndexed { index, channel ->
        RiseIn(delayMs = (index + 1) * 40) {
            PermissionCard(
                title = "通知渠道 · ${channel.name}",
                description = if (channel.id == LocalNotificationTest.CHANNEL) {
                    "仅用于本机测试，不代表正式惦记的送达渠道"
                } else {
                    if (channel.importance == NotificationManager.IMPORTANCE_NONE) "这个渠道被关掉了" else "重要性 ${channel.importance}"
                },
                status = if (channel.importance == NotificationManager.IMPORTANCE_NONE) "已关闭" else "已开启",
                statusTint = if (channel.importance == NotificationManager.IMPORTANCE_NONE) H.Rose else H.Mint,
                action = "设置此通知渠道",
                onAction = {
                    open(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id),
                    )
                },
            )
        }
    }

    RiseIn(delayMs = 120) {
        PermissionCard(
            title = "精确闹钟",
            description = "用于本机准点调度，不保证服务器推送或绕过勿扰模式",
            status = if (exact) "可用" else "未授权",
            statusTint = if (exact) H.Mint else H.Coral,
            action = if (Build.VERSION.SDK_INT >= 31 && !exact) "去授权精确闹钟" else null,
            onAction = if (Build.VERSION.SDK_INT >= 31 && !exact) {
                { open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())) }
            } else {
                null
            },
        )
    }

    RiseIn(delayMs = 160) {
        PermissionCard(
            title = "全屏提醒",
            description = if (Build.VERSION.SDK_INT < 34) "此系统版本无需单独授权" else "到点全屏唤醒需要这项授权",
            status = when {
                Build.VERSION.SDK_INT < 34 -> "无需授权"
                fullScreen -> "已允许"
                else -> "未允许"
            },
            statusTint = if (Build.VERSION.SDK_INT < 34 || fullScreen) H.Mint else H.Coral,
            action = if (Build.VERSION.SDK_INT >= 34) "去全屏提醒设置" else null,
            onAction = if (Build.VERSION.SDK_INT >= 34) {
                { open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())) }
            } else {
                null
            },
        )
    }

    RiseIn(delayMs = 200) {
        DetailCard {
            Text("本地测试提醒", style = MaterialTheme.typography.titleLarge, color = H.Ink)
            Text(
                "点击后约 5 秒发送一条真实 Android 通知，可以退到桌面观察。只测试本机调度和通知，不创建、确认或修改正式惦记。",
                style = MaterialTheme.typography.bodySmall,
                color = H.Ink3Strong,
            )
            BuguButton(
                text = "5 秒后发送本地测试通知",
                onClick = {
                    try {
                        LocalNotificationTest.schedule(context)
                        onSnack("已安排 5 秒后的本地测试，请观察通知栏")
                    } catch (e: IllegalStateException) {
                        onSnack(e.message ?: "无法安排测试")
                    } catch (_: SecurityException) {
                        onSnack("权限已变化，请检查系统设置后重试")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
    var submitOk by remember { mutableStateOf<Boolean?>(null) }

    suspend fun load() {
        loading = true
        loadError = null
        try {
            items = api.myFeedback().take(50)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadError = "反馈记录加载失败，请重试"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    RiseIn {
        DetailCard {
            Text("常见问题", style = MaterialTheme.typography.titleLarge, color = H.Ink)
            FaqItem(
                question = "收不到提醒？",
                answer = "请查看「权限与送达诊断」，确认通知、通知渠道和精确闹钟状态，并检查系统省电与后台运行限制。",
            )
            FaqItem(
                question = "为什么没有全屏或声音？",
                answer = "全屏权限、通知渠道、音量与勿扰模式都可能影响呈现。本机测试成功不代表服务器推送一定成功。",
            )
            FaqItem(
                question = "如何修改资料？",
                answer = "在「我的」点击修改昵称，点击头像可从系统相册选择图片。",
            )
        }
    }

    RiseIn(delayMs = 60) {
        DetailCard {
            Text("提交反馈", style = MaterialTheme.typography.titleLarge, color = H.Ink)
            Text("选一个类别，告诉咕咕发生了什么。", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                feedbackCategories.forEach { (key, label) ->
                    val selected = key == category
                    Box(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) H.Coral else H.WarmFill)
                            .border(1.dp, if (selected) H.Coral else H.Hairline, RoundedCornerShape(999.dp))
                            .clickable(enabled = !submitting) { category = key; submitMessage = null }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else H.Ink2,
                        )
                    }
                }
            }
            BuguTextField(
                value = content,
                onValueChange = {
                    content = it
                    submitMessage = null
                    submitOk = null
                },
                label = "反馈内容（10 至 2000 字）",
                enabled = !submitting,
                singleLine = false,
                minLines = 4,
                isError = content.trim().length > 2000,
                supportingText = "${content.trim().length}/2000 · 请勿填写验证码等敏感信息",
            )
            submitMessage?.let { message ->
                val ok = submitOk == true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (ok) H.MintTint else H.RoseTint)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) H.MintInk else H.RoseInk,
                    )
                }
            }
            BuguButton(
                text = if (submitting) "提交中…" else "提交反馈",
                onClick = {
                    scope.launch {
                        submitting = true
                        submitMessage = null
                        submitOk = null
                        try {
                            val created = api.submitFeedback(
                                CreateFeedbackRequest.validated(category, content, BuildConfig.VERSION_NAME),
                            )
                            items = (listOf(created) + items.filterNot { it.id == created.id }).take(50)
                            content = ""
                            submitMessage = "反馈已提交，可以在下方查看处理状态和回复"
                            submitOk = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            submitMessage = "提交未成功确认，内容已保留。请先刷新记录确认是否收到，再决定重试。"
                            submitOk = false
                        } finally {
                            submitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && !submitting && content.trim().length in 10..2000,
                loading = submitting,
            )
        }
    }

    RiseIn(delayMs = 100) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的反馈 · 最近 50 条", style = MaterialTheme.typography.titleLarge, color = H.Ink)
            Spacer(Modifier.weight(1f))
            BuguButton(
                text = if (loading) "加载中…" else "刷新记录",
                onClick = { scope.launch { load() } },
                modifier = Modifier.width(108.dp),
                style = BuguButtonStyle.Secondary,
                height = 44.dp,
                enabled = !loading && !submitting,
            )
        }
    }

    loadError?.let { message ->
        DetailCard {
            Text(message, style = MaterialTheme.typography.bodySmall, color = H.RoseInk)
            BuguButton(
                text = "重试",
                onClick = { scope.launch { load() } },
                modifier = Modifier.fillMaxWidth(),
                style = BuguButtonStyle.Secondary,
                height = 44.dp,
            )
        }
    }

    if (loading && items.isEmpty()) {
        DetailCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在读取反馈记录…", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
            }
        }
    } else if (!loading && loadError == null && items.isEmpty()) {
        EmptyState(
            title = "还没有反馈记录",
            subtitle = "遇到问题随时告诉咕咕，它会记下来",
        )
    } else {
        items.forEachIndexed { index, item ->
            RiseIn(delayMs = index * 30) {
                FeedbackCard(item)
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(question, style = MaterialTheme.typography.titleMedium, color = H.Ink)
        Spacer(Modifier.height(2.dp))
        Text(answer, style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
    }
}

@Composable
private fun FeedbackCard(item: FeedbackDto) {
    BuguCard(
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                feedbackCategories[item.category] ?: item.category,
                style = MaterialTheme.typography.titleMedium,
                color = H.Ink,
                modifier = Modifier.weight(1f),
            )
            TagChip(
                text = feedbackStatusLabel(item.status),
                tint = when (item.status) {
                    "RESOLVED" -> H.Mint
                    "PROCESSING" -> H.Indigo
                    else -> H.Coral
                },
            )
        }
        Text(item.content, style = MaterialTheme.typography.bodyMedium, color = H.Ink)
        Text(
            "提交 ${item.createdAt.replace('T', ' ')}",
            style = MaterialTheme.typography.bodySmall,
            color = H.Ink3Strong,
        )
        val reply = item.reply?.takeIf { it.isNotBlank() }
        if (reply != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(H.WarmFill)
                    .padding(12.dp),
            ) {
                Text("咕咕回复", style = MaterialTheme.typography.titleSmall, color = H.CoralInk)
                Spacer(Modifier.height(2.dp))
                Text(reply, style = MaterialTheme.typography.bodyMedium, color = H.Ink)
            }
        } else {
            Text("咕咕还在看，有回复会出现在这里。", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
        }
    }
}

@Composable
private fun AboutContent() {
    var agreement by rememberSaveable { mutableStateOf<String?>(null) }
    RiseIn {
        BuguCard(
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = H.CoralTint,
            borderColor = H.Coral.copy(alpha = 0.38f),
            contentPadding = PaddingValues(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GuguBird(
                    mood = GuguMood.CHEER,
                    modifier = Modifier.size(64.dp),
                    onTap = null,
                    pokeLines = emptyList(),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("布谷 BUGU", style = MaterialTheme.typography.headlineSmall, color = H.CoralInk)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "让每一份惦记，都有回音。",
                        style = MaterialTheme.typography.bodySmall,
                        color = H.Ink3Strong,
                    )
                    Spacer(Modifier.height(8.dp))
                    TagChip(text = "v${BuildConfig.VERSION_NAME}", tint = H.Coral)
                }
            }
        }
    }
    RiseIn(delayMs = 60) {
        BuguCard(
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            onClick = { agreement = "user" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("用户协议", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = H.Ink3)
            }
        }
    }
    RiseIn(delayMs = 100) {
        BuguCard(
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            onClick = { agreement = "privacy" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("隐私政策", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = H.Ink3)
            }
        }
    }
    agreement?.let { AgreementDialog(it) { agreement = null } }
}
