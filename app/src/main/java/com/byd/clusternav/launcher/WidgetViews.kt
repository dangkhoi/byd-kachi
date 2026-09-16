package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Dữ liệu xe MẪU (emulator/demo) — GIỮ cho tương thích; **KHÔNG còn nằm trên đường wire** (OQ1: off-car "—", KHÔNG
 * demo). Đường thật = [CarStatus] LIVE bơm qua [HomeUiState.carStatus]; off-car mọi field null ⇒ widget "—".
 */
object DemoCarData : CarDataPort {
    override fun batteryPercent() = 82
    override fun rangeKm() = 418
    override fun tirePressuresBar() = listOf(2.4, 2.4, 2.3, 2.1)
    override fun pm25Level() = 2
    override fun speedKmh() = 56
    override fun outsideTempC() = 26
}

/**
 * Gói dữ liệu render cho widget: trạng thái xe [car] (nguồn sự thật state) + nhạc [media] (đọc live) + transport
 * [onMedia] + cổng ra lệnh [control].
 *
 * [control] có mặt từ RW0: ô giữa màn nay nhận được **cả hành động** (R2), và hành động thì phải có đường ra xe.
 * Mặc định [NoCar] ⇒ off-car/emulator bấm không làm gì, không sập.
 */
class WidgetData(
    val car: CarStatus = CarStatus(),
    val media: MediaSnapshot? = null,
    val onMedia: (String) -> Unit = {},
    val control: CarControlPort = NoCar,
    /**
     * Lựa chọn ĐƠN VỊ của người dùng (R11–R13). Mặc định = [UnitPrefs.DEFAULT] ⇒ mọi chỗ gọi cũ và test cũ giữ
     * nguyên hành vi (R12: không đổi gì thì không thấy khác biệt).
     */
    val units: UnitPrefs = UnitPrefs.DEFAULT,
    /**
     * U4(b) — nguồn ảnh cho widget trình chiếu. Chỗ gọi đọc thư mục MỘT LẦN rồi truyền vào; để mỗi ô tự đọc thư mục
     * là I/O lặp lại trên thread chính mỗi lần dựng ô.
     */
    val photos: List<String> = emptyList(),
    /** U4(b) — chu kỳ đổi ảnh của widget trình chiếu. */
    val photoIntervalSec: Int = Slideshow.DEFAULT_INTERVAL_SEC,
)

/**
 * Dựng View cho 1 widget. Ba họ:
 *  • **curated** (`w_*` trong [WidgetRegistry]) — thẻ dựng tay bám prototype, đọc từ [CarStatus].
 *  • **generic telemetry** (mọi `id` ĐỌC khác trong [TelemetryRegistry]) — render THEO [WidgetShape] qua
 *    [TelemetryReadout]: RING/DIAL/GAUGE/VALUE/CARD/BOARD/STRIP/BADGE. Field null/off-car ⇒ "—" + mờ; tier
 *    OVERDRIVE/DASHCAST ⇒ badge nhỏ.
 *  • **hành động** (`id` trong [ControlRegistry]) — ô BẤM ĐƯỢC qua [ControlTileFactory] (RW0/R2). Trước RW0 nhánh
 *    này rơi vào đường telemetry, [TelemetryReadout.of] trả null và ra ô vô dụng (chữ hoa + "—") — sự thật Đ2.
 */
object WidgetViews {


    fun build(ctx: Context, id: String, data: WidgetData): View = when (id) {
        "w_clock" -> clock(ctx, data.car)
        "w_energy" -> energyRing(ctx, data.car)
        "w_pm25" -> pm25Ring(ctx, data.car)
        "w_speed" -> speed(ctx, data.car)
        "w_tire" -> tyreBoard(ctx, data)
        "w_media" -> MediaWidgetView.build(ctx, data)
        "w_car" -> carState(ctx, data.car)
        "w_board" -> board(ctx, data)
        "w_photos" -> PhotoWidgetView(ctx).apply { bind(data.photos, data.photoIntervalSec) }
        // G1·T3: NHÓM khả năng → ba bộ vẽ dùng chung. Đặt TRƯỚC nhánh hành động (thứ tự y như
        // [CapabilityCatalog.kindOf]); bảng 4 bánh truyền vào bằng lambda để KHÔNG có bản dựng thứ hai.
        else -> if (CapabilityGroups.byId(id) != null) GroupTiles.build(ctx, id, data) { c, d -> tyreBoard(c, d) }
        // Hành động → ô bấm được; còn lại (đọc) → đường telemetry cũ, KHÔNG đổi một dòng.
        else if (CapabilityCatalog.isWrite(id)) actionTile(ctx, id, data, TileSize.BIG)
        else telemetry(ctx, id, data.car, data.units)
    }

