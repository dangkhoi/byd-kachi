package com.byd.clusternav.launcher.voice

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ H2 — BÀI CANH PHẦN THUẦN của nhật ký lượt nói ═══════════════════════════════════════════════════════════
 *
 * Ba tính chất, ba kiểu hỏng khác hẳn nhau, và cả ba đều **im lặng** nếu không có bài kiểm:
 *
 *  1. **Vòng đệm** — sai thì bộ nhớ xe đầy dần suốt hàng tháng trước khi ai đó nhận ra. Và trần có HAI vế
 *     (30 mục · 30 MB), nên một bài chỉ thử một vế sẽ xanh với một bản chỉ thi hành một vế.
 *  2. **Khuôn WAV** — lệch `byteRate`/`dataSize` thì tệp vẫn mở được, vẫn kêu, chỉ *sai*: host chạy lại ra kết
 *     quả khác xe và người đo sẽ đi đổ lỗi cho mô hình. Đúng loại lỗi phải bắt bằng phép so **từng byte**.
 *  3. **Khuôn JSON** — `replay-car-log.py` đọc bằng `json.load`; một chuỗi chưa thoát (câu người ta nói có thể
 *     mang xuống dòng) làm cả gói không đọc được.
 *
 * Off-device: [VoiceUtteranceLog.prune] · [VoiceUtteranceLog.wavHeader] · [VoiceUtteranceLog.json] đều thuần
 * (không `Context`) — cùng khuôn [VoiceModelSideloadTest], và cũng cùng lý do: phần dễ sai nhất phải kiểm được
 * mà không cần cắm xe.
 */
class VoiceUtteranceLogTest {

    private fun tmpDir(): File = Files.createTempDirectory("voicelog").toFile().apply { deleteOnExit() }

    /** Một cặp tệp đúng khuôn app ghi: `<stamp>.wav` (cỡ [wavBytes]) + `<stamp>.json`. */
    private fun entry(d: File, stamp: String, wavBytes: Int = 1_000) {
        File(d, "$stamp.wav").writeBytes(ByteArray(wavBytes))
        File(d, "$stamp.json").writeText("""{"stamp":"$stamp"}""")
    }

    private fun stamps(d: File): List<String> =
        d.listFiles()!!.map { it.name.substringBeforeLast('.') }.distinct().sorted()

    // ── (1) Vòng đệm — hai trần, kiểm RIÊNG từng cái ─────────────────────────────────────────────

    @Test
    fun `tran so muc giu dung 30 muc moi nhat, xoa ca cap`() {
        val d = tmpDir()
        // 35 mục nhỏ ⇒ chưa chạm trần BYTE, nên ca này cô lập đúng trần SỐ MỤC.
        (1..35).forEach { entry(d, "20260916-0000%02d-000".format(it)) }
        val removed = VoiceUtteranceLog.prune(d)
        assertEquals(5, removed, "phải xoá đúng 5 mốc cũ nhất")
        val left = stamps(d)
        assertEquals(VoiceUtteranceLog.MAX_ENTRIES, left.size)
        assertTrue(left.first().endsWith("000006-000"), "mốc cũ nhất còn lại phải là mốc thứ 6: ${left.first()}")
        // Cả CẶP phải đi cùng nhau — một `.json` mồ côi là một mục nói về một khúc tiếng không còn tồn tại.
        left.forEach { s ->
            assertTrue(File(d, "$s.wav").isFile && File(d, "$s.json").isFile, "mốc $s bị xé lẻ")
        }
        assertFalse(File(d, "20260916-000001-000.wav").exists())
        assertFalse(File(d, "20260916-000001-000.json").exists())
    }

    @Test
    fun `tran dung luong cat bot du so muc con trong han muc`() {
        val d = tmpDir()
        // 10 mục × 4 MB = 40 MB > 30 MB, mà số mục (10) thì thừa sức dưới 30 ⇒ ca này cô lập trần BYTE.
        val fourMb = 4 * 1024 * 1024
        (1..10).forEach { entry(d, "20260916-0000%02d-000".format(it), wavBytes = fourMb) }
        val removed = VoiceUtteranceLog.prune(d)
        assertTrue(removed >= 3, "40 MB phải bị cắt ít nhất 3 mốc, thấy $removed")
        val total = d.listFiles()!!.sumOf { it.length() }
        assertTrue(total <= VoiceUtteranceLog.MAX_BYTES, "còn $total byte — trên trần ${VoiceUtteranceLog.MAX_BYTES}")
        assertTrue(stamps(d).size < 10, "phải có mốc bị xoá")
        // Và vẫn là mốc CŨ NHẤT bị xoá trước, không phải mốc bất kỳ.
        assertTrue(stamps(d).last() == "20260916-000010-000", "mốc mới nhất phải sống sót")
    }

    @Test
    fun `duoi ca hai tran thi khong xoa gi`() {
        val d = tmpDir()
        (1..5).forEach { entry(d, "20260916-0000%02d-000".format(it)) }
        assertEquals(0, VoiceUtteranceLog.prune(d), "dưới cả hai trần thì không được đụng vào tệp nào")
        assertEquals(5, stamps(d).size)
    }

    /**
     * Một mốc **nửa vời** (tiến trình bị giết giữa `writeWav` và `writeJson`) vẫn phải dọn được.
     *
     * Bản dọn-theo-tệp sẽ để lại nửa còn lại sống mãi; bản dọn-theo-mốc coi nó là một mốc bình thường.
     */
    @Test
    fun `moc nua voi van bi dem va van xoa duoc`() {
        val d = tmpDir()
        (1..34).forEach { entry(d, "20260916-0000%02d-000".format(it)) }
        File(d, "20260916-000000-000.wav").writeBytes(ByteArray(10))   // mốc CỔ NHẤT, thiếu .json
        assertEquals(35, stamps(d).size)
        VoiceUtteranceLog.prune(d)
        assertEquals(VoiceUtteranceLog.MAX_ENTRIES, stamps(d).size)
        assertFalse(File(d, "20260916-000000-000.wav").exists(), "mốc nửa vời cũ nhất phải bị dọn trước")
    }

