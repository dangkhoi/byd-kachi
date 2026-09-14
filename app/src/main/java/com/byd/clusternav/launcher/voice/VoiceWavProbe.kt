package com.byd.clusternav.launcher.voice

import android.content.Context
import android.os.Environment
import android.util.Log
import com.byd.clusternav.Lang
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * ═══ V1 pha NGHE · ĐƯỜNG CHỨNG MINH **KHÔNG CẦN MICRO** ══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R14**. Đẩy một tệp WAV qua **đúng** [VoiceRecognizer] mà micro
 * dùng, rồi trả về chữ nghe được.
 *
 * ## Vì sao thứ này phải tồn tại, và vì sao nó không phải "mã cho demo"
 * Máy ảo không có micro thật, còn xe thì [ĐO] tắt WiFi ngay khi cắm CarPlay/Android Auto (CLAUDE.md §11) — tức
 * đúng lúc cần đo thì adb từ ngoài không vào được. Không có đường này thì câu hỏi *"mô hình + ngữ pháp có nghe
 * ra câu lệnh không"* chỉ trả lời được bằng cách ngồi lên xe và nói — một vòng thử mất hàng chục phút cho mỗi
 * lần sửa một cụm từ. Với nó, cùng một câu hỏi trả lời được bằng một tệp và một cú bấm, **trên cùng con đường
 * mã** mà phiên nghe thật đi qua. Nó cũng chính là thứ khoá lời hứa *"nhận dạng chạy tại máy"*: không mạng,
 * không dịch vụ hệ thống, chỉ một tệp và một thư viện.
 *
 * ## Định dạng nhận: WAV PCM 16-bit · 1 kênh · 16 kHz
 * Không có bộ chuyển đổi ở đây **có chủ ý**: tự lấy mẫu lại trong app nghĩa là phép đo chạy qua một tầng mà
 * phiên nghe thật KHÔNG có, và kết quả đo sẽ không nói gì về phiên thật. Tệp sai khuôn ⇒ nói thẳng sai chỗ nào.
 */
object VoiceWavProbe {

    private const val TAG = "KachiVoiceWav"

    /** Tên tệp thử — một tên, nhiều chỗ tìm (xem [candidates]). */
    const val FILE_NAME = "kachi-voice-test.wav"

    /** Kết quả một lượt thử. */
    data class Result(val path: String, val heard: String, val error: String?)

    /** Trần cho khối `fmt ` — WAVE_FORMAT_EXTENSIBLE dài 40 byte; dài hơn nữa là tệp lạ, bỏ qua phần dư. */
    private const val MAX_FMT_BYTES = 64

