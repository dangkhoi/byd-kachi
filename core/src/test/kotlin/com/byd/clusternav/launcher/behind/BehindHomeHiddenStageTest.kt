package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.behind.BehindHomeSequence.Result
import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ L4 (FIELD-286-BEHIND) — khoá các lỗi hiện trường của 2.86 bằng bản `am stack list` NGUYÊN VĂN từ máy ảo 03/10 ═══
 *
 * Owner 03/10 (xe thật, 2.86): *"Autostart app background không work, để normal thì work"*. [ĐO máy ảo `e2e/e6-no-app-slot`]
 * bố cục chỉ có widget ⇒ `stagingSlot` = null ⇒ `NO_STAGE`, 0 lệnh. Bài này khoá:
 *  1. D2(a) — bố cục không ô app sống KHÔNG còn ra `NO_STAGE`: chọn màn ảo ẨN ([BehindHomePlan.stageFor]) — ô sống vẫn đứng
 *     trước (CLAUDE.md §6);
 *  2. chuỗi màn ảo ẩn đúng thứ tự đã đo (`p3/e2e-L4/e6c-hidden`, fixture `l4-hidden-*` nguyên văn): tạo → K4 → lớp che → giữ
 *     chỗ → move-task → gỡ che → NHẢ màn ảo chỉ khi đọc thấy trống; 0 lệnh `--display 0`;
 *  3. X thoát lên display 0 trong lúc dàn (trung chuyển VIEW, [ĐO `p3/e2e-L4/m5a`]) ⇒ K12 NGAY, không dựng lớp che;
 *  4. rào nhả: X kẹt màn ảo ẩn ⇒ K7 (rào màn nhà + camera) + dấu + K12 rồi mới nhả; K7 bị rào chặn ⇒ GIỮ màn ảo;
 *  5. D4 — task KHÔNG tiến trình ([ĐO `p3/e2e-L4/m1-stale-task-k4`]) là NGUỘI ⇒ K4 chạy (K4 kéo task cũ vào màn ảo).
 */
class BehindHomeHiddenStageTest {

