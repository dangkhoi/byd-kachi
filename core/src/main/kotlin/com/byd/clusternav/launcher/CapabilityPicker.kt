package com.byd.clusternav.launcher

/**
 * ═══ G1 · T4 — MÀN CHỌN BÀY NHÓM TRƯỚC ═══════════════════════════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm được off-car. Spec `docs/specs/kachi-capability-groups.html`
 * §4.2 (*"màn chọn xếp Nhóm lên trước, mục rời vẫn còn cho ai cần"*) + R2.
 *
 * ## Bệnh nó chữa — và vì sao T1–T3 CHƯA chữa được
 * T1 dựng 12 nhóm, T3 vẽ được ô nhóm và đặt được vào ô giữa màn. Nhưng người dùng **gặp** nhóm ở đâu? Trước T4:
 * [CapabilityCatalog.byDomain] rải 12 nhóm vào **trong** lĩnh vực của chúng, mỗi lĩnh vực một nhóm nằm ở đầu — nên
 * muốn thấy ô "Lốp" phải cuộn qua lĩnh vực Năng lượng (28 ô) + Động lực + Khí hậu trước. Đúng câu owner nói:
 * *"không ai xem áp suất lốp 1 lốp cả, phải xem cả 4 cùng lúc"* — nếu nhóm có mà phải cuộn qua cả trăm ô rời mới
 * thấy thì coi như chưa làm.
 *
 * ## Vì sao phần này ở `:core` chứ không viết ở mỗi màn chọn
 * Có **HAI** màn chọn (ngăn kéo cho ô giữa màn · Cài đặt cho thanh nút xe). Viết ở tầng vẽ là hai bản sao của cùng
 * một quyết định, và chúng sẽ lệch nhau — dự án đã trả giá đúng kiểu này nhiều lần (`unitPrefs` từng có **4** bản
 * sao, `customLayout` 2 bản). Ở đây có đúng một chỗ trả lời ba câu: *"nhóm nào bày ra"*, *"mục rời nào còn lại"*,
 * *"chỗ này thuộc nhóm nào"*.
 *
 * ## ⚠⚠ [singlesOf] KHÔNG PHẢI phép dọn cho gọn — nó chặn một lỗi CÓ THẬT
 * Cả hai màn chọn giữ **bảng tra `mã khả năng → view`** để tô ô đang bật (`AppDrawer.widgetTiles`,
 * `CapabilityGridSection.tiles`). Bày nhóm ở mục riêng RỒI vẫn để nó nằm trong lĩnh vực nghĩa là **cùng một mã có
 * hai ô** ⇒ `tiles[id]` bị ghi đè ⇒ chỉ ô sau được tô, ô trước nói sai cấu hình. Đó đúng ba lỗi cùng lúc mà RW0 đã
 * ghi lại (xem KDoc [CapabilityGridSection] nếu đọc từ `:app`). Vì thế lọc nhóm khỏi lĩnh vực là **bắt buộc**, và
 * có test canh cả hai màn đều gọi hàm này.
 *
 * Lọc ở đây KHÔNG xoá mục rời nào (§4.2 + OQ2): 123 datum · 64 nút · 9 widget · 4 gói lệnh còn nguyên, chỉ 12 mã
 * NHÓM thôi không xuất hiện hai lần. Có test đếm.
 */
object CapabilityPicker {

    /**
     * Tiêu đề mục NHÓM — thứ người dùng gặp đầu tiên.
     *
     * ⚠ U5 · T2 đổi từ `const val` sang thuộc tính có getter: `const` là **hằng biên dịch**, không đổi được lúc chạy
     * ⇒ nó sẽ mãi tiếng Việt. Đây đúng cái bẫy `KachiTheme` gặp với 13 `const val` màu (spec §1) — cùng nguyên nhân,
     * cùng cách chữa. Mọi chỗ gọi vẫn là phép đọc thuộc tính nên không phải sửa gì.
     */
    val GROUPS_TITLE: String get() = Strings.t("Nhóm — xem cả cụm cùng lúc", "Groups — see a whole set at once")

