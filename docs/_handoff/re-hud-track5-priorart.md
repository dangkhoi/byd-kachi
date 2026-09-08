# RE Track 5 — PRIOR ART: bật nav trên HUD-zin (kính lái) + tìm variant/firmware nơi HUD-zin CÓ nav

> **Loại:** RE đọc-hiểu (read-only, KHÔNG sửa code, KHÔNG commit/push) · **Ngày:** 2026-08-19
> **Mục đích:** Gom mọi thứ đã thử/đã biết về bật HUD-zin nav để các track khác **không lặp lại**; và liệt kê dấu vết trong các cây decompiled cho thấy HUD-zin hiện nav ở variant/region khác.
> **Nguồn đã đọc:** `docs/specs/windshield-hud-enable.html`, `docs/design/navigation-hud-evidence.html`, `docs/decisions/0002-hud-nav-coding-locked.md`, `docs/diagnostics/hud-provisioning-compare-2026-08-19.md`, `docs/archive/_handoff/{cluster-hud-injection-STATE, session-2026-08-06-pm2-firstlaunch-fixes-and-hud-enable, stage-impl-hud-done}.md`, `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md`, `docs/PROJECT-BACKLOG.md` (A7/A11/C3).
> **Cây decompiled đã grep:** `~/Library/Caches/clusternav-re/decoded/*/{jadx-auto,jadx-fallback,apktool}` (AmapService.apk, L3_new/L3_old.apk, VehicleSettings dex, common/jump dex), `~/Library/Caches/clusternav-re/sysimg/*` (DiCarServer/ClusterDebug/CanDataCollect/BydDevelopmentTools), `~/Library/Caches/clusternav-re/openbyd-2.3/*` (OpenBYD 2.3).

---

## TL;DR (kết luận hiện hành — đã được củng cố bằng bằng chứng xe thật)

**HUD-zin nav = feature bị gate bởi VARIANT-CODING của XE (biến thể `40d` 138 vs 162), KHÔNG phải bug app, KHÔNG phải phần cứng HUD, KHÔNG phải cờ `0x38B00030` đọc được.** App ClusterNav đã ghi **đúng** đường AmapService/instrument-guide; **chính app này** làm HUD-zin lên nav trên **xe anh em** (`40d`=162, region ROW). Owner car (`40d`=138) đọc `38B00030` **y hệt** anh em (`−2147482648`) mà không lên nav ⇒ chặn nằm ở coding không-đọc-được-qua-getraw. **Mở khoá = re-code sang provisioning 162 qua OBD/UDS (dealer/coding tool), KHÔNG qua adb/app/no-root.** Byte coding cụ thể **chưa xác định** (không nằm trong các cờ 38B đọc được).

**⇒ Track khác đừng lặp:** đừng "fix" oversea/HUD write trong app (đúng rồi); đừng đuổi theo `0x38B00030=1`; đừng đổi HUD hardware; đừng thử tìm "missing Intent field / AmapService branch" phía app (đã bị bằng chứng xe thật loại). Việc còn lại là **coding XE**.

---

## (1) `windshield-hud-enable.html` — đã KẾT LUẬN/THỬ gì, còn để mở gì?

**Bản chất:** đây là **consolidated spec/plan** ngày 2026-08-06 (baseline `main@d85b9f2e13c3`), qua **10 review pass** ("APPROVED — 0 P0–P3"), nhưng **ON-CAR = NOT STARTED**. Toàn bộ Tasks **T0–T8** và evidence rows **D0–D5 / P0–P8 / M1 / M2 chưa từng chạy trên xe**. Nó là *kế hoạch được chứng nhận*, **không** phải kết quả on-car.

