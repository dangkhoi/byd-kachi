# Hướng dẫn chạy thu data — sáng 2026-08-18 (cho anh em)

APK: **ClusterNav2.0-debug-thu-data-20260818.apk** (bản debug, tự ghi log).
Mục tiêu: thu đủ data để map icon dẫn đường lên HUD (đặc biệt **vòng xuyến**), so sánh GMaps/VietMap/Waze.

## 1. Cài + chuẩn bị (5 phút)
1. Cài APK (gỡ bản cũ `com.byd.clusternav2` nếu khác chữ ký → cài lại).
2. Mở app **Cluster Nav 2.0** → bật **Navigation + HUD**, bật **Cluster Cast**.
3. Mở đồng thời (cùng dẫn 1 tuyến càng tốt): **Google Maps** + **VietMap** + **Waze** + **Waze Mod**.
   - GMaps để **màn chính** dẫn đường (nguồn nav chính).
   - Bật cả VietMap/Waze/WazeMod dẫn để thử lấy tín hiệu của chúng.
4. (Muốn xem badge tốc độ trên cụm thì cast active + để mặc định — badge tự bật.)

## 2. Lái thế nào để data GIÁ TRỊ
- **QUAN TRỌNG NHẤT: đi qua NHIỀU VÒNG XUYẾN** (các loại: 2/3/4 nhánh, ra nhánh 1/2/3, trái/phải) — chuyến trước THIẾU vòng xuyến, cần bù.
- Đi qua đủ kiểu rẽ: trái/phải/chếch/gắt/quay đầu/giữ làn.
- Đi đường **có biển tốc độ** + nếu được **qua camera tốc độ** (để bắt icon + giá trị camera VietMap).
- Đi đủ dài (30–60 phút), nhiều loại đường.
- Nếu tiện: 1 đoạn **mở YouTube đè lên** trong lúc GMaps+VietMap dẫn nền (test thu data khi không foreground).

## 3. Xong → lấy log
- Theo `docs/HUONG-DAN-LAY-LOG.md` (Cách A USB hoặc WiFi) — kéo cả thư mục `files/` về gửi lại.
- Log tự ghi (debug auto-verbose), không phải bật gì.

## 4. App thu gì (để off-car phân tích)
- **GMaps**: nav_notif (hướng rẽ/cự ly/đường/ETA + **maneuver code** cho icon/vòng xuyến) + nav_arrow (bitmap icon) + accessibility (giọng dẫn, ground-truth cự ly).
- **VietMap**: vietmap_signal (tốc độ + giới hạn + **alert icon**: camera/biển) + vietmap_views (field lạ) + alert value (đã fix đọc place_holder).
- **Waze/WazeMod**: nav qua **accessibility announcement** (giọng) — nav_access nay **có cột package** để tách GMaps/VietMap/Waze/WazeMod.
- **Ảnh so sánh** mỗi ngã rẽ: `-cluster` (fission -d0 = cụm + badge), `-main` (màn chính), `-cluster-overlay` (surface cast).

---

# PLAN off-car (sau khi có data)

## Đã làm (commit bd09a4d, feat)
1. nav_access + tag package + nghe 5 app (GMaps×2/VietMap/Waze/WazeMod).
2. Fix VietMap alert extraction (place_holder).
3. Fix ảnh: fission -d0=cụm, -d1=main.
4. Fix badge lifecycle (idempotent init + DisplayListener retry/teardown, hết degrade vĩnh viễn) + toggle (mặc định BẬT).
Gates: 5 suite xanh (core 520, app 387), review APPROVED, scan CLEAN.

## Chờ verify ON-CAR (chuyến anh em)
- Badge lifecycle: cast bật/tắt → badge tự lên/gỡ (chưa test được off-car, emulator không có display 1).
- VietMap/Waze/WazeMod announcement có bắt được + tách đúng package không.
- Camera VietMap: icon + value (place_holder) có ra không.
- Vòng xuyến GMaps: đủ maneuver code + bitmap để map HUD.

## Off-car sau khi có data (Track L + mở rộng)
- Phân tích maneuver code + arrow bitmap → **bảng map icon HUD** (đặc biệt vòng xuyến: ra-nhánh mấy, hướng).
- Phân tích nav_access theo package → nguồn nào cho nav gì (GMaps vs VietMap vs Waze vs WazeMod).
- Đối chiếu interpolation cự ly (nav_log) vs on-screen ground-truth.
- VietMap alert: map hash icon → loại (camera/cấm dừng/đỗ/...); value từ place_holder.
- Waze/WazeMod: nếu announcement không đủ → cân nhắc ESP32/peer cho HudLink (đã có tag fix sẵn).

## Ghi chú bảo mật (owner xem sau, KHÔNG gấp)
- File cũ `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` có mật khẩu factory DiLink (đã ẩn ở đây) + IP firmware — đã commit từ trước; nên scrub + rotate nếu repo public.
