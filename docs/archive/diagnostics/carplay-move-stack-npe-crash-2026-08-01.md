# CarPlay: `am display move-stack` crash NPE thật trong AOSP — ảnh hưởng trực tiếp `MOVE_STACK_TO_CLUSTER`

Ngày đo: 2026-08-01 (rạng sáng), trên DiLink3 (<vehicle-ip>), CarPlay đang cắm dây thật, phiên sống.

## Mức bằng chứng: ĐÃ CHỨNG MINH (CLAUDE.md §2)

Không phải suy đoán — lệnh chạy tay qua adb, exception bắt được nguyên văn, lặp lại **3/3 lần** với các biến thể khác nhau (có/không tắt animation), luôn cùng một stack trace.

## Lệnh gây crash

```
am display move-stack <stackId> 1
```

Đây **chính xác** là lệnh mà `CommandKind.MOVE_STACK_TO_CLUSTER` phát ra trong sản phẩm thật:

```kotlin
// car-integration/.../CastPlacementCommands.kt:53-60
CommandKind.MOVE_STACK_TO_CLUSTER -> when {
    pkg == null -> NO_OP
    landedStack(adb, pkg, display, cancelled) != null -> NO_OP
    else -> sourceStack(adb, pkg, display, cancelled)
        ?.let { "am display move-stack $it $display" }
        ?: NO_OP
}
```

Comment gốc tại chỗ đó: *"R2: the decisive rung. reparent bypasses ActivityStarter display gating entirely."* — đây là **đường DUY NHẤT** để dời một activity không-exported (như CarPlay) sang display khác, vì `am start -n` bị chặn thẳng (xem phần dưới).

## Stack trace nguyên văn

```
Exception occurred while executing:
java.lang.NullPointerException: Attempt to invoke virtual method 'int com.android.server.wm.DisplayContent.getRotation()' on a null object reference
	at com.android.server.wm.TaskSnapshotController.createTaskSnapshot(TaskSnapshotController.java:298)
	at com.android.server.wm.AppWindowToken.initializeChangeTransition(AppWindowToken.java:1816)
	at com.android.server.wm.AppWindowToken.onConfigurationChanged(AppWindowToken.java:1756)
	...
	at com.android.server.wm.DisplayContent.moveStackToDisplay(DisplayContent.java:2489)
	at com.android.server.wm.TaskStack.reparent(TaskStack.java:630)
	at com.android.server.wm.ActivityStack.reparent(ActivityStack.java:922)
	at com.android.server.wm.RootActivityContainer.moveStackToDisplay(RootActivityContainer.java:970)
	at com.android.server.wm.ActivityTaskManagerService.moveStackToDisplay(ActivityTaskManagerService.java:3622)
	at com.android.server.am.ActivityManagerShellCommand.runDisplayMoveStack(ActivityManagerShellCommand.java:2519)
```

Crash xảy ra **giữa lúc** `DisplayContent.moveStackToDisplay` đang reparent stack: nó gọi `addStackToDisplay` → `TaskStack.onParentChanged` → `onConfigurationChanged` → `AppWindowToken.initializeChangeTransition` (chuẩn bị animation chuyển tiếp) → `TaskSnapshotController.createTaskSnapshot` cần đọc `DisplayContent.getRotation()` của display **cũ**, nhưng tham chiếu đó đã null vào đúng thời điểm này (khả năng: display cũ đã bị gỡ khỏi cây trước khi snapshot kịp đọc rotation của nó — race hoặc thứ tự dọn dẹp sai trong chính framework, không phải lỗi ClusterNav).

## Hậu quả đo được (không phải suy đoán — quan sát trực tiếp)

1. **Task biến mất khỏi `am stack list` hoàn toàn** — không còn ở display 0 (nguồn) lẫn display 1 (đích). Không phải "ẩn", là **mất khỏi hệ thống**, cần điện thoại tự trigger kết nối lại CarPlay từ đầu (rút/cắm cáp hoặc bật tắt CarPlay) mới thấy task mới xuất hiện.
2. **Khi phần snapshot kịp tạo trước lúc crash**: cụm hiện được **đúng một ảnh tĩnh đóng băng** của CarPlay tại thời điểm crash (xác nhận bằng quan sát trực tiếp của chủ dự án: chuyển CarPlay sang màn nhạc trên điện thoại, cụm vẫn đứng hình bản đồ cũ) — không phải surface sống, không bao giờ tự cập nhật lại.
3. **Màn chính (display 0) cũng bị xáo trộn**: chủ dự án quan sát thấy CarPlay bị icon launcher đè lên trong lúc này (cả hai màn cùng hiện CarPlay một lúc ngắn, "hơi kỳ") — đúng dấu hiệu của một reparent nửa chừng.

