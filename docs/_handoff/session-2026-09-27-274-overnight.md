# Handoff — đêm 26→27/09/2026: 2.74 (175) làm qua đêm, sáng 27/09 lên xe chỉ test

> **Trạng thái**: Superseded bởi `session-2026-09-27-275-after-car.md` · **Cập nhật**: 2026-09-27 (rạng sáng) · **Mục đích**: người mở phiên sáng (hoặc chính owner) biết ngay bản nào ở đâu, đã chứng minh gì off-car, còn gì phải đo trên xe, câu nào chờ owner. Spec: `docs/specs/kachi-274-ux-voice-camera.html` (R1–R10, §9 nhật ký, §10 review). Runbook xe: `docs/diagnostics/oncar-runbook-2.74.md`.

## 1. Bản

| Ở đâu | Bản | Ghi chú |
|---|---|---|
| Xe owner (Seal) | **2.70 (171)** cài tay 26/09 | chưa nhận 2.73/2.74 |
| Kênh OTA `main` | **2.74 (175)** `apk/Kachi-2.74-release.apk` (43 768 607 B, sha256 `67105b12…8d04`) | = 2.73 + toàn bộ mục §2 |
| Máy ảo `clusternav10` (cửa sổ đang mở) | bản gộp 2.74 vehicleTest | owner đã xem UX1–UX7 tối 26/09 |

## 2. Đã làm đêm nay (mỗi mục có doc + test + reviewer đối kháng)

| # | Việc | Bằng chứng off-car | Doc |
|---|---|---|---|
| UX1 | Avatar hồ sơ: đĩa lệch 4dp trong pill (lề chữ tên đã GONE) ⇒ chip tự đo; a11y nút | QA đo: lệch **0,0 px**, khe nút 12/12/12 | `offcar-2026-09-26/ux-ux1-avatar.md` |
| UX2 | Widget lốp: 3 lỗi độc lập (baseline hằng, số lệch trục, thẻ đè xe) + feather theo mực mép, decodePlan fit | QA: thẻ không đè ảnh, gương còn, số lệch ≤ 1,5 px | `ux-ux2-tyre.md` |
| UX3 | Danh sách lệnh nói sinh từ ngữ pháp, gập/mở cuối *Giọng nói*; mọi câu parse đúng (test) | QA: 12 nhóm, 156 câu, không cắt | `ux-ux3-voice-list.md` |
| UX4 | Gió: − ở mức 1 ⇒ AUTO (không ghi mức 0, xe bỏ qua); chip "AUTO n"; giọng nói cùng luật; nhịp 400 ms rời luồng chính | QA lượt 4: AUTO **1 dòng** 39×11 px, số lớn lại 15 px, ô 47×31, nút Nhiệt 0 px; **chữ AUTO trên chip cần xe** (chỉ báo chưa đo) | `ux-ux4-ux5-climate-seat.md` |
| UX5/5b | Mức số + glyph ghế ghép; **Ghế lái · Ghế phụ** hai chip mặc định (5 chip), datum ghế phụ seatID 2, di trú danh sách cũ | QA: 5 chip mặc định, hai hình khác | cùng doc §8 |
| UX6 | Khe icon→nhãn 0dp (lỗi thứ tự) ⇒ 8dp; khe chip 16 → 12dp một hằng | QA: 12 px / 18 px | `ux-ux6-header-chip-spacing.md` |
| UX7 | 14 bề mặt vẽ chữ: vòng Năng lượng/Không khí (+14,6 %·d), Tốc độ, thẻ nhóm, ô đọc, Cửa 0,86 → 0,98 | QA lượt 2/3: tâm vòng +0,5 px, Tốc độ/thẻ/ô đọc +0,5 px, Cửa xe ×1,15 | `ux-ux7-composite-widgets.md` |
| OPEN-TURN | Câu dở ⇒ giữ mic ≤ 1,2 s, ghép vế; câu đủ 0 ms thêm | 30 WAV xe 0/30 đổi; E2E 105/106 | `voice-open-turn.md` |
| CAM-FRAME | `camera_frame` PNG khung thô (AOSP `getBitmap` không áp transform) | test; **PNG thật cần xe** | `camera-frame-capture.md` |
| CAM-A | Crop dải/bề rộng/hình TRÒN/kênh HAL sau chip; `rmPreviewSurface`; mặc định = 2.73 | test literal | `camera-view-electro-a.md` |
| CAM-B | Nắn fisheye GL (theo RE Electro): toán :core + đường vẽ EGL + 6 núm | **máy ảo: lưới 48,6 → 3,3 px** | `camera-dewarp-math.md`, `camera-dewarp-gl.md` |
| RE | Electro: 159 khẳng định có nhãn; 2 luồng HAL từ 4 camera; 4 dải 25 %; toán nắn | — | `electro-camera-RE-2026-09-26.md` |

## 3. Sáng 27/09 trên xe — làm theo `oncar-runbook-2.74.md` §2 (thứ tự theo giá trị/phút)
F1 khung PNG thô (ưu tiên 1) → CAM-B1 hai số GPU → E2 "vào ô số 2" → F2/F2b OPEN-TURN → E1 taskbar → E4 hồ sơ → E7/E7b khung camera → CAM-A1..A4 → CAM-B2..B4 → G1–G5 UX → E5/E6/E8/E9/E11/E12. Không adb ⇒ §4 chỉ chụp màn.

## 4. Câu chờ owner (trả lời sau khi nhìn xe/máy ảo)
1. **VOICE-OPEN-TURN-2** — ghép khi vế trước đã đủ nghĩa ("mở vietmap" ⟨ngừng⟩ "vào ô số hai")? (chờ = cộng trễ mọi lệnh mở app)
2. **CAM-ROT-3** — khung gương ngang 360×192 giữ, hay 4:3 kiểu kinex (đổi crop)?
3. **OQ7** — khối 4 thẻ lốp cao hơn tâm xe ~16 px: giữ hay dịch khung ảnh?
4. **ac_mode_auto vs ac_wind_auto** — giữ chip nào (dedupe)?
5. Hai hình ghế lái/phụ chỉ khác bằng lật gương — cần dấu thứ hai không?
6. Chip hồ sơ 34dp tròn vs 3 pill 40dp — có cần cùng bề ngang?
7. Bảng 15 cặp "nên gộp gì nữa" (doc UX4/5 §6) — 4 cặp NÊN GỘP chưa làm.

## 5. Nợ kỹ thuật ghi backlog (không chặn ship)
**VOICE-WRITE-LANE [P2, chờ owner chấp nhận]**: câu ghép ("tăng gió rồi tắt điều hoà") — lệnh mức gió vế trước có thể bị rớt mà Kachi đọc ✓ (không mất an toàn). Ngoài ra: DEBT-500 (3 tệp voice đúng 500, `KachiTestBridge` 497, `GroupTileViews` 500) · VOICE-WRITE-LANE (câu ghép vs vế bất đồng bộ) · 3 bản `SystemProperties` reflection · ảnh xe người dùng còn giải mã cover · `setPreviewSize` (K5) chưa làm · `LangCoverageTest` 501 dòng.
