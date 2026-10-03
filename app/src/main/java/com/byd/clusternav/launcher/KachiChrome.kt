package com.byd.clusternav.launcher

import android.graphics.drawable.Drawable
import com.byd.clusternav.launcher.ChromeStack.Layer
import com.byd.clusternav.launcher.ChromeStack.Stack

/**
 * ═══ 2.87 · R-OP — ĐỘ ĐỤC NỀN CHUNG của màn chính, áp ở ĐÚNG năm chỗ dựng ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §3 R-OP1..3 · §4.1. Owner 03/10: *"Cho chỉnh độ transparent của
 * header bar, taskbar, widget được không? Chỉnh chung, không cần riêng từng cái."*
 *
 * ## Vì sao không nhân alpha vào bảng màu
 * `BAR_TOP` · `surface` · `slot`… dùng CHUNG với Cài đặt, hộp thoại, ngăn kéo, lớp phủ giọng nói, và với hai dải che
 * của [WallView]. Nhân vào bảng là làm trong suốt cả những chỗ owner không hỏi — và làm số đo của
 * `ThemePaletteContractTest.textOn` (bỏ qua alpha của nền) nói về một nền không còn được vẽ. Nên hệ số sống ở đây và
 * chỉ năm chỗ dựng của màn chính đọc nó (bài `KachiChromeContractTest` ghim đủ năm, cấm mọi chỗ khác):
 *  1. `KachiGlass.paint` — thẻ/khay kính tone NEUTRAL · WELL (widget, ô nhóm, khay ô làm việc);
 *  2. `ControlTileFactory.applyBg` nhánh TẮT — ô nút (thanh nút + ô giữa màn);
 *  3. `readTileOf` — ô đọc số của thanh nút;
 *  4. `KachiTopStrip` — nền thanh trên ([KachiGlass.bar]);
 *  5. `ControlDockView` — nền thanh nút xe ([KachiGlass.bar]).
 *
 * KHÔNG mờ: chữ/icon (chỉ alpha của `Drawable` nền, không bao giờ `View.alpha`), ô đang BẬT, ô cảnh báo, nền widget
 * bên thứ ba, đĩa ⇄, ảnh/nhạc, dải che của hình nền, và mọi thứ ngoài màn chính.
 *
 * ## Hệ số người chọn ≠ hệ số được vẽ (R-OP3)
 * [fraction] là bậc người chọn. Thứ được vẽ không bao giờ trong hơn mức để mọi chồng lớp hôm nay đọc được vẫn đọc được
 * ([ChromeStack]):
 *  • **không ảnh nền** — nền dưới đã biết (nền màn + tâm hai vầng sáng, theo màu nhấn) ⇒ một hệ số cho mỗi VÙNG
 *    ([plainTop] · [plainDock] · [plainWell]), tính một lần mỗi khi bậc/bảng màu đổi;
 *  • **có ảnh nền** — thẻ/khay kính: bộ giải lớp che theo độ chói đo dưới từng thẻ (`WallWindowDrawable`); thanh:
 *    [barAlpha] theo vùng ảnh TỆ NHẤT dưới thanh; ô TẮT trên thanh nút: [dockTileArt] (thanh đã kịch trần hôm nay mà
 *    ảnh dưới quá sáng thì chính ô phải giữ đục).
 *
 * ## Một chỗ ghi
 * [apply] chỉ được gọi từ `ThemeHost.sync` (cùng chỗ áp bảng màu) ⇒ đổi bậc đi đúng đường đổi màu: `render` →
 * `applyThemeInPlace` dựng lại nền tại chỗ, KHÔNG `recreate` (ô app + màn ảo sống tiếp). Trả `false` khi không đổi
 * vì `sync` chạy mỗi nhịp trạng thái (1 Hz) — trả `true` vô điều kiện là dựng lại thanh nút + ô mỗi giây.
 */
object KachiChrome {

    /** Hệ số người chọn, 0..1 (1 = như hôm nay). Chỉ [apply] ghi. */
    var fraction: Double = 1.0
        private set

