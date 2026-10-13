# kachi-301 · Stage 1 · G — bước −/+ khung chiếu cụm (R1–R4, R7) — XONG phần off-car

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` §2.1, §2.2, §4.1 · R1–R4, R7. Không commit. Chưa chạy máy ảo / xe.

## Gốc lỗi (nhắc lại, mức bằng chứng)
- [SUY — đọc mã] `SettingsCastGeometryBlock.nudge` đọc lại bản ghim NGAY sau `setGeometryBounds`, mà lệnh áp chạy bất đồng bộ trên
  executor ⇒ bấm lần 1 thấy số cũ, lần 2 nhảy 40, bấm nhanh nuốt bước. Đây là gốc chính (R1).
- [ĐO test] Hàng 1 chỗ + bỏ-cũ-nhất của `BoundedCastExecutor` cũ ĐÃ tự gộp chuỗi bấm của MỘT ô khi executor bận (gỡ hàng gộp mới
  vẫn ra 1 lệnh trong bài "3 lần bấm khi bận"). Cái nó làm hỏng: (a) chia đôi — lệnh nửa phải đẩy lệnh nửa trái khỏi hàng, nửa trái
  mất im lặng; (b) mỗi cú bấm có thể đẩy việc chờ của người khác (chip DPI…); (c) lệnh hỏng không báo về màn.

## Tệp đã đổi
Mới:
- `core/.../clustercast/simplified/CastResizeQueue.kt` — hàng gộp: một ô chờ / ô khung (full · L · R), tối đa 1 việc xả trên executor
  mỗi chuỗi bấm, kết cục APPLIED/FAILED/DROPPED cho người nghe, tự gỡ kẹt khi executor bỏ việc (`dropped(tag)` nối `onDiscard`) hoặc
  việc chờ quá `castTimeoutMs` (xếp lại).
- `core/.../clustercast/simplified/CastGeometryIntent.kt` — `CastEdge`, `CastGeometryIntent<K>` (ý định màn, cộng dồn + kẹp đồng bộ),
  `CastGeometrySteps` (5/20/50, mặc định 20, khoá `cast_frame_step`), `SimpleCastState.geometryKey()`.
- `app/.../launcher/SettingsRepeatTouch.kt` — nhấn giữ: 400 ms rồi mỗi 150 ms; đã lặp thì nuốt ACTION_UP (không thêm bước); gắn
  từ ngoài vào hàng `stepperRow` (2 con clickable = −, +). 0 luồng / 0 nhịp nền.
- `app/src/main/res/values{,-en,-zh-rCN,-th,-ms}/strings_kachi_geom301.xml` — 3 chuỗi × 5 ngôn ngữ.
- Test: `core/src/test/.../CastResizeQueueTest.kt` (8 bài thuần + 3 bài coordinator thật), `CastGeometryIntentTest.kt` (8 bài),
  `app/src/test/.../SettingsCastGeometryWiringContractTest.kt` (6 bài dây nối).

Sửa:
- `core/.../SimpleCastCoordinator.kt` (499 dòng): `resizeQueue` (public val, kiểu tường minh), `onDiscard` báo hàng gộp,
  `resizeActiveTarget/Slot` → `resizeQueue.offer(...)` (chữ ký giữ, vẫn trả Boolean).
- `core/.../CastSessionPin.kt`: `runResize`; `resizeFullBody/resizeSlotBody` trả `CastResizeResult` (trước: Unit).
- `core/.../CastGeometryGuard.kt`: `clampBounds(..., maxRight, maxBottom)` (overload 7 tham số; 4/5 tham số giữ y hành vi cũ),
  `overflowMax(frame)` = +50 % (trần 8192), `OVERFLOW_PCT = 50`. `BOUNDS_RE`/`boundsOk`/`DIMENSION_MAX` không đổi — khung tràn
  (≤ 2880×1080) vẫn qua chốt lưu/áp ([ĐO test] `CastGeometryIntentTest` R7).
- `app/.../ClusterNavBridgeGeometry.kt`: `geometryClamp`, `geometryResizeBusy`, `observeGeometryOutcome`, `geometryStep`,
  `setGeometryStep`; `clampToBand` cho tràn dưới mọi ô, tràn phải CHỈ toàn cụm; `resetGeometry` trả khung đã gửi; bỏ `GEOMETRY_STEP_PX`.
- `app/.../SettingsSectionsCastGeometry.kt`: ý định + `sync()` + nghe kết cục (Toast khi FAILED) + chip Bước + dòng phụ tràn + nhấn giữ.
- `app/.../SettingsSectionsCast.kt` (`liveStatus` thôi): khoá nhẹ `geometryKey` — đổi mà `statusKey` y nguyên ⇒ chỉ `geometryBlock.sync()`.

⚠ Ngoài danh sách tệp được giao (cần thiết, báo rõ):
- `core/.../CastGeometryController.kt` — MỘT dòng ở `applyPinned`: kẹp với trần tràn. Không sửa thì mỗi lần chiếu lại/repin khung
  tràn bị kẹp về 0..W×0..H ⇒ R7 vô nghĩa.
- `core/.../launcher/ProfileScopeCluster.kt` (`CAST_PROFILE_KEYS` → `PROFILE_EXTRA_KEYS`, `DECLARED_TYPES` INT) và
  `ProfileSharePolicy.kt` (R_CAST) — đăng ký khoá hồ sơ R4 (được giao "register properly").
- Test cũ sửa theo hành vi mới: `CastSessionPinTest` (`CastApplyPinnedTest` — kỳ vọng kẹp 1280×480 nay +50 % tràn; đầu vào đổi để
  vẫn chứng minh có kẹp), `ProfileScopeClusterTest` (thêm khoá), `ClusterProfileSwitchWiringContractTest` (literal phép kẹp thêm `maxRight`).

## Hợp đồng đổi
- `SimpleCastCoordinator.resizeQueue: CastResizeQueue` (mới, public) — `offer/busy/addListener/dropped`.
- `resizeFullBody/resizeSlotBody`: `Unit` → `CastResizeResult` (internal).
- `ClusterNavBridge.resetGeometry(target)`: `Unit` → `CastBounds`.
- Thẻ executor của −/+: `"resize"` cho cả hai (trước `"resize-slot"` riêng cho nửa) — chỉ ảnh hưởng log `DISCARDED:`.
- Khoá prefs mới `cast_frame_step` (Int, tệp `simple_cast_prefs`, theo HỒ SƠ, chia sẻ được). Đặt tên tránh "geom" vì bài
  `ProfileSharePolicyTest.locationShaped` bắt gốc "geo" (dương tính giả). Ghi/đọc thẳng `getSharedPreferences(SIMPLE_CAST_FILE)` ở
  cầu (không thêm vào `SimpleCastPrefs` vì dịch vụ chiếu không đọc khoá này) — [quyết định tự chủ].
- Không thêm loại lệnh shell nào (CLAUDE.md §4): vẫn đúng `am task resize` qua `CastGeometryController`.

## Test (đếm từ XML `build/test-results`)
- `:core:test` (CastGeometry*, CastSessionPin*, SimpleCast*, CastResize*, CastApplyPinned*, ProfileScope*, ProfileSharePolicy*,
  CastRect*, SettingsCatalog*, ClusterSnapshot*, PersistentWindowState*): **211 / 0 fail** (23 lớp).
- `:app:compileDebugKotlin` xanh; `:app:testDebugUnitTest` (\*Geometry\*, \*SettingsCast\*, \*ClusterProfile\*, \*SettingsScreenWiring\*,
  \*SettingsCatalogControl\*, \*TypeScale\*, \*SettingsStackMargin\*, \*ClusterRectOption\*, \*Profile\*Key\*, \*ClusterNavKeys\*,
  \*LayeringRules\*, \*ProfileIo\*): **147 / 0 fail** (15 lớp). Thêm sửa `ClusterProfileScopeCoverageTest.indirect` (khoá ghi bằng
  hằng `:core` — bộ quét literal không thấy; bằng chứng `putInt(CastGeometrySteps.KEY,` ở cầu).

## Chứng minh đỏ (gỡ bản vá ⇒ đỏ, đã khôi phục)
- `CastGeometryIntent.nudge` cộng trên `truth` (hành vi cũ) ⇒ `nam lan bam nhanh… = dung +100` đỏ (`[20,20,20,20,20]`) + 2 bài khác.
- `resizeActiveSlot` về `executor.submit("resize-slot")` ⇒ `chia doi - chinh hai nua khi executor ban` đỏ (1 lệnh, nửa trái mất).
- App: thêm lại `show(bridge.geometryBounds(target))` sau `setGeometryBounds` trong `nudge` ⇒ `SettingsCastGeometryWiringContractTest` R1 đỏ.
- `resizeActiveTarget` về `executor.submit("resize")` ⇒ `ba lan bam khi executor ban…` đỏ ở kết cục R2 (không có tín hiệu về màn);
  số lệnh thì KHÔNG đỏ — xem [ĐO test] ở trên.

## Rủi ro / chưa biết
- [CHƯA BIẾT] WM có giữ khung `right > W`/`bottom > H` cho task freeform trên màn ảo cụm không (shell nhận — [ĐO source A10]
  `ActivityManagerShellCommand.getBounds` chỉ chặn trái/trên âm). Nếu WM kẹp lại, bản ghim nói số tràn mà cụm không tràn; cụm Chữ nhật
  `verifyFrame` sẽ thấy lệch và resize lại một lần rồi log. Cách chốt: máy ảo API 29 màn phụ, `am task resize <id> 0 0 2400 900` rồi
  `am stack list` đọc bounds.
- [SUY] Nhấn giữ trong ScrollView: trang cuộn giành chạm ⇒ ACTION_CANCEL ⇒ dừng lặp (đúng ý). Chưa thử tay trên máy ảo.
- [SUY] FAILED khi trạng thái đổi giữa chừng được báo DROPPED (im) — không có Toast giả khi người lái vừa Dừng chiếu.
- Toast lỗi dùng `android.widget.Toast` (Activity Cài đặt) — không qua KachiDialog (T2) vì là thông báo thoáng qua.
- Chưa: QA máy ảo (bấm + ×5 ⇒ `am stack list` top=100), ảnh Sáng/Tối hàng chip Bước — thuộc T8.
