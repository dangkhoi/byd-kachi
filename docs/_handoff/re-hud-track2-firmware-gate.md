# RE TRACK 2 — Firmware gate cho HUD kính hiện nav (cluster app + native libBydCluster)

> **Loại:** RE handoff (đọc-hiểu, KHÔNG sửa/commit) · **Ngày:** 2026-08-19 · **Scope:** off-car source RE
> **Mục tiêu:** Tìm ĐIỀU KIỆN CHÍNH XÁC mà firmware zin dùng để quyết định có mirror nav ra HUD kính hay không, và cờ nào bật nó.
> **Nguồn đọc:** decompile `jadx-DiCarServer/.../instrument/Instrument.java` + `Hud00600401300000.java` (carsettings DEX), catalog RE trong codebase, docs prior-art (windshield-hud-enable, cluster-hud-injection-STATE, findings-2026-08-10, ADR 0002, hud-provisioning-compare-2026-08-19).

---

## TL;DR — điều quan trọng nhất phải đọc trước

1. **Có tồn tại một cờ gate rõ ràng trong firmware zin:** `INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG = 0x38B00030`. Consumer thật (đã đọc được source Java) là `Hud00600401300000.readSelfLearnState()`, điều kiện **`return i == 1;`** (so sánh vô hướng `== 1`, KHÔNG phải bitmask). Đây là cờ **provisioning/capability** của cơ chế "cluster tự mirror nav → HUD kính" (self-learn mirror).

2. **NHƯNG cờ này KHÔNG phải thứ quyết định HUD kính có hiện nav hay không trên thực tế.** Bằng chứng on-car mạnh (2026-08-19): xe anh em (BYD Sealion 6, variant `40d=162`) **HUD hiện nav bằng chính app ClusterNav này + GMaps**, mà `0x38B00030` đọc về `-2147482648` **y hệt xe owner** (Seal, `40d=138`). ⇒ Cả hai xe đều `readSelfLearnState()==false`, nhưng một xe vẫn lên nav HUD. Cơ chế `0x38B00030`/self-learn-mirror **không xe nào dùng**; đường nav thật đi qua **`AmapService` → instrument-guide (họ `0x43E`/`0x43F`)**.

3. **Chặn thật nằm ở phía XE — biến thể coding `40d` 138 (owner) vs 162 (anh em)** — một cờ coding CHƯA xác định, **KHÔNG nằm trong bất kỳ cờ `38B*` đọc được nào** (mọi cờ 38B đọc được đều GIỐNG HỆT giữa hai xe). App ghi đúng; unlock = re-code variant qua tool BYD (OBD/UDS), ngoài scope app/adb.

> Nói cách khác: câu hỏi "cờ `0x38B00030==1` có phải gate không" — **về mặt source code là ĐÚNG cho cơ chế self-learn-mirror**, nhưng **về mặt hành vi thực tế đã bị xe thật BÁC** (ADR 0002 đã amend). Handoff này trả lời cả hai tầng.

---

## Họ register HUD nav-map trong firmware zin (Instrument.java)

Nguồn: `jadx-DiCarServer/sources/com/byd/feature/instrument/Instrument.java` (feature-id constants) + `InstrumentMapper.java` (name map).

| Hex id | Tên OEM (Instrument.java) | Vai trò | Dòng |
|---|---|---|---|
| `0x38B00030` | `INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG` | **cờ provisioning/capability** (self-learn gate) | :536 |
| `0x30100030` | `INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG_STATUS` | status-mirror của config (họ `0x3010xxxx`) | :537 |
| `0x32B1102E` | `INSTRUMENT_HUD_NAVIGATION_MAP_SET` | **setter** on/off (ghi 2=ON / 1=OFF) | :538 |
| `0x38B0002E` | `INSTRUMENT_HUD_NAVIGATION_MAP_STATUS` | **runtime status** (2=đang ON) | :539 |

Các register HUD/nav lân cận (khác cơ chế, hay bị nhầm là "nav-content"):

