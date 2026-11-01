package com.bugu.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bugu.client.api.*
import com.bugu.ui.common.*
import com.bugu.ui.theme.H
import kotlinx.coroutines.launch

@Composable
fun CompanionScreen(
    bonds: List<BondDto>,
    bondsLoading: Boolean,
    bondsError: Boolean,
    onOpenFriends: () -> Unit,
    onOpenBond: (Long) -> Unit,
    onRefreshBonds: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // 顶部:咕咕鸟 + 标题 + 添加按钮(对齐看板顶部锚点)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuguBird(
                mood = GuguMood.RING,
                modifier = Modifier.size(44.dp),
                onTap = { onRefreshBonds() },
                pokeLines = listOf("咕咕!", "谁在惦记你呀?", "去看看吧～"),
                bubbleOffset = (-8).dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("陪伴", style = MaterialTheme.typography.headlineSmall, color = H.Ink)
                Text(
                    "让每一份惦记,都有回应。",
                    style = MaterialTheme.typography.bodySmall,
                    color = H.Ink3Strong,
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, H.Hairline, CircleShape)
                    .clickable(onClick = onOpenFriends)
                    .semantics { contentDescription = "添加亲友" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PersonAdd, null, Modifier.size(22.dp), tint = H.Ink2)
            }
        }

        Spacer(Modifier.height(20.dp))

        val active = bonds.filter { it.status == "ACTIVE" }

        when {
            bondsLoading -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .border(1.dp, H.Hairline, RoundedCornerShape(22.dp))
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("正在同步互伴关系…", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
                }
            }
            bondsError -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .border(1.dp, H.Hairline, RoundedCornerShape(22.dp))
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("亲友列表加载失败", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text("请重试", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRefreshBonds) { Text("重试", color = H.CoralInk) }
                }
            }
            active.isEmpty() -> {
                EmptyState(
                    title = "还没有互伴",
                    subtitle = "绑定家人朋友,开始互相惦记",
                    actionText = "添加第一位亲友",
                    onAction = onOpenFriends,
                )
            }
            else -> {
                Text(
                    "已关联 · ${active.size} 人",
                    style = MaterialTheme.typography.titleSmall,
                    color = H.Ink3Strong,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                active.forEachIndexed { index, bond ->
                    RiseIn(delayMs = index * 40) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White)
                                .border(1.dp, H.Hairline, RoundedCornerShape(22.dp))
                                .clickable(role = Role.Button, onClickLabel = "查看${bond.displayName}的陪伴详情") { onOpenBond(bond.id) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusAvatar(avatar = null, size = 48.dp, fallbackText = bond.displayName.take(1))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bond.displayName, style = MaterialTheme.typography.titleMedium, color = H.Ink)
                                Text(
                                    "查看双方提醒与独立设置",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = H.Ink3Strong,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                Modifier.size(20.dp),
                                tint = H.Ink3,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "备注仅自己可见。点击一位亲友查看双方提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = H.Ink3Strong,
                )
            }
        }

        if (bonds.any { it.status == "PENDING_OUTBOUND" }) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onOpenFriends) {
                Icon(Icons.Outlined.PersonAdd, null, Modifier.size(16.dp), tint = H.CoralInk)
                Spacer(Modifier.width(4.dp))
                Text("邀请中的关联 · 查看邀请码", color = H.CoralInk)
            }
        }
    }
}

