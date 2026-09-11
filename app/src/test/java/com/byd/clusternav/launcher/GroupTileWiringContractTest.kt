package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * G1 · T3 — khoá **DÂY NỐI** của ô nhóm: những bất biến nằm ở chỗ ghép giữa `:core` và View Android, nơi test đơn vị
 * không tới được (dự án không dùng Robolectric, và ca "nhịp trạng thái xe 1 lần/giây" **không quan sát được off-car**
 * vì không có xe thì trạng thái không đổi).
 *
 * Năm nhóm bất biến, mỗi nhóm là một chỗ dự án ĐÃ trả giá:
 *  1. **Làm mới TẠI CHỖ** — ô nhóm không được thay view theo nhịp trạng thái xe (nhóm kính/cửa/đèn có nút bên trong
 *     ⇒ thay view là **mất cú bấm**, bệnh [SOÁT P1-1]).
 *  2. **OQ1** — chạm ô con chỉ để xem; lệnh chỉ từ hàng nút.
 *  3. **Một ngưỡng** — bảng lốp dùng LẠI [TyreBoard]; `:app` không có ngưỡng thứ hai (lỗi đã xảy ra thật: `< 2.2`
 *     viết tại chỗ trong widget lốp cũ).
 *  4. **Một lớp đơn vị** — số đi qua [UnitFormat] ở `:core`, bộ vẽ không tự đổi đơn vị ([ĐO] bảng lốp từng ghi `°C`
 *     khi người dùng chọn `°F`).
 *  5. **Một danh sách thành viên** — lấy từ [CapabilityGroups], không chép tay sang tầng vẽ.
 *
 * ⚠ Mọi phép cắt vùng đi qua [SourceRoots.body] — nó **tự nổ** nếu mốc không tồn tại. Bản cũ dùng
 * `substringAfter/Before` với mốc kết không nằm sau mốc đầu nên quét tràn tới hết tệp, tức assert `contains` gần như
 * không thể đỏ (14 bài đã chứng minh là test giả).
 */
class GroupTileWiringContractTest {

    private val widgets by lazy { code("src/main/java/com/byd/clusternav/launcher/WidgetViews.kt") }

    /**
     * Tầng vẽ nhóm = **HAI tệp** nối lại (`GroupTiles.kt` cửa vào + `GroupTileViews.kt` bộ vẽ).
     *
     * ⚠⚠ [SOÁT G1] Phải nối, không được chỉ đọc một tệp. `GroupTiles` được **tách ra** khi `GroupTileViews.kt` vượt
     * trần 500 dòng; nếu bài này chỉ quét tệp còn lại thì mọi `assertFalse(...)` bên dưới (không tra dữ liệu · không
     * ngưỡng · không chép mã thành viên · một chỗ mở cổng ra xe · một mã hex) **thôi phủ** phần vừa tách — tức
     * *tách tệp* trở thành cách lách bài canh, đúng họ lỗi "bài quét vùng sai" mà dự án đã trả giá.
     *
     * Có bài [tang ve nhom van la dung cac tep da khai] canh chính giả định đó, để mai ai tách thêm tệp nữa mà
     * quên khai vào đây thì đỏ.
     */
    private val tileFiles = listOf(
        "src/main/java/com/byd/clusternav/launcher/GroupTiles.kt",
        "src/main/java/com/byd/clusternav/launcher/GroupTileViews.kt",
        "src/main/java/com/byd/clusternav/launcher/GroupTileParts.kt",
    )
    private val tiles by lazy { tileFiles.joinToString("\n") { code(it) } }
    private val radar by lazy { code("src/main/java/com/byd/clusternav/launcher/RadarBoardView.kt") }
    private val board by lazy { code("src/main/kotlin/com/byd/clusternav/launcher/GroupBoard.kt") }

