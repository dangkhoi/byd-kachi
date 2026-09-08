# v0.44 — Đợt 2 (sai hành vi đã báo) · nối tiếp v0.43 (Đợt 1: an toàn)

APK: `apk/ClusterNav-0.44-release.apk` · 141 test pass · build sạch từ `clean`.
**Chưa cài lên xe lần nào** — v0.43 cũng chưa. Cả hai đợt cần test cùng lúc.

## Đã sửa (8/9 mục Đợt 2)

| # | Lỗi | Ai bị ảnh hưởng |
|---|---|---|
| W2-1 | Định danh phiên chiếu không sống qua tiến trình → **teardown gửi tới service không tồn tại** | Xe DiLink5. `svcName` chỉ gán trong `cast()`, mà `stop()`/`rollback()`/`reconcile` đọc nó ở tiến trình chưa từng chạy `cast()`. Giờ opcode **chỉ dựng được từ hồ sơ đã resolve** (`ClusterProfile.svcCall`), và hồ sơ đang giữ cụm được **lưu bền**. Không lưu `vd` — opcode 16 tái tạo VD id mới nên phải luôn dò lại. |
| W2-2 | **App đang chiếu chết mà cụm cứ chiếu tiếp** — khiếu nại số 1 | Watchdog trên alarm 60s sẵn có (khai trong manifest → sống cả khi tiến trình đã chết). Đòi 2 nhịp liên tiếp thấy app biến mất mới tự trả đồng hồ. Không nối được dadb ≠ app đã chết → không teardown. |
| W2-3 | Nhánh warm/cold quyết bằng **cờ RAM**, mà chính cờ đó quyết có đi nhìn hay không | Sau khi tiến trình bị kill, cờ về false và **không bằng chứng nào sửa được** → chiếu lại từ đầu ~8s trên cụm đang chiếu, ADAS nháy lại. Giờ luôn đi nhìn rồi mới quyết (`StackParse.isWarm`), và điều kiện là **có app đang ở trên cụm** chứ không phải "VD tồn tại" (VD tồn tại sẵn kể cả khi không chiếu). |
| W2-4 | Parser **chết hẳn trên Android 12** | DiLink5 in `RootTask id=` thay `Stack id=` → parse ra rỗng, cast tự huỷ với thông báo sai nguyên nhân. Một regex nhận cả hai. **Kèm bắt buộc** cổng `hasOverscan` (ràng buộc C-4): `wm overscan` đã bị gỡ khỏi Android 11+, sửa parser mà không có cổng này là biến DL5 từ *hỏng an toàn* thành *hỏng nguy hiểm*. |
| W2-5 | Khung tính theo **kích cụm đoán mò** | `lastClusterW/H` chỉ có sau một lần chiếu thành công → người mới chỉnh khung lần đầu là chỉnh theo số hardcode 1920×720. Giờ **đo thật ngay khi mở màn** qua `DisplayManager`, không cần shell, không cần chiếu. |
| W2-6 | Nút Kiểu ◠/▭ **im lặng vô tác dụng** ngoài Seal | Ý nghĩa opcode 30 bị đoán từ ngoài hồ sơ; DL5 có `castSeq=[16]` nên nút lưu được, nhãn đổi được, mà cụm không bao giờ đổi hình — một nút nói dối. Giờ hồ sơ tự khai `styleOps`, đời xe không hỗ trợ thì **ẩn hẳn nút**. |
| W2-7 | `rollback()` trả PIP bằng `"allow"` cứng | Ghi đè ý người dùng (họ có thể đã tự tắt PIP) và bỏ quên marker → lần sau báo `pipStuck` nhầm. Giờ dùng chung đường `restorePip`. |
| W2-8 | (a) Ghi một khoá settings **không tồn tại**; (b) "giá trị animation gốc" thật ra là giá trị **do chính app ghi** | (b) đáng chú ý: `Mượt UI` mặc định bật → mỗi lần mở app ghi 0.5 toàn cục → `cast()` đọc lại ra 0.5 rồi lưu thành "gốc" → bấm TẮT là chốt máy ở 0.5 vĩnh viễn. |
| W2-9 | `AutotestActivity` **exported, không permission** | Nó lái HAL cụm + provider vị trí. Bất kỳ app nào trên xe cũng gọi được. Khoá bằng `WRITE_SECURE_SETTINGS` — shell vẫn gọi được, app thứ ba thì không. |

## Cố ý CHƯA làm

**W2-5 phần đổi `AppScale` sang phân số.** Phần *lỗi* của nó (khung tính theo số đoán) đã sửa bằng cách đo thật.
Phần còn lại là *tính năng* — cho phép chia sẻ khung per-app giữa các xe khác kích cụm — kèm **migration dữ liệu
đã lưu của người dùng**. Em không tự làm đổi định dạng dữ liệu qua đêm; để anh quyết.

## Kiểm trên xe — gộp cả v0.43 và v0.44

Chạy checklist trong `README-v0.43.md` trước (7 bước, phần an toàn). Sau đó thêm:

8. **Chiếu app → giết app đó** (Cài đặt → Ứng dụng → Buộc dừng, hoặc rút cáp CarPlay) → **trong ~2 phút cụm phải tự trả về đồng hồ**, không cần bấm gì.
9. **Chiếu → tắt app ClusterNav khỏi recents → mở lại → bấm TẮT** → phải trả đồng hồ được (trước đây `stop()` bỏ qua reset vì mất `lastDisplayId`).
10. **Chiếu app A → đổi sang app B** ngay sau khi app ClusterNav bị kill → phải đi nhánh WARM (không chiếu lại từ đầu ~8s, không nháy ADAS).
11. Mở **Cài đặt chiếu** khi **chưa chiếu lần nào** → nhãn nhóm khung phải hiện đúng kích cụm thật (vd `Khung · 1920×720 (8:3)`), **không** kèm chữ "chưa đo".

Xe DiLink5 (nếu hội có ai): giờ mới bắt đầu chạy được — báo em log qua **🩺 Chẩn đoán**.

## Còn lại: Đợt 3-4

Nợ kiến trúc, perf, test. Đáng chú ý: `ClusterCast.kt` giờ **1092 dòng** — gấp hơn 2 lần ngưỡng repo tự đặt (500),
và Đợt 2 làm nó dài thêm. `applyBounds` vẫn trả câu tiếng Việt mà nơi gọi đi so chuỗi.
Chi tiết + ràng buộc thứ tự: `docs/review/2026-07-21-senior-review-plan.md`.
