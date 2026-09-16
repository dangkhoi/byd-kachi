# E2E giọng nói trên máy ảo — TOÀN BỘ lớp lệnh, hai tầng (chữ + tiếng)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-15 (**lượt 2 — bản 1.64**, xem §6) · **Loại**: Diagnostics (evidence, có số đo thật) ·
> **Owner**: dangkhoi · **Spec**: `docs/specs/kachi-voice-command.html` · `docs/specs/kachi-voice-engine-v2.html` ·
> `docs/specs/kachi-test-bridge.html` · **Kịch bản**: `scripts/emulator/voice-e2e.sh` + `voice-cases.tsv` +
> `voice-wavgen.sh`

**Quy ước mức bằng chứng** (CLAUDE.md §2 · `.kiro/steering/conversation-protocol.md`): `[ĐO]` = số đo trực tiếp ·
`[SUY]` = suy luận khớp dữ kiện · `[CHƯA ĐO]` = chưa có phép đo.

> ⚠ **Mức bằng chứng tối đa của tài liệu này**: mọi con số đo trên **máy ảo `emulator-5554` (Android 10 / API 29,
> arm64, Google APIs)** với **giọng tổng hợp `say -v Linh`**. KHÔNG phải DiLink3, KHÔNG có HAL BYD, KHÔNG có mic
> 4 kênh, KHÔNG có ồn đường. Vì vậy:
> * mọi lệnh `Control` trả `✗ … xe không nhận lệnh` là **ĐÚNG** (không có xe để nhận) — thứ đo được ở đây là
>   **ý định** (`intents`) + **câu trả lời** + **tác dụng phụ trong launcher**, không phải HAL;
> * ba thứ tác dụng phụ đo được thật: app lên màn (`dumpsys activity activities`), ô đổi nội dung (bridge
>   `state.layout.slots`), hồ sơ đổi (bridge `state.profile.active`);
> * độ chính xác nghe (T2) là **thuộc tính của model** nên chuyển sang thiết bị khác giữ được; RTF thì KHÔNG.

## 0. Bản đã đo

| Mục | Giá trị |
|---|---|
| Máy | `emulator-5554` · `sdk_gphone64_arm64` · API 29 · arm64-v8a |
| APK | `com.byd.launcher` **1.63 (versionCode 64)** bản **vehicleTest** (debuggable, ký khoá release) — `app/build/outputs/apk/vehicleTest/app-vehicleTest.apk` |
| Lượt đối chứng | **cùng bộ ca chạy trước đó trên 1.60 release** (đường `adb root`): kết quả **trùng KHỚP TỪNG CA** (`diff` hai bảng chỉ khác chỗ cắt chuỗi) ⇒ mọi lỗi dưới đây **không phải** hồi quy của 1.63 |
| Mô hình nghe | `zipformer-vi-2025-04-20` (sherpa-onnx, Apache-2.0) — 4 tệp, sha256 **khớp đúng bản ghim** `SherpaModelCatalog.ZIPFORMER_VI` |
| Biasing | `biasing=true` [ĐO logcat `KachiVoiceEngine`], nạp engine **1 520 ms**, giải mã **~80–150 ms/câu** |
| App đích có mặt | YouTube · YT Music · Google Maps · Waze · VietMap Live |
| Hồ sơ | chỉ **một** (`Mặc định`) ⇒ ca đổi hồ sơ đổi sang chính nó (vẫn đo được cổng CONFIRM, xem T1 t57/t58) |

⚠ **Sổ địa chỉ chưa có trong APK này**: [ĐO] `dẫn đường về nhà` trên 1.63 → `Nav(query="về nhà")` + hỏi lại
(đường từ-vựng-mở cũ), `về nhà` / `đến công ty` → `Unknown(NO_VERB)`. Nhánh `VoiceIntent.NavigateSaved` +
`SherpaBiasing.hotwordsFile(places)` đang nằm trong **cây làm việc chưa biên dịch vào bản này** ⇒ chưa đo được.

## 1. Cách bật cầu kiểm thử và cách nạp mô hình (đã đo, tái lập được)

**Chế độ kiểm thử** — `TestBridgeStore` (`app/.../testbridge/TestBridgeStore.kt:27,29`) cố ý **không có đường bật
bằng broadcast**: tệp prefs `kachi_test_bridge`, khoá `test_bridge_until`, giá trị `"<boot_id>:<elapsedRealtime +
60 phút>"` (`TestBridgeWindow.encode`). Script dựng lại đúng khuôn đó từ `/proc/sys/kernel/random/boot_id` +
`/proc/uptime` (trừ 2 phút để không rơi vào nhánh *"giá trị bị sửa tay"* `left > WINDOW_MS`), `am force-stop`
**trước** khi ghi (SharedPreferences giữ bản RAM), rồi mở lại màn chính. [ĐO] `test_mode_minutes_left = 58`.

Hai đường ghi vào vùng dữ liệu app, script tự chọn:
* `run-as com.byd.launcher` — bản **vehicleTest** debuggable; đường **duy nhất chạy được trên xe thật**.
  [ĐO] đây là đường đã dùng cho bảng chính (1.63);
* `adb root` + `cp` + `chown <uid>` + `restorecon` — máy ảo cho root ⇒ đo được **cả trên bản release** đang cài
  mà không phải cài lại. [ĐO] đường này đã dùng cho lượt đối chứng 1.60. Cả hai cho **cùng một kết quả**.

**Mô hình** (đường (b) trong yêu cầu): chép thẳng 4 tệp vào `files/sherpa/zipformer-vi-2025-04-20/` ⇒
`VoiceModelStore.isReady()` = `true` **không cần bấm gì trong Cài đặt**; `bpe_vocab.txt` do chính app chép ra từ
asset lúc nạp engine ([ĐO] có mặt, 55 006 byte). Đường (a) — `<ext>/sherpa/import/<model-id>/` +
`VoiceModelSideload` — **chưa đo** trong lượt này (nó cần một cú bấm *"tải mô hình"* trong Cài đặt).

sha256 4 tệp tải từ HuggingFace **khớp 100%** bản ghim trong `SherpaModelCatalog.ZIPFORMER_VI` [ĐO `shasum -a 256`].

## 2. Kết quả

Cột **kết quả** của T1 so với **kỳ vọng khai trong `voice-cases.tsv`**, không phải so với "đúng/sai theo cảm tính":
mỗi ca chỉ kiểm thứ nó khai (kind · chuỗi trong preview · có/không hỏi lại · tác dụng phụ). Một ca `PASS` mà hành
vi vẫn sai (vd *"đóng YouTube"* lại **mở** YouTube) thì nằm ở **§3 LỖI** — bảng chỉ nói *"máy làm đúng thứ nó
đang được viết để làm"*.

### T1 — chữ → ý định → thi hành (`say`)

