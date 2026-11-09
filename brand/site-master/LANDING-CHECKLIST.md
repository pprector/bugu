# 布谷 · 母版落地清单（v1 · 待产品选定方案一 / 三 A）

> 适用范围：方案一（站点母版）与方案三 A（双呆毛 + 站点）共用同一套替换点，**只有 App 前景图一个文件不同**，其余 21 处完全一致。
> 本清单只做核对用，不代表已改动任何三端代码。

---

## 0. 纪律（先读，避免误伤）

| 文件 | 状态 | 本次动作 |
|---|---|---|
| `app/src/main/kotlin/com/bugu/kuikly/BuguBridgeNativeModule.kt` | 另一会话正在改（`android.R.drawable.ic_dialog_info` → `ic_notification`） | **不碰、不 checkout、不还原** |
| `website/index.html`、`website/bond/index.html`、`website/privacy.html`、`website/terms.html` | 本批之前已由本会话补过 OG / favicon 版本参数（未提交） | **保留**，本次只在其上继续改 |
| `website/public/favicon.svg`（2242B，旧 App 版） | 未部署 | 替换，勿还原 |
| `website/src/components/GuguBird.vue`（首页大咕咕插画） | 母版来源 | **不动** |

---

## 1. 线上现状取证（为什么必须有这份清单）

刚实测 `www.yzhzhaohao.cn`：

| URL | 实际返回 | 结论 |
|---|---|---|
| `/favicon.svg` | 634B，`sha256=9d3ec492…15a3e`，与 `brand/site-master/gugu-site-master.svg` **逐字节相同**（`Last-Modified: 11-05`，即原站点自带文件，从未被覆盖） | 线上 SVG 就是母版本身，不需要「替换」，只需要别把它改坏 |
| `/favicon-16.png` `/favicon-32.png` `/favicon-512.png` `/apple-touch-icon.png` | HTTP 200，但 `Content-Type: text/html`、`Content-Length: 1060`，内容是 SPA 兜底 `index.html` | **这些 PNG 从来没部署成功过**（页面链路实际是 404 兜底） |
| `/`（首页） | 11-05 构建，favicon 只有一条 `favicon.svg`，无版本参数、无 apple-touch-icon | 线上 HTML 还是旧构建 |

→ 这就是「改了但没生效」的实证：**SVG 生效了，PNG 全部没生效**。本次必须把 `public/` 全量部署 + 版本参数同步，否则浏览器和 iOS 主屏继续吃旧图。

---

## 2. App（7 项）

### 2.1 资源归属先说清（同名互斥问题的答案）

- `AndroidManifest.xml:40` → `android:icon="@mipmap/ic_launcher"`
- 仓库里 **没有** `mipmap-hdpi/xhdpi/…`，只有 `res/mipmap-anydpi-v26/ic_launcher.xml`，内容是 adaptive-icon，三层全部指向 `@drawable/*`：

```xml
<background android:drawable="@drawable/ic_launcher_bg" />
<foreground android:drawable="@drawable/ic_launcher_foreground" />
<monochrome android:drawable="@drawable/ic_launcher_monochrome" />
```

- `minSdk = 26` → 不需要任何密度 PNG 兜底，anydpi-v26 一个文件就够。

**怎么避免同名互斥 / 改了没生效：**

1. **容器不动**：`mipmap-anydpi-v26/ic_launcher.xml` 保持原样，只换它引用的 3 个 `drawable`。
2. **三个层名一个都不能改**：`ic_launcher_bg` / `ic_launcher_foreground` / `ic_launcher_monochrome`。改名 → manifest 的 `@mipmap/ic_launcher` 仍指旧引用，等于没换。
3. **不要往 `res/drawable/` 放名为 `ic_launcher.xml` 的文件**。`@drawable/ic_launcher` 与 `@mipmap/ic_launcher` 是两种资源类型，编译不报错、但 manifest 只认 mipmap → 静默不生效。
4. **不要新增 `res/mipmap-anydpi-v26/ic_launcher.png`**：与同名 XML 在同一限定符目录 → `duplicate resources` 构建失败。
5. 当前 `drawable/ic_launcher_bg.xml` 是 `#FFEDE6`（旧奶油粉），新母版是 `#FAF8F5`，**这是有意的可见变化**，不是笔误。

### 2.2 替换表

