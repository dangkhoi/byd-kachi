package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · R-KC — mã đích phím → nút xe: CODEC + SINH TỪ REGISTRY + NHÓM ═══════════════════════════════════════
 *
 * Owner 03/10 *"cover hết các chức năng mình có"*. Ba thứ dễ hỏng nhất và đều KHÔNG lộ ở UI:
 *  1. **Mã đích lẫn với đích cũ** — một chuỗi `ctl:` bị đọc thành tên gói (hoặc ngược lại) ⇒ phím mở nhầm/không làm gì.
 *  2. **Danh sách chép tay** — thêm nút vào registry mà quên thêm đích ⇒ owner không gán được, không ai biết.
 *  3. **Đảo bằng cờ RAM** — nút không có đường đọc mà vẫn có "đảo" ⇒ đóng cái kính đang đóng (CLAUDE.md §5).
 */
class KeyCtlTargetTest {

    private val trunk get() = ControlRegistry.byId("trunk")!!

    /** Mọi đích sinh ra từ một registry (mặc định registry thật) — đúng phép mà `KeyCtlTargets.groups` dùng. */
    private fun all(reg: List<ControlDef> = ControlRegistry.ALL) = reg.flatMap { KeyCtlTargets.actionsFor(it) }

    // ══ 1 · Codec ══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `moi dich sinh ra deu ma hoa roi doc lai dung chinh no`() {
        val all = all()
        assertTrue(all.size > ControlRegistry.ALL.size, "mỗi nút ≥ 1 việc, đa số ≥ 2")
        all.forEach { t ->
            assertEquals(t, KeyCtlTargets.decode(t.spec), "vòng mã hoá hỏng: ${t.spec}")
            assertTrue(Regex("^ctl:[a-z0-9_]+:(on|off|flip|\\+1|-1|open|close|next|press|=[0-9]+)$").matches(t.spec),
                "mã đích chỉ được mang mã nút + mã việc (không vị trí, không chữ người dùng): ${t.spec}")
        }
        assertEquals(all.size, all.map { it.spec }.toSet().size, "hai việc trùng một mã đích ⇒ tra ngược không tất định")
    }

    /** KC7 — gán phím đi theo HỒ SƠ và bản chia sẻ mang theo (mã đích chỉ là mã nút + mã việc, không vị trí). */
    @Test
    fun `ban chia se ho so mang theo danh sach gan phim`() {
        assertTrue(ProfileSharePolicy.shareable("voicekey_bindings"), "bản chia sẻ phải mang gán phím (owner 03/10)")
        assertTrue("voicekey_bindings" !in ProfileSharePolicy.PRIVATE)
        assertTrue(ProfileSharePolicy.SHAREABLE.getValue("voicekey_bindings").contains("ctl:"), "lý do soát phải nhắc mã nút xe")
    }

    @Test
    fun `vi du cua owner ma hoa dung`() {
        assertEquals("ctl:fan:+1", KeyCtlTarget("fan", KeyCtlAction.UP).spec)
        assertEquals("ctl:win_lf:on", KeyCtlTarget("win_lf", KeyCtlAction.ON).spec)
        assertEquals("ctl:sunshade:=2", KeyCtlTarget("sunshade", KeyCtlAction.SET, 2).spec)
        assertEquals(KeyCtlTarget("fan", KeyCtlAction.DOWN), KeyCtlTargets.decode("ctl:fan:-1"))
    }

    /** Đích ≤ 2.85 (tên gói, sentinel) KHÔNG BAO GIỜ là mã nút xe — dữ liệu cũ đọc lên nguyên vẹn, không migrate. */
    @Test
    fun `dich cu khong bi doc thanh ma nut xe`() {
        listOf("ai.zalo.kiki.car", "com.google.android.apps.maps", "__ASSIST__", "__RECOGNIZER__", "__VOICEKEY231__",
            "__KACHI_VOICE__", "").forEach {
            assertFalse(KeyCtlTargets.isCtl(it), it)
            assertNull(KeyCtlTargets.parse(it), it)
            assertNull(KeyCtlTargets.decode(it), it)
        }
    }

    @Test
    fun `chuoi hong hoac khong khop registry thi decode tra null`() {
        listOf(
            "ctl:", "ctl:fan", "ctl:fan:", "ctl:fan:+2", "ctl:fan:on:x", "ctl:FAN:+1", "ctl:fan:=", "ctl:fan:=-1",
            "ctl:fan:=01", "ctl:fan :+1", "ctl:fan:SET",
        ).forEach { assertNull(KeyCtlTargets.decode(it), "phải từ chối: '$it'") }
        // Cú pháp đúng nhưng KHÔNG hợp registry: nút không còn · việc không thuộc kiểu nút.
        assertNotNull(KeyCtlTargets.parse("ctl:no_such_ctl:on"))
        assertNull(KeyCtlTargets.decode("ctl:no_such_ctl:on"), "nút đã gỡ khỏi registry ⇒ không hợp lệ")
        assertNull(KeyCtlTargets.decode("ctl:fan:on"), "STEP không có Bật")
        assertNull(KeyCtlTargets.decode("ctl:win_lf:+1"), "TOGGLE không có +1")
        assertNull(KeyCtlTargets.decode("ctl:sunshade:=3"), "rèm chỉ có 3 mức (0..2)")
        assertNull(KeyCtlTargets.decode("ctl:trunk:flip"), "cốp chưa có đường đọc ⇒ không có Đảo")
    }

    // ══ 2 · Sinh từ registry — thêm nút là có đích ════════════════════════════════════════════════════════════

    @Test
    fun `moi nut cua registry co it nhat mot dich, va chi nut cua registry`() {
        val ids = all().map { it.controlId }.toSet()
        assertEquals(ControlRegistry.ALL.map { it.id }.toSet(), ids, "đích phải phủ ĐÚNG tập nút của registry")
    }

    /** Bài canh "thêm nút mới ⇒ tự có đích": một dòng registry dựng tay, không ai sửa KeyCtlTargets. */
    @Test
    fun `nut moi them vao registry tu co dich theo kieu`() {
        val step = ControlDef("zz_vol", "Âm lượng mới", "ic", ControlKind.STEP, min = 0, max = 30, labelEn = "New volume")
        val tog = ControlDef("zz_tog", "Mới", "ic", ControlKind.TOGGLE, readKey = "speed")
        val cov = ControlDef("zz_cov", "Rèm mới", "ic", ControlKind.COVER, args = listOf("Đóng", "Mở", "Nửa", "Hé"))
        val sel = ControlDef("zz_sel", "Chọn mới", "ic", ControlKind.SELECT, args = listOf("A", "B", "C"))
        val btn = ControlDef("zz_btn", "Bấm mới", "ic", ControlKind.BUTTON)
        val reg = ControlRegistry.ALL + listOf(step, tog, cov, sel, btn)
        val all = all(reg)
        fun of(id: String) = all.filter { it.controlId == id }.map { it.action to it.level }
        assertEquals(listOf(KeyCtlAction.UP to 0, KeyCtlAction.DOWN to 0), of("zz_vol"))
        assertEquals(listOf(KeyCtlAction.ON to 0, KeyCtlAction.OFF to 0, KeyCtlAction.FLIP to 0), of("zz_tog"))
        assertEquals(
            listOf(KeyCtlAction.OPEN to 0, KeyCtlAction.CLOSE to 0, KeyCtlAction.SET to 2, KeyCtlAction.SET to 3),
            of("zz_cov"), "COVER: mở · đóng · mỗi mức thêm của args; không readKey ⇒ không Đảo",
        )
        assertEquals(listOf(KeyCtlAction.SET to 0, KeyCtlAction.SET to 1, KeyCtlAction.SET to 2), of("zz_sel"),
            "SELECT không readKey ⇒ không Kế tiếp, vẫn chọn thẳng từng lựa chọn")
        assertEquals(listOf(KeyCtlAction.PRESS to 0), of("zz_btn"))
        val groups = KeyCtlTargets.groups(reg)
        assertEquals(1, groups.count { g -> g.targets.any { it.controlId == "zz_vol" } }, "nút mới vào đúng một nhóm")
    }

    /** §5 — Đảo/Kế tiếp chỉ khi nút CÓ đường đọc; số lượng suy từ registry (không ghim con số cho nút cụ thể). */
    @Test
    fun `dao va ke tiep chi co khi nut co duong doc`() {
        ControlRegistry.ALL.forEach { def ->
            val acts = KeyCtlTargets.actionsFor(def).map { it.action }
            val readable = def.readKey.isNotBlank()
            assertEquals(readable && def.kind in setOf(ControlKind.TOGGLE, ControlKind.COVER), KeyCtlAction.FLIP in acts,
                "${def.id}: Đảo ⟺ có readKey (TOGGLE/COVER)")
            assertEquals(readable && def.kind == ControlKind.SELECT && def.args.size >= 2, KeyCtlAction.NEXT in acts,
                "${def.id}: Kế tiếp ⟺ có readKey (SELECT)")
        }
    }

    /**
     * Owner 03/10 *"Cốp mở đóng … 1 nút on/off"*. [ĐO mã 03/10] cốp KHÔNG có đường đọc (`trunk.readKey` rỗng —
     * `tailgate_status` gỡ 25/09, rỗng mọi arg trên xe owner) ⇒ hôm nay cốp chỉ có Mở + Đóng riêng; Đảo bằng cờ RAM
     * bị CẤM. Bài này đỏ ĐÚNG ngày ai đó khai readKey cho `trunk` — khi ấy Đảo tự xuất hiện và câu khẳng định dưới
     * phải đổi cùng lượt với một phép ĐO trên xe (CLAUDE.md §14), không trước.
     */
    @Test
    fun `cot chi co mo va dong rieng cho toi khi co duong doc do tren xe`() {
        assertTrue(trunk.readKey.isBlank(), "[CHƯA BIẾT] đường đọc cốp — có datum mới thì cập nhật bài + spec R-KC")
        assertEquals(
            listOf(KeyCtlTarget("trunk", KeyCtlAction.OPEN), KeyCtlTarget("trunk", KeyCtlAction.CLOSE)),
            KeyCtlTargets.actionsFor(trunk),
        )
    }

    @Test
    fun `vi du owner co mat — kinh trai mo dong, gio cong tru`() {
        val all = all().map { it.spec }.toSet()
        listOf("ctl:win_lf:on", "ctl:win_lf:off", "ctl:win_lf:flip", "ctl:fan:+1", "ctl:fan:-1", "ctl:temp:+1",
            "ctl:trunk:open", "ctl:trunk:close", "ctl:seatc:next", "ctl:seatc:=2", "ctl:windows_close_all:press")
            .forEach { assertTrue(it in all, "thiếu $it") }
    }

    // ══ 3 · Nhóm cho hộp chọn ══════════════════════════════════════════════════════════════════════════════

    @Test
    fun `moi dich nam trong dung mot nhom, nhom khong rong, thu tu CapabilityGroups truoc`() {
        val groups = KeyCtlTargets.groups()
        val flat = groups.flatMap { it.targets }
        assertEquals(all().toSet(), flat.toSet())
        assertEquals(flat.size, flat.toSet().size, "một việc ở hai nhóm")
        assertTrue(groups.none { it.targets.isEmpty() })
        assertEquals(groups.size, groups.map { it.id }.toSet().size)
        val ctlByGroup = groups.associate { g -> g.id to g.targets.map { it.controlId }.toSet() }
        ControlRegistry.ALL.forEach { def ->
            assertEquals(1, ctlByGroup.count { def.id in it.value }, "${def.id} phải ở đúng MỘT nhóm")
        }
        // Nhóm có hàng nút của CapabilityGroups đi trước (Kính · Cửa & khoang · Đèn), rồi tới Domain.
        val capIds = CapabilityGroups.ALL.filter { it.writes.isNotEmpty() }.map { it.id }
        assertEquals(capIds, groups.map { it.id }.take(capIds.size))
        assertTrue(groups.drop(capIds.size).all { it.id.startsWith(KeyCtlTargets.DOMAIN_GROUP_PREFIX) })
        val fanGroup = groups.first { g -> g.targets.any { it.controlId == "fan" } }
        assertEquals("d_climate", fanGroup.id, "gió nằm ở Khí hậu & không khí")
        assertEquals("g_doors", groups.first { g -> g.targets.any { it.controlId == "trunk" } }.id)
    }

    // ══ 4 · Nhãn vi/en ═════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `nhan vi en khong rong, khong trung trong mot nhom, va dung vi du`() {
        assertEquals("Gió +1", KeyCtlTargets.displayLabel(KeyCtlTarget("fan", KeyCtlAction.UP), Lang.VI))
        assertEquals("Fan −1", KeyCtlTargets.displayLabel(KeyCtlTarget("fan", KeyCtlAction.DOWN), Lang.EN))
        assertEquals("Bật Kính lái", KeyCtlTargets.displayLabel(KeyCtlTarget("win_lf", KeyCtlAction.ON), Lang.VI))
        assertEquals("Open Tailgate", KeyCtlTargets.displayLabel(KeyCtlTarget("trunk", KeyCtlAction.OPEN), Lang.EN))
        assertEquals("Rèm che nắng: Nửa", KeyCtlTargets.displayLabel(KeyCtlTarget("sunshade", KeyCtlAction.SET, 2), Lang.VI))
        assertEquals("Sunshade: Half", KeyCtlTargets.displayLabel(KeyCtlTarget("sunshade", KeyCtlAction.SET, 2), Lang.EN))
        for (lang in Lang.entries) {
            KeyCtlTargets.groups().forEach { g ->
                assertTrue(g.labelIn(lang).isNotBlank())
                val labels = g.targets.map { KeyCtlTargets.displayLabel(it, lang) }
                assertTrue(labels.none { it.isBlank() || it.startsWith(KeyCtlTargets.PREFIX) }, "$lang ${g.id}: $labels")
                assertEquals(labels.size, labels.toSet().size, "$lang ${g.id}: hai việc cùng nhãn ⇒ owner không phân biệt: $labels")
            }
        }
        assertEquals("ctl:gone:on", KeyCtlTargets.displayLabelOf("ctl:gone:on", Lang.VI), "mã không còn ⇒ hiện nguyên chuỗi để còn xoá")
    }
}
