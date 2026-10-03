package com.byd.clusternav.launcher.behind

import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Review 287 [P3] — màn ảo ẩn bị GIỮ (K7 bị rào chặn / đọc hỏng / chuỗi ném giữa chừng) trước đây KHÔNG BAO GIỜ được nhả khi
 * tiến trình còn sống: màn ảo + luồng `kachi-stage` + `ImageReader` cỡ display 0 rò thêm một bộ mỗi lần chạm *Chạy ngầm* /
 * mỗi chuyến. Bài này khoá [HiddenStageReclaim] trên bản `am stack list` NGUYÊN VĂN của máy ảo 03/10 (`l4-hidden-*`):
 *  - màn ảo còn app người dùng (Waze trên 277, `l4-hidden-covered`) ⇒ GIỮ — rào nhả D2 / L4-c không đổi;
 *  - chỉ còn lớp che của chính Kachi (dẫn xuất: bỏ khối Waze 689) hoặc không còn gì (`l4-hidden-after`) ⇒ NHẢ;
 *  - đọc hỏng ⇒ giữ hết; không có màn ảo nào bị giữ ⇒ 0 lệnh (không tốn một lượt đọc).
 */
class HiddenStageReclaimTest {

    private fun text(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-03-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $name")

    private val self = "com.byd.launcher"

    /** DẪN XUẤT từ `l4-hidden-covered`: bỏ khối stack của Waze (689) ⇒ trên 277 chỉ còn lớp che của Kachi (gỡ chậm). */
    private fun coverOnly(): String = Regex("(?ms)^Stack id=689 .*?(?=^Stack id=|\\z)").replace(text("l4-hidden-covered"), "")

    private class Port(private val keep: List<Int>) : BehindHomeSequence.HiddenStagePort {
        val freed = ArrayList<Int>()
        override fun create(): Int? = error("thu hồi không được tạo màn ảo")
        override fun release(vd: Int) { error("thu hồi đi qua reclaim, không qua release của lượt") }
        override fun cover(vd: Int): Boolean = error("không che")
        override fun uncover(): Int = error("không gỡ che")
        override fun kept(): Collection<Int> = keep
        override fun reclaim(vd: Int) { freed += vd }
    }

    @Test
    fun `con app nguoi dung thi GIU, chi con lop che hoac trong thi NHA, doc hong thi giu het`() {
        val covered = StackParse.parse(text("l4-hidden-covered"))
        assertTrue(covered.any { it.displayId == 277 && it.pkg == "com.waze" }, "fixture: Waze trên màn ảo ẩn 277")
        assertEquals(emptyList<Int>(), HiddenStageReclaim.reclaimable(covered, listOf(277), self), "Waze còn trên 277 ⇒ GIỮ")
        assertEquals(listOf(277), HiddenStageReclaim.reclaimable(StackParse.parse(coverOnly()), listOf(277), self), "chỉ lớp che ⇒ nhả")
        assertEquals(listOf(277, 300), HiddenStageReclaim.reclaimable(StackParse.parse(text("l4-hidden-after")), listOf(277, 300), self))
        assertEquals(emptyList<Int>(), HiddenStageReclaim.reclaimable(null, listOf(277), self), "đọc hỏng ⇒ giữ")
        assertEquals(emptyList<Int>(), HiddenStageReclaim.reclaimable(emptyList(), listOf(277), self), "parse rỗng = đọc hỏng ⇒ giữ")
        assertEquals(emptyList<Int>(), HiddenStageReclaim.reclaimable(covered, listOf(0, -1), self), "không bao giờ display 0 / id lạ")
    }

    @Test
    fun `mot luot - khong giu gi thi 0 lenh, co thi doc MOT lan roi nha dung cai trong`() {
        val log = ArrayList<String>()
        val sh: (String) -> String = { cmd -> log += cmd; text("l4-hidden-covered") }
        assertNull(HiddenStageReclaim.run(sh, Port(emptyList()), self))
        assertEquals(emptyList<String>(), log, "không màn ảo nào bị giữ ⇒ không đọc")

        val p = Port(listOf(277, 300))
        val line = HiddenStageReclaim.run(sh, p, self)
        assertEquals(listOf(BehindHomePlan.LIST_CMD), log, "đúng MỘT lệnh chỉ đọc")
        assertEquals(listOf(300), p.freed, "277 còn Waze ⇒ giữ; 300 không còn gì ⇒ nhả: $line")

        val broken = Port(listOf(300))
        HiddenStageReclaim.run({ throw java.io.IOException("kênh đứt") }, broken, self)
        assertEquals(emptyList<Int>(), broken.freed, "kênh đứt ⇒ không nhả gì")
    }
}
