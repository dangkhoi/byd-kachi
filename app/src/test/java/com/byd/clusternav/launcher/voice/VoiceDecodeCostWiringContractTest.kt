package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ WAKE-DECODE-COST (3.1.12) — BÀI CANH DÂY NỐI tuỳ chọn onnxruntime (CLAUDE.md §8: compile xanh ≠ code chạy) ═══
 *
 * [ĐO xe 17/10] một lượt giải mã "Hey Kachi" tốn 2,2–4,4 s CPU, phần lớn là worker onnxruntime quay chờ sau `Run()`. Sửa ở
 * [SherpaModelCatalog.ORT_SESSION_CONFIG] (số đo trong KDoc). Bài này khoá ba điều mà một lần sửa nhầm sẽ làm mất IM LẶNG:
 *  1. recognizer DUY NHẤT của tiến trình ([VoiceEngine.build] — wake lẫn phiên lệnh dùng chung) lấy provider qua
 *     [VoiceOrtConfig] chứ không phải literal `"cpu"` (literal ⇒ quay chờ trở lại, chẳng test nào khác đỏ);
 *  2. phiên lệnh KHÔNG đổi: số luồng vẫn `threadsForDecode()` kẹp [2, 4] như trước 3.1.12 — đổi số luồng đổi chữ ra
 *     (đo host: 1 cửa sổ/837 khác giữa 4 và 1–2 luồng) và làm chậm câu lệnh dài;
 *  3. fail-safe: [VoiceOrtConfig] chỉ đưa đường dẫn ĐÃ đọc lại được (sherpa-onnx thoát tiến trình khi không mở được tệp).
 * Đỏ-khi-gỡ: trả `provider = "cpu"` ở `VoiceRecognizer.kt` ⇒ (1) đỏ; bỏ `check(...)` đọc lại ⇒ (3) đỏ.
 */
class VoiceDecodeCostWiringContractTest {

    private val engine by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt") }
    private val ort by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceOrtConfig.kt") }

    @Test
    fun `recognizer chung lay provider qua VoiceOrtConfig, khong con literal cpu`() {
        assertEquals(1, Regex("""provider\s*=\s*VoiceOrtConfig\.provider\(ctx\)""").findAll(engine).count())
        assertFalse(Regex("""provider\s*=\s*"cpu"""").containsMatchIn(engine), "literal \"cpu\" = quay chờ trở lại")
        assertTrue(ort.contains("SherpaModelCatalog.ORT_SESSION_CONFIG_FILE") && ort.contains("SherpaModelCatalog.ORT_SESSION_CONFIG"))
        assertTrue(ort.contains("SherpaModelCatalog.provider(path)"), "chuỗi provider dựng ở :core (một chỗ khai)")
    }

    @Test
    fun `phien lenh khong doi so luong giai ma`() {
        assertEquals(1, Regex("""numThreads\s*=\s*threadsForDecode\(\)""").findAll(engine).count())
        assertTrue(engine.contains("private const val MIN_THREADS = 2"))
        assertTrue(engine.contains("private const val MAX_THREADS = 4"))
    }

    @Test
    fun `chi dua duong dan da doc lai duoc, hong thi roi ve cpu`() {
        assertTrue(Regex("""check\(f\.canRead\(\) && f\.readText\(\) == want\)""").containsMatchIn(ort), "phải đọc lại trước khi trả")
        assertTrue(ort.contains(".getOrNull()"), "lỗi ghi/đọc ⇒ path null ⇒ provider(null) = \"cpu\"")
    }
}
