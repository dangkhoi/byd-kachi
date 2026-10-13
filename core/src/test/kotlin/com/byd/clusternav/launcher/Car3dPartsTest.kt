package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 3.01 R25 hoàn thiện — ba phép ĐO trên lưới AVM lúc nạp (lưới TỔNG HỢP, repo không chứa byte BYD):
 *  • [Car3dDupes] khoá lỗi owner 13/10 "cửa mở màu đen/sai màu, kính cửa mở khác": `doorlf_open` của Seal là HỢP hình học của
 *    cánh cửa mở (sơn + kính + ốp) vẽ bằng ảnh cửa ⇒ lớp đen trùng mặt. Đèn chạy (`rear` ⊇ `rear0on..`) KHÔNG được coi là gộp.
 *  • [Car3dLid] khoá lỗi "đã gửi `voiceCtlBackDoor [1]` mà nắp cốp đứng yên": Seal không có bộ phận cốp ⇒ cắt nắp từ thân.
 *  • [Car3dLamps] khoá lỗi "đèn đuôi là dải trắng cháy": đèn ở phần tư sau ⇒ đỏ; xi-nhan ⇒ hổ phách; đèn lùi ⇒ trắng.
 */
class Car3dPartsTest {

    private fun block(kind: Int, name: String, mat: String, count: Int, body: ByteArray): ByteArray {
        val h = ByteBuffer.allocate(BydAvmMesh.HEADER).order(ByteOrder.LITTLE_ENDIAN)
        h.putInt(BydAvmMesh.HEADER + body.size).putInt(kind).put(name.toByteArray().copyOf(32)).put(mat.toByteArray().copyOf(32))
        h.putFloat(0f).putFloat(0f).putFloat(0f).putInt(count)
        return h.array() + body
    }

