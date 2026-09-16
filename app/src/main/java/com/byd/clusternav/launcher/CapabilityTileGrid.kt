package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ KỶ LUẬT KHOẢNG CÁCH CỦA MỌI LƯỚI Ô CHỌN — MỘT NƠI QUYẾT ĐỊNH ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §3 **R5** (*"lưới thanh nút xe và lưới trong ngăn kéo dùng CÙNG kỷ
 * luật khoảng cách — khe `Sp.S`/`Sp.XS`, ô đồng cao, căn trên"*).
 *
 * ## Bệnh nó chữa — [ĐO] soát ảnh pha 2 design system (`kachi-design-system.html` §10 Pass 2, `[P1]`)
 * Pha 1 vá lưới **trong màn Cài đặt** (`CapabilityGridSection.addGrid`, tệp nay đã xoá — R-UI (m)): khe dọc
 * `Sp.S`, khe ngang `Sp.XS`, ô đồng cao. Ngăn kéo ([AppDrawer]) có **ba** lưới cùng loại và **không** lưới nào
 * được vá ⇒ ảnh máy ảo đo được: *"ô chọn cạnh nhau dính 0px, chung một đường viền, nền liền mạch; ô cùng hàng
 * không đồng cao (165 vs 183px)"*. Cùng một bệnh, bề mặt khác — đúng hình dạng lỗi mà dự án đã trả giá nhiều lần
 * khi một bản vá chỉ được áp ở chỗ người ta nhìn thấy nó.
 *
 * ## ⚠⚠ Chỉ dùng chung phần HÌNH HỌC, KHÔNG dùng chung bộ dựng Ô
 * `CapabilityGridSection` (đã xoá) có cảnh báo *"một lưới = một bảng tiles"* viết bằng máu: dùng lại **bộ dựng ô**
 * cho hai lưới khác hành vi đã sinh ra **ba lỗi cùng lúc** ở phiên RW0 (sự kiện bấm bắn nhầm lưới · `tiles[id]` bị
 * ghi đè · hai chỗ tô nền tranh nhau). Nên chỗ này **cố ý** chỉ nhận một hàm `tileAt(i)` và không biết gì về ô:
 * mỗi màn giữ bộ dựng ô RIÊNG (với bảng tra `mã → view` riêng), chỉ **khe và chiều cao hàng** là chung.
 *
 * Đó cũng là ranh giới đúng theo R5: thứ hai màn phải giống nhau là *nhịp*, không phải *hành vi bấm*.
 */
object CapabilityTileGrid {

    private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /**
     * Xếp [count] ô thành các hàng [cols] cột vào [parent], ô thứ `i` do [tileAt] dựng.
     *
     * Ba tính chất — cả ba đều là bản vá của một lỗi đã đo được, không phải sở thích:
     *  1. **Khe dọc `Sp.S`** giữa hai hàng: trước đây 0px ⇒ ô hàng trên chạm ô hàng dưới (owner báo *"nút/widget
     *     dính nhau"*). Cùng nhịp với stack spacing của [SettingsRows].
     *  2. **Khe ngang `2 × Sp.XS` GIỮA hai ô, mép ngoài hàng PHẲNG** (ô đầu không có `marginStart`, ô cuối không
     *     có `marginEnd`): hai ô cạnh nhau vẫn tách được, mà lưới không còn **thụt vào** so với tiêu đề mục.
     *     [ĐO] soát ảnh Settings v2 2026-09-13: lưới ngăn kéo bắt đầu ở `x = 96` trong khi tiêu đề ở `x = 85` —
     *     đúng bằng một `Sp.XS`, và mắt đọc ra "lưới lệch khỏi cột chữ".
     *  3. **Ô đồng cao do [UniformRow] tự đo hai pha**, KHÔNG nhờ `MATCH_PARENT` (xem cảnh báo dưới).
     */
    fun rows(context: Context, parent: LinearLayout, count: Int, cols: Int, tileAt: (Int) -> View) {
        // ⚠ [SOÁT OCR 2026-09-16 · Null Safety] `cols <= 0` dừng ở ĐÂY, không ở `i % cols` (chia cho 0 ném
        // `ArithmeticException` giữa lượt dựng màn) — một lưới 0 cột thì "không vẽ gì" mới là hành vi đúng.
        // Và bỏ hai `!!` bên dưới: `row` là biến BẮT nên trình biên dịch không suy ra được non-null, và một
        // `!!` ở đường dựng giao diện là một `NullPointerException` chờ sẵn cho mọi lượt sửa sau này.
        if (count <= 0 || cols <= 0) return
        var row: UniformRow? = null
        repeat(count) { i ->
            val col = i % cols
            val target = if (col == 0) {
                UniformRow(context).also { fresh ->
                    row = fresh
                    parent.addView(
                        fresh,
                        LinearLayout.LayoutParams(MATCH, WRAP).also { lp -> lp.bottomMargin = dpi(context, Sp.S) },
                    )
                }
            } else {
                row ?: return
            }
            target.addView(tileAt(i), cellLp(context, WRAP, col, cols))
        }
        val rem = count % cols
        val last = row ?: return
        // ⚠ Ô CHÈN cao ĐÚNG 0, không `WRAP_CONTENT`: một [View] trơ khai `WRAP_CONTENT` **giãn hết** dưới spec
        // `AT_MOST` (`getDefaultSize` trả trọn `specSize`) — dự án đã trả giá đúng bẫy này ở nhóm Đèn (9 ô con
        // không hiện một pixel). Cao 0 thì bẫy đó không còn cửa vào, và [UniformRow] cũng bỏ qua nó khi đồng cao.
        if (rem != 0) repeat(cols - rem) { k -> last.addView(View(context), cellLp(context, 0, rem + k, cols)) }
    }

    /**
     * Khung của MỘT ô trong hàng: chia đều bề ngang (weight), khe [Sp.XS] ở **mép trong**, mép ngoài phẳng.
     *
     * @param col vị trí trong hàng (0 = đầu hàng) — quyết định ô nào được bỏ lề ngoài.
     */
    private fun cellLp(context: Context, height: Int, col: Int, cols: Int) =
        LinearLayout.LayoutParams(0, height, 1f).also {
            it.marginStart = if (col == 0) 0 else dpi(context, Sp.XS)
            it.marginEnd = if (col == cols - 1) 0 else dpi(context, Sp.XS)
        }
}

/**
 * ═══ HÀNG NGANG TỰ ĐỒNG CAO — KHÔNG PHỤ THUỘC SPEC CỦA CHA ═══════════════════════════════════════════════════
 *
 * ## [ĐO] Bệnh: hàng ô chip thanh trạng thái vẽ CAO 0 (soát ảnh Settings v2, 2026-09-13, `[P1]`)
 * `dumpsys activity top` trên máy ảo: hàng `LinearLayout 0,0-1377,0`, ba ô con `…-332,0`, `TextView` trong ô ở
 * `y = 66` cao 0 ⇒ **cả hàng bị ép EXACTLY 0**. Cùng helper ở ngăn kéo lại vẽ được, nên bệnh **không** nằm ở ô.
 *
 * ## [ĐÃ CHỨNG MINH] Nguyên nhân — đọc `android-10.0.0_r47/core/java/android/widget/LinearLayout.java`
 * Bản cũ cho ô cao `MATCH_PARENT` rồi nhờ `forceUniformHeight` của [LinearLayout] đồng cao. Đường đó có một cửa
 * sập:
 *  • `LinearLayout.java:1408` — `allFillParent = allFillParent && lp.height == MATCH_PARENT`: **một** con không
 *    `MATCH_PARENT` là cờ tắt. Ô CHÈN của lưới (cao đúng 0, xem [CapabilityTileGrid.rows]) chính là con đó, nên
 *    mọi hàng **thiếu ô** (3 chip / 4 cột) đều rơi vào nhánh này.
 *  • `LinearLayout.java:1470` — `if (!allFillParent && heightMode != EXACTLY) maxHeight = alternativeMaxHeight;`
 *    mà `alternativeMaxHeight` (`:1269`, `:1405`) chỉ cộng `margin` cho con `MATCH_PARENT` ⇒ với ô không lề dọc
 *    thì bằng **0**. Dưới `ScrollView` (`heightMode = UNSPECIFIED`) điều kiện thứ hai luôn đúng.
 *  • `LinearLayout.java:1492` — `forceUniformHeight` lấy `uniformMeasureSpec = EXACTLY(getMeasuredHeight())` =
 *    **EXACTLY 0**, rồi đo lại mọi con `MATCH_PARENT` bằng spec đó ⇒ ba ô cao 0.
 * Tức chiều cao của ô phụ thuộc **spec của cha và thành phần của hàng** — một phụ thuộc vòng không ai đọc ra khi
 * thêm một lưới mới (đúng luật CLAUDE.md §3: *"không gate một đường phục hồi bằng dữ liệu mà chỉ đường đó làm mới
 * được"*).
 *
 * ## Cách chữa: ô khai `WRAP_CONTENT`, hàng tự đo HAI PHA
 *  1. `super.onMeasure` — mỗi ô cao tự nhiên (không con nào `MATCH_PARENT` ⇒ không còn `forceUniformHeight`, và
 *     chiều cao hàng = ô cao nhất, đúng ở cả ba `heightMode`).
 *  2. Ô nào thấp hơn thì **đo lại** bằng `EXACTLY(max)` — trừ ô chèn (`lp.height == 0`, không có gì để vẽ).
 * Không đụng `minimumHeight` (nó gọi `requestLayout()` **giữa** lượt đo) và không đổi `measuredDimension` của
 * hàng (pha 1 đã cho đúng số) — hai chỗ dễ sinh vòng lặp bố cục.
 *
 * ## Kiểm bằng tay (phần dựng view không test off-device được)
 * `adb shell dumpsys activity top` lúc mở nhóm *Thanh trạng thái & thanh nút*: hàng ô chip phải cao > 0 và **mọi**
 * ô trong cùng hàng phải có `bottom` bằng nhau. Bài canh thuần ở [DrawerGridSeamContractTest] chỉ khoá được
 * *cơ chế* (mọi lưới đi qua đây · hai pha còn nguyên), không thay được phép đo đó.
 */
private class UniformRow(context: Context) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var max = 0
        forEachCell { child, _ -> if (child.measuredHeight > max) max = child.measuredHeight }
        if (max <= 0) return
        forEachCell { child, lp ->
            // Ô CHÈN (cao khai 0) giữ nguyên: nó không vẽ gì, và kéo nó cao lên chỉ thêm một con có thể phình.
            if (lp.height != 0 && child.measuredHeight != max) {
                child.measure(
                    MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY),
                )
            }
        }
    }

    private inline fun forEachCell(action: (View, LayoutParams) -> Unit) {
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == GONE) continue
            action(child, child.layoutParams as LayoutParams)
        }
    }
}
