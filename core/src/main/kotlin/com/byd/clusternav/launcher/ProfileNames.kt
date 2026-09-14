package com.byd.clusternav.launcher

/**
 * ═══ [SOÁT P3-4] TÊN HỒ SƠ: **KHOÁ giữ nguyên, NHÃN thì dịch** ═════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm được off-car.
 *
 * ## Vấn đề đo được
 * [ĐO] 2026-09-12: quét **179 nút chữ** trên **7 trang** của màn Cài đặt khi đang đặt **tiếng Anh** ⇒ còn đúng **hai**
 * chuỗi tiếng Việt: `"Tiếng Việt"` (ĐÚNG — tên một ngôn ngữ thì viết bằng chính ngôn ngữ đó, đúng quy ước mọi bộ chọn
 * ngôn ngữ) và **`"Mặc định"`** — tên hồ sơ tài xế mặc định. Người dùng English thấy một mẩu tiếng Việt giữa màn.
 *
 * ## Vì sao KHÔNG dịch [HomeUiState.DEFAULT_PROFILE], dù đó là chỗ dễ nghĩ nhất
 * Hằng đó là **TIỀN TỐ KHOÁ LƯU BỀN**: `WorkspacePrefs` ghi mọi cấu hình theo hồ sơ dưới dạng `"<tên hồ sơ>__<hậu tố>"`
 * (`Mặc định__preset`, `Mặc định__slot_0`, `Mặc định__scenes`…). Dịch nó thành `"Default"` làm **mọi khoá cũ thành mồ
 * côi** ⇒ người dùng mở launcher lên thấy bố cục trắng và tưởng mất hết cấu hình, **không có gì báo lỗi**. Lý do này
 * đã ghi ở KDoc [HomeUiState.DEFAULT_PROFILE] từ U5; tệp này là **phần thi hành** của nó.
 *
 * ⇒ Tách hai khái niệm cho rõ, vì chúng *trông* y như nhau:
 *  - **khoá** = [HomeUiState.DEFAULT_PROFILE], không bao giờ đổi, không bao giờ dịch;
 *  - **nhãn** = [display], chỉ dùng ở chỗ VẼ, dịch theo ngôn ngữ đang chọn.
 *
 * ## Vì sao ở `:core` mà không ở `:app` (nơi có tài nguyên chuỗi)
 * Để nó nằm **cạnh chính cái khoá** nó đang che — người sửa sau đọc hằng là thấy ngay lớp trình bày, thay vì phải đoán
 * rằng ở đâu đó trong `:app` có một chỗ dịch. Đây cũng là cách `:core` vốn làm với mọi nhãn khác ([Strings.t]).
 *
 * ⚠ Chỉ áp cho **đúng một tên**: tên do người dùng tự đặt là **dữ liệu của họ**, dịch nó là làm sai (cùng luật đã ghi
 * ở [Scene.name]).
 */
object ProfileNames {

    /**
     * Nhãn để HIỂN THỊ của một tên hồ sơ. Chỉ [HomeUiState.DEFAULT_PROFILE] được dịch; mọi tên khác trả về nguyên văn.
     *
     * ⚠ Hàm này **chỉ được dùng ở chỗ vẽ**. Truyền kết quả của nó trở lại `switchProfile`/`deleteProfile`/`key()` sẽ
     * làm mất sạch cấu hình của người dùng ở máy tiếng Anh — có bài canh đòi mọi đường ghi/tra vẫn dùng tên GỐC.
     */
    fun display(name: String): String =
        if (name == HomeUiState.DEFAULT_PROFILE) Strings.t(HomeUiState.DEFAULT_PROFILE, "Default") else name

    /** Chữ đầu cho avatar thanh trên — theo **nhãn** (nên máy tiếng Anh hiện `D`, không phải `M`). */
    fun initial(name: String): String = display(name).take(1).uppercase()

    /**
     * Tóm tắt bố cục của MỘT hồ sơ — owner 2026-09-14: *"chưa thấy hồ sơ nó gắn với bố cục chỗ nào?"*.
     *
     * Mối gắn là thật ([ĐO] mọi khoá bố cục/ô/thanh nút/cảnh lưu dưới tiền tố `<tên hồ sơ>__`) nhưng trước đây
     * KHÔNG câu nào trong giao diện nói ra. Câu này đặt cạnh tên hồ sơ để người dùng thấy ngay hồ sơ đó đang giữ
     * bố cục gì. `preset == null` = hồ sơ dùng bố cục tự vẽ.
     */
    fun summary(
        preset: LayoutPreset?,
        filledSlots: Int,
        theme: ThemeMode? = null,
        autostart: Boolean? = null,
    ): String {
        val layout = preset?.label ?: Strings.t("Tự vẽ", "Custom")
        // ⚠ Số ít/số nhiều của tiếng Anh phải làm BẰNG TAY ở `:core` (không có `Context` nên không có
        // `getQuantityString`) — cùng lỗi mà `finding #18` đã bắt một lần ở `layoutSummary`: bản một-chuỗi in
        // *"1 slots filled"*. Tiếng Việt không chia số nên một câu là đủ.
        val en = if (filledSlots == 1) "1 slot filled" else "$filledSlots slots filled"
        val parts = mutableListOf(Strings.t("$layout · $filledSlots ô có nội dung", "$layout · $en"))
        // S4 · R8 — hồ sơ nay giữ CẢ chủ đề và cờ tự mở, nên câu tóm tắt phải nói ra: owner 2026-09-14 hỏi đúng câu
        // *"chưa thấy hồ sơ nó gắn với bố cục chỗ nào?"* cho phần bố cục, và từ S4 phần "giữ những gì" rộng hơn hẳn.
        // ⚠ Cả hai tham số là TUỲ CHỌN và mặc định `null` = **không biết** (chỗ gọi chưa đọc được giá trị của hồ sơ
        // đó) — khác hẳn "biết và bằng mặc định". Bịa ra một giá trị ở đây thì thẻ hồ sơ nói sai một cách tự tin.
        theme?.let { parts += it.label() }
        if (autostart == true) parts += Strings.t("Tự mở", "Auto-start")
        return parts.joinToString(" · ")
    }
}
