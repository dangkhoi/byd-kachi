package com.byd.clusternav.launcher

/**
 * ═══ VISUAL-REFRESH P1b · R8 — NGƯỜI DÙNG CHỌN MÀU (owner 2026-09-16: *"có cho người ta chọn màu không nhỉ?"*) ══
 *
 * Ba lựa chọn, lưu **theo hồ sơ tài xế** (AC8.4, khoá `color_choice` trong [ProfileScope.LAUNCHER_PERSONAL_SUFFIXES]):
 *  • [AccentChoice] — màu nhấn: 8 ô chọn nhanh + *theo ảnh nền* (AC8.1);
 *  • [CardTone] — tông thẻ: trung tính · ấm · lạnh (AC8.2);
 *  • [ColorChoice.paint] — màu sơn xe cho hình xe tổng hợp (AC8.3) — **chỗ để sẵn** cho P3: P1b chỉ giữ chuỗi
 *    mã qua encode/decode, không có UI và không ai đọc; P3 khai enum và nối vào `CarPartStyle`. Để sẵn trường
 *    để định dạng lưu bền không phải đổi lần nữa (một byte hỏng trên đĩa xe là một lỗi hiện trường).
 *
 * Cấu hình cũ không có khoá ⇒ [ColorChoice.DEFAULT] — **không hỏi** (AC8.4; owner 2026-09-16: không hỏi xác nhận
 * mặc định). Không bánh xe màu, không mã hex, không chỉnh từng thành phần (AC8.6): người lái chọn một ô.
 *
 * Mã màu của từng ô **không** ở đây — chúng là dữ liệu của bảng màu (`KachiPalette`, `:app`, chỗ duy nhất được
 * viết hex). `:core` chỉ biết *tên* lựa chọn và cách lưu.
 */
enum class AccentChoice {
    KACHI_BLUE, VIOLET, TEAL, AMBER, CHERRY, CORAL, SILVER, WARM_WHITE,

    /** Lấy màu trội của ảnh nền — tính một lần lúc ảnh được nạp (§4.10 mục 4). Chưa có ảnh ⇒ như [KACHI_BLUE]. */
    FROM_ART;

    /** Nhãn cho người đọc — sinh bằng `when` để dịch tại chỗ ([Strings.t]), cùng lối [ThemeMode.label]. */
    fun label(): String = when (this) {
        KACHI_BLUE -> Strings.t("Xanh Kachi", "Kachi blue")
        VIOLET -> Strings.t("Tím", "Violet")
        TEAL -> Strings.t("Lục ngọc", "Teal")
        AMBER -> Strings.t("Hổ phách", "Amber")
        CHERRY -> Strings.t("Hồng anh đào", "Cherry pink")
        CORAL -> Strings.t("Đỏ san hô", "Coral red")
        SILVER -> Strings.t("Bạc", "Silver")
        WARM_WHITE -> Strings.t("Trắng ấm", "Warm white")
        FROM_ART -> Strings.t("Theo ảnh nền", "From wallpaper")
    }
}

/** Tông thẻ — dịch nhẹ `surfFrom/To` theo nhiệt màu (AC8.2). */
enum class CardTone {
    NEUTRAL, WARM, COOL;

    fun label(): String = when (this) {
        NEUTRAL -> Strings.t("Trung tính", "Neutral")
        WARM -> Strings.t("Ấm", "Warm")
        COOL -> Strings.t("Lạnh", "Cool")
    }
}

data class ColorChoice(
    val accent: AccentChoice = AccentChoice.KACHI_BLUE,
    val tone: CardTone = CardTone.NEUTRAL,
    /** Mã màu sơn (P3). Rỗng = chưa chọn = mặc định của P3. Giữ nguyên qua encode/decode, không diễn giải ở P1b. */
    val paint: String = "",
) {
    fun encode(): String = "${accent.name};${tone.name};${paint.replace(';', ' ').trim()}"

    companion object {
        val DEFAULT = ColorChoice()

        /** Giải mã; thiếu/rác ⇒ mặc định cho phần đó, KHÔNG sập và KHÔNG mất phần đọc được (cùng luật [WallpaperPrefs.decode]). */
        fun decode(s: String?): ColorChoice {
            if (s.isNullOrBlank()) return DEFAULT
            val p = s.split(";")
            return ColorChoice(
                accent = p.getOrNull(0)?.trim()?.let { n -> AccentChoice.values().firstOrNull { it.name == n } } ?: DEFAULT.accent,
                tone = p.getOrNull(1)?.trim()?.let { n -> CardTone.values().firstOrNull { it.name == n } } ?: DEFAULT.tone,
                paint = p.getOrNull(2)?.trim() ?: "",
            )
        }
    }
}
