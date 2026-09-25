package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Hardening 2026-09-25 · audit F14 [P2] — "Trả cụm về đồng hồ" hẹn mở lại chiếu sau 2 s; người dùng gạt TẮT Cluster
 * Cast trong 2 s ấy thì hẹn KHÔNG được nổ (giành lại cụm sau khi đã tắt = vi phạm CLAUDE.md §4/§5).
 * Khoá cả hai lá chắn: `cancel()` rút hẹn; `gate` đọc lại công tắc ngay trước khi chạy. (Wiring vào
 * `ClusterNavBridgeCast` canh ở `:app` — `ClusterReopenWiringTest`.)
 */
class DelayedGatedRunTest {

    /** Handler giả: giữ runnable + delay, cho test "nổ" bằng tay. */
    private class FakeHandler {
        val posted = mutableListOf<Pair<Runnable, Long>>()
        val removed = mutableListOf<Runnable>()
        val timer = DelayedGatedRun(post = { r, d -> posted += r to d }, remove = { removed += it })
        fun fireAll() = posted.map { it.first }.forEach { it.run() }
    }

    @Test fun `hen roi huy thi khong chay du hen van no`() {
        val h = FakeHandler()
        var ran = 0
        h.timer.schedule(2_000, gate = { true }) { ran++ }
        assertEquals(2_000L, h.posted.single().second)
        h.timer.cancel()
        assertEquals(listOf(h.posted.single().first), h.removed, "phải removeCallbacks đúng runnable đã post")
        h.fireAll()   // Handler thật đã gỡ; giả lập ca race: message đã dequeue trước removeCallbacks
        assertEquals(0, ran, "hẹn đã huỷ không được chạy")
        assertFalse(h.timer.isPending())
    }

    @Test fun `cong dong luc chay thi khong mo lai, cong mo thi mo`() {
        val h = FakeHandler()
        var castOn = true
        var ran = 0
        h.timer.schedule(2_000, gate = { castOn }) { ran++ }
        castOn = false          // người dùng TẮT trong 2 s, ai đó quên gọi cancel()
        h.fireAll()
        assertEquals(0, ran, "gate đọc sự thật lúc chạy — không giành lại cụm")

        h.timer.schedule(2_000, gate = { castOn }) { ran++ }
        castOn = true
        h.posted.last().first.run()
        assertEquals(1, ran)
        assertFalse(h.timer.isPending(), "chạy xong thì hết chờ")
    }

    @Test fun `hen lan hai thay lan mot — khong mo chieu hai lan`() {
        val h = FakeHandler()
        var ran = 0
        h.timer.schedule(2_000, gate = { true }) { ran++ }
        h.timer.schedule(2_000, gate = { true }) { ran++ }
        assertEquals(1, h.removed.size, "hẹn cũ bị gỡ")
        h.fireAll()
        assertEquals(1, ran, "chỉ hẹn mới nhất chạy")
    }
}
