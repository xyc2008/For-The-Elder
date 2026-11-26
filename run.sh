#!/usr/bin/env bash
# 常伴（For-The-Elder）一键运行脚本：构建 → 装到设备 → 启动 → 回显读数。
#
# 为什么要有它：README 里那三行（assembleDebug / adb install / am start）是手工粘的，
# 每换一台机器都要改 SDK 路径与包名（debug 变体的 applicationId 带 `.debug` 后缀，见
# app/build.gradle.kts 的 applicationIdSuffix）。本脚本把「找 SDK、找 adb、找 gradlew、
# 从 APK 里读出包名与启动组件」全部当场算出来，一个都不写死，找不到就报错停下。
#
# 用法：
#   ./run.sh                 构建 → 装到第一个已连接设备 → 启动
#   ./run.sh --emulator      没有已连接设备时自动开模拟器（默认 AVD：Elder_API35）
#   ./run.sh --no-build      跳过构建，直接用现有的 app-debug.apk
#   ./run.sh --reinstall     启动前先卸载同名包（清掉上一次的数据）
#   ./run.sh --shot          启动后截一张图存到 build/run-shot.png
#   ./run.sh --logcat        启动后跟随 logcat（Ctrl-C 退出）
#   ./run.sh --log FILE      把整次运行的完整输出（含参数、时间、读数）写进 FILE，同时照常打印到屏幕
#                            （入库留档的「运行证据」日志就出自这里，用例见 records/run-evidence/；
#                             相对路径按仓库根解析）
#   ./run.sh -h | --help     看这段说明
#
# 环境变量（都可选）：AVD 覆盖 AVD 名；ANDROID_SDK / ANDROID_HOME 覆盖 SDK 位置。
set -euo pipefail

# Windows 上 adb.exe / aapt.exe 是原生程序，Git Bash 默认会把 /c/... 这类参数改写成 C:/...
# 连「设备上的路径」一起改写（screencap 的 /data/local/tmp/run-shot.png 会被改写坏），
# 所以这里关掉自动改写，本地路径统一由 to_win 显式转成 Windows 形式再交给它们。
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
# 原始参数：下面的 while 循环会把 $@ 一路 shift 光，而日志 wrapper 还要把原样参数转发给子进程，
# 所以在这里先留一份（ARGS_STR 给日志头显示，ORIG_ARGS 给子进程原样转发）
ARGS_STR="$*"
ORIG_ARGS=("$@")

AVD_NAME="${AVD:-Elder_API35}"
BOOT_EMULATOR=0
DO_BUILD=1
DO_REINSTALL=0
DO_SHOT=0
FOLLOW_LOGCAT=0
LOG_PATH=""

usage() {
    awk 'NR == 1 { next } /^#/ { print; next } { exit }' "$0" | sed 's/^# \{0,1\}//'
    exit 0
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --emulator) BOOT_EMULATOR=1 ;;
        --no-build) DO_BUILD=0 ;;
        --reinstall) DO_REINSTALL=1 ;;
        --shot) DO_SHOT=1 ;;
        --logcat) FOLLOW_LOGCAT=1 ;;
        --log) shift; LOG_PATH="${1:-}" ;;
        --avd) shift; AVD_NAME="${1:-}" ;;
        -h | --help) usage ;;
        *) echo "不认识的参数：$1（用 ./run.sh --help 看用法）" >&2; exit 2 ;;
    esac
    shift
done

say() { printf '[run] %s\n' "$*"; }
die() { printf '[run] 失败：%s\n' "$*" >&2; exit 1; }

# ---- 0. 日志（--log）：自己再起一个自己，把子进程的全部输出直接重定向进文件，跑完再 cat 回来 ----
# 为什么不用「进程替换 + tee」把输出分到两处：进程替换在 Git Bash 上收尾有 flush 竞态，日志可能被
# 截断 —— 那本身就会造出「看起来完整、其实缺尾巴」的假证据；重定向给子进程、跑完再 cat 就没这竞态。
if [ -n "$LOG_PATH" ] && [ -z "${FTE_RUN_LOGGED:-}" ]; then
    case "$LOG_PATH" in /* | [A-Za-z]:*) ;; *) LOG_PATH="$ROOT/$LOG_PATH" ;; esac
    mkdir -p "$(dirname "$LOG_PATH")"
    set +e
    FTE_RUN_LOGGED=1 bash "$ROOT/$(basename "$0")" "${ORIG_ARGS[@]}" >"$LOG_PATH" 2>&1
    rc=$?
    set -e
    cat "$LOG_PATH"
    printf '[run] 日志 %s（退出码 %s）\n' "$LOG_PATH" "$rc"
    exit "$rc"
fi
# 被 --log 起起来的那个子进程：把「时间 / 命令 / 主机」也记进日志头，免得事后不知道这份日志出自谁
if [ -n "${FTE_RUN_LOGGED:-}" ]; then
    say "时间 $(date '+%Y-%m-%d %H:%M:%S %z')"
    say "命令 $(basename "$0")${ARGS_STR:+ $ARGS_STR}"
    say "主机 $(uname -s) $(uname -r) / bash ${BASH_VERSION%%(*}"
fi

EXE=""
case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) EXE=".exe" ;;
esac

# 本地路径要交给原生 Windows 程序时先转成 C:\... 形式（非 Windows 上原样返回）
to_win() {
    if [ -n "$EXE" ] && command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s\n' "$1"
    fi
}

# ---- 1. 找 SDK ----
SDK=""
for c in "${ANDROID_SDK:-}" "${ANDROID_HOME:-}" "$ROOT/.toolchain/android-sdk"; do
    if [ -n "$c" ] && [ -d "$c/platform-tools" ]; then SDK="$c"; break; fi
done
if [ -z "$SDK" ] && [ -f "$ROOT/local.properties" ]; then
    c="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | tr -d '\r' | head -1)"
    [ -n "$c" ] && [ -d "$c/platform-tools" ] && SDK="$c"
fi
[ -n "$SDK" ] || die "找不到 Android SDK：设 ANDROID_SDK，或让 local.properties 里有 sdk.dir"
ADB="$SDK/platform-tools/adb$EXE"
[ -x "$ADB" ] || [ -f "$ADB" ] || die "$ADB 不存在"
say "SDK  $SDK"

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

# ---- 2. 构建 ----
if [ "$DO_BUILD" -eq 1 ]; then
    [ -x "$ROOT/gradlew" ] || die "gradlew 不存在或不可执行"
    say "构建 :app:assembleDebug（跳过构建用 --no-build）"
    "$ROOT/gradlew" --console=plain :app:assembleDebug
fi
[ -f "$APK" ] || die "没有 $APK：先去掉 --no-build 构建一次"

# APK 的字节数与 md5 是每轮留档的读数，顺手打出来
if command -v md5sum >/dev/null 2>&1; then
    APK_MD5="$(md5sum "$APK" | awk '{print $1}')"
elif command -v md5 >/dev/null 2>&1; then
    APK_MD5="$(md5 -q "$APK")"
else
    APK_MD5="n/a"
fi
if stat -c%s "$APK" >/dev/null 2>&1; then
    APK_BYTES="$(stat -c%s "$APK")"
else
    APK_BYTES="$(stat -f%z "$APK")"
fi
say "APK  $APK（$APK_BYTES 字节，md5 $APK_MD5）"

# ---- 3. 从 APK 里读出包名（不写死 .debug 后缀） ----
PKG=""
AAPT="$(ls -1 "$SDK"/build-tools/*/aapt$EXE 2>/dev/null | sort -V | tail -1 || true)"
if [ -n "$AAPT" ] && [ -f "$AAPT" ]; then
    PKG="$("$AAPT" dump badging "$(to_win "$APK")" 2>/dev/null \
        | tr -d '\r' | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1 || true)"