**Mô hình nó chốt (design):**
- **AmapService là canonical content path** (`AUTONAVI_STANDARD_BROADCAST_SEND` → `AmapFrameBuilder` → AmapService). **KHÔNG** thay bằng direct HAL làm transport chính (R2, §4.9 REJECTED "replace AmapService").
- Giả thuyết trung tâm: một số biến thể HUD **tự mirror** maneuver+distance từ **cùng** cluster trigger; **Seal không mirror** → bài toán = tìm **"mirror gate / condition / field / profile" còn thiếu trên Seal**. Road name tách thành **M2 side-channel** (HUD variant chạy được cũng thiếu road name).
- Correction bắt buộc đã ghi: (a) HUD Seal là **W-mode và đang ON** → "bật switch" không phải lời giải; (b) `rc=0` ≠ hiển thị; (c) direct `INSTRUMENT_*` rc=0 đơn lẻ **không render** HUD Seal; (d) `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET=1` đơn lẻ **không** làm HUD mirror.

**Còn để MỞ (Open Questions Q1–Q10 — TẤT CẢ còn mở, không cái nào đóng on-car):**
- Q1/Q2: điều kiện chính xác làm AmapService session mirror sang HUD (nằm ở Intent field/state? AmapService branch? vehicle setting/property? fission/1for2? **downstream HUD ECU profile**?).
- Q3: có nav start-state ngoài `10019/9` STOP không (code hiện không bi; cấm đoán).
- Q4: `EXTRA_IS_FOREGROUND=0` / `TYPE=1` / map-source flags có tham gia không.
- Q5: HUD variant chạy được nghe **HAL/CAN** hay `sendInfo2(4, FlatBuffer NaviInfo)`.
- Q6: tại sao road name bị drop dù cluster có `NEXT_ROAD_NAME`.
- Q7: model/HUD/ROM cụ thể nào owner đã thấy auto-mirror.
- Q8: coupled-only hay có independent HUD gate.
- Q9: app-UID có quyền apply gate/physical switch không (shell-only success không đủ).
- Q10: fallback nếu không tìm được gate → giữ cluster nav, HUD content disabled, chỉ nói "UNSUPPORTED" sau khi vét cạn matrix.

**⚠ Quan trọng — spec này ĐÃ BỊ VƯỢT QUA (superseded) một phần:** cách đóng khung "tìm field/condition thiếu phía APP" đã bị bằng chứng on-car sau đó bác:
- **A11 (2026-08-16/18):** write `0x38B00030` bị từ chối, status/config = sentinel → "coding-locked".
- **C3 (2026-08-19):** xe anh em (`40d`=162) lên HUD nav bằng **chính app này**, `38B00030` **giống hệt** owner → chặn = **variant coding 138 vs 162**, KHÔNG phải field/branch phía app.
- Tức là **Q5 ("downstream HUD ECU profile remains plausible") hoá ra là nhánh đúng.** Bộ scaffolding spec dự tính (vehicleTest receiver, `HudMirrorController`, `AmapEmissionArbiter`, D0–D5/P0–P8) **chưa build/chạy** và phần lớn không cần nữa cho câu hỏi gốc — vì gate không nằm phía app.

---

## (2) Các handoff cũ đã thử đường nào (settings/service/HAL/CAN), kết quả? (ĐỪNG LẶP)

### `session-2026-08-06-pm2` — HAL probe qua adb (`TEST_HAL_WRITE` / `TEST_HUD_NAV`)
- **INSTRUMENT device** `writeNavFrame` (`INSTRUMENT_SEND_NAVI_STATUS_SET`, `INSTRUMENT_GUIDE_INFO_SIMPLE_SET`, `INSTRUMENT_FRONT_CROSSING_DISTANCE_SET`, `PATHNAME`) → **tất cả rc=0 nhưng KHÔNG gì hiện trên HUD** → HUD nav layer không enabled/coding. **Bài học lõi: `rc=0` (HAL accept) ≠ display render.**
- **ADAS device** → **từ chối MỌI write** (`rc=-2147482648`): `ADAS_SLA_STATE_SET`, `setSLAState(50)`, `INSTRUMENT_TRAFFIC_SIGN_RECOGNITION_SYSTEM`; path `writeSpeedLimit` cũ trỏ feature-id **không tồn tại** trên ROM này.
- **SETTING device** → **ACCEPT (rc=0)**: `SETTING_SPEED_LIMIT_SET`, `SET_SPEED_REMINDER_SET` — nhưng **không render** (là setpoint nhắc quá tốc, không phải sign).
- Nav→cluster qua `AUTONAVI_STANDARD_BROADCAST_SEND` → **WORKS** (đã proven lâu, out-of-scope).

