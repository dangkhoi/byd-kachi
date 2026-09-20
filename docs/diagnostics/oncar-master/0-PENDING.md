# 0-PENDING — DANH SÁCH ĐẦY ĐỦ MỤC CẦN XE (Kachi 1.83 · 84)

> **Loại**: Diagnostics (on-car master index) · **Trạng thái**: Current · **Ngày**: 2026-09-20 · **Bản đích**: **1.83 (84)**
> **Mục đích**: gom **HẾT** mục `🚗 chờ xe` vào MỘT chỗ (đếm bằng máy, không đoán) — mỗi nhóm có **số lượng · ưu tiên · doc chứa cách-làm · outcome→off-car**. Đây là "list hết ra" owner yêu cầu 2026-09-20; **cách làm chi tiết** nằm ở 4 area doc + `RUNBOOK.md` (8 phase). **Off-car chỉ làm SAU khi có dữ liệu buổi này.**
> **Nhãn** (CLAUDE.md §2): `[ĐO source]` đọc được ở mã có `file:line` · `[ĐO xe]` số thật lượt trước · `[SUY]` · `[CHƯA BIẾT]`.

---

## 1. BẢNG TỔNG — 8 nhóm, ưu tiên theo (giá trị × rủi ro × phụ-thuộc)

| # | Nhóm | Số mục CẦN XE | Ưu tiên | Doc cách-làm | Thời lượng |
|---|---|---|---|---|---|
| A | **Kênh + bản + cầu sống** (tiền đề, chặn mọi thứ) | 3 tiền đề | ⭐ chặn | RUNBOOK Phase A · 4-system §0 | 20′ |
| B | **`:tts` kill — gốc bug mất phím** (1.79, CHƯA CHẠY XE) | 1 test (F3+F4) | ⭐⭐⭐ | RUNBOOK Phase F · 3-voice | 40′ |
| C | **Logcat A/B** (vietmap dẫn thật? music phát thật?) | 2 (+Δ2 mở-app) | ⭐⭐⭐ | RUNBOOK Phase C · 3-voice · §0.0 Δ2/Δ7 | 30′ |
| D | **HAL info — đọc telemetry** | **100 datum** (12 NEEDS_CAR không đường đọc + ~87 wired chưa xác nhận) | ⭐⭐ | 1-hal §1 (sweep) + captest tool | 40′ |
| E | **HAL action — ghi control** ("RE tiếp 34") | **34 control** (28 OVERDRIVE + 5 NEEDS_CAR + 1 AWAITING) | ⭐⭐ | 1-hal §2–6 · §0.0 Δ3/Δ3b/Δ4/Δ5/Δ6 | 70′ |
| F | **Hey Kachi** (có CỔNG load-guard) | 5 (load · crash · CPU · KWS · P2 marker) | ⭐⭐ (gated) | RUNBOOK Phase G · oncar-runbook-hey-kachi · §0.0 Δ8/Δ9 | 10–30′ |
| G | **Voice parser giọng thật** (3 fix 1.82) | 3 (Δ1 tìm-nhạc · Δ2 việt-máp · Δ3 điều-hòa-độ) | ⭐ | RUNBOOK Phase C · §0.0 Δ1–Δ3 | 15′ |
| H | **Cast + app-vào-ô** (chạm cụm — rủi ro cao nhất) | 9 option A–I + 5 cast-confirm | ⭐ (cuối) | 2-slot-cast | 60′ |
| I | **System** (K5 CPU · K8 phục hồi · P7/S5 HOME-reboot · F4 USB-dialog · W5 camera-keys) | 5 mảng | ⭐ | 4-system §1–5 | 90′ |

> **Tổng mục đếm được cần xe**: **100 info + 34 action + 1 :tts-test + 2 logcat + 3 parser + 5 Hey-Kachi + 14 cast + 5 system-mảng**. Con số owner nhớ "27" = **(N) ADAS-PURGE đã GỠ 27 mục** (10 nút + 17 info an toàn) — đó là *đã xoá*, KHÔNG phải pending. Pending thật lớn hơn nhiều (chủ yếu là 100 info + 34 action).

---

## 2. CHI TIẾT TỪNG NHÓM — items + cách-làm tóm tắt + outcome→off-car

