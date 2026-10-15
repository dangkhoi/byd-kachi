package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.7 · STALE-AUDIT — lượt ghi KHÔNG đi qua ô (phím · giọng nói) để lại ĐỦ ba dấu vết của cú chạm ([ControlWriteEcho]): bảng ·
 * đích giữ [LampHold] · báo màn vẽ lại. Chẩn đoán: `docs/diagnostics/stale-display-audit-2026-10-15.md`.
 *
 * Mỗi bài dựng ĐÚNG luật "hiện gì" của bề mặt (ô TOGGLE/COVER, chấm cốp/đèn 3D: `LampHold.shown(id, số đọc) ?: bảng`) để khoá
 * *"phím ghi xong ⇒ màn hiện số mới"* — gỡ đích giữ hoặc gỡ lượt báo ⇒ bài đỏ.
 */
class ControlWriteEchoTest {

    private val trunk = ControlRegistry.byId("trunk")!!

    /** Luật hiện của ô hai mức có readKey (ControlTileFactory TOGGLE/COVER · Car3dTaps · bảng Đèn): đích giữ › số đọc › bảng. */
    private fun shown(hold: LampHold, sent: ControlLastSent, id: String, read: Int?, now: Long): Int =
        hold.shown(id, read, now) ?: sent.index(id)

    @Test
    fun `phim dong cop - xe con doc mo - man hien DONG ngay nhu cu cham`() {
        val sent = ControlLastSent().also { it.record("trunk", 1) }
        val hold = LampHold()
        val drawn = shown(hold, sent, "trunk", read = 1, now = 0)
        assertEquals(1, drawn, "trước: cốp đang mở")
        ControlWriteEcho.applied(trunk, 0, setStep = { _, _ -> }, sent = sent, hold = hold, now = 1_000)
        assertEquals(0, sent.index("trunk"), "bảng = lệnh vừa ăn")
        assertEquals(1L, sent.relayed.value, "báo màn vẽ lại (thiếu ⇒ ô chỉ đổi ở nhịp xe kế, [CHƯA BIẾT] bao lâu khi số đứng)")
        // Cốp chỉ đọc 0 khi chốt khoá (~5,8 s): trong lúc ấy số đọc vẫn 1 — không đích giữ thì màn hiện MỞ, khác hẳn cú chạm.
        assertEquals(0, shown(hold, sent, "trunk", read = 1, now = 3_000), "đích giữ thắng số đọc cũ")
        assertEquals(0, shown(hold, sent, "trunk", read = 0, now = 6_800), "xe xác nhận ⇒ hiện số mới")
        assertNull(hold.held("trunk", false, 6_801), "đã xác nhận ⇒ thả giữ")
    }

    @Test
    fun `xe tu choi - het han giu - so doc thang`() {
        val sent = ControlLastSent(); val hold = LampHold()
        ControlWriteEcho.applied(trunk, 1, setStep = { _, _ -> }, sent = sent, hold = hold, now = 0)
        val after = hold.holdMsOf("trunk") + 1
        assertEquals(0, shown(hold, sent, "trunk", read = 0, now = after), "không lạc quan mãi: xe không làm thì màn nói thật")
    }

    @Test
    fun `nut khong doc duoc - khong giu dich - bang va bao man la du`() {
        val def = ControlRegistry.ALL.first { it.kind == ControlKind.TOGGLE && it.readKey.isBlank() }
        val sent = ControlLastSent(); val hold = LampHold()
        val before = sent.index(def.id)
        ControlWriteEcho.applied(def, if (before > 0) 0 else 1, setStep = { _, _ -> }, sent = sent, hold = hold, now = 0)
        assertFalse(ControlWriteEcho.holds(def))
        assertNull(hold.held(def.id, null, 1), "${def.id}: không số đọc để xác nhận ⇒ không giữ")
        assertEquals(if (before > 0) 0 else 1, shown(hold, sent, def.id, read = null, now = 1), "hình theo bảng")
        assertEquals(1L, sent.relayed.value, "bảng đổi mà không ô nào vẽ ⇒ phải báo (ô/điểm không ô đọc chỉ vẽ khi được gọi)")
    }

    @Test
    fun `STEP ghi qua bang muc cua app, khong cham bang lenh cuoi, van bao man`() {
        val fan = ControlRegistry.byId("fan")!!
        val sent = ControlLastSent(); val hold = LampHold()
        var step: Pair<String, Int>? = null
        ControlWriteEcho.applied(fan, 3, setStep = { id, v -> step = id to v }, sent = sent, hold = hold, now = 0)
        assertEquals("fan" to 3, step)
        assertEquals(ControlLastSent.startIndex("fan"), sent.index("fan"), "STEP không dùng bảng lệnh cuối")
        assertNull(hold.held("fan", null, 1), "STEP không giữ đích (ô STEP so với hình — StepTileSync)")
        assertEquals(1L, sent.relayed.value)
    }

    @Test
    fun `record cua o tu ve KHONG bao - tranh vong lap giua hai be mat`() {
        val sent = ControlLastSent()
        sent.record("trunk", 1); sent.record("trunk", 0)
        assertEquals(0L, sent.relayed.value)
        sent.announce()
        assertEquals(1L, sent.relayed.value)
    }

    @Test
    fun `giu dich dung cho TOGGLE va COVER doc duoc, sinh tu registry`() {
        ControlRegistry.ALL.forEach { d ->
            val want = (d.kind == ControlKind.TOGGLE || d.kind == ControlKind.COVER) && d.readKey.isNotBlank()
            assertEquals(want, ControlWriteEcho.holds(d), d.id)
        }
        assertTrue(ControlWriteEcho.holds(trunk))
    }

    @Test
    fun `dong ho cung goc uptime - don dieu`() {
        val a = ControlWriteEcho.uptimeMs(); val b = ControlWriteEcho.uptimeMs()
        assertTrue(b >= a)
    }
}
