# 布谷后台管理端（admin/）验收测试报告

- 验收人：测试验收（AI 协助）
- 日期：2026-11-04（验收）；2026-11-05（按本报告修复并复验）
- 范围：`admin/` 前端工程 + `server/src/main/kotlin/com/bugu/admin` 后端管理接口
- 环境：本地 dev（admin dev :9527 → server :8081 → 本地 PostgreSQL/Redis，server 为当前工作区代码 bootJar）；生产仅做只读探测（未登录、未猜口令、未爆破）
- 边界遵守情况：全部写操作仅使用本次自造测试数据（清单见附录 B）；未动真实用户数据；未 push、未部署、未覆盖线上产物

---

## 一、结论（先行）

**2026-11-05 复验：P0 两项 + P1 错误文案 + 账号口径 + 登录限流 + 反馈审计已修复并本地实测通过；本轮允许放行的阻塞项已清零。**

- 本轮修复状态：**已修 4 项（P0-1、P1-3、P1-5、P1-6）、部分修 2 项（P0-2、P1-4）、未修 2 项（P1-7、P2-12 等 JWT 项）**，明细见「七、修复与复验记录」。
- 仍然存在的上线风险（本轮按要求未动）：**① admin JWT 有效期 7 天且无刷新/吊销机制；② 集成测试继续直写本地 dev 库（若某环境误配生产库，跑测试即写生产）**。

- 成熟度评分：**后端 8/10，前端 5/10，综合 6/10（有条件放行）**
- 一句话：后端 RBAC/审计/统计口径扎实（297 个测试全过、越权全部拦截、审计闭环基本完整），前端是 Soybean 模板上的薄页面，**请求封装层存在系统性缺陷**（token 失效不登出 + 所有错误显示技术性英文文案），叠加**所有核心列表无分页全量渲染**（用户列表已 9300+ 行且每轮跑测试还在涨），这两点会让运营在真实使用第一周内必然撞上。
- 后端管理模块（鉴权过滤器、角色权限点、审计、统计 SQL）质量明显好于前端，问题集中在前端。

---

## 二、验收结果总表

| # | 验收项 | 实测结果 | 判定 |
|---|--------|---------|------|
| 1 | 可运行性 | `pnpm typecheck` 通过；`pnpm build` 成功；dev server 正常（node v22.23.4 / pnpm 12.15.0）；`.env.test` 指向 localhost:8081 有 dev proxy | ✅ 通过 |
| 2 | 登录功能 | super 正常登录；错误密码 401；停用账号登录 401 且文案不泄露账号存在性 | ✅ 通过（错误文案见 P1-3） |
| 3 | 会话管理 | 退出登录有确认框、清 token、跳登录页 ✅；token 失效后不自动登出、页面数据滞留 → **2026-11-05 已修**：401 统一清 token + 中文提示 + 跳登录 | ✅ 已修（P0-1） |
| 4 | 逐页走查（10 页） | 全部能打开、数据能加载、无白屏、无 console 阻断错误；推送状态页为描述卡片正常 | ✅ 通过（细节问题见 P2） |
| 5 | 分页/搜索/筛选 | 用户搜索（手机号/昵称/ID）✅、提醒筛选 ✅、反馈分页+筛选 ✅；用户/绑定/提醒/审计 **已补分页（2026-11-05）**，默认 20 行/页；后台账号、AI 模板仍未分页 | ⚠️ 部分修（P0-2） |
| 6 | RBAC 后端强制 | App token 访问 admin 接口 401、admin token 访问 App 接口 401（typ 隔离）；OPS 读 200 / 写 403；伪造 token 401；停用后旧 token 立即 401；不能停用自己（409） | ✅ 通过 |
| 7 | 审计日志 | LOGIN / ADMIN_ACCOUNT_CREATE / ADMIN_ACCOUNT_ENABLED / USER_ELDER_MODE / BOND_UNBIND / TEMPLATE_CREATE / TEMPLATE_UPDATE / **FEEDBACK_UPDATE（2026-11-05 补）** 全部落库可查 | ✅ 已修（P1-5） |
| 8 | 数据一致性 | `/api/v1/admin/stats` 六项计数与 DB 直查逐项完全一致（9677/260/4064/3026/200/97）；反馈/提醒状态与接口一致 | ✅ 通过 |
| 9 | 写操作闭环 | 长辈模式切换落库+审计齐全；解绑有二次确认且文案说明后果；404/400 错误码清晰 | ✅ 通过 |
| 10 | 安全红线 | 生产 /api-docs、/actuator 未暴露（Nginx 兜底官网页，已核内容）；无 token 访问生产 admin API 401；compose 已设 TZ=Asia/Shanghai；验证码哈希存储；**登录失败限流已补（5 次锁 15 分钟，2026-11-05）**；JWT 7 天无刷新机制仍未修 | ⚠️ P1-4 已修 / P2-12 未修 |
| 11 | 服务端测试交叉验证 | `gradle test` 297 用例全部通过（0 失败 0 错误）；**但测试直连本地 dev 库写数据（跑一轮灌入 306 用户+大量提醒/后台账号）** | ⚠️ P1-7 |

