package com.byd.clusternav.launcher

import android.content.Context
import android.os.Process
import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ═══ FIX286 · SR6 — NHẬT KÝ BỀN mỗi lệnh ghi xe: `filesDir/diag/ctl-writes.log` + logcat ═════════════════════════
 *
 * Phần CHẠM HỆ THỐNG của [CtlWriteJournal] (định dạng thuần ở `:core`). Ba đường đọc, cùng khuôn nhật ký gắn Hỗ trợ
 * (2.83) vì cùng một ràng buộc: bản PHÁT HÀNH không mở được màn Chẩn đoán, không `run-as` (CLAUDE.md §11):
 *  1. **logcat** tag [TAG] — MỌI dòng ra `Log.i` TRƯỚC khi ghi tệp ⇒ `kachi-logs/usage-*.log` (logcat của chính pid
 *     Kachi, `KachiLog`) mang theo; ghi tệp hỏng thì dòng vẫn đã nằm ở đó;
 *  2. **tệp vòng** [CtlWriteJournal.MAX_LINES] dòng qua [DiagRingFile] — sống qua lần BYD giết Kachi khi tắt máy
 *     ([ĐO xe 29/09]), khoá liên tiến trình vì phím vô-lăng ghi xe từ `:wake`;
 *  3. **cầu kiểm thử** `ctllog` (chỉ đọc — `TestBridgeCtlLog`).
 *
 * Mỗi dòng có tiền tố giờ tường + `pid=` (hai tiến trình cùng ghi một tệp; `seq` đếm theo tiến trình).
 *
 * ## Không chặn lệnh ghi
 * [journal] được gọi NGAY TRÊN luồng đang ghi xe (làn ô đơn · làn giọng nói · luồng nhả). Phần logcat rẻ, làm tại
 * chỗ; phần tệp (đọc-sửa-ghi ≤ 200 dòng) xuống làn tuần tự [LANE] trên pool dùng chung [MacroExec] — không dựng
 * luồng mới (global §4.1), và thứ tự dòng trong tệp = thứ tự gọi.
 */
object CtlJournalStore {

    /** Tag logcat — grep `KachiCtl` trong `usage-*.log`. */
    const val TAG = "KachiCtl"

    /** Tên tệp trong `filesDir/diag/`. */
    const val NAME = "ctl-writes.log"

    /** Làn tuần tự của phần ghi tệp. */
    private const val LANE = "ctl-journal"

    private val ring = DiagRingFile(NAME, CtlWriteJournal.MAX_LINES, TAG)

    /** `DateTimeFormatter` bất biến ⇒ an toàn đa luồng (khác `SimpleDateFormat`). */
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    /** Nơi nhận cho `HalBindingTable` của tiến trình này (`AppContainer` tiêm). */
    fun journal(context: Context): CtlJournal {
        val app = context.applicationContext
        return CtlJournal { line -> record(app, line) }
    }

    /** Toàn bộ nhật ký (mọi tiến trình), mới nhất ở cuối. */
    fun read(context: Context): List<String> = ring.read(context.applicationContext)

    private fun record(app: Context, line: String) {
        val full = "${clock.format(LocalDateTime.now())} pid=${Process.myPid()} $line"
        Log.i(TAG, full)
        MacroExec.submitSerial(LANE) { ring.append(app, full) }
    }
}
