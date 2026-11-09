# Tasks：B4（我的 + 资料详情 + 陪伴独立设置）

## T1 Kuikly 侧（bugu-ui）
- [x] `BuguProfilePage.kt`：真实资料（昵称/手机号脱敏/账号类型/亲友数）+ 三列统计（发出/收到/本月完成）
- [x] 昵称编辑：`PATCH /auth/me`，1..32 字校验，成功 banner + 页面即时更新
- [x] 大字关怀模式：读写 `SharedPreferences("bugu_main").care_mode`（与原生同键），本页字号 x1.12
- [x] 菜单入口：我的提醒 / 权限诊断 / 帮助反馈 / 关于；退出登录 inline 确认 → 清 token → 回登录页
- [x] `BuguProfileDetailsPage.kt`（`params.page` 三合一）：permissions / feedback / about
- [x] 权限诊断：通知、精确闹钟、后台限制三项状态；testNotify 拒绝返回 `PERMISSION_DENIED`，授权后成功弹出通知
- [x] 反馈：列表 + 提交（`POST /feedback`：category/content/appVersion）+ FAQ
- [x] 关于：`appVersion` 取真实 versionName；用户协议/隐私政策 inline 展开
- [x] `BuguCompanionPage.kt` 补齐独立设置：静音单次提醒（PUT/DELETE `/reminder/mute`）、长辈模式（`POST /bond/elder-mode`）、私人备注（`PUT /bond/{id}/remark`）、解除关联（`POST /bond/{id}/unbind`）
- [x] 共享件 `jsonString(raw)` 放共享文件 internal，供昵称/备注复用

## T2 宿主侧
- [x] `BuguBridgeNativeModule`：`netRequest`（GET/POST/PUT/PATCH/DELETE）、`careModeGet/Set`、`appVersion`、`testNotify`
- [x] `KNOWN_PAGES` 扩到 10 页；`ProbeHostActivity` 支持 `--es page` 直开两页 + `--es detail` 传 `page` 参数
- [x] `:bugu-ui:assembleRelease` → 复制 AAR 到本地 repo → `:probe:assembleDebug --refresh-dependencies` → 安装

## T3 设备验证（AVD haohao36）
- [x] V1 我的页：`BuguFriend / 154****0641 / 3 位亲友`、统计 2 发出 / 2 收到 / 0 本月完成（v1_profile.png）
- [x] V2 昵称：编辑 → 输入 → PATCH 200 → 成功 banner（v2a/v2b/v2c）
- [x] V3 大字关怀：开启后字号放大、Prefs 落盘（v3a/v3_care_mode）
- [x] V4 权限诊断：三项状态 + 拒绝 `PERMISSION_DENIED` + `pm grant` 后 testNotify 成功、通知栏可见（v4/v4a/v4b/v4c）
- [x] V5 反馈：26 字提交 → 列表出现「提醒问题/待处理」（v5/v5b/v5c）
- [x] V6 陪伴设置：静音 PUT 200、长辈模式 POST 200（DB `elder_mode=t`）、备注 PUT 200、解绑 200（4 人 → 3 人）（v6a–v6g）
- [x] V7 关于：`appVersion=0.1.0`、协议/隐私展开（v7/v7b）
- [x] V8 退出登录：token 清空（`bugu_probe.xml` 为 `<map />`）→ `BuguLoginPage`（v8_logout_login）
- [x] 全程 `FATAL EXCEPTION=0` / `ClassNotFoundException=0` / `NoClassDefFoundError=0`；4 次 `onPageLoadComplete isSucceed=true`（logcat_b4.txt）
- [x] 模拟器已 `emu kill`，`adb devices` 为空

## T4 收尾
- [x] 迁移文档补「七、发布前提：AAR 本地 POM 必须补齐传递依赖」
- [x] B4 里程碑报告；未 commit / push / 部署；未动 `res/**`、`AlarmNotifications.kt`、`LocalNotificationTest.kt`、`website/`、`admin/`、`brand/`、`version.properties`

## 未验证 / 留后续
- `BondDto` 不返回 `counterpartElderMode`，对方长辈模式初始态无法跨设备读取；页面以本次操作为准（需服务端补字段）
- 长辈模式对 NORMAL 账号返回 403「仅长辈免注册账号的邀请方可代办」，属服务端合法限制，已用 ELDER 测试账号闭环
- 真实头像换图、系统分享面板、语音识别、FlashRow/RiseIn 动画、草稿保存恢复、真宿主 ReminderSync、iOS/鸿蒙

