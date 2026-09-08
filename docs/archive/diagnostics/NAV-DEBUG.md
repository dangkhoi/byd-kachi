# ClusterNav — Debug "cluster nav đơn giản KHÔNG lên"

> Triệu chứng (2026-07-14): làn nav zin trên cụm — **km + mũi tên + tên đường** — không hiện, không chỉ HUD.
> Đây là tính năng LÕI. Bộ này chẩn đoán gãy chặng nào **mà không cài lại app**.

## ⚠️ 3 luật vàng khi debug
1. **KHÔNG uninstall/reinstall APK.** Cài lại rớt quyền notification = chính nghi phạm #1. Dùng bản đang có sẵn trên xe.
2. **KHÔNG `am force-stop`.** Kill process có thể phá binding listener trên firmware BYD (nó bỏ qua requestRebind).
3. **Frame giả luôn dùng `--es byd false`.** byd=true đi đường mIsBYDMapNaving → KHÔNG render trên xe này. false = recipe đã chứng minh 2026-06-23.

## 3 chặng của đường nav (audit code đã xác nhận Chặng 2 còn nguyên, không phải regression)
```
Chặng 1  NavNotificationListener  đọc noti GMaps      cần: BOUND + Prefs.enabled(mặc định ON)
Chặng 2  ClusterBroadcaster.emit  bắn AUTONAVI bcast   OK — send() vô điều kiện, tự re-arm sau stop()
Chặng 3  AmapService (firmware)   vẽ làn nav trên cụm  cần: cờ mIsBYDMapNaving không kẹt + profile đúng
```

## Chạy (2 terminal)
```
# Terminal #1 — để yên nhìn log:
pwsh docs/diagnostics/nav-log.ps1 -Wifi

# Terminal #2 — bisect có hướng dẫn:
pwsh docs/diagnostics/nav-debug.ps1 -Wifi
```
Chạy lẻ: `nav-debug.ps1 -Step check|fix|render|clean`.

## Cây quyết định
```
CHECK (bơm frame giả (1) '250m Nguyễn Huệ' + đọc 'notif-listener bật:')
│
├─ notif-listener = false ───────────────► GT#1  quyền rớt (cài lại APK)      → FIX (allow_listener)
│
├─ true & frame(1) HIỆN trên cụm ────────► Chặng 2+3 OK, lỗi ở Chặng 1
│     └─ dẫn GMaps: có 'NavListener: nav dist=..'?
│           ├─ không ─────────────────────► GT#2  granted-nhưng-không-bound   → FIX (disallow→allow)
│           └─ có ────────────────────────► listener sống + bcast vẽ → nav phải lên (nếu không, gửi log)
│
└─ frame(1) KHÔNG hiện ──────────────────► Chặng 3 nghi → RENDER (etabroadcast reset cờ)
      └─ frame(2) '500m Nguyễn Huệ' hiện?
            ├─ có ──────────────────────► GT#3  cờ mIsBYDMapNaving kẹt (reset là hết)
            └─ không ───────────────────► GT#4  profile cụm kẹt (vd 31 do chiếu-app) → CLEAN → adb reboot
```

## 5 giả thuyết (xếp theo khả năng) — chữ ký & cách vá
| # | Nguyên nhân | Chặng | Chữ ký xác nhận | Cách vá |
|---|---|---|---|---|
| 1 | Cài lại APK làm **rớt quyền notification** (Android xoá `enabled_notification_listeners`) | 1 | `notif-listener bật: false`; không có dòng `NavListener` nào khi dẫn | `adb shell cmd notification allow_listener <comp>` (PC, uid shell, không popup) |
| 2 | **Granted nhưng không bound** (BYD bỏ qua requestRebind; dadb chỉ chạy khi mở app) | 1 | `bật: true` + frame(1) hiện, nhưng dẫn GMaps KHÔNG ra `nav dist=..` | `disallow_listener` rồi `allow_listener` (ép rebind) |
| 3 | **Cờ mIsBYDMapNaving kẹt** (sessionReset gate bỏ reset nếu phiên cũ còn) | 3 | frame(1) trống nhưng **etabroadcast** (có reset) thì hiện | `-Step render` (đã reset sẵn); reboot xoá latch tồn dư |
| 4 | Lần **chiếu-app trước để cụm ở profile 31** ('mất km/h', không có vùng nav zin) | 3 | cả frame(1) lẫn etabroadcast đều trống, listener=true | `adb reboot` (broadcast không đổi được profile). **Không** reinstall |
| 5 | **dadb tự-cấp lỗi thầm lặng** (key filesDir bị xoá → popup Allow; hoặc tcpip5555 chưa bật) | 1 | log `NavConnect` báo lỗi 'popup Allow chưa bấm?' / connect localhost:5555 fail | Cấp từ PC (không phụ thuộc dadb/popup) — xem GT#1/#2 |

## Lệnh thô (dán tay nếu cần) — chuỗi đã verify từ code
```powershell
# 1) log 3 chặng
adb logcat -c; adb logcat -s CLNAV_AUTO:V NavListener:V ClusterBroadcaster:V NavConnect:V NavRebind:V

# 2) self-test + frame giả + report  (component đã verify: com.byd.clusternav/.modules.AutotestActivity)
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity
adb pull /sdcard/Android/data/com.byd.clusternav/files/autotest-report.txt .

# 3) FIX Chặng 1 — cấp + ép rebind từ PC (không popup, không cần tcpip5555)
adb shell cmd notification disallow_listener com.byd.clusternav/com.byd.clusternav.NavNotificationListener
adb shell cmd notification allow_listener   com.byd.clusternav/com.byd.clusternav.NavNotificationListener

# 4) RENDER-isolation — reset cờ kẹt rồi bắn '500m Nguyễn Huệ' (byd=false BẮT BUỘC)
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode etabroadcast --es byd false

# 5) CLEAN + reboot (khi GT#4)
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode clearnav
adb reboot
```

## Backlog vá cứng (LÀM SAU — cần rebuild nên KHÔNG làm trong phiên debug này)
Cả hai audit đánh `worthIt=false` **cho phiên này** vì rebuild+reinstall lại rớt quyền — đúng bug đang truy. Gói vào lần build kế tiếp:
- **MainActivity**: hiện `NavNotificationListener.connected` (cờ bound thật) tách khỏi 'granted' → phân biệt *granted-nhưng-chưa-bound* (lỗi BYD) với *granted-nhưng-rảnh*; nay cả hai đều hiện chấm vàng 'waiting'.
- **NavConnect.doReconnect**: nâng `Log.e` thầm lặng (popup Allow / tcpip5555 down) thành Toast/status nhìn thấy → giải thích vì sao nút Reconnect no-op.
- **(cân nhắc) ClusterCast.stop()**: kết thúc cast bằng profile *native/cong 30* thay vì 31, để làn nav zin còn chỗ vẽ; + reset cờ khi rời cast.
