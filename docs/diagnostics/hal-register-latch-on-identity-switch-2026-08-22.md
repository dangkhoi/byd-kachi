# R1 — Register HAL LATCH sống qua lần đổi danh tính nguồn dẫn (chưa chữa)

**Ngày:** 2026-08-22 · **Bối cảnh:** hạng mục B-I (bất biến MỘT-PACKAGE-MỘT-KHUNG) · **Mức bằng chứng:** đã
chứng minh phần ĐỌC SOURCE, **chưa đo trên xe** phần hành vi cụm.

---

## 1. Phát biểu

B-I thu hẹp thứ ta **GHI** (chỉ ghi các kênh cùng một package). Nó **không** — và **không được** — đụng tới
thứ cụm **còn ĐANG HIỆN**. Sau khi đổi nguồn Waze → VietMap, **dải làn và badge camera cũ nhiều khả năng vẫn
sáng dưới mũi tên mới**, vì không có đường nào xoá chúng.

## 2. Bằng chứng đọc source (file:line)

| Nơi | Điều đọc được | Hệ quả |
|---|---|---|
| `app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt:393` | `if (segMeters >= 0) { … }` trong `pushNavigation` | `seg = -1` là **BỎ GHI**, KHÔNG phải xoá. Con số cự-ly cũ vẫn nằm trong register. |
| `BydHal.kt:351-358` `clearNavFrame` | chỉ ghi `INSTRUMENT_SEND_NAVI_STATUS_SET=4`, `INSTRUMENT_GUIDE_INFO_SIMPLE_SET=0`, `INSTRUMENT_FRONT_CROSSING_DISTANCE_SET=-1` | **KHÔNG chạm** `LANE_n_*` (`LANE_1_GUIDANCE_ARROW_ID = 0x19802058`, bước `0x10`) lẫn CAMERA (`GUIDE_INFO_CAMERA_ID = 0x43F03010`). |
| `NavOutputDecision.decide` (sau B-I) | DROP kênh lệch package, `clear` chỉ bật khi TẤT CẢ kênh stale | Đổi danh tính KHÔNG phát sinh clear (cố ý — xem §4). |

## 3. Vì sao KHÔNG được vá mù ngay trong B-I

Hai cách vá "hiển nhiên" đều nguy hiểm hơn bệnh:

1. **Gọi thêm `clearNavFrame` khi đổi danh tính.** Nó ghi `SEND_NAVI_STATUS` — session latch vốn là **độc
   quyền của `NavigationHudOwner`** (`PhysicalHudOwnershipTest` khoá đúng ranh giới này). Gọi từ owner ảnh có
   thể **hạ phiên HUD giữa lúc đang lái**.
2. **`pushCamera(0, …)` / `pushLane` rỗng để tự blank.** Nhánh `iconCode = 0` **chưa từng có call site nào**
   (grep) ⇒ **chưa từng chạy trên xe**. Ghi một giá trị chưa ai đo vào register cụm của xe đang chạy là đúng
   loại việc CLAUDE.md §3/§14 cấm.

## 4. Vì sao B-I chọn "DROP không kéo theo clear"

Nếu để DROP kéo theo `clear`, mỗi lần xuất hiện một kênh lạ (rất thường xuyên khi hai app dẫn cùng hiển thị,
hoặc trong 6 giây chuyển nguồn) khung đang hiện sẽ bị nhả rồi dựng lại ⇒ **cụm nhấp nháy trên xe đang chạy**.
Chọn: hiện ÍT hơn (thiếu làn/camera trong ≤ 6s) chứ không bao giờ hiện SAI, và không nhấp nháy.

## 5. Probe cần chạy trên xe (CLAUDE.md §14 tầng 1 — shell thô TRƯỚC, code SAU)

Mục tiêu: biết register có **đọc lại được** không, và `SEND_NAVI_STATUS = 4` thật sự nghĩa là gì. Chưa có ba
câu trả lời này thì **chưa được thiết kế đường blanking**.

```sh
# 0) Bản đang chạy trên xe (CLAUDE.md §9 — không bao giờ đoán)
adb shell dumpsys package com.byd.clusternav | grep -m1 versionName

# 1) Trong lúc ĐANG DẪN bằng Waze (làn + mũi tên đang hiện trên cụm):
#    ghi rồi ĐỌC LẠI từng register — cần biết register là WRITE-ONLY hay đọc lại được.
#    (chạy qua màn DiagActivity / ClusterDiag nếu adb ngoài không vào được — xem CLAUDE.md §11)
#    LANE_1_GUIDANCE_ARROW = 0x19802058 · IS_LANE_1_RECOMMENDED = 0x19802064
#    GUIDE_INFO_CAMERA     = 0x43F03010 · CAMERA_DISPLAY_STATE  = 0x43F03018
#    FRONT_CROSSING_DISTANCE (domestic) + DISTANCE_TARGET_HEAD (oversea)

# 2) Chuyển sang VietMap dẫn (Waze về nền). Chờ > 6s (STALE_MS) rồi:
#    - CHỤP MÀN cụm: dải làn cũ còn sáng không? badge camera cũ còn không?
#    - ĐỌC LẠI đúng các register ở bước 1 → so sánh giá trị.

# 3) Ngữ nghĩa SEND_NAVI_STATUS: ghi 4 rồi đọc lại + chụp cụm; lặp với 2 (đang dẫn).
#    Cần biết 4 = "tắt nav" hay chỉ "đổi mode", và nó có kéo theo blank LANE_n_* không.
```

**Ghi lại:** giá trị đọc được + ảnh chụp cụm cho mỗi bước, vào chính thư mục này.

## 6. Chỉ SAU KHI có số đo mới được thiết kế

- Nếu register **đọc lại được** và `LANE_n_*` giữ giá trị cũ ⇒ cần một đường **blank làn/camera riêng**
  (không đi qua `clearNavFrame`, không chạm session latch), gọi khi `plan.framePkg` đổi.
- Nếu `SEND_NAVI_STATUS = 4` thật sự blank cả dải làn ⇒ vẫn KHÔNG dùng nó từ owner ảnh; phải chuyển yêu cầu
  cho `NavigationHudOwner` (chủ latch) qua một seam rõ ràng.

Đây là **hạng mục riêng**, gate bằng phép đo ở §5. B-I dừng đúng ở chỗ "không bao giờ GHI sai".
