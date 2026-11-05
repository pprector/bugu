# 布谷 Android 真机验收交接清单

- 适用 APK：`1.1.0`（versionCode 2），release 签名 `CN=Bugu`
- 对应提交：`f557487`（推送点击/ack 新契约）、`0e59001`（响铃页时钟修正）
- 模拟器已完成项见同轮验收报告；本文件只列**必须真机验证**的项目。
- 前置条件：
  1. 服务端 fireAt 推送那批改动**尚未部署**。`FIRE_AT` 推送、`nextFireId/nextFireAt` 的端到端联调要等服务端部署后再做。
  2. 使用真实账号，保持通知权限开启；另准备一个可接收短信的手机号用于登录。
  3. 每项失败先抓：`adb logcat -c` 清空，复现后保存 `adb logcat -d > bugu-$(date +%H%M).log`。

## 一、推荐机型矩阵

| 优先级 | 机型/系统 | 重点 |
|---|---|---|
| P0 | 华为/荣耀 HarmonyOS 4.x 或 EMUI | 省电、自启动、锁屏全屏、极光到达 |
| P0 | 小米/Redmi HyperOS 2 / MIUI 14 | 后台限制、通知渠道、锁屏 |
| P0 | OPPO/OnePlus ColorOS 15 / OxygenOS | 自启动、关联启动、省电 |
| P1 | vivo/iQOO OriginOS 5 | 后台高耗电、通知折叠 |
| P1 | Samsung One UI 7 | 通知权限、精确闹钟、Doze |
| P2 | Android 原生/模拟器（Pixel） | 基线对照 |

## 二、真机验证清单

### 1. 极光推送到达率

**步骤**
1. 安装 APK，登录并确认首页出现「惦记同步就绪」。
2. 在服务端/后台给当前账号的 registrationID 发一条 T0 或 T1 推送。
3. 分别测试：前台、后台、最近任务划掉、锁屏、省电模式、待机 30 分钟后。
4. 记录通知是否到达、延迟秒数、点击后落到哪个页面。

**期望**
- 通知到达；T0/T1/T2 点击回退主界面，FIRE_AT（服务端部署后）点击进入 `RingActivity`。
- 进程被杀后仍可收到极光通知（厂商通道生效时）。

**失败看什么**
- `adb logcat -s BuguPush BuguPushClick`：registrationID 是否上报、点击回调是否触发。
- 极光控制台该设备在线状态/厂商通道状态。
- `adb shell dumpsys notification --noredact | grep com.bugu`：通知是否被系统创建。

### 2. 国产 ROM 省电/自启动/后台

**步骤**
1. 系统设置中把布谷设为「允许自启动/允许后台活动/不限制耗电」。
2. 创建 5 分钟后的提醒，按 Home 回桌面，锁屏，等待到点。
3. 再测试「最近任务划掉」和「强制停止」两种状态（强制停止会取消 AlarmManager，属系统预期，不应作为缺陷）。

**期望**
- 到点闹钟仍触发；锁屏能亮屏并出现响铃页或高优先级通知。
- 划掉最近任务后仍能到点响应；强制停止后不要求响应（系统限制）。

**失败看什么**
- `adb shell dumpsys alarm | grep -A4 com.bugu.ACTION_FIRE`：闹钟是否还在。
- `adb logcat -s BuguAlarm BuguAlarmNotification BuguRing`：广播是否收到、通知是否 post。
- 厂商「自启动/后台弹窗/锁屏显示」权限是否开启。

### 3. 锁屏全屏与解锁通知

**步骤**
1. 锁屏状态下等到点：期望亮屏并出现响铃页（`setFullScreenIntent` 路径）。
2. 解锁、停留在桌面或其它 App，等到点：观察是直接全屏还是只出高优先级通知。
3. 若只出通知，点击通知：必须进入 `RingActivity`，不能停在主界面。
4. 检查通知标题/正文是否含提醒人、提醒内容，点击后按钮/滑动交互正常。

**期望**
- 锁屏可亮屏响铃；解锁状态至少有一条高优先级通知；点击进入响铃页。
- 通知文案含提醒人/内容，不泄露备注、手机号。

