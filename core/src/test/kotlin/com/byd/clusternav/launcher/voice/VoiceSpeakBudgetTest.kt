package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [ĐO xe 2026-09-18 §B] Lưới an toàn phải PHỦ được câu trả lời dài nhất, dưới CPU của xe ════════════════════
 *
 * Bài này canh đúng một tính chất: *"câu càng dài thì lưới càng dài"*. Bản 1.78 dùng hằng **10 s cứng**, và dưới
 * load 14 nó là thứ đóng tấm chữ **giữa lúc Piper đang đọc** (owner: *"chưa hết câu đã mất overlay"*).
 * Bằng chứng: `docs/diagnostics/oncar-voice-cases-findings-2026-09-18.md` §B.
 */
class VoiceSpeakBudgetTest {

    /** Sàn duy nhất của dự án — `VoiceSession.SPEAK_SAFETY_MS` chỉ là tên gọi của chính hằng này ở tầng Android. */
    private val floor = VoiceSpeakBudget.FLOOR_MS

    @Test
    fun `san la mot con so duy nhat, du de phu ca doan Piper tong hop`() {
        assertEquals(15_000L, VoiceSpeakBudget.FLOOR_MS, "sàn đổi ⇒ xem lại §B trước khi ghim số mới")
        assertTrue(
            VoiceSpeakBudget.FLOOR_MS > 10_000L,
            "10 s là con số của 1.78 và nó CẮT tấm chữ giữa câu trên xe — sàn phải cao hơn nó",
        )
    }

    @Test
    fun `cau ngan thi lay dung san, khong ngan hon`() {
        assertEquals(floor, VoiceSpeakBudget.estimateMs(listOf("Đã bật đèn đọc"), floor))
        assertEquals(floor, VoiceSpeakBudget.estimateMs(emptyList(), floor), "batch rỗng vẫn không dưới sàn")
        assertEquals(floor, VoiceSpeakBudget.estimateMs(listOf(""), floor))
    }

    /**
     * Ca THẬT làm lỗi lộ ra: một câu ghép ba vế. 10 s cứng của 1.78 không phủ nổi nó khi CPU bão hoà; công thức
     * theo độ dài thì phủ, và phủ với biên ~3× thời lượng đọc thật.
     */
    @Test
    fun `cau ghep dai thi luoi dai theo do dai cau`() {
        val batch = listOf(
            "✓ Mở Tất cả kính — chưa kiểm trên xe",
            "Đã đặt Nhiệt độ 24 độ",
            "Đã giảm Gió xuống mức 2",
        )
        val chars = batch.sumOf { it.length }
        val got = VoiceSpeakBudget.estimateMs(batch, floor)
        assertEquals(chars * VoiceSpeakBudget.MS_PER_CHAR + VoiceSpeakBudget.HEAD_ROOM_MS, got)
        assertTrue(got > floor, "câu ghép ba vế phải vượt sàn, nếu không công thức chẳng làm gì: $got")
        // Và phải phủ được ca 1.78 đã hỏng: 10 s là con số cũ, lưới mới bắt buộc dài hơn nó.
        assertTrue(got > 10_000L, "lưới mới phải dài hơn hằng 10 s đã cắt tấm chữ giữa câu trên xe: $got")
    }

    /**
     * **Đơn điệu tăng** — thêm chữ không bao giờ được làm lưới ngắn lại. Đây là tính chất duy nhất khiến công
     * thức đúng cho cả những câu chưa ai viết; một trần trên (nếu ai đó thêm) sẽ làm bài này đỏ, và đó là chủ ý:
     * trần trên = một câu đủ dài lại bị cắt giữa chừng, tức lỗi hôm nay quay lại ở một ngưỡng khác.
     */
    @Test
    fun `them chu khong bao gio lam luoi ngan lai`() {
        var prev = 0L
        var line = "a"
        repeat(12) {
            val got = VoiceSpeakBudget.estimateMs(listOf(line), floor)
            assertTrue(got >= prev, "độ dài ${line.length} cho lưới ${got} ms, ngắn hơn lượt trước $prev ms")
            prev = got
            line += "abcdefghij".repeat(3)
        }
    }

    /**
     * Biên so với thời lượng đọc THẬT phải còn ≥ 2×. [ĐO host] Piper đọc 11 từ (~50 ký tự) ra 2,11 s ⇒ ~42 ms/ký
     * tự khi CPU rảnh; xe thì chậm hơn, nên biên là toàn bộ lý do con số này hào phóng.
     */
    @Test
    fun `bien so voi thoi luong doc that con it nhat hai lan`() {
        assertTrue(
            VoiceSpeakBudget.MS_PER_CHAR >= 2 * 65L,
            "chỉ ${VoiceSpeakBudget.MS_PER_CHAR} ms/ký tự — hết biên cho một CPU đang bão hoà",
        )
    }
}
