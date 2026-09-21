package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import kotlin.math.ceil
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BỘ DỰNG Ô DÙNG CHUNG (RW0 · spec `kachi-unified-capability-tile.html` §4.2/§4.3 việc 3).
 *
 * Trước đây phần dựng ô nút sống **bên trong** [ControlDockView], nên ô giữa màn không dùng lại được: nhét một mã
 * hành động vào ô thì nó rơi vào đường telemetry, [TelemetryReadout.of] trả `null` và ra một ô vô dụng (chữ hoa +
 * "—") — đúng sự thật Đ2 đã đo. Nay chỗ dựng ô nằm ở ĐÂY, cả **thanh nút** lẫn **ô giữa màn** ([WidgetViews]) gọi
 * chung, nên hai chiều đang bị chặn của R2 dùng CÙNG một bộ vẽ (sửa một chỗ, cả hai vùng đổi theo).
 *
 * Hai loại ô, đúng theo [CapabilityKind]:
 *  • [actionTile] — **HÀNH ĐỘNG** (bấm được), render theo [ControlKind]: TOGGLE · STEP · COVER · SELECT · BUTTON.
 *  • [readTile] — **ĐỌC** (KHÔNG bấm): icon + nhãn + số + đơn vị, cập nhật tại chỗ qua [ReadTile.bind].
 *
 * Tier OVERDRIVE/DASHCAST ⇒ chấm mờ "chưa kiểm trên xe" (cả hai loại; U10 — vẽ bằng [PickerBadge.dot], xem
 * [withBadge]). **KHÔNG gate an toàn** — mọi ô bấm được bất kể tốc độ/số (owner bỏ gate 2026-09-10).
 */
