# Handoff — SENIOR REVIEW lượt tách `:wake` + wire tải model KWS ("Hey Kachi" crowd-test)

> **Trạng thái**: APPROVED off-car · **Ngày**: 2026-09-19 · **Phạm vi**: review `docs/_handoff/wake-isolate-done.md` + `git diff` của lượt impl.
> **CHƯA COMMIT · CHƯA PUSH · CHƯA bump versionCode · CHƯA security scan** (việc của main-agent).
> Spec đã ghi Reviewer Log: `docs/specs/kachi-wake-word.html` §10 **Pass 2**.

---

## 1. VERDICT

**APPROVED off-car.** 0×[P0]. Bốn trong sáu mục owner nêu **PASS y như lượt impl báo** (tôi tự kiểm lại, không tin báo cáo). Hai mục có vấn đề thật ⇒ **đã vá + mutation-proved**. Ba mục **báo-không-vá**, trong đó **một cần owner quyết**.

| # | Mục owner yêu cầu kiểm | Kết quả |
|---|---|---|
| 1 | Cô lập tiến trình `:wake` (crash KWS không giết launcher) · wake KHÔNG chạm `AppContainer` · `KachiApplication` skip cả `:tts` lẫn `:wake` · `startActivity` cross-process | **PASS** |
| 2 | Handoff mic: nhả trước `startActivity` · resume không chồng · bị chặn vẫn resume · không hai luồng giữ mic | **PASS có điều kiện** → vá 1×[P2] + 1×[P3] |
| 3 | Catalog: 5 sha256/bytes ghim đúng · `downloadable` · cài vào `filesDir/kws/` khớp `defaultKws` · fail-safe | **PASS** (tự đo lại cả 5 tệp) |
| 4 | Trigger tải không chặn luồng về | **PASS** |
| 5 | Trần 500 dòng | **PASS** (cao nhất 349) |
| 6 | Test thật đo được | **PASS** (mutation-proved 3 phép) |
| — | *(ngoài danh sách)* thử lại lượt tải khi hỏng | **FAIL** → vá 1×[P1] |

---