### `cluster-hud-injection-STATE.md` + `hud-cluster-injection-findings-2026-08-10.md`
- **Verdict findings (§ đầu, dòng 5/11/13/46):** "NOT achievable via any adb/app-reachable software path. Proven exhaustively on-car. Remaining doors OFF-CAR only: (1) **Variant/OBD coding** (most promising for HUD-nav; standard BYD per-trim gating), (2) native decompile, (3) CAN."
  - `Instrument HUD nav-map: set 0x32B1102E, status 0x38B0002E, config 0x38B00030 → write REJECTED; status/config=sentinel (not provisioned) → the cluster→HUD mirror enable, coding-locked`.
- **STATE.md verdict thời điểm đó:** "Nav → HUD: GATED (**hardware**)" — nghĩ HUD Seal đời cũ, không có nav widget; **verdict này SAU BỊ BÁC** (xem §3: cùng HUD line lên nav trên xe anh em chỉ với app). Vẫn hữu ích: đã ghi `0x43F01010 INSTRUMENT_GUIDE_INFO_SIMPLE_SET` tồn tại nhưng HUD scene không render nav trên trim 138; `sendSimpleGuidanceInfo` trực tiếp render **nothing**.
- **Channels đã phân tích & fail cho speed-sign (liên quan, đều cần root):** `sendInfo(1000)`/`clusterDebug` (opcode-only, không value), `sendInfo2(8)`/`handleIviRccReqMsg` (không có setDataItem), HAL `setraw` (rc=0 nhưng display bỏ qua), ZMQ publish (`192.168.195.x` — domain firmware, unreachable từ Android), TEST-device CAN SIMULATE `0xAA00020F` (cần arbitration id + bits, config root-locked). **Vì sao app cũng không:** cùng phía Android, không tới được `192.168.195.x`, AutoContainer whitelist = **chỉ** `com.xdja.clusterdemo`.

### `oncar-session-2026-08-16` §4 (A11 readback)
- `38B00030 = −2147482648` (NOT provisioned), `38B0002E` không provisioned. Các toggle chung **BẬT**: `38B00015=1` (W-mode), `38B0001C=1` (switch), `38B00028=1` (nav-content), `38B0001E=1` (adas) → nghịch lý "bật nav-content mà HUD không nav".
- Write `38B00030` **rejected**. Đường nuôi cụm `43F01010/018` ghi **rc=0** (accepted). Oversea `0x1F7*` + dualIcon domestic `43F01030` **rejected** (`no permission … device: 1007`; device codes app dùng 1007/1023/1038/1014).

### `stage-impl-hud-done.md` (2026-07-24)
- `BydHal.writeNavFrame` ghi road `toByteArray(UTF_16LE)` vào `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET`; `NavFormat.fitRoadName` + `HUD_ROAD_MAX_UNITS=7`. **OQ2:** buffer size HUD thật chưa verify on-car (chưa nâng quá 7).

---

## (3) Dấu vết trong firmware/apk cho thấy HUD-zin hiện nav ở variant/region khác

