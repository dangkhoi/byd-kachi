#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════════════════════════════
# cluster-now-playing.sh — KỊCH BẢN XE cho CLUSTER-NOW-PLAYING (tên bài YouTube / YT Music lên cụm)
#
# ⚠ 3.1.12: ĐÃ VƯỢT QUA. [ĐO xe 17/10] `com.byd.mediacontroller` tự ghi tên bài vào 0x43FB1008 (MEDIA_SOURCE=999);
#   ô nhạc gốc chỉ bị mặt chiếu Kachi che ⇒ Kachi vẽ dải tên bài trên mặt chiếu (doc §9). W1 (ghi nguồn 11) KHÔNG cần
#   nữa; giữ kịch bản để tra cứu phép đo M0–M6 (chỉ đọc). W2 mặc định bỏ qua.
#
# Cơ chế + id + M0–M6/W1: docs/diagnostics/cluster-now-playing-2026-10-16.md (§5 lệnh, §7 kịch bản này).
# Chạy với Kachi 3.1.10 (215) ĐÃ PHÁT HÀNH — KHÔNG cần APK mới cho M0–M6 + W1 + W1b.
#   • Đọc: adb shell (getprop/dumpsys/logcat) + cầu kiểm thử `hal getid` (chỉ đọc).
#   • Ghi (W1): cầu kiểm thử `hal setev` — feature INT trên device instrument: nguồn 0x33F00030 = 11,
#     trạng thái 0x43E0000A = 1. Cổng `--ez auto_confirm true`. Cầu 3.1.10 không ghi được byte[] ⇒ chữ tên
#     bài là do CHÍNH MediaController của BYD gửi khi đổi bài (W1b). Không có bước ghi tên bài cố định.
#   • W2 (tính năng Kachi mới) — tuỳ chọn, tự bỏ qua nếu bản cài < 3.1.11 (versionCode < 216) hoặc
#     KACHI_NP_CMD chưa đặt.
#
# DÙNG:   scripts/oncar/cluster-now-playing.sh [<adb-serial>] [<thư-mục-ra>]
#         serial mặc định 127.0.0.1:15555 (cầu nc — docs/diagnostics/adb-car-tunnel-macos.md)
#         thư mục ra mặc định docs/diagnostics/oncar-cluster-np-<YYYYmmdd-HHMMSS>/
# Biến:   ADB=<đường adb> · FORCE_W1=1 (cho W1 chạy dù M1 chưa xanh — KHÔNG khuyên)
#         KACHI_NP_CMD / KACHI_NP_OFF_CMD (W2) — xem doc §7.5
#
# LUẬT (CLAUDE.md §4, memory "thấy sập lần đầu là dừng"):
#   • Lệnh đổi state DUY NHẤT: feature-id nhạc trên device instrument. Không am start/wm, không display, không stack.
#   • Hoàn tác: nguồn = 26 (đúng giá trị MediaController đang nhớ) — trap EXIT luôn chạy, rồi đọc lại.
#   • Bất thường đầu tiên (SurfaceFlinger/system_server đổi pid, Kachi chết/crash, cụm trống) ⇒ hoàn tác + thoát.
# macOS bash 3.2: không mảng kết hợp, không `timeout`, không vòng sleep.
# ═════════════════════════════════════════════════════════════════════════════════════════════════
set -uo pipefail

SERIAL="${1:-127.0.0.1:15555}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${2:-$ROOT/docs/diagnostics/oncar-cluster-np-$(date +%Y%m%d-%H%M%S)}"
ADB="${ADB:-adb}"

PKG="com.byd.launcher"
MC_PKG="com.byd.mediacontroller"
COMP="$PKG/com.byd.clusternav.launcher.testbridge.KachiTestBridge"
ACTION="$PKG.TEST"
DEV="BYDAutoInstrumentDevice"

# Feature-id (doc §1(3)) — thập phân.
ID_SRC_SET=871366704      # 0x33F00030 INSTRUMENT_MUSIC_SOURCE_SET
ID_STATE_SET=1138753546   # 0x43E0000A INSTRUMENT_MUSIC_STATE_SET (1 phát · 2 dừng · 3 stop)
ID_TITLE_SET=1140527112   # 0x43FB1008 INSTRUMENT_MUSIC_INFO_SET (buffer UTF-16LE) — chỉ ĐỌC ở đây
ID_SRC_READ=970981412     # 0x39E00024 INSTRUMENT_MUSIC_SOURCE (đọc, ý nghĩa CHƯA BIẾT)
ID_INFO_RESULT=1108021264 # 0x420B1010 INSTRUMENT_MUSIC_INFO_RESULT (đọc)
SRC_TEST=11               # MUSIC_SOURCE_OTHERS — trong tập cụm cho hiện, icon mediaIcon14
SRC_RESTORE=26            # giá trị MediaController gán cho app lạ (OtherClient.java:168-188)

