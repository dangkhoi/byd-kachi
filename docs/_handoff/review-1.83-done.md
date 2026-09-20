# Soát độc lập — 1.83 (`:wake` isolation · P2 prefs split · 3 fix parser)

> **Trạng thái**: Current · **Ngày**: 2026-09-19 · **Vai**: senior architect, soát ĐỘC LẬP
> **Phạm vi**: (1) Hey Kachi ở tiến trình `:wake` · (2) P2 tách tệp prefs · (3) ba fix parser `:core`
> **Verdict**: **APPROVED** — 0 P0, 0 P1 còn lại (1 P1 tìm ra trong lượt soát đã VÁ + khoá bằng bài canh).
> Không đụng version/build/commit theo yêu cầu.

---

## 1. Bảng phát hiện

| # | Sev | Tệp:dòng | Vấn đề | Hành động |
|---|-----|----------|--------|-----------|
| F1 | **P1** | `core/…/voice/VoiceControlParse.kt:31` (trước vá) | Fix (c) hỏi `num != null && after.any { it.norm == "do" }`. Bỏ dấu thì `do` cũng là **âm tiết thứ hai** của *"chế độ"* / *"mức độ"* ⇒ **[ĐO off-car]** `"bật điều hòa chế độ hai"` → `Control(temp, 17)`: người lái nói một CHẾ ĐỘ, xe đặt nhiệt xuống **mức thấp nhất** (2 bị `clamp` vào 17..33). Lệnh GHI không ai xin — đúng họ lỗi mà fix (c) sinh ra để đóng. Hồi quy do chính lượt này gây ra (trước fix (c), câu đó ra `ac_auto=1`, vô hại). | **ĐÃ VÁ**: thêm `degreesSetpoint()` đòi số đứng **liền ngay trước** `"độ"`. `"chế độ <số>"` có `do` đứng TRƯỚC số ⇒ không khớp. Khoá 2 chiều bằng `VoiceLogCases0919Test.che do hay muc do co so KHONG bi hieu thanh setpoint nhiet`. |
| F2 | **P2** | `app/…/Prefs.kt:226-232` + `SettingsSections.kt:247` | **Cầu chì tự-tắt vô hình với launcher.** `SharedPreferences` cache theo TIẾN TRÌNH và **không** quan sát lượt ghi của tiến trình khác. `:wake` ghi `service_disabled=true`; launcher (đã nạp `kachi_wake_state` từ trước) vẫn đọc `false` ⇒ `wakeEnabled()` = `true` ⇒ **công tắc Cài đặt vẫn hiện BẬT trong khi không có gì đang nghe**. Toast đã tắt, và đường khôi phục duy nhất (gạt OFF→ON) là việc người dùng không có lý do gì để làm. Tự lành sau reboot (tiến trình mới đọc lại). Fuse = `MAX_WAKES 6`/phút ⇒ **đạt được thật** trong cabin ồn, tức không phải ca hiếm với một lượt crowd-test. | **BÁO, KHÔNG TỰ VÁ** — xem §4. Vá đúng = đổi cơ chế lưu (marker **file**, `File.exists()` không bao giờ cache) + sửa 3 assert của bài canh vừa viết ⇒ là quyết định thiết kế của owner, không phải vá tối thiểu. |
| F3 | P3 | `VoiceWakeService.kt:131,135` + `VoiceWakeListener.kt:75-78` | `ensureListener()` chạy **TRƯỚC** `setListening(screenOn() && !handoffActive())`, mà `start()` đặt `listening = true` ⇒ luồng mới `park()` qua ngay, `acquireWake` (per-process, không thấy launcher) thành công, mở `AudioRecord` ~1 khung **rồi** mới bị tắt. Tức bất biến *"một cú `sync()` giữa lượt nhường không được giành lại mic"* có lỗ. Kích hoạt hiện **gần như không tới được** (đường `reloadModel` cần model đã có mà handoff lại cần wake nổ ⇒ cần model). Latent, không quan sát được. | Báo. Vá gợi ý: `start(listening: Boolean = true)` + `ensureListener(want)`. Cố ý không sửa: đường luồng/mic không kiểm được off-car, và dự án cấm đổi đường đã chạy mà không đo. |
| F4 | P3 | `core/…/voice/WakeModelCatalogTest.kt` | Bảng ghim chỉ được kiểm **nội bộ nhất quán** (`totalBytes` ↔ tổng, tên phẳng, URL↔tên, staging). **Không bài nào so sha256 với 5 tệp thật trong `voice/kws/`** — mà chúng **có trong git**, nên so được off-car. Ghim sai ⇒ tải về lệch ⇒ `build` trả `null` ⇒ "Hey Kachi" im lặng không nhận. | Báo (pin hiện tại **đã kiểm tay, khớp cả 5** — xem §3). Gợi ý thêm 1 bài `shasum` tệp repo. |
| F5 | P3 | `VoiceIntentParser.kt:459` | `MEDIA_WORDS` có cụm MỘT từ `"bai"` ⇒ **[ĐO]** `"tìm bài viết về xe"` → `Media(QUERY, "viết về xe")`. Không phải lệnh xe, hoàn tác được, và cái giá của việc bỏ `"bai"` (mất *"tìm bài <tên>"* — cách nói tự nhiên nhất) cao hơn. | Không sửa (có chủ ý). Ghi lại để không ai "phát hiện lại". |
| F6 | P3 | `VoiceIntentParser.kt:462` | `mediaSearch` gọi `media(VoiceVerb.PLAY, after, "")` — truyền `original = ""`. Hiện vô hại (nhánh PLAY không trả `Unknown`), nhưng một nhánh thêm sau này sẽ sinh `Unknown` với text RỖNG ⇒ câu hỏi lại không có nguyên văn. | Báo. |
| F7 | P3 | `VoiceControlParse.kt:31` | `if (id == "ac_auto")` là `if (id == "…")` — đúng thứ KDoc `VoiceIntentParser` tuyên bố *"không luật nào là `if (id == "…")`"*. Thực dụng và có ghi chú, nhưng là một ngoại lệ với chính kiến trúc đã công bố. | Báo, giữ nguyên (vá F1 không làm nó tệ thêm). |
| F8 | P3 | `VoiceIntentParser.kt` = **497/500 dòng** | Sát trần 500 (CLAUDE.md §4.1). Lượt sau thêm bất cứ gì vào đây là phải tách. | Báo. |
| F9 | P3 | `Prefs.kt:222-228` | Cả tách tệp chỉ an toàn vì `kachi_wake_state` có **đúng 1 key** — ghi đè cả tệp thì không mất gì. Nếu ai đó thêm key thứ hai vào tệp ấy, clobber quay lại y như cũ, **và im lặng**. Không bài nào ghim tính chất "một key". | Báo. Gợi ý: bài canh đếm số key đi qua `wakeSp(`. |

