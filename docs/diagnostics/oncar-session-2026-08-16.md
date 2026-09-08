# ON-CAR SESSION HANDOFF — 2026-08-16 (xe owner: BYD Seal · DiLink3.0 · region ROW · 40d=138)

> **Trạng thái**: Current · **Cập nhật**: 2026-08-16 · **Mục đích**: Tổng hợp + TODO phiên on-car 2026-08-16 (catalog icon, nav-on-cluster).

> Parked-only · IP redact `<vehicle-ip>` · VIN redact `<vin>` · KHÔNG assume — mỗi claim trace `[readback]`/`[owner]`/`[RE:doc]`.
> Phiên dài, nhiều nhánh. Đây là bản tổng hợp + TODO. Chi tiết 4-mode xem `oncar-runbook-4mode-track-a-probes-2026-08-14.md` §EXECUTED 2026-08-16.

---

## 0. TL;DR — làm được gì

| Việc | Kết quả |
|---|---|
| Catalog 60 ảnh icon sweep (HUD/cụm) | ✅ `icon maps/nav-icon-sweep-catalog.html`; owner xác nhận toàn bộ nhóm vòng xuyến |
| **CAN 14** (icon cuối chưa có) | ✅ **xác nhận on-car**: vào vòng xuyến bẻ TRÁI = cho nước lái bên trái (CW/LHT), gương mã 13 |
| **Cơ chế naviState** (phát hiện lớn) | ✅ centre "Giữa+ETA" chỉ render khi **naviState=1** (nav session sống) — giải thích regression "reboot mất centre" |
| Probe 4-mode layout (P0/P1/P2a/P2b/P3) | ✅ Verdict: **KHÔNG switch live từ Android nếu không root**; content điều khiển 100% |
| **HUD kính không lên nav** (câu hỏi lâu năm) | ✅ **root-cause = cờ coding `0x38B00030` chưa provisioned** (không phải phần cứng, không phải app) |

---

## 1. Icon GMaps → HUD/cụm (catalog) — ĐÃ VERIFY

Owner chạy `scripts/vehicle/nav-icon-sweep.sh` (dist=code, mỗi ảnh tự định danh qua nhãn "Nm"). 60 ảnh đã catalog + owner soi từng glyph:

- **Vòng xuyến số lối ra ĐỦ 2 họ** (khớp 100% công thức RE `toHudIcon`): CCW `CAN 25–34 = lối ra 1–10`; CW `CAN 35–44 = lối ra 1–10`.
- **Biến thể vòng xuyến 13–24** owner mô tả glyph thật (vào/ra + bo hướng + góc):
  - 13 = vào vòng xuyến (thẳng→móc phải, RHT) · **14 = vào vòng xuyến bẻ trái (CW/LHT)** · 15/16 = ra trái 90° · 17 = (đường dài) ra phải 90° · 18 = ra phải 90° ngay · 19/20/23/24 = ra thẳng · 21/22 = quay ngược (~U-turn).
- Mã cao render thật: 45 waypoint · 46 service · 47 toll · 48 đích · 49 hầm.
- Turns 1–10 hiện `现在` (cự ly ≤10m → HUD thay số bằng "ngay bây giờ").

**Ý nghĩa cho code:** các mapping icon trong `core/.../navigation/Maneuver.kt` (`toHudIcon`/`toAmapIcon`) + wire CAN 24+N (bản 1.25) **được xác nhận đúng on-car**. Fix 1.23 (ROUNDABOUT 15→13) đúng: mã 15 render "vòng xuyến RỒI rẽ trái" (ép lối trái), 13 mới là "vào vòng xuyến" trung tính.

**TODO (icon):**
- [ ] Fold kết quả đã xác nhận vào `docs/diagnostics/nav-icon-mapping-2026-08-16.html` — điền cột "Kết quả/ngày" cho các dòng on-car (số lối ra 25–44, waypoint/service/toll/tunnel 45–49, ROUNDABOUT=13, CONTINUE=12, biến thể 13–24).
- [ ] (tùy chọn) cân nhắc map GMaps "roundabout + turn left/right/straight" → CAN 15–20 thay vì chỉ 13, nếu GMaps expose (cần P-ICON-B: đọc CSV lúc lái qua vòng xuyến — CHƯA chạy).

---

## 2. ★ Phát hiện lớn: naviState gate (centre "Giữa+ETA")

