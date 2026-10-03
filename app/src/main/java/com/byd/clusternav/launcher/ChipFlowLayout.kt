package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/**
 * ═══ Dãy chip TỰ XUỐNG DÒNG — cho [SettingsRows.chipRow] khi `wrap = true` ═════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` R1 · §4.5: bộ chọn ngôn ngữ nay có SÁU mục (Theo xe · Tiếng Việt ·
 * English · 简体中文 · ไทย · Bahasa Melayu), và nhãn "Theo xe" còn được dịch sang tiếng đang chọn (Mã Lai dài hơn
 * tiếng Anh 20–40%) ⇒ một hàng `LinearLayout` ngang không xuống dòng sẽ đẩy chip cuối ra ngoài mép mà KHÔNG báo gì —
 * đúng mục người dùng cần bấm để quay về thứ tiếng họ đọc được.
 *
 * Xếp con trái→phải; con kế tiếp không còn đủ chỗ thì sang hàng mới. Vừa một hàng thì vị trí y hệt dãy chip cũ:
 * mỗi chip cách mép trái của nó [gapPx] (đúng `marginStart = S` của `addChip`), nên chuyển sang lớp này KHÔNG đổi
 * một pixel nào cho đến khi thật sự tràn.
 *
 * ⚠ Cố ý KHÔNG đọc lề (`margin`) của con: `addChip` đặt `marginStart` (lề TƯƠNG ĐỐI), mà lề tương đối chỉ được giải
 * thành trái/phải khi con tự đo — tức SAU chỗ lớp cha cần nó. Dùng một khoảng cách của chính lớp này thì không
 * phụ thuộc thứ tự giải đó. Chỉ chạy LTR (năm tiếng của Kachi đều trái→phải).
 */
internal class ChipFlowLayout(context: Context, private val gapPx: Int) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val bounded = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        var usedW = 0
        forEachVisible { c ->
            val lp = c.layoutParams
            c.measure(
                getChildMeasureSpec(widthMeasureSpec, paddingLeft + paddingRight + gapPx, lp.width),
                getChildMeasureSpec(heightMeasureSpec, paddingTop + paddingBottom, lp.height),
            )
            val w = gapPx + c.measuredWidth
            if (bounded && x > 0 && x + w > maxW) {
                y += rowH + gapPx
                x = 0
                rowH = 0
            }
            x += w
            rowH = max(rowH, c.measuredHeight)
            usedW = max(usedW, x)
        }
        setMeasuredDimension(
            resolveSize(usedW + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(y + rowH + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        forEachVisible { c ->
            val w = gapPx + c.measuredWidth
            if (x > 0 && x + w > maxW) {
                y += rowH + gapPx
                x = 0
                rowH = 0
            }
            val left = paddingLeft + x + gapPx
            val top = paddingTop + y
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
            x += w
            rowH = max(rowH, c.measuredHeight)
        }
    }

    private inline fun forEachVisible(block: (View) -> Unit) {
        for (i in 0 until childCount) getChildAt(i).takeIf { it.visibility != GONE }?.let(block)
    }

    // Nhận mọi LayoutParams có lề (kể cả `LinearLayout.LayoutParams` mà `addChip` dựng) — chỉ đọc width/height.
    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(WRAP, WRAP)
    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)
    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)

    private companion object {
        const val WRAP = LayoutParams.WRAP_CONTENT
    }
}
