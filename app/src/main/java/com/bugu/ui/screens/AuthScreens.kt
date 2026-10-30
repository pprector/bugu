package com.bugu.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bugu.client.api.GeitaApiException
import com.bugu.client.api.UserDto
import com.bugu.ApiFactory
import com.bugu.SharedPrefsTokenStore
import com.bugu.ui.common.FieldError
import com.bugu.ui.common.GradientButton
import com.bugu.ui.common.GuguBird
import com.bugu.ui.common.GuguMood
import com.bugu.ui.common.Waveform
import com.bugu.ui.theme.H
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- 登录 ----------

private const val SEND_CODE_WINDOW_SECONDS = 60
private val GUGU_IDLE_LINES = listOf("输入验证码就好", "每一份惦记,都有回音", "咕咕看着你写～")

/** 大陆手机号前置校验:1 开头第二位 3-9。 */
private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")

/** 3-4-4 分组显示:138 0000 8000。 */
private fun formatPhone(raw: String): String = buildString {
    raw.forEachIndexed { index, c ->
        if (index == 3 || index == 7) append(' ')
        append(c)
    }
}

/**
 * 登录屏:手机号 + 短信验证码走真实后端(send-code 60s 限流 → login 换 JWT)。
 * JWT 落 SharedPrefs,登录成功回传 UserDto 由 AppRoot 接管导航。
 * 体验细节:验证码输满自动提交、系统 OTP 自动填充、手机号满自动跳验证码、
 * 倒计时按发送时间戳持久化(进程回收/旋转不丢)、协议可查看且同意状态记住。
 */
