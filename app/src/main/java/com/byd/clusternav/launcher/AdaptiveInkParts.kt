package com.byd.clusternav.launcher

import android.graphics.Rect
import android.text.Spanned
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.byd.clusternav.launcher.ContrastBackdrop.Layer

/**
 * 3.1.6 R29 — hai bản ghi trạng thái của [AdaptiveInk] gắn lên view qua tag (`R.id.kachi_ink_surface` / `R.id.kachi_ink_mark`):
 * MẶT ([InkSurface]) và dấu MỰC ([InkMark]). Tách khỏi `AdaptiveInk.kt` để giữ trần 500 dòng (perf §8.1 thêm [InkMotion]). CHỈ
 * luồng chính.
 */
internal class InkSurface(
    val variant: Any?, val listen: Boolean, val scope: AdaptiveInk.Scope, val wantsUnder: Boolean, val groups: (Rect) -> List<List<List<Layer>>>,
) : View.OnLayoutChangeListener {
    val rect = Rect()
    var backdrop: ContrastBackdrop? = null
    /** Nền dưới vùng KHÔNG gồm lớp của chính mặt (nút đĩa đọc qua [under]). */
    var under: ContrastBackdrop? = null
    var palette: KachiPalette? = null
    var fraction = -1.0
    var art: WallArt? = null
    /** Lượt đo cuối có đổi nền không. */
    var changed = false
    /** Mặt gốc: họ đang hiện (−1 = chưa chọn; giữ qua lần dựng lại mặt — trễ) · lần chọn cuối · các ô (mặt, vai) đã tính. */
    var family = -1
    var choice: FamilyChoice? = null
    var seen: Set<Long> = emptySet()
    var motion: InkMotion? = null
    override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) { if (v != null) AdaptiveInk.update(v) }
}

/** Dấu trên một phần tử mực: vai màu + có tô cả drawable kép không; [after] = việc của chỗ gọi sau mỗi lần tô ([onInk]). */
internal class InkMark(val key: InkKey, val compound: Boolean, var after: ((View, Resolution, ContrastBackdrop) -> Unit)? = null) {
    var last: Resolution? = null
    /** Nền tệ nhất dưới CHÍNH vùng của phần tử ([rect], toạ độ cửa sổ) — đo khi vùng / mặt chứa nó đổi. */
    var ground: ContrastBackdrop? = null
    val rect = Rect()
    /** R29-RESOLVE-STORM — nền ([ground], cùng đối tượng) mà [last] được giải trên · mực đã tô lên view lần cuối. */
    var solvedOn: ContrastBackdrop? = null
    var shown: Int? = null
    /** Soát Pass 9 [P2]: đối tượng `ColorFilter` mà `paint` đặt lên `ImageView` — ai khác `setColorFilter`/xoá thì khác đối tượng ⇒ tô lại. */
    var filter: android.graphics.ColorFilter? = null
}

/** Phần tử đo được: đã gắn cửa sổ và khác 0×0 — đúng điều kiện của `AdaptiveInk.measure`/`groundOf` (chỗ khác bỏ qua nó). */
internal fun inkMeasurable(v: View) = v.isAttachedToWindow && v.width > 0 && v.height > 0

/** [v] đang hiện đúng mực [r] (chữ không đoạn màu riêng, không drawable kép) ⇒ lời tô lặp là no-op. */
internal fun inkShown(v: View, m: InkMark, r: Resolution): Boolean = m.shown == r.ink && !m.compound && when (v) {
    is TextView -> v.currentTextColor == r.ink && (v.text as? Spanned)?.getSpans(0, v.text.length, AdaptiveInk.InkSpan::class.java).isNullOrEmpty()
    is ImageView -> v.colorFilter != null && v.colorFilter === m.filter   // so ĐỐI TƯỢNG: `setColorFilter(int)` tạo filter mới mỗi lần (AOSP r47 ImageView.java:1509-1510, so tham chiếu :1565-1566)
    else -> false
}

internal fun inkRaw(v: View, color: Int, compound: Boolean) {
    when (v) {
        is TextView -> { v.setTextColor(color); if (compound) v.compoundDrawablesRelative.forEach { it?.setTint(color) } }
        is ImageView -> v.setColorFilter(color)
    }
}

internal fun inkRole(v: View, key: InkKey) = InkModel.role(key, icon = v is ImageView, large = v is TextView && inkLarge(v))

private fun inkLarge(tv: TextView): Boolean {
    val sp = tv.textSize / tv.resources.displayMetrics.density
    val bold = tv.typeface?.isBold == true || tv.paint.isFakeBoldText
    return sp >= InkModel.LARGE_SP || (bold && sp >= InkModel.LARGE_BOLD_SP)
}

/** Ô (phần tử, vai, loại) — một phần tử của phép chọn họ; view mới hoặc vai mới ⇒ phần tử mới ⇒ chọn lại họ. */
internal fun inkSlot(v: View, key: InkKey, role: InkRole): Long =
    (System.identityHashCode(v).toLong() shl 16) or (key.ordinal * 8 + role.ordinal).toLong()