YTM_PKG="com.google.android.apps.youtube.music"
YT_PKG="com.google.android.youtube"
W2_MIN_CODE="${W2_MIN_CODE:-216}"   # 3.1.11

# ── W2: lệnh cầu của tính năng Kachi mới ────────────────────────────────────────────────────────
# TODO(CLUSTER-NOW-PLAYING): agent tính năng 3.1.11 điền tên công tắc/lệnh vào doc §7.5 rồi đặt ở đây (hoặc
#   truyền qua biến môi trường). Dạng = các extra SAU `am broadcast -n … -a …`, ví dụ:
#     KACHI_NP_CMD='--es cmd prefs_set --es key <khoá-công-tắc> --es text true'
#     KACHI_NP_OFF_CMD='--es cmd prefs_set --es key <khoá-công-tắc> --es text false'
#   Rỗng ⇒ W2 tự bỏ qua.
#   3.1.11 (216): ĐÃ ĐIỀN — lệnh cầu `np` (doc §8): `on` gắn bộ nghe phiên NGAY (không chờ khởi động lại), cần auto_confirm;
#   `off` trả nguồn cụm + gỡ bộ nghe. Đọc tình trạng: `--es cmd np` (op vắng = status); dòng ghi HAL: `--es cmd ctllog`.
#   3.1.12 (217): bộ ghi nguồn 11 + cầu `np` ĐÃ GỠ (doc §9 — [ĐO 17/10] MediaController tự gửi tên bài, ô nhạc chỉ bị mặt chiếu
#   che) ⇒ W2 mặc định RỖNG (tự bỏ qua). Chỉ đặt biến khi đo lại một bản ≤ 3.1.11.
KACHI_NP_CMD="${KACHI_NP_CMD:-}"
KACHI_NP_OFF_CMD="${KACHI_NP_OFF_CMD:-}"

mkdir -p "$OUT" || { echo "ERROR: không tạo được $OUT" >&2; exit 2; }
LOG="$OUT/session.log"
SUMMARY="$OUT/summary.tsv"
HALTSV="$OUT/hal-reads.tsv"
printf 'step\tquestion\tanswer\tevidence\n' > "$SUMMARY"
printf 'time\tstep\tfeature_id\tvalue\tsentinel\treply\n' > "$HALTSV"

# ── trạng thái phiên ────────────────────────────────────────────────────────────────────────────
SEQ=0
W1_DIRTY=0        # 1 = đã (có thể đã) ghi nguồn ⇒ hoàn tác phải trả 26
W2_ON=0           # 1 = đã bật tính năng Kachi ⇒ hoàn tác phải tắt
BRIDGE=0          # 1 = cầu kiểm thử sống + cổng mở
SF_PID=""; SS_PID=""; K_PID=""; T0=""; K_VER=""; K_CODE=""
M1_BT="?"; M2_YTM="?"; W1_RES="?"; W1B_RES="?"; ANS=""; CAP_FILE=""; PLAY_HIT=n
BR_RAW=""; BR_JSON=""

# ── in ấn + ghi ─────────────────────────────────────────────────────────────────────────────────
now()   { date +%Y%m%d-%H%M%S; }
say()   { printf '%s\n' "$*"; printf '%s  %s\n' "$(date +%FT%T%z)" "$*" >> "$LOG"; }
hr()    { say "──────────────────────────────────────────────────────────────"; }
note()  { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "${4:-}" >> "$SUMMARY"; }
a()     { "$ADB" -s "$SERIAL" "$@"; }
ash()   { a shell "$@" 2>&1 | tr -d '\r'; }

# Việc của owner: MỘT dòng tiếng Việt, chờ Enter (đọc /dev/tty để không ăn nhầm stdin).
todo() {
  printf '\n>>> [VIỆC CỦA ANH] %s\n    (xong bấm Enter) ' "$*"
  printf '%s  TODO %s\n' "$(date +%FT%T%z)" "$*" >> "$LOG"
  local _x; read -r _x </dev/tty || abort "không đọc được /dev/tty (chạy ở terminal thật, không nohup/pipe)"
}