    @Test
    fun `thu muc chua ton tai thi prune khong nem`() {
        assertEquals(0, VoiceUtteranceLog.prune(File(tmpDir(), "chua-co")))
    }

    // ── (2) Khuôn WAV — so TỪNG BYTE ─────────────────────────────────────────────────────────────

    /**
     * 44 byte RIFF/WAVE cho PCM16 · mono · 16 kHz.
     *
     * Con số đối chiếu tính tay, không lấy từ chính mã: `byteRate = 16000 × 1 × 2 = 32000` (0x7D00),
     * `blockAlign = 2`, `bitsPerSample = 16`, `riffSize = 36 + dataSize`.
     */
    @Test
    fun `khuon WAV dung tung byte cho PCM16 mono 16 kHz`() {
        val data = 32_000      // 1 giây tiếng
        val h = VoiceUtteranceLog.wavHeader(data)
        assertEquals(VoiceUtteranceLog.WAV_HEADER_BYTES, h.size)

        fun ascii(at: Int, n: Int) = String(h, at, n, Charsets.US_ASCII)
        fun le32(at: Int): Int =
            (h[at].toInt() and 0xFF) or ((h[at + 1].toInt() and 0xFF) shl 8) or
                ((h[at + 2].toInt() and 0xFF) shl 16) or ((h[at + 3].toInt() and 0xFF) shl 24)
        fun le16(at: Int): Int = (h[at].toInt() and 0xFF) or ((h[at + 1].toInt() and 0xFF) shl 8)

        assertEquals("RIFF", ascii(0, 4))
        assertEquals(36 + data, le32(4), "riffSize = 36 + dataSize")
        assertEquals("WAVE", ascii(8, 4))
        assertEquals("fmt ", ascii(12, 4))
        assertEquals(16, le32(16), "cỡ khối fmt của PCM luôn là 16")
        assertEquals(1, le16(20), "audioFormat 1 = PCM không nén")
        assertEquals(1, le16(22), "mono")
        assertEquals(16_000, le32(24), "sampleRate — cùng số với mô hình")
        assertEquals(32_000, le32(28), "byteRate = sampleRate × channels × bits/8")
        assertEquals(2, le16(32), "blockAlign")
        assertEquals(16, le16(34), "bitsPerSample")
        assertEquals("data", ascii(36, 4))
        assertEquals(data, le32(40), "dataSize")
    }

    @Test
    fun `WAV rong van ra khuon hop le`() {
        val h = VoiceUtteranceLog.wavHeader(0)
        assertEquals(VoiceUtteranceLog.WAV_HEADER_BYTES, h.size)
        assertEquals(36, (h[4].toInt() and 0xFF) or ((h[5].toInt() and 0xFF) shl 8))
    }

    // ── (3) Khuôn JSON ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `JSON mang du truong ma script doc va thoat chuoi dung`() {
        val m = VoiceUtteranceLog.Meta(
            heard = "bật đèn đọc",
            sentence = "bật đèn đọc",
            micSource = 1,
            micSourceName = "MIC",
            modelId = "zipformer-vi-int8-2025-04-20",
            hotwords = 1902,
            endpointMs = 1_400,
            speechMs = 600,
            silenceMs = 800,
            endpointFired = true,
            listenMs = 1_450,
            decodeMs = 320,
            intents = listOf("Control"),
            replies = listOf("Đã bật đèn đọc"),
            clarify = false,
            followUp = true,
        )
        val s = VoiceUtteranceLog.json("20260916-101112-000", m)
        // Đúng những tên trường mà `scripts/voice/replay-car-log.py` đọc — đổi tên ở một bên là gãy bên kia.
        listOf(
            "\"stamp\"", "\"heard\"", "\"sentence\"", "\"intents\"", "\"replies\"", "\"mic_source\"",
            "\"mic_source_name\"", "\"model_id\"", "\"hotwords\"", "\"endpoint_ms\"", "\"speech_ms\"",
            "\"silence_ms\"", "\"endpoint_fired\"", "\"listen_ms\"", "\"decode_ms\"", "\"clarify\"", "\"follow_up\"",
        ).forEach { assertTrue(s.contains(it), "JSON thiếu trường $it") }
        assertTrue(s.contains("\"endpoint_fired\":true"), "bool phải là bool, không phải chuỗi")
        assertTrue(s.contains("\"endpoint_ms\":1400"), "số phải là số")
        assertTrue(s.contains("\"intents\":[\"Control\"]"), "mảng phải là mảng")
    }

    @Test
    fun `cau noi co xuong dong khong lam hong ca goi`() {
        val s = VoiceUtteranceLog.json("x", VoiceUtteranceLog.Meta(heard = "a\nb\"c"))
        // Hai chuỗi tìm kiếm là **hai ký tự** mỗi cái: `\` + `n`, và `\` + `"` — tức dạng ĐÃ THOÁT nằm trong JSON.
        assertTrue(s.contains("\\n"), "xuống dòng phải được thoát")
        assertTrue(s.contains("\\\""), "dấu nháy phải được thoát")
        assertFalse(s.contains("a\nb"), "không được để xuống dòng THÔ lọt vào JSON — `json.load` sẽ gãy")
    }
}
