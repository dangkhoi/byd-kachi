# kachi-301 · Stage 6 · R6 — "Dịch nội dung" (overscan TOP có dấu, theo app) — XONG phần off-car

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` R6, §4.2, §9 (mục "T7 (R6)"). Không commit. Chưa chạy trên xe / máy ảo.
> Ca nghiệm thu (owner 13/10): *"đang bị ở app Waze, khi chiếu qua cụm nó dư tầm 20px trên top, chứ gmaps hay vietmap chiếu qua cụm ok"*.

## Vì sao làm dù máy ảo ĐỎ (mức bằng chứng)
- [ĐO máy ảo A10] overscan âm không dời activity thường trên overlay display (`emu-overscan-neg-2026-10-13.md`).
- [ĐO mã] bản đang chạy đã áp overscan ÂM cho CarPlay (`DisplayConfig.CARPLAY` top −120); ClusterNav V1 chỉnh khung bằng `wm overscan`
  + tự kiểm `overscanVerified`. Màn cụm BYD là màn ảo `xdja`, khác overlay display.
- [CHƯA BIẾT] ROM DiLink3 có dời cửa sổ Waze/VietMap theo overscan âm không ⇒ thiết kế = ÁP → ĐO → GIỮ/TRẢ lúc chạy, app không nhận
  thì hàng tự ẩn. Không `if` theo tên gói (§7).

## Cách owner chỉnh Waze −20 (đường bấm)
Chiếu Waze **toàn cụm** (nút nổi › Waze, không chia đôi) → mở **Cài đặt › Chiếu cụm** → khối **Khung và DPI (khi đang chiếu)** →
chip **Bước: 5 px** (hoặc 20 px) → hàng **"Dịch nội dung (âm = lên)"** bấm **−** tới **−20 px** (Bước 5: 4 lần; Bước 20: 1 lần;
nhấn giữ thì lặp). Kachi áp rồi tự đo: nhận ⇒ lưu cho Waze (hồ sơ đang dùng), lần sau chiếu Waze tự áp; không nhận ⇒ cụm trả ngay
như cũ + dòng báo *"App này không nhận dịch nội dung trên xe này"*, hàng ẩn với Waze. GMaps/VietMap không bị đụng (giá trị riêng = 0).

## Tệp
Mới:
- `core/.../simplified/CastCoalescingQueue.kt` — thân hàng gộp tách từ `CastResizeQueue` (generic khoá/yêu cầu/kết cục), luật + chữ log giữ.
- `core/.../simplified/CastContentShift.kt` — THUẦN: dải −200..200, `topOf/withTop/clamp`, `usableOn(sdk ≤ 29)`, bộ đọc `probe`
  (khối `Window #… <gói>/…` trên đúng display, ưu tiên `ty=BASE_APPLICATION`, đỉnh nội dung = `mFrame.top` + inset overscan top),
  phán quyết `moved` (đúng chiều, ≥ ½ độ xin), `CastShiftRequest`, `CastShiftResult` (APPLIED/UNCHANGED/INEFFECTIVE/FAILED/DROPPED),
  hai lệnh ĐỌC `WINDOWS_CMD`/`DISPLAYS_CMD`, tiền tố khoá phán quyết `cast_shift_verdict_`.
- `core/.../simplified/CastContentShiftOps.kt` — `CastContentShifter` (đọc overscan thật → mốc → `applyOverscan` → đọc lại 4×150 ms →
  giữ hoặc trả), `runContentShift`, `applySavedContentShift` (lúc chiếu, CUỐI), `undoContentShift` (lúc Dừng, đọc sự thật),
  `shiftContent`/`contentShiftSupported` (public).
- `app/.../launcher/ClusterNavBridgeContentShift.kt` — hiện/ẩn, số của phiên, gửi, nghe kết cục.
- `app/.../launcher/SettingsCastShiftRow.kt` — hàng −/+ (ý định ngay, chip Bước chung, nhấn giữ, a11y).
- `app/src/main/res/values{,-en,-zh-rCN,-th,-ms}/strings_kachi_shift301.xml` — 5 chuỗi `kshift_*` × 5 ngôn ngữ.
- Test: `core/src/test/.../CastContentShiftTest.kt` (8), `CastContentShifterTest.kt` (5), `CastContentShiftCoordinatorTest.kt` (7);
  `app/src/test/.../SettingsCastShiftWiringContractTest.kt` (5); fixture `core/src/test/resources/diagnostics/emu-overscan-neg-2026-10-13/`
  (8 tệp, `cmp` = nguyên văn từ `docs/diagnostics/…`).

Sửa:
- `CastResizeQueue.kt` — ủy quyền cho `CastCoalescingQueue` (API công khai y nguyên).
- `SimpleCastCoordinator.kt` (499 dòng) — `shiftQueue`; `onDiscard` báo cả hàng dịch; bớt một dòng trống.
- `SimpleCastCoordinatorIntents.kt` — `applySavedContentShift` sau `applySessionPin` ở hai nhánh CastFull (nhánh "đã trên cụm" đối soát
  cả giá trị 0); `undoContentShift()` cuối nhánh Dừng của app thường.
