package com.byd.clusternav.launcher.voice

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.byd.clusternav.Lang
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.testbridge.TestBridgeJson
import com.byd.clusternav.voiceKeepLog
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ═══ H2 — NHẬT KÝ LƯỢT NÓI: một tệp WAV + một tệp JSON cho MỖI phiên nghe ════════════════════════════════════
 *
 * Owner 2026-09-16: thứ còn thiếu để chỉnh đường nghe không phải thêm ý tưởng, mà là **tiếng thật trên đường**.
 * Off-car dự án chỉ có 25 tệp TTS macOS, và CLAUDE.md §2 đã ghi thẳng: số đo trên tập ấy nói về *model + hotword*,
 * **không** nói về giọng thật + mic 4 kênh + 80 km/h + điều hoà + nhạc. Mỗi chuyến đi không ghi lại là một chuyến
 * phải lái lại — và mỗi lần lái lại, cabin đã khác.
 *
 * ## Vì sao CẶP tệp, không phải một tệp
 *  • **`<stamp>.wav`** là thứ chạy lại được: `scripts/voice/replay-car-log.py` đưa đúng khúc tiếng ấy qua
 *    sherpa trên host với mọi cấu hình hotword/beam muốn thử. Một bản ghi chữ thì không thử lại được gì.
 *  • **`<stamp>.json`** là thứ **xe đã nghe ra** cùng lúc: chữ, ý định, câu trả lời, mốc giờ, nguồn micro, mô hình,
 *    số cụm hotword, các số của bộ ngắt câu. Không có nó thì host chạy lại ra một kết quả mà không có gì để so.
 *
 * Hai nửa ấy phải đi cùng nhau: câu hỏi đáng giá duy nhất là *"host nghe khác xe ở chỗ nào, và vì sao"*.
 *
 * ## Vòng đệm: **30 mục VÀ 30 MB**, cũ trước — hai trần, hai kiểu tràn khác nhau
 * Trần theo SỐ MỤC một mình không đủ: 30 lượt 9 giây là ~17 MB, nhưng một ROM đọc khối lệch chuẩn có thể cho ra
 * những khúc dài hơn dự tính. Trần theo BYTE một mình cũng không đủ: 30 MB chia cho những lượt hai từ là hàng
 * trăm cặp tệp, tức một thư mục mà `adb pull` và chính vòng dọn này phải duyệt mỗi lượt nói. Nên cả hai, và mục
 * cũ nhất đi trước.
 *
 * ⚠ Dọn theo **mốc (stamp)**, không theo từng tệp: một tiến trình bị giết giữa chừng để lại `X.wav` không có
 * `X.json` (hoặc ngược lại). Dọn theo tệp thì cặp ấy tan ra và phần còn lại sống mãi như một mục vô nghĩa; dọn
 * theo mốc thì cả cặp đi cùng nhau, và một mốc **nửa vời cũng là một mốc** (nó vẫn chiếm chỗ, vẫn phải bị tính).
 *
 * ## Ba ràng buộc không được vi phạm
 *  1. **Không chạm mạng.** Bài canh `khong tep Voice nao gui tieng noi ra mang` quét đúng tệp này. Tiếng rời khỏi
 *     xe CHỈ khi người dùng tự bấm *Xuất nhật ký voice* (hoặc lệnh `voice_dump`) — một tệp zip trên thẻ, do họ
 *     cầm đi, không phải một lượt gửi.
 *  2. **Không I/O trên luồng gọi.** Mọi lượt ghi/dọn/nén nằm trên [io] (một luồng nền daemon). [ĐO xe 1.68]
 *     ngân sách `KachiPerf`: shell 27 lần/phút · log 9,8 KB/phút — một lượt ghi 300 KB trên luồng vẽ ở đúng
 *     khoảnh khắc người lái vừa nói xong là thứ người ta cảm thấy ngay.
 *  3. **Một lượt ghi cho một PHIÊN**, không phải cho một khối. Micro cho ra 5 khối/giây; ghi theo khối là biến
 *     một tính năng chẩn đoán thành một nguồn tải đĩa thường trực.
 */
object VoiceUtteranceLog {

    private const val TAG = "KachiVoiceLog"

    /** Thư mục trong **bộ nhớ riêng** của app (`filesDir`) — app khác trên xe không đọc được. */
    const val DIR_NAME = "voice-log"

    /** Trần số mục (một mục = một cặp `<stamp>.wav` + `<stamp>.json`). */
    const val MAX_ENTRIES = 30

