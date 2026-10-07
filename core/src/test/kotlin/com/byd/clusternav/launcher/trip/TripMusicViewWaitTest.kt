package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.96 · R9 — YouTube phát tiếp KHÔNG bỏ cuộc khi ô chưa sẵn ([TripMusicPlace.viewWhenReady]) ═══════════════════════════
 *
 * Khoá lỗi [ĐO log xe 07/10 21:05:14] (dòng `run trip=… -> NOOP … music:<yt>:in-slot:0:alive+4358ms override:resume:found:
 * view:SLOT_NOT_READY`): chờ ô nói sống ở +4358 ms, ~15 s sau (chờ phiên) lúc giao link host ô 0 không còn chỗ dàn dựng của
 * app ⇒ cổng `Ports.view` trả `null` ⇒ bản 2.94/2.95 ghi NOOP rồi thôi — người lái chạm ⇒ YouTube phát bài khác từ đầu.
 * Lần nổ máy trước (20:49:03) ô sẵn ⇒ `view(MOVED)` + tua đúng giây. Nay: ô chưa sẵn ⇒ chờ CHÍNH ô đó sống lại (CÙNG
 * [TripMusicPlace.await]) rồi giao lại; hết trần ⇒ mã + lý do rõ, không NOOP câm.
 */
class TripMusicViewWaitTest {

    private val yt = "com.google.android.youtube"
    private class Clock { var t = 10_000L }
    private fun stage(alive: Boolean, vd: Int = 10) = BehindHomePlan.Stage(0, vd, yt, 1L, alive)
    private fun out(r: BehindHomeSequence.Result) = BehindHomeSequence.Outcome(r, "test")

    @Test
    fun `21h05 - lan giao dau o chua san (null) thi cho o song lai roi giao lai, ra MOVED khong NOOP`() {
        val c = Clock()
        val asked = ArrayList<Boolean>()
        var reads = 0
        val v = TripMusicPlace.viewWhenReady(
            yt, 0, TripMusicPlace.until(c.t, c.t + 150_000), { c.t }, { c.t += it },
            // Host ô 0 đang mở lại app: 3 nhịp chưa có chỗ dàn dựng, rồi sống.
            read = { reads++; TripMusicPlace.Where(0, if (reads <= 3) null else stage(alive = true)) },
            stacks = { error("nhịp đo đã nói sống ⇒ không đọc thẳng") },
            view = { inSlot -> asked += inSlot; if (asked.size == 1) null else out(BehindHomeSequence.Result.MOVED) },
        )
        assertEquals(listOf(true, true), asked, "app ở ô ⇒ CHỈ đường ô, hai lần giao")
        assertEquals(TripStepCode.MOVED, v.code)
        assertEquals(BehindHomeSequence.Result.MOVED, v.outcome?.result)
        assertEquals(2, v.tries)
        assertEquals(0, v.slot)
        assertNull(v.why)
        assertEquals(4 * TripMusicPlace.SLOT_POLL_MS, v.waitedMs, "1 nhịp trước chờ + 3 nhịp chờ ô")
    }

    @Test
    fun `task chua tren man ao o (X_NOT_STAGED) cung cho roi giao lai`() {
        val c = Clock()
        var n = 0
        val v = TripMusicPlace.viewWhenReady(
            yt, 0, c.t + 60_000, { c.t }, { c.t += it }, { TripMusicPlace.Where(0, stage(true)) }, { null },
            view = { n++; out(if (n < 3) BehindHomeSequence.Result.X_NOT_STAGED else BehindHomeSequence.Result.MOVED) },
        )
        assertEquals(TripStepCode.MOVED, v.code)
        assertEquals(3, v.tries)
    }

    @Test
    fun `het tran cho o - SLOT_WAIT kem ly do, khong qua moc dung`() {
        val c = Clock()
        val until = c.t + 7_000
        val v = TripMusicPlace.viewWhenReady(yt, 0, until, { c.t }, { c.t += it }, { TripMusicPlace.Where(0, null) }, { null }, view = { null })
        assertEquals(TripStepCode.SLOT_WAIT, v.code)
        assertEquals("slot-wait", v.why)
        assertEquals(1, v.tries)
        assertTrue(c.t <= until, "không chờ quá mốc dừng")
    }

    @Test
    fun `het han truoc khi cho lai - SLOT_WAIT deadline, khong ngu them`() {
        val c = Clock()
        val v = TripMusicPlace.viewWhenReady(yt, 0, c.t, { c.t }, { error("không ngủ khi đã hết hạn") }, { null }, { null }, view = { null })
        assertEquals(TripStepCode.SLOT_WAIT, v.code)
        assertEquals("deadline", v.why)
    }

    @Test
    fun `nhip do noi song ma giao van khong thay o - toi da VIEW_TRIES lan roi SLOT_NOT_READY tries`() {
        val c = Clock()
        var n = 0
        val v = TripMusicPlace.viewWhenReady(yt, 0, c.t + 600_000, { c.t }, { c.t += it }, { TripMusicPlace.Where(0, stage(true)) }, { null },
            view = { n++; null })
        assertEquals(TripMusicPlace.VIEW_TRIES, n)
        assertEquals(TripStepCode.SLOT_NOT_READY, v.code)
        assertEquals("tries", v.why)
    }

    @Test
    fun `app roi bo cuc giua luc cho - SLOT_NOT_READY left, khong giao ngoai o`() {
        val c = Clock()
        val asked = ArrayList<Boolean>()
        val v = TripMusicPlace.viewWhenReady(yt, 0, c.t + 60_000, { c.t }, { c.t += it }, { TripMusicPlace.Where(null, null) }, { null },
            view = { asked += it; null })
        assertEquals(listOf(true), asked)
        assertEquals(TripStepCode.SLOT_NOT_READY, v.code)
        assertEquals("left", v.why)
    }

    @Test
    fun `app ngoai o - dung MOT lan giao nhu cu, ket qua theo ma chuoi`() {
        val c = Clock()
        var n = 0
        val none = TripMusicPlace.viewWhenReady(yt, null, c.t + 60_000, { c.t }, { error("không chờ") }, { error("không đọc") }, { null },
            view = { n++; assertEquals(false, it); null })
        assertEquals(1, n)
        assertEquals(TripOutcome.ofView(false, null), none.code)
        assertNull(none.why)
    }
}
