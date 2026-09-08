# Session summary 2026-08-03 — On-car test chiều

## Kết quả

### ✅ Working
- **Projection open** — mở app = cụm chuyển cong + đen sẵn (proven sequence: wm size → 30/16/35 → black activity → resize full)
- **Cast app thường** — VietMap, GMaps lên cụm OK
- **Split 50/50** — cast trái/phải đúng tỉ lệ
- **DPI chỉnh** — per-app, lưu + cycle 320/240/160 OK
- **Stop app thường** — trả VietMap/GMaps về display 0 OK
- **Clean on start** — mở app dọn sạch apps cũ trên cluster OK
- **CP cast lên cụm** — move-task work, CP hiện trên cluster

### ❌ Chưa work / Kẹt
- **CP return** — `am stack move-task` CP về display 0 gây crash surfaceflinger (màn chính đen). Shell thủ công work nhưng code path gây conflict
- **CP resolution** — cast qua size không đúng spec (1422×800) — app set wm size đúng nhưng task resize không apply
- **Foreground detection trong split mode** — bubble báo "Không xác định" khi đang split + tap slot trống
- **cleanDisplay1 move launcher** — gây surfaceflinger crash (đã fix: skip com.android.*)

## Root causes xác định

| Issue | Root cause | Status |
|-------|-----------|--------|
| Projection không hiện | Sleep 300ms quá ngắn giữa profile commands — cần 2s | ✅ Fixed |
| App "mất" không lên cụm | `--activity-clear-task` kill task, Android 10 BYD không recreate trên display 1 | ✅ Fixed |
| Foreground detection fail | `parseForeground` regex sai format `am stack list` (tìm `realActivity=` thay vì `taskId=N: pkg/`) | ✅ Fixed |
| Projection tự đóng | OEM firmware đóng projection khi display 1 trống → cần black activity giữ content | ✅ Fixed |
| CP return crash system | move-task system app (launcher/CP) giữa displays → surfaceflinger abort | ❌ Cần research |
| CP cast lại vô hạn | Bubble thấy state=Idle (adopt chưa run) → cast CP → CP đã ở display 1 → no-op → kẹt loop | Partially fixed (isAppOnDisplay check) nhưng CP return vẫn crash |

## Timing proven trên xe

| Bước | Thời gian cần |
|------|-------------|
| wm size/overscan/density | instant |
| Profile 30 → 16 | 2s |
| Profile 16 → 35 | 2s |
| Profile 35 → black activity | 1s |
| Black activity → resize | 1s |
| **Tổng open projection** | **~7-8s** |
| Cast app (am start) | ~1s |
| Stop app (am start --display 0) | instant |

## Việc cần làm (off-car research)

### CP handling — cần approach khác
1. Đọc V1 (`ClusterCast.kt`) cách nó return CP — có thể KHÔNG dùng move-task
2. Test shell thủ công: `am stack move-task <CP_task> <stack_display0> true` — xác nhận khi nào work khi nào crash
3. Có thể CP return cần: close projection (18/0) trước → CP tự về? Hoặc `input keyevent HOME`?
4. Research DashCast source: DashCast handle CP/AA return thế nào?

### Foreground detection trong split
1. Debug tại sao `foregroundPackage` return null khi VietMap visible trên display 0
2. Có thể: dadb connection timeout trên main thread

### DPI trong split mode
1. Verify DPI save/restore per-app khi cast lại vào slot

## Files thay đổi hôm nay
- `core/.../simplified/ProjectionManager.kt` — sleep 2s giữa commands
- `core/.../simplified/AppMover.kt` — remove --activity-clear-task, add findOrCreateClusterStack, CP resize 1422×800, findNonHomeStackOnDisplay
- `core/.../simplified/SimpleCastCoordinator.kt` — cleanDisplay1, adopt detection, isAppOnDisplay, error auto-recovery
- `app/.../ClusterBlackActivity.kt` — NEW: black placeholder
- `app/.../MainActivityCastController.kt` — V2 removed, all simplified, zone buttons, resize view, DPI split
- `app/.../FloatingBubbleService.kt` — simplified intercept, split slot logic
- Layouts: zone buttons, VietMap bubble removed
- AndroidManifest: ClusterBlackActivity added, CastLifecycleReceiver removed