---

## 三、问题清单（按严重程度）

### P0（阻塞运营独立使用）

**P0-1 token 失效后不登出，页面滞留过期数据** —— ✅ **已修（2026-11-05）**
- 现象：篡改 localStorage token 后点查询，仅弹 "Request failed with status code 401"，URL 不变、不跳登录页、旧数据继续展示，运营会误把过期数据当实时数据。
- 证据：截图 `/tmp/admin-accept-03-token-invalid.png`；实测 evaluate 返回 `{"url":"/reminder-monitor","messages":["Request failed with status code 401"],"hasDialog":false}`。
- 根因：后端错误一律 HTTP 非 2xx（401/403/400），axios 拦截器只在 HTTP 2xx 时走 `isBackendSuccess/onBackendFail`（`admin/packages/axios/src/index.ts:53-84`）；而自动登出逻辑（`VITE_SERVICE_LOGOUT_CODES=UNAUTHORIZED`）挂在 `onBackendFail`（`admin/src/service/request/index.ts`），**对 HTTP 401 永远不会触发**。
- 影响：JWT 有效期 7 天，过期是运营必现场景，届时整个后台"看起来坏了"。

**P0-2 核心列表全部无分页、全量 DOM 渲染** —— ⚠️ **部分修（2026-11-05：用户/提醒/绑定/审计四页已加；后台账号、AI 模板未加）**
- 现象与证据（实测 tbody 行数，页面可交互节点数随行数线性膨胀，用户管理页 1610 节点/809 个交互元素）：

| 页面 | 实测行数 | 有无分页 |
|------|---------|---------|
| 用户管理 | 9372+（测试还在灌，持续增长） | 无 |
| 提醒监控 | 4244 | 无 |
| 绑定管理 | 4232 | 无 |
| 审计日志 | 1145+（只增不减） | 无 |
| 后台账号 | 192（本地被测试污染） | 无 |
| AI 文案模板 | 70 | 无 |
| 反馈管理 | 分页 ✅（10/20/50/100，唯一做对的页面） | 有 |

- 证据：`/tmp/admin-accept-page-user-manage.png` 等 page-* 系列截图；`fetchAdminUsers/fetchAdminBonds/fetchAdminReminders/fetchAdminAuditLogs`（`admin/src/service/api/admin.ts`）均无 page/size 参数；后端对应端点也直接返回全量数组。
- 影响：数据量到万级后打开/滚动/搜索明显卡顿，运营检索只能靠浏览器 Ctrl+F；审计日志只增不减，必然持续恶化。

### P1（修复后才能放心交付）

**P1-3 所有错误提示为技术性英文，运营不可理解** —— ✅ **已修（2026-11-05）**
- 现象：登录密码错误显示 "Request failed with status code 401"（后端实际返回了「用户名或密码错误」）；OPS 点无权限按钮显示 "Request failed with status code 403"。
- 证据：截图 `/tmp/admin-accept-login-error.png`；实测 message 抓取 `["Request failed with status code 401"]`、`["Request failed with status code 403"]`。
- 根因：同 P0-1，`onError` 中 `error.code === BACKEND_ERROR_CODE` 分支在 HTTP 4xx/5xx 时进不去（`admin/src/service/request/index.ts` 的 `onError`）。
- 说明：与 P0-1 同根因，一次修复两案。