### A · Tiền đề (chặn) — RUNBOOK Phase A, 4-system §0
3 thứ bật một lần đầu buổi: (1) kênh adb `adb_raw.py <car-ip> 5555`; (2) bản = **1.83** (thấp hơn ⇒ `40-ota.sh`); (3) cầu test-bridge + a11y booster + notif-listener sống. **FAIL A ⇒ mọi phase sau vô nghĩa.**

### B · `:tts` kill — gốc bug mất phím ⭐⭐⭐ (giá trị cao nhất)
- **1 test**: kill `:tts` GIỮA lúc đọc → `:app` pid KHÔNG đổi · a11y **Bound** · overlay đóng bình thường · câu sau đọc được (`:tts` pid mới). **[ĐO tombstone] Piper SIGSEGV từng giết CẢ launcher → mất binding phím** — 1.79 tách `:tts` để cô lập, **chưa từng chạy trên xe**.
- **Cách**: cần bản `vehicleTest` (run-as cùng uid mới kill được) — RUNBOOK Phase F3 thang-3-đường. Phím sống ≥10 lượt (F4).
- **Outcome**: 4-đúng ⇒ đóng nợ gốc, bỏ đề xuất chuyển System-TTS · `:app` đổi pid ⇒ đi đường (b) AndroidTtsSpeaker mặc định.

### C · Logcat A/B — thứ log WavProbe KHÔNG trả lời được ⭐⭐⭐
- **A vietmap**: `logcat -c` → "dẫn đường tới X bằng vietmap" → `grep 'START.*vietmap\|google.navigation'` → geocode ra `vietmaplive://…lat&lng` hay rớt GMaps? + VietMap **dẫn thật**? (C1/C1b — 4 option chữ nếu geocode fail).
- **B music (Δ7)**: `logcat -c` → "mở bài hát X" → `grep MediaSession\|PLAY_FROM_SEARCH\|youtube` + **NGHE** → phát thật? UA desktop 1.79 có trả videoId trên mạng xe?
- **Outcome**: A đạt ⇒ đổi `coordEvidence` VietMap MEASURED · A fail ⇒ 4 option chữ (B1 ACTION_SEND thắng lớn) · music fail ⇒ trace resolver UA/mạng xe.

### D · HAL info — đọc 100 telemetry — 1-hal §1
- **13 NEEDS_CAR (không có đường đọc)**: `cell_v_high` `cell_v_low` `target_soc` `coolant_temp` · `tyre_t_fl/fr/rl/rr` (4 nhiệt lốp) · `gps_lat/lon/elevation/heading` (4 GPS). → sweep tìm getter/feature-id THẬT (`featmap`), có ⇒ nối off-car; không ⇒ ẩn.
- **Bụi mịn NGOÀI xe** (`pm25_outside`) — ⚠ **1.85 ĐÃ NỐI, việc trên xe đổi từ "đi tìm getter" sang "xác nhận một con số"**. Câu hỏi 1.84 đặt sai chỗ: không có getter riêng nào (grep 0 trên toàn bộ cây decompile + javadoc SDK), vì `getPM2p5Value()` trả **`int[]` hai ô** — [ĐO nguồn] javadoc chính thức BYD: *"first = value **in** auto, second = value **out** of auto"*; callback `onPM2p5ValueChanged(value_in, value_out)` và [ĐO car log] `in:8 out:22` khớp. Nay `pm25_value` lấy ô [0], `pm25_outside` lấy ô [1] (`HalReadTables.ARRAY_INDEX`). **Trên xe cần ĐÚNG MỘT lượt**: `hal --es op getid --es dev BYDAutoPM2p5Device --es m getPM2p5Value` → ra `[a, b]` với b ≈ 22 khi a ≈ 8 ⇒ đổi tier `NEEDS_CAR → PROVEN`. Ra một số ĐƠN ⇒ trim chỉ đo trong cabin, ô ngoài xe hiện "—" (CỐ Ý không lùi về ô [0] — lùi là hiện số trong cabin dưới nhãn ngoài xe) ⇒ ẩn datum.
- **Nhiệt cài đặt** (`inside_temp`) — 1.85 sửa `area` 0 → 1 (`AC_TEMPERATURE_MAIN`): [ĐO nguồn] javadoc `getTemprature(area)` chỉ nhận 1/2/3/4, còn 0 (`MAIN_DEPUTY`) là *type* của đường GHI ⇒ đọc area 0 trả sentinel, đúng ca [ĐO xe 2026-09-20 §4] *"nhiệt cài đặt = X"*. Trên xe: đọc lại datum này ⇒ phải ra số 17..33 khớp màn AC.
- **Nhiệt trong cabin** (`cabin_temp`) — **CHƯA sửa, có lý do**: [ĐO nguồn] không thiết bị nào phơi getter nhiệt ĐO ĐƯỢC trong cabin (AC chỉ có `getTemprature` = setpoint; Instrument có `getOutCarTemperature` = ngoài xe) ⇒ nghi `AmapService: temp=22` chính là setpoint. Trên xe: đọc feature `1031798832` trên device AC rồi **đổi setpoint** và đọc lại — hai số dính nhau ⇒ ô này TRÙNG `inside_temp`, nên BỎ chứ không nối thêm getter.
- **~87 wired chưa xác nhận trên xe owner**: quét `hal --es op read-all` một phát → đối chiếu giá trị thật. [ĐO] xe owner bị chặn ĐỌC do provision (một số chỉ OK trên S6) ⇒ ghi rõ đọc-được/sentinel/unavailable.
- **Cách**: 1-hal §1 giao-thức-sweep (read-all → route-all) HOẶC captest tool trong app (đi tuần tự, hiện giá trị từng mục). **Outcome**: sentinel ⇒ trim không có, ẩn · unavailable ⇒ gõ lại với `--es dev` đúng device · số thật ⇒ ✓.

