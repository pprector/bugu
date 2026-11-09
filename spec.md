# Spec：B5 段四 · 新旧版对照审计与逐页对齐

> 触发：产品试用反馈「新版 UI 与功能和老版本不一致」
> 日期：2026-11-06
> 状态：审计完成，差异清单待产品确认；确认后才进入修复

## 1. 目标

把"感觉不一致"落成可复核的**逐页差异清单**，再按页修复 P0/P1，每页用同一数据状态做旧/新对照验收。不做无依据的零散改动。

## 2. 基准与范围

- **旧版基准**：commit `5a65d39`（删除 Compose 前的最后一次入库）。15 个 Compose 文件用 `git show` 导出到只读基线 `/tmp/bugu-compose-baseline/`；**不 checkout、不改工作区删除状态**。
- **新版对象**：`kuikly-poc/KuiklyUI/bugu-ui/src/commonMain/kotlin/com/bugu/kuikly/ui/` 下 11 个页面 + 共享件 `BuguBrand.kt`；响铃为原生 `app/src/main/kotlin/com/bugu/ui/RingScreenView.kt`。
- **范围**：登录、权限引导、看板、新建提醒、历史、日历、亲友、陪伴列表、陪伴详情、我的、资料详情、响铃，共 12 页。
- **不在范围**：服务端接口、闹钟/推送/深链/登录态存储等保留原生的链路（只审计 UI 呈现与入口）。

## 3. 审计方法（可复核）

1. 对每一页，旧/新两个文件全文对读，按固定维度记录：字段与数据来源、区块顺序、文案原文（逐字）、空态、错误态、按钮与入口（点击后的结果）、间距与字号档位。
2. 每条差异必须给出**旧版代码位置**（`5a65d39:app/src/main/java/com/bugu/ui/...:行号`），便于复核。
3. 截图只作改后验收，不作为差异发现的主要依据。
4. 严重度：
   - **P0**：用户能感知的**核心链路**缺失（登录合规、创建、确认、撤销、权限生效、资料能力）。
   - **P1**：明显视觉/文案偏差、辅助入口缺失、交互结果不同。
   - **P2**：仅由目标框架（Kuikly/原生 View）能力缺失导致、改造成本不划算的偏差。
5. P2 单独成表并逐条给理由；**未完成的功能不得进 P2**（品牌插画、字体、头像、分享、通知渠道均按 P1/P0 处理）。

## 4. 交付物

| 文件 | 内容 |
|---|---|
| `B5_对照审计_差异清单.md` | 12 页逐条差异（页面/旧版含行号/新版/类型/P0-P1-P2）+ P2《可接受的差异》+ 修复批次 |
| `tasks.md` § B5 段四 | 审计进度、第一批 P0、第二批 P1、约束 |

## 5. 验收标准

### 5.1 清单验收（当前卡点）
- 12 页全部覆盖，P0/P1/P2 有明确判定。
- 每条差异带旧版文件+行号，能按行号复核对读。
- P2 表与 P1/P0 表没有重叠，且每条 P2 有"为什么可接受"的理由。

### 5.2 修复验收（放行后）
- 一页一修，禁止一次改多页再统一验收。
- 每页交付：旧版行为（源码行号）→ 新版同数据状态并排截图（同一账号、同一条测试数据）。
- P0 项逐条勾销；构建 `assembleDebug` 通过；页面可进可退；`FATAL EXCEPTION=0`。
- 响铃页 P1 只改文案/信息，不触碰段二已验收的响铃核心（循环、锁屏直达、ack 即停、队列、WakeLock）。

## 6. 约束

- 不 commit、不 push。
- 禁区不碰：`res/**`、`AlarmNotifications.kt`、`LocalNotificationTest.kt`、`website/`、`admin/`、`brand/`、`version.properties`。
- `haohao36` 留给产品试用，不占用；对照/验证用 `kuikly36`。
- 审计基线目录只读；如需重导，重新 `git show`，不得 `git checkout` 回工作区。

## 7. 已知风险