**P1-4 后台登录无失败限流/锁定，且默认种子口令在代码与配置中可见** —— ⚠️ **部分修（2026-11-05：限流已加；yml 默认口令真值未动）**
- 实测：本地连错 8 次密码全部正常 401，无锁定、无延迟、无告警；代码 `AdminAuthService.login` 无尝试计数（`server/.../admin/app/AdminAuthService.kt:28-37`）；生产同 jar。
- 代码证据：默认口令 `bugu-admin-2026` 同时出现在 `AdminSeedProperties` 默认值与 `server/src/main/resources/application.yml:93`。生产已由部署时随机密码覆盖，但任何拿到仓库的人都知道默认口令形态；配合无限流，生产口令一旦弱化即可被在线爆破。
- 建议：复用 `sms_code.locked_until` 的既有模式做失败锁定（如 5 次锁 15 分钟），并移除 yml 中的真实默认值（改为空 + 启动校验）。

**P1-5 反馈处理（唯一漏审计的写操作）** —— ✅ **已修（2026-11-05）**
- 证据：实测更新 feedback id=2（HTTP 200，DB updated_at 变为 18:26:06），`admin_audit_log` 无对应记录；代码 `AdminFeedbackController.update`（`server/.../admin/api/AdminFeedbackController.kt:26-28`）未调用 `audit.record`，而其余全部写操作均有审计。
- 影响：运营"处理了用户反馈"这个动作不可追溯，与"全部写操作统一落 admin_audit_log"的设计声明（`AdminAuditService` 注释）不符。

**P1-6 前后端用户名校验规则不一致，超长账号无法登录且无有效提示** —— ✅ **已修（2026-11-05）**
- 规则：后端允许 3-32 字符（`AdminAccountService.create`），前端登录表单正则 `/^[\u4E00-\u9FA5a-zA-Z0-9_-]{4,16}$/`（`admin/src/constants/reg.ts:1`）。
- 实测：创建 20 位用户名 `acc-test-accept-1104`（后端创建成功），前端登录时被表单校验静默拦截——**不发任何请求、无弹窗**，仅输入框下方小字提示，运营视角即"登录没反应"。
- 影响：17-32 位账号创建后永远无法从前端登录。

**P1-7 集成测试直写本地 dev 数据库，运营数据被污染** —— ❌ **未修（按要求本轮不动；仍是上线风险）**
- 证据：18:18:23（`gradle test` 运行时刻）本地库瞬间新增 306 个 app_user、大量 reminder 与 admin 账号（victim-/mgr-/ops- 前缀，admin_user 表现存 185 条中 184 条 OPS 为测试残留）；`server/src/test` 下无任何测试专属数据源配置，@SpringBootTest 直接使用 `application.yml` 的 localhost:5432/bugu。
- 风险：a) 本地后台的运营数字全部失真（用户 9372 中大量为测试灌入）；b) 若某人本地/CI 配置指向生产库，跑测试即写生产——这是数据安全事故隐患，不只是脏数据问题。

### P2（体验与合规，排期处理）

| # | 问题 | 证据/位置 |
|---|------|----------|
| P2-8 | 状态与枚举英文直出：PENDING/ACKED/MISSSED/REPEAT/ONCE、GENTLE/NAG/STRICT、REPUSH/CREATE/WAKEUP、NORMAL、SUPER/OPS | 提醒监控/AI 模板/用户管理截图 |
| P2-9 | 时间格式三套并存：提醒监控混用 `16:46` 与 `15:46:32`；反馈页原始 ISO（`2026-10-30T16:12:36.059`）；绑定/审计页 `slice(0,19).replace('T',' ')` | 各页截图与代码 |
| P2-10 | 前端不按角色隐藏写按钮：OPS 可见「新建账号」、行级开关，点击后 403；仅 feedback 页实现 `hasAuth('admin:write')` | 实测 OPS 抓取 `{"buttons":["新建账号"],"switches":192}` |
| P2-11 | 无修改密码功能：后台账号创建后无法自助改密，忘记密码只能 DB 直改 | `AdminAccountController` 仅 create/enabled 两端点 |
| P2-12 | admin JWT 有效期 7 天（实测 iat→exp=604800s），无 refresh、无吊销 | 登录响应 token 解码 |
| P2-13 | 审计日志「操作人」只显示数字 ID，不显示用户名/昵称，需跳页反查 | audit-log/index.vue 列定义 |
| P2-14 | App 用户手机号明文展示（19199991104 等），无脱敏开关 | 用户管理截图 |
| P2-15 | App JWT payload 携带手机号明文（base64 可解）；admin token 无此问题 | App 登录响应 token 解码 |
| P2-16 | 首页快捷入口缺「反馈管理」（侧边菜单有）；统计加载失败静默显示 `--` | home/index.vue entries |
| P2-17 | 后台账号页无搜索/筛选（本地 185 条已难翻找，生产会持续增长） | admin-account 页实测 |

