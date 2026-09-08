package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit test THUẦN cho HudKeepAlivePolicy (J1 + B1 Lỗ 1, handoff 2026-08-15) — khoá logic
 * "khi nào re-assert / khi nào clear giữ HUD sống":
 *  - chưa có frame → không bao giờ re-assert / clear;
 *  - sau real push → chỉ re-assert khi đã ≥ interval VÀ còn trong TRẦN TUỔI (maxAge=180s);
 *  - keep-alive re-assert nhịp lại lastWrite NHƯNG KHÔNG kéo dài trần tuổi (Lỗ 1: nhịp tim không tự nuôi mình);
 *  - vượt trần tuổi → NGỪNG re-assert + shouldClear=true → owner nhả frame cũ;
 *  - real push mở LẠI cửa sổ trần tuổi;
 *  - HỒI QUY từ số đo thật: real push rồi im 108s (kẹt trong hầm) VẪN re-assert, KHÔNG clear;
 *  - onCleared() dừng tất cả.
 */
class HudKeepAlivePolicyTest {

    @Test fun `chưa có frame — không bao giờ re-assert hay clear`() {
        val p = HudKeepAlivePolicy(400L)
        assertFalse(p.shouldReassert(0L))
        assertFalse(p.shouldReassert(10_000L))
        assertFalse(p.shouldClear(0L))
        assertFalse(p.shouldClear(10_000L))
    }

    @Test fun `sau real push — chỉ re-assert khi đã đủ interval (và còn trong trần tuổi)`() {
        val p = HudKeepAlivePolicy(400L)          // maxAge mặc định 180_000
        p.onFrameWritten(1_000L)                  // real push (realPush=true mặc định)
        assertFalse(p.shouldReassert(1_000L))     // cùng thời điểm
        assertFalse(p.shouldReassert(1_399L))     // vừa dưới ngưỡng interval
        assertTrue(p.shouldReassert(1_400L))      // đúng interval, còn trong trần tuổi
        assertTrue(p.shouldReassert(5_000L))      // 4s: quá interval nhưng CÒN trong trần tuổi 180s
        assertFalse(p.shouldClear(5_000L))        // 4s ≪ 180s → chưa clear
    }

    @Test fun `real push làm tươi CẢ nhịp lẫn trần tuổi, re-assert CHỈ làm tươi nhịp`() {
        val p = HudKeepAlivePolicy(400L)          // maxAge mặc định 180_000
        p.onFrameWritten(1_000L)                  // real push: lastWrite=1000, lastRealPush=1000
        assertTrue(p.shouldReassert(1_400L))
        // Keep-alive re-assert (realPush=false): dời NHỊP (lastWrite) sang 1_400 nhưng KHÔNG chạm trần tuổi.
        p.onFrameWritten(1_400L, realPush = false)
        assertFalse(p.shouldReassert(1_500L))     // 100ms kể từ nhịp cuối → chưa tới interval
        assertTrue(p.shouldReassert(1_800L))      // 400ms kể từ nhịp cuối → re-assert lại
    }

    @Test fun `Lỗ 1 — re-assert KHÔNG kéo dài trần tuổi, quá 180s thì ngừng re-assert và phải clear`() {
        val p = HudKeepAlivePolicy(intervalMs = 400L)   // maxAge mặc định 180_000
        p.onFrameWritten(1_000L)                        // real push mở phiên
        // Chuỗi nhịp keep-alive re-assert (realPush=false): lastWrite tiến dần, lastRealPush ĐỨNG YÊN ở 1_000.
        p.onFrameWritten(1_400L, realPush = false)
        p.onFrameWritten(50_000L, realPush = false)
        p.onFrameWritten(150_000L, realPush = false)
        // Ngay sau nhịp re-assert cuối, vẫn còn trong trần tuổi (≈150s < 180s kể từ real push):
        assertTrue(p.shouldReassert(150_400L), "còn trong trần tuổi → vẫn re-assert")
        assertFalse(p.shouldClear(150_400L))
        // Nguồn IM tới mốc > 180s kể từ REAL push (1_000L). Nhịp tim đã giữ lastWrite=150_000 NHƯNG trần tuổi
        // vẫn tính từ real push ⇒ PHẢI ngừng re-assert và CLEAR. Đây chính là Lỗ 1: nhịp tim không tự nuôi mình.
        val pastCeiling = 181_001L                      // 181_001 - 1_000 = 180_001 > 180_000
        assertFalse(p.shouldReassert(pastCeiling), "quá trần tuổi → KHÔNG re-assert (dù nhịp tim vẫn tick)")
        assertTrue(p.shouldClear(pastCeiling), "quá trần tuổi → phải clear frame cũ")
    }

