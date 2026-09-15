# Quality review toàn dự án — Kachi (2026-09-15)

- **Ngày:** 2026-09-15 · **Chủ:** dangkhoi · **Bối cảnh:** owner báo *"code rất nhiều rồi, bắt đầu thấy làm case-by-case, ad-hoc, không còn respect kiến trúc… càng fix càng làm product không stable"* — và bug "một app hiện ở 2 ô" vá 3 lần (tầng state) vẫn không được trên xe.
- **Phương pháp:** 4 lượt review độc lập (2 Opus + 2 Sonnet), read-only, trích `file:line`, mức bằng chứng theo CLAUDE.md §2 ([ĐO]/[SUY]/[CHƯA BIẾT]).
- **Trạng thái code:** MỌI thay đổi gần đây (fix HAL kính/rèm/đèn + one-app-one-slot) còn Ở MÁY, **chưa push** (remote vẫn 1.59).

---

## Kết luận một dòng
Bất ổn là **thật**, nhưng KHÔNG đến từ các bản vá gần đây (chúng được đánh giá là **kỷ luật, đặt đúng tầng**). Nó đến từ **hạ tầng cast + shell** (2 lỗi **P0** thật) và một **đường nối STATE→WINDOW không có bộ reconcile** (gốc của bug ô trùng + cả một lớp bug tái diễn). Bug ô trùng **không thể** sửa ở tầng state — đó là lý do vá 3 lần không ăn.

---

## P0 — sửa TRƯỚC khi test trên xe tiếp

### P0-1 · Cast wire lại một lệnh shell ĐÃ BỊ CẤM (làm treo system_server) [ĐO mâu thuẫn 2 file · SUY crash từ đo cũ của chính repo]
- `core/.../clustercast/simplified/AppMover.kt:111` — nhánh app-thường gọi `am display move-stack $stackId $displayId` (đưa stack từ display 0 lên màn cụm freeform).
- Chính repo đã **CẤM bằng chữ**: `carexec/CarExecClusterProjectionCatalog.kt:60-83` ghi `am display move-stack` = `MAY_HANG_SYSTEM`, **crash system_server 3/3** trên DiLink3 (NPE `DisplayContent.moveStackToDisplay`), CarPlay rớt, phải rút cắm lại, Android 10 không có patch. Lệnh AN TOÀN = `am stack move-task`. `CastShell.kt:264,297,340,412,416` cũng cấm rõ.
- Cùng file lại **mâu thuẫn**: nhánh CP/AA dùng đúng `am stack move-task` (`AppMover.kt:41,385`), chỉ nhánh app-thường dùng lệnh cấm. KDoc bảo "proven trên xe ClusterCast.placeLadder" nhưng ClusterCast là **code chết không chạy tới** (xem P1-3) ⇒ nhãn "proven" lấy từ code chết.
- **Sửa:** đổi nhánh app-thường sang `am stack move-task` (hoặc chặn fallback) — verify trên xe ĐỖ.

### P0-2 · `ShellTransport` KHÔNG có socket timeout → một lệnh treo làm ĐƠ TOÀN BỘ đặt cửa sổ + cast [ĐO — decompiled dadb jar]
- `app/.../system/ShellTransport.kt:57` — `Dadb.create("localhost", 5555, keys)` (3-arg) ⇒ mặc định `connectTimeoutMs=0, socketTimeoutMs=0` = **vô hạn**.
- Mọi lệnh cửa sổ (`placeApp/closeApp/reflow`) + mọi lệnh cast dồn vào 1 worker của `PrioritySerialExecutor` chặn ở `Future.get()` **không timeout**. `adbd` xe wedge giữa chừng (xóc, chớp nguồn, TCP nửa-mở) ⇒ thread đó **chặn vĩnh viễn**: mọi cú chạm ô im lặng không làm gì, cast STOP không preempt được, `VdAppHost` mỗi lần chạm lại spawn thread mới không chặn ⇒ rò thread → có thể OOM/crash. Không exception, không log.
- Repo **đã biết cách**: `car-integration/.../CarExecShell.kt:21-22` truyền `connectTimeoutMs=3_000, socketTimeoutMs=10_000` — nhưng bài học chưa tới `ShellTransport` (đường launcher THẬT dùng).
- **Sửa:** truyền timeout qua `ShellTransport.conn()` như `CarExecShell` + bọc `Future.get(timeout)` đóng/nối-lại khi hết giờ.

---