## B5 段二前置：Compose 响铃行为基线（已录，作为原生 View 等价基线）
- [x] 冷启动直达：`RingActivity` 直接在栈顶，无闪屏
- [x] 动机→循环：`notice done; loop start` 间隔约 1.2s（14:04:18.7 → 14:04:19.843）
- [x] 循环响 ≥60s：13:55 起持续到 14:03 右滑确认，期间倒计时停在 `00:00`、无自动停止
- [x] ack 立即停：右滑后 Activity 结束、`dumpsys power` Wake Locks `size=0`
- [x] 音频焦点：`GAIN_TRANSIENT` + `usage=USAGE_ALARM`；WakeLock `Bugu:alarmRing`
- [x] 文案基线：现版含「若 40 秒未确认，咕咕会再轻轻叫你一次」（段二按产品口径改为「会一直响到你确认」）
- 证据目录：`shots-b5-baseline/`（base_ring_t3.png / base_ring_t60.png / base_ring_start2.png）

## B5 段一：真实 App 接入 Kuikly 壳（已完成）
- [x] `app/repo` 接入 Kuikly 2.1.21 本地 AAR 仓库；`settings.gradle.kts` + `build.gradle.kts` 依赖（bugu-entry / bugu-ui / core / compose / core-annotations / core-render-android）
- [x] debug 变体 `BuildConfig.API_BASE_URL=http://10.0.2.2:8081` + `src/debug/AndroidManifest.xml` 明文放行；release 仍为生产域名
- [x] `BuguBridgeNativeModule` 移植：token 复用 `bugu/jwt`、netRequest（401 清 token + 广播）、careMode、appVersion、testNotify、permissionStatus/Request、takeCode/clearCode、ringOpen、alarmResync、routerOpen
- [x] `BuguKuiklyHost`：页面装载/路由栈/看板 payload（IO 线程）/会话恢复（me() 校验）/冷热深链/返回键双击退出
- [x] 登录成功联动 PushReporter + ReminderSync（与 AppRoot 一致）；退出登录清本地闹钟状态
- [x] `MainActivity` 改壳；`AppRoot.kt`、`AlarmScreen.kt`、`Theme.kt`、`RingActivity.kt` 全部保留，Compose 依赖与 `plugin.compose` 未动
- [x] `BuguFriendsPage` 支持 `params.code` 深链预填（AAR 重编并 `--refresh-dependencies`）
- [x] 设备验证（AVD haohao36）：
  - 冷启动无 token → `BuguLoginPage`（s1_login.png）
  - 注入登录态冷启动 → `me()` ok → 看板真实数据（s2_dashboard2.png / s14_resume.png）
  - 十页逐页直开全部 `isSucceed=true`：看板 / 登录 / 权限引导 / 新建 / 历史 / 日历 / 亲友 / 陪伴 / 我的 / 资料详情（`shots-b5/p_*.png`、`p2_*.png`）
  - 冷启动深链 → 登录页邀请码回填 `654321`（s10_deeplink_cold.png）
  - 热深链（已登录）→ 亲友页预填并查询，假码提示「邀请码无效」（s11_deeplink_hot_friends.png）
  - 退出登录 → token 清空（`bugu.xml` 为 `<map />`）→ 回登录页（s12_logout_login.png）
  - 后台 + 锁屏 + 解锁 + 返回 → 看板存活、数据未丢（s13/s14）
  - 响铃回归冒烟：壳版宿主内 `RingActivity` 正常拉起，右滑 ack 后 Wake Locks `size=0`（s15_ring_smoke.png）
  - `FATAL EXCEPTION=0`、`ClassNotFoundException/NoClassDefFoundError=0`（`shots-b5/logcat_b5_*.txt`）
- 已知问题（不阻塞段二，需在段三前处理）：
  - 看板只有「新建提醒」一个出口，其余页面在真实 App 内无 UI 入口（段一用 debug 直开入口 `--es bugu_page` 验证，等价探针壳）
  - 页面首帧到数据齐全约 6–9s（四路 bridge 请求串行展示加载态）
  - 新建页时间 chip 右缘裁切（Kuikly 页面原有布局问题）

