package com.byd.clusternav.launcher

/**
 * ═══ H1 · ĐỌC ĐÚNG THỨ ĐANG HIỆN, KHÔNG ĐỌC CẢ BẢNG DATUM ═══════════════════════════════════════════════════
 *
 * [ĐO xe 2026-09-16] (`docs/diagnostics/perf-profile-2026-09-16.md` §0): trong 47 phút lăn bánh, Kachi ghi
 * **24 055 dòng getter HAL (≈510/phút)** và **10 804 dòng "no permission" (≈229/phút)** — vì [CarDataAdapter] đọc
 * **TOÀN BỘ** bảng datum mỗi nhịp, bất kể màn hình đang bày cái gì. Mỗi lượt đọc là một binder IPC vào framework;
 * đếm theo số nhịp trong chính log ấy thì một nhịp NHANH mất ≈1,42 s thay vì 1,00 s ⇒ ≈0,4 s mỗi giây là thời
 * gian CPU nằm chờ HAL, khớp với `top` (6,6 % trên 8 lõi ≈ nửa lõi liên tục khi KHÔNG làm gì).
 *
 * Đây là phép tính **nhu cầu**: từ [HomeUiState] (nguồn sự thật DUY NHẤT của màn chính) suy ra tập `id` datum mà
 * màn hình thật sự bày ra. [CarDataAdapter] đọc đúng tập đó; phần còn lại GIỮ giá trị cũ (không đặt `null` —
 * `null` nghĩa là *"không đọc được"* và sẽ biến ô thành "—").
 *
 * ## Bốn bề mặt bày số của xe — không có bề mặt thứ năm
 *  1. **chip thanh trên** ([TopStripConfig.ids]) — ba chip TỔNG HỢP dựng sẵn mang nhiều datum (xem [CHIPS]);
 *  2. **ô giữa màn** ([WorkspaceState.slots] loại [SlotContent.Widget]) — widget dựng tay ([CURATED]), nhóm khả
 *     năng ([CapabilityGroup.reads]), hoặc thẳng một mã datum;
 *  3. **thanh nút** ([DockConfig.enabled]) — ô ĐỌC đặt được ở thanh nút (RW0);
 *  4. **câu hỏi bằng giọng** — KHÔNG đi qua đây: nó đọc thẳng `HalBindingTable` một lượt (xem ⚠ dưới).
 *
 * ## ⚠ Ba đường CỐ Ý không bị lọc
 * Cổng này chỉ áp cho **vòng poll nền**. Ba đường sau là hành động TƯỜNG MINH của người dùng/kiểm thử và phải đọc
 * đủ, nếu không thì cái cổng hiệu năng lại biến thành một lỗi chức năng:
 *  • cầu kiểm thử `sweep`/`read` (`KachiTestBridge`) — gọi thẳng `HalBindingTable`, không qua [CarDataAdapter];
 *  • màn *Kiểm tra từng nút* + `telemetryText` — cùng đường thẳng ấy;
 *  • **câu hỏi bằng giọng** (*"pin còn bao nhiêu"*) — đường này KHÁC hai đường trên, xem ⚠⚠ ngay dưới.
 * Hai đường đầu KHÔNG đi qua [CarDataAdapter.readFast]/[readSlow] nên tự nhiên nằm ngoài cổng — không cần cờ miễn trừ.
 *
 * ## ⚠⚠ [SOÁT P1-1 · 2026-09-16] Câu hỏi bằng giọng KHÔNG đi qua `telemetryText`
 * Bản đầu của tệp này ghi *"câu hỏi bằng giọng — cũng `telemetryText`"*. **Sai, đã kiểm**: `VoiceDispatcher.runRead`
 * đọc `TelemetryReadout.of(id, state().carStatus)` — tức chính **ảnh chụp của vòng poll**, thứ mà cổng này vừa lọc.
 * `telemetryText` chỉ có đúng một chỗ gọi là màn *Kiểm tra từng nút* (`KachiHomeActivity.readInfo`).
 *
 * Hậu quả nếu để nguyên: hỏi một datum KHÔNG có trên màn (vd *"tốc độ bao nhiêu"* khi màn chỉ bày pin·bụi·nhiệt độ)
 * thì [CarDataAdapter] đã **giữ giá trị cũ** cho nó suốt phiên ⇒ xe đứng yên mà Kachi đọc to con số của lần cuối ô
 * Tốc độ còn trên màn. Một câu trả lời CŨ mà nghe như đang sống là tệ hơn *"chưa đọc được"*.
 *
 * Cách chữa: [Holder.withExtra] — đường giọng nói **ghim** datum nó sắp trả lời vào nhu cầu rồi bắt vòng poll đọc
 * NGAY một lượt (`CarStatusRepository.refreshNow`). Ghim là **hợp**, không phải **thay**: nhu cầu của màn hình vẫn
 * còn nguyên trong lượt đó, nên một nhịp poll chạy song song không bị bỏ đói.
 *
 * ## An toàn khi bảng [CURATED] thiếu (fail-open)
 * Một mã `w_*` lạ (widget thêm về sau mà quên khai ở đây) ⇒ [of] trả `null` = *"đọc hết như cũ"*. Chọn hướng
 * **hụt hiệu năng** chứ không **hụt dữ liệu**: hậu quả của hướng kia là một ô câm mà không ai hiểu vì sao, đúng
 * họ lỗi mà CLAUDE.md §8 cảnh báo (compile xanh ≠ chạy đúng).
 */
