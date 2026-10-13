# kachi-301 — stage 1, agent E (R5/T6) — XONG (chỉ đo, 0 dòng code)

Chi tiết + bảng: `docs/diagnostics/emu-overscan-neg-2026-10-13.md` · dump/ảnh: `docs/diagnostics/emu-overscan-neg-2026-10-13/`.

1. **R5 trên máy ảo A10 = ĐỎ.** [ĐO] `wm overscan 0,-40|-120,0,0 -d 1` KHÔNG đổi `mFrame`/`parent`/insets/pixel của cửa sổ activity thường (Settings, Đồng hồ, Google Maps) — cả task toàn màn lẫn freeform.
2. [ĐO] Chỉ cửa sổ KHÔNG có `FLAG_LAYOUT_IN_SCREEN` (Chrome FRE, kiểu dialog) bị kéo lên (`parent=[0,-T]`). Mọi activity không-floating đã đo đều có `LAYOUT_IN_SCREEN|LAYOUT_INSET_DECOR` ⇒ miễn nhiễm.
3. [ĐO] Overscan DƯƠNG chạy qua inset (`Cur insets: overscan=[0,40]`, pixel dịch xuống 40) — ở toàn màn; freeform thì dương cũng vô hiệu.
4. [ĐO] `am task resize` top/left âm bị từ chối ("bad top arg"); tràn phải/dưới (`0 0 1920 800`, `0 0 2000 760`) được nhận ⇒ R7 khả thi trên A10.
5. [ĐO] overscan âm ghi bền `display_settings.xml` (`overscanTop="-120"`), `reset` gỡ sạch.
6. **Cảnh báo:** `DisplayFrames` (`mUnrestricted/mContent`) LUÔN đổi theo overscan âm ⇒ KHÔNG được dùng làm bằng chứng "ăn" (dương tính giả).
7. [CHƯA BIẾT] ROM BYD có vá DisplayPolicy không; cờ cửa sổ thật của VietMap/AA/CP trên cụm; chế độ VD AutoContainer.

**Đề xuất tự-đo lúc chạy (nếu vẫn làm R6, mặc định ẩn hàng):** áp `wm overscan 0,-T,0,0 -d <vd>` (T thử = 40), chờ ~1 s, đọc khối `dumpsys window windows` của cửa sổ chính app (lọc `mDisplayId=<vd>` + tên gói), so với trước khi áp: hiện hàng "Dịch nội dung" CHỈ KHI `mFrame.top` giảm ≈T (hoặc `Frames: parent=` top = −T). Không đạt ⇒ `wm overscan reset -d <vd>` ngay + ẩn hàng + nhớ kết quả theo (gói, hồ sơ). Không dùng `DisplayFrames`/`Cur insets` (insets kẹp ≥ 0). Parser thuần + fixture từ các `.txt` trong thư mục trên.
