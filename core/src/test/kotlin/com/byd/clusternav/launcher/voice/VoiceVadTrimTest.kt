package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CẮT CỬA SỔ MIC (`head`) — số học thuần, kiểm off-car ════════════════════════════════════════════════════
 *
 * Bằng chứng: `docs/diagnostics/voice-stream-eval-2026-09-16.md` §6 · §8.
 *
 * Bài này khoá **một phép tính ba dòng mà sai thì không có gì báo**: cắt hụt là mất nửa sau câu nói, cắt thừa là
 * rơi lại đúng cái bẫy §6 (đuôi im lặng kéo 22/25 → 6/25 trên chính mô hình đang ship). Cả hai kiểu sai đều cho
 * ra một chuỗi *trông như* một câu, nên không lượt chạy nào sẽ tự tố cáo chúng.
 */
class VoiceVadTrimTest {

    private val rate = 16_000

    private fun seg(startMs: Int, lenMs: Int) =
        VoiceVadTrim.Segment(VoiceVadTrim.msToSamples(startMs, rate), VoiceVadTrim.msToSamples(lenMs, rate))

    private fun ms(samples: Int) = VoiceVadTrim.samplesToMs(samples, rate)

    // ══ NGANG BẰNG với `scripts/voice/stream-matrix.py` (chế độ `head`) ═══════════════════════════════════

    /**
     * ⚠ Bài này khẳng định **số học**, không chạy script host.
     *
     * Script là Python + sherpa-onnx + 1 899 WAV; gọi nó từ một bài JUnit là biến `:core:test` thành thứ cần một
     * venv và 2 GB mô hình — tức nó sẽ bị tắt, và một bài test bị tắt tệ hơn không có. Nên chỗ nối giữa hai bên
     * là **công thức**, và bài này ghim đúng công thức ấy bằng các con số chép tay từ script:
     * ```python
     * end = vad.front.start + len(vad.front.samples) + int(margin * sr)
     * seg = audio[:max(1, min(len(audio), end))]
     * ```
     * Đổi công thức ở một bên mà quên bên kia ⇒ bài này đỏ, và người sửa đọc được ngay bên nào lệch.
     */
    @Test
    fun `cong thuc head trung khop voi script host`() {
        // Ca thường của bộ đo: cửa sổ 3 s, tiếng từ 200 ms tới 1 400 ms.
        val window = VoiceVadTrim.msToSamples(3_000, rate)
        val segments = listOf(seg(200, 1_200))
        val expected = VoiceVadTrim.msToSamples(1_400, rate)   // start + length, margin 0
        assertEquals(expected, VoiceVadTrim.headTrimSamples(segments, window))
        // …và `head` KHÁC hẳn hai cách kia — đó là toàn bộ lý do nó được chọn (§8: 21/25 vs 18/25 vs 8/25).
        assertTrue(
            VoiceVadTrim.headTrimSamples(segments, window) < window,
            "`head` phải cắt bớt đuôi, nếu không nó chính là `window`",
        )
        assertTrue(
            VoiceVadTrim.headTrimSamples(segments, window) > segments[0].startSample,
            "`head` phải giữ TRỌN phần đầu cửa sổ, nếu không nó chính là `segment` (nuốt mất từ đầu câu)",
        )
    }

    @Test
    fun `margin duoc cong vao dung nhu script`() {
        val window = VoiceVadTrim.msToSamples(3_000, rate)
        val segments = listOf(seg(200, 1_200))
        val margin = VoiceVadTrim.msToSamples(250, rate)
        assertEquals(
            VoiceVadTrim.msToSamples(1_650, rate),
            VoiceVadTrim.headTrimSamples(segments, window, margin),
        )
        // Nhưng bộ đã chốt là margin 0 — chừa thêm đuôi là đi ngược đúng phát hiện §6.
        assertEquals(0, VoiceVadTrim.MARGIN_MS)
    }