object CarDataDemand {

    /**
     * Datum mà mỗi **widget dựng tay** (`w_*` trong [WidgetRegistry]) thật sự bày ra.
     *
     * Bảng này là DỮ LIỆU chép từ chính bộ vẽ (`WidgetViews` ở `:app`, các hàm `energyRing`/`pm25Ring`/`speed`/
     * `tyreBoard`/`carState`/`board`/`clock` + bản nén `mini`), không phải một phép đoán theo tên. Mỗi dòng ghi cả
     * hai bản vẽ (to + nén) vì một ô có thể đang ở dạng nào cũng được.
     *
     * `w_media` / `w_photos` không có mặt: chúng KHÔNG đọc gì từ xe (nhạc + tệp ảnh) — có mặt với tập rỗng để bài
     * test đếm đủ 9 widget, chứ không phải bị bỏ sót.
     */
    val CURATED: Map<String, Set<String>> = mapOf(
        "w_energy" to setOf("soc", "ev_range_km"),
        "w_pm25" to setOf("pm25_value", "pm25_level"),
        "w_speed" to setOf("speed"),
        "w_tire" to setOf(
            "tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr",
            "tyre_t_fl", "tyre_t_fr", "tyre_t_rl", "tyre_t_rr",
        ),
        "w_car" to setOf("door_lf", "door_rf", "door_lr", "door_rr", "tailgate_status"),
        // ⚠ [SOÁT P1-1] Bốn mã NHIỆT lốp có mặt dù ô tổng hợp chỉ *in ra* áp suất: bộ vẽ chuyền **cả cụm**
        // `CarStatus.Tyres` cho `TyreBoard.readings(...)` rồi đọc `status.alert` của kết quả. Hôm nay phép xét
        // ấy chỉ dùng áp suất — nhưng nó là quyết định của MỘT LỚP KHÁC, và ngày ai đó cho nhiệt vào ngưỡng thì
        // ô này sẽ âm thầm xét bằng một con số đóng băng. Chép ranh giới theo **thứ được chuyền đi**, không
        // theo thứ đang được in ra; giá là 4 lượt đọc mỗi nhịp chậm khi ô có trên màn.
        "w_board" to setOf(
            "soc", "ev_range_km", "pm25_value", "pm25_level",
            "tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr",
            "tyre_t_fl", "tyre_t_fr", "tyre_t_rl", "tyre_t_rr",
        ),
        "w_clock" to setOf("ext_temp"),
        "w_media" to emptySet(),
        "w_photos" to emptySet(),
    )

    /**
     * Datum của ba chip TỔNG HỢP dựng sẵn ([TopStripChips]) — chúng KHÔNG phải một datum nên không tra được qua
     * [TelemetryRegistry]; chip `chip_energy` một mình mang HAI datum.
     */
    val CHIPS: Map<String, Set<String>> = mapOf(
        TopStripConfig.PM25 to setOf("pm25_level"),
        TopStripConfig.TEMP to setOf("ext_temp"),
        TopStripConfig.ENERGY to setOf("soc", "ev_range_km"),
    )

    /**
     * Tập `id` datum mà [state] thật sự bày ra, hoặc `null` = *"không tính được ⇒ đọc hết"* (xem KDoc lớp).
     *
     * THUẦN — không Android, không đọc đĩa; test off-device bằng một [HomeUiState] dựng tay.
     */
    fun of(state: HomeUiState): Set<String>? {
        val out = LinkedHashSet<String>()
        state.topStrip.ids.forEach { if (!expand(it, out)) return null }
        state.dock.enabled.forEach { if (!expand(it, out)) return null }
        state.workspace.slots.forEach { slot ->
            if (slot is SlotContent.Widget) slot.ids.forEach { if (!expand(it, out)) return null }
        }
        return out
    }

