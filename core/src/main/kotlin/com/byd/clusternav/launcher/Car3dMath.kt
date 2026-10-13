package com.byd.clusternav.launcher

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * ═══ 3.01 R25 — TOÁN của widget xe 3D (thuần `:core`, test off-device) ═══════════════════════════════════════════
 *
 * Owner 13/10: *"làm 1 cái widget to, cho xe 3D chiếu nghiêng, hiện hết cả cửa và đèn, cho xoay tới lui luôn"*.
 * Bộ vẽ (`:app`, Filament) và lớp chấm chạm phải nhìn CÙNG một camera — nên camera, phép chiếu điểm lên màn, kẹp góc
 * xoay và quán tính vuốt đều nằm ở đây; `:app` chỉ chép số ra `Camera.lookAt`/`setProjection` của Filament.
 *
 * Quy ước khung THẾ GIỚI (sau [Car3dOrient]): trục đứng +Y, đầu xe +Z, sườn TRÁI xe +X (tay phải: X = Y × Z). Camera
 * quay QUANH xe (xe đứng yên): yaw 0° = nhìn thẳng mũi xe, yaw dương = vòng sang bên TRÁI xe.
 */
data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun normalized(): V3 = length().let { if (it < 1e-12) this else this * (1.0 / it) }

    companion object { val ZERO = V3(0.0, 0.0, 0.0); val UP = V3(0.0, 1.0, 0.0) }
}

/** Điểm chiếu lên màn (px, gốc trên-trái) + độ sâu dọc trục nhìn (dùng xếp chấm gần/xa). */
data class ScreenPoint(val x: Float, val y: Float, val depth: Double)

/**
 * Camera quỹ đạo. [fovDeg] là góc nhìn DỌC (khớp `Camera.Fov.VERTICAL` của Filament).
 */
data class Car3dCamera(
    val target: V3, val distance: Double, val yawDeg: Double, val pitchDeg: Double, val fovDeg: Double,
    val near: Double = 0.05, val far: Double = 100.0,
) {
    val eye: V3
        get() {
            val yaw = Math.toRadians(yawDeg); val pitch = Math.toRadians(pitchDeg)
            return target + V3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch)) * distance
        }

    /**
     * Chiếu điểm thế giới [p] lên khung [w]×[h] px. `null` = sau lưng camera (không vẽ chấm). Cùng phép phối cảnh
     * `lookAt(eye, target, +Y)` + `perspective(fovDeg dọc, w/h)` mà `:app` đặt cho Filament.
     */
    fun project(p: V3, w: Int, h: Int): ScreenPoint? {
        if (w <= 0 || h <= 0) return null
        val e = eye
        val f = (target - e).normalized()
        val s = f.cross(V3.UP).normalized()
        val u = s.cross(f)
        val d = p - e
        val z = d.dot(f)
        if (z <= near) return null
        val t = tan(Math.toRadians(fovDeg) / 2.0)
        val aspect = w.toDouble() / h
        val nx = d.dot(s) / (z * t * aspect)
        val ny = d.dot(u) / (z * t)
        return ScreenPoint(((nx + 1.0) / 2.0 * w).toFloat(), ((1.0 - ny) / 2.0 * h).toFloat(), z)
    }

    /**
     * Mặt có quay về camera không: [outward] = hướng ra ngoài của bộ phận (ngang, từ tâm xe ra). Chấm phía khuất
     * thân xe bị ẩn — không cho chạm "xuyên xe". Hướng gần 0 (bộ phận ở tâm, vd nóc) ⇒ luôn thấy.
     */
    fun facing(anchor: V3, outward: V3): Boolean =
        outward.length() < 1e-6 || outward.dot(eye - anchor) > 0.0
}

/** Khung hộp bao (thế giới) của xe: tâm + nửa cạnh. */
data class Car3dBounds(val center: V3, val half: V3) {
    val radius: Double get() = half.length()
}

object Car3dMath {

    /**
     * Khoảng cách camera để quả cầu bao xe vừa khung [w]×[h] ở góc dọc [fovDeg]: theo góc NHỎ hơn trong hai chiều (ô
     * hẹp-cao thì góc ngang hẹp hơn). [zoom] < 1 ⇒ xe to hơn (cắt nhẹ mép hộp bao — hộp bao của xe thừa nhiều ở góc).
     */
    fun frameDistance(b: Car3dBounds, fovDeg: Double, w: Int, h: Int, zoom: Double): Double {
        val v = Math.toRadians(fovDeg)
        val aspect = if (w > 0 && h > 0) w.toDouble() / h else 1.0
        val hor = 2.0 * atan(tan(v / 2.0) * aspect)
        val fov = minOf(v, hor)
        return b.radius / sin(fov / 2.0) * zoom
    }

