# NOTES — bộ icon P2 (agent B1 · 2026-09-17) — cho agent tích hợp

> ⚠ **Tích hợp 2026-09-17 (agent C):** `design/out/` KHÔNG còn — script ghi thẳng `app/src/main/res/drawable/` (`--check` so byte với đích thật); contact sheet/bảng tương phản ở `docs/diagnostics/visual-refresh-2026-09-16/p2/`; script kiểm ô quang học dung sai **0.05**, cạnh ≥ **16** (bằng `IconGeometryContractTest`); lớp `bg` luôn vẽ TRƯỚC; path có gradient KHÔNG kèm `android:fillColor` phẳng (AGP từ chối). Sáu glyph vẽ lại: engine · mode · group_windows · pedal · torque · hood.


Spec: `docs/specs/kachi-visual-refresh.html` §R2 · §4.2 · §4.6 · T4 (đường ống) · T7 · T9 · T10.
Phạm vi lượt này: **NGUỒN + SINH RA THƯ MỤC TẠM**. Chưa chạm `res/drawable`, Kotlin, test, spec HTML.

## 0 · Số đếm [ĐO bằng `scripts/design/icon-audit.py`]

| | số |
|---|---|
| tệp `ic_*.xml` trong `res/drawable` hôm nay | 140 |
| — hình xe `ic_car_*` (agent B2, `design/car/`) | 43 |
| — LOẠI có lý do (điều hướng · nút nổi Cast · mặt nạ · icon app) | 13 (bảng §3) |
| **vẽ lại — `design/glyph/*.svg`** | **85** (= 140 − 43 − 13 + 1: `ic_clock_g` vốn dùng cả ở launcher lẫn cockpit, giữ một tệp) |
| tệp sinh `design/out/drawable/` | 127 = 85 × 24dp + 21 icon `large` × {32 `_l`, 48 `_xl`} |
| tên `ic-…` `:core` truyền vào `KachiTheme.iconRes` | 112 → 69 tra ra tệp SINH · 43 tra ra tệp LOẠI (đều là `ic_car_*`) · 0 thiếu |
| tệp cũ chưa phủ (stale) | 0 |

## 1 · Đường ống (§4.6) — ba tệp, không phụ thuộc ngoài (Python 3 stdlib; PIL chỉ cho contact sheet)

```
design/icon-grammar.json      ngữ pháp: họ màu theo lĩnh vực · cờ large · ghi chú khái niệm   (MỘT nơi — AC2.5)
design/glyph/<id>.svg         nguồn hình 24×24, path mang data-layer main|sub|bg (+ data-fx grad|glow, data-min 32)
scripts/design/gen-icons.py   sinh VectorDrawable tất định; luật cứng AC2.1–2.5 · AC5.1 · §4.2 kiểm NGAY LÚC SINH
scripts/design/icon-audit.py  đọc lại XML thật: luật · phủ registry · bảng tương phản · contact sheet (PIL)
```

Lệnh (từ gốc repo):
```
python3 scripts/design/gen-icons.py                                 # → design/out/drawable/
python3 scripts/design/gen-icons.py --out app/src/main/res/drawable # bước tích hợp
python3 scripts/design/gen-icons.py --check app/src/main/res/drawable   # exit 1 nếu có tệp bị vá tay  ← T10 "sinh-lại-so-byte"
python3 scripts/design/icon-audit.py --drawable app/src/main/res/drawable --sheets
```
[ĐO] `--check` đã thử: sửa 1 ký tự trong `ic_ac.xml` ⇒ báo `LỆCH byte`, exit 1; trả lại ⇒ 127/127 khớp.

Đầu ra tất định: thuộc tính thứ tự cố định, số làm tròn 2 chữ số, header ghi rõ *SINH BỞI … từ design/glyph/<f>.svg — ĐỪNG VÁ TAY*.
Comment trong XML bị aapt bỏ khi biên dịch ⇒ 508 KB nguồn KHÔNG phải Δ APK (đo thật ở T11 §6.3).

## 2 · Ngữ pháp đã đóng vào script (mỗi dòng = một AC)