| id | lớp | câu | intent | reply | hỏi lại | tác dụng phụ | kết quả |
|---|---|---|---|---|---|---|---|
| t01 | toggle | bật đèn đọc | Control · Bật Đèn đọc | ✗ Bật Đèn đọc — xe không nhận lệnh |  | - | PASS |
| t02 | toggle | tắt đèn đọc | Control · Tắt Đèn đọc | ✗ Tắt Đèn đọc — xe không nhận lệnh |  | - | PASS |
| t03 | toggle | bật lọc bụi | Control · Bật Lọc bụi | ✗ Bật Lọc bụi — xe không nhận lệnh |  | - | PASS |
| t04 | toggle | tắt điều hoà | Control · Tắt Điều hoà AUTO | ✗ Tắt Điều hoà AUTO — xe không nhận lệnh |  | - | PASS |
| t05 | toggle | bật sưởi ghế | Control · Bật Ghế sưởi | ✗ Bật Ghế sưởi — xe không nhận lệnh |  | - | PASS |
| t06 | toggle | mở cửa sổ trời | Control · Bật Cửa sổ trời | ✗ Bật Cửa sổ trời — xe không nhận lệnh |  | - | PASS |
| t07 | toggle | khoá xe | Control · Bật Khoá / mở khoá | ✗ Bật Khoá / mở khoá — xe không nhận lệnh |  | - | PASS |
| t08 | toggle-confirm | tắt khoá xe | Control · Tắt Khoá / mở khoá | ✗ Tắt Khoá / mở khoá — đã huỷ | Tắt Khoá / mở khoá — chưa kiểm trên xe? … | - | PASS |
| t09 | toggle-confirm | dừng chiếu cụm | Control · Tắt Chiếu cụm | ✗ Tắt Chiếu cụm — đã huỷ | Tắt Chiếu cụm — chưa kiểm trên xe? ⏎ dừn… | - | PASS |
| t10 | cover | mở kính trước trái | Control · Mở Kính trước-trái | ✗ Mở Kính trước-trái — xe không nhận lệnh |  | - | PASS |
| t11 | cover | đóng kính trước trái | Control · Đóng Kính trước-trái | ✗ Đóng Kính trước-trái — xe không nhận lệnh |  | - | PASS |
| t12 | cover | mở kính bên lái | Control · Mở Kính trước-trái | ✗ Mở Kính trước-trái — xe không nhận lệnh |  | - | PASS |
| t13 | cover-confirm | mở hết kính | Macro · Chạy gói Mở hết kính | ✗ Chạy gói Mở hết kính — đã huỷ | Chạy gói Mở hết kính? ⏎ hạ hết kính | - | PASS |
| t14 | macro | đóng hết kính | Macro · Chạy gói Đóng hết kính |  |  | - | PASS |
| t15 | step | nhiệt độ hai mươi bốn độ | Control · Đặt Nhiệt độ = 24 | ✗ Đặt Nhiệt độ = 24 — xe không nhận lệnh |  | - | PASS |
| t16 | step | đặt nhiệt độ 24 độ | Control · Đặt Nhiệt độ = 24 | ✗ Đặt Nhiệt độ = 24 — xe không nhận lệnh |  | - | PASS |
| t17 | step | tăng gió | Control · Tăng Gió 1 nấc | ✗ Đặt Gió = 5 — xe không nhận lệnh |  | - | PASS |
| t18 | step | giảm âm lượng | Control · Giảm Âm lượng 1 nấc | ✓ Đặt Âm lượng = 11 |  | - | PASS |
| t19 | step | tăng âm lượng tối đa | Control · Đặt Âm lượng = 30 | ✓ Đặt Âm lượng = 30 |  | - | PASS |
| t20 | step | đặt độ sáng màn 8 | Control · Đặt Độ sáng màn = 8 | ✗ Đặt Độ sáng màn = 8 — xe không nhận lệnh |  | - | PASS |
| t21 | select | chế độ lái thể thao | Control · Chế độ lái: Thể thao | ✗ Chế độ lái: Thể thao — xe không nhận lệnh |  | - | PASS |
| t22 | select | đặt chế độ lái eco | Control · Chế độ lái: Eco | ✗ Chế độ lái: Eco — xe không nhận lệnh |  | - | PASS |
| t23 | select | màu đèn viền xanh lá | Control · Màu đèn viền: Xanh lá | ✗ Màu đèn viền: Xanh lá — xe không nhận lệnh |  | - | PASS |
| t24 | button | lọc ngay | Control · Bấm Lọc ngay | ✗ Bấm Lọc ngay — xe không nhận lệnh |  | - | PASS |
| t25 | button | sạc ngay | Control · Bấm Sạc ngay | ✗ Bấm Sạc ngay — xe không nhận lệnh |  | - | PASS |
| t26 | button-confirm | mở khoá cửa | Control · Bấm Mở khoá cửa | ✗ Bấm Mở khoá cửa — đã huỷ | Bấm Mở khoá cửa — chưa kiểm trên xe? ⏎ m… | - | PASS |
| t27 | button-confirm | mở khoá cửa | Control · Bấm Mở khoá cửa | ✗ Bấm Mở khoá cửa — xe không nhận lệnh |  | - | PASS |
| t28 | macro | rời xe | Macro · Chạy gói Rời xe |  |  | - | PASS |
| t29 | macro | chạy gói mở cửa + đèn đọc | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "chạy gói mở… | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "chạy gói mở cửa + đèn… |  | - | **FAIL** — kind=Unknown (mong Macro) |
| t30 | launcher | mở cài đặt | Launcher · Mở Cài đặt | ✓ Mở Cài đặt |  | - | PASS |
| t31 | launcher | mở ứng dụng | Launcher · Mở Ứng dụng | ✓ Mở Ứng dụng |  | - | PASS |
| t32 | launcher | nói với xe | Launcher · Mở Nói với xe | ✓ Mở Nói với xe |  | - | PASS |
| t33 | read | xem pin | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t34 | read | pin còn bao nhiêu | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t35 | read | nhiệt độ ngoài trời bao nhiêu | Read · Xem Nhiệt ngoài xe | Nhiệt ngoài xe: chưa đọc được |  | - | PASS |
| t36 | read | đọc tầm hoạt động | Read · Xem Tầm hoạt động EV | Tầm hoạt động EV: chưa đọc được |  | - | PASS |
| t37 | read-mismatch | xem đèn đọc | Unknown · Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ:… | Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ: "xem đèn … |  | - | PASS |
| t38 | app | mở YouTube | OpenApp · Mở ứng dụng YouTube | ✓ Mở ứng dụng YouTube |  | mResumedActivity: ActivityRecord{3973f1 u0 com… | PASS |
| t39 | app | mở Maps | OpenApp · Mở ứng dụng Maps | ✓ Mở ứng dụng Maps |  | mResumedActivity: ActivityRecord{263e2ec u0 co… | PASS |
| t40 | app | mở ứng dụng YouTube | OpenApp · Mở ứng dụng YouTube | ✓ Mở ứng dụng YouTube |  | mResumedActivity: ActivityRecord{3973f1 u0 com… | PASS |
| t41 | app-close | đóng YouTube | OpenApp · Mở ứng dụng YouTube | ✓ Mở ứng dụng YouTube |  | - | PASS |
| t42 | app-close | tắt YouTube | OpenApp · Mở ứng dụng YouTube | ✓ Mở ứng dụng YouTube |  | - | PASS |
| t43 | app-slot | đưa YouTube vào ô hai | OpenApp · Mở ứng dụng YouTube vào ô 2 | ✓ Mở ứng dụng YouTube vào ô 2 |  | app:com.google.android.youtube | PASS |
| t44 | app-slot | mở YouTube vào ô số 9 | OpenApp · Mở ứng dụng YouTube vào ô 9 | ✗ Mở ứng dụng YouTube vào ô 9 — bố cục hiện chỉ có 3 ô |  | - | PASS |
| t45 | app | mở bản đồ | Unknown · Không tìm thấy thứ đó trong xe hay trong launcher: "mở bản đ… | Không tìm thấy thứ đó trong xe hay trong launcher: "mở bản đồ" |  | - | PASS |
| t46 | media | phát nhạc | Media · Phát nhạc | ✗ Phát nhạc — chưa có phiên nhạc nào — mở app nhạc rồi nói lại |  | package=com.android.server.telecom | PASS |
| t47 | media | dừng nhạc | Media · Dừng nhạc | ✗ Dừng nhạc — chưa có phiên nhạc nào — mở app nhạc rồi nói lại |  | - | PASS |
| t48 | media | bài tiếp theo | Media · Bài tiếp theo | ✗ Bài tiếp theo — chưa có phiên nhạc nào — mở app nhạc rồi nói lại |  | - | PASS |
| t49 | media | bài trước | Media · Bài trước | ✗ Bài trước — chưa có phiên nhạc nào — mở app nhạc rồi nói lại |  | - | PASS |
| t50 | media | mở nhạc trên YouTube Music | Media · Phát nhạc trên YouTube Music | ✗ Phát nhạc trên YouTube Music — chưa có phiên nhạc nào — mở app nhạc … |  | - | PASS |
| t51 | media-open-vocab | phát bài Diễm Xưa | Media · Tìm bài «Diễm Xưa» | ✗ Tìm bài «Diễm Xưa» — đã huỷ | Tìm bài «Diễm Xưa»? ⏎ đoạn trong ngoặc d… | - | PASS |
| t52 | media-open-vocab | mở bài Diễm Xưa trên YouTube Music | Media · Tìm bài «Diễm Xưa» trên YouTube Music | ✓ Tìm bài «Diễm Xưa» trên YouTube Music — đã mở kết quả tìm — bấm Play… |  | mResumedActivity: ActivityRecord{1b11a9e u0 co… | PASS |
| t53 | nav | dẫn đường đến Bitexco | Nav · Dẫn đường tới Bitexco | ✗ Dẫn đường tới Bitexco — đã huỷ | Dẫn đường tới Bitexco? ⏎ đoạn trong ngoặ… | - | PASS |
| t54 | nav | dẫn đường tới chợ Bến Thành bằng Waze | Nav · Dẫn đường tới chợ Bến Thành trên Waze | ✓ Dẫn đường tới chợ Bến Thành trên Waze |  | mResumedActivity: ActivityRecord{7cb2b7a u0 co… | PASS |
| t55 | nav | chỉ đường đến sân bay bằng google map | Nav · Dẫn đường tới sân bay trên Google Maps | ✗ Dẫn đường tới sân bay trên Google Maps — đã huỷ | Dẫn đường tới sân bay trên Google Maps? … | - | PASS |
| t56 | nav | dẫn đường | Unknown · Không tìm thấy thứ đó trong xe hay trong launcher: "dẫn đườn… | Không tìm thấy thứ đó trong xe hay trong launcher: "dẫn đường" |  | - | PASS |
| t57 | profile | đổi sang hồ sơ Mặc định | Profile · Đổi sang hồ sơ Mặc định | ✗ Đổi sang hồ sơ Mặc định — đã huỷ | Đổi sang hồ sơ Mặc định? ⏎ đổi hồ sơ tha… | - | PASS |
| t58 | profile | chuyển sang hồ sơ Mặc định | Profile · Đổi sang hồ sơ Mặc định | ✓ Đổi sang hồ sơ Mặc định |  | Mặc định | PASS |
| t59 | layout | đổi bố cục 4 ô | Unknown · Không tìm thấy thứ đó trong xe hay trong launcher: "đổi bố c… | Không tìm thấy thứ đó trong xe hay trong launcher: "đổi bố cục 4 ô" |  | - | PASS |
| t60 | layout | bố cục hai ô | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "bố cục hai … | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "bố cục hai ô" |  | - | PASS |
| t61 | compound | bật đèn đọc và tắt lọc bụi | Control,Control · Bật Đèn đọc / Tắt Lọc bụi | ✗ Bật Đèn đọc — xe không nhận lệnh ⏎ ✗ Tắt Lọc bụi — xe không nhận lện… |  | - | PASS |
| t62 | compound-confirm | mở khoá cửa rồi bật đèn đọc | Control,Control · Bấm Mở khoá cửa / Bật Đèn đọc | ✗ Bấm Mở khoá cửa — đã huỷ, 1 việc sau không chạy | Bấm Mở khoá cửa — chưa kiểm trên xe? ⏎ m… | - | PASS |
| t63 | unknown | hôm nay trời đẹp quá | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "hôm nay trờ… | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "hôm nay trời đẹp quá" |  | - | PASS |
| t64 | unknown | kể cho tôi nghe một câu chuyện | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "kể cho tôi … | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "kể cho tôi nghe một c… |  | - | PASS |
| t65 | unknown | bật abcxyz | Unknown · Không tìm thấy thứ đó trong xe hay trong launcher: "bật abcx… | Không tìm thấy thứ đó trong xe hay trong launcher: "bật abcxyz" |  | - | PASS |
| t66 | unknown | kachi ơi | Unknown · Chưa có câu lệnh nào: "kachi ơi" | Chưa có câu lệnh nào: "kachi ơi" |  | - | PASS |
| t67 | unknown | xem xe | Unknown · Không tìm thấy thứ đó trong xe hay trong launcher: "xem xe" | Không tìm thấy thứ đó trong xe hay trong launcher: "xem xe" |  | - | PASS |

**T1: 66/67 PASS** (1 FAIL)

### T2 — tiếng → nhận dạng → ý định (`wav`)

| id | câu gốc | heard | ngữ pháp (lượt 1) | tự do (lượt 2) | intent | khớp |
|---|---|---|---|---|---|---|
| w01 | mở YouTube | mở youtube | mở youtube |  | OpenApp · Mở ứng dụng YouTube | ✅ |
| w02 | bật đèn đọc | bật đèn đọc | bật đèn đọc |  | Control · Bật Đèn đọc | ✅ |
| w03 | tắt đèn đọc | tắt đèn đọc | tắt đèn đọc |  | Control · Tắt Đèn đọc | ✅ |
| w04 | mở kính trước trái | mở kín trước trái | mở kín trước trái |  | Unknown · Không tìm thấy thứ đó trong xe hay trong lau… | ≈ |
| w05 | đóng kính trước trái | đóng kính trước trái | đóng kính trước trái |  | Control · Đóng Kính trước-trái | ✅ |
| w06 | nhiệt độ hai mươi bốn độ | nhiệt độ hai mươi bốn độ | nhiệt độ hai mươi bốn độ |  | Control · Đặt Nhiệt độ = 24 | ✅ |
| w07 | dẫn đường đến Bitexco | dẫn đường đến bico | dẫn đường đến bi eco | dẫn đường đến bico | Nav · Dẫn đường tới bico | ≈ |
| w08 | phát nhạc | phát nhạc | phát nhạc |  | Media · Phát nhạc | ✅ |
| w09 | dừng nhạc | rừng nhạc | rừng nhạc |  | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem… | ❌ |
| w10 | đưa YouTube vào ô số hai | đưa youtube vào ô số hai | đưa youtube vào ô số hai |  | OpenApp · Mở ứng dụng YouTube vào ô 2 | ✅ |
| w11 | mở khoá cửa | mở khóa cửa | mở khóa cửa |  | Control · Bấm Mở khoá cửa | ✅ |
| w12 | xem pin | xem tin | xem tin |  | Unknown · Không tìm thấy thứ đó trong xe hay trong lau… | ❌ |
| w13 | tăng âm lượng | tăng âm lượng | tăng âm lượng |  | Control · Tăng Âm lượng 1 nấc | ✅ |
| w14 | giảm nhiệt độ | giảm nhiệt độ | giảm nhiệt độ |  | Control · Giảm Nhiệt độ 1 nấc | ✅ |
| w15 | bật sưởi ghế | bật sưởi ghế | bật sưởi ghế |  | Control · Bật Ghế sưởi | ✅ |
| w16 | đóng hết kính | đóng hết kính | đóng hết kính |  | Macro · Chạy gói Đóng hết kính | ✅ |
| w17 | mở cài đặt | mở cài đặt | mở cài đặt |  | Launcher · Mở Cài đặt | ✅ |
| w18 | bài tiếp theo | bài tiếp theo | bài tiếp theo |  | Media · Bài tiếp theo | ✅ |
| w19 | chế độ lái thể thao | chế độ lái thể thao | chế độ lái thể thao |  | Control · Chế độ lái: Thể thao | ✅ |
| w20 | lọc ngay | lọc ngay | lọc ngay |  | Control · Bấm Lọc ngay | ✅ |
| w21 | hôm nay trời đẹp quá | hôm nay trời đẹp quá | hôm nay trời đẹp quá |  | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem… | ✅ |
| w22 | pin còn bao nhiêu | còn bao nhiêu | còn bao nhiêu |  | Unknown · Không tìm thấy thứ đó trong xe hay trong lau… | ≈ |
| w23 | bật lọc bụi và tắt đèn đọc | bật lọc bụi và tắt đèn đọc | bật lọc bụi và tắt đèn đọc |  | Control,Control · Bật Lọc bụi / Tắt Đèn đọc | ✅ |
| w24 | dẫn đường tới chợ Bến Thành bằng Waze | dẫn đường tới chợ bến thành bằng loa e | dẫn đường tới chợ bến thành bằng l… | dẫn đường tới chợ bến th… | Nav · Dẫn đường tới chợ bến thành bằng loa e | ≈ |
| w25 | đổi sang hồ sơ Chính | đổi sang hồ sơ chính | đổi sang hồ sơ chính |  | Unknown · Việc đó không đi với thứ đó — thử nêu mức, h… | ✅ |

**T2: 19/25 nghe ĐÚNG NGUYÊN VĂN** (xem cột intent cho ca nghe lệch mà ý định vẫn đúng)

**Đếm theo Ý ĐỊNH** (thứ thật sự quyết định máy có làm đúng việc không): **18/24 đúng hoàn toàn (75%)** · **2 đúng loại nhưng sai nội dung mở** (w07 điểm đến `bico`, w24 mất app đích `Waze` → `loa e`) · **4 sai** (w04 `kính`→`kín`, w09 `dừng`→`rừng`, w12 `pin`→`tin`, w22 rụng chữ `pin`) · **1 không tính** (w25 gọi hồ sơ *Chính* — máy ảo chỉ có hồ sơ *Mặc định*, lỗi của bộ ca). Cả 4 ca sai đều rơi đúng vào lỗ hotwords **L3** — xem §3.

### Bốn phép đo thêm (ngoài ma trận — chạy tay qua bridge; [ĐO] trên 1.60, bộ ca chính đã cho thấy 1.60 ≡ 1.63)

| Câu | Kết quả [ĐO] |
|---|---|
| `mở cửa + đèn đọc` | `Macro · Chạy gói Mở cửa + đèn đọc` · reply `Mở cửa + đèn đọc: xe không nhận lệnh nào` (ms=805) |
| `mở cửa và đèn đọc` | **chỉ 1** intent `Control · Bật Đèn đọc` — vế *"mở cửa"* bị bỏ **im lặng** (xem L5) |
| `dẫn đường đến Bitexco` + `auto_confirm` | reply *"đang tra điểm đến…"*, sau đó **VietMap Live lên màn** (`mResumedActivity: vn.vietmap.live/.MainActivity`); câu trả lời CUỐI không vào được lời đáp (xem L4) |
| `tăng nhiệt độ 2 nấc` · `tắt máy lạnh` · `mở cốp` · `gạt mưa` · `mở nhạc` | đúng nút/ý định (`Nhiệt độ = 24` từ mốc 22 · `Điều hoà AUTO` · `Cốp sau` · `Gạt mưa` · `Phát nhạc`) |
| `dẫn đường về nhà` · `về nhà` · `đến công ty` (trên **1.63**) | `Nav(query="về nhà")` + hỏi lại · `Unknown(NO_VERB)` · `Unknown(NO_VERB)` — sổ địa chỉ chưa có trong APK này |

## 3. LỖI (mỗi lỗi: câu → kỳ vọng → thực tế → gốc → đề xuất)

> ⚠ **Số trong mục này là của lượt 1 (bản 1.63) và KHÔNG được sửa.** Trạng thái sau khi vá nằm ở dòng
> `[VÁ 1.64]` của từng mục và ở **§6** (bảng trước/sau của lượt đo lại).

### [P1] L1 — *"đóng/tắt &lt;app&gt;"* lại **MỞ** app đó
* **Câu**: `đóng YouTube` · `tắt YouTube` (T1 t41/t42).
* **Kỳ vọng**: đóng app đang mở, hoặc ít nhất nói *"chưa làm được"*.
* **Thực tế [ĐO]**: `OpenApp · Mở ứng dụng YouTube` → `✓ Mở ứng dụng YouTube` — máy làm **đúng việc ngược lại**.
* **Gốc [ĐO nguồn]**: `core/.../voice/VoiceIntentParser.kt:228` — nhánh `VoiceTermKind.APP ->
  VoiceIntent.OpenApp(term.id, slotAt(after))` dựng ý định **không xét động từ** (mọi nhánh khác đều xét:
  CONTROL/MACRO/LAUNCHER đi qua `VoiceGrammar.isAction`); và `VoiceIntent` (`VoiceIntent.kt:22–104`) **không có**
  nhánh đóng app.
* **Đề xuất**: (a) vá tối thiểu, an toàn ngay: động từ `CLOSE`/`OFF`/`PAUSE` + đối tượng APP ⇒
  `Unknown(MISMATCH)` — nói *"chưa đóng được app bằng giọng"* thay vì mở nó ra; (b) nếu owner muốn đóng thật:
  đó là **cơ chế mới** ⇒ CLAUDE.md §14 tầng 1 (shell thô trên xe: `am force-stop` / `am task` có ăn trên ROM BYD
  không) trước khi viết `:core`.
* **[VÁ 1.64]** ĐÃ VÁ theo hướng (a) [ĐO lượt 2, t41/t42]: `đóng YouTube` · `tắt YouTube` ⇒ `Unknown(APP_CLOSE)`
  → *"Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app khác đè lên"*; **không** app nào lên màn nữa.
  Gốc vá: `VoiceIntentParser` nay xét động từ ở nhánh APP (và ở nhánh *"mở ứng dụng &lt;tên&gt;"*), lý do riêng
  `VoiceUnknownReason.APP_CLOSE` để câu trả lời nói đúng việc. Đóng app THẬT vẫn là cơ chế mới ⇒ còn ở §14 tầng 1,
  **chưa đo**.

### [P1] L2 — *"phát nhạc trên YouTube Music"* không mở YT Music, báo *"chưa có phiên nhạc nào"*
* **Câu**: `mở nhạc trên YouTube Music` (T1 t50), `phát nhạc` (t46).
* **Kỳ vọng**: mở/đưa lệnh cho app nhạc được nêu đích danh.
* **Thực tế [ĐO]**: ý định **đúng** (`Media · Phát nhạc trên YouTube Music`, tức `app=ytmusic` đã được phân tích
  ra) nhưng reply `✗ … chưa có phiên nhạc nào — mở app nhạc rồi nói lại`; **không app nào lên màn**.
* **Gốc [ĐO nguồn]**: `app/.../launcher/VoiceDispatcher.kt:299` — `runMedia` mở đầu bằng
  `if (i.op != VoiceMediaOp.QUERY) { runTransport(i); return }`, tức mọi lệnh **không phải QUERY** rơi thẳng vào
  transport và trường `i.app` bị **vứt**. `runTransport` (`:364–374`) chỉ gọi `MediaBridge`, mà `MediaBridge`
  degrade-safe khi chưa có phiên ⇒ câu trả lời đúng theo mã, sai theo ý người nói.
* **Đề xuất**: `op == PLAY` mà (`i.app != null` **hoặc** `mediaPackage() == null`) ⇒ đi đường `pickMusic` +
  `openPlain`/`deliver` như nhánh QUERY, rồi mới transport. Giữ nguyên câu *"chưa có phiên nhạc"* cho ca
  `PAUSE/NEXT/PREV` (ở đó nó đúng).
* **[VÁ 1.64]** ĐÃ VÁ đúng đề xuất [ĐO lượt 2]: `phát nhạc` (t46) → `✓ Phát nhạc — đã mở YouTube Music; chưa có
  phiên nhạc nào để điều khiển — bấm Play trong app`; `mở nhạc trên YouTube Music` (t50) → cùng dạng, và
  [ĐO `dumpsys activity activities`] **YT Music thật sự lên màn**
  (`com.google.android.apps.youtube.music/.activities.MusicActivity`). `dừng nhạc` (t47) giữ nguyên transport +
  câu *"chưa có phiên nhạc nào"* — ở đó nó đúng. App đích ĐANG phát ⇒ vẫn transport, không mở đè.

### [P2] L3 — biasing mất **50/187** nhãn vì một dấu câu ⇒ nghe sai đúng những từ hay dùng nhất
* **Câu [ĐO T2]**: `xem pin` → nghe `xem tin` (w12, ra Unknown) · `mở kính trước trái` → `mở kín trước trái`
  (w04, ra Unknown) · `dừng nhạc` → `rừng nhạc` (w09, ra Unknown).
* **Gốc [ĐO nguồn + đếm bằng script]**: `core/.../voice/SherpaHotwords.kt` `normalize()` — nhánh
  `else -> return null` **bỏ CẢ cụm** khi gặp một ký tự không phải chữ/khoảng trắng. Đếm trên danh mục:
  **12/64** nhãn `ControlRegistry` (gồm cả bốn *"Kính trước-trái/phải"*, *"Khoá / mở khoá"*, *"EV / HEV"*) và
  **38/123** nhãn `TelemetryRegistry` (gồm ***"Pin (SOC)"***, *"Áp lốp trước-trái"*…) **không bao giờ** thành
  hotword. Thêm hai lỗ nữa: `SherpaBiasing.accentedControlPhrases()` **không** lấy `VoiceSynonyms.CONTROL/
  TELEMETRY` — nơi chứa đúng các từ đời thường `pin` · `kính` · `cửa sổ` · `điều hoà` — và **không** lấy động từ
  (`VoiceGrammar.VERBS`: *bật · tắt · mở · đóng · dừng · tăng · giảm*), tức chính từ bị nghe nhầm ở w09.
* **Vì sao quan trọng**: `biasing=true` [ĐO logcat] nên nhìn từ ngoài mọi thứ "đang bật", mà ba từ trung tâm của
  bộ lệnh vẫn sai. Con số **87% intent** của `vn-stt-sherpa-emulator-eval-2026-09-14.md` đo bằng **danh sách
  hotword thủ công** (có `PIN`) ⇒ **không áp dụng** cho bản đang ship — đây là một chỗ `[ĐO]` cũ bị mang sang
  làm bảo chứng cho một cấu hình khác (CLAUDE.md §14).
* **Đề xuất**: trong `normalize`, thay ký tự không phải chữ bằng **khoảng trắng** (giữ nguyên luật loại chữ số);
  và đưa `VoiceSynonyms.CONTROL/TELEMETRY` + `VoiceGrammar.VERBS` vào nguồn hotwords. Khoá bằng một bài canh
  off-car: *"mọi cụm trong danh mục đều ra được ít nhất một hotword"* (cùng hình dạng bài canh
  `VoiceGrammarPhrasesTest` đang có cho pha Vosk).
* **[VÁ 1.64 — MỘT PHẦN]** [ĐO lượt 2]: lỗ **50/187 nhãn đã đóng hoàn toàn** (0 nhãn mất hotword), tập hotword
  **408 → 623** dòng, và `PIN` · `KÍNH` · `KÍNH TRƯỚC TRÁI` · `DỪNG` · `NHẠC` **nay đều có mặt** (trước không có
  cụm nào). Kết quả nghe: **w04 `mở kính trước trái` ĐÃ ĐÚNG** (trước: *"mở kín trước trái"* ⇒ Unknown).
  **Nhưng w09 (`dừng`→*rừng*) và w12 (`pin`→*tin*) KHÔNG đổi** dù hotword đã có — xem §6.3, đó là một lỗ KHÁC
  (hotword một-từ-ngắn không thắng được âm), chưa có kết luận gốc.

### [P2] L4 — câu trả lời của **gói lệnh** và của **nav cần toạ độ** không bao giờ vào được lời đáp `say`
* **Thực tế [ĐO]**: `đóng hết kính` → `replies: []` (ms=703) · `rời xe` → `replies: []` ·
  `dẫn đường đến Bitexco --ez auto_confirm true` → chỉ có `"… đang tra điểm đến…"`. Ngược lại
  `mở cửa + đèn đọc` (gói **không** có bước chờ) → có reply (ms=805).
* **Gốc [ĐO nguồn]**: `app/.../testbridge/KachiTestBridge.kt:399` `GRACE_MS = 700` ms, trong khi `runMacro` chạy
  `MacroRunner` trên luồng nền **có `Thread.sleep` giữa các bước** và `runNav` chờ geocode ở luồng nền
  (`VoiceDispatcher.kt:276–294`). Trần cả lượt thì tận `CAP_MS = 20 000`, nên chỗ hụt là **nhịp chờ**, không
  phải trần.
* **Hậu quả**: mọi script đo (kể cả bộ này) **mù** với kết quả thật của đúng hai nhánh chạy nền — tức hai nhánh
  dễ hỏng nhất lại là hai nhánh không quan sát được.
* **Đề xuất**: chờ theo **việc** chứ không theo hằng số: `say` giữ `PendingResult` tới khi không còn vế nào đang
  chạy (hoặc GRACE theo loại ý định: Macro/Nav ⇒ 5–8 s), trần vẫn `CAP_MS`.
* **[VÁ 1.64]** ĐÃ VÁ theo hướng *chờ-theo-việc* (`TestBridgeSettle`, thuần `:core`, có bài canh): đủ số dòng so
  với số ý định **và** lặng 300 ms ⇒ chốt; còn dòng TẠM (`…`) ⇒ chờ tới 12 s; trần khác 5 s; trần lượt vẫn
  `CAP_MS = 20 s`. [ĐO lượt 2] `đóng hết kính` `replies: []` → **1 dòng** (`wait_ms` 1 749) · `rời xe` → **1
  dòng** (2 823) · `dẫn đường đến Bitexco --ez auto_confirm true` → **2 dòng**, có cả câu CUỐI `✓ Dẫn đường tới
  Bitexco` (1 474). Số ca `replies: []` trong 67 ca: **2 → 0**. Lệnh thường **nhanh hơn** nhịp cũ:
  `wait_ms` ≈ 0,36–0,61 s thay vì 0,70 s cố định.

### [P2] L5 — câu ghép: một vế không hiểu ⇒ vế đó bị bỏ **im lặng**
* **Câu [ĐO]**: `mở cửa và đèn đọc` → **một** intent `Bật Đèn đọc`; không câu nào nói *"mở cửa"* đã bị bỏ.
* **Gốc [ĐO nguồn]**: `VoiceIntentParser.kt:52–57` — tách theo liên từ, **chỉ nhận** khi mọi vế hiểu được, ngược
  lại phân tích lại **nguyên câu**; lúc đó luật *"cách hiểu đầu tiên có nghĩa"* tìm thấy `đèn đọc` ở giữa câu và
  trả đúng một ý định. Luật này sinh ra cho ca *"Mở bài Cỏ dại và hoa dành dành"* (đúng chỗ đó), nhưng ở đây nó
  nuốt mất một mệnh đề.
* **Đề xuất**: khi câu **có liên từ** mà bản phân tích cả-câu chỉ dùng một phần chuỗi, nói thêm một dòng
  *"đã bỏ qua: «mở cửa»"* — im lặng ở đây là người lái tưởng cả hai việc đã chạy.
* **[VÁ 1.64]** ĐÃ VÁ [ĐO lượt 2, chạy tay qua cầu]: `mở cửa và đèn đọc` ⇒ **2** ý định —
  `Control · Bật Đèn đọc` + `Unknown · Đã bỏ qua vế không hiểu: "mở cửa"`, và cả hai dòng đều vào lời đáp.
  Phân biệt với tên bài có chữ *"và"*: `Mở bài Cỏ dại và hoa dành dành` vẫn **1** ý định, không báo gì (vế ấy
  nằm trong phần từ-vựng-mở mà cả câu đã nhận).

### [P2] L6 — tên app là **nhãn hệ thống** ⇒ nhiều app *"gõ được mà không nói được"*
* **Câu [ĐO]**: `mở bản đồ` → `Unknown` (máy ảo có nhãn tiếng Anh *"Maps"*); `mở Maps` → ✓.
* **Gốc**: `VoiceWiring.appsByLabel` lấy `ri.loadLabel(pm)` (generic — đúng CLAUDE.md §7), nhưng không có lớp
  *"cách gọi tiếng Việt"* cho `OpenApp`. Bảng `VoiceSynonyms.APP_TARGETS` **đã có** cách gọi tiếng Việt
  (*"bản đồ google"*, *"viet map"*, *"quay"*…) nhưng cố ý chỉ tra ở mệnh đề *"bằng &lt;app&gt;"*
  (`VoiceIntentParser.appAfterMarker`).
* **Cộng hưởng với T2**: model VN **không phát ra** token tiếng Anh ([ĐO] eval 09-14 `youtube` → *"ô tường"*;
  lượt này w01 `mở youtube` nghe ĐÚNG vì giọng TTS đọc theo âm Việt — **giọng thật có thể khác**).
* **Đề xuất**: cho `OpenApp` tra thêm `APP_TARGETS` (một cụm → tên gói → nhãn), giữ nguyên ưu tiên nhãn thật.
* **[VÁ 1.64]** ĐÃ VÁ đúng đề xuất [ĐO lượt 2, t45]: `mở bản đồ` ⇒ `OpenApp · Mở ứng dụng Google Maps` và
  [ĐO `dumpsys`] **Google Maps thật sự lên màn** (`com.google.android.apps.maps/com.google.android.maps.MapsActivity`).
  Nhãn thật vẫn thắng (`mở Maps` ⇒ nhãn *"Maps"* của máy); mệnh đề ô còn nguyên (`mở bản đồ vào ô số 2`);
  app chưa cài ⇒ *"chưa cài &lt;tên&gt;"* thay vì *"không mở được"*.

### [P3] L7 — **bố cục** chưa có ý định giọng nói
`đổi bố cục 4 ô` · `bố cục hai ô` → `Unknown` [ĐO t59/t60]. `VoiceIntent` không có nhánh bố cục, `LauncherActions`
chỉ có `APPS/SETTINGS/VOICE` — trong khi cầu kiểm thử **đã có** lệnh `preset` (`TestBridgeCommands.PRESET`) ⇒ khả
năng có sẵn ở tầng dưới, chỉ thiếu đường từ câu nói. Spec `kachi-voice-command.html` chưa khai R nào cho việc này
⇒ **quyết định của owner**, không tự thêm.

### [P3] L8 — bẫy của chính harness (đã vá): `adb` nuốt `stdin` của vòng lặp
Lượt chạy đầu chỉ thực hiện **1/67** ca rồi im. Gốc: `while read … done < cases.tsv` + `adb shell` bên trong —
`adb` đọc hết stdin. Vá: đọc ca qua **fd 3** (`done 3< "$CASES"`) và `adbs() { "$ADB" … </dev/null; }`. Ghi lại vì
mọi script adb có vòng lặp đều dính bẫy này, và triệu chứng của nó là *"chạy xanh nhưng thiếu việc"*.

### [INFO] L9 — một ca trong ma trận sai từ đầu
`chạy gói mở cửa + đèn đọc` → `Unknown(NO_VERB)`: *"chạy"* không nằm trong `VoiceGrammar.VERBS`. Câu gọi đúng tên
gói (`mở cửa + đèn đọc`) chạy đúng. Đây là lỗi của bộ ca, **không** phải của sản phẩm — giữ lại trong bảng để
lượt sau không "sửa" nhầm sản phẩm.

## 4. [CHƯA ĐO] — và vì sao

| Việc | Vì sao chưa đo | Cần gì để chốt |
|---|---|---|
| Transport nhạc với **phiên thật** (play/pause/next/prev có hiệu lực) | [ĐO] đã thử tạo phiên: mở `https://www.youtube.com/watch?v=…` → YouTube của máy ảo dừng ở **tường nâng cấp** (`NewVersionAvailableActivity`) ⇒ `dumpsys media_session` vẫn `have 0 sessions` (máy CÓ mạng: ping 8.8.8.8 = 39 ms). `MediaBridge` no-op ⇒ mọi reply là *"chưa có phiên nhạc nào"* (đúng theo mã) | một app nhạc phát được (bản YouTube/YT Music mới hơn), hoặc đo trên xe |
| Đường side-load (a) `<ext>/sherpa/import/<id>/` + nút *"tải mô hình"* | lượt này dùng đường (b) (chép thẳng vào `filesDir`) cho tất định | một lượt bấm trong Cài đặt (UI) hoặc một entry gọi `VoiceModelStore.install` |
| `--es cmd listen` (phiên mic thật) | máy ảo không có micro | xe thật (§11 CLAUDE.md) |
| Câu trả lời CUỐI của nhánh nav cần toạ độ | rơi ngoài `GRACE_MS` (L4) | vá L4 rồi đo lại |
| Sổ địa chỉ (`NavigateSaved`, biasing kèm nhãn địa chỉ) | chưa có trong APK 1.63 đã đo (còn trong cây làm việc) | build lại rồi chạy §5, một lệnh |
| Giọng thật · ồn đường · mic 4 kênh · RTF trên A10 | thuộc phần xe | playbook §2.14 / K1–K3 |

## 5. Chạy lại (một lệnh)

```bash
# 1) sinh WAV (macOS, giọng Linh) — ~25 câu, 16 kHz mono PCM16
scripts/emulator/voice-wavgen.sh /tmp/kachi-voice-wav

# 2) tải mô hình (4 tệp, ~270 MB) vào một thư mục bất kỳ; sha256 phải khớp SherpaModelCatalog.ZIPFORMER_VI
BASE=https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20/resolve/main
curl -sSL -o encoder.onnx "$BASE/encoder-epoch-12-avg-8.onnx"   # d566456163…
curl -sSL -o decoder.onnx "$BASE/decoder-epoch-12-avg-8.onnx"   # d1d27cca84…
curl -sSL -o joiner.onnx  "$BASE/joiner-epoch-12-avg-8.onnx"    # a186d4ddf0…
curl -sSL -o tokens.txt   "$BASE/tokens.txt"                    # f536d03c2e…

# 3) chạy cả hai tầng (tự bật chế độ kiểm thử, tự nạp mô hình nếu thiếu)
scripts/emulator/voice-e2e.sh --serial emulator-5554 --apk apk/Kachi-<ver>-vehicleTest.apk \
    --modeldir <thư mục 4 tệp> --wavdir /tmp/kachi-voice-wav --out /tmp/kachi-voice-e2e
# chỉ một tầng:  --only say   |   --only wav
```

Bảng kết quả: `<out>/report.md`; dữ liệu thô từng ca: `<out>/t1-results.tsv`, `<out>/t2-results.tsv` (cột cuối là
**nguyên văn JSON** mà cầu trả về, để lượt sau `diff` được).

## 6. LƯỢT 2 — bản **1.64 (65)** sau khi vá L1 · L2 · L3 · L4 · L5 · L6

[ĐO] cùng máy `emulator-5554`, **cùng bộ ca** (`voice-cases.tsv` 67 ca + 25 WAV cũ, không sinh lại tiếng), cùng
mô hình đã nạp sẵn. Lệnh chạy y như §5, chỉ đổi `--apk` sang bản 1.64 và `--out /tmp/kachi-voice-e2e-164`.
Số của **lượt 1 ở §2/§3 KHÔNG bị sửa** — bảng dưới là số MỚI đặt cạnh số cũ.

### 6.1 Tổng quan trước/sau

| Thước đo | 1.63 (lượt 1) | 1.64 (lượt 2) |
|---|---|---|
| T1 — ca PASS | 66/67 (1 FAIL: **t29**, lỗi của bộ ca — L9) | **66/67** (vẫn đúng ca t29 ấy, không ca nào mới đỏ) |
| T1 — ca trả `replies: []` | **2** (`t14 đóng hết kính`, `t28 rời xe`) | **0** |
| T2 — nghe đúng nguyên văn | 19/25 | **20/25** (thêm **w04**) |
| T2 — ý định đúng hoàn toàn | 18/24 (75%) | **19/24 (79%)** |
| Tập hotword sinh ra | **408** dòng | **623** dòng |
| Nhãn danh mục KHÔNG ra hotword nào | **50/187** (12 control + 38 datum) | **0/187** |
| `PIN` · `KÍNH` · `KÍNH TRƯỚC TRÁI` · `DỪNG` · `NHẠC` trong tập hotword | **không có cụm nào** | **có đủ 5** |

### 6.2 Từng lỗi — trước → sau ([ĐO] nguyên văn lời đáp của cầu)

| Lỗi | Câu | 1.63 | 1.64 |
|---|---|---|---|
| **[P1] L1** | `đóng YouTube` (t41) | `OpenApp` → `✓ Mở ứng dụng YouTube` (**làm ngược**) | `Unknown(APP_CLOSE)` → *"Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app khác đè lên"* |
| **[P1] L2** | `mở nhạc trên YouTube Music` (t50) | `✗ … chưa có phiên nhạc nào`, **không app nào lên màn** | `✓ Phát nhạc trên YouTube Music — đã mở YouTube Music; chưa có phiên nhạc nào để điều khiển — bấm Play trong app` + [ĐO `dumpsys`] **`…youtube.music/.activities.MusicActivity` lên màn** |
| **[P1] L2** | `dừng nhạc` (t47) | `✗ … chưa có phiên nhạc nào` | **giữ nguyên** (đúng: không có gì đang phát để dừng) |
| **[P2] L3** | `mở kính trước trái` (w04) | nghe *"mở kín trước trái"* → `Unknown` | nghe **đúng** → `Control · Mở Kính trước-trái` |
| **[P2] L3** | `xem pin` (w12) · `dừng nhạc` (w09) | *"xem tin"* · *"rừng nhạc"* | **KHÔNG đổi** — xem §6.3 |
| **[P2] L4** | `đóng hết kính` (t14) | `replies: []` (ms=703) | `Đóng hết kính: xe không nhận lệnh nào` · `wait_ms=1 749` |
| **[P2] L4** | `rời xe` (t28) | `replies: []` | `Rời xe: xe không nhận lệnh nào` · `wait_ms=2 823` |
| **[P2] L4** | `dẫn đường đến Bitexco --ez auto_confirm true` | chỉ có dòng TẠM *"…đang tra điểm đến…"* | **2 dòng**: dòng TẠM + câu CUỐI `✓ Dẫn đường tới Bitexco` · `wait_ms=1 474` |
| **[P2] L5** | `mở cửa và đèn đọc` | **1** ý định (`Bật Đèn đọc`), vế *"mở cửa"* bị bỏ **im lặng** | **2** ý định: `Bật Đèn đọc` + `Đã bỏ qua vế không hiểu: "mở cửa"` |
| **[P2] L5** | `Mở bài Cỏ dại và hoa dành dành` | 1 ý định (đúng) | **1 ý định, không báo gì** (vế ấy nằm trong tên bài — luật phân biệt bằng phần từ-vựng-mở) |
| **[P2] L6** | `mở bản đồ` (t45) | `Unknown` (*"không tìm thấy…"*) | `OpenApp · Mở ứng dụng Google Maps` + [ĐO `dumpsys`] **Google Maps lên màn**; `mở Maps` vẫn đi đường **nhãn thật** |

**Nhịp chờ mới (L4)** — `say` nay trả thêm trường `wait_ms` (thời gian nán chờ thật): lệnh thường
**0,36–0,61 s** (nhanh hơn hằng cũ 0,70 s), gói lệnh 1,7–2,8 s, nav cần toạ độ 1,5 s; trần lượt vẫn `CAP_MS`
20 s và **không ca nào** chạm trần.

### 6.3 [CHƯA BIẾT] — vì sao `pin` · `dừng` vẫn nghe sai dù ĐÃ là hotword

[ĐO] w12 `xem pin` → *"xem tin"*, w09 `dừng nhạc` → *"rừng nhạc"*, w22 `pin còn bao nhiêu` → *"còn bao nhiêu"* —
**giống hệt lượt 1**, trong khi cùng lượt ấy w04 (`KÍNH TRƯỚC TRÁI`, cụm **3 từ**) lại được kéo về đúng. Tức
biasing **có ăn** (w04 là bằng chứng), nhưng không thắng ở **cụm một từ ngắn**.

Ba dữ kiện đã có, KHÔNG được trộn với nhau:
* [ĐO] tập hotword 1.64 **có** `PIN` và `DỪNG` (kiểm bằng bài canh `SherpaBiasingCoverageTest`);
* [ĐO] `hotwords_score = 3.0`, `modeling_unit = bpe`, `biasing=true` (logcat `KachiVoiceEngine`) — **không đổi**
  so với lượt 1;
* [ĐO] `vn-stt-sherpa-emulator-eval-2026-09-14.md` §2 ca 06: cùng câu *"xem pin"*, cùng score 3.0, **biasing SỬA
  được** — nhưng phép đo ấy chạy **trên macOS** với một **danh sách hotword thủ công nhỏ**, không phải 623 dòng.

⇒ [SUY] hai giả thuyết còn lại, **chưa tách được**: (a) cụm một-từ-ngắn không đủ sức kéo so với âm thật của
giọng TTS; (b) danh sách 623 dòng làm loãng/ganh nhau trong đồ thị ngữ cảnh. **Cách chốt (một phép đo)**: chạy
sherpa-onnx trên host với **cùng 3 tệp WAV** và **hai** tệp hotword — (1) đúng 623 dòng của 1.64, (2) chỉ 3 dòng
`PIN` / `XEM PIN` / `DỪNG NHẠC` — rồi so HYP. Kết quả quyết định bước sau: nếu (1) sai mà (2) đúng ⇒ vấn đề là
**độ dài cụm** ⇒ thêm cụm **động từ + đối tượng** vào nguồn hotword; nếu cả hai sai ⇒ vấn đề nằm ở `score` /
giọng, xử bằng phép đo giọng thật trên xe. **Chưa làm** vì host hiện không có model (266 MB) lẫn sherpa CLI.

⚠ Ca canh vẫn xanh: **w21** *"hôm nay trời đẹp quá"* và **w25** nghe **đúng nguyên văn** ở 1.64 ⇒ 623 hotword
(kể cả các từ đơn `MỞ` · `TRƯỚC` · `TIẾP`) **không** chèn lệnh vào câu thường.

#### [ĐO host 2026-09-15] — phép đo chốt (a) vs (b), chạy trực tiếp sherpa-onnx trên macOS

Chạy được cả 4 việc §6.3 để ngỏ ("chưa làm vì host không có model lẫn sherpa CLI"): cài `sherpa-onnx==1.13.8`
(pip, venv tạm `/tmp/sherpa-venv`, KHÔNG đụng repo) — 4 tệp model (`encoder/decoder/joiner/tokens`, `zipformer-
vi-2025-04-20`) đã có sẵn từ phiên trước trong scratchpad; `bpe_vocab.txt` lấy nguyên tệp asset thật
`app/src/main/assets/voice/zipformer-vi-2025-04-20.bpe_vocab.txt` (không phải bản dựng lại).

**Tệp hotword 623 dòng**: repo không cho chạy Gradle/kotlinc ở phép đo này, nên tái dựng bằng **script Python**
đọc lại nguyên văn 4 bộ đăng ký (`ControlRegistry.kt`, `TelemetryRegistry.kt`, `ActionMacros.kt`,
`LauncherActions.kt` — mọi `label/labelEn/short/shortEn/args/argsEn`, chép tay từng dòng) + `SherpaSpokenWords.kt`
(`VERBS`/`ACCENTED` nguyên văn), rồi cài lại đúng luật `SherpaHotwords.normalize`/`phrasesOf` (tách theo
`ALT_SEPARATORS`, bỏ token có số, `MIN_LEN=2`, HOA). Script:
`build_hotwords.py` (scratchpad phiên này). **Độ khớp**: sinh ra đúng **623 dòng** — khớp CHÍNH XÁC con số đã đo
trên xe ở bảng §6.1 — và có đủ `PIN`/`DỪNG`/`KÍNH`/`KÍNH TRƯỚC TRÁI`/`NHẠC` như dòng cuối bảng đó. Số dòng trùng
tuyệt đối là bằng chứng gián tiếp mạnh rằng tái dựng khớp bản 1.64 thật (không phải chỉ "gần đúng"); rủi ro còn
lại duy nhất là **thứ tự xuất hiện** giữa hai nhãn hiếm khi trùng ký tự sau chuẩn hoá — không ảnh hưởng phép đo
vì `LinkedHashSet` khử trùng theo NỘI DUNG, không theo thứ tự.

**Tham số recognizer** — khớp `VoiceRecognizer.kt`/`SherpaModelCatalog.kt`: `decoding_method=modified_beam_search`,
`hotwords_score=3.0`, `modeling_unit=bpe`, `bpe_vocab=<asset thật>`, `num_threads=2`, `max_active_paths=4`,
`sample_rate=16000`, `feature_dim=80`. Chạy `hotwords_score=5.0` thêm ở CỘT RIÊNG, chỉ để tham khảo (đề bài yêu
cầu — không phải đề xuất đổi hằng số).

**Ma trận** — 4 tệp hotword × 5 WAV, ở `hotwords_score=3.0` (script `run_matrix.py`, log đầy đủ
`matrix_full_stdout.log`):

| Tệp hotword | w09 `dừng nhạc` | w12 `xem pin` | w22 `pin còn bao nhiêu` | w04 `mở kính trước trái` | w21 (canh) |
|---|---|---|---|---|---|
| (không hotword) | ✗ *"rừng nhạc"* | ✗ *"xem tin"* | ✗ *"còn bao nhiêu"* | ✗ *"mở kín trước trái"* | ✓ |
| chỉ 3 dòng `PIN`/`XEM PIN`/`DỪNG NHẠC` | **✓ đúng** | **✓ đúng** | ✗ *"còn bao nhiêu"* | ✗ *"mở kín trước trái"* | ✓ |
| **đủ 623 dòng (1.64 thật)** | ✗ *"rừng nhạc"* | ✗ *"xem tin"* | ✗ *"còn bao nhiêu"* | ✓ đúng | ✓ |
| 623 dòng **+ thêm** `XEM PIN`/`DỪNG NHẠC` | ✗ *"rừng nhạc"* | ✗ *"xem tin"* | ✗ *"còn bao nhiêu"* | ✓ đúng | ✓ |

Tham khảo `hotwords_score=5.0` (KHÔNG phải đề xuất, chỉ để thấy xu hướng):

| Tệp hotword | w09 | w12 | w22 | w04 |
|---|---|---|---|---|
| 3 dòng | ✓ đúng | ✓ đúng | ✗ | ✗ *"mở kín trước trái"* |
| 623 dòng | ✓ đúng (đổi so với 3.0) | ✗ *"xem tin"* (KHÔNG đổi) | ✗ | ✓ đúng |
| 623 + 2 cụm | ✓ đúng | ✗ *"xem tin"* | ✗ | ✓ đúng |

**Kết luận [ĐO]** — tách rõ theo §2, KHÔNG trộn cơ chế với quy kết:

1. **[ĐO]** Ở CÙNG `hotwords_score=3.0`, danh sách **3 dòng** sửa được `w09`/`w12` mà danh sách **623 dòng ĐANG
   CHẠY TRÊN XE** thì KHÔNG (hàng 2 vs hàng 3 của bảng trên) ⇒ biến số quyết định KHÔNG PHẢI "hotword đó có tồn
   tại trong tệp hay không" (`PIN`/`DỪNG` có mặt ở cả hai) mà là **kích thước/mật độ của cả tệp** — đúng giả
   thuyết (b).
2. **[ĐO]** Đây là bằng chứng **bác bỏ một phần** cách đọc rubric gốc của §6.3 ("(1) sai mà (2) đúng ⇒ vấn đề là
   độ dài cụm ⇒ thêm cụm động từ + đối tượng"): hàng 4 của bảng **đã làm đúng việc đó** — thêm chính hai cụm
   verb+object `XEM PIN`/`DỪNG NHẠC` vào NGUYÊN tệp 623 dòng — và **không sửa được gì cả** (giống hệt hàng 3).
   Nếu nguyên nhân thuần là "cụm quá ngắn" (giả thuyết a), thêm đúng cụm dài hơn phải sửa được ngay khi nó đã có
   mặt trong tệp — nhưng không. ⇒ **chỉ thêm cụm dài hơn KHÔNG đủ** một khi tệp đã lớn; phần thắng của `w04`
   (cụm 3 từ `KÍNH TRƯỚC TRÁI`, đã có sẵn trong 623 dòng từ 1.64) cho thấy cụm dài *có* lợi thế kéo hơn cụm ngắn
   **trong cùng một tệp lớn** (ủng hộ một phần giả thuyết a như một hiệu ứng PHỤ), nhưng lợi thế đó không đủ
   thắng hiệu ứng loãng của (b) cho `xem pin`/`dừng nhạc`.
3. **[ĐO]** Ở mức tham khảo `hotwords_score=5.0`: nâng score cho NGUYÊN tệp 623 dòng sửa được `w09` (không đổi
   với 3 dòng) nhưng **vẫn không sửa `w12`** — nâng đều một hằng số bù được MỘT PHẦN hiệu ứng loãng, không hết,
   và bù không đều giữa các câu (không phải hướng vá đáng tin — đúng lý do đề bài xếp mục này là tham khảo).

⇒ **[SUY] hướng vá đúng theo bằng chứng**: (b) là nguyên nhân CHÍNH đo được, không phải (a) đơn thuần. Thêm cụm
động từ+đối tượng vào `SherpaSpokenWords` (hướng rubric gốc đề xuất cho nhánh "(1) sai, (2) đúng") **đã được đo
trực tiếp là không đủ** — vì nó vẫn nằm trong cùng tệp 623 dòng bị loãng. Hướng cần thử (CHƯA làm, ngoài phạm vi
phép đo này — đổi hành vi cần spec + approve theo CLAUDE.md §1):
   - giảm kích thước tệp hotword đưa vào MỘT phiên nghe (vd chỉ bias các cụm liên quan tới ngữ cảnh đang mở, thay
     vì đổ nguyên 4 bộ đăng ký mỗi lần), hoặc
   - tách hai tầng hotword: một tệp NHỎ ưu tiên cao (động từ + danh từ lõi hay dùng nhất, kiểu tệp 3 dòng đã đo ở
     đây) cộng một tệp lớn ưu tiên thấp hơn cho phần còn lại — nếu sherpa-onnx hỗ trợ trọng số khác nhau theo
     dòng (`hotwords_file` chỉ nhận MỘT `hotwords_score` cho cả tệp ở bản 1.13.8 — [ĐO] `OfflineRecognizer.
     from_transducer.__doc__`; muốn trọng số riêng theo cụm phải kiểm API `boost` per-phrase nếu tồn tại ở bản
     mới hơn, hoặc build FST hotword thủ công — CHƯA kiểm, ghi vào Open Questions).
Không kết luận thêm gì về score 5.0 — đó là tham khảo, không phải đề xuất theo yêu cầu đề bài.

**Script + log** (scratchpad phiên đo, không phải trong repo): `build_hotwords.py`, `run_matrix.py`,
`matrix_full_stdout.log`, `hotwords_full_623.txt`, `hotwords_3lines.txt`, `hotwords_623_plus2.txt`.

#### [ĐO host 2026-09-16] — kết luận "loãng" ở trên là SAI một phần: biến số thật là **TỪ RỜI**, không phải kích thước

Cùng máy/model/tham số như phép đo 09-15 (sherpa-onnx 1.13.8 pip, `hotwords_score=3.0`, beam 4), nhưng chạy **cả 25
WAV** thay vì 5, và tách hẳn hai biến: kích thước tệp ↔ hình dạng dòng (từ rời / cụm). Số = câu nghe đúng nguyên văn
/25. Log nguyên văn: scratchpad phiên `matrix2..6_stdout.log` (script `run_matrix2..6.py`, tệp `hw*_*.txt`);
bản chép vào repo: `scripts/voice/hotword-matrix.py`.

**Ma trận 2 — chỉ đổi kích thước, giữ hình dạng cũ (nhãn/động từ RỜI):**

| tệp | dòng | đúng/25 | w04 `mở kính trước trái` | w09 `dừng nhạc` | w12 `xem pin` | w13 `tăng âm lượng` |
|---|---|---|---|---|---|---|
| không hotword | 0 | 17 | ✗ | ✗ | ✗ | ✗ |
| 1.64 thật | 623 | 19 | ✓ | ✗ | ✗ | ✓ |
| chỉ dòng có dấu (bỏ 283 dòng nhãn EN) | 340 | 19 | ✓ | ✗ | ✗ | ✓ |
| chỉ `SherpaSpokenWords.ALL` (động từ + cách nói) | 144 | 18 | ✓ | ✗ | ✗ | ✗ |
| chỉ 39 động từ | 39 | 17 | ✗ | ✗ | ✗ | ✗ |
| hai tầng per-line score (core `:3.0`, còn lại `:1.0`) | 347 | 19 | ✓ | ✗ | ✗ | ✓ |

⇒ Thu nhỏ tệp **không** sửa w09/w12. "Loãng" (giả thuyết b ở §6.3) **bị bác**. Tệp 3 dòng hôm qua thắng không phải
vì nhỏ — mà vì nó chứa **cụm** `XEM PIN` / `DỪNG NHẠC`.

**Ma trận 3 — cụm động từ + đối tượng:**

| tệp | dòng | đúng/25 | w04 | w09 | w12 | w13 |
|---|---|---|---|---|---|---|
| 3 dòng `PIN` · `XEM PIN` · `DỪNG NHẠC` | 3 | 19 | ✗ | ✓ | ✓ | ✗ |
| 39 động từ rời + 2 cụm | 41 | 17 | ✗ | ✗ | ✗ | ✗ |
| 144 + 2 cụm | 146 | 18 | ✓ | ✗ | ✗ | ✗ |
| 340 + 2 cụm | 342 | 20 | ✓ | ✗ | ✓ | ✓ |
| 623 + 2 cụm | 625 | 19 | ✓ | ✗ | ✗ | ✓ |
| **chỉ cụm sinh generic** (động từ theo loại × nhãn VN), **0 từ rời** | 756 | **21** | ✓ | ✓ | ✓ | ✓ |
| 340 + cụm generic | 1093 | 20 | ✓ | ✗ | ✓ | ✓ |
| cụm generic + cụm cho cách nói đời thường | **1440** | **21** | ✓ | ✓ | ✓ | ✓ |
| "ngữ cảnh" mô phỏng: 39 động từ rời + cụm cho 10 nhãn | 114 | 17 | ✗ | ✗ | ✗ | ✗ |

⇒ Tệp **lớn nhất** (1440 dòng, toàn cụm) tốt nhất; tệp 114 dòng có động từ rời tệ nhất.

**Ma trận 4 — tách "từ rời":**

| tệp | dòng | đúng/25 | w04 | w09 | w12 | w13 |
|---|---|---|---|---|---|---|
| 340 **bỏ mọi dòng 1 từ** + 2 cụm | 297 | **21** | ✓ | ✓ | ✓ | ✓ |
| 340 bỏ chỉ động từ rời + 2 cụm | 325 | 20 | ✓ | ✗ | ✓ | ✓ |
| cụm generic + 8 danh từ rời (`PIN` `NHẠC` `KÍNH`…) | 764 | 20 | ✓ | ✗ | ✓ | ✓ |
| cụm generic + 39 động từ rời | 775 | **17** | ✗ | ✗ | ✗ | ✗ |
| cụm generic + `PIN CÒN BAO NHIÊU` | 758 | 21 | ✓ | ✓ | ✓ | ✓ (w22 vẫn ✗) |

**Cơ chế** (đọc source sherpa-onnx v1.13.8 `sherpa-onnx/csrc/context-graph.cc`, `ContextGraph::ForwardOneStep`): khi
tới nút `is_end` (khớp trọn một hotword) ở chế độ non-strict, hàm trả `score + output_score − node_score` và trạng
thái **về `root_`**. Hệ quả đo được: (a) `XEM` rời là tiền tố của `XEM PIN` — khớp xong `XEM` là về gốc, cụm dài
không bao giờ được cộng đủ; (b) `NHẠC` rời đứng cuối cộng +3 cho **cả đường sai** *"RỪNG NHẠC"*, biên lợi thế của
*"DỪNG NHẠC"* (+6) so với đường sai co còn 3 và thua âm học; (c) cùng lẽ, **cụm ngắn là tiền tố của cụm dài** cũng
chặn cụm dài (ma trận 6 dưới). `csrc/utils.cc` `EncodeHotwords` nhận per-line score `:x` (ma trận 2 hàng cuối chạy
được) — không cần dùng.

**Ma trận 5/6 — đúng tệp Kotlin sinh** (`SherpaBiasing.hotwordsFile()` dump từ `SherpaBiasingCoverageTest`):

| tệp | dòng | đúng/25 | w19 `chế độ lái thể thao` | `createStream(hotwords)` host |
|---|---|---|---|---|
| 1.64 thật (623) | 623 | 19 | ✓ | 1,5 ms |
| Kotlin cụm, còn nhãn trần nhiều từ | 2057 | 20 | ✗ *"CHẾ ĐỘ LÁI"* | 6,6 ms |
| Kotlin cụm **bỏ dòng tiền-tố-của-dòng-khác** (bản ship) | 1902 | **21** | ✓ | — |
| chỉ cụm bắt đầu bằng động từ | 1770 | 21 | ✓ | — |
| chỉ cụm động từ + bỏ tiền tố | 1638 | 21 | ✓ | — |

Chọn luật **bỏ tiền tố** vì nó suy thẳng từ cơ chế (a)/(c), không phải danh sách động từ chép tay. Canh
over-trigger: w21 *"hôm nay trời đẹp quá"* ✓ ở **mọi** tệp (kể cả 2057 dòng). Chi phí mỗi phiên nghe 6,6 ms host ⇒
×20 trên xe vẫn < 150 ms ⇒ **không cần** thu nhỏ theo hồ sơ/ngữ cảnh (ý ban đầu của V-HOTWORD-CTX).

**[ĐO máy ảo, bản 1.65 (66) vehicleTest, `voice-e2e.sh --only wav`]**: T2 **22/25** nghe đúng nguyên văn (1.64: 20/25) —
w04 · w09 · w12 · w13 · w19 đều ✓ và ra đúng ý định (`Read · Xem Pin (SOC)` · `Media · Dừng nhạc` · `Control · Chế độ
lái: Thể thao`); 3 ca ≈ còn lại đúng là w07 · w22 · w24 (w11 *khóa* được bộ so khớp coi là đúng). Tệp hotword thật
trên máy: 1902 dòng, `biasing=true` (logcat `KachiVoiceEngine`).


**[ĐO host 2026-09-16 · giọng NHANH]** — anh em trên xe (1.66): *"phải đọc chậm, đọc nhanh nó không hiểu"*. Thử trên host: 25 câu `say -v Linh -r 230`
(nhanh hơn ~30 % so với mặc định ~175) × 2 model, cùng tệp hotword ship (1933 dòng): **int8 21/25 · fp32 20/25** (fp32 rớt thêm
w15 `bật sưởi ghế` → *"bật ruồi ghế"*); không hotword: 16/25 (mặc định là 17). ⇒ tốc độ TTS **không** tái hiện được lỗi trên xe
⇒ vấn đề nằm ở **giọng thật/ngữ âm vùng/nói liền** mà TTS không mô phỏng được; muốn sửa phải **thu WAV thật từ xe** (bản 1.67:
nút "Xuất nhật ký voice" kèm WAV) rồi chạy lại ma trận trên đúng WAV đó. Không chỉnh beam/score theo TTS.

**Ngoài tầm hotword** (4 ca còn lại, ở mọi tệp): w22 `pin còn bao nhiêu` → *"còn bao nhiêu"* (thêm 300/600 ms im
lặng đầu ⇒ *"TIN"* / *"PRAKIN"* — mô hình nghe sai âm *"pin"* ở đầu câu của giọng TTS, không phải bias); w07
`Bitexco` · w24 `Waze` (tên riêng/EN); w11 *"khoá"* → *"KHÓA"* (chính tả cũ/mới, `VoiceLexicon.deaccent` gộp về
`khoa` ⇒ ý định vẫn đúng).

### 6.4 Còn lại sau lượt 2

| Việc | Trạng thái |
|---|---|
| L3 phần `pin`/`dừng` (w09 · w12 · w22) | **✅ vá ở 1.65** — kết luận "loãng" 09-15 bị [ĐO host 09-16] bác; gốc là **từ rời/tiền tố** trong tệp hotword (§6.3 phần [ĐO host 2026-09-16]); spec `kachi-voice-hotword-phrases.html`; máy ảo 20 → **22/25**. Riêng w22 còn (lỗi âm học "pin" đầu câu, ngoài tầm hotword) |
| L7 bố cục bằng giọng nói (t59/t60) | **✅ xong ở voice pha 2 (2026-09-16)** — owner duyệt; spec `kachi-voice-command.html` **L7**; ba ca t59 · t60 · t60b PASS, xem **§7** |
| w07 `Bitexco` · w24 `Waze` (tên riêng / tên app tiếng Anh) | **còn** — thuộc lỗ *"model VN không phát ra token tiếng Anh"* (§3 L6 + eval 09-14 §4), không phải lỗi mã |
| t29 *"chạy gói …"* | **không sửa** — lỗi của bộ ca (L9), giữ nguyên để lượt sau không sửa nhầm sản phẩm |
| Transport nhạc với **phiên thật** · mic thật · giọng thật | vẫn [CHƯA ĐO] — xem §4 |

### 6.5 Mã đã đổi trong 1.64 (để lượt sau lần ngược được)

`SherpaHotwords` (dấu câu = ngắt từ, chữ số bỏ theo **token**, `phrasesOf` trả nhiều hotword cho một nhãn) ·
`SherpaSpokenWords` (**mới**: dạng CÓ DẤU của `VoiceSynonyms` + động từ) · `SherpaBiasing` ·
`TestBridgeSettle` (**mới**, `:core`: chờ-theo-việc cho `say`) · `KachiTestBridge.runSay` ·
`VoiceIntentParser` + `VoiceTailClause` (**mới**: mệnh đề đuôi + cách gọi app) · `VoiceIntent` (`APP_CLOSE` ·
`DROPPED_CLAUSE` · `OpenApp.appKey`) · `VoiceReply` · `VoiceDispatcher` (`runMedia` tách nhánh PLAY) ·
`MediaBridge` (interface `MediaTransport`) · `VoiceSynonyms.APP_TARGETS` (thêm cách nói *"bản đồ"*).
Bài canh mới: `SherpaBiasingCoverageTest` · `TestBridgeSettleTest` · `VoiceE2EFix0915Test` ·
`VoiceMediaOpenAppTest`. [ĐO] `:core` 1 899 ca / `:app` 955 ca — **0 đỏ**.

---

## 7. LƯỢT 3 — **voice pha 2** trên bản `vehicleTest` 1.65 (66) · [ĐO] 2026-09-16

> Thay đổi đo ở lượt này: **T8** gói giọng ĐỌC tỉa 13 tệp (tải từng tệp + side-load cây thư mục) · **T9** hai công
> tắc R4 · **T10/R5** đọc lại giá trị THẬT sau khi ghi · **OQ4** đọc xong câu hỏi xác nhận rồi mới mở micro ·
> **L7** bố cục bằng giọng nói. Spec: `docs/specs/kachi-voice-feedback.html` + `kachi-voice-command.html`.

### 7.1 Lệnh tái lập (nguyên văn)

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g \
  ./gradlew :core:test :app:testDebugUnitTest :app:assembleVehicleTest --max-workers=2
PATH="$HOME/Library/Android/sdk/platform-tools:$PATH" bash scripts/emulator/voice-e2e.sh \
  --serial emulator-5554 --apk app/build/outputs/apk/vehicleTest/app-vehicleTest.apk --only say --out <out>
```

### 7.2 Kết quả

| Đo | Trước (1.65, §6) | Sau (pha 2) |
|---|---|---|
| `:core` (đếm từ XML) | 1 899 ca · 0 đỏ | **1 928 ca · 0 đỏ** (+29) → **1 932** sau lượt soát (+4, `VoiceConfirmAnswerTest`) |
| `:app` (đếm từ XML) | 955 ca · 0 đỏ | **965 ca · 0 đỏ** (+10) |
| T1 `say` | 66/67 PASS | **67/68 PASS** — ca đỏ vẫn đúng **t29** |
| Ca bố cục t59 · t60 | **FAIL** (`-`/`-`, chưa có tính năng) | **PASS** cả hai, + **t60b** mới cũng PASS |

- **t29** (*"chạy gói mở cửa + đèn đọc"*) — **không đổi, không sửa**: đây là **lỗi của bộ ca** (L9 ở §3), giữ
  nguyên đúng như quyết định 09-15 để lượt sau không sửa nhầm sản phẩm.
- **Lượt soát Opus cùng ngày** chạy lại đúng lệnh trên: **T1 67/68**, ca đỏ vẫn đúng `t29` ⇒ 5 bản vá của lượt soát (xem `kachi-voice-feedback.html` §10 Pass 2) **không làm hồi quy ca nào**. Lưu ý: E2E **không chạm tới** hai quyết định C1/C2 — `auto_confirm` của cầu kiểm thử không đi qua `VoiceSession.confirm`, và máy ảo không có giọng nào ⇒ cả hai vẫn thuộc cột 🚗 (§7.3).
- Ba ca bố cục kiểm **tác dụng phụ thật**: cột `side` có kiểu mới `preset:<TEN>`, đọc `state.layout.preset` —
  cùng nguồn mà màn hình vẽ, không đoán qua ảnh chụp. [ĐO] `QUAD` · `TWO_COL` · `TWO_ROW` đúng cả ba.

### 7.3 Ba thứ máy ảo **KHÔNG** đo được (và vì sao) — chuyển sang playbook §6f

| Hạng mục | Vì sao máy ảo mù | Chốt ở đâu |
|---|---|---|
| **R5 nhánh "lệch"** | máy ảo dùng `NoCar` ⇒ `readStep` trả `null` ⇒ luôn đi nhánh *"không đọc được"*; không có HAL để trả một con số khác | playbook §6f(c) + `VoiceStepReadbackTest` (7 ca JVM, có ca *xe chậm một nhịp*) |
| **OQ4 đọc-xong-rồi-nghe** | máy ảo **không có giọng nào** (`app_voices` rỗng — Đ2 của spec) ⇒ `speaker.available()` = false ⇒ mở micro ngay như 1.65; `auto_confirm` của bridge cũng không đi qua `VoiceSession.confirm` | playbook §6f(d) + `VoiceSpeakerDoneContractTest` (hợp đồng `onDone` luôn gọi) |
| **T8 tải qua mạng** | 13 asset **chưa được đăng** lên GitHub Release (TODO owner) | playbook §6f(a): side-load cây thư mục, `state.tts.offline_pack_ready` |

### 7.4 Bốn trường mới của `state.tts` (đọc được **ngay cả khi chưa nói câu nào**)

```
offline_pack_ready   gói giọng ĐỌC đã đủ 13 tệp trên đĩa chưa (đọc từ ĐĨA, không từ ảnh chụp phiên nói)
offline_pack_dir     đường dẫn TUYỆT ĐỐI của thư mục gói — để người cầm adb chép đúng chỗ, không phải đoán
speak_replies        công tắc R4 "Đọc phản hồi bằng giọng"   (mặc định true)
prefer_offline       công tắc R4 "Ưu tiên giọng offline"     (mặc định false)
```

### 7.5 Mã đã đổi ở lượt này (để lượt sau lần ngược được)

**Mới**: `core/…/voice/VoicePack.kt` · `core/…/voice/VoiceLayouts.kt` · `app/…/VoiceTargetDispatch.kt` ·
`app/…/voice/VoiceFreeTail.kt`.
**Sửa**: `SherpaTtsCatalog` (13 tệp ghim, gỡ hẳn `archive*`/`needsArchiveExtract`/`rootInArchive`) ·
`SherpaModelCatalog` (`SherpaModel : VoicePack`) · `VoiceModelStore` (nhận `VoicePack`, `requireSafe` nhiều đoạn,
staging theo họ gói) · `VoiceModelSideload` (đường dẫn nhiều đoạn) · `VoiceModelSettings` (4 hàng: 2 gói + 2 công
tắc) · `Prefs` + `ClusterNavBridgeKeys` (2 khoá mới) · `SettingsCatalogEntries`/`SettingsCatalogClusterNav`/
`ProfileScope` (khai khoá + phạm vi) · `LauncherPorts` + `CarControlAdapter` (`readStep`) · `VoiceReply`
(`doneActual` · `layoutNotHere` · nhánh `Layout`) · `VoiceDispatcher` (`sayStepResult` · `onLayout`) ·
`VoiceIntent` (`Layout`) · `VoiceIntentParser` (bước b½) · `VoiceSpeaker`/`AndroidTtsSpeaker`/`SherpaTtsSpeaker`/
`VoiceSpeakerRouter` (`speak(text, onDone)`) · `VoiceSession` (`askAloudThenListen` · gác R4) · `VoicePhrases` +
`SherpaPhraseHotwords` (cụm bố cục) · `TestBridgeState` (4 trường) · `TestBridgeHooks`/`KachiHomeWiring`/
`KachiHomeActivity`/`VoiceTextConsole` (nối `onLayout`).
**Bài canh mới**: `VoiceLayoutParseTest` · `VoiceReplyActualTest` · `VoiceSpeakerDoneContractTest` ·
`VoiceStepReadbackTest` + 3 bài mới trong `VoiceCommandWiringContractTest`.

---

## 8. [ĐO host 2026-09-16] VOICE-DATA — corpus tự dựng thay cho việc "chờ xe"

Owner 2026-09-16: *"cái voice phải nói chậm, nhận diện khó này kia, là đã fix chưa, có dùng AI LLM này để
generate ra nhiều nhiều các mẫu câu, vùng miền, tiếng anh kiểu tiếng việt này kia để đảm bảo nhận dạng tốt
hơn không? tự xây data kiểu vậy thì may ra mới ổn, chứ chờ xe cũng ko biết chờ gì"*.

Toàn bộ lượt này chạy **trên host**, không gradle, không máy ảo, không đụng một dòng Kotlin nào. Số chi tiết:
[`voice-mishear-2026-09-16.md`](voice-mishear-2026-09-16.md). Đợt thu giọng người:
[`voice-recording-campaign-2026-09-16.md`](voice-recording-campaign-2026-09-16.md).

### 8.1 Dựng được gì

| Thứ | Số | Ghi chú |
|---|---|---|
| `scripts/voice/data/variants.tsv` | **4 484 câu · 182 mã** (24,6 câu/mã, mọi mã ≥ 12) | LLM soạn từ vựng vùng miền + khuôn câu; `gen-variants.py` nở tất định từ `registry.json` |
| WAV corpus `/tmp/kachi-voice-corpus` | **1 874 WAV** từ 431 câu (83 tier A · 348 tier B) | `say -v Linh` 140/180/220/260/300 · piper `vi_VN-vais1000-medium` 0,9/1,0/1,3 · nén thời gian 1,3×/1,6× |
| `data/aliases-proposed.tsv` | **542 dòng** (101 `synonym` · 23 `hotword` · 418 `clarify`) | CHỈ từ chuỗi model thật sự in ra |
| `data/hotwords-proposed.txt` | **448 dòng** | HOA có dấu, ≥ 2 từ, không đụng tiền tố với 1 757 dòng đang ship |
| Thời gian máy | TTS ~24 ph · giải mã 30 s/cấu hình (5 cấu hình ≈ 2,5 ph) | giải mã RTF ≈ 0,015 — rẻ hơn TTS hai bậc |

Piper chạy **qua chính `sherpa_onnx.OfflineTts`** với gói `voice/tts/piper-vi_VN-vais1000-medium/` đã có trong
repo ⇒ **không** phải cài `piper-tts`, không thêm phụ thuộc nào.

### 8.2 *"Phải nói chậm"* — [ĐO] **BÁC**, và ngược lại là đằng khác

| giọng · tốc độ | `none` | tệp đang ship | +đề xuất |
|---|---|---|---|
| linh 140 (chậm) | 38,6% | 43,4% | 49,4% |
| linh 180 (thường) | 50,3% | **62,9%** | 65,2% |
| linh 220 | 39,8% | 44,6% | 49,4% |
| linh 260 (nhanh) | 49,9% | **63,8%** | 65,9% |
| linh 300 (rất nhanh) | 38,6% | 42,2% | 44,6% |

Đọc **chậm hơn** (140) KHÔNG tốt hơn đọc thường (43,4% vs 62,9%), và 260 ≈ 180. Tức cảm giác *"phải nói chậm
mới nhận"* **không khớp** với cách mô hình hành xử ở tầng này. [SUY] Nó nhiều khả năng là chuyện của **điểm
ngắt câu** (`VoiceEndpointer` cắt sớm khi người ta ngập ngừng) hoặc mic trên xe, chứ không phải tốc độ đọc —
và đó là thứ **chỉ đo được trên xe**, không đo được ở đây.

⚠ Cột 140/220/300 chỉ có 83 WAV (tier A) so với 431 WAV ở 180/260, nên đừng so ngang tuyệt đối giữa hai
nhóm; so trong cùng nhóm (140 vs 220 vs 300, và 180 vs 260) thì kết luận trên vẫn đứng.

### 8.3 Đúng ba câu tester báo sai — nghe lại bằng máy, 10–11 lần mỗi câu

| Câu | Đúng nguyên văn | Model hay nghe ra |
|---|---|---|
| `lọc ngay` | 8/11 | *"đọc ngày"* · *"học này"* |
| `lọc bụi` | 7/10 | *"các bụi"* |
| `điều hoà` | **10/10** | *"điều hòa"* — **khác chỗ đặt dấu, không phải nghe sai** |
| `bật điều hoà` | **10/10** | *"bật điều hòa"* |
| `Google Map` | 5/10 | *"oh em at"* · *"d ot"* |
| `Google` (đối chứng tester nói ĐƯỢC) | **0/10** | *"goovel"* · *"ruvel"* |
| `bật đèn đọc` (đối chứng OK) | 10/11 | — |
| `mở YouTube` (đối chứng OK) | 7/11 | — |

Hai điều rút ra:
- *"điều hoà"* **không** phải lỗi nghe. Mô hình nghe ra *"ĐIỀU HÒA"* (dấu kiểu mới) trong khi cả dự án viết
  *"điều hoà"* (kiểu cũ). Chỗ hỏng nằm ở **so khớp chữ**, không ở tai. Xem 8.5.
- `Google` đứng một mình 0/10 là **[SUY], nhiều khả năng là lỗi của TTS**: `say -v Linh` đọc một từ tiếng
  Anh trơ trọi không giống người Việt đọc nó. Đây đúng là chỗ phải để giọng thật trả lời (8.6).

### 8.4 Hotword đề xuất — **+55 câu, 0 hồi quy**

| Tệp hotword | 1 899 WAV | 25 WAV của ma trận cũ |
|---|---|---|
| `none` | 729 (38,4%) | 18/25 |
| đang ship (1 757 dòng) | 935 (49,2%) | **22/25** |
| ship + 448 dòng đề xuất (2 205) | **990 (52,1%)** | **22/25** |

Chỗ ăn điểm đúng là chỗ tester kêu: `app` 12,6% → **20,6%** · kiểu nói `tieng_anh_viet` 11,3% → **21,8%** ·
`than_mat` 15,4% → 19,9% · `macro` 61,9% → **81,0%** · giọng Nam 21,8% → 25,1%. Một chỗ **tụt**: `media`
57,1% → 52,4% (21 WAV ⇒ đúng **một** ca; mẫu quá nhỏ để kết luận, phải đo lại nếu ship).

**Một dòng duy nhất suýt gây hồi quy** — `YOUTUBE MUSIC` làm w10 (*"đưa YouTube vào ô số hai"*) tụt xuống
*"ĐƯA YOUTUBE"*, mất sạch đuôi. Cơ chế là **mặt sau của luật tiền tố**: giải mã xong `YOUTUBE`, đồ thị ngữ
cảnh đang đứng GIỮA cụm `YOUTUBE MUSIC` nên cộng điểm cho `MUSIC` và trừ mọi đường khác (kể cả `VÀO Ô SỐ
HAI`). Đã thành **luật máy** trong `propose-hotwords.py`: *cụm bắt đầu bằng một tên app đứng-một-mình thì
bỏ* — tên app là chỗ KẾT của câu, đặt nó làm đầu cụm là mời lỗi này.

### 8.5 [ĐO] Chỗ đặt dấu — 54/1 757 dòng hotword viết chữ mô hình KHÔNG có

`tokens.txt` của `zipformer-vi` có `▁HÒA` · `▁KHÓA` · `▁KHỎE`, **không** có `▁HOÀ` · `▁KHOÁ` · `▁KHOẺ`. Tệp
hotword đang ship dùng dạng sau ở **54 dòng** (KHOÁ ×24 · HOÀ ×23 · KHOẺ ×7).

⚠ Nhưng giả thuyết *"viết sai kiểu ⇒ hotword vô hiệu"* đã bị **chính phép đo bác**: sửa riêng chỗ đặt dấu cho
**đúng bằng** tệp cũ (935 = 935, 22/25 = 22/25), chỉ nhích ở nhánh `piper 0.9`. BPE vẫn kéo được bằng mảnh
nhỏ. ⇒ **Nên sửa** (đó là chính tả của từ điển mô hình, và nó làm mọi phép so chữ sạch hơn) nhưng **không
được bán như một bản vá cải thiện độ chính xác**. Lưu ý sửa **đúng âm tiết mở**: `HOÀN` · `NGOÀI` · `TOÀN` ·
`THOÁNG` vốn đã đúng và `tokens.txt` cũng ghi như vậy — đổi mù cả cụm là làm hỏng bốn từ đang chạy tốt (đã
mắc và bắt được ngay trong lượt này).

### 8.6 Cái này **KHÔNG** trả lời được, và ai trả lời

| Câu hỏi | Vì sao host không trả lời được | Chốt ở đâu |
|---|---|---|
| Trên xe nghe đúng bao nhiêu % | giọng TTS ≠ giọng người + mic 4 kênh + ồn đường (CLAUDE.md §2) | đợt thu giọng `voice-recording-campaign-2026-09-16.md`, rồi chạy `hotword-matrix.py` trên WAV thật |
| *"Phải nói chậm"* đến từ đâu | tốc độ đọc đã loại (8.2); còn lại là điểm ngắt câu / mic | [CHƯA BIẾT] — cần một lượt xe đọc `state` của `VoiceEndpointer` |
| `createStream` với 2 205 dòng tốn bao lâu trên đầu xe | host 6,9 ms; [SUY] ×20 ⇒ ~140 ms, vượt ngân sách 100 ms của spec | OQ2 của `kachi-voice-hotword-phrases.html` — phải đo trên xe trước khi ship tệp to hơn |
| 101 bí danh đề xuất có làm hiểu nhầm câu khác không | `approx_parse` là **xấp xỉ**, không phải `VoiceIntentParser` | chạy lại bộ `:core` sau khi nhập vào `VoiceSynonyms` |

### 8.7 Chạy lại (một lệnh mỗi bước)

```bash
python3 scripts/voice/gen-variants.py                         # 4 484 câu, tất định
/tmp/sherpa-venv/bin/python scripts/voice/synth-corpus.py     # ~24 ph, 1 874 WAV
/tmp/sherpa-venv/bin/python scripts/voice/mishear-table.py --model <MODEL_DIR> \
    --primary hotwords-phrases.txt --dump /tmp/dump.tsv \
    --hotwords none core/build/hotwords/hotwords-phrases.txt <tệp-thử-khác>
python3 scripts/voice/propose-hotwords.py --dump /tmp/dump.tsv
```
