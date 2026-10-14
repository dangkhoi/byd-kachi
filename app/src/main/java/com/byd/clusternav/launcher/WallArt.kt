package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * ═══ VISUAL-REFRESH P1b · §4.10 — ẢNH NỀN "ĐÃ NẤU" MỘT LẦN: mờ ¼ độ phân giải · lưới độ chói · màu trội ══════════
 *
 * Owner 2026-09-16 (ảnh trên xe): thẻ xám trên ảnh nền là *miếng vá*. §4.10 chốt sáu việc, và ba việc đầu là **dữ
 * liệu tính một lần lúc nạp ảnh**, không phải việc lúc vẽ:
 *  1. ảnh mờ ở **¼ độ phân giải màn**, dựng đúng như [WallView] sẽ vẽ (cùng phép phủ/vừa khung, cùng lớp làm tối)
 *     ⇒ thẻ ở toạ độ màn `(x, y)` chỉ việc nhìn xuống điểm `(x/4, y/4)` của ảnh này, không cần cắt gì thêm;
 *  4. **màu trội** (2–3 màu, [DominantColors]) để nhuộm nhẹ thẻ và làm hạt giống cho ô *theo ảnh nền* (R8);
 *  5. **lưới độ chói** 16×9 (đo trên ảnh **đã làm tối**) để lớp che của từng thẻ chọn theo độ chói *đo được*.
 *
 * Làm mờ bằng **box blur ba lượt** thuần Kotlin trên mảng `Int` (≈ Gaussian) — `RenderScript` đã bị Android bỏ, và
 * mọi thứ ở đây chạy **một lần trên thread nền** rồi lưu xuống đĩa, nên tốc độ của thuật toán không quan trọng
 * bằng việc không có phụ thuộc mới. **0 blur lúc chạy** (AC5.2).
 *
 * ## Bộ đệm đĩa
 * `<thư mục ảnh>/.kachi-art/<tên>-<mtime>-<cỡ>-<WxH>-<phủ>-<tối>.png` + `.txt` (lưới + màu trội). Khoá mang mtime +
 * cỡ tệp ⇒ đổi ảnh cùng tên là tính lại; mang cỡ màn + cách phủ + mức làm tối ⇒ đổi lựa chọn là tính lại. `DiagStorageCap`
 * KHÔNG BAO GIỜ chạm thư mục này (ảnh nền là dữ liệu người dùng — DIAG-CAP-USERDATA 2.92) ⇒ trần là luật RIÊNG của bộ đệm
 * (2.93 `WALLART-CACHE-BOUND`, [WallArtCachePolicy]): mỗi lần nấu khoá mới thì dọn đệm mồ côi + đệm cũ quá trần.
 */
