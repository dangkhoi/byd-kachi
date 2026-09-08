# ON-CAR HANDOFF — verify 1.12 (Giữa+ETA qua HAL) · 2026-08-13

> **Supersedes** `oncar-handoff-2026-08-12-evening.md`.
> Xe: BYD Seal DiLink 3.0, Android 10 (API 29), **không root**. Chủ: Đăng Khôi (dangkhoi).
> **1 CHỖ DUY NHẤT** cho lần lên xe tới. Tất cả **parked-only** (số P, phanh tay). Dọn = **power-cycle nút nguồn vật lý** (không tính `adb reboot`).
> Bản trên `main`/OTA cuối phiên: **1.12 (versionCode 112)** — commit `3884d55`.

---

## 0. TRẠNG THÁI (sau phiên 2026-08-13)

| Hạng mục | Trạng thái |
|---|---|
| **1.12** (HAL nav-screen + selector chế độ cụm in-app) | **Đã push `main`/OTA.** Off-car green (full JVM suite + aapt2 vc112, no test surface). **CHƯA verify on-car.** |
| **BIG WIN — Menu "Nav trên cụm" của AMAP** | ✅ **MỞ KHOÁ ĐƯỢC** = ghi HAL `SET_NAVI_SCREEN_STATUS_SET` (**`0x4C10E015`** · BYDAutoSettingDevice). Menu OEM: **Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF**. (owner xác nhận trên xe hôm nay) |
| op39 ch1000 (`service call AutoContainer 2 i32 1000 i32 39`) | ⛔ **NO-OP trên trim này** (op6/7 đổi day/night OK ⇒ kênh sống, nhưng op39 KHÔNG đổi center). Đã bỏ vai trò lever. |
| Đường HAL trong app | ✅ Đã wire `NavigationHudOwner`→`BydHal.writeNavFrame` vào `NavRepository` lane, gated Cast-OFF. Trước bị T7 fail-closed để mồ côi. |
| Selector chế độ cụm | ✅ Đã thêm (spinner_cluster_mode, cả 2 layout) → `Prefs.navClusterScreenMode` (đọc live). |

**3 ĐIỀU CHƯA VERIFY ON-CAR (mục tiêu phiên tới — xem §3).**

---

## 1. CHUẨN BỊ (nối máy)
```bash
export VEH=<vehicle-ip>:5555        # HỎI lại IP (hotspot đổi), ĐỪNG đoán
adb connect $VEH && adb devices     # thấy DiLink3.0, KHÔNG nhầm emulator
```
- **Cụm = `fission_screencap -d 0`** (help ghi ngược; `-d 0` thấy đồng hồ ⇒ đúng). IVI = `-d 1`.
- **navopen** sẵn ở **`/data/local/tmp/navopen.jar`** (uid shell). Prefix:
  `CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen <cmd>`
- Opcode CẤM (app blocklist, script không bắn): `1·18·41·91·92·43·42·45`.

---

## 2. LEVER THẬT — HAL feature ids (đã verify RE + navopen rc=0)

| Tên | id | Device | Ý nghĩa |
|---|---|---|---|
| `SET_NAVI_SCREEN_STATUS_SET` | **`0x4C10E015`** | Setting | Chọn chế độ nav-cụm (mở menu Đơn giản/Nhỏ/Toàn/OFF) |
| `INSTRUMENT_SEND_NAVI_STATUS_SET` | `0x43E0003A` | Instrument | Trạng thái đang-dẫn (2=navigating, 4=ended) |
| `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` | `0x43F01010` | Instrument | Nội dung simple-nav (icon rẽ) |
| `INSTRUMENT_FRONT_CROSSING_DISTANCE_SET` | `0x43F01018` | Instrument | Cự ly tới ngã rẽ |
| `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET` | `1140461576` | Instrument | Tên đường (UTF-16LE) |

Demo mở nav thủ công (đối chiếu): chạy `navopen` KHÔNG tham số → set status=2 + screen=3 + 1 frame "Nguyen Hue 250m".

---

## 3. VIỆC PHẢI LÀM ON-CAR (1.12) — theo thứ tự

