# FIX286 R-KC — E2E máy ảo (CHỈ emulator-5554). source file này (bash).
SERIAL=emulator-5554
case "$SERIAL" in emulator-*) ;; *) echo "chỉ chạy trên máy ảo" >&2; return 1;; esac
PKG=com.byd.launcher
HOME_ACT=$PKG/com.byd.clusternav.launcher.KachiHomeActivity
A11Y=$PKG/com.byd.clusternav.modules.navaccess.NavAccessibilityService
SP=<scratchpad>
K=$SP/lane2-286/kc
REPO=<home>/Documents/workspaces/experiments/byd/byd-launcher
DD=/data/data/$PKG
ADB=$HOME/Library/Android/sdk/platform-tools/adb
a() { "$ADB" -s "$SERIAL" "$@" </dev/null; }
ain() { "$ADB" -s "$SERIAL" "$@"; }
fixperm() { a shell "chown 10163:10163 $1; chmod 660 $1; restorecon $1"; }
put_prefs() {
  a push "$2" /data/local/tmp/kc-put.xml >/dev/null
  a shell "am force-stop $PKG; cp /data/local/tmp/kc-put.xml $DD/shared_prefs/$1.xml; rm /data/local/tmp/kc-put.xml; chown 10163:10163 $DD/shared_prefs/$1.xml; chmod 660 $DD/shared_prefs/$1.xml; restorecon $DD/shared_prefs/$1.xml; am force-stop $PKG"
}
cat_prefs() { a shell "cat $DD/shared_prefs/$1.xml" | tr -d '\r'; }
backup() {
  mkdir -p $K/backup
  a shell "cd $DD && tar cf /data/local/tmp/kc-prefs.tar shared_prefs files/diag 2>/dev/null; ls -la /data/local/tmp/kc-prefs.tar"
  a pull /data/local/tmp/kc-prefs.tar $K/backup/prefs.tar >/dev/null
  a shell "rm -f /data/local/tmp/kc-prefs.tar"
  a shell "cd $DD && sha256sum shared_prefs/*" | tr -d '\r' | sort > $K/backup/prefs.sha
  a shell settings get secure enabled_accessibility_services | tr -d '\r' > $K/backup/a11y.before
  a shell settings get secure accessibility_enabled | tr -d '\r' > $K/backup/a11y_enabled.before
  echo "backup: $(tar tf $K/backup/prefs.tar | wc -l) mục · $(wc -l < $K/backup/prefs.sha) prefs · a11y=$(cat $K/backup/a11y.before)"
}
restore() {
  a push $K/backup/prefs.tar /data/local/tmp/kc-prefs.tar >/dev/null
  a shell "am force-stop $PKG; cd $DD && tar xf /data/local/tmp/kc-prefs.tar; rm -f $DD/shared_prefs/kachi_test_bridge.xml; chown -R 10163:10163 $DD/shared_prefs $DD/files/diag; chmod 660 $DD/shared_prefs/*.xml; restorecon -R $DD/shared_prefs $DD/files/diag; am force-stop $PKG; rm -f /data/local/tmp/kc-prefs.tar"
  a shell settings put secure enabled_accessibility_services "$(cat $K/backup/a11y.before)"
  a shell settings put secure accessibility_enabled "$(cat $K/backup/a11y_enabled.before)"
  a shell "cd $DD && sha256sum shared_prefs/*" | tr -d '\r' | sort > $K/backup/prefs-after.sha
  if diff $K/backup/prefs.sha $K/backup/prefs-after.sha >/dev/null; then echo "restore: sha prefs khớp $(wc -l < $K/backup/prefs.sha)/$(wc -l < $K/backup/prefs-after.sha)"; else echo "restore: LỆCH"; diff $K/backup/prefs.sha $K/backup/prefs-after.sha; fi
}
bridge_on() {
  local boot up until_ms
  boot="$(a shell cat /proc/sys/kernel/random/boot_id | tr -d '\r\n')"
  up="$(a shell cat /proc/uptime | tr -d '\r' | cut -d' ' -f1)"
  until_ms="$(python3 -c "import sys;print(int(float(sys.argv[1])*1000)+3600000-120000)" "$up")"
  printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"test_bridge_until\">%s:%s</string>\n</map>\n" "$boot" "$until_ms" > "$K/tb.xml"
  put_prefs kachi_test_bridge "$K/tb.xml"
}
home() { a shell am start -n "$HOME_ACT" >/dev/null; }
br() { a shell "am broadcast -a $PKG.TEST -p $PKG $*" | python3 $REPO/scripts/emulator/voice_e2e_json.py extract; }
shot() { a exec-out screencap -p > "$K/shots/$1.png"; }
ui() { a shell uiautomator dump /sdcard/kc-ui.xml >/dev/null 2>&1; a exec-out cat /sdcard/kc-ui.xml > "$K/shots/$1.xml"; }
tap_text() {
  local xy
  xy="$(python3 - "$K/shots/$1.xml" "$2" "${3:-0}" <<'PY'
import sys,re,html
s=open(sys.argv[1]).read(); want=sys.argv[2]; idx=int(sys.argv[3]); n=0
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',s):
    t=html.unescape(m.group(1)); d=html.unescape(m.group(2))
    if want in t or want in d:
        if n==idx:
            x1,y1,x2,y2=map(int,m.groups()[2:]); print((x1+x2)//2,(y1+y2)//2); break
        n+=1
PY
)"
  [ -z "$xy" ] && { echo "KHÔNG thấy '$2' trên màn ($1)"; return 1; }
  a shell input tap $xy; echo "tap '$2' @ $xy"
}
texts() { python3 - "$K/shots/$1.xml" <<'PY'
import sys,re,html
s=open(sys.argv[1]).read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="([^"]*)"',s):
    t=html.unescape(m.group(1)); d=html.unescape(m.group(2))
    if t or d: print(repr(t), '|', repr(d), m.group(3))
