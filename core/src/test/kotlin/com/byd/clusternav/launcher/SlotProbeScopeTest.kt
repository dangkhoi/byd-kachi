package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotProbeScope.Held
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R3 · `SLOT-ELSEWHERE-TWO-HOMES` (spec `docs/specs/kachi-298-plan.html`; review SLOT Pass 2 mục 6 ở `kachi-293-slot.html`).
 *
 * Khoá: hai `KachiHomeActivity` cùng sống, màn MỚI nhận ô n (`SlotVdOwner.adopt` nhả màn ảo ô n của màn CŨ, không báo host cũ) rồi
 * mở app vào màn ảo của NÓ ⇒ bản đo còn sót của màn cũ KHÔNG được kết luận "đã rời ô, vẫn mở ngoài ô" (câu báo sai đè lên màn mới).
 * Bản đọc nguyên văn `am-stack-list-emulator-2026-10-02-esc-b-on-top.txt` [ĐO máy ảo]: Waze task 2668 trên màn ảo 185, cùng lúc BA
 * task `KachiHomeActivity` (2438 · 2084 · 2055) — chính bản đọc này cho thấy nhiều màn chính cùng sống là có thật trên máy ảo.
 * Vai trong bài: 185 = màn ảo ô của màn MỚI (`ws@new`), 187 = màn ảo ô của màn CŨ đã bị nhả (không còn trong sổ).
 */
class SlotProbeScopeTest {

    private fun text(name: String, day: String = "2026-10-02"): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-$day-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $day-$name")

    private val waze = "com.waze"
    private val oldKey = "ws@old#2"
    private val oldVd = 187
    private val newVd = 185

    /** Sổ sau khi màn mới nhận ô 2: màn ảo của màn cũ đã nhả (vắng), còn màn ảo đỗ ô 7 + màn ảo dàn dựng (không phải màn chính). */
    private val heldAfterAdopt = listOf(Held("ws@new", newVd), Held("park", 190), Held("stage", 191))

    @Test
    fun `hai man chinh - app o man ao o cua man MOI khong phai O CHO KHAC voi ban do cua man CU`() {
        val out = text("esc-b-on-top")
        // Trước 2.98 (tái lập lỗi): bản đo của màn cũ thấy Waze ở display khác ⇒ ELSEWHERE ⇒ câu "đã rời ô, vẫn mở ngoài ô".
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(out, waze, oldVd))
        val homes = SlotProbeScope.otherHomes(heldAfterAdopt, oldKey, oldVd)
        assertEquals(setOf(newVd), homes, "chỉ màn ảo ô của màn chính KHÁC; ô 7 / dàn dựng không tính")
        assertEquals(SlotPresence.GONE, SlotPresence.of(out, waze, oldVd, homes), "rời ô của màn cũ — không 'ra ngoài ô'")
    }

    @Test
    fun `chuoi day du - da thay song, man moi nhan o, ban do cu ket luan IM LANG nhu 2_92, khong co elsewhere`() {
        val out = text("esc-b-on-top")
        val homes = SlotProbeScope.otherHomes(heldAfterAdopt, oldKey, oldVd)
        val l = SlotLiveness()
        assertFalse(l.observe(alive = true), "màn cũ đã thấy app sống trong ô của nó")
        val away = SlotPresence.of(out, waze, oldVd, homes) == SlotPresence.ELSEWHERE
        assertFalse(away)
        assertFalse(l.observe(alive = false, away = away), "một nhịp hụt chưa kết luận")
        assertTrue(l.observe(alive = false, away = away), "hai nhịp hụt ⇒ hoàn ô ở màn cũ (luật 2.92)")
        assertFalse(l.elsewhere, "KHÔNG câu 'đã rời ô, vẫn mở ngoài ô'")
        // Chưa từng thấy sống (màn cũ vừa mở ô thì bị nhận mất): không bao giờ kết luận 'ở chỗ khác'.
        val fresh = SlotLiveness()
        repeat(10) { assertFalse(fresh.observe(alive = false, away = away)) }
    }

    @Test
    fun `MOT man chinh - tap rong, hanh vi 2_93 giu nguyen ca voi o khac cung man, o 7 va dan dung`() {
        val out = text("esc-b-on-top")
        val single = listOf(Held("ws@a", 186), Held("ws@a", newVd), Held("park", 190), Held("stage", 191))
        val homes = SlotProbeScope.otherHomes(single, "ws@a#1", 186)
        assertTrue(homes.isEmpty(), "CLAUDE.md §6 — một màn Kachi ⇒ không bỏ display nào")
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(out, waze, 186, homes))
        // Chủ mặc định `"ws"` của VdAppHost cũng là màn chính.
        assertTrue(SlotProbeScope.otherHomes(listOf(Held("ws", 186), Held("ws", newVd)), "ws#1", 186).isEmpty())
    }

    @Test
    fun `hai man chinh nhung app ra display 0 - van O CHO KHAC nhu 2_93`() {
        val out = text("esc-after-move")   // Waze task 2668 trên display 0, ô màn ảo không còn task
        val homes = SlotProbeScope.otherHomes(heldAfterAdopt, oldKey, oldVd)
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(out, waze, oldVd, homes))
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(out, waze, newVd, SlotProbeScope.otherHomes(heldAfterAdopt, "ws@new#2", newVd)))
    }

    @Test
    fun `IN_SLOT va doc hong khong doi khi co tap bo qua`() {
        val out = text("esc-b-on-top")
        assertEquals(SlotPresence.IN_SLOT, SlotPresence.of(out, waze, newVd, setOf(newVd)), "ô của chính nó đứng trước tập bỏ qua")
        assertEquals(SlotPresence.UNKNOWN, SlotPresence.of("", waze, oldVd, setOf(newVd)))
        assertEquals(SlotPresence.GONE, SlotPresence.of(out, "com.android.chrome", oldVd, setOf(newVd)))
    }

    @Test
    fun `chu cua ban do va chu man chinh`() {
        assertEquals("ws@1a2b", SlotProbeScope.ownerOf("ws@1a2b#3"))
        assertEquals("ws", SlotProbeScope.ownerOf("ws#0"))
        assertEquals("odd", SlotProbeScope.ownerOf("odd"))
        assertTrue(SlotProbeScope.isHome("ws") && SlotProbeScope.isHome("ws@ff"))
        assertFalse(SlotProbeScope.isHome("park") || SlotProbeScope.isHome("stage") || SlotProbeScope.isHome("wsx"))
    }
}
