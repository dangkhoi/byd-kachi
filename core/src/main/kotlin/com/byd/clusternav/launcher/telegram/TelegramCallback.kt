package com.byd.clusternav.launcher.telegram

/**
 * `callback_data` của nút inline — spec `kachi-telegram-buttons.html` R-nf1/R-nf2 (QD-15/QD-16). Thuần Kotlin
 * (`:core`, cấm `android.*`) ⇒ kiểm off-car.
 *
 * ## Khuôn: `v<nonce>:<domain>:<action>:<id>`
 * - **nonce** = số phiên bản bảng (QD-16). Nút của bảng CŨ mang nonce cũ ⇒ `:app` trả toast "bảng cũ", KHÔNG thi hành.
 * - **domain** = nhóm (3.1.21 chỉ còn `nav`).
 * - **action** = việc trong nhóm (vd `temp` · `fan` · `auto` · `play` · `open`).
 * - **id** = tham số (vd `24` cho 24°, chỉ số nơi `3`, `on`/`off`). CHỈ chữ+số+vài ký tự an toàn — KHÔNG BAO GIỜ
 *   chứa chữ người dùng nhập (tên địa điểm tiếng Việt UTF-8 làm vỡ giới hạn 64 byte của Telegram — QD-15).
 *
 * ## Vì sao ≤64 byte là bất biến cứng
 * Telegram cắt/từ chối `callback_data` > 64 byte. [encode] tự kiểm và [parse] trả `null` cho chuỗi quá dài ⇒ nút
 * hỏng được phát hiện ở tầng thuần (test), không phải lúc chạy trên xe.
 */
data class TelegramCallback(
    val nonce: Int,
    val domain: String,
    val action: String,
    val id: String = "",
) {
    /** Mã hoá thành `callback_data`. Ném nếu vượt 64 byte (lỗi lập trình — bảng dựng sai, bắt ở test). */
    fun encode(): String {
        val s = "v$nonce:$domain:$action:$id"
        require(s.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "callback_data vượt $MAX_BYTES byte: $s (${s.toByteArray(Charsets.UTF_8).size})"
        }
        return s
    }

    companion object {
        /** Giới hạn cứng của Telegram Bot API. */
        const val MAX_BYTES = 64

        /** Domain hợp lệ — khai một chỗ để `:app` khớp khi phân giải. */
        val DOMAINS = setOf("nav")   // 3.1.21: chỉ bảng nơi đã lưu

        /**
         * Phân giải `callback_data`. Trả `null` khi: không bắt đầu `v`, nonce không phải số, thiếu trường, domain lạ,
         * hoặc > 64 byte. `null` ⇒ `:app` bỏ qua an toàn (không thi hành lệnh mơ hồ).
         */
        fun parse(raw: String): TelegramCallback? {
            if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
            if (!raw.startsWith("v")) return null
            // Tách 4 phần: "v<nonce>", domain, action, id (id có thể rỗng; split giới hạn 4 để id KHÔNG nuốt dấu ':').
            val parts = raw.split(':', limit = 4)
            if (parts.size < 3) return null
            val nonce = parts[0].removePrefix("v").toIntOrNull() ?: return null
            val domain = parts[1]
            if (domain !in DOMAINS) return null
            val action = parts[2]
            if (action.isEmpty()) return null
            val id = parts.getOrNull(3) ?: ""
            return TelegramCallback(nonce, domain, action, id)
        }
    }
}
