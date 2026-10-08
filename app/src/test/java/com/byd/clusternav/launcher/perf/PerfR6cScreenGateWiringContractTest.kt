package com.byd.clusternav.launcher.perf

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R6-C (owner 09/10: *"chỉ đọc khi màn sáng, đọc suốt đêm tốn pin xe"*) — dây nối phía `:app`; luật ở `CarStatusRepositoryTest` +
 * `ScreenLitRuleTest`. Container tiêm cổng màn sáng HAI nguồn (`isInteractive` + trạng thái display 0 — soát Pass 4: "tắt màn" BYD có
 * thể không lật interactive), màn chính móc SCREEN_ON + đổi display 0 ⇒ `wake()`, gỡ khi dừng. Thử ĐỎ: bỏ `awake = …` hoặc bỏ móc.
 */
class PerfR6cScreenGateWiringContractTest {

    @Test
    fun `cong man sang hai nguon va danh thuc khi man doi`() {
        val c = SourceRoots.codeOf("src/main/java/com/byd/clusternav/AppContainer.kt")
        assertTrue("CarStatusRepository(carDataAdapter, carScope, awake = { screenAwake() })" in c)
        assertTrue("screenAwake = { com.byd.clusternav.system.ScreenLit.read(app) }" in c)
        val lit = SourceRoots.codeOf("src/main/java/com/byd/clusternav/system/ScreenLit.kt")
        assertTrue("ScreenLitRule.lit(ScreenInteractive.read(ctx), displayOn(ctx))" in lit)
        assertTrue("it != Display.STATE_OFF" in lit)
        assertTrue("IntentFilter(Intent.ACTION_SCREEN_ON)" in lit && "registerDisplayListener(listener" in lit)
        assertTrue("unregisterReceiver(receiver)" in lit && "unregisterDisplayListener(listener)" in lit, "gỡ cả hai")
        assertFalse("RECEIVER_EXPORTED" in lit || "RECEIVER_NOT_EXPORTED" in lit, "SCREEN_ON là protected-broadcast ⇒ không cờ")
        val w = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt")
        assertTrue("com.byd.clusternav.system.ScreenLit.watch(it) { container.carStatusRepository.wake() }" in w)
        assertTrue("unwatch?.invoke()" in w, "gỡ móc khi dừng")
    }
}