    /**
     * Nội dung ô widget: **1** widget → to, giữa ô; **2..8** → lưới card đều nhau (1·2·3=1 hàng; 4=2+2; 5=2+3;
     * 6=3+3; 7=3+4; 8=4+4).
     */
    fun buildGrid(ctx: Context, ids: List<String>, data: WidgetData): View {
        val list = ids.take(8)
        if (list.isEmpty()) return label(ctx, ctx.getString(R.string.kachi_widget_none), "—", "")
        if (list.size == 1) return build(ctx, list[0], data).also { it.tag = WidgetTag(list[0], compact = false) }
        val n = list.size
        val topN = if (n <= 3) n else n / 2
        val rows = if (n <= 3) listOf(list) else listOf(list.subList(0, topN), list.subList(topN, n))
        val g = dpi(ctx, Sp.XS)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(g, g, g, g)
            rows.forEach { rowIds ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                rowIds.forEach { id ->
                    row.addView(
                        mini(ctx, id, data).also { it.tag = WidgetTag(id, compact = true) },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                            .also { it.setMargins(g, g, g, g) },
                    )
                }
                addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        }
    }

    /** Thẻ gắn lên mỗi view con: mã khả năng + nó được dựng bằng bộ vẽ đầy-đủ hay bộ vẽ nén. */
    private data class WidgetTag(val id: String, val compact: Boolean)

    /**
     * Làm mới **TẠI CHỖ** những ô con là **mục ĐỌC**, giữ nguyên view của nút HÀNH ĐỘNG và của widget tự-lo-nội-dung.
     * Trả về số ô con đã thay (0 ⇒ chỗ gọi tự dựng lại cả ô cho chắc).
     *
     * ## ⚠ [SOÁT P1-1] Vì sao phải có hàm này
     * Luật cũ: ô widget có **bất kỳ** mục đọc ⇒ dựng lại **CẢ Ô** mỗi khi trạng thái xe đổi (trên xe: mỗi giây).
     * Với ô TRỘN (ví dụ áp suất lốp + nút "Đóng hết kính", hoặc trình chiếu ảnh + tốc độ) hậu quả là:
     *  • nút bị tháo/gắn giữa cú chạm ⇒ **mất cú bấm**;
     *  • widget trình chiếu bị dựng lại ⇒ trạng thái quay vòng đặt lại (**ảnh đứng một tấm**) + mỗi giây một lượt
     *    đọc tệp & giải mã ảnh trên thread chính.
     * Bản vá trước chỉ cứu ô mà **mọi** mục là trình chiếu ([WorkspaceRenderPlanner.selfDriven]); ô trộn vẫn hỏng.
     *
     * Cách làm: mỗi view con mang [WidgetTag] nên đổi được **đúng con cần đổi**, không phụ thuộc vào việc đoán lại
     * cấu trúc cây mà [buildGrid] đã dựng.
     */
    fun refreshRead(root: View, data: WidgetData): Int {
        var changed = 0
        fun walk(v: View) {
            val tag = v.tag as? WidgetTag
            if (tag != null) {
                val keep = CapabilityCatalog.isWrite(tag.id) || WorkspaceRenderPlanner.selfDriven(tag.id)
                if (!keep) {
                    // G1·T3 — ô NHÓM tự đổi chữ TẠI CHỖ. KHÔNG được thay view của nó: nhóm kính/cửa/đèn có **hàng
                    // nút bên trong**, thay view là tháo/gắn nút giữa cú chạm ⇒ mất cú bấm (đúng bệnh [SOÁT P1-1]).
                    val group = v as? GroupTileView
                    if (group != null) {
                        if (group.refresh(data)) changed++
                        return
                    }
                    val parent = v.parent as? ViewGroup ?: return
                    val at = parent.indexOfChild(v)
                    val lp = v.layoutParams
                    val fresh = (if (tag.compact) mini(v.context, tag.id, data) else build(v.context, tag.id, data))
                        .also { it.tag = tag }
                    parent.removeViewAt(at)
                    parent.addView(fresh, at, lp)
                    changed++
                }
                return      // thẻ đánh dấu một ô con hoàn chỉnh — không đi sâu hơn
            }
            if (v is ViewGroup) for (i in v.childCount - 1 downTo 0) walk(v.getChildAt(i))
        }
        walk(root)
        return changed
    }

