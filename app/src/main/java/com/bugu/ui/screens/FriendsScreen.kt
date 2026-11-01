package com.bugu.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bugu.client.api.*
import com.bugu.ui.common.BuguButton
import com.bugu.ui.common.BuguButtonStyle
import com.bugu.ui.common.BuguCard
import com.bugu.ui.common.BuguDialog
import com.bugu.ui.common.BuguDialogActions
import com.bugu.ui.common.BuguTextField
import com.bugu.ui.common.EmptyState
import com.bugu.ui.common.GuguMood
import com.bugu.ui.common.RiseIn
import com.bugu.ui.common.StatusAvatar
import com.bugu.ui.common.TagChip
import com.bugu.ui.common.TopBar
import com.bugu.ui.common.parseIsoTime
import com.bugu.ui.theme.H
import java.time.format.DateTimeFormatter
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
            try {
                invite = api.initiateBond()
                onRefresh()
            } catch (_: Exception) {
                inviteError = true
            } finally {
                busy = false
            }
        }
    }

    fun copy(codeValue: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("布谷邀请码", codeValue))
        onSnack("邀请码已复制")
    }

    fun share(inv: InitiateResponse) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "和我一起用布谷互相惦记吧，邀请码：${inv.bindCode}，${inv.deeplink}",
                    )
                },
                "分享邀请",
            ),
        )
    }

    LaunchedEffect(Unit) { initiate() }

    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .navigationBarsPadding(),
    ) {
        TopBar(
            title = "添加亲友",
            onBack = onBack,
            guguMood = GuguMood.CHEER,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "多一份惦记。双方确认后，就能互相发惦记。",
                style = MaterialTheme.typography.bodySmall,
                color = H.Ink3Strong,
            )

            // 邀请码 Hero 卡
            RiseIn {
                if (invite == null && busy) {
                    LoadingCard("正在为你准备邀请码…")
                } else if (invite == null && inviteError) {
                    BuguCard(
                        shape = MaterialTheme.shapes.extraLarge,
                        contentPadding = PaddingValues(20.dp),
                    ) {
                        Text("邀请码暂时没准备好", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                        Spacer(Modifier.size(4.dp))
                        Text("检查一下网络，再让咕咕试一次。", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
                        Spacer(Modifier.size(12.dp))
                        BuguButton(
                            text = "重新获取",
                            onClick = ::initiate,
                            modifier = Modifier.fillMaxWidth(),
                            style = BuguButtonStyle.Secondary,
                            height = 46.dp,
                        )
                    }
                } else {
                    invite?.let { inv ->
                        BuguCard(
                            shape = MaterialTheme.shapes.extraLarge,
                            containerColor = H.CoralTint,
                            borderColor = H.Coral.copy(alpha = 0.38f),
                            contentPadding = PaddingValues(20.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("我的邀请码", style = MaterialTheme.typography.titleLarge, color = H.CoralInk)
                                Spacer(Modifier.weight(1f))
                                TagChip(text = "给家人朋友", tint = H.Coral)
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(inv.bindCode, style = MaterialTheme.typography.displayMedium, color = H.CoralInk)
                            Spacer(Modifier.size(2.dp))
                            Text(
                                "把这串数字发给家人朋友，就能开始互相惦记。",
                                style = MaterialTheme.typography.bodySmall,
                                color = H.Ink3Strong,
                            )
                            Spacer(Modifier.size(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BuguButton(
                                    text = "复制邀请码",
                                    onClick = { copy(inv.bindCode) },
                                    modifier = Modifier.weight(1f),
                                    style = BuguButtonStyle.Secondary,
                                    height = 46.dp,
                                )
                                BuguButton(
                                    text = "分享邀请",
                                    onClick = { share(inv) },
                                    modifier = Modifier.weight(1f),
                                    height = 46.dp,
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "有效期至 ${
                                        parseIsoTime(inv.expiresAt)
                                            ?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                                            ?: inv.expiresAt
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = H.Ink3Strong,
                                    modifier = Modifier.weight(1f),
                                )
                                BuguButton(
                                    text = if (busy) "刷新中…" else "刷新",
                                    onClick = ::initiate,
                                    modifier = Modifier.width(88.dp),
                                    style = BuguButtonStyle.Secondary,
                                    height = 44.dp,
                                    loading = busy,
                                )
                            }
                        }
                    }
                }
            }

            // 接受邀请卡
            RiseIn(delayMs = 60) {
                BuguCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(20.dp),
                ) {
                    Text("接受对方的邀请", style = MaterialTheme.typography.titleLarge, color = H.Ink)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "输入对方给你的 6 位邀请码，确认后就能互相惦记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = H.Ink3Strong,
                    )
                    Spacer(Modifier.size(12.dp))
                    BuguTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c in '0'..'9' }.take(6) },
                        label = "对方的 6 位邀请码",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = "输入后，咕咕会帮你确认对方是谁",
                    )
                    Spacer(Modifier.size(10.dp))
                    BuguButton(
                        text = if (busy) "查询中…" else "下一步",
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    invitation = api.invitation(code)
                                } catch (e: Exception) {
                                    onSnack(e.message ?: "邀请查询失败，请重试")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = code.length == 6 && !busy,
                        loading = busy && code.length == 6,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "关联不会授予修改对方个人资料的权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = H.Ink3Strong,
                    )
                }
            }

            // 已关联亲友
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已关联亲友", style = MaterialTheme.typography.titleLarge, color = H.Ink)
                Spacer(Modifier.weight(1f))
                val activeCount = bonds.count { it.status == "ACTIVE" }
                TagChip(text = "$activeCount 人", tint = H.Indigo)
            }

            when {
                bondsLoading -> LoadingCard("正在同步亲友列表…")
                bondsError -> BuguCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(20.dp),
                ) {
                    Text("亲友列表没加载出来", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                    Spacer(Modifier.size(4.dp))
                    Text("检查网络后再试试。", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
                    Spacer(Modifier.size(12.dp))
                    BuguButton(
                        text = "重试",
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        style = BuguButtonStyle.Secondary,
                        height = 46.dp,
                    )
                }
                bonds.none { it.status == "ACTIVE" } -> EmptyState(
                    title = "还没有关联亲友",
                    subtitle = "把邀请码发给家人，开始互相惦记",
                    actionText = "刷新邀请码",
                    onAction = ::initiate,
                )
                else -> bonds.filter { it.status == "ACTIVE" }.forEachIndexed { index, bond ->
                    RiseIn(delayMs = index * 40) {
                        BuguCard(
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            onClick = { onOpenBond(bond.id) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusAvatar(
                                    avatar = null,
                                    size = 48.dp,
                                    fallbackText = bond.displayName.take(1),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(bond.displayName, style = MaterialTheme.typography.titleMedium, color = H.Ink)
                                    Text(
                                        "查看双方的惦记与设置",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = H.Ink3Strong,
                                    )
                                }
                                BuguButton(
                                    text = "发惦记",
                                    onClick = { onCreateFor(bond.id) },
                                    modifier = Modifier.width(84.dp),
                                    style = BuguButtonStyle.Secondary,
                                    height = 44.dp,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = H.Ink3,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    invitation?.let { inv ->
        BuguDialog(
            title = "确认关联",
            onDismissRequest = { if (!busy) invitation = null },
            mood = GuguMood.CHEER,
            subtitle = "有人想和你互相惦记",
            tag = "新的互伴",
            dismissible = !busy,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "对方邀请码 · ${inv.bindCode}",
                        style = MaterialTheme.typography.titleMedium,
                        color = H.CoralInk,
                    )
                    Text(
                        "确认后你们可以互相发惦记，也可以各自设置只有自己能看见的私人备注。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = H.Ink3Strong,
                    )
                }
            },
            actions = {
                BuguDialogActions(
                    confirmText = "确认关联",
                    onConfirm = {
                        scope.launch {
                            busy = true
                            try {
                                api.confirmBond(inv.bindCode)
                                invitation = null
                                code = ""
                                onRefresh()
                                onSnack("关联成功，可继续添加亲友")
                            } catch (e: Exception) {
                                onSnack(e.message ?: "关联失败，请重试")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    dismissText = "取消",
                    onDismiss = { invitation = null },
                    loading = busy,
                )
            },
        )
    }
}

@Composable
private fun LoadingCard(text: String) {
    BuguCard(
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(vertical = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
        }
    }
}
