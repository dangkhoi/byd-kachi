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
    /** Ô bấm được cho [def]. Không đặt `layoutParams` — cỡ do VÙNG quyết định (thanh nút vs ô giữa màn). */
    fun actionTile(def: ControlDef): View {
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
        tile.addView(reserveTwoLines(label))
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
            text = "${state.value(def)}$unit"; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp)
            typeface = Typeface.DEFAULT_BOLD; setPadding(dpi(ctx, Sp.S), 0, dpi(ctx, Sp.S), 0)
        }
        val minus = stepBtn("−"); val plus = stepBtn("+")
        minus.setOnClickListener {
            val nv = def.clamp(state.value(def) - def.step); state.setValue(def.id, nv); vtext.text = "$nv$unit"; control().step(def.id, nv)
        }
        plus.setOnClickListener {
            val nv = def.clamp(state.value(def) + def.step); state.setValue(def.id, nv); vtext.text = "$nv$unit"; control().step(def.id, nv)
        }
        // Nút −/+ mang WEIGHT, chữ giá trị WRAP: chữ lấy đủ chỗ trước, hai nút chia phần còn lại ⇒ giá trị
        // không bao giờ bị bóp xuống hai dòng (lỗi [ĐO] khi hai nút dùng minWidth cố định).
        vtext.maxLines = 1
        tile.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(minus, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(vtext, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(plus, LinearLayout.LayoutParams(0, WRAP, 1f))
        })
        // [R7] Đích chạm: nới VÙNG NHẬN CHẠM ra nửa ô (≥ Sp.TOUCH bề dọc), KHÔNG nới cái nút — nới nút thì
        // 2×48 > 68dp dùng được của ô và chữ giá trị xuống hai dòng ([ĐO] ghi ở KDoc Sp.TOUCH_TIGHT).
        StepTouchTarget.attach(tile, minus, plus)
    }

    private fun tileCover(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        applyBg(tile, false); tint(icon, label, true)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 1f); tile.addView(label)
        // T7 (owner 2026-09-15 "nút mở 50%"): MỘT nút cho MỖI mức trong `displayArgs` (0=Đóng · 1=Mở · 2=Nửa…), bấm
        // gửi ĐÚNG chỉ số mức qua `coverLevel` (kính 2→OPEN_HALF=4, rèm 2→50%). Control chỉ khai 2 mức (windows_all)
        // vẫn ra đúng 2 nút Đóng/Mở như trước — generic theo dữ liệu registry, không hardcode "kính".
        val labels = def.displayArgs.ifEmpty {
            listOf(ctx.getString(R.string.kachi_cover_close), ctx.getString(R.string.kachi_cover_open))
        }
        val buttons = labels.mapIndexed { level, text -> miniBtn(text) { control().coverLevel(def.id, level) } }
        tile.addView(LinearLayout(ctx).apply {
            // ⚠⚠ [KIỂM TOÁN 2026-09-12 mục 3] XẾP DỌC ở ô HẸP.
            //
            // [ĐO] ảnh máy ảo: trong ô 82px của hàng nút nhóm *Kính*, hai nút chia ngang còn ~32px mỗi nút ⇒ `"Đóng"`
            // hiện thành `"Đ"`, `"Close"` thành `"C"` — cắt CỨNG (`maxLines = 1`, không `ellipsize`) nên không có cả
            // dấu `…` để người dùng biết là chữ bị cắt. Chia đều bằng weight (bản vá trước) chỉ chống được TRÀN, không
            // làm chữ vừa.
            //
            // Xếp dọc thì mỗi nút được TRỌN bề ngang ô (~70px) ⇒ chữ nguyên vẹn ở cả hai thứ tiếng. Giá là bề cao, và
            // ô nhóm có bề cao đó sau khi lưới đọc biết nhường (`GroupTileView.onMeasure`).
            orientation = if (size.narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER; setPadding(0, dpi(ctx, Sp.XS), 0, 0)
            // ⚠ [ĐO] máy ảo: hai nút WRAP + lề trong S làm tổng bề ngang 84dp > 68dp dùng được của ô ⇒ nhãn
            // nút thứ hai bị cắt, "Mở" hiện thành "M". Cho hai nút CHIA ĐỀU bằng weight thì không thể tràn.
            buttons.forEachIndexed { i, b ->
                val last = i == buttons.lastIndex
                if (size.narrow) {
                    addView(b, LinearLayout.LayoutParams(MATCH, WRAP).also { if (!last) it.bottomMargin = dpi(ctx, Sp.XS) })
                } else {
                    addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).also { if (!last) it.marginEnd = dpi(ctx, Sp.XS) })
                }
            }
        })
    }

    private fun tileSelect(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        applyBg(tile, false); tint(icon, label, true)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 1.5f); tile.addView(label)
        val optView = TextView(ctx).apply {
            text = ControlTileLogic.selectLabel(def, state.sel(def.id)); setTextColor(c(KachiTheme.ACCENT_INK))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size.optionSp); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            maxLines = 1; setPadding(0, dpi(ctx, Sp.XS), 0, 0)
        }
        tile.addView(optView)
        tile.setOnClickListener {
            val next = ControlTileLogic.nextSelectIndex(state.sel(def.id), def.args.size)
            state.setSel(def.id, next); optView.text = ControlTileLogic.selectLabel(def, next); control().select(def.id, next)
        }
    }

    private fun tileButton(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        tile.addView(reserveTwoLines(label)); applyBg(tile, false); tint(icon, label, true)
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
                    val notice = res?.notice(macro.displayLabel)
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
        val r = KachiTheme.iconRes(pick.icon)
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
    fun readTile(pick: CapabilityPick): ReadTile {
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
            background = KachiTheme.surface(ctx, size.radius, domain = pick.domain)
        }
        val r = KachiTheme.iconRes(pick.icon)
        if (r != 0) content.addView(
            ImageView(ctx).apply { setImageResource(r); setColorFilter(c(KachiTheme.ICON)) },
            LinearLayout.LayoutParams(dpi(ctx, size.iconDp - Sp.XS), dpi(ctx, size.iconDp - Sp.XS)).also { it.bottomMargin = dpi(ctx, Sp.XS) },
        )
        val label = TextView(ctx).apply {
            // [ĐO] máy ảo 2026-09-10: một dòng + cắt cuối làm "Áp lốp trước-trái" và "Áp lốp trước-phải" đều thành
            // "Áp lốp trước-t…" ⇒ hai ô trông Y HỆT, người dùng không biết ô nào là bánh nào. Sửa: cho 2 DÒNG.
            // Cố ý KHÔNG bịa quy tắc viết tắt (kiểu bỏ tiền tố / lấy chữ đầu): nhãn đến từ bộ đăng ký với 195 mục
            // đủ kiểu, mọi quy tắc tự nghĩ đều sẽ tạo ra nhãn vô nghĩa ở đâu đó mà không ai kiểm được.
            text = pick.displayLabel
            setTextColor(c(KachiTheme.INK2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 1.5f)
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        val value = TextView(ctx).apply {
            text = TelemetryView.PLACEHOLDER; setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp); gravity = Gravity.CENTER; maxLines = 1
            // ⚠ [SOÁT ĐỘC LẬP 2026-09-12] `maxLines = 1` mà KHÔNG ellipsize ⇒ chữ bị cắt CỨNG, không có "…" — đúng
            // họ lỗi mà chính tệp này đã vá hai lần cho NHÃN ô ([actionTile] và nhãn của ô đọc ngay trên). Từ khi
            // giá trị và đơn vị chia CHUNG một hàng ngang, giá trị dài không còn được cả bề ngang ô nữa nên ca cắt
            // gần hơn trước; có "…" thì người dùng đọc ra là "còn nữa", không đọc ra "số bị sai".
            ellipsize = TextUtils.TruncateAt.END
        }
        val unit = TextView(ctx).apply {
            setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 2f)
            gravity = Gravity.CENTER; maxLines = 1; visibility = View.GONE
        }
        content.addView(label)
        // ⚠ [SOÁT UI 2026-09-12] Giá trị + đơn vị trên MỘT hàng ngang (trước đây đơn vị là dòng RIÊNG dưới giá trị).
        // Ở ô THẤP của thanh nút (vd "Mức xăng"), ba dòng dọc (nhãn tối đa 2 dòng + giá trị + đơn vị) tràn khỏi ô ⇒
        // đơn vị "%" bị CẮT ở đáy và trông lạc lõng, trong khi ô kế bên KHÔNG có nút −/+ nên ô này nhìn như hỏng.
        // Gộp một hàng vừa hết cắt vừa đọc "— %" thành một cụm. Giữ 2 TextView riêng để [ReadTile.bind] không đổi.
        content.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(value)
            addView(unit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.marginStart = dpi(ctx, Sp.XS) })
        })
        val outer = if (pick.needsBadge) withBadge(content) else content
        return ReadTile(outer, content, value, unit)
    }

    /**
     * Nhãn của ô **CHỈ-BẬT-TẮT / BẤM-MỘT-PHÁT** luôn chiếm ĐÚNG hai dòng, dù chữ chỉ có một dòng.
     *
     * ## ⚠⚠ [KIỂM TOÁN UX mục 6] Vì sao phải cố định, không phải rút ngắn nhãn
     * [ĐO] trong 9 ô của thanh nút, *"Khoá / mở khoá"* là nhãn **duy nhất** xuống hai dòng ⇒ nội dung ô đó cao hơn
     * các ô khác một dòng, mà ô căn giữa dọc ⇒ **icon của nó lệch trục 4–7px** so với tám ô còn lại. Rút ngắn nhãn
     * chữa được ĐÚNG ô này và **không chữa nguyên nhân**: 64 nút, nhãn nào cũng có thể xuống dòng ở cỡ ô khác
     * (thanh dọc rộng 100dp vs ngang 84dp), và lần sau sẽ không ai nhớ luật này.
     *
     * Chốt chỗ cho hai dòng thì chiều cao nội dung **không còn phụ thuộc độ dài chữ** ⇒ icon nằm cùng trục do cấu
     * tạo. Chỉ áp cho hai kiểu ô mà nhãn là phần TỬ CUỐI: ô có thêm hàng giá trị (STEP/COVER/SELECT) thì thêm một
     * dòng nữa sẽ đẩy hàng giá trị ra ngoài trần 86dp của ô — đúng bẫy [KachiSpace.TOUCH_TIGHT] đã đo.
     */
    private fun reserveTwoLines(label: TextView): TextView = label.apply { minLines = 2 }

    // ── Helper dùng chung ───────────────────────────────────────────────────────────────────────────────
    /** iconRes theo def.icon; nếu chưa map (ic-adas/ic-drive/ic-mirror…) → icon đại diện domain. */
    private fun iconRes(def: ControlDef): Int {
        val r = KachiTheme.iconRes(def.icon)
        return if (r != 0) r else KachiTheme.iconRes(WidgetCatalog.iconFor(def.domain))
    }

    private fun tint(icon: ImageView, label: TextView, active: Boolean) {
        // ⚠ [T1 · ĐO TỪ PIXEL] Icon lúc BẬT dùng [KachiTheme.INK_ON_ACCENT], KHÔNG dùng `ON_ACCENT`. Hai vai này
        // khác nhau đúng ở chỗ nền: `ON_ACCENT` (trắng) dành cho nền nhấn **ĐẶC** (gradient của pill/nút); còn ô này
        // dùng `gradientSoft` = nền nhấn **BÁN TRONG SUỐT**, nên ở bảng sáng nó trộn ra tím nhạt (214,217,248) và
        // icon trắng chỉ còn **1.39:1** — [ĐO] trên ảnh thanh nút xe, glyph ổ khoá gần như biến mất. Ở bảng tối
        // `INK_ON_ACCENT` = `#e7ecff` nên icon vẫn gần như trắng ⇒ bản tối không đổi hình.
        icon.setColorFilter(c(if (active) KachiTheme.INK_ON_ACCENT else KachiTheme.ICON))
        label.setTextColor(c(if (active) KachiTheme.INK_ON_ACCENT else KachiTheme.INK2))
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

    private fun miniBtn(s: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = s; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 0.5f); gravity = Gravity.CENTER
        // Lề NGANG = XS (không phải S): nút này nằm trong ô 68dp cùng một nút nữa, lề rộng ăn hết chỗ của chữ.
        setPadding(dpi(ctx, Sp.XS), dpi(ctx, Sp.XS), dpi(ctx, Sp.XS), dpi(ctx, Sp.XS)); maxLines = 1
        background = GradientDrawable().apply { cornerRadius = dpi(ctx, Sp.RADIUS_S).toFloat(); setColor(c(KachiTheme.DIM)) }
        setOnClickListener { onClick() }
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
     * ## ⚠ U10 — chấm vẽ bằng [PickerBadge.dot], KHÔNG còn [KachiTheme.AMBER]
     * U7·R6 đã hạ chấm của **bộ chọn** xuống mực mờ vì hổ phách là màu CẢNH BÁO mà dấu này hiện trên gần như mọi ô
     * (chỉ 21/195 mã ở mức PROVEN) ⇒ cả trang đọc thành "toàn lỗi". Chỗ này bị bỏ sót nên thanh nút vẫn sáng hổ
     * phách: cùng một sự thật, hai giọng, ở hai bề mặt nhìn thấy nhau. Nay cả hai gọi chung một hàm vẽ. **Cỡ** giữ
     * [KachiSpace.DOT] (8dp) chứ không lấy tỉ lệ icon như bộ chọn: chấm ở đây dán vào góc **Ô** (84×86dp) nên icon/5
     * (= 4dp với [TileSize.DOCK]) sẽ vô hình; trần R6 *"≤ 8% ô"* vẫn thừa chỗ (π·4² ≈ 50dp² / 7224dp² ⇒ **0,7%**).
     */
    private fun withBadge(content: LinearLayout): View = FrameLayout(ctx).apply {
        addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(PickerBadge.dot(ctx), FrameLayout.LayoutParams(dpi(ctx, Sp.DOT), dpi(ctx, Sp.DOT), Gravity.TOP or Gravity.END).also {
            it.topMargin = dpi(ctx, Sp.S); it.marginEnd = dpi(ctx, Sp.S)
        })
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** Nút phụ xếp DỌC lấy trọn bề ngang ô — xem [tileCover]. */
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        /** Thẻ nhật ký của gói lệnh — một chỗ để `adb logcat -s ActionMacro` bắt đủ cả lượt chạy lẫn lượt hỏng. */
        const val TAG_MACRO = "ActionMacro"
    }
}

