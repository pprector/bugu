# 技术债清单

记录已知但暂未处理的技术债。每条包含现状、成因、为什么是债，以及候选方案；未定方案的不自行推进。

## TD-001 `app/repo/` 内的本地 Maven 二进制（AAR）

- **状态**：未决定，等产品选方案
- **记录日期**：2026-11-09

**现状**

- `app/repo/` 下有 12 个文件、约 9.5MB，全部是 Maven 坐标形式的二进制与 POM；
- 其中 `com.bugu.kuikly:bugu-ui:0.1.0` 的 AAR 约 1.2MB，是 Kuikly 页面（看板、我的、陪伴、权限引导等）的实际代码；
- 该目录同时存放 `com.tencent.kuikly-open:*` 的 core/compose AAR。

**为什么必须入库**

KuiklyUI 工程不在本仓库内（`~/Documents/phk/kuikly-poc/KuiklyUI`），App 模块只能通过 `app/repo` 这个本地 Maven 仓库解析
`bugu-ui` / `bugu-entry` 依赖；不提交这 12 个文件，其他人拉下代码就无法构建 App。

**为什么是债**

- 二进制无法 diff、无法 review，UI 改动在提交里只表现为「AAR 变了」，看不到实际代码改动；
- 每次 Kuikly UI 改动都要重新构建并覆盖一次 AAR，漏覆盖就会「代码改了但 APK 没变」；
- 多人同时改 UI 时，AAR 必然冲突，且二进制冲突无法手工合并；
- 仓库体积随每次 UI 迭代单调增长，旧版本 AAR 永久留在 git 历史里。

**候选方案（二选一，尚未拍板）**

1. 发私有 Maven 仓库（如公司 Nexus / GitHub Packages / 云厂商制品库）：App 改为从远端仓库拉依赖，
   仓库里只保留版本号；需要解决制品上传权限与 CI 发布流程。
2. 把 KuiklyUI 并入本仓库（子树或 git submodule）：UI 代码回到可 review 的状态，
   代价是仓库变大、构建链需要统一，且 submodule 对日常提交有额外心智负担。

**决策前的临时约定**

- 覆盖 AAR 的提交必须在 message 中写明包含哪些页面改动（参考 `b4ae080`）；
- 修改 Kuikly 页面后必须重新执行 `scripts/publish-kuikly-aar.sh`，再构建 App 验证。
