# FIX286 R-OQ8 — ⇄ ô trống trên đĩa kính (phương án B): bằng chứng máy ảo

> **Trạng thái**: Current · **Cập nhật**: 2026-10-03 · **Loại**: Diagnostics · **Owner**: dangkhoi ·
> **Mục đích**: số đo máy ảo cho quyết định OQ8 = phương án B (chốt 2026-10-03 — phiên điều phối, owner có thể đổi): nút ⇄ của
> ô trống luôn nằm trên một đĩa kính 36 dp, lòng ô vẫn trong suốt — tương phản trên ảnh sọc/hoa văn/nhiều chi tiết + trắng/xám/đen.
> **Spec**: `docs/specs/kachi-286-field-fixes.html` §3.6 R-OQ8 · §9 luồng chốt 03/10 · **Backlog**: `FIX286-EMPTY-SLOT`
> · Bằng chứng phương án A (trước lượt này): `docs/diagnostics/fix286-empty-slot-emulator-2026-10-03/`

Mức bằng chứng theo CLAUDE.md §2: `[ĐO]` = output thật của lần chạy này · `[SUY]` = đọc nguồn, chưa chạy · `[CHƯA BIẾT]`.

Máy ảo `emulator-5554` (Android 10, API 29, 1920×1080, 240 dpi ⇒ đĩa ⌀ 54 px, icon 30 px), bản **vehicleTest dựng từ cây làm việc
03/10** (bốn luồng FIX286 + lượt này, chưa commit, versionName vẫn 2.85 — không bump). Hồ sơ thử `Test` (bố cục THREE, cả 3 ô trống).
Prefs sao lưu bằng `tar` (adb root) TRƯỚC, trả bằng `am force-stop` → `tar xf` đè tại chỗ → `chown 10163:10163` → `chmod 660` →
`restorecon` → `am force-stop` (không `rm -rf`), HOME một lần, rồi cài lại `apk/Kachi-2.85-release.apk` (sha `c3a7b12f…` khớp
`base.apk`); 9/9 tệp prefs trùng sha ngay sau khi trả. Ảnh nền thử là ảnh **tổng hợp**, không dữ liệu thật.

## 1. Tương phản icon ⇄ trên đĩa (đo trên ảnh chụp THẬT)

**Cách đo** (`measureB2.py`): nền của icon nay là chính đĩa ⇒ đo thẳng trên ảnh chụp bố cục THREE, không cần ảnh tham chiếu như
phương án A. Lấy mọi điểm ảnh của đĩa trong vành 16 ≤ r ≤ 24 px quanh tâm nút, bỏ hộp icon 30×30 (nét ⇄ có thể chạm góc hộp); mép
khử răng cưa của đĩa ở r ≈ 27. Mực = `MUT` của chủ đề (`#9daabe` tối · `#4c5869` sáng) — [ĐO] mọi ca có điểm ảnh icon trùng đúng mã
`MUT` (lệch kênh 0) ⇒ icon vẽ đúng màu, không bị tô lại. Mỗi ô bảng là giá trị THẤP NHẤT trong ba nút (ô 1, 2 hàng trên — nằm
trong dải che đỉnh màn; ô 3 ngoài dải). Đủ 114 ca (2 chủ đề × 19 cấu hình × 3 nút) ở `matrix-B.jsonl`.

