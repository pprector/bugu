package com.bugu.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.bugu.ui.common.BrandSwitch
import com.bugu.ui.common.GradientButton
import com.bugu.ui.common.StatusAvatar
import com.bugu.ui.common.TagChip
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
    Column(Modifier.fillMaxSize().background(H.Canvas).statusBarsPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("陪伴", style = MaterialTheme.typography.headlineSmall, color = H.Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenFriends) { Text("添加亲友", color = H.CoralInk) }
        }
        Text("让每一份惦记，都有回应。", color = H.Ink2)
        Spacer(Modifier.height(24.dp))
        if (bondsLoading) CircularProgressIndicator(color = H.Coral)
        if (bondsError) {
            Text("亲友列表加载失败，请重试", color = H.Rose)
            TextButton(onClick = onRefreshBonds) { Text("重试") }
        }
        val active = bonds.filter { it.status == "ACTIVE" }
        Text("已关联 · ${active.size} 人", color = H.Ink2)
        if (active.isEmpty() && !bondsLoading && !bondsError) {
            Text("还没有关联亲友，添加后就能互相发送提醒。", modifier = Modifier.padding(vertical = 24.dp), color = H.Ink2)
        }
        active.forEach { bond ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(22.dp))
                .background(Color.White).clickable(role = Role.Button, onClickLabel = "查看${bond.displayName}的陪伴详情") { onOpenBond(bond.id) }
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusAvatar(avatar = null, size = 48.dp, fallbackText = bond.displayName.take(1))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bond.displayName, style = MaterialTheme.typography.titleMedium, color = H.Ink)
                    Text("查看双方提醒与独立设置", color = H.Ink2, style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = H.Ink3)
            }
        }
        Text("备注仅自己可见。点击一位亲友查看双方提醒。", color = H.Ink2,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
        if (bonds.any { it.status == "PENDING_OUTBOUND" }) {
            TextButton(onClick = onOpenFriends) { Text("邀请中的关联 · 查看邀请码", color = H.CoralInk) }
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    Column(Modifier.fillMaxSize().background(H.Canvas).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("返回陪伴", color = H.CoralInk) }
        if (bond == null) {
            Text("此关联已失效", color = H.Ink)
            TextButton(onClick = onRefreshBonds) { Text("刷新关系") }
            return@Column
        }
        val name = bond.displayName
        Text(name, style = MaterialTheme.typography.headlineSmall, color = H.Ink)
        Text("对方昵称：${bond.counterpartNickname ?: "未设置"}", color = H.Ink2)
        Spacer(Modifier.height(20.dp))
        GradientButton("给${name}发提醒", { onCreateFor(bondId) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text("双方提醒", style = MaterialTheme.typography.titleLarge, color = H.Ink)
        if (error) {
            Text("提醒与接收设置加载失败", color = H.Rose)
            TextButton(onClick = { retry++ }) { Text("重试") }
        } else if (reminders == null) {
            CircularProgressIndicator(color = H.Coral)
        } else if (reminders!!.isEmpty()) {
            Text("暂无双方提醒，发出第一份惦记吧", color = H.Ink2, modifier = Modifier.padding(vertical = 16.dp))
        }
        reminders.orEmpty().forEach { (byMe, r) ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(20.dp))
                .background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, color = H.Ink)
                    Text("${if (byMe) "我发出" else "对方发来"} · ${parseIsoTime(r.fireAt)?.format(TIME_FMT) ?: r.fireAt}",
                        color = H.Ink2, style = MaterialTheme.typography.bodySmall)
                }
                TagChip(statusLabel(r.status), statusColor(r.status))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("暂停接收单次提醒", color = H.Ink)
                Text("仅影响此亲友，其他亲友不受影响", color = H.Ink2, style = MaterialTheme.typography.bodySmall)
            }
            if (muted != null && !error && !busy) BrandSwitch(checked = muted == true, onCheckedChange = { next ->
                scope.launch {
                    busy = true
                    try {
                        val peerId = bond.counterpartId ?: return@launch
                        if (next) api.mute(peerId, "ONCE") else api.unmute(peerId, "ONCE")
                        muted = next
                        onSnack(if (next) "已暂停此人的单次提醒" else "已恢复此人的单次提醒")
                    } catch (_: Exception) { onSnack("设置失败，请重试") }
                    finally { busy = false }
                }
            }, modifier = Modifier.semantics { contentDescription = "暂停接收${name}的单次提醒" })
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { editRemark = true }, enabled = !busy) { Text("设置私人备注 · ${bond.remark ?: "未设置"}", color = H.CoralInk) }
        Text("仅自己可见，留空后显示对方昵称，最多 30 字。", color = H.Ink2, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { confirmUnbind = true }, enabled = !busy) { Text("解除关联", color = H.Rose) }
    }
    if (editRemark) AlertDialog(
        onDismissRequest = { if (!busy) editRemark = false },
        title = { Text("设置私人备注") },
        text = { Column {
            Text("仅自己可见，留空显示对方昵称（最多 30 字）")
            OutlinedTextField(value = remark, onValueChange = { remark = it.take(30) },
                label = { Text("私人备注") }, singleLine = true, enabled = !busy)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                try {
                    api.updateBondRemark(bondId, remark.trim().takeIf { it.isNotEmpty() })
                    editRemark = false
                    onRefreshBonds()
                    onSnack("私人备注已保存，仅自己可见")
                } catch (_: Exception) { onSnack("备注保存失败，请重试") }
                finally { busy = false }
            }
        }) { Text("保存") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { editRemark = false }) { Text("取消") } },
    )
    if (confirmUnbind) AlertDialog(
        onDismissRequest = { if (!busy) confirmUnbind = false },
        title = { Text("解除关联？") },
        text = { Text("解除后无法继续互发提醒，此关联未完成的提醒将停止触发。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                try {
                    api.unbind(bondId)
                    com.bugu.schedule.ReminderSync.removeBond(context, bondId)
                    confirmUnbind = false
                    onRefreshBonds()
                    onBack()
                    onSnack("已解除关联")
                } catch (_: Exception) { onSnack("解除关联失败，请重试") }
                finally { busy = false }
            }
        }) { Text("确认解除", color = H.Rose) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmUnbind = false }) { Text("保留关联") } },
    )
}
