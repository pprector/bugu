---
title: 'V2: 新建惦记页二次优化（头像 / 信息架构 / 时间方式拆分 / AI 浮层）'
type: 'feature'
ticket: ''
created: '2026-11-01'
status: 'built'
baseline_revision: '61f7bff592827c28b8a7ad19100a14b91c7fd6f4'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt'
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/AppRoot.kt'
  - '/Users/peng/Documents/phk/bugu/app/src/main/kotlin/com/bugu/client/api/BondModels.kt'
  - '/Users/peng/Documents/phk/bugu/app/src/main/java/com/bugu/ui/common/Ui.kt'
---

## Intent

**Problem:** 新建惦记页仍偏"填表"：接收人只有文字首字看不出人、全选/清空在多选场景反而添乱、标题空白需从头想、吸底主按钮随键盘上跳、时间与方式挤在一个弹层、时间弹层铺 30 个"便捷时刻"造成选择噪音、AI 语音/文字入口是贴键盘的长条不够聚焦。

**Approach:** 六条反馈逐条落地：头像进 chip、去掉全选清空、标题给智能默认、主按钮固定不随键盘、时间/方式拆成两个行与两个弹层、时间弹层改为"大号时间 + 表盘"（参考华为时钟，取消预设网格）、AI 语音与文本改半屏浮层组件（识别结果先落在浮层内，确认后才回填表单）。

## Boundaries

**Always:**
- 白卡 hairline 边框、可点元素 `heightIn(min = 44.dp)`、`RiseIn` 错峰入场保持不变
- 头像加载复用现有 `api.downloadStatic`，不引入 Coil 等新依赖
- 草稿持久化、顶部 Rose 提示条 3.5s、`isPast` 一键改明天逻辑保持不变
- `MAX_TITLE=20` / `MAX_NOTE=50` / `MAX_PHRASE=200` 不变
- 状态色语义、H 色板用法不变

**Never:**
- 不改服务端、不新增第三方依赖
- 不改看板布局与底部导航
- 不删除「时刻已过」校验与 20 人上限校验

## 需求拆解（6 条 → 方案）

| # | 反馈 | 方案 |
|---|------|------|
| 1 | 展示自己和对方头像 | 新增公共 `AvatarBubble`（异步取头像，失败/加载中回退首字）；送给 chip 头像从 28dp 提升到 32dp；自己取 `UserDto.avatarUrl`，对方取 `BondDto.counterpartAvatarUrl`；`AppRoot` 给页面新增 `selfAvatarUrl` 参数 |
| 2 | 全选/清空不需要 | 删除送给卡右上角胶囊、`selectAllRecipients()`、`clearRecipients()`；保留「已选 N 人」计数 |
| 3 | 标题要有默认 | 未编辑标题时按接收人智能默认：仅自己「提醒自己」；一位亲友「提醒{昵称}」；多人「提醒{n}位亲友」。用户一输入即 `titleDirty=true` 不再自动改；草稿恢复视为已编辑 |
| 4 | 创建按钮不要随键盘上推 | 根布局去掉 `imePadding()`，只给可滚动内容区加，底部操作区固定贴底；键盘弹起时按钮被键盘覆盖、位置不动 |
| 5 | 时间和方式分开 | 表单卡由 3 行变 4 行：标题 / 时间 / 方式 / 捎句话；`showSheet` 拆成 `showTimeSheet` 与 `showModeSheet`；时间行只显示「明天 08:00」，方式行显示「单次 · 轻柔」或「循环 · 工作日 · 轻柔」 |
| 6 | 时间选择参考华为闹钟 | 删除 `TIME_PRESETS` 30 格网格与「自定义时间」格子；日期只留 今天/明天/后天/其他日期；主体改为**大号时间 + 表盘（Material3 TimePicker）**；分钟步进沿用 1 分钟，双击切 5 分钟列为可选增强 |
| 6' | AI 文字/语音改悬浮窗口 | 删除吸底智能条；新增 `GuguAssistSheet` 半屏浮层：多行文本框 + 🎤语音 + ✨帮我填表；语音识别结果只填入浮层文本框，点「帮我填表」才解析回填并关闭 |

