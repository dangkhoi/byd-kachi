# VietMap dẫn đường từ app ngoài — RE chốt (2026-09-23)

> **Trạng thái**: Current · **Kết luận**: VietMap Live nhận điểm đến từ app NGOÀI **chỉ bằng lat/lng**. KHÔNG có cửa nhận địa chỉ CHỮ tự do cho app ngoài. Đừng RE lại hướng "text URL".

## Câu hỏi
Owner: nói "dẫn tới X bằng VietMap" → mở GMaps (sai). Muốn VietMap dẫn **bằng chữ**, "bias toạ độ có vẻ không đúng" (con người không gõ lat/lng). VietMap app CHO gõ chữ + có trợ lý Mimi nghe chữ — vậy sao Kachi không đẩy chữ vào được?

## Đã ĐO (emulator, VietMap Live 3.3.4 đã login + link chia sẻ THẬT của owner)

### 1. Mọi URL text → KHÔNG bóc được địa chỉ
| URL thử | Kết quả |
|---|---|
| `https://www.google.com/maps/search/?api=1&query=<text>` | panel "VM Connect share", chỉ hiện "Google Maps", KHÔNG điểm đến |
| `https://www.google.com/maps/dir/?api=1&destination=<text>` | "Đang lấy thông tin vị trí", không điểm đến |
| `https://www.google.com/maps/place/<text>` | panel share, không điểm đến |
| `vietmaplive://companion/navigation?poiName=<text>` (không lat/lng) | **LỖI 1037 "Không thể bắt đầu dẫn đường"** |
| `vietmaplive://search?q=<text>` · `vietmaplive://{direction,place,SearchRoute,home}?keyword=<text>` | màn bản đồ idle, không search |
| `ACTION_SEND text/plain <text>` → MainActivity | màn idle, không search |

### 2. Link chia sẻ THẬT của VietMap = TOẠ ĐỘ (bằng chứng quyết định)
Owner gửi link chia sẻ do CHÍNH VietMap tạo: `https://vietmap.live/hRJE4PNKzGnAKXic7`
```
curl → HTTP 302 → location: https://vietmap.live/shareLocation/?latitude=10.7719839&longitude=106.7043911
```
⇒ Ngay cả **VietMap chia sẻ điểm đến cũng bằng lat/lng**, KHÔNG phải text. `vietmap.live` là short-link web (Firebase-style), redirect ra `shareLocation?lat&lng`; app chỉ nhận host `dlink.vietmap.live` (dynamic-link) / `vietmaplive://` — payload đều là toạ độ.

### 3. Mimi & ô search = nội bộ / signature-gated
- Trong `libapp.so` (Dart): `/v20/place/Geocode`, `/v20/place/Autocomplete`, `/v20/place/DecodeGoogleFullLink` — geocode/autocomplete chạy **server VietMap**, gọi từ **UI trong app** (ô search + Mimi nghe). `DecodeGoogleFullLink` = decode Google Maps **full share-link** (có place-id/toạ độ), KHÔNG phải `?query=text`.
- **Mimi** (`ai.vietmap.mimi.connect`) drive VietMap qua permission `ai.vietmap.mimi.Launch` **protectionLevel=signature** + `RunningStatusProvider`/`LauncherReceiver` (cùng permission signature). Mimi & VietMap **ký cùng khoá** ⇒ Kachi (khác khoá) **KHÔNG thể** dùng kênh này. `MimiAssistantActivity` cũng `permission=ai.vietmap.mimi.Launch`.

## Kết luận (đóng hướng)
- Con người gõ chữ TRONG VietMap (ô search) / Mimi nghe chữ → VietMap tự geocode nội bộ. **Từ app ngoài KHÔNG có đường đẩy text.**
- Kachi → VietMap **bắt buộc lat/lng** (`vietmaplive://companion/navigation?lat&lng&poiName`). Muốn "dẫn bằng chữ" thì Kachi phải **geocode text→lat/lng TRƯỚC** (Google/VietMap geocode API), rồi đưa toạ độ.
- ⚠ `vietmaplive://companion/navigation?lat&lng` [ĐO vmC] MỞ bản đồ đúng vùng nhưng **chưa thấy tuyến/panel dẫn** trên emulator (có thể do emulator thiếu GPS fix hiện tại) — cần xác nhận có bắt đầu dẫn thật không (xe/emulator có GPS).
- Code giữ đường `coord` (đã revert khỏi google.com/maps text — commit `33229ff`).

## Việc còn (owner quyết)
Muốn "dẫn bằng chữ" cho VietMap thì chỉ còn: **geocode text→toạ độ** (thêm bước mạng, dùng geocode API) rồi bắn `vietmaplive://...lat&lng`. Đây là toạ-độ-ẩn (người dùng vẫn nói chữ, Kachi tự tra toạ độ) — KHÁC "bias toạ độ" ở chỗ người dùng không thấy toạ độ. Owner cân nhắc có làm bước geocode này không, hay để VietMap = OpenOnly (mở app, user tự gõ) và ưu tiên GMaps cho lệnh dẫn-bằng-chữ (GMaps nhận text thẳng qua `google.navigation:q=`).
