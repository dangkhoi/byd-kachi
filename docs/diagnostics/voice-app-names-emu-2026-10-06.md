# VOICE-APP-NAMES (2.91) — đo trên máy ảo: dạy tên app bằng giọng TTS, nhãn locale thứ hai, harness voice trước/sau

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 (+ §9 senior review Pass 1: đo lại sau vá + sàn 3 chữ cái) · **Loại**: Diagnostics (đo máy ảo) · **Owner**: dangkhoi ·
> **Mục đích**: bằng chứng [ĐO máy ảo] cho Pass 2 của spec [`specs/kachi-290-voice-app-names.html`](../specs/kachi-290-voice-app-names.html)
> (task E2 · V1 · V2 · V3) + các phát hiện làm đổi ngưỡng/đề xuất. **Backlog**: `VOICE-APP-NAMES` (+ `VOICE-TEACH-SHORT-NAMES` ·
> `VOICE-TEACH-CONTEXT` · `VOICE-ROI-CONNECTOR` · `VOICE-OPEN-TURN-DYNVOCAB` · `VOICE-ALT-LABEL-HOTWORD` · `DEBT-VOICE-COMMON-SH`).

Mức bằng chứng theo CLAUDE.md §2 / conversation-protocol P1. **Giọng tổng hợp** (`say -v Linh` của macOS) + máy ảo API 29
⇒ mọi con số ở đây nói về **ĐƯỜNG ỐNG** (thu → giải mã → chuẩn hoá → cổng an toàn → lưu → hotword → hiểu → mở), KHÔNG nói về
độ chính xác giọng thật trên xe (E1/V5 🚗 — chưa có).

## 1. Môi trường

| Mục | Giá trị | Mức |
|---|---|---|
| Máy ảo | AVD `clusternav10` (API 29, 1920×1080), `emulator-5554` | [ĐO] |
| Locale máy ảo | `persist.sys.locale = vi-VN` · `system_locales = vi-VN` | [ĐO] |
| Mô hình nghe | `zipformer-vi-2025-04-20` (int8), đã có sẵn trên máy ảo | [ĐO] `state.voice_model.ready = true` |
| Bản NỀN | `vehicleTest` dựng từ worktree tách đúng `b7f312f` (2.90, 192) | [ĐO] |
| Bản MỚI | `vehicleTest` dựng từ cây làm việc Pass 2 (cùng `versionName` 2.90 — chưa bump vì chưa giao APK) | [ĐO] |
| Dữ liệu Kachi | sao lưu TRƯỚC khi cài (`cp -a` thư mục data + `tar` prefs), trả lại SAU khi đo — §7 | [ĐO] |

## 2. Phát hiện F1 — nói nhãn Anh TRẦN ⇒ mô hình in MỘT âm tiết ≤ 3 chữ cái

Lệnh cầu `teach` (WAV đi đúng đường giải mã của `wav`: `VoiceWavProbe` + recognizer + hotword của phiên lệnh), TTS đọc
nguyên văn nhãn tiếng Anh:

| Câu đưa TTS | Mô hình in (3 tốc độ 165/185/205 wpm) | `TeachSample` | Mức |
|---|---|---|---|
| "mở Chrome" | `mở cơm` · `mởm` · `mở cơm` | TOO_SHORT (3 chữ cái) | [ĐO máy ảo] |
| "mở Tệp" (Files, nhãn vi) | `mở thể` ×3 | TOO_SHORT | [ĐO máy ảo] |
| "mở Gmail" | `mở ghe` ×3 | TOO_SHORT | [ĐO máy ảo] |
| "mở Drive" | `mở nay` · `mở đ` · `mở nay` | TOO_SHORT / rỗng | [ĐO máy ảo] |
| "đưa Gmail vào ô số hai" | `đưa gel vào ô số hai` | — | [ĐO máy ảo] |

⇒ Sàn **4 chữ cái** (R7 bản đầu, spec §4.2 bước 5) loại hết — đúng chữ của luật, nhưng làm ĐÚNG những app owner muốn dạy thành
không dạy được. [CHƯA BIẾT] giọng thật ra gì — chốt bằng E1 trên xe. Harness lượt đầu đổi sang câu đọc Việt hoá (cách một người
Việt thật đọc tên app) — §3.