### 3a. Bằng chứng QUYẾT ĐỊNH (xe thật — mạnh nhất, không cần decompile)
- **Xe anh em: Seal-family, `40d` variant = 162, `ro.build.region = ROW`, `gbClientVersion` cùng `6125f`.** HUD-zin hiện nav (mũi tên + distance + đếm phút:giây + tên đường cuộn) bằng **chính app ClusterNav này + GMaps**; **trước khi cài app, HUD anh em KHÔNG có nav** (owner xác nhận). Owner car `40d`=138 thì không.
- Đường nav lên HUD anh em (log PID `AmapService`, ghi thành công 0 reject): `0x43E0003A INSTRUMENT_SEND_NAVI_STATUS=2`, `0x43FA1008 TARGET_NEXT_PATHNAME` (UTF-16LE cuộn), `0x43F02018 NAVI_TRIP_INFO_MINUTE`, `0x43F0201E NAVI_TRIP_REMAINING_SECOND` + `GuideInfo.naviState:1`. App mình ghi **đúng** các feature này (`BydHal.kt:284/290/312/313`).
- ⇒ Khác biệt DUY NHẤT bắt được = **`40d` 138 vs 162** (+ region ROW). Cờ 38B đọc được **giống hệt** hai xe. Byte coding cụ thể cho HUD-nav **chưa xác định**, KHÔNG nằm trong 38B đọc-được.

### 3b. Dấu vết trong cây decompiled (chứng minh firmware CÓ họ feature HUD-nav + tier AR-HUD)
`com.byd.feature.instrument.Instrument` (trong **DiCarServer** `sysimg/jadx-DiCarServer/...` và **L3_new/L3_old.apk** `decoded/1d10959…`,`f772c16…`):
```
INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG          = 0x38B00030   (cờ provisioning — 2 xe đều −2147482648)
INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG_STATUS   = 0x30100030
INSTRUMENT_HUD_NAVIGATION_MAP_SET             = 0x32B1102E
INSTRUMENT_HUD_NAVIGATION_MAP_STATUS          = 0x38B0002E
INSTRUMENT_ARHUD_NAVIGATION_MAP_RESOLUTION_STATUS = 0x34C0000B   ← firmware có cả tier AR-HUD (HUD cao cấp hơn)
```
- `com.byd.vehiclesettings.hud.model.optiondisplay.HudOptionDisplayModel` (VehicleSettings dex `decoded/ea75450d…`): `getHudNavigationState()` / `setHudNavigationState(boolean)` với ánh xạ **Ui2Mcu / Mcu2Ui** → toggle HUD-nav của **chính app OEM**, backed bởi **MCU state** ⇒ củng cố "gate ở MCU/coding-side".
- `com.byd.common.jump.JumpConstant` (`decoded/e23be25…`): deep-link `ACTION_3ST_VEHICLE_SETTINGS_HUD_HUDNAVIGATIONFUSE = "00600400090000"`, `..._HUD_HUDNAVIGATIONMAP = "00600401300000"` → firmware có **trang settings "HUD navigation map"** + một **"HUD navigation fuse"**.
- `com.byd.common.datacollect.DataCollectID.VS_HUD_NAVIGATION_MAP_DCID = 1633` → telemetry id cho HUD nav map (feature tồn tại trong firmware).
- `com.byd.car.feature.vision.ICarHudService` (Binder trong L3 APK): `enableHudAdaptive()`, `getHudAngle()`, `getHudBrightnessLevel()`, `getHudConfig(int)`, `getHudHeightLevel()`, `getHudMode()`, … → **service điều khiển HUD** (bề mặt CHƯA thử; gần như chắc chắn **platform-signature-gated** như AutoContainer whitelist; **không** thấy setter "set nav content" trong stub).

### 3c. Prior-art phần mềm (không phải firmware variant, nhưng xác nhận API write đúng)
- **OpenBYD 2.3** (`com.sr.openbyd`, app bên-thứ-3) — `~/Library/Caches/clusternav-re/openbyd-2.3/sources`:
  - Model `HudNavigationData(iconId, distanceMeters, roadName, remainingDistanceMeters, remainingTimeSeconds, secondaryIconId, secondaryDistance, secondaryRoadName)` (`defpackage/h60.java`) + selector app nav-HUD **GOOGLE_MAPS / WAZE / YANDEX** (`defpackage/g60.java`, `R.string.hud_nav_app_*`).
  - `com.sr.openbyd.proxy.CarControlImpl` ghi qua `BYDAutoInstrumentDevice` bằng **CHÍNH các feature-id ClusterNav dùng**: `INSTRUMENT_GUIDE_INFO_SIMPLE_SET=1139806224 (0x43F01010)`, `SET_NAVI_SCREEN_STATUS_SET=1276174357 (0x4C10E015)`, `HUD_SCREEN_NAV_LAYOUT=3`, `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET=1140461576`, `INSTRUMENT_SEND_NAVI_STATUS_SET`, `INSTRUMENT_NAVI_TRIP_INFO_MINUTE/SECOND_SET`, `NAVI_STATUS_ACTIVE=2`, `GUIDE_INFO_CLEAR=0`, …
  - ⇒ **Xác nhận đường write của app là ĐÚNG và trùng với một app thứ-3 đã chạy**. Đây là bằng chứng API, **KHÔNG** phải "firmware variant hiện nav" — OpenBYD vẫn dựa vào xe **đã provision** HUD-nav.
