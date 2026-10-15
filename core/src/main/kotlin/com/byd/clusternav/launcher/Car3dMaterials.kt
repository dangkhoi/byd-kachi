package com.byd.clusternav.launcher

/**
 * ═══ 3.1.5 — vật liệu của lưới AVM cho MỌI đời xe: khoá kiểu theo cấu trúc + từ chung, ảnh chỉ gắn khi ĐO thấy dùng được ═══════
 *
 * Owner 14/10 (ảnh SL6 của anh em, Kachi 3.1.4): dải đèn đuôi trắng đặc, ghế/khoang trắng sáng qua kính, vài chi tiết gần trắng,
 * một đường trắng dọc mép cốp. Seal (`eke`) đúng vì bảng vật liệu dựng theo TÊN + ẢNH của lưới Seal. Gốc [ĐO trên 36 lưới
 * `car.mesh` của AutoVideo.apk, bộ chạy thử ngoài repo — docs/diagnostics/widget-3d-301-2026-10-13.md §13]:
 *  1. Vật liệu khai mà bảng không có (`glasstail` — kính đèn đuôi, 10 lưới; `paint1` — 3 lưới) ⇒ kiểu mặc định XÁM 0,6 đục ⇒
 *     dưới nắng 55 k lux gần trắng. SL6: `body_glasstail` + `trunk_glasstail` = dải đèn đuôi + mép nắp cốp.
 *  2. Kiểu có ảnh (nền TRẮNG 1,0 × ảnh) mà ảnh VẮNG trong thư mục đời đó (`car_texture1.png` chỉ Seal/paha có; `car_lamp.png`
 *     eqedu không có; `wheel_texture.png` hc/sa20/stf không có) ⇒ trắng đặc (mâm còn phát sáng ×0,8 ⇒ trắng cháy).
 *  3. `attr` của bộ đỉnh không phải UV (SL6 `interior`: u tới 2 252) ⇒ dù có ảnh cũng lấy mẫu rác.
 *  4. Ảnh có nhưng UV rơi vào vùng ĐEN của ảnh (SL6 `lamp` × `car_lamp.png`: 100 % mẫu < 40) ⇒ đèn sáng = phát sáng × đen = không sáng.
 * Luật (thứ tự):
 *  • khoá kiểu: mẫu tên tường minh ([Car3dModel.partStyles] + vai đèn [Car3dLamps]) → vật liệu KHAI nếu bảng có → LỚP theo từ chung
 *    ([classOf] trên vật liệu khai, rồi tên bộ phận) → [NEUTRAL_KEY]. Không bao giờ rơi vào màu mặc định sáng.
 *  • ảnh chỉ gắn khi: tệp có · `attr` của chính các đỉnh bộ phận là UV ([uvLike]) · kiểu PHÁT SÁNG thì ảnh phải sáng dưới UV
 *    của **cả lưới** ([darkTextures] — xem dưới). Không đạt ⇒ bản TRƠN của kiểu ([Car3dMaterialStyle.plain], vắng ⇒ [NEUTRAL]) — tối,
 *    không trắng; bản trơn PHÁT SÁNG bị kẹp ≤ [PLAIN_GLOW_MAX] ([plain]) vì không còn ảnh làm tối.
 *
 * ## 3.1.5 (QA máy ảo 14/10 [ĐO]) — luật "ảnh đèn tối ⇒ bản trơn" theo CẢ LƯỚI, không theo từng bộ phận
 * Bản đầu xét từng bộ phận: Seal `body_lamp_15on` (bản thứ hai) có UV rơi vào vùng TỐI của `car_lamp.png` — ảnh VẼ nó tối là
 * cố ý (3.1.4 chưa từng sáng) — bị đưa sang `lamp_rear_on@plain` = phát sáng ×5 KHÔNG ảnh làm tối ⇒ PBR-Neutral kẹp ra
 * hồng-trắng: 90,8 % điểm ảnh đuôi ≥ 250, 66,5 % gần trắng; đổi lại ảnh ⇒ 0 điểm kẹp [ĐO QA]. Luật đúng: ảnh đã vẽ cho lưới
 * này chỉ bị bỏ khi **mọi** bộ phận phát sáng dùng nó đều tối dưới UV (SL6: 100 % mẫu < 40 — §13 #42); một bộ phận tối riêng
 * giữ ảnh (và vẫn tối — như tác giả vẽ).
 */