@Composable
fun CompanionDetailScreen(
    api: GeitaApi,
    bondId: Long,
    bonds: List<BondDto>,
    onBack: () -> Unit,
    onRefreshBonds: () -> Unit,
    onCreateFor: (Long) -> Unit,
    onSnack: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val bond = bonds.firstOrNull { it.id == bondId && it.status == "ACTIVE" }
    val scope = rememberCoroutineScope()
    var reminders by remember(bondId) { mutableStateOf<List<Pair<Boolean, ReminderView>>?>(null) }
    var muted by remember(bondId) { mutableStateOf<Boolean?>(null) }
    var error by remember(bondId) { mutableStateOf(false) }
    var retry by remember(bondId) { mutableStateOf(0) }
    var busy by remember(bondId) { mutableStateOf(false) }
    var editRemark by remember(bondId) { mutableStateOf(false) }
    var remark by remember(bond?.remark) { mutableStateOf(bond?.remark.orEmpty()) }
    var confirmUnbind by remember(bondId) { mutableStateOf(false) }
    LaunchedEffect(bondId, retry, bond?.status) {
        if (bond == null) return@LaunchedEffect
        error = false
        try {
            val mine = api.myReminders().filter { it.bondId == bondId }.map { true to it }
            val incoming = api.incomingReminders().filter { it.bondId == bondId }.map { false to it }
            reminders = (mine + incoming).distinctBy { it.second.id }.sortedByDescending { it.second.fireAt }
            muted = api.mutedList().any { it.creatorId == bond.counterpartId && it.kind == "ONCE" }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            error = true
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .navigationBarsPadding(),
    ) {
        TopBar(
            title = bond?.displayName ?: "陪伴详情",
            onBack = onBack,
            guguMood = GuguMood.CHEER,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
        if (bond == null) {
            Text("这段陪伴已经结束", color = H.Ink, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRefreshBonds) { Text("刷新关系", color = H.CoralInk) }
            return@Column
        }
        val name = bond.displayName
        Text(
            "对方昵称：${bond.counterpartNickname ?: "还没设置"}",
            color = H.Ink3Strong,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        // 发提醒入口:包进白卡,对齐看板卡片规范
        RiseIn {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .border(1.dp, H.Hairline, RoundedCornerShape(22.dp))
                    .padding(16.dp),
            ) {
                Text("为 $name 设一条惦记", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Spacer(Modifier.height(4.dp))
                Text("到点咕咕会替你准时呼唤 TA", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
                Spacer(Modifier.height(12.dp))
                GradientButton("给${name}发提醒", { onCreateFor(bondId) }, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("双方提醒", style = MaterialTheme.typography.titleLarge, color = H.Ink)
        Spacer(Modifier.height(8.dp))
        if (error) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .border(1.dp, H.Hairline, RoundedCornerShape(20.dp)).padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("提醒与接收设置加载失败", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { retry++ }) { Text("重试", color = H.CoralInk) }
            }
        } else if (reminders == null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else if (reminders!!.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .border(1.dp, H.Hairline, RoundedCornerShape(20.dp)).padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("暂无双方提醒", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Spacer(Modifier.height(4.dp))
                Text("发出第一份惦记吧", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
            }
        }
        reminders.orEmpty().forEachIndexed { index, (byMe, r) ->
            RiseIn(delayMs = index * 40) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, H.Hairline, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            parseIsoTime(r.fireAt)?.format(TIME_FMT) ?: "--:--",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                            color = H.Ink,
                        )
                        TagChip(text = statusLabel(r.status), tint = statusColor(r.status))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium, color = H.Ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${if (byMe) "我发出" else "对方发来"} · ${kindLabel(r.kind)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3Strong,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(1.dp, H.Hairline, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("暂停接收单次提醒", color = H.Ink, style = MaterialTheme.typography.titleMedium)
                Text("仅影响此亲友的单次提醒,循环提醒不受影响", color = H.Ink3Strong, style = MaterialTheme.typography.bodySmall)
            }
            if (muted != null && !error && !busy) {
                BrandSwitch(
                    checked = muted == true,
                    onCheckedChange = { next ->
                        scope.launch {
                            busy = true
                            try {
                                val peerId = bond.counterpartId ?: return@launch
                                if (next) api.mute(peerId, "ONCE") else api.unmute(peerId, "ONCE")
                                muted = next
                                onSnack(if (next) "已暂停此人的单次提醒" else "已恢复此人的单次提醒")
                            } catch (_: Exception) {
                                onSnack("设置失败,请重试")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "暂停接收${name}的单次提醒" },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BuguCard(
            shape = RoundedCornerShape(20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) { editRemark = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("私人备注", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                    Text(
                        bond.remark ?: "还没设置 · 仅自己可见",
                        style = MaterialTheme.typography.bodySmall,
                        color = H.Ink3Strong,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = H.Ink3)
            }
            HorizontalDivider(color = H.Hairline, thickness = 1.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) { confirmUnbind = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("解除关联", style = MaterialTheme.typography.titleMedium, color = H.RoseInk)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = H.Rose)
            }
        }
        Text(
            "备注仅自己可见，留空后显示对方昵称，最多 30 字。",
            color = H.Ink3Strong,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
        }
    }
    if (editRemark) BuguDialog(
        title = "设置私人备注",
        onDismissRequest = { if (!busy) editRemark = false },
        mood = GuguMood.CHEER,
        subtitle = "只有你能看见这句备注",
        dismissible = !busy,
        content = {
            BuguTextField(
                value = remark,
                onValueChange = { remark = it.take(30) },
                label = "私人备注",
                enabled = !busy,
                supportingText = "${remark.length}/30 · 留空显示对方昵称",
            )
        },
        actions = {
            BuguDialogActions(
                confirmText = if (busy) "保存中…" else "保存",
                onConfirm = {
                    scope.launch {
                        busy = true
                        try {
                            api.updateBondRemark(bondId, remark.trim().takeIf { it.isNotEmpty() })
                            editRemark = false
                            onRefreshBonds()
                            onSnack("私人备注已保存,仅自己可见")
                        } catch (_: Exception) {
                            onSnack("备注保存失败,请重试")
                        } finally {
                            busy = false
                        }
                    }
                },
                dismissText = "取消",
                onDismiss = { editRemark = false },
                loading = busy,
            )
        },
    )
    if (confirmUnbind) BuguDialog(
        title = "解除关联?",
        onDismissRequest = { if (!busy) confirmUnbind = false },
        mood = GuguMood.SLEEP,
        tag = "需要确认",
        tagTint = H.Rose,
        subtitle = "解除后就不能再互相惦记了",
        dismissible = !busy,
        content = {
            Text(
                "这段关联里还没完成的惦记会停下，之后也无法再互发惦记。",
                style = MaterialTheme.typography.bodyMedium,
                color = H.Ink3Strong,
            )
        },
        actions = {
            BuguDialogActions(
                confirmText = "确认解除",
                onConfirm = {
                    scope.launch {
                        busy = true
                        try {
                            api.unbind(bondId)
                            com.bugu.schedule.ReminderSync.removeBond(context, bondId)
                            confirmUnbind = false
                            onRefreshBonds()
                            onBack()
                            onSnack("已解除关联")
                        } catch (_: Exception) {
                            onSnack("解除关联失败,请重试")
                        } finally {
                            busy = false
                        }
                    }
                },
                dismissText = "保留关联",
                onDismiss = { confirmUnbind = false },
                confirmStyle = BuguButtonStyle.Danger,
                loading = busy,
            )
        },
    )
}
