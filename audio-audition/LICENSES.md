# 音源许可记录（LICENSES）

检索与核对日期：2026-11-05。所有候选均为**可商用、免署名、允许修改**；无「仅限个人使用」「许可不明」的素材入选。

## 统一说明

- **CC0 1.0**：<https://creativecommons.org/publicdomain/zero/1.0/>。可复制、修改、分发、商用，无需征得作者同意，也无需署名。本目录仍保留作者与来源页，便于复核。
- **Mixkit Sound Effects Free License**：<https://mixkit.co/license/#sfxFree>。站方明示可商用、免署名、允许修改；不允许把音效作为独立素材转售或再分发（不得做成音效库/素材站的一部分）。把音效作为 App 内闹钟音使用属于正常使用，不属于转售/再分发。
- 本目录的 Freesound 文件是官方 HQ 预览 MP3，Mixkit 文件是官方预览 MP3；`masters/` 下的两条母带是基于 Freesound 预览件制作的。CC0 允许直接使用预览件，但音质有损；若能登录下载原始 AIF/WAV，可用同一处理链重出母带。

## 逐条清单

| # | 本地文件 | 标题 | 作者 | 来源页 | 许可 | 商用 | 修改 | 发版建议 |
|---|---|---|---|---|---|---|---|---|
| 1 | `audio/fs_cuckoo_clock_12.mp3` | Cuckoo Clock Chimes 12.aif | DiArchangeli | <https://freesound.org/people/DiArchangeli/sounds/462930/> | CC0 1.0 | 是 | 是 | 登录下载原始 AIF，裁尾静音后重排 |
| 2 | `audio/fs_vocal_cuckoo_2x.mp3` | Vocal Cuckoos (2x).wav | se2001 | <https://freesound.org/people/se2001/sounds/478670/> | CC0 1.0 | 是 | 是 | 登录下载原始 WAV，增益后截取 |
| 3 | `audio/fs_happy_bird.mp3` | Happy Bird Singing | se2001 | <https://freesound.org/people/se2001/sounds/511094/> | CC0 1.0 | 是 | 是 | 可直接用预览件，建议取原始 WAV |
| 4 | `audio/mk_17.mp3` | Little birds singing in the trees | Mixkit | <https://mixkit.co/free-sound-effects/bird/> | Mixkit Free | 是 | 是 | 用原始 WAV：<https://assets.mixkit.co/active_storage/sfx/17/17.wav> |
| 5 | `audio/mk_3109.mp3` | Relaxing bell chime | Mixkit | <https://mixkit.co/free-sound-effects/bell/> | Mixkit Free | 是 | 是 | 用原始 WAV：<https://assets.mixkit.co/active_storage/sfx/3109/3109.wav> |
| 6 | `audio/mk_2310.mp3` | Melodical flute music notification | Mixkit | <https://mixkit.co/free-sound-effects/notification/> | Mixkit Free | 是 | 是 | 用原始 WAV：<https://assets.mixkit.co/active_storage/sfx/2310/2310.wav> |
| 7 | `audio/fs_soft_bell_loop.mp3` | Soft simple bell loop.mp3 | ZHRØ | <https://freesound.org/people/ZHR%C3%98/sounds/620003/> | CC0 1.0 | 是 | 是 | 登录下载原始 MP3，+12 dB 后检查底噪 |
| 8 | `audio/mk_2014.mp3` | Wind chimes | Mixkit | <https://mixkit.co/free-sound-effects/chime/> | Mixkit Free | 是 | 是 | 用原始 WAV：<https://assets.mixkit.co/active_storage/sfx/2014/2014.wav> |
| 9 | `audio/mk_1065.mp3` | Antique wood chime bell ring | Mixkit | <https://mixkit.co/free-sound-effects/bell/> | Mixkit Free | 是 | 是 | 用原始 WAV：<https://assets.mixkit.co/active_storage/sfx/1065/1065.wav> |
| 10 | `audio/fs_wind_chime_single.mp3` | wind chimes - single 04.wav | Anthousai | <https://freesound.org/people/Anthousai/sounds/398496/> | CC0 1.0 | 是 | 是 | 原始 96 kHz，登录下载后降采样 |
| 11 | `audio/mk_2863.mp3` | Elevator tone | Mixkit | <https://mixkit.co/free-sound-effects/notification/> | Mixkit Free | 是 | 是 | 用原始 WAV：<https://assets.mixkit.co/active_storage/sfx/2863/2863.wav> |
| 12 | `audio/fs_wake_up_02.mp3` | Wake Up 02.wav | LilMati | <https://freesound.org/people/LilMati/sounds/516856/> | CC0 1.0 | 是 | 是 | 仅气质参考：英文 + 峰值 0 dBFS 削波，不建议直接上线 |

