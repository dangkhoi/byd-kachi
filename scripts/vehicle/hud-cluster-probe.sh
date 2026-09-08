#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# hud-cluster-probe.sh — ON-CAR field kit: đưa nav/tên-đường/biển-tốc-độ lên
#   HUD (kính lái) và CLUSTER (đồng hồ). adb THUẦN, không cần cài APK/UI.
#
#   Cắm/để xe ĐỖ → connect adb → chạy script này → nó tự bắn từng ứng viên,
#   mỗi ứng viên: ĐỌC prior → GHI → BẠN NHÌN cụm/HUD → KHÔI PHỤC prior → verify.
#   Restore fail ở bất kỳ đâu → KHOÁ, dừng mọi ứng viên sau.
#
# CƠ CHẾ: navopen-v3.jar (reflection HAL, đã proven 29/07) + am broadcast Amap
#         (render cụm đã proven 29/07). KHÔNG ghi dữ liệu ra ngoài; chỉ local adb.
#
# ⚠ AN TOÀN: chỉ chạy khi XE ĐỖ, số P, phanh tay, khu vực riêng. KHÔNG chạy khi lái.
# ⚠ Đây là hobby/RE — ghi HAL feature-id là rủi ro; mọi ghi đều có prior+restore,
#   nhưng bạn tự chịu trách nhiệm. navopen dùng context-spoof (getPackageName=
#   com.byd.dashcast) → "chạy được ở đây" ≠ "app tự làm được bằng uid thật".
#
# DÙNG:
#   ./hud-cluster-probe.sh <serial|ip:port>        # ví dụ <vehicle-ip>:5555
#   SEL="A B C D" ./hud-cluster-probe.sh <ser>  # chọn nhóm (mặc định A B C D)
#   OBSERVE=manual  (mặc định: prompt p/f/b mỗi ca)  | OBSERVE=6 (tự sleep 6s)
#   I_CONFIRM_PARKED=1  → bỏ qua prompt "xe đã đỗ" (hands-free)
#   DRY=1  → chỉ in ra sẽ làm gì, KHÔNG chạm xe
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

# ── config ──────────────────────────────────────────────────────────────────
SERIAL="${1:-${CAR_HOST:-}}"
SEL="${SEL:-A B C D E}"
OBSERVE="${OBSERVE:-manual}"
DRY="${DRY:-0}"
JAR_SRC="${JAR_SRC:-$(cd "$(dirname "$0")/../.." && pwd)/../apks/navopen-v3.jar}"
JAR_DST="/data/local/tmp/navopen.jar"
TS="$(date +%Y%m%d-%H%M%S)"
LOG="hud-cluster-probe-$TS.log"

RECOVERY_LOCK=0        # 1 = restore đã fail → cấm mọi ghi tiếp
STOP_REQUESTED=0       # operator gõ 's' = skip phần còn lại
VERDICT=""
ARMED_DEV=""; ARMED_ID=""; ARMED_PRIOR=""   # frame rollback đang "lên nòng"

# ── util ──────────────────────────────────────────────────────────────────--
log(){ printf '%s\n' "$*" | tee -a "$LOG" ; }
hr(){ log "────────────────────────────────────────────────────────────"; }
die(){ log "FATAL: $*"; exit 1; }

adb_sh(){ # chạy 1 lệnh shell trên xe
  if [ "$DRY" = "1" ]; then printf '   [DRY] adb -s %s shell %q\n' "$SERIAL" "$1"; return 0; fi
  adb -s "$SERIAL" shell "$1"
}

navopen(){ adb_sh "CLASSPATH=$JAR_DST app_process /system/bin com.byd.navopen.NavOpen $*"; }

# hal_get <dev> <hexid> → in ra số nguyên, hoặc "NA" nếu không đọc được
hal_get(){
  local out val
  out="$(navopen getraw "$1" "$2" 2>&1)"; printf '%s\n' "$out" >>"$LOG"
  printf '%s\n' "$out" | grep -q "FAILED" && { echo "NA"; return; }
  val="$(printf '%s\n' "$out" | awk '/ get .*= /{v=$NF} END{print v}')"
  { [ -z "$val" ] || [ "$val" = "-2147483648" ]; } && echo "NA" || echo "$val"
}

# hal_set <dev> <hexid> <val> → in ra rc (log đầy đủ)
hal_set(){
  local out
  out="$(navopen setraw "$1" "$2" "$3" 2>&1)"; printf '%s\n' "$out" >>"$LOG"
  printf '%s\n' "$out" | awk -F'rc=' '/-> rc=/{r=$2} END{print r}'
}

