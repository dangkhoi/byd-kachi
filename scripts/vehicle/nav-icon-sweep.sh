#!/usr/bin/env bash
# nav-icon-sweep.sh — Bắn LẦN LƯỢT mọi mã icon dẫn đường lên xe, DỪNG sau mỗi mã để nhìn/chụp, tự
#   screencap CỤM (display 0) + ghi mô tả glyph vào master TSV. Dựng bảng glyph THẬT của Seal trong 1 buổi
#   (giải quyết 13-vs-15, 20-vs-24, số nhánh vòng xuyến, tunnel/service/toll/waypoint…) thay vì chạy xe mò.
#
#   3 phần:
#     CAN  — HUD kính lái + cụm-CENTRE "Giữa+ETA" (CÙNG feature INSTRUMENT_GUIDE_INFO_SIMPLE, bảng CAN 1..49).
#            Ghi cả 2 họ: domestic 0x43F01010 + oversea 0x1F701010.
#     AMAP — làn cụm STRIP (broadcast AUTONAVI NEW_ICON, bảng AMAP 0..28).
#     RAB  — vòng xuyến lối-ra-N: strip (NEW_ICON 11 + ROUNG_ABOUT_NUM=N) + HUD (CAN 24+N).
#
#   ⚠ screencap CHỈ chụp được CỤM (display 0). HUD KÍNH là màn riêng → đọc bằng MẮT / chụp ảnh tay.
#   ⚠ PARKED only (P + phanh tay). Reboot xe xoá sạch mọi thứ script để lại.
#
# Dùng:
#   VEH=<ip:port> bash scripts/vehicle/nav-icon-sweep.sh                  # full, dừng mỗi mã (gõ mô tả + Enter)
#   VEH=<ip:port> SECTION=can  ...                                        # chỉ CAN (HUD/centre)
#   VEH=<ip:port> SECTION=amap ...                                        # chỉ strip
#   VEH=<ip:port> SECTION=rab  ...                                        # chỉ vòng xuyến lối ra
#   VEH=<ip:port> START=13 SECTION=can ...                                # bắt đầu từ mã 13 (resume)
#   VEH=<ip:port> NONSTOP=1 DELAY=3 ...                                   # không dừng, cách 3s (chụp cụm auto, review sau)
#   NAVOPEN_JAR=<path navopen.jar>                                        # nếu chưa có sẵn ở /data/local/tmp/navopen.jar
#
# Master data: OUT/results.tsv  (type<TAB>code<TAB>feature<TAB>screencap<TAB>note). Gửi file này + ảnh cho agent
# để gộp vào docs/diagnostics/nav-icon-mapping (bảng glyph xác thực của Seal).
set -u
ADB="${ADB:-adb}"; S="${VEH:-}"; OUT="${OUT:-./nav-icon-sweep}"; SECTION="${SECTION:-all}"
START="${START:-0}"; NONSTOP="${NONSTOP:-0}"; DELAY="${DELAY:-3}"; DIST="${DIST:-500}"; ROAD="${ROAD:-Test Le Loi}"
CAN_MIN="${CAN_MIN:-1}"; CAN_MAX="${CAN_MAX:-49}"; AMAP_MIN="${AMAP_MIN:-0}"; AMAP_MAX="${AMAP_MAX:-28}"
JAR_LOCAL="${NAVOPEN_JAR:-}"

command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
[ -x "$ADB" ] || command -v "$ADB" >/dev/null 2>&1 || { echo "FATAL: adb not found (set ADB=...)"; exit 1; }
[ -n "$S" ] || S="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
[ -n "$S" ] || { echo "FATAL: no adb device (set VEH=ip:port)"; exit 1; }
mkdir -p "$OUT"; TSV="$OUT/results.tsv"
[ -f "$TSV" ] || printf 'type\tcode\tfeature\tscreencap\tnote\n' > "$TSV"

# ── feature ids (RE DiCarServer Instrument.java) — hex, KHÔNG 0x (đúng cú pháp navopen setraw) ──
STATUS=43E0003A          # INSTRUMENT_SEND_NAVI_STATUS_SET  (=2 active / =4 clear)
SCREEN=4C10E015          # SET_NAVI_SCREEN_STATUS_SET (setting) — chế độ nav-screen cụm
GUIDE_DOM=43F01010; GUIDE_OVR=1F701010     # mũi tên (CAN turn-id) domestic / oversea
DIST_DOM=43F01018;  DIST_OVR=1F701018      # cự ly domestic / oversea

NAVCP="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
nav(){ timeout 20 "$ADB" -s "$S" shell "$NAVCP $*" >/dev/null 2>&1; }
sh_(){ timeout 20 "$ADB" -s "$S" shell "$*" >/dev/null 2>&1; }
shot(){ sh_ "fission_screencap -d 0 -p /data/local/tmp/p.png"; timeout 20 "$ADB" -s "$S" pull /data/local/tmp/p.png "$OUT/$1" >/dev/null 2>&1; }