class WallArt(
    /** Ảnh mờ, `ARGB_8888`, cỡ = màn ÷ [scale], ĐÃ làm tối theo lựa chọn người dùng. */
    val blurred: Bitmap,
    val scale: Int,
    val cols: Int,
    val rows: Int,
    /** Độ chói WCAG trung bình của từng ô lưới (hàng-trước), đo trên ảnh đã làm tối, trước khi làm mờ. */
    val lum: FloatArray,
    /** ≤ 3 màu trội, đục, xếp theo điểm giảm dần; rỗng nếu ảnh không có gì để nói. */
    val dominant: IntArray,
    val screenW: Int,
    val screenH: Int,
    /**
     * 3.1.5 R29 — lưới hộp màu p10/p90 ([LumaField]) của ảnh SẮC, CHƯA làm tối, đo ở độ phân giải ảnh đã giải mã, trên đúng vùng
     * ảnh [WallFit] cắt ra; [sharpDst] = vùng màn nó phủ, [sharpScale] = điểm ảnh lưới / điểm ảnh màn (phủ/vừa khung giữ tỉ lệ).
     */
    val sharp: LumaField,
    val sharpDst: Rect,
    val sharpScale: Double,
    /** 3.1.5 R29 — lưới hộp màu của ảnh MỜ [blurred] (đã làm tối), toạ độ của chính ảnh mờ (màn ÷ [scale]). */
    val glass: LumaField,
    /** Mức làm tối đã nấu (cùng số `WallView.setPhoto` nhận) — lớp làm tối của [backdropAt]. */
    val dimPercent: Int,
) {
    /**
     * 3.1.5 R29 (T29-3) — hộp màu ảnh SẮC dưới vùng màn `[l, t, r, b)`; phần vùng nằm NGOÀI ảnh (viền "vừa khung") là nền [bg]
     * mà `WallView` tô trước ảnh. Không bao giờ là một con số giả (khác [luminanceOf]).
     */
    fun toneAt(l: Int, t: Int, r: Int, b: Int, bg: Int): ToneBox {
        val d = sharpDst
        val il = maxOf(l, d.left); val iu = maxOf(t, d.top); val ir = minOf(r, d.right); val ib = minOf(b, d.bottom)
        val inside = if (ir > il && ib > iu) sharp.range(il - d.left, iu - d.top, ir - d.left, ib - d.top, sharpScale) else null
        val border = if (inside == null || l < d.left || t < d.top || r > d.right || b > d.bottom) ToneBox.span(bg) else null
        return when {
            inside == null -> border!!
            border == null -> inside
            else -> inside.union(border)
        }
    }

    /** Hộp màu ảnh MỜ (đã làm tối) dưới vùng màn — thứ thẻ kính vẽ ở chỉ số 0; `null` nếu vùng nằm ngoài màn. */
    fun glassAt(l: Int, t: Int, r: Int, b: Int): ToneBox? = glass.range(l, t, r, b, 1.0 / scale)

    /**
     * 3.1.5 R29 (T29-3) — NỀN THẬT dưới vùng màn: ảnh sắc ([toneAt]) → lớp làm tối theo [dimPercent] (bảng [p], cùng phép
     * `WallView`) → [layers] của bên gọi. Chồng của thẻ/thanh do [AdaptiveInk] ghép tiếp.
     */
    fun backdropAt(l: Int, t: Int, r: Int, b: Int, p: KachiPalette, layers: List<ContrastBackdrop.Layer> = emptyList()): ContrastBackdrop =
        ContrastBackdrop(toneAt(l, t, r, b, ColorMath.withAlpha(ColorMath.parse(p.bg), 255)), listOfNotNull(InkModel.scrim(p, dimPercent)) + layers)


    /** Độ chói trung bình của vùng màn `[l, t, r, b]` (điểm ảnh màn) — trung bình các ô lưới bị vùng đó phủ. */
    fun luminanceOf(l: Int, t: Int, r: Int, b: Int): Double {
        if (cols == 0 || rows == 0 || screenW <= 0 || screenH <= 0) return 0.5
        val c0 = (l * cols / screenW).coerceIn(0, cols - 1)
        val c1 = ((r - 1) * cols / screenW).coerceIn(c0, cols - 1)
        val r0 = (t * rows / screenH).coerceIn(0, rows - 1)
        val r1 = ((b - 1) * rows / screenH).coerceIn(r0, rows - 1)
        var sum = 0.0; var n = 0
        for (y in r0..r1) for (x in c0..c1) { sum += lum[y * cols + x]; n++ }
        return if (n == 0) 0.5 else sum / n
    }
}