@Composable
fun LoginScreen(onLoggedIn: (UserDto) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bugu_main", Context.MODE_PRIVATE) }
    // 登录页独立建客户端(AppRoot 在登录前不持有 api);成功后写 JWT 供后续请求注入
    val api = remember { ApiFactory.create(context) }
    val tokenStore = remember { SharedPrefsTokenStore(context) }
    val scope = rememberCoroutineScope()

    var phone by rememberSaveable { mutableStateOf(prefs.getString("last_phone", "").orEmpty()) }
    var code by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(prefs.getBoolean("agreement_agreed", false)) }
    var lastSentAt by rememberSaveable { mutableLongStateOf(prefs.getLong("code_sent_at", 0L)) }
    var sending by remember { mutableStateOf(false) }
    var loggingIn by remember { mutableStateOf(false) }
    var phoneError by rememberSaveable { mutableStateOf<String?>(null) }
    var codeError by rememberSaveable { mutableStateOf<String?>(null) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingAgreement by remember { mutableStateOf<String?>(null) }
    // 协议门:未勾选时暂存被拦截的动作,「同意并继续」后自动勾选并执行
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var guguMood by remember { mutableStateOf(GuguMood.RING) }
    var guguLine by remember { mutableStateOf(GUGU_IDLE_LINES.random()) }
    var successUser by remember { mutableStateOf<UserDto?>(null) }

    val codeFocus = remember { FocusRequester() }

    fun remaining(): Int {
        val elapsedSeconds = (System.currentTimeMillis() - lastSentAt) / 1000
        return (SEND_CODE_WINDOW_SECONDS - elapsedSeconds).toInt().coerceIn(0, SEND_CODE_WINDOW_SECONDS)
    }
    var countdown by remember { mutableIntStateOf(remaining()) }

    // 倒计时:单协程按真实时间戳推算,后台挂起/进程回收恢复均不漂移
    LaunchedEffect(Unit) {
        while (true) {
            if (countdown > 0) {
                delay(1000)
                countdown = remaining()
            } else {
                delay(300)
            }
        }
    }

    // 发码成功的欢快情绪 2.6s 后回落
    LaunchedEffect(guguMood) {
        if (guguMood == GuguMood.CHEER) {
            delay(2600)
            guguMood = GuguMood.RING
            guguLine = GUGU_IDLE_LINES.random()
        }
    }

    // 登录成功的峰值时刻:全屏庆祝一拍再交给 AppRoot 接管导航
    LaunchedEffect(successUser) {
        successUser?.let {
            delay(900)
            onLoggedIn(it)
            successUser = null
        }
    }

    val phoneOk = PHONE_REGEX.matches(phone)
    val codeOk = code.length == 6
    val busy = sending || loggingIn

    fun agreeNow() {
        agreed = true
        prefs.edit().putBoolean("agreement_agreed", true).apply()
    }

    /** 协议门:已同意直接执行;未同意弹确认,同意后自动勾选并继续。 */
    fun ensureAgreed(action: () -> Unit) {
        if (agreed) action() else pendingAction = action
    }

    /** 发送验证码:服务端 60s 限流,失败展示服务端文案(如 SMS_RATE_LIMITED)。 */
    fun doSendCode() {
        scope.launch {
            sending = true
            try {
                api.sendCode(phone)
                lastSentAt = System.currentTimeMillis()
                prefs.edit().putLong("code_sent_at", lastSentAt).apply()
                countdown = SEND_CODE_WINDOW_SECONDS
                formError = null
                codeError = null
                guguMood = GuguMood.CHEER
                guguLine = "验证码飞过来啦～"
                runCatching { codeFocus.requestFocus() }
            } catch (e: GeitaApiException) {
                formError = e.message ?: "发送失败,请稍后再试"
            } catch (_: Exception) {
                formError = "网络不佳,请稍后再试"
            } finally {
                sending = false
            }
        }
    }

    /** 登录:换 JWT 持久化后回传用户(先展示成功反馈再导航)。 */
    fun doLogin() {
        scope.launch {
            loggingIn = true
            try {
                val resp = api.login(phone, code)
                tokenStore.set(resp.token)
                prefs.edit().putString("last_phone", phone).apply()
                successUser = resp.user
            } catch (e: GeitaApiException) {
                formError = e.message ?: "登录失败,请稍后再试"
            } catch (_: Exception) {
                formError = "网络不佳,请稍后再试"
            } finally {
                loggingIn = false
            }
        }
    }

    fun requestSendCode() {
        when {
            busy -> {}
            // 实时校验:phoneOk 等派生值是 composition 快照,自动触发场景会读到旧值
            !PHONE_REGEX.matches(phone) -> phoneError = "请输入正确的 11 位手机号"
            else -> ensureAgreed { doSendCode() }
        }
    }

    fun requestLogin() {
        when {
            busy -> {}
            !PHONE_REGEX.matches(phone) -> phoneError = "请输入正确的 11 位手机号"
            code.length != 6 -> codeError = "请输入 6 位短信验证码"
            else -> ensureAgreed { doLogin() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 表单整体居中偏上:键盘弹出(adjustResize+imePadding)时输入区不被遮挡
        Column(
            Modifier
                .fillMaxSize()
                .background(H.Canvas)
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // 品牌标志:咕咕(情绪随流程变化,点它会随机说一句话)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(92.dp)
                        .background(Color(0xFFFFEDE6), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    GuguBird(
                        guguMood,
                        Modifier.size(80.dp),
                        onTap = { guguLine = GUGU_IDLE_LINES.random() },
                        pokeLines = listOf(guguLine),
                        bubbleOffset = (-46).dp,
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Waveform(
                            bars = listOf(0.4f, 0.8f, 0.5f, 0.9f, 0.45f),
                            color = H.Mint,
                            modifier = Modifier
                                .height(14.dp)
                                .width(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "欢迎来到布谷",
                style = MaterialTheme.typography.headlineMedium,
                color = H.Ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .border(1.dp, H.Hairline, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(H.Coral))
                    Spacer(Modifier.width(7.dp))
                    Text("让每一份惦记 · 都有回音", style = MaterialTheme.typography.titleSmall, color = H.Ink2)
                }
            }

            Spacer(Modifier.height(24.dp))

            // 表单卡片
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(18.dp),
            ) {
                Text("手机号码", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(H.WarmFill)
                            .padding(horizontal = 14.dp, vertical = 15.dp),
                    ) {
                        Text("+86", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(H.WarmFill)
                            .border(1.dp, if (phone.isNotEmpty()) H.Coral else Color.Transparent, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        BasicInput(
                            value = phone,
                            onValue = { digits ->
                                val wasFull = phoneOk
                                phone = digits
                                phoneError = null
                                formError = null
                                // 输满 11 位自动跳到验证码,减少一次点击
                                if (!wasFull && digits.length == 11) runCatching { codeFocus.requestFocus() }
                            },
                            hint = "请输入手机号码",
                            number = true,
                            maxDigits = 11,
                            imeAction = ImeAction.Next,
                            onImeAction = { runCatching { codeFocus.requestFocus() } },
                            display = ::formatPhone,
                        )
                    }
                }
                FieldError(phoneError)

                Spacer(Modifier.height(16.dp))
                Text("短信验证码", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(H.WarmFill)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicInput(
                            value = code,
                            onValue = { digits ->
                                code = digits
                                codeError = null
                                formError = null
                                // 输满 6 位自动提交,免一次点击
                                if (digits.length == 6) requestLogin()
                            },
                            hint = "请输入6位验证码",
                            number = true,
                            maxDigits = 6,
                            imeAction = ImeAction.Done,
                            onImeAction = { requestLogin() },
                            otp = true,
                            modifier = Modifier.focusRequester(codeFocus),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (countdown > 0 || !phoneOk || sending) H.GreyTint else H.CoralTint)
                            .then(
                                if (countdown > 0 || !phoneOk || sending) Modifier
                                else Modifier.clickable { requestSendCode() },
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            when {
                                sending -> "发送中…"
                                countdown > 0 -> "重新获取(${countdown}s)"
                                else -> "获取验证码"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (countdown > 0 || !phoneOk || sending) H.Ink3 else H.Coral,
                        )
                    }
                }
                FieldError(codeError)

                Spacer(Modifier.height(20.dp))
                GradientButton(
                    text = if (loggingIn) "登录中…" else "登录 / 注册",
                    onClick = { requestLogin() },
                    enabled = !busy,
                    loading = loggingIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 服务端/网络/协议错误:内联在按钮下方,键盘弹出时仍可见
                FieldError(formError)
            }

            Spacer(Modifier.height(14.dp))

            AgreementRow(
                agreed = agreed,
                onAgreedChange = { checked ->
                    if (checked) {
                        agreeNow()
                    } else {
                        agreed = false
                        prefs.edit().putBoolean("agreement_agreed", false).apply()
                    }
                },
                onView = { viewingAgreement = it },
            )

            // 底部弹性留白:表单区整体居中偏上,键盘弹出时仍留可视余量
            Spacer(Modifier.weight(1f, fill = true))
            Spacer(Modifier.height(24.dp))
        }

        // 登录成功峰值反馈:全屏庆祝一拍再导航
        successUser?.let {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(H.Canvas.copy(alpha = 0.96f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GuguBird(
                        GuguMood.CHEER,
                        Modifier.size(96.dp),
                        pokeLines = listOf("登录成功,咕咕带你回家～"),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "登录成功",
                        style = MaterialTheme.typography.headlineMedium,
                        color = H.Ink,
                    )
                }
            }
        }
    }

    // 协议全文查看
    viewingAgreement?.let { which ->
        AgreementDialog(which = which, onDismiss = { viewingAgreement = null })
    }

    // 协议确认(未勾选时拦截发码/登录,确认后自动勾选并继续原动作)
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("需要你的同意", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "继续之前请先同意《用户协议》与《隐私政策》(点击协议名可查看全文);同意后布谷才能为你发送短信验证码。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = H.Ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    agreeNow()
                    action()
                }) { Text("同意并继续", color = H.Coral, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("暂不", color = H.Ink3) }
            },
        )
    }
}

@Composable
private fun BasicInput(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    number: Boolean = false,
    maxDigits: Int = 0,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
    display: (String) -> String = { it },
    otp: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // TextFieldValue 受控:文本与光标一起管理。普通 String 受控 + 展示层格式化会与
    // IME composing 竞争导致快速输入时字符乱序(实测踩坑),此为 Compose 格式化输入标准做法。
    val shown = display(value)
    val textField = remember { mutableStateOf(TextFieldValue(shown, TextRange(shown.length))) }
    // 外部值变化(初始/进程恢复/系统 autofill 填充)同步进来;自身输入循环中两者一致则不动
    LaunchedEffect(value) {
        if (textField.value.text != shown) {
            textField.value = TextFieldValue(shown, TextRange(shown.length))
        }
    }
    val baseModifier = if (otp) {
        // 系统验证码自动填充(Android autofill 框架读 contentType 映射 smsOTPCode hint)
        modifier
            .semantics { contentType = ContentType.SmsOtpCode }
            .fillMaxWidth()
    } else {
        modifier.fillMaxWidth()
    }
    BasicTextField(
        value = textField.value,
        onValueChange = { new ->
            val digits = if (number) {
                new.text.filter(Char::isDigit).let { if (maxDigits > 0) it.take(maxDigits) else it }
            } else {
                new.text
            }
            val formatted = display(digits)
            textField.value = TextFieldValue(formatted, TextRange(formatted.length))
            onValue(digits)
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = H.Ink),
        cursorBrush = Brush.verticalGradient(listOf(H.Coral, H.Coral)),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        modifier = baseModifier,
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) {
                    Text(hint, style = MaterialTheme.typography.bodyLarge, color = H.Ink3)
                }
                field()
            }
        },
    )
}

