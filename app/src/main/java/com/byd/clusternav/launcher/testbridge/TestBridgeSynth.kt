package com.byd.clusternav.launcher.testbridge

import android.os.Handler
import android.os.Looper

/**
 * Lệnh `camera_synth --es name on|off` — bơm **ảnh fisheye TỔNG HỢP** vào đường camera thay cho HAL (R8-B · 2.74).
 *
 * Vì sao lệnh này tồn tại: KDoc [TestBridgeCommands.CAMERA_SYNTH] (ngắn gọn — máy ảo không có `AVMCamera`, nên không
 * có nó thì cả tầng nắn GL lên xe mà chưa từng vẽ một pixel; CLAUDE.md §14 · §8).
 *
 * ## Vắng/lạ `--es name` ⇒ **BẬT**
 * Cùng luật `camera --es name`: tầng thi hành chỉ hỏi *"có phải off không"*. Một lệnh đo không nên cần đối số để bật
 * đúng thứ nó sinh ra để bật, và một chuỗi gõ sai (`--es name ON`, `--es name 1`) phải làm **việc người gõ muốn** chứ
 * không rơi vào nhánh tắt im lặng — tắt im lặng thì lượt đo sau đó đo một overlay trống và báo *"nắn không chạy"*.
 *
 * Trả `applied` = cờ **đọc lại** từ controller sau lượt đặt (cùng luật `read_back` của `prefs_set`): một lượt đặt
 * không có tác dụng (chưa có màn chính, controller chưa dựng được) phải thấy ngay, không phải ở ca thất bại sau đó.
 */
internal object TestBridgeSynth {

    /**
     * Chuỗi TẮT được nhận — **ASCII không dấu**, kể cả biến thể tiếng Việt (`tat`).
     *
     * Không nhận `"tắt"` có dấu: đối số này đi qua `am broadcast` trong một chuỗi shell, nơi dấu tiếng Việt là một
     * nguồn lỗi mã hoá thật (ADB/`sh` của ROM), và cùng luật *"mã lệnh/mã lỗi ASCII, không dịch"* mà cả cầu kiểm
     * thử đang theo. `tat` phủ đúng thứ owner gõ được từ bàn phím không dấu.
     */
    private val OFF = setOf("off", "0", "false", "no", "tat")

    fun run(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val a = cmd.arg.trim().lowercase()
        val on = a !in OFF
        // Đi qua main thread: `setSynth` đóng overlay đang treo (op `WindowManager` + cây view) — cùng khuôn
        // [TestBridgeCamera]. `goAsync` của receiver giữ tiến trình sống tới lúc `reply` chốt.
        Handler(Looper.getMainLooper()).post {
            val applied = runCatching { hooks.cameraSynth(on) }.getOrDefault(false)
            reply.ok(
                "arg" to a,
                "asked" to on,
                "applied" to applied,
                // Câu dặn ngay trong lời đáp: người gõ lệnh này trên máy ảo hầu như luôn cần đúng ba bước sau đó.
                "next" to "prefs_set camera_render GL + camera_signal_enabled 1, then `camera --es name left`," +
                    " then `camera_frame`",
            )
        }
    }
}
