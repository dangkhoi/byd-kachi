# Handoff — VOICE ISO (4 fix isolated) — DONE off-car

> **Trạng thái**: ✅ off-car xanh, **CHƯA commit / CHƯA push** (main-agent gom + ship) · **Ngày**: 2026-09-18
> **Phạm vi**: ① `MIN_SILENCE_MS` · ② lưới-an-toàn-đọc · ③ overlay immersive · ④ UA resolver.
> **KHÔNG đụng** `VoiceSpeaker*` / `PiperTtsService` / `RemotePiperSpeaker` (stage `tts-isolate`), **không đụng**
> parser core (`VoiceIntentParser` / `VoiceTailClause` / `VoiceSynonyms` — stage parser).
> Nguồn: `oncar-piper-crash-binding-2026-09-18.md` (#0) · `oncar-voice-cases-findings-2026-09-18.md` (§A §B §C) ·
> `oncar-voice-music-vietmap-2026-09-18.md` (§BUG B).

---

## 0. ⚠ ĐỌC TRƯỚC — hai việc mà một bản ghi handoff trước đó đã coi là xong

Cây làm việc **đã có sẵn** một lượt chạy trước của stage này (`docs/_handoff/voice-batch-iso-done.md`). Tôi không
tin bản báo cáo đó mà đo lại từng fix, và tìm ra **hai lỗ**:

1. **Fix ② chỉ vá MỘT trong HAI đường đọc.** `execute` đã đúng, nhưng `clarifyGaveUp` (câu *"bỏ cuộc"* sau 3 lượt
   hỏi lại) vẫn **hẹn đóng 2,5 s TRƯỚC khi đọc** ⇒ đúng bệnh §B, chỉ khác đường. Brief có nêu tên
   `VoiceSessionTurns.kt`; lượt trước không sửa tệp đó. **Đã vá.**
2. **Fix ④ (UA máy tính) một mình KHÔNG làm *"phát luôn"* chạy.** Đây là phát hiện quan trọng nhất của lượt này —
   xem §2.

Thêm: fix ③ ship **không có một bài canh nào**. Đã thêm (5 bài) + thử phá.

---

## 1. ✅ Bốn fix — trạng thái CUỐI (đã tự đo lại, không tin report)

| # | Việc | Trạng thái | Bằng chứng |
|---|---|---|---|
| ① | `VoiceVadTrim.MIN_SILENCE_MS` 150 → **600** | ✅ (lượt trước làm, tôi xác nhận) | `VoiceVadTrimTest` ghim 600 + sàn ≥ 500 · dải 80–800 **không nới** |
| ② | Lưới an toàn đọc theo **độ dài câu** | ✅ `execute` (lượt trước) + ✅ **`clarifyGaveUp` (lượt này)** | `VoiceFastNaturalWiringContractTest` ×2 khẳng định mới · MUT-3 đỏ |
| ③ | Overlay **không kéo taskbar** | ✅ (lượt trước làm) + ✅ **guard (lượt này)** | `VoiceOverlayImmersiveContractTest` 5 bài · MUT-1/MUT-2 đỏ |
| ④ | Resolver UA **máy tính** | ✅ UA (lượt trước) + ✅ **trần đọc + quét dòng chảy (lượt này)** | [ĐO] tôi tự chạy curl 2 UA · MUT-4/5/6 đỏ |

### ① — giả định đã kiểm, KHÔNG phải tin
Brief yêu cầu xác nhận head-trim (margin 0) cắt ở **hết đoạn tiếng** nên nâng núm chỉ tốn độ trễ. [ĐO] `VoiceVad.kt`
tầng Kotlin **không đủ** để kết luận (nó chỉ đọc `seg.start` + `seg.samples.size`); điều cần biết nằm trong native
và lượt trước đã tra đúng nguồn **sherpa-onnx v1.13.8** (pin ở `app/build.gradle.kts:260`):
`voice-activity-detector.cc` — `int32_t end = buffer_.Tail() - model_->MinSilenceDurationSamples();` ⇒ đuôi hangover
bị **trừ khỏi đoạn** trước khi vào hàng đợi ⇒ `headTrimSamples` cắt ở điểm hết tiếng thật, độc lập với núm. Tôi đọc
lại đoạn KDoc + `VoiceVad.headTrimSamples` và **đồng ý**: nâng 150 → 600 chỉ cộng ~450 ms độ trễ chốt câu
(p50 ~660 → ~1 110 ms), rất xa trần cứng `MAX_LISTEN_MS` = 8 s.
`VoiceVadWiringContractTest` **không cần sửa** — [ĐO] nó canh *dây nối* (`K_VOICE_VAD_MIN_SILENCE_MS,
VoiceVadTrim.MIN_SILENCE_MS`), không ghim giá trị 150.

### ② — đường thứ hai đã vá (việc MỚI của lượt này)
`app/…/voice/VoiceSessionTurns.kt` · `clarifyGaveUp`:

```kotlin
// TRƯỚC (1.78): tấm chữ đi sau 2,5 s trong khi loa còn đang đọc câu ~40 ký tự
scheduleClose(VoiceSession.LINGER_MS)
speakLines(listOf(line))

// SAU: cùng lưới của §B + mốc ĐỌC XONG mới rút về LINGER_MS (khuôn nhánh `flushed` của execute)
scheduleClose(VoiceSpeakBudget.estimateMs(listOf(line), VoiceSession.SPEAK_SAFETY_MS))
speakLines(listOf(line)) { post { if (!stale(my)) scheduleClose(VoiceSession.LINGER_MS) } }
```

`clarifyGaveUp(intents)` → `clarifyGaveUp(intents, my)` (chỗ gọi duy nhất ở `execute`, **0 dòng tăng** ở
`VoiceSession.kt`). `my` có mặt để một mốc đọc-xong **về muộn** không rút tấm chữ của **phiên khác** — cùng chốt
thế hệ mà dòng 327 của `execute` đã dùng; thiếu nó là dựng lại đúng họ lỗi `generation` mà dự án đã vá 3 lần.
⚠ Mức bằng chứng: §B là **[ĐO]** cho đường `execute`; với đường này quãng *"~40 ký tự ≈ 2,6 s"* là **[SUY]** (suy
từ nhịp đọc [ĐO host] ~65 ms/ký tự) — cơ chế thì giống hệt, và đã ghi rõ nhãn đó trong KDoc.

### ③ — guard đã thêm (việc MỚI của lượt này)
Mã của lượt trước **đúng** (6 cờ khớp `KachiHomeWiring.goImmersiveWindow`, áp ở 3 mốc, cổng chặn vòng lặp, giữ
`isFocusableInTouchMode` + Back, `dimAmount = 0f`, không `FLAG_DIM_BEHIND`, API 29 `systemUiVisibility`). Nhưng nó
**không có bài canh nào** — tức lỗi này mọc lại được mà không ai biết. Thêm
`app/src/test/…/voice/VoiceOverlayImmersiveContractTest.kt` (117 dòng, **5 bài**), quét nguồn (không dựng được
`WindowManager` trong JVM thuần, và máy ảo không có taskbar BYD):

1. **so TẬP CỜ** của overlay với của màn chính (không so chuỗi — thứ tự `or` đổi được mà nghĩa không đổi), + ghim
   **đúng 6 cờ** (nếu một ngày CẢ HAI bề mặt cùng rụng một cờ thì phép so bằng vẫn xanh) + đòi `IMMERSIVE_STICKY`.
2. áp lại ở `onAttachedToWindow` **và** `onWindowFocusChanged(true)` **và** listener — kèm **cổng chặn vòng lặp**
   (`vis and FULLSCREEN == 0`), vì gỡ cổng đó là áp lại vô hạn trên luồng vẽ.
3. **chiều ngược** — cách *"chữa"* sai rất dễ nghĩ ra: `FLAG_NOT_FOCUSABLE` cũng ẩn được thanh hệ thống nhưng giết
   đường thoát bằng Back ⇒ bài canh đòi tiêu điểm + Back **còn nguyên** và cấm token đó.
4. **không làm tối màn** (brief): cấm `FLAG_DIM_BEHIND`, đòi `lp.dimAmount = 0f`.
5. trần 500 dòng cho 2 tệp lượt này chạm.

---

## 2. ⚠⚠ FIX ④ — UA ĐÚNG MÀ VẪN TRẢ `null`: một giả định thứ hai chưa ai đo

**Tôi tự đo lại cả bảng của brief, và nó đúng — nhưng chưa đủ.**

[ĐO của tôi, curl mô phỏng đúng lớp resolver: theo redirect + `Accept: text/html` + `Accept-Language` + `Cookie:
CONSENT=YES+1`]

| UA | HTTP | thân | `"videoId"` bắt được |
|---|---|---|---|
| di động (bản 1.75) | 200 | 559 878 B (trang mobile JS-only) | **0** |
| máy tính (bản vá) | 200 | 1 279 268 B | **225** |

Tới đây báo cáo cũ dừng lại. Nhưng resolver **không đọc cả trang**: `MAX_CHARS = 600_000` kèm lý do viết trong
KDoc *"videoId bài đầu nằm sớm trong `ytInitialData` — không cần đọc cả trang"*. [ĐO] **giả định đó SAI**:

| truy vấn | cỡ trang (ký tự) | `"videoId"` đầu ở ký tự | trong trần 600 K? |
|---|---|---|---|
| `diem xua` | 1 288 025 | **763 319** | ❌ |
| `son tung mtp` | 1 525 895 | **787 261** | ❌ |
| `hay trao cho anh` | 1 283 353 | **769 370** | ❌ |
| `noi nay co anh` | 1 296 627 | **765 226** | ❌ |

⇒ **4/4 nằm ngoài trần** ⇒ `firstVideoId` trả `null` **kể cả sau khi UA đã đúng** ⇒ vẫn lùi về
`MEDIA_PLAY_FROM_SEARCH` ⇒ *"phát luôn"* vẫn không chạy trên xe. Đổi UA một mình là **một nửa bản vá**.

### Đã làm — và vì sao chọn cách này
Trần phải vượt ~790 K, nhưng một `StringBuilder` 1,3 M ký tự là ~2,6 MB (chưa kể lượt nhân đôi khi giãn) — **đúng
loại áp lực RAM đang là gốc của `SEGV_MAPERR` trong Piper** (doc #0). Nên không chỉ nâng trần mà **đổi cách đọc**:

* `:core YoutubeSearchParse.firstVideoId(reader, maxChars, chunkChars)` — **quét theo dòng chảy**, dừng ở khớp
  ĐẦU TIÊN, giữ trong RAM một cửa sổ ~16 K ký tự bất kể trang to bao nhiêu. Thuần (`java.io.Reader`, 0 `android.*`)
  ⇒ kiểm off-car bằng `StringReader`.
* Cửa sổ giữ lại `OVERLAP_CHARS = 64` — một khớp dài 23 ký tự (`"videoId":"` + 11 + `"`) có thể nằm **vắt qua**
  ranh giới hai khối; bỏ đuôi đi là mất đúng khớp đó, **im lặng**, và chỉ mất ở một vài cỡ khối nhất định.
* `:app VoiceYoutubeResolver.MAX_CHARS` 600 K → **1 800 000**, và nay nó chỉ chặn **thời gian/băng thông**, KHÔNG
  chặn bộ nhớ.
* Thứ tự trả về **không đổi**: vẫn là khớp đầu theo thứ tự tài liệu (có bài canh).

### Băng thông có đủ cho hạn cứng 7 s không — [ĐO] có
`HttpConn.open` **không** đặt `Accept-Encoding` ⇒ lớp kết nối HTTP của Android tự xin gzip + tự giải nén. Đo:
cả trang 1,27 M ký tự = **275 784 B trên dây** (1,18 s ở mạng của tôi); tới khớp đầu (~60 % trang) ≈ **165 KB**
⇒ cần ~24 KB/s. **KHÔNG** đổi `TOTAL_BUDGET_MS` (7 s) — không phải việc của stage này, và đường lùi
`MEDIA_PLAY_FROM_SEARCH` vẫn còn cho ca mạng tệ hơn thế.

---

## 3. Tệp đã đổi trong lượt NÀY (ngoài phần lượt trước đã làm)

| Tệp | Đổi gì | Dòng |
|---|---|---|
| `app/…/voice/VoiceSessionTurns.kt` | `clarifyGaveUp` + `my` + lưới theo độ dài câu + rút ở mốc đọc-xong | 484 → **491** |
| `app/…/voice/VoiceSession.kt` | chỗ gọi `clarifyGaveUp(intents, my)` | **498** (không đổi) |
| `app/…/voice/VoiceYoutubeResolver.kt` | trần đọc 600 K → 1,8 M · đọc theo dòng chảy · KDoc bảng [ĐO] | 80 → **100** |
| `core/…/voice/YoutubeSearchParse.kt` | **MỚI** vế `firstVideoId(reader, …)` + `scan(CharSequence)` + 2 hằng | 33 → **89** |
| `app/src/test/…/voice/VoiceOverlayImmersiveContractTest.kt` | **MỚI** — guard fix ③ | **117** |
| `app/src/test/…/VoiceFastNaturalWiringContractTest.kt` | bài `clarifyGaveUp` cập nhật + 2 khẳng định mới | — |
| `app/src/test/…/VoiceMusicPlayTest.kt` | bài MỚI: trần đọc ≥ 1 M + phải quét dòng chảy + cấm `StringBuilder` | — |
| `core/src/test/…/voice/YoutubeSearchParseTest.kt` | +5 bài: id @765 K · ranh giới khối · thứ tự · biên · đuôi | 29 → **91** |

Mọi tệp **dưới trần 500 dòng** (CLAUDE.md §4.1) — có bài canh cho 2 tệp dễ vượt nhất.

---

## 4. [ĐO] Kiểm — tôi tự chạy lại trên nguồn CUỐI

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g \
  ./gradlew test :app:assembleDebug :app:assembleRelease --rerun-tasks --continue
⇒ BUILD SUCCESSFUL in 1m 17s
```

| module | test | đỏ |
|---|---|---|
| `app:testDebugUnitTest` | 1170 | 0 |
| `app:testVehicleTestUnitTest` | 1182 | 0 |
| `core:test` | 2279 | 0 |
| `car-integration:test` | 61 | 0 |
| `offcar-planner:test` | 99 | 0 |
| `vehicle-contracts:test` | 22 | 0 |
| **TỔNG** | **4813** | **0** |

Trước lượt này 4796 ⇒ **+17**, khớp **đúng** số bài mới (5 core Youtube + 5 overlay ×2 biến thể app + 1 MusicPlay
×2 biến thể = 5 + 10 + 2). `assembleRelease` xanh ⇒ **lint `abortOnError = true` cũng qua**.

### ⚠ Hai bẫy lệnh — brief ghi sai một task, ghi lại cho phiên sau
1. **`:app:testReleaseUnitTest` KHÔNG TỒN TẠI** ([ĐO] `Cannot locate tasks that match ':app:testReleaseUnitTest'`;
   `:app:tasks --all` chỉ có **`testDebugUnitTest`** + **`testVehicleTestUnitTest`**). Brief yêu cầu task đó — tôi
   chạy hai task thật **thay thế** (phủ rộng hơn, vì `vehicleTest` = release + debuggable).
2. `--tests` **không lọc** `:core:test` trong cấu hình này (nó chạy trọn bộ), và task test báo `UP-TO-DATE` khi chỉ
   đổi filter ⇒ phải thêm `--rerun`. Run xanh **không in** số đếm ⇒ đọc `*/build/test-results/**/*.xml` mới biết
   đã chạy bao nhiêu (tôi đã kiểm từng lớp guard mới **thật sự có mặt** trong kết quả, không chỉ tin BUILD SUCCESSFUL).

### Thử phá — **6 phép, đỏ đúng chỗ cả 6** (chứng minh guard không mù)
Sao lưu **theo tệp** (`cp` → `/tmp/kachi-iso-backup`, **KHÔNG** `git checkout` trên cây chưa commit — luật của dự
án), mutate → chạy → phục hồi → `shasum -a 256 -c` **khớp từng byte**.

| # | Phép phá | Bài đỏ |
|---|---|---|
| 1 | gỡ `if (hasWindowFocus) goImmersive(this)` | `VoiceOverlayImmersive… > co duoc ap lai moi luot lay lai tieu diem` |
| 2 | gỡ cờ `IMMERSIVE_STICKY` khỏi overlay | `VoiceOverlayImmersive… > tam chu dung DUNG bo co…` |
| 3 | `clarifyGaveUp` về bản 1.78 (LINGER trước, không rút ở onDone) | `VoiceFastNatural… > het tran hoi thi noi cau bo cuoc…` |
| 4 | hạ trần đọc về `600_000` | `VoiceMusicPlayTest > tran doc vuot moc da do…` |
| 5 | gom cả trang vào `StringBuilder` (bản 1.75) | `VoiceMusicPlayTest > tran doc vuot moc da do…` |
| 6 | bỏ đuôi giữ lại giữa hai khối (`window.setLength(0)`) | `YoutubeSearchParseTest > khop vat qua ranh gioi hai khoi doc…` |

### GUARD KHÔNG BỊ NỚI — và một guard đã bắt đúng tôi
`VoiceCommandWiringContractTest > khong tep Voice nao gui tieng noi ra mang` **đỏ** ở lượt đầu vì KDoc mới của tôi
viết tên lớp kết nối HTTP của nền tảng ra chữ — bài đó quét **văn bản thô** mọi tệp `Voice*` (cố ý chặn cả chú
thích, khác bài `chi tiep tieng chi duoc mo o DUNG MOT TEP` vốn tha tên lớp trong KDoc). **Không** thêm mục cho
phép; tôi **đổi câu chữ** và ghi lý do tại chỗ. ⇒ **0 dòng guard bị sửa theo hướng yếu đi** trong cả lượt.
Rà lại: dải `MIN_MIN_SILENCE_MS`/`MAX_MIN_SILENCE_MS` = **80..800 không đụng**; bất biến *"VAD chốt sớm hơn RMS"*
còn nguyên; lượt trước **thêm** sàn `>= 500` (chặt hơn, không lỏng hơn).

---

## 5. Mặc định đã đổi (tường minh, không ngầm)

| Khoá / hằng | Trước | Sau | Ai xin |
|---|---|---|---|
| `VoiceVadTrim.MIN_SILENCE_MS` | 150 ms | **600 ms** | brief ① (núm `voice_vad_min_silence_ms` vẫn chỉnh được trên xe) |
| `VoiceSession.SPEAK_SAFETY_MS` | 10 s cứng | **15 s (SÀN)** + cộng theo độ dài câu | brief ② (chính công thức của brief: `max(15_000, …)`) |
| `VoiceYoutubeResolver.MAX_CHARS` | 600 000 | **1 800 000** | §2 — **không** có trong brief, nhưng thiếu nó thì fix ④ không chạy |

**Không** đổi: `voice_speak_replies` · `voice_ask_aloud` · `voice_tts_speed` · `voice_feedback_voice` · giọng phản
hồi (vẫn Piper) · `LINGER_MS` · `NETWORK_WAIT_MS` · `CLARIFY_LISTEN_MS` · `TOTAL_BUDGET_MS` · `READ_TIMEOUT_MS`.

## 6. Sai lệch có chủ ý so với brief (4 điểm)

1. **`estimateSpeakMs` nằm ở `:core` (`VoiceSpeakBudget.estimateMs`), không phải hàm private trong `VoiceSession`**
   — quyết định của lượt trước, tôi **giữ**: `VoiceSession.kt` đang 498/500 dòng (thêm hàm + KDoc là vượt trần), và
   dự án có luật *"mặc định phải LÀ hằng của `:core`"*. **Công thức khớp brief từng con số**:
   `max(15_000L, Σ ký tự × 200L + 5_000L)`.
2. **Vá thêm đường `clarifyGaveUp`** — brief có nêu `VoiceSessionTurns.kt`; đây là đường đọc thứ hai (§1).
3. **Thêm trần đọc + quét theo dòng chảy cho ④** — ngoài chữ của brief ("chỉ đổi UA"), nhưng **bắt buộc** nếu fix ④
   phải đạt mục tiêu của nó (§2). Không làm = ship một bản vá đã đo được là vô tác dụng.
4. **Thêm 5 bài canh cho ③** — brief không đòi test cho ③; nhưng nó là fix duy nhất không có guard nào.

## 7. 🚗 CHƯA đo trên xe / rủi ro còn lại

1. **① độ trễ +450 ms trên giọng thật**, và 600 ms có phủ hết quãng ngừng dài nhất của owner không. Owner thử
   **không cần build**: `prefs_set voice_vad_min_silence_ms` (dải 80–800).
2. **② câu dài dưới load 14 có đọc trọn không** — và `onDone` của Piper có thật sự về không (nay qua ranh giới
   tiến trình `:tts` của stage #0). Đường `clarifyGaveUp` chỉ chạy sau **3 lượt hỏi lại không hiểu** ⇒ khó gặp khi
   test nhanh; muốn dựng lại thì nói 3 câu vô nghĩa liên tiếp.
3. **③ taskbar còn bị kéo lên không** — chỉ xe trả lời được (máy ảo không có taskbar BYD). `IMMERSIVE_STICKY` giả
   định ROM BYD tôn trọng cờ ẩn thanh hệ thống cho cửa sổ `TYPE_APPLICATION_OVERLAY` có tiêu điểm: [SUY] theo cách
   màn chính đang chạy, **chưa đo** cho cửa sổ overlay.
4. **④ trên mạng của XE**: mọi số đo của tôi là ở mạng máy tôi. Cần ~165 KB (gzip) trong 7 s. Và YouTube **đổi
   markup/offset lúc nào cũng được** ⇒ scrape vẫn mong manh; hai lớp phòng (regex hẹp + lùi
   `MEDIA_PLAY_FROM_SEARCH`) giữ nguyên. Nếu on-car vẫn lùi: kiểm theo thứ tự (a) HTTP code, (b) có tới được
   ~790 K ký tự trong 7 s không, (c) offset có nhảy xa hơn 1,8 M không.
5. **Offset ~765 K không phải hằng số của YouTube** — nó là [ĐO] ngày 2026-09-18 trên 4 truy vấn. Trần 1,8 M cho
   biên ~2,3× mốc đo; nếu YouTube đổi layout thì bài canh `bat duoc id nam sau 765 nghin ky tu` **không** đỏ (nó
   dùng trang giả) — thứ đỏ sẽ là hành vi trên xe. Đây là giới hạn thật của một bài canh off-car cho một bản scrape.

## 8. Dọn dẹp / nợ cho main-agent

* 🧹 **2 tệp test tạm của stage parser** còn trong cây, **không nên ship**:
  `core/src/test/…/voice/ZzScratchProbeTest.kt` + `ZzScratchProbe2Test.kt` (chính KDoc của chúng ghi *"TẠM …
  Xoá sau khi đo"*). Tôi **không xoá** — không phải tệp của stage này, và stage `tts-isolate` đã flag cùng việc.
* 📄 **Doc/backlog CHƯA cập nhật** (chủ ý — main-agent gom): `docs/specs/kachi-voice-rearchitecture-and-remaining.html`
  còn nói `SPEAK_SAFETY_MS` là *"lưới an toàn cố định"*; ba doc diagnostics 2026-09-18 còn ghi A/B/C/§BUG B ở trạng
  thái *"CHƯA implement"*; `PROJECT-BACKLOG.md` + `project-context.md` chưa có mục cho lượt này. **Nên sửa thêm**:
  KDoc *"videoId bài đầu nằm sớm trong ytInitialData"* ở `YoutubeSearchParse` **đã bị bác** — tôi đã viết lại tại
  chỗ, nhưng `oncar-voice-music-vietmap-2026-09-18.md` §BUG B vẫn mô tả fix ④ như *"chỉ đổi UA"*.
* ⚠ **`VoiceSession.kt` = 498/500 dòng · `VoiceSessionTurns.kt` = 491/500.** Stage sau muốn thêm gì vào đường phiên
  thoại thì **phải tách tệp trước**.
* **KHÔNG commit · KHÔNG push · KHÔNG bump version** (main-agent làm khi ship 1.79/vc80).