    // ── Compact (lưới nhiều widget) ────────────────────────────────────────────────────────────────────
    private fun mini(ctx: Context, id: String, data: WidgetData): View {
        val car = data.car
        return when (id) {
            "w_energy" -> miniCard(ctx, "ic-bolt", car.energy.soc?.let { "$it%" } ?: "—", car.energy.evRangeKm?.let { "$it km" } ?: "", KachiTheme.GREEN, false)
            "w_pm25"   -> miniCard(ctx, "ic-leaf", pm25Ug(car)?.toString() ?: "—", "µg · " + (car.climate.pm25Level?.let { pm(ctx, it) } ?: "—"), KachiTheme.CYAN, false)
            "w_speed"  -> miniCard(ctx, "ic-speed", car.drivetrain.speedKmh?.toString() ?: "—", "km/h", KachiTheme.RED, false)
            "w_tire"   -> tyreMini(ctx, car, data.units)
            "w_clock"  -> miniCard(ctx, "ic-sun", SimpleDateFormat("HH:mm", LangHost.locale()).format(Date()), SimpleDateFormat("dd/MM", LangHost.locale()).format(Date()), KachiTheme.INK, false)
            "w_media"  -> miniCard(ctx, "ic-music", data.media?.title ?: "—", data.media?.artist ?: "", KachiTheme.AMBER, false)
            "w_car"    -> miniCard(ctx, "ic-lock", ctx.getString(R.string.kachi_widget_car), "", KachiTheme.GREEN, false)
            "w_board"  -> miniCard(ctx, "ic-grid", ctx.getString(R.string.kachi_widget_board), "", KachiTheme.ACCENT, false)
            "w_photos" -> PhotoWidgetView(ctx).apply { bind(data.photos, data.photoIntervalSec) }
            // G1·T3: nhóm trong ô nén ⇒ TÓM TẮT (xem KDoc GroupTiles.mini), không vẽ dải/bảng thu nhỏ.
            else       -> if (CapabilityGroups.byId(id) != null) GroupTiles.mini(ctx, id, data)
            else if (CapabilityCatalog.isWrite(id)) actionTile(ctx, id, data, TileSize.DOCK)
            else telemetryMini(ctx, id, car, data.units)
        }
    }

