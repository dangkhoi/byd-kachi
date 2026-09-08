# Factory (ZIN) HUD nav — tổng hợp đường RE + xếp hạng khả thi

> **Loại:** Diagnostics · **Trạng thái:** Current · **Ngày:** 2026-08-19
> **Mục đích:** Gom MỌI đường tìm được (5 track RE) để làm **HUD kính ZIN (OEM/nhà máy)** hiện nav, phân loại khả thi × chi phí, kèm bằng chứng `file:line` và điều-kiện-mở-khoá cho từng đường "không thể" (theo `trace-den-tan-cung.md`).
> **Nguồn:** tổng hợp `docs/_handoff/re-hud-track{1..5}-*.md` (RE đọc-hiểu 2026-08-19) + `docs/diagnostics/hud-provisioning-compare-2026-08-19.md` + `docs/decisions/0002-hud-nav-coding-locked.md`.
> **Scope:** RE đọc-hiểu; doc này KHÔNG sửa code, KHÔNG commit/push. Việc coding XE (OBD/UDS) là quyết định của owner.

---

## 0. Đính chính TRỌNG TÂM — HUD Taobao ≠ HUD zin (đọc trước tất cả)

Kết luận trước đây (ADR 0002 amend + `hud-provisioning-compare-2026-08-19.md` + 3/5 track) cho rằng **`0x38B00030` đã bị BÁC là gate**, vì "xe anh em đọc `0x38B00030 = −2147482648` y hệt owner mà HUD vẫn hiện nav". **Suy luận đó SAI ở chỗ so sánh nhầm đối tượng:**

- **HUD hiện nav trên xe anh em là HUD Taobao aftermarket** — một thiết bị gắn thêm, **đường render ĐỘC LẬP**, đọc tín hiệu nav từ bus (guide-info app phát ra) rồi **tự vẽ bằng firmware riêng của nó**. Nó **KHÔNG đi qua firmware/MCU render của HUD zin**.
- Vì vậy "xe anh em lên nav" chỉ **chứng minh app ghi tín hiệu nav lên bus ĐÚNG** (Taobao HUD bắt được + vẽ; **cụm cũng hiện nav**). Nó **KHÔNG chứng minh** lớp nav của **HUD zin** có bị gate bởi `0x38B00030` hay không — vì hai đường render khác nhau hoàn toàn.
- ⇒ **`0x38B00030` VẪN là nghi phạm gate HUD-ZIN** (consumer `Hud00600401300000.readSelfLearnState()==1`, RE libBydCluster/carsettings). **CHƯA bị bác.** Việc hai xe cùng đọc `−2147482648` không nói lên gì về HUD zin, vì xe anh em không dùng HUD zin để hiện nav.
- **Biến thể `40d` 138 (owner) vs 162 (anh em) chỉ là khác biệt TÌNH CỜ bắt được** — khác biệt THẬT giữa hai quan sát là **HUD zin (owner) vs HUD Taobao (anh em)**, không phải con số `40d`. Không có bằng chứng nào cho thấy re-code `138→162` sẽ bật HUD-zin nav; đó là suy đoán chưa có cơ sở.

**Hệ quả:** hiện **CHƯA có ví dụ nào xác nhận một HUD zin hiện nav** (mẫu "162 lên nav" là HUD Taobao, không tính cho HUD zin). Câu hỏi RE mở lại về đúng trọng tâm: **lớp nav của HUD zin bị gate bởi cờ provisioning firmware nào — `0x38B00030` (± cờ họ hàng) là nghi phạm số 1, chưa bác.**

## 0.1 — ĐÍNH CHÍNH 2026-08-21 (OWNER xác nhận, ưu tiên CAO NHẤT — thay thế suy luận Taobao ở §0)
**HUD anh em là HUD BYD ZIN (chính hãng) GẮN THÊM — KHÔNG phải Taobao.** Thị trường VN **cắt HUD** khỏi xe khi bán; anh em **mua HUD zin rồi lắp vào** SL6. ⇒ §0 ("HUD Taobao aftermarket / đường render độc lập") **SAI**. HUD anh em đi **đúng đường render firmware factory** như HUD zin.

**Hệ quả (đảo lại §0 + phục hồi so-sánh coding):**
- HUD zin SL6 **hiện arrow+dist** dù đọc `0x38B00030 = NOT_PROVISIONED` (giống Seal) ⇒ **`0x38B00030` KHÔNG gate arrow+dist**. Nó gate **ROAD-NAME** (lớp sâu hơn: `0x420A1010` check-state — cả 2 xe đều =0, chưa provision ở đâu).
- Khác biệt **Seal (không gì) vs SL6 (arrow+dist)** = **coding biến thể `40d` 138 (Seal) vs 162 (SL6)** — 162 bật lớp HUD-nav-guide, 138 không. (Chính là kết luận GỐC trước khi "đính chính Taobao" — nay phục hồi vì HUD là ZIN.)
- **Gate PHÂN TẦNG:** Lớp 1 arrow+dist = coding `40d` (SL6 có, Seal không) · Lớp 2 road-name = `0x38B00030`/check-state (cả 2 chưa có).
- **Unlock owner:** (1) lắp **HUD zin** (VN-cut nên phải mua+gắn như anh em); (2) **code Seal 138→162** (VDS2100) để bật arrow+dist — PROVEN concept (HUD zin SL6 + 162 = arrow+dist). Road-name = cần provision `0x38B00030` thêm (lớp sâu, chưa proven ở đâu).
- ADR 0002 §Sửa-lần-2 + §0 doc này + `hud-provisioning-compare` mục 5-6 (nói "Taobao") → **stale, đọc §0.1 này thay**.

### Hai đường ra (owner quyết)

| | Đường | Trạng thái | Bản chất |
|---|---|---|---|
| **(a)** | **HUD Taobao aftermarket** | **ĐÃ CHỨNG MINH chạy** với app hiện tại | Render độc lập; app ghi guide-info đúng → Taobao HUD tự vẽ. Chi phí = mua thiết bị (~vài trăm k–vài triệu). Không đụng firmware zin. |
| **(b)** | **HUD zin (provisioning firmware)** | **CHƯA mở** — nghi phạm `0x38B00030` ± cờ khác | Cần crack/coding lớp provisioning firmware. Các đường xem bảng xếp hạng bên dưới. |

---

## 1. Bảng xếp hạng đường (khả thi × chi phí)

> Xếp từ **NÊN LÀM TRƯỚC** (khả thi cao × chi phí thấp / info-gain cao) → **bất khả thi** (kèm điều-kiện-mở-khoá). "Khả thi" = xác suất mở được HUD-zin nav (hoặc thu được bằng chứng quyết định). "Chi phí" = công + rủi ro + phụ thuộc tool/hardware ngoài.

