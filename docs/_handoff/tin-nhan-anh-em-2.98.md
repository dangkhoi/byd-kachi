# Tin nhắn gửi anh em test Kachi 2.98

> **Trạng thái**: Session · **Cập nhật**: 2026-10-10 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.98 (201) lên kênh cập nhật tự động — kiêm nhật ký thay đổi. Nguồn: spec `docs/specs/kachi-298-plan.html` (R1–R6, R9, R11–R15, R18, R-OTA). Owner 10/10: OTA với công tắc R18 TẮT mặc định. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.98 đã lên `main`.

---

Chào anh em, Kachi **2.98** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.98 (mã 201)**.

**Giọng nói**
- Nói **"hả …"** (hỏi lại) không còn bị hiểu thành **"hạ …"** — vd "hả cốp" không mở cốp, "hả kính lái" không hạ kính. Chỉ "hạ" mới là lệnh.
- Nói hai việc trong một câu **không cần chữ "và"**: "tắt sưởi ghế phụ tắt gió tự động" ⇒ làm cả hai. Câu có chữ **"xong"** ("đóng kính lái xong bật đèn đọc") cũng ra hai lệnh, không mất vế sau.

**Ô màn hình chính**
- App bị sập ngay lúc vào ô không còn để lại ô trắng: ô tự trả về như trước (trong suốt hoặc widget đã lưu).
- App ra khỏi ô thì câu báo "đã rời ô" hiện nhanh hơn (≤ ~10 giây sau khi về màn chính), và không còn báo nhầm khi có hai màn chính cùng mở.
- App đang đỗ ở ô ẩn bị dừng rồi đặt lại vào ô ⇒ app mở lại, ô không bị xoá.
- Thanh lối tắt nhớ đúng chỗ đã cuộn.

**Mới — thử nghiệm, mặc định TẮT**: *Cài đặt › Màn hình chính › **Kéo app thoát ô về lại ô (thử nghiệm)***. Một số app (hay gặp nhất là **Waze**) thỉnh thoảng tự bật ra khỏi ô thành toàn màn hình. Bật công tắc này thì Kachi đưa app đó về lại đúng ô, app vẫn chạy tiếp (không mở lại). Đang thử nên để tắt sẵn — tắt đi là về y như 2.97. Nếu nó gặp lỗi, Kachi tự tắt và ghi lý do ngay dưới công tắc. **Chỉ bật khi xe đang đỗ** để thử lần đầu.

**HUD / cụm (SL6)**
- Kachi gửi **tên đường** lên HUD kính lái và cụm theo đúng nhịp của bản đồ gốc BYD — trước chỉ có mũi tên + cự ly.

**Cập nhật**
- Có bản mới thì hiện **chấm đỏ nhỏ** trên nút ⚙ và ở hàng *Kiểm tra cập nhật* (khi bật *Tự động cập nhật*, hoặc sau khi bấm kiểm tra tay mà chọn "Để sau").

**Nhẹ hơn**
- Màn tắt thì màn chính của Kachi thôi đọc dữ liệu xe; bớt ghi nhật ký thừa; tự xoá tệp cập nhật ~45 MB đã cài xong và dọn dữ liệu khung của app đã gỡ (sau 30 ngày) ⇒ đầu xe đỡ đầy bộ nhớ khi dùng lâu.

**Nhờ anh em thử và gửi về** 🚗
1. **Chủ SL6**: bật *Dẫn đường lên cụm đồng hồ*, chạy dẫn đường Google Maps (HUD hiện chỉ nhận Google Maps), nhìn **HUD kính lái** ⇒ có thấy **tên đường** không? Chụp HUD gửi về giúp (có hay không đều quý).
2. **Giọng nói**: thử "tắt sưởi ghế phụ tắt gió tự động", "đóng kính lái xong bật đèn đọc", "hả cốp" ⇒ Kachi làm đúng mấy việc? Có câu nào mất vế không?
3. **Ô**: mở Waze (hoặc app hay bị bật ra) trong ô, dùng một lúc (tìm đường, bấm vào địa điểm) ⇒ app có bật ra khỏi ô không, câu báo "đã rời ô" có hiện không? Rồi **khi xe đỗ**, bật công tắc *Kéo app thoát ô về lại ô (thử nghiệm)* và làm lại ⇒ app có về lại ô không, gõ phím/chạm trong ô có được không, có thấy màn chớp hay dòng thông báo của xe không? Thấy gì lạ thì **tắt công tắc đi** và gửi log.
4. Mở *Cài đặt › Màn hình chính* xem dòng chữ dưới công tắc thử nghiệm — nếu ghi "Đã tự tắt vì lỗi …" thì chụp gửi về.

Gặp lỗi gì gửi lại thư mục `kachi-logs` như lần trước. Cập nhật tự động không lùi bản được, lỗi sẽ gom sửa ở bản sau.
