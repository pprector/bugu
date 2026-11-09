# B5 段四 对照审计与第一批 P0 验收清单

## 审计交付
- [x] `5a65d39` 的 15 个旧 Compose 文件已导出到只读基线 `/tmp/bugu-compose-baseline/`，未 `git checkout`、未改动工作区删除状态
- [x] 差异清单逐页覆盖（十二页 + 响铃页），每条带旧版文件与行号
- [x] P2 单独成表并逐条说明理由；未完成项未混入 P2
- [x] 清单已获用户确认，第一批 P0 放行

## 第一批 P0（5 页）
- [x] 看板：收到提醒三出口、我发出的撤销、进行中响铃卡；`/reminder/ack`、`DELETE /reminder/{id}`、`/reminder/current` 设备验证通过
- [x] 新建：任意未来日期（月历，过去置灰）+ 任意分钟（0..59 步进）；设备验证可设 11-10 05:30 并创建成功
- [x] 新建：草稿自动保存、挂载提示「继续编辑/丢弃草稿」、恢复后字段还原、创建成功清除
- [x] 新建：`permissionStatus` 驱动「开启提醒权限并创建」；`alarmResync` 失败停在本页并给 Rose Banner
- [x] 登录：协议勾选行（位于登录按钮下方，文案「已阅读并同意」）+ 用户协议/隐私政策全文（逐字复用旧版）
- [x] 登录：未勾选触发「需要你的同意」内联确认，同意后自动勾选并继续原动作，验证码发送成功
- [x] 陪伴详情：「为 X 设一条提醒」卡片文案与旧版一致，点击进入新建页并预选对方、默认标题联动
- [x] 我的：头像更换（相册选图 → avatar/token → COS 直传 → confirm → 即时预览），上传成功并提示「头像已更新」

## 截图验收（同一数据状态、旧/新并排，位于 `shots-b5-audit/`）
- [x] `sbs_dashboard.png`
- [x] `sbs_create.png`
- [x] `sbs_login.png`
- [x] `sbs_companion_detail.png`
- [x] `sbs_profile.png`

## 构建与约束
- [x] `:bugu-ui:assembleRelease` 通过
- [x] `app` `assembleDebug --refresh-dependencies` 通过
- [x] 新版运行 0 条 `FATAL EXCEPTION`
- [x] 本轮未新增对禁区文件的修改（`res/**`、`AlarmNotifications.kt`、`LocalNotificationTest.kt`、`website/`、`admin/`、`brand/`、`version.properties`）
- [x] 未 commit、未 push
- [x] `haohao36`（emulator-5554）未被占用；对照使用 `kuikly36`（5556）与 `bugu-compare36`（5558）

## 第二批 P1（已通过设备目视验收）
- [x] 看板：分段 Tab、通知权限横幅、时钟卡（56sp Grotesk + 闹钟图标 + 7 天统计）、周历、行信息、空态动作、刷新与错误重试
- [x] 新建：去绑定握手态/失败重试、接收人横滑与头像、方式说明、占位与字段红字、右下「咕咕帮填」
- [x] 全局：GuguBird 完整插画、Plus Jakarta/Space Grotesk（运行时 `loaded=true` 双字体）、状态语义色、64dp TopBar
- [x] 系统栏避让：宿主按导航栏 inset 底部兜底，「咕咕帮填」「+ 新建提醒」不再被导航栏覆盖

## 第二批 P1 第二批（4 页，2026-11-06 设备验收）
- [x] 登录 L2–L11：设备验证倒计时(`v_login_countdown.png`)、字段红字(`v_login_error3.png`)、品牌区/按钮/邀请码移除(`v_login_top2.png`)、格式化+自动聚焦(`v_login_phone_slow2.png`)、6 位自动提交+协议门(`v_login_autosubmit.png`)
  - 代码级未上机：L9 全屏庆祝、L10 发码成功文案；L8 的 OTP 自动填充未实现（框架无 smsOtp semantics）
- [x] 权限引导 G1–G5：`v_perm_full.png`（返回/大插画/文案/待开启）、`v_perm_bottom3.png`（双按钮）
  - G3 仅代码级：`pageDidAppear()` 重读权限状态