## 2. [ĐO] FULL 5-MODULE — số CHÍNH XÁC, đếm từ JUnit XML

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew test --rerun-tasks --continue
→ BUILD SUCCESSFUL in 1m 15s · 73 actionable tasks: 73 executed
```

| Module / task | tests | failures | errors | skipped |
|---|---|---|---|---|
| `app :: testDebugUnitTest` | 1201 | 0 | 0 | 0 |
| `app :: testVehicleTestUnitTest` | 1213 | 0 | 0 | 0 |
| `core :: test` | 2315 | 0 | 0 | 0 |
| `car-integration :: test` | 61 | 0 | 0 | 0 |
| `offcar-planner :: test` | 99 | 0 | 0 | 0 |
| `vehicle-contracts :: test` | 22 | 0 | 0 | 0 |
| **TOTAL** | **4911** | **0** | **0** | **0** |

Mốc lượt impl = **4907** ⇒ **+4** = 2 bài canh mới × 2 biến thể app. Khớp chính xác.

**Kèm bằng chứng mà lượt impl CHƯA có — biến thể RELEASE** (anh em nhận bản release, impl chỉ chạy `assembleDebug`):
* `:app:compileReleaseKotlin` **BUILD SUCCESSFUL**.
* Manifest **ĐÃ MERGE** của release mang **cả hai** `android:process=":tts"` + `android:process=":wake"`; khối `VoiceWakeService` có `exported="false"` + `foregroundServiceType="microphone"`, **không** `<property>`/`<intent-filter>`.
* ⚠ Lúc đầu manifest merge release **không** có `:wake` — kiểm mốc thời gian thì đó là artefact **cũ 17:32** (bản 1.80) chứ không phải lỗi thật. Ghi ra vì đây đúng bài học lặp lại của dự án: *số đo nói code sai thì kiểm phép đo trước*.

**Mutation — 3 phép, đỏ đúng chỗ cả 3, restore byte-identical (`shasum -c` OK):**

| Phép phá | Bài đỏ |
|---|---|
| Bỏ cổng `if (on && handoffActive())` ở `onScreen` | `luot nhuong micro khong bi SCREEN_ON hay sync huy giua duong` |
| Bỏ `ensureWakeModelIfEnabled(app)` khỏi `KachiAutostart` | `no may thi thu lai luot tai model neu cong tac dang bat` |
| Khôi phục công thức `staging` cũ *(kiểm lại claim của lượt impl)* | `thu muc dung do khong nam trong thu muc goi` |

---

## 3. FINDINGS ĐÃ VÁ

### [P1] Lượt tải model KWS **không có đường thử lại** → tính năng chết im
Gạt công tắc là trigger **duy nhất** (`setWakeEnabled` → `WakeModelFetch.ensure`). Nhưng đó không phải lúc chắc có mạng — trên xe thì **thường là không** (garage, 4G chập chờn). Hỏng một lượt ⇒ `VoiceWakeKws.build` trả `null` ⇒ degrade **chỉ-RMS** ⇒ "Hey Kachi" bật mà **không bao giờ nhận**, **không câu nào nói vì sao**, và **không đường nào thử lại** cho tới khi người dùng tình cờ gạt tắt–bật. `KachiAutostart` bước (5) chỉ gọi `VoiceWakeService.sync(app)`, không gọi lượt tải.

Với một lượt crowd-test mà mục đích DUY NHẤT là đo *"KWS có nhận Hey Kachi không"*, đây là khác biệt giữa **đo được tỉ lệ nhận** và **không ai chạy được mà không ai biết tại sao**.

**Vá**: `internal fun ensureWakeModelIfEnabled(app)` (cùng tệp cầu `ClusterNavBridgeWake.kt`) + gọi từ `KachiAutostart` bước (5) trong `runCatching`. Đi qua **chính** `WakeModelFetch.ensure` đã có ⇒ giữ nguyên bốn tính chất của nó (thoát sớm khi đủ tệp = 5 lần `stat` · chốt một-lượt-một-lần · daemon `MIN_PRIORITY` · bắt `Throwable`) ⇒ không làm chậm và không làm hỏng lượt boot.

### [P2] Lượt nhường mic 18 s **bị hai đường chẳng liên quan huỷ giữa đường** (hồi quy MỚI của lượt này)
Lượt nhường được biểu diễn bằng `listening = false` — mà cờ đó **cũng** là cổng màn-sáng, và **cũng** bị `onStartCommand` đặt lại mỗi lần service được start. Nên trong 18 s:
* một `SCREEN_ON` (bấm nút nguồn, hoặc màn hết giờ rồi được chạm) ⇒ `onScreen(true)` → `setListening(true)`;
* một cú `sync()` bất kỳ (gạt công tắc · boot · tải xong model) ⇒ `onStartCommand` → `setListening(screenOn())`;

…**bật nghe lại giữa lúc phiên lệnh đang ghi** ⇒ hai tiến trình CÙNG UID cùng mở `AudioRecord` = đúng triệu chứng mà cả lượt tách `:wake` sinh ra để chặn (*"gọi được nhưng nó không nghe mình nói gì"*). Trước lượt này lượt nhường là `nap(REARM_MS)` **nằm trong luồng nghe**, không đường nào ngoài huỷ được — nên đây là hồi quy do chính lượt này tạo ra.

**Vá**: mốc `handoffUntil` (`elapsedRealtime`, **tự hết hạn**) + `handoffActive()`:
* `fireWake` ghi mốc **trước cả** `startActivity` (một `SCREEN_ON`/`sync()` xen vào đúng khe đó cũng phải thấy "đang nhường");
* `onScreen` chỉ chặn ca **BẬT** — màn TẮT vẫn thi hành ngay (nhả mic, không có lý do gì để chờ);
* `onStartCommand` dùng `screenOn() && !handoffActive()`;
* `resumeTask` xoá mốc **TRƯỚC** khi kiểm hai cổng.

**Không tạo ngõ cụt** (đã duyệt từng ca): quá hạn ⇒ mọi đường bật nghe chạy như thường; màn tối đúng lúc hết hạn ⇒ `resumeTask` bỏ lượt nhưng mốc đã xoá nên `SCREEN_ON` sau đó bật lại bình thường; service chết ⇒ instance mới có mốc `0`.

### [P3] KDoc `fireWake` nói **sai cơ chế** (vá tài liệu, không đổi mã)
KDoc viết *"`setListening(false)` ⇒ vòng trong nhả mic trong một khung (~100 ms)"*. [ĐO đọc nguồn] tới dòng đó **mic đã nhả rồi**: `inner()` gọi `rec.stop()/rec.release()` trong `finally`, `runOuter()` nhả chốt `VoiceSingleFlight` — **cả hai trước khi** `onWake` được gọi (`safe(onWake)` nằm trong `when(action)` sau cả hai `finally`). Việc thật của dòng đó là **chặn lượt xin mic KẾ TIẾP**, tức **kéo dài** lượt nhường `REARM_MS` 4 s → `WAKE_HANDOFF_MS` 18 s.

Giữ nguyên bài canh thứ tự (vẫn là bất biến đúng), chỉ sửa **lý do** — để sau này không ai "tối ưu" bỏ `nap(REARM_MS)` vì tưởng dòng ấy làm việc nhả. *(Trả lời trực tiếp câu owner hỏi: "fireWake nhả mic TRƯỚC startActivity" — **thứ tự đúng như yêu cầu**, nhưng mic vốn đã trống từ trước; giá trị thật của dòng đó là chống giành-lại-sau-4-giây.)*

---

## 4. BÁO, KHÔNG VÁ

### [P2] `Prefs` là MỘT tệp `SharedPreferences` mà **cả hai tiến trình đều ghi** — cần owner quyết
Lượt impl đã ghi nhận chiều **đọc** (công tắc Cài đặt còn hiện BẬT sau khi cầu chì tắt wake). Chiều **ghi** nặng hơn và chưa ai nêu: `Prefs` dùng `MODE_PRIVATE`, một tệp, và `apply()` ghi **toàn bộ map** xuống đĩa. Cầu chì false-accept chạy trong `:wake` và ghi `voice_wake_enabled=false` ⇒ bản snapshot **cũ** của `:wake` có thể **đè mất** khoá khác mà tiến trình launcher vừa ghi (cast · nav · ghế · PM2.5 — cùng tệp `Prefs`, **không** phải `kachi_workspace`). Xác suất thấp (cầu chì hiếm + phải chồng khe ghi) nhưng là **mất dữ liệu im lặng**.

Không tự vá vì mọi đường vá đều **đổi kiến trúc hoặc đổi hành vi owner đã chốt** (OQ5 *"cầu chì tắt công tắc"*): (a) thêm kênh IPC ngược `:wake`→launcher; (b) tách tệp prefs riêng cho wake (launcher chỉ đọc); (c) bỏ persist — cầu chì chỉ dừng phiên, bật lại ở lần nổ máy sau. ⇒ **owner chọn**.

### [P3] `keywords.txt` đang khá "nhạy" — số để chỉnh **trên xe**
6 biến thể, ngưỡng `#0.20` (thấp hơn mặc định 0.25 của sherpa), và có hai cụm **rất ngắn**: `KACHI` và `CA CHI`. Tiếng Việt thường ngày có nhiều cụm gần âm (*"các chi"* · *"cá chi"* · *"cà chì"*) ⇒ nghi **false-accept cao**; mà false-accept nhiều thì **cầu chì tự tắt wake** và anh em sẽ báo *"bật xong một lúc là tự tắt"*. Là dữ liệu, không phải lỗi code — nhưng nên là mục đầu tiên chỉnh nếu on-car thấy hiện tượng đó. *(Mặt tốt: 4 biến thể phiên âm kiểu Việt `HEY CAH CHEE`/`HEY KAH CHEE` là đúng hướng cho giọng Việt.)*