## 改造后页面结构

```
TopBar(返回 + 咕咕鸟 + 新建惦记 + ✨AI入口 + 草稿)
错误提示条（Rose, 3.5s）
可滚动内容区（imePadding 只加在这里）
  ① 送给卡：把惦记送给谁 / 已选 N 人
       [头像+自己] [头像+妈妈] [头像+阿哲] ...
  ② 表单卡
       标题    [智能默认文本 / 20]
       时间    明天 08:00              >
       方式    单次 · 轻柔             >
       捎句话  写一句暖心的惦记…        /50
底部操作区（固定，不随键盘移动）
  创建按钮 / 改到明天再创建
  辅助说明
```

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error Handling |
|---|---|---|---|
| 头像加载成功 | avatarUrl 可读 | chip 内显示圆形头像；选中态加 Coral 描边 | — |
| 头像缺失/失败 | avatarUrl=null 或下载异常 | 回退首字，不闪白、不阻塞 | 静默忽略错误 |
| 未编辑标题 + 换接收人 | titleDirty=false | 标题同步为新默认文案 | — |
| 已编辑标题 + 换接收人 | titleDirty=true | 标题保持用户输入 | — |
| 键盘弹起 | 聚焦标题/便签 | 内容区滚动可见，底部按钮位置不动 | 按钮可能被键盘覆盖，收起键盘可点 |
| 时间弹层 | 打开 | 只用 今天/明天/后天/其他 + 大号时间 + 表盘 | 过去时刻沿用顶部红字提示 |
| 方式弹层 | 打开 | 单次/循环/互叫 + 循环规则 + 语气 + 语音开关 | 互叫仅一位亲友可选（沿用） |
| AI 浮层·语音 | 点🎤 | 识别结果写入浮层文本框，浮层保持打开 | 无语音服务：置灰 + 文案说明 |
| AI 浮层·填表 | 点✨ | 调 parse → 回填标题/时间/便签 → 关浮层 + FlashRow 高亮 | 失败：浮层内 Rose 文案，不关浮层 |
| 草稿恢复 | 有草稿 | 恢复全部字段，titleDirty=true | 解析失败清除草稿 |

## Code Map

- `app/src/main/java/com/bugu/ui/common/Ui.kt` -- 新增 `AvatarBubble(api, url, fallback, size, selected)`；复用 `downloadStatic` + `BitmapFactory`
- `app/src/main/java/com/bugu/ui/screens/AppRoot.kt` -- `CreateReminderScreen(...)` 增加 `selfAvatarUrl = user.avatarUrl`
- `app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt`
  - 参数：`selfAvatarUrl: String?`
  - 删除：`TIME_PRESETS`(L129)、`selectAllRecipients`/`clearRecipients`(L334-360)、全选清空 UI(L645-662)、吸底智能条(L917-1009)
  - 改造：`WhoRow`/`PersonChip` 接收头像；表单卡 4 行；`showTimeSheet`/`showModeSheet`；新增 `GuguAssistSheet`
  - 保留：`TopBar`/错误条/草稿/`doCreate(overrideDate)`/`RiseIn`
- `app/src/main/kotlin/com/bugu/client/api/BondModels.kt` -- `counterpartAvatarUrl` 已存在，无需改动
- `app/src/main/kotlin/com/bugu/client/api/AuthModels.kt` -- `UserDto.avatarUrl` 已存在，无需改动

## Tasks & Acceptance

