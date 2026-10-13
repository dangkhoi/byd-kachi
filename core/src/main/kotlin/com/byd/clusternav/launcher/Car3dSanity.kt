package com.byd.clusternav.launcher

import kotlin.math.abs
import kotlin.math.hypot

/**
 * ═══ 3.01 R25 — KIỂM HỢP LÝ lưới AVM lúc nạp (thuần `:core`) — hỏng ⇒ widget lùi bảng 2D + log đúng phép kiểm hỏng ════
 *
 * Owner 13/10 hỏi xe khác (Han, SL6): *"nó có khả năng lệch à?"*. Vị trí bánh KHÔNG có hằng số nào của Seal: nó là pivot của
 * từng khối trong `wheel.mesh` của CHÍNH đời xe đó (kind 0, toạ độ quanh pivot [ĐO]). Bộ kiểm này đo lại trên chính lưới vừa
 * đọc — không tin tên/kích thước nào — để một tệp đời lạ / hỏng / định dạng khác không bao giờ thành một chiếc xe méo trên
 * màn chính:
 *  1. **Hộp thân** (bộ phận không phải bánh, không phải bản "mở"): dài [LEN], rộng [WIDTH], cao [HEIGHT] (m). Dải rộng hơn
 *     sedan vì AVM có cả xe khách (`vde` dài 7,0 m [ĐO]) — mục đích là bắt tệp hỏng/sai đơn vị, không loại đời xe thật.
 *  2. **Bốn bánh**: đỉnh của các bộ phận `wheel*` chia theo dấu (x − tâm thân, z) phải đủ 4 góc (trước/sau × trái/phải);
 *     mỗi bánh bán kính [RADIUS]; đáy bánh thấp hơn đáy thân (bánh chạm đất, thân cách đất); chiều dài cơ sở [WHEELBASE];
 *     bánh trước ở nửa đầu xe; **nằm trong hốc**: không quá [ARCH_HITS] đỉnh thân nằm ở mặt ngoài bánh trong đĩa 0,85·r
 *     (bánh lệch ra khỏi hốc thì thân che mặt bánh).
 *  3. **Bộ phận cho điểm chạm**: ít nhất một neo KÍNH phân giải được (không thì widget 3D không có việc gì để làm).
 * [ĐO 13/10, bộ chạy thử ngoài repo trên 37 tệp `car.mesh` của AutoVideo.apk] mọi đời trong bảng [BydAvmModels] qua 1–2
 * (0 đỉnh thân trong đĩa bánh ở cả 4 góc); `sc2e/bev` không có bộ phận cửa nào ⇒ trượt 3 ⇒ bảng 2D.
 */
object Car3dSanity {

    data class Verdict(val failures: List<String>, val notes: List<String>) {
        val ok: Boolean get() = failures.isEmpty()
    }

