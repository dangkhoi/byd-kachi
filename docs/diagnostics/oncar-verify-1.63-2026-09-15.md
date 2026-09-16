# Playbook lượt xe — xác nhận bản 1.63 (không mò, mỗi mục ≤ 1-2 lệnh)

- **Ngày viết:** 2026-09-15 · **Xe:** DiLink3 (DL3), Android 10, Sealion 6 · **Bản:** 1.63 (versionCode 64), CHƯA push (remote 1.59).
- **Spec:** `docs/specs/kachi-hal187-cast-remediation.html` · **RE 187:** `hal-binding-remediation-2026-09-15.md`.
- **Nguyên tắc:** mọi thứ làm được off-car đã làm + test xanh. Lên xe chỉ **xác nhận** theo thứ tự dưới. Kết nối: `adb connect <ip-xe>:5555` (nếu `offline` → `adb kill-server; adb start-server; adb connect …`). Bật **chế độ kiểm thử** trong Cài đặt (tự tắt 60 phút) để dùng T-BRIDGE.
- Lệnh bridge: `B='am broadcast -a com.byd.launcher.TEST -p com.byd.launcher'` (PHẢI có `-p`).

---

## 0. Cài + nâng cấp (2 phút) — [ĐO cost một lần của HOME-alias]
```
adb install -r app/build/outputs/apk/release/app-release.apk     # 1.62 → 1.63, giữ data
adb shell dumpsys package com.byd.launcher | grep versionName    # = 1.63
```
- **Kỳ vọng:** sau nâng cấp, bấm Home có thể về **launcher3** (alias HOME mới tắt sẵn, chưa có marker). → Mở Kachi › Cài đặt › Hệ thống › **"Đặt làm màn hình chính"** bấm 1 lần. Quan sát: (a) ROM có hiện **hộp chọn launcher3/Kachi** không (như DuDu)? (b) sau đó bấm Home → Kachi.
```
adb shell "dumpsys package com.byd.launcher | grep -A2 KachiHome"   # alias enabled=true sau khi bấm
adb shell "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME" | grep -i kachi
```
- Ghi: hộp chọn có/không · Home về Kachi có/không.

## 1. Cast qua cụm (P0) — [phải PASS trước khi OTA]
Reboot (nút nguồn vật lý). Bật cast một app (vd GMaps) qua bong bóng/nút cast.
```
adb shell am stack list | grep -E "displayId|ClusterBlack|maps"
adb shell "logcat -d -s SimpleCast | grep -iE 'cluster display|detect|profile 35|-d [0-9]'" | tail -20
```
- **PASS:** task app + `ClusterBlackActivity` ở **display 2** (fission), KHÔNG ở display 1 (`kachi-slot-*`); log "cluster display … → 2" xuất hiện SAU profile 35.
- **FAIL:** nếu log "detect … -1 / bỏ cuộc" nhiều lần → VD fission lên chậm hơn 6 s → dán log, chỉnh `AWAIT_ATTEMPTS`.
- Chụp 1 lần output `DETECT_CMD` mới (có `|virtual:`): `adb shell "dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja|virtual:'"` → lưu vào `car-logs/`.

## 2. `sweep` — quét cạn 187 trong MỘT lệnh (thay bấm tay)
```
adb shell "$B --es cmd sweep"                       # trả tóm tắt + đường tệp
adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./car-logs/   # lấy sweep-<stamp>.json
```
- Đọc JSON: `info[].value` rỗng = vẫn không đọc được (so với danh sách NEEDS-ONCAR); có số = kiểm **đơn vị/scale** với táp-lô (nhiệt cabin, áp lốp kPa, tốc độ vô-lăng…).
- Đây là bằng chứng để chốt ~40 mục NEEDS-ONCAR — không cần mở màn "Kiểm tra từng nút xe".

## 3. Kính "Mở 50%" (T7) — 1 lệnh chốt enum
```
adb shell "$B --es cmd hal --es m setBodyWindowCtrlState --es args 1,4 --ez auto_confirm true"; sleep 5
adb shell "$B --es cmd hal --es m getWindowOpenPercent --es args 1"      # kỳ vọng ≈ 50
```
- PASS ⇒ nút "Nửa" trên dock/CapTest đúng. FAIL (0 hoặc 98) ⇒ enum 4 không phải half trên trim này → sweep 3/5.

## 4. YouTube trong ô (density-lever)
Mở YouTube vào ô, **bấm play** một video.
```
adb shell "logcat -d | grep slot-density"                      # kỳ vọng: dpi=200→199 (598.4dp→601.4dp)
adb shell "dumpsys activity activities | grep -B2 -A6 youtube | grep -iE 'mSizeCompatScale|smallestScreenWidthDp|screenOrientation'"
```
- **PASS:** app đầy khung, KHÔNG co dải dọc giữa; không có `mSizeCompatScale`. (Video 16:9 chỉ phủ kín pixel khi bấm fullscreen — đúng kỳ vọng.)
- **FAIL:** vẫn dải dọc → thử `wm density 192 -d <vd>` rồi play lại; nếu hết ⇒ tăng `thresholdDp` (620). Vẫn không ⇒ cơ chế khác, dán dump.

## 5. GUI-install (HOME-alias) — xác nhận cùng cơ chế DuDu
Chép `app-release.apk` vào USB → cài bằng file manager BYD.
- **PASS:** cài như app thường (không "Fail in installation of desktop apps"). Sau đó bấm "Đặt làm màn hình chính" như mục 0.
- **FAIL:** dán thông báo lỗi + `logcat | grep -i StopLauncher`.

## 6. Nhóm NEEDS-ONCAR ưu tiên (từ RE) — mỗi mục 1 lệnh `hal get`
| Mục | Lệnh | Chốt |
|---|---|---|
| Ambient ×8 (device?) | `hal --es dev BYDAutoSettingDevice --es m <getter>` theo `hal-binding-remediation` | SETTING hay LIGHT; scale 0–100? màu 31? |
| Motor rpm/torque/power | sweep (feature-id zero-hoá) | id thật |
| cell temp ×3 / SOH / batt_temp | sweep | có giá trị? scale |
| trip km/h/kWh | `hal --es dev BYDAutoStatisticDevice --es m getDrivingTimeValue` vs đồng hồ | phút hay giờ |
| regen_level enum | `hal set BYDAutoSettingDevice.setEnergyFeedback 0..3` + `get` | mốc thật |
| lock/door setter | `hal set BYDAutoDoorLockDevice.setDoorLockState 2` → xem `HalWriteProbe` ngoại lệ | có method không |
| engine_code/oil | nổ máy xăng rồi `hal --es dev BYDAutoEngineDevice --es m getEngineCode` | PHEV ngủ? |

