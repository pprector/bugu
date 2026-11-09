# Kuikly 迁移 · 前后对照与回归验收报告

- 日期：2026-11-09
- 范围：C5 一次性通知权限门、DND 引导、D 组回归、Kuikly（Compose → Kuikly）前后对照，以及本轮回归修复（响铃昵称 / 非法深链提示 / 退出确认 / 陪伴详情首帧 / 通知渠道定性）
- 环境：
  - 新版：emulator-5556，debug 包 `versionCode 4 / versionName 1.1.2`
  - 旧版：emulator-5558，`git 5a65d39`（Kuikly 全量迁移前最后一个 Compose 版本）另建 worktree 构建
  - 后端：本地 `http://localhost:8081`，Postgres 容器 `bugu-postgres`（库 `bugu`）
  - 账号：15617889809（9809）、15617889810（9810，有 1 条关联 + 2 条提醒）、15617889811（9811，无任何关联）
- **本次未提交代码、未发布、未部署生产。**

## 0. 结论摘要

| 项目 | 结果 |
| --- | --- |
| C5 一次性强制权限门 | 实现完成，5/5 验收 PASS |
| DND 引导（不申请权限） | 两处入口完成，跳转 PASS，真机勿扰实际效果「验不了」 |
| D 组回归 | 8 项中 7 项 PASS，1 项 FAIL（响铃页昵称）；本轮已修复并复验 PASS |
| 本轮修复复验 | 5/5 PASS：响铃昵称（全屏 / 通知点击 / 缓存缺失回退）、非法深链提示、退出确认可见可点、陪伴详情首进不再 0 条 |
| 权限诊断渠道 3→1 | **非 bug**：新版仅列出真实存在的 1 个渠道；旧版「3 条」是 AVD 残留渠道，干净重装旧版同样只有 1 条。无需改代码，见 3.2 |
| Kuikly 前后对照 | 12 个页面/场景完成对照：7 项等价、4 项有差异、1 项渠道差异已定性为设备残留 |

---

## 1. C5：一次性强制通知权限

### 1.1 实现位置

