package com.byd.clusternav.launcher

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * ═══ TỆP VÒNG CHẨN ĐOÁN `filesDir/diag/<tên>` — MỘT lớp cho mọi nhật ký bền của app (FIX286 · SR-T7) ════════════
 *
 * Tách từ `A11yBindJournalStore` (2.83) khi 2.86 cần nhật ký bền thứ hai (`ctl-writes.log`, mỗi lệnh ghi xe) — chép
 * lại phần đọc/khoá/cắt-N-dòng là bản sao thứ hai của cùng một việc (global §4.1 DRY). Nhật ký gắn Hỗ trợ uỷ quyền
 * xuống đây với **đúng** đường dẫn, đúng định dạng (`dòng\n` mỗi dòng, bỏ dòng trắng khi đọc) và đúng trần dòng.
 *
 * ## Hai lớp khoá
 *  1. `synchronized` — các luồng TRONG một tiến trình (watchdog FGS · lượt grant · làn ghi nhật ký);
 *  2. `FileLock` trên tệp `<tên>.lock` — các TIẾN TRÌNH: từ 2.62 phím vô-lăng chạy lệnh trong `:wake`, nên lệnh ghi
 *     xe đến từ hai tiến trình vào cùng `ctl-writes.log`. Đọc-sửa-ghi trọn tệp mà không khoá liên tiến trình thì hai
 *     lượt đan nhau sẽ ghi đè mất dòng của nhau. (`FileLock` thuộc tiến trình, nên lớp 1 vẫn phải có — hai luồng cùng
 *     tiến trình xin `lock()` chồng nhau là `OverlappingFileLockException`.)
 *
 * Không ném ra ngoài: `IOException` ⇒ một dòng `Log.w` + trả `false`/rỗng — đây là dụng cụ chẩn đoán, không phải
 * đường sống của tính năng nào.
 */
class DiagRingFile(private val name: String, private val maxLines: Int, private val tag: String) {

    private val lock = Any()

    private fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }

    /** Tệp nhật ký (tạo thư mục nếu chưa có). */
    fun file(ctx: Context): File = File(dir(ctx), name)

    /** Toàn bộ nhật ký, mới nhất ở cuối. Rỗng nếu chưa có gì hoặc đọc lỗi. */
    fun read(ctx: Context): List<String> = synchronized(lock) {
        try {
            acrossProcesses(ctx) { readUnlocked(ctx) }
        } catch (e: IOException) {
            Log.w(tag, "đọc nhật ký lỗi: ${e.message}")
            emptyList()
        }
    }

    /**
     * Đọc-sửa-ghi dưới cả hai khoá: [next] nhận các dòng hiện có + tệp, trả dòng cần nối thêm (hoặc `null` = không
     * ghi gì lượt này). Cắt về [maxLines] dòng cuối rồi ghi lại trọn tệp.
     *
     * @return `true` nếu vừa nối thêm một dòng.
     */
    fun appendIf(ctx: Context, next: (lines: List<String>, file: File) -> String?): Boolean = synchronized(lock) {
        try {
            acrossProcesses(ctx) {
                val f = file(ctx)
                val lines = readUnlocked(ctx)
                val line = next(lines, f)
                if (line == null) {
                    false
                } else {
                    val all = lines + line
                    val kept = if (all.size <= maxLines) all else all.subList(all.size - maxLines, all.size)
                    f.writeText(kept.joinToString("\n", postfix = "\n"))
                    true
                }
            }
        } catch (e: IOException) {
            Log.w(tag, "ghi nhật ký lỗi: ${e.message}")
            false
        }
    }

    /** Nối [line] vô điều kiện. */
    fun append(ctx: Context, line: String): Boolean = appendIf(ctx) { _, _ -> line }

    private fun readUnlocked(ctx: Context): List<String> {
        val f = file(ctx)
        return if (f.isFile) f.readLines().filter { it.isNotBlank() } else emptyList()
    }

    private fun <T> acrossProcesses(ctx: Context, body: () -> T): T =
        RandomAccessFile(File(dir(ctx), "$name$LOCK_SUFFIX"), "rw").use { raf ->
            raf.channel.lock().use { body() }
        }

    companion object {
        /** Thư mục chung của mọi nhật ký bền: `filesDir/diag`. */
        const val DIR = "diag"

        private const val LOCK_SUFFIX = ".lock"
    }
}
