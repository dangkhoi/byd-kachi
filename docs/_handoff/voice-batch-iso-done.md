# Handoff — VOICE BATCH ISOLATED (4 fix từ log xe 2026-09-18)

> **Trạng thái**: DONE off-car, **CHƯA commit** (main-agent gom) · **Ngày**: 2026-09-18 · **Phạm vi**: 4 fix isolated
> ①`MIN_SILENCE_MS` ②lưới-an-toàn-đọc ③overlay-immersive ④UA-resolver. **KHÔNG đụng** parser core
> (`VoiceIntentParser` / `VoiceTailClause` / `VoiceSynonyms`) — dành stage sau.
> Nguồn finding: `docs/diagnostics/oncar-voice-cases-findings-2026-09-18.md` (§A §B §C) +
> `oncar-voice-music-vietmap-2026-09-18.md` (§BUG B).

## 1. Kết quả kiểm — [ĐO] tự chạy lại trên nguồn CUỐI

| Module | Lệnh | Kết quả |
|---|---|---|
| `:core` | `:core:test` | **2261 test · 0 đỏ** (230 lớp) — trước batch 2255 ⇒ **+6** đúng bằng số bài mới |
| `:app` (debug) | `:app:testDebugUnitTest --rerun` (KHÔNG filter) | **1148 test · 0 đỏ** (140 lớp) — trước 1147 ⇒ **+1** |
| `:app` (release) | `:app:compileReleaseKotlin --rerun` | **BUILD SUCCESSFUL**, 0 error, 0 warning ở tệp đã sửa |

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew \
  :core:test :app:testDebugUnitTest \
  --tests '*Voice*' --tests '*Vad*' --tests '*Youtube*' --tests '*Music*' --continue