# Hỏi c/k. Kết quả ở $ANS = y|n.
ask_yn() {
  local r=""
  while :; do
    printf '\n??? %s [c/k] ' "$*"; read -r r </dev/tty || abort "không đọc được /dev/tty (hết đầu vào) — không đoán câu trả lời"
    case "$r" in c|C|y|Y|co|có) ANS=y; break ;; k|K|n|N|khong|không) ANS=n; break ;; esac
  done
  printf '%s  ASK %s -> %s\n' "$(date +%FT%T%z)" "$*" "$ANS" >> "$LOG"
}

# next_file <bước> <tên> → in đường dẫn NN-<bước>-<tên>-<ts>.txt mới
# Gán vào NF (không gọi trong $(…) — subshell làm SEQ không tăng).
next_file() { SEQ=$((SEQ + 1)); NF="$(printf '%s/%02d-%s-%s-%s.txt' "$OUT" "$SEQ" "$1" "$2" "$(now)")"; }

# cap <bước> <tên> <lệnh shell trên xe>  → $CAP_FILE (luôn || true)
cap() {
  local step="$1" name="$2"; shift 2
  next_file "$step" "$name"; CAP_FILE="$NF"
  { printf '# $ adb -s %s shell %s\n# %s\n\n' "$SERIAL" "$*" "$(date +%FT%T%z)"; ash "$@" || true; } > "$CAP_FILE"
  say "   → $(basename "$CAP_FILE") ($(wc -l < "$CAP_FILE" | tr -d ' ') dòng)"
}

devtime() { ash "date '+%m-%d %H:%M:%S.000'" | head -1; }

# ── cầu kiểm thử ───────────────────────────────────────────────────────────────────────────────
# bridge <bước> <extra…>  → $BR_RAW (thô), $BR_JSON (resultData). Ghi bridge.log.
bridge() {
  local step="$1"; shift
  BR_RAW="$(ash "am broadcast -n $COMP -a $ACTION $*")"
  { printf '### %s [%s] am broadcast … %s\n' "$(date +%FT%T%z)" "$step" "$*"; printf '%s\n' "$BR_RAW"; } >> "$OUT/bridge.log"
  BR_JSON="$(printf '%s' "$BR_RAW" | python3 -I -c 'import re,sys
t=sys.stdin.read(); m=re.search(r"data=\"(.*)\"", t, re.S); print(m.group(1).replace("\n"," ") if m else "")')"
}

# jfield <khoá> — một trường mức 1 của $BR_JSON, "" nếu không có.
jfield() {
  printf '%s' "$BR_JSON" | python3 -I -c 'import json,sys
k=sys.argv[1]
try: d=json.loads(sys.stdin.read())
except Exception: print(""); sys.exit(0)
v=d.get(k,""); print(str(v).lower() if isinstance(v,bool) else v)' "$1"
}

# bridge_alive — `cmd state` phải trả "ok":true (công tắc TẮT vẫn trả JSON với error=test_mode_off).
bridge_alive() {
  bridge gate "--es cmd state"
  [ "$(jfield ok)" = "true" ]
}

# need_bridge <bước> — kiểm cầu TRƯỚC bước cần nó; tắt ⇒ nhắc bật lại một lần. 0 = dùng được.
need_bridge() {
  if bridge_alive; then BRIDGE=1; return 0; fi
  say "   cầu kiểm thử không mở ($(jfield error)) — công tắc tự tắt sau 60 phút / khi tắt máy."
  todo "Bật lại Kachi › Cài đặt › Hệ thống & quyền › Nâng cao › 'Chế độ kiểm thử qua adb' (bước $1 cần nó)."
  if bridge_alive; then BRIDGE=1; return 0; fi
  BRIDGE=0; say "   cầu vẫn tắt ⇒ bước $1 bỏ phần cần cầu."; return 1
}

# hal_read <bước> <id>
hal_read() {
  local step="$1" id="$2" v s r
  if [ "$BRIDGE" != 1 ]; then printf '%s\t%s\t%s\t\t\tbridge_off\n' "$(now)" "$step" "$id" >> "$HALTSV"; return 0; fi
  bridge "$step" "--es cmd hal --es op getid --es dev $DEV --es m $id"
  v="$(jfield value)"; s="$(jfield sentinel)"; r="$(jfield reply)"
  [ -z "$r" ] && r="$(jfield error)"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(now)" "$step" "$id" "$v" "$s" "$r" >> "$HALTSV"
  say "   hal getid $id → value=[$v] sentinel=$s ($r)"
}