| Hex id | Tên OEM | Ghi chú |
|---|---|---|
| `0x38B0002A` | `INSTRUMENT_DYNAMIC_NAVI_FUNCTION` | chức năng nav động (Instrument.java:457) |
| `0x38B00028` | *(không có constant trong Instrument.java bản này)* | dự án gọi là **HUD_NAV_CONTENT_STATUS**; là status-feedback của toggle "nội dung nav trên HUD", cặp với SET `0x4C10E03A` `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET`. Đọc =1 trên **cả hai** xe. |
| `0x38B00015` | `INSTRUMENT_HUD_CONFIG` (~`SET_HUD_CONFIG`) | 0=không HUD, 1=W-mode kính thường, 2=AR-mode |
| `0x38B0001C` | HUD switch status feedback | 1=bật |
| `0x38B0001E` | HUD ADAS status | 1=bật |
| `0x43E0003A` | `INSTRUMENT_SEND_NAVI_STATUS_SET` | **đường nav thật** — AmapService ghi =2 (Instrument.java:762) |
| `0x43F01010` | `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` | guidance đơn giản (Instrument.java:515) |
| `0x1F701010` | `INSTRUMENT_EASY_NAVI_GUIDE_INFOR_SET` | easy-navi guide (Instrument.java:459) |

---

## Consumer CHÍNH XÁC (ground-truth source) — `Hud00600401300000.java`

**Đây là câu trả lời chốt cho "điều kiện chính xác".** File decompile:
`…/clusternav-re/decoded/e23be25…/jadx-auto/sources/com/byd/ccs/impl/server/hud/Hud00600401300000.java`
(class `com.byd.ccs.impl.server.hud.Hud00600401300000`, từ **carsettings/CarControlServer DEX** — KHÔNG phải native libBydCluster; xem "Đính chính" cuối doc).

```java
// GATE provisioning (self-learn): đọc CONFIG 0x38B00030, so == 1
@Override public boolean readSelfLearnState() {
    int i = DiCarGetter.getInstance().get("0x38B00030");
    CarControlServerLog.d(TAG, "Hud00600401300000 readVisibleFromSelfLearn flag : " + i);
    return i == 1;                      //  ← ĐIỀU KIỆN: scalar == 1 (KHÔNG bitmask)
}

// RUNTIME state: đọc STATUS 0x38B0002E, so == 2
@Override public Boolean getState() {
    int i = DiCarGetter.getInstance().get("0x38B0002E");
    return Boolean.valueOf(i == 2);     //  ← 2 = đang ON
}

// SETTER: ghi SET 0x32B1102E, true→2 / false→1
@Override public void setState(Boolean bool) {
    DiCarSetter.getInstance().set("0x32B1102E", bool.booleanValue() ? 2 : 1);
}

// LISTENER: đăng ký callback trên STATUS 0x38B0002E, đổi state khi intValue == 2
// iEntityCallBack.OnStateChange(Boolean.valueOf(iIntValue == 2));
// registerValueCallback(mAbsBYDAutoHudListener, "0x38B0002E");
```

Ba ngữ nghĩa tách bạch:
- **CONFIG `0x38B00030` == 1** → feature ĐƯỢC provision/hiện (capability). `readSelfLearnState()` = "self-learn thấy có HUD-nav-map trên biến thể này".
- **STATUS `0x38B0002E` == 2** → đang BẬT (runtime). `getState()` + listener.
- **SET `0x32B1102E` = 2/1** → bật/tắt (chỉ có nghĩa KHI đã provision).

Tên log "readVisibleFromSelfLearn" nối với `com.byd.i_uilib.VisibleFromSelfLearnUtil` (carsettings): quyết định một mục cài đặt có **hiện** hay không dựa trên cờ "self-learn", mà self-learn chỉ **mirror state HAL/MCU vào `CarSettingsDb`** (kho đọc), KHÔNG ghi coding.

---

## Trả lời 5 câu hỏi (kèm bằng chứng)

### (1) `0x38B00030` là cờ DUY NHẤT gate nav→HUD zin, hay còn cờ đi kèm?

**Hai tầng trả lời:**

- **Trong cơ chế self-learn-mirror (source):** `0x38B00030` (CONFIG) là **cờ gate duy nhất** mà consumer `readSelfLearnState()` kiểm tra. Nó đi cùng một **họ 4 register** cùng chức năng: CONFIG `0x38B00030` (gate) · STATUS `0x38B0002E` (runtime, `getState()==2`) · SET `0x32B1102E` (2/1) · CONFIG_STATUS `0x30100030` (mirror). `0x38B00028`/`0x4C10E03A` (nav-content toggle) và `0x38B0001C/1E/15` (HUD switch/adas/mode) là **toggle khác cơ chế**, không do `readSelfLearnState()` đọc.
- **Trong đường render thực tế (on-car):** `0x38B00030` **KHÔNG** phải gate. Đường nav→HUD chạy thật đi qua **AmapService → instrument-guide**: `0x43E0003A INSTRUMENT_SEND_NAVI_STATUS_SET=2`, `0x43FA1008` (pathname), `0x43F02018`/`0x43F0201E` (trip minute/second), kèm `naviState=1`. Xe anh em ghi các feature này **rc=0, 0 reject** đúng lúc HUD hiện nav.
  `[hud-provisioning-compare-2026-08-19.md §2]`