    /**
     * POLISH2 — khoảng cách TỐI THIỂU để cả 8 góc hộp xe [b] nằm trong khung [w]×[h] (chừa [margin] mỗi mép) ở góc nhìn
     * ([yawDeg], [pitchDeg]). [ĐO máy ảo 13/10] khoảng cách theo cầu × zoom (vừa khít ở góc ¾ ngang) cắt mất đuôi/đầu xe khi ngẩng
     * cao (nhìn từ trên: chiều dài xe chiếu dọc khung) — bên vẽ lấy MAX của hai khoảng cách.
     */
    fun fitDistance(b: Car3dBounds, target: V3, yawDeg: Double, pitchDeg: Double, fovDeg: Double, w: Int, h: Int, margin: Double = FIT_MARGIN): Double {
        if (w <= 0 || h <= 0) return 0.0
        val c = Car3dCamera(target, 1.0, yawDeg, pitchDeg, fovDeg)
        val f = (target - c.eye).normalized(); val s = f.cross(V3.UP).normalized(); val u = s.cross(f)
        val tv = tan(Math.toRadians(fovDeg) / 2.0) * (1.0 - 2 * margin); val th = tv * w.toDouble() / h
        var d = 0.0
        for (i in 0 until 8) {
            val p = V3(b.center.x + b.half.x * (if (i and 1 == 0) -1 else 1), b.center.y + b.half.y * (if (i and 2 == 0) -1 else 1),
                b.center.z + b.half.z * (if (i and 4 == 0) -1 else 1)) - target
            val zc = p.dot(f)
            d = maxOf(d, abs(p.dot(s)) / th - zc, abs(p.dot(u)) / tv - zc)
        }
        return d
    }

    const val FIT_MARGIN = 0.03

    /** Hướng ra ngoài (ngang) của điểm [a] so với tâm xe — cho [Car3dCamera.facing]. */
    fun outward(a: V3, center: V3): V3 = V3(a.x - center.x, 0.0, a.z - center.z)

    /**
     * Pháp tuyến NGANG theo elip của hộp bao [b] tại [a] (`d/h²` trên X, Z). Hướng ngang thuần sai với xe dài: cửa sau-trái
     * (x 0,78 · z −0,71) bị coi là "quay ra sau" và bị ẩn ở góc ¾ trước-trái [ĐO máy ảo 13/10]; chia theo nửa cạnh đưa nó về
     * đúng sườn. Điểm trên NÓC (cao hơn [ROOF_OF_HALF] nửa chiều cao) ⇒ hướng 0 = luôn thấy (camera luôn nhìn xuống, pitch > 0).
     */
    fun outward(a: V3, b: Car3dBounds): V3 {
        if (a.y - b.center.y > ROOF_OF_HALF * b.half.y) return V3.ZERO
        fun c(d: Double, h: Double) = if (h < 1e-9) 0.0 else d / (h * h)
        return V3(c(a.x - b.center.x, b.half.x), 0.0, c(a.z - b.center.z, b.half.z))
    }

    const val ROOF_OF_HALF = 0.8

    /**
     * Bóng đất (chuyển từ `Car3dWidgetView` — trần 500 dòng, R28): hình chiếu bầu dục đáy xe (nới [SHADOW_GROW]) lên khung [w]×[h] ⇒
     * (trái, trên, phải, dưới) px; một điểm sau camera ⇒ `null`.
     */
    fun groundRect(c: Car3dCamera, b: Car3dBounds, w: Int, h: Int): List<Float>? {
        val y = b.center.y - b.half.y
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bb = -Float.MAX_VALUE
        for (i in 0 until GROUND_POINTS) {
            val a = 2 * Math.PI * i / GROUND_POINTS
            val p = c.project(V3(b.center.x + Math.cos(a) * b.half.x * SHADOW_GROW, y, b.center.z + Math.sin(a) * b.half.z * SHADOW_GROW), w, h) ?: return null
            l = minOf(l, p.x); r = maxOf(r, p.x); t = minOf(t, p.y); bb = maxOf(bb, p.y)
        }
        return listOf(l, t, r, bb)
    }

    private const val GROUND_POINTS = 16
    private const val SHADOW_GROW = 1.06
}

