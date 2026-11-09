# 布谷安卓端「陪伴」Tab 能力界定与互伴管理设计报告

> 版本：v1.1 · 2026-10-30
> 范围：Android App（Kotlin + Jetpack Compose）· 服务端 bond / reminder 契约 · 官网与品牌口径
> 结论先行：**1v1 是策略约束而非数据限制，放开多互伴的成本可控。改造后「陪伴」一级页演进为"互伴列表 + 关系详情"；好友管理不进入一级导航，而是收敛为"添加互伴 / 邀请在途 / 解除关系"的管理动作。完整改造路径见 §12。**

---

## 摘要

1. **多互伴可以放开，且成本低于预期。** `bond` 表、`receiver_mute_setting`、`reminder` 都按"人 / 关系"建模，仅 `requireNoActiveBond` 一处硬校验；真正的改造量在邀请在途、同对去重、并发上限与端上信息架构，而不在数据库重写。
2. **当前「陪伴」页只是状态面板，未承担关系主页职责。** 页面内容为对端卡、长辈模式开关、单次惦记静音、最近 3 条回执和"去好友页"按钮；与「我的」页的互伴卡、独立好友页形成三处重复入口，没有任何一处是完整的"关系主页"。
3. **最影响陪伴感的问题不是缺功能，而是对方身份字段未下发。** 客户端 `BondDto` 预留了昵称、关系标签、长辈模式字段，但服务端 DTO 只返回 id、状态、角色、时间，导致线上长期显示"家人"，关系标签恒为"互伴家人"，长辈模式冷启动恒为关闭。
4. **改造顺序：** 先服务端解除 1v1 并补身份字段，再端上改为"列表 + 详情"，最后补轻碰、关系小结等关系感能力。建议把上限定为 5 人（家人圈），不做开放好友、不做搜索推荐。
5. **好友管理的归位：** 一级「陪伴」展示互伴列表；"添加互伴"是列表页顶部动作；邀请在途、解除关系收进二级管理页。独立好友页改造为"添加 / 邀请"页，不再是平级入口；"互伴"作为统一命名，"好友"仅用于文案解释。

---

## 1. 研究范围与方法

本报告以代码库为第一手证据，外部公开资料仅用于校准同类产品形态与平台规范，不替代本项目事实。

| # | 研究问题 | 关联决策 | 方法 | 判断标准 |
| --- | --- | --- | --- | --- |
| 主 | 「陪伴」Tab 应承载哪些能力，好友管理应放在哪里？ | 是否重构 Tab 定位与页面归属 | 代码库取证 + 竞品形态对照 + 平台规范核对 | 每条能力都能追溯到现有接口或明确的改造项；不引入无后端支撑的功能 |
| 1 | 当前关系模型是 1v1 还是多好友？ | 决定是否做列表型好友管理 | 读 schema、Service 约束、UI 取数逻辑 | 三处证据一致即定性 |
| 2 | 现有页面与接口的能力缺口在哪里？ | P0 改造清单 | 逐页对照 api 契约与 DTO | 缺口能落到具体文件与字段 |
| 3 | 陪伴感缺失的首要原因是什么？ | 优先级排序 | 页面实际渲染路径推演 | 能解释"为什么线上显示家人" |

**证据强度与局限：** 本报告没有用户访谈、问卷或埋点数据，涉及用户偏好与优先级的判断均标注为假设，验证方案见 §9。代码结论以当前工作区为准。

---

## 2. 现状盘点

### 2.1 能力矩阵

| 能力 | 现有入口 | 服务端支撑 | 端上实现 | 主要问题 |
| --- | --- | --- | --- | --- |
| 绑定码发起 / 确认 | 好友页 | `POST /bond/initiate`、`/confirm` | 完整 | 进入好友页即自动发起绑定码；无分享、无取消 |
| 解除绑定 | 好友页 | `POST /bond/{id}/unbind` | 完整（二次确认） | 入口埋在好友页；「我的」「陪伴」各自再导流一次 |
| 对方身份展示 | 陪伴页 / 好友页 | **DTO 未下发昵称、关系标签、长辈模式** | 兜底"家人" | 关系主页无法以"人"为主角 |
| 长辈模式代管 | 陪伴页 | `POST /bond/elder-mode` | 完整（仅发起方） | 状态不随绑定刷新回填；被代管方无告知 |
| 单次惦记静音 | 陪伴页 | `PUT/DELETE /reminder/mute` | 仅 `ONCE` | `REPEAT`、`MUTUAL_ALARM` 无 UI |
| 最近回执 | 陪伴页 | `GET /reminder/incoming` | 取前 3 条 | 只展示 TA 发来的；无"我发出的"对照；无已读概念 |
| 快捷送惦记 | 新建页 | `POST /reminder` | 完整 | 陪伴页没有主 CTA，需绕到新建页 |
| 互叫闹钟 | 新建页 | `MUTUAL_ALARM` 全链路 | 完整 | 陪伴页未暴露为关系动作 |
| 长辈免注册接入 | 无 | `POST /auth/elder-register` | 仅 API | 子女端无邀请入口，长辈端无引导 |
| 轻碰 / 想你了 | 无 | 无 | 无 | 官网已对外承诺"指尖轻碰" |
| 关系统计 / 连续天数 | 无 | 无 | 无 | 无关系感沉淀 |

