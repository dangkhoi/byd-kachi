#!/usr/bin/env bash
# hud-provisioning-compare.sh — READ-ONLY: so sánh provisioning HUD-nav giữa 2 xe BYD.
# Mục tiêu: tìm vì sao HUD kính lên nav ở xe này mà không lên ở xe kia (nghi cờ coding cụm→HUD).
# AN TOÀN: chỉ ĐỌC (getraw/getprop/dumpsys/logcat). KHÔNG ghi gì thay đổi trạng thái xe.
#
# CÁCH DÙNG (trên máy có adb, đã nối xe qua hotspot):
#   1) Push navopen 1 lần:  adb -s <ip>:5555 push navopen-v4.jar /data/local/tmp/navopen.jar
#   2) Chạy:  VEH=<ip>:5555 bash hud-provisioning-compare.sh 2>&1 | tee hud-compare-<ten-xe>.txt
#   3) Gửi lại file .txt.
#   * Để mục [4] có ý nghĩa: đang MỞ GMaps dẫn đường + bật Nav+HUD + HUD đang hiện nav thì hãy chạy.
set -u
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"; command -v "$ADB" >/dev/null 2>&1 || ADB=adb
VEH="${VEH:?Dat VEH=ip:5555}"; J=/data/local/tmp/navopen.jar
"$ADB" connect "$VEH" >/dev/null 2>&1
say(){ echo; echo "===== $* ====="; }
# getraw 1 feature: G <instr|setting|adas|statistic> <hexid>
G(){ "$ADB" -s "$VEH" shell "CLASSPATH=$J app_process /system/bin com.byd.navopen.NavOpen getraw $1 $2" 2>&1 \
     | grep -oiE 'get (instr|setting|adas|statistic) \(0x[0-9a-f]+\) = -?[0-9]+' | tr -d '\r' | tail -1; }

echo "### HUD PROVISIONING COMPARE · VEH=$VEH · $(date '+%F %T')"
echo "### rc/read: 0..N = giá trị thật provisioned · -10011 = write-only · -2147482648 = NOT provisioned/no-permission"

say "0. DEVICE / VARIANT / REGION (so model+market 2 xe)"
"$ADB" -s "$VEH" shell getprop 2>/dev/null | grep -iE 'ro\.product|ro\.build\.(fingerprint|type|display|version\.release)|region|country|market|locale|byd|vehicle|carconfig|dilink|vin|hud|navi' | tr -d '\r'
say "0b. App clusternav version"
"$ADB" -s "$VEH" shell dumpsys package com.byd.clusternav 2>/dev/null | grep -E 'versionName|versionCode' | tr -d '\r'
say "0c. navopen co san khong"
"$ADB" -s "$VEH" shell "ls -l $J 2>/dev/null || echo MISSING-navopen(push truoc)" | tr -d '\r'
say "0d. Cac package HUD/AMAP/navi"
"$ADB" -s "$VEH" shell "pm list packages 2>/dev/null | grep -iE 'hud|amap|navi|cluster|byd'" | tr -d '\r'

say "1. *** CO NAV-HUD MIRROR (cai quyet dinh) *** — owner Seal doc ra khong-provisioned"
echo "38B00030 HUD_NAV_MAP_CONFIG (ky vong xe co HUD nav = 1): $(G instr 38B00030)"
echo "38B0002E HUD_NAV_MAP_STATUS                            : $(G instr 38B0002E)"
echo "38B00015 HUD_CONFIG 0=none/1=W/2=AR (setting)          : $(G setting 38B00015)"
echo "38B0001C HUD_SWITCH_STATUS (setting)                   : $(G setting 38B0001C)"
echo "38B00028 HUD_NAV_CONTENT_STATUS (setting)              : $(G setting 38B00028)"
echo "38B0001E HUD_ADAS_STATUS (setting)                     : $(G setting 38B0001E)"

say "2. HO GUIDE OVERSEA 0x1F7 (owner Seal = REJECTED device 1007)"
for id in 1F701010 1F701018 1F704010 1F7A1008 1F702010 1F705018; do echo "$id: $(G instr $id)"; done
say "2b. HO GUIDE DOMESTIC 0x43F (owner: 43F01010/018 OK; 43F01030 rejected)"
for id in 43F01010 43F01018 43F01030 43FA1008 43F02018; do echo "$id: $(G instr $id)"; done
say "2c. Nav-type / screen state"
echo "40C03032 NAVI_TYPE(instr): $(G instr 40C03032)  |  4C10E015 NAVI_SCREEN(setting): $(G setting 4C10E015)"

say "3. BYDAuto DEVICE CODES + permission (so ma device vs owner=1007)"
"$ADB" -s "$VEH" logcat -d 2>/dev/null | grep -iE 'BYDAutoDeviceManager: addDevice|no permission to use|with this device' | tr -d '\r' | tail -30

say "4. *** LOGCAT lúc GMaps dẫn + HUD đang hiện (quyết định feature nào nuôi HUD) ***"
echo "-- clear + doi 12s (owner giu GMaps dan + Nav+HUD ON) --"
"$ADB" -s "$VEH" logcat -c 2>/dev/null; sleep 12
echo "[A] Feature app GHI THANH CONG + gia tri (distinct):"
"$ADB" -s "$VEH" logcat -d 2>/dev/null | grep -oiE 'set featureId is [0-9a-f]+ (intValue is -?[0-9]+|bufferData)' | tr 'A-F' 'a-f' | sort | uniq -c | sort -rn | head -30
echo "[B] Feature BI TU CHOI (no permission) + device code:"
"$ADB" -s "$VEH" logcat -d 2>/dev/null | grep -iE 'no permission to use|with this device' | tr -d '\r' | sort -u | head -15
echo "[C] SDK guidance method + NavigationHudOwner rc:"
"$ADB" -s "$VEH" logcat -d 2>/dev/null | grep -iE 'sendSimpleGuidance|sendNextPath|NavigationHudOwner|GuideInfo.naviState' | tr -d '\r' | tail -8

say "DONE — gui lai file .txt nay"
