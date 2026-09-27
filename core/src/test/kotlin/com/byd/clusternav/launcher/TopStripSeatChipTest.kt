package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * UX5/UX5b — chip GHẾ (datum mức + chip gộp ghế lái/ghế phụ) — nửa sau của [TopStripTest], tách THUẦN theo CHỦ ĐỀ (664 dòng
 * → trần 500, L6-debt 2026-09-27). Thân từng bài giữ nguyên byte; hai trợ giúp `seatStatus`/`one` đi theo vì chỉ nhóm này dùng.
 */
class TopStripSeatChipTest {
    // ── UX5 · datum MỨC (ghế) nói bằng màu + số, và chip GỘP ghế ──────────────────────────────────

    private fun seatStatus(
        heatRaw: Int? = null,
        ventRaw: Int? = null,
        heatRRaw: Int? = null,
        ventRRaw: Int? = null,
    ) = CarStatus(
        climate = CarStatus.Climate(
            seatHeatRaw = heatRaw, seatVentRaw = ventRaw,
            seatHeatRRaw = heatRRaw, seatVentRRaw = ventRRaw,
        ),
    )

    private fun one(id: String, status: CarStatus, labels: Boolean = true) =
        TopStripChips.render(TopStripConfig(listOf(id), showLabels = labels), status).single()

    /**
     * [ĐO đọc mã 2026-09-26] trước UX5 chip này in đúng chữ `"Ghế sưởi · Tắt"` — chữ trạng thái mà owner đã gạch bỏ
     * (2026-09-21 *"bỏ chữ Bật/Tắt đi"*), lọt qua vì ghế không phải datum bật/tắt nên nhánh `isOnOff` không với tới.
     */
    @Test
    fun `chip ghe khong con in chu Tat - tat thi chi con nhan va icon mo`() {
        // [ĐO xe 2026-09-17] thang ghế: raw 1 = TẮT · 2 = mức 1 · 3 = mức 2.
        val off = one("seat_heat_state", seatStatus(heatRaw = 1))
        assertEquals("Ghế sưởi", off.text, "mức 0 ⇒ chỉ nhãn ngắn, KHÔNG có '· Tắt'")
        assertEquals(ChipTone.INACTIVE, off.tone, "trạng thái nói bằng màu icon")
        assertEquals("Mức ghế sưởi: Tắt", off.desc, "câu cho trình đọc màn hình vẫn ĐẦY ĐỦ")
        assertEquals("", one("seat_heat_state", seatStatus(heatRaw = 1), labels = false).text)
    }

    /**
     * 2.76 L7 (owner 27/09: *"1 bông tuyết, 2 bông tuyết, 1 sưởi, 2 sưởi trên icon luôn mà không cần số 1-2"*): mức
     * nằm trong HÌNH. Bản 2.75 in `"Ghế sưởi · 2"` (B9); nay chip chỉ còn nhãn + hình mang đúng số dấu. Tắt nhãn ⇒
     * chip **chỉ-hình** (trước là `"2"`). Câu cho trình đọc màn hình vẫn đủ *"Mức 2"*.
     */
    @Test
    fun `chip ghe dang chay thi sang mau va MUC nam trong HINH, khong con so`() {
        val two = one("seat_heat_state", seatStatus(heatRaw = 3))
        assertEquals("Ghế sưởi", two.text, "mức 2 ⇒ chỉ nhãn — hai làn nhiệt trong hình đã nói mức")
        assertEquals("ic-seat-heat-left", two.icon, "hình khái niệm = mức CAO NHẤT (hai làn nhiệt)")
        assertEquals(ChipTone.ACTIVE, two.tone)
        assertEquals("Mức ghế sưởi: Mức 2", two.desc, "câu đọc màn hình vẫn nói con số")
        val oneLvl = one("seat_heat_state", seatStatus(heatRaw = 2))
        assertEquals("Ghế sưởi", oneLvl.text)
        assertEquals("ic-seat-heat-left-1", oneLvl.icon, "mức 1 ⇒ hình MỘT làn nhiệt")
        assertEquals("", one("seat_heat_state", seatStatus(heatRaw = 3), labels = false).text, "tắt nhãn ⇒ chip chỉ-hình")
        assertTrue(listOf(two, oneLvl).none { c -> c.text.any { it.isDigit() } }, "không còn chữ số mức trên chip")
    }

