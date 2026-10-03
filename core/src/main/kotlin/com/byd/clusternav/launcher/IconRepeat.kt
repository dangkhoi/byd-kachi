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
     * Mảnh tên icon chỉ VỊ TRÍ trên cùng một hình (`ic-car-top-window-lf` ↔ `-rf`/`-lr`/`-rr`/`-all`, `ic-seat-heat-left`
     * ↔ `-right`): các biến thể ấy khác nhau ở một dấu nhỏ trên CÙNG một bóng xe/ghế nhìn từ trên.
     */
    private val POSITION = setOf("lf", "rf", "lr", "rr", "fl", "fr", "rl", "all", "left", "right", "front", "rear")

    /** Bóng hình của [icon] — tên bỏ các mảnh [POSITION] (`ic-car-top-window-lf` ⇒ `ic-car-top-window`). */
    fun silhouette(icon: String): String = icon.split('-').filterNot { it in POSITION }.joinToString("-")

    /**
     * Luật cho ô KHÔNG NHÃN (dạng chỉ-icon của lưới widget, L5): đếm theo [silhouette], không theo tên tệp. QA 04/10
     * ([ĐO] máy ảo, ảnh `l5/icononly-zoom.png`): bốn nút kính mang bốn tên khác nhau nên luật theo tên cho bỏ nhãn,
     * nhưng ở 20–40dp bốn bóng xe chỉ khác một dấu kính cỡ 1–2px — người lái không phân biệt được kính nào. Ô nhóm vẫn
     * dùng [distinguishable] theo tên vì ở đó nhãn LUÔN hiện cạnh icon (icon chỉ là phụ).
     */
    fun distinguishableWithoutLabels(icons: Iterable<String>): Boolean = distinguishable(icons.map(::silhouette))

    /**
     * Cùng luật cho một danh sách MÃ khả năng (ô widget): hình tra từ [CapabilityCatalog.pick] — nguồn hình duy nhất
     * của mọi bề mặt. Mã lạ / không có hình ⇒ bỏ qua (không có hình thì không có gì để lặp). Đây là cổng của dạng
     * CHỈ-ICON (nhãn bị ẩn) ⇒ đếm theo bóng hình ([distinguishableWithoutLabels]).
     */
    fun ofIds(ids: List<String>): Boolean =
        distinguishableWithoutLabels(ids.mapNotNull { CapabilityCatalog.pick(it)?.icon?.takeIf { icon -> icon.isNotEmpty() } })
}
