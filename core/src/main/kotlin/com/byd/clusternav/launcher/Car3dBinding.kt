package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R25 — BẢNG RÀNG BUỘC MÔ HÌNH 3D (DỮ LIỆU, thuần `:core`) ═══════════════════════════════════════════════
 *
 * Một mô hình = một dòng [Car3dModel]: nguồn tệp, camera mặc định, danh sách điểm chạm (neo trên mô hình → nhóm khả năng
 * + vật trong nhóm), biến thể bộ phận theo trạng thái (cửa mở · đèn sáng), kiểu vật liệu. Thêm xe mới = thêm MỘT dòng —
 * không `if` theo xe trong mã vẽ (CLAUDE.md §7). Cách thả mô hình Seal của owner: `docs/diagnostics/widget-3d-301-2026-10-13.md` §5.
 *
 * Điểm chạm KHÔNG có đường ghi riêng: [Car3dHotspot.group] + [Car3dHotspot.target] chọn đúng các ô nút SẴN CÓ của
 * nhóm ([CapabilityGroups] → [GroupBoard] → [GroupActionCell]); `:app` ghi qua `ControlTileFactory` như bảng Kính 2D.
 */
sealed class Car3dAnchor {
    /** Điểm cố định trong khung MÔ HÌNH (đơn vị của tệp). */
    data class Point(val x: Double, val y: Double, val z: Double) : Car3dAnchor()

    /**
     * Tâm hộp bao của mọi nút có tên khớp [pattern] (`*` = chuỗi bất kỳ, không phân biệt hoa thường) — [ty] = độ cao
     * tương đối trong hộp đó (0 đáy · 1 đỉnh; kính ở nửa trên của tấm cửa).
     */
    data class Node(val pattern: String, val ty: Double = 0.5) : Car3dAnchor()

    /** Neo đầu tiên phân giải được — tên bộ phận khác nhau giữa các mô hình cùng quy ước. */
    data class FirstOf(val options: List<Car3dAnchor>) : Car3dAnchor()
}

/**
 * Một điểm chạm. [target] = mã nút (`headl`) HOẶC khoá vật theo quy ước thân xe (`lf`/`rf`/`lr`/`rr`/`trunk` —
 * [GroupActionMatrix.placeOf] đọc từ icon nút); rỗng ⇒ chỉ XEM. [read] = mã ô xem ghi đè (cửa: `door_lf`).
 */
data class Car3dHotspot(
    val id: String, val anchor: Car3dAnchor, val group: String, val target: String, val read: String? = null,
    /**
     * Owner 13/10: *"cái nào chuyển động được trên 3D thì không cần chấm trạng thái"*. Mẫu tên bộ phận mà khi CÓ trong mô hình
     * thì chính hình 3D nói trạng thái (kính tụt xuống · cánh cửa/nắp cốp mở · đèn sáng) ⇒ KHÔNG vẽ chấm, chạm thẳng vào bộ
     * phận ([Car3dPick]). `null` hoặc không bộ phận nào khớp ⇒ giữ chấm (vd đèn đọc sách — không có hình đèn).
     */
    val shownBy: String? = null,
    /**
     * POLISH2 (owner 13/10 *"chấm đèn đọc vẽ trên nóc"*): vật không có hình đèn riêng mà chiếu sáng KHOANG (đèn đọc) ⇒ bật thì
     * nội thất hắt sáng ấm (bên vẽ đổi `emissiveFactor` của vật liệu nội thất), chạm vào vùng kính cabin — không chấm.
     */
    val glow: Boolean = false,
)

/**
 * Biến thể: trạng thái [read] (nhóm [group]) "khác nghỉ" ⇒ hiện nút khớp [open], ẩn nút khớp [closed] (và ngược lại).
 * [read] có thể là nhiều ô nối bằng `|` — bật khi BẤT KỲ ô nào bật (đèn đuôi sáng theo cốt HOẶC pha — bảng đèn của AVM).
 */
data class Car3dVariant(val closed: String, val open: String, val group: String, val read: String) {
    companion object {
        /** Tiền tố [read] = "lệnh cuối Kachi đã gửi" cho mã nút sau nó (vật không có cảm biến — cốp). */
        const val CMD = "cmd:"
    }
}