## Đã loại trừ — KHÔNG phải nguyên nhân

- **Không phải do animation**: tắt cả `window_animation_scale`/`transition_animation_scale`/`animator_duration_scale` về 0 trước khi chạy lại — **crash y hệt**, cùng stack trace. `initializeChangeTransition` vẫn chạy đường tạo snapshot bất kể animation có bật hay không.
- **Không phải do thứ tự lệnh khác** — thử với stack fresh (CarPlay vừa kết nối lại, task mới tinh) — vẫn crash y hệt lần đầu.

## Đường thay thế đã thử và bị loại

`am start --display 1 --windowingMode 5 -n com.byd.carplay.ui/com.byd.carplay.ui.VideoActivity` (giống `PLACE_KEEP_SESSION`/`RESUME_PROTECTED` trong code) — **luôn luôn** bị chặn:

```
Security exception: Permission Denial: starting Intent { ... cmp=com.byd.carplay.ui/.VideoActivity }
from null (pid=... uid=2000) not exported from uid 1000
java.lang.SecurityException: ... not exported from uid 1000
```

Thử cả khi task CarPlay **đã tồn tại sẵn** (không phải cold-launch) — vẫn bị chặn y hệt. Kết luận: `com.byd.carplay.ui.VideoActivity` **không exported**, `am start -n` không bao giờ dùng được cho nó dù task cũ hay mới — `move-stack` (bypass ActivityStarter) là đường DUY NHẤT còn lại, và chính đường đó đang crash.

## Ý nghĩa cho sản phẩm

- `MOVE_STACK_TO_CLUSTER` — bước "quyết định" trong dải lệnh cast cho mọi target protected (CarPlay, có thể cả Android Auto) — **không an toàn trên đúng bản ROM/framework patch level này**. Đây rất có thể là gốc rễ thật của các báo cáo lịch sử "cast CarPlay lên được nhưng resize không mượt, lúc được lúc không" — không phải do resize, mà do chính bước move-stack thỉnh thoảng (hoặc luôn luôn, ở patch level hiện tại) crash và để lại snapshot đóng băng.
- Không thử thêm biến thể nào khác của `move-stack` nữa trong phiên này — mỗi lần thử đều làm rớt phiên CarPlay thật của chủ dự án, cần rút/cắm lại mới phục hồi. Việc lặp lại thêm rủi ro làm phiền chủ dự án và không cho thêm dữ kiện mới (đã đủ 3/3 lần giống hệt).

## Hướng điều tra tiếp — CHƯA làm, cần quyết định trước khi động vào code

1. **Đọc source AOSP thật** (đúng theo CLAUDE.md §3) tại `DisplayContent.moveStackToDisplay`/`TaskSnapshotController.createTaskSnapshot` cho đúng patch level của xe (SDK 29 dòng DiLink3) để hiểu chính xác điều kiện nào làm `DisplayContent` (cũ) thành null — có thể là bug đã biết, có bản vá OEM, hoặc cách né hoàn toàn khác (ví dụ tắt hẳn `mSurfaceAnimator`/snapshot cho riêng path này bằng cấu hình khác `animator_duration_scale`).
2. **Thử tắt tính năng snapshot task hoàn toàn** (nếu có cờ `persistentTaskSnapshotAllowed`/`SETTINGS_ENABLE_TASK_SNAPSHOT` tương đương ở tầng device — chưa xác nhận có tồn tại trên xe này) trước khi gọi move-stack, thay vì chỉ tắt animation scale (đã thử, không đủ).
3. **Tìm cơ chế khác hoàn toàn để "chiếu" nội dung protected sang cụm** không cần reparent stack — ví dụ: giữ CarPlay nguyên trên display 0, chỉ tạo virtual display MỚI để MIRROR (không di chuyển) nội dung sang cụm — cần nghiên cứu API tương ứng (`DisplayManager`/`VirtualDisplay` với `MediaProjection`, hoặc cơ chế riêng của BYD/XDJA containerservice mà `dashcast-src` có thể đã ghi lại cách né vấn đề này).
4. Việc này ảnh hưởng TRỰC TIẾP tới độ ổn định cast CarPlay/AA hiện tại trong bản 0.85 đang chạy — cần ưu tiên cao cho phiên sau, có xe thật để đo tiếp theo đúng quy trình §14 (không đoán, không code trước khi có bằng chứng thêm).
