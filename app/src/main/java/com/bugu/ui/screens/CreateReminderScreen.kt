@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.bugu.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bugu.client.api.BondDto
import com.bugu.client.api.displayName
import com.bugu.client.api.BatchCreateReminderRequest
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.ui.common.BrandSwitch
import com.bugu.ui.common.CircleIcon
import com.bugu.ui.common.FieldError
import com.bugu.ui.common.GradientButton
import com.bugu.ui.theme.H
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 与服务端 Reminder.MAX_TITLE / MAX_NOTE / MAX_PARSE_TEXT 严格对齐,避免本地放行后被 400 拒。 */
private const val MAX_TITLE = 20
private const val MAX_NOTE = 50
private const val MAX_PHRASE = 200

private val DAY_CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val DAY_CN = listOf("一", "二", "三", "四", "五", "六", "日")
private const val WORKDAY_CSV = "MON,TUE,WED,THU,FRI"

/** 快捷时刻网格(5 列);之外的钟点走「自定义时间…」时钟弹层。 */
private val TIME_PRESETS = listOf(
    "06:30", "07:00", "07:30", "08:00", "09:00",
    "10:00", "12:00", "13:00", "14:00", "15:00",
    "17:00", "18:00", "19:00", "20:00", "21:00",
)

/** 打字实时识别:检测到时间相关词即提示「点发送自动填表」。 */
private val TIME_HINT_REGEX = Regex(
    """\d{1,2}[:：]\d{2}|\d{1,2}点(半)?|今天|今晚|今早|明天|明早|明晚|后天|早上|早晨|上午|中午|下午|晚上|傍晚|每天|天天""",
)

/**
 * 新建惦记(v4 范式:常驻表单 + AI 自动填写)。
 *
 * 布局:表单常驻在屏上半部(手动零成本),智能条吸底在拇指区——
 * 说话 / 打字 / 粘贴通知文本任一输入都汇入 api.parse,解析结果逐字段
 * 自动填入表单(珊瑚闪光),哪里不对改哪里;未来新增输入源只动智能条。
 *
 * 表单状态全部 rememberSaveable,配合 AppRoot 的 SaveableStateHolder:
 * 切 Tab、去权限页再返回、进程回收后回来都能继续填。
 */