    /**
     * ═══ Các Ô ĐIỀU KHIỂN đang hiện MÀ CÓ đường đọc (2026-09-17) — tập cho [CarStatus.controls] ══════════════
     *
     * Khác [of] (tập DATUM cho các cụm/read-tile/nhóm): đây là tập **mã NÚT** để vòng poll đọc giá trị THẬT của
     * chúng qua [HalBindingTable.readState] rồi cất vào [CarStatus.controls] theo mã nút. Chỉ nút có [ControlDef.readKey]
     * (đường đọc) mới vào — nút chưa nối đường đọc thì ô lùi về mức RAM, không bịa.
     *
     * Vì sao TÁCH khỏi [of] thay vì nhét `readKey` vào tập datum: nhét vào thì vòng poll đọc datum ấy vào **field
     * của cụm** (vd `Climate.setTempC`) rồi tầng vẽ phải map NGƯỢC field→nút — thêm một bảng dễ lệch. Đọc thẳng
     * `readState(nút)` cho ra đúng con số ô cần, và [readState] là **một nguồn transform duy nhất** (thang mức ghế,
     * đảo AUTO) nên không có bản sao thứ hai. Chi phí: mỗi nút đang hiện = 1 lượt đọc HAL ở nhịp CHẬM (≤ số ô điều
     * khiển trên màn), trong ngân sách K1 (< 150 lượt/phút).
     *
     * THUẦN — cùng ba bề mặt với [of] (thanh trên KHÔNG mang nút; chip chỉ nhận mục ĐỌC — RW0).
     */
    fun controlsOf(state: HomeUiState): Set<String> {
        val out = LinkedHashSet<String>()
        fun scan(id: String) {
            val def = ControlRegistry.byId(id) ?: return
            if (def.readKey.isNotBlank()) out += id
        }
        state.dock.enabled.forEach(::scan)
        state.workspace.slots.forEach { slot ->
            if (slot is SlotContent.Widget) slot.ids.forEach(::scan)
        }
        return out
    }

    /**
     * Thêm vào [out] các datum mà một **mã khả năng** [id] bày ra. Trả `false` nghĩa là *"không biết mã này bày gì"*
     * ⇒ chỗ gọi phải rơi về đọc-hết.
     *
     * Thứ tự tra khớp đúng thứ tự mà bộ vẽ dùng (`WidgetViews.build`): widget dựng tay → nhóm khả năng → chip tổng
     * hợp → datum thường → hành động. Lệch thứ tự này là hai bảng nói hai chuyện về cùng một mã.
     */
    private fun expand(id: String, out: MutableSet<String>): Boolean {
        CURATED[id]?.let { out += it; return true }
        CapabilityGroups.byId(id)?.let { out += it.reads; return true }
        CHIPS[id]?.let { out += it; return true }
        if (TelemetryRegistry.byId(id) != null) { out += id; return true }
        // Nút BẤM (và gói lệnh) không bày số của xe: trạng thái bật/tắt của ô nút nằm trong RAM của chính nó
        // ([ControlTileState]), không đọc lại từ HAL. Bỏ qua, KHÔNG rơi về đọc-hết.
        if (ControlRegistry.byId(id) != null || ActionMacros.byId(id) != null) return true
        // S4 · R12 — hành động của CHÍNH launcher (*Ứng dụng* · *Cài đặt* · *Nói với xe*) ĐẶT ĐƯỢC lên thanh nút:
        // `DockConfig.setEnabled` nhận mọi mã có trong [CapabilityCatalog], và `DockPickerContractTest` khoá đúng
        // ca đó. Chúng không chạm `CarControlPort` nên cũng không bày một con số nào của xe ⇒ bỏ qua như nút BẤM.
        // ⚠ Thiếu dòng này thì chỉ cần MỘT ô *Ứng dụng* trên thanh nút là [of] trả `null` ⇒ cổng H1 tắt IM LẶNG
        // và mọi nhịp poll quay lại đọc cả bảng datum — đúng 77 % tải HAL mà 1.67 vừa cắt.
        if (LauncherActions.byId(id) != null) return true
        return false
    }

    /**
     * Có datum nào của **nhịp NHANH** nằm trong [demand] không.
     *
     * Vì sao cần: nhịp nhanh chạy 1 Hz và mang 15 datum; màn mặc định (pin · bụi · nhiệt độ ngoài) KHÔNG bày một
     * datum nhanh nào, nên cả vòng 1 Hz là **thuần lãng phí** — nhưng chỉ biết được điều đó bằng cách hỏi, chứ
     * không được đoán theo tên hồ sơ hay theo bố cục. `null` ([of] không tính được) ⇒ `true` (chạy như cũ).
     */
    fun needsFast(demand: Set<String>?): Boolean = demand == null || demand.any { it in FAST_IDS }

