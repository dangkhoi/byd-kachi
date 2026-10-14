package com.byd.clusternav.launcher

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ═══ 3.01 — mặt SƠN chỉ thấy được QUA KÍNH ⇒ đen như nội thất (thuần `:core`) ════════════════════════════════════════════
 *
 * Owner 14/10: *"trong kính chỗ đó phải cho đen luôn, trên xe không có khung cửa xe màu đỏ như thế"* — nhìn qua kính hông/kính
 * gió thấy một dải màu SƠN ở mặt trong khung cửa / trụ / ray nóc (đỏ với sơn đỏ, xám với sơn xám…).
 *
 * Gốc [ĐO lưới Seal 14/10, bộ chạy thử ngoài repo + chính lớp này trên `car.mesh` thật]: lưới AVM là MỘT lớp vỏ, vật liệu hai mặt;
 * quanh ô kính có tam giác của bộ phận `*_paint` gần như KHÔNG thấy trực tiếp từ ngoài xe mà chỉ thấy qua kính:
 *  • mặt trong khung kính của 4 cửa (`doorlr_paint` 285 · `doorrr` 346 · `doorlf` 136 · `doorrf` 324 tam giác; hai cửa sau có pháp
 *    tuyến nhất quán ⇒ 99 % hướng VÀO khoang; phần lớn y ≥ 1,0 m — đúng dải khung kính);
 *  • `body_paint` 2 476 tam giác ở trụ A/B/C + ray nóc (|z| 0,55–0,89, y 0,43–1,45): phần lớn là mặt sơn NẰM DƯỚI nẹp đen (`*_black`)
 *    — từ ngoài bị nẹp che, từ trong khoang thấy MẶT SAU của nó qua kính (vật liệu hai mặt) ⇒ dải màu sơn.
 * Pháp tuyến của lưới KHÔNG dùng được để phân loại (cửa trước: pháp tuyến đỉnh trái chiều mặt tam giác 55–70 % [ĐO]) ⇒ phân loại
 * bằng TẦM NHÌN, không bằng tên/pháp tuyến:
 *
 * Luật chung: dựng ảnh độ sâu trực giao của cả xe (tư thế ĐÓNG — bỏ bản `*open*`) từ [VIEWS] hướng rải đều trên mặt cầu (Fibonacci —
 * không giả định trục lên của mô hình), một bộ đệm cho mặt ĐỤC, một cho KÍNH. Mỗi điểm ảnh tam giác sơn là mặt đục gần nhất = THẤY
 * THẲNG nếu không có kính trước nó, = QUA KÍNH nếu có. Tam giác có thấy qua kính và số điểm ảnh thấy thẳng < 1/[DIRECT_RATIO] số
 * qua kính ⇒ sang vật liệu [MATERIAL] (đen sâu, KHÔNG nhận màu sơn — bên vẽ chỉ tô `paint`/`open`). Sơn ngoài (thấy thẳng nhiều,
 * kể cả khi cũng thấy qua hai lớp kính) GIỮ nguyên; sơn không bao giờ thấy (khe cửa) giữ nguyên. Tách theo MẶT (dấu diện tích chiếu =
 * mặt trước/sau theo chiều quấn): sơn ngoài thấy thẳng gần như chỉ từ MỘT mặt mà mặt KIA chỉ lộ qua kính (ray nóc dưới kính nóc,
 * mép trụ — [ĐO máy ảo 14/10] vệt đỏ lốm đốm trong mép nóc còn sau lượt đầu) ⇒ tách đôi: sơn MỘT mặt hướng ngoài + đen MỘT mặt
 * hướng vào ([BydAvmMesh.Part.single]/[BydAvmMesh.Part.flip]); từ ngoài nhìn y hệt, từ trong khoang thấy đen. [ĐO] so với bản tham chiếu 200
 * hướng × 900²: 64 × 512² giữ 98,8 % trọng số "thấy qua kính", các tam giác lệch có tổng thấy-thẳng ~1,6 k điểm ảnh (không đáng kể).
 * Bản MỞ của cửa (`X_open`, cùng số chỉ số + cùng thứ tự tam giác — quy ước [Car3dHinge.fitAll]) nhận ĐÚNG các tam giác đó ⇒ cửa mở
 * vẫn đen mặt trong khung. Phần tách giữ NGUYÊN tên (biến thể/neo/bản lề theo tên vẫn đúng); bộ phận nắp cốp giữ tư cách nắp.
 *
 * ## 3.1.5 — mặt NẰM TRÊN tấm kính (owner 14/10 *"kính còn lòi khung nhôm của cửa 1 chút"* · *"kính trước cũng bị vạch trong khung
 * nhôm"*) — [ĐO lưới Seal, bộ chạy thử ngoài repo, ảnh độ sâu 3,7 mm/điểm ảnh, docs/diagnostics/widget-3d-301-2026-10-13.md §14]
 * Trong ô kính cửa còn những mặt ĐỤC sáng màu nằm SÁT tấm kính (trong ±1 cm): gờ sơn khung kính cửa trước-trái ở góc trên-sau
 * (y 1,31–1,38, phía TRƯỚC kính 1–10 mm — dải sơn lòi ra) và mép ốp cửa `texture1` (`car_door.png`, xám) chạy dọc cung trên/mép
 * dưới kính, cắt qua lại mặt kính ⇒ chỗ thì kính thắng độ sâu (tối), chỗ thì ốp thắng (xám sáng, KHÔNG qua lớp kính tối) ⇒ vạch
 * răng cưa. Luật cũ đếm các điểm ảnh đó là "thấy thẳng" (kính không ở TRƯỚC chúng) ⇒ giữ màu. Luật chung: điểm ảnh của mặt đục
 * mà kính gần nhất cách nó ≤ [PLANE] (trước hay sau) = điểm ảnh "TRÊN tấm kính" — đó là gioăng/ray kính của xe thật (đen):
 *  • sơn: chỉ điểm ảnh sơn ở TRƯỚC kính ≤ [PLANE] (gờ lòi ra) — tính như qua kính, VÀ tam giác có điểm ảnh đó ≥ [PLANE_SHARE] ×
 *    số thấy thẳng ⇒ đen. [ĐO §14 #44b] bản đầu đếm cả sơn sát SAU kính ⇒ mép sơn trụ A/ray nóc giáp kính gió/kính nóc (chỉ 17–23 %
 *    điểm ảnh trên-kính ở trước kính; gờ khung kính cửa trước-trái 63 %) cũng đen ⇒ mép trụ A răng cưa ở góc ¾;
 *  • ốp nội thất (lớp `texture`/`interior` của [Car3dMaterials.classOf]) — bình thường CHỈ thấy qua kính, không được đen hết (ghế,
 *    táp-lô) ⇒ chỉ tam giác có điểm ảnh trên-kính ≥ 1/[PLANE_SHARE] số còn lại (thấy thẳng + qua kính) mới sang đen.
 * Đèn ([Car3dLamps] — tên/vật liệu có "lamp") không bao giờ là ứng viên (bóng đèn nằm sát kính đèn là đúng).
 *
 * Chi phí: MỘT lần lúc dựng tệp đệm GLB (luồng nền) — [ĐO] ~1,0–1,4 s JVM máy Mac trên lưới Seal; bộ đệm độ sâu 2 × [RES]² số thực
 * (~2 MB) + 2 bộ đếm Int theo tam giác sơn (~0,4 MB); đọc đỉnh thẳng từ đệm nguồn (không chép lưới ra heap). Lúc chạy: 0 (thêm
 * ≤ 9 nút GLB, không tham số/nhịp mới).
 */
