package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R23 — HÌNH HỌC ĐĂNG KÝ THEO TỪNG ẢNH XE (thuần `:core`) ═══════════════════════════════════════════════════════
 *
 * Owner 13/10 (Pass 4 spec `kachi-301`): xe vẽ tay "như đồ chơi con nít" ⇒ dùng ảnh xe CHÍNH HÃNG BYD (drawable của app hệ
 * thống `com.byd.carsettings.plugins`, đọc lúc chạy). Mỗi ảnh có bố cục riêng (khung, chiều đầu xe, lề trong suốt) nên mặt nạ
 * kính / neo bánh / neo cửa phải là DỮ LIỆU khoá theo ảnh, đo MỘT lần trên ảnh gốc — không mã vẽ nào đổi theo ảnh.
 *
 * Hệ đo: toạ độ px trên ẢNH GỐC ([srcW]×[srcH]). Khung hiển thị = vùng [crop] của ảnh gốc, xoay 90° thuận chiều kim đồng hồ
 * nếu [rotateCw] (ảnh BYD nhìn-trên vẽ đầu xe quay TRÁI; bảng Kachi vẽ đầu xe quay LÊN). [norm] quy một điểm gốc về hệ chuẩn
 * hoá của [CarLayout] (x 0 = trái xe, y 0 = đầu xe) — cùng phép biến đổi mà `:app` dùng khi giải mã (`Matrix.postRotate(90)`).
 *
 * Chỉ khai phía TRÁI xe; phía PHẢI = soi gương quanh trục dọc của xe: toạ độ ngang gốc `[mirrorSum] − v` (ngang gốc = `y` khi
 * xoay, `x` khi không). Mục không khai ⇒ lùi về neo chung [CarLayout] (ảnh người dùng / ảnh chưa đo vẫn chạy như trước).
 */
data class CarArt(
    /** Khoá ổn định của ảnh (tên tài nguyên BYD, hoặc đường asset mặc định) — cũng là dấu-vết cache. */
    val key: String,
    val srcW: Int,
    val srcH: Int,
    /** Vùng giữ lại của ảnh gốc (px): trái, trên, phải, dưới. */
    val crop: List<Float>,
    val rotateCw: Boolean,
    val mirrorSum: Float,
    /** Đa giác kính hông phía trái (px gốc) theo cửa trước/sau. */
    private val leftPanes: Map<CarPart, List<Pair<Float, Float>>>,
    /** Neo bộ phận (px gốc): cửa trái + vật ở trục giữa; cửa phải suy ra bằng soi gương. */
    private val leftParts: Map<CarPart, Pair<Float, Float>> = emptyMap(),
    /** Tâm bánh phía trái (px gốc). */
    private val leftWheels: Map<TyreCorner, Pair<Float, Float>> = emptyMap(),
    /**
     * 3.01 R24 — ĐIỂM của từng thứ gắn trên thân (đèn) theo KHOÁ VẬT = mã icon (quy ước "vật = icon" của [GroupIconBoard]),
     * px gốc, khai ĐỦ hai bên (đèn trái/phải là hai vật khác nhau — không soi gương). Không khai ⇒ [spot] `null` ⇒ không lên hình.
     */
    private val spots: Map<String, Pair<Float, Float>> = emptyMap(),
) {
    init {
        require(crop.size == 4 && crop[0] < crop[2] && crop[1] < crop[3]) { "crop sai: $crop" }
        require(crop[2] <= srcW && crop[3] <= srcH && crop[0] >= 0f && crop[1] >= 0f) { "crop ra ngoài ảnh $srcW×$srcH" }
    }

    private val cropW get() = crop[2] - crop[0]
    private val cropH get() = crop[3] - crop[1]

    /** Cỡ khung HIỂN THỊ (sau cắt + xoay), px gốc. */
    val frameW: Float get() = if (rotateCw) cropH else cropW
    val frameH: Float get() = if (rotateCw) cropW else cropH

    /** Rộng : cao của khung hiển thị — chỗ chứa đặt khung đúng tỉ lệ trước khi ảnh nạp xong. */
    val aspect: Float get() = frameW / frameH

    /** Điểm px gốc ⇒ hệ chuẩn hoá khung hiển thị. Xoay thuận: `x' = dưới − y`, `y' = x − trái` (hệ liên tục, không ±1). */
    fun norm(x: Float, y: Float): CarAnchor =
        if (rotateCw) CarAnchor((crop[3] - y) / frameW, (x - crop[0]) / frameH)
        else CarAnchor((x - crop[0]) / frameW, (y - crop[1]) / frameH)

    private fun mirror(p: Pair<Float, Float>): Pair<Float, Float> =
        if (rotateCw) p.first to mirrorSum - p.second else mirrorSum - p.first to p.second

    private fun leftOf(p: CarPart): CarPart? = when (p) {
        CarPart.DOOR_RF -> CarPart.DOOR_LF
        CarPart.DOOR_RR -> CarPart.DOOR_LR
        else -> null
    }

    /** Mảnh kính hông của cửa [p] (chuẩn hoá); `null` nếu không phải cửa hoặc ảnh chưa đo. */
    fun pane(p: CarPart): List<CarAnchor>? {
        val left = leftOf(p)
        val src = leftPanes[left ?: p] ?: return null
        return src.map { q -> (if (left != null) mirror(q) else q).let { (x, y) -> norm(x, y) } }
    }

    /** Neo bộ phận [p] trên ảnh này; chưa đo ⇒ neo chung [CarLayout.part]. */
    fun part(p: CarPart): CarAnchor {
        val left = leftOf(p)
        val src = leftParts[left ?: p] ?: return CarLayout.part(p)
        return (if (left != null) mirror(src) else src).let { (x, y) -> norm(x, y) }
    }

    /** Điểm của vật [key] (mã icon) trên ảnh này (chuẩn hoá); `null` = ảnh chưa đo điểm đó. */
    fun spot(key: String): CarAnchor? = spots[key]?.let { (x, y) -> norm(x, y) }

    /** Tâm bánh [c] trên ảnh này; chưa đo ⇒ neo chung [CarLayout.wheel]. */
    fun wheel(c: TyreCorner): CarAnchor {
        val left = when (c) {
            TyreCorner.FRONT_RIGHT -> TyreCorner.FRONT_LEFT
            TyreCorner.REAR_RIGHT -> TyreCorner.REAR_LEFT
            else -> null
        }
        val src = leftWheels[left ?: c] ?: return CarLayout.wheel(c)
        return (if (left != null) mirror(src) else src).let { (x, y) -> norm(x, y) }
    }
}

