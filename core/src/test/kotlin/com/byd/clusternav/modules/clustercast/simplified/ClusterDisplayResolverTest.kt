package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.CastDisplayFixtures2026_09_15
import com.byd.clusternav.modules.clustercast.DisplayParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * X2 + R1/R2 regression lock — cast phải chọn display CỤM ĐỘNG (dò fission/xdja), KHÔNG bao giờ giả định `1`,
 * KHÔNG fallback về seed/saved, và KHÔNG BAO GIỜ trả VD do chính launcher sở hữu.
 *
 * Bằng chứng [ĐO] xe DiLink3.0 2026-09-15 (fixture nguyên văn ở [CastDisplayFixtures2026_09_15]): display 1 =
 * `kachi-slot-0` (owner `com.byd.launcher`), cụm = **display 2** `fission_bg_xdjaVirtualSurface`. Bản trước:
 * dò hụt (trước khi mở projection) → rơi về seed 1 → ClusterBlack + GMaps đặt vào ô của launcher.
 */
class ClusterDisplayResolverTest {

    private val self = CastDisplayFixtures2026_09_15.LAUNCHER_PKG

    @Test
    fun `(a) detect grep thật 2026-09-15 → display 2`() {
        assertEquals(2, ClusterDisplayResolver.resolve(CastDisplayFixtures2026_09_15.DETECT_OUT, self))
    }

    @Test
    fun `(b) dump có slot-VD display 1 owner launcher → không bao giờ trả 1, vẫn ra 2`() {
        val id = ClusterDisplayResolver.resolve(CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT, self)
        assertEquals(2, id)
        assertTrue(DisplayParse.isOwnedVirtualDisplay(CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT, 1, self),
            "display 1 là VD của launcher → cấm đặt")
        assertFalse(DisplayParse.isOwnedVirtualDisplay(CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT, 2, self))
    }

    @Test
    fun `R1 - không fission (VD cụm chưa được tạo, trước khi mở projection) → -1, không có fallback`() {
        // Chỉ còn slot của launcher + màn giữa — đúng cảnh sau reboot TRƯỚC khi AutoContainer mở projection.
        assertEquals(-1, ClusterDisplayResolver.resolve(CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY, self))
        assertEquals(-1, ClusterDisplayResolver.resolve("", self))
    }

    @Test
    fun `R2 - id dò ra mà là VD của chính launcher → coi như CHƯA resolve (-1)`() {
        // Tổng hợp (không thể xảy ra thật): VD tên chứa "fission" nhưng uniqueId virtual:<launcher>.
        val poisoned = """
            |  Display 0:
            |  Display 1:
            |    mBaseDisplayInfo=DisplayInfo{"fission_fake, displayId 1", uniqueId "virtual:$self,10138,fission_fake,0", app 1872 x 748}
            |""".trimMargin()
        assertEquals(-1, ClusterDisplayResolver.resolve(poisoned, self))
        // Gói KHÁC sở hữu thì không bị guard (guard theo tham số, không hardcode tên gói).
        assertEquals(1, ClusterDisplayResolver.resolve(poisoned, "com.other.app"))
    }

    @Test
    fun `never returns 0 — display 0 is the centre screen, never the cluster`() {
        val onlyZero = "Display 0: DisplayDeviceInfo{\"fission...\"}"
        assertEquals(-1, ClusterDisplayResolver.resolve(onlyZero, self))
    }

    @Test
    fun `detectAndPersist runs the detect command, resolves and persists`() {
        val shell = object : SimpleCastShell {
            var lastCmd: String? = null
            override fun execute(command: String): ShellResult {
                lastCmd = command
                return ShellResult(0, CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT, "")
            }
        }
        var persisted: Int? = null
        val resolved = ClusterDisplayResolver.detectAndPersist(shell, self) { persisted = it }
        assertEquals(2, resolved)
        assertEquals(2, persisted)
        assertEquals(ClusterDisplayResolver.DETECT_CMD, shell.lastCmd)
    }

    @Test
    fun `detectAndPersist returns -1 and does not persist when the shell fails`() {
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult = ShellResult(1, "", "boom")
        }
        var persisted: Int? = null
        assertEquals(-1, ClusterDisplayResolver.detectAndPersist(shell, self) { persisted = it })
        assertNull(persisted)
    }

    @Test
    fun `awaitAndPersist lặp tới khi VD cụm xuất hiện (AutoContainer tạo sau khi mở projection)`() {
        var calls = 0
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                calls++
                val out = if (calls < 3) CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY
                else CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT
                return ShellResult(0, out, "")
            }
        }
        val sleeps = mutableListOf<Long>()
        val id = ClusterDisplayResolver.awaitAndPersist(shell, self, attempts = 5, sleepMs = { sleeps += it }) {}
        assertEquals(2, id)
        assertEquals(3, calls)
        assertEquals(listOf(ClusterDisplayResolver.AWAIT_SLEEP_MS, ClusterDisplayResolver.AWAIT_SLEEP_MS), sleeps)
    }

    @Test
    fun `awaitAndPersist bỏ cuộc sau attempts lần hụt → -1`() {
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult =
                ShellResult(0, CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY, "")
        }
        var persisted: Int? = null
        val id = ClusterDisplayResolver.awaitAndPersist(shell, self, attempts = 4, sleepMs = {}) { persisted = it }
        assertEquals(-1, id)
        assertNull(persisted, "không persist gì khi hụt")
    }
}