    /**
     * Nơi tìm tệp, **theo thứ tự**.
     *
     * 1. `Download/` — chỗ `adb push` mặc định, và là chỗ người ta nghĩ tới đầu tiên. Từ Android 10 app thường
     *    **không** đọc được nếu chưa có quyền bộ nhớ, nên nó có thể im lặng không thấy — vì thế mới có (2).
     * 2. Thư mục ngoài **của riêng app** (`/sdcard/Android/data/<gói>/files/`) — đọc được **không cần quyền
     *    nào**, và `adb push` tới đó vẫn chạy. Đây là đường dùng được ở mọi ROM.
     * 3. `filesDir` — chỗ cuối, dùng khi đẩy tệp bằng kênh shell của chính app.
     */
    private fun candidates(ctx: Context): List<File> = listOfNotNull(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILE_NAME),
        ctx.getExternalFilesDir(null)?.let { File(it, FILE_NAME) },
        File(ctx.filesDir, FILE_NAME),
    )

    /** Đường dẫn tệp thử đầu tiên đọc được, `null` nếu không có chỗ nào. */
    fun findFile(ctx: Context): File? = candidates(ctx).firstOrNull { it.isFile && it.canRead() }

    /** Câu gợi ý *"đặt tệp ở đâu"* khi không tìm thấy — nêu ĐÍCH DANH đường dẫn, không nói chung chung. */
    fun whereToPut(ctx: Context): String =
        candidates(ctx).joinToString("  ·  ") { it.absolutePath }

    /**
     * Chạy một lượt. **CHẶN** (nạp mô hình + giải mã) ⇒ luồng nền.
     *
     * @param profiles · [apps] cùng danh sách động mà phiên nghe thật dùng ⇒ phép đo nói về đúng ngữ pháp thật.
     */
    fun run(ctx: Context, profiles: List<String>, apps: List<String>): Result {
        val file = findFile(ctx)
            ?: return Result("", "", Lang.t("không thấy tệp $FILE_NAME", "no $FILE_NAME found"))
        val rec = VoiceRecognizer.open(ctx, profiles, apps)
            ?: return Result(file.absolutePath, "", Lang.t("mô hình chưa sẵn sàng", "model not ready"))
        return rec.use {
            runCatching { Result(file.absolutePath, decode(file, it), null) }
                .onFailure { t -> Log.w(TAG, "đọc WAV hỏng", t) }
                .getOrElse { t -> Result(file.absolutePath, "", t.message ?: t.javaClass.simpleName) }
        }
    }

    /** Đọc phần `data` của WAV rồi đẩy qua [rec] theo từng khối 200 ms — y hệt nhịp mà micro đẩy. */
    private fun decode(file: File, rec: VoiceRecognizer): String {
        file.inputStream().buffered().use { input ->
            val dataBytes = readHeader(input)
            val chunk = ShortArray(VoiceCapture.SAMPLE_RATE / 5)
            val raw = ByteArray(chunk.size * 2)
            var left = dataBytes
            while (left > 0) {
                val want = minOf(raw.size.toLong(), left).toInt()
                val n = input.readAtMost(raw, want)
                if (n <= 0) break
                val samples = n / 2
                for (i in 0 until samples) {
                    // WAV PCM là little-endian có dấu.
                    chunk[i] = ((raw[2 * i].toInt() and 0xFF) or (raw[2 * i + 1].toInt() shl 8)).toShort()
                }
                if (rec.accept(chunk, samples)) return rec.result()
                left -= n
            }
        }
        return rec.finalResult()
    }

    /**
     * Đọc header RIFF/WAVE, kiểm khuôn, trả **số byte** của khối `data` và để luồng đứng ngay đầu khối đó.
     *
     * Duyệt từng khối thay vì giả định `fmt ` rồi `data` liền nhau: tệp do `afconvert`/`ffmpeg` sinh ra hay có
     * thêm khối `LIST`/`fact` ở giữa, và bỏ qua chúng bằng một hằng offset là cách hỏng ngay ở tệp thứ hai.
     */
    @Suppress("MagicNumber", "ThrowsCount")
    private fun readHeader(input: InputStream): Long {
        val riff = ByteArray(12)
        if (input.readAtMost(riff, 12) != 12) throw IOException("tệp quá ngắn")
        if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") throw IOException("không phải WAV")
        var seenFmt = false
        val head = ByteArray(8)
        while (true) {
            if (input.readAtMost(head, 8) != 8) throw IOException("không thấy khối `data`")
            val id = String(head, 0, 4)
            val size = le32(head, 4)
            if (id == "fmt ") {
                val fmt = ByteArray(size.toInt().coerceAtMost(MAX_FMT_BYTES))
                input.readAtMost(fmt, fmt.size)
                val format = le16(fmt, 0); val channels = le16(fmt, 2)
                val rate = le32(fmt, 4); val bits = le16(fmt, 14)
                if (format != 1) throw IOException("WAV phải là PCM không nén (đang là mã $format)")
                if (channels != 1) throw IOException("WAV phải 1 kênh (đang là $channels)")
                if (rate != VoiceCapture.SAMPLE_RATE.toLong()) {
                    throw IOException("WAV phải ${VoiceCapture.SAMPLE_RATE} Hz (đang là $rate)")
                }
                if (bits != 16) throw IOException("WAV phải 16-bit (đang là $bits)")
                seenFmt = true
                if (size > fmt.size) input.skipExactly(size - fmt.size)
            } else if (id == "data") {
                if (!seenFmt) throw IOException("khối `data` đứng trước `fmt `")
                return size
            } else {
                input.skipExactly(size + (size and 1L))     // khối lẻ byte được đệm thêm 1
            }
        }
    }

    /**
     * Đọc tối đa [want] byte, **lặp cho tới khi đủ hoặc hết luồng**. Trả số byte đã đọc.
     *
     * ## ⚠ Vì sao KHÔNG dùng `InputStream.readNBytes`
     * [ĐO] máy ảo API 29, 2026-09-14: `java.lang.NoSuchMethodError: No virtual method readNBytes([BII)I in class
     * Ljava/io/InputStream;`. `readNBytes` là API của **Java 9**, Android chỉ có từ API 33 (và chỉ khi bật
     * desugaring). Nó **biên dịch sạch** với `compileSdk 37` rồi ngã lúc chạy trên `minSdk 29` — đúng họ lỗi mà
     * CLAUDE.md §3 nói tới (tin trí nhớ về nền tảng thay vì kiểm), chỉ khác là lần này nền tảng là JDK chứ không
     * phải AOSP.
     *
     * `read(b, off, len)` **được phép trả ít hơn** [want] ngay cả khi luồng còn dữ liệu (đó là hợp đồng của nó),
     * nên phải lặp — dùng thẳng một lần `read` là mất mẫu một cách im lặng ở đúng những tệp lớn.
     */
    private fun InputStream.readAtMost(buf: ByteArray, want: Int): Int {
        var got = 0
        while (got < want) {
            val n = read(buf, got, want - got)
            if (n <= 0) break
            got += n
        }
        return got
    }

    /** Bỏ qua đúng [n] byte. `skip` được phép trả ít hơn ⇒ lặp; hết luồng giữa chừng là tệp cụt. */
    @Throws(IOException::class)
    private fun InputStream.skipExactly(n: Long) {
        var left = n
        while (left > 0) {
            val got = skip(left)
            if (got > 0) { left -= got; continue }
            // `skip` trả 0 không nhất thiết là hết luồng ⇒ thử đọc một byte để phân biệt.
            if (read() < 0) throw IOException("tệp WAV cụt giữa chừng")
            left--
        }
    }

    private fun le16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
            ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)
}
