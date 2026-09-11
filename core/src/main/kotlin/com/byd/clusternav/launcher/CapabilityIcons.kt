package com.byd.clusternav.launcher

/**
 * ICON THEO KHÁI NIỆM cho các mục ĐỌC (U1) — thuần Kotlin (`:core`) ⇒ test off-car.
 *
 * ## Bệnh nó chữa
 * [ĐO] 2026-09-11: **123 mục đọc chỉ dùng 9 icon** vì icon lấy theo NHÓM ([WidgetCatalog.iconFor]) — cả 28 mục
 * năng lượng đều mang icon tia sét, cả 20 mục an toàn đều mang icon lưới. Trên bảng Tuỳ biến, một hàng 5 ô trông
 * y hệt nhau, icon **không giúp phân biệt gì**, người dùng phải đọc chữ (mà chữ thì bị cắt trong ô nhỏ).
 *
 * ## Cách làm — KHÔNG sửa 123 dòng registry
 * Bảng tra theo **khái niệm** (đo cái gì), không theo nhóm. Cùng lối với lớp đơn vị: thêm một mục vào registry mà
 * chưa khai icon thì nó tự lùi về icon của nhóm — **không bao giờ ra ô trống icon**.
 *
 * Ưu tiên dùng lại icon ĐÃ CÓ; chỉ 6 khái niệm phải vẽ mới (đường/quãng đường · pin · dây an toàn · cảm biến vùng ·
 * vị trí · vô-lăng) vì chúng xuất hiện nhiều mà không có icon nào gần nghĩa.
 */
object CapabilityIcons {

    /**
     * Icon cho một mục ĐỌC. Tra theo thứ tự: khớp CHÍNH XÁC mã → khớp TIỀN TỐ → lùi về icon của nhóm.
     * Lùi về nhóm là **cố ý**: thà trùng icon còn hơn ô trống icon.
     */
    fun forTelemetry(id: String, domain: Domain): String =
        EXACT[id] ?: PREFIX.firstOrNull { id.startsWith(it.first) }?.second ?: WidgetCatalog.iconFor(domain)

    /** Khớp chính xác — cho mục đơn lẻ có khái niệm riêng. */
    private val EXACT: Map<String, String> = mapOf(
        // ── Năng lượng: tách PIN / QUÃNG ĐƯỜNG / NHIỆT / THỜI GIAN thay vì tất cả là tia sét ──
        "soc" to "ic-battery", "soh_oem" to "ic-battery", "target_soc" to "ic-battery",
        "charging_pct" to "ic-battery", "fuel_pct" to "ic-battery",
        "ev_range_km" to "ic-road", "fuel_range_km" to "ic-road", "ev_mileage_km" to "ic-road",
        "odometer" to "ic-road", "trip_km" to "ic-road", "batt_range_bodywork" to "ic-road",
        "trip_hours" to "ic-clock", "charging_eta_hour" to "ic-clock", "charging_eta_min" to "ic-clock",
        "batt_temp" to "ic-temp", "cell_temp_high" to "ic-temp", "cell_temp_low" to "ic-temp",
        "cell_temp_avg" to "ic-temp",
        // ── Động lực ──
        "steering_deg" to "ic-steering",
        "gear" to "ic-grid", "op_mode" to "ic-grid", "energy_mode" to "ic-grid", "drift_mode" to "ic-grid",
        // ── Khí hậu: bụi ≠ nhiệt ≠ quạt ──
        "cabin_temp" to "ic-temp", "inside_temp" to "ic-temp", "ext_temp" to "ic-temp",
        "coolant_temp" to "ic-temp", "temp_unit" to "ic-temp",
        "ac_on" to "ic-fan", "ac_wind" to "ic-fan", "ac_cycle" to "ic-recirc",
        "anion_state" to "ic-leaf",
        // ── Thân xe ──
        "tailgate_status" to "ic-trunk", "tailgate_position" to "ic-trunk",
        "sunroof_state" to "ic-sunroof", "sunroof_pos" to "ic-sunroof", "sunshade_pct" to "ic-sunroof",
        "wiper_state" to "ic-wiper", "mirror_fold" to "ic-swap",
        // ── An toàn ──
        "radar_zones" to "ic-radar", "radar_volume" to "ic-volume",
        "child_presence" to "ic-seat", "oms_driver" to "ic-seat", "oms_passenger" to "ic-seat",
        "speed_limit_warning" to "ic-speed",
        "volt_12v" to "ic-bolt", "volt_12v_level" to "ic-bolt",
        // ── Danh tính ──
        "key_bluetooth" to "ic-lock",
        "oil_level" to "ic-hood", "engine_coolant_level" to "ic-hood", "engine_code" to "ic-hood",
    )

    /**
     * Khớp TIỀN TỐ — cho các họ mục cùng khái niệm (áp suất lốp ×4, nhiệt lốp ×4, kính ×4, cửa ×4…).
     * Thứ tự QUAN TRỌNG: tiền tố dài đứng trước (nhiệt lốp phải khớp trước áp suất lốp).
     */
    private val PREFIX: List<Pair<String, String>> = listOf(
        "tyre_t_" to "ic-temp",          // nhiệt lốp → icon nhiệt (KHÁC áp suất — đó là điểm của việc này)
        "tyre_p_" to "ic-tire",
        "window_" to "ic-window",
        "door_" to "ic-door",
        "seatbelt_" to "ic-seatbelt",
        "light_left_turn" to "ic-turn-left",
        "light_right_turn" to "ic-turn-right",
        "light_" to "ic-light",
        "ambient_" to "ic-readlight",     // đèn viền ≠ đèn ngoài
        "headlight_" to "ic-light",
        "gps_" to "ic-gps",
        "pm25_" to "ic-leaf",
        "bsd_" to "ic-radar", "lca_" to "ic-radar", "rcta_" to "ic-radar", "dow_" to "ic-radar",
        "motor_" to "ic-speed", "engine_rpm" to "ic-speed", "wheel_speed" to "ic-speed",
        "charge" to "ic-bolt", "charging" to "ic-bolt", "is_charging" to "ic-bolt",
        "cell_v_" to "ic-bolt",
        "trip_kwh" to "ic-bolt", "consumption_" to "ic-bolt",
    )

    /**
     * Số icon PHÂN BIỆT được trên toàn bộ mục đọc — dùng cho test đo tiến bộ, và để không ai âm thầm gộp hết về
     * một icon lần nữa.
     */
    fun distinctIconCount(): Int =
        TelemetryRegistry.ALL.map { forTelemetry(it.id, it.domain) }.toSet().size

    /** Các nhóm mà MỌI mục vẫn dùng chung ĐÚNG một icon (icon không giúp phân biệt gì trong nhóm đó). */
    fun domainsWithSingleIcon(): List<Domain> =
        Domain.values().filter { d ->
            val items = TelemetryRegistry.byDomain(d)
            items.size > 3 && items.map { forTelemetry(it.id, d) }.toSet().size == 1
        }
}
