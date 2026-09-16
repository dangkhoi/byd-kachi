# Kết quả KIỂM TRA TỪNG CHỨC NĂNG trên xe **Sealion 6** — 2026-09-16 (báo cáo anh em gửi)

- **Ngày nhận:** 2026-09-16 · **Chủ repo:** dangkhoi · **Xe:** BYD Sealion 6 (PHEV DM-i, DiLink 3) của anh em test, **không phải xe owner**.
- **Nguồn:** tệp `captest-report.txt` do công cụ *Kiểm tra từng nút xe* (Cài đặt › Hệ thống › Nâng cao) tự ghi ra thẻ, anh em chuyển lại. Nội dung là **nhãn + chấm OK/Không OK của người ngồi trên xe** ⇒ mức **[ĐO trên xe]** cho *hiện tượng* (có số / có tác dụng nhìn thấy), **không** phải giá trị HAL thô.
- **Bản Kachi:** báo cáo không ghi số bản. Bảng có đủ **187** mục (còn ADAS) ⇒ bản 1.64–1.66. **[CHƯA BIẾT]** chính xác bản nào — hỏi anh em / đọc `dumpsys package com.byd.launcher | grep versionName`.
- **Đối chiếu:** cột *Catalog trước* = `docs/catalog/status-by-id.json` tại commit `703fb14` (trước ADAS-PURGE): 🟢 đã đo chạy · ⚠ chạy một phần/nghi · ❌ không chạy · 🚗 chưa đo. Mục thuộc **(N) ADAS-PURGE** (27 id) đánh dấu 🗑 — đã gỡ khỏi launcher theo owner 2026-09-16, kết quả chỉ để lịch sử.
- **PII:** tệp chỉ có nhãn chức năng; không VIN, không số máy, không IP (đã grep).

## 1. Tổng

| | OK | Không OK | Tổng |
|---|---|---|---|
| Toàn bảng | **119** | **68** | 187 |
| Trong đó thuộc ADAS-PURGE 🗑 | 11 | 16 | 27 |
| **Còn lại sau purge** | **108** | **52** | 160 |

So với xe owner 2026-09-15 (`oncar-captest-results-2026-09-15.md`, **50/187 OK**, phần lớn "không đọc được"): Sealion 6 đọc được **gấp đôi**. [SUY] khớp ghi chú `project-context.md`: xe owner nhận sentinel `NOT_PROVISIONED_RC` cho nhiều feature (provision nội địa), xe Sealion 6 provision khác không bị chặn. **Chưa chứng minh** — cần `usage-*.log` của chính lượt chạy này (rc từng lệnh).

Bảng chéo với catalog trước:

| Kết quả S6 → Catalog trước | 🟢 | ⚠ | ❌ | 🚗 |
|---|---|---|---|---|
| OK | 10 | 53 | 0 | **56** |
| Không OK | 0 | 12 | 15 | 41 |

⇒ **56 mục lần đầu có bằng chứng chạy trên xe thật** (đang 🚗) và **53 mục ⚠ được xác nhận chạy** trên S6. Không có mục nào 🟢 mà S6 báo hỏng (0 mâu thuẫn ngược).

## 2. Các mục KHÔNG OK trên Sealion 6 — gom theo nguyên nhân đã biết

### Đã biết KHÔNG có setter trong framework thật (RE fw-dl3 09-16: `DoorLock`/`Bodywork` không có setter tương ứng) — cần đường `set(int[],BYDAutoEventValue)` / bridge `setev`, việc (F)

| id | Nhãn | Loại | Catalog trước |
|---|---|---|---|
| `lock` | Khoá / mở khoá | nút | ❌ |
| `door` | Mở khoá cửa | nút | ❌ |
| `trunk` | Cốp sau | nút | ❌ |
| `hood` | Ca-pô | nút | ❌ |
| `mirror_fold_btn` | Gập gương | nút | ❌ |
| `mirror_auto` | Gập gương khi khoá | nút | 🚗 |
| `child_lock` | Khoá trẻ em | nút | ❌ |
| `tailgate_position` | Vị trí cốp | tin | 🚗 |

