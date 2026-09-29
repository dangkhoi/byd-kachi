package com.byd.clusternav.modules.navaccess

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ghi [A11yBindJournal] xuống tệp của chính app — phần CÓ chạm hệ thống của R7.
 *
 * Tệp: `filesDir/diag/a11y-bind.log`. Sống qua standby, qua khởi động lại tiến trình, qua cả reboot. Đây là
 * thứ trả lời câu hỏi mà `logcat` không trả lời được: **mối nối đứt lúc nào, và lúc đó xe vừa ngủ bao lâu**
 * ([ĐO 2026-09-28] vòng đệm sự kiện trên xe chỉ còn 32 phút nên sáng ra đã trôi mất).
 *
 * ## Ba đường đọc (2.83)
 * [ĐO xe 29/09] trên bản PHÁT HÀNH màn Chẩn đoán không mở được (nút gỡ 21/09, `exported=false` ⇒ `am start` bị từ
 * chối) và `run-as` không có (không debuggable) ⇒ trước 2.83 tệp này ghi được mà KHÔNG AI ĐỌC ĐƯỢC trên xe owner.
 * Nay:
 *  1. **Cầu kiểm thử** `am broadcast … --es cmd a11ylog [--ei n <số dòng>]` (chỉ đọc, sau công tắc chế độ kiểm thử
 *     — `TestBridgeA11yLog`) trả N dòng cuối + hai mốc prefs của thang chữa;
 *  2. **logcat** — MỌI dòng vừa ghi (kể cả nhịp tim) cũng ra `Log.i` tag [TAG] ([A11yBindJournal.logcatLine]) ⇒
 *     `kachi-logs/usage-*.log` (`adb pull`, không cần root) mang theo;
 *  3. màn Chẩn đoán — còn cho bản build mở được nó.
 */
object A11yBindJournalStore {

    private const val TAG = "A11yJournal"
    private const val DIR = "diag"
    private const val NAME = "a11y-bind.log"

    /** Nhịp tim: không đổi trạng thái thì mỗi giờ vẫn ghi một dòng, để biết nhật ký còn sống. */
    private const val HEARTBEAT_MS = 3_600_000L

    private val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    /**
     * HAI luồng ghi vào cùng tệp này: watchdog 30 s (luồng chính của FGS) và thân lượt grant
     * ([com.byd.clusternav.NavConnect] — luồng nền riêng, `escalateIfStuck`). Cả hai làm đọc-sửa-ghi trọn tệp,
     * nên không có khoá thì hai lượt đan nhau sẽ ghi đè mất dòng của nhau (hoặc ghi ra tệp cắt dở đúng lúc màn
     * Chẩn đoán đang đọc). Khoá ở đây là đủ: tệp chỉ của riêng tiến trình này.
     */
    private val lock = Any()

    private fun file(ctx: Context): File = File(File(ctx.filesDir, DIR).apply { mkdirs() }, NAME)

    /** Toàn bộ nhật ký, mới nhất ở cuối. Rỗng nếu chưa có gì hoặc đọc lỗi. */
    fun read(ctx: Context): List<String> = synchronized(lock) { readLocked(ctx) }

    private fun readLocked(ctx: Context): List<String> = try {
        val f = file(ctx)
        if (f.isFile) f.readLines().filter { it.isNotBlank() } else emptyList()
    } catch (e: IOException) {
        Log.w(TAG, "đọc nhật ký lỗi: ${e.message}")
        emptyList()
    }

    /**
     * Ghi một dòng NẾU đáng ghi (đổi trạng thái, hoặc tới nhịp tim). Không ném ra ngoài: nhật ký hỏng thì
     * tính năng vẫn phải chạy — đây là dụng cụ chẩn đoán, không phải đường sống của phím.
     *
     * @param binderOnly quan sát CHỈ hỏi binder (không có bản `dumpsys`) — không tách được NOT_BOUND khỏi STUCK, xem
     *   [A11yBindJournal.sameState]. Chỉ watchdog 30 s truyền `true`.
     * @return `true` nếu vừa ghi thêm một dòng.
     */
    fun record(
        ctx: Context,
        state: A11yBindJournal.State,
        note: String,
        binderOnly: Boolean = false,
    ): Boolean = synchronized(lock) {
        recordLocked(ctx, state, note, binderOnly)
    }

    private fun recordLocked(ctx: Context, state: A11yBindJournal.State, note: String, binderOnly: Boolean): Boolean = try {
        val f = file(ctx)
        val lines = readLocked(ctx)
        val prev = A11yBindJournal.stateOf(lines.lastOrNull())
        val sinceLast = if (f.isFile) (System.currentTimeMillis() - f.lastModified()).coerceAtLeast(0L) else Long.MAX_VALUE
        if (!A11yBindJournal.shouldAppend(prev, state, sinceLast, HEARTBEAT_MS, binderOnly)) {
            false
        } else {
            val line = A11yBindJournal.line(
                wallIso = fmt.format(Date()),
                elapsedMs = SystemClock.elapsedRealtime(),
                uptimeMs = SystemClock.uptimeMillis(),
                state = state,
                pid = Process.myPid(),
                note = note,
            )
            // Ra logcat TRƯỚC khi ghi tệp, và KHÔNG gác theo "có đổi trạng thái không": nhịp tim cũng là bằng
            // chứng (nhật ký còn sống lúc đó). Ghi tệp hỏng (thẻ đầy, IOException bên dưới) thì dòng này vẫn đã nằm
            // trong usage log — đường đọc duy nhất còn lại của bản phát hành (KDoc đối tượng này).
            Log.i(TAG, A11yBindJournal.logcatLine(prev, line))
            val kept = A11yBindJournal.trim(lines + line)
            f.writeText(kept.joinToString("\n", postfix = "\n"))
            true
        }
    } catch (e: IOException) {
        Log.w(TAG, "ghi nhật ký lỗi: ${e.message}")
        false
    }
}