    /**
     * Câu phụ của mục nhóm. Nói **việc**, không nói kiến trúc: người ngồi trong xe không cần biết chữ "nhóm khả
     * năng", họ cần biết một ô cho cả bộ lốp thì đỡ phải đặt bốn ô.
     *
     * ⚠ Không viết số vào câu này (cùng lý do [CapabilityGroup.sub]): số nhóm/số thành viên đổi thì câu nói sai.
     */
    val GROUPS_NOTE: String
        get() = Strings.t(
            "Một ô cho cả bộ: cả bộ lốp, cả bộ kính, cả dải đèn. Đặt một ô thay vì đặt từng cái.",
            "One tile for a whole set: all the tyres, all the windows, the whole light strip. " +
                "Place one tile instead of placing each one.",
        )

    /** Tiêu đề phần MỤC RỜI — vẫn còn nguyên cho ai chỉ muốn một con số to giữa màn (§4.2). */
    val SINGLES_TITLE: String get() = Strings.t("Từng mục riêng", "Individual items")

    /** Câu mở đầu của phép GỢI Ý nhóm ở từng lĩnh vực — xem [groupHint]. */
    val HINT_PREFIX: String get() = Strings.t("Đã có trong nhóm: ", "Already in a group: ")

    /**
     * 12 ô nhóm theo **thứ tự khai** của [CapabilityGroups.ALL] (thứ hỏi thường xuyên trước).
     *
     * Lấy qua [CapabilityCatalog.all] chứ không tự dựng [CapabilityPick]: mức bằng chứng của nhóm (dấu *"chưa kiểm
     * trên xe"*) và dòng phụ đã được ghép ở đó rồi. Dựng bản thứ hai ở đây là hai chỗ quyết định cùng một thứ.
     */
    fun groupPicks(): List<CapabilityPick> = CapabilityCatalog.all().filter { it.group }

    /**
     * Mục rời của một lĩnh vực — **bỏ nhóm** vì nhóm đã bày ở mục riêng phía trên.
     *
     * Xem cảnh báo ở KDoc lớp: đây là phép chặn lỗi "hai ô cùng một mã", không phải phép dọn cho gọn.
     */
    fun singlesOf(picks: List<CapabilityPick>): List<CapabilityPick> = picks.filterNot { it.group }

    /**
     * GỢI Ý: trong danh sách mục rời này, những mục nào **đã có sẵn** trong nhóm nào.
     *
     * Trả `""` nếu không mục nào thuộc nhóm ⇒ chỗ gọi không vẽ gì (im lặng khi không có gì để nói — cùng luật với
     * vòng kiểm quyền: *"đủ thì IM LẶNG"*).
     *
     * ## Vì sao gợi ý đặt ở TIÊU ĐỀ LĨNH VỰC, không đặt trong từng ô
     * [ĐO] 88/123 datum thuộc nhóm. Thêm một dòng *"trong nhóm Lốp"* vào từng ô là **88 dòng chữ** trong lưới ô
     * 40dp — làm ô chật hơn và thành nhiễu, mà người dùng vẫn phải đọc từng ô mới thấy. Một dòng ở đầu lĩnh vực nói
     * đúng điều cần nói (*"thứ bạn đang cuộn qua đã có sẵn cả cụm ở trên"*) và tốn **6 dòng** cho toàn bộ danh sách:
     * chỉ 6/9 lĩnh vực có nhóm phủ.
     *
     * Đi qua [CapabilityGroups.groupsContaining] chứ không tự dò `reads`/`writes`: đó là hàm tra ngược DUY NHẤT, và
     * nó đã tính sẵn ca *"một mã thuộc hai nhóm"* (sẽ tới với `volt_12v`). Thứ tự ra theo thứ tự khai của
     * [CapabilityGroups.ALL] — không theo thứ tự gặp trong danh sách ô, để hai lĩnh vực không nói cùng một cặp nhóm
     * theo hai thứ tự khác nhau.
     */
    fun groupHint(picks: List<CapabilityPick>): String {
        val hit = singlesOf(picks).flatMapTo(mutableSetOf()) { p ->
            CapabilityGroups.groupsContaining(p.id).map { it.id }
        }
        if (hit.isEmpty()) return ""
        // ⚠ Ngăn cách bằng DẤU PHẨY, không bằng " · ": [ĐO] nhãn nhóm "An toàn · ADAS" đã chứa dấu giữa, nên nối bằng
        // " · " ra câu "Đã có trong nhóm: An toàn · ADAS · Người ngồi" — đọc thành BA nhóm thay vì hai. Không nhãn
        // nhóm nào chứa dấu phẩy (có test canh), nên dấu phẩy phân biệt được.
        return HINT_PREFIX + CapabilityGroups.ALL.filter { it.id in hit }.joinToString(", ") { it.displayLabel }
    }
}
