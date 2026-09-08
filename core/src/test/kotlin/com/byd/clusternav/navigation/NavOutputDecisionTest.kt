package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit test THUẦN cho [NavOutputDecision] (T4, spec `b3-full-nav-capture` §R3/§R4/§R5 + §R-BI) — khoá logic
 * "cho trạng thái từng kênh → bắn kênh nào / khi nào clear / khung là của ai" + hai hàm map (lane-arrow code,
 * camera icon code) off-car.
 *
 * Hai nhóm kịch bản:
 *  • **CÙNG package** (6 test đầu, viết lại từ bộ T4 cũ — trước B-I chữ ký là `decide(Boolean,Boolean,Boolean)`):
 *    hành vi phải Y HỆT trước B-I. Ý định khoá không đổi (arrow-only · lane+arrow · camera-only · cả ba ·
 *    all-stale→clear · keep-alive), chỉ thêm chiều package vì chữ ký đổi.
 *  • **LỆCH package** (§R-BI): kênh tươi khác app bị DROP, khung không bao giờ lai.
 */
class NavOutputDecisionTest {

    private val waze = "com.chisadin.wazemod"
    private val vietmap = NavApps.VIETMAP_LIVE

    private fun fresh(pkg: String) = NavChannelState(fresh = true, pkg = pkg)
    private fun stale(pkg: String? = null) = NavChannelState(fresh = false, pkg = pkg)

    // ── CÙNG PACKAGE: phải Y HỆT hành vi trước B-I (không hồi quy R3a "cứ bắn") ───────────────────────────

    @Test fun `arrow-only — chỉ bắn arrow, không clear`() {
        val p = NavOutputDecision.decide(fresh(waze), NavChannelState.ABSENT, NavChannelState.ABSENT)
        assertTrue(p.pushArrow)
        assertFalse(p.pushLane)
        assertFalse(p.pushCamera)
        assertFalse(p.clear)
        assertTrue(p.anyPush)
        assertEquals(waze, p.framePkg)
    }

    @Test fun `lane+arrow — bắn CẢ arrow lẫn lane (độc lập, cứ bắn), không clear`() {
        val p = NavOutputDecision.decide(fresh(waze), fresh(waze), NavChannelState.ABSENT)
        assertTrue(p.pushArrow)
        assertTrue(p.pushLane)
        assertFalse(p.pushCamera)
        assertFalse(p.clear)
        assertFalse(p.anyDropped)
    }

    @Test fun `camera — chỉ bắn camera (không cần arrow tươi, không gate chéo)`() {
        val p = NavOutputDecision.decide(NavChannelState.ABSENT, NavChannelState.ABSENT, fresh(vietmap))
        assertFalse(p.pushArrow)
        assertFalse(p.pushLane)
        assertTrue(p.pushCamera)
        assertFalse(p.clear)
        // KHOÁ: B-I KHÔNG siết thành "phải có mũi tên mới bắn" — ca VietMap phạt nguội không mũi tên vẫn chạy
        // như cũ, và khung mang danh tính của chính kênh camera.
        assertEquals(vietmap, p.framePkg)
    }

    @Test fun `cả ba kênh tươi — bắn hết (cứ bắn đủ field)`() {
        val p = NavOutputDecision.decide(fresh(waze), fresh(waze), fresh(waze))
        assertTrue(p.pushArrow && p.pushLane && p.pushCamera)
        assertFalse(p.clear)
        assertEquals(waze, p.framePkg)
    }

    @Test fun `all-stale → clear (không kênh nào tươi ⇒ nhả frame)`() {
        val p = NavOutputDecision.decide(stale(waze), stale(waze), stale(vietmap))
        assertFalse(p.anyPush)
        assertTrue(p.clear)
        assertNull(p.framePkg, "hết tươi ⇒ không còn khung nào")
    }

