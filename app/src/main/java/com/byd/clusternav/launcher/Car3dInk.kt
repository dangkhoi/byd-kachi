package com.byd.clusternav.launcher

import android.graphics.Rect
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import com.byd.clusternav.launcher.ContrastBackdrop.Layer

/**
 * ═══ 3.1.5 R29 pha 2b · T29-6 — MẶT "khung xe 3D" cho mực thích ứng: lớp phủ trên xe đọc đúng nền phía sau nó ═══════════════
 *
 * Spec `kachi-301-cast-steps-dialog-ia.html` §4.7 · §5.1 T29-6. Owner 14/10: *"luôn luôn nhìn thấy được"*. [ĐO máy ảo 14/10,
 * diagnostics `widget-visual-301` §12] hàng chân 3D Sáng: chữ 1,16 · icon 1,02 — mực theo một hằng, không theo nền.
 *
 * Khung [Car3dWidgetView] là MỘT mặt ([AdaptiveInk.surface]) khai lớp nó vẽ dưới một vùng bất kỳ ([groups]): ảnh nền xe (hộp đo
 * trên lưới của [Car3dBackdropView.boxUnder]) ∪ màu sơn khi vùng chạm khung xe ([InkModel.paintSpan]) · bóng đất khi chạm
 * ([InkModel.car3dVariants]). Lớp phủ khai vùng đo theo đúng vùng của nó (nhãn lốp, đĩa ghế, hàng chân, từng nút đĩa) ⇒ mỗi
 * nhãn ĐO trên nền dưới CHÍNH nó; chấm vẽ canvas ([Car3dDotsLayer.probe]) đo từng điểm bằng [AdaptiveInk.backdropAt]. 3.1.6 R29
 * (owner 15/10 "đồng đều"): khung là mặt GỐC riêng ⇒ mọi số đo đó gộp vào MỘT lần chọn họ, cả lớp phủ đổi mực cùng nhau.
 *
 * ## Sự kiện (không bao giờ mỗi khung)
 *  • ảnh nền nạp xong / đổi / căn xong · đổi màu sơn ⇒ [changed] (ép tính lại cả cây — lớp riêng đổi mà vùng giữ);
 *  • xoay DỪNG (cuối đợt khung — `Car3dWidgetView.frame`) · nhãn đặt lại không vẽ khung ([Car3dHeroLayer.reproject]) ⇒ [settle]:
 *    mặt con dời bằng translation ⇒ so vùng, chỉ cái DỜI mới đo lại; chấm đo lại; chọn lại họ ([AdaptiveInk.rescore]);
 *  • chủ đề / ảnh màn chính / độ đục / bố cục ⇒ đường sẵn có của [AdaptiveInk] (mặt thường lắng nghe layout + `onRefresh`);
 *  • lượt layout TRONG LÚC xoay (~20 Hz, nhãn dời) ⇒ [AdaptiveInk] GIỮ ([InkMotion], perf §8.1); [settle] là lượt chọn duy nhất.
 * 0 luồng · 0 nhịp; listener mới: `OnLayoutChangeListener` của mặt (như mọi mặt thường).
 */
internal object Car3dInk {

    /** Khai [host] là mặt khung 3D — mặt GỐC riêng ([AdaptiveInk.Scope.OWN]): cả lớp phủ (nhãn · hàng chân · đĩa · chấm) một họ. */
    fun declare(host: ViewGroup, backdrop: Car3dBackdropView) {
        AdaptiveInk.surface(host, listen = true, scope = AdaptiveInk.Scope.OWN, variant = VARIANT) { r -> groups(host, backdrop, r) }
    }

    /** Lớp khung 3D dưới vùng [r] (toạ độ cửa sổ). */
    fun groups(host: ViewGroup, backdrop: Car3dBackdropView, r: Rect): List<List<List<Layer>>> {
        val photo = backdrop.boxUnder(r)
        val paint = backdrop.paint?.takeIf { carArea(host, r) }?.let(InkModel::paintSpan)
        val shadow = if (backdrop.shadowHits(r)) Car3dBackdropView.SHADOW_CORE else 0
        return listOf(InkModel.car3dVariants(photo?.first, photo?.second ?: false, paint, shadow))
    }

    /** Ảnh nền xe nạp/đổi/căn xong · màu sơn đổi ⇒ giải lại mọi mặt con + chấm (MỘT lượt). */
    fun changed(host: View) = AdaptiveInk.update(host, force = true, cascade = true)

    /** Xoay dừng / nhãn đặt lại tại chỗ ⇒ mặt con đã DỜI đo lại; chấm đo lại theo chỗ chiếu mới; cả lớp phủ chọn lại MỘT họ. */
    fun settle(host: View) = AdaptiveInk.rescore(host)

    /** Vùng [r] chạm khung vẽ xe (TextureView — xe chỉ vẽ trong đó; hàng chân nằm ngoài ⇒ không cộng màu sơn). */
    private fun carArea(host: ViewGroup, r: Rect): Boolean {
        val tv = (0 until host.childCount).map(host::getChildAt).firstOrNull { it is TextureView } ?: return true
        val loc = IntArray(2).also { tv.getLocationInWindow(it) }
        return r.right > loc[0] && r.left < loc[0] + tv.width && r.bottom > loc[1] && r.top < loc[1] + tv.height
    }

    private const val VARIANT = "car3d"
}

/** R29 T29-6 — vùng đo KHÔNG lớp riêng (nhãn lốp · hàng chân 3D): nền đo = nền dưới CHÍNH vùng; họ = họ của lớp phủ 3D. */
internal fun View.inkRegion(variant: String) = AdaptiveInk.surface(this, listen = true, scope = AdaptiveInk.Scope.LAYER, variant = variant) { emptyList() }
