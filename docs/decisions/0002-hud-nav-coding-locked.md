# 0002 — HUD kính ZIN nav = gate firmware (nghi phạm `0x38B00030`), không phải app

> **Trạng thái**: Accepted · **Ngày**: 2026-08-19 · **Sửa**: 2026-08-19 (2 lần — xem §Sửa) · **Mục đích**: Chốt root-cause "HUD kính ZIN không lên nav" = gate provisioning của firmware xe, KHÔNG phải bug app → app đừng "fix" oversea/HUD write.

> **⚠ SỬA 2026-08-19 (LẦN 2 — đính chính LẦN 1): HUD Taobao ≠ HUD zin.** Bản sửa lần 1 (commit `ccac5af`) kết luận `0x38B00030` **bị bác** vì "xe anh em đọc `−2147482648` y hệt owner mà HUD vẫn hiện nav" — **suy luận đó SAI vì so nhầm đối tượng**. HUD hiện nav trên xe anh em là **HUD Taobao aftermarket**: đường render **ĐỘC LẬP**, đọc tín hiệu nav từ bus rồi **tự vẽ bằng firmware riêng**, **KHÔNG qua firmware/MCU render của HUD zin**. Vì vậy nó chỉ **chứng minh app ghi nav lên bus ĐÚNG** (Taobao HUD + cụm đều hiện) — **KHÔNG bác được** `0x38B00030` cho **HUD zin** (hai đường render khác nhau hoàn toàn). ⇒ **`0x38B00030` VẪN là nghi phạm gate HUD-ZIN (consumer `Hud00600401300000.readSelfLearnState()==1`, RE libBydCluster/carsettings), CHƯA bị bác.** Biến thể `40d` 138 vs 162 chỉ là khác biệt **tình cờ** — khác biệt THẬT giữa hai quan sát là **HUD zin (owner) vs HUD Taobao (anh em)**, không phải con số `40d`; không có cơ sở nói re-code 138→162 sẽ bật HUD-zin nav.
>
> **Hai đường ra:** **(a) HUD Taobao aftermarket** = đã chứng minh chạy với app hiện tại (render độc lập); **(b) HUD zin** = provisioning firmware (`0x38B00030` ± cờ khác) — các đường crack + xếp hạng khả thi: `docs/diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md`. **Quyết định lõi giữ nguyên** (HUD-zin nav = gate firmware, KHÔNG phải app; app ghi đúng). Phần "Context/Decision" dưới đây giữ nguyên làm bản ghi lịch sử; đọc kèm đính chính này.

## Context

Câu hỏi lâu năm: trên xe Seal của owner, **HUD kính lái không hiển thị nav**, trong khi cùng đời HUD (mua Taobao) gắn trên một xe **BYD Sealion 6** trong hội thì app lên HUD nav ngon. Trước nghĩ do phần cứng HUD — **SAI** (cùng dòng HUD, hiển thị y hệt).

Bằng chứng on-car (`docs/diagnostics/oncar-session-2026-08-16.md` §4; readback 2026-08-16; write-attempt 2026-08-18 = backlog **A11**):

- `INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG = 0x38B00030` là **cờ provisioning** cho việc cụm mirror nav → HUD kính. Consumer `Hud…readSelfLearnState()` chỉ bật khi **config == 1**. `[RE native decompile libBydCluster]`
- Xe owner đọc **`38B00030 = -2147482648` (KHÔNG provisioned)** + `38B0002E` (status) cũng không provisioned. `[readback 2026-08-16]`
- Các toggle HUD chung thì BẬT: `38B00015=1` (W-mode), `38B0001C=1` (switch on), `38B00028=1` (nav-content toggle on), `38B0001E=1` (adas). ⇒ nghịch lý "bật nav-content mà HUD không nav" = do **cờ mirror `38B00030` chưa provisioned** (toggle vô nghĩa).
- **Control (chứng minh app ghi được):** đường nuôi cụm `43F01010/018` **ghi rc=0** (provisioned, chấp nhận). Chỉ họ **oversea `0x1F7*`** + dualIcon domestic `43F01030` bị **reject** — `no permission … with this device: 1007`. Device codes app dùng: 1007/1023/1038/1014.
- **Write `38B00030` bị reject**; self-learn chỉ mirror state MCU vào cache đọc; firmware không có đường app ghi MCU coding.
- Trên Sealion 6 (cùng HUD) app → HUD nav chạy ⇒ khác biệt nằm ở **variant coding của XE**, không phải HUD, không phải app.

