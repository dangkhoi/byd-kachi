package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import com.byd.clusternav.Lang
import com.byd.clusternav.net.HttpConn
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * ═══ TẢI · KIỂM · LẮP một **gói giọng** (nghe HOẶC đọc) — NHIỀU tệp rời, ghim từng tệp ══════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html` §Design (đường NGHE) + `kachi-voice-feedback.html` **T8** (đường
 * ĐỌC). Bản kê (URL · sha256 · cỡ từng tệp) nằm ở `:core` ([SherpaModelCatalog] · [SherpaTtsCatalog]) và có bài
 * canh off-car; tệp này chỉ **thi hành**.
 *
 * ## T8 — vì sao lớp này nay nhận [VoicePack] chứ không chỉ mô hình NGHE
 * Gói ĐỌC (Piper tỉa, 13 tệp) cần **đúng bốn việc** mà lớp này đã làm cho mô hình nghe: tải từng tệp có ghim,
 * side-load từ USB, hỏi *"đã lắp đủ chưa"*, gỡ. Chép lớp này thành `VoiceTtsStore` là dựng bản sao thứ hai của
 * một thuật toán có bốn tính chất tinh tế (dưới) — và bản sao ấy sẽ lệch ở đúng lần ai đó vá một tính chất.
 * Nên hợp đồng ở `:core` ([VoicePack]) và **một** bản thi hành ở đây. Mọi API cũ (không tham số gói) vẫn còn:
 * chúng gập về gói NGHE đang chọn ([selected]), nên không chỗ gọi nào phải sửa.
 *
 * ## Bốn tính chất giữ nguyên tinh thần R9
 *  1. **Không để lại thư mục nửa vời.** Tải vào `.staging` rồi mới đổi tên sang thư mục thật. Tiến trình bị giết
 *     giữa chừng chỉ để rác trong `.staging`; thư mục gói hoặc chưa có, hoặc đủ tệp — onnxruntime không ngã
 *     bằng lỗi native khó bắt.
 *  2. **Băm TRONG lúc tải.** `DigestInputStream`-kiểu: đọc một lượt vừa ghi vừa băm, so ngay với bản ghim.
 *  3. **Hỏng thì XOÁ rồi nói ra.** Không giữ tệp cụt chiếm chỗ.
 *  4. **Từ chối gói chưa ghim.** [VoicePack.downloadable] = false (vd bản gated chưa mirror) ⇒ báo lý do,
 *     KHÔNG tải mù (fail-safe — CLAUDE.md §4.1).
 *
 * ⚠ Mọi hàm có I/O ở đây **chặn** ⇒ chỗ gọi chạy trên luồng nền (xem [VoiceModelSettings]).
 */
object VoiceModelStore {

    private const val TAG = "KachiVoiceModel"
    private const val VERSION_FILE = ".version"

    private const val READ_TIMEOUT_MS = 60_000
    private const val MB = 1024L * 1024L
    private const val SPACE_MARGIN_BYTES = 40L * MB

    /** Tiến trình cài gói — một dòng chữ cho người dùng. Giữ nguyên các nhánh để chữ trong Cài đặt khỏi đổi. */
    sealed interface Step {
        /** Đang tải; [percent] = `-1` khi máy chủ không nói tổng cỡ. */
        data class Downloading(val percent: Int) : Step
        object Verifying : Step
        object Extracting : Step
        /** Xong — [files] = số tệp đã đặt đúng chỗ. */
        data class Done(val files: Int) : Step
        data class Failed(val reason: String) : Step
    }

    /**
     * Model NGHE đang dùng.
     *
     * ## ⚠ Từ bản release production (owner 2026-09-21) chỉ còn đường ĐỌC, không còn đường CHỌN
     * Danh mục còn **đúng một** gói ([SherpaModelCatalog.ALL]) và hai nút đổi mô hình trong Cài đặt đã gỡ, nên
     * `select(ctx, id)` — chỗ ghi DUY NHẤT của [KEY_MODEL] — cũng gỡ theo. Khoá ấy vẫn được **đọc** ở dòng đầu vì
     * các bản ≤ 1.87 đã ghi nó trên những chiếc xe đang chạy; [SherpaModelCatalog.byId] lùi mọi id không còn trong
     * danh mục về gói duy nhất, nên một pref cũ không thể làm câm đường nghe.
     *
     * ## Vì sao hai nhánh dò-trên-đĩa của 1.66/1.70 đã BỎ, và vì sao bỏ được mà không đổi hành vi
     * Chúng tồn tại để một xe **đã** cài fp32 không mất mô hình khi mặc định đổi sang int8, và để int8 thắng fp32
     * khi cả hai cùng nằm trên đĩa ([ĐO xe 2026-09-17]: xe owner giải mã fp32 dù int8 đã tải xong, chỉ vì fp32
     * đứng trước trong `ALL`). Với một danh mục **một phần tử**, cả hai nhánh chỉ có thể trả về đúng gói mà
     * `default()` đã trả — tức mã không bao giờ chạy, đội lốt một lá chắn còn sống.
     *
     * ⚠ Hàm này **không ghi** gì: một lượt đọc không được phép chốt lựa chọn thay người dùng.
     */
    fun selected(ctx: Context): SherpaModelCatalog.SherpaModel =
        prefs(ctx).getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?.let { SherpaModelCatalog.byId(it) }
            ?: SherpaModelCatalog.default()

    /** Thư mục của một gói (mặc định: mô hình NGHE đang chọn). */
    fun dir(ctx: Context, pack: VoicePack = selected(ctx)): File =
        File(ctx.applicationContext.filesDir, pack.dir)

    /** Đường dẫn tuyệt đối một tệp thành phần trong thư mục gói. */
    fun filePath(ctx: Context, name: String): String = File(dir(ctx), name).absolutePath

    /**
     * Gói đã sẵn sàng chưa — kiểm **từng tệp** (tồn tại + khác rỗng), không chỉ kiểm thư mục.
     * Một thư mục thiếu tệp là cách chắc chắn nhất để onnxruntime ngã trong mã native.

     */
    fun isReady(ctx: Context, pack: VoicePack = selected(ctx)): Boolean {
        val root = dir(ctx, pack)
        if (!root.isDirectory) return false
        return pack.files.map { it.name }.all { File(root, it).let { f -> f.isFile && f.length() > 0L } }
    }

    /** Phiên bản gói ĐANG nằm trên đĩa (đọc `.version`); `0` nếu chưa cài hoặc file cũ (trước khi có cơ chế này). */
    fun installedVersion(ctx: Context, pack: VoicePack = selected(ctx)): Int =
        runCatching { File(dir(ctx, pack), VERSION_FILE).readText().trim().toInt() }.getOrDefault(0)

    /**
     * Gói đã cài NHƯNG có bản MỚI trên máy chủ (asset đổi ⇒ [VoicePack.version] catalog > số trên đĩa).
     * `false` khi chưa cài (dùng [isReady] cho ca đó) hoặc đã đúng bản mới nhất. Owner 2026-09-22 — nút "Cập nhật".
     */
    fun needsUpdate(ctx: Context, pack: VoicePack = selected(ctx)): Boolean =
        isReady(ctx, pack) && installedVersion(ctx, pack) < pack.version

    /** Cỡ thật đang chiếm trên đĩa (byte) cho một gói. */
    fun sizeOnDisk(ctx: Context, pack: VoicePack = selected(ctx)): Long =
        dir(ctx, pack).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Gỡ một gói (và mọi rác dựng dở của họ gói đó). Trả `true` nếu sau lệnh này thư mục gói không còn. */
    fun remove(ctx: Context, pack: VoicePack = selected(ctx)): Boolean {
        val ok = dir(ctx, pack).deleteRecursively()
        staging(ctx, pack).deleteRecursively()
        return ok
    }

    /**
     * Tải · kiểm từng tệp · đặt vào chỗ. **CHẶN** — gọi trên luồng nền.
     *
     * [pack] mặc định là mô hình NGHE đang chọn, nên mọi chỗ gọi cũ không phải sửa; màn Cài đặt truyền thêm gói
     * ĐỌC ([SherpaTtsCatalog.PIPER_VI_VAIS1000]) qua đúng hàm này.
     */
    @Suppress("ReturnCount")
    fun install(ctx: Context, pack: VoicePack = selected(ctx), onStep: (Step) -> Unit) {
        val app = ctx.applicationContext
        if (isReady(app, pack)) { onStep(Step.Done(pack.files.size)); return }
        // Side-load (owner 2026-09-15, xe không internet): tệp đặt sẵn ở `<ext>/sherpa/import/<id>/<đường dẫn
        // tương đối>` được ưu tiên, qua CÙNG phép kiểm bytes+sha256 với đường mạng ([VoiceModelSideload]).
        val importDir = importDir(app, pack)
        val sideloaded = pack.files.count { VoiceModelSideload.candidate(importDir, it.name) != null }
        // ⚠ Cổng `downloadable` chỉ chặn đường MẠNG. Gói chưa ghim mà có ĐỦ tệp side-load thì vẫn lắp được —
        // và đó chính là ca của chiếc xe không internet: sai một byte vẫn bị phép so sha256 dưới kia bắt.
        if (!pack.downloadable && sideloaded < pack.files.size) {
            onStep(Step.Failed(Lang.t(
                "gói '${pack.label}' chưa có nguồn tải (chờ mirror) — chép tệp vào thẻ hoặc chọn bản khác",
                "'${pack.label}' has no download source yet (awaiting mirror) — side-load it or pick another",
            )))
            return
        }
        if (!installing.compareAndSet(false, true)) {
            onStep(Step.Failed(Lang.t("đang cài rồi — đợi lượt này xong", "an install is already running")))
            return
        }
        try {
            spaceError(app, pack)?.let { onStep(Step.Failed(it)); return }
            remove(app, pack)
            val staging = staging(app, pack)
            val out = File(staging, pack.id)
            out.deleteRecursively(); out.mkdirs()

            val total = pack.totalBytes
            var done = 0L
            for (mf in pack.files) {
                val safe = requireSafe(mf.name) ?: run {
                    onStep(Step.Failed(Lang.t(
                        "tên tệp không hợp lệ: ${mf.name}", "invalid file name: ${mf.name}",
                    ))); return
                }
                val target = File(out, safe)
                // Cây thư mục nhiều tầng (`espeak-ng-data/lang/aav/…`): tạo thư mục cha TRƯỚC, nếu không thì
                // `outputStream()` ném `FileNotFoundException` cho một đường dẫn hoàn toàn hợp lệ.
                target.parentFile?.mkdirs()
                val err = fetch(mf, target, total, done, onStep, VoiceModelSideload.candidate(importDir, safe))
                if (err != null) { out.deleteRecursively(); onStep(Step.Failed(err)); return }
                done += mf.bytes
            }

            onStep(Step.Verifying)
            val missing = pack.files.filterNot { File(out, it.name).let { f -> f.isFile && f.length() > 0L } }
            if (missing.isNotEmpty()) {
                out.deleteRecursively()
                onStep(Step.Failed(Lang.t("thiếu tệp: ${missing.first().name}", "missing ${missing.first().name}")))
                return
            }

            onStep(Step.Extracting)   // "đang hoàn tất" — đổi tên là bước làm gói "xuất hiện"
            // Ghi phiên bản gói ra `.version` (owner 2026-09-22) — TRONG staging, TRƯỚC khi đổi tên (hardening
            // 2026-09-25 · audit F6): ghi sau rename mà hỏng (thẻ đầy ngay sau 61 MB) là im lặng ⇒ `installedVersion`
            // = 0 ⇒ `needsUpdate()` true mãi ⇒ mỗi lần bấm "Cập nhật" là tải lại 61 MB rồi hỏng đúng chỗ này. Nay
            // `.version` là một tệp của gói như mọi tệp khác: hoặc đủ, hoặc không có thư mục (tính chất 1).
            if (!writeVersionMarker(out, pack.version)) {
                out.deleteRecursively()
                onStep(Step.Failed(Lang.t("không ghi được .version (thẻ đầy?)", "could not write .version (storage full?)")))
                return
            }
            val dest = dir(app, pack)
            dest.parentFile?.mkdirs()
            dest.deleteRecursively()
            if (!out.renameTo(dest)) {
                out.deleteRecursively()
                onStep(Step.Failed(Lang.t("không chuyển được thư mục gói", "could not move the pack folder")))
                return
            }
            staging.deleteRecursively()
            Log.i(TAG, "gói sẵn sàng: ${dest.absolutePath} (${pack.files.size} tệp, v${pack.version})")
            onStep(Step.Done(pack.files.size))
        } finally {
            installing.set(false)
        }
    }

    private val installing = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Ghi `.version` vào [dir] rồi **đọc lại** để xác nhận (một `writeText` không ném vẫn có thể ghi cụt khi thẻ
     * đầy — `FileOutputStream.write` trên FAT/ext4 có thể thành công tới lúc `close`). `false` = không có tệp đúng
     * ⇒ chỗ gọi huỷ cả gói. THUẦN (chỉ `java.io.File`) ⇒ `VoiceModelVersionMarkerTest` khoá off-device.
     */
    internal fun writeVersionMarker(dir: File, version: Int): Boolean = runCatching {
        val f = File(dir, VERSION_FILE)
        f.writeText(version.toString())
        f.readText().trim().toInt() == version
    }.getOrDefault(false)


    /**
     * Thư mục dựng dở của [pack] — luật chỗ đặt ở `:core` ([VoicePackPaths.stagingDir], có bài canh off-car).
     *
     * Ở đây chỉ còn việc nối nó vào `filesDir`. Giữ luật ở `:core` vì nó là luật **đường dẫn thuần**, và vì nó
     * từng sai một cách không thể phát hiện bằng bất kỳ phép kiểm nào ở tầng này (xem KDoc [VoicePackPaths]).
     */
    private fun staging(ctx: Context, pack: VoicePack): File =
        File(ctx.applicationContext.filesDir, VoicePackPaths.stagingDir(pack.dir))

    /** Thư mục side-load của [pack] trên thẻ/USB; `null` khi máy không có thư mục ngoài (vắng thẻ). */
    private fun importDir(app: Context, pack: VoicePack): File? =
        runCatching { app.getExternalFilesDir(null) }.getOrNull()
            ?.let { File(it, "${VoiceModelSideload.IMPORT_SUBDIR}/${pack.id}") }

    /**
     * Đường dẫn tương đối AN TOÀN (§4.1) — trả chính chuỗi đó nếu hợp lệ, `null` nếu không.
     *
     * Từ T8 [name] có thể **nhiều đoạn** (`espeak-ng-data/lang/aav/vi`), nên luật kiểm theo **từng đoạn**: không
     * đoạn nào rỗng/`.`/`..`, không đoạn nào chứa `\` hay `:` (đường dẫn của HĐH khác), và cả chuỗi không bắt
     * đầu bằng `/` (đường tuyệt đối). Nới ra cho có `/` mà quên kiểm đoạn là mở thẳng đường `../../` ra khỏi
     * thư mục app — đúng thứ luật cũ (*"một đoạn tên thuần"*) đang chặn.
     */
    private fun requireSafe(name: String): String? {
        if (name.isBlank() || name.startsWith("/")) return null
        val parts = name.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." || it.any { c -> c == '\\' || c == ':' } }) return null
        return name
    }

    private fun spaceError(app: Context, pack: VoicePack): String? {
        val need = pack.totalBytes + SPACE_MARGIN_BYTES
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
        if (mf.url.isBlank()) {
            return Lang.t(
                "tệp ${mf.name} chưa có nguồn tải — chép qua thẻ/USB (xem Cài đặt › Giọng nói)",
                "${mf.name} has no download source — side-load it from a USB stick",
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