1. **Kuikly 弹层能力（已更正）**：`com.tencent.kuikly.compose.ui.window.Dialog` 可用，支持 `DialogProperties(dismissOnBackPress / dismissOnClickOutside / usePlatformDefaultWidth / inWindow / scrimColor)`，可做模态遮罩；**无** BottomSheet / DatePicker / TimePicker，新建页时间选择仍需自绘面板（P0 C1 主要工作量）。
2. **头像上传**：需要新增 Bridge（相册选择 + 上传 + 结果回传），不是纯 UI 改动；需与服务端 COS 签名接口联调。
3. **字体**：旧版 Plus Jakarta Sans / Space Grotesk 未随 Kuikly 打包；补字体涉及 AAR 资源与 `krFontAdapter`，改动面比单页大，排在 P1 第二批。
4. **响铃文案**：`会一直响到你确认` 是段二按产品口径改的，与旧基线不同，需产品确认"保留新版"还是"回退旧文案"，避免来回改。

## 8. 段五 · 5554 release 真实短信登录验收 + 登录页两项修复

> 触发：产品选「路径一 真实短信登录」，并插入登录页两个问题
> 日期：2026-11-09
> 状态：登录已完成，验收待跑完；弹窗修复待验收后执行

### 8.1 前提（已达成，作为验收基线）

- 设备 `emulator-5554`，release 包：versionCode **4** / versionName **1.1.2**，APK SHA-256 `671a2b4c8b2c5b2c94f67b473818e45c6121b54a52193b7aa3f5ae48ea6473cf`。
- 真实短信登录链路：手机号 `156 1788 9809` + 产品下发验证码 `015565`，登录成功；`/data/data/com.bugu/shared_prefs/bugu.xml` 含 `jwt`，长度 208（不贴明文）。
- 设备时间 `2026-11-09 12:31 CST`；登录后自动经权限引导，当前落点看板。
- **全程标注：真实短信登录链路**，不是注入态。

### 8.2 判定规则（逐页四要素）

每页必须分别记录：**可达** / **返回路径** / **关键元素在不在** / **异常**。
缺任何一项单独列出；没验过的写「未验」，不得写成通过。
问题分三级：**阻断上架 / 影响使用 / 观感**，每条附截图文件名。
收尾计数：`FATAL EXCEPTION`、`E AndroidRuntime`、`ANR`；并说明是否出现白屏 / 闪退 / 卡死。

### 8.3 页面导航矩阵（全部真实点击，禁止 debug 直开）

| 页面 | 入口 | 返回 | 关键元素 |
|---|---|---|---|
| 登录 | 本轮已真实走完 | 根页，无返回 | 品牌区、+86、3-4-4 分组、协议勾选+全文入口、获取验证码、60s 倒计时 |
| 权限引导 | 登录后自动 replace | `routerReplace` 回看板 | 返回、三权限卡（温和通知/准时闹钟/后台保活）、双按钮「开启并进入布谷 / 暂不开启，先去逛逛」 |
| 看板 | 登录后落点 | 根页，返回键双击退出 | 问候+日期、刷新看板、正在呼唤卡三出口、四出口（历史/日历/亲友/我的）、时钟卡、待我回应/我的提醒分段、空态、日程周历 |
| 新建 | 看板「我的提醒」段「+ 新建提醒」/ 亲友 / 陪伴详情 | TopBar 返回 → 来源页 | 接收人、标题、时间 chip、方式、便签、咕咕帮填、创建按钮、草稿 |
| 历史 | 看板「历史」卡 | 返回 → 看板 | `我 → X` / `X → 我` 方向、类型+重复+时刻、空态 |
| 日历 | 看板「日历」卡 | 返回 → 看板 | 月份切换、日期格、当日列表、空态 |
| 亲友 | 看板「亲友」卡 | 返回 → 看板 | 邀请码、互伴卡、进入陪伴列表 |
| 陪伴列表 | 亲友页互伴卡 | 返回 → 亲友 | 标题、双方条目、空态 |
| 陪伴详情 | 陪伴列表某条 | `routerReplace` 回列表 | 对方昵称、提醒列表、暂停/长辈 BrandSwitch、为 X 设一条提醒、PD4 关系失效兜底 |
| 我的 | 看板「我的」卡 | 返回 → 看板 | M1 头像/昵称、M2 互伴卡、M3 菜单顺序+图标、M5 修改昵称按钮、大字关怀 BrandSwitch、权限与送达诊断、帮助与反馈、关于布谷、退出登录 |
| 资料详情 | 我的 → 权限与送达诊断 / 帮助与反馈 / 关于布谷 | 返回 → 我的 | 对应 `page=permissions/feedback/about` 内容、状态行 |

