# PROFILE-SWITCH-SLOTS · T0a — `am stack remove` cho cửa sổ nổi của app khách (máy ảo, 2026-10-01)

Spec: [`specs/kachi-profile-switch-slots.html`](../specs/kachi-profile-switch-slots.html) §2.6 · §4.3 · T0a.
Mục đích: tầng 1 của CLAUDE.md §14 cho phần dọn cửa sổ nổi (lỗi B) — chạy lệnh shell thô **trước** khi viết mã
tầng 2–3. Đây là **máy ảo Android 10** (`clusternav10`, API 29), **không phải xe**. Trên xe vẫn là [CHƯA BIẾT] tới
khi có OC-0 (T0b).

## Bối cảnh lúc đo [ĐO]

- Kachi 2.84 (185) là HOME, có kênh shell, VietMap nhúng trong ô (màn ảo `displayId=15`, stack 24).
- `enable_freeform_support=1`, `force_resizable_activities=1`.
- `adb shell` của máy ảo này chạy **uid 0**. Đường thật của Kachi là uid 2000 (shell), nên lượt chính chạy lại bằng
  `su shell …` (đã kiểm `su shell id` ⇒ `uid=2000(shell)`).
- Lệnh mở app là đúng chuỗi `FreeformLaunch.launchCmd` (đường cửa sổ nổi của Kachi).

## Kết quả [ĐO]

| Bước (uid 2000) | Lệnh | Kết quả nguyên văn |
|---|---|---|
| 1 | `am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --display 0 --windowingMode 5 -n 'com.google.android.youtube/.app.honeycomb.Shell$HomeActivity'` | `Starting: Intent { … }` · `exit=0` |
| 2 | `am stack list` | `Stack id=26 … displayId=0` · `mWindowingMode=freeform` · `mActivityType=standard` · `taskId=2002: com.google.android.youtube/…` (fixture `am-stack-list-emulator-2026-10-01-t0a-opened.txt`) |
| 3 | `am stack remove 26` | stdout rỗng · `exit=0` |
| 4 | `am stack list` | chỉ còn stack 24 (VietMap, display 15) và stack 0 (home, Kachi) — fixture `…-t0a-after-remove.txt` |
| 5 | `am stack remove 0` (stack home) | `java.lang.IllegalArgumentException: Removing non-standard stack is not allowed.` · `at …ActivityTaskManagerService.removeStack(ActivityTaskManagerService.java:3384)` · `exit=255`; stack 0 còn nguyên |

Cùng kịch bản chạy bằng uid 0 trước đó (stack 25) cho kết quả y hệt. Thêm một ca uid 0:
`am stack remove 99999` ⇒ stdout rỗng, `exit=0`, logcat `W/ActivityTaskManager: removeStack: No stack with id=99999`.

Hệ quả phụ [ĐO]:
- **Không đụng stack khác**: stack 24 (VietMap trong màn ảo của ô) và stack 0 (Kachi) giữ nguyên id, Kachi giữ
  nguyên pid suốt lượt đo.
- **Tiến trình app khách KHÔNG bị giết**: `pidof com.google.android.youtube` = 9907 trước và sau khi gỡ.
  Cơ chế [ĐO AOSP A10 r47]: `cleanUpRemovedTaskLocked` bỏ qua tiến trình có activity chưa dừng
  (`ActivityStackSupervisor.java:1848-1852` → `WindowProcessController.shouldKillProcessForRemovedTask`
  `:694-709`, `if (!activity.stopped) return false`). Cửa sổ vừa hiện thì chưa dừng ⇒ gỡ cửa sổ, giữ tiến trình —
  nhẹ hơn `am force-stop`.

## Kết luận

- [ĐO máy ảo A10] `am stack remove <id>` đóng đúng một stack nổi `standard` trên display 0, không đụng stack khác,
  chạy được bằng uid 2000. Rào loại stack của framework A10 có thật (ném lỗi, `exit=255`, không gỡ).
- [ĐO AOSP] Android 12 dùng cùng chuỗi lệnh nhưng **không** có rào loại (`ActivityTaskManagerService.removeTask`
  r34 `:1904-1926`) ⇒ bộ lọc `standard` của Kachi là rào duy nhất trên DL5 (spec §2.6).
- [CHƯA BIẾT] ROM BYD (DiLink 3/4 Android 10; DL5 Android 12) có giữ nguyên lệnh và quyền không ⇒ OC-0 (T0b) trước
  khi đăng OTA.

Tệp gốc của lượt đo nằm ở thư mục nháp của phiên (không đưa vào repo); hai dump `am stack list` nguyên văn đã chép
vào `core/src/test/resources/diagnostics/am-stack-list-emulator-2026-10-01-t0a-{opened,after-remove}.txt`.
