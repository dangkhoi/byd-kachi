package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 · SLOT-APP-ESCAPE + SHORTCUTS-B-ESCAPE (spec `docs/specs/kachi-293-slot.html` R3) — app RA KHỎI màn ảo ô mà task còn ở
 * display khác ⇒ kết luận "ở chỗ khác" (không phải "đã đóng"), kể cả khi bộ đo chưa từng thấy app trong ô.
 *
 * Khoá lỗi [ĐO máy ảo 02/10 `finish/esc-*`, QA 04/10]: Waze vào ô rồi tự `launchToSide` ra display 0 ~1 s sau ⇒ nhịp đo đầu tiên
 * (5 s sau) không bao giờ thấy nó trong ô ⇒ luật 1 cấm kết luận ⇒ ô đen mãi (`esc-after-chain`: "ô trống"); hoặc đã thấy rồi mới
 * thoát ⇒ báo "app đã đóng" sai (QA 04/10: widget LƯU về trong khi Waze phủ HOME, câu giọng nói "✓ Mở … vào ô 3" không đính
 * chính). Bản đọc dùng nguyên văn fixture `esc-after-move` (máy ảo 02/10): Waze task 2668 trên display 0, ô màn ảo không còn task.
 */
class SlotLivenessElsewhereTest {

    private fun text(name: String, day: String = "2026-10-02"): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-$day-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $day-$name")

    private val waze = "com.waze"

    /** Senior review Pass 2 [P3] — màn ảo ô của CHÍNH lượt đo đó là 185 (`esc-b-on-top`: Waze + VietMap trên display 185); bản đầu ghi 187. */
    private val slotVd = 185

