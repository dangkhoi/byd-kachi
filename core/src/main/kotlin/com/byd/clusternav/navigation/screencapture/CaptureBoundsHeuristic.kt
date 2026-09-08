package com.byd.clusternav.navigation.screencapture

import kotlin.math.max
import kotlin.math.min

/**
 * Chọn node a11y ứng viên cho bounds mũi tên (Waze) / icon camera (VietMap) — THUẦN, không Android, để
 * `NavAccessibilityService` (:app, biết Android) chỉ lo gom node → gọi đây → publish vào [CaptureBoundsSource].
 *
 * Đây là phần khoá-được-off-car của deliverable T3: LOGIC chọn node (deterministic) test được; còn việc a11y
 * đọc ĐÚNG node/độ ổn định trên xe là VERIFY-ON-CAR (OQ2).
 *
 * ── B3.5 (bug repro trên emulator Waze+VietMap @960x720) ──────────────────────────────────────────────────
 * Trước đây pick() chọn node DIỆN TÍCH NHỎ NHẤT trong nhóm khớp → trên xe nó trả về RÁC nhỏ xíu (18×19 =
 * icon biển báo, 62×62 = mảnh marker xe) THAY VÌ banner mũi tên Waze (~180×80) / icon camera VietMap. Sửa:
 *   - LỌC CỨNG ứng viên quá nhỏ: dưới [MIN_ICON_W]×[MIN_ICON_H] HOẶC diện tích < max([MIN_ICON_AREA], tỉ lệ
 *     [MIN_AREA_FRAC_PERMILLE]‰ của [region]) → loại (giết 18×19 và 62×62);
 *   - LỌC CỨNG panel toàn màn: cả hai cạnh > [MAX_ICON_PX] → loại;
 *   - CHẤM ĐIỂM thay cho "nhỏ nhất thắng": tier (khớp-từ-khoá ≫ node-ảnh) + độ-hợp-lý-kích-thước (đỉnh tại
 *     [IDEAL_AREA], phạt cả quá nhỏ lẫn panel) + vùng-màn kỳ vọng (nav cue ở NỬA TRÊN; mũi tên Waze lệch TRÁI)
 *     − phạt tỉ-lệ-cạnh dị thường (sliver/marquee). Node điểm cao nhất thắng.
 * Vẫn THUẦN + degrade-safe: không có ứng viên hợp lệ → null (router rớt về tầng cố-định).
 */
object CaptureBoundsHeuristic {

    /** Một node a11y đã rút gọn: class + mô tả + bounds tuyệt đối (đã đọc getBoundsInScreen). */
    data class Candidate(
        val className: String,
        val contentDescription: String,
        val rect: CropRect,
    )

    // ── Bộ lọc CỨNG (loại thẳng, không chấm điểm) ───────────────────────────────────────────────────────────
    /** Cạnh tối thiểu (sau clamp) — chống sliver/1px. Banner mũi tên ~180×80, icon camera ~160 → thừa sức. */
    private const val MIN_ICON_W = 32
    private const val MIN_ICON_H = 24

    /** Diện tích tối thiểu tuyệt đối (~71×71). Giết mảnh marker 62×62 (=3844) mà vẫn giữ icon ~80×80 (=6400). */
    private const val MIN_ICON_AREA = 5000L

    /** …HOẶC tối thiểu theo TỈ LỆ region (‰) — an toàn khi display rất lớn; lấy max với tuyệt đối. */
    private const val MIN_AREA_FRAC_PERMILLE = 2L   // 0.2% của vùng app

    /** Icon không nên quá to (panel/toàn màn). Loại khi CẢ HAI cạnh vượt trần (giữ nguyên hành vi cũ). */
    private const val MAX_ICON_PX = 360

    // ── Trọng số CHẤM ĐIỂM (tier luôn thắng size/zone/aspect để giữ ưu tiên khớp-từ-khoá) ──────────────────
    private const val KEYWORD_BASE = 1000   // khớp từ khoá target (mạnh nhất)
    private const val IMAGE_BASE = 200      // node ảnh (ImageView…) không từ khoá (yếu, fallback)
    private const val SIZE_WEIGHT = 300     // 0..300 theo độ hợp lý kích thước
    private const val UPPER_BONUS = 40      // nav cue nằm NỬA TRÊN màn (không phải thanh trạng thái đáy)
    private const val LEFT_BONUS = 20       // mũi tên Waze là banner góc TRÊN-TRÁI
    private const val ASPECT_PENALTY_CAP = 150
    private const val ASPECT_PENALTY_PER = 30

    /** Diện tích "lý tưởng" của icon/banner (~180×110 hoặc ~140×140) — điểm kích thước đạt đỉnh ở đây. */
    private const val IDEAL_AREA = 20000.0

    /** Tỉ lệ cạnh (dài/ngắn) ≤ 4 coi là bình thường; vượt → phạt lũy tiến (loại marquee/sliver). */
    private const val MAX_ASPECT_OK = 4.0

    /** Ngưỡng "nửa trên" + "lệch trái" của vùng (tỉ lệ 0..1). Heuristic — tune on-car. */
    private const val UPPER_FRAC = 0.55
    private const val LEFT_FRAC = 0.60

