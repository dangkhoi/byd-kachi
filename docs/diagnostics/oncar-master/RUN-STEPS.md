# RUN-STEPS — RUNBOOK THỰC THI TỪNG BƯỚC (Kachi 1.83 · 84)

> **Loại**: Diagnostics (on-car step-by-step) · **Trạng thái**: Current · **Ngày**: 2026-09-20 · **Bản đích**: **1.83 (84)**
> **Mục đích**: đi HẾT một vòng từ đầu đến cuối, **mỗi step ghi rõ 🤖 EM làm gì · 👤 ANH làm gì · PASS/FAIL**. Xong vòng 1 → **§CUỐI tổng hợp** → vòng 2 chỉ chạy lại step ❌. Khoa học, không wasting time.
> **Danh sách đầy đủ + vì sao**: `0-PENDING.md`. **Chi tiết method/bẫy**: `1-hal.md · 2-slot-cast.md · 3-voice.md · 4-system.md`. **Doc này là SCRIPT chạy** — bám theo số step.
>
> **Quy ước vai**:
> - 🤖 **EM** = chạy adb qua `adb_raw.py` (nếu xe reachable từ máy em) HOẶC đưa anh lệnh copy-paste + phân tích output anh gửi.
> - 👤 **ANH** = việc VẬT LÝ: **NHÌN** xe (cửa/kính/cốp/AC/cụm) · **NÓI** vào mic (test ASR thật) · **BẤM** phím vô-lăng / nút nguồn · **LÁI** (bước gate tốc độ). Ghi `✅/❌/⬜`.
> - **rc=0 ≠ xe làm** — mọi step GHI phải có 👤 anh NHÌN xác nhận.
> - **An toàn**: xe ĐỖ · số P · phanh tay cho mọi step GHI. Bước lái (S23) cần **người thứ hai** gõ adb.

---

## VÒNG 1 — đi hết một lượt

### ▶ PHA 0 · KẾT NỐI + TIỀN ĐỀ (chặn — sai là mọi step sau vô nghĩa)

**S0 · Kết nối adb — quyết AI chạy lệnh**
- 🤖 EM: thử `python3 scripts/vehicle/kachi/adb_raw.py <car-ip> 5555 shell "echo OK"`.
- 👤 ANH: cho em biết **IP xe** (màn Cài đặt Wi-Fi) + xe và máy em cùng mạng chưa.
- **Nhánh**: ra `OK` ⇒ **EM chạy hết mọi lệnh adb** (anh chỉ vật lý). · Timeout ⇒ anh cắm laptop có adb, em đưa lệnh anh paste; HOẶC dùng captest tool trong app cho phần đọc. → **Ghi rõ chế độ nào trước khi đi tiếp.**
- ✅ `echo OK` chạy được (bằng một trong hai đường). ⬜

**S1 · Bản = 1.83?**
- 🤖 EM: `... shell "dumpsys package com.byd.launcher | grep -E 'versionName|versionCode'"`.
- 👤 ANH: nếu thấp hơn 1.83 → bật **Nav+HUD** trong app để OTA tự tải (chờ vài phút) rồi báo em.
- ✅ `versionName=1.83 / versionCode=84`. ⬜

**S2 · Ba tiền đề sống**
- 🤖 EM: kiểm cầu test-bridge (`am broadcast ... TEST`), a11y booster (`dumpsys accessibility | grep Bound`), notif-listener.
- 👤 ANH: bật công tắc **"Chế độ kiểm thử qua adb"** (Cài đặt › Hệ thống › Nâng cao) — **chỉ bật tay được**, tự tắt sau 60′.
- ✅ cầu trả `ok:true` · a11y Bound · notif granted. ⬜

> ⚠ **Cho PHA 3 (:tts kill) cần bản `vehicleTest`** (run-as cùng uid mới kill được `:tts`). Nếu buổi này định làm S16 → **báo em build `vehicleTest 1.83 + diagLog` TRƯỚC**, anh cài tay, cuối buổi cài lại release.

