package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * KHO ẢNH cho hình nền + trình chiếu (U4) — phần chạm Android. Quyết định nằm ở `:core` ([Slideshow]).
 *
 * ## Ảnh lấy từ đâu, và vì sao chọn chỗ đó
 * Đọc từ **thư mục riêng của app ở bộ nhớ ngoài** (`Android/data/<gói>/files/wallpapers`). Ba lý do, đều là ràng
 * buộc thật của xe:
 *  • **Không cần quyền nào** — thư mục riêng của app đọc/ghi được mà không phải xin quyền lúc chạy.
 *  • **Không cần màn chọn tệp của hệ thống** — [ĐO] các màn hệ thống trên xe bị khoá (*"Hệ thống IVI không hỗ trợ
 *    hoạt động này"*), nên nút "chọn ảnh" kiểu thường sẽ dẫn người dùng vào chỗ không làm được gì.
 *  • App **đã dùng** chính lối này để xuất log ⇒ người dùng đã có đường bỏ tệp vào đó.
 *
 * ## Ảnh to thì sao
 * Ảnh máy ảnh 12MP nạp nguyên cỡ là ~48 MB bộ nhớ cho MỘT ảnh — đủ để làm launcher chết khi trình chiếu qua vài ảnh.
 * Nên luôn nạp **giảm cỡ theo khung sẽ vẽ** ([loadScaled]), không bao giờ nạp nguyên bản.
 */
object WallpaperStore {

    private const val TAG = "Wallpaper"

    /** Tên thư mục người dùng bỏ ảnh vào. */
    const val FOLDER = "wallpapers"

    /** Thư mục ảnh; `null` nếu bộ nhớ ngoài không dùng được. Tự tạo nếu chưa có (để người dùng thấy chỗ mà bỏ vào). */
    fun folder(ctx: Context): File? = runCatching {
        val base = ctx.applicationContext.getExternalFilesDir(null) ?: return null
        File(base, FOLDER).apply { if (!exists()) mkdirs() }
    }.getOrNull()

    /** Đường dẫn để chỉ cho người dùng biết bỏ ảnh vào đâu. */
    fun folderHint(ctx: Context): String = folder(ctx)?.absolutePath ?: "(bộ nhớ ngoài không dùng được)"

    /**
     * Danh sách ảnh, **thứ tự ổn định** (xem [Slideshow.imagesFrom] — thứ tự đổi mỗi lần đọc sẽ làm trình chiếu
     * nhảy loạn thay vì chạy vòng). Lỗi đọc ⇒ danh sách rỗng, KHÔNG sập.
     */
    fun images(ctx: Context): List<String> = runCatching {
        val dir = folder(ctx) ?: return emptyList()
        val names = dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        Slideshow.imagesFrom(names).map { File(dir, it).absolutePath }
    }.getOrElse {
        Log.w(TAG, "không đọc được thư mục ảnh: ${it.javaClass.simpleName}")
        emptyList()
    }

    /**
     * Nạp ảnh **đã giảm cỡ** cho khung [reqW]×[reqH]. `null` nếu không giải mã được (tệp hỏng / không phải ảnh thật
     * dù đúng đuôi) — chỗ gọi vẽ nền mặc định.
     *
     * Hai lượt đọc là cố ý: lượt đầu chỉ đọc **kích thước** (không nạp pixel), lượt sau nạp với tỉ lệ giảm. Nạp
     * nguyên bản rồi mới thu nhỏ là cách chắc chắn để hết bộ nhớ.
     */
    fun loadScaled(path: String, reqW: Int, reqH: Int): Bitmap? {
        if (reqW <= 0 || reqH <= 0) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            }
            BitmapFactory.decodeFile(path, opts)
        }.getOrElse {
            Log.w(TAG, "không giải mã được ảnh: ${it.javaClass.simpleName}")
            null
        }
    }

    /**
     * Tỉ lệ giảm cỡ — luỹ thừa của 2 (yêu cầu của bộ giải mã ảnh Android). Giảm tới khi ảnh vừa đủ **lớn hơn** khung,
     * không nhỏ hơn: nhỏ hơn thì phải phóng lên và nhìn nhoè.
     */
    fun sampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var s = 1
        while (srcW / (s * 2) >= reqW && srcH / (s * 2) >= reqH) s *= 2
        return s
    }
}