Trace bằng logcat đường app relay GMaps (đang chạy ngon):
- Centre "Giữa+ETA" (HAL `43F01010`) **chỉ render khi có nav session sống** — logcat `AmapService: GuideInfo.naviState: 1`. `[readback]`
- Raw inject qua navopen (`frame`, đủ icon+dist+road+eta, kể cả screen=1) trả **rc=0 nhưng KHÔNG hiện** khi thiếu session. Gửi **AMAP broadcast** `AUTONAVI_STANDARD_BROADCAST_SEND … --ei EXTRA_STATE 1` → mở session (naviState=1) → content hiện ngay. `[owner]`
- **Content control = 100%**: đổi NEW_ICON/road/dist/ETA qua broadcast → cụm phản ánh đúng (verify: rẽ-phải/Le Loi → U-turn/NGUYEN TRAI/555m). `[owner]`

**Ý nghĩa:** boot/không-session = centre trống → đây là gốc regression **"reboot mất centre"**.
**TODO:** đảm bảo app **tái lập naviState** (đẩy 1 broadcast / re-assert session) lúc boot headless + lúc bật Nav+HUD, để centre lên ổn định thay vì chờ GMaps.

---

## 3. Probe 4-mode layout — VERDICT no-root

Chi tiết đầy đủ: `oncar-runbook-4mode-track-a-probes-2026-08-14.md` §EXECUTED 2026-08-16. Tóm tắt:
- `4C10A018` (INSTRUMENT_NAVI_TYPE_SET): write **rc=0** (provisioned) nhưng **layout thị giác KHÔNG đổi** (v=3/4 vẫn EASY). `40C03032` nhúc nhích 2→3→0 nhưng visual bất động.
- `4C130041` (NAVIGATION_STYLE): **reject** mọi giá trị (dead).
- `ac 5 0` (AutoContainer): đẩy state nhưng visual vẫn EASY; `ac2 4` cần flatbuffer-hex navopen không dump được.
- ⇒ **Không switch 4-mode live từ Android nếu không root** (layout do cluster-side/fission quyết). Restore `4C10A018=2` (boot EASY).
**TODO:** bỏ theo đuổi switch FULL/SMALL live; chuyển sang giữ "Giữa+ETA ổn định" (mục 2).

---

## 4. ★ HUD KÍNH KHÔNG LÊN NAV (root-cause) — quan trọng nhất phiên

**Bối cảnh:** owner + 1 xe **BYD Sealion 6** trong hội, HUD **mua Taobao (HUD Trung Quốc)** gắn thêm. Trên Sealion 6 app lên HUD nav ngon; trên Seal của owner HUD không lên. Trước nghĩ do phần cứng — **SAI** (HUD cùng dòng, hiển thị y chang).

**Root-cause (RE 2026-08-10 §10 + verify 2026-08-16):**
- `INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG = 0x38B00030` = cờ provisioning cho **cụm mirror nav → HUD kính**. Consumer `Hud…readSelfLearnState()`: bật khi **config == 1**. `[RE: native decompile libBydCluster]`
- **Xe owner đọc `38B00030 = -2147482648` (KHÔNG provisioned)** + `38B0002E` (status) cũng không provisioned. `[readback 2026-08-16]`
- HUD chung thì bật: `38B00015=1` (W-mode), `38B0001C=1` (switch on), `38B00028=1` (nav-content toggle on), `38B0001E=1` (adas). ⇒ nghịch lý "toggle nav-content ON mà HUD không nav" = do **cờ mirror 38B00030 chưa provisioned** (toggle vô nghĩa).
- Họ oversea `0x1F7*` (1F701010/018/704010/A1008/…) **rejected hết** — `no permission … with this device: 1007`. dualIcon domestic `43F01030` cũng rejected. Chỉ `43F01010/018` (nuôi cụm) provisioned. `[readback]`
- Device codes app dùng: **1007 / 1023 / 1038 / 1014**.

⇒ **Đây là chênh lệch VARIANT CODING của XE, không phải HUD, không phải app.** App đã ghi đủ (cả domestic + oversea); trên xe code đúng cờ (Sealion 6) nav **tự mirror ra HUD**. Xe owner thiếu cờ.

**Sửa được không:** `38B00030` **write bị reject**; self-learn chỉ mirror MCU state vào cache đọc; firmware không có app ghi MCU coding. → **Chỉ set được qua coding tool BYD ngoài (OBD → instrument ECU variant coding, UDS `WriteDataByIdentifier`)**, KHÔNG qua adb/no-root. Set `0x38B00030=1` → nav sẵn có tự lên HUD.

