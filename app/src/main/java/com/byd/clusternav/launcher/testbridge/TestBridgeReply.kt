package com.byd.clusternav.launcher.testbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ T-BRIDGE · LỜI ĐÁP CỦA MỘT LỆNH — **đúng một lần**, ba nơi cùng lúc ═════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R4. Một lượt chạy trả lời ở ba chỗ, mỗi chỗ cho một người đọc khác:
 *  • `setResultData` ⇒ `am broadcast` **in ngay ra terminal** — người đang gõ lệnh đọc cái này;
 *  • tệp JSON trong `files/test/` ⇒ script `adb pull` đọc, và nó còn lại sau khi terminal đã cuộn mất;
 *  • `Log.i("KachiTest")` ⇒ `adb logcat` đọc, và nó **xen đúng thứ tự** giữa nhật ký của launcher (`KachiVoice`,
 *    `KachiVd`, `Preflight`) — thứ mà hai nguồn kia không làm được.
 *
 * ## Vì sao phải là một lớp có chốt, không phải ba lời gọi rải rác
 * `PendingResult.finish()` gọi lần thứ hai là `IllegalStateException` **giết tiến trình launcher**. Mà lượt chạy
 * có tới ba đường về đích: chạy xong · hết giờ ([KachiTestBridge.CAP_MS]) · ném giữa chừng. Ba đường đó không
 * biết nhau, nên chốt phải nằm ở chỗ cả ba đi qua. Đây đúng hình dạng `single { }` mà `VoiceTextConsole.ask` đã
 * phải dựng cho hộp xác nhận, chỉ khác hậu quả: ở đây gọi hai lần là app chết, không phải một câu treo.
 */
internal class TestBridgeReply(
    ctx: Context,
    private val pending: BroadcastReceiver.PendingResult?,
    private val cmd: String,
) {

    private val app = ctx.applicationContext
    private val startedAt = SystemClock.elapsedRealtime()
    private val answered = AtomicBoolean(false)

    /** Lượt chạy đã trả lời chưa — đường hết-giờ hỏi trước khi ghi một câu "timeout" thừa vào nhật ký. */
    fun isAnswered(): Boolean = answered.get()

    fun ok(vararg fields: Pair<String, Any?>) = finish(true, fields.toList())

    fun ok(fields: List<Pair<String, Any?>>) = finish(true, fields)

    /** Trả lỗi. [code] là **mã ASCII** (`test_mode_off`, `home_not_running`…), không phải câu cho người đọc. */
    fun fail(code: String, vararg fields: Pair<String, Any?>) =
        finish(false, listOf<Pair<String, Any?>>("error" to code) + fields.toList())

    private fun finish(ok: Boolean, fields: List<Pair<String, Any?>>) {
        if (!answered.compareAndSet(false, true)) return
        val ms = SystemClock.elapsedRealtime() - startedAt
        val json = TestBridgeJson.obj(
            listOf<Pair<String, Any?>>("ok" to ok, "cmd" to cmd, "ms" to ms) + fields,
        )
        val path = write(json)
        // Nhật ký ghi bản CẮT NGẮN: `Log` tự cắt ở ~4 kB và cắt giữa chừng thì dòng JSON trong logcat không phân
        // tích được — thà nói rõ "đã cắt, xem tệp" còn hơn để lại một mảnh JSON trông như hợp lệ.
        Log.i(TAG, "reply cmd=$cmd ok=$ok ms=$ms file=${path ?: "-"} json=${trim(json)}")
        pending?.let { p ->
            runCatching {
                p.setResultCode(if (ok) RESULT_OK else RESULT_ERR)
                p.setResultData(json)
                p.finish()
            }.onFailure { Log.w(TAG, "tra ket qua cho am broadcast hong: ${it.javaClass.simpleName}") }
        }
    }

    /**
     * Ghi JSON ra `files/test/<mốc>-<lệnh>.json` — thư mục **ngoài của riêng app**
     * (`/sdcard/Android/data/<gói>/files/test/`), đọc được bằng `adb pull` mà **không cần quyền bộ nhớ nào**.
     *
     * Cùng lựa chọn với `ClusterDiag` (ghi `files/diag/`) và `VoiceWavProbe` (đọc `files/`): đó là đường duy nhất
     * chạy được ở mọi ROM từ Android 10 trở lên mà không phải đi xin quyền.
     *
     * Hỏng ⇒ trả `null` và đi tiếp: mất tệp thì vẫn còn `setResultData` + logcat. Một lượt đo mất một trong ba
     * đường về vẫn là một lượt đo; ném ở đây thì mất cả ba.
     */
    private fun write(json: String): String? = runCatching {
        val dir = app.getExternalFilesDir(DIR) ?: File(app.filesDir, DIR)
        if (!dir.isDirectory) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val out = File(dir, "$stamp-$cmd$SUFFIX")
        out.writeText(json)
        prune(dir)
        out.absolutePath
    }.onFailure { Log.w(TAG, "ghi tep ket qua hong: ${it.javaClass.simpleName}") }.getOrNull()

    /**
     * Giữ [KEEP] tệp mới nhất, xoá phần còn lại.
     *
     * ⚠ Cần vì receiver `exported`: **mọi** lượt nhận đều ghi một tệp, kể cả lượt bị từ chối ngay
     * (`test_mode_off`). Không có trần thì một app bất kỳ trên xe bắn broadcast liên tục là bộ nhớ đầy dần —
     * một đường phá hoại không cần chế độ kiểm thử bật. Trần theo SỐ LƯỢNG (không theo tuổi) vì nó không phụ
     * thuộc đồng hồ treo tường, thứ mà [TestBridgeWindow] đã ghi là không tin được.
     *
     * Xoá hỏng thì bỏ qua: mất một tệp cũ không đáng để hỏng lượt đo đang chạy.
     */
    private fun prune(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(SUFFIX) } ?: return
        if (files.size <= KEEP) return
        files.sortedBy { it.lastModified() }.take(files.size - KEEP).forEach { runCatching { it.delete() } }
    }

    private fun trim(json: String): String =
        if (json.length <= LOG_CAP) json else json.take(LOG_CAP) + "...[cut, see file]"

    internal companion object {
        const val TAG = "KachiTest"

        /** Thư mục con của `getExternalFilesDir` — một tên, một chỗ khai. */
        const val DIR = "test"

        /** Đuôi tệp kết quả — dùng cho cả lượt ghi lẫn lượt dọn, một chỗ khai. */
        const val SUFFIX = ".json"

        /** Trần số tệp kết quả giữ lại (xem `prune`). */
        const val KEEP = 50

        /** Mã trả về cho `am broadcast` (`Broadcast completed: result=N`) — script kiểm được mà không cần `jq`. */
        const val RESULT_OK = 0
        const val RESULT_ERR = 1

        private const val LOG_CAP = 3_000
    }
}
