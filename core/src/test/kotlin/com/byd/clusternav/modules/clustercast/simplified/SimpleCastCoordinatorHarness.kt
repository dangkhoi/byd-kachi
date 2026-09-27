package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach

/**
 * Khung dựng chung của [SimpleCastCoordinatorTest] và [SimpleCastCoordinatorProfileTest]: fake shell/prefs + coordinator
 * (`sleepMs = {}`), hai phép chờ `awaitState`/`awaitTrue`. Tách THUẦN khi tệp gốc vượt trần 500 dòng (539, L6-debt 2026-09-27);
 * thân giữ nguyên byte, chỉ `private` → `internal` để hai lớp con dùng. JUnit 5 chạy `@BeforeEach` của lớp cha trước mỗi bài.
 */
abstract class SimpleCastCoordinatorHarness {
    internal lateinit var shell: FakeShell
    internal lateinit var prefs: FakePrefs
    internal lateinit var coordinator: SimpleCastCoordinator

    @BeforeEach
    fun setup() {
        shell = FakeShell()
        prefs = FakePrefs()
        val projection = ProjectionManager(shell, sleepMs = {}) // no actual sleep in tests
        val configurator = DisplayConfigurator(shell)
        val mover = AppMover(shell, sleepMs = {})
        coordinator = SimpleCastCoordinator(
            projection, configurator, mover, prefs, shell, displayId = 1,
            castTimeoutMs = 15_000L,
            stopTimeoutMs = 5_000L,
        )
    }


    internal inline fun <reified T : SimpleCastState> awaitState(timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (coordinator.state !is T && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(coordinator.state is T, "Expected ${T::class.simpleName} but got ${coordinator.state}")
    }

    internal fun awaitTrue(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(condition(), "condition not met within ${timeoutMs}ms")
    }
}
