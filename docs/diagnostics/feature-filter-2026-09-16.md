# Lọc chức năng launcher theo bảng owner 2026-09-16 (`feature.xlsx`, cột "Dùng")

- **Chủ:** dangkhoi · **Nguồn:** owner chấm YES/NO trên báo cáo captest Sealion 6 (187 mục) + "GÔM LẠI THÀNH HÌNH XE, 1 WIDGET" cho 8 mục lốp. Owner: *"các cái NO là bỏ hẳn, YES đã OK thì giữ, chưa OK thì phải RE tiếp, riêng cái lốp (áp suất, nhiệt độ) thì chỉ gôm lại thành 1 widget có hình xe đẹp, hiện đủ … thông tin rõ ràng là OK"*.
- **Luật "đã OK"**: OK trên **ít nhất một** xe (S6 hoặc xe owner) — vì xe owner bị chặn ĐỌC do provision (`oncar-trace-2026-09-16b` §6), một mục đọc được trên S6 là đã chứng minh cơ chế.
- Tổng: YES 136 (giữ-đã-OK **99** · RE tiếp **34** · YES nhưng đã gỡ theo (N) 3) · NO 43 (đã gỡ theo (N) 24 · **bỏ thêm 19**) · lốp gộp 8.

## 1. BỎ HẲN thêm (NO) — ✅ ĐÃ XOÁ 2026-09-17 (bản **1.69 · 70**)

> **Trạng thái: DONE.** Cả 19 mã dưới đây đã xoá khỏi registry/HAL/mô tả/icon/giọng nói/catalog đúng theo playbook
> của đợt (N) (`adas-purge-2026-09-16.md`). **Số mới đếm từ registry**: **47 nút · 100 thông tin · 4 gói ·
> 3 hành động = 154** *(trước lượt này 167; trước (N) là 187)*; nhãn EN 297 → **278**; `status-by-id.json`
> 164 → **145**; tệp `ic_*.xml` 144 → **140** (xoá `ic_battery_charging` · `ic_plug` · `ic_drift` ·
> `ic_car_top_window_rain` — tính bằng máy, 0 tham chiếu sống). Nhóm `g_energy` đổi tên *"Năng lượng & sạc"* →
> *"Năng lượng"* và đổi thành viên (5 ô sạc ra, `consumption_50km` vào). **Cơ chế mất chủ cũng xoá** (không để
> bảng rỗng): `HalReadTables.ARRAY_INDEX` · `BOOL_WHEN_EQUALS` · `HalBindingTable.readIntList` ·
> `CHARGE_STOP_MARKS`/`chargeStopCapacityEnum`. Tám ô lốp lẻ **KHÔNG xoá** — vào
> `CapabilityCatalog.HIDDEN_FROM_PICKER` (ẩn khỏi bộ chọn, datum + nhóm `g_tyres` + widget `w_tire` giữ nguyên).
> **[ĐO]** `:core` **2134**/0 · `:app` **1095**/0 (JUnit XML). Bài hồi quy mới:
> `WorkspaceStateTest.sanitized bo het 19 ma cua luot FEATURE-FILTER` (duyệt từng mã) +
> `tam o lop le chi bi an, khong bi don khoi cau hinh da luu`.


| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `is_charging` | tin | Đang sạc | OK | X |
| `charge_power` | tin | Công suất sạc | OK | X |
| `charging_pct` | tin | Sạc % | OK | X |
| `charging_eta_hour` | tin | Còn (giờ) | OK | X |
| `charging_eta_min` | tin | Còn (phút) | OK | X |
| `charging_capacity_kwh` | tin | Đã sạc phiên | OK | X |
| `charging_state` | tin | Trạng thái sạc | OK | X |
| `charger_work_state` | tin | Trạng thái bộ sạc | OK | X |
| `batt_range_bodywork` | tin | Tầm pin (thân xe) | X | X |
| `target_soc_set` | nút | Mục tiêu sạc | X | X |
| `charge_cap` | nút | Giới hạn sạc | X | OK |
| `start_charging` | nút | Sạc ngay | OK | X |
| `drift_mode` | tin | Chế độ drift | X | X |
| `drive_mode` | nút | Chế độ lái | OK | X |
| `rain_close` | nút | Tự đóng kính khi mưa | X | OK |
| `mirror_auto` | nút | Gập gương khi khoá | X | X |
| `mirror_fold_btn` | nút | Gập gương | X | X |
| `mcu_status` | tin | Trạng thái nguồn (MCU) | OK | OK |
| `key_bluetooth` | tin | Chìa Bluetooth | X | X |