- `com.tmap.auto.byd` (TMAP, SKT Hàn Quốc — `sysimg/tmap_c1/c2`, **không cài trên xe owner**) = nav stack cho bản **ROW/export** ⇒ khớp region ROW của xe anh em; export build ship nav stack khác.

**Bottom line Q3:** "variant nơi HUD-zin hiện nav" = **xe anh em `40d`=162 / region ROW chạy chính app này** (documented, owner-confirmed). Chuỗi firmware xác nhận họ feature HUD-nav (+ tier AR-HUD) tồn tại và là feature **gate theo MCU-state / variant-coding**. **Không** có cờ app-đọc-được phân biệt 138 vs 162; byte coding chính xác **chưa xác định**.

---

## (4) Danh sách "ĐÃ THỬ & THẤT BẠI" (kèm lý do) — để track khác KHÔNG lặp

| # | Đã thử | Kết quả | Lý do / ghi chú |
|---|--------|---------|-----------------|
| 1 | Direct `INSTRUMENT_*` HAL writes (`writeNavFrame`: NAVI_STATUS / GUIDE_INFO_SIMPLE / FRONT_CROSSING_DISTANCE / PATHNAME) | rc=0 nhưng **HUD trống** | `rc=0` ≠ render; nav layer HUD chưa provisioned trên 138 |
| 2 | Write `INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG 0x38B00030 = 1` | **REJECTED** | register coding MCU; firmware **không** có đường app ghi MCU coding (self-learn chỉ mirror MCU→cache đọc) |
| 3 | Read/write `0x38B0002E` (STATUS), `0x32B1102E` (SET) | status=sentinel (not provisioned); set rejected | coding-locked |
| 4 | Bật các toggle HUD chung (`38B00015` W-mode, `38B0001C` switch, `38B00028` nav-content, `38B0001E` adas — đều đã =1) | HUD hiện speed/ADAS/call/phút:giây **nhưng KHÔNG nav** | toggle vô nghĩa khi thiếu provisioning variant; lớp nav gate riêng |
| 5 | Giả thuyết **"`38B00030==1` là cờ quyết định"** (ADR 0002 gốc) | **BÁC** | xe anh em đọc `−2147482648` y hệt mà vẫn lên nav ⇒ không phải cờ này |
| 6 | Giả thuyết **"HUD Seal hardware quá cũ / không có nav widget"** (STATE 2026-08-10) | **BÁC** | cùng HUD line lên nav trên xe anh em chỉ với app; hardware ổn |
| 7 | "Bật switch HUD ON" | không phải lời giải | HUD Seal là W-mode và **đã** ON |
| 8 | `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET=1` đơn lẻ | HUD **không** mirror | single toggle không đủ |
| 9 | Oversea frames `0x1F7*` + dualIcon domestic `0x43F01030` | **REJECTED** `no permission … device: 1007` | device-code/permission gate trên trim không provision (đúng hành vi; đã cache để hết log spam, **KHÔNG** hard-remove) |
| 10 | Thay AmapService bằng direct HAL làm transport chính | REJECTED (design) | AmapService là canonical proven; nav xe anh em đi qua 43E/43F |
| 11 | ADAS device writes (speed-sign, liên quan) | tất cả rejected `rc=-2147482648` | ADAS domain từ chối app write; id `writeSpeedLimit` cũ không tồn tại trên ROM |
| 12 | Root để mở coding | **DECLINED bởi owner** | không an toàn trên xe, rủi ro brick |
| 13 | HUD-zin nav owner car qua **bất kỳ** đường adb/app/no-root | **NOT achievable** | gate = variant coding (`40d` 138 vs 162), off-car only |
| 14 | ZMQ / TEST-device CAN / sendInfo(2) (cho pipeline cụm, liên quan) | fail no-root | domain `192.168.195.x` unreachable; arbitration id root-locked; AutoContainer whitelist chỉ `com.xdja.clusterdemo` |