    /** Đọc source rồi **bỏ chú thích**: bài này canh CODE, không canh văn xuôi (KDoc có nhắc chính token bị cấm). */
    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    /**
     * Mọi tệp `.kt` dựng ô nhóm PHẢI có trong [tileFiles].
     *
     * Không có bài này thì bài canh yếu đi **âm thầm** mỗi lần ai tách thêm một tệp: các `assertFalse` vẫn xanh vì
     * chúng chỉ soi những tệp đã khai. Dò theo dấu hiệu *"tệp này dựng ô nhóm"* = có gọi `GroupBoard.` hoặc
     * `GroupTileView`, trừ chính ô vẽ radar (đã quét riêng qua [radar]) và các tệp chỉ **gọi** ô nhóm.
     */
    @Test
    fun `tang ve nhom van la dung cac tep da khai`() {
        val dir = SourceRoots.path("src/main/java/com/byd/clusternav/launcher")
        val declared = tileFiles.map { it.substringAfterLast('/') }.toSet()
        // Tệp chỉ ĐIỀU PHỐI (gọi GroupTiles.build/mini) hoặc là ô vẽ riêng — không phải bộ dựng ô nhóm.
        val notBuilders = mapOf(
            "WidgetViews.kt" to "chỗ GỌI (rẽ nhánh sang GroupTiles), không dựng ô nhóm — đã quét riêng qua `widgets`",
            "RadarBoardView.kt" to "ô vẽ Canvas của một nhóm BOARD — đã quét riêng qua `radar`",
            "ControlDockView.kt" to "thanh nút: chỉ hỏi tóm tắt — đã quét riêng trong GroupPickerWiringContractTest",
        )
        val builders = java.nio.file.Files.list(dir).use { s ->
            s.map { it.fileName.toString() }
                .filter { it.endsWith(".kt") }
                .toList()
        }.filter { name ->
            name !in notBuilders &&
                code("src/main/java/com/byd/clusternav/launcher/$name")
                    .let { it.contains("GroupBoard.") || it.contains("GroupTileView") }
        }.toSet()
        assertEquals(
            declared, builders,
            "tệp dựng ô nhóm không khớp danh sách quét ⇒ phần lệch KHÔNG bị bài canh nào phủ. " +
                "Thêm tệp mới vào tileFiles, hoặc khai lý do vào notBuilders.",
        )
    }

    // ── 1 · Ô nhóm đặt được vào ô giữa màn ───────────────────────────────────────────────────────

    @Test
    fun `o giua man re nhanh sang o NHOM, va re TRUOC nhanh hanh dong`() {
        val fn = SourceRoots.body(widgets, "fun build(")
        assertTrue(fn.contains("CapabilityGroups.byId("), "phải nhận ra mã nhóm ở ô giữa màn")
        assertTrue(fn.contains("GroupTiles.build("), "và dựng bằng bộ vẽ nhóm")
        val groupAt = fn.indexOf("CapabilityGroups.byId(")
        val writeAt = fn.indexOf("CapabilityCatalog.isWrite(")
        assertTrue(
            groupAt in 1 until writeAt,
            "nhánh nhóm phải xét TRƯỚC nhánh hành động — cùng thứ tự với CapabilityCatalog.kindOf, để không phụ " +
                "thuộc vào việc mã nhóm tình cờ không trùng bộ nào khác",
        )
        assertTrue(fn.contains("telemetry(ctx, id"), "đường telemetry cũ phải còn nguyên (không phá thứ đang chạy)")
    }

    @Test
    fun `o nen cung nhan duoc nhom, khong roi vao duong telemetry`() {
        // Rơi vào đường telemetry thì `TelemetryReadout.of("g_tyres")` trả null ⇒ ô hiện MÃ TRẦN ("g_tyres") cho
        // người dùng — sai im lặng, không sập, nên không ai phát hiện.
        val fn = SourceRoots.body(widgets, "private fun mini(")
        assertTrue(fn.contains("CapabilityGroups.byId("), "ô nén phải nhận ra mã nhóm")
        assertTrue(fn.contains("GroupTiles.mini("), "và dựng ô tóm tắt riêng")
    }

