package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F6: gói không cài ⇒ không gửi, dò lại PackageManager chỉ sau TTL, log vắng đúng 1 lần.
 * Đỏ→xanh: bỏ điều kiện `nowMs - checkedAtMs >= ttlMs` (dò mỗi lần) ⇒ `probe=4` ĐỎ; bỏ `absenceLogged` ⇒ log 2 lần ĐỎ.
 */
class InstalledPackageGateTest {
    @Test
    fun `khong cai thi khong gui, chi do lai sau TTL, log vang dung 1 lan`() {
        val g = InstalledPackageGate(60_000L)
        var probes = 0
        var logs = 0
        val probe = { probes++; false }
        assertFalse(g.installed(0L, probe) { logs++ })
        assertFalse(g.installed(2_000L, probe) { logs++ })
        assertFalse(g.installed(59_999L, probe) { logs++ })
        assertEquals(1, probes, "trong TTL không hỏi lại PackageManager")
        assertEquals(1, logs, "log 'bỏ vì không cài' đúng một lần")
        assertFalse(g.installed(60_000L, probe) { logs++ })
        assertEquals(2, probes, "hết TTL mới dò lại")
        assertEquals(1, logs, "vẫn vắng ⇒ không log thêm")
    }

    @Test
    fun `cai xong thi gui lai, go roi thi log vang lai 1 lan`() {
        val g = InstalledPackageGate(60_000L)
        var present = false
        var logs = 0
        val probe = { present }
        assertFalse(g.installed(0L, probe) { logs++ })
        present = true
        assertFalse(g.installed(30_000L, probe) { logs++ }, "chưa hết TTL ⇒ vẫn theo kết quả nhớ")
        assertTrue(g.installed(60_000L, probe) { logs++ }, "hết TTL ⇒ thấy đã cài")
        present = false
        assertFalse(g.installed(120_000L, probe) { logs++ })
        assertEquals(2, logs, "gỡ đi ⇒ log vắng lại một lần cho chuỗi vắng mới")
    }
}
