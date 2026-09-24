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
**ĐÃ CHỐT 2026-09-24:** xe **KHÔNG có Google Play Services** ⇒ `android.location.Geocoder` on-device (dựa Play Services) KHÔNG chạy trên xe (`isPresent()`=false → trả null). Owner chốt **(B)**: cho HTTP free ra ngoài (không API key/tiền), cấm API key phức tạp.

⇒ **Đường cuối (đã đúng trong code, không cần sửa thêm):**
1. VietMap `launch=OpenOnly` + `coord=vietmaplive://companion/navigation?lat&lng&poiName` ⇒ `needsCoords=true`.
2. `VoiceTargetDispatch.runNav` geocode text→lat/lng qua `VoiceGeocoder.resolve` = `onDevice ?: online`. Trên xe: `onDevice` (Geocoder.isPresent=false vì không GMS) trả null NHANH → `online` = **Nominatim/OSM** (free, không key, chỉ HTTP ra ngoài — đúng B).
3. Bắn `vietmaplive://…lat&lng` → VietMap **BẮT ĐẦU DẪN THẬT** [ĐO emulator 2026-09-23/24: tuyến polyline + chevron + panel có nút X huỷ + thanh tiến độ; số liệu rỗng `--` vì GPS mock đặt LỆCH tuyến, không phải lỗi cửa].
4. Geocode hỏng → mở VietMap trơn + "chưa tra được điểm đến".

Người dùng nói CHỮ; Kachi tra toạ độ qua Nominatim (không key); VietMap nhận toạ độ. KHÔNG "bias toạ độ" trên bề mặt (user không gõ lat/lng). GMaps vẫn nhận text thẳng (`google.navigation:q=`) không cần geocode.

⚠ CÒN đo trên xe: (a) Nominatim geocode địa chỉ VN có đủ tốt không (hẻm nhỏ/địa chỉ mới có thể thiếu); (b) `vietmaplive://…lat&lng` với GPS THẬT (không mock lệch) có sinh số liệu dẫn (ETA/km) đầy đủ không.
