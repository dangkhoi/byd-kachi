package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import com.byd.clusternav.Lang
import com.byd.clusternav.net.HttpConn
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * ═══ V1 pha NGHE · TẢI · KIỂM · GIẢI NÉN MÔ HÌNH NHẬN DẠNG ═══════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R9**. Bản kê (URL · sha256 · cỡ · tệp bắt buộc · luật chống leo
 * thư mục) nằm ở `:core` ([VoiceModelManifest]) và có bài canh off-car; tệp này chỉ **thi hành**.
 *
 * ## Bốn tính chất, mỗi cái chữa một ca đã thấy trong dự án
 *  1. **Không bao giờ để lại một thư mục nửa vời.** Giải nén vào [TMP_DIR] rồi mới đổi tên sang thư mục thật.
 *     Tiến trình bị giết giữa chừng (chuyện thường trên đầu xe) chỉ để lại rác trong `.tmp`, còn thư mục mô hình
 *     thì hoặc chưa có, hoặc đủ. Ca "có thư mục mà thiếu ruột" làm Vosk ngã bằng `KALDI_ERR` **trong mã native**
 *     — không `try/catch` Kotlin nào đỡ được trên vài ROM.
 *  2. **Băm TRONG lúc tải, không băm lại sau.** Tệp 32 MB; đọc lại lượt thứ hai là tốn gấp đôi I/O trên bộ nhớ
 *     của đầu xe. `DigestInputStream` cho cả hai kết quả trong một lượt.
 *  3. **Hỏng thì XOÁ rồi nói ra.** Giữ lại một gói hỏng để "lần sau thử tiếp" nghe hợp lý cho tới khi nó chiếm
 *     32 MB vĩnh viễn trên một máy 16 GB và không ai biết tại sao. Cũng là lý do [remove] có mặt.
 *  4. **Từ điển rút MỘT lần rồi ghi cạnh mô hình.** `graph/Gr.fst` nặng 25 MB; đọc header của nó mỗi lần mở
 *     phiên nghe là đọc thừa. Ghi ra `graph/words.txt` — đúng chỗ và đúng tên mà Vosk sẽ tự tìm nếu về sau ta
 *     đổi sang mô hình có sẵn tệp ấy ([VoiceModelManifest.WORDS_FILE]).
 *
 * ⚠ Mọi hàm có I/O ở đây **chặn** ⇒ chỗ gọi chịu trách nhiệm chạy trên luồng nền (xem `VoiceModelSettings`).
 */
object VoiceModelStore {

    private const val TAG = "KachiVoiceModel"

    /** Thư mục dựng dở — đổi tên sang thư mục thật ở bước cuối. Dấu `.` đầu để nó không bị nhầm là mô hình. */
    private const val TMP_DIR = "vosk/.staging"

    /** Thời hạn một lượt đọc khi tải mô hình. Dài hơn đọc JSON vì mỗi lượt là một khối 64 KB qua mạng xe. */
    private const val READ_TIMEOUT_MS = 60_000

    private const val MB = 1024L * 1024L

    /** Chỗ thở phải còn lại SAU khi cài xong — xem [spaceError]. */
    private const val SPACE_MARGIN_BYTES = 20L * 1024L * 1024L

    /** Tiến trình cài mô hình — một dòng chữ cho người dùng, không phải một enum để máy đọc. */
    sealed interface Step {
        /** Đang tải; [percent] = `-1` khi máy chủ không nói tổng cỡ. */
        data class Downloading(val percent: Int) : Step
        object Verifying : Step
        object Extracting : Step
        /** Xong. [words] = số từ trong từ điển mô hình — con số duy nhất chứng minh mô hình dùng được thật. */
        data class Done(val words: Int) : Step
        data class Failed(val reason: String) : Step
    }

    /** Thư mục mô hình đã cài. */
    fun dir(ctx: Context): File = File(ctx.applicationContext.filesDir, VoiceModelManifest.DIR)

    /**
     * Mô hình đã sẵn sàng chưa — kiểm **từng tệp bắt buộc**, không chỉ kiểm thư mục có tồn tại.
     *
     * Xem KDoc lớp, tính chất (1): một thư mục thiếu ruột là cách chắc chắn nhất để ngã trong mã native.
     */
    fun isReady(ctx: Context): Boolean {
        val root = dir(ctx)
        if (!root.isDirectory) return false
        return VoiceModelManifest.REQUIRED_FILES.all { File(root, it).isFile } &&
            File(root, VoiceModelManifest.WORDS_FILE).isFile
    }