### Đã kiểm và ĐẠT (không có phát hiện)

| Hạng mục | Bằng chứng |
|---|---|
| `:wake` không chạm đồ thị DI | Grep 3 tệp wake: 0 lần `AppContainer`/`ShellTransport`/`WindowCommandDispatcher`/`NavRepository`/`SimpleCastRuntime`. `:wake` chỉ import `Prefs`/`R`/`KachiHomeActivity::class.java` (class literal — không nạp lớp). ⇒ không có `ShellTransport` thứ hai mở thêm kết nối dadb. |
| Cô lập crash native | Manifest: `android:process=":wake"` · `exported="false"` · `foregroundServiceType="microphone"` · **không** `<property>` (bẫy packageinstaller Android 10) · **không** `<intent-filter>`. |
| Skip preload nặng ở tiến trình nền | `KachiApplication.onCreate` thoát sớm TRƯỚC `AppContainer.get` / `VoiceEngine.preload` (74 MB) / `VoiceVad.preload`; cổng so CẢ `:tts` và `:wake` bằng chính hằng `PROCESS_SUFFIX` (không chép chuỗi lần hai). |
| Single-writer mỗi tệp prefs | `:tts` **0 lần** chạm `Prefs` (grep). `:wake` chỉ ghi `setWakeServiceDisabled` → `kachi_wake_state`. `clusternav_prefs` chỉ launcher ghi. Manifest chỉ có 2 `android:process`. |
| `:core` vẫn thuần | 0 `import android.*` trong `VoiceIntentParser` · `VoiceControlParse` · `VoiceSynonyms` · `WakeModelCatalog` · `SherpaSpokenWords`. |
| Handler/receiver leak | `onDestroy` gọi `main.removeCallbacks(resumeTask)` **TRƯỚC** `stopListening()`, rồi `unregisterReceiver`. `resumeTask` bọc `ensureListener()` trong `if (enabled() && screenOn())` ⇒ không thể dựng lại bộ nghe sau khi cầu chì đã tắt. |
| Race `handoffUntil` | Mọi lối đọc/ghi (`fireWake`, `resumeTask`, `onStartCommand`, `onScreen` qua receiver không-handler) đều trên **luồng main** ⇒ không cần `@Volatile`. `handoffUntil` khai TRƯỚC `resumeTask` (đúng luật thứ-tự-khởi-tạo mà dự án đã cắn 2 lần). Mốc **tự hết hạn** ⇒ không tạo ngõ cụt. |
| Mic single-flight | Nhãn theo lượt `wake#<n>` + `release(label)` so **quyền sở hữu** ⇒ luồng cũ đang thoát không nhả được chốt của lượt mới. `stop()` hết trần 700 ms thì **không cướp** chốt (đúng — cướp là cách tạo hai mic). `acquireWake` không tiêu hạn mức phút ⇒ tính năng nền không giết nút mic. |
| Parser không tự chạy lệnh nhập nhằng | `"tìm trạm xăng gần đây"` · `"tìm nhà hàng gần đây"` · `"tìm kiếm nhà thuốc"` · `"tìm chỗ đậu xe"` · `"tìm bài hát"` (trống) → **tất cả `Unknown(NO_VERB)`**, tức hỏi lại. `"tìm đường đến sân bay Nội Bài"` → `Nav` (động từ NAV dài hơn thắng). |
| `mediaSearch` không thể phá câu đang chạy | Nó chỉ được gọi ở nhánh `verbHit == null` **sau** `headMatch`/`VoiceLayouts`/`savedPlace` — nhánh trước đây trả thẳng `NO_VERB`. Tức nó chỉ biến `NO_VERB` → `Media QUERY`, không lấy được câu của ai. |
| VietMap phiên âm | 4 biến thể mở đúng app (`việt máp/mép/mốp/mụp`); dạng có dấu đã khai ở `SherpaSpokenWords.ACCENTED:249-255` ⇒ `SherpaBiasingCoverageTest` xanh. `bySpokenLoose` (rụng-âm-cuối) không bị đụng. |
| Bảng ghim KWS | **Kiểm tay: 5/5 sha256 + 5/5 byte khớp tệp thật**; tổng = `5 253 782` = `totalBytes`. |

