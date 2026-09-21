package com.byd.clusternav.launcher

/**
 * ═══ UX-OVERHAUL · WP4 — VỊ TRÍ TỪNG ITEM **BÊN TRONG** HAI THANH ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP4 · R4.1. Owner: *"cho chọn vị trí từng item BÊN TRONG header +
 * taskbar (panel Tuỳ biến thanh: kéo/chọn thứ tự-vị trí). KHÔNG đổi vị trí bar. Taskbar 4-cạnh giữ nguyên."*
 *
 * ## Phạm vi — cái KHÔNG thuộc tệp này, để chỗ trống không bị đọc thành sơ suất
 *  • **Vị trí của chính THANH** không đổi: thanh trên vẫn ở trên, thanh nút vẫn chọn 1 trong 4 viền
 *    ([DockConfig.edge]) và cờ ẩn/hiện ([DockConfig.visible]). WP4 chỉ nói về **thứ tự các vật bên trong**.
 *  • **Chọn item nào** (chip nào lên thanh trên, nút nào vào thanh nút) đã có sẵn: [TopStripConfig.ids] và
 *    [DockConfig.enabled]. WP4 thêm đúng một thứ còn thiếu: **sắp lại chỗ đứng**.
 *
 * ## Vì sao MỘT phép sắp lại dùng cho CẢ HAI thanh ([BarOrder.move])
 * Thanh trên sắp một danh sách [HeaderItem]; thanh nút sắp một danh sách **mã khả năng** (`String`). Hai loại dữ
 * liệu khác nhau, nhưng *phép* thì y hệt: dời một phần tử lên/xuống một bậc, đụng đầu/đụng cuối thì không làm gì.
 * Viết hai bản là dựng sẵn bẫy hai-bản-sao mà dự án đã trả giá nhiều lần (`unitPrefs` 4 bản · `customLayout` 2
 * bản): chúng sẽ lệch nhau ở đúng lần ai đó sửa một bên — ví dụ một bên kẹp chỉ số, bên kia để nó âm.
 *
 * Tổng quát theo `<T>` (không theo một giao diện chung) vì hai danh sách **không có** điểm chung nào về ngữ nghĩa
 * để đặt vào một giao diện; điểm chung duy nhất là *chúng là danh sách có thứ tự*.
 */
object BarOrder {

    /**
     * Dời [item] đi [delta] bậc trong [items] (âm = về đầu, dương = về cuối).
     *
     * **Trả về CHÍNH [items] khi không dời được** — đụng biên, hoặc [item] không có trong danh sách. Tính chất này
     * là thứ tầng vẽ dựa vào: nút ◀/▶ ở đầu/cuối danh sách phải **nói ra** là không dời được (làm mờ), và cách
     * duy nhất để nó biết mà không nhân đôi luật là so kết quả với đầu vào. Ném ngoại lệ thì mỗi chỗ gọi phải tự
     * kiểm trước; trả một danh sách *đã kẹp* thì chỗ gọi tưởng vừa dời được và ghi bền một lượt vô nghĩa.
     *
     * ⚠ [delta] **không** bị kẹp về ±1: nút bấm chỉ dời một bậc, nhưng một cú **kéo-thả** dời nhiều bậc một lượt,
     * và đó là cùng một phép. Kẹp ở đây sẽ buộc đường kéo-thả gọi vòng lặp — tức lại một phép sắp thứ hai.
     */
    fun <T> move(items: List<T>, item: T, delta: Int): List<T> {
        val from = items.indexOf(item)
        if (from < 0 || delta == 0) return items
        val to = from + delta
        if (to < 0 || to > items.lastIndex) return items
        val out = items.toMutableList()
        out.removeAt(from)
        out.add(to, item)
        return out
    }

    /** Dời được [item] theo [delta] hay không — CÙNG luật với [move], không phải một phép kiểm thứ hai. */
    fun <T> canMove(items: List<T>, item: T, delta: Int): Boolean = move(items, item, delta) !== items
}

/**
 * MỘT VẬT trên thanh trạng thái trên — đơn vị mà người dùng sắp được chỗ đứng (WP4 · R4.1).
 *
 * ## Vì sao đồng hồ + ngày là MỘT vật ([CLOCK]), không phải hai
 * Chúng là một cụm chữ đọc liền nhau (*"14:07 · Chủ nhật, 20/09"*); tách ra thì người dùng có thể đặt ngày ở
 * giữa hàng chip và đồng hồ ở cuối thanh — một trạng thái không ai muốn nhưng bộ chọn lại mời làm. Số lựa chọn
 * ít hơn mà **không** mất khả năng nào có ích.
 *
 * ## Vì sao hàng chip là MỘT vật ([CHIPS])
 * Thứ tự **giữa các chip** đã do [TopStripConfig.ids] quyết (và bộ chọn chip đã bày ra). Đưa từng chip vào đây
 * là hai bề mặt cùng quyết một thứ — đúng bẫy hai-bản-sao. Ở đây hàng chip đứng như một khối.
 *
 * @property info Vật **THÔNG TIN** (chỉ đọc) hay **NÚT** (bấm được). Dùng ở hai chỗ: WP5 giữ nguyên cỡ chữ của
 *   vật thông tin trong khi hạ cỡ nút còn 70 %, và bộ chọn nhóm hai loại cho dễ hiểu. Là **dữ liệu** ở `:core`
 *   nên cả hai chỗ đọc cùng một sự thật.
 */
