# Closing Backlog — Kachi launcher

> **Trạng thái**: Current · **Tạo**: 2026-09-22 · **Mục đích**: gom MỌI việc còn lại để đóng dự án, sau khi phiên off-car 1.90 (91) đã ship 16 việc owner.
> Nguồn: chắt từ `docs/PROJECT-BACKLOG.md`. Cập nhật trạng thái tại đây khi làm.

Mốc hiện tại: **1.90 (91)** đã OTA + cài emulator. Phần lớn UI/dọn/voice-parse đã xong; còn lại chủ yếu **cần xe** hoặc **việc lớn**.

---

## A. CẦN OWNER NỔ MÁY VERIFY (từ 1.90 — code đã sửa, chỉ chờ xác nhận trên xe)

| # | Việc | Verify gì |
|---|------|-----------|
| A1 | Taskbar ROM lòi lúc boot | Nổ máy → launcher lên, taskbar KHÔNG lòi/đẩy layout, không phải nhấn Home |
| A2 | Bóng VietMap không lên khi boot | Nổ máy → bóng VietMap TỰ lên trên cụm (không cần bấm) |
| A3 | YouTube trong ô ĐEN sau boot | Nổ máy → app trong ô hiện hình (không đen) |
| A4 | Chip header đổi màu | Bật/tắt (sấy kính, đèn...) → chip sáng (bật) / mờ (tắt), không còn chữ Tắt/Bật |
| A5 | Control realtime | Chỉnh nhiệt/gió trên màn xe → launcher đổi trong ~1s |
| A6 | "mở hết cửa sổ" | Nói/gõ → mở CẢ 4 kính (không chỉ bên lái) |

---

## B. LÀM ĐƯỢC OFF-CAR NGAY (ưu tiên — biến "chưa chắc" thành "chắc")

| # | Việc | Ghi chú |
|---|------|---------|
| B1 | **P8-BOUND** — vòng kiểm quyền lúc start kiểm accessibility **BOUND** (không chỉ ENABLED); chưa bound → tự toggle rebind ngay, không báo lỗi | `PermissionPreflight`/`LauncherRequirements` + `FixBy.SELF`; đọc `dumpsys accessibility` "Bound services" |
| B2 | **BIND-SELFHEAL** — vòng NỀN định kỳ (~15-30s, trong FGS đã chạy) tự kiểm enabled-nhưng-không-bound → tự toggle rebind → phím tự sống lại giữa lúc lái khi CPU cao MÀ không cần mở app | Đi cùng B1. Verify off-car bằng test parse dumpsys; on-car khi tải cao |
| B3 | **LOC-500** — nợ trần 500 dòng: tách theo VAI `SimpleCastCoordinator.kt`(784) · `BydHal.kt`(654) · `FloatingBubbleService.kt`(537) · `VietMapWidgetBridge.kt`(505) · `AppDrawer.kt`(508) · `KachiHomeActivity.kt`(552) · `VoiceCapture.kt`(499) | Dọn kỹ thuật, không đổi hành vi |

---

## C. VIỆC LỚN OFF-CAR (cần công sức/thu mẫu — quyết sau)

| # | Việc | Ghi chú |
|---|------|---------|
| C1 | **KWS-VI** — fine-tune/thay model KWS tiếng Việt cho Hey Kachi | Gốc: gigaspeech tiếng Anh không bắt "Kachi" giọng Việt. Wiring đã xong (model trong APK, service tự dựng). Cần thu mẫu + train keyword |
| C2 | **ASR-VI-TONE** — model nghe hụt âm cuối/thanh nặng ("đãng") | Cần model nghe tiếng Việt tốt hơn (tên bài là từ-vựng-mở, không sửa bằng luật) |
| C3 | **V-TTS / voice pha 2** — gói giọng ĐỌC phản hồi ("Đã tăng nhiệt độ 24...") | Cần đăng asset TTS Piper (13 tệp `voice/tts/piper-vi_VN-vais1000-medium/`) + đo `isLanguageAvailable(vi)` trên xe. T8 giải nén không cần (tải từng tệp) |
| C4 | **W (voice-clone)** — giọng con gái owner làm giọng phản hồi | Gói 1546 clip đang dựng nền; cần T3(:core) + T6 danh mục sha256 + T7 ClipSpeaker + T8 Cài đặt + T9 đăng gói + 🚗 T10 độ trễ AAC |

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
