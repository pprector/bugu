# 布谷 Android UI 全量 Kuikly 迁移方案（取消 S1 双轨灰度）

> 日期：2026-11-06 ｜ 前提：App 未上架、无存量用户，取消页面级灰度，直接全量重写 Android UI
> 依据：POC 通过（`KUIKLY_POC_验证报告与迁移路线.md`）；本文件补充版本策略实测、页面批次与 Bridge 契约

---

## 一、结论先行

1. **版本策略选 A**：Kuikly UI 模块在独立构建里用 Kotlin 2.1.21 / AGP 7.4.2 编译成 AAR，宿主 App 保持 Kotlin 2.3.21 / AGP 9.4.1 / compileSdk 37.2 / Compose BOM 2026.09。**编译与打包已实测通过**。
2. **B（整 App 降到 2.1.21）不可行**：AGP 9.4.1 的 Kotlin 编译器最低是 2.2.x，强行钉 KGP 2.1.21 会在插件应用阶段直接报错；真要 2.1.21 必须退回 AGP 8.x，同时失去 compileSdk 37.2（Compose BOM 2026.09 依赖它）。
3. **页面分 5 批**：看板 → 登录/权限/Create → 历史/日历/好友/陪伴 → 我的/资料 → 下线 Compose。响铃页 `RingActivity`、闹钟链路、推送、深链、登录态存储全部保原生。
4. **壳工程已跑通**：Kuikly 宿主 + 看板页 + 原生拉真实接口（用户 0641 / 2 份提醒 / 3 位家人），模拟器截图 + logcat 0 FATAL。
5. 最大风险：Kotlin 2.1.21 与 2.3.21 的"双工具链孤岛"带来构建复杂度，以及 `CreateReminderScreen`（1635 行）的交互还原。

---

## 二、版本策略与编译证据

### 2.1 方案 A：Kuikly 模块独立编 AAR，宿主保持 2.3.21

**Kuikly 侧构建**（`kuikly-poc/KuiklyUI`，JDK 17.0.21 / Gradle 7.6.3 / AGP 7.4.2 / Kotlin 2.1.21）：

**构建前提（必读）**：本机默认 JBR 为 25，Gradle 7.6.3 的 `buildSrc` 用 Kotlin 1.7.10 编译，`JavaVersion.parse` 不接受 `25.0.4.1`，会直接报 `:buildSrc:compileKotlin` 失败。必须显式用工程内 JDK 17：

```
export JAVA_HOME=/Users/peng/Documents/phk/kuikly-poc/toolchain/jdk-17.0.21+9/Contents/Home
./gradlew :bugu-ui:assembleRelease
BUILD SUCCESSFUL
→ bugu-ui/build/outputs/aar/bugu-ui-release.aar
```

不设 `JAVA_HOME` 时即使只改一行 Kuikly 代码也无法产出 AAR，这是环境差异而非代码问题。

AAR 内 classes.jar 的元数据版本实测：

```
javap -v com/bugu/kuikly/ui/BuguRealDashboardPage.class
kotlin.Metadata  mv=[2,1,0]      # 2.1.21 编译
```

**宿主侧编译消费**（`kuikly-version-probe`，AGP 9.4.1 / 内置 Kotlin 2.3.21 / compileSdk 37.2 / Compose BOM 2026.09）：

```
./gradlew :probe:assembleRelease
BUILD SUCCESSFUL
kotlin-compiler-embeddable:2.3.21   # 编译器版本实查
kotlin-stdlib:2.3.21
```

探针源码直接引用 AAR 里的 `BuguRealDashboardPage`、`ComposeContainer`、`@Page`，产物 `probe-release.aar` 内含 `KuiklyArtifactProbe.class`，证明 **2.3.21 编译器可读 2.1.21 元数据并成功打包**。

> 证据文件：`kuikly-poc/probe-a-build.log`；探针工程 `/Users/peng/Documents/phk/kuikly-version-probe`。

**多 UI 模块的坑（已解决）**：两个 Kuikly 模块会各自生成同名的 `com.tencent.kuikly.core.android.KuiklyCoreEntry`，dex 合并直接失败（`Type ... is defined multiple times`）。解法是 KSP 多模块参数：

- 主模块：`enableMultiModule=true`、`isMainModule=true`、`subModules="buguui"`
- 子模块：`enableMultiModule=true`、`isMainModule=false`、`moduleId="buguui"`
- 主模块需在 `androidMain` 可见子模块（`implementation(project(":bugu-ui"))`）

