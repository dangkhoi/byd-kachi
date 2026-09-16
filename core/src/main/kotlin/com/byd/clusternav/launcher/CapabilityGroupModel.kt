package com.byd.clusternav.launcher

/**
 * ⚠ **VÌ SAO TỆP NÀY TÁCH RA** (U5 · T2): kiểu [CapabilityGroup] và danh mục 12 nhóm ([CapabilityGroups]) từng ở cùng
 * một tệp. Thêm nhãn tiếng Anh cho 12 nhóm đẩy tệp đó lên **491 dòng** — dưới trần 500 của dự án nhưng chỉ còn 9 dòng
 * dư, tức phiên sau thêm một nhóm là vượt trần và phải tách gấp giữa lúc đang làm việc khác. Tách theo đúng đường
 * `CapabilityModel.kt` (kiểu) ↔ registry (dữ liệu) đã có sẵn trong dự án.
 */

/**
 * Một NHÓM khả năng — nhiều mục rời gom lại thành **một câu người lái thật sự hỏi**.
 *
 * @property id mã ổn định, tiền tố `g_` — xem [CapabilityGroups.ID_PREFIX].
 * @property label nhãn cho người đọc, viết theo **câu người ta hỏi** ("Lốp", "Kính", "Cửa & khoang"), KHÔNG theo tên
 *   bảng dữ liệu ("TPMS", "bodywork"). Người ngồi trong xe hỏi *"lốp tôi ổn không"*, không hỏi *"giá trị TPMS"*.
 * @property icon tên KHÁI NIỆM `ic-group-*`, `:app` dịch sang drawable trong `KachiTheme.iconRes` — cùng quy ước với
 *   [WidgetDef.icon] / [ControlDef.icon] / [CapabilityIcons]. `:core` KHÔNG giữ mã màu và KHÔNG giữ id tài nguyên
 *   Android.
 *
 *   ⚠ Tên là **HỢP ĐỒNG giữa hai module**: đổi một bên mà không đổi bên kia thì `iconRes` rơi vào `else -> 0` và ô
 *   nhóm hiện ra **không có icon** — sai im lặng, vì không có ngoại lệ nào được ném. Nhóm dùng tiền tố riêng
 *   `ic-group-` (không dùng lại `ic-tire`/`ic-window`…) vì icon nhóm phải nói *"đây là cả bốn bánh"* chứ không phải
 *   *"đây là một cái lốp"*; T2 vẽ một icon riêng cho từng nhóm. Có test hai đầu: `:core` canh tiền tố,
 *   `:app` canh mọi tên tra ra được drawable.
 * @property domain nhóm hiển thị ở màn chọn. Chỉ để **gom chỗ bày**, không mang ngữ nghĩa an toàn (xem [Domain]).
 *   ⚠ Thành viên KHÔNG buộc cùng domain với nhóm: một mã khai ở domain này vẫn được một nhóm của domain khác dùng,
 *   vì nhóm gom theo **câu người lái hỏi** còn domain gom theo bảng dữ liệu — bắt hai thứ đó phải khớp nhau sẽ làm
 *   nhóm sai theo cách người dùng nghĩ.
 * @property shape bộ vẽ dùng chung (§4.3): [WidgetShape.BOARD] · [WidgetShape.STRIP] · [WidgetShape.CARD]. Ba giá
 *   trị này ĐÃ có sẵn trong enum từ trước — [WidgetShape.BOARD] còn ghi rõ *"4 lốp"*. Nghĩa là việc gom nhóm
 *   KHÔNG phải khái niệm mới, mà là **làm nốt thứ đã thiết kế nhưng chưa dựng**.
 * @property reads mã datum ĐỌC trong [TelemetryRegistry], theo thứ tự hiện ra.
 * @property writes mã nút ([ControlRegistry]) hoặc gói lệnh ([ActionMacros]) — hàng dưới cùng ô (§4.3).
 * @property sub **nội dung** nhóm nói bằng chữ, KHÔNG có số. Dùng cho dòng phụ của ô chọn (T4): người dùng thấy ô
 *   *"Lốp"* mà không biết bên trong có gì thì vẫn phải đoán, và đoán sai thì họ đặt bốn ô rời như trước.
 *
 *   ⚠⚠ **CẤM viết số vào đây** (có [init] chặn): số thành viên phải lấy từ CHÍNH [reads]/[writes] — xem
 *   [contentLine]. Nếu chép tay *"4 bánh"* thì ngày ai đó thêm/bớt một thành viên, dòng phụ nói sai mà **không
 *   test nào đỏ** — đúng họ lỗi hai-bản-sao mà dự án đã trả giá nhiều lần (`unitPrefs` từng có 4 bản).
 */
