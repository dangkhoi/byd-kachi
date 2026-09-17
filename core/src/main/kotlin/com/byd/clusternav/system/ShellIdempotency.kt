package com.byd.clusternav.system

/**
 * Lệnh shell nào được phép **gửi lại** khi lượt đầu hỏng — luật thuần (:core), một chỗ, để `ShellTransport.exec`
 * hỏi trước khi thử lại.
 *
 * ## Vì sao tồn tại — [ĐO xe 2026-09-17]
 * `ShellTransport.exec` thử lại **một lần** khi lượt đầu ném (đóng + nối lại + gửi lại) — đúng cho `am stack list`
 * hay `wm size` (đọc, hoặc ghi cùng giá trị), và **sai** cho `input -d <id> tap x y`: dưới tải xe (load 18, đầu
 * máy đang chạy Maps + YouTube + Vietmap) một lượt `input` có thể mất hơn `SOCKET_TIMEOUT` để về, trong khi cú
 * chạm **đã được bơm** vào app. Lượt thử lại bơm cú chạm THỨ HAI ⇒ owner thấy *"nhấn play/pause nó nhận 2 tap:
 * play → pause → play"*. Một lệnh có tác dụng phụ không lặp lại được thì **không bao giờ** được gửi lại — mất một
 * cú chạm còn hơn nhân đôi nó.
 *
 * Luật hẹp cố ý: chỉ họ `input …` (chạm · vuốt · phím). Mọi lệnh khác giữ nguyên hành vi tự chữa của B1.
 */
object ShellIdempotency {
    /** `true` ⇒ được thử lại khi hỏng; `false` ⇒ gửi đúng MỘT lần, hỏng thì thôi. */
    fun retryable(cmd: String): Boolean {
        val c = cmd.trimStart()
        return !(c.startsWith("input ") || c.startsWith("input\t") || c == "input")
    }
}
