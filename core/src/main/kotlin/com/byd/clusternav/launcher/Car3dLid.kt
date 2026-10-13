package com.byd.clusternav.launcher

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * ═══ 3.01 R25 — NẮP CỐP diễn hoạt được cho MỌI đời lưới AVM (thuần `:core`) ═══════════════════════════════════════════
 *
 * Owner 13/10: logcat đã thấy `voiceCtlBackDoor [1]` gửi đi mà hình 3D đứng yên. [ĐO 37 tệp `car.mesh`]: chỉ eqeal · ew · paha ·
 * sc3e · sc3ebe · sthny có cặp `trunk*` ↔ `trunk*_open` ([Car3dHinge.fitAll] khớp bản lề từ cặp đó); 20 tệp có `trunk_*` nhưng
 * KHÔNG có bản mở (hc, sa20, stf, hx, …); Seal (eke), vde KHÔNG có bộ phận cốp nào (nắp nằm trong `body_paint`).
 *
 * Luật chung, theo thứ tự (không `if` theo đời xe):
 *  1. Có `trunk*open*` ⇒ không làm gì (bản lề khớp từ cặp đóng/mở).
 *  2. Có `trunk*` (không mở) ở phần tư sau ⇒ đó là nắp.
 *  3. Không có ⇒ TÁCH nắp THẬT từ thân theo KHE TẤM ([Car3dPanels]; POLISH2 — owner 13/10 *"cốp… bị cắt đôi"*: bản cắt ngang
 *     cũ ở đáy dải đèn đuôi chỉ nhấc nửa trên). Tấm HẠT GIỐNG = tấm SƠN nằm hẳn trong phần tư sau, vươn ra sau mép dưới kính
 *     hậu, cao hơn sàn [FLOOR_FRACTION] chiều cao thân (loại cản sau), vắt qua trục giữa và rộng ≥ [LID_MIN_WIDTH] bề ngang
 *     thân (loại chi tiết nhỏ) — Seal [ĐO]: mặt trên (190 tam giác) + mặt sau (379) của nắp. Hộp bao các hạt giống (nới [PAD])
 *     = vỏ nắp; MỌI tấm của mọi bộ phận thân (đèn, viền đen, logo, ốp — trừ kính/nội thất) nằm TRỌN trong vỏ đi theo nắp: đoạn
 *     giữa dải đèn đuôi trên nắp theo nắp, hai đoạn hông trên sườn ở lại. Phần tách giữ NGUYÊN tên bộ phận gốc + `extras.grp`.
 * Bản lề: trục ngang (Z) qua mép TRƯỚC-TRÊN của nắp (x lớn nhất, y lớn nhất của nắp); mở [OPEN_DEG]° nhấc đuôi lên. Trục mô
 * hình AVM: +X đầu xe, +Y lên [ĐO Seal, `Car3dSealTest`]. KHOANG cốp ([CAVITY_PART]) — đáy + vách MỘT màu đen sâu dưới vỏ
 * nắp — thay cho "lỗ đen" nhìn xuyên vào thân khi nắp mở. Không đủ dữ liệu ⇒ `null` (cốp giữ chấm như trước, không bịa nắp).
 */
object Car3dLid {

    /** [mesh] sau khi tách (+ khoang) · [lid] các bộ phận NẮP (đồng nhất theo đối tượng) · [hinge] bản lề chung của nắp. */
    class Result(val mesh: BydAvmMesh, val lid: Set<BydAvmMesh.Part>, val hinge: Car3dHinge.Hinge)

    fun of(mesh: BydAvmMesh): Result? {
        val names = mesh.parts.map { it.name }
        if (names.any { Car3dNames.matches(OPEN_PATTERN, it) }) return null
        // Bộ phận `trunk_*` có tâm ở phần tư SAU (đo: hc/bev có `trunk_*` trải tới đầu xe, ster tới giữa xe — không phải nắp).
        val named = mesh.parts.filter { Car3dNames.matches(NAMED, it.name) }.let { all ->
            val body = mesh.parts.filter { !it.name.startsWith(WHEEL) && !it.name.contains(OPEN) }
            val (lo, hi) = xRange(mesh, body) ?: return null
            val cx = (lo + hi) / 2; val half = (hi - lo) / 2
            all.filter { p -> xRange(mesh, listOf(p))?.let { (a, b) -> (a + b) / 2 < cx - REAR_QUARTER * half } == true }
        }
        if (named.isNotEmpty()) {
            val h = hinge(named, mesh) ?: return null
            return Result(withCavity(mesh, mesh.parts, named), named.toIdentitySet(), h)
        }
        return split(mesh)
    }

