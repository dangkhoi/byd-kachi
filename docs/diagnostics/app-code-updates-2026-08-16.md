# APP CODE UPDATES — rút từ phiên on-car 2026-08-16

> **Trạng thái**: Current · **Cập nhật**: 2026-08-16 · **Mục đích**: Danh sách sửa CODE app rút từ phiên on-car 2026-08-16 (file:line + acceptance).

> Việc cần sửa trong CODE app (không phải doc). Tất cả **OFF-CAR** trừ khi ghi rõ. Mỗi task có file:line + acceptance.
> Nguồn: catalog icon on-car 2026-08-16 (`oncar-session-2026-08-16.md`) + học OpenBYD 2.3 (RE cache `openbyd-2.3/sources/`).

---

## ★ Task 1 [P1] — Vòng xuyến: hiện HƯỚNG RA + SỐ LỐI RA (bug user báo nhiều nhất)

**Bug:** vòng xuyến **toàn báo generic "vào vòng xuyến"**, không biết ra hướng nào / lối ra mấy.

**Nguyên nhân (đọc code):**
- `core/.../navigation/ManeuverSignature.kt:223` — `name.contains("roundabout") -> 11` **gộp TẤT CẢ biến thể vòng xuyến về generic (AMAP 11)** → `Maneuver.ROUNDABOUT.toHudIcon()=20` (generic) → vứt mất hướng ra mà GMaps ĐÃ cho qua icon.
- `app/.../NavRepository.kt:126-133` — chỉ ra `24+N` (số lối ra) khi text có "lối ra thứ N"; else fallback generic.

**GMaps CÓ cho hướng ra qua ICON** (proven off-car): `ManeuverRegistry.kt` có đủ chữ ký `maneuver_roundabout_enter_and_exit_ccw_{normal,slight,sharp}_{left,right}` + `_straight` + `_u_turn` (cả CW). Classifier khớp được nhưng đang bỏ.

**Bảng map authoritative — COPY từ OpenBYD `w40.java` (đã cross-validate 100% với catalog on-car + `HudController.java`):**

CAN icon (HudController.java): `15=RAB_3/4_LEFT · 16=RAB_1/4_LEFT · 17=RAB_3/4_RIGHT · 18=RAB_1/4_RIGHT · 21/22=u-turn(L_TO_R/R_TO_L) · 25..34=CCW lối ra 1..10 · 35..44=CW lối ra 1..10`.

Map chữ ký GMaps → CAN (VN = CCW/RHT là chính; CW cho LHT):
| Chữ ký (ManeuverRegistry name) | CAN |
|---|---|
| `roundabout_enter_ccw` · `..._ccw_straight` · `roundabout_exit_ccw` · generic | **20** |
| `roundabout_enter_and_exit_ccw_{normal,slight,sharp}_left` | **15** |
| `roundabout_enter_and_exit_ccw_{normal,slight,sharp}_right` | **18** |
| `roundabout_enter_and_exit_ccw_u_turn` | **22** |
| `roundabout_enter_cw` · `..._cw_straight` · `roundabout_exit_cw` | **19** |
| `roundabout_enter_and_exit_cw_..._left` | **16** |
| `roundabout_enter_and_exit_cw_..._right` | **17** |
| `roundabout_enter_and_exit_cw_u_turn` | **21** |

**Số lối ra** (25–44): OpenBYD `YandexManager.java` parse `"exit N"/"Nth exit"` từ TEXT (cả Waze) — GIỐNG `NavFormat.roundaboutExit` mình đã có. Giữ path `24+N` (`NavRepository.kt:130`). Ưu tiên: **có số → 24+N; không có số → dùng hướng (bảng trên); không có gì → generic 20.**

**Cách sửa (đề xuất):**
1. `Maneuver.kt`: thêm member hướng ra — `ROUNDABOUT_LEFT`(→HUD 15), `ROUNDABOUT_RIGHT`(→18), `ROUNDABOUT_STRAIGHT`(→20), `ROUNDABOUT_UTURN`(→22). (CW: 16/17/19/21 nếu muốn phủ LHT.) `toAmapIcon` cho cụm-strip giữ 11 (strip không có glyph hướng — chỉ HUD/centre CAN mới có).
2. `ManeuverSignature.kt`: thay dòng gộp `roundabout -> 11` bằng map per-variant theo bảng trên (mirror `w40.java`), trả ra Maneuver hướng tương ứng.
3. `NavRepository.kt`: giữ nguyên logic `24+N` (số lối ra ưu tiên); khi không có số, `toHudIcon()` giờ tự ra 15/17/18/20/22 theo hướng.
4. `IconResource.kt` (small-icon name → AMAP): mirror `mm1.java` nếu muốn phủ đường small-icon (`directions_roundabout_l/r/s/u`).