**Đã quyết (điều phối, senior review Pass 1 — spec §7 OQ4 · §9 R1)**: sàn tuyệt đối **3** chữ cái; tên GIỌNG 3 chữ cái chỉ lưu khi
mô hình in ra ĐÚNG chuỗi ấy ở **≥ 2 lượt** và qua MỌI cổng va chạm; một lượt ⇒ vẫn cần ≥ 4; tên gõ: sàn 3. Đo lại ở §9: «cơm»
(Chrome) 1 lượt ⇒ CHẶN, 2 lượt ⇒ lưu ⇒ *"mở cơm"* mở Chrome; «ghe» (Gmail = «ghế») CHẶN dù 2 lượt (tiền tố lệnh ghế).

## 3. V1 — `scripts/emulator/voice-teach-e2e.sh`: **20/20 PASS** + 2 dòng đo độ ổn định (chạy 2 lần: bản giữa + bản CUỐI sau mọi vá — phán quyết từng dòng trùng nhau)

Lệnh tái lập: `scripts/emulator/voice-teach-e2e.sh --serial emulator-5554 --apk <vehicleTest.apk> --out <thư mục riêng>`.
App tự chọn (nhãn Latin, ngoài bảng đích, có trên máy ảo): **Drive** (`com.google.android.apps.docs`) · **Chrome**
(`com.android.chrome`). Câu TTS: *"mở gu gồ đờ rai"* · *"mở cờ rôm"*. Bảng đầy đủ: [`voice-app-names-emu-2026-10-06/teach-results.tsv`](voice-app-names-emu-2026-10-06/teach-results.tsv).

| Bước | Kết quả | Mức |
|---|---|---|
| Trước khi dạy | `mở google đ` ⇒ **Mở Google** (khớp mờ sang app khác) · `mở cửa rôm` ⇒ *không tìm thấy* | [ĐO] |
| Dạy 3 lượt | Drive: `google đ` (CẢNH BÁO: STEALS_FUZZY + NEAR_OTHER_APP) · `gue` (dưới sàn) · `gu ray` (MỚI) — Chrome: `cửa rôm` ×3 (MỚI, ổn định 3/3) | [ĐO] |
| Lưu (`op=save`, qua máy dò hồi quy + cổng ViewModel) | S1 ✅ `google đ` · S2 ✅ `cửa rôm` | [ĐO] |
| WAV thử ở tốc độ KHÁC | W1 ✅ ⇒ Drive · W2 ✅ ⇒ Chrome | [ĐO] |
| `say` chuỗi đã nghe | T1 ✅ · T2 ✅ (OpenApp đúng nhãn thật) | [ĐO] |
| Mệnh đề ô | O1 ✅ `đưa google đ vào ô số hai` ⇒ *Mở Drive vào ô 2* · O2 ✅ | [ĐO] |
| Đóng | C1 ✅ · C2 ✅ ⇒ `APP_CLOSE` (không mở app) | [ĐO] |
| Va chạm lệnh xe | `đèn đọc` · `nhiệt` · `xem phim` · `cài đặt` ⇒ BLOCK, không lưu ✅ ×4 · `bật đèn đọc` vẫn ⇒ Control ✅ | [ĐO] |
| Đổi hồ sơ | P1 ✅ hồ sơ khác: `mở google đ` ⇒ Google (không Drive) · P2 ✅ về lại ⇒ Drive | [ĐO] |
| Gỡ / cài lại app | U1 ✅ gỡ Chrome (user 0) ⇒ không mở · U2 ✅ `install-existing` ⇒ mở lại | [ĐO] |
| Xoá tên | D1 ✅ `teach_clear` ⇒ `mở google đ` về lại Google | [ĐO] |
| ℹ Độ ổn định theo NGỮ CẢNH | `đưa <tên> vào ô số hai` (WAV) ra `google đy` / `cờ rôm` ≠ tên đã dạy ⇒ KHÔNG khớp (WS1 ⇒ Google ô 2, WS2 ⇒ MISMATCH) | [ĐO] — F3 |

Ba phát hiện đi kèm:
- **B1 (bug, đã vá)** — lượt chạy đầu chọn Gmail: TTS *"mở gờ meo"* ⇒ mô hình in `mở gmail` ⇒ Kachi ĐÃ mở được Gmail bằng
  nhãn; cổng trả CẢNH BÁO (ĐÃ HIỂU SẴN + MỘT TỪ, lấy mức cao nhất theo thứ tự khai) ⇒ tên bị LƯU thừa. Vá: CHẶN > ĐÃ HIỂU SẴN
  > CẢNH BÁO > MỚI (`TeachGuard.levelOf`) + test; thử ĐỎ (đảo thứ tự ⇒ đỏ).
