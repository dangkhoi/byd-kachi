# Tin nhắn gửi anh em test Kachi 3.1.13

> **Trạng thái**: Session · **Cập nhật**: 2026-10-17 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 3.1.13 (218) lên kênh cập nhật tự động — kiêm nhật ký thay đổi từ 3.0 (204). Kênh đã đi 3.0 → 3.1.10 (215) ngày 16/10 mà **chưa có tin nhắn**, nên tin này gom mọi thứ người đang ở 3.0 nhận được; 3.1.1–3.1.9 và 3.1.11–3.1.12 chỉ là bản thử cài tay, chưa từng lên kênh. Nguồn: commit `9b22a7f7..3c075455` (thân commit 3.1.1–3.1.13) · spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` (R1–R32) · `.kiro/steering/project-context.md` khối 3.1.x · `apk/README.md` · chẩn đoán `diagnostics/voice-oncar-2026-10-17.md` · `diagnostics/seat-vent-auto-off-2026-10-14.md` · `diagnostics/trunk-state-read-2026-10-15.md` · `diagnostics/hud-clusterdebug-path-2026-10-16.md` §9 · `diagnostics/cluster-black-patch-after-cast-off-2026-10-17.md`. Tin riêng cho chủ SL6: `_handoff/sl6-hud-roadname-tester-2026-10-17.md`. OTA **không qua buổi xe của owner**. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 3.1.13 đã lên `main`.

---

Chào anh em, Kachi **3.1.13** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài (có bản mới thì nút ⚙ có **chấm đỏ nhỏ**), rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 3.1.13 (mã 218)**. Anh em nào đang ở 3.0 thì đây là lần đầu nhận dòng 3.1 — dưới đây là mọi thứ mới từ 3.0.

**Widget xe 3D (mới)**
- Hình xe 3D trên màn chính: **kính, cửa, cốp, đèn, ghế** hiện theo trạng thái thật của xe. Các phần bấm được có **chấm** — chạm chấm là điều khiển (chạm đúp chấm kính = mở hết). **Vuốt để xoay** tự do quanh xe.
- Mô hình lấy từ chính xe (app camera 360 của BYD). Xe nào không vẽ được 3D thì khung ghi *"3D không khả dụng trên xe này"* — **không bao giờ tự lùi về hình 2D**.
- Chọn màu xe, ảnh nền sau xe, hiện áp suất/nhiệt độ lốp dưới từng bánh ở *Cài đặt › Giao diện › Hình xe*.
- Chỉ chạy khi đang hiện trên màn (rời màn chính, tắt màn ⇒ tự nghỉ). Icon sưởi/mát trên ghế không còn đè chấm kính; SL6 hết bị cháy sáng trắng.

**Màn hình chính**
- **Chữ và icon tự đổi màu** theo ảnh nền phía sau (cả Sáng lẫn Tối), đổi đồng đều cả mặt — hết cảnh lúc thấy lúc không.
- Đầu mỗi ô có thêm nút **Bung to ô**: bung một khung ra trọn màn chính, ẩn các khung khác; chạm lại là về đúng như cũ. Dùng được ngay khi đang lái, không phải đổi hồ sơ.
- **Thanh nút xe có chữ lại** dưới icon như 3.0.
- Widget co giãn theo cỡ ô không còn vỡ chữ; hộp thoại đồng bộ một kiểu, nút to dễ chạm. Bộ chọn ô có hàng **Gần đây** trên cùng.

**Cốp, gió, đèn, ghế**
- **Cốp đọc trạng thái thật** của xe: đóng/mở bằng nút trên xe thì ô cốp + nắp cốp 3D về đúng. Phím gán *cốp (đảo)* luôn chạy: đọc được thì theo xe, xe đang chạy thì chỉ đóng. Mở cốp khi xe chạy vẫn bị chặn ở mọi đường.
- **Số gió** hết kẹt số cũ (lăn núm vô-lăng 2 → 3 giờ hiện đúng 3); nút gió 3D ghi "AUTO n". Bấm phím/nói lệnh thì mọi ô trên màn vẽ lại ngay, không đứng số cũ.
- Đèn ban ngày hiện đúng bật/tắt; xi-nhan trên màn theo xe thật.
- **Sưởi/mát ghế**: firmware xe tự tắt sau khoảng 10 phút ⇒ Kachi **bật lại** cho anh em (với mọi ghế đang bật, có giới hạn số lần).

**Chiếu cụm đồng hồ**
- Đang chiếu một app mà mở app khác ở màn chính rồi chạm **bóng nổi** ⇒ cụm **đổi sang app mới trong một chạm** (trước phải chạm hai lần).
- Nút −/+ chỉnh khung cụm ăn đúng từng bước, giữ để lặp, chọn bước 5 / 20 / 50 px. Waze trên cụm không còn bị ép khung.
- **Mới, tuỳ chọn**: *Cài đặt › Dẫn đường + HUD › **Tên bài trên cụm khi đang chiếu*** — **mặc định TẮT**. Bật lên thì khi đang chiếu, một góc khung chiếu hiện *"♪ tên bài — nghệ sĩ"* chạy chữ (chọn góc: Trái/Phải, trên/dưới), dừng nhạc hơn 10 giây thì tự ẩn. Phần này **chưa từng chạy trên cụm thật** — nhờ anh em bật thử (xem mục nhờ thử).

**Hey Kachi + giọng nói**
- Hey Kachi **nhẹ CPU hơn nhiều** và **sẵn sàng nhanh hơn**; gọi xong nói lệnh liền được, không mất chữ đầu. Lần bấm phím nghe đầu tiên không còn "không nghe rõ".
- Bắt được câu gọi **nói nhỏ** hoặc bị nghe lệch (kiểu *"hay ca trên"*).
- **Không còn tự bật** khi giọng dẫn đường đọc *"có camera"* hay khi trong xe có người nói chữ *"khách"*.
- *"nhạc Sơn Tùng trên YouTube"*, *"video … trên YouTube"* — **không cần chữ "mở/phát"** vẫn phát. *"nhạc của Đen"* ⇒ tìm "Đen". Câu hỏi có chữ **"gì"** được hiểu.
- Nhạc nhỏ lại khi Kachi nghe tính theo độ to thật của loa, không bao giờ tắt hẳn; micro mở không chậm hơn 3.0.

**Chung**
- **Nhẹ hơn, ít đọc xe hơn**: Kachi nghe sự kiện xe thay vì hỏi liên tục, màn chỉ vẽ lại phần đổi, log gọn hơn, rời màn chính tốn ít RAM hơn.

**Nhờ anh em thử và gửi về** 🚗
1. **Widget 3D**: chạm chấm kính/cửa/cốp/đèn, xoay xe ⇒ xe có làm đúng, hình có đúng trạng thái không? Xe không lên 3D thì chụp dòng báo gửi về.
2. **Tên bài trên cụm**: bật *Tên bài trên cụm khi đang chiếu*, chiếu bản đồ lên cụm, mở YouTube/YouTube Music ⇒ chụp cụm: dải chữ nằm **góc nào**, có **đọc rõ** không, có che gì quan trọng (tốc độ, chỉ đường) không? Thấy lạ thì tắt công tắc.
3. **Sưởi/mát ghế**: bật rồi để quá 10 phút ⇒ ghế có tự bật lại không?
4. **Hey Kachi**: gọi nhỏ giọng, gọi lúc đang dẫn đường ⇒ có ăn không, có tự bật nhầm không?
5. **Chủ SL6 — tên đường lên HUD**: vào *Cài đặt › Dẫn đường + HUD › **Đường gửi tên đường lên HUD***, có 4 nút **A** (cũ, mặc định) · **A48** · **B** · **C**. Xe đỗ (hoặc người ghế phụ làm), chọn **B**, dẫn đường Google Maps/VietMap qua ít nhất 2 con đường khác tên ⇒ HUD có hiện **tên đường** không, đúng dấu không, mũi tên còn không? Không hiện thì thử **C**, rồi **A48**. Gửi ảnh hàng cài đặt (dòng *"Gửi lúc …"*) + ảnh HUD cho từng cách; B/C bị mờ thì chụp dòng lý do là đủ. Có gì lạ thì chọn lại **A** ngay. Chi tiết trong tin riêng cho chủ SL6.

**Giới hạn đã biết (nói trước cho anh em đỡ mất công)**
- **HUD Seal không hiện được dẫn đường** — đã đo trên xe, gói tới xe nhưng HUD không hiện bằng cả hai cách; ngoài tầm app.
- Tắt chiếu cụm xong, **giữa cụm có thể còn mảng đen** tới lần chiếu sau hoặc khởi động lại — chưa rõ gốc, để bản sau.
- Ngay sau khi cập nhật, YouTube trong ô có thể phải **bấm phát một lần**.

Gặp lỗi gì gửi lại thư mục `kachi-logs` như lần trước. Cập nhật tự động không lùi bản được.
