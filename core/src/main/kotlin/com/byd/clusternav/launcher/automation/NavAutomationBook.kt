package com.byd.clusternav.launcher.automation

/**
 * ═══ SỔ LUẬT DẪN ĐƯỜNG TỰ ĐỘNG — danh sách + mã hoá nhiều dòng ════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.1 (*"hỗ trợ >1 rule — vd sáng đến cty, chiều về nhà"*) · §Design.
 * Thuần Kotlin (`:core`, cấm `android.*`). Một luật một dòng ([ScheduledNavRules.encodeLine]); sổ = các dòng nối
 * bằng `\n`, cùng lệ `SavedPlaces.encode`.
 *
 * ## ⚠ Bất biến quan trọng nhất của tệp này: `id` KHÔNG ĐƯỢC TRÙNG
 * [ScheduledNavRule.id] là khoá của **sổ đã-dẫn** (`lastFiredDay[id]` — xem [ScheduledNavPolicy]). Hai luật trùng
 * `id` ⇒ luật nổ trước đóng dấu *"hôm nay đã dẫn"* cho **cả hai**, nên luật thứ hai (vd *"chiều về nhà"*) không
 * bao giờ chạy — và nó hỏng **im lặng**: cấu hình vẫn hiện đủ hai dòng trong Cài đặt, test đơn lẻ từng luật vẫn
 * xanh. Vì vậy [decode] **bỏ** dòng trùng `id` và [upsert] coi `id` là khoá ghi-đè, còn [newId] không bao giờ cấp
 * lại một `id` đang có.
 */
object NavAutomationBook {

    /**
     * Trần số luật.
     *
     * 8 đủ xa cho *"sáng đi làm · chiều về · thứ Bảy đưa con học · Chủ Nhật về ngoại"* và chặn một chuỗi prefs
     * phình vô hạn. Vượt trần thì [upsert] **từ chối thêm mới** (giữ nguyên sổ) chứ không cắt bớt luật cũ — cắt
     * một luật người dùng không yêu cầu xoá là mất dữ liệu; chỗ gọi so độ dài trước/sau để nói ra (cùng lệ
     * `SavedPlaces.MAX`).
     */
    const val MAX = 8

    /** Tiền tố `id` do máy cấp ([newId]). Chữ + số ⇒ không bao giờ chứa ký tự ngăn. */
    private const val ID_PREFIX = "r"

    fun encode(rules: List<ScheduledNavRule>): String =
        rules.joinToString("\n") { ScheduledNavRules.encodeLine(it) }

    /**
     * Đọc sổ từ chuỗi đã lưu. Dòng hỏng ⇒ **bỏ đúng dòng đó**, không ném và không bỏ cả sổ.
     *
     * Ba ca hỏng đều có thật: sửa tay tệp prefs · bản cũ/mới ghi khác số trường · dòng trùng `id` (xem KDoc lớp).
     * Giữ **dòng đầu** khi trùng `id`: nó là thứ người dùng tạo trước, và một phép chọn tuỳ tiện ở đây sẽ làm
     * chuỗi trên đĩa đọc ra khác nhau giữa các lần.
     */
    fun decode(raw: String?): List<ScheduledNavRule> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = mutableSetOf<String>()
        return raw.split('\n')
            .mapNotNull { line -> if (line.isBlank()) null else ScheduledNavRules.decodeLine(line) }
            .filter { seen.add(it.id) }
            .take(MAX)
    }

    /** Luật mang `id` này, hoặc `null`. */
    fun find(rules: List<ScheduledNavRule>, id: String): ScheduledNavRule? = rules.firstOrNull { it.id == id }

    /**
     * Thêm mới, hoặc **ghi đè** luật cùng `id` (đó là đường SỬA).
     *
     * Giữ **nguyên vị trí cũ** khi ghi đè: sửa giờ của luật buổi sáng không làm nó nhảy xuống cuối danh sách. Một
     * hàm `update` riêng là đường ghi thứ hai cho cùng một bảng, tức chỗ để hai đường lệch nhau (bài học
     * `unitPrefs` ×4 bản) ⇒ cố ý chỉ có một (cùng lệ `SavedPlaces.upsert`).
     *
     * Vượt [MAX] mà là luật MỚI ⇒ trả về sổ **không đổi**.
     */
    fun upsert(rules: List<ScheduledNavRule>, rule: ScheduledNavRule): List<ScheduledNavRule> {
        val at = rules.indexOfFirst { it.id == rule.id }
        if (at >= 0) return rules.toMutableList().also { it[at] = rule }
        if (rules.size >= MAX) return rules
        return rules + rule
    }

    fun remove(rules: List<ScheduledNavRule>, id: String): List<ScheduledNavRule> = rules.filterNot { it.id == id }

    /** Bật/tắt một luật tại chỗ; `id` lạ ⇒ sổ không đổi (dữ liệu có thể đã bị xoá ở bề mặt khác). */
    fun setEnabled(rules: List<ScheduledNavRule>, id: String, enabled: Boolean): List<ScheduledNavRule> =
        rules.map { if (it.id == id) it.copy(enabled = enabled) else it }

    /**
     * `id` mới chưa ai dùng — `r1`, `r2`, … Tăng dần, **không tái sử dụng** số đã có trong sổ.
     *
     * ## Vì sao không lấy `"r" + (size + 1)` cho gọn
     * Xoá `r1` rồi thêm luật mới: `size + 1` = `r2` — trùng đúng luật đang còn. Sổ đã-dẫn của `r2` cũ liền đóng
     * dấu cho luật mới ⇒ luật vừa tạo **không chạy hôm nay** mà không một lời nào. Nên phép cấp `id` phải hỏi
     * chính sổ, và nó **thuần** (không đồng hồ, không ngẫu nhiên) để bài canh chạm được vào đúng ca xoá-rồi-thêm.
     */
    fun newId(rules: List<ScheduledNavRule>): String {
        val used = rules.map { it.id }.toSet()
        var n = 1
        while ("$ID_PREFIX$n" in used) n++
        return "$ID_PREFIX$n"
    }
}
