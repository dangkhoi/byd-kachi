# ON-CAR HANDOFF — screenRead ground-truth fix + chuyến lái về (tinh chỉnh nội suy) · 2026-08-14 PM

> Xe: BYD Seal DiLink 3.0, Android 10 (API 29), KHÔNG root. Chủ: Đăng Khôi (`dangkhoi`).
> **HỎI LẠI IP** mỗi phiên (hotspot đổi): `export VEH=<vehicle-ip>:5555`. ĐỪNG đoán IP.
> Nguyên tắc: KHÔNG assume — mọi claim trace về nguồn (log/readback). Xem `.kiro/steering/no-assumptions.md`.
> Thread này = **tinh chỉnh nội suy cự-ly-tới-rẽ (FACTOR)**, TÁCH khỏi regression "Giữa+ETA" ở `oncar-handoff-2026-08-14.md`.

---

## 0. TL;DR
- Mục tiêu: chỉnh **FACTOR** (nội suy cự ly tới rẽ) từ **dữ liệu lái thật**, cần cột ground-truth **`screenRead_m`** (cự ly GMaps đọc trên màn qua accessibility).
- Sáng nay commute về **rỗng `screenRead_m`** (0 fresh) — **lần thứ 2 liên tiếp**. Gốc: quyền accessibility bị reboot xoá + app không tự cấp lại được.
- **ĐÃ**: cấp lại quyền live trên xe → verify screenRead chảy (185 fresh rows, =210 khớp GMaps). **ĐÃ**: vá code để tự-cấp (chưa ship). **CHƯA**: có data FACTOR (đỗ yên → mean 0).
- **CHIỀU**: lái về với Nav+HUD ON + GMaps foreground → có data FACTOR. **ĐỪNG power-cycle xe trước khi lái** (quyền live mất khi reboot; bản 1.17 trên xe chưa tự-lành).

---

## 1. ĐÃ LÀM SÁNG NAY (2026-08-14 AM) — evidence

### 1.1 Commute sáng → KHÔNG dùng được để chỉnh FACTOR
- Pull `nav_log_1786668422023.csv` (7810 dòng, commute sáng) → analyzer: **`fresh rows: 0`**. Cột `screenRead_m` toàn `-1`. [on-car pull + `analyze-nav-distance-log.py`]
- ⇒ không có `projected − screen` → **FACTOR giữ nguyên 0.95**, chưa chỉnh.

### 1.2 Gốc rễ (readback trên xe, KHÔNG đoán)
- `settings get secure enabled_accessibility_services` = `com.byd.vrassistant.xf/…:com.android.systemui/…` — **THIẾU** `com.byd.clusternav/…NavAccessibilityService`. `accessibility_enabled=1` nhưng service không có trong list → **không bound** (`dumpsys accessibility` chỉ thấy StatusBar). [on-car readback]
- GMaps đúng package `com.google.android.apps.maps` (reader chỉ match package này + revanced) → KHÔNG phải lỗi package. [on-car `pm list packages`]
- App **không có đường tự cấp lại**: `NavConnect.grantAccessibility` tồn tại nhưng **0 caller** (mồ côi từ khi gỡ UI voice-key). [grep `app/src/main`]
- ⇒ Reboot (state reset) xoá quyền → screenRead rỗng cả 2 chuyến. Đây là root cause, không phải lỗi interp.

### 1.3 Fix LIVE trên xe (đã chạy)
Append service (giữ nguyên service BYD), bật accessibility:
```bash
ACC="com.byd.clusternav/com.byd.clusternav.modules.navaccess.NavAccessibilityService"
CUR=$(adb -s "$VEH" shell 'settings get secure enabled_accessibility_services' | tr -d '\r')
case "$CUR" in *"$ACC"*) : ;; null|"") adb -s "$VEH" shell "settings put secure enabled_accessibility_services '$ACC'";; *) adb -s "$VEH" shell "settings put secure enabled_accessibility_services '$CUR:$ACC'";; esac
adb -s "$VEH" shell settings put secure accessibility_enabled 1
```
- Verify: `dumpsys accessibility` → `Service[label=ClusterNav — booster…]` trong **Enabled services**; logcat `NavAccess: accessibility booster connected`. [on-car]
- ⚠️ Quyền này **chỉ sống tới lần reboot sau** (secure setting, mất khi state-reset/power-cycle).

### 1.4 Verify screenRead chảy end-to-end (đỗ yên, GMaps dẫn)
- Tail log real-time: `screenRead_m = 210`, `age` 76–1139 ms (tươi), road "Tân Phú". [on-car tail]
- Analyzer: **`fresh rows: 185`** (sáng = 0). Đỗ yên → raw=proj=disp=screen=210 → mean 0 (chưa có tín hiệu tuning, nhưng **plumbing OK**).

### 1.5 Vá code (đã verify off-car, CHƯA commit/push)
- `app/.../MainActivity.kt`: tự cấp accessibility qua dadb **khi bật Nav+HUD** và **khi mở app lúc Nav+HUD đã bật** — chỉ khi thiếu (đọc secure setting local trước, không mở dadb thừa). Mirror pattern notification `selfGrant`.
  - Thêm helper `accessibilityBoosterGranted()` (đọc `enabled_accessibility_services` local).
  - 2 call-site: nhánh `if (enabled)` của công tắc + startup `if (Prefs.enabled(this))`.
