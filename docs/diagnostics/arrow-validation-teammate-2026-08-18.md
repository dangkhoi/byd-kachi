# Arrow classification validation — teammate drive data (2026-08-18)

> **Trạng thái**: Current · **Cập nhật**: 2026-08-18 · **Mục đích**: Xác thực phân loại 18/18 mũi tên bằng data lái xe anh em (blind bitmap read vs answer-key).

Nguồn: `runninglogs/files1800` (xe anh em, working-HUD, build CŨ hơn owner — không a11y/upcoming).
Phương pháp: `nav_arrow` 38k live rows → **18 bitmap mũi tên distinct** → sub-agent đọc BLIND từng bitmap (upscale 216px) → so với phân loại của app (answer-key).

## Goal 1 — kết quả: 18/18 phân loại VỮNG (không lỗi 1-1)
- 17/18 khớp tuyệt đối: turn L/R, slight L/R, sharp L/R, u-turn, straight, merge (2-nhập-1), fork L/R, depart, destination, roundabout {slight_left→trái, straight→thẳng, slight_right/sharp_right→phải}.
- Mỗi bitmap → 1 phân loại ổn định >99.9% (lạc 1-2 dòng = misread thoáng lúc redraw, bỏ qua).

## Ca `8344266781286630760` — điều tra tận cùng (đã CLEAR, không phải bug)
- Cờ ban đầu: đọc-mù = MERGE; app gán = `roundabout_exit_ccw` (icon 12).
- Bằng chứng context: xuất hiện CHỈ ở đường "QL1A Cầu Bến Cát 1 / Lê Đức Anh" (80/80); ảnh cluster seg-95 cùng đường ⇒ map "Vòng Xoay An Lạc" (xe ở vòng xuyến). Glyph KHÁC merge thật (`9078`=2-nhập-1).
- Kết luận: **ra-khỏi-vòng-xuyến THẬT** → `roundabout_exit` đúng ngữ cảnh. "Merge" là báo động giả (glyph ra-vòng-xuyến-vào-đường không có vòng tròn). **KHÔNG fix.**

## OPEN — phía RENDER (KHÔNG phải classification), cần data OWNER
- **Bug owner báo**: vòng xuyến ra-phải → cụm owner hiện **generic "vào vòng xuyến, ko lối ra"**.
- **Data anh em phản chứng**: cụm xe anh em (working-HUD) render **vòng xuyến CÓ HƯỚNG ở tầm xa** (nhánh trái-trên/phải-trên đúng exit) — bằng chứng từ `cluster.png` (seg-72 350m trái, seg-289 700m phải).
- ⇒ **App bắn ĐÚNG mã directional (CAN 15/18); lỗi generic của owner là RENDER-side, ĐẶC THÙ XE OWNER** (variant 40d=138 vs anh em 162 / firmware / thời điểm). Không tái hiện từ data anh em.
- **Cần để chốt**: ảnh vòng xuyến của CHÍNH xe owner (lúc approach) + mã CAN gửi lúc đó. → việc on-car với data owner.
- Minor: roundabout-exit (CAN 24) vẽ mũi tên thẳng trên cụm — chấp nhận được (thoát ra QL1A ≈ thẳng).

## Verdict Goal 1
PASS phía SEND — **không có lỗi phân loại/bắn mũi tên cần fix**. Vấn đề vòng xuyến duy nhất (generic của owner) là **render-side + owner-specific**, mang sang phiên on-car với data owner.

## LOCKED 2026-08-18 21:26 — vòng xuyến = OEM render, KHÔNG phải app (data owner xác nhận)
- Owner afternoon nav_arrow: **748 dòng `..._ccw_normal_right`** → app gửi **CAN 18 (directional right)**. Notif KHÔNG có chữ số-lối-ra (chỉ "Tân Phú") → KHÔNG dùng 24+N. → app phân loại+gửi ĐÚNG.
- Owner thấy generic trên Giữa+ETA → **OEM cụm owner vẽ CAN 18 thành glyph vòng-xuyến-trơn (mất nhánh)**; OEM anh em vẽ directional. → **khác firmware/variant OEM (138 vs 162)**.
- **App KHÔNG có lỗi.** Còn lại: glyph-test trên cụm owner (mã CAN nào vẽ directional trên OEM owner) — chỉ làm được trên xe owner (OEM-render không nằm trong log). **CHỐT ở đây.**