**失败看什么**
- `adb logcat -s BuguAlarmNotification BuguRing BuguPushClick`
- `adb shell dumpsys activity activities | head`：确认顶层是 `.ui.RingActivity`。
- `adb shell dumpsys notification --noredact | grep -A8 com.bugu`：通知渠道重要性、fullScreenIntent、contentIntent。
- 系统「锁屏通知/全屏通知」权限是否允许。

### 4. 两条提醒同时到期/进程被杀

**步骤**
1. 同一分钟创建两条提醒（或同一分钟两条不同 fire），等待同时到期。
2. 第一条响铃时不要确认，第二条应排队；确认第一条后应自动切到第二条。
3. 第一条响铃时杀掉布谷进程（`adb shell am kill com.bugu`），再观察第二条通知/闹钟。
4. 检查第二条是否还能通过通知点击进入响铃页。

**期望**
- 只出现一个 `RingActivity`；第二条排队并在第一条确认/稍后后自动切换。
- 进程被杀后，第二条至少有独立通知可点；若通知权限关闭，打开 App 后首页仍显示待处理，可手动处理。

**已知取舍**
- 当前排队队列是进程内 `ArrayDeque`，未做本地持久化。原因是：
  - 每条 fire 到点都会各自 post 通知，通知自带 `PendingIntent`，进程被杀后第二条仍可点通知恢复；
  - 若做持久化队列，需要在冷启动/开机时恢复并再次拉起 Activity，易与已 ACK/已取消状态冲突，且受后台启动限制；
  - 通知权限关闭时第二条不会自动再响，但服务端仍有 T1/T2 升级链，App 打开后首页可见待处理。
- 本条结论：**暂不实现持久化队列**，作为后续产品决策。

**失败看什么**
- `adb logcat -s BuguRing`：应看到 `fire queued`、`advance to fire=...`。
- `adb shell dumpsys notification --noredact | grep com.bugu`：第二条通知是否在。
- `adb shell dumpsys alarm | grep -A4 com.bugu.ACTION_FIRE`：两条闹钟是否分别注册。

### 5. 稍后二次响

**步骤**
1. 到点响铃后点「小憩 15 分钟」。
2. 记录当前时间，等待 15 分钟。
3. 观察是否第二次响铃，文案/标题是否仍是原提醒。

**期望**
- 约 15 分钟后再次响铃/通知；确认后不再响。

**失败看什么**
- `adb shell dumpsys alarm | grep -A4 com.bugu.ACTION_FIRE`：是否有 +15 分钟的新闹钟。
- `adb logcat -s BuguRing BuguAlarm`。
- 首页「稍后再叫」状态是否在 15 分钟后恢复为可操作。

### 6. 循环提醒次日注册

**步骤**
1. 创建「每天」或「工作日」循环提醒，设为今天稍后。
2. 到点确认完成后，等待同步。
3. 检查下一条闹钟是否注册到次日同一时刻。

**期望**
- 次日同一时刻有 `RTC_WAKEUP` 闹钟；首页显示「明天」待触发；系列不因本次确认而终止。

**失败看什么**
- `adb shell dumpsys alarm | grep -A4 com.bugu.ACTION_FIRE`：`origWhen` 是否为次日。
- `adb logcat -s BuguRing` 与首页状态。
- 服务端 `/incoming` 返回的 `fireAt` 是否推进；旧服务端无 `nextFireAt` 时端侧仍以列表为准。

### 7. 通知权限拒绝引导

**步骤**
1. 系统设置关闭布谷通知权限。
2. 回到 App 首页，应出现「通知权限未开启」Banner。
3. 点击 Banner，应跳到系统「应用通知设置」；开启后返回首页，Banner 消失。

**期望**
- Banner 出现且可跳转；开启后消失。
- 权限关闭时到点仍尝试全屏/本地闹钟，但不能依赖通知。

**失败看什么**
- `adb logcat -s BuguAlarmNotification`：是否打印 `POST_NOTIFICATIONS unavailable`。
- `adb shell dumpsys notification --noredact | grep com.bugu`：权限状态。
- 截图/`uiautomator dump` 确认 Banner 与设置页。

### 8. 真实闹钟听感/震动/音频焦点

**步骤**
1. 正常媒体音量、静音、振动模式各测一次。
2. 耳机/蓝牙连接时到点；播放音乐时到点。
3. 响铃持续 ≥90 秒，观察铃声循环、震动是否持续、是否被系统打断。

