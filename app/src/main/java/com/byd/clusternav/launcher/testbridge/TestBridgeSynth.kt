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

    /** Tiền tố chọn một PNG THẬT thay cho ảnh sinh: `camera_synth --es name file:strip-L-1.png`. */
    private const val FILE_PREFIX = "file:"

    /**
     * Tên tệp đã **lọc** từ đối số của người gõ, `""` khi không dùng được (CLAUDE.md §4.1: đầu vào → đường tệp).
     *
     * Chỉ giữ **phần tên** (`File(raw).name` bỏ mọi thành phần thư mục ⇒ `../../secret` thành `secret`), rồi từ chối
     * tên rỗng, tên còn `..`, và tên bắt đầu bằng dấu chấm. Thư mục gốc do tầng gọi quyết
     * ([com.byd.clusternav.launcher.camera.CameraSignalController.setSynth] ghép với `getExternalFilesDir`), nên
     * một tên đã lọc không thể trỏ ra ngoài hộp cát của app.
     */
    internal fun safeName(raw: String): String {
        val name = java.io.File(raw.trim()).name
        if (name.isEmpty() || name == ".." || name.startsWith(".")) return ""
        return name
    }

    fun run(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        // Tên tệp KHÔNG được hạ chữ thường (`strip-L-1.png` ≠ `strip-l-1.png` trên `ext4`) ⇒ tách trước khi lowercase.
        val rawArg = cmd.arg.trim()
        val file = if (rawArg.startsWith(FILE_PREFIX, ignoreCase = true)) {
            safeName(rawArg.substring(FILE_PREFIX.length))
        } else {
            ""
        }
        val a = rawArg.lowercase()
        val on = a !in OFF
        // Đi qua main thread: `setSynth` đóng overlay đang treo (op `WindowManager` + cây view) — cùng khuôn
        // [TestBridgeCamera]. `goAsync` của receiver giữ tiến trình sống tới lúc `reply` chốt.
        Handler(Looper.getMainLooper()).post {
            val applied = runCatching { hooks.cameraSynth(on, file) }.getOrDefault(false)
            reply.ok(
                "arg" to a,
                "file" to file,
                "asked" to on,
                "applied" to applied,
                // Câu dặn ngay trong lời đáp: người gõ lệnh này trên máy ảo hầu như luôn cần đúng ba bước sau đó.
                "next" to "prefs_set camera_render GL + camera_signal_enabled 1, then `camera --es name left`," +
                    " then `camera_frame`",
            )
        }
    }
}
