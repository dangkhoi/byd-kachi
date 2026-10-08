package com.byd.clusternav.core

/**
 * 2.98 · R6-F — trần TỪNG TỆP cho tệp chẩn đoán ghi nối đuôi (`nav_notif_log_*.csv` · `nav_notif_raw_*.csv`).
 *
 * [ĐO mã] hai writer đó (`:app` `NavNotifLog` / `NavNotifRawLog`, chỉ chạy khi `NavLog.verbose`) mở MỘT tệp mỗi tiến
 * trình rồi ghi mãi — không trần từng tệp; chỉ có trần TỔNG 150 MiB của `DiagStorageCap` chặn ở đuôi. Cùng khuôn với
 * `usage-*.log` (`KachiLog.USAGE_CAP_BYTES` = 8 MiB/tệp): mỗi tệp ≤ [PER_FILE_CAP_BYTES], quá thì XOAY sang tệp mới (dữ
 * liệu mới quý hơn, và tệp cũ thành ứng viên cho `DiagStorageCap` xoá trước — nó dọn cũ trước).
 *
 * Cách dùng (một luồng ghi):
 * ```
 * if (writer == null || budget.mustRotateBefore(n)) { mở tệp mới + ghi tiêu đề; budget.startFile(header) }
 * ghi dòng; budget.record(n)
 * ```
 * THUẦN, không khoá: chỉ dùng trên luồng đơn của writer. `FileByteBudgetTest` khoá off-device.
 */
class FileByteBudget(private val capBytes: Long = PER_FILE_CAP_BYTES) {

    init { require(capBytes > 0) { "capBytes phải > 0" } }

    /** Số byte đã ghi vào tệp hiện tại (kể cả tiêu đề). */
    var written: Long = 0L
        private set

    private var hasBody = false

    /**
     * Sắp ghi [bytes] byte: `true` = phải xoay sang tệp mới TRƯỚC (tệp hiện tại đã có ít nhất một dòng và ghi thêm sẽ
     * vượt trần). Tệp chỉ có tiêu đề thì không bao giờ xoay ⇒ một dòng lớn hơn trần vẫn được ghi, không vòng xoay vô tận,
     * không nuốt dòng nào. Không đổi trạng thái — gọi [record] sau khi ghi.
     */
    fun mustRotateBefore(bytes: Long): Boolean = hasBody && written + bytes.coerceAtLeast(0L) > capBytes

    /** Vừa ghi [bytes] byte của một dòng. */
    fun record(bytes: Long) {
        written += bytes.coerceAtLeast(0L)
        hasBody = true
    }

    /** Tệp mới vừa mở và đã ghi [headerBytes] (dòng tiêu đề). */
    fun startFile(headerBytes: Long) {
        written = headerBytes.coerceAtLeast(0L)
        hasBody = false
    }

    companion object {
        /** 8 MiB — cùng trần một tệp `usage-*.log`. */
        const val PER_FILE_CAP_BYTES: Long = 8L * 1024L * 1024L
    }
}