fi
[ -n "$PKG" ] || die "读不出 APK 的包名（aapt：${AAPT:-没找到 $SDK/build-tools/*/aapt$EXE}）"
say "包名 $PKG"

# ---- 4. 找设备（没有就按需开模拟器） ----
"$ADB" start-server >/dev/null 2>&1 || true
DEV=""
list_devices() { "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'; }
DEV="$(list_devices | head -1 || true)"

if [ -z "$DEV" ] && [ "$BOOT_EMULATOR" -eq 1 ]; then
    EMU="$SDK/emulator/emulator$EXE"
    [ -f "$EMU" ] || die "没有已连接设备，而 $EMU 也不存在"
    mkdir -p "$ROOT/build"
    ELOG="$ROOT/build/run-emulator.log"
    say "开模拟器 $AVD_NAME（日志 $ELOG）"
    "$EMU" -avd "$AVD_NAME" -no-audio -no-boot-anim -no-snapshot \
        -gpu swiftshader_indirect >"$ELOG" 2>&1 &
    echo $! >"$ROOT/build/run-emulator.pid"
    "$ADB" wait-for-device
    waited=0
    until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 3
        waited=$((waited + 3))
        [ "$waited" -ge 240 ] && die "模拟器 240 秒内没开机完成，看 $ELOG"
    done
    DEV="$(list_devices | head -1 || true)"
fi

[ -n "$DEV" ] || die "没有已连接设备（开模拟器加 --emulator，或先手动起一个）"
say "设备 $DEV"

# ---- 5. 装 ----
if [ "$DO_REINSTALL" -eq 1 ]; then
    say "先卸载 $PKG"
    "$ADB" -s "$DEV" uninstall "$PKG" >/dev/null 2>&1 || say "（本来就没装，跳过）"
fi
say "安装"
INSTALL_OUT="$("$ADB" -s "$DEV" install -r "$(to_win "$APK")" 2>&1 | tr -d '\r' || true)"
printf '%s\n' "$INSTALL_OUT" | sed 's/^/      /'
case "$INSTALL_OUT" in
    *Success*) ;;
    *) die "安装没成功：$INSTALL_OUT" ;;
esac

# ---- 6. 启动 ----
COMP="$("$ADB" -s "$DEV" shell cmd package resolve-activity --brief "$PKG" 2>/dev/null \
    | tr -d '\r' | sed -n '/\//p' | tail -1 || true)"
[ -n "$COMP" ] || die "解不出 $PKG 的启动组件：装上了但没有 launcher activity？"
say "启动 $COMP"
"$ADB" -s "$DEV" shell am start -W -n "$COMP" 2>&1 | tr -d '\r' | sed 's/^/      /'
PID="$("$ADB" -s "$DEV" shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
say "进程 pid ${PID:-（没读出来）}"

# ---- 7. 可选的截图 / logcat ----
if [ "$DO_SHOT" -eq 1 ]; then
    mkdir -p "$ROOT/build"
    SHOT="$ROOT/build/run-shot.png"
    "$ADB" -s "$DEV" shell screencap -p /data/local/tmp/run-shot.png
    "$ADB" -s "$DEV" pull /data/local/tmp/run-shot.png "$(to_win "$SHOT")" >/dev/null
    "$ADB" -s "$DEV" shell rm -f /data/local/tmp/run-shot.png
    say "截图 $SHOT"
fi
if [ "$FOLLOW_LOGCAT" -eq 1 ]; then
    say "跟随 logcat（Ctrl-C 退出）"
    exec "$ADB" -s "$DEV" logcat -v brief
fi

say "完成：$PKG 已在 $DEV 上跑起来"
