# Runbook buổi xe kế — 2.75 (must-have ~30 phút, theo giá trị/phút)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (trưa, sau buổi xe 2.74) · **Bản kiểm**: **2.75 (176)** qua OTA `main` · **Xe**: Seal DiLink 3.0, đang chạy 2.74 (175) (cài tay 09:57 27/09) · **Mục đích**: mỗi thứ 2.75 sửa theo số đo buổi 27/09 đều có một phép đo chốt trên xe, viết trước tiêu chí. Kết quả buổi 2.74 + số đo gốc: `docs/PROJECT-BACKLOG.md` dòng `ONCAR-2026-09-27`.
> Biến: `$A` adb · `$S` = `127.0.0.1:15555` (cầu nc, memory `kachi-adb-car-tunnel`) · `<pkg>` = `com.byd.launcher` · `BR` = `$A -s $S shell am broadcast -n <pkg>/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a <pkg>.TEST`. **Chế độ kiểm thử** tắt sau mỗi lần launcher khởi động lại ⇒ bật lại ở *Cài đặt › Hệ thống & quyền › Quyền › Chế độ kiểm thử qua adb* trước khi gõ lệnh.
> ⚠ Bẫy đã dính 27/09: `hal get` cho getter **không tham số** thì **KHÔNG** truyền `--es args` (truyền `args 0` ⇒ gateway tìm chữ ký `(int)` ⇒ `unavailable`). Đổi pref camera chỉ áp sau `camera none` → `camera left`.

## 0. Ở nhà (3 phút)
1. Tải sẵn `apk/Kachi-2.75-release.apk` (mạng bãi xe kém).
2. Đọc §2 một lượt; mọi việc camera đều làm được **không cần adb** qua Cài đặt (chip *Nguồn*, hàng *Dịch khung* −/+, xoay trái/phải) — adb chỉ để lấy số.
3. Hai terminal: `logcat -v time > logcat-2.75-<giờ>.txt` (buffer xe tràn sau ~30 s) + một gõ lệnh.

## 1. Nối adb + cài (5 phút)
```bash
mkfifo /tmp/kfifo; (nc -l 127.0.0.1 15555 </tmp/kfifo | nc <ip-xe> 5555 >/tmp/kfifo) &
$A connect 127.0.0.1:15555 && $A -s $S shell dumpsys package <pkg> | grep versionCode     # phải 175 trước khi cài
$A -s $S install -r Kachi-2.75-release.apk && $A -s $S shell dumpsys package <pkg> | grep versionCode   # 176
```
Bật *Chế độ kiểm thử*. Kiểm prefs camera còn nguyên bộ owner duyệt: `BR --es cmd prefs_get --es key camera_dewarp_focal` → 55 (scale 130, k 100, amount 100, cx = cy = 0, rot_left L90).

## 2. THỨ TỰ THEO GIÁ TRỊ/PHÚT

