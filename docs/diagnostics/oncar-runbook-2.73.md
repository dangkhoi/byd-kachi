# Runbook buổi xe 2.73 — kiểm 8 việc off-car 26/09 (~40 phút, theo phút)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 · **Bản kiểm**: **2.73 (174)** (`apk/Kachi-2.73-release.apk`, kênh OTA `main`) · **Xe**: Seal DiLink 3.0 (Android 10), bản đang chạy 2.70 (171) cài tay hôm 26/09.
> Mỗi dòng E1–E12 khoá một việc off-car đã làm; **cột "đạt khi" viết trước khi đo** để không đọc số theo ý mình. Chi tiết cơ chế + tiêu chí ở 4 doc `offcar-2026-09-26/*.md`. Không cần adb cũng làm được §8 (chỉ chụp màn).
> Biến: `$A` = đường dẫn adb · `$S` = serial (`127.0.0.1:15555` qua cầu nc, xem memory `kachi-adb-car-tunnel`) · `<pkg>` = `com.byd.launcher`.

## 0. Ở nhà, TRƯỚC khi ra xe (5 phút)

1. Điện thoại/laptop có sẵn link OTA hoặc file `Kachi-2.73-release.apk` (43,5 MB) — mạng ở bãi xe không tin được.
2. Đọc lại 3 quyết định còn chờ owner để **nhìn** trên xe rồi trả lời một lần: WATCHDOG-GATE-8S · CAM-ROT-3 (khung camera ngang 360×192) · VOICE-OPEN-TURN (backlog *QUYẾT ĐỊNH CẦN OWNER*).
3. Mở sẵn 2 terminal: một chạy `logcat` **stream ra file** (buffer xe tràn sau ~30 s, `-d` không tin được), một để gõ lệnh.

## 1. Nối adb (3 phút)

Đường 1 (cầu nc, laptop cùng hotspot xe):
```bash
mkfifo /tmp/kfifo; (nc -l 127.0.0.1 15555 </tmp/kfifo | nc <ip-xe> 5555 >/tmp/kfifo) &
$A connect 127.0.0.1:15555 && $A -s $S shell dumpsys package <pkg> | grep versionName   # phải thấy 2.70 trước khi cài
```
Đường 2 (không adb): bỏ qua §2–§7, làm §8.

## 2. CÀI 2.73 (3 phút) — cài đè, giữ data

Cài đặt › Hệ thống › Kiểm tra cập nhật (OTA) **hoặc** `$A -s $S install -r Kachi-2.73-release.apk`. Chốt:
```bash
$A -s $S shell dumpsys package <pkg> | grep -E "versionName|versionCode"      # 2.73 / 174
$A -s $S shell run-as <pkg> ls lib/ 2>/dev/null; $A -s $S shell "pm path <pkg>"  # release không có run-as ⇒ chỉ cần pm path OK
```
Bật `logcat` stream ngay sau cài:
```bash
$A -s $S logcat -v time > logcat-2.73-$(date +%H%M).txt &
```

## 3. BASELINE nhanh 2.73 yên (5 phút) — màn chính, không chạm

`scripts/emulator/perf-snapshot.sh oncar-273-idle 300 $S` (cùng script buổi 26/09). Đọc dòng `KachiPerf` cuối: ghi **shell=/phút** và **log= KB/phút** — đây là E5.

## 4. BÀI TẬP E1–E12 (22 phút) — mỗi dòng: làm · đạt khi · lệnh chốt · nếu sai