object Car3dCabin {

    class Result(val mesh: BydAvmMesh, val lid: Car3dLid.Result?, val blackened: Map<String, Int>, val backed: Map<String, Int> = emptyMap())

    /** Phân loại một tam giác sơn. */
    enum class Kind { KEEP, CABIN, BACK_OF_FRONT, BACK_OF_BACK }

    fun apply(mesh: BydAvmMesh, lid: Car3dLid.Result? = null): Result {
        val scene = mesh.parts.filter { !it.name.contains(OPEN) && it.indexCount >= 3 }
        val cands = scene.filter { isShell(it) || isTrim(it) }
        if (cands.isEmpty()) return Result(mesh, lid, emptyMap())
        val kinds = classify(mesh, scene, cands)
        if (kinds.values.all { k -> k.all { it == Kind.KEEP } }) return Result(mesh, lid, emptyMap())
        val byName = mesh.parts.groupBy { it.name }
        val split = java.util.IdentityHashMap<BydAvmMesh.Part, Array<Kind>>()
        for ((p, k) in kinds) {
            if (k.all { it == Kind.KEEP }) continue
            split[p] = k
            // Bản mở cùng tên `X_open`, cùng số chỉ số ⇒ cùng thứ tự tam giác ⇒ đúng các tam giác đó.
            byName[p.name + "_" + OPEN]?.singleOrNull { it.indexCount == p.indexCount }?.let { split[it] = k }
        }
        val parts = ArrayList<BydAvmMesh.Part>(); val lidOut = ArrayList<BydAvmMesh.Part>()
        val inLid = lid?.lid
        val cabin = LinkedHashMap<String, Int>(); val backed = LinkedHashMap<String, Int>()
        for (p in mesh.parts) {
            val k = split[p]
            val news = if (k == null) listOf(p) else {
                fun of(kind: Kind) = k.indices.filter { k[it] == kind }.toIntArray()
                val keep = of(Kind.KEEP); val cab = of(Kind.CABIN); val bf = of(Kind.BACK_OF_FRONT); val bb = of(Kind.BACK_OF_BACK)
                if (cab.isNotEmpty()) cabin[p.name] = (cabin[p.name] ?: 0) + cab.size
                if (bf.size + bb.size > 0) backed[p.name] = (backed[p.name] ?: 0) + bf.size + bb.size
                listOfNotNull(
                    keep.sub(p, p.material, single = false, flip = false),
                    cab.sub(p, MATERIAL, single = false, flip = false),
                    // Mặt NGOÀI = mặt trước theo chiều quấn: sơn một mặt giữ nguyên; mặt sau thành đen một mặt (đảo mặt).
                    bf.sub(p, p.material, single = true, flip = false), bf.sub(p, MATERIAL, single = true, flip = true),
                    // Mặt ngoài = mặt SAU theo chiều quấn (pháp tuyến cửa trước lưới AVM lẫn lộn [ĐO]): sơn đảo mặt, đen giữ chiều.
                    bb.sub(p, p.material, single = true, flip = true), bb.sub(p, MATERIAL, single = true, flip = false),
                )
            }
            parts += news
            if (inLid != null && p in inLid) lidOut += news
        }
        val out = BydAvmMesh(mesh.buffers, parts, mesh.skipped)
        val lid2 = lid?.let { l ->
            val set = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<BydAvmMesh.Part, Boolean>()).also { it.addAll(lidOut) }
            Car3dLid.Result(out, set, l.hinge)
        }
        return Result(out, lid2, cabin, backed)
    }

    private fun IntArray.sub(p: BydAvmMesh.Part, material: String, single: Boolean, flip: Boolean) =
        takeIf { it.isNotEmpty() }?.let { Car3dPanels.subset(p, it, material, single, flip) }

    private fun isPaint(p: BydAvmMesh.Part) = p.material.contains(PAINT) || p.name.contains(PAINT)

    /**
     * Mặt VỎ — thấy thẳng từ ngoài là bình thường, chỉ-qua-kính thì phải đen: SƠN, và (3.1.5, owner 14/10 *"các kim loại mà bên
     * trong xe, thì có thể làm đen được ko, nó vẫn lòi thòi ra, cả ở cửa kính, cả trên viền nóc"*) KIM LOẠI/CRÔM (lớp `metal` của
     * [Car3dMaterials.classOf] — `metal`/`chrome`; không gồm `metal_black` đã đen). Cùng một luật tầm nhìn — kim loại thấy thẳng từ
     * ngoài (viền kính ngoài, gương, logo, tay nắm) giữ nguyên. Đèn, kính không bao giờ.
     */
    private fun isShell(p: BydAvmMesh.Part): Boolean {
        if (isPaint(p)) return true
        if (isGlass(p) || p.material.contains(LAMP) || p.name.contains(LAMP)) return false
        return (Car3dMaterials.classOf(p.material) ?: Car3dMaterials.classOf(p.name)) == METAL
    }
    private fun isGlass(p: BydAvmMesh.Part) = p.material.contains(GLASS) || p.name.contains(GLASS)
    /** Ốp nội thất/viền có ảnh (lớp chung `texture`/`interior`) — ứng viên CHỈ của luật "trên tấm kính"; đèn không bao giờ. */
    private fun isTrim(p: BydAvmMesh.Part): Boolean {
        if (isShell(p) || isGlass(p) || p.material.contains(LAMP) || p.name.contains(LAMP)) return false
        val c = Car3dMaterials.classOf(p.material) ?: Car3dMaterials.classOf(p.name)
        return c == TEXTURE || c == INTERIOR
    }

    /**
     * Mỗi tam giác sơn: [Kind.CABIN] — thấy qua kính, gần như không thấy thẳng (mọi mặt) ⇒ đen hai mặt; [Kind.BACK_OF_FRONT] /
     * [Kind.BACK_OF_BACK] — thấy thẳng gần như CHỈ từ một mặt (mặt trước / mặt sau theo chiều quấn) còn mặt KIA chỉ thấy qua kính
     * (sơn ngoài ray nóc/mép trụ — mặt sau của nó lộ qua kính nóc/kính hông thành vệt màu sơn) ⇒ tách đôi: sơn một mặt ra ngoài +
     * đen một mặt vào trong; còn lại [Kind.KEEP].
     */
    internal fun classify(
        mesh: BydAvmMesh, scene: List<BydAvmMesh.Part>, cands: List<BydAvmMesh.Part>, views: Int = VIEWS, res: Int = RES,
    ): Map<BydAvmMesh.Part, Array<Kind>> = hits(mesh, scene, cands, views, res).mapValues { (p, h) ->
        val paint = isShell(p)
        Array(h.directFront.size) { t ->
            val df = h.directFront[t]; val db = h.directBack[t]; val vf = h.viaFront[t]; val vb = h.viaBack[t]; val on = h.onGlass[t]
            fun rare(direct: Int, via: Int) = via > 0 && direct * DIRECT_RATIO < via
            when {
                // Ốp nội thất: chỉ luật "trên tấm kính" (thấy qua kính là bình thường — ghế, táp-lô).
                !paint -> if (on > 0 && on * PLANE_SHARE >= df + db + vf + vb) Kind.CABIN else Kind.KEEP
                rare(df + db, vf + vb + on) -> Kind.CABIN
                on > 0 && on >= PLANE_SHARE * (df + db) -> Kind.CABIN
                df > 0 && rare(db, vb) && db * DIRECT_RATIO < df -> Kind.BACK_OF_FRONT
                db > 0 && rare(df, vf) && df * DIRECT_RATIO < db -> Kind.BACK_OF_BACK
                else -> Kind.KEEP
            }
        }
    }

    /** Số điểm ảnh (cộng mọi hướng) mỗi tam giác sơn, tách theo mặt thấy (trước/sau theo chiều quấn) và thấy thẳng/qua kính. */
    internal class Hits(n: Int) {
        val directFront = IntArray(n); val directBack = IntArray(n); val viaFront = IntArray(n); val viaBack = IntArray(n)
        /**
         * Điểm ảnh mặt này nằm TRÊN tấm kính (cả hai mặt cộng chung): ốp — kính gần nhất cách ≤ [PLANE] trước hay sau; sơn — chỉ khi
         * sơn ở TRƯỚC kính ≤ [PLANE] (gờ khung lòi ra; sơn sát sau kính đã bị lớp kính phủ).
         */
        val onGlass = IntArray(n)
    }

    /** Số điểm ảnh (cộng mọi hướng) mỗi tam giác sơn: (thấy thẳng, thấy qua kính). */
    internal fun hits(
        mesh: BydAvmMesh, scene: List<BydAvmMesh.Part>, cands: List<BydAvmMesh.Part>, views: Int, res: Int,
    ): Map<BydAvmMesh.Part, Hits> {
        val mn = DoubleArray(3) { Double.MAX_VALUE }; val mx = DoubleArray(3) { -Double.MAX_VALUE }
        for (p in scene) { val b = mesh.buffer(p.buffer) ?: continue
            for (i in 0 until p.indexCount) { val v = p.index(i); for (k in 0..2) { val x = b.position(v, k).toDouble(); if (x < mn[k]) mn[k] = x; if (x > mx[k]) mx[k] = x } } }
        if (mn[0] > mx[0]) return emptyMap()
        val c = DoubleArray(3) { (mn[it] + mx[it]) / 2 }
        val radius = sqrt((0..2).sumOf { (mx[it] - mn[it]) * (mx[it] - mn[it]) }) / 2
        val pix = maxOf(2 * radius / res, MIN_PIX)
        val n = ceil(2 * radius / pix).toInt() + 2
        val opaque = FloatArray(n * n); val glass = FloatArray(n * n)
        // Toạ độ tam giác GÓI một lần (Short lượng tử ~0,1 mm theo bán kính cầu bao — Seal 206 k tam giác ⇒ ~3,7 MB): [ĐO máy ảo
        // 14/10] đọc đỉnh qua đệm ánh xạ mỗi hướng nhìn = 6,8 s trên ART; mảng thường nhanh hơn nhiều lần.
        val total = scene.sumOf { it.indexCount / 3 }
        val pk = ShortArray(total * 9); val isGl = BooleanArray(total); val start = java.util.IdentityHashMap<BydAvmMesh.Part, Int>()
        val qs = radius / Short.MAX_VALUE
        var at = 0
        for (p in scene) {
            start[p] = at
            val b = mesh.buffer(p.buffer)
            val gl = isGlass(p)
            for (t in 0 until p.indexCount / 3) {
                isGl[at] = gl
                for (k in 0..2) for (a in 0..2) {
                    val x = if (b == null) 0.0 else (b.position(p.index(t * 3 + k), a) - c[a]) / qs
                    pk[at * 9 + k * 3 + a] = x.toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                at++
            }
        }
        val hits = cands.associateWith { Hits(it.indexCount / 3) }
        val gold = Math.PI * (3 - sqrt(5.0))
        val q = DoubleArray(9)
        for (vi in 0 until views) {
            // Hướng nhìn thứ vi (Fibonacci trên mặt cầu) + hai trục ảnh vuông góc.
            val y = 1 - 2 * (vi + 0.5) / views; val r = sqrt(1 - y * y); val th = gold * vi
            val d = V3(cos(th) * r, y, sin(th) * r)
            val ref = if (kotlin.math.abs(d.y) < 0.9) V3(0.0, 1.0, 0.0) else V3(1.0, 0.0, 0.0)
            val u = ref.cross(d).let { it * (1 / it.length()) }; val w = d.cross(u)
            val su = qs / pix; val off = radius / pix + 1
            val ux = u.x * su; val uy = u.y * su; val uz = u.z * su; val wx = w.x * su; val wy = w.y * su; val wz = w.z * su
            val dx = -d.x * qs; val dy = -d.y * qs; val dz = -d.z * qs
            opaque.fill(Float.MAX_VALUE); glass.fill(Float.MAX_VALUE)
            fun load(t: Int) {
                for (k in 0..2) {
                    val o = t * 9 + k * 3
                    val px = pk[o].toDouble(); val py = pk[o + 1].toDouble(); val pz = pk[o + 2].toDouble()
                    q[k * 3] = px * ux + py * uy + pz * uz + off
                    q[k * 3 + 1] = px * wx + py * wy + pz * wz + off
                    q[k * 3 + 2] = px * dx + py * dy + pz * dz   // nhỏ = gần mắt
                }
            }
            for (t in 0 until total) { val buf = if (isGl[t]) glass else opaque; load(t); raster(q, n) { i, z -> if (z < buf[i]) buf[i] = z } }
            for (p in cands) {
                val s0 = start.getValue(p); val h = hits.getValue(p); val paint = isShell(p)
                for (t in 0 until p.indexCount / 3) {
                    load(s0 + t)
                    // Mặt TRƯỚC (quấn ngược kim đồng hồ) quay về mắt ⇔ diện tích có dấu > 0 trong hệ (u, w), mắt ở +d.
                    val front = (q[3] - q[0]) * (q[7] - q[1]) - (q[6] - q[0]) * (q[4] - q[1]) > 0
                    val s = if (front) h.directFront else h.directBack; val g = if (front) h.viaFront else h.viaBack
                    raster(q, n) { i, z ->
                        if (z <= opaque[i] + EPS) {
                            val gz = glass[i]
                            // Sơn: chỉ phía TRƯỚC kính (gờ khung lòi ra) — sơn sát SAU kính đã bị lớp kính phủ, giữ cách đếm cũ (§14 #44b).
                            val on = gz != Float.MAX_VALUE && (if (paint) z < gz && gz - z <= PLANE else kotlin.math.abs(gz - z) <= PLANE)
                            if (on) h.onGlass[t]++
                            else if (gz < z - EPS) g[t]++ else s[t]++
                        }
                    }
                }
            }
        }
        return hits
    }

    /**
     * Phủ tam giác [q] (x, y theo điểm ảnh · độ sâu) lên lưới [n]×[n]: mọi tâm điểm ảnh trong tam giác (cạnh tính vào), độ sâu
     * nội suy phẳng. Tam giác nhỏ hơn một điểm ảnh (không chứa tâm nào — phần lớn lưới AVM ở [RES]) ⇒ MỘT điểm tại trọng tâm, để
     * tam giác nào cũng có mặt trong ảnh độ sâu (lỗ thủng ⇒ mặt sau lọt ra như thấy thẳng).
     */
    private inline fun raster(q: DoubleArray, n: Int, plot: (Int, Float) -> Unit) {
        val x0 = q[0]; val y0 = q[1]; val x1 = q[3]; val y1 = q[4]; val x2 = q[6]; val y2 = q[7]
        val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        val lx = maxOf(0, ceil(minOf(x0, x1, x2) - 0.5).toInt()); val hx = minOf(n - 1, floor(maxOf(x0, x1, x2) - 0.5).toInt())
        val ly = maxOf(0, ceil(minOf(y0, y1, y2) - 0.5).toInt()); val hy = minOf(n - 1, floor(maxOf(y0, y1, y2) - 0.5).toInt())
        var any = false
        if (kotlin.math.abs(area) > 1e-12 && lx <= hx && ly <= hy) {
            val inv = 1.0 / area
            for (iy in ly..hy) {
                val py = iy + 0.5
                for (ix in lx..hx) {
                    val px = ix + 0.5
                    val a = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) * inv
                    val b = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) * inv
                    val g = 1 - a - b
                    if (a < -IN || b < -IN || g < -IN) continue
                    any = true
                    plot(iy * n + ix, (a * q[2] + b * q[5] + g * q[8]).toFloat())
                }
            }
        }
        if (!any) {
            val cx = ((x0 + x1 + x2) / 3).toInt(); val cy = ((y0 + y1 + y2) / 3).toInt()
            if (cx in 0 until n && cy in 0 until n) plot(cy * n + cx, ((q[2] + q[5] + q[8]) / 3).toFloat())
        }
    }

    /** Vật liệu đen sâu của mặt sơn chỉ thấy qua kính (`Car3dCatalog` có kiểu cùng tên; bên vẽ KHÔNG tô màu sơn lên nó). */
    const val MATERIAL = "cabin"
    /** Số hướng nhìn (Fibonacci trên mặt cầu) — xem đo độ ổn định ở đầu tệp. */
    const val VIEWS = 64
    /** Ảnh độ sâu RES×RES theo đường kính cầu bao xe (Seal ~5,1 m ⇒ ~1 cm/điểm ảnh). */
    const val RES = 512
    private const val MIN_PIX = 1e-4
    /** Tam giác vẫn coi là "qua kính" khi số điểm ảnh thấy THẲNG < 1/[DIRECT_RATIO] số thấy qua kính (răng cưa ở mép kính). */
    const val DIRECT_RATIO = 10
    /** Dung sai độ sâu (m): mặt tự so với chính nó (cùng tâm điểm ảnh, cùng mặt phẳng) bằng nhau; nẹp chồng lên sơn cách ≥ vài mm. */
    private const val EPS = 0.002f
    /**
     * Mặt đục cách kính gần nhất ≤ [PLANE] (m) = nằm TRÊN tấm kính (gioăng/ray/gờ khung). [ĐO lưới Seal §14]: gờ sơn lòi ở góc kính
     * trước-trái nằm TRƯỚC kính 1–10 mm; sơn ngoài viền cửa ≥ 2 cm trước kính; ốp cửa cắt qua mặt kính trong ±4 mm.
     */
    const val PLANE = 0.010f
    /** Tỉ lệ "trên tấm kính" so với phần còn lại để một tam giác sang đen (xem đầu tệp). [ĐO §14] 2 ⇒ còn 76/495 điểm ảnh sáng. */
    const val PLANE_SHARE = 2
    private const val IN = 1e-9
    private const val OPEN = "open"
    private const val PAINT = "paint"
    private const val GLASS = "glass"
    private const val LAMP = "lamp"
    private const val TEXTURE = "texture"
    private const val INTERIOR = "interior"
    private const val METAL = "metal"
}