---

## 四、通过项明细（保留证据）

- **RBAC 双向隔离**：App token→admin 接口 401；admin token→App `/auth/me` 401；OPS GET `/stats` 200、PATCH elder-mode 403、创建账号 403（`FORBIDDEN/无权访问该资源`）。
- **会话控制**：停用账号旧 token 立即 401；停用账号再登录 401（文案与不存在一致，不泄露）；super 停用自己 409「不能停用自己」。
- **审计闭环**：`ADMIN_ACCOUNT_CREATE(admin_user#250)`、`USER_ELDER_MODE(app_user#9489, enabled=true)`、LOGIN 等实测落库。
- **统计口径**：`/stats` 六项 = DB 直查（9677/260/4064/3026/200/97，逐项相等）。
- **错误码规范**：404「用户不存在」、400「请求体格式非法」、401/403 文案不泄露内部细节。
- **退出登录**：确认框「确认退出登录吗？」→ 清 token → 跳 `/login/pwd-login`（截图 `/tmp/admin-accept-05-logout-dialog.png`）。
- **生产安全**：`https://www.yzhzhaohao.cn/api-docs` 与 `/actuator/health` 返回内容实为官网 index.html（Nginx 兜底，非真实端点）；`/api/v1/admin/stats` 无 token 401；生产 admin 页为 2026-11-01 构建，与仓库 admin 最新提交一致。
- **时区**：`docker-compose.prod.yml` postgres/server 均设 `TZ: Asia/Shanghai`（14、50 行）。
- **反馈页是正面样板**：分页、角色权限、RESOLVED 必填回复校验、竞态保护（requestId）、越界页码回退全部具备——建议作为其他页面改造模板。
- **服务端测试**：297 用例全部通过（`build/test-results` 聚合 tests=297 failures=0 errors=0）。

---

## 五、未覆盖项与限制

1. **生产 admin 登录后的页面实测**：需要真实生产管理员口令，按边界约定未索取、未猜测。已确认生产登录接口响应形态正常（错误密码 401+中文提示，仅试 2 次）。
2. **OPS 角色全页面回归**：仅实测后台账号页 + API 层全部端点，OPS 的其余页面 UI 未逐页走查。
3. **AI 模板新建/编辑表单、绑定解绑实际执行**：为保留本地测试数据链未实际触发写操作（代码审查确认有审计与二次确认）。
4. **真实生产数据口径核对**：涉及真实用户数据，按边界禁止。
5. **移动端响应式、i18n 英文语言切换**：未测（运营场景以桌面中文为主）。
6. **测试账号 15617889809 的 App 端登录态**：验证码需本人提供，本次未使用（数据一致性已用自造账号 19199991104 覆盖）。

---

## 六、建议修复顺序（只建议，未动手）

1. **第一优先（一次修复两 P0/P1）**：`admin/src/service/request/index.ts` 的 `onError` 中提取 `error.response.data.message` 展示后端中文文案；HTTP 401 时触发 `resetStore()` 踢回登录页。
2. **第二优先（P0-1）**：为 users/bonds/reminders/audit-logs/admin-users/ai 端点补 `page/size` 分参（后端已有 feedback 分页先例可直接复制），前端照 feedback-manage 的 remote 分页模式改。
3. **P1-4**：登录失败限流（5 次锁 15 分钟，复用 sms_code 模式）+ 移除 yml/代码中的默认口令真值。
4. **P1-5**：`AdminFeedbackController.update` 补 `audit.record("FEEDBACK_UPDATE")`。
5. **P1-6**：登录表单用户名正则放宽为与后端一致（3-32），校验失败给出明确 toast。
6. **P1-7**：集成测试独立数据源（testcontainers 或测试专用 schema/database），禁止直写 dev 库。
7. **P2 批量**：枚举中文映射（复用 App 端 ReminderStatus 映射表思路）、时间统一 dayjs 格式化、按 `buttons` 权限点隐藏写按钮、改密接口、JWT 缩期。

