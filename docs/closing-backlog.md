# Closing Backlog — Kachi launcher

> **Trạng thái**: Current · **Tạo**: 2026-09-22 · **Mục đích**: gom MỌI việc còn lại để đóng dự án, sau khi phiên off-car 1.90 (91) đã ship 16 việc owner.
> Nguồn: chắt từ `docs/PROJECT-BACKLOG.md`. Cập nhật trạng thái tại đây khi làm.

Mốc hiện tại: **1.90 (91)** đã OTA + cài emulator. Phần lớn UI/dọn/voice-parse đã xong; còn lại chủ yếu **cần xe** hoặc **việc lớn**.

---

## A. CẦN OWNER NỔ MÁY VERIFY (từ 1.90 — code đã sửa, chỉ chờ xác nhận trên xe)

| # | Việc | Verify gì | KQ 2026-09-22 |
|---|------|-----------|---------------|
| A1 | Taskbar ROM lòi lúc boot | Nổ máy → taskbar KHÔNG lòi | ✅ OK |
| A2 | Bóng VietMap không lên khi boot | Nổ máy → bóng TỰ lên trên cụm | ⚠ VietMap lên nhưng **KHÔNG có bóng trên cụm** → **TRACE TRÊN XE** (xe offline lúc kiểm): kiểm `VMBluetoothService` có chạy + `byd_float_app_list` có VietMap + VietMap bản MOD hay gốc (bóng chỉ có ở bản MOD). Fix #11 chờ service đó nhưng nếu service không lên thì vô ích |
| A3 | YouTube trong ô đen sau boot | app hiện hình | ✅ OK |
| A4 | Chip header đổi màu | sáng/mờ, hết chữ Tắt/Bật | ⬜ chưa test |
| A5 | Control realtime | chỉnh nhiệt/gió → launcher ~1s | ✅ OK |
| A6 | "mở hết cửa sổ" mở cả 4 | | ✅ OK |

---

## B. LÀM ĐƯỢC OFF-CAR NGAY (ưu tiên — biến "chưa chắc" thành "chắc")

| # | Việc | Ghi chú |
|---|------|---------|
| B1 | **P8-BOUND** — vòng kiểm quyền lúc start kiểm accessibility **BOUND** (không chỉ ENABLED); chưa bound → tự toggle rebind ngay, không báo lỗi | `PermissionPreflight`/`LauncherRequirements` + `FixBy.SELF`; đọc `dumpsys accessibility` "Bound services" |
| B2 | **BIND-SELFHEAL** — vòng NỀN định kỳ (~15-30s, trong FGS đã chạy) tự kiểm enabled-nhưng-không-bound → tự toggle rebind → phím tự sống lại giữa lúc lái khi CPU cao MÀ không cần mở app | Đi cùng B1. Verify off-car bằng test parse dumpsys; on-car khi tải cao |
| B3 | **LOC-500** — nợ trần 500 dòng: tách theo VAI `SimpleCastCoordinator.kt`(784) · `BydHal.kt`(654) · `FloatingBubbleService.kt`(537) · `VietMapWidgetBridge.kt`(505) · `AppDrawer.kt`(508) · `KachiHomeActivity.kt`(552) · `VoiceCapture.kt`(499) | Dọn kỹ thuật, không đổi hành vi |
| B4 | **WIDGET-FONT** (owner 2026-09-22) — số áp suất/nhiệt lốp (và các widget thông tin) hiện **quá bé**; cần **min/max font size co giãn theo cỡ khung** (khung to chữ to, khung nhỏ chữ vừa đọc được, không bé quá) | Áp cho mọi widget đọc số; `TyreBoardView`/`WidgetTelemetry`/mini-card |
| B5 | **WIDGET-MINI** (owner 2026-09-22) — thông tin xe đưa vào **khung NHỎ**: icon bị **nhỏ quá mức** + **chữ mất tiêu**; cần sàn icon + luôn giữ chữ đọc được (hoặc bỏ icon, ưu tiên số) khi khung nhỏ | `telemetryMini`/`MiniCard`/lưới compact |
| B6 | **HEADER-WIDTH** (owner 2026-09-22) — header bar đang **fix width từng icon/chip** ⇒ thông tin dài bị CẮT (vd "15.9 kWh/50km" còn "15.9 kWh…"); icon (sấy...) thừa khoảng trắng 2 bên. Cần bỏ fix-width, để **margin/wrap_content** — chip rộng theo nội dung, icon không thừa lề | `KachiTopStrip.fitChips`/`applyChipFace` |
| B7 | **HEADER-CAP-10** (owner 2026-09-22) — nâng trần header bar **8 → 10** item | `TopStripConfig.CAP` + KDoc lý do |
| B8 | **HEADER-ARROW** (owner 2026-09-22) — nút ◀▶ dời vị trí chip **quá bé, khó nhấn, nhấn nhầm lại ẩn mất chip**. Cần: đích chạm to hơn + tách hẳn khỏi vùng chạm toggle (nhấn ◀▶ KHÔNG được vô tình tắt chip) | `TopStripPicker.arrowBtn` + tách sự kiện |
| B9 | **HEADER-SEAT-CHIP** (owner 2026-09-22) — thêm ghế mát/sưởi lên header bar: hình ghế + mức, nhưng **chỉ số "2"** (bỏ chữ "Mức" cho đỡ chật) | chip cho `seatc`/`seath` với nhãn số ngắn |
| B10 | **SEAT-PASSENGER** (owner 2026-09-22) — ghế mát/sưởi hiện **chỉ có ghế LÁI**, chưa có ghế PHỤ. Thêm nút ghế phụ (seatID 2) | `ControlRegistry` +`seatc_r`/`seath_r` (RE seatID phụ) — ⚠ có thể cần verify HAL trên xe |
| B11 | **WINDOW-HALF/EACH/ALL** (owner 2026-09-22) — chưa có nút **mở 50% kính**, **từng kính**, **tất cả kính** | records: đường GHI % (`setBodyWindowCtrlState` state 4 = HALF) proven; nút từng kính đã có (win_lf/rf/lr/rr), thiếu nút HALF + gộp — spec §4.5 |