| Ảnh | Làm tối | Tối: min / μ / % điểm ≥ 4.5 | Sáng: min / μ / % điểm ≥ 4.5 | Kết |
|---|---|---|---|---|
| không ảnh | — | 6.20 / 6.89 / 100 | 6.81 / 7.01 / 100 | ✅ |
| trắng | 0 % | **4.86** / 5.38 / 100 | 6.86 / 6.99 / 100 | ✅ |
| trắng | 45 % | 5.09 / 5.64 / 100 | 6.06 / 6.19 / 100 | ✅ |
| trắng | 90 % | 6.21 / 6.80 / 100 | 5.27 / 5.42 / 100 | ✅ |
| xám | 0 % | 5.33 / 5.88 / 100 | 5.90 / 6.01 / 100 | ✅ |
| xám | 45 % | 5.89 / 6.45 / 100 | 5.52 / 5.64 / 100 | ✅ |
| xám | 90 % | 6.46 / 7.03 / 100 | 5.27 / 5.38 / 100 | ✅ |
| đen | 0 · 45 · 90 % | 6.76 / 7.28 / 100 | 5.07 / 5.22 / 100 | ✅ |
| nhiều chi tiết (khối 40 px + sọc 2 px) | 0 % | 5.20 / 5.66 / 100 | 5.72 / 5.80 / 100 | ✅ |
| nhiều chi tiết | 45 % (mặc định) | 5.89 / 6.39 / 100 | 5.37 / 5.49 / 100 | ✅ |
| nhiều chi tiết | 90 % | 6.62 / 7.12 / 100 | 5.17 / 5.28 / 100 | ✅ |
| sọc dọc đen/trắng 3 px | 0 % | 5.48 / 6.03 / 100 | 5.79 / 5.92 / 100 | ✅ |
| sọc dọc đen/trắng 3 px | 45 % | 6.05 / 6.60 / 100 | 5.42 / 5.54 / 100 | ✅ |
| sọc dọc đen/trắng 3 px | 90 % | 6.62 / 7.17 / 100 | 5.07 / 5.18 / 100 | ✅ |
| hoa văn (bàn cờ 10 px sáu màu + chéo trắng 2 px) | 0 % | 5.34 / 5.88 / 100 | 6.00 / 6.14 / 100 | ✅ |
| hoa văn | 45 % | 5.82 / 6.35 / 100 | 5.58 / 5.71 / 100 | ✅ |
| hoa văn | 90 % | 6.46 / 6.98 / 100 | 5.17 / 5.32 / 100 | ✅ |

- [ĐO] **114/114 ca: mọi điểm ảnh đĩa đo được ≥ 4.86:1** (100 % điểm ≥ 4.5:1, sàn WCAG 1.4.11 cho icon là 3:1). Ca tệ nhất:
  chủ đề tối × ảnh trắng không làm tối (đĩa sáng lên nhất vì ảnh mờ dưới nó trắng tinh).
- [ĐO] Hai ca phương án A **không đạt** ở lượt trước nay đạt: sáng × nhiều chi tiết 45 % ô 1 (A: μ 3.36, 32 % điểm ≥ 3:1) ⇒ B min
  5.46; tối × nhiều chi tiết 0 % ô 2 (A: μ 2.60) ⇒ B min 5.76.
- [SUY] Vì sao đạt trên MỌI ảnh mà không phải đo theo ảnh: đĩa đi qua đúng `KachiGlass.apply(…, NEUTRAL)` của thẻ — ảnh MỜ (chi tiết
  nhỏ đã bị làm nhoè) + lớp che chọn theo độ chói đo được (`GlassVeil.alphaFor`) + bề mặt 80 %; hợp đồng `MUT ≥ 4.5:1` trên mọi độ
  chói ảnh đã có bài canh (`ColorChoiceContractTest.lop che kinh du…`). Mô hình thuần của chính đĩa (`SwapDiscModel`, bài
  `ThemePaletteContractTest.o trong nhan ra duoc…`): tệ nhất 4.72 (tối) · 5.07 (sáng) — khớp chiều với số đo (4.86 · 5.07).
- Ảnh cho owner: `oq8-B-toi.png` (tối: không ảnh · trắng 0 % · chi tiết 0 % · sọc 0 % · hoa văn 45 %), `oq8-B-sang.png` (sáng:
  không ảnh · đen 0 % · chi tiết 45 % · sọc 0 % · hoa văn 0 %) — mỗi cột hai ô cắt quanh ⇄ ô 1 (trên, trong dải che) và ô 3 (dưới),
  phóng 3×. So với A: `../fix286-empty-slot-emulator-2026-10-03/b1-…png`, `b3-…png`.

## 2. Các điểm khác

