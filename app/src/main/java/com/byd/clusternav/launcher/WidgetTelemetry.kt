package com.byd.clusternav.launcher

import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ Ô ĐỌC CHUNG (generic telemetry) + Ô NÉN — dựng theo [WidgetShape], ĐỔ SỐ **TẠI CHỖ** ════════════════════
 *
 * Tách khỏi [WidgetViews] ngày 2026-09-21 vì hai lý do cộng lại:
 *  • tệp đó đã **516 dòng**, quá trần 500 của dự án (CLAUDE.md §4.1), và
 *  • đây là một VAI gọn, tự chứa: *"một mã datum + một [WidgetShape] → một ô, rồi đổ số mới vào chính ô đó"*. Nó
 *    KHÔNG biết widget dựng tay nào, không biết nhóm khả năng, không chạm xe — nó chỉ hỏi [TelemetryReadout]
 *    (giá trị) và [UnitFormat] (đơn vị người dùng chọn).
 *
 * ## ⚠ BẤT BIẾN: khung dựng MỘT LẦN, chỉ số liệu đổi
 * `label` / `shape` / `tier` của một mã datum là **cố định** ([TelemetryRegistry]) ⇒ nhãn, hình, dấu *"chưa kiểm
 * trên xe"* dựng một lần ở hàm dựng; chỉ [TelemetryView.valueText] (và độ mờ suy từ nó) đổi ⇒ vào
 * [TelemetryBody.fill]. Dựng view mới trong `fill` là mang lại đúng cú giật mà [WidgetRefreshers] sinh ra để chữa.
 *
 * ## Vì sao dùng lại `WidgetViews.col`/`WidgetViews.tv`
 * Để ô telemetry KHÔNG lệch phông với widget dựng tay. Có một bộ dựng chữ thứ hai ở đây là cách chắc chắn nhất để
 * hai họ ô trông khác nhau sau vài lượt sửa — cùng lẽ với [MediaWidgetView].
 */
internal object WidgetTelemetry {

    /** Khung của một ô ĐỌC + đường đổ số mới vào CHÍNH khung đó. [fill] tuyệt đối không dựng view mới. */
    internal class TelemetryBody(val root: View, val fill: (TelemetryView) -> Unit)

    /**
     * Giá trị của một ô NÉN ở MỘT nhịp.
     *
     * @property color `null` = giữ màu đã dựng. Chỉ ô lốp cần đổi màu theo nhịp (non/căng ⇒ hổ phách), nên màu là
     *   tham số **tuỳ chọn** thay vì bắt mọi chỗ gọi nhắc lại màu của chính nó.
     */
    internal data class MiniValue(
        val big: String,
        val caption: String = "",
        val color: String? = null,
        val dim: Boolean = false,
    )

    /**
     * Ô NÉN: icon + số lớn + dòng phụ (+ chấm *"chưa kiểm"*). Khung dựng ở hàm dựng, số đổ qua [set].
     *
     * ⚠ Thứ tự KHAI là thứ tự khởi tạo: [bigView]/[subView] phải khai **trước** [root] vì khối dựng của `root` gắn
     * chúng vào cây. Đảo lại là `null` lúc chạy — đúng họ lỗi *"thứ tự khởi tạo field"* đã cắn dự án hai lần.
     *
     * ⚠ Dòng phụ rỗng ⇒ [View.GONE] chứ không phải *"không thêm view"* như bản cũ: `GONE` không được `LinearLayout`
     * đo, nên bố cục giống hệt bản cũ **từng pixel**, mà vẫn còn chỗ để đổ chữ khi xe trả số về.
     */
    internal class MiniCard(
        ctx: Context,
        icon: String,
        private val color: String,
        badge: Boolean,
        domain: Domain?,
    ) {
        private val bigView: TextView = WidgetViews.tv(ctx, "", 17f, color, true)
            .apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val subView: TextView = WidgetViews.tv(ctx, "", 10.5f, KachiTheme.MUT)
            .apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }

