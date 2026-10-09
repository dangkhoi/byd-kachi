package com.byd.clusternav.system

import com.byd.clusternav.launcher.BootHomeUp
import com.byd.clusternav.launcher.HomeGuardPolicy
import com.byd.clusternav.modules.navaccess.A11yBindJournal
import com.byd.clusternav.navigation.HudKeepAlivePolicy
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 · R18 — khoá các bài học hiệu năng standby/chạy thường (spec `kachi-296-plan.html` §3 R18,
 * `docs/diagnostics/perf-inventory-2026-10-07.md`).
 */
class StandbyPerfR18Test {

    @AfterEach fun reset() = StackListSnapshot.clear()

    /** [ĐO log xe 07/10] standby 8 h: `am stack list` mỗi 4 s khi SoC thức. Màn bật / không đọc được = nhịp CŨ, không đổi. */
    @Test
    fun `nhip nut noi va repin chi thua khi man DOC DUOC la tat`() {
        assertEquals(2_000L, StandbyCadence.bubbleRefreshMs(true))
        assertEquals(2_000L, StandbyCadence.bubbleRefreshMs(null))
        assertEquals(10_000L, StandbyCadence.bubbleRefreshMs(false))
        assertEquals(4_000L, StandbyCadence.repinMinIntervalMs(true))
        assertEquals(4_000L, StandbyCadence.repinMinIntervalMs(null))
        assertEquals(30_000L, StandbyCadence.repinMinIntervalMs(false))
        // Thưa chứ không tắt: lưới repin vẫn còn nếu "tắt màn khi đang lái" làm isInteractive=false ([CHƯA BIẾT]) — ≤ 1 phút.
        assertTrue(StandbyCadence.repinMinIntervalMs(false) <= 60_000L)
    }

    /** [ĐO log xe] 15 + ~4 `am stack list`/phút trùng: đo ô dùng lại bản đọc repin mới hơn nhịp đo trước của chính nó. */
    @Test
    fun `ban doc am stack list dung lai khi du moi va chup sau nhip do truoc`() {
        val out = "Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0\n  taskId=1: com.x/.Home"
        assertNull(StackListSnapshot.fresh(notBeforeMs = Long.MIN_VALUE, nowMs = 1_000L), "chưa có bản nào")
        StackListSnapshot.record(out, atMs = 1_000L)
        assertEquals(out, StackListSnapshot.fresh(notBeforeMs = 500L, nowMs = 3_000L))
        assertNull(StackListSnapshot.fresh(notBeforeMs = 1_000L, nowMs = 3_000L), "không dùng lại bản cũ hơn/ bằng nhịp đo trước")
        assertNull(StackListSnapshot.fresh(notBeforeMs = 0L, nowMs = 1_000L + StackListSnapshot.REUSE_MAX_AGE_MS + 1), "quá tuổi")
        assertNull(StackListSnapshot.fresh(notBeforeMs = 0L, nowMs = 900L), "đồng hồ lùi")
    }

    @Test
    fun `ban doc khong doc duoc thi khong ghi - ben doc tu chay lenh`() {
        StackListSnapshot.record("", atMs = 1_000L)
        StackListSnapshot.record(null, atMs = 1_000L)
        StackListSnapshot.record("error: connection reset", atMs = 1_000L)
        assertNull(StackListSnapshot.fresh(notBeforeMs = Long.MIN_VALUE, nowMs = 1_500L))
    }

    @Test
    fun `repin ghi ban doc cua no ngay sau lenh doc`() {
        val ops = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinatorOps.kt")
        val read = ops.indexOf("shell.execute(\"am stack list\").let { if (it.success) it.stdout else null }")
        val rec = ops.indexOf("StackListSnapshot.record(stackOut, atMs = readAt)")
        assertTrue(read >= 0 && rec > read, "lượt dò repin phải ghi bản đọc cho nhịp đo ô")
        // 2.98 · R13: mốc ghi = lúc BẮT ĐẦU đọc (lấy TRƯỚC lệnh) — bản chụp không mới hơn lúc lệnh bắt đầu.
        assertTrue(ops.indexOf("val readAt = com.byd.clusternav.system.StackListSnapshot.nowMs()") in 0 until read, "mốc lấy trước lệnh đọc")
        val coord = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt")
        assertTrue(coord.contains("StandbyCadence.repinMinIntervalMs(interactive)"), "khoảng dò repin theo màn")
    }

