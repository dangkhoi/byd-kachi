package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.NavHudStatus.Source
import com.byd.clusternav.navigation.NlsLiveDump.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** FIX286 R-HUD (S4) — dòng tình trạng HUD: thứ tự ưu tiên + tóm tắt rc từ chuỗi THẬT của `NavigationHudOwner` trên xe. */
class NavHudStatusTest {

    private fun src(
        enabled: Boolean = true, granted: Boolean? = true, bound: Boolean = false,
        connectedAt: Long = 0L, truth: Verdict? = null, truthAt: Long = 0L,
    ) = NavHudStatus.source(enabled, granted, bound, connectedAt, truth, truthAt)

    @Test
    fun `cong tac tat va chua cap dung dau`() {
        assertEquals(Source.OFF, src(enabled = false, truth = Verdict.LIVE, truthAt = 5))
        assertEquals(Source.NO_ACCESS, src(granted = false, truth = Verdict.LIVE, truthAt = 5))
        assertEquals(Source.NO_ACCESS, src(granted = null))
    }

    @Test
    fun `su that NMS moi hon callback thi theo su that`() {
        assertEquals(Source.NOT_LIVE, src(bound = true, connectedAt = 100, truth = Verdict.NOT_LIVE, truthAt = 200))
        assertEquals(Source.LIVE, src(truth = Verdict.LIVE, truthAt = 200))
        // Callback tới SAU lần đọc (vd lượt chữa vừa gắn được) ⇒ theo callback.
        assertEquals(Source.BOUND_IN_PROCESS, src(bound = true, connectedAt = 300, truth = Verdict.NOT_LIVE, truthAt = 200))
        assertEquals(Source.BOUND_IN_PROCESS, src(bound = true, connectedAt = 300, truth = Verdict.UNREADABLE, truthAt = 400))
        assertEquals(Source.UNKNOWN, src())
    }

    /**
     * Senior review FIX286 Pass 4 · P3 — dump ĐÃ đọc mà không ra khối Live (khuôn lạ trên ROM BYD, OC-HUD1) phải là một
     * trạng thái RIÊNG, không rơi vào [Source.UNKNOWN] ("kênh chưa sẵn") — sai nguyên nhân đúng ca cần chụp màn.
     */
    @Test
    fun `dump doc duoc ma khong ra khoi Live la trang thai rieng, khong phai kenh chua san`() {
        assertEquals(Source.UNREADABLE, src(truth = Verdict.UNREADABLE, truthAt = 200))
        // Callback trong tiến trình vẫn nói được "đã gắn" ⇒ ưu tiên nó (thông tin dương), như mọi ca không có sự thật mới.
        assertEquals(Source.BOUND_IN_PROCESS, src(bound = true, connectedAt = 100, truth = Verdict.UNREADABLE, truthAt = 200))
        // Chưa đọc lần nào ⇒ vẫn UNKNOWN.
        assertEquals(Source.UNKNOWN, src(truth = null))
    }

    @Test
    fun `tom tat rc - chuoi that tu log xe`() {
        // Nguyên văn một dòng `cluster-nav … →` (docs/diagnostics, đã có trong repo) — 0 ⇒ nhận, sentinel/ném ⇒ từ chối, skip ⇒ bỏ.
        val rc = "INSTRUMENT_SEND_NAVI_STATUS_SET=0 INSTRUMENT_GUIDE_INFO_SIMPLE_SET=0 INSTRUMENT_FRONT_CROSSING_DISTANCE_SET=0 " +
            "PATHNAME=0 GUIDE_OVERSEA=skip DIST_OVERSEA=skip PATHNAME_OVERSEA=skip RT_H=0 RT_HO=skip " +
            "sdk.guide=-2147482645 sdk.road=0 sdk.rest!=SecurityException"
        assertEquals(HudWriteSummary.Counts(ok = 6, rejected = 2, skipped = 4, noDevice = false), HudWriteSummary.of(rc))
        assertEquals(HudWriteSummary.Counts(0, 1, 0, false), HudWriteSummary.of("GUIDE_OVERSEA=-2147482648"))
        assertTrue(HudWriteSummary.of("InstrumentDevice null (không ghi được)").noDevice)
        assertEquals(HudWriteSummary.Counts(0, 0, 0, false), HudWriteSummary.of(null))
    }
}
