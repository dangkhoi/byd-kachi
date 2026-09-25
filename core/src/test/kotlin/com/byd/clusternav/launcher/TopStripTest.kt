package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RW0 **vùng thứ ba** — thanh trạng thái trên nay đọc từ không gian khả năng thay vì 3 chip viết cứng.
 *
 * Bài quan trọng nhất ở đây là bài ĐẦU TIÊN: **mặc định phải ra ĐÚNG chuỗi của bản viết cứng cũ**. Nới một bề mặt đã
 * được owner duyệt mà âm thầm đổi thứ hiện ra là loại lỗi dự án đã gặp (làm tròn bố cục 3 ô, chip bỏ qua đơn vị) —
 * "cho cấu hình được" không phải là giấy phép đổi mặc định.
 */
class TopStripTest {

    private val full = CarStatus(
        climate = CarStatus.Climate(pm25Level = 2, outsideTempC = 24),
        energy = CarStatus.Energy(soc = 82, evRangeKm = 418),
    )

    @Test
    fun `mac dinh ra DUNG ba chip cua ban viet cung cu`() {
        val chips = TopStripChips.render(TopStripConfig.DEFAULT, full)
        assertEquals(3, chips.size, "mặc định phải là đúng 3 chip như trước")
        assertEquals("PM2.5 · Tốt", chips[0].text)
        assertEquals("24°C ngoài", chips[1].text)
        assertEquals("82% · 418 km", chips[2].text)
        assertEquals(listOf("ic-leaf", null, "ic-bolt"), chips.map { it.icon }, "icon phải giữ như cũ")
        assertEquals(ChipTone.ENERGY, chips[2].tone, "chip pin vẫn là sắc thái riêng (xanh)")
    }

    @Test
    fun `off-car moi field null thi chip noi chua doc duoc chu khong bia so`() {
        val chips = TopStripChips.render(TopStripConfig.DEFAULT, CarStatus())
        assertEquals("PM2.5 · —", chips[0].text)
        assertEquals("—°C ngoài", chips[1].text)
        assertEquals("—% · — km", chips[2].text)
    }

    @Test
    fun `chip di qua lop don vi giong moi be mat khac`() {
        // [ĐO] lỗi thật ở gói 2: người dùng chọn °F mà chip vẫn ghi °C, vì bề mặt này tự dựng chuỗi từ CarStatus.
        val f = UnitPrefs.DEFAULT.with(Quantity.TEMPERATURE, "°F").with(Quantity.DISTANCE, "mile")
        val chips = TopStripChips.render(TopStripConfig.DEFAULT, full, f)
        assertTrue(chips[1].text.endsWith("°F ngoài"), "phải theo đơn vị đã chọn, thấy: ${chips[1].text}")
        assertTrue(chips[2].text.contains(" mile"), "tầm chạy phải theo đơn vị đã chọn, thấy: ${chips[2].text}")
        assertFalse(chips[1].text.contains("°C"), "không được còn đơn vị gốc")
    }

