# kachi-301 · stage 6 · ICON → R22·C (chạm chấm đi vòng · 2 nút chân · cốp trên hình xe · bỏ mảng xanh) — DONE off-car (máy ảo), chưa commit

Owner 13/10, theo thứ tự: (1) *"mấy cái cửa sổ cũng làm cho nó đẹp nha, tinh vi hơn"* (icon cửa sổ nhỏ) → (2) đổi phạm vi: *"nhấn vô
các hình tròn cho nó mở đóng luôn… thêm 1 icon mở all, 1 icon đóng all là xong… nhấn lần 1 mở 50%, lần 2 mở hết, lần 3 đóng hết…
thêm cả cốp vào đó luôn"* → (3) *"khi click sao nó có mảng xanh hiện lên vậy"*. Công tắc chế độ `Mở hẳn | Hé` và chân 3 nút ĐÃ GỠ.

## Tương tác (ô Kính, hình xe gọn)
| Chạm | Bước (quyết ở `:core`) | Lệnh [ĐO logcat `KachiCtl` máy ảo] |
|---|---|---|
| chấm kính, mức 0 / chưa đọc | → 50 % | `win_half_lf` `setBodyWindowCtrlState [1,4]` |
| chấm kính, 0 < mức < 50 (vd 35) | → 50 % | `win_half_*` |
| chấm kính, 50 ≤ mức < 100 | → 100 % | `win_lf [1,1]` |
| chấm kính, 100 % | → đóng | `win_lf [1,2]` (TOGGLE tắt) |
| chấm cốp (đuôi xe) | mở ↔ đóng theo lệnh cuối | `trunk voiceCtlBackDoor [1]` / `[3]` |
| nút chân ⇊ "Mở tất cả kính" | đặt BẬT tường minh (bấm 2 lần vẫn mở) | `windows_all setAllWindowState [1,1,1,1]` ×2 |
| nút chân ⇈ "Đóng tất cả kính" | — | `windows_close_all setAllWindowState [2,2,2,2]` (chỉ kính, không cốp) |

