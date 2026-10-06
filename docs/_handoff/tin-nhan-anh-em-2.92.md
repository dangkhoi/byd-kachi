# Tin nhắn gửi anh em test Kachi 2.92

> **Trạng thái**: Session · **Cập nhật**: 2026-10-06 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.92 (194) lên kênh cập nhật tự động — kiêm nhật ký thay đổi (changelog). Owner 06/10: *"widget shortcut app, icon hơi bé so với thanh, margin 2 bên nhiều quá phí… không nên giới hạn 8 app"* · *"có lấy thêm được ngoài sau không?"* · *"ok làm luôn đi, xong thì OTA 2.92 luôn nhé"* · *"lên 2.92 test trước, có lỗi gôm vô 2.93"*. Nguồn nội dung: spec `docs/specs/kachi-292-shortcut-widget.html`, `docs/specs/kachi-292-camera-full-view.html`, `docs/specs/kachi-292-diag-cap.html`, `docs/specs/kachi-292-profile-new-keys.html`, `docs/specs/kachi-292-cluster-frame-chosen.html`; nghiên cứu `docs/diagnostics/camera-rear-coverage-2026-10-06.md`. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.92 đã lên `main`.

---

Chào anh em, Kachi **2.92** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.92 (mã 194)**.

**Mới: camera xi-nhan thấy xa ra phía sau**
- *Cài đặt › Tiện nghi xe › Camera theo xi-nhan › Kiểu hình camera*:
  - **Nắn thẳng**: như cũ, sát thân xe (mặc định — ai không chỉnh thì không đổi gì).
  - **Thẳng rộng (thấy xa)**: gần như thẳng (cột, mép xe vẫn thẳng), nhìn tới phía sau xa hơn hẳn.
  - **Gương cầu (thấy hết)**: trọn ảnh mắt cá như gương cầu, không cắt gì.
- Thêm thanh **Thu phóng** 50–150 %. Đổi kiểu hay thu phóng khi camera đang hiện là thấy ngay.
- Dùng *Thẳng rộng* thì nên để *Hình khung camera* là **Chữ nhật** (khung tròn bị hụt hai mép trên/dưới).
- Kiểu và thu phóng lưu theo hồ sơ.

**Widget Lối tắt ứng dụng**
- Icon to hơn, lề hai bên nhỏ lại (khung dọc hẹp 8 app: từ 1 cột icon nhỏ thành 2 cột icon to, lề còn ~12 px).
- **Không còn giới hạn 8 app**: *Cài đặt › … › Lối tắt ứng dụng › Chọn ứng dụng…* chọn bao nhiêu cũng được; nhiều quá thì widget **cuộn**, mép mờ dần báo là cuộn được.
- Các widget khác (đồng hồ, tốc độ, trạng thái xe, nhạc, năng lượng, bụi mịn) bớt lề trong; widget của app khác giờ lề đều 8 dp.

**Sửa lỗi quan trọng**
- **Kachi dọn bộ nhớ có thể xoá nhầm ảnh của anh em**: trước đây khi thư mục của Kachi vượt ~150 MB, Kachi xoá file cũ nhất — kể cả **ảnh trình chiếu, hình nền, ảnh xe, tệp hồ sơ** chứ không riêng file nhật ký. Giờ Kachi chỉ dọn file nhật ký/chẩn đoán. Ai thấy thiếu ảnh trình chiếu thì bỏ ảnh vào lại (file đã xoá không lấy lại được).
- **GMaps (và app khác) lên cụm Chữ nhật dùng đúng khung anh em đã chỉnh**: trước đây sau mỗi lần tắt/nổ máy, nhiều lượt chiếu Kachi bỏ khung đã lưu, kéo app ra trọn cụm 1920×720, và chỉnh khung lúc đó bị lưu nhầm chỗ ⇒ lúc đúng lúc không. Giờ khung đã lưu của kiểu cụm anh em chọn luôn được dùng. Nếu cụm hiện sai kiểu (Bo tròn/Chữ nhật), vào *Cài đặt › Chiếu cụm* bấm **Áp ngay**.
- **Đổi hồ sơ không còn mang lựa chọn mới sang hồ sơ cũ**: kiểu hình + thu phóng camera, kiểu chiếu cụm (Bo tròn/Chữ nhật), ẩn/hiện bóng VietMap chọn ở hồ sơ này không còn tự đi theo sang hồ sơ lưu từ bản trước — mỗi hồ sơ giữ cái của mình.

**Nhờ anh em thử và chụp màn hình gửi về** 🚗
1. Bật xi-nhan trái/phải với từng kiểu **Nắn thẳng · Thẳng rộng · Gương cầu** → kiểu nào nhìn rõ xe phía sau nhất? Chụp màn từng kiểu.
2. Kéo **Thu phóng** khi camera đang hiện → hình đổi theo ngay không?
3. Widget **Lối tắt ứng dụng** trong khung dọc hẹp: icon có to, lề hai bên có còn phí không? Chọn hơn 8 app → có cuộn được, bấm đúng app không?
4. Widget của app khác (đồng hồ, lịch…) trong khung: có bị cắt chữ không?
5. Cụm **Chữ nhật**: chỉnh khung GMaps cho vừa, tắt máy rồi nổ lại, chiếu lại GMaps → có lên đúng khung vừa chỉnh không? Chụp màn cụm.

Bản này chưa chạy trên xe thật — gặp lỗi gì cứ chụp gửi về. Cập nhật tự động không lùi bản được, lỗi sẽ gom sửa ở 2.93.