⇒ Không có "cờ 38B đi kèm" nào giải thích được sự khác biệt: **mọi cờ 38B đọc được đều GIỐNG HỆT giữa xe có-HUD-nav và xe không**.

### (2) Điều kiện chính xác trong consumer: `==1`? bitmask?

**Scalar equality, KHÔNG bitmask.**
- `readSelfLearnState()`: `int i = DiCarGetter.get("0x38B00030"); return i == 1;`
- `getState()`: `int i = DiCarGetter.get("0x38B0002E"); return i == 2;`
- listener: `OnStateChange(intValue == 2)` trên `0x38B0002E`.

Không có phép `& mask`, không dịch bit, không range. Chỉ so bằng hằng nhỏ (1 cho config, 2 cho status).
`[Hud00600401300000.java: readSelfLearnState/getState/lambda$new$0]`

### (3) Giá trị nào BẬT + `-2147482648` nghĩa gì?

- **Giá trị BẬT:**
  - CONFIG `0x38B00030` = **1** → `readSelfLearnState()` true (provisioned).
  - STATUS `0x38B0002E` = **2** → `getState()` true (đang ON).
  - SET `0x32B1102E` = **2** (bật) / **1** (tắt).
- **`-2147482648` = sentinel "KHÔNG provisioned / không đọc được kiểu này"** do HAL (`DiCarGetter.get`) trả về, **KHÔNG phải giá trị coding thật**. Về nhị phân = `Integer.MIN_VALUE + 1000` = `0x800003E8` (bit dấu 0x80000000 bật). Toàn dự án nhất quán coi nó là "not provisioned / REJECTED / dead id"; phân biệt rõ với giá trị thật (0/1/2) và với các mã lỗi HAL khác (`-2147482645` = `0x800003EB`, `-10011`, `-10013`, hoặc `no permission device 1007`).
  `[oncar-runbook-4mode-track-a-probes:59 "−2147482648 = REJECTED/not provisioned"; speed-limit-sign-oncar-plan:15 "rc=-2147482648 = HAL error sentinel"]`
- **Readback thực tế:** owner `0x38B00030 = -2147482648`; **anh em cũng `-2147482648`** (không xe nào provisioned) — nhưng anh em vẫn lên nav HUD ⇒ đây chính là bằng chứng bác `0x38B00030` là gate.
  `[hud-provisioning-compare-2026-08-19.md §3, bảng cờ]`

### (4) Cờ được SET thế nào? Có "self-learn trigger" không?

- **SET tĩnh = MCU/variant coding.** `0x38B00030` là **coding softcode của instrument-ECU (MCU)**, đọc qua HAL (`DiCarGetter` → DiCar property → MCU). Không phải giá trị học-được-lúc-lái.
- **"Self-learn" ở đây KHÔNG phải re-learn từ MCU/CAN.** Cơ chế `VisibleFromSelfLearnUtil` + `readSelfLearnState()` chỉ **mirror state HAL/MCU vào `CarSettingsDb`** để quyết định menu có **hiện** hay không. Không có "trigger" nào (điều kiện lái/thao tác) làm MCU re-learn cờ này. Không nhầm với `BYDAutoGearboxDevice: EPB self learning result: 1` trong logcat — đó là self-learn **phanh tay/hộp số**, không liên quan HUD.
  `[VisibleFromSelfLearnUtil.java; cast-stuck-recovering…logcat: EPB self learning]`
- **Không ghi được từ head-unit:** write `0x38B00030` bị reject; write SET `0x32B1102E` bị reject khi chưa provisioned; quét toàn firmware **không thấy app factory/diagnostic nào ghi MCU coding**.
- ⇒ **Chỉ set được qua công cụ coding BYD ngoài** (OBD → instrument-ECU **variant coding**, UDS `WriteDataByIdentifier`). **KHÔNG** qua adb/no-root.
  `[findings-2026-08-10 §10; ADR 0002 §Bằng chứng/§Decision]`