# Push navopen nếu có jar local; else giả định đã ở /data/local/tmp/navopen.jar (theo RE để lại trên xe).
if [ -z "$JAR_LOCAL" ]; then for c in apks/navopen-v4.jar apks/navopen-v3.jar "$(dirname "$0")/../../apks/navopen-v4.jar"; do [ -f "$c" ] && { JAR_LOCAL="$c"; break; }; done; fi
if [ -n "$JAR_LOCAL" ] && [ -f "$JAR_LOCAL" ]; then "$ADB" -s "$S" push "$JAR_LOCAL" /data/local/tmp/navopen.jar >/dev/null 2>&1 && echo "pushed navopen ($JAR_LOCAL)"; else echo "note: giả định navopen.jar đã ở /data/local/tmp/ trên xe"; fi
echo "device=$S  section=$SECTION  out=$OUT  (Ctrl-C để dừng; reboot xe để xoá sạch)"

activate(){ nav setraw instr "$STATUS" 2; nav setraw setting "$SCREEN" 3; }
clear_nav(){ nav setraw instr "$STATUS" 4; }

# capture: chụp cụm + log + dừng chờ mô tả. return 2 = bắn lại.
capture(){ # $1=type $2=code $3=feature
  local png="$1_$2.png"; shot "$png"
  if [ "$NONSTOP" = 1 ]; then printf '%s\t%s\t%s\t%s\t\n' "$1" "$2" "$3" "$png" >> "$TSV"; sleep "$DELAY"; return 0; fi
  printf '\n──> [%s %s] đã bắn (%s). Nhìn HUD KÍNH + CỤM (đã chụp cụm: %s)\n' "$1" "$2" "$3" "$OUT/$png"
  printf '    Mô tả glyph rồi Enter | Enter trống=bỏ trống | r=bắn lại | s=bỏ qua | q=thoát: '
  read -r note </dev/tty
  case "$note" in q|Q) clear_nav; echo "đã thoát; kết quả ở $TSV"; exit 0;; r|R) return 2;; s|S) return 0;; esac
  printf '%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$png" "$note" >> "$TSV"
  return 0
}

fire_can(){ # $1=can code
  activate
  nav setraw instr "$GUIDE_DOM" "$1"; nav setraw instr "$GUIDE_OVR" "$1"
  nav setraw instr "$DIST_DOM" "$DIST"; nav setraw instr "$DIST_OVR" "$DIST"
}
fire_amap(){ # $1=amap new_icon  [$2=roundabout num]
  local extra=""; [ "${2:-0}" -gt 0 ] 2>/dev/null && extra="--ei ROUNG_ABOUT_NUM $2"
  sh_ "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 \
       --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON $1 $extra \
       --ei SEG_REMAIN_DIS $DIST --es NEXT_ROAD_NAME '$ROAD' --es SEG_REMAIN_DIS_AUTO '$DIST m' \
       --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300 --es ROUTE_REMAIN_DIS_AUTO '6.0 km' \
       --es ROUTE_REMAIN_TIME_AUTO '5 min' --es ROUTE_REMAIN_TIME_STRING '5 min'"
}

run_can(){ echo "== PHẦN CAN — HUD kính + cụm-centre (GUIDE_INFO_SIMPLE, bảng CAN $CAN_MIN..$CAN_MAX) =="
  for c in $(seq "$CAN_MIN" "$CAN_MAX"); do [ "$c" -lt "$START" ] && continue
    while :; do fire_can "$c"; capture CAN "$c" "GUIDE_INFO_SIMPLE dom+ovr"; [ $? -eq 2 ] || break; done
  done; }
run_amap(){ echo "== PHẦN AMAP — làn cụm strip (broadcast NEW_ICON $AMAP_MIN..$AMAP_MAX) =="
  for a in $(seq "$AMAP_MIN" "$AMAP_MAX"); do [ "$a" -lt "$START" ] && continue
    while :; do fire_amap "$a"; capture AMAP "$a" "broadcast NEW_ICON"; [ $? -eq 2 ] || break; done
  done; }
run_rab(){ echo "== PHẦN RAB — vòng xuyến lối ra N (strip 11+NUM  &  HUD CAN 24+N) =="
  for n in $(seq 1 10); do [ "$n" -lt "$START" ] && continue
    while :; do fire_amap 11 "$n"; fire_can $((24+n)); capture RAB_EXIT "$n" "strip NEW_ICON11+NUM=$n / HUD CAN $((24+n))"; [ $? -eq 2 ] || break; done
  done; }

case "$SECTION" in
  can)  run_can ;;
  amap) run_amap ;;
  rab)  run_rab ;;
  all)  run_can; run_amap; run_rab ;;
  *) echo "SECTION=can|amap|rab|all"; exit 1 ;;
esac

clear_nav
echo ""; echo "XONG. Master data: $TSV  (+ ảnh cụm trong $OUT/). Gửi cho agent để gộp vào bảng glyph Seal."
echo "Reboot xe (nút nguồn) để xoá sạch nav test."
