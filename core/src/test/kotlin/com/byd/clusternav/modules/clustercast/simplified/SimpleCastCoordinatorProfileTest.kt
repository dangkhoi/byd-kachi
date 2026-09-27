package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Nửa sau của [SimpleCastCoordinatorTest] — hồ sơ theo tỉ lệ (R4/R5/R6) · đổi tỉ lệ chia đôi sống · thứ tự autostart (T1) ·
 * lỗi tự nhả ([SOÁT 1.69 · P2]). Tách THUẦN theo CHỦ ĐỀ (539 dòng → trần 500, L6-debt 2026-09-27); thân bài giữ nguyên byte,
 * khung dựng ở [SimpleCastCoordinatorHarness].
 */
class SimpleCastCoordinatorProfileTest : SimpleCastCoordinatorHarness() {

    // ─── Profiles + per-slot resize (R4/R5/R6) ────────────────────────────────

    @Test
    fun `CastProfile of maps side and leftPercent`() {
        assertEquals("L50", CastProfile.of(ClusterSlotSide.LEFT, 50).key)
        assertEquals("L30", CastProfile.of(ClusterSlotSide.LEFT, 30).key)
        assertEquals("L70", CastProfile.of(ClusterSlotSide.LEFT, 70).key)
        assertEquals("R50", CastProfile.of(ClusterSlotSide.RIGHT, 50).key)
        assertEquals("R30", CastProfile.of(ClusterSlotSide.RIGHT, 30).key)
        assertEquals("R70", CastProfile.of(ClusterSlotSide.RIGHT, 70).key)
        // Out-of-set leftPercent (never one of the 9 ratios) falls back to the default (50).
        assertEquals(CastProfile.of(ClusterSlotSide.LEFT, 50), CastProfile.of(ClusterSlotSide.LEFT, 55))
        assertEquals(CastProfile.of(ClusterSlotSide.RIGHT, 50), CastProfile.of(ClusterSlotSide.RIGHT, 999))
    }

