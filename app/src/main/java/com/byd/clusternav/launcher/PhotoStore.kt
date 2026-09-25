package com.byd.clusternav.launcher

import android.content.Context
import android.util.Log
import java.io.File

/**
 * ═══ KHO ẢNH cho WIDGET **TRÌNH CHIẾU ẢNH** — THƯ MỤC RIÊNG (owner 2026-09-21) ═══════════════════════════════
 *
 * Owner: *"cái folder để làm wallpaper, widget trình chiếu ảnh phải khác với car image chứ, lý thuyết là phải 3
 * folder khác nhau"*. Đúng — nay có **ba** thư mục ảnh tách bạch, mỗi cái một việc:
 *  • `files/car/`        → ảnh xe tổng hợp ([CarImageStore])
 *  • `files/wallpapers/` → ảnh NỀN màn hình ([WallpaperStore])
 *  • `files/photos/`     → ảnh cho widget *Trình chiếu ảnh* ([PhotoStore] — tệp này)
 *
 * Trước đây widget trình chiếu ĐỌC CHUNG `files/wallpapers/` với hình nền ⇒ đặt một ảnh làm nền thì nó cũng hiện
 * trong widget và ngược lại. Tách ra để người dùng bỏ ảnh gia đình vào widget mà không biến chúng thành hình nền.
 *
 * Chỉ là **địa chỉ thư mục + liệt kê**; phần giải mã giảm-cỡ dùng lại [WallpaperStore.loadScaled] (một nơi làm
 * việc nạp ảnh an toàn RAM, không chép bản thứ hai).
 */
object PhotoStore {

    private const val TAG = "PhotoStore"

    /** Tên thư mục người dùng bỏ ảnh trình chiếu vào. */
    const val FOLDER = "photos"

    /** Thư mục ảnh trình chiếu; `null` nếu bộ nhớ ngoài không dùng được. Tự tạo (để người dùng thấy chỗ mà bỏ vào). */
    fun folder(ctx: Context): File? = runCatching {
        val base = ctx.applicationContext.getExternalFilesDir(null) ?: return null
        File(base, FOLDER).apply { if (!exists()) mkdirs() }
    }.onFailure { Log.w(TAG, "folder: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()   // audit F7: widget trống phải có lý do

    /** Đường dẫn để chỉ cho người dùng biết bỏ ảnh vào đâu. */
    fun folderHint(ctx: Context): String =
        folder(ctx)?.absolutePath ?: ctx.getString(com.byd.clusternav.R.string.kachi_wall_no_ext_storage)

    /** Danh sách ảnh, thứ tự ổn định (cùng luật [Slideshow.imagesFrom] như hình nền). Lỗi đọc ⇒ rỗng, không sập. */
    fun images(ctx: Context): List<String> = runCatching {
        val dir = folder(ctx) ?: return emptyList()
        val names = dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        Slideshow.imagesFrom(names).map { File(dir, it).absolutePath }
    }.getOrElse {
        Log.w(TAG, "không đọc được thư mục ảnh trình chiếu: ${it.javaClass.simpleName}")
        emptyList()
    }
}