    private fun text(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-03-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $name")

    private val waze = "com.waze"
    private val wazeComp = "com.waze/com.waze.FreeMapAppActivity"
    private val ytm = "com.google.android.apps.youtube.music"
    private val self = "com.byd.launcher"
    private val anchorComp = "com.byd.launcher/com.byd.clusternav.launcher.behind.BehindAnchorActivity"
    private val homes = listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome", "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity")
    private val k12 = "GO_HOME_FENCE"
    private val list = BehindHomePlan.LIST_CMD

    /** Shell + cổng Android + màn ảo ẩn giả, MỘT nhật ký chung. [reads] cạn ⇒ lặp bản cuối. */
    private inner class Rig(reads: List<String>, private val vd: Int? = 277, private val pid: String = "") {
        val log = ArrayList<String>()
        private val q = ArrayDeque(reads)
        private var last = ""
        val sh: (String) -> String = { cmd ->
            log += cmd
            when {
                cmd == list -> (q.removeFirstOrNull() ?: last).also { last = it }
                cmd.startsWith("pidof ") -> pid
                else -> ""
            }
        }
        private val anchor = object : BehindHomeSequence.AnchorPort {
            override val component = anchorComp
            override fun start(): Boolean { log += "ANCHOR_START"; return true }
            override fun removeAll(): Int { log += "ANCHOR_REMOVE"; return 0 }
            override fun isSystemApp(pkg: String) = false
            override fun markBehind(taskId: Int, pkg: String): Boolean { log += "MARK $taskId $pkg"; return true }
            override fun unmarkBehind(taskId: Int) { log += "UNMARK $taskId" }
        }
        val port = object : BehindHomeSequence.HiddenStagePort {
            override fun create(): Int? { log += "CREATE"; return vd }
            override fun cover(vd: Int): Boolean { log += "COVER $vd"; return true }
            override fun uncover(): Int { log += "UNCOVER"; return 1 }
            override fun release(vd: Int) { log += "RELEASE $vd" }
        }
        val seq = BehindHomeSequence(sh, anchor, self, k12, sleep = {}, homeComps = homes, cameraSig = "com.byd.avc/")
    }

    /** DẪN XUẤT từ `l4-hidden-covered`: bỏ khối stack của lớp che (690) ⇒ "X một mình trên màn ảo ẩn" (lớp che chưa lên). */
    private fun alone(): String = Regex("(?ms)^Stack id=690 .*?(?=^Stack id=|\\z)").replace(text("l4-hidden-covered"), "")

    @Test
    fun `bo cuc chi widget - khong con NO_STAGE, chon man ao an, o song van dung truoc`() {
        assertEquals(BehindHomePlan.Stage.HIDDEN, BehindHomePlan.stageFor(emptyList(), waze), "e6: stages=0 ⇒ màn ảo ẩn")
        assertTrue(BehindHomePlan.Stage.HIDDEN.hidden)
        val live = BehindHomePlan.Stage(0, 272, "vn.vietmap.live", area = 1, alive = true)
        assertEquals(live, BehindHomePlan.stageFor(listOf(live), waze), "ô sống = đường đã đo, LUÔN trước (§6)")
        val dead = live.copy(alive = false)
        assertEquals(BehindHomePlan.Stage.HIDDEN, BehindHomePlan.stageFor(listOf(dead), waze), "ô chưa sống ⇒ không dàn qua nó")
        assertEquals(BehindHomePlan.Stage.HIDDEN, BehindHomePlan.stageFor(listOf(live.copy(pkg = waze)), waze), "ô của chính X ⇒ không")
    }

    @Test
    fun `duong that e6c - tao, K4, che, giu cho, move-task, go che, NHA - MOVED, 0 lenh display 0`() {
        val r = Rig(listOf(text("l4-hidden-before"), alone()) + listOf("covered", "covered", "covered", "anchor", "moved", "after", "after").map { text("l4-hidden-$it") })
        val out = r.seq.startBehindHidden(waze, r.port, wazeComp)
        assertEquals(Result.MOVED, out.result, out.line)
        val k4 = BehindHomePlan.stageCmd(277, wazeComp)
        assertEquals(
            listOf(list, "CREATE", k4, list, "COVER 277", list, "ANCHOR_REMOVE", list, list, "ANCHOR_START", list,
                "MARK 3267 $waze", "am stack move-task 3267 691 true", list, "ANCHOR_REMOVE", "UNCOVER", list, "RELEASE 277", list),
            r.log,
        )
        assertFalse(r.log.any { "--display 0" in it || it == k12 }, "không lệnh nào lên display 0, không K12: ${r.log}")
        assertTrue(out.line.contains("vd=277 nhả"), out.line)
    }

    @Test
    fun `khong tao duoc man ao an - NO_STAGE, 0 lenh doi cua so`() {
        val r = Rig(listOf(text("l4-hidden-before")), vd = null)
        val out = r.seq.startBehindHidden(waze, r.port, wazeComp)
        assertEquals(Result.NO_STAGE, out.result, out.line)
        assertEquals(listOf(list, "CREATE"), r.log)
    }

    /**
     * [ĐO máy ảo 03/10 `p3/e2e-L4/m5a`]: K4-VIEW ⇒ trung chuyển YT Music trên màn ảo, activity chính NEW_TASK lên display 0
     * TRƯỚC màn nhà (fixture `l4-view-escaped` nguyên văn — ghép với `l4-hidden-before` làm bản đọc trước). Chuỗi phải K12 NGAY:
     * không dựng lớp che, không giữ chỗ, không move-task; dấu của task trên display 0 ghi TRƯỚC K12; màn ảo nhả SAU K12.
     */
    @Test
    fun `X thoat len display 0 khi dang dan - K12 ngay, khong lop che, nha sau K12`() {
        val view: (Int) -> String = { vd -> "VIEW→$vd" }
        val r = Rig(listOf(text("l4-hidden-before"), text("l4-view-escaped"), text("l4-view-escaped"), text("l4-view-after-k12")), vd = 284)
        val out = r.seq.startBehindHidden(ytm, r.port, view = view)
        assertEquals(Result.X_FRONT_HOME_RESTORED, out.result, out.line)
        assertFalse(r.log.any { it.startsWith("COVER") || it == "ANCHOR_START" || it.startsWith("am stack move-task") }, r.log.toString())
        val k12At = r.log.indexOf(k12)
        assertTrue(r.log.indexOf("MARK 3311 $ytm") in 0 until k12At, "dấu TRƯỚC K12: ${r.log}")
        assertTrue(r.log.indexOf("RELEASE 284") > k12At, "nhả màn ảo SAU K12 (màn nhà lên trước): ${r.log}")
        assertEquals(1, r.log.count { it == "VIEW→284" }, "đúng một K4-VIEW, nhắm đúng màn ảo ẩn")
    }

    /**
     * Rào nhả (D2): giữ chỗ không dựng được (đọc lại không thấy stack mới — ca `e6-hidden` lượt 1 trước bản vá chờ lớp che
     * resume) ⇒ X còn trên màn ảo ẩn ⇒ K7 qua rào (màn nhà đang hiện + camera) ⇒ đọc thấy X đã rời ⇒ dấu + K12 ⇒ mới nhả.
     * Bản đọc sau K7 = `l4-hidden-after` nguyên văn (Waze ở display 0, ẩn sau màn nhà).
     */
    @Test
    fun `X ket man ao an - K7 qua rao, dau, K12, roi moi nha`() {
        val stuck = alone()
        val reads = listOf(text("l4-hidden-before"), stuck, text("l4-hidden-covered")) +
            List(1 + 1 + BehindHomeSequence.ANCHOR_TRIES) { text("l4-hidden-covered") } + listOf(stuck, text("l4-hidden-after"))
        val r = Rig(reads)
        val out = r.seq.startBehindHidden(waze, r.port, wazeComp)
        assertEquals(Result.X_FRONT_HOME_RESTORED, out.result, out.line)
        val k7 = r.log.indexOfFirst { it.contains("--display 0") && it.contains("com.waze/com.waze.FreeMapAppActivity") }
        assertTrue(k7 > r.log.indexOf("UNCOVER"), "K7 chỉ sau khi gỡ che: ${r.log}")
        assertTrue(r.log[k7].contains("com.byd.avc/") && r.log[k7].contains("KachiHome "), "K7 ĐI QUA rào camera + màn nhà: ${r.log[k7]}")
        val mark = r.log.indexOf("MARK 3267 $waze")
        val k12At = r.log.lastIndexOf(k12)
        assertTrue(mark in (k7 + 1) until k12At, "dấu sau K7, trước K12: ${r.log}")
        assertTrue(r.log.indexOf("RELEASE 277") > k12At, "nhả chỉ khi X đã rời màn ảo: ${r.log}")
        assertFalse(r.log.any { it.startsWith("am stack move-task") }, "giữ chỗ hỏng ⇒ 0 move-task")

        // K7 bị rào chặn (camera / màn nhà không hiện): X vẫn trên màn ảo ⇒ KHÔNG nhả, không K12.
        val blocked = Rig(reads.dropLast(1) + listOf(stuck, stuck, stuck))
        val b = blocked.seq.startBehindHidden(waze, blocked.port, wazeComp)
        assertFalse(blocked.log.any { it.startsWith("RELEASE") }, "còn task app người dùng ⇒ không nhả: ${blocked.log}")
        assertFalse(blocked.log.contains(k12), blocked.log.toString())
        assertTrue(b.line.contains("GIỮ"), b.line)
    }

    /**
     * Review 287 [P1] — rào nhả bị VƯỢT khi đọc hỏng: K4 đã đưa Waze lên màn ảo ẩn 277, rồi kênh đứt / cổng READY-AT-HOME từ chối
     * (mọi `am stack list` sau K4 ném ⇒ bản cũ nuốt thành `[]`) ⇒ bản cũ đọc "màn ảo TRỐNG" ⇒ `RELEASE 277` khi Waze còn trên
     * đó (A10: cờ 256 KẾT THÚC app người dùng vừa xin chạy; ROM BYD [CHƯA BIẾT] — OC-L4-2) mà sổ vẫn ghi `KEPT_UNDER` (OK ⇒ RAN).
     * Nay: đọc hỏng ≠ trống ⇒ GIỮ màn ảo, mã `UNREAD` (chưa rõ — không phải OK).
     */
    @Test
    fun `doc lai hong sau K4 - KHONG nha man ao, ma UNREAD chua ro, khong phai OK`() {
        val r = Rig(listOf(text("l4-hidden-before"), ""))
        val out = r.seq.startBehindHidden(waze, r.port, wazeComp)
        assertTrue(r.log.contains(BehindHomePlan.stageCmd(277, wazeComp)), "K4 đã chạy: ${r.log}")
        assertFalse(r.log.any { it.startsWith("RELEASE") }, "đọc hỏng ⇒ KHÔNG nhả (Waze có thể còn trên màn ảo): ${r.log}")
        assertFalse(r.log.any { it.startsWith("am stack move-task") || it.startsWith("MARK") || it == k12 }, r.log.toString())
        assertEquals(Result.UNREAD, out.result, out.line)
        assertTrue(out.line.contains("vd=277 GIỮ (không đọc được"), out.line)
        val step = com.byd.clusternav.launcher.trip.TripOutcome.ofBehind(out.result)
        assertEquals(com.byd.clusternav.launcher.trip.TripStepCode.Result.UNCONFIRMED, step.result, "sổ không được nói 'đã chạy'")
    }

    /**
     * Review 287 [P1] — nhánh K7: X kẹt màn ảo ẩn ⇒ K7 qua rào; bản đọc SAU K7 hỏng. Bản cũ coi `[]` là "X đã rời" ⇒ dấu + K12 +
     * `rescued` + NHẢ. Nay: không dấu (không có task id thật), KHÔNG K12 (K7 có thể đã bị rào chặn vì app khác ở trước — K12 khi
     * đó kéo người dùng khỏi app họ đang dùng), không nhả, `UNREAD`.
     */
    @Test
    fun `doc lai hong sau K7 - khong dau, khong K12, khong nha`() {
        val stuck = alone()
        val reads = listOf(text("l4-hidden-before"), stuck, text("l4-hidden-covered")) +
            List(1 + 1 + BehindHomeSequence.ANCHOR_TRIES) { text("l4-hidden-covered") } + listOf(stuck, "")
        val r = Rig(reads)
        val out = r.seq.startBehindHidden(waze, r.port, wazeComp)
        val k7 = r.log.indexOfFirst { it.contains("--display 0") && it.contains(wazeComp) }
        assertTrue(k7 > r.log.indexOf("UNCOVER"), "K7 đã chạy sau gỡ che: ${r.log}")
        assertFalse(r.log.drop(k7 + 1).any { it == k12 || it.startsWith("MARK") || it.startsWith("RELEASE") }, "sau K7 + đọc hỏng: ${r.log}")
        assertEquals(Result.UNREAD, out.result, out.line)
    }

    /**
     * Soát vòng 2 [P3] (a) — afterStage: X ĐÃ được thấy tự lên display 0 trước màn nhà (trung chuyển VIEW, `m5a`), rồi bản đọc
     * lại của afterStage hỏng một lượt. Bản cũ: `[]` ⇒ `fellFront` false ⇒ trả `KEPT_UNDER` (OK ⇒ Cài đặt nói "đang chạy ẩn")
     * trong khi YT Music che màn nhà. Nay: `UNREAD`, 0 dấu, 0 K12 (không quyết trên bản đọc không có).
     */
    @Test
    fun `doc lai hong o afterStage sau khi thay X len truoc man nha - UNREAD, khong dau, khong K12`() {
        val view: (Int) -> String = { vd -> "VIEW→$vd" }
        val r = Rig(listOf(text("l4-hidden-before"), text("l4-view-escaped"), "", text("l4-view-after-k12")), vd = 284)
        val out = r.seq.startBehindHidden(ytm, r.port, view = view)
        assertEquals(Result.UNREAD, out.result, out.line)
        assertFalse(r.log.any { it == k12 || it.startsWith("MARK") }, "đọc hỏng ⇒ không dấu, không K12: ${r.log}")
        assertEquals(com.byd.clusternav.launcher.trip.TripStepCode.Result.UNCONFIRMED,
            com.byd.clusternav.launcher.trip.TripOutcome.ofBehind(out.result).result, "sổ không được nói 'đã chạy ẩn'")
    }

    /**
     * Soát vòng 2 [P3] (b) — moveBehind: move-task đã chạy, bản đọc ngay sau hỏng. Bản cũ: `verifyMoved([])` = NOT_MOVED ⇒
     * `UNMARK` dấu bền trong khi Waze có thể đã ở sau màn nhà (CLAUDE.md §5: dấu phải sống lâu hơn thay đổi). Nay: GIỮ dấu,
     * gỡ giữ chỗ, `UNREAD`.
     */
    @Test
    fun `doc lai hong ngay sau move-task - GIU dau ben, go giu cho, UNREAD`() {
        val r = Rig(listOf(text("l4-hidden-before"), alone()) + listOf("covered", "covered", "covered", "anchor").map { text("l4-hidden-$it") } +
            listOf("") + listOf("after", "after").map { text("l4-hidden-$it") })
        val out = r.seq.startBehindHidden(waze, r.port, wazeComp)
        val mv = r.log.indexOf("am stack move-task 3267 691 true")
        assertTrue(mv > r.log.indexOf("MARK 3267 $waze"), "dấu trước lệnh, lệnh đã chạy: ${r.log}")
        assertFalse(r.log.any { it.startsWith("UNMARK") }, "đọc hỏng ⇒ KHÔNG gỡ dấu bền: ${r.log}")
        assertTrue(r.log.withIndex().any { (i, c) -> i > mv && c == "ANCHOR_REMOVE" }, "giữ chỗ vẫn gỡ ở mọi lối ra (R0.7): ${r.log}")
        assertFalse(r.log.contains(k12), r.log.toString())
        assertEquals(Result.UNREAD, out.result, out.line)
    }

    /**
     * Soát vòng 2 [P3] (c) — vacate: K7 đưa X ra display 0, bản đọc màn ảo sau K7 thấy X đã rời, nhưng bản đọc để ghi DẤU
     * hỏng. Bản cũ: `markMain([])` = 0 dấu rồi VẪN K12 ⇒ X sau màn nhà KHÔNG mang dấu (Kachi chết là X nổi lên) + nhả màn ảo.
     * Nay: 0 dấu, 0 K12, GIỮ màn ảo (lượt sau thu hồi), `UNREAD`.
     */
    @Test
    fun `doc hong luc ghi dau sau K7 - khong dau, khong K12, khong nha`() {
        val stuck = alone()
        val reads = listOf(text("l4-hidden-before"), stuck, text("l4-hidden-covered")) +
            List(1 + 1 + BehindHomeSequence.ANCHOR_TRIES) { text("l4-hidden-covered") } + listOf(stuck, text("l4-hidden-after"), "")
        val r = Rig(reads)
        val out = r.seq.startBehindHidden(waze, r.port, wazeComp)
        val k7 = r.log.indexOfFirst { it.contains("--display 0") && it.contains(wazeComp) }
        assertTrue(k7 > r.log.indexOf("UNCOVER"), "K7 đã chạy sau gỡ che: ${r.log}")
        assertFalse(r.log.drop(k7 + 1).any { it == k12 || it.startsWith("MARK") || it.startsWith("RELEASE") }, "sau K7 + đọc dấu hỏng: ${r.log}")
        assertEquals(Result.UNREAD, out.result, out.line)
    }

    /** Review 287 [P1] — bản đọc ĐẦU hỏng: không biết X có đang ở ô / cụm không ⇒ 0 lệnh, không tạo màn ảo (K4 sẽ kéo X khỏi chỗ). */
    @Test
    fun `doc dau hong - 0 lenh, khong tao man ao`() {
        val r = Rig(listOf(""))
        val out = r.seq.startBehindHidden(waze, r.port, wazeComp)
        assertEquals(listOf(list), r.log, "chỉ một lệnh đọc")
        assertEquals(Result.NO_CHANNEL, out.result, out.line)
    }

    /**
     * D4 [ĐO máy ảo 03/10 `p3/e2e-L4/m1-stale-task-k4`, fixture `l4-m1-stale-task` nguyên văn]: Waze còn task 3245 trên
     * display 0 mà tiến trình đã chết (`kill -9`) — bản 2.86 coi "có task" là đang chạy ⇒ `ALREADY_RUNNING`, 0 lệnh, chuyến ghi
     * đã chạy mà app không chạy. Nay: hỏi `pidof` ⇒ rỗng ⇒ NGUỘI ⇒ K4 (đã đo: K4 kéo task cũ vào màn ảo, `reparentToDisplay`).
     * Có pid ⇒ vẫn `ALREADY_RUNNING`, đúng hai lệnh chỉ đọc.
     */
    @Test
    fun `task khong tien trinh la NGUOI - K4 chay, co tien trinh moi la dang chay`() {
        val stage = BehindHomePlan.Stage(0, 272, "vn.vietmap.live", area = 1, alive = true)
        assertTrue(StackParse.parse(text("l4-m1-stale-task")).any { it.pkg == waze && it.displayId == 0 }, "fixture: Waze có task trên display 0")
        val cold = Rig(listOf(text("l4-m1-stale-task")), pid = "")
        val out = cold.seq.startBehind(waze, stage, wazeComp)
        assertTrue(out.result != Result.ALREADY_RUNNING, out.line)
        assertTrue(cold.log.contains("pidof $waze") && cold.log.contains(BehindHomePlan.stageCmd(272, wazeComp)), "pidof rồi K4: ${cold.log}")
        assertFalse(cold.log.any { it.startsWith("am force-stop") }, cold.log.toString())

        val warm = Rig(listOf(text("l4-m1-stale-task")), pid = "4242")
        assertEquals(Result.ALREADY_RUNNING, warm.seq.startBehind(waze, stage, wazeComp).result)
        assertEquals(listOf(list, "pidof $waze"), warm.log, "đang chạy thật ⇒ chỉ hai lệnh đọc, 0 lệnh đổi cửa sổ")

        assertTrue(BehindHomePlan.running(StackParse.parse(text("l4-m1-stale-task")), waze, "4242\n"))
        assertFalse(BehindHomePlan.running(StackParse.parse(text("l4-m1-stale-task")), waze, "  "), "task không pid ⇒ nguội")
        assertFalse(BehindHomePlan.running(StackParse.parse(text("l4-hidden-before")), waze, "4242"), "pid không task (widget) ⇒ nguội")
        assertFalse(BehindHomePlan.running(StackParse.parse(text("l4-m1-stale-task")), waze, "pidof: not found"), "đầu ra lạ ⇒ không coi là có pid")
    }
}