## B5 段二：响铃页改原生 View（已完成）
- [x] `RingActivity` 去掉 `setContent` / `BuguTheme` / Compose 状态，改 `setContentView` 原生视图；仍保留 `ComponentActivity`、extras 契约、单 Activity 队列、音频焦点 / WakeLock / 震动 / TTS / ack 收尾逻辑
- [x] 新增 `app/src/main/kotlin/com/bugu/ui/RingScreenView.kt`：纯代码构建（零 layout XML / 零 drawable / 零 style）
  - 色板常量与 `Theme.H` 逐项一致（Canvas / White / Hairline / WarmFill / Track / Ink / Ink2 / Ink3 / Ink3Strong / CoralInk / IndigoInk / Coral / Indigo / Mint / MintDeep / CoralTint / IndigoTint）
  - 图标（闪电 / 心 / 录音 / 信息 / 播放暂停 / 右滑箭头 / 咕咕 RING 形象 / 波形）全部 Canvas 绘制
  - 尺寸 dp、字号 sp；内容整体 ScrollView，右滑轨道高度按标签行高自适应（最小 64dp）
- [x] 文案修正：「若 40 秒未确认，咕咕会再轻轻叫你一次」→「会一直响到你确认」（仅此一句，未新增自动再叫）
- [x] 全屏适配：`setDecorFitsSystemWindows(false)` + WindowInsets 留边；状态栏/导航栏透明、浅色图标、关闭导航栏对比蒙层
- [x] 设备验证（AVD haohao36，`shots-b5-ring/`）：
  - 真实接收器 fire（锁屏状态、app UID 广播 `com.bugu.ACTION_FIRE`）→ 全屏直达 RingActivity（`n11_receiver_locked.png`）
  - 循环响 ≥60s：持续 >90s，倒计时停在 `00:00`，无自动停止（`n3_ring_1x_60s.png`）
  - ack 立即停：右滑后音频焦点栈清空、`Bugu:alarmRing` WakeLock 释放、Activity 结束
  - 队列接管：两条真实 fire 时 ack 第一条后 `advance immediately to fire=1011`，无静音空拍（`n12_queue_advance.png`）
  - 锁屏直达：灭屏后拉起即显示，`isKeyguardShowing=false`（`n5_ring_lockscreen.png`）
  - 字体四档：0.85（`n6_font_085.png`）/ 1.0（`n2_ring_1x.png`）/ 1.3（`n7_font_130.png`）/ 2.0（`n8_font_200_top.png`、`n9_font_200_scrolled.png`）
    - 四档右滑把手均完整可见且可拖拽成功；2.0 长标题完整换行不截断；2.0 内容滚动后右滑控件与底部文案完整
  - 动机 → 循环日志：`notice done; loop start` 单条同步衔接（两个播放器均预加载，循环在同一回调内 start）

## B5 段三：删除 Compose（已完成，commit `5a65d39` 为删除前基线）
- Kotlin（15 个，全部为 Compose UI，删除后确认无其他引用）：
  - `app/src/main/java/com/bugu/ui/AppRoot.kt`
  - `app/src/main/java/com/bugu/ui/theme/Theme.kt`
  - `app/src/main/java/com/bugu/ui/common/Ui.kt`
  - `app/src/main/java/com/bugu/ui/common/BuguComponents.kt`
  - `app/src/main/java/com/bugu/ui/common/ReminderStatus.kt`
  - `app/src/main/java/com/bugu/ui/screens/AuthScreens.kt`
  - `app/src/main/java/com/bugu/ui/screens/DashboardScreen.kt`
  - `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt`
  - `app/src/main/java/com/bugu/ui/screens/HistoryScreen.kt`
  - `app/src/main/java/com/bugu/ui/screens/CalendarScreen.kt`
  - `app/src/main/java/com/bugu/ui/screens/FriendsScreen.kt`
  - `app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt`
  - `app/src/main/java/com/bugu/ui/screens/ProfileScreen.kt`
  - `app/src/main/java/com/bugu/ui/screens/ProfileDetails.kt`
  - `app/src/main/java/com/bugu/ui/screens/AlarmScreen.kt`