## P1 — nợ kiến trúc (gốc của bug ô trùng + bug tái diễn)

### P1-1 · KHÔNG có bộ reconcile STATE→WINDOW; app-location có BA nguồn sự thật song song [ĐO]
- **Tile** = hàm thuần của state (`WorkspaceView.render → WorkspaceRenderPlanner.decide`, có test). **Task/cửa sổ THẬT thì KHÔNG.**
- Ba "nguồn sự thật" cho vị trí app: (1) `WorkspaceState.slots` [model — chỗ 3 bản vá đụng]; (2) **task WindowManager thật** trên VirtualDisplay/freeform, chỉ đổi bằng chuỗi `am`, không ai đọc lại; (3) `AppLocationRegistry` — map pkg→(display,slot) sửa tay ở 4 chỗ. `grep reconcile` trong launcher = **0 hit**. Không hàm nào bắt window-set thật khớp state.
- **[ĐO then chốt]:** trên xe `embedding = shell != null` = true ⇒ đường freeform của `LauncherWindows` **early-return, bất động** (`LauncherWindows.kt:105,143,159`). App vẽ HOÀN TOÀN bởi `VdAppHost` trên VirtualDisplay; `windows().placeApp/closeApp` trong `assignApp/clearSlot` là **no-op trên xe** — chỉ sửa registry, không sửa pixel.

### Gốc rễ bug "một app hai ô" [SUY tin cậy cao]
- App = **một task**. `am start --display vd1` cho app đang ở vd0 = **DI CHUYỂN** task sang vd1; phía vd0 task biến mất. Nhưng `VdAppHost` dùng `SurfaceView` — **vẫn vẽ KHUNG ĐÓNG BĂNG cuối** (đúng lỗi "khung đóng băng" H2 đã ghi). Phát hiện chết là **polling**: `SlotLiveness` cần `seenAlive` + **2 lần trượt × 5s** mới ẩn surface ⇒ **~10-15s** app sống ở ô mới VÀ đóng băng ở ô cũ = hiện cả hai.
- Dedup state KHÔNG chữa được: lúc di chuyển state ĐÃ đúng (1 ô); cái trùng là **surface GPU cũ** trên display model không còn trỏ tới. Đây là lỗi **thuần tầng RENDER/WINDOW**.
- **Vector trùng thật** (dedup không tới): (a) `openAppFullscreen`/U3 (`KachiHomeSlots.kt:67-73`) mở app fullscreen display 0, **không đụng state lẫn registry** ⇒ task fullscreen + task VdAppHost = 2 đại diện của 1 app; (b) freeform off-car: `moveToSlot`/resize bị từ chối khi đang fullscreen ⇒ cửa sổ cũ nằm lại; (c) **hai instance Activity Kachi** (H2: 4 activity/4 VD cho 2 ô).
- **[CHƯA BIẾT] owner trúng vector nào** — chốt bằng **1 lệnh `am stack list`** lúc tái hiện trên xe (display nào giữ task + ô nguồn đã trống chưa). CLAUDE.md §14/§15: đo, đừng đoán.

### P1-2 · `openAppFullscreen` tạo task KHÔNG có state/registry [ĐO] — vector trùng độc lập với dedup (`KachiHomeSlots.kt:67-73`).

### P1-3 · BA hệ cast chồng nhau (~7,150 LOC); file lớn nhất là code CHẾT nhưng vẫn bị gọi [ĐO]
- Legacy `modules/clustercast/*` = 4,052 LOC (ClusterCast.kt **1,286 LOC** — file lớn nhất repo), tự khai "unreachable" nhưng còn 1 nhánh sống: `ClusterNavBridgeKeys.kt:246` gọi `ClusterCast.listInstalledApps`. Live `clustercast/simplified/` = 2,856 LOC **chép tay** logic "proven" của legacy. Thứ ba `cast/platform/CastAppCatalog.kt`. ⇒ Fix có thể rơi vào bản chết/bản sống/bản platform. **Đây là "động cơ" của càng-fix-càng-hỏng.**
- **Sửa:** chuyển `listInstalledApps` ra, XOÁ ClusterCast, SimpleCast là nguồn duy nhất.

---