object Car3dMaterials {

    /** Khoá kiểu tối trung tính — bộ phận không lớp nào nhận. */
    const val NEUTRAL_KEY = "neutral"
    /** Kính đèn đuôi (`glasstail`): đỏ sẫm trong mờ — đèn đuôi tắt nhìn đỏ sẫm, đèn sáng bên dưới vẫn lộ ra. */
    const val LENS_REAR = "lens_rear"
    /** Kính che đèn pha (`glasslamp`): trong, khói nhẹ — không phải bóng đèn. */
    const val LENS_CLEAR = "lens_clear"
    /** Sơn phụ (`paint1`…): bên vẽ không tô (chỉ `paint`/`open`) ⇒ tối trung tính, không xám-trắng. */
    const val PAINT_ACCENT = "paint_accent"

    val NEUTRAL = Car3dMaterialStyle(listOf(0.05, 0.05, 0.055, 1.0), roughness = 0.6)

    /**
     * Lớp theo TỪ CHUNG của lưới AVM (vật liệu khai, rồi tên bộ phận). Thứ tự quan trọng: `glasstail` có "glass" lẫn "tail",
     * `glasslamp` có "glass" lẫn "lamp" — kính đèn là KÍNH, không phải bóng đèn. `null` = không từ nào khớp.
     */
    fun classOf(token: String): String? {
        val t = token.lowercase()
        return when {
            GLASS in t && (TAIL in t || REAR in t) -> LENS_REAR
            GLASS in t && LAMP in t -> LENS_CLEAR
            GLASS in t -> GLASS
            LAMP in t -> Car3dLamps.WHITE
            INTERIOR.any { it in t } -> "interior"
            TIRE.any { it in t } -> "tire"
            "metal_black" in t -> "metal_black"
            "black" in t -> "black"
            "metal" in t || "chrome" in t -> "metal"
            PAINT in t -> PAINT_ACCENT
            "texture" in t -> "texture"
            else -> null
        }
    }

    /** Khoá kiểu của [p] — xem luật ở đầu tệp. Kết quả luôn có trong [styles] hoặc là [NEUTRAL_KEY]. */
    fun key(p: BydAvmMesh.Part, styles: Map<String, Car3dMaterialStyle>, partStyles: List<Pair<String, String>>): String =
        key(p.name, p.material, styles, partStyles)

    /** Như trên theo TÊN bộ phận + vật liệu khai (test chạy trên danh sách tên của lưới thật). */
    fun key(name: String, material: String, styles: Map<String, Car3dMaterialStyle>, partStyles: List<Pair<String, String>>): String {
        partStyles.firstOrNull { Car3dNames.matches(it.first, name) }?.second?.let { return it }
        if (material in styles) return material
        return listOf(material, name).firstNotNullOfOrNull { classOf(it)?.takeIf { k -> k in styles } } ?: NEUTRAL_KEY
    }

    fun style(key: String, styles: Map<String, Car3dMaterialStyle>): Car3dMaterialStyle = styles[key] ?: NEUTRAL

