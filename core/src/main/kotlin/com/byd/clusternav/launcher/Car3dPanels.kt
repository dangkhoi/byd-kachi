package com.byd.clusternav.launcher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ═══ 3.01 POLISH2 — TẤM VỎ của lưới AVM: thành phần liên thông giới hạn bởi KHE TẤM (thuần `:core`) ═══════════════════════
 *
 * Owner 13/10: *"cốp mở chưa chuẩn, đang mở 1/2 bên trên của cốp… giống như cốp nó bị cắt đôi"*. Gốc [ĐO lưới Seal, bộ chạy thử
 * ngoài repo]: nắp cốp THẬT trong `body_paint` là HAI tấm rời (mặt trên 190 tam giác y 0,96–1,12 · mặt sau 379 tam giác y
 * 0,65–0,92), ngăn bởi dải đèn đuôi; bản cắt hình học cũ cắt ngang ở đáy dải đèn (y 0,86) ⇒ chỉ nửa trên nhấc lên. Lưới có khe
 * tấm THẬT: hai tấm không chung đỉnh nào (kể cả đỉnh trùng vị trí ở đường may UV) ⇒ tách tấm = thành phần liên thông của tam
 * giác nối qua đỉnh HÀN theo vị trí (lượng tử [WELD_M]).
 *
 * Chi phí: chỉ tam giác có đỉnh trong vùng [xBelow] (phần sau xe) — vài chục nghìn tam giác, một lần lúc dựng tệp đệm GLB;
 * bảng hàn địa chỉ mở `LongArray`/`IntArray` (không hộp `Long`) ⇒ vài MB heap trên trần 48 MB [ĐO máy ảo].
 */
object Car3dPanels {

    /** Một tấm: [tris] = chỉ số TAM GIÁC (thứ tự trong bộ phận [part]) · hộp bao [min]/[max] (x, y, z). */
    class Panel(val part: BydAvmMesh.Part, val tris: IntArray, val min: DoubleArray, val max: DoubleArray) {
        fun inside(lo: DoubleArray, hi: DoubleArray, pad: Double): Boolean = (0..2).all { k -> min[k] >= lo[k] - pad && max[k] <= hi[k] + pad }
    }

    /** Các tấm của [part] trong vùng x < [xBelow] (tam giác có ít nhất một đỉnh trong vùng). Bộ phận không chỉ số ⇒ rỗng. */
    fun of(mesh: BydAvmMesh, part: BydAvmMesh.Part, xBelow: Double): List<Panel> {
        val b = mesh.buffer(part.buffer) ?: return emptyList()
        if (!part.indexed) return emptyList()
        val n = part.indexCount / 3
        val inRegion = IntArrayList()
        for (t in 0 until n) if ((0..2).any { c -> b.position(part.index(t * 3 + c), 0) < xBelow }) inRegion.add(t)
        if (inRegion.size == 0) return emptyList()
        val weld = Weld(inRegion.size * 3)
        val vid = IntArray(inRegion.size * 3)
        for (i in 0 until inRegion.size) for (c in 0..2) {
            val v = part.index(inRegion[i] * 3 + c)
            vid[i * 3 + c] = weld.id(key(b.position(v, 0), b.position(v, 1), b.position(v, 2)))
        }
        val parent = IntArray(weld.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for (i in 0 until inRegion.size) {
            val r0 = find(vid[i * 3])
            for (c in 1..2) { val r = find(vid[i * 3 + c]); if (r != r0) parent[r] = r0 }
        }
        val byRoot = HashMap<Int, Int>()
        val members = ArrayList<IntArrayList>(); val mins = ArrayList<DoubleArray>(); val maxs = ArrayList<DoubleArray>()
        for (i in 0 until inRegion.size) {
            val slot = byRoot.getOrPut(find(vid[i * 3])) {
                members += IntArrayList(); mins += DoubleArray(3) { Double.MAX_VALUE }; maxs += DoubleArray(3) { -Double.MAX_VALUE }; members.size - 1
            }
            val t = inRegion[i]
            members[slot].add(t)
            val mn = mins[slot]; val mx = maxs[slot]
            for (c in 0..2) { val v = part.index(t * 3 + c); for (k in 0..2) { val x = b.position(v, k).toDouble(); if (x < mn[k]) mn[k] = x; if (x > mx[k]) mx[k] = x } }
        }
        return members.indices.map { Panel(part, members[it].toArray(), mins[it], maxs[it]) }
    }

    /** Bộ phận [p] chỉ còn các tam giác [tris] (thứ tự gốc), giữ tên (biến thể sơn/đèn theo tên vẫn áp); vật liệu/mặt mặc định giữ. */
    fun subset(
        p: BydAvmMesh.Part, tris: IntArray, material: String = p.material, single: Boolean = p.single, flip: Boolean = p.flip,
    ): BydAvmMesh.Part {
        val bb = ByteBuffer.allocate(tris.size * 3 * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (t in tris) for (c in 0..2) bb.putInt(p.index(t * 3 + c))
        return BydAvmMesh.Part(p.name, p.buffer, p.pivot, tris.size * 3, bb, 0, material, single, flip)
    }

    private fun key(x: Float, y: Float, z: Float): Long {
        fun q(v: Float) = (Math.round(v / WELD_M) + OFFSET).coerceIn(0L, MASK)
        return (q(x) shl 42) or (q(y) shl 21) or q(z)
    }

    /** Bảng băm địa chỉ mở khoá `Long` → mã đỉnh hàn (không hộp). */
    private class Weld(expected: Int) {
        private var cap = Integer.highestOneBit(maxOf(16, expected * 2)) shl 1
        private var keys = LongArray(cap) { EMPTY }
        private var vals = IntArray(cap)
        var size = 0; private set
        fun id(k: Long): Int {
            var i = (java.lang.Long.hashCode(k * -7046029254386353131L) and Int.MAX_VALUE) and (cap - 1)
            while (true) {
                val cur = keys[i]
                if (cur == EMPTY) { keys[i] = k; vals[i] = size; return size++ }
                if (cur == k) return vals[i]
                i = (i + 1) and (cap - 1)
            }
        }
    }

    internal class IntArrayList {
        private var a = IntArray(64); var size = 0; private set
        fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        operator fun get(i: Int) = a[i]
        fun toArray(): IntArray = a.copyOf(size)
    }

    /** Lượng tử hàn đỉnh: 0,5 mm — đỉnh trùng vị trí ở đường may UV coi là một; khe tấm thật của lưới AVM rộng hơn nhiều [ĐO Seal]. */
    const val WELD_M = 0.0005
    private const val OFFSET = 1L shl 20
    private const val MASK = (1L shl 21) - 1
    private const val EMPTY = Long.MIN_VALUE
}