    /** Trần dung lượng cả thư mục. */
    const val MAX_BYTES = 30L * 1024L * 1024L

    /** PCM16 · mono · 16 kHz — cùng số với [VoiceCapture.SAMPLE_RATE] và với mô hình. */
    const val SAMPLE_RATE = 16_000

    /** Khuôn RIFF/WAVE tối giản: 12 byte RIFF + 24 byte `fmt ` + 8 byte `data`. */
    const val WAV_HEADER_BYTES = 44

    private const val BITS_PER_SAMPLE = 16
    private const val CHANNELS = 1
    private const val PCM_FORMAT = 1

    /** Tên tệp zip xuất ra thẻ: `kachi-voice-<stamp>.zip`. */
    const val EXPORT_PREFIX = "kachi-voice-"
    private const val ZIP_MIME = "application/zip"

    /**
     * Mọi thứ **không phải tiếng** của một lượt nói.
     *
     * Một `data class` phẳng chứ không phải mười tham số: lượt ghi xảy ra ở hai thì (ngay sau khi nghe xong, rồi
     * lại sau khi thi hành xong — xem [record] / [update]), và một danh sách tham số dài truyền hai lần là đúng
     * chỗ hai lời gọi lệch nhau mà không ai thấy.
     */
    data class Meta(
        /** Chữ THÔ của lượt 1 (ngữ pháp + biasing). */
        val heard: String = "",
        /** Câu cuối cùng đưa xuống bộ phân tích (sau lượt 2 tự do, nếu có). */
        val sentence: String = "",
        /** Hằng `MediaRecorder.AudioSource` thật sự mở được — xem [VoiceMicSource]. */
        val micSource: Int = 0,
        val micSourceName: String = "",
        /** Id mô hình đang chọn ([SherpaModelCatalog.SherpaModel.id]). */
        val modelId: String = "",
        /** Số dòng hotword đã bơm vào phiên (0 = chạy không biasing). */
        val hotwords: Int = 0,
        /** Bộ ngắt câu chốt ở mốc nào (ms kể từ lúc mở micro). */
        val endpointMs: Int = 0,
        /** Tổng thời gian CÓ TIẾNG (ms). */
        val speechMs: Int = 0,
        /** Quãng im liên tục lúc chốt (ms). */
        val silenceMs: Int = 0,
        /** `true` = bộ ngắt câu chốt; `false` = chạm trần cứng của phiên. */
        val endpointFired: Boolean = false,
        /** Micro mở bao lâu (ms). */
        val listenMs: Long = 0L,
        /** Lượt giải mã mất bao lâu (ms). */
        val decodeMs: Long = 0L,
        /** Mã loại của từng ý định hiểu ra (`OpenApp`, `Control`, `Unknown`…). */
        val intents: List<String> = emptyList(),
        /** Các dòng Kachi trả lời. */
        val replies: List<String> = emptyList(),
        /** Lượt này có đi qua một vòng **hỏi lại** không (V3 · R8). */
        val clarify: Boolean = false,
        /** Sau lượt này có mở một lượt **hội thoại** nối không (V3 · R9). */
        val followUp: Boolean = false,
    )

