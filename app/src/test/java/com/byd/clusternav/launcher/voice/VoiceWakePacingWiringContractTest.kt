package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WAKE-WEAK-UNIT (3.1.14) — dây nối `:app` của luật lùi theo tải máy (luật thuần: `VoiceWakePacerTest`).
 * Mỗi bài khoá một chỗ mà nếu đứt thì luật đúng mà không ai gọi (CLAUDE.md §8):
 *  1. mọi lượt dựng recognizer chạy ở nice 19 (luồng ORT thừa kế — doc §3b);
 *  2. lượt nạp NỀN (nạp sẵn + lượt nạp đầu của bộ nghe) chờ máy rảnh TRƯỚC khi dựng;
 *  3. vòng nghe đo tải mỗi giây trên chính luồng nghe và áp trần vào controller; dòng `ngan_sach` mang trạng thái;
 *  4. màn Chẩn đoán in dòng trạng thái của `:wake`.
 * Red-proof: trả `VoiceRecognizer.kt`, `VoiceWakeListener.kt`, `PerfDiag.kt` về HEAD ⇒ cả 4 bài đỏ.
 */
class VoiceWakePacingWiringContractTest {
    private val engine by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt") }
    private val listener by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeListener.kt") }
    private val pacing by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakePacing.kt") }
    private val diag by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/perf/PerfDiag.kt") }

    private fun order(src: String, vararg tokens: String) {
        val at = tokens.map { t -> src.indexOf(t).also { assertTrue(it >= 0, "thiếu '$t'") } }
        assertEquals(at.sorted(), at, "thứ tự sai: ${tokens.joinToString(" → ")}")
    }

    @Test
    fun `1 - luot dung recognizer chay o nice 19 va tra lai muc cu`() {
        val build = SourceRoots.body(engine, "private fun build(ctx: Context, model: SherpaModelCatalog.SherpaModel): OfflineRecognizer?")
        assertTrue("VoiceLoadPacing.atLowest { OfflineRecognizer(assetManager = null, config = config) }" in build,
            "OfflineRecognizer phải dựng dưới atLowest (luồng ORT thừa kế nice của luồng dựng)")
        val low = SourceRoots.body(pacing, "fun <T> atLowest(block: () -> T): T")
        order(low, "Process.getThreadPriority(tid)", "Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)", "return block()", "finally", "Process.setThreadPriority(old)")
    }

    @Test
    fun `2 - nap nen cho may ranh truoc khi dung`() {
        val pre = SourceRoots.body(engine, "fun preload(ctx: Context, delayMs: Long = PRELOAD_DELAY_MS, inWake: Boolean = false)")
        order(pre, "VoicePreloadPolicy.shouldPreload(", "VoiceLoadPacing.awaitCalm(", "recognizer(app)")
        val outer = SourceRoots.body(listener, "private fun runOuter()")
        order(outer, "pacing = VoiceWakePacing(ctx)", "if (!VoiceEngine.loaded()) VoiceLoadPacing.awaitCalm(", "kwsFactory(ctx)")
        // Soát Pass 15: mô hình đã có (người bấm mic nạp ngay, không qua cổng chờ) ⇒ lượt chờ thôi NGAY, không đứng tới 120 s.
        val wait = SourceRoots.body(SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakePacing.kt"), "fun awaitCalm(why: String")
        order(wait, "while (go == VoiceLoadDefer.Go.WAIT)", "if (ready()) { go = VoiceLoadDefer.Go.READY", "probe.napSample(")
    }

    @Test
    fun `3 - vong nghe do tai moi giay va ap tran, dong ngan_sach mang trang thai`() {
        val inner = SourceRoots.body(listener, "private fun inner(kws: WakeEngine?): Inner")
        assertTrue("pacing?.tick(controller, now)" in inner, "nhịp tải mỗi giây phải gọi tick")
        val tick = SourceRoots.body(pacing, "fun tick(ctl: VoiceWakeController, nowMs: Long)")
        order(tick, "probe.napSample(PROBE_MS)", "backoff.sample(d)", "ctl.pace(deviceDuty(), busy, nowMs)")
        assertTrue("it.minute(controller" in SourceRoots.body(listener, "private fun logBudget(kws: WakeEngine?)"))
        val minute = SourceRoots.body(pacing, "fun minute(ctl: VoiceWakeController, decodes: Long): String")
        for (k in listOf("cpu_wake=", "tai_he=", "tran=", "nap_ms=", "cho_nap_ms=", "STATUS.append(")) assertTrue(k in minute, "dòng phút thiếu $k")
    }

    @Test
    fun `4 - man Chan doan in dong trang thai cua wake`() {
        assertTrue("VoiceWakePacing.lastStatus(ctx)" in diag)
    }
}
