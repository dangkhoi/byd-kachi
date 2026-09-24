package com.byd.clusternav.launcher

import android.content.Context
import android.util.Log
import java.io.File

/**
 * #4 (owner 2026-09-24) — Ghi/đọc file HỒ SƠ để backup + chia sẻ.
 *
 * Thư mục **riêng của app ở bộ nhớ ngoài** (`Android/data/<gói>/files/profiles/`) — KHÔNG cần quyền nào (cùng lối
 * `WallpaperStore`), và người dùng cắm USB / dùng trình quản lý file chép ra/vào được để chia sẻ giữa xe.
 *
 * Định dạng file = đúng chuỗi `WorkspacePrefsProfile.exportProfile` sinh ra (header `kachi-profile\tv1\t<tên>` +
 * PrefSnapshot). Đuôi `.kachi` để nhận dạng. Tên file lấy từ tên hồ sơ (khử ký tự không hợp lệ).
 */
object ProfileIoStore {
    private const val TAG = "KachiProfileIO"
    private const val DIR = "profiles"
    private const val EXT = ".kachi"

    private fun dir(ctx: Context): File? =
        ctx.applicationContext.getExternalFilesDir(null)?.let { File(it, DIR).apply { mkdirs() } }

    /** Đường dẫn thư mục (hiện cho người dùng biết chép file vào/ra đâu). */
    fun folderPath(ctx: Context): String = dir(ctx)?.absolutePath ?: ""

    /** Ghi [data] ra file tên theo [profileName]. Trả đường dẫn file, hoặc null nếu hỏng. */
    fun write(ctx: Context, profileName: String, data: String): String? {
        val d = dir(ctx) ?: return null
        val safe = profileName.trim().replace(Regex("[^\\p{L}\\p{Nd} _-]"), "_").ifBlank { "profile" }
        val f = File(d, "$safe$EXT")
        return runCatching { f.writeText(data); f.absolutePath }
            .onFailure { Log.w(TAG, "ghi hồ sơ hỏng: ${it.message}") }.getOrNull()
    }

    /** Đọc TẤT CẢ file `.kachi` trong thư mục (cho lượt Nhập). Mỗi phần tử = nội dung một file. */
    fun readAll(ctx: Context): List<String> {
        val d = dir(ctx) ?: return emptyList()
        return d.listFiles { f -> f.isFile && f.name.endsWith(EXT) }?.sortedBy { it.name }
            ?.mapNotNull { runCatching { it.readText() }.getOrNull() } ?: emptyList()
    }
}
