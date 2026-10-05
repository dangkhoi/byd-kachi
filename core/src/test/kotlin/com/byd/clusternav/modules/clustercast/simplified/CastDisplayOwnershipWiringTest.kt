package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.CastDisplayFixtures2026_09_15
import com.byd.clusternav.system.DisplayOwner
import com.byd.clusternav.system.DisplayOwnershipRegistry
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.89 · B4 DISPLAY-OWNER-DYNAMIC — khoá DÂY NỐI coordinator → cổng sở hữu display của launcher
 * ([SimpleCastCoordinator.onCastDisplay], `:app` nối tới `WindowCommandDispatcher.setCastDisplay`).
 *
 * Cảnh dựng từ fixture NGUYÊN VĂN xe 15/09 ([CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT]): display 1 = ô
 * `kachi-slot-0` của CHÍNH launcher, cụm thật = display 2. Khoá:
 *  1. mở chiếu dò được cụm ⇒ registry: 2 = CAST, còn ô 1 (launcher đã đăng ký) vẫn LAUNCHER;
 *  2. đóng chiếu (công tắc TẮT `closeProjection` · ý định `Close`) ⇒ id cụm bị xoá ⇒ display 2 không còn chủ (từ chối);
 *  3. dò hụt ⇒ báo `null` (không bao giờ báo seed `1`);
 *  4. bên nhận ném ⇒ đường chiếu không gãy (CLAUDE.md §6).
 */
class CastDisplayOwnershipWiringTest {

    private val self = CastDisplayFixtures2026_09_15.LAUNCHER_PKG

    private fun shell() = FakeShell().apply {
        clusterDisplayId = 2
        selfPackage = self
        clusterDetectOut = CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT
    }

    private fun coordinator(sh: FakeShell, onCastDisplay: (Int?) -> Unit) = SimpleCastCoordinator(
        ProjectionManager(sh, sleepMs = {}),
        DisplayConfigurator(sh),
        AppMover(sh, sleepMs = {}),
        FakePrefs(), sh,
        displayId = 1,              // seed sai — đúng cảnh boot log "Cluster display seed = 1"
        selfPackage = self,
        detectSleepMs = {},
        onCastDisplay = onCastDisplay,
    )

    @Test
    fun `open binds the detected cluster id 2 as CAST while slot VD 1 stays LAUNCHER, close clears it`() {
        val own = DisplayOwnershipRegistry()
        own.registerVirtualDisplay(1)   // ô kachi-slot-0 — màn phụ đầu tiên sau khởi động nguội
        val c = coordinator(shell(), own::setCastDisplay)

        c.openProjection()
        awaitState<SimpleCastState.Idle>(c)
        assertEquals(2, own.castDisplay(), "id cụm dò live phải vào cổng sở hữu")
        assertEquals(DisplayOwner.CAST, own.ownerOf(2))
        assertEquals(DisplayOwner.LAUNCHER, own.ownerOf(1), "ô của launcher không bao giờ thành cụm")

        c.closeProjection()
        awaitState<SimpleCastState.Off>(c)
        assertNull(own.castDisplay(), "đóng chiếu ⇒ xoá id cụm")
        assertNull(own.ownerOf(2), "display 2 không còn chủ ⇒ fail-safe từ chối")
        assertEquals(DisplayOwner.LAUNCHER, own.ownerOf(1))
    }

    @Test
    fun `Close intent (closeProjectionSync) also clears the cast id`() {
        val seen = CopyOnWriteArrayList<Int?>()
        val c = coordinator(shell()) { seen += it }
        c.openProjection()
        awaitState<SimpleCastState.Idle>(c)
        c.dispatch(SimpleCastIntent.Close)
        awaitState<SimpleCastState.Off>(c)
        assertTrue(seen.contains(2), "phải từng báo id 2: $seen")
        assertNull(seen.last(), "lượt cuối sau Close phải là null: $seen")
    }

    @Test
    fun `detection miss publishes null, never the seed 1`() {
        val seen = CopyOnWriteArrayList<Int?>()
        val sh = shell().apply { clusterDetectOut = CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY }
        val c = coordinator(sh) { seen += it }
        c.openProjection()
        awaitState<SimpleCastState.Error>(c)
        assertTrue(seen.isNotEmpty(), "mỗi lượt dò phải báo kết quả")
        assertTrue(seen.all { it == null }, "dò hụt chỉ được báo null (seed 1 = ô của launcher): $seen")
    }

    @Test
    fun `a throwing ownership sink never breaks the cast open`() {
        val c = coordinator(shell()) { throw IllegalStateException("sink hỏng") }
        c.openProjection()
        awaitState<SimpleCastState.Idle>(c)
    }

    private inline fun <reified T : SimpleCastState> awaitState(c: SimpleCastCoordinator, timeoutMs: Long = 6000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (c.state !is T && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(c.state is T, "Expected ${T::class.simpleName} but got ${c.state}")
    }
}