**Execution:**
- [x] `Ui.kt` -- 新增公共 `AvatarBubble`：`LaunchedEffect(url)` 拉取并缓存 Bitmap，失败/加载中显示首字 -- 需求 1
- [x] `AppRoot.kt` -- 传入 `selfAvatarUrl` -- 需求 1
- [x] `CreateReminderScreen.kt` -- `PersonChip` 增加 `avatarUrl`，自己 chip 用本人头像，对方 chip 用 `counterpartAvatarUrl`，头像 32dp -- 需求 1
- [x] `CreateReminderScreen.kt` -- 删除全选/清空 UI 与两个函数，保留已选计数 -- 需求 2
- [x] `CreateReminderScreen.kt` -- 标题智能默认 + `titleDirty` 标记；接收人变化时仅在未编辑状态更新 -- 需求 3
- [x] `CreateReminderScreen.kt` -- 根布局移除 `imePadding()`，仅内容区保留；操作区固定 -- 需求 4
- [x] `CreateReminderScreen.kt` -- 表单卡拆为「时间」「方式」两行，两个独立弹层与两个 summary 文案 -- 需求 5
- [x] `CreateReminderScreen.kt` -- 时间弹层删除预设网格；改为大号时间 + 日期 chips + 表盘选择器；表盘作为唯一时间入口 -- 需求 6
- [x] `CreateReminderScreen.kt` -- 删除吸底智能条，新增 `GuguAssistSheet`：识别文本落框、语音填框、点「帮我填表」才解析回填 -- 需求 6'
- [x] 编译验证：`cd app && ./gradlew assembleDebug --no-daemon` -- BUILD SUCCESSFUL（31s）

**Acceptance Criteria:**
- Given 送给卡, when 渲染 chips, then 自己与每位亲友显示圆形头像；无头像时显示首字
- Given 送给卡, when 选中多人, then 不出现「全选/清空」
- Given 首次进入, when 标题未编辑, then 显示默认标题且随接收人变化；一旦输入则不再自动改
- Given 键盘弹起, when 聚焦标题, then 创建按钮位置不变
- Given 表单卡, when 查看, then 存在独立的「时间」行与「方式」行，各自打开对应弹层
- Given 时间弹层, when 打开, then 无 06:30/07:00… 预设网格，只有日期 chips + 大号时间 + 表盘
- Given AI 浮层, when 语音识别完成, then 文本显示在浮层输入框内且浮层不关闭；点「帮我填表」后才回填并关闭
- Given 规则, when 编译, then BUILD SUCCESSFUL 且未改动服务端与依赖

## Design Notes