### E · HAL action — ghi 34 control ("RE tiếp 34") — 1-hal §2–6 ⭐⭐
- ⚠ **1.85 · BỐN việc MỚI, ưu tiên cao — route đã nối off-car, cần xác nhận HÀNH VI VẬT LÝ** (bẫy giá trị ĐẢO ở cả hai: rc=0 mà xe làm ngược là lỗi *im lặng*, chỉ người ngồi trong xe thấy):
  1. **Gió tự động** (`ac_auto` → `Ac.AC_CTRL_MODE_SET`, ghi **0 = AUTO** / 1 = tay): bật nút trên Kachi ⇒ màn AC phải hiện gió AUTO; tắt ⇒ về chỉnh tay. ⚠ nút nay mang nhãn **"Gió tự động"** (không còn "Điều hòa AUTO") — owner xem nhãn có chấp nhận được không.
  2. **Chỉ báo gió auto** (`ac_wind_auto` → `getAcWindLevelManualSign`): đọc khi AUTO ⇒ **0**, khi tay ⇒ 1. Lệch ⇒ gỡ `readInverted`.
  3. **Khoá trẻ em TRÁI** (`child_lock`, ghi **2 = BẬT** / 1 = TẮT) và **PHẢI** (`child_lock_r`): bật từng bên ⇒ **thử mở cửa sau bên đó từ BÊN TRONG** (đó là thứ khoá trẻ em làm; nhìn đèn/nghe tiếng là không đủ). Nếu bấm trái mà cửa PHẢI khoá ⇒ hai id bị đảo.
  4. **Khoá cửa chính** (`lock`/`door`) — **KHÔNG wire ở 1.85, có lý do**: [ĐO xe 2026-09-20 §3] `DOOR_LOCK_COMMAND_AREA_LEFT_FRONT`(960495668) `setev` trả **rc=-2147482648 (NOT_PROVISIONED)** và cửa không phản ứng; main-lock **không có biến thể `_SET`**; named setter `setDoorLockState` cũng X (CAPTEST). ⇒ nghi trim CHẶN door-lock qua HAL app (an ninh). Hai nút giữ `NEEDS_CAR` + setter cũ để lượt sweep ghi lại đúng chuỗi ngoại lệ. Trên xe: `featmap` xem set của device DOOR_LOCK(1041) có id khoá/mở nào khác không; không có ⇒ ghi BẤT KHẢ (kèm bằng chứng) chứ không đoán thêm giá trị.
