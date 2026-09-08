package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * R6 (#1): the coordinator's return/stop path must leave a non-freeform/non-split state AND push a
 * fullscreen (windowing-mode 1) reset for every NORMAL app it returns — the regression guard for the
 * owner-observed "cast VietMap back and forth → stuck as a window". Uses the shared cast fakes.
 */
class CastReturnFullscreenTest {

    private lateinit var shell: FakeShell
    private lateinit var prefs: FakePrefs
    private lateinit var coordinator: SimpleCastCoordinator

    @BeforeEach
    fun setup() {
        shell = FakeShell()
        prefs = FakePrefs()
        coordinator = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}),
            DisplayConfigurator(shell),
            AppMover(shell, sleepMs = {}),
            prefs, shell, displayId = 1,
        )
    }

    @Test
    fun `stop from full returns the app fullscreen and leaves a non-freeform state`() {
        coordinator.openProjection(); awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        shell.history.clear()
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()

        // (a) state is not stuck in a freeform/split cast state.
        assertEquals(SimpleCastState.Idle, coordinator.state)

        // (b) the return issued a display-0 fullscreen (windowing-mode 1) reset via the proven recipe.
        val returns = shell.history.filter { it.startsWith("am start --display 0") && it.contains("com.test.app") }
        assertTrue(returns.isNotEmpty(), "no display-0 return issued; history=${shell.history}")
        assertTrue(returns.all { it.contains("--windowingMode 1") }, "return not fullscreen; $returns")
        assertTrue(
            returns.any { it.contains("-f 0x20000000") && it.contains("android.intent.category.LAUNCHER") },
            "return did not use the proven SINGLE_TOP + LAUNCHER recipe; $returns",
        )
    }

    @Test
    fun `stop-all from split returns both apps fullscreen and leaves no lingering split state`() {
        coordinator.openProjection(); awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.test.right" }
        shell.history.clear()

        coordinator.dispatch(SimpleCastIntent.Stop(slot = null)) // return everything (bubble tap)
        awaitState<SimpleCastState.Idle>()

        assertEquals(SimpleCastState.Idle, coordinator.state)
        // Both slot apps must receive a fullscreen reset — neither may be left as a freeform window.
        assertTrue(
            shell.history.any { it.contains("--windowingMode 1") && it.contains("com.test.left") },
            "left app not returned fullscreen; history=${shell.history}",
        )
        assertTrue(
            shell.history.any { it.contains("--windowingMode 1") && it.contains("com.test.right") },
            "right app not returned fullscreen; history=${shell.history}",
        )
    }

    // ─── Helpers (mirror SimpleCastCoordinatorTest) ───────────────────────────
    private inline fun <reified T : SimpleCastState> awaitState(timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (coordinator.state !is T && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(coordinator.state is T, "Expected ${T::class.simpleName} but got ${coordinator.state}")
    }

    private fun awaitTrue(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(condition(), "condition not met within ${timeoutMs}ms")
    }
}