    /**
     * Hộp giữ nhu cầu HIỆN TẠI — cầu một chiều `state → nhu cầu → vòng poll`.
     *
     * Vòng poll ([CarStatusRepository]) chạy trên coroutine nền còn nhu cầu sinh ra ở luồng thu state, nên phải có
     * một chỗ **một-người-ghi / nhiều-người-đọc**. Đặt ở `:core` vì nó thuần (guard `LayeringRules` cấm file thuần
     * nằm trong `:app`), và cố ý KHÔNG dùng `StateFlow`: vòng poll chỉ cần *"lúc này đang cần gì"*, không cần được
     * đánh thức khi nhu cầu đổi — nó tự hỏi lại ở nhịp sau.
     *
     * Mặc định `null` = **đọc hết**: lượt poll đầu tiên (trước khi state đầu tiên về) làm tươi mọi datum một lần,
     * đúng thứ ta muốn khi vừa mở màn.
     */
    class Holder {
        @Volatile private var value: Set<String>? = null

        /**
         * Datum GHIM THÊM ngoài màn hình — xem ⚠⚠ ở KDoc lớp (đường câu hỏi bằng giọng).
         *
         * Tách khỏi [value] chứ không hợp sẵn vào đó, vì [value] là **ảnh của màn hình** và chỉ luồng thu state
         * được ghi; ghim là thứ sống đúng một lượt đọc của một luồng khác. Trộn hai vai vào một biến thì lượt
         * render kế tiếp sẽ xoá mất ghim (hoặc ngược lại, ghim rò rỉ vào nhu cầu của màn) tuỳ ai ghi sau.
         */
        @Volatile private var extra: Set<String> = emptySet()

        /**
         * Tập mã NÚT đang hiện có đường đọc ([controlsOf]) — cho [CarStatus.controls].
         *
         * Rời khỏi [value] vì [value] là tập DATUM còn đây là tập NÚT, và [CarDataAdapter] đọc chúng bằng hai đường
         * khác nhau (`readInt(datum)` vào field cụm · `readState(nút)` vào map controls). Trộn chung một biến là
         * trộn hai đơn vị.
         */
        @Volatile private var controls: Set<String> = emptySet()

        fun set(v: Set<String>?) { value = v }

        /** Đặt tập nút đang hiện (từ [controlsOf]). Màn rời tiền cảnh ⇒ [clear] xoá về rỗng = không đọc control nào. */
        fun setControls(ids: Set<String>) { controls = ids }

        /** Tập nút đang hiện — [CarDataAdapter] đọc `readState` cho từng mã rồi cất vào [CarStatus.controls]. */
        fun controls(): Set<String> = controls

        /** Nhu cầu THẬT của lượt poll này = nhu cầu màn hình **hợp** phần đang ghim. `null` vẫn là *"đọc hết"*. */
        fun get(): Set<String>? {
            val v = value ?: return null
            val e = extra
            return if (e.isEmpty()) v else v + e
        }

        /**
         * Chạy [body] trong lúc [ids] được coi như *"đang hiện"*.
         *
         * `finally` bỏ ghim ngay cả khi [body] ném: bỏ sót một lượt gỡ là ghim sống mãi, tức đúng cái tải mà H1
         * vừa cắt lại mọc lại — im lặng, và chỉ lộ ra ở bộ đếm sau hàng chục phút.
         *
         * ⚠ KHÔNG lồng nhau được (một biến, không phải ngăn xếp): hôm nay chỉ có một chỗ gọi (đường giọng nói,
         * tuần tự). Lồng thì lượt trong cùng gỡ hết ghim của lượt ngoài — hậu quả tối đa là **đọc thiếu một
         * datum một lượt**, không phải mất dữ liệu, nên không dựng ngăn xếp cho một ca chưa tồn tại.
         */
        fun <T> withExtra(ids: Set<String>, body: () -> T): T {
            extra = ids
            return try { body() } finally { extra = emptySet() }
        }

        /** Màn chính rời tiền cảnh ⇒ quên nhu cầu; lần mở sau bắt đầu lại bằng một lượt đọc đủ. */
        fun clear() { value = null; extra = emptySet(); controls = emptySet() }
    }

    /**
     * Danh sách datum của nhịp NHANH — **cùng một danh sách** mà [CarDataAdapter.readFast] đọc.
     *
     * ⚠ Hai chỗ này phải khớp; `CarDataDemandTest` ép đúng điều đó bằng cách đọc lại qua một bảng giả đếm id, nên
     * thêm một dòng vào `readFast` mà quên ở đây sẽ ĐỎ off-car chứ không im lặng thành "datum không bao giờ tươi".
     */
    val FAST_IDS: Set<String> = setOf(
        "speed", "gear", "op_mode", "energy_mode", "motor_power",
    )
}
