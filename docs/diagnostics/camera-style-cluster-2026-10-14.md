# Camera xi-nhan: chọn «Theo cụm» mà vẫn ra chữ nhật (2026-10-14)

> Trạng thái: Current · 3.1.5 (chưa bump/OTA) · backlog `CAMERA-STYLE-CLUSTER-RECT`
> Owner 14/10: *"camera xi nhan, chọn kiểu thể hiện theo cụm nó vẫn chữ nhật"*.

## 1. Bằng chứng

Nguồn: logcat chuyến 14/10 07:59–08:49 (Seal, 3.1.4) cùng các tệp `usage-*.log` app tự ghi từ 06/10 tới 14/10.

| Mức | Khẳng định |
|---|---|
| [ĐO] | Có 29 phiên camera trên xe 14/10, phiên nào cũng `kiểu=WIDE` (*Thẳng rộng*), `hình=RECT`, `kết xuất=GL`, `chỗ=TL` (cả camera PHẢI), `cửa=321x428 tại=140,132 lớp=321x428 phóng=100% cong=140/140/140` (mép thẳng). Ví dụ: 08:00:34.088, 08:03:20.070, 08:05:29.705 |
| [ĐO] | Từ 07/10 07:10 tới 14/10 08:47, mọi phiên đều là `WIDE`/`RECT`. Lần cuối thấy `hình=CLUSTER`: 06/10 13:53:15 (2.91, *Nắn thẳng*, `cửa=391x428 tại=19,132 lớp=391x521 phóng=122% cong=46/41/89`). Từ 06/10 13:54:41, `camera_shape` CHUNG đã là `RECT`, không có dòng log nào ghi lại lượt đổi đó (chạm chip ở Cài đặt không ghi log) |
| [ĐO mã] | Phép ép *Theo cụm* + kiểu trọn dải ⇒ chữ nhật: `core/.../camera/CameraViewMode.kt` `frameShape()` (2.92 spec R7), được `CameraViewPlan.crops` gọi. Kết quả `crops.frameShape` đi vào `overlay.show(shape = …)` (`CameraSignalController.openSession`) và vào ô xem trước *Từng camera* (`ClusterNavBridgeCameraPerCam.kt:115`) |
| [SUY mạnh] | Người lái ở *Thẳng rộng* (đo được ở mọi phiên) mà chọn *Theo cụm* thì kiểu gì cũng ra chữ nhật. Chip vẫn sáng *Theo cụm* nên lỗi im lặng (silent-wrong) |
| [CHƯA BIẾT] | Owner chọn *Theo cụm* lúc nào, ở hàng CHUNG hay ở *Từng camera*. Log chuyến tới 08:47 chưa có phiên nào sau một lượt chọn như vậy. Nếu hàng *Từng camera* đang để `RECT` thì nó thắng hàng chung (`CameraCamConfig.effectiveShape`): đó là hành vi đúng thiết kế, không phải lỗi |
| [ĐO git] | Không phải lỗi mới của 3.1: `git diff e5f0c122` không động tới hình khung, dải cụm hay `ClusterProfile.band`. Phép ép có từ 2.92 (`6bfe20b0`) |

Phép ép này trái với quyết định của owner ngày 27/09: *"khi chiếu camera lên cụm, user vẫn có thể chọn chữ nhật/tròn/theo cụm nhé, không ép"* (KDoc `CameraClusterBand.effectiveShape`).

## 2. Sửa (tầng chung, 3.1.5)

- Gỡ `CameraViewMode.frameShape`. `CameraViewPlan.crops` trả đúng hình người lái chọn ở mọi kiểu hình. Kiểu trọn dải vẫn lấy khung = nội dung như trước.
- Phép quy duy nhất còn lại là theo display: *Theo cụm* trên màn chính ⇒ chữ nhật (`CameraClusterBand.effectiveShape`), không đổi.
- Hình học *Theo cụm* đi lại đúng đường đã chạy trên xe 06/10: nới ra kính, phóng-cắt kiểu cover (không kéo giãn), mép trong mờ dần. Cái giá là mất ~18 % tầm nhìn ở mép, và owner đã chấp nhận cái giá này cho *Theo cụm* (*"chấp nhận mất 20%"*). Với *Gương cầu* + *Theo cụm*, câu "không cắt gì" trong ghi chú Cài đặt không còn đúng nữa (xem §4).
- Chi phí: 0 lệnh shell, 0 luồng, 0 nhịp, 0 listener mới. Chỉ bỏ một nhánh.

Bài khoá trong `core/src/test/.../camera/CameraViewPlanTest.kt`:

- `theo cum giu theo cum o moi kieu`: 3 kiểu × 3 hình × 2 bên.
- `log xe 14-10 - Thang rong + Theo cum tren cum ra theo cum, khong ra chu nhat`: dựng từ đúng số trong log. Nguồn 5120×960, crop `0.25,0,0.5,1` rot −90 và `0.5,0,0.75,1` rot 90, `chỗ=TL`, dải `140,132-1780,560`. Chữ nhật phải ra đúng `321x428 @140,132` như log. *Theo cụm* phải nới ra kính, có bảng cong, lớp cao hơn cửa và dải mờ. Khôi phục phép ép thì bài này ĐỎ.

Bài `Theo cum o kieu tron dai ve chu nhat` cũ (ở `CameraViewModeTest` và `CameraViewPlanTest`) khoá đúng phép ép vừa gỡ, nên đã xoá cùng với nó.

## 3. Kiểm trên xe (lần tới)

1. Cài đặt › Camera: hàng chung *Hình khung camera* chọn **Theo cụm**. Ở *Từng camera* (trái, phải), *Hình khung* để **Theo chung**.
2. Giữ *Kiểu hình* = *Thẳng rộng*, bật xi-nhan. Trong log `KachiCamera`, dòng `camera … kiểu=WIDE … hình=CLUSTER` và dòng `overlay show … hình=CLUSTER … phóng=1xx% mất=1x% cong=a/b/c` phải có ba số `cong` KHÁC nhau. Mắt thường: mép ngoài bám kính cụm, mép trong mờ dần.
3. Nếu dòng đầu vẫn là `hình=RECT` thì lượt chọn chưa được lưu, hoặc hàng *Từng camera* đang đè. Đọc `camera_shape`, `camera_shape_left`, `camera_shape_right` bằng `prefs` của cầu kiểm thử.

## 4. Còn mở

- Chuỗi `kachi_camera_projection_note` (*"Gương cầu: … không cắt gì"*) sai khi đi cùng *Theo cụm*. Sửa chữ ở 5 ngôn ngữ cần owner chốt câu.
- Ghi log lượt chạm chip hình khung ở Cài đặt, để lần sau biết pref đổi lúc nào (06/10 13:54 không truy được).