    // ══ Các ca biên ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * ⚠ **Ca hai đoạn** — chỗ bài này khác script một cách có chủ ý, và là ca duy nhất sai được mà im lặng.
     *
     * Người ta ngắt giữa câu ("bật đèn đọc … và mở kính") ⇒ VAD chốt hai đoạn. Lấy đoạn **đầu** (như `front` của
     * script, thứ ở đó luôn là đoạn duy nhất) sẽ cắt mất vế sau: không lỗi, không cảnh báo, chỉ là Kachi hiểu
     * một nửa. Lấy đoạn **cuối** thì ca một-đoạn vẫn ra đúng con số của script.
     */
    @Test
    fun `hai doan thi cat toi HET doan CUOI, khong phai doan dau`() {
        val window = VoiceVadTrim.msToSamples(5_000, rate)
        val segments = listOf(seg(200, 800), seg(1_600, 900))   // 200–1 000 ms và 1 600–2 500 ms
        assertEquals(VoiceVadTrim.msToSamples(2_500, rate), VoiceVadTrim.headTrimSamples(segments, window))
        // Thứ tự VAD trả ra không được ảnh hưởng kết quả (hàng đợi có thể rút ra theo thứ tự khác).
        assertEquals(
            VoiceVadTrim.headTrimSamples(segments, window),
            VoiceVadTrim.headTrimSamples(segments.reversed(), window),
        )
    }

    /** Không đoạn nào ⇒ nạp NGUYÊN cửa sổ — cùng nhánh `if seg is None: seg = audio` của script. */
    @Test
    fun `khong co doan nao thi nap nguyen cua so`() {
        val window = VoiceVadTrim.msToSamples(4_200, rate)
        assertEquals(window, VoiceVadTrim.headTrimSamples(emptyList(), window))
    }

    /** Đoạn dài hơn cửa sổ (VAD đếm mẫu của chính nó) ⇒ kẹp về cửa sổ, không bao giờ đọc ra ngoài mảng. */
    @Test
    fun `khong bao gio tra ve qua do dai cua so`() {
        val window = VoiceVadTrim.msToSamples(1_000, rate)
        val segments = listOf(seg(0, 5_000))
        assertEquals(window, VoiceVadTrim.headTrimSamples(segments, window))
    }

    @Test
    fun `luon tra ve it nhat mot mau khi cua so khac rong`() {
        val window = VoiceVadTrim.msToSamples(2_000, rate)
        assertEquals(1, VoiceVadTrim.headTrimSamples(listOf(VoiceVadTrim.Segment(0, 0)), window))
        // Cửa sổ rỗng thì không có gì để giải mã — 0, không phải 1 (đừng bịa ra một mẫu không tồn tại).
        assertEquals(0, VoiceVadTrim.headTrimSamples(listOf(VoiceVadTrim.Segment(0, 0)), 0))
    }

    // ══ Ba ca của bộ E2E: tệp đã cắt sát vs tệp có đuôi ═══════════════════════════════════════════════════

    /**
     * Tệp **đã cắt sát tiếng** (`w01`–`w25`) ⇒ phép cắt gần như **no-op**; tệp có đuôi (`w26`–`w28`) ⇒ cắt thật.
     *
     * Đây là tính chất khiến bộ đo cũ **không được phép xê dịch** khi thêm phép cắt: nếu nó cắt cả những tệp đã
     * sát thì 22/25 sẽ đổi, và lúc ấy không ai biết con số mới nói về phép cắt hay về một hồi quy.
     */
    @Test
    fun `tep da cat sat gan nhu khong bi dong, tep co duoi thi bi cat that`() {
        // w-cũ: 1,4 s tiếng, tệp dài 1,45 s ⇒ mất ≤ 50 ms.
        val tight = VoiceVadTrim.msToSamples(1_450, rate)
        val tightTrim = VoiceVadTrim.headTrimSamples(listOf(seg(50, 1_350)), tight)
        assertTrue(ms(tight - tightTrim) <= 50, "tệp đã cắt sát chỉ được mất ≤ 50 ms, mất ${ms(tight - tightTrim)} ms")
        // w26: cùng câu, nối thêm 2 s im lặng ⇒ phải bỏ đúng khoảng 2 s.
        val withTail = VoiceVadTrim.msToSamples(3_450, rate)
        val tailTrim = VoiceVadTrim.headTrimSamples(listOf(seg(50, 1_350)), withTail)
        assertEquals(tightTrim, tailTrim, "cùng câu + cùng đoạn tiếng ⇒ cắt ra CÙNG một khúc, bất kể đuôi dài bao nhiêu")
        assertTrue(ms(withTail - tailTrim) >= 1_900, "phải bỏ ~2 s đuôi, mới bỏ ${ms(withTail - tailTrim)} ms")
    }

