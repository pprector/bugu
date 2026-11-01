@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.bugu.ui.screens

import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugu.client.api.BondDto
import com.bugu.client.api.displayName
import com.bugu.client.api.BatchCreateReminderRequest
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.ui.common.AvatarBubble
import com.bugu.ui.common.BrandSwitch
import com.bugu.ui.common.FieldError
import com.bugu.ui.common.GradientButton
import com.bugu.ui.common.GuguMood
import com.bugu.ui.common.HeaderIconButton
import com.bugu.ui.common.RiseIn
import com.bugu.ui.common.TopBar
import com.bugu.ui.common.parseIsoTime
import com.bugu.ui.theme.H
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 与服务端 Reminder.MAX_TITLE / MAX_NOTE / MAX_PARSE_TEXT 严格对齐,避免本地放行后被 400 拒。 */
private const val MAX_TITLE = 20
private const val MAX_NOTE = 50
private const val MAX_PHRASE = 200

private val DAY_CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val DAY_CN = listOf("一", "二", "三", "四", "五", "六", "日")
private const val WORKDAY_CSV = "MON,TUE,WED,THU,FRI"


@Composable
fun CreateReminderScreen(
    api: GeitaApi,
    userId: Long,
    selfAvatarUrl: String? = null,
    selfNickname: String? = null,
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
    var title by rememberSaveable { mutableStateOf("提醒自己") }
    // 用户是否手动改过标题:未改时随接收人联动默认文案
    var titleDirty by rememberSaveable { mutableStateOf(false) }
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

    // 标题默认:未手动编辑时按接收人联动,给出可读的初始标题
    val defaultTitle = when {
        selectedRecipientIds.size == 1 && selfSelected -> "提醒自己"
        selectedRecipientIds.size == 1 -> "提醒" + (selectedBonds.firstOrNull()?.displayName ?: "亲友")
        else -> "提醒 ${selectedRecipientIds.size} 人"
    }
    LaunchedEffect(defaultTitle) {
        if (!titleDirty) title = defaultTitle
    }

    // 智能条与解析结果
    var smartText by rememberSaveable { mutableStateOf("") }
    var parsedOk by rememberSaveable { mutableStateOf(false) }
    var degradedParse by rememberSaveable { mutableStateOf(false) }
    var phraseError by rememberSaveable { mutableStateOf("") }
    var titleError by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf("") }

    fun clearErrors() {
        phraseError = ""
        titleError = ""
        formError = ""
    }

    // 草稿:本地 SharedPreferences 持久化,保存/恢复表单快照
    var showDraftDialog by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("bugu_main", Context.MODE_PRIVATE) }
    // 用状态镜像草稿是否存在:保存后立即出现恢复入口,且避免每次重组同步读盘
    var draftExists by remember { mutableStateOf(prefs.getString("draft_reminder", null)?.isNotBlank() == true) }
    fun saveDraft() {
        val json = JSONObject().apply {
            put("title", title)
            put("note", note)
            put("dateEpochDay", dateEpochDay)
            put("hour", hour)
            put("minute", minute)
            put("kind", kind)
            put("tone", tone)
            put("repeatMode", repeatMode)
            put("customDaysCsv", customDaysCsv)
            put("voiceEnabled", voiceEnabled)
            put("selectedRecipientIds", selectedRecipientIds.joinToString(","))
        }
        prefs.edit().putString("draft_reminder", json.toString()).apply()
        draftExists = true
        onSnack("草稿已保存")
    }
    fun restoreDraft() {
        val raw = prefs.getString("draft_reminder", null) ?: return
        try {
            val o = JSONObject(raw)
            val savedTitle = o.optString("title", "").take(MAX_TITLE)
            titleDirty = savedTitle.isNotBlank()
            title = savedTitle.ifBlank { defaultTitle }
            note = o.optString("note", "")
            dateEpochDay = o.optLong("dateEpochDay", LocalDate.now().plusDays(1).toEpochDay())
            hour = o.optInt("hour", 8)
            minute = o.optInt("minute", 0)
            kind = o.optString("kind", "ONCE")
            tone = o.optString("tone", "GENTLE")
            repeatMode = o.optString("repeatMode", "DAILY")
            customDaysCsv = o.optString("customDaysCsv", WORKDAY_CSV)
            voiceEnabled = o.optBoolean("voiceEnabled", false)
            val ids = o.optString("selectedRecipientIds", "")
            selectedRecipientIds = if (ids.isBlank()) listOf(userId) else ids.split(",").mapNotNull { it.trim().toLongOrNull() }
            clearErrors()
        } catch (_: Exception) {
            prefs.edit().remove("draft_reminder").apply()
            draftExists = false
        }
        showDraftDialog = false
    }

    // 瞬时状态(不跨进程)
    var parsing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    var showAssist by remember { mutableStateOf(false) }
    // 浮层内错误:不随顶部提示条 3.5s 自动清除,保证用户在浮层里看得到
    var assistError by remember { mutableStateOf("") }
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
        assistError = ""
        scope.launch {
            parsing = true
            try {
                val draft = api.parse(text.take(MAX_PHRASE))
                if (draft.needsManual) {
                    parsedOk = false
                    degradedParse = draft.degraded
                    if (title.isBlank() || !titleDirty) {
                        title = text.take(MAX_TITLE)
                        titleDirty = true
                        titleTick++
                    }
                    assistError = "咕咕没太听懂,标题和时间帮它补一下"
                    phraseError = assistError
                } else {
                    smartText = ""
                    phraseError = ""
                    assistError = ""
                    // 逐字段填入:标题 → 时刻·方式 → 便签,每步珊瑚闪光,像咕咕逐项落笔
                    draft.title?.takeIf { it.isNotBlank() && it != title.trim() }?.let {
                        title = it.take(MAX_TITLE)
                        titleDirty = true
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
                if (!titleDirty) {
                    title = text.take(MAX_TITLE)
                    titleDirty = true
                    titleTick++
                }
                phraseError = e.message ?: "解析失败,手动填一下就好"
                assistError = phraseError
            } catch (_: Exception) {
                parsedOk = false
                if (!titleDirty) {
                    title = text.take(MAX_TITLE)
                    titleDirty = true
                    titleTick++
                }
                phraseError = "网络不佳,先手动填一下吧"
                assistError = phraseError
            } finally {
                parsing = false
            }
        }
    }

    // 语音识别结果只填进 AI 浮层的文本框,是否填表由用户点「帮我填表」决定
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val recognized = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (recognized.isNullOrBlank()) {
            phraseError = "没听清,再试一次或直接打字"
        } else {
            smartText = recognized.take(MAX_PHRASE)
            showAssist = true
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
        title = defaultTitle
        titleDirty = false
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

    /** 创建:先做本地校验(与 Server validate 对齐),再直连 API。overrideDate 用于「改到明天再创建」立即提交。 */
    fun doCreate(overrideDate: LocalDate? = null) {
        if (creating) return
        clearErrors()
        val receiverIds = selectedRecipientIds.toList()
        val fireDate = overrideDate ?: date
        val fireAtValue = LocalDateTime.of(fireDate, LocalTime.of(hour, minute))
        val past = !fireAtValue.isAfter(LocalDateTime.now())
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
            past -> {
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
                        fireAt = "%sT%02d:%02d:00".format(fireDate, hour, minute),
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

    // 页面骨架:顶部栏 + 可滚动表单 + 固定底部操作区;AI 入口为右下悬浮 FAB
    Box(
        Modifier
            .fillMaxSize()
            .background(H.Canvas),
    ) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "新建惦记",
            onBack = onBack,
            guguMood = GuguMood.CHEER,
            actions = {
                if (draftExists) {
                    HeaderIconButton(
                        icon = Icons.Rounded.EditNote,
                        contentDescription = "恢复草稿",
                        onClick = { showDraftDialog = true },
                    )
                }
                HeaderIconButton(
                    icon = Icons.Rounded.Drafts,
                    contentDescription = "保存草稿",
                    onClick = { saveDraft() },
                )
            },
        )

        // 顶部错误提示条:解析失败/创建失败,3.5s 自动消失(对齐看板)
        val topError = phraseError.takeIf { it.isNotEmpty() } ?: formError.takeIf { it.isNotEmpty() }
        AnimatedVisibility(visible = topError != null) {
            topError?.let { msg ->
                LaunchedEffect(msg) { delay(3500); phraseError = ""; formError = "" }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(H.RoseTint)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(16.dp), tint = H.Rose)
                    Spacer(Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = H.Rose, modifier = Modifier.weight(1f))
                }
            }
        }

        Box(Modifier.weight(1f)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            RiseIn {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, H.Hairline, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("把惦记送给谁", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                            Text("可多选 · 已选 ${selectedRecipientIds.size} 人", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
                        }
                    }
                    if (invalidRecipient) {
                        Spacer(Modifier.height(6.dp))
                        Text("部分亲友关联已失效，请取消后重新选择", style = MaterialTheme.typography.bodySmall, color = H.Rose)
                    }
                    Spacer(Modifier.height(10.dp))
                    WhoRow(
                        api = api,
                        selfAvatarUrl = selfAvatarUrl,
                        selfNickname = selfNickname,
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
                }
            }

            Spacer(Modifier.height(12.dp))

            // ② 常驻表单:标题 / 时刻·方式 / 捎句话 / 语气
            RiseIn(delayMs = 60) {
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
                        Icon(Icons.Rounded.EditNote, null, Modifier.size(20.dp), tint = H.Ink3)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("惦记标题", style = MaterialTheme.typography.titleSmall, color = H.Ink3Strong)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${title.length}/$MAX_TITLE",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (title.length >= MAX_TITLE) H.Rose else H.Ink3Strong,
                                )
                            }
                            BasicTextField(
                                value = title,
                                onValueChange = {
                                    title = it.take(MAX_TITLE)
                                    titleDirty = true
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
                                        Text("如:吃降压药 / 早安叫醒", style = MaterialTheme.typography.titleMedium, color = H.Ink3Strong)
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
                            .clickable { showTimeSheet = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, Modifier.size(20.dp), tint = H.Ink3)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("时间", style = MaterialTheme.typography.titleSmall, color = H.Ink3Strong)
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    timeLabelOf(date, hour, minute),
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

                FlashRow(tick = 0) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showModeSheet = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EventRepeat, null, Modifier.size(20.dp), tint = H.Ink3)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("方式", style = MaterialTheme.typography.titleSmall, color = H.Ink3Strong)
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    modeLabelOf(kind, repeatMode, customDaysCsv, tone),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = H.CoralInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.Rounded.Edit, null, Modifier.size(13.dp), tint = H.Ink3)
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
                        Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(20.dp), tint = H.Ink3)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("捎句话(可选)", style = MaterialTheme.typography.titleSmall, color = H.Ink3Strong)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${note.length}/$MAX_NOTE",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (note.length >= MAX_NOTE) H.Rose else H.Ink3Strong,
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
                                        Text("写一句暖心的惦记…", style = MaterialTheme.typography.titleMedium, color = H.Ink3Strong)
                                    }
                                    inner()
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = H.Hairline, thickness = 1.dp)
            }
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

            Spacer(Modifier.height(96.dp))
        }

        // AI 悬浮入口:右下常驻,点开半屏浮层
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(H.Coral)
                .clickable { assistError = ""; showAssist = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, "咕咕帮填", Modifier.size(17.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("咕咕帮填", style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
        }

        // ③ 固定操作区:按钮不随表单滚动,也不随键盘上移
        RiseIn(delayMs = 120) {
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
                        isPast -> "改到明天再创建"
                        else -> "创建惦记"
                    },
                    onClick = {
                        when {
                            needsPermission -> onRequestPermission()
                            isPast -> {
                                val tomorrow = LocalDate.now().plusDays(1)
                                dateEpochDay = tomorrow.toEpochDay()
                                formError = ""
                                doCreate(tomorrow)
                            }
                            else -> doCreate()
                        }
                    },
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
                        color = H.Ink3Strong,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ④ 智能输入已收进右下「咕咕帮填」浮层
    }
    }

    // 时间弹层:大号时间 + 日期 chips + Material3 表盘
    if (showTimeSheet) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        ModalBottomSheet(
            onDismissRequest = { showTimeSheet = false },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                // 滚轮需要自由上下滑动,禁止把下拉手势当成关闭
                confirmValueChange = { it != SheetValue.Hidden },
            ),
            containerColor = H.Canvas,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // 拦截垂直拖拽:手指轻移时弹层不再跟随滑动,避免表盘内容位移造成点偏
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, _ -> change.consume() }
                    }
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "选择时间",
                        style = MaterialTheme.typography.titleLarge,
                        color = H.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        onClick = { showTimeSheet = false },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "%02d:%02d".format(timeState.hour, timeState.minute),
                    style = MaterialTheme.typography.displayMedium,
                    color = H.Ink,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    dayLabelOf(date),
                    style = MaterialTheme.typography.titleSmall,
                    color = H.Ink3Strong,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val today = LocalDate.now()
                    (0..2).forEach { offset ->
                        val d = today.plusDays(offset.toLong())
                        ChoiceChip(
                            text = listOf("今天", "明天", "后天")[offset],
                            selected = d == date,
                            onClick = {
                                dateEpochDay = d.toEpochDay()
                                formError = ""
                            },
                        )
                    }
                    ChoiceChip(
                        text = "其他日期…",
                        selected = date.toEpochDay() !in today.toEpochDay()..today.plusDays(2).toEpochDay(),
                        onClick = { showDatePicker = true },
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 给足高度让表盘保持正圆:被压扁成椭圆时,视觉位置与圆形触摸几何不一致会点偏
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(
                        state = timeState,
                        modifier = Modifier.size(width = 320.dp, height = 400.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                GradientButton(
                    text = "就这个",
                    onClick = {
                        hour = timeState.hour
                        minute = timeState.minute
                        formError = ""
                        showTimeSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                )
            }
        }
    }

    // 方式弹层:单次 / 循环 / 互叫 + 循环规则 + 语气 + 语音开关
    if (showModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModeSheet = false },
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
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "选择方式",
                        style = MaterialTheme.typography.titleLarge,
                        color = H.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        onClick = { showModeSheet = false },
                    )
                }
                Spacer(Modifier.height(16.dp))
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
                            Text("到点播报咕咕语音,更有真人感", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
                        }
                        BrandSwitch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it })
                    }
                }

                // 语气:非关键信息收进弹层,表单卡保持四行紧凑
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
                    onClick = { showModeSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                )
            }
        }
    }

    // AI 浮层:识别到的文字先落在框里,点「帮我填表」才解析回填
    if (showAssist) {
        // 解析成功后自动收起浮层
        LaunchedEffect(parsedOk) { if (parsedOk) showAssist = false }
        ModalBottomSheet(
            onDismissRequest = { showAssist = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = H.Canvas,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("咕咕帮你填", style = MaterialTheme.typography.titleLarge, color = H.Ink)
                        Text(
                            "说一句或写一句,识别结果先放在下面,确认后填表",
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3Strong,
                        )
                    }
                    HeaderIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        onClick = { showAssist = false },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, H.Hairline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = smartText,
                        onValueChange = {
                            smartText = it.take(MAX_PHRASE)
                            phraseError = ""
                            assistError = ""
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = H.Ink),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    ) { inner ->
                        Box {
                            if (smartText.isEmpty()) {
                                Text(
                                    "如:明早七点半提醒${receiverName}吃降压药",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = H.Ink3Strong,
                                )
                            }
                            inner()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${smartText.length}/$MAX_PHRASE",
                        style = MaterialTheme.typography.titleSmall,
                        color = H.Ink3Strong,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                }
                if (assistError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(assistError, style = MaterialTheme.typography.titleSmall, color = H.Rose)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (voiceAvailable) H.CoralTint else H.GreyTint)
                            .clickable(enabled = voiceAvailable && !parsing) { startVoice() }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            "语音识别",
                            Modifier.size(18.dp),
                            tint = if (voiceAvailable) H.CoralInk else H.Ink3,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (voiceAvailable) "语音" else "无语音服务",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (voiceAvailable) H.CoralInk else H.Ink3,
                        )
                    }
                    GradientButton(
                        text = if (parsing) "咕咕在想…" else "帮我填表",
                        onClick = { doParse() },
                        enabled = smartText.isNotBlank() && !parsing,
                        loading = parsing,
                        modifier = Modifier.weight(1.2f),
                        height = 48.dp,
                    )
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

    // 草稿恢复确认对话框
    if (showDraftDialog) {
        AlertDialog(
            onDismissRequest = { showDraftDialog = false },
            title = { Text("恢复草稿?", color = H.Ink) },
            text = { Text("将用上次保存的草稿覆盖当前填写内容。", color = H.Ink3Strong) },
            confirmButton = {
                TextButton(onClick = { restoreDraft() }) { Text("恢复", color = H.CoralInk) }
            },
            dismissButton = {
                TextButton(onClick = { showDraftDialog = false }) { Text("取消", color = H.Ink2) }
            },
        )
    }
}

