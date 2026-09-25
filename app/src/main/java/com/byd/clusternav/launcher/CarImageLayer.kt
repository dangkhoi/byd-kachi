package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * ═══ WP3-v5 · LỚP ẢNH XE dùng chung cho các bảng Canvas ══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP3-v5. Ba bảng ([TyreBoardView] · [DoorBoardView] · [CarMiniView])
 * đều cần: nạp ảnh xe (nền, khỏi chặn luồng vẽ) · cache theo cỡ + dấu-vết-tệp · vẽ ảnh feather (hoặc placeholder)
 * · nhả đúng lúc. Gom vào MỘT component để không chép ba lần (và ba lần lệch nhau).
 *
 * ## Luồng
 *  • [ensure] gọi từ `onDraw` (rẻ khi đã cache): cỡ/ảnh đổi ⇒ hỏi kho chung [CarImageStore.peek] trước — có sẵn thì
 *    gán NGAY (không lượt nền, không khung placeholder, không `invalidate` thêm); chưa có ⇒ xếp một lượt lên [io]
 *    (daemon) gọi [CarImageStore.shared], xong post về luồng chính, gọi [onReady] (= `invalidate`). Thẻ thế hệ [gen]
 *    bỏ lượt cũ về muộn (đổi cỡ liên tục / ô bị nhả) — cùng bất biến [WallpaperController].
 *  • [draw] blit ảnh feather **giữ tỉ lệ, canh giữa** vào khung; chưa có ảnh ⇒ [drawPlaceholder] (silhouette mờ,
 *    KHÔNG vector cũ, KHÔNG viền).
 *  • [release] gọi lúc ô bị tháo: huỷ lượt đang bay + bỏ tham chiếu.
 *
 * ## Quyền sở hữu bitmap (closeout 2026-09-25): thuộc KHO [CarImageStore], KHÔNG thuộc lớp này
 * Trước, mỗi lớp giải mã một bản riêng và `recycle()` khi thay/tháo. Nay ba lớp (lốp · cửa · mini) có thể cùng trỏ
 * một bitmap ⇒ lớp này **không bao giờ** `recycle()` (recycle ở đây = lớp khác vẽ trúng ảnh đã huỷ ⇒ crash trên
 * luồng vẽ). Bản không còn ai dùng do GC nhả. `isRecycled` vẫn được kiểm phòng hờ ở [draw].
 *
 * ## Ràng buộc (WP1/WP3-v5): 0 blur runtime · 0 shadow · 0 viền. Feather đã tính sẵn ([CarImageStore.feather]).
 */