## 6b. VOICE "hoàn toàn không work" + LAG — ĐO trước khi đổ cho phần cứng (owner 2026-09-15)
Fact đã đọc từ source (off-car): chạm vào ô đi `injectInputEvent` (**không** fork `input` mỗi sự kiện — `VdAppHost.kt:231`); voice cần quyền **`RECORD_AUDIO` runtime** (`VoiceCapture.kt:46`, thiếu ⇒ tấm chữ báo "thiếu quyền" 8 s rồi im); 2 poller nền qua dadb: `SlotLiveProbe` (1 `am stack list`/nhịp) + SimpleCast watchdog (`am stack list` ~4 s/lần — thấy trong logcat xe). Bisect 3 lệnh:
```
# (a) MỘT lệnh: bridge `state` báo shell_usable + quyền còn THIẾU + voice_model (TestBridgeState:85-95)
adb shell "$B --es cmd state" | tr ',' '\n' | grep -iE "shell_usable|missing|unknown|microphone|voice_model|ready"
#   ⚠ ĐỌC ĐÚNG: `state` KHÔNG in "granted". Nó in `permissions.missing` / `permissions.unknown` (mã
#   `LauncherRequirements.MICROPHONE.id` = "microphone"). VẮNG "microphone" trong hai mảng đó = ĐÃ cấp.
#   `voice_model` chỉ có 2 khoá: `ready` (true/false) + `bytes` — không có khoá "loaded".
#   mic chưa cấp → app TỰ cấp qua pm grant (PermissionPreflight:104) NHƯNG cần kênh shell (dadb loopback) sống:
#   shell_usable=false ⇒ gốc là kênh shell, không phải mic. Cấp tay: adb shell pm grant com.byd.launcher android.permission.RECORD_AUDIO
# (b) bộ nhận dạng + mô hình có chạy không — KHÔNG cần mic: đẩy 1 WAV qua ĐÚNG đường VoiceWavProbe
adb push <câu-mẫu.wav> /sdcard/Download/kachi.wav
adb shell "$B --es cmd wav --es path /sdcard/Download/kachi.wav"     # đọc heard/grammar/probe_error
# (c) phiên nghe thật + log
adb shell "$B --es cmd listen"; adb shell "logcat -d -s KachiVoice" | tail -30
```
- (a) sai ⇒ **gốc là quyền**, không phải phần cứng. (b) nghe được ⇒ mô hình/CPU OK, lỗi ở mic/phím vô-lăng (đọc `AccessibilityForceBind`/key binding). (b) `probe_error` "chưa tải mô hình" ⇒ mô hình **sherpa ONNX** (`zipformer-vi-2025-04-20`) chưa có — xe không internet thì đường tải mạng không bao giờ xong → dùng **side-load** (mục 6c). (b) chậm nhiều giây ⇒ mới nói tới CPU.

**Lag chạm VietMap trong ô** — nghi **chạm lệch do size-compat** (app co lại nhưng toạ độ map cả ô), cùng gốc YouTube, KHÔNG phải lag:
```
adb shell "dumpsys activity activities | grep -B2 -A6 vietmap | grep -iE 'mSizeCompatScale|mBounds|screenOrientation'"
```
- Có `mSizeCompatScale` ⇒ density-lever (mục 4) chữa luôn; không có mà vẫn khó ⇒ đo tiếp `dumpsys gfxinfo com.byd.launcher` (jank) + `top -n 1 -m 10` (CPU ai ăn).

**CPU/độ trễ toàn launcher — 3 số đo, 1 phút:**
```
adb shell top -n 1 -m 12                                   # ai ăn CPU khi Kachi idle (kỳ vọng: không phải com.byd.launcher)
adb shell dumpsys gfxinfo com.byd.launcher | grep -A12 "Janky"   # % khung giật của launcher
adb shell "logcat -d -s SimpleCast | grep -c 'am stack list'"    # số lần poll/phút — nếu cao ⇒ giảm nhịp/đổi sang event
```
Ứng viên tối ưu (chỉ sau khi đo): gộp 2 poller `am stack list` thành 1 + kéo dài nhịp khi không cast; tắt `KachiLog` ghi logcat ra thẻ khi không ở chế độ kiểm thử; đo lại.

## 6c. SIDE-LOAD mô hình voice (xe không internet) — T8, bản 1.63 batch 2
`install()` giờ ưu tiên tệp đặt sẵn, kiểm **cùng sha256+bytes** với đường mạng (`VoiceModelSideload`). Model mặc định `zipformer-vi-2025-04-20`, 4 tệp **phải mang đúng tên đích**: `encoder.onnx · decoder.onnx · joiner.onnx · tokens.txt` (~266 MB; URL + sha256 + cỡ ghim trong `SherpaModelCatalog.ZIPFORMER_VI`).