### 4.1 So sánh với Sealion 6 — kit đã chuẩn bị
- Tool: `scripts/vehicle/hud-provisioning-compare.sh` (read-only) + `apks/navopen-v4.jar`.
- Bạn dùng **Bugjaeger gõ tay** (không laptop). Lệnh tối thiểu (khi HUD đang hiện nav):
  1. `logcat -c`
  2. (đợi ~10s) `logcat -d -s AbsBYDAutoDevice BYDAutoInstrumentDevice NavigationHudOwner` → tìm `set featureId is 1f7…` có kèm `no permission` không.
  3. `getprop persist.sys.vehicle_40d_code` · `getprop ro.build.region` · `getprop persist.sys.byd.bluetooth_name`
  4. (tùy chọn, đẩy jar) `CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen getraw instr 38B00030`
- **Kỳ vọng chốt:** Sealion 6 `38B00030=1` (hoặc oversea provisioned) trong khi Seal owner không → xác nhận 100% chênh ở cờ coding.

**TODO (HUD):**
- [ ] Nhận data Sealion 6 → dựng bảng so sánh 2 xe, chốt cờ.
- [ ] Nếu chốt cờ: tìm coding tool BYD (OBD/UDS) set `38B00030=1` cho xe owner. Không có tool → line này đóng cho hobby.
- [ ] **KHÔNG "fix" oversea write trong app** — nó đúng rồi, chỉ bị xe owner reject. Ghi rõ để lần sau khỏi tưởng bug.

---

## 5. TODO — Nút vật lý (carry-over, KHÔNG đụng phiên nay)

> Phiên hôm nay KHÔNG probe nút vật lý — mục này là carry-over từ context/spec, cần owner xác nhận scope.
- [ ] **Voice-key sau reboot** (session-plan-2026-08-15 §S0.5): reboot xoá `enabled_accessibility_services` → mic→Kiki/Gemini có thể chết (enabled-but-not-bound). Cần **force-bind accessibility headless** lúc boot (BootSetupService) — mô tả là "chưa code" trong session-plan. Xác nhận đã có trong 1.18 chưa (README 1.18 nói "accessibility booster self-grant on Nav+HUD" — kiểm tra có phủ cả voice-key path không).
- [ ] Xác nhận on-car mapping **nút mic giữ (keycode 328) → Kiki** + short-press → 小迪 (README 1.18, "đang confirm on-car").
- [ ] Gemini path (README 1.17): mở app `com.google.android.apps.bard` — confirm auto-listen on-car.
- (Chi tiết fix cụ thể: theo spec `docs/specs/notif-grant-docs-voicekey-1.13.html` + README changelog — owner bổ sung scope nếu cần sửa mới.)

---

## 6. Artifacts phiên này
- `icon maps/nav-icon-sweep-catalog.html` — catalog 60 ảnh (ngoài repo, cạnh folder `icon/`).
- `icon maps/_catalog-notes.tsv` — scratch data catalog (rebuild source).
- `scripts/vehicle/hud-provisioning-compare.sh` — script so sánh HUD provisioning 2 xe (mới).
- `apks/navopen-v4.jar` — tool getraw/setraw (pull từ xe owner).
- `docs/diagnostics/oncar-runbook-4mode-track-a-probes-2026-08-14.md` §EXECUTED 2026-08-16 — kết quả 4-mode.
- `docs/diagnostics/oncar-session-plan-2026-08-15.md` §S2 — note đã chạy.

## 7. Trạng thái xe khi kết phiên
- `4C10A018=2` (config-53 → boot EASY). `40C03032` đọc 0 (state nội bộ, KHÔNG mirror visual — visual vẫn EASY). navopen.jar để lại `/data/local/tmp/` (vô hại).
- **Khuyến nghị: power-cycle nút nguồn** cho sạch (Android write không reset được `40C03032`).

## 8. Nguồn
- HUD coding flag: `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §10.
- Kiến trúc 2 đường + bảng domestic/oversea: `docs/diagnostics/nav-output-architecture-2026-08-16.html`.
- Icon tables: `docs/diagnostics/re-maneuver-icon-tables-2026-08-14.md` · mapping: `docs/diagnostics/nav-icon-mapping-2026-08-16.html`.
- 4-mode cơ chế: `docs/diagnostics/re-4mode-amap-layout-mechanism-2026-08-14.md`.
