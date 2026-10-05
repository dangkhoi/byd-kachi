# 2.89 · B2 VM-PREREQ-TRUTH — đầu ra nguyên văn `cmd deviceidle whitelist` · `appops get` · TOP-RESUMED (máy ảo)

> **Trạng thái**: Current · **Cập nhật**: 2026-10-05 · **Loại**: Diagnostics (fixture) · **Owner**: dangkhoi ·
> **Mục đích**: định dạng THẬT cho bộ đọc thuần `:core` `DozeWhitelistRead` · `OverlayOpRead` · `VietMapBubbleWait.topResumed`.
> **Spec**: `docs/specs/kachi-289-field-fixes.html` §B2 · **Backlog**: `VM-PREREQ-TRUTH` · Test: `AppPrereqReadTest`,
> `VietMapBubbleWaitTest` (đọc thẳng các tệp trong thư mục này).

Mức bằng chứng theo CLAUDE.md §2: `[ĐO]` = output thật của lần chạy này · `[SUY]` = đọc nguồn, chưa chạy · `[CHƯA BIẾT]`.

Máy ảo `emulator-5554` (AVD `clusternav10`, Android 10 / API 29, google_apis). Đầu ra lấy bằng
`adb -s emulator-5554 shell <lệnh>` (stdout, kèm `2>&1` cho tệp `10-*`); không sửa một byte. Máy ảo đang bị tạm dừng
(`kill -STOP`, luồng khác chạy test) — được `kill -CONT` trong lúc đo rồi `kill -STOP` lại như cũ.

## Tệp và lệnh

| Tệp | Lệnh | Ghi chú |
|---|---|---|
| `01-whitelist-before.txt` | `cmd deviceidle whitelist` | trạng thái ban đầu: VietMap ở `user,` (đã được thêm từ trước) |
| `02-remove-vietmap.txt` | `cmd deviceidle whitelist -vn.vietmap.live` | `Removed: vn.vietmap.live` — để có hình "VẮNG" |
| `03-whitelist-vietmap-absent.txt` | `cmd deviceidle whitelist` | VietMap vắng; `com.android.providers.calendar` chỉ có ở `system-excidle,` |
| `04-add-vietmap.txt` | `cmd deviceidle whitelist +vn.vietmap.live` | `Added: vn.vietmap.live` — trả về như cũ |
| `05-whitelist-vietmap-user.txt` | `cmd deviceidle whitelist` | `diff` với `01` = rỗng (đã trả nguyên trạng) |
| `06-add-not-installed.txt` | `cmd deviceidle whitelist +com.example.kachi.notinstalled` | `Unknown package: …` (không đổi trạng thái) |
| `07-appops-vietmap-allow.txt` | `appops get vn.vietmap.live SYSTEM_ALERT_WINDOW` | `allow; time=…; duration=…` |
| `08-appops-youtube-default.txt` | `appops get com.google.android.youtube SYSTEM_ALERT_WINDOW` | `default; rejectTime=…` (bị từ chối vẽ nổi) |
| `09-appops-maps-no-operations.txt` | `appops get com.google.android.apps.maps SYSTEM_ALERT_WINDOW` | `No operations.` + `Default mode: default` |
| `10-appops-not-installed.txt` | `appops get com.example.kachi.notinstalled SYSTEM_ALERT_WINDOW 2>&1` | `Error: No UID …`, exit 255 |
| `11-appops-chrome-uid-ignore.txt` | `appops set --uid com.android.chrome SYSTEM_ALERT_WINDOW ignore` rồi `appops get com.android.chrome SYSTEM_ALERT_WINDOW` | in `Uid mode: COARSE_LOCATION: foreground` — **lỗi AOSP 10** (dưới); đã trả `--uid … default`, `get` trước/sau trùng nhau |
| `12-resumed-grep.txt` | `dumpsys activity activities \| grep -E 'mResumedActivity\|topResumedActivity\|ResumedActivity'` | đúng lệnh `VietMapAutostart` dùng |
| `13-resumed-per-display.txt` | `dumpsys activity activities \| grep -E '^Display #\|ResumedActivity'` | gắn mỗi dòng với màn của nó |
| `14-am-stack-list.txt` | `am stack list` | VietMap ở màn ảo 3 (ô Kachi), Kachi ở display 0 |
| `15-vietmap-services.txt` | `dumpsys activity services vn.vietmap.live \| grep ServiceRecord` | `VMBluetoothService` đang chạy |

## Kết luận đo được

1. **[ĐO]** Định dạng danh sách miễn pin khớp nguồn AOSP r47 `DeviceIdleController.java:3941-3961`: ba khối
   `system-excidle,` → `system,` → `user,`, mỗi dòng `<loại>,<gói>,<appId>`, không dòng đầu/cuối, không `\r`.
   Gói chỉ có ở `system-excidle,` (vd `com.android.providers.calendar`) KHÔNG được `isIgnoringBatteryOptimizations` tính
   (r47 `:2278-2283`) ⇒ bộ đọc trả `EXCEPT_IDLE_ONLY` = KHÔNG miễn.
2. **[ĐO]** `-<gói>` rồi `+<gói>` trả danh sách về đúng từng byte (`diff 01 05` rỗng) — đường hoàn tác của §4 chạy được.
3. **[ĐO]** Lỗi AOSP 10 của `appops get <gói> <op>` khi gói có chế độ uid: dòng `Uid mode:` mang tên/giá trị op uid ĐẦU
   TIÊN, không phải op hỏi (`AppOpsService.java:1057-1063` r47 dùng `keyAt(j)`; r34 `:2222-2229` đã sửa). Bộ đọc coi
   dòng `Uid mode:` mang op khác là "có chế độ uid, không đọc được" ⇒ KHÔNG BIẾT ⇒ không ghi.
4. **[ĐO]** Dòng tổng `  ResumedActivity: ` (hai dấu cách, có dấu cách sau `:`) có mặt cùng các dòng theo màn
   (`" ResumedActivity:"`, không dấu cách) và theo stack (`mResumedActivity:`). Ở lần đo, dòng tổng = VietMap trong
   **màn ảo 3** (ô Kachi đang giữ tiêu điểm) trong khi display 0 là Kachi — dòng tổng nói ai có TIÊU ĐIỂM, không nói ai
   đang ở display 0. Với lượt chờ bóng (chỉ chạy khi VietMap vừa được mở mới bằng `monkey` vào display 0) điều này đủ;
   ghi lại để không ai dùng dòng tổng làm "VietMap đang hiện trước người lái" ở ca khác.
5. **[CHƯA BIẾT]** Trên xe (ROM 2602030): định dạng hai lệnh y hệt AOSP [SUY — spec §B2: `services.jar` của ROM giữ nguyên mã in
   danh sách, `DeviceIdleController.java:3592-3610` bản dịch ngược]. Chốt bằng màn Chẩn đoán (khối *điều kiện nền app*) và mục
   `MIỄN PIN (deviceidle)` của `ClusterDiag`.
