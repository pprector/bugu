# 布谷 App v2 优化方案报告

> 评估范围：Dashboard / CreateReminder / Companion / Profile 四端页面
> 评估方法：impeccable `critique`（启发式）+ `audit.native`（技术质量）双视角，以看板页为视觉标杆
> 优先级定义：**P0** 阻断体验/数据正确性 · **P1** 明显影响一致性或功能完整 · **P2** 打磨细节

---

## 一、看板设计语言（标杆基准，后续页面必须对齐）

| 维度 | 看板规范 | 关键数值 |
|---|---|---|
| 容器 | `LazyColumn` + `spacedBy(12.dp)` | 水平 padding 20dp |
| 背景 | `H.Canvas`(#FAF8F5) | — |
| 卡片 | 白底 + `RoundedCornerShape(20~24dp)` + `border(1.dp, H.Hairline)` | 内边距 h20 / v14~16 |
| 顶部锚点 | 咕咕鸟 52dp + 时辰问候 + 圆形 HeaderIconButton(44dp) | — |
| 动效 | `RiseIn`(淡入+上移18dp,380ms,错峰) / `pulseScale`(今天呼吸) / spring 分段指示条 | — |
| 色彩语义 | PENDING=Indigo / FIRED=Coral / ACKED=Mint / SNOOZED=Amber / DECLINED=Rose / 终态=Ink3Strong | — |
| 状态反馈 | 成功(MintTint) / 错误(RoseTint) 顶部条，"知道了"关闭 | — |
| 触控目标 | 所有可点元素 `heightIn(min = 44.dp)` | — |
| 字体层级 | 大标题 `headlineSmall`(20sp) / 卡片标题 `titleMedium`(15sp) / 辅助 `bodySmall`(13sp,H.Ink3Strong) | — |

**看板自身仅需小修（P2）：**
- `refresh()` 无互斥锁，快速连点会并发拉取；加 `if (refreshing) return` 守卫。
- `notice`/`error` 提示条无自动消失，建议 3.5s 后自动 dismiss（保留"知道了"）。
- 进行中卡 `EscalationCountdown` 每秒重算，可缓存减少重组。

---

## 二、新建惦记页（向看板对齐）

### P1 — 视觉风格对齐

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| N1 | 顶部仅返回箭头+居中标题，无咕咕鸟 | 缺少品牌锚点，与看板顶部割裂 | 顶部行引入 40dp 咕咕鸟(CHEER 态)+ 左侧返回，标题左对齐 `headlineSmall`，右侧放"草稿"入口（保存到本地草稿箱） |
| N2 | 表单卡用 `CircleIcon`(32dp 彩色圆角方块) 做行图标 | 看板无此组件，风格独异 | 改为看板同款：`Icon` + 行内标签，去掉彩色方块背景，用 `H.Ink3` 图标色 |
| N3 | 智能条话筒用 `Brush.linearGradient` 渐变 | 看板无渐变元素，突兀 | 改为纯色 `H.Coral` 圆底（与 `TagChip solid` 一致），去掉渐变 |
| N4 | "送给行"无卡片包裹，纯文字+chips | 视觉松散，与看板卡片节奏不一致 | 包进白卡 `RoundedCornerShape(20dp)` + hairline 边框，内加标题行"把惦记送给谁" |
| N5 | 无 `RiseIn` 入场动效 | 与看板列表入场节奏不一致 | 表单卡、送给卡、操作区分别加 `RiseIn` 错峰入场 |

### P1 — 交互体验

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| N6 | 时间·方式弹层"就这个"按钮在内容底部 | 长内容需滚到底才能关闭 | 弹层顶部加固定关闭按钮(`HeaderIconButton` X)，底部"就这个"保留作确认 |
| N7 | 解析失败时 `phraseError` 只在智能条上方显示 | 错误离输入框远，且无视觉锚点 | 改为看板同款 `AnimatedVisibility` 顶部 tint 提示条(Mint/Rose)，全页可见 |
| N8 | 多选接收人无全选/清空快捷 | 多选场景操作繁琐 | 送给卡右上角加"全选/清空"胶囊(选中 >1 人时出现) |
| N9 | `isPast` 仅在时刻行内提示"改到明天" | 提交时才二次拦截，体验割裂 | 过去时刻时主按钮直接变"改到明天再创建"，一键修复+创建 |

### P2 — 业务逻辑严谨性

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| N10 | `doParse()` 内 `delay(170)` 硬编码逐字段填入 | 快速重复解析可能竞态 | 用 `Mutex` 或 `Job?.cancel()` 串行化解析，避免旧结果覆盖新结果 |
| N11 | `resetForm()` 不重置 `selectedRecipientIds` | 创建后接收人残留，易误发 | 重置为 `[userId]`（默认送自己） |
| N12 | 语音识别结果直接 `doParse()`，无 loading 态 | 识别→解析期间用户无反馈 | 智能条上方显示"听咕咕在想…"（复用 parsing 态） |

---

## 三、陪伴页（向看板对齐，最需重做）

> 当前陪伴页是全 App 视觉最弱的页面，无卡片边框、无动效、无品牌锚点，与看板割裂最严重。

### P0 — 视觉重做（对齐看板）

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| C1 | 列表项无 `border(H.Hairline)` | 卡片无边界感，漂浮 | 所有白卡加 `border(1.dp, H.Hairline, RoundedCornerShape(22.dp))`，与看板一致 |
| C2 | 右箭头用 `Text("›")` | 视觉粗糙，非矢量 | 改为 `Icons.AutoMirrored.Filled.KeyboardArrowRight`，tint `H.Ink3` |
| C3 | 顶部"添加亲友"用 `TextButton` | 与看板 `HeaderIconButton` 不一致 | 顶部行：咕咕鸟(44dp,RING态)+ 标题"陪伴" + 圆形"添加"按钮(Icon.PersonAdd) |
| C4 | 无 `RiseIn` 动效 | 列表静态出现 | 列表项加 `RiseIn(delayMs = i*40)` 错峰入场 |
| C5 | loading 直接 `CircularProgressIndicator` 裸放 | 无容器，视觉散 | 包进白卡居中，配文案"正在同步互伴关系…" |
| C6 | 副标题"让每一份惦记，都有回应。"无层级 | 与标题挤在一起 | 改为看板同款：`bodySmall` + `H.Ink3Strong`，与标题间距 4dp |

### P1 — 陪伴详情页对齐

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| C7 | 详情页"双方提醒"列表项极简（仅标题+时间+状态） | 比看板列表项信息密度低 | 复用看板 `ReminderDisplay` 卡片：左侧大时间 + 状态 TagChip + 右侧操作（撤销/三出口） |
| C8 | "给XX发提醒" `GradientButton` 裸放 | 无卡片包裹，突兀 | 包进白卡，上方加"为 TA 设一条惦记"引导文案 |
| C9 | 静音开关只控制 ONCE | REPEAT/MUTUAL_ALARM 静音状态不可见 | 改为分段静音：单次提醒 / 循环提醒 两个独立开关，分别调 `mute(kind)` |
| C10 | 无 `RiseIn` / `TagChip` 等看板组件 | 详情页视觉独立 | 全页改用看板卡片规范 + `RiseIn` 入场 |

### P2 — 业务逻辑

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| C11 | `CompanionDetailScreen` 拉取 `api.myReminders()+api.incomingReminders()` 全量再本地过滤 | 数据量大时低效 | 服务端补 `remindersByBond(bondId)` 接口（如已有则改用），减少传输 |
| C12 | 解除关联后 `onBack()` 但 `bonds` 未及时刷新前可能闪回 | 时序依赖外部 `onRefreshBonds` | 解除成功后本地 `bonds` 列表移除该 bond，再 `onBack`，避免闪回 |

---

## 四、我的页面（保证功能完整性）

> 当前功能已覆盖：头像/昵称/手机号/互伴卡/历史/大字模式/权限诊断/帮助反馈/关于/退出。
> 缺口集中在**数据反馈、消息中心、账号安全、互伴快捷操作**。

### P1 — 功能补强

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| M1 | 无个人数据统计 | 长辈用户缺少成就反馈 | 头部互伴卡下方加"惦记统计"白卡：发出 X / 收到 Y / 已完成 Z（本月），数据来自 `myReminders+incomingReminders` 聚合 |
| M2 | 无 App 内消息中心 | 绑定请求、惦记回执、系统公告无处查看 | 新增"消息"入口（HeaderIconButton 铃铛），列表分三类：互伴动态 / 惦记回执 / 系统通知；未读红点 |
| M3 | 无账号安全入口 | 无换绑手机号、注销账号（合规风险） | "通用"分组加"账号与安全"行：换绑手机号 / 注销账号（注销走二次确认+服务端软删） |
| M4 | 互伴管理仅一个跳转卡 | 无法在我的页直接静音/备注 | 互伴卡展开为可滚动行：每位互伴一行(头像+昵称+静音开关+备注入口)，点击进详情 |
| M5 | 大字关怀模式无预览 | 开关后无即时反馈 | 开关下方加预览区：用当前字号渲染"示例惦记标题"+"示例副标题"，切换实时反映 |

### P2 — 视觉对齐看板

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| M6 | `MenuRow` 图标统一 `H.Coral` | 无语义色彩 | 按功能分色：历史=Mint / 大字=Indigo / 权限=Coral / 帮助=Amber / 关于=Ink2，对齐看板语义色 |
| M7 | 退出登录行未复用 `MenuRow` | 代码重复 | 抽取 `MenuRow` 支持 `trailingIcon` + `tint` 参数，退出行复用之 |
| M8 | 头像加载无骨架屏 | 网络慢时空白 | `EditableAvatar` 加 `AnimatedVisibility` 骨架渐变占位，位图加载完成后替换 |

### P1 — 业务逻辑严谨性

| # | 现状 | 问题 | 优化方案 |
|---|---|---|---|
| M9 | 头像上传失败后 `avatarBitmap` 仍为 null，但 `user.avatarUrl` 可能已是旧值 | `LaunchedEffect(user.avatarUrl)` 因 url 未变不重拉，永远显示首字 | 上传失败时不更新 `user.avatarUrl`，或失败后强制重置 `avatarBitmap=null` 触发重拉 |
| M10 | 昵称修改 `AlertDialog` 内 `nickname` 初始值为 `user.nickname`，但用户在对话框改过又取消后，下次打开残留上次输入 | 状态未重置 | 打开对话框时 `nickname = user.nickname` 重置（当前已有，但需确认 `editingNickname` 切换时执行） |

---

## 五、全局一致性（跨页面）

### P1 — 组件统一

| # | 问题 | 优化方案 |
|---|---|---|
| G1 | 看板用 `HeaderIconButton`，新建/陪伴/我的各写各的返回按钮 | 抽取 `TopBar(title, onBack, actions)` 公共组件，四页统一，含咕咕鸟可选 |
| G2 | 状态色 `statusLabel/statusColor` 定义在 DashboardScreen 内，陪伴详情页复用不到 | 上移到 `ui/common/ReminderStatus.kt`，全局共享 |
| G3 | 空态：看板用 `EmptyState`(咕咕鸟)，陪伴/详情用纯文字 | 所有空态统一用 `EmptyState` 组件，配对应 mood |

### P1 — 无障碍

| # | 问题 | 优化方案 |
|---|---|---|
| G4 | `CircleIcon`、`AckChip`、`TagChip` 大量 `contentDescription = null` | 交互元素补 `contentDescription`；纯装饰图标保留 null |
| G5 | 陪伴详情页 `BrandSwitch` 有 `contentDescription`，但新建页 `BrandSwitch` 无 | 所有 `BrandSwitch` 统一补语义描述 |
| G6 | 颜色对比度：`H.Ink3`(#9C9488) 在白底对比度约 3.2:1，未达 4.5:1 | 正文辅助色统一用 `H.Ink3Strong`(#6B655C)，`H.Ink3` 仅用于极次要文案 |

### P2 — 动效规范

| # | 问题 | 优化方案 |
|---|---|---|
| G7 | 各页入场动效不统一（看板 RiseIn / 新建无 / 陪伴无） | 所有列表项/卡片统一 `RiseIn` 错峰，形成 App 级节奏 |
| G8 | 点击反馈仅 `clickable`，无按压态 | 公共点击容器加 `indication = rememberRipple()`，统一水波纹反馈 |

---

## 六、优先级排期总表

| 优先级 | 页面 | 编号 | 事项 | 预估 |
|---|---|---|---|---|
| **P0** | 陪伴 | C1-C6 | 陪伴页视觉重做(对齐看板卡片/边框/箭头/动效) | 4h |
| **P0** | 我的 | M1 | 惦记统计卡(数据反馈) | 2h |
| **P0** | 我的 | M3 | 账号安全(换绑/注销) | 3h |
| **P0** | 全局 | G2 | 状态色上移公共模块 | 1h |
| **P0** | 全局 | G6 | Ink3→Ink3Strong 对比度修复 | 1h |
| **P1** | 新建 | N1-N5 | 视觉对齐看板(咕咕鸟/图标/渐变/卡片/动效) | 5h |
| **P1** | 新建 | N6-N9 | 弹层关闭/错误提示/多选快捷/过去时刻修复 | 3h |
| **P1** | 陪伴 | C7-C10 | 详情页对齐看板卡片+分段静音 | 4h |
| **P1** | 我的 | M2 | 消息中心 | 6h |
| **P1** | 我的 | M4-M5 | 互伴快捷操作+大字预览 | 3h |
| **P1** | 全局 | G1 | TopBar 公共组件 | 2h |
| **P1** | 全局 | G3 | EmptyState 统一 | 1h |
| **P1** | 全局 | G4-G5 | 无障碍 contentDescription | 2h |
| **P2** | 看板 | 看板小修 | refresh 互斥/提示自动消失 | 1h |
| **P2** | 新建 | N10-N12 | 解析串行/重置接收人/语音 loading | 2h |
| **P2** | 陪伴 | C11-C12 | 接口优化/解除关联防闪回 | 2h |
| **P2** | 我的 | M6-M10 | 图标语义色/MenuRow 复用/骨架屏/头像逻辑 | 3h |
| **P2** | 全局 | G7-G8 | 入场动效统一/水波纹反馈 | 2h |

**合计：P0 约 11h · P1 约 28h · P2 约 10h**

---

## 七、落地建议

1. **先做陪伴页 P0**：视觉重做投入产出比最高，做完即与看板统一。
2. **再做我的页 P0**：统计卡 + 账号安全，补功能完整性。
3. **全局 G2/G6 同步**：状态色上移 + 对比度修复，影响所有页面但改动小。
4. **新建页 P1 随后**：视觉对齐 + 交互打磨。
5. **消息中心(M2)单独排期**：涉及服务端接口，可与后端联调。

每完成一个 P0/P1 批次，用 `mobile-android-design` skill 做一次回归审查，确保不偏离看板标杆。
