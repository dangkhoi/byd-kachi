package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * VOICE-WAKE-HANDOFF-GAP + VOICE-WAKE-CPU-BUDGET — khoá dây nối (bài canh mã nguồn; cơ chế thuần ở `:core`).
 *
 * [ĐO xe 15/10 18:37] khớp "Hey Kachi" → micro phiên lệnh mở lại sau 2,9 s ⇒ mất đầu lệnh. Bài này khoá: (1) khớp xong
 * thì bộ nghe GIAO micro đang ghi, không nhả; (2) `finally` không đóng micro đã giao; (3) phiên NHẬN micro giao trước
 * khi mở thiết bị mới và không `startRecording` lại; (4) mỗi lượt gọi engine được tính ms CPU THẬT vào ngân sách.
 */
class VoiceWakeHandoffWiringContractTest {

    private val listener by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeListener.kt") }
    private val capture by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceCapture.kt") }

    @Test fun `khop cau goi thi giao micro, khong nha, khong goi onWake hai lan`() {
        val inner = SourceRoots.body(listener, "private fun inner(kws: WakeEngine?): Inner")
        assertTrue(inner.contains("handed = handOff(rec, buf)"), "FIRE phải giao micro (VoiceWakeHandoff)")
        assertTrue(inner.contains("if (!handed) {"), "finally không được stop/release micro đã giao")
        val hand = SourceRoots.body(listener, "private fun handOff(rec: AudioRecord, buf: ShortArray): Boolean")
        assertTrue(hand.contains("VoiceSingleFlight.yieldRequested()") && hand.contains("VoiceWakeHandoff.park(rec, pcm, n)"),
            "chỉ giao khi phiên XIN micro (một luồng đọc tại một thời điểm)")
        assertTrue(hand.contains("VoiceWakeHandoff.MAX_PREROLL_MS"), "phải có trần — không phiên nào xin thì nhả như cũ")
        assertFalse(listener.contains("Inner.WOKE ->"), "đường cũ gọi onWake SAU khi nhả micro phải biến mất (không gọi hai lần)")
    }

    @Test fun `phien nhan micro giao truoc khi mo moi, nap dem truoc, khong startRecording lai`() {
        val granted = SourceRoots.body(capture, "private fun listenGranted(")
        val adopt = granted.indexOf("VoiceCaptureDevice.adopt(ctx, VoiceWakeHandoff.takeOver())")
        val open = granted.indexOf("VoiceCaptureDevice.open(ctx)")
        assertTrue(adopt in 0 until open, "phải thử nhận micro đã giao TRƯỚC khi mở micro mới")
        assertTrue(granted.contains("if (handed == null) runCatching { record.startRecording() }"), "micro giao đang ghi sẵn")
        assertTrue(granted.contains("pre.n - preAt"), "tiếng đệm sau câu gọi phải được nạp trước micro")
    }

    @Test fun `moi luot goi engine bi tinh ms CPU that vao ngan sach`() {
        val inner = SourceRoots.body(listener, "private fun inner(kws: WakeEngine?): Inner")
        assertTrue(inner.contains("kws.observe(fbuf, n)"), "hết ngân sách ⇒ engine chỉ gom (OBSERVE)")
        assertTrue(Regex("controller\\.onInferenceCost\\(Process\\.getElapsedCpuTime\\(\\) - c0, now\\)").findAll(inner).count() == 3,
            "feed, observe và lượt cuối (finish) đều phải báo CPU thật")
        assertTrue(inner.contains("val hit = kws.finish()"), "sườn tiếng→im ⇒ lượt giải mã cuối (VOICE-WAKE-TAIL-UNDECODED)")
    }
}
