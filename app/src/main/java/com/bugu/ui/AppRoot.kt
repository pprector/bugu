package com.bugu.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bugu.client.api.BondDto
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.client.api.UserDto
import com.bugu.ApiFactory
import com.bugu.PushReporter
import com.bugu.SharedPrefsTokenStore
import com.bugu.schedule.ReminderSync
import com.bugu.ui.common.GradientButton
import com.bugu.ui.common.GuguBird
import com.bugu.ui.common.GuguMood
import com.bugu.ui.screens.CalendarScreen
import com.bugu.ui.screens.CompanionScreen
import com.bugu.ui.screens.CreateReminderScreen
import com.bugu.ui.screens.DashboardScreen
import com.bugu.ui.screens.FriendsScreen
import com.bugu.ui.screens.HistoryScreen
import com.bugu.ui.screens.LoginScreen
import com.bugu.ui.screens.PermissionsScreen
import com.bugu.ui.screens.ProfileScreen
import com.bugu.ui.theme.H
import kotlinx.coroutines.launch

/** 大字关怀模式的内容字号倍率(不放大 dp,避免底部导航与固定尺寸组件被挤压)。 */
private const val CARE_FONT_SCALE = 1.18f

/** 一级页面(响铃不在其中:真实触发由 RingActivity 全屏承载)。 */
sealed interface Screen {
    data object Login : Screen
    data object Permissions : Screen
    data object Main : Screen
    data object Friends : Screen
    data object History : Screen
    data object Calendar : Screen
}

enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("看板", Icons.Filled.GridView),
    CREATE("新建", Icons.Filled.Add),
    COMPANION("陪伴", Icons.Filled.Favorite),
    PROFILE("我的", Icons.Filled.Person),
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val tokenStore = remember { SharedPrefsTokenStore(context) }
    var booted by remember { mutableStateOf(false) }
    var bootFailed by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf<UserDto?>(null) }
    var bonds by remember { mutableStateOf<List<BondDto>>(emptyList()) }
    var bondsLoading by remember { mutableStateOf(false) }
    var bondsError by remember { mutableStateOf(false) }
    // Tab 内表单状态跨页保留(去权限页/切 Tab/进程回收后回来仍能续填)
    val tabStates = rememberSaveableStateHolder()
    // 全局 401:任意接口登录态失效即清 token 踢回登录页
    val api = remember {
        ApiFactory.create(context) {
            tokenStore.set(null)
            user = null
            bonds = emptyList()
        }
    }
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    // 大字关怀模式:按字号缩放,而非把看板当成「新用户空态」;持久化后重启仍生效
    var careMode by remember {
        mutableStateOf(
            context.getSharedPreferences("bugu_main", Context.MODE_PRIVATE).getBoolean("care_mode", false),
        )
    }
    val setCareMode: (Boolean) -> Unit = { enabled ->
        careMode = enabled
        context.getSharedPreferences("bugu_main", Context.MODE_PRIVATE)
            .edit().putBoolean("care_mode", enabled).apply()
    }
    // 权限引导标记:持久化(走过一次即不再拦),进程重启后仍生效
    var permAsked by remember {
        mutableStateOf(
            context.getSharedPreferences("bugu_main", Context.MODE_PRIVATE).getBoolean("perm_asked", false),
        )
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    // 启动恢复登录态:有 token 校验 me();401 清 token 走登录,网络故障保留 token 给重试页
    suspend fun restoreSession() {
        bootFailed = false
        if (SharedPrefsTokenStore(context).get() == null) return
        user = try {
            api.me()
        } catch (e: GeitaApiException) {
            if (e.code == "UNAUTHORIZED") null else null.also { bootFailed = true }
        } catch (_: Exception) {
            null.also { bootFailed = true }
        }
        if (user != null) {
            PushReporter.launch(context, api)
        }
    }

    LaunchedEffect(Unit) {
        restoreSession()
        booted = true
    }

    /** 拉绑定关系:失败保留旧值并置错误态,避免网络抖动把「已绑定」误显示成「未绑定」。 */
    suspend fun loadBonds() {
        bondsLoading = true
        bondsError = false
        try {
            bonds = api.myBonds()
        } catch (_: Exception) {
            bondsError = true
        } finally {
            bondsLoading = false
        }
    }

    // 登录态就绪后拉绑定关系(1v1)并同步端侧精确闹钟
    LaunchedEffect(user?.id) {
        if (user != null) {
            loadBonds()
            // FR-07:把我接收的待响应提醒注册为 AlarmManager 精确闹钟(推送之外的第二保险)
            runCatching { ReminderSync.sync(context, api) }
        }
    }

    fun logout() {
        scope.launch {
            SharedPrefsTokenStore(context).set(null)
            user = null
            bonds = emptyList()
            bondsError = false
        }
    }

    /** 绑定状态变化(发起/确认/解绑)后重拉。 */
    fun refreshBonds() {
        scope.launch { loadBonds() }
    }

    Box(Modifier.fillMaxSize().background(H.Canvas)) {
        AnimatedContent(
            targetState = Triple(booted, user, screen),
            transitionSpec = {
                (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 8 }) togetherWith
                    fadeOut(tween(160))
            },
            label = "screenTransition",
        ) { (ready, u, s) ->
            when {
                !ready -> Unit

                bootFailed -> BootRetryScreen(
                    onRetry = { scope.launch { restoreSession() } },
                )

                u == null -> LoginScreen(
                    onLoggedIn = { fresh ->
                        user = fresh
                        PushReporter.launch(context, api)
                    },
                )

                s == Screen.Permissions -> PermissionsScreen(
                    onBack = {
                        markPermAsked(context) { permAsked = it }
                        screen = Screen.Main
                    },
                    onDone = { granted ->
                        markPermAsked(context) { permAsked = it }
                        if (granted) {
                            showSnack("权限就绪,咕咕可以准时唤人了")
                        } else {
                            showSnack("已进入体验模式,随时可回来开启权限")
                        }
                        screen = Screen.Main
                    },
                )

                s == Screen.Friends -> FriendsScreen(
                    api = api,
                    bonds = bonds,
                    onRefresh = ::refreshBonds,
                    onBack = { screen = Screen.Main },
                    onSnack = showSnack,
                    onCreateFor = {
                        screen = Screen.Main
                        tab = Tab.CREATE
                    },
                )

                s == Screen.History -> HistoryScreen(
                    api = api,
                    peerName = activePeerName(bonds),
                    onBack = { screen = Screen.Main },
                    onGoCreate = {
                        screen = Screen.Main
                        tab = Tab.CREATE
                    },
                )

                s == Screen.Calendar -> CalendarScreen(
                    api = api,
                    peerName = activePeerName(bonds),
                    onBack = { screen = Screen.Main },
                )

                else -> MainHost(
                    api = api,
                    user = u,
                    onUserUpdated = { user = it },
                    bonds = bonds,
                    bondsLoading = bondsLoading,
                    bondsError = bondsError,
                    peerName = activePeerName(bonds),
                    onRefreshBonds = ::refreshBonds,
                    onLogout = ::logout,
                    tab = tab,
                    onTabChange = { tab = it },
                    onOpen = { screen = it },
                    careMode = careMode,
                    onCareModeChange = setCareMode,
                    permAsked = permAsked,
                    tabStates = tabStates,
                    onSnack = showSnack,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        ) { data ->
            Snackbar(
                containerColor = H.Ink,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) { Text(data.visuals.message) }
        }
    }
}

/** 启动恢复失败(非 401 的网络/服务故障):保留 token,给一次重试入口,不踢回登录。 */
@Composable
private fun BootRetryScreen(onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GuguBird(
            GuguMood.SLEEP,
            Modifier.size(88.dp),
            pokeLines = listOf("咕咕打了个盹…"),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "网络有点不稳定",
            style = MaterialTheme.typography.headlineSmall,
            color = H.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "没能连上布谷,检查网络后再试试",
            style = MaterialTheme.typography.bodyMedium,
            color = H.Ink2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton(
            text = "重新连接",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** ACTIVE 绑定的对端称呼;未绑定时回落「家人」。 */
private fun activePeerName(bonds: List<BondDto>): String =
    bonds.firstOrNull { it.status == "ACTIVE" }?.counterpartNickname
        ?: bonds.firstOrNull()?.counterpartNickname
        ?: "家人"

/** 登录后的主框架(四 Tab)与二级页跳转。 */
@Composable
private fun MainHost(
    api: GeitaApi,
    user: UserDto,
    onUserUpdated: (UserDto) -> Unit,
    bonds: List<BondDto>,
    bondsLoading: Boolean,
    bondsError: Boolean,
    peerName: String,
    onRefreshBonds: () -> Unit,
    onLogout: () -> Unit,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    onOpen: (Screen) -> Unit,
    careMode: Boolean,
    onCareModeChange: (Boolean) -> Unit,
    permAsked: Boolean,
    tabStates: SaveableStateHolder,
    onSnack: (String) -> Unit,
) {
    val baseDensity = LocalDensity.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // 关怀模式只放大内容字号,不改密度;底部导航保持稳定高度,避免大字号挤压
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = baseDensity.fontScale * if (careMode) CARE_FONT_SCALE else 1f,
                ),
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 22 }) togetherWith
                            fadeOut(tween(120))
                    },
                    label = "tabTransition",
                ) { t ->
                    // 每个 Tab 一个状态容器:切走/回来、去权限页再返回,表单草稿都能续上
                    tabStates.SaveableStateProvider(t) {
                        when (t) {
                            Tab.DASHBOARD -> DashboardScreen(
                                api = api,
                                peerName = peerName,
                                onOpenHistory = { onOpen(Screen.History) },
                                onOpenCalendar = { onOpen(Screen.Calendar) },
                                onGoCreate = { onTabChange(Tab.CREATE) },
                            )

                            Tab.CREATE -> CreateReminderScreen(
                                api = api,
                                userId = user?.id ?: 0L,
                                peerName = peerName,
                                bonds = bonds,
                                bondsLoading = bondsLoading,
                                bondsError = bondsError,
                                needsPermission = !permAsked,
                                onRequestPermission = { onOpen(Screen.Permissions) },
                                onBack = { onTabChange(Tab.DASHBOARD) },
                                onOpenFriends = { onOpen(Screen.Friends) },
                                onRefreshBonds = onRefreshBonds,
                                onSnack = onSnack,
                                onCreated = { onTabChange(Tab.DASHBOARD) },
                            )

                            Tab.COMPANION -> CompanionScreen(
                                api = api,
                                bonds = bonds,
                                peerName = peerName,
                                onOpenFriends = { onOpen(Screen.Friends) },
                                onRefreshBonds = onRefreshBonds,
                                onSnack = onSnack,
                            )

                            Tab.PROFILE -> ProfileScreen(
                                api = api,
                                user = user,
                                bonds = bonds,
                                onUserUpdated = onUserUpdated,
                                onOpenHistory = { onOpen(Screen.History) },
                                onOpenFriends = { onOpen(Screen.Friends) },
                                onLogout = onLogout,
                                careMode = careMode,
                                onCareModeChange = onCareModeChange,
                                onSnack = onSnack,
                            )
                        }
                    }
                }
            }
        }
        BottomBar(current = tab, onSelect = onTabChange)
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val navPadding = WindowInsets.navigationBars.asPaddingValues()
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xF7FAF8F5))
            .padding(bottom = navPadding.calculateBottomPadding()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(Tab.DASHBOARD, current, onSelect, Modifier.weight(1f))
            val fabTouch = remember { MutableInteractionSource() }
            val fabPressed by fabTouch.collectIsPressedAsState()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(interactionSource = fabTouch, indication = null) { onSelect(Tab.CREATE) },
                contentAlignment = Alignment.Center,
            ) {
                // 按压缩放 + 轻微心跳脉动,让主操作更有呼吸感
                val fabScale by animateFloatAsState(
                    targetValue = if (fabPressed) 0.86f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                    label = "fabScale",
                )
                val beat = rememberInfiniteTransition(label = "fabBeat").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.04f,
                    animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
                    label = "beat",
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .graphicsLayer {
                                val s = fabScale * beat.value
                                scaleX = s
                                scaleY = s
                            }
                            .shadow(10.dp, CircleShape, spotColor = H.Coral.copy(alpha = 0.45f))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(H.CoralLight, H.Coral))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(26.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("新建", style = MaterialTheme.typography.titleSmall, color = H.Ink2)
                }
            }
            TabItem(Tab.COMPANION, current, onSelect, Modifier.weight(1f))
            TabItem(Tab.PROFILE, current, onSelect, Modifier.weight(1f))
        }
    }
}

/** 权限引导已展示过:持久化标记,后续创建不再拦截。 */
private fun markPermAsked(context: Context, onSet: (Boolean) -> Unit) {
    context.getSharedPreferences("bugu_main", Context.MODE_PRIVATE)
        .edit().putBoolean("perm_asked", true).apply()
    onSet(true)
}

@Composable
private fun TabItem(tab: Tab, current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    if (tab == Tab.CREATE) return
    val active = current == tab
    // 选中时颜色渐变 + 图标弹跳;文字使用深色变体保证 13sp 可读
    val iconTint by animateColorAsState(if (active) H.Coral else H.Ink3, tween(220), label = "tabIconTint")
    val labelColor by animateColorAsState(if (active) H.CoralInk else H.Ink3Strong, tween(220), label = "tabLabelColor")
    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabScale",
    )
    Column(
        modifier
            .fillMaxSize()
            .clickable { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, null, Modifier.size(24.dp).scale(scale), tint = iconTint)
        Spacer(Modifier.height(3.dp))
        Text(tab.label, style = MaterialTheme.typography.titleSmall, color = labelColor)
    }
}
