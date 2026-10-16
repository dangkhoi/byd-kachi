package com.byd.clusternav.launcher

import com.byd.clusternav.navigation.SpeedShare
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.1.9 · HAL-POLL-COVERED — cắt poll khi sự kiện đã chứng minh (doc `oncar-perf-2026-10-15-evening.md` §B' · §E) ═══════════════
 *
 * Khoá bốn điều:
 *  1. `readControls` ở nhịp THƯỜNG bỏ nút đang được sự kiện phục vụ (cùng luật `fastNeeded`);
 *  2. bằng chứng sự kiện sống theo LẦN NỐI, không theo kế hoạch (thu hẹp rồi nở lại không quên) + tin theo DEVICE (F6-TRUST) với hạn 30 s
 *     cho id chưa có sự kiện riêng;
 *  3. cổng an toàn cốp đọc tốc độ TƯƠI từ binder — không qua bộ nhớ sự kiện, không qua mẫu chung [SpeedShare];
 *  4. dòng "còn poll gì, vì sao" ([HalPollReport]).
 *
 * Thử ĐỎ [ĐO 15/10, mỗi dòng một lần sửa rồi hoàn tác — kết quả ở §E của doc]:
 *  - `readControls` bỏ `filterNotTo(…, ::controlCovered)` ⇒ `nhip thuong bo nut da duoc su kien phuc vu` ĐỎ;
 *  - `setPlan` thu lại `seen.retainAll(keep)` ⇒ `bang chung su kien song qua thu hep ke hoach` ĐỎ;
 *  - `hit` quay về `id !in seen && !(b.trusted && …)` ⇒ `device da chung minh thi id chua co su kien rieng cung thay poll` ĐỎ;
 *  - `CarDataAdapter.readFast` đọc tốc độ từ `speedShare.recent()` ⇒ `cong coc doc toc do tuoi` ĐỎ.
 */
class HalPollCoveredTest {

    private val names = mapOf("AC_WIND_LEVEL" to 111, "AC_CYCLE_MODE" to 112, "BODYWORK_LEFT_HAND_FRONT_DOOR" to 692060168)

    private class Counting(private val inner: HalGateway) : HalGateway by inner {
        val calls = ArrayList<String>()
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? { calls += method; return inner.getter(deviceFqn, method, arg) }
        override fun featureGet(deviceFqn: String, id: Int): String? { calls += "feature#$id"; return inner.featureGet(deviceFqn, id) }
    }

    private fun gw(speed: String = "0") = Counting(FakeHalGateway(
        getters = mapOf("getAcWindLevel" to "3", "getAcCycleMode" to "1", "getCurrentSpeed" to speed, "getCurrentGear" to "3"),
        gettersByArg = mapOf("getDoorState" to mapOf(1 to "0")),
        featureNames = names,
    ))

    private var now = 1_000_000L

    private fun live(c: HalEventCache, g: HalGateway, vararg datums: String): List<HalEventMap.Binding> {
        val b = datums.map { requireNotNull(HalEventMap.bindingOf(it, g)) { it } }
        c.setPlan(b); c.onHello()
        b.groupBy { it.device }.forEach { (d, bs) -> c.onReg(bs.flatMap { it.ids }.toSet(), emptySet(), d) }
        return b
    }

    // ── 1. readControls ở nhịp thường ─────────────────────────────────────────────────────────────────────────

    @Test fun `nhip thuong bo nut da duoc su kien phuc vu`() {
        val g = gw()
        var cov = false
        val a = CarDataAdapter(HalBindingTable(g), { emptySet() }, clock = { now }, controlDemand = { setOf("fan") }, covered = { cov && it == "ac_wind" })
        val s1 = a.readFast(CarStatus())
        assertEquals(listOf("getAcWindLevel"), g.calls, "chưa có sự kiện ⇒ nhịp 1 s đọc nút như cũ")
        assertTrue(a.fastNeeded())
        cov = true
        val s2 = a.readFast(s1)
        assertEquals(1, g.calls.size, "datum của nút được sự kiện phục vụ ⇒ nhịp thường 0 lượt đọc nút")
        assertEquals(s1.controls, s2.controls, "bỏ đọc ⇒ GIỮ số cũ (không nháy \"—\")")
        assertFalse(a.fastNeeded(), "cùng luật ⇒ nhịp 1 s nghỉ")
        cov = false   // hết hạn đối soát ⇒ covers false ⇒ đọc thật một lượt
        a.readFast(s2)
        assertEquals(2, g.calls.size)
    }

    @Test fun `luot su kien van doc nut cua datum vua doi`() {
        val g = gw(); val c = HalEventCache(clock = { now })
        val a = CarDataAdapter(HalBindingTable(EventCachedGateway(g, c)), { emptySet() }, clock = { now }, controlDemand = { setOf("fan") }, covered = c::covers)
        live(c, g, "ac_wind")
        c.onEvent(111, 3, 3.0)
        assertTrue(c.covers("ac_wind"))
        val s = a.readEvents(CarStatus(), setOf("ac_wind"))
        assertTrue("fan" in s.controls, "lượt sự kiện làm mới nút mượn datum vừa đổi: ${s.controls}")
        assertTrue(g.calls.isEmpty(), "0 HAL — giá trị từ bộ nhớ sự kiện")
    }

    // ── 2. bằng chứng sự kiện ─────────────────────────────────────────────────────────────────────────────────

    @Test fun `bang chung su kien song qua thu hep ke hoach`() {
        val g = gw(); val c = HalEventCache(clock = { now })
        val b = live(c, g, "ac_wind").single()
        c.onEvent(111, 4, 4.0)   // [ĐO xe 15/10 17:59:46] sự kiện ĐẦU AC
        c.setPlan(emptyList()); c.onReg(emptySet(), emptySet(), b.device)   // 18:01:15 thu hẹp (màn khuất)
        live(c, g, "ac_wind")                                             // 18:02:36 nở lại
        c.store(b.key, "4")
        now += 40_000
        assertEquals("4", c.hit(b.key), "id ĐÃ có sự kiện riêng trong lần nối ⇒ hạn 60 s (bản cũ quên ⇒ chỉ còn hạn suy 30 s)")
        c.linkDown(); live(c, g, "ac_wind"); c.store(b.key, "4")
        assertNull(c.hit(b.key), "lần nối mới ⇒ chứng minh lại từ đầu")
    }

    @Test fun `device da chung minh thi id chua co su kien rieng cung thay poll, han 30 s`() {
        val g = gw(); val c = HalEventCache(clock = { now })
        val (wind, cycle) = live(c, g, "ac_wind", "ac_cycle")
        c.store(cycle.key, "1")
        assertNull(c.hit(cycle.key), "device AC chưa đẩy gì tới helper ⇒ poll")
        assertEquals(HalEventCache.Trust.UNPROVEN, c.trust("ac_cycle"))
        c.onEvent(111, 2, 2.0)   // chỉ GIÓ đổi
        assertEquals("1", c.hit(cycle.key), "cùng device đã chứng minh ⇒ lấy chế độ lấy gió từ lượt đối soát đã cất")
        assertEquals(HalEventCache.Trust.PROVEN, c.trust("ac_cycle"))
        assertEquals("2", c.hit(wind.key))
        now += HalEventCache.RECONCILE_MS / 2
        assertNull(c.hit(cycle.key), "id chưa có sự kiện riêng ⇒ số cũ chặn ở 30 s")
        assertEquals("2", c.hit(wind.key), "id có sự kiện riêng ⇒ 60 s")
        assertEquals(HalEventCache.Trust.PROVEN, c.trust("ac_cycle"), "trust không xét hạn (log không nhảy mỗi phút)")
        assertEquals(HalEventCache.Trust.NONE, c.trust("door_lf"))
        c.linkDown()
        assertEquals(HalEventCache.Trust.OFF, c.trust("ac_cycle"))
    }

    // ── 3. cổng an toàn cốp ───────────────────────────────────────────────────────────────────────────────────

    @Test fun `cong coc doc toc do tuoi - khong bo nho su kien, khong mau chung`() {
        val g = gw(speed = "37")
        assertNull(HalEventMap.bindingOf("speed", g), "tốc độ KHÔNG bao giờ vào kế hoạch sự kiện ⇒ EventCachedGateway không phục vụ nó")
        assertNull(HalEventMap.bindingOf("gear", g))
        val c = HalEventCache(clock = { now })
        live(c, g, "ac_wind"); c.onEvent(111, 1, 1.0)   // bộ nhớ sự kiện SỐNG, device đã chứng minh
        val share = SpeedShare(clock = { now }).apply { offer(0.0) }   // mẫu chung tươi nói "đứng yên"
        val holder = CarDataDemand.Holder()
        val a = CarDataAdapter(HalBindingTable(EventCachedGateway(g, c)), holder::get, clock = { now }, covered = c::covers, speedShare = share)
        // Phép đọc tươi của AppContainer.readFresh khi màn chính không công bố nhu cầu (người lái ở app dẫn đường).
        val fresh = { id: String -> holder.withSoloIfIdle(setOf(id)) { a.readBody(a.readSlow(a.readFast(CarStatus()))) } }
        repeat(3) {
            val m = CtlSafetyPolicy.readMotion(fresh = fresh, snapshot = { CarStatus() })
            assertEquals(37, m.speedKmh, "đọc binder, không lấy 0 của mẫu chung")
        }
        assertEquals(3, g.calls.count { it == "getCurrentSpeed" }, "mỗi lần bấm một lượt binder")
        assertEquals(37.0, share.recent(), "lượt đọc tươi góp vào mẫu chung (chỉ ghi, không đọc)")
    }

    @Test fun `mau toc do chung - het han thi doc lai, so hong khong ghi de`() {
        val s = SpeedShare(ttlMs = 1_000, clock = { now })
        assertNull(s.recent())
        s.offer(42.0)
        now += 999
        assertEquals(42.0, s.recent())
        s.offer(null); s.offer(-1.0)
        assertEquals(42.0, s.recent(), "đọc hỏng / sentinel không xoá mẫu tốt")
        now += 1
        assertNull(s.recent(), "già 1 s ⇒ người hỏi tự đọc binder")
    }

    @Test fun `nhip nhanh chi gop toc do khi that su doc`() {
        val g = gw(speed = "50")
        val share = SpeedShare(clock = { now })
        var demand: Set<String> = setOf("soc")
        val a = CarDataAdapter(HalBindingTable(g), { demand }, clock = { now }, speedShare = share)
        a.readFast(CarStatus(drivetrain = CarStatus.Drivetrain(speedKmh = 9, gear = null)))
        assertNull(share.recent(), "màn không bày tốc độ ⇒ không đọc ⇒ không góp số CŨ (9) làm như tươi")
        demand = setOf("speed")
        a.readFast(CarStatus())
        assertEquals(50.0, share.recent())
    }

    // ── 4. dòng log ───────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `dong con poll gi va vi sao`() {
        val t = mapOf("ac_wind" to HalEventCache.Trust.PROVEN, "ac_cycle" to HalEventCache.Trust.UNPROVEN)
        val line = HalPollReport.line(setOf("speed", "light_low_beam", "ac_cycle"), setOf("fan")) { t[it] ?: HalEventCache.Trust.NONE }
        assertEquals("poll còn lại: nhanh=speed · không-sự-kiện=light_low_beam · chưa-chứng-minh=ac_cycle · sự-kiện=1", line)
        assertTrue(HalPollReport.line(null, emptySet()) { HalEventCache.Trust.OFF }.contains("đọc hết"))
        val logs = ArrayList<String>()
        val a = CarDataAdapter(HalBindingTable(gw()), { setOf("ac_cycle") }, clock = { now }, trust = { HalEventCache.Trust.UNPROVEN }, log = { logs += it })
        repeat(3) { a.readSlow(CarStatus()) }
        assertEquals(1, logs.size, "chỉ log khi dòng ĐỔI: $logs")
    }
}
