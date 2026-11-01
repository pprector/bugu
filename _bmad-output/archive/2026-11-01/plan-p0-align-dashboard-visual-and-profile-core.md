---
title: 'P0: 对齐看板视觉标杆 + 我的页统计卡'
type: 'refactor'
ticket: ''
created: '2026-10-31'
status: 'built'
baseline_revision: '61f7bff592827c28b8a7ad19100a14b91c7fd6f4'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: [quick]
review_loop_iteration: 0
context:
  - '/Users/peng/Documents/phk/bugu/app-ui-ux-optimization-report.md'
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/theme/Theme.kt'
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/common/Ui.kt'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 陪伴页视觉与看板标杆严重割裂（无边框、无动效、文字箭头、无品牌锚点）；全局状态色/类型映射函数定义在看板内导致陪伴详情无法复用；正文辅助色 H.Ink3 对比度仅 3.2:1 未达 WCAG 4.5:1；我的页缺少个人惦记数据反馈。

**Approach:** 抽取提醒状态映射到公共模块；全局正文辅助色 Ink3→Ink3Strong；陪伴页按看板卡片规范重做（边框/矢量箭头/咕咕鸟/RiseIn）；我的页新增惦记统计卡。

## Boundaries & Constraints

**Always:**
- 所有白卡必须 `border(1.dp, H.Hairline, RoundedCornerShape(...))`
- 所有可点元素 `heightIn(min = 44.dp)`
- 入场动效统一用 `RiseIn` 错峰
- 状态色语义：PENDING=Indigo/FIRED=Coral/ACKED=Mint/SNOOZED=Amber/DECLINED=Rose/终态=Ink3Strong
- 正文辅助色一律 `H.Ink3Strong`，`H.Ink3` 仅用于极次要装饰文字