    /**
     * Bản trơn (không ảnh) của [st]. Phát sáng bị kẹp ≤ [PLAIN_GLOW_MAX]: bản có ảnh phát sáng ×4–5 **nhân ảnh** (ảnh đèn phần lớn
     * tối ⇒ hiệu dụng ~1), bản trơn không có ảnh làm tối ⇒ cùng hệ số sẽ kẹp trắng (QA 14/10 [ĐO]: đuôi Seal 90,8 % ≥ 250).
     */
    fun plain(st: Car3dMaterialStyle): Car3dMaterialStyle {
        val p = st.plain ?: NEUTRAL
        val q = if (p.emissive != null && p.emissiveStrength > PLAIN_GLOW_MAX) p.copy(emissiveStrength = PLAIN_GLOW_MAX) else p
        // Đèn khoang của bản trơn: kênh lớn nhất ≤ PLAIN_GLOW_MAX (giữ tỉ lệ màu) — cùng lý do, không ảnh làm tối (§15).
        val g = q.glowOn ?: return q
        val m = g.maxOrNull() ?: 0.0
        return if (m > PLAIN_GLOW_MAX) q.copy(glowOn = g.map { it * PLAIN_GLOW_MAX / m }) else q
    }

    /**
     * Ánh ấm của đèn khoang (đèn đọc) trên nội thất CÓ ảnh (tuyến tính, × ảnh nội thất). R28 [ĐO máy ảo 13/10]: 0,22/0,15/0,06
     * không đổi một điểm ảnh nào ở khoang qua kính ⇒ ×12. Trước 3.1.7 là hằng số ở `Car3dRenderer` đặt lên MỌI nội thất.
     */
    val READ_GLOW: List<Double> = listOf(5.2, 3.6, 1.5)

    /**
     * Bản TRƠN (không ảnh): [READ_GLOW] × độ sáng tuyến tính trung bình của ảnh nội thất Seal dưới UV nội thất ([ĐO JVM 15/10]
     * sRGB TB 44–46/255 ⇒ tuyến tính ≈ 0,03) ⇒ cùng độ hắt sáng như khoang Seal có ảnh. [ĐO cơ chế] SL6 (không ảnh) trước đó nhận
     * nguyên ×5,2 ⇒ ~35 lần Seal ⇒ khoang + khe cốp kem cháy (§15).
     */
    val READ_GLOW_PLAIN: List<Double> = READ_GLOW.map { it * SEAL_INTERIOR_LINEAR }
    const val SEAL_INTERIOR_LINEAR = 0.03

    /** Một lượt đếm mẫu ảnh dưới UV: [dark] mẫu tối hơn [DARK_LUM] trên [n] mẫu. */
    class Tally { var dark = 0; var n = 0 }

    /**
     * Ảnh của kiểu PHÁT SÁNG mà **mọi** bộ phận của [mesh] dùng nó đều tối dưới UV (gộp mẫu của cả lưới: > [DARK_SHARE] mẫu <
     * [DARK_LUM]) ⇒ bỏ ảnh cho cả lưới ([textured] ⇒ bản trơn). Chỉ bộ phận có UV thật ([uvLike]) + tệp ảnh có mới được đếm.
     * Một bộ phận tối riêng giữa các bộ phận sáng KHÔNG làm ảnh bị bỏ (Seal `body_lamp_15on` — xem đầu tệp).
     */
    fun darkTextures(
        mesh: BydAvmMesh, styles: Map<String, Car3dMaterialStyle>, partStyles: List<Pair<String, String>>,
        has: (String) -> Boolean, texels: Map<String, Texels>, flipV: Boolean,
    ): Set<String> {
        if (texels.isEmpty()) return emptySet()
        val tallies = HashMap<String, Tally>()
        for (p in mesh.parts) {
            if (p.indexCount == 0) continue
            val st = style(key(p, styles, partStyles), styles)
            val name = st.texture ?: continue
            if (st.emissive == null || !has(name)) continue
            val tex = texels[name] ?: continue
            val buf = mesh.buffer(p.buffer) ?: continue
            val verts = IntArray(p.indexCount) { p.index(it) }
            if (!uvLike(buf, verts)) continue
            tally(buf, verts, tex, flipV, tallies.getOrPut(name) { Tally() })
        }
        return tallies.filterValues { it.n > 0 && it.dark > DARK_SHARE * it.n }.keys
    }

