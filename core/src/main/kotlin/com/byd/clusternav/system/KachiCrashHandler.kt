package com.byd.clusternav.system

/**
 * ═══ HANDLER NGOẠI LỆ CHƯA BẮT — ghi vết rồi **chuyển tiếp**, không nuốt, không tự khởi động lại ══════════════
 *
 * Hardening 2026-09-25 · audit F23 [P2]: app có ≥25 `Thread { }` trần (update-download, bridge-*, pm25-*, seat-*…)
 * và **không** có `Thread.setDefaultUncaughtExceptionHandler`. Một ngoại lệ bất kỳ trên luồng phụ = launcher
 * (HOME) chết = mọi ô VD + a11y binding mất — và vì không ai chụp, dòng cuối trước khi chết cũng không có.
 *
 * Hợp đồng (chủ ý hẹp):
 *  1. [record] chạy TRƯỚC (best-effort, tự bọc — một lỗi khi ghi vết không được che ngoại lệ gốc);
 *  2. **luôn** chuyển tiếp cho [prev] (handler mặc định của Android = in stack + kill process). Không nuốt: nuốt là
 *     để tiến trình sống với state hỏng; không tự restart: đó là quyết định của owner (CLAUDE.md §4 — "hoàn tác
 *     kiểu gì" phải có câu trả lời trước khi đổi hành vi hệ thống).
 *
 * THUẦN (không Android) ⇒ `KachiCrashHandlerTest` khoá off-device: chuyển tiếp đúng một lần, kể cả khi [record] ném.
 */
class KachiCrashHandler(
    private val prev: Thread.UncaughtExceptionHandler?,
    private val record: (Thread, Throwable) -> Unit,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching { record(t, e) }
        prev?.uncaughtException(t, e)
    }

    companion object {
        /**
         * Cài làm handler mặc định của tiến trình, bọc handler hiện có. Idempotent: gọi lần hai (Application
         * `onCreate` không chạy hai lần, nhưng test thì có) không bọc chồng — nếu không mỗi lần là một tầng ghi vết.
         */
        fun install(record: (Thread, Throwable) -> Unit): KachiCrashHandler {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is KachiCrashHandler) return current
            return KachiCrashHandler(current, record).also { Thread.setDefaultUncaughtExceptionHandler(it) }
        }
    }
}