| # | Đường | Phân loại | Khả thi | Chi phí | Bước kế (1 dòng) |
|---|-------|-----------|---------|---------|------------------|
| **0** | **HUD Taobao aftermarket** (đường render độc lập) | ✅ đã chứng minh | **CAO** (đã chạy) | Thấp–TB (mua thiết bị) | Nếu owner chấp nhận HUD gắn thêm → mua 1 chiếc, app phát guide-info sẵn rồi |
| **1** | Sweep 2 **fusion switch** `0x8e2fcdbf`/`0xd61b6746` + đọc feedback `0x38B00034`/`0x38B00032` | 🔬 on-car probe (non-root) | Thấp–TB | **Rất thấp** | On-car: set 2 switch = 1, kèm guide feed, xem HUD zin đổi gì; đọc feedback |
| **2** | **ICarHudService** đọc capability HUD zin owner (`getHudConfig(NAVIGATION_MAP/FUSION/DYNAMIC)`, `getHudSupportedModes`, `isNavigationMapEnabled`) | 🔬 on-car probe (đọc, cần DiCar client đặc quyền) | Thấp (chẩn đoán) | Thấp | On-car: navopen-style đọc capability → HUD zin có "quảng cáo" nav support không |
| **3** | Probe `0x32B1102E` (INSTRUMENT_HUD_NAVIGATION_MAP_SET) đọc→thử set 2→rollback | 🔬 on-car probe (non-root) | Thấp | Rất thấp | On-car: `getraw instr 32B1102E`, thử `setraw 2`, đọc lại, rollback |
| **4** | **Provision `0x38B00030=1`** (INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG) — nghi phạm gate chính | 🔧 cần tool coding (UDS/OBD, seed-key) | **TB–CAO** (nếu đúng gate) | CAO (tool + rủi ro DTC) | Dealer/OBD-UDS `WriteDataByIdentifier` set config nav-map; HOẶC on-car `BYDAutoOtaDevice 0xAA000140` (gated seed/key) |
| **5** | **Variant-coding dump** đầy đủ HUD zin owner → tìm cờ provisioning nav-map cụ thể | 🔧 cần tool coding (đọc coding) | TB (chẩn đoán) | CAO (tool ngoài) | Dump coding instrument-ECU; tra cờ họ HUD-nav (`0x38B00030` + lân cận) |
| 6 | **Provision qua CAN sniff** trên một xe có HUD-zin-nav thật (nếu tìm được) → diff frame | 🔬 on-car probe (cần xe mẫu) | Thấp (phụ thuộc tìm xe) | TB–CAO | Cần 1 xe HUD **zin** thật lên nav để sniff `BYDAutoBigDataDevice` → so bus |
| 7 | HAL write thẳng `0x38B00030=1` | ❌ bất khả thi qua app | 0 | — | **Rejected on-car**; đọc-only. Mở khoá = UDS coding (đường 4) |
| 8 | Nhồi nav vào field HUD đang render (ADAS/tốc-độ/call) | ❌ bất khả thi | 0 | — | QML bind 1 chiều + HUD zin **thiếu widget nav**. Mở khoá = provision widget (đường 4/5) |
| 9 | CAN-inject frame để **bật** nav-HUD-mirror | ❌ bất khả thi cho nav | 0 | — | Là softcode MCU, không frame app nào lật. Mở khoá = UDS coding |
| 10 | App kỹ thuật/factory Android set coding HUD (BydDevelopmentTools) | ❌ bất khả thi | 0 | — | Không app nào trong image làm coding. Mở khoá = tool BYD ngoài |
| 11 | settings-secure / persist prop / service-call firmware đọc để bật | ❌ bất khả thi | 0 | — | Không surface Android-side nào firmware tra. Gate ở MCU |
| 12 | Gọi cross-process `VehicleSettings.setHudNavigationState(true)` của app OEM | ❌ bất khả thi qua app | 0 | — | Setter Ui→Mcu, permission-gated + vẫn bọc bởi provisioning MCU |

**Tóm tắt chiến lược:** làm **đường 1→2→3** trước (rẻ, on-car, non-root, thu thông tin về HUD zin owner) → nếu cả 3 xác nhận HUD zin **không quảng cáo/không nhận** nav ⇒ củng cố giả thuyết provisioning `0x38B00030`, và **đường 4** (UDS coding) là cửa mở khoá thật. **Đường 0** (Taobao) là fallback **đã chạy** nếu owner chỉ cần "có nav trên kính" mà không nhất thiết là HUD zin.

---

## 2. Chi tiết từng đường

### Đường 0 — HUD Taobao aftermarket (ĐÃ CHỨNG MINH) ✅

- **Mô tả:** Một HUD gắn thêm mua Taobao, đọc tín hiệu nav từ bus và **tự render** bằng firmware riêng — không phụ thuộc provisioning của xe. Đây là đường **duy nhất đã xác nhận hiện nav** với app hiện tại.
- **Bằng chứng:** `hud-provisioning-compare-2026-08-19.md §1–2` — xe anh em (HUD Taobao) hiện nav bằng **chính app này + GMaps**; trước khi cài app HUD không có nav; log `AmapService` ghi `0x43E0003A=2`, `0x43FA1008` (pathname), `0x43F02018/0201E` (trip) **rc=0, 0 reject**. ADR 0002 §Context: "cùng đời HUD (**mua Taobao**) gắn trên một xe BYD Sealion 6". App ghi đúng: `[track4 Phụ lục B; BydHal.kt:284/290/312/313]`.
- **Phân loại:** ✅ **off-car RE xong + đã chạy on-car** (trên thiết bị Taobao). App-side hoàn tất.
- **Rủi ro:** thấp — thiết bị gắn thêm, không đụng firmware xe. Chất lượng render phụ thuộc HUD Taobao. Không phải "HUD zin" (nếu owner yêu cầu đúng HUD zin thì đây không thoả).
- **Bước kế:** nếu owner chấp nhận HUD gắn thêm → mua 1 chiếc tương thích; app đã phát guide-info sẵn, không cần đổi code. Xác minh loại HUD + giao thức bus mà thiết bị Taobao dùng (CAN trực tiếp / OBD).

### Đường 1 — Sweep 2 fusion switch `0x8e2fcdbf` / `0xd61b6746` 🔬

