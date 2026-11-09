# 咕咕（Gugu）品牌色板与使用规范 · 冻结版

> **图标造型自 2026-11-06 冻结，不再做改版。** 全站唯一母版 = `app/src/main/res/drawable/ic_launcher_foreground.xml` 的真实造型与实测色值。
> 不使用绿色呆毛、不加描边、不改比例、不去声波。本文档替代此前所有返工/变体版本。

## 0. 采样方法与来源

- 仓库内没有 `mipmap-*dpi/ic_launcher.png`，实际图标源是：
  - `app/src/main/res/drawable/ic_launcher_foreground.xml`（矢量前景）
  - `app/src/main/res/drawable/ic_launcher_bg.xml`（矢量背景）
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`（adaptive 装配）
- 做法：把前景矢量按原坐标 1:1 还原为 SVG，用 Chrome 无头渲染，再逐像素解码 PNG 统计精确 RGB。
- 采样结果与 XML 的 `fillColor` 完全一致（无色彩配置偏移）。

## 1. 实测色值（原图标，全部保留）

| 名称 | Hex | 采样位置 | 用在哪 |
|---|---|---|---|
| 身体 Body | `#FA5E38` | 圆心上方 (54,40) | 圆身、心形肚皮上的心 |
| 翅膀 Wing | `#E0523F` | 左右翅 (34,62)/(74,62) | 双翅、**两颗呆毛**（原图标中翅与身体是两个不同的红） |
| 肚皮 Belly | `#FFF3EC` | (54,56) | 肚皮/脸 |
| 上嘴 Beak | `#FFB020` | (54,54.5) | 上喙 |
| 下嘴 Beak Shade | `#F59E0B` | (54,61) | 下喙 |
| 眼睛 Eye / 墨 Ink | `#1C1917` | (46.4,48.5) | 眼睛、声波、文字 |
| 眼白 Highlight | `#FFFFFF` | (47.1,47.8) | 眼睛高光（原图标自带） |
| 腮红 Blush | `#FFB8A8` | (40.5,58.7) | 左右腮红 |
| 图标底 Surface | `#FFEDE6` | 前景之外的底色 | adaptive 背景、方形图标底、favicon/OG 底 |

**允许色集合 = 上表 9 项。不新增任何品牌色。**

### 派生色（不是新色）
`#998D88` = `#1C1917` 以 45% 不透明度叠在 `#FFEDE6` 上，来自原图标第二道声波的 `stroke-opacity=0.45`。它只在不透明奶油底上出现；在 adaptive 前景里仍是「墨 + alpha」，不是实色。不要写进任何设计稿。

## 2. 规则

- 呆毛 = `#E0523F`，与翅膀同色；绿色方案全部作废。
- 除原图标自带的声波 `stroke` 外，禁止新增任何描边。
- 禁止阴影、渐变、柔光、内发光；不透明度只允许原图标已有的 45% 声波。
- 需要「浅一点的珊瑚」：用原色 + 不透明度，不许调新 hex。
- 造型、比例、元素位置、绘制顺序一律不动；16–32px 只允许做光学补偿（见 §3），色值必须仍来自 §1。

## 3. 尺寸 → 造型规则

| 场景 | 用哪份 | 处理 |
|---|---|---|
| ≥48px（母版、512、OG、横版 Logo、后台 64） | `gugu-master.svg` | 完整 1:1：圆身 + 心形肚皮 + 双翅 + 两颗呆毛 + 嘴 + 腮红 + 声波 |
| 16–32px（favicon） | `favicon/favicon-compact.svg` | 独立光学字形：鸟头撑满画布，眼/嘴/呆毛按 16px 加粗；色值全部原色 |
| 24dp 通知栏 | `gugu-notification.svg` | 纯白实心剪影 + 透明负形双眼/嘴 + 两颗呆毛；系统自动染色 |
| 单色 | `gugu-master-mono.svg` | 实心剪影，眼睛/嘴为负形；`currentColor` |

- 通知剪影必须保留双眼：去掉后圆身会读成「禁止符号」。
- 单色取舍：心形肚皮与翅膀会并入剪影，无法同时可辨；32px 后台优先保证「看得出一只鸟」。
- favicon 16px 不再按 108 画布等比缩（那样鸟只占 10px），改用专用光学字形，仍是原色板。

## 4. 对比度实测（不变）

| 组合 | 比值 | 结论 |
|---|---|---|
| Ink `#1C1917` on Surface `#FFEDE6` | 15.41 : 1 | 正文安全 |
| Wing `#E0523F` on Surface | 3.40 : 1 | 仅图形 |
| Body `#FA5E38` on Surface | 2.75 : 1 | 仅图形（原图标同样如此） |
| Belly `#FFF3EC` on Body | 2.87 : 1 | 图形安全，符合原图标 |
| Beak `#FFB020` on Belly | 1.68 : 1 | 原图标即如此；不要在小尺寸外单独把嘴当信息载体 |
| Blush `#FFB8A8` on Body | 1.89 : 1 | 装饰，可省略 |
| White on Dark `#0F172A` | 17.85 : 1 | 通知剪影（系统会再染色） |

## 5. 文件清单

```
brand/assets/
├─ palette.md                      本文件（冻结版）
├─ gugu-master.svg                 彩色母版（108×108，原图标坐标，含声波）
├─ gugu-master-mono.svg            单色母版（currentColor）
├─ gugu-compact.svg                16–32px 光学参考（32×32）
├─ gugu-notification.svg           24dp 通知剪影（纯白）
├─ app/                            adaptive 前景/背景 108–432、传统 48–192、512、通知 24–96（含 22dp 内边距版）、monochrome 层
├─ favicon/                        16 / 32 / 48 / 180 / 512 PNG + favicon.svg + favicon-compact.svg
├─ web/                            gugu-mark-64.svg、bugu-logo-header.svg + @1x/@2x/@3x、og-1200x630
├─ admin/                          bugu-admin-mark.svg + 墨色/纯白 32、64 PNG
├─ preview.html                    自包含预览
└─ apply-plan.md                   落地清单
```

## 6. 合规自检

- [x] 造型/比例/元素位置与原 `ic_launcher_foreground.xml` 一致；呆毛恢复 `#E0523F`
- [x] 无新增描边 / 阴影 / 渐变；色值仅 §1 的 9 项（另加 45% 声波的合成色）
- [x] 全量 PNG 尺寸与目标一致
- [x] 16px 真实像素下仍是「圆脸 + 双眼 + 琥珀嘴 + 呆毛」
- [x] 24dp 剪影深/浅底均清晰，纯白透明底，双眼保留
- [x] 官网/后台/App 不再引用任何绿色或旧橙色简化图标
