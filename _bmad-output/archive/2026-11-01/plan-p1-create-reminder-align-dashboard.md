---
title: 'P1: 新建惦记页对齐看板视觉与交互'
type: 'feature'
ticket: ''
created: '2026-10-31'
status: 'built'
baseline_revision: '61f7bff592827c28b8a7ad19100a14b91c7fd6f4'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'auto'
lenses_ran: ['quick']
review_loop_iteration: 1
context:
  - '/Users/peng/Documents/phk/bugu/app-ui-ux-optimization-report.md'
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/theme/Theme.kt'
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/common/Ui.kt'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 新建惦记页视觉与看板标杆割裂——顶部无品牌锚点、表单行用彩色方块图标、话筒用渐变、送给行无卡片包裹、无入场动效；交互上弹层无关闭按钮、解析错误就近显示易被键盘遮挡、多选无全选/清空、过去时刻需手动改时间。

**Approach:** 抽取 TopBar/HeaderIconButton 到公共模块；新建页顶部加 40dp 咕咕鸟(CHEER)+ 草稿入口；表单行图标去彩色方块改纯色、话筒改纯色圆底；送给行包白卡加全选/清空；弹层加顶部关闭按钮、解析错误改顶部 tint 提示条、过去时刻主按钮一键改明天创建；全页加 RiseIn 错峰入场。

## Boundaries & Constraints

**Always:**
- 所有白卡 `border(1.dp, H.Hairline, RoundedCornerShape(...))`
- 可点元素 `heightIn(min = 44.dp)`
- 入场动效用 `RiseIn` 错峰
- 状态色语义不变（PENDING=Indigo 等）
- 正文辅助色 `H.Ink3Strong`，装饰图标 tint 保留 `H.Ink3`
- 草稿持久化用现有 `bugu_main` SharedPreferences，不引入新依赖

**Never:**
- 不改动服务端代码
- 不引入新第三方依赖
- 不改动看板页布局
- 不改表单字段语义与校验规则（MAX_TITLE/MAX_NOTE/MAX_PHRASE 等保持不变）

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 页面打开 | 首次进入 | 顶部咕咕鸟(CHEER)+返回+标题左对齐+右侧草稿按钮；表单卡/送给卡 RiseIn 入场 | — |
| 草稿恢复 | 本地有草稿 JSON | 顶部草稿按钮可点，弹出恢复确认；确认后回填所有字段 | 草稿解析失败则忽略并清除 |
| 多选全选 | 选中 >1 人 | 送给卡右上角出现「全选/清空」胶囊 | — |
| 过去时刻 | fireAt ≤ now | 主按钮文案变「改到明天再创建」，点击自动 +1 天并创建 | — |
| 解析失败 | api.parse 抛异常 | 顶部 Rose tint 提示条显示错误，3.5s 自动消失 | — |
| 弹层关闭 | 时刻·方式弹层打开 | 顶部右侧 X 按钮关闭弹层；底部「就这个」保留作确认 | — |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/bugu/ui/common/Ui.kt` -- 公共组件库；已有 GuguBird/GuguMood/RiseIn/EmptyState/CircleIcon/TagChip；新增 HeaderIconButton(从看板迁移) + TopBar
- `app/src/main/java/com/bugu/ui/screens/DashboardScreen.kt` -- L800 HeaderIconButton 定义，迁移到 Ui.kt 后看板改为 import
- `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 1440 行；L510 顶部栏、L542 送给行、L560 表单卡、L736 主按钮、L770 智能条、L871 时刻弹层
- `app/src/main/java/com/bugu/ui/AppRoot.kt` -- L155 `bugu_main` SharedPreferences 用法（草稿持久化参考）
- `app/src/main/java/com/bugu/ui/theme/Theme.kt` -- H 色板（Ink3/Ink3Strong/Hairline/Coral/CoralTint 等）

## Tasks & Acceptance

**Execution:**
- [x] `app/src/main/java/com/bugu/ui/common/Ui.kt` -- 新增 public `HeaderIconButton`(从 DashboardScreen L800 迁移，补 import) 与 `TopBar(title, onBack, actions, guguMood)` 公共组件 -- G1 统一顶部栏
- [x] `app/src/main/java/com/bugu/ui/screens/DashboardScreen.kt` -- 删除本地 HeaderIconButton，改为 import common.HeaderIconButton -- 消除重复定义
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 顶部栏改用 TopBar：40dp 咕咕鸟(CHEER)+返回箭头+标题左对齐 headlineSmall+右侧「草稿」按钮(SharedPreferences 存/取 JSON) -- N1
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 表单行 CircleIcon 改为纯色 Icon(tint H.Ink3, 无背景方块)，标题/时刻/捎句话三行统一 -- N2
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 话筒背景 `Brush.linearGradient` 改纯色 `H.Coral` 圆底(对齐 TagChip solid) -- N3
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 送给行包进白卡(border+RoundedCornerShape 20dp)，卡内标题行「把惦记送给谁」+ 已选计数 + 右上角「全选/清空」胶囊(选中>1人时显示) -- N4 + N8
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 表单卡/送给卡/操作区加 `RiseIn(delayMs)` 错峰入场 -- N5
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 时刻·方式弹层顶部加固定关闭 X 按钮(HeaderIconButton)，底部「就这个」保留 -- N6
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- phraseError/formError 由 FieldError 改为看板同款顶部 tint 提示条(Rose/Mint)，3.5s 自动消失 -- N7
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- isPast 时主按钮文案变「改到明天再创建」，点击自动 dateEpochDay+1 天再走 doCreate -- N9