- **Mô tả:** `SET_NAVIGATION_FUSION_SWITCH_SET (0x8e2fcdbf)` + `SET_SAFETY_DRIVING_AID_FUSION_SWITCH_SET (0xd61b6746)` là 2 switch **ghi-được** ở SettingDevice, có thể chi phối việc "fuse" nav lên HUD/cụm — **chưa sweep dứt điểm** (§19 mới thử HUD mode/switch + guidance, chưa thử 2 fusion switch obfuscated này kèm guide feed).
- **Bằng chứng:** `[track4 Q1.3 bảng SettingDevice]` — `0x8e2fcdbf` NAVIGATION_FUSION (feedback `0x38B00034`), `0xd61b6746` SAFETY_DRIVING_AID_FUSION (feedback `0x38B00032`); `[track4 Q4.2 cửa (5)]` liệt kê là "chưa sweep dứt điểm — rẻ, đáng xác nhận".
- **Phân loại:** 🔬 **on-car probe (non-root, app-reachable)**.
- **Rủi ro:** thấp — 2 switch cùng họ HUD/nav SettingDevice; ghi thử + đọc feedback + rollback. Rủi ro làm "làn cụm nhảy sang HUD" đã được cảnh báo trong `CarExecClusterDiagnosticsCatalog.kt` → theo dõi cụm khi test.
- **Bước kế:** on-car set mỗi switch = 1 (kèm guide feed 43E/43F đang chạy), quan sát HUD zin + đọc feedback `0x38B00034`/`0x38B00032`; rollback về giá trị cũ.

### Đường 2 — ICarHudService đọc capability HUD zin owner 🔬

- **Mô tả:** `ICarHudService`/`ICarHudManager` (DiCar vision SPI) có các **read capability**: `getHudSupportedModes()`, `getHudConfig(featureMask)`, `isNavigationMapEnabled()`. Đọc để biết **HUD zin owner có "quảng cáo" nav support** (bit `NAVIGATION_MAP=2048`/`NAVIGATION_FUSION=1024`/`DYNAMIC_NAVIGATION=256`) hay không — chẩn đoán quyết định.
- **Bằng chứng:** `[track1 §1; HudFeature.java:6-20]` bit mask; `[track1 §1; ICarHudManager.java:12,14,20,24,26]` getHudConfig/getHudSupportedModes/is*Enabled; `[track1 §2; car/n2.java:26-30]` transport qua ContentProvider `CarServiceProvider/sync_binder`; `[track1 §2; PermissionUtils.java:11-31]` server gate theo caller uid. **Không có content-push method** (không setTurnIcon/distance) → chỉ đọc/enable, không tự vẽ nav.
- **Phân loại:** 🔬 **on-car probe (đọc-only)** — cần **DiCar client đặc quyền** (navopen-style app_process). Ghi = ❌ (permission-gated system/OEM-signed; dadb uid-2000 thiếu quyền BYD).
- **Rủi ro:** thấp (chỉ đọc). Nếu provider chặn export/permission → không đọc được (ghi nhận là "không tiếp cận").
- **Bước kế:** on-car `dumpsys package providers | grep CarServiceProvider` (đọc exported/permission), rồi navopen đọc `getHudConfig(NAVIGATION_MAP)` + `getHudSupportedModes()`. **Không cần ghi.** Kết quả "HUD zin không advertise nav" ⇒ củng cố giả thuyết provisioning.

### Đường 3 — Probe `0x32B1102E` (HUD_NAV_MAP_SET) 🔬

- **Mô tả:** `0x32B1102E` (INSTRUMENT_HUD_NAVIGATION_MAP_SET, 2=ON/1=OFF) là **setter runtime** gần nghĩa "bật nav-map HUD" nhất; OEM `Hud00600401300000.setState()` ghi id này. Đọc trước, thử set 2, đọc lại, rollback — xác nhận nó có phải unlock (kỳ vọng thấp: bị reject khi config chưa provision).
- **Bằng chứng:** `[track2 §Consumer; Hud00600401300000.setState → DiCarSetter.set("0x32B1102E", 2/1)]`; `[track2 Instrument.java:538]`; `[track3 (2); track4 Q1.2]` "chỉ có `0x32B1102E` ghi được nhưng REJECTED vì `0x38B00030` chưa provisioned".
- **Phân loại:** 🔬 **on-car probe (non-root)** — kỳ vọng thấp (đã reject khi unprovisioned) nhưng rẻ, đáng đóng cửa.
- **Rủi ro:** thấp — 1 setter runtime, có rollback. Nếu rc=0 mà HUD vẫn không nav ⇒ xác nhận thêm gate là provisioning (config), không phải toggle.
- **Bước kế:** on-car `getraw instr 32B1102E` → `setraw instr 32B1102E 2` → đọc lại + quan sát HUD zin → rollback giá trị cũ.

### Đường 4 — Provision `0x38B00030=1` qua UDS coding (NGHI PHẠM GATE CHÍNH) 🔧

- **Mô tả:** `0x38B00030` (INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG) là **cờ provisioning/capability** mà consumer `Hud00600401300000.readSelfLearnState()` kiểm tra `== 1` — cơ chế "cụm mirror nav → HUD zin". **Đây là nghi phạm gate HUD-zin số 1, CHƯA bị bác** (đính chính §0). Set nó =1 = provisioning firmware, chỉ làm được qua UDS coding.
- **Bằng chứng:** `[track2 §Consumer; Hud00600401300000.readSelfLearnState() = get("0x38B00030")==1]`; `[track2 Instrument.java:536]`; `[track4 Q4.2 cửa (4); BYDAutoOtaDevice set({0xAA000140}, udsFrame) + listener {0x99000140}, đọc softcode getbytes ota 99000053]`; `[track3 Verdict; UDS DiagnosticSessionControl(0x10)+SecurityAccess(0x27)+WriteDataByIdentifier(0x2E)]`. Giá trị BẬT = `1`; `−2147482648` = sentinel not-provisioned `[track2 (3)]`.
- **Phân loại:** 🔧 **cần tool coding (UDS/OBD, seed-key gated)** — HOẶC on-car `BYDAutoOtaDevice` (cũng gated coding DID + security-access 0x27).
- **Rủi ro:** **CAO** — coding sai → DTC / misconfig instrument-ECU; cần recovery. Security-access seed/key có thể chặn hoàn toàn nếu không có key dealer.
- **Bước kế:** (owner quyết) mang xe tới nơi có tool coding BYD; đọc coding hiện tại họ HUD-nav; thử set `0x38B00030`/config nav-map = provisioned; test app guide feed (đã chạy sẵn) có lên HUD zin không. **Cần xác định coding DID cụ thể** (đường 5).

### Đường 5 — Variant-coding dump HUD zin owner → tìm cờ provisioning cụ thể 🔧

