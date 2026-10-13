package com.byd.clusternav.launcher

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ═══ 3.01 R25 — BẢN LỀ suy từ cặp bộ phận đóng/mở của lưới AVM (thuần `:core`) ════════════════════════════════════
 *
 * Owner 13/10: *"cửa mở, cốp mở có mở từ từ ra không? đang thấy giật phát ra luôn"*. Lưới AVM KHÔNG có dữ liệu bản lề — chỉ có
 * hai bản hình học: `doorlf_paint` (đóng) và `doorlf_paint_open` (mở) với CÙNG số chỉ số và cùng thứ tự tam giác [ĐO Seal:
 * 4 966 tam giác cả hai]. Phép biến đổi cứng đóng → mở được KHỚP từ chính các cặp đỉnh tương ứng (phương pháp quaternion của
 * Horn, trị riêng lớn nhất của ma trận 4×4 đối xứng bằng Jacobi) ⇒ trục + góc + một điểm trên trục. Sai số khớp
 * ([Hinge.rms]) > [MAX_RMS] ⇒ không phải chuyển động cứng ⇒ `null` (đổi bản ngay như cũ, không bịa đường đi).
 * Diễn hoạt = quay bản ĐÓNG quanh trục đó theo tỉ lệ góc s ∈ [0,1], tới 1 thì đổi sang đúng bản MỞ của tệp.
 */
object Car3dHinge {

    /** p_mở ≈ R·(p − c) + c, R = quay [angle] rad quanh [axis] (đơn vị) qua điểm [pivot]. */
    data class Hinge(val axis: V3, val angle: Double, val pivot: V3, val rms: Double) {
        /** Ma trận 4×4 cột-chính (Filament) cho tỉ lệ góc [s]. */
        fun matrix(s: Float): FloatArray {
            val a = angle * s.coerceIn(0f, 1f)
            val (x, y, z) = Triple(axis.x, axis.y, axis.z)
            val c = cos(a); val sn = sin(a); val t = 1 - c
            val r = arrayOf(
                doubleArrayOf(t * x * x + c, t * x * y - sn * z, t * x * z + sn * y),
                doubleArrayOf(t * x * y + sn * z, t * y * y + c, t * y * z - sn * x),
                doubleArrayOf(t * x * z - sn * y, t * y * z + sn * x, t * z * z + c),
            )
            val p = doubleArrayOf(pivot.x, pivot.y, pivot.z)
            val tr = DoubleArray(3) { i -> p[i] - (0..2).sumOf { j -> r[i][j] * p[j] } }
            return floatArrayOf(
                r[0][0].toFloat(), r[1][0].toFloat(), r[2][0].toFloat(), 0f,
                r[0][1].toFloat(), r[1][1].toFloat(), r[2][1].toFloat(), 0f,
                r[0][2].toFloat(), r[1][2].toFloat(), r[2][2].toFloat(), 0f,
                tr[0].toFloat(), tr[1].toFloat(), tr[2].toFloat(), 1f,
            )
        }