    // ══ Tham số đã chốt bằng lưới ═════════════════════════════════════════════════════════════════════════

    /** Mặc định phải bằng ĐÚNG bộ đã đo (§8) — đổi một con số ở đây là đổi một kết luận đã có bằng chứng. */
    @Test
    fun `tham so mac dinh bang dung bo da chot tren host`() {
        assertEquals(0.5f, VoiceVadTrim.THRESHOLD)
        assertEquals(100, VoiceVadTrim.MIN_SPEECH_MS)     // 0,10 s
        assertEquals(150, VoiceVadTrim.MIN_SILENCE_MS)    // 0,15 s
        assertEquals(0, VoiceVadTrim.MARGIN_MS)
        assertEquals(512, VoiceVadTrim.WINDOW_SIZE)       // Silero v4 @16 kHz, cũng là mặc định của AAR 1.13.8
    }

    @Test
    fun `moi mac dinh nam trong chinh dai cua no`() {
        assertTrue(VoiceVadTrim.THRESHOLD in VoiceVadTrim.MIN_THRESHOLD..VoiceVadTrim.MAX_THRESHOLD)
        assertTrue(VoiceVadTrim.MIN_SPEECH_MS in VoiceVadTrim.MIN_MIN_SPEECH_MS..VoiceVadTrim.MAX_MIN_SPEECH_MS)
        assertTrue(VoiceVadTrim.MIN_SILENCE_MS in VoiceVadTrim.MIN_MIN_SILENCE_MS..VoiceVadTrim.MAX_MIN_SILENCE_MS)
        // Ngưỡng im của VAD phải NHỎ HƠN HẲN của bộ RMS: Silero biết "có phải giọng người không" nên dám chốt
        // sớm ([ĐO] 0/1 899 cắt giữa câu), còn RMS chỉ biết to/nhỏ nên phải chờ 800 ms.
        assertTrue(
            VoiceVadTrim.MIN_SILENCE_MS < VoiceEndpointer.HANGOVER_MS,
            "VAD chốt sớm hơn RMS — đó là toàn bộ lý do nó thành đường chính",
        )
    }

    @Test
    fun `doi don vi ms sang giay va sang mau khong lam tron sai`() {
        assertEquals(0.15f, VoiceVadTrim.msToSeconds(150))
        assertEquals(0.10f, VoiceVadTrim.msToSeconds(100))
        assertEquals(1_600, VoiceVadTrim.msToSamples(100, rate))
        assertEquals(100, VoiceVadTrim.samplesToMs(1_600, rate))
        // Vòng tròn ms → mẫu → ms phải đứng yên ở mọi mốc thật của bộ tham số.
        listOf(40, 80, 100, 150, 500, 800, 8_400).forEach {
            assertEquals(it, VoiceVadTrim.samplesToMs(VoiceVadTrim.msToSamples(it, rate), rate), "mốc $it ms")
        }
        assertEquals(0, VoiceVadTrim.samplesToMs(1_600, 0), "chia cho 0 phải trả 0, không được ném")
    }
}
