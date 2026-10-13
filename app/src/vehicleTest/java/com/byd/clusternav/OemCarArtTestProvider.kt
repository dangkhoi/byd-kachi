package com.byd.clusternav

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.byd.clusternav.launcher.Car3dModelSource
import com.byd.clusternav.launcher.CarArtCatalog
import com.byd.clusternav.launcher.OemCarArt

/**
 * ═══ 3.01 R23 — ẢNH XE BYD cho MÁY ẢO (vehicleTest-ONLY) ════════════════════════════════════════════════════════════════
 *
 * Owner 13/10: *"cho phép dùng ảnh firmware BYD trong bản kiểm thử"* — CHỈ bản `vehicleTest`. Máy ảo không có gói
 * `com.byd.carsettings.plugins` ⇒ [OemCarArt] không mở được tài nguyên hệ thống ⇒ móc [OemCarArt.bundled] trả ảnh trích firmware
 * đóng trong `src/vehicleTest/assets/oem-car/` (không có trong release/debug — bài canh `OemCarArtSourceSetContractTest`).
 *
 * Provider (không xuất, không dữ liệu) chỉ để chạy [onCreate] lúc tiến trình khởi động — trước khi màn nhà vẽ bảng xe. Trên xe
 * thật có gói BYD thì đường hệ thống thắng (móc chỉ được hỏi khi đường đó trả `null`); đời xe khác Seal ⇒ không trả gì.
 */
class OemCarArtTestProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        OemCarArt.bundled = ::bundled
        // 3.01 R25 — mô hình 3D Seal trích firmware (`assets/oem-3d/`, .gitignore) cho widget xe 3D trên máy ảo.
        Car3dModelSource.bundled = { it.applicationContext.assets }
        return true
    }

    private fun bundled(ctx: Context, wanted: String?): OemCarArt.Source? {
        val art = CarArtCatalog.SEAL_TOP
        if (wanted != null && wanted != art.key) return null
        val path = "$DIR/${art.key}.png"
        val am = ctx.applicationContext.assets
        if (runCatching { am.list(DIR)?.contains("${art.key}.png") }.getOrNull() != true) return null
        return OemCarArt.Source(art) { am.open(path) }
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    private companion object {
        const val DIR = "oem-car"
    }
}
