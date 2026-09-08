package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.screencapture.CropRect
import com.byd.clusternav.navigation.screencapture.PixelFrameOps

/**
 * Đọc dải LANE-GUIDANCE (Waze/VietMap) → [LaneInfo] (TRÁI→PHẢI) — T1, spec `b3-full-nav-capture` §R4.
 * THUẦN (không Android) → unit-test off-car.
 *
 * Cách (BASIC — tinh chỉnh với crop làn THẬT trên emulator/xe, OQ2):
 *   1. Chia bề rộng dải thành [laneCount] cột đều (laneCount = số con của lane-view qua a11y nếu biết; null →
 *      dò bằng cụm-cột-sáng ngăn bởi khe tối).
 *   2. Mỗi cột: hướng qua [ManeuverSignature.classifyManeuver] (tái dùng); RECOMMENDED = độ sáng cột ≥
 *      [REC_FRACTION] × (cột sáng nhất) — làn nên-đi Waze/GMaps vẽ SÁNG, làn khác MỜ.
 *
 * null nếu không đọc được / dải quá tối (không có lane-guidance).
 */
object LaneSignature {

    /** Làn recommended = sáng ≥ 62% làn sáng nhất (làn mờ ~40-60% làn sáng). */
    private const val REC_FRACTION = 0.62f

    /** Dải sáng nhất < ngưỡng này ⇒ coi như KHÔNG có lane-guidance. */
    private const val MIN_MAX_BRIGHT = 40f

    fun classify(frame: PixelFrame?, laneCount: Int? = null): LaneInfo? {
        if (frame == null || frame.width < 8 || frame.height < 6) return null
        val px = frame.argb() ?: return null
        val w = frame.width
        val h = frame.height
        val n = laneCount ?: detectLaneCount(px, w, h)
        if (n <= 0 || n > 12 || w < n * 4) return null

        val bright = FloatArray(n)
        val dirs = arrayOfNulls<Maneuver>(n)
        for (i in 0 until n) {
            val x0 = i * w / n
            val x1 = (i + 1) * w / n
            bright[i] = meanBrightness(px, w, h, x0, x1)
            dirs[i] = PixelFrameOps.crop(frame, CropRect(x0, 0, x1, h))?.let { ManeuverSignature.classifyManeuver(it) }
        }
        val maxB = bright.max()
        if (maxB < MIN_MAX_BRIGHT) return null
        val thr = maxB * REC_FRACTION
        val lanes = (0 until n).map { i ->
            Lane(dirs[i]?.let { listOf(it) } ?: emptyList(), recommended = bright[i] >= thr)
        }
        return LaneInfo(lanes)
    }

    private fun meanBrightness(px: IntArray, w: Int, h: Int, x0: Int, x1: Int): Float {
        var sum = 0L
        var cnt = 0
        for (y in 0 until h) {
            for (x in x0 until x1) {
                val c = px[y * w + x]
                sum += (((c ushr 16) and 0xFF) + ((c ushr 8) and 0xFF) + (c and 0xFF)) / 3
                cnt++
            }
        }
        return if (cnt == 0) 0f else sum.toFloat() / cnt
    }

    /** Dò số làn = số cụm-cột-sáng ngăn bởi khe tối (fallback khi a11y không cho laneCount). 0 nếu không rõ. */
    private fun detectLaneCount(px: IntArray, w: Int, h: Int): Int {
        val colB = FloatArray(w)
        var maxB = 0f
        for (x in 0 until w) {
            var s = 0L
            for (y in 0 until h) {
                val c = px[y * w + x]
                s += (((c ushr 16) and 0xFF) + ((c ushr 8) and 0xFF) + (c and 0xFF)) / 3
            }
            colB[x] = s.toFloat() / h
            if (colB[x] > maxB) maxB = colB[x]
        }
        if (maxB < MIN_MAX_BRIGHT) return 0
        val gate = maxB * 0.35f
        var lanes = 0
        var inLane = false
        for (x in 0 until w) {
            val bright = colB[x] >= gate
            if (bright && !inLane) { lanes++; inLane = true } else if (!bright) inLane = false
        }
        return lanes
    }
}