    @Test
    fun `nhip trang thai xe LAM MOI TAI CHO o nhom, khong thay view`() {
        val fn = SourceRoots.body(widgets, "fun refreshRead(")
        assertTrue(fn.contains("as? GroupTileView"), "phải nhận ra ô nhóm")
        assertTrue(fn.contains(".refresh(data)"), "và gọi đường làm mới tại chỗ của nó")
        val groupAt = fn.indexOf("as? GroupTileView")
        val removeAt = fn.indexOf("removeViewAt")
        assertTrue(
            groupAt in 1 until removeAt,
            "nhánh nhóm phải nằm TRƯỚC chỗ tháo view: đảo thứ tự là tháo/gắn cả hàng nút bên trong nhóm mỗi giây",
        )
        // Và phải THOÁT sau khi làm mới, không rơi tiếp xuống nhánh thay view (rơi tiếp = làm cả hai việc).
        val afterGroup = fn.substring(groupAt)
        assertTrue(
            afterGroup.indexOf("return") in 1 until afterGroup.indexOf("removeViewAt"),
            "sau khi làm mới tại chỗ phải thoát khỏi ô con này",
        )
    }

    @Test
    fun `bo ve nhom co duong lam moi tai cho, va duong do KHONG cham hang nut`() {
        val refresh = SourceRoots.body(tiles, "fun refresh(")
        assertTrue(refresh.contains("binders["), "làm mới = đổ lại chữ qua bộ nối")
        assertFalse(
            refresh.contains("actionsRow("),
            "hàng nút dựng MỘT LẦN ở bind(); chạm tới nó trong refresh là mở lại đúng cái bệnh đã vá",
        )
        assertFalse(refresh.contains("removeAllViews(); binders.clear()"), "làm mới không được dựng lại cả ô")
        val bind = SourceRoots.body(tiles, "fun bind(")
        assertTrue(bind.contains("actionsRow("), "hàng nút dựng ở bind()")
    }

    /**
     * ⚠⚠ [KIỂM TOÁN UX mục 4c] Ô con chỉ hiện icon khi icon **phân biệt được**.
     *
     * [ĐO] nhóm ADAS có 8/10 mục cùng icon sóng radar; nhóm *Người ngồi* có 3 mục cùng icon ghế. Ở đó icon không
     * mang thông tin nào mà vẫn ăn bề cao của nhãn và con số. Quyết định *"có phân biệt được không"* nằm ở `:core`
     * ([GroupBoardModel.iconsDistinguish]) — bài này canh việc tầng vẽ **có hỏi** nó, vì nếu không hỏi thì luật ở
     * `:core` chỉ là một thuộc tính không ai đọc (đúng loại "nút chết" mà dự án cấm).
     */
    @Test
    fun `o con chi hien icon khi icon phan biet duoc`() {
        val fn = SourceRoots.body(tiles, "private fun stripBody(")
        assertTrue(fn.contains("m.iconsDistinguish"), "tầng vẽ phải hỏi :core xem icon có phân biệt được không")
        assertTrue(fn.contains("!m.hasActions"), "và vẫn giữ điều kiện cũ: nhóm có nút thì nhường bề cao cho nút")
    }

    // ── 2 · OQ1: chạm ô con chỉ để xem ───────────────────────────────────────────────────────────

    @Test
    fun `cham o con KHONG ban lenh xe - lenh chi tu hang nut`() {
        val cell = SourceRoots.body(tiles, "private fun stripCell(")
        assertFalse(cell.contains("setOnClickListener"), "ô con của dải KHÔNG được gắn chạm (OQ1)")
        assertFalse(cell.contains("setOnLongClickListener"), "kể cả giữ — ô con nhỏ, lệnh kính/cửa không hoàn lại")
        assertFalse(cell.contains("control"), "ô con không có đường ra xe")
        val lead = SourceRoots.body(tiles, "private fun leadRow(")
        assertFalse(lead.contains("setOnClickListener"), "số chính của thẻ CARD cũng chỉ để xem")
        // Cổng ra xe chỉ có ĐÚNG ở hàng nút.
        val actions = SourceRoots.body(tiles, "private fun actionsRow(")
        assertTrue(actions.contains("ControlTileFactory("), "nút dựng bằng CÙNG bộ dựng với thanh nút")
        assertEquals(
            1, Regex("""ControlTileFactory\(""").findAll(tiles).count(),
            "chỉ được có MỘT chỗ mở cổng ra xe trong tệp này",
        )
    }