- **28 OVERDRIVE** (có đường HAL, chưa xác nhận xe làm) + **5 NEEDS_CAR** (chưa có setter) + **1 AWAITING_CAR**.
- **Nhóm RE tiếp** [ĐO feature-filter §1]: cốp/khoá/cửa (không setter) · điều hoà AUTO/sấy sau/ion/sưởi vô-lăng/lọc-bụi (route) · đèn pha/viền · EV-HEV/tái tạo · pin (batt_temp, cell_v, target_soc) · GPS · cast/xoay/góc camera · mã máy/nước làm mát/dầu.
- ⚠ **3 bug id-trùng đã tra** [ĐO 1-hal §0.3]: **`hud_brightness` đang đổi ĐỘ SÁNG MÀN CHÍNH** (`1276174360`=SET_BRIGHTNESS_GEAR, không phải HUD) — phải thành MODE hoặc gỡ; `camera_view`→`setDisplayMode` (method không tồn tại); `headl` on/off không có id.
- + **Δ3/Δ3b** điều-hòa-X-độ→temp (giọng thật) · **Δ4** mở-kính-1-cửa · **Δ5** cốp-gate-tốc-độ (cần xe lăn + người thứ 2) · **Δ6** read-back.
- **Cách**: 1-hal §1 sweep (write named-method + confirm → NHÌN xe → đọc lại datum). ⚠ **rc=0 ≠ xe làm** — phải NHÌN. **Outcome**: mỗi mã → PROVEN (giữ) / sửa route (off-car) / gỡ-ẩn (sentinel/không id).

### F · Hey Kachi — ⚠ CÓ CỔNG, đừng đo mù — RUNBOOK Phase G
- **BƯỚC ĐẦU BẮT BUỘC**: đo load nền 4 trạng thái (idle/vietmap/gmaps-in-slot/gmaps+vietmap). [ĐO 18-09] load 10–14; `VoiceLoadGuard` chặn inference khi load **>6 THÔ** (không chia lõi) ⇒ "Hey Kachi" **0/20 bất kể model**. Load cao ⇒ **KHÔNG đo độ chính xác, về sửa guard chia-lõi off-car trước** (âm-tính-giả, mất cả buổi nếu bỏ qua).
- 5 mục: (1) load 4-trạng-thái · (2) `:wake` cô lập crash native (như `:tts`) · (3) CPU `:wake` · (4) KWS nhận "Hey Kachi" giọng Việt (chỉ khi cổng qua) · (5) **Δ8/Δ9 P2 marker**: ép auto-tắt → công tắc hiện OFF (không ON dởm) + setting biển-tốc-độ KHÔNG bị `:wake` xoá.

### G · Voice parser giọng thật (3 fix 1.82) — §0.0 Δ1–Δ3
Δ1 "tìm bài hát X"→Media · Δ2 "mở việt máp/mép/mốp"→VietMap (không drawer) · Δ3 "điều hòa 25 độ"→temp. Đo bằng **mic thật** (say chỉ test parse, không test ASR). Gộp chung Phase C/D.

### H · Cast + app-vào-ô — 2-slot-cast ⭐ (để cuối, chạm cụm) 
- **9 option A–I** đưa app (Waze) vào ô: C (freeform display 0 — probe chính), D (move-task — cơ chế proven chưa thử cho ô), G (nhờ ROM tạo display), H (mod APK) là các cửa còn mở; A/B/E/F đã loại. → chạy §4 thứ-tự-một-buổi (rẻ→đắt).
- **5 cast-confirm** (X2, sau bản sửa chưa đo): dò display cụm động · `am start --display` còn bị chặn? · fallback move-task · geometry/bóng VietMap · SpeedBadge gắn đúng màn.

### I · System — 4-system §1–5
K5 (tải hệ thống sau 1.67: CPU/RSS/HAL-per-phút) · K8 (3 nhánh phục hồi máy-ảo-không-dựng-được) · P7+S5 (HOME sống qua **reboot NÚT NGUỒN VẬT LÝ** — 2 lượt) · F4 (hộp "Cho phép gỡ lỗi USB" lần mở đầu) · W5 (mã phím khi app camera tiền cảnh).

---

## 3. THỨ TỰ CHẠY MỘT BUỔI (nếu đủ thời gian: A→B→C→D→E→F→G→H→I)
Nếu **ít thời gian**, chạy tối thiểu: **A** (tiền đề) → **B** (:tts kill = bug gốc) → **C** (logcat A/B) → **F bước-load** (quyết Hey Kachi) → **E một phần** (3 bug id-trùng + Δ4/Δ5 an toàn thân xe). D/H/I để buổi sau.

## 4. SAU BUỔI XE → OFF-CAR (không làm trước khi có dữ liệu)
Mỗi outcome ở §2 map thẳng sang một việc off-car: sửa route HAL (E) · nối getter datum tra được (D) · sửa `hud_brightness`→MODE (E bug id-trùng) · guard load chia-lõi + host model (F) · đổi VietMap dispatch (C) · option cast thắng (H). **Ghi kết quả buổi xe vào ô của từng phase rồi mới mở việc off-car tương ứng.**