    /**
     * Mức NGOÀI bảng hình (`seath` [SUY] còn nấc thứ 3 — `ControlLevels` giữ 4 mã thô, chưa đo) ⇒ chip **in lại con
     * số** như 2.75 với hình khái niệm: thà nói *"3"* còn hơn vẽ hai dấu cho một mức chưa ai đo (CLAUDE.md §2).
     */
    @Test
    fun `muc ngoai bang hinh thi con so quay lai, khong ve bua`() {
        val three = one("seat_heat_state", seatStatus(heatRaw = 4))
        assertEquals(3, TelemetryReadout.of("seat_heat_state", seatStatus(heatRaw = 4))?.level, "mồi: raw 4 = mức 3 [SUY]")
        assertEquals("Ghế sưởi · 3", three.text)
        assertEquals("ic-seat-heat-left", three.icon, "không có hình mức 3 ⇒ hình khái niệm")
        assertEquals(ChipTone.ACTIVE, three.tone)
        assertEquals("Ghế lái · 3", one(TopStripConfig.SEAT, seatStatus(heatRaw = 4, ventRaw = 1)).text, "chip gộp cùng luật")
    }

    @Test
    fun `chua doc duoc muc ghe thi KHONG mo icon - khong biet khac dang tat`() {
        val unread = one("seat_heat_state", seatStatus(heatRaw = null))
        assertEquals(ChipTone.NEUTRAL, unread.tone, "'không biết' ≠ 'đang tắt' — luật chung của thanh trên")
        assertEquals("Ghế sưởi · —", unread.text)
        // Mã thô NGOÀI thang cũng là "chưa biết", không được làm tròn thành mức 1.
        assertEquals(ChipTone.NEUTRAL, one("seat_heat_state", seatStatus(heatRaw = 99)).tone)
    }

    /** Hình của chip ghế nay là glyph GHÉP (ghế + phương thức) và **trùng với hình của chính nút** ghế. */
    @Test
    fun `chip ghe mang dung hinh cua nut ghe`() {
        assertEquals("ic-seat-heat-left", one("seat_heat_state", seatStatus(heatRaw = 3)).icon)
        assertEquals("ic-seat-vent-left", one("seat_vent_state", seatStatus(ventRaw = 3)).icon)
        assertEquals(ControlRegistry.byId("seath")!!.icon, one("seat_heat_state", seatStatus(heatRaw = 1)).icon)
        // L7: mức 1 = bản `-1` của CHÍNH hình nút ấy (bảng `CapabilityIcons.LEVEL` khoá theo hình nút) — ô nút đọc cùng dòng.
        assertEquals(CapabilityIcons.forLevel(ControlRegistry.byId("seatc")!!.icon, 1), one("seat_vent_state", seatStatus(ventRaw = 2)).icon)
        assertEquals("ic-seat-vent-left-1", one("seat_vent_state", seatStatus(ventRaw = 2)).icon)
    }

    /**
     * ⚠ UX5b (owner 2026-09-27, nhìn máy ảo): *"ghế sao không có ghế lái hay ghế phụ? 2 ghế nó khác nhau mà"* ⇒ chữ
     * trên chip nay là **GHẾ NÀO** (`"Ghế lái"`), không phải chế độ (`"Ghế sưởi"`). Chế độ đã nằm trong glyph ghép;
     * với hai chip ghế cạnh nhau thì cả hai in `"Ghế sưởi"` là thông tin đặt sai chỗ. [ChipView.desc] vẫn đủ hai chế độ.
     */
    @Test
    fun `chip GOP ghe doi hinh theo che do dang chay`() {
        // L7 (2.76): chữ chỉ còn GHẾ NÀO; chế độ VÀ mức đều nằm trong hình (hai làn nhiệt = sưởi mức 2 · một bông = mát mức 1).
        val heat = one(TopStripConfig.SEAT, seatStatus(heatRaw = 3, ventRaw = 1))
        assertEquals("Ghế lái", heat.text); assertEquals("ic-seat-heat-left", heat.icon)
        assertEquals(ChipTone.ACTIVE, heat.tone)
        assertEquals("Mức ghế sưởi: Mức 2 · Mức ghế mát: Tắt", heat.desc, "con số chỉ còn ở câu đọc màn hình")

        val vent = one(TopStripConfig.SEAT, seatStatus(heatRaw = 1, ventRaw = 2))
        assertEquals("Ghế lái", vent.text, "nhãn KHÔNG đổi theo chế độ — hình mới là thứ đổi")
        assertEquals("ic-seat-vent-left-1", vent.icon, "mát mức 1 ⇒ MỘT bông tuyết")
        assertEquals("ic-seat-vent-left", one(TopStripConfig.SEAT, seatStatus(heatRaw = 1, ventRaw = 3)).icon, "mát mức 2 ⇒ HAI bông")

        val off = one(TopStripConfig.SEAT, seatStatus(heatRaw = 1, ventRaw = 1))
        assertEquals("Ghế lái", off.text, "cả hai tắt ⇒ ghế TRỐNG, mờ, không số")
        assertEquals("ic-seat-left", off.icon); assertEquals(ChipTone.INACTIVE, off.tone)

        val unread = one(TopStripConfig.SEAT, seatStatus())
        assertEquals("Ghế lái · —", unread.text); assertEquals(ChipTone.NEUTRAL, unread.tone)
        assertEquals("Mức ghế sưởi: — · Mức ghế mát: —", unread.desc, "câu đọc màn hình nói đủ CẢ HAI chế độ")

        // ⚠ Mờ = khẳng định "cả hai đang tắt" ⇒ chỉ nói khi CẢ HAI đã đọc được. Một mã còn nguội thì là "chưa biết".
        val half = one(TopStripConfig.SEAT, seatStatus(heatRaw = 1))
        assertEquals(ChipTone.NEUTRAL, half.tone, "biết sưởi tắt mà chưa biết mát ⇒ chưa được mờ")
        // …nhưng nếu một mã đang CHẠY thì trạng thái thật vẫn hiện ra, không bị luật trên giấu mất.
        assertEquals(ChipTone.ACTIVE, one(TopStripConfig.SEAT, seatStatus(ventRaw = 3)).tone)
    }

