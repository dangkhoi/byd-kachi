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
 *
 * ## ĐỢT 2 (U6) — vì sao "9 icon → 34 icon" vẫn CHƯA đủ
 * [ĐO] soát ảnh lưới ngăn kéo 2026-09-12/13: tổng số icon phân biệt được đã lên 34, nhưng **phân bố mới là thứ
 * người dùng nhìn thấy**, không phải tổng. Trong CÙNG một màn cuộn:
 *  • Năng lượng — **9/28** ô cùng tia sét, **6/28** cùng con đường, 4 cùng pin, 4 cùng nhiệt kế;
 *  • Động lực — **6/14** ô cùng đồng hồ tốc, 4 cùng cần số, và **3 ô rpm/mô-men mang hình ĐỌC RA LÀ ẮC-QUY**
 *    (`ic_motor` cũ là hộp bo góc có cực lồi bên phải — xem lời ghi trong chính tệp đó);
 *  • Khí hậu — **5/12** ô cùng nhiệt kế, **4/12** cùng chiếc lá.
 *
 * Luật rút ra và đóng vào bài canh ([CapabilityIconsDiversityTest]): trong một nhóm, **không hình nào được mang quá
 * [MAX_PER_DOMAIN] ô**. Đó là ngưỡng người ta còn quét mắt qua được; quá ngưỡng thì icon thành hoa văn nền.
 * Ngưỡng áp cứng cho ba nhóm U6 chạm tới; các nhóm còn lại ghim TRẦN ĐO ĐƯỢC hôm nay (chỉ được xuống, không được
 * lên) để phần nợ nhìn thấy được thay vì tàng hình.
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
        // U6: PIN chỉ còn nghĩa "trạng thái của gói pin" (mức · sức khoẻ · mức muốn sạc tới). Việc NẠP có hình
        // riêng (pin + tia sét) vì đó là một trạng thái khác hẳn, và trước đây nó lẫn vào cả hai phía.
        "soc" to "ic-battery", "soh_oem" to "ic-battery",
        // MỤC TIÊU sạc là con số MONG MUỐN, không phải trạng thái hiện tại ⇒ vòng ngắm, không phải pin. Nút
        // `target_soc_set` mang CÙNG hình (xem `ControlRegistry`): một khái niệm, một hình.
        "target_soc" to "ic-target",
        "charging_pct" to "ic-battery-charging", "is_charging" to "ic-battery-charging",
        // Công suất tức thời (kW) và lượng đã nạp (kWh) là đại lượng ĐIỆN ⇒ giữ tia sét trần; hai TRẠNG THÁI thì
        // tách hẳn: một cái nói về cổng trên xe, một cái nói về thiết bị sạc bên ngoài.
        "charge_power" to "ic-bolt", "charging_capacity_kwh" to "ic-bolt",
        "charging_state" to "ic-plug", "charger_work_state" to "ic-charger",
        "trip_kwh" to "ic-consumption", "consumption_50km" to "ic-consumption",
        // [KIỂM TOÁN UX mục 4d] Xăng KHÔNG dùng icon PIN: trên xe hybrid đó là hai bình chứa khác nhau.
        "fuel_pct" to "ic-fuel",
        // U6: "còn đi được bao xa" (tầm) ≠ "đã đi được bao xa" (odo/chuyến) — trước đây cả sáu cùng ic-road.
        "ev_range_km" to "ic-range", "fuel_range_km" to "ic-range", "batt_range_bodywork" to "ic-range",
        "odometer" to "ic-road", "ev_mileage_km" to "ic-road", "trip_km" to "ic-road",
        "trip_hours" to "ic-clock", "charging_eta_hour" to "ic-clock", "charging_eta_min" to "ic-clock",
        // U6: nhiệt/áp của CELL tách khỏi nhiệt của cả gói pin — dãy cell + đại lượng, hai hình cùng họ.
        "batt_temp" to "ic-temp",
        "cell_temp_high" to "ic-cell-temp", "cell_temp_low" to "ic-cell-temp", "cell_temp_avg" to "ic-cell-temp",
        "cell_v_high" to "ic-cell-volt", "cell_v_low" to "ic-cell-volt",
        // ── Động lực ──
        "steering_deg" to "ic-steering",
        // U6: bàn đạp và độ dốc trước đây lùi về icon LĨNH VỰC (đồng hồ tốc) ⇒ 6/14 ô cùng một hình.
        "accel_pct" to "ic-pedal", "brake_pct" to "ic-brake", "slope_deg" to "ic-slope",
        // [KIỂM TOÁN UX mục 4a] Bốn mục CHẾ ĐỘ LÁI trước đây tra ra `ic-grid` (⊞) — cùng hình với widget "Bảng tổng
        // hợp" và với kính cửa. `ic-drive` đã có sẵn và nói đúng việc.
        // U6 siết thêm: `ic-drive` VẼ cần số chữ H ⇒ để đúng cho `gear`; ba CHẾ ĐỘ là thứ để chọn nên mang núm chọn.
        "gear" to "ic-drive",
        // ⚠ Ô XEM và NÚT của CÙNG một việc phải mang CÙNG một hình (U6): [ĐO] ảnh 2026-09-13 hai ô đều tên
        // "Drive mode" mà một cái là núm chọn, một cái là cần số — người dùng đọc ra hai việc khác nhau.
        //   • chế độ lái  → núm chọn  (nút `drive_mode` cũng vậy)
        //   • chế độ năng lượng → tia sét (nút `powertrain_mode` "EV / HEV" đã mang tia sét từ trước)
        //   • drift → vệt trượt: khác nghĩa nhất trong ba, và tách nó ra thì hai cái kia vừa đủ trong ngưỡng
        "op_mode" to "ic-mode", "energy_mode" to "ic-bolt", "drift_mode" to "ic-drift",
        // ── Khí hậu: bụi ≠ nhiệt ≠ quạt ──
        // U6: nước làm mát là mạch ĐỘNG CƠ, không phải không khí cabin ⇒ ký hiệu nhiệt-kế-trên-sóng chuẩn táp-lô.
        // U6: nhóm Khí hậu có BỐN thứ đo bằng nhiệt kế (kể cả nút "Nhiệt độ") — cái duy nhất không nói về không
        // khí TRONG XE là nhiệt ngoài trời, nên nó là cái tách ra.
        "cabin_temp" to "ic-temp", "inside_temp" to "ic-temp", "ext_temp" to "ic-temp-out",
        "coolant_temp" to "ic-coolant", "temp_unit" to "ic-temp",
        // U6: LÀM LẠNH (bông tuyết) ≠ QUẠT GIÓ — quạt vẫn chạy khi lạnh đã tắt. Nút `ac_auto` cùng hình với `ac_on`.
        "ac_on" to "ic-ac", "ac_wind" to "ic-fan", "ac_cycle" to "ic-recirc",
        "anion_state" to "ic-leaf",
        // ── Thân xe ──
        // U6: ba mục này trước đây lùi về icon LĨNH VỰC của Thân xe = hình KÍNH CỬA ⇒ "Cảnh báo khẩn" và "Mẫu xe"
        // trông y hệt bốn ô kính. Đây là icon SAI NGHĨA, không chỉ là icon trùng.
        "power_level" to "ic-bolt", "vehicle_type" to "ic-car", "emergency_alarm" to "ic-alert",
        // U7 lượt 2 · [ĐO bài mù Pass 1] hai cặp này trước dùng CHUNG một hình: `sunroof_state` ↔ `sunroof_pos`
        // khác nhau 0 pixel, `tailgate_status` ↔ `tailgate_position` khác 2% — mà chúng nằm KỀ NHAU trong
        // nhóm Thân xe. Nay: ô TRẠNG THÁI = nắp/tấm kín liền khối; ô VỊ TRÍ = hé mở + mũi tên mức.
        "tailgate_status" to "ic-car-top-trunk", "tailgate_position" to "ic-car-top-trunk-pos",
        "sunroof_state" to "ic-car-top-sunroof", "sunroof_pos" to "ic-car-top-sunroof-pos",
        "sunshade_pct" to "ic-car-top-sunshade",
        "wiper_state" to "ic-wiper", "mirror_fold" to "ic-car-top-mirror",
        // U7 · BỐN CỬA và BỐN KÍNH — mã đã mang vị trí (`_lf`/`_rf`/`_lr`/`_rr`), nay HÌNH cũng mang.
        // Cửa vẽ VẠT CỬA MỞ RA NGOÀI thân; kính vẽ THANH KÍNH TRONG vách ⇒ hai họ không lẫn nhau.
        "door_lf" to "ic-car-top-door-lf", "door_rf" to "ic-car-top-door-rf",
        "door_lr" to "ic-car-top-door-lr", "door_rr" to "ic-car-top-door-rr",
        "window_lf" to "ic-car-top-window-lf", "window_rf" to "ic-car-top-window-rf",
        "window_lr" to "ic-car-top-window-lr", "window_rr" to "ic-car-top-window-rr",
        // ── Đèn (U7) — MỖI LOẠI MỘT HÌNH THẬT, không chỉ đổi nhãn (R2) ──
        // Trước U7: 7 đèn ngoài + 5 mục đèn viền dùng chung ĐÚNG HAI hình (bóng đèn · đèn đọc) ⇒ 14 ô
        // cùng một glyph trong một nhóm. Nay mỗi loại có chùm sáng riêng trên khung xe nhìn TỪ TRƯỚC
        // (pha thẳng · cốt chúc xuống · sương mù có vệt sương · ban ngày là dải mảnh · xi-nhan là mũi
        // tên ở đúng góc), còn đèn viền nằm trên khung nhìn TỪ TRÊN vì nó ở trong khoang.
        "light_low_beam" to "ic-car-front-lowbeam", "light_high_beam" to "ic-car-front-highbeam",
        "light_front_fog" to "ic-car-front-fog", "light_rear_fog" to "ic-car-rear-fog",
        "light_left_turn" to "ic-car-front-turn-l", "light_right_turn" to "ic-car-front-turn-r",
        "light_side" to "ic-car-front-sidelight", "light_drl" to "ic-car-front-drl",
        "headlight_feedback" to "ic-car-front-headlight-mode",
        "ambient_enabled" to "ic-car-top-ambient",
        "ambient_front_color" to "ic-car-top-ambient-color-front",
        "ambient_rear_color" to "ic-car-top-ambient-color-rear",
        // Màu vẽ dải LIỀN KHỐI, độ sáng vẽ dải CHIA NẤC — cùng vị trí nhưng khác hình, vì đây là hai
        // đại lượng khác nhau của cùng một dải (R2: khác biệt phải ở HÌNH, không chỉ ở nhãn).
        "ambient_front_brightness" to "ic-car-top-ambient-bright-front",
        "ambient_rear_brightness" to "ic-car-top-ambient-bright-rear",
        // ── An toàn ──
        // U7: 9 cảnh báo vùng trước đây dùng chung MỘT sóng radar. Nay vùng cảm biến được vẽ ĐÚNG CHỖ
        // trên khung xe nhìn từ trên: điểm mù = hai vòng sóng ở hông sau; chuyển làn = mũi tên cong rời
        // thân; cắt ngang sau = mũi tên chạy ngang sau đuôi; mở cửa = vạt cửa + xe tới từ phía sau.
        "bsd_fl_alarm" to "ic-car-top-bsd-l", "bsd_fr_alarm" to "ic-car-top-bsd-r",
        "lca_left" to "ic-car-top-lca-l", "lca_right" to "ic-car-top-lca-r",
        "rcta_left" to "ic-car-top-rcta-l", "rcta_right" to "ic-car-top-rcta-r",
        "dow_left" to "ic-car-top-dow-l", "dow_right" to "ic-car-top-dow-r",
        "seatbelt_driver" to "ic-car-top-belt-fl", "seatbelt_passenger" to "ic-car-top-belt-fr",
        "radar_zones" to "ic-car-top-park-all", "radar_volume" to "ic-volume",
        // Ghế nào có người: vẽ NGƯỜI (đầu + vai nhìn từ trên) ở đúng ghế, không vẽ cái ghế —
        // ba mục này trước đây cùng một hình ghế nên không nói được "ai đang ngồi đâu".
        "child_presence" to "ic-car-top-occupant-rear",
        "oms_driver" to "ic-car-top-occupant-fl", "oms_passenger" to "ic-car-top-occupant-fr",
        "speed_limit_warning" to "ic-speed",
        // ESP có ký hiệu chuẩn trên táp-lô; trước đây nó lùi về icon nhóm AN TOÀN = hình lưới ⊞ (mục 4a).
        "esp_state" to "ic-esp",
        "volt_12v" to "ic-bolt", "volt_12v_level" to "ic-bolt",
        // ── Lốp (U7) — vị trí bánh nằm trong mã, nay nằm cả trong hình ──
        // Áp suất: ba bánh kia là NÉT, bánh đang nói tới TÔ ĐẶC. Nhiệt: bánh đó TÔ + nhiệt kế giữa xe.
        "tyre_p_fl" to "ic-car-top-tyre-fl", "tyre_p_fr" to "ic-car-top-tyre-fr",
        "tyre_p_rl" to "ic-car-top-tyre-rl", "tyre_p_rr" to "ic-car-top-tyre-rr",
        "tyre_t_fl" to "ic-car-top-tyre-temp-fl", "tyre_t_fr" to "ic-car-top-tyre-temp-fr",
        "tyre_t_rl" to "ic-car-top-tyre-temp-rl", "tyre_t_rr" to "ic-car-top-tyre-temp-rr",
        // ── Danh tính ──
        // U7: bốn mục GPS trước đây cùng một hình ghim vị trí. Chúng là bốn ĐẠI LƯỢNG khác nhau nên
        // tách theo đúng thứ chúng đo: vĩ tuyến (ngang) · kinh tuyến (dọc) · cao độ (núi + thước) ·
        // hướng (kim la bàn). Đây KHÔNG phải nhóm "nằm trên xe" nên giữ glyph trừu tượng (OQ1).
        "gps_lat" to "ic-gps-lat", "gps_lon" to "ic-gps-lon",
        "gps_elevation" to "ic-gps-alt", "gps_heading" to "ic-gps-heading",
        "key_bluetooth" to "ic-lock",
        "oil_level" to "ic-hood", "engine_coolant_level" to "ic-hood", "engine_code" to "ic-hood",
    )

    /**
     * Khớp TIỀN TỐ — cho các họ mục cùng khái niệm (áp suất lốp ×4, nhiệt lốp ×4, kính ×4, cửa ×4…).
     * Thứ tự QUAN TRỌNG: tiền tố dài đứng trước (nhiệt lốp phải khớp trước áp suất lốp).
     */
    private val PREFIX: List<Pair<String, String>> = listOf(
        // ⚠ U7 GỠ 15 TIỀN TỐ Ở ĐÂY (lốp · kính · cửa · dây an toàn · đèn · đèn viền · GPS · ADAS).
        // Tiền tố là công cụ để nói "cả HỌ này cùng một hình" — đúng khi khác biệt nằm ở KHÁI NIỆM.
        // Nhưng ở các họ đó khác biệt nằm ở VỊ TRÍ, nên gộp theo tiền tố chính là thứ tạo ra "14 ô
        // cùng một bóng đèn". Nay mỗi mã tra thẳng ra hình mang đúng vị trí của nó (bảng EXACT ở trên).
        // U6: bụi mịn là thứ được ĐO, chiếc lá là thứ đang LÀM (lọc/ion) — trước đây cả bốn mục cùng chiếc lá.
        // `pm25_online` nói về THIẾT BỊ (cảm biến còn sống không) nên nó tách khỏi cả hai, khớp TRƯỚC tiền tố chung.
        "pm25_online" to "ic-sensor", "pm25_" to "ic-dust",
        // [KIỂM TOÁN UX mục 4d] Công suất mô-tơ KHÔNG phải tốc độ ⇒ không dùng icon đồng hồ tốc.
        // U6: và vòng tua / mô-men KHÔNG phải công suất — ba đại lượng khác nhau của cùng một mô-tơ. Tiền tố dài
        // đứng trước tiền tố ngắn (`motor_front_rpm` phải khớp trước `motor_`), cùng luật đã dùng cho lốp.
        "motor_front_rpm" to "ic-rpm", "motor_rear_rpm" to "ic-rpm", "motor_front_torque" to "ic-torque",
        "motor_" to "ic-motor",
        // Máy XĂNG có hình riêng: trên DM-i hai vòng tua nằm cạnh nhau, cùng hình là không đọc ra cái nào của cái gì.
        "engine_rpm" to "ic-engine", "wheel_speed" to "ic-speed",
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

    /**
     * TRẦN (U6): số ô tối đa được phép dùng chung MỘT hình **trong cùng một nhóm**.
     *
     * Vì sao là một con số chứ không phải "càng ít càng tốt": người dùng cuộn qua một nhóm như cuộn qua một trang —
     * ba ô cùng hình thì mắt còn tách ra được bằng vị trí, quá ba thì hình thành hoa văn nền và người ta quay lại
     * đọc chữ trong ô 40dp (mà chữ bị cắt — đúng bệnh U1 sinh ra để chữa). Số này là **ràng buộc hình**, không phải
     * văn phong, nên nó nằm cạnh bảng tra chứ không nằm trong bài test.
     */
    const val MAX_PER_DOMAIN = 3

    // ⚠ KHÔNG thêm hàm "đếm icon theo nhóm" ở đây: phép đếm phải chạy trên [CapabilityCatalog.all] (ô XEM **và**
    // ô BẤM, đã lọc mã ẩn) vì đó mới là thứ bày ra màn hình — đếm trên `TelemetryRegistry` bỏ sót đúng hàng bốn NÚT
    // sạc cùng một tia sét mà owner soi ra. Phép đếm nằm ở [CapabilityIconsDiversityTest.shownIconUse].
}