### 2.2 方案 B：整 App 降到 2.1.21 —— 不可行

实测两步都失败：

| 尝试 | 结果 |
|---|---|
| 只把 Compose 编译器插件声明为 2.1.21 | 编译能过，但 `kotlinCompilerClasspath` 实查仍是 **2.2.10**（AGP 9 内置 KGP），并没有真正降到 2.1.21 |
| 额外 `buildscript` 钉 `kotlin-gradle-plugin:2.1.21` | **插件应用失败**：`Failed to apply plugin 'com.android.internal.library' ... Could not generate a decorated class for type KotlinAndroidTarget ... com/android/build/gradle/api/BaseVariant` |

原因：KGP 2.1.21 依赖 AGP 8 时代的 `BaseVariant` API，AGP 9.4.1 已移除。要真正用 2.1.21 需整体退回 AGP 8.x，而 `compileSdk = 37 / compileSdkMinor = 2` 与 Compose BOM 2026.09 是 AGP 9 能力，B 会把现有 App 工具链一起拖下水。

> 证据文件：`kuikly-poc/probe-b-force-kgp-fail.log`

### 2.3 选 A 的理由

- 宿主 App 的 2.3.21 / AGP 9.4.1 / Compose BOM 2026.09 一行不改，迁移期间原生壳（闹钟、响铃、推送、权限）继续按现有工具链演进；
- Kuikly 的 2.1.21 被隔离在独立构建，未来 Kuikly 升级只需换 AAR，不影响 App 主工具链；
- 代价：UI 改动需先出 AAR 再进宿主，调试链路多一步（用 `publishToMavenLocal` 或本地 repo 目录均可）。

---

## 三、页面清单与迁移批次

以 `app/src/main` 实际文件为准（共 8,839 行 Compose UI）：

| 页面 / 能力 | 文件 | 行数 | 处置 | 需要 Bridge |
|---|---|---:|---|---|
| 看板（首页 + 分段 Tab + 三出口） | `ui/screens/DashboardScreen.kt` | 926 | **Kuikly 重写** | 登录态、网络、路由、权限状态 |
| 登录 / 验证码 / 新用户引导 | `ui/screens/AuthScreens.kt` | 963 | **Kuikly 重写** | 登录态、网络、深链待处理码 |
| 权限引导（通知/精确闹钟/后台限制） | `ui/screens/AuthScreens.kt:739` | — | **Kuikly 重写**（壳内页面，能力原生） | 权限状态/申请 |
| 新建提醒 | `ui/screens/CreateReminderScreen.kt` | 1635 | **Kuikly 重写** | 网络、闹钟重排 |
| 历史 | `ui/screens/HistoryScreen.kt` | 260 | **Kuikly 重写** | 网络、路由 |
| 日历 | `ui/screens/CalendarScreen.kt` | 353 | **Kuikly 重写** | 网络 |
| 好友列表 | `ui/screens/FriendsScreen.kt` | 434 | **Kuikly 重写** | 网络、路由 |
| 陪伴/好友详情 | `ui/screens/CompanionScreen.kt` | 495 | **Kuikly 重写** | 网络、路由 |
| 我的 | `ui/screens/ProfileScreen.kt` | 550 | **Kuikly 重写** | 登录态、网络、权限状态 |
| 资料详情 | `ui/screens/ProfileDetails.kt` | 602 | **Kuikly 重写** | 登录态、网络 |
| 全屏响铃页 | `ui/RingActivity.kt` + `screens/AlarmScreen.kt` | 435 + 419 | **保原生**（WakeLock、锁屏直达、alarm_notice/alarm_loop、ack 队列） | 反向：Kuikly 只调用 `ring.open(fireId)` |
| 闹钟链路 | `schedule/AlarmScheduler.kt`、`AlarmReceiver.kt`、`ReminderSync.kt` | — | **保原生** | `alarm.resync()` |
| JPush / 通知渠道 `bugu_alarm_ring_v2` | `push/*`、`AlarmNotifications.kt` | — | **保原生**（另一会话正在改，不碰） | 无 |
| 深链 `bugu://bond` | `deeplink/InviteDeepLink.kt` | — | **保原生** | 待处理码读取/清除 |
| 登录态存储 | `client/api/TokenStore.kt` | — | **保原生** | token 读写清 |

**批次划分**

