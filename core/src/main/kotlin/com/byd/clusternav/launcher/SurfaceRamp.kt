package com.byd.clusternav.launcher

/**
 * ═══ THANG BỀ MẶT — derive các vai NỀN ĐỤC của một bảng màu từ MỘT recipe (2026-09-17) ══════════════════════
 *
 * Owner 2026-09-17: *"đi đúng IA, không hotfix, cái nào lỡ hardcode thì phải gỡ và patch lại về IA hết"*.
 *
 * Trước lượt này, ~16 vai nền đục của [KachiPalette] (`bg` `card` `panel` `field` `cell` `tile` `slot` `surfFrom`
 * `surfTo` `fieldSunken`…) là **hex đặt tay rời rạc** — mood "tối tăm" nằm rải trong 16 con số, nên đổi sáng/tối/ấm
 * là phải mò 16 chỗ và chúng dễ lệch nhau. Đó đúng là chỗ chưa-IA mà owner chỉ ra.
 *
 * Thang này biến 16 con số ấy thành **một quyết định**: mỗi vai chỉ khai **ĐỘ CAO** (bậc ngữ nghĩa — thẻ cao hơn
 * nền, ô lõm thấp hơn), còn *sáng tối / sắc nền* là recipe ([base]/[lift]/[sink]/[step]). Đổi mood = đổi recipe,
 * cả thang dịch theo, coherent, không lệch. Cùng lối mà màu NHẤN đã đi (seed → [ColorMath.recolor]).
 *
 * THUẦN (`:core`, không Android): nhận/trả `Int` ARGB qua [ColorMath]; mã màu chỉ sống ở [KachiPalette] (`:app`).
 * `ThemePaletteContractTest` (WCAG) là rào — chỉnh recipe cho tới khi xanh, KHÔNG tweak một vai lẻ.
 *
 * @property base màu của bậc 0 = **nền màn** (`bg`). Đây là chỗ mood sống nhiều nhất: base sáng hơn/ấm hơn ⇒ cả
 *   thang sáng/ấm hơn. Ràng buộc cứng: mực mờ nhất còn phải đọc ([KachiPalette.mut2]) ≥ 4.5:1 trên base ⇒ base
 *   của bảng TỐI không lên quá một ngưỡng (xem recipe ở [KachiPaletteSeeds]).
 * @property lift màu để **nâng** (mix về phía này khi độ cao > 0) — trắng hơi ấm cho bảng tối, trắng cho bảng sáng.
 * @property sink màu để **hạ** (độ cao < 0) — gần đen cho bảng tối.
 * @property step tỉ lệ trộn MỖI bậc (0..1). Lớn hơn ⇒ các lớp tách nhau rõ hơn ("sáng sủa" kiểu nhiều lớp nổi).
 */
data class SurfaceRamp(
    val base: Int,
    val lift: Int,
    val sink: Int,
    val step: Double,
) {
    /**
     * Hex của một vai ở [elevation] bậc so với nền. `0` = nền màn; dương = **nổi** (thẻ/ô), âm = **lõm** (ô nhập).
     *
     * [alpha] (0..255) cho các vai bán trong suốt cùng họ nền (thanh nút · thẻ trên ảnh) — mặc định 255 (đục).
     * Trộn tuyến tính về [lift]/[sink] theo `|elevation|·step`; kẹp ≤ 0.98 để bậc cao nhất không hoá trắng tinh.
     */
    fun at(elevation: Int, alpha: Int = 255): String {
        val t = (kotlin.math.abs(elevation) * step).coerceIn(0.0, 0.98)
        val c = if (elevation >= 0) ColorMath.mix(base, lift, t) else ColorMath.mix(base, sink, t)
        return ColorMath.hex(ColorMath.withAlpha(c, alpha))
    }

    companion object {
        /** Dựng recipe từ mã hex (chỗ gọi ở `:app` nơi mã màu được phép sống). */
        fun of(base: String, lift: String, sink: String, step: Double): SurfaceRamp =
            SurfaceRamp(ColorMath.parse(base), ColorMath.parse(lift), ColorMath.parse(sink), step)
    }
}
