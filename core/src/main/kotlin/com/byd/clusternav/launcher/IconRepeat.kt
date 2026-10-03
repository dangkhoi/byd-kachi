package com.byd.clusternav.launcher

/**
 * Luật *"icon có PHÂN BIỆT được không"* — MỘT chỗ cho ô nhóm ([GroupBoardModel.iconsDistinguish] /
 * [GroupBoardModel.actionIconsDistinguish]) và lưới widget (L5 WIDGET-FIT-ALL: dạng CHỈ-ICON của [GridFit] chỉ được
 * phép khi [ofIds] đúng).
 *
 * Rút ra khỏi `GroupBoardModel` (2.87, L5) vì lưới widget cần ĐÚNG luật đó: khung quá nhỏ cho nhãn thì lưới có thể bỏ
 * nhãn giữ icon — nhưng bốn nút kính cùng một hình mà bỏ nhãn là người lái bấm nhầm kính. Chép lại con số 3 ở chỗ thứ
 * hai là hai luật sẽ lệch nhau.
 */
object IconRepeat {

    /**
     * Số lần một hình được lặp trước khi coi là "không phân biệt được".
     *
     * 3 chứ không 2: một CẶP trái/phải cùng hình là chuyện bình thường và vẫn đọc được nhờ nhãn; từ ba ô thì
     * không còn là cặp nữa mà là một dãy đồng nhất.
     */
    const val CAP = 3

    /** `true` nếu không hình nào trong [icons] lặp ≥ [CAP] lần. */
    fun distinguishable(icons: Iterable<String>): Boolean = icons.groupingBy { it }.eachCount().none { it.value >= CAP }

    /**
     * Cùng luật cho một danh sách MÃ khả năng (ô widget): hình tra từ [CapabilityCatalog.pick] — nguồn hình duy nhất
     * của mọi bề mặt. Mã lạ / không có hình ⇒ bỏ qua (không có hình thì không có gì để lặp).
     */
    fun ofIds(ids: List<String>): Boolean =
        distinguishable(ids.mapNotNull { CapabilityCatalog.pick(it)?.icon?.takeIf { icon -> icon.isNotEmpty() } })
}
