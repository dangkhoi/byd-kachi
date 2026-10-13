package com.byd.clusternav.launcher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ═══ 3.01 R25 — bộ đọc lưới `car.mesh` của app AVM BYD (`com.byd.avc`) — thuần, không chứa byte nào của BYD ═══════
 *
 * Định dạng [ĐO 13/10 — đọc bằng CHÍNH bộ đọc này trên `car.mesh`/`wheel.mesh` thật của Seal (eke), bộ chạy thử ngoài
 * repo: 35 479 288 B ⇒ 11 bộ đỉnh + 95 bộ phận, đọc hết đúng tới byte cuối]. Không khớp thì NÉM (fail-loud):
 *
 *  khối nối tiếp: `u32 blockSize · u32 kind · char name[32] · char material[32] · f32 pivot[3] · u32 count` + thân
 *   • kind 1 — bộ đỉnh của MỘT vật liệu: thân = `pos[count·3] · attr[count·3] · normal[count·3]` (f32, mảng-cấu-trúc).
 *     `attr` là `aTextureCoord vec3` trong shader của `libVehiclePano.so` — UV (z = 0) với vật liệu có ảnh, tham số màu
 *     với vật liệu trơn ⇒ ta dùng 2 thành phần đầu làm UV;
 *   • kind 2 — một BỘ PHẬN có tên: thân = `u32 index[count]` trỏ vào bộ đỉnh có tên = `material` của khối; toạ độ TUYỆT ĐỐI
 *     (pivot chỉ là bản lề/tâm, không cộng);
 *   • kind 0 — lưới RỜI không chỉ số (`wheel.mesh`): thân = `pos[n·3] · attr[n·3] · normal[n·3] · f32 extra[n]` (10 f32/đỉnh,
 *     `extra` toàn 0 [ĐO]); toạ độ TƯƠNG ĐỐI pivot (bánh ±0,353 quanh tâm trục [ĐO]) ⇒ [Buffer.offset] = pivot; thành bộ đỉnh
 *     + bộ phận KHÔNG chỉ số cùng tên.
 *  `blockSize` [ĐO] tính cả đầu khối (88 + thân); vẫn nhận cách "chỉ thân" cho tệp đời khác, khác cả hai ⇒ ném [Malformed].
 *  Kind lạ có `blockSize` ≥ đầu khối ⇒ bỏ qua (đếm vào [BydAvmMesh.skipped]). Thứ tự byte: little-endian (ARM).
 *
 * ## Không chép đỉnh vào heap Java
 * [ĐO máy ảo 13/10] heap tối đa của tiến trình launcher = 48 MB (`growth limit 50331648`): bản đầu chép 35 MB đỉnh ra
 * `FloatArray` ⇒ `OutOfMemoryError` (widget lùi 2D đúng như thiết kế, nhưng không bao giờ hiện 3D). Nay [Buffer]/[Part] chỉ
 * là CỬA SỔ (vị trí + số lượng) trên chính đệm nguồn (`:app` đọc tài sản vào đệm TRỰC TIẾP — bộ nhớ native); [Car3dGlb] chép
 * thẳng từ đệm nguồn sang đệm kết quả. Heap Java chỉ còn danh sách tên.
 */
class BydAvmMesh(val buffers: List<Buffer>, val parts: List<Part>, val skipped: Int) {

    /** Bộ đỉnh: [count] đỉnh trong [src] (LE) tại [posAt]/[attrAt]/[nrmAt]; [offset] cộng vào vị trí (lưới rời). */
    class Buffer internal constructor(
        val name: String, val pivot: V3, val count: Int,
        internal val src: ByteBuffer, internal val posAt: Int, internal val attrAt: Int, internal val nrmAt: Int,
        val offset: V3 = V3.ZERO,
    ) {
        fun position(i: Int, k: Int): Float = src.getFloat(posAt + (i * 3 + k) * 4) + offset.comp(k).toFloat()
        fun uv(i: Int, k: Int): Float = src.getFloat(attrAt + (i * 3 + k) * 4)
        fun normal(i: Int, k: Int): Float = src.getFloat(nrmAt + (i * 3 + k) * 4)
        /** Bản chép ra mảng — CHỈ cho test / lưới nhỏ (đừng gọi trên lưới thân xe). */
        val positions: FloatArray get() = FloatArray(count * 3) { position(it / 3, it % 3) }
        val uvs: FloatArray get() = FloatArray(count * 2) { uv(it / 2, it % 2) }
        val normals: FloatArray get() = FloatArray(count * 3) { normal(it / 3, it % 3) }
    }

    /** Bộ phận: [indexCount] chỉ số `u32` trong [src] tại [at]; [src] `null` = lưới rời (chỉ số ngầm 0..n−1). */
    class Part internal constructor(
        val name: String, val buffer: String, val pivot: V3, val indexCount: Int,
        internal val src: ByteBuffer?, internal val at: Int,
        /**
         * Vật liệu KHAI trong tệp (trường `material` của khối) — giữ nguyên kể cả khi [buffer] được sửa trỏ sang bộ đỉnh khác.
         * [ĐO máy ảo 13/10] owner: *"cửa mở kính bị trắng xoá"* — `doorlf_glass_open` khai `glass` nhưng chỉ số thuộc bộ đỉnh
         * `open`; tô theo tên bộ ĐỈNH ⇒ vật liệu `open` = SƠN trắng đục. Tô theo vật liệu khai ⇒ kính.
         */
        val material: String = buffer,
    ) {
        val indexed: Boolean get() = src != null
        fun index(i: Int): Int = src?.getInt(at + i * 4) ?: i
        /** Bản chép ra mảng — CHỈ cho test. */
        val indices: IntArray get() = IntArray(indexCount) { index(it) }
        internal fun withBuffer(b: String) = Part(name, b, pivot, indexCount, src, at, material)
    }

