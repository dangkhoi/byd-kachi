package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedSignSourceLifecycleTest {
    private val listener = SourceRoots.text("src/main/java/com/byd/clusternav/NavNotificationListener.kt")
    private val owner = SourceRoots.text("src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt")
    private val main = SourceRoots.text("src/main/java/com/byd/clusternav/MainActivity.kt")
    private val prefs = SourceRoots.text("src/main/java/com/byd/clusternav/Prefs.kt")
    private val bridge = SourceRoots.text("src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt")

    @Test
    fun `listener clears VietMap before bridge teardown`() {
        val cases = listOf(
            "override fun onListenerDisconnected()" to "onProviderDisconnected(SpeedLimitSource.VIETMAP)",
            "override fun onDestroy()" to "onSourceStopped(SpeedLimitSource.VIETMAP)",
        )
        cases.forEach { (signature, vietmapEvent) ->
            val body = functionBody(listener, signature)
            val vietmapClear = body.indexOf(vietmapEvent)
            val bridgeStop = body.indexOf("bridge.stop")
            val listenerRemoval = body.indexOf("bridge.removeListener")
            assertTrue(vietmapClear in 0 until bridgeStop, "$signature VietMap clear must precede bridge stop")
            assertTrue(bridgeStop in 0 until listenerRemoval, "$signature bridge publishes clear before listener removal")
        }
    }

    /**
     * HỒI QUY 2026-08-22 — nhánh **Waze HLP đã gỡ hẳn**, không được tái sinh mà không có bằng chứng mới.
     *
     * Vì sao gỡ: `WazeHudSource` poll `logcat -s WazeHudLink` qua dadb **mỗi 900ms (~4000 lệnh shell/giờ)**,
     * chạy VÔ ĐIỀU KIỆN — không theo lựa chọn nguồn, thậm chí TRƯỚC cổng `Prefs.enabled` — để nhận về **0
     * dòng**: WazeMod chỉ phát tag đó khi có peer HUD BT/BLE (đo 08-22, Waze ĐANG dẫn, máy không HUD).
     *
     * Muốn bật lại thì phải kèm phép đo chứng minh nó phát dữ liệu thật trên xe, VÀ phải gate theo lựa chọn
     * nguồn + cổng master — không lặp lại kiểu poll vô điều kiện.
     */
    @Test
    fun `nhanh Waze HLP da go — khong con poll logcat vo dieu kien`() {
        listOf("WazeHudSource", "startWazeHudSource", "stopWazeHudSource", "WazeHudLink")
            .forEach { token ->
                val live = listener.lineSequence()
                    .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
                    .any { it.contains(token) }
                assertFalse(live, "\"$token\" phải chỉ còn trong comment giải thích, không còn code sống")
            }
    }

    @Test
    fun `VietMap fresh null is zero while unavailable is provider disconnect`() {
        val pusher = functionBody(listener, "private val speedLimitPusher")
        assertTrue(pusher.contains("snapshot.speedLimitKph ?: 0"))
        assertTrue(pusher.contains("snapshot.speedUpdatedAtElapsedMs"))
        assertTrue(pusher.contains("onProviderDisconnected(SpeedLimitSource.VIETMAP)"))
    }

    @Test
    fun `VietMap publishes clear before host stop and drops late callbacks`() {
        val stop = functionBody(bridge, "fun stop(owner: VietMapWidgetOwner)")
        assertTrue(stop.indexOf("clearRuntimeValues()") < stop.indexOf("publishSnapshot()"))
        assertTrue(stop.indexOf("publishSnapshot()") < stop.indexOf("listening = false"))
        assertTrue(stop.indexOf("listening = false") < stop.indexOf("host.stopListening()"))
        val callback = functionBody(bridge, "private fun onHostViewUpdated")
        assertTrue(callback.contains("if (!listening) return@onMain"))
        assertTrue(callback.contains("if (views[slot] !== view) return@onMain"))
    }

    @Test
    fun `runtime ports are ClusterSpeedBadge and HalSpeedSign with no old ADAS encoding`() {
        assertTrue(owner.contains("ClusterSpeedBadgePort(badgeOverlay)"))
        assertTrue(owner.contains("HalSpeedSignPort(appContext)"))
        assertFalse(owner.contains("distanceMeters"))
        assertFalse(owner.contains("writeSpeedLimit"))
        assertFalse(owner.contains("clearSpeedLimit"))
    }

    @Test
    fun `BUG-1 exactly one SpeedBadgeOverlay is constructed and shared with the debug force-show`() {
        // The real cluster port and the debug force-show must share ONE overlay window — so there is exactly
        // one `SpeedBadgeOverlay(` construction in the owner and the old separate debugBadgeOverlay is gone.
        val constructions = Regex("SpeedBadgeOverlay\\(").findAll(owner).count()
        assertEquals(1, constructions, "expected exactly one SpeedBadgeOverlay( construction in the owner")
        assertTrue(owner.contains("badgeOverlay"), "shared overlay field must exist")
        assertFalse(owner.contains("debugBadgeOverlay"), "the separate debug overlay must be removed (BUG-1)")
    }

    @Test
    fun `existing controls only feed master source and output events with typed Prefs mapping`() {
        assertTrue(main.contains("speedSign.onMasterEnabled(enabled)"))
        assertTrue(main.contains("speedSign.onSourceSelected(Prefs.speedLimitSource"))
        // Owner 2026-08-11: cluster-lane output (incl. its speed-sign CLUSTER output) follows the
        // master switch now — cb_lane removed — so it is enabled with the master (constant `true` on
        // enable), not a separate checkbox listener's `enabled`.
        assertTrue(main.contains("speedSign.onOutputEnabled(SpeedSignOutput.CLUSTER, true)"))
        // R1 (#6, docs/specs/cast-nav-ux-release-v104.html): the nav→HUD output toggle is hidden and
        // force-disabled once at init — there is no user-driven HUD-enable path anymore, so the
        // control feeds a constant `false` (not the old `enabled` from a checkbox listener).
        assertTrue(main.contains("speedSign.onOutputEnabled(SpeedSignOutput.HUD, false)"))
        assertTrue(prefs.contains("fun speedLimitSource(ctx: Context): SpeedLimitSource"))
        // 08-22: nguồn tốc độ KHÔNG còn là lựa chọn — chỉ widget VietMap. Selector + prefs key đã gỡ.
        assertFalse(prefs.contains("fun setSpeedSource"), "setter nguồn tốc độ phải đã gỡ")
        assertFalse(prefs.contains("K_SPEED_SOURCE"), "khoá prefs nguồn tốc độ phải đã gỡ")
        assertFalse(main.contains("spinner_speed_source"), "spinner nguồn tốc độ phải đã gỡ khỏi UI")
        assertFalse(main.contains("SignCandidateGateway"))
        assertFalse(main.contains("vehicleTest"))
    }

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        var depth = 0
        var opened = false
        for (index in start until source.length) {
            when (source[index]) {
                '{' -> { depth++; opened = true }
                '}' -> if (opened && --depth == 0) return source.substring(start, index + 1)
            }
        }
        error("unterminated $signature")
    }
}