---

### ▶ PHA 1 · ĐỌC AN TOÀN (0 rủi ro)

**S3 · CPU/RAM baseline (K5)**
- 🤖 EM: `top -b -n2 -d5 | grep launcher` + `dumpsys meminfo com.byd.launcher` **và** `...launcher:tts` (RIÊNG) + `/proc/loadavg`.
- 👤 ANH: chỉ để app chạy ~30s, không chạm.
- ✅ `:tts` PSS **< ~100 MB** (nếu >100 ⇒ nó nạp model NGHE 74MB = `isTtsProcess()` hỏng = [P0]). Ghi load average. ⬜

**S4 · Đọc 100 telemetry một phát (nhóm D)**
- 🤖 EM: `... hal --es op read-all` → JSON (đọc-được / sentinel / unavailable từng datum).
- 👤 ANH: không cần.
- ✅ ghi được bảng 100 dòng. Đánh dấu 12 NEEDS_CAR (cell_v×2/target_soc/coolant/tyre_t×4/gps×4) + đếm bao nhiêu ra số thật vs sentinel. ⬜

---

### ▶ PHA 2 · LOGCAT A/B (⭐⭐⭐ — thứ log WavProbe KHÔNG trả lời được)

**S5 · A — VietMap dẫn thật?**
- 🤖 EM: `logcat -c` (xoá log) → chờ anh nói → `logcat -d | grep -E 'START.*vietmap|google.navigation|vietmaplive'`.
- 👤 ANH: **NÓI vào mic**: *"dẫn đường tới chợ Bến Thành bằng vietmap"* → **NHÌN**: VietMap có mở + **dẫn thật** (có tuyến đường) không?
- ✅ log ra `vietmaplive://…lat&lng pkg=vn.vietmap.live` **VÀ** VietMap dẫn thật. ❌ nếu rớt `google.navigation` (GMaps) hoặc VietMap mở mà không dẫn → ghi log, em xử off-car (C1b: 4 option chữ). ⬜

**S6 · B — Music phát thật?**
- 🤖 EM: `logcat -c` → chờ → `logcat -d | grep -iE 'MediaSession|PLAY_FROM_SEARCH|youtube|YtResolve'`.
- 👤 ANH: **NÓI**: *"mở bài hát [tên bài anh thích]"* → **NGHE**: có phát nhạc thật không?
- ✅ nhạc kêu + log có videoId/MediaSession. ❌ mở app mà không phát → ghi log resolver, em xử off-car (UA/mạng xe). ⬜

**S7 · Δ2 — "mở việt máp" (biến thể phiên âm)**
- 🤖 EM: `logcat -c` → chờ → `logcat -d | grep 'START.*vietmap'`.
- 👤 ANH: **NÓI**: *"mở việt máp"* (rồi thử *"mở việt mép"*) → **NHÌN**: mở VietMap hay mở ngăn kéo app?
- ✅ mở `vn.vietmap.live`. ❌ mở drawer → ghi từ ASR nghe được, em thêm biến thể off-car. ⬜

---

### ▶ PHA 3 · `:tts` KILL — GỐC BUG MẤT PHÍM (⭐⭐⭐ · chưa từng chạy xe · cần vehicleTest)

**S8 · Mốc TRƯỚC**
- 🤖 EM: `ps -A | grep launcher` (ghi pid `:app`; `:tts` chưa có — bind lười) + `dumpsys accessibility | grep -A6 NavAccessibilityService` (Bound?).
- 👤 ANH: không cần.
- ✅ ghi pid `:app` + a11y Bound. ⬜