### Cố ý chặn — không xin quyền vị trí (`DeadReckonRetirementTest`), catalog đã ghi BLOCKED-BY-DESIGN

| id | Nhãn | Loại | Catalog trước |
|---|---|---|---|
| `gps_lat` | Vĩ độ | tin | ❌ |
| `gps_lon` | Kinh độ | tin | ❌ |
| `gps_elevation` | Cao độ | tin | ❌ |
| `gps_heading` | Hướng | tin | ❌ |

### Owner E6 đã chốt cần RE feature id (4 nút "vắng trên trim" thực ra có trên xe): điều hoà AUTO · đèn viền · gập gương · khoá trẻ em — cùng họ với sấy kính sau / ion / sưởi vô-lăng / lọc bụi

| id | Nhãn | Loại | Catalog trước |
|---|---|---|---|
| `ac_auto` | Điều hoà AUTO | nút | ❌ |
| `ambient_enabled` | Đèn viền cabin | tin | 🚗 |
| `ambient_power` | Đèn viền cabin | nút | ❌ |
| `ambient_color` | Màu đèn viền | nút | 🚗 |
| `ambient_brightness` | Độ sáng viền | nút | 🚗 |
| `ambient_music` | Đèn viền theo nhạc | nút | 🚗 |
| `defrost_rear` | Sấy kính sau | nút | 🚗 |
| `anion` | Ion âm | nút | 🚗 |
| `steer_heat` | Sưởi vô-lăng | nút | 🚗 |
| `pm25` | Lọc bụi | nút | 🚗 |

### Đọc trạng thái điều hoà/nhiệt — R5 1.66 tra sai đường GHI (spec `kachi-live-state-ux.html` phát hiện #1) ⇒ null; nước làm mát PHEV cùng nhóm getter

| id | Nhãn | Loại | Catalog trước |
|---|---|---|---|
| `cabin_temp` | Nhiệt trong cabin | tin | ⚠ |
| `inside_temp` | Nhiệt cài đặt | tin | 🚗 |
| `coolant_temp` | Nhiệt nước làm mát | tin | 🚗 |
| `engine_coolant_level` | Mức nước làm mát | tin | 🚗 |

### Cast lên cụm + màn hình — **MỚI, ưu tiên cao**: trên Sealion 6 nút "Chiếu cụm" KHÔNG OK. [CHƯA BIẾT] vì sao (tên VD cụm khác `ClusterProfile`? bấm khi chưa có app trong ô? lỗi (A) cast rơi vào ô launcher chưa được xác nhận trên xe?). Cần `am stack list` + `dumpsys display` trên chính xe đó.

| id | Nhãn | Loại | Catalog trước |
|---|---|---|---|
| `cast` | Chiếu cụm | nút | 🚗 |
| `screen_rotation` | Xoay màn hình | nút | 🚗 |
| `camera_view` | Góc camera | nút | ⚠ |

### Động lực/sạc — setter chế độ lái & sạc chưa có bằng chứng trên xe nào (catalog 🚗) — chờ CapTest v2 lấy rc

| id | Nhãn | Loại | Catalog trước |
|---|---|---|---|
| `powertrain_mode` | EV / HEV | nút | ⚠ |
| `regen_level` | Mức tái tạo | nút | 🚗 |
| `target_soc_set` | Mục tiêu sạc | nút | ⚠ |
| `charge_cap` | Giới hạn sạc | nút | 🚗 |
| `target_soc` | Mục tiêu sạc | tin | 🚗 |
| `drift_mode` | Chế độ drift | tin | ⚠ |
| `engine_rpm` | Vòng tua máy xăng | tin | 🚗 |
| `batt_range_bodywork` | Tầm pin (thân xe) | tin | ❌ |
| `batt_temp` | Nhiệt độ pin | tin | 🚗 |
| `cell_v_high` | Áp cell cao | tin | ❌ |
| `cell_v_low` | Áp cell thấp | tin | ❌ |