- **F2** — hotword kéo đúng chiều: sau khi lưu `google đ`, hai lượt dạy kế (trước ra `gue`/`gu ray`) đều ra `mở google đ`.
  `createStream(hotwords)` 7 ms (0 tên) → 10 ms (2 tên) [ĐO logcat `KachiVoiceRec`]; xe [CHƯA BIẾT] 🚗.
- **F3** — câu KHÁC đổi chuỗi tên (dòng ℹ ở bảng): khớp mờ không cứu (neo 4 ký tự lệch). Đề xuất: hộp dạy gợi ý nói MỘT lượt
  bằng câu có ô ⇒ backlog `VOICE-TEACH-CONTEXT`.

## 4. V2 — harness voice NỀN `scripts/emulator/voice-e2e.sh`: trước = sau

Cùng máy ảo, cùng 27 WAV (`voice-wavgen.sh`), cùng bộ 108 ca `say`. Lệnh: `scripts/emulator/voice-e2e.sh --serial emulator-5554
[--apk <vehicleTest.apk>] --out <thư mục>`.

| | T1 (`say` → ý định → thi hành) | T2 (WAV → nghe đúng nguyên văn) | Mức |
|---|---|---|---|
| **Trước** (b7f312f, 2.90) | **108/108 PASS** | **23/27** | [ĐO] |
| **Sau** — lượt 1 (bản giữa Pass 2) | **108/108 PASS** | **23/27** | [ĐO] |
| **Sau** — lượt 2 (bản CUỐI, sau mọi vá) | **108/108 PASS** | **23/27** | [ĐO] |
| Ca đổi so với Trước | 0 | 0 — chuỗi `heard` + `preview` từng ca y hệt (so tự động 27/27) | [ĐO] |

Bốn ca T2 lệch nguyên văn là bốn ca có sẵn từ trước (w07 Bitexco · w13 *tăng âm lượng* · w22 *pin còn bao nhiêu* · w24 Waze) —
không ca nào do thay đổi này. Báo cáo: [`voice-e2e-before.md`](voice-app-names-emu-2026-10-06/voice-e2e-before.md) ·
[`voice-e2e-after.md`](voice-app-names-emu-2026-10-06/voice-e2e-after.md).

## 5. E2 — nhãn locale thứ hai (`AppAltLabels`) + giao diện

| Đo | Kết quả | Mức |
|---|---|---|
| App soát / dựng lại / nhãn phụ | 25 / 23 / **11** nhãn `en` (máy `vi-VN`) | [ĐO logcat `KachiAltLabels`] |
| Mẫu nhãn | Contacts · Camera · Clock · Phone · Photos · Wallpapers · Voice Search · Files · Settings · Play Music · Calendar — đúng chữ hệ thống ở locale `en` | [ĐO `state.voice_names.alt_sample`] |
| Thời gian | dựng lạnh lần đầu **2 935 ms** (luồng nền ưu tiên thấp — phiên nghe không chờ) · tiến trình sau **33–166 ms** · hâm lại không đổi **8 ms** | [ĐO] |
| Chưa đo | đổi locale máy sang `en` rồi so nhãn `vi` với `loadLabel` ở `vi` | [CHƯA BIẾT] |

Giao diện (ảnh chụp máy ảo chỉ lưu cục bộ, **không đăng** — `.gitignore` chặn `docs/diagnostics/**/*.png` vì ảnh có tên tài khoản Google của máy ảo; mô tả dưới đây thay cho ảnh):
- `page-pending.png` — `TEACH_APP s:<mẫu>` qua intent ⇒ Cài đặt › Giọng nói › trang *Dạy tên app* với dòng *Mẫu đang chờ* · lọc
  *Tất cả 22 / Đã dạy 0 / Nên dạy 4* · Drive hiện *"Kachi hiểu sẵn: đờ rai, drive"* (dạng đọc sinh từ nhãn).
- `dialog-pending.png` — chạm Chrome ⇒ hộp dạy có sẵn *Lần 1: «nep leag» ✓ Mới*.
- `saved.png` — gõ tên ⇒ *Lưu* ⇒ máy dò hồi quy nền ⇒ *"Đã lưu 1 tên cho Chrome"*; trang về *Đã dạy 1 · Chrome: 1 tên*.
- `drawer-longpress.png` — nhấn giữ icon ở ngăn kéo thường ⇒ menu *"Dạy tên gọi bằng giọng"* ⇒ trang mở hộp dạy cho app ấy.
  [ĐO] lượt đầu trang mở NẰM DƯỚI lớp ngăn kéo ⇒ vá: đóng ngăn kéo trước khi mở (`AppDrawerApps.teachMenu`).
