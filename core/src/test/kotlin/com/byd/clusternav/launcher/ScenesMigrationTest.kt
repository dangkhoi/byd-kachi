package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ S4 · R2 — chuyển CẢNH đời cũ thành HỒ SƠ, không mất gì ═══════════════════════════════════════════════════
 *
 * ## ⚠⚠ Vì sao bài này giữ **chuỗi fixture nguyên văn** thay vì luôn gọi `SceneBook.encode`
 * `SceneBook` bị xoá ở T2. Một bài canh chỉ dựng dữ liệu bằng `SceneBook.encode` sẽ **chết theo** — và lúc đó không
 * còn gì chứng minh rằng [ScenesMigration] đọc đúng thứ đang nằm trên đĩa của xe đang chạy. Nên có HAI bài, đi đôi:
 *  1. ở T1 có một bài đối chiếu `fixture` với `SceneBook.encode` — nó chứng minh chuỗi literal dưới đây **đúng là
 *     thứ bản cũ ghi ra**, rồi **chết cùng `SceneBook`** ở T2 (đúng ý đồ, không phải sót);
 *  2. mọi bài còn lại chỉ dùng **chuỗi literal** ⇒ sau T2 chúng vẫn chạy và vẫn khoá đúng hợp đồng với đĩa.
 *
 * Đây là cùng khuôn "fixture lấy nguyên văn từ dump thật" mà CLAUDE.md §10 đòi cho mọi parser.
 */
class ScenesMigrationTest {

    // ── Fixture: chuỗi `<hồ sơ>__scenes` NGUYÊN VĂN do bản 2026-09-14 ghi ra ─────────────────────

    /** Hai cảnh: *Đi làm* (QUAD, 1 widget bên thứ ba + 1 app, thanh nút phải) và *Đi chơi* (bố cục tự vẽ). */
    private val fixture =
        "s1|Đi làm|QUAD||aw:700@com.x/.W;app:com.waze;;;;|RIGHT|lock,window\n" +
            "s2|Đi chơi|THREE|0,0,7,4;7,0,5,6|widget:w_board;;;;;|BOTTOM|"

    // ── 1 · đọc đúng nội dung ────────────────────────────────────────────────────────────────────

    @Test
    fun `moi canh thanh mot ho so cung ten, dung bo cuc o va thanh nut`() {
        val plan = ScenesMigration.plan(fixture, null, "Mặc định", listOf("Mặc định"))
        assertEquals(listOf("Đi làm", "Đi chơi"), plan.newProfiles.map { it.name })

        val work = plan.newProfiles[0]
        assertEquals(LayoutPreset.QUAD, work.workspace.preset)
        assertEquals(SlotContent.AppWidget(700, "com.x/.W"), work.workspace.slots[0], "id widget phải giữ NGUYÊN")
        assertEquals(SlotContent.App("com.waze"), work.workspace.slots[1])
        assertEquals(WorkspaceState.SLOT_CAP, work.workspace.slots.size)
        assertEquals(DockEdge.RIGHT, work.dock.edge)
        assertEquals(listOf("lock", "window"), work.dock.enabled)
        assertNull(work.grid, "cảnh không có bố cục tự vẽ ⇒ hồ sơ dùng bố cục sẵn")

        val play = plan.newProfiles[1]
        assertEquals(2, play.grid?.frames?.size, "bố cục tự vẽ phải giải mã ra đúng số khung")
        assertEquals(
            emptyList<String>(), play.dock.enabled,
            "thanh nút RỖNG phải được giữ rỗng — `DockConfig.setEnabled` chỉ remove, không có sàn (bài học loadDock)",
        )
    }

    @Test
    fun `khong co canh nao thi khong co gi de lam`() {
        assertTrue(ScenesMigration.plan(null, null, "Mặc định", emptyList()).empty)
        assertTrue(ScenesMigration.plan("", "s1", "Mặc định", emptyList()).empty)
        assertTrue(ScenesMigration.plan("   ", null, "Mặc định", emptyList()).empty)
    }

    // ── 2 · cảnh lúc nổ máy → hồ sơ lúc nổ máy ───────────────────────────────────────────────────

    @Test
    fun `canh luc no may thanh HO SO luc no may, tra ve TEN khong phai ma`() {
        assertEquals("Đi chơi", ScenesMigration.plan(fixture, "s2", "Mặc định", emptyList()).bootProfile)
    }

    /** Con trỏ TREO (cảnh đã xoá) ⇒ `null` = "hồ sơ dùng gần nhất", không phải một tên không có thật. */
    @Test
    fun `con tro no may treo thi bo han, khong dung ten khong co that`() {
        assertNull(ScenesMigration.plan(fixture, "s9", "Mặc định", emptyList()).bootProfile)
        assertNull(ScenesMigration.plan(fixture, "", "Mặc định", emptyList()).bootProfile)
        assertNull(ScenesMigration.plan(fixture, null, "Mặc định", emptyList()).bootProfile)
    }

