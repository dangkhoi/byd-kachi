#!/usr/bin/env bash
# ═══ BUILD HAL HELPER → app/src/main/assets/kachi_hal.jar ════════════════════════════════════════════════════
#
# Biên dịch `hal-helper/KachiHalMain.java` thành **dex trong jar**, chạy được bằng:
#   CLASSPATH=/data/local/tmp/kachi_hal.jar app_process / com.byd.clusternav.hal.KachiHalMain 19322
#
# Vì sao là script rời chứ không phải module Gradle: helper chạy dưới `app_process` NGOÀI app, không được mang
# kotlin-stdlib hay lớp nào của Kachi. Một module Gradle sẽ kéo AGP/Kotlin vào và làm mờ đúng ranh giới đó.
# Tiền lệ trong repo: `assets/navopen.jar` cũng là dex-in-jar sinh ngoài Gradle.
#
# ⚠ Stub `hal-helper/stubs/**` CHỈ dùng lúc biên dịch (d8 nhận qua `--classpath` ⇒ KHÔNG nhúng vào dex). Lúc
#   chạy, dex nạp lớp THẬT từ framework của xe. Xem KDoc trong stub về lý do phải khai đủ 8 phương thức.
#
# Chạy: scripts/build-hal-helper.sh   (không tham số)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO/hal-helper"
OUT_JAR="$REPO/app/src/main/assets/kachi_hal.jar"
MAIN_CLASS="com.byd.clusternav.hal.KachiHalMain"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BUILD_TOOLS_VER="${BUILD_TOOLS_VER:-36.0.0}"
D8="$SDK/build-tools/$BUILD_TOOLS_VER/d8"
JAVA_HOME_DEFAULT="/opt/homebrew/opt/openjdk@17"
JHOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
JAVAC="$JHOME/bin/javac"

# android.jar: lấy platform CAO NHẤT có sẵn (helper chỉ dùng API nền tảng rất cũ, nên bản nào cũng đủ).
ANDROID_JAR="${ANDROID_JAR:-}"
if [[ -z "$ANDROID_JAR" ]]; then
  ANDROID_JAR="$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)"
fi

die() { echo "ERROR: $*" >&2; exit 1; }

# d8/jar là script wrapper gọi `java` từ PATH/JAVA_HOME. Máy này không có system java ⇒ phải trỏ tường minh,
# không thì d8 ngã "Unable to locate a Java Runtime" NGAY SAU khi javac đã chạy xong (rất dễ đọc thành lỗi javac).
export JAVA_HOME="$JHOME"
export PATH="$JHOME/bin:$PATH"

[[ -x "$D8" ]]        || die "không thấy d8 tại $D8 (đặt BUILD_TOOLS_VER=<ver>)"
[[ -x "$JAVAC" ]]     || die "không thấy javac tại $JAVAC (đặt JAVA_HOME=...)"
[[ -f "$ANDROID_JAR" ]] || die "không thấy android.jar trong $SDK/platforms (đặt ANDROID_JAR=...)"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
CLASSES="$WORK/classes"      # class của helper  → VÀO dex
STUBS="$WORK/stubs"          # class của stub    → KHÔNG vào dex
mkdir -p "$CLASSES" "$STUBS"

echo "▸ javac stub (chỉ để biên dịch, không đóng gói)"
"$JAVAC" -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
  -d "$STUBS" $(find "$SRC/stubs" -name '*.java')

echo "▸ javac helper"
"$JAVAC" -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
  -cp "$STUBS" -d "$CLASSES" "$SRC/KachiHalMain.java"

echo "▸ d8 → dex-in-jar"
# Chỉ truyền class của HELPER làm đầu vào; stub đi qua --classpath nên không bị nhúng.
# `--output <file>.jar` cho d8 tự ghi archive chứa `classes.dex` ở gốc — đúng dạng kinex dùng
# ([ĐO] kinex_hal_api_service.jar có đúng MỘT entry `classes.dex`, không META-INF) và bỏ được bước `jar`.
mkdir -p "$(dirname "$OUT_JAR")"
rm -f "$OUT_JAR"
"$D8" --min-api 29 --output "$OUT_JAR" \
  --lib "$ANDROID_JAR" --classpath "$STUBS" \
  $(find "$CLASSES" -name '*.class')

[[ -f "$OUT_JAR" ]] || die "d8 không sinh ra $OUT_JAR"

# ── Verify: jar phải có classes.dex, và dex phải chứa ĐÚNG lớp main, KHÔNG chứa stub ────────────────────────
echo "▸ verify"
unzip -l "$OUT_JAR" | grep -q 'classes\.dex' || die "jar thiếu classes.dex"

DEX="$WORK/verify.dex"
unzip -p "$OUT_JAR" classes.dex > "$DEX"

MAIN_DESC="L${MAIN_CLASS//./\/};"
grep -q "$MAIN_DESC" "$DEX" || die "dex không chứa $MAIN_CLASS"

# Stub PHẢI vắng mặt như một định nghĩa lớp. Chuỗi tên lớp vẫn xuất hiện (helper tham chiếu nó qua
# Class.forName + extends) nên không thể grep tên; kiểm bằng cách đếm định nghĩa lớp mà d8 đã nhận vào.
CLASS_COUNT="$(find "$CLASSES" -name '*.class' | wc -l | tr -d ' ')"
echo "  · lớp đóng vào dex: $CLASS_COUNT (helper + inner)"
echo "  · $(unzip -l "$OUT_JAR" | awk '/classes\.dex/{print $1" bytes dex"}')"
echo "  · jar: $(cd "$REPO" && ls -l "${OUT_JAR#$REPO/}" | awk '{print $5" bytes"}')"

echo "✅ $OUT_JAR"
echo "   chạy trên xe: CLASSPATH=/data/local/tmp/kachi_hal.jar app_process / $MAIN_CLASS 19322"