    class Malformed(msg: String) : IllegalArgumentException(msg)

    fun buffer(name: String): Buffer? = buffers.firstOrNull { it.name == name }

    /** Gộp hai lưới (thân + bánh) — tên trùng thì ném (bộ phận phải trỏ đúng một bộ đỉnh). */
    operator fun plus(o: BydAvmMesh): BydAvmMesh {
        val dup = buffers.map { it.name }.intersect(o.buffers.map { it.name }.toSet())
        if (dup.isNotEmpty()) throw Malformed("bộ đỉnh trùng tên khi gộp: $dup")
        return BydAvmMesh(buffers + o.buffers, parts + o.parts, skipped + o.skipped)
    }

    companion object {
        const val HEADER = 4 + 4 + 32 + 32 + 12 + 4
        const val KIND_BUFFER = 1
        const val KIND_PART = 2
        const val KIND_SOUP = 0
        private const val NAME = 32

        fun parse(bytes: ByteArray): BydAvmMesh = parse(ByteBuffer.wrap(bytes))

        /** Đọc từ [src] (vị trí → giới hạn). Kết quả GIỮ tham chiếu tới đệm này — đừng tái dùng nó khi còn dùng lưới. */
        fun parse(src: ByteBuffer): BydAvmMesh {
            val b = src.slice().order(ByteOrder.LITTLE_ENDIAN)
            val buffers = ArrayList<Buffer>(); val parts = ArrayList<Part>(); var skipped = 0
            while (b.remaining() > 0) {
                val at = b.position()
                if (b.remaining() < HEADER) throw Malformed("đầu khối cụt tại $at (còn ${b.remaining()} byte)")
                val size = b.int; val kind = b.int
                val name = cstr(b); val material = cstr(b)
                val pivot = V3(b.float.toDouble(), b.float.toDouble(), b.float.toDouble())
                val count = b.int
                if (count < 0) throw Malformed("count âm tại $at")
                val payload = when (kind) {
                    KIND_BUFFER -> count.toLong() * 9 * 4
                    KIND_PART -> count.toLong() * 4
                    KIND_SOUP -> count.toLong() * 10 * 4
                    else -> -1L
                }
                val body = at + HEADER
                val next: Long = when {
                    payload < 0 && size >= HEADER -> { skipped++; at.toLong() + size }
                    payload < 0 -> throw Malformed("kind $kind lạ với blockSize $size tại $at")
                    size.toLong() == HEADER + payload -> at.toLong() + size
                    size.toLong() == payload -> body + payload
                    else -> throw Malformed("blockSize $size không khớp thân $payload (kind $kind '$name') tại $at")
                }
                if (next > b.limit() || body + payload > b.limit()) throw Malformed("khối '$name' tràn tệp tại $at")
                val nm = name.ifEmpty { material }
                when (kind) {
                    KIND_BUFFER -> buffers += Buffer(nm, pivot, count, b, body, body + count * 12, body + count * 24)
                    KIND_PART -> parts += Part(name, material, pivot, count, b, body)
                    KIND_SOUP -> {
                        if (count % 3 != 0) throw Malformed("lưới rời '$name' có $count đỉnh (không chia hết 3)")
                        buffers += Buffer(nm, pivot, count, b, body, body + count * 12, body + count * 24, offset = pivot)
                        parts += Part(nm, nm, pivot, count, null, 0, material.ifEmpty { nm })
                    }
                }
                b.position(next.toInt())
            }
            val byName = buffers.associateBy { it.name }
            val fixed = parts.map { p ->
                fun fits(buf: Buffer?) = buf != null && (0 until p.indexCount).all { val i = p.index(it); i >= 0 && i < buf.count }
                val own = byName[p.buffer]
                when {
                    fits(own) -> p
                    // [ĐO Seal] `doorlf_open` khai vật liệu `texture1` (38 904 đỉnh) nhưng chỉ số tới 115 043 — trỏ bộ đỉnh
                    // `open` (128 160 đỉnh) mang đúng tên một đoạn của tên bộ phận. Quy tắc chung: không vừa bộ đỉnh khai báo
                    // ⇒ thử bộ đỉnh trùng một đoạn `_` của tên bộ phận mà VỪA; không có ⇒ ném.
                    else -> p.name.split('_').mapNotNull { byName[it] }.firstOrNull(::fits)?.let { p.withBuffer(it.name) }
                        ?: run {
                            val buf = own ?: throw Malformed("bộ phận '${p.name}' trỏ bộ đỉnh '${p.buffer}' không có")
                            val bad = (0 until p.indexCount).map(p::index).first { it < 0 || it >= buf.count }
                            throw Malformed("bộ phận '${p.name}' chỉ số $bad ngoài 0..${buf.count - 1}")
                        }
                }
            }
            for (p in fixed) if (p.indexCount % 3 != 0) throw Malformed("bộ phận '${p.name}' có ${p.indexCount} chỉ số (không chia hết 3)")
            return BydAvmMesh(buffers, fixed, skipped)
        }

        private fun cstr(b: ByteBuffer): String {
            val raw = ByteArray(NAME); b.get(raw)
            val end = raw.indexOf(0).let { if (it < 0) NAME else it }
            return String(raw, 0, end, Charsets.US_ASCII).trim()
        }
    }
}

internal fun V3.comp(i: Int) = when (i) { 0 -> x; 1 -> y; else -> z }
