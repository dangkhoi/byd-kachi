# FIX286 R-HUD — đo trên máy ảo emulator-5554 (chỉ máy ảo). source file này rồi gọi hàm.
D=${D:?đặt D = thư mục chứa adbw (bọc adb -s emulator-5554)}
A=$D/adbw
H=$D/hud
PKG=com.byd.launcher
C=$PKG/com.byd.clusternav.NavNotificationListener
HOME_ACT=$PKG/com.byd.clusternav.launcher.KachiHomeActivity
MIRROR="com.google.android.apps.nexuslauncher/com.android.launcher3.notification.NotificationListener:${C}:com.google.android.apps.restore/com.google.android.apps.pixelmigrate.component.NotificationConsolidatorService"

live() { $A shell dumpsys notification p $PKG | grep -A1 "Live notification listeners" | grep -c "ComponentInfo{$C}"; }
mirror_has() { $A shell settings get secure enabled_notification_listeners | tr -d '\r' | tr ':' '\n' | grep -cx "$C"; }
# Giả lập "đã cấp mà KHÔNG gắn": NMS gỡ duyệt (disallow) rồi trả bản gương Settings về như cũ (KHÔNG rebind).
# §4: display — không; app — chỉ component của Kachi; loại — một mục duyệt bộ nghe; hoàn tác — allow_listener ($C).
# NMS ghi bản gương BẤT ĐỒNG BỘ sau disallow (handleSavePolicyFile → writeXml) ⇒ chờ nó ghi xong rồi mới đặt lại, kiểm lại.
break_nls() {
  $A shell cmd notification disallow_listener $C; sleep 6
  for i in 1 2 3; do $A shell settings put secure enabled_notification_listeners "$MIRROR"; sleep 2; [ "$(mirror_has)" = "1" ] && break; done
}
restore_nls() { $A shell cmd notification allow_listener $C; sleep 3; }
# Đặt công tắc Dẫn đường (khoá 'enabled' trong clusternav_prefs.xml) khi app ĐÃ dừng.
set_enabled() {
  # MỘT lệnh shell: force-stop → sửa → force-stop lại (HOME có thể tự bật trong khe đó và ghi đè map cũ).
  $A shell "am force-stop $PKG; cd /data/data/$PKG/shared_prefs && sed -i '/<boolean name=\"enabled\" /d' clusternav_prefs.xml && sed -i 's#</map>#    <boolean name=\"enabled\" value=\"$1\" />\n</map>#' clusternav_prefs.xml && chown 10163:10163 clusternav_prefs.xml && chmod 660 clusternav_prefs.xml && restorecon clusternav_prefs.xml; am force-stop $PKG; grep -c 'name=\"enabled\" value=\"$1\"' clusternav_prefs.xml"
}
restart_home() { $A shell am force-stop $PKG; sleep 1; $A shell am start -n $HOME_ACT >/dev/null; }
logs() { $A logcat -d -v threadtime -s NlsHeal:V NavListener:V NavConnect:V NotificationListeners:V KachiReady:V NavRebind:V; }
watchdog() { $A shell am broadcast -a com.byd.clusternav.REBIND_WATCHDOG -n $PKG/com.byd.clusternav.RebindReceiver; }
# Lượt giết kiểu BYD (không PACKAGE_RESTARTED ⇒ NMS không rebindServices, bản gương không bị ghi lại); HOME tự dựng lại.
kill_like_byd() { local p; p=$($A shell pidof $PKG | tr -d '\r' | awk '{print $1}'); $A shell kill -9 $p; echo "killed pid=$p"; }
pid() { $A shell pidof $PKG | tr -d '\r'; }
# Giữ bản gương "đã cấp" trong N giây (NMS ghi đè nó mỗi lần lưu chính sách — vd tiến trình mới tạo kênh thông báo).
hold_mirror() { local n=$1 i=0; while [ $i -lt $n ]; do $A shell settings put secure enabled_notification_listeners "$MIRROR"; sleep 1; i=$((i+1)); done; }
# Bản nhanh: ghi lại bản gương liên tục (không ngủ) trong N lượt — che khe Preflight đọc lúc tiến trình vừa bật.
hold_mirror_fast() { local n=$1 i=0; while [ $i -lt $n ]; do $A shell settings put secure enabled_notification_listeners "$MIRROR"; i=$((i+1)); done; }
# Trên xe Android dựng lại HOME 0,25–0,33 s sau lượt giết (hud-inv §2). Máy ảo: app khách của ô rơi về display 0 và
# che HOME ⇒ HOME không tự dựng ⇒ gọi HOME tường minh ngay sau lượt giết (cùng tác dụng).
kill_and_home() { kill_like_byd; $A shell am start -n $HOME_ACT >/dev/null 2>&1; }