**S9 · Kill `:tts` giữa lúc đọc**
- 🤖 EM: đặt bẫy vòng lặp `run-as ... kill -9` ngay khi `:tts` xuất hiện (luôn trúng lúc synth).
- 👤 ANH: **NÓI** một câu có reply DÀI: *"mở tất cả kính"* → **NHÌN**: overlay đóng bình thường không (không treo ~10s)?
- ✅ **4 điều**: `:app` pid **KHÔNG đổi** · a11y vẫn **Bound** · overlay đóng thường · câu SAU đọc được. ❌ `:app` đổi pid ⇒ #0 thất bại → em đi đường AndroidTtsSpeaker mặc định. ⬜

**S10 · Phím sống qua ≥10 lượt**
- 🤖 EM: sau mỗi lượt voice ở buổi, `dumpsys accessibility | grep -c Bound` (không giảm) + `logcat | grep -c 'Fatal signal 11'`.
- 👤 ANH: rải suốt buổi — **BẤM phím vô-lăng gán** thử vài lần giữa các step.
- ✅ Bound không giảm · phím còn ăn sau ≥10 lượt. ⬜

---

### ▶ PHA 4 · HEY KACHI (⭐⭐ · CÓ CỔNG — đừng đo mù)

**S11 · Đo load 4 trạng thái (CỔNG — làm TRƯỚC khi đo nhận dạng)**
- 🤖 EM: đo `loadavg` 60s ở mỗi trạng thái.
- 👤 ANH: lần lượt: (1) để yên · (2) mở VietMap · (3) chiếu GMaps vào ô · (4) GMaps+VietMap.
- ✅ có chuỗi ≥3 mẫu **< 4.0** ⇒ cổng MỞ, đi S12. ❌ load toàn **> 6** ([ĐO 18-09] 10–14 rất khả năng) ⇒ **CỔNG ĐÓNG: KHÔNG đo nhận dạng** (sẽ 0/20 vô ích), ghi "guard chia-lõi = việc off-car", nhảy S13. ⬜

**S12 · KWS nhận "Hey Kachi"? (CHỈ khi S11 mở cổng)**
- 🤖 EM: `logcat | grep WakeListen` đếm hit.
- 👤 ANH: bật Hey Kachi (Cài đặt) → **NÓI "Hey Kachi"** 20 lần (10 im, 10 có nhạc).
- ✅ hit ≥ 8/10 im + ≥ 6/10 nhạc, false-accept ≤ 1/30′. ⬜ (bỏ qua nếu cổng đóng)

**S13 · P2 marker (Δ8/Δ9) — cầu chì + không xoá setting**
- 🤖 EM: kiểm tồn tại marker `files/kachi_wake_disabled` + đọc lại vị trí biển tốc độ trước/sau.
- 👤 ANH: đặt biển tốc độ vị trí dễ nhớ → bật Hey Kachi → **NÓI bừa 6 lần/phút** ép auto-tắt → mở Cài đặt **NHÌN công tắc** (phải OFF, không ON dởm) → kiểm biển tốc độ **còn nguyên vị trí**.
- ✅ công tắc hiện OFF sau auto-tắt · biển tốc độ KHÔNG mất. ⬜

---

### ▶ PHA 5 · HAL ACTION — GHI 34 CONTROL (⭐⭐ · xe ĐỖ · anh NHÌN từng cái)

> Chạy theo `1-hal.md §1` sweep. Mỗi control: 🤖 EM bắn `hal write named-method --ez auto_confirm true` → 👤 ANH **NHÌN xe** → 🤖 EM đọc lại datum. Batch theo nhóm dưới.

**S14 · 3 BUG id-trùng đã tra (làm trước — giá trị cao)**
- 🤖 EM: kéo "Độ sáng HUD" → đọc `SET_BRIGHTNESS_GEAR`. `camera_view` → thử `setDisplayMode`. `headl` on/off.
- 👤 ANH: **NHÌN**: kéo "độ sáng HUD" có làm **màn chính** tối/sáng không (bug)? camera_view có đổi gì? đèn pha có bật/tắt?
- ✅ xác nhận `hud_brightness` đổi nhầm màn chính → em sửa thành MODE off-car · các cái khác ghi kết quả. ⬜

