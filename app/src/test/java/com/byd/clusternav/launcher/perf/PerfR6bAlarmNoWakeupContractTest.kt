package com.byd.clusternav.launcher.perf

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R6-B′ (owner duyệt 09/10, soát log xe: báo thức 0 lần cứu phím) — báo thức `REBIND_WATCHDOG` KHÔNG thức máy, nhưng VẪN hẹn ở
 * mọi chỗ như cũ (bỏ hẹn = tháo lưới khi FGS chết — B gốc bị bác). Thử ĐỎ: trả lại `ELAPSED_REALTIME_WAKEUP`, hoặc bỏ một chỗ hẹn.
 */
class PerfR6bAlarmNoWakeupContractTest {

    @Test
    fun `bao thuc khong thuc may nhung van hen o moi cho`() {
        val r = SourceRoots.codeOf("src/main/java/com/byd/clusternav/RebindReceiver.kt")
        val fn = SourceRoots.body(r, "fun scheduleWatchdog(context: Context)")
        assertTrue("AlarmManager.ELAPSED_REALTIME," in fn, fn)
        assertFalse("ELAPSED_REALTIME_WAKEUP" in fn, "không thức máy")
        assertTrue("setInexactRepeating(" in fn)
        val callers = listOf(
            "src/main/java/com/byd/clusternav/VoiceKeyKeepAliveService.kt",
            "src/main/java/com/byd/clusternav/EarlyShellChannel.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt",
        ).count { "scheduleWatchdog(" in SourceRoots.codeOf(it) }
        assertEquals(3, callers, "mọi chỗ hẹn giữ nguyên")
    }
}