    /**
     * Cả hai chế độ cùng > 0 **không nên xảy ra** ([SUY] RE `seat-comfort-auto.html` §2: hai chế độ loại trừ nhau,
     * nhưng đường HAL của launcher chưa đo ca ấy) ⇒ quy tắc CỐ ĐỊNH: ưu tiên SƯỞI. Khoá ở đây để nó là một quyết
     * định có chủ ý chứ không phải thứ tự tình cờ của một `when`.
     */
    @Test
    fun `ca hai che do cung chay thi uu tien SUOI, va khong duoc nem`() {
        val both = one(TopStripConfig.SEAT, seatStatus(heatRaw = 2, ventRaw = 3))
        assertEquals("Ghế lái", both.text)
        assertEquals("ic-seat-heat-left-1", both.icon, "mức lấy của SƯỞI (raw 2 ⇒ mức 1 = MỘT làn nhiệt), không phải hai bông của mát")
        // …và luật ấy là của BỘ DỰNG, không của một nhánh theo mã ⇒ ghế phụ phải cho cùng câu trả lời.
        val bothR = one(TopStripConfig.SEAT_R, seatStatus(heatRRaw = 2, ventRRaw = 3))
        assertEquals("Ghế phụ", bothR.text)
        assertEquals("ic-seat-heat-right-1", bothR.icon)
    }