| # | 当前文件 | 现状 | 替换为（品牌目录相对路径） | 需重建 | 构建产物 |
|---|---|---|---|---|---|
| A1 | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 3 层 adaptive-icon | **不改** | 是 | apk |
| A2 | `app/src/main/res/drawable/ic_launcher_bg.xml` | 108 视口，`#FFEDE6` 满铺 | `brand/site-master/app/ic_launcher_background.svg`（`#FAF8F5`）转 VectorDrawable | 是 | apk |
| A3 | `app/src/main/res/drawable/ic_launcher_foreground.xml` | 108 视口，App 原图标 15 条 path | `brand/site-master/app/ic_launcher_foreground.svg`（`<g translate(21,21) scale(1.03125)>` + 站点几何）转 VectorDrawable | 是 | apk |
| A4 | `app/src/main/res/drawable/ic_launcher_monochrome.xml` | App 原结构单色层 | `brand/site-master/app/ic_launcher_monochrome.svg` 转 VectorDrawable | 是 | apk |
| A5 | `app/src/main/res/drawable/ic_notification.xml` | 24dp 白色剪影（App 双呆毛版） | `brand/site-master/app/ic_notification.svg` 转 VectorDrawable | 是 | apk |
| A6 | `AndroidManifest.xml:40` | `@mipmap/ic_launcher` | **不改** | — | — |
| A7 | Play 商店图标 | 仓库内无此资产 | `brand/site-master/app/ic_launcher_512.png`（512×512，32 位 PNG、含底、直角满幅） | **否** | 直接上传 Play Console |

> A5 注意：另一会话正在把代码里的通知图标指向 `ic_notification`。换内容可以，但**不要同时改 `BuguBridgeNativeModule.kt`**，也不要删除该文件，否则那一路会 404 掉回系统图标。

### 2.3 App 构建与验收

```bash
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/bugu-app-release.apk
```

验收（必须全过才算「生效」）：

1. `unzip -l app/build/outputs/apk/release/*.apk | grep -E "ic_launcher|ic_notification"` —— 不应出现任何 `mipmap-*dpi/ic_launcher.png`（出现即说明混入了旧密度图）。
2. 装到机器上看桌面图标是 **站点绿芽版**（不是双呆毛）。
3. 下拉通知栏看小图标是新的 24dp 剪影，颜色由系统染色（白）。
4. 长按图标 →「图标」→ 主题色（Material You）能跟随染色（验证 monochrome 层生效）。
5. `versionCode` 上传前 +1（当前 `4 / 1.1.2`，见 `app/version.properties`）。

---

## 3. 官网（3 处内联 + 4 个 HTML + 6 个 public 文件）

### 3.1 页头内联 SVG（3 处，同一段内容）

| # | 当前文件 | 行 | 现状 | 替换为 |
|---|---|---|---|---|
| W1 | `website/src/components/SiteHeader.vue` | 23–43 | `<svg viewBox="0 0 64 64" class="logo-mark">` 内是 App 原图标（双呆毛 + 声波，`translate(-26.43,-19.15)`） | `gugu-site-mark.svg` 的 4 条 path（见附录 A，**无腮红版**） |
| W2 | `website/src/components/LegalLayout.vue` | 15–35 | 同上 | 同上 |
| W3 | `website/src/pages/BondPage.vue` | 108–128 | 同上 | 同上 |

- 只换 `<svg>…</svg>` 内部，`class="logo-mark"`（30×30）与该行的「布谷 / BUGU」文字保持不动。
- 无需改 CSS：新几何在 30px 下占画布 81%，比原来 39% 大一圈，视觉上正好补上现在「图标偏小」的问题。

### 3.2 四个 HTML 的 favicon 引用（每页 4 条 icon + 1 条 og:image）

| # | 当前文件 | 行 | 现状 | 替换为 |
|---|---|---|---|---|
| W4 | `website/index.html` | 5–8 | `./favicon.svg?v=20261109`、`favicon-32.png`、`favicon-16.png`、`apple-touch-icon.png` | 路径不变，**版本号 → `?v=20261110`** |
| W5 | `website/bond/index.html` | 5–8 | `../` 前缀同上 | 同上 |
| W6 | `website/privacy.html` | 5–8 | `./` 同上 | 同上 |
| W7 | `website/terms.html` | 5–8 | `./` 同上 | 同上 |
| W8 | 上述 4 页 | 23 / 24 | `og:image = https://www.yzhzhaohao.cn/og-image.png`（**无版本参数**） | 追加 `?v=20261110` |