### [P3] `Prefs.wakePhraseId` + `VoiceWakePhrase` (3 preset) không ai đọc để cấu hình engine
Câu gọi đến từ `keywords.txt` trong gói; `defaultKws` truyền `keywords = null`. Chưa phải "nút chết" (không có UI chọn preset), nhưng nếu sau này bày preset ra Cài đặt mà không nối vào `VoiceWakeKws` thì đúng là nút chết — ghi ra để không rơi vào đó.

---

## 5. 🚗 PHẢI ĐO TRÊN XE — danh sách cho buổi test

Xếp theo thứ tự nên đo (cái chặn nhiều nhất trước).

| # | Phép đo | Cách | PASS là gì | FAIL thì |
|---|---|---|---|---|
| 1 | **Cô lập có thật không** (đường nóng của cả lượt này **chưa từng chạy**) | `pidof com.byd.launcher:wake` → `kill <pid>` giữa lúc đang nghe | launcher **sống**, phím gán **còn ăn**, màn không nhấp; `:wake` tự dựng lại (START_STICKY) | cô lập hỏng ⇒ dừng ship, xem lại `android:process` có vào APK thật |
| 2 | **Handoff mic** — "Hey Kachi" rồi nói lệnh | nói câu gọi → nói lệnh ngay | lệnh được nghe đúng, không mất chữ đầu | nghi hai mic ⇒ xem #3 |
| 3 | **Nút mic bấm tay TRONG LÚC `:wake` đang giữ mic** ⚠ rủi ro lớn nhất, **[CHƯA BIẾT]** | bật Hey Kachi → bấm nút mic/phím 328 → nói | nghe được bình thường | `VoiceMicPreempt` là in-process nên KHÔNG bắc qua `:wake`. Vá nhỏ nhất: `VoiceCapture` bắn một lệnh "tạm nghỉ" sang `:wake` qua **chính `sync()`** đã có (thêm một extra, không thêm IPC) — nay đã có `handoffUntil` để đặt mốc |
| 4 | **KWS có nhận "Hey Kachi" không** (mục đích chính của crowd-test) | nói câu gọi ×20 ở 2–3 mức ồn, đếm | đếm được tỉ lệ nhận | model là gói **tiếng Anh** (gigaspeech) ⇒ nếu tỉ lệ thấp: chỉnh `keywords.txt` (ngưỡng/biến thể) trước khi kết luận |
| 5 | **False-accept** — có bị cầu chì tự tắt không | chạy nền 30–60 phút có nói chuyện bình thường trong cabin | wake không tự tắt | xem §4 [P3] `CA CHI` / ngưỡng `0.20` |
| 6 | **CPU của `:wake`** | `top -m 20 -n 2` / `dumpsys cpuinfo`, lọc `:wake` | ≤ vài % khi im (RMS + gating), không vòng nóng | so với baseline chỉ-RMS (tắt model) để tách phần KWS |
| 7 | **RAM: ba tiến trình** | `dumpsys meminfo com.byd.launcher` + `:tts` + `:wake` | `:wake` nhỏ (KWS ~5 MB, **không** có mô hình NGHE 74 MB) | nếu `:wake` to ⇒ cổng `isBackgroundVoiceProcess()` không ăn |
| 8 | **`WAKE_HANDOFF_MS = 18 s`** — [SUY], chưa đo | nói hai câu liên tiếp (có một lượt hỏi-lại) | không bị wake giành mic giữa câu; gọi lại được sau đó | ngắn quá ⇒ tăng; dài quá ⇒ giảm |
| 9 | **Đường tải model qua mạng xe** | bật công tắc khi có mạng; xem `logcat -s KachiWakeModel` | 5 tệp về đủ, sha khớp, bộ nghe dựng lại **ngay** (không cần nổ máy lại) | sha lệch ⇒ tệp trên repo bị re-encode (xem §6) |
| 10 | **Thử lại khi hỏng** (vá [P1] lượt này) | bật công tắc lúc **không mạng** → tắt máy → nổ lại khi có mạng | boot tự tải xong | không tải ⇒ kiểm `ensureWakeModelIfEnabled` có chạy (log `KachiWakeModel`) |
| 11 | **FGS type `microphone` trên DL3** (API 29) — vẫn [SUY] từ 1.77 | cài bản release bằng trình cài GUI | cài được, FGS lên, notif IMPORTANCE_MIN | "Fail in installation" ⇒ khối `<service>` đã giữ đơn giản, xem lại quyền `FOREGROUND_SERVICE_MICROPHONE` |
| 12 | **Cầu chì ghi pref cross-process** (§4 [P2]) | ép false-accept tới khi cầu chì tắt → mở Cài đặt | công tắc hiện TẮT | hiện BẬT = đúng như dự đoán ⇒ owner chọn đường vá |

