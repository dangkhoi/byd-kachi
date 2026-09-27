package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TRIP-TIME-6MIN — khoá bài học **[ĐO xe Seal 2026-09-27 11:48–11:52 · cầu kiểm thử `hal get`]**:
 * `BYDAutoInstrumentDevice.getCurrentJourneyDriveTime` = `1.8000001` (11:48:19) rồi `1.9` (từ 11:49:08, đứng nhiều phút)
 * ⇒ GIỜ, bậc 0,1 h = 6 phút (`DRIVE_TIME_MAX = 99.9`). Owner: *"thời gian trip không realtime, lâu lâu mới nhảy 1 lần,
 * tầm 5-6 phút"*. Hiển thị `h:mm` từ số ấy là phút GIẢ ("1:48" → "1:54"). Các bài dưới dựng bằng đồng hồ giả, không xe.
 */
class TripTimeSmootherTest {

    private class Clock(var now: Long = 1_700_000_000_000L)
    private fun min(m: Int) = m * 60_000L
    private fun hm(h: Double?) = TelemetryReadout.of("trip_hours", CarStatus(energy = CarStatus.Energy(tripHours = h)))!!.display

    // ═══ 1. Chuỗi bậc thật 1,8 → 1,9 (số đo trên xe) ═══════════════════════════════════════════════════════════════

    @Test fun `1,8 roi 1,9 - hien thi tien tung phut, KHONG cham 1,9 truoc khi HAL noi, nhay dung 1,9 khi HAL doi`() {
        val c = Clock(); val s = TripTimeSmoother { c.now }
        assertEquals("1:48", hm(s.smooth(1.8000001)))                       // mốc như xe trả (float→double)
        val seen = ArrayList<String>()
        repeat(8) { c.now += min(1); seen += hm(s.smooth(1.8000001)) }      // HAL đứng yên 8 phút
        assertEquals(listOf("1:49", "1:50", "1:51", "1:52", "1:53", "1:53", "1:53", "1:53"), seen,
            "tiến từng phút tới +5 rồi ĐỨNG — không bao giờ in 1:54 khi HAL còn nói 1,8")
        c.now += min(1)
        assertEquals("1:54", hm(s.smooth(1.9)), "HAL đổi bậc ⇒ nhảy TIẾN đúng mốc mới")
        c.now += min(1)
        assertEquals("1:55", hm(s.smooth(1.9)), "pha nay chính xác: phút kế tiếp đúng 60 s sau bậc")
        assertEquals(1.9, s.raw)
    }

    @Test fun `kep - 60 phut HAL dung yen thi gia tri DUOI moc + 0,1 va bang moc + 5 phut`() {
        val c = Clock(); val s = TripTimeSmoother { c.now }
        s.smooth(1.8)
        var last = 0.0
        repeat(60) { c.now += min(1); last = s.smooth(1.8)!! }
        assertEquals(1.8 + TripTimeSmoother.MAX_EXTRA_MIN / 60.0, last, 1e-12)
        assertTrue(last < 1.8 + TripTimeSmoother.HAL_STEP_H, "kẹp phải nằm DƯỚI bậc kế tiếp")
    }

    @Test fun `hang so - kep MAX_EXTRA_MIN phai nho hon bac HAL (khong bao gio cham 1,9)`() {
        assertTrue(TripTimeSmoother.MAX_EXTRA_MIN / 60.0 < TripTimeSmoother.HAL_STEP_H)
        assertTrue((TripTimeSmoother.MAX_EXTRA_MIN + 1) / 60.0 >= TripTimeSmoother.HAL_STEP_H, "kẹp không được chặt hơn cần")
    }

    // ═══ 2. Reset / null / nhiễu ════════════════════════════════════════════════════════════════════════════════════

    @Test fun `HAL lui (chuyen moi) thi quen het va di thang`() {
        val c = Clock(); val s = TripTimeSmoother { c.now }
        s.smooth(1.9); c.now += min(4); assertEquals("1:58", hm(s.smooth(1.9)))
        assertEquals("0:06", hm(s.smooth(0.1)), "chuyến mới: đi thẳng mốc mới, không giữ +4 phút của chuyến cũ")
        c.now += min(2)
        assertEquals("0:08", hm(s.smooth(0.1)), "đồng hồ trôi đếm lại từ mốc mới")
    }

