package com.byd.clusternav.launcher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ═══ 3.01 R25 — dựng `.glb` (glTF 2.0 nhị phân) từ lưới AVM BYD đã đọc — thuần `:core` ═════════════════════════
 *
 * Vì sao dựng GLB trong bộ nhớ thay vì bộ đệm Filament trực tiếp: Filament cần vật liệu ĐÃ BIÊN DỊCH (`.filamat`); bộ nạp
 * glTF (`gltfio`) có sẵn ubershader PBR cho mọi vật liệu glTF ⇒ một đường vẽ duy nhất, không kéo `filamat` (vài MB) vào APK.
 *
 * Mỗi BỘ PHẬN thành một nút có TÊN (để bảng ràng buộc neo chấm / bật-tắt biến thể theo tên) với khối đỉnh RIÊNG — chỉ các đỉnh
 * nó dùng, chỉ số đánh lại. [ĐO máy ảo 13/10] bản đầu cho các bộ phận CHUNG accessor của bộ đỉnh vật liệu: gltfio dựng một
 * VertexBuffer đầy đủ cho TỪNG primitive ⇒ PSS launcher 118 → 521 MB. Hộp bao riêng của bộ phận ghi vào `extras.bbox` (hộp
 * Filament của primitive = min/max accessor) — bên vẽ neo chấm bằng nó ([Car3dBox]). Ảnh vật liệu nhúng thẳng vào BIN.
 *
 * Hai lượt, heap nhỏ: lượt 1 tính bố cục + JSON + bảng đánh lại chỉ số (vài MB `IntArray`); lượt 2 ghi từ đệm nguồn
 * ([BydAvmMesh], ánh xạ tệp) sang đệm kết quả do [write] `alloc` cấp (`:app` cấp tệp đệm ánh xạ) — trần heap 48 MB [ĐO].
 */
object Car3dGlb {

    private const val FLOAT = 5126
    private const val UINT = 5125
    private const val ARRAY_BUFFER = 34962
    private const val ELEMENT_BUFFER = 34963