- 构建配置（`app/build.gradle.kts`）：
  - `plugins { kotlin("plugin.compose") }`
  - `buildFeatures { compose = true }`
  - Compose BOM 2026.09.00、activity-compose、ui、foundation、material3、material-icons-core、material-icons-extended、ui-tooling
- 说明：上轮口径的「17 个文件」含 `MainActivity.kt` 与 `RingActivity.kt` 两份 Compose 内容，二者本轮已重写为壳入口 / 原生 View，不删除文件本身。
- 结果：15 文件 + Compose 插件/BOM/依赖已删，`assembleDebug`/`assembleRelease` 通过，dex 内 Compose UI 类为 0，已全量回归（0 FATAL）。

## B5 段四：新旧版对照审计与对齐（进行中）

### 审计（已交付，待确认）
- [x] 用 `git show 5a65d39:<path>` 导出 15 个旧 Compose 文件到只读基线 `/tmp/bugu-compose-baseline/`（未 checkout，工作区删除状态未动）
- [x] 逐页对读旧 Compose ↔ 新 Kuikly（字段/数据来源、区块顺序、文案原文、空态错误态、入口与交互结果、间距字号）
- [x] 产出逐页差异清单（12 页，P0×9 / P1×48 / P2×5），每条带旧文件行号：`B5_对照审计_差异清单.md`
- [x] P2 单列「可接受的差异」并逐条给理由，未完成项（头像/品牌鸟/字体/分享/通知渠道）不进 P2
- [x] 用户确认清单、放行第一批修复

### 第一批 P0（逐页修，逐页给旧/新并排截图）
- [x] 看板：收到提醒三出口（完成了/稍后15分/无法完成）+ 我发出的撤销 + 进行中响铃卡
  - 验证：`/reminder/ack` 稍后 → 新 fire、状态 SNOOZED；`DELETE /reminder/{id}` 仅 PENDING 可撤销；`/reminder/current` 实时卡三出口可用。
- [x] 新建：时间范围恢复（任意未来日期 + 任意分钟）、草稿保存/恢复、权限联动与闹钟重排失败提示
  - 验证：月历选 11-10、05:30 可设并能创建；草稿自动保存/「继续编辑」恢复/创建后清除；`permissionStatus` 驱动按钮文案，`alarmResync` 失败停在本页并给 Rose Banner。
- [x] 登录：协议勾选 + 用户协议/隐私政策全文入口（合规）
  - 验证：未勾选点发码弹出「需要你的同意」，同意后自动勾选并继续；全文逐字复用旧版；勾选行位于登录按钮下方，文案「已阅读并同意」。
- [x] 陪伴详情：`为 X 设一条提醒`入口
  - 验证：入口卡片文案与旧版一致，点击进入新建页并预选对方、标题「提醒{昵称}」。
- [x] 我的：头像更换（相册选择 + 上传 + 即时预览）
  - 验证：`pickAvatar` 走「选图→avatar/token→COS 直传→confirm」，上传后头像即时预览并提示「头像已更新」。

### 第二批 P1（按页收口）
- [x] 看板：分段 Tab、通知权限横幅、时钟卡、周历、行信息（类型/语气/重复/相对日）、空态动作、调试条、刷新与错误重试
  - 验证：时钟卡（HH:mm + 7 天统计）、`待我回应(n)/我的提醒(n)` 分段、日程周历、`刷新看板`、错误重试均已呈现；调试条已移除；行信息透传服务端 `kind/tone/repeatRule`。
- [x] 新建：去绑定/失败重试、语音输入、默认标题随接收人、接收人横滑与头像、方式说明、时间已过 inline 与文案度量
  - 验证：办理握手态「正在检查互伴…」/失败重试、默认标题随接收人、接收人横滑与头像+选中勾、方式说明与「展开」、语音入口、占位与字段红字、右下「咕咕帮填」、成功提示均已呈现；设备截图 `shots-b5-audit/v_create_top2.png`、`v_create_mid3.png`。
- [x] 全局：GuguBird 完整插画、Plus Jakarta/Space Grotesk 字体、状态文案与语义色、TopBar 组件
  - 验证：`BuguFontAdapter` 运行时日志 `family=plus_jakarta_sans loaded=true`、`family=space_grotesk loaded=true`；页头 64dp 圆返 + 21sp 标题 + 40dp 咕咕；状态 chip `待触发/正在呼唤` 颜色与旧版一致；`v_dash_clock2.png`。
