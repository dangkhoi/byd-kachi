# kachi-301 · Stage 7 · R26 — cụm freeform: gỡ ép hình học, bỏ `wmSize` theo app, chặn R6 — XONG off-car

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` R26 (nhóm B) + §9. Không commit. Chưa chạy lại trên xe.

## Bằng chứng (xe Seal, Kachi 3.0 (204), `docs/diagnostics/oncar-2026-10-13/`)
- [ĐO] `01-read.txt`: cụm = display 1 `fission_bg_xdjaVirtualSurface` 1920×720, mọi stack `mDisplayWindowingMode=freeform`.
- [ĐO] `04`/`06`: overscan âm, `wm size 1920x760`, overscan 0,90,0,90 ⇒ `mFrame` Waze không đổi (freeform bỏ qua ép mức display).
- [ĐO] `05`: `am task resize 14 0 -40 1920 420` ⇒ `Error: bad top arg: -40`, exit 255.
- [ĐO] `07`: `wm size reset -d 1` + `wm overscan reset -d 1` ⇒ parent 720, owner xác nhận.
- [ĐO] `10-recast-waze-2.txt`: Dừng + chiếu lại Waze ⇒ `Override size: 1920x520` quay lại ⇒ Kachi áp lại wmSize đã lưu.

## Gốc lỗi trong mã [ĐO mã]
1. `core/.../simplified/CastSessionPin.kt` `pinFull` (trước sửa: `val config = saved ?: DisplayConfig.NORMAL_DEFAULT`) lấy nguyên bản
   ghi FULL của app (gồm `wmSize`) → `SimpleCastCoordinatorIntents.kt` `handleCastFull` → `configurator.apply(vd, config)` →
   `DisplayConfigurator.apply` chạy `wm size ${config.wmSize} -d $displayId` ⇒ `wm size 1920x520 -d 1` mỗi lần chiếu.
2. Nguồn của bản ghi: `CastGeometryController.resizeFull` tầng 2 (`wm size ${scaleW}x${scaleH} -d` + `saveDisplayConfig(…wmSize=…)`).
   [SUY] lượt nào làm tầng 1 hỏng để rơi xuống — không chụp được; ứng viên: lệnh có top âm (`05`).
3. V2 mất luật V1 `CastShell.resetDisplayGeometry` (git `93709693` `ClusterCast.applyBounds`: resize ăn ⇒ gỡ size + overscan,
   giữ DPI) ⇒ không có gì gỡ ép hình học.

## Tệp
Mới:
- `core/.../simplified/ClusterDisplayFacts.kt` — THUẦN: `displayFreeform(stackList, id)`, `sanitize(config, facts)`, `editFrameSize(physical, wmSize)`.
- `core/.../simplified/CastFreeformGeometry.kt` — `releaseDisplayGeometry()` (bỏ khi phiên CP/AA hoặc `liveDisplayId < 1`),
  `sanitizedForFreeform`, `clusterFreeform` / `clusterPhysicalSize` (public, chỉ đọc RAM).
- Test: `core/src/test/.../CastFreeformGeometryTest.kt` (9), fixture `core/src/test/resources/diagnostics/oncar-2026-10-13/{01-read,10-recast-waze-2}.txt`
  (chép y byte), `app/src/test/.../launcher/CastFreeformBandWiringContractTest.kt` (2).

Sửa:
- `DisplayConfigurator.kt` — `releaseGeometry(displayId)`: `wm size reset` + `wm overscan reset`, KHÔNG density; `vd < 1` từ chối; lặp
  lại trên cùng display mà chưa có `apply`/`reset` xen giữa ⇒ 0 lệnh; `currentConfig = null`. `apply` xoá dấu trước khi chạy lệnh.
- `CastGeometryController.kt` — tham số `onTaskResized` (gọi sau resize ăn ở `resizeFull`, `resizeSlot`, `applyPinned`); tầng 2 `wm size`
  bị cấm khi `displayFacts().freeform == true`; `displayFacts()` (2 lệnh đọc, MỘT lần mỗi phiên × display, cả khi hụt), `knownFacts`,
  `forgetDisplayFacts`; `applyPinned` kẹp theo Physical khi freeform; `persistBounds` làm sạch `wmSize` cũ của bản ghi.
- `SimpleCastCoordinator.kt` (499 dòng, gộp vào dòng sẵn có) — `onTaskResized = { releaseDisplayGeometry() }`.
- `SimpleCastCoordinatorOps.kt` — `geometry.forgetDisplayFacts()` đầu lượt mở chiếu.
- `CastSessionPin.kt` — `pinFull`/`slotDisplayConfig` qua `sanitizedForFreeform` (Chữ nhật giữ `oneToOne`).
- `CastStyleSessionOps.kt` — `applySessionPin` cuối: cụm freeform ⇒ `releaseDisplayGeometry()` (cả khi chưa có khung để resize).
- `CastContentShiftOps.kt` — R6: `contentShiftSupported` = recipe ∧ `clusterFreeform != true`; `runContentShift` DROPPED trên freeform;
  `applySavedContentShift` bỏ độ dịch đã lưu trên freeform.
- `app/.../launcher/ClusterNavBridgeGeometry.kt` — `geometryFrame()` = `ClusterDisplayFacts.editFrameSize(coordinator.clusterPhysicalSize, wmSize)`;
  bỏ hai hằng FALLBACK không còn dùng.
- `CastRectSessionCoordinatorTest.kt` — một kỳ vọng: cho phép `wm size reset -d 1` (cũng là 1:1) bên cạnh `wm size 1920x720 -d 1`.
- Tài liệu: spec (Changelog Pass 6, R26, §9), `docs/PROJECT-BACKLOG.md` (`CAST-SHIFT-FREEFORM-RISK` → DONE nhờ đo; mới `CAST-FREEFORM-RESET-R26`).

## Bốn câu CLAUDE.md §4 (`wm size reset` / `wm overscan reset -d <cụm>`)
1. Display: `liveDisplayId` ≥ 1 đã xác minh; `< 1` ⇒ 0 lệnh. 2. App: chỉ phiên app thường (CP/AA trả sớm; CP/AA không resize qua
`CastGeometryController`). 3. Loại: lệnh mức display, không chạm stack/task; DPI giữ. 4. Hoàn tác: trạng thái đích là mặc định của màn;
lượt `apply` kế tiếp (app khác / CP) đặt lại cấu hình riêng vì `currentConfig = null`.

## Chi phí (perf)
+2 lệnh ĐỌC (`am stack list`, `wm size -d`) mỗi phiên ở lượt chiếu đầu; +2 lệnh `wm … reset` MỘT lần sau resize/pin đầu trên cụm
freeform; −/+ sau đó 0 lệnh thêm. 0 luồng / 0 nhịp nền.

## Test (đếm từ XML)
- Bộ tập trung (`*CastGeometry* *CastResize* *CastSessionPin* *SimpleCast* *DisplayConfig* *CastContentShift* *SettingsCast* *Geometry*
  *LauncherI18n* *SourcePathHygiene* *LayeringRules* *CastFreeform* *CastRect* *CastApplyPinned* *ClusterRectStale* *CastSafety*
  *CastCoordinator* *CastRobustness* *CastThemeSafe* *Budget* *FileSize*`): core **27 lớp / 229 / 0 hỏng**; app **10 lớp / 70 / 0 hỏng**.
- Toàn `:core:test`: **601 lớp / 5255 / 0 hỏng**.
- Chứng minh đỏ (đã khôi phục): tắt `releaseDisplayGeometry` + làm sạch ⇒ 1 bài đỏ (ca Waze); bỏ chặn tầng 2 ⇒
  `resize hong tren man freeform KHONG lui wm size va khong luu` đỏ.
- Chưa chạy: `./gradlew clean assembleRelease` (cây chung với agent khác).

## Quyết định tự chủ
- Làm sạch lúc ĐỌC (không quét-ghi prefs mọi hồ sơ); bản ghi tự sạch khi người lái chỉnh khung lần tới (`persistBounds`).
- Chỉ làm sạch khi cụm đo được là freeform; chưa đọc được / không freeform ⇒ hành vi cũ (đường lùi `wm size` vẫn là cơ chế thật
  khi freeform chưa sống).
- Dải −/+ dùng kích vật lý ở MỌI chế độ khi đã đo (toạ độ khung luôn là toạ độ vật lý, kể cả tầng 2 tính kích từ khung trong 0..physical).
- `wm overscan reset` luôn gửi (như `DisplayConfigurator.reset`), hỏng trên A11+ không làm hỏng lượt gỡ.

## [CHƯA BIẾT] / cần xe
- 🚗 Cài bản mới, chiếu Waze (bản ghi cũ 1920×520 còn trong prefs): kỳ vọng `wm size -d 1` không còn `Override`, task parent 720.
  Lệnh chốt: `wm size -d 1; am stack list | grep -A2 waze`.
- Trên DL5 (A12) `am stack list` có in `mDisplayWindowingMode` không — không có ⇒ `freeform=null` ⇒ hành vi cũ (an toàn).
