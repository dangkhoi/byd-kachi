package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ WAKE-INTERMITTENT-1017 — ngân sách CPU của bộ nghe chỉ tính lượt gọi ENGINE, không tính phiên lệnh ═════════════════════════
 *
 * Câu hỏi của buổi xe 17/10: CPU của phiên lệnh (cùng tiến trình `:wake`) có rò vào [VoiceWakeCpuBudget] không. [ĐO xe
 * `/proc/<pid>/task/<tid>/stat` + log] không: phần tính được (304,5 s) ≤ CPU của các luồng suy diễn wake; phiên lệnh và lúc nạp mô hình
 * nằm ngoài cửa sổ tính. Lý do theo mã: mỗi lần tính là hiệu `Process.getElapsedCpuTime()` quanh ĐÚNG MỘT lời gọi engine
 * (`feed`/`observe`/`finish`), và bộ nghe chỉ gọi engine khi đang GIỮ micro — phiên lệnh không bao giờ chạy lúc ấy
 * ([VoiceSingleFlight]: nhường micro trước khi phiên mở). Đồng hồ CPU **cả tiến trình** là cố ý: [ĐO xe] 9 luồng onnxruntime của
 * recognizer (tên `kachi-wake`) gánh ~200 s — đo CPU của riêng luồng nghe là bỏ sót hơn nửa giá thật.
 *
 * Bài này khoá cái hình dạng ấy: thêm một lần tính bọc thứ khác (vd quanh `handOff`, quanh cả vòng `inner`) là rò phiên lệnh vào
 * ngân sách ⇒ đỏ. Phần "không thừa kế nợ khi nghe lại" là số học thuần, khoá ở `VoiceWakeCpuBudgetTest`.
 */
class VoiceWakeBudgetChargeContractTest {

    private val listener by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeListener.kt") }

    @Test fun `moi lan tinh ngan sach boc dung mot loi goi engine`() {
        val inner = SourceRoots.body(listener, "private fun inner(kws: WakeEngine?): Inner")
        val charges = Regex("""controller\.onInferenceCost\(""").findAll(listener).count()
        val shaped = Regex(
            """val c0 = Process\.getElapsedCpuTime\(\)\s*\n\s*(val hit = )?kws\.(feed|observe|finish)\([^\n]*\)\s*\n\s*""" +
                """controller\.onInferenceCost\(Process\.getElapsedCpuTime\(\) - c0, now\)""",
        ).findAll(inner).count()
        assertEquals(3, charges, "đúng ba chỗ tính: feed · observe · finish")
        assertEquals(charges, shaped, "mỗi chỗ tính phải là hiệu CPU quanh ĐÚNG một lời gọi engine, liền nhau")
        assertTrue("onInferenceCost" !in SourceRoots.body(listener, "private fun handOff(rec: AudioRecord, buf: ShortArray): Boolean"))
    }
}