- [x] 历史 H1–H4：`v_history_final.png`（`我 → 用户0410`、`用户8000 → 我`、类型 chip + 时刻 + 重复规则、TopBar）
  - H3 空态插画仅代码级（测试账号非空）
- [x] 日历 K1–K4：`v_calendar_final.png`（方向姓名 + 重复、左右 44dp 月份按钮、TopBar 无副标题）
  - K2 空态插画仅代码级（模拟器点击日期格未命中）

## 第三批 P1 第三批（4 页，2026-11-06 设备验收）
- [x] 亲友 F1–F11：系统分享(`v_friends_share4.png`)、刷新中…(`v_friends_refresh.png`)、有效期`11月13日 14:08`、复制仅 6 位（logcat `copyText ok len=6`）、失败/重试卡与空态动作、私人备注+合规说明、条目矢量 chevron（`v_friends_final.png`、`v_friends_bottom.png`）、深链错误文案
  - 代码级未上机：F5 错误卡、F6 空态、F7 确认区、F11 深链错误（测试账号接口正常，未构造故障）
- [x] 陪伴列表 P1–P4：页头`添加亲友`入口（`v_companion_final.png`，点击直达亲友页 `v_companion_add_entry.png`）、`邀请中的关联 · 查看邀请码`、失败文案`亲友列表加载失败/请重试/重试`、平色头像 + 矢量 chevron
  - 口径修正：审计稿「渐变头像 + 发提醒按钮」与 `5a65d39` 源码不符（旧版列表为平色头像、仅 chevron），已按源码对齐
- [x] 资料详情 MD1–MD5：通知渠道逐项（`通知渠道 · 布谷闹钟`）、全屏提醒卡（`已允许`）、`5 秒后发送本地测试通知`（`v_profile_test_card.png`，logcat `testNotify ok`）、`pageDidAppear` 自动刷新、FAQ 补通知渠道（`v_profile_details_final.png`）
- [x] 响铃 R1–R4：顶部标题按类型（`单次提醒/循环提醒/互叫闹钟`）、来源/留言/回执带亲友昵称（`v_ring_final2.png`）、`长辈极简模式`标记（`v_ring_elder.png`）
  - 昵称取自 `ReminderSync` 缓存的 `bond_names_json`；离线无缓存时回退中性「亲友」
  - **R5 底部文案未改**：仍为「会一直响到你确认」，待产品确认是否回到「若 40 秒未确认…」口径
- [x] `haohao36`(5554) release 重装：`versionCode=4` / `versionName=1.1.2`，`install -r` 成功保留数据，冷启动落登录页（本机无登录态，属正常）`release_haohao36_cold2.png`

## 待后续（剩余 P1，未在本轮声称完成）
- 我的 M1–M7（头像/统计口径等，需单列下一批）
- 陪伴详情 PD1–PD8（除 P0 已完成的「为 X 设一条提醒」入口）
- 响铃 R2/R3 依赖的昵称缓存需真实双账号到点链路复验（当前用模拟 RingActivity 验证显示）

## 段五：5554 release 真实短信登录验收（2026-11-09，真实链路）

