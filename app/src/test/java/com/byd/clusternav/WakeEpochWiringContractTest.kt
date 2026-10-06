package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 · F3 — dây nối của luật thuần `WakeEpochPolicy` (`:core` `WakeEpochPolicyTest` khoá hành vi; spec
 * `kachi-291-small-fixes.html` §4.3). Bài này canh mã đã bỏ chú thích: lần TẮT màn được nghe và ghi, `wake` dùng đúng luật, và
 * `readyChain` kiểm cổng TRƯỚC khi tiêu mốc. Thử ĐỎ: trả `readyChain` về `compareAndSet` trước rồi mới kiểm cổng.
 */
class WakeEpochWiringContractTest {

    private val early by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/EarlyShellChannel.kt") }
    private val log by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/KachiReadyLog.kt") }

    private fun order(src: String, vararg parts: String) {
        var at = -1
        parts.forEach { p ->
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai / thiếu '$p'")
            at = i
        }
    }

    @Test
    fun `bo thu nghe ca man TAT va ghi moc tat`() {
        assertTrue("IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }" in early)
        order(SourceRoots.body(early, "override fun onReceive("),
            "if (intent?.action == Intent.ACTION_SCREEN_OFF) { KachiReadyLog.screenOff(SystemClock.elapsedRealtime()); return }",
            "if (intent?.action != Intent.ACTION_SCREEN_ON) return", "KachiReadyLog.wake(SystemClock.elapsedRealtime(), \"broadcast\")")
        assertTrue("screenOffAt.set(at)" in SourceRoots.body(log, "fun screenOff(at: Long)"))
    }

    @Test
    fun `wake dung luat thuan - mot lan tat chen giua la lan thuc moi`() {
        val wake = SourceRoots.body(log, "fun wake(at: Long, src: String) {")
        order(wake, "if (!WakeEpochPolicy.isNewWake(screenOnAt.get(), screenOffAt.get(), at)) return", "screenOnAt.set(at)")
        assertFalse("SAME_WAKE_MS" in log.substringBefore("fun wake("), "không giữ hằng gộp riêng — một luật ở :core")
    }

    @Test
    fun `chuoi SAN kiem cong TRUOC khi tieu moc`() {
        val chain = SourceRoots.body(early, "private fun readyChain(")
        order(chain, "val prev = chainFor.get()",
            "if (!WakeEpochPolicy.shouldRun(prev, epoch, ShellReadiness.isUp(), interactive(app))) return",
            "if (!chainFor.compareAndSet(prev, epoch)) return", "AppPrereqs.onReady(app)")
    }
}