@Composable
fun CreateReminderScreen(
    api: GeitaApi,
    userId: Long,
    bonds: List<BondDto>,
    bondsLoading: Boolean,
    bondsError: Boolean,
    needsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onRefreshBonds: () -> Unit,
    onSnack: (String) -> Unit,
    onCreated: () -> Unit,
    preselectedBondId: Long? = null,
    createRequest: Int = 0,
    onPreselectionConsumed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // ---- 接收人:自己和 ACTIVE 绑定可多选;派生选中避免异步竞态 ----
    val activeBonds = remember(bonds) { bonds.filter { it.status == "ACTIVE" && it.counterpartId != null } }
    var selectedRecipientIds by rememberSaveable { mutableStateOf(listOf(userId)) }
    LaunchedEffect(createRequest, userId) {
        if (preselectedBondId != null) {
            activeBonds.firstOrNull { it.id == preselectedBondId }?.counterpartId?.let { counterpartId ->
                selectedRecipientIds = listOf(counterpartId)
            }
        }
        onPreselectionConsumed()
    }
    val selectedRecipientIdSet = selectedRecipientIds.toSet()
    val selectedBonds = activeBonds.filter { it.counterpartId in selectedRecipientIdSet }
    val invalidRecipient = selectedRecipientIds.any { recipientId ->
        recipientId != userId && activeBonds.none { it.counterpartId == recipientId }
    }
    val selfSelected = userId in selectedRecipientIdSet
    val singleBondRecipient = selectedBonds.singleOrNull()?.counterpartId
    val mutualAlarmAvailable = selectedRecipientIds.size == 1 && singleBondRecipient != null
    val receiverNames = buildList {
        if (selfSelected) add("你")
        addAll(selectedBonds.map { it.displayName })
    }
    val receiverName = receiverNames.joinToString("、").ifEmpty { "亲友" }

    // ---- 可保存的表单状态(切页面/进程回收不丢) ----
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toEpochDay()) }
    var hour by rememberSaveable { mutableStateOf(8) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var kind by rememberSaveable { mutableStateOf("ONCE") }
    var tone by rememberSaveable { mutableStateOf("GENTLE") }
    // 重复模式:DAILY / WEEKDAY / CUSTOM,配合自定义星期集合
    var repeatMode by rememberSaveable { mutableStateOf("DAILY") }
    var customDaysCsv by rememberSaveable { mutableStateOf(WORKDAY_CSV) }
    var voiceEnabled by rememberSaveable { mutableStateOf(false) }

    // 智能条与解析结果
    var smartText by rememberSaveable { mutableStateOf("") }
    var parsedOk by rememberSaveable { mutableStateOf(false) }
    var degradedParse by rememberSaveable { mutableStateOf(false) }
    var phraseError by rememberSaveable { mutableStateOf("") }
    var titleError by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf("") }

    // 瞬时状态(不跨进程)
    var parsing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // 逐字段填入闪光触发器(>0 触发一次闪光动画)
    var titleTick by remember { mutableStateOf(0) }
    var timeTick by remember { mutableStateOf(0) }
    var noteTick by remember { mutableStateOf(0) }

    // 每 30s 推进一次时钟,让「时间已过」判断与会话保持同步
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val fireAt = LocalDateTime.of(date, LocalTime.of(hour, minute))
    // 本地预校验:服务端要求 fireAt 在未来,避免提交后才收到 400
    val isPast = !fireAt.isAfter(now)
    val busy = parsing || creating
    val selectedDays = customDaysCsv.split(",").filter { it.isNotBlank() }.toSet()
    val repeatRule = when {
        kind != "REPEAT" -> null
        repeatMode == "DAILY" -> "DAILY"
        repeatMode == "WEEKDAY" -> "WEEKLY:$WORKDAY_CSV"
        else -> "WEEKLY:" + DAY_CODES.filter { it in selectedDays }.joinToString(",")
    }
    val canSubmit = !busy && title.isNotBlank() && selectedRecipientIds.isNotEmpty() && !invalidRecipient

    // 系统返回键:回看板而不是退出 App(与左上角箭头一致)
    BackHandler { onBack() }

    fun clearErrors() {
        phraseError = ""
        titleError = ""
        formError = ""
    }

    /** 选择提醒类型:非互叫类型必须关掉语音;多人或自己参与时不能使用互叫闹钟。 */
    fun selectKind(next: String) {
        kind = if (next == "MUTUAL_ALARM" && !mutualAlarmAvailable) "ONCE" else next
        if (kind != "MUTUAL_ALARM") voiceEnabled = false
        formError = ""
    }

    fun toggleRecipient(recipientId: Long) {
        if (busy) return
        selectedRecipientIds = when {
            recipientId in selectedRecipientIds -> selectedRecipientIds - recipientId
            selectedRecipientIds.size >= 20 -> {
                onSnack("最多选择 20 位接收人")
                selectedRecipientIds
            }
            else -> selectedRecipientIds + recipientId
        }
        if (kind == "MUTUAL_ALARM" &&
            (selectedRecipientIds.size != 1 || userId in selectedRecipientIds)) {
            kind = "ONCE"
            voiceEnabled = false
        }
        formError = ""
    }

    fun toggleDay(code: String) {
        val next = selectedDays.toMutableSet().apply { if (code in this) remove(code) else add(code) }
        customDaysCsv = DAY_CODES.filter { it in next }.joinToString(",")
        repeatMode = "CUSTOM"
        formError = ""
    }

    fun applyRepeatRule(rule: String?) {
        when {
            rule == null || rule == "DAILY" -> repeatMode = "DAILY"
            rule.startsWith("WEEKLY:") -> {
                val days = rule.removePrefix("WEEKLY:").split(",")
                    .map { it.trim().uppercase() }
                    .filter { it in DAY_CODES }
                    .toSet()
                if (days.isNotEmpty()) customDaysCsv = DAY_CODES.filter { it in days }.joinToString(",")
                repeatMode = if (customDaysCsv == WORKDAY_CSV) "WEEKDAY" else "CUSTOM"
            }
        }
    }

    /** 智能条提交:说话/打字/粘贴统一走 api.parse,结果逐字段自动填入并闪光。 */
    fun doParse() {
        val text = smartText.trim()
        if (text.isEmpty() || parsing) return
        focusManager.clearFocus()
        scope.launch {
            parsing = true
            try {
                val draft = api.parse(text.take(MAX_PHRASE))
                if (draft.needsManual) {
                    parsedOk = false
                    degradedParse = draft.degraded
                    if (title.isBlank()) {
                        title = text.take(MAX_TITLE)
                        titleTick++
                    }
                    phraseError = "咕咕没太听懂,标题和时间帮它补一下"
                } else {
                    smartText = ""
                    phraseError = ""
                    // 逐字段填入:标题 → 时刻·方式 → 便签,每步珊瑚闪光,像咕咕逐项落笔
                    draft.title?.takeIf { it.isNotBlank() && it != title.trim() }?.let {
                        title = it.take(MAX_TITLE)
                        titleError = ""
                        titleTick++
                        delay(170)
                    }
                    var timeChanged = false
                    parseIsoTime(draft.fireAt ?: "")?.let {
                        dateEpochDay = it.toLocalDate().toEpochDay()
                        hour = it.hour
                        minute = it.minute
                        timeChanged = true
                    }
                    if (draft.kind == "REPEAT" || draft.kind == "ONCE" || draft.kind == "MUTUAL_ALARM") {
                        if (draft.kind != kind) {
                            val before = kind
                            selectKind(draft.kind)
                            if (kind != before) timeChanged = true
                        }
                        if (draft.kind == "REPEAT") applyRepeatRule(draft.repeatRule)
                    }
                    if (timeChanged) {
                        formError = ""
                        timeTick++
                        delay(170)
                    }
                    draft.note?.takeIf { it.isNotBlank() && it != note.trim() }?.let {
                        note = it.take(MAX_NOTE)
                        noteTick++
                    }
                    parsedOk = true
                    degradedParse = draft.degraded
                }
            } catch (e: GeitaApiException) {
                parsedOk = false
                if (title.isBlank()) {
                    title = text.take(MAX_TITLE)
                    titleTick++
                }
                phraseError = e.message ?: "解析失败,手动填一下就好"
            } catch (_: Exception) {
                parsedOk = false
                if (title.isBlank()) {
                    title = text.take(MAX_TITLE)
                    titleTick++
                }
                phraseError = "网络不佳,先手动填一下吧"
            } finally {
                parsing = false
            }
        }
    }

    // 语音识别结果回填智能条并自动解析
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val recognized = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (recognized.isNullOrBlank()) {
            phraseError = "没听清,再试一次或直接打字"
        } else {
            smartText = recognized.take(MAX_PHRASE)
            doParse()
        }
    }

    // 语音可用性:不可用的设备(部分模拟器/无语音引擎)话筒点击给明确反馈,不再「没反应」
    val voiceAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    /** 语音输入:系统语音识别兜底(无语音服务的设备给明确提示改打字)。 */
    fun startVoice() {
        if (parsing) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说说要惦记的事")
        }
        when {
            !voiceAvailable || intent.resolveActivity(context.packageManager) == null ->
                onSnack("这台设备没有语音识别服务,打字也一样快")
            else -> try {
                voiceLauncher.launch(intent)
            } catch (_: Exception) {
                onSnack("语音识别没能启动,打字也一样快")
            }
        }
    }

    fun resetForm() {
        title = ""
        note = ""
        smartText = ""
        parsedOk = false
        degradedParse = false
        clearErrors()
        kind = "ONCE"
        tone = "GENTLE"
        repeatMode = "DAILY"
        customDaysCsv = WORKDAY_CSV
        voiceEnabled = false
        dateEpochDay = LocalDate.now().plusDays(1).toEpochDay()
        hour = 8
        minute = 0
    }

    /** 创建:先做本地校验(与 Server validate 对齐),再直连 API。 */
    fun doCreate() {
        if (creating) return
        clearErrors()
        val receiverIds = selectedRecipientIds.toList()
        when {
            title.isBlank() -> {
                titleError = "给惦记起个名字吧"
                return
            }
            title.length > MAX_TITLE -> {
                titleError = "标题不超过 $MAX_TITLE 字"
                return
            }
            note.length > MAX_NOTE -> {
                formError = "便签不超过 $MAX_NOTE 字"
                return
            }
            isPast -> {
                formError = "这个时刻已经过去了,换个更晚的时间吧"
                return
            }
            kind == "REPEAT" && repeatMode == "CUSTOM" && selectedDays.isEmpty() -> {
                formError = "循环至少选一天"
                return
            }
            kind == "MUTUAL_ALARM" && !mutualAlarmAvailable -> {
                formError = "互叫闹钟只能选择一位亲友"
                return
            }
            receiverIds.isEmpty() -> {
                formError = "至少选择一位接收人"
                return
            }
            receiverIds.size > 20 -> {
                formError = "最多选择 20 位接收人"
                return
            }
            invalidRecipient -> {
                formError = "部分亲友关联已失效，请重新选择"
                return
            }
        }
        scope.launch {
            creating = true
            try {
                api.createRemindersBatch(
                    BatchCreateReminderRequest(
                        receiverIds = receiverIds,
                        title = title.trim(),
                        kind = kind,
                        fireAt = "%sT%02d:%02d:00".format(date, hour, minute),
                        repeatRule = repeatRule,
                        tone = tone,
                        voiceEnabled = voiceEnabled,
                        note = note.trim().takeIf { it.isNotEmpty() },
                    ),
                )
                onSnack(
                    if (receiverIds.size > 1) "已为 ${receiverIds.size} 人分别设好惦记"
                    else if (selfSelected) "已设好惦记,到点咕咕会准时唤你"
                    else "已设好惦记,到点咕咕会准时唤$receiverName",
                )
                resetForm()
                onCreated()
            } catch (e: GeitaApiException) {
                formError = e.message ?: "创建失败,请稍后再试"
            } catch (_: Exception) {
                formError = "网络不佳,请稍后再试"
            } finally {
                creating = false
            }
        }
    }

    // 页面骨架:常驻表单在上,吸底智能条贴键盘;本屏嵌在底部导航之上的内容区,导航栏内边距由外层负责
    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIcon(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                bg = Color.White,
                tint = H.Ink,
                border = H.Hairline,
                size = 44.dp,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text(
                "新建惦记",
                style = MaterialTheme.typography.titleLarge,
                color = H.Ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(44.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Text("把惦记送给谁", style = MaterialTheme.typography.titleMedium, color = H.Ink)
            Text("可多选 · 已选 ${selectedRecipientIds.size} 人", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
            if (invalidRecipient) Text("部分亲友关联已失效，请取消后重新选择", color = H.Rose)
            WhoRow(
                bonds = activeBonds,
                selectedRecipientIds = selectedRecipientIdSet,
                selfSelected = selfSelected,
                loading = bondsLoading,
                failed = bondsError,
                onSelectSelf = { toggleRecipient(userId) },
                onSelectBond = { toggleRecipient(it) },
                onGoBind = onOpenFriends,
                onRetry = onRefreshBonds,
            )

            Spacer(Modifier.height(12.dp))

            // ② 常驻表单:标题 / 时刻·方式 / 捎句话 / 语气
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White),
            ) {
                FlashRow(tick = titleTick) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircleIcon(icon = Icons.Rounded.EditNote, bg = H.CoralTint, tint = H.Coral, size = 32.dp, iconSize = 16.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("惦记标题", style = MaterialTheme.typography.titleSmall, color = H.Ink3)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${title.length}/$MAX_TITLE",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (title.length >= MAX_TITLE) H.Rose else H.Ink3,
                                )
                            }
                            BasicTextField(
                                value = title,
                                onValueChange = {
                                    title = it.take(MAX_TITLE)
                                    titleError = ""
                                    formError = ""
                                },
                                textStyle = MaterialTheme.typography.titleMedium.copy(color = H.Ink),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                            ) { inner ->
                                Box {
                                    if (title.isEmpty()) {
                                        Text("如:吃降压药 / 早安叫醒", style = MaterialTheme.typography.titleMedium, color = H.Ink3)
                                    }
                                    inner()
                                }
                            }
                            FieldError(titleError.takeIf { it.isNotEmpty() }, Modifier.padding(top = 4.dp))
                        }
                    }
                }

                HorizontalDivider(color = H.Hairline, thickness = 1.dp)

                FlashRow(tick = timeTick) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showSheet = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircleIcon(icon = Icons.Rounded.Schedule, bg = H.CoralTint, tint = H.Coral, size = 32.dp, iconSize = 16.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("时刻 · 方式", style = MaterialTheme.typography.titleSmall, color = H.Ink3)
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    timeValueLabel(date, hour, minute, kind, repeatMode, customDaysCsv, tone),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = H.CoralInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.Rounded.Edit, null, Modifier.size(13.dp), tint = H.Ink3)
                        }
                        if (isPast) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Warning, null, Modifier.size(13.dp), tint = H.Rose)
                                Spacer(Modifier.width(4.dp))
                                Text("这个时刻已经过去了", style = MaterialTheme.typography.titleSmall, color = H.Rose)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "改到明天",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = H.CoralInk,
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .clickable {
                                            dateEpochDay = LocalDate.now().plusDays(1).toEpochDay()
                                            formError = ""
                                        }
                                        .padding(horizontal = 4.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = H.Hairline, thickness = 1.dp)

                FlashRow(tick = noteTick) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircleIcon(icon = Icons.AutoMirrored.Rounded.Chat, bg = H.MintTint, tint = H.Mint, size = 32.dp, iconSize = 16.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("捎句话(可选)", style = MaterialTheme.typography.titleSmall, color = H.Ink3)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${note.length}/$MAX_NOTE",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (note.length >= MAX_NOTE) H.Rose else H.Ink3,
                                )
                            }
                            BasicTextField(
                                value = note,
                                onValueChange = {
                                    note = it.take(MAX_NOTE)
                                    formError = ""
                                },
                                textStyle = MaterialTheme.typography.titleMedium.copy(color = H.Ink),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                            ) { inner ->
                                Box {
                                    if (note.isEmpty()) {
                                        Text("写一句暖心的惦记…", style = MaterialTheme.typography.titleMedium, color = H.Ink3)
                                    }
                                    inner()
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = H.Hairline, thickness = 1.dp)
            }

            // AI 填好提示:解析成功后出现在表单下方(如实区分降级)
            AnimatedVisibility(visible = parsedOk) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(14.dp), tint = H.Mint)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (degradedParse) "咕咕按本地规则填好了,检查一下再用"
                        else "咕咕已帮你填好,不满意就改",
                        style = MaterialTheme.typography.titleSmall,
                        color = H.MintInk,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // ③ 固定操作区:按钮不随表单滚动,键盘出现时随 imePadding 一起上移
        Column(
            Modifier
                .fillMaxWidth()
                .background(H.Canvas)
                .padding(horizontal = 20.dp),
        ) {
            GradientButton(
                text = when {
                    creating -> "创建中…"
                    needsPermission -> "开启提醒权限并创建"
                    title.isBlank() -> "填写标题后创建"
                    else -> "创建惦记"
                },
                onClick = { if (needsPermission) onRequestPermission() else doCreate() },
                enabled = canSubmit,
                loading = creating,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Alarm, null, Modifier.size(13.dp), tint = H.Ink3)
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        selfSelected -> "送给自己,到点提醒你自己"
                        needsPermission -> "首次创建需开启提醒权限,仅需一次"
                        else -> "创建后可在看板随时撤销"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = H.Ink3,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ④ 吸底智能条:说话 / 打字 / 粘贴统一入口,imePadding 贴键盘
        Column(
            Modifier
                .fillMaxWidth()
                .background(H.Canvas)
                .padding(horizontal = 16.dp),
        ) {
            if (parsing) {
                Row(
                    Modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = H.Coral, strokeWidth = 2.dp, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("咕咕在想…", style = MaterialTheme.typography.titleSmall, color = H.CoralInk)
                }
            } else if (smartText.trim().length > 3 && TIME_HINT_REGEX.containsMatchIn(smartText)) {
                Row(
                    Modifier
                        .padding(top = 8.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(H.CoralTint)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "✨ 识别到时间,点发送帮你自动填表",
                        style = MaterialTheme.typography.titleSmall,
                        color = H.CoralInk,
                    )
                }
            }
            FieldError(phraseError.takeIf { it.isNotEmpty() }, Modifier.padding(top = 6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .border(1.dp, H.Hairline, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 话筒:系统语音识别,识别结果回填并自动解析;无语音服务的设备点击给明确反馈
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (voiceAvailable) listOf(H.CoralLight, H.Coral)
                                else listOf(H.GreyTint, H.GreyTint),
                            ),
                        )
                        .clickable { startVoice() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Mic, "说话", Modifier.size(19.dp), tint = Color.White)
                }
                BasicTextField(
                    value = smartText,
                    onValueChange = {
                        smartText = it.take(MAX_PHRASE)
                        phraseError = ""
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = H.Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { doParse() }),
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                ) { inner ->
                    Box {
                        if (smartText.isEmpty()) {
                            Text("如:明早七点半提醒${receiverName}吃降压药", style = MaterialTheme.typography.bodyMedium, color = H.Ink3, maxLines = 1)
                        }
                        inner()
                    }
                }
                // 发送:解析中转圈,空文本置灰
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (smartText.isNotBlank() && !parsing) H.CoralTint else H.GreyTint)
                        .clickable(enabled = smartText.isNotBlank() && !parsing) { doParse() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        "发送",
                        Modifier.size(18.dp),
                        tint = if (smartText.isNotBlank() && !parsing) H.Coral else H.Ink3,
                    )
                }
            }
        }
    }

    // 时间·方式弹层:日期 chips + 时刻网格 + 方式三选,循环/语音叫醒细节就地展开
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = H.Canvas,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    "时刻与方式",
                    style = MaterialTheme.typography.titleLarge,
                    color = H.Ink,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                SheetLabel("日期")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val today = LocalDate.now()
                    (0..6).forEach { offset ->
                        val d = today.plusDays(offset.toLong())
                        ChoiceChip(
                            text = if (offset <= 2) listOf("今天", "明天", "后天")[offset] else dayChipLabel(d),
                            selected = d == date,
                            onClick = {
                                dateEpochDay = d.toEpochDay()
                                formError = ""
                            },
                        )
                    }
                    ChoiceChip(
                        text = "其他日期…",
                        selected = date.toEpochDay() !in today.toEpochDay()..today.plusDays(6).toEpochDay(),
                        onClick = { showDatePicker = true },
                    )
                }

                Spacer(Modifier.height(14.dp))
                SheetLabel("时间")
                TIME_PRESETS.chunked(5).forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { preset ->
                            val parts = preset.split(":")
                            val h = parts[0].toInt()
                            val m = parts[1].toInt()
                            GridCell(
                                text = preset,
                                selected = hour == h && minute == m,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hour = h
                                    minute = m
                                    formError = ""
                                },
                            )
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val presetHit = "%02d:%02d".format(hour, minute) in TIME_PRESETS
                    GridCell(
                        text = "自定义时间…",
                        selected = !presetHit,
                        modifier = Modifier.weight(1f),
                        onClick = { showTimePicker = true },
                    )
                    repeat(4) { Spacer(Modifier.weight(1f)) }
                }

                Spacer(Modifier.height(14.dp))
                SheetLabel("方式")
                KindOption(
                    icon = Icons.Rounded.Alarm,
                    title = "单次",
                    desc = "到点呼唤一次,适合临时安排",
                    selected = kind == "ONCE",
                    onClick = { selectKind("ONCE") },
                )
                KindOption(
                    icon = Icons.Rounded.EventRepeat,
                    title = "循环",
                    desc = "按规则每天/工作日/自选星期到点呼唤",
                    selected = kind == "REPEAT",
                    onClick = { selectKind("REPEAT") },
                )
                // 互叫闹钟只适用于一位亲友
                if (mutualAlarmAvailable) {
                    KindOption(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "互叫闹钟",
                        desc = "到点双方同时响铃,谁先按停都算完成",
                        selected = kind == "MUTUAL_ALARM",
                        onClick = { selectKind("MUTUAL_ALARM") },
                    )
                }
                if (kind == "REPEAT") {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("每天", repeatMode == "DAILY") { repeatMode = "DAILY"; formError = "" }
                        ChoiceChip("工作日", repeatMode == "WEEKDAY") { repeatMode = "WEEKDAY"; formError = "" }
                        ChoiceChip("自定义", repeatMode == "CUSTOM") { repeatMode = "CUSTOM"; formError = "" }
                    }
                    if (repeatMode == "CUSTOM") {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DAY_CODES.forEachIndexed { index, code ->
                                val on = code in selectedDays
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (on) H.Coral else H.WarmFill)
                                        .selectable(selected = on, role = Role.Checkbox, onClick = { toggleDay(code) }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        DAY_CN[index],
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (on) Color.White else H.Ink2,
                                    )
                                }
                            }
                        }
                        FieldError(
                            "循环至少选一天".takeIf { selectedDays.isEmpty() },
                            Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (kind == "MUTUAL_ALARM") {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White)
                            .border(1.dp, H.Coral, RoundedCornerShape(15.dp))
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Mic, null, Modifier.size(16.dp), tint = H.Coral)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("专属语音叫醒", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                            Text("到点播报咕咕语音,更有真人感", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
                        }
                        BrandSwitch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it })
                    }
                }

                // 语气:非关键信息收进弹层,表单卡保持三行紧凑
                Spacer(Modifier.height(14.dp))
                SheetLabel("语气")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("GENTLE" to "轻柔", "NAG" to "唠叨", "STRICT" to "严格").forEach { (value, label) ->
                        GridCell(
                            text = label,
                            selected = tone == value,
                            modifier = Modifier.weight(1f),
                            onClick = { tone = value; clearErrors() },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                GradientButton(
                    text = "就这个",
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                )
            }
        }
    }

    // 时钟弹层(24 小时制):快捷网格之外的任意时刻
    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(H.Canvas)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("选择呼唤时刻", style = MaterialTheme.typography.titleLarge, color = H.Ink)
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showTimePicker = false }) { Text("取消", color = H.Ink2) }
                    TextButton(
                        onClick = {
                            hour = state.hour
                            minute = state.minute
                            formError = ""
                            showTimePicker = false
                        },
                    ) { Text("确定", color = H.CoralInk) }
                }
            }
        }
    }

    // 日期选择器:只允许今天及以后(服务端要求 fireAt 在未来)
    if (showDatePicker) {
        val todayUtcMillis = LocalDate.now().toEpochDay() * 86_400_000L
        val selectableDates = remember(todayUtcMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
                override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
            }
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateEpochDay * 86_400_000L,
            selectableDates = selectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        formError = ""
                        showDatePicker = false
                    },
                ) { Text("确定", color = H.CoralInk) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消", color = H.Ink2) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** 送给行:「自己」默认选中 + 好友横滑选择(好友多时右缘渐隐示意可滑);加载/失败/未绑定仅在好友区轻提示。 */
@Composable
private fun WhoRow(
    bonds: List<BondDto>,
    selectedRecipientIds: Set<Long>,
    selfSelected: Boolean,
    loading: Boolean,
    failed: Boolean,
    onSelectSelf: () -> Unit,
    onSelectBond: (Long) -> Unit,
    onGoBind: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("送给", style = MaterialTheme.typography.titleSmall, color = H.Ink3)
        Spacer(Modifier.width(10.dp))
        // 自己 chip 恒在最前
        PersonChip(
            name = "自己",
            avatarText = "自",
            selected = selfSelected,
            onClick = onSelectSelf,
        )
        when {
            bonds.isNotEmpty() -> Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    bonds.forEach { bond ->
                        val name = bond.displayName
                        PersonChip(
                            name = name,
                            avatarText = name.take(1),
                            selected = bond.counterpartId in selectedRecipientIds,
                            onClick = { bond.counterpartId?.let(onSelectBond) },
                        )
                    }
                }
                // 右缘渐隐:示意可以横滑
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(24.dp)
                        .height(36.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, H.Canvas),
                            ),
                        ),
                )
            }
            loading -> Text(
                "正在检查互伴…",
                style = MaterialTheme.typography.titleSmall,
                color = H.Ink3,
                modifier = Modifier.padding(start = 8.dp),
            )
            failed -> {
                Text(
                    "绑定状态获取失败",
                    style = MaterialTheme.typography.titleSmall,
                    color = H.Rose,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                MiniAction("重试", onRetry, H.Ink2)
            }
            else -> {
                Text(
                    "绑定互伴,把惦记送给 TA",
                    style = MaterialTheme.typography.titleSmall,
                    color = H.Ink3,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                MiniAction("去绑定", onGoBind, H.CoralInk)
            }
        }
    }
}