    /**
     * Không ảnh nền: hệ số THỰC (≥ [fraction]) theo VÙNG — thanh trên · thanh nút (+ ô trên nó) · khay ô làm việc (+ thẻ,
     * ô trong khay). Ba vùng tách nhau vì nền dưới chúng giống nhau (nền màn + vầng sáng) nhưng thứ nằm trên thì khác:
     * [ĐO bài quét] ở vài màu (sáng · Lục ngọc · ấm/lạnh) chữ trên KHAY đã sát 4.5 trên tâm vầng sáng nên khay không
     * mờ được chút nào, trong khi hai thanh vẫn mờ được tới ~55 % — một hệ số chung là bắt cả màn đứng yên theo khay.
     */
    private var plainTop = 1.0
    private var plainDock = 1.0
    private var plainWell = 1.0

    /** Có ảnh nền: hệ số THỰC của ô TẮT/ô đọc trên thanh nút (≥ [fraction]). */
    private var dockTileArt = 1.0

    private var art = false
    private var floorsFor: Pair<KachiPalette, Double>? = null
    private var topArtStacks: List<Stack> = emptyList()
    private var dockArtStacks: List<Stack> = emptyList()
    private var wellArtStacks: List<Stack> = emptyList()

    /**
     * Đặt bậc [pct] (đi qua [ChromeOpacity.snap]) cho bảng màu ĐANG áp ([KachiTheme.palette] — gọi SAU
     * `applyTheme`) và trạng thái [hasArt] của hình nền. `true` nếu thứ được vẽ ĐỔI. Ở 100 % mọi thứ là 1 ⇒ bật/tắt
     * ảnh hay đổi bảng màu không làm hàm này báo đổi (việc đó của `applyTheme`/`KachiGlass.refresh`).
     */
    internal fun apply(pct: Int, hasArt: Boolean): Boolean {
        val wasF = fraction; val wasTop = plainTop; val wasPlainDock = plainDock; val wasWell = plainWell
        val wasDock = dockTileArt; val wasArt = art
        fraction = ChromeOpacity.fraction(pct)
        art = hasArt
        if (fraction < 1.0) recomputeFloors() else { plainTop = 1.0; plainDock = 1.0; plainWell = 1.0; dockTileArt = 1.0; floorsFor = null }
        if (fraction >= 1.0 && wasF >= 1.0) return false   // 100 % trước và sau: không có gì được làm mờ để đổi
        return fraction != wasF || plainTop != wasTop || plainDock != wasPlainDock || plainWell != wasWell ||
            dockTileArt != wasDock || art != wasArt
    }

    /** Tính lại sàn khi (bảng màu, bậc) đổi — một phép so ở mọi nhịp khác (`sync` chạy 1 Hz). */
    private fun recomputeFloors() {
        val p = KachiTheme.palette
        if (floorsFor?.let { it.first === p && it.second == fraction } == true) return
        val r = ChromeRoles()
        val g = r.plainGrounds()
        plainTop = ChromeStack.floor(fraction, r.plainTop(), g)
        plainDock = ChromeStack.floor(fraction, r.plainDock(), g)
        plainWell = ChromeStack.floor(fraction, r.plainWell(), g)
        dockTileArt = ChromeStack.floor(fraction, listOf(r.offTile()), r.barGroundsAtBase())
        topArtStacks = listOf(r.text()) + r.chipTexts()
        dockArtStacks = listOf(r.text(), r.offTile(), r.onTile())
        wellArtStacks = r.wellOverlays()
        floorsFor = p to fraction
    }

    /** Tone nào được làm trong: chỉ bề mặt TRUNG TÍNH (thẻ) và KHAY. BẬT/LÕM mang thông tin hoặc là ô nhập. */
    fun fades(tone: SurfaceTone): Boolean = tone == SurfaceTone.NEUTRAL || tone == SurfaceTone.WELL

    /**
     * Hệ số THỰC cho một nền mờ dựng lúc này. [onBar] = ô nằm trên thanh nút (ô TẮT/ô đọc); [hasArt] mở ra cho test.
     * Có ảnh nền mà không trên thanh ⇒ đúng [fraction] — nền dưới là khay kính, bộ giải lớp che của khay giữ chữ.
     */
    internal fun surfaceFraction(onBar: Boolean, hasArt: Boolean = WallArtStore.current != null): Double = when {
        fraction >= 1.0 -> 1.0
        !hasArt -> if (onBar) plainDock else plainWell
        onBar -> dockTileArt
        else -> fraction
    }