> 关键：`?v=` 只加在 HTML 里没用，**必须同时把 `public/` 的 PNG 换成新文件再重新 build**。版本参数只是让浏览器放弃缓存，不会让旧文件变新。

### 3.3 `website/public/` 资产

| # | 当前文件 | 现状 | 替换为（`brand/site-master/favicon/`） |
|---|---|---|---|
| W9 | `website/public/favicon.svg` | 2242B，旧 App 推导版（64 视口、`#FFEDE6` 圆角底） | `favicon.svg`（634B，与线上一致） |
| W10 | `website/public/favicon-16.png` | 旧版 | `favicon-16.png` |
| W11 | `website/public/favicon-32.png` | 旧版 | `favicon-32.png` |
| W12 | `website/public/favicon-48.png` | 旧版（HTML 未引用，保留一致性） | `favicon-48.png` |
| W13 | `website/public/favicon-512.png` | 旧版 | `favicon-512.png` |
| W14 | `website/public/apple-touch-icon.png` | 180×180 旧版 | `apple-touch-icon.png` |
| W15 | `website/public/og-image.png` | 旧版 | `brand/site-master/web/og-1200x630.png` |

> 以上 W1–W15 **全部需要重新构建**：W1–W3 是 `.vue` 源码，W4–W8 是 HTML，W9–W15 是 `public/` 静态资产（Vite 构建时整目录拷进 `dist/`）。构建产物统一在 `website/dist/`，再由部署脚本同步到服务器站点根目录。

### 3.4 官网构建与验收

```bash
pnpm -C website build
# 产物：website/dist/（vite base='./'；public/ 会被整目录拷贝）
```

验收：

1. `grep -o 'favicon[^"]*' website/dist/index.html` → 4 条均带 `?v=20261110`。
2. `shasum -a 256 website/dist/favicon.svg` → `9d3ec4928db2256cdf7e0f322aaf4f430a03352ab00b84735c431529d2115a3e`。
3. `file website/dist/favicon-16.png` → PNG image data，**不是** HTML。
4. 部署后 `curl -sI https://www.yzhzhaohao.cn/favicon-32.png` → `Content-Type: image/png`（现在是 `text/html`，这条是本次的核心验收项）。
5. 部署后 `curl -s https://www.yzhzhaohao.cn/ | grep favicon` → 能看到 `?v=20261110`。

---

## 4. 后台（4 项）

| # | 当前文件 | 行 | 现状 | 替换为 |
|---|---|---|---|---|
| B1 | `admin/src/components/common/system-logo.vue` | 3–28 | `<mask>`（白形体 + 黑负形眼嘴）+ `<rect fill="currentColor" mask=…>`，几何为 App 原图标 | `brand/site-master/admin/bugu-admin-mark.svg` 内容（`currentColor` + `fill-rule="evenodd"` 负形，无 mask） |
| B2 | `admin/public/favicon.svg` | — | 2242B 旧版 | `brand/site-master/favicon/favicon.svg`（634B） |
| B3 | `admin/public/apple-touch-icon.png` | — | 旧 180×180 | `brand/site-master/favicon/apple-touch-icon.png` |
| B4 | `admin/index.html` | 5 | `<link rel="icon" href="/favicon.svg" />`（无版本参数） | `href="/favicon.svg?v=20261110"` |

- `system-logo.vue` 的 `.app-logo { color: rgb(var(--primary-600-color)) }` 保留：新 SVG 用 `currentColor`，换主题色照样跟随。
- 后台生产 base 是 `/admin/`（`admin/.env.prod`），Vite 会把 `/favicon.svg` 重写成 `/admin/favicon.svg` —— 所以后台 favicon 与官网根 favicon **是两个文件，必须分别替换**。
- 以上 B1–B4 **全部需要重新构建**，产物在 `admin/dist/`（`assets/`、`favicon.svg`、`apple-touch-icon.png`、`index.html`）。

构建与验收：

```bash
pnpm -C admin build     # -> admin/dist/
grep -o 'href="[^"]*favicon[^"]*"' admin/dist/index.html   # 期望 /admin/favicon.svg?v=20261110
```

---

## 5. 版本参数（独立一项，最容易漏）