hal_read_all() {
  local step="$1" id
  need_bridge "$step" || return 0
  for id in $ID_SRC_SET $ID_STATE_SET $ID_TITLE_SET $ID_SRC_READ $ID_INFO_RESULT; do hal_read "$step" "$id"; done
}

# hal_write <bước> <id> <value>  → 0 nếu HAL nhận ("ok: HAL accepted…").
hal_write() {
  local step="$1" id="$2" val="$3" ok rc rep hl
  bridge "$step" "--es cmd hal --es op setev --es dev $DEV --es m $id --es args $val --ez auto_confirm true"
  ok="$(jfield ok)"; rc="$(jfield rc)"; rep="$(jfield reply)"; hl="$(jfield hal_line)"
  [ -z "$rep" ] && rep="$(jfield error)"
  say "   hal setev $id=$val → ok=$ok rc=$rc hal_line=[$hl] ($rep)"
  note "$step" "setev $id=$val" "rc=$rc" "$rep"
  case "$rep" in "ok: HAL accepted"*) return 0 ;; *) return 1 ;; esac
}

# ── sức khoẻ: dừng ở bất thường đầu tiên ─────────────────────────────────────────────────────────
pidof_dev() { ash "pidof $1" | head -1; }

baseline() {
  T0="$(devtime)"
  SF_PID="$(pidof_dev surfaceflinger)"; SS_PID="$(pidof_dev system_server)"; K_PID="$(pidof_dev "$PKG")"
  say "mốc sức khoẻ: t0=[$T0] surfaceflinger=$SF_PID system_server=$SS_PID kachi=$K_PID"
  [ -z "$K_PID" ] && say "   [CẢNH BÁO] Kachi chưa chạy (pidof rỗng)."
}

# health <bước> [ask] — so pid + crash buffer từ t0; 'ask' ⇒ hỏi owner cụm còn bình thường không.
health() {
  local step="$1" why="" sf ss k
  sf="$(pidof_dev surfaceflinger)"; ss="$(pidof_dev system_server)"; k="$(pidof_dev "$PKG")"
  [ "$sf" != "$SF_PID" ] && why="$why surfaceflinger pid ${SF_PID}→${sf:-mất};"
  [ "$ss" != "$SS_PID" ] && why="$why system_server pid ${SS_PID}→${ss:-mất};"
  if [ -n "$K_PID" ] && [ "$k" != "$K_PID" ]; then why="$why Kachi pid ${K_PID}→${k:-chết};"; fi
  cap "$step" crash "logcat -b crash -d -v time -t '$T0'"
  if grep -v '^#' "$CAP_FILE" | grep -Eq "$PKG|surfaceflinger|system_server"; then
    why="$why crash buffer có Kachi/SurfaceFlinger/system_server;"
  fi
  if [ "${2:-}" = ask ] && [ -z "$why" ]; then
    ask_yn "Cụm còn hiện bình thường (km/h, đồng hồ, không đen/trống)?"
    [ "$ANS" = n ] && why="$why owner: cụm bất thường;"
  fi
  if [ -n "$why" ]; then
    say "!!! BẤT THƯỜNG ở bước $step:$why"
    note "$step" "health" "ANOMALY" "$why"
    cap "$step" anomaly-logcat "logcat -d -v time -t '$T0'"
    abort "bất thường ở $step"
  fi
}

