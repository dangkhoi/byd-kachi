# Next on-car: Investigate full-screen cast (like CarPlay native)

## Observation (SL8 reference photo 2026-08-03)
- CarPlay trên Sealion 8 cast Google Maps **full cụm** — bản đồ phủ toàn bộ pixel
- Gauges (km/h, pin, trip) là **overlay OEM đè lên** bản đồ — không phải app vẽ
- Kết quả: bản đồ đẹp, full, không bị bóp

## Hiện trạng trên Seal/SL6
- Bounds proven: `[0, 90, 1920, 630]` (tối 02/8) — app hiện đúng, nhìn được
- Nhưng bị "bóp" vào giữa — không full như CarPlay
- Chưa thử bounds `[0, 0, 1920, 720]` trên xe → **cần test**

## Thí nghiệm cần làm trên xe
1. `am task resize <taskId> 0 0 1920 720` — full display, không inset
   - Kết quả mong đợi: app full cụm, gauge OEM đè lên (giống CarPlay)
   - Có thể: app bị cắt/không hiện/hiện sai → ghi nhận

2. Thử `wm overscan 0,0,0,0 -d 1` trước khi cast (reset overscan)

3. So sánh: CarPlay native trên SL6 có full không? Hay cũng bị inset 90px?
   - `am stack list` khi CarPlay đang chiếu → xem bounds của task CarPlay

4. `dumpsys display` khi CarPlay đang full → xem OEM config gì cho nó

## Bài học
- CarPlay native LÀM ĐƯỢC full cụm → mình CŨNG phải làm được
- Không phải bế tắc, chỉ là chưa tìm đúng config
- Cần đo trên xe, không đoán

## Khi tìm được config đúng
- Cập nhật `AppMover.fitToCluster()` bounds
- Có thể cần option: full (đè gauge) vs safe (tránh gauge) cho user chọn
