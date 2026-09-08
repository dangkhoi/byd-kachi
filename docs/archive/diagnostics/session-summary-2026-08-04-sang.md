# Session summary 2026-08-04 sáng — On-car test

## CP Cast/Return — ĐÃ FIX

### Bugs tìm được và fix:
| Bug | Root cause | Fix |
|-----|-----------|-----|
| CP return crash (đêm qua) | `refreshCluster()` (service call AutoContainer 0) bắn ngay sau move-task → race surfaceflinger | Bỏ `refreshCluster()` cho CP/AA, chỉ `wm density reset` |
| CP return move sai task | `findTaskId()` pick task ĐẦU TIÊN tìm được (có thể task trên display 0), không phải task thật trên cụm | Lưu taskId lúc cast → dùng chính nó khi return. Thêm `findTaskIdOnDisplay()` fallback |
| ClusterNav app bị cast lên cụm | ClusterBlackActivity cùng task stack với MainActivity → kéo cả app lên display 1 | `singleInstance` + `taskAffinity` riêng cho ClusterBlackActivity |
| Bubble cast nhầm ClusterNav | Foreground detection pick `com.byd.clusternav` (app đang hiện trên display 0) khi CP vừa trả về | Thêm `com.byd.clusternav` vào excluded set |
| App crash khi mở | Layout `layout-w960dp` (xe dùng) thiếu HUD panel views | Thêm panel vào cả 2 layout files |

### Kết quả cuối:
- CP cast lên cụm: ✅ OK (lần 1 verified)
- CP return về display 0: ✅ OK (lần 1 verified, exit=0, đúng taskId)
- Cần test thêm nhiều lần để confirm ổn định

## VietMap BLE HUD — BLOCKED

### Findings:
- Đầu xe BYD DiLink3 **không support BLE peripheral advertising** (chip chỉ central)
- `onStartSuccess` callback misleading — API success nhưng sóng không phát ra ngoài
- iPhone scan cũng không thấy → confirmed hardware limitation
- VietMap trên xe connect tới HUD thật bằng BLE central (app_if: 6)
- Cùng device: app A không thể scan thấy BLE advertise của app B (Android BLE limitation)

### Kết luận:
Approach giả lập HUD peripheral từ đầu xe = **KHÔNG KHẢ THI**. Cần thiết bị bên ngoài (ESP32, điện thoại phụ) hoặc approach hoàn toàn khác.

## Bugs chưa fix (noted)

| Bug | Mô tả | Status |
|-----|--------|--------|
| App nhớ display sau reboot | Tắt xe khi đang chiếu → sáng mở app nhảy lên cụm | Noted, cần research |
| SL6 split full thay vì chia đôi | Chờ SL6 test bản mới + gửi log | Chờ feedback |
| Nav cluster chỉ hiện mũi tên thẳng | Trước đây có đầy đủ trái/phải/quẹo, giờ chỉ còn thẳng | Noted, regression — check IconResource.resolve hoặc arrow bitmap loading |

## Files thay đổi hôm nay
- `core/.../simplified/AppMover.kt` — findTaskIdOnDisplay, castToCluster trả taskId, CP return dùng saved taskId
- `core/.../simplified/SimpleCastCoordinator.kt` — handleStop pass taskId, doze whitelist, logging
- `core/.../simplified/SimpleCastModels.kt` — CastingFull có taskId field
- `app/.../FloatingBubbleService.kt` — exclude `com.byd.clusternav` khỏi foreground detection
- `app/.../vietmaphud/VietMapHudService.kt` — BLE + SPP server (blocked by hardware)
- `app/.../vietmaphud/MainActivityHudController.kt` — UI panel (nullable safe)
- `app/AndroidManifest.xml` — BLE permissions, ClusterBlackActivity singleInstance
- `app/res/layout-w960dp/activity_main.xml` — HUD panel added
