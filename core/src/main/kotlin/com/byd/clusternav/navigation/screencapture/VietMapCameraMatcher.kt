package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.PixelFrame
import kotlin.math.sqrt

/**
 * Một template icon camera VietMap: tên + chữ ký lưới [VietMapCameraMatcher.GRID]² (luminance-nhân-alpha,
 * 0..255 mỗi ô). Dựng từ ảnh thật qua [VietMapCameraMatcher.templateFrom] (OQ4 thu trên xe) hoặc từ fixture.
 */
class CameraTemplate(val name: String, val fill: FloatArray) {
    override fun equals(other: Any?): Boolean =
        other is CameraTemplate && other.name == name && other.fill.contentEquals(fill)

    override fun hashCode(): Int = 31 * name.hashCode() + fill.contentHashCode()
}

/**
 * Kết quả khớp icon camera. [speedKmh]/[distanceMeters] để null vòng này (OCR/known-template là OQ4 on-car).
 */
data class CameraMatch(
    val hasCamera: Boolean,
    val score: Float,
    val templateName: String?,
    val speedKmh: Int? = null,
    val distanceMeters: Int? = null,
) {
    companion object {
        /** Không khớp (không camera). */
        val NONE = CameraMatch(hasCamera = false, score = 0f, templateName = null)
    }
}

/**
 * Nhận diện icon camera phạt nguội của VietMap bằng TEMPLATE-MATCH — THUẦN, không Android (R2/R-nf5, §4.5).
 *
 * TÁI DÙNG CÁCH của [com.byd.clusternav.navigation.ManeuverSignature]: hạ mẫu vùng crop về lưới [GRID]²,
 * lấy đặc trưng mỗi ô rồi khớp mềm bằng NCC (normalized cross-correlation) như `matchNCC` của signature.
 * (Không gọi được `signature()` của ManeuverSignature vì nó private + trả bit hướng rẽ; ở đây dùng CÙNG kỹ
 * thuật cho đặc trưng camera.) NCC bất biến với thang/độ lệch sáng → chịu được camera sáng-tối khác nhau.
 *
 * Đặc trưng mỗi ô = trung bình (luminance × alpha/255) → nền trong suốt/đen tuyền = 0 ⇒ khung trống có
 * phương sai 0 ⇒ trả [CameraMatch.NONE] (KHÔNG false-positive, đúng R2).
 *
 * Registry template dựng sẵn [BUILTIN] để RỖNG (template thật = OQ4 thu trên xe); lớp :app / test nạp
 * template qua constructor. Ngưỡng [minScore] chặn false-positive.
 */
