# Tin nhắn gửi anh em test Kachi 3.0

> **Trạng thái**: Session · **Cập nhật**: 2026-10-11 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 3.0 (204) lên kênh cập nhật tự động — kiêm nhật ký thay đổi từ 2.98. 2.99 chưa từng lên OTA nên mọi thứ của 2.99 nằm trong 3.0. Nguồn: spec `docs/specs/kachi-299-plan.html` (R1–R10) + `docs/specs/kachi-300-plan.html` (R1–R5). Owner 11/10: *"A luôn đi, đóng dự án"* — OTA 3.0 **không qua buổi xe của owner**; 3.0 là bản cuối của dự án (`docs/_handoff/CLOSEOUT-3.0-2026-10-11.md`). Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 3.0 đã lên `main`.

---

Chào anh em, Kachi **3.0** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài (có bản mới thì nút ⚙ có **chấm đỏ nhỏ**), rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 3.0 (mã 204)**. Đây nhiều khả năng là **bản cuối** của Kachi — cảm ơn anh em đã chạy thử suốt thời gian qua.

**Ô màn hình chính**
- Công tắc *Cài đặt › Màn hình chính › **Kéo app thoát ô về lại ô*** nay **BẬT sẵn**. App hay tự bật ra khỏi ô thành toàn màn hình (hay gặp nhất là **Waze**) sẽ được Kachi đưa về đúng ô, app vẫn chạy tiếp. Ai đã tự tắt công tắc này ở 2.98 thì Kachi giữ nguyên lựa chọn của anh em. Thấy lạ thì tắt công tắc là về như cũ; nếu nó gặp lỗi, Kachi tự tắt và ghi lý do ngay dưới công tắc.

**Nhạc YouTube**
- Bài YouTube tự mở lúc lên xe (phát tiếp bài cũ, hoặc link/từ khoá ở ô *Phát gì*) nay **hết bài tự sang bài kế** (danh sách Mix của chính bài đó), không còn hát đúng một bài rồi nghỉ.
- Đổi lại, bài mở ra ở **khung thường** chứ không tự toàn màn như 2.96–2.98. Muốn toàn màn thì **chạm nút toàn màn** của YouTube — danh sách bài kế vẫn còn.

**Thanh trên**
- **Ngày tháng** trên thanh trên nay cùng màu + cỡ chữ với các chip bên cạnh, không còn chìm khi trùng màu ảnh nền.

**Giọng nói**
- Lúc Kachi **đang nghe** lệnh, nhạc **nhỏ lại còn khoảng 20 %** mức đang nghe; nghe xong nhạc **trả về mức cũ** (không dừng nhạc, không hiện thanh âm lượng). Đang nghe mà anh em tự vặn âm lượng thì Kachi giữ mức anh em vặn. Lỡ tắt máy đúng lúc đang nghe thì lần mở sau Kachi trả mức cũ.
- **Dẫn đường bằng nhiều app trong một câu**: *"dẫn đường đến chợ Bến Thành bằng Google Maps, VietMap và Waze"* ⇒ cả ba app cùng nhận điểm đến (trước chỉ app đầu dẫn, app khác chỉ mở trơn). App nào nằm trên cùng là app mở xong sau cùng.
- Hiểu tên app tiếng Anh tốt hơn: **"Waze"** (trước hay nghe thành "way"/"quây"), **"Google Maps"**, **"VietMap"**, **"YouTube"** (trước có lúc nghe thành "diếp").
- *"mở YouTube vào ô số 2"* không còn hỏi nhầm *"Số nào — Odo tổng hay Số VIN?"*. Nếu Kachi không nghe ra tên app thì hỏi *"Mở app nào?"* và vẫn nhớ ô số mấy.
- **"đóng cốp"** bị nghe thành "đóng góp" nay vẫn đóng cốp (vẫn chỉ khi xe đứng yên như trước).
- Câu ghép có vế bị nghe thiếu chữ: *"đóng lái xong bật đèn đọc"* (rụng chữ "kính") nay **BẬT** đèn đọc đúng như nói — trước bị làm ngược thành TẮT. Kachi báo vế không hiểu là đã bỏ qua. Nói *"đóng lái"* một mình thì Kachi hỏi lại *"Đóng gì?"* thay vì kết thúc phiên.
- Tấm chữ giọng nói **nhẹ hơn nhiều** (bớt khoảng 80 % CPU lúc đang nghe trên máy thử) — đầu xe yếu sẽ đỡ giật lúc tấm chữ hiện.

**Nhật ký**
- Tệp log `kachi-logs` không còn ngừng ghi giữa chừng khi xe ngủ dậy, và có thêm tệp riêng cho phần giọng nói (`usage-wake-…`) — anh em gửi log về sẽ đủ hơn.

**Nhờ anh em thử và gửi về** 🚗
1. **Nhạc nhỏ lại khi Kachi nghe**: mở YouTube (hoặc app nhạc khác), gọi Kachi ⇒ **tai nghe** nhạc có nhỏ hẳn lại không, nghe xong có to lại đúng mức cũ không? ⚠ Đây là phần **chưa thử được trên xe thật**: nếu núm âm lượng của xe anh em không đi theo âm lượng Android thì nhạc có thể **không nhỏ đi** — báo về giúp (có hay không đều quý). Lỡ thấy nhạc kẹt nhỏ sau khi Kachi nghe xong thì vặn núm lên là xong, và gửi log.
2. **Dẫn đường nhiều app**: **tắt nhạc trước** cho Kachi nghe rõ, rồi nói *"dẫn đường đến chợ Bến Thành bằng Google Maps, VietMap và Waze"* ⇒ app nào dẫn được, app nào chỉ mở mà không dẫn? Riêng **Waze** có vào thẳng chỉ đường không hay dừng ở màn kết quả tìm kiếm?
3. **Chủ SL6** (vẫn cần từ 2.98): bật *Dẫn đường lên cụm đồng hồ*, chạy dẫn đường Google Maps, nhìn **HUD kính lái** ⇒ có thấy **tên đường** không? Chụp HUD gửi về.
4. **YouTube**: để bài tự mở lúc lên xe chạy hết ⇒ có sang bài kế không?
5. **Công tắc kéo app về ô** (nay bật sẵn): mở YouTube / VietMap / Google Maps trong ô rồi dùng một lúc ⇒ app có bị bật ra rồi về lại ô không, chạm trong ô có được không? Thấy màn chớp, app đứng, hay đầu xe lạ ⇒ **tắt công tắc**, gửi log.

**Giới hạn đã biết (nói trước cho anh em đỡ mất công)**
- Giới hạn tốc độ theo bản đồ **không đẩy được vào ô biển gốc của cụm/HUD** — trên xe đã thử, xe nhận lệnh mà không hiện; cần mã hoá lại hộp ADAS ở tiệm, ngoài tầm app. Kachi giữ biển tự vẽ.
- Câu nhiều app mà nối thêm việc khác (*"… bằng Google Maps và VietMap rồi bật điều hòa"*) chưa tách được — nói thành hai câu.
- Câu trả lời của Kachi đi chung núm âm lượng với nhạc, nên giữa hai lượt nghe nhạc về lại mức cũ trong lúc Kachi đọc câu trả lời (nhạc vẫn được hạ nhẹ dưới tiếng Kachi như trước).

Gặp lỗi gì gửi lại thư mục `kachi-logs` như lần trước. Cập nhật tự động không lùi bản được.
