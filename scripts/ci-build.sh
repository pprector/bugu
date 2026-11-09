#!/usr/bin/env bash
#
# 无人值守构建门禁:Kuikly 模块 → AAR 落到宿主可见的本地仓库 → 编译 app 的 debug/release。
#
# 设计原则:
#   1. 任何一步失败,整体以非 0 退出;没有 `|| true`、没有静默跳过。
#   2. `--app-only` 只跳过 Kuikly 编译,并且明确打印「AAR 来自仓库内 app/repo」及其
#      sha256,让日志里能看出这一步省了什么,而不是假装全链路通过。
#   3. release 签名缺失直接失败(app/build.gradle.kts 本身也不会回退 debug 签名)。
#
# 用法:
#   ./scripts/ci-build.sh            # 全链路:编译 Kuikly → 安装 AAR → app debug+release
#   ./scripts/ci-build.sh --full     # 同上
#   ./scripts/ci-build.sh --app-only # 仅 app 构建,复用仓库内已提交的 AAR
#
# 环境变量:
#   KUIKLY_UI_DIR   KuiklyUI 工程目录(全链路模式需要)
#   APP_JAVA_HOME   app 工程使用的 JDK(默认沿用当前 JAVA_HOME)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$PROJECT_ROOT/app"
LOCAL_REPO="$APP_DIR/repo"

MODE="full"
case "${1:-}" in
  ""|--full)   MODE="full" ;;
  --app-only)  MODE="app-only" ;;
  -h|--help)   sed -n '2,24p' "${BASH_SOURCE[0]}"; exit 0 ;;
  *)           printf 'ERROR: 未知参数 %s(可用 --full / --app-only)\n' "$1" >&2; exit 2 ;;
esac

log()  { printf '\n[ci-build] %s\n' "$*"; }
fail() { printf '\n[ci-build] ERROR: %s\n' "$*" >&2; exit 1; }
sha()  { shasum -a 256 "$1" | cut -d' ' -f1; }

[[ -x "$APP_DIR/gradlew" ]] || fail "找不到 $APP_DIR/gradlew"
[[ -f "$APP_DIR/version.properties" ]] || fail "缺少 app/version.properties"

# ---- 1. Kuikly → AAR ------------------------------------------------------
if [[ "$MODE" == "full" ]]; then
  log "步骤 1/3 编译 Kuikly 模块并发布 AAR"
  "$SCRIPT_DIR/publish-kuikly-aar.sh" || fail "Kuikly → AAR 失败"
else
  log "步骤 1/3 跳过 Kuikly 编译(--app-only),复用仓库内 AAR"
  for artifact in bugu-ui bugu-entry; do
    pom="$LOCAL_REPO/com/bugu/kuikly/$artifact/0.1.0/$artifact-0.1.0.pom"
    aar="$LOCAL_REPO/com/bugu/kuikly/$artifact/0.1.0/$artifact-0.1.0.aar"
    [[ -f "$pom" ]] || fail "--app-only 需要已提交的 $pom,当前不存在"
    [[ -f "$aar" ]] || fail "--app-only 需要已提交的 $aar,当前不存在"
    log "  复用 $artifact-0.1.0.aar sha256=$(sha "$aar")"
  done
fi

# ---- 2. 校验本地仓库完整 --------------------------------------------------
log "步骤 2/3 校验本地 Maven 仓库依赖坐标"
for artifact in bugu-ui bugu-entry; do
  [[ -f "$LOCAL_REPO/com/bugu/kuikly/$artifact/0.1.0/$artifact-0.1.0.pom" ]] \
    || fail "本地仓库缺少 $artifact 的 POM"
done
for coord in core compose core-annotations core-render-android; do
  [[ -f "$LOCAL_REPO/com/tencent/kuikly-open/$coord/2.1.21-local/$coord-2.1.21-local.pom" ]] \
    || fail "本地仓库缺少 com.tencent.kuikly-open:$coord:2.1.21-local"
done

# ---- 3. app debug + release ----------------------------------------------
log "步骤 3/3 编译 app(assembleDebug + assembleRelease)"
if [[ -n "${APP_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$APP_JAVA_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
log "JAVA_HOME=${JAVA_HOME:-<继承当前环境>}"

# release 签名检查提前做,避免编译几分钟后才因签名缺失失败
if [[ -f "$APP_DIR/local.properties" ]]; then
  for key in bugu.release.storeFile bugu.release.storePassword bugu.release.keyAlias bugu.release.keyPassword; do
    grep -q "^$key=." "$APP_DIR/local.properties" || fail "app/local.properties 缺少 $key,无法构建 release(不会回退 debug 签名)"
  done
  store_file="$(grep '^bugu.release.storeFile=' "$APP_DIR/local.properties" | cut -d= -f2-)"
  if [[ ! -f "$store_file" && ! -f "$APP_DIR/$store_file" ]]; then
    fail "release keystore 不存在: $store_file"
  fi
else
  fail "缺少 app/local.properties(release 签名必需),不会回退 debug 签名"
fi

(
  cd "$APP_DIR"
  ./gradlew --console=plain --stacktrace assembleDebug assembleRelease
) || fail "app 构建失败"

# ---- 产物清单 -------------------------------------------------------------
log "构建产物"
found=0
for apk in "$APP_DIR"/build/outputs/apk/debug/*.apk "$APP_DIR"/build/outputs/apk/release/*.apk; do
  [[ -f "$apk" ]] || continue
  found=1
  printf '  %s (%s bytes, sha256=%s)\n' "$apk" "$(wc -c < "$apk" | tr -d ' ')" "$(sha "$apk")"
done
[[ "$found" == "1" ]] || fail "构建成功但没有找到 APK 产物"

log "全部通过"
