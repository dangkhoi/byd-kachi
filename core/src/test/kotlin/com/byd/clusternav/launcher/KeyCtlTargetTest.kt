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
 *  3. **Thiếu Đảo / Kế tiếp** — 2.87 · R-FL1 (owner 03/10 *"cái nào đảo đc phải làm đảo hết nhé, chứ hao phím lắm"*):
 *     MỌI nút TOGGLE/COVER có Đảo, MỌI SELECT có Kế tiếp, không còn hỏi `readKey` (nguồn trạng thái quyết lúc chạy ở
 *     `KeyCtlPlan` — xe nếu đọc được, không thì lệnh cuối Kachi đã gửi; `KeyCtlPlanTest`).
 *  4. **Dữ liệu gán phím cũ** (≤ 2.86, R-FL3) — mọi mã đích bản cũ lưu được phải đọc lên NGUYÊN VẸN.
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
        assertNull(KeyCtlTargets.decode("ctl:fan:flip"), "STEP không có Đảo")
        assertNull(KeyCtlTargets.decode("ctl:fan:next"), "STEP không có Kế tiếp")
        assertNull(KeyCtlTargets.decode("ctl:windows_close_all:flip"), "BUTTON không có Đảo")
        assertNull(KeyCtlTargets.decode("ctl:trunk:next"), "COVER không có Kế tiếp (Đảo mới là việc của nó)")
        // 2.87 · R-FL1: cốp KHÔNG có readKey mà vẫn có Đảo (quyết bằng lệnh cuối — KeyCtlPlan).
        assertEquals(KeyCtlTarget("trunk", KeyCtlAction.FLIP), KeyCtlTargets.decode("ctl:trunk:flip"))
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
        val tog2 = ControlDef("zz_tog2", "Mới không đọc", "ic", ControlKind.TOGGLE)
        val cov = ControlDef("zz_cov", "Rèm mới", "ic", ControlKind.COVER, args = listOf("Đóng", "Mở", "Nửa", "Hé"))
        val sel = ControlDef("zz_sel", "Chọn mới", "ic", ControlKind.SELECT, args = listOf("A", "B", "C"))
        val sel1 = ControlDef("zz_sel1", "Một lựa chọn", "ic", ControlKind.SELECT, args = listOf("A"))
        val btn = ControlDef("zz_btn", "Bấm mới", "ic", ControlKind.BUTTON)
        val reg = ControlRegistry.ALL + listOf(step, tog, tog2, cov, sel, sel1, btn)
        val all = all(reg)
        fun of(id: String) = all.filter { it.controlId == id }.map { it.action to it.level }
        assertEquals(listOf(KeyCtlAction.UP to 0, KeyCtlAction.DOWN to 0), of("zz_vol"))
        assertEquals(listOf(KeyCtlAction.ON to 0, KeyCtlAction.OFF to 0, KeyCtlAction.FLIP to 0), of("zz_tog"))
        assertEquals(listOf(KeyCtlAction.ON to 0, KeyCtlAction.OFF to 0, KeyCtlAction.FLIP to 0), of("zz_tog2"),
            "2.87: TOGGLE không readKey VẪN có Đảo (lệnh cuối)")
        assertEquals(
            listOf(KeyCtlAction.OPEN to 0, KeyCtlAction.CLOSE to 0, KeyCtlAction.SET to 2, KeyCtlAction.SET to 3, KeyCtlAction.FLIP to 0),
            of("zz_cov"), "COVER: mở · đóng · mỗi mức thêm của args · Đảo (không cần readKey)",
        )
        assertEquals(listOf(KeyCtlAction.NEXT to 0, KeyCtlAction.SET to 0, KeyCtlAction.SET to 1, KeyCtlAction.SET to 2), of("zz_sel"),
            "SELECT không readKey VẪN có Kế tiếp, rồi chọn thẳng từng lựa chọn")
        assertEquals(listOf(KeyCtlAction.SET to 0), of("zz_sel1"), "một lựa chọn ⇒ không có 'kế tiếp' nào")
        assertEquals(listOf(KeyCtlAction.PRESS to 0), of("zz_btn"))
        val groups = KeyCtlTargets.groups(reg)
        assertEquals(1, groups.count { g -> g.targets.any { it.controlId == "zz_vol" } }, "nút mới vào đúng một nhóm")
    }

    /**
     * 2.87 · R-FL1 — SINH TỪ REGISTRY: mọi TOGGLE/COVER có Đảo, mọi SELECT có Kế tiếp, BẤT KỂ `readKey` (owner 03/10
     * *"cái nào đảo đc phải làm đảo hết nhé"*). Không ghim con số cho nút cụ thể — thêm nút vào registry là bài tự phủ.
     */
    @Test
    fun `moi nut dao duoc deu co Dao, moi SELECT deu co Ke tiep`() {
        val flippable = setOf(ControlKind.TOGGLE, ControlKind.COVER)
        ControlRegistry.ALL.forEach { def ->
            val acts = KeyCtlTargets.actionsFor(def).map { it.action }
            assertEquals(def.kind in flippable, KeyCtlAction.FLIP in acts, "${def.id} (${def.kind}): Đảo ⟺ TOGGLE/COVER")
            if (def.kind == ControlKind.SELECT) {
                assertTrue(def.args.size >= 2, "${def.id}: SELECT của registry phải có ≥ 2 lựa chọn (không thì Kế tiếp vô nghĩa)")
            }
            assertEquals(def.kind == ControlKind.SELECT, KeyCtlAction.NEXT in acts, "${def.id} (${def.kind}): Kế tiếp ⟺ SELECT")
        }
        // Có nút KHÔNG readKey trong mỗi họ (đúng ca R-FL1 sinh ra để chữa) — không thì bài trên chỉ thử ca dễ.
        assertTrue(ControlRegistry.ALL.any { it.kind == ControlKind.TOGGLE && it.readKey.isBlank() })
        assertTrue(ControlRegistry.ALL.any { it.kind == ControlKind.COVER && it.readKey.isBlank() })
    }

    /**
     * Owner 03/10 *"phím đóng mở cốp không chung được à, phải 2 nút à?"*. [ĐO mã 03/10] cốp KHÔNG có đường đọc
     * (`trunk.readKey` rỗng — `tailgate_status` gỡ 25/09, rỗng mọi arg trên xe owner) ⇒ Đảo của cốp quyết bằng lệnh
     * cuối Kachi đã gửi (`ControlLastSent`). Mở · Đóng riêng VẪN còn, đứng trước (thứ tự hộp chọn không đổi).
     */
    @Test
    fun `cot co Mo, Dong rieng va mot phim Dao`() {
        assertEquals(
            listOf(KeyCtlTarget("trunk", KeyCtlAction.OPEN), KeyCtlTarget("trunk", KeyCtlAction.CLOSE), KeyCtlTarget("trunk", KeyCtlAction.FLIP)),
            KeyCtlTargets.actionsFor(trunk),
        )
    }

    /**
     * 2.87 · R-FL3 — dữ liệu gán phím ≤ 2.86 đọc NGUYÊN VẸN. Dựng lại ĐÚNG luật sinh đích của 2.86 (Đảo/Kế tiếp chỉ khi
     * có readKey) trên registry hiện tại: mọi mã bản cũ lưu được phải decode ra đúng nó và mã hoá lại ra đúng chuỗi cũ.
     * Thêm vài chuỗi viết tay (đúng dạng `voicekey_bindings` 2.86 lưu) để bài không chỉ tự so với chính phép sinh.
     */
    @Test
    fun `ma dich da luu tu 2_86 tro ve doc len nguyen ven`() {
        fun legacy286(def: ControlDef): List<KeyCtlTarget> {
            val readable = def.readKey.isNotBlank()
            fun t(a: KeyCtlAction, n: Int = 0) = KeyCtlTarget(def.id, a, n)
            return when (def.kind) {
                ControlKind.TOGGLE -> listOfNotNull(t(KeyCtlAction.ON), t(KeyCtlAction.OFF), t(KeyCtlAction.FLIP).takeIf { readable })
                ControlKind.STEP -> listOf(t(KeyCtlAction.UP), t(KeyCtlAction.DOWN))
                ControlKind.COVER -> listOf(t(KeyCtlAction.OPEN), t(KeyCtlAction.CLOSE)) +
                    (2 until def.args.size).map { t(KeyCtlAction.SET, it) } + listOfNotNull(t(KeyCtlAction.FLIP).takeIf { readable })
                ControlKind.SELECT -> listOfNotNull(t(KeyCtlAction.NEXT).takeIf { readable && def.args.size >= 2 }) +
                    def.args.indices.map { t(KeyCtlAction.SET, it) }
                ControlKind.BUTTON -> listOf(t(KeyCtlAction.PRESS))
            }
        }
        val old = ControlRegistry.ALL.flatMap(::legacy286)
        assertTrue(old.size > ControlRegistry.ALL.size)
        old.forEach { t ->
            assertEquals(t, KeyCtlTargets.decode(t.spec), "mã 2.86 '${t.spec}' phải đọc lên nguyên vẹn")
            assertEquals(t.spec, KeyCtlTargets.decode(t.spec)!!.spec, "mã hoá lại phải ra đúng chuỗi cũ")
        }
        assertTrue(all().containsAll(old), "2.87 chỉ THÊM đích (Đảo/Kế tiếp), không bỏ đích nào của 2.86")
        listOf(
            "ctl:trunk:open" to KeyCtlTarget("trunk", KeyCtlAction.OPEN),
            "ctl:trunk:close" to KeyCtlTarget("trunk", KeyCtlAction.CLOSE),
            "ctl:win_lf:on" to KeyCtlTarget("win_lf", KeyCtlAction.ON),
            "ctl:win_lf:off" to KeyCtlTarget("win_lf", KeyCtlAction.OFF),
            "ctl:win_lf:flip" to KeyCtlTarget("win_lf", KeyCtlAction.FLIP),
            "ctl:fan:+1" to KeyCtlTarget("fan", KeyCtlAction.UP),
            "ctl:temp:-1" to KeyCtlTarget("temp", KeyCtlAction.DOWN),
            "ctl:sunshade:=2" to KeyCtlTarget("sunshade", KeyCtlAction.SET, 2),
            "ctl:seatc:next" to KeyCtlTarget("seatc", KeyCtlAction.NEXT),
            "ctl:seatc:=1" to KeyCtlTarget("seatc", KeyCtlAction.SET, 1),
            "ctl:windows_close_all:press" to KeyCtlTarget("windows_close_all", KeyCtlAction.PRESS),
        ).forEach { (spec, want) -> assertEquals(want, KeyCtlTargets.decode(spec), spec) }
    }

    @Test
    fun `vi du owner co mat — kinh trai mo dong, gio cong tru`() {
        val all = all().map { it.spec }.toSet()
        listOf("ctl:win_lf:on", "ctl:win_lf:off", "ctl:win_lf:flip", "ctl:fan:+1", "ctl:fan:-1", "ctl:temp:+1",
            "ctl:trunk:open", "ctl:trunk:close", "ctl:seatc:next", "ctl:seatc:=2", "ctl:windows_close_all:press",
            // 2.87 · R-FL1 — nút không readKey nay có Đảo (owner: "hao phím lắm").
            "ctl:trunk:flip", "ctl:sunshade:flip", "ctl:readl:flip", "ctl:pm25:flip", "ctl:drl:flip")
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
