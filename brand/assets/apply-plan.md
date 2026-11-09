# 咕咕资产落地记录（冻结版）

> **状态：已落盘（2026-11-06），未 commit / 未 push / 未部署。**
> 图标造型自 2026-11-06 冻结，不再做改版。唯一母版 = `app/src/main/res/drawable/ic_launcher_foreground.xml`。
> 本文档替代此前的绿色呆毛版清单；绿色、描边方案全部作废，`brand/variants/` 仅作历史存档，不再被任何端引用。

## 0. 唯一基准

- 造型：原 `ic_launcher_foreground.xml` 的 108 画布坐标，1:1 复刻（含声波）。
- 色板：见 `palette.md` §1（9 项实测色），不新增品牌色。
- adaptive 装配：`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` 三层，前景 = 原彩色前景，monochrome = 新增单色层。

## 1. 资产清单（brand/assets，共 55 个文件）

| 目录 | 数量 | 内容 |
|---|---|---|
| 根目录 | 4 SVG | `gugu-master.svg`（1:1 含声波）、`gugu-master-mono.svg`、`gugu-compact.svg`、`gugu-notification.svg` |
| `app/` | 32 | adaptive 前景/背景 108–432（PNG + SVG）、传统方形 48–192、商店 512、通知剪影 24–96、22dp 内边距版、monochrome 层 |
| `favicon/` | 7 | 16 / 32 / 48 / 180 / 512 PNG + `favicon.svg` + `favicon-compact.svg` |
| `web/` | 7 | `gugu-mark-64.svg`、`bugu-logo-header.svg` + @1x/@2x/@3x PNG、`og-1200x630` SVG/PNG |
| `admin/` | 5 | `bugu-admin-mark.svg` + 墨色/纯白 32、64 PNG |

说明：
- 16/32 favicon 用专用光学字形（鸟头撑满画布、眼嘴加粗），色值仍取原色板。
- 通知剪影保留双眼（去掉会读成禁止符号）；22dp 内边距版供裁切偏紧的机型备用。
- 所有 PNG 均由对应 SVG 以 Chrome 无头实拍导出，尺寸已逐张校验。

## 2. App（已改）

| 文件 | 改动 |
|---|---|
| `app/src/main/res/drawable/ic_notification.xml` | 新增。24dp 单色剪影 VectorDrawable，`fillColor=#FFFFFF` + `evenOdd` 负形双眼/嘴，系统自动染色 |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | 新增。108dp 单色层，眼睛/嘴为负形，供 Android 13+ 主题图标 |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | `<monochrome>` 由彩色前景改为 `@drawable/ic_launcher_monochrome` |
| `app/src/main/kotlin/com/bugu/schedule/AlarmNotifications.kt` | `setSmallIcon(R.drawable.ic_launcher_foreground)` → `R.drawable.ic_notification` |
| `app/src/main/kotlin/com/bugu/diagnostics/LocalNotificationTest.kt` | 同上；测试通知与正式提醒走同一小图标 |
| `app/src/main/kotlin/com/bugu/PushReporter.kt` | 新增 `JPushInterface.setDefaultPushNotificationBuilder(BasicPushNotificationBuilder(...).statusBarDrawable = R.drawable.ic_notification)`，避免极光回落彩色图标 |

未做（刻意）：
- **没有**往 `res/mipmap-*dpi` 加位图，也没有改 `AndroidManifest.xml` 的 `android:icon`——保持纯矢量，避免与另一会话的 Gradle/shared 改动冲突。
- 没有动 `app/build.gradle.kts`、`app/settings.gradle.kts`。
- 没有改任何文案、版本号（仍 4 / 1.1.2）、布局。

## 3. 官网（已改）

| 文件 | 改动 |
|---|---|
| `website/src/components/SiteHeader.vue` | 页头内联 SVG → 原图标 1:1 图形（含双呆毛、双翅、声波）；「布谷 BUGU」字标不动 |
| `website/src/components/LegalLayout.vue` | 法务页页头内联 SVG 同款 |
| `website/src/pages/BondPage.vue` | 绑定页页头内联 SVG 同款；页面内 `GuguBird` 吉祥物插画未动 |
| `website/index.html`、`privacy.html`、`terms.html`、`bond/index.html` | 各 4 行 favicon 引用：`favicon.svg` / `favicon-32.png` / `favicon-16.png` / `apple-touch-icon.png`（bond 目录用 `../`） |
| `website/public/` | 覆盖 `favicon.svg`；新增 `favicon-16.png`、`favicon-32.png`、`favicon-48.png`、`apple-touch-icon.png`、`og-image.png` |

## 4. 后台（已改）

| 文件 | 改动 |
|---|---|
| `admin/src/components/common/system-logo.vue` | 渐变橙色鸟 → 单色 mask 鸟（`currentColor` 跟随 `--primary-600-color`），32/64 两处复用 |
| `admin/public/favicon.svg` | 覆盖为冻结版母版 |
| `admin/public/apple-touch-icon.png` | 新增 180 |

## 5. 回滚

- 图标：恢复 `mipmap-anydpi-v26/ic_launcher.xml` 的 monochrome 行、删除两个新 drawable 即可；彩色前景/背景原文件未动。
- 通知：两处 `setSmallIcon` 改回 `R.drawable.ic_launcher_foreground`，去掉 `PushReporter.kt` 的 builder 设置。
- 官网/后台：`git checkout -- <文件>` 即可（均为工作区改动，无提交）。

## 6. 验收

| 项 | 结果 |
|---|---|
| `./gradlew assembleRelease` | BUILD SUCCESSFUL |
| `adb install -r` → emulator-5554 | 成功，版本仍 `versionCode 4 / versionName 1.1.2` |
| 通知栏（浅色/深色） | 通知行显示蓝底白色咕咕（monochrome 生效）；heads-up 小图标 8× 放大后为「圆头 + 双眼 + 嘴 + 双呆毛」，不是白块/彩块 |
| `logcat` | 无 `FATAL EXCEPTION`，crash buffer 为空 |
| `website` `pnpm build` | 通过；`/`、`/privacy.html`、`/terms.html`、`/bond/?code=123456` 页头/页脚/favicon 已逐页截图 |
| `admin` `pnpm build` | 通过；`SystemLogo` 引用路径一致 |

截图：`brand/assets/screenshots/`
- `app-notification-light.png` / `app-notification-dark.png`：通知栏深浅两套
- `app-notification-icon-8x.png`：状态栏小图标 8× 放大
- `app-statusbar-light.png` / `app-statusbar-dark.png` / `app-statusbar-on-dark-app.png`
- `website-index.png` / `website-privacy.png` / `website-terms.png` / `website-bond.png`

## 7. 仍存在的风险

1. **极光真实推送**：`statusBarDrawable` 已固定，但控制台若单独下发图标/自定义布局，仍可能覆盖；需一条真机推送复核。
2. **平板型 AVD 状态栏**：该 AVD 常驻栏不显示通知图标，证据以通知栏 + heads-up 为准；手机上需再确认一次。
3. **官网大咕咕插画**（`GuguBird.vue`，橙色 + 绿色嫩芽）是页面插画不是图标，本次按「只动图标」未改，视觉上与图标母版仍有差异。
4. **favicon 缓存**：浏览器可能仍显示旧图标，换名或加版本参数才能强刷（本次未加）。
5. **后台单色 Logo**：32px 下心形肚皮/翅膀并入剪影，只能保证「看得出是一只鸟」，不保证细节可辨。