    /** [ĐO máy ảo 07/10] `kachi-home-guard` thức 0,6 lần/giây cả khi màn tắt. */
    @Test
    fun `HomeGuard thua lai khi man tat, nhip cu khi bat`() {
        assertEquals(HomeGuardPolicy.SCREEN_OFF_TICK_MS, HomeGuardPolicy.nextDelayMs(0L, false))
        assertEquals(HomeGuardPolicy.FAST_TICK_MS, HomeGuardPolicy.nextDelayMs(0L, true))
        assertEquals(HomeGuardPolicy.FAST_TICK_MS, HomeGuardPolicy.nextDelayMs(0L, null))
        assertEquals(HomeGuardPolicy.SLOW_TICK_MS, HomeGuardPolicy.nextDelayMs(HomeGuardPolicy.FAST_WINDOW_MS, true))
        // Bật lại màn ⇒ thấy trong ≤ 10 s, vẫn kịp ca giành HOME đã đo (~15–17 s sau khi Kachi lên).
        assertTrue(HomeGuardPolicy.SCREEN_OFF_TICK_MS < 15_000L)
    }

    /** [ĐO log xe 20:48:44] BOOT_COMPLETED +24 s chạy `am start` màn chính đã resumed (~0,5 s kênh shell). */
    @Test
    fun `am start man chinh chi khi chua co man nao resumed`() {
        assertTrue(BootHomeUp.needsStart(0))
        assertFalse(BootHomeUp.needsStart(1))
        assertFalse(BootHomeUp.needsStart(2))
        // Bộ đếm không bao giờ âm (onPause lạc) ⇒ không bao giờ chặn nhầm lượt dựng màn.
        val base = com.byd.clusternav.launcher.HomeResumed.count()
        com.byd.clusternav.launcher.HomeResumed.up(); assertEquals(base + 1, com.byd.clusternav.launcher.HomeResumed.count())
        com.byd.clusternav.launcher.HomeResumed.down(); com.byd.clusternav.launcher.HomeResumed.down()
        assertEquals(0, com.byd.clusternav.launcher.HomeResumed.count())
    }

    /** [ĐO mã] nhịp 30 s ghi SharedPreferences mỗi lần dù mốc ngủ không đổi. */
    @Test
    fun `moc ngu chi ghi khi doi`() {
        assertTrue(A11yBindJournal.shouldPersistDeepSleep(-1L, 0L), "chưa có mốc")
        assertFalse(A11yBindJournal.shouldPersistDeepSleep(5_000L, 5_000L))
        assertFalse(A11yBindJournal.shouldPersistDeepSleep(5_000L, 5_001L), "nhiễu đọc đồng hồ")
        assertTrue(A11yBindJournal.shouldPersistDeepSleep(5_000L, 5_000L + A11yBindJournal.PERSIST_STEP_MS), "SoC vừa ngủ")
        assertTrue(A11yBindJournal.shouldPersistDeepSleep(9_000_000L, 0L), "khởi động lại ⇒ mốc về nhỏ")
        // Ngưỡng ngủ dài (giờ) không bị ảnh hưởng bởi bước ghi 1 s.
        assertTrue(A11yBindJournal.wokeFromLongSleep(0L, 2 * 3_600_000L, 2 * 3_600_000L))
    }

    /** [ĐO máy ảo 07/10] `hud-keepalive` thức 4 lần/giây kể cả khi không có frame nào (79 % số lần thức lúc màn tắt). */
    @Test
    fun `nhip giu HUD bao het viec khi khong co frame`() {
        val p = HudKeepAlivePolicy()
        assertFalse(p.hasFrame())
        p.onFrameWritten(1_000L, realPush = true)
        assertTrue(p.hasFrame())
        p.onCleared()
        assertFalse(p.hasFrame())
    }
}