    @Test
    fun `datum bat tat chua doc thi chip khong hien gach — (B10)`() {
        // Sấy kính (defrost_front_state) trên xe owner không bao giờ có tín hiệu ⇒ trước đây "Sấy trước · —".
        // Nay: datum bật/tắt chưa đọc = chỉ nhãn/icon + tone INACTIVE (icon mờ), KHÔNG "· —".
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25)).setEnabled("defrost_front_state", true)
        val chip = TopStripChips.render(cfg, CarStatus()).first { it.icon != "ic-leaf" }
        assertFalse(chip.text.contains("—"), "datum bật/tắt chưa đọc KHÔNG được hiện dấu — (vô nghĩa + chiếm chỗ), thấy: \"${chip.text}\"")
        assertEquals(ChipTone.NEUTRAL, chip.tone, "chưa đọc = NEUTRAL (không khẳng định tắt)")
    }

    @Test
    fun `mot datum bat ky dat duoc len thanh tren`() {
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25)).setEnabled("tyre_p_fl", true)
        assertTrue(cfg.has("tyre_p_fl"))
        val chips = TopStripChips.render(cfg, CarStatus(tyres = CarStatus.Tyres(pFlKpa = 241.0)))
        assertEquals(2, chips.size)
        // Giá trị đi qua lớp đơn vị: 241 kPa ⇒ mặc định dự án là **bar** ⇒ "2.4 bar". Chip KHÔNG được hiện số thô.
        assertTrue(chips[1].text.endsWith("2.4 bar"), "chip datum phải hiện giá trị đã quy đổi, thấy: ${chips[1].text}")
        // Chip phải dùng nhãn NGẮN, không nhãn đầy: thanh trên chỉ rộng vài chục pixel, "Áp lốp trước-trái" sẽ đẩy
        // đồng hồ ra khỏi thanh. Đây là mục đích của `TelemetrySpec.short` (RW0 mục c).
        val spec = TelemetryRegistry.byId("tyre_p_fl")!!
        assertEquals("Lốp TT", spec.shortLabel, "datum bề-mặt-hẹp phải có nhãn ngắn")
        assertTrue(chips[1].text.startsWith(spec.shortLabel), "chip phải dùng nhãn ngắn, thấy: ${chips[1].text}")
        assertFalse(chips[1].text.startsWith(spec.label), "không được dùng nhãn đầy trên chip")
        assertTrue(chips[1].desc.isNotBlank(), "chip rất ngắn ⇒ phải có câu đọc cho trình đọc màn hình")
    }

    @Test
    fun `KHONG nhan nut hay goi lenh - day la quyet dinh co chu y`() {
        // Lý do ở KDoc TopStripConfig: chip ~24dp là quá nhỏ cho một đích chạm, và một cú chạm lệch có thể bắn lệnh
        // xe không hoàn lại được (vd mở khoá cửa). Đây là bài khoá **ý định**, không phải khoá hiện trạng.
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25))
        assertEquals(cfg, cfg.setEnabled("recirc", true), "nút KHÔNG được lên thanh trên")
        assertEquals(cfg, cfg.setEnabled("mac_leave", true), "gói lệnh KHÔNG được lên thanh trên")
        assertEquals(cfg, cfg.setEnabled("khong_co_ma_nay", true), "mã lạ vẫn bị từ chối như DockConfig")
        assertTrue(TopStripConfig.choices().none { it.kind == CapabilityKind.WRITE }, "màn chọn cũng không được bày nút")
    }

    /**
     * S4 · R12 — **hành động của launcher cũng KHÔNG lên được thanh trên**, và bị chặn do CẤU TẠO.
     *
     * [TopStripConfig.isChippable] hỏi *"có phải mục ĐỌC không"*, chứ không hỏi *"có phải nút không"*. Nhờ chiều
     * khẳng định đó, loại khả năng MỚI mặc định bị từ chối; viết theo chiều phủ định (`!= WRITE`) thì mỗi loại
     * mới lại lọt lên thanh trên cho tới khi có ai nhớ ra phải chặn — và chip 24dp vẫn là một đích chạm quá nhỏ,
     * dù cú bấm đi tới launcher hay tới xe.
     */
    @Test
    fun `hanh dong cua launcher KHONG len duoc thanh tren`() {
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25))
        LauncherActions.ALL.forEach { a ->
            assertEquals(CapabilityKind.LAUNCHER, CapabilityCatalog.kindOf(a.id), "tiền đề: ${a.id} là loại LAUNCHER")
            assertFalse(TopStripConfig.isChippable(a.id), "${a.id} không được coi là chip được")
            assertEquals(cfg, cfg.setEnabled(a.id, true), "${a.id} KHÔNG được lên thanh trên")
        }
        assertTrue(
            TopStripConfig.choices().none { it.kind == CapabilityKind.LAUNCHER },
            "màn chọn chip cũng không được bày chúng — bày ra thứ `setEnabled` sẽ từ chối là để người dùng bấm " +
                "vào chỗ không có gì xảy ra",
        )
    }

    @Test
    fun `day tran thi bo qua chu khong day mot chip khac ra`() {
        var cfg = TopStripConfig(emptyList())
        val ids = TelemetryRegistry.ALL.take(TopStripConfig.CAP + 2).map { it.id }
        ids.forEach { cfg = cfg.setEnabled(it, true) }
        assertEquals(TopStripConfig.CAP, cfg.ids.size, "không được vượt trần")
        assertEquals(ids.take(TopStripConfig.CAP), cfg.ids, "phải giữ những cái vào TRƯỚC, không đẩy ra")
    }

    @Test
    fun `chuoi luu doc lai nguyen ven va tu chua du lieu hong`() {
        val cfg = TopStripConfig(listOf(TopStripConfig.ENERGY, "tyre_p_fl"))
        assertEquals(cfg, TopStripConfig.decode(TopStripConfig.encode(cfg)), "lưu rồi đọc phải ra y hệt")
        assertEquals(TopStripConfig.DEFAULT, TopStripConfig.decode(null), "chưa có gì ⇒ mặc định")
        assertEquals(TopStripConfig.DEFAULT, TopStripConfig.decode("   "), "rỗng ⇒ mặc định, không để thanh trên trắng")
        assertEquals(TopStripConfig.DEFAULT, TopStripConfig.decode("ma_da_bi_xoa,ma_rac"), "toàn mã lạ ⇒ mặc định")
        // Một mã lạ lẫn giữa mã tốt: bỏ MỤC đó, KHÔNG bỏ cả dòng (mất luôn cấu hình người dùng vì một mã rữa là quá tay).
        assertEquals(
            listOf(TopStripConfig.PM25, "tyre_p_fl"),
            TopStripConfig.decode(TopStripConfig.PM25 + ",ma_rac,tyre_p_fl").ids,
        )
    }

    @Test
    fun `nhan ngan tu lui ve nhan day khi chua khai`() {
        val spec = TelemetryRegistry.ALL.first { it.short == null }
        assertEquals(spec.label, spec.shortLabel, "chưa khai nhãn ngắn ⇒ phải lùi về nhãn đầy, không rỗng")
    }

    // ── Soát U6 (2026-09-13): hai lỗ mà bộ bài cũ không chạm tới ────────────────────────────────────

    /**
     * **KHÔNG hai ô nào trên cùng một màn chọn được mang chữ y hệt nhau** — kể cả 3 chip dựng sẵn.
     *
     * ## Lỗ mà bài này bịt
     * `CapabilityCatalogTest.sau khi phan biet thi KHONG con O nao trung nhau` chạy trên
     * [CapabilityCatalog.all] — mà 3 chip TỔNG HỢP **không có dòng registry nào**, chúng được dựng ngay trong
     * [TopStripConfig.choices]. Nên chúng nằm ngoài cả phép so đó lẫn [CapabilityCatalog.collidingLabels] (⇒ không
     * ô nào được gợi ý loại để phân biệt).
     *
     * [ĐO] soát U6: đổi `pm25_value` từ `"PM2.5"` sang `"Bụi mịn PM2.5"` làm nó **trùng khít** nhãn của chip dựng
     * sẵn `chip_pm25` — hai ô chữ y hệt nhau, cả VI lẫn EN. S4 · R11 làm ca này **nặng hơn** chứ không nhẹ đi: cả
     * hai mã đều thuộc [Domain.CLIMATE] nên từ nay chúng nằm CẠNH NHAU trong cùng một khối của [TopStripConfig.picks]
     * (ô chỉ có nhãn, **không** vẽ dòng phụ ⇒ `displaySub` không cứu được ở đây).
     * Hai ô đó trỏ vào hai việc khác nhau: chip đổi mức 1–6 thành CHỮ, datum là TRỊ SỐ µg/m³.
     *
     * So **cả hai ngôn ngữ**: nhãn Anh là chuỗi riêng, trùng ở một bên không suy ra trùng ở bên kia.
     */
    @Test
    fun `hai o tren cung mot man chon khong duoc mang chu y het nhau`() {
        try {
            listOf(Lang.VI, Lang.EN).forEach { lang ->
                Strings.current = lang
                val dup = TopStripConfig.choices()
                    .groupBy { it.displayLabel }
                    .filterValues { it.size > 1 }
                    .map { (label, g) -> "$label → " + g.map { it.id } }
                assertEquals(
                    emptyList<String>(), dup,
                    "hai ô cùng chữ trong màn chọn chip ($lang) ⇒ người dùng bấm nhầm và không biết mình " +
                        "vừa bấm cái nào — ô ở đây chỉ có nhãn, không có dòng phụ để cứu",
                )
            }
        } finally {
            Strings.current = Lang.VI
        }
    }

    // ── S4 · R11 (2026-09-14): trần 8 + màn chọn bày MỌI mục đọc theo lĩnh vực ──────────────────────

    /**
     * **Trần = 8**, ghim tại `:core` chứ không ở tầng vẽ.
     *
     * Con số này đi vào **ba** chỗ ở `:app` (câu chú thích màn chọn · câu nhắc khi đầy · phép chia bề rộng chip của
     * `KachiTopStrip.fitChips`). Khoá nó ở đây nghĩa là ba chỗ đó không thể lệch nhau, và đổi trần là một sửa đổi
     * **có chủ ý** (bài này đỏ) chứ không phải một con số ai đó nới lúc vá giao diện — xem lý do ở KDoc [TopStripConfig.CAP].
     */
    @Test
    fun `tran chip la 16 - khoa o core`() {
        assertEquals(16, TopStripConfig.CAP, "V6 (owner 2026-09-25): 10 → 16 item trên header bar (user tự lựa)")
        assertTrue(
            TopStripConfig.DEFAULT.ids.size < TopStripConfig.CAP,
            "nới trần KHÔNG được kéo theo mặc định: ai không sửa gì vẫn phải thấy đúng 3 chip cũ",
        )
    }

    /**
     * Màn chọn bày **ĐỦ** mọi thứ đặt được — và mỗi mã **đúng một ô**.
     *
     * ## Lỗ mà bài này bịt
     * Tới U6 màn chọn bày ≤ 7 ô, phần còn lại giấu sau nút *"Thêm chip khác…"* (một hộp thoại phẳng 120 dòng). R11
     * bỏ hộp thoại đó và bày thẳng theo lĩnh vực ⇒ phép kiểm đúng không còn là *"có nút kia không"* mà là **tổng số
     * ô = tổng số mã đặt được**. Thiếu một khối (vd quên [Domain] mới) thì mã của khối đó biến mất khỏi màn chọn mà
     * không có gì báo — đúng loại hụt chỉ phép đếm mới thấy.
     *
     * Phép đếm thứ hai (mỗi mã một ô) khoá lỗi RW0: cả hai màn chọn giữ bảng tra `tiles[id]`, một mã hai ô thì bảng
     * bị ghi đè và ô trước nói sai cấu hình.
     */
    @Test
    fun `man chon bay du moi muc dat duoc va moi ma dung mot o`() {
        // ⚠ (V) 2026-09-17: mã mẫu đổi `tyre_p_fl` → `batt_temp` — tám ô lốp lẻ nay ẩn khỏi bộ chọn, và khối
        //    "đang bật" CỐ Ý vẫn bày mã ẩn (để còn gỡ được), nên dùng mã ẩn ở đây là so hai tập khác nhau.
        val cfg = TopStripConfig.DEFAULT.setEnabled("batt_temp", true)
        val ids = TopStripConfig.picks(cfg).flatMap { it.picks }.map { it.id }
        assertEquals(ids.distinct(), ids, "một mã hai ô ⇒ bảng tra `tiles[id]` bị ghi đè (đúng lỗi RW0)")
        assertEquals(
            TopStripConfig.choices().map { it.id }.toSet(), ids.toSet(),
            "màn chọn phải bày ĐỦ mọi mã đặt được — không còn hộp thoại 'Thêm chip khác…' để giấu phần thiếu",
        )
        // Sàn 100 → 88 sau (V) FEATURE-FILTER 2026-09-17 (12 datum xoá + 8 ô lốp ẩn ⇒ [ĐO] 94 ô).
        // ⚠ WP8 2026-09-20: 88 → 60 (purge 29 datum ⇒ [ĐO] 67 ô). Sàn là chốt chống bộ quét hỏng.
        assertTrue(ids.size > 60, "…và đó là hàng chục mục đọc, không phải 7 ô như trước R11: ${ids.size}")
    }

    /** Khối *"đang bật"* đứng ĐẦU và giữ đúng thứ tự chip trên thanh — nó là ảnh của thanh trên, không phải một tập. */
    @Test
    fun `khoi dang bat dung dau va theo dung thu tu chip tren thanh`() {
        val cfg = TopStripConfig(listOf(TopStripConfig.ENERGY, "batt_temp", TopStripConfig.PM25))
        val first = TopStripConfig.picks(cfg).first()
        assertTrue(first.on, "khối đầu phải là khối 'đang bật'")
        assertTrue(first.open, "khối 'đang bật' không bao giờ gấp — nó là thứ trả lời 'thanh trên đang có gì'")
        assertEquals(cfg.ids, first.picks.map { it.id }, "thứ tự ô phải bằng thứ tự chip, không phải thứ tự đăng ký")
        // …và không mã nào của nó còn ở khối lĩnh vực (xem lỗi `tiles[id]` ở bài trên).
        val rest = TopStripConfig.picks(cfg).drop(1).flatMap { it.picks }.map { it.id }
        assertTrue(cfg.ids.none { it in rest }, "mục đang bật KHÔNG được lặp lại ở khối lĩnh vực")
    }

    /**
     * Khối lĩnh vực mặc định **mở khi đang có chip của nó trên thanh**, gấp khi không.
     *
     * Lý do là R4 (*mỗi nhóm ≤ 2 màn cuộn*): bày phẳng 123 ô là ~31 hàng, một mình nó đã dài hơn cả nhóm. Gấp theo
     * lĩnh vực đưa màn chọn về ~9 dòng tiêu đề + hai khối mở. Mở đúng lĩnh vực đang dùng vì đó là nơi người dùng
     * nhiều khả năng muốn thêm cái kế bên (đang xem 1 lốp thì thường muốn xem lốp thứ hai).
     */
    @Test
    fun `khoi linh vuc mac dinh GAP het - R4`() {
        // [ĐO] T4 2026-09-14: mở sẵn lĩnh vực đang có chip làm trang dài quá 2 màn (Năng lượng 28 + Khí hậu 11 ô).
        val cfg = TopStripConfig(listOf("tyre_p_fl"))
        val sections = TopStripConfig.picks(cfg)
        assertTrue(sections.first().on && sections.first().open, "khối 'đang bật' luôn mở")
        assertTrue(sections.filter { !it.on && it.domain != null }.all { !it.open }, "mọi lĩnh vực gấp sẵn, kể cả lĩnh vực đang có chip")
        val def = TopStripConfig.picks(TopStripConfig.DEFAULT).filter { !it.on && it.domain != null && it.open }
        assertTrue(def.isEmpty(), "mặc định của dự án cũng gấp hết — R4 ≤ 2 màn cuộn")
    }

    /**
     * **Chip đã đặt từ bản trước phải còn đường GỠ**, kể cả khi mã của nó đã bị ẩn khỏi bộ chọn (U6
     * [CapabilityCatalog.HIDDEN_FROM_PICKER]).
     *
     * [decode] cố ý GIỮ mã ẩn (nó lọc bằng [TopStripConfig.isChippable], không bằng danh sách) — đúng thiết kế: khoá
     * lưu bền của người dùng không được biến mất. Nhưng nếu khối *"đang bật"* của màn chọn cũng dựng từ [choices]
     * thì chip ấy **hiện trên thanh mà không còn ô nào để bấm tắt**: một trạng thái không có đường ra. Đó là lý do
     * khối đó tra thẳng [CapabilityCatalog.pick] thay vì lọc trên [choices].
     */
    @Test
    fun `chip mang ma da an van co o de bam go`() {
        val hidden = CapabilityCatalog.HIDDEN_FROM_PICKER.keys.firstOrNull { TopStripConfig.isChippable(it) }
        assertTrue(hidden != null, "tiền đề: có ít nhất một mã vừa bị ẩn vừa đặt được lên thanh trên")
        val cfg = TopStripConfig.decode(hidden + "," + TopStripConfig.PM25)
        assertTrue(cfg.has(hidden!!), "decode phải GIỮ mã đã ẩn — nó là khoá lưu bền của người dùng")
        assertTrue(
            TopStripConfig.choices().none { it.id == hidden },
            "tiền đề: mã ẩn KHÔNG được bày ra để đặt thêm",
        )
        assertTrue(
            TopStripConfig.picks(cfg).first().picks.any { it.id == hidden },
            "…nhưng khối 'đang bật' PHẢI bày nó, không thì chip này không bao giờ gỡ được nữa",
        )
        // Và không được đẻ ra ô thừa ở khối lĩnh vực (mã ẩn vẫn phải im lặng ở đó).
        val rest = TopStripConfig.picks(cfg).drop(1).flatMap { it.picks }.map { it.id }
        assertTrue(hidden !in rest, "mã đã ẩn chỉ được hiện ở khối 'đang bật', không phải một lời mời đặt thêm")
    }

    // ══ V3 · R14 — CÔNG TẮC NHÃN CHIP (owner 2026-09-16) ═════════════════════════════════════════════════

    /**
     * Owner: *"chỉ hiện icon và chỉ số thôi, text nhiều chật chỗ, cho cái toggle hiện text label"*.
     *
     * Thử làm nó ĐỎ: bỏ `if (labels)` ở `TopStripChips.chip` ⇒ nửa sau của bài này đỏ ngay.
     */
    @Test
    fun `tat nhan thi chip chi con icon va gia tri`() {
        val on = TopStripChips.render(TopStripConfig.DEFAULT, full)
        val off = TopStripChips.render(TopStripConfig.DEFAULT.copy(showLabels = false), full)
        assertEquals(listOf("PM2.5 · Tốt", "24°C ngoài", "82% · 418 km"), on.map { it.text })
        assertEquals(listOf("Tốt", "24°C", "82% · 418 km"), off.map { it.text })
        // Icon KHÔNG được mất — nó là thứ duy nhất còn nói "chip này về cái gì".
        assertEquals(on.map { it.icon }, off.map { it.icon })
    }

    @Test
    fun `chip DATUM thuong cung bo nhan, giu nguyen don vi`() {
        val cfg = TopStripConfig(listOf("tyre_p_fl"))
        val st = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 240.0))
        val on = TopStripChips.render(cfg, st).single().text
        val off = TopStripChips.render(cfg.copy(showLabels = false), st).single().text
        assertTrue(on.contains(" · "), "bật nhãn: '<nhãn ngắn> · <giá trị>' — nhận '$on'")
        assertEquals(on.substringAfter(" · "), off, "tắt nhãn giữ NGUYÊN phần giá trị + đơn vị")
    }

    /**
     * Câu cho trình đọc màn hình **luôn đầy đủ**, kể cả khi tắt nhãn.
     *
     * Tắt nhãn là một quyết định về chỗ trên thanh, không phải về nội dung: người không nhìn được màn hình mà
     * nghe đúng hai chữ *"24 độ C"* thì mất hẳn thông tin *"của cái gì"*.
     */
    @Test
    fun `tat nhan KHONG lam cut cau cho trinh doc man hinh`() {
        val on = TopStripChips.render(TopStripConfig.DEFAULT, full)
        val off = TopStripChips.render(TopStripConfig.DEFAULT.copy(showLabels = false), full)
        assertEquals(on.map { it.desc }, off.map { it.desc })
    }

    @Test
    fun `co nhan KHONG nam trong chuoi ma — chuoi cu tren dia van doc duoc`() {
        // Chuỗi `top_strip` đã nằm trên đĩa của xe đang chạy; nhét thêm một ô lạ vào là bản cũ dựng chip rỗng.
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25), showLabels = false)
        assertEquals("chip_pm25", TopStripConfig.encode(cfg), "cờ nhãn KHÔNG được lọt vào chuỗi mã")
        assertFalse(TopStripConfig.decode("chip_pm25", showLabels = false).showLabels)
        assertTrue(TopStripConfig.decode("chip_pm25").showLabels, "mặc định là CÓ nhãn (giữ nguyên bản cũ)")
        // Chuỗi hỏng/rỗng vẫn phải giữ lựa chọn nhãn — không thì một dòng prefs hỏng kéo theo cả cách vẽ.
        assertFalse(TopStripConfig.decode(null, showLabels = false).showLabels)
        assertFalse(TopStripConfig.decode("", showLabels = false).showLabels)
        assertFalse(TopStripConfig.decode("ma_khong_ton_tai", showLabels = false).showLabels)
    }

    @Test
    fun `bat tat mot chip KHONG lam mat lua chon nhan`() {
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25), showLabels = false)
        assertFalse(cfg.setEnabled(TopStripConfig.TEMP, true).showLabels)
        assertFalse(cfg.setEnabled(TopStripConfig.PM25, false).showLabels)
    }

    // ══ ICON-STATE (2026-09-21) — datum bật/tắt tô TRẠNG THÁI bằng icon, không bằng chữ ════════════════════

    /** Cấu hình một chip duy nhất = datum sấy kính trước, để bài đọc `chips[0]` không phải đếm. */
    private fun defrostOnly(labels: Boolean = true) =
        TopStripConfig(listOf("defrost_front_state"), showLabels = labels)

    private fun defrost(on: Boolean?) = CarStatus(climate = CarStatus.Climate(defrostFrontOn = on))

    /**
     * [ĐO xe 2026-09-21] chip hiện `"Sấy kính · Tắt"`. Owner: *"trạng thái bật/tắt phải thể hiện bằng ICON
     * active/inactive (màu), KHÔNG bằng chữ Tắt/Bật"*.
     */
    @Test
    fun `chip datum bat-tat KHONG con chu Bat-Tat, trang thai nam o sac thai`() {
        val onChip = TopStripChips.render(defrostOnly(), defrost(true)).single()
        assertEquals(ChipTone.ACTIVE, onChip.tone, "đang sấy ⇒ sắc thái ACTIVE (icon sáng + màu nhấn)")
        val offChip = TopStripChips.render(defrostOnly(), defrost(false)).single()
        assertEquals(ChipTone.INACTIVE, offChip.tone, "đang tắt ⇒ sắc thái INACTIVE (icon mờ)")

        // Chữ chỉ còn NHÃN NGẮN — không còn giá trị nào trên chip, ở cả hai trạng thái và cả hai thứ tiếng.
        val short = TelemetryRegistry.byId("defrost_front_state")!!.shortLabel
        listOf(onChip, offChip).forEach { c ->
            assertEquals(short, c.text, "chip bật/tắt chỉ còn nhãn ngắn, thấy: '${c.text}'")
            setOf("Bật", "Tắt", "On", "Off").forEach { w ->
                assertFalse(c.text.contains(w), "chip KHÔNG được còn chữ '$w', thấy: '${c.text}'")
            }
            assertTrue(c.icon != null, "trạng thái nằm ở icon ⇒ chip bật/tắt PHẢI có icon")
        }
    }

    @Test
    fun `cau doc cho trinh doc man hinh VAN day du - mau thi ho khong thay duoc`() {
        // Cùng lập luận đã ghi cho ca tắt nhãn: bỏ chữ là quyết định về CHỖ trên thanh, không phải về nội dung.
        // Người dùng trình đọc màn hình không thấy được icon mờ/sáng, nên với họ chữ là đường DUY NHẤT.
        assertTrue(TopStripChips.render(defrostOnly(), defrost(false)).single().desc.endsWith("Tắt"))
        assertTrue(TopStripChips.render(defrostOnly(), defrost(true)).single().desc.endsWith("Bật"))
        // Tắt nhãn ⇒ chip chỉ-icon (chuỗi rỗng), nhưng câu đọc vẫn nguyên.
        val bare = TopStripChips.render(defrostOnly(labels = false), defrost(true)).single()
        assertEquals("", bare.text, "tắt nhãn + bật/tắt ⇒ chip chỉ còn icon")
        assertTrue(bare.desc.endsWith("Bật"), "câu đọc không được rỗng theo, thấy: '${bare.desc}'")
    }

    @Test
    fun `chua doc duoc thi TRUNG TINH va KHONG hien dash (B10)`() {
        // Off-car / không có trên trim: icon MỜ ở đây là nói "đang tắt" trong khi sự thật là "không biết" ⇒ giữ
        // NEUTRAL. NHƯNG dấu "· —" là vô nghĩa + chiếm chỗ (owner 2026-09-23) ⇒ BỎ, còn nhãn ngắn (khi bật nhãn).
        val c = TopStripChips.render(defrostOnly(), defrost(null)).single()
        assertEquals(ChipTone.NEUTRAL, c.tone, "chưa đọc được ⇒ NEUTRAL, không phải INACTIVE")
        assertFalse(c.text.contains("—"), "KHÔNG hiện dấu — cho datum bật/tắt chưa đọc, thấy: \"${c.text}\"")
        val short = TelemetryRegistry.byId("defrost_front_state")!!.shortLabel
        assertEquals(short, c.text, "còn nhãn ngắn (bật nhãn), không có '· —'")
    }

    @Test
    fun `datum SO khong bi doi - van NEUTRAL va van hien gia tri`() {
        // Ràng buộc của owner: "ĐỪNG đụng datum số thường (nhiệt/gió/pin)". Bài này canh đúng câu đó.
        val cfg = TopStripConfig(listOf("tyre_p_fl", "cabin_temp", "soc"))
        val chips = TopStripChips.render(
            cfg,
            CarStatus(
                tyres = CarStatus.Tyres(pFlKpa = 241.0),
                climate = CarStatus.Climate(cabinTempC = 24),
                energy = CarStatus.Energy(soc = 82),
            ),
        )
        assertEquals(List(3) { ChipTone.NEUTRAL }, chips.map { it.tone }, "datum số giữ NEUTRAL")
        assertTrue(chips[0].text.endsWith("2.4 bar"), "vẫn hiện giá trị đã quy đổi, thấy: ${chips[0].text}")
        assertTrue(chips[1].text.endsWith("24 °C"), "thấy: ${chips[1].text}")
        assertTrue(chips[2].text.endsWith("82 %"), "thấy: ${chips[2].text}")
    }

    @Test fun `moveEarlier va moveLater doi thu tu chip #15`() {
        val cfg = TopStripConfig(ids = listOf("chip_pm25", "chip_energy", "chip_outside_temp"))
        assertEquals(listOf("chip_energy", "chip_pm25", "chip_outside_temp"), cfg.moveEarlier("chip_energy").ids)
        assertEquals(listOf("chip_pm25", "chip_outside_temp", "chip_energy"), cfg.moveLater("chip_energy").ids)
        // đầu/cuối/không có ⇒ no-op (trả nguyên)
        assertEquals(cfg, cfg.moveEarlier("chip_pm25"))
        assertEquals(cfg, cfg.moveLater("chip_outside_temp"))
        assertEquals(cfg, cfg.moveEarlier("khong_co"))
    }

}
