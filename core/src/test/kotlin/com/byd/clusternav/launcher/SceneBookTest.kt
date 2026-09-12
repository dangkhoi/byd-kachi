package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ P7 + P6 — CẢNH: model thuần ═══════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-scenes-and-widgets.html` §3.1. Sáu nhóm tính chất:
 *  1. **thêm · xoá · đổi tên** và trần 8 (đủ trần phải *nói được*, không im lặng);
 *  2. **encode/decode vòng tròn** — chuỗi lưu là hợp đồng với đĩa của xe đang chạy;
 *  3. **tự chữa dữ liệu hỏng** — nguồn là một chuỗi người dùng sửa tay được, nên không được sập;
 *  4. **tên có ký tự phân tách** — cùng bệnh mà `WorkspacePrefs.addProfile` đã phải lọc `[\r\n]`;
 *  5. **con trỏ cảnh khởi động không được treo**;
 *  6. **R4 (C5 của dự án)** — áp cảnh không đổi ô thì `WorkspaceRenderPlanner` KHÔNG đòi dựng lại ô đó.
 */
class SceneBookTest {

    // ── Hạ tầng ──────────────────────────────────────────────────────────────────────────────────

    private fun state(
        preset: LayoutPreset = LayoutPreset.QUAD,
        slots: List<SlotContent> = listOf(SlotContent.Widget("w_board"), SlotContent.App("com.waze")),
        grid: GridLayout? = null,
        dock: DockConfig = DockConfig(DockEdge.RIGHT, listOf("lock", "window")),
    ) = HomeUiState(
        workspace = WorkspaceState(preset, List(WorkspaceState.SLOT_CAP) { slots.getOrElse(it) { SlotContent.Empty } }),
        dock = dock,
        customLayout = grid,
    )

    private fun bookOf(vararg names: String): SceneBook =
        names.fold(SceneBook.EMPTY) { acc, n -> acc.saved(n, state()) }

    // ── 1 · thêm · xoá · đổi tên · trần ──────────────────────────────────────────────────────────

    @Test
    fun `luu canh moi thi vao so va mang mo on dinh`() {
        val book = SceneBook.EMPTY.saved("Đi làm", state())
        assertEquals(1, book.scenes.size)
        assertEquals("Đi làm", book.scenes[0].name)
        assertEquals("s1", book.scenes[0].id, "mã phải là s1..s8 — ngắn, đọc được, và tái dùng được sau khi xoá")
        assertEquals(LayoutPreset.QUAD, book.scenes[0].preset)
        assertEquals(DockEdge.RIGHT, book.scenes[0].dock.edge)
    }

    @Test
    fun `xoa canh thi bo khoi so`() {
        val book = bookOf("A", "B", "C")
        val after = book.removed("s2")
        assertEquals(listOf("A", "C"), after.scenes.map { it.name })
        // Mã của cảnh vừa xoá được dùng lại cho cảnh sau — an toàn vì dấu nổ máy trỏ theo mã và tự bỏ khi xoá.
        assertEquals("s2", after.saved("D", state()).scenes.last().id)
    }

    @Test
    fun `doi ten giu nguyen ma va noi dung`() {
        val book = bookOf("Đi làm")
        val after = book.renamed("s1", "Đi chơi")
        assertEquals("Đi chơi", after.scenes[0].name)
        assertEquals("s1", after.scenes[0].id, "đổi tên KHÔNG được đổi mã — dấu nổ máy trỏ vào mã")
        assertEquals(book.scenes[0].slots, after.scenes[0].slots)
    }

    @Test
    fun `doi ten sang ten cua canh KHAC thi bi tu choi`() {
        val book = bookOf("A", "B")
        assertSame(book, book.renamed("s1", "B"), "hai cảnh cùng tên làm 'lưu lại cảnh B' ghi đè cảnh không chỉ định")
        // Đổi thành CHÍNH tên cũ thì không sao (người dùng chỉ sửa hoa/thường hay khoảng trắng).
        assertEquals("A", book.renamed("s1", " A ").scenes[0].name)
    }

    @Test
    fun `tran 8 canh — canh thu 9 khong vao va so NOI duoc la da du`() {
        val eight = bookOf("1", "2", "3", "4", "5", "6", "7", "8")
        assertEquals(8, eight.scenes.size)
        assertTrue(eight.full, "sổ phải NÓI được là đã đủ — tầng UI đọc cờ này để báo, không chặn im lặng")
        val ninth = eight.saved("9", state())
        assertEquals(8, ninth.scenes.size, "không được vượt trần")
        assertTrue(ninth.scenes.none { it.name == "9" }, "và cảnh thứ 9 không được lặng lẽ thay cảnh nào")
        assertFalse(bookOf("1").full)
    }

    @Test
    fun `luu trung ten thi GHI DE va giu ca ma lan dau no may`() {
        val book = bookOf("Đi làm").withBootScene("s1")
        val after = book.saved("Đi làm", state(preset = LayoutPreset.ONE))
        assertEquals(1, after.scenes.size, "trùng tên là ghi đè, không phải thêm cảnh thứ hai cùng tên")
        assertEquals(LayoutPreset.ONE, after.scenes[0].preset, "nội dung phải là trạng thái MỚI")
        assertEquals("s1", after.bootSceneId, "lưu lại một cảnh KHÔNG được làm mất dấu nổ máy của nó")
    }

    @Test
    fun `luu trung ten van duoc phep khi da du tran`() {
        val eight = bookOf("1", "2", "3", "4", "5", "6", "7", "8")
        val after = eight.saved("3", state(preset = LayoutPreset.TWO_ROW))
        assertEquals(8, after.scenes.size)
        assertEquals(LayoutPreset.TWO_ROW, after.byName("3")!!.preset, "ghi đè không thêm cảnh nào ⇒ trần không chặn")
    }

    @Test
    fun `ten rong sau khi lam sach thi khong luu gi`() {
        assertSame(SceneBook.EMPTY, SceneBook.EMPTY.saved("   ", state()))
        assertSame(SceneBook.EMPTY, SceneBook.EMPTY.saved("\n|;", state()))
    }

    // ── 2 · encode / decode vòng tròn ────────────────────────────────────────────────────────────

    @Test
    fun `encode roi decode ra dung so cu`() {
        val book = SceneBook.EMPTY
            .saved("Đi làm", state(LayoutPreset.QUAD, listOf(SlotContent.App("com.waze"), SlotContent.Widget(listOf("w_board", "w_pm25")))))
            .saved("Đi xa", state(LayoutPreset.TWO_COL, grid = GridLayout(listOf(GridFrame(0, 0, 7, 6), GridFrame(7, 0, 5, 6)))))
            .withBootScene("s2")
        val round = SceneBook.decode(SceneBook.encode(book), book.bootSceneId)
        assertEquals(book, round, "vòng tròn phải ra ĐÚNG sổ cũ — chuỗi này là hợp đồng với đĩa của xe đang chạy")
        assertEquals("0,0,7,6;7,0,5,6", round.byName("Đi xa")!!.gridLayout)
        assertEquals(listOf("w_board", "w_pm25"), (round.byName("Đi làm")!!.slots[1] as SlotContent.Widget).ids)
    }

    @Test
    fun `so rong encode ra chuoi rong va decode lai duoc`() {
        assertEquals("", SceneBook.encode(SceneBook.EMPTY))
        assertEquals(SceneBook.EMPTY, SceneBook.decode("", null))
        assertEquals(SceneBook.EMPTY, SceneBook.decode(null, null))
    }

    @Test
    fun `nội dung o dung CUNG phep ma hoa voi cho luu bo cuc`() {
        // Nếu cảnh tự nghĩ ra một dạng chuỗi thứ hai thì nó sẽ đọc ra nội dung ô khác thứ người dùng đã lưu.
        listOf(SlotContent.Empty, SlotContent.App("com.waze"), SlotContent.Widget(listOf("w_a", "w_b")))
            .forEach { assertEquals(it, SlotCodec.decode(SlotCodec.encode(it))) }
        assertEquals("app:com.waze", SlotCodec.encode(SlotContent.App("com.waze")), "dạng chuỗi KHÔNG được đổi")
        assertEquals("widget:w_a,w_b", SlotCodec.encode(SlotContent.Widget(listOf("w_a", "w_b"))))
        assertEquals(SlotContent.Empty, SlotCodec.decode("rác không đọc được"))
    }

    /**
     * ⚠⚠ **BÀI QUAN TRỌNG NHẤT CỦA GIAO ĐIỂM CẢNH × WIDGET BÊN THỨ BA.** Cảnh chứa widget của app khác phải đọc lại
     * được — thứ mà 23 bài trước đó KHÔNG bài nào kiểm, vì bài vòng-tròn ở trên chỉ đi qua ba loại ô CŨ.
     *
     * [ĐO] `emulator-5554` 2026-09-12, bản trước bản vá: đặt widget đồng hồ vào ô rồi lưu cảnh ⇒ chuỗi trên đĩa là
     * `s1|CoWidget|ONE|…|widget:g_windows;widget:g_adas;aw:651|com.google.android.deskclock/…;…|BOTTOM|lock,…` =
     * **8 trường** (dấu ngăn id/provider lúc đó là `|`, trùng dấu ngăn TRƯỜNG) ⇒ [SceneBook.decode] bỏ cả bản ghi ⇒
     * khởi động lại launcher thì màn Cài đặt hiện *"Chưa có cảnh nào · 0 / 8"*. Người dùng mất cảnh, không một lời nào.
     */
    @Test
    fun `canh chua widget ben thu ba doc lai duoc nguyen ven`() {
        val provider = "com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider"
        val book = SceneBook.EMPTY.saved(
            "CoWidget",
            state(LayoutPreset.QUAD, listOf(SlotContent.Widget("w_board"), SlotContent.AppWidget(651, provider))),
        )
        val round = SceneBook.decode(SceneBook.encode(book), null)
        assertEquals(1, round.scenes.size, "cảnh có widget bên thứ ba KHÔNG được biến mất khi đọc lại")
        assertEquals(book, round)
        assertEquals(SlotContent.AppWidget(651, provider), round.scenes[0].slots[1], "phải giữ CẢ id lẫn provider")
    }

    /**
     * ⚠⚠ Chốt chặn **NGUYÊN NHÂN**, không phải hiện tượng: đầu ra của [SlotCodec.encode] được **nhúng vào** chuỗi
     * cảnh, nên nó không được chứa ký tự ngăn cấu trúc nào của [SceneBook.RESERVED].
     *
     * Bài trên chứng minh loại ô thứ tư đã đúng; bài này chứng minh **mọi** loại ô đều đúng, kể cả loại thứ năm mai
     * sau — nó liệt kê từ [SlotContent] chứ không từ một danh sách viết tay, nên thêm loại mới mà chọn sai ký tự thì
     * ĐỎ ngay tại đây thay vì đợi người dùng mất cảnh.
     */
    @Test
    fun `khong loai o nao ma hoa ra ky tu ngan cau truc cua chuoi canh`() {
        val samples = listOf(
            SlotContent.Empty,
            SlotContent.App("com.google.android.deskclock"),
            SlotContent.Widget(listOf("w_board", "w_pm25")),
            SlotContent.AppWidget(651, "com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider"),
        )
        // Phủ hết KHÔNG dùng reflection (`sealedSubclasses` đòi `kotlin-reflect` — [ĐO] bản đầu của bài này ném
        // `KotlinReflectionNotSupportedError`, và thêm một thư viện chỉ để đếm loại là cái giá sai). `when` không có
        // `else` mạnh hơn: thêm loại ô thứ năm vào `SlotContent` thì bài này **không biên dịch được**, tức không có
        // cách nào lọt qua trong im lặng.
        fun kindOf(c: SlotContent): String = when (c) {
            SlotContent.Empty -> "trống"
            is SlotContent.App -> "app"
            is SlotContent.Widget -> "thẻ dựng tay"
            is SlotContent.AppWidget -> "widget bên thứ ba"
        }
        assertEquals(
            4, samples.map { kindOf(it) }.distinct().size,
            "mỗi loại ô phải có đúng một mẫu — sửa `when` ở trên xong thì thêm mẫu và nâng số này",
        )
        samples.forEach { c ->
            val s = SlotCodec.encode(c)
            SceneBook.RESERVED.forEach { sep ->
                assertFalse(
                    sep in s,
                    "dạng lưu của $c ('$s') chứa ký tự ngăn cấu trúc ${sep.replace("\n", "\\n")} của chuỗi cảnh " +
                        "⇒ bản ghi cảnh sẽ sai số trường và bị decode BỎ trong im lặng",
                )
            }
        }
        // Và luật phải nói đúng ba ký tự đang dùng thật (bản ghi · trường · ô).
        assertEquals(listOf("\n", "|", ";"), SceneBook.RESERVED)
    }

    // ── 3 · tự chữa dữ liệu hỏng ─────────────────────────────────────────────────────────────────

    @Test
    fun `du lieu hong thi bo muc loi, KHONG sap`() {
        val good = SceneBook.encode(bookOf("Tốt"))
        val raw = listOf(
            "thiếu|trường",                       // sai số trường
            good,                                 // hợp lệ
            "",                                   // dòng rỗng
            "|Không có mã|QUAD||||",              // thiếu mã
            "s5||QUAD||||",                       // thiếu tên
            "s6|Enum lạ|KHÔNG_TỒN_TẠI||app:x|LẠ|lock",   // enum lạ ⇒ lùi mặc định, KHÔNG bỏ cảnh
        ).joinToString("\n")
        val book = SceneBook.decode(raw, null)
        assertEquals(listOf("Tốt", "Enum lạ"), book.scenes.map { it.name })
        val odd = book.byName("Enum lạ")!!
        assertEquals(LayoutPreset.THREE, odd.preset, "tên preset lạ ⇒ lùi mặc định (đừng mất nội dung ô của người dùng)")
        assertEquals(DockEdge.BOTTOM, odd.dock.edge)
        assertEquals(SlotContent.App("x"), odd.slots[0], "nội dung ô — phần người dùng bỏ công nhất — phải còn")
    }

    @Test
    fun `nhieu hon tran thi CAT, va ma trung thi giu ban dau`() {
        val nine = (1..9).joinToString("\n") { "s$it|Tên $it|QUAD||widget:w_board|BOTTOM|lock" }
        assertEquals(SceneBook.CAP, SceneBook.decode(nine, null).scenes.size, "quá trần ⇒ cắt, không ném")
        val dup = "s1|Đầu|QUAD||widget:w_board|BOTTOM|lock\ns1|Sau|ONE||widget:w_pm25|TOP|window"
        val book = SceneBook.decode(dup, null)
        assertEquals(listOf("Đầu"), book.scenes.map { it.name }, "mã trùng ⇒ giữ bản ĐẦU (một mã, một cảnh)")
    }

    @Test
    fun `canh co nhieu o hon tran hien tai thi KHONG nem`() {
        // Ca THẬT: bản sau nới trần ô rồi người dùng hạ cấp bản. `WorkspaceState.of` sẽ ném ở ca này.
        val many = List(WorkspaceState.SLOT_CAP + 3) { SlotContent.Widget("w_board") }
        val scene = Scene("s1", "Quá nhiều", LayoutPreset.QUAD, null, many, DockConfig())
        assertEquals(WorkspaceState.SLOT_CAP, scene.workspaceState().slots.size)
        // Và thiếu ô thì đệm ô trống, cũng không ném.
        val few = Scene("s2", "Ít", LayoutPreset.ONE, null, listOf(SlotContent.App("a")), DockConfig())
        assertEquals(WorkspaceState.SLOT_CAP, few.workspaceState().slots.size)
        assertEquals(SlotContent.Empty, few.workspaceState().slots.last())
    }

    // ── 4 · tên có ký tự phân tách ───────────────────────────────────────────────────────────────

    @Test
    fun `ten co ky tu phan tach bi lam sach truoc khi vao so`() {
        val dirty = "Đi\nlàm|buổi;sáng"
        val book = SceneBook.EMPTY.saved(dirty, state())
        assertEquals("Đi làm buổi sáng", book.scenes[0].name)
        // Và quan trọng hơn: chuỗi lưu vẫn đọc lại đúng MỘT cảnh (không bị tên cắt đôi bản ghi).
        val round = SceneBook.decode(SceneBook.encode(book), null)
        assertEquals(1, round.scenes.size)
        assertEquals("Đi làm buổi sáng", round.scenes[0].name)
    }

    @Test
    fun `ten dai bi cat ve tran do dai`() {
        val long = "X".repeat(Scene.NAME_MAX + 40)
        assertEquals(Scene.NAME_MAX, SceneBook.EMPTY.saved(long, state()).scenes[0].name.length)
    }

    // ── 5 · con trỏ cảnh khởi động ───────────────────────────────────────────────────────────────

    @Test
    fun `canh khoi dong tro toi canh da xoa thi TU BO`() {
        val book = bookOf("A", "B").withBootScene("s2")
        assertEquals("s2", book.bootSceneId)
        val after = book.removed("s2")
        assertNull(after.bootSceneId, "xoá cảnh đang là cảnh nổ máy ⇒ dấu tự bỏ, không để con trỏ treo")
        assertNull(after.bootScene())
        // Cả khi con trỏ treo đến từ ĐĨA (sửa tay / lưu bởi bản khác) thì lượt đọc cũng phải gỡ nó.
        assertNull(SceneBook.decode(SceneBook.encode(bookOf("A")), "s7").bootSceneId)
        assertNull(SceneBook.decode(null, "s3").bootSceneId, "không có cảnh nào thì không có cảnh khởi động")
    }

    @Test
    fun `khong nhan ma khong thuoc so, va bo dau duoc`() {
        val book = bookOf("A")
        assertSame(book, book.withBootScene("s9"), "mã lạ ⇒ giữ nguyên, không nhận con trỏ treo")
        val marked = book.withBootScene("s1")
        assertNotNull(marked.bootScene())
        assertNull(marked.withBootScene(null).bootSceneId, "phải có đường BỎ dấu mà không phải xoá cảnh")
    }

    // ── 6 · R4 (C5) — áp cảnh không được làm app trong ô mở lại ──────────────────────────────────

    /**
     * ⚠⚠ **Đây là bài quan trọng nhất của T1.** Ràng buộc C5 của dự án: đổi cấu hình KHÔNG được làm app đang chiếu
     * trong ô bị nhả/gắn lại. Bài đi qua **đúng đường sản phẩm** ([withScene] → [WorkspaceRenderPlanner.decide]) chứ
     * không dựng lại luật ở đây, nên nếu ai đó sửa `withScene` thành ba bước rời thì bài này đỏ.
     */
    @Test
    fun `ap canh cung so o va cung noi dung thi KHONG dung lai o App`() {
        val live = state(
            LayoutPreset.QUAD,
            listOf(SlotContent.App("com.waze"), SlotContent.Widget("w_board"), SlotContent.Widget("w_pm25")),
        )
        // Cảnh chụp CHÍNH trạng thái đang dùng ⇒ gọi lại nó thì không có gì phải dựng lại.
        val scene = Scene.capture("s1", "Y nguyên", live)
        val next = live.withScene(scene)
        val plan = WorkspaceRenderPlanner.decide(
            old = live.workspace, new = next.workspace,
            builtSlotCount = next.preset.slotCount, statusChanged = false,
            slotCount = next.preset.slotCount,
        )
        assertTrue(plan is WorkspaceRenderPlan.PerSlot, "cùng bố cục ⇒ không được là 'dựng lại tất cả'")
        assertEquals(
            emptyList<Int>(), (plan as WorkspaceRenderPlan.PerSlot).rebuild,
            "không ô nào đổi ⇒ KHÔNG ô nào được dựng lại (C5: app trong ô không bị nhả/gắn lại)",
        )
    }

    @Test
    fun `ap canh chi doi mot o thi CHI o do dung lai — o App khac giu nguyen`() {
        val live = state(LayoutPreset.QUAD, listOf(SlotContent.App("com.waze"), SlotContent.Widget("w_board")))
        val scene = Scene.capture("s1", "Đổi ô 2", state(LayoutPreset.QUAD, listOf(SlotContent.App("com.waze"), SlotContent.Widget("w_pm25"))))
        val next = live.withScene(scene)
        val plan = WorkspaceRenderPlanner.decide(
            old = live.workspace, new = next.workspace,
            builtSlotCount = next.preset.slotCount, statusChanged = false,
            slotCount = next.preset.slotCount,
        )
        assertEquals(listOf(1), (plan as WorkspaceRenderPlan.PerSlot).rebuild, "chỉ ô đã đổi nội dung")
    }

    // ── §6 OQ1 — cảnh KHÔNG mang hình nền / chip / đơn vị / giao diện / hồ sơ ─────────────────────

    @Test
    fun `ap canh KHONG doi hinh nen, chip, don vi, giao dien hay ho so`() {
        val live = HomeUiState(
            workspace = WorkspaceState(LayoutPreset.ONE, List(WorkspaceState.SLOT_CAP) { SlotContent.Empty }),
            wallpaper = WallpaperPrefs.DEFAULT.copy(enabled = true),
            topStrip = TopStripConfig(listOf("chip_pm25")),
            unitPrefs = UnitPrefs.DEFAULT.with(Quantity.TEMPERATURE, "°F"),
            themeMode = ThemeMode.DAY,
            langMode = LangMode.EN,
            activeProfile = "Vợ",
            autostart = false,
        )
        val next = live.withScene(Scene.capture("s1", "Cảnh", state()))
        assertEquals(live.wallpaper, next.wallpaper, "cảnh là cách bố trí VÙNG LÀM VIỆC, không phải cả giao diện")
        assertEquals(live.topStrip, next.topStrip)
        assertEquals(live.unitPrefs, next.unitPrefs)
        assertEquals(live.themeMode, next.themeMode)
        assertEquals(live.langMode, next.langMode)
        assertEquals(live.activeProfile, next.activeProfile)
        assertEquals(live.autostart, next.autostart)
        assertEquals(live.scenes, next.scenes, "áp cảnh không được đụng chính sổ cảnh")
        // Nhưng ba thứ nó SỞ HỮU thì phải đổi.
        assertEquals(LayoutPreset.QUAD, next.preset)
        assertEquals(DockEdge.RIGHT, next.dock.edge)
    }

    @Test
    fun `chup canh bat dung bo cuc tu ve, va goi lai thi giai ma nguoc`() {
        val grid = GridLayout(listOf(GridFrame(0, 0, 5, 6), GridFrame(5, 0, 7, 3), GridFrame(5, 3, 7, 3)))
        val scene = Scene.capture("s1", "Tự vẽ", state(grid = grid))
        assertEquals(WorkspaceGrid.encode(grid), scene.gridLayout)
        assertEquals(grid, scene.grid())
        // Bố cục sẵn ⇒ null (không phải chuỗi rỗng), khớp giao kèo của HomeUiState.customLayout.
        assertNull(Scene.capture("s2", "Sẵn", state(grid = null)).gridLayout)
        assertNull(Scene.capture("s3", "Rỗng", state(grid = GridLayout(emptyList()))).gridLayout)
        assertNull(Scene("s4", "x", LayoutPreset.ONE, "", emptyList(), DockConfig()).grid())
    }

    @Test
    fun `danh sach nut rong thi doc ra nut mac dinh — khop cach loadDock doc`() {
        // Cùng ca rỗng, cùng câu trả lời như `WorkspacePrefs.loadDock` — hai lối đọc khác nhau cho cùng một chuỗi
        // mới là chỗ sinh lệch. Giá phải trả (không phân biệt "không nút nào" với "chưa lưu") ghi rõ ở KDoc decode.
        val book = SceneBook.decode("s1|A|QUAD||widget:w_board|BOTTOM|", null)
        assertEquals(ControlRegistry.defaultEnabledIds(), book.scenes[0].dock.enabled)
    }
}