### 2.2 三个结构性问题

**（1）关系模型是 1v1，界面却按"好友"叙事。**
服务端在发起与确认时都执行 `requireNoActiveBond`，冲突时返回"已有绑定关系，上限 1 条"（[BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L172-L176)）。数据库 `bond` 表没有唯一约束、`receiver_mute_setting` 按"接收人×发起人×类型"建模、`reminder` 按 `bond_id` 归属，说明底层其实具备多关系能力，**1v1 是业务层策略而非数据结构限制**。但端上 `CompanionScreen` 只取第一条 ACTIVE（[CompanionScreen.kt](app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt#L71)），新建页已按多 chip 渲染（[CreateReminderScreen.kt](app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt#L1106-L1135)），两种叙事并存。

**（2）对方身份字段在契约层断裂。**
服务端 `BondDto` 只有 `id/status/role/counterpartId/createdAt/confirmedAt`（[BondDtos.kt](server/src/main/kotlin/com/bugu/bond/api/BondDtos.kt#L27-L40)），映射函数也不补对方资料（[BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L204-L211)）。客户端却已预留 `counterpartNickname / counterpartRelationTag / counterpartElderMode` 三个可选字段（[BondModels.kt](app/src/main/kotlin/com/bugu/client/api/BondModels.kt#L35-L53)），AppRoot 以"家人"兜底（[AppRoot.kt](app/src/main/java/com/bugu/ui/AppRoot.kt#L364-L367)）。结果是：**绑定成功后，陪伴页永远显示"家人"，长辈模式开关永远从"关"开始。** 这直接摧毁了"有人惦记你"的关系感。

**（3）关系状态机不完整。**
一端发起绑定码后，只能被动等待；服务端没有绑定成功推送，端上也无轮询或角标（push 模块只处理提醒事件）。用户取消等待也无接口——`unbind` 仅对 ACTIVE 生效。更严重的是，确认时只校验**确认方**没有 ACTIVE 关系，未校验**发起方**：A 发起码后与他人绑定成功，旧码仍可被第三人确认，导致 A 出现两条 ACTIVE 关系，破坏 1v1 不变量（[BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L102-L126)）。

### 2.3 入口重复

| 页面 | 互伴相关入口 | 说明 |
| --- | --- | --- |
| 陪伴 Tab | 空态"去绑定互伴"、底部"去好友页管理互伴" | Tab 变成跳板 |
| 我的 Tab | 互伴状态卡，点击进好友页（[ProfileScreen.kt](app/src/main/java/com/bugu/ui/screens/ProfileScreen.kt#L224-L257)） | 与陪伴页信息重复 |
| 新建 Tab | "去绑定"轻提示 | 合理，但应指向同一向导 |
| 好友页 | 发起码、确认码、解绑 | 承担了建立与管理两种职责 |

---

## 3. 定位判断：陪伴不是好友管理

> 本节判断以 1v1 为前提。放开多互伴后的修订见 §12：定位不变（仍是关系空间，不是社交广场），但一级页从"单关系主页"演进为"互伴列表 + 关系详情"。

**产品口径支持"关系主页"而非"好友列表"。** 品牌信息屋第三根支柱是"有人惦记你"，并把好友互相惦记、指尖轻碰、陪伴页并列为证据（[布谷-品牌信息体系.md](brand/布谷-品牌信息体系.md#L60)）；官网把"互相惦记 / 指尖轻碰 / 长辈模式 / 安静陪伴"作为陪伴模块的四项能力（[CompanionSection.vue](website/src/components/CompanionSection.vue#L2-L7)）。两处叙事的主角都是"一个重要的人"，不是"一组好友"。

**同类产品同样以关系空间为单位。** Between 以"只属于两个人的私密空间"组织聊天、照片与日程[cite:1]；Paired 以每日问答与双人练习驱动关系互动[cite:2]；Love Nudge 以"轻推 + 目标 + 关系进度"组织双人体验[cite:3][cite:4]。共性是没有一个把"好友管理"放在一级导航；一级导航承载的是关系空间本身。

**做好友管理的三项直接代价：**

| 代价 | 具体表现 |
| --- | --- |
| 结构矛盾 | 服务端上限 1 条 ACTIVE，列表页最多一行；"添加好友""好友分组""好友搜索"全部无意义 |
| 品牌稀释 | 1v1 私密关系被做成轻社交，与"不鸽保证""安静陪伴"的信任感冲突 |
| 工程浪费 | 需要新增关系列表、权限、资料页、消息通知等一整套能力，而当前真正的缺口是身份字段与状态机 |

**结论：**「陪伴」= 关系主页（Relationship Hub）；「好友管理」= 互伴管理（关系设置 + 绑定/解绑流程）。二者是包含关系，不是并列关系。若未来决定支持多互伴，再让「陪伴」演进为"关系列表 + 关系详情"，此时好友管理才成立；这属于 §11 的战略决策，不应由界面设计先行假设。

---

## 4. 用户任务与设计原则

### 4.1 三类任务（JTBD）

| 任务 | 用户问题 | 对应界面能力 |
| --- | --- | --- |
| 确认关系还在 | "TA 还在吗？我们的惦记还算数吗？" | 关系头卡、绑定天数、最近互动、状态 chip |
| 快速表达惦记 | "现在就想让 TA 知道我在意" | 主 CTA"送一条惦记"、轻碰、互叫闹钟 |
| 把关系调舒服 | "别太吵、爸妈看得清、谁在替我管" | 静音、长辈模式、关系标签、解绑 |

### 4.2 五条设计原则

1. **人物优先：** 首屏第一块必须是"对方是谁 + 关系处于什么状态"，而不是开关列表。
2. **一个主 CTA：** 已绑定态的主操作只有"给 TA 设一条惦记"；其余动作降为次级或收纳。
3. **状态可见且可解释：** 等待确认、长辈模式、静音必须显示"谁设置的、对什么生效"，代管要透明。
4. **低频设置下沉：** 关系标签、静音细分、解绑进入二级页；一级页不出现危险操作。
5. **不新增叙事：** 不引入聊天、动态、好友推荐；所有能力必须能映射到既有 `bond` / `reminder` 模型或明确的新增契约。

---

## 5. 能力清单与优先级

### 5.1 P0：把现有闭环做通（不做新功能也能显著提升）

| # | 能力 | 用户价值 | 依赖 | 说明 |
| --- | --- | --- | --- | --- |
| P0-1 | 关系档案头卡 | 让"家人"变成"妈妈" | 服务端 `BondDto` 补 `counterpartNickname / avatarUrl / relationTag / elderMode` | 一次契约修复，三处页面同时受益 |
| P0-2 | 回执时间线 | 让"有回音"可被看见 | 现有 `incoming` + `mine` | 分段"TA 的 / 我的"，展示状态、时间、类型；空态给出发第一条 |
| P0-3 | 快捷送惦记 | 降低表达成本 | 复用新建页，预选对方 | 陪伴页主 CTA，跳转时带入 `bondId` |
| P0-4 | 互伴管理二级页 | 给设置一个家 | 现有 elder-mode / mute / unbind | 关系标签、长辈模式、三类静音、解绑全部归位 |
| P0-5 | 绑定向导重构 | 降低首次绑定流失 | 现有 initiate/confirm | 未绑定空态直接承载两条路径；支持系统分享绑定码；支持取消等待（需新增接口） |
| P0-6 | 关系状态机修复 | 数据正确性与信任 | 服务端 | 确认时校验发起方无其他 ACTIVE；过期码失效；绑定成功推送/角标 |
| P0-7 | 入口去重 | 消除"三处跳转" | 端上 | 「我的」移除互伴卡或降为一行；陪伴页不再以"去好友页"收尾 |

### 5.2 P1：补关系感

| # | 能力 | 说明 | 前提 |
| --- | --- | --- | --- |
| P1-1 | 轻碰 / 想你了 | 一键向对方发一个无内容轻提醒，带频率限制与免打扰 | 新增 `poke` 端点与表；官网承诺需兑现[cite:3][cite:4] |
| P1-2 | 关系小结 | 本周互发数、完成率、连续互叫天数 | 新增统计查询；先端上聚合，后服务端固化 |
| P1-3 | 长辈账号接入 | 子女端生成长辈邀请码/二维码与三步说明；长辈端首启引导 | 已有 `elder-register` API，缺 UI |
| P1-4 | 绑定结果通知 | 对方确认后推送发起方；Tab 显示注意点 | push 模块新增 bond 事件 |
| P1-5 | 关系标签编辑 | 普通用户也能改"妈妈/爸爸/恋人" | 现仅 `elder-profile` 可写，需要通用端点 |

### 5.3 P2：需要产品决策

| # | 能力 | 说明 |
| --- | --- | --- |
| P2-1 | 多互伴 | **已前置为 P0**，详见 §12；底层 schema 已具备条件，主要是策略、邀请在途与 UI 改造 |
| P2-2 | 纪念日 / 关系日 | 用 `REPEAT` 年循环承载，避免新增表 |
| P2-3 | 导航形态调整 | 将"新建"从中置 Tab 改为真正的 FAB，导航栏回到 3 个目的地，符合 M3"3–5 个目的地"规范[cite:7] |

### 5.4 明确不做

| 不做项 | 理由 |
| --- | --- |
| IM 聊天 / 语音通话 | 与"安静陪伴"冲突，且无消息模型 |
| 动态 / 朋友圈 | 1v1 关系无公共场域 |
| 好友搜索 / 推荐 / 通讯录导入 | 服务端上限 1 条，能力无处落地 |
| 关系排行榜 / 打卡竞赛 | 会把陪伴变成压力，违背"永远不责备"的品牌声音 |

---

## 6. 信息架构与页面设计

### 6.1 目标 IA

```
底部导航（4 目的地：看板 / 新建 / 陪伴 / 我的）
└─ 陪伴（关系主页）
   ├─ 状态 A：未绑定
   │   ├─ 价值说明（绑定后你们的惦记会互相看见）
   │   ├─ 路径 1：我的绑定码（大数字 + 复制/系统分享 + 有效期）
   │   ├─ 路径 2：输入对方的 6 位码
   │   └─ 次级入口：帮爸妈装布谷（P1）
   ├─ 状态 B：等待对方确认
   │   ├─ 绑定码 + 有效期 + 重新生成 + 分享
   │   ├─ 取消等待（P0-5 新增）
   │   └─ 状态说明（对方确认后会通知你）
   ├─ 状态 C：已互伴
   │   ├─ 关系档案卡（头像 / 昵称 / 关系标签 / 绑定天数 / 角色）
   │   ├─ 主 CTA：给 TA 设一条惦记
   │   ├─ 关系动作：互叫闹钟 / 轻碰（P1）
   │   ├─ 最近回执（分段：TA 的 / 我的）
   │   └─ 右上角"管理" → 互伴管理
   └─ 互伴管理（二级页）
       ├─ 对方资料与关系标签（P1-5）
       ├─ 长辈模式（仅发起方；显示"由你代管"）
       ├─ 静音设置（单次 / 重复 / 互叫，按类型分组）
       ├─ 长辈账号接入（P1-3）
       └─ 危险区：解除互伴（二次确认 + 即时隔离说明）
```

### 6.2 已互伴态线框（首屏）

```
┌───────────────────────────────┐
│ 陪伴                      ⚙︎  │  56dp 顶栏，管理入口
├───────────────────────────────┤
│  (头像)  妈妈   [妈妈]         │  关系档案卡
│          已互伴 86 天 · 你发起的│  身份 + 时长 + 角色
│          长辈模式未开启        │  状态可解释
│  [ 轻碰一下 ]（P1）            │  低负担表达
├───────────────────────────────┤
│  ▸ 给妈妈设一条惦记            │  唯一主 CTA
├───────────────────────────────┤
│  互叫闹钟      静音      长辈  │  次级动作，44dp+
├───────────────────────────────┤
│  最近回执        TA 的 | 我的  │  分段切换
│  · 吃药 已触发 10:30          │
│  · 早点睡 已完成 昨天         │
│  查看全部 →                    │
└───────────────────────────────┘
```

设计要点：身份卡不使用纯白大卡片堆叠（现有页面连续 3 张白卡），改为"1 张关系卡 + 1 个动作区 + 1 个回执区"，降低首屏卡片密度；回执区默认展示 5 条，避免无限滚动把设置挤出屏幕。

### 6.3 三态设计规范

| 状态 | 主文案 | 主 CTA | 次 CTA | 备注 |
| --- | --- | --- | --- | --- |
| 未绑定 | "和重要的人，互相惦记" | 生成我的绑定码 | 输入对方的码 | 不再一进页就自动发码；由用户显式选择路径 |
| 等待确认 | "等 TA 输入绑定码" | 分享绑定码 | 取消等待 | 显示有效期倒计时；过期自动切回未绑定 |
| 已互伴 | "给{昵称}设一条惦记" | 送惦记 | 轻碰 / 互叫 | 关系标签缺失时回退"家人"，但需有编辑入口 |

空态需包含五要素：图示、对话式标题、一句价值说明、一个主按钮、一个次路径[cite:9]。现有 `EmptyState` 已有标题/副标题/动作三要素，补图示与次路径即可。

### 6.4 其他页面调整

| 页面 | 调整 |
| --- | --- |
| 我的 | 删除互伴状态大卡；改为"互伴：妈妈 ›"一行轻入口，避免与陪伴页重复 |
| 好友页 | 保留为实现载体，改名"互伴设置"或"绑定互伴"；从陪伴页空态/管理页进入，不再由「我的」直达 |
| 新建页 | "送给"行保持"自己 + 互伴"；"去绑定"指向同一绑定向导；创建成功回看板而非陪伴页 |
| 看板 | 不承载关系管理；只在空态提示"绑定互伴后可把惦记送给 TA" |

---

## 7. 交互与视觉规范（Compose / Material 3）

| 维度 | 规范 | 现状与动作 |
| --- | --- | --- |
| 导航 | 底部导航承载 3–5 个同级目的地，目的地应稳定[cite:7] | 当前 4 槽（3 Tab + 中置"新建"）。建议 P2 将"新建"改为 FAB，导航回到 3 目的地 |
| 触控 | 主要组件 ≥48dp；适老版主要焦点 ≥60dp[cite:5][cite:6] | 现有 chips 已 ≥44dp；陪伴页开关行需保证整行可点，长辈模式下主 CTA 抬到 60dp |
| 语义 | 开关用 `Role.Switch` + 状态描述；可点卡片给 `contentDescription`；装饰头像置 null | 现有 `BrandSwitch` 需补 `stateDescription`（"已开启/已关闭"） |
| 状态表达 | 不只靠颜色；chip 文案与图标同时表达 | 现有 `TagChip` 已带文案，保持；长辈模式/静音加"谁设置"说明 |
| 错误与离线 | 保留旧数据，显示可重试，不把"已绑定"误判为"未绑定" | 现有 `bondsError` 已按此实现，扩展到回执区与设置操作 |
| 加载 | 身份卡用骨架/占位，回执区用行内 loading，不用全屏 spinner | 现为局部 spinner，可保留 |
| 动效 | 列表错峰 40–70ms；按压弹簧；不做炫技转场 | 现有 `RiseIn` 与 Tab 弹跳已符合，避免每次重组重放 |
| 色彩 | 暖底 + 珊瑚主色 + 薄荷点缀；不启用 Material You 动态取色 | 与 M3 默认"支持动态色"相反，本项目应以品牌一致性优先，显式关闭动态色 |
| 危险操作 | 解绑放二级页危险区，二次确认，说明"即时隔离、不可恢复" | 现有弹层文案合格，位置需从好友页移入管理页 |

**长辈代管的透明性：** 当前发起方可单方面开关对方长辈模式（[CompanionScreen.kt](app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt#L99-L116)），被代管方无告知。建议在对方端以一条系统内提示说明"由{昵称}为你开启"，并在管理页显示"由你代管"。远程代管本身有成熟先例（家庭管理类产品以监护人集中设置为主[cite:8]），但必须可见、可撤销。

---

## 8. 数据与接口改造清单

| # | 改动 | 端 | 说明 | 风险 |
| --- | --- | --- | --- | --- |
| D1 | `BondDto` 增加 `counterpartNickname / counterpartAvatarUrl / counterpartRelationTag / counterpartElderMode` | 服务端 | 在 `toDto` 中联查 `app_user` 与 `bond.relation_tag` | 需避免 N+1，列表场景一次批量查 |
| D2 | 确认绑定校验发起方不变量 | 服务端 | `confirm` 增加"发起方无其他 ACTIVE"校验，或用户进入 ACTIVE 时失效其 PENDING 码 | 修复双 ACTIVE 数据风险 |
| D3 | 取消等待 / 过期清理 | 服务端 | 新增 `POST /bond/{id}/cancel` 或复用 unbind 支持 PENDING | 与"重复发起幂等续期"逻辑对齐 |
| D4 | 通用关系标签更新 | 服务端 | `PATCH /bond/{id}` 仅允许双方改 `relationTag` | 现仅 `elder-profile` 可写 |
| D5 | 绑定成功推送 | 服务端 + 端 | push 模块新增 bond confirmed 事件 | 复用现有 device_token |
| D6 | 静音三类暴露 | 端 | 管理页按 `ONCE / REPEAT / MUTUAL_ALARM` 分组 | 接口已支持 |
| D7 | 轻碰端点 | 服务端 + 端 | 新表 `companion_poke`（sender/receiver/bond/created_at），限频 + 免打扰 | 防骚扰边界 |
| D8 | 回执已读/角标 | 端优先 | 本地记录 lastSeenAt，推导 Tab 角标；后续再做服务端已读 | 换机丢状态，可接受 |
| D9 | 头像加载 | 端 | 新增图片加载依赖或复用 `downloadStatic` | 现无 Coil/Glide |
| D10 | 绑定码分享 | 端 | 系统分享 `deeplink` 与 6 位码；深链已由服务端返回但未使用 | 需处理 App 未安装场景 |

---

## 9. 度量与验证计划

### 9.1 指标定义

| 层级 | 指标 | 定义 |
| --- | --- | --- |
| 北极星 | 互伴周互动率 | 7 日内至少 1 次"发出 + 对方确认完成"的 ACTIVE 关系数 / ACTIVE 关系总数 |
| 过程 | 绑定完成率 | 生成绑定码后 24h 内确认成功的比例 |
| 过程 | 陪伴页主 CTA 点击率 | 送惦记点击 / 陪伴页曝光 |
| 过程 | 回执打开率 | 回执条目点击 / 回执曝光 |
| 质量 | 关系月流失率 | 30 日内解绑的 ACTIVE 关系占比 |

埋点事件：`companion_view`、`companion_manage_open`、`bind_code_share`、`bind_confirm_success`、`quick_remind_click`、`receipt_open`、`poke_send`（P1）。

### 9.2 研究方案

| 方法 | 对象 | 样本 | 目的 |
| --- | --- | --- | --- |
| 深度访谈 | 子女照护者 4 人、异地恋人 4 人 | 8 人（每类 4 人，接近定性饱和下限） | 确认三类任务排序与代管边界 |
| 可用性测试 | 未绑定 / 等待 / 已互伴三态 | 5 人 | 任务成功率 ≥80%，SUS ≥70 |
| 问卷（可选） | 现有 ACTIVE 用户 | N=400（95% 置信、±5% 误差） | 量化静音与轻碰需求强度；子群 N≥50 |

访谈提纲聚焦过去行为，例如"最近一次想提醒对方却没发，是什么情况？"，避免询问"你喜不喜欢这个功能"。验证输出应能直接决定 P1-1 是否上线。

---

## 10. 实施路线

| 阶段 | 内容 | 完成标志 |
| --- | --- | --- |
| 阶段 0 契约与去重 | D1、D2、D3、入口去重、页面改名 | 冷启动能看到真实昵称与关系标签；无双 ACTIVE 可能 |
| 阶段 1 关系主页 | P0-1~P0-5，重建 `CompanionScreen`，新增互伴管理页 | 三态可用性测试通过；主 CTA 点击率可观测 |
| 阶段 2 关系感 | D5、D7、D8、P1-1~P1-3 | 绑定成功有通知；轻碰有频控；长辈接入有完整引导 |
| 阶段 3 战略评审 | P2-1 多互伴、P2-3 导航形态 | 以数据与访谈决定是否放开 1v1 |

依赖顺序：D1 必须先于阶段 1 的任何 UI 打磨；D2 与 D3 可并行；D7 需先定防骚扰规则再开发。

---

## 11. 风险与开放问题

| # | 问题 | 影响 | 建议 |
| --- | --- | --- | --- |
| R1 | 是否放开多互伴 | 决定 IA 是"关系主页"还是"关系列表" | 现阶段维持 1v1；用互伴周互动率与解绑原因验证需求 |
| R2 | 轻碰被滥用 | 骚扰、反感、卸载 | 每对关系每日上限、免打扰时段、可静音、可关闭 |
| R3 | 长辈代管权限边界 | 伦理与信任 | 代管动作双向可见；被代管方可一键退出长辈模式 |
| R4 | 回执暴露对方行为 | 隐私预期 | 仅展示与自己相关的提醒；不展示对方其他关系数据 |
| R5 | 解绑后静音残留 | 重新绑定的体验 | 静音按"接收人×发起人×类型"存储，解绑再绑会延续，需确认是否符合预期 |
| R6 | 长辈账号无手机号 | 换机无法找回 | 目前 `device_uuid` 唯一；需补设备迁移或补绑手机号引导 |

开放问题：绑定码是否允许被邀请方反向发起（当前只有发起方生成码，关系建立不对称）；关系标签的默认值由谁决定（发起方代填还是被邀请方自选）；陪伴页是否需要对"TA 今天没有回应"给出解释（涉及已读语义，建议不做）。

---

## 12. 多互伴改造方案（v1.1 追加）

### 12.1 决策参数

| 参数 | 建议值 | 说明 |
| --- | --- | --- |
| 上限 | 5 条 ACTIVE | 配置项，灰度时先 2 后 5；定位"家人圈"，不是开放好友 |
| 邀请在途 | 3 条 PENDING | 每人同时最多 3 个未确认邀请，防止刷码 |
| 绑定码有效期 | 10 分钟 | 沿用现有 `BindCodePolicy` |
| 同对唯一 | 1 条 ACTIVE | 同一对用户不可重复绑定；解绑后可重新建立（新行） |
| 命名 | 互伴 | "好友"只用于解释，不进入产品文案与路由 |
| 不做 | 搜索 / 推荐 / 通讯录导入 / 分组 | 5 人以内用列表与关系标签即可 |

### 12.2 服务端改造（按顺序）

| # | 位置 | 改动 |
| --- | --- | --- |
| S1 | [BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L172-L176) | 用 `BondLimitPolicy` 替换 `requireNoActiveBond`，上限读配置；统一错误码 `BOND_LIMITED` |
| S2 | [BondRepository.kt](server/src/main/kotlin/com/bugu/bond/infra/BondRepository.kt#L62-L76) | 新增 `countActive(userId)`、`findAllPendingByRequester`、`findActiveBetween(a,b)`；`hasActiveBond` 保留供旧语义使用 |
| S3 | [BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L46-L84) | `initiate` 改为"每次新建一条邀请"：独立码、pending 上限、生成限频；保留 `/initiate` 兼容语义为"返回最近一条未过期邀请" |
| S4 | [BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L90-L126) | `confirm` 去掉单一上限，改为 `requireCanAddBond(confirmer)` + `requireCanAddBond(requester)` + `requireNoDuplicatePair(a,b)`；余额不足返回明确原因 |
| S5 | [BondController.kt](server/src/main/kotlin/com/bugu/bond/api/BondController.kt#L20-L39) | 新增 `POST /bond/{id}/cancel`（仅发起方、仅 PENDING）、`PATCH /bond/{id}`（双方可改 `relation_tag`） |
| S6 | [ElderBondService.kt](server/src/main/kotlin/com/bugu/bond/app/ElderBondService.kt#L59-L63) | 长辈注册改为上限校验，不再要求"0 条 ACTIVE" |
| S7 | [BondDtos.kt](server/src/main/kotlin/com/bugu/bond/api/BondDtos.kt#L27-L40) | 补 `relationTag / counterpartNickname / counterpartAvatarUrl / counterpartElderMode`；`toDto` 批量联查用户，避免 N+1 |
| S8 | [BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L161-L170) | 列表按 `confirmedAt` 升序稳定返回，保证旧端仍显示最早关系；P1 再加 `lastInteractionAt` |
| S9 | push 模块 | 新增"绑定成功"事件，通知发起方；Tab 显示注意点 |
| S10 | 深链 | 复用 `InitiateResponse.deeplink`，邀请与确认落地到对应关系 |

**并发与不变量**

- 同对重复由数据库唯一索引兜底（见 12.3）；应用层只做友好提示。
- 上限是"计数约束"，检查与写入存在竞态。事务内按用户 id 升序执行 `pg_advisory_xact_lock(uid)`，再校验上限；若不引入锁，则必须接受偶发超限，并明确"上限是体验约束、非强一致"。
- `confirm` 必须同时校验**确认方**与**发起方**都未满员，否则会出现"先发码、后对方满员"的静默超限。
- 旧的确认漏洞（只校验确认方）需一并修复，否则多互伴会放大双 ACTIVE 数据。

**提醒链路不需要改**

- 创建提醒按 `receiverId` 建，按 `findActiveBond(creator, receiver)` 找唯一一对关系（[ReminderService.kt](server/src/main/kotlin/com/bugu/reminder/app/ReminderService.kt#L98-L114)、[ReminderService.kt](server/src/main/kotlin/com/bugu/reminder/app/ReminderService.kt#L551-L553)）。
- 静音维度是接收人×发起人×类型（[database-schema.sql](database-schema.sql#L147-L157)），天然按人隔离。
- 收件箱已按 ACTIVE bondId 集合过滤（[ReminderService.kt](server/src/main/kotlin/com/bugu/reminder/app/ReminderService.kt#L501-L503)）。
- 因此多互伴的兼容性风险集中在"展示混流"，不在数据层。

### 12.3 数据库变更

```sql
-- 同一对用户只允许一条 ACTIVE；解绑后新行可再建立
CREATE UNIQUE INDEX uk_bond_active_pair ON bond (
  LEAST(requester_id, receiver_id),
  GREATEST(requester_id, receiver_id)
)
WHERE status = 'ACTIVE';

-- 列表与上限统计
CREATE INDEX idx_bond_requester_status ON bond (requester_id, status, confirmed_at);
CREATE INDEX idx_bond_receiver_status  ON bond (receiver_id, status, confirmed_at);
```

- 不改表结构：`relation_tag` 已存在，`bind_code` 仍唯一。
- 建索引前先查历史重复（旧的确认漏洞可能留下双 ACTIVE）：

```sql
SELECT LEAST(requester_id, receiver_id) AS a,
       GREATEST(requester_id, receiver_id) AS b,
       count(*)
FROM bond
WHERE status = 'ACTIVE'
GROUP BY 1, 2
HAVING count(*) > 1;
```

- 回滚：删除三个索引即可；服务端开关关闭后行为回到 1v1。注意关闭开关不会清理已存在的多条 ACTIVE，回滚预案需明确"保留只读 / 人工置 UNBOUND"。

### 12.4 客户端改造

| 位置 | 改动 |
| --- | --- |
| [AppRoot.kt](app/src/main/java/com/bugu/ui/AppRoot.kt#L435-L442) | `CompanionScreen` 拆为 `CompanionListScreen` + `CompanionDetailScreen(bondId)`；`selectedBondId` 用 `rememberSaveable` |
| [AppRoot.kt](app/src/main/java/com/bugu/ui/AppRoot.kt#L364-L367) | 删除全局 `activePeerName`；看板、历史、日历改为"最近互伴"或接收 `bondId` 后取名 |
| [CompanionScreen.kt](app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt#L71) | 移除 `firstOrNull`；详情页按 `bondId` 取单条关系 |
| [FriendsScreen.kt](app/src/main/java/com/bugu/ui/screens/FriendsScreen.kt#L87-L89) | 改为"添加互伴 / 邀请在途"：不再进页自动发码；展示邀请列表、分享、取消、输入码 |
| [ProfileScreen.kt](app/src/main/java/com/bugu/ui/screens/ProfileScreen.kt#L93) | 改为"互伴 3 人 ›"，点击进列表 |
| [CreateReminderScreen.kt](app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt#L163-L166) | 已支持多 chip，保留；补最近互动排序与无互伴引导 |
| History / Calendar | 单人称呼改泛称"互伴"；P1 增加按人筛选 |
| 推送 / 深链 | 事件带 `bondId`，点击直达对应关系详情 |

目标信息架构：

```
陪伴（互伴列表）
├─ 顶部：我的互伴 3/5  ·  ＋添加
├─ 列表行：头像  昵称  [妈妈]  最近回执 · 未读点
│    └─ 点击 → 关系详情（沿用 §6.2 线框，数据源改为单个 bond）
└─ 邀请在途（折叠）：等待确认 · 复制码 · 取消
```

详情页沿用单关系线框；管理页按人展示关系标签、长辈模式、三类静音、解除关系。

### 12.5 交互与文案

- 添加入口：列表右上角"＋"，弹层两条路径"生成邀请码 / 输入对方码"；生成后显示"已邀请 1 人，等待确认"。
- 邀请码单次有效，可系统分享；同一人重复邀请不重复建行，直接复用未过期码。
- 满员：显示"家人圈已满 5 人"，添加按钮置灰，提示先解除一条关系。
- 空态五要素：图示、对话式标题、价值说明、主 CTA、次路径。
- 通知文案必须带人名（"妈妈给你设了一条惦记"），禁止再用全局"家人"称呼。

### 12.6 兼容与灰度

1. 后端先上，开关 `bond.multi.enabled=false`，行为保持不变；新增 DTO 字段旧端忽略。
2. 端上列表/详情随开关灰度；旧端仍取第一条，服务端按 `confirmedAt` 升序保证显示最早关系。
3. 灰度内上限从 2 起，观察两周再放到 5。
4. 回滚：关开关即停止新增多关系；已存在的多条 ACTIVE 需按预案处理（只读或人工解绑）。

### 12.7 测试清单

- 集成：三方连续确认、同对重复确认、满 5 后第 6 条、并发确认、取消与过期、长辈注册在满员、列表排序、跨关系提醒与静音隔离。
- 现有 [BondFlowIntegrationTest.kt](server/src/test/kotlin/com/bugu/bond/api/BondFlowIntegrationTest.kt) 中"第二条绑定 409"的断言需改为"同对 409 / 不同对成功 / 超限 409"。
- 端上：列表滚动、详情切换不串数据、满员置灰、邀请取消、深链落地。

### 12.8 指标（防止陪伴感摊薄）

- 北极星改为**单关系周互动率**：每条 ACTIVE 关系 7 日内至少 1 次"发出 + 对方确认"；同时看人均互伴数分布。
- 护栏：放开后单关系周互动率下降不超过 20%，30 日解绑率上升不超过 5 个百分点；越界则回退上限或暂停放开。
- 过程指标：邀请确认率、添加入口点击率、列表→详情打开率。

### 12.9 风险

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| 陪伴感摊薄 | 精力分散到 5 人，单关系互动下降 | 护栏指标、上限 5、列表按最近互动排序 |
| 邀请码滥用 | 码被转发给陌生人 | 10 分钟有效、单次、pending ≤3、生成限频、确认前展示对方昵称 |
| 长辈代管复杂 | 多个长辈各一套设置 | 管理页按人分组；代管动作双向可见 |
| 回执混流 | 多个发送人混在一起 | 列表页按人聚合；详情页只看该人 |
| 旧端语义 | 只显示第一条 | `confirmedAt` 升序 + 灰度 |
| 品牌口径 | 代码注释、PRD 写死 1v1 | 同步更新 [Bond.kt](server/src/main/kotlin/com/bugu/bond/domain/Bond.kt#L6)、[BondController.kt](server/src/main/kotlin/com/bugu/bond/api/BondController.kt#L14)、PRD 与 admin 文档 |

### 12.10 实施顺序

| 阶段 | 内容 | 出口条件 |
| --- | --- | --- |
| 0 | 确认上限 5、命名互伴、满员与回滚策略 | 产品确认 |
| 1 | 后端：上限策略、去重索引、邀请列表、取消、DTO 身份、推送（开关关） | 集成测试全绿，旧端无回归 |
| 2 | 端上：列表 + 详情 + 添加页（开关灰度） | 5 人可用性测试通过 |
| 3 | 上限 2 → 5，观察护栏指标两周 | 单关系互动不塌 |
| 4 | P1 轻碰、关系小结、长辈账号接入 | 按 §9 研究结论 |

## 附录 A：代码证据索引

| 主题 | 位置 |
| --- | --- |
| Tab 定义与陪伴页接线 | [AppRoot.kt](app/src/main/java/com/bugu/ui/AppRoot.kt#L113-L118)、[AppRoot.kt](app/src/main/java/com/bugu/ui/AppRoot.kt#L435-L442) |
| 陪伴页现状 | [CompanionScreen.kt](app/src/main/java/com/bugu/ui/screens/CompanionScreen.kt#L61-L346) |
| 好友页与自动发码 | [FriendsScreen.kt](app/src/main/java/com/bugu/ui/screens/FriendsScreen.kt#L149-L151) |
| 我的页互伴卡 | [ProfileScreen.kt](app/src/main/java/com/bugu/ui/screens/ProfileScreen.kt#L224-L257) |
| 服务端 BondDto | [BondDtos.kt](server/src/main/kotlin/com/bugu/bond/api/BondDtos.kt#L27-L40) |
| 1v1 约束与 toDto | [BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L172-L176)、[BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L204-L211) |
| 确认流程 | [BondService.kt](server/src/main/kotlin/com/bugu/bond/app/BondService.kt#L102-L126) |
| 长辈接口 | [ElderController.kt](server/src/main/kotlin/com/bugu/bond/api/ElderController.kt#L18-L29) |
| 静音与互叫接口 | [ReminderController.kt](server/src/main/kotlin/com/bugu/reminder/api/ReminderController.kt#L71-L101) |
| 客户端契约 | [GeitaApi.kt](app/src/main/kotlin/com/bugu/client/api/GeitaApi.kt#L44-L71)、[GeitaApi.kt](app/src/main/kotlin/com/bugu/client/api/GeitaApi.kt#L111-L120) |
| 多 chip 选择 | [CreateReminderScreen.kt](app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt#L163-L166)、[CreateReminderScreen.kt](app/src/main/java/com/bugu/ui/screens/CreateReminderScreen.kt#L1106-L1135) |
| 数据模型 | [database-schema.sql](database-schema.sql#L114-L157) |
| 官网承诺 | [CompanionSection.vue](website/src/components/CompanionSection.vue#L2-L7) |
| 品牌口径 | [布谷-品牌信息体系.md](brand/布谷-品牌信息体系.md#L60) |

## 附录 B：外部来源

[cite:1] [Between - Best App for Couples](https://between.us/intro)
[cite:2] [18 Apps for Couples To Grow Closer Every Day | Paired](https://www.paired.com/articles/best-apps-for-couples)
[cite:3] [Love Nudge App - App Store](https://apps.apple.com/cn/app/love-nudge/id495326842?platform=tv)
[cite:4] [Love Nudge™ Mobile App | 5 Love Languages](https://5lovelanguages.com/resources/app)
[cite:5] [工业和信息化部办公厅关于进一步抓好互联网应用适老化及无障碍改造专项行动实施工作的通知](https://www.miit.gov.cn/jgsj/xgj/wjfb/art/2021/art_81e8b738d6b24ad6a04f7ecb3f4e0702.html)
[cite:6] [移动互联网应用（APP）适老化通用设计规范](http://wza.isc.org.cn/bztx/bzjd/mobile/index.html)
[cite:7] [Navigation bar – Material Design 3](https://m3.material.io/components/navigation-bar/guidelines)
[cite:8] [Google Family Link - 家庭网上安全及家长监控工具](https://families.google/intl/zh-HK_ALL/familylink/)
[cite:9] [Why App Empty States Are Your Most-Seen Screens (Best Practices)](https://www.appypie.com/blog/app-empty-states)
