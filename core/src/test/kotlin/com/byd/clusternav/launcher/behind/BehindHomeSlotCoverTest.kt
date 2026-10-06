package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.behind.BehindHomeSequence.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ L8 — nút *chạy nền* đầu ô cho MỌI ô app: lớp che của Kachi TRONG màn ảo của ô ([BehindHomeSequence.evictCovered]) ═══
 *
 * Owner 03/10: *"đẩy app ra chạy nền … để UI trong suốt thấy nền background"* — nút phải làm được ở MỌI ô app (D-L6-1 cũ:
 * chỉ khi ô có app LƯU khác, vì A ở ĐỈNH màn ảo lúc `move-task` ⇒ stack đích lên che màn nhà, A10 r47
 * `TaskRecord.java:736-737` `wasFront`). Bài này khoá đường đã ĐO trên máy ảo (A10 `clusternav10`, `e2e-L8 · e2b-ytm-saved-playing` (bằng chứng phiên, ngoài repo):
 * YT Music ĐANG PHÁT ở ô 0, màn ảo 295; HOME đỉnh display 0 ở 235/235 mẫu, 30 mẫu trong cửa sổ chuỗi 4 s; YT Music pid giữ,
 * `state=3`) bằng 5 bản `am stack list` NGUYÊN VĂN (`l8-slotback-*`):
 *  1. thứ tự: đọc → lớp che lên màn ảo Ô (CHỜ nó đứng đỉnh) → giữ chỗ → dấu → `move-task` → gỡ giữ chỗ → GỠ CHE → đọc lại;
 *  2. phạm vi (CLAUDE.md §4): lệnh shell đổi trạng thái DUY NHẤT là `move-task` của đúng task A trên đúng màn ảo ô; 0 lệnh
 *     `--display 0`, 0 K12, 0 tạo/nhả màn ảo (màn ảo của ô là của host ô);
 *  3. rào: màn nhà không ở đỉnh / app hệ thống / A không ở màn ảo / màn ảo < 1 ⇒ 0 lệnh; lớp che không lên ⇒ 0 move-task
 *     nhưng VẪN gỡ che; bản đọc cuối còn thấy A trên màn ảo ⇒ ô GIỮ app (bên gọi không nhả màn ảo — cờ 256 kết thúc nó).
 */
class BehindHomeSlotCoverTest {