# arm/disarm frame rollback (để trap khôi phục nếu Ctrl-C giữa chừng)
arm(){ ARMED_DEV="$1"; ARMED_ID="$2"; ARMED_PRIOR="$3"; }
disarm(){ ARMED_DEV=""; ARMED_ID=""; ARMED_PRIOR=""; }

on_signal(){
  log ""; log "!! NGẮT — khôi phục frame đang lên nòng (nếu có) rồi thoát."
  [ -n "$ARMED_DEV" ] && [ "$ARMED_PRIOR" != "NA" ] && hal_set "$ARMED_DEV" "$ARMED_ID" "$ARMED_PRIOR" >/dev/null 2>&1
  exit 130
}
trap on_signal INT TERM

# observe: dừng cho người nhìn cụm/HUD; trả VERDICT; return 2 nếu skip-rest
observe(){
  log "   OBSERVE ▶ $1"
  if [ "$OBSERVE" != "manual" ]; then
    printf '   … giữ %ss — NHÌN CỤM/HUD …\n' "$OBSERVE"; [ "$DRY" = 1 ] || sleep "$OBSERVE"
    VERDICT="AUTO"; return 0
  fi
  local ans=""
  printf '   >> NHÌN CỤM/HUD rồi nhập  p=PASS  f=FAIL  b=BLOCKED  s=dừng-phần-còn-lại  [Enter=noted]: '
  read -r ans </dev/tty 2>/dev/null || ans=""
  case "$ans" in
    p|P) VERDICT=PASS;; f|F) VERDICT=FAIL;; b|B) VERDICT=BLOCKED;;
    s|S) VERDICT=SKIP; STOP_REQUESTED=1; return 2;; *) VERDICT=NOTED;;
  esac
  log "   VERDICT=$VERDICT"
}

# gác đầu mỗi ca: bỏ qua nếu đã khoá recovery hoặc operator xin dừng
gate(){
  [ "$RECOVERY_LOCK" = 1 ] && { log "   ⛔ RECOVERY_LOCK — bỏ qua $1"; return 1; }
  [ "$STOP_REQUESTED" = 1 ] && { log "   ⏭  STOP_REQUESTED — bỏ qua $1"; return 1; }
  return 0
}

# restore + verify 1 feature; set RECOVERY_LOCK nếu không khớp
restore_verify(){ # dev id prior
  local dev="$1" id="$2" prior="$3" got
  if [ "$prior" = "NA" ]; then log "   (không có prior để khôi phục — đã không ghi)"; disarm; return 0; fi
  hal_set "$dev" "$id" "$prior" >/dev/null
  got="$(hal_get "$dev" "$id")"
  if [ "$got" = "$prior" ]; then
    log "   ✅ RESTORE ok ($dev 0x$id → $prior, verify=$got)"; disarm; return 0
  fi
  log "   ❌ RESTORE FAIL ($dev 0x$id: muốn $prior, đọc lại $got) — KHOÁ, dừng mọi ca sau"
  RECOVERY_LOCK=1; return 1
}

# ── case types ────────────────────────────────────────────────────────────--

# nav render lên CỤM (broadcast Amap đã PROVEN 29/07) → observe → stop
nav_render(){ # desc
  gate "NAV-RENDER" || return 0
  hr; log "▶ [A] CLUSTER nav render (broadcast Amap, PROVEN 29/07) — $1"
  adb_sh "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 3 --ei SEG_REMAIN_DIS 444 --es NEXT_ROAD_NAME 'Ba Test Le Loi' --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300 --es SEG_REMAIN_DIS_AUTO '444 m' --es ROUTE_REMAIN_DIS_AUTO '6.0 km' --es ROUTE_REMAIN_TIME_AUTO '5 min' --es ROUTE_REMAIN_TIME_STRING '5 min'" >>"$LOG" 2>&1
  observe "CỤM: có icon rẽ + '444 m' + 'Ba Test Le Loi' không?"
  # stop/reset: khử cờ kẹt (true→false) + navopen close
  adb_sh "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10019 --ei EXTRA_STATE 9 --ez IS_BYD_MAP true"  >>"$LOG" 2>&1
  adb_sh "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10019 --ei EXTRA_STATE 9 --ez IS_BYD_MAP false" >>"$LOG" 2>&1
  navopen close >>"$LOG" 2>&1
  log "   ↩ nav stop gửi xong."
}