---

## 2. Bài canh có ĐỎ được không (chống bài canh trang trí)

Chạy **3 phép thử phá**, mỗi phép hoàn nguyên và kiểm `diff` byte-identical sau đó.

| Phép phá | Kết quả | Kết luận |
|---|---|---|
| M1 — nới cổng TỪ-NHẠC của `mediaSearch` (`?: return null` → `?: emptyList()`) | **3 bài ĐỎ**: `VoiceLogCases0919Test.tim phi-nhac giu nguyen NO_VERB` (*expected NO_VERB but was null*) + **2 bài CÓ TRƯỚC** `VoiceIntentParserTest.radio truyen hinh tin tuc hoi dap deu KHONG doan mo` và `VoicePhoneticMatchTest.cau doi thuong KHONG duoc thanh lenh xe` | Bài canh mới **thật**, và bất biến "không đoán" còn được **phòng thủ nhiều lớp** — không phụ thuộc một bài. |
| M2 — cầu chì ghi tệp CHUNG (`setWakeServiceDisabled` → `setWakeEnabled`) | **ĐỎ** `VoiceWakeIsolationContractTest.wake khong ghi tep prefs chung` | Bài canh P2 thật. |
| M3 — thêm `Prefs.setWakePhraseId(...)` vào `:wake` | Guard **CŨ** (`assertFalse(contains("Prefs.setWakeEnabled("))`) sẽ **XANH** → guard **MỚI ĐỎ** đúng chỗ | Guard cũ chặn *một cái tên*, không chặn *nguyên nhân* ⇒ **đã siết** (xem §4). |

