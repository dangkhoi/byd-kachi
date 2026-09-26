package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Khoá bài học LOG-41KB (buổi xe 2026-09-26): `log=41,2 KB/phút` lúc màn chính đứng yên, mà **≈90 % byte** là
 * `Log.d` của thư viện HAL BYD lặp lại **y nguyên từng chữ** theo nhịp đọc ô điều khiển 1 Hz (owner yêu cầu nhịp
 * đó, 2026-09-21 #5 — không được cắt). Xem KDoc [LogLineThrottle] để có bảng tra theo tag.
 *
 * Bài canh phải đòi **cả bốn** vế, vì mỗi vế là một cách bản vá này hoá thành vô dụng hoặc nguy hiểm:
 *  1. dòng lặp trong cửa sổ bị bỏ (nếu không thì chẳng cắt được gì),
 *  2. dòng **đổi giá trị** đi qua NGAY (nếu không thì mất đúng dữ kiện đi tìm — thang ghế OFF=1/1=2/2=3 buổi 17/09),
 *  3. dòng bị bỏ **có dấu vết** (số lần bỏ đi kèm dòng ghi kế),
 *  4. đồng hồ lùi không khoá được một dòng vĩnh viễn.
 */
class LogLineThrottleTest {

    private val t = LogLineThrottle(windowMs = 10_000L)

    @Test
    fun `dong lap trong cua so bi bo, ngoai cua so duoc ghi lai`() {
        assertEquals(0, t.suppressedBefore("A", 0))
        assertNull(t.suppressedBefore("A", 1_000))
        assertNull(t.suppressedBefore("A", 9_999))
        assertEquals(2, t.suppressedBefore("A", 10_000), "đúng biên: 10 000 ms là ĐÃ hết cửa sổ, và đã bỏ 2 dòng")
        assertNull(t.suppressedBefore("A", 10_001))
    }

    /**
     * Vế quan trọng nhất về mặt chẩn đoán: giá trị HAL đổi ⇒ **chuỗi khác** ⇒ chưa từng thấy ⇒ qua ngay, dù nó
     * xảy ra 1 ms sau dòng cũ. Gỡ vế này (vd tiết chế theo tag thay vì theo nội dung) là mất sạch cái giá trị
     * còn lại của những dòng ấy.
     */
    @Test
    fun `gia tri DOI thi qua NGAY, khong cho het cua so`() {
        assertEquals(0, t.suppressedBefore("D/BYDAutoSettingDevice( 1): … is 3", 0))
        assertNull(t.suppressedBefore("D/BYDAutoSettingDevice( 1): … is 3", 1))
        assertEquals(
            0,
            t.suppressedBefore("D/BYDAutoSettingDevice( 1): … is 1", 1),
            "đổi 3 → 1 là một dòng KHÁC: phải qua ngay, cùng millisecond cũng phải qua",
        )
    }

    @Test
    fun `hau to noi ro da bo bao nhieu dong`() {
        assertEquals("", LogLineThrottle.repeatSuffix(0))
        assertEquals("", LogLineThrottle.repeatSuffix(-1))
        assertEquals("   [+9 lặp]", LogLineThrottle.repeatSuffix(9))
    }

    /** [ĐO xe 14/09] giờ tường của đầu xe bị chỉnh nhảy >5 s giữa phiên — không được biến thành log câm. */
    @Test
    fun `dong ho lui thi GHI, khong khoa vinh vien`() {
        assertEquals(0, t.suppressedBefore("A", 100_000))
        assertNull(t.suppressedBefore("A", 100_001))
        assertEquals(1, t.suppressedBefore("A", 50_000), "giờ lùi ⇒ coi như hết cửa sổ ⇒ ghi, và báo 1 dòng đã bỏ")
    }

    /** Trần LRU là một biên bộ nhớ, KHÔNG được biến thành một dòng bị mất: khoá bị đẩy ra ⇒ lần sau ghi thẳng. */
    @Test
    fun `tran LRU day khoa cu ra thi khoa do duoc ghi lai`() {
        val small = LogLineThrottle(windowMs = 10_000L, maxKeys = 2)
        assertEquals(0, small.suppressedBefore("A", 0))
        assertEquals(0, small.suppressedBefore("B", 0))
        assertEquals(0, small.suppressedBefore("C", 0))   // đẩy "A" ra
        assertEquals(0, small.suppressedBefore("A", 1), "khoá đã bị đẩy ra ⇒ 'chưa từng thấy' ⇒ GHI (không bỏ)")
    }

    /**
     * Ca THẬT của buổi xe 26/09, dựng từ đúng những dòng đo được trong
     * `docs/diagnostics/perf-oncar-2026-09-26/kachi-logs/usage-*.log` (tệp cục bộ, không commit): 8 câu chữ khác
     * nhau lặp vòng ở nhịp 1 Hz suốt 60 s = 480 dòng. Con số này là thứ khoá lời hứa *"cắt ~90 %"*.
     */
    @Test
    fun `nhip 1 Hz cua thu vien HAL bi cat khoang 90 phan tram`() {
        val cycle = listOf(
            "D/BYDAutoSettingDevice(17149): getSeatVentilatingState: The state of 1 seat ventilating is 3",
            "D/BYDAutoAcDevice(17149): getTemprature area is: 1",
            "D/BYDAutoAcDevice(17149): getTemprature temprature is: 24",
            "D/BYDAutoAcDevice(17149): getAcWindLevel value is: 2",
            "D/BYDAutoBodyworkDevice(17149): getWindowOpenPercent: percent is 0",
            "D/BYDAutoAcDevice(17149): getTemprature area is: 2",
            "D/BYDAutoAcDevice(17149): getTemprature temprature is: 24",
            "D/BYDAutoSettingDevice(17149): getSeatVentilatingState: The state of 2 seat ventilating is 3",
        )
        var written = 0
        var total = 0
        for (second in 0 until 60) {
            for (l in cycle) {
                total++
                if (t.suppressedBefore(l, second * 1_000L) != null) written++
            }
        }
        assertEquals(480, total)
        // 8 dòng mỗi giây nhưng chỉ **7 khoá** (câu `getTemprature temprature is: 24` xuất hiện hai lần trong
        // vòng), mỗi khoá ghi lại mỗi 10 s trong 60 s ⇒ 7 × 6 = 42.
        assertEquals(42, written, "mỗi khoá chỉ được ghi lại sau mỗi 10 s")
        val cutPct = 100 - written * 100 / total
        assertEquals(92, cutPct, "cắt ~90 % dòng — đúng bậc số đã ghi trong doc off-car 26/09")
    }

    /** Cửa sổ `0` = tắt tiết chế (đường thoát khi cần đọc log thô, không phải đổi code). */
    @Test
    fun `cua so 0 thi moi dong deu qua`() {
        val off = LogLineThrottle(windowMs = 0L)
        assertNotNull(off.suppressedBefore("A", 0))
        assertEquals(0, off.suppressedBefore("A", 0))
    }
}