# navopen open+frame (đường HAL instrument trực tiếp, thay cho broadcast)
nav_frame(){
  gate "NAV-FRAME" || return 0
  hr; log "▶ [A2] CLUSTER nav qua navopen open+frame (HAL instrument trực tiếp)"
  navopen full "Le Loi Test" >>"$LOG" 2>&1
  observe "CỤM: làn nav zin có hiện icon + 'Le Loi Test' không?"
  navopen close >>"$LOG" 2>&1
  log "   ↩ navopen close xong."
}

# toggle on/off 1 feature: đọc prior(status) → set ON → observe → restore
toggle_case(){ # tag dev setid statusid onval offval desc
  local tag="$1" dev="$2" setid="$3" statusid="$4" onv="$5" offv="$6" desc="$7" prior
  gate "$tag" || return 0
  hr; log "▶ $tag — $desc"
  log "   set=0x$setid status=0x$statusid on=$onv off=$offv (dev=$dev)"
  prior="$(hal_get "$dev" "$statusid")"
  log "   prior(status)=$prior"
  if [ "$prior" = "NA" ]; then log "   ⛔ BLOCKED/NO_PRIOR — không ghi gì"; return 0; fi
  arm "$dev" "$setid" "$prior"
  hal_set "$dev" "$setid" "$onv" >/dev/null; log "   → set ON ($onv)"
  observe "$desc — có xuất hiện trên HUD/CỤM không?"
  restore_verify "$dev" "$setid" "$prior"
}

# value sequence: đọc prior(id) → set v1 → observe → set v2 → observe → restore
value_seq_case(){ # tag dev id v1 v2 desc
  local tag="$1" dev="$2" id="$3" v1="$4" v2="$5" desc="$6" prior
  gate "$tag" || return 0
  hr; log "▶ $tag — $desc"
  log "   id=0x$id seq: prior → $v1 → $v2 → prior (dev=$dev)"
  prior="$(hal_get "$dev" "$id")"
  log "   prior=$prior"
  if [ "$prior" = "NA" ]; then log "   ⛔ BLOCKED/NO_PRIOR — không ghi gì"; return 0; fi
  arm "$dev" "$id" "$prior"
  hal_set "$dev" "$id" "$v1" >/dev/null; log "   → set $v1"
  observe "$desc: CỤM hiện biển '$v1' km/h?"; [ "$STOP_REQUESTED" = 1 ] && { restore_verify "$dev" "$id" "$prior"; return 0; }
  hal_set "$dev" "$id" "$v2" >/dev/null; log "   → set $v2"
  observe "$desc: CỤM đổi sang '$v2' km/h?"
  restore_verify "$dev" "$id" "$prior"
}

# HUD combo: bật HUD master + 1 content-gate + bơm nav → nhìn KÍNH LÁI → restore
hud_combo_case(){ # tag gate_setid gate_statusid feednav desc
  local tag="$1" gsid="$2" gstat="$3" feednav="$4" desc="$5"
  local mprior gprior
  gate "$tag" || return 0
  hr; log "▶ $tag — $desc (bật HUD master + gate, đúng thứ tự 29/07 CHƯA thử)"
  mprior="$(hal_get setting 38B0001C)"      # HUD switch status
  gprior="$(hal_get setting "$gstat")"       # content-gate status
  log "   prior HUD-master=$mprior  gate=$gprior"
  [ "$mprior" = "NA" ] && { log "   ⛔ BLOCKED/NO_PRIOR (master) — không ghi"; return 0; }
  arm setting 4C10E023 "$mprior"
  hal_set setting 4C10E023 1 >/dev/null; log "   → HUD master ON"
  [ "$gprior" != "NA" ] && hal_set setting "$gsid" 1 >/dev/null && log "   → content-gate ON"
  if [ "$feednav" = "1" ]; then
    adb_sh "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 3 --ei SEG_REMAIN_DIS 444 --es NEXT_ROAD_NAME 'Ba Test Le Loi' --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300 --es SEG_REMAIN_DIS_AUTO '444 m' --es ROUTE_REMAIN_DIS_AUTO '6.0 km' --es ROUTE_REMAIN_TIME_AUTO '5 min' --es ROUTE_REMAIN_TIME_STRING '5 min'" >>"$LOG" 2>&1
    navopen full "Le Loi Test" >>"$LOG" 2>&1
  fi
  observe "KÍNH LÁI (HUD): có hiện $desc không? (cụm có thể có, HUD là câu hỏi)"
  # restore gate trước (verify), rồi master; nav stop
  if [ "$gprior" != "NA" ]; then
    hal_set setting "$gsid" "$gprior" >/dev/null
    [ "$(hal_get setting "$gstat")" = "$gprior" ] || { log "   ❌ RESTORE FAIL (gate 0x$gsid → $gprior) — KHOÁ"; RECOVERY_LOCK=1; }
  fi
  navopen close >>"$LOG" 2>&1
  restore_verify setting 4C10E023 "$mprior"
}

