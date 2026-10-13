package com.byd.clusternav.launcher

import kotlin.math.abs

/**
 * ═══ 3.01 R25 — QUAY mô hình về khung thế giới chung (thuần `:core`) ════════════════════════════════════════════
 *
 * Mỗi nguồn mô hình có trục riêng (glTF chuẩn là Y-lên; mô hình AVM của BYD chưa biết trục — [CHƯA BIẾT], chưa đo được
 * tệp thật). Thay vì ghi tay trục cho từng xe (CLAUDE.md §7: khác biệt giữa đời xe phải lộ ra bằng ĐO), ta đo:
 *  • trục ĐỨNG = trục hộp bao NGẮN nhất (xe nào cũng thấp hơn rộng, rộng hơn dài — sedan/SUV/MPV đều đúng);
 *  • trục DỌC xe = trục DÀI nhất; chiều đầu xe = từ neo ĐUÔI sang neo ĐẦU nếu bảng ràng buộc có cả hai (đèn trước ·
 *    cốp), không thì theo [frontSign] khai trong bảng.
 *  • trục còn lại thành X sao cho phép quay giữ chiều tay phải (det = +1) ⇒ trái vẫn là trái, không soi gương.
 * Kết quả: ma trận 4×4 (cột-chính, đúng kiểu `TransformManager.setTransform` của Filament) đưa tâm hộp bao về gốc.
 */
object Car3dOrient {

    /** Phép đặt: quay [rot] (3×3 hàng-chính: hàng i = trục thế giới i tính theo trục mô hình) quanh [center]. */
    data class Placement(val center: V3, val rot: List<V3>, val half: V3) {
        /** Điểm mô hình → thế giới. */
        fun apply(p: V3): V3 { val d = p - center; return V3(rot[0].dot(d), rot[1].dot(d), rot[2].dot(d)) }

        /** Hộp bao trong thế giới (tâm ở gốc). */
        val bounds: Car3dBounds get() = Car3dBounds(V3.ZERO, half)

        /** Ma trận 4×4 cột-chính cho Filament: world = R · (p − c). */
        fun matrix(): FloatArray {
            val t = V3(-rot[0].dot(center), -rot[1].dot(center), -rot[2].dot(center))
            return floatArrayOf(
                rot[0].x.toFloat(), rot[1].x.toFloat(), rot[2].x.toFloat(), 0f,
                rot[0].y.toFloat(), rot[1].y.toFloat(), rot[2].y.toFloat(), 0f,
                rot[0].z.toFloat(), rot[1].z.toFloat(), rot[2].z.toFloat(), 0f,
                t.x.toFloat(), t.y.toFloat(), t.z.toFloat(), 1f,
            )
        }
    }

    /**
     * @param center tâm hộp bao mô hình · [half] nửa cạnh (theo trục mô hình).
     * @param front / [rear] neo đầu/đuôi (khung mô hình) — `null` khi bảng không neo được ⇒ dùng [frontSign].
     */
    fun place(center: V3, half: V3, front: V3? = null, rear: V3? = null, frontSign: Int = 1): Placement {
        val ext = listOf(abs(half.x), abs(half.y), abs(half.z))
        val up = ext.indices.minByOrNull { ext[it] }!!
        val len = ext.indices.filter { it != up }.maxByOrNull { ext[it] }!!
        val sign = if (front != null && rear != null) {
            val d = comp(front - rear, len)
            if (abs(d) > 1e-9) (if (d > 0) 1 else -1) else frontSign
        } else frontSign
        val yAxis = unit(up, 1)
        val zAxis = unit(len, sign)
        val xAxis = yAxis.cross(zAxis)
        // Hàng của R = trục thế giới biểu diễn theo trục mô hình (R · v_model = v_world).
        val rot = listOf(xAxis, yAxis, zAxis)
        val h = V3(abs(rot[0].dot(half)), abs(rot[1].dot(half)), abs(rot[2].dot(half)))
        return Placement(center, rot, h)
    }

    private fun comp(v: V3, i: Int) = when (i) { 0 -> v.x; 1 -> v.y; else -> v.z }
    private fun unit(i: Int, s: Int): V3 = when (i) {
        0 -> V3(s.toDouble(), 0.0, 0.0)
        1 -> V3(0.0, s.toDouble(), 0.0)
        else -> V3(0.0, 0.0, s.toDouble())
    }
}