**期望**
- 铃声使用闹钟通道且循环；震动持续；来电/媒体不抢占闹钟；确认/稍后后立即停止。

**失败看什么**
- `adb logcat -s BuguRing`：`bell start failed`、`audio focus lost`。
- `adb shell dumpsys audio | grep -A5 bugu`。
- `adb shell dumpsys power | grep "Wake Locks"`：响铃期间应有 WakeLock，确认后 size=0。

### 9. 锁屏/勿扰/免打扰

**步骤**
1. 开启勿扰模式，分别允许/禁止闹钟突破勿扰。
2. 锁屏 + 息屏等待到点。
3. 检查是否亮屏、全屏、声音/震动是否符合系统策略。

**期望**
- 系统允许时闹钟可突破勿扰；不允许时至少保留通知/闹钟记录；不应崩溃。

**失败看什么**
- `adb logcat -s BuguAlarmNotification BuguRing`。
- `adb shell dumpsys notification` 中 channel 的 `mBypassDnd`。
- 系统勿扰「闹钟/提醒」例外设置。

### 10. 服务端新契约联调（部署后）

**前置**：服务端 fireAt 推送批次已部署，数据库迁移 V17 成功。

**步骤**
1. 服务端创建一条 1 分钟后提醒，接收端 App 在后台/锁屏。
2. 到点后检查是否收到 `FIRE_AT` 推送；标题/正文是否含提醒人和提醒内容。
3. 点击推送：应进入 `RingActivity`，而不是主界面；同一 fire 不应与本地闹钟叠加两次。
4. SNOOZE 后检查 ack 响应中的 `nextFireId/nextFireAt` 是否被端侧用于排期。
5. REPEAT 确认后检查 `nextFireAt` 是否为次日。

**期望**
- 推送点击直达响铃页；标题/正文可读；同一 fire 去重；snooze/repeat 不靠「确认时间 +15 分钟」猜测。
- 旧服务端（无新字段）时端侧回退 `+15` 分钟仍可用。

**失败看什么**
- `adb logcat -s BuguPushClick BuguRing BuguPush`
- `adb shell dumpsys activity activities | head`：顶层是否 `RingActivity`。
- 服务端日志：`fire-at push`、`escalation_log` 的 `FIRE_AT` 行。
- 抓包/服务端接口响应确认 `nextFireId/nextFireAt` 是否存在。

## 三、通用日志命令

```bash
# 布谷相关日志
adb logcat -s BuguAlarm BuguAlarmNotification BuguRing BuguPush BuguPushClick

# 当前顶层页面
adb shell dumpsys activity activities | head -20

# 已注册闹钟
adb shell dumpsys alarm | grep -A4 'com.bugu.ACTION_FIRE'

# 通知与渠道
adb shell dumpsys notification --noredact | grep -A10 'com.bugu'

# WakeLock
adb shell dumpsys power | grep 'Wake Locks'

# 保存完整日志
adb logcat -d > bugu-real-device-$(date +%Y%m%d-%H%M).log
```

## 四、验收状态与边界

**模拟器已验证（本轮，Android 15 / Pixel 模拟器，release 包 1.1.0 / versionCode 2）**

| 项 | 结果 |
|---|---|
| 新建→到点→全屏→右滑 ACKED | 通过（锁屏息屏也能亮屏进入 `RingActivity`） |
| 取消后不响 | 通过（到点无广播、无通知，闹钟已移除） |
| 稍后二次响 | 通过（+15 分钟再次响铃；旧服务端走回退路径） |
| 循环次日注册 | 通过（确认后次日 10:54 有新闹钟，`/incoming` 已推进） |
| 两条同时到期排队 | 通过（`fire queued` → `advance to fire=...`，单 Activity） |
| 通知权限拒绝 | 通过（Banner + 跳系统设置；到点日志 `POST_NOTIFICATIONS unavailable`，前台仍进响铃页） |
| 进程被杀 + 锁屏 | 通过（`am kill` 后新进程处理到点 fire，亮屏全屏） |
| 通知点击进响铃页 | 通过（后台解锁到点只出高优先级通知，点击顶层为 `.ui.RingActivity`） |
| 登出清理 | 通过（root 快照→登出：jwt/调度/闹钟全清→恢复快照，登录态与次日闹钟恢复） |

**仍未验证 / 不能验证**

