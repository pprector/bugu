---
title: 'P0: 品牌弹窗与浮层体系（Theme token + 共享组件）'
type: 'refactor'
ticket: ''
created: '2026-11-01'
status: 'built'
baseline_revision: '85d0dfdef8fcc392bf811821df0c075e765de0e4'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'none'
review_source: 'pinned'
lenses_ran: []
review_loop_iteration: 0
context:
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/theme/Theme.kt'
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/common/Ui.kt'
  - '/Users/peng/Documents/phk/bugu/brand/布谷-品牌信息体系.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 当前 8 个弹窗和 3 个底部浮层仍是原生 Material 3，且 `Theme.kt` 未补齐 `surfaceContainer*`、`labelLarge`、`Shapes`，导致弹窗背景回落到冷紫灰基线、按钮使用系统字体、输入框只有 4dp 圆角；同时添加亲友弹窗暴露 `requesterId`，协议弹窗文案声称可点但链接不可点。用户明确要求弹窗与看板品牌调性一致。

**Approach:** 先补齐 Material 3 token 底座，再新增一套共享品牌浮层组件 `BuguCard`、`BuguButton`、`BuguDialog`、`BuguDialogActions`、`BuguSheet`、`BuguTextField`，并把现有弹窗与底浮层全部迁移到该体系；保留现有业务逻辑、忙碌态和 `CreateReminderScreen` 最近的手动改动。

## Boundaries & Constraints

**Always:** 以看板为基准——暖米画布、白卡 20/24dp、1dp `Hairline`、珊瑚主操作、咕咕唯一 IP、13sp 正文用 `Ink3Strong`、可点目标 ≥44dp。每个弹窗必须有 24dp 白卡 + hairline、可选 36dp 咕咕、44dp 关闭；主操作 48dp 渐变，危险操作用 `RoseInk`，次操作暖灰胶囊。保留所有校验、忙碌态、防重复提交与 `CreateReminderScreen` 现有改动。

**Never:** 不改页面头部/列表卡（另批处理）、不改任何 API/服务端、不改提醒业务规则、不迁移 Snackbar/Toast、不开动态取色或深色主题、不引入新依赖、不删除既有计划或 deferred 记录、不在生产 UI 保留“演示/关怀圈”类未实现承诺。

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| OPEN_CONFIRM | 点击弹窗主按钮 | 回调只执行一次，弹窗关闭或进入 loading | 失败时保留弹窗并显示错误 |
| CANCEL | 点击关闭/取消/返回键 | 不触发主回调，状态复位 | busy 时禁止 dismiss |
| KEYBOARD | 昵称/备注输入框获得焦点 | 输入框与操作按钮在 IME 上方可见，可滚动 | 键盘不遮挡主按钮 |
| DANGER | 解除关联 | 安全项「保留关联」与危险项「确认解除」明确分离 | 危险项 Rose；不可误触 |
| AGREEMENT | 协议同意门 | 协议名可点开全文，同意后原动作执行一次 | 取消不执行原动作 |
| SHEET_DISMISS | 时间/方式/AI 浮层 | 关闭、下滑、返回键均可用，选择状态保留 | 时间表盘拖拽不误关 |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/bugu/ui/theme/Theme.kt` -- `H` 色板、`AppTypography`、`lightColorScheme`；补 surface/inverse/scrim 角色、`labelLarge/labelMedium/displaySmall/headlineLarge` 与 `Shapes`。
- `app/src/main/java/com/bugu/ui/common/Ui.kt` -- 复用 `HeaderIconButton`、`GradientButton`、`TagChip`、`GuguBird`、`FieldError`、`BrandSwitch`；不要改其签名。
- `app/src/main/java/com/bugu/ui/common/BuguComponents.kt`（新增）-- `BuguCard`、`BuguButtonStyle`、`BuguButton`、`BuguDialog`、`BuguDialogActions`、`BuguSheet`、`BuguTextField`。
- `app/src/main/java/com/bugu/ui/screens/ProfileScreen.kt` -- 昵称 `AlertDialog`（约 170-197 行）迁移。
- `app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt` -- 私人备注与解除关联弹窗（约 373-431 行）迁移。
- `app/src/main/java/com/bugu/ui/screens/FriendsScreen.kt` -- 确认关联弹窗（约 116-134 行）迁移，移除 `requesterId` 展示。
- `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 时间/方式/AI 三个 `ModalBottomSheet`（约 936-1292 行）、日期选择与恢复草稿弹窗（约 1294-1342 行）迁移；保留表盘尺寸、拖拽拦截、`noteError` 与 `BackHandler` 改动。
- `app/src/main/java/com/bugu/ui/screens/AuthScreens.kt` -- 协议同意门（约 500-523 行）与 `AgreementDialog`（约 645-676 行）迁移，并修复协议链接可点。
- `app/src/main/java/com/bugu/ui/screens/ProfileDetails.kt` -- 继续调用 `AgreementDialog(which, onDismiss)`，不改签名。

## Tasks & Acceptance

