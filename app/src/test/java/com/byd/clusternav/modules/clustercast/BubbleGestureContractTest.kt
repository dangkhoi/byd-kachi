package com.byd.clusternav.modules.clustercast

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Single-icon bubble WIRING contract (R5 / #7). The pure decisions are unit-tested in
 * `BubbleGesturePlannerTest` (:core); the Android glue (foreground detection, coordinator dispatch,
 * startActivity, the long-press overlay) needs a device/Robolectric, so — like
 * [FloatingBubbleFirstLaunchContractTest] — this locks the wiring by reading the source. On-car
 * visual checks (drag, long-press feel, arrow visibility) are noted in the spec §Verification.
 */
class BubbleGestureContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun source(relative: String): String =
        app("src/main/java/com/byd/clusternav/modules/clustercast/$relative").toFile().readText()

    private val dispatcher by lazy { source("BubbleActionDispatcher.kt") }
    private val service by lazy { source("FloatingBubbleService.kt") }
    private val overlay by lazy { source("BubbleSubmenuOverlay.kt") }
    private val renderer by lazy { source("BubbleRenderer.kt") }

    /** Body of a top-level member from its signature to the next 4-space `fun`/`companion`. */
    private fun body(text: String, signature: String): String {
        val start = text.indexOf(signature)
        require(start >= 0) { "missing '$signature'" }
        val after = start + signature.length
        val next = listOf("\n    fun ", "\n    private fun ", "\n    override fun ", "\n    companion object")
            .mapNotNull { text.indexOf(it, after).takeIf { i -> i >= 0 } }
            .minOrNull() ?: text.length
        return text.substring(start, next)
    }

    // ─── TAP = toggle full, driven by the pure planner + Wave-1 launcher guard ─

    @Test
    fun `dispatcher tap toggles full via the pure planner`() {
        val onTap = body(dispatcher, "fun onTap()")
        assertTrue(onTap.contains("BubbleGesturePlanner.tapOutcome(coordinator.state)"), "tap reads the planner outcome")
        assertTrue(onTap.contains("SimpleCastIntent.Stop()"), "RETURN → slot-less Stop (return to gauges)")
        assertTrue(onTap.contains("detectForeground(coordinator) ?: return"), "CAST_FULL bails on the launcher guard")
        assertTrue(onTap.contains("SimpleCastIntent.CastFull("), "CAST_FULL casts the foreground full")
    }

    @Test
    fun `dispatcher preserves the Wave-1 launcher exclusion guard`() {
        val detect = body(dispatcher, "private fun detectForeground(")
        assertTrue(detect.contains("homePackages()"), "unions the full CATEGORY_HOME resolver list")
        assertTrue(detect.contains("AppMover.isLauncher(foreground)"), "also rejects dudu/launcher by name")
        assertTrue(detect.contains("Không cast màn hình chính"), "posts the won't-cast-home toast")
        val home = body(dispatcher, "private fun homePackages()")
        assertTrue(home.contains("CATEGORY_HOME"), "queries every home package, not just default")
    }

    // ─── LONG-PRESS submenu → CastSlot LEFT/RIGHT + open config ──────────────

    @Test
    fun `dispatcher routes a submenu choice through the pure slot mapping`() {
        val onSubmenu = body(dispatcher, "fun onSubmenuAction(")
        assertTrue(onSubmenu.contains("BubbleGesturePlanner.slotFor(action)"), "uses the pure slot mapping")
        assertTrue(onSubmenu.contains("onCastSlot(slot)"), "Trái/Phải → cast a slot")
        assertTrue(onSubmenu.contains("openConfig()"), "Cấu hình → open the app")
    }

    @Test
    fun `dispatcher casts the foreground into the chosen slot with the guard`() {
        val onCastSlot = body(dispatcher, "fun onCastSlot(")
        assertTrue(onCastSlot.contains("detectForeground(coordinator) ?: return"), "slot cast also honours the guard")
        assertTrue(onCastSlot.contains("SimpleCastIntent.CastSlot(foreground, side)"), "casts into LEFT/RIGHT")
    }

    @Test
    fun `Cau hinh builds a MainActivity intent with NEW_TASK and starts it`() {
        val openConfig = body(dispatcher, "fun openConfig()")
        assertTrue(openConfig.contains("MainActivity::class.java"), "opens MainActivity")
        assertTrue(openConfig.contains("FLAG_ACTIVITY_NEW_TASK"), "with NEW_TASK (Service has no task)")
        assertTrue(openConfig.contains("startActivity("), "actually starts it")
    }

    // ─── Service wires the three gestures ────────────────────────────────────

    @Test
    fun `service wires tap and long-press callbacks to the gesture handler`() {
        assertTrue(service.contains("onTap = { onBubbleTap() }"), "tap callback wired")
        assertTrue(service.contains("onLongPress = { onBubbleLongPress() }"), "long-press callback wired")
        val tap = body(service, "private fun onBubbleTap()")
        assertTrue(tap.contains("submitTapAction(\"bubble-tap\")") && tap.contains("actionDispatcher.onTap()"),
            "tap is token-gated and calls the dispatcher")
    }

    @Test
    fun `service long-press shows the submenu and routes choices correctly`() {
        val lp = body(service, "private fun onBubbleLongPress()")
        assertTrue(lp.contains("BubbleSubmenuOverlay("), "creates the submenu overlay")
        assertTrue(lp.contains("overlay.show"), "shows it")
        assertTrue(lp.contains("actionDispatcher.onSubmenuAction(action)"), "dispatches the chosen action")
        assertTrue(lp.contains("submitTapAction(\"submenu-"), "cast choices run token-gated on the executor")
        assertTrue(lp.contains("isShowing()") && lp.contains("dismiss()"), "a second long-press toggles it closed")
    }

    @Test
    fun `service dismisses the submenu on destroy`() {
        val destroy = body(service, "override fun onDestroy()")
        assertTrue(destroy.contains("submenu?.dismiss()"), "overlay must be removed on destroy")
    }

    @Test
    fun `service still builds the single-icon bubble`() {
        assertTrue(service.contains("renderer.buildBubble()"), "single-icon builder, not the 3-zone layout")
        assertTrue(!service.contains("buildBubbleLayout"), "the 3-zone layout builder is gone")
        assertTrue(!service.contains("onZoneTap"), "the zone-tap entry point is gone")
    }

    // ─── Submenu overlay: 3 rows from the planner, ≥48dp, dismiss on tap/outside ─

    @Test
    fun `overlay renders the planner rows at a 48dp touch target`() {
        assertTrue(overlay.contains("BubbleGesturePlanner.submenuItems()"), "rows come from the planner")
        assertTrue(overlay.contains("dp(ROW_HEIGHT_DP)"), "each row uses the compact row height (owner: ~80% of 48dp)")
    }

    @Test
    fun `overlay dismisses on a choice and on an outside tap`() {
        assertTrue(overlay.contains("setOnClickListener { dismiss() }"), "outside (scrim) tap dismisses")
        val show = body(overlay, "fun show(")
        assertTrue(show.contains("dismiss()") && show.contains("onAction(item.action)"), "choosing a row dismisses then acts")
    }

    // ─── Renderer: one transparent icon, no background/border ────────────────

    @Test
    fun `renderer builds one transparent nav-arrow icon with no background`() {
        val build = body(renderer, "fun buildBubble()")
        assertTrue(build.contains("ImageView("), "one ImageView")
        assertTrue(build.contains("R.drawable.ic_bubble_nav"), "shows the nav arrow vector")
        assertTrue(build.contains("background = null"), "no background / border / fill (R5)")
        assertTrue(build.contains("isLongClickable = true"), "long-clickable for the submenu gesture")
    }

    // ─── Submenu redesign (#7 follow-up): beside the bubble, compact, icons + separators ─

    @Test
    fun `overlay anchors beside the bubble with icons and separators`() {
        assertTrue(overlay.contains("BubbleSubmenuAnchor.offset("), "card is placed beside the bubble (not centred)")
        assertTrue(
            overlay.contains("R.drawable.ic_menu_left") &&
                overlay.contains("R.drawable.ic_menu_right") &&
                overlay.contains("R.drawable.ic_menu_config"),
            "each row has a leading SVG glyph",
        )
        assertTrue(overlay.contains("showDividers"), "rows are divided by hairline dividers")
        assertTrue(!overlay.contains("0xF21565C0"), "the heavy brand-blue card background is gone")
    }

    @Test
    fun `service passes the bubble rect so the submenu anchors to it`() {
        val lp = body(service, "private fun onBubbleLongPress()")
        assertTrue(
            lp.contains("overlay.show(") && lp.contains("bubbleWidthPx()") && lp.contains("bubbleHeightPx()"),
            "the bubble position + size are passed to the submenu",
        )
    }
}
