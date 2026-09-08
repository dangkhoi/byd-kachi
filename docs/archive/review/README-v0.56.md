# v0.56 — nghiệm thu trên xe (chiều 22/07)

APK: `apk/ClusterNav-0.56-release.apk` · 157 test pass · xe đang chạy **0.47** (đọc từ máy, đừng đoán — §9)

## Trước khi bắt đầu

1. Cài đè.
2. **TẮT MÁY XE HẲN MỘT LẦN** rồi mở lại. Bắt buộc: cờ freeform chỉ đọc lúc boot.
3. Mở **🩺 Chẩn đoán → 🔬 Máy dò dẫn đường → bấm nút tím để TẮT máy dò.**
   Lý do: máy dò soi 4 app của đường chiếu và duyệt cây node mỗi 2s. Bật nó lúc đo chiếu thì
   nếu giật sẽ không phân biệt được do bản vá hay do máy dò.

## Phần A — đo CHIẾU (máy dò TẮT)

| # | Việc | Đạt khi |
|---|---|---|
| A1 | Nhìn màn hình giữa | Vietmap **không** còn bị thu nhỏ/lệch. Nếu còn → 🩺 → **GỠ CHẾ ĐỘ CỬA SỔ NỔI** → tắt máy xe → mở lại → xem lại |
| A2 | Chiếu Vietmap lên cụm | lên bình thường, khung đúng |
| A3 | Đổi qua lại Vietmap ↔ CarPlay **10 lần** | không app nào kẹt dạng cửa sổ nổi trên màn giữa; không có hai app cùng trên cụm |
| A4 | Chiếu một app không lên được | app **dừng an toàn**: cụm trả về đồng hồ, app cũ ở màn giữa. KHÔNG giằng co qua lại |
| A5 | Bấm TẮT chiếu | cụm về đồng hồ, app về màn giữa dạng **toàn màn** |
| A6 | Sau A3, rút/cắm lại **CarPlay** | CP cắm lên được, không phải khởi động lại xe |
| A7 | Chỉnh kích thước khi app **đang** chiếu | ăn ngay |

**Nếu app báo "đầu xe đang ở trạng thái hỏng… cần TẮT MÁY XE"**: ĐỪNG tấp lề vội.
Chụp màn hình + bấm CHỤP CHẨN ĐOÁN trước. v0.55 đã siết 4 nguồn báo giả nhưng vẫn cần đo thật.

**Nếu cụm có biểu hiện lạ**: bấm **TẮT**, đừng bấm CHIẾU lại nhiều lần.

## Phần B — săn CarPlay / AA (bật máy dò sau khi xong phần A)

1. 🩺 → Máy dò → bấm **▶ BẮT ĐẦU DÒ**. Kiểm dòng "quyền": phải thấy `ĐÃ CẤP CẢ HAI ✓`.
2. Cắm **CarPlay**, dẫn đường bằng Google Maps trên iPhone. Chạy càng lâu càng tốt.
3. Rút, cắm **Android Auto**, dẫn đường.
4. Mở Vietmap và Waze, mỗi cái dẫn đường thật một đoạn.
5. Về: 🩺 → Máy dò → **XEM TRƯỚC** (file có nguyên văn thông báo — đọc rồi mới gửi) → **CHIA SẺ**.

Trong file, ba chữ đáng tìm: `[MÀN HÌNH]` · `[BROADCAST]` · `[MEDIA]`.

## Lấy APK Android Auto (cần adb, xe cùng WiFi/hotspot)

```
scratchpad/pull-projection.sh
```
Chạy khi **KHÔNG cắm CP/AA** — cắm vào là đầu xe tắt WiFi, adb không vào được (§11).
APK là file tĩnh nên không cần app đang chạy. Có APK rồi thì mổ offline được cả đêm.

## Trạng thái lúc ĐANG cắm — app tự chụp, không cần WiFi

Không lấy bằng adb được. `NavProbeSnap` chụp qua dadb **loopback**:
- **tự động** khi nghe broadcast cắm/rút CP-AA,
- hoặc bấm tay nút **📸 CHỤP NGAY trạng thái CP/AA** trong màn Máy dò.

Chụp: `dumpsys media_session` · service AA · service CarPlay · activity đang chạy · stack · getprop.
Trong file tìm chữ `[CHỤP CP/AA]`.

## Đã sửa trong v0.50→v0.55

Lỗi 1 (Vietmap scale màn chính) · lỗi 2 (CP/AA mất kết nối) · lỗi 3 (hai app đè nhau) — xem
`2026-07-22-3loi-hien-truong.md`. Cộng 5 lỗi P1 + 13 lỗi P2/P3 do senior review tìm ra.

## Nợ, cố ý chưa làm

- `ClusterCast.kt` ~1160 LOC, quá ngưỡng 500 của repo (§4.1). Tách file vài tiếng trước khi lên xe
  là phá cái đang chạy để cho gọn — để đợt riêng.
- `keepSessionApps` vẫn rỗng → `am force-stop` vẫn được phép bắn lên CarPlay/AA. Là 1 trong 4 giả
  thuyết của lỗi 2, chưa chốt được cái nào đúng nên chưa sửa mò.
- Chưa commit: HEAD ở v0.35, cây làm việc v0.55. Cần quét dữ liệu nhạy cảm trước (§6 global).