- `CastSessionPin.kt` — `FullPin.shift`; `pinFull` tách TOP overscan khỏi `config`/`pinned` (Bo tròn) ⇒ lượt `apply` đầu không mang độ dịch.
- `DisplayConfigurator.kt` — `applyOverscan(displayId, overscan)` (chốt sạch, `vd < 1` từ chối, cập nhật `currentConfig`).
- `ProjectionRecipe.kt` — `supportsOverscan` (mặc định false) + tham số `of`.
- `SimpleCastModels.kt` — `SimpleCastPrefs.contentShiftVerdict/setContentShiftVerdict` (mặc định không lưu).
- `CastGeometryIntent.kt` — `geometryKey` FULL mang cả overscan (màn đồng bộ số khi bản ghim đổi).
- `ProfileScopeCluster.kt` — `DEVICE_KEY_PREFIXES` += `cast_shift_verdict_` (phạm vi XE).
- `ClusterProfile.kt` — `supportsOverscan = diLink < 5`; `projectionRecipe(sdkInt = Build.VERSION.SDK_INT)`.
- `SimpleCastRuntime.kt` — `SharedPrefsSimpleCastPrefs` lưu phán quyết (`"ok"`/`"no"`).
- `SettingsSectionsCastGeometry.kt` — dựng hàng, nghe kết cục, `sync`, `detach`.
- Test fake `CastCoordinatorTestFakes.kt` — `FakeShell.responders` (trả lời lệnh đọc), `FakePrefs` lưu phán quyết.

## Bốn câu CLAUDE.md §4 (`wm overscan … -d`)
1. Display: `liveDisplayId` ≥ 1 (đã xác minh); hoàn tác qua `undoTargetDisplay`. 2. App: chỉ `CastingFull` app thường, gói khớp — không
CP/AA, không chia đôi. 3. Loại: lệnh mức display, không chạm stack; chỉ đời xe/API có lệnh, chỉ cụm Bo tròn. 4. Hoàn tác: không dời ⇒
trả ngay; Dừng ⇒ TOP về 0 (đọc `mDisplayInfoOverscan`); tiến trình chết ⇒ lượt mở chiếu kế áp `NORMAL_DEFAULT` (overscan 0) lên id dò
sống, `closeOrphanProjection` ⇒ `wm overscan reset` — quyết bằng VD dò thật, nên KHÔNG thêm dấu prefs mới [quyết định tự chủ].

## Quyết định tự chủ
- Hàng HIỆN khi chưa đo (phải cho người lái áp lần đầu thì mới đo được); ẩn sau một lần đo "không nhận". Spec R6 viết "chỉ hiện khi đo
  thấy ăn" — đây là cách đọc khả thi duy nhất; ghi lại để owner chốt.
- Phán quyết theo (XE × gói), không theo hồ sơ người lái: đó là sự thật đo được, không phải sở thích. Độ dịch thì theo hồ sơ (bản ghi `config_*`).
- Về 0 áp thẳng, không đo/không trả (0 là trạng thái gốc an toàn).
- Chữ Nhật: không có hàng (overscan 1:1 cho lớp km/h — `ClusterRectLayout.oneToOne`).
- Không hỏi quyền kênh trước khi gửi (giống bốn thanh mép, khác chip DPI).

## Chi phí (perf)
Mỗi chuỗi bấm: 2–3 lệnh đọc (`dumpsys window displays|grep`, `dumpsys window windows|grep` ×≤5) + 1–2 `wm overscan`, ~0,25–0,85 s
trên executor chiếu. Lúc chiếu toàn cụm: 0 lệnh thêm nếu app không có độ dịch lưu (nhánh "đã trên cụm" +1 lệnh đọc trên đời xe có
lệnh). Lúc Dừng (app thường, đời xe có lệnh): +1 lệnh đọc. 0 luồng / 0 nhịp nền mới.

## Test (đếm từ XML)
- `:core:test` (bộ lọc gồm `*CastGeometry* *CastResize* *CastSessionPin* *SimpleCast* *DisplayConfig* *Overscan* *ClusterProfile*
  *Geometry* *SourcePathHygiene* *CastContentShift* *ProfileScope* *ProfileSharePolicy* *ProjectionRecipe* *CastApplyPinned*`):
  **593 lớp / 5214 bài / 0 hỏng** (bộ lọc khớp rộng).
- `:app:testDebugUnitTest` (cùng bộ lọc + `*SettingsCast* *LauncherI18n* *KachiDialogMigration*`): **13 lớp / 133 bài / 0 hỏng**.
- Chứng minh ĐỎ (đã khôi phục): `moved()` luôn `true` ⇒ 4 bài đỏ (Maps/Settings không dời, sai chiều, app không dời qua coordinator);
  gỡ `undoContentShift()` ở Dừng ⇒ 2 bài đỏ (ca Waze, đổi hồ sơ).
- Chưa chạy: `./gradlew clean assembleRelease` (agent khác đang sửa cây chung); đã chạy `:app:compileDebugUnitTestKotlin` (gồm tài nguyên).

## [CHƯA BIẾT] — chỉ xe trả lời được (tự đo trong app sẽ trả lời ở lần bấm đầu)
- ROM DiLink3 có dời cửa sổ **Waze** theo overscan TOP âm không (Waze có `FLAG_LAYOUT_IN_SCREEN` không — chưa đo). Nếu không ⇒ hàng ẩn
  với Waze, và cần đường khác (vd R7 tràn dưới + top không âm không làm được — framework chặn).
- Task chiếu toàn cụm của app thường được `am task resize` thành **freeform** — máy ảo cho thấy freeform bỏ qua overscan hoàn toàn. Nếu xe
  giống vậy, mọi app thường sẽ bị đo "không nhận". Lệnh chốt nhanh trên xe (khi Waze đang chiếu, sau đó trả ngay):
  `wm overscan 0,-20,0,0 -d <cụm>` → `dumpsys window windows | grep -E 'Window #|mDisplayId=|mFrame='` → `wm overscan 0,0,0,0 -d <cụm>`.
- Khi app cập nhật mà bắt đầu nhận: phán quyết "không nhận" là bền, chưa có nút đo lại (ghi nợ nếu owner cần).
- ⚠ Đường cũ (không đổi ở đây): CarPlay để lại overscan `10,-120,10,50` sau Dừng ở cụm Bo tròn (chỉ density reset) — đã có từ trước.
