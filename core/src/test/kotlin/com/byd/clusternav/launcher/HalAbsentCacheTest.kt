package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bài học H1 (PERF 2026-09-16) — "ngưng hỏi lại thứ xe không có" phải là **giãn dần**, không phải **cấm**.
 *
 * Bài `datum vang tam thoi quay lai duoc` là bài đắt nhất: nếu ai đó đổi [HalAbsentCache] thành cấm vĩnh viễn thì
 * mọi test khác ở đây vẫn xanh, chỉ có ô "thời gian sạc còn lại" là câm suốt chuyến sau khi người ta cắm sạc.
 */
class HalAbsentCacheTest {

    @Test
    fun `chua du so lan null thi van doc`() {
        val c = HalAbsentCache(missesBeforeCold = 3)
        c.record("x", got = false, nowMs = 0)
        c.record("x", got = false, nowMs = 1_000)
        assertTrue(c.shouldRead("x", 2_000), "hai lần null chưa đủ để kết luận")
    }

    @Test
    fun `du so lan null thi nguoi`() {
        val c = HalAbsentCache(missesBeforeCold = 3, firstRetryMs = 60_000)
        repeat(3) { c.record("x", got = false, nowMs = it * 1_000L) }
        assertFalse(c.shouldRead("x", 3_000))
        assertFalse(c.shouldRead("x", 61_000), "mốc tính từ lần null thứ ba (t=2 000)")
        assertTrue(c.shouldRead("x", 62_001))
    }

    @Test
    fun `nguoi lau dan nhung co tran`() {
        val c = HalAbsentCache(missesBeforeCold = 1, firstRetryMs = 1_000, maxRetryMs = 4_000)
        c.record("x", got = false, nowMs = 0); assertFalse(c.shouldRead("x", 999))
        c.record("x", got = false, nowMs = 1_000); assertFalse(c.shouldRead("x", 2_999))   // 2 s
        c.record("x", got = false, nowMs = 3_000); assertFalse(c.shouldRead("x", 6_999))   // 4 s (trần)
        c.record("x", got = false, nowMs = 7_000); assertTrue(c.shouldRead("x", 11_001))   // vẫn 4 s, không hơn
    }

    @Test
    fun `datum vang tam thoi quay lai duoc`() {
        // Nổ máy xăng: "vòng tua máy xăng" đang câm bỗng đọc ra số ⇒ cache phải QUÊN sạch, không giữ nhịp giãn cũ.
        // ((V) 2026-09-17: mẫu cũ là `charging_eta_min` — datum đó đã gỡ theo lệnh owner.)
        val c = HalAbsentCache(missesBeforeCold = 2, firstRetryMs = 60_000)
        repeat(2) { c.record("engine_rpm", got = false, nowMs = 0) }
        assertFalse(c.shouldRead("engine_rpm", 100))
        c.record("engine_rpm", got = true, nowMs = 60_001)
        assertTrue(c.shouldRead("engine_rpm", 60_002))
        assertEquals(0, c.coldCount(60_002))
    }

    @Test
    fun `lan null xen ke khong cong don`() {
        val c = HalAbsentCache(missesBeforeCold = 3)
        c.record("x", got = false, nowMs = 0)
        c.record("x", got = false, nowMs = 1)
        c.record("x", got = true, nowMs = 2)      // đọc được một lần ⇒ đếm lại từ đầu
        c.record("x", got = false, nowMs = 3)
        assertTrue(c.shouldRead("x", 4))
    }

    @Test
    fun `cong nhu cau chan truoc cong nguoi`() {
        // Datum KHÔNG hiện thì không được tính là "miss" — nếu không, thứ ta cố ý không đọc sẽ tự nguội, rồi lúc
        // người dùng kéo nó lên màn nó câm thêm một nhịp giãn nữa.
        val table = HalBindingTable(FakeHalGateway())
        val absent = HalAbsentCache(missesBeforeCold = 1)
        val a = CarDataAdapter(table, demand = { emptySet() }, absent = absent, clock = { 0L })
        repeat(5) { a.readSlow(CarStatus()) }
        assertEquals(0, absent.coldCount(0), "datum không hiện thì không được ghi là vắng mặt")
    }

    @Test
    fun `forget lam datum nguoi doc lai NGAY`() {
        // owner 2026-09-24: khi có action, control datum phải chuyển ngay. forget(id) xoá nguội.
        val c = HalAbsentCache(missesBeforeCold = 3, firstRetryMs = 60_000)
        repeat(3) { c.record("inside_temp", got = false, nowMs = it * 1_000L) }
        assertFalse(c.shouldRead("inside_temp", 3_000), "đã nguội")
        c.forget("inside_temp")
        assertTrue(c.shouldRead("inside_temp", 3_100), "sau forget phải đọc lại ngay (không đợi 60s)")
    }

}