| 批次 | 内容 | 退出标准 |
|---|---|---|
| B0（已完成） | 壳工程 + 看板页真实数据 | 可编译安装、截图、0 FATAL |
| B1 | 登录/验证码/引导 + 权限引导 + 深链回填 | 登录 → 看板全链路在 Kuikly 内跑通 |
| B2 | 新建提醒 + 历史（含 CreateReminderScreen 的提醒人/时间/语气/语音分段） | 创建 → 服务端落库 → 闹钟重排 → 历史可见 |
| B3 | 日历 + 好友 + 陪伴详情 | 列表/详情/返回链路对齐 |
| B4 | 我的 + 资料详情 | 资料修改、退出登录、权限入口 |
| B5 | 删除 Compose 页面与依赖，仅保留原生壳 | APK 体积与启动时间不劣化，响铃链路回归通过 |

---

## 四、Bridge 契约

统一命名空间 `bugu.*`，全部异步（Promise 回调 `onSuccess(result)` / `onFailure(code, message)`），主线程调用，失败必须回调而不是静默。

### 4.1 登录态

| 方法 | 入参 | 出参 | 失败表现 |
|---|---|---|---|
| `bugu.auth.token` | 无 | `String`（未登录为 `""`） | 读取异常 → `AUTH_STORAGE_ERROR` + 空串 |
| `bugu.auth.save` | `token: String` | `Boolean` | 写失败 → `AUTH_STORAGE_ERROR`，页面提示重新登录 |
| `bugu.auth.clear` | 无 | `Boolean` | 幂等，失败仅打日志 |
| 事件 `bugu.auth.expired` | — | — | 任意接口 401 时原生广播，Kuikly 跳登录页 |

### 4.2 路由

| 方法 | 入参 | 出参 | 失败表现 |
|---|---|---|---|
| `bugu.router.open` | `page: String, params: Map` | `Boolean` | `ROUTE_NOT_FOUND`，不跳转并 toast「页面不存在」 |
| `bugu.router.close` | 无 | `Boolean` | 根页时 no-op |
| `bugu.router.replace` | `page, params` | `Boolean` | 同上 |

### 4.3 响铃入口

| 方法 | 入参 | 出参 | 失败表现 |
|---|---|---|---|
| `bugu.ring.open` | `fireId: Long` | `Boolean` | `RING_NOT_FOUND`（提醒已撤销/已 ack）→ 刷新看板；不得在 Kuikly 内自绘响铃 |
| `bugu.ring.state` | 无 | `{ringing: Boolean, fireId: Long}` | 读取失败返回 `{ringing:false}` |

响铃页仍是 `RingActivity`，`AlarmManager` 的 `ACTION_FIRE` / `dumpsys alarm` 语义完全不变；Kuikly 只作为入口与状态展示。

### 4.4 通知 / 精确闹钟 / 后台限制

| 方法 | 入参 | 出参 | 失败表现 |
|---|---|---|---|
| `bugu.permission.status` | 无 | `{notification: Boolean, exactAlarm: Boolean, batteryUnrestricted: Boolean}` | 异常 → 全 false |
| `bugu.permission.request` | `kind: "notification"｜"exactAlarm"｜"battery"` | `{granted: Boolean, needSettings: Boolean}` | `PERMISSION_UNSUPPORTED`（系统无对应能力）；`needSettings=true` 时原生拉起系统页 |

### 4.5 网络与错误

| 方法 | 入参 | 出参 | 失败表现 |
|---|---|---|---|
| `bugu.net.get` | `path: String`（已带 `/api/v1` 前缀） | `ApiEnvelope.data`（JSON 字符串） | `NET_TIMEOUT(8s)`、`NET_UNREACHABLE`、`HTTP_<code>`，`message` 取服务端 `message` |
| `bugu.net.post` | `path, bodyJson` | 同上 | 同上；4xx 不重试 |
| 401 处理 | — | 清除 token + 广播 `auth.expired` | Kuikly 统一跳登录 |

### 4.6 深链待处理码

| 方法 | 入参 | 出参 | 失败表现 |
|---|---|---|---|
| `bugu.deeplink.takeCode` | 无 | `String?`（取后不清除） | 无待处理码返回 `""` |
| `bugu.deeplink.clearCode` | 无 | `Boolean` | 幂等 |

深链由原生 `InviteDeepLink.handle()` 落到 `SharedPreferences("bugu_main").pending_invite_code`，Kuikly 登录页挂载时调用 `takeCode` 回填验证码/邀请码输入框。

