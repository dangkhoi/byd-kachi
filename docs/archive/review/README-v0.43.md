# v0.43 — Đợt 1 (an toàn) · test trên xe 2026-07-22

APK: `apk/ClusterNav-0.43-release.apk` · 132 test pass · build sạch từ `clean`.

## Đã sửa (chỉ những thứ ĐÃ QUA PHẢN BIỆN)

| # | Lỗi | Vì sao nguy hiểm |
|---|---|---|
| W1-1 | Autotest để lại **chỉ dẫn rẽ BỊA** trên cụm 3 phút | `emit()` bật heartbeat gửi lại mỗi 400ms suốt `STALE_MS`=3 phút, nhánh autotest không hề dọn. Tài xế nhìn thấy "rẽ phải 250m Nguyễn Huệ" không có thật, sống cả sau khi rút máy. Giờ dọn trong `finally`, chờ 5s cho kịp nhìn rồi xoá. |
| W1-2 | `selfTest()` **bơm mock thật** rồi gỡ | Đang trong hầm mà chạy self-test là gỡ luôn provider đang phục vụ. Chết giữa `start`/`stop` là để lại provider mồ côi **chặn GPS cả xe**. Giờ selfTest chỉ ĐỌC appops. Thêm dấu bền + dọn mồ côi trên alarm 60s (hook duy nhất chạy được khi tiến trình đã chết), đòi 2 lần liên tiếp mới dám gỡ. |
| W1-3 | "Không đọc được tốc độ" bị coi là **"xe đang đỗ"** | `SpeedProvider.mps()` trả 0.0 vĩnh viễn khi HAL câm, mà cổng cold-seed là `speed < 2.0` với ý nghĩa "đang đỗ" → trên xe đó cổng LUÔN mở, ghim GPS vào toạ độ cũ (tới 7 ngày) suốt chuyến, lại còn được miễn failsafe. Thêm `mpsOrNull()`: null = KHÔNG BIẾT = coi như đang chạy. Thêm trần tuổi seed 30 phút. |
| W1-4 | Tự chiếu lúc nổ máy **không kiểm xe có đang chạy không** | 25 giây sau khi nổ máy là lúc đang lùi ra khỏi chỗ đỗ, mà chuỗi `[30,16,35]` cấu hình lại phần cứng cụm. Giờ đòi quan sát khẳng định xe đứng yên (<0.5 m/s), và **cấm rung force-stop** cho mọi lần chiếu không do người bấm. Bỏ luôn migration tự gỡ ◈ của Android Auto/CarPlay — **migration không bao giờ được nới quyền**. |
| W1-5 | Một lần chiếu treo là **không bấm TẮT được nữa** | `busy` chiếm ở luồng gọi, nhả trong `finally` luồng nền, mà I/O dadb không có timeout — adbd treo đúng lúc CarPlay/AA ngắt WiFi. Cụm đang chiếu mà không trả về được. Giờ latch có hạn 90s, quá hạn thì thao tác sau giành quyền. |

## Cần kiểm trên xe (theo thứ tự)

1. **Nổ máy, KHÔNG động vào gì.** Nếu đã bật tự-chiếu: xe đứng yên thì app tự chiếu sau ~25s. Thử lần nữa nhưng **cho xe lăn bánh trước giây thứ 25** → log phải ghi `bỏ qua tự chiếu — xe đang chạy`.
2. **Chiếu → bấm TẮT** → cụm về đồng hồ gốc, app về màn giữa **toàn màn hình** (không phải cửa sổ nổi). Dudu launcher phải bấm được bình thường.
3. **Chiếu app này rồi đổi sang app kia**, vài lần. Không được dính mode của app trước.
4. **Chỉnh kích thước**: bấm nút ở panel app ĐANG chiếu (dòng xanh "● Đang chiếu app này"). Bấm ở app khác phải hiện Toast báo chưa chiếu.
5. **Vietmap chiếu lâu** → xem bong bóng tốc độ có nhân bản không (đã bớt một lần relaunch mỗi lần chỉnh).
6. **Chạy autotest** → xong phải **không còn** chỉ dẫn rẽ nào lạ trên cụm.
7. Vào hầm / ra hầm → dẫn đường không dựt theo chu kỳ.

Có gì lạ: mở **🩺 Chẩn đoán · lấy log gửi dev** → CHỤP → chụp màn hình gửi về. Không cần WiFi, không cần adb.

## CHƯA làm — chờ anh duyệt

Đợt 2 (sai hành vi đã báo): gom session identity vào một chỗ (`svcCall` theo profile — **DiLink5 hiện teardown sai service**), watchdog app-chết-giữa-chừng, sửa `restoreFullscreenOnMain` còn quét `displayId>=1` không lọc.
Đợt 3-4 (nợ kiến trúc, perf, test): `ClusterCast.kt` 924 dòng — gấp ~1.85 lần ngưỡng repo tự đặt; `applyBounds` trả câu tiếng Việt mà nơi gọi đi so chuỗi; `AppScale` lưu pixel tuyệt đối nên dữ liệu per-app hoá ra là per-model.

Chi tiết: `docs/review/2026-07-21-senior-review-plan.md` (có ràng buộc thứ tự C-1..C-6 — **đừng đảo**).
