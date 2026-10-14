package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.byd.clusternav.system.PackageQueries
import java.io.File
import java.io.IOException

/**
 * 3.1.5 — BỘ ẢNH NỀN MẶC ĐỊNH đóng trong APK (`assets/wall-default/`) — phần chạm Android. Quyết định ở [WallSourcePolicy].
 *
 * Owner 14/10: ảnh của owner + hình nền BYD Seal là nền mặc định, nhưng CHỈ đóng vào APK dựng tại máy owner: thư mục asset bị
 * gitignore (repo công khai, ảnh BYD có bản quyền). Bản clone sạch ⇒ thư mục không có ⇒ [list] rỗng ⇒ nền vẽ bằng mã
 * ([WallDefault]) như trước. Không danh sách tệp cứng nào: đọc thư mục lúc chạy (`AssetManager.list`).
 *
 * Ảnh đóng gói đi CÙNG đường ảnh của người dùng ([WallpaperController.step]: giải mã giảm cỡ theo màn → [WallArtBuilder] →
 * [WallView] + [WallArtStore]) ⇒ kính · [GlassVeil] · lớp làm tối y hệt. Đường dẫn mang tiền tố [PREFIX] (đường dẫn ảnh người
 * dùng luôn là đường tuyệt đối `/…`) để hai nguồn không lẫn nhau.
 */
internal object DefaultWallpapers {

    private const val TAG = "Wallpaper"

    /** Thư mục trong `assets/` — gitignored, xem `.gitignore` + docs/diagnostics/widget-visual-301-2026-10-13.md §ảnh mặc định. */
    const val ASSET_DIR = "wall-default"

    const val PREFIX = "asset:$ASSET_DIR/"

    /** Bộ đệm ảnh mờ RIÊNG (trong `cacheDir`): bộ dọn của thư mục người dùng coi khoá asset là mồ côi và xoá mỗi lượt. */
    private const val ART_DIR = "wall-default-art"

    @Volatile private var cached: List<String>? = null

    fun isBundled(path: String): Boolean = path.startsWith(PREFIX)

    /**
     * Ảnh đóng gói (đường dẫn có [PREFIX]), thứ tự ổn định. Asset không đổi trong một tiến trình ⇒ đọc MỘT lần. Lỗi đọc ⇒
     * rỗng (lùi nền vẽ bằng mã), có log. Gọi ở thread nền.
     */
    fun list(ctx: Context): List<String> {
        cached?.let { return it }
        val names = try {
            ctx.assets.list(ASSET_DIR)?.toList()
        } catch (e: IOException) {
            Log.w(TAG, "không đọc được asset $ASSET_DIR: ${e.javaClass.simpleName}"); null
        }
        val out = WallSourcePolicy.bundledFrom(names).map { PREFIX + it }
        Log.i(TAG, if (out.isEmpty()) "không có ảnh nền đóng gói ⇒ nền vẽ bằng mã" else "ảnh nền đóng gói: ${out.size} ảnh")
        cached = out
        return out
    }

    /** Giải mã giảm cỡ theo khung — CÙNG luật hạ cỡ với ảnh người dùng ([WallpaperStore.decodeScaled]). `null` = hỏng. */
    fun loadScaled(ctx: Context, path: String, reqW: Int, reqH: Int): Bitmap? {
        val name = assetName(path) ?: return null
        val am = ctx.assets
        return WallpaperStore.decodeScaled(reqW, reqH) { opts -> am.open(name).use { BitmapFactory.decodeStream(it, null, opts) } }
    }

    /**
     * Ô bộ đệm ảnh mờ cho ảnh đóng gói: tem = lúc cài/nâng cấp APK (asset chỉ đổi khi cài bản mới), cỡ = cỡ asset (JPEG
     * không nén lại trong APK ⇒ `openFd` được; không được thì 0). Nguồn để dọn = chính danh sách asset.
     */
    fun artCache(ctx: Context, path: String): WallArtBuilder.ArtCache? {
        val name = assetName(path) ?: return null
        val stamp = PackageQueries.packageInfo(ctx.packageManager, ctx.packageName)?.lastUpdateTime ?: 0L
        val size = runCatching { ctx.assets.openFd(name).use { it.length } }.getOrDefault(0L).coerceAtLeast(0L)
        val dir = File(ctx.cacheDir, ART_DIR)
        val sources = list(ctx).map { it.removePrefix(PREFIX) }.toSet()
        return WallArtBuilder.ArtCache(dir, name.substringAfterLast('/'), stamp.coerceAtLeast(0L), size) { sources }
    }

    private fun assetName(path: String): String? =
        if (isBundled(path)) ASSET_DIR + "/" + path.removePrefix(PREFIX) else null
}