enum class HeaderItem(
    override val label: String,
    override val labelEn: String,
    val info: Boolean,
) : Localized {
    CLOCK("Đồng hồ và ngày", "Clock and date", info = true),
    CHIPS("Chip trạng thái xe", "Car status chips", info = true),
    VOICE("Nút nói", "Talk button", info = false),
    APPS("Nút ứng dụng", "Apps button", info = false),
    SETTINGS("Nút cài đặt", "Settings button", info = false),
    PROFILE("Chip hồ sơ", "Profile chip", info = false),
}

/**
 * THỨ TỰ các vật trên thanh trạng thái trên — cấu hình bền, **theo HỒ SƠ** (khoá `header_order`).
 *
 * Theo hồ sơ vì cùng lẽ với [TopStripConfig.ids] và [DockConfig.enabled] (S4 *"hồ sơ là tất cả"*): hai tài xế
 * quen tay hai kiểu là chuyện thường, và mọi lựa chọn bố cục của launcher đã theo hồ sơ.
 *
 * ## Bất biến: [order] là một PHÉP HOÁN VỊ ĐỦ của [HeaderItem]
 * Không phải "một tập con". Vắng một vật thì nó **biến mất khỏi thanh** mà không có bề mặt nào nói ra —
 * tức một tính năng mất im lặng vì một dòng prefs. Ẩn/hiện là việc riêng và đã có chỗ riêng cho từng vật
 * (chip: bộ chọn chip · nút nói: công tắc *Nút mic* · hai nút kia: luôn hiện). [init] ép bất biến này, và
 * [decode] **chữa** dữ liệu thiếu thay vì từ chối (xem ở đó).
 */
data class HeaderLayout(val order: List<HeaderItem> = DEFAULT_ORDER) {

    init {
        require(order.toSet() == HeaderItem.values().toSet() && order.size == HeaderItem.values().size) {
            "thứ tự thanh trên phải chứa ĐÚNG MỘT LẦN mỗi vật, nhận: $order"
        }
    }

    /** Dời một vật đi [delta] bậc — uỷ quyền [BarOrder.move] (không dời được ⇒ trả về chính vật này). */
    fun move(item: HeaderItem, delta: Int): HeaderLayout {
        val next = BarOrder.move(order, item, delta)
        return if (next === order) this else HeaderLayout(next)
    }

    fun canMove(item: HeaderItem, delta: Int): Boolean = BarOrder.canMove(order, item, delta)

    /**
     * Hàng chip có căn về **mép CUỐI** của khoảng co giãn hay không.
     *
     * Hàng chip là phần **co giãn** của thanh (`0dp + weight 1`, xem `KachiTopStrip.fitChips`), nên khoảng trống
     * của thanh nằm *bên trong* nó. Căn END ⇒ trống ở **trước** chip; căn START ⇒ trống ở **sau** chip. Luật:
     * khoảng trống nên nằm **giữa** thanh, tức về phía có nhiều vật hơn… và [ĐO số học] với 6 vật thì tính chất
     * đó quy về một câu đơn giản hơn: chip **không** phải vật đầu tiên ⇒ căn END (trống lùi về giữa, mặc định
     * giữ y nguyên hình dạng 1.85). Chip đứng đầu ⇒ căn START, nếu không thì thanh mở đầu bằng một quãng trống.
     *
     * Ở `:core` vì nó là một LUẬT kiểm được off-car, không phải một con số của tầng vẽ.
     */
    val chipsAlignEnd: Boolean get() = order.firstOrNull() != HeaderItem.CHIPS

    companion object {
        /**
         * Thứ tự MẶC ĐỊNH = **đúng hình dạng 1.85** (đồng hồ · ngày → chip → nói → ứng dụng → cài đặt → hồ sơ).
         * Ai không sửa gì thì không thấy gì khác — có bài canh khoá cả thứ tự này.
         */
        val DEFAULT_ORDER: List<HeaderItem> = listOf(
            HeaderItem.CLOCK, HeaderItem.CHIPS, HeaderItem.VOICE,
            HeaderItem.APPS, HeaderItem.SETTINGS, HeaderItem.PROFILE,
        )

        val DEFAULT = HeaderLayout(DEFAULT_ORDER)

        /**
         * `"CLOCK,CHIPS,…"` → thứ tự. **Chữa** dữ liệu hỏng, không từ chối nó.
         *
         * Ba ca thật, và cả ba phải ra một thanh dùng được:
         *  1. **rỗng / null** (máy chưa từng sắp) ⇒ [DEFAULT].
         *  2. **tên lạ** (bản sau xoá một vật, hoặc người dùng sửa tay) ⇒ bỏ tên đó.
         *  3. **THIẾU vật** (bản sau THÊM một vật, mà chuỗi trên đĩa của xe đang chạy viết trước đó) ⇒ nối các
         *     vật còn thiếu vào cuối theo thứ tự [DEFAULT_ORDER]. Đây là ca quan trọng nhất: không có nó thì
         *     nâng cấp bản mới làm vật mới **không bao giờ xuất hiện** trên thanh của người đang dùng, và
         *     [HeaderLayout] sẽ ném ngay lúc nạp ⇒ launcher sập khi mở (đúng lỗi `DEFAULT_WORKSPACE` ở P9).
         *
         * Chuỗi lưu là **tên hằng** đọc được bằng mắt (cứu tay qua `run-as … cat`), cùng lệ `grid_layout`/`top_strip`.
         */
        fun decode(s: String?): HeaderLayout {
            val names = s?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return DEFAULT
            val kept = names.mapNotNull { n -> HeaderItem.values().firstOrNull { it.name == n } }.distinct()
            if (kept.isEmpty()) return DEFAULT
            return HeaderLayout(kept + DEFAULT_ORDER.filterNot { it in kept })
        }

        fun encode(l: HeaderLayout): String = l.order.joinToString(",") { it.name }
    }
}