    private fun le(n: Int, f: ByteBuffer.() -> Unit) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).apply(f).array()

    /** Bộ đỉnh [name] = các tam giác [tris] (mỗi tam giác 3 điểm), trả kèm khối bộ đỉnh. */
    private fun buf(name: String, pts: List<FloatArray>): ByteArray {
        val n = pts.size
        val body = le(n * 36) {
            pts.forEach { p -> p.forEach { putFloat(it) } }
            repeat(n * 3) { putFloat(0f) }
            repeat(n) { putFloat(0f); putFloat(1f); putFloat(0f) }
        }
        return block(1, name, name, n, body)
    }

    private fun part(name: String, mat: String, idx: List<Int>) = block(2, name, mat, idx.size, le(idx.size * 4) { idx.forEach { putInt(it) } })

    private fun tri(x: Float, y: Float, z: Float, s: Float = 0.1f) = listOf(floatArrayOf(x, y, z), floatArrayOf(x + s, y, z), floatArrayOf(x, y + s, z))

    @Test
    fun `bo phan gop (hop cua canh cua mo nhieu vat lieu) bi bo, den chay va ban sao giu`() {
        val a = tri(0.5f, 0.8f, -1.3f); val b = tri(0.5f, 1.2f, -1.3f); val c = tri(-2.0f, 0.9f, -0.6f); val d = tri(-2.0f, 0.95f, -0.5f)
        val bytes = buf("paint", a) + buf("glass", b) + buf("open", a + b) + buf("lamp", c + d + c + d) +
            part("doorlf_paint_open", "paint", listOf(0, 1, 2)) + part("doorlf_glass_open", "glass", listOf(0, 1, 2)) +
            part("doorlf_open", "texture1", listOf(0, 1, 2, 3, 4, 5)) +
            // Cụm đèn chạy: bản TẮT chứa hai đoạn sáng (cùng vật liệu lamp) + hai bản sao giống hệt nhau.
            part("body_lamp_turnrightrear", "lamp", listOf(0, 1, 2, 3, 4, 5)) +
            part("body_lamp_turnrightrear0on", "lamp", listOf(6, 7, 8)) + part("body_lamp_turnrightrear1on", "lamp", listOf(9, 10, 11)) +
            part("body_lamp_15", "lamp", listOf(0, 1, 2)) + part("body_lamp_15on", "lamp", listOf(0, 1, 2))
        val mesh = BydAvmMesh.parse(bytes)
        assertEquals("open", mesh.parts.first { it.name == "doorlf_open" }.buffer)
        val dupes = Car3dDupes.aggregates(mesh)
        assertEquals(setOf("doorlf_open"), dupes)
        val kept = Car3dDupes.without(mesh, dupes).parts.map { it.name }
        assertFalse("doorlf_open" in kept); assertTrue("doorlf_paint_open" in kept && "body_lamp_turnrightrear" in kept && "body_lamp_15on" in kept)
    }

    private fun quad(vararg p: Float): List<FloatArray> = (0 until 4).map { i -> floatArrayOf(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]) }
    private fun quads(from: Int, n: Int): List<Int> = (0 until n).flatMap { q -> listOf(0, 1, 2, 0, 2, 3).map { from + q * 4 + it } }

    /**
     * Thân sedan tối giản (+X đầu xe), dựng theo hình học ĐO trên lưới Seal (POLISH2): nắp THẬT = hai tấm sơn RỜI (mặt trên y 1,0 ·
     * mặt sau y 0,65–0,9) ngăn bởi dải đèn đuôi; đoạn giữa dải đèn nằm trên nắp, đoạn hông nằm trên sườn; cản sau sát đất.
     */
    private fun sedan(trunkParts: Boolean = false, trunkOpen: Boolean = false): BydAvmMesh {
        val paint = quad(2.2f, .5f, -.5f, 2.2f, .5f, .5f, 2.2f, .8f, .5f, 2.2f, .8f, -.5f) +                 // Q0 đầu xe
            quad(-.5f, .5f, .9f, -2.2f, .5f, .9f, -2.2f, .9f, .9f, -.5f, .9f, .9f) +                          // Q1 sườn sau
            quad(-2.35f, .3f, -.9f, -2.35f, .3f, .9f, -2.35f, .6f, .9f, -2.35f, .6f, -.9f) +                  // Q2 cản sau
            quad(-2.25f, 1f, -.6f, -1.9f, 1f, -.6f, -1.9f, 1f, .6f, -2.25f, 1f, .6f) +                        // Q3 mặt trên nắp
            quad(-2.3f, .65f, -.6f, -2.3f, .65f, .6f, -2.3f, .9f, .6f, -2.3f, .9f, -.6f)                      // Q4 mặt sau nắp
        val glass = listOf(floatArrayOf(1.0f, 1.3f, 0f), floatArrayOf(-1.9f, 1.0f, 0f), floatArrayOf(0f, 1.45f, 0.3f))
        val lamp = quad(-2.31f, .86f, -.55f, -2.31f, .86f, .55f, -2.31f, .95f, .55f, -2.31f, .95f, -.55f) +   // đoạn giữa (trên nắp)
            quad(-2.2f, .86f, .62f, -2.2f, .86f, .85f, -2.2f, .95f, .85f, -2.2f, .95f, .62f)                  // đoạn hông (trên sườn)
        val wheel = tri(1.5f, 0.0f, -0.8f) + tri(-1.4f, 0.0f, 0.8f)
        var b = buf("paint", paint) + buf("glass", glass) + buf("lamp", lamp) + buf("black", wheel)
        b += if (trunkParts) part("body_paint", "paint", quads(0, 3)) + part("trunk_paint", "paint", quads(12, 2))
        else part("body_paint", "paint", quads(0, 5))
        if (trunkOpen) b += part("trunk_paint_open", "paint", quads(12, 2))
        b += part("body_glass", "glass", listOf(0, 1, 2)) + part("body_lamp_15", "lamp", quads(0, 2)) + part("wheelr_texture", "black", (0 until 6).toList())
        return BydAvmMesh.parse(b)
    }

    @Test
    fun `nap cop tach THEO KHE TAM - ca mat tren lan mat sau, doan den giua theo nap, ban le o mep truoc, co khoang`() {
        val r = Car3dLid.of(sedan()).also { assertNotNull(it) }!!
        val lid = r.lid.toList()
        assertEquals(setOf("body_paint", "body_lamp_15"), lid.map { it.name }.toSet(), "phần tách giữ TÊN gốc ⇒ biến thể sơn/đèn theo tên vẫn áp")
        assertEquals(12, lid.filter { it.name == "body_paint" }.sumOf { it.indexCount }, "CẢ hai tấm nắp (4 tam giác) — không cắt đôi (owner 13/10)")
        assertEquals(6, lid.filter { it.name == "body_lamp_15" }.sumOf { it.indexCount }, "chỉ đoạn đèn GIỮA nằm trên nắp")
        assertEquals(30, r.mesh.parts.filter { it.name == "body_paint" }.sumOf { it.indexCount }, "không mất tam giác nào")
        assertEquals(12, r.mesh.parts.filter { it.name == "body_lamp_15" }.sumOf { it.indexCount }, "đoạn đèn hông ở lại thân")
        assertEquals(-1.9, r.hinge.pivot.x, 0.02); assertEquals(1.0, r.hinge.pivot.y, 0.02)
        val bottom = V3(-2.3, 0.65, 0.0)
        assertTrue(r.hinge.apply(bottom, 1f).y > 1.0, "mở hết: MÉP DƯỚI mặt sau nắp cũng nhấc lên (không chỉ nửa trên)")
        assertEquals(bottom.y, r.hinge.apply(bottom, 0f).y, 1e-6)
        val cav = r.mesh.parts.single { it.name == Car3dLid.CAVITY_PART }
        assertEquals(Car3dLid.CAVITY, cav.material); assertFalse(cav in r.lid, "khoang đứng yên, không theo nắp")
        // [ĐO máy ảo 13/10] sàn quấn ngược pháp tuyến ⇒ Filament (hai mặt) lật pháp tuyến ⇒ sàn ĐEN. Mọi tam giác khoang: thứ tự
        // đỉnh (CCW) cùng chiều pháp tuyến khai.
        val cb = r.mesh.buffer(cav.buffer)!!
        fun p(i: Int) = V3(cb.position(i, 0).toDouble(), cb.position(i, 1).toDouble(), cb.position(i, 2).toDouble())
        for (t in 0 until cav.indexCount / 3) {
            val n = V3(cb.normal(t * 3, 0).toDouble(), cb.normal(t * 3, 1).toDouble(), cb.normal(t * 3, 2).toDouble())
            assertTrue((p(t * 3 + 1) - p(t * 3)).cross(p(t * 3 + 2) - p(t * 3)).dot(n) > 0, "tam giác khoang $t quấn ngược pháp tuyến")
        }
        assertTrue(Car3dLid.CAVITY in Car3dCatalog.BYD_AVM.materials, "khoang có kiểu vật liệu xám (không đen phẳng)")
    }

    @Test
    fun `nap theo trunk_ cua tep - co ban mo thi de cap dong mo, khong co ban mo thi ban le tong hop`() {
        assertNull(Car3dLid.of(sedan(trunkParts = true, trunkOpen = true)), "có trunk*open* ⇒ bản lề khớp từ cặp đóng/mở")
        val r = Car3dLid.of(sedan(trunkParts = true))!!
        assertEquals(listOf("trunk_paint"), r.lid.map { it.name })
        assertTrue(r.hinge.angle < 0)
        assertTrue(r.mesh.parts.any { it.name == Car3dLid.CAVITY_PART })
    }

    @Test
    fun `vai den theo vi tri - duoi do, xi nhan ho phach, den lui trang, truoc trang, ban tat ban sang dung`() {
        val f = tri(2.2f, 0.7f, 0.6f); val r = tri(-2.3f, 0.9f, 0.6f); val mid = tri(0f, 0.5f, 0f); val far = tri(-2.3f, 0.2f, 0f) + tri(2.3f, 0.2f, 0f)
        val bytes = buf("lamp", f + r + mid) + buf("paint", far) + part("body_paint", "paint", (0 until 6).toList()) +
            part("body_lamp_3", "lamp", listOf(0, 1, 2)) + part("body_lamp_3on", "lamp", listOf(0, 1, 2)) +
            part("body_lamp_15", "lamp", listOf(3, 4, 5)) + part("body_lamp_15on", "lamp", listOf(3, 4, 5)) +
            part("body_lamp_17on", "lamp", listOf(3, 4, 5)) + part("trunk_lamp_reverson", "lamp", listOf(3, 4, 5)) +
            part("body_lamp_turnleftrear0on", "lamp", listOf(3, 4, 5)) + part("body_lamp_logo", "lamp", listOf(6, 7, 8)) +
            part("trunk_lamp_position", "lamp", listOf(3, 4, 5)) + part("trunk_lamp_positionon", "lamp", listOf(3, 4, 5))
        val m = BydAvmMesh.parse(bytes)
        val s = Car3dLamps.styles(m, Car3dCatalog.BYD_AVM.whiteLamps + "body_lamp_17*").toMap()
        assertEquals(Car3dLamps.WHITE, s["body_lamp_3"]); assertEquals(Car3dLamps.WHITE_ON, s["body_lamp_3on"])
        assertEquals(Car3dLamps.REAR, s["body_lamp_15"]); assertEquals(Car3dLamps.REAR_ON, s["body_lamp_15on"])
        assertEquals(Car3dLamps.WHITE_ON, s["body_lamp_17on"], "đèn lùi (bảng nhóm) trắng"); assertEquals(Car3dLamps.WHITE_ON, s["trunk_lamp_reverson"])
        assertEquals(Car3dLamps.TURN_ON, s["body_lamp_turnleftrear0on"]); assertEquals(Car3dLamps.WHITE, s["body_lamp_logo"])
        assertEquals(Car3dLamps.REAR, s["trunk_lamp_position"], "`position` tận cùng 'on' nhưng có `positionon` ⇒ bản TẮT")
        assertEquals(Car3dLamps.REAR_ON, s["trunk_lamp_positionon"])
        // Vật liệu: đèn sáng phát sáng VỪA PHẢI (không ×8 trắng) và đỏ/hổ phách thật.
        val mats = Car3dCatalog.BYD_AVM.materials
        val rear = mats.getValue(Car3dLamps.REAR_ON)
        assertTrue(rear.emissive!![0] > 5 * rear.emissive!![1] && rear.emissiveStrength <= 6.0)
        assertTrue(mats.getValue(Car3dLamps.REAR).rgba[0] > 4 * mats.getValue(Car3dLamps.REAR).rgba[1], "đèn đuôi tắt = đỏ sẫm")
        assertTrue(listOf(Car3dLamps.WHITE_ON, Car3dLamps.TURN_ON, Car3dLamps.REAR_ON).all { mats.getValue(it).texture == "car_lamp.png" })
    }

    @Test
    fun `GLB - phat sang nhan anh den, nap cop mang ban le va nhom`() {
        val mesh = sedan()
        val lid = Car3dLid.of(mesh)!!
        val styles = Car3dCatalog.BYD_AVM.materials
        val glb = Car3dGlb.write(lid.mesh, styles, mapOf("car_lamp.png" to byteArrayOf(1, 2, 3, 4)),
            listOf("body_lamp_15" to Car3dLamps.REAR_ON), lid = lid)
        val json = String(Car3dGlb.bytes(glb), Charsets.ISO_8859_1)
        assertTrue(Regex(""""emissiveFactor":\[[^]]*],"emissiveTexture":\{"index":0}""").containsMatchIn(json), "phát sáng × ảnh đèn")
        val lidNodes = Regex(""""name":"body_paint","mesh":\d+,"extras":\{[^}]*}""").findAll(json).map { it.value }.toList()
        assertEquals(2, lidNodes.size)
        assertEquals(1, lidNodes.count { it.contains(""""grp":"trunk"""") && it.contains(""""hinge":[""") })
        assertEquals("trunk", Car3dBox.group(lidNodes.first { it.contains("grp") }.substringAfter("\"extras\":")))
    }

    @Test
    fun `Seal - xi nhan sang thi phan ban ngay trung cho tat, xi nhan tat thi DRL hien lai`() {
        val m = Car3dCatalog.forAvm("eke")
        fun shown(name: String, drl: Boolean, turn: Boolean) = Car3dNames.visibleAll(m.variants, m.variants.map { v ->
            v.read.split('|').any { (it == "light_drl" && drl) || (it == "light_left_turn" && turn) }
        }, name)
        assertEquals(true, shown("body_lamp_turnleftfront4on", drl = true, turn = false))
        assertEquals(false, shown("body_lamp_turnleftfront4on", drl = true, turn = true), "trắng không đè hổ phách")
        assertEquals(true, shown("body_lamp_turnleftfronton", drl = true, turn = true))
    }
}
