package com.byd.clusternav.system

/**
 * Dòng log cho một lệnh shell hỏng cả hai lượt ở [ShellTransport.run] — **có tiết chế theo lớp lỗi**.
 *
 * ## Vì sao tồn tại (hardening 2026-09-25 · audit F1 [P1])
 * `run()` giữ hợp đồng cũ *"hỏng ⇒ `""`"* (byte-for-byte `DadbShell.run`), nhưng trước đây nó nuốt **im**: một
 * adbd wedge biến mọi lệnh cửa sổ/cast thành chuỗi rỗng, consumer đọc `""` như *"không có dữ liệu"* (`pidof` rỗng
 * ⇒ "app chưa lên" ⇒ `am start` lần hai) mà logcat trắng — không có gì để chốt (CLAUDE.md §2).
 *
 * ## Vì sao KHÔNG log mọi lần
 * Khi dadb chết hẳn, mỗi lệnh hỏng là hai lượt chặn + một dòng W; nhịp poll/reflow có thể đẩy hàng trăm dòng
 * một phút vào tệp usage trên thẻ (H3 xả ngay dòng W). Tiết chế **theo lớp lỗi**: cùng một lớp (vd
 * `SocketTimeoutException`) chỉ ghi lại sau [THROTTLE_MS]; lớp khác vẫn ghi ngay — vì lớp khác là bệnh khác.
 * KHÔNG thêm bộ đếm `KachiPerf` (KDoc ở đó cấm bộ đếm suy ra được từ cái khác — số lệnh hỏng suy được từ log).
 *
 * THUẦN (không Android) ⇒ khoá off-device bởi `ShellRunFailureLogTest`.
 */
object ShellRunFailureLog {

    /** Cửa sổ tiết chế cho MỘT lớp lỗi. 60 s: đủ để mỗi phút vẫn có một dòng làm mốc thời gian khi dadb chết lâu. */
    const val THROTTLE_MS = 60_000L

    /** Lệnh trong dòng log cắt ở đây — đủ nhận diện (`am start -n …`), không dán cả một `dumpsys` vào log. */
    const val CMD_MAX = 80

    private val lastByClass = HashMap<String, Long>()

    /**
     * Dòng W cần ghi cho lệnh [cmd] hỏng vì [error] tại [nowMs], hoặc `null` nếu cùng lớp lỗi vừa được ghi trong
     * [THROTTLE_MS] (chỗ gọi thì thôi, không ghi).
     */
    @Synchronized
    fun line(cmd: String, error: Throwable, nowMs: Long): String? {
        val cls = error.javaClass.simpleName.ifBlank { error.javaClass.name }
        val last = lastByClass[cls]
        if (last != null && nowMs - last in 0 until THROTTLE_MS) return null
        lastByClass[cls] = nowMs
        return "run FAILED ($cls: ${error.message}) cmd=${cmd.take(CMD_MAX)}"
    }

    /** Test-only: quên mọi mốc (state là process-global). */
    @Synchronized
    internal fun resetForTest() = lastByClass.clear()
}
