# 布谷 × KuiklyUI POC 验证报告与整体迁移路线图

> 日期：2026-11-05 ｜ 结论：**POC 通过**，整体转 Kuikly 决策成立，可进入正式迁移
> POC 工程：`/Users/peng/Documents/phk/kuikly-poc/KuiklyUI`（官方仓库 + 布谷两页，独立于 bugu 仓库，未 commit）

---

## 一、POC 验证结果

### 1.1 验证环境

| 项 | 值 |
|---|---|
| Kuikly 版本 | main 源码构建（core 2.0.0-SNAPSHOT，本地编译） |
| 工具链 | JDK 17.0.21（Temurin，本地装于 kuikly-poc/toolchain）+ Gradle 7.6.3 + AGP 7.4.2 + Kotlin 2.1.21 |
| 验证设备 | 独立新建 AVD `kuikly36`（API 36，未触碰文案会话占用的 emulator-5554） |
| 布谷页面 | 看板页 `BuguDashboardPage` + 新建提醒页 `BuguCreatePage`，Compose DSL 编写，mock 数据 |

### 1.2 逐项验证清单（全部通过，截图在 kuikly-poc/ 目录）

| # | 验证点 | 结果 | 证据 |
|---|---|---|---|
| 1 | 工具链可落地：官方仓库完整编译出 APK | ✅ BUILD SUCCESSFUL（增量 1m15s） | build.log |
| 2 | 看板页 UI 还原：咕咕鸟(Canvas 自绘)+问候+进行中卡+时钟卡+分段 Tab+提醒卡列表+主操作，品牌色板(奶油底/珊瑚/薄荷/靛蓝)完全对齐 | ✅ 与 Jetpack Compose 版信息架构一致 | shot_dashboard.png |
| 3 | 三出口交互：点「稍后 15 分」→ 状态变更 + 绿色回执条「15 分钟后咕咕再叫你」+ 进行中卡消失 | ✅ | shot_snooze.png |
| 4 | 页面跳转：RouterModule.openPage 直达新建页 | ✅ | shot_create.png |
| 5 | 输入框：BasicTextField 输入正常（含光标/占位符样式） | ✅ | shot_form.png |
| 6 | Chip 选择 + 咕咕摘要实时联动（响应式状态驱动） | ✅ 妈妈→爸爸切换即摘要更新 | shot_form.png |
| 7 | 提交 → 成功态（✓ 咕咕记下了！+ 表单数据回显） | ✅ | shot_done.png |
| 8 | closePage 返回看板 | ✅ | shot_back.png |
| 9 | 分段 Tab 切换：列表联动 + 选中白底 | ✅（换 fillMaxWidth 布局后） | shot_tab9.png |
| 10 | 标准 KMP 库复用：kotlinx-datetime 0.6.2（布谷同版本）在 Kuikly Kotlin 2.1.21 下正常 | ✅ 时钟每秒刷新无异常 | — |
| 11 | 稳定性：全程 logcat 0 FATAL | ✅ | — |

### 1.3 POC 中踩到的坑（正式迁移需固化）

| # | 坑 | 解决/规避 |
|---|---|---|
| 1 | **Kotlin 版本锁**：Kuikly 工具链上限 2.1.21，布谷 app 用 2.3.21 | Kuikly 模块独立工程/独立版本编译，宿主以预编译产物集成；`shared/` 逻辑层降到 2.1.21（Ktor/kotlinx 全兼容） |
| 2 | AGP 7.4.2 默认 build-tools 30.0.3 / compileSdk 34，与免 Google 源环境冲突 | 全局 hook 覆盖为 buildTools 36.0.0 + compileSdk 36 + `android.aapt2FromMavenOverride` 指 build-tools 36 的 aapt2（已写入 POC 仓库 gradle 配置） |
| 3 | **matchParentSize 覆盖层 + RowScope.weight 的分段控件点击不响应**（3 种写法实验：覆盖层吞点击/weight 命中区错位），换 `fillMaxWidth` 比例布局 + 选中态自带白底后正常 | 迁移规范：分段控件用已验证写法或 material3 TabRow；避免在可点击内容下方铺全尺寸装饰层；此问题值得反馈 Kuikly 社区 |
| 4 | Gradle 官方源/google() 直连挂起 | 阿里云镜像 init 脚本（~/.gradle/init.d/kuikly-mirrors.gradle）+ 腾讯镜像 Gradle 发行包（Kuikly 插件仅在腾讯镜像有） |
| 5 | a11y 树文本为空（uiautomator dump 拿不到文本） | 与布谷现有 Compose 自动化结论一致；UI 自动化继续走坐标 + 截图方案 |

---

## 二、整体迁移路线图（六端目标）

### 原则