---

## 6. VIỆC CỦA MAIN-AGENT (chưa làm, theo yêu cầu)

1. **Host 5 tệp** lên `main` của `dangkhoi/byd-kachi` tại `voice/kws/<tên phẳng>` — hiện `?? voice/kws/` (untracked). URL app gọi: `https://raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/kws/<tên>`. **Tự đo lại và khớp** (tôi đã verify độc lập, khớp từng tệp):

   | tệp | bytes | sha256 |
   |---|---|---|
   | `encoder.int8.onnx` | 4 807 159 | `1e721676515bcd42a186979733981213c66c80db680e1cc582dfedf3be76e678` |
   | `decoder.int8.onnx` | 277 985 | `e40ff43297abe815e8898494c17e71bba2152d9d40fa3eb803f75d0f7533329a` |
   | `joiner.int8.onnx` | 163 380 | `eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c` |
   | `tokens.txt` | 5 006 | `fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53` |
   | `keywords.txt` | 252 | `992decc8a440bcf56dd8bbac2b6ea738f3751cdab82daacd765f88afdaae7850` |

   Tổng **5 253 782 B**. Re-encode/LFS đổi byte ⇒ app **từ chối tải** (đúng thiết kế, `VoiceModelStore` so sha256 trong lúc tải).
2. **Bump `versionCode`/`versionName`** trước khi giao APK (CLAUDE.md §9) — lượt impl và lượt review đều **chưa** bump.
3. **Security scan** trước commit (rule global §6) — **chưa chạy**.
4. Cập nhật `project-context.md` + `PROJECT-BACKLOG.md` khi chốt số hiệu bản.
5. Cân nhắc đặt `keywords.txt` **ngưỡng cao hơn** (vd `#0.25`) hoặc bỏ hai biến thể ngắn (`KACHI`, `CA CHI`) cho lượt đầu — xem §4 [P3]. Là 1 dòng dữ liệu, đổi thì phải **ghim lại sha256** trong `WakeModelCatalog` (có bài canh giữ hai vế khớp nhau).

---

## 7. TỆP ĐÃ ĐỤNG TRONG LƯỢT REVIEW (5)

```
app/src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt   [P2]+[P3]  handoffUntil · handoffActive() · onScreen/onStartCommand tôn trọng mốc · KDoc đúng cơ chế
app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeWake.kt    [P1]       + internal ensureWakeModelIfEnabled()
app/src/main/java/com/byd/clusternav/KachiAutostart.kt                   [P1]       bước (5) gọi thử-lại-tải
app/src/test/java/com/byd/clusternav/launcher/voice/VoiceWakeIsolationContractTest.kt  +2 bài canh (mutation-proved)
docs/specs/kachi-wake-word.html                                          §10 Reviewer Log Pass 2
```

Trần 500 dòng sau vá: service **303** · listener 329 · bridge **116** · catalog 104 · application 57 · autostart **155** · test **349**. Tất cả dưới trần (có bài canh).
