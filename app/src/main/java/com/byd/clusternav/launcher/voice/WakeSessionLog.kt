package com.byd.clusternav.launcher.voice

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.launcher.DiagRingFile
import com.byd.clusternav.launcher.MacroExec
import com.byd.clusternav.launcher.voice.WakeSessionJournal.Entry
import com.byd.clusternav.launcher.voice.WakeSessionJournal.Outcome
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ═══ FIX286 · VK6 — NHẬT KÝ PHIÊN `:wake`: `filesDir/diag/wake-sessions.log` + logcat `KachiWakeSession` ═══════════════
 *
 * Phần CHẠM HỆ THỐNG của [WakeSessionJournal] (định dạng thuần ở `:core`); cùng khuôn `CtlJournalStore` (SR6): logcat
 * TRƯỚC, tệp vòng sau qua [DiagRingFile] (khoá liên tiến trình), làn tuần tự trên pool dùng chung [MacroExec] — không
 * dựng luồng mới, không I/O trên luồng chính. Đọc: cầu `wakelog` (`TestBridgeWakeLog`, tiến trình chính — tệp chung).
 *
 * ## Vì sao lối vào đi qua một "phiếu chờ" ([pending]) thay vì gắn thẳng vào phiên
 * `VoiceWakeService` biết LỐI VÀO (extra của `LISTEN_NOW` / câu gọi) và mốc NHẬN lệnh; phiên ([VoiceSession]) biết
 * lúc nó THẬT SỰ bắt đầu, lúc micro mở và lúc nó đóng. Hai bên gặp nhau qua [pending] → [claim]: service đặt phiếu,
 * phiên vừa bắt đầu thì nhận phiếu vào [Marks] CỦA RIÊNG NÓ. Nhờ vậy phiên cũ bị cắt (preempt ⇒ `stop()` ⇒ `close()`
 * tới SAU khi phiên mới đã chạy) ghi dòng của chính nó, không đè dòng của phiên mới. Phiếu quá [PENDING_MAX_MS] (lệnh
 * tới lúc phiên đang nghe — không phiên mới nào bắt đầu) bị coi là cũ: phiên sau ghi lối vào `?` thay vì mốc sai.
 */
internal object WakeSessionLog {

    const val TAG = "KachiWakeSession"
    const val NAME = "wake-sessions.log"
    private const val LANE = "wake-journal"

    /** Phiếu chờ cũ hơn ngần này thì không thuộc phiên vừa bắt đầu (xem KDoc lớp). */
    private const val PENDING_MAX_MS = 5_000L

    private val ring = DiagRingFile(NAME, WakeSessionJournal.MAX_LINES, TAG)
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    /** Một phiên đang mở trong nhật ký. `readyAt` ghi từ luồng nền của phiên ⇒ `@Volatile`. */
    class Rec(val entry: Entry, val mode: VoiceWakeMode, val t0: Long, val warm: Boolean, val loading: Boolean, val builds: Int) {
        @Volatile var readyAt = 0L
    }

    @Volatile private var pending: Rec? = null

    /** Service: vừa nhận lệnh mở phiên ([entry], chế độ hiện hành) — đặt phiếu, đo mô hình NGAY lúc nhận. */
    fun pending(entry: Entry, mode: VoiceWakeMode) {
        val r = Rec(entry, mode, SystemClock.elapsedRealtime(), VoiceEngine.loaded(), VoiceEngine.loading(), VoiceEngine.builds())
        pending = r
        // Route đã chọn (CLAUDE.md §6/§7: mỗi đường phải tự nói nó đi đâu) — logcat của `:wake`, đọc bằng adb khi có.
        Log.i(TAG, "nhận lệnh nghe · lối vào=${entry.code} · chế độ=$mode · mô hình sẵn=${r.warm} · đang nạp=${r.loading}")
    }

    /** Phiên vừa bắt đầu: nhận phiếu còn tươi, hoặc mở bản ghi lối vào `?` (không đoán). */
    private fun claim(ctx: Context): Rec {
        val now = SystemClock.elapsedRealtime()
        val p = pending
        pending = null
        if (p != null && now - p.t0 <= PENDING_MAX_MS) return p
        val mode = runCatching { VoiceWakeHold.modeInWake(ctx) }.getOrDefault(VoiceWakeMode.OFF)
        return Rec(Entry.UNKNOWN, mode, now, VoiceEngine.loaded(), VoiceEngine.loading(), VoiceEngine.builds())
    }

    private fun flush(app: Context, r: Rec, outcome: Outcome) {
        val loadMs = if (VoiceEngine.builds() > r.builds) VoiceEngine.lastBuildMs() else null
        val readyMs = r.readyAt.takeIf { it > 0L }?.let { it - r.t0 }
        val line = WakeSessionJournal.line(r.entry, r.mode, r.warm, r.loading, loadMs, readyMs, outcome)
        val full = "${clock.format(LocalDateTime.now())} pid=${Process.myPid()} $line"
        Log.i(TAG, full)
        MacroExec.submitSerial(LANE) { ring.append(app, full) }
    }

    /** Toàn bộ nhật ký (mọi tiến trình), mới nhất ở cuối — cầu `wakelog`. */
    fun read(ctx: Context): List<String> = ring.read(ctx.applicationContext)

    /** Mốc của MỘT phiên `:wake` (factory dựng mỗi lần dựng phiên). Mọi lượt gọi trừ [ready] ở luồng chính. */
    class Marks(ctx: Context) : VoiceSessionMarks {
        private val app = ctx.applicationContext

        @Volatile private var rec: Rec? = null

        override fun started() {
            rec?.let { flush(app, it, Outcome.CANCELLED) }   // phiên được mở lại khi bản ghi cũ chưa đóng — đóng nó trước
            rec = claim(app)
        }

        override fun ready() {
            rec?.let { if (it.readyAt == 0L) it.readyAt = SystemClock.elapsedRealtime() }
        }

        override fun closed(cancelled: Boolean) {
            val r = rec ?: return
            rec = null
            flush(app, r, WakeSessionJournal.outcomeOf(cancelled, r.readyAt > 0L))
        }

        override fun abort(outcome: Outcome) {
            val r = rec ?: return
            rec = null
            flush(app, r, outcome)
        }
    }
}

/**
 * FIX286 · VK6 — bốn mốc mà [VoiceSession] báo cho nhật ký phiên (`null` ở phiên màn chính = không ghi gì). Giao diện
 * thay vì gọi thẳng [WakeSessionLog] để phiên màn chính (cùng lớp [VoiceSession]) không bao giờ chạm tệp của `:wake`.
 */
interface VoiceSessionMarks {
    /** Phiên THẬT SỰ bắt đầu (đã qua cổng "đang có phiên"). */
    fun started()

    /** Micro vừa mở (mốc "sẵn sàng nghe"). Gọi từ luồng nền của phiên. */
    fun ready()

    /** Phiên đóng hẳn (về IDLE) — [cancelled] = người lái huỷ / bị cắt. */
    fun closed(cancelled: Boolean)

    /** Kết cục do bên ngoài quyết (đứng xuống cắt phiên kẹt) — ghi TRƯỚC `stop()`, lượt [closed] sau đó là no-op. */
    fun abort(outcome: Outcome)
}