- [x] 登录 L2–L11（60s 倒计时按 `code_sent_at` 持久化、字段级红字、`+86` 与 3-4-4、满 11 位自动聚焦、`登录 / 注册`、品牌区 88dp 圆底 + 72dp 咕咕、移除未接线邀请码、满 6 位自动提交、900ms 庆祝、发码文案）
  - 设备证据（5556）：`v_login_top2.png`、`v_login_error3.png`、`v_login_countdown.png`、`v_login_phone_slow2.png`、`v_login_autosubmit.png`
  - 仅代码级：L9 全屏庆祝（需真实登录）、L10 发码成功文案（未真实发短信）；L8 系统 OTP 自动填充未做（Kuikly String 版 BasicTextField 无 smsOtp semantics，待框架/原生桥）
- [x] 权限引导 G1–G5（TopBar 返回、双按钮、108/96dp 大插画、旧版文案、ON_RESUME 刷新）
  - 设备证据：`v_perm_full.png`、`v_perm_bottom3.png`；G3 仅代码级（`pageDidAppear` 重读状态）
- [x] 历史 H1–H4（对方姓名方向、类型+重复+时刻、空态插画、TopBar）
  - 设备证据：`v_history_final.png`（`我 → 用户0410` / `用户8000 → 我`）；H3 空态插画仅代码级（测试账号历史非空）
- [x] 日历 K1–K4（方向姓名、完整重复、空态插画、44dp 月份按钮、TopBar 无副标题）
  - 设备证据：`v_calendar_final.png`；K2 空态插画仅代码级（模拟器点击日期格未命中，环境手势问题）
- [ ] 亲友 F1–F11 / 陪伴列表 P1–P4 / 资料详情 / 响铃：逐页文案与度量对齐

### 约束（不变）
- 不 commit / 不 push
- 禁区不碰：`res/**`、`AlarmNotifications.kt`、`LocalNotificationTest.kt`、`website/`、`admin/`、`brand/`、`version.properties`
- `haohao36` 留给产品试用；对照用 `kuikly36`

## B5 段五：5554 release 真实短信登录验收 + 登录页修复（进行中）

对应 `spec.md §8`。**全程真实短信登录链路**（手机号 156 1788 9809 / 验证码 015565，已登录）。

### 5.1 登录与基线（已完成）
- [x] 5554 release 真实发码 → 倒计时 → 输入 015565 → 登录成功 → 权限引导 → 看板
- [x] 记录 versionCode **4** / versionName **1.1.2** / 设备时间 `2026-11-09 12:31 CST`
- [x] `bugu.xml` 含 `jwt`，长度 208（不贴明文）
- [x] 登录页 `release_re_cold_start.png`、`release_re_login_clean.png`；权限引导 `l2.png`、`l3.png`（待归档）；看板 `rel_01_dashboard.png`