    /**
     * Một luồng nền daemon, **một** — không phải một Thread mỗi lượt.
     *
     * Vì sao một luồng tuần tự: [record] và [update] phải chạy **đúng thứ tự** (cái sau ghi đè tệp JSON cái trước
     * vừa tạo). Hai Thread rời thì thứ tự do bộ lập lịch quyết, và ca thua là JSON mất phần ý định/câu trả lời —
     * đúng nửa dữ liệu đáng giá nhất. Daemon để nó không giữ tiến trình sống.
     */
    private val io: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "KachiVoiceLog").apply { isDaemon = true } }
    }

    private val stampFormat get() = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)

    /** Thư mục nhật ký (chưa chắc tồn tại). */
    fun dir(ctx: Context): File = File(ctx.applicationContext.filesDir, DIR_NAME)

    /** Công tắc `voice_keep_log` — **mặc định BẬT** (xem KDoc `Prefs.voiceKeepLog`). */
    fun enabled(ctx: Context): Boolean = runCatching { Prefs.voiceKeepLog(ctx) }.getOrDefault(true)

    /**
     * Ghi một lượt: `<stamp>.wav` + `<stamp>.json`, rồi dọn vòng đệm. **Không chặn** — đẩy hết sang [io].
     *
     * @return mốc của lượt vừa ghi (để [update] gọi lại sau khi thi hành xong), hoặc `null` khi công tắc tắt /
     *   không có tiếng để ghi. `null` là một câu trả lời hợp lệ, không phải lỗi: chỗ gọi chỉ việc bỏ qua [update].
     */
    fun record(ctx: Context, pcm: ShortArray, samples: Int, meta: Meta): String? {
        if (!enabled(ctx)) return null
        if (samples <= 0) return null
        val app = ctx.applicationContext
        val stamp = stampFormat.format(Date())
        // ⚠ Chép khúc PCM NGAY trên luồng gọi: mảng `kept` của [VoiceCapture] thuộc về lượt nghe vừa xong và chỗ
        // gọi có thể dùng lại nó (lượt 2 tự do đọc chính mảng đó). Đẩy tham chiếu sang luồng nền rồi ghi muộn là
        // ghi một khúc tiếng đã bị ai đó ghi đè — và tệp WAV sẽ nghe *gần đúng*, kiểu sai khó thấy nhất.
        val copy = pcm.copyOf(minOf(samples, pcm.size))
        // Nhãn việc ASCII (cùng luật `PermissionReport.logLine`): nó chỉ đi vào logcat, và hai lượt đo trên hai
        // máy khác ngôn ngữ phải grep được bằng MỘT chuỗi.
        submit("ghi $stamp") {
            val d = dir(app)
            d.mkdirs()
            writeWav(File(d, "$stamp.wav"), copy)
            writeJson(File(d, "$stamp.json"), stamp, meta)
            prune(d)
        }
        return stamp
    }

    /**
     * Ghi lại phần JSON của một lượt đã có — dùng khi ý định/câu trả lời chỉ biết được SAU khi thi hành.
     *
     * [stamp] `null` (công tắc tắt) ⇒ không làm gì. Tệp WAV không bị đụng tới.
     */
    fun update(ctx: Context, stamp: String?, meta: Meta) {
        if (stamp == null) return
        val app = ctx.applicationContext
        submit("cap nhat $stamp") {
            val f = File(dir(app), "$stamp.json")
            // Tệp đã bị vòng đệm dọn mất (một phiên rất dài, nhiều lượt nối) ⇒ **không** dựng lại: dựng lại là để
            // một mục JSON mồ côi không có tiếng đi kèm, đúng thứ [prune] vừa dọn đi.
            if (f.isFile) writeJson(f, stamp, meta)
        }
    }

    // ── WAV ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Khuôn RIFF/WAVE 44 byte cho PCM16 mono [SAMPLE_RATE].
     *
     * Tách hàm (và công khai) vì đây là thứ **sai được mà tai không nghe ra**: lệch `byteRate` thì tệp mở vẫn kêu
     * nhưng sai cao độ, lệch `dataSize` thì một số bộ đọc cắt cụt câu cuối — và cả hai chỉ lộ ra khi phép đo trên
     * host cho kết quả khác xe mà không ai hiểu vì sao. Một bài kiểm so từng byte rẻ hơn nhiều một buổi truy vết.
     *
     * @param pcmBytes số byte dữ liệu PCM (= số mẫu × 2).
     */
    fun wavHeader(pcmBytes: Int): ByteArray {
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
        val out = ByteArray(WAV_HEADER_BYTES)
        var i = 0
        fun ascii(s: String) { s.forEach { out[i++] = it.code.toByte() } }
        fun le32(v: Int) { repeat(4) { out[i++] = (v shr (8 * it)).toByte() } }
        fun le16(v: Int) { repeat(2) { out[i++] = (v shr (8 * it)).toByte() } }
        ascii("RIFF")
        // 36 = phần còn lại của khuôn sau trường này (4 "WAVE" + 24 fmt + 8 data). Không phải một hằng bí ẩn:
        // nó là `WAV_HEADER_BYTES - 8`, và viết như vậy để lần ai đó thêm một chunk thì nó tự đúng.
        le32(WAV_HEADER_BYTES - 8 + pcmBytes)
        ascii("WAVE")
        ascii("fmt ")
        le32(16)                 // cỡ khối fmt của PCM
        le16(PCM_FORMAT)
        le16(CHANNELS)
        le32(SAMPLE_RATE)
        le32(byteRate)
        le16(blockAlign)
        le16(BITS_PER_SAMPLE)
        ascii("data")
        le32(pcmBytes)
        return out
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val bytes = ByteArray(pcm.size * 2)
        for (n in pcm.indices) {
            val v = pcm[n].toInt()
            bytes[n * 2] = v.toByte()
            bytes[n * 2 + 1] = (v shr 8).toByte()
        }
        f.outputStream().buffered().use { out ->
            out.write(wavHeader(bytes.size))
            out.write(bytes)
        }
    }

    // ── JSON ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * Một mục nhật ký, dạng JSON.
     *
     * Dùng [TestBridgeJson] chứ không dựng bộ ghi thứ hai: nó ở `:core`, thuần, đã có bài kiểm khuôn (kể cả phép
     * thoát ký tự điều khiển — câu người ta nói có thể mang `\n` từ bộ giải mã), và **đầu đọc cũng là một**:
     * `replay-car-log.py` đọc tệp này bằng `json.load` y như script đọc lời đáp của cầu kiểm thử.
     */
    fun json(stamp: String, m: Meta): String = TestBridgeJson.obj(
        listOf(
            "stamp" to stamp,
            "heard" to m.heard,
            "sentence" to m.sentence,
            "intents" to TestBridgeJson.Raw(TestBridgeJson.arr(m.intents)),
            "replies" to TestBridgeJson.Raw(TestBridgeJson.arr(m.replies)),
            "mic_source" to m.micSource,
            "mic_source_name" to m.micSourceName,
            "model_id" to m.modelId,
            "hotwords" to m.hotwords,
            "endpoint_ms" to m.endpointMs,
            "speech_ms" to m.speechMs,
            "silence_ms" to m.silenceMs,
            "endpoint_fired" to m.endpointFired,
            "listen_ms" to m.listenMs,
            "decode_ms" to m.decodeMs,
            "clarify" to m.clarify,
            "follow_up" to m.followUp,
        ),
    )

    private fun writeJson(f: File, stamp: String, m: Meta) = f.writeText(json(stamp, m))

    // ── Vòng đệm ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Dọn [d] về trong **cả hai** trần ([MAX_ENTRIES] · [MAX_BYTES]), mốc CŨ NHẤT đi trước.
     *
     * Thuần `java.io` (không `Context`) ⇒ kiểm off-device bằng một thư mục tạm, đúng khuôn [VoiceModelSideload].
     *
     * @return số mốc đã xoá.
     */
    fun prune(d: File): Int {
        val files = d.listFiles()?.filter { it.isFile } ?: return 0
        // Gom theo MỐC (tên tệp bỏ đuôi) — xem KDoc lớp về vì sao không gom theo tệp.
        val byStamp = files.groupBy { it.name.substringBeforeLast('.') }
        // Sắp theo chính tên mốc: khuôn `yyyyMMdd-HHmmss-SSS` **sắp chữ ra đúng thứ tự thời gian**, nên không
        // phải hỏi `lastModified()` — thứ mà một lượt `adb push`/chép thẻ có thể làm sai lệch.
        val stamps = byStamp.keys.sorted().toMutableList()
        var total = files.sumOf { it.length() }
        var removed = 0
        while (stamps.isNotEmpty() && (stamps.size > MAX_ENTRIES || total > MAX_BYTES)) {
            val oldest = stamps.removeAt(0)
            byStamp[oldest]?.forEach { f ->
                val len = f.length()
                if (runCatching { f.delete() }.getOrDefault(false)) total -= len
            }
            removed++
        }
        return removed
    }

    // ── Xuất ra thẻ ──────────────────────────────────────────────────────────────────────────────

    /** Kết quả một lượt xuất: đường dẫn đọc được cho người dùng, hoặc câu lỗi. */
    sealed interface Export {
        data class Ok(val path: String, val entries: Int, val bytes: Long) : Export
        data class Failed(val reason: String) : Export
    }

    /**
     * Nén cả `voice-log/` thành `Download/kachi-voice-<stamp>.zip`. **CHẶN** ⇒ gọi trên luồng nền.
     *
     * ## Vì sao thư mục Download CÔNG KHAI, không phải thư mục riêng của app
     * Cả hai đầu đọc đều ở ngoài app: người ta cắm USB rồi copy, hoặc mở Zalo chọn tệp. Một tệp trong
     * `Android/data/<gói>/files` thì trình quản lý tệp của ROM xe **không** duyệt tới được (và trên Android 11+
     * thì cả trình chọn tệp cũng không), tức nút này sẽ báo "xong" và đưa ra một đường dẫn không ai mở được —
     * đúng kiểu hỏng im lặng CLAUDE.md §11 nói tới.
     *
     * Đi qua `MediaStore.Downloads` (API 29+, không cần quyền nào) chứ không `File("/sdcard/Download")`: từ
     * Android 10 đường ghi thẳng ấy bị chặn, và thứ nó ném ra là `FileNotFoundException` — một câu lỗi không nói
     * gì về nguyên nhân thật. Không dựng được (ROM cắt MediaStore) ⇒ **lùi** về thư mục ngoài của app và nói rõ
     * đường dẫn, chứ không báo hỏng: một tệp khó lấy vẫn hơn không có tệp.
     */
    fun exportZip(ctx: Context): Export {
        val app = ctx.applicationContext
        val src = dir(app)
        val entries = src.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        if (entries.isEmpty()) {
            return Export.Failed(
                Lang.t(
                    "chưa có lượt nói nào được ghi — bật công tắc rồi nói một câu",
                    "no utterances recorded yet — turn the switch on and say something",
                ),
            )
        }
        val name = EXPORT_PREFIX + stampFormat.format(Date()) + ".zip"
        return runCatching { writeZipToDownloads(app, name, entries) }
            .getOrElse { t ->
                Log.w(TAG, "xuất nhật ký hỏng", t)
                Export.Failed("${t.javaClass.simpleName}: ${t.message}")
            }
    }

    private fun writeZipToDownloads(app: Context, name: String, entries: List<File>): Export {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, ZIP_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = app.contentResolver
        val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }
            .onFailure { Log.w(TAG, "MediaStore từ chối — lùi về thư mục ngoài của app", it) }
            .getOrNull()
        if (uri == null) return zipToAppDir(app, name, entries)
        val bytes = runCatching {
            val out = resolver.openOutputStream(uri) ?: throw FileNotFoundException(uri.toString())
            out.use { zipInto(it, entries) }
        }.getOrElse { t ->
            // Xoá mục MediaStore rỗng: để lại là để một tệp 0 byte trong Download mà người dùng sẽ mở rồi tưởng
            // nhật ký hỏng. `runCatching` vì xoá hỏng không được phép che mất lỗi THẬT ở dòng dưới.
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return Export.Ok("${Environment.DIRECTORY_DOWNLOADS}/$name", entries.size, bytes)
    }

    /** Đường LÙI khi ROM không cho ghi vào Download — vẫn ra một tệp thật, kèm đường dẫn tuyệt đối. */
    private fun zipToAppDir(app: Context, name: String, entries: List<File>): Export {
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val f = File(dir, name)
        val bytes = f.outputStream().use { zipInto(it, entries) }
        return Export.Ok(f.absolutePath, entries.size, bytes)
    }

    /** Nén [entries] vào [sink] (phẳng, không thư mục con). Trả tổng byte NGUỒN đã nén. */
    private fun zipInto(sink: java.io.OutputStream, entries: List<File>): Long {
        var read = 0L
        ZipOutputStream(sink.buffered()).use { zip ->
            entries.forEach { f ->
                // Một tệp biến mất giữa lượt nén (vòng đệm vừa dọn nó) KHÔNG được phép giết cả lượt xuất: bỏ qua
                // đúng tệp ấy và nén tiếp. Bắt `IOException` — không phải `Exception` (CLAUDE.md §4.1).
                runCatching {
                    zip.putNextEntry(ZipEntry(f.name))
                    read += f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }.onFailure { t ->
                    if (t !is IOException) throw t
                    Log.w(TAG, "bỏ qua ${f.name} trong lượt nén: ${t.javaClass.simpleName}")
                }
            }
        }
        return read
    }

    // ── tiện ích ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Đẩy một việc I/O sang [io], nuốt mọi hỏng hóc **có ghi lại**.
     *
     * Một tính năng chẩn đoán không bao giờ được giết một phiên nghe (cùng luật `VoiceSession.runSession`) — nhưng
     * cũng không được hỏng im lặng, vì lúc ấy người ta sẽ đi tìm lỗi ở chỗ khác. `IOException` là ca thường (đĩa
     * đầy, tệp bị dọn giữa chừng); mọi thứ khác cũng ghi lại đầy đủ vì nó là lỗi mã, không phải lỗi môi trường.
     */
    private fun submit(what: String, block: () -> Unit) {
        runCatching {
            io.execute {
                runCatching(block).onFailure { t ->
                    if (t is IOException) Log.w(TAG, "$what hỏng: ${t.javaClass.simpleName} ${t.message}")
                    else Log.w(TAG, "$what hỏng", t)
                }
            }
        }.onFailure { Log.w(TAG, "không xếp được việc '$what'", it) }
    }
}
