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
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

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
 * Tier OVERDRIVE/DASHCAST ⇒ chấm amber "chưa kiểm trên xe" (cả hai loại). **KHÔNG gate an toàn** — mọi ô bấm được
 * bất kể tốc độ/số (owner bỏ gate 2026-09-10).
 */
class ControlTileFactory(
    private val ctx: Context,
    private val control: () -> CarControlPort,
    private val size: TileSize = TileSize.DOCK,
    private val state: ControlTileState = ControlTileState.shared,
) {

    // ── HÀNH ĐỘNG ───────────────────────────────────────────────────────────────────────────────────────
    /** Ô bấm được cho [def]. Không đặt `layoutParams` — cỡ do VÙNG quyết định (thanh nút vs ô giữa màn). */
    fun actionTile(def: ControlDef): View {
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
        }
        val icon = ImageView(ctx).apply {
            val r = iconRes(def); if (r != 0) setImageResource(r)
            layoutParams = LinearLayout.LayoutParams(dpi(ctx, size.iconDp), dpi(ctx, size.iconDp))
                .also { it.bottomMargin = dpi(ctx, 4) }
        }
        val label = TextView(ctx).apply {
            // [SOÁT P3] `maxLines = 1` mà KHÔNG ellipsize ⇒ chữ bị cắt CỨNG, không có "…": trong ô 84dp thì 4 ô kính
            // ("Kính trước-trái/phải", "Kính sau-trái/phải") trông gần như y hệt nhau. Ô ĐỌC đã sửa từ gói 2, ô hành
            // động thì chưa.
            text = def.label; setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp); gravity = Gravity.CENTER
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        content.addView(icon)

        when (def.kind) {
            ControlKind.TOGGLE -> tileToggle(def, content, icon, label)
            ControlKind.STEP -> tileStep(def, content, icon, label)
            ControlKind.COVER -> tileCover(def, content, icon, label)
            ControlKind.SELECT -> tileSelect(def, content, icon, label)
            ControlKind.BUTTON -> tileButton(def, content, icon, label)
        }
        return if (ControlTileLogic.needsBadge(def)) withBadge(content) else content
    }

    private fun tileToggle(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        tile.addView(label)
        val active = state.isOn(def.id)
        applyBg(tile, active); tint(icon, label, active)
        tile.setOnClickListener {
            val nv = !state.isOn(def.id); state.setOn(def.id, nv)
            applyBg(tile, nv); tint(icon, label, nv); control().toggle(def.id, nv)
        }
    }

    private fun tileStep(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        val unit = if (def.id == "temp") "°" else ""
        applyBg(tile, false); tint(icon, label, true)
        val vtext = TextView(ctx).apply {
            text = "${state.value(def)}$unit"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp)
            typeface = Typeface.DEFAULT_BOLD; setPadding(dpi(ctx, 8), 0, dpi(ctx, 8), 0)
        }
        val minus = stepBtn("−"); val plus = stepBtn("+")
        minus.setOnClickListener {
            val nv = def.clamp(state.value(def) - def.step); state.setValue(def.id, nv); vtext.text = "$nv$unit"; control().step(def.id, nv)
        }
        plus.setOnClickListener {
            val nv = def.clamp(state.value(def) + def.step); state.setValue(def.id, nv); vtext.text = "$nv$unit"; control().step(def.id, nv)
        }
        tile.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; addView(minus); addView(vtext); addView(plus)
        })
    }

    private fun tileCover(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        applyBg(tile, false); tint(icon, label, true)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 1f); tile.addView(label)
        val closeLbl = def.args.getOrElse(0) { "Đóng" }; val openLbl = def.args.getOrElse(1) { "Mở" }
        val close = miniBtn(closeLbl) { control().cover(def.id, false) }
        val open = miniBtn(openLbl) { control().cover(def.id, true) }
        tile.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dpi(ctx, 3), 0, 0)
            addView(close, LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginEnd = dpi(ctx, 4) }); addView(open)
        })
    }

    private fun tileSelect(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        applyBg(tile, false); tint(icon, label, true)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 1.5f); tile.addView(label)
        val optView = TextView(ctx).apply {
            text = ControlTileLogic.selectLabel(def, state.sel(def.id)); setTextColor(c(KachiTheme.ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size.optionSp); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            maxLines = 1; setPadding(0, dpi(ctx, 2), 0, 0)
        }
        tile.addView(optView)
        tile.setOnClickListener {
            val next = ControlTileLogic.nextSelectIndex(state.sel(def.id), def.args.size)
            state.setSel(def.id, next); optView.text = ControlTileLogic.selectLabel(def, next); control().select(def.id, next)
        }
    }

    private fun tileButton(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        tile.addView(label); applyBg(tile, false); tint(icon, label, true)
        tile.setOnClickListener {
            applyBg(tile, true); tint(icon, label, true)
            control().press(def.id)
            tile.postDelayed({ applyBg(tile, false); tint(icon, label, true) }, 220)   // nháy sáng momentary
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
        val r = KachiTheme.iconRes(macro.icon)
        val icon = ImageView(ctx).apply { if (r != 0) setImageResource(r) }
        tile.addView(icon, LinearLayout.LayoutParams(dpi(ctx, size.iconDp), dpi(ctx, size.iconDp)))
        val label = TextView(ctx).apply {
            text = macro.label; setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp)
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        tile.addView(label)
        applyBg(tile, false); tint(icon, label, true)

        tile.setOnClickListener {
            // Chốt theo MÃ GÓI ở bảng dùng chung — không theo View (xem ControlTileState.beginRun).
            if (!state.beginRun(macro.id)) return@setOnClickListener
            applyBg(tile, true); tint(icon, label, true)
            val port = control()
            Thread({
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
                    val notice = res?.notice(macro.label)
                    tile.post {
                        runCatching {
                            applyBg(tile, false); tint(icon, label, true)
                            if (notice != null) Toast.makeText(ctx, notice, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, "macro-${macro.id}").start()
        }
        return if (macro.needsBadge()) withBadge(tile) else tile
    }

    // ── ĐỌC ─────────────────────────────────────────────────────────────────────────────────────────────
    /**
     * Ô CHỈ-XEM cho [pick]: icon + nhãn + số + đơn vị. **KHÔNG gắn `setOnClickListener`** — thông tin đọc không
     * phải nút; gắn chạm vào đây là xoá luôn ranh giới ĐỌC/HÀNH ĐỘNG mà cả gói này dựng ra (test khoá điều đó).
     *
     * Số KHÔNG được nhét vào lúc dựng: gọi [ReadTile.bind] để đổ giá trị và đổi giá trị VỀ SAU **tại chỗ** — nhờ
     * vậy nhịp trạng thái xe không phải dựng lại view (ràng buộc C5: dựng lại là nháy + mất trạng thái vừa bấm).
     */
    fun readTile(pick: CapabilityPick): ReadTile {
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
            background = KachiTheme.card(ctx, size.radius, "#242a34")
        }
        val r = KachiTheme.iconRes(pick.icon)
        if (r != 0) content.addView(
            ImageView(ctx).apply { setImageResource(r); setColorFilter(c("#aeb8c8")) },
            LinearLayout.LayoutParams(dpi(ctx, size.iconDp - 4), dpi(ctx, size.iconDp - 4)).also { it.bottomMargin = dpi(ctx, 2) },
        )
        val label = TextView(ctx).apply {
            // [ĐO] máy ảo 2026-09-10: một dòng + cắt cuối làm "Áp lốp trước-trái" và "Áp lốp trước-phải" đều thành
            // "Áp lốp trước-t…" ⇒ hai ô trông Y HỆT, người dùng không biết ô nào là bánh nào. Sửa: cho 2 DÒNG.
            // Cố ý KHÔNG bịa quy tắc viết tắt (kiểu bỏ tiền tố / lấy chữ đầu): nhãn đến từ bộ đăng ký với 195 mục
            // đủ kiểu, mọi quy tắc tự nghĩ đều sẽ tạo ra nhãn vô nghĩa ở đâu đó mà không ai kiểm được.
            text = pick.label
            setTextColor(c("#c3cee0")); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 1.5f)
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        val value = TextView(ctx).apply {
            text = TelemetryView.PLACEHOLDER; setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp); gravity = Gravity.CENTER; maxLines = 1
        }
        val unit = TextView(ctx).apply {
            setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 2f)
            gravity = Gravity.CENTER; maxLines = 1; visibility = View.GONE
        }
        content.addView(label); content.addView(value); content.addView(unit)
        val outer = if (pick.needsBadge) withBadge(content) else content
        return ReadTile(outer, content, value, unit)
    }

    // ── Helper dùng chung ───────────────────────────────────────────────────────────────────────────────
    /** iconRes theo def.icon; nếu chưa map (ic-adas/ic-drive/ic-mirror…) → icon đại diện domain. */
    private fun iconRes(def: ControlDef): Int {
        val r = KachiTheme.iconRes(def.icon)
        return if (r != 0) r else KachiTheme.iconRes(WidgetCatalog.iconFor(def.domain))
    }

    private fun tint(icon: ImageView, label: TextView, active: Boolean) {
        icon.setColorFilter(c(if (active) "#ffffff" else "#aeb8c8"))
        label.setTextColor(c(if (active) "#e7ecff" else "#c3cee0"))
    }

    private fun stepBtn(s: String) = TextView(ctx).apply {
        text = s; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp); gravity = Gravity.CENTER
        val sz = dpi(ctx, 22); minWidth = sz; minHeight = sz
        background = GradientDrawable().apply { cornerRadius = dpi(ctx, 6).toFloat(); setColor(c("#2a2f3a")) }
    }

    private fun miniBtn(s: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = s; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 0.5f); gravity = Gravity.CENTER
        setPadding(dpi(ctx, 8), dpi(ctx, 3), dpi(ctx, 8), dpi(ctx, 3)); maxLines = 1
        background = GradientDrawable().apply { cornerRadius = dpi(ctx, 8).toFloat(); setColor(c("#2a2f3a")) }
        setOnClickListener { onClick() }
    }

    private fun applyBg(v: View, active: Boolean) {
        v.background = if (active) KachiTheme.gradientSoft(ctx, size.radius) else KachiTheme.card(ctx, size.radius, "#242a34")
    }

    /** Bọc ô + chấm amber góc trên-phải cho tier "chưa kiểm trên xe" (OVERDRIVE/DASHCAST). */
    private fun withBadge(content: LinearLayout): View = FrameLayout(ctx).apply {
        addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.AMBER)) }
        }, FrameLayout.LayoutParams(dpi(ctx, 6), dpi(ctx, 6), Gravity.TOP or Gravity.END).also {
            it.topMargin = dpi(ctx, 6); it.marginEnd = dpi(ctx, 6)
        })
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** Thẻ nhật ký của gói lệnh — một chỗ để `adb logcat -s ActionMacro` bắt đủ cả lượt chạy lẫn lượt hỏng. */
        const val TAG_MACRO = "ActionMacro"
    }
}

