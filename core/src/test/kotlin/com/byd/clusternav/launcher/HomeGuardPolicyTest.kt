package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.HomeGuardPolicy.Facts
import com.byd.clusternav.launcher.HomeGuardPolicy.Reason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 · R8 — khoá luật giữ HOME. Ca gốc [ĐO xe 07/10, 3/3 lần nổ máy]: launcher khác giành HOME ~17 s sau khi Kachi
 * lên, lượt đặt lại lúc ~6 s của KachiAutostart thua ⇒ guard phải thấy và đặt lại; người dùng không chọn Kachi ⇒ không
 * bao giờ đụng; giằng co bị chặn bởi khoảng cách tối thiểu + trần mỗi chuyến.
 */
class HomeGuardPolicyTest {

    private val own = "com.example.kachi"
    private val other = "com.example.otherlauncher"

    private fun facts(
        chosen: Boolean = true,
        cur: String? = other,
        interactive: Boolean? = true,
        channelUp: Boolean = true,
        now: Long = 17_000L,
        last: Long? = null,
        count: Int = 0,
    ) = Facts(chosen, own, cur, interactive, channelUp, now, last, count)

    @Test
    fun `bi gianh luc 17 s sau khi len - dat lai, ghi goi da gianh`() {
        val d = HomeGuardPolicy.decide(facts(now = 17_000L))
        assertTrue(d.reassert)
        assertEquals(Reason.TAKEN, d.reason)
        assertEquals(other, d.taker)
    }

    @Test
    fun `nguoi dung khong chon Kachi - khong bao gio dat lai`() {
        listOf(other, "android", null, own).forEach { cur ->
            val d = HomeGuardPolicy.decide(facts(chosen = false, cur = cur))
            assertFalse(d.reassert)
            assertEquals(Reason.NOT_CHOSEN, d.reason)
        }
    }

    @Test
    fun `wantsKachiHome - mot trong hai dau`() {
        assertTrue(HomeGuardPolicy.wantsKachiHome(homeChosen = true, keepHomeOnBoot = false))
        assertTrue(HomeGuardPolicy.wantsKachiHome(homeChosen = false, keepHomeOnBoot = true))
        assertFalse(HomeGuardPolicy.wantsKachiHome(homeChosen = false, keepHomeOnBoot = false))
    }

    @Test
    fun `Kachi dang la HOME - khong lam gi`() {
        assertEquals(Reason.ALREADY_HOME, HomeGuardPolicy.decide(facts(cur = own)).reason)
    }

    @Test
    fun `khong doc duoc HOME - khong ket luan bi gianh`() {
        val d = HomeGuardPolicy.decide(facts(cur = null))
        assertFalse(d.reassert); assertEquals(Reason.UNREADABLE, d.reason)
    }

    @Test
    fun `hop chon he thong (khong ai giu) cung la khong phai Kachi - dat lai`() {
        // [ĐO] log autostart: "was …ResolverActivity" — gói `android`. Generic: khác gói Kachi là bị mất HOME.
        assertTrue(HomeGuardPolicy.decide(facts(cur = "android")).reassert)
    }

    @Test
    fun `man tat - cho chuyen sau`() {
        assertEquals(Reason.SCREEN_OFF, HomeGuardPolicy.decide(facts(interactive = false)).reason)
        assertTrue(HomeGuardPolicy.decide(facts(interactive = null)).reassert)
    }

    @Test
    fun `kenh chua len - bo qua, nhip sau thu lai`() {
        val d = HomeGuardPolicy.decide(facts(channelUp = false))
        assertFalse(d.reassert); assertEquals(Reason.CHANNEL_DOWN, d.reason); assertEquals(other, d.taker)
    }

    @Test
    fun `khoang cach toi thieu giua hai lan dat lai`() {
        val last = 30_000L
        val near = HomeGuardPolicy.decide(facts(now = last + HomeGuardPolicy.MIN_GAP_MS - 1, last = last, count = 1))
        assertEquals(Reason.RATE_LIMITED, near.reason)
        val far = HomeGuardPolicy.decide(facts(now = last + HomeGuardPolicy.MIN_GAP_MS, last = last, count = 1))
        assertTrue(far.reassert)
    }

    @Test
    fun `tran moi chuyen - het tran thi chi ghi log`() {
        val d = HomeGuardPolicy.decide(facts(now = 1_000_000L, last = 0L, count = HomeGuardPolicy.MAX_PER_TRIP))
        assertFalse(d.reassert); assertEquals(Reason.CAP_REACHED, d.reason)
        assertTrue(HomeGuardPolicy.decide(facts(now = 1_000_000L, last = 0L, count = HomeGuardPolicy.MAX_PER_TRIP - 1)).reassert)
    }

    @Test
    fun `vong giang co mo phong - so lan dat lai khong vuot tran va cach nhau du xa`() {
        // Launcher kia giành lại NGAY sau mỗi lần đặt (ca xấu nhất). Chạy 30 phút theo nhịp thật.
        var now = 0L; var last: Long? = null; var count = 0
        val times = mutableListOf<Long>()
        while (now < 30 * 60_000L) {
            val d = HomeGuardPolicy.decide(facts(now = now, last = last, count = count))
            if (d.reassert) { times += now; last = now; count++ }
            now += HomeGuardPolicy.nextDelayMs(now)
        }
        assertEquals(HomeGuardPolicy.MAX_PER_TRIP, times.size)
        times.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= HomeGuardPolicy.MIN_GAP_MS) }
    }

    @Test
    fun `nhip day dau chuyen roi thua`() {
        assertEquals(HomeGuardPolicy.FAST_TICK_MS, HomeGuardPolicy.nextDelayMs(0L))
        assertEquals(HomeGuardPolicy.FAST_TICK_MS, HomeGuardPolicy.nextDelayMs(HomeGuardPolicy.FAST_WINDOW_MS - 1))
        assertEquals(HomeGuardPolicy.SLOW_TICK_MS, HomeGuardPolicy.nextDelayMs(HomeGuardPolicy.FAST_WINDOW_MS))
        // Ca đã đo (~17 s) nằm gọn trong cửa sổ dày.
        assertTrue(17_000L < HomeGuardPolicy.FAST_WINDOW_MS)
    }

    @Test
    fun `chuyen moi chi khi man tat roi bat`() {
        assertTrue(HomeGuardPolicy.startsNewTrip(false, true))
        assertFalse(HomeGuardPolicy.startsNewTrip(true, true))
        assertFalse(HomeGuardPolicy.startsNewTrip(null, true))
        assertFalse(HomeGuardPolicy.startsNewTrip(false, null))
        assertFalse(HomeGuardPolicy.startsNewTrip(true, false))
    }

    @Test
    fun `log chi khi doi ly do hoac goi giu HOME, va moi lan dat lai`() {
        val a = HomeGuardPolicy.decide(facts(cur = own))
        assertTrue(HomeGuardPolicy.shouldLog(null, a))
        assertFalse(HomeGuardPolicy.shouldLog(a, a))
        val taken = HomeGuardPolicy.decide(facts())
        assertTrue(HomeGuardPolicy.shouldLog(taken, taken))
        val down = HomeGuardPolicy.decide(facts(channelUp = false))
        assertTrue(HomeGuardPolicy.shouldLog(a, down))
        assertFalse(HomeGuardPolicy.shouldLog(down, down))
        assertTrue(HomeGuardPolicy.shouldLog(down, HomeGuardPolicy.decide(facts(channelUp = false, cur = "x.y"))))
    }
}