- `app/src/test/.../NavCastUiWiringContractTest.kt`: thêm test `nav+HUD self-grants the accessibility booster…` khoá wiring (chống mồ côi lại).
- Verify: `compileReleaseKotlin` OK · `testDebugUnitTest --tests NavCastUiWiringContractTest` = **12/12 pass**. [gradle]
- **Chưa** bump version, **chưa** build APK, **chưa** commit/push.

---

## 2. VIỆC CHO CHIỀU LÁI VỀ (theo thứ tự)

### 2.1 TRƯỚC KHI LÁI (30s)
- [ ] **ĐỪNG power-cycle xe** (giữ quyền accessibility live). Nếu lỡ reboot → chạy lại lệnh §1.3 rồi mới đi.
- [ ] Mở **ClusterNav** → gạt **Nav+HUD ON** (mặc định OFF; đây là gate cho cả log lẫn screenRead) · **Cast OFF**.
- [ ] Mở **Google Maps** dẫn về → **để GMaps hiện trên màn IVI** suốt chuyến (bị app khác che → booster câm).

### 2.2 (Tùy chọn) PRE-CHECK 20s lúc còn đỗ — chắc ăn không phí chuyến
```bash
export VEH=<vehicle-ip>:5555
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$VEH"
# để GMaps dẫn ~1 phút rồi:
NAVLOG=$("$ADB" -s "$VEH" shell 'ls -t /sdcard/Android/data/com.byd.clusternav/files/nav_log_*.csv | head -1' | tr -d '\r')
"$ADB" -s "$VEH" pull "$NAVLOG" ./precheck.csv
python3 scripts/analyze-nav-distance-log.py ./precheck.csv | sed -n '1,10p'
```
→ thấy **`fresh rows: N>0`** = OK, lái. (Nếu 0 → quyền lại mất, chạy §1.3.)

### 2.3 LÚC LÁI
- Không thao tác gì. Lái bình thường, đi qua **vài lần rẽ** (thuật toán dùng mẫu đoạn tới rẽ <2000 m khi **đang di chuyển**).

### 2.4 TỚI NƠI (đỗ, còn hotspot)
```bash
export VEH=<vehicle-ip>:5555
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$VEH"
NAVLOG=$("$ADB" -s "$VEH" shell 'ls -t /sdcard/Android/data/com.byd.clusternav/files/nav_log_*.csv | head -1' | tr -d '\r')
echo "$NAVLOG"
"$ADB" -s "$VEH" pull "$NAVLOG" ./commute-2026-08-14-pm.csv
python3 scripts/analyze-nav-distance-log.py ./commute-2026-08-14-pm.csv
```
Đọc:
- **`projected − screen`** mean (chỉ tính lúc di chuyển) → hướng chỉnh FACTOR:
  - **âm** (nội suy thấp hơn màn) → lùi quá nhanh → **GIẢM FACTOR** (0.95 → ~0.90).
  - **dương** (cao hơn) → lùi quá chậm → **TĂNG FACTOR** về gần 1.0.
- **`display − screen`** → xác nhận round (1.16) đã khử bias-xuống −34.5 m của bản floor.
- Cần **≥20 mẫu tươi lúc DI CHUYỂN** thì script mới in tuning hint (mẫu đỗ yên = mean 0, bỏ qua).

### 2.5 SAU KHI CÓ DATA
- [ ] Chỉnh **FACTOR** trong `core/.../TurnDistanceInterpolator.kt` (`private const val FACTOR = 0.95`) theo hướng §2.4.
- [ ] **Ship 1.18**: bump version + build release + push OTA — **chạy security-scan bắt buộc trước push** (steering §6). Gộp **cả** fix accessibility (§1.5) + FACTOR mới vào 1 bản.

---

## 3. THAM CHIẾU
- FACTOR + cơ chế nội suy: `core/src/main/kotlin/com/byd/clusternav/navigation/TurnDistanceInterpolator.kt` (`FACTOR=0.95`, `refine()` = ghi ground-truth).
- Quantize round (1.16): `core/.../navigation/NavParse.kt` `quantizeDisplay()`.
- Booster đọc màn: `app/.../modules/navaccess/NavAccessibilityService.kt` (gate: `Prefs.enabled` + `Prefs.accBooster`, cả 2 default ON).
- Cấp quyền: `app/.../NavConnect.kt` (`grantAccessibility` = dadb append) · `MainActivity.kt` (call-site mới).
- Log: `app/.../NavDistanceLog.kt` (cột `t_ms,rawGmaps_m,projected_m,display_m,closing_mps,speed_mps,screenRead_m,screenRead_age_ms,road,key`).
- Analyzer: `scripts/analyze-nav-distance-log.py`.
- Doc gốc thread này: `docs/diagnostics/oncar-sdk-findings-2026-08-13.md` (§J2, "Queued for next drive") · spec `docs/specs/hud-keepalive-interp-log-1.15.html`.
- Build: `export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)` rồi `./gradlew :app:compileReleaseKotlin :app:testDebugUnitTest --tests "com.byd.clusternav.NavCastUiWiringContractTest"`.

## 4. TRẠNG THÁI FILE LOG TRÊN XE (để không lẫn)
- Process app còn sống từ sáng → cả rows commute sáng (screenRead rỗng) **và** rows trưa (screenRead=210) nằm CHUNG `nav_log_1786668422023.csv`.
- Chiều: nếu app không bị kill → append tiếp file này; nếu bị kill/restart → file mới. Lệnh "`ls -t … head -1`" luôn lấy đúng file mới nhất.