### 5.2 十一页真实点击（逐页四要素：可达/返回/关键元素/异常）
- [x] 看板：三出口可见；撤销实测成功（DELETE /reminder/50 200 + alarmResync）；进行中响铃卡显示 2026-11-04 过期 fire，「完成了」返回 409「当前状态不可确认」；四出口/时钟卡/分段/空态/周历齐全（`rel_01_dashboard.png`、`rel_02_dashboard_mine.png`、`rel_04_undo.png`、`rel_03_ack_done.png`）
- [x] 新建：看板「我的提醒」段下滑后「+ 新建提醒」进入；系统返回键回看板；接收人/标题/时间/方式/便签/咕咕帮填/创建齐全；页头随内容滚走（`rel_07_create.png`、`rel_08_create_bottom.png`、`rel_09_create_foot.png`、`rel_11_back_dashboard2.png`）
- [x] 历史：返回看板；`我 → 自己` 方向、类型+重复+时刻、51 条分组齐全（`rel_12_history.png`）
- [x] 日历：返回看板；月份切换 11→12 月正常；点 3 号显示 2 条、点 10 号显示空态插画；空态由设备实测（`rel_13_calendar.png`、`rel_17_calendar_day3.png`、`rel_18_calendar_day10_empty.png`）
- [x] 亲友：返回看板；邀请码 523881/到期/复制/分享/刷新、接受邀请输入、已关联亲友 0 人空态；与看板「已和 1 位家人连上」不一致（`rel_20_friends.png`）
- [ ] 陪伴列表：**不可达**——亲友页 ACTIVE 亲友为 0，无互伴卡可进；未验
- [ ] 陪伴详情 PD4：**未验/代码级**——无 ACTIVE 关系，无法进入；代码分支已存在
- [x] 我的：M1 头像/昵称/脱敏手机号/修改昵称；M2 互伴卡；M3 菜单顺序与图标；M4 大字关怀 BrandSwitch 开/关实测（careMode.set true→false）；M5 编辑器展开并取消；M6 诊断/反馈/关于；M7 退出登录入口可见（未触发，保留会话供产品点击）；系统返回键在本页会重载本页而非回看板（`rel_22_profile.png`、`rel_23_profile_bottom.png`、`rel_28_care_on2.png`、`rel_30_nickname_edit2.png`）
- [x] 资料详情：permissions/feedback/about 三页均实测可达且返回正常（`rel_24_care_on.png`（实为权限诊断）、`rel_25_feedback.png`、`rel_26_about.png`）
- [x] 登录：本轮真实短信链路已走完；退出登录未触发以免丢会话
- [x] 权限引导：登录后自动进入，三权限卡 + 双按钮齐全（`l2.png`、`l3.png`）

### 5.3 响铃（真实 fire，已完成）
- [x] 13:15:00 `AlarmManager` 真实拉起 `RingActivity`（`START ... from uid 10163`、`Displayed +546ms`，非 `am start` 注入）
- [x] 13:15:02.098 `BuguRing: notice done; loop start`；13:16:32（+92s）仍前台、循环持续 ≥60s（`ring_t65.png`）
- [x] 13:16:47 ack 即停：`REL Bugu:alarmRing`、`Wake Locks: size=0`、音频焦点栈空、topResumed 回 `MainActivity`（`ring_ack.png`）
- [x] snooze 真实调度：服务端 `nextFireAt` → `RTC_WAKEUP com.bugu.ACTION_FIRE @ 13:31:48` → 13:31:54 再次真实全屏拉起（`ring2_t0.png`）
- [x] 右滑「我已完成」→ `/reminder/ack` 落库；历史回执 13:15 条由「稍后再叫」翻为**已完成**（`ring2_history_done.png`）
- [x] `dumpsys` 证据：`Wake Locks: size=0`、`Audio Focus stack entries` 为空

### 5.4 收尾（已完成）
- [x] `FATAL EXCEPTION=0` / `E AndroidRuntime=0` / `ANR=0`（com.bugu 均为 0，logcat 16575 行）
- [x] 白屏 / 闪退 / 卡死：均未出现；5554 保持运行、未关闭、未重装
- [x] 问题三级分类见本轮报告（每条附截图文件名）

### 5.5 验收后：协议门改模态弹窗（已完成）
- [x] `BuguLoginPage.kt`：`ConsentGate` 内联卡 → `ConsentGateDialog`（`com.tencent.kuikly.compose.ui.window.Dialog`），`dismissOnClickOutside=false`、`usePlatformDefaultWidth=false`、`scrimColor=黑 0.6`、`inWindow=false`、`dismissOnBackPress=true`
- [x] 框架限制与修正：Kuikly `Dialog` 可用；但宿主 `BuguKuiklyHost` 用 `activity.onBackPressedDispatcher` 抢在页面之前消费返回键，导致 Dialog 内 `BackHandler` 收不到事件（已实测：`inWindow=true` 会回抛 `pageBack`，退栈到看板再 `routerReplace` 回登录页、输入被清空）。修正：`onBack()` 先 `delegator.onBackPressed()`，页面消费则不再走路由回退
- [x] 5556 验收：弹窗出现（`dlg_17`）→ 点遮罩不误触不关闭且 0 次 `send-code`（`dlg_18`）→「暂不」关闭且 0 次发码（`dlg_19`）→「同意并继续」才 `agreeSet=true` + `/auth/send-code` 200 + 倒计时（`dlg_20`）→ 返回键仅关弹窗、停留登录页且手机号保留（`dlg_16`）
- [ ] 5554 release 统一重装（按最新指令暂缓：5554 保持运行、待输入框改法一起重装）