    /**
     * Hạ alpha của nền [d] theo [surfaceFraction] rồi trả lại chính nó (để bọc cùng dòng ở chỗ dựng). Ở 100 % KHÔNG
     * chạm gì — đầu ra từng byte như hôm nay.
     *
     * Không cần `mutate()`: mỗi lời gọi `KachiTheme.surface`/`card` dựng một `Drawable` MỚI (KDoc ở đó), và
     * `GradientDrawable.setAlpha` chỉ nhân vào màu tô lúc vẽ — không lớp offscreen vì dự án 0 viền [ĐO AOSP r47
     * GradientDrawable.java:741-742]. `LayerDrawable.setAlpha` chuyền xuống mọi lớp con (LayerDrawable.java:1358-1367),
     * nên bề mặt có lớp sắc lĩnh vực cũng mờ đều.
     */
    fun fade(d: Drawable, onBar: Boolean = false): Drawable {
        val t = surfaceFraction(onBar)
        if (t < 1.0) d.alpha = ChromeStack.byteOf(t)
        return d
    }

    /**
     * Thẻ/khay KÍNH trên ảnh: sàn lớp che để thứ bộ giải chữ không thấy vẫn không tệ hơn hôm nay ở vùng ảnh độ chói
     * [l] — `WallWindowDrawable.relocate` lấy `max` với bộ giải chữ. Hai nguồn: thứ nằm TRÊN khay ([well] — ô TẮT · ô
     * BẬT · ô cảnh báo) và chữ MÀU thẻ tự khai ([extraInks], `KachiGlass.apply` — soát 2.87 P2: nhãn ACCENT của
     * `w_board`), mỗi mực một chồng riêng (mực hôm nay đã hụt không kéo mực khác theo). [todaySurfaces] = bề mặt ở
     * 100 %, [surfaces] = bề mặt đang vẽ (đã mờ), [veilMin] = sàn lớp che đang dùng. 100 % / không gì để giữ ⇒ 0.
     */
    internal fun glassFloor(
        l: Double, veil: Int, todaySurfaces: IntArray, surfaces: IntArray, inks: IntArray, extraInks: IntArray,
        well: Boolean, veilMin: Double,
    ): Double {
        if (fraction >= 1.0) return 0.0
        val stacks = (if (well) wellArtStacks else emptyList()) + extraInks.map { Stack(emptyList(), intArrayOf(it)) }
        if (stacks.isEmpty()) return 0.0
        val todayVeil = GlassVeil.alphaFor(l, veil, todaySurfaces, inks)
        return ChromeStack.overlayVeil(l, veil, surfaces, todaySurfaces, todayVeil, stacks, surfaceFraction(onBar = false, hasArt = true), veilMin, max = 1.0)
    }

    /**
     * Soát vòng 2 [P3] — hệ số BỀ MẶT của thẻ/khay kính trên vùng ảnh độ chói [l] (≥ [fraction]): khi lớp che tới 100 % vẫn
     * không giữ được một chồng HÔM NAY đạt (chữ trung tính · thứ trên khay · mực màu KHAI [extraInks] — vd ACCENT lấy theo ảnh
     * trên thẻ NEUTRAL bảng sáng) thì giữ bề mặt của ĐÚNG thẻ đó đục hơn ([ChromeStack.glassSurface]). Lớp che cho từng hệ số
     * thử là đúng lớp che `WallWindowDrawable.relocate` sẽ chọn ([glassVeil]). 100 % ⇒ 1 (không đổi một byte).
     */
    internal fun glassSurface(
        l: Double, veil: Int, pair: IntArray, inks: IntArray, extraInks: IntArray, well: Boolean, veilMin: Double,
    ): Double {
        if (fraction >= 1.0) return 1.0
        val stacks = listOf(Stack(emptyList(), inks)) + (if (well) wellArtStacks else emptyList()) +
            extraInks.map { Stack(emptyList(), intArrayOf(it)) }
        val todayVeil = GlassVeil.alphaFor(l, veil, pair, inks)
        return ChromeStack.glassSurface(l, veil, pair, todayVeil, stacks, surfaceFraction(onBar = false, hasArt = true), fraction) { shown ->
            glassVeil(l, veil, pair, shown, inks, extraInks, well, veilMin)
        }
    }

