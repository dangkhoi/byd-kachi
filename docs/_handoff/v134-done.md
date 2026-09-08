# v1.34 — sửa 3 lỗi on-car v1.33 (task `v134-fixes`)

> **Trạng thái**: off-car DONE (build xanh + full test 5 module 0 fail) · **Ngày**: 2026-09-06 · CHỜ test trên xe.
> Off-car baseline: v1.33 = commit `e37735a` (parent `a79eb57` = v1.32). KHÔNG commit / KHÔNG bump version (chờ owner test xe).

## Tóm tắt

| # | Lỗi on-car v1.33 | Sửa | Bằng chứng |
|---|---|---|---|
| 1 | Ghế chỉnh về **Tắt** không tắt (mức 1/2 chạy rc=0) | Đường chạm ghế ghi HAL **state=1 (Tắt)** thay vì bỏ qua | `applySeat` gửi `stateForLevel(level)`; test 7/0 |
| 2 | v1.33 thu 70% **cả chữ** ⇒ chữ quá nhỏ, khó đọc | Chữ về gốc v1.32; **chỉ giảm ~70% chiều DỌC** | seal re-pin lan 25; parity 87; test 2/0 |
| 3 | Mở ClusterNav **relaunch VietMap** dù đã mở (bóng không lên) | Bỏ launch nếu VietMap **đã có bản ghi activity** | `hasActivityRecord`; test 10/0 |

**Full test**: `./gradlew test --rerun-tasks --continue -Dorg.gradle.parallel=false` ⇒ **1940 test, 0 fail, 0 err** (app 966 · core 809 · offcar-planner 99 · car-integration 44 · vehicle-contracts 22). **Build**: `./gradlew :app:assembleDebug -q` ⇒ exit 0.

---

## FIX 1 — Ghế "Tắt" không tắt được

**Gốc**: đường ghi HAL bulk `SeatComfortApplier.apply()` (dùng cho `applyOnStart`/`applyNow`) có `if (level == LEVEL_OFF) continue` ⇒ **KHÔNG BAO GIỜ** gửi state=1 (Tắt). Chạm ghế về Tắt trước đây gọi `applyNow` → `apply()` → bỏ qua ghế Tắt ⇒ ghế không tắt. (Đường bulk cố ý bỏ Tắt là ĐÚNG cho lúc khởi động: không cưỡng bức tắt mọi ghế.)

**Đổi**:
- `app/.../comfort/SeatComfortApplier.kt` — thêm `fun applySeat(ctx, seatIndex, level)`: ghi ĐÚNG 1 ghế với `state = SeatComfort.stateForLevel(level)` (0→1 Tắt · 1→2 · 2→3) qua `BydHal.callNamedInt(dev, methodFor(mode), seatId, state)` trên thread nền, degrade-safe (dev null off-car → log + thoát), Log tag `SeatComfort`. Tách helper `currentMode(app)` dùng chung; `apply()` (bulk) GIỮ nguyên `continue` qua Tắt.
- `app/.../MainActivity.kt` — `seat_diagram.onSeatLevelChanged` gọi `SeatComfortApplier.applySeat(this, seat, level)` (thay `applyNow`). Đổi CHẾ ĐỘ (seg_seat_mode) vẫn dùng `applyNow` (áp lại mọi ghế ≠ Tắt cho mode mới — đúng).
- `core/.../comfort/SeatComfortTest.kt` — thêm test `OFF level is a sendable HAL turn-off command, never a skip sentinel` (khoá: `stateForLevel(0)==STATE_OFF==1`; mọi mức 0/1/2 → state 1/2/3 sendable).

**Grep**: `SeatComfortApplier.kt:68  val state = SeatComfort.stateForLevel(level) // 0→1(Tắt)…` · `MainActivity.kt:1240 SeatComfortApplier.applySeat(...)`.

---

## FIX 2 — Trả cỡ chữ về gốc, chỉ giảm ~70% chiều cao

**Gốc**: v1.33 (`stage2-ui · T2`) scale MỌI dp/sp ×0.7 ⇒ owner báo chữ quá nhỏ trên màn xe.

**Nguyên tắc**: tái sinh layout từ nền **v1.32** rồi scale ×0.7 **CHỈ thuộc tính DỌC**. Cỡ chữ (sp), dp NGANG (rộng · paddingStart/End/Left/Right · marginStart/End · drawablePadding), padding tất-cả-cạnh, và cỡ icon/dot **vuông** → giữ nguyên v1.32 (khôi phục). Dọc (paddingTop/Bottom · marginTop/Bottom · minHeight-dp · layout_height-dp phần tử KHÔNG vuông) → giữ ×0.7. 4 custom view gauge/dial/sơ-đồ/preview → scale CẢ rộng+cao (giữ vuông; thuộc "chiều cao custom view → giữ 70%").