    private fun split(mesh: BydAvmMesh): Result? {
        val body = mesh.parts.filter { it.name.startsWith(BODY) && !it.name.contains(OPEN) && it.indexed }
        if (body.isEmpty()) return null
        val mn = DoubleArray(3) { Double.MAX_VALUE }; val mx = DoubleArray(3) { -Double.MAX_VALUE }
        each(mesh, body) { p -> for (k in 0..2) { if (p[k] < mn[k]) mn[k] = p[k]; if (p[k] > mx[k]) mx[k] = p[k] } }
        val cx = (mn[0] + mx[0]) / 2; val halfLen = (mx[0] - mn[0]) / 2; val halfW = (mx[2] - mn[2]) / 2
        // Mép dưới kính hậu: điểm kính có x nhỏ nhất trong dải giữa.
        var gx = Double.MAX_VALUE
        each(mesh, body.filter { it.name.contains(GLASS) }) { p -> if (abs(p[2]) < CENTER_BAND * halfW && p[0] < gx) gx = p[0] }
        if (gx == Double.MAX_VALUE || gx > cx) return null
        val rear = cx - REAR_QUARTER * halfLen
        val floor = mn[1] + FLOOR_FRACTION * (mx[1] - mn[1])
        val panels = body.filter { !it.name.contains(GLASS) && !it.name.contains(INTERIOR) }.flatMap { Car3dPanels.of(mesh, it, rear) }
        val seeds = panels.filter { s ->
            (s.part.material.contains(PAINT) || s.part.name.contains(PAINT)) && s.max[0] < rear && s.min[0] < gx && s.min[1] > floor &&
                s.min[2] < 0 && s.max[2] > 0 && s.max[2] - s.min[2] >= LID_MIN_WIDTH * 2 * halfW
        }
        if (seeds.isEmpty()) return null
        val lo = DoubleArray(3) { k -> seeds.minOf { it.min[k] } }; val hi = DoubleArray(3) { k -> seeds.maxOf { it.max[k] } }
        val onLid = panels.filter { it.max[0] < rear && it.inside(lo, hi, PAD) }.groupBy { it.part }
        val parts = ArrayList<BydAvmMesh.Part>(); val lid = ArrayList<BydAvmMesh.Part>()
        for (p in mesh.parts) {
            val mine = onLid[p]
            if (mine == null) { parts += p; continue }
            val take = HashSet<Int>(); mine.forEach { pn -> pn.tris.forEach { take += it } }
            val keep = (0 until p.indexCount / 3).filter { it !in take }.toIntArray()
            if (keep.isNotEmpty()) parts += Car3dPanels.subset(p, keep)
            Car3dPanels.subset(p, take.sorted().toIntArray()).also { parts += it; lid += it }
        }
        if (lid.isEmpty()) return null
        val h = hinge(lid, mesh) ?: return null
        return Result(withCavity(mesh, parts, lid), lid.toIdentitySet(), h)
    }

