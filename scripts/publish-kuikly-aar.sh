#!/usr/bin/env bash
#
# 构建 Kuikly 迁移的两个 AAR(bugu-ui / bugu-entry),连同渲染好的 POM 一起
# 安装到宿主可见的本地 Maven 仓库 app/repo。
#
# 为什么需要这个脚本:
#   KuiklyUI 工程不在本仓库内,app 依赖的是已构建的 AAR。之前 POM 靠手工编写,
#   漏了 kotlinx-datetime 这类传递依赖,`--refresh-dependencies` 时就会解析失败。
#   这里把「构建 → 复制 AAR → 渲染 POM → 校验依赖坐标」固化下来,
#   任何一步失败都直接退出非 0,不留静默成功。
#
# 用法:
#   ./scripts/publish-kuikly-aar.sh
#
# 可用环境变量覆盖:
#   KUIKLY_UI_DIR        KuiklyUI 工程目录
#   BUGU_UI_VERSION      AAR 版本,默认 0.1.0
#   KUIKLY_CORE_VERSION  com.tencent.kuikly-open 版本,默认 2.1.21-local
#   KOTLINX_DATETIME_VERSION  默认 0.6.2
#   GRADLE_EXTRA_ARGS    透传给 KuiklyUI 的 gradle 参数(如 --offline)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$PROJECT_ROOT/app"
LOCAL_REPO="$APP_DIR/repo"
TEMPLATE_DIR="$SCRIPT_DIR/kuikly-pom"

KUIKLY_UI_DIR="${KUIKLY_UI_DIR:-$HOME/Documents/phk/kuikly-poc/KuiklyUI}"
BUGU_UI_VERSION="${BUGU_UI_VERSION:-0.1.0}"
KUIKLY_CORE_VERSION="${KUIKLY_CORE_VERSION:-2.1.21-local}"
KOTLINX_DATETIME_VERSION="${KOTLINX_DATETIME_VERSION:-0.6.2}"
GRADLE_EXTRA_ARGS="${GRADLE_EXTRA_ARGS:-}"

# core 版本形如 <kuiklyVersion>-<kotlinVersion>,拆回去喂给 KuiklyUI 的构建脚本
KUIKLY_VERSION="${KUIKLY_VERSION:-${KUIKLY_CORE_VERSION%-*}}"
KUIKLY_KOTLIN_VERSION="${KUIKLY_KOTLIN_VERSION:-${KUIKLY_CORE_VERSION##*-}}"

log()  { printf '[publish-kuikly-aar] %s\n' "$*"; }
fail() { printf '[publish-kuikly-aar] ERROR: %s\n' "$*" >&2; exit 1; }

# KuiklyUI 是 Gradle 7.6.3 + buildSrc 里的 Kotlin 1.7.10,在 JDK 25 下会因
# JavaVersion.parse("25.0.4.1") 抛 IllegalArgumentException,必须用 JDK 17。
is_jdk17() { [[ -x "$1/bin/java" ]] && "$1/bin/java" -version 2>&1 | head -1 | grep -q '"17\.'; }