### 3A — Selector chế độ cụm có lên "Giữa + ETA" không (chính)
1. Đợi xe OTA lên **1.12** (hoặc `adb -s $VEH install -r apk/ClusterNav-1.12-release.apk`).
2. **Cast = TẮT**. Mở GMaps dẫn đường (có route thật, đứng yên OK).
3. Mở app ClusterNav → card Nav có spinner **"Chế độ hiển thị trên cụm"**. Lần lượt chọn **Đơn giản → Toàn màn hình → Màn hình nhỏ → OFF**, mỗi lần chụp cụm:
   ```bash
   adb -s $VEH shell "fission_screencap -d 0 -p /data/local/tmp/m.png"; adb -s $VEH pull /data/local/tmp/m.png ./mode-<ten>.png
   ```
4. Xem logcat owner (rc mỗi feature; rc=0 = HAL nhận):
   ```bash
   adb -s $VEH shell "logcat -d -s NavigationHudOwner" | tail
   # kỳ vọng: "cluster-nav icon=.. seg=.. mode=<n> → INSTRUMENT_SEND_NAVI_STATUS_SET=0 NAVI_SCREEN=0 INSTRUMENT_GUIDE_INFO_SIMPLE_SET=0 ..."
   ```
   - **rc=0 hết + cụm lên nav giữa** ⇒ app-uid ghi HAL OK. **CHỐT value nào = "Đơn giản (Giữa+ETA)"** → báo lại để set default `Prefs.NAV_SCREEN_*`/`NAV_SCREEN_MODE_ON`.
   - **rc có SecurityException / cụm không đổi** ⇒ app-uid ghi HAL bị chặn → sang **3C**.

### 3B — Map value ↔ menu (độc lập app, để chốt con số)
Đối chiếu bằng navopen (uid shell — đã proven), sweep + chụp:
```bash
for v in 0 1 2 3; do
  adb -s $VEH shell "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen setraw setting 4C10E015 $v"
  sleep 2; adb -s $VEH shell "fission_screencap -d 0 -p /data/local/tmp/s$v.png"; adb -s $VEH pull /data/local/tmp/s$v.png ./screen-$v.png
done
```
→ ghi bảng value→(Đơn giản/Nhỏ/Toàn/OFF). (Đoán hiện tại: 3=Toàn màn hình đã proven; Đơn giản có thể =1 — CẦN xác nhận.)

### 3C — Nếu app-uid ghi HAL KHÔNG render (fallback)
navopen chạy được vì uid **shell (2000)**; app chạy uid app. Nếu 3A cho thấy in-process (BydHal bypass-context) bị HAL từ chối:
- Đổi `NavigationHudOwner`/`BydHal` sang **chạy navopen qua dadb loopback** (`SimpleCastCoordinator.executeShell`, uid shell) thay vì reflection in-process. (Việc off-car sau khi có kết luận.)

### 3D — Mũi tên rẽ
Xem icon mũi tên trên cụm có đúng hướng không (nghi vấn: `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` nhận mã **AMAP** hay **CAN**). Nếu sai → map `TurnIdMapToCAN` trước khi ghi.

---

## 4. DỌN DẸP (bắt buộc trước khi rời/lái)
- **Power-cycle nút nguồn vật lý** — dọn opcode/HAL tích lũy + trạng thái compositor.
- Nếu đã set OFF/nav-screen lạ → chọn lại chế độ cụm mong muốn ở menu OEM (hoặc selector app) sau power-cycle.

---

## 5. THAM CHIẾU
- **Findings + root-cause đầy đủ:** `docs/diagnostics/oncar-2026-08-13-amap-cluster-menu-and-op39-rootcause.md`
- **Spec:** `docs/specs/nav-cluster-op39-selfdiagnose.html` (v3 changelog + Pass 2 reviewer log)
- **Code 1.12:** `NavRepository.kt` (wire owner + navOnlyMode gate) · `NavigationHudOwner.kt` (đọc Prefs mode, clear đúng) · `modules/hal/BydHal.kt` (`writeNavFrame(screenMode)`, `NAV_SCREEN_MODE_ON`) · `Prefs.kt` (`navClusterScreenMode`) · `MainActivity.kt` (spinner_cluster_mode) · `res/layout*/activity_main.xml`
- **RE firmware:** `~/Library/Caches/clusternav-re/diagnostic-amap/.../AmapService.java` · BYDAutoFeatureIds (id thật)
- **Fence re-seal:** `offcar-planner/.../ExpansionTransportFenceTest.kt` (T11 activity_main.xml hash)
- Commit OTA: `3884d55` (release 1.12).