### Thuộc ADAS-PURGE 🗑 — không sửa nữa

| id | Nhãn | Loại | Catalog trước |
|---|---|---|---|
| `adas_esp` | Cân bằng điện tử (ESP) | nút | 🚗 |
| `adas_fcw` | Cảnh báo va chạm trước | nút | 🚗 |
| `adas_lane` | Hỗ trợ giữ làn | nút | 🚗 |
| `adas_rcta` | Cắt ngang phía sau | nút | 🚗 |
| `adas_slw` | Cảnh báo quá tốc | nút | 🚗 |
| `adas_tsr` | Nhận diện biển báo | nút | 🚗 |
| `avh` | Giữ phanh tự động (AVH) | nút | ⚠ |
| `bsd_fl_alarm` | Điểm mù trước-trái | tin | ⚠ |
| `bsd_fr_alarm` | Điểm mù trước-phải | tin | ⚠ |
| `itac` | iTAC (kiểm soát mô-men) | nút | 🚗 |
| `lca_left` | Chuyển làn trái | tin | 🚗 |
| `lca_right` | Chuyển làn phải | tin | 🚗 |
| `oms_driver` | Nhận diện tài xế | tin | ⚠ |
| `radar_volume` | Âm lượng cảm biến | tin | 🚗 |
| `rcta_left` | Cắt ngang sau trái | tin | 🚗 |
| `rcta_right` | Cắt ngang sau phải | tin | 🚗 |

### Chưa có giả thuyết (12 mục) — cần log rc của lượt chạy (H2 "xuất nhật ký" + CapTest v2 sẽ lấy tự động)

| id | Nhãn | Loại | Mục | Catalog trước |
|---|---|---|---|---|
| `mirror_fold` | Gương chiếu hậu | tin | Thân xe · cửa · kính | 🚗 |
| `sunroof` | Cửa sổ trời | nút | Thân xe · cửa · kính | ⚠ |
| `wiper` | Gạt mưa | nút | Thân xe · cửa · kính | 🚗 |
| `rain_close` | Tự đóng kính khi mưa | nút | Thân xe · cửa · kính | 🚗 |
| `seat_memory` | Nhớ ghế lái | nút | Thân xe · cửa · kính | 🚗 |
| `light_left_turn` | Xi-nhan trái | tin | Đèn | 🚗 |
| `light_right_turn` | Xi-nhan phải | tin | Đèn | 🚗 |
| `light_side` | Đèn hông | tin | Đèn | 🚗 |
| `headl` | Đèn pha | nút | Đèn | ⚠ |
| `headlight_mode` | Chế độ đèn pha | nút | Đèn | ⚠ |
| `key_bluetooth` | Chìa Bluetooth | tin | Danh tính · khoá | 🚗 |
| `engine_code` | Mã máy | tin | Danh tính · khoá | 🚗 |

## 3. Bảng đầy đủ 187 mục (nguyên văn chấm của anh em, đã tra id)