    private fun text(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-03-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $name")

    private fun l8(s: String) = text("l8-slotback-$s")

    private val ytm = "com.google.android.apps.youtube.music"
    private val self = "com.byd.launcher"
    private val anchorComp = "com.byd.launcher/com.byd.clusternav.launcher.behind.BehindAnchorActivity"
    private val homes = listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome", "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity")
    private val k12 = "GO_HOME_FENCE"
    private val list = BehindHomePlan.LIST_CMD
    private val vd = 295

    /** Shell + cổng Android + lớp che giả, MỘT nhật ký chung. [reads] cạn ⇒ lặp bản cuối. */
    private inner class Rig(reads: List<String>, private val system: Boolean = false, private val covers: Boolean = true) {
        val log = ArrayList<String>()
        private val q = ArrayDeque(reads)
        private var last = ""
        val sh: (String) -> String = { cmd ->
            log += cmd
            if (cmd == list) (q.removeFirstOrNull() ?: last).also { last = it } else ""
        }
        private val anchor = object : BehindHomeSequence.AnchorPort {
            override val component = anchorComp
            override fun start(): Boolean { log += "ANCHOR_START"; return true }
            override fun removeAll(): Int { log += "ANCHOR_REMOVE"; return 0 }
            override fun isSystemApp(pkg: String) = system
            override fun markBehind(taskId: Int, pkg: String): Boolean { log += "MARK $taskId $pkg"; return true }
            override fun unmarkBehind(taskId: Int) { log += "UNMARK $taskId" }
        }
        val cover = object : BehindHomeSequence.CoverPort {
            override fun cover(vd: Int): Boolean { log += "COVER $vd"; return covers }
            override fun uncover(): Int { log += "UNCOVER"; return 1 }
        }
        val seq = BehindHomeSequence(sh, anchor, self, k12, sleep = {}, homeComps = homes, cameraSig = "com.byd.avc/")
    }

    @Test
    fun `duong that e2b - che, giu cho, move-task, go che - MOVED, chi mot lenh doi cua so`() {
        val r = Rig(listOf("before", "covered", "covered", "covered", "anchor", "moved", "after", "after").map(::l8))
        val out = r.seq.evictCovered(vd, ytm, r.cover)
        assertEquals(Result.MOVED, out.result, out.line)
        assertTrue(out.outOfStage)
        assertEquals(
            listOf(list, "COVER $vd", list, "ANCHOR_REMOVE", list, list, "ANCHOR_START", list,
                "MARK 3380 $ytm", "am stack move-task 3380 745 true", list, "ANCHOR_REMOVE", "UNCOVER", list, list),
            r.log,
        )
        val shellWrites = r.log.filter { it != list && it.contains(' ') && !it.startsWith("MARK") && !it.startsWith("COVER") }
        assertEquals(listOf("am stack move-task 3380 745 true"), shellWrites, "lệnh shell đổi trạng thái duy nhất = move-task của task A (§4)")
        assertFalse(r.log.any { "--display" in it || it == k12 || it.startsWith("CREATE") || it.startsWith("RELEASE") }, r.log.toString())
    }

    @Test
    fun `lop che phai len dinh man ao o TRUOC giu cho, go che SAU move-task`() {
        val r = Rig(listOf("before", "covered", "covered", "covered", "anchor", "moved", "after", "after").map(::l8))
        r.seq.evictCovered(vd, ytm, r.cover)
        val cover = r.log.indexOf("COVER $vd")
        val anchor = r.log.indexOf("ANCHOR_START")
        val move = r.log.indexOfFirst { it.startsWith("am stack move-task") }
        val uncover = r.log.indexOf("UNCOVER")
        assertTrue(cover in 0 until anchor && anchor < move && move < uncover, r.log.toString())
        assertEquals(list, r.log[cover + 1], "đọc lại thấy lớp che ĐỨNG ĐỈNH màn ảo ô rồi mới dựng giữ chỗ (R0.2)")
    }

    /**
     * DẪN XUẤT từ `l8-slotback-covered`: lớp che (stack 744) không có trong bản đọc ⇒ "che chưa lên đỉnh" mãi ⇒ hết trần
     * chờ ⇒ 0 giữ chỗ, 0 move-task; gỡ che vẫn chạy (lớp che có thể lên muộn — không để nó đứng trên app của ô).
     */
    @Test
    fun `lop che khong len dinh - 0 move-task, van go che, o giu app`() {
        val noCover = Regex("(?ms)^Stack id=744 .*?(?=^Stack id=|\\z)").replace(l8("covered"), "")
        val r = Rig(listOf(l8("before"), noCover))
        val out = r.seq.evictCovered(vd, ytm, r.cover)
        assertEquals(Result.KEPT_UNDER, out.result, out.line)
        assertFalse(out.outOfStage)
        assertFalse(r.log.any { it == "ANCHOR_START" || it.startsWith("am stack move-task") }, r.log.toString())
        assertTrue(r.log.indexOf("UNCOVER") > r.log.indexOf("COVER $vd"), r.log.toString())
        assertEquals(1 + (BehindHomeSequence.X_TOP_WAIT_MS / BehindHomeSequence.X_TOP_STEP_MS).toInt() + 1, r.log.count { it == list },
            "đọc trước · chờ che lên đỉnh tới trần · đọc lại sau (rào X lên trước màn nhà)")

        val refused = Rig(listOf(l8("before")), covers = false)
        assertEquals(Result.KEPT_UNDER, refused.seq.evictCovered(vd, ytm, refused.cover).result)
        assertEquals(listOf(list, "COVER $vd", "UNCOVER", list), refused.log)
    }

    /**
     * DẪN XUẤT từ `l8-slotback-before`: HOME `visible=false`, VietMap (stack 739, display 0) `visible=true` ⇒ một app toàn màn
     * đứng trước màn nhà (camera lùi / app khác) ⇒ 0 lệnh — kể cả lớp che (người dùng vừa chạm nút TRÊN màn nhà; màn nhà
     * mất đỉnh giữa chừng thì không đẩy gì ra "sau" nó).
     */
    @Test
    fun `man nha khong o dinh display 0 - 0 lenh`() {
        val notHome = l8("before")
            .replace("KachiHome bounds=[0,0][1920,1080] userId=0 visible=true", "KachiHome bounds=[0,0][1920,1080] userId=0 visible=false")
            .replace("vn.vietmap.live.MainActivity bounds=[0,0][1920,1080] userId=0 visible=false", "vn.vietmap.live.MainActivity bounds=[0,0][1920,1080] userId=0 visible=true")
        assertTrue(notHome != l8("before"))
        val r = Rig(listOf(notHome))
        val out = r.seq.evictCovered(vd, ytm, r.cover)
        assertEquals(Result.KEPT_UNDER, out.result, out.line)
        assertEquals(listOf(list), r.log, "chỉ một lượt ĐỌC")
    }

    @Test
    fun `tu choi truoc moi lenh - man ao sai, app he thong, chinh Kachi, A khong o man ao o`() {
        Rig(listOf(l8("before"))).let { r ->
            assertEquals(Result.X_NOT_STAGED, r.seq.evictCovered(0, ytm, r.cover).result); assertEquals(emptyList<String>(), r.log)
        }
        Rig(listOf(l8("before")), system = true).let { r ->
            assertEquals(Result.SYSTEM_APP, r.seq.evictCovered(vd, ytm, r.cover).result); assertEquals(emptyList<String>(), r.log)
        }
        Rig(listOf(l8("before"))).let { r ->
            assertEquals(Result.KEPT_UNDER, r.seq.evictCovered(vd, self, r.cover).result); assertEquals(emptyList<String>(), r.log)
        }
        Rig(listOf(l8("before"))).let { r ->
            assertEquals(Result.X_NOT_STAGED, r.seq.evictCovered(vd, "vn.vietmap.live", r.cover).result, "VietMap ở display 0, không ở ô")
            assertEquals(listOf(list), r.log)
        }
        Rig(listOf(l8("before"))).let { r ->
            assertEquals(Result.X_NOT_STAGED, r.seq.evictCovered(vd + 1, ytm, r.cover).result, "màn ảo của ô KHÁC")
            assertEquals(listOf(list), r.log)
        }
    }

    /**
     * DẪN XUẤT: bản đọc CUỐI = `l8-slotback-after` + khối stack 743 của `l8-slotback-before` (YT Music còn trên màn ảo 295)
     * ⇒ dù move-task báo OK, ô GIỮ app (bên gọi KHÔNG nhả màn ảo — cờ 256 sẽ kết thúc activity còn trên đó). Bản đọc cuối
     * rỗng (kênh đứt) ⇒ cũng giữ: không biết thì không nhả.
     */
    @Test
    fun `ban doc cuoi con A tren man ao o hoac khong doc duoc - o giu app`() {
        val stillThere = l8("after") + "\n" + Regex("(?ms)^Stack id=743 .*?(?=^Stack id=|\\z)").find(l8("before"))!!.value
        val r = Rig(listOf("before", "covered", "covered", "covered", "anchor", "moved", "after").map(::l8) + stillThere)
        val out = r.seq.evictCovered(vd, ytm, r.cover)
        assertEquals(Result.KEPT_UNDER, out.result, out.line)
        assertTrue(out.line.contains("ô giữ app"), out.line)

        val blank = Rig(listOf("before", "covered", "covered", "covered", "anchor", "moved", "after").map(::l8) + "")
        assertEquals(Result.KEPT_UNDER, blank.seq.evictCovered(vd, ytm, blank.cover).result)
    }

    /**
     * Lỗi xe 2.87 (owner 04/10: *"bấm vào nó đen cái khung, xong rồi lại lòi lên lại"*; chưa có log xe) — khoá: MỌI đường dừng
     * của chuỗi *chạy nền* ra một lý do NGẮN ([BehindReason.short]) chỉ đúng chỗ dừng, rút từ CHÍNH dòng chuỗi vừa in (không
     * dựng song song). Câu báo trên màn mang lý do này ⇒ ảnh chụp anh em gửi về đủ để biết bước nào hỏng (CLAUDE.md §11).
     * Mỗi ca chạy CHUỖI THẬT trên fixture nguyên văn / dẫn xuất ở các bài trên — dạng dòng đổi là bài này đỏ.
     */
    @Test
    fun `ly do ngan cho anh chup - moi duong dung cua chuoi chay nen noi dung cho dung`() {
        val noCover = Regex("(?ms)^Stack id=744 .*?(?=^Stack id=|\\z)").replace(l8("covered"), "")
        val notHome = l8("before")
            .replace("KachiHome bounds=[0,0][1920,1080] userId=0 visible=true", "KachiHome bounds=[0,0][1920,1080] userId=0 visible=false")
            .replace("vn.vietmap.live.MainActivity bounds=[0,0][1920,1080] userId=0 visible=false", "vn.vietmap.live.MainActivity bounds=[0,0][1920,1080] userId=0 visible=true")
        val stillThere = l8("after") + "\n" + Regex("(?ms)^Stack id=743 .*?(?=^Stack id=|\\z)").find(l8("before"))!!.value
        val path = listOf("before", "covered", "covered", "covered", "anchor", "moved", "after").map(::l8)
        fun why(reads: List<String>, a: String = ytm, v: Int = vd, system: Boolean = false, covers: Boolean = true): String {
            val r = Rig(reads, system = system, covers = covers)
            val out = r.seq.evictCovered(v, a, r.cover)
            val short = BehindReason.short(out)
            assertTrue(short.length <= BehindReason.MAX, short)
            assertEquals(BehindReason.Report(out.outOfStage, short, out.line), BehindReason.report(out))
            return short
        }
        assertEquals("KEPT_UNDER · lớp che không lên đỉnh màn ảo ô", why(listOf(l8("before"), noCover)))
        assertEquals("KEPT_UNDER · lớp che không lên", why(listOf(l8("before")), covers = false))
        assertEquals("KEPT_UNDER · màn nhà không ở đỉnh display 0", why(listOf(notHome)))
        assertEquals("KEPT_UNDER · đọc lại: A còn task trên màn ảo ô ⇒ ô giữ app", why(path + stillThere))
        assertEquals("KEPT_UNDER · đọc lại: không đọc được ⇒ ô giữ app", why(path + ""))
        assertEquals("SYSTEM_APP · từ chối (app hệ thống, R0.6)", why(listOf(l8("before")), system = true))
        assertEquals("KEPT_UNDER · từ chối (chính mình)", why(listOf(l8("before")), a = self))
        assertEquals("X_NOT_STAGED · A không ở màn ảo ô", why(listOf(l8("before")), a = "vn.vietmap.live"))
        assertEquals("X_NOT_STAGED · màn ảo không hợp lệ", why(listOf(l8("before")), v = 0))
        assertEquals("MOVED · kiểm=OK", why(path + l8("after")), "đường thành công cũng đọc được (câu báo chỉ hiện khi hỏng)")
    }
}
