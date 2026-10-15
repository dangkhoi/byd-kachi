package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá hồi quy perf §8.1 (`docs/diagnostics/perf-315-2026-10-14.md`, [ĐO máy ảo 15/10]): xoay xe 3D 6 vuốt ⇒ mặt khung 3D chọn lại
 * họ ~53 lần/đợt (mỗi lượt layout ~20 Hz) thay vì chỉ khi dừng; luồng chính 160–190 → 240–380 ms/đợt. Luật [InkMotion]:
 *  1. trong lúc nội dung DỜI: 0 lượt chọn (mỗi lần dời chỉ đặt lại MỘT hẹn — không nhịp lặp, không tích hẹn);
 *  2. dừng mà không có tín hiệu ⇒ đúng MỘT lượt chọn, [InkMotion.QUIET_MS] sau lần dời CUỐI;
 *  3. dừng CÓ tín hiệu tường minh (`Car3dInk.settle` cuối đợt khung) ⇒ lượt đó là lượt DUY NHẤT — hẹn đang chờ bị bỏ.
 * Bỏ [InkMotion.hold] khỏi lượt sự kiện của `AdaptiveInk.refresh` ⇒ `AdaptiveInkSourceContractTest` đỏ; bỏ [InkMotion.solving]
 * ⇒ ca 3 ở đây đỏ (2 lượt chọn).
 */
class InkMotionTest {

    /** Hàng đợi giờ ảo một luồng (như `Handler` của luồng chính): `post` / `removeCallbacks` / chạy tới mốc. */
    private class Clock {
        var now = 0L
        private val q = ArrayList<Pair<Long, Runnable>>()
        val pending get() = q.size
        fun post(r: Runnable, ms: Long) { q += (now + ms) to r }
        fun unpost(r: Runnable) { q.removeAll { it.second === r } }
        fun advance(to: Long) {
            while (true) {
                val next = q.filter { it.first <= to }.minByOrNull { it.first } ?: break
                q.remove(next); now = next.first; next.second.run()
            }
            now = to
        }
    }

    /** Mặt giả: lượt sự kiện thấy dời ⇒ giữ; lượt đầy đủ ⇒ [InkMotion.solving] rồi chọn (đúng thứ tự của `AdaptiveInk.refresh`). */
    private class Sim(val clock: Clock) {
        var solves = 0
        val motion: InkMotion = InkMotion(InkMotion.QUIET_MS, clock::post, clock::unpost) { full() }
        fun layoutWhileMoving() = motion.hold()
        fun full() { motion.solving(); solves++ }
    }

    /** 6 vuốt × 26 khung, lượt layout mỗi ~50 ms ([ĐO] khoảng cách các dòng `R29 chấm` ở §8 B2 = 48–52 ms). */
    private fun rotate(s: Sim, frames: Int = 26, stepMs: Long = 50) {
        repeat(frames) { s.clock.advance(s.clock.now + stepMs); s.layoutWhileMoving() }
    }

    @Test
    fun `dang doi - 0 luot chon, chi MOT hen cho`() {
        val s = Sim(Clock())
        rotate(s)
        assertEquals(0, s.solves, "chọn lại trong lúc xoay = đúng hồi quy §8.1")
        assertEquals(1, s.clock.pending, "mỗi lần dời đặt LẠI một hẹn, không tích hẹn")
        assertTrue(s.motion.armed)
    }

    @Test
    fun `dung khong tin hieu - dung MOT luot chon sau QUIET_MS ke tu lan doi cuoi`() {
        val s = Sim(Clock())
        rotate(s)
        val last = s.clock.now
        s.clock.advance(last + InkMotion.QUIET_MS - 1)
        assertEquals(0, s.solves, "chưa lặng đủ")
        s.clock.advance(last + InkMotion.QUIET_MS)
        assertEquals(1, s.solves)
        s.clock.advance(last + 60_000)
        assertEquals(1, s.solves, "không nhịp lặp: lặng mãi vẫn chỉ một lượt")
        assertFalse(s.motion.armed)
    }

    @Test
    fun `dung co tin hieu settle - luot settle la luot DUY NHAT`() {
        val s = Sim(Clock())
        rotate(s)
        s.full()                                    // Car3dInk.settle cuối đợt khung ⇒ AdaptiveInk.rescore ⇒ lượt đầy đủ
        s.clock.advance(s.clock.now + 10_000)
        assertEquals(1, s.solves, "hẹn chờ phải bị bỏ ⇒ không chọn lần hai")
        assertEquals(0, s.clock.pending)
    }

    @Test
    fun `ba dot xoay cach nhau - moi dot dung MOT luot`() {
        val s = Sim(Clock())
        repeat(3) { rotate(s); s.clock.advance(s.clock.now + 900) }   // khe 0,9 s giữa hai vuốt (kịch bản §8.4) > QUIET_MS
        assertEquals(3, s.solves)
    }
}