    /**
     * ═══ HAI DANH SÁCH, HAI CÂU HỎI — và **quyết định của owner** mới được đổi câu trả lời ═══════════════════
     *
     * [TopStripConfig.BUILT_IN] trả lời *"đặt được cái gì"*; [TopStripConfig.DEFAULT_IDS] trả lời *"ai không sửa gì
     * thì thấy cái gì"*. UX5 tách hai danh sách này ra vì thêm một chip dựng sẵn khi chúng là MỘT thì thanh trên của
     * mọi người lặng lẽ mọc thêm một chip — một thay đổi mặc định mà không ai xin.
     *
     * ## ⚠ Bài này ĐỔI Ý ngày 2026-09-27 (UX5b), và đổi một cách có chủ ý
     * Bản UX5 của bài ghim *"chip dựng sẵn mới KHÔNG được vào mặc định"* — tức nó canh **cơ chế** bằng cách ghim
     * **nội dung**. Owner 2026-09-27, ngồi trước máy ảo: *"sao còn ghế mát và ghế sưởi riêng, với ghế sao không có
     * ghế lái hay ghế phụ? 2 ghế nó khác nhau mà"* ⇒ hai chip ghế GỘP **vào mặc định**. Xoá bài đi thì mất luôn cái
     * chốt chống-rữa; giữ nguyên thì nó nói sai. Nên nó được **viết lại đúng thứ nó phải canh**:
     *  • hai danh sách vẫn là hai vật rời (`DEFAULT_IDS !== BUILT_IN.toList()`) ⇒ chip dựng sẵn thứ SÁU vẫn phải có
     *    người xin mới vào được mặc định;
     *  • mặc định vẫn **mở đầu** bằng đúng ba chip cũ, theo đúng thứ tự cũ (đường mới xuống cuối, CLAUDE.md §6);
     *  • hai datum ghế LẺ vẫn đặt riêng được (chip gộp là lựa chọn thêm, không phải bản thay thế).
     */
    @Test
    fun `hai chip ghe GOP vao mac dinh - quyet dinh cua owner 2026-09-27`() {
        listOf(TopStripConfig.SEAT, TopStripConfig.SEAT_R).forEach { id ->
            assertTrue(id in TopStripConfig.BUILT_IN, "$id phải đặt được (isChippable)")
            assertTrue(TopStripConfig.isChippable(id))
            assertTrue(id in TopStripConfig.DEFAULT_IDS, "$id vào mặc định — owner xin 2026-09-27")
            assertTrue(
                TopStripConfig.choices().any { it.id == id },
                "$id phải hiện trong màn chọn, nếu không thì có mà không ai GỠ được",
            )
        }
        assertEquals(
            listOf(TopStripConfig.PM25, TopStripConfig.TEMP, TopStripConfig.ENERGY,
                TopStripConfig.SEAT, TopStripConfig.SEAT_R),
            TopStripConfig.DEFAULT_IDS,
            "ba chip cũ giữ ĐÚNG thứ tự cũ và đứng trước; chip mới nối vào CUỐI",
        )
        // ⚠⚠ NÓI THẲNG MỘT ĐIỂM YẾU: **hôm nay hai danh sách trùng nội dung** (cả năm chip dựng sẵn đều là mặc
        // định), nên KHÔNG có phép so giá trị nào ở đây phân biệt được *"hai khai báo rời"* với
        // `DEFAULT_IDS = BUILT_IN.toList()`. Viết một `assertNotEquals` ở đây sẽ **đỏ oan** và người sau sẽ nới nó
        // ra. Chốt cấu trúc vì thế nằm ở chỗ đọc được cấu trúc: `TopStripWiringContractTest.hai danh sach chip dung
        // san la HAI khai bao roi` (đọc mã nguồn `:core`). Ở đây chỉ ghim NỘI DUNG mặc định, đúng tầm của một bài
        // hành vi.
        // Hai datum LẺ ở lại: chip gộp là lựa chọn THỨ BA, không phải bản thay thế.
        listOf("seat_heat_state", "seat_vent_state", "seat_heat_state_r", "seat_vent_state_r").forEach {
            assertTrue(TopStripConfig.isChippable(it), "$it vẫn phải đặt riêng được")
        }
    }

    /**
     * Chip ghế PHỤ đi qua **cùng bộ dựng** với ghế lái, chỉ khác ba dữ liệu (cặp mã · nhãn · hình ghế trống). Bài
     * này đo rằng nó thật sự đọc **cặp mã của ghế phụ** — chép bộ dựng ra hai bản thì ca đầu vẫn xanh, còn ca "ghế
     * lái đang chạy mà ghế phụ tắt" sẽ lộ ngay chỗ lẫn cặp mã.
     */
    @Test
    fun `chip ghe PHU doc dung cap ma cua ghe phu`() {
        val ventR = one(TopStripConfig.SEAT_R, seatStatus(heatRRaw = 1, ventRRaw = 3))
        assertEquals("Ghế phụ", ventR.text); assertEquals("ic-seat-vent-right", ventR.icon)   // L7: mức 2 = hai bông, không số
        assertEquals(ChipTone.ACTIVE, ventR.tone)

        val offR = one(TopStripConfig.SEAT_R, seatStatus(heatRRaw = 1, ventRRaw = 1))
        assertEquals("Ghế phụ", offR.text); assertEquals("ic-seat", offR.icon)
        assertEquals(ChipTone.INACTIVE, offR.tone)

        // ⚠ Chốt LẪN CẶP MÃ: ghế LÁI đang sưởi mức 2, ghế phụ **chưa đọc được** ⇒ chip ghế phụ phải nói "chưa biết",
        // KHÔNG được mượn số của ghế lái. Đây đúng là lỗi một bản chép sẽ mắc.
        val crossed = one(TopStripConfig.SEAT_R, seatStatus(heatRaw = 3, ventRaw = 1))
        assertEquals("Ghế phụ · —", crossed.text)
        assertEquals(ChipTone.NEUTRAL, crossed.tone)
        // …và chiều ngược lại.
        val crossedL = one(TopStripConfig.SEAT, seatStatus(heatRRaw = 3, ventRRaw = 1))
        assertEquals("Ghế lái · —", crossedL.text)
        assertEquals(ChipTone.NEUTRAL, crossedL.tone)

        // Bảng cặp mã là nguồn DUY NHẤT — hai chip, hai cặp rời nhau, không mã nào dùng hai lần.
        val pairs = TopStripConfig.SEAT_PAIRS
        assertEquals(setOf(TopStripConfig.SEAT, TopStripConfig.SEAT_R), pairs.keys)
        val ids = pairs.values.flatMap { listOf(it.first, it.second) }
        assertEquals(ids.size, ids.toSet().size, "một datum không được thuộc hai chip ghế")
        ids.forEach { assertNotNull(TelemetryRegistry.byId(it), "mã '$it' trong SEAT_PAIRS không có trong registry") }
    }