重点复验：看板三出口/撤销/进行中响铃卡；亲友；陪伴详情 PD4（有真实数据则实测，否则标代码级）；我的 M1–M7。

### 8.4 响铃验收

- 主路径（真实 fire）：在「我的提醒」新建一条 1–2 分钟内触发的提醒 → 等 `AlarmManager` 真实拉起 `RingActivity`（全屏、锁屏可直达）。
- 循环持续 ≥60s 不自动停；右滑 ack 即停、Activity 结束。
- 证据：`dumpsys audio` 音频焦点入栈/释放；`dumpsys power` WakeLock `Bugu:alarmRing` 从持有到 `size=0`。
- 若生产闹钟路径不可得，退化为 `am start` 显式注入，并**明确标注「非真实 fire」**，不得冒充。

### 8.5 协议门 → 模态弹窗（验收跑完后立即做）

- 文件：`kuikly-poc/KuiklyUI/bugu-ui/src/commonMain/kotlin/com/bugu/kuikly/ui/BuguLoginPage.kt`（仅此文件 + 必要时 `BuguBrand.kt`）。
- 实现：用 `com.tencent.kuikly.compose.ui.window.Dialog`，`DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false, inWindow = true, scrimColor = 黑 0.6)`；内容为居中卡片，标题「需要你的同意」，正文沿用旧版原文，按钮「暂不」/「同意并继续」。
- 行为：点遮罩不触发同意；返回键只关弹窗不退出登录页；「暂不」关闭且不发码；「同意并继续」勾选协议并继续发码。
- 验收（5556 debug）：未勾选点获取验证码 → 弹窗出现 → 点遮罩无动作 → 暂不关闭且 logcat 无发码请求 → 同意并继续才发码；截图 + logcat。

### 8.6 登录输入框诊断（只诊断，不改）

在 5556 实测三种状态并截图：正常输入、字段级红字出现时、「手机号 11 位 / 验证码 6 位」边界态；并判断「`adb input text` 极速灌字吞字符」是否在人工节奏复现。结论报回，等产品补充描述后再定改法。

### 8.7 约束（不变）

- 不 commit / 不 push。
- 禁区不碰：`res/**`、`AlarmNotifications.kt`、`LocalNotificationTest.kt`、`website/`、`admin/`、`brand/`、`version.properties`。
- 5554 验收期间不重编、不重装；验收结束保持运行，产品自行点击。
- 弹窗修复完成后统一重装 5554。

---

## 9. 段六：四条问题修复（用户已定口径，无需再确认）

### 9.1 登录输入框 3-4-4 错位（方案 A）

文件：`kuikly-poc/KuiklyUI/bugu-ui/src/commonMain/kotlin/com/bugu/kuikly/ui/BuguLoginPage.kt`

- 手机号字段从 `BasicTextField(value: String)` 改为 `BasicTextField(value: TextFieldValue)`，由页面自己维护光标。
- 保留 3-4-4 显示；输入时取 `v.text` 中数字截断到 11 位，按数字位还原分组空格。
- 光标映射：`digitsBefore = v.text.take(selection.start/end).count { isDigit }`，再映射回格式化串中的位置（`phoneCursorForDigits`）。
- 满 11 位自动跳焦：不在 `onValueChange` 内同步 `requestFocus`，改为置 `jumpToCode` 标志，`LaunchedEffect` 延迟 120ms 再请求，避免抢焦点丢最后一位。
- 字段加 `Modifier.maxLength(13)`（11 位 + 2 空格）阻断第 12 位，防止"忽略输入但原生文本已变化"。
- 验证：5556 逐键输入 `15617889809`、`13800138000`；整段粘贴同两条号码；满 11 位自动跳到验证码且号码完整。
- 若方案 A 仍不稳定：记录失败点（哪条路径、光标偏移值），再退方案 B（输入时纯数字、失焦/点发码时才格式化）。

