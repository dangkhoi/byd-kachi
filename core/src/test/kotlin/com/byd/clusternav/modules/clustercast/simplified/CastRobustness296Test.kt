package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.96 · R5/R6/R7 — chiếu cụm chắc hơn sau nổ máy (dựng từ log xe 07/10 20:48 + 21:04, `logs/carlog-1007-*`, ngoài repo) ═══
 *
 *  • R5 [ĐO 20:48:55] nửa PHẢI chờ sau nửa TRÁI bị nhịp watchdog repin đẩy khỏi hàng ⇒ VietMap không bao giờ lên cụm.
 *  • R6 [ĐO 4/4 lượt mở gửi theme 31] màn ảo GIỮ id 4 ⇒ lượt chờ id mới đốt cả 12 lượt dò (20:48 chạm hạn 25 s).
 *  • R7 [ĐO 20:48:47.959 + dumpsys 20:55] lượt mở bị ngắt SAU 16/35, TRƯỚC ClusterBlack ⇒ cụm chiếu mà không có nền.
 */
class CastRobustness296Test : SimpleCastCoordinatorHarness() {

    // ─── R5 ───────────────────────────────────────────────────────────────────

    /**
     * Tái hiện 20:48:55: bộ nghe tự-chiếu (như `BubbleAutostart.split`) bắn nửa PHẢI NGAY trong `setState(CastingSplit)` của nửa
     * TRÁI (nửa TRÁI còn chạy `applySessionPin`) ⇒ nửa PHẢI chờ trong hàng; đúng lúc đó nhịp watchdog (`FloatingBubbleService` ~2 s)
     * gọi repin. Thử ĐỎ: `repinEscapedCastApps` dùng lại `executor.submit` ⇒ nửa PHẢI bị bỏ, trạng thái kẹt `right = null`.
     */
    @Test
    fun `R5 - nua PHAI dang cho khi watchdog repin toi - nua PHAI van duoc chieu`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        val fired = AtomicBoolean(false)
        coordinator.addStateListener { s ->
            if (s is SimpleCastState.CastingSplit && s.left != null && s.right == null && fired.compareAndSet(false, true)) {
                coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
                coordinator.repinEscapedCastApps()   // nhịp watchdog trùng lúc
            }
        }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue(5000) { (coordinator.state as? SimpleCastState.CastingSplit)?.right != null }
        assertTrue(fired.get())
        assertTrue(shell.history.any { it.startsWith("am start") && it.contains("com.test.right") }, "${shell.history}")
    }

    // ─── R6 ───────────────────────────────────────────────────────────────────

    private val self = "com.byd.launcher"

    private fun scripted(vararg ids: Int): Pair<SimpleCastShell, () -> Int> {
        val fake = FakeShell()
        var i = 0
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                val id = ids[minOf(i++, ids.lastIndex)]
                return ShellResult(0, if (id < 1) "  Display 0:\n" else fake.detectOutFor(listOf(id)), "")
            }
        }
        return shell to { i }
    }

    /** Thử ĐỎ: bỏ nhánh `excludeGrace` ⇒ dò đủ 12 lượt. */
    @Test
    fun `R6 - sau theme chi thay id cu - nhan sau EXCLUDE_GRACE_POLLS luot, khong dot het vong`() {
        val (shell, calls) = scripted(4)
        val sleeps = mutableListOf<Long>()
        val id = ClusterDisplayResolver.awaitAndPersist(shell, self, sleepMs = { sleeps += it }, exclude = 4) {}
        assertEquals(4, id)
        assertEquals(ClusterDisplayResolver.EXCLUDE_GRACE_POLLS, calls())
        assertEquals(ClusterDisplayResolver.EXCLUDE_GRACE_POLLS - 1, sleeps.size)
        assertTrue(ClusterDisplayResolver.EXCLUDE_GRACE_POLLS < ClusterDisplayResolver.AWAIT_ATTEMPTS)
    }

    /** Hành vi 2602 (màn ảo dựng lại 4 → 9) giữ nguyên: id mới xuất hiện trong khoảng ân hạn ⇒ dùng id mới. */
    @Test
    fun `R6 - id moi xuat hien trong khoang an han - dung id moi`() {
        val (shell, _) = scripted(4, 4, 9)
        var persisted: Int? = null
        assertEquals(9, ClusterDisplayResolver.awaitAndPersist(shell, self, sleepMs = {}, exclude = 4) { persisted = it })
        assertEquals(9, persisted)
    }

    /** Lượt hụt (màn cũ đã gỡ, màn mới chưa có) không tính vào ân hạn — chờ tiếp tới id mới. */
    @Test
    fun `R6 - luot hut khong tinh vao an han`() {
        val (shell, _) = scripted(4, -1, -1, 4, -1, 9)
        assertEquals(9, ClusterDisplayResolver.awaitAndPersist(shell, self, sleepMs = {}, exclude = 4) {})
    }

    /** Coordinator thật trên Seal mức B, màn ảo giữ id 4 sau 31 (ca 07/10): số lượt dò sau 35 = ân hạn, rồi ClusterBlack lên 4. */
    @Test
    fun `R6 - lượt mo gui 31, man ao giu id 4 - ClusterBlack dat sau EXCLUDE_GRACE_POLLS luot do`() {
        val fake = FakeShell().apply { clusterDisplayId = 4; vdAfterTheme = 4 }
        val c = SimpleCastCoordinator(
            ProjectionManager(fake, sleepMs = {}, recipe = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT, themeOnVacantVd = true)),
            DisplayConfigurator(fake), AppMover(fake, sleepMs = {}), FakePrefs(), fake,
            displayId = 4, detectSleepMs = {}, desiredStyle = { CastStyle.RECT },
        )
        c.openProjection()
        val deadline = System.currentTimeMillis() + 8000
        while (c.state !is SimpleCastState.Idle && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(c.state is SimpleCastState.Idle, "${c.state} ${fake.history}")
        val h = fake.history.toList()
        assertTrue(h.any { it.contains(" i32 1000 i32 31 ") }, "mức B phải gửi 31: $h")
        val after35 = h.indexOfFirst { it.contains(" i32 1000 i32 35 ") }
        val black = h.indexOfFirst { it.startsWith("am start --display 4 ") && it.contains("ClusterBlackActivity") }
        assertTrue(after35 in 0 until black, "$h")
        assertEquals(ClusterDisplayResolver.EXCLUDE_GRACE_POLLS,
            h.subList(after35, black).count { it == ClusterDisplayResolver.DETECT_CMD }, "$h")
        c.shutdown()
    }

    // ─── R7 ───────────────────────────────────────────────────────────────────

    /**
     * Tái hiện 20:48: sau 35 màn ảo cụm không dò ra kịp ⇒ lượt mở chạm hạn (ở đây 1,5 s) và bị ngắt TRƯỚC ClusterBlack. Trạng thái
     * phải nhất quán với xe (chiếu đang mở ⇒ `isOpen`, hồi về Idle), và lượt chiếu đầu tiên từ Idle phải đặt bù ClusterBlack trên id
     * dò tươi TRƯỚC khi đặt app. Thử ĐỎ: bỏ lời gọi `ensureClusterPlaceholder` trong `handleCastSlot`.
     */
    @Test
    fun `R7 - luot mo bi ngat sau 35 - luot chieu dau tu Idle dat bu ClusterBlack truoc app`() {
        val fake = FakeShell()
        val hide = AtomicBoolean(true)
        val sent35 = AtomicBoolean(false)
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                if (command.contains(" i32 1000 i32 35 ")) sent35.set(true)
                if (command == ClusterDisplayResolver.DETECT_CMD && sent35.get() && hide.get()) {
                    fake.history.add(command)
                    return ShellResult(0, "  Display 0:\n", "")
                }
                return fake.execute(command)
            }
        }
        val c = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}), DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), FakePrefs(), shell,
            displayId = 1, openTimeoutMs = 1_500L,
            detectSleepMs = { if (sent35.get()) Thread.sleep(it) },
        )
        c.openProjection()
        val deadline = System.currentTimeMillis() + 8000
        while (c.state !is SimpleCastState.Idle && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(c.state is SimpleCastState.Idle, "Error hồi về Idle vì chiếu ĐANG mở: ${c.state}")
        assertTrue(c.projection.isOpen, "16/35 đã gửi ⇒ isOpen khớp sự thật phía xe")
        assertFalse(fake.history.any { it.contains("ClusterBlackActivity") }, "lượt mở bị ngắt trước ClusterBlack: ${fake.history}")

        hide.set(false)
        val mark = fake.history.size
        c.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        val d2 = System.currentTimeMillis() + 5000
        while (c.state !is SimpleCastState.CastingSplit && System.currentTimeMillis() < d2) Thread.sleep(20)
        assertTrue(c.state is SimpleCastState.CastingSplit, "${c.state} ${fake.history}")
        val h = fake.history.drop(mark)
        val black = h.indexOfFirst { it.startsWith("am start --display 1 ") && it.contains("ClusterBlackActivity") }
        val app = h.indexOfFirst { it.contains("com.test.left") }
        assertTrue(black >= 0 && black < app, "đặt bù nền chiếu TRƯỚC app, đúng display dò tươi: $h")
        c.shutdown()
    }

    /** Đường thường: lượt mở đã đặt nền ⇒ lượt chiếu từ Idle chỉ ĐỌC, 0 lệnh `am start` ClusterBlack thứ hai. */
    @Test
    fun `R7 - nen chieu da co - khong dat lan hai`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(1, shell.history.count { it.contains("ClusterBlackActivity") && it.startsWith("am start") })
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.full", AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        assertEquals(1, shell.history.count { it.contains("ClusterBlackActivity") && it.startsWith("am start") }, "${shell.history}")
    }

    /** Parser THUẦN trên dump thật [ĐO xe 29/09] (`am-stack-list-oncar-2026-09-29-fullscreen-app-top.txt`: ClusterBlack task 51 ở display 1). */
    @Test
    fun `R7 - presentOn tren dump that`() {
        val dump = javaClass.classLoader.getResource("diagnostics/am-stack-list-oncar-2026-09-29-fullscreen-app-top.txt")!!.readText()
        assertTrue(ClusterPlaceholder.presentOn(dump, 1, "com.byd.launcher"))
        assertFalse(ClusterPlaceholder.presentOn(dump, 4, "com.byd.launcher"), "khác display ⇒ vắng")
        assertFalse(ClusterPlaceholder.presentOn(dump, 1, "com.byd.launcher2"), "gói khác (khớp TRỌN) ⇒ không phải nền của mình")
        assertFalse(ClusterPlaceholder.presentOn(dump, 7, "com.byd.launcher"), "display 7 có YouTube, không có nền")
    }
}
