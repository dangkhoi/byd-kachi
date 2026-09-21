package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.Log
import com.byd.clusternav.R
import java.io.File

/**
 * ═══ WP3-v5 · KHO ẢNH XE — hình xe tổng hợp nay là ẢNH bitmap (owner bỏ vector car) ══════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP3-v5. Dùng lại **nguyên mẫu [WallpaperStore]** (owner: *"picker như
 * wallpaper"*): đọc từ **thư mục riêng của app** `Android/data/<gói>/files/car/`, KHÔNG cần quyền, KHÔNG dùng màn
 * chọn tệp hệ thống (khoá trên xe), nạp **giảm cỡ**. Ba lý do y hệt [WallpaperStore].
 *
 * ## "user thay được" mà KHÔNG có khoá pref — vì sao
 * [ĐO] `SettingsCoverageContractTest` quét mọi `getSharedPreferences`/`put*` và đòi khoá phải được xếp loại ở
 * [ProfileScope] + [SettingsCatalog]. Một khoá `car_image` mới ⇒ kéo theo coupling đó. Thay vào đó: **ảnh mới
 * nhất trong thư mục thắng** ([imagePath]). Thư mục riêng của app **đã là device-wide** (không đi theo hồ sơ),
 * nên đây đúng là "lưu theo XE" mà không cần một khoá lưu bền nào. Owner thay ảnh = thả tệp mới vào thư mục.
 * Thư mục **trống** ⇒ dùng ảnh MẶC ĐỊNH đóng theo APK ([DEFAULT_ASSET]) — widget hình xe có hình ngay từ lần mở
 * đầu, không phải một silhouette trống mà người dùng không biết phải làm gì.
 * (Chọn tường minh giữa nhiều ảnh là follow-up nhỏ nếu owner cần — backlog.)
 *
 * ## Feather (mờ cạnh) — cho ảnh chữ-nhật tiệp vào nền thẻ, KHÔNG dán cứng
 * [feather] carve alpha ở 4 mép bằng 4 [LinearGradient] chế độ `DST_OUT` (đục ở mép → trong suốt vào trong). Tính
 * **MỘT LẦN** lúc nạp (như ảnh mờ của hình nền) ⇒ **0 blur runtime** — bắt buộc vì xe API 29 GPU yếu (spec R1.3).
 * DST_OUT: `dst.alpha × (1 − src.alpha)` ⇒ mép (src đục) bị xoá, giữa (src trong suốt) giữ nguyên. Nếu ảnh vốn có
 * nền trong suốt thì feather chỉ làm mượt thêm mép chữ-nhật; nếu ảnh đục thì mép tan vào màu thẻ.
 */
object CarImageStore {

    private const val TAG = "CarImage"

    /** Tên thư mục người dùng bỏ ảnh xe vào. */
    const val FOLDER = "car"

    /**
     * Ảnh xe MẶC ĐỊNH đóng theo APK (owner đưa `images/seal-3.png`, AI-gen, KHÔNG có logo/nhãn hiệu — clean;
     * đã cắt + làm trong suốt bằng
     * `scripts/design/gen-default-car.py`). Dùng khi thư mục [FOLDER] chưa có ảnh nào — để widget hình xe **có
     * hình ngay từ lần mở đầu**, thay vì silhouette trống mà người dùng không biết phải làm gì.
     */
    const val DEFAULT_ASSET = "car/default-car.png"

    /** Phần mép được feather, theo cạnh nhỏ nhất của ảnh. 8 % đủ mềm mà không ăn vào thân xe ở giữa. */
    const val FEATHER_FRACTION = 0.08f

    /** Mặt nạ ĐỤC cho DST_OUT (alpha 255) — chỉ kênh alpha có tác dụng, RGB không hiển thị (xem [feather]). */
    private const val OPAQUE_MASK = 0xFF000000.toInt()

    /** Thư mục ảnh xe; `null` nếu bộ nhớ ngoài không dùng được. Tự tạo (để người dùng thấy chỗ mà bỏ vào). */
    fun folder(ctx: Context): File? = runCatching {
        val base = ctx.applicationContext.getExternalFilesDir(null) ?: return null
        File(base, FOLDER).apply { if (!exists()) mkdirs() }
    }.getOrNull()

    /** Đường dẫn để chỉ cho người dùng biết bỏ ảnh vào đâu. */
    fun folderHint(ctx: Context): String =
        folder(ctx)?.absolutePath ?: ctx.getString(R.string.kachi_wall_no_ext_storage)

    /** Tên các tệp ảnh trong thư mục (mới nhất trước) — cho màn Cài đặt liệt kê. */
    fun imageNames(ctx: Context): List<String> = imageFiles(ctx).map { it.name }

