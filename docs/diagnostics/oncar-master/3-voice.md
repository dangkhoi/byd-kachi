# On-car master · MẢNG 3 — VOICE: RE + bài test QUYẾT ĐỊNH (một-lần-đủ)

> **Trạng thái**: Superseded — thay bởi `docs/diagnostics/offcar-2026-09-26/voice-tail-fuzzy-phonetic.md` · **Ngày**: 2026-09-19 · **Mục đích**: mọi việc voice CHƯA verify on-car sau 1.79, mỗi việc kèm **MỌI option** + **probe sẵn** để nếu option A chết trên xe thì B/C thử NGAY cùng buổi. · **Điều kiện tiên quyết**: xe phải ở **1.79 (vc80)** (xe đang 1.76 ⇒ OTA trước, không có 1.79 thì 3/4 việc dưới đây vô nghĩa). · **Client**: `/tmp/adb_raw.py <car-ip> 5555 '<shell>'` ([ĐO] có mặt; chưa lưu repo).
> **Xây trên**: `oncar-piper-crash-binding-2026-09-18.md` · `oncar-voice-music-vietmap-2026-09-18.md` · `oncar-voice-cases-findings-2026-09-18.md` · `oncar-voice-number-and-voicekey-bind-2026-09-18.md` · `oncar-runbook-hey-kachi.md`. **KHÔNG RE lại** những gì 4 doc đó đã chốt.

---

## 0. NĂM PHÁT HIỆN CỦA PHIÊN RE NÀY — ĐỔI KẾ HOẠCH TEST

Đọc mục này trước. Ba trong năm phát hiện **đảo ngược** thứ tự việc, một phát hiện **huỷ** một phase của runbook cũ.

| # | Phát hiện | Mức | Hệ quả cho buổi test |
|---|---|---|---|
| **F1** | **VietMap CÓ cửa nhận CHỮ** — `MainActivity` khai `ACTION_SEND` `text/*` + `*/*`, **và** `VIEW https://www.google.com/maps/*`, `VIEW https://goo.gl/maps/*`. Plugin xử lý có thật trong dex: `receive_sharing_intent/events-text`, `uni_links`, `app_links`. | **[ĐO source]** | `VoiceLaunch.OpenOnly` cho VietMap (`VoiceAppTargets.kt`) là kết luận **HẸP HƠN sự thật** — phép đo 09-14 chỉ thử `geo:`. ⇒ có **4 option mới**, mỗi cái 1 lệnh `am start`. Có thể **bỏ hẳn geocode** nếu một trong số đó ăn. |
| **F2** | **Kiki KHÔNG geocode bằng `android.location.Geocoder`, KHÔNG dùng Nominatim.** Directive của server mang **cả hai** đường: `serverGeocoding` (LocationRequest: pathKey/pathUrl) **và** `clientGeocoding` (GeocodingConfig: `enable`/`query`/`timeout`) — server bật/tắt từ xa. Đường client = **scrape web Google Maps** (UA **desktop Mac Chrome 120**, `Accept-Language: vi`, cookie-warmup 3 lượt × 2000ms, bóc `window.APP_INITIALIZATION_STATE`). | **[ĐO source]** | Đường geocode CHÍNH của Kiki là **server của họ** — Kachi không có. |
| **F3** | **Đường scrape của Kiki HIỆN ĐANG HỎNG.** Chạy lại đúng recipe (kể cả cookie-warmup): HTTP 200, có marker, nhưng `a[3]` chỉ dài **2** (`search`) / **6** (`place`) — Kiki cần index `6` ⇒ `PATH_FAIL`; **0 toạ độ trong trang**. | **[ĐO off-car 2026-09-19]** | **ĐỪNG** đặt cược buổi test vào "copy geocode của Kiki". Nó là option cuối, không phải option đầu. |
| **F4** | **Nominatim CHẠY**: 3/3 truy vấn trả lat/lng đúng, HTTP 200, **0.84–0.97 s**, với đúng UA `ClusterNav-Updater` mà `HttpConn` gửi. ⚠ chất lượng: *"Landmark 81"* → trả **"PetroVietnam Landmark"** (POI SAI). | **[ĐO off-car 2026-09-19]** | Đường mạng của 1.79 **đúng về cơ chế**; ẩn số duy nhất còn lại là **mạng XE có ra được không**. Nhưng `limit=1` sẽ chọn sai với tên mơ hồ ⇒ nợ riêng. |
| **F5** ⚠⚠ | **"Hey Kachi" KHÔNG THỂ nghe trên xe — chứng minh được mà không cần model.** `VoiceWakeListener.kt:286` đọc `/proc/loadavg` lấy **load1 THÔ** (`substringBefore(' ')`), **KHÔNG chia số lõi**. `VoiceWakeController.kt:25` dựng `VoiceLoadGuard()` **mặc định, KHÔNG có núm pref**: `suspendAbove=6.0` · `resumeBelow=4.0` · `resumeStableReads=3` (`VoiceLoadGuard.kt:59-61`). Load xe [ĐO 18-09] = **10.71 / 14.45 / 17.02**, lúc "nhẹ" vẫn **10 / 11 / 14.78**. | **[ĐO source] + [ĐO xe]** | `onFrame` trả `Frame.SUSPENDED` **vĩnh viễn** (`VoiceWakeController.kt:48`) ⇒ nhả mic, 0 inference. **PHASE 2 của `oncar-runbook-hey-kachi.md` sẽ đo ra 0/20 bất kể model tốt đến đâu** — một âm tính GIẢ. ⇒ Phase 2 **HUỶ**; thay bằng probe load 5 phút (§4). |