- **Mô tả:** Dump toàn bộ variant coding của instrument/HUD ECU xe owner để tìm **cờ/DID provisioning nav-map cụ thể** (họ `0x38B00030` + lân cận), thay vì đoán. Đây là bước chẩn đoán nền cho đường 4.
- **Bằng chứng:** `[track3 (3); CarInfo.java 138=EK/0x8A, 162=SA3EJ/0xA2]` — `40d` đọc-only qua `ICarInfoManager` (10 getter, 0 setter); `[track2 (4)]` "coding softcode MCU, đọc qua HAL, set qua UDS ngoài"; `[track3 Verdict]` "cờ coding cụ thể chưa xác định — cần variant-coding dump". Lưu ý §0: **không nên đóng khung là 138→162** (khác biệt tình cờ); nên tìm **cờ họ HUD-nav provisioning** trực tiếp.
- **Phân loại:** 🔧 **cần tool coding (đọc coding)**.
- **Rủi ro:** TB — chỉ đọc coding (không ghi) thì an toàn; cần tool + có thể cần security-access để đọc coding block.
- **Bước kế:** dùng tool chẩn đoán BYD đọc coding instrument-ECU; đối chiếu họ cờ HUD-nav (`0x38B00030`, `0x30100030`, `0x38B0002E`) với giá trị "provisioned" mong đợi.

### Đường 6 — CAN sniff trên một xe có HUD **zin** nav thật (nếu tìm được) 🔬

- **Mô tả:** Nếu tìm được **một xe có HUD ZIN (không phải Taobao) thật sự hiện nav**, sniff bus lúc HUD zin render nav để so với xe owner → xác định frame/coding khác biệt. `BYDAutoBigDataDevice` cho sniff whole-frame on-device không cần hardware.
- **Bằng chứng:** `[track4 Q3.2; CanDataCollectService.java:375-377 registerListener({-1728053216}), :290 onWholeFrameDataChanged(byte[])]`; `[track4 CanDataHandle.java:232-247 format frame]`. **Lưu ý:** đây là công cụ; **điều kiện tiên quyết là tìm được xe HUD-zin-nav thật** — hiện **chưa có** (mẫu anh em là Taobao).
- **Phân loại:** 🔬 **on-car probe** nhưng **phụ thuộc tìm xe mẫu HUD-zin-nav** (chưa có).
- **Rủi ro:** TB — sniff đọc-only an toàn; rủi ro là **không tìm được xe mẫu** ⇒ đường này bế tắc cho tới khi có mẫu.
- **Bước kế:** tìm xe BYD (trim/region) mà **HUD zin** hiện nav; nếu có → sniff + diff. Nếu không tìm được → không khả dụng.

### Đường 7 — HAL write thẳng `0x38B00030=1` ❌

- **Phân loại:** ❌ **bất khả thi qua app/adb.**
- **Bằng chứng:** `[track2 (5); findings-2026-08-10 §10]` write `0x38B00030` **REJECTED** on-car; `[track4 Q1.2]` họ `0x38B0xxxx` là feedback/config **đọc-only, không có `*_SET`**; `[track3 (2)]` "ghi thẳng `0x38B00030` đã bị REJECT".
- **Điều-kiện-mở-khoá:** set qua **UDS coding ngoài** (đường 4), không phải HAL write từ head-unit.

### Đường 8 — Nhồi nav vào field HUD đang render (ADAS/tốc-độ/call) ❌

- **Phân loại:** ❌ **bất khả thi trên trim này.**
- **Bằng chứng:** `[track4 Q2]` HUD zin trim này có widget km/h + speed-limit + ADAS nhưng **KHÔNG có widget nav** (owner xác nhận §19); QML bind **một chiều** `Text{text:DataSource.x}` (§25 Q4), giá trị do data-item CAN nội bộ nuôi, **QML không gán ngược**; ghi guidance `0x43F01010`/SDK `sendSimpleGuidanceInfo` → **rc=0 nhưng HUD trống**; repurpose field call phá UI cuộc gọi, không phải nav thật.
- **Điều-kiện-mở-khoá:** provision để **widget nav xuất hiện** (= coding `0x38B00030` family, đường 4/5). Không có widget thì không có gì để "nhồi".

### Đường 9 — CAN-inject frame để bật nav-HUD-mirror ❌

- **Phân loại:** ❌ **bất khả thi cho việc BẬT nav-HUD** (chỉ khả thi để giả **giá trị** speed-limit, không phải nav).
- **Bằng chứng:** `[track4 Q3.3]` nav-HUD-mirror là **softcode MCU `0x38B00030`** (coding UDS), **không frame CAN nào app phát ra lật được**; `[track4 Q3.4]` CanDataCollect = telemetry thuần (0 ref hud/nav); `BYDAutoTestDevice 0xAA00020F` inject chỉ giả tín hiệu speed-limit **value**, không tạo widget nav.
- **Điều-kiện-mở-khoá:** UDS coding (đường 4). ESP32/CAN-inject không tạo được lớp nav trên HUD zin.

### Đường 10 — App kỹ thuật/factory Android set coding HUD ❌

- **Phân loại:** ❌ **bất khả thi — không tồn tại trong image.**
- **Bằng chứng:** `[track3 (1)]` `BydDevelopmentTools` = repair/rollbench/OBD-readiness/log; grep `hud|coding|variant|writeData|0x2E|38B000|4C10E0` trên app-code = **0 khớp**; `[track3 (2)]` grep UDS primitive `WriteDataByIdentifier|SecurityAccess|DiagnosticSession` toàn corpus = **0 khớp**; `vehiclesettings` HUD UI chỉ **đọc** `0x38B00015`, ghi user-toggle, không code.
- **Điều-kiện-mở-khoá:** tool coding BYD ngoài (dealer/OBD), không có app Android nào thay được.

### Đường 11 — settings-secure / persist prop / service-call firmware đọc ❌

- **Phân loại:** ❌ **bất khả thi — không có surface Android-side nào.**
- **Bằng chứng:** `[track3 (4)]` persist prop duy nhất là `repair_mode` (không liên quan); không Settings.Secure/Global key nào firmware tra để bật HUD-nav; quyết định ở MCU variant coding, không phải giá trị Android.
- **Điều-kiện-mở-khoá:** provisioning MCU (đường 4/5).

### Đường 12 — Cross-process `VehicleSettings.setHudNavigationState(true)` ❌

- **Phân loại:** ❌ **bất khả thi qua app.**
- **Bằng chứng:** `[track5 (4) bề mặt chưa thử; track3 (1)]` `HudOptionDisplayModel.setHudNavigationState` là setter **Ui→Mcu của app OEM**, permission-gated, **vẫn bọc bởi provisioning MCU**; app mình không gọi cross-process được (thiếu quyền).
- **Điều-kiện-mở-khoá:** kể cả gọi được cũng vẫn cần provisioning MCU (đường 4). Không phải đường tắt.

