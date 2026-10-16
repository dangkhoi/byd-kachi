package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 3.01 — khoá lỗi owner 14/10 *"trong kính chỗ đó phải cho đen luôn, trên xe không có khung cửa xe màu đỏ như thế"*: mặt SƠN chỉ
 * thấy được QUA KÍNH (mặt trong khung kính) sang vật liệu đen [Car3dCabin.MATERIAL]; sơn ngoài giữ nguyên dù cũng thấy được qua
 * kính; cửa MỞ nhận đúng các tam giác đó. Lưới TỔNG HỢP (repo không chứa byte BYD): hộp thân 2 m có ô kính ở sườn +Z.
 */
class Car3dCabinTest {

    /** Số băm đầu ra [Car3dCabin.hits] trên lưới mẫu ứng với [Car3dCabin.RULES] = c1 (xem `day bao RULES`). */
    private val RULES_DIGEST = -5096490549957274320L

    private fun block(kind: Int, name: String, mat: String, count: Int, body: ByteArray): ByteArray {
        val h = ByteBuffer.allocate(BydAvmMesh.HEADER).order(ByteOrder.LITTLE_ENDIAN)
        h.putInt(BydAvmMesh.HEADER + body.size).putInt(kind).put(name.toByteArray().copyOf(32)).put(mat.toByteArray().copyOf(32))
        h.putFloat(0f).putFloat(0f).putFloat(0f).putInt(count)
        return h.array() + body
    }