    @Test fun `null hien gach ngang nhung KHONG quen - doc lai cung moc thi giu phut da hien, khong cong phut vang`() {
        val c = Clock(); val s = TripTimeSmoother { c.now }
        s.smooth(1.8); c.now += min(3); assertEquals("1:51", hm(s.smooth(1.8)))
        assertNull(s.smooth(null)); assertEquals(1.8, s.raw, "mốc giữ nguyên qua một nhịp đọc hụt")
        c.now += min(30)
        assertEquals("1:51", hm(s.smooth(1.8)), "sau null: giữ 1:51 đã hiện, KHÔNG cộng 30 phút vắng (không thành 1:53)")
        c.now += min(1)
        assertEquals("1:52", hm(s.smooth(1.8)), "đồng hồ trôi tiếp từ chỗ dừng")
    }

    // [Review Pass 2 · P3 #10] Khoá ca người lái thấy số LÙI: một nhịp HAL trả null (đọc hụt, không phải chuyến mới)
    // từng làm bộ làm mượt reset ⇒ "1:59" tụt về "1:54". Nay null chỉ hiện "—" một nhịp rồi số tiếp tục từ chỗ cũ.
    @Test fun `mot nhip HAL null khong lam so lui - 1h59 van la 1h59 sau nhip hut`() {
        val c = Clock(); val s = TripTimeSmoother { c.now }
        s.smooth(1.8); c.now += min(1); s.smooth(1.9)            // bậc thật ⇒ pha chính xác
        c.now += min(5); assertEquals("1:59", hm(s.smooth(1.9)))
        assertNull(s.smooth(null))                                // đọc hụt một nhịp
        c.now += 10_000L
        assertEquals("1:59", hm(s.smooth(1.9)), "không lùi về 1:54")
        c.now += min(1)
        assertEquals("2:00", hm(s.smooth(2.0)), "bậc HAL tới ⇒ nhảy tiến bình thường")
        assertEquals("0:00", hm(s.smooth(0.0)), "HAL lùi thật (chuyến mới) vẫn reset")
    }

    @Test fun `nhieu float 1,8000001 vs 1,8 la CUNG mot moc - khong reset, khong neo lai`() {
        val c = Clock(); val s = TripTimeSmoother { c.now }
        s.smooth(1.8000001); c.now += min(2)
        assertEquals("1:50", hm(s.smooth(1.8)), "1.8 sau 1.8000001 KHÔNG phải HAL lùi")
        c.now += min(1)
        assertEquals("1:51", hm(s.smooth(1.8000001)), "1.8000001 sau 1.8 KHÔNG phải bậc tăng (không về 1:48)")
    }

    // ═══ 3. Đồng hồ lùi + đơn điệu ═════════════════════════════════════════════════════════════════════════════════

    @Test fun `dong ho he thong lui thi giu so phut dang hien, roi dem tiep`() {
        val c = Clock(); val s = TripTimeSmoother { c.now }
        s.smooth(1.8); c.now += min(3); assertEquals("1:51", hm(s.smooth(1.8)))
        c.now -= min(60)                                              // NTP/GPS kéo giờ lùi một tiếng
        assertEquals("1:51", hm(s.smooth(1.8)), "không lùi về 1:48")
        c.now += min(1)
        assertEquals("1:52", hm(s.smooth(1.8)), "đếm tiếp từ số đang hiện, không đứng chết một tiếng")
    }

    @Test fun `don dieu - giua hai lan reset gia tri khong bao gio giam`() {
        val c = Clock(); val s = TripTimeSmoother { c.now }
        val raws = listOf(1.8, 1.8, 1.8000001, 1.8, 1.9, 1.9, 1.9, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.1)
        var prev = -1.0
        raws.forEachIndexed { i, r ->
            c.now += if (i % 3 == 0) min(1) else 10_000L             // xen nhịp 10 s và nhịp phút
            val v = s.smooth(r)!!
            assertTrue(v >= prev, "bước $i: $v < $prev")
            assertTrue(v < r + TripTimeSmoother.HAL_STEP_H, "bước $i: $v vượt bậc kế của $r")
            prev = v
        }
    }

    // ═══ 4. hoursToHm — biên phút bị sai số nhị phân (phát hiện khi làm bộ làm mượt) ══════════════════════════════