/** 圆角人物胶囊:头像圆 + 昵称;选中珊瑚底白字,触摸目标 ≥44dp。 */
@Composable
private fun PersonChip(name: String, avatarText: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) H.CoralTint else Color.White)
            .border(1.dp, if (selected) H.Coral else H.Hairline, RoundedCornerShape(999.dp))
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(start = 5.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (selected) H.Coral else H.WarmFill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                avatarText,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) Color.White else H.Ink2,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) H.CoralInk else H.Ink2,
            maxLines = 1,
        )
    }
}

/** 表单行闪光容器:解析结果填入时整行珊瑚底淡出 + 轻微 pop,指示「这个字段被咕咕改了」。 */
@Composable
private fun FlashRow(tick: Int, content: @Composable ColumnScope.() -> Unit) {
    val flash = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(tick) {
        if (tick > 0) {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing))
            pop.snapTo(0.97f)
            pop.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .background(H.Coral.copy(alpha = 0.12f * (1f - flash.value))),
        content = content,
    )
}

/** 白底描边网格单元:时刻网格与语气三选用,选中珊瑚实底。 */
@Composable
private fun GridCell(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) H.Coral else Color.White)
            .border(1.dp, if (selected) H.Coral else H.Hairline, RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Color.White else H.Ink,
            maxLines = 1,
        )
    }
}