    @Test fun `keep-alive — còn tươi thì mỗi tick vẫn ra plan bắn (re-assert), hết tươi thì clear`() {
        // Nguồn còn tươi qua nhiều tick liên tiếp ⇒ decide luôn trả "bắn" ⇒ owner re-assert nội dung mỗi nhịp.
        repeat(4) {
            val p = NavOutputDecision.decide(fresh(waze), fresh(waze), NavChannelState.ABSENT)
            assertTrue(p.anyPush, "còn tươi ⇒ vẫn bắn (keep-alive re-assert)")
            assertFalse(p.clear)
        }
        // Nguồn hết tươi ⇒ chuyển sang clear đúng một lần (owner tự chống clear lặp bằng hasFrame).
        val hetTuoi = NavOutputDecision.decide(stale(waze), stale(waze), NavChannelState.ABSENT)
        assertFalse(hetTuoi.anyPush)
        assertTrue(hetTuoi.clear)
    }

    // ── §R-BI: LỆCH PACKAGE ───────────────────────────────────────────────────────────────────────────────

    /** KHOÁ chính bug B-I: làn của một ngã ba KHÁC nằm ngay cạnh mũi tên đang báo. */
    @Test fun `mũi tên Waze + làn VietMap ⇒ DROP làn`() {
        val p = NavOutputDecision.decide(fresh(waze), fresh(vietmap), NavChannelState.ABSENT)
        assertTrue(p.pushArrow)
        assertFalse(p.pushLane)
        assertTrue(p.droppedLane)
        assertEquals(waze, p.framePkg)
        assertFalse(p.clear)
    }

    /** KHOÁ: badge camera của app khác không được bám vào khung của app đang dẫn. */
    @Test fun `mũi tên Waze + camera VietMap ⇒ DROP camera`() {
        val p = NavOutputDecision.decide(fresh(waze), NavChannelState.ABSENT, fresh(vietmap))
        assertTrue(p.pushArrow)
        assertFalse(p.pushCamera)
        assertTrue(p.droppedCamera)
        assertEquals(waze, p.framePkg)
    }

    /**
     * KHOÁ: thứ tự rơi bậc ARROW > LANE > CAMERA là TẤT ĐỊNH — KHÔNG phải "kênh mới nhất thắng". Với luật
     * "mới nhất thắng", khi `CaptureForegroundSource` dao động (đã xảy ra thật — B3.7 overlay WazeMod đè
     * VietMap) danh tính sẽ lật mỗi tick ⇒ cụm nhấp nháy trên xe đang chạy.
     */
    @Test fun `không mũi tên - làn X + camera Y ⇒ danh tính = làn, camera DROP`() {
        val p = NavOutputDecision.decide(NavChannelState.ABSENT, fresh(waze), fresh(vietmap))
        assertEquals(waze, p.framePkg)
        assertTrue(p.pushLane)
        assertFalse(p.pushCamera)
        assertTrue(p.droppedCamera)
    }

    /** KHOÁ: chỉ kênh CÒN TƯƠI mới dựng được danh tính — mũi tên hết hạn không giữ khoá vĩnh viễn. */
    @Test fun `mũi tên STALE + làn tươi khác pkg ⇒ làn LÊN`() {
        val p = NavOutputDecision.decide(stale(waze), fresh(vietmap), NavChannelState.ABSENT)
        assertEquals(vietmap, p.framePkg)
        assertTrue(p.pushLane)
        assertFalse(p.pushArrow)
        assertFalse(p.droppedArrow, "kênh đã hết tươi thì không tính là bị DROP")
        assertFalse(p.clear)
    }

    /** KHOÁ: khung đang hiện KHÔNG bị nhả/nhấp nháy chỉ vì xuất hiện một kênh lạ. */
    @Test fun `DROP không kéo theo clear`() {
        val p = NavOutputDecision.decide(fresh(waze), fresh(vietmap), fresh(vietmap))
        assertTrue(p.anyDropped)
        assertFalse(p.clear, "DROP ≠ nhả khung")
    }