/**
 * Bảng ảnh đã ĐO. Thêm ảnh mới = thêm một mục (đo trên ảnh gốc, ghi bằng chứng ở `docs/diagnostics/oem-car-art-*.md`) — ảnh
 * BYD nào chưa có mục ở đây thì KHÔNG được dùng (mặt nạ lệch = "miếng xanh dán lệch" owner đã chê) ⇒ lùi ảnh mặc định.
 */
object CarArtCatalog {

    /** Ảnh mặc định đóng theo APK (`assets/car/default-car.png`, 678×1397, đầu xe quay lên) — số đo R22·R giữ nguyên. */
    val DEFAULT = CarArt(
        key = "car/default-car.png", srcW = 678, srcH = 1397,
        crop = listOf(0f, 0f, 678f, 1397f), rotateCw = false, mirrorSum = 677f,
        leftPanes = mapOf(
            CarPart.DOOR_LF to listOf(110f to 458f, 120f to 462f, 131f to 520f, 135f to 600f, 136f to 685f, 110f to 685f),
            CarPart.DOOR_LR to listOf(
                110f to 728f, 137f to 728f, 137f to 850f, 134f to 960f, 127f to 1040f, 118f to 1062f, 110f to 980f,
            ),
        ),
        // 3.01 R22·C — chấm cốp: tâm nắp cốp [ĐO 13/10 cột giữa x = 338,5]: kính hậu tối tới y ≈ 1 240, nắp cốp sơn sáng
        // y 1 245–1 330, mép cản 1 335, hết ảnh 1 344 ⇒ tâm nắp y 1 288 (neo chung 0,90 = 1 257 rơi sát mép kính hậu).
        leftParts = mapOf(CarPart.TAILGATE to (338.5f to 1288f)),
        spots = LightSpots.of(
            lowL = 148f to 115f, highR = 524f to 115f, nose = 336f to 58f, bonnet = 336f to 250f, mirrorL = 62f to 468f,
            mirrorR = 617f to 468f, tailL = 160f to 1290f, rear = 336f to 1340f, cabin = 336f to 540f,
        ),
    )