    /**
     * @param textures tên ảnh ([Car3dMaterialStyle.texture]) → byte PNG; ảnh vắng ⇒ vật liệu không gắn ảnh.
     * @param partStyles (mẫu tên bộ phận → khoá kiểu trong [styles]) — mẫu khớp đầu tiên thắng; không khớp ⇒ kiểu theo vật
     *   liệu khai của bộ phận ([BydAvmMesh.Part.material]). Cho đèn "…on" một vật liệu phát sáng riêng mà vẫn dùng chung đỉnh
     *   với đèn tắt.
     * @param hinges tên bộ phận ĐÓNG → bản lề ([Car3dHinge]) ghi vào `extras.hinge` để bên vẽ diễn hoạt mở/đóng.
     * Bộ đỉnh không bộ phận nào trỏ tới thì KHÔNG ghi (lưới Seal: bộ `open` không ai dùng trước khi sửa chỉ số [ĐO]).
     * @param lid nắp cốp ([Car3dLid]): bộ phận NẮP (theo đối tượng — phần cắt giữ tên gốc) nhận bản lề chung + `extras.grp`.
     * @param texels ảnh → bộ lấy mẫu độ sáng (`:app` giải mã thu nhỏ): kiểu PHÁT SÁNG chỉ gắn ảnh khi ảnh sáng dưới UV của bộ phận
     *   ([Car3dMaterials.textured]); vắng ⇒ chỉ xét tệp có + UV thật.
     * @param flipV gốc UV của nguồn ở ĐÁY ảnh (quy ước GL + `stbi_set_flip_vertically_on_load` của AVM) ⇒ ghi `v' = 1 − v`
     *   (glTF đặt gốc ở ĐỈNH ảnh). Xem [Car3dModel.uvOriginBottom].
     */
    fun write(
        mesh: BydAvmMesh, styles: Map<String, Car3dMaterialStyle>, textures: Map<String, ByteArray> = emptyMap(),
        partStyles: List<Pair<String, String>> = emptyList(),
        flipV: Boolean = false,
        hinges: Map<String, Car3dHinge.Hinge> = emptyMap(),
        lid: Car3dLid.Result? = null,
        texels: Map<String, Car3dMaterials.Texels> = emptyMap(),
        alloc: (Int) -> ByteBuffer = ByteBuffer::allocate,
    ): ByteBuffer {
        val plan = ArrayList<(ByteBuffer) -> Unit>()
        var binSize = 0
        /** Đặt một đoạn [len] byte vào BIN (căn 4), [fill] ghi đúng [len] byte lúc lượt 2. Trả offset. */
        fun reserve(len: Int, fill: (ByteBuffer) -> Unit): Int {
            val off = binSize
            plan += { out -> fill(out); repeat(pad4(len) - len) { out.put(0) } }
            binSize += pad4(len)
            return off
        }
        val views = ArrayList<String>(); val accessors = ArrayList<String>()
        val materials = ArrayList<String>(); val matIndex = HashMap<String, Int>()
        var usesStrength = false
        var usesUnlit = false
        val images = ArrayList<String>(); val texturesJson = ArrayList<String>(); val texIndex = HashMap<String, Int>()

        fun view(off: Int, len: Int, target: Int): Int {
            views += """{"buffer":0,"byteOffset":$off,"byteLength":$len,"target":$target}"""
            return views.size - 1
        }

        // [plain] = bản trơn của kiểu (ảnh không gắn được cho bộ phận này — [Car3dMaterials.textured]); tên vật liệu glTF có hậu tố
        // [PLAIN], `extras.mat` của nút vẫn là [key].
        fun material(key: String, single: Boolean = false, plain: Boolean = false): Int =
            matIndex.getOrPut((if (plain) "$key$PLAIN" else key) + if (single) SINGLE else "") {
            val st0 = Car3dMaterials.style(key, styles)
            val st = if (plain) Car3dMaterials.plain(st0) else st0
            val name = (if (plain) "$key$PLAIN" else key) + if (single) SINGLE else ""
            val tex = st.texture?.let { name ->
                texIndex[name] ?: textures[name]?.let { png ->
                    val iOff = reserve(png.size) { out -> out.put(png) }
                    views += """{"buffer":0,"byteOffset":$iOff,"byteLength":${png.size}}"""
                    images += """{"bufferView":${views.size - 1},"mimeType":"image/png"}"""
                    texturesJson += """{"sampler":0,"source":${images.size - 1}}"""
                    (texturesJson.size - 1).also { texIndex[name] = it }
                }
            }
            val base = buildString {
                append("""{"baseColorFactor":[${st.rgba.joinToString(",")}],"metallicFactor":${st.metallic},"roughnessFactor":${st.roughness}""")
                if (tex != null) append(""","baseColorTexture":{"index":$tex}""")
                append("}")
            }
            val extra = buildString {
                if (st.blend) append(""","alphaMode":"BLEND"""")
                st.emissive?.let { append(""","emissiveFactor":[${it.joinToString(",")}]""") }
                // Phát sáng nhân CHÍNH ảnh đèn ⇒ hoa văn LED giữ nguyên, phần tối của ảnh không sáng (không thành dải trắng đặc).
                if (st.emissive != null && tex != null) append(""","emissiveTexture":{"index":$tex}""")
                val ext = ArrayList<String>()
                if (st.emissive != null && st.emissiveStrength != 1.0) {
                    ext += """"$EMISSIVE_STRENGTH":{"emissiveStrength":${st.emissiveStrength}}"""
                    usesStrength = true
                }
                // Không chiếu sáng ([Car3dMaterialStyle.unlit] — gltfio 1.77.3 hỗ trợ `KHR_materials_unlit` [ĐO chuỗi trong libgltfio-jni]).
                if (st.unlit) { ext += """"$UNLIT":{}"""; usesUnlit = true }
                if (ext.isNotEmpty()) append(""","extensions":{${ext.joinToString(",")}}""")
            }
            // Một mặt ([BydAvmMesh.Part.single], [Car3dCabin]): cùng kiểu, mặt sau bị loại — `extras.mat` của nút vẫn là [key] (bên vẽ tô sơn).
            materials += """{"name":${q(name)},"pbrMetallicRoughness":$base,"doubleSided":${!single}$extra}"""
            materials.size - 1
        }

        // Ảnh của kiểu PHÁT SÁNG tối dưới UV của CẢ lưới (SL6) ⇒ bỏ ảnh cho mọi bộ phận dùng nó; quyết MỘT lần cho cả lưới
        // ([Car3dMaterials.darkTextures]) — không theo từng bộ phận (Seal `body_lamp_15on` tối riêng phải giữ ảnh, QA 14/10).
        val dark = Car3dMaterials.darkTextures(mesh, styles, partStyles, { it in textures }, texels, flipV)
        val meshes = ArrayList<String>(); val nodes = ArrayList<String>(); val children = ArrayList<Int>()
        nodes += "" // chỗ của nút gốc
        for (p in mesh.parts) {
            if (p.indexCount == 0) continue
            val buf = mesh.buffer(p.buffer) ?: continue
            // MỖI bộ phận một khối đỉnh RIÊNG (chỉ đỉnh nó dùng, chỉ số đánh lại). [ĐO máy ảo 13/10] bản chia sẻ accessor làm
            // gltfio dựng một VertexBuffer ĐẦY ĐỦ cho từng primitive (bộ `paint` 214 k đỉnh × ~20 bộ phận…) ⇒ PSS +403 MB.
            val uniq: IntArray; val remap: IntArray?
            if (p.indexed) {
                val mark = IntArray(buf.count) { -1 }
                val order = IntArray(p.indexCount); var n = 0
                remap = IntArray(p.indexCount) { i -> val v = p.index(i); if (mark[v] < 0) { mark[v] = n; order[n++] = v }; mark[v] }
                uniq = order.copyOf(n)
            } else { uniq = IntArray(p.indexCount) { it }; remap = null }
            val n = uniq.size; val len = n * 12
            val mn = DoubleArray(3) { Double.MAX_VALUE }; val mx = DoubleArray(3) { -Double.MAX_VALUE }
            for (v in uniq) for (k in 0..2) { val x = buf.position(v, k).toDouble(); if (x < mn[k]) mn[k] = x; if (x > mx[k]) mx[k] = x }
            val pOff = reserve(len) { out -> for (v in uniq) for (k in 0..2) out.putFloat(buf.position(v, k)) }
            accessors += """{"bufferView":${view(pOff, len, ARRAY_BUFFER)},"componentType":$FLOAT,"count":$n,"type":"VEC3","min":[${mn.joinToString(",")}],"max":[${mx.joinToString(",")}]}"""
            val pos = accessors.size - 1
            val nSign = if (p.flip) -1f else 1f   // đảo mặt ⇒ đảo pháp tuyến (cùng ánh sáng như mặt sau của vật liệu hai mặt)
            val nOff = reserve(len) { out -> for (v in uniq) for (k in 0..2) out.putFloat(nSign * buf.normal(v, k)) }
            accessors += """{"bufferView":${view(nOff, len, ARRAY_BUFFER)},"componentType":$FLOAT,"count":$n,"type":"VEC3"}"""
            val nrm = accessors.size - 1
            val uLen = n * 8
            val uOff = reserve(uLen) { out -> for (v in uniq) { out.putFloat(buf.uv(v, 0)); out.putFloat(if (flipV) 1f - buf.uv(v, 1) else buf.uv(v, 1)) } }
            accessors += """{"bufferView":${view(uOff, uLen, ARRAY_BUFFER)},"componentType":$FLOAT,"count":$n,"type":"VEC2"}"""
            val uv = accessors.size - 1
            val idx = if (remap != null) {
                val iLen = remap.size * 4
                // Đảo mặt ⇒ đảo chiều quấn (a, c, b).
                val iOff = reserve(iLen) { out -> for (i in remap.indices) out.putInt(remap[if (!p.flip) i else i - i % 3 + (3 - i % 3) % 3]) }
                accessors += """{"bufferView":${view(iOff, iLen, ELEMENT_BUFFER)},"componentType":$UINT,"count":${remap.size},"type":"SCALAR"}"""
                ""","indices":${accessors.size - 1}"""
            } else ""
            // Kiểu: mẫu tên bộ phận ([partStyles]) thắng; rồi vật liệu KHAI của bộ phận (không phải bộ đỉnh nó trỏ); rồi lớp theo từ
            // chung; không thì tối trung tính ([Car3dMaterials.key]). Ảnh chỉ gắn khi đo thấy dùng được cho CHÍNH các đỉnh này.
            val key = Car3dMaterials.key(p, styles, partStyles)
            val st = Car3dMaterials.style(key, styles)
            val plain = st.texture != null && !Car3dMaterials.textured(st, buf, uniq, { it in textures }, dark)
            meshes += """{"name":${q(p.name)},"primitives":[{"attributes":{"POSITION":$pos,"NORMAL":$nrm,"TEXCOORD_0":$uv}$idx,"material":${material(key, p.single, plain)}}]}"""
            val inLid = lid != null && p in lid.lid
            val hinge = (if (inLid) lid?.hinge else hinges[p.name])?.let { ""","hinge":${it.json()}""" } ?: ""
            val grp = if (inLid) ""","grp":${q(Car3dLid.GROUP)}""" else ""
            // `mat` = khoá kiểu đã áp — bên vẽ đổi màu sơn theo NÚT (mọi bộ phận sơn, kể cả cánh cửa mở), không theo tên vật liệu
            // của gltfio [ĐO máy ảo 13/10, owner: "mở đóng cửa, màu cửa sai, không theo màu xe"].
            nodes += """{"name":${q(p.name)},"mesh":${meshes.size - 1},"extras":{"bbox":[${mn.joinToString(",") { it.toFloat().toString() }},${mx.joinToString(",") { it.toFloat().toString() }}]$hinge$grp,"mat":${q(key)}}}"""
            children += nodes.size - 1
        }
        nodes[0] = """{"name":"car","children":[${children.joinToString(",")}]}"""

        val json = buildString {
            append("""{"asset":{"version":"2.0","generator":"Kachi Car3dGlb"},"scene":0,"scenes":[{"nodes":[0]}]""")
            append(""","nodes":[${nodes.joinToString(",")}]""")
            if (meshes.isNotEmpty()) append(""","meshes":[${meshes.joinToString(",")}]""")
            if (materials.isNotEmpty()) append(""","materials":[${materials.joinToString(",")}]""")
            if (accessors.isNotEmpty()) append(""","accessors":[${accessors.joinToString(",")}]""")
            if (views.isNotEmpty()) append(""","bufferViews":[${views.joinToString(",")}]""")
            if (images.isNotEmpty()) append(""","images":[${images.joinToString(",")}],"textures":[${texturesJson.joinToString(",")}],"samplers":[$SAMPLER]""")
            val used = listOfNotNull(EMISSIVE_STRENGTH.takeIf { usesStrength }, UNLIT.takeIf { usesUnlit })
            if (used.isNotEmpty()) append(""","extensionsUsed":[${used.joinToString(",") { q(it) }}]""")
            append(""","buffers":[{"byteLength":$binSize}]}""")
        }.toByteArray(Charsets.UTF_8)

        val jLen = pad4(json.size)
        val total = 12 + 8 + jLen + 8 + binSize
        val out = alloc(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67).putInt(2).putInt(total)
        out.putInt(jLen).putInt(0x4E4F534A).put(json); repeat(jLen - json.size) { out.put(' '.code.toByte()) }
        out.putInt(binSize).putInt(0x004E4942)
        plan.forEach { it(out) }
        check(out.position() == total) { "GLB ghi ${out.position()} ≠ dự tính $total" }
        out.flip()
        return out
    }

    /** Bản trả mảng byte (test + ghi đệm tệp). */
    fun bytes(glb: ByteBuffer): ByteArray = ByteArray(glb.remaining()).also { glb.duplicate().get(it) }

    /**
     * Lọc ảnh tường minh: phóng = tuyến tính, thu = tuyến tính + mipmap tuyến tính (gltfio tự dựng mipmap khi bộ lọc thu cần
     * mipmap) — ảnh đèn/viền 1024² thu nhỏ trên ô ~600 px không còn răng cưa/lấp lánh; lặp REPEAT (mặc định glTF).
     */
    private const val SAMPLER = """{"magFilter":9729,"minFilter":9987,"wrapS":10497,"wrapT":10497}"""

    private const val EMISSIVE_STRENGTH = "KHR_materials_emissive_strength"
    const val UNLIT = "KHR_materials_unlit"
    /** Hậu tố tên vật liệu glTF bản một mặt. */
    const val SINGLE = "@1s"
    /** Hậu tố tên vật liệu glTF bản trơn (không ảnh — [Car3dMaterialStyle.plain]). */
    const val PLAIN = "@plain"

    /** Chuỗi JSON có thoát ký tự (tên bộ phận đến từ tệp ngoài). */
    fun q(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when {
                c == '"' -> append("\\\""); c == '\\' -> append("\\\\")
                c < ' ' -> append(String.format("\\u%04x", c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private fun pad4(n: Int) = (n + 3) and 3.inv()
}