| # | Việc (bản) | Làm | Đạt khi | Lệnh chốt | Nếu sai → chép |
|---|---|---|---|---|---|
| **CAM-B5** | **[P0] BufferQueue abandoned** (2.75) | Xe đậu. Xi-nhan **trái → phải trực tiếp** (gạt qua giữa nhanh) 3 lần, rồi tắt; hoặc `BR --es cmd camera --es name left` → 3 s → `--es name right` → 3 s → `--es name none` | 30 s sau khi tắt: `logcat -s BufferQueueProducer:E` **im**; CPU launcher lúc nghỉ < 1 %; `KachiPerf` log < 20 KB/phút | `$A -s $S logcat -d -s BufferQueueProducer:E \| grep -c abandoned` → **0** | > 0 ⇒ chép 60 s logcat + `ps -T \| grep launcher` |
| **CAM-B6** | Jank sau trần 15 fps (2.75) | `$A -s $S shell dumpsys gfxinfo <pkg> reset` → xi-nhan trái 20 s (GL) → `dumpsys gfxinfo <pkg> \| grep -E "Total frames\|Janky\|95th\|99th"` | `Total frames` ≈ **một nửa** 2.74 (193 → ~100), `Janky` giảm rõ (2.74: 11,15 %, p99 61 ms); log `KachiCamera` có `fpsSkip=` > 0 | dòng gfxinfo | janky KHÔNG giảm ⇒ lỗi là **kích cỡ vẽ**, không phải nhịp ⇒ việc sau là `setPreviewSize` (RE §5 K5), không hạ trần thêm |
| **CAM-C1** | Nguồn **một kênh** (2.75) | Cài đặt › Camera › chip *Nguồn* = **Một kênh** (hoặc `BR --es cmd prefs_set --es key camera_source --es text CHANNEL`) → `camera none` → `camera left`; rồi phải | Hình **4:3 đứng, không kéo giãn**, không đen; log `nguồn=CHANNEL halMode=2` (trái) / `3` (phải); F55/S130 vẫn đúng như dải | `logcat -s KachiCamera \| grep "nguồn="` | đen hoặc `rc=false` ⇒ chip về *Toàn cảnh* (chưa có tự lùi), chép log |
| **CAM-C2** | Xoay dọc cả 2 bên | Trái giữ **L90** (đuôi ở dưới, đã chốt 27/09). Phải đặt **R90** rồi nhìn: đuôi xe có ở **dưới** không | Cả 2 bên: đuôi dưới, đường thẳng, không cong | log `overlay show … rot=` | phải bị ngược ⇒ thử L90, ghi lại giá trị đúng để làm mặc định `ClusterProfile` |
| **G7** | Núm **Dịch khung** (2.75, owner "kéo ra sau chút, không heavy") | Cài đặt › Camera › *Dịch khung* − từng bước **5** (hoặc `prefs_set camera_dewarp_pan_x -5 / -10 / -15`, mỗi lần `camera none`/`camera left`) — thử **cả 2 bên**: kiểm đuôi xe vào **cùng chiều** ở cả trái lẫn phải | Thấy thêm bánh sau, đường **vẫn thẳng**; giá trị anh chọn ghi lại (`read_back`) | `BR --es cmd prefs_get --es key camera_dewarp_pan_x` | bên phải chạy **ngược chiều** bên trái ⇒ ghi rõ (xem §4 dấu pan theo bên) |
| **UX8** | Chip trạng thái bằng hình (2.75) | Thanh trên có chip *Lấy gió* (Cài đặt › Thanh trên nếu chưa). Ở màn AC gốc bật/tắt **lấy gió trong** 3 lần; nhìn chip *Gió* lúc AUTO | Chip *Lấy gió* **đổi hình** (vòng trong khoang ↔ mũi tên từ ngoài), **không chữ** sau nhãn; chip *Gió* đọc **`auto 1`** chữ thường (ô bấm vẫn AUTO) | — (mắt) | không đổi hình ⇒ `logcat -s KachiHal \| grep -i cycle` |
| **E2'** | Voice "vào ô số N" sau vá (2.75; 27/09: 9 đúng/22) | 10 lượt *"mở vietmap vào ô số hai"* liền hơi; 3 lượt *"vietmap vào ô số một"* (KHÔNG động từ); 3 lượt *"mở vietmap hai"* | ≥ **8/10**; 3/3 mở đúng ô; 3/3 vào ô 2 | `logcat -s KachiVoiceSession \| grep "quyết định"` | < 8/10 ⇒ `BR --es cmd voice_dump --ez auto_confirm true` lấy zip WAV (lượt nối nay CÓ bản thu) |
| **E3** | VOICE-APP-NAME-FUZZY (2.73, **chưa kiểm xe**) | 3 lượt *"mở YouTube vào ô hai"* nhanh, nuốt đuôi | 3/3 YouTube ở ô 2 kể cả ASR ra "youtubex" | `heard` + `OpenApp(YouTube→ô 2)` | mở app khác ⇒ chép `heard` |
| **E10** | Hồi quy nhanh giọng nói (2.73, **chưa kiểm xe**) | Hey Kachi → *"mấy giờ rồi"*, rồi *"bật gió tự động"*; sau đó 2 câu qua **nút mic** | 3/3 đúng việc, nghe ngay < 1 s | `KachiVoiceTiming` | — |
| **TRIP** | `TripTimeSmoother` (2.75; HAL bậc 0,1 h) | Đậu ≥ **7 phút**, nhìn widget *Thời gian chuyến* mỗi phút; ghi giờ lúc HAL nhảy bậc | Phút **nhảy từng phút**; tới bậc HAL số **không lùi**, chỉ tiến; trước bậc đầu tiên sau khi mở máy có thể trễ ≤ 6 phút rồi bắt đúng | `BR --es cmd hal --es m getCurrentJourneyDriveTime --es dev BYDAutoInstrumentDevice --es op get` (KHÔNG `args`) | lùi ⇒ chép 2 số (widget, HAL) + giờ |
| **E12'** | Perf tổng | `perf-snapshot.sh oncar-275-idle 300 $S` sau 5 phút nghỉ, camera tắt | PSS launcher ≤ 70 MB (2.74: **75**), `:wake` ~237, `:tts` ~176 không tăng | JSON | tăng > 20 % ⇒ chép |
| **E11** | crash | — | 0 tệp crash | `ls /sdcard/Android/data/<pkg>/files/kachi-logs/ \| grep crash` | pull |

## 3. Câu chờ owner (trả lời khi nhìn xe)
1. **G7** — giá trị *Dịch khung* cuối cùng cho trái/phải (làm mặc định `ClusterProfile` Seal).
2. **CAM-C1** — có chuyển mặc định sang *Một kênh* không (ảnh đầy đủ hơn dải 25 % nhưng HAL đổi chế độ mỗi lần bật)?
3. **G8** — mã HAL `1003` khi `setPanoOperation(WORK_OFF)` (bit 31 = lỗi?) — chỉ đọc log, không cần anh làm gì.
4. Còn nguyên từ 2.74: VOICE-WRITE-LANE [P2] · ghép vế khi vế trước đủ nghĩa · OQ7 thẻ lốp · dedupe `ac_mode_auto`/`ac_wind_auto` · dấu thứ hai cho ghế · chip hồ sơ.

## 4. Ghi chú kỹ thuật cho người chạy
- **Dấu pan theo bên** [ĐO khung thô 27/09 `camera-frame-20260927-095818.png`]: dải trái thân xe ở góc **dưới-trái**, dải phải ở góc **dưới-phải** ⇒ hai camera **đối xứng gương**; "về phía đuôi" = −x ở dải trái, +x ở dải phải. Một núm chung phải được nhân dấu theo bên (đã chuyển reviewer 2.75 kiểm/vá) — G7 chính là phép đo chốt.
- **Chip Kết xuất**: GL vẫn là mặc định owner (F55/S130 chỉ có ở GL); TV = đường dự phòng 1 % janky.
- Không adb ⇒ mọi việc camera/UX8/TRIP làm qua Cài đặt + mắt; voice chỉ cần đếm; chụp *Cài đặt › Hệ thống › Nâng cao › Chẩn đoán* cuối buổi.

## 5. Mang về (2 phút)
`logcat-2.75-*.txt`, dòng gfxinfo CAM-B6, JSON perf, giá trị `read_back` pan/rot, zip voice nếu E2' < 8/10 ⇒ `docs/diagnostics/` (raw gitignored, commit bản .md tóm tắt + backlog cùng phiên, R2.1).