**Acceptance Criteria:**
- Given 新建页打开, when 渲染顶部栏, then 显示 40dp 咕咕鸟(CHEER)、返回箭头、左对齐标题「新建惦记」、右侧草稿按钮
- Given 表单行, when 查看标题/时刻/捎句话图标, then 图标为纯色 H.Ink3 无彩色背景方块
- Given 智能条话筒, when 语音可用, then 话筒背景为纯色 H.Coral 圆底无渐变
- Given 送给行, when 渲染, then 包在带 hairline 边框的白卡内，标题行含「把惦记送给谁」
- Given 多选接收人, when 选中 >1 人, then 送给卡右上角出现「全选/清空」胶囊
- Given 时刻已过, when 查看主按钮, then 文案为「改到明天再创建」且点击后自动 +1 天并创建
- Given 解析失败, when 错误发生, then 顶部出现 Rose tint 提示条并 3.5s 自动消失
- Given 时刻弹层打开, when 查看顶部, then 右侧有 X 关闭按钮

## Implementation Notes

- 编译命令：`cd app && JAVA_HOME=/Users/peng/Documents/phk/haohao/.toolchain/java-home PATH=$JAVA_HOME/bin:$PATH ./gradlew compileDebugKotlin --no-daemon`。结果 BUILD SUCCESSFUL（exit code 1 仅因 sandbox 拦截 Kotlin daemon tmp 文件清理，不影响编译产物）。
- Kotlin 局部函数顺序约束：`clearErrors()` 必须声明在 `restoreDraft()` 之前（restoreDraft 调用 clearErrors）。已将整个草稿块（showDraftDialog/prefs/hasDraft/saveDraft/restoreDraft）移到 clearErrors 之后，所有被引用变量（title/note/.../selectedRecipientIds）与 clearErrors 均在草稿块之前声明。
- `HeaderIconButton` 从 DashboardScreen 迁移到 Ui.kt 并改为 public；DashboardScreen 删除本地定义改 import common。
- 无用 import 清理：CreateReminderScreen 删除 `Rounded.ArrowBack`（已封装进 TopBar）与 `GuguBird`（仅用 GuguMood.CHEER）。
- 改动文件：Ui.kt(+87)、CreateReminderScreen.kt(+318/-184)、DashboardScreen.kt(-122)。新建 ReminderStatus.kt 为 P0 遗留。
- 最终复核补丁：`doCreate(overrideDate)` 修复过去时刻一键改明天后仍被旧 `isPast` 拦截的 bug；操作区补 `RiseIn(delayMs=120)`；草稿存在性改为 `draftExists` 状态镜像。最终 `compileDebugKotlin --rerun-tasks` → BUILD SUCCESSFUL（16s，仅 ProfileScreen 无关 warning；命令 exit 1 仅因 sandbox 拦截 Kotlin daemon tmp 清理）。

## Plan Change Log

## Review Triage Log

- 2026-10-31 quick lens：0 PATCH / 6 DEFER。8 条验收标准全部通过。D1(ArrowBack 无用 import)、D2(GuguBird 无用 import) 已在本轮修复；D3(全选未校验20人上限)、D5(草稿图标语义)、D6(phraseError 遮蔽 formError) 写入 deferred-work.md；D4(hasDraft 未缓存) 本轮已改为 `draftExists` 状态镜像，同时修复「保存草稿后恢复入口不立即出现」。
- 审查重点核对：Kotlin 局部函数前向引用（clearErrors L221 → restoreDraft L248，顺序正确）、草稿 JSON 字段完整性、MAX_TITLE/NOTE/PHRASE 常量未改动、DashboardScreen 旧 import 无残留。
- 2026-10-31 复核（completion audit）发现 1 PATCH：`isPast` 分支 onClick 调用 `doCreate()` 时，doCreate 仍读取组合期旧 `isPast`/`date`，会立即 return 报「时刻已过」根本不创建。已修：`doCreate(overrideDate: LocalDate? = null)` 内部用 `fireDate` 重算 `past` 与 `fireAt`，按钮点击传 `tomorrow`；同时补 N5 操作区 `RiseIn(delayMs = 120)`，三区块延迟 0/60/120 完成错峰。

## Design Notes

TopBar 组件签名：`TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, guguMood: GuguMood? = null, actions: @Composable RowScope.() -> Unit = {})`。咕咕鸟可选，为 null 时不显示。actions 槽位放右侧按钮（草稿等）。

草稿格式：SharedPreferences `bugu_main` 存 key=`draft_reminder`，值为 JSON 字符串（title/note/dateEpochDay/hour/minute/kind/tone/repeatMode/customDaysCsv/voiceEnabled/selectedRecipientIds）。恢复时用 AlertDialog 确认。

## Verification

**Commands:**
- `cd app && JAVA_HOME=/Users/peng/Documents/phk/haohao/.toolchain/java-home PATH=$JAVA_HOME/bin:$PATH ./gradlew compileDebugKotlin --no-daemon` -- expected: BUILD SUCCESSFUL

**Manual checks:**
- 新建页顶部有咕咕鸟+返回+左对齐标题+草稿按钮
- 表单行图标无彩色方块、话筒纯色
- 送给行包白卡且多选>1人有全选/清空
- 过去时刻主按钮变「改到明天再创建」
- 解析错误顶部提示条
- 时刻弹层有 X 关闭