---

## 3. Điều còn mở (trace-den-tan-cung — chưa đóng cửa)

1. **HUD zin owner có "advertise" nav support không** — chưa đọc `ICarHudService.getHudConfig/getHudSupportedModes` on-car (đường 2). Chưa làm ⇒ chưa kết luận HUD zin thiếu widget nav ở tầng capability.
2. **Cờ provisioning HUD-zin-nav cụ thể** — `0x38B00030` là nghi phạm số 1 (source-level `readSelfLearnState==1`), **chưa xác nhận** là đủ để bật (chưa từng set =1 thành công để test). Có thể cần thêm cờ họ hàng (`0x30100030` config-status, `0x38B0002E` status). Cần: đọc coding dump (đường 5) + thử set (đường 4).
3. **Chưa có mẫu HUD ZIN nào hiện nav** để đối chứng (mẫu anh em là Taobao). Nếu tìm được xe HUD-zin-nav thật ⇒ mở đường 6 (sniff + diff coding).
4. **`40d` 138 vs 162 là khác biệt tình cờ**, chưa có cơ sở nói re-code sang 162 sẽ bật HUD-zin nav. Không đóng khung unlock theo con số này; đóng khung theo **cờ provisioning nav-map**.

## 4. Nguồn (handoff + file:line)

- **Track 1** (`docs/_handoff/re-hud-track1-hudservice.md`): `HudFeature.java:6-20` (bit mask nav), `ICarHudManager.java:12/14/20/24/26/38/39/44/45/46/47`, `car/n2.java:26-30` (ContentProvider transport), `PermissionUtils.java:11-31` (server permission gate), `Hud00600401300000` getState/setState/readSelfLearnState.
- **Track 2** (`docs/_handoff/re-hud-track2-firmware-gate.md`): `Instrument.java:536` (`0x38B00030`), `:537` (`0x30100030`), `:538` (`0x32B1102E`), `:539` (`0x38B0002E`), `:515` (`0x43F01010`), `:762` (`0x43E0003A`); `Hud00600401300000.java` readSelfLearnState(`==1`)/getState(`==2`)/setState(2/1).
- **Track 3** (`docs/_handoff/re-hud-track3-coding-path.md`): `BydDevelopmentTools` surface (repair/rollbench/log), grep âm tính UDS + hud/coding, `CarInfo.java` (138=EK/0x8A, 162=SA3EJ/0xA2), `ICarInfoManager` (10 getter/0 setter), `Setting.java` bảng SET_HUD_*, verdict UDS ngoài (0x10/0x27/0x2E).
- **Track 4** (`docs/_handoff/re-hud-track4-hal-can.md`): `Setting.java` SET ids + 2 fusion switch `0x8e2fcdbf`/`0xd61b6746` (feedback `0x38B00034`/`0x38B00032`), `Instrument.java` họ `0x38B*` đọc-only, `Adas.java` SLA read-only, `CanDataCollectService.java:375-377/:290`, `CanDataHandle.java:232-247`, `BydHal.kt:284/290/312/313`, Q2 (QML bind 1 chiều, không widget nav), Q4 5 cửa chưa thử.
- **Track 5** (`docs/_handoff/re-hud-track5-priorart.md`): `windshield-hud-enable.html` (plan, on-car NOT STARTED, Q1–Q10 mở), findings-2026-08-10 (14 mục đã thử & fail), OpenBYD `CarControlImpl` (xác nhận API write đúng: `0x43F01010`/`0x4C10E015`), `ICarHudService` bề mặt chưa thử (đọc-only), `HudOptionDisplayModel.setHudNavigationState` (Ui→Mcu, gated), AR-HUD tier `0x34C0000B`.
- **Diagnostics nền:** `docs/diagnostics/hud-provisioning-compare-2026-08-19.md` (HUD Taobao anh em — **đính chính §0**: là aftermarket, render độc lập, KHÔNG bác được `0x38B00030` cho HUD zin), `docs/decisions/0002-hud-nav-coding-locked.md`.

> RE-only. Không sửa code, không commit/push.

---

## Cập nhật 2026-08-19 (chiều) — khe "TÊN ĐƯỜNG không lên HUD" (RE tập trung)

**Bối cảnh mới từ owner:** HUD "Taobao" thực chất là **HUD BYD xịn** (VN cắt ra, anh em mua gắn lại) → nói đúng protocol OEM. Qua **app ClusterNav**: HUD xe anh em lên **mũi tên + cự ly**, **CHƯA lên tên đường**. Showroom mode (OEM) thì hiện đủ cả tên đường (五一大道南).

**Phát hiện (evidence decompile):**
- Tên đường `0x43FA1008` (TARGET_NEXT_PATHNAME) là **buffer, ghép cặp với `0x420A1010` GET_ROAD_NAME_CHECK_STATE** (SDK `getRoadNameCheckState()` → VALID=1/INVALID=2). Mũi tên `0x43F01010` + cự ly là **INT, KHÔNG có check-state** → vẽ vô điều kiện. **Đây là lý do bất đối xứng** (mũi tên/cự ly lên, tên đường không).
- **App CHƯA BAO GIỜ đọc `0x420A1010`** (grep core/app = 0) → mù việc MCU coi chuỗi VALID hay không.
- App ghi **khác format OEM**: OEM ghi khung cuộn có dấu-cách dẫn `[32,0,'K',...]` + chữ CJK; app ghi chuỗi thô 1 lần, có thể chứa dấu tiếng Việt (font cụm chưa chứng minh phủ).
- **Showroom KHÔNG có "chuỗi bí mật" trong Android** — demo nav do **firmware MCU cụm tự vẽ** (không qua SDK). Xác nhận MCU có widget nav + ô tên đường, nhưng không copy được đường Android.

**Test rẻ (đã thêm vào `scripts/vehicle/hud-nav-enable-probe.sh` PHASE A + D):** đọc `0x420A1010` lúc app đang dẫn:
- **`2` INVALID** → MCU từ chối chuỗi tên đường của app → thử **charset/khung**: dấu-cách dẫn, thêm NUL UTF-16LE, bỏ dấu tiếng Việt (ASCII), giữ ≤7-8 ký tự.
- **`1` VALID** mà kính vẫn trống → **widget/coding gate `0x38B00030`** (không phải format).

