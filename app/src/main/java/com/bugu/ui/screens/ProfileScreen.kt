package com.bugu.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bugu.client.api.BondDto
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.UserDto
import com.bugu.ui.common.BrandSwitch
import com.bugu.ui.common.RiseIn
import com.bugu.ui.common.TagChip
import com.bugu.ui.theme.H
import kotlinx.coroutines.launch

/**
 * 我的:真实账号信息(昵称/手机号/账号类型)+ 头像上传 + 互伴状态 + 大字关怀模式开关(缩放内容字号)。
 * 头像走系统照片选择器(无需存储权限),COS 签名直传后本地即时展示;
 * 惦记历史、好友互伴走路由;退出登录由 AppRoot 清理会话。无假会员与假设置。
 */
@Composable
fun ProfileScreen(
    api: GeitaApi,
    user: UserDto,
    bonds: List<BondDto>,
    onUserUpdated: (UserDto) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFriends: () -> Unit,
    onLogout: () -> Unit,
    careMode: Boolean,
    onCareModeChange: (Boolean) -> Unit,
    onSnack: (String) -> Unit,
) {
    val active = bonds.firstOrNull { it.status == "ACTIVE" }
    val peerName = active?.counterpartNickname ?: "家人"
    // 账号类型:NORMAL 手机号账号;ELDER 为免注册接入的长辈设备账号
    val accountLabel = if (user.accountType == "ELDER") "长辈设备账号" else "手机号账号"

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var detail by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var editingNickname by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var nickname by androidx.compose.runtime.saveable.rememberSaveable(user.id) { mutableStateOf(user.nickname) }
    var savingNickname by remember { mutableStateOf(false) }
    var nicknameError by remember { mutableStateOf<String?>(null) }

    // 服务端已有头像(重启后本地无缓存)时拉取展示;上传成功后本地位图优先生效
    LaunchedEffect(user.avatarUrl) {
        val path = user.avatarUrl ?: return@LaunchedEffect
        if (avatarBitmap != null) return@LaunchedEffect
        runCatching { api.downloadStatic(path) }
            .getOrNull()
            ?.let { raw -> avatarBitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap() }
    }

    // 系统照片选择器(photo picker):无需存储权限
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("图片读取失败")
                val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
                // 对象存储签名直传三步:申请凭证(服务端持密钥)→ 表单直传 COS → 确认落库
                val ticket = api.avatarUploadTicket(contentType)
                if (bytes.size > ticket.maxBytes) throw IllegalStateException("头像不能超过 2MB")
                api.uploadAvatarObject(ticket, contentType, bytes)
                val updated = api.confirmAvatarUpload(ticket.objectKey)
                avatarBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                onUserUpdated(updated)
                onSnack("头像已更新")
            } catch (e: Exception) {
                onSnack(e.message ?: "头像上传失败,稍后再试")
            } finally {
                uploading = false
            }
        }
    }

    detail?.let { page ->
        ProfileDetailPage(page, api, onBack = { detail = null }, onSnack = onSnack)
        return
    }
    if (editingNickname) {
        AlertDialog(
            onDismissRequest = { if (!savingNickname) editingNickname = false },
            title = { Text("修改昵称") },
            text = {
                Column {
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it; nicknameError = null },
                        enabled = !savingNickname, singleLine = true, label = { Text("昵称（1至32个字符）") },
                        isError = nickname.trim().length !in 1..32)
                    nicknameError?.let { Text(it, color = H.Rose) }
                }
            },
            confirmButton = {
                TextButton(enabled = !savingNickname && nickname.trim().length in 1..32, onClick = {
                    scope.launch {
                        savingNickname = true
                        try {
                            onUserUpdated(api.updateNickname(nickname.trim()))
                            editingNickname = false
                            onSnack("昵称已更新")
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { nicknameError = "昵称保存失败，请重试" }
                        finally { savingNickname = false }
                    }
                }) { Text(if (savingNickname) "保存中…" else "保存") }
            },
            dismissButton = { TextButton(enabled = !savingNickname, onClick = { editingNickname = false }) { Text("取消") } },
        )
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
        // 头部:头像 + 昵称 + 手机号
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditableAvatar(
                bitmap = avatarBitmap,
                fallback = user.nickname.take(1),
                uploading = uploading,
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.nickname, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = H.Ink)
                    TextButton(onClick = { nickname = user.nickname; editingNickname = true }) { Text("修改昵称", color = H.CoralInk) }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.phone?.replace(Regex("(\\d{3})\\d{4}(\\d{4})"), "$1****$2") ?: "未绑定手机号",
                        style = MaterialTheme.typography.bodySmall,
                        color = H.Ink2,
                    )
                    Spacer(Modifier.width(8.dp))
                    TagChip(
                        text = accountLabel,
                        tint = if (user.accountType == "ELDER") H.Indigo else H.Mint,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // 互伴状态卡:有互伴展示对端,无互伴引导绑定
        RiseIn {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .clickable(onClick = onOpenFriends)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(H.CoralTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, null, Modifier.size(20.dp), tint = H.Coral)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (active != null) {
                        Text("与${peerName}互伴中", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                        Text("惦记彼此可见,去管理绑定", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
                    } else {
                        Text("还没有互伴", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                        Text("绑定家人朋友,开始互相惦记", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
                    }
                }
                TagChip(text = if (active != null) "已互伴" else "去绑定", tint = H.Coral)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = H.Ink3)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("通用", style = MaterialTheme.typography.titleLarge, color = H.Ink)
        Spacer(Modifier.height(10.dp))

        // 我的惦记(历史回执)
        MenuRow(icon = Icons.Outlined.Alarm, title = "我的惦记", subtitle = "发出与收到的历史回执", onClick = onOpenHistory)

        // 大字关怀模式:纯本地视觉开关(字体与布局放大)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.TextFields, null, Modifier.size(20.dp), tint = H.Indigo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("大字关怀模式", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Text("更大的字号与按钮,看着更省心", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
            }
            BrandSwitch(checked = careMode, onCheckedChange = onCareModeChange)
        }

        Spacer(Modifier.height(12.dp))
        MenuRow(Icons.Outlined.Notifications, "权限与提醒诊断", "检查系统权限，发送本机测试通知") { detail = "权限与提醒诊断" }
        Spacer(Modifier.height(12.dp))
        MenuRow(Icons.Outlined.HelpOutline, "帮助与反馈", "常见问题、反馈进度与回复") { detail = "帮助与反馈" }
        Spacer(Modifier.height(12.dp))
        MenuRow(Icons.Outlined.Info, "关于布谷", "版本、用户协议与隐私政策") { detail = "关于布谷" }
        Spacer(Modifier.height(12.dp))

        // 退出登录
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .clickable(onClick = onLogout)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(20.dp), tint = H.Rose)
            Spacer(Modifier.width(12.dp))
            Text("退出登录", style = MaterialTheme.typography.titleMedium, color = H.Rose)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = H.Ink3)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "布谷 · 让每一份惦记都有回音",
            style = MaterialTheme.typography.titleSmall,
            color = H.Ink3,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** 菜单行:白卡 + 图标 + 标题/副标题 + 右箭头。 */
@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = H.Coral)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = H.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = H.Ink3)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = H.Ink3)
    }
}

/**
 * 可点击更换的头像:圆环 + 位图(无则首字回退),右下角相机角标提示可更换;
 * 上传中显示进度圈并禁点。视觉与全局 StatusAvatar 保持一致。
 */
@Composable
private fun EditableAvatar(bitmap: ImageBitmap?, fallback: String, uploading: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(60.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(2.dp, H.Coral.copy(alpha = 0.45f), CircleShape)
                .background(H.Hairline)
                .clickable(enabled = !uploading, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(fallback, style = MaterialTheme.typography.titleMedium, color = H.Ink2)
            }
            if (uploading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = H.Coral)
            }
        }
        // 相机角标:提示头像可点击更换
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(H.Coral),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(12.dp), tint = Color.White)
        }
    }
}