Riêng F1: sau khi vá, hoàn nguyên phép vá làm bài canh mới đỏ; số đo trước/sau ở §3.

---

## 3. Đã verify bằng cách CHẠY

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g \
  ./gradlew test --rerun-tasks --continue
```

⚠ **`--rerun-tasks` là bắt buộc.** Lần chạy đầu không có nó trả `Task :core:test UP-TO-DATE · BUILD SUCCESSFUL` — đúng cái **dấu xanh giả** mà dự án đã ghi (bài canh quét `docs/`/source không được gradle coi là đầu vào).

**Toàn bộ 5 module, sau khi vá — đếm từ JUnit XML:**

| Module | Lớp | Test | Đỏ |
|---|---|---|---|
| `core` | 235 | 2 322 | 0 |
| `app` (debug) | 144 | 1 202 | 0 |
| `app` (vehicleTest) | 145 | 1 214 | 0 |
| `car-integration` | 7 | 61 | 0 |
| `offcar-planner` | 19 | 99 | 0 |
| `vehicle-contracts` | 2 | 22 | 0 |
| **TỔNG** | | **4 920** | **0** |

**Đo hành vi parser thật** (probe tạm, đã xoá sau khi đo):

| Câu | TRƯỚC vá | SAU vá |
|---|---|---|
| `bật điều hòa chế độ hai` | `Control(temp, 17)` ❌ | `Control(ac_auto, 1)` ✅ |
| `mở điều hòa chế độ 3` | `Control(temp, 17)` ❌ | `Control(ac_auto, 1)` ✅ |
| `bật điều hòa mức độ 2` | `Control(temp, 17)` ❌ | `Control(ac_auto, 1)` ✅ |
| `mở điều hòa hai mươi lăm độ` | `Control(temp, 25)` ✅ | `Control(temp, 25)` ✅ (không đổi) |
| `chỉnh máy lạnh hai mươi hai độ` | `Control(temp, 22)` ✅ | `Control(temp, 22)` ✅ (không đổi) |
| `bật điều hòa` / `tắt điều hòa` | `ac_auto 1` / `ac_auto 0` | không đổi ✅ |

**Bảng ghim KWS — so với tệp thật trong repo** (`shasum -a 256` + `stat -f%z`, 5/5 khớp):

```
1e721676…e678  4 807 159  encoder.int8.onnx
e40ff432…329a    277 985  decoder.int8.onnx
eae9da0c…624c    163 380  joiner.int8.onnx
fd2ded40…3ba53     5 006  tokens.txt
992decc8…7850        252  keywords.txt
                 ─────────
                 5 253 782  = WakeModelCatalog.totalBytes ✅
