package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * X2 regression lock — cast phải chọn display CỤM ĐỘNG (dò fission/xdja), KHÔNG bao giờ giả định `1`.
 *
 * Bằng chứng [ĐO] xe DiLink3.0 2026-09-14: cụm = **display 2** `fission_bg_xdjaVirtualSurface`. Bản SimpleCast
 * cũ hardcode `savedDisplayId ?: 1` → mọi `wm …/am start --display 1` nhắm sai màn. Fixture dưới lấy đúng dạng
 * dòng `dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'` quan sát trên xe.
 */
class ClusterDisplayResolverTest {

    /** Dạng grep thật trên xe: header "Display N:" + dòng chứa tên VD fission/xdja. */
    private val vehicleGrep = """
        Display 0: DisplayDeviceInfo{"Built-in Screen": ...}
        Display 2: DisplayDeviceInfo{"fission_bg_xdjaVirtualSurface": uniqueId "local:...", 1920 x 720, ...}
    """.trimIndent()

    @Test
    fun `detects the fission cluster display (2) — never assumes 1`() {
        // saved=1 (giá trị hardcode cũ) KHÔNG được thắng detection live.
        val id = ClusterDisplayResolver.resolve(vehicleGrep, savedId = 1, fallback = 1)
        assertEquals(2, id, "phải dò ra display 2 theo tên VD fission/xdja, không phải 1")
    }

    @Test
    fun `detection wins over a stale saved id`() {
        val id = ClusterDisplayResolver.resolve(vehicleGrep, savedId = 7, fallback = 1)
        assertEquals(2, id, "live detection tự sửa khi VD id lệch với giá trị đã lưu")
    }

    @Test
    fun `falls back to saved id when detection is empty`() {
        val id = ClusterDisplayResolver.resolve("", savedId = 3, fallback = 1)
        assertEquals(3, id, "dò hụt → dùng id đã lưu")
    }

    @Test
    fun `falls back to fallback when nothing detected and nothing saved`() {
        val id = ClusterDisplayResolver.resolve("", savedId = null, fallback = 1)
        assertEquals(1, id)
    }

    @Test
    fun `detectAndPersist runs the detect command, resolves and persists`() {
        val shell = object : SimpleCastShell {
            var lastCmd: String? = null
            override fun execute(command: String): ShellResult {
                lastCmd = command
                return ShellResult(0, vehicleGrep, "")
            }
        }
        var persisted: Int? = null
        val resolved = ClusterDisplayResolver.detectAndPersist(shell, currentId = 1) { persisted = it }
        assertEquals(2, resolved, "phải dò ra display 2")
        assertEquals(2, persisted, "id đã chọn phải được persist")
        assertEquals(ClusterDisplayResolver.DETECT_CMD, shell.lastCmd)
    }

    @Test
    fun `detectAndPersist keeps current id and does not persist when the shell fails`() {
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult = ShellResult(1, "", "boom")
        }
        var persisted: Int? = null
        val resolved = ClusterDisplayResolver.detectAndPersist(shell, currentId = 5) { persisted = it }
        assertEquals(5, resolved, "dò hụt → giữ nguyên id hiện tại")
        assertEquals(null, persisted, "không persist khi dò hụt")
    }

    @Test
    fun `never returns 0 — display 0 is the centre screen, never the cluster`() {
        // Ngay cả khi tên VD gắn với "Display 0" (không thể xảy ra thật), resolver không được trả 0.
        val onlyZero = "Display 0: DisplayDeviceInfo{\"fission...\"}"
        val id = ClusterDisplayResolver.resolve(onlyZero, savedId = null, fallback = 1)
        assertEquals(1, id, "clusterDisplayId bỏ qua id 0 → rơi về fallback ≥ 1")
    }
}