    @Test
    fun `ban doc that - Waze roi man ao o, con task o display 0 = O CHO KHAC, khong phai GONE`() {
        val out = text("esc-after-move")
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(out, waze, slotVd))
        assertEquals(SlotPresence.GONE, SlotPresence.of(out, "com.android.chrome", slotVd), "gói không task ⇒ đã đóng")
        assertEquals(SlotPresence.UNKNOWN, SlotPresence.of("", waze, slotVd), "đọc hỏng ⇒ không kết luận")
    }

    @Test
    fun `chua tung thay trong o ma o cho khac 2 nhip doc duoc lien tiep - ket luan O CHO KHAC`() {
        val l = SlotLiveness()
        assertFalse(l.observe(alive = false, away = true), "một nhịp đơn lẻ có thể rơi giữa lúc hệ dời task")
        assertTrue(l.observe(alive = false, away = true), "hai nhịp liên tiếp ⇒ app ra khỏi ô, còn mở")
        assertTrue(l.elsewhere)
        assertFalse(l.missing)
        repeat(5) { assertFalse(l.observe(alive = false, away = true), "đã báo ⇒ không báo lại") }
    }

    @Test
    fun `chua thay song va KHONG o cho khac - luat 1 giu nguyen, khong bao gio ket luan`() {
        val l = SlotLiveness()
        repeat(50) { assertFalse(l.observe(alive = false, away = false), "app đang mở (chưa có task) ⇒ không kết luận") }
        val broken = SlotLiveness()
        assertFalse(broken.observe(alive = false, away = true))
        assertFalse(broken.observe(alive = false, away = false), "nhịp xen giữa không còn ở chỗ khác ⇒ đếm lại từ đầu")
        assertFalse(broken.observe(alive = false, away = true))
        assertTrue(broken.observe(alive = false, away = true))
    }

    @Test
    fun `da thay song roi thoat ra cho khac - van 2 nhip hut nhu cu, co bao O CHO KHAC`() {
        val l = SlotLiveness()
        assertFalse(l.observe(alive = true))
        assertFalse(l.observe(alive = false, away = true))
        assertTrue(l.observe(alive = false, away = true))
        assertTrue(l.elsewhere, "nhịp kết luận thấy app ở display khác ⇒ không phải đã đóng")
        val died = SlotLiveness()
        died.observe(alive = true)
        died.observe(alive = false, away = false)
        assertTrue(died.observe(alive = false, away = false))
        assertFalse(died.elsewhere, "không còn task ở đâu ⇒ đã đóng như trước 2.93")
    }

    @Test
    fun `man ao nhan lai tu o 7 giu luat cu - TRONG thi mo lai, khong bao O CHO KHAC truoc`() {
        val l = SlotLiveness(adopted = true)
        assertTrue(l.observe(alive = false, away = true), "PARK-2b: một nhịp đọc được ⇒ kết luận như cũ")
        assertTrue(l.missing, "bên gọi ưu tiên onMissing (mở lại vào chính màn ảo) — không đổi")
    }

    /**
     * Senior review 2.93 Pass 2 [P3] — kết luận chưa-từng-thấy-sống chỉ khi màn ảo ô KHÔNG còn app khác: kết luận ⇒ luật hoàn ô ⇒
     * host nhả màn ảo ⇒ cờ 256 kết thúc mọi activity còn trên đó. Bản đọc NGUYÊN VĂN `l4-m1-stale-task` (máy ảo 03/10): Waze task
     * 3245 trên display 0, VietMap task 3244 vẫn ở màn ảo 272 ⇒ một ô 272 đang đo Waze thấy "ở chỗ khác" nhưng ô CHƯA trống (ca đặt
     * tạm `B_NOT_IN_SLOT`: A còn ở đỉnh, chuỗi `evict` mới là bên quyết). `esc-after-move`: màn ảo 185 không còn task ⇒ trống.
     */
    @Test
    fun `o cho khac ma man ao o con app khac - chua trong, khong ket luan cho toi khi trong`() {
        val stale = text("l4-m1-stale-task", day = "2026-10-03")
        val vietmapVd = 272
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(stale, waze, vietmapVd), "Waze rời màn ảo 272, còn task ở display 0")
        assertTrue(SlotLiveness.othersInSlot(stale, waze, vietmapVd), "VietMap còn ở màn ảo 272 ⇒ ô chưa trống")
        assertFalse(SlotLiveness.othersInSlot(stale, "vn.vietmap.live", vietmapVd), "chính app của ô không tính là 'app khác'")
        assertFalse(SlotLiveness.othersInSlot(text("esc-after-move"), waze, slotVd), "màn ảo 185 không còn task ⇒ trống")
        assertFalse(SlotLiveness.othersInSlot("", waze, vietmapVd), "đọc rỗng ⇒ false (bên gọi chỉ hỏi sau ELSEWHERE)")
        assertFalse(SlotLiveness.othersInSlot(stale, waze, 0), "display 0 không phải màn ảo ô")

        val l = SlotLiveness()
        repeat(10) { assertFalse(l.observe(alive = false, away = true, othersInSlot = true), "ô chưa trống ⇒ không kết luận") }
        assertFalse(l.observe(alive = false, away = true), "vừa trống: một nhịp đơn lẻ chưa đủ")
        assertTrue(l.observe(alive = false, away = true), "hai nhịp liên tiếp ô trống + app ở chỗ khác ⇒ kết luận")
        assertTrue(l.elsewhere)

        val flip = SlotLiveness()
        assertFalse(flip.observe(alive = false, away = true))
        assertFalse(flip.observe(alive = false, away = true, othersInSlot = true), "xen một nhịp ô chưa trống ⇒ đếm lại từ đầu")
        assertFalse(flip.observe(alive = false, away = true))
        assertTrue(flip.observe(alive = false, away = true))
    }

    /** Pass 2: cổng "ô chưa trống" CHỈ áp cho đường mới (chưa từng thấy sống) — đã thấy sống thì luật 2 + nhãn như 2.93 Pass 1. */
    @Test
    fun `da thay song thi cong o chua trong khong doi luat 2`() {
        val l = SlotLiveness()
        assertFalse(l.observe(alive = true))
        assertFalse(l.observe(alive = false, away = true, othersInSlot = true))
        assertTrue(l.observe(alive = false, away = true, othersInSlot = true), "2 nhịp hụt như mọi ô đã sống")
        assertTrue(l.elsewhere, "nhãn theo nhịp kết luận: còn task ở display khác")
    }

    /** Cùng bảng với APP_DIED ở MỌI tổ hợp (LƯU, đang HIỆN): sự kiện mới chỉ khác câu báo, không khác chỗ ô về. */
    @Test
    fun `luat hoan o - O CHO KHAC di dung bang cua app vua roi o`() {
        val contents = listOf(
            SlotContent.Empty, SlotContent.App("com.waze"), SlotContent.App("vn.vietmap.live"), SlotContent.Widget(listOf("w_clock")),
        )
        contents.forEach { saved ->
            contents.forEach { shown ->
                listOf(null, "com.waze", "vn.vietmap.live").forEach { pkg ->
                    assertEquals(
                        SlotRevertPlan.next(saved, shown, SlotRevertPlan.Event.APP_DIED, pkg),
                        SlotRevertPlan.next(saved, shown, SlotRevertPlan.Event.APP_ELSEWHERE, pkg),
                        "LƯU=$saved HIỆN=$shown gói=$pkg",
                    )
                }
            }
        }
    }
}