| # | Việc off-car | Làm | Đạt khi | Lệnh chốt | Nếu sai → chép gì |
|---|---|---|---|---|---|
| **E1** | SYS-TASKBAR-VOICE-FOCUS | Hey Kachi BẬT → nói *"mở vietmap"* (lệnh có hành động mở app) → **nhìn thanh dưới** suốt lúc Kachi đọc phản hồi | Thanh điều hướng hệ thống **không trồi lên**, không nháy lúc tấm chữ hiện | `$A -s $S shell dumpsys window \| grep mCurrentFocus` trong lúc tấm chữ hiện ⇒ **không** phải `VoiceOverlay` | thanh vẫn lên ⇒ chép `dumpsys window` + 10 s logcat quanh `setBarShowingLw` |
| **E1b** | (đổi hành vi) | Đang nghe → bấm **Back** | Back đi tới app phía sau (**đúng thiết kế**, không huỷ phiên); chạm **ra ngoài tấm chữ** ⇒ huỷ ngay | — (mắt) | chạm ngoài không huỷ ⇒ chép `logcat -s KachiVoiceSession` |
| **E2** | VOICE-SLOT-TAIL-CUT | Nói **10 lượt** *"mở vietmap vào ô số 2"* (giọng bình thường, ngừng giữa hai vế như thật) | ≥ 8/10 ra VietMap **ở ô 2** + Kachi đọc "đã mở … vào ô 2" (26/09: 1/10) | `grep -E "heard|intent" logcat-2.73-*.txt` — chuỗi ASR phải có *"vào ô số hai"* | < 8/10 ⇒ `voice_dump --ez auto_confirm true` lấy zip WAV về replay ở nhà (đường đã chứng minh tái lập từng ký tự) |
| **E2b** | VAD trần 1200 | `prefs_set voice_vad_min_silence_ms 1100` rồi nói lại 3 lượt E2; sau đó trả `600` | Lệnh **được nhận** (26/09 bị từ chối vì trần 800); độ trễ chốt câu dài hơn cảm nhận được ~0,5 s | `… --es cmd prefs_set --es key voice_vad_min_silence_ms --es text 1100` có `read_back=1100` | read_back ≠ 1100 ⇒ chép output broadcast |
| **E3** | VOICE-APP-NAME-FUZZY | Nói 3 lượt *"mở YouTube vào ô hai"* nhanh, hơi nuốt đuôi | 3/3 mở YouTube ô 2 dù ASR ra "youtubex/youtubec" | chuỗi `heard` trong log + intent `OpenApp(YouTube→ô 2)` | ra app khác ⇒ **[P0] false-positive**, chép cả chuỗi heard + tên app bị nhầm |
| **E4** | VOICE-PROFILE-NAME-PHONETIC | Cài đặt › Hồ sơ: có hồ sơ **"Test"** (tạo nếu chưa). Nói 3 lượt *"chuyển sang hồ sơ Test"*, 3 lượt *"đổi sang hồ sơ"* (cố ý thiếu tên) | 3/3 đổi sang Test; 3/3 thiếu tên ⇒ Kachi **hỏi lại** *"Hồ sơ nào — Mặc định hay Test?"* | `$A -s $S shell run-as <pkg> cat files/voice/grammar-snapshot.tsv` (chỉ bản vehicleTest) hoặc `grep "HỒ SƠ" logcat` | không hỏi lại ⇒ chép `logcat -s KachiVoiceSession KachiVoiceRec` |
| **E5** | SHELL-19 + LOG-41KB | Đọc `KachiPerf` từ §3 (yên 5 phút) | **log < 20 KB/phút** (kỳ vọng ≈ 9; 26/09 = 34–41) · **shell ≈ 19/phút** (không nhắm giảm) | `grep KachiPerf logcat-2.73-*.txt \| tail -3` | log > 20 ⇒ chép 60 s usage log; shell lệch xa 19 ⇒ có nguồn thứ ba chưa biết, chép `logcat -s SimpleCast` |
| **E5b** | chốt [SUY] 4 lệnh probe | Đóng hết ô App (chỉ widget), chờ 2 phút | shell rơi về **≈ 15/phút** | cùng lệnh E5 | không rơi ⇒ [SUY] sai, ghi số |
| **E6** | CLOSE-4 WAKE-MALLOPT | Sau cài, Hey Kachi BẬT, chờ mô hình nạp xong; đo trước; một lượt thoại thật; chờ > 18 s; đo sau | `lib=true` trong log; RSS ở dòng `purge(...)` rơi ≥ 20 MB ít nhất một mốc **và** `Native Heap` PSS `:wake` sau < trước | `$A -s $S logcat -d -s KachiWake \| grep M_DECAY_TIME` · `$A -s $S shell dumpsys meminfo <pkg>:wake \| grep -E "Native Heap\|TOTAL"` (trước/sau) · `$A -s $S logcat -d -s KachiMem:D` | `lib=false` ⇒ APK thiếu .so, chép `pm path` + `ls`; `ok=true` mà không rơi ⇒ ghi số, **dừng**, không thêm mốc (doc §6) |
| **E7** | CAM-ROT-2 khung đúng tỉ lệ | Xi-nhan trái ≥ 20 s, rồi phải | Khung camera **ngang 360×192** (không còn ô vuông), **0 viền đen, 0 méo**; hai bên đúng chiều | `$A -s $S logcat -s KachiCamera` có `overlay show … khung=360x192 vùng=360x360 nguồn-biết=true` + `previewSize HAL = <W>x<H>` (hoặc `trả 0x0`) | khung vẫn 360×360 ⇒ chép 2 dòng trên; `previewSize` ≠ 5120×960 ⇒ ghi số thật (chốt [CHƯA BIẾT]) |
| **E7b** | **OWNER NHÌN → quyết CAM-ROT-3** | Nhìn khung ngang 360×192 | Trả lời: giữ đúng tỉ lệ dải gương (ngang) **hay** muốn 4:3 kiểu kinex (thu dải y crop, đổi đường đã chạy) | — | ghi câu trả lời vào backlog *QUYẾT ĐỊNH CẦN OWNER (ii)* |
| **E8** | CLOSE-14 chip Kết xuất (đo L2) | `dumpsys gfxinfo <pkg> reset` → xi-nhan 20 s → đọc; `prefs_set camera_render SV` → lặp; trả `TV` | So `Janky %` + p99 TV↔SV **cùng đoạn đường, cùng tốc độ**; SV chỉ "đáng bàn" khi giảm Janky ≥ 1/3 **và** không mất crop/xoay/bo góc | `$A -s $S shell dumpsys gfxinfo <pkg> \| grep -E "Total frames\|Janky\|percentile"` · `… --es key camera_render --es text SV` | hai lượt cùng cấu hình lệch > 2× ⇒ đo 3 lượt lấy trung vị; SV không xoay ⇒ ghi `setDisplayOrientation(...) nhận=` |
| **E9** | 2.72 lưới an toàn (chưa kiểm xe) | Xi-nhan bật, camera hiện → `am force-stop` helper HAL (hoặc rút công tắc camera) | Camera **đóng**, không treo | `$A -s $S logcat -s KachiHalSignal` có dòng đứt dây ⇒ OFF | treo ⇒ chép `ps -T` + logcat |
| **E10** | hồi quy nhanh | Hey Kachi → nói *"mấy giờ rồi"*, *"bật gió tự động"*; nút mic màn chính | Trả lời đúng, nghe ngay (< 1 s) | — | sai ⇒ chép `logcat -s KachiVoiceEntry` |
| **E11** | crash | — | không tệp crash mới | `$A -s $S shell ls /sdcard/Android/data/<pkg>/files/kachi-logs/ \| grep crash` | có ⇒ pull |
| **E12** | perf tổng | `perf-snapshot.sh oncar-273-after 300 $S` sau E1–E10, màn chính | PSS launcher ≤ 70 MB · `:wake` PSS sau E6 ghi số · khung/5 phút không tăng so 2.70 (510) | JSON của script | tăng > 20 % ⇒ chép |

