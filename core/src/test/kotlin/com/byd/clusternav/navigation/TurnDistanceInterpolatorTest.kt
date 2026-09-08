package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit test THUẦN cho TurnDistanceInterpolator — khoá lại các kịch bản bug đã sửa:
 * dừng-đèn-đỏ phải GIỮ số (không tự trôi về 0), đang đi thì trừ dần, maneuver mới thì snap.
 */
class TurnDistanceInterpolatorTest {

    @BeforeEach fun setup() { TurnDistanceInterpolator.reset() }

    @Test fun `chưa anchor thì project trả -1`() {
        assertEquals(-1, TurnDistanceInterpolator.project(10.0, 1000L))
    }

    @Test fun `anchor đặt baseline`() {
        TurnDistanceInterpolator.anchor(1000, "k", 1000L)
        assertEquals(1000, TurnDistanceInterpolator.anchorMeters())
    }

    @Test fun `dừng (tốc độ 0) GIỮ số — không trôi về 0`() {
        TurnDistanceInterpolator.anchor(1000, "k", 1000L)
        assertEquals(1000, TurnDistanceInterpolator.project(0.0, 2000L))
        assertEquals(1000, TurnDistanceInterpolator.project(0.0, 5000L))   // đứng yên lâu vẫn giữ
    }

    @Test fun `đang đi thì cự ly trừ dần theo tốc độ thật`() {
        TurnDistanceInterpolator.anchor(1000, "k", 1000L)
        val out = TurnDistanceInterpolator.project(10.0, 2000L)   // 10 m/s trong 1s → ~9.5m (FACTOR 0.95)
        assertTrue(out in 985..995, "kỳ vọng ~990, thực tế $out")
        assertTrue(out < 1000, "phải giảm so với baseline")
    }

    @Test fun `maneuver mới (đổi key) SNAP thẳng sang cự ly mới`() {
        TurnDistanceInterpolator.anchor(1000, "k1", 1000L)
        TurnDistanceInterpolator.project(10.0, 2000L)
        TurnDistanceInterpolator.anchor(500, "k2", 3000L)          // key khác → snap
        assertEquals(500, TurnDistanceInterpolator.anchorMeters())
        assertEquals(500, TurnDistanceInterpolator.project(0.0, 3000L))
    }

    @Test fun `clearAnchor xoá track (frame chỉ-hướng)`() {
        TurnDistanceInterpolator.anchor(1000, "k", 1000L)
        TurnDistanceInterpolator.clearAnchor()
        assertEquals(-1, TurnDistanceInterpolator.project(10.0, 2000L))
    }

    @Test fun `refine ghi lại ground-truth đọc-màn cho log (kể cả chưa anchor)`() {
        assertEquals(-1, TurnDistanceInterpolator.lastRefined())
        TurnDistanceInterpolator.refine(250, 1234L)          // chưa anchor: không snap nhưng VẪN ghi ground-truth
        assertEquals(250, TurnDistanceInterpolator.lastRefined())
        assertEquals(1234L, TurnDistanceInterpolator.lastRefinedAt())
    }

    @Test fun `refine âm không đè ground-truth đã ghi`() {
        TurnDistanceInterpolator.refine(180, 1000L)
        TurnDistanceInterpolator.refine(-1, 2000L)           // đọc-màn thất bại → giữ giá trị cũ
        assertEquals(180, TurnDistanceInterpolator.lastRefined())
        assertEquals(1000L, TurnDistanceInterpolator.lastRefinedAt())
    }

    @Test fun `reset xoá ground-truth đọc-màn`() {
        TurnDistanceInterpolator.refine(180, 1000L)
        TurnDistanceInterpolator.reset()
        assertEquals(-1, TurnDistanceInterpolator.lastRefined())
        assertEquals(0L, TurnDistanceInterpolator.lastRefinedAt())
    }

    // ── B4 (2026-08-19): vệ sinh chẩn đoán — screen-read INVALID khi stale/không-đường + guard refine ──

    @Test fun `freshScreenRead giữ nguyên khi tươi + có đường`() {
        assertEquals(250, TurnDistanceInterpolator.freshScreenRead(250, 1000L, "Nguyễn Văn Linh"))
        assertEquals(0, TurnDistanceInterpolator.freshScreenRead(0, 0L, "Đường A"))   // 0m (đã tới) vẫn là mẫu thật
    }