    /** Cỡ thật đang chiếm trên đĩa (byte) — để màn Cài đặt nói đúng con số, không đọc lại [VoiceModelManifest]. */
    fun sizeOnDisk(ctx: Context): Long =
        dir(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Từ điển mô hình. Rỗng ⇒ chưa cài hoặc tệp hỏng (chỗ gọi **không** được bật mic — xem
     * [VoicePhrases.build]).
     *
     * ## [SOÁT Pass 2 · P2] Nhớ lại giữa các lần gọi, và khoá nhớ là **dấu vết tệp**
     * 19.529 dòng được đọc lại ở mọi chỗ hỏi: mỗi lần mở một phiên nghe (luồng nền — không sao), **và** mỗi lần
     * dựng trang *Nâng cao* của Cài đặt cùng mỗi bước `Done` của lượt tải (`VoiceModelSettings.statusText`) —
     * hai chỗ sau chạy trên **luồng vẽ**. Nhớ theo `đường dẫn|lastModified|length` chứ không theo một cờ boolean:
     * gỡ rồi cài lại cho ra một tệp khác, và một bộ nhớ đệm không tự biết điều đó sẽ phục vụ từ điển của mô hình
     * đã xoá.
     */
    fun words(ctx: Context): Set<String> {
        val f = File(dir(ctx), VoiceModelManifest.WORDS_FILE)
        if (!f.isFile) { cached = null; return emptySet() }
        val stamp = "${f.absolutePath}|${f.lastModified()}|${f.length()}"
        cached?.let { if (it.first == stamp) return it.second }
        val words = runCatching { f.bufferedReader().useLines { seq -> seq.filter { it.isNotBlank() }.toSet() } }
            .onFailure { Log.w(TAG, "đọc từ điển hỏng", it) }
            .getOrDefault(emptySet())
        if (words.isNotEmpty()) cached = stamp to words
        return words
    }

    /** Từ điển đã đọc + dấu vết của tệp sinh ra nó. `@Volatile`: đọc từ luồng vẽ lẫn luồng nghe. */
    @Volatile private var cached: Pair<String, Set<String>>? = null

    /** Gỡ mô hình (và mọi rác dựng dở). Trả `true` nếu sau lệnh này trên đĩa không còn gì. */
    fun remove(ctx: Context): Boolean {
        val files = ctx.applicationContext.filesDir
        cached = null                                 // từ điển trong bộ nhớ thuộc về tệp sắp bị xoá
        val ok = File(files, VoiceModelManifest.DIR).deleteRecursively()
        File(files, TMP_DIR).deleteRecursively()
        return ok
    }

    /**
     * Tải · kiểm · giải nén. **CHẶN** — gọi trên luồng nền.
     *
     * Thử lần lượt mọi URL trong [VoiceModelManifest.URLS]: nguồn đầu chết thì đi nguồn sau, và **mọi** nguồn
     * đều phải qua đúng một phép kiểm sha256 (một đường lùi dễ dãi hơn đường chính là một lỗ, không phải một
     * đường lùi).
     */
    @Suppress("ReturnCount")
    fun install(ctx: Context, onStep: (Step) -> Unit) {
        val app = ctx.applicationContext
        if (isReady(app)) { onStep(Step.Done(words(app).size)); return }
        // [SOÁT Pass 2 · P1] MỘT lượt cài tại một thời điểm. Nút trong Cài đặt tự khoá lúc đang chạy, nhưng nó
        // chỉ là một `TextView` của **trang đang dựng**: đóng màn Cài đặt rồi mở lại (hoặc một lượt
        // `invalidateSettings` do đổi hồ sơ) cho ra một nút MỚI, bật sẵn, trong khi luồng cũ vẫn đang tải. Hai
        // luồng cùng ghi `model.zip` rồi cùng `remove()` của nhau = hai lượt tải 32 MB, cả hai đều hỏng sha.
        if (!installing.compareAndSet(false, true)) {
            onStep(Step.Failed(Lang.t("đang cài rồi — đợi lượt này xong", "an install is already running")))
            return
        }
        try {
            spaceError(app)?.let { onStep(Step.Failed(it)); return }
            remove(app)                               // dọn rác của lần hỏng trước TRƯỚC khi chiếm thêm 32 MB
            val staging = File(app.filesDir, TMP_DIR)
            val zip = File(staging, "model.zip")
            staging.mkdirs()
            var lastError: String = Lang.t("không có nguồn nào", "no source available")
            for (url in VoiceModelManifest.URLS) {
                val err = tryOne(app, url, zip, staging, onStep)
                if (err == null) { onStep(Step.Done(words(app).size)); return }
                Log.w(TAG, "nguồn $url hỏng: $err")
                lastError = err
            }
            remove(app)
            onStep(Step.Failed(lastError))
        } finally {
            installing.set(false)
        }
    }

    /** Chốt "một lượt cài tại một thời điểm" — xem [install]. */
    private val installing = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Câu lỗi nếu đĩa không đủ chỗ, `null` nếu đủ.
     *
     * ## [SOÁT Pass 2 · P2] Vì sao phải hỏi TRƯỚC, không để `IOException` tự nói
     * Một lượt cài giữ **cả hai** bản cùng lúc: gói nén 32 MB trong `.staging` và 51 MB đã giải ra cạnh nó (gói
     * chỉ bị xoá sau khi giải nén xong). Hết chỗ giữa chừng trên đầu xe thì thứ người dùng nhận được là một câu
     * `ENOSPC` bằng tiếng Anh của libc sau khi đã tải xong 32 MB qua mạng 4G — mất tiền, mất mười phút, và không
     * ai biết phải xoá gì. Hỏi trước tốn một lời gọi `usableSpace`.
     *
     * Ngưỡng = gói + phần giải ra + [SPACE_MARGIN_BYTES] (chỗ thở cho nhật ký/prefs của chính app trong lúc tải).
     */
    private fun spaceError(app: Context): String? {
        val need = VoiceModelManifest.ZIP_BYTES + VoiceModelManifest.UNPACKED_BYTES + SPACE_MARGIN_BYTES
        val free = runCatching { app.filesDir.usableSpace }.getOrDefault(0L)
        // `0` cũng là thứ một ROM trả khi không đọc được ⇒ không chặn người dùng vì một phép đo không có.
        if (free <= 0L || free >= need) return null
        val needMb = need / MB
        val freeMb = free / MB
        return Lang.t(
            "máy còn $freeMb MB, cần khoảng $needMb MB — xoá bớt rồi thử lại",
            "only $freeMb MB free, about $needMb MB needed — free some space and retry",
        )
    }

    /** Một nguồn. Trả `null` khi xong, hoặc câu lỗi đọc được. */
    @Suppress("ReturnCount")
    private fun tryOne(ctx: Context, url: String, zip: File, staging: File, onStep: (Step) -> Unit): String? {
        onStep(Step.Downloading(0))
        val got = runCatching { download(url, zip, onStep) }
            .getOrElse { return Lang.t("lỗi mạng: ${it.message}", "network error: ${it.message}") }
            ?: return Lang.t("máy chủ từ chối", "server refused")

        onStep(Step.Verifying)
        if (!VoiceModelManifest.matches(got.sha256, got.bytes)) {
            zip.delete()
            return Lang.t(
                "gói tải về không khớp bản đã ghim (${got.bytes} byte, sha ${got.sha256.take(12)}…)",
                "downloaded package does not match the pinned one (${got.bytes} bytes, sha ${got.sha256.take(12)}…)",
            )
        }

        onStep(Step.Extracting)
        val out = File(staging, VoiceModelManifest.ID)
        out.deleteRecursively()
        runCatching { unzip(zip, out) }
            .onFailure { return Lang.t("giải nén hỏng: ${it.message}", "unpack failed: ${it.message}") }
        zip.delete()

        val missing = VoiceModelManifest.REQUIRED_FILES.filterNot { File(out, it).isFile }
        if (missing.isNotEmpty()) {
            out.deleteRecursively()
            return Lang.t("gói thiếu tệp: ${missing.first()}", "package is missing ${missing.first()}")
        }

        val words = runCatching { readWords(out) }
            .getOrElse { return Lang.t("không đọc được từ điển: ${it.message}", "cannot read vocabulary: ${it.message}") }
        if (words.isEmpty()) {
            out.deleteRecursively()
            return Lang.t("mô hình không có từ điển dùng được", "model carries no usable vocabulary")
        }
        File(out, VoiceModelManifest.WORDS_FILE).writeText(words.joinToString("\n"))

        val dest = dir(ctx)
        dest.parentFile?.mkdirs()
        dest.deleteRecursively()
        // Đổi tên là bước DUY NHẤT làm mô hình "xuất hiện" — xem KDoc lớp, tính chất (1).
        if (!out.renameTo(dest)) {
            out.deleteRecursively()
            return Lang.t("không chuyển được thư mục mô hình", "could not move the model folder")
        }
        staging.deleteRecursively()
        Log.i(TAG, "mô hình sẵn sàng: ${dest.absolutePath} (${words.size} từ)")
        return null
    }

    private data class Downloaded(val bytes: Long, val sha256: String)

    /** Tải một tệp, **băm trong lúc tải**. `null` = máy chủ trả mã lỗi. */
    private fun download(url: String, out: File, onStep: (Step) -> Unit): Downloaded? {
        val conn = HttpConn.open(url, READ_TIMEOUT_MS)
        try {
            if (conn.responseCode !in 200..299) return null
            // [SOÁT Pass 2 · P2] `HttpConn` chốt HTTPS ở địa chỉ ta GÕ VÀO; dòng này chốt địa chỉ ta THỰC SỰ đọc.
            // Cả hai nguồn đều trả 302 sang CDN (`instanceFollowRedirects = true`), và tuy tầng HTTP của Android
            // không tự đi từ https sang http, đó là hành vi của **thư viện** — không phải một lời hứa của dự án.
            // Kiểm lại bằng `conn.url` biến nó thành lời hứa của dự án.
            // (Chữ nghĩa ở đây cố ý tránh tên lớp `java.net` — bài canh "không tệp Voice* nào ra mạng" quét theo
            // chuỗi, và nó đúng khi bắt cả một dòng chú thích: chỗ duy nhất được mở kết nối là `HttpConn`.)
            if (!conn.url.protocol.equals("https", ignoreCase = true)) {
                throw IOException("máy chủ chuyển hướng sang ${conn.url.protocol}:// — từ chối")
            }
            val total = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            var lastPercent = -2
            out.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                out.outputStream().buffered().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        // Chỉ báo khi con số ĐỔI: `onStep` đi thẳng lên luồng vẽ, gọi nó 500 lần/giây là tự
                        // làm nghẽn màn hình bằng chính cái thanh tiến trình đang vẽ.
                        val pct = if (total > 0) ((read * 100) / total).toInt() else -1
                        if (pct != lastPercent) { lastPercent = pct; onStep(Step.Downloading(pct)) }
                    }
                }
            }
            return Downloaded(read, digest.digest().joinToString("") { "%02x".format(it) })
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Giải nén vào [dest].
     *
     * ⚠ Mỗi mục đi qua [VoiceModelManifest.safeEntryPath] — tên mục trong một gói tải từ Internet **là dữ liệu
     * của người khác** (CLAUDE.md §4.1). Mục không hợp lệ bị **bỏ qua có ghi nhật ký**, không làm hỏng cả lượt:
     * gói thật có mục thư mục (kết thúc `/`) mà ta không cần tạo riêng.
     */
    private fun unzip(zip: File, dest: File) {
        val root = dest.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val rel = VoiceModelManifest.safeEntryPath(entry.name)
                if (rel == null) {
                    if (!entry.isDirectory) Log.w(TAG, "bỏ mục không hợp lệ trong gói: ${entry.name}")
                    zin.closeEntry(); continue
                }
                val target = File(root, rel).canonicalFile
                // Lớp chắn THỨ HAI: dù luật ở `:core` có sơ hở, đường dẫn thật vẫn phải nằm dưới thư mục mô hình.
                if (!target.path.startsWith(root.path + File.separator)) {
                    throw IOException("mục `${entry.name}` trỏ ra ngoài thư mục mô hình")
                }
                target.parentFile?.mkdirs()
                target.outputStream().buffered().use { zin.copyTo(it, 64 * 1024) }
                zin.closeEntry()
            }
        }
    }

    /** Rút từ điển ra khỏi `graph/Gr.fst` — xem [VoskWordList] về vì sao mô hình này không có `words.txt`. */
    private fun readWords(modelDir: File): List<String> =
        File(modelDir, VoiceModelManifest.GRAPH_FST).inputStream().use { VoskWordList.readOutputSymbols(it) }
}