## 5. Đường KHÔNG-adb (khi không nối được) — 15 phút, chỉ chụp màn

E1 (nhìn thanh) · E1b · E2 (đếm số lượt đúng /10) · E3 · E4 · E7 (chụp ảnh khung camera 2 bên) · E7b (trả lời) · E10. Cuối buổi chụp **Cài đặt › Hệ thống › Nâng cao › Chẩn đoán** (có `KachiPerf` shell/log phút, bản, tiến trình) — đó là E5 + E12 không cần adb.

## 6. Mang về (2 phút)

- File `logcat-2.73-*.txt`, 2 JSON `perf-snapshot`, zip `voice_dump` nếu E2 < 8/10 ⇒ để vào `docs/diagnostics/perf-oncar-<ngày>/` (**raw data gitignored**, chỉ commit bản tóm tắt .md).
- 3 câu trả lời owner: WATCHDOG-GATE-8S (giữ 4 s?) · CAM-ROT-3 (E7b) · VOICE-OPEN-TURN (có làm không).
- Kết quả ✓/✗ từng E → ghi vào backlog dòng tương ứng (nâng 🚗 → [ĐO]) trong cùng phiên.

## Thứ tự nếu chỉ có 15 phút

§2 cài → **E2** (10 lượt "vào ô số 2") → **E1** (nhìn thanh) → **E7** (khung camera + E7b nhìn) → **E6** (3 lệnh meminfo/logcat) → chụp Chẩn đoán.
