# kachi-301 · stage 4 · W2 (R20 cân đối — owner 13/10) — DONE (off-car, chưa commit)

Owner 13/10: *"chỉ số lệch phải… canh cho cân đối hết, nhiều cái hiện số lên nằm chơi vơi"*. Đảo UX7 (OQ §7.1) + sửa CARD ô cao
trống (§7.2). Bằng chứng: `docs/diagnostics/widget-fit-301-2026-10-13.md` §8 + ảnh `widget-fit-301/09|10|11-*-balance.png`.

## Tệp
- `core/.../CellTextLayout.kt` — `lineStartX(centerX, lineWidth, left, right, inset)`: bỏ `anchorWidth`, cân giữa CẢ khối.
- `app/.../AxisRow.kt` (KDoc + truyền bề rộng cả khối) · `TyreBoardView.kt` (2 chỗ gọi) · chú thích chỗ gọi ở `WidgetTelemetry.kt`,
  `WidgetViews.kt`, `ReadTile.kt`, `GroupTileViews.kt`.
- `app/.../GroupTileViews.kt` (498 dòng): trường `breath` + `onLayout` dời thân xuống nửa khoảng thở. Không đổi phép đo.
- Test: `core/.../CellTextLayoutTest.kt` (luật mới, 21 ca) · `CompositeWidgetLayoutContractTest.kt` (+1 bài: AxisRow truyền
  bề rộng cả khối, không `anchorWidth`) · `GroupTileTightSpaceContractTest.kt` (cấm `weight` trước thân + bắt `onLayout` dời) ·
  `Goi2FeatureWiringContractTest.kt` (chữ thông báo).

## Test [ĐO XML]
`:core:test` (*Fit* *Grid* *Group* *CarStrip* *Widget* *CarStatus* *CellText*) **343/0** ·
`:app:testDebugUnitTest` (*GroupTile* *Widget* *Fit* *Axis* *UX7* *CarStrip* *ReadGrid* *Spacing* *TypeScale* *LayeringRules*
*Composite* *DummyCar* *Control* *Goi2* *Tyre* *ReadTile*) **241/0**. Full suite CHƯA chạy.
Red-proof [ĐO]: `lineWidth / 3f` ⇒ CellTextLayoutTest đỏ 3; AxisRow truyền `anchor.measuredWidth` ⇒ Composite đỏ 1; bỏ khoảng
trên (bản Space cũ) ⇒ TightSpace đỏ 1; khôi phục ⇒ xanh.
Call site §8: `breath` (gán ở `bind`, đọc ở `onLayout`) · `offsetTopAndBottom(half)` · `lineStartX(` 3 chỗ (AxisRow, Tyre×2).

## Máy ảo [ĐO]
- APK `:app:assembleVehicleTest` 3.1 (205) — **CHƯA bump** (orchestrator quyết §9) — cài emulator-5556 12:2x.
  ⚠ Máy ảo đầy (/data 94%, 359 MB trống) ⇒ `INSTALL_FAILED_INSUFFICIENT_STORAGE`; đã cài bằng cách tạm
  `settings put global sys_storage_threshold_max_bytes 104857600` rồi `settings delete` ngay (giá trị gốc `null`, đã trả).
- Kiểm kê lại (harness `docs/diagnostics/widget-fit-301/harness/`, + `balance.py`, `ink.py`, `owner.sh`): 106/106 bad=0.
- Sao lưu prefs trước khi đụng: scratchpad `wfit2/backup/` (8 tệp). So sánh hiện tại: chỉ khác `lang`/`QA2__lang`
  (cố ý) + `a11y_proc_start_elapsed` (app tự ghi).

## Màn hình để lại cho owner
HOME ở trước, bố cục owner (QA2: YouTube · Lốp · Chuyến đi), **Tiếng Việt**, **số giả BẬT**.
Trả lại: ngôn ngữ trước đó = `auto` (cả `clusternav_lang.xml` `lang` và `kachi_workspace.xml` `QA2__lang`) — chọn "Tự động"
trong Cài đặt › Ngôn ngữ; tắt số giả: `adb -s emulator-5556 shell am broadcast -a com.byd.clusternav.vehicletest.DUMMY_CAR
-p com.byd.launcher --ez on false` (hoặc tự hết khi Kachi khởi động lại).

## Còn mở / đề nghị BACKLOG
- `WIDGET-AXISROW-OQ` ⇒ **Done** (owner 13/10 chốt cân giữa cả khối). `WIDGET-CARD-LARGE-EMPTY` ⇒ **Done**.
- MỚI `WIDGET-MULTI-ROW-ICON-GAP`: ô nhiều mục, icon dính mép trái, cặp số cân giữa phần còn lại ⇒ icon xa số 40–60 px; cần
  owner chọn danh sách canh trái hay giữ.
- INDEX: thêm 3 ảnh `*-balance.png` + §8 vào mục Evidence của widget-fit-301.
