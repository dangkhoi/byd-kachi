package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import com.byd.clusternav.Lang
import com.byd.clusternav.net.HttpConn
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * ═══ V2 pha NGHE · TẢI · KIỂM mô hình sherpa-onnx (NHIỀU tệp rời) ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html` §Design. Bản kê (URL · sha256 · cỡ từng tệp · model A/B) nằm ở
 * `:core` ([SherpaModelCatalog]) và có bài canh off-car; tệp này chỉ **thi hành**. Thay bản Vosk (một gói zip)
 * bằng đường **nhiều tệp ONNX rời** — không giải nén, mỗi tệp tự mang sha256 + kích thước.
 *
 * ## Bốn tính chất giữ nguyên tinh thần R9
 *  1. **Không để lại thư mục nửa vời.** Tải vào [TMP_DIR] rồi mới đổi tên sang thư mục thật. Tiến trình bị giết
 *     giữa chừng chỉ để rác trong `.staging`; thư mục mô hình hoặc chưa có, hoặc đủ tệp — onnxruntime không ngã
 *     bằng lỗi native khó bắt.
 *  2. **Băm TRONG lúc tải.** `DigestInputStream`-kiểu: đọc một lượt vừa ghi vừa băm, so ngay với bản ghim.
 *  3. **Hỏng thì XOÁ rồi nói ra.** Không giữ tệp cụt chiếm chỗ.
 *  4. **Từ chối model chưa ghim.** [SherpaModel.downloadable] = false (vd bản gated chưa mirror) ⇒ báo lý do,
 *     KHÔNG tải mù (fail-safe — CLAUDE.md §4.1).
 *
 * ⚠ Mọi hàm có I/O ở đây **chặn** ⇒ chỗ gọi chạy trên luồng nền (xem [VoiceModelSettings]).
 */
object VoiceModelStore {

    private const val TAG = "KachiVoiceModel"

    /** Thư mục dựng dở — đổi tên sang thư mục thật ở bước cuối. Dấu `.` đầu để không bị nhầm là mô hình. */
    private const val TMP_DIR = "sherpa/.staging"

    private const val READ_TIMEOUT_MS = 60_000
    private const val MB = 1024L * 1024L
    private const val SPACE_MARGIN_BYTES = 40L * MB

    /** Tiến trình cài mô hình — một dòng chữ cho người dùng. Giữ nguyên các nhánh để chữ trong Cài đặt khỏi đổi. */
    sealed interface Step {
        /** Đang tải; [percent] = `-1` khi máy chủ không nói tổng cỡ. */
        data class Downloading(val percent: Int) : Step
        object Verifying : Step
        object Extracting : Step
        /** Xong — [files] = số tệp đã đặt đúng chỗ. */
        data class Done(val files: Int) : Step
        data class Failed(val reason: String) : Step
    }

    /** Model đang chọn (A/B) — lưu ở prefs riêng, mặc định bản license-sạch tải-được ([SherpaModelCatalog.DEFAULT_ID]). */
    fun selected(ctx: Context): SherpaModelCatalog.SherpaModel =
        SherpaModelCatalog.byId(prefs(ctx).getString(KEY_MODEL, null))

    /** Đổi model đang chọn (Cài đặt A/B). Không tải — chỉ ghi lựa chọn; lần bật mic sau nạp bản mới nếu đã cài. */
    fun select(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_MODEL, id).apply()
    }

    /** Thư mục mô hình đang chọn. */
    fun dir(ctx: Context): File = File(ctx.applicationContext.filesDir, selected(ctx).dir)

    /** Đường dẫn tuyệt đối một tệp thành phần trong thư mục mô hình đang chọn. */
    fun filePath(ctx: Context, name: String): String = File(dir(ctx), name).absolutePath

    /**
     * Mô hình đang chọn đã sẵn sàng chưa — kiểm **từng tệp** (tồn tại + khác rỗng), không chỉ kiểm thư mục.
     * Một thư mục thiếu tệp là cách chắc chắn nhất để onnxruntime ngã trong mã native.
     */
    fun isReady(ctx: Context): Boolean {
        val model = selected(ctx)
        val root = dir(ctx)
        if (!root.isDirectory) return false
        return model.files.all { File(root, it.name).let { f -> f.isFile && f.length() > 0L } }
    }

    /** Cỡ thật đang chiếm trên đĩa (byte) cho model đang chọn. */
    fun sizeOnDisk(ctx: Context): Long =
        dir(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Gỡ mô hình đang chọn (và mọi rác dựng dở). Trả `true` nếu sau lệnh này thư mục model không còn. */
    fun remove(ctx: Context): Boolean {
        val files = ctx.applicationContext.filesDir
        val ok = dir(ctx).deleteRecursively()
        File(files, TMP_DIR).deleteRecursively()
        return ok
    }

    /**
     * Tải · kiểm từng tệp · đặt vào chỗ. **CHẶN** — gọi trên luồng nền.
     */
    @Suppress("ReturnCount")
    fun install(ctx: Context, onStep: (Step) -> Unit) {
        val app = ctx.applicationContext
        val model = selected(app)
        if (isReady(app)) { onStep(Step.Done(model.files.size)); return }
        if (!model.downloadable) {
            onStep(Step.Failed(Lang.t(
                "mô hình '${model.label}' chưa có nguồn tải (chờ mirror) — chọn bản khác",
                "model '${model.label}' has no download source yet (awaiting mirror) — pick another",
            )))
            return
        }
        if (!installing.compareAndSet(false, true)) {
            onStep(Step.Failed(Lang.t("đang cài rồi — đợi lượt này xong", "an install is already running")))
            return
        }
        try {
            spaceError(app, model)?.let { onStep(Step.Failed(it)); return }
            remove(app)
            val staging = File(app.filesDir, TMP_DIR)
            val out = File(staging, model.id)
            out.deleteRecursively(); out.mkdirs()

            val total = model.totalBytes
            var done = 0L
            // Side-load (owner 2026-09-15, xe không internet): tệp đặt sẵn ở `<ext>/sherpa/import/<model-id>/` được
            // ưu tiên, qua CÙNG phép kiểm bytes+sha256 với đường mạng (xem [VoiceModelSideload]). Vắng thẻ ⇒ null ⇒ mạng.
            val importDir = runCatching { app.getExternalFilesDir(null) }.getOrNull()
                ?.let { File(it, "${VoiceModelSideload.IMPORT_SUBDIR}/${model.id}") }
            for (mf in model.files) {
                val safe = requireSafe(mf.name) ?: run {
                    onStep(Step.Failed(Lang.t(
                        "tên tệp không hợp lệ: ${mf.name}", "invalid file name: ${mf.name}",
                    ))); return
                }
                val target = File(out, safe)
                val err = fetch(mf, target, total, done, onStep, VoiceModelSideload.candidate(importDir, safe))
                if (err != null) { out.deleteRecursively(); onStep(Step.Failed(err)); return }
                done += mf.bytes
            }

            onStep(Step.Verifying)
            val missing = model.files.filterNot { File(out, it.name).let { f -> f.isFile && f.length() > 0L } }
            if (missing.isNotEmpty()) {
                out.deleteRecursively()
                onStep(Step.Failed(Lang.t("thiếu tệp: ${missing.first().name}", "missing ${missing.first().name}")))
                return
            }

            onStep(Step.Extracting)   // "đang hoàn tất" — đổi tên là bước làm mô hình "xuất hiện"
            val dest = dir(app)
            dest.parentFile?.mkdirs()
            dest.deleteRecursively()
            if (!out.renameTo(dest)) {
                out.deleteRecursively()
                onStep(Step.Failed(Lang.t("không chuyển được thư mục mô hình", "could not move the model folder")))
                return
            }
            staging.deleteRecursively()
            Log.i(TAG, "mô hình sẵn sàng: ${dest.absolutePath} (${model.files.size} tệp)")
            onStep(Step.Done(model.files.size))
        } finally {
            installing.set(false)
        }
    }

    private val installing = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Tên tệp an toàn (§4.1) — một đoạn tên thuần, không `/ \ : .. .`. */
    private fun requireSafe(name: String): String? {
        if (name.isBlank() || name == "." || name == "..") return null
        if (name.any { it == '/' || it == '\\' || it == ':' }) return null
        return name
    }

    private fun spaceError(app: Context, model: SherpaModelCatalog.SherpaModel): String? {
        val need = model.totalBytes + SPACE_MARGIN_BYTES
        val free = runCatching { app.filesDir.usableSpace }.getOrDefault(0L)
        if (free <= 0L || free >= need) return null
        return Lang.t(
            "máy còn ${free / MB} MB, cần khoảng ${need / MB} MB — xoá bớt rồi thử lại",
            "only ${free / MB} MB free, about ${need / MB} MB needed — free some space and retry",
        )
    }

    /** Tải một tệp thành phần, băm trong lúc tải, so với bản ghim. Trả `null` khi xong, hoặc câu lỗi. */
    @Suppress("ReturnCount")
    private fun fetch(
        mf: SherpaModelCatalog.ModelFile,
        target: File,
        totalAll: Long,
        doneBefore: Long,
        onStep: (Step) -> Unit,
        sideload: File? = null,
    ): String? {
        if (!mf.pinned) return Lang.t("tệp ${mf.name} chưa ghim sha256/cỡ", "${mf.name} is not pinned")
        onStep(Step.Downloading(if (totalAll > 0) ((doneBefore * 100) / totalAll).toInt() else -1))
        if (sideload != null) {
            // Có tệp side-load ⇒ KHÔNG chạm mạng. Sai ⇒ báo thẳng (người chép USB cần biết), không rơi về mạng.
            Log.i(TAG, "side-load ${mf.name} từ ${sideload.absolutePath}")
            return VoiceModelSideload.copyVerified(
                sideload, target, mf.bytes, mf.sha256, percentReporter(totalAll, doneBefore, onStep),
            )
        }
        val got = runCatching { download(mf.url, target, totalAll, doneBefore, onStep) }
            .getOrElse { return Lang.t("lỗi mạng ${mf.name}: ${it.message}", "network error ${mf.name}: ${it.message}") }
            ?: return Lang.t("máy chủ từ chối ${mf.name}", "server refused ${mf.name}")
        if (got.bytes != mf.bytes || !got.sha256.equals(mf.sha256, ignoreCase = true)) {
            target.delete()
            return Lang.t(
                "tệp ${mf.name} không khớp bản ghim (${got.bytes} byte, sha ${got.sha256.take(12)}…)",
                "${mf.name} does not match pin (${got.bytes} bytes, sha ${got.sha256.take(12)}…)",
            )
        }
        return null
    }

    private data class Downloaded(val bytes: Long, val sha256: String)

    /**
     * Một bộ báo nhịp `%` dùng CHUNG cho cả hai đường lấy tệp (mạng · side-load): nhận **tổng byte đã lấy của tệp
     * đang chạy**, quy ra phần trăm của CẢ bộ rồi chỉ phát khi con số đổi (100 lần thay vì 4000 lần/tệp).
     * Một bản duy nhất để hai đường không bao giờ báo lệch nhau (CLAUDE.md §4.1 DRY).
     */
    private fun percentReporter(totalAll: Long, doneBefore: Long, onStep: (Step) -> Unit): (Long) -> Unit {
        var lastPercent = -2
        return { read ->
            val pct = if (totalAll > 0) (((doneBefore + read) * 100) / totalAll).toInt() else -1
            if (pct != lastPercent) { lastPercent = pct; onStep(Step.Downloading(pct)) }
        }
    }

    /** Tải một tệp, băm trong lúc tải; báo % theo tổng của cả bộ. `null` = máy chủ trả mã lỗi. */
    private fun download(url: String, out: File, totalAll: Long, doneBefore: Long, onStep: (Step) -> Unit): Downloaded? {
        val conn = HttpConn.open(url, READ_TIMEOUT_MS)
        try {
            if (conn.responseCode !in 200..299) return null
            // Chốt HTTPS ở địa chỉ THỰC SỰ đọc (cả hai nguồn 302 sang CDN) — xem KDoc bản Vosk cũ.
            if (!conn.url.protocol.equals("https", ignoreCase = true)) {
                throw IOException("máy chủ chuyển hướng sang ${conn.url.protocol}:// — từ chối")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            val report = percentReporter(totalAll, doneBefore, onStep)
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
                        report(read)
                    }
                }
            }
            return Downloaded(read, digest.digest().joinToString("") { "%02x".format(it) })
        } finally {
            conn.disconnect()
        }
    }

    private const val PREFS = "kachi_voice"
    private const val KEY_MODEL = "sherpa_model_id"
    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
