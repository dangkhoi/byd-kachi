# HANDOFF A — CODING (update app) · nguồn: phiên 2026-08-16

> File này ĐỘC LẬP, đủ để session sau code không cần hỏi lại. Cặp với **HANDOFF B — RESEARCH** (`next-session-B-research-hud-2026-08-16.md`).
> Tất cả task **OFF-CAR** (unit test đủ; on-car chỉ verify nhẹ sau). Quy tắc: plan→approve→code→test→senior-review→security-scan (workflow.md).
> Working tree hiện: **1.27** (uncommitted: `Maneuver.kt`, `ManeuverTest.kt`, `NavRepository.kt`, `BydHal.kt`, `build.gradle.kts`, vài doc). APK OTA mới nhất trên `apk/`: 1.27. README ghi "current 1.18" (chưa cập nhật).

## Build / test (macOS, JDK17)
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
./gradlew :core:test :app:testDebugUnitTest :car-integration:test    # unit
./gradlew :app:assembleRelease                                       # build APK
```

## Bối cảnh đã xác nhận on-car 2026-08-16 (dùng cho mọi task icon)
- **Bảng CAN icon vòng xuyến của xe = KHỚP 100% OpenBYD `HudController.java`** (owner soi ảnh sweep + OpenBYD constants trùng khít): `15=RAB_3/4_LEFT · 16=RAB_1/4_LEFT · 17=RAB_3/4_RIGHT · 18=RAB_1/4_RIGHT · 21/22=u-turn · 25..34=CCW lối ra 1..10 · 35..44=CW lối ra 1..10`. CAN 13=vào vòng xuyến (thẳng→móc phải RHT), 14=vào vòng xuyến bẻ trái (CW/LHT), 20=generic/straight/exit CCW, 19=generic CW.
- **Centre "Giữa+ETA" chỉ render khi naviState=1** (nav session sống, do broadcast AUTONAVI mở). Raw HAL inject rc=0 nhưng KHÔNG hiện nếu thiếu session. → liên quan Task "naviState priming".
- **HUD kính không nuôi được trên xe owner** = cờ coding xe `0x38B00030` chưa provisioned (KHÔNG phải bug app — xem HANDOFF B). Đừng "fix" oversea trong code.

---

# ★ TASK 1 — Vòng xuyến: hiện HƯỚNG RA + SỐ LỐI RA (bug user báo dai dẳng)

## Vì sao đây là ưu tiên #1
User **đi đều báo sai**: vòng xuyến **toàn hiện generic "vào vòng xuyến"**, không biết ra lối nào/hướng nào. **ĐÃ sửa qua nhiều version mà VẪN lỗi** — phải hiểu lịch sử để không lặp:
- **1.23**: sửa `ROUNDABOUT.toHudIcon` 15→13 (rồi 2026-08-16 đổi tiếp thành **20** theo OpenBYD; `13/15 méo trên HUD`). → chỉ chỉnh GIÁ TRỊ generic, KHÔNG thêm hướng.
- **1.25**: wire số lối ra `CAN 24+N` (25..34) vào HUD khi text có "lối ra thứ N".
- **1.26**: gọi OEM SDK `sendSimpleGuidanceInfo` (sdk.guide **fail** -2147482645 trên xe nhưng centre vẫn render — SDK không phải điều kiện).
- ⇒ Tất cả các bản trên **vẫn để generic** vì 2 lỗ gốc dưới. Đừng chỉ chỉnh lại con số generic lần nữa — phải xử 2 lỗ:

## 2 lỗ gốc (đọc code)
1. **Hướng ra bị VỨT:** `core/.../navigation/ManeuverSignature.kt:223` — `name.contains("roundabout") -> 11` **gộp MỌI biến thể vòng xuyến về generic (AMAP 11 → Maneuver.ROUNDABOUT → toHudIcon 20)**. GMaps ĐÃ cho hướng qua icon: `ManeuverRegistry.kt` có đủ chữ ký `maneuver_roundabout_enter_and_exit_ccw_{normal,slight,sharp}_{left,right}` + `_straight` + `_u_turn` (cả CW) — nhưng bị collapse.
2. **Số lối ra phụ thuộc TEXT:** `app/.../NavRepository.kt:126-133` chỉ ra `24+N` khi `NavFormat.roundaboutExit(text)` bắt được "lối ra thứ N". GMaps VN **thường không nhét số vào text** → -1 → generic.

## Bảng map authoritative — COPY từ OpenBYD `w40.java` (cross-validate 100% on-car)
Chữ ký icon (ManeuverRegistry name) → CAN. VN dùng CCW là chính; giữ CW cho LHT:
| Chữ ký | CAN | | Chữ ký | CAN |
|---|---|---|---|---|
| `roundabout_enter_ccw` / generic / `_ccw_straight` / `roundabout_exit_ccw` | 20 | | `roundabout_enter_cw` / `_cw_straight` / `roundabout_exit_cw` | 19 |
| `_ccw_normal_left` / `_slight_left` / `_sharp_left` | **15** | | `_cw_normal_left` / `_slight_left` / `_sharp_left` | **16** |
| `_ccw_normal_right` / `_slight_right` / `_sharp_right` | **18** | | `_cw_normal_right` / `_slight_right` / `_sharp_right` | **17** |
| `_ccw_u_turn` | **22** | | `_cw_u_turn` | **21** |

Số lối ra: OpenBYD `YandexManager.java` parse `"exit N"/"Nth exit"` từ text (cả **Waze**) — GIỐNG `NavFormat.roundaboutExit`. **Ưu tiên: có số → 24+N (25..44); không số → dùng hướng (bảng trên); không gì → generic 20.**

## Cách sửa (đề xuất)
1. `core/.../Maneuver.kt`: thêm member hướng — `ROUNDABOUT_LEFT`(HUD 15), `ROUNDABOUT_RIGHT`(18), `ROUNDABOUT_STRAIGHT`(20), `ROUNDABOUT_UTURN`(22). `toAmapIcon` (cụm-strip) giữ 11 (strip không có glyph hướng; chỉ HUD/centre CAN mới có). Cân nhắc CW: 16/17/19/21.
2. `ManeuverSignature.kt`: thay dòng gộp `roundabout -> 11` bằng map per-variant (mirror `w40.java`) → trả Maneuver hướng.
3. `NavRepository.kt:126-133`: giữ ưu tiên số lối ra `24+N`; khi không số, `toHudIcon()` tự ra 15/17/18/20/22 theo hướng.
4. `app/.../IconResource.kt` (small-icon name→AMAP): mirror OpenBYD `mm1.java` nếu muốn phủ đường small-icon (`directions_roundabout_l/r/s/u`, `_uk`).

## Acceptance / Test / Rủi ro
- Accept: GMaps vòng xuyến rẽ trái → HUD hiện CAN 15; rẽ phải → 18; thẳng → 20; "lối ra thứ 3" → 27. HẾT generic.
- Test: unit khoá map từng chữ ký → CAN (kiểu `ManeuverTest`); `NavFormatTest.roundaboutExit` giữ. **Off-car.**
- Rủi ro: map SAI hướng còn tệ hơn generic → test bám ĐÚNG bảng OpenBYD (đã cross-validate on-car).
- Nguồn OpenBYD: `~/Library/Caches/clusternav-re/openbyd-2.3/sources/defpackage/w40.java`, `.../HudController.java`, `.../mm1.java`, `.../YandexManager.java`.

---

# ★ TASK 2 — HUD/centre VẪN NHÁY vài đoạn (dù 1.15 đã "fix")

## Triệu chứng (owner)
Trên đoạn dài không rẽ, HUD/centre "Giữa+ETA" **vẫn chớp/nháy vài đoạn** — DÙ **1.15 đã thêm HUD keep-alive** (heartbeat 400ms re-assert last frame) để chống blank ~1s. Tức fix 1.15 CHƯA đủ.

## Cơ chế hiện tại (đọc để biết chỗ nghi)
- `app/.../NavigationHudOwner.kt`: keep-alive scheduler (`HudKeepAlivePolicy`), dedup theo **applied-state** (icon/seg/road), re-assert bypass dedup khi stale ≥ interval, clear khi quá **maxAge 180s**. Có "Lỗ 1/2/3" (handoff 2026-08-15): realPush vs KEEPALIVE_SESSION, re-arm nhịp sau stop() theo-tuyến.
- `core/.../navigation/HudKeepAlivePolicy.kt`: interval + maxAge + shouldReassert/shouldClear.
- `core/.../navigation/ManeuverHold.kt` (1.24): giữ hướng khi 1 frame không phân loại được (chống nháy do lỗi đọc icon).
- Two-track: `NavRepository.navOnlyMode()` — chỉ ghi centre khi Cast master OFF.

## Hướng điều tra (CHƯA chẩn root — session sau đo)
Ứng viên (cần logcat `NavigationHudOwner` timing + owner tả đoạn nào nháy):
1. **Interval keep-alive vs timeout OEM**: 400ms có thực sự đủ nhanh? Đo khoảng cách giữa 2 lần write thực (log timestamp). Nếu jitter > timeout OEM → nháy.
2. **naviState drop**: nếu giữa 2 frame guidance, session (naviState) rớt (broadcast thưa) → centre gate off 1 nhịp → nháy. (Phát hiện 2026-08-16: centre cần naviState=1.) Kiểm broadcast heartbeat có đều với HAL heartbeat không.
3. **Dedup nuốt re-assert**: applied-state dedup có thể nuốt nhịp khi giá trị trùng, để OEM tự timeout giữa chừng.
4. **maxAge/clear**: `shouldClear` (180s) có bắn nhầm giữa route dài không rẽ?
5. **Cast gating**: `navOnlyMode` lật khi Cast state đổi → ngừng ghi centre 1 đoạn.

## Việc session sau
- Bật logcat `NavigationHudOwner` + `NavRebind` + AmapService khi đi đoạn dài; đo interval write thực; xác định ứng viên nào. Rồi sửa (vd: hạ interval, hoặc re-assert cả broadcast+HAL đồng bộ, hoặc tách naviState keep-alive riêng).
- **KHÔNG đoán fix** — đo trước (đây là bug timing, phải có số).

---

# ★ TASK 3 — Nút vật lý chết sau reboot: tắt/bật lại để RESET + xin lại quyền bind key

## Bug (owner chốt 2026-08-16)
Sau **reboot**, giữ nút mic gọi Kiki KHÔNG được → **nhảy Bluetooth setting** (= key không bind: accessibility ENABLED nhưng NOT BOUND → `onKeyEvent` không chạy → phím rơi về ACTION_ASSIST hệ thống). App tự xin quyền nhưng **kẹt** (loop xin 1 quyền fail); **restart app thì OK lại**.

## Vì sao restart mới hết (đọc `NavConnect.doGrantAccessibility`)
Grant chạy dưới **single-flight** `grantingAcc` (AtomicBoolean). Nếu 1 lần grant **hang** (dadb session chờ/kẹt), cờ giữ `true` → **mọi lần gọi lại (kể cả bật lại công tắc) bị "bỏ lần trùng" = no-op** → chỉ **restart app** (reset static) mới xin lại được.

## YÊU CẦU OWNER (scope gọn — "chỉ vậy thôi")
Cho user **disable → enable** lại chức năng **"Nút vật lý → Trợ lý"**; khi **ENABLE** thì **RESET trạng thái xin quyền + xin lại quyền bind key** (fresh grant + force-rebind), KHÔNG bị single-flight nuốt → gặp lại tình huống thì user tự khôi phục, không phải restart app. **KHÔNG auto-loop/backoff.**

## Chỗ + cách sửa
- `app/.../MainActivity.kt` `setupVoiceKeyControls()` (switch handler) · `app/.../NavConnect.kt` `doGrantAccessibility` (`grantingAcc` + `forceRebindIfNeeded`) · `Prefs.kt` `K_VK_ENABLED`.
1. Toggle "Nút vật lý" **OFF→ON**: reset single-flight (`grantingAcc=false` + interrupt/timeout grant cũ nếu hang) rồi gọi `grantAccessibility` fresh (chạy force-rebind lại). Thêm đường "grant có RESET" cho path enable (khác path start bình thường vẫn giữ single-flight).
2. (hỗ trợ) cho dadb session trong grant **timeout** để không hang vô hạn → single-flight tự nhả.
- Accept: kẹt (giữ mic ra Bluetooth) → user tắt→bật "Nút vật lý" → key bind lại, ra **Kiki**, KHÔNG cần restart app. Test wiring off-car.
- Nền: `docs/diagnostics/oncar-handoff-voicekey-2026-08-14.md` §8 (root enabled-but-not-bound); `AccessibilityRebind.kt` + `AccessibilityRebindTest`.

---

# TASK 4 — Bỏ/relabel selector "chế độ hiển thị cụm" (nút chết)
3 mode layout (Đơn giản/Toàn/Nhỏ) **không đổi được layout live** (verify on-car 2026-08-16: no-root wall — `4C10E015`+`4C10A018`+trigger đều không đổi visual). Chỉ **OFF** chạy. → rút còn ON/OFF hoặc ghi rõ "không đổi được trên trim này". Chỗ: `MainActivity` spinner + `Prefs.navClusterScreenMode` + `BydHal.screenMode`. (Chi tiết verdict: `oncar-runbook-4mode-track-a-probes-2026-08-14.md §EXECUTED 2026-08-16`.)

# TASK 5 — Ngưng ghi lặp feature không provisioned (dọn log spam)
`BydHal.writeNavFrame` mỗi frame ghi oversea `0x1F7` + SDK `sendSimpleGuidanceInfo` + dòng `INSTRUMENT_GUIDE_INFO_AND_ROAD_AHEAD` → trên trim owner rc=-2147482648 + spam `no permission device 1007`. Sửa: cache **runtime-rejection per-feature**, skip sau lần đầu; **PHẢI giữ trên xe provision được oversea (Sealion 6)** → skip theo rejection thực tế, KHÔNG hard-remove. Chỗ: `BydHal.kt:172-240`.

# TASK 6 [verify trước] — Prime naviState lúc boot cho centre
Centre chỉ render khi naviState=1. App đã gửi broadcast mỗi frame nhưng "reboot mất centre" có thể do frame HAL đầu ghi trước khi broadcast set naviState. **Đọc ordering `NavRepository.kt:110-140` trước**; nếu có gap → prime 1 broadcast trước/cùng frame HAL đầu ở start/boot.

---

# KHÔNG phải code (đừng đụng)
- Icon mappings KHÁC vòng xuyến (`Maneuver.kt` toHudIcon/toAmapIcon): đã verify đúng on-car → giữ. Chỉ fold vào `nav-icon-mapping-2026-08-16.html`.
- HUD kính không lên trên xe owner: cờ coding xe `0x38B00030` (xem HANDOFF B) — KHÔNG phải bug app. App ghi oversea đã đúng.

# Nguồn
- Handoff phiên: `docs/diagnostics/oncar-session-2026-08-16.md`.
- Icon: `nav-icon-mapping-2026-08-16.html` · `re-maneuver-icon-tables-2026-08-14.md` · `nav-output-architecture-2026-08-16.html`.
- Voice-key: `oncar-handoff-voicekey-2026-08-14.md`.
- OpenBYD RE: `~/Library/Caches/clusternav-re/openbyd-2.3/sources/`.
