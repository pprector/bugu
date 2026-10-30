package com.bugu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bugu.client.api.BondDto
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.client.api.InitiateResponse
import com.bugu.ui.common.CircleIcon
import com.bugu.ui.common.EmptyState
import com.bugu.ui.common.GuguBird
import com.bugu.ui.common.GuguMood
import com.bugu.ui.common.RiseIn
import com.bugu.ui.common.StatusAvatar
import com.bugu.ui.common.TagChip
import com.bugu.ui.theme.H
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val EXPIRE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/**
 * 好友与绑定(PRD 1v1):ACTIVE 展示对端卡(可解绑);无 ACTIVE 时给出
 * 「发起绑定码 / 输入对方码确认」两条真实路径,PENDING_OUTBOUND 展示等待确认态。
 */
@Composable
fun FriendsScreen(
    api: GeitaApi,
    bonds: List<BondDto>,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSnack: (String) -> Unit,
    onCreateFor: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val active = bonds.firstOrNull { it.status == "ACTIVE" }
    val pendingOut = bonds.firstOrNull { it.status == "PENDING_OUTBOUND" }
    val peerName = active?.counterpartNickname ?: "家人"

    // 发起的绑定码(PENDING_OUTBOUND 等待确认时展示;可续发刷新)
    var invite by remember { mutableStateOf<InitiateResponse?>(null) }
    var initiating by remember { mutableStateOf(false) }
    var confirmCode by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var unbindTarget by remember { mutableStateOf<BondDto?>(null) } // 解绑确认弹层

    /** 发起/续发绑定码:服务端按 PENDING_OUTBOUND 幂等续期。 */
    fun initiate() {
        scope.launch {
            initiating = true
            try {
                invite = api.initiateBond()
            } catch (e: GeitaApiException) {
                onSnack(e.message ?: "获取绑定码失败,请稍后再试")
            } catch (_: Exception) {
                onSnack("网络不佳,请稍后再试")
            } finally {
                initiating = false
            }
        }
    }

    /** 输入对方绑定码确认,成功后重拉关系。 */
    fun confirm() {
        if (confirmCode.length != 6 || confirming) return
        scope.launch {
            confirming = true
            try {
                api.confirmBond(confirmCode)
                confirmCode = ""
                onSnack("绑定成功,开始互相惦记吧")
                onRefresh()
            } catch (e: GeitaApiException) {
                onSnack(e.message ?: "确认失败,请稍后再试")
            } catch (_: Exception) {
                onSnack("网络不佳,请稍后再试")
            } finally {
                confirming = false
            }
        }
    }

    fun unbind(bond: BondDto) {
        scope.launch {
            try {
                api.unbind(bond.id)
                onSnack("已解绑,惦记互不可见")
                onRefresh()
            } catch (e: GeitaApiException) {
                onSnack(e.message ?: "解绑失败,请稍后再试")
            } catch (_: Exception) {
                onSnack("网络不佳,请稍后再试")
            }
        }
    }

    // 无 ACTIVE 关系时自动发起一次,展示绑定码(进入即看得到,不靠手点)
    LaunchedEffect(active?.id) {
        if (active == null) initiate()
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 顶栏
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    bg = Color.White,
                    tint = H.Ink,
                    border = H.Hairline,
                    modifier = Modifier.clickable(onClick = onBack),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "好友与互伴",
                    style = MaterialTheme.typography.headlineSmall,
                    color = H.Ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (active == null) {
            // 绑定码发起卡:6 位码 + 有效期,可续发
            item {
                RiseIn {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFFFDF0EA))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircleIcon(icon = Icons.Filled.Favorite, bg = Color.White, tint = H.Coral)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("我的绑定码", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                                Text("把它念给或发给想互伴的 Ta", style = MaterialTheme.typography.bodySmall, color = H.Ink2)
                            }
                            CircleIcon(
                                icon = Icons.Outlined.Refresh,
                                bg = Color.White,
                                tint = H.Coral,
                                size = 38.dp,
                                iconSize = 18.dp,
                                modifier = Modifier.clickable(enabled = !initiating) { initiate() },
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        if (initiating && invite == null) {
                            CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        } else {
                            val inv = invite
                            if (inv != null) {
                                Text(
                                    inv.bindCode.chunked(1).joinToString(" "),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = H.Coral,
                                )
                                Spacer(Modifier.height(6.dp))
                                val expireText = remember(inv.bindCode) {
                                    parseIsoTime(inv.expiresAt)?.format(EXPIRE_FMT) ?: inv.expiresAt
                                }
                                Text(
                                    "有效期至 $expireText · 过期可点右上角刷新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = H.Ink3,
                                )
                            }
                        }
                    }
                }
            }

            // 等待确认态(我方为发起方)
            if (pendingOut != null) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GuguBird(GuguMood.VOICE, Modifier.size(46.dp), onTap = {})
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("等待对方确认中", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                            Text(
                                "对方输入绑定码后,你们就会成为互伴",
                                style = MaterialTheme.typography.bodySmall,
                                color = H.Ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TagChip(text = "待确认", tint = H.Indigo)
                    }
                }
            }

            // 输入对方绑定码确认
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleIcon(icon = Icons.Outlined.Link, bg = H.IndigoTint, tint = H.Indigo)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("有对方的绑定码?", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                            Text("输入 6 位码,立刻完成互伴", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(H.WarmFill)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            BasicTextField(
                                value = confirmCode,
                                onValueChange = { confirmCode = it.filter(Char::isDigit).take(6) },
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    color = H.Ink,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                    letterSpacing = 2.sp,
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { field ->
                                    Box {
                                        if (confirmCode.isEmpty()) {
                                            Text("6 位绑定码", style = MaterialTheme.typography.bodyLarge, color = H.Ink3)
                                        }
                                        field()
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (confirmCode.length == 6 && !confirming) {
                                        Brush.linearGradient(listOf(H.CoralLight, H.Coral))
                                    } else {
                                        Brush.linearGradient(listOf(H.Track, H.Track))
                                    },
                                )
                                .clickable(enabled = confirmCode.length == 6 && !confirming) { confirm() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                if (confirming) "确认中…" else "绑定",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (confirmCode.length == 6 && !confirming) Color.White else H.Ink3,
                            )
                        }
                    }
                }
            }

            item {
                EmptyState(
                    title = "还没有互伴",
                    subtitle = "发起绑定码,或输入对方的码,一切从这里开始",
                )
            }
        } else {
            // ACTIVE 互伴卡
            item {
                RiseIn {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.White)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusAvatar(
                                avatar = null,
                                size = 52.dp,
                                ring = H.Coral.copy(alpha = 0.5f),
                                fallbackText = peerName.take(1),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(peerName, style = MaterialTheme.typography.titleLarge, color = H.Ink)
                                    Spacer(Modifier.width(8.dp))
                                    TagChip(text = active.counterpartRelationTag ?: "互伴家人", tint = H.Coral)
                                    if (active.counterpartElderMode == true) {
                                        Spacer(Modifier.width(6.dp))
                                        TagChip(text = "长辈模式", tint = H.Indigo)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(13.dp), tint = H.Mint)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "已互伴 · 惦记彼此可见,可互相设提醒",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = H.Ink2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(H.RoseTint)
                                    .clickable { unbindTarget = active }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text("解绑", style = MaterialTheme.typography.titleSmall, color = H.Rose)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "给 TA 设一条惦记吧",
                                style = MaterialTheme.typography.bodySmall,
                                color = H.Ink3,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(H.CoralTint)
                                    .clickable(onClick = onCreateFor)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Alarm, null, Modifier.size(14.dp), tint = H.Coral)
                                    Spacer(Modifier.width(4.dp))
                                    Text("发起惦记", style = MaterialTheme.typography.titleSmall, color = H.Coral)
                                }
                            }
                        }
                    }
                }
            }

            item {
                EmptyState(
                    title = "和${peerName}互伴中",
                    subtitle = "回到看板就能给 TA 设惦记,或查看 TA 的惦记",
                )
            }
        }
    }

    // 解绑确认弹层:解绑即时隔离,不可逆,务必二次确认
    unbindTarget?.let { bond ->
        AlertDialog(
            onDismissRequest = { unbindTarget = null },
            containerColor = H.Canvas,
            shape = RoundedCornerShape(24.dp),
            title = { Text("与${peerName}解绑?", style = MaterialTheme.typography.titleLarge, color = H.Ink) },
            text = {
                Text(
                    "解绑后,双方立即看不到彼此的惦记与回执,且无法恢复。确定要解绑吗?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = H.Ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    unbindTarget = null
                    unbind(bond)
                }) { Text("确定解绑", color = H.Rose) }
            },
            dismissButton = {
                TextButton(onClick = { unbindTarget = null }) { Text("再想想", color = H.Ink2) }
            },
        )
    }
}
