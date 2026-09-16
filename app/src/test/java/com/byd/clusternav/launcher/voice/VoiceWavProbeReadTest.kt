package com.byd.clusternav.launcher.voice

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT 2026-09-16 · P3] GHÉP PCM16: byte LẺ phải treo sang lượt sau, không được vứt ══════════════════════
 *
 * Bản trước của `VoiceWavProbe.readPcm` ghép `n / 2` mẫu rồi trừ **cả** `n`: một lượt đọc trả về **số byte lẻ**
 * làm byte cao của mẫu cuối bị vứt **trong khi vị trí luồng đã đi qua nó** ⇒ từ đó về sau mọi mẫu ghép từ một
 * cặp byte **lệch một** — tiếng thành nhiễu, im lặng, ở giữa cửa sổ đo. Và đường đo này chính là thứ trả lời
 * *"mô hình có nghe ra câu lệnh không"* mà không cần lên xe: nó nói sai thì cả một buổi chỉnh ngữ pháp đi sai.
 *
 * ⚠ **Mức bằng chứng** (CLAUDE.md §2): với một `BufferedInputStream` bình thường ca này **[SUY] không tới được**
 * — `readAtMost` lặp tới khi đủ nên `n` chỉ lẻ ở lượt CUỐI. Nó tới được khi luồng bên dưới trả `0` giữa chừng
 * (hợp đồng `InputStream` cho phép chốt lượt đọc sớm), và đó đúng là thứ bài này dựng. Giá trị thật của bài:
 * hàm ghép **tự đúng**, không mượn bảo đảm của một hàm khác.
 */
class VoiceWavProbeReadTest {

    /**
     * Luồng trả về **từng khúc [chunk] byte**, xen giữa là một lượt đọc trả `0` — đủ để `readAtMost` chốt lượt
     * ở một số byte LẺ trong khi luồng **vẫn còn dữ liệu**. Đây là ca mà một tệp WAV bình thường không cho.
     */
    private class OddChunkStream(private val data: ByteArray, private val chunk: Int) : InputStream() {
        private var at = 0
        private var stall = false

        override fun read(): Int = if (at < data.size) data[at++].toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (at >= data.size) return -1
            if (stall) { stall = false; return 0 }
            stall = true
            val n = minOf(chunk, len, data.size - at)
            System.arraycopy(data, at, b, off, n)
            at += n
            return n
        }
    }

    /** `shorts` → byte PCM16 little-endian, đúng khuôn khối `data` của WAV. */
    private fun le(vararg shorts: Int): ByteArray {
        val out = ByteArray(shorts.size * 2)
        shorts.forEachIndexed { i, s ->
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun `khuc doc le byte KHONG lam lech moi mau ve sau`() {
        val bytes = le(1, 2, 3, 4, 5, 6)
        val out = ShortArray(16)
        val n = VoiceWavProbe.readSamples(OddChunkStream(bytes, 3), out, bytes.size.toLong())
        assertEquals(6, n, "12 byte = 6 mẫu, bất kể luồng cắt khúc ở đâu")
        assertArrayEquals(
            shortArrayOf(1, 2, 3, 4, 5, 6),
            out.copyOf(n),
            "byte lẻ phải được treo sang lượt sau; vứt nó đi thì mẫu thứ hai trở đi lệch một byte (1, 768, …)",
        )
    }

    @Test
    fun `khuc chan — duong thuong khong doi mot li`() {
        val bytes = le(-2, -1, 0, 1, 32767)
        val out = ShortArray(16)
        val n = VoiceWavProbe.readSamples(ByteArrayInputStream(bytes), out, bytes.size.toLong())
        assertEquals(5, n)
        assertArrayEquals(shortArrayOf(-2, -1, 0, 1, 32767), out.copyOf(n), "PCM16 có dấu, little-endian")
    }

    @Test
    fun `luong cut giua mot mau thi dung, khong ghep bua mot mau nua`() {
        val bytes = le(7, 8) + byteArrayOf(9)          // 5 byte: hai mẫu đủ + một byte cụt
        val out = ShortArray(16)
        val n = VoiceWavProbe.readSamples(ByteArrayInputStream(bytes), out, bytes.size.toLong())
        assertEquals(2, n, "byte cuối không đủ một mẫu ⇒ bỏ, KHÔNG được độn 0 thành một mẫu ma")
        assertArrayEquals(shortArrayOf(7, 8), out.copyOf(n))
    }

    @Test
    fun `tran mang giu nguyen — khong bao gio ghi qua cho da cap`() {
        val bytes = le(1, 2, 3, 4, 5, 6)
        val out = ShortArray(2)
        val n = VoiceWavProbe.readSamples(OddChunkStream(bytes, 3), out, bytes.size.toLong())
        assertEquals(2, n, "trần `MAX_KEPT_SAMPLES` là trần cứng — cùng trần với khúc tiếng micro giữ lại")
        assertArrayEquals(shortArrayOf(1, 2), out)
    }
}