    /**
     * HÀNH ĐỘNG trong ô giữa màn (R2 — chiều thứ hai, chiều bị chặn trước RW0). Dựng bằng **cùng** bộ dựng với thanh
     * nút ([ControlTileFactory]) nên hai vùng không thể lệch nhau về hình dáng hay hành vi bấm.
     *
     * Mã hành động có trong [CapabilityCatalog] nhưng KHÔNG có trong [ControlRegistry] là không thể theo cách tra
     * ([CapabilityCatalog.isWrite] hỏi đúng bộ đó) — vẫn giữ suy giảm an toàn cũ cho chắc, không sập.
     */
    private fun actionTile(ctx: Context, id: String, data: WidgetData, size: TileSize): View {
        // Gói lệnh (W2) cũng là HÀNH ĐỘNG ⇒ đặt được trong ô giữa màn như mọi nút khác. Đi qua CÙNG lớp đệm với ô nút
        // (bản đầu trả ô trần ⇒ ô gói lệnh dính sát mép khung trong khi ô nút bên cạnh có đệm 12dp).
        val factory = ControlTileFactory(ctx, control = { data.control }, size = size)
        val tile = ActionMacros.byId(id)?.let { factory.macroTile(it) }
            ?: ControlRegistry.byId(id)?.let { factory.actionTile(it) }
            ?: return label(ctx, id.uppercase(), "—", "")
        val pad = if (size == TileSize.BIG) dpi(ctx, Sp.M) else 0
        return FrameLayout(ctx).apply {
            setPadding(pad, pad, pad, pad)
            addView(tile, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    /** Mini cho 1 telemetry id (icon domain + số + đơn vị/nhãn), "—"+mờ khi null, badge khi cần. */
    private fun telemetryMini(ctx: Context, id: String, car: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): View {
        val raw = TelemetryReadout.of(id, car) ?: return miniCard(ctx, "", id, "", KachiTheme.MUT, false)
        val v = UnitFormat.apply(raw, units)
        val icon = WidgetCatalog.pick(id)?.icon ?: ""
        val sub = if (v.unit.isNotEmpty()) v.unit else v.label
        // P1 · sắc lĩnh vực lấy TỪ BỘ ĐĂNG KÝ (không đoán theo tên id); tra không ra ⇒ `null` = thẻ trung tính.
        return miniCard(ctx, icon, v.display, sub, KachiTheme.INK, v.needsBadge, !v.available, TelemetryRegistry.byId(id)?.domain)
    }

    private fun miniCard(ctx: Context, icon: String, big: String, sub: String, color: String, badge: Boolean, dim: Boolean = false, domain: Domain? = null): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = KachiTheme.surface(ctx, Sp.RADIUS_M, domain = domain)
            val p = dpi(ctx, Sp.S); setPadding(p, p, p, p)
            if (dim) alpha = 0.5f
            val r = KachiTheme.iconRes(icon)
            if (r != 0) addView(ImageView(ctx).apply { setImageResource(r); setColorFilter(c(color)) },
                LinearLayout.LayoutParams(dpi(ctx, Sp.ICON_S), dpi(ctx, Sp.ICON_S)).also { it.bottomMargin = dpi(ctx, Sp.XS) })
            addView(tv(ctx, big, 17f, color, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            if (sub.isNotEmpty()) addView(tv(ctx, sub, 10.5f, KachiTheme.MUT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            if (badge) addView(badgeView(ctx))
        }

    // ── Generic telemetry theo shape ────────────────────────────────────────────────────────────────────
    /**
     * Ô ĐỌC chung. Giá trị đi qua [UnitFormat] để theo lựa chọn đơn vị của người dùng (R11) — đây là chỗ DUY NHẤT
     * ô giữa màn đổi đơn vị, không rải `/ 100` tại từng bộ vẽ như bản cũ.
     */
    private fun telemetry(ctx: Context, id: String, car: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): View {
        val raw = TelemetryReadout.of(id, car) ?: return label(ctx, id.uppercase(), "—", "")
        val v = UnitFormat.apply(raw, units)
        val body = when (v.shape) {
            WidgetShape.RING -> telemetryRing(ctx, v)
            WidgetShape.BADGE -> badgeShape(ctx, v)
            WidgetShape.STRIP -> stripShape(ctx, v)
            else -> valueShape(ctx, v)   // DIAL/GAUGE/VALUE/CARD/BOARD/MEDIA → số + đơn vị + nhãn
        }
        if (!v.available) body.alpha = 0.5f
        if (v.needsBadge) return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(badgeView(ctx))
        }
        return body
    }

    private fun telemetryRing(ctx: Context, v: TelemetryView): View = col(ctx).apply {
        val pctv = v.valueText?.toFloatOrNull()?.let { if (v.unit == "%") it else it.coerceIn(0f, 100f) } ?: 0f
        addView(RingView(ctx).apply { set(pctv, KachiTheme.CYAN, v.display, v.unit) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(tv(ctx, v.label, 12.5f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, Sp.XS), 0, 0) })
    }

    private fun valueShape(ctx: Context, v: TelemetryView): View = col(ctx).apply {
        addView(eyebrow(ctx, v.label))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(tv(ctx, v.display, 34f, KachiTheme.INK, true))
        if (v.unit.isNotEmpty()) row.addView(tv(ctx, " ${v.unit}", 14f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, Sp.M), 0, 0) })
        addView(row.apply { setPadding(0, dpi(ctx, Sp.XS), 0, 0) })
    }

    private fun badgeShape(ctx: Context, v: TelemetryView): View = col(ctx).apply {
        addView(eyebrow(ctx, v.label))
        val badge = TextView(ctx).apply {
            // ⚠ typeface đậm đi qua `bold = true` chứ KHÔNG đặt riêng: `KachiType.apply` sở hữu CẢ cỡ CẢ nét, đặt
            // typeface trước rồi gọi nó là mất đậm ÂM THẦM (xem KDoc KachiType.apply).
            text = v.display; setTextColor(c(if (v.available) KachiTheme.INK else KachiTheme.MUT))
            KachiType.apply(this, KachiType.SECTION, bold = true); gravity = Gravity.CENTER
            setPadding(dpi(ctx, Sp.L), dpi(ctx, Sp.S), dpi(ctx, Sp.L), dpi(ctx, Sp.S))
            background = KachiTheme.pill(ctx, KachiTheme.CELL)
        }
        addView(badge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dpi(ctx, Sp.S) })
    }

    private fun stripShape(ctx: Context, v: TelemetryView): View = col(ctx).apply {
        addView(eyebrow(ctx, v.label))
        addView(tv(ctx, v.display, 22f, if (v.available) KachiTheme.INK else KachiTheme.MUT, true).apply { setPadding(0, dpi(ctx, Sp.XS), 0, 0) })
    }

    private fun badgeView(ctx: Context): View = TextView(ctx).apply {
        // [type scale] ngoại lệ: dấu "chưa kiểm trên xe" là badge MICRO ở góc thẻ, dưới hẳn bậc nhỏ nhất
        // (CAPTION 12). Đưa lên 12 là +26% trên một dấu cố ý phải LẶNG, và nó `maxLines = 1` nằm chồng trên nội
        // dung ô ⇒ nguy cơ cắt chữ. Cùng họ ngoại lệ với 2 cỡ ô lưới mật độ cao của `CapabilityGridSection`.
        text = ctx.getString(R.string.kachi_badge_unverified); setTextColor(c(KachiTheme.AMBER)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f); gravity = Gravity.CENTER
        setPadding(dpi(ctx, Sp.S), dpi(ctx, Sp.XS), dpi(ctx, Sp.S), dpi(ctx, Sp.XS)); maxLines = 1
        background = GradientDrawable().apply { cornerRadius = dpi(ctx, Sp.RADIUS_S).toFloat(); setColor(c(KachiTheme.AMBER_SOFT)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dpi(ctx, Sp.XS) }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────────────
    private fun pm(ctx: Context, level: Int) = ctx.getString(
        when { level <= 2 -> R.string.kachi_pm_good; level <= 4 -> R.string.kachi_pm_fair; else -> R.string.kachi_pm_poor },
    )

    /** PM2.5 µg/m³: giá trị thật nếu có, nếu chỉ có mức thì suy diễn xấp xỉ (mức × 9); null → null. */
    private fun pm25Ug(car: CarStatus): Int? = car.climate.pm25ValueUgm3 ?: car.climate.pm25Level?.let { it * 9 }

    // ── W4 · Bảng áp suất lốp 4 bánh ──────────────────────────────────────────────────────────────────
    /**
     * Ô lớn: [TyreBoardView] (hình xe + từng bánh một số).
     *
     * Bản cũ (`tire()`) vẽ 4 ô chữ với **ngưỡng cứng viết tại chỗ** `t[i] < 2.2` — ngưỡng THỨ BA của dự án, lệch với
     * [TyreBoard]. Nay mọi phán xét về non/căng/lệch đến từ [TyreBoard.readings] (thuần, test off-car) và mọi con số
     * đi qua [UnitFormat] ⇒ **một** nơi định nghĩa ngưỡng, **một** nơi đổi đơn vị.
     */
    private fun tyreBoard(ctx: Context, data: WidgetData): View {
        val readings = TyreBoard.readings(data.car.tyres)
        val unit = data.units.unitFor(Quantity.PRESSURE)
        val values = readings.map { rd -> rd.pressureKpa?.let { formatPressure(it, data.units) } }
        // Nhiệt độ CŨNG phải đi qua lớp đơn vị (chọn °F thì bảng lốp phải ghi °F) — [ĐO] bản đầu ghép "°C" cứng.
        val tUnit = data.units.unitFor(Quantity.TEMPERATURE)
        val temps = readings.map { rd -> rd.tempC?.let { formatTemp(it.toDouble(), data.units) + tUnit } }
        // R8 — DẤU CHƯA KIỂM cho phần nhiệt: kênh nhiệt lốp ở mức [EvidenceTier.NEEDS_CAR] (feature-id số, chưa xác
        // nhận trên xe owner) nên có thể không bao giờ có số. `EvidenceTier.needsBadge` chỉ đúng cho
        // OVERDRIVE/DASHCAST ⇒ chấm amber KHÔNG áp được ở đây; nói bằng chữ ở dòng chân bảng là đường duy nhất
        // không phải bịa. Ô vẽ vẫn KHÔNG biết gì về mức bằng chứng — chuỗi do chỗ gọi dựng.
        //
        // Chân bảng nay là **KẾT LUẬN** ([TyreBoard.verdict], quyết định ở `:core`) chứ không phải nhãn đơn vị: đơn
        // vị đã đứng ngay cạnh từng số (kiểm toán UX mục 1), nên để nó một mình ở chân bảng là vừa lặp vừa chiếm
        // đúng chỗ đáng giá nhất — dòng cuối là chỗ mắt dừng lại.
        val verdict = TyreBoard.verdict(readings)
        val footer = if (TyreBoard.tempTier.wired) verdict
        else ctx.getString(R.string.kachi_tyre_temp_unverified, verdict)
        return TyreBoardView(ctx).apply { set(readings, values, unit, temps, footer) }
    }

    /**
     * Ô nhỏ (lưới nhiều widget trong 1 ô): khoảng cao–thấp + màu theo [TyreBoard.anyAlert].
     *
     * ⚠ U7 — hình ở đây là **`ic-group-tyres` (khung xe + BỐN bánh tô)**, không phải `ic-tire` (MỘT bánh):
     * ô này gộp số của cả bốn bánh, nên hình một bánh nói sai nội dung. [ĐO] ảnh máy ảo 2026-09-13: ô
     * *"Tyre pressure (bar)"* giữa màn mang glyph một bánh trong khi nó đang tóm tắt cả bộ.
     * `ic-tire` vẫn sống — nó là hình lùi-về của lĩnh vực Lốp (`WidgetCatalog.iconFor`).
     *
     * Lấy tên hình từ **[CapabilityGroups.TYRES]** chứ không gõ chuỗi: nhóm Lốp là chỗ DUY NHẤT định nghĩa
     * "hình của cả bộ lốp", nên đổi hình ở đó là mọi bề mặt đổi theo — không có bản sao thứ hai phải nhớ sửa.
     */
    private fun tyreMini(ctx: Context, car: CarStatus, units: UnitPrefs): View {
        val readings = TyreBoard.readings(car.tyres)
        val known = readings.mapNotNull { it.pressureKpa }
        val unit = units.unitFor(Quantity.PRESSURE)
        if (known.isEmpty()) return miniCard(ctx, CapabilityGroups.TYRES.icon, "—", unit, KachiTheme.INK, false, dim = true)
        val lo = formatPressure(known.min(), units)
        val hi = formatPressure(known.max(), units)
        val text = if (lo == hi) lo else "$lo–$hi"
        val alert = readings.any { it.status.alert }
        return miniCard(ctx, CapabilityGroups.TYRES.icon, text, unit, if (alert) KachiTheme.AMBER else KachiTheme.INK, false)
    }

    /**
     * kPa → chuỗi theo đơn vị người dùng chọn. Đi qua [UnitFormat] (KHÔNG tự chia 100 tại chỗ như bản cũ — đó chính
     * là chỗ khiến bộ đăng ký nói `kPa` mà widget hiện `bar`).
     */
    private fun formatPressure(kpa: Double, units: UnitPrefs): String {
        val raw = TelemetryView("tyre", "", Units.BASE[Quantity.PRESSURE] ?: "kPa",
            WidgetShape.BOARD, EvidenceTier.PROVEN, trimNumber(kpa))
        return UnitFormat.apply(raw, units).display
    }

    /** °C → chuỗi theo đơn vị nhiệt người dùng chọn (cùng đường với [formatPressure]). */
    private fun formatTemp(celsius: Double, units: UnitPrefs): String {
        val raw = TelemetryView("tyre_t", "", Units.BASE[Quantity.TEMPERATURE] ?: "°C",
            WidgetShape.BOARD, EvidenceTier.NEEDS_CAR, trimNumber(celsius))
        return UnitFormat.apply(raw, units).display
    }

    /** Bỏ ".0" cho số nguyên để chuỗi vào [UnitFormat] gọn (nó tự áp số chữ số thập phân của đơn vị đích). */
    private fun trimNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    internal fun col(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val p = dpi(ctx, Sp.L); setPadding(p, p, p, p)
    }
    internal fun tv(ctx: Context, s: String, sp: Float, color: String, bold: Boolean = false) = TextView(ctx).apply {
        // [type scale] ngoại lệ: cỡ đã là THAM SỐ của hàm — bậc do chỗ GỌI quyết. Các chỗ gọi trong tệp này còn
        // truyền số tay (34/30/22/14/13/12.5/11f: giá trị hero + eyebrow của ô widget, phần lớn nằm ngoài 5 bậc) ⇒
        // việc chuyển chúng là một lượt riêng, không thuộc phạm vi lượt này.
        text = s; setTextColor(c(color)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun eyebrow(ctx: Context, s: String) = tv(ctx, s, 11f, KachiTheme.MUT2).apply { letterSpacing = 0.08f }

    private fun label(ctx: Context, title: String, big: String, sub: String) = col(ctx).apply {
        addView(eyebrow(ctx, title))
        addView(tv(ctx, big, 30f, KachiTheme.INK, true).apply { setPadding(0, dpi(ctx, Sp.XS), 0, dpi(ctx, Sp.XS)) })
        if (sub.isNotEmpty()) addView(tv(ctx, sub, 13f, KachiTheme.MUT))
    }

    private fun ring(ctx: Context, percent: Float, color: String, big: String, small: String, sub: String): View =
        col(ctx).apply {
            addView(RingView(ctx).apply { set(percent, color, big, small) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            if (sub.isNotEmpty()) addView(tv(ctx, sub, 12.5f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, Sp.XS), 0, 0) })
        }

    // ── Curated widgets (đọc từ CarStatus) ───────────────────────────────────────────────────────────────
    private fun energyRing(ctx: Context, car: CarStatus): View {
        val soc = car.energy.soc
        return ring(ctx, (soc ?: 0).toFloat(), KachiTheme.GREEN, soc?.let { "$it%" } ?: "—",
            ctx.getString(R.string.kachi_widget_battery),
            car.energy.evRangeKm?.let { "≈ $it km" } ?: "")
    }

    private fun pm25Ring(ctx: Context, car: CarStatus): View {
        val ug = pm25Ug(car); val lvl = car.climate.pm25Level
        return ring(ctx, (ug ?: 0) * 1.2f, KachiTheme.CYAN, ug?.toString() ?: "—", "µg/m³",
            "PM2.5 · " + (lvl?.let { pm(ctx, it) } ?: "—"))
    }

    private fun clock(ctx: Context, car: CarStatus) = col(ctx).apply {
        addView(tv(ctx, SimpleDateFormat("HH:mm", LangHost.locale()).format(Date()), 50f, KachiTheme.INK, true))
        addView(tv(ctx, SimpleDateFormat("EEEE, dd/MM", LangHost.locale()).format(Date()), 14f, KachiTheme.MUT))
        val outside = ctx.getString(R.string.kachi_outside_temp, car.climate.outsideTempC?.let { "$it°C" } ?: "—")
        addView(tv(ctx, outside, 13f, KachiTheme.MUT).apply {
            setPadding(0, dpi(ctx, Sp.S), 0, 0)
            val r = KachiTheme.iconRes("ic-sun")
            if (r != 0) {
                val d = resources.getDrawable(r, ctx.theme).apply { setBounds(0, 0, dpi(ctx, Sp.ICON_XS), dpi(ctx, Sp.ICON_XS)); setTint(c(KachiTheme.AMBER)) }
                setCompoundDrawablesRelative(d, null, null, null); compoundDrawablePadding = dpi(ctx, Sp.S)
            }
        })
    }

    private fun speed(ctx: Context, car: CarStatus) = col(ctx).apply {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(tv(ctx, car.drivetrain.speedKmh?.toString() ?: "—", 44f, KachiTheme.INK, true))
        row.addView(tv(ctx, " km/h", 15f, KachiTheme.MUT))
        addView(row)
        // ⚠ 2026-09-16 — dòng dưới TỪNG đổi thành *"Vượt tốc độ"* (đỏ) khi datum `speed_limit_warning` bật. Cảnh
        // báo quá tốc là chức năng AN TOÀN và owner đã gỡ toàn bộ khỏi launcher: xe tự cảnh báo bằng hệ của nó.
        // Ô này nay chỉ nói nó đang hiện cái gì.
        addView(tv(ctx, ctx.getString(R.string.kachi_speed_current), 13f, KachiTheme.MUT)
            .apply { setPadding(0, dpi(ctx, Sp.S), 0, 0) })
    }

    private fun carState(ctx: Context, car: CarStatus) = col(ctx).apply {
        addView(CarMiniView(ctx), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val doors = listOf(car.body.doorLfOpen, car.body.doorRfOpen, car.body.doorLrOpen, car.body.doorRrOpen)
        val doorLine = ctx.getString(when {
            doors.all { it == null } -> R.string.kachi_doors_unknown
            doors.any { it == true } -> R.string.kachi_doors_open
            else -> R.string.kachi_doors_closed
        })
        addView(tv(ctx, doorLine, 13f, if (doors.any { it == true }) KachiTheme.AMBER else KachiTheme.GREEN).apply { setPadding(0, dpi(ctx, Sp.S), 0, 0) })
        val tail = ctx.getString(when (car.body.tailgateOpen) {
            true -> R.string.kachi_tailgate_open; false -> R.string.kachi_tailgate_closed
            null -> R.string.kachi_tailgate_unknown
        })
        addView(tv(ctx, tail, 13f, if (car.body.tailgateOpen == true) KachiTheme.AMBER else KachiTheme.MUT))
    }

    private fun board(ctx: Context, data: WidgetData): View {
        val car = data.car
        fun cell(icon: String, big: String, sub: String, color: String) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = KachiTheme.surface(ctx, Sp.RADIUS_M)
            val r = KachiTheme.iconRes(icon)
            if (r != 0) addView(ImageView(ctx).apply { setImageResource(r); setColorFilter(c(color)) },
                LinearLayout.LayoutParams(dpi(ctx, Sp.ICON_S), dpi(ctx, Sp.ICON_S)).also { it.bottomMargin = dpi(ctx, Sp.XS) })
            addView(tv(ctx, big, 17f, color, true))
            if (sub.isNotEmpty()) addView(tv(ctx, sub, 11f, KachiTheme.MUT))
        }
        fun rowOf(a: View, b: View) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also { it.setMargins(dpi(ctx, Sp.XS),dpi(ctx, Sp.XS),dpi(ctx, Sp.XS),dpi(ctx, Sp.XS)) })
            addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also { it.setMargins(dpi(ctx, Sp.XS),dpi(ctx, Sp.XS),dpi(ctx, Sp.XS),dpi(ctx, Sp.XS)) })
        }
        val bat = car.energy.soc; val km = car.energy.evRangeKm; val lvl = car.climate.pm25Level; val ug = pm25Ug(car)
        // Lốp: qua TyreBoard (ngưỡng TẬP TRUNG) + đơn vị người dùng — không tự chia 100 tại chỗ nữa.
        val tRead = TyreBoard.readings(car.tyres)
        val tKnown = tRead.mapNotNull { it.pressureKpa }
        val tUnit = data.units.unitFor(Quantity.PRESSURE)
        val tText = if (tKnown.isEmpty()) null else {
            val lo = formatPressure(tKnown.min(), data.units); val hi = formatPressure(tKnown.max(), data.units)
            if (lo == hi) lo else "$lo\u2013$hi"
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; val p = dpi(ctx, Sp.S); setPadding(p, p, p, p)
            addView(rowOf(
                cell("ic-bolt", bat?.let { "$it%" } ?: "—", km?.let { "$it km" } ?: "", KachiTheme.GREEN),
                cell("ic-leaf", ug?.let { "${it}µg" } ?: "—", "PM2.5 " + (lvl?.let { pm(ctx, it) } ?: ""), KachiTheme.CYAN),
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(rowOf(
                cell(CapabilityGroups.TYRES.icon, tText ?: "—", ctx.getString(R.string.kachi_tyre_pressure_unit, tUnit),
                    if (tRead.any { it.status.alert }) KachiTheme.AMBER else KachiTheme.INK),
                cell("ic-music", data.media?.title ?: "—", data.media?.artist ?: "", KachiTheme.INK),
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }
}
