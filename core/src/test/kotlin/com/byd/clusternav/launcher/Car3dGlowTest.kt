package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Đo trên 36 lưới thật (`KACHI_AVM_ROOT`, ngoài repo): dựng GLB ĐÚNG đường nạp của `Car3dModelSource` rồi đọc lại JSON — bộ
 * phận nào PHÁT SÁNG mà vẫn HIỆN khi mọi đèn tắt (mọi biến thể ở trạng thái nghỉ).
 */
class Car3dGlowTest {

    private class Row(val name: String, val key: String, val mat: String, val rgba: List<Double>, val emissive: List<Double>?,
                      val strength: Double, val textured: Boolean, val unlit: Boolean, val visibleOff: Boolean, val glow: List<Double>?)

    @Suppress("UNCHECKED_CAST")
    private fun rows(d: File, rel: String): List<Row> {
        val cat = Car3dCatalog.forAvm(rel.substringBefore('/'))
        val raw = BydAvmMesh.parse(File(d, "car.mesh").readBytes()) + BydAvmMesh.parse(File(d, "wheel.mesh").readBytes())
        val mesh0 = Car3dDupes.without(raw, Car3dDupes.aggregates(raw))
        val lid = Car3dLid.of(mesh0)
        val mesh = lid?.mesh ?: mesh0
        val hinges = Car3dHinge.fitAll(mesh)
        val lamps = Car3dLamps.styles(mesh, cat.whiteLamps, cat.variants)
        val cabin = Car3dCabin.apply(mesh, lid)
        val pngs = d.list()!!.filter { it.endsWith(".png") }.toSet()
        val tex = cat.materials.values.mapNotNull { it.texture }.distinct().filter { it in pngs }.associateWith { File(d, it).readBytes() }
        val texels = cat.materials.values.filter { it.emissive != null }.mapNotNull { it.texture }.distinct().filter { it in pngs }.associateWith { n ->
            val img = javax.imageio.ImageIO.read(File(d, n))
            Car3dMaterials.Texels { u, v ->
                val c = img.getRGB((u * (img.width - 1)).toInt(), (v * (img.height - 1)).toInt())
                (0.2126 * ((c shr 16) and 255) + 0.7152 * ((c shr 8) and 255) + 0.0722 * (c and 255)).toInt()
            }
        }
        val glb = Car3dGlb.bytes(Car3dGlb.write(cabin.mesh, cat.materials, tex, cat.partStyles + lamps, cat.uvOriginBottom, hinges, cabin.lid, texels))
        val j = Car3dGlbJson.of(glb)
        val nodes = j["nodes"] as List<Map<String, Any?>>
        val meshes = j["meshes"] as List<Map<String, Any?>>
        val mats = j["materials"] as List<Map<String, Any?>>
        val off = cat.variants.map { false }
        return nodes.drop(1).map { n ->
            val name = n["name"] as String
            val extras = n["extras"] as Map<String, Any?>
            val prim = (meshes[(n["mesh"] as Double).toInt()]["primitives"] as List<Map<String, Any?>>)[0]
            val m = mats[(prim["material"] as Double).toInt()]
            val pbr = m["pbrMetallicRoughness"] as Map<String, Any?>
            val ext = m["extensions"] as Map<String, Any?>?
            val strength = ((ext?.get("KHR_materials_emissive_strength") as Map<String, Any?>?)?.get("emissiveStrength") as Double?) ?: 1.0
            Row(name, extras["mat"] as String, m["name"] as String, (pbr["baseColorFactor"] as List<Double>),
                m["emissiveFactor"] as List<Double>?, strength, pbr.containsKey("baseColorTexture"), ext?.containsKey("KHR_materials_unlit") == true,
                Car3dNames.visibleAll(cat.variants, off, name) ?: true, extras["glow"] as List<Double>?)
        }
    }

    /** Phát sáng thật (hệ số × cường độ > 0). Mâm ×0,8 nhân ảnh mâm = ánh nền có chủ đích ([Car3dCatalog] WHEEL_FILL) — tách riêng. */
    private fun glows(r: Row) = (r.emissive?.maxOrNull() ?: 0.0) * r.strength > 0.0

    @Test
    fun `36 luoi that - khong bo phan nao phat sang khi moi den tat (KACHI_AVM_ROOT)`() {
        val root = System.getenv("KACHI_AVM_ROOT")?.let(::File)
        assumeTrue(root != null && root.isDirectory, "không có KACHI_AVM_ROOT — bỏ qua (repo không chứa byte BYD)")
        val dirs = root!!.walkTopDown().filter { File(it, "car.mesh").isFile }.sortedBy { it.path }.toList()
        val bad = ArrayList<String>()
        for (d in dirs) {
            val rel = d.relativeTo(root).path
            val rows = rows(d, rel)
            val lit = rows.filter { it.visibleOff && glows(it) && it.key != "wheel" }
            val fill = rows.count { it.visibleOff && glows(it) && it.key == "wheel" }
            val readPlain = rows.filter { it.glow != null && !it.textured }.maxOfOrNull { it.glow!!.max() }
            println("CAR3D-GLOW|$rel|${rows.size}|lit-off ${lit.size} ${lit.take(8).map { "${it.name}:${it.mat}" }}|wheel-fill $fill|readl-plain-max $readPlain")
            if (System.getenv("CAR3D_DUMP") == rel) rows.forEach {
                println("CAR3D-PART|${it.name}|${it.key}|${it.mat}|rgba ${it.rgba}|em ${it.emissive} x${it.strength}|tex ${it.textured}|unlit ${it.unlit}|off-visible ${it.visibleOff}|glow ${it.glow}")
            }
            if (lit.isNotEmpty()) bad += "$rel ${lit.map { it.name }}"
            // Đèn khoang BẬT: bản trơn không có ảnh làm tối ⇒ ≤ trần; nội thất nào cũng còn hắt được (đèn đọc không mất).
            if ((readPlain ?: 0.0) > Car3dMaterials.PLAIN_GLOW_MAX) bad += "$rel đèn khoang trơn ×$readPlain"
            rows.filter { it.key == "interior" && it.glow == null }.forEach { bad += "$rel ${it.name} nội thất không có extras.glow" }
        }
        assertEquals(emptyList<String>(), bad)
    }
}