# ── hoàn tác (gọi tay sau W1/W2 + trap EXIT) ─────────────────────────────────────────────────────
restore() {
  if [ "$W2_ON" = 1 ]; then
    say "HOÀN TÁC W2: tắt tính năng Kachi"
    if [ -n "$KACHI_NP_OFF_CMD" ]; then
      bridge restore-w2 "$KACHI_NP_OFF_CMD"; say "   → $BR_JSON"
      # Công tắc là PREFS BỀN (`ClusterNowPlaying.setEnabled` → `Prefs.setNowPlayingEnabled`): `off` không tới nơi (cầu tự tắt
      # sau 60 phút / Kachi chết) ⇒ xe GIỮ tính năng bật sau buổi thử — phải báo rõ + để W2_ON=1 cho trap thử lại một lần nữa.
      if [ "$(jfield ok)" = "true" ]; then W2_ON=0
      else
        say "!!! HOÀN TÁC W2 CHƯA XÁC NHẬN ($(jfield error)). Công tắc lưu bền ⇒ tắt tay:"
        say "    Kachi › Cài đặt › công tắc 'Tên bài hát lên cụm đồng hồ' = TẮT; hoặc bật cầu rồi gõ:"
        say "    $ADB -s $SERIAL shell am broadcast -n $COMP -a $ACTION $KACHI_NP_OFF_CMD"
      fi
    else
      say "   [!] KACHI_NP_OFF_CMD rỗng — tắt công tắc 'Tên bài hát lên cụm đồng hồ' bằng tay trong Cài đặt."; W2_ON=0
    fi
  fi
  if [ "$W1_DIRTY" = 1 ]; then
    say "HOÀN TÁC W1: nguồn nhạc cụm = $SRC_RESTORE"
    if hal_write restore "$ID_SRC_SET" "$SRC_RESTORE"; then
      W1_DIRTY=0
      BRIDGE=1; hal_read restore-readback "$ID_SRC_SET"; hal_read restore-readback "$ID_SRC_READ"
    else
      say "!!! HOÀN TÁC CHƯA XÁC NHẬN. Bật cầu rồi gõ tay:"
      say "    $ADB -s $SERIAL shell am broadcast -n $COMP -a $ACTION --es cmd hal --es op setev --es dev $DEV --es m $ID_SRC_SET --es args $SRC_RESTORE --ez auto_confirm true"
      say "    Hoặc: đổi sang app nhạc khác rồi quay lại (MediaController đổi nguồn ⇒ tự ghi lại)."
    fi
  fi
}
# shellcheck disable=SC2329  # gọi qua trap EXIT
on_exit() { restore; say "Bằng chứng: $OUT"; }
abort() { say "DỪNG: $*"; exit 3; }
trap on_exit EXIT
trap 'say "Ngắt (Ctrl-C)"; exit 130' INT TERM

# ── chụp lúc nhạc đang phát (M2/M3/M4) ──────────────────────────────────────────────────────────
# play_capture <bước> <gói-mong-đợi|""> <việc của owner>  → $PLAY_HIT = y nếu log thấy MediaController gửi tên gói đó
play_capture() {
  local step="$1" want="$2" msg="$3" since lc flt fs
  since="$(devtime)"
  todo "$msg"
  cap "$step" logcat "logcat -d -v time -t '$since'"
  lc="$CAP_FILE"
  next_file "$step" mediactl-filtered; flt="$NF"
  grep -E 'MediaController|OtherClient|MediaInfoSender|MediaTaskManager|AbsBYDAutoDevice' "$lc" > "$flt" || true
  say "   lọc MediaController/OtherClient/MediaInfoSender: $(wc -l < "$flt" | tr -d ' ') dòng"
  grep -E 'focus package|onMediaControllerChange|sendMusic(Name|Source|State) result|onMetadataChanged' "$flt" \
    | tail -12 | sed 's/^/     /'
  cap "$step" media_session "dumpsys media_session"
  grep -E 'package=|state=PlaybackState|description=' "$CAP_FILE" | head -12 | sed 's/^/     /'
  cap "$step" audio "dumpsys audio"
  next_file "$step" focus-stack; fs="$NF"
  sed -n '/Audio Focus stack/,/^$/p' "$CAP_FILE" > "$fs"
  head -10 "$fs" | sed 's/^/     /'
  hal_read_all "$step"
  PLAY_HIT=n
  if [ -n "$want" ] && grep -q "$want" "$flt" && grep -Eq 'sendMusicName result|onMediaControllerChange' "$flt"; then PLAY_HIT=y; fi
  [ -n "$want" ] && note "$step" "log: MediaController bắt $want + gửi tên" "$PLAY_HIT" "$(basename "$flt")"
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════
say "cluster-now-playing.sh · xe=$SERIAL · ra=$OUT"
hr
if ! a get-state >/dev/null 2>&1; then
  say "ERROR: adb không thấy $SERIAL. Dựng cầu nc theo docs/diagnostics/adb-car-tunnel-macos.md rồi chạy lại."
  exit 4
fi

# ── B0 — bật cầu kiểm thử (kiểm bằng `cmd state`) ──────────────────────────────────────────────
say "[B0] cầu kiểm thử"
todo "Nổ máy (xe ON, cụm sáng); mở Kachi › Cài đặt › Hệ thống & quyền › Nâng cao › BẬT 'Chế độ kiểm thử qua adb'."
if need_bridge B0; then say "   cầu kiểm thử SỐNG (cmd state ok:true)"; fi
note B0 "cầu kiểm thử (cmd state)" "$BRIDGE" "$(jfield error)"

# ── M0 — danh tính & cờ (CHỈ ĐỌC) ───────────────────────────────────────────────────────────────
hr; say "[M0] danh tính & cờ"
cap M0 props "getprop ro.build.display.id; getprop ro.build.fingerprint; getprop persist.sys.car.type; echo forbidsend=[\$(getprop sys.byd.mediacenter.forbidsend)]"
FORBID="$(sed -n 's/^forbidsend=\[\(.*\)\]$/\1/p' "$CAP_FILE" | head -1)"
say "   sys.byd.mediacenter.forbidsend=[$FORBID]"
note M0 "sys.byd.mediacenter.forbidsend" "[$FORBID]" "rỗng/false ⇒ loại H5"
cap M0 pkg-kachi "dumpsys package $PKG | grep -E 'versionName|versionCode'"
K_VER="$(sed -n 's/.*versionName=\([^ ]*\).*/\1/p' "$CAP_FILE" | head -1)"
K_CODE="$(sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' "$CAP_FILE" | head -1)"
cap M0 pkg-mediacontroller "dumpsys package $MC_PKG | grep -E 'versionName|versionCode|enabled='"
MC_VER="$(sed -n 's/.*versionName=\([^ ]*\).*/\1/p' "$CAP_FILE" | head -1)"
say "   Kachi=$K_VER ($K_CODE) · mediacontroller=$MC_VER"
note M0 "phiên bản" "Kachi $K_VER ($K_CODE) · mediacontroller $MC_VER" ""
case "$FORBID" in true|1) say "   [!] forbidsend=$FORBID ⇒ MediaController KHÔNG gửi gì (H5). Vẫn đo tiếp để có bằng chứng." ;; esac
baseline
health M0