    // ── 3 · Một ngưỡng: bảng lốp dùng lại, không có bản thứ hai ──────────────────────────────────

    @Test
    fun `bang lop dung LAI bo dung da co, khong co ban thu hai trong tang ve`() {
        assertFalse(
            tiles.contains("TyreBoardView("),
            "bộ vẽ nhóm KHÔNG được tự dựng bảng lốp — nó nhận qua cổng vào (lambda) từ WidgetViews",
        )
        assertFalse(tiles.contains("TyreBoard.readings("), "và không tự đọc lại 4 bánh")
        val body = SourceRoots.body(tiles, "private fun boardBody(")
        assertTrue(body.contains("tyreBoardPort"), "bảng lốp đến từ cổng vào")
        assertTrue(body.contains("CapabilityGroups.TYRES.id"), "nhận nhóm Lốp bằng mã của CHÍNH nhóm đó")
        // Chỗ gọi phải truyền đúng bộ dựng đang chạy (một bản duy nhất).
        assertTrue(
            SourceRoots.body(widgets, "fun build(").contains("tyreBoard(c, d)"),
            "WidgetViews phải truyền bộ dựng bảng lốp hiện có vào ô nhóm",
        )
    }

    @Test
    fun `tang ve nhom KHONG chua nguong nao`() {
        // Ngưỡng non/căng/lệch (2.0 / 3.2 / 0.3 bar) và ngưỡng bụi thuộc :core. Số nào trong hai tệp vẽ cũng phải là
        // số HÌNH HỌC/CỠ CHỮ, không được là số phán xét.
        listOf("TyreBoard.LOW_BAR", "TyreBoard.HIGH_BAR", "TyreBoard.SPREAD_BAR", "Pm25Filter", "2.2").forEach {
            assertFalse(tiles.contains(it), "tầng vẽ nhóm không được biết ngưỡng: $it")
            assertFalse(radar.contains(it), "ô vẽ radar không được biết ngưỡng: $it")
        }
        // Ô vẽ radar cũng KHÔNG tự quyết mức nào là đỏ — nó hỏi :core.
        assertTrue(radar.contains("GroupBoard.radarTone("), "màu vùng phải theo sắc thái do :core quyết định")
        assertFalse(
            radar.contains("RADAR_ALERT_LEVEL ="),
            "ngưỡng vùng radar khai ở :core (một chỗ), không khai lại trong ô vẽ",
        )
    }

    // ── 4 · Một lớp đơn vị ───────────────────────────────────────────────────────────────────────

    @Test
    fun `so cua o nhom di qua lop don vi o core, tang ve khong tu doi don vi`() {
        assertTrue(tiles.contains("GroupBoard.of("), "tầng vẽ lấy model đã quyết định xong từ :core")
        listOf("TelemetryReadout.of(", "UnitFormat.apply(", "Units.", "Quantity.").forEach {
            assertFalse(
                tiles.contains(it),
                "tầng vẽ nhóm KHÔNG được tự tra dữ liệu/đổi đơn vị ($it) — đó là cách bảng lốp từng ghi °C khi " +
                    "người dùng chọn °F",
            )
            assertFalse(radar.contains(it), "ô vẽ radar cũng không ($it)")
        }
        // Và :core thì PHẢI đi qua nó.
        val cell = SourceRoots.body(board, "private fun cell(")
        assertTrue(cell.contains("TelemetryReadout.of("), "giá trị đọc từ bộ đăng ký")
        assertTrue(cell.contains("UnitFormat.apply("), "rồi áp lựa chọn đơn vị của người dùng")
        assertTrue(cell.contains("shortLabel"), "nhãn ô con dùng nhãn NGẮN")
    }

