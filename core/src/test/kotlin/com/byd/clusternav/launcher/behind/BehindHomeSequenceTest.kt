package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.behind.BehindHomeSequence.Result
import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A4 (spec shortcuts-autostart R0.1–R0.5) — CHUỖI thi hành BEHIND-HOME chạy off-device: shell ghi âm trả `am stack list`
 * NGUYÊN BẢN theo đúng thứ tự đã chụp trên máy ảo (T-M1, `BehindHomePlanTm1Test`), [BehindHomeSequence.AnchorPort] giả.
 * Mọi bài khoá THỨ TỰ (một nhật ký chung cho cả lệnh shell lẫn lời gọi Android): đọc → giữ chỗ → đọc → dấu bền →
 * move-task → đọc → gỡ giữ chỗ. Mỗi đường hỏng: 0 `move-task`, giữ chỗ luôn được gỡ.
 */
class BehindHomeSequenceTest {

    private fun text(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-02-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $name")

    private val vm = "vn.vietmap.live"
    private val clock = "com.google.android.deskclock"
    private val anchorComp = "com.byd.launcher/com.byd.clusternav.launcher.behind.BehindAnchorActivity"
    private val home = "GO_HOME_FENCE"

    /** Shell + cổng Android giả, ghi chung MỘT nhật ký. [reads] cạn ⇒ lặp bản cuối (hệ "đứng yên"). */
    private inner class Rig(reads: List<String>, private val markOk: Boolean = true, private val system: Boolean = false) {
        val log = ArrayList<String>()
        private val q = ArrayDeque(reads)
        private var last = ""
        val sh: (String) -> String = { cmd ->
            log += cmd
            if (cmd == BehindHomePlan.LIST_CMD) (q.removeFirstOrNull() ?: last).also { last = it } else ""
        }
        val port = object : BehindHomeSequence.AnchorPort {
            override val component = anchorComp
            override fun start(): Boolean { log += "ANCHOR_START"; return true }
            override fun removeAll(): Int { log += "ANCHOR_REMOVE"; return 0 }
            override fun isSystemApp(pkg: String) = system
            override fun markBehind(taskId: Int, pkg: String): Boolean { log += "MARK $taskId $pkg"; return markOk }
            override fun unmarkBehind(taskId: Int) { log += "UNMARK $taskId" }
        }
        val seq = BehindHomeSequence(sh, port, "com.byd.launcher", home, sleep = {})
    }

    private val list = BehindHomePlan.LIST_CMD

    @Test
    fun `duong that T-M1 - dung thu tu, MOVED, giu cho duoc go`() {
        val r = Rig(listOf("tm1-b-on-top", "tm1-b-on-top", "tm1-anchor", "tm1-moved").map(::text))
        val out = r.seq.evict(83, vm, clock)
        assertEquals(Result.MOVED, out.result, out.line)
        assertEquals(
            listOf("ANCHOR_REMOVE", list, list, "ANCHOR_START", list, "MARK 2258 $vm", "am stack move-task 2258 122 true", list, "ANCHOR_REMOVE"),
            r.log,
        )
    }

    @Test
    fun `A con o dinh man ao (B khong vao duoc o) - 0 giu cho, 0 move-task, bao B_NOT_IN_SLOT`() {
        val r = Rig(listOf(text("tm1-before-swap")))
        val out = r.seq.evict(83, vm, clock)
        assertEquals(Result.B_NOT_IN_SLOT, out.result, out.line)
        assertFalse(r.log.any { it.startsWith("am stack move-task") || it == "ANCHOR_START" }, r.log.toString())
        assertEquals(BehindHomeSequence.B_TOP_TRIES, r.log.count { it == list }, "đọc lại có trần, không lặp vô hạn")
    }

    @Test
    fun `app he thong - 0 lenh doi cua so`() {
        val r = Rig(listOf(text("tm1-b-on-top")), system = true)
        assertEquals(Result.KEPT_UNDER, r.seq.evict(83, vm, clock).result)
        assertFalse(r.log.any { it.startsWith("am stack move-task") || it == "ANCHOR_START" })
    }

    @Test
    fun `S loai rong (khong doc duoc bang chu) - khong move-task, giu cho duoc go`() {
        // Bản đọc ĐÃ LỌC (06/10): B lên đỉnh, giữ chỗ Settings mới ở stack 76 nhưng loại rỗng ⇒ NotStrict.
        val anchorComp10 = "com.android.settings/com.android.settings.Settings"
        val r = Rig(listOf("behind-06-after-3", "behind-06-start", "behind-10-before-move").map(::text))
        val seq = BehindHomeSequence(r.sh, object : BehindHomeSequence.AnchorPort by r.port {
            override val component = anchorComp10
        }, "com.byd.launcher", home, sleep = {})
        val out = seq.evict(44, clock, vm)
        assertEquals(Result.KEPT_UNDER, out.result, out.line)
        assertTrue(out.line.contains("NotStrict"), out.line)
        assertFalse(r.log.any { it.startsWith("am stack move-task") })
        assertEquals("ANCHOR_REMOVE", r.log.last())
    }

    @Test
    fun `giu cho len TRUOC man nha (ROM bo qua khoa) - dua HOME lai qua rao camera, khong move-task`() {
        // DẪN XUẤT từ tm1-anchor: đưa khối stack giữ chỗ 122 lên đầu display 0 và cho nó `visible=true`.
        val a = text("tm1-anchor")
        val block = Regex("(?ms)^Stack id=122 .*?(?=^Stack id=|\\z)").find(a)!!.value
        val front = a.replace(block, "").replace("Stack id=0 ", block.replace("visible=false", "visible=true") + "Stack id=0 ")
        val r = Rig(listOf(text("tm1-b-on-top"), text("tm1-b-on-top"), front))
        val out = r.seq.evict(83, vm, clock)
        assertEquals(Result.ANCHOR_IN_FRONT, out.result, out.line)
        assertTrue(r.log.contains(home), "HOME trước đó ở đỉnh ⇒ K12")
        assertFalse(r.log.any { it.startsWith("am stack move-task") })
        assertTrue(r.log.lastIndexOf("ANCHOR_REMOVE") in (r.log.indexOf("ANCHOR_START") + 1) until r.log.indexOf(home),
            "gỡ giữ chỗ TRƯỚC rồi mới đưa HOME: ${r.log}")
    }

    @Test
    fun `ghi dau ben hong - KHONG move-task (CLAUDE §5 dau truoc lenh)`() {
        val r = Rig(listOf("tm1-b-on-top", "tm1-b-on-top", "tm1-anchor").map(::text), markOk = false)
        assertEquals(Result.KEPT_UNDER, r.seq.evict(83, vm, clock).result)
        assertFalse(r.log.any { it.startsWith("am stack move-task") })
        assertEquals("ANCHOR_REMOVE", r.log.last())
    }

    @Test
    fun `move-task khong an (doc lai A van o man ao) - go dau, KEPT_UNDER`() {
        val r = Rig(listOf("tm1-b-on-top", "tm1-b-on-top", "tm1-anchor", "tm1-b-on-top").map(::text))
        val out = r.seq.evict(83, vm, clock)
        assertEquals(Result.KEPT_UNDER, out.result, out.line)
        assertTrue(r.log.indexOf("UNMARK 2258") > r.log.indexOf("am stack move-task 2258 122 true"))
    }

    @Test
    fun `sau move-task HOME mat dinh - dua HOME lai qua rao camera`() {
        val r = Rig(listOf("tm1-b-on-top", "tm1-b-on-top", "tm1-anchor", "tm1-killed-surfaced").map(::text))
        val out = r.seq.evict(83, vm, clock)
        assertEquals(Result.MOVED_HOME_RESTORED, out.result, out.line)
        assertTrue(r.log.drop(r.log.indexOf("am stack move-task 2258 122 true")).contains(home), r.log.toString())
    }

    @Test
    fun `chay ngam - K4 mo X vao o dan dung, cho X len dinh, K3 dua app o len lai, roi moi day`() {
        val stage = BehindHomePlan.Stage(0, 83, vm, area = 1, alive = true)
        val xComp = "com.google.android.deskclock/com.android.deskclock.DeskClock"
        val r = Rig(listOf("tm1-before-swap", "tm1-b-on-top", "tm1-before-swap").map(::text))
        r.seq.startBehind(clock, stage, xComp)
        val k4 = r.log.indexOf(BehindHomePlan.stageCmd(83, xComp))
        val k3 = r.log.indexOf(BehindHomePlan.bringToFrontCmd(83, "vn.vietmap.live/vn.vietmap.live.MainActivity"))
        assertTrue(k4 > 0 && k3 > k4, "K4 trước K3: ${r.log}")
        assertTrue(r.log.subList(k4, k3).contains(list), "phải đọc lại (chờ X lên đỉnh) giữa K4 và K3")
        assertTrue(r.log.drop(k3).contains("ANCHOR_REMOVE"), "rồi mới vào chuỗi đẩy (dọn giữ chỗ mồ côi trước)")
    }

    /**
     * Review lượt 2 [P2] — X không ở lại màn ảo dàn dựng mà tự lên display 0 TRƯỚC màn nhà (activity trung chuyển mở
     * NEW_TASK — cơ chế [ĐO] T-M3 với ý-định VIEW của YT Music). Trước bản vá: `evict` dừng ở `A_NOT_ON_VD` ⇒ KEPT_UNDER
     * và X che màn nhà tới khi người lái tự bấm Home — trái owner *"không che home"*. Nay: đọc lại, X ở đỉnh đang hiện
     * display 0 + màn nhà ở đỉnh TRƯỚC khi dàn ⇒ K12 (rào camera), 0 `move-task`, giữ chỗ không dựng.
     * Fixture DẪN XUẤT từ `tm1-b-on-top`: khối stack Đồng hồ (121, màn ảo 83) chuyển sang `displayId=0`, đặt đầu danh sách.
     */
    @Test
    fun `chay ngam - X tu len display 0 truoc man nha - dua HOME lai qua rao camera, 0 move-task`() {
        val stage = BehindHomePlan.Stage(0, 83, vm, area = 1, alive = true)
        val xComp = "com.google.android.deskclock/com.android.deskclock.DeskClock"
        val b = text("tm1-b-on-top")
        val clockBlock = Regex("(?ms)^Stack id=121 .*?(?=^Stack id=|\\z)").find(b)!!.value
        val fell = clockBlock.replace("displayId=83 ", "displayId=0 ") + text("tm1-before-swap")
        assertTrue(BehindHomePlan.fellFront(StackParse.parse(fell), clock), "dẫn xuất đúng")
        val r = Rig(listOf(text("tm1-before-swap"), fell))
        val out = r.seq.startBehind(clock, stage, xComp)
        assertEquals(Result.X_FRONT_HOME_RESTORED, out.result, out.line)
        assertFalse(out.moved, "lùi — đếm BEHIND_FAIL")
        assertEquals(home, r.log.last(), "K12 là lệnh CUỐI: ${r.log}")
        // Nhận xét review lượt 3: X nằm sau màn nhà sau K12 ⇒ phải mang dấu (ghi TRƯỚC K12), nếu không Kachi chết là X nổi lên
        // mà lượt trả lại không nhận ra.
        assertEquals(r.log.size - 2, r.log.indexOf("MARK 2259 $clock"), "dấu của X ghi NGAY TRƯỚC K12: ${r.log}")
        assertFalse(r.log.any { it.startsWith("am stack move-task") || it == "ANCHOR_START" }, r.log.toString())

        // Màn nhà KHÔNG ở đỉnh trước khi dàn (DẪN XUẤT: dòng task HOME của `tm1-before-swap` đổi `visible=false`) ⇒ không
        // giành màn hình bằng K12.
        val homeHidden = text("tm1-before-swap").lines().joinToString("\n") { l ->
            if ("launcher.KachiHome bounds" in l) l.replace("visible=true", "visible=false") else l
        }
        val notHome = Rig(listOf(homeHidden, fell))
        assertEquals(Result.KEPT_UNDER, notHome.seq.startBehind(clock, stage, xComp).result)
        assertFalse(notHome.log.contains(home), notHome.log.toString())
    }

    /**
     * Review lượt 3 [P3] — task X trên màn ảo ra sau màn nhà ĐÚNG (`MOVED`, chuỗi T-M1 nguyên văn với X = VietMap, C = Đồng
     * hồ) nhưng X còn MỘT task khác tự lên display 0 trước màn nhà từ lúc dàn (trung chuyển ở lại màn ảo + task chính mở
     * NEW_TASK — [SUY], chưa có app nào trên máy ảo đi đường này). `verifyMoved` so đỉnh với bản đọc NGAY TRƯỚC move-task
     * nên thấy "không đổi"; trước bản vá chuỗi trả `MOVED` và X che màn nhà. Nay: đọc lại ⇒ K12 ⇒ `X_FRONT_HOME_RESTORED`.
     * Fixture DẪN XUẤT: (a) `before` = `tm1-b-on-top` bỏ khối stack VietMap (120) — X chưa có task; (b) bản đọc cuối =
     * `tm1-moved` + khối VietMap của `tm1-before-swap` chuyển sang `displayId=0`, task đổi id 2261, đặt đầu danh sách.
     */
    @Test
    fun `chay ngam - MOVED nhung X con mot task truoc man nha - van dua HOME lai qua rao camera`() {
        val stage = BehindHomePlan.Stage(0, 83, clock, area = 1, alive = true)
        val vmComp = "vn.vietmap.live/vn.vietmap.live.MainActivity"
        val vmBlock = Regex("(?ms)^Stack id=120 .*?(?=^Stack id=|\\z)")
        val before = vmBlock.replace(text("tm1-b-on-top"), "")
        val front = vmBlock.find(text("tm1-before-swap"))!!.value
            .replace("displayId=83 ", "displayId=0 ").replace("taskId=2258:", "taskId=2261:")
        val after = front + text("tm1-moved")
        assertFalse(StackParse.parse(before).any { it.pkg == vm }, "dẫn xuất đúng: X chưa có task")
        assertTrue(BehindHomePlan.fellFront(StackParse.parse(after), vm), "dẫn xuất đúng: X đứng trước màn nhà")
        val reads = listOf(before, text("tm1-before-swap")) +
            listOf("tm1-b-on-top", "tm1-b-on-top", "tm1-anchor", "tm1-moved").map(::text) + after
        val r = Rig(reads)
        val out = r.seq.startBehind(vm, stage, vmComp)
        assertTrue(r.log.contains("am stack move-task 2258 122 true"), "task X trên màn ảo vẫn được đẩy: ${r.log}")
        assertEquals(Result.X_FRONT_HOME_RESTORED, out.result, out.line)
        assertEquals(home, r.log.last(), "K12 là lệnh CUỐI: ${r.log}")
        assertTrue(r.log.indexOf("MARK 2261 $vm") in (r.log.indexOf("am stack move-task 2258 122 true") + 1) until r.log.lastIndexOf(home),
            "task X còn ở trước màn nhà (2261) mang dấu TRƯỚC K12: ${r.log}")

        // Đối chứng: cùng chuỗi, bản đọc cuối là `tm1-moved` nguyên văn (màn nhà ở đỉnh) ⇒ MOVED, 0 K12.
        val clean = Rig(reads.dropLast(1))
        assertEquals(Result.MOVED, clean.seq.startBehind(vm, stage, vmComp).result)
        assertFalse(clean.log.contains(home), clean.log.toString())
    }

    /**
     * [ĐO máy ảo 02/10 `finish/esc`] Đặt tạm Waze (B) vào ô có VietMap (A): B lên đỉnh màn ảo (lượt đọc lại thấy đúng), rồi
     * Waze tự chuyển task sang display 0 (`launchToSide`) GIỮA lần đọc lại và `move-task` ⇒ A thành đỉnh màn ảo lúc lệnh chạy
     * (O2-sai) ⇒ S lên trước (`am_focused_stack [0,0,339,340,moveTaskToStack]`). Chuỗi thấy `FRONT_CHANGED` ⇒ K12. Sau K12 cả
     * A lẫn B nằm SAU màn nhà; B không mang dấu thì Kachi chết là B có thể nổi lên mà lượt trả lại bỏ qua ⇒ dấu cho MỌI task
     * của A/B trên display 0, TRƯỚC K12. Fixture: `esc-b-on-top` (s036) + `esc-after-move` (đọc sau lệnh) NGUYÊN VĂN; bản đọc
     * có giữ chỗ DẪN XUẤT = `esc-b-on-top` + khối giữ chỗ của `tm1-anchor` đổi id stack 122→339, task 2260→2669.
     */
    @Test
    fun `B thoat khoi o giua doc lai va move-task - K12, ca A lan B mang dau truoc K12`() {
        val waze = "com.waze"
        val anchorBlock = Regex("(?ms)^Stack id=122 .*?(?=^Stack id=|\\z)").find(text("tm1-anchor"))!!.value
            .replace("Stack id=122 ", "Stack id=339 ").replace("taskId=2260:", "taskId=2669:")
        val withAnchor = text("esc-b-on-top").trimEnd() + "\n\n" + anchorBlock
        assertTrue(BehindHomePlan.pickAnchor(StackParse.parse(text("esc-b-on-top")), StackParse.parse(withAnchor), anchorComp)
            == BehindHomePlan.Anchor.Ok(339), "dẫn xuất đúng: giữ chỗ hợp lệ")
        val r = Rig(listOf(text("esc-b-on-top"), text("esc-b-on-top"), withAnchor, text("esc-after-move")))
        val out = r.seq.evict(185, vm, waze)
        assertEquals(Result.MOVED_HOME_RESTORED, out.result, out.line)
        val move = r.log.indexOf("am stack move-task 2667 339 true")
        assertTrue(r.log.indexOf("MARK 2667 $vm") in 0 until move, "dấu A trước move-task: ${r.log}")
        val k12 = r.log.lastIndexOf(home)
        assertTrue(r.log.indexOf("MARK 2668 $waze") in (move + 1) until k12, "B (2668) mang dấu TRƯỚC K12: ${r.log}")
    }

    /**
     * Nhóm B (R1.5 dòng 12 · R2.4) — X ĐÃ có TASK (đang toàn màn / sau màn nhà / trong ô / đang chiếu) ⇒ dàn lại là kéo nó
     * khỏi chỗ người dùng đang dùng. Một lần đọc `am stack list` rồi dừng: 0 `am start`, 0 giữ chỗ.
     */
    @Test
    fun `chay ngam app da co task - ALREADY_RUNNING, chi mot lan doc, 0 lenh doi cua so`() {
        val stage = BehindHomePlan.Stage(0, 83, vm, area = 1, alive = true)
        val r = Rig(listOf(text("tm1-b-on-top")))      // Đồng hồ (task 2259) đang ở đỉnh màn ảo 83
        val out = r.seq.startBehind(clock, stage, "a/b")
        assertEquals(Result.ALREADY_RUNNING, out.result, out.line)
        assertFalse(out.moved)
        assertEquals(listOf(list), r.log, "chỉ đọc am stack list rồi dừng")
    }

    /**
     * Khoá review lượt 2 [P2] [ĐO máy ảo 02/10 `e2e/r2-alias-trip`]: widget YT Music ở ô 2 làm hệ bật TIẾN TRÌNH YT Music
     * bằng broadcast (không task) 3,6 s trước bước nhạc; bản đo bằng `pidof` coi đó là "đang chạy" ⇒ nhạc lên xe KHÔNG BAO
     * GIỜ phát. Tiến trình có mà không task ⇒ vẫn dàn (K4) như app nguội. `pidof` trả pid ở đây để bản cũ đỏ.
     */
    @Test
    fun `chay ngam - tien trinh co ma KHONG co task (widget) van dan nhu app nguoi`() {
        val stage = BehindHomePlan.Stage(0, 83, vm, area = 1, alive = true)
        val xComp = "com.google.android.deskclock/com.android.deskclock.DeskClock"
        val r = Rig(listOf("tm1-before-swap", "tm1-b-on-top", "tm1-before-swap").map(::text))
        val sh: (String) -> String = { cmd -> if (cmd == "pidof $clock") "4242" else r.sh(cmd) }
        val out = BehindHomeSequence(sh, r.port, "com.byd.launcher", home, sleep = {}).startBehind(clock, stage, xComp)
        assertTrue(out.result != Result.ALREADY_RUNNING, out.line)
        assertTrue(r.log.contains(BehindHomePlan.stageCmd(83, xComp)), "K4 phải chạy: ${r.log}")
    }

    @Test
    fun `chay ngam ten goi la - 0 lenh nao (ten goi di vao lenh phan giai va K4)`() {
        val stage = BehindHomePlan.Stage(0, 83, vm, area = 1, alive = true)
        val r = Rig(listOf(text("tm1-before-swap")))
        assertEquals(Result.X_NOT_STAGED, r.seq.startBehind("x;reboot", stage, "a/b").result)
        assertTrue(r.log.isEmpty(), r.log.toString())
    }

    @Test
    fun `chay ngam app he thong hoac khong phan giai duoc - 0 lenh doi cua so`() {
        val stage = BehindHomePlan.Stage(0, 83, vm, area = 1, alive = true)
        val sys = Rig(listOf(text("tm1-before-swap")), system = true)
        assertEquals(Result.KEPT_UNDER, sys.seq.startBehind(clock, stage, "a/b").result)
        assertTrue(sys.log.isEmpty(), sys.log.toString())
        val bad = Rig(listOf(text("tm1-before-swap")))
        assertEquals(Result.X_NOT_STAGED, bad.seq.startBehind(clock, stage, "x' ; reboot ; '/y").result)
        assertFalse(bad.log.any { it.startsWith("am start") })
    }
}
