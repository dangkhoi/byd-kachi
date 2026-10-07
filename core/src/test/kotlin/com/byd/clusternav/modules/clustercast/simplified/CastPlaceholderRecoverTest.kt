package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 2.96 · R15 — khe còn lại của R7 ([ĐO xe 07/10 20:48 + dumpsys 20:55]): lượt mở bị ngắt SAU 16/35, TRƯỚC ClusterBlack, và người
 * lái KHÔNG chiếu app nào ⇒ trước R15 cụm ở chế độ chiếu không nền mãi. Nay một việc nền "placeholder-recover" (chỉ khi executor
 * rảnh — R5) đặt bù ClusterBlack.
 */
class CastPlaceholderRecoverTest {

    private fun await(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        return cond()
    }

    private fun blackStarts(h: List<String>) = h.count { it.startsWith("am start") && it.contains("ClusterBlackActivity") }

    /**
     * Tái hiện 20:48 như R7 (màn ảo cụm chưa dò ra kịp sau 35 ⇒ chạm hạn 1,5 s), rồi màn ảo xuất hiện muộn. KHÔNG có lượt chiếu
     * app nào: việc nền phải tự đặt ClusterBlack lên display dò tươi, trạng thái vẫn Idle (không phiên chiếu nào được dựng).
     * Thử ĐỎ: bỏ lời gọi `schedulePlaceholderRecover` trong catch của `openProjection` ⇒ 0 ClusterBlack.
     */
    @Test
    fun `luot mo bi ngat sau 35 - khong chieu app - viec nen dat bu ClusterBlack`() {
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
            placeholderRecoverDelayMs = 600L,
        )
        c.openProjection()
        assertTrue(await(6000) { c.state is SimpleCastState.Error }, "lượt mở phải bị ngắt: ${c.state}")
        assertTrue(c.projection.isOpen)
        assertEquals(0, blackStarts(fake.history), "bị ngắt trước ClusterBlack: ${fake.history}")
        hide.set(false)   // màn ảo cụm hiện ra muộn (sau khi lượt mở đã chạm hạn)

        assertTrue(await(8000) { blackStarts(fake.history) == 1 }, "việc nền phải đặt bù đúng MỘT lần: ${fake.history}")
        assertTrue(fake.history.any { it.startsWith("am start --display 1 ") && it.contains("ClusterBlackActivity") })
        assertTrue(await(4000) { c.state is SimpleCastState.Idle }, "${c.state}")
        assertFalse(fake.history.any { it.contains("com.test.") }, "không lượt chiếu app nào")
        Thread.sleep(800)
        assertEquals(1, blackStarts(fake.history), "đặt rồi ⇒ không đặt lần hai")
        c.shutdown()
    }

    /** Executor bận (lệnh người dùng đang chạy + một lệnh chờ) ⇒ việc nền KHÔNG vào hàng, lệnh chờ không bị đẩy; rảnh rồi mới chạy. */
    @Test
    fun `executor ban - viec nen khong day lenh nguoi dung, chay sau khi ranh`() {
        val shell = FakeShell()
        val c = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}), DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), FakePrefs(), shell,
            displayId = 1, detectSleepMs = {},
        )
        c.openProjection()
        assertTrue(await(3000) { c.state is SimpleCastState.Idle }, "${c.state}")

        val release = CountDownLatch(1)
        val pendingRan = AtomicBoolean(false)
        c.executor.submit("user-running") { release.await(5, TimeUnit.SECONDS) }
        c.executor.submit("user-pending") { pendingRan.set(true) }
        val mark = shell.history.size
        c.schedulePlaceholderRecover(delayMs = 50L)
        Thread.sleep(300)   // lần thử đầu gặp executor bận ⇒ hẹn lại (RETRY_MS), không xếp
        release.countDown()
        assertTrue(await(3000) { pendingRan.get() }, "lệnh chờ của người dùng KHÔNG được bị bỏ")
        // Lần thử lại chạy khi rảnh: đọc `am stack list`, nền đã có ⇒ 0 lệnh ghi.
        assertTrue(await(PlaceholderRecover.RETRY_MS + 3000) {
            shell.history.drop(mark).any { it == ClusterDisplayResolver.DETECT_CMD }
        }, "việc nền chạy sau khi rảnh: ${shell.history.drop(mark)}")
        assertTrue(await(2000) { shell.history.drop(mark).any { it == "am stack list" } })
        assertEquals(1, blackStarts(shell.history), "nền đã có từ lượt mở ⇒ không đặt thêm")
        c.shutdown()
    }

    @Test
    fun `stillWanted - chi khi chieu mo va Idle hoac Error`() {
        assertTrue(PlaceholderRecover.stillWanted(true, SimpleCastState.Idle))
        assertTrue(PlaceholderRecover.stillWanted(true, SimpleCastState.Error("x")))
        assertFalse(PlaceholderRecover.stillWanted(false, SimpleCastState.Idle), "chiếu đã đóng")
        assertFalse(PlaceholderRecover.stillWanted(true, SimpleCastState.Off))
        assertFalse(PlaceholderRecover.stillWanted(true, SimpleCastState.Opening), "đang mở lại — lượt mở tự đặt")
        assertFalse(PlaceholderRecover.stillWanted(true, SimpleCastState.CastingFull("p", AppType.NORMAL, DisplayConfig.NORMAL_DEFAULT)),
            "đã chiếu app ⇒ R7 đã lo")
    }
}
