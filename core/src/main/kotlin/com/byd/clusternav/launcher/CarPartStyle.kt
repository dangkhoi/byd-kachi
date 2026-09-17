package com.byd.clusternav.launcher

/**
 * ═══ VISUAL-REFRESH P3 · §4.4 — `CarPartStyle`: BỘ PHẬN × SẮC THÁI → TÊN TOKEN MÀU (thuần `:core`) ═══════════════
 *
 * Spec `docs/specs/kachi-visual-refresh.html` §4.4 (bảng bộ phận × tone) · R1 AC1.5/AC1.6 · R8 AC8.3/AC8.5 · §4.8
 * (màu sơn). Đây là **nơi duy nhất** quyết định một bộ phận của hình xe trông thế nào; tầng vẽ (`:app`,
 * `CarArtPainter`) chỉ tra bảng rồi tô — nó *không phán xét gì* (đúng luật đang có ở `GroupBoardModel`: *"tô khi nào
 * là một câu hỏi về dữ liệu"*).
 *
 * ## Ba luật của bảng (spec §4.4)
 *  (a) mọi cặp (vai × tone × available) tra ra **đúng một** kết quả — `when` vét cạn, KHÔNG có nhánh `else` im lặng
 *      (`CarPartStyleTest` duyệt toàn bộ tích Đề-các);
 *  (b) bảng trả về **TÊN token** ([CarInk]), không phải mã hex — hex chỉ sống trong `KachiPalette` (`:app`);
 *  (c) `ALERT` luôn là thứ **đậm nhất** trên hình: đỏ tô đặc + nét đỏ, kể cả khi bộ phận `ACTIVE` đứng cạnh.
 *
 * ## Vai (role) đến từ đâu
 * [CarPartRole] là **tên vai ghi trong SVG nguồn** (`design/car/{top,front,side}.svg`, thuộc tính `data-role`), được script
 * `gen-car.py` chép vào `CarFramesGenerated.Piece.role`. Một tên lạ trong SVG ⇒ [CarPartRole.of] trả `null` và bài
 * canh ở `:app` đỏ — không có chuyện "vai mới tự nhiên tô được".
 *
 * ## Sơn ĐỎ (§4.8 bảng màu sơn — [ĐO] `design/car/paint.json` → `alertToneOverride: amber_outline`)
 * `RED` = vai *cảnh báo*. Xe sơn đỏ mà bộ phận `ALERT` cũng tô đỏ thì "xe đỏ" đọc thành "xe đang báo lỗi". Khi
 * [CarPaint.RED] được chọn, [look] chuyển `ALERT` sang **AMBER tô đặc + viền tĩnh** ([PartLook.outline]) — vẫn là thứ
 * đậm nhất trên hình vì `WARN` chỉ có tô nhạt, không viền.
 */

/** Ba mặt của hình xe — `Piece.face` trong `CarFramesGenerated` là `name.lowercase()`. */
enum class CarFace { TOP, FRONT, SIDE }

/**
 * Vai màu của một mảnh hình, đúng tên `data-role` trong SVG nguồn (thứ tự = thứ tự lớp §4.3 từ dưới lên).
 *
 * @property decor mảnh **trang trí** (quầng đèn · bóng đổ · biển số · vành · gờ · rèm cuộn · ký hiệu icon): chỉ vẽ ở
 *   mặt "nghỉ", **không** mang trạng thái dữ liệu ⇒ `available = false` thì ẩn hẳn, không bịa.
 */
enum class CarPartRole(val svgRole: String, val decor: Boolean = false) {
    PAINT("paint"),
    GLASS("glass"),
    SHADE("shade", decor = true),
    LAMP("lamp"),
    DRL("drl"),
    TURN("turn"),
    TAIL("tail"),
    GLOW("glow", decor = true),
    GLOWTAIL("glowtail", decor = true),
    TYRE("tyre"),
    RIM("rim", decor = true),
    FLAP("flap"),
    PANEL("panel"),
    MIRROR("mirror"),
    SHADOW("shadow", decor = true),
    PLATE("plate", decor = true),
    GLYPH("glyph", decor = true),
    /** Lớp 6 §4.3 — viền theo tone phủ lên thân; không phải path riêng (`HIGHLIGHT_REF` trỏ về thân). */
    HIGHLIGHT("highlight");