    /** Cảnh nổ máy bị **đổi tên** vì trùng ⇒ con trỏ phải theo tên MỚI, không theo tên gốc. */
    @Test
    fun `canh no may bi doi ten vi trung thi con tro theo ten moi`() {
        val plan = ScenesMigration.plan(fixture, "s1", "Đi làm", listOf("Đi làm"))
        assertEquals("Đi làm 2", plan.bootProfile)
    }

    // ── 3 · trùng tên ⇒ " 2", " 3" ───────────────────────────────────────────────────────────────

    @Test
    fun `trung ten ho so dang co thi them hau to 2 roi 3`() {
        val raw = "s1|A|QUAD||;;;;;|BOTTOM|\ns2|A|QUAD||;;;;;|BOTTOM|\ns3|A|QUAD||;;;;;|BOTTOM|"
        val plan = ScenesMigration.plan(raw, null, "A", listOf("A"))
        assertEquals(listOf("A 2", "A 3", "A 4"), plan.newProfiles.map { it.name })
    }

    @Test
    fun `ho so dang dung luon duoc tinh la da co du khong nam trong danh sach truyen vao`() {
        val raw = "s1|Vợ|QUAD||;;;;;|BOTTOM|"
        assertEquals(listOf("Vợ 2"), ScenesMigration.plan(raw, null, "Vợ", emptyList()).newProfiles.map { it.name })
    }

    /**
     * Tên đủ trần + hậu tố: phần gốc bị **cắt bớt** để tổng vẫn ≤ [ScenesMigration.NAME_MAX].
     *
     * Nối thẳng thì tên hồ sơ dài hơn thứ mọi chỗ khác chấp nhận — mà khoá lưu bền ghép thẳng tên vào tiền tố nên nó
     * im lặng trôi vào đĩa.
     */
    @Test
    fun `ten dai dung tran thi cat bot goc truoc khi noi hau to`() {
        val long = "A".repeat(ScenesMigration.NAME_MAX)
        val plan = ScenesMigration.plan("s1|$long|QUAD||;;;;;|BOTTOM|", null, long, listOf(long))
        val name = plan.newProfiles.single().name
        assertTrue(name.length <= ScenesMigration.NAME_MAX, "tên ra $name dài ${name.length}")
        assertTrue(name.endsWith(" 2"))
    }

    // ── 4 · dữ liệu hỏng: tự chữa, KHÔNG ném ─────────────────────────────────────────────────────

    @Test
    fun `ban ghi sai so truong bi bo, cac canh con lai van vao`() {
        val raw = "rác|rác\ns1|Tốt|QUAD||;;;;;|BOTTOM|\n\ns2|Thiếu|QUAD||;;;;;|BOTTOM"
        assertEquals(listOf("Tốt"), ScenesMigration.plan(raw, null, "M", emptyList()).newProfiles.map { it.name })
    }

    @Test
    fun `enum la thi LUI VE mac dinh chu khong bo ca canh`() {
        val raw = "s1|Lạ|KHONG_CO_PRESET||app:com.waze;;;;;|KHONG_CO_EDGE|lock"
        val rec = ScenesMigration.plan(raw, null, "M", emptyList()).newProfiles.single()
        assertEquals(LayoutPreset.THREE, rec.workspace.preset)
        assertEquals(DockEdge.BOTTOM, rec.dock.edge)
        assertEquals(
            SlotContent.App("com.waze"), rec.workspace.slots[0],
            "nội dung ô là phần người dùng bỏ công nhất — mất nó vì một tên enum đổi là thiệt hại lớn hơn hẳn",
        )
    }

    @Test
    fun `ma trung giu ban DAU va cat ve tran 8`() {
        val dup = "s1|Đầu|QUAD||;;;;;|BOTTOM|\ns1|Sau|QUAD||;;;;;|BOTTOM|"
        assertEquals(listOf("Đầu"), ScenesMigration.plan(dup, null, "M", emptyList()).newProfiles.map { it.name })

        val many = (1..12).joinToString("\n") { "s$it|C$it|QUAD||;;;;;|BOTTOM|" }
        assertEquals(
            ScenesMigration.LEGACY_CAP,
            ScenesMigration.plan(many, null, "M", emptyList()).newProfiles.size,
            "quá trần ⇒ CẮT, không ném — bản cũ đã cắt ở 8 nên đĩa không bao giờ có bản ghi thứ 9 hợp lệ",
        )
    }

    @Test
    fun `ten rong sau khi lam sach thi bo ban ghi, khong sinh ho so khong ten`() {
        val raw = "s1|   |QUAD||;;;;;|BOTTOM|\ns2|Thật|QUAD||;;;;;|BOTTOM|"
        assertEquals(listOf("Thật"), ScenesMigration.plan(raw, null, "M", emptyList()).newProfiles.map { it.name })
    }

