# Chuyển bài sau Bung to / Thu về phải bấm ≥ 2 lần — dựng lại, gốc, hướng sửa (2026-10-15)

> Backlog `MEDIA-NEXT-TWO-PRESS`. Bản đo: 3.1.6 (211) `vehicleTest`, máy ảo `kachi_play` (A10, YouTube 21.35.442,
> YT Music 9.35.54). **Không sửa mã** — gốc nằm ở hai luật tự ẩn, một của owner (2.87 R-AH), một của YouTube; đổi
> luật của Kachi là quyết định của owner (§5). Chưa commit, không bump.

Owner 15/10: *"đang mở nhạc, bấm fullscreen, bấm trả lại, bấm chuyển bài … trên xe kiểu gì cũng phải nhấn rất nhiều
lần nó mới chịu chạy, ít nhất là 2 lần"*.

## 1. Bằng chứng xe trước (log 15/10, 3.1.5) [ĐO]

ROM BYD in mỗi DOWN: `InputDispatcher: debug_input pos:[x,y], displayId, Window`. Lần theo đúng cảnh owner tả
(YouTube ở ô 1 = màn ảo 9, khung 590×978 @157):

| Giờ | Chạm (màn 0) | Đi đâu | Kết quả |
|---|---|---|---|
| 07:50:20.415 | (1461,226) giữa ô | Kachi chuyển vào màn ảo 9 (237,137), +15 ms | đầu ô hiện |
| 07:50:21.828 | (1666,125) | nút ⤢ | `bung ô 1 · 14 ms` |
| 07:53:44.383 | (1018,696) giữa ô bung | chuyển (999,607), +5 ms | đầu ô hiện |
| 07:53:45.800 | (1062,117) | nút ⤡ | `thu ô 1 · 47 ms (TAP)` |

- Mỗi lần *Bung to* và *Thu về* trên xe đều tốn **đúng 2 chạm**: một chạm để lộ đầu ô, một chạm vào nút.
- Độ trễ chuyển chạm Kachi → màn ảo trên xe: n = 13, p50 = **15 ms**, chỉ một lần 319 ms. Đường chạm của Kachi
  không chậm.
- Tiêu đề YouTube trong chuyến chỉ đổi đúng lúc **bài hết tự nhiên**. Ví dụ 07:46:46 + 612 s = 07:56:58, và bài
  mới hiện lúc 07:56:59. Các bài sau cũng vậy. Chuyến này không có lần *chuyển bài bằng tay* nào thành công. Owner
  có bấm ⏭ mà trượt hay không thì log không đủ để nói: vị trí nút ⏭ của YouTube ở khung 590×978 là [CHƯA BIẾT].

## 2. Dựng lại trên máy ảo [ĐO]

Chuẩn bị: bố cục THREE, ô 1 là YouTube (màn ảo 733×482), phát danh sách mix `&list=RD…`. Mỗi lượt ghi tiêu đề
`dumpsys media_session` trước và sau. Toạ độ nút đọc từ `dumpsys activity top`: `player_control_next_button`
nằm ở [292,117]–[324,149] trên màn ảo, tương ứng (1476,222) trên màn 0. Có hai đường bấm:

- **K**: `input tap` trên màn 0, đi qua `VdAppHost.onTouchEvent` → daemon (đường thật của người dùng).
- **V**: `input -d <vd> tap`, tiêm thẳng vào màn ảo, bỏ qua Kachi (đối chứng).

| Ca | K | V |
|---|---|---|
| Điều khiển YouTube đang ẩn, chạm 1 lần vào chỗ ⏭ | 0 / 9 chuyển bài | chạm đầu chỉ lộ thanh: cờ nút ⏭ `G` → `V` 3/3 |
| Lộ điều khiển → 1 s → ⏭, bố cục thường | 3 / 3 | 3 / 3 |
| Lộ → ⏭ ngay sau Bung to + Thu về | 5 / 6 | 3 / 6 |
| Phím `KEYCODE_MEDIA_NEXT` (vô-lăng / phiên nhạc), YouTube | 6 / 6 | — |
| Phím `KEYCODE_MEDIA_NEXT`, YT Music | 2 / 2 | — |
| Chạm 4 lần (cách 1,2 s) vào chỗ nút ⤢ khi đầu ô đang ẩn | 0 / 4 lần bung | — |

Đọc bảng:

- **Chạm đầu tiên vào ⏭ của YouTube không bao giờ chuyển bài.** Lúc đang phát, YouTube ẩn thanh điều khiển
  (`player_control_next_button` cờ `G`), nên chạm đầu chỉ làm lộ thanh. Ở đường V, chạm đầu cũng chỉ đổi cờ `G` → `V` ⇒ đây là hành vi của
  YouTube, không phải Kachi.
- Sau Bung/Thu, tỉ lệ *lộ → ⏭* tụt (8/12, so với 6/6 ở bố cục thường). Đường V **cũng tụt** ⇒ không phải đường chạm
  của Kachi. Đây là trạng thái bên trong YouTube sau khi màn ảo đổi cỡ (activity không dựng lại, cùng bản thể
  `3a6c625`). Máy ảo nhiễu (2 GB RAM, Kachi bị giết và dựng lại nhiều lần trong buổi đo, một lần đọc được nguyên dòng `has died: vis TOP`, hộp thoại setupwizard và Settings nhảy lên) ⇒ con số
  này chỉ ở mức "nhiều khả năng", không phải "đã chứng minh".
- Phím nhạc (đường vô-lăng và phiên nhạc) chuyển bài **ngay lần đầu** 8/8. Kachi không nằm trên đường này
  (`grep KEYCODE_MEDIA` trong `app`/`core` = 0).
