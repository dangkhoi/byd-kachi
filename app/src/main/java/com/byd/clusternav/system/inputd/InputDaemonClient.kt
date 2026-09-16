package com.byd.clusternav.system.inputd

import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Vòng đời + gửi cho input-daemon. Đặt ở :app (quản daemon THIẾT BỊ qua socket + shell).
 *
 * ── HAI ĐƯỜNG TÁCH BIỆT (ràng buộc B4) ───────────────────────────────────────────────────────────────────────
 *  • LIFECYCLE (khởi động daemon): 1 lần, qua [launchShell] = seam `ShellTransport` (HÀNG ĐỢI lệnh cửa sổ). OK.
 *  • DỮ LIỆU (mỗi sự kiện chạm): qua [DaemonChannel] (LocalSocket) — KHÔNG qua hàng đợi lệnh.
 *
 * [sendTouch] trả:
 *  • `true`  → daemon healthy, khung đã đưa vào [senderExecutor] để ghi socket (caller KHÔNG fallback).
 *  • `false` → daemon chưa/không sẵn → caller chạy đường lùi theo CỬ CHỈ (`GestureFallback` + `input -d`).
 *              Đồng thời kick [ensureStarted] (throttle) để lần chạm sau dùng được daemon.
 *
 * Android-aware (log mặc định `android.util.Log`) nhưng MỌI phụ thuộc thiết bị được TIÊM (channel/log/executor/
 * sleep/now) ⇒ test JVM off-device. Idempotent + tự hồi (reconnect có throttle) + degrade-safe (mọi lỗi → fallback,
 * KHÔNG sập UI). Một daemon THƯỜNG TRÚ dùng chung cho MỌI ô vì mỗi khung tự mang `displayId`.
 *
 * ═══ 1.69 — ĐẦU DÒ: vì sao daemon KHÔNG lên trên xe ═════════════════════════════════════════════════════════
 * [ĐO xe 2026-09-16] (`docs/diagnostics/oncar-trace-2026-09-16b/README.md` §9.1): mọi lần mở app đều in
 * *"daemon did not come up; staying on input -d fallback"* — một câu **không mang thông tin nào**: không biết
 * lệnh khởi động chạy được không, không biết `connect` hỏng vì *refused* hay vì *permission denied*, không biết
 * đợi bao lâu. Bốn lượt xe trôi qua mà câu trả lời vẫn [CHƯA BIẾT]. Lượt này sửa đúng chỗ đó, KHÔNG đoán nguyên
 * nhân:
 *  1. **Nhật ký daemon ra tệp thật** — [logDir] + `InputDaemonLaunch.logCmd` ⇒ stdout/stderr của `app_process`
 *     nằm ở `kachi-logs/inputd-<stamp>.log` (cùng thư mục `usage-*.log`, cùng một lệnh `adb pull`).
 *  2. **Lý do TỪNG lượt nối** — [DaemonChannel.lastError] mang câu chữ nguyên văn của nền tảng.
 *  3. **`nohup` có tồn tại không** — dò bằng `which` MỘT lần rồi chọn biến thể, thay vì giả định toybox DL3 có nó.
 *  4. **Chờ đủ lâu** — 25 × 200 ms = **5 s** (trước: 12 × 120 ms = 1,44 s; trên TRINKET một lượt `app_process`
 *     nạp dex có thể lâu hơn thế).
 *
 * ⚠ Mọi phỏng đoán về NGUYÊN NHÂN vẫn là [CHƯA BIẾT] cho tới khi có tệp log từ xe. Việc của lượt này là **sinh ra
 * bằng chứng đó**, không phải kết luận thay nó.
 *
 * ### Vì sao KHÔNG in một dòng logcat cho mỗi lượt nối
 * 25 lượt mỗi 3 giây trên một xe mà daemon không bao giờ lên = ~500 dòng/phút — đúng loại log mà 1.67 vừa cắt đi
 * ([ĐO xe] 79 KB/phút ra thẻ). Ở đây: in lượt ĐẦU, in mỗi khi **lý do ĐỔI**, in lượt CUỐI (kèm tổng số lượt và
 * đường dẫn tệp log). Tổng số lượt + lý do cuối luôn đọc được qua cầu kiểm thử ([lastSnapshot] → `state.inputd`).
 */