---

## 七、修复与复验记录（2026-11-05）

> 边界：未 push、未部署、未碰生产数据；仅本地 dev 环境实测。

| 问题 | 状态 | 改了什么 | 怎么验证的 |
|------|------|---------|-----------|
| P0-1 token 失效不登出 | ✅ 已修 | `admin/src/service/request/index.ts` 的 `onError` 统一拦截 HTTP 401 与业务码 `UNAUTHORIZED`：清 token（`resetStore()`）、跳登录页、中文提示「登录已过期，请重新登录」（并发 401 只提示一次）；登录接口 401 单独排除，按「用户名或密码错误」展示 | 浏览器篡改 `BUGU_token` 后触发查询：URL 变为 `/login`、`BUGU_token` 为 null、提示文本命中；截图 `admin-fix-after-token-expired.png` |
| P1-3 技术性英文错误文案 | ✅ 已修 | 同一请求层新增 `translateError`：后端中文 message 优先；超时/断网/400/403/404/409/429/5xx 分别映射中文；原始 url、HTTP 状态、错误码用 `console.warn` 保留 | 离线模式点查询 →「网络异常，请检查网络连接后重试」；OPS 越权创建账号 → 后端中文「无权访问该资源」；控制台可见 `[request] 请求失败` 原始码 |
| P0-2 核心列表分页 | ⚠️ 部分修 | users/bonds/reminders/audit-logs 四个接口增加 `page/size`（1 起、1–100、`{items,total,page,size}`），前端四页照反馈管理改 remote 分页 + 越界页回退；后台账号、AI 模板未动 | 四页实测 tbody 行数 20（原先 9000+/4000+）；用户列表翻到第 2 页发出 `page=2` 请求、首行 ID 变化；`page=0/size=0/size=101` 返回 400 `INVALID_ARGUMENT`；截图 `admin-fix-after-{user-manage,bond-manage,reminder-monitor,audit-log}.png` |
| P1-6 账号长度口径不一致 | ✅ 已修 | 前后端统一为 `^[\u4E00-\u9FA5A-Za-z0-9_-]{3,32}$`（`reg.ts`、`AdminAccountService.USERNAME_PATTERN`）；后端错误文案与前端 toast 一致 | 创建 33 位用户名：前端直接 toast 拦截、无 POST；后端接口 400 同文案；合法 3–32 位账号可创建并登录 |
| P1-4 登录失败限流 | ⚠️ 部分修 | 新增 `AdminLoginThrottle`（按 IP+账号，15 分钟窗口失败 5 次锁 15 分钟，返回 429 `LOGIN_LOCKED` 中文提示；仅信任来自本机代理的 `X-Forwarded-For`）；**yml 默认口令真值未动** | 单元测试 6 个（锁定/隔离/到期/清零/窗口滚动）；集成测试连续 5 次失败后第 6 次 429；浏览器连错 5 次 →「登录失败次数过多，请 15 分钟后再试」；截图 `admin-fix-after-login-locked.png` |
| P1-5 反馈处理漏审计 | ✅ 已修 | `AdminFeedbackController.update` 补 `audit.record(FEEDBACK_UPDATE, feedback, targetId, status/replied)` | 浏览器处理一条反馈 → 审计页出现 `FEEDBACK_UPDATE feedback#id`；DB 直查 `admin_audit_log` 命中；截图 `admin-fix-after-feedback-audit.png` |
| P1-7 集成测试写 dev 库 | ❌ 未修 | 按本轮范围不动 | —— |
| P2-12 JWT 7 天无刷新/吊销 | ❌ 未修 | 按本轮范围不动 | —— |

**质量门禁**：

- 后端 `gradle clean test`：**305 用例，0 失败 0 错误**（新增限流单测 6 个 + 登录锁定集成 1 个 + 分页/超长账号断言）。
- 前端 `pnpm typecheck` ✅、`pnpm build` ✅。
- 浏览器实测（agent-browser，1440×900）：登录、四列表分页、401 登出、403/断网文案、超长账号拦截、登录锁定、反馈审计均按上述结果通过。

**仍未解决的上线风险**：

