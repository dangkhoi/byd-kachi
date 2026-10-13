package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import com.byd.clusternav.modules.clustercast.ClusterProfile
import java.io.InputStream

/**
 * ═══ 3.01 R23 — ẢNH XE CHÍNH HÃNG BYD, đọc LÚC CHẠY từ app hệ thống (không phân phối lại) ══════════════════════════════
 *
 * Owner 13/10 (Pass 4): xe vẽ tay "như đồ chơi con nít" ⇒ bảng hình xe (Kính/Lốp/Cửa) dùng ảnh nhìn-trên của CarSettings.
 * Bản phát hành KHÔNG đóng gói ảnh BYD nào: nó mở tài nguyên của gói [PKG] (có sẵn trên xe, `/system/priv-app/
 * CarSettingsPlugins/`) bằng `createPackageContext` + `getIdentifier` — đúng đường CarSettings tự dùng
 * (`PluginResourceLoader.init` → `createPackageContext(DeviceUtil.getPluginPkgName(), 3).getResources()` [ĐO jadx]).
 *
 * Chọn ảnh: tên theo đời xe ([CarArtCatalog.oemTopViewName] từ `persist.sys.car.type`) VÀ phải có hình học đã đo
 * ([CarArtCatalog.byKey]) — ảnh chưa đo thì không dùng (mặt nạ lệch). Mọi hỏng hóc (gói vắng, tên vắng, cỡ ảnh khác số đo,
 * giải mã lỗi) ⇒ `null` ⇒ ảnh mặc định đóng theo APK — không bao giờ ảnh BYD với hình học sai.
 *
 * [bundled] = MÓC cho bản `vehicleTest` (máy ảo không có gói BYD): chỉ mã trong `src/vehicleTest` gán nó (owner 13/10 cho phép
 * dùng ảnh trích firmware CHỈ trong bản kiểm thử). Bản release/debug không ai gán ⇒ luôn `null`.
 */
object OemCarArt {

    private const val TAG = "OemCarArt"

    /** Gói tài nguyên CarSettings (`Constants.SETTINGS_PLUGIN_PKG_NAME` [ĐO jadx], `aapt2 dump packagename` [ĐO]). */
    const val PKG = "com.byd.carsettings.plugins"

    /** Một ảnh BYD đã chọn: hình học [art] + cách mở luồng PNG gốc. */
    class Source(val art: CarArt, val open: () -> InputStream?)

    /** Nguồn kiểm thử: nhận tên ảnh muốn (có thể `null` = không rõ đời xe) ⇒ trả nguồn đóng gói trong bản kiểm thử. */
    @Volatile var bundled: ((Context, String?) -> Source?)? = null

    private val lock = Any()
    @Volatile private var resolved = false
    @Volatile private var cached: Source? = null

    /** Ảnh BYD dùng được cho xe này (giải một lần mỗi tiến trình); `null` ⇒ ảnh mặc định. */
    fun source(ctx: Context): Source? {
        if (resolved) return cached
        synchronized(lock) {
            if (!resolved) {
                cached = resolve(ctx.applicationContext)
                resolved = true
                Log.i(TAG, "car art = ${cached?.art?.key ?: "default"}")
            }
            return cached
        }
    }

    /** Ảnh vừa chọn hỏng khi giải mã ⇒ quên nó cho tới hết tiến trình (lượt sau rơi về ảnh mặc định, khoá cache đổi). */
    fun markBroken(key: String) {
        synchronized(lock) {
            if (cached?.art?.key == key) {
                Log.w(TAG, "ảnh $key hỏng ⇒ dùng ảnh mặc định")
                cached = null
                resolved = true
            }
        }
    }

    private fun resolve(ctx: Context): Source? {
        val name = runCatching { CarArtCatalog.oemTopViewName(ClusterProfile.carType(ctx)) }.getOrNull()
        fromSystem(ctx, name)?.let { return it }
        return runCatching { bundled?.invoke(ctx, name) }.getOrNull()
    }

    /** Đường release DUY NHẤT: tài nguyên của gói hệ thống [PKG]. */
    private fun fromSystem(ctx: Context, name: String?): Source? {
        val art = CarArtCatalog.byKey(name) ?: return null
        return runCatching {
            val res = ctx.createPackageContext(PKG, 0).resources
            val id = res.getIdentifier(art.key, "drawable", PKG)
            if (id == 0) null else Source(art) { res.openRawResource(id) }
        }.getOrElse {
            Log.i(TAG, "không mở được $PKG: ${it.javaClass.simpleName}")
            null
        }
    }

    /**
     * Giải mã [src] thành bitmap ĐÃ cắt + xoay về khung hiển thị, vừa lọt [reqW]×[reqH] (fit — cùng lẽ [CarImageStore.decodePlan]).
     * Cỡ ảnh gốc khác số đo của [CarArt] (firmware đổi ảnh) ⇒ `null` — hình học sẽ lệch, không được vẽ.
     */
    fun decode(src: Source, reqW: Int, reqH: Int): Bitmap? = runCatching {
        val art = src.art
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Lượt đo cỡ: `decodeStream` LUÔN trả null khi `inJustDecodeBounds` ⇒ không lấy kết quả của nó làm điều kiện.
        (src.open() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth != art.srcW || bounds.outHeight != art.srcH) {
            Log.w(TAG, "${art.key}: cỡ ${bounds.outWidth}×${bounds.outHeight} ≠ số đo ${art.srcW}×${art.srcH}")
            return null
        }
        val fit = minOf(reqW / art.frameW, reqH / art.frameH, 1f)
        val sample = WallpaperStore.sampleSize(art.frameW.toInt(), art.frameH.toInt(),
            (art.frameW * fit).toInt().coerceAtLeast(1), (art.frameH * fit).toInt().coerceAtLeast(1))
        val full = src.open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val k = full.width.toFloat() / art.srcW
        val c = art.crop
        val x = (c[0] * k).toInt(); val y = (c[1] * k).toInt()
        val w = ((c[2] - c[0]) * k).toInt().coerceIn(1, full.width - x)
        val h = ((c[3] - c[1]) * k).toInt().coerceIn(1, full.height - y)
        val m = Matrix().apply {
            val s = (fit / k).coerceAtMost(1f)
            postScale(s, s)
            if (art.rotateCw) postRotate(90f)
        }
        val out = Bitmap.createBitmap(full, x, y, w, h, m, true)
        if (out !== full) full.recycle()
        out
    }.getOrElse {
        Log.w(TAG, "giải mã ${src.art.key} hỏng: ${it.javaClass.simpleName}")
        null
    }
}