    @Test
    fun `prefs round-trip per profile uses distinct non-colliding keys`() {
        val pkg = "com.test.app"
        val full = DisplayConfig("1920x720", "0,0,0,0", "160", CastBounds(0, 0, 1920, 720))
        val l30 = DisplayConfig("1920x720", "0,0,0,0", "200", CastBounds(0, 0, 576, 720))
        val r70 = DisplayConfig("1920x720", "0,0,0,0", "240", CastBounds(576, 0, 1920, 720))

        prefs.saveDisplayConfig(pkg, CastProfile.FULL, full)
        prefs.saveDisplayConfig(pkg, CastProfile.of(ClusterSlotSide.LEFT, 30), l30)
        prefs.saveDisplayConfig(pkg, CastProfile.of(ClusterSlotSide.RIGHT, 70), r70)

        // Each profile reads back its own value — no cross-contamination.
        assertEquals(full, prefs.displayConfigFor(pkg, CastProfile.FULL))
        assertEquals(l30, prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.LEFT, 30)))
        assertEquals(r70, prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.RIGHT, 70)))
        // No-arg overload is the FULL profile (backward compat).
        assertEquals(full, prefs.displayConfigFor(pkg))
        // Untouched profiles remain null.
        assertNull(prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.LEFT, 50)))
        assertNull(prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.RIGHT, 30)))
    }

    @Test
    fun `resizeActiveSlot persists to the matching profile on shell success`() {
        prefs.setSplitRatioLeftPercent(30)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()

        coordinator.resizeActiveSlot(ClusterSlotSide.LEFT, 0, 0, 500, 720)
        // LEFT × ratio 30 → profile L30
        awaitTrue { prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 30))?.bounds == CastBounds(0, 0, 500, 720) }

        assertEquals(
            CastBounds(0, 0, 500, 720),
            prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 30))?.bounds,
        )
        // Persisted to L30 only — the FULL profile is untouched.
        assertNull(prefs.displayConfigFor("com.test.left", CastProfile.FULL))
    }

    @Test
    fun `resizeActiveSlot does not persist when am task resize fails`() {
        prefs.setSplitRatioLeftPercent(70)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitState<SimpleCastState.CastingSplit>()

        // Force the per-slot resize shell command to fail — nothing must be persisted (R6).
        shell.failCommands.add("am task resize")
        coordinator.resizeActiveSlot(ClusterSlotSide.RIGHT, 600, 0, 1920, 720)
        Thread.sleep(300) // allow the serial executor to run the (failing) resize

        // RIGHT × ratio 70 → profile R70; must stay null because the shell rejected the resize.
        assertNull(prefs.displayConfigFor("com.test.right", CastProfile.of(ClusterSlotSide.RIGHT, 70)))
    }

    // ─── Live split-ratio change (Feature 2) ──────────────────────────────────

    @Test
    fun `applySplitRatioLive re-resizes both slots in place when casting split`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.test.right" }

        // Mark history rather than clearing it: FakeShell reconstructs the display-1 stack from the
        // recorded `am start --display 1` commands, so clearing would make both apps "disappear" and
        // the resize would find no task. The ratio-30 bounds below are unique to applySplitRatioLive
        // (the initial cast used the default ratio 50 → boundary 960), so scoping to new commands is
        // enough to prove the live re-resize happened.
        val mark = shell.history.size
        coordinator.applySplitRatioLive(30)

        // (a) new ratio persisted for the next cast.
        awaitTrue { prefs.splitRatioLeftPercent() == 30 }
        // (b) BOTH slots re-resized to the ratio-30 split. FakeShell returns no `wm size`, so the
        // coordinator falls back to 1920×720 → boundary = 1920·30/100 = 576.
        awaitTrue { shell.history.drop(mark).any { it.contains("am task resize") && it.endsWith(" 0 0 576 720") } }
        awaitTrue { shell.history.drop(mark).any { it.contains("am task resize") && it.endsWith(" 576 0 1920 720") } }
        // In-place resize: nothing is returned to the main display (no return+recast).
        assertTrue(
            shell.history.drop(mark).none { it.contains("--display 0") },
            "live ratio change must not return apps to main; commands=${shell.history.drop(mark)}",
        )
        // Per-ratio profiles updated on success (R6).
        awaitTrue { prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 30))?.bounds == CastBounds(0, 0, 576, 720) }
        awaitTrue { prefs.displayConfigFor("com.test.right", CastProfile.of(ClusterSlotSide.RIGHT, 30))?.bounds == CastBounds(576, 0, 1920, 720) }
    }

    @Test
    fun `applySplitRatioLive only persists the ratio when not casting split`() {
        // Off (never cast). Must persist the ratio but issue NO slot resize.
        coordinator.applySplitRatioLive(70)
        awaitTrue { prefs.splitRatioLeftPercent() == 70 }
        Thread.sleep(100)
        assertTrue(
            shell.history.none { it.contains("am task resize") },
            "no split → no slot resize; commands=${shell.history}",
        )
    }

    @Test
    fun `applySplitRatioLive normalizes an out-of-set percent to the default 50`() {
        // Public entry-point hardening: a percent outside the 9 supported buckets (10..90 step 10)
        // must NOT persist verbatim — it would corrupt the next split cast's fitToCluster geometry,
        // drive a degenerate `am task resize`, and file bounds under a mismatched profile key.
        // It clamps to the R3 default (50), matching CastProfile.of/normalizePercent everywhere else.
        coordinator.applySplitRatioLive(55) // 55 is not one of SPLIT_PERCENTS
        awaitTrue { prefs.splitRatioLeftPercent() == 50 }
        coordinator.applySplitRatioLive(0) // degenerate — would make a zero-width slot
        awaitTrue { prefs.splitRatioLeftPercent() == 50 }
        coordinator.applySplitRatioLive(1000) // out of range high
        awaitTrue { prefs.splitRatioLeftPercent() == 50 }
        // A valid bucket still round-trips unchanged.
        coordinator.applySplitRatioLive(30)
        awaitTrue { prefs.splitRatioLeftPercent() == 30 }
    }

    // ─── T1 autostart sequencing proof (R1) ──────────────────────────────────

    @Test
    fun `autostart split sequences LEFT then RIGHT into CastingSplit with no Error`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        // Record every transition from Idle onward so we can prove no Error slips in.
        val states = CopyOnWriteArrayList<SimpleCastState>()
        coordinator.addStateListener { states.add(it) }

        // The service's fixed sequencing: cast LEFT, then cast RIGHT ONLY after the coordinator
        // reports CastingSplit with a landed left slot (never a blind delay).
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }

        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue {
            val cur = coordinator.state
            cur is SimpleCastState.CastingSplit &&
                cur.left?.pkg == "com.test.left" && cur.right?.pkg == "com.test.right"
        }

        val s = coordinator.state as SimpleCastState.CastingSplit
        assertEquals("com.test.left", s.left?.pkg)
        assertEquals("com.test.right", s.right?.pkg)
        // R1: no Error state may appear during a correctly-sequenced split autostart.
        assertTrue(
            states.none { it is SimpleCastState.Error },
            "sequenced split autostart must not enter Error; saw: $states",
        )
    }

    @Test
    fun `dispatching the same slot twice is rejected SLOT_OCCUPIED without returning the other slot`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        // Establish a full split: LEFT then RIGHT (RIGHT only after LEFT landed).
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.test.right" }

        // Precondition: both slots occupied.
        val before = coordinator.state as SimpleCastState.CastingSplit
        assertEquals("com.test.left", before.left?.pkg)
        assertEquals("com.test.right", before.right?.pkg)

        // Mark the shell history, then dispatch the SAME (already-occupied) LEFT slot again —
        // exactly what the old two-driver autostart did.
        val historyMark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))

        // The coordinator rejects the duplicate with SLOT_OCCUPIED (transient Error).
        awaitState<SimpleCastState.Error>()
        val err = coordinator.state as SimpleCastState.Error
        assertTrue(
            err.message.contains(CastRejectReason.SLOT_OCCUPIED.name),
            "expected SLOT_OCCUPIED rejection, got: ${err.message}",
        )

        // The rejection must not tear down the OTHER slot: no app is returned to the main display
        // (returnToMain always issues `am start --display 0 ...`). The reject path issues no shell
        // at all, so the right (and left) app placement on the cluster is left untouched.
        val newCommands = shell.history.drop(historyMark)
        assertTrue(
            newCommands.none { it.contains("--display 0") },
            "duplicate-slot reject must not return any app to the main display; commands=$newCommands",
        )
    }

    /**
     * KHOÁ [SOÁT 1.69 · P2]: **KHÔNG Error nào được kẹt vĩnh viễn** — mỗi lỗi tự hẹn giờ nhả về Idle/Off.
     *
     * Bản vá 1.69 chuyển lượt hồi lỗi từ worker cast sang `TIMEOUT_SCHEDULER` (đúng: `Thread.sleep(3000)` trên
     * worker duy nhất nuốt lệnh chiếu kế tiếp). Nhưng khối ấy nay chạy **song song** với worker, nên
     * `if (state is Error) setState(Idle)` hai bước có thể đè lên một trạng thái MỚI HƠN ⇒ đổi sang CAS trên
     * ĐÚNG thực thể lỗi đã hẹn. Hệ quả: lỗi nào **không** tự hẹn giờ thì không còn ăn ké lượt hẹn của lỗi khác
     * được nữa — `closeProjection` hỏng là đúng ca đó, và trước bản vá nó sẽ nằm lại `Error` mãi mãi.
     *
     * Chuyển `setError("Projection close failed")` về `setState(SimpleCastState.Error(...))` trần thì ca này ĐỎ.
     */
    @Test
    fun `loi dong projection cung tu nha ve Off, khong ket o Error`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        shell.shouldFail = true
        coordinator.closeProjection()
        awaitState<SimpleCastState.Error>()
        // 3 s hồi lỗi + biên cho lượt lập lịch; hết hạn mà vẫn Error ⇒ đúng cái kẹt vĩnh viễn phải chặn.
        awaitTrue(timeoutMs = 6_000) { coordinator.state !is SimpleCastState.Error }
    }
}
