# Tin nhắn cho anh em SL6 — thử đường gửi tên đường lên HUD (Kachi 3.1.12)

> **Trạng thái**: Bản nháp tin nhắn · **Cập nhật**: 2026-10-17 · **Chủ**: dangkhoi · **Backlog**: `HUD-ROADNAME-ALT-PATH`
> **Kỹ thuật**: `docs/diagnostics/hud-clusterdebug-path-2026-10-16.md` §9 · Bản cần cài: **Kachi 3.1.12 (217)** (bản thử, chưa OTA)

---

Chào anh em SL6,

HUD kính lái của SL6 đã hiện **mũi tên + khoảng cách** của Kachi, nhưng chưa bao giờ hiện **tên đường**. Bản 3.1.12 thêm cách gửi
tên đường khác để anh em thử giúp. Mũi tên và khoảng cách vẫn chạy như cũ ở mọi cách.

**Cách đổi**

1. Kiểm bản: *Cài đặt › Giới thiệu* phải ghi **3.1.12**.
2. Vào *Cài đặt › Dẫn đường + HUD*, kéo xuống hàng **"Đường gửi tên đường lên HUD"**. Có 4 nút:
   - **A**: cách cũ (mặc định).
   - **A48**: cách cũ, nhưng tên dài bị cắt còn 48 ký tự.
   - **B**: gửi qua ClusterDebug của xe (cách DiAuto đi).
   - **C**: gửi cả A và B.
3. Nếu B và C bị mờ thì dòng ngay dưới sẽ ghi lý do (ví dụ "xe không có ClusterDebug"). Chụp màn đó gửi về là đủ, không cần thử tiếp.

**Cách thử** (xe đỗ, hoặc người ngồi ghế phụ thao tác, không thao tác khi đang lái)

1. Chọn **B**. Mở Google Maps hoặc VietMap, dẫn tới một chỗ bất kỳ có rẽ, đi qua ít nhất 2 con đường khác tên.
2. Nhìn HUD: tên đường có hiện không? Có đúng dấu tiếng Việt không? Mũi tên + khoảng cách còn không?
3. Nếu B không hiện tên, thử thêm **C**, rồi **A48**, mỗi cách đi qua 2 con đường.
4. Xong thì chọn lại **A** (hoặc giữ cách nào hiện được tên).

**Gửi về cho mình**

- Ảnh chụp hàng "Đường gửi tên đường lên HUD" (có dòng "Gửi lúc … bằng … · … ký tự · …") sau mỗi lần thử.
- Ảnh/clip HUD cho từng cách: **A / A48 / B / C → hiện tên: có/không, đúng dấu: có/không, mũi tên còn: có/không**.
- Có gì lạ (HUD nháy, mất mũi tên, đồng hồ báo lỗi) thì chọn lại **A** ngay và nhắn mình.

Cảm ơn anh em!
