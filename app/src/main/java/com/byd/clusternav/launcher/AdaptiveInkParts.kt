package com.byd.clusternav.launcher

import android.graphics.Rect
import android.view.View
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
}