    /** KHOÁ (degrade-safe): publish dị thường (pkg rỗng) không làm câm cả khung — rơi xuống kênh sau. */
    @Test fun `pkg rỗng ở một kênh tươi ⇒ kênh đó không dựng được danh tính, rơi xuống kênh sau`() {
        val p = NavOutputDecision.decide(NavChannelState(fresh = true, pkg = ""), fresh(vietmap), NavChannelState.ABSENT)
        assertEquals(vietmap, p.framePkg)
        assertTrue(p.pushLane)
        assertFalse(p.pushArrow)
        assertTrue(p.droppedArrow, "kênh tươi vô chủ vẫn là kênh bị bỏ — phải hiện ra trong log DROP")
    }

    /** KHOÁ bất biến TOÀN PHẦN: mọi kênh được bắn đều thuộc đúng framePkg; không có framePkg ⇒ không bắn gì. */
    @Test fun `framePkg == null ⇔ không bắn kênh nào`() {
        val voChu = NavOutputDecision.decide(
            NavChannelState(fresh = true, pkg = null),
            NavChannelState(fresh = true, pkg = ""),
            NavChannelState(fresh = true, pkg = null),
        )
        assertNull(voChu.framePkg)
        assertFalse(voChu.anyPush)
        assertFalse(voChu.clear, "vẫn có kênh tươi ⇒ chưa được nhả khung")

        // Chiều ngược lại: có framePkg ⇒ chắc chắn có kênh được bắn.
        val coChu = NavOutputDecision.decide(NavChannelState.ABSENT, NavChannelState.ABSENT, fresh(vietmap))
        assertEquals(vietmap, coChu.framePkg)
        assertTrue(coChu.anyPush)
    }

    // ── stateAt: chấm tươi + mang chủ ─────────────────────────────────────────────────────────────────────

    @Test fun `stateAt — null hoặc mốc 0 ⇒ ABSENT, biên tươi tính CẢ mốc bằng đúng ngưỡng`() {
        val nothing: NavSignalSample? = null
        assertEquals(NavChannelState.ABSENT, nothing.stateAt(now = 1_000L, staleMs = 6_000L))

        val s = object : NavSignalSample {
            override val pkg = waze
            override val atMs = 1_000L
        }
        assertTrue(s.stateAt(now = 7_000L, staleMs = 6_000L).fresh, "đúng ngưỡng vẫn còn tươi")
        val het = s.stateAt(now = 7_001L, staleMs = 6_000L)
        assertFalse(het.fresh)
        assertEquals(waze, het.pkg, "hết tươi vẫn mang chủ để log đọc được là của ai")

        val chuaPublish = object : NavSignalSample {
            override val pkg = waze
            override val atMs = 0L
        }
        assertFalse(chuaPublish.stateAt(now = 0L, staleMs = 6_000L).fresh, "mốc 0 = chưa publish")
    }

    // ── map giá trị (không đổi) ───────────────────────────────────────────────────────────────────────────

    @Test fun `laneArrowCode — dùng AMAP NEW_ICON của mũi tên chính, rỗng = 0`() {
        assertEquals(0, NavOutputDecision.laneArrowCode(emptyList()))
        assertEquals(2, NavOutputDecision.laneArrowCode(listOf(Maneuver.TURN_LEFT)))     // AMAP 2
        assertEquals(3, NavOutputDecision.laneArrowCode(listOf(Maneuver.TURN_RIGHT)))    // AMAP 3
        assertEquals(9, NavOutputDecision.laneArrowCode(listOf(Maneuver.STRAIGHT)))      // AMAP 9
        // Làn nhiều hướng (thẳng + phải): lấy mũi tên CHÍNH (đầu) = thẳng → 9.
        assertEquals(9, NavOutputDecision.laneArrowCode(listOf(Maneuver.STRAIGHT, Maneuver.TURN_RIGHT)))
    }

    @Test fun `laneArrowCode khớp Maneuver_toAmapIcon của phần tử đầu (single source)`() {
        for (m in Maneuver.values()) {
            assertEquals(m.toAmapIcon(), NavOutputDecision.laneArrowCode(listOf(m)), "$m")
        }
    }

    @Test fun `cameraIconCode — 1 khi có camera, 0 khi không`() {
        assertEquals(1, NavOutputDecision.cameraIconCode(hasCamera = true))
        assertEquals(0, NavOutputDecision.cameraIconCode(hasCamera = false))
    }
}