    /**
     * BYD Seal (`car.type` = 138 = 0x8a, `CAR_AUTO_TYPE_EK`) — ảnh nhìn-trên bỏ nóc của CarSettings, 840×576, đầu xe quay TRÁI,
     * sườn TRÁI xe ở mép DƯỚI ảnh. [ĐO 13/10 trên bản trích fw-2606, xem diagnostics `oem-car-art-2026-10-13.md`]: mực đục
     * x 42–801 · y 107–448 (thân 128–426, gương 107/448 ở x ≈ 300–335) ⇒ cắt chừa 6 px. Dải mép cửa (kính hạ vào đây) y 386–405
     * (trái) / 150–168 (phải) ⇒ trục giữa `y ≈ 277,25` ⇒ `mirrorSum` 554,5. Đường ghép cửa trước ↔ sau x ≈ 447–452; cửa sau tối
     * hẳn từ x ≈ 595. Bánh: [SUY] theo kích thước Seal (dài 4 800 mm, cơ sở 2 920 mm) + vè rộng nhất ⇒ trục trước x ≈ 205,
     * sau x ≈ 650 (±20 px), tâm bánh lùi 30 px vào trong mép thân.
     */
    val SEAL_TOP = CarArt(
        key = "common_top_view_img_6125f_ivi_0x8a", srcW = 840, srcH = 576,
        crop = listOf(36f, 101f, 807f, 454f), rotateCw = true, mirrorSum = 554.5f,
        leftPanes = mapOf(
            CarPart.DOOR_LF to listOf(290f to 388f, 445f to 386f, 445f to 404f, 292f to 403f),
            CarPart.DOOR_LR to listOf(455f to 386f, 590f to 387f, 590f to 402f, 455f to 404f),
        ),
        leftParts = mapOf(
            CarPart.DOOR_LF to (368f to 392f), CarPart.DOOR_LR to (522f to 392f),
            CarPart.TAILGATE to (760f to 277f), CarPart.SUNROOF to (420f to 277f), CarPart.SUNSHADE to (520f to 277f),
        ),
        leftWheels = mapOf(TyreCorner.FRONT_LEFT to (205f to 396f), TyreCorner.REAR_LEFT to (650f to 396f)),
        spots = LightSpots.of(
            lowL = 97f to 378f, highR = 100f to 173f, nose = 50f to 277f, bonnet = 160f to 277f, mirrorL = 312f to 432f,
            mirrorR = 312f to 122f, tailL = 735f to 411f, rear = 796f to 277f, cabin = 370f to 277f,
        ),
    )

    private val byKey = listOf(DEFAULT, SEAL_TOP).associateBy { it.key }

    /** Ảnh đã đo theo khoá; `null` = chưa đo ⇒ không dùng. */
    fun byKey(key: String?): CarArt? = key?.let { byKey[it] }

    /** Nền tảng trong tên ảnh CarSettings (`DeviceUtil.getDevicePlatform()` = `"6125f_ivi"`). */
    const val OEM_PLATFORM = "6125f_ivi"

    /**
     * Tên ảnh nhìn-trên mà CarSettings dùng cho đời xe [carType] (số thập phân của `persist.sys.car.type`): công thức BYD
     * `"common_top_view_img_" + platform + "_" + String.format("0x%x", vehicleId)` (+ `"_right"` cho tay lái nghịch — Kachi
     * chưa đọc được cờ này ⇒ luôn bản tay lái thuận). `null` = mã không đọc được.
     */
    fun oemTopViewName(carType: String?): String? {
        val id = carType?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return "common_top_view_img_${OEM_PLATFORM}_0x${Integer.toHexString(id)}"
    }
}

/**
 * 3.01 R24 — gán ĐÈN vào các mốc đo trên ảnh (owner 13/10: *"hình xe và chấm tròn hay icon đèn xung quanh, đúng vị trí đèn"*).
 *
 * Mốc [ĐO 13/10, diagnostics `oem-car-art-2026-10-13.md` §R24 — Seal: đo trên thấu kính thật ±4–8 px; ảnh mặc định không
 * thấy thấu kính ⇒ đèn pha/đèn hậu SUY từ đường viền nắp ca-pô/cốp ±15–25 px]. Một đèn có HAI bên (pha, xi-nhan…) nhưng ô chỉ
 * có MỘT dữ liệu ⇒ một điểm mỗi đèn, chọn theo chỗ (owner: *"đèn pha (cốt/pha/ban ngày) ở hai góc trước, sương mù/đèn hậu
 * phía sau, đèn đọc giữa khoang"*): cốt = đèn trước-TRÁI · pha = đèn trước-PHẢI · ban ngày = giữa ca-pô ngay sau hai đèn (≈ 0,16 chiều dài — tách khỏi
 * điểm sương mù ở mũi đủ một điểm 20 dp ở ô Đèn 267×329 px [ĐO máy ảo 13/10]) · sương mù
 * trước = mũi xe · xi-nhan = đèn báo trên gương hai bên · đèn hông (đèn vị trí) = đèn hậu trái · sương mù sau = đuôi giữa ·
 * đèn đọc = khe giữa hai ghế trước. `headlight_feedback` (chế độ, không phải một bóng đèn) KHÔNG có điểm ⇒ không lên hình.
 */
internal object LightSpots {
    fun of(
        lowL: Pair<Float, Float>, highR: Pair<Float, Float>, nose: Pair<Float, Float>, bonnet: Pair<Float, Float>,
        mirrorL: Pair<Float, Float>, mirrorR: Pair<Float, Float>, tailL: Pair<Float, Float>, rear: Pair<Float, Float>,
        cabin: Pair<Float, Float>,
    ): Map<String, Pair<Float, Float>> = mapOf(
        "ic-car-front-lowbeam" to lowL, "ic-car-front-highbeam" to highR, "ic-car-front-drl" to bonnet,
        "ic-car-front-fog" to nose, "ic-car-front-turn-l" to mirrorL, "ic-car-front-turn-r" to mirrorR,
        "ic-car-front-sidelight" to tailL, "ic-car-rear-fog" to rear, "ic-readlight" to cabin,
    )
}