- **Nút đầu ô đang ẩn: chạm thẳng vào chỗ nút thì nút không bao giờ hiện**, có chạm bao nhiêu lần cũng vậy. Đây là
  điều 4 của `SlotHeadAutoHide`.

## 3. Gốc

| Mức | Khẳng định |
|---|---|
| Đã chứng minh (mã) | `SlotHeadTouch.onUp` (`core/.../launcher/SlotHeadTouch.kt:76-79`): khi đầu ô đang ẩn, cú chạm mà DOWN rơi vào CHỖ một nút đầu ô (`inHead`) ⇒ **không có `Act`**, nên không hiện. Cú chạm ở chỗ khác ⇒ `RevealAfterDoubleTap`. Từ R30, hàng đầu ô dài thêm nút ⤢/⤡ ở bên PHẢI (`SlotHeadActions.of`), nên vùng "chạm không hiện" rộng thêm đúng chỗ nút đó. |
| Đã chứng minh (máy ảo + log xe) | Bung to / Thu về luôn cần **lộ rồi bấm** (≥ 2 chạm). Chạm lặp lại vào chỗ nút đang ẩn thì không bao giờ được, và người dùng cảm nhận là *"nhấn rất nhiều lần"*. |
| Đã chứng minh (máy ảo) | Nút ⏭ của chính YouTube cũng cần **lộ rồi bấm**, ở cả đường K lẫn V. |
| Nhiều khả năng | Ngay sau khi màn ảo đổi cỡ, YouTube nhận cú ⏭ kém hơn (8/12, so với 6/6). Cách chốt: lặp 10 lượt *Bung + Thu → lộ → ⏭* (cả K lẫn V) trên một máy ảo đủ RAM, hoặc đo trên xe theo §6. |

Đã loại, kèm bằng chứng:

- **Chọn nhầm phiên nhạc (`MediaBridge.active`)**: Kachi không nằm trên đường ⏭ của YouTube hay của vô-lăng.
  `MediaBridge` chỉ phục vụ widget `w_media` và giọng nói, và `active` được đọc lại mỗi lượt vẽ widget
  (`WorkspaceView.kt:172/215`).
- **Màn ảo OFF sau khi thu**: ô bung không bị che, nên OQ8 (màn ảo OFF) chỉ áp cho ô bị ẨN. YouTube giữ nguyên bản thể activity qua Bung/Thu và phát liên tục (`state=3`). Trạng thái RESUMED thì chưa đọc riêng.
- **Tiêu điểm lệch màn**: phím nhạc chuyển bài được ở mọi trạng thái tiêu điểm (8/8).
- **Đường chạm Kachi chậm hoặc nuốt chạm**: trên xe p50 15 ms, và mọi chạm vào ô YouTube đều được chuyển. Máy ảo
  lúc tải nặng 9–343 ms.
- **Cuộc đua micro với giọng nói, chống dội phím**: không có trên đường này.

## 4. Vì sao không sửa trong lượt này

Điều 4 là quyết định có chủ ý (2.87 R-AH): ô tìm kiếm của Google Maps nằm giữa-trên, đúng chỗ ⇄. Nếu chạm vào chỗ
nút cũng làm đầu ô hiện, thì cú chạm thứ hai rơi vào nút của Kachi thay vì app. Gỡ luật đó là đảo một quyết định
của owner, có cái giá đã biết, nên cần owner chọn. Phần của YouTube (thanh điều khiển tự ẩn) thì Kachi không đổi
được.

## 5. Hướng sửa cho owner chọn (đều generic, không hỏi tên gói)

1. **A — luật theo từng nút**: chỉ ⇄ (giữa-trên, chỗ ô tìm kiếm Maps) giữ điều 4. Chạm vào chỗ ⤢/⤡ (mép phải)
   khi đang ẩn thì vẫn hẹn hiện. Cách làm: `SlotHeadTouch` nhận thêm vùng "giữ im" thay cho cờ `inHead` chung, và
   thêm một bài trong `SlotHeadTouchTest`. Vẫn cần 2 chạm, nhưng không còn chạm hoài vô hiệu.
2. **B — đầu ô luôn hiện khi đang bung**: ô bung chiếm trọn launcher và chỉ có một lối thu, nên ⤡ một chạm. Cái
   giá: hàng nút đè lên mép trên của app suốt lúc bung.
3. **C — chuyển bài một chạm không phụ thuộc YouTube**: phím vô-lăng ⏭ (đã chạy 8/8) hoặc giọng nói *"bài tiếp"*
   (`VoiceTargetDispatch` → `MediaBridge.next`). Nếu owner muốn một nút trên màn, đó là tính năng mới (⏭ ở đầu ô cho
   ô có phiên nhạc), cần spec riêng.

## 6. Kiểm trên xe (khi có buổi xe, ~5 phút, chỉ đọc log)

Lọc log: `logcat -s InputDispatcher:D KachiWorkspace:I MediaController:D`. Mỗi bước đếm số `debug_input` trước khi
`thu ô`/`bung ô`, hoặc trước khi tiêu đề `onMetadataChanged` đổi.

1. YouTube ở ô, đang phát, đầu ô ẩn. Chạm thẳng vào chỗ ⤢ 3 lần ⇒ kỳ vọng 0 lần bung (xác nhận §2 trên xe).
2. Chạm giữa ô rồi bấm ⤢ ⇒ bung. Đợi 5 s. Chạm thẳng ⏭ một lần ⇒ kỳ vọng không đổi bài (thanh YouTube ẩn).
3. Lộ thanh → ⏭ (trong vòng 2 s), 5 lượt sau khi Bung + Thu ⇒ đếm số lượt đổi bài lần đầu (chốt §3 "nhiều khả năng").
4. Phím ⏭ vô-lăng sau Bung + Thu ⇒ kỳ vọng đổi bài lần đầu.