NO đã gỡ theo (N): `avh`, `seatbelt_driver`, `seatbelt_passenger`, `oms_driver`, `oms_passenger`, `child_presence`, `speed_limit_warning`, `bsd_fl_alarm`, `bsd_fr_alarm`, `lca_left`, `lca_right`, `rcta_left`, `rcta_right`, `dow_left`, `dow_right`, `radar_zones`, `radar_volume`, `esp_state`, `adas_esp`, `adas_lane`, `adas_fcw`, `adas_rcta`, `adas_dow`, `adas_cpd`

## 2. GIỮ (YES, đã OK ≥ 1 xe)

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `soc` | tin | Pin (SOC) | OK | OK |
| `ev_range_km` | tin | Tầm hoạt động EV | OK | OK |
| `fuel_range_km` | tin | Tầm hoạt động xăng | OK | ? |
| `fuel_pct` | tin | Mức xăng | OK | ? |
| `odometer` | tin | Odo tổng | OK | OK |
| `ev_mileage_km` | tin | Km chạy điện | OK | X |
| `trip_km` | tin | Quãng đường chuyến | OK | X |
| `trip_hours` | tin | Thời gian chuyến | OK | X |
| `trip_kwh` | tin | Điện tiêu thụ chuyến | OK | X |
| `consumption_50km` | tin | Tiêu thụ 50km | OK | OK |
| `motor_power` | tin | Công suất mô-tơ | OK | X |
| `cell_temp_high` | tin | Nhiệt cell cao | OK | X |
| `cell_temp_low` | tin | Nhiệt cell thấp | OK | X |
| `cell_temp_avg` | tin | Nhiệt cell TB | OK | X |
| `soh_oem` | tin | Sức khoẻ pin (SOH) | OK | X |
| `wireless_charge` | nút | Sạc không dây | OK | X |
| `speed` | tin | Tốc độ | OK | OK |
| `accel_pct` | tin | Chân ga | OK | OK |
| `brake_pct` | tin | Chân phanh | OK | OK |
| `motor_front_rpm` | tin | Vòng tua mô-tơ trước | OK | X |
| `motor_rear_rpm` | tin | Vòng tua mô-tơ sau | OK | X |
| `motor_front_torque` | tin | Mô-men mô-tơ trước | OK | X |
| `engine_rpm` | tin | Vòng tua máy xăng | X | OK |
| `steering_deg` | tin | Góc vô-lăng | OK | X |
| `wheel_speed` | tin | Tốc độ bánh | OK | X |
| `slope_deg` | tin | Độ dốc | OK | X |
| `gear` | tin | Số | OK | OK |
| `op_mode` | tin | Chế độ lái | OK | X |
| `energy_mode` | tin | Chế độ năng lượng | OK | X |
| `pm25_level` | tin | Mức bụi mịn | OK | OK |
| `pm25_value` | tin | Bụi mịn PM2.5 | OK | X |
| `pm25_online` | tin | Cảm biến bụi mịn | OK | OK |
| `ext_temp` | tin | Nhiệt ngoài xe | OK | OK |
| `ac_on` | tin | Điều hoà | OK | OK |
| `ac_wind` | tin | Mức quạt gió | OK | X |
| `ac_cycle` | tin | Chế độ lấy gió | OK | X |
| `temp_unit` | tin | Đơn vị nhiệt | OK | X |
| `anion_state` | tin | Ion âm | OK | X |
| `pm25` | nút | Lọc bụi | X | OK |
| `seatc` | nút | Ghế mát | OK | OK |
| `temp` | nút | Nhiệt độ | OK | OK |
| `fan` | nút | Gió | OK | OK |
| `defrost` | nút | Sấy kính | OK | OK |
| `seath` | nút | Ghế sưởi | OK | OK |
| `recirc` | nút | Lấy gió trong | OK | OK |
| `defrost_rear` | nút | Sấy kính sau | X | OK |
| `anion` | nút | Ion âm | X | OK |
| `steer_heat` | nút | Sưởi vô-lăng | X | OK |
| `pm25_clean_now` | nút | Lọc ngay | OK | OK |
| `window_lf` | tin | Kính trước-trái | OK | OK |
| `window_rf` | tin | Kính trước-phải | OK | OK |
| `window_lr` | tin | Kính sau-trái | OK | OK |
| `window_rr` | tin | Kính sau-phải | OK | OK |
| `door_lf` | tin | Cửa trước-trái | OK | X |
| `door_rf` | tin | Cửa trước-phải | OK | X |
| `door_lr` | tin | Cửa sau-trái | OK | X |
| `door_rr` | tin | Cửa sau-phải | OK | X |
| `tailgate_status` | tin | Cốp sau | OK | X |
| `sunroof_state` | tin | Cửa sổ trời | OK | OK |
| `sunroof_pos` | tin | Vị trí cửa sổ trời | OK | OK |
| `sunshade_pct` | tin | Rèm che nắng | OK | OK |
| `wiper_state` | tin | Gạt mưa | OK | X |
| `power_level` | tin | Nguồn xe | OK | OK |
| `vehicle_type` | tin | Mẫu xe | OK | OK |
| `emergency_alarm` | tin | Cảnh báo khẩn | OK | X |
| `window` | nút | Kính cửa lái | OK | OK |
| `win_lf` | nút | Kính trước-trái | OK | OK |
| `win_rf` | nút | Kính trước-phải | OK | OK |
| `win_lr` | nút | Kính sau-trái | OK | OK |
| `win_rr` | nút | Kính sau-phải | OK | OK |
| `windows_all` | nút | Tất cả kính | OK | OK |
| `sunshade` | nút | Rèm che nắng | OK | OK |
| `seat_memory` | nút | Nhớ ghế lái | X | OK |
| `light_low_beam` | tin | Đèn cốt | OK | X |
| `light_high_beam` | tin | Đèn pha | OK | X |
| `light_front_fog` | tin | Đèn sương mù trước | OK | OK |
| `light_rear_fog` | tin | Đèn sương mù sau | OK | OK |
| `light_left_turn` | tin | Xi-nhan trái | X | OK |
| `light_right_turn` | tin | Xi-nhan phải | X | OK |
| `light_side` | tin | Đèn hông | X | OK |
| `light_drl` | tin | Đèn ban ngày | OK | X |
| `headlight_feedback` | tin | Chế độ đèn pha | OK | X |
| `ambient_front_color` | tin | Màu viền trước | OK | X |
| `ambient_rear_color` | tin | Màu viền sau | OK | X |
| `ambient_front_brightness` | tin | Độ sáng viền trước | OK | X |
| `ambient_rear_brightness` | tin | Độ sáng viền sau | OK | X |
| `readl` | nút | Đèn đọc | OK | OK |
| `drl` | nút | Đèn ban ngày | OK | X |
| `volt_12v` | tin | Ắc-quy 12V | OK | X |
| `volt_12v_level` | tin | Mức ắc-quy 12V | OK | X |
| `vin` | tin | Số VIN | OK | OK |
| `engine_coolant_level` | tin | Mức nước làm mát | X | OK |
| `oil_level` | tin | Mức dầu | OK | X |
| `cam` | nút | Camera 360 | OK | X |
| `vol` | nút | Âm lượng | OK | OK |
| `cluster_music` | nút | Nhạc trên cụm | OK | X |
| `brightness_gear` | nút | Độ sáng màn | OK | OK |
| `hud_switch` | nút | HUD kính lái | OK | OK |
| `hud_brightness` | nút | Độ sáng HUD | OK | OK |