    @Test fun `hoursToHm - 4,1 h in 4h06 (khong 4h05) va 1,9 + 1 phut in 1h55 (khong 1h54)`() {
        // Trước 2026-09-27: `(h*60).toInt()` ⇒ 4.1*60 = 245.99999999999997 ⇒ "4:05". Với mốc + k/60 sai 620/6000 ca.
        assertEquals("4:06", hm(4.1))
        assertEquals("1:55", hm(1.9 + 1 / 60.0))
        assertEquals("1:36", hm(1.6))                                 // ca V4 gốc giữ nguyên
        assertEquals("0:00", hm(0.0))
    }

    // ═══ 5. END-TO-END: HAL giả → CarDataAdapter (Gate) → CarStatus → TelemetryReadout ════════════════════════════

    @Test fun `E2E qua CarDataAdapter - chu trip_hours tien 1h48 roi 1h49 roi tiep theo dong ho gia, nhay 1h54 khi HAL 1,9`() {
        val getters = mutableMapOf<String, String?>("getCurrentJourneyDriveTime" to "1.8000001")
        val c = Clock()
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway(getters = getters)), clock = { c.now })
        var st = a.readSlow(CarStatus())
        assertEquals("1:48", hm(st.energy.tripHours))
        val seen = ArrayList<String>()
        repeat(7) { c.now += min(1); st = a.readSlow(st); seen += hm(st.energy.tripHours) }
        assertEquals(listOf("1:49", "1:50", "1:51", "1:52", "1:53", "1:53", "1:53"), seen)
        getters["getCurrentJourneyDriveTime"] = "1.9"                 // xe đổi bậc
        st = a.readSlow(st)
        assertEquals("1:54", hm(st.energy.tripHours))
    }

    @Test fun `E2E - o khong hien thi GIU prev va KHONG dua prev da lam muot vao lai bo lam muot`() {
        // Bệnh cần chặn: `Gate.read` trả `prev` khi datum ngoài nhu cầu; prev = 1.85 (đã mượt) mà đi vào smoother thì bị
        // coi là bậc tăng ⇒ mốc tự nâng ⇒ có thể in 1:59 trong khi xe mới 1,8 h.
        val getters = mutableMapOf<String, String?>("getCurrentJourneyDriveTime" to "1.8")
        val c = Clock(); var want: Set<String>? = null
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway(getters = getters)), demand = { want }, clock = { c.now })
        var st = a.readSlow(CarStatus())
        repeat(3) { c.now += min(1); st = a.readSlow(st) }
        assertEquals("1:51", hm(st.energy.tripHours))
        want = setOf("soc")                                            // trip rời màn: 10 nhịp không đọc
        repeat(10) { c.now += 10_000L; st = a.readSlow(st) }
        assertEquals("1:51", hm(st.energy.tripHours), "ngoài nhu cầu ⇒ giữ nguyên số cũ, không nháy '—'")
        want = null                                                    // trip về màn: đồng hồ trôi vẫn neo ở mốc 1,8 gốc
        c.now += min(1)                                                // tổng 5:40 kể từ mốc ⇒ +5 phút
        st = a.readSlow(st)
        assertEquals("1:53", hm(st.energy.tripHours), "mốc vẫn là 1,8: +5 phút kẹp, không phải 1,85 + gì đó")
        repeat(3) { c.now += min(1); st = a.readSlow(st) }
        assertEquals("1:53", hm(st.energy.tripHours), "vẫn kẹp dưới 1,9 dù thêm 3 phút")
    }

    @Test fun `E2E - HAL doc khong ra (null) thi o hien gach ngang - moc lam muot giu nguyen`() {
        val getters = mutableMapOf<String, String?>("getCurrentJourneyDriveTime" to "1.8")
        val c = Clock()
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway(getters = getters)), clock = { c.now })
        var st = a.readSlow(CarStatus()); c.now += min(2); st = a.readSlow(st)
        assertEquals("1:50", hm(st.energy.tripHours))
        getters["getCurrentJourneyDriveTime"] = null
        st = a.readSlow(st)
        assertNull(st.energy.tripHours, "đọc không ra ⇒ null ⇒ '—' (không đóng băng 1:50 trông như đang sống)")
    }
}