**Execution:**
- [x] `app/src/main/java/com/bugu/ui/theme/Theme.kt` -- 补齐 M3 color roles、Typography 与 Shapes -- 让原生控件不再回落到基线色/系统字体/小圆角。
- [x] `app/src/main/java/com/bugu/ui/common/BuguComponents.kt` -- 新增品牌卡片、按钮、弹窗、弹窗动作区、底部浮层与输入框 -- 提供唯一浮层入口，复用现有组件。
- [x] `app/src/main/java/com/bugu/ui/screens/ProfileScreen.kt` -- 昵称弹窗改用 `BuguDialog + BuguTextField + BuguDialogActions` -- 统一资料编辑体验。
- [x] `app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt` -- 备注与解除关联改用品牌弹窗；危险操作独立样式 -- 统一关系设置与危险确认。
- [x] `app/src/main/java/com/bugu/ui/screens/FriendsScreen.kt` -- 邀请确认改用品牌弹窗，展示邀请码而非内部用户 ID -- 修复冷冰冰的技术文案。
- [x] `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt` -- 三个底浮层与两个弹窗迁移；日期选择用 `BuguSheet` 承载 -- 统一新建页所有浮层。
- [x] `app/src/main/java/com/bugu/ui/screens/AuthScreens.kt` -- 协议同意门与全文弹窗迁移，协议名可点 -- 修复合规文案与交互矛盾。
- [x] `app/src/main/java/com/bugu/ui/screens/ProfileDetails.kt` -- 适配协议全文弹窗 -- 保证三处调用一致（共享 `AgreementDialog` 签名未变，无需改调用点）。
- [x] 运行编译与单测，检查大字体、键盘、busy、危险操作 -- 编译与既有单测已通过；UI 手动检查待有设备时执行。

**Acceptance Criteria:**
- Given 任一弹窗打开，When 渲染完成，Then 24dp 白卡 + hairline + 品牌字体，且不存在原生 `AlertDialog`/`TextButton` 外观。
- Given 昵称或备注键盘打开，When 输入长文本，Then 输入框和底部按钮仍可见且可点。
- Given 解除关联弹窗，When 点「确认解除」，Then 使用 Rose 危险样式；点「保留关联」或返回则不做任何请求。
- Given 忙碌态，When 连点主按钮/关闭/遮罩，Then 不重复请求且不可 dismiss。
- Given 协议同意门，When 点协议名，Then 打开全文；点「同意并继续」后原动作只执行一次。
- Given 时间/方式/AI 浮层，When 关闭/下滑/返回，Then 正常关闭；时间表盘拖拽不会误关。
- Given `AlertDialog`/`DatePicker` 内部场景，When 打开，Then 使用品牌暖白 surface，不出现 Material 基线冷紫灰。
- Given 执行 `cd app && ./gradlew compileDebugKotlin`，When 构建完成，Then 输出 `BUILD SUCCESSFUL`。

## Implementation Notes

- 实际 Gradle 根项目是单模块（无 `:app` 子项目），验证命令改为 `./gradlew compileDebugKotlin` / `testDebugUnitTest`。
- 日期选择从 `DatePickerDialog` 改为 `BuguSheet` 承载 `DatePicker(title=null, headline=null, showModeToggle=false)`，保留“今天及以后”可选日期约束。
- 时间浮层把拖拽拦截从整个 Sheet 收窄到表盘 Box：表盘内拖动不改时间，Sheet 其他区域仍可下滑关闭。
- `BuguCard` 首次用于 AI 浮层的输入容器；`BuguTextField` 只用于昵称/备注，未改登录与新建页的 `BasicTextField`。
- `ProfileDetails.kt` 无需改动：`AgreementDialog(which, onDismiss)` 签名保持不变，调用点自动获得新视觉。
- 项目没有 Compose UI 测试依赖，也没有 `adb`/设备；I/O Matrix 的弹窗交互行只能做静态与编译验证，键盘/大字/危险操作的真实 UI 手动检查仍待有设备时执行。
- 用户选择 `review: none` 收尾，跳过独立 Quick Review；未提交的 BMad 评审提示已移除。

## Plan Change Log

## Review Triage Log

## Design Notes

`AlertDialog` 不使用 `MaterialTheme.shapes`，且默认容器色是 `surfaceContainerHigh`，所以即使补齐 token 也无法稳定得到品牌 24dp 白卡；统一改用自定义 `Dialog` 包装器。`ModalBottomSheet` 保留 Material 行为，只统一容器色、拖拽条、页头与内容卡。所有浮层统一「咕咕 36dp + 标题 + 可选 TagChip + 44dp 关闭」，动作区纵向排列以适配大字关怀模式。

## Verification

**Commands:**
- `cd app && ./gradlew compileDebugKotlin` -- expected: `BUILD SUCCESSFUL`
- `cd app && ./gradlew testDebugUnitTest` -- expected: 既有单测通过

**Manual checks (if no CLI):**
- 逐页打开昵称、备注、解除关联、确认关联、恢复草稿、协议同意、协议全文、日期选择、时间、方式、AI 浮层。
- 开启大字关怀模式（`CARE_FONT_SCALE = 1.18`）检查按钮换行、弹窗高度与滚动。
- 键盘弹出时检查输入框与主按钮是否可见。