⚠ **Tên trên HuggingFace KHÁC tên đích** — tải về là `encoder-epoch-12-avg-8.onnx`, phải **đổi tên** thành `encoder.onnx` (tương tự decoder/joiner). Đặt sai tên ⇒ `candidate()` trả `null` ⇒ install im lặng quay về **đường mạng** ⇒ xe không internet thì trông y hệt "side-load không chạy".
```
# 1) trên máy tính — tải + ĐỔI TÊN (HF trả 302 sang CDN ⇒ cần -L)
U=https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20/resolve/main
curl -L -o encoder.onnx $U/encoder-epoch-12-avg-8.onnx
curl -L -o decoder.onnx $U/decoder-epoch-12-avg-8.onnx
curl -L -o joiner.onnx  $U/joiner-epoch-12-avg-8.onnx
curl -L -o tokens.txt   $U/tokens.txt
shasum -a 256 encoder.onnx decoder.onnx joiner.onnx tokens.txt   # so với sha ghim trong SherpaModelCatalog.kt
# 2) tạo thư mục TRƯỚC rồi mới push (adb push nhiều tệp KHÔNG tự tạo thư mục đích)
D=/sdcard/Android/data/com.byd.launcher/files/sherpa/import/zipformer-vi-2025-04-20
adb shell mkdir -p $D
adb push encoder.onnx decoder.onnx joiner.onnx tokens.txt $D/
adb shell ls -l $D          # kỳ vọng ĐỦ 4 tệp, đúng tên, cỡ 261057692/5165084/4104465/25847
# 3) trên xe: Cài đặt › Giọng nói › tải mô hình → install() thấy tệp side-load, KHÔNG chạm mạng
adb shell "$B --es cmd state" | tr ',' '\n' | grep -iE "voice_model|ready|bytes"   # kỳ vọng ready:true
# 4) xong thì trả lại chỗ (bản chép trong máy mới là bản dùng)
adb shell rm -rf $D
```
- **Phải đủ cả 4 tệp.** Thiếu tệp nào thì RIÊNG tệp đó đi đường mạng (trộn theo từng tệp) — xe không mạng ⇒ cả lượt cài hỏng với câu "lỗi mạng &lt;tên tệp&gt;", đó là tệp bị thiếu/sai tên trong thư mục import.
- Cần chỗ trống **~266 MB trong bộ nhớ trong** (chưa kể bản gốc còn nằm trên thẻ) — thiếu thì `install()` báo "máy còn … MB" trước khi chép.
- Sai nội dung ⇒ báo thẳng "side-load … không khớp bản ghim" (không âm thầm rơi về mạng) → chép lại đúng tệp.
- Thanh % chạy theo tổng byte đã chép (chung một bộ đếm với đường mạng) — đứng im nhiều phút mới là bất thường.
- Xong ⇒ chạy lại 6b(b) `wav` để xác nhận nhận dạng chạy trên xe.

## 6d. Thêm cho bản 1.64 (commit `e598925`) — 3 mục on-car mới, mỗi mục 1 lệnh
Cài `app-release.apk` 1.65 (66) (cùng khoá, `install -r`). Mọi thứ dưới đã xanh off-car + E2E emulator (`emulator-voice-e2e-2026-09-15.md` §6).
```
# (a) Phản hồi giọng: nói 1 câu bất kỳ rồi đọc khối tts — vi_status: 0/1 = có giọng vi (Android TTS đọc được);
#     -1 = engine có, thiếu gói (tải là xong); -2 = engine không bao giờ đọc tiếng Việt → cần sherpa offline (pha 2)
adb shell "$B --es cmd state" | tr ',' '\n' | grep -iE "tts|vi_status|engine|offline_voice"
# (b) Sổ địa chỉ: Cài đặt › Dẫn đường › Sổ địa chỉ → thêm "Nhà" có "lat, lng" dán từ Google Maps → nói "về nhà"
#     kỳ vọng: có toạ độ ⇒ VietMap mở đúng điểm (AWAITING_CAR — chưa ai đo VietMap nhận lat/lng thật); không toạ độ ⇒ Google Maps geo:
adb shell "$B --es cmd say --es text 'về nhà'"          # đọc intents/replies; rồi am stack list | grep -iE "vietmap|maps"
# (c) Voice sau vá: "đóng YouTube" phải KHÔNG mở app + câu hướng dẫn; "phát nhạc trên YouTube Music" phải mở app
adb shell "$B --es cmd say --es text 'đóng YouTube'"; adb shell "$B --es cmd say --es text 'phát nhạc trên YouTube Music'"
```
- Biasing/hotword (nhãn 50→0 mất) và nhịp chờ bridge không cần xe — đã đo trên emulator.
- ~~Còn lỗ đã biết: từ đơn `pin`/`dừng` nghe sai do tệp hotword 623 dòng bị LOÃNG~~ — **kết luận "loãng" đã bị bác** ([ĐO host 2026-09-16], `emulator-voice-e2e` §6.3): gốc là **từ rời/tiền tố** trong tệp hotword; vá ở 1.65 (§6e).

## 6e. Thêm cho bản 1.65 (66) — hotword theo CỤM (spec `kachi-voice-hotword-phrases.html`), 1 mục on-car

Máy ảo đã đo 20 → 22/25 (giọng TTS). Trên xe chỉ còn câu hỏi **giọng thật + mic 4 kênh** (OQ2 của spec): nói 3 câu
từng sai ở 1.64, mỗi câu 2 lần, qua nút mic thanh trên; rồi đọc `state` xem `heard`/`intent`:

```bash
# kỳ vọng: "xem pin" → Read · Xem Pin (SOC); "dừng nhạc" → Media · Dừng nhạc; "chế độ lái thể thao" → Control · Chế độ lái: Thể thao
adb shell "$B --es cmd state" | tr ',' '\n' | grep -iE "heard|intent|hotword"
adb shell "run-as com.byd.launcher wc -l files/sherpa/hotwords.txt" 2>/dev/null   # nếu có tệp: kỳ vọng ~1902 dòng (không có ⇒ per-stream, không tệp)
```
- Nếu giọng thật vẫn sai ở đúng 3 câu ấy mà máy ảo đúng ⇒ vấn đề là **âm học** (mic/ồn), không phải hotword — đo tiếp theo §6b (WAV thu từ mic xe → `wav` bridge).

## 6f. Thêm cho **voice pha 2** (1.65 (66) — TTS gói tỉa · công tắc đọc · đọc lại giá trị thật · bố cục bằng giọng)

Spec: `docs/specs/kachi-voice-feedback.html` (T8 · T9 · T10 · OQ4) + `kachi-voice-command.html` (L7). Mọi thứ dưới
đã xanh off-car + E2E máy ảo (`emulator-voice-e2e-2026-09-15.md` §7). Bốn mục, mỗi mục 1–2 lệnh.

### (a) SIDE-LOAD gói GIỌNG ĐỌC (61 MB, 13 tệp) — xe không internet

⚠ **Asset trên GitHub Release CHƯA được đăng** (owner phải tự upload — máy soạn thảo không có `gh`), nên tới lúc
đó nút *Tải* sẽ hỏng fail-safe (404 / sha không khớp, câu lỗi **nói rõ tệp nào**). Đường side-load thì chạy ngay.

