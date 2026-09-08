# Coupling UI ↔ tầng dưới — đo tự động, chỉ được phép giảm

Cập nhật 2026-07-27 13:40. Ba con số dưới đây ghim trong `CastArchitectureRatchetTest`; tăng là fail.

| Số đo | Khởi điểm | Hiện tại | Ý nghĩa |
|---|---|---|---|
| Kiểu `v2` mà UI import | 42 | **31** | gồm cả lớp còn sống trong `:app` |
| Trong đó **vượt tầng** (`:core`/`:car-integration`) | — | **27** | đây mới là khoảng cách thật tới "UI chỉ thấy façade" |
| Điểm mở adb ngoài `:car-integration` | 13 | **13** | 8 trong đó ở `ClusterCast.kt` (engine V1 cũ) |

## Một sai của phép đo, đã sửa

Bản đo đầu tiên đếm theo **package** nên gộp cả `CastAndroidRuntime`, `CastAppCatalog`,
`CastAndroidLifecycle`, `CastAppEntry` — những lớp vẫn sống trong `:app`. UI dùng chúng là app→app, không
phải vượt tầng. Ratchet giờ tra **module khai báo** của từng kiểu rồi mới đếm. Bài học giống lần trước:
số dễ đo không phải số đúng.

## Đã dọn được gì (42 → 31)

| Việc | Kiểu bỏ khỏi UI |
|---|---|
| `facade.envelope()` thay 7 chỗ tự bóc `StoreRead.Loaded` | `StoreRead` |
| `facade.renderModel()` gói projector | `CastRuntimeUi` |
| `facade.observedState()` thay việc bóc `ObservationValue.Known` | `ObservationValue` |
| `facade.recordOperation()` / `operationLog()` | `CastOperationLog` |
| `facade.planStop/planRecover/planGeometry` dựng ý định thay UI | `CastIntent`, `CastIntentKind`, `TargetEvidence` |
| `facade.applyVehicleTestRollout()` / `v2OwnsActions()` | `CastRolloutRegistry` |
| `facade.bubbleProjection()` / `unavailableReason()` | `CastBubbleProjection` |
| `facade.selectionReady()` | `eligibilityFor`, `CastManualTargetEligibility` |
| `facade.stopAcknowledgementGraceMillis()` truyền vào timers | `CastUiRenderer` |
| `facade.runBootAutomationIntent()` / `pendingIntentIsUserRequested()` | `CastIntentOrigin` |
| `facade.storeStatusLine()` cho màn Chẩn đoán | — |

## Còn lại

Phần lớn 27 kiểu còn lại là **dữ liệu hợp đồng** UI cần để vẽ (`CastAction`, `CastAppRow`,
`BubbleProjection`, `CastRenderModel`, `AppIconState`, `ClusterStyle`, geometry…). Chúng nằm ở `:core`
theo đúng thiết kế — cột "Data · metadata · config" trong lưới — nên coupling tới chúng là **chấp nhận
được**, không phải nợ. Phần thật sự còn phải dọn là mấy kiểu điều khiển: `CastManualTargetReader`,
`CastAutomationSettings`, `ExecutionResult`, `CastAppPresentation`.

Nói rõ để không tự lừa: con số 27 sẽ **không** về 0, và không nên về 0. Mục tiêu đúng là 0 kiểu **điều
khiển**, còn kiểu dữ liệu thì cứ để UI đọc.
