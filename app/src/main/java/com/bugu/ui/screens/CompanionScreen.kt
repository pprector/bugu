package com.bugu.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Favorite
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugu.client.api.BondDto
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.client.api.ReminderView
import com.bugu.ui.common.BrandSwitch
import com.bugu.ui.common.CircleIcon
import com.bugu.ui.common.EmptyState
import com.bugu.ui.common.GradientButton
import com.bugu.ui.common.RiseIn
import com.bugu.ui.common.StatusAvatar
import com.bugu.ui.common.TagChip
import com.bugu.ui.theme.H
import kotlinx.coroutines.launch

/**
 * 互伴陪伴:对端关怀卡、代 TA 设长辈模式(仅绑定发起方可操作)、
 * TA 惦记我的最近回执、对 TA 发来的单次惦记做静音管理。
 * 静音与长辈模式均为服务端状态,操作后本地同步,失败回滚并提示。
 */
@Composable
fun CompanionScreen(
    api: GeitaApi,
    bonds: List<BondDto>,
    peerName: String,
    onOpenFriends: () -> Unit,
    onRefreshBonds: () -> Unit,
    onSnack: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val active = bonds.firstOrNull { it.status == "ACTIVE" }
    val peerId = active?.counterpartId

    // 长辈模式:初值取服务端视图,操作失败回滚
    var elderMode by remember { mutableStateOf<Boolean?>(null) } // null = 未拉取
    // 静音:TA 发来的单次惦记
    var muted by remember { mutableStateOf<Boolean?>(null) }
    // 最近回执:TA 发来、已有结果的惦记
    var receipts by remember { mutableStateOf<List<ReminderView>?>(null) }

    LaunchedEffect(active?.id) {
        if (peerId == null) return@LaunchedEffect
        if (elderMode == null) elderMode = active.counterpartElderMode == true
        try {
            receipts = api.incomingReminders()
                .sortedByDescending { it.fireAt }
                .take(3)
            muted = api.mutedList().any { it.creatorId == peerId && it.kind == "ONCE" }
        } catch (_: GeitaApiException) {
            receipts = emptyList()
            muted = false
        } catch (_: Exception) {
            receipts = emptyList()
            muted = false
        }
    }

    /** 代 TA 开关长辈模式:仅绑定发起方可操作,失败回滚。 */
    fun toggleElder(enabled: Boolean) {
        val pid = peerId ?: return
        val prev = elderMode ?: false
        elderMode = enabled
        scope.launch {
            try {
                api.setElderMode(pid, enabled)
                onSnack(if (enabled) "已为${peerName}开启长辈模式" else "已为${peerName}关闭长辈模式")
                onRefreshBonds()
            } catch (e: GeitaApiException) {
                elderMode = prev
                onSnack(e.message ?: "设置失败,请稍后再试")
            } catch (_: Exception) {
                elderMode = prev
                onSnack("网络不佳,请稍后再试")
            }
        }
    }

    /** 静音/恢复 TA 发来的单次惦记。 */
    fun toggleMute(toMute: Boolean) {
        val pid = peerId ?: return
        val prev = muted ?: false
        muted = toMute
        scope.launch {
            try {
                if (toMute) api.mute(pid, "ONCE") else api.unmute(pid, "ONCE")
                onSnack(if (toMute) "已静音${peerName}的单次惦记" else "已恢复${peerName}的单次惦记")
            } catch (e: GeitaApiException) {
                muted = prev
                onSnack(e.message ?: "设置失败,请稍后再试")
            } catch (_: Exception) {
                muted = prev
                onSnack("网络不佳,请稍后再试")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                bg = Color.White,
                tint = H.Ink,
                border = H.Hairline,
                modifier = Modifier.clickable(onClick = onOpenFriends),
            )
            Text(
                "互伴陪伴",
                style = MaterialTheme.typography.titleLarge,
                color = H.Ink,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.width(40.dp))
        }

        Spacer(Modifier.height(10.dp))

        if (active == null || peerId == null) {
            // 无互伴:引导去好友页绑定
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Favorite, null, Modifier.size(44.dp), tint = H.CoralLight)
                    Spacer(Modifier.height(10.dp))
                    Text("还没有互伴", style = MaterialTheme.typography.titleLarge, color = H.Ink)
                }
            }
            EmptyState(
                title = "先和 TA 完成绑定",
                subtitle = "互伴后,这里会长出陪伴与回执",
                actionText = "去绑定互伴",
                onAction = onOpenFriends,
            )
        } else {
            val isRequester = active.role == "REQUESTER"
            // 关怀卡:对端身份与关系
            RiseIn {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusAvatar(
                        avatar = null,
                        size = 54.dp,
                        ring = H.Coral.copy(alpha = 0.5f),
                        fallbackText = peerName.take(1),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(peerName, style = MaterialTheme.typography.titleLarge, color = H.Ink)
                            Spacer(Modifier.width(8.dp))
                            TagChip(text = active.counterpartRelationTag ?: "互伴家人", tint = H.Coral)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (isRequester) "你是这段互伴的发起人" else "${peerName}发起的互伴",
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3,
                        )
                    }
                    Icon(Icons.Outlined.Favorite, null, Modifier.size(20.dp), tint = H.CoralLight)
                }
            }

            Spacer(Modifier.height(14.dp))

            // 代 TA 设长辈模式(仅发起方可用)
            RiseIn(delayMs = 40) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("代 TA 设长辈模式", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                        Text(
                            if (isRequester) "大字极简看板,只留核心操作" else "仅由互伴发起方设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3,
                        )
                    }
                    if (isRequester) {
                        BrandSwitch(
                            checked = elderMode == true,
                            onCheckedChange = { toggleElder(it) },
                        )
                    } else {
                        TagChip(text = "TA 侧设置", tint = H.Ink3)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 静音管理:TA 发来的单次惦记
            RiseIn(delayMs = 80) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.AlarmOff, null, Modifier.size(18.dp), tint = H.Ink2)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("静音 TA 的单次惦记", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                        Text(
                            "开启后,${peerName}的单次提醒不再强提醒你",
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3,
                        )
                    }
                    when (muted) {
                        null -> CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else -> BrandSwitch(checked = muted == true, onCheckedChange = { toggleMute(it) })
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("最近回执", style = MaterialTheme.typography.titleLarge, color = H.Ink)
            Spacer(Modifier.height(10.dp))

            val list = receipts
            when {
                list == null -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    }
                }
                list.isEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White)
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("TA 还没给你设惦记", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                        Spacer(Modifier.height(4.dp))
                        Text("TA 的惦记送达后,回执会出现在这里", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
                    }
                }
                else -> {
                    list.forEachIndexed { i, r ->
                        RiseIn(delayMs = i * 50) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        r.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = H.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "由${peerName}发出 · ${parseIsoTime(r.fireAt)?.format(TIME_FMT) ?: r.fireAt}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = H.Ink3,
                                    )
                                }
                                TagChip(text = statusLabel(r.status), tint = statusColor(r.status))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            GradientButton(text = "去好友页管理互伴", onClick = onOpenFriends, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}
