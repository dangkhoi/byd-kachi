package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KHOÁ [ĐO xe 2026-09-17]: lệnh `input …` (chạm/vuốt) KHÔNG được gửi lại khi lượt đầu hỏng — lượt thử lại của
 * `ShellTransport.exec` là nguồn của cú chạm ĐÔI (*"play → pause → play"*) khi `input tap` chậm hơn socket
 * timeout dưới tải xe nhưng đã bơm xong.
 */
class ShellIdempotencyTest {

    @Test
    fun `lenh input khong duoc gui lai`() {
        listOf(
            "input -d 2 tap 640 360",
            "input -d 2 swipe 100 800 100 300 400",
            "  input keyevent 4",
            "input\ttext hello",
            "input",
        ).forEach { assertFalse(ShellIdempotency.retryable(it), "không được thử lại: $it") }
    }

    @Test
    fun `lenh doc va lenh cua so van duoc tu chua nhu B1`() {
        listOf(
            "am stack list",
            "wm size",
            "dumpsys display",
            "CLASSPATH=/x/base.apk app_process / a.B kachi_input </dev/null >/dev/null 2>&1 &",
            "settings get secure tts_default_synth",
            "inputmethod list",   // tiền tố khác `input ` — không phải họ bơm chạm
        ).forEach { assertTrue(ShellIdempotency.retryable(it), "vẫn được thử lại: $it") }
    }
}