```

Ngoài ra kiểm bằng lệnh: manifest block của `VoiceWakeService`; `git ls-files voice/kws/` (5 tệp đều tracked); trần 500 dòng (`VoiceControlParse` 155 · `VoiceIntentParser` 497 · `VoiceWakeService` 303).

---

## 4. Thay đổi của lượt soát (3 tệp, +108/−4)

**Sản phẩm (1 tệp)** — `core/…/voice/VoiceControlParse.kt`
- `degreesSetpoint(after)` thay `num != null && after.any { it.norm == "do" }`: đòi số đứng **liền ngay trước** `"độ"`, dùng `VoiceLexicon.readNumber().consumed` để biết số kết thúc ở đâu. Vá F1.

**Bài canh (2 tệp)**
- `core/…/VoiceLogCases0919Test.kt` — thêm `che do hay muc do co so KHONG bi hieu thanh setpoint nhiet` (3 ca, khoá F1 hai chiều).
- `app/…/VoiceWakeIsolationContractTest.kt` — siết guard prefs: quét **mọi** `Prefs.set*` trong 3 tệp wake với danh sách cho phép `{setWakeServiceDisabled}`, **cộng** 4 reader tự-ghi-khi-migrate (`badgeCenterX/Y`, `voiceKeyBindings`, `voiceKeyTargetSpec` — chúng ghi `clusternav_prefs` từ trong đường ĐỌC). Chặn nguyên nhân thay vì một cái tên (M3).

**KHÔNG đụng**: version/versionCode, build, commit, `Prefs.kt`, `VoiceWakeService.kt`, `VoiceWakeListener.kt`, `VoiceIntentParser.kt`, `VoiceSynonyms.kt`, manifest.

### Việc tiếp theo owner cần quyết (F2 — ưu tiên cao nhất)

Cầu chì hiện **không có bề mặt nào đáng tin** cho launcher. Ba đường:

1. **(khuyến nghị)** Đổi cờ tự-tắt sang **marker file** `filesDir/wake_disabled` — `File.exists()` là syscall, **không bao giờ** cache ⇒ đúng cross-process, và vẫn giữ nguyên điều P2 cần (không đụng `clusternav_prefs`). Giá: 1 `stat` mỗi lượt đọc (chỉ ở sự kiện màn/start service, không phải đường nóng). Phải sửa 3 assert đang ghim `kachi_wake_state`/`service_disabled`.
2. `:wake` bắn broadcast cho launcher khi cầu chì nổ.
3. Chấp nhận, nhưng đổi Toast thành **notification đọng** để tester còn thấy dấu vết sau khi Toast tắt.

🚗 **Chưa đo trên xe** (không đo được off-car, không tự nhận là chạy): `android:process=":wake"` có thật sự cô lập SIGSEGV của `libonnxruntime.so`; `fireWake` có bị Android 10 chặn start-activity-từ-nền không (hệ **không ném** ⇒ `runCatching` không thấy); `WAKE_HANDOFF_MS = 18 s` trên một lượt nói hai câu liên tiếp; KWS có nhận "Hey Kachi" giọng Việt không; `createStream("")` + `keywordsFile` có đúng hợp đồng sherpa không; CPU của `:wake`.

---

## 5. Verdict

**APPROVED.**

- **P0: 0.**
- **P1: 0 còn lại** — F1 tìm ra trong lượt soát, đã vá tối thiểu, đã khoá bằng bài canh **đỏ được** (chứng minh bằng thử phá), và đã đo trước/sau bằng hành vi parser thật.
- **P2: 1 (F2)** — báo cáo kèm 3 đường vá cụ thể; cố ý **không** tự sửa vì vá đúng là đổi cơ chế lưu + viết lại assert mà owner vừa chốt ⇒ thuộc quyền owner. Không chặn ship: triệu chứng là công tắc hiện sai trạng thái, tự lành sau reboot, **không** mất dữ liệu và **không** phải lệnh xe.
- **P3: 7** — ghi lại, không cái nào chặn.
- **Không guard nào bị làm yếu** trong lượt này; một guard (prefs `:wake`) đã được **siết** sau khi chứng minh guard cũ để lọt `setWakePhraseId`.
- Kiến trúc đạt: `:wake` không chạm đồ thị DI; mỗi tệp prefs một người ghi; `:core` vẫn thuần; parser không bao giờ tự chạy lệnh nhập nhằng (5 câu "tìm …" phi-nhạc đều `NO_VERB`).
- **4 920 test / 0 đỏ** trên cả 5 module với `--rerun-tasks`.
