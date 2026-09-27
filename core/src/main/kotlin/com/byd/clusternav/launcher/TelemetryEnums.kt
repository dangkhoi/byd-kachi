package com.byd.clusternav.launcher

/**
 * ═══ 2.76 · BẢNG MÃ → CHỮ cho datum **NHIỀU CHẾ ĐỘ** (≥ 3 trạng thái) — thuần, một nơi ══════════════════════
 *
 * ## Bệnh nó chữa (UX8 deferred [P3], owner 2026-09-27 nhìn thanh trên xe thật)
 * Chip `power_level` in `"Nguồn xe · 2"`, chip `headlight_feedback` in `"Chế độ đèn pha · 2"`: **mã thô của
 * khung** lọt thẳng ra màn hình vì [TelemetryReadout.format] chỉ `toString()`. Một con số không có đơn vị và không
 * có bảng tra thì với người lái nó là **rác** — không phải "chưa đọc được" (đó là `"—"`), cũng không phải một giá
 * trị (đó là `24 °C`).
 *
 * ## Vì sao ≥ 3 trạng thái thì ra CHỮ, không ra HÌNH
 * Ba họ đã có luật (bật/tắt → sáng/mờ · thang mức → số · **hai** chế độ → cặp hình, xem [CapabilityIcons.STATE])
 * đều dựa vào việc một glyph đọc được **ngay** là trạng thái nào. Với 4–6 trạng thái (tắt · ACC · bật · sẵn sàng…)
 * thì 4–6 glyph khác nhau cho **một** khái niệm là hoa văn nền, không phải thông tin (cùng lẽ trần
 * [CapabilityIcons.MAX_PER_DOMAIN]). Chữ là câu trả lời đúng, và chữ ấy phải **dịch** (U5 · T2) và **không bịa**:
 * mã ngoài bảng ⇒ *"mã N"* — vẫn hiện con số (để anh em chụp màn báo về), nhưng có tiền tố nói rõ đây là mã chưa
 * có nghĩa, không phải một giá trị.
 *
 * ## Nguồn của từng bảng — [ĐO source], dẫn `file:line`, KHÔNG dựa trí nhớ (CLAUDE.md §3)
 * Mỗi bảng ghi đúng hằng OEM nó dịch. Nghĩa của tên hằng (vd `OK` = *sẵn sàng*) là **[SUY]** từ tên — phần chữ
 * hiện ra chọn sao cho người lái hiểu được mà không hứa nhiều hơn tên hằng nói; chỗ nào [SUY] đều ghi ở dòng đó.
 *
 * ## Hợp đồng với bài canh
 *  • `TopStripStateIconTest` (soát máy) coi *"datum có bảng ở đây với ≥ 3 mục"* là **họ thứ TƯ** — được phép in
 *    chữ trạng thái trên chip. Bảng **2 mục** thì KHÔNG được ở đây (đó là họ hai chế độ, phải khai hình).
 *  • `TelemetryReadoutTest`: mọi hằng ánh xạ ra chữ (không có tiền tố *"mã"*), mã lạ lùi về [unknown], VI + EN.
 */
object TelemetryEnums {

    /** Một bảng mã → (VI, EN) của MỘT datum. */
    class Table internal constructor(val id: String, val entries: Map<Int, Pair<String, String>>) {
        /** Chữ cho [code] theo [lang]; mã ngoài bảng ⇒ [unknown]. */
        fun text(code: Int, lang: Lang = Strings.current): String =
            entries[code]?.let { Strings.t(it.first, it.second, lang) } ?: unknown(code, lang)
    }