- **Lưu ý (đường render thật):** vì `0x38B00030` không phải gate thật, việc set nó =1 **chưa chắc** bật được HUD nav. Cờ coding thật là **variant `40d`** (138→162) — DID cụ thể **chưa xác định**.

### (5) Prior art đã thử gì, kết quả gì?

**`docs/specs/windshield-hud-enable.html`** (spec probe, cập nhật cuối Pass 6 · 2026-08-06):
- Mô hình: **AmapService là đường cluster chuẩn; một số biến thể HUD TỰ mirror maneuver+distance từ trigger đó** (bỏ tên đường). Spec định probe "Seal mirror condition" theo differential biến thể: field nào / session-transition / source-flag / setting / property / profile gate việc mirror. M1 = bật maneuver+distance khi cluster active; M2 = riêng tên đường.
- **Không** chứa `0x38B00030` — spec này đã hướng đúng vào "variant mirror-gate qua AmapService", khớp kết luận cuối 2026-08-19. "Replace AmapService với transport mới" → **REJECTED** (mâu thuẫn bằng chứng cross-variant mirror).

**`docs/archive/_handoff/cluster-hud-injection-STATE.md`** (+ findings-2026-08-10 §10):
- Nav→cluster: **WORKS** (AUTONAVI broadcast → amapservice → cụm). Ngoài scope.
- Nav→HUD: kết luận (khi đó) **GATED** — thử ghi trực tiếp: HUD config `0x38B00015→2` **REJECTED**; nav-map SET `0x32B1102E` + CONFIG `0x38B00030` **REJECTED (not provisioned)**; nav-content toggle `0x4C10E03A` rc=0 nhưng **không hiệu ứng HUD nhìn thấy**; gọi thẳng `INSTRUMENT_GUIDE_INFO_SIMPLE_SET 0x43F01010` (`sendSimpleGuidanceInfo`) **render nothing** trên trim này.
- Đọc ra consumer `readSelfLearnState() == 1`; owner đọc `-2147482648` ⇒ giả thuyết "coding-locked, cần UDS set `0x38B00030=1`".

**`docs/diagnostics/oncar-session-2026-08-16.md` + `next-session-B` + backlog A11:** readback on-car xác nhận owner `0x38B00030=-2147482648`, `0x38B0002E` not-provisioned, các toggle HUD chung `=1`. Write-attempt → mọi write reject (coding-locked). Chốt ADR 0002 (coding-locked).

**`docs/diagnostics/hud-provisioning-compare-2026-08-19.md` (SỬA ADR 0002 — kết luận mới nhất):**
- So xe owner (Seal, `40d=138`) vs anh em (Sealion 6, `40d=162`, region ROW), **cùng app ClusterNav + GMaps**.
- Anh em: **HUD hiện nav bằng chính app này** (trước khi cài app, HUD không có nav); log AmapService ghi `43E0003A=2`, pathname, trip-info **rc=0**.
- **Cả hai xe đọc `0x38B00030 = -2147482648` GIỐNG HỆT** + mọi cờ 38B đọc được giống hệt.
- ⇒ **`0x38B00030` bị bác là gate.** Nghịch lý "toggle nav-content ON mà HUD không nav" **không phải** do `38B00030` chưa provisioned. Chặn = **variant coding `40d` 138 vs 162** (cờ cụ thể chưa biết, không nằm trong 38B). App đúng; unlock = re-code variant qua tool BYD OBD/UDS.

---

## Đính chính RE (để phiên sau không lặp lại nhầm)

- **`readSelfLearnState()` là JAVA (carsettings/CCS), KHÔNG phải native libBydCluster.** Findings-2026-08-10 §10 ghi "native decompile libBydCluster" là **không chính xác**: consumer thật là `com.byd.ccs.impl.server.hud.Hud00600401300000` trong **carsettings DEX**, đọc qua `DiCarGetter` (HAL getter). RE native libBydCluster (T3) chỉ phủ **data-item biển báo tốc độ** (`trafficSignValue@0xd95fc→0xd9978`, `slaEquip`, `trafficSign`…), **không** đụng cờ HUD `0x38B00030`.
  `[FirmwareEvidence.kt H1 cite Hud00600401300000.java:69/62; hud-sign-re/README.md "Concrete Java evidence"]`