Gói giữ **nguyên cây thư mục** — khác gói NGHE (4 tệp phẳng, phải đổi tên): ở đây **không đổi tên gì cả**, chỉ
chép nguyên thư mục. Nguồn: giải nén `vits-piper-vi_VN-vais1000-medium.tar.bz2` rồi **xoá bớt** `espeak-ng-data`
còn đúng 11 mục dưới đây (bảng ghim đầy đủ: `SherpaTtsCatalog.PIPER_VI_VAIS1000`).

```bash
# 1) trên máy tính — dựng cây gói TỈA (13 tệp, 63 877 499 byte)
#    giữ: espeak-ng-data/{intonations, phondata, phondata-manifest, phonindex, phontab, vi_dict}
#         espeak-ng-data/lang/aav/{vi, vi-VN-x-central, vi-VN-x-south}
#         MODEL_CARD, tokens.txt, vi_VN-vais1000-medium.onnx, vi_VN-vais1000-medium.onnx.json
find . -type f | sort | while read f; do printf '%s\t%s\t%s\n' "${f#./}" "$(stat -f%z "$f")" "$(shasum -a 256 "$f"|cut -d' ' -f1)"; done
#    → so từng dòng với bảng ghim trong SherpaTtsCatalog.kt (lệch 1 byte là install từ chối)

# 2) chép NGUYÊN CÂY sang xe (adb push thư mục giữ cấu trúc con)
D=/sdcard/Android/data/com.byd.launcher/files/sherpa/import/piper-vi_VN-vais1000-medium
adb shell mkdir -p $D
adb push ./espeak-ng-data $D/
adb push MODEL_CARD tokens.txt vi_VN-vais1000-medium.onnx vi_VN-vais1000-medium.onnx.json $D/
adb shell "find $D -type f | wc -l"        # kỳ vọng 13

# 3) trên xe: Cài đặt › Hệ thống & quyền › Nâng cao › "Giọng đọc offline (tại máy)" → bấm Tải
#    (install() thấy đủ 13 tệp side-load ⇒ KHÔNG chạm mạng, vẫn băm sha256 từng tệp)
adb shell "$B --es cmd state" | tr ',' '\n' | grep -iE "offline_pack|speak_replies|prefer_offline"
#    kỳ vọng: offline_pack_ready:true · offline_pack_dir:/data/user/0/…/files/sherpa-tts/piper-vi_VN-vais1000-medium

# 4) xong thì trả lại chỗ trên thẻ (bản chép trong máy mới là bản dùng)
adb shell rm -rf $D
```
- Cần **~101 MB** trống trong bộ nhớ trong (61 MB gói + 40 MB lề của `install`); thiếu ⇒ báo "máy còn … MB" TRƯỚC khi chép.
- Sai nội dung ⇒ "side-load &lt;tên&gt; không khớp bản ghim" — **không** âm thầm rơi về mạng.
- Thư mục `import` giữ **nguyên đường dẫn tương đối**; đặt phẳng (`vi` ra ngoài `lang/aav/`) ⇒ tệp đó đi đường mạng ⇒ xe không mạng thì cả lượt hỏng.

### (b) Công tắc R4 + câu trả lời có tiếng

```bash
# Hai công tắc ở ngay dưới hai hàng gói: "Đọc phản hồi bằng giọng" (mặc định BẬT) · "Ưu tiên giọng offline" (TẮT)
adb shell "$B --es cmd say --es text 'bật đèn đọc'"     # phải NGHE thấy câu "Đã bật Đèn đọc" (không chỉ hiện chữ)
# tắt công tắc thứ nhất rồi nói lại ⇒ chỉ còn chữ + âm báo (y như 1.65)
adb shell "$B --es cmd state" | tr ',' '\n' | grep -iE "speak_replies|prefer_offline|ask_aloud|kind|engine"
```
- `kind` = `ANDROID_TTS` khi máy có giọng `vi-VN`; bật *"Ưu tiên giọng offline"* sau khi lắp gói ⇒ phải đổi sang `SHERPA_OFFLINE`.

### (c) R5 — đọc lại giá trị THẬT (đây là mục **cần xe**, máy ảo không lộ được)

```bash
adb shell "$B --es cmd say --es text 'đặt nhiệt độ 24'"
# ba kết quả đều hợp lệ, và mỗi cái nói một chuyện khác nhau:
#   "✓ Đặt Nhiệt độ = 24"                → xe báo đúng 24 (hoặc trim này không đọc được — xem hal get bên dưới)
#   "✓ Đã gửi Nhiệt độ 24 — xe báo 23"   → LỆCH thật: xe kẹp, xe chậm, hoặc nút không ăn trên trim
adb shell "$B --es cmd hal --es op get --es id temp"    # phân biệt "không đọc được" với "đọc được nhưng lệch"
```
- **OQ6 (cần đo)**: `READBACK_SETTLE_MS = 300 ms` là **giả định**. Bấm giờ giữa lượt ghi và lần `hal get` ĐẦU TIÊN
  trả số mới (lặp 10 lần cho `temp` và `fan`) → nếu > 300 ms thì câu trả lời sẽ hay thuật lại số cũ ⇒ nâng hằng.

### (d) Cổng xác nhận — câu trả lời, và (tuỳ chọn) đọc câu hỏi

> ⚠ **Owner chốt 2026-09-16: KHÔNG đọc câu hỏi xác nhận** (chỉ đọc phản hồi sau lệnh). Khoá `voice_ask_aloud`
> **mặc định false** và **chưa có hàng trong Cài đặt** ⇒ trên bản đang cầm, câu hỏi chỉ **hiện chữ** rồi mở micro
> ngay như 1.65. Kiểm bằng `state`, đừng kiểm bằng tai.