/**
 * Chỗ giữ [WallArt] **đang hiển thị** + màu trội của **ảnh đầu tiên** (hạt giống ô *theo ảnh nền*).
 *
 * ## Vì sao là `object` cấp tiến trình chứ không là field của Activity
 * Chọn *theo ảnh nền* ⇒ bảng màu đổi ⇒ màn **dựng lại** (`recreate()`, đúng đường của nút chủ đề). Nếu màu trội
 * chết theo Activity thì `onCreate` mới áp bảng KHÔNG có màu ảnh → ảnh nạp xong lại đổi bảng → dựng lại → … vòng lặp
 * vô tận. Giữ ở tiến trình thì lượt dựng lại đã có sẵn hạt giống ngay ở `ThemeHost.sync` đầu tiên.
 *
 * ## Vì sao hạt giống lấy từ ảnh ĐẦU, không phải ảnh đang chiếu
 * Trình chiếu đổi ảnh mỗi 15 s–30 ph; đổi bảng màu theo từng ảnh nghĩa là dựng lại màn theo nhịp đó — app đang
 * chiếu trong ô bị nhả/gắn liên tục (họ lỗi P-bug1/R3). Ảnh đầu (thứ tự ổn định của [Slideshow.imagesFrom]) là
 * một hạt giống **ổn định** cho tới khi người dùng đổi bộ ảnh; còn lớp nhuộm nhẹ trên từng thẻ thì vẫn theo ảnh
 * đang chiếu (nó nằm trong drawable, không nằm trong bảng màu).
 *
 * **Một chỗ ghi**: chỉ [WallpaperController] gọi [swap]/[clear] (trên thread chính). `ThemeHost` và `KachiGlass` chỉ đọc.
 */
object WallArtStore {
    @Volatile var current: WallArt? = null
        private set

    @Volatile private var firstDominant: IntArray? = null

    /** Màu trội của ảnh đầu tiên — đầu vào của `KachiTheme.applyTheme` cho ô *theo ảnh nền*; `null` = chưa có ảnh. */
    fun accentDominant(): IntArray? = firstDominant

    /** Đặt ảnh đang hiển thị; [first] = đây là ảnh đầu của bộ ⇒ cập nhật hạt giống. Trả về ảnh CŨ để chỗ gọi nhả SAU khi đã vẽ lại. */
    fun swap(art: WallArt, first: Boolean): WallArt? {
        val old = current
        current = art
        if (first || firstDominant == null) firstDominant = art.dominant
        return old
    }

    /** Hình nền tắt / đổi hồ sơ không ảnh ⇒ không còn kính, không còn hạt giống. Trả về ảnh CŨ để nhả sau. */
    fun clear(): WallArt? {
        val old = current
        current = null
        firstDominant = null
        return old
    }
}

/** Phép phủ/vừa khung dùng CHUNG cho [WallView] (vẽ) và [WallArtBuilder] (nấu) — hai bản là hai chỗ để lệch nhau. */
object WallFit {
    /** Điền [src] (vùng ảnh) và [dst] (vùng đích) cho ảnh [bw]×[bh] vào khung [w]×[h] theo [fit]. */
    fun map(bw: Int, bh: Int, w: Float, h: Float, fit: ImageFit, src: Rect, dst: Rect) {
        when (fit) {
            ImageFit.FILL -> {
                // Phủ kín: cắt phần thừa ở giữa ảnh (cắt lệch một bên sẽ mất chủ thể).
                val scale = maxOf(w / bw, h / bh)
                val cw = (w / scale).toInt().coerceAtMost(bw)
                val ch = (h / scale).toInt().coerceAtMost(bh)
                src.set((bw - cw) / 2, (bh - ch) / 2, (bw - cw) / 2 + cw, (bh - ch) / 2 + ch)
                dst.set(0, 0, w.toInt(), h.toInt())
            }
            ImageFit.FIT -> {
                val scale = minOf(w / bw, h / bh)
                val dw = (bw * scale).toInt()
                val dh = (bh * scale).toInt()
                src.set(0, 0, bw, bh)
                val left = ((w.toInt() - dw) / 2); val top = ((h.toInt() - dh) / 2)
                dst.set(left, top, left + dw, top + dh)
            }
        }
    }
}

object WallArtBuilder {
    private const val TAG = "WallArt"
    const val SCALE = 4
    const val COLS = 16
    const val ROWS = 9
    /** Bán kính box blur ở ¼ độ phân giải (≈ 24 px trên màn), ba lượt ≈ Gaussian σ≈10 px màn. */
    const val RADIUS = 6
    const val PASSES = 3
    const val CACHE_DIR = ".kachi-art"
    /** R29: lưới ảnh MỜ 32 cột (ảnh đã mờ σ≈10 px màn ⇒ ô 60 px màn đủ mịn; đệm nhỏ một nửa so với 64 cột). */
    const val GLASS_COLS = 32