    /**
     * CẤP NGUỒN XE — `BYDAutoBodyworkDevice.getPowerLevel()` (`jadx-tmap/.../bodywork/BYDAutoBodyworkDevice.java:484`).
     * [ĐO source] hằng `BODYWORK_POWER_LEVEL_*` cùng tệp `:197-202`:
     *   OFF = 0 · ACC = 1 · ON = 2 · OK = 3 · FAKE_OK = 4 · INVALID = 255.
     * [SUY] `OK` = hệ cao áp đã **sẵn sàng lăn bánh** (READY); `FAKE_OK` = *"sẵn sàng giả"* — trạng thái READY
     * chưa đủ điều kiện (vd chưa đạp phanh). Hai chữ chọn để nói đúng chừng ấy, không hơn. `INVALID` là mã hợp lệ
     * của bảng (khung nói *"không xác định"*) nên nó là một mục, không phải mã lạ.
     */
    val POWER_LEVEL = Table(
        "power_level",
        mapOf(
            0 to ("Tắt" to "Off"),
            1 to ("ACC" to "ACC"),
            2 to ("Bật" to "On"),
            3 to ("Sẵn sàng" to "Ready"),
            4 to ("Sẵn sàng (giả)" to "Ready (fake)"),
            255 to ("Không xác định" to "Invalid"),
        ),
    )

    /**
     * CHẾ ĐỘ CẦN ĐÈN PHA — feature `INSTRUMENT_HEADLIGHT_CONTROL_FEEDBACK` (1011875880 · `0x3C500028`, đo trên xe
     * `oncar-trace-2026-09-16b/featmap-20260916.json`; cùng số trong CarSettings OEM
     * `carsettings-apk/jadx-carsettings/.../com/byd/feature/instrument/Instrument.java:517`).
     *
     * [ĐO source — app CarSettings của BYD] `.../vehiclesettings/outsidelight/view/LightControl.java`:
     *   • `:81 getRadioTextIds()` = `[light_control_close, light_control_auto, light_control_small_light,
     *     light_control_low_beam]` (chỉ số 0..3);
     *   • `:106-111 getStateFromHal()` = `feedback > 0 ? feedback − 1 : 0` ⇒ chỉ số = mã − 1;
     *   • `:117 setState2Hal(i)` ghi `INSTRUMENT_HEADLIGHT_CONTROL_SET = i + 1`.
     * ⇒ **1 = Tắt · 2 = Auto · 3 = Đèn hông (小灯 / position) · 4 = Cốt**; `0` là *"chưa có phản hồi"* (CarSettings
     * ép về ô đầu) — ở đây KHÔNG bịa thành "Tắt", để nó rơi vào *"mã 0"*. Chữ *"Đèn hông"* dùng lại đúng nhãn của
     * datum `light_side` ([TelemetryRegistry]) để hai chỗ không gọi một thứ bằng hai tên.
     * Khớp bảng RE cũ `kachi-capability-catalog-2026-09-10.md:130` (*1=off/2=auto/3=parking/4=low*).
     */
    val HEADLIGHT_MODE = Table(
        "headlight_feedback",
        mapOf(
            1 to ("Tắt" to "Off"),
            2 to ("Auto" to "Auto"),
            3 to ("Đèn hông" to "Position"),
            4 to ("Cốt" to "Low beam"),
        ),
    )

    /** Mọi bảng, tra theo mã datum. Thêm datum nhiều chế độ = thêm một [Table] và một dòng ở đây. */
    private val BY_ID: Map<String, Table> = listOf(POWER_LEVEL, HEADLIGHT_MODE).associateBy { it.id }

    /**
     * Số trạng thái đã khai của [id]; `0` = không phải datum nhiều chế độ. Là câu hỏi về **bảng khai** (như
     * [CapabilityIcons.hasStateIcons]) cho các bài canh phân họ — cùng lệ `stateIconTable()`: mã chạy đọc chữ qua
     * [text], không rẽ nhánh theo con số này.
     */
    fun size(id: String): Int = BY_ID[id]?.entries?.size ?: 0

    /** Chữ cho ([id], [code]); `null` nếu [id] không có bảng. Chỗ gọi DUY NHẤT ở mã chạy: [TelemetryReadout.format]. */
    fun text(id: String, code: Int): String? = BY_ID[id]?.text(code)

    /**
     * Mã ngoài mọi bảng ⇒ *"mã N"* / *"code N"*: hiện con số để còn chẩn đoán, nhưng có tiền tố để không ai đọc nó
     * thành một giá trị. Dùng chung cho cả datum hai chế độ khi khung trả mã lạ (xem [TelemetryReadout.format]).
     */
    fun unknown(code: Int, lang: Lang = Strings.current): String = Strings.t("mã $code", "code $code", lang)
}