**Never:**
- 不改动服务端代码
- 不改动看板页的现有布局（仅小修：refresh 互斥守卫、提示条自动消失）
- 不引入新的第三方依赖
- 不做账号安全（M3，依赖服务端接口，延后）

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 陪伴页加载 | bonds 非空 | 列表项带边框+矢量箭头+RiseIn 入场 | — |
| 陪伴页空 | bonds 空且无 loading | EmptyState 组件(咕咕鸟 SLEEP) | — |
| 陪伴页加载中 | bondsLoading=true | 白卡居中 CircularProgressIndicator + 文案 | — |
| 我的页统计 | myReminders+incomingReminders 已加载 | 展示发出/收到/已完成(本月)计数 | API 失败显示"—" |
| 状态色复用 | 任意页面引用 statusLabel/Color | 从公共模块导入，行为与看板一致 | — |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/bugu/ui/screens/DashboardScreen.kt` -- 标杆；statusLabel/statusColor/kindLabel/toneLabel/repeatLabel 定义在 L109-L154，需上移
- `app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt` -- 待重做；当前列表项无边框、用 Text("›")、无 RiseIn
- `app/src/main/java/com/bugu/ui/screens/ProfileScreen.kt` -- 头部互伴卡下方加统计卡；当前无统计
- `app/src/main/java/com/bugu/ui/theme/Theme.kt` -- H.Ink3(#9C9488) vs H.Ink3Strong(#6B655C) 定义在 L27-L30
- `app/src/main/java/com/bugu/ui/common/Ui.kt` -- 公共组件库；新增 ReminderStatus.kt 放状态映射
- `app/src/main/kotlin/com/bugu/client/api/GeitaApi.kt` -- myReminders()/incomingReminders() 已存在，统计卡复用

## Tasks & Acceptance

**Execution:**
- [ ] `app/src/main/java/com/bugu/ui/common/ReminderStatus.kt` -- 新建；迁移 statusLabel/statusColor/kindLabel/toneLabel/repeatLabel/parseIsoTime/TIME_FMT/relativeDayLabel；改 internal 为 public -- 消除看板私有定义，全局复用
- [ ] `app/src/main/java/com/bugu/ui/screens/DashboardScreen.kt` -- 删除本地重复定义，改为 import ReminderStatus.* -- 行为不变
- [ ] `app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt` -- 列表项加 border(H.Hairline)；箭头改 KeyboardArrowRight；顶部加 44dp 咕咕鸟(RING)+圆形添加按钮；列表项加 RiseIn(delayMs=i*40)；loading/empty 用 EmptyState/白卡 -- 视觉对齐看板
- [ ] `app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt` -- CompanionDetailScreen 列表项改用看板同款卡片结构（左时间+状态标签）；"给XX发提醒"包进白卡 -- 详情页对齐
- [ ] 全局 Ink3→Ink3Strong -- 搜索替换 ui/ 下 9 个文件中正文辅助色 H.Ink3 为 H.Ink3Strong（仅正文/标签用，装饰保留 Ink3） -- 对比度达 4.5:1
- [ ] `app/src/main/java/com/bugu/ui/screens/ProfileScreen.kt` -- 互伴卡下方加惦记统计白卡：发出/收到/已完成(本月)，数据从已有 reminders 聚合；加载失败显示"—" -- 补数据反馈
- [ ] `app/src/main/java/com/bugu/ui/screens/DashboardScreen.kt` -- refresh() 加 `if (refreshing) return` 守卫；notice/error 提示条 3.5s 后自动 dismiss -- 小修

**Acceptance Criteria:**
- Given 陪伴页打开, when bonds 加载完成, then 每个列表项有 hairline 边框且右箭头是矢量图标
- Given 陪伴页打开, when 列表渲染, then 每项有 RiseIn 错峰入场动画
- Given 我的页打开, when 统计数据已加载, then 显示发出/收到/已完成三个数字
- Given 任意页面, when 查看正文辅助文字, then 颜色为 H.Ink3Strong 对比度 ≥4.5:1
- Given 看板/陪伴详情, when 显示提醒状态, then 颜色文案与公共 ReminderStatus 一致

## Implementation Notes

- 新建 `ReminderStatus.kt` 迁移状态映射为 public 顶层函数；看板/陪伴详情/日历/历史/新建/亲友页改为 import 公共模块。
- 陪伴页重做：顶部 44dp 咕咕鸟(RING)+圆形添加按钮；列表项 border(H.Hairline)+KeyboardArrowRight 矢量箭头+RiseIn 错峰；loading/error/empty 卡片化（EmptyState）；详情页「发提醒」包白卡，列表项改用看板卡片结构。
- 全局正文辅助色 Ink3→Ink3Strong（9 文件）；装饰性图标 tint / disabled 态 / 弱化态保留 Ink3。
- 我的页新增惦记统计白卡（发出/收到/本月完成），LaunchedEffect 自包含拉取，失败显示「—」。
- 看板小修：refresh 互斥守卫 + 提示条 3.5s 自动消失。
- Review patch：返回按钮箭头改左、EmptyState 补 hairline 边框、统计 runCatching 加 onFailure 日志。
- 编译验证：`./gradlew compileDebugKotlin` BUILD SUCCESSFUL（kotlin-daemon tmp sandbox 警告为已知非阻塞）。

## Plan Change Log

## Review Triage Log

| # | Finding (file:line) | Verdict | Evidence | Route |
|---|---------------------|---------|----------|-------|
| 1 | 返回按钮箭头方向语义错误 (CompanionScreen.kt:234) | low | "返回陪伴"按钮使用 `KeyboardArrowRight`，LTR 下渲染为右箭头，与返回语义相反；文字标签虽清晰但视觉箭头误导。改 `KeyboardArrowLeft` 即可。 | patch |
| 2 | EmptyState 缺少 hairline 边框 (Ui.kt:282-285) | low | EmptyState 容器为 `background(Color.White)+clip(RoundedCornerShape(26.dp))` 无 border；计划 Boundaries「所有白卡必须 border」。本 diff 已修改该组件，补齐边框即可。 | patch |
| 3 | Profile 统计加载失败无重试路径 (ProfileScreen.kt:114-128) | low | 失败显示「—」符合计划 I/O 矩阵；但无 in-page 重试，仅靠离开重进触发 LaunchedEffect(Unit)。属超出计划的健壮性增强。 | defer |
| 4 | runCatching 静默吞异常 (ProfileScreen.kt:115) | low | `runCatching{}` 无 `.onFailure`/日志，异常被丢弃，线上无法区分 API 失败与真零数据；同行头像加载用了 `.getOrNull()` 不一致。加 `.onFailure{Log.w(...)}` 即可。 | patch |
| 5 | 本月已完成计数基于 fireAt (ProfileScreen.kt:119-123) | low | ReminderView 仅有 fireAt/createdAt 无确认时间字段，用 fireAt 月份近似「本月完成」；上下月跨期确认会偏差。受「不改服务端」约束无法修复。 | defer |
| 6 | 咕咕鸟 onTap 触发刷新为计划外交互 (CompanionScreen.kt:56) | false | GuguBird 组件 tap 有 haptic+squash+flap+气泡反馈（Ui.kt:453-477），非无反馈隐藏操作；tap-to-refresh 是带明确反馈的合理增强，不构成用户困惑。 | reject |
| 7 | Profile 已有白卡缺边框 (ProfileScreen.kt:252/314/340) | low | 互伴卡/大字模式/退出登录/MenuRow 为 `background(Color.White)` 无 border；这些卡片在本变更前已存在，变更未移除其边框。属既有视觉不一致。 | defer |
| 8 | loading 与 error 互斥可能掩盖错误 (CompanionScreen.kt:87-127) | false | AppRoot.loadBonds() 在 catch 置 bondsError=true 后，finally 同步置 bondsLoading=false，中间无挂起点，Compose 不会在两者之间重组；错误态不会被 loading 长期掩盖。 | reject |

**Patch 处理：** 1 / 2 / 4 为 trivial patch，直接修复。
**Defer：** 3 / 5 / 7 记入 deferred-work.md。

## Design Notes

状态色映射上移后，CompanionDetailScreen 的 `TagChip(statusLabel(r.status), statusColor(r.status))` 可直接复用，无需重写。统计卡数据来源：ProfileScreen 目前不持有 reminders，需在 AppRoot 层拉取后传入，或在 ProfileScreen 内用 LaunchedEffect 拉取（与 CompanionDetailScreen 模式一致）。选择后者以保持 ProfileScreen 自包含。

## Verification

**Commands:**
- `cd app && PATH=/Users/peng/Documents/phk/haohao/.toolchain/java-home/bin:$PATH ../gradlew assembleDebug` -- expected: BUILD SUCCESSFUL
- `cd app && PATH=/Users/peng/Documents/phk/haohao/.toolchain/java-home/bin:$PATH ../gradlew compileDebugKotlin` -- expected: no compile errors

**Manual checks:**
- 陪伴页：列表项有边框、箭头为矢量、有咕咕鸟、入场有动画
- 我的页：统计卡显示三个数字
- 全局：正文辅助文字颜色变深（Ink3Strong）
