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
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            val sampledW = bounds.outWidth / sample
            val sampledH = bounds.outHeight / sample
            val targetW = scaledWidth(sampledW, sampledH, reqW, reqH)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                // [SOÁT P2-4] hạ đúng khung NGAY TRONG lúc giải mã: làm bằng cách tạo bitmap to rồi thu nhỏ sẽ cần
                // hai ảnh cùng sống, tức đúng thứ đang muốn tránh.
                if (targetW > 0) {
                    inScaled = true
                    inDensity = sampledW
                    inTargetDensity = targetW
                }
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

    /**
     * Bề rộng đích sau khi đã hạ **đúng khung** — dùng cho bước hạ cỡ trong lúc giải mã.
     *
     * ## [SOÁT P2-4] Vì sao cần bước này
     * [sampleSize] chỉ giảm theo **luỹ thừa 2** và dừng khi một chiều sắp nhỏ hơn khung ⇒ với ảnh **tỉ lệ lệch** thì
     * chiều còn lại vẫn rất lớn. Ví dụ thật: ảnh 12000×9000 cho khung 1920×1080 ⇒ tỉ lệ giảm 4 ⇒ còn 3000×2250 =
     * **27 MB**. Lúc đổi ảnh có **hai** ảnh cùng sống, cộng thêm widget nữa ⇒ nguy cơ hết bộ nhớ.
     *
     * Bước này hạ tiếp cho **vừa khung** (vẫn phủ kín, vì chế độ phủ kín sẽ cắt): lấy tỉ lệ **lớn hơn** trong hai
     * chiều nên ảnh vẫn trùm đủ khung. Ví dụ trên xuống còn 1920×1440 ≈ **11 MB**.
     *
     * Trả về 0 nghĩa là **không cần hạ thêm** (ảnh đã nhỏ hơn khung).
     */
    fun scaledWidth(sampledW: Int, sampledH: Int, reqW: Int, reqH: Int): Int {
        if (sampledW <= 0 || sampledH <= 0 || reqW <= 0 || reqH <= 0) return 0
        val scale = maxOf(reqW.toFloat() / sampledW, reqH.toFloat() / sampledH)
        if (scale >= 1f) return 0
        return (sampledW * scale).toInt().coerceAtLeast(1)
    }
}