- 服务端 fireAt 推送批次**未提交、未部署**；`FIRE_AT` 推送、`nextFireId/nextFireAt` 端到端联调必须等服务端上线后执行（见第 10 节）。
- 真实短信验证码重登：登出清理用快照恢复验证，未走「登出→收码→重登」链路。
- 排队队列未做本地持久化，取舍见第 4 节；进程被杀期间仅在途 AlarmManager 闹钟与已 post 通知可恢复，第三条及以后的内存排队会丢。
- 国产 ROM/真实极光送达/真实闹钟听感必须在真机验证，模拟器只能证明逻辑路径。

## 五、必须用户真机验证清单（逐项操作）

> 每项失败：先 `adb logcat -c`，复现后 `adb logcat -d > bugu-$(date +%H%M).log`，再按「失败看什么」定位。

| # | 机型/条件 | 操作步骤 | 期望结果 | 失败看什么 |
|---|---|---|---|---|
| D1 | 华为/荣耀 HarmonyOS 4.x；小米 HyperOS 2；OPPO ColorOS 15（各一台） | 设置允许自启动/后台活动/不限制耗电 → 建 5 分钟后提醒 → Home 回桌面 → 锁屏等待 | 到点亮屏 + 响铃页；右滑确认后服务端状态 ACKED | `dumpsys alarm \| grep -A4 com.bugu.ACTION_FIRE`；`logcat -s BuguAlarm BuguAlarmNotification`；厂商自启动/锁屏显示开关 |
| D2 | 同上，划掉最近任务 | 建 2 分钟后提醒 → 上滑清除最近任务 → 等待 | 仍能到点响铃或至少 post 通知 | `dumpsys alarm` 是否还有闹钟；`logcat -s BuguAlarm` |
| D3 | 任意国产 ROM，解锁停留桌面 | 建 2 分钟后提醒 → 解锁停在桌面等待 | 只出高优先级通知（不强制全屏）；点击通知进 `RingActivity` | `dumpsys notification --noredact \| grep -A8 com.bugu`；`dumpsys activity activities \| head` |
| D4 | 华为/小米/OPPO/vivo 各一台 | 服务端部署后：用另一账号给本机建 1 分钟后提醒，本机后台/锁屏 | 收到极光 `FIRE_AT` 推送；标题含提醒人+内容；点击直达响铃页且不与本地闹钟叠加 | `logcat -s BuguPush BuguPushClick BuguRing`；极光控制台在线/厂商通道；服务端 `escalation_log` FIRE_AT 行 |
| D5 | 任意真机，正常媒体音量/静音/振动模式 | 各建一条 1 分钟后提醒，响铃 ≥90 秒 | 铃声循环、震动持续、属闹钟通道；确认/稍后立即停止 | `logcat -s BuguRing`（`bell start failed`/`audio focus lost`）；`dumpsys audio \| grep -A5 bugu`；`dumpsys power \| grep "Wake Locks"` |
| D6 | 任意真机，勿扰模式 | 开启勿扰（分别允许/禁止闹钟例外）→ 锁屏等待 | 系统允许时突破勿扰；不允许时不崩溃、至少留通知 | `logcat -s BuguAlarmNotification BuguRing`；`dumpsys notification` 中 `mBypassDnd` |
| D7 | 任意真机，耳机/蓝牙/播放音乐 | 到点时连接耳机或正在播放音乐 | 闹钟不被媒体抢占，暂停/压低媒体后响铃 | `dumpsys audio`；`logcat -s BuguRing` |
| D8 | 任意真机，省电模式 + 待机 30 分钟 | 开启省电模式 → 建 10 分钟后提醒 → 息屏待机 | 到点仍触发（厂商允许时） | `dumpsys deviceidle`；`dumpsys alarm`；厂商省电白名单 |
| D9 | 任意真机，通知权限关闭 | 关闭通知权限 → 建 2 分钟后提醒 → 后台等待 | 首页有 Banner；到点仍能进响铃页（视系统 BAL 策略） | `logcat -s BuguAlarmNotification`；截图/Dashboard Banner |
| D10 | 两台真机，互相惦记 | A 给 B 建 1 分钟后提醒，B 锁屏 | B 到点响铃；A 的「TA 的惦记」状态更新为 ACKED | 两端首页状态；服务端 `/incoming`；`logcat -s BuguAlarm BuguRing` |
