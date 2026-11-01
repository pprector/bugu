# Deferred Work

- source_plan: `/Users/peng/Documents/phk/bugu/_bmad-output/plan-p0-align-dashboard-visual-and-profile-core.md`
  summary: 我的页惦记统计卡加载失败仅显示「—」，无 in-page 重试入口，需离开重进才会重新拉取。
  evidence: ProfileScreen.kt LaunchedEffect(Unit)+runCatching 失败后状态保持 null，StatColumn 显示「—」；计划 I/O 矩阵仅要求显示「—」，重试属健壮性增强。

- source_plan: `/Users/peng/Documents/phk/bugu/_bmad-output/plan-p0-align-dashboard-visual-and-profile-core.md`
  summary: 「本月已完成」计数基于 fireAt 月份而非确认时间，上下月跨期确认的提醒会被错计。
  evidence: ReminderView 仅有 fireAt/createdAt 字段无 ackedAt，受「不改服务端」约束无法在端侧修正；需服务端新增确认时间字段。

- source_plan: `/Users/peng/Documents/phk/bugu/_bmad-output/plan-p0-align-dashboard-visual-and-profile-core.md`
  summary: ProfileScreen 既有白卡（互伴卡/大字模式/退出登录/MenuRow）缺少 hairline 边框，与新增统计卡视觉不一致。
  evidence: 这些卡片在本变更前已存在且无边框；计划 Boundaries 要求所有白卡加 border，但既有卡片未在本计划范围内一并修复。

- source_plan: `/Users/peng/Documents/phk/bugu/_bmad-output/plan-p1-create-reminder-align-dashboard.md`
  summary: 「全选」接收人未校验 20 人上限，好友数 >19 时全选会超限直到 doCreate 才报错。
  evidence: CreateReminderScreen.kt selectAllRecipients 直接赋值全部好友；toggleRecipient 有 size>=20 拦截并弹 snack，但全选路径缺失同等校验。建议全选时截断到 20 人并提示。

- source_plan: `/Users/peng/Documents/phk/bugu/_bmad-output/plan-p1-create-reminder-align-dashboard.md`
  summary: 草稿恢复按钮图标用 EditNote，与标题行图标撞款，语义不准。
  evidence: CreateReminderScreen.kt 草稿入口 Icon 为 Icons.Rounded.EditNote；建议换 Icons.Rounded.History 或 Icons.Outlined.Drafts 区分语义。

- source_plan: `/Users/peng/Documents/phk/bugu/_bmad-output/plan-p1-create-reminder-align-dashboard.md`
  summary: 顶部错误条 phraseError 优先级遮蔽 formError，新 formError 在 phraseError 未消失时被遮蔽。
  evidence: CreateReminderScreen.kt topError = phraseError ?: formError；建议改为并列展示或让新错误重置 3.5s 计时。

- source_plan: `/Users/peng/Documents/phk/bugu/_bmad-output/plan-v2-create-reminder.md`
  summary: 时间选择未实现华为式「双击分钟切换 1/5 分钟步进」，Material3 TimePicker 不支持该交互。
  evidence: 华为官方支持文档说明双击分钟数字可切换 5 分钟快调；当前使用 Material3 TimePicker 表盘，如需完全对齐需自绘小时/分钟双列滚轮组件。

- source_plan: none
  summary: P0 页面品牌重做：添加亲友、我的 3 个二级页、权限引导、全屏响铃。
  evidence: 从「品牌底座」目标拆分；这四组页面依赖新的共享品牌组件与弹窗体系，但各自可独立验收、合并，且完整重做超出单次 BMad Build 的 500 行/单目标范围。

- source_plan: none
  summary: P1 页面风格统一：陪伴详情、日历、惦记历史、我的、新建惦记。
  evidence: 这些页面已有可用结构，主要问题为页头/卡片/动效/文案不一致；应在品牌底座落地后逐页迁移，避免与底座重复返工。

- source_plan: none
  summary: P2 品牌打磨：Snackbar/Toast、无障碍、emoji 图标化、底部导航与加载态。
  evidence: 属收尾级一致性项，不阻塞 P0/P1；放在共享组件稳定后统一处理成本最低。