    // ── 5 · Một danh sách thành viên ─────────────────────────────────────────────────────────────

    @Test
    fun `thanh vien nhom lay tu CapabilityGroups, khong chep tay sang tang ve`() {
        val of = SourceRoots.body(board, "fun of(g: CapabilityGroup")
        assertTrue(of.contains("g.reads.map"), "ô con sinh từ danh sách XEM của nhóm")
        assertTrue(of.contains("g.writes.map"), "nút sinh từ danh sách BẤM của nhóm")
        // Tầng vẽ không được có MỘT mã thành viên nào viết tay: đó là bản sao thứ hai phải giữ đồng bộ bằng trí nhớ.
        listOf("\"tyre_p_", "\"tyre_t_", "\"window_", "\"door_", "\"light_", "\"radar_", "\"seatbelt_", "\"pm25_")
            .forEach {
                assertFalse(tiles.contains(it), "tầng vẽ nhóm chép tay mã thành viên: $it")
                assertFalse(radar.contains(it), "ô vẽ radar chép tay mã thành viên: $it")
            }
    }

    @Test
    fun `core khong giu ma mau - chi giu SAC THAI`() {
        // Cùng luật với ChipTone của RW0: [ĐO] bản nháp trước viết #37d67a ở :core trong khi theme là #34d399.
        assertFalse(board.contains("#"), ":core không được giữ mã màu hex")
        assertFalse(board.contains("KachiTheme"), ":core không được biết bảng màu của :app")
        assertFalse(board.contains("import android"), ":core phải thuần JVM")
        // Và :app phải là nơi DUY NHẤT dịch sắc thái sang màu.
        assertTrue(tiles.contains("fun tintOf(") && tiles.contains("fun fillOf("), "bảng dịch sắc thái → màu ở :app")
        assertTrue(tiles.contains("KachiTheme.AMBER") && tiles.contains("KachiTheme.RED"), "màu lấy từ bảng màu chung")
    }