# ── preflight ────────────────────────────────────────────────────────────--
main(){
  : >"$LOG"
  hr; log "hud-cluster-probe  $TS   log=$LOG"
  [ -z "$SERIAL" ] && die "thiếu serial. Dùng: ./hud-cluster-probe.sh <serial|ip:port>"
  [ -f "$JAR_SRC" ] || die "không thấy navopen jar: $JAR_SRC (set JAR_SRC=...)"
  log "serial=$SERIAL  groups=[$SEL]  observe=$OBSERVE  dry=$DRY"
  log "jar=$JAR_SRC"

  if [ "${I_CONFIRM_PARKED:-0}" != "1" ] && [ "$DRY" != "1" ]; then
    printf '\n⚠  XÁC NHẬN: xe ĐỖ, số P, phanh tay, khu vực riêng, KHÔNG lái. Gõ YES để tiếp: '
    local c; read -r c </dev/tty; [ "$c" = "YES" ] || die "chưa xác nhận đỗ xe — dừng."
  fi

  # connect (nếu ip:port)
  case "$SERIAL" in *:*|[0-9]*.[0-9]*.[0-9]*.*) [ "$DRY" = 1 ] || adb connect "$SERIAL" | tee -a "$LOG";; esac
  [ "$DRY" = 1 ] || adb -s "$SERIAL" get-state >/dev/null 2>&1 || die "adb không thấy $SERIAL (get-state fail)"

  # push jar
  if [ "$DRY" = 1 ]; then log "[DRY] adb push $JAR_SRC $JAR_DST"; else
    adb -s "$SERIAL" push "$JAR_SRC" "$JAR_DST" | tee -a "$LOG" || die "push jar fail"
  fi

  # inventory (đọc-only): HAL cấp device nào + config HUD/SLA hiện tại
  hr; log "▶ PREFLIGHT (đọc-only)"
  navopen probe >>"$LOG" 2>&1 || true
  log "   HUD config (0=none 1=W 2=AR): $(hal_get setting 38B00015)"
  log "   HUD switch status (1=on 2=off): $(hal_get setting 38B0001C)"
  log "   ADAS SLA state (0=off 1=fusion…): $(hal_get adas 31600025)"
  log "   ADAS SLA output speed-limit (r): $(hal_get adas 2D500020)"
  log "   ISA current road speed-limit (r): $(hal_get statistic 4B40001C)"

  # ── GROUP A: CLUSTER nav render (sanity — đã proven) ──
  case " $SEL " in *" A "*)
    nav_render "sanity: rig hoạt động?"
    nav_frame
  ;; esac

  # ── GROUP B: CLUSTER speed-limit sign (câu hỏi MỞ chính) ──
  case " $SEL " in *" B "*)
    value_seq_case "B1-ISA-CUR"   statistic  4B40001C 50 80 "ISA current-road speed-limit VALUE"
    # type + value cùng nhau (type có thể cần để vẽ biển)
    if gate "B2-ISA-TYPE"; then
      hr; log "▶ B2 — ISA type(0x4B400034)=1 kèm value(0x4B40001C)"
      tp="$(hal_get statistic 4B400034)"; vp="$(hal_get statistic 4B40001C)"
      log "   prior type=$tp value=$vp"
      if [ "$vp" != "NA" ]; then
        arm statistic 4B40001C "$vp"
        [ "$tp" != "NA" ] && hal_set statistic 4B400034 1 >/dev/null
        hal_set statistic 4B40001C 60 >/dev/null; log "   → type=1 value=60"
        observe "CỤM: biển '60' có vẽ ra khi kèm type không?"
        if [ "$tp" != "NA" ]; then
          hal_set statistic 4B400034 "$tp" >/dev/null
          [ "$(hal_get statistic 4B400034)" = "$tp" ] || { log "   ❌ RESTORE FAIL (type 0x4B400034 → $tp) — KHOÁ"; RECOVERY_LOCK=1; }
        fi
        restore_verify statistic 4B40001C "$vp"
      else log "   ⛔ BLOCKED/NO_PRIOR"; fi
    fi
    value_seq_case "B3-ISA-TSTYPE" statistic 4B400064 1 5  "ISA traffic-sign-type"
    value_seq_case "B4-INSTR-TSR"  instr     23A00010 50 80 "INSTRUMENT traffic-sign-identify VALUE"
    value_seq_case "B6-SETTING-S5" setting   4B4000AA 50 80 "Setting S5 sign value"
    toggle_case    "B7-SLA-STATE"  adas      38500022 31600025 1 0 "ADAS SLA/TSR bật (on/off, đọc output 0x2D500020)"
  ;; esac

  # ── GROUP C: HUD nav guidance + road name (kính lái) ──
  case " $SEL " in *" C "*)
    hud_combo_case "C1-HUD-NAV"  4C10E03A 38B00028 1 "dẫn đường + tên đường trên HUD"
    hud_combo_case "C2-HUD-ADAS" 4C10E030 38B0001E 0 "icon ADAS/an-toàn trên HUD"
    toggle_case    "C3-INSTR-HUDMAP" instr 32B1102E 38B0002E 2 1 "INSTRUMENT HUD nav-map (H1)"
  ;; esac

  # ── GROUP D: HUD speed-sign / AR-HUD ──
  case " $SEL " in *" D "*)
    hr; log "▶ D preflight ARHUD/HUD-system (đọc-only)"
    log "   ADAS ACC_MODE_ARHUD(r): $(hal_get adas 29C0000C)"
    log "   ADAS HUD_SYSTEM_STATUS(r): $(hal_get adas 17F00008)"
    toggle_case "D1-SMART-SLC" adas 32B0E018 1FF02012 1 0 "ADAS smart-speed-limit-control (biển tốc độ HUD?)"
  ;; esac

  # ── GROUP E: APP speed-limit badge overlay on cluster (display 1) + HAL write ──
  case " $SEL " in *" E "*)
    hr; log "▶ E preflight: app installed + overlay"
    local ver
    ver="$(adb_sh "dumpsys package com.byd.clusternav2 | grep versionName" 2>/dev/null | awk -F= '{print $2}' | tr -d '[:space:]')"
    if [ -z "$ver" ]; then
      log "   ⛔ com.byd.clusternav2 NOT INSTALLED — skip Group E"; STOP_REQUESTED=0
    else
      log "   app version=$ver"
      # E1: overlay window present on display 1?
      if gate "E1-OVERLAY"; then
        hr; log "▶ E1 — overlay window on display 1"
        local wdump
        wdump="$(adb_sh "dumpsys window windows | grep -i speedbadge" 2>/dev/null)"
        if [ -n "$wdump" ]; then log "   ✅ overlay found: $wdump"
        else log "   ⚠ overlay NOT found in window dump (app may need Nav+HUD enabled)"; fi
      fi
      # E2: baseline ISA read
      if gate "E2-ISA-BASELINE"; then
        hr; log "▶ E2 — ISA baseline read"
        local isa_before
        isa_before="$(hal_get statistic 4B40001C)"
        log "   ISA 0x4B40001C baseline=$isa_before"
      fi
      # E3: operator observe (drive past speed sign with VietMap/Waze active)
      if gate "E3-DRIVE-OBSERVE"; then
        hr; log "▶ E3 — operator: mở VietMap/Waze, chạy qua biển tốc độ, NHÌN CỤM (display 1)"
        observe "CỤM (display 1): có badge tốc độ (vòng đỏ + số) ở góc trên-phải không?"
      fi
      # E4: ISA read-back after app write
      if gate "E4-ISA-READBACK"; then
        hr; log "▶ E4 — ISA read-back after app write"
        local isa_after
        isa_after="$(hal_get statistic 4B40001C)"
        log "   ISA 0x4B40001C after=$isa_after (baseline was ${isa_before:-?})"
        if [ "${isa_after:-NA}" != "NA" ] && [ "${isa_after:-}" != "${isa_before:-}" ]; then
          log "   ✅ ISA CHANGED — HalSpeedSignPort wrote successfully"
        else
          log "   ⚠ ISA unchanged or unreadable — check app logs"
        fi
      fi
    fi
  ;; esac

  hr
  if [ "$RECOVERY_LOCK" = 1 ]; then
    log "KẾT THÚC: ⚠ RECOVERY_LOCK BẬT — có restore thất bại. Kiểm tra cụm/HUD, reboot xe nếu cần. Log: $LOG"
  else
    log "KẾT THÚC: mọi ca đã restore. Xem verdict trong $LOG"
  fi
}
main "$@"
