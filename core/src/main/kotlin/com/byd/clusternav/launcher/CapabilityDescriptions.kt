package com.byd.clusternav.launcher

/**
 * Diễn giải một dòng cho MỖI khả năng (thông tin + hành động) — dùng cho công cụ kiểm tra từng nút trên xe
 * (`docs/specs/kachi-capability-test.html`) và cho tài liệu liệt kê. Tách khỏi [ControlRegistry]/[TelemetryRegistry]
 * để KHÔNG đụng phép đếm nhãn tuyệt đối của chúng. Thiếu id ⇒ tầng trên lùi về nhãn.
 */
object CapabilityDescriptions {
    data class Desc(val vi: String, val en: String)

    fun of(id: String): Desc? = MAP[id]

    val MAP: Map<String, Desc> = mapOf(
        // ── ENERGY (INFO) ──
        "soc" to Desc("Phần trăm pin cao áp còn lại", "High-voltage battery charge remaining (%)"),
        "ev_range_km" to Desc("Quãng đường ước tính còn chạy bằng điện", "Estimated remaining driving range on electric power (km)"),
        "fuel_range_km" to Desc("Quãng đường ước tính còn chạy bằng xăng", "Estimated remaining driving range on petrol (km)"),
        "fuel_pct" to Desc("Phần trăm nhiên liệu xăng còn lại", "Petrol fuel level remaining (%)"),
        "odometer" to Desc("Tổng quãng đường xe đã đi", "Total lifetime distance driven (km)"),
        "ev_mileage_km" to Desc("Quãng đường đã chạy bằng điện", "Distance driven on electric power (km)"),
        "trip_km" to Desc("Quãng đường của chuyến đi hiện tại", "Distance covered on the current trip (km)"),
        "trip_hours" to Desc("Thời gian đã đi của chuyến hiện tại", "Elapsed time of the current trip (h)"),
        "trip_kwh" to Desc("Điện năng đã tiêu thụ trong chuyến", "Electricity consumed on the current trip (kWh)"),
        "consumption_50km" to Desc("Mức tiêu thụ điện trung bình 50km gần nhất", "Average power consumption over the last 50km (kWh/100km)"),
        "motor_power" to Desc("Công suất mô-tơ điện đang phát ra", "Electric motor power output (kW)"),
        "batt_temp" to Desc("Nhiệt độ hiện tại của pin cao áp", "Current high-voltage battery temperature (°C)"),
        "cell_temp_high" to Desc("Nhiệt độ cell pin cao nhất", "Highest individual battery cell temperature (°C)"),
        "cell_temp_low" to Desc("Nhiệt độ cell pin thấp nhất", "Lowest individual battery cell temperature (°C)"),
        "cell_temp_avg" to Desc("Nhiệt độ trung bình các cell pin", "Average battery cell temperature (°C)"),
        "cell_v_high" to Desc("Điện áp cell pin cao nhất", "Highest individual battery cell voltage (V)"),
        "cell_v_low" to Desc("Điện áp cell pin thấp nhất", "Lowest individual battery cell voltage (V)"),
        "soh_oem" to Desc("Tình trạng sức khoẻ pin so với lúc mới", "Battery health versus original capacity (%)"),
        "target_soc" to Desc("Mức pin mục tiêu đã đặt cho sạc", "Target charge level configured for charging (%)"),

        // ── DRIVETRAIN (INFO) ──
        "speed" to Desc("Tốc độ di chuyển hiện tại của xe", "Current vehicle road speed (km/h)"),
        "accel_pct" to Desc("Độ nhấn bàn đạp ga hiện tại", "Current accelerator pedal position (%)"),
        "brake_pct" to Desc("Độ nhấn bàn đạp phanh hiện tại", "Current brake pedal position (%)"),
        "motor_front_rpm" to Desc("Tốc độ quay mô-tơ điện trục trước", "Front electric motor rotation speed (rpm)"),
        "motor_rear_rpm" to Desc("Tốc độ quay mô-tơ điện trục sau", "Rear electric motor rotation speed (rpm)"),
        "motor_front_torque" to Desc("Mô-men xoắn mô-tơ điện trục trước", "Front electric motor torque output (Nm)"),
        "engine_rpm" to Desc("Tốc độ quay động cơ xăng", "Petrol engine rotation speed (rpm)"),
        "steering_deg" to Desc("Góc xoay hiện tại của vô-lăng", "Current steering wheel angle (°)"),
        "wheel_speed" to Desc("Tốc độ quay của bánh xe", "Individual wheel rotation speed (km/h)"),
        "slope_deg" to Desc("Độ dốc mặt đường xe đang đi", "Road gradient the car is currently on (°)"),
        "gear" to Desc("Số hiện tại đang cài đặt (P/R/N/D)", "Currently selected gear (P/R/N/D)"),
        "op_mode" to Desc("Chế độ lái đang được kích hoạt", "Currently active drive mode"),
        "energy_mode" to Desc("Chế độ vận hành năng lượng đang dùng (EV/HEV)", "Current powertrain energy mode (EV/HEV)"),

        // ── CLIMATE (INFO) ──
        "pm25_level" to Desc("Mức đánh giá chất lượng không khí trong xe", "Cabin air-quality rating based on fine dust"),
        "pm25_value" to Desc("Nồng độ bụi mịn PM2.5 trong cabin", "PM2.5 fine dust concentration in the cabin (µg/m³)"),
        "pm25_online" to Desc("Cảm biến bụi mịn có đang hoạt động không", "Whether the PM2.5 sensor is online"),
        "cabin_temp" to Desc("Nhiệt độ thực tế đo trong khoang cabin", "Actual measured cabin air temperature (°C)"),
        "inside_temp" to Desc("Nhiệt độ điều hoà đã cài đặt", "A/C target temperature currently set (°C)"),
        "ext_temp" to Desc("Nhiệt độ không khí bên ngoài xe", "Outside ambient air temperature (°C)"),
        "coolant_temp" to Desc("Nhiệt độ nước làm mát động cơ", "Engine coolant temperature (°C)"),
        "ac_on" to Desc("Điều hoà có đang bật hay không", "Whether the air conditioning is currently on"),
        "ac_wind" to Desc("Mức quạt gió điều hoà đang chạy", "Current A/C fan speed level"),
        "ac_cycle" to Desc("Chế độ lấy gió trong/ngoài đang dùng", "Current air recirculation mode (fresh/recirculated)"),
        "temp_unit" to Desc("Đơn vị hiển thị nhiệt độ đang dùng (°C/°F)", "Temperature display unit in use (°C/°F)"),
        "anion_state" to Desc("Chức năng ion âm có đang bật không", "Whether the anion (ioniser) function is active"),
        // H1 · T2 — sáu ô đọc mới; chữ nói đúng thứ getter trả về, kể cả chỗ thang mức còn đang chờ điểm đo thứ hai.
        "seat_vent_state" to Desc("Ghế lái đang thổi mát ở mức mấy", "Driver seat ventilation level in use"),
        "seat_heat_state" to Desc("Ghế lái đang sưởi ở mức mấy", "Driver seat heating level in use"),
        "defrost_front_state" to Desc("Sấy kính trước có đang bật không", "Whether front windscreen defrost is on"),
        "defrost_rear_state" to Desc("Sấy kính sau có đang bật không", "Whether rear windscreen defrost is on"),
        "ac_mode_auto" to Desc("Điều hòa đang ở chế độ AUTO hay chỉnh tay", "Whether the A/C is in AUTO or manual mode"),

        // ── TYRES (INFO) ──
        "tyre_p_fl" to Desc("Áp suất lốp trước bên trái", "Front-left tyre pressure (kPa)"),
        "tyre_p_fr" to Desc("Áp suất lốp trước bên phải", "Front-right tyre pressure (kPa)"),
        "tyre_p_rl" to Desc("Áp suất lốp sau bên trái", "Rear-left tyre pressure (kPa)"),
        "tyre_p_rr" to Desc("Áp suất lốp sau bên phải", "Rear-right tyre pressure (kPa)"),
        "tyre_t_fl" to Desc("Nhiệt độ lốp trước bên trái", "Front-left tyre temperature (°C)"),
        "tyre_t_fr" to Desc("Nhiệt độ lốp trước bên phải", "Front-right tyre temperature (°C)"),
        "tyre_t_rl" to Desc("Nhiệt độ lốp sau bên trái", "Rear-left tyre temperature (°C)"),
        "tyre_t_rr" to Desc("Nhiệt độ lốp sau bên phải", "Rear-right tyre temperature (°C)"),

        // ── BODY (INFO) ──
        "window_lf" to Desc("Độ mở kính cửa trước bên trái", "Front-left window open percentage (%)"),
        "window_rf" to Desc("Độ mở kính cửa trước bên phải", "Front-right window open percentage (%)"),
        "window_lr" to Desc("Độ mở kính cửa sau bên trái", "Rear-left window open percentage (%)"),
        "window_rr" to Desc("Độ mở kính cửa sau bên phải", "Rear-right window open percentage (%)"),
        "door_lf" to Desc("Trạng thái đóng/mở cửa trước bên trái", "Front-left door open/closed state"),
        "door_rf" to Desc("Trạng thái đóng/mở cửa trước bên phải", "Front-right door open/closed state"),
        "door_lr" to Desc("Trạng thái đóng/mở cửa sau bên trái", "Rear-left door open/closed state"),
        "door_rr" to Desc("Trạng thái đóng/mở cửa sau bên phải", "Rear-right door open/closed state"),
        "tailgate_status" to Desc("Trạng thái đóng/mở cốp sau", "Rear tailgate open/closed state"),
        "tailgate_position" to Desc("Vị trí mở hiện tại của cốp sau", "Current tailgate open position (%)"),
        "sunroof_state" to Desc("Trạng thái đóng/mở cửa sổ trời", "Sunroof open/closed state"),
        "sunroof_pos" to Desc("Vị trí mở hiện tại của cửa sổ trời", "Current sunroof open position (%)"),
        "sunshade_pct" to Desc("Vị trí mở hiện tại của rèm che nắng", "Current sunshade open position (%)"),
        "mirror_fold" to Desc("Trạng thái gập/mở của gương chiếu hậu", "Wing mirror folded/unfolded state"),
        "wiper_state" to Desc("Trạng thái hoạt động của gạt mưa", "Windscreen wiper operating state"),
        "power_level" to Desc("Cấp nguồn hiện tại của xe (tắt/ACC/bật máy)", "Current vehicle power level (off/ACC/on)"),
        "vehicle_type" to Desc("Mã model của xe", "Vehicle model identifier"),
        "emergency_alarm" to Desc("Đèn cảnh báo khẩn cấp có đang bật không", "Whether the hazard warning alarm is active"),

        // ── LIGHTS (INFO) ──
        "light_low_beam" to Desc("Đèn cốt có đang bật hay không", "Whether the low-beam headlights are on"),
        "light_high_beam" to Desc("Đèn pha (chiếu xa) có đang bật hay không", "Whether the high-beam headlights are on"),
        "light_front_fog" to Desc("Đèn sương mù trước có đang bật hay không", "Whether the front fog lights are on"),
        "light_rear_fog" to Desc("Đèn sương mù sau có đang bật hay không", "Whether the rear fog light is on"),
        "light_left_turn" to Desc("Xi-nhan trái có đang nháy hay không", "Whether the left turn signal is flashing"),
        "light_right_turn" to Desc("Xi-nhan phải có đang nháy hay không", "Whether the right turn signal is flashing"),
        "light_side" to Desc("Đèn hông (đèn định vị) có đang bật không", "Whether the side marker lights are on"),
        "light_drl" to Desc("Đèn chạy ban ngày (DRL) có đang bật không", "Whether the daytime running lights are on"),
        "headlight_feedback" to Desc("Chế độ đèn pha hiện tại (auto/thủ công…)", "Current headlight mode feedback (auto/manual/…)"),
        "ambient_enabled" to Desc("Đèn viền nội thất có đang bật không", "Whether cabin ambient lighting is on"),
        "ambient_front_color" to Desc("Màu đèn viền nội thất khu vực trước", "Ambient light colour for the front zone"),
        "ambient_rear_color" to Desc("Màu đèn viền nội thất khu vực sau", "Ambient light colour for the rear zone"),
        "ambient_front_brightness" to Desc("Độ sáng đèn viền nội thất khu vực trước", "Ambient light brightness for the front zone"),
        "ambient_rear_brightness" to Desc("Độ sáng đèn viền nội thất khu vực sau", "Ambient light brightness for the rear zone"),

        // ── Điện phụ 12V / nguồn máy (nhóm ADAS/an toàn đã gỡ 2026-09-16) ──
        "volt_12v" to Desc("Điện áp ắc-quy 12V hiện tại", "Current 12V auxiliary battery voltage (V)"),
        "volt_12v_level" to Desc("Mức đánh giá tình trạng ắc-quy 12V", "12V auxiliary battery health level rating"),

        // ── IDENTITY (INFO) ──
        "vin" to Desc("Số khung nhận dạng xe (VIN)", "Vehicle identification number (VIN)"),
        "engine_code" to Desc("Mã định danh động cơ xe", "Engine identification code"),
        "engine_coolant_level" to Desc("Mức nước làm mát động cơ còn lại", "Engine coolant level remaining"),
        "oil_level" to Desc("Phần trăm dầu động cơ còn lại", "Engine oil level remaining (%)"),
        "gps_lat" to Desc("Vĩ độ GPS hiện tại của xe", "Current GPS latitude of the car (°)"),
        "gps_lon" to Desc("Kinh độ GPS hiện tại của xe", "Current GPS longitude of the car (°)"),
        "gps_elevation" to Desc("Độ cao GPS hiện tại của xe", "Current GPS elevation of the car (m)"),
        "gps_heading" to Desc("Hướng di chuyển hiện tại theo la bàn", "Current compass heading of travel (°)"),

        // ── BODY (ACT) ──
        "lock" to Desc("Bật/tắt khoá cửa xe, di chuyển chốt khoá vật lý", "Lock/unlock the car doors (moves the physical latch)"),
        "window" to Desc("Bật/tắt điều khiển kính cửa lái, dịch chuyển kính vật lý", "Turn the driver's window control on/off (moves the glass)"),
        "trunk" to Desc("Bật/tắt mở cốp sau, dịch chuyển cốp vật lý", "Turn the boot/tailgate release on/off (moves the boot)"),
        "door" to Desc("Mở khoá cửa xe ngay lập tức", "Unlock the car doors immediately"),
        "hood" to Desc("Bật/tắt mở ca-pô, dịch chuyển nắp ca-pô vật lý", "Turn the bonnet release on/off (moves the bonnet)"),
        "sunroof" to Desc("Bật/tắt điều khiển cửa sổ trời, dịch chuyển tấm kính", "Turn sunroof control on/off (moves the glass panel)"),
        "wiper" to Desc("Bật/tắt gạt mưa kính chắn gió", "Turn the windscreen wipers on/off"),
        "win_lf" to Desc("Mở/đóng kính cửa trước bên trái", "Open/close the front-left window"),
        "win_rf" to Desc("Mở/đóng kính cửa trước bên phải", "Open/close the front-right window"),
        "win_lr" to Desc("Mở/đóng kính cửa sau bên trái", "Open/close the rear-left window"),
        "win_rr" to Desc("Mở/đóng kính cửa sau bên phải", "Open/close the rear-right window"),
        "windows_all" to Desc("Mở/đóng đồng thời toàn bộ kính cửa xe", "Open/close all windows at once"),
        "sunshade" to Desc("Mở/đóng rèm che nắng cửa sổ trời", "Open/close the sunroof sunshade"),
        "child_lock" to Desc("Bật/tắt khoá trẻ em cho cửa sau", "Turn the rear child-safety lock on/off"),
        "seat_memory" to Desc("Gọi lại vị trí ghế lái đã lưu", "Recall the saved driver seat position"),

        // ── LIGHTS (ACT) ──
        "readl" to Desc("Bật/tắt đèn đọc sách trong cabin", "Turn the cabin reading light on/off"),
        "headl" to Desc("Bật/tắt đèn pha", "Turn the headlights on/off"),
        "drl" to Desc("Bật/tắt đèn chạy ban ngày", "Turn the daytime running lights on/off"),
        "ambient_power" to Desc("Bật/tắt đèn viền nội thất cabin", "Turn cabin ambient lighting on/off"),
        "ambient_color" to Desc("Chọn màu đèn viền nội thất", "Pick the ambient lighting colour"),
        "ambient_brightness" to Desc("Tăng/giảm độ sáng đèn viền nội thất", "Raise/lower the ambient lighting brightness"),
        "ambient_music" to Desc("Bật/tắt đèn viền nhấp nháy theo nhạc", "Turn music-synced ambient lighting on/off"),
        "headlight_mode" to Desc("Chọn chế độ đèn pha (auto/cốt/pha…)", "Pick the headlight mode (auto/low/high beam…)"),

        // ── CLIMATE (ACT) ──
        "pm25" to Desc("Bật/tắt chế độ lọc bụi mịn tự động", "Turn automatic air purification on/off"),
        "seatc" to Desc("Bật/tắt quạt làm mát ghế", "Turn seat ventilation on/off"),
        "temp" to Desc("Tăng/giảm nhiệt độ điều hoà", "Raise/lower the A/C temperature"),
        "fan" to Desc("Tăng/giảm mức quạt gió điều hoà", "Raise/lower the A/C fan speed"),
        "defrost" to Desc("Bật/tắt sấy kính chắn gió trước", "Turn the front windscreen defroster on/off"),
        "seath" to Desc("Bật/tắt sưởi ghế", "Turn seat heating on/off"),
        "recirc" to Desc("Bật/tắt chế độ lấy gió trong xe", "Turn cabin air recirculation on/off"),
        "ac_auto" to Desc("Bật/tắt chế độ điều hoà tự động (AUTO)", "Turn automatic A/C mode on/off"),
        "defrost_rear" to Desc("Bật/tắt sấy kính chắn gió sau", "Turn the rear windscreen defroster on/off"),
        "anion" to Desc("Bật/tắt chức năng ion âm lọc không khí", "Turn the anion air ioniser on/off"),
        "steer_heat" to Desc("Bật/tắt sưởi vô-lăng", "Turn steering wheel heating on/off"),
        "pm25_clean_now" to Desc("Chạy lọc không khí một lần ngay", "Run a one-shot air purification now"),

        // ── INFOTAINMENT (INFO) ──
        "media_vol" to Desc("Âm lượng nhạc/giải trí đang đặt ở mức mấy", "Current media (music) volume level"),

        // ── INFOTAINMENT (ACT) ──
        "cam" to Desc("Bật/tắt hiển thị camera 360 độ", "Turn the 360-degree camera view on/off"),
        "vol" to Desc("Tăng/giảm âm lượng hệ thống giải trí", "Raise/lower the infotainment volume"),
        "cast" to Desc("Bật/tắt chiếu màn hình lên cụm đồng hồ", "Turn screen mirroring to the instrument cluster on/off"),
        "screen_rotation" to Desc("Chọn hướng xoay màn hình trung tâm", "Pick the centre screen rotation orientation"),
        "camera_view" to Desc("Chọn góc nhìn camera hỗ trợ đỗ xe", "Pick the parking camera viewing angle"),
        "cluster_music" to Desc("Bật/tắt hiển thị thông tin nhạc trên cụm đồng hồ", "Turn music info display on the instrument cluster on/off"),
        "brightness_gear" to Desc("Tăng/giảm độ sáng màn hình trung tâm", "Raise/lower the centre screen brightness"),
        "hud_switch" to Desc("Bật/tắt hiển thị HUD trên kính lái", "Turn the head-up display (HUD) on/off"),
        "hud_brightness" to Desc("Tăng/giảm độ sáng hiển thị HUD", "Raise/lower the HUD brightness"),

        // ── DRIVETRAIN (ACT) ──
        "powertrain_mode" to Desc("Chọn chế độ vận hành động cơ (EV/HEV)", "Pick the powertrain mode (EV/HEV)"),
        "regen_level" to Desc("Chọn mức thu hồi năng lượng phanh tái tạo", "Pick the regenerative braking level"),

        // ── ENERGY (ACT) ──
        "wireless_charge" to Desc("Bật/tắt sạc không dây cho điện thoại", "Turn the wireless phone charger on/off"),
    )
}
