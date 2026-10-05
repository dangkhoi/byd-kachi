package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.behind.BehindHomeSequence.Result
import com.byd.clusternav.launcher.trip.TripOutcome
import com.byd.clusternav.launcher.trip.TripStepCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ A2 · 2.89 — ô 7 cho app nhạc NGOÀI ô ([HiddenPark]) — khoá bằng `am stack list` nguyên văn máy ảo 03/10 ═══════════════
 *
 * Khoá cái lỗi xe 05/10 phải tránh ([ĐO] `oncar-2026-10-05-slot-cluster.md` §1–§2): chuỗi BEHIND-HOME trên ROM BYD ném NPE ở giữ
 * chỗ và lối lùi K7 đổi display ⇒ YouTube dựng lại, mất nhạc. Ô 7 = mở X lên màn ảo ẩn rồi ĐỂ YÊN: không giữ chỗ, không
 * `move-task`, không K7, không lệnh `--display 0`. Fixture `l4-hidden-*` / `l4-view-*` NGUYÊN VĂN (máy ảo 03/10); bản DẪN XUẤT
 * ghi rõ tại chỗ.
 */
class HiddenParkTest {

    private fun text(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-03-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $name")

    private val waze = "com.waze"
    private val wazeComp = "com.waze/com.waze.FreeMapAppActivity"
    private val ytm = "com.google.android.apps.youtube.music"
    private val ytmComp = "$ytm/$ytm.activities.MusicActivity"
    private val self = "com.byd.launcher"
    private val homes = listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome", "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity")
    private val k12 = "GO_HOME_FENCE"
    private val list = BehindHomePlan.LIST_CMD

    private inner class Rig(
        reads: List<String>,
        private val vd: Int? = 277,
        private val pid: String = "",
        private val parks: Boolean = true,
        private val system: Boolean = false,
    ) {
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
        val port = object : HiddenPark.Port {
            override fun create(): Int? { log += "CREATE"; return vd }
            override fun park(vd: Int, pkg: String): Boolean { log += "PARK $vd $pkg"; return parks }
            override fun release(vd: Int) { log += "RELEASE $vd" }
        }
        val park = HiddenPark(sh, self, k12, homes, { system }, { id, p -> log += "MARK $id $p"; true }, sleep = {})
    }

    /** DẪN XUẤT từ `l4-hidden-covered`: bỏ khối lớp che (690) ⇒ "X một mình ở đỉnh màn ảo ẩn 277" — đúng thứ K4 để lại. */
    private fun alone(): String = Regex("(?ms)^Stack id=690 .*?(?=^Stack id=|\\z)").replace(text("l4-hidden-covered"), "")

    private fun drop(t: String, vararg stacks: Int): String =
        stacks.fold(t) { acc, id -> Regex("(?ms)^Stack id=$id .*?(?=^Stack id=|\\z)").replace(acc, "") }

    private fun banned(log: List<String>) {
        assertFalse(log.any { it == "ANCHOR_START" || it.startsWith("am stack move-task") || "--display 0" in it }, "ô 7: 0 giữ chỗ / move-task / display 0: $log")
    }

    @Test
    fun `duong thuong - tao, K4, X len dinh, TRAO cho o 7 - khong giu cho, khong move-task, khong nha`() {
        val r = Rig(listOf(text("l4-hidden-before"), alone()))
        val out = r.park.park(waze, r.port, wazeComp)
        assertEquals(Result.PARKED, out.result, out.line)
        assertEquals(listOf(list, "CREATE", BehindHomePlan.stageCmd(277, wazeComp), list, "PARK 277 $waze", list), r.log, "đọc lại MỘT lần sau khi đỗ")
        banned(r.log)
        assertFalse(r.log.contains(k12) || r.log.any { it.startsWith("RELEASE") }, r.log.toString())
        assertEquals(TripStepCode.PARKED, TripOutcome.ofBehind(out.result))
        assertEquals(TripStepCode.Result.OK, TripOutcome.ofBehind(out.result).result, "đỗ ô 7 = app sống, không che màn nhà")
    }

    @Test
    fun `so o 7 khong nhan - GIU man ao (X dang song tren do), khong nha, khong lenh them`() {
        val r = Rig(listOf(text("l4-hidden-before"), alone()), parks = false)
        val out = r.park.park(waze, r.port, wazeComp)
        assertEquals(Result.KEPT_UNDER, out.result, out.line)
        assertFalse(r.log.any { it.startsWith("RELEASE") }, r.log.toString())
        assertTrue(out.line.contains("GIỮ màn ảo vd=277"), out.line)
    }

    @Test
    fun `khong tao duoc man ao - NO_STAGE, 0 lenh doi cua so (duong cu con cua)`() {
        val r = Rig(listOf(text("l4-hidden-before")), vd = null)
        val out = r.park.park(waze, r.port, wazeComp)
        assertEquals(Result.NO_STAGE, out.result, out.line)
        assertEquals(listOf(list, "CREATE"), r.log)
    }

    @Test
    fun `doc hong truoc lenh - NO_CHANNEL, 0 lenh`() {
        val r = Rig(listOf(""))
        val out = r.park.park(waze, r.port, wazeComp)
        assertEquals(Result.NO_CHANNEL, out.result, out.line)
        assertEquals(listOf(list), r.log)
    }

    @Test
    fun `dang chay that (task + pid) - ALREADY_RUNNING, khong tao man ao, task nguoi thi van mo`() {
        val running = Rig(listOf(text("l4-hidden-after")), pid = "4242")      // Waze có task trên display 0 (nguyên văn)
        assertEquals(Result.ALREADY_RUNNING, running.park.park(waze, running.port, wazeComp).result)
        assertFalse(running.log.contains("CREATE"), running.log.toString())
        val cold = Rig(listOf(text("l4-hidden-after"), alone()), pid = "")    // task không tiến trình = NGUỘI (L4 · D4)
        assertEquals(Result.PARKED, cold.park.park(waze, cold.port, wazeComp).result)
        assertTrue(cold.log.contains(BehindHomePlan.stageCmd(277, wazeComp)), cold.log.toString())
    }

    /**
     * [ĐO máy ảo 03/10 `p3/e2e-L4/m5a`] YT Music tự mở activity chính NEW_TASK lên display 0 TRƯỚC màn nhà (`l4-view-escaped`
     * nguyên văn; màn ảo ẩn của lượt = 284, trống — cùng cách ghép của `BehindHomeHiddenStageTest`). ⇒ dấu 3311 TRƯỚC K12, K12
     * NGAY; bản đọc sau (`l4-view-after-k12` nguyên văn) thấy màn ảo 284 trống ⇒ nhả SAU K12, không đỗ.
     */
    @Test
    fun `X tu len truoc man nha - dau, K12 ngay, man ao trong thi nha sau K12`() {
        val r = Rig(listOf(text("l4-hidden-before"), text("l4-view-escaped"), text("l4-view-after-k12")), vd = 284)
        val out = r.park.park(ytm, r.port, ytmComp)
        assertEquals(Result.X_FRONT_HOME_RESTORED, out.result, out.line)
        val mark = r.log.indexOf("MARK 3311 $ytm")
        val home = r.log.indexOf(k12)
        assertTrue(mark in 0 until home && home < r.log.indexOf("RELEASE 284"), "dấu → K12 → nhả: ${r.log}")
        assertFalse(r.log.any { it.startsWith("PARK") }, "màn ảo trống ⇒ không đỗ: ${r.log}")
        banned(r.log)

        // DẪN XUẤT (màn ảo của lượt = 285; bỏ VietMap 706): trung chuyển 3310 ở ĐỈNH màn ảo cùng lúc 3311 che màn nhà ⇒ "che màn
        // nhà" thắng (K12 NGAY, không đỗ trước); sau K12 trung chuyển còn trên màn ảo ⇒ đỗ màn ảo đó, KHÔNG nhả.
        val escaped = drop(text("l4-view-escaped"), 706).replace("displayId=283", "displayId=285")
        val kept = Rig(listOf(text("l4-hidden-before"), escaped, drop(escaped, 708)), vd = 285)
        assertEquals(Result.X_FRONT_HOME_RESTORED, kept.park.park(ytm, kept.port, ytmComp).result)
        assertTrue(kept.log.indexOf(k12) in 0 until kept.log.indexOf("PARK 285 $ytm"), "K12 trước, đỗ sau: ${kept.log}")
        assertTrue(kept.log.none { it.startsWith("RELEASE") }, kept.log.toString())
    }

    /**
     * Trung chuyển mở activity chính lên display 0 SAU lần đọc thấy X ở đỉnh màn ảo (DẪN XUẤT: lần 1 = `alone()` kiểu YT Music
     * trên 277, lần 2 = `l4-view-escaped`) ⇒ đã đỗ vẫn phải đọc lại MỘT lần: dấu + K12, mã nói thật là màn nhà bị che thoáng qua.
     */
    @Test
    fun `da do roi X moi len truoc man nha - doc lai mot lan, dau + K12`() {
        val ytmAlone = alone().replace(wazeComp, ytmComp).replace("com.waze/", "$ytm/").replace("taskId=3267", "taskId=3310")
        val r = Rig(listOf(text("l4-hidden-before"), ytmAlone, text("l4-view-escaped")))
        val out = r.park.park(ytm, r.port, ytmComp)
        assertEquals(Result.X_FRONT_HOME_RESTORED, out.result, out.line)
        val park = r.log.indexOf("PARK 277 $ytm")
        assertTrue(park in 0 until r.log.indexOf("MARK 3311 $ytm") && r.log.indexOf("MARK 3311 $ytm") < r.log.indexOf(k12), r.log.toString())
        assertEquals(TripStepCode.HOME_RESTORED, TripOutcome.ofBehind(out.result))
    }

    @Test
    fun `X khong bao gio len man ao - het tran, man ao trong thi nha, X_NOT_STAGED (duong cu duoc thu)`() {
        val r = Rig(listOf(text("l4-hidden-before")))
        val out = r.park.park(waze, r.port, wazeComp)
        assertEquals(Result.X_NOT_STAGED, out.result, out.line)
        assertTrue(r.log.last() == "RELEASE 277", r.log.toString())
        assertEquals(1 + BehindHomeSequence.X_TOP_WAIT_MS / BehindHomeSequence.X_TOP_STEP_MS + 1, r.log.count { it == list }.toLong(), "đọc mỗi nhịp tới trần")
    }

    @Test
    fun `doc hong sau K4 - GIU man ao, UNREAD (chua ro, khong phai OK)`() {
        val r = Rig(listOf(text("l4-hidden-before"), ""))
        val out = r.park.park(waze, r.port, wazeComp)
        assertEquals(Result.UNREAD, out.result, out.line)
        assertFalse(r.log.any { it.startsWith("RELEASE") || it.startsWith("PARK") || it.startsWith("MARK") || it == k12 }, r.log.toString())
        assertEquals(TripStepCode.Result.UNCONFIRMED, TripOutcome.ofBehind(out.result).result)
    }

    @Test
    fun `tu choi truoc MOI lenh - chinh Kachi, app he thong, ten goi la`() {
        listOf(
            Triple(self, false, Result.KEPT_UNDER),
            Triple(waze, true, Result.SYSTEM_APP),
            Triple("com.x;reboot", false, Result.X_NOT_STAGED),
        ).forEach { (pkg, system, want) ->
            val r = Rig(listOf(text("l4-hidden-before")), system = system)
            assertEquals(want, r.park.park(pkg, r.port).result, pkg)
            assertTrue(r.log.isEmpty(), "$pkg: 0 lệnh, kể cả đọc: ${r.log}")
        }
    }
}
