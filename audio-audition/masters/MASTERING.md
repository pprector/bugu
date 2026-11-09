# 响铃母带说明（组合 A）

制作日期：2026-11-05。产物目录 `audio-audition/masters/`，**不参与 App 打包**。

## 结论

两条母带达到可接入标准：单声道 44.1 kHz，OGG 目标 96 kbps，公共响度目标 **-19.7 LUFS**，实测两条均为 **-19.7 LUFS**，真峰值 **-6.6 / -1.8 dBTP**（均 ≤ -1.5 dBTP），循环接缝连续、无爆音。

> **代码接入待 App 文案会话收尾（版本 4 / 1.1.2）后再排。** 本轮只交付母带与对照页，未改 `app/src`、`app/version.properties`、`brand/assets`。

## 选材与许可

| 用途 | 成品 | 源文件（本仓库） | 作者 / 来源 | 许可 |
|---|---|---|---|---|
| 通知动机（一次性） | `alarm_notice.ogg` / `.wav` | `audio/fs_vocal_cuckoo_2x.mp3` | se2001，<https://freesound.org/people/se2001/sounds/478670/> | CC0 1.0 |
| 循环响铃 | `alarm_loop.ogg` / `.wav` | `audio/fs_cuckoo_clock_12.mp3` | DiArchangeli，<https://freesound.org/people/DiArchangeli/sounds/462930/> | CC0 1.0 |

两者均为 CC0，可商用、免署名、允许修改。**说明**：母带基于 Freesound 官方 HQ 预览 MP3 制作，不是原始 AIF/WAV；CC0 允许这样使用，但若后续能登录下载原始文件，可用同一处理链重出母带以取得更好保真度。

## 处理步骤

1. **解码**：源 MP3 → 单声道 44.1 kHz、16-bit PCM，供后续处理。
2. **裁首尾静音**（阈值 -45 dBFS，首尾各留 20 ms 保护余量）：
   - 通知动机：源 1.509 s → 母带 **1.248 s**（首尾共裁掉约 0.26 s 低电平）。
   - 循环响铃：源 14.118 s → 母带 **10.16 s**。末声报时约在 9.9 s，尾部约 3.5 s 底噪/静音裁掉，只保留衰减尾部，避免循环时出现长空拍。
3. **循环接缝**：在**自然安静的底噪段**（源 10.40–10.60 s 与开头 0.24–0.44 s 之间）做 **200 ms 等功率交叉淡化**（四分之一正弦，`cos/sin` 功率互补），而不是压在首声报时的攻击段上，避免淡化把第一声吃掉、也避免线性淡化造成音量凹陷。
4. **电平校准**：以循环段的真峰值为约束（源接近 0 dBTP，不能直接推高），公共目标定为 **-19.7 LUFS**：
   - 通知动机 **+12.6 dB**（源 -32.4 → -19.8 LUFS）
   - 循环响铃 **-1.6 dB**（源 -18.2 → -19.7 LUFS）
5. **导出**：
   - OGG Vorbis，单声道 44.1 kHz，目标 96 kbps；
   - 同时保留 16-bit PCM WAV 无损源，供 iOS 复用/转 CAF、AIFF。

## 成品数据

| 文件 | 时长 | 采样率/声道 | 编码 | 声明码率 | 字节数 | 实测 LUFS | 真峰值 |
|---|---|---|---|---|---|---|---|
| `masters/alarm_notice.ogg` | 1.2478 s | 44.1 kHz / mono | OGG Vorbis | 96 kbps | 15,950 B（15.58 KiB） | -19.7 | -6.6 dBTP |
| `masters/alarm_loop.ogg` | 10.1600 s | 44.1 kHz / mono | OGG Vorbis | 96 kbps | 129,087 B（126.06 KiB） | -19.7 | -1.8 dBTP |
| `masters/alarm_notice.wav` | 1.2478 s | 44.1 kHz / mono | PCM 16-bit | — | 110,100 B | -19.8 | -6.6 dBTP |
| `masters/alarm_loop.wav` | 10.1600 s | 44.1 kHz / mono | PCM 16-bit | — | 896,156 B | -19.7 | -1.6 dBTP |