# rồi chạy rộng hơn cho chắc (tôi có sửa tệp mà contract test quét-nguồn toàn cây đụng tới):
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew :app:testDebugUnitTest --rerun --continue
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew :app:compileReleaseKotlin --rerun
```

⚠ **Hai bẫy lệnh đã gặp, phiên sau khỏi mất thời gian**:
1. `:app:testReleaseUnitTest` **KHÔNG tồn tại** — app chỉ có unit-test cho `debug` + `vehicleTest`
   (`compileDebugUnitTestSources` / `compileVehicleTestUnitTestSources`). Dùng **`:app:testDebugUnitTest`**.
2. `--tests` **không lọc được `:core:test`** trong cấu hình này (nó chạy trọn 2261 bài dù đã truyền filter), và task
   test bị báo `UP-TO-DATE` khi chỉ đổi filter ⇒ muốn chạy thật phải thêm **`--rerun`**. Số đếm chỉ in ra khi CÓ bài
   đỏ; run xanh thì im ⇒ đọc `*/build/test-results/**/*.xml` mới biết đã chạy bao nhiêu.

### Thử phá — 3 guard mới đều ĐỎ ĐÚNG CHỖ
Sao lưu **theo tệp** (`cp` → `/tmp/kachi-mut-backup`, KHÔNG dùng `git checkout` trên cây chưa commit), mutate, chạy,
phục hồi, `shasum -a 256` khớp trước/sau ⇒ **byte-identical**.

| Phép phá | Bài đỏ |
|---|---|
| `MIN_SILENCE_MS` 600 → 150 | `VoiceVadTrimTest` ×2 (`tham so mac dinh…` · `nguong im chiu duoc quang ngung…`) |
| UA máy tính → UA di động | `VoiceMusicPlayTest.UA cua resolver la UA may tinh…` |
| `estimateMs(batch, …)` → `scheduleClose(SPEAK_SAFETY_MS)` | `VoiceFastNaturalWiringContractTest.hoi thoai cho DOC XONG…` |

## 2. Tệp đã đổi

### ① Ngắt câu quá gấp (§A)
- `core/.../voice/VoiceVadTrim.kt` — **`MIN_SILENCE_MS` 150 → 600**; KDoc viết lại (bằng chứng xe + bằng chứng
  nguồn upstream); **sửa lý do sai** của `MAX_MIN_SILENCE_MS` (xem §3); ghi chú p50/p90 dịch +450 ms.
- `app/.../voice/VoiceVad.kt` — 1 câu KDoc: hai con số endpoint đo với `min_silence = 0,15 s`, nay +450 ms.
- `core/src/test/.../VoiceVadTrimTest.kt` — ghim 600 (kèm lý do `[ĐO 2026-09-18]`); đổi tên bài
  `…bang dung bo da chot tren host` → `…bang dung bo dang chay` (min_silence **không còn** là điểm lưới host);
  bài MỚI `nguong im chiu duoc quang ngung lay hoi giua cau` (sàn 500 ms); thêm mốc 600 ms vào vòng đổi đơn vị;
  sửa chú thích so-với-RMS (hết là *"nhỏ hơn HẲN"*, 600 vs 800).
- **KHÔNG phải sửa** `app/src/test/.../VoiceVadWiringContractTest.kt`: nó canh *dây nối* (`K_VOICE_VAD_MIN_SILENCE_MS,
  VoiceVadTrim.MIN_SILENCE_MS`), không ghim giá trị ⇒ vẫn xanh.

### ② Piper đọc chưa hết câu đã mất overlay (§B)
- `core/.../voice/VoiceSpeakBudget.kt` — **MỚI**, thuần (66 dòng): `MS_PER_CHAR = 200L` · `HEAD_ROOM_MS = 5_000L` ·
  `FLOOR_MS = 15_000L` · `estimateMs(lines, floorMs = FLOOR_MS)`.
- `app/.../voice/VoiceSession.kt` — `scheduleClose(SPEAK_SAFETY_MS)` → `scheduleClose(VoiceSpeakBudget.estimateMs(batch,
  SPEAK_SAFETY_MS))`; `SPEAK_SAFETY_MS` **giữ tên**, đổi vai thành **SÀN** và đọc từ `:core`
  (`= VoiceSpeakBudget.FLOOR_MS`, 10 s → 15 s). `onReplyDone` / `LINGER_MS` / `NETWORK_WAIT_MS` **không đụng**.
- `core/src/test/.../VoiceSpeakBudgetTest.kt` — MỚI, 5 bài (sàn · câu ghép dài · đơn điệu tăng · biên ≥ 2× thời
  lượng đọc thật · sàn > 10 s của 1.78).
- `app/src/test/.../VoiceFastNaturalWiringContractTest.kt` — +2 khẳng định: hẹn đóng **phải** qua `estimateMs`, và
  `scheduleClose(SPEAK_SAFETY_MS)` trơn **không được** quay lại.

### ③ Overlay kéo taskbar hệ thống lên (§C)
- `app/.../voice/VoiceOverlay.kt` — thêm `goImmersive(view)` dùng **đúng bộ cờ của màn chính**
  (`KachiHomeWiring.goImmersiveWindow`: `LAYOUT_STABLE|LAYOUT_HIDE_NAVIGATION|LAYOUT_FULLSCREEN|HIDE_NAVIGATION|
  FULLSCREEN|IMMERSIVE_STICKY`), áp ở **3 mốc**: lúc dựng (`apply`), `onAttachedToWindow`, và mỗi lượt **lấy lại**
  tiêu điểm (`onWindowFocusChanged(true)`) + `setOnSystemUiVisibilityChangeListener` (chỉ áp lại khi cờ `FULLSCREEN`
  chưa bật ⇒ **không vòng lặp**).
- GIỮ nguyên: `isFocusableInTouchMode = true`, `dispatchKeyEvent` (Back), `dimAmount = 0f`, không `FLAG_DIM_BEHIND`
  ⇒ **không làm tối màn**. Window flags (`FLAG_LAYOUT_NO_LIMITS`) không đụng.
- API: `systemUiVisibility` (deprecated ở SDK 30+ nhưng xe là **API 29**) — cùng lý do đã ghi ở `goImmersiveWindow`
  và `ClusterNavActivity`; có `@Suppress("DEPRECATION")`.

### ④ Nhạc YouTube không phát (§BUG B)
- `app/.../voice/VoiceYoutubeResolver.kt` — `BROWSER_UA` → **desktop**
  `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36`
  (KHÔNG có `Mobile`), kèm bảng [ĐO] mobile→0 videoId / desktop→225 trong KDoc.
- `app/src/test/.../VoiceMusicPlayTest.kt` — bài MỚI ghim UA: không `Mobile`, không `Android`, phải có `Windows NT`.
  Quét qua `SourceRoots.codeOf` (**đã bỏ chú thích**) nên bảng so sánh trong KDoc không làm bài báo sai.
- Không tệp nào assert UA trước đó ⇒ đây là guard **thêm mới**, không nới guard cũ.

## 3. Giả định đã KIỂM — và một lý do cũ hoá ra SAI

**Giả định của brief (fix ①) là ĐÚNG, nhưng nó chỉ đúng nhờ một chi tiết ở upstream, không phải hiển nhiên.**
Brief yêu cầu xác nhận qua `VoiceVad.kt`; đọc tầng Kotlin **không đủ** — nó chỉ đọc `seg.start` + `seg.samples.size`,
còn *"hai con số đó có gồm đuôi hangover không"* thì nằm trong native. Đã tra thẳng nguồn
**sherpa-onnx v1.13.8** (đúng version `app/build.gradle.kts:260` đang pin):

- `csrc/silero-vad-model.cc` — `IsSpeech()` trả **`true` suốt** quãng hangover
  (`if (current_sample_ - temp_end_ < min_silence_samples_) return true; // continue speaking`) ⇒ nếu chỉ có chỗ này
  thì đuôi im **sẽ** vào đoạn, tức giả định SAI.
- `csrc/voice-activity-detector.cc` — nhưng đoạn được đẩy vào hàng đợi thì **trừ hẳn** quãng đó ra:
  `int32_t end = buffer_.Tail() - model_->MinSilenceDurationSamples();` ⇒ `segment.start + samples.size` = **điểm hết
  tiếng thật**, độc lập với núm.