/** Kiểu vật liệu cho nguồn KHÔNG mang vật liệu (lưới AVM của BYD): màu rgba 0..1, kim loại, nhám, trong suốt, ảnh. */
data class Car3dMaterialStyle(
    val rgba: List<Double>, val metallic: Double = 0.0, val roughness: Double = 0.5,
    val blend: Boolean = false, val texture: String? = null, val emissive: List<Double>? = null,
    /** `KHR_materials_emissive_strength`: phát sáng × hệ số này (đèn sáng phải nổi trên thân dưới ánh nắng 55 k lux). */
    val emissiveStrength: Double = 1.0,
)

sealed class Car3dSource {
    /** Lưới `car.mesh` (+ `wheel.mesh`) của app AVM BYD trên xe, chọn theo `car.type` ([BydAvmModels]). */
    object BydAvm : Car3dSource()
}

data class Car3dModel(
    val id: String,
    val source: Car3dSource,
    /** Yaw camera mặc định (độ, +: vòng sang trái xe) — 40° = ¾ trước-trái như ảnh tham chiếu owner. */
    val yawDeg: Double = 40.0, val yawMin: Double = -120.0, val yawMax: Double = 200.0,
    val pitchDeg: Double = 16.0, val fovDeg: Double = 30.0, val zoom: Double = 0.78,
    /** Neo đầu/đuôi xe để [Car3dOrient] tự tìm chiều đầu xe; `null` ⇒ [frontSign]. */
    val front: Car3dAnchor? = null, val rear: Car3dAnchor? = null, val frontSign: Int = 1,
    val hotspots: List<Car3dHotspot>,
    val variants: List<Car3dVariant> = emptyList(),
    val materials: Map<String, Car3dMaterialStyle> = emptyMap(),
    /** (mẫu tên bộ phận → khoá trong [materials]) cho bộ phận cần vật liệu khác bộ đỉnh của nó (đèn sáng). */
    val partStyles: List<Pair<String, String>> = emptyList(),
    /**
     * Gốc UV của nguồn ở ĐÁY ảnh (GL) ⇒ [Car3dGlb.write] lật `v`. [ĐO 13/10] lưới AVM: mặt ngoài bánh `wheellf_texture` ánh xạ
     * `v = 0,734·y + 0,72` (tâm mâm ở v 0,72) trong khi mâm vẽ trong `wheel_texture.png` nằm ở 0,27 tính từ ĐỈNH ảnh; thư viện
     * vẽ của AVM (`libVehiclePano.so`) nhập `stbi_set_flip_vertically_on_load`. Không lật ⇒ mâm lệch khỏi lốp (owner 13/10).
     */
    val uvOriginBottom: Boolean = false,
    /** Mẫu tên đèn ở phần tư SAU nhưng TRẮNG (đèn lùi) — [Car3dLamps]; còn lại ở đuôi là đỏ. */
    val whiteLamps: List<String> = listOf("*revers*", "*resers*", "*backup*"),
    /**
     * 3.01 R28 — màu sơn THÊM ngoài `car_body_color.csv` của AVM (dữ liệu của Kachi, cùng không gian: sRGB hiển thị 0..1), xếp SAU
     * các dòng của tệp. Owner 13/10 *"làm thêm 1 màu này cho seal"* (ảnh xe thật) ⇒ chỉ nhóm `eke`.
     */
    val extraPaints: List<Car3dPaint.Preset> = emptyList(),
)

object Car3dCatalog {

    private const val W = "g_windows"
    private const val L = "g_lights"
    private const val D = "g_doors"
    /**
     * Phát sáng VỪA PHẢI (×4–5, nhân ảnh đèn): [ĐO máy ảo 13/10] ×8 trắng ⇒ đèn đuôi là dải trắng cháy (owner gửi ảnh). Với phơi
     * sáng camera (EV100 ≈ 15) Filament nhân phát sáng ≈ 0,1 ⇒ ×4–5 ≈ 0,4–0,5 trên nền màu của ảnh — nổi mà không bão hoà.
     */
    private const val GLOW_WHITE = 4.0
    private const val GLOW_COLOR = 5.0
    /** Phát sáng nền của mâm (× ảnh mâm) — đủ nâng mép dưới, không làm mâm "tự phát sáng" (≪ đèn ×4–5). */
    private const val WHEEL_FILL = 0.8

