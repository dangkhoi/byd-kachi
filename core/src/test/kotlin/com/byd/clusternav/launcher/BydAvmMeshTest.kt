package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 3.01 R25 — bộ đọc `car.mesh` AVM + bộ dựng `.glb`, trên tệp TỔNG HỢP dựng tại chỗ theo mô tả định dạng (repo không
 * chứa byte nào của BYD). Khoá: hai cách hiểu `blockSize` đều đọc đúng; mọi chỗ không khớp đều NÉM (không dựng hình sai
 * lặng lẽ); GLB đúng khuôn glTF 2.0 nhị phân, mỗi bộ phận một nút có TÊN, kính trong suốt.
 */
class BydAvmMeshTest {

    private class Block(val kind: Int, val name: String, val material: String, val count: Int, val body: ByteArray, val pivot: FloatArray = FloatArray(3))

    private fun floats(vararg f: Float) = ByteBuffer.allocate(f.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { f.forEach { putFloat(it) } }.array()
    private fun ints(vararg i: Int) = ByteBuffer.allocate(i.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { i.forEach { putInt(it) } }.array()

    private fun buffer(name: String, n: Int): Block {
        val pos = FloatArray(n * 3) { (it % 3 + it / 3).toFloat() }
        val uv = FloatArray(n * 3) { if (it % 3 == 2) 0f else 0.5f }
        val nrm = FloatArray(n * 3) { if (it % 3 == 1) 1f else 0f }
        return Block(1, name, name, n, floats(*pos) + floats(*uv) + floats(*nrm))
    }

    private fun part(name: String, buf: String, vararg idx: Int) = Block(2, name, buf, idx.size, ints(*idx))

    /** [sizeIncludesHeader] = cách hiểu `blockSize`; [sizeDelta] làm hỏng có chủ đích. */
    private fun file(blocks: List<Block>, sizeIncludesHeader: Boolean = true, sizeDelta: Int = 0): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (b in blocks) {
            val h = ByteBuffer.allocate(BydAvmMesh.HEADER).order(ByteOrder.LITTLE_ENDIAN)
            h.putInt((if (sizeIncludesHeader) BydAvmMesh.HEADER + b.body.size else b.body.size) + sizeDelta).putInt(b.kind)
            h.put(b.name.toByteArray().copyOf(32)).put(b.material.toByteArray().copyOf(32))
            h.putFloat(b.pivot[0]).putFloat(b.pivot[1]).putFloat(b.pivot[2]).putInt(b.count)
            out.write(h.array()); out.write(b.body)
        }
        return out.toByteArray()
    }

    private val sample = listOf(
        buffer("paint", 4), buffer("glass", 3),
        part("doorlf_paint", "paint", 0, 1, 2, 0, 2, 3), part("doorlf_glass", "glass", 0, 1, 2),
    )

    @Test
    fun `doc dung ca hai cach hieu blockSize`() {
        for (inc in listOf(true, false)) {
            val m = BydAvmMesh.parse(file(sample, inc))
            assertEquals(listOf("paint", "glass"), m.buffers.map { it.name })
            assertEquals(4, m.buffer("paint")!!.count)
            assertEquals(8, m.buffer("paint")!!.uvs.size)   // uv 3 thành phần → bỏ z
            assertEquals(0.5f, m.buffer("paint")!!.uvs[1])
            assertEquals(1f, m.buffer("glass")!!.normals[1])
            assertEquals(listOf("doorlf_paint", "doorlf_glass"), m.parts.map { it.name })
            assertEquals("paint", m.parts[0].buffer)
        }
    }

    @Test
    fun `khong khop thi nem, khong dung hinh sai lang le`() {
        assertThrows(BydAvmMesh.Malformed::class.java) { BydAvmMesh.parse(file(sample, sizeDelta = 8)) }
        assertThrows(BydAvmMesh.Malformed::class.java) { BydAvmMesh.parse(file(listOf(buffer("paint", 3), part("p", "paint", 0, 1, 7)))) }
        assertThrows(BydAvmMesh.Malformed::class.java) { BydAvmMesh.parse(file(listOf(part("p", "khong_co", 0, 1, 2)))) }
        assertThrows(BydAvmMesh.Malformed::class.java) { BydAvmMesh.parse(file(listOf(buffer("paint", 3), part("p", "paint", 0, 1)))) }
        assertThrows(BydAvmMesh.Malformed::class.java) { BydAvmMesh.parse(file(sample).copyOf(200)) }
        // Kind lạ có kích thước hợp lệ ⇒ bỏ qua, đếm lại.
        val unknown = Block(7, "x", "x", 0, ByteArray(16))
        assertEquals(1, BydAvmMesh.parse(file(sample + unknown)).skipped)
    }

    @Test
    fun `glb dung khuon glTF nhi phan, moi bo phan mot nut co ten`() {
        val m = BydAvmMesh.parse(file(sample))
        val glb = Car3dGlb.bytes(Car3dGlb.write(m, Car3dCatalog.BYD_AVM.materials, mapOf("car_texture.png" to ByteArray(5))))
        val b = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, b.getInt(0)); assertEquals(2, b.getInt(4)); assertEquals(glb.size, b.getInt(8))
        val jLen = b.getInt(12); assertEquals(0x4E4F534A, b.getInt(16)); assertEquals(0, jLen % 4)
        val json = String(glb, 20, jLen, Charsets.UTF_8)
        assertEquals(0x004E4942, b.getInt(20 + jLen + 4))
        assertEquals(glb.size, 20 + jLen + 8 + b.getInt(20 + jLen))
        assertTrue(json.contains("\"name\":\"doorlf_paint\"") && json.contains("\"name\":\"doorlf_glass\""), json)
        assertTrue(json.contains("\"children\":[1,2]"), json)
        assertTrue(json.contains("\"alphaMode\":\"BLEND\""), "kính trong suốt")
        assertTrue(json.contains("\"min\":[0.0,1.0,2.0]"), json)   // accessor POSITION có min/max (glTF bắt buộc)
        assertTrue(!json.contains("\"images\""), "ảnh không ai dùng thì không nhúng")
    }