---

## C. VIỆC LỚN OFF-CAR (cần công sức/thu mẫu — quyết sau)

| # | Việc | Ghi chú |
|---|------|---------|
| C1 | **KWS-VI** — fine-tune/thay model KWS tiếng Việt cho Hey Kachi | Gốc: gigaspeech tiếng Anh không bắt "Kachi" giọng Việt. Wiring đã xong (model trong APK, service tự dựng). Cần thu mẫu + train keyword |
| C2 | **ASR-VI-TONE** — model nghe hụt âm cuối/thanh nặng ("đãng") | Cần model nghe tiếng Việt tốt hơn (tên bài là từ-vựng-mở, không sửa bằng luật) |
| C3 | **V-TTS / voice pha 2** — gói giọng ĐỌC phản hồi ("Đã tăng nhiệt độ 24...") | Cần đăng asset TTS Piper (13 tệp `voice/tts/piper-vi_VN-vais1000-medium/`) + đo `isLanguageAvailable(vi)` trên xe. T8 giải nén không cần (tải từng tệp) |
| C4 | **W (voice-clone)** — giọng con gái owner làm giọng phản hồi | Gói 1546 clip đang dựng nền; cần T3(:core) + T6 danh mục sha256 + T7 ClipSpeaker + T8 Cài đặt + T9 đăng gói + 🚗 T10 độ trễ AAC |
| C5 | **VOICE-FEMALE-SN** (owner 2026-09-22) — ✅ **Phần 1 DONE (2.01)**: BỎ HẲN giọng bé (voice-clone C4) + mọi UI/code/prefs/catalog/test/gói-đĩa. 🔬 **Phần 2 NOTE nghiên cứu sau**: model TTS giọng NỮ MIỀN NAM. | **[KHẢO 2026-09-22]** KHÔNG có model nào vừa NHẸ (~60MB VITS/Piper 1-file, cắm thẳng sherpa như hiện tại) VỪA giọng nữ Nam sẵn: sherpa/Piper chính thức chỉ 1 giọng Việt = `vais1000` (đang dùng). Các model CÓ giọng nữ Nam đều **flow-matching/clone, nặng + chậm hơn ~30-50× + kiến trúc khác (không cắm thẳng)**: KorvaTTS `bao_kim` (Apache-2.0, Supertonic 99M, `vector_estimator.onnx` 256MB, ~300MB tổng); **VieNeu-TTS v3 Turbo/Nano** (Apache-2.0, giọng nữ Nam **Mỹ Duyên/Kim Thanh/Thục Đoan**, torch-free ONNX-CPU nhưng RTF≈0.55 Turbo / 0.11-0.22 Nano trên i5 desktop — chip xe yếu hơn → rủi ro trễ; Nano ~282MB, chất lượng thấp hơn). Dataset nữ-Nam CodeLinkIO 10h = "research only" (license) + phải tự train. ⇒ **Giọng nữ Nam chất lượng = phải chấp nhận nặng/chậm + port engine mới.** Nếu làm: (1) ưu tiên VieNeu v3 Nano; (2) **ĐO RTF trên xe TRƯỚC** khi port (RTF>1 = vô ích); (3) hoặc train Piper nữ-Nam (cần dataset+GPU+license). |