    companion object {
        /** Tên vai trong SVG → vai; `null` = vai lạ (bài canh ở `:app` đỏ, không đoán). */
        fun of(svgRole: String): CarPartRole? = values().firstOrNull { it.svgRole == svgRole }
    }
}

/**
 * MÀU SƠN của hình xe (R8 AC8.3) — chỉ là **trang trí**, không phải vai ngữ nghĩa (§4.8 luật (b)). Mã hex của từng
 * màu nằm ở `KachiPalette.CarPaintSwatches` (`:app`); `id` là chuỗi lưu bền trong [ColorChoice.paint]
 * (khớp `design/car/paint.json`).
 */
enum class CarPaint(val id: String) {
    PEARL("pearl"), TITAN("titan"), BLACK("black"), KACHI("kachi"), RED("red");

    /** Tên đã dịch tại chỗ ([Strings.t]) — không phải `Localized.label` (luôn tiếng Việt gốc). */
    fun title(): String = when (this) {
        PEARL -> Strings.t("Trắng ngọc trai", "Pearl white")
        TITAN -> Strings.t("Xám titan", "Titanium grey")
        BLACK -> Strings.t("Đen bóng", "Gloss black")
        KACHI -> Strings.t("Xanh Kachi", "Kachi blue")
        RED -> Strings.t("Đỏ", "Red")
    }

    companion object {
        /** Mặc định Trắng ngọc trai — **không hỏi lúc cài** (owner 2026-09-16; §4.8 luật (c)). */
        val DEFAULT = PEARL

        /** Chuỗi lưu bền → màu; rỗng/lạ ⇒ [DEFAULT] (cấu hình cũ không có trường, AC8.4). */
        fun of(id: String?): CarPaint = values().firstOrNull { it.id == id?.trim() } ?: DEFAULT

        /** Sàn tương phản (§4.8 luật (a)): đầu gradient nào chạm nền dưới sàn này ⇒ app **tự** bật viền thân. */
        const val OUTLINE_FLOOR = 3.0

        /** [ĐO] cả hai đầu gradient so với nền; đầu tệ nhất dưới sàn ⇒ viền bắt buộc (AC8.5 — không cấm chọn). */
        fun outlineNeeded(worstContrast: Double): Boolean = worstContrast < OUTLINE_FLOOR
    }
}

/**
 * Token màu mà tầng vẽ tra sang `KachiTheme` — TÊN, không phải hex (§4.4 luật (b)).
 *
 * Hai loại: vai **bảng màu** (`PART_LINE`…`RED_SOFT`) và vai **chất liệu mức tả thực (1)** (`PAINT` = chuyển sắc màu
 * sơn · `GLASS` = chuyển sắc kính · `LAMP_ON` · `GLOW`…), do `CarArtPainter` dựng shader một lần.
 */
enum class CarInk {
    NONE, PART_LINE, PART_FILL, DIM, MUT2,
    ACCENT, ACCENT_SOFT, ACCENT_WASH, ACCENT_LINE,
    AMBER, AMBER_SOFT, RED, RED_SOFT,
    PAINT, GLASS, LAMP_ON, LAMP_GLOW, TAIL_ON, SHADOW, DECOR,
}

/**
 * Cách tô MỘT mảnh: [fill] vùng tô · [stroke] nét · [outline] viền tĩnh bắt buộc (sơn đỏ + ALERT, hoặc sơn tối trên
 * nền tối — §4.8 luật (a)).
 */
data class PartLook(val fill: CarInk, val stroke: CarInk, val outline: Boolean = false) {
    val visible: Boolean get() = fill != CarInk.NONE || stroke != CarInk.NONE
}

object CarPartStyle {