    /**
     * Thêm KHOANG cốp sạch dưới vỏ nắp [lid] (owner 13/10 *"bên trong cốp lộn xộn, không giống sàn cốp xe thật"*):
     *  1. ẨN theo ĐO bao kín: mọi tam giác thân (không thuộc nắp, không kính) nằm TRỌN trong thể tích khoang — chỉ thấy được qua
     *     miệng cốp khi nắp mở (nắp đóng thì nó bị vỏ che) — là kết cấu trong (gân, mặt trong cản/đèn) ⇒ bỏ.
     *  2. SÀN lót phẳng ngang mép dưới nắp ([CAVITY_FLOOR_DROP] dưới mép) trải gần trọn miệng + vách trước, hai vách hông lùi vào
     *     ([CAVITY_REAR] · [CAVITY_WIDTH] · [CAVITY_HEIGHT]): [ĐO máy ảo 13/10, bản đầu] khoang theo NGUYÊN hộp vỏ nắp chọc ra ngoài
     *     (mặt sau cong vào ở hai góc: x −2,35 giữa · −2,14 ở góc) ⇒ hai vệt đen dọc khi nắp ĐÓNG; sàn sâu 15 cm thì mép trong cản
     *     sau (y 0,56) chọc lên thành dải răng cưa. Vật liệu [CAVITY] đen sâu một màu (owner 13/10), pháp tuyến hướng VÀO khoang.
     */
    private fun withCavity(mesh: BydAvmMesh, parts0: List<BydAvmMesh.Part>, lid: List<BydAvmMesh.Part>): BydAvmMesh {
        val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        each(mesh, lid) { p -> for (k in 0..2) { if (p[k] < lo[k]) lo[k] = p[k]; if (p[k] > hi[k]) hi[k] = p[k] } }
        if (lo[0] > hi[0]) return BydAvmMesh(mesh.buffers, parts0, mesh.skipped)
        val len = hi[0] - lo[0]; val hgt = hi[1] - lo[1]; val half = maxOf(abs(lo[2]), abs(hi[2]))
        val xf = lo[0] + 2 * CAVITY_INSET; val xw = lo[0] + CAVITY_REAR * len; val x1 = hi[0] - CAVITY_INSET
        val z = CAVITY_WIDTH * half; val yF = lo[1] - CAVITY_FLOOR_DROP; val yT = lo[1] + CAVITY_HEIGHT * hgt
        // 1. Kết cấu trong khoang (đo bao kín, không theo tên).
        val inLid = lid.toIdentitySet()
        // Dải sau (giữa mép nắp và vách) chỉ trong bề ngang dải sàn sau — hai góc là cản/hông ôm vào (vỏ ngoài, không được bỏ).
        val zs = CAVITY_SILL_WIDTH * half
        fun inside(x: Double, y: Double, zz: Double): Boolean {
            val wide = if (x > xw) z else zs
            return x > xf && x < x1 && y > yF && y < hi[1] - CAVITY_INSET && abs(zz) < wide
        }
        val parts = parts0.map { p ->
            if (p in inLid || !p.indexed || !p.name.startsWith(BODY) || p.name.contains(GLASS)) return@map p
            val b = mesh.buffer(p.buffer) ?: return@map p
            // Theo TRỌNG TÂM tam giác: [ĐO máy ảo 13/10] luật "cả ba đỉnh trong" để lại tam giác lớn vắt qua mép ⇒ sàn vẫn đen lổn nhổn.
            val keep = (0 until p.indexCount / 3).filter { t ->
                val c = DoubleArray(3); for (k in 0..2) { val v = p.index(t * 3 + k); for (a in 0..2) c[a] += b.position(v, a) / 3.0 }
                !inside(c[0], c[1], c[2])
            }
            if (keep.size * 3 == p.indexCount) p else Car3dPanels.subset(p, keep.toIntArray())
        }.filter { it.indexCount > 0 }
        // 2. Sàn + vách.
        val q = ArrayList<FloatArray>()   // mỗi đỉnh: x, y, z, nx, ny, nz
        // Thứ tự đỉnh theo PHÁP TUYẾN: [ĐO máy ảo 13/10] vật liệu hai mặt của Filament LẬT pháp tuyến ở mặt sau ⇒ sàn quấn ngược
        // chiều thành "mặt sau" khi nhìn từ trên, pháp tuyến chúi xuống ⇒ sàn ĐEN dù vách xám. Quấn ngược ⇒ đảo thứ tự.
        fun quad(a: V3, b: V3, c: V3, d: V3, n: V3) {
            val ccw = (b - a).cross(c - a).dot(n) > 0
            (if (ccw) listOf(a, b, c, a, c, d) else listOf(a, c, b, a, d, c)).forEach { v ->
                q += floatArrayOf(v.x.toFloat(), v.y.toFloat(), v.z.toFloat(), n.x.toFloat(), n.y.toFloat(), n.z.toFloat())
            }
        }
        quad(V3(xw, yF, -z), V3(x1, yF, -z), V3(x1, yF, z), V3(xw, yF, z), V3(0.0, 1.0, 0.0))     // sàn lót
        // dải sàn sau hẹp hơn ([zs]): hai góc sau mặt nắp cong vào (bản rộng lòi ra ngoài ở góc)
        quad(V3(xf, yF, -zs), V3(xw, yF, -zs), V3(xw, yF, zs), V3(xf, yF, zs), V3(0.0, 1.0, 0.0))
        quad(V3(x1, yF, -z), V3(x1, yT, -z), V3(x1, yT, z), V3(x1, yF, z), V3(-1.0, 0.0, 0.0))    // vách trước
        quad(V3(xw, yF, -z), V3(x1, yF, -z), V3(x1, yT, -z), V3(xw, yT, -z), V3(0.0, 0.0, 1.0))   // vách hông −Z
        quad(V3(xw, yF, z), V3(x1, yF, z), V3(x1, yT, z), V3(xw, yT, z), V3(0.0, 0.0, -1.0))      // vách hông +Z
        val n = q.size
        val bb = ByteBuffer.allocate(n * 36).order(ByteOrder.LITTLE_ENDIAN)
        q.forEach { v -> bb.putFloat(v[0]); bb.putFloat(v[1]); bb.putFloat(v[2]) }
        repeat(n * 3) { bb.putFloat(0f) }
        q.forEach { v -> bb.putFloat(v[3]); bb.putFloat(v[4]); bb.putFloat(v[5]) }
        val buf = BydAvmMesh.Buffer(CAVITY_PART, V3.ZERO, n, bb, 0, n * 12, n * 24)
        val part = BydAvmMesh.Part(CAVITY_PART, CAVITY_PART, V3.ZERO, n, null, 0, CAVITY)
        return BydAvmMesh(mesh.buffers + buf, parts + part, mesh.skipped)
    }