    /**
     * Nấu [WallArt] cho ảnh [source] (đã giảm cỡ theo màn) ở [path]. Chạy trên **thread nền**. Lỗi đĩa ⇒ vẫn trả
     * kết quả tính được (đệm chỉ là tiết kiệm, không phải điều kiện). Trả `null` chỉ khi không dựng được bitmap.
     */
    fun build(ctx: Context, path: String, source: Bitmap, fit: ImageFit, dimPercent: Int, screenW: Int, screenH: Int): WallArt? {
        val file = File(path)
        val dir = WallpaperStore.folder(ctx)?.let { File(it, CACHE_DIR) }
        return build(ArtCache(dir, file.name, file.lastModified(), file.length()) { dir?.parentFile?.list()?.toSet() },
            source, fit, dimPercent, screenW, screenH)
    }

    /**
     * Ô bộ đệm đĩa của một ảnh nguồn: thư mục đệm · tên ảnh · tem (mtime) · cỡ tệp · [sources] = tên các ảnh nguồn còn sống
     * (cho bộ dọn; `null` = không đọc được ⇒ không dọn). 3.1.5: ảnh nền đóng gói dùng ô RIÊNG ([DefaultWallpapers.artCache]).
     */
    class ArtCache(val dir: File?, val name: String, val stamp: Long, val size: Long, val sources: () -> Set<String>?)

    /** Như [build] theo đường dẫn, với ô đệm [cache] cho sẵn. */
    fun build(cache: ArtCache, source: Bitmap, fit: ImageFit, dimPercent: Int, screenW: Int, screenH: Int): WallArt? {
        if (screenW <= 0 || screenH <= 0 || source.isRecycled) return null
        val w = maxOf(1, screenW / SCALE); val h = maxOf(1, screenH / SCALE)
        val key = "${cache.name}-${cache.stamp}-${cache.size}-${screenW}x$screenH-${fit.name}-$dimPercent"
        val dir = cache.dir
        loadCached(dir, key, w, h, screenW, screenH)?.let { return it }

        val bmp = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor(KachiTheme.BG))
        val src = Rect(); val dst = Rect()
        WallFit.map(source.width, source.height, screenW.toFloat(), screenH.toFloat(), fit, src, dst)
        val t0 = SystemClock.elapsedRealtimeNanos()
        val sharp = sharpField(source, src)   // R29: ảnh SẮC, theo hàng, trên đúng vùng sẽ hiện
        val sharpDst = Rect(dst)
        val sharpScale = src.width().toDouble() / maxOf(1, dst.width())
        val t1 = SystemClock.elapsedRealtimeNanos()
        dst.set(dst.left / SCALE, dst.top / SCALE, dst.right / SCALE, dst.bottom / SCALE)
        canvas.drawBitmap(source, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        // Màu trội đo TRƯỚC khi làm tối (làm tối là lựa chọn trình bày, không phải màu của bức ảnh).
        val dominant = DominantColors.of(px, 3)
        // Cùng lớp làm tối với WallView — cùng vai màu, cùng cách trộn.
        val dim = dimPercent.coerceIn(0, 90)
        if (dim > 0) {
            val scrim = ColorMath.withAlpha(Color.parseColor(KachiTheme.WALL_SCRIM), dim * 255 / 100)
            for (i in px.indices) px[i] = ColorMath.over(scrim, px[i])
        }
        val lum = luminanceGrid(px, w, h)
        boxBlur(px, w, h, RADIUS, PASSES)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        val t2 = SystemClock.elapsedRealtimeNanos()
        val glass = LumaField.of(px, w, h, GLASS_COLS)   // R29: ảnh MỜ sau làm tối + làm mờ (đúng thứ thẻ kính vẽ)
        val t3 = SystemClock.elapsedRealtimeNanos()
        val art = WallArt(bmp, SCALE, COLS, ROWS, lum, dominant, screenW, screenH, sharp, sharpDst, sharpScale, glass, dim)
        val kb = saveCached(dir, key, art)
        // R29 chi phí (perf-every-fix): một dòng mỗi lần NẤU (không mỗi lần đọc đệm) — lưới sắc · lưới mờ · cỡ đệm.
        Log.i(TAG, "R29 đo nền: lưới sắc ${sharp.cols}×${sharp.rows} %.1f ms · lưới mờ ${glass.cols}×${glass.rows} %.1f ms · đệm %.1f KB"
            .format((t1 - t0) / 1e6, (t3 - t2) / 1e6, kb / 1024.0))
        prune(dir, keep = key, cache.sources)   // 2.93 WALLART-CACHE-BOUND — chỉ khi vừa ghi khoá MỚI (đệm chỉ lớn lên ở đây)
        return art
    }

