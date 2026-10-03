package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.behind.BehindHomePlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L4 · D3(ii) — link vào app nhạc ĐANG Ở Ô bằng K4-VIEW ([TripMusicView]). [ĐO máy ảo 03/10 `p3/e2e-L4/m5a`, fixture
 * `l4-view-escaped` + `l4-view-after-k12` NGUYÊN VĂN]: VIEW vào màn ảo ô ⇒ trung chuyển YT Music ở lại màn ảo, activity chính
 * NEW_TASK lên display 0 TRƯỚC màn nhà (task 3311) ⇒ K12 đưa màn nhà lên (máy ảo: che ≈ 0,7 s, rồi phát Gangnam Style sau
 * màn nhà). Bản đọc "trước" và "sau K8" là DẪN XUẤT (ghi tại chỗ) vì lượt đo thật có VietMap — không phải YT Music — trong ô.
 */
class TripMusicViewTest {

    private fun text(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-03-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $name")

    private val ytm = "com.google.android.apps.youtube.music"
    private val ytmMain = "$ytm/$ytm.activities.MusicActivity"
    private val url = "https://music.youtube.com/watch?v=9bZkp7q19f0"
    private val homes = listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome", "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity")
    private val k12 = "GO_HOME_FENCE"
    private val list = BehindHomePlan.LIST_CMD
    private val block708 = Regex("(?ms)^Stack id=708 .*?(?=^Stack id=|\\z)")

    /** DẪN XUẤT từ `l4-view-after-k12`: bỏ khối YT Music ở display 0 (708), app của ô 283 đổi VietMap → YT Music (task 3309). */
    private fun before(): String = block708.replace(text("l4-view-after-k12"), "")
        .replace("vn.vietmap.live/vn.vietmap.live.MainActivity", ytmMain).replace("vn.vietmap.live/", "$ytm/")

    /** DẪN XUẤT từ `l4-view-after-k12`: khối 708 (task 3311) chuyển sang màn ảo 283 — đọc lại sau K8 thấy task ở ô. */
    private fun afterK8(): String {
        val t = text("l4-view-after-k12")
        val b = block708.find(t)!!.value
        return t.replace(b, "") + b.replace("displayId=0 ", "displayId=283 ")
    }

    private inner class Rig(reads: List<String>) {
        val log = ArrayList<String>()
        private val q = ArrayDeque(reads)
        private var last = ""
        private val sh: (String) -> String = { cmd ->
            log += cmd
            if (cmd == list) (q.removeFirstOrNull() ?: last).also { last = it } else ""
        }
        val view = TripMusicView(sh, k12, homes, { id, p -> log += "MARK $id $p"; true }, sleep = {})
    }

    @Test
    fun `app thoat len display 0 - dau, K12 NGAY, K8 ve lai o`() {
        val r = Rig(listOf(before(), text("l4-view-escaped"), text("l4-view-after-k12"), afterK8()))
        val out = r.view.inSlot(ytm, 283, url)
        assertEquals(TripMusicView.Result.RETURNED, out.result, out.line)
        val v = r.log.indexOf(TripMusicPlan.viewCmd(283, url, ytm))
        val mark = r.log.indexOf("MARK 3311 $ytm")
        val home = r.log.indexOf(k12)
        val k8 = r.log.indexOf(BehindHomePlan.bringToFrontCmd(283, ytmMain))
        assertTrue(v in 0 until mark && mark < home && home < k8, "VIEW → dấu → K12 → K8: ${r.log}")
        assertEquals(1, r.log.count { it == k12 }, "đúng một K12")
    }

    @Test
    fun `app o lai o - khong K12, khong K8`() {
        val r = Rig(listOf(before()))
        val out = r.view.inSlot(ytm, 283, url)
        assertEquals(TripMusicView.Result.STAYED, out.result, out.line)
        assertFalse(r.log.contains(k12) || r.log.any { it.startsWith("am start --display 283 -n") }, r.log.toString())
    }

    @Test
    fun `man nha khong o dinh luc dau - khong gianh man hinh bang K12`() {
        val r = Rig(listOf(text("l4-view-escaped")))   // YT Music đã ở trước màn nhà TỪ TRƯỚC (người lái tự mở)
        val out = r.view.inSlot(ytm, 283, url)
        assertFalse(r.log.contains(k12), r.log.toString())
        assertEquals(TripMusicView.Result.STAYED, out.result, out.line)
    }

    @Test
    fun `app khong o o - 0 lenh`() {
        val r = Rig(listOf(text("l4-hidden-before")))
        assertEquals(TripMusicView.Result.NOT_IN_SLOT, r.view.inSlot(ytm, 283, url).result)
        assertEquals(listOf(list), r.log)
    }
}