**Bề mặt CHƯA thử (đánh giá, đừng đuổi mù):**
- `com.byd.car.feature.vision.ICarHudService` Binder (`enableHudAdaptive`/`getHudConfig(int)`/`getHudMode`…) — gần như chắc **platform-signature-gated**; **không** thấy setter nav-content. Chỉ nên đọc-thử `getHudConfig`/`getHudMode` để so 138 vs 162 (read-only), KHÔNG kỳ vọng ghi được.
- VehicleSettings `setHudNavigationState(true)` — là setter Ui→Mcu của **app OEM**, vẫn bị bao bởi provisioning MCU; app mình không gọi cross-process được (permission).

**Điều gì SẼ mở khoá (để owner quyết — theo `trace-den-tan-cung`):**
1. **Re-code xe owner `40d` 138 → provisioning biến thể 162** qua công cụ coding BYD (OBD → instrument ECU, UDS `WriteDataByIdentifier`). Đây là việc **coding XE**, ngoài scope app.
2. Hoặc lấy **variant-coding dump đầy đủ** cả hai xe (138 vs 162) / bảng tra ý nghĩa `40d` để xác định **byte coding cụ thể** cho HUD-nav (hiện chưa biết, không nằm trong 38B đọc-được).

---

## Con trỏ nguồn (để verify)
- Quyết định lõi + amendment: `docs/decisions/0002-hud-nav-coding-locked.md`.
- Bằng chứng 2 xe (138 vs 162): `docs/diagnostics/hud-provisioning-compare-2026-08-19.md`; readback `~/Desktop/hud-xe-minh.txt`, `hud-compare-2.txt` (off-repo).
- Verdict exhaust adb/app: `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` (dòng 5/11/13/46), `docs/archive/_handoff/cluster-hud-injection-STATE.md`.
- HAL probe rc=0≠render: `docs/archive/_handoff/session-2026-08-06-pm2-firstlaunch-fixes-and-hud-enable.md`.
- Plan (chưa chạy on-car) + Open Questions Q1–Q10: `docs/specs/windshield-hud-enable.html`.
- Feature-id table: `~/Library/Caches/clusternav-re/sysimg/jadx-DiCarServer/sources/com/byd/feature/instrument/Instrument.java` (0x38B00030 / 0x32B1102E / 0x38B0002E / 0x34C0000B AR-HUD).
- VehicleSettings HUD-nav toggle (Ui↔Mcu): `~/Library/Caches/clusternav-re/decoded/ea75450d…/…/vehiclesettings/hud/model/optiondisplay/HudOptionDisplayModel.java`.
- HUD settings deep-links: `~/Library/Caches/clusternav-re/decoded/e23be25a…/…/common/jump/JumpConstant.java`.
- HUD Binder service: `~/Library/Caches/clusternav-re/decoded/1d10959…/jadx-fallback/…/byd/car/feature/vision/ICarHudService.java`.
- OpenBYD 2.3 prior-art: `~/Library/Caches/clusternav-re/openbyd-2.3/sources/com/sr/openbyd/proxy/CarControlImpl.java`, `defpackage/{h60,g60}.java`.
- Backlog: `docs/PROJECT-BACKLOG.md` A7/A11/C3 (C3 = XONG, chốt variant-coding).

> RE-only. Không sửa code, không commit/push.