    @Test
    fun `mau nen o con suy ra tu bang mau chung, khong khai bang mau thu hai`() {
        val fill = SourceRoots.body(tiles, "fun fillOf(")
        assertTrue(fill.contains("alpha(KachiTheme."), "nền suy ra từ chính màu của bảng màu chung + kênh trong suốt")
        val stroke = SourceRoots.body(tiles, "fun strokeOf(")
        assertTrue(stroke.contains("alpha(KachiTheme."), "viền cũng vậy")
        // Đếm mã hex viết trực tiếp: chỉ được đúng MỘT (nền ô con bình thường, dùng lại giá trị ô nén đang có).
        assertEquals(
            1, Regex(""""#[0-9a-fA-F]{6,8}"""").findAll(tiles).count(),
            "mỗi mã hex thêm vào đây là một bước tiến tới bảng màu thứ hai",
        )
    }

    // ── 6 · Phép chia hàng (HÀNH VI, không phải dây nối) ─────────────────────────────────────────

    /**
     * ⚠⚠ **[SOÁT G1] Bài này lấp một lỗ CÓ THẬT, không phải thêm cho đủ.**
     *
     * [ĐO] thử phá: thay thân [GroupTileView.rowsOf] thành `return emptyList()` — tức **mọi ô nhóm mất sạch ô con,
     * chỉ còn cái nhãn ở đầu ô** — rồi chạy `:app:testDebugUnitTest`: **0 bài đỏ**. Hàm này là toán thuần trên
     * `List` (không chạm Android), lại là chỗ đã sinh ra một lỗi nhìn-thấy-được ([ĐO] nhóm Kính 4 ô con với
     * `perRow = 5` từng bỏ trống 1/5 bề ngang), nhưng chưa có bài nào gọi nó.
     *
     * Bốn tính chất phải giữ, và cả bốn đều kiểm được off-car:
     *  1. **Không mất phần tử** — nối các hàng lại phải ra đúng danh sách gốc, đúng thứ tự.
     *  2. **Không hàng nào vượt trần** — `perRow` là *giới hạn*.
     *  3. **Chia ĐỀU, không nhồi-rồi-tràn** — hàng cuối chỉ có 1 ô con trông như lỗi bố cục.
     *  4. **Ca biên không ném** — rỗng / trần 0 / trần âm.
     */
    @Test
    fun `phep chia hang giu du o con, khong vuot tran, va chia deu`() {
        val cases = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 20)
        val caps = listOf(1, 2, 3, 4, 5, 6)
        cases.forEach { n ->
            val items = (1..n).toList()
            caps.forEach { cap ->
                val rows = GroupTileView.rowsOf(items, cap)
                assertEquals(items, rows.flatten(), "n=$n cap=$cap: không được mất/đổi thứ tự ô con nào")
                assertTrue(rows.all { it.size <= cap }, "n=$n cap=$cap: hàng vượt trần $cap → ${rows.map { it.size }}")
                assertTrue(rows.none { it.isEmpty() }, "n=$n cap=$cap: hàng rỗng = một dải trắng giữa bảng")
                // Chia đều: hàng dài nhất và ngắn nhất chênh nhau tối đa 1 ô.
                val sizes = rows.map { it.size }
                assertTrue(
                    sizes.max() - sizes.min() <= 1,
                    "n=$n cap=$cap: hàng lệch nhau hơn 1 ô ($sizes) — hàng cuối trơ trọi trông như lỗi bố cục",
                )
            }
        }
        // Ba ca ĐÍCH DANH mà KDoc của hàm hứa (nếu ai đổi sang `chunked(max)` thì ca thứ hai đỏ).
        assertEquals(listOf(5, 4), GroupTileView.rowsOf((1..9).toList(), 5).map { it.size }, "9 ⇒ 5+4")
        assertEquals(listOf(5, 5), GroupTileView.rowsOf((1..10).toList(), 6).map { it.size }, "10 với trần 6 ⇒ 5+5")
        assertEquals(listOf(4), GroupTileView.rowsOf((1..4).toList(), 5).map { it.size }, "4 với trần 5 ⇒ một hàng 4")
    }

    @Test
    fun `phep chia hang khong nem o ca bien`() {
        assertTrue(GroupTileView.rowsOf(emptyList<Int>(), 5).isEmpty(), "danh sách rỗng ⇒ không hàng nào")
        assertTrue(GroupTileView.rowsOf(listOf(1, 2), 0).isEmpty(), "trần 0 ⇒ không hàng nào, KHÔNG chia cho 0")
        assertTrue(GroupTileView.rowsOf(listOf(1, 2), -3).isEmpty(), "trần âm ⇒ không hàng nào")
    }

