package com.bugu.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bugu.ui.theme.H

/** 标准品牌白卡：白底 + 20dp 圆角 + 1dp hairline；可选点击态。 */
@Composable
fun BuguCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = Color.White,
    borderColor: Color = H.Hairline,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .then(clickModifier)
            .padding(contentPadding),
        content = content,
    )
}

enum class BuguButtonStyle { Primary, Secondary, Danger }

/** 品牌按钮：主操作渐变，次操作暖灰胶囊，危险操作玫瑰实底。 */
@Composable
fun BuguButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BuguButtonStyle = BuguButtonStyle.Primary,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    if (style == BuguButtonStyle.Primary) {
        GradientButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            height = height,
            enabled = enabled,
            loading = loading,
        )
        return
    }
    val background = if (style == BuguButtonStyle.Danger) H.RoseInk else H.WarmFill
    val foreground = if (style == BuguButtonStyle.Danger) Color.White else H.Ink2
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(background)
            .then(
                if (style == BuguButtonStyle.Secondary) Modifier.border(1.dp, H.Hairline, shape) else Modifier,
            )
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(role = Role.Button, enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    color = foreground,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = foreground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 品牌弹窗动作区：纵向排列，保证大字关怀模式下不挤压缩放。 */
@Composable
fun BuguDialogActions(
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: () -> Unit = {},
    confirmStyle: BuguButtonStyle = BuguButtonStyle.Primary,
    confirmEnabled: Boolean = true,
    loading: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dismissText != null) {
            BuguButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                style = BuguButtonStyle.Secondary,
                height = 46.dp,
                enabled = !loading,
            )
        }
        BuguButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            style = confirmStyle,
            height = 48.dp,
            enabled = confirmEnabled,
            loading = loading,
        )
    }
}

/**
 * 品牌弹窗：24dp 白卡 + hairline，可选咕咕与语义胶囊，44dp 关闭。
 * 不使用 Material3 AlertDialog，避免其固定 28dp 形状与未定义 surfaceContainer 回落。
 */
@Composable
fun BuguDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    mood: GuguMood? = null,
    tag: String? = null,
    tagTint: Color = H.Coral,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(1.dp, H.Hairline, RoundedCornerShape(24.dp))
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mood != null) {
                        GuguBird(
                            mood = mood,
                            modifier = Modifier.size(36.dp),
                            onTap = null,
                            pokeLines = emptyList(),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                color = H.Ink,
                            )
                            if (tag != null) {
                                Spacer(Modifier.width(6.dp))
                                TagChip(text = tag, tint = tagTint)
                            }
                        }
                        if (subtitle != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = H.Ink3Strong,
                            )
                        }
                    }
                    if (dismissible) {
                        Spacer(Modifier.width(8.dp))
                        HeaderIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            onClick = onDismissRequest,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                content()
                Spacer(Modifier.height(18.dp))
                actions()
            }
        }
    }
}

/** 品牌底部浮层：暖米底 + 24dp 顶角 + 拖拽条 + 咕咕页头 + 44dp 关闭。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuguSheet(
    onDismissRequest: () -> Unit,
    title: String,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    mood: GuguMood = GuguMood.CHEER,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = H.Canvas,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(H.Track),
            )
        },
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .then(contentModifier)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GuguBird(
                    mood = mood,
                    modifier = Modifier.size(32.dp),
                    onTap = null,
                    pokeLines = emptyList(),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = H.Ink,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3Strong,
                        )
                    }
                }
                HeaderIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    onClick = onDismissRequest,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** 品牌输入框：暖灰底 + 14dp 圆角，和登录/新建页一致，避免 Material 默认 4dp 小圆角。 */
@Composable
fun BuguTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        maxLines = maxLines,
        label = { Text(label, style = MaterialTheme.typography.titleSmall) },
        supportingText = supportingText?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) H.Rose else H.Ink3Strong,
                )
            }
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = H.Ink),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = H.WarmFill,
            unfocusedContainerColor = H.WarmFill,
            disabledContainerColor = H.GreyTint,
            focusedBorderColor = H.Coral,
            unfocusedBorderColor = H.Hairline,
            disabledBorderColor = H.Hairline,
            cursorColor = H.Coral,
            focusedLabelColor = H.CoralInk,
            unfocusedLabelColor = H.Ink3Strong,
            focusedTextColor = H.Ink,
            unfocusedTextColor = H.Ink,
            errorBorderColor = H.Rose,
            errorLabelColor = H.RoseInk,
        ),
    )
}
