package com.byd.clusternav.launcher.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.byd.clusternav.launcher.KachiLog
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══ GHI một khung camera ra PNG — MỘT chỗ cho lệnh `camera_frame` (cầu kiểm thử) lẫn nút *📷 Khung thô* (Chẩn đoán) ═══
 *
 * Tách khỏi `TestBridgeCameraFrame` ở 2.92 (spec `kachi-292-camera-full-view.html` R8, CLAUDE.md §4.1 DRY): hai lối vào,
 * một cách nén/đặt tên/dọn. Lý do của từng lựa chọn (giữ nguyên từ bản gốc):
 *  • **PNG không mất mát** — JPEG thêm đúng loại nhiễu mắt đọc thành "viền mờ", làm bẩn chính phép đo vòng ảnh.
 *  • **`kachi-logs/`** ([KachiLog.dir]) — chỗ `adb pull` lấy được không cần `run-as`, không cần quyền bộ nhớ.
 *  • **Giữ [KEEP_PNG] tệp mới nhất** của CHÍNH tiền tố này — một khung 5120×960 nén ra hàng MB; tệp khác trong thư mục
 *    là của chủ khác, không xoá hộ.
 *
 * ## Bản sao vào Thư viện ảnh ([copyToPictures], 2.92)
 * `kachi-logs/` nằm trong `getExternalFilesDir` — cây mà `DiagStorageCap` dọn **cũ nhất trước** khi vượt trần (research
 * `camera-rear-coverage-2026-10-06.md` K8: ảnh khung thô 27/09 có thể đã mất đúng cách ấy). Bản sao qua `MediaStore` vào
 * `Pictures/Kachi/` nằm NGOÀI cây đó: sống qua mọi lượt dọn, mở được bằng Thư viện ảnh / chép ra USB — không cần adb
 * (CLAUDE.md §11). API 29+ chèn vào bộ sưu tập ảnh của chính app **không cần quyền nào** (cùng khuôn
 * `VoiceUtteranceLog.exportZip` với `Downloads`).
 */
internal object CameraFrameFiles {

    /** Tiền tố tệp ảnh trong `kachi-logs/` — một tên, một chỗ khai (script `adb pull` grep theo chuỗi này). */
    const val PREFIX = "camera-frame-"

    private const val EXT = ".png"

    /** Số ảnh giữ lại trong `kachi-logs/` — mười khung đủ cho một buổi xe, và là ~10–60 MB. */
    private const val KEEP_PNG = 10

    /** Mốc thời gian tới GIÂY: một lượt chụp/giây là nhiều hơn mọi nhịp xi-nhan thật. */
    private const val STAMP = "yyyyMMdd-HHmmss"

    /** `compress` bỏ qua tham số này với PNG (không mất mát) — ghi 100 cho rõ ý định. */
    private const val QUALITY = 100

    /** Thư mục con trong `Pictures/` của bản sao Thư viện ảnh. */
    private const val GALLERY_DIR = "Kachi"

    private const val TAG = "KachiCameraFrame"

    /** Kết quả một lượt ghi — tệp thật, hoặc lý do (ASCII: đi vào lời đáp JSON và `logcat`). */
    sealed interface Saved {
        data class Ok(val file: File) : Saved
        data class Failed(val reason: String, val path: String? = null) : Saved
    }

    /**
     * Nén [bmp] ra `kachi-logs/camera-frame-<giờ><tag>.png`. Chạy ở LUỒNG NỀN (nén khung cỡ luồng gốc mất hàng trăm ms).
     * KHÔNG `recycle` [bmp] — chỗ gọi lo, trong `finally`, kể cả nhánh hỏng.
     */
    fun savePng(app: Context, bmp: Bitmap, tag: String = ""): Saved {
        val dir = KachiLog.dir(app) ?: return Saved.Failed("no external files dir")
        prune(dir)
        val stamp = SimpleDateFormat(STAMP, Locale.US).format(Date())
        val out = File(dir, "$PREFIX$stamp$tag$EXT")
        val wrote = runCatching {
            out.outputStream().buffered().use { bmp.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }
        }.onFailure { Log.w(TAG, "write ${out.name} failed: ${it.javaClass.simpleName}") }.isSuccess
        return if (wrote) Saved.Ok(out) else Saved.Failed("compress/write threw", out.absolutePath)
    }

    /**
     * Pixel của lượt chụp THÔ qua FBO ([CameraGlRenderer.grabRaw]) → [Bitmap], hoặc `null` khi dựng hỏng.
     *
     * ═══ [P1 · SOÁT Opus 2026-09-27] BỀ RỘNG phải suy bằng ĐÚNG phép kẹp của `grabRaw` ═══════════════════════════════
     * `grabRaw` kẹp **CẢ HAI** cạnh theo `GL_MAX_TEXTURE_SIZE` ([CameraGlInfo.cap]). Tin bề rộng đã xin rồi suy
     * `h = px.size / w` là sai: trên đầu máy trần 4096 mà xin 5120×960, `grabRaw` trả 4096×960 px ⇒ dựng 5120×768 ⇒ mỗi
     * hàng lệch 1024 px ⇒ ảnh fisheye **xiên chéo**, `createBitmap` không ném, không log nào — người đo lấy tâm/bán kính
     * trên một ảnh đã bị xé. Đúng loại *"câu trả lời sai trông y như câu trả lời đúng"* CLAUDE.md §2 cấm.
     */
    fun rawBitmap(px: IntArray, askedW: Int): Bitmap? {
        val cap = CameraGlInfo.cap(askedW)
        val w = askedW.coerceIn(1, cap)
        val h = if (w > 0) px.size / w else 0
        return runCatching { Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888) }.getOrNull()
    }

    /**
     * Chép [file] vào `Pictures/Kachi/` qua `MediaStore` — trả đường hiển thị (`Pictures/Kachi/<tên>`) hoặc `null` (đã
     * ghi `logcat` lý do; tệp gốc trong `kachi-logs/` vẫn còn nên một lượt chép hỏng KHÔNG làm hỏng lượt chụp). Luồng nền.
     *
     * Mục `MediaStore` rỗng sau một lượt ghi hỏng bị XOÁ: để lại là một ảnh 0 byte trong Thư viện mà người dùng mở ra
     * rồi tưởng camera hỏng.
     */
    fun copyToPictures(app: Context, file: File): String? {
        val rel = Environment.DIRECTORY_PICTURES + "/" + GALLERY_DIR
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
        }
        val resolver = app.contentResolver
        val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }
            .onFailure { Log.w(TAG, "MediaStore từ chối ${file.name}: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return null
        return try {
            val out = resolver.openOutputStream(uri) ?: throw FileNotFoundException(uri.toString())
            out.use { sink -> file.inputStream().use { it.copyTo(sink) } }
            "$rel/${file.name}"
        } catch (e: IOException) {
            Log.w(TAG, "chép ${file.name} vào Thư viện hỏng: ${e.javaClass.simpleName}")
            runCatching { resolver.delete(uri, null, null) }
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "chép ${file.name} vào Thư viện bị từ chối: ${e.message}")
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** Dọn `camera-frame-*.png` cũ nhất, giữ [KEEP_PNG] — chỉ tệp của CHÍNH tiền tố này. */
    private fun prune(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(EXT) }
            ?: return
        if (files.size <= KEEP_PNG) return
        files.sortedBy { it.lastModified() }.take(files.size - KEEP_PNG).forEach { runCatching { it.delete() } }
    }
}
