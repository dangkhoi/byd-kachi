package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.WidgetTelemetry.BoardCell
import com.byd.clusternav.launcher.WidgetTelemetry.MiniValue
import com.byd.clusternav.launcher.WidgetTelemetry.labelCard
import com.byd.clusternav.launcher.WidgetTelemetry.miniCard
import com.byd.clusternav.launcher.WidgetTelemetry.ringCard
import com.byd.clusternav.launcher.WidgetTelemetry.telemetry
import com.byd.clusternav.launcher.WidgetTelemetry.telemetryMini
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
 *    OVERDRIVE/DASHCAST ⇒ badge nhỏ. Bộ vẽ ở [WidgetTelemetry] (tách 2026-09-21 vì trần 500 dòng).
 *  • **hành động** (`id` trong [ControlRegistry]) — ô BẤM ĐƯỢC qua [ControlTileFactory] (RW0/R2). Trước RW0 nhánh
 *    này rơi vào đường telemetry, [TelemetryReadout.of] trả null và ra ô vô dụng (chữ hoa + "—") — sự thật Đ2.
 *
 * ## ⚠⚠ Bất biến từ 2026-09-21: MỖI bộ vẽ đăng ký một đường ĐỔ GIÁ TRỊ TẠI CHỖ
 * Owner: *"widget curated như Áp suất lốp refresh lấy số mới bị GIẬT"*. Nay mọi bộ vẽ curated **dựng khung một lần**
 * rồi đăng ký hàm đổ vào [WidgetRefreshers]; [refreshRead] gọi hàm đó thay vì tháo/gắn view. Bộ vẽ mới PHẢI làm
 * cùng cách — quên thì ô đó rơi về đường lùi (dựng lại) và giật như cũ, chứ không câm.
 *
 * ⚠ Mỗi hàm đổ mang **tên riêng** (`fillTyreBoard`/`fillEnergy`/…), không phải `fill` dùng chung: bài canh
 * `CarDataDemandRendererContractTest` tra hàm phụ **theo tên** và lấy lần khai ĐẦU TIÊN trong tệp, nên bảy hàm cùng
 * tên làm nó quy nhu cầu dữ liệu của ô lốp cho **mọi** widget. [ĐO] đúng lỗi đó khi lượt này mới viết xong.
 */
object WidgetViews {