    /** Khối kind 0 kiểu `wheel.mesh` [ĐO]: 10 f32/đỉnh, toạ độ tương đối pivot, không chỉ số. */
    private fun soup(name: String, n: Int, pivot: FloatArray): Block {
        val pos = FloatArray(n * 3) { if (it % 3 == 1) 0.35f else 0f }
        return Block(0, name, "texture", n, floats(*pos) + floats(*FloatArray(n * 3)) + floats(*FloatArray(n * 3) { if (it % 3 == 2) 1f else 0f }) + floats(*FloatArray(n)), pivot)
    }

    @Test
    fun `luoi roi kind 0 cua banh xe - cong pivot, thanh bo dinh va bo phan cung ten`() {
        val w = BydAvmMesh.parse(file(listOf(soup("wheellf_texture", 3, floatArrayOf(1.5f, 0.36f, -0.78f)))))
        val b = w.buffer("wheellf_texture")!!
        assertEquals(3, b.count); assertEquals(1.5f, b.positions[0]); assertEquals(0.71f, b.positions[1], 1e-6f); assertEquals(-0.78f, b.positions[2])
        assertEquals(listOf(0, 1, 2), w.parts.single().indices.toList())
        assertThrows(BydAvmMesh.Malformed::class.java) { BydAvmMesh.parse(file(listOf(soup("w", 4, FloatArray(3))))) }
        // Gộp thân + bánh; trùng tên bộ đỉnh thì ném.
        val all = BydAvmMesh.parse(file(sample)) + w
        assertEquals(3, all.buffers.size); assertEquals(3, all.parts.size)
        assertThrows(BydAvmMesh.Malformed::class.java) { all + w }
    }

    @Test
    fun `bo phan vuot bo dinh khai bao thi tim bo dinh trung ten doan, khong thi nem`() {
        // [ĐO Seal] `doorlf_open` khai `texture1` nhưng chỉ số thuộc bộ đỉnh `open`.
        val m = BydAvmMesh.parse(file(listOf(buffer("texture1", 3), buffer("open", 6), part("doorlf_open", "texture1", 3, 4, 5))))
        assertEquals("open", m.parts.single().buffer)
        assertThrows(BydAvmMesh.Malformed::class.java) {
            BydAvmMesh.parse(file(listOf(buffer("texture1", 3), buffer("open", 4), part("doorlf_open", "texture1", 3, 4, 5))))
        }
    }

