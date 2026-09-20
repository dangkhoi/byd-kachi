# RE task — 23 mục CẦN + on-car check (owner triage 2026-09-20)

> **Trạng thái**: Current · **Nguồn**: owner triage list `chưa-OK` (2026-09-20) sau phiên on-car 1.84.
> Owner chốt: các mục **CẦN** (chưa hoạt động nhưng cần) → gom **1 task RE**, RE off-car (featmap + đọc source) → **lên xe check từng cái** qua cầu `hal` (đã fix `-n`). Mục **BỎ** → xoá khỏi dự án (doc riêng/purge).
> Cầu on-car: `am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd hal --es op getid|setev --es dev <Device> --es m <id|TÊN>`. Featmap: `/sdcard/Android/data/com.byd.launcher/files/kachi-logs/featmap-20260916-185942.json`.

## 23 mục CẦN — nhóm theo device để RE 1 lượt/device

### G1 · Năng lượng — trip data (đọc, chưa getter) — device BYDAutoBmsDevice / Statistic
| # | Mục | RE: tìm feature-id |
|---|---|---|
| 1 | Km chạy điện | grep featmap `STATISTIC*EV*MILE*`/`*EV_DISTANCE*`/`*PURE_ELEC*` |
| 2 | Quãng đường chuyến | `*TRIP*DISTANCE*`/`STATISTIC*TRIP*` |
| 3 | Thời gian chuyến | `*TRIP*TIME*`/`*DRIVE*TIME*` |
| 4 | Điện tiêu thụ chuyến | `*TRIP*CONSUM*`/`*ENERGY*TRIP*` |
→ on-car: `getid` từng candidate, giá trị hợp lý (≠ sentinel/65535) = đúng.

### G2 · Động lực
| # | Mục | RE |
|---|---|---|
| 19 | Số P/R/N/D (gear) | grep `*GEAR*`/`SET_GEAR*`/`*SHIFT*` (device Drive?) — đọc trạng thái số hiện tại |

### G3 · Khí hậu — nhiệt (data CÓ, sai read route) — device BYDAutoAcDevice
| # | Mục | RE |
|---|---|---|
| 21 | Nhiệt trong cabin | AmapService log `temp=22` ⇒ data có. Tìm `AC_TEMP_*IN*CAR*`/`*INSIDE*TEMP*`/`AC_ENV_TEMP*`; thử `readArg` area khác (0/1) |
| 22 | Nhiệt cài đặt | `AC_TEMP_MAIN`(id gần 501219368 = `AC_TEMP_MAIN_SET`) — đọc base state; readArg đúng |

### G4 · Lốp — nhiệt (đọc, chưa getter) — device BYDAutoTyreDevice
| # | Mục | RE |
|---|---|---|
| 24-27 | Nhiệt lốp ×4 | grep `*TYRE*TEMP*`/`*TIRE*TEMP*` — 4 area (FL/FR/RL/RR), như áp lốp đã OK |

### G5 · Cửa & khoang — device BYDAutoBodyworkDevice
| # | Mục | RE |
|---|---|---|
| 28 | Cốp sau — trạng thái đóng/mở | grep `*HATCH*`/`*TAILGATE*`/`*BACK_DOOR*STATE*` (ghi cốp đã OK, chỉ thiếu getter đọc) |

### G6 · Khóa cửa chính — device BYDAutoDoorLockDevice (⚠ setev area = NOT_PROVISIONED)
| # | Mục | RE |
|---|---|---|
| 32 | Khóa / mở khóa cửa | Đào **named setter** `setDoorLockState`/`setAllDoorLock`; thử `set` op (named method) thay `setev`; hoặc device khác. **Nhiều khả năng trim chặn an ninh** — nếu bất khả, ghi rõ + hỏi owner giữ/bỏ. |
| 33 | Mở khóa cửa | (cùng trên — 1 chiều unlock) |

### G7 · Đèn — device BYDAutoLightDevice (đọc + ghi)
| # | Mục | RE |
|---|---|---|
| 35 | Đèn cốt (đọc) | grep `*LOW_BEAM*`/`*DIPPED*` |
| 36 | Đèn pha (đọc) | `*HIGH_BEAM*` |
| 37 | Đèn ban ngày/DRL (đọc) | `*DRL*`/`*DAYTIME*` |
| 38 | Chế độ đèn pha (đọc) | `*HEADLIGHT*MODE*`/`*LIGHT_MODE*` |
| 44 | Đèn pha (ghi) | `*HIGH_BEAM*SET*` |
| 45 | Đèn ban ngày (ghi) | `*DRL*SET*` |
| 50 | Chế độ đèn pha (ghi) | `*HEADLIGHT*MODE*SET*`/`*LIGHT_MODE*SET*` |
→ ⚠ `hud_brightness` [ĐO] trùng id `brightness_gear`; cẩn thận id đèn cũng có thể trùng/sai — verify từng cái trên xe.

### G8 · Camera 360 + góc cam — device BYDAutoADASDevice (owner XÀI)
| # | Mục | RE |
|---|---|---|
| 57 | Camera 360 (bật/tắt) | `ADAS_AVM_APA_SWITCH`(487587878)/`_SET`(944767002) hoặc `setAVMSwitchState`; test bật → màn hiện 360 |
| 60 | Góc camera | grep `*AVM*VIEW*`/`*CAMERA*VIEW*`/`*AVM*ANGLE*` — chọn góc nhìn |

## 🆕 FEATURE MỚI (note — CHƯA làm)
**Voice chọn camera khi app camera-360 của xe đang mở**: người lái nói "cam phải/trái/trước/sau" → chuyển view camera tương ứng (để nhìn khi lái, vd cua/đỗ). Cần RE lệnh đổi view của app AVM xe (có thể qua feature-id `ADAS_AVM_*VIEW*` hoặc key-event/intent tới app camera). Gắn vào voice grammar sau khi RE được #60 (góc cam). → **task riêng, sau khi RE camera xong.**

## Kế hoạch thực thi
1. **Off-car**: grep featmap từng nhóm (G1-G8) → liệt kê candidate feature-id + device.
2. **On-car** (1 buổi, cầu `-n`): `getid` mọi candidate đọc + `setev` candidate ghi (đèn/khóa/camera — owner nhìn xe) → chốt id/route đúng từng mục.
3. **Off-car**: wire vào TelemetryRegistry/ControlRegistry (getter/route đã chốt) + test.
4. Mục nào on-car xác nhận **bất khả** (như khóa cửa chính nếu trim chặn) → báo owner giữ-hiện-"—" hay bỏ.

## Ngoài phạm vi task này
- **BỎ 40 mục** (`5-18,20,23,29,30,31,34,39-43,46-49,51-56,58,59,61,62,63`) → purge riêng (xoá registry/UI/voice/icon/json + cập nhật count).