    /**
     * Bảng §4.4. [available] = đã đọc được dữ liệu của bộ phận chưa — chưa thì **nét DIM, không tô, không bịa
     * trạng thái đóng** (luật `GroupBoardModel.kt` "off-car là ca thường"); mảnh trang trí thì ẩn.
     */
    fun look(role: CarPartRole, tone: GroupTone, available: Boolean, paint: CarPaint = CarPaint.DEFAULT): PartLook {
        if (!available) return if (role.decor) HIDDEN else PartLook(CarInk.NONE, CarInk.DIM)
        // Sơn đỏ: ALERT không được đỏ (xe đỏ ≠ xe lỗi) ⇒ hổ phách đặc + viền tĩnh, vẫn đậm nhất trên hình.
        val redPaintAlert = paint == CarPaint.RED && tone == GroupTone.ALERT
        return when (role) {
            CarPartRole.PAINT -> when (tone) {
                GroupTone.NEUTRAL, GroupTone.ACTIVE, GroupTone.WARN, GroupTone.ALERT -> PartLook(CarInk.PAINT, CarInk.PART_LINE)
            }
            CarPartRole.GLASS -> when (tone) {
                GroupTone.NEUTRAL -> PartLook(CarInk.GLASS, CarInk.NONE)
                GroupTone.ACTIVE -> PartLook(CarInk.ACCENT_WASH, CarInk.ACCENT_LINE)
                GroupTone.WARN -> PartLook(CarInk.AMBER_SOFT, CarInk.AMBER)
                GroupTone.ALERT -> alert(redPaintAlert, CarInk.RED_SOFT, CarInk.RED)
            }
            CarPartRole.FLAP, CarPartRole.PANEL -> when (tone) {
                GroupTone.NEUTRAL -> PartLook(CarInk.NONE, CarInk.MUT2)
                GroupTone.ACTIVE -> PartLook(CarInk.ACCENT_SOFT, CarInk.ACCENT_LINE)
                GroupTone.WARN -> PartLook(CarInk.AMBER_SOFT, CarInk.AMBER)
                GroupTone.ALERT -> alert(redPaintAlert, CarInk.RED_SOFT, CarInk.RED)
            }
            CarPartRole.TYRE -> when (tone) {
                GroupTone.NEUTRAL -> PartLook(CarInk.PART_FILL, CarInk.NONE)
                GroupTone.ACTIVE -> PartLook(CarInk.ACCENT, CarInk.NONE)
                GroupTone.WARN -> PartLook(CarInk.AMBER, CarInk.NONE)
                GroupTone.ALERT -> alert(redPaintAlert, CarInk.RED, CarInk.RED)
            }
            CarPartRole.LAMP, CarPartRole.DRL, CarPartRole.TURN, CarPartRole.TAIL -> when (tone) {
                // Đèn TẮT = mảng mờ (`partFill`), KHÔNG nét: §4.4 ghi "nét MUT2" nhưng [ĐO máy ảo 2026-09-17] nét thân
                // (≥ 2dp, có sàn) bọc quanh thấu kính 1–2 đơn vị thành hai cục xám ở mũi xe — đèn nhỏ hơn nét của nó.
                GroupTone.NEUTRAL -> PartLook(CarInk.PART_FILL, CarInk.NONE)
                GroupTone.ACTIVE -> PartLook(if (role == CarPartRole.TAIL) CarInk.TAIL_ON else CarInk.LAMP_ON, CarInk.NONE)
                GroupTone.WARN -> PartLook(CarInk.AMBER, CarInk.NONE)
                GroupTone.ALERT -> alert(redPaintAlert, CarInk.RED, CarInk.RED)
            }
            CarPartRole.MIRROR -> when (tone) {
                GroupTone.NEUTRAL -> PartLook(CarInk.NONE, CarInk.MUT2)
                GroupTone.ACTIVE -> PartLook(CarInk.ACCENT_SOFT, CarInk.ACCENT_LINE)   // gập
                // §4.4 ghi "—" cho hai ô này: gương không có trạng thái cảnh báo. Không để nhánh im lặng — nếu dữ liệu
                // bao giờ mang tone đó thì nó vẫn phải HIỆN ra, theo cùng thang của các bộ phận khác.
                GroupTone.WARN -> PartLook(CarInk.AMBER_SOFT, CarInk.AMBER)
                GroupTone.ALERT -> alert(redPaintAlert, CarInk.RED_SOFT, CarInk.RED)
            }
            CarPartRole.HIGHLIGHT -> when (tone) {
                GroupTone.NEUTRAL -> HIDDEN
                GroupTone.ACTIVE -> PartLook(CarInk.NONE, CarInk.ACCENT_LINE)
                GroupTone.WARN -> PartLook(CarInk.NONE, CarInk.AMBER)
                GroupTone.ALERT -> alert(redPaintAlert, CarInk.NONE, CarInk.RED)
            }
            // Trang trí: chỉ ở mặt "nghỉ" — tone không đổi cách tô của chúng (glow theo đèn, bóng theo thân).
            CarPartRole.GLOW, CarPartRole.GLOWTAIL -> when (tone) {
                GroupTone.NEUTRAL, GroupTone.ACTIVE, GroupTone.WARN, GroupTone.ALERT ->
                    PartLook(if (role == CarPartRole.GLOW) CarInk.LAMP_GLOW else CarInk.TAIL_ON, CarInk.NONE)
            }
            CarPartRole.SHADOW -> when (tone) {
                GroupTone.NEUTRAL, GroupTone.ACTIVE, GroupTone.WARN, GroupTone.ALERT -> PartLook(CarInk.SHADOW, CarInk.NONE)
            }
            CarPartRole.RIM, CarPartRole.PLATE, CarPartRole.SHADE, CarPartRole.GLYPH -> when (tone) {
                GroupTone.NEUTRAL, GroupTone.ACTIVE, GroupTone.WARN, GroupTone.ALERT -> PartLook(CarInk.DECOR, CarInk.NONE)
            }
        }
    }

