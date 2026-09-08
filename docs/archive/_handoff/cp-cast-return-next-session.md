# Handoff: CP Cast/Return — Next Session

## Tình trạng hiện tại
- **App thường (VietMap, GMaps): DONE** — cast/stop/split/DPI/resize tất cả OK trên xe
- **CP: shell commands work, code implementation KHÔNG work** — rất gần nhưng chưa đúng

## CP — Proven bằng shell thủ công (100% work)

### Cast CP lên cụm:
```bash
# Tìm stack trên display 1
am stack list | grep "displayId=1"
# Move CP task vào stack display 1
am stack move-task <CP_taskId> <stack_on_display1> true
# Resize full
am task resize <CP_taskId> 0 0 1422 800
```

### Return CP về màn chính:
```bash
# Tìm non-home stack trên display 0
am stack list | grep "Stack id=.*displayId=0" | grep -v "id=0 "
# Move CP task về
am stack move-task <CP_taskId> <stack_on_display0> true
```

**Cả hai đều chạy thủ công trên xe ngon lành.** Vấn đề là CODE app không gọi đúng.

## Bugs đã xác định trong code

### Bug 1: Return CP gây crash surfaceflinger
- **Triệu chứng**: màn chính đen, cụm vẫn frame CP cũ
- **Root cause chưa rõ 100%**: có thể `cleanDisplay1()` move LAUNCHER task → crash. Đã fix skip `com.android.*` nhưng VẪN crash.
- **Giả thuyết**: code đang gọi thêm lệnh gì đó ngoài move-task đơn thuần (wm size reset? refreshCluster? configurator.apply?)
- **Hướng debug**: so sánh CHÍNH XÁC shell commands log khi user bấm stop (SimpleCast log) vs lệnh thủ công work. Khác ở đâu = bug ở đó.

### Bug 2: Cast CP nhưng resolution sai
- **Triệu chứng**: CP hiện nhưng bounds 1920×800 thay vì 1422×800
- **Root cause**: `am task resize` exit=-1 (fail) — có thể do task chưa fully landed khi resize, hoặc resize CP task (system uid) bị reject
- **Hướng debug**: thử `am task resize` thủ công ngay sau move-task, verify exit code

### Bug 3: Bubble cast CP vô hạn (đã partially fix)
- **Root cause**: bubble detect foreground = CP (vì CP vừa bị move về display 0) → dispatch CastFull → code thấy CP ĐÃ trên display 1 → adopt state (FIX mới). Nhưng timing race vẫn có thể xảy ra.
- **Status**: `isAppOnDisplay` check đã thêm — chưa test on-car.

## Approach đề xuất cho phiên tới

### Step 1: Shell-first (5 phút on-car)
Chạy từng lệnh THỦ CÔNG, ghi lại KẾT QUẢ:
```bash
# 1. Cast CP
am stack move-task <CP_task> <cluster_stack> true
am task resize <CP_task> 0 0 1422 800
# Verify: am stack list → CP bounds = 1422×800 trên display 1?

# 2. Return CP
am stack move-task <CP_task> <display0_stack> true
# Verify: CP về display 0? Surfaceflinger còn sống?
```

### Step 2: So sánh với code log
Bật app, cast CP, xem SimpleCast log. So sánh TỪNG lệnh với sequence thủ công. Khác ở đâu?

### Step 3: Fix code cho đúng y hệt sequence thủ công
KHÔNG thêm bất kỳ lệnh nào ngoài sequence proven:
- Cast: move-task + resize. KHÔNG wm size, KHÔNG display config, KHÔNG overscan.
- Return: move-task. KHÔNG wm reset, KHÔNG refreshCluster, KHÔNG close projection.

### Step 4: Verify V1 approach (nếu step 3 vẫn fail)
Đọc `ClusterCast.kt` method `placeAppOnVd` và `stop()` — xem V1 return CP bằng gì:
- `am stack move-task`?
- `am display move-stack`?
- `input keyevent HOME`?
- Close projection (18/0)?

## Files liên quan
- `core/.../simplified/AppMover.kt` — cast/return logic
- `core/.../simplified/SimpleCastCoordinator.kt` — handleStop, cleanDisplay1
- `car-integration/.../CastPlacementCommands.kt` — V2 proven commands (reference)
- `app/.../ClusterCast.kt` — V1 legacy code (reference cho CP handling)

## JAVA_HOME
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
```

## ADB
```
~/Library/Android/sdk/platform-tools/adb connect <vehicle-ip>:5555
```