**华为闹钟调研结论（官方支持文档）：** 新建闹钟以大字号时间为绝对主视觉，时间调节用表盘/滚轮；分钟默认按 1 分钟步进，**双击分钟数字可切换为每次 5 分钟**的快速调节；页面上不铺一排排"便捷时刻"，重复/铃声/振动等以列表行呈现，与"时间"分离。参考：[闹钟 | 华为官网](https://consumer.huawei.com/cn/support/content/zh-cn16100687/)、[设置闹钟（双击分钟切换快调）| 华为官网](https://consumer.huawei.com/cn/support/content/zh-cn00765957/)

**采纳：** 大号时间主视觉、取消预设网格、时间与方式分离为列表行、保留表盘/轮盘选择。
**不采纳/暂缓：** Material3 `TimePicker` 不支持双击分钟切 5 分钟步进；如需完全对齐华为，可后续换自绘 双列滚轮（写入 deferred）。

**剩余时间文案：** 时间行显示「今天 08:00 / 明天 08:00」，过去时刻在该行下方红字提示，主按钮仍可一键改明天。

## Decisions（2026-11-01 已确认）

1. 标题默认：智能联动（提醒自己 / 提醒{昵称} / 提醒 N 人），用户输入即锁定。
2. AI 入口：右下悬浮 FAB「咕咕帮填」。
3. 时间控件：Material3 表盘 TimePicker。
4. 键盘弹起：底部创建按钮固定贴底，不随键盘上移（会被键盘覆盖）。

## Open Questions

无（4 项决策已确认，见上）。

## V2.1 修订（2026-11-01 用户反馈）

1. **时间选择器修复**：Material3 `TimePicker` 表盘在本机密度下点击命中错位（点 9 得到 00:50），改为自绘 `TimeWheel` 双列滚轮（小时 0-23 / 分钟 0-59，44dp 行高、吸附、中央珊瑚选中带、上下渐隐）。弹层禁止下拉关闭（`confirmValueChange = { it != SheetValue.Hidden }`），避免滑滚轮时弹层被拖走。
2. **送给行改抖音消息页样式**：去掉「送给」标签与角色文案，改为横向滚动的人物列：48dp 头像在上、备注/昵称在下；自己显示本人昵称（AppRoot 新增 `selfNickname`）；选中态珊瑚头像环 + 右下角勾。

## V2.2 修订（2026-11-01 用户反馈）

1. **时间选择器改回 Material3 表盘**：时间弹层恢复 `TimePicker`，用固定 340dp 容器居中避免拉伸；弹层保持不可下拉关闭、无 `verticalScroll`。
2. **系统返回统一**：新增 AppRoot 全局 `BackHandler` —— 子页面 → 主界面；非看板 Tab → 看板；看板再按一次才 `finish()`，首次提示「再按一次退出布谷」。
3. **返回按钮统一**：新增公共 `BackIconButton`（44dp 白底 hairline 圆形 + ArrowBack），`HeaderIconButton` 增加 `modifier` 参数；替换 权限页/日历/历史/添加亲友/陪伴详情/我的详情 6 处各异样式（含文字按钮），TopBar 也改为复用同一按钮。

## V2.3 修订（2026-11-01 时间选择器点击偏移）

**根因**：表盘本身命中是准确的（实测点内圈 18 → 18:xx、点 30 → :30、点 9 → 09:xx）；偏移来自 `ModalBottomSheet` 的拖拽手势——手指轻移几像素时弹层跟随滑动，表盘内容随之位移，抬起时点击落在相邻数字上。

**修复**：时间弹层内容 Column 增加 `pointerInput { detectVerticalDragGestures { change, _ -> change.consume() } }`，垂直拖拽被内容区拦截，弹层不再随手指位移；表盘自身的点按/拖拽选值仍然正常。

**验证**：轻微拖动表盘 → 弹层不动、时间由拖动选值（22:00）；点 9 → 09:00；「就这个」→ 时间行「明天 09:00」。

## V2.4 修订（2026-11-01 表盘不圆 / 点击偏）

**根因**：`TimePicker` 在弹层里被高度约束压成椭圆（实测宽约 282dp、高约 235dp），而组件内部按正圆计算触摸角度，导致视觉位置与命中位置错位（点 9 附近常落到 10/22）。

**修复**：给 `TimePicker` 足够高度 `size(width = 320.dp, height = 400.dp)`，表盘恢复正圆；保留弹层垂直拖拽拦截。

**验证**：表盘目视为正圆；点 9 标签 → 09:00；点内圈 18 → 18:xx；点 30 → :30；确认后时间行「明天 09:00」。

**关于现成组件**：当前使用的就是官方 Material3 `TimePicker`（无需引入第三方依赖）；若后续想换成滚轮风格，可接 `WheelPickerCompose`（JitPack）等成熟库，属样式偏好而非点击问题。

## V2.5 修订（2026-11-01 表单视觉与字数策略）

1. **去行内图标**：标题/时间/方式/捎句话不再放语义不贴切的左侧图标；时间与方式右侧改为 `KeyboardArrowRight` 进入箭头；表单卡补 hairline 边框。
2. **去掉常驻字数计数与硬截断**：输入不再 `take(MAX)`；超限时行内红字提示（标题 20 / 便签 50），提交时仍兜底校验；AI 回填、草稿恢复同样按长度提示。
3. **键盘返回**：系统返回在 IME 可见时交给系统先收键盘（`BackHandler(enabled = !WindowInsets.isImeVisible)`），避免编辑中误离页。

**验证**：标题输入超 20 字未截断并显示「标题不超过 20 字」；键盘弹起时按返回只收键盘、停留在新建页。

## Implementation Notes

- 头像由 `api.downloadStatic` 拉字节 + `BitmapFactory` 解码为 `ImageBitmap`，随 URL 记忆；失败/加载中一律回退首字，不闪白。自己头像经 `AppRoot` 新增的 `selfAvatarUrl` 传入。
- 标题默认用 `LaunchedEffect(defaultTitle)`：`titleDirty=false` 时联动「提醒自己 / 提醒{昵称} / 提醒 N 人」；手动输入、AI 回填、草稿恢复都会把 `titleDirty` 置 true 并锁定。
- 时间/方式拆成两个 `ModalBottomSheet`；时间弹层用 Material3 `TimePicker`（320×400dp 让表盘保持正圆），大号时间实时反映 `timePickerState`；日期只留 今天/明天/后天/其他。
- AI 浮层使用 `smartText` 作为草稿文本；语音识别结果只写回 `smartText` 并打开浮层，不自动解析；`parsedOk` 为 true 时 `LaunchedEffect` 自动收起浮层；另有 `assistError` 专供浮层内展示（不被顶部提示条 3.5s 清除）。
- 根布局改为 `Box{ Column{ TopBar / 错误条 / Box(weight){ 滚动内容 + FAB } / 固定操作区 } }`；`imePadding()` 只加在滚动内容上，操作区在键盘弹起时保持原位置（被键盘覆盖）。
- 删除 `TIME_PRESETS`、`TIME_HINT_REGEX`、`dayChipLabel`、`selectAllRecipients`、`clearRecipients` 及旧时钟 `Dialog`。

## Verification Evidence（模拟器 emulator-5554）

- 收件 chips 显示真实头像照片（临时本地图床验证后已还原 DB）；无头像回退首字。
- 标题初始「提醒自己」；多选后「已选 2 人」，已编辑标题保持用户内容。
- 时间行「明天 08:00」→ 弹层大号 08:00 + 明天 + 双列滚轮，无预设网格。
- 方式行「单次 · 轻柔」→ 弹层单次/循环 + 语气，无时间项。
- FAB「咕咕帮填」→ 浮层：文本框 5/200、无语音服务置灰、帮我填表可点；失败文案在浮层内持续可见。
- 键盘弹起时创建按钮位置不变（被键盘覆盖），符合需求 4。
- V2.1：时间弹层滚动小时轮到 09、点分钟 01 → 大号时间同步 09:01 → 「就这个」后时间行显示「明天 09:01」；滑动滚轮时弹层不再被下拉关闭。
- V2.1：送给行显示 48dp 头像 + 下方昵称（用户8000 / 用户0641），无「送给/自己/亲友」角色文案，选中态有珊瑚环与右下勾。
- V2.2：Material3 表盘点 9 → 09:00，点「就这个」后时间行显示「明天 09:00」。
- V2.2：新建页按系统返回 → 看板；「我的」Tab 返回 → 看板；看板返回 → 应用内提示「再按一次退出布谷」未退出。
- V2.2：历史页、帮助与反馈页顶部返回按钮均为统一 44dp 圆形白底 ArrowBack 图标。

**Commands:**
- `cd app && JAVA_HOME=/Users/peng/Documents/phk/haohao/.toolchain/java-home PATH=$JAVA_HOME/bin:$PATH ./gradlew compileDebugKotlin --no-daemon --rerun-tasks` -- expected: BUILD SUCCESSFUL

**Manual checks（模拟器 emulator-5554）：**
- 送给行头像正确、无全选清空
- 标题默认与联动
- 键盘弹起按钮不动
- 时间/方式两个弹层
- 时间弹层无预设网格
- AI 浮层语音/填表闭环
