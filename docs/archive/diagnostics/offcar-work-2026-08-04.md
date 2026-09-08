# Off-car work — 2026-08-04

## 1. CP cast/return ổn định hóa
- Lần 1 qua/về OK, lần 2 có vấn đề (bubble cast nhầm ClusterNav → đã fix exclude)
- Cần verify: sau fix exclude, cast CP qua/về nhiều lần liên tiếp có ổn không?
- Trace code path: khi CP trả về → bubble state = gì? Foreground detection pick ai?

## 2. Nav cluster maneuver icon regression
- **Triệu chứng:** chỉ hiện mũi tên thẳng, trước đây có đủ trái/phải/quẹo/vòng xuyến
- **Check:**
  - `IconResource.resolve()` — có đang trả đúng icon code không?
  - `loadIconBitmap()` — large icon từ GMaps notification có null không?
  - `ClusterBroadcaster` — maneuverIcon có được truyền xuống CAN/broadcast đúng không?
  - So sánh: GMaps version trên xe có update không? Notification format có đổi?
- **Test:** dump notification GMaps đang dẫn: `adb shell dumpsys notification | grep -A20 "com.google.android.apps.maps"`

## 3. App nhớ display sau reboot
- Tắt xe khi đang chiếu → mở lại → app nhảy lên cụm
- `cleanDisplay1()` move-task nhưng Android nhớ display cũ → user tap icon → app mở lại ở display 1
- **Hướng fix:**
  - Sau move-task, bắn `am start --display 0 --windowingMode 1 -n 'pkg/activity'` để overwrite display ghi nhớ
  - Hoặc force-stop app sau move → lần mở tiếp = fresh task trên display 0
  - Test off-car: đọc AOSP source xem `am start --display 0` có thật sự overwrite preferred display

## 4. SL6 split cast
- Chờ feedback từ anh em SL6 test bản mới
- Nếu vẫn fail: cần log từ `adb logcat -s SimpleCast` trên SL6

## 5. VietMap BLE HUD — pivot approach
- BLE peripheral từ đầu xe = FAIL (hardware không support)
- **Alternatives còn lại:**
  - Accessibility Service đọc speed limit từ UI VietMap (khả thi, cùng device)
  - Tìm cách khác (shared memory, ContentProvider, logcat sniff...)
- **Quyết định:** dừng hay thử Accessibility?

## 6. Autostart split (trái + phải)
- Chưa implement (đêm qua chỉ research)
- Effort: ~1h — prefs + UI + boot logic
- Priority thấp hơn bugs trên

## Thứ tự ưu tiên
1. Nav maneuver regression (user-facing, dễ nhận thấy)
2. CP ổn định (đã gần xong, cần verify)
3. App nhớ display (annoying nhưng có workaround: mở ClusterNav trước)
4. Autostart split (feature mới, không gấp)
5. VietMap speed limit (research, chưa rõ approach)
