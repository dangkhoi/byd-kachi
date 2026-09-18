# Voice on-car 2026-09-18: VietMap không dẫn + YouTube/YT Music không phát — GỐC + hướng fix

> **Trạng thái**: Đã trace (off-car từ log xe), **CHƯA fix** (owner: "lấy log làm off-car, ko implement"). · **Ngày**: 2026-09-18 · **Nguồn**: log xe THẬT (<car-ip>, 1.76 vc77) — các lần owner thử voice lúc 13:44, lấy qua client ADB thô pure-python. Full log: `/sdcard/Download/kachi-all-1347.log` (4644 dòng) trên xe.

## Bối cảnh đo
- ⚠ **Verbose voice logging TẮT** (release, `Prefs.navVerboseLog` mặc định off) ⇒ KHÔNG có log parse/ASR/YtResolve nội bộ. Chẩn đoán dựa vào **intent DISPATCH thật** (`ActivityTaskManager START` — system log) + phép thử resolver off-car. Vẫn đủ để chốt gốc.

## BUG A — VietMap "chưa dẫn được" (GMaps thì ngon) = **PARSE, KHÔNG phải geocode**
[ĐO log] Câu owner nói (đại ý *"dẫn đường tới chợ Bến Thành bằng VietMap"*) → Kachi bắn:
```
START act=VIEW dat=google.navigation:q=chợ bến thành bằng vietma  pkg=com.google.android.apps.maps  (uid 10138)
```
Ba lỗi lộ ra từ MỘT dòng:
1. **App selector "bằng vietmap" KHÔNG được tách** ⇒ rơi về app **mặc định (GMaps)**, không phải VietMap.
2. **Chuỗi "bằng vietma" bị để NGUYÊN trong địa chỉ** (`q=chợ bến thành bằng vietma`) ⇒ GMaps geocode một địa chỉ rác.
3. **ASR nghe "vietma"** (rớt 'p') ⇒ kể cả nếu parser bắt "bằng <app>" thì còn phải khớp-mờ tên app.

⇒ Giả thuyết geocode (Nominatim/Geocoder) ở doc `oncar-voice-number-and-voicekey-bind` §? là **SAI** cho ca này — deep-link VietMap (`vietmaplive://…?lat=&lng=`) **chưa bao giờ được bắn**; câu chưa từng route tới VietMap. (Geocode vẫn là vấn đề TIỀM ẨN khi route đúng VietMap — xe không GMS, `NetworkLocation` ném lỗi ⇒ phụ thuộc Nominatim HTTP; nhưng đó là bước SAU.)

**Hướng fix (chưa làm):** ở parser nav (`VoiceIntentParser`/`VoiceTailClause`) — tách mệnh đề đuôi *"bằng/qua/dùng &lt;app&gt;"* làm **app selector** cho nav (bỏ khỏi địa chỉ + route đúng app), giống cách đã làm cho *"vào ô N"* / *"bằng &lt;app&gt;"* của nhạc. + khớp-mờ tên app (`vietmap`/`việt map`/`vietmáp`/`vietma`) qua `VoicePhoneticMatch`. Đây là **nợ đã biết** (backlog: *app-hint "dùng google map" chưa bắt*; *«mở &lt;tên&gt; trên youtube» rớt tên*).

## BUG B — YouTube/YT Music "không mở nhạc, tìm cũng ko ra" = **resolver UA DI ĐỘNG → 0 videoId → lùi**
[ĐO log] Câu owner (đại ý *"phát bài … trên YouTube"*) → Kachi bắn (2 lần):
```
START act=android.media.action.MEDIA_PLAY_FROM_SEARCH  pkg=com.google.android.youtube  (uid 10138)
```
⇒ Đây là **đường LÙI** (`MEDIA_PLAY_FROM_SEARCH`), KHÔNG phải watch auto-play (`VIEW watch?v=`). Tức `VoiceYoutubeResolver` **trả null** (không bóc được video_id) ⇒ `runMediaQuery` lùi về `deliver`. YouTube app mở `Shell_MediaSearchActivity` nhưng **không tự phát** (YouTube ≠ YT Music; MEDIA_PLAY_FROM_SEARCH ở app YouTube chỉ mở tìm kiếm).

**Vì sao resolver trả null — [ĐO off-car] mô phỏng đúng `VoiceYoutubeResolver` (follow redirect + cookie CONSENT):**

| UA gửi | Kết quả `results?search_query=` | videoId regex bắt |
|---|---|---|
| **Mobile** `…Android 10… Mobile Safari` (UA HIỆN TẠI của resolver) | HTTP 302 → trang consent/mobile, body rỗng/JS-only | **0** |
| Desktop `…Windows NT 10.0… Safari` (không "Mobile") | trang kết quả có `ytInitialData` | **225** |

⇒ **GỐC: `VoiceYoutubeResolver.BROWSER_UA` là UA DI ĐỘNG** (`Mozilla/5.0 (Linux; Android 10) … Mobile Safari`). YouTube trả trang di động (JS-only) / redirect consent ⇒ regex `"videoId":"…"` không có gì để bắt ⇒ null ⇒ auto-play không bao giờ chạy.

**Hướng fix (chưa làm):** đổi `BROWSER_UA` sang **desktop** (`Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36`, KHÔNG có "Mobile"). [ĐO off-car] desktop UA → 225 videoId kể cả với `CONSENT=YES+1` hiện có. Sau đó watch-URL auto-play sẽ chạy (YT Music [ĐO] có `MusicServiceDeepLinkActivity` xử lý `music.youtube.com/watch?v=`). Cập nhật `YoutubeSearchParseTest`/`VoiceMusicPlayTest` nếu cần. ⚠ vẫn mong manh theo markup YouTube — nhưng desktop UA là điều kiện cần.
Phụ: câu route tới **YouTube** (com.google.android.youtube) chứ không YT Music — tuỳ chữ owner nói; YT Music watch-URL auto-play tốt hơn.

## Cũng đo phiên này (đã ghi doc riêng `oncar-voice-number-and-voicekey-bind-2026-09-18.md` §4)
- **CPU/lag**: 8 lõi bão hoà — GMaps-in-slot 61% + BYD cdr 39% + surfaceflinger 34% + VietMap 18%; Kachi 2%.
- **Phím gán**: `NavAccessibilityService` rớt bind dưới tải → khôi phục live bằng toggle a11y; fix nới `GRANT_TIMEOUT_MS` 9s→20s (đã ship 1.78).
- **adb wireless** sống suốt phiên ⇒ bug2 không tái hiện lúc này.

## Tồn / bước sau (khi owner cho implement)
1. Fix parser nav app-selector "bằng &lt;app&gt;" + khớp-mờ tên app (BUG A).
2. Đổi resolver UA sang desktop (BUG B) + cân nhắc route YT Music.
3. [SUY] Geocode VietMap khi route đúng: xe không GMS ⇒ chỉ còn Nominatim — cần đo Nominatim có trả kết quả trên mạng xe không (bước sau khi BUG A xong).
4. Client `/tmp/adb_raw.py` chưa lưu repo — cân nhắc `scripts/vehicle/kachi/adb_raw.py` cho phiên sau.
