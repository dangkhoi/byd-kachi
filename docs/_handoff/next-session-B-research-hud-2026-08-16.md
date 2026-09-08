# HANDOFF B — RESEARCH: HUD kính không lên nav (so sánh xe owner vs Sealion 6) · phiên 2026-08-16

> File ĐỘC LẬP. Cặp với **HANDOFF A — CODING**. Đây là NGHIÊN CỨU (không phải code app).
> IP/VIN redact. Xe owner: BYD **Seal** DiLink3.0, region ROW, `vehicle_40d_code=138`, HUD kính = **VIETMAP_HUD_H50** (BT name). Xe so sánh: **BYD Sealion 6** + HUD **mua Taobao (HUD Trung Quốc)** lắp thêm.

## Câu hỏi
Cùng dòng HUD (hiển thị y chang), mà **xe bạn (Sealion 6): app lên HUD nav ngon; xe owner (Seal): HUD KHÔNG lên nav** (cụm "Giữa+ETA" thì lên). Trước nghĩ do phần cứng → **SAI**. Tìm nguyên nhân thật + có gỡ được không.

## Kết luận (evidence — độ tin cao)
**Thủ phạm = 1 cờ VARIANT CODING của XE, không phải HUD, không phải app.**

`INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG = 0x38B00030` = cờ provisioning cho **cụm mirror nav → HUD kính**. Consumer `Hud…readSelfLearnState()`: HUD nav bật khi **config == 1** `[RE 2026-08-10 §10: native decompile libBydCluster, docs/_handoff/hud-cluster-injection-findings-2026-08-10.md]`.

### Readback xe owner (2026-08-16) — bằng chứng
| Feature | Xe owner (Seal) | Ý nghĩa |
|---|---|---|
| `0x38B00030` HUD_NAV_MAP_CONFIG | **-2147482648 (NOT provisioned)** | cờ mirror nav→HUD **TẮT** ← thủ phạm |
| `0x38B0002E` HUD_NAV_MAP_STATUS | -2147482648 | không provisioned |
| `0x38B00015` HUD_CONFIG (0/1/2) | **=1** (W-mode) | HUD nhận diện OK |
| `0x38B0001C` HUD switch / `0x38B00028` nav-content / `0x38B0001E` adas | **=1 cả 3** | HUD bật, toggle "nav content" ON |
| Oversea `0x1F701010/018/704010/A1008` | **rejected hết** — `no permission ... device: 1007` | họ export un-provisioned |
| Domestic `0x43F01010/018` | -10011 (provisioned, write-only) | nuôi CỤM (chạy được) |
| Domestic dualIcon `0x43F01030` | rejected | un-provisioned |
| Device codes app dùng | **1007 / 1023 / 1038 / 1014** | so với xe bạn |

⇒ **Nghịch lý sáng tỏ:** HUD bật + toggle "nav content" ON (`38B00028=1`) nhưng **cờ mirror `38B00030` chưa provisioned** → toggle vô nghĩa, cụm không đẩy nav ra HUD. App ghi oversea đã đúng; xe code đúng cờ (Sealion 6) thì nav **tự mirror ra HUD, không cần sửa app**.

## Có gỡ được không (từ Android/adb)?
**KHÔNG.** `0x38B00030` write bị REJECT; self-learn chỉ mirror MCU state vào cache đọc; firmware không có app ghi MCU coding `[RE §10]`. → Chỉ set được qua **coding tool BYD ngoài** (OBD → instrument ECU **variant coding**, UDS `WriteDataByIdentifier`) set `0x38B00030 = 1`. Sau đó nav sẵn có tự lên HUD. Cấp đại lý/diagnostic, không phải phần mềm.

## Việc cần làm — LẤY DATA XE SEALION 6 ĐỂ CHỐT 100%

Xe bạn HUD lên ⇒ **kỳ vọng `0x38B00030 = 1`** (hoặc oversea provisioned). Lấy data để so, chốt "chênh nhau đúng ở cờ coding".

### Ràng buộc: bạn dùng **Bugjaeger gõ tay từng lệnh** (không laptop, không script)
Gửi bạn 2 thứ: file `apks/navopen-v4.jar` (đẩy vào `/data/local/tmp/navopen.jar` qua mục File của Bugjaeger). Rồi gõ (đang lúc HUD hiện nav):

**Phần 1 — không cần jar, gõ ngay:**
```
logcat -c
```
(đợi ~10s cho HUD chạy nav) rồi:
```
logcat -d -s AbsBYDAutoDevice BYDAutoInstrumentDevice NavigationHudOwner
```
→ chụp màn hình gửi về. Tìm: dòng `set featureId is 1f7…` (oversea) — nếu **CÓ mà KHÔNG kèm** `no permission` ⇒ oversea provisioned (khác xe owner có `no permission ... 1f7... device 1007`). + dòng `sendSimpleGuidanceInfo`/`sendNextPathName`.
```
getprop persist.sys.vehicle_40d_code
getprop ro.build.region
getprop persist.sys.byd.bluetooth_name
```

**Phần 2 — đọc chính xác cờ (sau khi đẩy jar):**
```
CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen getraw instr 38B00030
```
→ kỳ vọng ra `= 1` (xe owner ra `= -2147482648`). Đổi `38B00030` → `1F701010`, `38B0002E` để check thêm.

> Có sẵn script `scripts/vehicle/hud-provisioning-compare.sh` (read-only) chạy full nếu bạn nào có laptop adb.

### Cách interpret (chốt)
- Sealion 6 `38B00030 = 1` (hoặc oversea `0x1F7` không bị reject) trong khi Seal owner không → **XÁC NHẬN 100%: chênh ở cờ coding**. Đóng giả thuyết.
- So thêm `vehicle_40d_code`, region, device codes để hiểu cờ này gắn với variant nào (phục vụ nếu muốn coding).

## Sau khi chốt
- Nếu xác nhận cờ: tìm **coding tool BYD** (OBD/UDS) set `0x38B00030=1` cho xe owner (+ có thể provision họ oversea `0x1F7`). Không có tool → line này đóng cho hobby (app đã đúng, chỉ chờ coding).
- Ghi data Sealion 6 + verdict vào doc mới `docs/diagnostics/hud-provisioning-compare-<ngày>.md`.

## Context liên quan (đừng lẫn)
- **naviState gate**: centre "Giữa+ETA" (khác HUD kính) chỉ render khi naviState=1 — đây là chuyện CỤM, đã chạy trên xe owner. HUD kính là chuyện cờ `38B00030` riêng.
- **KHÔNG sửa app** cho vụ HUD này — app ghi oversea + SDK đã đúng; vấn đề nằm ở coding xe.

## Nguồn
- Root-cause cờ: `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §10 (INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG 0x38B00030).
- Kiến trúc domestic/oversea: `docs/diagnostics/nav-output-architecture-2026-08-16.html`.
- Readback + handoff phiên: `docs/diagnostics/oncar-session-2026-08-16.md` §4.
- Tool: `scripts/vehicle/hud-provisioning-compare.sh`, `apks/navopen-v4.jar`.