## 3. RE TIẾP (YES, chưa OK xe nào) — gom theo gốc, thứ tự ưu tiên owner: cốp · điều hoà AUTO trước

### Thân xe — không có setter trong framework thật (cần `set(int[],BYDAutoEventValue)`/`voiceCtlBackDoor`; spec S)

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `trunk` | nút | Cốp sau | X | X |
| `lock` | nút | Khoá / mở khoá | X | X |
| `door` | nút | Mở khoá cửa | X | X |
| `hood` | nút | Ca-pô | X | X |
| `child_lock` | nút | Khoá trẻ em | X | X |
| `sunroof` | nút | Cửa sổ trời | X | X |
| `wiper` | nút | Gạt mưa | X | X |
| `tailgate_position` | tin | Vị trí cốp | X | X |
| `mirror_fold` | tin | Gương chiếu hậu | X | X |

### Điều hoà — đổi route sang method thật (`setAcControlMode`, `setAcDefrostState`, PM2P5/Setting device; spec S + live-state)

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `ac_auto` | nút | Điều hoà AUTO | X | X |
| `cabin_temp` | tin | Nhiệt trong cabin | X | X |
| `inside_temp` | tin | Nhiệt cài đặt | X | X |
| `coolant_temp` | tin | Nhiệt nước làm mát | X | X |