    /**
     * Sắc thái ALERT: đỏ (mặc định) hoặc — khi xe sơn đỏ — hổ phách + viền tĩnh.
     *
     * ⚠⚠ [SOÁT P2/P3 2026-09-17] Phép đổi sang hổ phách phải giữ **hình dạng** của cách tô, không chỉ đổi màu:
     * vai nào vốn **chỉ có NÉT** ([CarPartRole.HIGHLIGHT] — viền tone phủ lên thân, `HIGHLIGHT_REF` trỏ về CHÍNH
     * path thân xe) thì ở lại chỉ-có-nét. Bản đầu trả `PartLook(AMBER, AMBER)` cho mọi vai ⇒ sơn ĐỎ + một bộ phận
     * ALERT (một bánh non là đủ) làm `CarArtPainter.draw` **tô kín thân xe bằng hổ phách đặc** — mất cả chiếc xe
     * trên bảng lốp / bảng cửa / widget xe. Bài canh cũ khoá đúng điều sai (`assertEquals(PartLook(AMBER, AMBER,
     * outline = true), …)` cho MỌI vai kể cả `HIGHLIGHT`), nên nó không thể đỏ — đảo chiều bài canh trước, gỡ mã sau.
     */
    private fun alert(redPaint: Boolean, fill: CarInk, stroke: CarInk): PartLook = when {
        !redPaint -> PartLook(fill, stroke)
        fill == CarInk.NONE -> PartLook(CarInk.NONE, CarInk.AMBER, outline = true)
        else -> PartLook(CarInk.AMBER, CarInk.AMBER, outline = true)
    }

    /** Tone của một bánh lốp từ trạng thái TPMS — một chỗ dịch, để bảng lốp và widget lốp gộp không lệch nhau. */
    fun tyreTone(status: TyreStatus): GroupTone = when (status) {
        TyreStatus.LOW, TyreStatus.HIGH -> GroupTone.ALERT
        TyreStatus.UNEVEN -> GroupTone.WARN
        TyreStatus.OK, TyreStatus.UNKNOWN -> GroupTone.NEUTRAL
    }

    private val HIDDEN = PartLook(CarInk.NONE, CarInk.NONE)
}
