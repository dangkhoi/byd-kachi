package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá BỘ CHỌN cửa sổ nổi cần đóng (PROFILE-SWITCH-SLOTS R-B3 · §4.3) trên dump `am stack list` NGUYÊN VĂN.
 *
 * Fixture (`core/src/test/resources/diagnostics/`):
 *  - `am-stack-list-emulator-2026-10-01-*.txt` — máy ảo Android 10, KHÔNG phải xe (spec §2.7). Bộ `noshell-*` là đúng
 *    lỗi B1 đo được: sau A→B→A YouTube (stack 3, ô 1 của hồ sơ B) còn nổi trên nhà cạnh VietMap (stack 4, ô 0 của A).
 *    `shellup-A` là lỗi B2: kênh lên rồi mà YouTube (stack 3, `visible=false`) vẫn ở đó. `t0a-*` là lượt đo tầng 1
 *    (`docs/diagnostics/profile-switch-slots-emulator-2026-10-01.md`) — VietMap trong màn ảo 15 + YouTube nổi.
 *  - `am-stack-list-oncar-2026-09-29-after-fix-orphan-top.txt` — XE thật 29/09 (E8): YouTube nổi stack 32 trên nhà.
 * Ca dẫn xuất (sửa chữ trong test) đều ghi rõ là dẫn xuất và vì sao.
 */
class FloatingOrphanPlanTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    private fun emu(name: String) = StackParse.parse(fixture("am-stack-list-emulator-2026-10-01-$name"))

    private val self = "com.byd.launcher"
    private val yt = "com.google.android.youtube"
    private val vm = "vn.vietmap.live"

    // ── Ca đo được ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `noshell-A-back - chi go stack YouTube, khong dung home 0 hay VietMap o con giu`() {
        val e = emu("noshell-A-back")
        assertEquals(listOf(3), FloatingOrphanPlan.plan(e, opened = listOf(vm, yt), held = setOf(vm), selfPkg = self))
    }

    @Test
    fun `noshell-A-back - khong o nao giu thi go ca hai, dung thu tu doc, khong bao gio stack home`() {
        val e = emu("noshell-A-back")
        val ids = FloatingOrphanPlan.plan(e, opened = listOf(vm, yt), held = emptySet(), selfPkg = self)
        assertEquals(listOf(4, 3), ids)
        assertFalse(0 in ids, "stack 0 là home (Kachi) — vùng cấm §4")
    }

    @Test
    fun `dau rong thi khong go gi - allow-list, khong phai moi thu tru`() {
        assertEquals(emptyList<Int>(), FloatingOrphanPlan.plan(emu("noshell-A-back"), emptyList(), emptySet(), self))
    }

    @Test
    fun `shellup-A - YouTube an van go, Kachi standard toan man va home khong bao gio`() {
        val e = emu("shellup-A")
        val ids = FloatingOrphanPlan.plan(e, opened = listOf(yt, self), held = setOf(vm), selfPkg = self)
        assertEquals(listOf(3), ids)
        assertFalse(5 in ids || 0 in ids)
    }

    @Test
    fun `embedded-initial - VietMap o man ao display 2 thi khong phai cua so noi man chinh`() {
        assertEquals(emptyList<Int>(), FloatingOrphanPlan.plan(emu("embedded-initial"), listOf(vm), emptySet(), self))
    }

    @Test
    fun `t0a - chi go YouTube noi, khong dung VietMap trong man ao 15 hay home`() {
        val e = emu("t0a-opened")
        assertEquals(listOf(26), FloatingOrphanPlan.plan(e, listOf(vm, yt), emptySet(), self))
        assertEquals(listOf(26), FloatingOrphanPlan.plan(e, listOf(yt), setOf(vm), self))
    }

    @Test
    fun `xe 29-09 - YouTube mo coi stack 32 thi go, con o giu thi khong, Google Maps tren cum display 1 khong bao gio`() {
        val e = StackParse.parse(fixture("am-stack-list-oncar-2026-09-29-after-fix-orphan-top"))
        assertEquals(listOf(32), FloatingOrphanPlan.plan(e, listOf(yt), emptySet(), self))
        assertEquals(emptyList<Int>(), FloatingOrphanPlan.plan(e, listOf(yt), setOf(yt), self))
        // stack 33/34 là freeform standard nhưng ở display 1 (cụm) — câu 1 của §4.
        assertEquals(emptyList<Int>(), FloatingOrphanPlan.plan(e, listOf("com.google.android.apps.maps", self), emptySet(), self))
    }

    // ── Ca dẫn xuất: các lằn ranh mà dump đo được chưa chạm tới ───────────────────────────────────

    /**
     * DẪN XUẤT: xoá chữ `mActivityType=…` ⇒ chế độ vẫn `freeform` nhưng loại TRỐNG. [StackParse.floatingOnMain] coi
     * loại trống là standard (đúng cho đường cast chỉ đọc); với LỆNH GỠ thì trống = không biết = không làm (A12 không có
     * rào loại ở framework, spec §2.6). Khoá cả hai tầng: bộ lọc chặt VÀ guard [FloatingOrphanPlan.admissible].
     */
    @Test
    fun `loai stack TRONG thi khong go - ca bo loc chat lan guard`() {
        val text = fixture("am-stack-list-emulator-2026-10-01-noshell-A-back").replace(Regex("mActivityType=[A-Za-z-]+"), "")
        val e = StackParse.parse(text)
        assertTrue(e.any { it.stackId == 3 && it.isFreeform && it.activityType.isEmpty() }, "dẫn xuất phải còn freeform")
        assertEquals(2, StackParse.floatingOnMain(e).size, "hàm cũ nhận loại trống — đúng là chỗ phải chặt hơn")
        assertEquals(emptyList<Int>(), FloatingOrphanPlan.strictFloatingOnMain(e).map { it.stackId })
        assertFalse(FloatingOrphanPlan.admissible(3, e, listOf(yt), emptySet(), self), "guard phải tự từ chối loại trống")
        assertEquals(emptyList<Int>(), FloatingOrphanPlan.plan(e, listOf(vm, yt), emptySet(), self))
    }

    @Test
    fun `dump thieu dong configuration thi khong go gi`() {
        val text = fixture("am-stack-list-emulator-2026-10-01-noshell-A-back")
            .lines().filterNot { "configuration=" in it }.joinToString("\n")
        assertEquals(emptyList<Int>(), FloatingOrphanPlan.plan(StackParse.parse(text), listOf(vm, yt), emptySet(), self))
    }

    /**
     * DẪN XUẤT A12 (DL5 — chưa có dump thật, OQ-5): đầu dòng `RootTask id=` (định dạng `ActivityTaskManager.java`
     * r34 :542-567). Root task HOME mang gói nằm trong dấu ⇒ không gỡ (A12 `removeTask` không tự chặn loại).
     */
    @Test
    fun `A12 RootTask - home mang goi trong dau khong bao gio go, khach noi thi go`() {
        val a12 = """
            RootTask id=12 bounds=[0,0][1920,1080] displayId=0 userId=0
             configuration={1.0 winConfig={ mWindowingMode=freeform mDisplayWindowingMode=fullscreen mActivityType=standard} s.3}
              taskId=12: com.google.android.youtube/.Home bounds=[198,620][1883,1043] userId=0 visible=true
            RootTask id=1 bounds=[0,0][1920,1080] displayId=0 userId=0
             configuration={1.0 winConfig={ mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=home} s.1}
              taskId=7: com.dudu.launcher/.Home bounds=[0,0][1920,1080] userId=0 visible=true
        """.trimIndent()
        val e = StackParse.parse(a12)
        assertEquals(listOf(12), FloatingOrphanPlan.plan(e, listOf(yt, "com.dudu.launcher"), emptySet(), self))
        assertFalse(FloatingOrphanPlan.admissible(1, e, listOf("com.dudu.launcher"), emptySet(), self))
    }

    /** DẪN XUẤT: một stack chứa task của gói NGOÀI dấu ⇒ bỏ cả stack (allow-list theo từng task, không theo đầu stack). */
    @Test
    fun `stack tron goi ngoai dau thi bo ca stack`() {
        val text = fixture("am-stack-list-emulator-2026-10-01-noshell-B").replace(
            "Stack id=0 ",
            "  taskId=1999: com.other.app/.Main bounds=[0,0][10,10] userId=0 visible=true\n\nStack id=0 ",
        )
        val e = StackParse.parse(text)
        assertEquals(setOf(yt, "com.other.app"), e.filter { it.stackId == 3 }.map { it.pkg }.toSet(), "dẫn xuất phải trộn")
        assertEquals(emptyList<Int>(), FloatingOrphanPlan.plan(e, listOf(yt), emptySet(), self))
        assertEquals(listOf(3), FloatingOrphanPlan.plan(e, listOf(yt, "com.other.app"), emptySet(), self))
    }

    /** DẪN XUẤT: gói có task ở display ≥ 1 (màn ảo ô / cụm) ⇒ không đóng cửa sổ nổi của nó trên display 0. */
    @Test
    fun `goi con task o display khac 0 thi khong go`() {
        val vdBlock = fixture("am-stack-list-emulator-2026-10-01-embedded-initial")
            .lines().dropWhile { !it.startsWith("Stack id=6 ") }.joinToString("\n")
        check(vdBlock.contains("displayId=2") && vdBlock.contains(vm)) { "fixture đổi?" }
        val e = StackParse.parse(fixture("am-stack-list-emulator-2026-10-01-noshell-A-back") + "\n" + vdBlock)
        assertEquals(listOf(3), FloatingOrphanPlan.plan(e, listOf(vm, yt), emptySet(), self))
    }

    @Test
    fun `selfPkg trong hoac stack khong co trong ban doc thi guard tu choi`() {
        val e = emu("noshell-A-back")
        assertFalse(FloatingOrphanPlan.admissible(3, e, listOf(yt), emptySet(), ""))
        assertFalse(FloatingOrphanPlan.admissible(99, e, listOf(yt), emptySet(), self))
        assertTrue(FloatingOrphanPlan.admissible(3, e, listOf(yt), emptySet(), self))
    }

    // ── Xoá dấu ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `xoa dau - chi goi het noi VA khong con o nao giu`() {
        val after = emu("t0a-after-remove")
        assertEquals(setOf(yt), FloatingOrphanPlan.forgettable(after, listOf(yt), emptySet()))
        assertEquals(emptySet<String>(), FloatingOrphanPlan.forgettable(after, listOf(yt), setOf(yt)), "còn giữ ⇒ giữ dấu")
        val stillThere = emu("noshell-A-back")
        assertEquals(emptySet<String>(), FloatingOrphanPlan.forgettable(stillThere, listOf(yt, vm), emptySet()))
        assertEquals(setOf(yt, vm), FloatingOrphanPlan.stillFloating(stillThere, listOf(yt, vm, "com.x.y")))
    }

    // ── Golden ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chuoi lenh tung byte`() {
        assertEquals("am stack list", FloatingOrphanPlan.LIST_CMD)
        assertEquals("am stack remove 3", FloatingOrphanPlan.removeCmd(3))
        assertEquals("am stack remove 26", FloatingOrphanPlan.removeCmd(26))
    }
}