Mức hiện tại = đích lạc quan vừa gửi (còn hạn 12 s) hoặc số đọc `window_*` ⇒ chạm nhanh liên tiếp đi 50→100→0 [ĐO #4–#6]. Chấm hiện ngay
đích (vòng nhấn = đang chờ xe), rồi theo số đọc khi tới đích/hết hạn. Đọc to: `Kính lái · 0 % · chạm: mở 50 %` · `Cốp sau · chạm: mở`
[ĐO uiautomator]. Cốp KHÔNG có cảm biến ([ĐO xe 09-25]) ⇒ chấm cốp: đã gửi MỞ = chấm đầy + quầng; còn lại = vòng đứt nét "chưa biết"
(không bao giờ khẳng định "đóng").

## ⚠ An toàn cốp
Chấm cốp chạm vào CHÍNH ô nút `trunk` sẵn có (`performClick`, cùng đường ghi như nhóm Cửa & khoang). Ô nút trên màn **chưa từng có**
cổng xác nhận hay cổng vận tốc — `CtlSafetyPolicy.REQUIRES_STATIONARY` chỉ áp ở giọng nói/phím (`VoiceControlDispatch`, `KeyCtlPlan`);
`CONFIRM_REQUIRED` chỉ chặn broadcast cầu kiểm thử. Không bỏ qua cổng nào, nhưng chấm cốp nay nằm sát bốn chấm kính hay chạm ⇒
**đề nghị owner quyết**: thêm `CtlSafetyPolicy.blockedAtSpeed` cho đường ô nút (chặn MỞ khi > 0 km/h). [CHƯA BIẾT] firmware có tự từ chối
`voiceCtlBackDoor(1)` khi xe chạy không.

## Thiết kế
- `:core` mới `WindowCycle.kt` (`next` · `plan` theo `GroupActionCell.level` · `levelOf`). `GroupActionMatrix`: `placeOf` đọc
  `CarPart.key` (hậu tố icon `-lf…`, `-trunk`); `atCarEnd` (neo ≤ 0,2 / ≥ 0,8); `ActionMatrix.onCar/openAll/closeAll`; `CompactMetrics`
  bỏ `modes/modePx`, thêm `ends`; `zones` thêm DẢI đầu/đuôi xe cả bề ngang (ranh trung điểm, kẹp ≥ đích chạm). Ô quá thấp cho dải
  (vd 6×2) ⇒ cốp xuống chân làm nút icon thứ ba (không mất nút).
- `CapabilityGroups.WINDOWS.writes` + `trunk`; dòng phụ vi/en + zh/th/ms tsv. Hệ quả: hộp chọn phím vô-lăng nay xếp cốp dưới nhóm Kính
  (nhóm ĐẦU khai nó — luật cũ) — `KeyCtlTargetTest` cập nhật kèm trích owner.
- `CarArt.DEFAULT` + neo `TAILGATE` (338,5; 1 288) [ĐO cột giữa ảnh: kính hậu tới y≈1 240, nắp cốp 1 245–1 330]; Seal dùng neo đã đo (760; 277).
- `:app`: `ActionTile.set` + `ControlTileFactory.tileToggle` tách `write(nv)` (chạm ô và đặt tường minh CÙNG một hàm ghi);
  `MatrixKit.set`; `ActionMatrixCompact` viết lại (252 dòng); `CarLevelsLayer` (`ends`, `dotOf`, `lit(CarPart)`, `pulse`).
- Icon: `design/glyph/win_open_all_g.svg` · `win_close_all_g.svg` (khung cửa sổ bên: trụ A vát, góc sau bo, nét 1,8; kính lớp nền 16 %;
  mũi đôi ⇊/⇈) → `gen-icons.py` sinh `ic_win_*_all_g.xml` (họ chrome, tint). `WindowGlyph` Canvas đã GỠ (ZeroBorder INK_VIEWS bỏ mục).
  Nút chân: đĩa tròn mực 8 % + sóng nhấn trong đĩa (mask OVAL), icon `ICON` → nhấn `ACCENT_INK`; không chữ; tên ở `contentDescription`
  (`strings_kachi_winctl301.xml` ×5 thư mục).
- Mảng xanh: vùng chạm ≥ 48 dp KHÔNG còn nền/sóng; phản hồi = chấm nảy một nhịp 120 ms (`1 + 0,3·sin πt`, không lặp) + đổi trạng thái;
  tiếng chạm hệ thống giữ (app không dùng haptic ở đâu).

## Tương phản
[ĐO pixel máy ảo] nét icon vs đĩa nút: Tối **7,25:1** · Sáng **4,13:1**. Test `WindowControlIconContractTest` tính theo bảng màu (card +
tile, nghỉ + nhấn, Tối + Sáng) ≥ 3:1 — xanh.

## Test [ĐO XML, gradle-locked.sh]
- `:core:test` TOÀN BỘ: 5 221 bài, 1 đỏ (`LangCoverageTest` — dòng phụ nhóm Kính đổi theo ý owner) → sửa kỳ vọng, chạy lại 21/0.
- `:app` tập trung (`*ActionMatrix* *IconStyle* *ZeroBorder* *ThemePalette* *CarDot* *Widget* *GroupTile* *SourcePathHygiene* *LauncherI18n*
  *LayeringRules* *Door* *Trunk* *Cycle* *WindowControl* *SpacingScale* *KachiDialogMigration* *ControlTile*`): **252 / 0 đỏ / 1 bỏ qua**
  (IconStyle sinh-lại-so-byte cần python ≥ 3.10; chạy tay `gen-icons.py --check` = 131 tệp khớp, `gen-car.py --check` OK).
- Mới: `WindowCycleTest` (3), `WindowControlIconContractTest` (2), `ActionMatrixWiringContractTest.cham cham di vong…` (không công tắc,
  2 nút chân, `kit.set`, cốp `onCar`, vùng chạm không nền, chấm nảy), `GroupActionMatrixCompactTest` (dải cốp 162–234 ở ô 2×3, 6×2 cốp
  xuống chân). Sửa kỳ vọng có trích owner: `GroupActionMatrixTest`, `GroupTileWiringContractTest` ([6,5]→[6,6]), `KeyCtlTargetTest`.
- Thử ĐỎ: `WindowCycle.next` đổi `level >= HALF -> FULL` thành `level > 0 -> FULL` ⇒ `WindowCycleTest` 1/3 đỏ; khôi phục ⇒ xanh.
- Call site §8: `WindowCycle.next(` 2 · `WindowCycle.plan(` 1 · `kit.set(` 2 · `levels.pulse(` 1 · `mx.onCar`/`openAll`/`closeAll` ≥ 1.

## Máy ảo emulator-5556 [ĐO]
`:app:assembleVehicleTest` (3.1 — CHƯA bump), cài `-r` 2 lần Success. ⚠ `/data` chỉ còn ~280–400 MB (ngưỡng cài ~500 MB): mỗi lần cài
phải TẠM rút mô hình sherpa (bản side-load ngoài + bản trong `files/sherpa`) ra máy chủ rồi trả lại — đã trả, md5 bản trong khớp
nguyên. Bấm giữ (`motionevent DOWN`) trên vùng kính: **0 px đổi** (không mảng); thả: chỉ hộp 18×19 px quanh chấm đổi. Nhấn nút chân: đổi
trong ô 72 px (đĩa tròn). Sáng: đổi tạm `kachi_workspace.xml` sang DAY rồi trả bản sao lưu (cmp = giống hệt).
Ảnh có ảnh BYD: chỉ ở scratchpad `shots/` (`after1-*`, `cyc-strip.png`, `trunk-strip.png`, `press-*`, `light-*`). Ảnh chỉ icon:
`docs/diagnostics/widget-visual-301/33-windows-footer-dark-light-icons.png`.
Màn để lại: HOME, Tiếng Việt, Tối, số giả BẬT. ⚠ Bố cục hiện tại là **Kính lớn · Chuyến đi · Đèn** — đã như vậy TRƯỚC lần cài đầu của
lượt này (16:59; lúc 16:41 còn Kính nhỏ) — không phải do lượt này; bố cục owner `YouTube · Chuyến đi · Kính 2×3` nằm ở
`/data/local/tmp/ws.xml` (`apply.sh`), CHƯA khôi phục để không đè việc của agent khác.

## Còn mở
- [CHƯA ĐO máy ảo] ô Kính 2×3 (chỉ test `:core` 267×312 ⇒ STACK, dải cốp 72 px) và ô 6×2 (cốp xuống chân).
- `windows_all` vẫn tier OVERDRIVE (chưa kiểm trên xe) — ngữ nghĩa nút "mở tất cả" giữ như chân cũ.
- Spec `kachi-301` §Tasks/§Design + backlog chưa ghi R22·C (một chủ spec — orchestrator).