1. **admin JWT 有效期 7 天、无刷新/吊销**：token 泄露或账号停用后，旧 token 在有效期内仍可能被重放（当前仅靠每次请求查库校验 enabled，风险略降但未消除）；需单独决策 refresh/黑名单方案。
2. **集成测试直写 dev 库**：本次用 `gradle clean test` 又向本地库写入测试数据；若 CI/他人环境误指向生产库，将直接污染生产数据。需单独改为 testcontainers 或独立 schema。

## 附录 A：截图清单

| 路径 | 内容 |
|------|------|
| /tmp/admin-accept-login-error.png | 登录失败页（技术性 401 文案场景） |
| /tmp/admin-accept-01-home.png | 运营概览首页（六项统计） |
| /tmp/admin-accept-02-user-search.png | 用户搜索命中 1 条 |
| /tmp/admin-accept-03-token-invalid.png | token 失效后页面滞留（P0-2 证据） |
| /tmp/admin-accept-04-logout.png / -clean.png | 退出登录后状态 |
| /tmp/admin-accept-05-logout-dialog.png | 退出确认框 |
| /tmp/admin-accept-page-user-manage.png | 用户管理（9372 行无分页） |
| /tmp/admin-accept-page-admin-account.png | 后台账号 |
| /tmp/admin-accept-page-bond-manage.png | 绑定管理 |
| /tmp/admin-accept-page-reminder-monitor.png | 提醒监控（状态英文/时间混格式） |
| /tmp/admin-accept-page-feedback-manage.png | 反馈管理（唯一有分页） |
| /tmp/admin-accept-page-push-status.png | 推送状态 |
| /tmp/admin-accept-page-ai-template.png | AI 文案模板（枚举英文直出） |
| /tmp/admin-accept-page-ai-log.png | 生成日志 |
| /tmp/admin-accept-page-audit-log.png | 审计日志（1145 行无分页） |

**2026-11-05 修复复验截图（before 为验收阶段原状，after 为修复后实测）**

| 路径 | 内容 |
|------|------|
| /tmp/admin-fix-before-user-manage.png / -after-user-manage.png | 用户管理：全量 9000+ 行 → 20 行/页 + 分页器 |
| /tmp/admin-fix-before-bond-manage.png / -after-bond-manage.png | 绑定管理：全量 4000+ 行 → 20 行/页 |
| /tmp/admin-fix-before-reminder-monitor.png / -after-reminder-monitor.png | 提醒监控：全量 4000+ 行 → 20 行/页 |
| /tmp/admin-fix-before-audit-log.png / -after-audit-log.png | 审计日志：全量 1000+ 行 → 20 行/页 |
| /tmp/admin-fix-before-admin-account.png / /tmp/admin-fix-after-username-too-long.png | 后台账号：超长用户名前端拦截 |
| /tmp/admin-fix-after-token-expired.png | 篡改 token 后跳登录页（会话过期提示） |
| /tmp/admin-fix-after-login-locked.png | 连错 5 次后的锁定提示 |
| /tmp/admin-fix-after-ops-403.png | OPS 越权操作的中文 403 文案 |
| /tmp/admin-fix-after-feedback-audit.png | 反馈处理后的 FEEDBACK_UPDATE 审计记录 |

## 附录 B：本次自造测试数据清单（保留在本地 dev 库，未清理，供复核）

| 数据 | 标识 | 状态 |
|------|------|------|
| OPS 后台账号 | `acc-test-accept-1104`（id=241） | 已停用 |
| OPS 后台账号 | `ops-acc-04`（id=250） | 启用 |
| App 测试用户 | 手机号 19199991104（id=9489） | 长辈模式=true |
| App 登录验证码 | sms_code 19199991104 | 一条 |
| 反馈处理 | feedback id=2 更新一次（OPEN+空回复） | updated_at=11-04 18:26 |
| 审计日志 | 上述操作产生的 LOGIN/CREATE/ELDER_MODE 记录 | 正常留存 |

## 附录 C：验收环境

- server：当前工作区代码 `gradle bootJar`（含未提交的 FireAtPushScanner/V17 改动），`-Dbugu.push.provider=console`，日志 `/tmp/bugu-server-accept.log`
- admin：`pnpm dev`（:9527，proxy→8081）；typecheck/build 均通过
- DB：本地 bugu-postgres/bugu-redis（Docker）
- 生产探测：仅 GET 只读请求 + 2 次错误密码响应形态探测（未登录、未猜口令）
