# Firmware 2606 = OTA "V2.0.4" — ghi chú phát hành chính thức (màn xe) + đối chiếu RE

> **Trạng thái**: Current · **Cập nhật**: 2026-10-08 · **Loại**: Diagnostics · **Owner**: dangkhoi ·
> **Nguồn**: [ĐO ảnh màn xe owner 08/10] *Cài đặt › Phiên bản › Chi tiết phiên bản* ("Hướng dẫn nâng cấp phiên bản", V2.0.4) ·
> RE off-car: [`fw-2606-blocker-recheck.md`](fw-2606-blocker-recheck.md) · build `eng.build.20260610` (phần mềm giải trí 13.1.33.2606100.1).

BYD ghi "các chức năng nêu trên đã có sẵn trên một số xe" — mục nào có trên Seal VN cần nhìn trên xe.

## Tính năng mới (13)
1. Liên kết bộ nhớ giữa xe và tài khoản — cài đặt cá nhân của chủ xe theo tài khoản BYD.
2. Nút chuyển tài khoản một chạm trong Trung tâm Tài khoản.
3. Lên lịch sạc cho sạc thông minh.
4. Làm nóng trước khi sạc (một số cấu hình).
5. Trợ lý BYD thêm lệnh giọng nói: tắt sưởi, bật/tắt thông gió (một số quốc gia).
6. Chọn bài Spotify bằng giọng nói (một số quốc gia).
7. TuneIn Radio trong Cửa hàng BYD.
8. Quản lý dữ liệu đám mây.
9. Ghi nhớ cài đặt ITAC sau khi khởi động lại (chỉ dẫn động bốn bánh).
10. Công tắc chỉnh tốc độ hành trình bằng bàn đạp ga (chế độ ghi đè) cho ICC.
11. Công tắc chỉnh tốc độ trên đường cong cho ACC và ICC (một số quốc gia).
12. Hợp tác lái xe cho ICC và LDA.
13. Bám mép làn đường mở rộng cho ICC.

## Tối ưu (10)
1. Android Auto / Apple CarPlay (một số thị trường).
2. Thao tác ứng dụng BYD với mật khẩu lái xe.
3. Điều kiện tắt ICC: tắt được khi chưa kích hoạt.
4. Kích hoạt giọng nói bằng nút vô-lăng khi màn hình tắt.
5. Logic thoát chế độ xem toàn cảnh.
6. Bản dịch đa ngôn ngữ.
7. Hiển thị giao diện và lời nhắc.
8. Chiến lược xả điện khi nhiệt độ thấp (tăng công suất khi lạnh).
9. Chào đón ghế trước; chống kẹp hàng ghế sau (một số mẫu xe).
10. Trải nghiệm chuyển số.

## Đối chiếu
- Khớp gần trọn các bản Seal châu Âu V2.1.0 (12/2025: ICC/LDA, chỉnh tốc vào cua) · V2.1.1 (01/2026: liên kết tài khoản, lệnh giọng nói điều hoà, đám mây) · V2.2.0 (05/2026: chuyển hồ sơ, TuneIn) ⇒ V2.0.4 VN = bản gom [SUY].
- Chuỗi giao diện MỚI trong app Cài đặt xe 2606 (Trợ lý quản lý, phanh khẩn cấp tốc độ thấp, cốp chiếu biểu tượng, đèn đỗ…) **KHÔNG** có trong ghi chú ⇒ là mã dùng chung nhiều dòng xe, không phải tính năng của Seal VN [SUY] — đừng coi là tính năng mới.
- Không ghi chú nào nói về ISA/biển tốc độ theo bản đồ; mã có sẵn (`setIsaMap*`) ⇒ vẫn chỉ là cửa cần đo (`oncar-runbook-2.96.md` §B).

## Mục có thể đụng Kachi (đã thêm vào `oncar-runbook-2.96.md` §A)
- **Tối ưu #4** — BYD đổi đường nhận nút giọng nói vô-lăng khi màn tắt; Kachi cũng bắt phím vô-lăng để gọi Kachi ⇒ kiểm ai lên (Kachi hay Trợ lý BYD) khi màn bật/tắt.
- **Tối ưu #5** — logic thoát camera 360 đổi; Kachi có camera theo yêu cầu ⇒ kiểm mở/tắt camera Kachi không làm 360 của xe bật/tắt sai.
- **Mới #1–#2** — tài khoản/hồ sơ BYD tách biệt hồ sơ Kachi (không liên quan, tránh nhầm khi đổi hồ sơ).