/**
 * Cỡ ô theo VÙNG. Con số của [DOCK] là **y hệt** bản cũ nằm trong [ControlDockView] (22dp icon · nhãn 11.5sp ·
 * số 15sp · lựa chọn 12.5sp · đệm 8dp · bo 14dp) ⇒ thanh nút không đổi một pixel sau khi rút bộ dựng ra ngoài.
 * [BIG] cho ô giữa màn (khung to hơn nhiều nên chữ/icon phải to theo, không thì ô trông như bị hụt).
 */
enum class TileSize(
    val iconDp: Int,
    val labelSp: Float,
    val valueSp: Float,
    val optionSp: Float,
    val padDp: Int,
    val radius: Float,
) {
    DOCK(22, 11.5f, 15f, 12.5f, 8, 14f),
    BIG(34, 15f, 26f, 16f, 14, 16f),
}

/**
 * Trạng thái ô nút mà UI tự giữ (lạc quan): bật/tắt · giá trị −/+ · lựa chọn đang chọn.
 *
 * Vì sao có [shared]: cùng một nút giờ đặt được ở **hai vùng** (thanh nút và ô giữa màn). Nếu mỗi vùng giữ một bảng
 * riêng thì bật "lấy gió trong" ở ô giữa màn xong nhìn sang thanh nút vẫn thấy tắt — hai bề mặt nói hai điều về
 * MỘT cái xe. Một bảng dùng chung cho cả tiến trình khớp với thực tế (chỉ có một cái xe) và không tốn gì.
 *
 * ⚠ Đây KHÔNG phải trạng thái đọc từ xe (phần lớn nút không có đường đọc lại) — nó chỉ là "tôi vừa bấm cái này".
 */