| 项 | 现值 | 改为 | 数量 |
|---|---|---|---|
| 四页 favicon 链接 | `?v=20261109` | `?v=20261110` | 16 处（4 页 × 4 条） |
| 四页 `og:image` | 无参数 | `?v=20261110` | 4 处 |
| 后台 `favicon` | 无参数 | `?v=20261110` | 1 处 |

改完自检（应为 0）：

```bash
grep -rn "v=20261109" website/index.html website/bond/index.html website/privacy.html website/terms.html | wc -l
```

> 若上线日不是 11-10，把 `20261110` 整体换成实际上线日；**同一天内二次发布**必须再 +1（如 `20261110b`），否则 CDN/浏览器仍吃缓存。

---

## 6. 一键核对脚本（部署后跑，全绿才算完成）

```bash
BASE=https://www.yzhzhaohao.cn
for f in favicon.svg favicon-16.png favicon-32.png favicon-512.png apple-touch-icon.png og-image.png; do
  printf "%-22s " "$f"
  curl -sI "$BASE/$f" | awk 'tolower($0) ~ /content-type/ {print $2}'
done
# 期望全部为 image/*；若出现 text/html 即为 404 兜底，说明没部署成功

curl -s "$BASE/" | grep -o 'favicon[^"]*' | sort -u
# 期望全部带 ?v=20261110
```

---

## 7. 回滚

- 代码：三处 `.vue` + 四个 HTML 用 `git diff` 反向 patch（**不要** `git checkout` 整个文件，会连带冲掉 App 通知图标的改动）。
- 资产：`website/public/`、`admin/public/` 的旧文件先备份到 `brand/site-master/_backup/` 再覆盖。
- App：已上架版本无法回收，`versionCode` 只增；如需回退，发新版本覆盖。

---

## 附录 A · 页头内联片段（无腮红，直接粘进 3 个 `.vue`）

```html
<circle cx="32" cy="36" r="26" fill="#FA5E38"/>
<ellipse cx="32" cy="43" rx="16" ry="13" fill="#FFF6EE"/>
<circle cx="24" cy="30" r="3" fill="#1E1B18"/>
<circle cx="40" cy="30" r="3" fill="#1E1B18"/>
<path d="M32 34 L28 40 L36 40 Z" fill="#FFC966"/>
<path d="M32 10 C32 6 36 4 38 6" stroke="#10B981" stroke-width="4" stroke-linecap="round" fill="none"/>
<circle cx="39" cy="7" r="4.5" fill="#10B981"/>
```

## 附录 B · SVG → VectorDrawable 转换要点（A2–A5 用）

VectorDrawable 不支持 `<circle>` / `<ellipse>`，必须转 pathData；圆用双弧写法：

| 元素 | pathData |
|---|---|
| 背景满铺 | `M0,0 H108 V108 H0 Z`（`fillColor="#FAF8F5"`） |
| 身体 `c(32,36) r26` | `M6,36 a26,26 0 1,0 52,0 a26,26 0 1,0 -52,0 Z` |
| 肚皮 `c(32,43) 16×13` | `M16,43 a16,13 0 1,0 32,0 a16,13 0 1,0 -32,0 Z` |
| 眼 `c(24,30) r3` / `c(40,30) r3` | `M21,30 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 Z` / `M37,30 …` |
| 嘴 | `M32,34 L28,40 L36,40 Z` |
| 嫩芽茎 | `M32,10 C32,6 36,4 38,6` + `strokeColor="#10B981" strokeWidth="4" strokeLineCap="round"` |
| 叶 `c(39,7) r4.5` | `M34.5,7 a4.5,4.5 0 1,0 9,0 a4.5,4.5 0 1,0 -9,0 Z` |

- 前景/单色层保留 `<group android:translateX="21" android:translateY="21" android:scaleX="1.03125" android:scaleY="1.03125">`，内层坐标仍用 64 视口。
- 单色层/通知图标所有 `fillColor` 统一 `#FFFFFF`，负形用 `android:fillType="evenOdd"`（注意大小写，`evenodd` 在 VD 里非法）。
- **不要把 `.svg` 直接丢进 `res/drawable/`**：aapt2 不认 SVG，会构建失败。

---

_资产来源：`brand/site-master/`（母版 `gugu-site-master.svg` 与线上 favicon 逐字节一致）。_
_本清单不包含任何已执行的代码改动。_