/**
 * Góc xoay + quán tính vuốt. Có GIỚI HẠN (spec R25 "có giới hạn"): yaw [min]..[max] độ; chạm biên thì dừng quán tính.
 * Owner 13/10 *"không xoay lên xuống được à?"* ⇒ thêm góc NGẨNG camera [pitch] ([pitchMin]..[pitchMax], mặc định
 * [PITCH_MIN]..[PITCH_MAX]): vuốt dọc nghiêng camera, vuốt chéo áp cả hai, quán tính riêng mỗi trục.
 * Thuần — `:app` gọi [drag] trong `onTouchEvent`, [fling] một lần khi nhấc tay, [step] mỗi khung đến khi [moving] sai.
 */
data class Car3dOrbit(
    val yaw: Double, val velocity: Double, val min: Double, val max: Double,
    val pitch: Double = 16.0, val pitchVelocity: Double = 0.0, val pitchMin: Double = PITCH_MIN, val pitchMax: Double = PITCH_MAX,
) {

    val moving: Boolean get() = abs(velocity) > STOP_DEG_PER_S || abs(pitchVelocity) > STOP_DEG_PER_S

    /** Kéo [dxPx] px trên khung rộng [widthPx]: cả bề ngang = [DEG_PER_WIDTH] độ. Kéo sang PHẢI ⇒ xe quay theo tay. */
    fun drag(dxPx: Float, widthPx: Int): Car3dOrbit =
        copy(yaw = (yaw - dxPx.toDouble() / widthPx.coerceAtLeast(1) * DEG_PER_WIDTH).coerceIn(min, max), velocity = 0.0)

    /** Kéo 2 trục: thêm [dyPx] trên khung cao [heightPx] — kéo XUỐNG ⇒ camera lên cao (thấy nóc), cả chiều cao = [DEG_PER_HEIGHT]. */
    fun drag(dxPx: Float, dyPx: Float, widthPx: Int, heightPx: Int): Car3dOrbit =
        drag(dxPx, widthPx).copy(
            pitch = (pitch + dyPx.toDouble() / heightPx.coerceAtLeast(1) * DEG_PER_HEIGHT).coerceIn(pitchMin, pitchMax), pitchVelocity = 0.0,
        )

    /** Nhấc tay với vận tốc [pxPerS] ⇒ vận tốc góc (kẹp [MAX_DEG_PER_S]). */
    fun fling(pxPerS: Float, widthPx: Int): Car3dOrbit {
        val v = -pxPerS.toDouble() / widthPx.coerceAtLeast(1) * DEG_PER_WIDTH
        return copy(velocity = v.coerceIn(-MAX_DEG_PER_S, MAX_DEG_PER_S))
    }

    /** Nhấc tay 2 trục. */
    fun fling(vxPxPerS: Float, vyPxPerS: Float, widthPx: Int, heightPx: Int): Car3dOrbit {
        val vp = vyPxPerS.toDouble() / heightPx.coerceAtLeast(1) * DEG_PER_HEIGHT
        return fling(vxPxPerS, widthPx).copy(pitchVelocity = vp.coerceIn(-MAX_DEG_PER_S, MAX_DEG_PER_S))
    }

    /** Tiến [dtS] giây: quán tính tắt dần theo hàm mũ; chạm biên ⇒ trục đó dừng hẳn. */
    fun step(dtS: Double): Car3dOrbit {
        if (!moving) return copy(velocity = 0.0, pitchVelocity = 0.0)
        val decay = exp(-FRICTION_PER_S * dtS)
        fun axis(pos: Double, vel: Double, lo: Double, hi: Double): Pair<Double, Double> {
            if (abs(vel) <= STOP_DEG_PER_S) return pos to 0.0
            val p = pos + vel * dtS
            if (p <= lo || p >= hi) return p.coerceIn(lo, hi) to 0.0
            val v = vel * decay
            return p to (if (abs(v) <= STOP_DEG_PER_S) 0.0 else v)
        }
        val (y, vy) = axis(yaw, velocity, min, max)
        val (p, vp) = axis(pitch, pitchVelocity, pitchMin, pitchMax)
        return copy(yaw = y, velocity = vy, pitch = p, pitchVelocity = vp)
    }

    companion object {
        /** Vuốt hết bề ngang khung = 200° (cả xe trong khoảng một vuốt rưỡi). */
        const val DEG_PER_WIDTH = 200.0
        const val MAX_DEG_PER_S = 540.0
        /** Hệ số tắt dần (1/s): sau ~0,5 s còn ~8 %. */
        const val FRICTION_PER_S = 5.0
        const val STOP_DEG_PER_S = 4.0
        /** Vuốt hết chiều cao khung = 90° ngẩng. Dải ngẩng: hơi dưới ngang (−5°) tới gần nhìn từ trên (65°). */
        const val DEG_PER_HEIGHT = 90.0
        const val PITCH_MIN = -5.0
        const val PITCH_MAX = 65.0
    }
}
