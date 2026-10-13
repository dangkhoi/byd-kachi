# kachi-301 · stage 4 · W (R19–R21 widget co giãn theo ô) — DONE (off-car, chưa commit)

Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` §2.5 + nhóm E. Bằng chứng: `docs/diagnostics/widget-fit-301-2026-10-13.md`
+ `docs/diagnostics/widget-fit-301/` (13 PNG ≤ 1280 px, 1,3 MB; `harness/` script + bảng kiểm kê trước/sau/số giả).

## Phạm vi
- TRONG: R19 kiểm kê 25 widget × 4 cỡ ô thật (tiếng Việt chính + en/zh/th/ms); R20 sửa tầng chung; R21 không hồi quy ô lớn + chi phí.
- Owner bổ sung giữa chừng (qua orchestrator): tiếng Việt là ngôn ngữ đo chính; canh lề cân đối khi có số thật; **số giả chỉ bản vehicleTest**.
- NGOÀI scope, KHÔNG làm (đề nghị vào backlog): OQ `AxisRow` UX7 (cụm `112 km/h` lệch phải — đảo quyết định UX7 cần owner) ·
  thẻ CARD ô lớn nửa dưới trống · Kính tiếng Mã Lai ở ô 2×3 rơi về tóm tắt · số giả cho nhạc (MediaSession).

## Tệp (git status)
Mới: `core/.../GroupCellFit.kt` (188) · `core/.../CarStatusTap.kt` (32) · `app/.../GroupCellGrid.kt` (323) ·
`app/src/vehicleTest/java/com/byd/clusternav/DummyCarReceiver.kt` · tests `core/.../GroupCellFitTest.kt`,
`core/.../FitRulesWordSplitTest.kt` (2 lớp), `app/.../DummyCarTapContractTest.kt`.
Sửa: `GroupTileViews.kt` (500→486) · `GroupTileParts.kt` (ReadGrid ⇒ `TrimArea` + `overflowPx`; `actionsRow` ⇒ `GroupActionGrid`) ·
`FitProbe.kt` (+2 luật) · `FitScale.kt` (499, 1 dòng đổi) · `FitValueRow.kt` (`align`) · `core FitRules.kt` (`splitsWord`,
`tooManyLines`, `FREE_LINES`) · `KachiHomeWiring.kt` (498; thu trạng thái xe qua `CarStatusTap`) · `app/src/vehicleTest/AndroidManifest.xml`
(receiver `DUMMY_CAR`) · `GroupTileWiringContractTest.kt` (bài ô chèn: nay khoá hai lưới tự đặt vị trí; `GroupCellGrid.kt` vào
`notBuilders` vì chỉ bố trí ô đã dựng). KHÔNG đụng tệp hộp thoại của agent khác.

## Thiết kế
- `GroupCellFit.plan(needs, width, heightLimit?, metrics, maxCols, icons)` → `Plan(shown, rowCounts, option, rowPx, fits, columns)`:
  cột nhiều nhất ≤ trần cũ (5 dải / 3 thẻ ⇒ ô rộng y cũ); bậc dạng icon → nhãn 1 dòng [ngang hai cột chung · dọc] → nhãn 2 dòng
  (ngắt ở dấu cách, `twoLineWidth`); ép cao ⇒ bỏ icon → bỏ mục cuối. `actionCols` cho hàng nút (≥ đích chạm 48 dp).
- `GroupCellGrid` (ViewGroup) đo nhu cầu bằng `Paint` thật khi dấu chữ đổi; lượt đo đầu KHÔNG trần cao (báo đúng chỗ cần), lượt
  nhường đặt trần; `recheck()` ở `GroupTileView.refresh` vì cột số/nhãn bề rộng tĩnh. Ngang = nhãn canh phải sát số, số canh trái,
  cặp canh giữa (owner #2 "lệch phải").
- `GroupTileView.onMeasure`: vòng ≤ 4 lượt, vùng nhường theo thứ tự [số chính `ReadGrid(LEAD_ROW)`, lưới]; phần tràn = max(tổng con −
  hộp, Σ`overflowPx`) — cha `WRAP` kẹp mất phần tràn nên phải tự báo.
- `FitProbe.clippedText` thêm: từ bị bẻ (chữ Latinh/Kirin/Hy Lạp/số; Thái/Hán miễn) · chữ không `maxLines` > 2 dòng.
- `FitValueRow.align`: hàng ngang số `END` + chú thích `START`, dọc ⇒ canh gốc.
- Số giả: `CarStatusTap` (core, mặc định null) ← chỉ `DummyCarReceiver` (vehicleTest) gán.
  Lệnh: `adb -s emulator-5556 shell am broadcast -a com.byd.clusternav.vehicletest.DUMMY_CAR -p com.byd.launcher --ez on true|false`.

## Test [ĐO XML, chạy bằng gradle-locked.sh]
- `:core:test --tests '*Fit*' '*Grid*' '*Group*' '*CarStrip*' '*Widget*' '*CarStatus*'` ⇒ **323 / 0 fail**
  (`core/build/test-results/test`); trong đó `GroupCellFitTest` 8/0, `FitRulesWordSplitTest` 4/0, `FitRulesFreeLinesTest` 1/0.
- `:app:testDebugUnitTest --tests '*GroupTile*' '*Widget*' '*Fit*' '*CarStrip*' '*ReadGrid*' '*Spacing*' '*TypeScale*' '*LayeringRules*'
  '*Composite*' '*DummyCar*' '*Control*'` ⇒ **216 / 0 fail**.
- Full suite CHƯA chạy (orchestrator). `testVehicleTest` (VehicleTestSurfaceContractTest) CHƯA chạy — receiver mới không đụng
  hai phần tử nó khoá (`HudSignProbeReceiver`, `HalProbeReceiver`) [SUY].
- **Red-proof**: thay `for (cols in minOf(shown, maxCols) downTo 1)` bằng số cột cố định (đúng bệnh cũ) ⇒ ĐỎ 3/7:
  `chuyen di 267 px - mot cot nhan so mot dong, khong vo chu`, `dai STRIP co icon…`, `bi ep cao thi bo muc CUOI…`; khôi phục ⇒ xanh
  (cmp bản sao lưu).
- Call site (§8) đều ≥ 1: `GroupCellGrid(` · `GroupActionGrid(` · `.recheck()` · `FitRules.splitsWord(` · `FitRules.tooManyLines(` ·
  `row?.align(` · `CarStatusTap.apply(` · `GroupCellFit.plan(` · `GroupCellFit.actionCols(`.

## Máy ảo / E2E [ĐO]
- APK `:app:assembleVehicleTest` (versionName 3.1, versionCode 205 — **CHƯA bump**; orchestrator quyết theo §9) cài `emulator-5556`.
- Kiểm kê trước/sau: 25 widget × 4 cỡ, vi: cờ 98 → 0 (tổng `bad=` của analyze.py); số giả vi: 0; en/zh/th: 0; ms: 0 lỗi thật.
- Dữ liệu owner: `shared_prefs` khôi phục từ bản sao lưu 10:52 — sha khớp 7/8 tệp; `clusternav_prefs.xml` chỉ khác
  `a11y_proc_start_elapsed` (app ghi lúc khởi động). HOME đang ở trước, bố cục owner (QA2, ô 3 = Chuyến đi), **số giả ĐANG BẬT**
  để owner xem (RAM, mất khi Kachi khởi động lại; tắt: `--ez on false`).

## Chi phí (R21)
Đo `Paint` chỉ khi dấu chữ đổi; nhịp 1 Hz chỉ dựng chuỗi dấu (~10 ô con/nhóm), chữ số rộng nhất đo 1 lần/ô; plan thuần trong
`onMeasure` (≤ 4 lượt khi chật); 0 luồng/nhịp/listener mới. Phiên bản cũ đã có `requestLayout` mỗi nhịp khi chữ số `WRAP` đổi —
dạng ngang mới dùng bề rộng tĩnh ⇒ ít lượt layout hơn [SUY, chưa đo perf-snapshot].

## Đề nghị INDEX/BACKLOG (orchestrator gộp, tránh ghi chồng với agent khác)
- INDEX: `diagnostics/widget-fit-301-2026-10-13.md` + `diagnostics/widget-fit-301/` — Evidence 2026-10-13.
- BACKLOG mới: `WIDGET-AXISROW-OQ` (owner chốt UX7 hay canh giữa cụm) · `WIDGET-CARD-LARGE-EMPTY` · `WIDGET-MS-WINDOWS-SUMMARY` ·
  `WIDGET-DUMMY-MEDIA`.
