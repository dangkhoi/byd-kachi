package com.byd.clusternav.launcher

import kotlin.math.roundToLong

/**
 * ═══ 3.01 R25 — bỏ BỘ PHẬN GỘP (bản sao hợp của nhiều bộ phận khác) khỏi lưới AVM (thuần `:core`) ═══════════════════════
 *
 * [ĐO 13/10, bộ chạy thử ngoài repo trên `car.mesh` Seal] `doorlf_open` (khai vật liệu `texture1`, chỉ số vào bộ đỉnh `open`)
 * là HỢP hình học của cả cánh cửa mở: 100 % đỉnh của `doorlf_paint_open` · `doorlf_texture1_open` · `doorlf_glass_open` nằm
 * trong nó, còn nó chỉ trùng 45 % với `paint_open`, 41 % với `black_open`, 15 % với `texture1_open`. App AVM dùng nó vào việc
 * khác (thuộc tính `attr` của bộ đỉnh `open` là tham số màu, không phải UV); vẽ nó bằng ảnh cửa ⇒ một lớp ĐEN trùng mặt phủ lên
 * cánh cửa mở (owner 13/10: *"cửa mở màu sai, không theo màu xe"*, *"kính cửa mở trông khác"*).
 *
 * Luật chung (đo, không theo tên): bộ phận P là GỘP khi ≥ [COVER] mẫu đỉnh của P trùng (lưới [GRID_M]) đỉnh của bộ phận KHÁC,
 * nhưng KHÔNG một bộ phận đơn lẻ nào phủ ≥ [COVER] mẫu đó (phủ bởi hợp, không phải bản sao của một bộ phận — loại trừ đèn
 * xi-nhan chạy `rear0on..3on` vốn là các bản sao giống hệt nhau, và cặp `lamp_15`/`lamp_15on`), và các mẫu trùng rải trên ≥ 2
 * bộ phận, và P CHỨA TRỌN ≥ 2 bộ phận thuộc ≥ 2 vật liệu khác nhau. Bộ nhớ: một `LongArray` khoá đỉnh (≤ ~7 MB với Seal), sống trong lời gọi.
 */
object Car3dDupes {

    /** Tên các bộ phận GỘP trong [mesh]. */
    fun aggregates(mesh: BydAvmMesh): Set<String> {
        val parts = mesh.parts.filter { it.indexCount > 0 && mesh.buffer(it.buffer) != null }
        if (parts.size < 3) return emptySet()
        var n = 0
        for (p in parts) n += p.indexCount
        val all = LongArray(n); var k = 0
        parts.forEachIndexed { pi, p ->
            val b = mesh.buffer(p.buffer)!!
            for (i in 0 until p.indexCount) all[k++] = (key(b, p.index(i)) shl PART_BITS) or pi.toLong()
        }
        all.sort()
        val out = HashSet<String>()
        parts.forEachIndexed { pi, p ->
            val b = mesh.buffer(p.buffer)!!
            val step = maxOf(1, p.indexCount / SAMPLES)
            var samples = 0; var covered = 0
            val per = HashMap<Int, Int>()
            var i = 0
            while (i < p.indexCount) {
                samples++
                val owners = ownersOf(all, key(b, p.index(i)), pi)
                if (owners.isNotEmpty()) { covered++; owners.forEach { per[it] = (per[it] ?: 0) + 1 } }
                i += step
            }
            val best = per.values.maxOrNull() ?: 0
            if (covered < COVER * samples || best >= COVER * samples || per.size < 2) return@forEachIndexed
            // Và P CHỨA TRỌN ≥ 2 bộ phận khác (đúng nghĩa "hợp của nhiều bộ phận") — bộ phận thật chỉ tình cờ trùng mép với
            // vài bộ phận (đo trên sa3ef/sa3hkl: `doorrf_paint`) không qua.
            val inside = per.keys.filter { qi -> qi != pi && contains(all, mesh, parts[qi], pi) }
            // …thuộc ≥ 2 VẬT LIỆU khác nhau: một mặt thật chỉ có một vật liệu; cụm đèn chạy (`turnrightrear` ⊇ `rear0on..3on`, đo
            // sc2e/paha) chứa nhiều bộ phận nhưng cùng vật liệu `lamp` ⇒ là bản TẮT thật, giữ.
            if (inside.size >= 2 && inside.map { parts[it].material }.distinct().size >= 2) out += p.name
        }
        return out
    }

    /** ≥ [COVER] mẫu đỉnh của [q] có trong bộ phận thứ [pi]. */
    private fun contains(all: LongArray, mesh: BydAvmMesh, q: BydAvmMesh.Part, pi: Int): Boolean {
        val b = mesh.buffer(q.buffer) ?: return false
        val step = maxOf(1, q.indexCount / SAMPLES)
        var n = 0; var hit = 0; var i = 0
        while (i < q.indexCount) { n++; if (pi in ownersOf(all, key(b, q.index(i)), -1)) hit++; i += step }
        return n > 0 && hit >= COVER * n
    }

    /** [mesh] không có các bộ phận [names]. */
    fun without(mesh: BydAvmMesh, names: Set<String>): BydAvmMesh =
        if (names.isEmpty()) mesh else BydAvmMesh(mesh.buffers, mesh.parts.filter { it.name !in names }, mesh.skipped)

    private fun ownersOf(all: LongArray, key: Long, self: Int): Set<Int> {
        val lo = key shl PART_BITS
        var a = 0; var z = all.size
        while (a < z) { val m = (a + z) ushr 1; if (all[m] < lo) a = m + 1 else z = m }
        val out = HashSet<Int>()
        var i = a
        while (i < all.size && (all[i] ushr PART_BITS) == key) {
            val p = (all[i] and PART_MASK).toInt(); if (p != self) out += p; i++
        }
        return out
    }

    private fun key(b: BydAvmMesh.Buffer, v: Int): Long {
        fun q(c: Float): Long = ((c / GRID_M).roundToLong() + AXIS_OFFSET) and AXIS_MASK
        return (q(b.position(v, 0)) shl (AXIS_BITS * 2)) or (q(b.position(v, 1)) shl AXIS_BITS) or q(b.position(v, 2))
    }

    /** Lưới so đỉnh 2 mm (nhiễu float của hai bản xuất cùng hình < 0,1 mm). */
    const val GRID_M = 0.002
    const val COVER = 0.95
    private const val SAMPLES = 400
    private const val AXIS_BITS = 15
    private const val AXIS_MASK = (1L shl AXIS_BITS) - 1
    private const val AXIS_OFFSET = 1L shl (AXIS_BITS - 1)   // ±32 m ở lưới 2 mm
    private const val PART_BITS = 16
    private const val PART_MASK = (1L shl PART_BITS) - 1
}
