package com.byd.clusternav

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.launcher.KachiPerf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ READY-AT-HOME §4.10 — MỐC "READY" đo được (tag `KachiReady`) ══════════════════════════════════════════════
 *
 * Mỗi mốc là MỘT dòng: `t` = ms từ lúc tiến trình bật (`Process.getStartElapsedRealtime()`), `so` = ms từ lần màn bật
 * gần nhất (`-` = chưa thấy lần nào trong tiến trình này), và giờ tường. Dòng đi vào `usage-*.log` qua `KachiLog` (bắt
 * logcat theo pid ⇒ cả các dòng in TRƯỚC khi màn chính mở). Dòng `summary` gần nhất hiện ở màn Chẩn đoán (CLAUDE.md
 * §11: anh em chỉ cần chụp màn). Không in khoá / token — chỉ 8 ký tự đầu của vân tay (R-nf6).
 *
 * Chữ ASCII cố ý: hai lần đo trên máy tiếng Việt và tiếng Anh phải so được với nhau (cùng luật `PermissionReport.logLine`).
 */
internal object KachiReadyLog {

    const val TAG = "KachiReady"

    /** Hai tín hiệu màn bật cách nhau dưới chừng này là CÙNG một lần thức (xem [wake]). */
    private const val SAME_WAKE_MS = 10_000L

    /** Mỗi bên gọi bị chặn chỉ in một dòng `deny` mỗi chừng này (bộ đếm vẫn đếm hết). */
    private const val DENY_LOG_GAP_MS = 30_000L

    /** Trần số bên gọi nhớ để tiết chế dòng `deny` (xem [deny]). */
    private const val DENY_KEYS_MAX = 64
    private val DIGITS = Regex("\\d+")

    private val procStart: Long = runCatching { Process.getStartElapsedRealtime() }.getOrDefault(SystemClock.elapsedRealtime())
    private val screenOnAt = AtomicLong(-1L)
    private val upAt = AtomicLong(-1L)
    @Volatile private var upSrc: String = "-"
    @Volatile private var keys: String = "-"
    /** Lần màn bật (hoặc mốc tiến trình) mà dòng `summary` đã in — mỗi lần màn bật một dòng. */
    private val summaryFor = AtomicLong(Long.MIN_VALUE)
    private val lastSummary = AtomicReference<String?>(null)
    private val denyLast = ConcurrentHashMap<String, Long>()
    private val denyTotal = AtomicLong(0)

    fun line(event: String) {
        val now = SystemClock.elapsedRealtime()
        val so = screenOnAt.get().let { if (it < 0) "-" else "+${now - it}" }
        Log.i(TAG, "$event t=${now - procStart} so=$so wall=${wall()}")
    }

    /**
     * Mốc MÀN BẬT của lần thức này = tín hiệu SỚM NHẤT trong hai: `ACTION_SCREEN_ON` hoặc HOME hiện lại lúc màn tương
     * tác (`adopt`). Hai tín hiệu cách nhau < [SAME_WAKE_MS] là cùng một lần thức. Vì sao cần cả hai: [ĐO xe c2 29/09]
     * `on_restart` của HOME 11:34:13.153 đi TRƯỚC `power_screen_state` 11:34:14.236; [ĐO máy ảo 01/10] `home adopt`
     * 23:53:30.034 đi trước `screen_on` 23:53:30.444 — đo từ broadcast là đo hụt phần đầu.
     */
    fun wake(at: Long, src: String) {
        val cur = screenOnAt.get()
        if (cur >= 0 && at >= cur && at - cur < SAME_WAKE_MS) return
        screenOnAt.set(at)
        line("screen_on src=$src")
    }

    fun lastScreenOnAt(): Long = screenOnAt.get()

    fun up(src: String) {
        upAt.compareAndSet(-1L, SystemClock.elapsedRealtime())
        upSrc = src
    }

    fun keys(verdict: String) {
        keys = verdict
        line("keys=$verdict")
    }

    /** VdAppHost vừa tạo màn ảo cho ô [slot] — một dòng, và dòng `summary` cho lần màn bật này (một lần). */
    fun tile(vd: Int, slot: Int) {
        line("tile vd=$vd slot=$slot")
        val now = SystemClock.elapsedRealtime()
        val on = screenOnAt.get()
        val epoch = if (on >= 0) on else procStart
        val prev = summaryFor.get()
        if (prev == epoch || !summaryFor.compareAndSet(prev, epoch)) return
        val from = if (on >= 0) "screen_on" else "proc"
        val up = upAt.get().let { if (it < 0) "-" else "${it - procStart}" }
        val s = "summary $from->tile=${now - epoch} proc->up=$up src=$upSrc keys=$keys"
        lastSummary.set("$s wall=${wall()}")
        Log.i(TAG, s)
    }

    /** Cổng thi hành vừa chặn một phiên NỀN — đếm hết, in tiết chế theo bên gọi. */
    fun deny(caller: String, phase: String) {
        denyTotal.incrementAndGet()
        KachiPerf.add(KachiPerf.Counter.SHELL_DENY)
        val now = SystemClock.elapsedRealtime()
        // Review lượt 1 [P3]: khoá tiết chế = tên luồng ĐÃ BỎ SỐ. `NavConnect` dựng `Thread {}` mới cho mỗi lượt cấp
        // (watchdog 30 s) ⇒ tên `Thread-123`, `Thread-124`… không bao giờ lặp ⇒ bản đồ phình theo giờ chạy và tiết chế vô
        // hiệu (một dòng mỗi 30 s suốt chuyến khi kênh chưa duyệt). Trần [DENY_KEYS_MAX] là lưới an toàn cuối.
        val key = caller.replace(DIGITS, "#")
        val last = denyLast[key]
        if (last != null && now - last < DENY_LOG_GAP_MS) return
        if (denyLast.size >= DENY_KEYS_MAX) denyLast.clear()
        denyLast[key] = now
        line("deny BACKGROUND caller=$caller state=$phase total=${denyTotal.get()}")
    }

    /**
     * Cho màn Chẩn đoán. `keys(now)` = kết luận phím MỚI NHẤT — dòng summary chụp lúc ô có màn ảo, khi lượt cấp phím có
     * thể còn đang chạy (`GRANT(pending)`); kết quả thật tới sau vài giây (review lượt 1 [P2]).
     */
    fun summaryForDiag(): String =
        (lastSummary.get() ?: "(chưa có — dòng summary in khi ô đầu tiên có màn ảo)") + " · keys(now)=$keys"

    fun denyCount(): Long = denyTotal.get()

    private fun wall(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
}