internal class CarImageLayer(
    private val ctx: Context,
    private val onReady: () -> Unit,
) {
    private var bmp: Bitmap? = null
    private var loadedKey: CarImageStore.CacheKey? = null
    private var requestedKey: CarImageStore.CacheKey? = null
    private var gen = 0

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val src = Rect()
    private val dstFit = RectF()
    private val holder = RectF()

    /** Đảm bảo có ảnh cho khung [w]×[h]; nạp nền nếu cỡ/ảnh đổi. Trả `true` nếu đã có ảnh dùng được ngay. */
    fun ensure(w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0) return hasImage()
        // Khoá = dấu-vết-tệp + khung gom bậc 32px (đổi cỡ nhỏ không nạp lại; đổi ảnh thì nạp lại).
        val key = CarImageStore.cacheKey(CarImageStore.signature(ctx), w, h)
        // Khoá đã nạp (kể cả nạp HỎNG ⇒ placeholder) ⇒ không hỏi lại tới khi khoá đổi. Trước: nạp hỏng ⇒ lượt sau lại
        // xếp nạp ⇒ onReady ⇒ invalidate ⇒ onDraw ⇒ nạp… vòng vô hạn ở tốc độ giải mã (chỉ không lộ vì asset mặc định
        // luôn có).
        if (key == loadedKey) return hasImage()
        // Kho chung đã có bản này (lớp khác nạp rồi / ô gắn lại) ⇒ dùng ngay, không lượt nền, không khung trống.
        CarImageStore.peek(key)?.let { hit ->
            gen++                       // huỷ lượt đang bay (nếu có) — về muộn cũng bị bỏ
            bmp = hit
            loadedKey = key
            requestedKey = null
            return true
        }
        if (key == requestedKey) return hasImage()   // đang nạp đúng khung này rồi
        requestedKey = key
        val my = ++gen
        io.execute {
            val next = CarImageStore.shared(ctx, key)   // kho sở hữu; KHÔNG recycle ở lớp này
            main.post {
                if (my != gen) return@post
                bmp = next
                loadedKey = key
                requestedKey = null
                if (next != null) onReady()   // hỏng ⇒ placeholder đã vẽ sẵn, không invalidate thêm
            }
        }
        return hasImage()
    }

    private fun hasImage(): Boolean = bmp?.isRecycled == false

    /**
     * Vẽ ảnh xe **giữ tỉ lệ, canh giữa** vào [dst]. Chưa có ảnh ⇒ placeholder. Overlay trạng thái do chỗ gọi vẽ
     * SAU (chấm màu tại neo [CarLayout]).
     */
    fun draw(canvas: Canvas, dst: RectF) {
        val b = bmp
        if (b == null || b.isRecycled) { drawPlaceholder(canvas, dst); return }
        val bw = b.width.toFloat(); val bh = b.height.toFloat()
        if (bw <= 0f || bh <= 0f) { drawPlaceholder(canvas, dst); return }
        fitRect(dst, bw, bh, dstFit)
        src.set(0, 0, b.width, b.height)
        canvas.drawBitmap(b, src, dstFit, paint)
    }

    /**
     * Khung mà hình xe THỰC SỰ chiếm trong [dst] (ảnh feather đã canh giữa-giữ-tỉ-lệ, hoặc placeholder). Chỗ gọi
     * đặt chấm trạng thái theo neo chuẩn hoá [CarLayout] vào khung này: `px = out.left + ax*out.width()`.
     */
    fun contentRect(dst: RectF, out: RectF) {
        val b = bmp
        if (b != null && !b.isRecycled && b.width > 0 && b.height > 0) {
            fitRect(dst, b.width.toFloat(), b.height.toFloat(), out)
        } else {
            fitRect(dst, PLACEHOLDER_ASPECT, 1f, out)
        }
    }

    /** Khung con của [dst] giữ tỉ lệ [srcW]:[srcH], canh giữa (letterbox). */
    private fun fitRect(dst: RectF, srcW: Float, srcH: Float, out: RectF) {
        if (srcW <= 0f || srcH <= 0f) { out.set(dst); return }
        val scale = minOf(dst.width() / srcW, dst.height() / srcH)
        val dw = srcW * scale; val dh = srcH * scale
        val cx = dst.centerX(); val cy = dst.centerY()
        out.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f)
    }

    /**
     * Placeholder khi chưa có ảnh: một thân xe bo góc **mờ nhạt** (màu [KachiTheme.DIM]) — KHÔNG dùng lại vector
     * cũ, KHÔNG viền (WP1 R1.1). Tỉ lệ ~ xe nhìn từ trên (rộng:cao ≈ 0.46) để overlay theo neo vẫn rơi đúng vùng.
     */
    private fun drawPlaceholder(canvas: Canvas, dst: RectF) {
        fitRect(dst, PLACEHOLDER_ASPECT, 1f, holder)
        val r = holder.width() * 0.30f
        placeholder.color = Color.parseColor(KachiTheme.DIM)
        canvas.drawRoundRect(holder, r, r, placeholder)
    }

    /** Ô bị tháo ⇒ huỷ lượt đang bay + bỏ tham chiếu (bitmap thuộc kho chung — KHÔNG recycle, xem KDoc lớp). */
    fun release() {
        gen++
        requestedKey = null
        bmp = null
        loadedKey = null
    }

    private companion object {
        /** Một luồng daemon dùng chung cho MỌI lớp ảnh xe — giải mã ảnh nhỏ, không cần nhiều luồng. */
        val io = Executors.newSingleThreadExecutor { r -> Thread(r, "car-image").apply { isDaemon = true } }
        val main = Handler(Looper.getMainLooper())

        /** Tỉ lệ rộng:cao của thân xe top-down — dùng cho placeholder + khung neo overlay khi chưa có ảnh. */
        const val PLACEHOLDER_ASPECT = 0.46f
    }
}