现状对照（非候选，仅用于 A/B）：

| 文件 | 说明 | 许可 |
|---|---|---|
| `audio/current_bugu_call.wav` | 现网 `app/src/main/res/raw/bugu_call.wav` 的副本，1.42 s 单声道 44.1 kHz | 项目自有资产 |

## 母带派生记录（组合 A）

| 成品 | 派生自 | 处理 | 许可 | 字节数 |
|---|---|---|---|---|
| `masters/alarm_notice.ogg` | `audio/fs_vocal_cuckoo_2x.mp3`（se2001，CC0） | 裁首尾静音、+12.6 dB、OGG 96 kbps 单声道 44.1 kHz | CC0 1.0 | 15,950 |
| `masters/alarm_notice.wav` | 同上 | 同上，16-bit PCM 无损源 | CC0 1.0 | 110,100 |
| `masters/alarm_loop.ogg` | `audio/fs_cuckoo_clock_12.mp3`（DiArchangeli，CC0） | 裁尾约 3.5 s、-1.6 dB、200 ms 等功率交叉淡化、OGG 96 kbps 单声道 44.1 kHz | CC0 1.0 | 129,087 |
| `masters/alarm_loop.wav` | 同上 | 同上，16-bit PCM 无损源 | CC0 1.0 | 896,156 |

处理细节与响度/接缝实测见 `masters/MASTERING.md`。

## 文件校验（SHA-256）

```
35a9946f407de53ae3caceb88540ebcaf67df06aac5f6c3da54768b86974ea91  current_bugu_call.wav
8cea1f0570ab20f9f530e38a09dbdaf4e5bbef2258103d2af47f62b9f7959d6a  fs_cuckoo_clock_12.mp3
06d926e3f67a6d59d37b3dde751c10158b58876180f7a1b8786a0b3f5d72890c  fs_happy_bird.mp3
f84910c411b369c103828551af239e4b80ee32b6943298808a546db37231afaa  fs_soft_bell_loop.mp3
7a3c72ba935371c65f4d67a8e2e8e6f351edf493d9eb3e11ecd7ba9ea68bd7e3  fs_vocal_cuckoo_2x.mp3
cfbdf667c56cbc3239eef603e5795b1f343e86b74f34de26dd568c054b32cf6b  fs_wake_up_02.mp3
f3654772f54f60fcddfb6b57cca3b6047490506427f4cb59dc653f03103520b9  fs_wind_chime_single.mp3
cbed2597fbe8f7e8d90c65fd544ef29150f7df98459dfb11fe92ea99f862f9ff  mk_1065.mp3
31afb3341b8c583038acbdc1a120f2c45699767ddd3a8fa200ec5f72fb54e89f  mk_17.mp3
fe445764c56ab580a11fee8e6f9743c6d6e6b220af7d461293826b1a8a5d52a6  mk_2014.mp3
31ecf209d5fce9809e15ae0a6d257a241a13190d78259c7e8c22147da26b123d  mk_2310.mp3
384619d4fd6e446c812b9f6e7f0d70bf248794551920b7eade6899713fc3d4fe  mk_2863.mp3
8b294a697c2e3396849d2aa1c0d5e40910a876be704791c4a1bcbeefae8ff252  mk_3109.mp3
c295abfe29e031590bc5d04b1f6e536e3ffc4d6ff65eee7c912ece83018449a4  masters/alarm_loop.ogg
b20ff01699298564281b5fea95ea129bd8e7c660499256cf95b224deeb987d3a  masters/alarm_loop.wav
e6064f1fdd29f6c411383f28b1c39c995fbf42456e931d788156df191b2b7d33  masters/alarm_notice.ogg
ecd6a9793b9e0ba9ef230f6acded87225496657091a67afb52b545e7a0b32982  masters/alarm_notice.wav
```

## 若后续引入需署名的素材

1. 把许可证全文（不是链接）落到仓库 `audio-audition/licenses/<素材 id>.txt`，并记录作者、来源页、许可版本、检索日期。
2. 在 App「关于」页增加「开源许可与音效署名」入口，逐条列出署名文本。
3. Android 渠道音与 iOS 通知音的文件名、哈希一并登记，避免后续替换时丢失出处。