**Acceptance:** GMaps vòng xuyến rẽ trái → HUD/centre hiện glyph "vòng xuyến ra trái" (CAN 15), rẽ phải → 18, thẳng → 20, có "lối ra thứ 3" → 27 (24+3). KHÔNG còn toàn generic.
**Test:** unit test map từng chữ ký → CAN đúng bảng (khoá như `ManeuverTest`); `NavFormatTest.roundaboutExit` giữ. **Off-car** (unit test đủ; on-car chỉ để mắt xác nhận sau).
**Rủi ro:** map sai hướng tệ hơn generic → test bám ĐÚNG bảng OpenBYD (đã cross-validate on-car) là an toàn.
**On-car:** KHÔNG cần (mapping đã proven qua OpenBYD + catalog).

---

## Task 2 [P1] — Bỏ/relabel selector "chế độ hiển thị cụm" (nút chết)

**Bug:** UI cho chọn Đơn giản/Toàn màn hình/Màn hình nhỏ/OFF nhưng **3 mode layout KHÔNG đổi được** (verify on-car 2026-08-16: `4C10E015`+`4C10A018`+trigger đều không đổi visual — no-root wall). Chỉ **OFF** chạy.

**Chỗ:** `MainActivity.kt:124-135` (spinner) · `Prefs.kt:39-50` (`navClusterScreenMode`) · `NavigationHudOwner.kt:75` (đọc mode) · `BydHal.kt:173-186` (`screenMode`→`4C10E015`).

**Sửa:** rút selector còn **ON/OFF** (bỏ 3 lựa chọn layout), HOẶC giữ nhưng ghi rõ "không đổi được trên trim này". Tránh dead/lying UI.
**Acceptance:** không còn nút layout vô tác dụng. OFF vẫn tắt "Giữa+ETA".
**On-car:** KHÔNG cần (đã verify).

---

## Task 3 [P2] — Ngưng ghi lặp feature không provisioned (dọn log spam)

**Vấn đề:** `BydHal.writeNavFrame` mỗi frame ghi họ **oversea `0x1F7`** + gọi SDK `sendSimpleGuidanceInfo` + dòng `INSTRUMENT_GUIDE_INFO_AND_ROAD_AHEAD` — trên trim owner **rc=-2147482648 + log spam `no permission device 1007`** mỗi frame, vô ích.

**Chỗ:** `BydHal.kt:172-240` (`writeNavFrame`, khối oversea + SDK).

**Sửa:** probe/cache **runtime-rejection per-feature** (feature nào trả -2147482648 lần đầu → cache skip). **PHẢI giữ nguyên trên xe provision được oversea (Sealion 6 → nuôi HUD)** ⇒ skip theo rejection thực tế, KHÔNG hard-remove code oversea.
**Acceptance:** trên trim không provision, sau 1 frame thôi ghi oversea; trên trim có provision, vẫn ghi đủ.
**On-car:** không bắt buộc (logic runtime-detect; verify log sau).

---

## Task 4 [P3 — verify trước] — Prime naviState lúc start/boot cho centre

**Nền:** centre "Giữa+ETA" chỉ render khi **naviState=1** (nav session sống, do broadcast AUTONAVI mở). App đã gửi broadcast mỗi frame nên bình thường OK — nhưng **gốc regression "reboot mất centre"** có thể do frame HAL-centre đầu ghi trước khi broadcast kịp set naviState.

**Chỗ:** `NavRepository.kt:110-140` (createCoordinator, thứ tự emitLane broadcast vs HAL write) · `NavigationHudOwner.start()`.

**Việc:** **đọc lại ordering trước** — nếu có gap, prime 1 broadcast (hoặc đảm bảo emitLane chạy trước/cùng) trước frame HAL-centre đầu ở start/boot.
**Acceptance:** sau boot có GMaps dẫn → centre lên ngay, không cần chờ.
**On-car:** verify nhẹ sau khi sửa (không phải điều kiện làm).

