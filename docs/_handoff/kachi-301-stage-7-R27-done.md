# kachi-301 · stage 7 · R27 — hàng "Gần đây" trên cùng bộ chọn của Ô — DONE (off-car + máy ảo)

Owner 13/10: *"khi chọn widget cũng thêm section các ứng dụng hay dùng như nhấn nút ứng dụng nhé, trên cùng"*.
Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` R27 + §9.

## Tệp đổi
- MỚI `app/src/main/java/com/byd/clusternav/launcher/AppDrawerRecent.kt` — `internal fun AppDrawer.recentSection(body, apps, all, recentApps, cols): Boolean`
  (một chỗ dựng hàng Gần đây; rỗng ⇒ không bày gì, trả `false`).
- `AppDrawer.kt` (498 → 496 dòng): ASSIGN_SLOT gọi `recentSection` TRƯỚC mục Ứng dụng (tiêu đề Ứng dụng có thêm đệm trên `Sp.L` khi
  có hàng Gần đây); OPEN_APP gọi lại đúng hàm đó (hành vi giữ nguyên); `sectionLabel` từ `private` thành `internal`. Khối BUG (O) không đổi.
- `DrawerController.kt` `open(index)`: thêm `recentApps = recentApps()`. KachiHomeActivity không cần đổi (đã chuyền sẵn `recentApps` cho bộ điều khiển).
- MỚI test `app/src/test/java/com/byd/clusternav/launcher/AssignSlotRecentContractTest.kt` (4 bài).

## Hợp đồng / đường chạm
Ô gần đây = `AppDrawerApps.recent(all, recentApps)` trên CHÍNH `all = apps.load()` của lưới Ứng dụng ⇒ chép nguyên `onTap`
= `onPickApp(pkg)` → `DrawerController.open` → `onPickApp(index, pkg)` → `slots.assignApp`. Không có đường thứ hai.
PICK_DOCK / PICK_SHORTCUTS / PICK_TRIP không đổi (test khoá).

## Test [ĐO]
- Bộ tập trung `*Drawer* *AppDrawer* *PickerCap* *LauncherI18n* *LayeringRules* *AssignSlotRecent* *OpenAppWiring* *DockPicker* *VoiceAppNamesWiring*`:
  9 lớp / 105 bài / 0 hỏng / 0 bỏ qua (đếm từ XML).
- Chứng minh đỏ: gỡ `recentApps = recentApps()` ở `open()` + gỡ lời gọi `recentSection` ở ASSIGN_SLOT ⇒ 4/4 đỏ; khôi phục ⇒ xanh.
- `:app:assembleVehicleTest` xanh.

## Máy ảo emulator-5556 [ĐO]
APK vehicleTest đã cài trên máy ảo trùng sha1 với bản dựng (`953c425b…`, chứa `AppDrawerRecentKt`). Lần `install -r` của chính mình bị lỗi
"not enough space" (/data đầy 99 %, còn 74 MB), nhưng bản đã cài chính là APK này (agent khác cài lúc 18:32). Đã mở Clock
qua ngăn kéo Ứng dụng để có một app gần đây, rồi ô 1 › Đổi ứng dụng ⇒ dump: "Gần đây" y=168, Clock, "Ứng dụng" y=367.
Icon ở hai hàng cùng cỡ 66×66 px (dump). Ảnh `docs/diagnostics/qa-301-ui/picker-recent.png` (1280×720, không có hình xe).
Đã về HOME. KHÔNG chạm ô gần đây (sẽ đổi ô 1 của hồ sơ QA2); đường chạm được test hợp đồng khoá.

## Còn mở
- 🚗 chưa xem trên xe.
- /data của máy ảo đầy 99 % — lần cài sau có thể hỏng; owner tự quyết dọn.