- [x] 真实短信登录：手机号 `156 1788 9809` + 验证码 `015565` → 登录成功 → 权限引导 → 看板；`bugu.xml` 含 `jwt`（长度 208，不贴明文）
- [x] 包基线：versionCode 4 / versionName 1.1.2 / SHA-256 `671a2b4c…ea6473cf`；设备时间 2026-11-09 12:31 CST
- [x] 登录页：`release_re_cold_start.png`、`release_re_login_clean.png`
- [x] 权限引导：三权限卡 + 双按钮（`l2_permission.png`、`l3_permission_bottom.png`）
- [x] 看板：三出口可见；撤销实测成功；四出口/时钟卡/分段/空态/周历齐全（`rel_01_dashboard.png`、`rel_02_dashboard_mine.png`、`rel_04_undo.png`）
- [x] 新建：入口/返回/关键元素齐全（`rel_07_create.png`、`rel_09_create_foot.png`）
- [x] 历史：方向/类型/重复/时刻齐全（`rel_12_history.png`）
- [x] 日历：月份切换、日期选择、空态插画（`rel_13_calendar.png`、`rel_17_calendar_day3.png`、`rel_18_calendar_day10_empty.png`）
- [x] 亲友：邀请码/复制/分享/刷新、ACTIVE 亲友 0 空态（`rel_20_friends.png`）
- [x] 我的 M1–M7（`rel_22_profile.png`、`rel_23_profile_bottom.png`、`rel_28_care_on2.png`、`rel_30_nickname_edit2.png`）
- [x] 资料详情：permissions / feedback / about（`rel_24_care_on.png`、`rel_25_feedback.png`、`rel_26_about.png`）
- [x] 陪伴列表 / 陪伴详情 PD4：**不可达（无 ACTIVE 亲友），标代码级**
- [x] 响铃：13:15 `AlarmManager` 真实全屏拉起 + 循环 92s + 右滑 ack 即停；`Wake Locks: size=0`、音频焦点栈空（`ring_t0.png`、`ring_t65.png`、`ring_ack.png`）
- [x] 响铃 snooze 重调度：`RTC_WAKEUP com.bugu.ACTION_FIRE @13:31:48` → 13:31:54 二次真实拉起；再右滑完成 → 历史回执翻为「已完成」（`ring2_t0.png`、`ring2_history_done.png`）
- [x] 收尾计数：`FATAL EXCEPTION=0` / `E AndroidRuntime=0` / `ANR=0`；无白屏/闪退/卡死
- [x] 协议门改模态弹窗（`Dialog` + 黑 0.6 遮罩 + 隔离触摸），5556 验收：遮罩不误触、暂不不发码、同意才发码、返回键仅关弹窗（`dlg_17`～`dlg_20`、`dlg_16`）
- [x] 宿主返回键修正：`BuguKuiklyHost.onBack()` 先 `delegator.onBackPressed()`，避免弹窗关闭时整页退栈重载
- [x] 5554 release 统一重装（`install -r` 保留数据/登录态；versionCode 4 / versionName 1.1.2；`boot me() ok user=用户9809`）
- [x] 登录输入框诊断：三态截图已取（`diag_fast_phone2.png`、`diag_error_empty.png`、`diag_boundary_11_6.png`），产品定方案 A 后已修

## 段六：四条修复验收（2026-11-09，5556 → 5554）

- [x] 登录框方案 A：`TextFieldValue` + 光标数字位映射；5556/5554 逐键 `15617889809`、粘贴 `13800138000` 均显示 `3-4-4` 且满 11 位自动跳焦（`s6_phone_slow_156.png`、`s6_5554_final_paste_138.png` 等）
- [x] 过期 fire 409：`ack` 透出 `code`，`HTTP_409` 写 `bugu_main.expired_fire_ids`（按 fireId）；5554 真实 11-04 `B2 cancel`：点击后卡消失 + 提示，刷新/冷启动均不复活（`s6_5554_final_expired.png`、`s6_5554_final_refresh.png`、`s6_5554_final_restart.png`，日志 `httpCode=409`、`dismissedFires.get ids=6`）
- [x] 亲友数口径：看板改统计 `ACTIVE`；5556 0/0 与加 ACTIVE 后 1/1 双向验证，5554 0/0（`s6_dash_before2.png`、`s6_friends_0.png`、`s6_dash_1active.png`、`s6_friends_1.png`、`s6_5554_final_friends.png`）
- [x] 我的页系统返回键：5556/5554 日志 `attach page=BuguProfilePage` → 返回 `attach page=BuguRealDashboardPage`（`s6_5554_final_profile_back.png`）
- [x] 陪伴列表/详情判断：纯数据（0 ACTIVE / 1 PENDING_OUTBOUND）；补 ACTIVE 数据后详情页正常，代码无缺陷（`s6_companion_detail.png`）
- [x] 两设备 `FATAL EXCEPTION=0` / `E AndroidRuntime=0` / `ANR=0`；5554 保持运行
- [x] 未 commit / 未 push；禁区文件未动