---

## D. CẦN XE (RE / verify HAL — không làm được off-car)

| # | Việc | Ghi chú |
|---|------|---------|
| D1 | **Camera 360** — HAL kích được (AVM chạy) nhưng KHÔNG surface lên màn | RE lại đường điều khiển AVM view thật, hoặc ẩn nếu quá khó |
| D2 | **Datum sentinel** — RE getter đúng: `trip_km/hours/kwh` (=-10013), `sunroof_pos`/`volt_12v_level` (=65535), `tailgate_status` (PROVEN nhưng rỗng) | Sweep getter trên xe |
| D3 | **H1-T2b** — nối đường ĐỌC cho ~27 nút còn lại (hiện 17/47) | `cam`/`pm25`/`steer_heat` nối được ngay off-car; còn lại cần `featmap`/HAL trên xe |
| D4 | **L-RE** — verify feature-id số + method theo trim trên xe | catalog ≈187 feature-id đã thu; còn verify theo trim |
| D5 | **X1 cast-side** — dọn kiến trúc cast (gộp geometry-writer vào FreeformSeedPolicy, cast-side AppLocationRegistry) | Không E2E-verify off-car được |
| D6 | **INPUTD** — chốt sepolicy `avc denied connectto` trên ROM DL3 thật (mới đo máy ảo) | 3 lệnh ~1 phút; owner quyết gỡ daemon hay giữ |
| D8 | **PM25-AUTO-INVESTIGATE** (owner 2026-09-22) — bật lọc bụi tự động thì trong xe cứ 1 lúc lại KÉM rồi lại lọc (dao động); TẮT thì đi lâu vẫn thấy PM2.5 TỐT. Nghi `setAutoCleanAirState(1)` trên trim owner KHÔNG phải "tự lọc" mà là "tự trao đổi khí / lấy gió ngoài định kỳ" ⇒ kéo bụi ngoài vào → kém → quick-clean bù → cưa răng. Cần trace on-car: bật lọc rồi đọc `getPM2p5Level` + trạng thái recirc/lấy-gió theo thời gian; so với TẮT. Nếu đúng ⇒ BỎ `setAutoCleanAirState`, chỉ dùng poll+`setQuickCleanAirState` (hoặc bỏ luôn auto, chỉ giữ nút "Lọc ngay"). ⚠ ĐỌC (`getPM2p5Level`) không có vẻ sai — nghi ở lệnh GHI `setAutoCleanAirState`. |
| D7 | **Voice on-car (đã trace, chờ owner cho làm)** — VietMap dẫn (parser tách "bằng <app>") + YT/YT Music auto-play (đổi UA desktop) | Doc `oncar-voice-music-vietmap-2026-09-18.md` |

---

## E. NHỎ / TÙY OWNER

| # | Việc | Ghi chú |
|---|------|---------|
| E1 | DRL read-back báo sai "xe không nhận lệnh" khi tắt (dù tắt được) | On-car timing — tune trên xe |
| E2 | Ca-pô (hood) — không có datum đọc → chưa vẽ trong bảng Cửa & khoang | OQ2 chờ owner |
| E3 | Model fp32→int8 di trú | Xe đang chạy fp32 thì lần đầu bấm mic sau OTA báo "chưa tải" trong lúc tải int8 |
| E4 | Nút chiếu-cụm gọi lại pipeline cast (P6) | Cần xe |
| E5 | Cluster nav / HUD zin (firmware gate `0x38B00030`) | Đường coding firmware, không phải app — ADR 0002 |

---

## Ưu tiên đề xuất
1. **B1+B2** (binding phím chắc ăn) — off-car ngay, đúng lo ngại owner.
2. **A1–A6** — owner nổ máy verify 1.90 (không tốn công dev).
3. **B3** (dọn trần 500) — kỹ thuật, làm khi rảnh.
4. **C/D** — quyết theo lịch (thu mẫu / có xe).
