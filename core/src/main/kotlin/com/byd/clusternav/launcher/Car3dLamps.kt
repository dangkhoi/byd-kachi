package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R25 — VAI của từng bóng đèn trên lưới AVM ⇒ kiểu vật liệu (thuần `:core`) ═══════════════════════════════════════
 *
 * Owner 13/10 (ảnh chụp): đèn đuôi là một dải TRẮNG cháy, mất hết chi tiết. Gốc [ĐO đọc mã]: mọi bộ phận `*lamp*` dùng chung
 * vật liệu `lamp` (ảnh `car_lamp.png` × trắng) và mọi bản `…on` dùng `lamp_on` phát sáng TRẮNG ×8 (đủ đỏ/hổ phách chỉ có cho
 * vài tên đoán theo số đèn Seal) ⇒ đèn đuôi sáng = trắng bão hoà. Nay vai suy ra bằng ĐO trên chính lưới (CLAUDE.md §7):
 *  • `*turn*` ⇒ xi-nhan (hổ phách khi sáng);
 *  • tâm bộ phận ở phần tư SAU của thân ([REAR_OF_HALF] nửa chiều dài, +X = đầu xe [ĐO]) ⇒ đèn SAU: luôn ĐỎ (tắt = đỏ sẫm, sáng =
 *    đỏ phát sáng vừa phải) — trừ mẫu đèn lùi trắng ([Car3dModel.whiteLamps]);
 *  • còn lại ⇒ đèn trắng (pha/cốt/ban ngày/gương/logo).
 * "Sáng" = tên tận cùng `on` mà KHÔNG có bộ phận tên + `on` (`trunk_lamp_position` có `…positionon` ⇒ nó là bản TẮT).
 * Phát sáng nhân với CHÍNH ảnh đèn (`emissiveTexture`, [Car3dGlb]) ⇒ giữ hoa văn LED; cường độ vừa phải + ánh xạ tông giữ màu
 * (`Car3dRenderer`) ⇒ không cháy trắng.
 */
object Car3dLamps {

    const val WHITE = "lamp"
    const val WHITE_ON = "lamp_on"
    const val REAR = "lamp_rear"
    const val REAR_ON = "lamp_rear_on"
    const val TURN_ON = "lamp_turn_on"

    /** (tên bộ phận chính xác → khoá kiểu) cho mọi bộ phận đèn của [mesh]; nối SAU `partStyles` tường minh của bảng. */
    fun styles(mesh: BydAvmMesh, whiteLamps: List<String>): List<Pair<String, String>> {
        val names = mesh.parts.map { it.name }.toSet()
        val body = mesh.parts.filter { !it.name.startsWith(WHEEL) && !it.name.contains(OPEN) }
        val (lo, hi) = xRange(mesh, body) ?: return emptyList()
        val cx = (lo + hi) / 2; val half = (hi - lo) / 2
        val out = ArrayList<Pair<String, String>>()
        for (p in mesh.parts) {
            // Kính che đèn (`body_glasslamp`, vật liệu `glasslamp`/`glasstail` [ĐO SL6]) là KÍNH ([Car3dMaterials.classOf]), không phải bóng.
            if (!p.name.contains(LAMP) || p.name.contains(GLASS) || p.material.contains(GLASS)) continue
            val on = isOn(p.name, names)
            val (a, b) = xRange(mesh, listOf(p)) ?: continue
            val rear = (a + b) / 2 < cx - REAR_OF_HALF * half
            val key = when {
                p.name.contains(TURN) -> if (on) TURN_ON else WHITE
                rear && whiteLamps.none { Car3dNames.matches(it, p.name) } -> if (on) REAR_ON else REAR
                else -> if (on) WHITE_ON else WHITE
            }
            out += p.name to key
        }
        return out
    }

    fun isOn(name: String, names: Set<String>): Boolean = name.endsWith(ON) && (name + ON) !in names

    private fun xRange(mesh: BydAvmMesh, parts: List<BydAvmMesh.Part>): Pair<Double, Double>? {
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        for (p in parts) {
            val b = mesh.buffer(p.buffer) ?: continue
            for (i in 0 until p.indexCount) { val x = b.position(p.index(i), 0).toDouble(); if (x < lo) lo = x; if (x > hi) hi = x }
        }
        return if (lo > hi) null else lo to hi
    }

    /** Phần tư sau: tâm cách tâm thân về phía đuôi hơn nửa nửa-chiều-dài. */
    const val REAR_OF_HALF = 0.5
    private const val LAMP = "lamp"
    private const val GLASS = "glass"
    private const val TURN = "turn"
    private const val ON = "on"
    private const val OPEN = "open"
    private const val WHEEL = "wheel"
}