        val root: LinearLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            KachiGlass.apply(this, Sp.RADIUS_M, domain = domain)   // P1b: kính khi có ảnh nền, surface() khi không
            val p = dpi(ctx, Sp.S); setPadding(p, p, p, p)
            val r = KachiTheme.iconRes(icon)
            if (r != 0) addView(
                ImageView(ctx).apply { setImageResource(r); setColorFilter(c(color)) },
                LinearLayout.LayoutParams(dpi(ctx, Sp.ICON_S), dpi(ctx, Sp.ICON_S))
                    .also { it.bottomMargin = dpi(ctx, Sp.XS) },
            )
            addView(bigView)
            addView(subView)
            if (badge) addView(badgeView(ctx))
        }

        fun set(v: MiniValue) {
            bigView.text = v.big
            bigView.setTextColor(c(v.color ?: color))
            subView.text = v.caption
            subView.visibility = if (v.caption.isEmpty()) View.GONE else View.VISIBLE
            root.alpha = if (v.dim) 0.5f else 1f
        }
    }

    /**
     * Ô NÉN **sống**: khung dựng một lần, [value] chạy lại mỗi nhịp và kết quả đổ vào chính khung đó.
     *
     * Giữ tên `miniCard` có chủ ý: `LauncherI18nContractTest.UI_SURFACE` coi `miniCard(` là một **bề mặt chữ**, nên
     * mọi chuỗi trần mà chỗ gọi truyền vào vẫn bị bài canh đa ngôn ngữ soi. Đổi tên là lặng lẽ đưa 7 ô nén ra ngoài
     * tầm bài canh ấy.
     */
    internal fun miniCard(
        ctx: Context,
        data: WidgetData,
        icon: String,
        color: String,
        badge: Boolean = false,
        domain: Domain? = null,
        value: (WidgetData) -> MiniValue,
    ): View {
        val card = MiniCard(ctx, icon, color, badge, domain)
        val fillCard = { d: WidgetData -> card.set(value(d)) }
        fillCard(data)
        return WidgetRefreshers.live(card.root, fillCard)
    }

    /**
     * Vòng đo + dòng phụ — khung dựng MỘT LẦN, số đổ lại qua [set]. Dùng cho ô *Năng lượng* và ô *Không khí*.
     *
     * Dòng phụ rỗng ⇒ [View.GONE] (bản cũ: không thêm view). `GONE` không được `LinearLayout` đo nên bố cục giống
     * bản cũ từng pixel, mà vẫn còn chỗ để đổ chữ khi xe trả số về giữa chuyến.
     */
    internal class RingCard(val root: View, private val ring: RingView, private val subView: TextView) {
        fun set(pct: Float, color: String, big: String, small: String, caption: String) {
            ring.set(pct, color, big, small)
            subView.text = caption
            subView.visibility = if (caption.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    internal fun ringCard(ctx: Context): RingCard {
        val ring = RingView(ctx)
        val sub = WidgetViews.tv(ctx, "", 12.5f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, Sp.XS), 0, 0) }
        val root = WidgetViews.col(ctx).apply {
            addView(ring, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(sub)
        }
        return RingCard(root, ring, sub)
    }

    /**
     * Ô con của **bảng tổng hợp**: khung dựng một lần, số đổ lại tại chỗ qua [set].
     *
     * ⚠ Gần giống [MiniCard] nhưng KHÔNG phải nó: ô con của bảng không có lề trong ([Sp.S]) và dòng phụ 11f thay vì
     * 10.5f. Gộp hai cái lại là đổi pixel của một màn owner đã duyệt để tiết kiệm mươi dòng — không đáng.
     */
    internal class BoardCell(ctx: Context, icon: String, private val color: String) {
        private val bigView: TextView = WidgetViews.tv(ctx, "", 17f, color, true)
        private val subView: TextView = WidgetViews.tv(ctx, "", 11f, KachiTheme.MUT)
        val root: LinearLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            KachiGlass.apply(this, Sp.RADIUS_M)
            val r = KachiTheme.iconRes(icon)
            if (r != 0) addView(
                ImageView(ctx).apply { setImageResource(r); setColorFilter(c(color)) },
                LinearLayout.LayoutParams(dpi(ctx, Sp.ICON_S), dpi(ctx, Sp.ICON_S))
                    .also { it.bottomMargin = dpi(ctx, Sp.XS) },
            )
            addView(bigView)
            addView(subView)
        }

        fun set(v: MiniValue) {
            bigView.text = v.big
            bigView.setTextColor(c(v.color ?: color))
            subView.text = v.caption
            subView.visibility = if (v.caption.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** Mini cho 1 telemetry id (icon lĩnh vực + số + đơn vị/nhãn), `"—"` + mờ khi chưa đọc được, badge khi cần. */
    internal fun telemetryMini(
        ctx: Context,
        id: String,
        car: CarStatus,
        units: UnitPrefs = UnitPrefs.DEFAULT,
    ): View {
        val first = read(id, car, units)
        if (first == null) {
            // Mã không có trong bộ đăng ký ⇒ chỉ hiện chính mã đó. Giá trị này không bao giờ đổi, nhưng vẫn đăng ký
            // một hàm đổ RỖNG để nhịp trạng thái xe không dựng lại ô vô ích (đường lùi ở `refreshRead`).
            val card = MiniCard(ctx, "", KachiTheme.MUT, false, null)
            card.set(MiniValue(id))
            return WidgetRefreshers.live(card.root) { }
        }
        // P1 · sắc lĩnh vực lấy TỪ BỘ ĐĂNG KÝ (không đoán theo tên id); tra không ra ⇒ `null` = thẻ trung tính.
        val card = MiniCard(
            ctx, WidgetCatalog.pick(id)?.icon ?: "", KachiTheme.INK,
            first.needsBadge, TelemetryRegistry.byId(id)?.domain,
        )
        fun fillMini(now: CarStatus, unit: UnitPrefs) {
            val v = read(id, now, unit) ?: return
            card.set(MiniValue(v.display, if (v.unit.isNotEmpty()) v.unit else v.label, dim = !v.available))
        }
        fillMini(car, units)
        return WidgetRefreshers.live(card.root) { d -> fillMini(d.car, d.units) }
    }

    /**
     * Ô ĐỌC chung. Giá trị đi qua [UnitFormat] để theo lựa chọn đơn vị của người dùng (R11) — đây là chỗ DUY NHẤT
     * ô giữa màn đổi đơn vị, không rải `/ 100` tại từng bộ vẽ như bản cũ.
     */
    internal fun telemetry(
        ctx: Context,
        id: String,
        car: CarStatus,
        units: UnitPrefs = UnitPrefs.DEFAULT,
    ): View {
        val first = read(id, car, units)
            ?: return WidgetRefreshers.live(labelCard(ctx, id.uppercase(), "—", "")) { }
        val body = when (first.shape) {
            WidgetShape.RING -> telemetryRing(ctx, first)
            WidgetShape.BADGE -> badgeShape(ctx, first)
            WidgetShape.STRIP -> stripShape(ctx, first)
            else -> valueShape(ctx, first)   // DIAL/GAUGE/VALUE/CARD/BOARD/MEDIA → số + đơn vị + nhãn
        }
        // Dấu "chưa kiểm trên xe" đến từ `tier` — CỐ ĐỊNH theo mã datum ⇒ khung ngoài dựng một lần, hàm đổ không
        // bao giờ phải thêm/bớt nó.
        val root = if (!first.needsBadge) body.root else LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(body.root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(badgeView(ctx))
        }
        fun fillShape(now: CarStatus, unit: UnitPrefs) {
            val v = read(id, now, unit) ?: return
            body.fill(v)
            body.root.alpha = if (v.available) 1f else 0.5f
        }
        fillShape(car, units)
        return WidgetRefreshers.live(root) { d -> fillShape(d.car, d.units) }
    }

    /** Một lượt đọc: bộ đăng ký → [CarStatus] → lựa chọn đơn vị. `null` = mã không có trong bộ đăng ký. */
    private fun read(id: String, car: CarStatus, units: UnitPrefs): TelemetryView? =
        TelemetryReadout.of(id, car)?.let { UnitFormat.apply(it, units) }

    // ── Bốn bộ vẽ theo shape: dựng khung + trả đường đổ ────────────────────────────────────────────────
    private fun telemetryRing(ctx: Context, v: TelemetryView): TelemetryBody {
        val ring = RingView(ctx)
        val caption = WidgetViews.tv(ctx, v.label, 12.5f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, Sp.XS), 0, 0) }
        val root = WidgetViews.col(ctx).apply {
            addView(ring, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(caption)
        }
        return TelemetryBody(root) { n ->
            val pctv = n.valueText?.toFloatOrNull()?.let { if (n.unit == "%") it else it.coerceIn(0f, 100f) } ?: 0f
            ring.set(pctv, KachiTheme.CYAN, n.display, n.unit)
        }
    }

    private fun valueShape(ctx: Context, v: TelemetryView): TelemetryBody {
        val number = WidgetViews.tv(ctx, v.display, 34f, KachiTheme.INK, true)
        val unit = WidgetViews.tv(ctx, "", 14f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, Sp.M), 0, 0) }
        val root = WidgetViews.col(ctx).apply {
            addView(eyebrow(ctx, v.label))
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            row.addView(number)
            row.addView(unit)
            addView(row.apply { setPadding(0, dpi(ctx, Sp.XS), 0, 0) })
        }
        return TelemetryBody(root) { n ->
            number.text = n.display
            unit.text = " ${n.unit}"
            unit.visibility = if (n.unit.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun badgeShape(ctx: Context, v: TelemetryView): TelemetryBody {
        val badge = TextView(ctx).apply {
            // ⚠ typeface đậm đi qua `bold = true` chứ KHÔNG đặt riêng: `KachiType.apply` sở hữu CẢ cỡ CẢ nét, đặt
            // typeface trước rồi gọi nó là mất đậm ÂM THẦM (xem KDoc KachiType.apply).
            text = v.display
            KachiType.apply(this, KachiType.SECTION, bold = true); gravity = Gravity.CENTER
            setPadding(dpi(ctx, Sp.L), dpi(ctx, Sp.S), dpi(ctx, Sp.L), dpi(ctx, Sp.S))
            background = KachiTheme.pill(ctx, KachiTheme.CELL)
        }
        val root = WidgetViews.col(ctx).apply {
            addView(eyebrow(ctx, v.label))
            addView(
                badge,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dpi(ctx, Sp.S) },
            )
        }
        return TelemetryBody(root) { n ->
            badge.text = n.display
            badge.setTextColor(c(if (n.available) KachiTheme.INK else KachiTheme.MUT))
        }
    }

    private fun stripShape(ctx: Context, v: TelemetryView): TelemetryBody {
        val number = WidgetViews.tv(ctx, v.display, 22f, KachiTheme.INK, true)
            .apply { setPadding(0, dpi(ctx, Sp.XS), 0, 0) }
        val root = WidgetViews.col(ctx).apply { addView(eyebrow(ctx, v.label)); addView(number) }
        return TelemetryBody(root) { n ->
            number.text = n.display
            number.setTextColor(c(if (n.available) KachiTheme.INK else KachiTheme.MUT))
        }
    }

    // ── Bộ dựng nhỏ dùng chung với widget dựng tay ─────────────────────────────────────────────────────
    internal fun badgeView(ctx: Context): View = TextView(ctx).apply {
        // [type scale] ngoại lệ: dấu "chưa kiểm trên xe" là badge MICRO ở góc thẻ, dưới hẳn bậc nhỏ nhất
        // (CAPTION 12). Đưa lên 12 là +26% trên một dấu cố ý phải LẶNG, và nó `maxLines = 1` nằm chồng trên nội
        // dung ô ⇒ nguy cơ cắt chữ. Cùng họ ngoại lệ với 2 cỡ ô lưới mật độ cao của `CapabilityGridSection`.
        text = ctx.getString(R.string.kachi_badge_unverified); setTextColor(c(KachiTheme.AMBER))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f); gravity = Gravity.CENTER
        setPadding(dpi(ctx, Sp.S), dpi(ctx, Sp.XS), dpi(ctx, Sp.S), dpi(ctx, Sp.XS)); maxLines = 1
        background = GradientDrawable().apply {
            cornerRadius = dpi(ctx, Sp.RADIUS_S).toFloat(); setColor(c(KachiTheme.AMBER_SOFT))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).also { it.topMargin = dpi(ctx, Sp.XS) }
    }

    private fun eyebrow(ctx: Context, s: String) =
        WidgetViews.tv(ctx, s, 11f, KachiTheme.MUT2).apply { letterSpacing = 0.08f }

    /** Thẻ chữ suy giảm: eyebrow + số to + dòng phụ. Dùng cho ô trống và cho mã không tra ra được. */
    internal fun labelCard(ctx: Context, title: String, big: String, sub: String) = WidgetViews.col(ctx).apply {
        addView(eyebrow(ctx, title))
        addView(
            WidgetViews.tv(ctx, big, 30f, KachiTheme.INK, true)
                .apply { setPadding(0, dpi(ctx, Sp.XS), 0, dpi(ctx, Sp.XS)) },
        )
        if (sub.isNotEmpty()) addView(WidgetViews.tv(ctx, sub, 13f, KachiTheme.MUT))
    }
}