1. **Android 线上版本（1.1.x）不动**，Kuikly 新 UI 以页面级嵌入灰度，避免已打磨 UI 的大爆炸重写回归；
2. **逻辑层先行**：`shared/` KMP 核心（API/Ktor/Planner/幂等/上报队列/端口抽象）是两条路线通用资产，继续完成并降到 Kotlin 2.1.21；
3. **平台能力不跨端**：闹钟（AlarmManager/UNUserNotificationCenter/BGTaskScheduler）、全屏响铃、推送（JPush/APNs）、通知权限引导一律各端原生模块 + Bridge，这是布谷产品命脉，不做降级。

### 阶段划分

| 阶段 | 内容 | 退出标准 | 估算（人月，经验值） |
|---|---|---|---|
| **S0 逻辑层收口** | 完成 `shared/`（jvm/iosArm64/iosSimulatorArm64 目标），接 Android 侧冒烟；Kotlin 降 2.1.21 | shared 测试全绿 + Android 宿主消费验证 | 0.5 |
| **S1 Android 双轨** | Kuikly 容器嵌入 Android 宿主；看板/新建/历史三页 Kuikly 版完成并与原生页并存灰度；统一 Bridge（登录态/路由/响铃入口） | 灰度页面崩溃率 ≤ 原生基线，核心交互对齐 | 1.5–2 |
| **S2 Android 全量切换** | 其余页面迁移 + 响铃页保持原生（RingActivity 不迁）；下线旧 Compose UI | Android 单 UI 栈，旧代码删除 | 1–1.5 |
| **S3 iOS** | Xcode 环境 + Kuikly iOS Render 接入；原生模块：APNs、通知权限、响铃页（SwiftUI 全屏+AVAudioPlayer）、Bridge；UI 直接复用 Kuikly 页面 | iOS 端到端：登录→创建→到点通知→响铃确认 | 2–2.5（含 iOS 原生能力，UI 复用省约 1 人月） |
| **S4 鸿蒙** | DevEco + Kuikly OHOS Render；ArkTS 原生模块（推送/响铃/闹钟）；UI 复用 | 鸿蒙真机全链路 | 1.5–2 |
| **S5 Web/小程序/macOS** | 跟随 Kuikly 平台成熟度（官方 2026 Roadmap：Web/小程序 Release、macOS Beta）；响铃类重能力页面在各端用 H5 唤起原生/仅展示 | 按平台逐个上线 | 1–1.5/端，按需 |

**合计参考**：Android+iOS+鸿蒙三端约 6.5–8.5 人月（UI 一份代码的复利从 S3 起显现）；S0–S2（Android 自身迁移）约 3–4 人月。

### 风险登记

| 风险 | 缓解 |
|---|---|
| Compose DSL 仍为 Beta（2026 才 GA），API 对齐度爬坡 | POC 已验证布谷两页复杂度完全可控；迁移中每页先过 POC 同款验证清单；复杂动效页保留自研 DSL 逃生门 |
| Kuikly 版本锁 + 单一上游（腾讯 TDS） | 锁定 release tag（当前 2.30.0）+ 升级走回归清单；License 允许正常使用/修改/商用，唯「自研热更叠加」需腾讯书面同意（不用即可） |
| 响铃/闹钟体验（锁屏直达、全屏、排队响铃）在 Kuikly 容器页外的可行性 | 这部分**不迁移**，保持原生 RingActivity/VC，Kuikly 页经 Bridge 拉起——路线中已明确 |
| Web/小程序端到点提醒能力天然受限（无系统闹钟） | 六端中的 Web/小程序定位为查看/创建端，到点提醒依赖推送或提示降级，产品侧需知悉 |

---

## 三、立即可执行的下一步（S0）

1. 把本 POC 的工程配置沉淀（镜像 init 脚本、buildTools/compileSdk 覆盖、aapt2 override）归档进 `shared/` 工程文档；
2. `shared/` 逻辑层完成并降 Kotlin 2.1.21，补 commonTest；
3. 在 POC 仓库继续：接 `shared` 的 GeitaApi 替换 mock 数据（Ktor 2.3.10 已在 demo 验证），跑通「登录→看板真实数据」；
4. S1 开工前向 Kuikly 社区确认分段控件命中测试问题（issue），并评估固定到 2.30.0。

---

## 附：POC 产物清单

- `/Users/peng/Documents/phk/kuikly-poc/KuiklyUI/`：官方仓库 + 布谷页面（`demo/src/commonMain/kotlin/com/tencent/kuikly/demo/pages/compose/Bugu{Brand,DashboardPage,CreatePage}.kt`）
- 截图：`shot_dashboard / shot_snooze / shot_create / shot_form / shot_done / shot_back / shot_tab9.png`（全链路）
- 构建日志：`kuikly-poc/build.log`
- 独立 AVD：`kuikly36`（可复用，未占用 haohao36/emulator-5554）
- bugu 仓库：本次仅新增本报告与调研报告，**未改任何业务代码，未 commit**