    /** Hệ số phát sáng tối đa của bản TRƠN (không ảnh) — xem [plain]. [TÍNH] PBR-Neutral: đỉnh kênh ≈ 1 + phản xạ nắng ~0,4 ⇒ còn đỏ/cam, không kẹp. */
    const val PLAIN_GLOW_MAX = 1.0

    /** Độ sáng (0..255) của ảnh tại UV glTF (gốc ĐỈNH ảnh, đã lật nếu cần); `:app` dựng từ ảnh giải mã thu nhỏ. */
    fun interface Texels { fun lum(u: Float, v: Float): Int }

    /**
     * `attr` của các đỉnh [verts] là UV thật: thành phần thứ ba = 0 (vật liệu trơn của AVM mang tham số ≠ 0 ở đó [ĐO `glass`
     * 0,2–0,6 · `metal` 0–0,7 · `black` 0,05–0,3]) và ≥ [UV_SHARE] số đỉnh nằm trong [−0,5; 1,5]² ([ĐO] SL6 `interior` u tới 2 252).
     */
    fun uvLike(buf: BydAvmMesh.Buffer, verts: IntArray): Boolean {
        if (verts.isEmpty()) return false
        var inside = 0; var n = 0
        forSample(verts) { v ->
            n++
            if (kotlin.math.abs(buf.uv(v, 2)) > Z_EPS) return false
            val u = buf.uv(v, 0); val w = buf.uv(v, 1)
            if (u in UV_LO..UV_HI && w in UV_LO..UV_HI) inside++
        }
        return inside >= UV_SHARE * n
    }

    /** Đếm mẫu ảnh [tex] dưới UV của [verts] vào [into] (≤ [SAMPLES] mẫu mỗi bộ phận). */
    fun tally(buf: BydAvmMesh.Buffer, verts: IntArray, tex: Texels, flipV: Boolean, into: Tally) {
        forSample(verts) { v ->
            into.n++
            val u = wrap(buf.uv(v, 0)); val w = wrap(if (flipV) 1f - buf.uv(v, 1) else buf.uv(v, 1))
            if (tex.lum(u, w) < DARK_LUM) into.dark++
        }
    }

    /**
     * Gắn ảnh cho bộ phận này không: kiểu có ảnh · tệp có · UV thật · (kiểu phát sáng ⇒ ảnh KHÔNG nằm trong [dark] — bộ ảnh tối
     * dưới UV của CẢ lưới, [darkTextures]). `false` với kiểu có ảnh ⇒ bên ghi dùng [plain].
     */
    fun textured(st: Car3dMaterialStyle, buf: BydAvmMesh.Buffer, verts: IntArray, has: (String) -> Boolean, dark: Set<String>): Boolean {
        val name = st.texture ?: return false
        if (!has(name) || !uvLike(buf, verts)) return false
        return st.emissive == null || name !in dark
    }

    private fun wrap(x: Float): Float { val f = x - kotlin.math.floor(x); return if (f.isNaN()) 0f else f }

    private inline fun forSample(verts: IntArray, f: (Int) -> Unit) {
        val step = maxOf(1, verts.size / SAMPLES)
        var i = 0
        while (i < verts.size) { f(verts[i]); i += step }
    }

    const val UV_LO = -0.5f
    const val UV_HI = 1.5f
    const val UV_SHARE = 0.98
    const val Z_EPS = 1e-4f
    const val DARK_LUM = 40
    const val DARK_SHARE = 0.9
    private const val SAMPLES = 512

    private const val GLASS = "glass"
    private const val LAMP = "lamp"
    private const val TAIL = "tail"
    private const val REAR = "rear"
    private const val PAINT = "paint"
    private val INTERIOR = listOf("interior", "seat")
    private val TIRE = listOf("tire", "tyre", "rubber")
}
