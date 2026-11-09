# B2：CreateReminderScreen（1635 行）Kuikly 骨架拆分方案

> 原则：骨架先行（本批交付，可创建→落库→历史可见），交互细节第二批补齐；每一步都标注「已验证 / 未验证」。

## 一、原页面功能盘点（1635 行 → 7 个能力块）

| 块 | 原实现 | 骨架处置 | 细节批次 |
|---|---|---|---|
| ① 接收人多选 | WhoRow 横滑头像+最多20人+失效检测 | **保留全部逻辑**；头像位用首字圆形（无图片加载） | 真实头像（bridge 图片） |
| ② 标题 | BasicTextField+20字限制+按接收人联动默认标题+dirty 追踪 | **全保留** | — |
| ③ 时间选择 | M3 TimePicker 表盘 + 日期 chips + DatePicker | **简化为内联选择**：今天/明天/后天 + 小时网格(6-23) + 分钟档(00/15/30/45)；isPast 校验+「改到明天再创建」保留 | M3 级表盘(自绘) |
| ④ 方式 | BottomSheet：单次/循环/互叫 + 循环规则(每天/工作日/自定义星期) + 语气 + 语音开关 | **逻辑全保留，弹层改内联展开区**；互叫约束、voiceEnabled 联动不变 | BottomSheet 化 |
| ⑤ AI 帮填 | /reminder/parse + 系统语音识别 + FlashRow 闪光逐字段回填 | **保留 parse 文本链路**（逐字段回填），语音识别与闪光动画不做 | 语音 bridge + 动画 |
| ⑥ 草稿 | SharedPreferences 保存/恢复 + 恢复对话框 | **骨架不做** | bridge `bugu.draft.*` |
| ⑦ 创建+闹钟重排 | /reminder/batch + ReminderSync.sync + isPast/长度校验 | **全保留**；resync 走 `bugu.alarm.resync`（probe 壳为桩） | 真宿主接 ReminderSync |

## 二、Kuikly compose 能力边界（B0/B1 实测）

- 有：BasicTextField、clickable、LazyColumn、mutableStateOf 重组、didInit 桥接、pageData e2e 钩子
- 无：LaunchedEffect/协程 effects、Material3 TimePicker/DatePicker/BottomSheet、AnimatedVisibility、Icons、rememberSaveable
- 推论：弹层一律改「内联展开区」；时钟用挂载时刻快照（isPast 在提交时再校验，双保险）；状态不跨进程恢复（草稿留细节批次）

## 三、数据流（复用 B1 验证过的链路）

- `netGet /bond` → ACTIVE 绑定；`netPost /reminder/batch`（BatchCreateReminderRequest 同形 JSON）；`netPost /reminder/parse`
- `alarmResync`（新增 bridge 方法）：probe 壳=桩(log+ok)，真宿主=ReminderSync.sync；失败 `ALARM_PERMISSION_DENIED` 时页面展示精确闹钟引导（契约 4.7）
- 路由：白名单 +`BuguCreatePage`/`BuguHistoryPage`；看板「+ 新建提醒」接 `routerOpen`；宿主支持 `--es page` 直开指定页便于逐页验证

## 四、验证清单（本批）

1. 创建：看板→新建→骨架表单→创建 200 →服务端落库（curl 可查）→历史页可见
2. parse：输入「明早七点半提醒自己吃降压药」→ 标题/时间/便签逐字段回填
3. 校验：过去时间→「改到明天再创建」；空标题→按钮禁用+提示
4. 历史：双列表合并按日分组、状态彩标、方向、空态
5. 每步截图 + logcat；0 FATAL

**本批不验证（明确标注）**：语音识别、闪光/入场动画、草稿保存恢复、真实头像、M3 表盘交互、跨进程表单恢复、真实宿主 ReminderSync（probe 为桩）。