**Trung thực:** register/cặp/encoding = bằng chứng cứng từ decompile. Việc INVALID có THỰC SỰ chặn render hay không nằm ở **firmware MCU cụm** (ngoài nguồn Android) → **test đọc `0x420A1010` on-car là mấu chốt, CHƯA chạy**. Đây là khe tractable nhất hiện có cho "nav đầy đủ trên HUD BYD"; xe owner (HUD trắng hoàn toàn) vẫn là gate `0x38B00030` riêng.

---

## Cập nhật 2026-08-19 (chiều-2) — ĐƯỜNG CODING/UDS cho HUD zin xe owner (RE sâu)

**ECU:** `0x38B00030` + họ (`30100030` CONFIG_STATUS, `32B1102E` SET, `38B0002E` STATUS, `34C00026` FORMAT) do **cụm đồng hồ (instrument-cluster) giữ** (`Instrument.java:534-539` → `InstrumentMapper` → `BYDAutoInstrumentDevice`; render + config store trong cụm, `libBydDataSource.so`). `-2147482648` (=`0x800003E8`) là **giá trị THẬT cụm trả**, không phải lỗi parse.

**Cơ chế provisioning (phát hiện mới, có cơ sở):** `BusinessSelfStudy::vehicleCodeSelfStudyUpdate` + log *"selfstudy has completed, vehicleCode is 0x%02x"* → `BydConfigInfo`/`ConfigureManager` (config XML), **cùng lớp equipment với ADAS**. **`40d` 138=`0x8A` / 162=`0xA2`** khớp khuôn `vehicleCode` 1-byte → HUD-nav là **feature bật bằng coding equipment/vehicleCode của cụm**. (KHÔNG có bảng `0xA2→ON` trong image → giá trị bật cụ thể chưa biết.)

**On-device coding — CÓ kênh nhưng KHÓA:** tồn tại UDS-thô on-device (`BYDAutoOtaDevice 0xAA000140` + `BYDAutoSettingDevice` secret-OBD `0xAA000241/0x99000241`); factory app `BydDevelopmentTools` chạy UDS chuẩn qua đó (`10 03`→`27 01/02`→`2E/31`). **Nhưng gated `sharedUserId="android.uid.system"` (platform-signed) + per-property permission.** ClusterNav (user-signed / dadb uid-2000) **KHÔNG với tới** — cần root/platform-key HOẶC drive BydDevelopmentTools. Factory tool **không có màn coding HUD** (chỉ CAN-ID mapping + đọc version).

**OBD-UDS ngoài:** `10 03` + SecurityAccess `27 01/02` + `2E/31`. Thuật toán seed→key **lộ** trong `ObdDataManager.k()` — **nhưng chỉ cho GATEWAY (`0x720/0x747`), KHÔNG phải cụm.** DID coding nav-HUD **không có trong image**; key + DID của **cụm** chưa biết.

**Kết luận cho xe owner (thành thật):** bật HUD-nav = **việc CODING cụm** (equipment/vehicleCode self-study), **KHÔNG sửa được bằng app/script** (kênh on-device khoá sau `android.uid.system`; app không có quyền). Đường thực tế:
1. **Tool coding BYD dealer** (OBD-II + ODX): đọc equipment matrix cụm → bật cờ HUD-nav (key dealer) → trigger self-study → `0x38B00030=1` → test app. ⟵ chắc nhất.
2. **On-device** chỉ khả thi nếu có **root/platform-key** + reqId/rxId + security-key + DID của **cụm** (đều chưa biết) → rủi ro cao.
- **Bước AN TOÀN đọc-only kế (on-car):** `getraw instr 30100030` (CONFIG_STATUS) + `38B0002E` (STATUS) + thử UDS `22 <DID>` đọc — không ghi.

**Điều kiện mở khoá (để owner quyết):** cần **máy chẩn đoán/coding BYD + DB equipment cụm** (giá trị vehicleCode/flag bật HUD-nav). Đây là việc **coding XE tại tiệm có tool**, không phải app.

---

## Cập nhật 2026-08-20 — KẾT QUẢ C7 (test tên đường trên HUD BYD anh em)

`hud-roadname-test.bat` chạy trên xe anh em (HUD BYD, đã lên mũi tên+cự ly). Log: `hud-roadname-test_1.txt`.

**Kết quả (bằng chứng):**
- **Sanity = co** (mũi tên + cự ly lên → setup + nav frame OK). `38B00030 = -2147482648` (như mọi xe).
- **6/6 biến thể `khong-hien`** tên đường.
- **`0x420A1010` ROAD_NAME_CHECK_STATE = 0** ở mọi biến thể (KHÔNG phải 1=VALID, KHÔNG phải 2=INVALID) → MCU **chưa hề đánh giá** tên đường.
- `getbytes 0x43FA1008 = <4B> 00000000` (register `_SET`, đọc-ngược trả 4-byte default → inconclusive về write).

**Kết luận (control CJK cho kết quả sạch):**
- **Biến thể C = đúng chuỗi showroom `五一大道南` CŨNG không hiện → LOẠI lỗi font/charset/độ-dài.** Vấn đề KHÔNG ở nội dung/mã hoá.
- `check-state=0` (chưa tới validation; nếu sai nội dung phải ra 2=INVALID) → tên đường **không vào được pipeline** MCU bằng `setbytes 0x43FA1008` đơn lẻ.
- **Khớp app** (app có `sendNextPathName`+setbytes vẫn không lên tên đường trên HUD anh em) → không phải lỗi navopen riêng.
- ⇒ (a) cần **chuỗi kích hoạt OEM** — `SEND_DESTINATION_STATUS 0x43E00038=2` + guidance đúng thứ tự (status→dest→guidance→pathname) mà cả app lẫn test CHƯA làm; hoặc (b) ô tên-đường **gate provisioning riêng** (arrow+dist = element cơ bản; road-name cần thêm).

**Follow-up = MA TRẬN probe** (`scripts/vehicle/hud-roadname-matrix.bat`, gói `~/Desktop/HUD-RoadName-Matrix.zip`): quét **BASELINE + 7 lever** — L1 `SEND_DESTINATION_STATUS 0x43E00038=2` · L2 `DYNAMIC_NAVI_FUNCTION 0x38B0002A` · L3 `MAP_TRANSFER_FLAG 0x40500025` · L4 `GUIDE_ROAD_AHEAD 0x43F01030` · L5 `GUIDE_ADVANCED_ACTION 0x43F08030` · L6 `HUD_NAVIGATION_MAP_SET 0x32B1102E` · L7 `ARRIVAL_PASSPOINT 0x43FFF030` — **+ COMBO(L1-3)**. Mỗi case: bơm nav frame + lever + tên đường (CJK control) → **đọc cụm-status** (`420A1010` check · `38B0002E` navmap · `30100030` cfg · `40C0103B` dest) → quan sát kính → reset lever→0 (cách ly). **Tìm case đổi `420A1010` 0→1/2 hoặc hiện tên đường** = đột phá (+ fix app: thêm lever đó vào `BydHal`); tất cả vẫn 0 = **gate provisioning** → nhánh D6/VDS2100.