/** 方式选项卡:图标 + 标题 + 说明,选中珊瑚描边底色。 */
@Composable
private fun KindOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) H.CoralTint else Color.White)
            .border(1.dp, if (selected) H.Coral else H.Hairline, RoundedCornerShape(15.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (selected) Color.White else H.WarmFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = H.Coral)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = H.Ink)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = H.Ink3)
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = H.Coral)
        }
    }
}

/** 弹层分组标签。 */
@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = H.Ink3)
    Spacer(Modifier.height(8.dp))
}

/** 圆角选择胶囊:选中珊瑚底白字,未选中暖灰底;触摸目标 ≥44dp。 */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) H.Coral else H.WarmFill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Color.White else H.Ink2,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 次要胶囊动作(去绑定/重试),与主按钮区分但保持 44dp 触摸目标。 */
@Composable
private fun MiniAction(text: String, onClick: () -> Unit, color: Color) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

// ---- 文案/日期小工具 ----

private fun dayChipLabel(d: LocalDate): String {
    val offset = d.toEpochDay() - LocalDate.now().toEpochDay()
    return if (offset in 3..6) {
        "周" + "一二三四五六日"[d.dayOfWeek.value - 1]
    } else {
        "%d月%d日".format(d.monthValue, d.dayOfMonth)
    }
}