    @Test
    fun `chuoi rac hoan toan thi KHONG nem`() {
        listOf("rác", "|||||||", "\n\n\n", "s1|A", " ").forEach { raw ->
            assertTrue(ScenesMigration.plan(raw, "s1", "M", emptyList()).newProfiles.isEmpty(), "vào: $raw")
        }
    }

    /** Cảnh của bản nới trần ô (nhiều ô hơn trần hiện tại) ⇒ CẮT, không ném — ca "người dùng hạ cấp bản". */
    @Test
    fun `canh co nhieu o hon tran hien tai thi KHONG nem`() {
        val slots = (1..WorkspaceState.SLOT_CAP + 3).joinToString(";") { "app:com.p$it" }
        val rec = ScenesMigration.plan("s1|Nhiều|QUAD||$slots|BOTTOM|", null, "M", emptyList()).newProfiles.single()
        assertEquals(WorkspaceState.SLOT_CAP, rec.workspace.slots.size)
    }

    // ── 5 · tên có ký tự phân tách (cùng bệnh WorkspacePrefs.addProfile phải lọc `[\r\n]`) ────────

    @Test
    fun `ten lam sach bo ky tu ngan cau truc va gop khoang trang`() {
        assertEquals("a b", ScenesMigration.sanitiseName("a|b"))
        assertEquals("a b", ScenesMigration.sanitiseName("a;b"))
        assertEquals("a b", ScenesMigration.sanitiseName(" a \n b "))
        assertEquals(ScenesMigration.NAME_MAX, ScenesMigration.sanitiseName("x".repeat(50)).length)
    }

    // ── 6 · lưới an toàn cho widget bên thứ ba trước khi chuyển đổi ──────────────────────────────

    /**
     * Trong cửa sổ *"bản mới đã cài nhưng lượt chuyển đổi chưa chạy"*, id widget nằm trong chuỗi cảnh cũ vẫn phải
     * tính là **đang dùng** — không thì lượt dọn rác lúc khởi động xoá chúng **vĩnh viễn** trước khi kịp thành hồ sơ.
     */
    @Test
    fun `id widget trong chuoi canh doi cu van doc ra duoc`() {
        assertEquals(setOf(700), AppWidgetIds.idsInLegacyScenes(fixture))
        assertEquals(emptySet<Int>(), AppWidgetIds.idsInLegacyScenes(null))
        assertEquals(emptySet<Int>(), AppWidgetIds.idsInLegacyScenes("rác|rác"))
    }

    // ── 7 · ký tự ngăn cấu trúc (bài chuyển nguyên từ `SceneBookTest`, S4 · T2) ──────────────────

    /**
     * ⚠⚠ Chốt chặn **NGUYÊN NHÂN**, không phải hiện tượng: đầu ra của [SlotCodec.encode] được **nhúng vào** chuỗi
     * cảnh đời cũ, nên nó không được chứa ký tự ngăn cấu trúc nào của [ScenesMigration.RESERVED].
     *
     * Bài này theo `SceneBook` sang đây thay vì chết cùng nó: chuỗi cảnh vẫn nằm trên đĩa của xe đang chạy **mãi
     * mãi** (lượt chuyển đổi một-lần là thứ duy nhất còn đọc nó), nên một loại ô thứ năm mai sau chọn sai ký tự vẫn
     * làm mất đúng cảnh đó — chỉ khác là nay mất trong lượt chuyển đổi thay vì lúc gọi cảnh.
     */
    @Test
    fun `khong loai o nao ma hoa ra ky tu ngan cau truc cua chuoi canh`() {
        val samples = listOf(
            SlotContent.Empty,
            SlotContent.App("com.google.android.deskclock"),
            SlotContent.Widget(listOf("w_board", "w_pm25")),
            SlotContent.AppWidget(651, "com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider"),
        )
        // Phủ hết KHÔNG dùng reflection (`sealedSubclasses` đòi `kotlin-reflect`). `when` không có `else` mạnh hơn:
        // thêm loại ô thứ năm vào `SlotContent` thì bài này **không biên dịch được**, tức không lọt qua trong im lặng.
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
            ScenesMigration.RESERVED.forEach { sep ->
                assertFalse(
                    sep in s,
                    "dạng lưu của $c ('$s') chứa ký tự ngăn cấu trúc ${sep.replace("\n", "\\n")} của chuỗi cảnh " +
                        "⇒ bản ghi sẽ sai số trường và bị lượt chuyển đổi BỎ trong im lặng",
                )
            }
        }
        // Và luật phải nói đúng ba ký tự đang dùng thật (bản ghi · trường · ô).
        assertEquals(listOf("\n", "|", ";"), ScenesMigration.RESERVED)
    }
}
