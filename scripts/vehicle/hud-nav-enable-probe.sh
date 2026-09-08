#!/usr/bin/env bash
# =============================================================================
# hud-nav-enable-probe.sh — thử BẬT nav lên HUD ZIN (các đường CHƯA thử)
# =============================================================================
# MỤC ĐÍCH: probe 3 candidate MỚI (verified có thật trong firmware) mà đợt A11
#   chưa đụng, + đọc capability HUD. Nguồn: docs/diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md
#
#   [instr]    0x32B1102E = INSTRUMENT_HUD_NAVIGATION_MAP_SET   (owner mới thử _CONFIG 0x38B00030, CHƯA thử cái _SET này)
#   [setting]  0x8e2fcdbf = SET_NAVIGATION_FUSION_SWITCH_SET
#   [setting]  0xd61b6746 = SET_SAFETY_DRIVING_AID_FUSION_SWITCH_SET
#   + capability: service list | dumpsys  (HUD zin có khai báo mode nav không)
#
# ⚠ AN TOÀN: script GHI vào xe NHƯNG luôn ĐỌC-giá-trị-cũ TRƯỚC rồi KHÔI PHỤC sau mỗi thử.
#   Chỉ ghi 1 candidate 1 lúc, restore xong mới sang cái kế. Nếu write bị từ chối
#   (rc=-2147482648) → đúng như dự đoán (cùng gate provisioning), ghi log rồi bỏ qua.
# KỲ VỌNG THẬT: xác suất bật được nav = THẤP; giá trị chính = CHẨN ĐOÁN dứt điểm
#   (đặc biệt capability: không có mode nav ⇒ HUD zin chịu, đi Taobao).
#
# CHUẨN BỊ TRƯỚC KHI CHẠY:
#   1. Mở GMaps dẫn đường qua app ClusterNav (để có nội dung nav trên bus).
#   2. HUD đang hiện speed/ADAS (xác nhận HUD zin sống).
#   3. NGỒI NHÌN kính lái trong lúc chạy — script sẽ dừng hỏi "HUD có hiện nav không?".
#   4. navopen jar đặt cạnh script (hoặc set JAR_SRC=...). Mặc định navopen-v4.jar.
#
# CÁCH CHẠY:  ./hud-nav-enable-probe.sh            (USB, adb -d)
#             CAR_IP=192.168.1.50 ./hud-nav-enable-probe.sh   (WiFi)
# =============================================================================
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
JAR_SRC="${JAR_SRC:-$HERE/navopen-v4.jar}"
[ -f "$JAR_SRC" ] || JAR_SRC="$HERE/navopen.jar"
JAR_DST="/data/local/tmp/navopen.jar"
TS="$(date +%Y%m%d-%H%M%S)"
LOG="${LOG:-$HERE/hud-nav-enable-probe-$TS.txt}"

# --- adb target: USB (-d) mặc định, hoặc CAR_IP cho WiFi ---
ADB="${ADB:-adb}"
if [ -n "${CAR_IP:-}" ]; then "$ADB" connect "$CAR_IP:5555" >/dev/null 2>&1; TGT=(-s "$CAR_IP:5555"); else TGT=(-d); fi
adb_sh(){ "$ADB" "${TGT[@]}" shell "$@"; }

log(){ printf '%s\n' "$*" | tee -a "$LOG"; }
hr(){ log "------------------------------------------------------------------"; }

[ -f "$JAR_SRC" ] || { echo "FATAL: không thấy navopen jar ($JAR_SRC). set JAR_SRC=path"; exit 1; }
"$ADB" "${TGT[@]}" get-state >/dev/null 2>&1 || { echo "FATAL: không thấy xe qua adb (USB cắm chưa? hoặc set CAR_IP=)"; exit 1; }

log "### HUD NAV-ENABLE PROBE  $TS"
log "### GHI+ROLLBACK. Đọc kỹ khi script hỏi. Log: $LOG"
"$ADB" "${TGT[@]}" push "$JAR_SRC" "$JAR_DST" >/dev/null 2>&1 && log "-- navopen pushed"
NAV="CLASSPATH=$JAR_DST app_process /system/bin com.byd.navopen.NavOpen"

# rc từ dòng "get ... = N" hoặc "set ... = N"
val_of(){ printf '%s' "$1" | sed -n 's/.*= *\(-\{0,1\}[0-9][0-9]*\).*/\1/p' | tail -1; }
getraw(){ adb_sh "$NAV getraw $1 $2" 2>&1; }
setraw(){ adb_sh "$NAV setraw $1 $2 $3" 2>&1; }