容器平均码率实测约 101–102 kb/s（含容器开销），音频流本身为 96 kb/s。

### 循环接缝实测

| 指标 | WAV 母带 | OGG 解码后 |
|---|---|---|
| 首尾拼接跳变 | 0.00193 | 0.00183 |
| 文件内部相邻样本差 p99 | 0.0968 | 0.0952 |
| 首 10 ms RMS | -46.3 dB | -46.4 dB |
| 末 10 ms RMS | -46.1 dB | -46.2 dB |

首尾跳变量仅相当于内部正常样本级差的约 2%，且接缝位于安静段，听感上无爆音、无断点。建议在对照页开启循环，戴耳机连听 3–5 圈复核。

## 包体增量

- 旧 `app/src/main/res/raw/bugu_call.wav`：**125,286 B（122.35 KiB）**
- 新两条 OGG 合计：**145,037 B（141.64 KiB）**
- **净增 19,751 B（+19.29 KiB / +19.75 KB）**

前提是接入时删除旧 `bugu_call.wav`；若旧文件保留，则实际为 +141.64 KiB。

## iOS 复用

- 两条均 < 30 s（1.25 s / 10.16 s），满足 iOS 通知音时长上限。
- iOS 不原生支持 OGG；用 `masters/*.wav` 作为无损源，按需转 CAF/AIFF 后放入 iOS 通知音目录。
- iOS 通知音只播一次，循环响铃需在 App 内用 AVAudioPlayer 循环，不能依赖通知音循环。

## 代码接入方案要点（本轮不实施）

### 现有双重响的根因

- 通知渠道 `bugu_alarm_ring` 在 `AlarmNotifications.ensureChannel()` 里用 `RingtoneManager.getDefaultUri(TYPE_ALARM)` 设置了系统闹铃；
- 同时 `RingActivity` 用 `MediaPlayer.isLooping = true` 循环 `R.raw.bugu_call`；
- 到点时两路同时发声，形成叠响。

### 接入要点

1. **响铃页接管**：`RingActivity` 改为播放 `R.raw.bugu_alarm_loop`（循环、`USAGE_ALARM`、音频焦点由响铃页统一管理），停止时统一释放。
2. **撤掉通知渠道响铃**：新建渠道 **`bugu_alarm_ring_v2`**，`IMPORTANCE_HIGH`，`setSound(null, null)`，震动关闭（震动也由响铃页接管），保留 `CATEGORY_ALARM` + `setFullScreenIntent`。
3. **删除旧渠道** `bugu_alarm_ring`（Android 26+ 渠道声音/重要性创建后不可改，只能换新 id；删除旧渠道会丢用户自定义设置，属预期代价）。同步更新所有渠道检查（如看板里的 `AlarmNotifications.CHANNEL_ID`）到 v2。
4. **`android.resource://` 优先，`content://` 兜底**：优先 `android.resource://com.bugu/<resId>`；若个别 OEM 对 resource URI 播放失败，把 raw 资源复制到 cache，经 `FileProvider` 生成 `content://` 兜底（或用 `openRawResourceFd` + `setDataSource(fd, offset, length)`）。
5. **通知动机的角色**：`alarm_notice.ogg` 用于一次性提示场景（普通通知渠道提示音 / iOS 本地通知），不参与响铃循环；具体挂载点在下一轮接入时与文案会话一起确认。
6. **清理旧资源**：接入完成后从 `res/raw` 删除 `bugu_call.wav`，包体增量按上文 +19.29 KiB 计。

## 遗留风险

- 母带源为官方预览 MP3（有损），非原始 AIF/WAV；CC0 合规，但保真度有理论上限。
- 为保留布谷钟瞬态，未做压限；受源峰值约束，循环段响度 -19.7 LUFS，比旧 `bugu_call`（-14.9 LUFS）低约 4.8 LU。若产品要求与旧响度对齐，需加真峰值限幅后重出，会牺牲少量瞬态。
- OGG 96 kbps 为有损编码；接缝指标已在解码后复核，但最终仍应以真机外放/耳机/蓝牙试听为准。
- 渠道迁移会清掉用户对旧渠道的自定义设置；旧渠道删除后不可恢复。
