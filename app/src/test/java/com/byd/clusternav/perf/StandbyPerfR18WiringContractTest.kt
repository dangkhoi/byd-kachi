package com.byd.clusternav.perf

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 · R18 — dây nối của các tối ưu standby/chạy thường (luật thuần ở `:core` `StandbyPerfR18Test`). Mỗi hàm mới phải có
 * call site thật (CLAUDE.md §8) và mỗi đường cũ giữ nguyên khi màn bật.
 */
class StandbyPerfR18WiringContractTest {

    private fun code(p: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$p")

    @Test
    fun `nut noi doc man mot lan moi nhip, truyen cho repin, hen nhip theo man`() {
        val run = SourceRoots.body(code("modules/clustercast/FloatingBubbleService.kt"), "override fun run()")
        assertTrue(run.contains("ScreenInteractive.read(applicationContext)"))
        assertTrue(run.contains("repinEscapedCastApps(interactive)"))
        assertTrue(run.contains("StandbyCadence.bubbleRefreshMs(interactive)"))
        assertFalse(code("modules/clustercast/FloatingBubbleService.kt").contains("REFRESH_INTERVAL_MS"), "một nguồn nhịp duy nhất")
    }

    @Test
    fun `do o hoi ban doc dung chung TRUOC khi chay lenh, ghi lai ban tu chay`() {
        val sweep = SourceRoots.body(code("launcher/SlotLiveProbe.kt"), "private fun sweep()")
        val fresh = sweep.indexOf("StackListSnapshot.fresh(")
        val shell = sweep.indexOf("shell(\"am stack list\")")
        assertTrue(fresh in 0 until shell, "dùng lại trước, tự chạy sau")
        // 2.98 · R13 — ĐỔI GHIM có lý do: ghi kèm mốc LÚC BẮT ĐẦU đọc (nhịp kế không dùng lại chính bản của nhịp trước — PARK7-FORCESTOP-CLEAR).
        assertTrue(sweep.contains("StackListSnapshot.record(it, atMs = startedAt)"))
    }

    @Test
    fun `HomeGuard man tat thi khong doc HOME, nhip theo man`() {
        val guard = code("launcher/HomeGuard.kt")
        val tick = SourceRoots.body(guard, "private fun tick(")
        val off = tick.indexOf("if (interactive == false)")
        assertTrue(off >= 0 && off < tick.indexOf("DefaultHome.currentPackage(app)"), "cổng màn tắt đứng TRƯỚC lượt đọc HOME")
        assertTrue(SourceRoots.body(guard, "private fun loop(").contains("nextDelayMs(SystemClock.elapsedRealtime() - tripStartMs, prevInteractive)"))
    }

    @Test
    fun `boot bo am start khi man chinh da resumed - bo dem noi o vong doi`() {
        val auto = code("KachiAutostart.kt")
        assertTrue(auto.contains("BootHomeUp.needsStart(HomeResumed.count())"))
        assertTrue(auto.indexOf("BootHomeUp.needsStart") < auto.indexOf("seam(\"am start -n \$launchComp\")"))
        val home = code("launcher/KachiHomeActivity.kt")
        assertTrue(SourceRoots.body(home, "override fun onResume()").contains("HomeResumed.up()"))
        assertTrue(home.contains("handler.removeCallbacks(tick); HomeResumed.down()"), "onPause trả bộ đếm")
    }

    @Test
    fun `nhip giu HUD huy khi het frame, trong khoa, va van re-arm o lan day that`() {
        val hud = code("NavigationHudOwner.kt")
        val tick = SourceRoots.body(hud, "private fun keepAliveTick()")
        assertTrue(tick.contains("synchronized(keepAliveLock)") && tick.contains("!keepAlive.hasFrame()"))
        assertTrue(hud.contains("if (realPush) armKeepAlive()"), "đường dựng lại nhịp (Lỗ 3) còn nguyên")
    }

    @Test
    fun `keep-alive phim thoai chi ghi moc ngu khi doi`() {
        val src = code("VoiceKeyKeepAliveService.kt")
        assertTrue(src.contains("if (A11yBindJournal.shouldPersistDeepSleep(prev, now)) Prefs.setLastDeepSleepMs(app, now)"))
    }

    /**
     * Soát 2.96 Pass 1 [P2] (phát hiện phụ R18): nhịp 2 s gọi `refreshBubbleState()` và lượt nào cũng `wakeBubble()` ⇒ bộ hẹn mờ
     * 2,5 s bị gỡ trước khi nổ ⇒ bong bóng không bao giờ mờ. Khoá: sáng CHỈ khi trạng thái ĐỔI so với lần vẽ trước; nhịp
     * `refresh` không gọi `wakeBubble()` trực tiếp; đường chạm tay (`onWake`) giữ nguyên.
     */
    @Test
    fun `nut noi chi sang khi trang thai DOI - nhip 2 s khong giu no sang mai`() {
        val src = code("modules/clustercast/FloatingBubbleService.kt")
        val paint = SourceRoots.body(src, "private fun refreshBubbleState()")
        assertTrue(paint.contains("if (state != paintedState)"), "cổng đổi trạng thái trước wakeBubble")
        assertTrue(paint.indexOf("if (state != paintedState)") < paint.indexOf("wakeBubble()"), "wakeBubble nằm TRONG cổng")
        assertTrue(paint.contains("paintedState = state"), "ghi lại trạng thái đã vẽ")
        assertFalse(SourceRoots.body(src, "override fun run()").contains("wakeBubble()"), "nhịp định kỳ không tự sáng bong bóng")
        assertTrue(src.contains("onWake = { wakeBubble() }"), "chạm tay vẫn sáng")
    }
}