| Mục | Loại | id | Nhãn | S6 | Catalog trước | Ghi chú |
|---|---|---|---|---|---|---|
| Năng lượng & sạc | tin | `soc` | Pin (SOC) | ✅ | 🟢 |  |
| Năng lượng & sạc | tin | `ev_range_km` | Tầm hoạt động EV | ✅ | 🚗 |  |
| Năng lượng & sạc | tin | `fuel_range_km` | Tầm hoạt động xăng | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `fuel_pct` | Mức xăng | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `odometer` | Odo tổng | ✅ | 🟢 |  |
| Năng lượng & sạc | tin | `ev_mileage_km` | Km chạy điện | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `trip_km` | Quãng đường chuyến | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `trip_hours` | Thời gian chuyến | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `trip_kwh` | Điện tiêu thụ chuyến | ✅ | 🚗 |  |
| Năng lượng & sạc | tin | `consumption_50km` | Tiêu thụ 50km | ✅ | 🚗 |  |
| Năng lượng & sạc | tin | `motor_power` | Công suất mô-tơ | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `is_charging` | Đang sạc | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `charge_power` | Công suất sạc | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `charging_pct` | Sạc % | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `charging_eta_hour` | Còn (giờ) | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `charging_eta_min` | Còn (phút) | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `charging_capacity_kwh` | Đã sạc phiên | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `charging_state` | Trạng thái sạc | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `charger_work_state` | Trạng thái bộ sạc | ✅ | ⚠ |  |
| Năng lượng & sạc | tin | `batt_temp` | Nhiệt độ pin | ❌ | 🚗 |  |
| Năng lượng & sạc | tin | `cell_temp_high` | Nhiệt cell cao | ✅ | 🚗 |  |
| Năng lượng & sạc | tin | `cell_temp_low` | Nhiệt cell thấp | ✅ | 🚗 |  |
| Năng lượng & sạc | tin | `cell_temp_avg` | Nhiệt cell TB | ✅ | 🚗 |  |
| Năng lượng & sạc | tin | `cell_v_high` | Áp cell cao | ❌ | ❌ |  |
| Năng lượng & sạc | tin | `cell_v_low` | Áp cell thấp | ❌ | ❌ |  |
| Năng lượng & sạc | tin | `soh_oem` | Sức khoẻ pin (SOH) | ✅ | 🚗 |  |
| Năng lượng & sạc | tin | `target_soc` | Mục tiêu sạc | ❌ | 🚗 |  |
| Năng lượng & sạc | tin | `batt_range_bodywork` | Tầm pin (thân xe) | ❌ | ❌ |  |
| Năng lượng & sạc | nút | `target_soc_set` | Mục tiêu sạc | ❌ | ⚠ |  |
| Năng lượng & sạc | nút | `charge_cap` | Giới hạn sạc | ❌ | 🚗 |  |
| Năng lượng & sạc | nút | `wireless_charge` | Sạc không dây | ✅ | ⚠ |  |
| Năng lượng & sạc | nút | `start_charging` | Sạc ngay | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `speed` | Tốc độ | ✅ | 🚗 |  |
| Động lực & tốc độ | tin | `accel_pct` | Chân ga | ✅ | 🚗 |  |
| Động lực & tốc độ | tin | `brake_pct` | Chân phanh | ✅ | 🚗 |  |
| Động lực & tốc độ | tin | `motor_front_rpm` | Vòng tua mô-tơ trước | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `motor_rear_rpm` | Vòng tua mô-tơ sau | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `motor_front_torque` | Mô-men mô-tơ trước | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `engine_rpm` | Vòng tua máy xăng | ❌ | 🚗 |  |
| Động lực & tốc độ | tin | `steering_deg` | Góc vô-lăng | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `wheel_speed` | Tốc độ bánh | ✅ | 🚗 |  |
| Động lực & tốc độ | tin | `slope_deg` | Độ dốc | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `gear` | Số | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `op_mode` | Chế độ lái | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `energy_mode` | Chế độ năng lượng | ✅ | ⚠ |  |
| Động lực & tốc độ | tin | `drift_mode` | Chế độ drift | ❌ | ⚠ |  |
| Động lực & tốc độ | nút | `drive_mode` | Chế độ lái | ✅ | 🚗 |  |
| Động lực & tốc độ | nút | `powertrain_mode` | EV / HEV | ❌ | ⚠ |  |
| Động lực & tốc độ | nút | `regen_level` | Mức tái tạo | ❌ | 🚗 |  |
| Động lực & tốc độ | nút | `itac` | iTAC (kiểm soát mô-men) | ❌ | 🚗 | 🗑 ADAS-PURGE |
| Động lực & tốc độ | nút | `avh` | Giữ phanh tự động (AVH) | ❌ | ⚠ | 🗑 ADAS-PURGE |
| Khí hậu & không khí | tin | `pm25_level` | Mức bụi mịn | ✅ | ⚠ |  |
| Khí hậu & không khí | tin | `pm25_value` | Bụi mịn PM2.5 | ✅ | ⚠ |  |
| Khí hậu & không khí | tin | `pm25_online` | Cảm biến bụi mịn | ✅ | 🚗 |  |
| Khí hậu & không khí | tin | `cabin_temp` | Nhiệt trong cabin | ❌ | ⚠ |  |
| Khí hậu & không khí | tin | `inside_temp` | Nhiệt cài đặt | ❌ | 🚗 |  |
| Khí hậu & không khí | tin | `ext_temp` | Nhiệt ngoài xe | ✅ | 🚗 |  |
| Khí hậu & không khí | tin | `coolant_temp` | Nhiệt nước làm mát | ❌ | 🚗 |  |
| Khí hậu & không khí | tin | `ac_on` | Điều hoà | ✅ | 🚗 |  |
| Khí hậu & không khí | tin | `ac_wind` | Mức quạt gió | ✅ | ⚠ |  |
| Khí hậu & không khí | tin | `ac_cycle` | Chế độ lấy gió | ✅ | ⚠ |  |
| Khí hậu & không khí | tin | `temp_unit` | Đơn vị nhiệt | ✅ | ⚠ |  |
| Khí hậu & không khí | tin | `anion_state` | Ion âm | ✅ | ⚠ |  |
| Khí hậu & không khí | nút | `pm25` | Lọc bụi | ❌ | 🚗 |  |
| Khí hậu & không khí | nút | `seatc` | Ghế mát | ✅ | 🚗 |  |
| Khí hậu & không khí | nút | `temp` | Nhiệt độ | ✅ | 🚗 |  |
| Khí hậu & không khí | nút | `fan` | Gió | ✅ | 🚗 |  |
| Khí hậu & không khí | nút | `defrost` | Sấy kính | ✅ | 🟢 |  |
| Khí hậu & không khí | nút | `seath` | Ghế sưởi | ✅ | 🚗 |  |
| Khí hậu & không khí | nút | `recirc` | Lấy gió trong | ✅ | 🚗 |  |
| Khí hậu & không khí | nút | `ac_auto` | Điều hoà AUTO | ❌ | ❌ |  |
| Khí hậu & không khí | nút | `defrost_rear` | Sấy kính sau | ❌ | 🚗 |  |
| Khí hậu & không khí | nút | `anion` | Ion âm | ❌ | 🚗 |  |
| Khí hậu & không khí | nút | `steer_heat` | Sưởi vô-lăng | ❌ | 🚗 |  |
| Khí hậu & không khí | nút | `pm25_clean_now` | Lọc ngay | ✅ | 🟢 |  |
| Lốp | tin | `tyre_p_fl` | Áp lốp trước-trái | ✅ | ⚠ |  |
| Lốp | tin | `tyre_p_fr` | Áp lốp trước-phải | ✅ | ⚠ |  |
| Lốp | tin | `tyre_p_rl` | Áp lốp sau-trái | ✅ | ⚠ |  |
| Lốp | tin | `tyre_p_rr` | Áp lốp sau-phải | ✅ | ⚠ |  |
| Lốp | tin | `tyre_t_fl` | Nhiệt lốp trước-trái | ✅ | 🚗 |  |
| Lốp | tin | `tyre_t_fr` | Nhiệt lốp trước-phải | ✅ | 🚗 |  |
| Lốp | tin | `tyre_t_rl` | Nhiệt lốp sau-trái | ✅ | 🚗 |  |
| Lốp | tin | `tyre_t_rr` | Nhiệt lốp sau-phải | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `window_lf` | Kính trước-trái | ✅ | 🟢 |  |
| Thân xe · cửa · kính | tin | `window_rf` | Kính trước-phải | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `window_lr` | Kính sau-trái | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `window_rr` | Kính sau-phải | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `door_lf` | Cửa trước-trái | ✅ | ⚠ |  |
| Thân xe · cửa · kính | tin | `door_rf` | Cửa trước-phải | ✅ | ⚠ |  |
| Thân xe · cửa · kính | tin | `door_lr` | Cửa sau-trái | ✅ | ⚠ |  |
| Thân xe · cửa · kính | tin | `door_rr` | Cửa sau-phải | ✅ | ⚠ |  |
| Thân xe · cửa · kính | tin | `tailgate_status` | Cốp sau | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `tailgate_position` | Vị trí cốp | ❌ | 🚗 |  |
| Thân xe · cửa · kính | tin | `sunroof_state` | Cửa sổ trời | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `sunroof_pos` | Vị trí cửa sổ trời | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `sunshade_pct` | Rèm che nắng | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `mirror_fold` | Gương chiếu hậu | ❌ | 🚗 |  |
| Thân xe · cửa · kính | tin | `wiper_state` | Gạt mưa | ✅ | ⚠ |  |
| Thân xe · cửa · kính | tin | `power_level` | Nguồn xe | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `vehicle_type` | Mẫu xe | ✅ | 🚗 |  |
| Thân xe · cửa · kính | tin | `emergency_alarm` | Cảnh báo khẩn | ✅ | ⚠ |  |
| Thân xe · cửa · kính | nút | `lock` | Khoá / mở khoá | ❌ | ❌ |  |
| Thân xe · cửa · kính | nút | `window` | Kính cửa lái | ✅ | 🚗 |  |
| Thân xe · cửa · kính | nút | `trunk` | Cốp sau | ❌ | ❌ |  |
| Thân xe · cửa · kính | nút | `door` | Mở khoá cửa | ❌ | ❌ |  |
| Thân xe · cửa · kính | nút | `hood` | Ca-pô | ❌ | ❌ |  |
| Thân xe · cửa · kính | nút | `sunroof` | Cửa sổ trời | ❌ | ⚠ |  |
| Thân xe · cửa · kính | nút | `wiper` | Gạt mưa | ❌ | 🚗 |  |
| Thân xe · cửa · kính | nút | `win_lf` | Kính trước-trái | ✅ | 🟢 |  |
| Thân xe · cửa · kính | nút | `win_rf` | Kính trước-phải | ✅ | 🟢 |  |
| Thân xe · cửa · kính | nút | `win_lr` | Kính sau-trái | ✅ | 🟢 |  |
| Thân xe · cửa · kính | nút | `win_rr` | Kính sau-phải | ✅ | 🟢 |  |
| Thân xe · cửa · kính | nút | `windows_all` | Tất cả kính | ✅ | ⚠ |  |
| Thân xe · cửa · kính | nút | `sunshade` | Rèm che nắng | ✅ | ⚠ |  |
| Thân xe · cửa · kính | nút | `child_lock` | Khoá trẻ em | ❌ | ❌ |  |
| Thân xe · cửa · kính | nút | `rain_close` | Tự đóng kính khi mưa | ❌ | 🚗 |  |
| Thân xe · cửa · kính | nút | `mirror_auto` | Gập gương khi khoá | ❌ | 🚗 |  |
| Thân xe · cửa · kính | nút | `mirror_fold_btn` | Gập gương | ❌ | ❌ |  |
| Thân xe · cửa · kính | nút | `seat_memory` | Nhớ ghế lái | ❌ | 🚗 |  |
| Đèn | tin | `light_low_beam` | Đèn cốt | ✅ | ⚠ |  |
| Đèn | tin | `light_high_beam` | Đèn pha | ✅ | ⚠ |  |
| Đèn | tin | `light_front_fog` | Đèn sương mù trước | ✅ | 🚗 |  |
| Đèn | tin | `light_rear_fog` | Đèn sương mù sau | ✅ | 🚗 |  |
| Đèn | tin | `light_left_turn` | Xi-nhan trái | ❌ | 🚗 |  |
| Đèn | tin | `light_right_turn` | Xi-nhan phải | ❌ | 🚗 |  |
| Đèn | tin | `light_side` | Đèn hông | ❌ | 🚗 |  |
| Đèn | tin | `light_drl` | Đèn ban ngày | ✅ | 🚗 |  |
| Đèn | tin | `headlight_feedback` | Chế độ đèn pha | ✅ | 🚗 |  |
| Đèn | tin | `ambient_enabled` | Đèn viền cabin | ❌ | 🚗 |  |
| Đèn | tin | `ambient_front_color` | Màu viền trước | ✅ | ⚠ |  |
| Đèn | tin | `ambient_rear_color` | Màu viền sau | ✅ | ⚠ |  |
| Đèn | tin | `ambient_front_brightness` | Độ sáng viền trước | ✅ | 🚗 |  |
| Đèn | tin | `ambient_rear_brightness` | Độ sáng viền sau | ✅ | 🚗 |  |
| Đèn | nút | `readl` | Đèn đọc | ✅ | ⚠ |  |
| Đèn | nút | `headl` | Đèn pha | ❌ | ⚠ |  |
| Đèn | nút | `drl` | Đèn ban ngày | ✅ | ⚠ |  |
| Đèn | nút | `ambient_power` | Đèn viền cabin | ❌ | ❌ |  |
| Đèn | nút | `ambient_color` | Màu đèn viền | ❌ | 🚗 |  |
| Đèn | nút | `ambient_brightness` | Độ sáng viền | ❌ | 🚗 |  |
| Đèn | nút | `ambient_music` | Đèn viền theo nhạc | ❌ | 🚗 |  |
| Đèn | nút | `headlight_mode` | Chế độ đèn pha | ❌ | ⚠ |  |
| An toàn · ADAS | tin | `seatbelt_driver` | Dây an toàn lái | ✅ | ⚠ | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `seatbelt_passenger` | Dây an toàn phụ | ✅ | ⚠ | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `oms_driver` | Nhận diện tài xế | ❌ | ⚠ | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `oms_passenger` | Nhận diện ghế phụ | ✅ | ⚠ | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `child_presence` | Phát hiện trẻ em | ✅ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `speed_limit_warning` | Cảnh báo quá tốc | ✅ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `bsd_fl_alarm` | Điểm mù trước-trái | ❌ | ⚠ | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `bsd_fr_alarm` | Điểm mù trước-phải | ❌ | ⚠ | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `lca_left` | Chuyển làn trái | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `lca_right` | Chuyển làn phải | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `rcta_left` | Cắt ngang sau trái | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `rcta_right` | Cắt ngang sau phải | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `dow_left` | Mở cửa cảnh báo trái | ✅ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `dow_right` | Mở cửa cảnh báo phải | ✅ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `radar_zones` | Cảm biến đỗ (8 vùng) | ✅ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `radar_volume` | Âm lượng cảm biến | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `esp_state` | Cân bằng điện tử (ESP) | ✅ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | tin | `mcu_status` | Trạng thái nguồn (MCU) | ✅ | 🚗 |  |
| An toàn · ADAS | tin | `volt_12v` | Ắc-quy 12V | ✅ | ⚠ |  |
| An toàn · ADAS | tin | `volt_12v_level` | Mức ắc-quy 12V | ✅ | 🚗 |  |
| An toàn · ADAS | nút | `adas_slw` | Cảnh báo quá tốc | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | nút | `adas_esp` | Cân bằng điện tử (ESP) | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | nút | `adas_tsr` | Nhận diện biển báo | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | nút | `adas_lane` | Hỗ trợ giữ làn | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | nút | `adas_fcw` | Cảnh báo va chạm trước | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | nút | `adas_rcta` | Cắt ngang phía sau | ❌ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | nút | `adas_dow` | Cảnh báo mở cửa | ✅ | 🚗 | 🗑 ADAS-PURGE |
| An toàn · ADAS | nút | `adas_cpd` | Phát hiện trẻ em | ✅ | 🚗 | 🗑 ADAS-PURGE |
| Danh tính · khoá | tin | `vin` | Số VIN | ✅ | 🟢 |  |
| Danh tính · khoá | tin | `key_bluetooth` | Chìa Bluetooth | ❌ | 🚗 |  |
| Danh tính · khoá | tin | `engine_code` | Mã máy | ❌ | 🚗 |  |
| Danh tính · khoá | tin | `engine_coolant_level` | Mức nước làm mát | ❌ | 🚗 |  |
| Danh tính · khoá | tin | `oil_level` | Mức dầu | ✅ | 🚗 |  |
| Danh tính · khoá | tin | `gps_lat` | Vĩ độ | ❌ | ❌ |  |
| Danh tính · khoá | tin | `gps_lon` | Kinh độ | ❌ | ❌ |  |
| Danh tính · khoá | tin | `gps_elevation` | Cao độ | ❌ | ❌ |  |
| Danh tính · khoá | tin | `gps_heading` | Hướng | ❌ | ❌ |  |
| Giải trí · cụm · HUD | nút | `cam` | Camera 360 | ✅ | ⚠ |  |
| Giải trí · cụm · HUD | nút | `vol` | Âm lượng | ✅ | 🚗 |  |
| Giải trí · cụm · HUD | nút | `cast` | Chiếu cụm | ❌ | 🚗 |  |
| Giải trí · cụm · HUD | nút | `screen_rotation` | Xoay màn hình | ❌ | 🚗 |  |
| Giải trí · cụm · HUD | nút | `camera_view` | Góc camera | ❌ | ⚠ |  |
| Giải trí · cụm · HUD | nút | `cluster_music` | Nhạc trên cụm | ✅ | 🚗 |  |
| Giải trí · cụm · HUD | nút | `brightness_gear` | Độ sáng màn | ✅ | 🚗 |  |
| Giải trí · cụm · HUD | nút | `hud_switch` | HUD kính lái | ✅ | 🚗 |  |
| Giải trí · cụm · HUD | nút | `hud_brightness` | Độ sáng HUD | ✅ | 🚗 |  |

## 4. Việc tiếp

1. **Catalog**: thêm cột **S6** vào `docs/catalog/status-by-id.json` (`sealion6: "✅"/"❌"`, evidence = doc này) và cột trong `docs/kachi-feature-catalog.html` — làm ngay sau khi commit (N) để không ghi đè. Luật: status chính vẫn theo **xe owner**; S6 là bằng chứng độc lập, nâng 🚗→"✅ S6" chứ không tự nâng 🟢.
2. 🚗 **Hỏi anh em**: bản Kachi (versionName), và xin thêm `usage-*.log` cùng lượt (thẻ: `Android/data/com.byd.launcher/files/kachi-logs/`) để phân loại 33 mục "chưa có giả thuyết" bằng rc thật.
3. 🚗 **Chiếu cụm hỏng trên S6** — ưu tiên: đưa vào playbook chuyến xe (dump display/stack trên S6) hoặc nhờ anh em chụp màn `Cài đặt › Chiếu màn lên cụm` + `DiagActivity`.
4. CapTest v2 (spec `kachi-captest-v2.html`) — chính là công cụ để lượt sau **tự ghi rc + giá trị trước/sau**, không cần chấm tay.