/**
 * Cỡ ô theo VÙNG. [BIG] cho ô giữa màn (khung to hơn nhiều nên chữ/icon phải to theo, không thì ô trông hụt).
 *
 * ## ⚠ T5 — số của [DOCK] KHÔNG còn "y hệt bản cũ"
 * KDoc trước ghi *"con số của DOCK là y hệt bản cũ nằm trong `ControlDockView` (22dp icon · đệm 8dp · bo 14dp)
 * ⇒ thanh nút không đổi một pixel"*. Từ T5 điều đó **hết đúng** và cố ý: icon 22 → [Sp.ICON_S] (20),
 * bo 14 → [Sp.RADIUS_L] (16), đệm 8 giữ nguyên ([Sp.S]). Lý do là chính bệnh T5 đi dọn — 22 và
 * 14 nằm ngoài mọi nhịp, nên thanh nút lệch nhịp với phần còn lại của màn.
 *
 * Ghi lại ở đây thay vì xoá câu cũ: bất biến "không đổi một pixel" từng là **có thật** và là lý do bộ dựng ô
 * được rút ra khỏi [ControlDockView] an toàn. Ai đọc sau cần biết nó đã được cố ý bỏ, chứ không phải bị quên.
 */
enum class TileSize(
    val iconDp: Int,
    val labelSp: Float,
    val valueSp: Float,
    val optionSp: Float,
    val padDp: Int,
    /** Bo góc, **dp dạng Int** từ T5 (họ `Sp.RADIUS_*`) — trước đây là `Float` với số trần 14f/16f. */
    val radius: Int,
    /**
     * Ô **HẸP** — bề ngang do vùng chia ra, không phải cỡ cố định.
     *
     * Ba thứ đổi theo, và cả ba đều là [ĐO] từ ảnh máy ảo 2026-09-12 (ô 82px ở khung 4/12 màn):
     *  1. **Nhãn dùng bản NGẮN** ([ControlDef.displayShortLabel]) — nhãn đầy bị cắt `"Window front-ri…"` /
     *     `"Kính trước-p…"`, làm hai ô kính trước đọc ra y hệt nhau.
     *  2. **Nút phụ của ô COVER xếp DỌC** — xếp ngang thì mỗi nút còn ~32px, chữ `"Đóng"`/`"Close"` bị cắt cứng thành
     *     `"Đ"`/`"C"` (không cả dấu `…`). Xếp dọc thì mỗi nút được TRỌN bề ngang ô ⇒ chữ nguyên vẹn. Giá phải trả là
     *     bề cao, và đó là thứ ô nhóm **có** sau khi lưới đọc biết nhường (xem `GroupTileView.onMeasure`).
     *  3. **Lề trong nhỏ hơn** ([KachiSpace.XS] thay vì [KachiSpace.S]) — lấy lại 12px bề cao cho chính việc trên.
     */
    val narrow: Boolean = false,
) {
    DOCK(Sp.ICON_S, 11.5f, 15f, 12.5f, Sp.S, Sp.RADIUS_L),
    BIG(Sp.ICON_L, 15f, 26f, 16f, Sp.L, Sp.RADIUS_L),

    /**
     * Ô trong **hàng nút của ô nhóm** — hẹp nhất trong ba vùng: bề ngang = bề ngang ô nhóm ÷ số nút mỗi hàng.
     *
     * Icon nhỏ hơn [DOCK] một bậc ([Sp.ICON_XS]) vì ô này còn phải chứa hàng nút phụ xếp dọc; cỡ chữ giữ **y như**
     * [DOCK] — thu chữ ở một ô còn hẹp hơn thanh nút là đi ngược chuẩn đọc được của G1.
     */
    GROUP(Sp.ICON_XS, 11.5f, 15f, 12.5f, Sp.XS, Sp.RADIUS_M, narrow = true),
}
