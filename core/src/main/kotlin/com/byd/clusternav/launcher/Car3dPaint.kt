package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R25 — MÀU SƠN xe 3D từ `car_body_color.csv` của app AVM (thuần `:core`, không chứa byte BYD) ════════════════
 *
 * Định dạng [ĐO 13/10 tệp Seal + chuỗi định dạng log trong `libVehiclePano.so`:
 * `"car body color: %s r%d g%d b%d gg%d fs%d lt0%d lth0%d lt1%d lth1%d"`]: mỗi dòng `tên,r,g,b,gg,fs,lt0,lth0,lt1,lth1`
 * (CRLF), 8 dòng trên Seal: white · black · grey · darkblue · purple · red · smartblue · green.
 *  • `r,g,b` ∈ 0..~90 — phần trăm MÀU HIỂN THỊ (sRGB): [ĐO shader `CarPaint` trong `libVehiclePano.so`] `fragColor.rgb =
 *    uColor·(0,4 + 0,5·diff) + phản chiếu` ghi THẲNG ra màn, không đổi gamma ⇒ `uColor` là màu hiển thị. Filament cần màu nền
 *    TUYẾN TÍNH ⇒ đổi sRGB → tuyến tính ([style]). Ô chọn tô thẳng `/100` ([argb]).
 *    Lỗi bản trước [ĐO máy ảo 13/10, owner: *"màu xám không chọn được"*]: dùng `/100` làm giá trị TUYẾN TÍNH ⇒ grey (46,52,62)
 *    thành 0,46–0,62 tuyến tính ≈ sáng gần bằng white 0,82 dưới nắng + ánh xạ tông ⇒ xám trông như trắng. Nay grey ⇒ 0,18–0,34
 *    tuyến tính. (Bản đầu nữa chuẩn hoá theo kênh lớn nhất ⇒ `black` thành xám 0,9.)
 *  • `gg, fs, lt0, lth0, lt1, lth1` — tham số shader riêng của AVM (bóng/phản chiếu?) [CHƯA BIẾT] ⇒ bỏ qua; kim loại/nhám
 *    lấy chung ([METALLIC]/[ROUGHNESS]).
 * Mỗi đời xe có tệp RIÊNG trong thư mục mô hình của nó [ĐO 37 tệp: Seal 8 màu; em_3ui 7 màu không có trắng; sthny 12 màu…]
 * ⇒ hàng chọn màu bày đúng các dòng của tệp đời xe đang chạy.
 * Dòng hỏng (thiếu số, chữ) ⇒ bỏ dòng đó; tệp không có dòng nào ⇒ danh sách rỗng ⇒ `:app` ẩn hàng chọn màu.
 */
object Car3dPaint {

    /** Một màu sơn: [name] = tên dòng (khoá lưu trong [ColorChoice.carPaint]) · [rgb] màu HIỂN THỊ (sRGB) 0..1 của tệp. */
    data class Preset(val name: String, val rgb: List<Double>) {
        /** Màu nền TUYẾN TÍNH cho vật liệu Filament. */
        val linear: List<Double> get() = rgb.map(::toLinear)
        val style: Car3dMaterialStyle get() = Car3dMaterialStyle(linear + 1.0, metallic = METALLIC, roughness = ROUGHNESS)

        /** Màu ô chọn trong Cài đặt (ARGB đặc) = đúng màu hiển thị của tệp. */
        fun argb(): Int {
            val c = rgb.map { (it * 255.0 + 0.5).toInt().coerceIn(0, 255) }
            return (0xFF shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
        }
    }

    fun presets(csv: String?): List<Preset> = csv.orEmpty().lineSequence().mapNotNull { line ->
        val f = line.trim().split(',').map { it.trim() }
        val name = f.firstOrNull()?.takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '_' } } ?: return@mapNotNull null
        val rgb = f.drop(1).take(3).map { it.toDoubleOrNull() ?: return@mapNotNull null }
        if (rgb.size < 3 || rgb.any { it < 0.0 || it > MAX }) null else Preset(name, rgb.map { it / MAX })
    }.distinctBy { it.name }.toList()

    /**
     * 3.01 R28 — dòng của tệp + màu thêm của Kachi ([Car3dModel.extraPaints]) xếp SAU, trùng tên thì dòng tệp thắng. Tệp không có dòng
     * nào ⇒ rỗng (không có mô hình/màu AVM thì Cài đặt ẩn hàng — màu thêm không tự làm hàng sống lại).
     */
    fun withExtras(file: List<Preset>, extras: List<Preset>): List<Preset> =
        if (file.isEmpty()) file else (file + extras).distinctBy { it.name }

    /** Sơn cho lựa chọn [choice] (`null` ⇒ [fallback] = `Car3DColor` mặc định của AVM); tên lạ ⇒ dòng mặc định; không có ⇒ `null`. */
    fun pick(presets: List<Preset>, choice: String?, fallback: String = DEFAULT): Preset? =
        presets.firstOrNull { it.name == choice } ?: presets.firstOrNull { it.name == fallback } ?: presets.firstOrNull()

    fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

    /** `Car3DColor` của `config.csv` Seal [ĐO] — mọi cột đời xe trong tệp đó đều `white`. */
    const val DEFAULT = "white"
    private const val MAX = 100.0
    const val METALLIC = 0.1
    const val ROUGHNESS = 0.3
}