resolve_jdk17() {
  local candidate
  if [[ -n "${KUIKLY_JAVA_HOME:-}" ]] && is_jdk17 "$KUIKLY_JAVA_HOME"; then
    echo "$KUIKLY_JAVA_HOME"; return 0
  fi
  for candidate in "$KUIKLY_UI_DIR"/../toolchain/jdk-17*/Contents/Home \
                   "$KUIKLY_UI_DIR"/../toolchain/jdk-17* \
                   /Library/Java/JavaVirtualMachines/*/Contents/Home; do
    if is_jdk17 "$candidate"; then
      echo "$candidate"; return 0
    fi
  done
  if [[ -n "${JAVA_HOME:-}" ]] && is_jdk17 "$JAVA_HOME"; then
    echo "$JAVA_HOME"; return 0
  fi
  return 1
}

[[ -d "$KUIKLY_UI_DIR" ]] || fail "找不到 KuiklyUI 工程: $KUIKLY_UI_DIR (可用 KUIKLY_UI_DIR 覆盖)"
[[ -x "$KUIKLY_UI_DIR/gradlew" ]] || fail "找不到 $KUIKLY_UI_DIR/gradlew"
[[ -d "$TEMPLATE_DIR" ]] || fail "找不到 POM 模板目录: $TEMPLATE_DIR"
command -v python3 >/dev/null 2>&1 || fail "需要 python3 来校验 POM 依赖坐标"

render_pom() {
  local tpl="$1" out="$2"
  sed \
    -e "s|\${BUGU_UI_VERSION}|$BUGU_UI_VERSION|g" \
    -e "s|\${KUIKLY_CORE_VERSION}|$KUIKLY_CORE_VERSION|g" \
    -e "s|\${KOTLINX_DATETIME_VERSION}|$KOTLINX_DATETIME_VERSION|g" \
    "$tpl" > "$out"

  if grep -q '\${' "$out"; then
    fail "POM 里还有未替换的占位符: $out"
  fi
}

# 校验渲染出的 POM:本仓库内的依赖必须真实存在于 app/repo,否则宿主解析必挂
check_pom_dependencies() {
  local pom="$1"
  python3 - "$pom" "$LOCAL_REPO" <<'PY'
import sys, os
import xml.etree.ElementTree as ET

pom_path, local_repo = sys.argv[1], sys.argv[2]
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(pom_path).getroot()
local_groups = ("com.bugu.kuikly", "com.tencent.kuikly-open")
missing = []
for dep in root.findall("m:dependencies/m:dependency", ns):
    g = dep.findtext("m:groupId", default="", namespaces=ns)
    a = dep.findtext("m:artifactId", default="", namespaces=ns)
    v = dep.findtext("m:version", default="", namespaces=ns)
    if not (g, a, v):
        print(f"ERROR: {pom_path} 有空依赖坐标")
        sys.exit(1)
    if g in local_groups:
        p = os.path.join(local_repo, g.replace(".", "/"), a, v, f"{a}-{v}.pom")
        if not os.path.isfile(p):
            missing.append(f"{g}:{a}:{v} -> 缺少 {p}")
if missing:
    print("ERROR: 本地仓库缺少以下依赖(先安装它们,或修正模板):")
    for m in missing:
        print("  " + m)
    sys.exit(1)
print(f"  POM 依赖坐标校验通过: {os.path.basename(pom_path)}")
PY
}

install_module() {
  local artifact="$1"
  local aar_src="$KUIKLY_UI_DIR/$artifact/build/outputs/aar/$artifact-release.aar"
  local dest_dir="$LOCAL_REPO/com/bugu/kuikly/$artifact/$BUGU_UI_VERSION"
  local aar_dest="$dest_dir/$artifact-$BUGU_UI_VERSION.aar"
  local pom_dest="$dest_dir/$artifact-$BUGU_UI_VERSION.pom"

  [[ -f "$aar_src" ]] || fail "构建产物不存在: $aar_src"

  mkdir -p "$dest_dir"
  cp -f "$aar_src" "$aar_dest"
  render_pom "$TEMPLATE_DIR/$artifact.pom.tpl" "$pom_dest"
  check_pom_dependencies "$pom_dest"

  log "已安装 $artifact:"
  log "  $aar_dest ($(wc -c < "$aar_dest" | tr -d ' ') bytes, sha256=$(shasum -a 256 "$aar_dest" | cut -d' ' -f1))"
  log "  $pom_dest"
}

log "KuiklyUI : $KUIKLY_UI_DIR"
log "本地仓库 : $LOCAL_REPO"
log "版本     : bugu=$BUGU_UI_VERSION core=$KUIKLY_CORE_VERSION (KUIKLY_VERSION=$KUIKLY_VERSION KUIKLY_KOTLIN_VERSION=$KUIKLY_KOTLIN_VERSION)"

log "步骤 1/2 编译 Kuikly 模块"
KUIKLY_JAVA_HOME="$(resolve_jdk17)" || fail "找不到 JDK 17(KuiklyUI 的 Gradle 7.6.3 + Kotlin 1.7.10 buildSrc 无法在 JDK 18+ 上运行),可用 KUIKLY_JAVA_HOME 指定"
log "JDK      : $KUIKLY_JAVA_HOME"
(
  cd "$KUIKLY_UI_DIR"
  JAVA_HOME="$KUIKLY_JAVA_HOME" \
  PATH="$KUIKLY_JAVA_HOME/bin:$PATH" \
  KUIKLY_VERSION="$KUIKLY_VERSION" \
  KUIKLY_KOTLIN_VERSION="$KUIKLY_KOTLIN_VERSION" \
  ./gradlew --console=plain :bugu-ui:assembleRelease :bugu-entry:assembleRelease $GRADLE_EXTRA_ARGS
) || fail "Kuikly 模块构建失败"

log "步骤 2/2 安装 AAR + 生成 POM"
install_module bugu-ui
install_module bugu-entry

log "完成。宿主刷新依赖: cd app && ./gradlew assembleDebug --refresh-dependencies"
