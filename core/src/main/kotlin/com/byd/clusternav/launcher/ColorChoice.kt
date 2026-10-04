package com.byd.clusternav.launcher

/**
 * ═══ VISUAL-REFRESH P1b · R8 — NGƯỜI DÙNG CHỌN MÀU (owner 2026-09-16: *"có cho người ta chọn màu không nhỉ?"*) ══
 *
 * Hai lựa chọn, lưu **theo hồ sơ tài xế** (AC8.4, khoá `color_choice` trong [ProfileScope.LAUNCHER_PERSONAL_SUFFIXES]):
 *  • [AccentChoice] — màu nhấn: 8 ô chọn nhanh + *theo ảnh nền* (AC8.1);
 *  • [CardTone] — tông thẻ: trung tính · ấm · lạnh (AC8.2).
 *
 * ⚠ WP3-v5 (2026-09-20) — hai trường `paint`/`model` (màu sơn + kiểu xe cho **vector car**) đã GỠ cùng toàn bộ
 * vector car (owner: bỏ vector, dùng ảnh bitmap). Chúng chỉ tô/kéo dãn hình vector — với ẢNH thì vô nghĩa (nút
 * chết). `decode` vẫn tha thứ chuỗi cũ `ACCENT;TONE;paint;model` (bỏ qua trường dư) nên cấu hình đã lưu không sập.
 *
 * 2.87 · R-OP1 thêm trường thứ ba [ColorChoice.surfaceOpacity] (độ đục nền chung; 2.88 thành thanh kéo 0–100 %) — xem
 * KDoc của nó và [ChromeOpacity].
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

/**
 * @property surfaceOpacity 2.87 · R-OP1 — độ đục chung của nền thanh trên · thanh nút xe · widget, phần trăm, một bội
 *   của [ChromeOpacity.STEP] trong `[0, 100]` (2.88 — thanh kéo; 2.87 chỉ có 100 · 85 · 70 · 55 · 40, đều là bội của 5
 *   nên hồ sơ cũ giữ y số). Không phải màu nên phép suy bảng màu (`KachiPaletteDerive`) KHÔNG đọc nó — mặc định vẫn là
 *   bảng gốc cùng thực thể. Nằm ở đây (không mở khoá mới) để thừa hưởng nguyên đường theo-hồ-sơ + chia sẻ của
 *   `color_choice`.
 */
data class ColorChoice(
    val accent: AccentChoice = AccentChoice.KACHI_BLUE,
    val tone: CardTone = CardTone.NEUTRAL,
    val surfaceOpacity: Int = ChromeOpacity.DEFAULT,
) {
    /**
     * `ACCENT;TONE` — thêm `;o<pct>` CHỈ khi độ đục khác mặc định, nên chuỗi của người chưa chỉnh gì giữ đúng từng byte
     * như ≤ 2.86. Trường độ đục mang NHÃN `o` chứ không là số trần ở vị trí 3: dạng cũ `ACCENT;TONE;paint;model` có
     * chữ ở đó, và một số trần thì không phân biệt được với một trường lạ của bản khác.
     */
    fun encode(): String =
        if (surfaceOpacity == ChromeOpacity.DEFAULT) "${accent.name};${tone.name}"
        else "${accent.name};${tone.name};$OPACITY_TAG$surfaceOpacity"

    companion object {
        val DEFAULT = ColorChoice()

        private const val OPACITY_TAG = "o"
        private val OPACITY_FIELD = Regex("""o(\d{1,3})""")

        /**
         * Giải mã; thiếu/rác ⇒ mặc định cho phần đó, KHÔNG sập. Chuỗi cũ `ACCENT;TONE;paint;model` ⇒ bỏ qua trường dư.
         * Độ đục: trường ĐẦU TIÊN từ vị trí 3 trở đi khớp đúng `o<1–3 chữ số>` ⇒ [ChromeOpacity.snap] (bội của 5 gần
         * nhất, kẹp `[0, 100]`); không có ⇒ 100. Bản ≤ 2.86 đọc chuỗi mới vẫn đúng màu (chúng bỏ qua mọi trường từ vị trí 3).
         * Hồ sơ 2.88 nhập vào 2.87 thì 2.87 kéo số về bậc gần nhất của nó (vd `o0`, `o30` ⇒ 40) — chấp nhận được: kênh OTA
         * không bao giờ hạ bản, và chiều ngược (2.87 → 2.88) giữ đúng số.
         */
        fun decode(s: String?): ColorChoice {
            if (s.isNullOrBlank()) return DEFAULT
            val p = s.split(";")
            val opacity = p.drop(2).firstNotNullOfOrNull { OPACITY_FIELD.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
            return ColorChoice(
                accent = p.getOrNull(0)?.trim()?.let { n -> AccentChoice.values().firstOrNull { it.name == n } } ?: DEFAULT.accent,
                tone = p.getOrNull(1)?.trim()?.let { n -> CardTone.values().firstOrNull { it.name == n } } ?: DEFAULT.tone,
                surfaceOpacity = opacity?.let(ChromeOpacity::snap) ?: ChromeOpacity.DEFAULT,
            )
        }
    }
}