data class CapabilityGroup(
    val id: String,
    override val label: String,
    val icon: String,
    val domain: Domain,
    val shape: WidgetShape,
    val reads: List<String>,
    val writes: List<String> = emptyList(),
    val sub: String = "",
    /** Nhãn tiếng Anh (U5 · T2) — tham số mặc định, xem KDoc [Strings]. */
    override val labelEn: String? = null,
    /**
     * [sub] bằng tiếng Anh. `null` ⇒ [displaySub] lùi về [sub].
     *
     * ⚠ Cùng luật **CẤM viết số** như [sub] (có [CapabilityGroups.init] chặn cả hai): số thành viên phải đến từ
     * [reads]/[writes] qua [contentLine], không phải chép tay ở đây rồi lệch khi ai đó thêm/bớt một thành viên.
     */
    val subEn: String? = null,
) : Localized {
    /** Mọi thành viên: phần XEM trước, phần BẤM sau — đúng thứ tự trình bày của §4.3. */
    val members: List<String> get() = reads + writes

    /** Nhóm có nút bấm không. Chỉ 3 nhóm có (kính · cửa & khoang · đèn) — xem [CapabilityGroups]. */
    val hasWrites: Boolean get() = writes.isNotEmpty()

    /** [sub] theo [Strings.current] — tự lùi về tiếng Việt nếu chưa dịch. */
    val displaySub: String get() = Strings.pick(sub, subEn)

    /**
     * Dòng phụ của ô chọn: **số đếm lấy từ dữ liệu**, chữ lấy từ [sub].
     *
     * Ví dụ thật: `"8 mục · áp suất + nhiệt độ từng bánh"` (Lốp) ·
     * `"4 mục · 6 nút · phần trăm mở + mở/đóng từng kính"` (Kính).
     *
     * Ghép ở `:core` chứ không ở tầng vẽ vì có HAI màn chọn (ngăn kéo + Cài đặt) — ghép ở tầng vẽ là hai bản sao,
     * và chúng sẽ lệch nhau đúng lúc ai đó sửa một chỗ.
     *
     * U5 · T2: cả ba mảnh đều theo ngôn ngữ (từ đếm · từ "nút" · [displaySub]), nên câu tiếng Anh không bị trộn.
     */
    val contentLine: String
        get() = buildString {
            append("$visibleReadCount " + Strings.t("mục", if (visibleReadCount == 1) "item" else "items"))
            if (writes.isNotEmpty()) {
                append(" · ${writes.size} " + Strings.t("nút", if (writes.size == 1) "button" else "buttons"))
            }
            val s = displaySub
            if (s.isNotEmpty()) append(" · $s")
        }

    /**
     * Số mục **người dùng THẤY** trong ô.
     *
     * ## ⚠⚠ [KIỂM TOÁN UX mục 6] Con số này từng KHÁC số mã datum, và bản trước đếm sai
     * [ĐO] màn chọn từng ghi *"Cảm biến đỗ — **2 mục**"* trong khi ô đó hiện **8 ô vùng** + một dòng âm lượng: mã
     * `radar_zones` là một mã mang cả 8 vùng. Nhóm đó đã bị gỡ cùng toàn bộ ADAS/an toàn (owner 2026-09-16) nên
     * hôm nay **không mã nào nở ra nhiều ô con** ⇒ mỗi mã đúng một ô.
     *
     * ⚠ Ngày nào lại có một mã "nở ra nhiều ô" thì phải khai số nở ở MỘT chỗ (như bảng `EXPANDING` cũ) chứ không
     * sửa dòng phụ bằng tay — luật *"không chép tay số"* của [sub] vẫn giữ nguyên.
     */
    val visibleReadCount: Int get() = reads.size
}