**S15 · Thân xe (cửa/kính/cốp/khoá) — Δ4 + Δ5**
- 🤖 EM: bắn từng nút thân xe.
- 👤 ANH: **NÓI "mở kính"** → NHÌN chỉ **1 cửa lái** hạ (không cả 4)? · các nút cốp/khoá/cửa: NHÌN xe làm gì.
- ✅ "mở kính" = 1 cửa lái · ghi nút nào ăn/không. ⬜

**S16 · Điều hoà (Δ3/Δ3b) + AC/sấy/ion/sưởi**
- 🤖 EM: `T say "'mở điều hòa hai mươi lăm độ'"` + `T say "'bật điều hòa chế độ hai'"`.
- 👤 ANH: **NHÌN** màn AC: câu 1 đặt **25°C** (không chỉ bật AUTO)? câu 2 **KHÔNG** hạ nhiệt xuống 17 (chỉ bật chế độ)?
- ✅ 25 độ set đúng · "chế độ hai" không đặt nhiệt 17. ⬜

**S17 · Δ6 read-back**
- 🤖 EM: nghe reply.
- 👤 ANH: **NÓI "bật đèn đọc"** → nghe reply: khớp → "đã bật" (bỏ hedge); lệch → "xe không nhận lệnh".
- ✅ read-back đúng. ⬜

**S18 · Còn lại của 34 control (EV/pin/đèn/camera/mã máy…)**
- 🤖 EM: sweep batch theo `1-hal.md §2–6`, mỗi cái write→đọc-lại.
- 👤 ANH: NHÌN từng cái (em sẽ đọc tên trước mỗi lần bắn).
- ✅ ghi PROVEN / sai-route / sentinel-ẩn cho từng mã. ⬜

---

### ▶ PHA 6 · CAST + APP-VÀO-Ô (⭐ · chạm CỤM trước mặt người lái — để gần cuối)

**S19 · Cast cụm còn tốt (X2)**
- 🤖 EM: chiếu 1 app → `am stack list | grep displayId` + dò display cụm động.
- 👤 ANH: **NHÌN CỤM**: app hiện đúng trên cụm không?
- ✅ cast lên cụm OK. ⬜

**S20 · App (Waze) vào ô — probe C/D/G/H**
- 🤖 EM: thử option C (freeform display 0) → D (move-task) → G/H theo `2-slot-cast.md §4`.
- 👤 ANH: **NHÌN**: app vào ô hay rớt toàn màn?
- ✅ có option đưa được Waze vào ô. ⬜

---

### ▶ PHA 7 · SYSTEM (K8 · P7/S5 · F4 · W5)

**S21 · Reboot NÚT NGUỒN VẬT LÝ (P7/S5 · HOME sống + cầu chì Hey Kachi persist)**
- 👤 ANH: **BẤM NÚT NGUỒN** tắt/mở máy (KHÔNG dùng `adb reboot` — không tính) → sau khi lên: bấm Home.
- 🤖 EM: sau reboot kiểm HOME = Kachi + marker Hey Kachi còn (nếu đã auto-tắt).
- ✅ Kachi vẫn là HOME · Hey Kachi vẫn OFF tới khi bật tay. ⬜

**S22 · W5 — phím khi app camera tiền cảnh**
- 👤 ANH: mở app camera 360 → **BẤM** phím tăng/giảm âm + trái/phải.
- 🤖 EM: `getevent -lt` bắt keycode.
- ✅ ghi keycode 4 phím + camera_view có ăn khi cam mở không. ⬜

**S23 · Gate tốc độ cốp/ca-pô (⚠ CẦN LÁI — người thứ hai gõ adb)**
- 👤 ANH: **LÁI** (người thứ 2 gõ) → khi đỗ: "mở cốp" ăn; khi lăn: "mở cốp" → "chỉ mở khi xe dừng".
- 🤖 EM: (người thứ 2) bắn lệnh + đọc `speed`.
- ✅ gate đúng cả 2 trạng thái. ⬜ *(bỏ nếu không có người thứ 2 — để buổi khác)*

