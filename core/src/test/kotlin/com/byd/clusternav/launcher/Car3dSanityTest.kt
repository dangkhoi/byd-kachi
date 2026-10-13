package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3.01 R25 — kiểm hợp lý lưới AVM lúc nạp ([Car3dSanity]) + bảng mô hình theo nhóm AVM ([Car3dCatalog.forAvm]).
 * Lưới TỔNG HỢP dựng tại chỗ (repo không chứa byte BYD): thân hộp có tấm sườn chừa hốc bánh + 4 bánh kind 0 quanh pivot.
 * Ca thật 37 tệp của AutoVideo.apk chạy khi có biến môi trường `KACHI_AVM_ROOT` (thư mục `assets/resource_pi` đã giải nén,
 * NGOÀI repo) — không có thì bỏ qua.
 */
class Car3dSanityTest {

    private fun f32(vararg f: Float) = ByteBuffer.allocate(f.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { f.forEach { putFloat(it) } }.array()
    private fun i32(vararg i: Int) = ByteBuffer.allocate(i.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { i.forEach { putInt(it) } }.array()

    private fun block(kind: Int, name: String, mat: String, count: Int, body: ByteArray, pivot: FloatArray = FloatArray(3)): ByteArray {
        val h = ByteBuffer.allocate(BydAvmMesh.HEADER).order(ByteOrder.LITTLE_ENDIAN)
        h.putInt(BydAvmMesh.HEADER + body.size).putInt(kind).put(name.toByteArray().copyOf(32)).put(mat.toByteArray().copyOf(32))
        h.putFloat(pivot[0]).putFloat(pivot[1]).putFloat(pivot[2]).putInt(count)
        return h.array() + body
    }

    /** Bộ đỉnh [pts] + bộ phận [name] trỏ mọi đỉnh (đệm cho chia hết 3). */
    private fun buffer(name: String, pts: List<FloatArray>): ByteArray {
        val p = pts.toMutableList(); while (p.size % 3 != 0) p += p.last()
        val pos = p.flatMap { it.toList() }.toFloatArray()
        val vb = block(1, name, name, p.size, f32(*pos) + f32(*FloatArray(pos.size)) + f32(*FloatArray(pos.size) { if (it % 3 == 1) 1f else 0f }))
        return vb + block(2, name, name, p.size, i32(*IntArray(p.size) { it }))
    }

    private val hubsX = listOf(1.4f, -1.4f)

    /** Thân 4,6 × 1,9 × 1,3 m (đáy 0,18): góc hộp + tấm sườn |z| 0,95 lưới 5 cm, CHỪA hốc bán kính 0,4 quanh mỗi bánh. */
    private fun body(): ByteArray {
        val pts = ArrayList<FloatArray>()
        for (x in listOf(-2.3f, 2.3f)) for (y in listOf(0.18f, 1.48f)) for (z in listOf(-0.95f, 0.95f)) pts += floatArrayOf(x, y, z)
        var x = -2.25f
        while (x <= 2.25f) {
            var y = 0.2f
            while (y <= 1.0f) {
                if (hubsX.none { hx -> (x - hx) * (x - hx) + (y - 0.35f) * (y - 0.35f) < 0.16f }) {
                    pts += floatArrayOf(x, y, -0.95f); pts += floatArrayOf(x, y, 0.95f)
                }
                y += 0.05f
            }
            x += 0.05f
        }
        return buffer("paint", pts) + buffer("doorlf_glass", listOf(floatArrayOf(0.3f, 1.1f, -0.8f), floatArrayOf(0.6f, 1.1f, -0.8f), floatArrayOf(0.4f, 1.3f, -0.8f)))
    }

    /** Bánh rời (kind 0) quanh [pivot]: vành 36 điểm bán kính 0,35 ở hai mặt z ±0,11 quanh pivot. */
    private fun wheel(name: String, pivot: FloatArray, zSpan: Float = 0.11f): ByteArray {
        val pts = ArrayList<Float>()
        for (side in listOf(-zSpan, zSpan)) for (i in 0 until 36) {
            val a = i * Math.PI * 2 / 36
            pts += listOf((0.35 * cos(a)).toFloat(), (0.35 * sin(a)).toFloat(), side)
        }
        val n = pts.size / 3
        return block(0, name, "texture", n, f32(*pts.toFloatArray()) + f32(*FloatArray(n * 3)) + f32(*FloatArray(n * 3)) + i32(*IntArray(n) { it }), pivot)
    }

    private fun mesh(frontX: Float = 1.4f, sideZ: Float = 0.77f): BydAvmMesh {
        val w = wheel("wheellf_texture", floatArrayOf(frontX, 0.35f, -sideZ)) + wheel("wheelrf_texture", floatArrayOf(frontX, 0.35f, sideZ)) +
            wheel("wheelr_texture", floatArrayOf(-1.4f, 0.35f, 0f), zSpan = sideZ + 0.11f).let { it }
        return BydAvmMesh.parse(body()) + BydAvmMesh.parse(w)
    }

    @Test
    fun `luoi hop ly qua kiem - than, bon banh trong hoc, neo kinh`() {
        val v = Car3dSanity.check(mesh(), Car3dCatalog.BYD_AVM)
        assertTrue(v.ok, "${v.failures} ${v.notes}")
        assertTrue(v.notes.any { it.startsWith("body L4.60") }, "${v.notes}")
    }

    @Test
    fun `banh lech khoi hoc, thieu banh, than sai don vi, khong cua thi truot`() {
        // Bánh trước dời ra giữa sườn (tấm sườn che mặt bánh) ⇒ "outside its arch" — đúng loại lệch owner hỏi.
        val off = Car3dSanity.check(mesh(frontX = 0.2f), Car3dCatalog.BYD_AVM)
        assertFalse(off.ok); assertTrue(off.failures.any { it.contains("FL outside its arch") }, "${off.failures}")
        // Không bánh nào.
        val none = Car3dSanity.check(BydAvmMesh.parse(body()), Car3dCatalog.BYD_AVM)
        assertTrue(none.failures.any { it.contains("wheel FL missing") }, "${none.failures}")
        // Không bộ phận cửa/kính ⇒ widget 3D không có việc (sc2e/bev [ĐO]).
        val noDoor = Car3dSanity.check(BydAvmMesh.parse(buffer("paint", listOf(floatArrayOf(-2.3f, 0.2f, -0.9f), floatArrayOf(2.3f, 1.5f, 0.9f)))) +
            BydAvmMesh.parse(wheel("wheellf_texture", floatArrayOf(1.4f, 0.35f, -0.77f))), Car3dCatalog.BYD_AVM)
        assertTrue(noDoor.failures.any { it.contains("no window anchor") }, "${noDoor.failures}")
        // Thân sai đơn vị (mm thay m) ⇒ trượt hộp.
        val mm = Car3dSanity.check(BydAvmMesh.parse(buffer("paint", listOf(floatArrayOf(-2300f, 200f, -900f), floatArrayOf(2300f, 1500f, 900f)))), Car3dCatalog.BYD_AVM)
        assertTrue(mm.failures.any { it.startsWith("body length") }, "${mm.failures}")
    }

    @Test
    fun `mo hinh theo nhom AVM - Seal co bang den da do, doi khac den so luon tat`() {
        val seal = Car3dCatalog.forAvm("eke"); val other = Car3dCatalog.forAvm("hc")
        assertEquals("byd-avm-eke", seal.id); assertEquals(Car3dCatalog.BYD_AVM, other)
        fun vis(m: Car3dModel, name: String, vararg on: String) =
            Car3dNames.visibleAll(m.variants, m.variants.map { v -> v.read.split('|').any { it.isNotEmpty() && it in on } }, name)
        // Đời chưa giải mã: đèn SỐ không bao giờ sáng (nghĩa số khác nhau giữa các đời) — không sáng sai.
        assertEquals(false, vis(other, "body_lamp_3on", "light_high_beam", "light_low_beam", "light_drl"))
        assertEquals(true, vis(other, "body_lamp_3", "light_high_beam"))
        assertEquals(false, vis(other, "trunk_lamp_13on", "light_low_beam"))
        // Đèn có TÊN chung thì theo số đọc.
        assertEquals(true, vis(other, "body_lamp_head3on", "light_low_beam")); assertEquals(false, vis(other, "body_lamp_head3", "light_low_beam"))
        assertEquals(true, vis(other, "trunk_lamp_tailon", "light_side")); assertEquals(true, vis(other, "trunk_lamp_turnleft0on", "light_left_turn"))
        assertEquals(false, vis(other, "trunk_lamp_stopon", "light_low_beam"))
        // Neo đèn của Seal theo số đã đo; đời khác chỉ theo tên có nghĩa.
        assertEquals(Car3dAnchor.Node("body_lamp_4"), seal.hotspots.first { it.id == "headl" }.anchor)
        assertFalse(Car3dSanity.resolves(other.hotspots.first { it.id == "headl" }.anchor, listOf("body_lamp_4", "body_lamp_3")))
        assertTrue(Car3dSanity.resolves(other.hotspots.first { it.id == "headl" }.anchor, listOf("body_lamp_head3")))
        // Cốp: bản mở theo LỆNH cuối (cmd:trunk), đời có `trunk_open` [ĐO ew/paha/sc3e…].
        val trunk = other.variants.first { it.read == "cmd:trunk" }
        assertEquals(true, Car3dNames.visible(trunk, "trunk_open", open = true)); assertEquals(false, Car3dNames.visible(trunk, "trunk_paint", open = true))
        assertTrue(Car3dNames.matches("*_lamp_#*", "trunk_lamp_16_2")); assertFalse(Car3dNames.matches("*_lamp_#*", "doorlf_lamp_turnleft10off"))
    }

    @Test
    fun `37 tep car mesh that cua AutoVideo - chi chay khi co KACHI_AVM_ROOT`() {
        val root = System.getenv("KACHI_AVM_ROOT")?.let(::File)
        assumeTrue(root != null && root.isDirectory, "không có KACHI_AVM_ROOT — bỏ qua (repo không chứa byte BYD)")
        val dirs = root!!.walkTopDown().filter { File(it, "car.mesh").isFile }.sortedBy { it.path }.toList()
        val bad = ArrayList<String>()
        for (d in dirs) {
            val rel = d.relativeTo(root).path
            val group = rel.substringBefore('/')
            val m = Car3dCatalog.forAvm(group)
            val raw = BydAvmMesh.parse(File(d, "car.mesh").readBytes()) + BydAvmMesh.parse(File(d, "wheel.mesh").readBytes())
            // Cùng đường nạp của `Car3dModelSource`: bỏ bộ phận gộp → kiểm hợp lý → nắp cốp.
            val dupes = Car3dDupes.aggregates(raw)
            val mesh = Car3dDupes.without(raw, dupes)
            val v = Car3dSanity.check(mesh, m)
            val names = mesh.parts.map { it.name }
            val dots = m.hotspots.count { Car3dSanity.resolves(it.anchor, names) }
            val trunkOpen = names.any { it.startsWith("trunk") && it.contains("open") }
            val lid = Car3dLid.of(mesh)
            val trunk = when { trunkOpen -> "pair"; lid == null -> "none"; names.any { it.startsWith("trunk") } -> "named-hinge"; else -> "cut ${lid.lid.sumOf { it.indexCount } / 3} tri" }
            val wheels = v.notes.count { Regex("^(FL|FR|RL|RR) ").containsMatchIn(it) }
            println("CAR3D-ROW|$rel|${mesh.parts.size}|${if (v.ok) "OK" else "FAIL"}|${v.failures.joinToString("; ")}|${v.notes.joinToString("; ")}|dots $dots/${m.hotspots.size}|trunk $trunk|wheels $wheels/4|dupes ${dupes.size}")
            if (!v.ok) bad += rel
        }
        assertTrue(dirs.size >= 30, "thấy ${dirs.size} thư mục")
        assertEquals(listOf("sc2e/CarModel/bev"), bad, "chỉ sc2e (không bộ phận cửa) được trượt")
    }
}