---

## Cập nhật 2026-08-20 (2) — SHOWROOM COMPARE: HUD Seal owner NHIỀU KHẢ NĂNG KHÔNG CÓ chức năng nav

**Quan sát owner (2 xe, showroom/demo mode):**
- **SL6 anh em** ở showroom → **demo nav + tên đường, đẹp** → firmware HUD SL6 **CÓ** lớp nav (present+provisioned).
- **Seal owner** ở showroom → **chỉ km/h + ADAS, KHÔNG có demo nav** → firmware HUD Seal **KHÔNG có/không provision** lớp nav.

**Vì sao mạnh:** showroom = OEM tự chạy demo do **firmware MCU/cụm vẽ** (RE §chiều-1 xác định không có Android driver) → exercise đúng cái firmware hỗ trợ. Seal không demo nav = firmware không có lớp nav. **Khớp `0x38B00030 = -2147482648` (not provisioned, A11).**

**Kết luận (hiệu chỉnh, hạ hy vọng coding cho Seal):**
- Bằng chứng **mạnh**: HUD Seal owner **không có chức năng dẫn đường** ở tầng firmware (render speed/ADAS/call, không render nav).
- Coding (VDS2100) **BẬT được feature có-nhưng-tắt; KHÔNG THÊM được feature firmware không có.** Nếu Seal thiếu lớp nav → **coding cũng chịu**.
- **Caveat (chưa 100%):** có thể là lựa-chọn-nội-dung-demo hoặc firmware-có-mà-gate-hoàn-toàn. **Xác nhận cuối = C6 `getHudSupportedModes`**: không có nav-mode → HUD Seal chịu (đổi HUD loại SL6 / HUD rời); có nav-mode-tắt → còn tia coding.
- ⇒ Ưu tiên chạy **C6 probe** khi ra xe để chốt; và **HUD-nav "lên kính Seal" gần như = đổi phần cứng HUD / HUD rời**, không phải code HUD Seal.


---

## Cập nhật 2026-08-20 (3) — KẾT QUẢ MA TRẬN tên đường (HUD BYD anh em) → Android-lever CẠN

`hud-roadname-matrix.bat` chạy trên xe anh em (HUD BYD, đã lên mũi tên+cự ly qua app). Mỗi case lặp 7× (~14s):
`frame 20 250 RD 8 5000` (guidance) → `setraw <LEVER>` → `setbytes 43FA1008 <ROAD>` (pathname); `navistate 2`
set 1 lần đầu; đọc cụm-status; reset lever→0 giữa case. **Thứ tự + persistence đúng** (guidance active +
lever + pathname re-assert).

**Kết quả (BASELINE + L1–L7 + COMBO L1+L2+L3):**
- `0x420A1010` ROAD_NAME_CHECK_STATE = **0 ở MỌI case** (không 1=VALID, không 2=INVALID). Tên đường **không hiện** case nào.
- `0x38B0002E` = **-2147482648** (sentinel CHƯA-PROVISION) · `0x30100030` = **-2147482648** (chưa-provision) · `0x40C0103B`(dest) = 0.
- 7 lever quét: L1 `SEND_DESTINATION_STATUS 0x43E00038=2` · L2 `DYNAMIC_NAVI 0x38B0002A=1` · L3 `MAP_TRANSFER 0x40500025=1` · L4 `GUIDE_ROAD_AHEAD 0x43F01030=250` · L5 `GUIDE_ADVANCED_ACTION 0x43F08030=1` · L6 `HUD_NAV_MAP_SET 0x32B1102E=2` · L7 `ARRIVAL_PASSPOINT 0x43FFF030=1` + COMBO(L1-3). **Tất cả NEGATIVE.**

**Kết luận (bằng chứng):** tên đường trên HUD BYD bị **GATE bởi PROVISIONING ở MCU** — không lever Android nào (đơn lẻ hoặc combo, có guidance+pathname active) đổi được check-state khỏi 0. Củng cố: 2 register HUD-nav-config (`0x38B0002E`, `0x30100030`) đọc ra **not-provisioned**. Bất đối xứng arrow/dist (INT, vẽ vô điều kiện) vs road-name (buffer + check-state gated) khớp hoàn toàn. **Android-lever avenue = CẠN** (cộng C7 v1 cũng negative + app HAL đầy đủ vẫn không lên tên đường).

**Caveat rigor (trung thực):** `.bat` che set-rc (`>nul`) → chưa xác nhận từng lever ĐÃ áp (rc=0) vs bị từ-chối-vì-not-provisioned. Lever họ `0x38B/0x32B` (L2/L6) nhiều khả năng bị từ chối = **chính là** bằng chứng provisioning-gate; lever guidance (L1/L3/L4/L5/L7) nhiều khả năng áp-được-mà-vô-hiệu. Cả hai nhánh → **không Android-activatable**. (v3 tuỳ chọn: log set-rc + đọc thêm họ `0x38B00xx` provisioning để chốt "tại sao" — nhưng gần như chắc chỉ xác nhận gate, không tìm ra lever chạy.)

**Path forward (road-name trên HUD BYD):**
1. **Provisioning/coding VDS2100** — provision HUD-nav-config (`0x38B00030` gate + họ `0x38B00xx`/`0x30100030`) rồi test lại app. Đây là nhánh khả thi nhất còn lại. (D6.)
2. Hoặc **chấp nhận arrow+distance-only** qua app (đang chạy) + tên đường là giới hạn provisioning.
3. Chức năng tên đường CÓ trong firmware HUD (SL6 showroom demo được) nhưng **gated** — nên là bài toán coding/provisioning, không phải app.


---

## Cập nhật 2026-08-20 (4) — v3 PROBE: config-gate + display-content + read-map (dao them, gui anh em)

Sau matrix negative (§08-20(3)), RE thêm bản đồ register đầy đủ (`jadx-DiCarServer/.../instrument/Instrument.java`
= name→hex thật) tìm avenue CHƯA thử. **Phát hiện: matrix ghi `0x32B1102E HUD_NAVIGATION_MAP_SET` chứ CHƯA HỀ
ghi cổng CONFIG `0x38B00030 HUD_NAVIGATION_MAP_CONFIG`** (chỉ ĐỌC status `0x38B0002E`/`0x30100030`). Và có
`0x38B00042 DISPLAY_CONTENT_SET_FUNCTION_CONFIG` (chọn NỘI DUNG hiển thị) chưa thử.