/** 送给行:抖音消息页式「头像在上、备注/昵称在下」横滑列表;头像优先,缺失回退首字。 */
@Composable
private fun WhoRow(
    api: GeitaApi,
    selfAvatarUrl: String?,
    selfNickname: String?,
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
    val selfAvatar = rememberAvatarBitmap(api, selfAvatarUrl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 自己恒在最前
        PersonColumn(
            name = selfNickname?.takeIf { it.isNotBlank() } ?: "我",
            avatarText = "自",
            avatarBitmap = selfAvatar,
            selected = selfSelected,
            onClick = onSelectSelf,
        )
        when {
            bonds.isNotEmpty() -> Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    bonds.forEach { bond ->
                        PersonColumn(
                            name = bond.displayName,
                            avatarText = bond.displayName.take(1),
                            avatarBitmap = rememberAvatarBitmap(api, bond.counterpartAvatarUrl),
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
                        .height(64.dp)
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
                color = H.Ink3Strong,
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
                    color = H.Ink3Strong,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                MiniAction("去绑定", onGoBind, H.CoralInk)
            }
        }
    }
}

/** 抖音消息页式人物项:头像在上、备注/昵称在下;选中加珊瑚环与右下勾。 */
@Composable
private fun PersonColumn(
    name: String,
    avatarText: String,
    avatarBitmap: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AvatarBubble(
                bitmap = avatarBitmap,
                fallbackText = avatarText,
                size = 48.dp,
                selected = selected,
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(H.Coral)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(11.dp), tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) H.CoralInk else H.Ink3Strong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 轻量头像加载:按 URL 拉一次并解码;失败返回 null,由 AvatarBubble 回退首字。 */
@Composable
private fun rememberAvatarBitmap(api: GeitaApi, url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        runCatching { api.downloadStatic(url) }.getOrNull()?.let { raw ->
            bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()
        }
    }
    return bitmap
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
            Text(desc, style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = H.Coral)
        }
    }
}

/** 弹层分组标签。 */
@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = H.Ink3Strong)
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

/** 日期回显:今天/明天/后天,其余显示月日。 */
private fun dayLabelOf(date: LocalDate): String = when (date.toEpochDay() - LocalDate.now().toEpochDay()) {
    0L -> "今天"
    1L -> "明天"
    2L -> "后天"
    else -> "%d月%d日".format(date.monthValue, date.dayOfMonth)
}

/** 时间行回显:如「明天 08:00」。 */
private fun timeLabelOf(date: LocalDate, hour: Int, minute: Int): String =
    "%s %02d:%02d".format(dayLabelOf(date), hour, minute)

/** 方式行回显:如「循环·工作日 · 轻柔」「单次 · 唠叨」。 */
private fun modeLabelOf(
    kind: String,
    repeatMode: String,
    customDaysCsv: String,
    tone: String,
): String {
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
    return "$kindLabel · $toneLabel"
}
