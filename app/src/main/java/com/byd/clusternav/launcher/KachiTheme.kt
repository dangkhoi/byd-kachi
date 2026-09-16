package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.byd.clusternav.R

/**
 * Ba trạng thái BỀ MẶT của [KachiTheme.surface] (VISUAL-REFRESH P1 · T2).
 *
 * Khai ở tầng trên cùng chứ không lồng trong `object KachiTheme` để chỗ gọi viết `SurfaceTone.ACTIVE` thay vì
 * `KachiTheme.SurfaceTone.ACTIVE` — cùng lối [ThemeMode]/[LayoutPreset] của `:core`.
 */
enum class SurfaceTone {
    /** Thẻ nội dung thường — chuyển sắc dọc + hairline. (Pass 5 gỡ mép sáng/nét đỉnh, xem [KachiTheme.surface].) */
    NEUTRAL,

    /**
     * **KHAY** — thẻ ô làm việc ở màn chính, tức cái mặt mà [NEUTRAL] đứng lên (VISUAL-REFRESH P1 · soát Pass 4).
     *
     * Cùng cách dựng với [NEUTRAL] nhưng lấy cặp [KachiTheme.SLOT]/[KachiTheme.SLOT_TO] (**tối hơn** thẻ nội dung một
     * bậc) và viền [KachiTheme.LINE_STRONG] — viền sáng rõ mà ô làm việc đã dùng từ prototype và đã chạy tốt trên
     * xe (CLAUDE.md §6: không đảo đường đang chạy tốt).
     *
     * Vì sao phải là một tone RIÊNG chứ không dùng lại [NEUTRAL]: khay và thẻ mà cùng một sắc độ thì thẻ hết chỗ
     * nổi lên. Đây đúng là chỗ P1 hụt — ô làm việc dựng `GradientDrawable` thẳng tại chỗ nên nó **không nằm trong
     * bảng rà 26 chỗ gọi `card()`**, và [ĐO] điểm ảnh của nó TRƯỚC/SAU P1 giống nhau từng byte.
     */
    WELL,

    /** Thẻ/ô ĐANG BẬT — cùng cách dựng nhưng mang màu nhấn; chữ trên nó là `INK`, **không** phải `ON_ACCENT`. */
    ACTIVE,

    /** Ô LÕM (nhập liệu, rãnh, đoạn phân đoạn) — một tô đặc tối hơn, **không** gradient. */
    SUNKEN,
}

/**
 * Bảng màu + helper drawable của launcher. **Tra theo chủ đề đang chọn** (T1) — mã hex nằm ở [KachiPalette].
 *
 * ## ⚠ Trước T1 đây là 13 `const val` — và đó là lý do nút gạt chủ đề từng bị BỎ
 * `const val` là hằng **biên dịch**: chỗ gọi được nhúng thẳng chuỗi vào bytecode, nên đổi chủ đề lúc chạy không
 * thể có tác dụng. Phiên S1 đo đúng điều đó rồi kết luận nút gạt sẽ là **nút chết** và cố ý không làm
 * (`kachi-settings-screen.html` §4.5). T1 đổi 13 hằng thành **thuộc tính có getter** ⇒ tên gọi giữ nguyên (21 tệp
 * không phải sửa cách gọi) nhưng giá trị nay đọc lại mỗi lần vẽ.
 *
 * ## Một nơi ghi duy nhất
 * [applyTheme] là **chỗ duy nhất** đổi [palette]. Nó KHÔNG phải bản sao thứ hai của trạng thái: nguồn sự thật vẫn
 * là `HomeUiState.themeMode`, còn đây là **hình chiếu lúc vẽ** của nguồn đó. Bài canh
 * [com.byd.clusternav.launcher.ThemePaletteContractTest] đếm số chỗ gọi [applyTheme] trong `app/src/main` và đỏ nếu
 * có chỗ thứ hai — chính cái bẫy "hai bản sao cùng khoá" mà dự án đã sập vào ba lần.
 */
object KachiTheme {

    /** Bảng màu đang dùng. Mặc định TỐI để mọi đường vẽ trước lượt [applyTheme] đầu tiên vẫn ra bảng cũ. */
    var palette: KachiPalette = KachiPalette.DARK
        private set

    /**
     * Chọn bảng màu cho [mode] tại giờ [hour] (0..23, chỉ dùng khi mode = AUTO).
     *
     * Trả về `true` nếu bảng **ĐỔI** — chỗ gọi dùng giá trị đó để quyết định có dựng lại màn hay không. Trả về
     * `false` thay vì dựng lại vô điều kiện vì `applyTheme` được gọi mỗi lượt trạng thái đổi (1 nhịp/giây trên xe),
     * và dựng lại màn mỗi giây thì app đang chiếu trong ô bị nhả/gắn liên tục — đúng họ lỗi P-bug1/R3.
     */
    fun applyTheme(mode: ThemeMode, hour: Int): Boolean {
        val next = if (mode.isNight(hour)) KachiPalette.DARK else KachiPalette.LIGHT
        if (next == palette) return false
        palette = next
        return true
    }

