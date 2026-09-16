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

    /**
     * Kết quả một lượt thử.
     *
     * @property heard câu **cuối cùng** (đã ghép lượt 2 nếu có) — đúng chuỗi mà phiên nghe thật đưa xuống bộ
     *   phân tích.
     * @property grammarText chữ của riêng lượt 1 (còn `[unk]`), `""` khi hỏng. Phơi ra vì phép đo cần thấy **cả
     *   hai** lượt: *"ngữ pháp nghe ra gì"* và *"tự do đọc thêm được gì"* là hai câu hỏi khác nhau, và trộn
     *   chúng vào một dòng là mất đúng thứ cần đo.
     * @property freeText chữ của lượt 2, `""` khi lượt 2 không chạy.
     */
    data class Result(
        val path: String,
        val heard: String,
        val error: String?,
        val grammarText: String = "",
        val freeText: String = "",
    )

    /** Trần cho khối `fmt ` — WAVE_FORMAT_EXTENSIBLE dài 40 byte; dài hơn nữa là tệp lạ, bỏ qua phần dư. */
    private const val MAX_FMT_BYTES = 64

    /**
     * Số mẫu đưa vào bộ giải mã sau khi cắt đuôi — **cùng chế độ `head`** với phiên nghe thật.
     *
     * Đẩy cả tệp qua Silero theo từng khối đúng cỡ khối micro ([VoiceCapture.CHUNK_SAMPLES]) để phép đo giống
     * phiên thật tới cả nhịp nạp, rồi `flush()` (tệp hết ⇒ đoạn cuối phải được chốt, y như lượt chạm trần).
     *
     * Không dựng được VAD ⇒ trả **nguyên** độ dài: đường đo thà nói về một cửa sổ chưa cắt còn hơn im lặng đổi
     * kết quả bằng một phép cắt không ai đo được (cùng luật [VoiceTurnEndpoint.trimSamples] ở đường lùi).
     */
    private fun trimSamples(ctx: Context, pcm: ShortArray, n: Int): Int {
        val vad = VoiceVad.open(ctx) ?: return n
        return vad.use {
            var at = 0
            while (at < n) {
                val len = minOf(VoiceCapture.CHUNK_SAMPLES, n - at)
                it.accept(pcm.copyOfRange(at, at + len), len)
                at += len
            }
            it.flush()
            it.headTrimSamples(n).also { t ->
                if (t < n) Log.i(TAG, "cắt đuôi WAV: $n → $t mẫu (bỏ ${(n - t) * 1000 / 16000} ms)")
            }
        }
    }

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
    fun run(ctx: Context, profiles: List<String>, apps: List<String>, installed: Set<String> = emptySet()): Result {
        val file = findFile(ctx)
            ?: return Result("", "", Lang.t("không thấy tệp $FILE_NAME", "no $FILE_NAME found"))
        val rec = VoiceRecognizer.open(ctx, profiles, apps, installed)
            ?: return Result(file.absolutePath, "", Lang.t("mô hình chưa sẵn sàng", "model not ready"))
        return runCatching {
            val pcm = readPcm(file)
            // ═══ CẮT ĐUÔI **cùng phép cắt** mà phiên nghe thật dùng ═══════════════════════════════════
            // Phiên thật nay cắt cửa sổ tại điểm hết tiếng (`head`, xem [VoiceVadTrim]); nếu đường đo này KHÔNG
            // cắt thì nó thôi nói về phiên thật — đúng thứ KDoc lớp cấm ("phép đo phải đi qua cùng con đường").
            //
            // ⚠ Với tệp đã cắt sát tiếng (mọi WAV `w01`–`w25` của bộ đo) đây gần như **no-op**: VAD chốt đoạn ở
            // sát cuối tệp nên `trim ≈ n`. Nó chỉ thật sự cắt ở những tệp CÓ đuôi — đúng ba ca `w26`/`w27`/`w28`
            // mà [ĐO máy ảo 1.69] cho ra *"bật đèn đọc **sách**"* và *"xem pin **và**"*: cùng dạng token mọc thêm
            // với *"đang đọc sách"* / *"mở cửa sổ **bật**"* trong log xe thật.
            val trimmed = trimSamples(ctx, pcm.first, pcm.second)
            val grammarText = rec.use { it.decodeAll(pcm.first, trimmed) }
            // ĐÚNG hai lượt như phiên nghe thật (R16) — phép đo phải đi qua cùng con đường, không phải một
            // đường rút gọn; nếu không thì nó không nói gì về phiên thật (xem KDoc lớp).
            val free = if (VoiceOpenVocab.triggerOf(grammarText) == null) {
                ""
            } else {
                VoiceRecognizer.openFree(ctx)?.use { it.decodeAll(pcm.first, trimmed) }.orEmpty()
            }
            val merged = VoiceOpenVocab.merge(grammarText, free)
            Result(file.absolutePath, merged.text, null, grammarText, free)
        }.onFailure { t -> Log.w(TAG, "đọc WAV hỏng", t) }
            .getOrElse { t -> Result(file.absolutePath, "", t.message ?: t.javaClass.simpleName) }
    }

    /**
     * Đọc phần `data` của WAV thành PCM16 trong RAM — trần [VoiceCapture.MAX_KEPT_SAMPLES], **cùng trần** với
     * khúc tiếng mà micro giữ lại, để phép đo không bao giờ nói về một khúc dài hơn thứ phiên thật xử lý được.
     *
     * @return mảng mẫu + số mẫu thật sự đọc được.
     */
    private fun readPcm(file: File): Pair<ShortArray, Int> {
        val out = ShortArray(VoiceCapture.MAX_KEPT_SAMPLES)
        return file.inputStream().buffered().use { input -> out to readSamples(input, out, readHeader(input)) }
    }

    /**
     * Ghép [dataBytes] byte PCM16 little-endian từ [input] vào [out], trả **số mẫu** đã ghép.
     *
     * `internal` để bài kiểm off-car gọi được thẳng bằng một luồng dựng tay — đó là cách duy nhất ép ra được ca
     * "khối lẻ byte" mà một tệp WAV bình thường không bao giờ cho.
     *
     * ## [SOÁT 2026-09-16 · P3] Byte LẺ phải được **treo sang lượt sau**, không được vứt
     * Bản trước ghép `n / 2` mẫu rồi trừ `left -= n`: một lượt đọc trả về **số byte lẻ** làm byte cao của mẫu
     * cuối bị **vứt đi trong khi vị trí luồng đã đi qua nó** ⇒ từ đó trở đi mọi mẫu được ghép từ một cặp byte
     * **lệch một** — tức tiếng thành nhiễu, im lặng, ở giữa cửa sổ đo.
     *
     * ⚠ **Mức bằng chứng** (CLAUDE.md §2): với [readAtMost] hiện tại (nó **lặp** tới khi đủ hoặc hết luồng)
     * ca ấy **[SUY] không tới được** — `n` lẻ chỉ xảy ra khi (a) luồng cạn giữa một mẫu, hoặc (b) `want` lẻ, mà
     * `want` chỉ lẻ ở khối `data` lẻ byte, tức lượt cuối. Cả hai đều là lượt **cuối cùng**, nên không còn mẫu
     * nào để làm lệch. Nhưng điều đó chỉ đúng **nhờ một chi tiết của một hàm khác** — một bất biến ngầm giữa
     * hai hàm, đúng họ lỗi mà CLAUDE.md §3 cảnh báo. Vòng lặp này nay tự đúng, không mượn bảo đảm của ai.
     */
    internal fun readSamples(input: InputStream, out: ShortArray, dataBytes: Long): Int {
        var at = 0
        var left = dataBytes
        var carry = -1                                          // byte THẤP còn treo từ lượt trước; -1 = không có
        val raw = ByteArray(VoiceCapture.SAMPLE_RATE / 5 * 2)
        while (left > 0 && at < out.size) {
            val want = minOf(raw.size.toLong(), left).toInt()
            val n = input.readAtMost(raw, want)
            if (n <= 0) break
            var i = 0
            // WAV PCM là little-endian có dấu.
            if (carry >= 0) { out[at++] = (carry or (raw[i++].toInt() shl 8)).toShort(); carry = -1 }
            while (i + 1 < n && at < out.size) {
                out[at++] = ((raw[i].toInt() and 0xFF) or (raw[i + 1].toInt() shl 8)).toShort()
                i += 2
            }
            if (i < n && at < out.size) carry = raw[i].toInt() and 0xFF
            left -= n
        }
        return at
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