### 4.7 闹钟重排

| 方法 | 入参 | 出参 | 失败表现 |
|---|---|---|---|
| `bugu.alarm.resync` | 无 | `Boolean` | `ALARM_PERMISSION_DENIED` 时页面展示精确闹钟引导；其余失败走 WorkManager 重试 |

---

## 五、已完成 / 未完成

**已完成**
- 方案 A 编译证据（Kuikly 2.1.21 AAR → Kotlin 2.3.21 宿主消费 + 打包成功）
- 方案 B 不可行证据（AGP 9.4.1 无法使用 KGP 2.1.21）
- 多 Kuikly 模块入口冲突修复（KSP `enableMultiModule/moduleId`）
- 壳工程：宿主 `BuguShellActivity` 原生拉真实接口 → pageData 传 Kuikly 看板页
- 模拟器 `kuikly36` 安装运行，截图 `kuikly-poc/shot_shell_dashboard.png`
- logcat 0 FATAL
- 页面清单 / 批次 / Bridge 契约（本文档）

**未完成 / 未验证**
- 壳运行在 2.1.21 宿主（KuiklyUI 官方 demo 工程）；**A 方案的 2.3.21 宿主只验证到编译打包，未在真机/模拟器跑通渲染**（下一步把壳模块迁到 `kuikly-version-probe` 做运行时验证）
- 仅看板页完成；登录、新建、历史、日历、好友、我的等 8 个页面尚未开始
- iOS / 鸿蒙端未动（本机无 Xcode，无法编译验证）
- 原生壳（闹钟、响铃、推送、权限）未做 Bridge 封装，契约目前是设计稿
- 文案以当前代码为准（统一「提醒」），壳页已按此输出

---

## 六、最大风险

1. **双工具链孤岛**（高）：UI 改动必须"改 Kuikly 模块 → 出 AAR → 宿主重编"，调试链路长；版本漂移时 AAR 与宿主不一致最难查。
2. **CreateReminderScreen 还原**（高）：1635 行含时间轮盘、语气/语音、提醒人选择、校验与错误态，是所有页面里最重的交互，需单独排期。
3. **Compose DSL 仍为 Beta**（中）：POC 已踩到 `RowScope.weight` + 覆盖层命中测试失效；页面越多，越可能遇到布局/动画 API 缺口，需保留自研 DSL 逃生门。
4. **AGP 9 宿主运行 AAR 未做运行时验证**（中）：目前只证明编译打包通过，Kuikly 渲染库在 AGP 9 宿主上的 assets/jniLibs/资源合并行为尚未在设备上验证。
5. **原生能力边界**（低-中）：响铃/闹钟/推送保原生是正确的，但 Bridge 的 401、权限拒绝、锁屏直达等失败路径需要在 B1 前补齐端到端用例，否则容易在联调期集中爆雷。

---

## 七、发布前提：AAR 本地 POM 必须补齐传递依赖

`KuiklyUI` 产出的 `bugu-ui-release.aar` 会被发布成本地 maven 构件（`repo/com/bugu/kuikly/bugu-ui/0.1.0/`）。**首次运行时验证曾因 POM 缺依赖直接白屏**：`NoClassDefFoundError: kotlinx/atomicfu/AtomicFU`。

发布或升级 AAR 时必须同步维护以下 POM，否则宿主编译能过、运行时必崩：

- `compose-2.1.21-local.pom` 需显式声明：`kotlinx-atomicfu 0.25.0`、`kotlinx-coroutines-core 1.8.1`、`androidx.annotation 1.9.1`、`androidx.collection:collection-internal 1.7.3`、`compose runtime/runtime-saveable 1.7.3`、`androidx.profileinstaller 1.3.1`。
- `core-render-android-*.pom` 需补：`recyclerview 1.1.0`、`appcompat 1.2.0`、`dynamicanimation 1.0.0`。
- `bugu-entry-0.1.0.pom` 需声明依赖 `bugu-ui`、`compose`、`core`。
- 任何一次 AAR 重编后，宿主侧必须 `--refresh-dependencies` 重解析，否则 Gradle 可能复用旧缓存 AAR（版本号不变时尤其隐蔽）。

**验收口径**：安装宿主 APK 后 `logcat` 无 `NoClassDefFoundError` / `ClassNotFoundException`，且 Kuikly 页面正常渲染（`onPageLoadComplete isSucceed=true, mode=JVM`）。