# ---------------------------------------------------------------------------
hr; log "== PHASE A — ĐỌC context (không ghi) =="
for pair in "instr 32B1102E:HUD_NAVIGATION_MAP_SET(_SET moi)" \
            "instr 420A1010:ROAD_NAME_CHECK_STATE(1=VALID/2=INVALID — vi sao ten duong khong len)" \
            "instr 30100030:HUD_NAV_MAP_CONFIG_STATUS" \
            "setting 31E00008:EXHIBITION_MODE(showroom)" \
            "setting 8e2fcdbf:NAVIGATION_FUSION_SWITCH" \
            "setting d61b6746:SAFETY_DRIVING_AID_FUSION_SWITCH" \
            "instr 38B00030:HUD_NAV_MAP_CONFIG(da biet)" \
            "setting 38B00015:HUD_CONFIG(1=W)" \
            "setting 38B0001C:HUD_SWITCH_STATUS" \
            "setting 38B00028:HUD_NAV_CONTENT_STATUS"; do
  dev="${pair%% *}"; rest="${pair#* }"; id="${rest%%:*}"; name="${rest#*:}"
  out="$(getraw "$dev" "$id")"; log "  [$dev $id] $name = $(val_of "$out")   ($(printf '%s' "$out" | tail -1))"
done

hr; log "== PHASE B — CAPABILITY (HUD zin co khai bao mode nav khong?) =="
log "-- service list (hud/vision):"; adb_sh "service list 2>/dev/null | grep -iE 'hud|vision|instrument'" 2>&1 | tee -a "$LOG"
for svc in CarHudService hud vision ICarHudService; do
  d="$(adb_sh "dumpsys $svc 2>/dev/null" 2>&1)"; [ -n "$d" ] && { log "-- dumpsys $svc:"; printf '%s\n' "$d" | grep -iE 'mode|nav|support|feature|config' | head -20 | tee -a "$LOG"; }
done
log "  (Nếu KHÔNG thấy 'nav' trong supported modes → HUD zin nhiều khả năng không hỗ trợ nav ⇒ chốt đi Taobao.)"

# ---------------------------------------------------------------------------
# PROBE 1 candidate: đọc-cũ → set test → HỎI owner nhìn kính → restore.
probe(){ # dev id name val1 [val2...]
  local dev="$1" id="$2" name="$3"; shift 3
  hr; log "== PROBE [$dev $id] $name =="
  local prior; prior="$(val_of "$(getraw "$dev" "$id")")"; log "  giá trị CŨ = ${prior:-NA}"
  for v in "$@"; do
    log "  → thử set = $v ..."
    local so; so="$(setraw "$dev" "$id" "$v")"; local rc; rc="$(val_of "$so")"
    log "     kết quả: $(printf '%s' "$so" | tail -1)"
    case "$rc" in
      -2147482648) log "     → BỊ TỪ CHỐI (not provisioned) — đúng như dự đoán, cùng gate. Bỏ qua." ;;
      *) log "     → GHI ĐƯỢC (rc=$rc). >>> NHÌN KÍNH LÁI NGAY: HUD có hiện NAV (mũi tên/đường) không?";
         read -r -p "     Nhập q.sát (vd 'co-nav' / 'khong' / mô tả) rồi Enter: " obs; log "     [owner thấy]: $obs" ;;
    esac
  done
  # RESTORE
  if [ -n "${prior:-}" ] && [ "$prior" != "NA" ]; then
    setraw "$dev" "$id" "$prior" >/dev/null 2>&1; log "  ↺ khôi phục về $prior"
  else log "  (không có giá trị cũ hợp lệ để khôi phục — candidate vốn not-provisioned, không đổi gì)"; fi
}

hr; log "== PHASE C — PROBE có rollback (mỗi cái thử xong khôi phục ngay) =="
log ">>> Đảm bảo GMaps đang dẫn + HUD hiện speed TRƯỚC khi tiếp. Enter để bắt đầu."; read -r _
probe instr   32B1102E "HUD_NAVIGATION_MAP_SET" 1 2
probe setting 8e2fcdbf "NAVIGATION_FUSION_SWITCH" 1
probe setting d61b6746 "SAFETY_DRIVING_AID_FUSION_SWITCH" 1

hr; log "== PHASE D — CHẨN ĐOÁN TÊN ĐƯỜNG (vì sao mũi tên+cự ly lên mà tên đường không) =="
log ">>> ĐỂ APP ĐANG DẪN (GMaps qua ClusterNav, đang ghi tên đường 0x43FA1008). Enter để đọc check-state."; read -r _
for i in 1 2 3; do
  cs="$(val_of "$(getraw instr 420A1010)")"; log "  [lần $i] ROAD_NAME_CHECK_STATE (0x420A1010) = ${cs:-NA}"; sleep 1
done
log "  Diễn giải:  2 = INVALID → MCU TỪ CHỐI chuỗi tên đường của app (→ thử charset/khung: dấu-cách dẫn, NUL, bỏ dấu tiếng Việt, ≤7-8 ký tự)."
log "              1 = VALID nhưng kính vẫn trống → widget/coding gate (0x38B00030), không phải format."
log "              (Mũi tên/cự ly là INT không có check-state nên luôn lên — đó là lý do bất đối xứng.)"

hr; log "== XONG =="
log "Gửi lại file log này: $LOG"
log "Đọc kết quả: (a) PHASE B có 'nav' trong supported modes? (b) PROBE nào GHI ĐƯỢC (rc>=0) và owner có thấy nav trên kính?"
log "Kỳ vọng thật: nhiều khả năng tất cả bị từ chối / không có mode nav → chốt HUD zin chịu, đi Taobao. Nếu có cái ghi được + kính hiện nav → BÁO NGAY, đào tiếp."
