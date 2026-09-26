package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceSessionOwner.Phase
import com.byd.clusternav.launcher.voice.VoiceSessionOwner.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá VOICE-WAKE-SESSION-OWNER (2.69): một tiến trình `:wake` chỉ có MỘT phiên. Phiên giả = một hộp pha có thể đổi
 * từ ngoài (mô phỏng `micOpen()`/`phase` thật đổi theo thời gian).
 */
class VoiceSessionOwnerTest {

    private class Fake(var phase: Phase = Phase.IDLE) { var stopped = 0 }

    private var built = 0
    private fun owner() = VoiceSessionOwner<Fake>(classify = { it.phase }, stop = { it.stopped++ })
    private fun build(phase: Phase = Phase.IDLE): Fake { built++; return Fake(phase) }

    @Test
    fun `NONE - lan dau dung mot phien, khong cat gi`() {
        val o = owner()
        assertEquals(State.NONE, o.state()); assertNull(o.current())
        val a = o.acquire(preempt = true) { build() }
        assertFalse(a.reused); assertFalse(a.preempted); assertEquals(1, built)
        assertSame(a.session, o.current())
        assertEquals(State.READY, o.state())
    }

    @Test
    fun `READY - phien da dong duoc DUNG LAI, khong dung moi (stop() moi la mot chieu)`() {
        val o = owner()
        val first = o.acquire(preempt = true) { build() }.session
        first.phase = Phase.IDLE
        val again = o.acquire(preempt = true) { build() }
        assertTrue(again.reused); assertFalse(again.preempted); assertSame(first, again.session)
        assertEquals(1, built); assertEquals(0, first.stopped)
    }

    @Test
    fun `LISTENING - bam them lan nua KHONG cat, ke ca preempt (cung nghia start no-op in-process)`() {
        val o = owner()
        val s = o.acquire(preempt = true) { build() }.session
        s.phase = Phase.LISTENING
        assertEquals(State.LISTENING, o.state())
        val a = o.acquire(preempt = true) { build() }
        assertSame(s, a.session); assertTrue(a.reused); assertFalse(a.preempted)
        assertEquals(0, s.stopped); assertEquals(1, built)
    }

    /** Ca thật của Pass 3: phiên cũ đang ĐỌC ("nói nốt") + phím-thoại ⇒ cắt cũ, dựng mới, đúng MỘT phiên còn được giữ. */
    @Test
    fun `BUSY + preempt - cat phien cu roi dung moi, owner chi giu phien moi`() {
        val o = owner()
        val old = o.acquire(preempt = true) { build() }.session
        old.phase = Phase.BUSY
        assertEquals(State.BUSY, o.state())
        val a = o.acquire(preempt = true) { build() }
        assertTrue(a.preempted); assertFalse(a.reused)
        assertNotSame(old, a.session); assertSame(a.session, o.current())
        assertEquals(1, old.stopped, "phiên cũ phải bị stop() đúng một lần")
        assertEquals(0, a.session.stopped); assertEquals(2, built)
    }

    /** "Hey Kachi" giữa câu trả lời (có thể false-accept) ⇒ KHÔNG cắt — giữ phiên đang chạy. */
    @Test
    fun `BUSY + khong preempt - giu phien cu, khong stop, khong dung moi`() {
        val o = owner()
        val old = o.acquire(preempt = false) { build() }.session
        old.phase = Phase.BUSY
        val a = o.acquire(preempt = false) { build() }
        assertSame(old, a.session); assertTrue(a.reused); assertFalse(a.preempted)
        assertEquals(0, old.stopped); assertEquals(1, built)
    }

    @Test
    fun `release - stop + bo tham chieu, lan sau dung phien moi - release khi rong tra false`() {
        val o = owner()
        assertFalse(o.release())
        val s = o.acquire(preempt = false) { build() }.session
        assertTrue(o.release())
        assertEquals(1, s.stopped); assertNull(o.current()); assertEquals(State.NONE, o.state())
        assertFalse(o.release(), "release lần hai không stop lại xác cũ")
        assertEquals(1, s.stopped)
        val next = o.acquire(preempt = false) { build() }.session
        assertNotSame(s, next, "sau stop() một chiều phải dựng phiên MỚI, không gọi lên xác cũ")
    }

    /** Thứ tự bên trong preempt: bỏ tham chiếu TRƯỚC khi gọi stop — stop() ném cũng không để owner giữ xác. */
    @Test
    fun `preempt - stop nem thi owner van khong giu xac cu`() {
        val o = VoiceSessionOwner<Fake>(classify = { it.phase }, stop = { error("stop hỏng") })
        val old = o.acquire(preempt = true) { build() }.session
        old.phase = Phase.BUSY
        runCatching { o.acquire(preempt = true) { build() } }
        assertNull(o.current(), "stop() ném giữa chừng ⇒ tham chiếu đã bỏ, lượt sau dựng mới")
    }
}