- **Grep `0x38B00030` trong `jadx-ClusterDebug` = 0 match.** ClusterDebug (app cụm Qt debug) không tham chiếu cờ này; cờ sống ở DiCarServer (Instrument constants) + carsettings (consumer). Xác nhận cờ là chuyện **feature/HAL service**, không phải Qt cluster app.

---

## Kết luận cho owner + điều kiện mở khoá (trace-den-tan-cung)

1. **Source-level:** firmware zin CÓ gate rõ ràng `0x38B00030 == 1` (`readSelfLearnState`) cho cơ chế cluster→HUD self-learn-mirror; giá trị bật = 1; `-2147482648` = not-provisioned sentinel; set bằng MCU variant coding (không có đường app).
2. **Behavior-level (đã kiểm chứng bằng 2 xe thật):** cơ chế đó **không phải** đường nav→HUD thực tế; `0x38B00030` **không** quyết định. Đường thật = AmapService → `0x43E/0x43F` guide + `naviState=1`, app **đã ghi đúng**.
3. **Chặn thật = variant coding XE `40d` 138 (owner) vs 162 (anh em)** — **KHÔNG phải app, KHÔNG phải `38B00030`, KHÔNG phải phần cứng HUD** (HUD owner render tốc độ/ADAS/phút:giây tốt, chỉ lớp nav tắt).
4. **Chưa đóng cửa (cần để mở khoá):**
   - Xác định **byte/DID coding cụ thể** khác giữa `40d` 138 và 162 → cần **variant-coding dump đầy đủ hai xe** bằng tool chẩn đoán BYD, hoặc bảng tra ý nghĩa `40d`. Không nằm trong cờ 38B đọc được.
   - Mở khoá (owner quyết): mang xe tới nơi có tool coding BYD, re-code provisioning HUD-nav theo biến thể 162 (OBD/UDS `WriteDataByIdentifier`). Ngoài scope app/adb/no-root.

---

## Chỉ mục nguồn (file:line)

- `…/jadx-DiCarServer/…/instrument/Instrument.java` — :536 CONFIG `0x38B00030`, :537 CONFIG_STATUS `0x30100030`, :538 SET `0x32B1102E`, :539 STATUS `0x38B0002E`, :457 DYNAMIC_NAVI `0x38B0002A`, :515 GUIDE_SIMPLE `0x43F01010`, :459 EASY_NAVI `0x1F701010`, :762 SEND_NAVI_STATUS `0x43E0003A`.
- `…/jadx-DiCarServer/…/instrument/InstrumentMapper.java` — :729/750/758/768/769 name map họ HUD_NAV.
- `…/clusternav-re/decoded/e23be25…/jadx-auto/…/hud/Hud00600401300000.java` — `readSelfLearnState()` (`==1`), `getState()` (`==2`), `setState()` (2/1), listener trên `0x38B0002E`.
- `…/clusternav-re/decoded/5f4b840…/…/i_uilib/VisibleFromSelfLearnUtil.java` — self-learn = mirror HAL→CarSettingsDb (menu visible), không ghi coding.
- `offcar-planner/…/FirmwareEvidence.kt` — H1 gate cite `Hud00600401300000.java:69` + `Instrument.java:536/538/539`; edge `E-NAV-HUD-GATE` = `CANDIDATE_UNEXECUTED`.
- `docs/diagnostics/hud-sign-re/README.md` — "H1 map gate": SET `0x32B1102E` 2=ON/1=OFF; status 2=enabled (`Hud00600401300000.java:39–43,62–63`).
- `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §10 — gate discovery (+ đính chính "native" ở trên).
- `docs/decisions/0002-hud-nav-coding-locked.md` — ADR (Accepted, **amended 2026-08-19**).
- `docs/diagnostics/hud-provisioning-compare-2026-08-19.md` — **REFUTATION**: 2 xe = `-2147482648`, anh em vẫn nav; chặn = `40d` 138 vs 162.
- `docs/specs/windshield-hud-enable.html` — spec probe AmapService variant mirror-gate (Pass 6, 2026-08-06).
- `core/…/carexec/CarExecHudCatalog.kt` + `CarExecClusterDiagnosticsCatalog.kt` — ghi chú RE candidate (HUD switch `0x4C10E023`, nav-content `0x4C10E03A`, cảnh báo "đừng mặc định bật cờ này thì làn cụm nhảy sang HUD").

> **KHÔNG commit/push** (RE đọc-hiểu). File này chỉ là handoff text.