    // ══ VAI MÀU — tên GIỮ NGUYÊN từ bản `const val` để 21 tệp không phải đổi cách gọi ═════════════════════
    val BG: String get() = palette.bg
    val INK: String get() = palette.ink
    val INK2: String get() = palette.ink2
    val MUT: String get() = palette.mut
    val MUT2: String get() = palette.mut2
    val ICON: String get() = palette.icon
    val CARD: String get() = palette.card
    val CARD2: String get() = palette.card2
    val CARD_FILL: String get() = palette.cardFill
    val PANEL: String get() = palette.panel
    val FIELD: String get() = palette.field
    val CELL: String get() = palette.cell
    val TILE: String get() = palette.tile
    val CHIP_OFF: String get() = palette.chipOff
    val DIM: String get() = palette.dim
    val TRACK: String get() = palette.track
    val SLOT: String get() = palette.slot
    val SLOT_TO: String get() = palette.slotTo
    val BAR: String get() = palette.bar
    val BAR_TOP: String get() = palette.barTop
    val LINE: String get() = palette.line
    val LINE_STRONG: String get() = palette.lineStrong
    val GRID_LINE: String get() = palette.gridLine
    val EMPTY_FILL: String get() = palette.emptyFill
    val EMPTY_LINE: String get() = palette.emptyLine
    val WASH: String get() = palette.wash
    val OVERLAY: String get() = palette.overlay
    val ACCENT: String get() = palette.accent
    val ACCENT_INK: String get() = palette.accentInk
    val ACCENT2: String get() = palette.accent2
    val GRAD_FROM: String get() = palette.gradFrom
    val GRAD_TO: String get() = palette.gradTo
    val ON_ACCENT: String get() = palette.onAccent
    val INK_ON_ACCENT: String get() = palette.inkOnAccent
    val ACCENT_SOFT: String get() = palette.accentSoft
    val ACCENT_LINE: String get() = palette.accentLine
    val ACCENT_WASH: String get() = palette.accentWash
    val TILE_ON_FROM: String get() = palette.tileOnFrom
    val TILE_ON_TO: String get() = palette.tileOnTo
    val TILE_ON_LINE: String get() = palette.tileOnLine
    val SCRIM_PANEL: String get() = palette.scrimPanel
    val SCRIM_BTN: String get() = palette.scrimBtn
    val WIDGET_BACKING: String get() = palette.widgetBacking
    val WALL_SCRIM: String get() = palette.wallScrim
    val SCRIM_HEAD: String get() = palette.scrimHead
    val GREEN: String get() = palette.green
    val AMBER: String get() = palette.amber
    val RED: String get() = palette.red
    val CYAN: String get() = palette.cyan
    val ORANGE: String get() = palette.orange
    val SLATE: String get() = palette.slate
    val AMBER_SOFT: String get() = palette.amberSoft
    val ART_TO: String get() = palette.artTo
    val GLOW1: String get() = palette.glow1
    val GLOW2: String get() = palette.glow2
    val CLEAR: String get() = palette.clear
    // ── VISUAL-REFRESH P1 · chất liệu bề mặt (KDoc của từng vai ở [KachiPalette]) ──
    val SURF_FROM: String get() = palette.surfFrom
    val SURF_TO: String get() = palette.surfTo
    val SURF_LINE: String get() = palette.surfLine
    val SURF_ON_FROM: String get() = palette.surfOnFrom
    val SURF_ON_TO: String get() = palette.surfOnTo
    val FIELD_SUNKEN: String get() = palette.fieldSunken
    val SURF_FROM_OVER_ART: String get() = palette.surfFromOverArt
    val SURF_TO_OVER_ART: String get() = palette.surfToOverArt

    /**
     * Sắc lĩnh vực của [domain] — vỏ bọc để chỗ vẽ **không** phải tự viết `?.name` (và không ai nghĩ ra cách thứ
     * hai để tra). `null` ⇒ [CLEAR] = không tint.
     */
    fun domainTint(domain: Domain?): String = palette.domainTint(domain?.name)