    private fun le(n: Int, f: ByteBuffer.() -> Unit) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).apply(f).array()

    private fun buf(name: String, pts: List<FloatArray>): ByteArray {
        val n = pts.size
        return block(1, name, name, n, le(n * 36) {
            pts.forEach { p -> p.forEach { putFloat(it) } }
            repeat(n * 3) { putFloat(0f) }
            repeat(n) { putFloat(0f); putFloat(1f); putFloat(0f) }
        })
    }

    private fun part(name: String, mat: String, idx: List<Int>) = block(2, name, mat, idx.size, le(idx.size * 4) { idx.forEach { putInt(it) } })

    /** Tứ giác trục-thẳng: [k] = trục cố định (0 x · 1 y · 2 z) tại [c]; hai trục còn lại trải [a0..a1] × [b0..b1]. */
    private fun quad(k: Int, c: Float, a0: Float, a1: Float, b0: Float, b1: Float): List<FloatArray> {
        val (ia, ib) = listOf(0, 1, 2).filter { it != k }
        return listOf(a0 to b0, a1 to b0, a1 to b1, a0 to b1).map { (a, b) -> FloatArray(3).also { it[k] = c; it[ia] = a; it[ib] = b } }
    }

    private fun idx(quads: Int, from: Int = 0) = (0 until quads).flatMap { q -> listOf(0, 1, 2, 0, 2, 3).map { from + q * 4 + it } }

    /**
     * Thân = hộp [-1,1]³ sơn, sườn +Z thủng ô kính x −0,4..0,4 · y −0,2..0,6 (4 tấm quanh ô). `doorlf_paint`: tấm 0 = mặt ngoài
     * cửa dưới ô kính (z 1,001) · tấm 1 = MẶT TRONG khung trên ô kính (z 0,95, y 0,5..0,6 — sau kính). `doorlf_paint_open` = cùng hai
     * tấm (đã "mở" lệch +0,5 z). Kính phủ ô tại z 1,0.
     */
    private fun car(): BydAvmMesh {
        val body = listOf(
            quad(2, -1f, -1f, 1f, -1f, 1f), quad(0, 1f, -1f, 1f, -1f, 1f), quad(0, -1f, -1f, 1f, -1f, 1f),
            quad(1, 1f, -1f, 1f, -1f, 1f), quad(1, -1f, -1f, 1f, -1f, 1f),
            // sườn +Z quanh ô kính: dưới · trên · trái · phải
            quad(2, 1f, -1f, 1f, -1f, -0.2f), quad(2, 1f, -1f, 1f, 0.6f, 1f), quad(2, 1f, -1f, -0.4f, -0.2f, 0.6f), quad(2, 1f, 0.4f, 1f, -0.2f, 0.6f),
        ).flatten()
        val door = quad(2, 1.001f, -0.4f, 0.4f, -0.8f, -0.4f) + quad(2, 0.95f, -0.4f, 0.4f, 0.5f, 0.6f)
        val doorOpen = door.map { floatArrayOf(it[0], it[1], it[2] + 0.5f) }
        val glass = quad(2, 1.0f, -0.4f, 0.4f, -0.2f, 0.6f)
        val pts = body + door + doorOpen
        val bytes = buf("paint", pts) + buf("glass", glass) +
            part("body_paint", "paint", idx(9)) + part("doorlf_paint", "paint", idx(2, 36)) +
            part("doorlf_paint_open", "paint", idx(2, 44)) + part("doorlf_glass", "glass", idx(1))
        return BydAvmMesh.parse(bytes)
    }

    /** Số tam giác theo (vật liệu, một mặt?) của các phần cùng tên [name]. */
    private fun mat(m: BydAvmMesh, name: String) = m.parts.filter { it.name == name }
        .groupBy { it.material + if (it.single) "@1s" else "" }.mapValues { (_, ps) -> ps.sumOf { it.indexCount / 3 } }

    @Test
    fun `mat trong khung kinh chi thay qua kinh thanh den, son ngoai giu nguyen, cua mo cung tam giac`() {
        val r = Car3dCabin.apply(car())
        // Mặt trong khung (2 tam giác) ⇒ đen; mặt ngoài cửa (2 tam giác) giữ sơn. Đảo luật (không tách) ⇒ bài này đỏ.
        assertEquals(mapOf("paint" to 2, Car3dCabin.MATERIAL to 2), mat(r.mesh, "doorlf_paint"))
        assertEquals(mapOf("paint" to 2, Car3dCabin.MATERIAL to 2), mat(r.mesh, "doorlf_paint_open"), "cửa mở: cùng mặt trong khung đen")
        // Tam giác đen của bản mở = ĐÚNG tấm trong khung (z 0,95 + 0,5), không phải tấm ngoài.
        val open = r.mesh.parts.single { it.name == "doorlf_paint_open" && it.material == Car3dCabin.MATERIAL }
        val b = r.mesh.buffer(open.buffer)!!
        assertTrue((0 until open.indexCount).all { kotlin.math.abs(b.position(open.index(it), 2) - 1.45f) < 1e-4 })
        // Thân: vách thấy THẲNG từ ngoài (mặt ngoài) và chỉ thấy MẶT SAU qua ô kính ⇒ tách đôi: sơn MỘT mặt hướng ngoài (vẫn đủ 18
        // tam giác sơn, màu sơn áp như cũ) + đen một mặt hướng vào (mặt sau sơn hai mặt từng lộ qua kính thành vệt màu sơn).
        val body = mat(r.mesh, "body_paint")
        assertEquals(18, (body["paint"] ?: 0) + (body["paint@1s"] ?: 0), "đủ 18 tam giác sơn ngoài: $body")
        assertEquals(body["paint@1s"], body["${Car3dCabin.MATERIAL}@1s"])
        assertEquals(mapOf("doorlf_paint" to 2, "doorlf_paint_open" to 2), r.blackened)
        // Vách XA (z −1) — thấy mặt sau qua ô kính — chắc chắn được tách (đảo luật ⇒ đỏ).
        val farBlack = r.mesh.parts.filter { it.name == "body_paint" && it.material == Car3dCabin.MATERIAL }.sumOf { p ->
            val b = r.mesh.buffer(p.buffer)!!
            (0 until p.indexCount / 3).count { t -> (0..2).all { c -> b.position(p.index(t * 3 + c), 2) == -1f } }
        }
        assertEquals(2, farBlack)
        // Cặp tách: mỗi phần sơn một mặt có phần đen một mặt cùng số tam giác, NGƯỢC chiều mặt.
        val bp = r.mesh.parts.filter { it.name == "body_paint" }
        for (paint in bp.filter { it.material == "paint" && it.single }) {
            assertTrue(bp.any { it.material == Car3dCabin.MATERIAL && it.indexCount == paint.indexCount && it.flip != paint.flip })
        }
        // Kính, tên bộ phận giữ nguyên (biến thể/neo theo tên).
        assertEquals(mapOf("glass" to 2), mat(r.mesh, "doorlf_glass"))
    }

    /**
     * 3.1.5 — khoá lỗi owner 14/10 *"kính còn lòi khung nhôm của cửa 1 chút"* / *"kính trước cũng bị vạch trong khung nhôm"*
     * ([ĐO lưới Seal §14]: gờ sơn khung kính nằm TRƯỚC kính 1–10 mm, ốp `texture1` cắt qua mặt kính ±4 mm — luật cũ coi là "thấy
     * thẳng" ⇒ giữ màu). Hộp thân như [car]; trong ô kính (z 1,0): gờ SƠN ở z 1,005 (trước kính 5 mm, chỉ thấy thẳng) · dải ốp
     * `texture1` ở z 0,998 (sau kính 2 mm) · tấm ốp `texture1` sâu trong khoang z 0,6 (thấy qua kính — như ghế/táp-lô) · bóng đèn
     * sát kính z 0,999. Đảo luật (bỏ [Car3dCabin.PLANE]) ⇒ gờ sơn + dải ốp giữ màu ⇒ đỏ.
     */
    @Test
    fun `mat duc nam tren tam kinh thanh den - go son, dai op - op sau trong khoang va bong den giu nguyen`() {
        val body = listOf(
            quad(2, -1f, -1f, 1f, -1f, 1f), quad(0, 1f, -1f, 1f, -1f, 1f), quad(0, -1f, -1f, 1f, -1f, 1f),
            quad(1, 1f, -1f, 1f, -1f, 1f), quad(1, -1f, -1f, 1f, -1f, 1f),
            quad(2, 1f, -1f, 1f, -1f, -0.2f), quad(2, 1f, -1f, 1f, 0.6f, 1f), quad(2, 1f, -1f, -0.4f, -0.2f, 0.6f), quad(2, 1f, 0.4f, 1f, -0.2f, 0.6f),
        ).flatten()
        val flange = quad(2, 1.005f, -0.4f, 0.4f, 0.5f, 0.55f)
        // #44b: sơn sát SAU kính 1 mm (mép sơn giáp kính gió/kính nóc) — lớp kính đã phủ ⇒ giữ cách đếm cũ, KHÔNG đen.
        val flush = quad(2, 0.999f, -0.4f, 0.4f, -0.1f, 0f)
        val trim = quad(2, 0.998f, -0.4f, 0.4f, -0.2f, -0.15f) + quad(2, 0.6f, -0.3f, 0.3f, -0.2f, 0.6f)
        val lamp = quad(2, 0.999f, -0.1f, 0.1f, 0.2f, 0.3f)
        val glass = quad(2, 1.0f, -0.4f, 0.4f, -0.2f, 0.6f)
        val bytes = buf("paint", body + flange + flush) + buf("texture1", trim) + buf("lamp", lamp) + buf("glass", glass) +
            part("body_paint", "paint", idx(9)) + part("doorlf_paint", "paint", idx(1, 36)) + part("roof_paint", "paint", idx(1, 40)) +
            part("doorlf_texture1", "texture1", idx(2)) + part("body_lamp_3", "lamp", idx(1)) + part("doorlf_glass", "glass", idx(1))
        val r = Car3dCabin.apply(BydAvmMesh.parse(bytes))
        assertEquals(mapOf(Car3dCabin.MATERIAL to 2), mat(r.mesh, "doorlf_paint"), "gờ sơn trước kính 5 mm ⇒ đen")
        // Dải ốp sát kính (2 tam giác đầu) ⇒ đen; tấm ốp sâu trong khoang (2 tam giác sau, chỉ thấy qua kính) giữ ảnh ốp.
        assertEquals(mapOf("texture1" to 2, Car3dCabin.MATERIAL to 2), mat(r.mesh, "doorlf_texture1"))
        val keptTrim = r.mesh.parts.single { it.name == "doorlf_texture1" && it.material == "texture1" }
        val kb = r.mesh.buffer(keptTrim.buffer)!!
        assertTrue((0 until keptTrim.indexCount).all { kotlin.math.abs(kb.position(keptTrim.index(it), 2) - 0.6f) < 1e-4 }, "ốp giữ lại = tấm sâu")
        assertEquals(mapOf("lamp" to 2), mat(r.mesh, "body_lamp_3"), "bóng đèn sát kính đèn không bao giờ thành đen")
        assertEquals(mapOf("paint" to 2), mat(r.mesh, "roof_paint"), "sơn sát SAU kính giữ màu (mép trụ A không răng cưa)")
        assertEquals(mapOf("glass" to 2), mat(r.mesh, "doorlf_glass"))
        // Sơn ngoài thân (thấy thẳng, cách kính) không tam giác nào thành đen hai mặt.
        assertTrue(r.mesh.parts.none { it.name == "body_paint" && it.material == Car3dCabin.MATERIAL && !it.single })
    }

    @Test
    fun `khong co kinh thi khong gi thanh den`() {
        val m = car()
        val noGlass = BydAvmMesh(m.buffers, m.parts.filter { !it.name.contains("glass") }, 0)
        val r = Car3dCabin.apply(noGlass)
        assertTrue(r.blackened.isEmpty())
        assertTrue(r.mesh.parts.none { it.material == Car3dCabin.MATERIAL })
    }

    @Test
    fun `GLB - phan den mang vat lieu cabin, ben ve khong to mau son len no`() {
        val r = Car3dCabin.apply(car())
        val styles = Car3dCatalog.BYD_AVM.materials
        assertTrue(Car3dCabin.MATERIAL in styles, "catalog phải có kiểu ${Car3dCabin.MATERIAL}")
        assertTrue(styles.getValue(Car3dCabin.MATERIAL).rgba.take(3).all { it < 0.01 }, "đen sâu")
        val json = String(Car3dGlb.bytes(Car3dGlb.write(r.mesh, styles)), Charsets.ISO_8859_1)
        assertTrue(json.contains(""""name":"doorlf_paint","mesh"""") && json.contains(""""mat":"${Car3dCabin.MATERIAL}""""))
        // Bản một mặt: vật liệu glTF riêng `…@1s` không hai mặt; `extras.mat` vẫn `paint` ⇒ bên vẽ vẫn tô màu sơn lên nó.
        assertTrue(json.contains(""""name":"paint${Car3dGlb.SINGLE}","pbrMetallicRoughness"""") && json.contains(""""doubleSided":false"""))
        assertTrue(json.contains(""""mat":"paint""""))
    }

    /**
     * 3.1.5 #45 — khoá lỗi owner 14/10 *"kính trước cũng bị vạch trong khung nhôm"* (vạch xám trung tính ĐÔI, răng cưa, cung trên
     * kính cửa trước-phải). [ĐO máy ảo, ẩn từng bộ phận cùng góc yaw −90]: 341/358 điểm ảnh vạch mất khi ẩn RIÊNG `doorrf_paint`
     * phần [Car3dCabin.MATERIAL]; ẩn kính/gioăng/nẹp/ốp: 0. Gốc = phản xạ gương điện môi (F0 0,04, Fresnel → 1 ở góc sượt) của mặt
     * đen sâu dưới nắng 55 k lux — màu nền ~0 không tắt được nó. Đen-chỉ-qua-kính phải KHÔNG chiếu sáng (`KHR_materials_unlit`).
     * Đảo (bỏ `unlit` ở catalog hoặc bỏ phần ghi phần mở rộng ở [Car3dGlb]) ⇒ đỏ.
     */
    @Test
    fun `GLB - mat den chi thay qua kinh khong chieu sang - khong vach xam phan xa guong`() {
        val styles = Car3dCatalog.BYD_AVM.materials
        assertTrue(styles.getValue(Car3dCabin.MATERIAL).unlit, "kiểu ${Car3dCabin.MATERIAL} phải không chiếu sáng")
        val json = String(Car3dGlb.bytes(Car3dGlb.write(Car3dCabin.apply(car()).mesh, styles)), Charsets.ISO_8859_1)
        fun mat(name: String): String {
            val i = json.indexOf("""{"name":"$name","pbrMetallicRoughness"""")
            assertTrue(i >= 0, "thiếu vật liệu $name")
            val ends = listOf(json.indexOf("{\"name\":", i + 1), json.indexOf("\"accessors\"", i)).filter { it > 0 }
            return json.substring(i, ends.minOrNull() ?: json.length)
        }
        assertTrue(mat(Car3dCabin.MATERIAL).contains(""""extensions":{"${Car3dGlb.UNLIT}":{}}"""), mat(Car3dCabin.MATERIAL))
        assertTrue(mat("${Car3dCabin.MATERIAL}${Car3dGlb.SINGLE}").contains(Car3dGlb.UNLIT), "bản một mặt cũng không chiếu sáng")
        assertTrue(json.contains(""""extensionsUsed":["${Car3dGlb.UNLIT}"]"""), "khai extensionsUsed")
        // Sơn/kính vẫn PBR (có phản xạ) — chỉ mặt đen-qua-kính bỏ chiếu sáng.
        assertTrue(!mat("paint").contains(Car3dGlb.UNLIT) && !mat("glass").contains(Car3dGlb.UNLIT))
    }

    @Test
    fun `GLB - dao mat thi dao chieu quan va dao phap tuyen`() {
        val m = car()
        val p = m.parts.single { it.name == "doorlf_glass" }
        val flipped = Car3dPanels.subset(p, intArrayOf(0), single = true, flip = true)
        val plain = Car3dPanels.subset(p, intArrayOf(0), single = true, flip = false)
        fun glb(x: BydAvmMesh.Part) = Car3dGlb.write(BydAvmMesh(m.buffers, listOf(x), 0), emptyMap())
        fun ints(b: ByteBuffer, from: Int, n: Int) = IntArray(n) { b.getInt(from + it * 4) }
        // Hai GLB cùng bố cục; khác đúng ở chỉ số (a, b, c) → (a, c, b) và dấu pháp tuyến.
        val a = glb(plain); val b = glb(flipped)
        assertEquals(a.capacity(), b.capacity())
        val bin = 12 + 8 + a.getInt(12) + 8
        val nPts = 3; val idxAt = bin + nPts * 12 * 2 + nPts * 8
        val ia = ints(a, idxAt, 3); val ib = ints(b, idxAt, 3)
        assertArrayEquals(intArrayOf(ia[0], ia[2], ia[1]), ib)
        assertEquals(-a.getFloat(bin + nPts * 12 + 4), b.getFloat(bin + nPts * 12 + 4))
    }

    /**
     * 3.1.5 — kim loại (owner 14/10 *"các kim loại mà bên trong xe … làm đen … cả ở cửa kính, cả trên viền nóc"*) trên lưới THẬT của
     * Seal (`eke`, cùng đường nạp của app: bỏ gộp → nắp cốp → [Car3dCabin.apply]). Chạy khi có `KACHI_AVM_ROOT` (repo không chứa byte BYD).
     */
    @Test
    fun `luoi that Seal - kim loai chi thay qua kinh thanh den, kim loai ngoai giu - chi chay khi co KACHI_AVM_ROOT`() {
        val root = System.getenv("KACHI_AVM_ROOT")?.let(::File)
        assumeTrue(root != null && root.isDirectory, "không có KACHI_AVM_ROOT — bỏ qua (repo không chứa byte BYD)")
        val d = File(root, "eke/CarModel/eke")
        assumeTrue(File(d, "car.mesh").isFile, "không có eke")
        val raw = BydAvmMesh.parse(File(d, "car.mesh").readBytes()) + BydAvmMesh.parse(File(d, "wheel.mesh").readBytes())
        val m0 = Car3dDupes.without(raw, Car3dDupes.aggregates(raw))
        val m = Car3dLid.of(m0)?.mesh ?: m0
        val r = Car3dCabin.apply(m)
        val metal = r.mesh.parts.filter { !it.name.contains("open") && (Car3dMaterials.classOf(it.name) == "metal") }
        val byPart = metal.groupBy { it.name }.mapValues { (_, ps) -> ps.groupBy { it.material + if (it.single) "@1s" else "" }.mapValues { (_, x) -> x.sumOf { it.indexCount / 3 } } }
        // [ĐO 14/10] Seal: kim loại gần như KHÔNG lộ qua kính (body_metal 1/14 374 tam giác, kim loại cửa trước 71/71 thấy thẳng) ⇒
        // luật kim loại không được đen hoá kim loại ngoài: ≤ 1 % tam giác kim loại thành đen, kim loại cửa giữ nguyên.
        val black = metal.filter { it.material == Car3dCabin.MATERIAL }.sumOf { it.indexCount / 3 }
        val all = metal.sumOf { it.indexCount / 3 }
        assertTrue(black * 100 <= all, "kim loại ngoài bị đen: $byPart")
        assertTrue(byPart.filterKeys { it.startsWith("door") }.values.all { Car3dCabin.MATERIAL !in it }, "$byPart")
    }


    /**
     * 3.1.10 `CAR3D-CABIN-BUILD-COST` — kết quả phân loại nhớ theo khoá suy từ ĐẦU VÀO ([Car3dCabin.key]): cùng lưới ⇒ cùng khoá,
     * lần sau đọc bản nhớ (không phủ lại); lệch một đỉnh ứng viên / đổi lớp vật liệu của bộ phận ⇒ khoá khác; bản nhớ hỏng hoặc lệch
     * ứng viên ⇒ tính lại, không dùng. Đảo (bỏ băm hình học, bỏ cờ lớp, hoặc dùng byte không kiểm) ⇒ đỏ.
     */
    @Test
    fun `nho phan loai theo dau vao - cung luoi doc ban nho, luoi doi thi tinh lai`() {
        class Rec : Car3dCabin.Memo {
            val store = HashMap<String, ByteArray>(); var gets = 0; var puts = 0
            override fun get(key: String): ByteArray? { gets++; return store[key] }
            override fun put(key: String, bytes: ByteArray) { puts++; store[key] = bytes }
        }
        val memo = Rec()
        val plain = Car3dCabin.apply(car())
        val first = Car3dCabin.apply(car(), memo = memo)
        assertEquals(1 to 1, memo.gets to memo.puts)
        val second = Car3dCabin.apply(car(), memo = memo)
        assertEquals(2 to 1, memo.gets to memo.puts, "cùng lưới ⇒ đọc bản nhớ, không ghi lại")
        for (r in listOf(first, second)) assertEquals(mat(plain.mesh, "doorlf_paint") to plain.blackened, mat(r.mesh, "doorlf_paint") to r.blackened)
        // Bản nhớ ĐƯỢC dùng thật: đổi byte hợp lệ (mọi tam giác KEEP) ⇒ kết quả theo bản nhớ.
        val key = memo.store.keys.single()
        val m = car(); val scene = m.parts.filter { !it.name.contains("open") }
        val cands = scene.filter { it.material == "paint" }
        memo.store[key] = Car3dCabin.encode(cands.associateWith { p -> Array(p.indexCount / 3) { Car3dCabin.Kind.KEEP } }, cands)
        assertTrue(Car3dCabin.apply(m, memo = memo).blackened.isEmpty())
        // Byte hỏng / lệch số tam giác ⇒ không dùng, tính lại như không có bản nhớ.
        memo.store[key] = byteArrayOf(1, 2, 3)
        assertEquals(plain.blackened, Car3dCabin.apply(car(), memo = memo).blackened)
        memo.store[key] = Car3dCabin.encode(cands.associateWith { p -> Array(p.indexCount / 3 + 1) { Car3dCabin.Kind.KEEP } }, cands)
        assertEquals(plain.blackened, Car3dCabin.apply(car(), memo = memo).blackened)
        // Khoá: cùng đầu vào ⇒ bằng nhau; dời tấm kính 1 mm ⇒ khác; tập ứng viên đổi (lớp vật liệu của bộ phận) ⇒ khác.
        fun keyOf(mesh: BydAvmMesh, shell: (BydAvmMesh.Part) -> Boolean = { it.material == "paint" }): String {
            val sc = mesh.parts.filter { !it.name.contains("open") }
            return Car3dCabin.key(Car3dCabin.pack(mesh, sc)!!, sc.filter(shell), Car3dCabin.VIEWS, Car3dCabin.RES)
        }
        assertEquals(keyOf(car()), keyOf(car()))
        val shifted = car().let { c -> BydAvmMesh(c.buffers.map { b -> if (b.name == "glass") BydAvmMesh.Buffer(b.name, b.pivot, b.count, b.src, b.posAt, b.attrAt, b.nrmAt, V3(0.0, 0.0, 0.001)) else b }, c.parts, 0) }
        assertTrue(keyOf(car()) != keyOf(shifted), "hình học đổi ⇒ khoá đổi")
        assertTrue(keyOf(car()) != keyOf(car()) { it.name == "doorlf_paint" }, "tập ứng viên đổi ⇒ khoá đổi")
        assertTrue(keyOf(car()) != keyOf(car()) { it.name == "doorlf_paint" || it.name == "doorlf_glass" }, "cùng SỐ ứng viên, khác bộ phận ⇒ khoá đổi")
    }

    /**
     * 3.1.10 — dây báo [Car3dCabin.RULES]: số điểm ảnh từng tam giác ứng viên trên lưới mẫu [car] được băm và khoá cùng [RULES].
     * Đổi MÃ luật (đếm, ngưỡng, hướng nhìn) mà quên đổi [RULES] ⇒ bản nhớ cũ trên xe bị dùng lại sai ⇒ bài này đỏ: đổi [RULES] VÀ
     * cập nhật số băm. [ĐO] đỏ khi bỏ nhánh hướng đối (`−d`) của [Car3dCabin.hits].
     */
    @Test
    fun `day bao RULES - dau ra phan loai tren luoi mau khop phien ban luat`() {
        val m = car(); val scene = m.parts.filter { !it.name.contains("open") && it.indexCount >= 3 }
        val cands = scene.filter { it.material == "paint" }
        val h = Car3dCabin.hits(Car3dCabin.pack(m, scene)!!, cands, Car3dCabin.VIEWS, Car3dCabin.RES)
        var d = 17L
        for (p in cands) { val x = h.getValue(p)
            for (a in listOf(x.directFront, x.directBack, x.viaFront, x.viaBack, x.onGlass)) d = d * 31 + a.contentHashCode() }
        assertEquals("c1:$RULES_DIGEST", "${Car3dCabin.RULES}:$d", "đổi luật ⇒ đổi Car3dCabin.RULES + số băm ở đây")
    }

    /**
     * 3.1.5 — kim loại/crôm (lớp `metal` của [Car3dMaterials.classOf]) cùng luật tầm nhìn với sơn: nẹp kim loại mặt trong khung kính
     * (z 0,95, chỉ thấy qua kính) ⇒ đen; nẹp kim loại ngoài cửa dưới ô kính (z 1,001, thấy thẳng) giữ `metal`; `metal_black` không là
     * ứng viên. Đảo (bỏ kim loại khỏi [Car3dCabin] — chỉ sơn) ⇒ đỏ.
     */
    @Test
    fun `kim loai chi thay qua kinh thanh den, kim loai ngoai giu nguyen`() {
        val body = listOf(
            quad(2, -1f, -1f, 1f, -1f, 1f), quad(0, 1f, -1f, 1f, -1f, 1f), quad(0, -1f, -1f, 1f, -1f, 1f),
            quad(1, 1f, -1f, 1f, -1f, 1f), quad(1, -1f, -1f, 1f, -1f, 1f),
            quad(2, 1f, -1f, 1f, -1f, -0.2f), quad(2, 1f, -1f, 1f, 0.6f, 1f), quad(2, 1f, -1f, -0.4f, -0.2f, 0.6f), quad(2, 1f, 0.4f, 1f, -0.2f, 0.6f),
        ).flatten()
        val metal = quad(2, 1.001f, -0.4f, 0.4f, -0.8f, -0.4f) + quad(2, 0.95f, -0.4f, 0.4f, 0.5f, 0.6f)
        val dark = quad(2, 0.95f, -0.4f, 0.4f, 0.4f, 0.45f)
        val glass = quad(2, 1.0f, -0.4f, 0.4f, -0.2f, 0.6f)
        val bytes = buf("paint", body) + buf("metal", metal) + buf("metal_black", dark) + buf("glass", glass) +
            part("body_paint", "paint", idx(9)) + part("doorlf_metal", "metal", idx(2)) + part("doorlf_metal_black", "metal_black", idx(1)) +
            part("doorlf_glass", "glass", idx(1))
        val r = Car3dCabin.apply(BydAvmMesh.parse(bytes))
        assertEquals(mapOf("metal" to 2, Car3dCabin.MATERIAL to 2), mat(r.mesh, "doorlf_metal"))
        val kept = r.mesh.parts.single { it.name == "doorlf_metal" && it.material == "metal" }
        val kb = r.mesh.buffer(kept.buffer)!!
        assertTrue((0 until kept.indexCount).all { kotlin.math.abs(kb.position(kept.index(it), 2) - 1.001f) < 1e-4 }, "kim loại giữ lại = nẹp NGOÀI")
        assertEquals(mapOf("metal_black" to 2), mat(r.mesh, "doorlf_metal_black"), "metal_black không là ứng viên")
    }

}
