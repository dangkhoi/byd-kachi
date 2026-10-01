package com.byd.clusternav.launcher

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * #4 (owner 2026-09-24) · PROFILE-IO-0930 (owner 2026-09-30) — Ghi/liệt kê/đọc tệp HỒ SƠ để sao lưu + chia sẻ.
 *
 * Thư mục **riêng của app ở bộ nhớ ngoài** (`Android/data/<gói>/files/profiles/`) — KHÔNG cần quyền nào (cùng lối
 * `WallpaperStore`), và người dùng cắm USB / dùng trình quản lý file chép ra/vào được để chia sẻ giữa xe.
 *
 * Lớp này CHỈ đổi `Context` thành thư mục + dấu thời gian rồi gọi [ProfileFiles] (`:core`, test off-device): tên tệp
 * `<tên>-<yyyyMMdd-HHmm>[-share].kachi`, trùng ⇒ hậu tố số, **không bao giờ ghi đè**; nhập đọc **đúng MỘT** tệp người
 * dùng chọn (tên tệp qua [ProfileFiles.safeName]: `File(x).name == x`, từ chối `..`/rỗng/dấu chấm đầu). Định dạng tệp
 * = chuỗi của [ProfileTransfer.export] (spec `kachi-profiles-are-everything.html` §12).
 */
object ProfileIoStore {
    private const val TAG = "KachiProfileIO"
    private const val DIR = "profiles"
    private const val STAMP = "yyyyMMdd-HHmm"

    private fun dir(ctx: Context): File? =
        ctx.applicationContext.getExternalFilesDir(null)?.let { File(it, DIR).apply { mkdirs() } }

    /** Đường dẫn thư mục (hiện cho người dùng biết chép file vào/ra đâu). */
    fun folderPath(ctx: Context): String = dir(ctx)?.absolutePath ?: ""

    /** Ghi [data] ra một tệp MỚI tên theo [profileName] + lúc này + [kind]. Trả đường dẫn tệp, hoặc null nếu hỏng. */
    fun write(ctx: Context, profileName: String, kind: ProfileTransfer.Kind, data: String): String? {
        val d = dir(ctx) ?: return null.also { Log.w(TAG, "ghi hồ sơ hỏng: không có bộ nhớ ngoài của app") }
        val stamp = SimpleDateFormat(STAMP, Locale.US).format(Date())
        return ProfileFiles.writeNew(d, profileName, stamp, kind, data)?.absolutePath
            ?: null.also { Log.w(TAG, "ghi hồ sơ hỏng trong ${d.absolutePath}") }
    }

    /** Mọi tệp hồ sơ trong thư mục, mới nhất trước (cho hộp chọn tệp nhập). */
    fun list(ctx: Context): List<ProfileFiles.Entry> = dir(ctx)?.let { ProfileFiles.list(it) }.orEmpty()

    /** Nội dung của ĐÚNG một tệp [fileName] trong thư mục; null nếu tên không an toàn / không có / đọc hỏng. */
    fun read(ctx: Context, fileName: String): String? {
        val d = dir(ctx) ?: return null
        return ProfileFiles.read(d, fileName) ?: null.also { Log.w(TAG, "không đọc được tệp hồ sơ ${fileName.take(80)}") }
    }
}