    /**
     * R29 (T29-2) — lưới hộp màu của ảnh SẮC [source] trên vùng [src]: đọc MỘT hàng mỗi lần (`getPixels` 1 hàng ⇒
     * [LumaField.Builder.addRow]), không chép cả ảnh ra mảng (1920×1080 ⇒ 8 MB). Thread nền, cùng lượt nấu.
     */
    private fun sharpField(source: Bitmap, src: Rect): LumaField {
        val w = maxOf(1, src.width()); val h = maxOf(1, src.height())
        val b = LumaField.Builder(w, h)
        val row = IntArray(w)
        for (y in 0 until h) {
            source.getPixels(row, 0, w, src.left, src.top + y, w, 1)
            b.addRow(y, row)
        }
        return b.build()
    }

    /**
     * 2.93 `WALLART-CACHE-BOUND` — bộ đệm có trần ([WallArtCachePolicy], `:core`): đệm mồ côi (ảnh nguồn đã gỡ) · quá
     * [WallArtCachePolicy.KEEP_PER_SOURCE] khoá mỗi ảnh · quá [WallArtCachePolicy.MAX_KEYS] khoá ⇒ xoá cặp `.png` + `.txt`.
     * Chỉ đụng tệp TRONG [dir] (`.kachi-art/`, tên lấy từ chính danh sách thư mục ⇒ không ra ngoài); khoá [keep] vừa nấu
     * không bao giờ bị xoá. Thread nền (cùng lượt nấu). Không đọc được thư mục ⇒ thôi, lần nấu sau dọn.
     */
    private fun prune(dir: File?, keep: String, liveSources: () -> Set<String>?) {
        if (dir == null) return
        try {
            val files = dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".png") || it.name.endsWith(".txt")) } ?: return
            val sources = liveSources() ?: return
            val entries = files.groupBy { it.name.substringBeforeLast('.') }
                .map { (k, fs) -> WallArtCachePolicy.Entry(k, fs.maxOf { it.lastModified() }) }
            val gone = WallArtCachePolicy.victims(entries, sources, protect = keep)
            gone.forEach { k -> File(dir, "$k.png").delete(); File(dir, "$k.txt").delete() }
            if (gone.isNotEmpty()) Log.i(TAG, "dọn bộ đệm ảnh mờ: ${gone.size} khoá (còn ${entries.size - gone.size})")
        } catch (e: SecurityException) {
            Log.w(TAG, "không dọn được bộ đệm ảnh mờ: ${e.javaClass.simpleName}")
        }
    }

    /** Lưới độ chói COLS×ROWS: trung bình độ chói WCAG của các điểm trong ô. */
    fun luminanceGrid(px: IntArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(COLS * ROWS)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val x0 = c * w / COLS; val x1 = maxOf(x0 + 1, (c + 1) * w / COLS)
            val y0 = r * h / ROWS; val y1 = maxOf(y0 + 1, (r + 1) * h / ROWS)
            var sum = 0.0; var n = 0
            var y = y0
            while (y < y1) { var x = x0; while (x < x1) { sum += ColorMath.luminance(px[y * w + x]); n++; x += 2 }; y += 2 }
            out[r * COLS + c] = if (n == 0) 0f else (sum / n).toFloat()
        }
        return out
    }

    /** Box blur tách trục, [passes] lượt (3 lượt ≈ Gaussian). Thuần `IntArray`, không cấp phát trong vòng lặp. */
    fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, passes: Int) {
        if (radius <= 0 || w < 2 || h < 2) return
        val tmp = IntArray(px.size)
        repeat(passes) {
            blurAxis(px, tmp, w, h, radius, horizontal = true)
            blurAxis(tmp, px, w, h, radius, horizontal = false)
        }
    }

    private fun blurAxis(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val win = 2 * r + 1
        for (line in 0 until lines) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            fun idx(i: Int) = if (horizontal) line * w + i else i * w + line
            fun add(i: Int, sign: Int) {
                val p = src[idx(i.coerceIn(0, len - 1))]
                sa += sign * ((p ushr 24) and 0xff); sr += sign * ((p ushr 16) and 0xff)
                sg += sign * ((p ushr 8) and 0xff); sb += sign * (p and 0xff)
            }
            for (i in -r..r) add(i, 1)
            for (i in 0 until len) {
                dst[idx(i)] = ((sa / win) shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                add(i - r, -1); add(i + r + 1, 1)
            }
        }
    }

    private fun loadCached(dir: File?, key: String, w: Int, h: Int, screenW: Int, screenH: Int): WallArt? {
        if (dir == null) return null
        val png = File(dir, "$key.png"); val meta = File(dir, "$key.txt")
        if (!png.isFile || !meta.isFile) return null
        return runCatching {
            val bmp = BitmapFactory.decodeFile(png.absolutePath, BitmapFactory.Options().apply { inMutable = false })
                ?: return null
            if (bmp.width != w || bmp.height != h) { bmp.recycle(); return null }
            val lines = meta.readLines()
            val lum = lines.first { it.startsWith("lum:") }.removePrefix("lum:").split(',').map { it.toFloat() }.toFloatArray()
            val dom = lines.first { it.startsWith("dom:") }.removePrefix("dom:").split(',').filter { it.isNotBlank() }.map { it.toInt() }.toIntArray()
            // R29: đệm cũ (trước 3.1.5) không có ba dòng lưới ⇒ nấu lại, KHÔNG đoán hộp màu.
            val sharp = LumaField.decode(lines.firstOrNull { it.startsWith("sharp:") }?.removePrefix("sharp:"))
            val glass = LumaField.decode(lines.firstOrNull { it.startsWith("glass:") }?.removePrefix("glass:"))
            val fitNums = lines.firstOrNull { it.startsWith("sfit:") }?.removePrefix("sfit:")?.split(',').orEmpty()
            if (lum.size != COLS * ROWS || sharp == null || glass == null || fitNums.size != 6) { bmp.recycle(); return null }
            val d = Rect(fitNums[0].toInt(), fitNums[1].toInt(), fitNums[2].toInt(), fitNums[3].toInt())
            WallArt(bmp, SCALE, COLS, ROWS, lum, dom, screenW, screenH, sharp, d, fitNums[4].toDouble(), glass, fitNums[5].toInt())
        }.getOrElse { Log.w(TAG, "bộ đệm ảnh mờ hỏng, tính lại: ${it.javaClass.simpleName}"); null }
    }

    /** Ghi đệm; trả cỡ tệp `.txt` (byte, 0 nếu không ghi) — số đo R29 cho dòng log của lượt nấu. */
    private fun saveCached(dir: File?, key: String, art: WallArt): Long {
        if (dir == null) return 0
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            File(dir, "$key.png").outputStream().use { art.blurred.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val d = art.sharpDst
            val meta = File(dir, "$key.txt")
            meta.writeText(
                "lum:" + art.lum.joinToString(",") + "\n" + "dom:" + art.dominant.joinToString(",") + "\n" +
                    "sharp:" + art.sharp.encode() + "\n" + "glass:" + art.glass.encode() + "\n" +
                    "sfit:${d.left},${d.top},${d.right},${d.bottom},${art.sharpScale},${art.dimPercent}\n",
            )
            meta.length()
        }.onFailure { Log.w(TAG, "không lưu được bộ đệm ảnh mờ: ${it.javaClass.simpleName}") }.getOrDefault(0)
    }
}