```bash
adb shell "$B --es cmd state" | tr ',' '\n' | grep -iE "ask_aloud|speak_replies"   # kỳ vọng ask_aloud:false
# Nói bằng MIC (không phải bridge) một câu CONFIRM: "mở khoá cửa"
#   → tấm chữ hiện câu hỏi, có tiếng bíp mở micro NGAY (không có lượt đọc chen vào)
#   → trả lời bằng BẤT KỲ từ nào: "ừ" · "vâng" · "có" · "được" · "đúng rồi" · "làm đi" · "đồng ý" · "ok"
```
- **C1 (cần đo trên mic thật)**: bảng `CONFIRM_YES` mở rộng 2026-09-16 sau khi [ĐO xe] thấy *"ừ"* bị bỏ **im
  lặng**. Đo: nói *"ừ"* ⇒ việc phải CHẠY (không phải *"đã huỷ"*). ⚠ Nếu nói *"dừng"* (ý là **thôi**) mà việc vẫn
  chạy thì đó là va chạm `đúng`/`dừng` đã biết — backlog `V-CONFIRM-DUNG`, báo về để owner chốt.
- **OQ7** chỉ đo được khi bật `voice_ask_aloud`: trần `ASK_ALOUD_CAP_MS = **6 s**` (nới từ 4 s ở lượt soát
  09-16; [SUY] câu dài nhất ~3,8 s). Nghe micro mở khi câu hỏi **chưa dứt** ⇒ trần vẫn hụt.
- Bấm nút *Đồng ý* **giữa lúc đang đọc** ⇒ câu phải **cắt ngay**, không đọc nốt.
- **Huỷ** (chạm ra ngoài) giữa lúc đang đọc ⇒ **không được** có tiếng bíp mở micro nào sau đó (lỗi [P2] vá ở
  lượt soát 09-16).

### (e) L7 — bố cục bằng giọng nói

```bash
adb shell "$B --es cmd say --es text 'đổi sang bố cục 2 cột'"   # kỳ vọng: ✓ Bố cục 2 cột, và MÀN HÌNH đổi thật
adb shell "$B --es cmd state" | tr ',' '\n' | grep -iE "preset|custom"
```
- ⚠ Đang dùng **bố cục tự vẽ** thì câu này **bỏ** bố cục tự vẽ (đúng như bấm chip ở Cài đặt) — kiểm `custom:false` sau lệnh.

## 6g. Thêm cho **voice pha 3** — 1.66 (67): NHANH + TỰ NHIÊN · HAL theo TÊN HẰNG

> Spec `docs/specs/kachi-voice-fast-natural.html`. Mọi mục dưới đây là thứ **chỉ đo được trên xe**; phần off-car
> đã xanh (5 module 0 đỏ · E2E máy ảo T1 67/68 · T2 22/25).

### (a) Sáu mốc giờ — MỘT lệnh logcat, không mò

```bash
adb shell logcat -c
# … bấm phím vô-lăng, nói "bật đèn đọc", chờ trả lời xong …
adb shell logcat -d -s KachiVoiceTiming:I | tail -30
```
Đọc theo thứ tự: `nạp sẵn mô hình … ms` (phải xuất hiện **sau ~3 s kể từ lúc mở app**, TRƯỚC khi bấm mic) →
`mic mở sau … ms` → `ngắt câu: nen=… nguong=… tieng_bat_dau=… tieng_dut=… chot=…` → `nghe … ms` →
`giải mã … ms (luồng=… lõi=…)` → `đóng mic: stop … ms · release … ms` → `nền: bíp … ms · nhả tiêu điểm … ms`.

| Mục | 1.64 [ĐO 09-16] | Kỳ vọng 1.66 | Nếu KHÔNG đạt thì ghi gì |
|---|---|---|---|
| lần bấm mic đầu | **15 s** | < 1 s | dòng `nạp sẵn` có chạy không? `ok=false` ⇒ mô hình chưa tải |
| nghe một câu 3 từ | 8,4 s (trần) | ≈ 1,5–2,5 s | dán nguyên dòng `ngắt câu:` — `nen`/`nguong` là hai số cần |
| giải mã | 2,35 s | thấp hơn | dán `luồng=`/`lõi=` |
| **lỗ 3,1 s** | 3,1 s × 4/4 | — | **thủ phạm là `stop` hay `release`?** dán dòng `đóng mic:` |

- **OQ5** — hai hằng `tiếng ≥ 400 ms` / `im ≥ 800 ms` mới là **lý lẽ**, chưa đo trên giọng thật trong cabin.
  Nói 10 câu, xem có câu nào bị **cắt giữa chừng** (`chot=` nhỏ hơn lúc bạn nói xong) hay **chờ lâu** không.

### (b) Nguồn micro — đổi ngay trên xe, không build lại (OQ2, owner D5)

*Cài đặt › Hệ thống & quyền › Nâng cao › Giọng nói › **Nguồn micro*** — bốn chip: Tự chọn · MIC (1) ·
VOICE_COMMUNICATION (7) · VOICE_RECOGNITION (6).

```bash
# Với MỖI nguồn: nói 10 câu khi xe ĐANG CHẠY 60–80 km/h và ĐANG MỞ NHẠC, rồi đọc mức tín hiệu:
adb shell logcat -d -s KachiVoiceMic:I | grep -E "micro mở bằng nguồn|mức micro" | tail -20
```
Ghi lại: nguồn nào · đỉnh · rms · sherpa nghe ra gì. [ĐO 09-16] nguồn 6 cho **đỉnh 136–255 · rms 30–50**
(gần câm), nguồn 1 cho **đỉnh 4429 · rms 237**. Câu hỏi CÒN MỞ: nguồn 7 (có AEC/NS của ROM) có thắng nguồn 1
khi có nhạc không.

### (c) int8 vs fp32 — cùng một tệp WAV, hai con số (OQ1, owner D4)

```bash
# 1) đang chạy fp32 (máy đã cài) — đo trước:
adb shell "$B --es cmd wav --es path /sdcard/Download/w02.wav"
adb shell logcat -d -s KachiVoiceTiming:I | grep "giải mã" | tail -2
# 2) Cài đặt › … › Nhận dạng giọng nói → chọn "Zipformer VN int8" → Tải (74 MB) → đo lại cùng tệp
```
So **hai số `giải mã`** và chữ nghe được. int8 nhỏ hơn 3,6 lần nhưng **[CHƯA BIẾT]** nó nhanh hay chậm hơn trên
ARM không có dot-product 8-bit — đó chính là câu hỏi. Báo về cả hai số; đừng kết luận từ một số.

### (d) Cổng xác nhận — mặc định KHÔNG hỏi gì cả (owner B1–B12)