class InputDaemonClient(
    private val apkPath: String,
    private val launchShell: (String) -> String,
    private val socketName: String = InputDaemonLaunch.DEFAULT_SOCKET,
    private val channelFactory: (String) -> DaemonChannel = { LocalAbstractChannel(it) },
    private val lifecycleExecutor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "kachi-inputd-life").apply { isDaemon = true }
    },
    private val senderExecutor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "kachi-inputd-send").apply { isDaemon = true }
    },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = { System.currentTimeMillis() },
    // 25 × 200 ms = 5 s tổng (1.69; trước là 12 × 120 ms = 1,44 s — có thể ngắn hơn cả một lượt nạp dex trên TRINKET).
    private val connectTries: Int = 25,
    private val connectStepMs: Long = 200L,
    private val retryCooldownMs: Long = 3_000L,
    /**
     * Thư mục ghi nhật ký daemon (tuyệt đối, **shell uid-2000 ghi được** ⇒ thư mục ngoài thẻ của `KachiLog`).
     * `null` ⇒ giữ nguyên hành vi cũ (`>/dev/null`) — đường của test và của máy không gắn được thẻ.
     */
    private val logDir: () -> String? = { null },
    /**
     * Công tắc ẩn `inputd_disabled` — ÉP đi đường lùi. Tồn tại để máy ảo (nơi daemon có thể lên bình thường) vẫn
     * diễn được đúng cái nhánh mà xe đang mắc kẹt; không có nó thì nhánh lùi chỉ kiểm được bằng... một chiếc xe.
     */
    private val disabled: () -> Boolean = { false },
    private val log: (String) -> Unit = { Log.i("Kachi/InputDaemonClient", it) },
) {
    @Volatile private var channel: DaemonChannel? = null
    @Volatile private var healthy = false

    /** CAS thay cờ `Boolean` (soát OCR #69): "kiểm rồi đặt" hai bước cho hai luồng chạm cùng lọt vào một lượt launch. */
    private val starting = AtomicBoolean(false)

    @Volatile private var lastStartAttempt = 0L
    @Volatile private var attempts = 0
    @Volatile private var lastError = ""
    @Volatile private var logPath = ""

    /** Daemon đang kết nối được không (health-check bề mặt). */
    fun isHealthy(): Boolean = healthy

    /**
     * Gửi 1 sự kiện chạm. `true` = đã định tuyến qua daemon (socket); `false` = caller phải chạy đường lùi.
     * Xem KDoc lớp. KHÔNG bao giờ ném (degrade-safe).
     */
    fun sendTouch(displayId: Int, action: Int, x: Int, y: Int): Boolean {
        if (disabled()) {
            if (lastError != DISABLED) {
                lastError = DISABLED
                healthy = false
                publish()
                log("inputd_disabled = true ⇒ ép đường lùi theo cử chỉ (không khởi daemon)")
            }
            return false
        }
        val ch = channel
        if (healthy && ch != null) {
            val frame = InputWireProtocol.encode(TouchFrame(displayId, action, x, y))
            senderExecutor.execute { if (!ch.write(frame)) markDown(WRITE_FAILED) }
            return true
        }
        ensureStarted()
        return false
    }

    /** Khởi động daemon (throttled, 1 in-flight). An toàn gọi nhiều lần / từ nhiều thread. Không block caller. */
    fun ensureStarted() {
        if (healthy || disabled()) return
        val t = now()
        if (lastStartAttempt != 0L && t - lastStartAttempt < retryCooldownMs) return
        // CAS: đúng MỘT luồng thắng cuộc và đi tiếp; kẻ thua trả về ngay (soát OCR #69).
        if (!starting.compareAndSet(false, true)) return
        lastStartAttempt = t
        lifecycleExecutor.execute {
            try {
                startAndConnect()
            } finally {
                starting.set(false)
            }
        }
    }

    private fun startAndConnect() {
        if (tryConnect(attempt = 0)) return   // daemon có thể đã chạy (resident, dùng chung mọi ô) → nối luôn
        val useNohup = nohupAvailable()
        val path = logDir()?.takeIf { it.isNotBlank() }
            ?.let { "$it/${InputDaemonLaunch.logFileName(now())}" }
        logPath = path.orEmpty()
        val cmd = InputDaemonLaunch.launchCmd(apkPath, socketName, path, useNohup)
        val rc = runCatching { launchShell(cmd) }
            .onFailure {
                lastError = "launch failed: ${it.message}"
                log("launch NÉM: ${it.message} — cmd=$cmd")
            }
            .getOrNull()
        log("launch rc=${rc?.trim()?.take(RC_CHARS) ?: "<ném>"} nohup=$useNohup log=${logPath.ifEmpty { "/dev/null" }}")
        var reason = ""
        for (i in 1..connectTries) {
            sleep(connectStepMs)
            if (tryConnect(attempt = i)) return
            val r = lastError
            // In lượt ĐẦU, mỗi khi lý do ĐỔI, và lượt CUỐI — xem KDoc lớp (vì sao không in đủ 25 dòng).
            if (i == 1 || r != reason || i == connectTries) log("connect #$i: ${r.ifEmpty { "?" }}")
            reason = r
        }
        log(
            "daemon did not come up sau ${connectTries * connectStepMs} ms (${connectTries} lượt);" +
                " lý do cuối=${reason.ifEmpty { "?" }}; nhật ký daemon=${logPath.ifEmpty { "/dev/null (không có thẻ)" }};" +
                " ở lại đường lùi theo cử chỉ",
        )
        publish()
    }

    /** `which nohup` MỘT lần mỗi tiến trình (kết quả không đổi trong một lượt khởi động máy). */
    private fun nohupAvailable(): Boolean {
        nohup?.let { return it }
        val out = runCatching { launchShell(InputDaemonLaunch.WHICH_NOHUP) }.getOrDefault("")
        val has = InputDaemonLaunch.hasNohup(out)
        if (!has) log("KHÔNG thấy `nohup` trên máy này (which ⇒ '${out.trim().take(RC_CHARS)}') ⇒ dùng biến thể `… &`")
        nohup = has
        return has
    }

    @Volatile private var nohup: Boolean? = null

    private fun tryConnect(attempt: Int): Boolean {
        attempts = attempt
        val ch = channelFactory(socketName)
        return if (runCatching { ch.connect() }.getOrDefault(false)) {
            channel = ch
            healthy = true
            lastError = ""
            publish()
            log("connected to daemon socket :$socketName (lượt thứ $attempt)")
            true
        } else {
            lastError = ch.lastError().orEmpty()
            runCatching { ch.close() }
            false
        }
    }

    /**
     * Hạ cờ khoẻ + đóng socket. [reason] là lý do ghi vào [lastError] — thứ mà `state.inputd` báo ra và là **cả
     * mục đích** của lượt 1.69 này (xem KDoc lớp: bốn lượt xe trôi qua vì không ai biết daemon hỏng ở đâu).
     *
     * ⚠ [SOÁT 1.69 · P3] Vì sao lý do là THAM SỐ chứ không phải hằng `"socket write failed"` viết trong thân:
     * [close] cũng đi qua đây, và khi đó **không có lượt ghi socket nào hỏng cả** — dán nhãn ấy lên là ghi đè
     * đúng câu chữ nguyên văn vừa đo được (`"IOException: Permission denied"` — sepolicy, §1 của
     * `docs/diagnostics/inputd-selinux-and-gesture-fallback-2026-09-17.md`) bằng một lý do **sai**, ngay trước
     * lúc người ta đọc nó. Chẩn đoán mà tự bịa lý do thì tệ hơn không có chẩn đoán.
     */
    private fun markDown(reason: String) {
        healthy = false
        lastError = reason
        runCatching { channel?.close() }
        channel = null
        publish()
    }

    /**
     * Đóng client: đóng socket, đánh dấu down, **và dừng hai executor của chính nó** (soát OCR #70).
     *
     * Hai executor mặc định là daemon-thread nên không giữ tiến trình sống — nhưng hợp đồng `close()` không nói
     * ra điều đó, và một instance THỨ HAI (test on-device, một chỗ dùng tương lai) sẽ rò hai luồng mỗi lần.
     * Executor **được tiêm từ ngoài** (test, hoặc một chủ sở hữu khác) thì không phải của ta để đóng — nên chỉ
     * đóng khi nó đúng là một [ExecutorService], tức đúng hai cái do hàm dựng này tạo.
     */
    fun close() {
        markDown(CLOSED)
        (lifecycleExecutor as? ExecutorService)?.shutdown()
        (senderExecutor as? ExecutorService)?.shutdown()
    }

    /** Đẩy ảnh chụp ra chỗ cầu kiểm thử đọc được ([lastSnapshot]). */
    private fun publish() {
        last = Health(healthy, lastError, attempts, logPath)
    }

    /** Ảnh chụp sức khoẻ daemon cho cầu kiểm thử (`state.inputd`) — KHÔNG có bề mặt người dùng nào. */
    data class Health(
        val healthy: Boolean,
        val lastError: String,
        val attempts: Int,
        val logPath: String,
    )

    companion object {
        private const val DISABLED = "disabled_by_pref"

        /** Lượt ghi socket hỏng ⇒ daemon coi như rớt (lần chạm sau đi đường lùi + kick khởi động lại). */
        private const val WRITE_FAILED = "socket write failed"

        /** [close] — KHÔNG phải một lỗi; xem KDoc [markDown] về vì sao hai ca này không được dùng chung câu chữ. */
        private const val CLOSED = "closed"

        /** Cắt đầu ra shell khi đưa vào logcat — một lệnh hỏng có thể trả về cả trang chữ. */
        private const val RC_CHARS = 120

        /**
         * Ảnh chụp gần nhất của **client đang chạy**. `object`-level vì cầu kiểm thử là một `BroadcastReceiver`
         * dựng mới tinh mỗi lượt broadcast, không có đường nào cầm được `AppContainer`. Cùng hình dạng (và cùng
         * lý do) với `VoiceSpeakerRouter.lastSnapshot()`.
         */
        @Volatile
        private var last = Health(healthy = false, lastError = "", attempts = 0, logPath = "")

        fun lastSnapshot(): Health = last
    }
}
