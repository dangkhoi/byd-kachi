# Tin nhắn gửi anh em test Kachi 2.93

> **Trạng thái**: Session · **Cập nhật**: 2026-10-07 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.93 (195) lên kênh cập nhật tự động — kiêm nhật ký thay đổi (changelog). Owner 06/10: *"cái camera, mình có thể lấy 4 cam, bẻ lại theo đúng 3 options này, đưa vào widget action, hoặc voice, để có thể mở lên được không nhỉ?"* · *"không nên timeout, các nút đều là toggle on/off là được"* · *"voice: mở cam trái, mở cam phải, chứ không phải ai cũng đọc là mở camera trái đâu nhé, nhiều option nhé"* · *"làm hết mọi thứ, ko để lại"*. Nguồn nội dung: kế hoạch `docs/specs/kachi-293-plan.html`; spec `docs/specs/kachi-293-cam.html`, `kachi-293-cast.html`, `kachi-293-voice.html`, `kachi-293-widget.html`, `kachi-293-slot.html`, `kachi-293-misc.html`, `kachi-293-wave2a.html`, `kachi-293-wave2c.html`. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.93 đã lên `main`.

---

Chào anh em, Kachi **2.93** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.93 (mã 195)**.

**Mới: camera theo yêu cầu (không cần xi-nhan)**
- Mở một trong 4 camera **sau · trái · phải · trước** bất cứ lúc nào. Camera hiện ở chỗ đã chọn cho camera xi-nhan (màn chính, hoặc cụm nếu bật *Hiện camera lên màn cụm*). Bấm lại hoặc nói lại là tắt — **không tự tắt theo giờ**.
- **Nút trên thanh nút**: *Cài đặt › Thanh trạng thái & thanh nút › Chọn nút trên thanh…* → khối **Camera theo yêu cầu**: *Camera sau · Camera trái · Camera phải · Camera trước · Tắt camera*. Nút của camera đang mở sẽ sáng.
- **Phím vô-lăng**: *Cài đặt › Phím vô-lăng › Thêm gán…* → chọn phím → loại *Camera theo yêu cầu* → chọn camera (bật/tắt) hoặc *Tắt camera*.
- **Ô widget**: chạm một ô trống → khối *Camera theo yêu cầu* → chọn các nút camera.
- **Giọng nói**, nói kiểu nào cũng được: *"mở cam trái"* · *"bật cam sau"* · *"xem cam lùi"* · *"cam bên phải"* · *"mở camera trước"*. Tắt: *"tắt cam"* (tắt camera đang mở) hoặc *"tắt cam sau"*.
- Mỗi lúc chỉ **một** camera: mở camera khác là thay luôn. Đang mở camera mà bật xi-nhan ⇒ camera xi-nhan hiện; tắt xi-nhan ⇒ camera vừa mở quay lại.
- **Chỉnh riêng từng camera**: *Cài đặt › Tiện nghi xe*, mục **Từng camera** → chọn Sau / Trái / Phải / Trước → *▶ Xem thử camera này*, kéo khung để đặt chỗ, chỉnh *Cỡ*, *Hình khung*, *Kiểu hình*, *Xoay*, *Lật gương*. Chỉnh lúc camera đang hiện là thấy ngay. Chỗ, cỡ, hình, kiểu lưu theo hồ sơ; xoay/lật lưu theo xe. Ai không chỉnh thì camera xi-nhan trái/phải y như cũ.
- ⚠ *"tắt camera"* giờ là tắt camera theo yêu cầu; **không có camera nào đang mở thì vẫn tắt Camera 360 của xe như cũ**. Gọi thẳng Camera 360: *"bật camera 360"* / *"tắt camera 360"*. Chỉ nói *"camera"* trống không thì Kachi hỏi lại.

**Giọng nói hiểu tốt hơn**
- Gọi app bằng **tên tiếng Việt** (Máy ảnh, Ghi âm, Máy tính…) nghe đúng hơn, nhất là khi ồn.
- *"tắt điều hòa và đèn đọc"* ⇒ tắt **cả hai** (trước đây tắt điều hòa nhưng lại bật đèn đọc).
- *"mở kính trước trái một nửa"* / *"… 50%"* ⇒ dùng đúng nút mở 50% của kính đó.
- Chỉ nói *"kính"*, *"cốp"*, *"cửa sổ trời"* (không có mở/đóng) ⇒ Kachi hỏi lại *"Mở hay đóng …?"* cho an toàn (ồn quá mất chữ "đóng" thì trước đây lại thành mở).
- *Dạy Kachi tên app* (*Cài đặt › Giọng nói*): lần 2 Kachi nhắc nói câu có ô — *"đưa <tên> vào ô số hai"* — để câu có ô cũng hiểu tên vừa dạy.

**Sửa lỗi**
- Ô lốp ghi thêm chữ *nổ lốp* / *bất thường* như màn gốc của xe — chỉ hiện khi cụm đồng hồ cũng báo đỏ/vàng, để không báo giả.
- Cụm / GMaps chắc hơn sau tắt-nổ máy: Kachi chờ lượt mở cụm đang dở xong rồi mới tự khởi động lại để chữa phím (trước đây có lúc cắt ngang, lượt sau không đổi được kiểu cụm).
- Nút **Dừng** chiếu cụm luôn ăn, kể cả khi lượt mở cụm đang chạy lâu.
- App tự bung ra khỏi ô (vd Waze) ⇒ Kachi báo *"… đã rời ô — app vẫn mở ngoài ô"* và trả ô về như cũ.
- Widget và dải lối tắt giữ chỗ đang cuộn (đổi sáng/tối, đổi đơn vị, mở app rồi về màn chính không còn nhảy về đầu).
- Lề trái/phải của màn chính hẹp lại còn 80 %.
- *Chế độ kiểm thử* tự đóng khi tắt máy (khi Kachi là màn hình chính).

**Nhờ anh em thử và chụp màn hình gửi về** 🚗
1. Mở/tắt từng camera (sau · trái · phải · trước) bằng **nút trên thanh nút**, **phím vô-lăng** và **giọng nói** → đúng camera không, có giật/đứng hình không? Đang mở một camera thì bật xi-nhan → camera xi-nhan có hiện, tắt xi-nhan có quay lại camera cũ không?
2. **Camera sau và camera trước**: hình có bị ngược, nằm ngang hay lật không? Nếu có, chỉnh ở *Từng camera › Xoay / Lật gương* cho đúng rồi chụp màn hình phần đã chỉnh gửi về.
3. Mở Camera 360 của xe (không mở camera nào của Kachi), bấm phím vô-lăng nói *"tắt camera"* → Camera 360 có tắt không?
4. Đang phát nhạc, mở một camera rồi nói *"tắt cam"* → camera tắt, không bật thêm gì khác?
5. Cụm **Chữ nhật** với GMaps: tắt máy, nổ lại, chiếu GMaps → có lên đúng khung đã chỉnh không? Chụp màn cụm.
6. *Dạy Kachi tên app*: dạy một app, lần 2 nói *"đưa <tên> vào ô số hai"* theo dòng nhắc; sau đó lúc lái nói đúng câu ấy → app có vào ô 2 không?

Bản này chưa chạy trên xe thật — gặp lỗi gì cứ chụp gửi về. Cập nhật tự động không lùi bản được, lỗi sẽ gom sửa ở 2.94.
