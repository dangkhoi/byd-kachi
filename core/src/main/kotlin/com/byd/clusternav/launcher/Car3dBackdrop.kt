package com.byd.clusternav.launcher

import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/**
 * ═══ 3.01 R25 — ẢNH NỀN sau xe 3D (owner 13/10 duyệt) — dữ liệu + phép căn thuần `:core` ═══════════════════════════════
 *
 * Ảnh lấy từ thư mục hình nền SẴN CÓ của Kachi (`WallpaperStore`, `Android/data/<gói>/files/wallpapers`) — app KHÔNG chở ảnh
 * nào (bài canh `Car3dBackdropContractTest`). Lưu THEO HỒ SƠ trong khoá `color_choice` (trường mang nhãn `bg=`, cùng đường
 * xuất/nhập với [ColorChoice.carPaint]). [file] chỉ là TÊN tệp (không thư mục, không `..`, không bắt đầu bằng `.`) — bên đọc
 * ghép với thư mục ảnh, không bao giờ đi ra ngoài nó.
 *
 * Căn ảnh: [zoom] ≥ 1 trên nền "phủ kín khung" (center-crop) · [dx]/[dy] dịch tâm ảnh theo phân số nửa khung (−1..1).
 */
data class Car3dBackdrop(val file: String, val zoom: Double = 1.0, val dx: Double = 0.0, val dy: Double = 0.0) {

    fun encode(): String = "$TAG$file@" + listOf(zoom, dx, dy).joinToString(",") { String.format(Locale.ROOT, "%.3f", it) }

    /** Kéo [dxPx],[dyPx] trên khung [w]×[h]. */
    fun pan(dxPx: Float, dyPx: Float, w: Int, h: Int): Car3dBackdrop =
        copy(dx = (dx + 2.0 * dxPx / w.coerceAtLeast(1)).coerceIn(-1.0, 1.0), dy = (dy + 2.0 * dyPx / h.coerceAtLeast(1)).coerceIn(-1.0, 1.0))

    fun zoomBy(k: Float): Car3dBackdrop = copy(zoom = (zoom * k).coerceIn(MIN_ZOOM, MAX_ZOOM))

    /**
     * Phép đặt ảnh [bw]×[bh] vào khung [vw]×[vh]: (tỉ lệ, dịch x, dịch y) — điểm ảnh (u,v) vẽ tại (u·s + tx, v·s + ty).
     * Tỉ lệ nền = phủ kín khung; tâm ảnh ở tâm khung + ([dx],[dy]) × nửa khung.
     */
    fun place(bw: Int, bh: Int, vw: Int, vh: Int): Triple<Float, Float, Float> {
        val s = maxOf(vw.toDouble() / bw.coerceAtLeast(1), vh.toDouble() / bh.coerceAtLeast(1)) * zoom
        val cx = vw / 2.0 * (1 + dx); val cy = vh / 2.0 * (1 + dy)
        return Triple(s.toFloat(), (cx - bw * s / 2).toFloat(), (cy - bh * s / 2).toFloat())
    }

    companion object {
        const val TAG = "bg="
        const val MIN_ZOOM = 1.0
        const val MAX_ZOOM = 4.0
        private val FILE = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,63}""")
        private val FIELD = Regex("""bg=([^@;]{1,64})@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")

        /** Tên tệp hợp lệ (chỉ tên, không đường dẫn) — mọi chỗ nhận tên từ ngoài đi qua đây. */
        fun validName(name: String?): Boolean = name != null && FILE.matches(name) && !name.contains("..")

        fun decode(field: String): Car3dBackdrop? {
            val m = FIELD.matchEntire(field.trim()) ?: return null
            val (f, z, x, y) = m.destructured
            if (!validName(f)) return null
            return Car3dBackdrop(f, z.toDouble().coerceIn(MIN_ZOOM, MAX_ZOOM), x.toDouble().coerceIn(-1.0, 1.0), y.toDouble().coerceIn(-1.0, 1.0))
        }
    }
}

/**
 * Hình chiếu LÚC VẼ của `colorChoice.carBackdrop` (cùng lẽ [Car3dPaintChoice]: chỉ `ThemeHost.sync` ghi [current]); [save] =
 * đường ghi MỘT chiều về `HomeViewModel.setColorChoice` (HOME gán) — widget gọi khi xong chế độ căn ảnh. [aligning] = đang ở
 * chế độ căn (Cài đặt bật, widget tắt khi bấm xong).
 */
object Car3dBackdropChoice {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile var current: Car3dBackdrop? = null
        set(v) { if (field != v) { field = v; listeners.forEach { it() } } }

    @Volatile var aligning: Boolean = false
        set(v) { if (field != v) { field = v; listeners.forEach { it() } } }

    @Volatile var save: ((Car3dBackdrop?) -> Unit)? = null

    fun listen(l: () -> Unit) { listeners += l }
    fun unlisten(l: () -> Unit) { listeners -= l }
}