### Đèn — route đèn pha/viền chưa đúng device (E6: RE feature id)

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `headl` | nút | Đèn pha | X | X |
| `headlight_mode` | nút | Chế độ đèn pha | X | X |
| `ambient_power` | nút | Đèn viền cabin | X | X |
| `ambient_color` | nút | Màu đèn viền | X | X |
| `ambient_brightness` | nút | Độ sáng viền | X | X |
| `ambient_music` | nút | Đèn viền theo nhạc | X | X |
| `ambient_enabled` | tin | Đèn viền cabin | X | X |

### Động lực — setter chế độ chưa có bằng chứng

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `powertrain_mode` | nút | EV / HEV | X | X |
| `regen_level` | nút | Mức tái tạo | X | X |

### Pin — getter trả rỗng trên cả 2 xe (có thể không có trên trim)

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `batt_temp` | tin | Nhiệt độ pin | X | X |
| `cell_v_high` | tin | Áp cell cao | X | X |
| `cell_v_low` | tin | Áp cell thấp | X | X |
| `target_soc` | tin | Mục tiêu sạc | X | X |

### GPS — owner cho xin quyền vị trí (spec S R5)

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `gps_lat` | tin | Vĩ độ | X | X |
| `gps_lon` | tin | Kinh độ | X | X |
| `gps_elevation` | tin | Cao độ | X | X |
| `gps_heading` | tin | Hướng | X | X |

### Giải trí/cụm — cast hỏng trên S6 (mới), xoay màn/góc camera chưa có bằng chứng

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `cast` | nút | Chiếu cụm | X | X |
| `screen_rotation` | nút | Xoay màn hình | X | X |
| `camera_view` | nút | Góc camera | X | X |

### Danh tính

| id | Loại | Nhãn | S6 | Owner |
|---|---|---|---|---|
| `engine_code` | tin | Mã máy | X | X |

## 4. LỐP — gộp thành MỘT widget hình xe

8 datum: `tyre_p_fl`, `tyre_p_fr`, `tyre_p_rl`, `tyre_p_rr`, `tyre_t_fl`, `tyre_t_fr`, `tyre_t_rl`, `tyre_t_rr` (áp suất + nhiệt độ 4 bánh). Owner: một widget có hình xe đẹp, hiện đủ thông tin rõ ràng. ⇒ thuộc VISUAL-REFRESH P3 (hình xe 3 mặt + `CarPartStyle`): widget `w_tyres`/nhóm Lốp thay bằng một thẻ duy nhất, bánh nào cảnh báo thì tô bánh đó; các ô lốp lẻ ẩn khỏi bộ chọn (không xoá mã — vẫn đọc được bằng giọng).

## 5. Mâu thuẫn cần owner chốt (mặc định tôi chọn)

- Bảng ghi **YES** cho `adas_slw` (Cảnh báo quá tốc), `adas_tsr` (Nhận diện biển báo), `itac` — ba nút này **đã gỡ** theo lệnh ADAS-PURGE 09-16 ("coi như chưa bao giờ tồn tại"). **Mặc định: giữ nguyên đã gỡ** (an toàn lái, đúng lệnh trước); owner muốn khôi phục thì nói, khôi phục được từ git.
- `volt_12v`/`volt_12v_level` YES (đã chuyển sang Năng lượng) — giữ. `mcu_status` NO — bỏ.