| Điểm | Kết quả |
|---|---|
| Lòng ô vẫn trong suốt (ES1) | [ĐO] `pixcheck2.py` (cặp điểm tránh đĩa): ảnh đồng màu + không ảnh, 2 chủ đề ⇒ lệch kênh trong/ngoài mép ô ≤ 1 ở mọi ca. Ảnh sọc/hoa văn/chi tiết: cặp điểm rơi vào hai ô hoa văn khác nhau nên lệch là của ẢNH, không phải của khung — không dùng làm số đo. |
| Làm mới khi ảnh đổi TRONG tiến trình (không dựng lại màn) | [ĐO] trình chiếu 2 ảnh (trắng/đen) chu kỳ 15 s, chủ đề tối, làm tối 0 %: 8 ảnh chụp cách ~6 s ⇒ đĩa ô 3 đổi theo ảnh `L 0.0113 (đen) → 0.0329 (trắng) → 0.0113`, tương phản 6.76 → 4.86 → 6.76 — khớp từng số với ma trận ⇒ lượt `KachiGlass.refresh` có sẵn dựng lại đĩa (tag kính nằm trên đĩa, không trên khung ô). |
| Chạm | [ĐO] chạm lòng ô trống (1531,750) ⇒ không mở gì (0 chữ của bảng chọn); chạm đĩa (1531,538) ⇒ bảng chọn "Đặt widget hoặc mở app vào ô này". |
| Điểm thả | [ĐO] kéo ô 1 (widget `w_board`) thả vào lòng ô 3 trống ⇒ `['', '', 'widget:w_board']`; kéo tiếp thả ĐÚNG lên đĩa của ô 2 ⇒ `['', 'widget:w_board', '']` — đĩa (View không bắt kéo) không chặn đích thả. ⚠ Lần thử đầu kéo từ ô App (Cài đặt) KHÔNG đi: kênh shell đang lên nên ô App nhúng cửa sổ Cài đặt thật, cú nhấn giữ rơi vào app khách chứ không vào khung ô — khác điều kiện với lượt R-ES (kênh tắt, ô App là thẻ tĩnh), không phải lỗi của B. |
| Mô tả trợ năng (ES4) | [ĐO] uiautomator: `Chọn ứng dụng cho ô 1 [552,100][624,160]` · `… ô 2 [1495,100][1567,160]` · `… ô 3 [1495,508][1567,568]` — khung chạm 48×40 dp không đổi. |
| Nhật ký (ES6) | [ĐO] `usage`/logcat: `[slot-empty] 3 ô trống (ô 1,2,3) trong suốt · ⇄ trên đĩa kính · chủ đề=tối` — dòng nói đúng phương án đang chạy. |
| Lệnh đổi trạng thái đã chạy (CLAUDE.md §4) | Chỉ `am force-stop/start` + `am broadcast -p com.byd.launcher` trên ĐÚNG gói Kachi, display 0, không chạm stack/app khác; hoàn tác = trả prefs + cài lại 2.85. |
| Trả máy ảo | [ĐO] prefs 9/9 trùng sha ngay sau khi trả; sau khi cài lại 2.85 và tiến trình chạy lại, 2 khoá đổi do CHÍNH 2.85 ghi lúc chạy (`a11y_proc_start_elapsed` · mốc `kachi_shell_approval`) — không phải dư lượng của lượt thử. Ảnh nền + bộ đệm `.kachi-art` trả lại; adbd không root; kênh shell `tcpip 5555` + `reverse` lên lại. |

## 3. Khôi phục phương án A (nếu owner đổi quyết định)

`phuong-an-A.patch` — đưa cây làm việc từ B về A (trước lượt này): khôi phục `SwapTint` (`:core`) + `SwapTintTest` + `SwapTintBinding` +
`WallArt.luminanceRange` + móc `KachiGlass.refresh` + tag `kachi_swap_tint` + `WallView.BAND_FRACTION` `internal`, gỡ `SwapDiscModel`
/ `Sp.SWAP_DISC`, trả các bài canh về chân A. `git apply --check` xanh trên cây làm việc 03/10. Không chạm R-F2 / R-OQ1.

## 4. Chưa biết / cần xe

- [CHƯA BIẾT] ⇄ + đĩa trên **ảnh thật** của owner, ngày và đêm — OC-ES (ảnh chụp màn). Ý kiến thẩm mỹ của owner về đĩa (B đi ngược
  ba lần owner chọn "không nền" 12/09 · 14/09 · 25/09) — owner đổi được, bản khôi phục A ở §3.
- [CHƯA BIẾT] DL5 (API 31) có bật kính thật hay không — nếu có, `WallView` tự làm mờ; đĩa vẫn qua `KachiGlass` như mọi thẻ.
