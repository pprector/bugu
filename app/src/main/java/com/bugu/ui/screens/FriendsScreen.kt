package com.bugu.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bugu.client.api.*
import com.bugu.ui.common.BackIconButton
import com.bugu.ui.common.GradientButton
import com.bugu.ui.common.parseIsoTime
import com.bugu.ui.theme.H
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(
    api: GeitaApi,
    bonds: List<BondDto>,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSnack: (String) -> Unit,
    onCreateFor: (Long) -> Unit,
    onOpenBond: (Long) -> Unit,
    bondsLoading: Boolean,
    bondsError: Boolean,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var invite by remember { mutableStateOf<InitiateResponse?>(null) }
    var busy by remember { mutableStateOf(false) }
    var inviteError by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf<InvitationDto?>(null) }
    fun initiate() {
        if (busy) return
        scope.launch {
            busy = true
            inviteError = false
            try { invite = api.initiateBond(); onRefresh() }
            catch (_: Exception) { inviteError = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { initiate() }
    Column(Modifier.fillMaxSize().background(H.Canvas).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackIconButton(onClick = onBack)
        Spacer(Modifier.height(12.dp))
        Text("添加亲友", style = MaterialTheme.typography.headlineSmall, color = H.Ink)
        Text("多一份惦记。双方确认后，即可互相发送提醒。", color = H.Ink2)
        Spacer(Modifier.height(24.dp))
        Text("我的邀请码", style = MaterialTheme.typography.titleLarge, color = H.Ink)
        if (busy && invite == null) CircularProgressIndicator(color = H.Coral)
        if (inviteError) Text("邀请码获取失败，请重试", color = H.Rose)
        invite?.let { inv ->
            Text(inv.bindCode, style = MaterialTheme.typography.displayMedium, color = H.Coral)
            Text("有效期至 ${parseIsoTime(inv.expiresAt) ?: inv.expiresAt}", color = H.Ink2)
            Row {
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("布谷邀请码", inv.bindCode))
                    onSnack("邀请码已复制")
                }) { Text("复制邀请码") }
                TextButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "和我一起用布谷互相惦记吧，邀请码：${inv.bindCode}，${inv.deeplink}")
                    }, "分享邀请"))
                }) { Text("分享邀请") }
            }
        }
        TextButton(onClick = ::initiate, enabled = !busy) { Text(if (inviteError) "重试邀请码" else "刷新邀请码") }
        Spacer(Modifier.height(20.dp))
        Text("接受对方的邀请", style = MaterialTheme.typography.titleLarge, color = H.Ink)
        OutlinedTextField(code, { code = it.filter { c -> c in '0'..'9' }.take(6) },
            label = { Text("对方的 6 位邀请码") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        GradientButton("下一步", {
            scope.launch {
                busy = true
                try { invitation = api.invitation(code) }
                catch (e: Exception) { onSnack(e.message ?: "邀请查询失败，请重试") }
                finally { busy = false }
            }
        }, enabled = code.length == 6 && !busy, modifier = Modifier.fillMaxWidth())
        Text("关联不会授予修改对方个人资料的权限。", color = H.Ink2, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Text("已关联亲友", style = MaterialTheme.typography.titleLarge, color = H.Ink)
        if (bondsLoading) CircularProgressIndicator(color = H.Coral)
        if (bondsError) {
            Text("关系列表加载失败", color = H.Rose)
            TextButton(onClick = onRefresh) { Text("重试") }
        }
        val active = bonds.filter { it.status == "ACTIVE" }
        if (active.isEmpty() && !bondsLoading && !bondsError) Text("还没有关联亲友", color = H.Ink2)
        active.forEach { bond ->
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { onOpenBond(bond.id) }, modifier = Modifier.weight(1f)) { Text(bond.displayName) }
                TextButton(onClick = { onCreateFor(bond.id) }) { Text("发提醒") }
            }
        }
    }
    invitation?.let { inv ->
        AlertDialog(onDismissRequest = { if (!busy) invitation = null },
            title = { Text("确认关联") },
            text = { Text("确认接受用户 ${inv.requesterId} 的邀请？关联后你们可以互相发送提醒，各自设置私人备注。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        api.confirmBond(inv.bindCode)
                        invitation = null
                        code = ""
                        onRefresh()
                        onSnack("关联成功，可继续添加亲友")
                    } catch (e: Exception) { onSnack(e.message ?: "关联失败，请重试") }
                    finally { busy = false }
                }
            }) { Text("确认关联") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { invitation = null }) { Text("取消") } })
    }
}