## P2/P3 — nợ cần dọn (không chặn)
- **[P2]** Phân loại CarPlay/AndroidAuto/projection **hardcode 4 danh sách rời** (§7 + DRY): `CastAppCatalog.kt:238`, `ClusterCast.kt:197`, `AppMover.kt:432-449`, `CastStackParser.kt:184-188` — sẽ lệch nhau khi gặp pkg mới.
- **[P2]** Cửa sổ trùng khung-đóng-băng ~10-15s do death-detection polling; thiếu bước **evict** khi đổi ô (`SlotLiveness` DEFAULT_MISSES=2, PROBE_PERIOD_MS=5_000).
- **[P2]** `AppLocationRegistry` sửa tay 4 chỗ, không sync lại từ WM thật ⇒ trôi (rủi ro cast §4/§5 one-location).
- **[P2]** `VdAppHost.maybeLaunch` thread nền không guard `release()` đồng thời (`VdAppHost.kt:139-162`) — bắn `am` vào display đã free; đề xuất bắt epoch token mỗi call-site.
- **[P2]** File > 500 LOC (§4.1): ClusterCast 1286 (chết), SimpleCastCoordinator 648, SpeedBadgeOverlay 600, BydHal 551, KachiHomeActivity 521, FloatingBubbleService 513, NavNotificationListener 506.
- **[P3]** id đọc/ghi lệch namespace cùng control (`window_lf` đọc vs `win_lf` ghi).

---

## Lỗ hổng TEST (vì sao "xanh mà vẫn hỏng")
- Test sâu ở **tầng model thuần** (:core 172 file/1707 test) + rộng ở **quét chuỗi nguồn** (:app 54% dùng SourceRoots). Nhưng **không module nào có test hành vi** chạm WindowManager/HAL thật (không androidTest/Robolectric).
- Đúng ca bug ô trùng: `WorkspaceState` test kín, nhưng **`KachiHomeSlots.assignApp` + `LauncherWindows` (đặt cửa sổ thật, async) KHÔNG có test nào**. Model xanh 100% trong khi glue/teardown regress im lặng.
- HAL: `HalBindingTable` test qua fake gateway (route/arg); `BydHalGateway` (reflection thật) **không test**, mọi lỗi nuốt về `null` ⇒ không phân biệt "không có trên trim" với "ta làm hỏng binding". `HalWriteProbe` bắt được rc thật nhưng chưa có test/baseline khoá lại.
- **Cần:** (1) test hành vi cho `assignApp` (fake dispatcher/windows — khoá THỨ TỰ gọi: đóng ô cũ → remove → place ô mới); (2) baseline on-car đọc `HalWriteProbe` cho vài control đã-verify để binding lệch là đỏ; (3) test reconciler applier (desired vs `am stack list`).

---

## Hiệu chỉnh: bản vá gần đây là TỐT (không phải nguồn bất ổn)
- HAL writeArgs (kính/rèm/đèn) mang **[ĐO xe] + trích RE file:line** (§3), mở rộng bảng-binding TRUNG TÂM (§7 cho phép), không special-case rải rác.
- one-app-one-slot đặt ở **tầng model** (`WorkspaceState.withSlot`) cho MỌI đường + `sanitized()` migrate prefs cũ + 4 test hồi quy. Đúng "generic fix". **Chỉ là chưa đủ** — bug thật nằm DƯỚI tầng này (P1-1).

---

## Lộ trình sửa (đúng tầng, không case-by-case)
1. **An toàn (nhỏ, tự chứa, repo đã có mẫu):** P0-2 timeout `ShellTransport` · P0-1 đổi move-stack→move-task. → trả lại ổn định tức thì.
2. **Đo chốt vector bug ô trùng:** 1 lượt `am stack list` + state lúc tái hiện trên xe (P1-1 [CHƯA BIẾT]).
3. **Cấu trúc (bản vá ĐÚNG cho bug ô trùng):** dựng **1 reconciler** — planner thuần :core `WindowPlan.desired(state)` + 1 applier diff desired vs `am stack list` (có bước **evict pkg khỏi display cũ**) + gọi từ **DUY NHẤT** collector `render(state)`; `assignApp/clearSlot/swap/openAppFullscreen` chỉ đổi STATE. `AppLocationRegistry` thành projection của state.
4. **Gộp cast:** xoá ClusterCast (chuyển listInstalledApps), SimpleCast là nguồn duy nhất; gộp 4 danh sách projection về 1.
5. **Test:** thêm test hành vi ở đường nối state→window + baseline HalWriteProbe on-car.

**Nguồn:** 4 báo cáo review đầy đủ trong phiên 2026-09-15 (kiến trúc/slot · ad-hoc · concurrency · test-integrity).