| 文件 | 改动 |
| --- | --- |
| [BuguKuiklyHost.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/kuikly/BuguKuiklyHost.kt#L136-L146) | `openDashboard()` 在「有 token 且 gate 未过」时改挂 `BuguPermissionGuidePage(gate=1)` |
| [BuguBridgeNativeModule.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/kuikly/BuguBridgeNativeModule.kt#L184-L195) | 新增 `permissionGateGet` / `permissionGateSet`，持久化到 `bugu_main` 的 `permission_gate_passed` |
| [BuguBridgeNativeModule.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/kuikly/BuguBridgeNativeModule.kt#L933-L935) | 宿主侧静态读取 `permissionGatePassed(context)` |
| [BuguPermissionGuidePage.kt](file:///Users/peng/Documents/phk/kuikly-poc/KuiklyUI/bugu-ui/src/commonMain/kotlin/com/bugu/kuikly/ui/BuguPermissionGuidePage.kt#L42-L50) | gate 模式：无返回、无跳过，仅「去开启通知」「退出登录」；授权后写标记并 `routerReplace` 看板 |
| BuguLoginPage.kt | 登录成功后先读 gate，再决定进看板还是进权限门 |

### 1.2 「一次」的边界

- 标记 `permission_gate_passed` 只在“通过这道门”（通知已授权）时写入一次，之后永不再拦。
- **过门之后即使再关掉系统通知权限，也不再阻断**，只保留看板顶部红色横幅。该行为已写入 [BuguKuiklyHost.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/kuikly/BuguKuiklyHost.kt#L137-L139) 与权限页注释，避免后续维护者误判为漏拦。
- 从系统设置返回时由 `pageDidAppear()` 重读权限，已授权则自动放行，无需再点按钮。
- 若用户始终未授权、直接「退出登录」，标记不会写入，下次登录仍会再拦一次——这是“未过门”而非“已过门后又拦”，与需求一致；通过门之后的退出重登已实测不再拦（见 1.3-5）。

### 1.3 验收结果

| # | 场景 | 结果 | 证据 |
| --- | --- | --- | --- |
| 1 | 全新安装 → 登录 → 停在权限页；未授权时点任何位置都进不了看板，返回键也被拦 | PASS | `docs/kuikly-compare/c5_gate.png`、`c5_back_blocked.png` |
| 2 | 去系统设置开启通知 → 返回 → 自动进看板 | PASS | `c5_settings_return.png` |
| 3 | 冷启动第二次 → 不再拦，直接看板 | PASS | `c5_coldstart2.png` |
| 4 | 系统设置里关掉通知 → 冷启动 → 仍直接进看板，仅在顶部显示红条 | PASS | `c5_no_perm_banner.png` |
| 5 | 退出登录再登录 → 不再拦 | PASS | `c5_relogin.png` |

辅助取证：`logcat -s BuguKuiklyHost BuguBridgeNative BuguPermission` 中可见 `permissionGate.get=false/true`、`permissionGate.set=true`、`route replace BuguRealDashboardPage`；`shared_prefs/bugu_main.xml` 中 `permission_gate_passed=true`。

---

## 2. DND：只做引导，不申请权限

### 2.1 实现

| 入口 | 位置 | 内容 |
| --- | --- | --- |
| 权限页 | `BuguPermissionGuidePage` 的 `DndGuideCard` | 「勿扰或静音模式下，到点可能听不到声音。建议在系统勿扰设置里把『闹钟』加入允许。」+「去勿扰设置」 |
| 资料详情 · 权限诊断 | [BuguProfileDetailsPage.kt](file:///Users/peng/Documents/phk/kuikly-poc/KuiklyUI/bugu-ui/src/commonMain/kotlin/com/bugu/kuikly/ui/BuguProfileDetailsPage.kt#L365-L383) | 同上文案 +「去勿扰设置」 |

跳转实现：[BuguBridgeNativeModule.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/kuikly/BuguBridgeNativeModule.kt#L773-L780) `openSettings("dnd")` 优先 `Intent("android.settings.ZEN_MODE_SETTINGS")`，失败回退 `ACTION_SETTINGS`。

明确未做：未申请 `ACCESS_NOTIFICATION_POLICY`、未给渠道 `setBypassDnd`、未新建渠道、未改任何通知逻辑。

### 2.2 验收

| 项 | 结果 | 证据 |
| --- | --- | --- |
| 权限页存在勿扰说明卡片 | PASS | `c5_gate.png`、`new_permission_gate.png` |
| 权限诊断页存在勿扰卡片 | PASS | `dnd_diag.png` |
| 点击「去勿扰设置」跳到系统勿扰设置，无崩溃（诊断页实测） | PASS | `dnd_system.png`（系统 Modes/勿扰页，`opened=true`） |
| 权限页内按钮点击 | 未单独点按，复用同一 bridge 方法（诊断页已验证） | — |
| 真机实际勿扰/静音效果 | **验不了**（模拟器无真实 DND 音频策略；国产 ROM 的闹钟例外策略也无法在模拟器验证） | — |

跳转后若系统页找不到「闹钟例外」入口，按产品要求仅正常展示文案，不做额外处理。

---

## 3. D 组回归

| # | 用例 | 结果 | 证据 / 说明 |
| --- | --- | --- | --- |
| D-1 | 登录成功全屏庆祝 | PASS | `d_celebrate.png` |
| D-2 | 历史空态 | PASS | `d_history_empty.png`（睡咕咕插画 +「还没有回执」+ CTA） |
| D-3 | 日历空态 | PASS | `d_calendar_empty.png`（「这天很清净」） |
| D-4 | 邀请码错误 | PASS（呈现为 toast，非失败卡） | `d_invite_error.png`（「邀请不存在或已被处理」） |
| D-5 | 深链 6 位错误码 | PASS（进亲友页并预填，提交出同一 toast） | 见 D-02 说明 |
| D-6 | 亲友空态（全新无 bond 账号 9811） | PASS | `d_friends_empty.png`（已关联 0 人、邀请码卡片、接受邀请输入框、下一步禁用） |
| D-7 | 陪伴详情关系失效兜底 | PASS | `d_detail_unbound.png`（「这段陪伴已经结束」+「刷新关系」，无崩溃；测后已恢复 ACTIVE） |
| D-8 | 双账号响铃昵称 | **PASS（本轮修复后）** | `d_ring_nickname_fixed.png`（全屏）/ `d_ring_nickname_notif.png`（点通知）/ `d_ring_nickname_fallback.png`（缓存缺失） |

### 3.1 缺陷与观察

**D-01（已修复，复验 PASS）响铃页永远显示「亲友」，不显示发起人昵称。**

- 现象：9809 给 9810 发提醒，9810 到点响铃页显示「来自 亲友 的提醒」「亲友 的留言」。
- 根因：[AlarmReceiver.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/schedule/AlarmReceiver.kt#L32-L68) 启动 `RingActivity` 时未读取/转发 `EXTRA_CREATOR_ID`；[AlarmNotifications.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/schedule/AlarmNotifications.kt#L83-L95) 的通知点击 PendingIntent 同样未带该 extra。`RingActivity` 因此永远拿到 `creatorId=0`，退回泛称。
- 修复：`AlarmReceiver` 读取 `EXTRA_CREATOR_ID` 并同时写入全屏 `RingActivity` Intent 与通知入参；`AlarmNotifications` 在 `setContentIntent` / `setFullScreenIntent` 共用的 `ringIntent` 里补该 extra；[RingActivity.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/ui/RingActivity.kt#L386-L394) 的昵称解析增加 `.ifBlank { "亲友" }`，缓存缺失/空串都回退「亲友」，不会渲染空或 “null”。
- 复验（5556，真实到点 + 通知点击 + 清缓存）：
  - 全屏路径：9809 建提醒 20:01 到点，显示「来自 用户9809 的提醒」「用户9809 的留言」——`d_ring_nickname_fixed.png`。
  - 通知点击路径：20:07 提醒在应用后台（BAL 拦截）时发通知，点通知进响铃页同样显示「用户9809」——`d_ring_nickname_notif.png`。
  - 缓存回退：删除 `bond_names_json` 后触发闹钟，显示「来自 亲友 的提醒」「亲友 的留言」——`d_ring_nickname_fallback.png`。

**D-02（已修复，复验 PASS）深链非 6 位码被静默丢弃。**

- 根因：`InviteDeepLink` 早已产出 `Event.Invalid`（含「邀请码格式不正确，请手动输入 6 位邀请码」），但 Kuikly 壳 [BuguKuiklyHost.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/kuikly/BuguKuiklyHost.kt#L55-L72) 从未消费 `events`，事件被静默覆盖。
- 修复：`BuguKuiklyHost.onCreate` 订阅 `InviteDeepLink.events`，`Invalid` 统一 Toast 提示并 `clearEvent()`；合法码路径不变。
- 复验：`bugu://bond?code=ZZZZZZ` 冷启 → Toast「邀请码格式不正确，请手动输入 6 位邀请码」，日志 `BuguKuiklyHost: deeplink invalid: ...`——`d_deeplink_invalid_toast.png`。

**D-03（已定位并修复，复验 PASS）陪伴详情首次进入偶发「共 0 条」。**

- 根因不是首帧竞态：详情页 `ready` 在 4 个请求都回来后才置 true，首帧不会提前渲染 0。真正原因是 `/reminder/mine` 与 `/reminder/incoming` 无先后保证，两个回调各自整体覆盖 `detailEntries`；当 `incoming` 先回、`mine`（空）后回时，已拿到的对方提醒被清空，稳定后即为「共 0 条」。
- 修复：两个回调各自只替换自己那一半（按 `byMe` 合并去重），晚到的空结果不再覆盖对方数据——[BuguCompanionPage.kt](file:///Users/peng/Documents/phk/kuikly-poc/KuiklyUI/bugu-ui/src/commonMain/kotlin/com/bugu/kuikly/ui/BuguCompanionPage.kt)。
- 复验：本账号 mine 为空、incoming 4 条，首进 1 秒即显示「共 4 条」且列表完整——`d_companion_detail_first.png`。

**D-04（已定性：非 bug，无需改代码）权限诊断的渠道覆盖从 3 条降为 1 条。**

- 查系统：`dumpsys notification --noredact` 下 `com.bugu` 名下——新版 5556 仅 `bugu_alarm_ring_v3`；**卸载后干净重装旧版 5a65d39（5558）同样仅 `bugu_alarm_ring`**。
- 查代码：诊断页渠道来自 `BuguBridgeNativeModule.permissionStatus` 遍历真实 `NotificationManager.notificationChannels`，未写死任何一条。
- 结论：属于三种可能中的第三种——旧截图里的 3 条包含 AVD 上此前安装残留的渠道（本地测试提醒 / JPush_Notification），旧版自身只创建 1 条，卸载重装即消失；新版只列出 1 条**是正确的**。
- 证据：`notif_channels_new_5556.txt`、`notif_channels_old_5558.txt`。

**D-05（已修复，复验 PASS）退出登录确认弹层被导航栏裁切。**

- 根因：不是导航栏遮挡。Kuikly 无 Dialog API，退出确认是 `LazyColumn` 内追加在列表末尾的展开项，展开后落在首屏之外且列表不滚动，用户看到的就是“被裁掉/点不到”。
- 修复：`BuguProfilePage` 用 `rememberLazyListState` + `LaunchedEffect`，展开确认时 `animateScrollToItem(last)` 滚到列表末尾，同时列表底部 `contentPadding.bottom = 104.dp` 留出安全区——[BuguProfilePage.kt](file:///Users/peng/Documents/phk/kuikly-poc/KuiklyUI/bugu-ui/src/commonMain/kotlin/com/bugu/kuikly/ui/BuguProfilePage.kt)。
- 复验：滚动到「退出登录」点击后，确认区完整露出、「再想想 / 退出登录」均在导航栏之上且可点；点「再想想」正常收起——`d_logout_confirm_visible.png`。

**D-06（观察）响铃页「滑动我已完成」手势在模拟器上多次滑动未触发。**

连续 3 次 swipe 均只移动了滑块未完成确认，最终以 force-stop 退出（无崩溃）。真机手感待验，暂记录。

---

## 4. Kuikly 前后对照

### 4.1 对照方法

- 旧版：`git worktree add /tmp/bugu-old 5a65d39`，独立构建 debug 包，装在第三台 AVD（5558），**未改动、未切换当前工作区**。
- 为保证不写生产库，仅在 worktree 内做了两处“本地联调”改动（不进入仓库）：`ApiFactory.BASE_URL` 改为 `http://10.0.2.2:8081`，`usesCleartextTraffic` 置 true。
- 两版使用同一账号（9810）、同一份本地数据、同一批截图尺寸对照。

### 4.2 逐页对照

| 页面/场景 | 旧版（Compose） | 新版（Kuikly） | 结论 |
| --- | --- | --- | --- |
| 看板 | `old_dashboard.png` | `new_dashboard.png` | **有差异（信息架构重构）**：旧版底部 4 Tab（看板/新建/陪伴/我的），新版取消 Tab、入口移到顶部快捷卡（历史/日历/亲友/我的）；默认分段从「我的提醒」改为「待我回应」 |
| 新建 | `old_create.png` | `new_create.png` | **有差异**：旧版为时间/方式/捎句话的行式表单 + 底部「开启提醒权限并创建」；新版改为日/时/分选择器 + 底部主按钮，需滚动才能看到完整表单 |
| 历史 | `old_history.png` | `new_history.png` | 等价（仅图标细节：旧版「单次提醒」带时钟图标） |
| 日历 | `old_calendar.png` | `new_calendar.png` | 等价（仅头部与底部 Tab 差异） |
| 亲友/陪伴 | `old_companions.png` | `new_friends.png` | **有差异（增强）**：新版把邀请码卡片、复制/分享/刷新、接受邀请表单直接内联；旧版需进「邀请中的关联」二级页 |
| 陪伴详情 | `old_companion_detail.png` | `new_companion_detail.png` | **有差异（增强）**：新版新增「独立设置」区（暂停接收单次提醒/长辈模式/私人备注/解除关联）；旧版为扁平行项目 |
| 我的 | `old_profile.png` | `new_profile.png` | 等价（数据一致：0 发出 / 2 收到 / 0 本月完成；新版无底部 Tab） |
| 资料详情 · 权限诊断 | `old_diagnostics.png` | `new_diagnostics.png` | **有差异**：新增「勿扰与静音」「全屏提醒」；但渠道项 3→1（见 D-04） |
| 登录 | `old_login.png` | `new_login.png` | 等价（视觉几乎一致；新版协议勾选状态可持久化） |
| 权限引导 | 旧版无此页 | `new_permission_gate.png` | 净新增（C5 强制门 + DND 引导卡） |
| 响铃页 | `old_ring.png` | `new_ring.png` | **有差异**：旧版标题「布谷来叫你啦」/「来自 惦记你的人 的提醒」/「若 40 秒未确认会再轻轻叫一次」；新版标题按类型显示「单次提醒」/「来自 <昵称> 的提醒」/「会一直响到你确认」。昵称能力已修复生效（见 D-01 / `d_ring_nickname_fixed.png`） |

### 4.3 对照结论

- **视觉与信息层级**整体完成度高，核心页面（历史、日历、我的、登录）达到等价。
- **交互重构**集中在看板导航与新建时间选择，属产品设计变化，需要产品/设计确认是否符合预期，不建议当作实现缺陷。
- **原风险项已闭环**：响铃昵称已生效（D-01 修复）；权限诊断渠道 3→1 已定性为设备残留，不是能力退化（D-04）。
- 未发现崩溃、白屏或明显错位；截图均为 1080×2400 同尺寸。

---

## 5. 已知限制与硬结论（带证据）

1. **C5：后台 + 无通知权限 = 完全静默。**
   证据：`audio_1842=0` / `vibrate_1842=0` / `notif_bugu=0`，日志含 `POST_NOTIFICATIONS unavailable` 与 `BAL_BLOCK`。
   应用内可以检测（看板红色横幅 + 冷启动权限对话框），但**检测解决不了**：用户不点开 App 就永远发现不了。这是本次选择“首次强制门”的直接原因。
2. **C3：完全静默模式 = 听不到也感觉不到**，只有通知栏可见；优先级模式下正常。国产 ROM 的 DND 豁免策略**验不了**。
3. **`am force-stop` 后完全静默**（`pending alarm=0`），系统语义如此，无解。
4. **「已静音」铃铛标记不代表真实出声。**
   正常模式亮屏横幅有静音标记但实测出声；DND 全静默场景反而没有该标记。方向相反，说明标记反映的是通知记录的 `sound=null`，与是否出声无关。仅记录，未改代码。
5. **全屏降级不成立（FR-07）。**
   targetSdk 36 下通知权限缺失时全屏路径不可用，锁屏/后台会完全静默；这是 Android 14+ 后台启动限制（BAL_BLOCK）决定的行为，不是可修复 bug。[AlarmReceiver.kt](file:///Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/schedule/AlarmReceiver.kt#L17-L19) 注释已改为如实描述，逻辑未动。
6. **真机勿扰/静音听感、国产 ROM 闹钟例外入口**：验不了。
7. **响铃页「我已完成」手势真机手感**：验不了。

---

## 6. 决策记录与已否决方案

### 6.1 C5 权限缺失的处理（已拍板：一次性强制）

| 方案 | 代价 | 结论 |
| --- | --- | --- |
| 登录后一次性强制（现行） | 首登多一道门，用户可能直接退出；但能保证“只要用下去就一定能收到” | **采用** |
| 每次冷启动强提示 | 打扰高频，仍可被无视，且与“已经授权过”的用户产生无意义拦截 | 否决 |
| 只做看板横幅 | 零打扰，但后台/锁屏场景下用户永远发现不了（见 5-1） | 否决 |

### 6.2 DND（已拍板：只引导，不申请）

| 方案 | 代价 | 结论 |
| --- | --- | --- |
| 文案引导 + 跳系统勿扰设置（现行） | 用户可能找不到「闹钟」例外入口；无法保证生效 | **采用** |
| 申请 `ACCESS_NOTIFICATION_POLICY` + 渠道 `setBypassDnd` | 多一个敏感权限；应用商店审核风险；用户可能反感“App 要改我的勿扰设置”；仍需处理厂商 ROM 差异 | 否决（本次不做） |

---

## 7. 证据索引

截图统一位于 `docs/kuikly-compare/`：

- C5：`c5_gate.png`、`c5_back_blocked.png`、`c5_settings_return.png`、`c5_coldstart2.png`、`c5_no_perm_banner.png`、`c5_relogin.png`
- DND：`dnd_diag.png`、`dnd_system.png`
- D 组：`d_celebrate.png`、`d_history_empty.png`、`d_calendar_empty.png`、`d_invite_error.png`、`d_friends_empty.png`、`d_detail_unbound.png`、`d_ring_nickname.png`（修复前）
- 本轮修复复验：`d_ring_nickname_fixed.png`、`d_ring_nickname_notif.png`、`d_ring_nickname_fallback.png`、`d_deeplink_invalid_toast.png`、`d_logout_confirm_visible.png`、`d_companion_detail_first.png`
- 渠道定性证据：`notif_channels_new_5556.txt`、`notif_channels_old_5558.txt`
- 前后对照：`old_*` / `new_*` 共 21 张

复现要点：本地后端 `localhost:8081`，短信码固定 `123456`（需向 `sms_code` 表写入其 SHA-256）；新版在 5556，旧版在 5558。