class VietMapCameraMatcher(
    private val templates: List<CameraTemplate>,
    private val minScore: Float = DEFAULT_MIN_SCORE,
) {

    /**
     * Khớp [frame] (đã crop về vùng camera) với các template. Trả template điểm NCC cao nhất ≥ [minScore];
     * nếu không có (hoặc frame null/quá nhỏ/trống/không template) → [CameraMatch.NONE].
     */
    fun match(frame: PixelFrame?): CameraMatch {
        if (templates.isEmpty()) return CameraMatch.NONE
        val q = signatureOf(frame) ?: return CameraMatch.NONE
        val qStat = stats(q) ?: return CameraMatch.NONE       // phương sai 0 (trống) → không khớp
        var best: CameraTemplate? = null
        var bestScore = minScore
        for (t in templates) {
            if (t.fill.size != q.size) continue
            val score = ncc(q, qStat, t.fill) ?: continue
            if (score >= bestScore) { bestScore = score; best = t }
        }
        return if (best == null) CameraMatch.NONE
        else CameraMatch(hasCamera = true, score = bestScore, templateName = best.name)
    }

    private class Stat(val mean: Float, val varSum: Float)

    private fun stats(v: FloatArray): Stat? {
        var m = 0f
        for (x in v) m += x
        m /= v.size
        var vs = 0f
        for (x in v) { val d = x - m; vs += d * d }
        if (vs < 1e-6f) return null
        return Stat(m, vs)
    }

    private fun ncc(q: FloatArray, qStat: Stat, t: FloatArray): Float? {
        val tStat = stats(t) ?: return null
        var cov = 0f
        for (i in q.indices) cov += (q[i] - qStat.mean) * (t[i] - tStat.mean)
        return cov / sqrt(qStat.varSum * tStat.varSum)
    }

    companion object {
        /** Lưới hạ mẫu — cùng kích thước signature của ManeuverSignature để nhất quán. */
        const val GRID = 15

        /** Ngưỡng NCC tối thiểu (thực nghiệm; đủ cao để chặn nền map không-camera). Tune on-car (OQ4). */
        const val DEFAULT_MIN_SCORE = 0.62f

        /** Kích thước tối thiểu khung crop để chấm (như ManeuverSignature). */
        private const val MIN_SIDE = 8

        /** Registry dựng sẵn — RỖNG (template thật thu trên xe, OQ4). Lớp :app nạp thêm khi có. */
        val BUILTIN: List<CameraTemplate> = emptyList()

        // ── B3.6: registry NẠP-ĐƯỢC lúc chạy (song song [com.byd.clusternav.navigation.WazeArrowRegistry]) —
        //    template camera THẬT thu trên xe (OQ4) nạp vào đây sau; production RỖNG ⇒ [fromRegistry] khớp NONE
        //    (an toàn, không false-positive). Thread-safe: ghi `@Synchronized`; đọc [loadedTemplates] trả snapshot.
        @Volatile private var loaded: List<CameraTemplate> = emptyList()

        /** Snapshot bất biến các template hiện có = [BUILTIN] + đã nạp. */
        fun loadedTemplates(): List<CameraTemplate> = if (loaded.isEmpty()) BUILTIN else BUILTIN + loaded

        /** Thêm MỘT template (bỏ qua nếu trùng y hệt). Dùng cho orchestrator/ test nạp template thu được. */
        @Synchronized
        fun register(template: CameraTemplate) {
            if (template in loaded) return
            loaded = loaded + template
        }

        /** Nạp nguyên bộ (thay thế tập đã-nạp; [BUILTIN] luôn giữ). */
        @Synchronized
        fun load(templates: List<CameraTemplate>) {
            loaded = templates.toList()
        }

        /** Xoá tập đã-nạp về rỗng (chủ yếu cho test, tránh rò template tổng hợp sang test khác). */
        @Synchronized
        fun clearLoaded() {
            loaded = emptyList()
        }

        /**
         * Dựng matcher trên registry NẠP-ĐƯỢC ([loadedTemplates] = BUILTIN + đã nạp) — cách orchestrator/ :app
         * lấy matcher tôn trọng template thật nạp sau (OQ4). Production (chưa nạp) ⇒ rỗng ⇒ match luôn NONE.
         */
        fun fromRegistry(minScore: Float = DEFAULT_MIN_SCORE): VietMapCameraMatcher =
            VietMapCameraMatcher(loadedTemplates(), minScore)

        /**
         * Chữ ký lưới [GRID]² của [frame]: mỗi ô = trung bình (luminance × alpha/255). null nếu frame
         * null / nhỏ hơn [MIN_SIDE] / argb() null.
         */
        fun signatureOf(frame: PixelFrame?): FloatArray? {
            if (frame == null || frame.width < MIN_SIDE || frame.height < MIN_SIDE) return null
            val px = frame.argb() ?: return null
            val w = frame.width
            val h = frame.height
            val out = FloatArray(GRID * GRID)
            var cell = 0
            for (gy in 0 until GRID) {
                val y0 = gy * h / GRID
                var y1 = (gy + 1) * h / GRID; if (y1 > h) y1 = h
                for (gx in 0 until GRID) {
                    val x0 = gx * w / GRID
                    var x1 = (gx + 1) * w / GRID; if (x1 > w) x1 = w
                    val area = (y1 - y0) * (x1 - x0)
                    var sum = 0f
                    var yy = y0
                    while (yy < y1) {
                        val base = yy * w
                        var xx = x0
                        while (xx < x1) {
                            val c = px[base + xx]
                            val a = (c ushr 24) and 0xFF
                            val lum = (((c ushr 16) and 0xFF) + ((c ushr 8) and 0xFF) + (c and 0xFF)) / 3
                            sum += lum * a / 255f
                            xx++
                        }
                        yy++
                    }
                    if (area > 0) out[cell] = sum / area
                    cell++
                }
            }
            return out
        }

        /** Dựng một [CameraTemplate] từ ảnh tham chiếu (fixture / ảnh thu on-car). null nếu ảnh không hợp lệ. */
        fun templateFrom(name: String, frame: PixelFrame): CameraTemplate? =
            signatureOf(frame)?.let { CameraTemplate(name, it) }
    }
}