    @Test fun `real push mở LẠI cửa sổ trần tuổi`() {
        val p = HudKeepAlivePolicy(intervalMs = 400L)   // maxAge mặc định 180_000
        p.onFrameWritten(1_000L)                        // real push #1
        p.onFrameWritten(180_000L, realPush = false)    // chỉ nhịp, gần hết trần tuổi
        assertTrue(p.shouldReassert(180_900L), "≈179s kể từ real push #1, vẫn trong trần → re-assert")
        // NGUỒN đẩy frame mới → trần tuổi tính LẠI từ đây.
        p.onFrameWritten(181_000L)                      // real push #2 (realPush=true)
        val later = 181_000L + 108_000L                 // 289_000L — 108s sau real push #2
        assertTrue(p.shouldReassert(later), "real push mở lại cửa sổ → re-assert tiếp")
        assertFalse(p.shouldClear(later), "real push mở lại cửa sổ → KHÔNG clear")
        // Không có real push nữa và vượt 180s kể từ push #2 → lại clear.
        val pastAgain = 181_000L + 180_001L             // 361_001L
        assertFalse(p.shouldReassert(pastAgain))
        assertTrue(p.shouldClear(pastAgain))
    }

    @Test fun `HỒI QUY (số đo thật) — real push rồi im 108s bò trong hầm VẪN re-assert, KHÔNG clear`() {
        // docs/diagnostics/nav-logs/commute-2026-08-14-pm.csv: gap noti dài nhất KHI ĐANG LĂN BÁNH = 108s
        // ('Hầm Nguyễn Hữu Cảnh', speed 2,8 m/s — GMaps chỉ đẩy mỗi bậc 100m). Trần 180s PHẢI ôm được khoảng
        // này; siết 15–20s sẽ trắng cụm giữa hầm ~30 lần/chuyến. 108s < 180s → GIỮ frame (re-assert), KHÔNG clear.
        val p = HudKeepAlivePolicy(intervalMs = 400L)   // maxAge mặc định 180_000
        p.onFrameWritten(10_000L)                       // real push
        val after108s = 10_000L + 108_000L              // 118_000L
        assertTrue(p.shouldReassert(after108s), "im 108s vẫn trong trần 180s → PHẢI re-assert (không để cụm trắng)")
        assertFalse(p.shouldClear(after108s), "im 108s CHƯA tới trần 180s → KHÔNG clear")
    }

    @Test fun `onCleared dừng cả re-assert lẫn clear`() {
        val p = HudKeepAlivePolicy(400L)
        p.onFrameWritten(1_000L)
        p.onCleared()
        assertFalse(p.shouldReassert(10_000L), "đã clear → không re-assert")
        assertFalse(p.shouldClear(200_000L), "đã clear → không còn frame để clear (tránh clear lặp)")
    }

    @Test fun `interval phải dương và maxAge phải lớn hơn interval`() {
        assertThrows(IllegalArgumentException::class.java) { HudKeepAlivePolicy(0L) }
        assertThrows(IllegalArgumentException::class.java) { HudKeepAlivePolicy(-1L) }
        assertThrows(IllegalArgumentException::class.java) { HudKeepAlivePolicy(intervalMs = 400L, maxAgeMs = 400L) }
        assertThrows(IllegalArgumentException::class.java) { HudKeepAlivePolicy(intervalMs = 400L, maxAgeMs = 100L) }
    }

    @Test fun `default interval = 250ms (hạ từ 400 để thu hẹp cửa sổ blank OEM — TASK 2)`() {
        assertEquals(250L, HudKeepAlivePolicy().intervalMs())
        assertEquals(HudKeepAlivePolicy.DEFAULT_INTERVAL_MS, HudKeepAlivePolicy().intervalMs())
    }

    @Test fun `default maxAge = 180_000ms (180s, khớp ClusterBroadcaster STALE_MS)`() {
        assertEquals(180_000L, HudKeepAlivePolicy().maxAgeMs())
        assertEquals(HudKeepAlivePolicy.DEFAULT_MAX_AGE_MS, HudKeepAlivePolicy().maxAgeMs())
    }
}