# ── M1 — đối chứng Bluetooth (widget cụm còn sống?) ──────────────────────────────────────────────
hr; say "[M1] đối chứng Bluetooth, Kachi TẮT chiếu cụm"
play_capture M1 "" "Tắt chiếu cụm của Kachi; phát nhạc Bluetooth từ điện thoại, chuyển bài 1 lần, nhìn cụm."
ask_yn "Cụm có hiện tên bài Bluetooth (ô nhạc + icon) không?"; M1_BT="$ANS"
note M1 "BT hiện tên trên cụm (chiếu tắt)" "$M1_BT" "c ⇒ widget sống; k ⇒ H3/cổng khác, KHÔNG đi W1"
health M1 ask

# ── M2–M4 — YT Music ────────────────────────────────────────────────────────────────────────────
hr; say "[M2-M4] YT Music"
play_capture M2-ytm "$YTM_PKG" "Mở YT Music trên màn xe, phát một bài, bấm bài kế 1 lần; nhìn cụm."
M2_YTM="$PLAY_HIT"
ask_yn "YT Music: cụm có hiện tên bài không?"; note M2-ytm "YTM hiện tên trên cụm" "$ANS" "kỳ vọng k (H1)"
health M2-ytm ask

# ── M2–M4 — YouTube ─────────────────────────────────────────────────────────────────────────────
hr; say "[M2-M4] YouTube"
play_capture M2-yt "$YT_PKG" "Mở YouTube trên màn xe, phát một video nhạc, chuyển video kế 1 lần; nhìn cụm."
ask_yn "YouTube: cụm có hiện tên bài không?"; note M2-yt "YT hiện tên trên cụm" "$ANS" "kỳ vọng k (H1)"
health M2-yt ask

# ── M5 — log cụm ───────────────────────────────────────────────────────────────────────────────
hr; say "[M5] CLUSTER_LOG (có cổng mức log — có thể im)"
cap M5 cluster-log "logcat -d -v time | grep -E 'CLUSTER_LOG|updateMediaInfoDisplay|ICON_MUSIC|Music Status'"
tail -8 "$CAP_FILE" | sed 's/^/     /'
M5_SRC="$(grep -o 'm_can33fMediaSource=[0-9]*' "$CAP_FILE" | tail -1)"
note M5 "m_can33fMediaSource cuối" "${M5_SRC:-không có dòng}" "=26 + hide ⇒ H1 [ĐO]"