    @Test fun `freshScreenRead INVALID khi STALE (GMaps nền, tuổi 224s như chuyến 2026-08-18)`() {
        assertEquals(
            TurnDistanceInterpolator.INVALID,
            TurnDistanceInterpolator.freshScreenRead(10, 224049L, "Nguyễn Văn Linh"),
        )
    }

    @Test fun `freshScreenRead INVALID khi đường rỗng-hoặc-trắng`() {
        assertEquals(TurnDistanceInterpolator.INVALID, TurnDistanceInterpolator.freshScreenRead(250, 1000L, ""))
        assertEquals(TurnDistanceInterpolator.INVALID, TurnDistanceInterpolator.freshScreenRead(250, 1000L, "   "))
    }

    @Test fun `freshScreenRead INVALID khi chưa đọc được (meters âm) hoặc chưa có mốc (age âm)`() {
        assertEquals(TurnDistanceInterpolator.INVALID, TurnDistanceInterpolator.freshScreenRead(-1, -1L, "road"))
        assertEquals(TurnDistanceInterpolator.INVALID, TurnDistanceInterpolator.freshScreenRead(250, -1L, "road"))
    }

    @Test fun `freshScreenRead biên tuổi = ngưỡng là hợp lệ, vượt 1ms là INVALID`() {
        assertEquals(
            250,
            TurnDistanceInterpolator.freshScreenRead(250, TurnDistanceInterpolator.SCREEN_READ_STALE_MS, "road"),
        )
        assertEquals(
            TurnDistanceInterpolator.INVALID,
            TurnDistanceInterpolator.freshScreenRead(250, TurnDistanceInterpolator.SCREEN_READ_STALE_MS + 1, "road"),
        )
    }

    @Test fun `refine TƯƠI (mặc định readAgeMs=0) vẫn snap + ghi ground-truth như cũ`() {
        TurnDistanceInterpolator.anchor(1000, "k", 1000L)
        TurnDistanceInterpolator.refine(500, 2000L)                       // default fresh → snap
        assertEquals(500, TurnDistanceInterpolator.anchorMeters())
        assertEquals(500, TurnDistanceInterpolator.lastRefined())
        assertEquals(2000L, TurnDistanceInterpolator.lastRefinedAt())
    }

    @Test fun `refine STALE (tuổi quá ngưỡng) bị từ chối — KHÔNG snap, KHÔNG ghi ground-truth`() {
        TurnDistanceInterpolator.anchor(1000, "k", 1000L)
        TurnDistanceInterpolator.refine(250, 1200L)                       // mẫu tươi → snap baseline về 250, ghi GT
        assertEquals(250, TurnDistanceInterpolator.anchorMeters())
        assertEquals(250, TurnDistanceInterpolator.lastRefined())
        // GMaps chạy nền → mẫu cũ (tuổi 5000ms > 2500) → reject hẳn: baseline + ground-truth GIỮ NGUYÊN
        TurnDistanceInterpolator.refine(999, 6000L, readAgeMs = 5000L)
        assertEquals(250, TurnDistanceInterpolator.anchorMeters(), "stale refine không được snap baseline (anchor rác)")
        assertEquals(250, TurnDistanceInterpolator.lastRefined(), "stale refine không được ghi ground-truth")
        assertEquals(1200L, TurnDistanceInterpolator.lastRefinedAt(), "mốc ground-truth giữ nguyên khi bị reject")
    }

    @Test fun `refine biên tuổi = ngưỡng chấp nhận, vượt 1ms từ chối`() {
        TurnDistanceInterpolator.anchor(1000, "k", 1000L)
        TurnDistanceInterpolator.refine(500, 2000L, readAgeMs = TurnDistanceInterpolator.SCREEN_READ_STALE_MS)  // ==2500 → OK
        assertEquals(500, TurnDistanceInterpolator.anchorMeters())
        TurnDistanceInterpolator.refine(123, 3000L, readAgeMs = TurnDistanceInterpolator.SCREEN_READ_STALE_MS + 1)  // reject
        assertEquals(500, TurnDistanceInterpolator.anchorMeters())
        assertEquals(500, TurnDistanceInterpolator.lastRefined())
    }
}