```bash
adb shell "$B --es cmd say --es text 'mở khoá cửa'"   # kỳ vọng: CHẠY LUÔN, không có needs_confirm
```
Rồi bật đúng **một** ô: *Cài đặt › … › **Hỏi xác nhận trước khi chạy*** → tích *"Mở khoá cửa"* → nói lại câu đó
bằng **micro** ⇒ phải hiện câu hỏi + mở micro nghe *"ừ"*. Đây là mục duy nhất của cổng này mà máy ảo không
kiểm được (cầu kiểm thử không có đường ghi prefs).

### (e) Hội thoại + hỏi lại (owner D1/D2) — chỉ đo bằng MICRO

1. Nói *"bật đèn đọc"* → nghe câu trả lời → **đừng bấm gì**: tấm chữ phải chuyển sang *"Nói tiếp…"* và micro mở
   lại **không có tiếng bíp**. Nói tiếp *"tắt đèn đọc"* ⇒ chạy luôn. Im 5 giây ⇒ tấm chữ tự biến, **không** có
   câu báo lỗi nào.
2. Nói **cụt**: *"bật"* ⇒ phải hỏi *"Bật gì?"* rồi nghe tiếp; trả lời *"đèn đọc"* ⇒ chạy. Nói *"mở kính"* ⇒ phải
   hỏi *"Kính nào — …?"*. Hai lượt không hiểu ⇒ câu bỏ cuộc có **ví dụ**, không phải *"không hiểu"* lần ba.
3. ⚠ Nói *"mở kính lái"* (R10) ⇒ phải là **MỘT** cửa kính, KHÔNG phải nút gộp 4 kính như 1.64.

### (f) `featmap` — một lệnh, mở khoá mọi dòng bind còn ngờ (OQ3)

```bash
adb shell "$B --es cmd featmap"
adb shell logcat -d -s KachiTest:I | tail -5      # đọc "file" + "names" + "devices"
adb pull <đường dẫn file trong reply> ./featmap.json
```
Có tệp này thì tra được **off-car**: `ac_auto` · `mirror_fold_btn` · `ambient_power` (3 nút owner nói xe CÓ) và
`drift_mode` · `ambient_front/rear_color` · `bsd_fl/fr_alarm` (5 datum mà số cũ **không tồn tại** trên ROM này).
Kiểm nhanh tại chỗ: 7 datum vừa bind theo tên đã hết *"—"* chưa.

```bash
adb shell "$B --es cmd sweep --es op info"        # trip_km · trip_hours · motor_* · wiper_state phải có giá trị
```

### (g) Khoá / cốp qua `set()` generic — ĐẦU DÒ, có người nhìn, có hoàn tác

[ĐO nguồn fw-dl3] `BYDAutoDoorLockDevice` **không có setter nào**; đường ghi duy nhất là
`AbsBYDAutoDevice.set(int[], BYDAutoEventValue)` với `DOOR_LOCK_COMMAND_AREA_*` + `DOOR_LOCK_STATE_UNLOCK=1` /
`LOCK=2`. Chưa ai thử.

```bash
# XE ĐANG ĐỖ, có người đứng nhìn. Thử KHOÁ trước (an toàn hơn mở khoá):
adb shell "$B --es cmd hal --es op setev --es dev BYDAutoDoorLockDevice \
  --es m Door.DOOR_LOCK_COMMAND_AREA_ALL --es args 2 --ez auto_confirm true"
```
- Tên hằng đúng lấy từ `featmap.json` ở (f) — đừng đoán. Đọc `reply`: `denied: no permission / wrong device` ⇒
  sai device, `ok: HAL accepted` ⇒ **vẫn phải NHÌN xe** (rc=0 không có nghĩa là xe làm).
- ⚠ `ControlRegistry` **không đổi**: đây là đầu dò. Đo xanh thì lượt sau mới sửa route.

### (h) Bốn thứ của launcher

```bash
adb shell "$B --es cmd state" | tr ',' '\n' | grep -E "chip_labels|active|boot"
```
- **Nhãn chip**: *Cài đặt › Thanh trạng thái & thanh nút › Hiện nhãn trên thanh trên* — tắt ⇒ chip còn **icon +
  số**; `state.bars.chip_labels` = `false`. **Chụp màn hình trước/sau.**
- **Đổi tên hồ sơ**: *Cài đặt › Hồ sơ tài xế* → *Đổi tên* → bố cục/ô/chip/cấu hình ClusterNav phải **giữ nguyên**.
  Đặt trùng tên hồ sơ khác ⇒ phải hiện câu *"Không đổi được…"*, không im lặng.
- **Ô/inset**: khởi động lại launcher **lúc thanh trên/dưới của ROM còn hiện**, mở YouTube vào một ô ⇒ **không
  còn dải xám** ở trên (bug ảnh `carlog-0916/slot-insets-bug.png`). Kiểm:
  `adb shell logcat -d | grep -E "slot-insets|slot-resize"`.
- **`hood`**: nút *Ca-pô* phải **không còn** trong màn chọn nút (owner B6) — ô ai đã đặt sẵn thì vẫn dựng được.

### (i) VOICE-HOTFIX 1.69 — **[P0] vòng lặp hội thoại**: đọc MỘT dòng là biết đã chết hẳn chưa

Bản 1.68 trên xe mở micro **309 lần trong 12 phút** từ chỉ **7** phiên thật, và ~200 lượt trong số đó giải mã
chuỗi mô hình tự bịa (`"ừ"` 102 · `"ừm"` 102). Đó là thứ làm CPU bão hoà ⇒ **ô YouTube không cuộn nổi**. Sau
bản vá, đếm lại đúng con số ấy:

```bash
adb logcat -c && echo "— giờ nói 5 câu bình thường, rồi ĐỂ YÊN 2 phút —"
# ... nói xong, đợi 2 phút ...
adb logcat -d -s KachiVoiceTiming KachiVoiceRec | grep -c "mic mở sau"      # kỳ vọng ≈ số câu + vài lượt nối
adb logcat -d -s KachiVoiceRec | grep -c 'sherpa ra: "ừ'                    # kỳ vọng **0**
adb logcat -d | grep -E "bỏ giải mã|chốt một-micro|cầu chì"                 # ba lớp chặn, có dòng là đang chạy
```
- `mic mở sau` **> 30 lần** trong 2 phút im lặng ⇒ vòng lặp CHƯA chết — gửi nguyên log về.
- `cầu chì` nổ trong lúc dùng **bình thường** ⇒ trần 12 lượt/phút đặt chặt quá, báo lại (đừng tự nới trên xe).

