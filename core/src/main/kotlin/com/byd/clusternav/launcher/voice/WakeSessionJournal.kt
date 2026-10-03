package com.byd.clusternav.launcher.voice

/**
 * ═══ FIX286 · VK6 — ĐỊNH DẠNG một dòng nhật ký mỗi phiên nghe của `:wake` (thuần, `:core`) ════════════════════════
 *
 * ## Vì sao có (CLAUDE.md §11)
 * [ĐO mã 02/10] `usage-*.log` chỉ chụp logcat của pid tiến trình CHÍNH (`KachiLog` lọc `--pid`), mà mọi phiên của phím
 * vô-lăng chạy ở `:wake` ⇒ báo cáo *"phím kẹt Getting ready"* của anh em không để lại một dòng nào cho biết: phiên có
 * nạp mô hình không, nạp bao lâu, bao lâu tới lúc mở micro, kết cục ra sao. Bản vá B′ (2.86) ship trước một phép đo
 * phím trên xe anh em (§14 — owner duyệt OTA thử) ⇒ lượt thử ấy PHẢI tự sinh bằng chứng. Một dòng mỗi phiên vào tệp
 * vòng `filesDir/diag/wake-sessions.log` (khoá liên tiến trình — `DiagRingFile`) + logcat, đọc qua cầu `wakelog`.
 *
 * ## Dạng dòng (khoá cố định — grep được)
 * `wake-session entry=<phim|mic|cau-goi|?> mode=<OFF|HOLD|WAKE> warm=<0|1> loading=<0|1> load=<ms|-> ready=<ms|->
 * out=<nghe|huy|loi|dung-xuong>`
 *  • `warm` — mô hình đã nằm sẵn trong RAM lúc nhận lệnh; `loading` — đang có lượt nạp khác chạy lúc nhận lệnh;
 *  • `load` — ms của lượt nạp xảy ra TRONG phiên này (`-` = không nạp: dùng bản có sẵn / chờ lượt nạp có sẵn xong);
 *  • `ready` — ms từ lúc `:wake` nhận lệnh tới lúc micro mở (`-` = không tới: huỷ/lỗi trước đó).
 * Tiền tố giờ tường + `pid=` do `:app` thêm (cùng khuôn `CtlJournalStore`). Không có dữ liệu người dùng (không chữ
 * nghe được, không tên app) — chỉ mốc, số và mã.
 */
object WakeSessionJournal {

    const val TAG_LINE = "wake-session"

    /** Trần dòng của tệp vòng — cùng trần với hai nhật ký bền kia (cầu đọc kẹp theo số này). */
    const val MAX_LINES = 200

    private const val NONE_MARK = "-"

    /** Lối vào của phiên — mã ASCII không dấu (đi qua extra của intent `LISTEN_NOW`). */
    enum class Entry(val code: String) {
        /** Phím vô-lăng gán Kachi nghe (`AssistantLauncher`). */
        KEY("phim"),

        /** Nút mic màn chính giao cho `:wake` (`VoiceEntry.tryWake`). */
        MIC("mic"),

        /** Câu gọi "Hey Kachi" (`VoiceWakeService.fireWake`). */
        WAKE_WORD("cau-goi"),

        /** Extra vắng/lạ — intent từ đường cũ hoặc ai đó khác trong gói. */
        UNKNOWN("?"),
        ;

        companion object {
            fun of(code: String?): Entry = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    /** Kết cục của phiên. */
    enum class Outcome(val code: String) {
        /** Micro đã mở (người lái được nghe) — dù sau đó nói gì. */
        HEARD("nghe"),

        /** Người lái huỷ (chạm ra ngoài) hoặc phiên bị cắt bởi lần bấm mới. */
        CANCELLED("huy"),

        /** Không tới được micro mà cũng không ai huỷ: thiếu quyền / chưa tải mô hình / nạp hỏng. */
        FAILED("loi"),

        /** Nhịp đứng xuống BG-20 cắt một phiên kẹt quá trần. */
        STOOD_DOWN("dung-xuong"),
    }

    /** Kết cục khi phiên tự đóng: huỷ thắng (người lái đã bỏ đi); không huỷ mà chưa tới micro ⇒ lỗi. */
    fun outcomeOf(cancelled: Boolean, reachedMic: Boolean): Outcome = when {
        cancelled -> Outcome.CANCELLED
        reachedMic -> Outcome.HEARD
        else -> Outcome.FAILED
    }

    private fun bit(b: Boolean) = if (b) "1" else "0"

    fun line(
        entry: Entry,
        mode: VoiceWakeMode,
        warm: Boolean,
        loading: Boolean,
        loadMs: Long?,
        readyMs: Long?,
        outcome: Outcome,
    ): String =
        "$TAG_LINE entry=${entry.code} mode=${mode.name} warm=${bit(warm)} loading=${bit(loading)} " +
            "load=${loadMs ?: NONE_MARK} ready=${readyMs ?: NONE_MARK} out=${outcome.code}"
}
