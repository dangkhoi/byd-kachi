package com.byd.clusternav.launcher.telegram

/**
 * Cổng TIN CŨ cho lệnh ĐIỀU KHIỂN XE từ Telegram — senior review 26/10 P0-1. Thuần Kotlin (`:core`) ⇒ test bằng giá trị.
 *
 * ## Mối nguy
 * Telegram giữ tin chưa nhận tới 24 giờ, mà bot chỉ nhận khi Kachi đang hỏi (không có server). Xe tắt ⇒ chủ xe bấm
 * "🧳 Trunk open" ⇒ không ai trả lời ⇒ họ tưởng lệnh trượt. Lần nổ máy sau Kachi nhận đúng tin đó, xe đang ở P ⇒ cổng
 * tốc độ cho qua ⇒ cốp bung lên lúc không ai chờ.
 *
 * ## Luật
 * - Chỉ áp cho lệnh GHI XE ([isCarControl]). Đặt điểm đến / nhạc / menu được phép tồn đọng — đó chính là usecase
 *   "gửi `/go` từ nhà, lên xe mới dẫn" (điểm đến có TTL 24h + chờ GPS riêng).
 * - Tin trong **lượt đọc tồn đọng** (lượt đầu sau mỗi khoảng Kachi không hỏi: vòng mới bật, màn vừa sáng lại, mạng vừa
 *   hồi) ⇒ lệnh xe BỎ. Không phụ thuộc đồng hồ xe (đồng hồ đầu xe có thể sai lúc mới nổ máy).
 * - Lệnh xe gõ chữ có `date` ⇒ thêm điều kiện tuổi: `now − date` trong [-[CLOCK_SKEW_SEC], [MAX_AGE_SEC]]. Nút bấm
 *   (callback) không có giờ bấm — Telegram không gửi — nên chỉ cổng tồn đọng chặn được nút.
 */
object TelegramFreshness {

    /** Tuổi tối đa của một lệnh xe gõ chữ (giây). */
    const val MAX_AGE_SEC = 120L

    /** Cho phép đồng hồ xe CHẬM hơn Telegram tối đa chừng này (giây) — tin "từ tương lai" vẫn coi là mới. */
    const val CLOCK_SKEW_SEC = 300L

    /** Domain nút menu có ghi xe. `nav`/`music`/`menu`/`page` không ghi xe. */
    val CAR_CONTROL_DOMAINS: Set<String> = setOf("ac", "seat", "misc")

    /** Update này có ghi xuống xe không: lệnh gõ `/x` điều khiển, hoặc nút thuộc [CAR_CONTROL_DOMAINS]. */
    fun isCarControl(command: TelegramCommand, menuDomain: String?): Boolean =
        command is TelegramCommand.Control || (menuDomain != null && menuDomain in CAR_CONTROL_DOMAINS)

    /**
     * `true` = được thi hành. Lệnh không ghi xe luôn `true`. Lệnh ghi xe: `false` nếu đến từ lượt tồn đọng, hoặc có
     * [dateSec] mà tuổi nằm ngoài khoảng cho phép.
     */
    fun allow(isCarControl: Boolean, fromBacklog: Boolean, dateSec: Long?, nowSec: Long): Boolean {
        if (!isCarControl) return true
        if (fromBacklog) return false
        if (dateSec == null) return true
        val age = nowSec - dateSec
        return age in -CLOCK_SKEW_SEC..MAX_AGE_SEC
    }
}