⇒ `headTrimSamples` (chế độ `head`, margin 0) cắt đúng ở điểm hết tiếng ⇒ nâng 150 → 600 **chỉ** tăng độ trễ chốt câu
(+450 ms), **không** nạp thêm im vào bộ giải mã. Đây cũng là lời giải thích số học cho §8: `head` 21/25 vs `window`
18/25 — `window` cắt ở *lúc chốt* nên chỉ nó mới dính `min_silence`.

⚠ **Hệ quả: một lý do đã ghi trong repo là SAI và đã sửa.** KDoc cũ của `MAX_MIN_SILENCE_MS` viết *"trên 800 ms thì
đuôi im lặng nạp vào mô hình lại dài bằng bản RMS cũ, tức núm mất tác dụng"* — theo nguồn trên thì đuôi ấy **chưa bao
giờ** vào bộ giải mã. **Dải 80–800 giữ nguyên** (không nới guard), chỉ viết lại lý do: trần trên là để **độ trễ** chốt
câu không ăn quá sâu vào trần cứng 8 s.

## 4. Sai lệch có chủ ý so với brief (3 điểm, đều nhỏ)

1. **`estimateSpeakMs` đặt ở `:core` (`VoiceSpeakBudget`), không phải hàm private trong `VoiceSession`.** Hai lý do:
   `VoiceSession.kt` lúc nhận việc đã **487/500 dòng** (thêm hàm + KDoc là vượt trần); và dự án có luật *"mặc định phải
   LÀ hằng của `:core`, không phải số chép tay"* (chính `VoiceVadWiringContractTest` đang canh luật đó cho 3 núm VAD).
   Đặt ở `:core` cũng làm số học kiểm được off-car — cùng khuôn `VoiceVadTrim`.
2. **`SPEAK_SAFETY_MS` giữ tên nhưng giá trị 10 s → 15 s và đọc từ `:core`.** Brief cho phép *"giữ làm sàn/tên nếu
   tiện"*. Nếu để 10 s làm sàn thì câu ngắn vẫn có thể bị cắt dưới tải (10 s là chính con số đã hỏng trên xe).
3. **KHÔNG đặt trần trên cho ước lượng.** Brief không yêu cầu, và một trần trên = *"câu đủ dài lại bị cắt giữa
   chừng"*, tức đúng lỗi hôm nay quay lại ở một ngưỡng khác. Cái giới hạn thật là `onReplyDone` (đọc xong ⇒ rút về
   `LINGER_MS` 2,5 s ngay); ca lưới dài chỉ xảy ra khi `onDone` **không bao giờ về** (engine chết), và lúc đó tấm chữ
   vẫn tắt được bằng chạm-ra-ngoài / Back. Bài `them chu khong bao gio lam luoi ngan lai` khoá tính chất này.

## 5. Nợ / cảnh báo cho stage sau

- ⚠ **`VoiceSession.kt` nay 498/500 dòng.** Stage parser (D1/D2/E-VietMap) **phải tách tệp trước** khi thêm bất cứ gì
  vào đó. Tất cả tệp đã sửa đều dưới trần: `VoiceVadTrim` 170 · `VoiceSpeakBudget` 66 · `VoiceVad` 248 ·
  `VoiceOverlay` 249 · `VoiceYoutubeResolver` 80.
- 📄 **Doc/backlog CHƯA cập nhật** (chủ ý — main-agent gom): `docs/specs/kachi-voice-rearchitecture-and-remaining.html`
  còn nhắc `SPEAK_SAFETY_MS` ở vai *"lưới an toàn cố định"*; hai doc diagnostics 2026-09-18 còn ghi các mục A/B/C ở
  trạng thái *"CHƯA implement"*; `PROJECT-BACKLOG.md` + `project-context.md` chưa có mục cho batch này.
- 🚗 **CHƯA đo gì trên xe** — cả 4 fix đều off-car:
  - ① độ trễ +450 ms trên giọng thật, và 600 ms có đủ cho quãng ngừng dài nhất của owner không. Owner thử **không cần
    build**: `prefs_set voice_vad_min_silence_ms` (dải 80–800).
  - ② một câu trả lời dài dưới load 14 có đọc trọn không (và `onDone` của Piper có thật sự về không).
  - ③ taskbar còn bị kéo lên không — chỉ xe mới trả lời được (máy ảo không có taskbar BYD).
  - ④ desktop UA trên **mạng của xe** (đo 225 videoId là ở mạng máy tôi); YouTube đổi markup lúc nào cũng được ⇒ đường
    lùi `MEDIA_PLAY_FROM_SEARCH` vẫn là lớp phòng thứ hai.
- ➡ **Còn nguyên trong 2 doc, ngoài phạm vi stage này**: D1 (câu HỎI bắn LỆNH GHI — an toàn, ưu tiên cao) · D2/D3
  (synonym *"đang bao nhiêu"* / áp suất / ghế mát + reply *"tính năng đã bỏ"*) · BUG A (parser tách *"bằng &lt;app&gt;"*
  cho nav + khớp-mờ tên app).
