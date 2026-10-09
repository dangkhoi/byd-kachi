# Seal 09/10 — biển tốc độ ADAS · cờ HUD trên 2606 · tắt màn · ô YouTube trắng · cụm sau khởi động lại

> **Trạng thái**: Evidence · **Cập nhật**: 2026-10-09 · **Chủ**: dangkhoi · **Xe**: Seal, DiLink3, fw `eng.build.20260610` (2606 = OTA V2.0.4), Kachi 2.97 (200)
> **Liên quan**: backlog `ISA-NATIVE-SIGN` · `HUD-ROADNAME-OEM-CADENCE` · `PERF-HOME-HAL-SCREEN-OFF` (2.98 R6-C) · `VM-SILENT-BG-CLUSTER` ·
> `docs/diagnostics/oncar-freeform-waze-2026-10-09.md`. Mọi lệnh dưới đây CHỈ ĐỌC (cầu kiểm thử Kachi `hal getid`, `dumpsys`, `logcat -d`).
> Log xe (có dữ liệu xe) không lưu vào repo.

## 1. Biển tốc độ — ADAS (đọc lại bằng đúng tên lớp)

Script 08/10 (`scripts/vehicle/isa-speedlimit-2606.sh`) gọi sai lớp `BYDAutoAdasDevice` (đúng: `BYDAutoADASDevice`) ⇒ pha đọc ADAS
hôm đó **không chạy** (`BydHalGateway … getInstance: null`). Đã sửa script 09/10 (7 chỗ).

| Mục | Giá trị [ĐO xe 09/10] | Nghĩa [ĐO nguồn fw 2606] |
|---|---|---|
| `ADAS_SLA_STATE` | 1 | `ADAS_SLA_STATE_FUSION_MODE` (`BYDAutoADASDevice.java:21-25`: 0 off · 1 fusion · 2 vision · 3 NV · 4 defect) |
| `ADAS_TSR_SPEED_LIMIT_MAP_CONFIG` | 1 | **TSR KHÔNG dùng bản đồ** — chỉ 2 = TRUE (`TsrSpeedLimitMapProperty.java:12-23`, DiCarServer 3.4.0) |
| `ADAS_INTELLIGENT_SPEED_LIMIT_INFORMATION_GRAY` | 0 | không khoá xám |
| `ADAS_INTELLIGENT_SPEED_LIMIT_CONTROL_GRAY` | 0 | không khoá xám |
| `ADAS_SLA_OUTPUT_SPEED_LIMIT` | — | tên không có trong bảng feature của cầu (`bad_feature`) |

⇒ [SUY mạnh] ECU ADAS Seal được cấu hình không trộn giới hạn từ bản đồ ⇒ khớp kết quả 08/10 (`setIsaMap*` rc=0, cụm/HUD không hiện).
Mở lại cần coding ADAS (đặt 2) — ngoài tầm Kachi. Hướng còn lại: bơm `0x2370002E` trực tiếp (đổi trạng thái, cần owner duyệt; chưa làm).

## 2. Cờ HUD trên 2606 — không đổi so với đo tháng 8 (fw cũ)

| Id | Thiết bị đọc được | Giá trị [ĐO xe 09/10] |
|---|---|---|
| `0x38B00015` (W-HUD) | Setting | 1 |
| `0x38B00028` (dẫn đường động) | Setting | 1 |
| `0x420A1010` (check-state tên đường) | Instrument | 0 |
| `0x38B00030` · `0x30100030` · `0x8e2fcdbf` · `0xd61b6746` · `0x32B1102E` | Instrument / Setting / Bodywork | sentinel `-2147482648` (HAL từ chối / không có) |

⇒ 2606 không mở thêm gì cho HUD dẫn đường trên Seal. Firmware 2602→2606 không đổi đường tên đường (`BYDAutoInstrumentDevice`,
AmapService cùng sha1) [ĐO so mã — báo cáo RE trong phiên 09/10]. Owner 09/10: SL6 dùng chung firmware 2606.

Nghe CAN (`NavOpen canmon`) **không phải chỉ đọc**: cần ghi bảng id vào MCU (`sendRegTable`), thời hạn sống của bảng [CHƯA BIẾT] ⇒ chưa chạy.

## 3. Tắt màn bằng nút (R6-C)

`logcat -b events` [ĐO xe 09/10]:

- 12:58:40.521 `power_screen_state: [0,2,0,0,1132]` · `screen_toggled: 0` — Android ngủ thật, `mLastSleepReason=force_suspend`.
- 12:58:40–41 `am_kill … stop com.google.android.youtube` rồi `stop com.byd.launcher` (+ `:wake`, `:tts`) — BYD giết app khi ngủ.
- 13:02:07.948 `screen_toggled: 1` · 13:02:09 `power_screen_state: [1,0,0,0,1427]`.

⇒ Tín hiệu R6-C (`isInteractive` + trạng thái display 0) đổi đúng khi tắt màn bằng nút. adb Wi-Fi rớt trong lúc ngủ (mạng đầu xe tắt).

## 4. Ô YouTube trắng sau khi bật màn lại

- 13:02:08 Kachi dựng lại ô 0 (`kachi-slot-0-…`, display 4), 13:02:11 mở YouTube vào ô.
- 13:02:13 YouTube **tự sập**: `FATAL EXCEPTION: GoogleApiHandler` · `java.util.ConcurrentModificationException` ·
  `at com.kangrio.extension.shared.spoof.SpoofUtil.spoofSignature(SpoofUtil.java:99)` — mã giả chữ ký của bản YouTube mod, không phải Kachi.
- Ô còn màn ảo trống ⇒ trắng. Kachi có chuyển ô về trong suốt / widget đã lưu sau khi app chết không: log dừng 13:02:10 ⇒ [CHƯA BIẾT] — soát trên máy ảo.
- Cùng lúc: hai task `KachiHome` (2, 11) trên display 0 — tình huống 2.98 R3.

## 5. Khởi động lại 11:36 / 12:32 và cụm sau khởi động lại

- `sys.boot.reason=reboot` cả hai lần, dropbox không có `SYSTEM_TOMBSTONE`/watchdog/kernel panic trước đó ⇒ khởi động lại có lệnh, nguồn [CHƯA BIẾT].
  11:03:38 có `SYSTEM_RESTART` (lõi Android chạy lại, không phải cả máy).
- Sau 11:37: `VietMapAutostart` silent-bg (`monkey`, không chỉ định màn) ⇒ 11:37:06.651 `am_create_activity` VietMap task 4 resume trên
  display 2 (cụm) trước khi Kachi chiếu ⇒ Kachi ghim VietMap toàn cụm, tự chia đôi không chạy. Backlog `VM-SILENT-BG-CLUSTER` (owner: để sau).