        fun apply(p: V3, s: Float): V3 {
            val m = matrix(s)
            return V3(m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12], m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13], m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14])
        }

        /** `extras` JSON: `[ax,ay,az,góc,cx,cy,cz]`. */
        fun json(): String = listOf(axis.x, axis.y, axis.z, angle, pivot.x, pivot.y, pivot.z).joinToString(",", "[", "]") { it.toFloat().toString() }
    }

    /** Khớp từ hai dãy điểm tương ứng; ít hơn 3 điểm / không quay / sai số lớn ⇒ `null`. */
    fun fit(closed: List<V3>, open: List<V3>): Hinge? {
        if (closed.size != open.size || closed.size < 3) return null
        val n = closed.size.toDouble()
        val ca = closed.fold(V3.ZERO) { s, p -> s + p } * (1 / n)
        val cb = open.fold(V3.ZERO) { s, p -> s + p } * (1 / n)
        val m = Array(3) { DoubleArray(3) }
        for (i in closed.indices) {
            val a = closed[i] - ca; val b = open[i] - cb
            val av = doubleArrayOf(a.x, a.y, a.z); val bv = doubleArrayOf(b.x, b.y, b.z)
            for (r in 0..2) for (c in 0..2) m[r][c] += av[r] * bv[c]
        }
        val (sxx, sxy, sxz) = Triple(m[0][0], m[0][1], m[0][2])
        val (syx, syy, syz) = Triple(m[1][0], m[1][1], m[1][2])
        val (szx, szy, szz) = Triple(m[2][0], m[2][1], m[2][2])
        val nm = arrayOf(
            doubleArrayOf(sxx + syy + szz, syz - szy, szx - sxz, sxy - syx),
            doubleArrayOf(syz - szy, sxx - syy - szz, sxy + syx, szx + sxz),
            doubleArrayOf(szx - sxz, sxy + syx, -sxx + syy - szz, syz + szy),
            doubleArrayOf(sxy - syx, szx + sxz, syz + szy, -sxx - syy + szz),
        )
        val q = largestEigenvector(nm)
        val qw = q[0]; val qv = V3(q[1], q[2], q[3])
        val sinHalf = qv.length()
        if (sinHalf < 1e-6) return null
        val angle = 2 * acos(qw.coerceIn(-1.0, 1.0))
        val axis = qv * (1 / sinHalf)
        // Điểm trên trục: R·(ca − c) + c = cb ⇒ (I − R)·c = cb − R·ca, cộng ràng buộc c·axis = ca·axis (khử bậc tự do dọc trục).
        val h0 = Hinge(axis, angle, V3.ZERO, 0.0)
        val rca = h0.apply(ca, 1f)
        val rhs = cb - rca
        val rm = h0.matrix(1f)
        val iMinusR = Array(3) { i -> DoubleArray(3) { j -> (if (i == j) 1.0 else 0.0) - rm[j * 4 + i] } }
        val ax = doubleArrayOf(axis.x, axis.y, axis.z)
        val ata = Array(3) { i -> DoubleArray(3) { j -> (0..2).sumOf { k -> iMinusR[k][i] * iMinusR[k][j] } + ax[i] * ax[j] } }
        val rv = doubleArrayOf(rhs.x, rhs.y, rhs.z)
        val atb = DoubleArray(3) { i -> (0..2).sumOf { k -> iMinusR[k][i] * rv[k] } + ax[i] * ca.dot(axis) }
        val c = solve3(ata, atb) ?: return null
        val h = Hinge(axis, angle, V3(c[0], c[1], c[2]), 0.0)
        val rms = sqrt(closed.indices.sumOf { i -> val d = h.apply(closed[i], 1f) - open[i]; d.dot(d) } / n)
        return if (rms > MAX_RMS || abs(angle) < MIN_ANGLE) null else h.copy(rms = rms)
    }

    /**
     * Bản lề cho MỌI bộ phận đóng có bản mở cùng tên trong [mesh]: `X` ↔ `X_open`, `Xon` ↔ `X_openon` (đèn trên cánh cửa), cùng
     * số chỉ số; điểm tương ứng = đỉnh ở cùng VỊ TRÍ chỉ số (lấy mẫu ≤ [SAMPLES]). Khớp hỏng ⇒ bộ phận đó không có bản lề.
     */
    fun fitAll(mesh: BydAvmMesh): Map<String, Hinge> {
        val byName = mesh.parts.associateBy { it.name }
        val out = HashMap<String, Hinge>()
        for (p in mesh.parts) {
            if (p.name.contains(OPEN)) continue
            val openName = if (p.name.endsWith(ON) && byName.containsKey(p.name.removeSuffix(ON) + "_" + OPEN + ON)) p.name.removeSuffix(ON) + "_" + OPEN + ON
            else p.name + "_" + OPEN
            val o = byName[openName] ?: continue
            if (o.indexCount != p.indexCount || p.indexCount < 3) continue
            val a = mesh.buffer(p.buffer) ?: continue; val b = mesh.buffer(o.buffer) ?: continue
            val step = maxOf(1, p.indexCount / SAMPLES)
            val idx = (0 until p.indexCount step step)
            fun pt(buf: BydAvmMesh.Buffer, v: Int) = V3(buf.position(v, 0).toDouble(), buf.position(v, 1).toDouble(), buf.position(v, 2).toDouble())
            fit(idx.map { pt(a, p.index(it)) }, idx.map { pt(b, o.index(it)) })?.let { out[p.name] = it }
        }
        return out
    }

    /** Đọc lại từ `extras` (`"hinge":[7 số]`); hỏng ⇒ `null`. */
    fun parse(extras: String?): Hinge? {
        val body = RX.find(extras ?: return null)?.groupValues?.get(1) ?: return null
        val v = body.split(',').map { it.trim().toDoubleOrNull() ?: return null }
        if (v.size != 7) return null
        return Hinge(V3(v[0], v[1], v[2]), v[3], V3(v[4], v[5], v[6]), 0.0)
    }

    private val RX = Regex(""""hinge"\s*:\s*\[([^\]]*)]""")

    private fun largestEigenvector(a0: Array<DoubleArray>): DoubleArray {
        val a = Array(4) { a0[it].copyOf() }
        val v = Array(4) { i -> DoubleArray(4) { j -> if (i == j) 1.0 else 0.0 } }
        repeat(JACOBI_SWEEPS) {
            for (p in 0 until 3) for (q in p + 1 until 4) {
                if (abs(a[p][q]) < 1e-15) continue
                val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
                val c = 1 / sqrt(t * t + 1); val s = t * c
                for (k in 0 until 4) {
                    val akp = a[k][p]; val akq = a[k][q]
                    a[k][p] = c * akp - s * akq; a[k][q] = s * akp + c * akq
                }
                for (k in 0 until 4) {
                    val apk = a[p][k]; val aqk = a[q][k]
                    a[p][k] = c * apk - s * aqk; a[q][k] = s * apk + c * aqk
                }
                for (k in 0 until 4) {
                    val vkp = v[k][p]; val vkq = v[k][q]
                    v[k][p] = c * vkp - s * vkq; v[k][q] = s * vkp + c * vkq
                }
            }
        }
        val best = (0 until 4).maxByOrNull { a[it][it] }!!
        val e = DoubleArray(4) { v[it][best] }
        return if (e[0] < 0) DoubleArray(4) { -e[it] } else e
    }

    private fun solve3(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val m = Array(3) { i -> doubleArrayOf(a[i][0], a[i][1], a[i][2], b[i]) }
        for (i in 0..2) {
            val p = (i..2).maxByOrNull { abs(m[it][i]) }!!
            if (abs(m[p][i]) < 1e-12) return null
            val tmp = m[i]; m[i] = m[p]; m[p] = tmp
            for (r in 0..2) if (r != i) { val k = m[r][i] / m[i][i]; for (c in i..3) m[r][c] -= k * m[i][c] }
        }
        return DoubleArray(3) { m[it][3] / m[it][it] }
    }

    /** Khớp cứng tốt hơn 1 cm (trung bình bình phương) mới là bản lề; quay < 2° coi như không quay. */
    const val MAX_RMS = 0.01
    private const val SAMPLES = 400
    private const val OPEN = "open"
    private const val ON = "on"
    private const val MIN_ANGLE = 0.035
    private const val JACOBI_SWEEPS = 20
}
