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
        // U9 pha 2: sổ đăng ký bảng BOARD (chọn ô vẽ + đổ dữ liệu tại chỗ), tách ra khi `GroupTileViews.kt` chạm
        // trần 500 dòng. Nó gọi `GroupBoard.` ⇒ là một phần tầng vẽ nhóm ⇒ phải nằm trong tầm mọi assertFalse dưới.
        "src/main/java/com/byd/clusternav/launcher/GroupBoardBinder.kt",
    )
    private val tiles by lazy { tileFiles.joinToString("\n") { code(it) } }
    // ⚠ `RadarBoardView.kt` + `SideBoardView.kt` đã XOÁ 2026-09-16 cùng toàn bộ ADAS/an toàn (owner) — hai nhóm
    // duy nhất dùng chúng (`g_parking` · `g_adas`) cũng không còn, nên không có gì để quét riêng nữa.
    private val door by lazy { code("src/main/java/com/byd/clusternav/launcher/DoorBoardView.kt") }
    /**
     * Phần `:core` của ô nhóm = **HAI tệp** nối lại (`GroupBoard.kt` phần quyết định + `GroupBoardModel.kt` các kiểu).
     *
     * ⚠⚠ Phải nối, không được chỉ đọc một tệp — cùng lý do với [tileFiles]: `GroupBoardModel.kt` được **tách ra** khi
     * `GroupBoard.kt` vượt trần 500 dòng, và nếu bài này chỉ quét tệp còn lại thì ba phép kiểm `:core` (không giữ mã
     * màu hex · không biết `KachiTheme` · thuần JVM) **thôi phủ** phần vừa tách ⇒ *tách tệp* thành cách lách bài canh.
     */
    private val boardFiles = listOf(
        "src/main/kotlin/com/byd/clusternav/launcher/GroupBoard.kt",
        "src/main/kotlin/com/byd/clusternav/launcher/GroupBoardModel.kt",
    )
    private val board by lazy { boardFiles.joinToString("\n") { code(it) } }

    /** Đọc source rồi **bỏ chú thích**: bài này canh CODE, không canh văn xuôi (KDoc có nhắc chính token bị cấm). */
    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    /**
     * Mọi tệp `.kt` dựng ô nhóm PHẢI có trong [tileFiles].
     *
     * Không có bài này thì bài canh yếu đi **âm thầm** mỗi lần ai tách thêm một tệp: các `assertFalse` vẫn xanh vì
     * chúng chỉ soi những tệp đã khai. Dò theo dấu hiệu *"tệp này dựng ô nhóm"* = có gọi `GroupBoard.` hoặc
     * `GroupTileView`, trừ chính các ô vẽ Canvas (đã quét riêng) và các tệp chỉ **gọi** ô nhóm.
     */
    @Test
    fun `tang ve nhom van la dung cac tep da khai`() {
        val dir = SourceRoots.path("src/main/java/com/byd/clusternav/launcher")
        val declared = tileFiles.map { it.substringAfterLast('/') }.toSet()
        // Tệp chỉ ĐIỀU PHỐI (gọi GroupTiles.build/mini) hoặc là ô vẽ riêng — không phải bộ dựng ô nhóm.
        val notBuilders = mapOf(
            "WidgetViews.kt" to "chỗ GỌI (rẽ nhánh sang GroupTiles), không dựng ô nhóm — đã quét riêng qua `widgets`",
            "DoorBoardView.kt" to
                "ô vẽ Canvas của nhóm *Cửa & khoang* (U9 pha 2) — đã quét riêng qua `door` (nó hỏi " +
                    "`GroupBoard.doorPlan` để biết bộ phận nào đang mở, thay vì tự đoán từ mã datum)",
            "ControlDockView.kt" to "thanh nút: chỉ hỏi tóm tắt — đã quét riêng trong GroupPickerWiringContractTest",
        )
        val builders = java.nio.file.Files.list(dir).use { s ->
            s.map { it.fileName.toString() }
                .filter { it.endsWith(".kt") }
                .toList()
        }.filter { name ->
            name !in notBuilders &&
                code("src/main/java/com/byd/clusternav/launcher/$name")
                    // ⚠ 2026-09-16 nới từ `"GroupBoard."` sang `"GroupBoard"`: sau lượt gỡ ADAS/an toàn,
                    // `GroupBoardBinder` không còn gọi `GroupBoard.<gì>` nào (hai bảng radar/sơ-đồ-bên đã xoá) —
                    // nó chỉ còn nhận `GroupBoardModel`. Giữ dấu hiệu chặt cũ thì chính tệp sổ-đăng-ký-bảng rơi ra
                    // ngoài tầm mọi `assertFalse` bên dưới, tức bài canh tự yếu đi vì một lượt xoá ở chỗ khác.
                    .let { it.contains("GroupBoard") || it.contains("GroupTileView") }
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
     * [ĐO] nhóm *Kính* có 4 mục cùng icon kính; nhóm *Cửa & khoang* có 4 mục cùng icon cửa. Ở đó icon không
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
        val actions = SourceRoots.body(tiles, "internal fun actionsRow(")
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
        assertTrue(
            SourceRoots.body(tiles, "private fun boardBody(").contains("tyreBoardPort"),
            "bảng lốp đến từ cổng vào",
        )
        // U9 pha 2: phép CHỌN bảng dời sang `GroupBoardBinder.build` (xem KDoc tệp đó) — quét đúng vùng mới, không
        // quét cả `tiles` (quét tràn = bài canh giả, xem KDoc SourceRoots.body).
        val pick = SourceRoots.body(code("src/main/java/com/byd/clusternav/launcher/GroupBoardBinder.kt"), "fun build(")
        assertTrue(pick.contains("tyreBoard?.invoke("), "bảng lốp vẫn đến từ cổng vào, không dựng tại chỗ")
        assertTrue(pick.contains("CapabilityGroups.TYRES.id"), "nhận nhóm Lốp bằng mã của CHÍNH nhóm đó")
        assertTrue(pick.contains("CapabilityGroups.DOORS.id"), "và nhóm Cửa & khoang cũng vậy (U9 pha 2)")
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
            assertFalse(door.contains(it), "ô vẽ bảng cửa không được biết ngưỡng: $it")
        }
        // Bảng cửa cũng KHÔNG tự quyết bộ phận nào đang mở — nó hỏi :core.
        assertTrue(door.contains("GroupBoard.doorPlan("), "trạng thái bộ phận phải do :core quyết định")
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
            assertFalse(door.contains(it), "ô vẽ bảng cửa cũng không ($it)")
        }
        // Và :core thì PHẢI đi qua nó.
        val cell = SourceRoots.body(board, "private fun cell(")
        assertTrue(cell.contains("TelemetryReadout.of("), "giá trị đọc từ bộ đăng ký")
        assertTrue(cell.contains("UnitFormat.apply("), "rồi áp lựa chọn đơn vị của người dùng")
        // ⚠ U5 · T2 — mốc này SIẾT lại, không nới: trước đây nó soi `shortLabel` (nhãn ngắn tiếng Việt), nay đòi
        // `displayShortLabel` = nhãn ngắn **theo ngôn ngữ đang dùng**. Luật cũ (*"ô con dùng nhãn NGẮN, không dùng
        // nhãn đầy"*) giữ nguyên và thêm một đòi hỏi: bản ngắn đó phải đi qua lớp ngôn ngữ, không thì ô nhóm tiếng
        // Anh hiện *"Lốp TT"*. Đổi mốc vì tên thuộc tính đổi, KHÔNG vì luật đổi.
        assertTrue(cell.contains("displayShortLabel"), "nhãn ô con dùng nhãn NGẮN, và bản ngắn THEO NGÔN NGỮ")
    }

    // ── 5 · Một danh sách thành viên ─────────────────────────────────────────────────────────────

    @Test
    fun `thanh vien nhom lay tu CapabilityGroups, khong chep tay sang tang ve`() {
        val of = SourceRoots.body(board, "fun of(g: CapabilityGroup")
        assertTrue(of.contains("g.reads.map"), "ô con sinh từ danh sách XEM của nhóm")
        assertTrue(of.contains("g.writes.map"), "nút sinh từ danh sách BẤM của nhóm")
        // Tầng vẽ không được có MỘT mã thành viên nào viết tay: đó là bản sao thứ hai phải giữ đồng bộ bằng trí nhớ.
        // ⚠ Bốn tiền tố cuối (`radar_` · `seatbelt_` · `bsd_` · `adas_`) là mã ĐÃ XOÁ 2026-09-16 cùng toàn bộ
        // ADAS/an toàn (owner). Giữ chúng trong danh sách này KHÔNG phải để canh bản-sao-thứ-hai nữa mà là **cổng
        // chống mọc lại ở tầng vẽ**: chép một mã an toàn vào `:app` là đường vòng quanh việc registry đã gỡ nó.
        listOf(
            "\"tyre_p_", "\"tyre_t_", "\"window_", "\"door_", "\"light_", "\"pm25_",
            "\"radar_", "\"seatbelt_", "\"bsd_", "\"adas_",
        )
            .forEach {
                assertFalse(tiles.contains(it), "tầng vẽ nhóm chép tay mã thành viên: $it")
                // ⚠ Bảng cửa là chỗ CÁM DỖ NHẤT của luật này: nó vẽ đúng bốn vạt cửa, nên viết thẳng `"door_lf"` để
                // tra trạng thái là cách "nhanh" hiển nhiên. Chỗ nối đúng là enum [CarPart] ở `:core`.
                assertFalse(door.contains(it), "ô vẽ bảng cửa chép tay mã thành viên: $it")
            }
        assertTrue(door.contains("GroupBoard.doorPlan("), "bảng cửa phải HỎI :core bộ phận nào đang mở")
        // WP3-v5 — bảng cửa nay nối enum bộ phận sang VỊ TRÍ chấm qua `CarLayout.part(s.part)` (trên ảnh xe), thay
        // vì tra path vector theo mã. Vẫn là "nối bằng enum bộ phận, không bằng mã datum".
        assertTrue(door.contains("CarLayout.part("), "và nối bộ phận → vị trí bằng CarLayout (enum), không bằng mã datum")
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
        // ⚠⚠ WP1 · R1.1 — bài này ĐẢO CHIỀU: bản cũ đòi `fun strokeOf(` tồn tại và cũng suy màu từ bảng chung.
        // Owner 2026-09-20 gỡ mọi viền, và riêng màu ngữ nghĩa thì *"chuyển sang nền/chữ màu"* ⇒ `strokeOf` bị XOÁ
        // (nó là bảng tra VIỀN). Nay phải canh hai điều thay cho nó, nếu không thì bỏ bài cũ là mất bất biến:
        //  (a) `strokeOf` **không được mọc lại** — kể cả dưới dạng "chỉ để tra màu";
        //  (b) nền WARN/ALERT phải **đậm hơn** nền ACTIVE, vì nó đang gánh một mình việc mà nền+viền từng chia nhau.
        //      [ĐO] 0x26 (15%) chỉ cho 1.38× trên nền ô; 0x59 (35%) cho 2.25× — đậm hơn cả cặp nền+viền cũ.
        assertFalse(tiles.contains("fun strokeOf("), "bảng tra VIỀN không được mọc lại (WP1 · R1.1)")
        assertTrue(
            fill.contains("SEMANTIC_ALPHA"),
            "WARN/ALERT phải dùng hằng độ đục riêng (SEMANTIC_ALPHA) — nó là con số gánh nghĩa cảnh báo sau khi " +
                "viền bị gỡ, nên nó phải có TÊN và có lý do tại chỗ, không nằm lẫn trong một chuỗi \"26\"",
        )
        val semantic = Regex("""SEMANTIC_ALPHA\s*=\s*"([0-9a-fA-F]{2})"""").find(tiles)
        assertTrue(semantic != null, "không tìm thấy khai báo SEMANTIC_ALPHA — sửa bài này, đừng để nó quét tràn")
        val semanticA = semantic!!.groupValues[1].toInt(16)
        val activeA = Regex("""GroupTone\.ACTIVE\s*->\s*alpha\(KachiTheme\.ACCENT,\s*"([0-9a-fA-F]{2})"\)""")
            .find(fill)?.groupValues?.get(1)?.toInt(16)
        assertTrue(activeA != null, "không đọc được độ đục của GroupTone.ACTIVE — sửa bài này")
        assertTrue(
            semanticA > activeA!!,
            "nền cảnh báo (0x${semantic.groupValues[1]}) phải ĐẬM hơn nền 'đang bật' (0x%02x): sau WP1 nó là dấu " +
                "hiệu DUY NHẤT của WARN/ALERT trên một màn hình lái xe".format(activeA),
        )
        // Đếm mã hex viết trực tiếp: nay phải là **0**. Trước T1 còn đúng một mã (`CELL_BG` = nền ô con bình thường,
        // dùng lại giá trị ô nén đang có); T1 đưa nó thành vai `KachiTheme.CELL` vì một mã cứng ở đây nghĩa là nền ô
        // con **không đổi theo chủ đề** — trên bảng sáng nó sẽ là một ô tối lọt giữa các thẻ trắng.
        assertEquals(
            0, Regex(""""#[0-9a-fA-F]{6,8}"""").findAll(tiles).count(),
            "mỗi mã hex thêm vào đây là một bước tiến tới bảng màu thứ hai — và nay còn là một ô không theo chủ đề",
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
     * Bản trước ra sai với `(7,3)` / `(13,5)`, nhưng cả 9 nhóm hiện nay đều không rơi vào ca đó. Bài này ghim
     * **số ô mỗi hàng** của cả 9 nhóm ở đúng ba trần đang dùng (`MAX_PER_ROW=5` cho dải · `CARD_PER_ROW=3` cho số
     * phụ của thẻ · `ACTIONS_PER_ROW=6` cho hàng nút) ⇒ nếu bản sửa làm xê dịch một hàng nào thì bài đỏ, và nếu
     * mai ai thêm/bớt thành viên thì cũng phải xem lại con số ở đây.
     *
     * ⚠ [SOÁT P1-1] Hàng nút của nhóm *Đèn* đi từ **4 → 3**: bản vá bỏ `headl` khỏi nhóm vì nó trùng byte với
     * `headlight_mode` (xem `CapabilityGroups.LIGHTS`). Đúng cái bài này sinh ra để làm — bắt người sửa xác nhận thay
     * đổi hình dạng bằng số đo, chứ không để nó lặng lẽ đi qua. Dải XEM và số phụ CARD **không đổi** (chỉ `writes` đổi).
     */
    @Test
    fun `phep chia hang khong doi hinh dang cua 8 nhom dang co`() {
        val strip = CapabilityGroups.ALL.associate { g ->
            g.id to GroupTileView.rowsOf(g.reads, 5).map { it.size }
        }
        assertEquals(
            mapOf(
                // ⚠ WP8 2026-09-20: `g_ambient` gỡ hẳn (hết thành viên) · `g_doors` 10 → 8 ô ⇒ [5,5] thành [4,4].
                "g_tyres" to listOf(4, 4), "g_windows" to listOf(4), "g_doors" to listOf(4, 4),
                "g_lights" to listOf(5, 4), "g_climate" to listOf(5, 4, 4),
                // ⚠ WP8 2026-09-20: `g_battery` còn 4 ô (batt_temp · soh_oem · volt_12v · volt_12v_level) sau khi
                // purge cell_v/target_soc theo (V) FEATURE-FILTER ⇒ [5,4] → [4].
                "g_energy" to listOf(3, 3), "g_battery" to listOf(4), "g_trip" to listOf(3, 3),
            ),
            strip,
            "dải STRIP (trần 5) đổi hình dạng",
        )
        val card = CapabilityGroups.ALL.associate { g ->
            g.id to GroupTileView.rowsOf(g.reads.drop(1), 3).map { it.size }
        }
        assertEquals(
            mapOf(
                "g_tyres" to listOf(3, 2, 2), "g_windows" to listOf(3), "g_doors" to listOf(3, 2, 2),
                "g_lights" to listOf(3, 3, 2), "g_climate" to listOf(3, 3, 3, 3),
                // ⚠ [(V) FEATURE-FILTER 2026-09-17] Nhóm Năng lượng 10 → 6 ô (5 ô sạc xoá theo lệnh owner,
                //    `consumption_50km` thêm vào): STRIP [5,5] → [3,3] · CARD [3,3,3] → [3,2].
                "g_energy" to listOf(3, 2), "g_battery" to listOf(3), "g_trip" to listOf(3, 2),
            ),
            card,
            "số phụ của thẻ CARD (trần 3) đổi hình dạng",
        )
        val actions = CapabilityGroups.ALL.filter { it.hasWrites }.associate { g ->
            g.id to GroupTileView.rowsOf(g.writes, 6).map { it.size }
        }
        assertEquals(
            mapOf("g_windows" to listOf(6, 5), "g_doors" to listOf(5), "g_lights" to listOf(3)),
            actions,
            "hàng nút (trần 6) đổi hình dạng — nhóm Đèn còn 3 nút sau [SOÁT P1-1] (bỏ `headl` vì trùng byte với " +
                "`headlight_mode`), nhóm Cửa & khoang còn 5 nút sau (V) FEATURE-FILTER 2026-09-17 (bỏ " +
                "`mirror_fold_btn` — owner chấm NO); nếu con số này đổi tiếp thì phải xem lại thành viên nhóm, " +
                "không sửa số cho xanh",
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
        listOf("private fun gridRows(", "internal fun actionsRow(").forEach { sig ->
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