    private val ARROW_KEYS = listOf(
        "turn", "arrow", "exit", "roundabout", "keep", "merge", "ramp", "rẽ", "ngã", "vòng xuyến", "lối ra",
    )
    private val CAMERA_KEYS = listOf(
        "camera", "speed camera", "phạt", "phat", "tốc độ", "toc do", "cam", "radar", "enforcement",
    )
    private val IMAGE_CLASSES = listOf("ImageView", "ImageButton", "Image")

    /**
     * Chọn CropRect tốt nhất cho [target] từ [candidates], giới hạn trong [region]. null nếu không có ứng viên
     * hợp lệ. Deterministic (không đọc thời gian / trạng thái).
     */
    fun pick(target: CaptureTarget, candidates: List<Candidate>, region: CropRect): CropRect? {
        if (candidates.isEmpty() || region.isEmpty()) return null
        val keys = if (target == CaptureTarget.CAMERA) CAMERA_KEYS else ARROW_KEYS
        val regionArea = region.width.toLong() * region.height.toLong()
        val minArea = max(MIN_ICON_AREA, regionArea * MIN_AREA_FRAC_PERMILLE / 1000L)

        var best: CropRect? = null
        var bestScore = Int.MIN_VALUE
        for (c in candidates) {
            val r = c.rect.clampTo(region)
            if (r.isEmpty()) continue
            // Lọc cứng: quá nhỏ (cạnh hoặc diện tích) → RÁC (18×19 / 62×62).
            if (r.width < MIN_ICON_W || r.height < MIN_ICON_H) continue
            if (r.width.toLong() * r.height.toLong() < minArea) continue
            // Lọc cứng: panel toàn màn (cả hai cạnh vượt trần).
            if (r.width > MAX_ICON_PX && r.height > MAX_ICON_PX) continue
            val s = score(c, r, region, keys, target)
            if (s <= 0) continue                 // không từ khoá & không phải node ảnh → không đoán bừa
            if (s > bestScore) {
                bestScore = s
                best = r
            }
        }
        return best
    }

    /** Điểm tổng: tier (keyword≫image) + size + zone − aspect. ≤0 ⇒ loại (không gợi ý gì). */
    private fun score(c: Candidate, r: CropRect, region: CropRect, keys: List<String>, target: CaptureTarget): Int {
        var s = when {
            matchesKeyword(c, keys) -> KEYWORD_BASE
            isImageClass(c) -> IMAGE_BASE
            else -> return 0
        }
        s += sizeScore(r)
        s += zoneScore(r, region, target)
        s -= aspectPenalty(r)
        return s
    }

    /**
     * Độ hợp lý kích thước 0..[SIZE_WEIGHT]: tăng tuyến tính tới [IDEAL_AREA] rồi GIẢM (∝ 1/diện tích) khi lớn
     * hơn — nên icon/banner đúng cỡ luôn thắng cả RÁC nhỏ lẫn panel-nhỡ. Đây là chỗ đảo "nhỏ nhất thắng" (bug).
     */
    private fun sizeScore(r: CropRect): Int {
        val area = (r.width.toLong() * r.height.toLong()).toDouble()
        val frac = if (area <= IDEAL_AREA) area / IDEAL_AREA else IDEAL_AREA / area
        return (frac.coerceIn(0.0, 1.0) * SIZE_WEIGHT).toInt()
    }

    /** Ưu tiên vùng kỳ vọng: nav cue ở NỬA TRÊN (cả 2 target); mũi tên Waze còn lệch TRÁI. Nhẹ (không đảo tier). */
    private fun zoneScore(r: CropRect, region: CropRect, target: CaptureTarget): Int {
        val h = max(1, region.height)
        val w = max(1, region.width)
        val relY = ((r.top + r.bottom) / 2.0 - region.top) / h
        val relX = ((r.left + r.right) / 2.0 - region.left) / w
        var z = 0
        if (relY <= UPPER_FRAC) z += UPPER_BONUS
        if (target == CaptureTarget.ARROW && relX <= LEFT_FRAC) z += LEFT_BONUS
        return z
    }

    /** Phạt tỉ-lệ-cạnh dị thường (dài/ngắn > [MAX_ASPECT_OK]) — loại marquee tên đường / thanh mảnh. */
    private fun aspectPenalty(r: CropRect): Int {
        val lo = min(r.width, r.height).coerceAtLeast(1)
        val hi = max(r.width, r.height)
        val aspect = hi.toDouble() / lo
        if (aspect <= MAX_ASPECT_OK) return 0
        return min(ASPECT_PENALTY_CAP, ((aspect - MAX_ASPECT_OK) * ASPECT_PENALTY_PER).toInt())
    }

    private fun matchesKeyword(c: Candidate, keys: List<String>): Boolean {
        val hay = (c.contentDescription + " " + c.className).lowercase()
        return keys.any { hay.contains(it) }
    }

    private fun isImageClass(c: Candidate): Boolean =
        IMAGE_CLASSES.any { c.className.contains(it, ignoreCase = true) }
}