## Decision

Chốt: **HUD kính lái lên nav là do cờ variant-coding `0x38B00030` của XE, KHÔNG phải hành vi app.** Do đó:

1. **App đã ghi ĐÚNG** cả frame domestic + oversea — **đừng "fix" oversea/HUD write trong code** (nó đúng, chỉ bị xe owner reject vì thiếu cờ).
2. Bật HUD nav trên xe owner **chỉ mở khoá được bằng công cụ coding BYD ngoài** (OBD → instrument ECU variant coding, UDS `WriteDataByIdentifier`) đặt `0x38B00030=1` — **KHÔNG** qua adb/no-root.
3. Feature "windshield HUD nav" **ngoài scope app** cho tới khi cờ được set ngoài; gate theo coding, không phải theo code.

## Consequences

- **Được:** dừng đuổi theo hướng sai (fix app / đổi HUD); tiết kiệm công. Mọi report "HUD không lên nav" trên xe owner về sau = **coding**, không phải bug code → khỏi debug lại.
- **Mất / trade-off:** HUD kính nav **bị chặn** cho xe owner đến khi có coding tool; đây là kết luận "không thể qua app" **kèm điều kiện mở khoá** (set `0x38B00030=1` qua OBD/UDS) — đúng tinh thần `trace-den-tan-cung.md`.
- **Việc liên quan:** giữ code oversea (đúng trên xe provision được như Sealion 6) nhưng **cache per-feature runtime-rejection** để hết log spam `1007` mỗi frame trên trim không provision (`docs/diagnostics/app-code-updates-2026-08-16.md` Task 3) — **không hard-remove** oversea. Provisioning-compare với Sealion 6 (backlog **C3**, `hud-compare.bat` qua USB) sẽ xác nhận delta cờ.
- Badge/nav hiện chạy trên **CỤM qua cast**; các feature liên quan HUD (vd "giới hạn tốc độ sắp tới" trên HUD) đều gate theo cờ này.

## Status

Accepted, **sửa 2 lần 2026-08-19** (xem §Sửa ở đầu). Quyết định lõi (HUD-zin nav = gate firmware/provisioning của xe, KHÔNG phải app; app ghi đúng) **giữ nguyên + được củng cố**.

- **Lần 1 (commit `ccac5af`):** kết luận `0x38B00030` bị bác — **đã bị đính chính là SAI** (so nhầm HUD Taobao với HUD zin).
- **Lần 2 (đính chính, hiện hành):** `0x38B00030` **VẪN là nghi phạm gate HUD-ZIN, CHƯA bị bác.** Bằng chứng "xe anh em" là **HUD Taobao aftermarket** (render độc lập, không qua firmware zin) nên **không đủ tư cách control** cho câu hỏi provisioning HUD zin. `40d` 138 vs 162 = khác biệt **tình cờ**, không phải gate đã xác lập.

**Hai đường ra:** (a) **HUD Taobao** = đã chứng minh chạy với app hiện tại; (b) **HUD zin** = provisioning firmware (`0x38B00030` ± cờ khác) qua tool coding BYD (OBD/UDS) — cờ cụ thể **chưa xác nhận đủ**. Các đường crack + xếp hạng khả thi: **`docs/diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md`**. Bằng chứng HUD Taobao: `docs/diagnostics/hud-provisioning-compare-2026-08-19.md` (đọc kèm đính chính §Sửa lần 2).

## Date

2026-08-19

---

**Tham chiếu:** **`docs/diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md`** (tổng hợp đường RE + xếp hạng — hiện hành) · `docs/diagnostics/hud-provisioning-compare-2026-08-19.md` (bằng chứng HUD Taobao — đọc kèm đính chính §Sửa lần 2) · `docs/_handoff/re-hud-track{1..5}-*.md` (RE đọc-hiểu) · `docs/diagnostics/oncar-session-2026-08-16.md` §4 · `docs/diagnostics/app-code-updates-2026-08-16.md` (mục "KHÔNG phải code" + Task 3) · `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §10 · `docs/PROJECT-BACKLOG.md` (A11, C3, D6) · readback `~/Desktop/hud-xe-minh.txt` (off-repo).