### (j) Bộ ngắt câu — ba con số mỗi lượt, và cái núm chỉnh được **không cần build**

```bash
adb logcat -d -s KachiVoiceTiming | grep -E "ngắt câu|hết trần"
```
Mỗi dòng nay in `nen=` · `nguong=` · `ngat_o=` · `tieng=` · `im=`. Đọc thế này:
- `tieng_bat_dau=-1` ở một lượt người ta **có nói** ⇒ ngưỡng vẫn cao hơn giọng của người đó;
- `nen=` lớn hơn ~90 ⇒ trần nền đang bị chạm (đúng chỗ bản vá kẹp lại).

Chỉnh ngay trên xe, không build lại (kẹp `40..400`, mặc định 90 — nhỏ hơn = nhạy hơn):
```bash
B='-a com.byd.launcher.TEST_BRIDGE -n com.byd.launcher/.launcher.testbridge.KachiTestBridgeReceiver'
adb shell "am broadcast $B --es cmd prefs_set --es key voice_endpoint_floor_cap --es text 60"
adb shell "am broadcast $B --es cmd prefs_set --es key voice_endpoint_silence_ms --es text 1000"
```
Lời đáp có `read_back` — đọc nó, đừng tin lệnh đã chạy chỉ vì không báo lỗi.

### (k) Xuất **nhật ký lượt nói** (WAV + JSON) — thứ chốt được mọi câu còn lại

Đây là mục quan trọng nhất của chuyến này: host **không** trả lời được câu *"trên xe nghe đúng bao nhiêu %"*
(giọng TTS ≠ giọng người, không có mic 4 kênh, không có ồn đường — §8.6 của `emulator-voice-e2e-2026-09-15.md`).
Bản 1.69 tự ghi lại, chỉ cần lấy về:

1. Nói **10–15 câu** như dùng thật (có cả câu tester kêu: *"lọc ngay"* · *"lọc bụi"* · *"điều hoà"* · *"Google Map"*),
   nên có một lượt **đang mở nhạc** (owner chốt voice phải chạy được khi đang nghe nhạc).
2. *Cài đặt › Voice › **Xuất nhật ký voice*** → nút hiện **đường dẫn thật**; hoặc bằng máy:
   ```bash
   adb shell "am broadcast $B --es cmd voice_dump"      # trả đường dẫn zip
   adb pull <đường-dẫn-zip> /tmp/
   ```
3. Trên máy phát triển:
   ```bash
   python3 scripts/voice/replay-car-log.py /tmp/kachi-voice-<stamp>.zip --model <MODEL_DIR>
   ```
   ⇒ bảng **xe nghe ra gì** vs **host nghe ra gì** cho từng lượt. Chênh nhau = lỗi âm học của mic/cabin; giống
   nhau mà vẫn sai = lỗi từ vựng (sửa bằng `VoiceSynonyms`, không cần đụng mô hình).

⚠ Tiếng **chỉ nằm trên xe**: vòng 30 mục / 30 MB, tự xoá cái cũ, không có đường nào gửi đi. Tắt bằng
*Cài đặt › Voice › Giữ nhật ký lượt nói*.

### (l) `createStream` với tệp hotword 2 241 dòng — con số owner đang chờ

Ngân sách owner chốt: **≤ 150 ms** trên đầu xe. Máy ảo đo được trung vị **~10 ms** (dải 7–13, lượt đầu 52) —
nhưng máy ảo chạy trên x86 của máy phát triển, nên con số ấy **không** nói gì về ARM của xe.

```bash
adb logcat -d -s KachiVoiceRec | grep -o "createStream(hotwords) [0-9]* ms" | sort -n -k2 | tail -20
```
Vượt 150 ms ⇒ báo lại kèm con số; **đừng** tự cắt tệp hotword trên xe (cỡ tệp đã được đo là *không* phải biến
số quyết định — xem `kachi-voice-hotword-phrases.html`).

### (m) H1 — *"tăng gió"* nay phải cộng vào **số THẬT của xe**

```bash
adb shell "am broadcast $B --es cmd hal --es dev BYDAutoAcDevice --es m getAcWindLevel"   # đọc mức thật
```
Chỉnh gió **bằng màn BYD gốc** về một mức lạ (vd 1), rồi nói *"tăng gió"*: câu trả lời phải đọc ra **mức thật + 1**
(vd *"Đặt Gió = 2"*), **không** phải 5. Trước bản vá nó luôn tính từ mặc định trong RAM (gió 4 · nhiệt 22) — đúng
câu tester tả: *"quất một phát như lò heo quay"*. Làm lại với *"tăng nhiệt độ"*.

⚠ Sáu nút **chưa** có đường đọc (`seatc` `seath` `defrost` `defrost_rear` `ac_auto` `vol`) — chúng vẫn tính từ
bảng của Kachi. Đó là **đã biết**, không phải hỏng; xem backlog (M).

### (n) `hal --es op getid` — đọc thẳng một feature-id (mở khoá mạch MƯA của (S))

```bash
adb shell "am broadcast $B --es cmd hal --es op getid --es dev BYDAutoWiperDevice --es m 321912848"
adb shell "am broadcast $B --es cmd hal --es op getid --es dev BYDAutoWiperDevice --es m WIPER_AREA_FRONT_STATE"
```
Chỉ-đọc ⇒ **không** cần `auto_confirm`. Lời đáp có `sentinel` — `true` nghĩa là *feature không có trên trim này*,
**đừng** đọc con số `-2147482648` như một mức gạt mưa. Chạy **trong lúc gạt mưa đang chạy**: `getWindscreenWiperRelayState`
trả 0 suốt lượt đo 09-16 nên nó không dùng được, hai id trên là ứng viên thay.

## 6h. CHẠM TRONG Ô — 1.69 (70): đọc nhật ký daemon mới · thử vuốt thật