    @Test
    fun `glb - moi bo phan khoi dinh rieng da danh lai chi so, den sang co vat lieu rieng`() {
        // [ĐO máy ảo 13/10] bản chung-accessor làm gltfio nhân bản cả bộ đỉnh cho TỪNG bộ phận ⇒ +403 MB. Khoá: bộ phận chỉ mang
        // đỉnh nó dùng (buffer 6 đỉnh, bộ phận dùng 3,4,5 ⇒ khối 3 đỉnh, chỉ số 0,1,2), bộ đỉnh không ai dùng không vào tệp.
        val m = BydAvmMesh.parse(file(listOf(buffer("lamp", 6), buffer("open", 3), part("body_lamp_3", "lamp", 3, 4, 5), part("body_lamp_3on", "lamp", 3, 4, 5))))
        // Vai đèn (trắng/đỏ/hổ phách, tắt/sáng) suy bằng ĐO ([Car3dLamps]) nối sau bảng tường minh — như `Car3dModelSource`.
        val glb = Car3dGlb.write(m, Car3dCatalog.BYD_AVM.materials, emptyMap(),
            Car3dCatalog.BYD_AVM.partStyles + Car3dLamps.styles(m, Car3dCatalog.BYD_AVM.whiteLamps))
        val b = Car3dGlb.bytes(glb); val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val jLen = bb.getInt(12)
        val json = String(b, 20, jLen, Charsets.UTF_8)
        val pos = Regex(""""count":(\d+),"type":"VEC3","min"""").findAll(json).map { it.groupValues[1].toInt() }.toList()
        assertEquals(listOf(3, 3), pos, "mỗi bộ phận 3 đỉnh riêng, bộ `open` không ghi")
        val binLen = bb.getInt(20 + jLen)
        assertEquals(2 * (3 * 12 + 3 * 12 + 3 * 8 + 3 * 4), binLen, "BIN = 2 × (vị trí + pháp tuyến + UV + chỉ số)")
        // Chỉ số đã đánh lại 0,1,2 (khối chỉ số là đoạn cuối của mỗi bộ phận).
        val bin = 20 + jLen + 8
        assertEquals(listOf(0, 1, 2), (0..2).map { bb.getInt(bin + 3 * 12 + 3 * 12 + 3 * 8 + it * 4) })
        assertTrue(json.contains("\"extras\":{\"bbox\":["), "hộp riêng của bộ phận trong extras")
        assertTrue(json.contains("\"name\":\"lamp_on\"") && json.contains("\"emissiveFactor\""), json)
        assertTrue(json.contains("\"name\":\"lamp\""), json)
    }

    @Test
    fun `uv goc day anh cua AVM thi lat v, anh co bo loc mipmap`() {
        // [ĐO 13/10] mặt ngoài bánh Seal ánh xạ tâm mâm v = 0,72 (gốc ĐÁY) — mâm trong ảnh ở 0,28 từ ĐỈNH. Không lật ⇒ mâm lệch.
        fun uvBytes(flip: Boolean): Pair<String, List<Float>> {
            val uv = FloatArray(9) { if (it % 3 == 0) 0.6f else if (it % 3 == 1) 0.72f else 0f }
            val blk = Block(1, "texture", "texture", 3, floats(*FloatArray(9) { it.toFloat() }) + floats(*uv) + floats(*FloatArray(9)))
            val m = BydAvmMesh.parse(file(listOf(blk, part("wheellf_texture", "texture", 0, 1, 2))))
            val b = Car3dGlb.bytes(Car3dGlb.write(m, Car3dCatalog.BYD_AVM.materials, mapOf("car_texture.png" to ByteArray(4)), flipV = flip))
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN); val jLen = bb.getInt(12)
            val json = String(b, 20, jLen, Charsets.UTF_8)
            // Khối UV đứng sau vị trí (3×12) + pháp tuyến (3×12) của bộ phận đầu (ảnh vật liệu ghi SAU dữ liệu bộ phận).
            val at = 20 + jLen + 8 + 36 + 36
            return json to (0..5).map { bb.getFloat(at + it * 4) }
        }
        val (j0, raw) = uvBytes(false); val (j1, flipped) = uvBytes(true)
        assertEquals(0.72f, raw[1], 1e-6f); assertEquals(0.28f, flipped[1], 1e-6f); assertEquals(0.6f, flipped[0], 1e-6f)
        listOf(j0, j1).forEach { assertTrue(it.contains("\"samplers\":[{\"magFilter\":9729,\"minFilter\":9987"), it) }
        assertTrue(Car3dCatalog.BYD_AVM.uvOriginBottom, "lưới AVM: gốc UV ở đáy ảnh")
    }

    @Test
    fun `ten bo phan la du lieu ngoai - thoat ky tu JSON`() {
        assertEquals("\"a\\\"b\\\\c\\u0001\"", Car3dGlb.q("a\"b\\c\u0001"))
    }
}
