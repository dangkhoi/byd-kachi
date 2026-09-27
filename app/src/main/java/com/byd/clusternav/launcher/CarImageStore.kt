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
 * DST_OUT: `dst.alpha × (1 − src.alpha)` ⇒ mép (src đục) bị xoá, giữa (src trong suốt) giữ nguyên. **Chỉ mép nào ĐẶC
 * MỰC mới được feather** (2.74 · R2, [edgeInkRatio] + [CarLayout.featherBand]): ảnh chụp chữ nhật ⇒ mép tan vào màu
 * thẻ như cũ; ảnh CẮT NỀN ⇒ 0 dải, vì ở đó "mép" chính là gương/mũi xe và làm mềm = xoá mất thân xe.
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
     * Hai bậc như [WallpaperStore.loadScaled]: [sample] = luỹ thừa 2 lớn nhất mà ảnh còn phủ khung (giảm thô ngay
     * trong bộ giải mã, không sinh ảnh to); rồi [inDensity]→[inTargetDensity] hạ tiếp cho ảnh **VỪA LỌT khung**
     * (fit, giữ tỉ lệ — xem [fitWidth]) cũng ngay trong lúc giải mã. `inTargetDensity == 0` ⇒ không cần bậc hai.
     *
     * ## Vì sao FIT ở đây, trong khi hình NỀN dùng COVER (2026-09-26 · nợ RAM đã trả)
     * Ảnh xe được vẽ **letterbox** (`CarImageLayer.fitRect` lấy `minOf`), nên mọi pixel mà cover cấp thêm ở chiều
     * rộng hơn là pixel **không bao giờ được vẽ**: [ĐO số học] khung 210×554 với ảnh mặc định 678×1397 ⇒ cover cho
     * bitmap 269×554 = 149 026 px, fit cho 210×432 = 90 720 px ⇒ **1,64× pixel** trả cho không. Hình nền thì khác
     * (phủ-kín-rồi-cắt) nên [WallpaperStore.scaledWidth] giữ nguyên `maxOf` — hai câu hỏi khác nhau, hai hàm.
     *
     * Trên cả lưới khung của `CarImageStoreTest` [ĐO] tổng pixel của ba lớp xuống còn **1/3,6** so với cover.
     *
     * 2.76 · R11 (3): **cả hai nguồn** đi qua kế hoạch này — ảnh MẶC ĐỊNH ([loadDefaultScaled], từ closeout) và ảnh
     * NGƯỜI DÙNG bỏ vào thư mục ([loadUserScaled]). Trước 2.76 ảnh người dùng còn đi [WallpaperStore.loadScaled]
     * (cover — cửa dùng chung với hình nền): [ĐO số học, `CarImageStoreTest`] ảnh chụp điện thoại 3000×4000 vào khung
     * lốp 210×554 ⇒ cover giải mã 415×553 (229 495 px) trong khi fit chỉ cần 210×280 (58 800 px) — **3,9×** pixel
     * không bao giờ được vẽ, ở đúng ca ảnh to nhất (ảnh xe mặc định chỉ 678×1397). Một [decodeOptions] cho cả hai.
     */
    data class DecodePlan(val sample: Int, val inDensity: Int, val inTargetDensity: Int) {
        val scaled: Boolean get() = inTargetDensity > 0 && inDensity > 0 && inTargetDensity != inDensity
    }

    /**
     * Bề rộng đích để ảnh [sampledW]×[sampledH] **vừa LỌT** khung [reqW]×[reqH] (fit, giữ tỉ lệ); `0` = không cần hạ
     * thêm (ảnh đã nhỏ hơn khung). Song sinh của [WallpaperStore.scaledWidth] và khác nó đúng một chữ: `minOf` thay
     * cho `maxOf` — xem KDoc [DecodePlan] về việc hai bề mặt hỏi hai câu khác nhau.
     *
     * Làm tròn **nửa lên** (`+ 0.5f`) chứ không cắt: cắt thì chiều siết hụt khung 1–2 px (`339×699` cho khung
     * `340×700`) ⇒ lớp vẽ phải PHÓNG ảnh lên một chút, tức đánh đổi độ nét để tiết kiệm 0,3% RAM. Làm tròn thì chiều
     * siết chạm đúng khung, chiều kia lệch ≤ 1 px (có bài canh trên cả lưới khung).
     */
    fun fitWidth(sampledW: Int, sampledH: Int, reqW: Int, reqH: Int): Int {
        if (sampledW <= 0 || sampledH <= 0 || reqW <= 0 || reqH <= 0) return 0
        val scale = minOf(reqW.toFloat() / sampledW, reqH.toFloat() / sampledH)
        if (scale >= 1f) return 0
        return (sampledW * scale + 0.5f).toInt().coerceAtLeast(1)
    }

    fun decodePlan(srcW: Int, srcH: Int, reqW: Int, reqH: Int): DecodePlan {
        if (srcW <= 0 || srcH <= 0 || reqW <= 0 || reqH <= 0) return DecodePlan(1, 0, 0)
        val sample = WallpaperStore.sampleSize(srcW, srcH, reqW, reqH)
        val sampledW = srcW / sample
        val sampledH = srcH / sample
        val targetW = fitWidth(sampledW, sampledH, reqW, reqH)
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
     * vẽ placeholder. Cả ảnh người dùng lẫn ảnh mặc định giải mã theo **fit** ([decodePlan]) — xem KDoc [DecodePlan].
     */
    fun loadFeathered(ctx: Context, reqW: Int, reqH: Int): Bitmap? {
        if (reqW <= 0 || reqH <= 0) return null
        val scaled = imagePath(ctx)?.let { loadUserScaled(it, reqW, reqH) }
            ?: loadDefaultScaled(ctx, reqW, reqH)   // thư mục trống ⇒ ảnh MẶC ĐỊNH đóng theo APK
            ?: return null
        return runCatching { feather(scaled) }.getOrElse {
            Log.w(TAG, "feather hỏng, dùng ảnh thô: ${it.javaClass.simpleName}")
            scaled
        }
    }

    /**
     * `BitmapFactory.Options` cho một [DecodePlan] — chỗ DUY NHẤT dịch kế hoạch sang cờ giải mã, để ảnh mặc định và
     * ảnh người dùng không thể lệch nhau một cờ. `inSampleSize` giảm thô ngay trong bộ giải mã; `inScaled` +
     * `inDensity`→`inTargetDensity` hạ tiếp về đúng khung cũng ngay lúc giải mã (không sinh bitmap to rồi thu nhỏ).
     */
    private fun decodeOptions(plan: DecodePlan): BitmapFactory.Options = BitmapFactory.Options().apply {
        inSampleSize = plan.sample
        if (plan.scaled) {
            inScaled = true
            inDensity = plan.inDensity
            inTargetDensity = plan.inTargetDensity
        }
    }

    /**
     * Giải mã ảnh xe NGƯỜI DÙNG (tệp ở [FOLDER]) **vừa lọt khung** — 2.76 · R11 (3). Cùng hai lượt đọc như
     * [WallpaperStore.loadScaled] (lượt đầu chỉ kích thước), khác đúng ở kế hoạch: [decodePlan] (fit) thay cho
     * cover. `null` nếu tệp hỏng / không phải ảnh.
     */
    private fun loadUserScaled(path: String, reqW: Int, reqH: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        BitmapFactory.decodeFile(path, decodeOptions(decodePlan(bounds.outWidth, bounds.outHeight, reqW, reqH)))
    }.getOrElse {
        Log.w(TAG, "không giải mã được ảnh xe người dùng: ${it.javaClass.simpleName}")
        null
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
        val opts = decodeOptions(decodePlan(bounds.outWidth, bounds.outHeight, reqW, reqH))
        am.open(DEFAULT_ASSET).use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrElse {
        Log.w(TAG, "không đọc được ảnh xe mặc định: ${it.javaClass.simpleName}")
        null
    }

    /** Alpha từ mức này trở lên được coi là CÓ MỰC khi đo mép ảnh (dưới mức này là viền khử răng cưa/nhiễu). */
    const val INK_ALPHA = 8

    /**
     * Tỉ lệ MỰC trên từng mép của [b] — `[trái, trên, phải, dưới]`, mỗi số trong `0f..1f`. Quét alpha **MỘT LẦN**
     * lúc nạp, ở luồng nền (cùng chỗ đã tính feather ⇒ 0 chi phí lúc vẽ).
     *
     * Vì sao đo TỈ LỆ MỰC chứ không đo LỀ TRONG SUỐT: hai ca dưới đây có lề **giống nhau** (= 0) mà cần hai xử lý
     * NGƯỢC nhau, nên lề không phân biệt được:
     *  • ảnh chụp chữ nhật đặc ⇒ cả mép đặc mực (tỉ lệ ≈ 1) ⇒ ĐÁNG feather (mép cứng tan vào nền thẻ);
     *  • ảnh cắt nền cắt sát (gương/mũi xe đúng biên ảnh) ⇒ chỉ vài dòng chạm mép (tỉ lệ nhỏ) ⇒ feather ở đó là
     *    **xoá alpha của thân xe** — đúng lỗi "mất gương" mà 2.74 đang vá.
     * Ngưỡng quyết định nằm ở `:core` ([CarLayout.SOLID_EDGE_INK]), test off-car.
     *
     * Ảnh **không có kênh alpha** (ảnh chụp JPEG/RGB_565) ⇒ trả `1` cho cả 4 mép ngay, không quét: mọi mép đều đặc
     * mực. Đọc pixel hỏng (bitmap cấu hình lạ, vd HARDWARE) ⇒ cũng trả `1` = **hành vi trước 2.74**, không ném.
     *
     * "Chạm mép" có dung sai [CarLayout.HARD_EDGE_PX] px để 1 px viền khử răng cưa của ảnh chụp vẫn tính là chạm.
     */
    fun edgeInkRatio(b: Bitmap): FloatArray {
        val w = b.width
        val h = b.height
        val solid = floatArrayOf(1f, 1f, 1f, 1f)
        if (w <= 0 || h <= 0 || !b.hasAlpha()) return solid
        return runCatching {
            val hard = CarLayout.HARD_EDGE_PX.toInt()
            val row = IntArray(w)
            var leftRows = 0; var rightRows = 0; var topInk = 0; var bottomInk = 0
            for (y in 0 until h) {
                b.getPixels(row, 0, w, 0, y, w, 1)
                var x0 = -1; var x1 = -1; var ink = 0
                for (x in 0 until w) {
                    if ((row[x] ushr 24) >= INK_ALPHA) { if (x0 < 0) x0 = x; x1 = x; ink++ }
                }
                if (x0 in 0..hard) leftRows++                       // dòng này có mực chạm mép TRÁI
                if (x1 >= 0 && x1 >= w - 1 - hard) rightRows++      // …mép PHẢI
                if (y <= hard) topInk = maxOf(topInk, ink)          // mép TRÊN: dòng ngoài cùng đặc mực tới đâu
                if (y >= h - 1 - hard) bottomInk = maxOf(bottomInk, ink)
            }
            floatArrayOf(leftRows / h.toFloat(), topInk / w.toFloat(), rightRows / h.toFloat(), bottomInk / w.toFloat())
        }.getOrElse {
            Log.w(TAG, "car image alpha scan failed: ${it.javaClass.simpleName}")
            solid
        }
    }

    /**
     * Trả bản feather MỚI của [src] rồi **nhả [src]** (chỗ gọi chỉ giữ ảnh trả về); ảnh KHÔNG có mép cứng nào thì
     * trả về CHÍNH [src] (không tạo bản thứ hai, **không** nhả — bitmap đó là thứ chỗ gọi sẽ giữ).
     *
     * Mỗi mép mờ dần về trong suốt trên dải riêng của nó = [CarLayout.featherBand] của `FEATHER_FRACTION × cạnh
     * nhỏ nhất` và **tỉ lệ mực ĐO ĐƯỢC của chính mép đó** ([edgeInkRatio]) — bằng gradient `DST_OUT`, tính một lần,
     * 0 blur.
     *
     * [ĐO 2026-09-26] vì sao phải đo thay vì áp mù 8 % cả 4 mép: ảnh xe top-down cao-hẹp (asset mặc định 678×1397,
     * KHÔNG có mực nào chạm 4 mép) nhận dải 54 px ⇒ dải ăn 25 px vào THÂN xe mỗi bên ⇒ gương chiếu hậu bị xoá tới
     * ~50 % alpha. Ảnh chụp chữ nhật đặc vẫn được feather đúng như trước (mép đặc mực ⇒ dải = 8 %).
     */
    fun feather(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val full = (minOf(w, h) * FEATHER_FRACTION).coerceAtLeast(1f)
        val edges = edgeInkRatio(src)
        val bands = FloatArray(4) { CarLayout.featherBand(full, edges[it]) }
        if (bands.all { it <= 0f }) return src   // ảnh cắt nền ⇒ không có mép CỨNG nào để làm mềm
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(src, 0f, 0f, null)
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        // MẶT NẠ alpha, KHÔNG phải màu hiển thị: DST_OUT chỉ đọc kênh ALPHA của src (`dst.a × (1 − src.a)`), RGB
        // không vẽ ra đâu cả — nên đây KHÔNG phải một vai màu của [KachiTheme] (ThemePaletteContractTest đúng khi
        // cấm `Color.BLACK`, nhưng ca này là mặt nạ). `0xFF000000` = alpha 255 ở mép ⇒ xoá; TRANSPARENT ⇒ giữ.
        val opaque = OPAQUE_MASK
        val clear = Color.TRANSPARENT
        val wf = w.toFloat(); val hf = h.toFloat()
        // trái
        bands[EDGE_LEFT].takeIf { it > 0f }?.let { m ->
            fade.shader = LinearGradient(0f, 0f, m, 0f, opaque, clear, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, m, hf, fade)
        }
        // trên
        bands[EDGE_TOP].takeIf { it > 0f }?.let { m ->
            fade.shader = LinearGradient(0f, 0f, 0f, m, opaque, clear, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, m, fade)
        }
        // phải
        bands[EDGE_RIGHT].takeIf { it > 0f }?.let { m ->
            fade.shader = LinearGradient(wf - m, 0f, wf, 0f, clear, opaque, Shader.TileMode.CLAMP)
            c.drawRect(wf - m, 0f, wf, hf, fade)
        }
        // dưới
        bands[EDGE_BOTTOM].takeIf { it > 0f }?.let { m ->
            fade.shader = LinearGradient(0f, hf - m, 0f, hf, clear, opaque, Shader.TileMode.CLAMP)
            c.drawRect(0f, hf - m, wf, hf, fade)
        }
        src.recycle()
        return out
    }

    /** Thứ tự 4 mép trong [edgeInkRatio] / dải feather — một quy ước, khai một chỗ. */
    const val EDGE_LEFT = 0
    const val EDGE_TOP = 1
    const val EDGE_RIGHT = 2
    const val EDGE_BOTTOM = 3
}