    /** Lớp che mà `WallWindowDrawable.relocate` chọn cho bề mặt đang vẽ [shown] dưới 100 %: max(bộ giải chữ, [glassFloor]). */
    internal fun glassVeil(
        l: Double, veil: Int, pair: IntArray, shown: IntArray, inks: IntArray, extraInks: IntArray, well: Boolean, veilMin: Double,
    ): Double = maxOf(
        GlassVeil.alphaFor(l, veil, shown, inks, min = veilMin, max = 1.0),
        glassFloor(l, veil, pair, shown, inks, extraInks, well, veilMin),
    )

    /**
     * Alpha của `Drawable` (0..255) cho nền một THANH có màu vai [role] (mang alpha gốc). [lums] = độ chói các ô lưới
     * ảnh dưới thanh (`null` = không có ảnh nền ⇒ sàn vùng [plainTop]/[plainDock]); [tiles] = thanh chở ô (thanh nút) ⇒ ô TẮT/ô BẬT cũng phải
     * đọc được trên nó. Độ đục thực = [ChromeOpacity.effective] với mức cần = [ChromeStack.neededOver]; đổi ra alpha
     * `Drawable` bằng cách chia cho alpha gốc (`GradientDrawable` NHÂN alpha của nó vào màu tô). 100 % ⇒ 255.
     */
    internal fun barAlpha(role: Int, lums: DoubleArray?, tiles: Boolean): Int {
        val f = fraction
        if (f >= 1.0) return 255
        if (lums == null) return ChromeStack.byteOf(if (tiles) plainDock else plainTop)
        val base = ColorMath.alpha(role) / 255.0
        if (base <= 0.0) return 255
        val stacks = if (tiles) dockArtStacks else topArtStacks
        val needed = ChromeStack.neededOver(lums, ColorMath.withAlpha(role, 255), base, stacks, dockTileArt)
        return ChromeStack.byteOf(ChromeOpacity.effective(base, f, needed) / base)
    }
}

/**
 * Các chồng lớp mà màn chính THẬT SỰ vẽ trên một nền mờ, dựng từ bảng màu ĐANG áp ([KachiTheme.palette]) — đầu vào
 * của [ChromeStack]. Mỗi dòng là một chỗ vẽ có thật (tham chiếu ở cuối dòng); thêm một thứ vẽ lên nền mờ mà không
 * thêm chồng của nó ở đây thì bài quét `ChromeOpacityContrastContractTest` (mô hình độc lập) đỏ.
 *
 * Đọc bảng ĐANG áp (không nhận bảng làm tham số) vì nền/mực ô cảnh báo lấy qua CHÍNH hàm vẽ
 * ([GroupTileView.fillOf]/[GroupTileView.tintOf]) — hai hàm đó đọc `KachiTheme`; nhận một bảng khác ở đây là hai
 * nguồn màu lệch nhau.
 */
internal class ChromeRoles {
    private val p: KachiPalette = KachiTheme.palette
    private fun c(s: String) = ColorMath.parse(s)
    private val inks = intArrayOf(c(p.mut), c(p.mut2), c(p.ink))                                     // = KachiGlass.veilInks(NEUTRAL)
    private val tileInks = inks + intArrayOf(c(p.ink2), c(p.icon))                                   // ControlTileFactory.tint (TẮT)
    private val surf = Layer(intArrayOf(c(p.surfFrom), c(p.surfTo)), fades = true)                   // applyBg TẮT · readTileOf · thẻ NEUTRAL
    private val tileOn = Layer(intArrayOf(c(p.tileOnFrom), c(p.tileOnTo)))                           // applyBg BẬT (gradientSoft)

    fun text() = Stack(emptyList(), inks)

    /**
     * Chữ + icon chip thanh trên — màu theo CHÍNH bảng map của thanh ([chipInk], mọi [ChipTone]), mỗi màu một chồng
     * (chip hôm nay đã hụt không kéo chữ thường theo). [ĐO bài quét · soát 2.87 P3] thiếu chúng thì ACCENT_INK của chip
     * BẬT tụt 5.55 → 4.29 ở bảng sáng.
     */
    fun chipTexts(): List<Stack> = ChipTone.entries.map { c(chipInk(it)) }.distinct().map { Stack(emptyList(), intArrayOf(it)) }
    fun offTile() = Stack(listOf(surf), tileInks)
    fun onTile() = Stack(listOf(tileOn), intArrayOf(c(p.inkOnAccent)))