> Bệnh: [ĐO xe 09-16 §9.1] vuốt trong ô **không cuộn**, chỉ nhận tap, và tap **lệch**. Vá 1.69 = đường lùi theo
> **cử chỉ** (một lệnh mỗi cử chỉ, toạ độ đã map, có `swipe`). Hai mục dưới đây trả lời hai câu khác nhau:
> (g) *vì sao daemon không lên trên xe* — câu này 4 lượt xe rồi vẫn **[CHƯA BIẾT]**; (i) *vuốt đã chạy chưa*.

### (g) Đọc NHẬT KÝ INPUTD mới — 3 lệnh, chốt được nguyên nhân

Trước 1.69 lệnh khởi động daemon ném stderr vào `/dev/null` ⇒ không có gì để đọc. Nay có tệp thật, **cùng thư mục
`usage-*.log`**, nên `adb pull` quen thuộc lấy luôn được.

```bash
# 1) Ảnh chụp một dòng — khoẻ không, hỏng vì gì, thử mấy lượt, log nằm đâu
adb shell "am broadcast $B --es cmd state" | grep -o '"inputd":{[^}]*}'

# 2) Nhật ký của CHÍNH daemon (stdout+stderr của app_process)
adb shell ls -la /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ | grep inputd
adb shell cat /sdcard/Android/data/com.byd.launcher/files/kachi-logs/inputd-<stamp>.log

# 3) Nếu (2) nói "start localabstract:…" mà (1) vẫn không khoẻ ⇒ bệnh ở lượt NỐI, không ở lượt khởi:
adb shell "logcat -d -b all | grep -i 'avc:' | grep kachi_input"
```

**Đọc kết quả** (cây quyết định, không đoán):

| `state.inputd.last_error` | Nghĩa | Việc tiếp |
|---|---|---|
| `Permission denied` **và** có dòng `avc: denied { connectto } … tcontext=u:r:shell` | **sepolicy** chặn app nối tới socket của miền shell — giống hệt [ĐO máy ảo 2026-09-17]. Kiến trúc daemon **không dùng được** trên ROM enforcing | Đóng hướng daemon, đường lùi theo cử chỉ là đường chính thức. Báo về để gỡ mã daemon hoặc đổi hướng |
| `Connection refused` ở **cả 25 lượt**, tệp log **rỗng/không có** | `app_process` không chạy được (CLASSPATH? SELinux `execute`?) | Chạy tay đúng dòng lệnh, xem stderr: `adb shell "CLASSPATH=$(pm path com.byd.launcher \| sed 's/package://') app_process / com.byd.clusternav.system.inputd.InputDaemonMain kachi_input"` |
| tệp log có `bind failed: Address already in use` | daemon **cũ còn sống**, chỉ là không nối tới được | như hàng 1 |
| `last_error` **rỗng** và `attempts=0` | chưa có cú chạm nào vào ô app từ lúc mở app | chạm vào ô app một cái rồi đọc lại |
| `forced_off: true` | ai đó còn để công tắc ẩn `inputd_disabled` trên máy này | gỡ khoá đó rồi đo lại (xem cuối §6h) |

Log client cũng in lý do **lượt đầu / mỗi lần lý do đổi / lượt cuối** (cố ý không in đủ 25 dòng — 1.67 vừa cắt log):
```bash
adb shell "logcat -d -v time -s Kachi/InputDaemonClient:* | tail -20"
```

### (i) VUỐT THẬT trong ô — cùng lệnh đã từng ra một cú tap

Đặt một app có danh sách dài vào ô (YouTube, hoặc Cài đặt của xe), rồi chạy **đúng** phép đo của 09-16:

```bash
adb shell "am broadcast $B --es cmd slot --ei n 2 --es pkg com.google.android.youtube"   # hoặc app khác
sleep 6
adb exec-out screencap -p > /tmp/slot-before.png
adb shell input swipe 1000 850 1000 350 400        # vào VÙNG Ô, không phải toàn màn
sleep 2
adb exec-out screencap -p > /tmp/slot-after.png
```

- **ĐẠT** = hai ảnh khác nhau vì **CUỘN** (không phải vì mở một video). [ĐO máy ảo 2026-09-17] ca này đã đạt.
- **KHÔNG ĐẠT** nếu ảnh sau là một video/trang mới ⇒ vẫn đang bị hiểu thành tap: dán `state.inputd` + 20 dòng
  `logcat -s Kachi/InputDaemonClient` + hai ảnh về.
- Kiểm thêm **chạm đơn không bắn đôi**: chạm một hàng trong danh sách ⇒ mở **một** trang, không nhảy hai cấp.
- Kiểm thêm **giữ lâu**: giữ ~1 s trên một mục ⇒ menu ngữ cảnh của app hiện ra (trước 1.69 không bao giờ hiện).

**Ép đường lùi để so hai nhánh** (chỉ khi cần; nhớ gỡ sau — công tắc ẩn, không có nút nào trong Cài đặt):

```bash
# BẬT: force-stop → ghi → force-stop LẦN NỮA → start  (một lần force-stop là chưa đủ, tiến trình tự dựng lại)
adb shell am force-stop com.byd.launcher; sleep 2
adb shell "run-as com.byd.launcher sh -c \"sed -i 's|</map>|    <boolean name=\\\"inputd_disabled\\\" value=\\\"true\\\" />\\n</map>|' shared_prefs/clusternav_prefs.xml\""
adb shell am force-stop com.byd.launcher; sleep 2
adb shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
adb shell "am broadcast $B --es cmd state" | grep -o '"forced_off":[a-z]*'      # phải là true

# GỠ (bắt buộc trước khi rời xe)
adb shell am force-stop com.byd.launcher; sleep 2
adb shell "run-as com.byd.launcher sed -i '/inputd_disabled/d' shared_prefs/clusternav_prefs.xml"
adb shell am force-stop com.byd.launcher; sleep 2
adb shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
```

## 7. Sau khi 1 + 2 + 3 PASS
→ báo về: em chạy lại senior review + security scan (đã chạy off-car) với log thật → OTA 1.63. Mục FAIL: dán nguyên output — sửa đúng chỗ.

## Dọn
- `appops set com.android.shell REQUEST_INSTALL_PACKAGES deny` (grant tạm hôm 09-15 để test cài) — nếu còn.