---

## §CUỐI · TỔNG HỢP VÒNG 1 → LÊN VÒNG 2

**Bảng tổng hợp** (điền sau vòng 1):

| Step | Mục | Kết quả | Ghi chú / log |
|---|---|---|---|
| S5 | A vietmap dẫn | ⬜ | |
| S6 | B music phát | ⬜ | |
| S9 | :tts kill | ⬜ | |
| S11 | Hey Kachi load-gate | ⬜ | |
| S14 | 3 bug id-trùng | ⬜ | |
| S16 | điều hoà độ | ⬜ | |
| … | (mọi step) | ⬜ | |

**Sau vòng 1** — 🤖 EM tổng hợp:
1. Liệt kê step **❌** + **log/nguyên nhân**.
2. Chia hai loại: (a) **sửa được off-car** (route HAL, resolver UA, guard chia-lõi, VietMap dispatch, hud_brightness→MODE) → em vá off-car, ra bản mới, **vòng 2 = chỉ chạy lại các step ❌ đó sau khi OTA**. (b) **cần đo thêm/option khác tại xe** (cast option kế, geocode) → chạy option kế NGAY trong buổi nếu còn thời gian.
3. Step **✅** đóng luôn (không chạm lại).

**Vòng 2, 3…**: lặp đúng cơ chế trên — **chỉ động vào cái chưa OK**, không chạy lại cái đã PASS. Mỗi vòng thu hẹp dần tới 0 ❌.

## Thứ tự nếu ít thời gian
S0→S2 (tiền đề) → **S5,S6** (logcat A/B) → **S8,S9** (:tts kill, nếu có vehicleTest) → **S11** (Hey Kachi load-gate, quyết luôn) → **S14** (3 bug id-trùng) → **S15,S16** (thân xe + điều hoà). Phần đọc 100 telemetry (S4) + cast (S19,S20) + system (S21-23) để vòng/buổi sau.

---

## PHỤ LỤC · LỆNH GÕ SẴN (dán IP là chạy — prep 2026-09-20)

> Xe reachable từ máy em ⇒ **EM chạy hết**. Dán IP một lần: `IP=<car-ip>` rồi `A="python3 scripts/vehicle/kachi/adb_raw.py $IP 5555"`.
> **Artefact đã build sẵn**: `apk/Kachi-1.83-release.apk` (vc84 `df7b832f…`, OTA) · `app/build/outputs/apk/vehicleTest/app-vehicleTest.apk` (vc84 diagLog, `ba71abf2…` — cho :tts kill).

