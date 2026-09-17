package com.byd.clusternav.launcher

/**
 * ═══ HẠT GIỐNG của bảng màu — phần "chọn được" của [KachiPalette] (P1b · R8) + MÀU SƠN xe (P3 · AC8.3) ═══════════
 *
 * Cùng một bảng màu với [KachiPalette] (chỗ DUY NHẤT thứ hai được viết hex — `ThemePaletteContractTest.hexAllowed`
 * ghi lý do), tách tệp vì trần 500 dòng. Không phải `const val` (KachiTheme.kt:11-15: hằng biên dịch là nút chết).
 */
internal object KachiPaletteSeeds {
    // ══ SURFACE RAMP (2026-09-17) — RECIPE của THANG BỀ MẶT (owner: "sáng sủa vui vẻ, đi đúng IA, gỡ hardcode") ══
    //
    // Đây là chỗ MOOD của nền sống — MỘT quyết định thay 16 hex đặt tay. [KachiPalette] chỉ khai ĐỘ CAO từng vai;
    // sáng/tối/ấm/lạnh nằm ở base+step dưới đây. Đổi ở đây là cả thang dịch theo, coherent (xem `SurfaceRamp`).
    //
    // ⚠ Ràng buộc cứng của bảng TỐI (đo được): mực mờ nhất còn phải đọc (`mut2` #99a4b6, WCAG L≈0.368) ≥ 4.5:1
    // trên MỌI bề mặt mà `ThemePaletteContractTest.textOn` liệt kê ⇒ bề mặt sáng nhất trong nhóm đó phải L ≤ 0.043,
    // và sau khi sắc lĩnh vực (10.2%) chồng lên vẫn phải ≥ 4.5. Nghĩa là **không thể** làm nền dark sáng rực — WCAG
    // chặn. "Sáng sủa vui vẻ" ở đây đến từ (a) HỌ MÀU ẤM: nền deep-indigo `#141b30` thay near-black `#0a0d13` lạnh-
    // chết — bớt "hố đen", ấm và có sức sống; (b) gradient thẻ SÂU (đỉnh moderate → đáy về đen) cho chiều nổi thật;
    // (c) accent tươi. `lift` lệch xanh-tím để lớp cao ấm; `sink` = đen để đáy gradient đủ sâu (span ≥ 1.20×).
    val DARK_RAMP = SurfaceRamp.of(base = "#141b30", lift = "#c6d0ff", sink = "#000000", step = 0.04)

    // Bảng SÁNG **layer NGƯỢC** bảng tối: nền gần trắng, các mặt NỔI = trắng (step lớn ⇒ chạm trần trắng nhanh),
    // tách nền chủ yếu bằng VIỀN + các vai LÕM tối hơn nền (§3.2). `sink` = xám xanh cho khay/ô-lõm — KHÔNG quá tối:
    // mực mờ nhất bảng sáng (`mut2` #54606f, L≈0.11) còn phải đọc trên vai lõm ⇒ vai lõm sâu nhất phải L ≥ 0.69,
    // nên sink `#d9dfe9` (L≈0.72) chứ không tối hơn. `lift` = trắng.
    val LIGHT_RAMP = SurfaceRamp.of(base = "#eef1f8", lift = "#ffffff", sink = "#d9dfe9", step = 0.5)

    // ══ VISUAL-REFRESH P1b · R8 — HẠT GIỐNG màu nhấn cho 7 ô chọn (owner: "có cho người ta chọn màu không nhỉ?")
    //    Ô mặc định (KACHI_BLUE) = chính bảng này, ô "theo ảnh nền" = màu trội của ảnh. Mỗi ô mỗi bảng đúng MỘT
    //    mã: mọi vai nhấn khác (accent2 · gradFrom/To · tileOn* · surfOn* · glow*) SUY RA bằng `ColorMath.recolor`
    //    — giữ bậc sáng/độ đục đã đo của bảng gốc, chỉ đổi họ màu (`KachiPaletteDerive.kt`), rồi qua
    //    `ContrastGuard` (AC8.5). Bản SÁNG đậm hơn, cùng lẽ §3.2 mục 3 (màu nhấn trên nền sáng phải tối đi).
    val ACCENT_SEEDS_DARK: Map<AccentChoice, String> = mapOf(
        AccentChoice.VIOLET to "#8b6dff", AccentChoice.TEAL to "#2dd4bf", AccentChoice.AMBER to "#f5b73d",
        AccentChoice.CHERRY to "#f27ca4", AccentChoice.CORAL to "#ff6b6b", AccentChoice.SILVER to "#b8c2d0",
        AccentChoice.WARM_WHITE to "#f3e9dc",
    )
    val ACCENT_SEEDS_LIGHT: Map<AccentChoice, String> = mapOf(
        AccentChoice.VIOLET to "#6b46e5", AccentChoice.TEAL to "#0f8f82", AccentChoice.AMBER to "#a86a00",
        AccentChoice.CHERRY to "#c2467a", AccentChoice.CORAL to "#d43d3d", AccentChoice.SILVER to "#6b7787",
        AccentChoice.WARM_WHITE to "#8c7b66",
    )

    /** Tông thẻ ẤM / LẠNH (AC8.2): nhuộm [TONE_MIX] vào các vai bề mặt — một mã cho hai bảng vì chỉ là HƯỚNG nhiệt màu. */
    val TONE_WARM = "#ff9a4a"
    val TONE_COOL = "#4aa8ff"
    val TONE_MIX = 0.08

    /**
     * ═══ VISUAL-REFRESH P3 · R8 AC8.3 — MÀU SƠN xe (từ → tới, chuyển sắc DỌC thân) — CÙNG mã với
     * `design/car/paint.json` (script `gen-car.py` điền `contrast`/`forceOutline` cho tệp ấy; ở đây
     * `KachiCarPaint` đo lại bằng chính bảng này lúc chạy, `CarPaintContrastContractTest` khoá hai bản không lệch).
     * Một mã cho hai chủ đề: đây là màu XE, không phải vai ngữ nghĩa — chỉ VIỀN đổi theo nền (§4.8 luật (a)).
     */
    val CAR_PAINTS: Map<CarPaint, Pair<String, String>> = mapOf(
        CarPaint.PEARL to ("#e9eef5" to "#8d99ab"),
        CarPaint.TITAN to ("#b9c6d6" to "#2e3743"),
        CarPaint.BLACK to ("#4a5361" to "#11151c"),
        CarPaint.KACHI to ("#6f92ff" to "#27407f"),
        CarPaint.RED to ("#ff8b8b" to "#7a2230"),
    )
}