### 9.2 过期 fire 卡 409 本地消除

文件：`BuguRealDashboardPage.kt`

- `ack` 回调增加服务端错误码出参：`(ok, message, code)`；失败时透传 `r.optString("code")`（HTTP 409 → `HTTP_409`）。
- `runAck` 收到 `HTTP_409` 时不伪造 ACK：把该 fireId 写入 `bugu_main.expired_fire_ids`（桥 `dismissedFiresGet/Set`，跨进程保留），触发重组后卡片从 `live` 与待回应列表同时过滤掉；展示 Mint 提示「这条提醒已过期，已从待回应里移除」。
- 本地标记只用于隐藏该 fire，不写服务端状态；服务端后续返回新 fire 或完成态仍以服务端为准。
- 验证：用 11-04 那条过期 fire 点「完成了」→ 卡消失 → 点「刷新看板」不复活 → 冷启动仍不复活。

### 9.3 亲友数口径统一

文件：`app/src/main/kotlin/com/bugu/kuikly/BuguKuiklyHost.kt`（`buildPayload`）

- `/bond` 同时返回 `ACTIVE` 与本人发出的 `PENDING_OUTBOUND`（见 `BondService.listMine`），原实现 `put("bonds", bonds.length())` 把邀请中计入。
- 改为只统计 `status == "ACTIVE"`，与亲友页（`BuguFriendsPage` 过滤 ACTIVE）、我的页（`BuguProfilePage` 过滤 ACTIVE）同口径。
- 文案「已和 N 位家人连上」以 ACTIVE 为准。
- 验证：看板与亲友页截图同屏数字一致。

### 9.4 我的页系统返回键

文件：`app/src/main/kotlin/com/bugu/kuikly/BuguKuiklyHost.kt`

- `onBack()` 当前实现：`stack.removeLast()` 后 `attach(previous)`，而 `attach` 又会 `addLast`，导致栈内同页重复，返回时反复重载本页。
- 修复：`onBack` 弹栈后把目标页也弹出，由 `attach` 重新压入唯一一份；并在 `attach` 压栈时对"栈顶同页"去重（重载/重复点击不再叠加）。
- 验证：我的页按系统返回键 → 看板（logcat `attach page=BuguRealDashboardPage`），不再 `attach ... BuguProfilePage`。

### 9.5 陪伴列表/详情：纯数据判断

- `BondService.listMine` 只返回 ACTIVE + 本人 PENDING_OUTBOUND；`PENDING_OUTBOUND` 的 `counterpartId=null`。
- 陪伴列表按 ACTIVE 过滤，账号当前 0 ACTIVE（1 条 PENDING_OUTBOUND），因此列表/详情为空属数据形态，不是列表代码缺陷。
- 结论：拿一个有 ACTIVE 亲友的账号即可验；详情页依赖 `bondId + counterpartId`，代码路径需 ACTIVE 数据才能走通。

### 9.6 验收与重装

- 5556（debug，`10.0.2.2:8081`）逐条验收：9.1 两条号码 × 逐键/粘贴；9.2 现有过期数据点击后消失且刷新不复活；9.3 两页截图数字一致；9.4 返回键落看板日志。
- 四条通过后：`:bugu-ui:assembleRelease` → 复制 AAR → `app:assembleRelease` → `adb install -r` 到 5554（保留数据/登录态），装完四条各复验一次。
- 全程 0 FATAL/ANR；不 commit / 不 push；禁区文件不动；5554 保持运行。