    /** Sơn mặc định (trắng — `Car3DColor=white` của `config.csv` Seal); `:app` thay bằng [Car3dPaint] từ tệp của AVM. */
    val PAINT = Car3dMaterialStyle(listOf(0.86, 0.87, 0.89, 1.0), metallic = 0.1, roughness = 0.3)

    /**
     * Lưới AVM của BYD. Tên bộ phận [ĐO 13/10] bằng bộ đọc [BydAvmMesh] trên `car.mesh` thật của Seal (eke): 98 bộ phận,
     * trục mô hình X = đầu xe, Y = lên, Z = sườn PHẢI (cửa `doorlf_*` ở z = −0,85; xi-nhan `turnright` ở z = +0,73).
     * Cửa có cặp bản đóng/mở (`doorlf_paint` ↔ `doorlf_paint_open` + `doorlf_open`: hình học cánh cửa đã mở, z tới −1,85).
     * Đèn có cặp tắt/sáng (`body_lamp_3` ↔ `body_lamp_3on`).
     *
     * ## Chung mọi đời vs riêng từng đời (owner 13/10 hỏi Han/SL6 "có khả năng lệch à?")
     * Dòng này CHỈ dùng quy ước TÊN chung của lưới AVM [ĐO trên 37 tệp `car.mesh` — bộ chạy thử ngoài repo]: `door{lf,rf,lr,rr}_
     * {paint,glass}` (+ `_open`), `body_glass`, `trunk*`, `*lamp_turn{left,right}*`, `body_lamp_head*`/`highbeam*`/`tail*`/`fog*`.
     * Vị trí bánh lấy từ pivot `wheel.mesh` của CHÍNH đời đó (không hằng số). Đèn ĐÁNH SỐ (`body_lamp_3`…) mang nghĩa KHÁC nhau
     * giữa các đời (mã máy AVM có một khối điều kiện riêng cho mỗi bộ tên) ⇒ đời chưa giải mã: đèn số luôn ở bản TẮT, điểm
     * đèn chỉ neo theo tên có nghĩa (vắng ⇒ ẩn) — không bao giờ sáng sai/neo sai. Đời đã giải mã nằm ở [AVM_GROUPS] (dữ liệu).
     */
    val BYD_AVM = Car3dModel(
        id = "byd-avm",
        source = Car3dSource.BydAvm,
        // [ĐO máy ảo 13/10] zoom mặc định 0,78 ⇒ xe chỉ chiếm ~40 % bề ngang ô lớn (quả cầu bao rộng hơn thân xe nhiều).
        zoom = 0.6,
        front = Car3dAnchor.FirstOf(listOf(Car3dAnchor.Node("hood*"), Car3dAnchor.Node("body_lamp_*front*"))),
        rear = Car3dAnchor.FirstOf(listOf(Car3dAnchor.Node("trunk*"), Car3dAnchor.Node("body_lamp_rear*"))),
        hotspots = listOf("lf", "rf", "lr", "rr").flatMap { k ->
            listOf(
                Car3dHotspot("win-$k", Car3dAnchor.FirstOf(listOf(
                    Car3dAnchor.Node("door${k}_glass"), Car3dAnchor.Node("door${k}*window*"), Car3dAnchor.Node("door${k}_paint", ty = 0.85),
                )), W, k, shownBy = "door${k}_glass"),
                Car3dHotspot("door-$k", Car3dAnchor.Node("door${k}_paint", ty = 0.3), D, "", read = "door_$k", shownBy = "door$k*open*"),
            )
        } + listOf(
            Car3dHotspot("trunk", Car3dAnchor.FirstOf(listOf(Car3dAnchor.Node("trunk_paint", ty = 0.7), Car3dAnchor.Node("trunk*", ty = 0.7))), W, "trunk",
                shownBy = "trunk*"),
            // POLISH2 [ĐO máy ảo 13/10]: ô đọc tường minh — `drl` khai `readKey = ""` nên điểm 3D lấy "lệnh cuối" (chưa gửi ⇒ TẮT)
            // trong khi hình đèn sáng theo `light_drl` ⇒ chạm đầu tiên gửi BẬT cho đèn đang sáng ("không tắt được"). Cùng cặp ô đọc
            // với bảng Đèn 2D (cùng icon — GroupIconBoard).
            Car3dHotspot("headl", Car3dAnchor.FirstOf(listOf(Car3dAnchor.Node("body_lamp_highbeam*"), Car3dAnchor.Node("body_lamp_head*"))), L, "headl",
                read = "light_high_beam", shownBy = "body_lamp_highbeam*on"),
            Car3dHotspot("drl", Car3dAnchor.Node("body_lamp_drl*"), L, "drl", read = "light_drl", shownBy = "body_lamp_drl*on"),
            Car3dHotspot("readl", Car3dAnchor.Node("body_glass", ty = 1.0), L, "readl", shownBy = "body_glass", glow = true),
        ) + readOnlyLamps(),
        variants = DOORS + namedLamps(),
        materials = mapOf(
            "paint" to PAINT, "open" to PAINT,
            "glass" to Car3dMaterialStyle(listOf(0.03, 0.04, 0.05, 0.55), roughness = 0.05, blend = true),
            "black" to Car3dMaterialStyle(listOf(0.02, 0.02, 0.02, 1.0), roughness = 0.6),
            // POLISH2 (owner 13/10 ảnh: viền khung kính/trụ B "bạc sáng" — xe thật là nẹp ĐEN): vật liệu `metal` của AVM phủ khung
            // cửa/trụ/mép nóc ⇒ đen mờ (satin), phản xạ thấp. Dữ liệu AVM không đánh dấu chi tiết mạ crôm nào ⇒ không giữ bạc.
            "metal" to Car3dMaterialStyle(listOf(0.035, 0.035, 0.04, 1.0), metallic = 0.2, roughness = 0.45),
            // Ảnh vật liệu theo độ phủ UV [SUY — đo: tỉ lệ đỉnh rơi vào điểm ảnh không đen, bộ chạy thử 13/10]: lamp→car_lamp 0,98.
            // Vai đèn (trắng/đỏ đuôi/xi-nhan, tắt/sáng) suy bằng ĐO vị trí — [Car3dLamps]. Màu nền NHÂN ảnh đèn ⇒ giữ hoa văn LED.
            Car3dLamps.WHITE to Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), roughness = 0.1, texture = LAMP_PNG),
            Car3dLamps.WHITE_ON to Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), roughness = 0.1, texture = LAMP_PNG, emissive = listOf(1.0, 0.95, 0.85), emissiveStrength = GLOW_WHITE),
            Car3dLamps.REAR to Car3dMaterialStyle(listOf(0.42, 0.03, 0.03, 1.0), roughness = 0.15, texture = LAMP_PNG),
            Car3dLamps.REAR_ON to Car3dMaterialStyle(listOf(0.85, 0.05, 0.04, 1.0), roughness = 0.15, texture = LAMP_PNG, emissive = listOf(1.0, 0.03, 0.02), emissiveStrength = GLOW_COLOR),
            Car3dLamps.TURN_ON to Car3dMaterialStyle(listOf(1.0, 0.5, 0.05, 1.0), roughness = 0.1, texture = LAMP_PNG, emissive = listOf(1.0, 0.42, 0.02), emissiveStrength = GLOW_COLOR),
            // POLISH2: phát sáng KHAI SẴN = 0 (tắt) — bên vẽ đổi `emissiveFactor` khi đèn đọc bật (nội thất hắt sáng ấm, Car3dRenderer.glow).
            "interior" to Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), roughness = 0.7, texture = "car_texture1.png", emissive = listOf(0.0, 0.0, 0.0)),
            "texture" to Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), roughness = 0.6, texture = "car_texture.png"),
            "texture1" to Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), roughness = 0.7, texture = "car_door.png"),
            "texture2" to Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), roughness = 0.7, texture = "car_control.png"),
            "texture3" to Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), roughness = 0.6, texture = "car_texture.png"),
            // POLISH2 (owner 13/10 *"mâm xe phía dưới mất màu, nan hoa màu đen"*): [ĐO] ảnh mâm áp ĐÚNG (độ sáng TB nửa trên 61 · nửa dưới
            // 63 trên ảnh theo UV đã lật); gốc tối = kim loại 0,3 dưới đèn chỉ có SH khuếch tán (không bản đồ phản chiếu) ⇒ phần
            // kim loại mất phần khuếch tán, nửa dưới (pháp tuyến chúi xuống, khuất nắng) thành đen. Mâm vẽ ĐIỆN MÔI (kim loại 0).
            // R28 (owner 13/10 "mép vành dưới tối hơn mép trên", ĐO ~65 vs ~172): mặt chúi xuống của mâm chỉ nhận đèn dội ⇒ thêm phát sáng
            // YẾU nhân chính ảnh mâm (cùng khuôn `emissiveTexture` của đèn) — cộng đều một nền sáng cho mọi hướng mặt, giữ hoa văn nan/lốp.
            "wheel" to Car3dMaterialStyle(listOf(1.0, 1.0, 1.0, 1.0), metallic = 0.0, roughness = 0.5, texture = "wheel_texture.png",
                emissive = listOf(1.0, 1.0, 1.0), emissiveStrength = WHEEL_FILL),
            // Bánh của đời khác tách vành/lốp [ĐO `wheel.mesh` hc/sa20/stf/hx/paha]: `wheel*_metal` · `*_tire` · `metal_black`.
            "tire" to Car3dMaterialStyle(listOf(0.03, 0.03, 0.03, 1.0), roughness = 0.85),
            "metal_black" to Car3dMaterialStyle(listOf(0.08, 0.08, 0.09, 1.0), metallic = 0.0, roughness = 0.45),
            // POLISH2 — khoang cốp tổng hợp ([Car3dLid.CAVITY]). Owner 13/10: *"trong lòng cốp chỉ làm 1 màu thôi, đen luôn sâu vào cho
            // giống cốp"* ⇒ MỘT màu đen sâu (≈ #050505), nhám hoàn toàn, không phát sáng — đọc như chiều sâu, không chi tiết.
            Car3dLid.CAVITY to Car3dMaterialStyle(listOf(0.0015, 0.0015, 0.0015, 1.0), roughness = 1.0),
        ),
        uvOriginBottom = true,
        partStyles = listOf(
            "*lamp_turn*front4on" to Car3dLamps.WHITE_ON,   // phần ban ngày của cụm xi-nhan: trắng, không hổ phách
            "wheel*_texture" to "wheel",
        ),
    )

    /**
     * Cửa: bản mở theo SỐ ĐỌC cửa thật. Cốp (owner 13/10 "3D có mở được cốp không"): KHÔNG có ô đọc cốp ⇒ theo LỆNH CUỐI Kachi
     * đã gửi (`cmd:trunk` — cùng trạng thái chấm cốp của bảng Kính 2D); chưa gửi ⇒ hình đóng. Chỉ đời có `trunk*_open` [ĐO:
     * eqeal · ew · paha · sc3e · sc3ebe · sthny; Seal KHÔNG có] mới thấy nắp mở.
     */
    private val DOORS: List<Car3dVariant> get() = listOf("lf", "rf", "lr", "rr").map { k -> Car3dVariant("door$k*", "door$k*open*", D, "door_$k") } +
        Car3dVariant("trunk*", "trunk*open*", W, "${Car3dVariant.CMD}trunk")

    /** Đèn theo TÊN có nghĩa (mọi đời): xi-nhan, đèn cốt/pha/đuôi/sương mù có tên chữ. Đèn đánh số không thuộc đây (luôn tắt). */
    private fun namedLamps(): List<Car3dVariant> = listOf(
        Car3dVariant("*lamp_turnleft*", "*lamp_turnleft*on", L, "light_left_turn"),
        Car3dVariant("*lamp_turnright*", "*lamp_turnright*on", L, "light_right_turn"),
        Car3dVariant("body_lamp_highbeam*", "body_lamp_highbeam*on", L, "light_high_beam"),
        Car3dVariant("body_lamp_head*", "body_lamp_head*on", L, "light_low_beam|light_high_beam"),
        Car3dVariant("*lamp_*tail*", "*lamp_*tail*on", L, "light_low_beam|light_high_beam|light_side"),
        Car3dVariant("body_lamp_fog*", "body_lamp_fog*on", L, "light_rear_fog"),
    ) + lamp("", "*lamp_*stop*", "*lamp_*revers*", "*lamp_*resers*") +   // phanh/lùi: Kachi không đọc ⇒ luôn tắt
        listOf(Car3dVariant("*_lamp_#*", "*_lamp_#*on", L, ""))

    /**
     * Phần RIÊNG từng nhóm thư mục AVM ([BydAvmModels.Entry.group]) — CHỈ những gì đã [ĐO]. Khoá lạ ⇒ [BYD_AVM] chung.
     *
     * `eke` (Seal) — bảng đèn [ĐO mã máy `DrawableMeshLamp::refreshTriangles` của `libVehiclePano.so`, khối có ĐÚNG bộ tên đèn của
     * lưới Seal (7·8·3·15·4·5·6·13·14·16·rear·logo·17·fog) — khối đó của Seal là [SUY]: khớp trọn bộ tên]: mỗi bộ phận bật theo ô
     * trạng thái đèn của AVM (`PanoAPI.LIGHT_*`): `3/4` pha · `5/6` cốt HOẶC pha · `7/8` ban ngày (DRL hoặc vị trí; phải/trái) ·
     * `15` dải đuôi = cốt HOẶC pha (HOẶC phanh) · `13/14/16` phanh · `17` lùi · `fog` sương mù sau · `turn…front4on` = phần ban
     * ngày trong cụm xi-nhan trước (sáng theo DRL) · `rear`/`logo` không bao giờ sáng. Không có bộ phận tên cốp/nắp capo ⇒ chấm
     * cốp neo đèn phanh trên cao (`body_lamp_16`, mép trên nắp cốp); điểm pha neo `body_lamp_4` (pha TRÁI), ban ngày `body_lamp_8`.
     */
    val AVM_GROUPS: Map<String, (Car3dModel) -> Car3dModel> = mapOf(
        "eke" to { m ->
            m.copy(
                id = "byd-avm-eke",
                extraPaints = listOf(Car3dPaint.Preset(SEAL_STEEL, SEAL_STEEL_RGB)),
                whiteLamps = m.whiteLamps + "body_lamp_17*",   // 17 = đèn lùi (khối đèn Seal của libVehiclePano, xem trên)
                hotspots = m.hotspots.map { h ->
                    when (h.id) {
                        "trunk" -> h.copy(anchor = Car3dAnchor.FirstOf(listOf(h.anchor, Car3dAnchor.Node("body_lamp_16"))))
                        "headl" -> h.copy(anchor = Car3dAnchor.Node("body_lamp_4"), shownBy = "body_lamp_4on")
                        "drl" -> h.copy(anchor = Car3dAnchor.Node("body_lamp_8"), shownBy = "body_lamp_8on")
                        "tail" -> h.copy(anchor = Car3dAnchor.Node("body_lamp_15"), shownBy = "body_lamp_15")
                        else -> h
                    }
                },
                variants = DOORS +
                    lamp("light_high_beam", "body_lamp_3", "body_lamp_4") +
                    lamp("light_low_beam|light_high_beam", "body_lamp_5", "body_lamp_6") +
                    lamp("light_drl|light_side", "body_lamp_7", "body_lamp_8") +
                    lamp("light_low_beam|light_high_beam", "body_lamp_15") +
                    lamp("light_rear_fog", "body_lamp_fog") +
                    turn("left", "light_left_turn") + turn("right", "light_right_turn") + listOf(
                        // Phần ban ngày của cụm xi-nhan trước: chỉ có bản "on" — sáng theo DRL; KHÔNG khớp mẫu xi-nhan (`…fronton`).
                        Car3dVariant("body_lamp_turnleftfront4*", "body_lamp_turnleftfront4on", L, "light_drl|light_side"),
                        Car3dVariant("body_lamp_turnrightfront4*", "body_lamp_turnrightfront4on", L, "light_drl|light_side"),
                        // Xi-nhan sáng ⇒ phần ban ngày CÙNG chỗ tắt (trùng hình học với `…fronton`: để cả hai thì trắng đè hổ phách — đo
                        // máy ảo 13/10 chỉ thấy hổ phách ở gương). Mẫu mở rỗng = không bao giờ là "bản mở" ⇒ chỉ có chiều ẩn.
                        Car3dVariant("body_lamp_turnleftfront4on", "", L, "light_left_turn"),
                        Car3dVariant("body_lamp_turnrightfront4on", "", L, "light_right_turn"),
                    ) +
                    // Đèn không có ô đọc (phanh 13/14/16, lùi 17) ⇒ luôn ở bản tắt.
                    lamp("", "body_lamp_13", "body_lamp_14", "body_lamp_16", "body_lamp_17"),
            )
        },
    )

    /** Mô hình cho nhóm thư mục AVM [group] (phần chung + phần riêng đã đo, nếu có). */
    fun forAvm(group: String?): Car3dModel = AVM_GROUPS[group]?.invoke(BYD_AVM) ?: BYD_AVM

    private const val LAMP_PNG = "car_lamp.png"

    /**
     * 3.01 R28 — màu "Xanh xám" của Seal owner (xanh thép ánh xám, ánh kim). Gốc số [ĐO]: ảnh xe owner gửi 13/10 (ngoài repo), trung vị
     * điểm ảnh sơn ở 5 vùng thân (tránh vệt sáng/phản chiếu): mặt hông khuất sáng (64,83,112) · mặt hắt sáng (135,153,180) — tông
     * 215–220°. Số dưới là màu HIỂN THỊ kiểu tệp AVM (0..1), CHỈNH trên máy ảo để cửa trước của xe 3D (cùng ánh sáng) ra gần màu
     * mặt hông trong ảnh — xem diagnostics §11.
     */
    const val SEAL_STEEL = "steelgrey"
    val SEAL_STEEL_RGB: List<Double> = listOf(0.51, 0.595, 0.72)

    /** Chỉ mô hình THẬT của xe — không mô hình mẫu trong bản phát hành; không nạp được ⇒ bảng 2D. */
    val ORDER: List<Car3dModel> = listOf(BYD_AVM)

    /**
     * Nút trên đầu widget: mở hết · hé 50 % cả 4 (owner 13/10 "thêm 1 nút mở 50% cả 4") · đóng hết kính — nút SẴN CÓ của nhóm
     * Kính, icon như chân bảng Kính 2D. Nút ĐẦU = mở hết, nút CUỐI = đóng hết.
     */
    val PILLS: List<String> = listOf("windows_all", "win_half_all", "windows_close_all")

    /** Nhóm mà widget cần đọc (cho [CarDataDemand] và lượt dựng model nhóm ở `:app`). */
    val GROUPS: List<String> = listOf(W, L, D)

    /**
     * Đèn CHỈ ĐỌC (owner 13/10: chạm đèn không điều khiển được ⇒ không lệnh, lắc): xi-nhan trước hai bên, đèn đuôi. Chạm thẳng bộ
     * phận đèn ([Car3dPick]) ⇒ `chỉ xem` + lắc khung ([Car3dHotspots.Tap.None] vì không có ô nút). Không bộ phận khớp ⇒ không có.
     */
    private fun readOnlyLamps(): List<Car3dHotspot> = listOf("left", "right").map { side ->
        val a = Car3dAnchor.FirstOf(listOf(Car3dAnchor.Node("body_lamp_turn${side}front"), Car3dAnchor.Node("*lamp_turn$side")))
        Car3dHotspot("turn-$side", a, L, "", read = "light_${side}_turn", shownBy = "*lamp_turn$side*")
    } + Car3dHotspot("tail", Car3dAnchor.Node("*lamp_*tail"), L, "", read = "light_side", shownBy = "*lamp_*tail*")

    private fun lamp(read: String, vararg base: String) = base.map { Car3dVariant(it, "${it}on", L, read) }

    /**
     * Xi-nhan một bên: từng CỤM một biến thể (mẫu đóng KHÔNG có `*` cuối ⇒ không nuốt phần ban ngày `…front4on`): đèn trước,
     * cụm đuôi `rear0..3`, đèn gương (cả bản cửa mở).
     */
    private fun turn(side: String, read: String): List<Car3dVariant> {
        val door = if (side == "left") "doorlf" else "doorrf"
        return listOf(
            Car3dVariant("body_lamp_turn${side}front", "body_lamp_turn${side}fronton", L, read),
            Car3dVariant("body_lamp_turn${side}rear*", "body_lamp_turn${side}rear*on", L, read),
            Car3dVariant("${door}_lamp_turn$side*", "${door}_lamp_turn$side*on", L, read),
        )
    }
}

