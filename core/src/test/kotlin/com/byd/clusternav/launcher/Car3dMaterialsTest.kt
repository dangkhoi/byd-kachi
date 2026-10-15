package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 3.1.5 — vật liệu lưới AVM cho MỌI đời xe ([Car3dMaterials]). Khoá lỗi SL6 (owner 14/10, ảnh xe anh em chạy 3.1.4): dải đèn
 * đuôi trắng đặc (`glasstail` không có trong bảng ⇒ xám 0,6), ghế/khoang trắng qua kính (`interior` nền 1,0 × ảnh
 * `car_texture1.png` VẮNG ở SL6, `attr` còn không phải UV), mép cốp trắng (`trunk_glasstail`).
 *
 * Bộ dữ liệu: `src/test/resources/car3d/avm-part-names.txt` — CHỈ tên bộ phận + vật liệu khai + tên ảnh của 36 lưới thật (không
 * byte BYD). Ca đo hình học/ảnh thật chạy khi có `KACHI_AVM_ROOT` (ngoài repo).
 */
class Car3dMaterialsTest {

    private class Model(val rel: String, val pngs: Set<String>, val parts: List<Pair<String, String>>) {
        val group get() = rel.substringBefore('/')
    }

    private val models: List<Model> by lazy {
        val text = javaClass.getResourceAsStream("/car3d/avm-part-names.txt")!!.bufferedReader().readText()
        val out = ArrayList<Model>()
        var rel = ""; var pngs = emptySet<String>(); var parts = ArrayList<Pair<String, String>>()
        fun flush() { if (rel.isNotEmpty()) out += Model(rel, pngs, parts) }
        for (line in text.lines()) when {
            line.isBlank() || line.startsWith("#") -> {}
            line.startsWith("> ") -> { flush(); rel = line.removePrefix("> ").trim(); pngs = emptySet(); parts = ArrayList() }
            line.startsWith("@") -> pngs = line.removePrefix("@").trim().split(' ').filter { it.isNotEmpty() }.toSet()
            else -> line.trim().split(' ').let { parts += it[0] to it.getOrElse(1) { "" } }   // vài bộ phận khai vật liệu rỗng [ĐO]
        }
        flush(); out
    }

    private val lampKeys = setOf(Car3dLamps.WHITE, Car3dLamps.WHITE_ON, Car3dLamps.REAR, Car3dLamps.REAR_ON, Car3dLamps.TURN_ON)

    /** Sáng gần trắng khi KHÔNG có ảnh nhân vào (sơn trừ ra — bên vẽ tô màu người dùng chọn; đèn SÁNG được phép sáng). */
    private fun whiteish(key: String, st: Car3dMaterialStyle) =
        key !in PAINTED && st.emissive?.any { it > 0.0 } != true && st.rgba.take(3).max() > 0.35

    /** Kiểu thực khi bộ phận dùng [key] ở đời có ảnh [pngs] (chưa xét UV/độ sáng ảnh — đó là ca đo thật). */
    private fun effective(key: String, styles: Map<String, Car3dMaterialStyle>, pngs: Set<String>): Pair<Car3dMaterialStyle, Boolean> {
        val st = Car3dMaterials.style(key, styles)
        val tex = st.texture
        return if (tex != null && tex !in pngs) Car3dMaterials.plain(st) to false else st to (tex != null)
    }