**Cite**: F1 `apk-ref/VIETMAP+LIVE+-+MiMi+AI_3.4.0_APKPure.xapk` → `AndroidManifest` (aapt2 xmltree) `line=204` (MainActivity, `exported=true`, `launchMode=2` singleTask) · `line=213/258/265/272` (SEND) · `line=289` (goo.gl/maps) · `line=300` (www.google.com/maps) · `line=312` (vietmaplive://) · `line=279` (autoVerify `dlink.vietmap.live`); plugin từ `classes*.dex` strings. F2 `jadx-kiki/sources/p526z/C8882n.java:67` (`GotechDirection(… clientGeocoding=… serverGeocoding=…)`) · `p526z/C8878j.java:13,16,57` (`GeocodingConfig(enable,query,timeout…)`) · `p526z/C8879k.java:51` (`LocationRequest(pathKey,pathUrl…)`) · `p250m/C5013h0.java:1143` (cổng: **GMaps được MIỄN** geocode) · `p331q7/AbstractC6124e.java:52,101-114` (URL + warmup) · `p331q7/C6120a.java:38` (UA desktop) · `p331q7/C6129j.java:31,43,67` (api `search|place`, marker, `getJSONArray(9)` = toạ độ). F5 `app/.../voice/VoiceWakeListener.kt:286` · `core/.../voice/VoiceWakeController.kt:25,48` · `core/.../voice/VoiceLoadGuard.kt:59-61`.

**Đường đã ĐÓNG (đừng mất thời gian)**: `jadx-amap`/`jadx-amap2` (Gaode, TQ) và `jadx-tmap` (SK Telecom, Hàn) — [ĐO source] **0** deep-link dẫn đường dùng được cho VN (grep `androidamap://`/`amapuri://` = rỗng; tmap chỉ ra khoá pref nội bộ). Không có geocode VN nào ở hai cây đó.

---

## 1. VIỆC 1 — VietMap dẫn đường thật (geocode / giao điểm đến)

**Trạng thái vào buổi**: 1.79 đã sửa parser (tách *"bằng/qua/dùng &lt;app&gt;"* + khớp-mờ `vietma`) ⇒ câu **mới route được tới VietMap lần đầu tiên**. Bước SAU đó (*giao điểm đến*) **chưa từng chạy trên xe**.
**Đường hiện tại**: `VoiceTargetDispatch.runNav` → `target.needsCoords` true (VietMap) → `VoiceGeocoder.resolveBounded` (on-device → Nominatim, hạn cứng 7 s) → `vietmaplive://companion/navigation?lat&lng&poiName`.

### 1.1 Bảng option — MỌI đường, xếp theo thứ tự thử trong buổi

| # | Option | Cơ chế | Mức | Probe (chạy được NGAY) | Chi phí nếu hỏng |
|---|---|---|---|---|---|
| **A** | **Đường 1.79 nguyên trạng**: Nominatim → `vietmaplive://…lat&lng` | đã ship | [ĐO off-car F4] · **on-car [CHƯA BIẾT]** | P1.1 + P1.2 | 0 (chỉ là lượt nói) |
| **A′** | Nhánh **on-device** của cùng đường đó (`Geocoder.isPresent()`) | `VoiceGeocoder.onDevice` | **[CHƯA BIẾT]** — xe không GMS ⇒ [SUY] `isPresent()` false hoặc treo | P1.2 (log phân biệt được A vs A′) | 0 |
| **B1** | **`ACTION_SEND` + `text/*` + `setPackage(vn.vietmap.live)`** — gửi CHỮ, **bỏ hẳn geocode** | F1 | **[ĐO source]** có filter + có plugin `events-text` | P1.3 | 1 lệnh |
| **B2** | **`VIEW https://www.google.com/maps/search/?api=1&query=<text>` + `setPackage(vn.vietmap.live)`** | F1 (`line=300`) | **[ĐO source]** có filter | P1.4 | 1 lệnh |
| **B3** | `VIEW https://www.google.com/maps/dir/?api=1&destination=<text>` + setPackage VietMap | F1 — cùng filter `/maps` | [SUY] | P1.5 | 1 lệnh |
| **B4** | `vietmaplive://` với tham số CHỮ (`searchText` · `keyword` · `query` · `startNavigation`) | các chuỗi này **có** trong `libapp.so`, nhưng presence ≠ param | **[SUY]** | P1.6 (4 biến thể) | 4 lệnh |
| **C** | **GMaps passthrough** — `google.navigation:q=<text>` tới GMaps | đã chạy | **[ĐO xe]** | — (đường nền) | 0 |
| **D** | Copy geocode Kiki (scrape GMaps web) | F2 | **[ĐO off-car: HỎNG]** (F3) | P1.7 (chỉ nếu A+B đều chết) | cao, đừng |
| **E** | Geocode qua API VietMap chính chủ | — | **[CHƯA BIẾT]** — [ĐO source] **0** hit `api.vietmap`/`maps.vietmap` trong cây Kiki | ngoài buổi (cần key) | — |

### 1.2 Probe — copy-paste

```bash
CAR=<car-ip>; A="python3 /tmp/adb_raw.py $CAR 5555"

# P1.0 — tiền đề: đúng bản 1.79 + VietMap có mặt
$A 'dumpsys package com.byd.launcher | grep -E "versionName|versionCode" | head -2'
$A 'pm list packages | grep -E "vietmap|apps.maps|youtube"'

# P1.1 — nói câu thật, xem intent CUỐI bắn đi đâu (đây là dòng quyết định, như 09-18)
$A 'logcat -c'
#   → owner nói: "dẫn đường tới chợ Bến Thành bằng VietMap"
$A 'logcat -d | grep -E "ActivityTaskManager: START|vietmaplive|google.navigation" | tail -5'
#   MONG ĐỢI A đạt: dat=vietmaplive://companion/navigation?lat=10.77…&lng=106.69… pkg=vn.vietmap.live

# P1.2 — geocode đi đường NÀO (phân biệt A vs A′ vs hỏng). Log Log.i/w, KHÔNG cần verbose.
$A 'logcat -d | grep -E "KachiVoiceGeo|quá hạn|máy chủ tra cứu|Geocoder của máy" | tail -10'
#   "Geocoder của máy không trả lời được" ⇒ A′ chết, đã lùi Nominatim
#   "máy chủ tra cứu trả <mã>"            ⇒ mạng xe RA được nhưng bị chặn/lỗi
#   "geocode quá hạn 7000ms"              ⇒ treo (rất có thể A′ treo — xem §1.3 kết luận)
#   không dòng nào + có vietmaplive lat/lng ⇒ geocode THÀNH CÔNG

# P1.3 — OPTION B1: gửi CHỮ cho VietMap (bỏ geocode)
$A 'am start -a android.intent.action.SEND -t "text/plain" -p vn.vietmap.live --es android.intent.extra.TEXT "chợ Bến Thành"'
$A 'sleep 4; dumpsys activity activities | grep -E "ResumedActivity|topResumedActivity" | head -2'

# P1.4 — OPTION B2: URL Google Maps search ép vào VietMap
$A 'am start -a android.intent.action.VIEW -p vn.vietmap.live -d "https://www.google.com/maps/search/?api=1&query=ch%E1%BB%A3%20B%E1%BA%BFn%20Th%C3%A0nh"'

# P1.5 — OPTION B3: URL dir (điểm đến)
$A 'am start -a android.intent.action.VIEW -p vn.vietmap.live -d "https://www.google.com/maps/dir/?api=1&destination=ch%E1%BB%A3%20B%E1%BA%BFn%20Th%C3%A0nh"'

# P1.6 — OPTION B4: bốn tham số CHỮ trên scheme riêng
for K in searchText keyword query q; do
  $A "am start -a android.intent.action.VIEW -p vn.vietmap.live -d 'vietmaplive://companion/navigation?$K=cho%20ben%20thanh&startNavigation=true'"
  $A 'sleep 3; dumpsys activity activities | grep ResumedActivity | head -1'
done

# P1.7 — CHỈ khi A và B1–B4 đều chết: mạng xe có ra internet không (tách "mạng" khỏi "geocode")
$A 'ping -c 2 -W 3 1.1.1.1'; $A 'ping -c 2 -W 3 nominatim.openstreetmap.org'
$A 'toybox wget -q -O - "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=ben+thanh" 2>&1 | head -c 200'
#   ⚠ [CHƯA BIẾT] toybox Android 10 có TLS không — ping thất bại/wget rỗng thì dùng P1.2 làm nguồn sự thật.
```

⚠ **Bẫy nháy** (đã trả giá ở T-BRIDGE): qua `adb shell`, chuỗi có khoảng trắng/`&` **phải** bọc nháy hai lớp, không thì shell cắt lệnh và phép đo nói về một URI khác.

### 1.3 Outcome → kết luận

| Quan sát | Kết luận | Việc tiếp |
|---|---|---|
| P1.1 ra `vietmaplive://…lat&lng` **và** VietMap dẫn thật | **A ĐẠT** — VietMap xong, đóng nợ 1.74→1.79 | Đổi `coordEvidence` VietMap `AWAITING_CAR`→`MEASURED` |
| P1.1 ra `vietmaplive://` nhưng VietMap mở mà **không dẫn** | geocode OK, **deep-link không khởi tuyến** ([SUY] thiếu cờ start / cần `poiName` thật thay `name`) | thử B4 `startNavigation=true`; thử `poiName` = tên thật |
| P1.2 có *"quá hạn 7000ms"* | **A′ TREO** (đúng ca KDoc `resolveBounded` lường) | bỏ hẳn nhánh on-device khi `!isPresent`-tương-đương; hoặc hạ hạn |
| P1.2 có *"máy chủ tra cứu trả …"* / P1.7 ping fail | **mạng xe không ra được** ⇒ mọi option HTTP chết (gồm cả music) | ⇒ **B1/B2/B3 là đường duy nhất** (0 mạng) |
| P1.3 mở VietMap **có** ô tìm/điểm đến = "chợ Bến Thành" | **B1 ĐẠT — thắng lớn**: bỏ geocode, bỏ ngoại lệ mạng | đổi VietMap `OpenOnly` → `VoiceLaunch.Action(ACTION_SEND …)`; gỡ `needsCoords` |
| P1.4/P1.5 VietMap nhận URL và hiện điểm đến | **B2/B3 ĐẠT** — cũng bỏ được geocode | thêm `VoiceLaunch.Uri` cho VietMap |
| P1.3–P1.6 đều mở app trơn | F1 chỉ là filter không có màn xử lý ⇒ giữ A | và **giữ nguyên** câu nói-thật `navNoPlace` |
| Tất cả chết | giữ **C** (GMaps passthrough) làm đường mặc định cho VietMap | nói rõ với owner: *"cái nào ra cái đó"* phải nhượng bộ ở VietMap |

---

## 2. VIỆC 2 — Nhạc: YouTube / YT Music phát thật

**Trạng thái**: 1.79 đổi `BROWSER_UA` → desktop (`VoiceYoutubeResolver.kt`) + nới `MAX_CHARS` 600 K → 1.8 M. **Chưa lượt nào chạy trên xe.**
**[ĐO off-car 2026-09-19]** — chạy lại đúng lớp 1.79:

| UA | HTTP | ký tự trang | số khớp `"videoId"` | khớp ĐẦU ở | id |
|---|---|---|---|---|---|
| desktop, *"Diễm Xưa Khánh Ly"* | 200 | 1 394 445 | **312** | **761 379** | `R0mpl0Az4Q8` |
| desktop, *"Sơn Tùng MTP"* | 200 | 1 539 629 | **332** | **779 839** | `abPmZCZZrFA` |
| mobile (bản 1.75) | 200 | 555 771 | **0** | — | — |

⇒ chẩn đoán 09-18 **đúng**, và trần mới **cần thiết**: khớp đầu ở 761–780 K, tức trần cũ 600 K vẫn trả `null` **kể cả sau khi UA đã đúng**. Ẩn số còn lại = **mạng xe** + **bản app trên xe**.

### 2.1 Bảng option

| # | Option | Mức | Probe | Ghi chú |
|---|---|---|---|---|
| **A** | YT Music `https://music.youtube.com/watch?v=<id>` (auto-play) | [ĐO source] có `MusicServiceDeepLinkActivity`; **on-car [CHƯA BIẾT]** | P2.2 + P2.4 | đường tốt nhất — app nhạc thật |
| **B** | YouTube `https://www.youtube.com/watch?v=<id>` | [SUY] | P2.3 + P2.4 | mở watch = tự phát |
| **C** | `MEDIA_PLAY_FROM_SEARCH` + `extra.focus=audio/*` | **[ĐO xe 09-18]** chạy nhưng **KHÔNG phát** ở app YouTube | — | đường LÙI hiện tại |
| **D** | `vnd.youtube:<video_id>` — scheme cổ điển, auto-play | **[CHƯA BIẾT]** (không có APK YouTube để RE) | P2.5 | option dự phòng nếu A/B chết |
| **E** | resolver reach từ mạng xe | **[CHƯA BIẾT]** | P2.1 | quyết định A/B có khả thi |
| **F** | Đổi đích mặc định *"phát bài X"* → **YT Music** thay YouTube | quyết định thiết kế | P2.2 | nếu A đạt mà B không |

### 2.2 Probe

```bash
# P2.1 — resolver có ra được không (ĐỌC LOG của chính resolver; Log.w không cần verbose)
$A 'logcat -c'
#   → owner nói: "phát bài Diễm Xưa trên YouTube Music"
$A 'logcat -d | grep -E "YtResolve|ActivityTaskManager: START" | tail -8'
#   "HTTP <mã>" / "giải video_id lỗi" / "quá hạn 7000ms" ⇒ mạng xe chặn ⇒ A,B chết, còn C
#   START dat=https://music.youtube.com/watch?v=…        ⇒ resolver ĐẠT

# P2.2/P2.3 — đường watch có ai nhận không (KHÔNG cần resolver, tách 2 biến số)
$A 'cmd package resolve-activity -a android.intent.action.VIEW -d "https://music.youtube.com/watch?v=R0mpl0Az4Q8"'
$A 'cmd package resolve-activity -a android.intent.action.VIEW -d "https://www.youtube.com/watch?v=R0mpl0Az4Q8"'

# P2.4 — bắn thật bằng id đã biết (bỏ qua resolver) → có TIẾNG không?
$A 'am start -a android.intent.action.VIEW -p com.google.android.apps.youtube.music -d "https://music.youtube.com/watch?v=R0mpl0Az4Q8"'
$A 'sleep 6; dumpsys media_session | grep -E "state=PlaybackState|package=" | head -6'
#   state=PlaybackState {state=3  ⇒ ĐANG PHÁT (3 = PLAYING)

# P2.5 — OPTION D
$A 'am start -a android.intent.action.VIEW -d "vnd.youtube:R0mpl0Az4Q8"'

# P2.6 — bản app trên xe (để biết vì sao khác máy ảo 2019)
$A 'dumpsys package com.google.android.apps.youtube.music | grep versionName | head -1'
$A 'dumpsys package com.google.android.youtube | grep versionName | head -1'
```

### 2.3 Outcome → kết luận

| Quan sát | Kết luận |
|---|---|
| P2.1 ra `START … music.youtube.com/watch?v=` + P2.4 `state=3` | **A ĐẠT** — "phát luôn" xong; `watch` của YT Music → `MEASURED` |
| P2.4 `state=3` nhưng P2.1 báo `YtResolve HTTP/quá hạn` | **deep-link đúng, MẠNG XE là nút thắt** ⇒ hoặc nới hạn 7 s, hoặc chuyển giải id sang lúc rảnh, hoặc chấp nhận C |
| P2.2 `resolve-activity` không ra activity nào | bản YT Music trên xe không nhận watch-URL ⇒ thử B rồi D |
| P2.5 mở và phát | **D ĐẠT** — thêm `vnd.youtube:` làm `watch` của YouTube (rẻ, 1 dòng dữ liệu) |
| Cả A,B,D chết | giữ **C** + đổi mặc định sang **YT Music** (nó tự phát với `MEDIA_PLAY_FROM_SEARCH`, [ĐO] máy ảo dừng ở nút Play ⇒ vẫn phải xác nhận trên xe) |

---

## 3. VIỆC 3 — Key-binding + `:tts`: **BÀI TEST QUYẾT ĐỊNH** (đường nóng #0 chưa từng chạy)

**Cái đang kiểm**: 1.79 đưa Piper ra tiến trình `:tts` (`PiperTtsService`, `android:process=":tts"`, manifest `line≈203-207`) để SIGSEGV native **không giết launcher**. **Toàn bộ giá trị của 1.79 nằm ở giả thuyết này, và nó chưa bao giờ được chạy.**

**Hợp đồng phải đúng** (`RemotePiperSpeaker.kt` KDoc — bảng "ai đóng sổ"): `:tts` chết ⇒ `onServiceDisconnected`/`onBindingDied` → `onRemoteGone()` → **mở MỌI chỗ đang chờ** `onDone` + `unbindService` + `bound=false` ⇒ câu sau **bind lại**.

### 3.1 Bốn giả thuyết phải bác/xác nhận trong một lượt

| ID | Giả thuyết | Đo bằng |
|---|---|---|
| H1 | Kill `:tts` giữa lúc đọc ⇒ **launcher SỐNG** (pid `com.byd.launcher` KHÔNG đổi) | so pid trước/sau |
| H2 | a11y `NavAccessibilityService` vẫn **Bound** ⇒ **phím còn sống** | `dumpsys accessibility` |
| H3 | `onDone` **vẫn nổ** ⇒ overlay đóng bình thường, KHÔNG treo tới `SPEAK_SAFETY` | logcat + overlay |
| H4 | Câu **kế tiếp** dựng lại `:tts` (pid MỚI) và đọc được | pid mới + có tiếng |

### 3.2 Probe — kill đúng lúc (đây là phần khó, đã giải)

```bash
# P3.0 — chốt mốc TRƯỚC
$A 'ps -A | grep -E "com.byd.launcher" '        # ghi pid :app và (chưa có) :tts
$A 'dumpsys accessibility | grep -A6 NavAccessibilityService | grep -E "Bound|Binding|ENABLED"'

# P3.1 — đặt bẫy: vòng lặp giết :tts NGAY khi nó xuất hiện ⇒ luôn trúng giữa lúc synth
#   (chạy TRƯỚC khi nói; :tts chỉ sinh ra ở câu đọc đầu tiên — RemotePiperSpeaker bind LƯỜI)
$A 'sh -c "for i in \$(seq 1 300); do p=\$(ps -A | grep \"com.byd.launcher:tts\" | grep -v grep | awk \"{print \\\$2}\"); if [ -n \"\$p\" ]; then echo TRAP_KILL \$p; kill -9 \$p; break; fi; sleep 0.1; done"'
#   → owner bấm phím thoại + nói một câu có reply DÀI (vd "mở tất cả kính") để synth kéo dài

# P3.2 — chốt mốc SAU (chạy ngay, <10 s)
$A 'ps -A | grep -E "com.byd.launcher"'
$A 'dumpsys accessibility | grep -A6 NavAccessibilityService | grep -E "Bound|Binding"'
$A 'logcat -d | grep -E "KachiVoiceTtsLink|tiến trình :tts chết|mở [0-9]+ chỗ đang chờ|SIGSEGV|WIN DEATH|Fatal signal" | tail -15'

# P3.3 — H4: nói câu THỨ HAI ngay ⇒ có tiếng + :tts pid MỚI?
$A 'ps -A | grep "com.byd.launcher:tts"'

# P3.4 — phím sống qua N lượt (bài test độ bền, N≥10, kể cả không kill)
#   sau mỗi lượt:
$A 'dumpsys accessibility | grep -c "Bound"'    # phải KHÔNG giảm
$A 'logcat -d | grep -cE "Fatal signal 11"'     # phải = 0 trong tiến trình :app

# P3.5 — đối chứng ÂM (chỉ nếu H1 thất bại): kill :app rồi xem có khác gì ca 1.76 không
```

### 3.3 Outcome → kết luận

| Quan sát | Kết luận | Việc tiếp |
|---|---|---|
| pid `:app` **không đổi** + a11y **Bound** + log *"mở N chỗ đang chờ"* + câu sau đọc được | **#0 ĐẠT** — 1.79 sửa đúng gốc. Đóng nợ crash-binding | ghi `MEASURED`; bỏ đề xuất (b) "chuyển mặc định sang System TTS" |
| pid `:app` **ĐỔI** | **#0 THẤT BẠI** — cô lập tiến trình không đủ | ⇒ đường (b) ngay: `AndroidTtsSpeaker` làm mặc định, Piper opt-in (`VoiceSpeakerSelector` đã có sẵn đường) |
| `:app` sống nhưng a11y **"Binding"** kẹt | crash không còn là gốc, **rebind vẫn kẹt** dưới tải | ⇒ làm **watchdog binding** (force-stop+re-enable, đường đã proven recover 09-18) |
| `:app` sống nhưng overlay treo tới ~10 s | `onDone` **không** nổ ⇒ có lỗ trong bảng "ai đóng sổ" | trace đúng nhánh (`sendSpeak` vs `onRemoteGone`) |
| `:tts` **không bao giờ xuất hiện** trong P3.1 | Piper không được chọn (`available()` false — thiếu gói giọng) hoặc đang dùng `ClipSpeaker` | kiểm `SherpaTtsSpeaker.voiceFilesPresent` + `voice_tts_voice` |
| P3.4 có `Fatal signal 11` trong `:tts` **tự nhiên** (không kill) | **xác nhận SIGSEGV thật vẫn còn**, nhưng đã bị cô lập | đây là bằng chứng mạnh nhất cho #0 — ghi lại tombstone |

⚠ **Kill `-9` mô phỏng SIGSEGV ở mức "tiến trình biến mất"** — đủ cho H1–H4 (nền tảng gọi cùng callback). Nó **không** mô phỏng heap corruption lan trước khi chết. Nếu P3.4 bắt được crash TỰ NHIÊN thì ưu tiên bằng chứng đó.

---

## 4. VIỆC 4 — "Hey Kachi": probe QUYẾT ĐỊNH 5 phút, KHÔNG cần model

### 4.1 Vì sao Phase 2 của runbook cũ bị huỷ

F5 (§0). Chuỗi suy luận **đóng kín, đọc được từ source**:

```
VoiceWakeListener.kt:286   load1 = /proc/loadavg[0]            (THÔ, không chia lõi)
VoiceWakeController.kt:25  loadGuard = VoiceLoadGuard()        (mặc định, KHÔNG pref)
VoiceLoadGuard.kt:59-61    suspendAbove=6.0 resumeBelow=4.0 resumeStableReads=3
VoiceWakeController.kt:48  if (!loadGuard.allow(load1)) return Frame.SUSPENDED
[ĐO xe 18-09]              load1 = 10.71 … 17.02   (nhẹ nhất 10)
⇒ allow() = false VĨNH VIỄN ⇒ nhả mic, 0 KWS ⇒ nói "Hey Kachi" 20 lần = 0 hit
```

⇒ Đo độ chính xác wake trên xe hôm nay **không đo được model**, chỉ đo lại cái ngưỡng. Thay bằng **probe load**: câu hỏi duy nhất đáng đo là *"load1 có bao giờ xuống dưới 4.0 ba giây liên tiếp trong cách owner dùng xe không?"*

### 4.2 Bảng option

| # | Option | Mức | Probe / việc |
|---|---|---|---|
| **A** | **Chuẩn hoá load theo số lõi** (`load1 / availableProcessors`) — 8 lõi ⇒ 14/8 = 1.75 < 4.0 ⇒ guard KHÔNG treo | [SUY] sửa 1 dòng; KDoc `VoiceLoadGuard` đã lường (*"có thể chia số lõi nếu cần chuẩn hoá"*) | P4.1 cho biết ngưỡng nào đúng |
| **B** | **Nới `suspendAbove`** + thêm **núm pref** (hôm nay hardcode) | [SUY] | P4.1 |
| **C** | gigaspeech EN KWS cho "Kachi" | **[CHƯA BIẾT]** — model chưa host | **Phase 0 off-car** (runbook) — KHÔNG phải việc on-car |
| **D** | KWS **tiếng Việt** | **[CHƯA BIẾT]** | tra ngoài buổi |
| **E** | Đổi cụm gọi (`hey_kachi`/`ok_kachi`/`hi_kachi` — `VoiceWakePhrase.PRESETS`) | cả 3 preset đều chứa "kachi" = **đúng phần khó** ⇒ đổi preset **không** cứu được vấn đề âm vị | Phase 0 |
| **F** | Chỉ bật wake khi **đỗ/nhẹ tải** (giữ guard, nói rõ giới hạn) | quyết định owner | P4.1 |
| **G** | **Bỏ wake**, phím vô-lăng là đường gọi chính | quyết định owner | phụ thuộc Việc 3 |

### 4.3 Probe (5 phút, làm được ngay, không cần model)

```bash
# P4.1 — load1 trong 4 trạng thái dùng thật, 60 mẫu/trạng thái
for S in "idle" "vietmap" "gmaps-in-slot" "gmaps+vietmap"; do
  echo "== $S =="; $A 'sh -c "for i in $(seq 1 60); do cat /proc/loadavg | cut -d\" \" -f1; sleep 1; done"'
done
$A 'cat /proc/cpuinfo | grep -c processor'      # số lõi (kỳ vọng 8)
#   ĐẾM: có chuỗi ≥3 mẫu LIÊN TIẾP < 4.0 không? có mẫu nào > 6.0 không?

# P4.2 — giá CPU của lớp mic+gating (RMS-only, model chưa có ⇒ đúng cái đo được hôm nay)
#   Cài đặt › Hệ thống › "Hey Kachi" = BẬT
$A 'logcat -d | grep -iE "VoiceWakeKws|chỉ-RMS|thiếu model KWS|VoiceWake" | tail -10'
$A 'top -b -n 1 | grep -E "byd.launcher" | head -3'    # so với baseline lúc TẮT
$A 'dumpsys media.audio_flinger | grep -i "input\|record" | head -5'   # mic có bị giữ không
```

### 4.4 Outcome → kết luận

| Quan sát | Kết luận |
|---|---|
| **0** chuỗi 3 mẫu < 4.0 ở mọi trạng thái (dự kiến) | **Guard sai chuẩn, không phải xe sai** ⇒ làm **A** (chia lõi) trước khi tốn công host model. Không có A thì C/D/E vô nghĩa |
| load1 < 4.0 lúc `idle` nhưng > 6.0 khi GMaps-in-slot | wake **chỉ chạy được lúc xe rảnh** ⇒ **A** + nói rõ giới hạn (**F**) |
| Bật wake ⇒ %CPU launcher tăng đáng kể **dù** đang SUSPENDED | có lỗ: đang treo mà vẫn đốt CPU ⇒ trace vòng đọc load/mic |
| Bật wake ⇒ %CPU ~không đổi + log *"chỉ-RMS"* | lớp gating rẻ đúng thiết kế ⇒ rủi ro #2 của runbook **hạ cấp**, còn lại đúng rủi ro #1 (model) |

⇒ **Cổng nghiệm thu Phase 3 của runbook giữ nguyên, nhưng chỉ chạy SAU khi (A hoặc B) đã ship và model đã qua Phase 0.** Buổi này chỉ lấy dữ liệu load + CPU baseline.

---

## 5. Thứ tự chạy trong buổi (time-box) — và vì sao thứ tự này

| Khe | Việc | Phút | Vì sao ở đây |
|---|---|---|---|
| 1 | P1.0 tiền đề + P3.0 mốc | 5 | sai bản ⇒ mọi thứ sau vô nghĩa |
| 2 | **Việc 3** (`:tts` kill) | 20 | **chặn trên hết**: phím chết thì không nói được câu nào để đo Việc 1–2 |
| 3 | **Việc 1** A → B1 → B2 → B3 → B4 | 25 | option B chỉ 1 lệnh/cái, thử hết trong cùng khe |
| 4 | **Việc 2** P2.1–P2.6 | 20 | dùng chung kết luận "mạng xe" với Việc 1 |
| 5 | **Việc 4** P4.1–P4.2 | 10 | thuần đọc số, không phụ thuộc gì |
| 6 | P3.4 độ bền phím ≥10 lượt | rải suốt buổi | mỗi lượt nói của khe 3–4 tính là 1 lượt |

**Hai biến số dùng chung, đo MỘT lần** (đừng đo lại ở từng việc):
1. **Mạng xe có ra internet không** (P1.7/P2.1) — chặn Nominatim *và* YouTube resolver cùng lúc.
2. **Load xe** (P4.1) — giải thích Piper chậm (finding B), a11y rớt bind, và wake bị treo.

---

## 6. Ma trận quyết định gộp

| Nếu | thì Việc 1 | Việc 2 | Việc 3 | Việc 4 |
|---|---|---|---|---|
| Mạng xe **ra được** + A đạt + resolver đạt | giữ Nominatim, ghi `MEASURED` | giữ watch-URL | — | — |
| Mạng xe **KHÔNG ra** | ⇒ **B1/B2/B3 bắt buộc** (0 mạng) | ⇒ chỉ còn **C** (`MEDIA_PLAY_FROM_SEARCH`) | — | — |
| `:app` chết khi kill `:tts` | (chưa đo được gì) | (chưa đo được gì) | ⇒ **System TTS mặc định** | — |
| load1 **luôn** > 6.0 | giải thích Piper chậm/overlay sớm | — | giải thích rớt bind | ⇒ **chia lõi** rồi mới host model |

---

## 7. Nợ / [CHƯA BIẾT] còn lại sau buổi này

1. **Chất lượng Nominatim** — [ĐO] *"Landmark 81"* → "PetroVietnam Landmark" (SAI POI). `limit=1` + `countrycodes=vn` chọn hit đầu, không chọn hit ĐÚNG. Nợ riêng: `limit=5` + chấm điểm theo khoảng cách tới vị trí xe. **Không** đo được trong buổi (cần nhiều câu).
2. **`Geocoder.isPresent()` trên DiLink** — không có probe trực tiếp; chỉ suy được từ log `KachiVoiceGeo` (P1.2).
3. **Tham số CHỮ của `vietmaplive://`** — [SUY] từ chuỗi trong `libapp.so`; P1.6 là phép thử, không phải RE. RE thật cần đọc Dart snapshot (chuỗi xếp theo hash, không đọc được bằng grep lân cận).
4. **Model KWS** — Phase 0 off-car chưa chạy (chưa có model + chưa tokenize). Vẫn là việc **ngoài xe**.
5. **`vnd.youtube:`** — không có APK YouTube trong `apk-ref/` để RE; chỉ probe được (P2.5).
6. **Geocode API VietMap chính chủ** — [ĐO source] 0 hit trong cây Kiki; cần key nhà cung cấp.
7. `/tmp/adb_raw.py` **chưa lưu repo** — cân nhắc `scripts/vehicle/kachi/adb_raw.py` (đã nêu 2 doc trước, vẫn chưa làm).
8. Núm chỉnh trên xe (không cần build): `voice_vad_min_silence_ms` (thử 600) · `voice_endpoint_silence_ms` · `voice_endpoint_min_speech_ms` · `voice_endpoint_floor_cap` · `voice_mic_source` — whitelist ở `TestBridgeCommand.kt:224-236`. **Không** có núm nào cho `VoiceLoadGuard` (F5).

---

## 8. Nguồn RE đã dùng (cite)

| Chủ đề | Nguồn | Dòng |
|---|---|---|
| VietMap deep-link toạ độ | `jadx-kiki/sources/p449vq/AbstractC8122l.java` · `p478x/C8454u.java` | 600 · 226 |
| Kiki server-vs-client geocode | `jadx-kiki/sources/p526z/C8882n.java` · `C8878j.java` · `C8879k.java` | 67 · 13,16,57 · 51 |
| GMaps được MIỄN geocode | `jadx-kiki/sources/p250m/C5013h0.java` | 1143 |
| Scrape GMaps: URL + warmup | `jadx-kiki/sources/p331q7/AbstractC6124e.java` | 52 · 101-114 |
| Scrape GMaps: UA desktop | `jadx-kiki/sources/p331q7/C6120a.java` | 38 |
| Scrape GMaps: parse toạ độ | `jadx-kiki/sources/p331q7/C6129j.java` | 31,43,67 |
| VietMap cửa CHỮ (SEND / google.com/maps) | `apk-ref/VIETMAP…3.4.0…xapk` → AndroidManifest (aapt2 xmltree) | 204,213,258,265,272,279,289,300,312 |
| VietMap plugin deep-link/share | cùng APK, `classes*.dex` strings | `receive_sharing_intent/events-text` · `uni_links` · `app_links` |
| Wake load guard | `core/.../voice/VoiceLoadGuard.kt` · `VoiceWakeController.kt` · `app/.../voice/VoiceWakeListener.kt` | 59-61 · 25,48 · 286 |
| `:tts` hợp đồng onDone | `app/.../voice/RemotePiperSpeaker.kt` (KDoc bảng) · `app/src/main/AndroidManifest.xml` | — · ~203-207 |
| Đường đóng (amap/tmap) | `jadx-amap`, `jadx-amap2`, `jadx-tmap` | grep deep-link = rỗng |