class ControlTileState {
    // ConcurrentHashMap, KHÔNG phải HashMap: gói lệnh (W2) ghi trạng thái từ **thread nền** (xem `macroTile`) trong
    // khi thread chính đang đọc để vẽ ô ⇒ HashMap ở đây là tranh chấp dữ liệu thật. Đổi sang map đồng thời là cách
    // rẻ nhất và không đổi API.
    private val on = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val values = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val selIndex = java.util.concurrent.ConcurrentHashMap<String, Int>()

    init { ControlRegistry.ALL.forEach { on[it.id] = it.onByDefault; values[it.id] = it.value } }

    fun isOn(id: String): Boolean = on[id] == true
    fun setOn(id: String, v: Boolean) { on[id] = v }
    fun value(def: ControlDef): Int = values[def.id] ?: def.value
    fun setValue(id: String, v: Int) { values[id] = v }
    fun sel(id: String): Int = selIndex[id] ?: 0
    fun setSel(id: String, i: Int) { selIndex[id] = i }

    /**
     * Chốt "gói lệnh này đang chạy" — **dùng chung theo mã gói, KHÔNG theo View**.
     *
     * ## ⚠ [SOÁT P1-1] Vì sao không để cờ trong View
     * Bản trước giữ `AtomicBoolean` **bên trong** ô (`macroTile`). Trên xe, trạng thái xe đổi mỗi giây và ô TRỘN
     * (có mục đọc + gói lệnh) bị **dựng lại** theo nhịp đó ⇒ ô mới có cờ mới `false` ⇒ người dùng bấm lần hai trong
     * lúc lượt một còn đang chạy ⇒ **hai lượt "đóng hết kính" chạy chồng nhau**, đúng thứ cờ này sinh ra để chặn.
     * Chốt theo mã gói thì dựng lại bao nhiêu lần cũng không mở được cửa thứ hai.
     */
    fun beginRun(id: String): Boolean = running.putIfAbsent(id, true) == null

    /** Nhả chốt. PHẢI gọi trong `finally`, trên chính thread đang chạy gói. */
    fun endRun(id: String) { running.remove(id) }

    private val running = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    companion object {
        /** Bảng dùng chung mọi vùng trong cùng tiến trình. */
        val shared = ControlTileState()
    }
}

/**
 * Một ô ĐỌC đã dựng: [view] để gắn vào vùng, [bind] để đổ/đổi số **mà không dựng lại view**.
 *
 * Chưa đọc được (`null` hoặc [TelemetryView.available] = false) ⇒ dấu gạch ngang + mờ 50% — KHÔNG bịa số
 * (off-car là ca thường, không phải ca lỗi).
 */
class ReadTile internal constructor(
    val view: View,
    private val content: View,
    private val value: TextView,
    private val unit: TextView,
) {
    fun bind(v: TelemetryView?) {
        value.text = v?.display ?: TelemetryView.PLACEHOLDER
        val u = v?.unit ?: ""
        unit.text = u
        unit.visibility = if (u.isEmpty()) View.GONE else View.VISIBLE
        content.alpha = if (v?.available == true) 1f else 0.5f
    }
}