**Đổi** (KHÔNG đụng @+id nào ⇒ parity 87 giữ; KHÔNG đụng `strings.xml`; GIỮ fix viền-2-nét của v1.33 ở drawable):
- `res/values/dimens.xml` — text về gốc (`text_title 24sp` · `text_status 16sp` · `text_body 15sp` · `text_label 13sp`), `card_pad 16dp` · `screen_pad 20dp` · `radius 16dp` về gốc; GIỮ `gap 10dp` (khoảng-cách-thẻ DỌC) + `touch_min 40dp` (min-height DỌC, sàn tap 40dp).
- `res/values/styles.xml` — text size literal về gốc (Row.Subtitle 12sp · HeroLead 12sp · HeroDist 34sp · HeroStreet 14sp · Pill 11sp); padding NGANG về gốc (Button 18dp · Compact 14dp · Row 16dp · Spinner 14/36 · SegmentButton minWidth 64 + 16dp · Pill 10dp · StatusPill 12dp · Switch switchPadding 10dp); IconTile về 34dp; elevation về gốc (Group 10 · HeroCard 14). GIỮ DỌC ×0.7 (minHeight 27/41 · paddingTop/Bottom 5/9/… · Row.Subtitle marginTop 1). HeroCard: tách `padding` → paddingLeft/Right 18 (gốc) + paddingTop/Bottom 13 (giữ).
- `res/layout/activity_main.xml` + `res/layout-w960dp/activity_main.xml` — tái sinh từ v1.32, scale DỌC ×0.7 (xác minh: diff-vs-v1.32 chỉ giảm DỌC + 2 gauge vuông; diff-vs-v1.33 chỉ KHÔI PHỤC ngang/icon, KHÔNG lệch giá trị DỌC).
- Custom view: `SegmentedControlView.kt` (text 12.5f gốc · padding ngang 13f/3f gốc · dọc 4f/2f giữ) · `ClusterPreviewView.kt` (radius 12f + text cap 11f gốc). `SpeedDialView`/`Pm25GaugeView`/`SeatDiagramView` GIỮ ×0.7 (chiều cao custom view).

**Seal (T11 narrow) re-pin lan 25**:
- File: `app/src/main/res/layout/activity_main.xml`
- Hash cũ (lan 24): `1a7c90f7e499909a7e8b3ad577c024eeec3421bafa37c19b574758abd636f921`
- **Hash mới (lan 25)**: `046d23b05bbbcc702990b3220bbc95aa0eb0b21a92d7d9f960f3c02b03fd8d98`
- `offcar-planner/.../ExpansionTransportFenceTest.kt` — T11_HASHES cập nhật + KDoc "lần 25" (không weaken/skip). Bản rộng `layout-w960dp` không pin (chỉ sửa theo cùng nguyên tắc; parity giữ).

---

## FIX 3 — VietMap autostart relaunch dù đã mở (nhánh bóng)

**Gốc**: nhánh silent-bg `if (bubbleOn || !running) { launch … }` — `bubbleOn=true` ⇒ **LUÔN** relaunch dù VietMap đang chạy + activity đã dựng. Guard `foreground` cũ chỉ bỏ khi VietMap **RESUMED**, mà mở ClusterNav thì ClusterNav mới resumed ⇒ VietMap không resumed ⇒ vẫn relaunch (flash, bóng không lên).

**Đổi**:
- `app/.../VietMapAutostart.kt` — thêm PURE `hasActivityRecord(dumpsysActivitiesGrep, pkg)`: từ output `dumpsys activity activities | grep <pkg>`, có tham chiếu component `pkg/…` HOẶC dòng `ActivityRecord`/`Task{`/`Hist ` kèm pkg ⇒ CÓ bản ghi activity (bóng đã init). Nhánh silent-bg: `hasActivity = running && runCatching { hasActivityRecord(sh("dumpsys activity activities | grep -E '$PKG'").output) }.getOrDefault(false)`; nếu `bubbleOn && hasActivity` ⇒ **bỏ launch** (log); ngược lại giữ `bubbleOn || !running` → launch cũ. GIỮ cooldown 30s + inFlight + castDefault. Degrade-safe: đọc dumpsys lỗi ⇒ `hasActivity=false` ⇒ rơi về hành vi cũ.
- `app/.../VietMapAutostartGateTest.kt` — thêm 5 test `hasActivityRecord` (rỗng→false · ActivityRecord/realActivity/Task→true · mention trần→false · default PKG).

**Grep**: `VietMapAutostart.kt:53 fun hasActivityRecord(...)` · `:160 hasActivityRecord(sh("dumpsys activity activities | grep -E '$PKG'")…)` · `:162 if (bubbleOn && hasActivity)`.

---

## CHỜ TEST XE
1. **Ghế**: chỉnh 1 ghế về **Tắt** → ghế tắt thật (logcat `-s SeatComfort` thấy `state=1`). Mức 1/2 vẫn chạy.
2. **UI**: chữ đọc được (cỡ như v1.32), layout ngắn hơn v1.32 trên màn xe; viền thẻ không bị 2 nét.
3. **VietMap bóng**: mở ClusterNav khi VietMap đang mở → KHÔNG relaunch VietMap; bóng lên cụm (logcat `-s VietMapAutostart` thấy "đã có bản ghi activity — bỏ launch").
