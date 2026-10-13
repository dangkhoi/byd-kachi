package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.HalSignalDemand.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 LAMPLIVE — owner 13/10 *"nối luôn đi, đâu cần camera xi nhan bật mới sáng"*. Khoá vòng đời helper theo NHU CẦU:
 * camera + bảng Đèn chồng nhau = MỘT lần giữ · hết nguồn ⇒ nhả SAU ân hạn · quay lại trong hạn ⇒ không khởi lại · gọi lặp = no-op ·
 * hẹn cũ lỡ chạy sau khi bị huỷ không nhả nhầm. Đồng hồ giả: [Clock] chạy hẹn bằng tay.
 *
 * Thử ĐỎ [ĐO 13/10]: bỏ ba dòng `cancelPending?.invoke()` / `= null` / `gen++` ⇒ `quay lai trong an han` ĐỎ (lượt thôi thứ hai
 * ăn mốc cũ ⇒ nhả sớm giữa lúc bảng đang hiện). `BACKGROUND(1)` ⇒ `PrioritySerialExecutorTest.BACKGROUND drains…` ĐỎ.
 */
class HalSignalDemandTest {

    /** Đồng hồ giả: giữ danh sách hẹn; [advance] chạy các hẹn tới hạn chưa bị huỷ. */
    private class Clock {
        var now = 0L
        private val tasks = mutableListOf<Triple<Long, () -> Unit, BooleanArray>>()
        val schedule: (Long, () -> Unit) -> () -> Unit = { ms, task ->
            val cancelled = BooleanArray(1)
            tasks += Triple(now + ms, task, cancelled)
            ({ cancelled[0] = true })
        }
        fun advance(ms: Long) {
            now += ms
            val due = tasks.filter { it.first <= now }
            tasks.removeAll(due)
            due.filter { !it.third[0] }.forEach { it.second() }
        }
        /** Chạy một hẹn KỂ CẢ khi đã bị huỷ (mô phỏng huỷ trễ — ScheduledFuture.cancel thua cuộc đua). */
        fun fireAllIgnoringCancel() { val all = tasks.toList(); tasks.clear(); all.forEach { it.second() } }
    }

    private class Rig(grace: Long = 60_000L) {
        val clock = Clock()
        val log = mutableListOf<String>()
        val d = HalSignalDemand(grace, clock.schedule, { log += "acquire" }, { log += "release" })
    }

    @Test
    fun `camera va bang Den chong nhau - giu MOT lan, nha sau an han`() {
        val r = Rig()
        r.d.set(Source.CAMERA, true)
        r.d.set(Source.LAMP_WIDGET, true)
        assertEquals(listOf("acquire"), r.log, "hai nguồn = một lần giữ")
        r.d.set(Source.CAMERA, false)
        r.clock.advance(120_000)
        assertEquals(listOf("acquire"), r.log, "còn bảng Đèn ⇒ không nhả")
        r.d.set(Source.LAMP_WIDGET, false)
        r.clock.advance(59_999)
        assertTrue(r.d.isActive, "trong ân hạn vẫn giữ")
        r.clock.advance(1)
        assertEquals(listOf("acquire", "release"), r.log)
        assertFalse(r.d.isActive)
    }

    @Test
    fun `quay lai trong an han - khong nha, khong khoi lai`() {
        val r = Rig()
        r.d.set(Source.LAMP_WIDGET, true)
        r.d.set(Source.LAMP_WIDGET, false)   // lướt sang trang khác
        r.clock.advance(30_000)
        r.d.set(Source.LAMP_WIDGET, true)    // quay lại
        r.clock.advance(120_000)
        assertEquals(listOf("acquire"), r.log)
        assertTrue(r.d.isActive)
        // Thôi lần hai ⇒ ân hạn tính LẠI từ đầu (không ăn mốc của lượt thôi trước).
        r.d.set(Source.LAMP_WIDGET, false)
        r.clock.advance(10_000)
        r.d.set(Source.LAMP_WIDGET, true)
        r.d.set(Source.LAMP_WIDGET, false)
        r.clock.advance(55_000)
        assertTrue(r.d.isActive, "ân hạn đếm từ lượt thôi MỚI NHẤT")
        r.clock.advance(5_000)
        assertEquals(listOf("acquire", "release"), r.log)
    }

    @Test
    fun `goi lap cung nguon - khong giu helper mai`() {
        val r = Rig()
        repeat(3) { r.d.set(Source.CAMERA, true) }
        r.d.set(Source.CAMERA, false)
        r.clock.advance(60_000)
        assertEquals(listOf("acquire", "release"), r.log, "đếm theo TẬP nguồn, không theo số lần gọi")
        repeat(2) { r.d.set(Source.CAMERA, false) }
        assertEquals(listOf("acquire", "release"), r.log, "thôi xin lặp khi đã nhả = no-op")
    }

    @Test
    fun `hen cu lo chay sau khi bi huy - khong nha nham`() {
        val r = Rig()
        r.d.set(Source.LAMP_WIDGET, true)
        r.d.set(Source.LAMP_WIDGET, false)
        r.d.set(Source.CAMERA, true)          // huỷ hẹn — nhưng giả như hẹn vẫn chạy
        r.clock.fireAllIgnoringCancel()
        assertEquals(listOf("acquire"), r.log)
        assertTrue(r.d.isActive)
        assertEquals(setOf(Source.CAMERA), r.d.sources)
    }

    @Test
    fun `nha xong roi xin lai - acquire lan hai`() {
        val r = Rig(grace = 0)
        r.d.set(Source.CAMERA, true)
        r.d.set(Source.CAMERA, false)
        r.clock.advance(0)
        r.d.set(Source.LAMP_WIDGET, true)
        assertEquals(listOf("acquire", "release", "acquire"), r.log)
    }

    @Test
    fun `Watch - chi bao khi DOI, man tat thi thoi can`() {
        val out = mutableListOf<Boolean>()
        val w = LampSignalDemand.Watch { out += it }
        w.demand(setOf("soc", "speed"))
        assertEquals(emptyList<Boolean>(), out, "không bày đèn ⇒ không gọi")
        w.demand(setOf("soc", "light_low_beam"))
        w.demand(setOf("light_left_turn"))          // vẫn bày đèn — không gọi lại
        w.screen(true)
        assertEquals(listOf(true), out)
        w.screen(false)
        w.screen(false)
        assertEquals(listOf(true, false), out, "màn tắt ⇒ thôi cần (một lần)")
        w.screen(true)
        w.demand(null)                              // HOME onStop ⇒ clear()
        assertEquals(listOf(true, false, true, false), out)
    }

    @Test
    fun `wants - null va headlight_feedback khong khoi helper`() {
        assertFalse(LampSignalDemand.wants(null), "null = màn chưa công bố/đã dừng ⇒ không tác dụng phụ")
        assertFalse(LampSignalDemand.wants(setOf("headlight_feedback")), "không có sự kiện cho datum này")
        assertTrue(LampSignalDemand.wants(CarDataDemand.CURATED.getValue("w_car3d")), "xe 3D bày đèn")
        assertTrue(LampSignalDemand.wants(CapabilityGroups.LIGHTS.reads.toSet()), "bảng Đèn bày đèn")
    }

    @Test
    fun `Holder bao man chinh cong bo - set va clear`() {
        val seen = mutableListOf<Set<String>?>()
        val h = CarDataDemand.Holder { seen += it }
        h.set(setOf("light_side"))
        h.clear()
        assertEquals(listOf(setOf("light_side"), null), seen)
    }
}