### 5.6 登录输入框诊断（只诊断不改）
- [x] 5556 三态截图：正常输入 `diag_fast_phone2.png` / 字段级红字 `diag_error_empty.png` / 手机号 11 位与验证码 6 位边界 `diag_boundary_11_6.png`
- [x] `adb input text` 吞字符确认：极速输入可成功（`138 0138 0000`），人工 0.5s/位逐位输入 `15617889809` 复现错字（变成 `156 7889 8100`，且末两位漏进验证码框）——根因是逐键重格式化导致光标/值错位，不是单纯速度
- [x] 补充证据（11-09 13:25）：极速 `input text 15617889809` 本次也错成 `156 7889 0981`（11 位但顺序错），说明错位不只发生在慢速；`13800138000` 极速输入则正确——与号码内容里重复数字/分组位置有关（`dlg_04_gate_modal.png`）
- [x] 结论：红字出现时布局未撑开/未裁切；DEL 清空不可靠；满 11 位自动跳焦点会把残余按键写入验证码框
- [x] 产品已定改法：方案 A（`spec.md` §9.1）

## 段六：四条问题修复（5556 验收 → 统一重装 5554）

### 6.1 登录框 3-4-4 光标映射（方案 A）
- [x] `BuguLoginPage.kt` 手机号改 `TextFieldValue` + `phoneCursorForDigits` 光标映射
- [x] 满 11 位跳焦改为 `jumpToCode` + `LaunchedEffect` 延迟 120ms；字段 `maxLength(13)`
- [x] 5556 逐键输入 `15617889809` / `13800138000` 不错位、不漏字（`s6_phone_slow_156.png` / `s6_phone_slow_138.png`）
- [x] 5556 整段粘贴两条号码不错位、满 11 位自动跳焦（`s6_phone_paste_156.png` / `s6_phone_paste_138.png`）

### 6.2 过期 fire 卡 409 本地消除
- [x] `ack` 回调透出 `code`；`HTTP_409` 写入持久标记 `bugu_main.expired_fire_ids`，按 fireId 过滤 `live`/待回应列表
- [x] 11-04 过期数据：点「完成了」→ `httpCode=409` → 卡消失 + 提示（5556 `s6_expired_after.png`；5554 `s6_5554_final_expired.png`）
- [x] 「刷新看板」不复活、冷启动（`dismissedFires.get ids=6`）不复活（`s6_5554_final_refresh.png` / `s6_5554_final_restart.png`）

### 6.3 亲友数口径
- [x] `BuguKuiklyHost.buildPayload` 仅统计 `status == ACTIVE` 的 bond
- [x] 5556 看板 `已和 0 位` / 亲友页 `已关联亲友 0 人`（`s6_dash_before2.png` / `s6_friends_0.png`）；加 ACTIVE 后两端均为 1（`s6_dash_1active.png` / `s6_friends_1.png`）
- [x] 5554 看板 0 / 亲友 0（`s6_5554_final_friends.png`）

### 6.4 我的页系统返回键
- [x] `BuguKuiklyHost.onBack` 弹栈去重；`attach` 压栈对栈顶同页同参去重
- [x] 5556/5554 日志：`attach page=BuguProfilePage` → 返回 → `attach page=BuguRealDashboardPage`（`s6_5554_final_profile_back.png`）

### 6.5 陪伴列表/详情判断
- [x] 结论：纯数据（0 ACTIVE，1 PENDING_OUTBOUND），非列表代码缺陷（见 `spec.md` §9.5）
- [x] 补 ACTIVE 测试数据后陪伴详情正常（用户9810、独立设置、解除关联；`s6_companion_detail.png`）

### 6.6 重装与复验
- [x] `:bugu-ui:assembleRelease` → AAR → `app:assembleRelease`
- [x] 5554 `install -r` 保留数据/登录态（versionCode 4 / 1.1.2，jwt 保留，`boot me() ok user=用户9809`）
- [x] 四条在 5554 各复验一次；最终看板无过期卡（`s6_5554_final_restored.png`）
- [x] 两设备 `FATAL EXCEPTION=0` / `E AndroidRuntime=0` / `ANR=0`