class ControlTileFactory(
    private val ctx: Context,
    private val control: () -> CarControlPort,
    private val size: TileSize = TileSize.DOCK,
    private val state: ControlTileState = ControlTileState.shared,
    /**
     * Có vẽ icon trong ô không.
     *
     * `false` chỉ dùng khi icon **không mang thông tin** — cụ thể là hàng nút của một ô nhóm mà ≥3 nút dùng chung một
     * hình (xem [GroupBoardModel.actionIconsDistinguish]). Quyết định đó nằm ở `:core`; ở đây chỉ thi hành.
     *
     * Mặc định `true` ⇒ thanh nút và ô giữa màn **không đổi một pixel**.
     */
    private val icons: Boolean = true,
) {

    // ── HÀNH ĐỘNG ───────────────────────────────────────────────────────────────────────────────────────
    /**
     * Ô bấm được cho [def]. Không đặt `layoutParams` — cỡ do VÙNG quyết định (thanh nút vs ô giữa màn).
     *
     * Trả [ActionTile] (view + [ActionTile.refresh]): từ 2026-09-17 ô control **đọc giá trị THẬT của xe** theo nhịp
     * poll ([CarStatus.controls]) và cập nhật TẠI CHỖ — không dựng lại ô (ràng buộc C5). `refresh` là no-op cho
     * COVER/BUTTON (chúng không mang một con số/trạng-thái bền để đọc lại).
     */
    fun actionTile(def: ControlDef): ActionTile {
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
        }
        val icon = ImageView(ctx).apply {
            val r = iconRes(def); if (r != 0) setImageResource(r)
            layoutParams = LinearLayout.LayoutParams(dpi(ctx, size.iconDp), dpi(ctx, size.iconDp))
                .also { it.bottomMargin = dpi(ctx, Sp.XS) }
        }
        val label = TextView(ctx).apply {
            // [SOÁT P3] `maxLines = 1` mà KHÔNG ellipsize ⇒ chữ bị cắt CỨNG, không có "…": trong ô 84dp thì 4 ô kính
            // ("Kính trước-trái/phải", "Kính sau-trái/phải") trông gần như y hệt nhau. Ô ĐỌC đã sửa từ gói 2, ô hành
            // động thì chưa.
            //
            // ⚠ [KIỂM TOÁN 2026-09-12 mục 4] Hai dòng vẫn KHÔNG đủ cho ô hẹp: [ĐO] ô 82px của hàng nút nhóm cắt
            // `"Window front-right"` thành `"Window front-ri…"` và `"Kính trước-phải"` thành `"Kính trước-p…"` ⇒ hai
            // kính TRƯỚC đọc ra y hệt nhau, đúng lỗi mà `short` đã chữa cho ô ĐỌC. Ở vùng hẹp dùng bản NGẮN; vùng
            // rộng giữ nhãn đầy (không thu chữ ở nơi không cần).
            text = if (size.narrow) def.displayShortLabel else def.displayLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp); gravity = Gravity.CENTER
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        if (icons) content.addView(icon)

        val refresh: (CarStatus) -> Unit = when (def.kind) {
            ControlKind.TOGGLE -> tileToggle(def, content, icon, label)
            ControlKind.STEP -> tileStep(def, content, icon, label)
            // WP2 · R2.3 — COVER nay CÓ đường đọc lại (cốp/kính mở ⇒ màu nhấn); trước WP2 nhánh này trả no-op.
            ControlKind.COVER -> tileCover(def, content, icon, label)
            ControlKind.SELECT -> tileSelect(def, content, icon, label)
            ControlKind.BUTTON -> { tileButton(def, content, icon, label); {} }
        }
        val outer = if (ControlTileLogic.needsBadge(def)) withBadge(content) else content
        return ActionTile(outer, refresh)
    }

    private fun tileToggle(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView): (CarStatus) -> Unit {
        tile.addView(reserveTwoLines(label))
        // WP2 · R2.1 — trạng thái nói bằng ICON + MÀU, KHÔNG bằng chữ "Bật/Tắt" (xem KDoc [ControlVisual]).
        look(def, tile, icon, label, on(def))
        tile.setOnClickListener {
            state.touch(def.id)   // ân hạn: đừng để nhịp poll nháy ngược ngay sau khi vừa bấm
            val nv = !state.isOn(def.id); state.setOn(def.id, nv)
            look(def, tile, icon, label, on(def)); control().toggle(def.id, nv)
        }
        // Đọc lại trạng thái THẬT của xe (0/1) — bỏ qua trong cửa sổ ân hạn, và chỉ đổi khi khác để không vẽ thừa.
        return refresh@{ car ->
            if (state.touchedWithin(def.id)) return@refresh
            val on = (car.controls[def.id] ?: return@refresh) > 0
            if (on != state.isOn(def.id)) { state.setOn(def.id, on); look(def, tile, icon, label, on(def)) }
        }
    }

    /** Cờ bật/tắt đang giữ, quy về con số mà [ControlVisuals.of] nhận cho mọi kiểu ô. */
    private fun on(def: ControlDef): Int = if (state.isOn(def.id)) 1 else 0

    private fun tileStep(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView): (CarStatus) -> Unit {
        val vtext = TextView(ctx).apply {
            text = ControlVisuals.stepText(def, state.value(def))
            setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; maxLines = 1
            setPadding(dpi(ctx, Sp.XS), 0, dpi(ctx, Sp.XS), 0)
            // WP2 · R2.4 — SÀN bề ngang = chỗ cho con số DÀI NHẤT của **mọi** nút STEP
            // ([ControlVisuals.STEP_VALUE_CHARS], suy từ registry) ⇒ `"4"` và `"22°"` chiếm CÙNG một ô, nên hai
            // nút −/+ đứng đúng một chỗ ở mọi ô stepper. Đo bằng chính `paint` (sau khi đã đặt cỡ chữ) chứ không
            // gõ một con số dp: cỡ chữ khác nhau theo vùng ([TileSize.valueSp]) nên một hằng dp sẽ sai ở BIG.
            minWidth = ceil(paint.measureText(DIGIT.repeat(ControlVisuals.STEP_VALUE_CHARS)).toDouble()).toInt()
        }
        look(def, tile, icon, label, state.value(def))
        val minus = stepBtn("−"); val plus = stepBtn("+")
        // ⚠ H1 — mốc để cộng/trừ là mức THẬT của xe, không phải mức lạc quan trong [ControlTileState]: người lái chỉnh
        // gió ở màn BYD gốc thì bảng kia không biết, nên nó vẫn giữ mặc định (gió 4 · nhiệt 22) và một cú bấm "+" nhảy
        // mấy nấc cùng lúc — đúng lỗi tester 1.66 báo. Đọc `null` (off-car · nút chưa có đường đọc) ⇒ lùi về hành vi
        // 1.68. MỘT lượt đọc cho MỘT cú chạm, KHÔNG đọc lúc dựng ô hay theo nhịp vẽ (ngân sách [ĐO xe 1.68] 33 lượt
        // đọc HAL/phút) — nên nó chạy trên luồng vẽ y như lượt `step()` ghi ngay sau, không đổi mô hình luồng tệp này.
        fun nudge(delta: Int) {
            state.touch(def.id)
            val base = runCatching { control().readState(def.id) }.getOrNull() ?: state.value(def)
            val nv = def.clamp(base + delta); state.setValue(def.id, nv)
            vtext.text = ControlVisuals.stepText(def, nv); look(def, tile, icon, label, nv); control().step(def.id, nv)
        }
        minus.setOnClickListener { nudge(-def.step) }
        plus.setOnClickListener { nudge(def.step) }
        // R2.4 — ba cột theo TỈ LỆ (bề rộng 0 + weight) thay vì "hai nút weight, chữ WRAP" như trước WP2: với WRAP,
        // bề ngang ô giá trị đổi theo độ dài con số nên hai nút −/+ trôi sang chỗ khác ở mỗi nút (gió `"4"` vs nhiệt
        // `"22°"`) — đúng cái owner gọi là "không cân đối". Tỉ lệ cố định ⇒ mọi ô stepper có cùng bố cục.
        tile.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(minus, LinearLayout.LayoutParams(0, WRAP, SIDE_WEIGHT))
            addView(vtext, LinearLayout.LayoutParams(0, WRAP, VALUE_WEIGHT))
            addView(plus, LinearLayout.LayoutParams(0, WRAP, SIDE_WEIGHT))
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        // [R7] Đích chạm: nới VÙNG NHẬN CHẠM ra nửa ô (≥ Sp.TOUCH bề dọc), KHÔNG nới cái nút — nới nút thì
        // 2×48 > 68dp dùng được của ô và chữ giá trị xuống hai dòng ([ĐO] ghi ở KDoc Sp.TOUCH_TIGHT).
        StepTouchTarget.attach(tile, minus, plus)
        // Đọc lại con số THẬT của xe (nhiệt/gió/âm lượng) — bỏ qua trong ân hạn, chỉ đổi chữ khi khác.
        return refresh@{ car ->
            if (state.touchedWithin(def.id)) return@refresh
            val v = car.controls[def.id] ?: return@refresh
            if (v != state.value(def)) {
                state.setValue(def.id, v)
                vtext.text = ControlVisuals.stepText(def, v); look(def, tile, icon, label, v)
            }
        }
    }

    /**
     * COVER — **WP3-v5 · Task B** (owner: *"sao phải có chữ close/open"*): cốp/rèm KHÔNG còn chữ Đóng/Mở/Nửa.
     * MỘT tile text-free, trạng thái nói bằng MÀU + (nếu nhiều mức) VẠCH:
     *  • ≤ 2 mức (cốp: đóng/mở) ⇒ như TOGGLE — icon active/mờ + đổi màu.
     *  • ≥ 3 mức (rèm: đóng/mở/nửa) ⇒ VẠCH như ghế ([ControlLevelBar], lit = mức hiện tại), KHÔNG chữ.
     * Bấm CYCLE qua các mức, gửi `coverLevel(id, mức)`. Số mức lấy từ `displayArgs` (rỗng ⇒ 2 = đóng/mở).
     * Đường đọc-lại xe (WP2 · R2.3, cốp/kính mở ⇒ màu nhấn) giữ nguyên.
     */
    private fun tileCover(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView): (CarStatus) -> Unit {
        tile.addView(reserveTwoLines(label))
        val levels = def.displayArgs.size.coerceAtLeast(2)
        // ≥3 mức ⇒ dải vạch (như ghế); ≤2 mức ⇒ chỉ active/mờ (như toggle). KHÔNG TextView chữ mức nào.
        val bar = if (levels > 2) ControlLevelBar.build(ctx, levels - 1) else null
        bar?.let { tile.addView(it) }
        fun show(i: Int) {
            look(def, tile, icon, label, i)      // MỘT cửa áp trạng thái (bg/icon/nhãn) do :core quyết
            bar?.let { ControlLevelBar.light(it, i) }
        }
        show(state.sel(def.id).coerceIn(0, levels - 1))
        tile.setOnClickListener {
            state.touch(def.id)
            val next = ControlTileLogic.nextSelectIndex(state.sel(def.id), levels)
            state.setSel(def.id, next); show(next); control().coverLevel(def.id, next)
        }
        // WP2 · R2.3 — đang mở ⇒ ô mang màu nhấn; đọc lại xe theo nhịp poll, bỏ qua trong ân hạn, chỉ vẽ lại khi ĐỔI
        // (`applyBg` dựng Drawable mới mỗi lượt, nhịp 1 Hz). Cốp đọc cờ mở/đóng · kính đọc % · nút không readKey ⇒ null.
        var open = false
        return refresh@{ car ->
            if (state.touchedWithin(def.id)) return@refresh
            val v = ControlVisuals.of(def, car.controls[def.id] ?: return@refresh)
            if (v.active != open) { open = v.active; applyBg(tile, v.active); tint(icon, label, v.active) }
        }
    }

    private fun tileSelect(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView): (CarStatus) -> Unit {
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 1.5f); tile.addView(label)
        // WP2 · R2.2 — nút NHIỀU MỨC (ghế mát/sưởi) nói mức bằng VẠCH; nút là một TẬP LỰA CHỌN (màu đèn viền · chế
        // độ đèn pha · EV/HEV …) thì GIỮ chữ, vì ở đó chữ là thông tin duy nhất của ô. Phép phân biệt + số vạch ở
        // `:core` ([ControlVisuals.isLevelScale]) — xem KDoc [ControlVisual] về vì sao không áp "bỏ chữ" cho cả hai.
        val ticks = ControlVisuals.tickCount(def)
        val bar = if (ticks > 0) ControlLevelBar.build(ctx, ticks) else null
        val optView = if (bar != null) null else TextView(ctx).apply {
            setTextColor(c(KachiTheme.ACCENT_INK))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size.optionSp); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            maxLines = 1; setPadding(0, dpi(ctx, Sp.XS), 0, 0)
        }
        tile.addView(bar ?: optView)
        fun show(index: Int) {
            val v = look(def, tile, icon, label, index)
            bar?.let { ControlLevelBar.light(it, v.lit) }
            optView?.text = v.option
        }
        show(state.sel(def.id))
        tile.setOnClickListener {
            state.touch(def.id)
            val next = ControlTileLogic.nextSelectIndex(state.sel(def.id), def.args.size)
            state.setSel(def.id, next); show(next); control().select(def.id, next)
        }
        // Đọc lại chỉ số lựa chọn THẬT của xe — bỏ qua trong ân hạn, chỉ nhận chỉ số hợp lệ (trong phạm vi args).
        return refresh@{ car ->
            if (state.touchedWithin(def.id)) return@refresh
            val v = car.controls[def.id] ?: return@refresh
            if (v != state.sel(def.id) && v >= 0 && v < def.args.size) { state.setSel(def.id, v); show(v) }
        }
    }

    private fun tileButton(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        tile.addView(reserveTwoLines(label)); look(def, tile, icon, label, 0)
        tile.setOnClickListener {
            look(def, tile, icon, label, 1)
            state.touch(def.id)
            control().press(def.id)
            tile.postDelayed({ look(def, tile, icon, label, 0) }, 220)   // nháy sáng momentary
        }
    }

    // ── GÓI LỆNH (W2) ───────────────────────────────────────────────────────────────────────────────────
    /**
     * Ô cho một GÓI LỆNH — bấm một phát, chạy nhiều lệnh theo thứ tự.
     *
     * Trông như ô bấm-một-phát (không có trạng thái bật/tắt vì gói không có trạng thái), nhưng:
     *  • chạy trên **thread nền**: gói có chờ giữa các bước (mặc định 400 ms/bước) nên chạy trên thread chính sẽ
     *    treo giao diện đúng bằng tổng thời gian chờ;
     *  • **chống bấm kép**: gói đang chạy thì bấm thêm không xếp thêm lượt — bắn hai lượt "đóng hết kính" chồng nhau
     *    là cách chắc chắn để một lệnh bị bỏ;
     *  • mỗi bước đi qua **đúng cửa theo kiểu nút** ([actByKind]) — KHÔNG bắn tất cả qua `toggle`, xem KDoc của
     *    [actByKind] để biết vì sao cách kia sai;
     *  • kết quả ghi ra nhật ký kèm ĐÍCH DANH bước hỏng (bộ chạy thuần đã trả về danh sách đó).
     */
    fun macroTile(macro: ActionMacro): View {
        val tile = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
        }
        val r = KachiIcons.res(macro.icon, size.iconDp)
        val icon = ImageView(ctx).apply { if (r != 0) setImageResource(r) }
        // Cùng hàng thì cùng luật: hàng nút nào bỏ icon thì ô gói lệnh cũng bỏ, không thì một hàng có hai kiểu ô.
        if (icons) tile.addView(icon, LinearLayout.LayoutParams(dpi(ctx, size.iconDp), dpi(ctx, size.iconDp)))
        val label = TextView(ctx).apply {
            text = macro.displayLabel; setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp)
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        tile.addView(label)
        applyBg(tile, false); tint(icon, label, true)

        tile.setOnClickListener {
            // Chốt theo MÃ GÓI ở bảng dùng chung — không theo View (xem ControlTileState.beginRun).
            if (!state.beginRun(macro.id)) return@setOnClickListener
            applyBg(tile, true); tint(icon, label, true)
            val port = control()
            MacroExec.submit(macro.id) {                      // [SOÁT P3] luồng daemon + có trần, xem KDoc MacroExec
                var res: MacroResult? = null
                try {
                    res = MacroRunner.run(
                        macro,
                        emit = { id, arg -> runCatching { port.actByKind(id, arg) }.getOrDefault(false) },
                        sleep = { ms -> runCatching { Thread.sleep(ms) } },
                    )
                    Log.i(TAG_MACRO, "${macro.id}: ${res.summary()}")
                } catch (t: Throwable) {
                    Log.w(TAG_MACRO, "${macro.id}: hỏng giữa lượt chạy", t)
                } finally {
                    // Nhả cờ NGAY TRÊN THREAD NÀY, KHÔNG nhả bên trong `tile.post`: `View.post` gọi trên view đã bị
                    // `removeView` (đổi bố cục / dựng lại ô giữa lúc gói đang chạy) chỉ XẾP HÀNG chờ lần gắn lại —
                    // có thể KHÔNG BAO GIỜ tới ⇒ cờ kẹt `true`, ô chết hẳn, bấm mãi không chạy nữa. Việc phục hồi
                    // giao diện thì vẫn phải về thread chính nên để trong `post`.
                    state.endRun(macro.id)
                    // Gói vừa GHI THẬT vào các nút bật/tắt ⇒ phải ghi lại vào bảng trạng thái DÙNG CHUNG, không thì
                    // ô "Đèn đọc" vẫn sáng sau khi gói "Rời xe" đã tắt đèn — hai bề mặt nói hai điều về MỘT cái xe.
                    //
                    // Ghi NGAY TRÊN THREAD NÀY, KHÔNG đặt trong `tile.post`: nếu ô đã bị `removeView` (người dùng đổi
                    // bố cục / đổi đơn vị / đổi hồ sơ giữa lúc gói đang chạy) thì việc `post` có chạy hay không là
                    // hành vi mà tài liệu Android KHÔNG nói rõ. Thiết kế này **không cần biết câu trả lời**: dữ liệu
                    // đi đường riêng, còn `post` chỉ làm việc trang trí (nếu ô mất thì trang trí cũng vô nghĩa).
                    // An toàn vì [ControlTileState] dùng map đồng thời.
                    // Chỉ ghi bước ĂN, và chỉ với TOGGLE (COVER/STEP/SELECT không giữ cờ bật/tắt trong ô).
                    macro.steps.zip(res?.results ?: emptyList()).forEach { (step, sr) ->
                        if (sr.ok && ControlRegistry.byId(step.controlId)?.kind == ControlKind.TOGGLE) {
                            state.setOn(step.controlId, step.arg > 0)
                        }
                    }
                    // Báo cho NGƯỜI DÙNG khi có bước không ăn. Trước đây kết quả chỉ vào nhật ký, mà người lái
                    // không bao giờ đọc nhật ký ⇒ bấm "Rời xe" xong xe khoá mà kính chưa đóng thì không ai biết.
                    // Thành công thì IM LẶNG (không ai muốn bị thông báo mỗi lần bấm đúng).
                    val notice = res?.notice(macro.displayLabel)
                    tile.post {
                        runCatching {
                            applyBg(tile, false); tint(icon, label, true)
                            if (notice != null) Toast.makeText(ctx, notice, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        return if (macro.needsBadge()) withBadge(tile) else tile
    }

    // ── HÀNH ĐỘNG CỦA LAUNCHER (S4 · R12) ───────────────────────────────────────────────────────────────
    /**
     * Ô cho một [CapabilityKind.LAUNCHER] — **trông y như ô [ControlKind.BUTTON]**, nhưng cú bấm đi vào [onTap]
     * (mở ngăn kéo / mở Cài đặt) chứ KHÔNG vào [control].
     *
     * Ba khác biệt so với [actionTile], cả ba đều có lý do:
     *  • **Không qua [CarControlPort]**: mã này không có dòng nào trong [ControlRegistry]; bắn `press` xuống cổng xe
     *    là gửi một lệnh không tồn tại tới phần cứng.
     *  • **Không chấm "chưa kiểm"**: tier luôn [EvidenceTier.PROVEN] (xem KDoc [LauncherActions]) ⇒ không gọi
     *    [withBadge]. Dấu đó nói *"lệnh xe này chưa chạy thật"* — dán lên nút mở ngăn kéo là nói sai.
     *  • **Không giữ trạng thái bật/tắt**: nó là cú bấm một phát, nên chỉ nháy sáng 220 ms như [tileButton] rồi trả
     *    nền về. Dùng lại đúng con số của [tileButton] để hai ô cạnh nhau không nháy hai nhịp khác nhau.
     */
    fun launcherTile(pick: CapabilityPick, onTap: () -> Unit): View {
        val tile = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
        }
        val r = KachiIcons.res(pick.icon, size.iconDp)
        val icon = ImageView(ctx).apply { if (r != 0) setImageResource(r) }
        if (icons) tile.addView(icon, LinearLayout.LayoutParams(dpi(ctx, size.iconDp), dpi(ctx, size.iconDp)))
        val label = TextView(ctx).apply {
            text = pick.displayLabel; setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp)
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        tile.addView(reserveTwoLines(label))
        applyBg(tile, false); tint(icon, label, true)
        tile.setOnClickListener {
            applyBg(tile, true); tint(icon, label, true)
            onTap()
            tile.postDelayed({ applyBg(tile, false); tint(icon, label, true) }, 220)   // nháy sáng momentary
        }
        return tile
    }

    // ── ĐỌC ─────────────────────────────────────────────────────────────────────────────────────────────
    /**
     * Ô CHỈ-XEM cho [pick]: icon + nhãn + số + đơn vị. **KHÔNG gắn `setOnClickListener`** — thông tin đọc không
     * phải nút; gắn chạm vào đây là xoá luôn ranh giới ĐỌC/HÀNH ĐỘNG mà cả gói này dựng ra (test khoá điều đó).
     *
     * Số KHÔNG được nhét vào lúc dựng: gọi [ReadTile.bind] để đổ giá trị và đổi giá trị VỀ SAU **tại chỗ** — nhờ
     * vậy nhịp trạng thái xe không phải dựng lại view (ràng buộc C5: dựng lại là nháy + mất trạng thái vừa bấm).
     */
    fun readTile(pick: CapabilityPick): ReadTile = readTileOf(ctx, size, pick) { withBadge(it) }

    // ── Helper dùng chung ───────────────────────────────────────────────────────────────────────────────
    /** Hình của [def] ở cỡ vùng hiện tại — phép tra ở [controlIconRes] (`TileSize.kt`). */
    private fun iconRes(def: ControlDef): Int = controlIconRes(def, size.iconDp)

    /**
     * WP2 — áp trạng thái hiển thị do `:core` quyết ([ControlVisuals.of]) lên nền + icon + nhãn, rồi trả về
     * chính trạng thái đó cho chỗ gọi dùng tiếp (vạch mức · chữ lựa chọn · chữ ô giá trị).
     *
     * **MỘT cửa cho cả năm [ControlKind]** — trước WP2 mỗi hàm dựng tự gọi `applyBg`/`tint` với cờ riêng và
     * bốn trong năm hàm truyền `active = true` cứng, tức nền nói *"tắt"* mà mực nói *"bật"*. Xem KDoc
     * [ControlVisual] về lý do luật nằm ở `:core`.
     */
    private fun look(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView, value: Int?): ControlVisual {
        val v = ControlVisuals.of(def, value)
        applyBg(tile, v.active); tint(icon, label, v.active)
        return v
    }

    private fun tint(icon: ImageView, label: TextView, active: Boolean) {
        // ⚠ [T1 · ĐO TỪ PIXEL] Icon lúc BẬT dùng [KachiTheme.INK_ON_ACCENT], KHÔNG dùng `ON_ACCENT`. Hai vai này
        // khác nhau đúng ở chỗ nền: `ON_ACCENT` (trắng) dành cho nền nhấn **ĐẶC** (gradient của pill/nút); còn ô này
        // dùng `gradientSoft` = nền nhấn **BÁN TRONG SUỐT**, nên ở bảng sáng nó trộn ra tím nhạt (214,217,248) và
        // icon trắng chỉ còn **1.39:1** — [ĐO] trên ảnh thanh nút xe, glyph ổ khoá gần như biến mất. Ở bảng tối
        // `INK_ON_ACCENT` = `#e7ecff` nên icon vẫn gần như trắng ⇒ bản tối không đổi hình.
        // P2 · AC2.6 — hợp đồng cỡ/chủ đề/trạng thái ở [KachiIcons.tint] (mặt nhỏ + bảng sáng vẫn tint mực như trước).
        KachiIcons.tint(icon, size.iconDp, active, if (active) KachiTheme.INK_ON_ACCENT else KachiTheme.ICON)
        label.setTextColor(c(if (active) KachiTheme.INK_ON_ACCENT else KachiTheme.INK2))
        // WP2 · R2.1 *"icon active/mờ"* — trạng thái TẮT hạ độ đục của ICON. NHÃN giữ nguyên độ đục: nó trả lời
        // *"ô này là cái gì"*, câu đó không phụ thuộc bật/tắt (cùng lẽ [ReadTile.bind]/[CellBinder] chỉ làm mờ GIÁ
        // TRỊ chứ không làm mờ cả ô — [ĐO] 2.33:1 của lượt kiểm toán UX).
        icon.alpha = if (active) 1f else ICON_OFF_ALPHA
    }

    /**
     * Nút −/+ của ô kiểu STEP.
     *
     * **Bề ngang do WEIGHT quyết định, KHÔNG phải `minWidth`** — đó là bài học [ĐO] trên máy ảo: đặt
     * `minWidth = 36dp` làm hai nút chiếm 72dp trong ô chỉ có 68dp bề ngang dùng được, nên chữ giá trị bị bóp và
     * `"22°"` **xuống hai dòng**. Với weight, hai nút tự chia phần còn lại sau khi chữ giá trị lấy đủ chỗ ⇒ không
     * bao giờ bóp chữ, mà vẫn to hết mức ô cho phép.
     *
     * Chỉ [Sp.TOUCH_TIGHT] cho bề DỌC — chiều duy nhất còn nới được trong ô 84×86dp (xem KDoc của hằng đó).
     */
    private fun stepBtn(s: String) = TextView(ctx).apply {
        text = s; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp); gravity = Gravity.CENTER
        minHeight = dpi(ctx, Sp.TOUCH_TIGHT); maxLines = 1
        background = GradientDrawable().apply { cornerRadius = dpi(ctx, Sp.RADIUS_S).toFloat(); setColor(c(KachiTheme.DIM)) }
    }

    private fun applyBg(v: View, active: Boolean) {
        // ⚠ Nhánh BẬT giữ [KachiTheme.gradientSoft] — KHÔNG đổi sang `surface(ACTIVE)`: chữ/icon của ô đang bật tô
        // bằng `INK_ON_ACCENT`, và bài canh `ThemePaletteContractTest` đo vai đó **trên nền `tileOn*`**. Đổi nền mà
        // giữ mực là làm số đo trong bài nói về một nền không còn tồn tại.
        v.background = if (active) KachiTheme.gradientSoft(ctx, size.radius) else KachiTheme.surface(ctx, size.radius)
    }

    /**
     * Bọc ô + chấm *"chưa kiểm trên xe"* ở góc trên-phải (tier OVERDRIVE/DASHCAST).
     *
     * ⚠ U10 — chấm vẽ bằng [PickerBadge.dot] (mực mờ), KHÔNG còn [KachiTheme.AMBER] (hổ phách = màu CẢNH BÁO, mà dấu
     * này hiện trên gần như mọi ô ⇒ cả trang đọc thành "toàn lỗi"). Cỡ giữ [KachiSpace.DOT] (8dp) — chấm dán vào góc
     * **Ô** (84×86dp) nên tỉ lệ icon/5 sẽ vô hình; trần R6 *"≤ 8% ô"* vẫn thừa (π·4² ≈ 50dp² / 7224dp² ⇒ **0,7%**).
     */
    private fun withBadge(content: LinearLayout): View = FrameLayout(ctx).apply {
        addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(PickerBadge.dot(ctx), FrameLayout.LayoutParams(dpi(ctx, Sp.DOT), dpi(ctx, Sp.DOT), Gravity.TOP or Gravity.END).also {
            it.topMargin = dpi(ctx, Sp.S); it.marginEnd = dpi(ctx, Sp.S)
        })
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** Hàng stepper lấy trọn bề ngang ô — xem [tileStep]. */
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        /** Thẻ nhật ký của gói lệnh — một chỗ để `adb logcat -s ActionMacro` bắt đủ cả lượt chạy lẫn lượt hỏng. */
        const val TAG_MACRO = "ActionMacro"

        /**
         * WP2 · R2.1 — độ đục của ICON khi ô đang TẮT. **0.72** lấy đúng con số [KachiIcons] đang dùng cho ô
         * *chưa chọn* ở mặt lớn, để hai bề mặt nói *"chưa bật"* bằng cùng một cường độ.
         */
        const val ICON_OFF_ALPHA = 0.72f

        /**
         * WP2 · R2.4 — tỉ lệ ba cột của hàng stepper: `[−] 1 · giá trị 2 · [+] 1`.
         *
         * Ô giá trị lấy **một nửa** hàng, hai nút chia đều phần còn lại ⇒ hai nút đối xứng và đứng đúng một chỗ ở
         * mọi ô stepper. [ĐO số học] ô thanh nút ngang dùng được 68dp ⇒ giá trị 34dp (sàn chữ chỉ ~25dp ở
         * [TileSize.DOCK]) và mỗi nút 17dp — trên bề ngang glyph `−`/`+`, và vùng CHẠM thì đã được
         * [StepTouchTarget] nới ra nửa ô nên 17dp không phải là đích bấm thật.
         */
        const val SIDE_WEIGHT = 1f
        const val VALUE_WEIGHT = 2f

        /** Chữ số dùng để ĐO sàn bề ngang ô giá trị — `'0'` là chữ số rộng nhất ở hầu hết phông chữ hệ thống. */
        const val DIGIT = "0"
    }
}