---

## ★ Task 5 [P1] — Nút vật lý chết sau reboot: cho user tắt/bật lại để RESET + xin lại quyền bind key (owner chốt 2026-08-16)

**Bug (owner):** sau **reboot**, giữ nút mic gọi Kiki **KHÔNG được → nhảy vào Bluetooth setting** (= key KHÔNG bind: accessibility ENABLED nhưng NOT BOUND → `onKeyEvent` không chạy → phím rơi về ACTION_ASSIST hệ thống = Bluetooth). App tự xin quyền nhưng **kẹt loop xin 1 quyền fail**; **restart app thì OK lại**.

**Vì sao restart mới hết (đọc `NavConnect.doGrantAccessibility`):** grant chạy dưới **single-flight** `grantingAcc` (AtomicBoolean). Nếu 1 lần grant bị **hang** (dadb session chờ/kẹt ở 1 quyền), cờ giữ `true` → **mọi lần gọi lại (kể cả bật lại công tắc) đều bị "bỏ lần trùng" = no-op** → chỉ **restart app** (reset static state) mới xin lại được.

**YÊU CẦU OWNER (scope gọn — "chỉ vậy thôi"):** cho user **disable → enable** lại chức năng **"Nút vật lý → Trợ lý"**; khi **ENABLE** thì **RESET trạng thái xin quyền + xin lại quyền bind key** (fresh grant + force-rebind), KHÔNG bị single-flight nuốt. ⇒ gặp lại tình huống thì user tự khôi phục, không phải restart app.

**Chỗ:** `MainActivity.kt` `setupVoiceKeyControls()` (switch handler bật/tắt) · `NavConnect.kt` `doGrantAccessibility` (`grantingAcc` single-flight + `forceRebindIfNeeded`) · `Prefs.kt` `K_VK_ENABLED`.

**Sửa:**
1. Toggle "Nút vật lý" **OFF→ON**: **reset** single-flight (đặt `grantingAcc=false`, interrupt grant cũ nếu đang hang) rồi gọi **`grantAccessibility` fresh** (chạy force-rebind lại). Tức thêm 1 đường "grant có RESET" cho path enable (khác path start bình thường vẫn giữ single-flight để không spam).
2. (hỗ trợ, nhỏ) cho dadb session trong grant một **timeout** để không hang vô hạn → single-flight tự nhả kể cả không toggle.
3. KHÔNG auto-loop/backoff — chỉ manual recovery qua toggle như owner yêu cầu.

**Acceptance:** khi kẹt (giữ mic ra Bluetooth), user **tắt→bật "Nút vật lý"** → key bind lại, giữ mic ra **Kiki**, **KHÔNG cần restart app**.
**Test:** unit/wiring — enable path gọi reset+grant (khác start path); off-car.
**On-car:** verify nhẹ sau (không phải điều kiện sửa).

---

## KHÔNG phải code (chỉ doc / đừng đụng)
- **Icon mappings `Maneuver.kt` toHudIcon/toAmapIcon (trừ vòng xuyến ở Task 1):** đã verify đúng on-car → GIỮ NGUYÊN. Chỉ fold kết quả vào `nav-icon-mapping-2026-08-16.html`.
- **HUD kính không lên nav trên xe owner:** là **cờ coding xe `0x38B00030` chưa provisioned** (không phải bug app). App ghi oversea đã đúng; xe khác (Sealion 6) provision → tự lên. **Đừng "fix" oversea trong code.** (Chi tiết: `oncar-session-2026-08-16.md` §4.)

## Nguồn
- Catalog + verdict phiên: `docs/diagnostics/oncar-session-2026-08-16.md`.
- OpenBYD roundabout: `~/Library/Caches/clusternav-re/openbyd-2.3/sources/defpackage/w40.java` (name→CAN), `com/sr/openbyd/services/HudController.java` (hằng CAN), `defpackage/mm1.java` (small-icon→CAN), `com/sr/openbyd/services/YandexManager.java` (Waze exit-number từ text).
- Bảng icon: `docs/diagnostics/re-maneuver-icon-tables-2026-08-14.md` · `nav-icon-mapping-2026-08-16.html`.