- [ĐO] hai lỗi giao diện tìm bằng máy ảo, đã vá: hộp dạy của lối (b) mở LẠI sau mỗi lượt lưu (yêu cầu gói không được tiêu thụ)
  · bàn phím bật ngay khi mở trang (ô tìm giành tiêu điểm).
- Không đo được trên máy ảo: lượt 🎤 bằng micro thật · nút *"Dạy tên «…»"* trên tấm chữ (cần một phiên mic thật) ⇒ 🚗.

## 6. V3 — test + lint + build

[ĐO 06/10, đếm từ XML, `--rerun-tasks --continue`, máy ảo tạm dừng bằng SIGSTOP trong lúc chạy] `:core:test` **4 420 / 0** · `:app:testDebugUnitTest` **2 077 / 0** · `:app:testVehicleTestUnitTest` **2 089 / 0** · `:car-integration:test` **80 / 0** · `:vehicle-contracts:test` **22 / 0** · `:offcar-planner:test` **99 / 0** — tổng **8 787 / 0**. `:app:lintDebug` **0 lỗi** (577 cảnh báo, chạy lại trên mã CUỐI; của tệp mới còn: PluralsCandidate ở chuỗi mới · UseKtx · SetTextI18n — cùng loại đang có trong cây; ObsoleteSdkInt + StaticFieldLeak đã sửa/khai lý do). `:app:assembleRelease` xanh. Thử làm ĐỎ (P5.3) 5 luật: nhánh tên đã dạy của `appAfterMarker` · đường `dropAppNameLeading` của luật đơn điệu · luật tiền tố lệnh · thứ tự mức phán quyết · luật che của gói thua khử trùng — gỡ ⇒ đỏ, trả lại ⇒ xanh. Lệnh: `./gradlew :core:test :app:testDebugUnitTest :app:testVehicleTestUnitTest :car-integration:test :vehicle-contracts:test :offcar-planner:test --rerun-tasks --continue` (JAVA_HOME = openjdk@17).

## 7. Trả máy ảo

Lúc nhận việc máy ảo KHÔNG chạy (`adb devices` rỗng) ⇒ phiên này khởi động AVD `clusternav10`. Trước khi cài: chép nguyên thư mục data của Kachi (`cp -a`, 188 MB gồm mô hình) vào `/data/local/tmp/kachi-backup` (để lại trên máy ảo cho lượt sau) + kéo về APK đang cài (2.89 release, 191). Sau MỖI đợt đo (2 đợt): cài lại đúng APK ấy (`install -r -d`), dừng Kachi, tráo NGUYÊN thư mục data về bản chép (đổi chỗ, không giải nén đè), `chown` + `restorecon`, dừng lần nữa, so sha256 9 tệp prefs: **8/9 trùng byte**; tệp thứ 9 (`clusternav_prefs.xml`) khác đúng MỘT khoá `a11y_proc_start_elapsed` = mốc khởi động tiến trình Kachi tự ghi mỗi lần lên (khoá này cũng khác giữa hai lần đọc TRƯỚC khi đo). Không còn `kachi_test_bridge.xml` (test-mode tắt); `files/voice-log` 60 = 60 mục như bản chép. [ĐO] Cuối phiên: máy ảo TẠM DỪNG bằng `SIGSTOP` (đánh thức: `kill -CONT <pid qemu>`).

## 8. Còn cần xe (🚗)

- **E1** — độ ổn định chuỗi mô hình in cho tên app ngoại, GIỌNG THẬT (owner nói 5 app × 5 lần; nhật ký H2 tự lưu khi test-mode
  bật). Workspace hôm nay không có WAV giọng thật nào để phát lại (188 tệp `.wav` đều là tiếng động cơ).
- **E3** — locale xe (`getprop persist.sys.locale` · `settings get system system_locales`) + danh sách app khởi chạy + mốc
  `createStream(hotwords) N ms` có tên đã dạy (ngân sách 150 ms).
- **V5** — dạy 5 app ngoại × 3 lần, thử 5 câu/app (có/không ô — chấm ở mức ý định + lệnh gắn ô được gửi), đổi hồ sơ, gỡ/cài lại;
  đường `:wake` (chế độ Hey Kachi / phím gán Kachi nghe) của lượt dạy chưa chạy ở đâu cả.

## 9. Senior review Pass 1 (06/10) — đo lại sau vá + quyết định sàn 3 chữ cái