    @Test
    fun `36 luoi that - moi bo phan co kieu, khong bo phan nao trang khi khong co anh`() {
        assertEquals(36, models.size)
        val bad = ArrayList<String>()
        for (m in models) {
            val cat = Car3dCatalog.forAvm(m.group)
            for ((name, mat) in m.parts) {
                val key = Car3dMaterials.key(name, mat, cat.materials, cat.partStyles)
                assertTrue(key in cat.materials, "${m.rel} $name → $key không có trong bảng")
                val (st, textured) = effective(key, cat.materials, m.pngs)
                if (!textured && whiteish(key, st)) bad += "${m.rel} $name($mat) → $key ${st.rgba}"
            }
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test
    fun `den la vat lieu den, kinh den la kinh - moi doi`() {
        for (m in models) {
            val cat = Car3dCatalog.forAvm(m.group)
            for ((name, mat) in m.parts) {
                val key = Car3dMaterials.key(name, mat, cat.materials, cat.partStyles)
                when {
                    mat == "glasstail" -> assertEquals(Car3dMaterials.LENS_REAR, key, "${m.rel} $name")
                    mat == "glasslamp" -> assertEquals(Car3dMaterials.LENS_CLEAR, key, "${m.rel} $name")
                    mat == "lamp" -> assertTrue(key in lampKeys, "${m.rel} $name → $key")
                }
            }
        }
        val lens = Car3dCatalog.BYD_AVM.materials.getValue(Car3dMaterials.LENS_REAR)
        assertTrue(lens.blend && lens.rgba[0] > 5 * lens.rgba[1] && lens.rgba[0] < 0.35, "kính đèn đuôi = đỏ sẫm trong mờ")
    }

    @Test
    fun `SL6 sa3hkl - dai den duoi, mep cop, khoang deu toi`() {
        val m = models.single { it.rel == "sa3hkl/CarModel/sa3hkl" }
        val cat = Car3dCatalog.forAvm(m.group)
        fun eff(name: String) = m.parts.single { it.first == name }.let { (n, mat) ->
            val k = Car3dMaterials.key(n, mat, cat.materials, cat.partStyles); k to effective(k, cat.materials, m.pngs)
        }
        assertEquals(Car3dMaterials.LENS_REAR, eff("body_glasstail").first)
        assertEquals(Car3dMaterials.LENS_REAR, eff("trunk_glasstail").first)
        assertEquals(Car3dMaterials.LENS_CLEAR, eff("body_glasslamp").first)
        val (k, e) = eff("body_interior")
        assertEquals("interior", k); assertFalse(e.second, "SL6 không có car_texture1.png ⇒ bản trơn")
        assertTrue(e.first.rgba.take(3).all { it < 0.06 }, "khoang tối như Seal")
        assertTrue(e.first.emissive != null, "giữ emissiveFactor khai ⇒ đèn đọc vẫn hắt sáng được")
    }

    @Test
    fun `lop theo tu chung - thu tu dung, la thi toi trung tinh`() {
        assertEquals(Car3dMaterials.LENS_REAR, Car3dMaterials.classOf("glasstail"))
        assertEquals(Car3dMaterials.LENS_CLEAR, Car3dMaterials.classOf("glasslamp"))
        assertEquals("glass", Car3dMaterials.classOf("glass2"))
        assertEquals(Car3dLamps.WHITE, Car3dMaterials.classOf("lamp3"))
        assertEquals("interior", Car3dMaterials.classOf("seat_leather"))
        assertEquals(Car3dMaterials.PAINT_ACCENT, Car3dMaterials.classOf("paint1"))
        assertEquals(null, Car3dMaterials.classOf("xyz"))
        val styles = Car3dCatalog.BYD_AVM.materials
        assertEquals(Car3dMaterials.NEUTRAL_KEY, Car3dMaterials.key("body_xyz", "xyz", styles, emptyList()))
        assertTrue(Car3dMaterials.NEUTRAL.rgba.take(3).all { it < 0.1 }, "không bao giờ xám sáng mặc định")
        // Mọi kiểu có ảnh trong bảng đều có bản trơn tối hoặc là đèn SÁNG (phát sáng theo trạng thái).
        for ((k, st) in styles) if (st.texture != null) {
            val p = Car3dMaterials.plain(st)
            assertTrue(p.texture == null && !whiteish(k, p), "$k: bản trơn ${p.rgba}")
        }
    }

    // ── GLB: quyết định gắn ảnh theo TỪNG bộ phận ───────────────────────────────────────────────────────────────────────────

    private fun f32(vararg f: Float) = ByteBuffer.allocate(f.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { f.forEach { putFloat(it) } }.array()
    private fun i32(vararg i: Int) = ByteBuffer.allocate(i.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { i.forEach { putInt(it) } }.array()
    private fun block(kind: Int, name: String, mat: String, count: Int, body: ByteArray): ByteArray {
        val h = ByteBuffer.allocate(BydAvmMesh.HEADER).order(ByteOrder.LITTLE_ENDIAN)
        h.putInt(BydAvmMesh.HEADER + body.size).putInt(kind).put(name.toByteArray().copyOf(32)).put(mat.toByteArray().copyOf(32))
        h.putFloat(0f).putFloat(0f).putFloat(0f).putInt(count)
        return h.array() + body
    }

    /** Bộ đỉnh [mat] 3 đỉnh với `attr` = [uv] (u, v, z) + bộ phận [name] khai [mat] (bộ đỉnh TRÙNG tên khi hai bộ phận cùng [mat]). */
    private fun mesh(vararg parts: Triple<String, String, FloatArray>): BydAvmMesh = mesh(parts.toList()) { _, mat -> mat }

    /** Như trên, tên bộ đỉnh theo [bufOf] (chỉ số, vật liệu) — mỗi bộ phận MỘT bộ đỉnh riêng khi cần UV khác nhau cùng vật liệu. */
    private fun mesh(parts: List<Triple<String, String, FloatArray>>, bufOf: (Int, String) -> String): BydAvmMesh {
        val bytes = parts.flatMapIndexed { i, (name, mat, uv) ->
            val attr = FloatArray(9) { uv[it % 3] }
            val buf = bufOf(i, mat)
            listOf(block(1, buf, mat, 3, f32(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f) + f32(*attr) + f32(*FloatArray(9) { if (it % 3 == 1) 1f else 0f })),
                block(2, name, buf, 3, i32(0, 1, 2)))
        }.reduce { a, b -> a + b }
        return BydAvmMesh.parse(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
    }

    private fun json(m: BydAvmMesh, textures: Set<String>, texels: Map<String, Car3dMaterials.Texels> = emptyMap(),
                     partStyles: List<Pair<String, String>> = emptyList()): String {
        val glb = Car3dGlb.bytes(Car3dGlb.write(m, Car3dCatalog.BYD_AVM.materials, textures.associateWith { byteArrayOf(1, 2, 3, 4) },
            partStyles, flipV = true, texels = texels))
        val jLen = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        return String(glb, 20, jLen, Charsets.UTF_8)
    }

    /** Đoạn JSON của vật liệu glTF tên [name] (tới vật liệu kế / hết mảng). */
    private fun materialJson(json: String, name: String): String? {
        val i = json.indexOf("""{"name":"$name","pbrMetallicRoughness"""").takeIf { it >= 0 } ?: return null
        val ends = listOf(json.indexOf("{\"name\":", i + 1), json.indexOf("\"accessors\"", i)).filter { it > 0 }
        return json.substring(i, ends.minOrNull() ?: json.length)
    }

    @Test
    fun `GLB - attr khong phai UV thi khong gan anh, dung ban tron toi`() {
        // [ĐO SL6] `interior` u tới 2 252 — không phải UV dù ảnh có.
        val j = json(mesh(Triple("body_interior", "interior", floatArrayOf(2252f, 820f, 0f))), setOf("car_texture1.png"))
        val mat = materialJson(j, "interior${Car3dGlb.PLAIN}")
        assertTrue(mat != null && !mat.contains("baseColorTexture") && mat.contains("\"emissiveFactor\""), j)
        assertTrue(j.contains(""""mat":"interior""""), "extras.mat giữ khoá — đèn đọc tìm nội thất theo khoá")
        assertFalse(j.contains("\"images\""))
    }

    @Test
    fun `GLB - anh vang thi ban tron, UV that va anh co thi gan anh`() {
        val m = mesh(Triple("body_interior", "interior", floatArrayOf(0.4f, 0.6f, 0f)))
        assertTrue(materialJson(json(m, emptySet()), "interior${Car3dGlb.PLAIN}") != null, "ảnh vắng ⇒ bản trơn")
        val withTex = json(m, setOf("car_texture1.png"))
        assertTrue(materialJson(withTex, "interior")?.contains("baseColorTexture") == true, withTex)
    }

    @Test
    fun `GLB - den phat sang ma UV roi vao vung den cua anh thi ban tron phat sang`() {
        val m = mesh(Triple("body_lamp_7on", "lamp", floatArrayOf(0.2f, 0.3f, 0f)))
        val ps = listOf("body_lamp_7on" to Car3dLamps.WHITE_ON)
        val dark = json(m, setOf("car_lamp.png"), mapOf("car_lamp.png" to Car3dMaterials.Texels { _, _ -> 0 }), ps)
        val plain = materialJson(dark, "${Car3dLamps.WHITE_ON}${Car3dGlb.PLAIN}")
        assertTrue(plain != null && !plain.contains("emissiveTexture") && plain.contains("emissiveFactor"), dark)
        val bright = json(m, setOf("car_lamp.png"), mapOf("car_lamp.png" to Car3dMaterials.Texels { _, _ -> 200 }), ps)
        assertTrue(materialJson(bright, Car3dLamps.WHITE_ON)?.contains("emissiveTexture") == true, bright)
    }

    /**
     * QA máy ảo 14/10 [ĐO]: Seal `body_lamp_15on` (bản thứ hai) có UV trên vùng TỐI của `car_lamp.png` — bản đầu xét từng bộ phận
     * đưa nó sang `lamp_rear_on@plain` (phát sáng ×5, không ảnh) ⇒ đuôi 90,8 % điểm ảnh ≥ 250. Luật theo CẢ LƯỚI: đèn của lưới nói
     * chung sáng ⇒ bộ phận tối riêng vẫn giữ ảnh (và tối như tác giả vẽ). Hoàn luật từng-bộ-phận ⇒ đỏ.
     */
    @Test
    fun `GLB - mot den toi rieng giua cac den sang van giu anh - Seal body_lamp_15on`() {
        // Hai bộ đỉnh RIÊNG (lưới thật: mỗi bộ phận đèn một bộ đỉnh `lamp*`) — bộ đỉnh thứ hai có UV rơi vào vùng tối.
        val m = mesh(listOf(Triple("body_lamp_7on", "lamp", floatArrayOf(0.2f, 0.3f, 0f)), Triple("body_lamp_15on", "lamp", floatArrayOf(0.8f, 0.8f, 0f)))) { i, mat -> "$mat$i" }
        val ps = listOf("body_lamp_7on" to Car3dLamps.REAR_ON, "body_lamp_15on" to Car3dLamps.REAR_ON)
        val tex = mapOf("car_lamp.png" to Car3dMaterials.Texels { u, _ -> if (u > 0.5f) 0 else 200 })   // vùng u > 0,5 tối
        val j = json(m, setOf("car_lamp.png"), tex, ps)
        assertTrue(materialJson(j, Car3dLamps.REAR_ON)?.contains("emissiveTexture") == true, j)
        assertFalse(j.contains(Car3dGlb.PLAIN), "không bộ phận nào rơi sang bản trơn khi đèn của lưới nói chung sáng: $j")
        // Đối chứng SL6: MỌI đèn đều tối dưới UV ⇒ cả lưới bỏ ảnh ⇒ bản trơn phát sáng (giữ #42).
        val sl6 = json(m, setOf("car_lamp.png"), mapOf("car_lamp.png" to Car3dMaterials.Texels { _, _ -> 0 }), ps)
        assertTrue(materialJson(sl6, "${Car3dLamps.REAR_ON}${Car3dGlb.PLAIN}")?.contains("emissiveFactor") == true, sl6)
        assertFalse(sl6.contains("emissiveTexture"))
    }

    /** Bản trơn phát sáng không có ảnh làm tối ⇒ hệ số phải ≤ [Car3dMaterials.PLAIN_GLOW_MAX], ở dữ liệu lẫn ở [Car3dMaterials.plain]. */
    @Test
    fun `ban tron phat sang khong kep trang - he so bi kep`() {
        assertTrue(Car3dMaterials.PLAIN_GLOW_MAX <= 1.0, "trần ×1: đỉnh kênh ≈ 1 + phản xạ nắng ~0,4 còn đỏ sau PBR-Neutral [TÍNH]")
        for ((k, st) in Car3dCatalog.BYD_AVM.materials) {
            val p = Car3dMaterials.plain(st)
            if (p.emissive?.any { it > 0.0 } == true) assertTrue(p.emissiveStrength <= Car3dMaterials.PLAIN_GLOW_MAX, "$k: bản trơn ×${p.emissiveStrength}")
            st.plain?.let { if (it.emissive?.any { e -> e > 0.0 } == true) assertTrue(it.emissiveStrength <= Car3dMaterials.PLAIN_GLOW_MAX, "$k khai ×${it.emissiveStrength}") }
        }
        // Kẹp ở tầng chung: một kiểu khai bản trơn ×5 vẫn ra ≤ trần.
        val loud = Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), texture = "x.png", emissive = listOf(1.0, 0.0, 0.0),
            plain = Car3dMaterialStyle(listOf(0.8, 0.0, 0.0, 1.0), emissive = listOf(1.0, 0.0, 0.0), emissiveStrength = 5.0))
        assertEquals(Car3dMaterials.PLAIN_GLOW_MAX, Car3dMaterials.plain(loud).emissiveStrength)
        // GLB ghi đúng hệ số đã kẹp cho bản trơn (SL6).
        val m = mesh(Triple("body_lamp_7on", "lamp", floatArrayOf(0.2f, 0.3f, 0f)))
        val j = json(m, setOf("car_lamp.png"), mapOf("car_lamp.png" to Car3dMaterials.Texels { _, _ -> 0 }), listOf("body_lamp_7on" to Car3dLamps.REAR_ON))
        val plain = materialJson(j, "${Car3dLamps.REAR_ON}${Car3dGlb.PLAIN}")!!
        assertFalse(plain.contains("\"emissiveStrength\":5"), plain)
    }

    /**
     * 3.1.7 (diagnostics §15) — SL6: đèn đọc bật ⇒ `Car3dRenderer` đặt ×5,2 ấm lên MỌI nội thất; nội thất SL6 là bản TRƠN (không
     * ảnh nhân vào) ⇒ khoang + khe cốp cháy kem. Nay giá trị KHI BẬT ghi theo đúng bản: có ảnh = [Car3dMaterials.READ_GLOW] (× ảnh),
     * trơn = [Car3dMaterials.READ_GLOW_PLAIN] ≤ [Car3dMaterials.PLAIN_GLOW_MAX]. Gỡ `extras.glow` / dùng ×5,2 cho bản trơn ⇒ đỏ.
     */
    @Test
    fun `GLB - den khoang theo ban da dung, noi that tron khong chay kem`() {
        fun glowOf(j: String): List<Double>? = Car3dBox.glow(Regex(""""extras":\{[^}]*"mat":"interior"\}""").find(j)?.value)?.map { it.toDouble() }
        val plainJ = json(mesh(Triple("body_interior", "interior", floatArrayOf(2252f, 820f, 0f))), setOf("car_texture1.png"))
        val g = glowOf(plainJ)
        assertTrue(g != null, "nội thất trơn vẫn hắt đèn khoang được: $plainJ")
        assertTrue(g!!.max() <= Car3dMaterials.PLAIN_GLOW_MAX && g.max() < 0.25, "bản trơn: ${g} (×5,2 = cháy kem)")
        assertTrue(g[0] > g[1] && g[1] > g[2], "vẫn ánh ấm")
        assertTrue(materialJson(plainJ, "interior${Car3dGlb.PLAIN}")!!.contains("\"emissiveFactor\""), "có tham số để bên vẽ đổi")
        val texJ = json(mesh(Triple("body_interior", "interior", floatArrayOf(0.4f, 0.6f, 0f))), setOf("car_texture1.png"))
        assertEquals(Car3dMaterials.READ_GLOW, glowOf(texJ)?.map { Math.round(it * 10) / 10.0 }, "bản có ảnh giữ ×5,2 nhân ảnh (Seal không đổi)")
        // Vật liệu không khai đèn khoang ⇒ không có `extras.glow`.
        val other = json(mesh(Triple("body_paint", "paint", floatArrayOf(0f, 0f, 0.3f))), emptySet())
        assertFalse(other.contains("\"glow\""), other)
        // Kẹp ở tầng chung: bản trơn khai đèn khoang ×5 ⇒ kênh lớn nhất = trần, giữ tỉ lệ.
        val loud = Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), texture = "x.png",
            plain = Car3dMaterialStyle(listOf(0.05, 0.05, 0.05, 1.0), glowOn = listOf(5.0, 2.5, 0.0)))
        assertEquals(listOf(Car3dMaterials.PLAIN_GLOW_MAX, 0.5 * Car3dMaterials.PLAIN_GLOW_MAX, 0.0), Car3dMaterials.plain(loud).glowOn)
        assertEquals(null, Car3dBox.glow("""{"glow":[1,2]}""")); assertEquals(null, Car3dBox.glow("""{"glow":[1,-2,0]}"""))
    }

    @Test
    fun `GLB - vat lieu khai la theo lop, khong lop thi toi trung tinh`() {
        val j = json(mesh(Triple("body_glasstail", "glasstail", floatArrayOf(0.5f, 0.5f, 0f)), Triple("body_foo", "foo", floatArrayOf(0f, 0f, 0.3f))), emptySet())
        assertTrue(materialJson(j, Car3dMaterials.LENS_REAR)?.contains("\"alphaMode\":\"BLEND\"") == true, j)
        assertTrue(j.contains(""""mat":"${Car3dMaterials.NEUTRAL_KEY}""""), j)
        assertFalse(j.contains("0.6,0.6,0.62"), "không còn xám sáng mặc định")
    }

    /**
     * Ca đo trên 36 lưới thật (ngoài repo) — cùng đường nạp của `Car3dModelSource`: bảng theo nhóm → vai đèn đo → quyết định ảnh
     * theo từng bộ phận (ảnh giải mã bằng ImageIO). In bảng `CAR3D-MAT|…` cho diagnostics §13.
     */
    @Test
    fun `36 luoi that - bang vat lieu, chi chay khi co KACHI_AVM_ROOT`() {
        val root = System.getenv("KACHI_AVM_ROOT")?.let(::File)
        assumeTrue(root != null && root.isDirectory, "không có KACHI_AVM_ROOT — bỏ qua (repo không chứa byte BYD)")
        val dirs = root!!.walkTopDown().filter { File(it, "car.mesh").isFile }.sortedBy { it.path }.toList()
        val bad = ArrayList<String>()
        for (d in dirs) {
            val rel = d.relativeTo(root).path
            val cat = Car3dCatalog.forAvm(rel.substringBefore('/'))
            val raw = BydAvmMesh.parse(File(d, "car.mesh").readBytes()) + BydAvmMesh.parse(File(d, "wheel.mesh").readBytes())
            val mesh = Car3dDupes.without(raw, Car3dDupes.aggregates(raw))
            val ps = cat.partStyles + Car3dLamps.styles(mesh, cat.whiteLamps, cat.variants)
            val pngs = d.list()!!.filter { it.endsWith(".png") }.toSet()
            val texels = pngs.associateWith { n ->
                val img = javax.imageio.ImageIO.read(File(d, n))
                Car3dMaterials.Texels { u, v ->
                    val c = img.getRGB((u * (img.width - 1)).toInt(), (v * (img.height - 1)).toInt())
                    (0.2126 * ((c shr 16) and 255) + 0.7152 * ((c shr 8) and 255) + 0.0722 * (c and 255)).toInt()
                }
            }
            var unmatched = 0; var plain = 0; val white = ArrayList<String>(); val classes = sortedSetOf<String>()
            val dark = Car3dMaterials.darkTextures(mesh, cat.materials, ps, { it in pngs }, texels, cat.uvOriginBottom)
            for (p in mesh.parts) {
                val key = Car3dMaterials.key(p, cat.materials, ps)
                if (p.material !in cat.materials && ps.none { Car3dNames.matches(it.first, p.name) }) { unmatched++; classes += "${p.material}→$key" }
                val st = Car3dMaterials.style(key, cat.materials)
                val buf = mesh.buffer(p.buffer)!!
                val verts = (0 until p.indexCount).map { p.index(it) }.distinct().toIntArray()
                val textured = st.texture != null && Car3dMaterials.textured(st, buf, verts, { it in pngs }, dark)
                val eff = if (st.texture != null && !textured) Car3dMaterials.plain(st).also { plain++ } else st
                if (!textured && whiteish(key, eff)) white += p.name
            }
            println("CAR3D-MAT|$rel|${mesh.parts.size}|unmatched $unmatched ${classes.joinToString(" ")}|plain $plain|white ${white.size} ${white.take(6)}")
            if (white.isNotEmpty()) bad += "$rel $white"
        }
        assertEquals(emptyList<String>(), bad)
    }

    private companion object { val PAINTED = setOf("paint", "open") }
}