// 协议勾选行:勾选框带弹簧缩放动画;协议名可点开查看全文
@Composable
private fun AgreementRow(agreed: Boolean, onAgreedChange: (Boolean) -> Unit, onView: (String) -> Unit) {
    val bg by animateColorAsState(if (agreed) H.Coral else Color.Transparent, tween(180), label = "agreeBg")
    val checkScale by animateFloatAsState(
        targetValue = if (agreed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "agreeCheck",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onAgreedChange(!agreed) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, if (agreed) Color.Transparent else H.Ink3, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Check, null, Modifier.size(12.dp).scale(checkScale), tint = Color.White)
        }
        Spacer(Modifier.width(7.dp))
        Text("已阅读并同意", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
        Text(
            "《用户协议》",
            style = MaterialTheme.typography.bodySmall,
            color = H.Coral,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onView("user") }
                .padding(horizontal = 1.dp, vertical = 4.dp),
        )
        Text("和", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
        Text(
            "《隐私政策》",
            style = MaterialTheme.typography.bodySmall,
            color = H.Coral,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onView("privacy") }
                .padding(horizontal = 1.dp, vertical = 4.dp),
        )
    }
}

// 协议全文:应用内弹窗查看(合规要求协议可读)
@Composable
internal fun AgreementDialog(which: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(22.dp),
        ) {
            Text(
                if (which == "user") "用户协议" else "隐私政策",
                style = MaterialTheme.typography.titleLarge,
                color = H.Ink,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (which == "user") USER_AGREEMENT_TEXT else PRIVACY_POLICY_TEXT,
                style = MaterialTheme.typography.bodySmall,
                color = H.Ink2,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(16.dp))
            GradientButton(
                text = "我知道了",
                onClick = onDismiss,
                height = 46.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val USER_AGREEMENT_TEXT = """
    欢迎使用布谷!本协议是你与布谷之间就使用本应用服务所订立的约定,开始使用前请仔细阅读。

    一、服务内容
    布谷为家人之间提供「惦记」提醒服务:创建提醒、到点送达、确认与互叫等。部分功能需要与家人完成绑定后使用。

    二、账号与安全
    1. 你可以使用手机号验证码登录/注册布谷账号,请妥善保管验证码,勿告知他人。
    2. 你的行为需遵守法律法规,不得利用布谷发送违法、骚扰或侵权内容。

    三、用户内容
    你创建的提醒标题、备注等内容由你负责,请勿填写违法或侵犯他人权益的信息。

    四、服务变更与终止
    我们会持续优化服务,可能新增或调整功能;如你不愿继续使用,可随时停止使用。

    五、免责声明
    因不可抗力、网络故障、设备限制(如系统权限未开启)导致的提醒送达延迟或失败,我们不承担责任,但会尽力保障送达。

    六、协议更新
    协议如有更新会在应用内提示,继续使用即视为接受更新后的条款。如有疑问可通过应用内「我的 → 帮助与反馈」提交反馈。
""".trimIndent()

private val PRIVACY_POLICY_TEXT = """
    布谷非常重视你的隐私。本政策说明我们收集哪些信息、如何使用与保护它们。

    一、我们收集的信息
    1. 账号信息:手机号(用于验证码登录与账号识别)。
    2. 资料信息:昵称、头像(上传后存储于腾讯云对象存储)。
    3. 设备信息:设备标识、机型、推送令牌(用于消息推送送达)。
    4. 使用数据:提醒内容与确认记录(仅用于功能展示与同步)。

    二、信息的使用
    仅用于提供布谷的服务:验证身份、送达提醒、展示历史与互叫状态。不会用于广告,也不会与第三方共享用于营销。

    三、信息的存储与保护
    数据存储于境内服务器,传输采用 HTTPS 加密,存储访问受权限控制。我们采取合理安全措施防止信息泄露、损毁或丢失。

    四、第三方服务
    短信验证码由火山引擎发送,推送由极光推送提供,对象存储由腾讯云提供;这些服务商仅获得提供对应服务所必需的最小信息。

    五、你的权利
    如需了解个人信息处理情况,可通过应用内「我的 → 帮助与反馈」提交反馈。

    六、未成年人保护
    布谷主要面向家庭用户。若你是未成年人,请在监护人陪同下阅读本政策并在其同意后使用。

    七、政策更新
    政策如有重大变更,我们会在应用内显著提示。
""".trimIndent()

// ---------- 权限引导 ----------

/**
 * 权限引导:真实申请 POST_NOTIFICATIONS(Android 13+)与 SCHEDULE_EXACT_ALARM(跳系统设置)。
 * 两者都拿到才回调 onDone(true);「跳过」与「暂不开启」回调 onDone(false),不再有假体验模式。
 */
@Composable
fun PermissionsScreen(onBack: () -> Unit, onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    // 通知权限:Android 13 起需运行时申请;低版本默认可通知
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    // 精确闹钟:Android 12 起需用户在系统设置授权;低版本默认可用
    var exactAlarmGranted by remember { mutableStateOf(checkExactAlarm(context)) }

    // 从系统设置返回时刷新精确闹钟状态(该权限无应用内弹窗,只能跳设置)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted =
                    Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                exactAlarmGranted = checkExactAlarm(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 通知权限:应用内系统弹窗
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 精确闹钟:跳系统设置页;ROM 裁剪该入口时兜底到应用详情页
    fun requestExactAlarm() {
        if (exactAlarmGranted) return
        try {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")),
                )
            } catch (_: Exception) {
                // 连应用详情都进不去的极旧 ROM:保持未授予,由用户稍后自行处理
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "返回",
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(10.dp),
                tint = H.Ink,
            )
            Text(
                "核心守护权限",
                style = MaterialTheme.typography.headlineSmall,
                color = H.Ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Text(
                "跳过",
                style = MaterialTheme.typography.titleMedium,
                color = H.Ink2,
                modifier = Modifier.clickable { onDone(false) }.padding(6.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        // 插画
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFE3DA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, null, Modifier.size(40.dp), tint = H.Coral)
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 90.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(H.CoralLight),
            )
        }

        Text(
            "为了让每声呼唤准时到达",
            style = MaterialTheme.typography.headlineMedium,
            color = H.Ink,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "开启 2 项核心守护权限,惦记才不会错过",
            style = MaterialTheme.typography.bodyMedium,
            color = H.Ink2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        val items = listOf(
            PermissionItem(
                icon = Icons.Outlined.Notifications,
                title = "温和通知权限",
                desc = "日常备忘与轻量问候,在屏幕顶部温和呈现",
                granted = notificationsGranted,
                onClick = { requestNotifications() },
            ),
            PermissionItem(
                icon = Icons.Outlined.Alarm,
                title = "准时闹钟权限",
                desc = "静音或勿扰模式下仍可准点全屏唤醒,防止睡过与漏服",
                granted = exactAlarmGranted,
                keyBadge = true,
                onClick = { requestExactAlarm() },
            ),
        )
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .clickable(onClick = item.onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(H.CoralTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, null, Modifier.size(22.dp), tint = H.Coral)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = H.Ink)
                        if (item.keyBadge) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(H.RoseTint)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("关键", style = MaterialTheme.typography.titleSmall, color = H.Rose)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(item.desc, style = MaterialTheme.typography.bodySmall, color = H.Ink3)
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (item.granted) H.Mint else H.Track),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.granted) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Color.White)
                    } else {
                        Icon(Icons.Outlined.Bolt, null, Modifier.size(16.dp), tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(H.GreyTint)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(20.dp), tint = H.Ink3)
            Spacer(Modifier.width(10.dp))
            Text(
                "权限仅用于准时送达惦记;没有授权也能先用,随时可回到本页开启",
                style = MaterialTheme.typography.bodySmall,
                color = H.Ink2,
            )
        }

        Spacer(Modifier.height(24.dp))
        GradientButton(
            text = "开启并进入布谷",
            onClick = { onDone(notificationsGranted && exactAlarmGranted) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "暂不开启,先去逛逛",
            style = MaterialTheme.typography.titleMedium,
            color = H.Ink2,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDone(false) }
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** 精确闹钟可用性:Android 12 起读系统授予状态,低版本视为可用。 */
private fun checkExactAlarm(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < 31) {
        true
    } else {
        try {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } catch (_: Exception) {
            false
        }
    }

private data class PermissionItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val desc: String,
    val granted: Boolean,
    val keyBadge: Boolean = false,
    val onClick: () -> Unit,
)