    /** Không ảnh: nền màn + tâm hai vầng sáng (WallView.buildGlow — tâm vầng sáng là màu ĐỦ, đục). */
    fun plainGrounds(): List<Int> {
        val bg = c(p.bg)
        return listOf(bg, ColorMath.over(c(p.glow1), bg), ColorMath.over(c(p.glow2), bg))
    }

    /** Có ảnh: thanh nút ở đúng độ đục hôm nay trên mọi độ chói 0..1 (trần của [KachiChrome.barAlpha]). */
    fun barGroundsAtBase(): List<Int> {
        val bar = c(p.bar)
        return ChromeStack.LUMS.map { ColorMath.over(bar, ColorMath.grayOfLuminance(it)) }
    }

    /** Thứ nằm TRÊN khay kính khi có ảnh nền (khay = lớp che + bề mặt của chính nó, bộ giải lo phần đó). */
    fun wellOverlays(): List<Stack> = listOf(
        offTile(),                                                                                   // ô nút TẮT trong ô giữa màn (WidgetViews.actionTile)
        onTile(),                                                                                    // ô nút BẬT
        warnStack(GroupTone.WARN, emptyList()), warnStack(GroupTone.ALERT, emptyList()),             // ô con nhóm cảnh báo
    )

    /** Không ảnh nền — thanh trên (KachiTopStrip): chữ/chip nằm thẳng trên nền thanh. */
    fun plainTop(): List<Stack> {
        val top = Layer(intArrayOf(c(p.barTop)), fades = true)
        return listOf(Stack(listOf(top), inks)) + chipTexts().map { Stack(listOf(top), it.inks) }
    }

    /** Không ảnh nền — thanh nút (ControlDockView) + ô TẮT/ô đọc (mờ cùng hệ số) + ô BẬT trên nó. */
    fun plainDock(): List<Stack> {
        val bar = Layer(intArrayOf(c(p.bar)), fades = true)
        return listOf(
            Stack(listOf(bar), inks),
            Stack(listOf(bar, surf), tileInks),
            Stack(listOf(bar, tileOn), intArrayOf(c(p.inkOnAccent))),
        )
    }

    /** Không ảnh nền — khay ô (WorkspaceView.makeSlot) + mọi thứ nằm trong khay. */
    fun plainWell(): List<Stack> {
        val well = Layer(intArrayOf(c(p.slot), c(p.slotTo)), fades = true)
        val active = Layer(intArrayOf(c(p.surfOnFrom), c(p.surfOnTo)))                               // kính ACTIVE không ảnh
        return listOf(
            Stack(listOf(well), inks),                                                               // chữ widget thẳng trên khay
            Stack(listOf(well, surf), tileInks),                                                     // thẻ NEUTRAL · ô TẮT
            Stack(listOf(well, tileOn), intArrayOf(c(p.inkOnAccent))),                               // ô BẬT
            Stack(listOf(well, active), intArrayOf(c(p.ink))),                                       // ô nhóm ACTIVE
            warnStack(GroupTone.WARN, listOf(well)), warnStack(GroupTone.ALERT, listOf(well)),       // ô nhóm cảnh báo
        )
    }

    /** Ô con nhóm WARN/ALERT: nền = sắc ngữ nghĩa bán trong suốt, chữ = chính sắc đó — đặt trên [under]. */
    private fun warnStack(t: GroupTone, under: List<Layer>) =
        Stack(under + Layer(intArrayOf(c(groupFill(t)))), intArrayOf(c(groupTint(t))))

    /** Nền + mực ô con nhóm cảnh báo — CÙNG hàm với chỗ vẽ (`GroupTileView.surfaceOf` · `text(…, tintOf(t))`). */
    private fun groupFill(t: GroupTone): String = GroupTileView.fillOf(t)
    private fun groupTint(t: GroupTone): String = GroupTileView.tintOf(t)
}