    /**
     * ⚠ **Bài GHIM: sửa [GroupTileView.rowsOf] cho khớp giao kèo KHÔNG được đổi hình dạng ô nhóm nào đang có.**
     *
     * Bản trước ra sai với `(7,3)` / `(13,5)`, nhưng cả 12 nhóm hiện nay đều không rơi vào ca đó. Bài này ghim
     * **số ô mỗi hàng** của cả 12 nhóm ở đúng ba trần đang dùng (`MAX_PER_ROW=5` cho dải · `CARD_PER_ROW=3` cho số
     * phụ của thẻ · `ACTIONS_PER_ROW=6` cho hàng nút) ⇒ nếu bản sửa làm xê dịch một hàng nào thì bài đỏ, và nếu
     * mai ai thêm/bớt thành viên thì cũng phải xem lại con số ở đây.
     */
    @Test
    fun `phep chia hang khong doi hinh dang cua 12 nhom dang co`() {
        val strip = CapabilityGroups.ALL.associate { g ->
            g.id to GroupTileView.rowsOf(g.reads, 5).map { it.size }
        }
        assertEquals(
            mapOf(
                "g_tyres" to listOf(4, 4), "g_windows" to listOf(4), "g_doors" to listOf(5, 5),
                "g_lights" to listOf(5, 4), "g_ambient" to listOf(5), "g_adas" to listOf(5, 5),
                "g_occupants" to listOf(5), "g_parking" to listOf(2), "g_climate" to listOf(5, 5),
                "g_energy" to listOf(5, 5), "g_battery" to listOf(5, 4), "g_trip" to listOf(3, 3),
            ),
            strip,
            "dải STRIP (trần 5) đổi hình dạng",
        )
        val card = CapabilityGroups.ALL.associate { g ->
            g.id to GroupTileView.rowsOf(g.reads.drop(1), 3).map { it.size }
        }
        assertEquals(
            mapOf(
                "g_tyres" to listOf(3, 2, 2), "g_windows" to listOf(3), "g_doors" to listOf(3, 3, 3),
                "g_lights" to listOf(3, 3, 2), "g_ambient" to listOf(2, 2), "g_adas" to listOf(3, 3, 3),
                "g_occupants" to listOf(2, 2), "g_parking" to listOf(1), "g_climate" to listOf(3, 3, 3),
                "g_energy" to listOf(3, 3, 3), "g_battery" to listOf(3, 3, 2), "g_trip" to listOf(3, 2),
            ),
            card,
            "số phụ của thẻ CARD (trần 3) đổi hình dạng",
        )
        val actions = CapabilityGroups.ALL.filter { it.hasWrites }.associate { g ->
            g.id to GroupTileView.rowsOf(g.writes, 6).map { it.size }
        }
        assertEquals(
            mapOf("g_windows" to listOf(6), "g_doors" to listOf(6), "g_lights" to listOf(4)),
            actions,
            "hàng nút (trần 6) đổi hình dạng",
        )
    }

    /**
     * ⚠⚠ **[SOÁT G1] Lỗ thứ hai — bẫy `getDefaultSize` không có ai canh.**
     *
     * [ĐO] thử phá: đổi ô chèn chỗ trống của hàng nút từ `LayoutParams(0, 0, 1f)` về `LayoutParams(0, WRAP, 1f)` —
     * đúng cái lỗi mà KDoc trong [GroupTileViews] ghi là *"đã ăn mất CẢ dải mục đọc"* (thân ô cao **0**, hàng nút
     * báo cao **648px**, 9 ô con của nhóm *Đèn* được dựng mà không hiện một pixel) — rồi chạy
     * `:app:testDebugUnitTest`: **0 bài đỏ**. Nó chỉ được tìm ra bằng cách đo trên máy ảo.
     *
     * Không đo được chiều cao thật off-car (không có Robolectric), nhưng **cấm được cái mẫu gây ra nó**: một `View`
     * trơ dùng làm ô chèn KHÔNG được khai `WRAP_CONTENT`, vì `View.getDefaultSize` trả TRỌN `specSize` khi spec là
     * `AT_MOST` ⇒ ô "trống" lại giãn hết chỗ.
     */
    @Test
    fun `o chen cho trong khong duoc khai WRAP - bay getDefaultSize`() {
        listOf("private fun gridRows(", "private fun actionsRow(").forEach { sig ->
            val fn = SourceRoots.body(tiles, sig)
            val fillers = fn.lines().filter { it.contains("repeat(cols") }
            assertTrue(fillers.isNotEmpty(), "$sig: phải có chỗ chèn ô trống (hàng phải đều nhau)")
            fillers.forEach { line ->
                assertTrue(
                    line.contains("View(context)"),
                    "$sig: ô chèn phải là một View trơ, không phải một ô con thật: $line",
                )
                assertFalse(
                    line.contains("WRAP"),
                    "$sig: ô chèn khai WRAP ⇒ View.getDefaultSize giãn nó hết chỗ còn lại và thân ô cao 0 " +
                        "(lỗi đã đo trên máy ảo: thân 0px / hàng nút 648px). Dùng 0 hoặc MATCH: $line",
                )
            }
        }
    }
}