Bản đo: `vehicleTest` dựng từ cây làm việc CUỐI của lượt review (sau mọi vá; cùng `versionName` 2.90 — chưa bump vì chưa giao APK). Chạy 2 lần (bản giữa + bản CUỐI) — phán quyết từng ca trùng nhau; số dưới là bản CUỐI.
Cùng máy ảo `clusternav10`, cùng 27 WAV (`voice-wavgen.sh`, bộ của §4), cùng bộ 108 ca `say`. Dữ liệu Kachi sao lưu TRƯỚC (`cp -a`
nguyên thư mục data) và trả SAU (cài lại APK 2.89 (191) đang có + tráo nguyên thư mục + `restorecon`) — 8/9 tệp prefs + ảnh chụp
ngữ pháp trùng byte; tệp thứ 9 (`clusternav_prefs.xml`) chỉ khác khoá `a11y_proc_start_elapsed` (Kachi tự ghi mỗi lần tiến trình
lên, như §7); `voice-log` 60 = 60; không còn `kachi_test_bridge.xml`. Máy ảo tạm dừng lại (`SIGSTOP`).

| Đo | Trước (§3 · §4, bản Pass 2) | Sau review | Mức |
|---|---|---|---|
| `voice-teach-e2e.sh` 20 ca cũ | 20/20 | **20/20** — phán quyết từng ca y hệt; riêng lượt 1b «gue» nay ra `BLOCK [ONE_WORD, SHORT_ONE_TAKE]` (trước: dưới sàn) | [ĐO] |
| `voice-teach-e2e.sh` mục 10 (mới — tên NGẮN) | — | **5/5**: SH1 «cơm» 1 lượt ⇒ CHẶN · SH2 lượt 2 ra đúng «cơm» ⇒ lưu · SH3 *"mở cơm"* ⇒ Chrome · SH4 *"bật đèn đọc"* vẫn ⇒ Control · SHG «ghe» ⇒ CHẶN dù 2 lượt (`COMMAND_PREFIX`) | [ĐO] |
| WAV thử 185 wpm *"mở Chrome"* | `mởm` (F1) | *"mở cơm"* ⇒ Mở Chrome — hotword `MỞ CƠM` kéo (cùng hiện tượng F2) | [ĐO] |
| `voice-e2e.sh` T1 (`say`) | 108/108 | **108/108** | [ĐO] |
| `voice-e2e.sh` T2 (WAV nguyên văn) | 23/27 | **23/27** — 27/27 dòng (`heard` + `preview`) y hệt bản *Sau* của §4 (so tự động); T1 108/108 dòng y hệt trừ mã đối tượng `ActivityRecord{…}` của cột tác dụng phụ (đổi mỗi lần chạy) | [ĐO] |
| R-nf6 dựng bảng gọi app có 1–2 tên đã dạy | chưa đo | `VoiceAppIndex.build` **0,35–1,1 ms** · cả `appIndex` (gồm `PackageManager`) **3,2–5,2 ms** (`state.voice_names.index_*_us`, 4 lần đọc / 2 lượt chạy) — ngân sách 20 ms | [ĐO máy ảo] · xe [CHƯA BIẾT] 🚗 |

Lệnh tái lập: `scripts/emulator/voice-teach-e2e.sh --serial emulator-5554 --apk <vehicleTest.apk> --out <thư mục>` rồi
`scripts/emulator/voice-e2e.sh --serial emulator-5554 --out <thư mục>`. Bảng: [`teach-results-review.tsv`](voice-app-names-emu-2026-10-06/teach-results-review.tsv) ·
[`voice-e2e-review.md`](voice-app-names-emu-2026-10-06/voice-e2e-review.md).

Phát hiện kèm (off-car, `:core`, [ĐO] chạy thử bộ phân tích): tên ngắn một âm tiết bỏ dấu đụng **từ đồng hình** — dạy «nay» cho
Drive ⇒ *"mở này"* / *"mở cái này"* (trước: không hiểu) thành mở Drive; dạy «cơm» ⇒ *"phát cơm"* (trước: tìm bài) thành mở Chrome.
Cùng họ đánh đổi spec §4.13 đã chấp nhận cho tên đã dạy/nhãn app, nhưng xác suất cao hơn với 3 chữ cái; cổng hiện có chỉ CẢNH
BÁO "một từ đơn" (phải bấm *Vẫn lưu*) ⇒ backlog `VOICE-TEACH-SHORT-HOMOGRAPH` (owner chốt có thêm cổng hay không). Tái lập: một
test `:core` tạm dựng `TeachContext(keys, taught = [TaughtName(<gói Drive>, SPEECH, "nay", …)])` rồi so `parse("mở này")` có/không tên.