/** 时刻·方式行的回显文案:如「明天 08:00 · 循环·工作日 · 轻柔」。 */
private fun timeValueLabel(
    date: LocalDate,
    hour: Int,
    minute: Int,
    kind: String,
    repeatMode: String,
    customDaysCsv: String,
    tone: String,
): String {
    val dateLabel = when (date.toEpochDay() - LocalDate.now().toEpochDay()) {
        0L -> "今天"
        1L -> "明天"
        2L -> "后天"
        else -> "%d月%d日".format(date.monthValue, date.dayOfMonth)
    }
    val kindLabel = when (kind) {
        "REPEAT" -> "循环·" + when (repeatMode) {
            "DAILY" -> "每天"
            "WEEKDAY" -> "工作日"
            else -> {
                val codes = DAY_CODES.filter { it in customDaysCsv.split(",").filter { c -> c.isNotBlank() }.toSet() }
                if (codes.isEmpty()) "每周(未选)" else "周" + codes.map { DAY_CN[DAY_CODES.indexOf(it)] }.joinToString("")
            }
        }
        "MUTUAL_ALARM" -> "互叫闹钟"
        else -> "单次"
    }
    val toneLabel = when (tone) {
        "NAG" -> "唠叨"
        "STRICT" -> "严格"
        else -> "轻柔"
    }
    return "$dateLabel %02d:%02d · %s · %s".format(hour, minute, kindLabel, toneLabel)
}
