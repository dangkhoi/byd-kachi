package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 *
 * ## Cache DÙNG CHUNG theo (nguồn, cỡ đích) — closeout 2026-09-25 (spec `kachi-closeout-hardening` R3, audit RAM §8)
 * Trước: mỗi [CarImageLayer] (bảng lốp · bảng cửa · xe mini) tự giải mã + feather một bản riêng, và ảnh MẶC ĐỊNH
 * (678×1397, 3,79 MB ARGB) chỉ có `inSampleSize` (luỹ thừa 2) mà **thiếu bước hạ đúng khung** (`inScaled`) ⇒ có thể
 * to gấp 2×/trục (4× pixel) so với cần. Nay: (1) giải mã theo ĐÚNG khung đích ([decodePlan] — cùng công thức
 * [WallpaperStore.loadScaled]); (2) một [SharedLru] nhỏ (≤ [MAX_SHARED] bản) khoá bằng [CacheKey] =
 * (dấu-vết-tệp, khung gom bậc 32 px): các lớp cùng khoá dùng chung MỘT bitmap đã feather; lớp tạo lại (ô tháo/gắn)
 * lấy lại ngay không giải mã. **Quyền sở hữu bitmap = KHO**: chỗ dùng KHÔNG `recycle()` (một lớp khác có thể còn
 * đang vẽ nó); bản bị đẩy khỏi LRU không recycle mà để GC nhả — tránh "trying to use a recycled bitmap" khi 2 view
 * chia nhau một ảnh.
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

    /** Bậc gom cỡ khung (px): đổi cỡ nhỏ hơn bậc này KHÔNG sinh khoá mới ⇒ không giải mã lại. */
    const val BUCKET_PX = 32

    /** Số bản ảnh xe giữ đồng thời trong kho chung (3 bảng dùng ảnh xe ⇒ tối đa 3 cỡ khác nhau). */
    const val MAX_SHARED = 3

    /** CLOSE-5 · thử-lại khi nạp HỎNG: mốc đầu 1 s → ×2 mỗi lần hỏng liên tiếp → trần 60 s (khuôn `HalAbsentCache`). */
    const val RETRY_FIRST_MS = 1_000L
    const val RETRY_MAX_MS = 60_000L

    /**
     * Lịch thử-lại theo ĐỒNG HỒ cho một [CarImageLayer] khi nạp HỎNG (CLOSE-5, backlog 2026-09-26) — THUẦN, nhận
     * `nowMs` làm tham số ⇒ test off-car bằng đồng hồ giả, không `Thread.sleep`.
     *
     * Vì sao "giãn dần" chứ không "cấm tới khi khoá đổi": một lần `null` từ [shared] KHÔNG chứng minh *"ảnh này
     * hỏng vĩnh viễn"* — có thể là tệp đang được chép dở vào thư mục `car/`, bộ nhớ ngoài vừa mount, hay OOM tạm
     * lúc giải mã. Cấm tới khi khung/tệp đổi ⇒ placeholder mãi cho tới lần đổi cỡ (review P3). Thử lại mỗi khung vẽ
     * ⇒ vòng nạp vô hạn ở tốc độ giải mã (lỗi đã vá ở `ensure()` trước đó). Ở giữa: [firstRetryMs] → ×2 → … →
     * trần [maxRetryMs]; nạp được ⇒ [reset] (đếm lại từ đầu, cùng bất biến `HalAbsentCache.record(got = true)`).
     *
     * Không đồng bộ: dùng trên MỘT luồng (luồng chính — `ensure()` từ `onDraw` và callback `main.post`).
     */
    class LoadRetry(
        private val firstRetryMs: Long = RETRY_FIRST_MS,
        private val maxRetryMs: Long = RETRY_MAX_MS,
    ) {
        /** Số lần hỏng LIÊN TIẾP kể từ lần nạp được cuối. */
        var failures: Int = 0
            private set

        /** Mốc (cùng đơn vị `nowMs`) từ đó được thử lại; `0` = chưa có lần hỏng nào ⇒ thử ngay. */
        var nextRetryAt: Long = 0L
            private set

        /** Có được xếp một lượt nạp ở thời điểm [nowMs] không. Chưa hỏng lần nào ⇒ luôn `true`. */
        fun shouldTry(nowMs: Long): Boolean = failures == 0 || nowMs >= nextRetryAt

        /** Ghi một lần hỏng tại [nowMs]; trả khoảng chờ (ms) tới mốc thử lại kế — chỗ gọi dùng để hẹn đánh thức. */
        fun recordFailure(nowMs: Long): Long {
            failures++
            val delay = delayMs(failures)
            nextRetryAt = nowMs + delay
            return delay
        }

        /** Nạp được (hoặc đổi khoá/tháo ô) ⇒ quên sạch lịch giãn. */
        fun reset() {
            failures = 0
            nextRetryAt = 0L
        }

        /** Khoảng chờ sau lần hỏng thứ [failures] (1-based): `first × 2^(n−1)`, trần [maxRetryMs]; `n ≤ 0` ⇒ 0. */
        fun delayMs(failures: Int): Long {
            if (failures <= 0) return 0L
            // Dịch bit có trần số mũ (30) để không tràn Long trước khi kẹp; trần thật là maxRetryMs.
            return (firstRetryMs shl (failures - 1).coerceAtMost(30)).coerceAtMost(maxRetryMs)
        }
    }

    /**
     * Khoá cache dùng chung: dấu-vết-tệp ([signature]) + khung đích ĐÃ gom bậc [BUCKET_PX]. [w]/[h] cũng chính là
     * cỡ yêu cầu khi giải mã ⇒ khoá xác định hoàn toàn bitmap, mọi lớp cùng khoá nhận đúng cùng một bản.
     */
    data class CacheKey(val signature: String, val w: Int, val h: Int)

    /** Gom [v] lên bậc [BUCKET_PX] (ceil). `v ≤ 0` ⇒ 0. */
    fun bucket(v: Int): Int = if (v <= 0) 0 else ((v + BUCKET_PX - 1) / BUCKET_PX) * BUCKET_PX

    fun cacheKey(signature: String, w: Int, h: Int): CacheKey = CacheKey(signature, bucket(w), bucket(h))

    /**
     * Kế hoạch giải mã một ảnh [srcW]×[srcH] cho khung [reqW]×[reqH] — thuần, test off-car.
     *
     * Hai bậc như [WallpaperStore.loadScaled]: [sample] = luỹ thừa 2 lớn nhất mà ảnh còn PHỦ khung (giảm thô ngay
     * trong bộ giải mã, không sinh ảnh to); rồi [inDensity]→[inTargetDensity] hạ tiếp về ĐÚNG khung (fit, giữ tỉ
     * lệ) cũng ngay trong lúc giải mã. `inTargetDensity == 0` ⇒ không cần bậc hai.
     */
    data class DecodePlan(val sample: Int, val inDensity: Int, val inTargetDensity: Int) {
        val scaled: Boolean get() = inTargetDensity > 0 && inDensity > 0 && inTargetDensity != inDensity
    }

    fun decodePlan(srcW: Int, srcH: Int, reqW: Int, reqH: Int): DecodePlan {
        if (srcW <= 0 || srcH <= 0 || reqW <= 0 || reqH <= 0) return DecodePlan(1, 0, 0)
        val sample = WallpaperStore.sampleSize(srcW, srcH, reqW, reqH)
        val sampledW = srcW / sample
        val sampledH = srcH / sample
        val targetW = WallpaperStore.scaledWidth(sampledW, sampledH, reqW, reqH)
        return if (targetW > 0) DecodePlan(sample, sampledW, targetW) else DecodePlan(sample, 0, 0)
    }

    /** Cỡ bitmap [plan] sẽ cho ra từ ảnh [srcW]×[srcH] (ước, làm tròn như BitmapFactory) — để test đo pixel. */
    fun plannedSize(srcW: Int, srcH: Int, plan: DecodePlan): Pair<Int, Int> {
        val sw = srcW / plan.sample; val sh = srcH / plan.sample
        if (!plan.scaled) return sw to sh
        val f = plan.inTargetDensity.toFloat() / plan.inDensity
        return (sw * f + 0.5f).toInt().coerceAtLeast(1) to (sh * f + 0.5f).toInt().coerceAtLeast(1)
    }

    /**
     * LRU có chặn cỡ, thuần (không Android) — tách lớp để test off-car. Truy cập = làm mới thứ tự; vượt [max] ⇒ bỏ
     * bản CŨ NHẤT (không huỷ: cho [Bitmap] thì để GC nhả, xem KDoc lớp). Mọi thao tác đồng bộ theo `this`.
     */
    class SharedLru<K : Any, V : Any>(private val max: Int) {
        private val map = object : LinkedHashMap<K, V>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > max
        }
        @Synchronized fun get(key: K): V? = map[key]
        @Synchronized fun put(key: K, value: V) { map[key] = value }
        @Synchronized fun remove(key: K): V? = map.remove(key)
        @Synchronized fun size(): Int = map.size
        @Synchronized fun keys(): List<K> = map.keys.toList()

        /**
         * Lấy theo [key]; thiếu ⇒ gọi [load] (có thể chậm — gọi NGOÀI khoá) rồi cất. Nếu trong lúc nạp đã có ai cất
         * cùng khoá thì dùng bản ĐÃ CÓ, bỏ bản mới (không hai bản cho một khoá). `null` từ [load] ⇒ không cất.
         */
        fun getOrLoad(key: K, load: () -> V?): V? {
            get(key)?.let { return it }
            val fresh = load() ?: return null
            synchronized(this) {
                map[key]?.let { return it }
                map[key] = fresh
            }
            return fresh
        }
    }

    private val shared = SharedLru<CacheKey, Bitmap>(MAX_SHARED)

    /** Ảnh đã có sẵn trong kho cho [key] (không giải mã) — gọi được từ luồng vẽ, O(1). */
    fun peek(key: CacheKey): Bitmap? = shared.get(key)?.takeIf { !it.isRecycled }

    /**
     * Ảnh feather dùng chung cho [key] — gọi ở luồng NỀN (giải mã khi kho chưa có). Bitmap trả về do KHO sở hữu:
     * chỗ gọi chỉ giữ tham chiếu, **không** `recycle()`.
     */
    fun shared(ctx: Context, key: CacheKey): Bitmap? {
        peek(key)?.let { return it }
        shared.remove(key)   // bản cũ đã recycled (phòng hờ) ⇒ bỏ, nạp lại
        return shared.getOrLoad(key) { loadFeathered(ctx, key.w, key.h) }
    }

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

    /**
     * Giải mã ảnh xe MẶC ĐỊNH từ assets **đúng khung** ([decodePlan]: `inSampleSize` + `inScaled` hạ về đích ngay
     * trong bộ giải mã — trước chỉ có `inSampleSize` ⇒ có thể to 2×/trục). `null` nếu asset thiếu/hỏng.
     */
    private fun loadDefaultScaled(ctx: Context, reqW: Int, reqH: Int): Bitmap? = runCatching {
        val am = ctx.applicationContext.assets
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        am.open(DEFAULT_ASSET).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val plan = decodePlan(bounds.outWidth, bounds.outHeight, reqW, reqH)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = plan.sample
            if (plan.scaled) {
                inScaled = true
                inDensity = plan.inDensity
                inTargetDensity = plan.inTargetDensity
            }
        }
        am.open(DEFAULT_ASSET).use { BitmapFactory.decodeStream(it, null, opts) }
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