    fun c(s: String): Int = Color.parseColor(s)
    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density
    fun dpi(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /**
     * Thẻ kính bo góc + viền mảnh.
     *
     * `radius` nhận **dp dạng `Int`** (T5) chứ không phải `Float` như trước: bán kính giờ đi qua họ hằng
     * `KachiSpace.RADIUS_*`, và để `Float` thì mọi chỗ gọi phải viết `.toFloat()` — tức là mời số trần quay lại.
     */
    fun card(
        ctx: Context,
        radius: Int = KachiSpace.RADIUS_XL,
        fill: String = CARD_FILL,
        stroke: String = LINE,
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = KachiSpace.dpf(ctx, radius)
            setColor(c(fill))
            setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(stroke))
        }

    /**
     * ═══ VISUAL-REFRESH P1 · T2 — BỀ MẶT CÓ CHẤT LIỆU ═══════════════════════════════════════════════════════
     *
     * Thay một lớp tô phẳng bằng **một–hai lớp**: (0) chuyển sắc DỌC + hairline ngoài, (1) lớp sắc lĩnh vực nếu
     * [domain] khác `null`. Thẻ lồi lên khỏi nền mà **không** bóng đổ.
     *
     * ## ⚠⚠ Pass 5 (2026-09-17) — GỠ mép sáng và nét đỉnh, KHÔNG phải hạ bớt
     * Owner nhìn 1.68 trên xe: *"làm bóng ở đầu mỗi nút nhìn kỳ lắm, không đẹp đâu, với nó có 1 cái gạch trên top
     * đấy nhé, bug rồi"*. Hai thứ bị gỡ là hai lớp Pass 4 thêm vào:
     *  • **nét đỉnh ĐẶC** 1–2dp (`crisp`) — một hình chữ nhật tô đặc cao đúng 1–2dp nằm sát đỉnh. Ở mọi cỡ thẻ nó
     *    đọc ra đúng một **VẠCH**, không đọc ra ánh sáng; đó chính là *"1 cái gạch trên top"* owner gọi là bug.
     *  • **dải mép sáng** mờ dần (`edge`, trắng 35 %) — trên ô nhỏ (tile thanh nút, chip, ô bộ chọn 40–56dp) dải
     *    cao 12dp chiếm ~1/4 chiều cao ô ⇒ đọc ra *"bóng ở đầu nút"*.
     *
     * **Quyết định thiết kế** (HMI tối cao cấp): chiều nổi do **chính chuyển sắc dọc** gánh (đỉnh sáng → đáy tối,
     * bước 1.35× so với nền ở bảng TỐI) cộng ba bậc `nền → khay → thẻ` và hairline. Một vạch sáng cứng ở đỉnh là
     * **specular**, và specular chỉ đúng khi bề mặt bo cạnh thật; ở đây nó là hình chữ nhật 1dp nên mắt đọc ra
     * *đường viền hở*, không đọc ra *mặt vát*. Chọn **bỏ hẳn** thay vì hạ xuống 8 %: một vạch 1dp ở bất kỳ alpha
     * nào vẫn là một vạch — cái sai là **hình dạng**, không phải cường độ. Hai vai màu `surfEdge`/`surfOnEdge` vì
     * thế bị xoá khỏi [KachiPalette] chứ không để lại (CLAUDE.md §8: vai không ai dùng là vai chết).
     *
     * Bài canh đảo chiều theo: [SurfaceContrastContractTest] nay đòi **không còn** lớp ánh sáng ở đỉnh, và
     * [SurfaceMaterialContractTest] đòi thân hàm này không còn `Gravity.TOP`/`setLayerHeight`.
     *
     * ## Vì sao không có bóng/blur/elevation — và vì sao có bài canh riêng
     * Đầu máy DiLink chạy GPU TRINKET. `setShadowLayer`/`BlurMaskFilter`/`RenderEffect` và `elevation` đều bắt GPU
     * vẽ thêm một lượt off-screen mỗi khung. Cảm giác "lồi" ở đây do **chênh sáng trong chính gradient** tạo ra, tức
     * là 0 chi phí thêm so với một tô đặc. [SurfaceMaterialContractTest] quét tầng `launcher/` và đỏ nếu một trong
     * bốn thứ đó quay lại.
     *
     * ## Vì sao chuyển sắc DỌC chứ không chéo
     * [gradient] đang dùng `TL_BR` (chéo) cho **nút/pill đang chọn**. Giữ chéo = nhận diện của *"cái đang được
     * chọn"*, dọc = nhận diện của *"bề mặt"* ⇒ hai vai không lẫn nhau. Đây là lý do chức năng, không phải sở thích.
     * Sau Pass 5 nó còn gánh thêm một vai nữa: **nó là toàn bộ chiều nổi**, nên chiều của nó (đỉnh sáng hơn đáy)
     * là một hợp đồng, không phải một lựa chọn — có bài canh riêng.
     *
     * ## ⚠ Mỗi lần gọi dựng một `Drawable` MỚI — cố ý
     * `Drawable` dùng chung giữa nhiều View thì chúng chia nhau **một** `ConstantState`: đổi bounds/alpha ở một ô là
     * đổi cả những ô kia. Hàm này vì thế **không cache**. Ràng buộc AC5.3 (*"dựng một lần"*) nói về **nhịp trạng
     * thái** — chỗ gọi phải dựng lúc dựng View, không dựng lại mỗi giây trong `bind`/`onDraw`.
     *
     * @param tone [SurfaceTone.NEUTRAL] thẻ nội dung · [SurfaceTone.WELL] **khay** mà thẻ đứng lên (ô làm việc ở
     *   màn chính) · [SurfaceTone.ACTIVE] thẻ/ô đang bật (mang màu nhấn) · [SurfaceTone.SUNKEN] ô lõm — **giữ
     *   phẳng**: một tô đặc, không gradient. Lồi và lõm phải khác nhau ở CƠ CHẾ chứ không chỉ ở độ sáng, nếu
     *   không thì hai vai đọc như một.
     * @param domain lĩnh vực của nội dung trong thẻ — thêm một lớp sắc rất nhạt để mắt tìm được vùng *trước khi*
     *   đọc chữ. `null` (mặc định) ⇒ không có lớp đó, không phải một màu mặc định.
     * @param overArtwork thẻ này nằm **trên ẢNH NỀN** ⇒ dùng bản bán trong suốt 80 % ([KachiPalette.surfFromOverArt])
     *   để ảnh lọt qua. Sinh ra cho P1b (spec §4.10) sau phản hồi owner 2026-09-16 kèm ảnh chụp trên xe: *"cái màu
     *   đen, xám của mình, khi nhét thêm hình nền vào, nó lại không đẹp nữa"* — thẻ đục trên ảnh đọc ra thành
     *   **miếng vá**, không thành **cửa sổ**.
     *   ⚠ P1 **chưa chỗ nào bật cờ này** (mặc định `false` ⇒ hành vi hôm nay không đổi một pixel). Nó có sẵn để
     *   P1b chỉ phải thêm **một lớp ảnh ở chỉ số 0** của [LayerDrawable] chứ không phải viết lại hàm này — sau
     *   Pass 5 chồng lớp chỉ còn `base` (+ tint) nên chèn lớp đáy càng không lệch gì.
     *   ⚠⚠ Và ghi ra chỗ CHƯA ĐỦ: ở 80 %, [ĐO] trên hai nền tệ nhất (trắng tinh / đen tuyền) [INK] còn 7.27:1
     *   (tối) và 10.17:1 (sáng) — đạt; nhưng [MUT] chỉ còn 3.15–3.91:1. P1b **phải** kèm lớp che 35–50 % hoặc
     *   chọn mực theo độ chói đo được của vùng ảnh dưới thẻ. Không có bước đó thì cờ này chưa dùng được thật.
     */
    fun surface(
        ctx: Context,
        radius: Int = KachiSpace.RADIUS_XL,
        tone: SurfaceTone = SurfaceTone.NEUTRAL,
        domain: Domain? = null,
        overArtwork: Boolean = false,
    ): Drawable {
        val r = KachiSpace.dpf(ctx, radius)
        val hair = dpi(ctx, KachiSpace.HAIRLINE)
        if (tone == SurfaceTone.SUNKEN) return GradientDrawable().apply {
            cornerRadius = r
            setColor(c(FIELD_SUNKEN))
            setStroke(hair, c(SURF_LINE))
        }
        val active = tone == SurfaceTone.ACTIVE
        val well = tone == SurfaceTone.WELL
        val neutralFrom = if (overArtwork) SURF_FROM_OVER_ART else SURF_FROM
        val neutralTo = if (overArtwork) SURF_TO_OVER_ART else SURF_TO
        val from = if (active) SURF_ON_FROM else if (well) SLOT else neutralFrom
        val to = if (active) SURF_ON_TO else if (well) SLOT_TO else neutralTo
        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(c(from), c(to)),
        ).apply {
            cornerRadius = r
            setStroke(hair, c(if (active) ACCENT_LINE else if (well) LINE_STRONG else SURF_LINE))
        }
        // ⚠ Pass 5 — KHÔNG còn lớp ánh sáng ở đỉnh (xem KDoc). Chiều nổi nằm hết trong `base`: đỉnh sáng → đáy
        // tối. Thêm bất cứ lớp nào cao vài dp ghim vào `Gravity.TOP` là dựng lại đúng cái vạch owner đã chê.
        val tint = domainTint(domain)
        if (tint == CLEAR) return base
        return LayerDrawable(arrayOf(base, GradientDrawable().apply { cornerRadius = r; setColor(c(tint)) }))
    }

    /**
     * Nền gradient accent (nút chính / tile bật).
     *
     * ⚠ Mặc định là [GRAD_FROM]/[GRAD_TO], **không** phải [ACCENT]/[ACCENT2]: chữ [ON_ACCENT] nằm TRÊN nền này, và
     * [ĐO] trắng trên `#4c7dff` chỉ **3.69:1** (dưới 4.5). [GRAD_FROM] là cùng họ xanh nhưng tối một bậc ⇒ 4.83:1.
     * [ACCENT] vẫn là màu nhận diện cho chấm/viền/lớp tô nhạt — chỗ không có chữ đè lên.
     */
    fun gradient(ctx: Context, radius: Int, from: String = GRAD_FROM, to: String = GRAD_TO): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(from), c(to))).apply {
            cornerRadius = KachiSpace.dpf(ctx, radius)
        }

    /**
     * Tile DOCK BẬT — gradient accent BÁN TRONG SUỐT + viền accent, khớp prototype `.dtile.on`
     * (accent 36% → accent2 32%, viền accent 55%). KHÁC gradient đặc [gradient] (dùng cho pill/preset chọn).
     */
    fun gradientSoft(ctx: Context, radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(TILE_ON_FROM), c(TILE_ON_TO))).apply {
            cornerRadius = KachiSpace.dpf(ctx, radius); setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(TILE_ON_LINE))
        }

    /**
     * Fade dưới nhãn ô (slot-head) — khớp prototype `linear-gradient(180deg, rgba(0,0,0,.55), transparent)`.
     *
     * ⚠ Bản SÁNG dùng mờ **TRẮNG** chứ không phải mờ đen: mực trên nhãn này là [INK], và ở bảng sáng [INK] là mực
     * đậm ⇒ mờ đen sẽ làm chữ đậm nằm trên nền đậm.
     */
    fun topFade(ctx: Context, radius: Int = KachiSpace.RADIUS_L): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(SCRIM_HEAD), c(CLEAR))).apply {
            val r = KachiSpace.dpf(ctx, radius)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }

    /** Viên thuốc (pill) bo tròn hết cỡ. */
    fun pill(ctx: Context, fill: String = CARD_FILL, stroke: String = LINE): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = KachiSpace.dpf(ctx, KachiSpace.RADIUS_PILL)
            setColor(c(fill))
            setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(stroke))
        }

    /** Ánh xạ tên icon (ControlDef.icon / WidgetDef.icon) → vector drawable. 0 = không có. */
    fun iconRes(icon: String): Int = when (icon) {
        "ic-lock" -> R.drawable.ic_lock
        "ic-window" -> R.drawable.ic_window
        "ic-readlight" -> R.drawable.ic_readlight
        "ic-leaf" -> R.drawable.ic_leaf
        "ic-seat" -> R.drawable.ic_seat
        "ic-temp" -> R.drawable.ic_temp
        "ic-fan" -> R.drawable.ic_fan
        "ic-defrost" -> R.drawable.ic_defrost
        "ic-cam" -> R.drawable.ic_cam
        "ic-door" -> R.drawable.ic_door
        "ic-hood" -> R.drawable.ic_hood
        "ic-light" -> R.drawable.ic_light
        "ic-recirc" -> R.drawable.ic_recirc
        "ic-volume" -> R.drawable.ic_volume
        "ic-wiper" -> R.drawable.ic_wiper
        "ic-cast" -> R.drawable.ic_cast
        "ic-bolt" -> R.drawable.ic_bolt
        "ic-tire" -> R.drawable.ic_tire
        "ic-sun" -> R.drawable.ic_sun
        "ic-music" -> R.drawable.ic_music
        "ic-prev" -> R.drawable.ic_prev
        "ic-play" -> R.drawable.ic_play
        "ic-next" -> R.drawable.ic_next
        "ic-speed" -> R.drawable.ic_speed
        "ic-grid" -> R.drawable.ic_grid
        "ic-swap" -> R.drawable.ic_swap
        "ic-close" -> R.drawable.ic_close
        // U1: 6 icon MỚI cho khái niệm xuất hiện nhiều mà trước đây không có icon nào gần nghĩa
        "ic-road" -> R.drawable.ic_road
        "ic-battery" -> R.drawable.ic_battery
        "ic-steering" -> R.drawable.ic_steering
        // [SOÁT P3] 3 tên icon TRƯỚC ĐÂY KHÔNG được map ⇒ 13/64 nút lùi về icon NHÓM: 2 nút gương mang hình
        // KÍNH (sai nghĩa), 3 nút chế độ lái và 8 nút hỗ trợ lái mang hình lưới (không gợi nghĩa gì).
        // ── [KIỂM TOÁN UX 2026-09-12 · mục 4] 8 icon vá NGHĨA SAI / NGHĨA TRÙNG ────────────────────────
        // Mỗi tên dưới đây tồn tại vì một hình đang mang SAI nghĩa hoặc mang NHIỀU nghĩa; lý do cụ thể ghi trong
        // chính tệp XML (đó là chỗ người sửa icon sẽ đọc).
        "ic-window-open" -> R.drawable.ic_window_open
        "ic-window-close" -> R.drawable.ic_window_close
        "ic-car" -> R.drawable.ic_car
        "ic-photo" -> R.drawable.ic_photo
        "ic-fuel" -> R.drawable.ic_fuel
        "ic-motor" -> R.drawable.ic_motor
        "ic-drive" -> R.drawable.ic_drive
        // ── [U6 · ĐO ảnh 2026-09-12] 18 icon MỚI: tách theo KHÁI NIỆM trong 3 nhóm dày nhất ───────────
        // Bệnh đo được: nhóm Năng lượng có 9/28 ô cùng glyph tia sét và 6/28 cùng glyph con đường; nhóm Động lực có
        // 6/14 ô cùng đồng hồ tốc; nhóm Khí hậu có 5/12 ô cùng nhiệt kế và 4/12 cùng chiếc lá. Icon trùng ở mật độ
        // đó thì nó không còn giúp phân biệt gì — người dùng phải đọc chữ trong ô 40dp (mà chữ thì bị cắt).
        // Lý do của TỪNG hình ghi trong chính tệp XML (đó là chỗ người sửa icon sẽ đọc).
        // ⚠ (V) FEATURE-FILTER 2026-09-17: `ic-battery-charging` · `ic-plug` · `ic-drift` · `ic-car-top-window-rain`
        // đã xoá (tên + tệp vector) — chủ duy nhất của chúng là 19 mã owner chấm NO. `ic-charger` ở lại vì nút
        // `wireless_charge` (KHÔNG thuộc danh sách NO) vẫn dùng.
        "ic-charger" -> R.drawable.ic_charger
        "ic-consumption" -> R.drawable.ic_consumption
        "ic-range" -> R.drawable.ic_range
        "ic-cell-temp" -> R.drawable.ic_cell_temp
        "ic-cell-volt" -> R.drawable.ic_cell_volt
        "ic-pedal" -> R.drawable.ic_pedal
        "ic-brake" -> R.drawable.ic_brake
        "ic-slope" -> R.drawable.ic_slope
        "ic-rpm" -> R.drawable.ic_rpm
        "ic-torque" -> R.drawable.ic_torque
        "ic-engine" -> R.drawable.ic_engine
        "ic-mode" -> R.drawable.ic_mode
        "ic-dust" -> R.drawable.ic_dust
        "ic-sensor" -> R.drawable.ic_sensor
        "ic-coolant" -> R.drawable.ic_coolant
        "ic-alert" -> R.drawable.ic_alert
        // 4 tên dưới dùng cho CẢ mục đọc lẫn NÚT cùng khái niệm (mục tiêu sạc · nhiệt ngoài · điều hoà · lọc khí):
        // hai ô cùng một việc thì phải cùng một hình, phần "xem hay bấm" đã nằm ở dòng phụ (U6).
        "ic-target" -> R.drawable.ic_target
        "ic-temp-out" -> R.drawable.ic_temp_out
        "ic-ac" -> R.drawable.ic_ac
        "ic-filter" -> R.drawable.ic_filter
        // Tên icon dùng lại tệp đã có (trước đây chưa được map nên tra ra 0 = ô trống icon)
        "ic-clock" -> R.drawable.ic_clock_g
        // ── S4 · R12 · hai hành động của CHÍNH launcher ([LauncherActions]) ───────────────────────────
        // KHÔNG vẽ hình mới: cả hai khái niệm đã có tệp đúng nghĩa trong bộ.
        //  • `ic-apps` → `ic_grid` (⊞ bốn ô). Đây KHÔNG phá luật *"⊞ chỉ còn nghĩa bảng tổng hợp"*
        //    (`CapabilityIconMeaningTest`): luật đó nói về **khả năng của XE** — mọi datum/nút/lĩnh vực từng lùi về
        //    ⊞ đã được gỡ. Ở đây ⊞ mang nghĩa gốc của nó trên mọi launcher Android: *lưới ứng dụng*. Hai chỗ dùng
        //    không bao giờ đứng cạnh nhau trong một danh sách: `w_board` có `domain = null` nên không vào bộ chọn
        //    nút, còn khối Launcher chỉ hiện ở chế độ chọn-nút-thanh-xe.
        //  • `ic-settings` → `ic_gear` (bánh răng THẬT — [ĐO] ảnh 2026-09-14: `ic_sys_g` là mặt trời 8 tia, đọc thành "độ sáng"; nét trắng 1.6 như cả bộ). `ic_menu_config.xml` cũng là bánh răng
        //    nhưng GIỮ MÀU xanh thương hiệu (nó vẽ thẳng cho bảng con nút nổi Cast, không qua bước tint) ⇒ dùng nó ở
        //    đây sẽ cho một ô xanh lạc giữa thanh nút.
        "ic-apps" -> R.drawable.ic_grid
        "ic-settings" -> R.drawable.ic_gear
        // V1 pha NGHE — `ic_mic` vẽ MỚI theo chuẩn bộ v2 (nét 1.6, ô quang học 20×20). KHÔNG dùng `ic_mic_g`
        // đang có: tệp đó thuộc màn ClusterNav cũ (ô cockpit `activity_main.xml`), mang màu riêng và tỉ lệ khác
        // — đặt nó cạnh `ic_grid`/`ic_gear` trên cùng một thanh là thấy ngay hai bộ hình.
        "ic-mic" -> R.drawable.ic_mic
        // ══ U7 · BỘ HÌNH XE THEO VỊ TRÍ (spec docs/specs/kachi-icon-set-v2.html) ═════════════════════
        // Ba KHUNG dùng chung (top · front · rear) + VÙNG TÔ là bộ phận đang được nói tới. Tên tệp mang
        // luôn khung + bộ phận + vị trí (`ic_car_top_door_lf`) nên đọc bảng này là đọc được cả nghĩa.
        // ⚠ Chín dòng đã bị GỠ ở U7 (`ic-trunk` · `ic-sunroof` · `ic-mirror` · `ic-seatbelt` · `ic-radar` ·
        // `ic-gps` · `ic-adas` · `ic-turn-left` · `ic-turn-right`): năm tệp đầu được hình xe thay 1:1 nên xoá luôn
        // tệp; hai `ic_turn_*` chỉ chết TÊN, còn TỆP vẫn sống (mũi tên rẽ của màn dẫn đường).
        // ⚠ 2026-09-16 — lượt gỡ ADAS/an toàn xoá tiếp 21 dòng + 21 tệp vector (dây an toàn · người ngồi · điểm mù
        // · chuyển làn · cắt ngang sau · cảnh báo mở cửa · giữ làn · va chạm trước · cảm biến đỗ · ESP · biển báo ·
        // khiên an toàn · ba icon nhóm).
        "ic-car-top-door-lf" -> R.drawable.ic_car_top_door_lf
        "ic-car-top-door-rf" -> R.drawable.ic_car_top_door_rf
        "ic-car-top-door-lr" -> R.drawable.ic_car_top_door_lr
        "ic-car-top-door-rr" -> R.drawable.ic_car_top_door_rr
        "ic-car-top-door-all" -> R.drawable.ic_car_top_door_all
        "ic-car-top-window-lf" -> R.drawable.ic_car_top_window_lf
        "ic-car-top-window-rf" -> R.drawable.ic_car_top_window_rf
        "ic-car-top-window-lr" -> R.drawable.ic_car_top_window_lr
        "ic-car-top-window-rr" -> R.drawable.ic_car_top_window_rr
        "ic-car-top-window-all" -> R.drawable.ic_car_top_window_all
        "ic-car-top-tyre-fl" -> R.drawable.ic_car_top_tyre_fl
        "ic-car-top-tyre-fr" -> R.drawable.ic_car_top_tyre_fr
        "ic-car-top-tyre-rl" -> R.drawable.ic_car_top_tyre_rl
        "ic-car-top-tyre-rr" -> R.drawable.ic_car_top_tyre_rr
        "ic-car-top-tyre-temp-fl" -> R.drawable.ic_car_top_tyre_temp_fl
        "ic-car-top-tyre-temp-fr" -> R.drawable.ic_car_top_tyre_temp_fr
        "ic-car-top-tyre-temp-rl" -> R.drawable.ic_car_top_tyre_temp_rl
        "ic-car-top-tyre-temp-rr" -> R.drawable.ic_car_top_tyre_temp_rr
        "ic-car-top-seat-fl" -> R.drawable.ic_car_top_seat_fl
        "ic-car-top-mirror" -> R.drawable.ic_car_top_mirror
        "ic-car-top-trunk" -> R.drawable.ic_car_top_trunk
        "ic-car-top-trunk-pos" -> R.drawable.ic_car_top_trunk_pos
        "ic-car-top-hood" -> R.drawable.ic_car_top_hood
        "ic-car-top-sunroof" -> R.drawable.ic_car_top_sunroof
        "ic-car-top-sunroof-pos" -> R.drawable.ic_car_top_sunroof_pos
        "ic-car-top-sunshade" -> R.drawable.ic_car_top_sunshade
        "ic-car-top-lock" -> R.drawable.ic_car_top_lock
        "ic-car-top-ambient" -> R.drawable.ic_car_top_ambient
        "ic-car-top-ambient-color-front" -> R.drawable.ic_car_top_ambient_color_front
        "ic-car-top-ambient-color-rear" -> R.drawable.ic_car_top_ambient_color_rear
        "ic-car-top-ambient-bright-front" -> R.drawable.ic_car_top_ambient_bright_front
        "ic-car-top-ambient-bright-rear" -> R.drawable.ic_car_top_ambient_bright_rear
        "ic-car-top-ambient-music" -> R.drawable.ic_car_top_ambient_music
        "ic-car-front-lowbeam" -> R.drawable.ic_car_front_lowbeam
        "ic-car-front-highbeam" -> R.drawable.ic_car_front_highbeam
        "ic-car-front-fog" -> R.drawable.ic_car_front_fog
        "ic-car-front-drl" -> R.drawable.ic_car_front_drl
        "ic-car-front-turn-l" -> R.drawable.ic_car_front_turn_l
        "ic-car-front-turn-r" -> R.drawable.ic_car_front_turn_r
        "ic-car-front-sidelight" -> R.drawable.ic_car_front_sidelight
        "ic-car-front-headlight-mode" -> R.drawable.ic_car_front_headlight_mode
        "ic-car-rear-fog" -> R.drawable.ic_car_rear_fog
        "ic-car-rear-defrost" -> R.drawable.ic_car_rear_defrost
        // Bốn thành phần của MỘT toạ độ, nhưng là bốn đại lượng khác nhau ⇒ bốn hình (U7 · OQ1: mục
        // "nằm trên xe" mới vẽ hình xe, đại lượng đo thì giữ glyph trừu tượng — cùng nét, cùng ô).
        "ic-gps-lat" -> R.drawable.ic_gps_lat
        "ic-gps-lon" -> R.drawable.ic_gps_lon
        "ic-gps-alt" -> R.drawable.ic_gps_alt
        "ic-gps-heading" -> R.drawable.ic_gps_heading
        // ── T2: 9 ICON NHÓM (spec kachi-capability-groups §4.1; 12 trước lượt gỡ ADAS 2026-09-16) ─────
        // Đây là ĐẦU `:app` của giao kèo tên icon cho nhóm khả năng: `CapabilityGroups` (T1, `:core`) khai
        // `icon = "ic-group-…"`, bảng này dịch sang `R.drawable`. Tên là HỢP ĐỒNG giữa hai module — đổi một bên mà
        // không đổi bên kia thì icon tra ra 0 (ô trống), nên có [IconStyleContractTest] canh đủ 9 tên tra được.
        "ic-group-tyres" -> R.drawable.ic_group_tyres
        "ic-group-windows" -> R.drawable.ic_group_windows
        "ic-group-doors" -> R.drawable.ic_group_doors
        "ic-group-lights" -> R.drawable.ic_group_lights
        "ic-group-ambient" -> R.drawable.ic_group_ambient
        "ic-group-climate" -> R.drawable.ic_group_climate
        "ic-group-energy" -> R.drawable.ic_group_energy
        "ic-group-battery" -> R.drawable.ic_group_battery_health
        "ic-group-trip" -> R.drawable.ic_group_trip
        else -> 0
    }

    /**
     * 9 tên icon NHÓM mà `:core` được phép khai (spec kachi-capability-groups §4.1).
     *
     * Khai ở đây thay vì rải trong test: nó là **danh sách hợp đồng**, và [iconRes] phải tra ra được từng tên.
     * Thứ tự = thứ tự nhóm trong spec.
     *
     * ⚠ Ba tên `ic-group-adas` · `ic-group-occupants` · `ic-group-parking` đã gỡ 2026-09-16 cùng ba nhóm ADAS/an
     * toàn (owner) — tệp vector của chúng cũng xoá khỏi `res/drawable`.
     */
    val GROUP_ICON_NAMES: List<String> = listOf(
        "ic-group-tyres", "ic-group-windows", "ic-group-doors", "ic-group-lights", "ic-group-ambient",
        "ic-group-climate", "ic-group-energy", "ic-group-battery", "ic-group-trip",
    )
}