    /**
     * ⚠ Bản **TIẾNG ANH** là một đường RIÊNG, phải đo riêng — cùng lẽ với bài
     * `hai o tren cung mot man chon khong duoc mang chu y het nhau`: nhãn chip gộp đi qua `shortEn` của bộ đăng ký
     * cho hai chế độ, nhưng chữ *"Ghế"* lúc **cả hai tắt/chưa biết** là một [Strings.t] viết inline ở [TopStripChips]
     * — đúng loại chuỗi lặng lẽ ở lại tiếng Việt khi người dùng đổi ngôn ngữ, và không bài nào khác với tới.
     *
     * Và đo luôn rằng chữ trạng thái *"Off"* cũng không lọt ra chip ở tiếng Anh (owner gạch bỏ chữ Bật/Tắt cho CẢ
     * hai thứ tiếng; bài VI ở trên chỉ chặn được chữ *"Tắt"*).
     */
    @Test
    fun `chip ghe noi tieng Anh bang chinh nhan EN, va khong in chu Off`() {
        try {
            Strings.current = Lang.EN
            // L7: mức trong hình ⇒ chữ EN cũng chỉ còn nhãn ghế (trước: `"Driver seat · 2"` / `"· 1"`).
            assertEquals("Driver seat", one(TopStripConfig.SEAT, seatStatus(heatRaw = 3, ventRaw = 1)).text)
            assertEquals("Driver seat", one(TopStripConfig.SEAT, seatStatus(heatRaw = 1, ventRaw = 2)).text)
            assertEquals("ic-seat-vent-left-1", one(TopStripConfig.SEAT, seatStatus(heatRaw = 1, ventRaw = 2)).icon)
            assertEquals(
                "Driver seat", one(TopStripConfig.SEAT, seatStatus(heatRaw = 1, ventRaw = 1)).text,
                "cả hai tắt ⇒ chữ ghế theo ngôn ngữ đang chọn, KHÔNG phải \"Ghế lái\" và KHÔNG có \"Off\"",
            )
            assertEquals("Driver seat · —", one(TopStripConfig.SEAT, seatStatus()).text)
            // UX5b — chip ghế PHỤ đi qua CÙNG một `Strings.t` inline nên nó cũng phải đổi tiếng, và nó là chuỗi
            // RIÊNG (không phải bản sao của chuỗi ghế lái) ⇒ đo riêng.
            assertEquals("Passenger seat", one(TopStripConfig.SEAT_R, seatStatus(heatRRaw = 3, ventRRaw = 1)).text)
            assertEquals("Passenger seat", one(TopStripConfig.SEAT_R, seatStatus(heatRRaw = 1, ventRRaw = 1)).text)
            assertEquals(
                "Passenger seat heating level: Off · Passenger seat ventilation level: Off",
                one(TopStripConfig.SEAT_R, seatStatus(heatRRaw = 1, ventRRaw = 1)).desc,
                "câu cho trình đọc màn hình phải nói rõ GHẾ PHỤ, bằng EN",
            )
            val off = one("seat_heat_state", seatStatus(heatRaw = 1))
            assertEquals("Seat heat", off.text, "datum lẻ cũng không in \"Off\"")
            assertEquals(ChipTone.INACTIVE, off.tone)
            assertEquals("Seat heating level: Off", off.desc, "câu cho trình đọc màn hình vẫn nói đủ, bằng EN")
        } finally {
            Strings.current = Lang.VI
        }
    }
}