    fun build(ctx: Context, id: String, data: WidgetData): View = when (id) {
        "w_clock" -> clock(ctx, data)
        "w_energy" -> energyRing(ctx, data)
        "w_pm25" -> pm25Ring(ctx, data)
        "w_speed" -> speed(ctx, data)
        "w_tire" -> tyreBoard(ctx, data)
        "w_media" -> MediaWidgetView.build(ctx, data)
        "w_car" -> carState(ctx, data)
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
        if (list.isEmpty()) return labelCard(ctx, ctx.getString(R.string.kachi_widget_none), "—", "")
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
     * Trả về số ô con đã đổ lại (0 ⇒ chỗ gọi tự dựng lại cả ô cho chắc).
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
     *
     * ## ⚠⚠ 2026-09-21 — ô ĐỌC hết bị THAY VIEW (owner báo GIẬT)
     * Bản trước dừng ở *"thay đúng ô con cần thay"*: đúng chỗ, nhưng vẫn là `removeViewAt` + `addView` mỗi giây cho
     * mỗi ô curated ⇒ khung mới đo–đặt–vẽ từ đầu, ô vẽ Canvas nạp lại ảnh xe, và mắt thấy một cú giật. Nay thứ tự
     * xử lý là: nút HÀNH ĐỘNG (đọc lại số) → ô NHÓM (đổ chữ qua `binders`) → **ô ĐỌC (đổ số qua
     * [WidgetRefreshers])** → cuối cùng mới là đường LÙI dựng-lại. Ba nhánh đầu đều KHÔNG chạm cây view.
     */
    fun refreshRead(root: View, data: WidgetData): Int {
        var changed = 0
        fun walk(v: View) {
            val tag = v.tag as? WidgetTag
            if (tag != null) {
                val keep = CapabilityCatalog.isWrite(tag.id) || WorkspaceRenderPlanner.selfDriven(tag.id)
                // 2026-09-17 — ô HÀNH ĐỘNG (WRITE) KHÔNG dựng lại (C5), nhưng phải ĐỌC LẠI giá trị THẬT của xe qua
                // hàm refresh đã giữ theo view. Gói lệnh không có refresher ⇒ bỏ qua như cũ.
                if (keep) {
                    WidgetRefreshers.refreshAction(v, data.car)
                    return
                }
                // G1·T3 — ô NHÓM tự đổi chữ TẠI CHỖ. KHÔNG được thay view của nó: nhóm kính/cửa/đèn có **hàng
                // nút bên trong**, thay view là tháo/gắn nút giữa cú chạm ⇒ mất cú bấm (đúng bệnh [SOÁT P1-1]).
                val group = v as? GroupTileView
                if (group != null) {
                    if (group.refresh(data)) changed++
                    return
                }
                // Đường CHÍNH của ô ĐỌC (curated + telemetry): đổ số mới vào CHÍNH view đang hiện.
                if (WidgetRefreshers.refresh(v, data)) {
                    changed++
                    return
                }
                // Đường LÙI — ô chưa đăng ký hàm đổ (bộ vẽ mới, hoặc bộ vẽ cố ý không làm tại chỗ): dựng lại rồi
                // thay vào ĐÚNG chỉ số cũ. Giật như bản trước, nhưng không bao giờ để ô câm.
                val parent = v.parent as? ViewGroup ?: return
                val at = parent.indexOfChild(v)
                val lp = v.layoutParams
                val fresh = (if (tag.compact) mini(v.context, tag.id, data) else build(v.context, tag.id, data))
                    .also { it.tag = tag }
                parent.removeViewAt(at)
                parent.addView(fresh, at, lp)
                changed++
                return      // thẻ đánh dấu một ô con hoàn chỉnh — không đi sâu hơn
            }
            if (v is ViewGroup) for (i in v.childCount - 1 downTo 0) walk(v.getChildAt(i))
        }
        walk(root)
        return changed
    }

    // ── Compact (lưới nhiều widget) ────────────────────────────────────────────────────────────────────
    /**
     * Ô NÉN. Mỗi nhánh truyền một **hàm sinh giá trị** cho [miniCard]: khung dựng một lần, hàm ấy chạy lại mỗi nhịp.
     * Nhờ vậy ô nén cũng hết giật — cùng một cơ chế với ô to, không phải hai đường.
     */
    private fun mini(ctx: Context, id: String, data: WidgetData): View {
        val car = data.car
        return when (id) {
            "w_energy" -> miniCard(ctx, data, "ic-bolt", KachiTheme.GREEN) { d -> MiniValue(d.car.energy.soc?.let { "$it%" } ?: "—", d.car.energy.evRangeKm?.let { "$it km" } ?: "") }
            "w_pm25"   -> miniCard(ctx, data, "ic-leaf", KachiTheme.CYAN) { d -> MiniValue(pm25Ug(d.car)?.toString() ?: "—", "µg · " + (d.car.climate.pm25Level?.let { pm(ctx, it) } ?: "—")) }
            "w_speed"  -> miniCard(ctx, data, "ic-speed", KachiTheme.RED) { d -> MiniValue(d.car.drivetrain.speedKmh?.toString() ?: "—", "km/h") }
            "w_tire"   -> tyreMini(ctx, data)
            "w_clock"  -> miniCard(ctx, data, "ic-sun", KachiTheme.INK) { MiniValue(SimpleDateFormat("HH:mm", LangHost.locale()).format(Date()), SimpleDateFormat("dd/MM", LangHost.locale()).format(Date())) }
            "w_media"  -> miniCard(ctx, data, "ic-music", KachiTheme.AMBER) { d -> MiniValue(d.media?.title ?: "—", d.media?.artist ?: "") }
            "w_car"    -> miniCard(ctx, data, "ic-lock", KachiTheme.GREEN) { MiniValue(ctx.getString(R.string.kachi_widget_car)) }
            "w_board"  -> miniCard(ctx, data, "ic-grid", KachiTheme.ACCENT) { MiniValue(ctx.getString(R.string.kachi_widget_board)) }
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
        // Nút đơn có đường đọc ⇒ ActionTile (view + refresh); gói lệnh ⇒ View trần (không có số để đọc lại).
        var refresh: ((CarStatus) -> Unit)? = null
        val tile = ActionMacros.byId(id)?.let { factory.macroTile(it) }
            ?: ControlRegistry.byId(id)?.let { factory.actionTile(it).also { at -> refresh = at.refresh }.view }
            ?: return labelCard(ctx, id.uppercase(), "—", "")
        val pad = if (size == TileSize.BIG) dpi(ctx, Sp.M) else 0
        return FrameLayout(ctx).apply {
            setPadding(pad, pad, pad, pad)
            addView(tile, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            // 2026-09-17 — giữ hàm refresh theo ô để [refreshRead] đổ giá trị THẬT của xe mà không dựng lại ô.
            refresh?.let { r -> WidgetRefreshers.liveAction(this, r); r(data.car) }
        }
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
     *
     * ## ⚠ 2026-09-21 — đây chính là ô owner báo GIẬT
     * [TyreBoardView] đã có sẵn đường đổ dữ liệu tại chỗ (`set` + `invalidate`) và nó **giữ ảnh xe đã nạp**
     * ([CarImageLayer]); thứ gây giật là chỗ gọi tháo/gắn ô này mỗi giây. Nay khung dựng một lần, mỗi nhịp chỉ gọi
     * `set` — nên ảnh xe không phải nạp lại và không có lượt đo–đặt nào của cây view.
     */
    private fun tyreBoard(ctx: Context, data: WidgetData): View {
        val boardView = TyreBoardView(ctx)
        fun fillTyreBoard(d: WidgetData) {
            val readings = TyreBoard.readings(d.car.tyres)
            val unit = d.units.unitFor(Quantity.PRESSURE)
            val values = readings.map { rd -> rd.pressureKpa?.let { formatPressure(it, d.units) } }
            // Nhiệt độ CŨNG phải đi qua lớp đơn vị (chọn °F thì bảng lốp phải ghi °F) — [ĐO] bản đầu ghép "°C" cứng.
            val tUnit = d.units.unitFor(Quantity.TEMPERATURE)
            val temps = readings.map { rd -> rd.tempC?.let { formatTemp(it.toDouble(), d.units) + tUnit } }
            // R8 — DẤU CHƯA KIỂM cho phần nhiệt: kênh nhiệt lốp ở mức [EvidenceTier.NEEDS_CAR] (feature-id số, chưa
            // xác nhận trên xe owner) nên có thể không bao giờ có số. `EvidenceTier.needsBadge` chỉ đúng cho
            // OVERDRIVE/DASHCAST ⇒ chấm amber KHÔNG áp được ở đây; nói bằng chữ ở dòng chân bảng là đường duy nhất
            // không phải bịa. Ô vẽ vẫn KHÔNG biết gì về mức bằng chứng — chuỗi do chỗ gọi dựng.
            //
            // Chân bảng nay là **KẾT LUẬN** ([TyreBoard.verdict], quyết định ở `:core`) chứ không phải nhãn đơn vị:
            // đơn vị đã đứng ngay cạnh từng số (kiểm toán UX mục 1), nên để nó một mình ở chân bảng là vừa lặp vừa
            // chiếm đúng chỗ đáng giá nhất — dòng cuối là chỗ mắt dừng lại.
            val verdict = TyreBoard.verdict(readings)
            val footer = if (TyreBoard.tempTier.wired) verdict
            else ctx.getString(R.string.kachi_tyre_temp_unverified, verdict)
            boardView.set(readings, values, unit, temps, footer)
        }
        fillTyreBoard(data)
        return WidgetRefreshers.live(boardView, ::fillTyreBoard)
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
    private fun tyreMini(ctx: Context, data: WidgetData): View =
        miniCard(ctx, data, CapabilityGroups.TYRES.icon, KachiTheme.INK) { d ->
            val readings = TyreBoard.readings(d.car.tyres)
            val known = readings.mapNotNull { it.pressureKpa }
            val unit = d.units.unitFor(Quantity.PRESSURE)
            if (known.isEmpty()) MiniValue("—", unit, dim = true)
            else {
                val lo = formatPressure(known.min(), d.units)
                val hi = formatPressure(known.max(), d.units)
                val tone = if (readings.any { it.status.alert }) KachiTheme.AMBER else KachiTheme.INK
                MiniValue(if (lo == hi) lo else "$lo–$hi", unit, tone)
            }
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

    // ── Curated widgets (đọc từ CarStatus) ───────────────────────────────────────────────────────────────
    private fun energyRing(ctx: Context, data: WidgetData): View {
        val card = ringCard(ctx)
        fun fillEnergy(d: WidgetData) {
            val soc = d.car.energy.soc
            val range = d.car.energy.evRangeKm?.let { "≈ $it km" } ?: ""
            card.set((soc ?: 0).toFloat(), KachiTheme.GREEN, soc?.let { "$it%" } ?: "—", ctx.getString(R.string.kachi_widget_battery), range)
        }
        fillEnergy(data)
        return WidgetRefreshers.live(card.root, ::fillEnergy)
    }

    private fun pm25Ring(ctx: Context, data: WidgetData): View {
        val card = ringCard(ctx)
        fun fillAir(d: WidgetData) {
            val ug = pm25Ug(d.car); val lvl = d.car.climate.pm25Level
            val caption = "PM2.5 · " + (lvl?.let { pm(ctx, it) } ?: "—")
            card.set((ug ?: 0) * 1.2f, KachiTheme.CYAN, ug?.toString() ?: "—", "µg/m³", caption)
        }
        fillAir(data)
        return WidgetRefreshers.live(card.root, ::fillAir)
    }

    private fun clock(ctx: Context, data: WidgetData): View {
        val time = tv(ctx, "", 50f, KachiTheme.INK, true)
        val date = tv(ctx, "", 14f, KachiTheme.MUT)
        val outside = tv(ctx, "", 13f, KachiTheme.MUT).apply {
            setPadding(0, dpi(ctx, Sp.S), 0, 0)
            val r = KachiTheme.iconRes("ic-sun")
            if (r != 0) {
                val glyph = resources.getDrawable(r, ctx.theme).apply {
                    setBounds(0, 0, dpi(ctx, Sp.ICON_XS), dpi(ctx, Sp.ICON_XS)); setTint(c(KachiTheme.AMBER))
                }
                setCompoundDrawablesRelative(glyph, null, null, null); compoundDrawablePadding = dpi(ctx, Sp.S)
            }
        }
        val root = col(ctx).apply { addView(time); addView(date); addView(outside) }
        fun fillClock(d: WidgetData) {
            val temp = d.car.climate.outsideTempC?.let { "$it°C" } ?: "—"
            time.text = SimpleDateFormat("HH:mm", LangHost.locale()).format(Date())
            date.text = SimpleDateFormat("EEEE, dd/MM", LangHost.locale()).format(Date())
            outside.text = ctx.getString(R.string.kachi_outside_temp, temp)
        }
        fillClock(data)
        return WidgetRefreshers.live(root, ::fillClock)
    }

    private fun speed(ctx: Context, data: WidgetData): View {
        val number = tv(ctx, "—", 44f, KachiTheme.INK, true)
        val root = col(ctx).apply {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            row.addView(number)
            row.addView(tv(ctx, " km/h", 15f, KachiTheme.MUT))
            addView(row)
            // ⚠ 2026-09-16 — dòng dưới TỪNG đổi thành *"Vượt tốc độ"* (đỏ) khi datum `speed_limit_warning` bật. Cảnh
            // báo quá tốc là chức năng AN TOÀN và owner đã gỡ toàn bộ khỏi launcher: xe tự cảnh báo bằng hệ của nó.
            // Ô này nay chỉ nói nó đang hiện cái gì.
            addView(tv(ctx, ctx.getString(R.string.kachi_speed_current), 13f, KachiTheme.MUT)
                .apply { setPadding(0, dpi(ctx, Sp.S), 0, 0) })
        }
        fun fillSpeed(d: WidgetData) { number.text = d.car.drivetrain.speedKmh?.toString() ?: "—" }
        fillSpeed(data)
        return WidgetRefreshers.live(root, ::fillSpeed)
    }

    private fun carState(ctx: Context, data: WidgetData): View {
        val art = CarMiniView(ctx)      // P3: cửa/cốp tô trên hình xe
        val doorLine = tv(ctx, "", 13f, KachiTheme.GREEN).apply { setPadding(0, dpi(ctx, Sp.S), 0, 0) }
        val tailLine = tv(ctx, "", 13f, KachiTheme.MUT)
        val root = col(ctx).apply {
            addView(art, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(doorLine)
            addView(tailLine)
        }
        fun fillCarState(d: WidgetData) {
            val doors = listOf(d.car.body.doorLfOpen, d.car.body.doorRfOpen, d.car.body.doorLrOpen, d.car.body.doorRrOpen)
            val tail = d.car.body.tailgateOpen
            art.set(doors, tail)
            val anyOpen = doors.any { it == true }
            doorLine.setText(
                when {
                    doors.all { it == null } -> R.string.kachi_doors_unknown
                    anyOpen -> R.string.kachi_doors_open
                    else -> R.string.kachi_doors_closed
                },
            )
            doorLine.setTextColor(c(if (anyOpen) KachiTheme.AMBER else KachiTheme.GREEN))
            tailLine.setText(
                when (tail) {
                    true -> R.string.kachi_tailgate_open
                    false -> R.string.kachi_tailgate_closed
                    null -> R.string.kachi_tailgate_unknown
                },
            )
            tailLine.setTextColor(c(if (tail == true) KachiTheme.AMBER else KachiTheme.MUT))
        }
        fillCarState(data)
        return WidgetRefreshers.live(root, ::fillCarState)
    }

    private fun board(ctx: Context, data: WidgetData): View {
        val energy = BoardCell(ctx, "ic-bolt", KachiTheme.GREEN)
        val air = BoardCell(ctx, "ic-leaf", KachiTheme.CYAN)
        val tyres = BoardCell(ctx, CapabilityGroups.TYRES.icon, KachiTheme.INK)
        val music = BoardCell(ctx, "ic-music", KachiTheme.INK)
        fun rowOf(a: View, b: View) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also { it.setMargins(dpi(ctx, Sp.XS),dpi(ctx, Sp.XS),dpi(ctx, Sp.XS),dpi(ctx, Sp.XS)) })
            addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also { it.setMargins(dpi(ctx, Sp.XS),dpi(ctx, Sp.XS),dpi(ctx, Sp.XS),dpi(ctx, Sp.XS)) })
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; val p = dpi(ctx, Sp.S); setPadding(p, p, p, p)
            addView(rowOf(energy.root, air.root), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(rowOf(tyres.root, music.root), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        fun fillBoard(d: WidgetData) {
            val bat = d.car.energy.soc; val km = d.car.energy.evRangeKm
            val lvl = d.car.climate.pm25Level; val ug = pm25Ug(d.car)
            energy.set(MiniValue(bat?.let { "$it%" } ?: "—", km?.let { "$it km" } ?: "", KachiTheme.GREEN))
            air.set(MiniValue(ug?.let { "${it}µg" } ?: "—", "PM2.5 " + (lvl?.let { pm(ctx, it) } ?: ""), KachiTheme.CYAN))
            // Lốp: qua TyreBoard (ngưỡng TẬP TRUNG) + đơn vị người dùng — không tự chia 100 tại chỗ nữa.
            val tRead = TyreBoard.readings(d.car.tyres)
            val tKnown = tRead.mapNotNull { it.pressureKpa }
            val tUnit = d.units.unitFor(Quantity.PRESSURE)
            val tText = if (tKnown.isEmpty()) null else {
                val lo = formatPressure(tKnown.min(), d.units); val hi = formatPressure(tKnown.max(), d.units)
                if (lo == hi) lo else "$lo\u2013$hi"
            }
            val tone = if (tRead.any { it.status.alert }) KachiTheme.AMBER else KachiTheme.INK
            tyres.set(MiniValue(tText ?: "—", ctx.getString(R.string.kachi_tyre_pressure_unit, tUnit), tone))
            music.set(MiniValue(d.media?.title ?: "—", d.media?.artist ?: "", KachiTheme.INK))
        }
        fillBoard(data)
        return WidgetRefreshers.live(root, ::fillBoard)
    }
}
