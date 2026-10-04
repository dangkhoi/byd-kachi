# KEY-SOURCE-SPLIT — số đo đầu tiên trên xe thật (2.87, 04/10)

> **Trạng thái**: Current · **Cập nhật**: 2026-10-04 · **Loại**: Diagnostics · **Owner**: dangkhoi ·
> **Mục đích**: chép lại (bằng chữ, không lưu ảnh) bốn ảnh owner chụp hộp *Học phím mới* trên xe chạy 2.87 (188) — bằng
> chứng tầng 1 (CLAUDE.md §14) cho việc phân biệt **núm âm lượng bệ giữa** với **nút âm lượng vô-lăng**.
> **Spec**: `docs/specs/kachi-287-look-and-keys.html` §4.7 (tầng 1) · `docs/specs/kachi-288-key-source-split.html` (tầng 2) ·
> **Backlog**: `KEY-SOURCE-SPLIT`

Mức bằng chứng theo CLAUDE.md §2: `[ĐO]` = đọc thẳng trên ảnh màn hình xe · `[SUY]` = suy luận · `[CHƯA BIẾT]`.

## 1. Số đo

Xe của owner, Kachi 2.87 (188), giao diện tiếng Anh, *Settings › Steering-wheel keys › Learn a new key…*. Mỗi ảnh là một
lần học, bấm/xoay **một** lần. Dòng chi tiết dưới ô tên, chép nguyên văn:

| # | Nút vật lý (owner bấm) | Dòng chi tiết trên màn |
|---|---|---|
| 1 | núm bệ giữa — xoay giảm | `code 292 · scan 114 · device "simulate-keys"#8 · source: centre-console knob (AUDIO_VOLUME_CTRL_MODE=1) · read 1 ms` |
| 2 | núm bệ giữa — xoay tăng | `code 291 · scan 115 · device "simulate-keys"#8 · source: centre-console knob (AUDIO_VOLUME_CTRL_MODE=1) · read 2 ms` |
| 3 | vô-lăng — giảm | `code 292 · scan 114 · device "simulate-keys"#8 · source: steering wheel (AUDIO_VOLUME_CTRL_MODE=2) · read 1 ms` |
| 4 | vô-lăng — tăng | `code 291 · scan 115 · device "simulate-keys"#8 · source: steering wheel (AUDIO_VOLUME_CTRL_MODE=2) · read 3 ms` |

## 2. Kết luận

- **[ĐO]** `KeyEvent` của hai nút **giống hệt**: cùng mã (291/292), cùng scancode (115/114), cùng thiết bị ảo
  `simulate-keys` (id 8 trong lần khởi động này). Chỉ dựa vào `KeyEvent` thì không tách được — đúng như dự đoán từ
  firmware (`auto.default.so`, spec 2.87 §4.7.1) và đúng lý do Overdrive cũng không tách được.
- **[ĐO]** Kachi **đọc được** feature HAL `AUDIO_VOLUME_CTRL_MODE` (`BYDAutoAudioDevice`) từ uid của app, không bị
  từ chối quyền: 4/4 lần ra đúng nguồn (1 = núm bệ giữa, 2 = vô-lăng), khớp với nút owner vừa bấm.
- **[ĐO]** Một lượt đọc mất **1–3 ms** (trần thiết kế 250 ms, hạn của framework cho `onKeyEvent` 500 ms) ⇒ đọc đồng bộ
  ngay trong luồng phím là khả thi về thời gian.
- **[SUY]** Cửa sổ "đọc nhầm nguồn" chỉ mở khi nút KIA bị bấm trong vài ms giữa lúc HAL ghi phím và lúc Kachi đọc —
  firmware đặt `AUDIO_VOLUME_CTRL_MODE` **trước** khi phát mỗi phím [ĐO tĩnh `setIntValue(F+0x9a8, 1|2)` @0x2d300 /
  @0x2c8d4]. Một người lái không bấm hai nút cách nhau vài ms.

## 3. Chưa biết — phải đo ở bản 2.88 trên xe

So với luật quyết định của spec 2.87 §4.7.4 (*"≥ 2 xe, mỗi nút ≥ 3 lần, cả tăng lẫn giảm, kể cả bấm xen kẽ nhanh"*), số
đo này mới có **1 xe × 1 lần mỗi chiều mỗi nút**. Phần còn thiếu:

- `[CHƯA BIẾT]` Bấm **xen kẽ nhanh** (núm rồi vô-lăng trong vòng 1 giây) — lần bấm đầu sau khi đổi nút có ra đúng nguồn
  không. Trong bốn ảnh, mỗi lần học cách nhau nhiều giây.
- `[CHƯA BIẾT]` Xoay núm **nhanh nhiều nấc** (DOWN cách nhau ~3 ms, fixture `keysrc-0917/usage-292-burst.txt`) — mọi nấc
  đều đọc ra 1?
- `[CHƯA BIẾT]` Giữ nút vô-lăng (mã nhấn-giữ 307/308) — có đi qua cùng feature không.
- `[CHƯA BIẾT]` Đời xe khác (DL5 / Atto 3 / Dolphin…) — feature có trong bảng không, giá trị có cùng nghĩa không.

Thiết kế tầng 2 (`kachi-288-key-source-split.html`) không cược vào bốn điều trên: **đọc hụt hoặc giá trị lạ ⇒ coi như
không biết nguồn ⇒ chỉ dùng dòng gán không phân nguồn (nếu có), không thì để phím đi tiếp (âm lượng chạy như xe gốc)**.
Mục thử trên xe cho anh em nằm ở spec đó, phần Verification.
