package com.byd.clusternav.housekeeping

import android.content.Context
import android.os.Process
import android.util.Log
import com.byd.clusternav.DiagStorageCap
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.98 · R6-E/G/H — dọn MỘT LẦN mỗi tiến trình, trên luồng nền ưu tiên thấp ══════════════════════════════════
 *
 * Owner 2026-10-08: không thêm chi phí chạy, không thứ gì phình vô hạn theo ngày/tháng. [ĐO xe 29/09] BYD giết Kachi mỗi
 * lần tắt máy ⇒ "mỗi lần tiến trình bật" = mỗi lần nổ máy — đủ dày cho ba việc dưới, nên KHÔNG có nhịp định kỳ mới:
 *  • G — [DiagStorageCap.enforce]: trước đây chỉ chạy lúc mở màn chính / NLS nối; tiến trình dựng lại lúc màn tắt
 *    (A11yLifecycleHeal) mà không mở màn chính thì không bao giờ được dọn. Trong MỘT tiến trình, các nguồn phình đều có
 *    trần riêng (`usage` dừng ở 8 MiB, `nav_notif_*` xoay 8 MiB — R6-F, và ClusterBroadcaster gọi lại bộ dọn mỗi 60 s khi
 *    verbose) ⇒ một lượt lúc bật là đủ.
 *  • H — [UpdateApkHousekeeping]: xoá APK OTA đã cài.
 *  • E — [CastPrefsHousekeeping]: gỡ khoá hình học chiếu của app đã gỡ ≥ 30 ngày.
 *
 * Trễ [DELAY_MS] để không tranh CPU/đĩa với đường "lên màn chính sẵn sàng" (READY-AT-HOME, ~3 s sau khi bật). Mỗi bước
 * bọc riêng: một bước hỏng không chặn bước sau và không bao giờ ném vào tiến trình launcher (HOME).
 */
object StartupHousekeeping {
    private const val TAG = "KachiHousekeeping"

    /** 30 s sau khi tiến trình bật. Chuyến < 30 s thì bỏ lượt — lần nổ máy sau làm. */
    const val DELAY_MS = 30_000L

    private val started = AtomicBoolean(false)

    /** Gọi từ `KachiApplication.onCreate` (chỉ tiến trình launcher). Idempotent: lần gọi thứ hai là no-op. */
    fun install(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                Thread.sleep(DELAY_MS)
            } catch (_: InterruptedException) {
                return@Thread
            }
            step("diag-cap") { DiagStorageCap.enforce(app) }
            step("ota-apk") { UpdateApkHousekeeping.sweep(app) }
            step("cast-prefs") { CastPrefsHousekeeping.prune(app) }
        }, "kachi-housekeeping").apply { isDaemon = true }.start()
    }

    /**
     * Một bước dọn. Bắt `RuntimeException` (PackageManager/binder hỏng, `SecurityException`) và `IOException` (đĩa) — đủ
     * cho mọi thứ các bước này ném; `Error` (OOM…) đi tiếp như mọi chỗ khác. Hỏng = bỏ bước, không thử lại.
     */
    private inline fun step(name: String, body: () -> Unit) {
        try {
            body()
        } catch (e: RuntimeException) {
            Log.w(TAG, "$name hỏng — bỏ lượt này", e)
        } catch (e: IOException) {
            Log.w(TAG, "$name hỏng (I/O) — bỏ lượt này", e)
        }
    }
}