# ── M6 — lặp khi Kachi ĐANG chiếu cụm (H3) ──────────────────────────────────────────────────────
hr; say "[M6] lặp khi Kachi ĐANG chiếu cụm"
play_capture M6-bt "" "BẬT chiếu cụm của Kachi như thường ngày; phát nhạc Bluetooth, chuyển bài 1 lần; nhìn cụm."
ask_yn "Đang chiếu: cụm có hiện tên bài Bluetooth không?"; note M6-bt "BT hiện tên khi đang chiếu" "$ANS" "M1=c mà đây=k ⇒ lớp chiếu đè (H3)"
health M6-bt ask
play_capture M6-ytm "$YTM_PKG" "Vẫn chiếu; chuyển sang YT Music, phát và bấm bài kế 1 lần; nhìn cụm."
ask_yn "Đang chiếu: YT Music có hiện tên bài không?"; note M6-ytm "YTM hiện tên khi đang chiếu" "$ANS" ""
health M6-ytm ask

# ═══ TẦNG 1b — GHI (duy nhất): W1 nguồn = 11 + trạng thái, W1b tên do MediaController gửi ═══════════
hr; say "[W1] ghi thử nguồn = $SRC_TEST (cụm cho hiện) — hoàn tác = $SRC_RESTORE"
W1_GO=y
if [ "$M1_BT" != y ] && [ "${FORCE_W1:-0}" != 1 ]; then
  say "   bỏ W1: M1 (Bluetooth) chưa hiện tên ⇒ ghi nguồn không trả lời được câu hỏi nào."
  W1_GO=n; note W1 "chạy" "skip" "M1=$M1_BT"
fi
[ "$M2_YTM" != y ] && say "   [lưu ý] log M2 chưa thấy MediaController gửi tên YTM (H2?) — W1b có thể không có tên để hiện."
if [ "$W1_GO" = y ] && ! need_bridge W1; then W1_GO=n; note W1 "chạy" "skip" "cầu tắt"; fi
if [ "$W1_GO" = y ]; then
  todo "TẮT chiếu cụm của Kachi; mở YT Music và để nó ĐANG PHÁT (cụm hiện không có ô nhạc)."
  say "   Sẽ ghi: setev $ID_SRC_SET=$SRC_TEST rồi $ID_STATE_SET=1 trên $DEV. Hoàn tác: $ID_SRC_SET=$SRC_RESTORE (tự động)."
  printf '\n!!! Gõ đúng chữ GHI rồi Enter để cho phép ghi (gõ khác = bỏ W1): '
  CONF=""; read -r CONF </dev/tty || CONF=""
  printf '%s  CONFIRM W1 -> [%s]\n' "$(date +%FT%T%z)" "$CONF" >> "$LOG"
  if [ "$CONF" = "GHI" ]; then
    hal_read W1-before "$ID_SRC_SET"; hal_read W1-before "$ID_SRC_READ"
    W1_DIRTY=1                                    # đặt TRƯỚC lệnh ghi: ngắt giữa chừng vẫn được trả 26
    if hal_write W1 "$ID_SRC_SET" "$SRC_TEST"; then
      hal_write W1 "$ID_STATE_SET" 1 || true      # 1 = đang phát = đúng giá trị MediaController nhớ
      health W1
      ask_yn "Ngay sau khi ghi: cụm có hiện ô nhạc (icon; có/không chữ) không?"; W1_RES="$ANS"
      note W1 "ô nhạc hiện sau nguồn=11 (chưa đổi bài)" "$W1_RES" "icon đổi ⇒ chuỗi bị xoá (doc §1(4)) nên có thể chưa có chữ"
      hal_read W1-after "$ID_SRC_SET"; hal_read W1-after "$ID_SRC_READ"; hal_read W1-after "$ID_INFO_RESULT"

      say "[W1b] MediaController tự gửi tên bài mới khi đổi bài (script KHÔNG ghi tên)"
      play_capture W1b "$YTM_PKG" "Bấm BÀI KẾ trên YT Music 1 lần, chờ bài mới chạy vài giây, nhìn cụm."
      ask_yn "Cụm có hiện TÊN BÀI MỚI (đúng tên đang phát) không?"; W1B_RES="$ANS"
      note W1b "tên bài mới hiện chỉ với nguồn=11" "$W1B_RES" "c ⇒ 'chỉ ghi nguồn' là đủ"
      ask_yn "Ô nhạc có chớp/tự tắt lại sau vài giây không?"
      note W1b "ô nhạc chớp/tự tắt" "$ANS" "c ⇒ rủi ro r2 (giằng co)"
      health W1b
    else
      say "   HAL/cầu TỪ CHỐI nguồn=11 ⇒ phương án A chết ở bước ghi (doc §5 W1). rc ở trên."
      W1_RES=rejected; note W1 "setev nguồn=11" "rejected" "$(jfield hal_line)"
    fi
    restore                                       # trả 26 NGAY, không đợi thoát
    ask_yn "Sau khi trả nguồn=26: ô nhạc đã ẩn lại chưa?"; note W1-restore "ô nhạc ẩn lại sau 26" "$ANS" "k ⇒ đổi app nhạc rồi quay lại"
    health W1-restore ask
  else
    say "   owner không gõ GHI ⇒ bỏ W1."; note W1 "xác nhận" "bỏ" ""
  fi
