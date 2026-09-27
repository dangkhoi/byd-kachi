package com.byd.clusternav.launcher

/**
 * ICON THEO KHÁI NIỆM cho các mục ĐỌC (U1) — thuần Kotlin (`:core`) ⇒ test off-car.
 *
 * ## Bệnh nó chữa
 * [ĐO] 2026-09-11: **123 mục đọc chỉ dùng 9 icon** vì icon lấy theo NHÓM ([WidgetCatalog.iconFor]) — cả 28 mục
 * năng lượng đều mang icon tia sét. Trên bảng Tuỳ biến, một hàng 5 ô trông
 * y hệt nhau, icon **không giúp phân biệt gì**, người dùng phải đọc chữ (mà chữ thì bị cắt trong ô nhỏ).
 *
 * ## Cách làm — KHÔNG sửa từng dòng registry
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

    // ═══ UX8 · HÌNH THEO TRẠNG THÁI — datum HAI CHẾ ĐỘ nói trạng thái bằng HÌNH, không bằng CHỮ ═══════════════

    /**
     * Bộ hình của MỘT datum hai chế độ: một hình cho mỗi mã trạng thái, cộng một hình cho lúc **chưa đọc được**.
     *
     * @property icons mã trạng thái ([TelemetryView.state]) → tên hình. Mã đánh từ 0, đúng thứ tự *"số nhỏ của
     *   khung là chế độ nào"* đã [ĐO] và ghi ở bảng đọc (`TelemetryReadout.stateTable`).
     * @property unknown hình lúc chưa đọc được. **Không được** trùng hình của một trạng thái *đáng báo*: một hình
     *   vẽ sẵn lúc chưa biết là một lời khẳng định mà không ai đo được (luật *"không biết ≠ đang tắt"* của
     *   [ChipTone]). Trùng với trạng thái **lành** thì được — đó đúng là cách datum bật/tắt đang làm (cùng hình,
     *   khác sắc thái), và chỗ nào dùng [forState] cũng phải trả sắc thái trung tính cho ca này.
     */
    class StateIcons internal constructor(val icons: Map<Int, String>, val unknown: String)

    /**
     * ═══ Bệnh nó chữa (owner 2026-09-27, nhìn thanh trên xe thật) ═══════════════════════════════════════════
     *
     * *"chip header → chế độ lấy gió đổi icon trong / ngoài, bỏ chữ trong / ngoài đi chứ; check xem còn chip nào
     * vẫn còn missing như thế này, mình có làm cái này 1 lần rồi mà?"*
     *
     * Lượt *"làm 1 lần rồi"* là `docs/specs/kachi-datum-icon-consistency.html` (2026-09-21/22), và nó phủ ĐÚNG hai
     * họ: datum **BẬT/TẮT** (R3 — một hình, sáng/mờ theo [TelemetryView.onOff]) và datum **THANG MỨC** (R4 — một
     * hình + số/chấm mức). Khe lọt là họ thứ ba: datum **HAI CHẾ ĐỘ**, nơi **không chế độ nào là "tắt"** — *lấy
     * gió trong* ↔ *lấy gió ngoài* đều là trạng thái đang chạy, nên sáng/mờ không nói được cái nào, và chip đành
     * quay về in CHỮ (`"Chế độ lấy gió · Trong"`) — đúng thứ owner đã gạch bỏ từ 09-21.
     *
     * Chữa bằng DỮ LIỆU, không bằng nhánh theo mã (CLAUDE.md §7): datum nào khai bảng này thì mọi bề mặt tự bỏ
     * chữ trạng thái và đổi hình; không khai thì đi đúng đường cũ. Thêm datum thứ ba chỉ là thêm một dòng.
     *
     * ⚠ **Không dùng cho datum BẬT/TẮT hay THANG MỨC** — hai họ ấy đã có luật riêng và tốt hơn (một hình quen
     * thuộc + sắc thái/mức). Khai chồng là để hai luật cùng nói về một ô. Có bài canh: `TopStripStateIconTest`.
     */
    private val STATE: Map<String, StateIcons> = mapOf(
        // LẤY GIÓ: [ĐO] `BYDAutoAcDevice.java:33-34` INLOOP=1 (trong) · OUTLOOP=0 (ngoài) — xem `ControlRegistry.recirc`.
        // Chế độ *trong* dùng lại đúng hình sẵn có của nút "Lấy gió trong" (`ic-recirc`) ⇒ chip · ô nút · bộ chọn
        // vẫn một hình cho một khái niệm (spec kachi-datum-icon-consistency R1). Chế độ *ngoài* là hình MỚI vẽ
        // thành CẶP với nó (cùng khoang xe, khác đường gió). Chưa đọc ⇒ khoang xe trống, không có đầu mũi tên.
        "ac_cycle" to StateIcons(mapOf(0 to "ic-air-fresh", 1 to "ic-recirc"), unknown = "ic-air-intake"),
        // CẢM BIẾN BỤI MỊN còn sống không (`getPM2p5OnlineState`). Đây là datum LIVENESS, không phải công tắc:
        // trạng thái **đáng báo** chỉ có một (chết) nên nó là cái mang hình riêng (gạch chéo); *"còn sống"* và
        // *"chưa đọc"* dùng chung hình cảm biến và phân biệt bằng sắc thái — đúng cách datum bật/tắt đang làm.
        "pm25_online" to StateIcons(mapOf(0 to "ic-sensor-off", 1 to "ic-sensor"), unknown = "ic-sensor"),
        // ═══ 2.76 (R8) · CỬA ×4 — cặp hình MỞ / ĐÓNG trên cùng khung xe nhìn từ trên, ĐÚNG GÓC xe ══════════════
        // MỞ (mã 1) dùng lại chính hình khái niệm của datum (`ic-car-top-door-*`: vạt cửa xoè ra ngoài thân — nó
        // vốn đã vẽ một cửa ĐANG MỞ) ⇒ vẫn một hình cho một khái niệm (R1). ĐÓNG (mã 0) là biến thể MỚI của cùng
        // bộ sinh `gen-car.py`: vạch cửa nằm SÁT thân, không có vạt. Chưa đọc ⇒ dùng hình ĐÓNG (trạng thái LÀNH,
        // khác nhau ở sắc thái) — không được dùng hình MỞ vì mở là trạng thái ĐÁNG BÁO (GroupBoard xếp ALERT).
        "door_lf" to StateIcons(mapOf(0 to "ic-car-top-door-lf-shut", 1 to "ic-car-top-door-lf"), unknown = "ic-car-top-door-lf-shut"),
        "door_rf" to StateIcons(mapOf(0 to "ic-car-top-door-rf-shut", 1 to "ic-car-top-door-rf"), unknown = "ic-car-top-door-rf-shut"),
        "door_lr" to StateIcons(mapOf(0 to "ic-car-top-door-lr-shut", 1 to "ic-car-top-door-lr"), unknown = "ic-car-top-door-lr-shut"),
        "door_rr" to StateIcons(mapOf(0 to "ic-car-top-door-rr-shut", 1 to "ic-car-top-door-rr"), unknown = "ic-car-top-door-rr-shut"),
        // CỬA SỔ TRỜI — ĐÓNG (mã 0) = hình khái niệm sẵn có (ô nóc tô đặc, kính kín ô); MỞ (mã 1) = ô nóc NÉT + khe
        // hở TÔ ở mép trước (kính đã trượt), KHÔNG mũi tên (mũi tên là "vị trí/hành động" của `sunroof_pos` đã gỡ).
        // [ĐO 4 lượt quét xe owner] `getSunroofState = 0` ⇒ trên xe ấy chip ra hình ĐÓNG **và SÁNG** (đã đọc được,
        // `TopStripChips` cho ACTIVE) — KHÔNG phải "trung tính" như chú thích cũ nói; con số 65535 của [ĐO 09-25] là
        // của `getSunroofPosition` (datum `sunroof_pos` đã gỡ), không phải của getter này. Hình MỞ là generic,
        // [CHƯA BIẾT] trên xe owner (🚗 `chips-icons-close.md` §5 có phép đo thật cho chip này).
        "sunroof_state" to StateIcons(mapOf(0 to "ic-car-top-sunroof", 1 to "ic-car-top-sunroof-open"), unknown = "ic-car-top-sunroof"),
        // ═══ 2.76 (R9) · TỰ ĐỘNG / CHỈNH TAY — chip icon hai trạng thái, KHÔNG chữ đuôi ═════════════════════════
        // Mã 0 = AUTO (`AC_CTRLMODE_AUTO` · `AC_WINDLEVEL_MANUAL_SIGN_OFF`, xem `TelemetryReadout.stateTable`).
        // AUTO = núm chọn mang chữ **A** (`ic-mode-auto`, glyph MỚI); TAY = chính núm chọn có kim (`ic-mode`, hình
        // khái niệm sẵn có của `ac_mode_auto` — người ta xoay kim tới nấc = đang tự chọn). Chưa đọc ⇒ núm có kim,
        // sắc thái trung tính (tay không phải trạng thái đáng báo). Hai datum dùng CHUNG cặp vì chúng hỏi cùng một
        // câu (*"tự động hay tay"*) — một khái niệm, một cặp hình (R1); `ac_wind_auto` còn bị ẨN khỏi bộ chọn chip
        // ([TopStripConfig.CHIP_HIDDEN]) vì chip Gió đã nói "auto n", nên cặp này thực tế chỉ hiện ở `ac_mode_auto`.
        "ac_mode_auto" to StateIcons(mapOf(0 to "ic-mode-auto", 1 to "ic-mode"), unknown = "ic-mode"),
        "ac_wind_auto" to StateIcons(mapOf(0 to "ic-mode-auto", 1 to "ic-mode"), unknown = "ic-mode"),
    )

    /** Datum này có khai hình theo trạng thái không ⇒ bề mặt hẹp bỏ CHỮ trạng thái, chỉ còn nhãn + hình. */
    fun hasStateIcons(id: String): Boolean = id in STATE

    /**
     * Hình của [id] ở trạng thái [state] ([TelemetryView.state]); `state == null` (chưa đọc) ⇒ hình trung tính.
     * `null` = datum không khai hình theo trạng thái ⇒ chỗ gọi dùng đường hình thường.
     *
     * Mã lạ (ngoài bảng) cũng về hình trung tính — thà nói *"chưa biết"* còn hơn vẽ bừa một chế độ.
     */
    fun forState(id: String, state: Int?): String? =
        STATE[id]?.let { s -> state?.let { s.icons[it] } ?: s.unknown }

    /** Bảng hình-theo-trạng-thái, cho bài canh đọc (không có chỗ dùng nào khác ở mã chạy). */
    fun stateIconTable(): Map<String, StateIcons> = STATE

    // ═══ 2.76 L7 · HÌNH THEO MỨC — datum THANG MỨC nói MỨC bằng SỐ DẤU trong hình, không bằng con số ═══════════════

    /**
     * Các hình của MỘT họ glyph theo mức: [byLevel]`[0]` = mức 1, `[1]` = mức 2 … Hình **khái niệm** của họ (tên
     * khoá của bảng) chính là hình mức CAO NHẤT — nên ô chọn / ô nút đang tắt (mờ) vẫn mang đủ dấu để nhận ra *sưởi*
     * hay *mát*, và mức tối đa không cần một tệp thứ ba y hệt.
     */
    class LevelIcons internal constructor(val byLevel: List<String>)

    /**
     * ═══ Bệnh nó chữa (owner 2026-09-27, câu cuối trước khi đi một tháng) ═══════════════════════════════════════
     *
     * *"cái icon sưởi / mát ghế, có thể làm icon kiểu 1 bông tuyết, 2 bông tuyết, 1 sưởi, 2 sưởi trên icon luôn mà
     * không cần số 1-2 cho đẹp được thì làm luôn nhé"*
     *
     * Họ THANG MỨC (spec kachi-datum-icon-consistency R4) tới 2.75 nói mức bằng **một hình + con số** (`"Ghế lái · 2"`)
     * hoặc hàng chấm. Nay mức nằm **trong hình**: một làn nhiệt / một bông tuyết = mức 1, hai = mức 2 (glyph
     * `design/glyph/seat_{heat,vent}_{left,right}[_1].svg`, sinh qua `gen-icons.py`, [ĐO] nhìn ở 24 px: `'`↔`"`,
     * `*`↔`**` không lẫn được — `docs/diagnostics/offcar-2026-09-27/seat-level-glyphs-mirror.md`).
     *
     * ## Vì sao khoá theo TÊN HÌNH khái niệm, không theo mã datum/nút
     * Chip (datum `seat_heat_state`) và ô nút (`seath`) đã cùng cầm **một** tên hình khái niệm (`ControlDef.icon`,
     * qua [CapabilityDots.iconOverride] cho datum). Bảng theo tên hình thì cả hai bề mặt tra cùng một dòng, không phải
     * dựng thêm một phép đảo mã datum → mã nút — đúng luật *một hình cho một khái niệm* (R1). Thêm một họ mức mới =
     * vẽ glyph + một dòng ở đây.
     *
     * Mức **0** (tắt) và **chưa đọc** không có hình riêng: chỗ gọi giữ hình khái niệm + sắc thái (mờ / trung tính),
     * y như datum bật/tắt. Mức **ngoài bảng** (vd `seath` [SUY] còn nấc 3) ⇒ `null` ⇒ chip **in lại con số** như
     * trước — thà nói *"3"* còn hơn vẽ hai dấu cho một mức chưa đo (CLAUDE.md §2).
     */
    private val LEVEL: Map<String, LevelIcons> = mapOf(
        "ic-seat-heat-left" to LevelIcons(listOf("ic-seat-heat-left-1", "ic-seat-heat-left")),
        "ic-seat-heat-right" to LevelIcons(listOf("ic-seat-heat-right-1", "ic-seat-heat-right")),
        "ic-seat-vent-left" to LevelIcons(listOf("ic-seat-vent-left-1", "ic-seat-vent-left")),
        "ic-seat-vent-right" to LevelIcons(listOf("ic-seat-vent-right-1", "ic-seat-vent-right")),
    )

    /**
     * Hình của họ [icon] ở mức [level] (1..n) — `null` khi: hình không khai họ mức · [level] `null`/`0` (chưa đọc / tắt
     * ⇒ chỗ gọi giữ hình khái niệm + sắc thái) · mức vượt bảng (chỗ gọi in số như cũ). Xem KDoc [LEVEL].
     */
    fun forLevel(icon: String?, level: Int?): String? =
        if (icon == null || level == null || level < 1) null else LEVEL[icon]?.byLevel?.getOrNull(level - 1)

    /** Bảng hình-theo-mức, cho bài canh đọc (không có chỗ dùng nào khác ở mã chạy). */
    fun levelIconTable(): Map<String, LevelIcons> = LEVEL

    /** Khớp chính xác — cho mục đơn lẻ có khái niệm riêng. */
    private val EXACT: Map<String, String> = mapOf(
        // ── Năng lượng: tách PIN / QUÃNG ĐƯỜNG / NHIỆT / THỜI GIAN thay vì tất cả là tia sét ──
        // U6: PIN chỉ còn nghĩa "trạng thái của gói pin" (mức · sức khoẻ · mức muốn sạc tới). Việc NẠP có hình
        // riêng (pin + tia sét) vì đó là một trạng thái khác hẳn, và trước đây nó lẫn vào cả hai phía.
        "soc" to "ic-battery", "soh_oem" to "ic-battery",
        // ⚠ 2026-09-25: `target_soc` (ic-target) · `trip_kwh` · `ev_mileage_km` · `batt_temp` (ic-temp) đã gỡ cùng
        // bảy datum chết — nhật ký ở `TelemetryRegistry`. `ic-target`/`ic-temp` vẫn ở `KachiTheme.iconRes` (icon
        // SINH, xoá tệp là làm `IconStyleContractTest` lệch byte) nhưng không còn mã nào tra tới chúng.
        "consumption_50km" to "ic-consumption",
        // [KIỂM TOÁN UX mục 4d] Xăng KHÔNG dùng icon PIN: trên xe hybrid đó là hai bình chứa khác nhau.
        "fuel_pct" to "ic-fuel",
        // U6: "còn đi được bao xa" (tầm) ≠ "đã đi được bao xa" (odo/chuyến) — trước đây cả sáu cùng ic-road.
        "ev_range_km" to "ic-range", "fuel_range_km" to "ic-range",
        "odometer" to "ic-road", "trip_km" to "ic-road",
        "trip_hours" to "ic-clock",
        // ── Động lực ──
        // U6: bàn đạp và độ dốc trước đây lùi về icon LĨNH VỰC (đồng hồ tốc) ⇒ 6/14 ô cùng một hình.
        // [KIỂM TOÁN UX mục 4a] Bốn mục CHẾ ĐỘ LÁI trước đây tra ra `ic-grid` (⊞) — cùng hình với widget "Bảng tổng
        // hợp" và với kính cửa. `ic-drive` đã có sẵn và nói đúng việc.
        // U6 siết thêm: `ic-drive` VẼ cần số chữ H ⇒ để đúng cho `gear`; ba CHẾ ĐỘ là thứ để chọn nên mang núm chọn.
        "gear" to "ic-drive",
        // ⚠ Ô XEM và NÚT của CÙNG một việc phải mang CÙNG một hình (U6): [ĐO] ảnh 2026-09-13 hai ô đều tên
        // "Drive mode" mà một cái là núm chọn, một cái là cần số — người dùng đọc ra hai việc khác nhau.
        //   • chế độ lái  → núm chọn
        //   • chế độ năng lượng → tia sét (nút `powertrain_mode` "EV / HEV" đã mang tia sét từ trước)
        // ⚠ (V) FEATURE-FILTER 2026-09-17: nút `drive_mode` và ô `drift_mode` đã xoá.
        // ⚠⚠ 1.90 · `op_mode` và `energy_mode` cũng xoá (owner 2026-09-21 — xe thuần điện) ⇒ lĩnh vực Động lực
        // nay KHÔNG còn mục nào mang hình núm chọn; `ic-mode` chỉ còn phục vụ nhóm Khí hậu (`ac_mode_auto` ·
        // `ac_wind_auto`) — xem hai chỗ dùng bên dưới.
        // ── Khí hậu: bụi ≠ nhiệt ≠ quạt ──
        // U6: nước làm mát là mạch ĐỘNG CƠ, không phải không khí cabin ⇒ ký hiệu nhiệt-kế-trên-sóng chuẩn táp-lô.
        // U6: nhóm Khí hậu có BỐN thứ đo bằng nhiệt kế (kể cả nút "Nhiệt độ") — cái duy nhất không nói về không
        // khí TRONG XE là nhiệt ngoài trời, nên nó là cái tách ra.
        "cabin_temp" to "ic-temp", "inside_temp" to "ic-temp", "ext_temp" to "ic-temp-out",
        // U6: LÀM LẠNH (bông tuyết) ≠ QUẠT GIÓ — quạt vẫn chạy khi lạnh đã tắt. Nút `ac_auto` cùng hình với `ac_on`.
        "ac_on" to "ic-ac", "ac_wind" to "ic-fan", "ac_cycle" to "ic-recirc",
        "anion_state" to "ic-leaf",
        // ═══ UX5 (2026-09-26) — GLYPH GHÉP *"ghế + dấu phương thức"* cho cả ô ĐỌC lẫn ô BẤM ══════════════════════
        //
        // [ĐO đọc mã] Trước UX5: bốn nút ghế chia nhau **hai** hình (`ic-seat-left` cho ghế lái, `ic-seat` cho ghế
        // phụ) ⇒ **sưởi và mát trông y hệt nhau**; hai datum mức thì lại mang hình thứ ba/thứ tư (`ic-car-top-seat-fl`
        // = ghế nhìn từ trên · `ic-sun` = mặt trời) ⇒ cùng một khái niệm mà chip và nút vẽ hai thứ khác nhau. Trên
        // chip thanh trên — bề mặt chỉ có **một glyph + một chuỗi** — không còn gì để phân biệt sưởi với mát.
        //
        // Nay bốn tên mới, mỗi tên là một glyph GHÉP sẵn: thân ghế (chép từ `seat.svg`, thu nhỏ) + **một** dấu ở góc
        // trống — làn nhiệt của `defrost.svg` cho SƯỞI, bông tuyết của `ac.svg` cho MÁT. Hai mô-típ ấy đã là "chữ"
        // mà launcher dùng cho nóng/lạnh nên người dùng không phải học hình mới.
        //
        // ## Vì sao ghép ở tầng GLYPH, KHÔNG ghép hai drawable lúc chạy
        // `ChipView` mang đúng một tên icon và `setCompoundDrawablesRelative` chỉ có khe start/top/end/bottom ⇒ dấu
        // phương thức sẽ rơi **sau** con số (sai thứ tự owner xin); muốn đúng thứ tự thì phải dựng `LayerDrawable`
        // mỗi lượt vẽ — tức trả lại đúng khoản cấp-phát-trong-tick mà bản vá P2-9 vừa dọn. Glyph ghép thì được đo
        // (`gen-icons.py` canh trần path + ô quang học), được sinh lại, được so byte.
        //
        // Phép đếm hình của nhóm Khí hậu **không đổi**: 4 tên trước → 4 tên sau (`ic-seat-vent-left` ×2 vì datum mức
        // dùng chung hình với chính nút của nó — đó là điều [CapabilityDots.iconOverride] sinh ra để bảo đảm).
        "seat_vent_state" to "ic-seat-vent-left", "seat_heat_state" to "ic-seat-heat-left",
        // UX5b (owner 2026-09-27) — hai datum ghế PHỤ mang **bản `-right`** của cùng cặp glyph ghép, đúng quy ước
        // cạnh đã có. Vẫn là *"datum mức dùng chung hình với chính nút của nó"* nên phép đếm hình của nhóm Khí hậu
        // chỉ có thêm hai lần dùng LẠI, không thêm tên mới.
        "seat_vent_state_r" to "ic-seat-vent-right", "seat_heat_state_r" to "ic-seat-heat-right",
        "defrost_front_state" to "ic-defrost", "defrost_rear_state" to "ic-car-rear-defrost",
        // *"Chế độ"* — cùng hình với ô CHẾ ĐỘ khác của xe (`op_mode`), khác hẳn hình `ic-ac`
        // của ô *"Điều hòa"* bật/tắt: hai câu hỏi khác nhau (đang AUTO hay tay ≠ lạnh đang chạy hay không).
        "ac_mode_auto" to "ic-mode",
        // 1.85 · chỉ báo **gió** auto. Dùng "ic-mode" (cùng hình với `ac_mode_auto`) chứ KHÔNG "ic-fan": [ĐO]
        // `CapabilityIconsDiversityTest` đỏ ngay khi thử — CLIMATE đã có ba ô mang hình quạt (`fan` · `ac_wind` ·
        // nút `ac_auto`) và ô thứ tư làm icon thành hoa văn nền. Cả hai ô này trả lời cùng một dạng câu hỏi
        // (*"đang tự động hay chỉnh tay"*) nên chung hình là đúng nghĩa, không phải nhượng bộ cho bài canh.
        "ac_wind_auto" to "ic-mode",
        // A9 — âm lượng dùng chung hình với nút `vol` (cùng MỘT việc: xem và chỉnh).
        "media_vol" to "ic-volume",
        // ── Thân xe ──
        // U6: ba mục này trước đây lùi về icon LĨNH VỰC của Thân xe = hình KÍNH CỬA ⇒ "Cảnh báo khẩn" và "Mẫu xe"
        // trông y hệt bốn ô kính. Đây là icon SAI NGHĨA, không chỉ là icon trùng.
        "power_level" to "ic-bolt", "vehicle_type" to "ic-car", "emergency_alarm" to "ic-alert",
        // U7 lượt 2 · [ĐO bài mù Pass 1] hai cặp này trước dùng CHUNG một hình: `sunroof_state` ↔ `sunroof_pos`
        // khác nhau 0 pixel, `tailgate_status` ↔ `tailgate_position` khác 2% — mà chúng nằm KỀ NHAU trong
        // nhóm Thân xe. ⚠ 2026-09-25 cả ba mã kia đã gỡ (cốp không cảm biến · xe không có cửa sổ trời) ⇒ chỉ còn
        // `sunroof_state`, va chạm hình tự hết.
        "sunroof_state" to "ic-car-top-sunroof",
        "sunshade_pct" to "ic-car-top-sunshade",
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
        // Màu vẽ dải LIỀN KHỐI, độ sáng vẽ dải CHIA NẤC — cùng vị trí nhưng khác hình, vì đây là hai
        // đại lượng khác nhau của cùng một dải (R2: khác biệt phải ở HÌNH, không chỉ ở nhãn).
        // ── Điện phụ 12V / nguồn máy (nhóm "An toàn · ADAS" đã gỡ hẳn 2026-09-16 cùng 17 datum của nó) ──
        // ⚠ Ba mục này chuyển từ `SAFETY` sang `ENERGY` cùng lượt gỡ, và tia sét trần thì **không còn chỗ**: [ĐO]
        // `CapabilityIconsDiversityTest` báo `ic-bolt ×6` trong lĩnh vực Năng lượng ngay lượt chạy đầu (trần là 3).
        // Nên mỗi mục lấy đúng hình của thứ nó đo: ĐIỆN ÁP (cùng họ với áp cell) · MỨC pin · THIẾT BỊ còn sống.
        // ⚠ (V) FEATURE-FILTER 2026-09-17: `mcu_status` (mục thứ ba của cụm này) đã xoá — owner chấm NO.
        // ⚠ 2026-09-25: `volt_12v_level` cũng xoá (getter = 65535) ⇒ cụm còn MỘT mục, mang hình ĐIỆN ÁP.
        "volt_12v" to "ic-cell-volt",
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
        "oil_level" to "ic-hood", )

    /**
     * Khớp TIỀN TỐ — cho các họ mục cùng khái niệm (áp suất lốp ×4, nhiệt lốp ×4, kính ×4, cửa ×4…).
     * Thứ tự QUAN TRỌNG: tiền tố dài đứng trước (nhiệt lốp phải khớp trước áp suất lốp).
     */
    private val PREFIX: List<Pair<String, String>> = listOf(
        // ⚠ U7 GỠ 15 TIỀN TỐ Ở ĐÂY (lốp · kính · cửa · đèn · đèn viền · GPS).
        // Tiền tố là công cụ để nói "cả HỌ này cùng một hình" — đúng khi khác biệt nằm ở KHÁI NIỆM.
        // Nhưng ở các họ đó khác biệt nằm ở VỊ TRÍ, nên gộp theo tiền tố chính là thứ tạo ra "14 ô
        // cùng một bóng đèn". Nay mỗi mã tra thẳng ra hình mang đúng vị trí của nó (bảng EXACT ở trên).
        // U6: bụi mịn là thứ được ĐO, chiếc lá là thứ đang LÀM (lọc/ion) — trước đây cả bốn mục cùng chiếc lá.
        // `pm25_online` nói về THIẾT BỊ (cảm biến còn sống không) nên nó tách khỏi cả hai, khớp TRƯỚC tiền tố chung.
        "pm25_online" to "ic-sensor", "pm25_" to "ic-dust",
        // [KIỂM TOÁN UX mục 4d] Công suất mô-tơ KHÔNG phải tốc độ ⇒ không dùng icon đồng hồ tốc.
        // U6: và vòng tua / mô-men KHÔNG phải công suất — ba đại lượng khác nhau của cùng một mô-tơ. Tiền tố dài
        // đứng trước tiền tố ngắn (`motor_front_rpm` phải khớp trước `motor_`), cùng luật đã dùng cho lốp.
        "motor_" to "ic-motor",
        // Máy XĂNG có hình riêng: trên DM-i hai vòng tua nằm cạnh nhau, cùng hình là không đọc ra cái nào của cái gì.
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