- **3 lớp, đúng 3 bậc alpha**: `main` 1.0 · `sub` 0.38 · `bg` 0.16 — alpha KHÔNG gõ tay, suy từ `data-layer` (AC2.1). Lớp `main` bắt buộc có.
- **2 độ dày nét**: lớp main 1.8 · lớp sub/bg 1.2 (AC2.2); cap/join tròn trên mọi đường nét; đường chỉ tô không mang thuộc tính nét (AC2.3 + luật cũ).
- **Khung 24×24, hộp mực (kể cả nửa nét) trong 2..22 (dung sai 0.35), cạnh lớn ≥ 14** (AC2.4). Biến thể 32/48 giữ viewport 24, chỉ đổi `width/height` dp ⇒ nét tỉ lệ theo cỡ.
- **Trần path**: ≤ 6 ở 24dp · ≤ 10 ở 32/48 (AC5.1).
- **Màu chỉ từ họ màu** của lĩnh vực trong grammar: token `ink` (#FFFFFF) · `main` · `light` · `deep`; `data-fx="grad"` = chuyển sắc tuyến tính light→deep trên-trái → dưới-phải (một hướng sáng cho cả bộ), `glow` = toả tròn. Dùng `<aapt:attr name="android:fillColor"><gradient>` — [ĐO 09-16] render đúng trên API 29.
- **Chi tiết biến mất theo cỡ** (§4.2 luật 6): `data-min="32"` ⇒ lớp đó chỉ xuất hiện ở 32/48 (đĩa nền, thân xe mờ…). 24dp = main (+sub).
- Họ màu = ĐÚNG mã `KachiPalette.DARK` (green/accent2/cyan/amber/accent/red + slate/silver/orange của `domainTints`) — không thêm dải màu thứ hai. Bốn stop `deep` đã **nâng** để ≥ 3:1 trên thẻ tối `#1d232e` (drive `#7059d7` · tyres `#626e82` · identity `#4068d7` · alert `#c63751`) — đo ở `design/out/contrast-families.md`.

Họ màu theo lĩnh vực: ENERGY→energy (lục) · DRIVETRAIN→drive (tím) · CLIMATE→climate (lam-xanh) · TYRES→tyres (xám-lam) · BODY→body (bạc) · LIGHTS→lights (vàng ấm) · IDENTITY→identity (xanh nhấn) · INFOTAINMENT→media (cam) · LAUNCHER/COCKPIT→chrome (mực). Ghi đè theo icon: `sun`→lights, `car`→body, `alert`→alert (đỏ = nghĩa).

## 3 · LOẠI khỏi bộ glyph (có lý do — cùng lệ `orphanPending`)

| tệp | vì sao |
|---|---|
| `ic_car_*` (43) | hình xe theo vị trí — nguồn `design/car/*.svg` của B2 (§4.3) |
| `ic_launcher` | icon app hoa anh đào (R7 xong 1.68) |
| `ic_turn_left` · `ic_turn_right` · `ic_turn_straight` · `ic_turn_right_g` | mũi tên rẽ của màn dẫn đường (bảng NEW_ICON/CAN, `re-maneuver-icon-tables-2026-08-14.md`) — không thuộc launcher |
| `ic_bubble_nav` · `ic_menu_config` · `ic_menu_left` · `ic_menu_right` | nút nổi Cast vẽ trực tiếp, xanh thương hiệu, bề mặt đã chạy trên xe (CLAUDE.md §6) |
| `ic_check_selected` · `ic_chevron_down` · `ic_corner_cut` | màu là nghĩa / nằm trong layer-list / mặt nạ |

Danh sách này nằm trong `EXCLUDED` của `icon-audit.py`; thêm/bớt phải có lý do.

## 4 · Tương phản & chủ đề SÁNG — điều tích hợp PHẢI biết

[ĐO] `design/out/contrast-families.md`: trên nền TỐI mọi `main`/`light` ≥ 4.4:1, mọi `deep` ≥ 3.0:1 trên thẻ. Trên nền **SÁNG** `#eef1f6` thì `main`/`light` của mọi họ **< 3:1** (vàng 1.47, lục 1.70…) — đây là bản chất màu sáng, không sửa được bằng cách đổi hex mà vẫn giữ một tệp.
⇒ **Hợp đồng**: chủ đề SÁNG **tiếp tục tint INK** như hôm nay (`setColorFilter(c(KachiTheme.INK))` — 14 chỗ gọi trong `launcher/`); thang alpha vẫn sống qua tint nên hình vẫn có 3 lớp. Chủ đề TỐI: chỗ nào muốn thấy màu/chuyển sắc thì **bỏ colour filter** (đề xuất: mặt lớn — thẻ widget 56dp `WorkspaceViewCards`, đầu nhóm, ô bộ chọn 44dp; giữ tint mực ở thanh trên 20dp và chip). Contact sheet `contact-sheet-36px-sang.png` cho thấy đúng hiện tượng (icon họ chrome biến mất trên nền sáng khi không tint).
AC2.6 (chọn/không-chọn): icon chưa chọn → `ColorMatrixColorFilter` giảm bão hoà + alpha, KHÔNG `setTint` đơn sắc; lớp `main` luôn mang đủ hình nên vẫn đọc được khi mất màu.

## 5 · Biến thể 32/48 (T9)

21 icon `large: true` = 13 mặt widget (`WidgetRegistry`: bolt · group_tyres · leaf · sun · music · car · speed · grid · photo · window_open · window_close · door · lock) + 9 đầu nhóm (`CapabilityGroups`). Tên: `ic_<id>_l.xml` (32dp) · `ic_<id>_xl.xml` (48dp). Cùng nguồn; khác 24dp ở chỗ **có thêm lớp `data-min=32`** (đĩa/thân mờ), không phải phóng to.
Tích hợp: `KachiTheme.iconRes(icon, sizeDp)` (hoặc hàm mới `iconResFor(icon, Sp.ICON_L/XL/XXL)`) tra `_l` khi cỡ ≥ 32, `_xl` khi ≥ 48, lùi về 24 nếu không có biến thể. Chưa wire thì bài "không tệp mồ côi" của `IconStyleContractTest` sẽ đỏ 42 tệp — đó là cố ý, đừng khai vào `orphanPending`.

## 6 · Bài canh cần đổi (T10) — B1 KHÔNG sửa test, chỉ liệt kê

`IconStyleContractTest`: (1) `strokeWidth` duy nhất 1.6 → tập {1.8, 1.2} · (2) *"không màu riêng"* → *"màu ∈ họ của lĩnh vực trong `design/icon-grammar.json`"* (đọc JSON trong test; `#FFFFFF`/trong suốt luôn hợp lệ; `colorExceptions` giữ cho 7 tệp loại) · (3) thêm thang alpha {1, 0.38, 0.16} · (4) trần path theo cỡ · (5) **sinh-lại-so-byte**: chạy `gen-icons.py --check` (ProcessBuilder `python3`) hoặc port hàm sinh — spec T10 đòi "cố tình vá tay một tệp ⇒ đỏ".
Bài mới `IconSetInventoryTest` (AC6.2): mọi id trong `TelemetryRegistry ∪ ControlRegistry ∪ WidgetRegistry ∪ CapabilityGroups ∪ ActionMacros` → `KachiTheme.iconRes ≠ 0` — `icon-audit.py` mục 2 đã làm bản Python (69 sinh + 43 loại, 0 thiếu), test Kotlin chỉ cần lặp lại từ phía registry.
`IconGeometryContractTest`: giữ (2..22 · ≥16 cho icon nó đang canh); script canh ≥ 14 cho cả bộ vì `close`/`swap`/`clock` cố ý gọn hơn.

## 7 · Icon yếu ở 24dp — nói thẳng

- `pedal` (bàn đạp nghiêng) và `torque` (trục + tay đòn + mũi tên) là hai hình trừu tượng nhất; ở 24px còn đọc được nhờ khối tô, nhưng nếu owner không nhận ra thì thay bằng chữ tắt trong ô là hợp lý hơn vẽ lại.
- `hood` (ca-pô hé) đọc ra "xe nhìn từ trước" trước khi đọc ra "nắp" — chấp nhận vì nó đứng cạnh nhãn.
- Họ `chrome` (thanh trên, cockpit) là một tông theo thiết kế — không có chuyển sắc để tint sạch ở cả hai chủ đề.

## 8 · Bảng kê icon → lĩnh vực · họ màu · cỡ · nơi dùng ở `:core` (sinh bằng máy)

| tệp | lĩnh vực | họ | cỡ | dùng ở `:core` |
|---|---|---|---|---|
| `ic_ac` | CLIMATE | climate | 24 | CapabilityIcons.kt: ac_cycle, ac_on, ac_wind; ControlRegistry.kt: ac_auto |
| `ic_adv_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_alert` | BODY | alert | 24 | CapabilityIcons.kt: emergency_alarm, power_level, vehicle_type |
| `ic_badge_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_battery` | ENERGY | energy | 24 | CapabilityIcons.kt: soc, soh_oem, volt_12v, volt_12v_level |
| `ic_bolt` | ENERGY | energy | 32·48 | CapabilityIcons.kt: emergency_alarm, energy_mode, op_mode, power_level, vehicle_type; ControlRegistry.kt: powertrain_mode, regen_level; Laun |
| `ic_brake` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: accel_pct, brake_pct, slope_deg |
| `ic_bubble_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_cam` | INFOTAINMENT | media | 24 | ControlRegistry.kt: cam, camera_view |
| `ic_car` | IDENTITY | body | 32·48 | CapabilityIcons.kt: emergency_alarm, power_level, vehicle_type; LauncherCatalog.kt: ·; WidgetRegistry.kt: · |
| `ic_cast` | INFOTAINMENT | media | 24 | ControlRegistry.kt: cast, hud_switch, screen_rotation; LauncherCatalog.kt: · |
| `ic_cast_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_cell_temp` | ENERGY | energy | 24 | CapabilityIcons.kt: cell_temp_avg, cell_temp_high, cell_temp_low |
| `ic_cell_volt` | ENERGY | energy | 24 | CapabilityIcons.kt: cell_v_high, cell_v_low, volt_12v, volt_12v_level |
| `ic_charger` | ENERGY | energy | 24 | ControlRegistry.kt: wireless_charge |
| `ic_clock_g` | ENERGY | energy | 24 | CapabilityIcons.kt: trip_hours |
| `ic_close` | LAUNCHER | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_consumption` | ENERGY | energy | 24 | CapabilityIcons.kt: consumption_50km, trip_kwh |
| `ic_coolant` | CLIMATE | climate | 24 | CapabilityIcons.kt: coolant_temp, temp_unit |
| `ic_defrost` | CLIMATE | climate | 24 | CapabilityIcons.kt: defrost_front_state, defrost_rear_state; ControlRegistry.kt: defrost |
| `ic_door` | BODY | body | 32·48 | ActionMacros.kt: · |
| `ic_drive` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: gear |
| `ic_dust` | CLIMATE | climate | 24 | CapabilityIcons.kt: pm25_, pm25_online |
| `ic_dust_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_engine` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: engine_rpm, wheel_speed |
| `ic_fan` | CLIMATE | climate | 24 | CapabilityIcons.kt: ac_cycle, ac_on, ac_wind; ControlRegistry.kt: fan; LauncherCatalog.kt: ·; TopStrip.kt: · |
| `ic_filter` | CLIMATE | climate | 24 | ControlRegistry.kt: pm25, pm25_clean_now |
| `ic_fuel` | ENERGY | energy | 24 | CapabilityIcons.kt: fuel_pct |
| `ic_gear` | LAUNCHER | chrome | 24 | LauncherActions.kt: · |
| `ic_gps_alt` | IDENTITY | identity | 24 | CapabilityIcons.kt: gps_elevation, gps_heading |
| `ic_gps_heading` | IDENTITY | identity | 24 | CapabilityIcons.kt: gps_elevation, gps_heading |
| `ic_gps_lat` | IDENTITY | identity | 24 | CapabilityIcons.kt: gps_lat, gps_lon |
| `ic_gps_lon` | IDENTITY | identity | 24 | CapabilityIcons.kt: gps_lat, gps_lon |
| `ic_grid` | LAUNCHER | chrome | 32·48 | LauncherActions.kt: ·; WidgetRegistry.kt: · |
| `ic_group_ambient` | LIGHTS | lights | 32·48 | CapabilityGroups.kt: · |
| `ic_group_battery_health` | ENERGY | energy | 32·48 | CapabilityGroups.kt: · |
| `ic_group_climate` | CLIMATE | climate | 32·48 | CapabilityGroups.kt: · |
| `ic_group_doors` | BODY | body | 32·48 | CapabilityGroups.kt: · |
| `ic_group_energy` | ENERGY | energy | 32·48 | CapabilityGroups.kt: · |
| `ic_group_lights` | LIGHTS | lights | 32·48 | CapabilityGroups.kt: · |
| `ic_group_trip` | ENERGY | energy | 32·48 | CapabilityGroups.kt: · |
| `ic_group_tyres` | TYRES | tyres | 32·48 | CapabilityGroups.kt: ·; WidgetRegistry.kt: · |
| `ic_group_windows` | BODY | body | 32·48 | CapabilityGroups.kt: · |
| `ic_hood` | BODY | body | 24 | CapabilityIcons.kt: engine_code, engine_coolant_level, oil_level |
| `ic_lang_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_leaf` | CLIMATE | climate | 32·48 | CapabilityIcons.kt: anion_state; ControlRegistry.kt: anion; TopStrip.kt: ·; WidgetRegistry.kt: · |
| `ic_left_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_light` | LIGHTS | lights | 24 | ControlRegistry.kt: brightness_gear, hud_brightness; LauncherCatalog.kt: · |
| `ic_lock` | BODY | body | 32·48 | ActionMacros.kt: ·; ControlRegistry.kt: child_lock |
| `ic_mic` | LAUNCHER | chrome | 24 | LauncherActions.kt: · |
| `ic_mic_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_mode` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: ac_mode_auto, energy_mode, op_mode |
| `ic_motor` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: motor_ |
| `ic_music` | INFOTAINMENT | media | 32·48 | ControlRegistry.kt: cluster_music; WidgetRegistry.kt: · |
| `ic_nav_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_next` | INFOTAINMENT | media | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_pedal` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: accel_pct, brake_pct, slope_deg |
| `ic_photo` | INFOTAINMENT | media | 32·48 | WidgetRegistry.kt: · |
| `ic_play` | INFOTAINMENT | media | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_prev` | INFOTAINMENT | media | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_range` | ENERGY | energy | 24 | CapabilityIcons.kt: ev_range_km, fuel_range_km |
| `ic_readlight` | LIGHTS | lights | 24 | ControlRegistry.kt: readl |
| `ic_recirc` | CLIMATE | climate | 24 | CapabilityIcons.kt: ac_cycle, ac_on, ac_wind; ControlRegistry.kt: recirc |
| `ic_right_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_road` | ENERGY | energy | 24 | CapabilityIcons.kt: ev_mileage_km, odometer, trip_km |
| `ic_rpm` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: motor_front_rpm, motor_front_torque, motor_rear_rpm |
| `ic_seat` | CLIMATE | climate | 24 | ControlRegistry.kt: seatc, seath, steer_heat |
| `ic_seat_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_sensor` | CLIMATE | climate | 24 | CapabilityIcons.kt: pm25_, pm25_online |
| `ic_slope` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: accel_pct, brake_pct, slope_deg |
| `ic_speed` | DRIVETRAIN | drive | 32·48 | CapabilityIcons.kt: engine_rpm, wheel_speed; LauncherCatalog.kt: ·; WidgetRegistry.kt: · |
| `ic_steering` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: steering_deg |
| `ic_sun` | CLIMATE | lights | 32·48 | CapabilityIcons.kt: seat_heat_state, seat_vent_state; WidgetRegistry.kt: · |
| `ic_swap` | LAUNCHER | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_sys_g` | COCKPIT | chrome | 24 | chỉ :app (thanh trên / cockpit / macro) |
| `ic_target` | ENERGY | energy | 24 | CapabilityIcons.kt: target_soc |
| `ic_temp` | CLIMATE | climate | 24 | CapabilityIcons.kt: batt_temp, cabin_temp, coolant_temp, ext_temp, inside_temp, temp_unit; ControlRegistry.kt: temp |
| `ic_temp_out` | CLIMATE | climate | 24 | CapabilityIcons.kt: cabin_temp, ext_temp, inside_temp |
| `ic_tire` | TYRES | tyres | 24 | LauncherCatalog.kt: · |
| `ic_torque` | DRIVETRAIN | drive | 24 | CapabilityIcons.kt: motor_front_rpm, motor_front_torque, motor_rear_rpm |
| `ic_volume` | INFOTAINMENT | media | 24 | CapabilityIcons.kt: media_vol; ControlRegistry.kt: vol |
| `ic_window` | BODY | body | 24 | LauncherCatalog.kt: · |
| `ic_window_close` | BODY | body | 32·48 | ActionMacros.kt: · |
| `ic_window_open` | BODY | body | 32·48 | ActionMacros.kt: · |
| `ic_wiper` | BODY | body | 24 | CapabilityIcons.kt: mirror_fold, wiper_state; ControlRegistry.kt: wiper |

## 9 · Công cụ

Không có `rsvg-convert`/`cairosvg`/ImageMagick trên máy này [ĐO]; contact sheet vẽ bằng bộ rasterizer PIL nội bộ trong `icon-audit.py` (flatten path → polygon/line, siêu lấy mẫu ×8, gradient xấp xỉ). Đủ để soát hình, KHÔNG thay được ảnh máy ảo (T11).