    /** Đường dẫn ảnh xe đang dùng = tệp **mới nhất** trong thư mục; `null` nếu chưa có ảnh nào. */
    fun imagePath(ctx: Context): String? = imageFiles(ctx).firstOrNull()?.absolutePath

    /** `true` nếu người dùng đã tự bỏ ảnh vào thư mục (khác với ảnh mặc định đóng theo APK). */
    fun hasUserImage(ctx: Context): Boolean = imageFiles(ctx).isNotEmpty()

    /** Dấu vết đổi ảnh (đường dẫn + lần sửa cuối) — [CarImageLayer] dùng làm khoá cache để nạp lại khi ảnh đổi.
     *  Thư mục trống ⇒ dùng dấu vết của ảnh MẶC ĐỊNH (đóng theo APK) để cache vẫn nạp lại đúng một lần. */
    fun signature(ctx: Context): String =
        imageFiles(ctx).firstOrNull()?.let { "${it.absolutePath}|${it.lastModified()}" } ?: "default:$DEFAULT_ASSET"

    private fun imageFiles(ctx: Context): List<File> = runCatching {
        val dir = folder(ctx) ?: return emptyList()
        dir.listFiles()?.asList()
            ?.filter { it.isFile && isImage(it.name) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }.getOrElse {
        Log.w(TAG, "không đọc được thư mục ảnh xe: ${it.javaClass.simpleName}")
        emptyList()
    }

    private fun isImage(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")
    }

    /**
     * Nạp ảnh xe **đã giảm cỡ + feather** cho khung [reqW]×[reqH]. `null` nếu chưa có ảnh / giải mã hỏng — chỗ gọi
     * vẽ placeholder. Dùng lại [WallpaperStore.loadScaled] cho phần giảm cỡ (một nơi làm việc giải mã an toàn RAM).
     */
    fun loadFeathered(ctx: Context, reqW: Int, reqH: Int): Bitmap? {
        if (reqW <= 0 || reqH <= 0) return null
        val scaled = imagePath(ctx)?.let { WallpaperStore.loadScaled(it, reqW, reqH) }
            ?: loadDefaultScaled(ctx, reqW, reqH)   // thư mục trống ⇒ ảnh MẶC ĐỊNH đóng theo APK
            ?: return null
        return runCatching { feather(scaled) }.getOrElse {
            Log.w(TAG, "feather hỏng, dùng ảnh thô: ${it.javaClass.simpleName}")
            scaled
        }
    }

    /** Giải mã ảnh xe MẶC ĐỊNH từ assets (giảm cỡ như [WallpaperStore.loadScaled]). `null` nếu asset thiếu/hỏng. */
    private fun loadDefaultScaled(ctx: Context, reqW: Int, reqH: Int): Bitmap? = runCatching {
        val am = ctx.applicationContext.assets
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        am.open(DEFAULT_ASSET).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        am.open(DEFAULT_ASSET).use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
    }.getOrElse {
        Log.w(TAG, "không đọc được ảnh xe mặc định: ${it.javaClass.simpleName}")
        null
    }

    /**
     * Trả bản feather MỚI của [src] rồi **nhả [src]** (chỗ gọi chỉ giữ ảnh trả về). Mép mờ dần về trong suốt trên
     * dải [FEATHER_FRACTION] × cạnh nhỏ nhất, bằng 4 gradient `DST_OUT` — tính một lần, 0 blur.
     */
    fun feather(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(src, 0f, 0f, null)
        val m = (minOf(w, h) * FEATHER_FRACTION).coerceAtLeast(1f)
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        // MẶT NẠ alpha, KHÔNG phải màu hiển thị: DST_OUT chỉ đọc kênh ALPHA của src (`dst.a × (1 − src.a)`), RGB
        // không vẽ ra đâu cả — nên đây KHÔNG phải một vai màu của [KachiTheme] (ThemePaletteContractTest đúng khi
        // cấm `Color.BLACK`, nhưng ca này là mặt nạ). `0xFF000000` = alpha 255 ở mép ⇒ xoá; TRANSPARENT ⇒ giữ.
        val opaque = OPAQUE_MASK
        val clear = Color.TRANSPARENT
        val wf = w.toFloat(); val hf = h.toFloat()
        // trái
        fade.shader = LinearGradient(0f, 0f, m, 0f, opaque, clear, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, m, hf, fade)
        // phải
        fade.shader = LinearGradient(wf - m, 0f, wf, 0f, clear, opaque, Shader.TileMode.CLAMP)
        c.drawRect(wf - m, 0f, wf, hf, fade)
        // trên
        fade.shader = LinearGradient(0f, 0f, 0f, m, opaque, clear, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, m, fade)
        // dưới
        fade.shader = LinearGradient(0f, hf - m, 0f, hf, clear, opaque, Shader.TileMode.CLAMP)
        c.drawRect(0f, hf - m, wf, hf, fade)
        src.recycle()
        return out
    }
}