    private fun hinge(lid: List<BydAvmMesh.Part>, mesh: BydAvmMesh): Car3dHinge.Hinge? {
        var x = -Double.MAX_VALUE; var y = -Double.MAX_VALUE; var any = false
        for (p in lid) {
            val b = mesh.buffer(p.buffer) ?: continue
            for (i in 0 until p.indexCount) { val v = p.index(i); any = true; x = maxOf(x, b.position(v, 0).toDouble()); y = maxOf(y, b.position(v, 1).toDouble()) }
        }
        if (!any) return null
        // Quay quanh +Z một góc ÂM: điểm sau bản lề (x nhỏ hơn) đi LÊN.
        return Car3dHinge.Hinge(V3(0.0, 0.0, 1.0), -Math.toRadians(OPEN_DEG), V3(x, y, 0.0), 0.0)
    }

    private fun xRange(mesh: BydAvmMesh, parts: List<BydAvmMesh.Part>): Pair<Double, Double>? {
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        each(mesh, parts) { q -> if (q[0] < lo) lo = q[0]; if (q[0] > hi) hi = q[0] }
        return if (lo > hi) null else lo to hi
    }

    private inline fun each(mesh: BydAvmMesh, parts: List<BydAvmMesh.Part>, f: (DoubleArray) -> Unit) {
        val p = DoubleArray(3)
        for (part in parts) {
            val b = mesh.buffer(part.buffer) ?: continue
            for (i in 0 until part.indexCount) { val v = part.index(i); for (k in 0..2) p[k] = b.position(v, k).toDouble(); f(p) }
        }
    }

    private fun List<BydAvmMesh.Part>.toIdentitySet(): Set<BydAvmMesh.Part> =
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap<BydAvmMesh.Part, Boolean>()).also { it.addAll(this) }

    const val GROUP = "trunk"
    const val OPEN_DEG = 62.0
    private const val OPEN_PATTERN = "trunk*open*"
    private const val NAMED = "trunk*"
    private const val BODY = "body_"
    private const val WHEEL = "wheel"
    private const val OPEN = "open"
    private const val GLASS = "glass"
    private const val INTERIOR = "interior"
    private const val PAINT = "paint"
    private const val CENTER_BAND = 0.25
    private const val REAR_QUARTER = 0.5
    /** Tấm hạt giống cao hơn đáy thân ngần này chiều cao thân — cản sau (sát đất) không phải nắp. */
    const val FLOOR_FRACTION = 0.25
    /** Tấm hạt giống rộng ≥ ngần này bề ngang thân — loại logo/ốp nhỏ ở giữa đuôi. */
    const val LID_MIN_WIDTH = 0.25
    /** Nới vỏ nắp khi gom tấm đi theo (m) — đèn/viền gắn trên nắp nhô nhẹ khỏi hộp sơn. */
    const val PAD = 0.02
    /** Khoang cốp: vật liệu (`Car3dCatalog` có kiểu cùng tên) · tên bộ phận · lùi vào khỏi vỏ (m). */
    const val CAVITY = "cavity"
    const val CAVITY_PART = "lid_cavity"
    /** Sàn lót thấp hơn mép dưới nắp ngần này (m) — ngang gờ cốp, không chìm xuống chỗ mép trong cản sau chọc lên. */
    const val CAVITY_FLOOR_DROP = 0.03
    const val CAVITY_INSET = 0.02
    /** Vách hông bắt đầu sau mép sau của vỏ nắp ngần này chiều dài · khoang rộng ngần này bề ngang nắp · vách cao tới ngần này chiều cao nắp. */
    const val CAVITY_REAR = 0.45
    /** Dải sàn sau (giữa mép sau nắp và vách hông) rộng ngần này bề ngang nắp. */
    const val CAVITY_SILL_WIDTH = 0.6
    const val CAVITY_WIDTH = 0.95
    const val CAVITY_HEIGHT = 0.45
}