**`scripts/vehicle/hud-roadname-v3.bat`** (gói `~/Desktop/HUD-RoadName-v3.zip` + navopen.jar) — khác matrix ở
chỗ **LOG rc MỌI write** (matrix che `>nul` nên không biết lever nào áp vs bị từ-chối-not-provisioned):
- **RE-FIRE L1-L7** (7 lever matrix cũ) với **LOG rc** → biết từng lever ĐÃ ghi (rc=0, vô hiệu thật) hay BỊ
  TỪ CHỐI (rc≠0/not-provisioned = matrix `>nul` che nên CHƯA thật sự test được). Đây là cái giải toả nghi vấn
  "matrix negative có phải vì lever bị từ chối không".
- **READ-MAP baseline**: đọc full họ HUD-nav config/status (`0x38B00030`/`0x30100030`/`0x38B0002E`/`0x30100031`
  /`0x38B00042`/`0x30100042`/`0x30100015`/`0x3010000D`/`0x40C0103B` + check `0x420A1010`) → thấy cái gì provisioned.
- **N1 = GHI CONFIG GATE `0x38B00030=1`** (cổng cả cuộc điều tra nghi; matrix chưa ghi) → readback + đọc status
  (`0x30100030`/`0x38B0002E` còn -2147482648 không?) → nếu status lật khỏi not-provisioned = ĐỘT PHÁ.
- **N2 = `0x38B00042 DISPLAY_CONTENT_FUNCTION_CONFIG` = 1/2/3** (chọn nội dung HUD; đọc status `0x30100042`).
- **N3 = `0x40C0B026 CP_MAP_NAVIGATION_TIPS`** (setbytes — field text tips, thử nhét tên đường).
- **N4 = OVERSEA `0x1F7A1008` pathname + `0x1F701010` guide** (SL6 export có thể đọc họ 0x1F7).
- **N6 = chuỗi STRICT theo thứ tự** `0x38B00030=1` → navistate 2 → `0x43E00038=2` (dest) → `0x43F01010` (guide)
  → `0x43F01018` (dist) → `0x43FA1008` (pathname) → đọc check-state (matrix set lever rời + reset, chưa thử
  chuỗi liền mạch có config-gate mở trước).
- Mỗi case rollback config về 0. `.bat` pure ASCII + CRLF (verify), self-contained navopen.jar (Windows anh em).

**Kỳ vọng:** N1/N2 là mạnh nhất (ghi đúng cổng CONFIG thay vì SET/status). Nếu `0x38B00030=1` được nhận (rc=0)
+ `0x30100030` lật khỏi -2147482648 → provisioning bật được từ Android (không cần VDS2100). Nếu bị từ chối
(rc≠0) hoặc status vẫn not-provisioned dù mọi write rc=0 → khẳng định **provisioning-gate cứng** → VDS2100/D6.
Dù kết quả nào, v3 (có log rc) sẽ CHỐT dứt điểm "Android bật được không".

## 0.2 — RE FINDING 2026-08-21: tầng ĐÚNG là CarService `ICarHudManager`, KHÔNG phải HAL bydauto thô
RE `sysimg/jadx-DiCarServer` + `decoded/1d1095…/com/byd/car/feature/vision/`:
- Có **CarService HUD manager `ICarHudManager`** (client `car/j2.java`) với API nav-riêng:
  - đọc: **`getHudConfig(int featureMask)`** (bitmask feature provisioned) · `getHudSupportedModes()` · **`isNavigationMapEnabled()`** · `isDynamicNavigationEnabled()` · `isNavigationFusionEnabled()` · `isHudSwitchEnabled()`
  - ghi: **`setNavigationMapEnabled(bool)`** · `setDynamicNavigationEnabled` · `setNavigationFusionEnabled` · `setHudMode`
- **`HudMode` = SIMPLE(1)/STANDARD(2)/OFF_ROAD(3)** = layout hiển thị, KHÔNG phải "mode nav" → nav là feature RIÊNG (`NavigationMapEnabled`), không phải 1 HudMode.
- ⇒ **Tôi đã probe nhầm tầng**: `navopen getraw/setraw 0x38B00030` là **HAL bydauto thô**; tầng điều khiển thật là **CarService `ICarHudManager`** (map xuống HAL). `getHudConfig` bitmask = **cách CHÍNH XÁC** so năng lực Seal vs SL6 (thay cho suy đoán `vehicle_40d` 138/162 — chỉ correlation, chưa chứng minh).
- **Provisioning gate:** service impl của ICarHudManager (native/tiến trình khác — KHÔNG trong jadx app) mới quyết support; nó đọc coding/equipment. HAL thô trả NOT_PROVISIONED trên Seal ⇒ nhiều khả năng CarService setter cũng fail, NHƯNG **chưa test qua tầng đúng**.
- **Bài probe kế (off-car build → on-car):** dùng **Car API `com.byd.car.Car` → ICarHudManager**: (1) `getHudConfig(mask)` + `isNavigationMapEnabled/Fusion/Dynamic` trên Seal — đọc năng lực THẬT; (2) so với SL6 → byte/bit khác chính xác; (3) thử `setNavigationMapEnabled(true)` — nếu FLIP true = **toggle (không cần coding dealer!)**, nếu fail = coding-gated. Đây là câu trả lời dứt điểm "toggle hay coding".

### 0.2.1 — Probe SẴN SÀNG (2026-08-21): `hud-carservice-probe.sh` + navopen-v5
Build xong `apks/navopen-v5.jar` (thêm lệnh `hud` READ + `hudset <true|false>` WRITE) — reflect `DiCar.getCarManager(ctx, ICarHudManager.class)` (pure reflection, hidden-api exempt, self-halt). Txn/descriptor từ RE: DESCRIPTOR `com.byd.car.feature.vision.ICarHudService`; TRANSACTION_getHudConfig=0x1, getHudSupportedModes=0x2, isDynamicNavigationEnabled=0x13, isNavigationFusionEnabled=0x15, isNavigationMapEnabled=0x17, setNavigationMapEnabled=0x18. `Status.STATUS_FAILED = -2147482648` = SENTINEL provisioning giống HAL.
- Chạy: `scripts/vehicle/hud-carservice-probe.sh <car>` (READ) · `… <car> set` (thử bật) · `… <car> off` (khôi phục).
- Kết quả quyết định: **isNavigationMapEnabled flip false→true sau `hudset` = TOGGLE (mở khoá KHÔNG cần coding dealer)**; set trả `STATUS_FAILED`/nguyên false = **coding-gated** (cần VDS2100). `com.byd.car` không trên classpath app_process = reachability NO (phải bundle SDK / chạy trong app).