PY
}
# Phím THẬT qua thiết bị nhập (root): `input keyevent` là sự kiện TIÊM — không qua bộ lọc phím của Hỗ trợ
# ([ĐO máy ảo 02/10] D-VK6). key <linux-code>
kdev() { a shell getevent -pl 2>/dev/null | tr -d '\r' | python3 -c "
import sys,re
dev=None;name=None;out=None
for l in sys.stdin:
    m=re.match(r'add device \d+: (\S+)',l)
    if m: dev=m.group(1); continue
    m=re.match(r'\s+name:\s+\"(.*)\"',l)
    if m and m.group(1)=='qwerty2': out=dev
print(out or '')
"; }
key() { local d="${KDEV:-/dev/input/event11}"; a shell "sendevent $d 1 $1 1; sendevent $d 0 0 0; sendevent $d 1 $1 0; sendevent $d 0 0 0"; }
lc_clear() { a logcat -c; }
lc_dump() { a logcat -d -v time -s KeyCtl:* NavAccess:* VoiceKeyLauncher:* KachiCtl:* CtlJournal:* AndroidRuntime:E > "$1"; }
# force-stop/cài lại gỡ bind Hỗ trợ; đặt lại khoá (rỗng rồi lại, GIỮ dịch vụ khác) để hệ thống bind lại.
a11y_rebind() {
  local before; before="$(cat $K/backup/a11y.before)"
  a shell settings put secure enabled_accessibility_services '""' >/dev/null
  a shell settings put secure enabled_accessibility_services "$before"
  a shell settings put secure accessibility_enabled 1
  for i in $(seq 1 40); do a logcat -d -s 'NavAccess:*' | grep -q "booster connected" && return 0; sleep 0.5; done
  echo "a11y chưa bind lại" >&2; return 1
}
# tap_exact <xml-name> <text ĐÚNG NGUYÊN VĂN> [chỉ số]
tap_exact() {
  local xy
  xy="$(python3 - "$K/shots/$1.xml" "$2" "${3:-0}" <<'PY'
import sys,re,html
s=open(sys.argv[1]).read(); want=sys.argv[2]; idx=int(sys.argv[3]); n=0
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',s):
    t=html.unescape(m.group(1)); d=html.unescape(m.group(2))
    if want == t or want == d:
        if n==idx:
            x1,y1,x2,y2=map(int,m.groups()[2:]); print((x1+x2)//2,(y1+y2)//2); break
        n+=1
PY
)"
  [ -z "$xy" ] && { echo "KHÔNG thấy ĐÚNG '$2' trên màn ($1)"; return 1; }
  a shell input tap $xy; echo "tap= '$2' @ $xy"
}
vol() { a shell dumpsys audio | tr -d '\r' | grep -A6 "STREAM_MUSIC:" | grep -m1 "streamVolume\|Current" ; }