```sh
IP=<car-ip>                                   # dán khi anh gửi
A="python3 scripts/vehicle/kachi/adb_raw.py $IP 5555"   # đường SHELL PROVEN (pure-python, không bị macOS chặn)
ADB=~/Library/Android/sdk/platform-tools/adb            # real adb — MACOS CÓ THỂ CHẶN daemon LAN (lý do adb_raw.py có)

# --- S0 test CẢ HAI đường (real adb có thể không connect được từ máy này) ---
$A shell "echo OK"                                       # (1) adb_raw.py — nếu ra OK ⇒ shell chạy chắc
$ADB connect $IP:5555 && $ADB -s $IP:5555 get-state      # (2) real adb — cần cho INSTALL + batch scripts (71-hal-sweep/60-cast/90-collect)
#   → (2) ra "device" ⇒ dùng real adb cho install + scripts. (2) timeout ⇒ CHỈ dùng $A (shell); install + HAL-sweep đi đường khác (dưới).

# --- Cài vehicleTest (S8-S10) ---
# NẾU (2) chạy: $ADB -s $IP:5555 install -r app/build/outputs/apk/vehicleTest/app-vehicleTest.apk   (proven)
# NẾU chỉ (1) : $A install app/build/outputs/apk/vehicleTest/app-vehicleTest.apk   (adb_raw.py install — CHƯA test off-car, verify sau)
# NẾU cả hai hỏng: BỎ vehicleTest ⇒ S8-S10 làm bằng crash TỰ NHIÊN trên release (F4), không có kill chủ động.
#   ⚠ cuối buổi (nếu đã cài vehicleTest): cài lại release — install -r apk/Kachi-1.83-release.apk

# S1  bản        : $A shell "dumpsys package com.byd.launcher | grep -E 'versionName|versionCode'"
# S2  a11y bound : $A shell "dumpsys accessibility | grep -c Bound"
# S3  CPU/RAM    : $A shell "top -b -n2 -d5 | grep -E 'com.byd.launcher|load average'"
#                  $A shell "dumpsys meminfo com.byd.launcher:tts | grep 'TOTAL PSS'"   # phải < ~100MB
# S4  telemetry  : (2) chạy ⇒ bash scripts/vehicle/kachi/71-hal-sweep.sh $IP:5555
#                  chỉ (1)  ⇒ $A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd hal --es op read-all" (đọc từng phần)
# --- S5 A vietmap (anh NÓI giữa 2 lệnh) ---
# S5a $A shell "logcat -c"
# S5b (anh nói "dẫn đường tới chợ Bến Thành bằng vietmap") → $A shell "logcat -d | grep -E 'ActivityTaskManager: START|vietmaplive|google.navigation|KachiVoiceGeo' | tail -8"
# --- S6 B music --- $A shell "logcat -c" ; (anh nói "mở bài hát <tên>") ; $A shell "logcat -d | grep -iE 'MediaSession|PLAY_FROM_SEARCH|youtube|YtResolve' | tail -8"
# --- S7 mở việt máp --- $A shell "logcat -c" ; (anh nói "mở việt máp") ; $A shell "logcat -d | grep 'START.*vietmap' | tail -5"
# --- S8-S9 :tts kill (vehicleTest) ---
# S8  $A shell "ps -A | grep com.byd.launcher" ; $A shell "dumpsys accessibility | grep -A6 NavAccessibilityService"
# S9  $A shell "run-as com.byd.launcher sh -c 'for i in \$(seq 1 300); do p=\$(ps -A|grep com.byd.launcher:tts|grep -v grep|awk \"{print \\\$2}\"); [ -n \"\$p\" ] && { echo TRAP \$p; kill -9 \$p; break; }; sleep 0.1; done'"
#     (anh nói "mở tất cả kính" NGAY sau) → $A shell "ps -A | grep com.byd.launcher" (pid :app KHÔNG đổi?) ; $A shell "dumpsys accessibility | grep -c Bound"
# S10 phím sống  : sau mỗi lượt: $A shell "dumpsys accessibility | grep -c Bound" ; $A shell "logcat -d | grep -c 'Fatal signal 11'"
# --- S11 Hey Kachi load (CỔNG) --- $A shell 'sh -c "for i in \$(seq 1 60); do cut -d\" \" -f1 /proc/loadavg; sleep 1; done"' ; $A shell "grep -c processor /proc/cpuinfo"
# --- S16 điều hoà (cầu say, không cần mic) ---
# S16  $A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd say --es text \"'mở điều hòa hai mươi lăm độ'\""
# S16b $A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd say --es text \"'bật điều hòa chế độ hai'\""
# --- batch (chỉ khi real adb (2) chạy) --- HAL: bash .../71-hal-sweep.sh $IP:5555 · cast: bash .../60-cast.sh $IP:5555 · thu log: bash .../90-collect.sh $IP:5555
```

> **Bẫy dấu nháy** (đã proven): cầu `say`/text có khoảng trắng phải nháy HAI lớp `"'chuỗi có dấu cách'"` — nếu không `am` cắt ở khoảng trắng đầu mà vẫn báo `ok:true` (helper `k_shq` trong `_common.sh` làm đúng). **CẤM `input tap` toạ độ** (xe 1920×720 ≠ máy ảo).