    fun check(mesh: BydAvmMesh, model: Car3dModel): Verdict {
        val fail = ArrayList<String>(); val note = ArrayList<String>()
        val isWheel = { n: String -> n.startsWith(WHEEL_PREFIX) }
        val body = mesh.parts.filter { !isWheel(it.name) && !it.name.contains(OPEN) }
        val wheels = mesh.parts.filter { isWheel(it.name) }
        val mn = DoubleArray(3) { Double.MAX_VALUE }; val mx = DoubleArray(3) { -Double.MAX_VALUE }
        forEachVertex(mesh, body) { x, y, z -> val p = doubleArrayOf(x, y, z); for (k in 0..2) { if (p[k] < mn[k]) mn[k] = p[k]; if (p[k] > mx[k]) mx[k] = p[k] } }
        if (mn[0] > mx[0]) return Verdict(listOf("body: no vertices"), note)
        val len = mx[0] - mn[0]; val wid = mx[2] - mn[2]; val hei = mx[1] - mn[1]
        note += "body L%.2f W%.2f H%.2f".format(len, wid, hei)
        if (len !in LEN) fail += "body length %.2f m not in $LEN".format(len)
        if (wid !in WIDTH) fail += "body width %.2f m not in $WIDTH".format(wid)
        if (hei !in HEIGHT) fail += "body height %.2f m not in $HEIGHT".format(hei)
        // Bốn góc bánh.
        val cx = (mn[0] + mx[0]) / 2
        val q = Array(4) { DoubleArray(6) { if (it % 2 == 0) Double.MAX_VALUE else -Double.MAX_VALUE } }   // minX maxX minY maxY minZ maxZ
        val cnt = IntArray(4)
        forEachVertex(mesh, wheels) { x, y, z ->
            val i = (if (x > cx) 0 else 2) + (if (z < 0) 0 else 1)
            cnt[i]++
            val b = q[i]
            if (x < b[0]) b[0] = x; if (x > b[1]) b[1] = x; if (y < b[2]) b[2] = y; if (y > b[3]) b[3] = y
            if (z < b[4]) b[4] = z; if (z > b[5]) b[5] = z
        }
        val names = listOf("FL", "FR", "RL", "RR")
        val hubs = (0..3).map { i ->
            if (cnt[i] < MIN_WHEEL_VERTS) { fail += "wheel ${names[i]} missing (${cnt[i]} vertices)"; null }
            else q[i].let { b -> Hub((b[0] + b[1]) / 2, (b[2] + b[3]) / 2, (b[3] - b[2]) / 2, b[2], minOf(abs(b[4]), abs(b[5])), maxOf(abs(b[4]), abs(b[5])), if (i % 2 == 0) -1 else 1) }
        }
        hubs.forEachIndexed { i, h ->
            h ?: return@forEachIndexed
            note += "${names[i]} (%.2f,%.2f,%.2f) r%.2f".format(h.x, h.y, h.sz * h.zOut, h.r)
            if (h.r !in RADIUS) fail += "wheel ${names[i]} radius %.2f not in $RADIUS".format(h.r)
            if (h.bottom >= mn[1]) fail += "wheel ${names[i]} bottom %.2f not below body %.2f".format(h.bottom, mn[1])
        }
        val f = hubs[0]; val r = hubs[2]
        if (f != null && r != null) {
            val wb = f.x - r.x
            if (wb !in WHEELBASE) fail += "wheelbase %.2f m not in $WHEELBASE".format(wb)
        }
        // Trong hốc: đỉnh thân ở mặt NGOÀI bánh (|z| ≥ mép ngoài − 2 cm, cùng phía) mà lọt đĩa 0,85·r.
        val hits = IntArray(4)
        forEachVertex(mesh, body) { x, y, z ->
            for (i in 0..3) {
                val h = hubs[i] ?: continue
                if (z * h.sz > 0 && abs(z) >= h.zOut - FACE_SLAB && hypot(x - h.x, y - h.y) < ARCH_DISC * h.r) hits[i]++
            }
        }
        hits.forEachIndexed { i, n -> if (hubs[i] != null && n > ARCH_HITS) fail += "wheel ${names[i]} outside its arch ($n body vertices over the wheel face)" }
        // Neo kính.
        val partNames = mesh.parts.map { it.name }
        val windows = model.hotspots.filter { it.group == Car3dCatalog.GROUPS.first() && it.target in DOOR_KEYS }
        val resolvable = windows.count { resolves(it.anchor, partNames) }
        note += "window anchors $resolvable/${windows.size}"
        if (windows.isNotEmpty() && resolvable == 0) fail += "no window anchor resolves (no door/glass parts)"
        return Verdict(fail, note)
    }

    /** Neo [a] có ít nhất một bộ phận khớp trong [names] (cùng luật [Car3dNames.matches] bên vẽ dùng). */
    fun resolves(a: Car3dAnchor, names: List<String>): Boolean = when (a) {
        is Car3dAnchor.Point -> true
        is Car3dAnchor.Node -> names.any { Car3dNames.matches(a.pattern, it) }
        is Car3dAnchor.FirstOf -> a.options.any { resolves(it, names) }
    }

    private class Hub(val x: Double, val y: Double, val r: Double, val bottom: Double, val zIn: Double, val zOut: Double, val sz: Int)

    private inline fun forEachVertex(mesh: BydAvmMesh, parts: List<BydAvmMesh.Part>, f: (Double, Double, Double) -> Unit) {
        for (p in parts) {
            val b = mesh.buffer(p.buffer) ?: continue
            for (i in 0 until p.indexCount) {
                val v = p.index(i)
                f(b.position(v, 0).toDouble(), b.position(v, 1).toDouble(), b.position(v, 2).toDouble())
            }
        }
    }

    private const val WHEEL_PREFIX = "wheel"
    private const val OPEN = "open"
    private val DOOR_KEYS = setOf("lf", "rf", "lr", "rr")
    val LEN = 3.0..8.0
    val WIDTH = 1.4..2.8
    val HEIGHT = 1.0..3.2
    val RADIUS = 0.2..0.55
    val WHEELBASE = 1.8..5.0
    private const val MIN_WHEEL_VERTS = 30
    private const val FACE_SLAB = 0.02
    private const val ARCH_DISC = 0.85
    const val ARCH_HITS = 20
}