fi

# ── W2 — tính năng Kachi mới (tuỳ chọn) ─────────────────────────────────────────────────────────
hr; say "[W2] tính năng Kachi (chỉ khi W1/W1b = c và bản cài ≥ $W2_MIN_CODE)"
if [ "$W1B_RES" != y ] && [ "$W1_RES" != y ]; then
  say "   bỏ W2: W1/W1b chưa cho thấy nguồn=11 làm hiện ô nhạc."; note W2 "chạy" "skip" "W1=$W1_RES W1b=$W1B_RES"
elif ! [ "${K_CODE:-0}" -ge "$W2_MIN_CODE" ] 2>/dev/null; then
  say "   bỏ W2: bản cài $K_VER ($K_CODE) < $W2_MIN_CODE (3.1.11)."; note W2 "chạy" "skip" "bản cài $K_CODE"
elif [ -z "$KACHI_NP_CMD" ]; then
  say "   bỏ W2: KACHI_NP_CMD chưa đặt (TODO — doc §7.5)."; note W2 "chạy" "skip" "KACHI_NP_CMD rỗng"
elif ! need_bridge W2; then
  note W2 "chạy" "skip" "cầu tắt"
else
  case "$KACHI_NP_CMD$KACHI_NP_OFF_CMD" in *[\;\|\&\`\$\<\>]*) abort "KACHI_NP_CMD chứa ký tự shell cấm" ;; esac
  printf '\n!!! Gõ đúng chữ BAT để bật tính năng Kachi (gõ khác = bỏ W2): '
  CONF=""; read -r CONF </dev/tty || CONF=""
  if [ "$CONF" = "BAT" ]; then
    W2_ON=1
    bridge W2-on "$KACHI_NP_CMD"; say "   → $BR_JSON"; note W2 "bật" "$(jfield ok)" "$(jfield error)"
    health W2-on
    play_capture W2-ytm "$YTM_PKG" "TẮT chiếu cụm; phát YT Music, bấm bài kế 1 lần, nhìn cụm."
    ask_yn "Kachi bật: YT Music có hiện tên bài trên cụm không?"; note W2-ytm "YTM hiện tên (Kachi)" "$ANS" ""
    health W2-ytm ask
    play_capture W2-yt "$YT_PKG" "Chuyển sang YouTube, phát video nhạc, chuyển video kế 1 lần, nhìn cụm."
    ask_yn "Kachi bật: YouTube có hiện tên bài trên cụm không?"; note W2-yt "YT hiện tên (Kachi)" "$ANS" ""
    health W2-yt ask
    play_capture W2-cast "$YTM_PKG" "BẬT chiếu cụm của Kachi; quay lại YT Music, bấm bài kế 1 lần, nhìn cụm."
    ask_yn "Kachi bật + đang chiếu: có hiện tên bài không?"; note W2-cast "hiện tên khi chiếu (Kachi)" "$ANS" ""
    health W2-cast ask
    restore
    sleep 2                                       # `np off` trả nguồn trên luồng `kachi-np` (bất đồng bộ) — một nhịp rồi mới đọc lại
    hal_read W2-off "$ID_SRC_SET"; hal_read W2-off "$ID_SRC_READ"
    ask_yn "Sau khi TẮT tính năng: ô nhạc YT Music đã ẩn lại (như trước) chưa?"; note W2-off "ẩn lại sau khi tắt" "$ANS" ""
  else
    note W2 "xác nhận" "bỏ" ""
  fi
fi

# ── tổng kết ────────────────────────────────────────────────────────────────────────────────────
hr; say "TỔNG KẾT (summary.tsv)"
column -t -s "$(printf '\t')" "$SUMMARY" 2>/dev/null | sed 's/^/  /' || cat "$SUMMARY"
cap end logcat-tail "logcat -d -v time -t '$T0' | grep -E 'MediaController|OtherClient|MediaInfoSender|AbsBYDAutoDevice|KachiTest|CLUSTER_LOG'"
health end
exit 0