/** Đọc hộp bao bộ phận từ `extras` của nút glTF (`{"bbox":[6 số]}` do [Car3dGlb] ghi). Thuần; hỏng ⇒ `null`. */
object Car3dBox {
    private val MAT = Regex(""""mat"\s*:\s*"([^"]*)"""")

    /** Khoá kiểu vật liệu đã áp cho nút (`extras.mat`), `null` nếu vắng. */
    fun material(extras: String?): String? = MAT.find(extras ?: return null)?.groupValues?.get(1)

    private val GRP = Regex(""""grp"\s*:\s*"([^"]*)"""")

    /** Nhóm diễn hoạt của nút (`extras.grp` — nắp cốp cắt từ thân, [Car3dLid]), `null` nếu vắng. */
    fun group(extras: String?): String? = GRP.find(extras ?: return null)?.groupValues?.get(1)

    private val RX = Regex(""""bbox"\s*:\s*\[([^\]]*)]""")
    fun parse(extras: String?): FloatArray? {
        val body = RX.find(extras ?: return null)?.groupValues?.get(1) ?: return null
        val v = body.split(',').map { it.trim().toFloatOrNull() ?: return null }
        return if (v.size == 6) v.toFloatArray() else null
    }
}

/** So tên nút với mẫu `*` (chuỗi bất kỳ) và `#` (một dãy chữ số) — không phân biệt hoa thường. Thuần. */
object Car3dNames {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    fun matches(pattern: String, name: String?): Boolean {
        if (name == null) return false
        val rx = cache.getOrPut(pattern) {
            Regex(pattern.lowercase().split('*').joinToString(".*") { seg -> seg.split('#').joinToString("[0-9]+") { Regex.escape(it) } })
        }
        return rx.matches(name.lowercase())
    }

    /**
     * Biến thể [v] khi trạng thái [open]: nút [name] có hiện không. `null` = nút không thuộc biến thể này (không đụng).
     * Nút khớp mẫu MỞ là "bản mở"; khớp mẫu ĐÓNG mà không khớp mẫu mở là "bản đóng".
     */
    fun visible(v: Car3dVariant, name: String?, open: Boolean): Boolean? = when {
        matches(v.open, name) -> open
        matches(v.closed, name) -> !open
        else -> null
    }

    /**
     * Gộp MỌI biến thể nhận nút [name]: hiện khi TẤT CẢ đồng ý (`doorlf_lamp_turnleft_openon` thuộc cả biến thể cửa lẫn
     * xi-nhan ⇒ chỉ hiện khi cửa mở VÀ xi-nhan sáng). [open] = trạng thái từng biến thể (cùng thứ tự). `null` = không biến
     * thể nào nhận nút này.
     */
    fun visibleAll(variants: List<Car3dVariant>, open: List<Boolean>, name: String?): Boolean? {
        var any = false; var all = true
        for (i in variants.indices) {
            val v = visible(variants[i], name, open[i]) ?: continue
            any = true; if (!v) all = false
        }
        return if (any) all else null
    }
}
