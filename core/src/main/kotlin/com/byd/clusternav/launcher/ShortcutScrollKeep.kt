package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ShortcutGridFit.Scroll

/**
 * ═══ 2.93 · SHORTCUT-GRID-SCROLL-KEEP + SHORTCUT-SCROLL-DOCK-RELAYOUT — vị trí cuộn của lưới lối tắt SỐNG QUA lượt đo/dựng ═══
 *
 * Spec `docs/specs/kachi-293-slot.html` R1/R2. Lưới `w_apps` cuộn được từ 2.92 (`ShortcutGridLayout`, R3 của spec 292). Hai
 * lỗi cùng một gốc — vị trí cuộn là MỘT con số bị ghi đè bởi mọi lượt đo:
 *
 *  - **SCROLL-KEEP** [SUY đọc mã 2.92]: `ShortcutIconsView.rebuild` dựng một `ShortcutGridLayout` MỚI (cuộn = 0) ở MỌI phát tin
 *    cài/gỡ/đổi gói của hệ thống — kể cả gói không nằm trong lối tắt —, mỗi lần đổi danh sách và mỗi lần gắn lại view ⇒ vị trí
 *    về 0 và cú kéo đang dở bị cắt.
 *  - **DOCK-RELAYOUT** [ĐO máy ảo QA 2.92, 2/2 lần]: thanh nút cạnh bên ở 150 % + 22 app, cuộn tới cuối (264 px) ⇒ mở app rồi
 *    về HOME ⇒ còn 43 px. Số học khớp đúng ba số [SUY]: khung thật 1362×148 ⇒ `maxScroll` 264; khung lượt đầu 1558×123 —
 *    đúng hình học của cấu hình thanh MẶC ĐỊNH (dưới, 100 %: 1 080 − 48 − 63 − 13 − 139 − 13 = 804 cao, ô `2,0,10,1` ⇒
 *    1558×123) ⇒ `maxScroll` 43 ⇒ lượt đo trung gian KẸP vị trí và lượt đúng sau đó không có gì để trả lại. Lượt trung gian
 *    từ đâu ra: [CHƯA BIẾT] (nhật ký `WidgetFit` 2.93 mang mã khung để chốt cùng view hay hai màn).
 *
 * Luật (thuần, test ở `ShortcutScrollKeepTest`): tách vị trí NGƯỜI LÁI CHỌN ([Wanted] — chỉ đổi khi người lái kéo / trôi /
 * cuộn bằng trợ năng) khỏi vị trí ĐANG ÁP (= [Wanted] kẹp vào quãng của khung hiện tại, [applied]). Lượt đo nào cũng chỉ
 * đọc [Wanted], không ghi ⇒ một lượt đo ở khung lạ không xoá được lựa chọn của người lái; lượt đo kế ở khung thật trả lại
 * đúng chỗ. Lượt dựng lại chuyển [Wanted] sang khung mới (kẹp theo quãng mới — cùng luật `settleScroll` cũ).
 */
object ShortcutScrollKeep {

    /**
     * Vị trí người lái ĐÃ CHỌN trên trục [axis] (px dọc trục, ≥ 0). [ORIGIN] = chưa cuộn bao giờ. Không phải vị trí đang áp:
     * khung đang hiện ngắn hơn thì [applied] kẹp, còn con số này giữ nguyên.
     */
    data class Wanted(val axis: Scroll, val px: Int) {
        companion object {
            val ORIGIN = Wanted(Scroll.NONE, 0)
        }
    }

    /**
     * Vị trí ÁP cho phép khớp [fit]: cùng trục cuộn ⇒ [Wanted.px] kẹp vào `[0, maxScrollPx]`; khung không cuộn hoặc cuộn
     * theo trục KHÁC ⇒ 0 (con số của trục cũ vẫn nằm trong [wanted] — lượt sau trở lại trục cũ thì trả lại được).
     */
    fun applied(wanted: Wanted, fit: ShortcutGridFit.Fit): Int =
        if (fit.scroll == Scroll.NONE || fit.scroll != wanted.axis) 0 else wanted.px.coerceIn(0, fit.maxScrollPx)

    /**
     * Người lái vừa cuộn tới [px] (đã kẹp theo khung đang hiện) trên trục [axis] ⇒ đây là lựa chọn mới. Trục [Scroll.NONE]
     * (khung không cuộn) ⇒ [Wanted.ORIGIN].
     */
    fun userScrolled(axis: Scroll, px: Int): Wanted =
        if (axis == Scroll.NONE) Wanted.ORIGIN else Wanted(axis, px.coerceAtLeast(0))

    /**
     * Phát tin cài/gỡ/đổi gói [pkg] có chạm danh sách lối tắt [listed] không. Không mang tên gói (`null`/rỗng) ⇒ `true`:
     * không biết thì làm mới (rẻ: chỉ nạp lại icon — [needsRebuild] mới quyết dựng lại cấu trúc).
     */
    fun touches(pkg: String?, listed: Collection<String>): Boolean = pkg.isNullOrEmpty() || pkg in listed

    /**
     * Có phải DỰNG LẠI cấu trúc (ô mới, khung lưới mới) không: chỉ khi danh sách hiển thị khác danh sách đã dựng ([built]
     * `null` = chưa dựng lần nào). Cài/gỡ một gói TRONG danh sách không đổi danh sách ⇒ chỉ nạp lại icon tại chỗ.
     */
    fun needsRebuild(built: List<AppShortcut>?, now: List<AppShortcut>): Boolean = built == null || built != now
}
