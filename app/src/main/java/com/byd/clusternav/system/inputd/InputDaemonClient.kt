package com.byd.clusternav.system.inputd

import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Vòng đời + gửi cho input-daemon. Đặt ở :app (quản daemon THIẾT BỊ qua socket + shell).
 *
 * ── HAI ĐƯỜNG TÁCH BIỆT (ràng buộc B4) ───────────────────────────────────────────────────────────────────────
 *  • LIFECYCLE (khởi động daemon): 1 lần, qua [launchShell] = seam `ShellTransport` (HÀNG ĐỢI lệnh cửa sổ). OK.
 *  • DỮ LIỆU (mỗi sự kiện chạm): qua [DaemonChannel] (LocalSocket) — KHÔNG qua hàng đợi lệnh.
 *
 * [sendTouch] trả:
 *  • `true`  → daemon healthy, khung đã đưa vào [senderExecutor] để ghi socket (caller KHÔNG fallback).
 *  • `false` → daemon chưa/không sẵn → caller chạy fallback `input -d` (byte-khớp cũ). Đồng thời kick
 *              [ensureStarted] (throttle) để lần chạm sau dùng được daemon.
 *
 * Android-aware (log mặc định `android.util.Log`) nhưng MỌI phụ thuộc thiết bị được TIÊM (channel/log/executor/
 * sleep/now) ⇒ test JVM off-device. Idempotent + tự hồi (reconnect có throttle) + degrade-safe (mọi lỗi → fallback,
 * KHÔNG sập UI). Một daemon THƯỜNG TRÚ dùng chung cho MỌI ô vì mỗi khung tự mang `displayId`.
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
    private val connectTries: Int = 12,
    private val connectStepMs: Long = 120L,
    private val retryCooldownMs: Long = 3_000L,
    private val log: (String) -> Unit = { Log.i("Kachi/InputDaemonClient", it) },
) {
    @Volatile private var channel: DaemonChannel? = null
    @Volatile private var healthy = false
    @Volatile private var starting = false
    @Volatile private var lastStartAttempt = 0L

    /** Daemon đang kết nối được không (health-check bề mặt). */
    fun isHealthy(): Boolean = healthy

    /**
     * Gửi 1 sự kiện chạm. `true` = đã định tuyến qua daemon (socket); `false` = caller phải fallback `input -d`.
     * Xem KDoc lớp. KHÔNG bao giờ ném (degrade-safe).
     */
    fun sendTouch(displayId: Int, action: Int, x: Int, y: Int): Boolean {
        val ch = channel
        if (healthy && ch != null) {
            val frame = InputWireProtocol.encode(TouchFrame(displayId, action, x, y))
            senderExecutor.execute { if (!ch.write(frame)) markDown() }
            return true
        }
        ensureStarted()
        return false
    }

    /** Khởi động daemon (throttled, 1 in-flight). An toàn gọi nhiều lần / từ nhiều thread. Không block caller. */
    fun ensureStarted() {
        if (healthy || starting) return
        val t = now()
        if (lastStartAttempt != 0L && t - lastStartAttempt < retryCooldownMs) return
        starting = true
        lastStartAttempt = t
        lifecycleExecutor.execute {
            try {
                startAndConnect()
            } finally {
                starting = false
            }
        }
    }

    private fun startAndConnect() {
        if (tryConnect()) return   // daemon có thể đã chạy (resident, dùng chung mọi ô) → nối luôn, không launch lại
        runCatching { launchShell(InputDaemonLaunch.launchCmd(apkPath, socketName)) }
            .onFailure { log("launch failed: ${it.message}") }
        for (i in 0 until connectTries) {
            sleep(connectStepMs)
            if (tryConnect()) return
        }
        log("daemon did not come up; staying on input -d fallback")
    }

    private fun tryConnect(): Boolean {
        val ch = channelFactory(socketName)
        return if (runCatching { ch.connect() }.getOrDefault(false)) {
            channel = ch
            healthy = true
            log("connected to daemon socket :$socketName")
            true
        } else {
            runCatching { ch.close() }
            false
        }
    }

    private fun markDown() {
        healthy = false
        runCatching { channel?.close() }
        channel = null
    }

    /** Đóng client (đóng socket + đánh dấu down). Executor mặc định là daemon-thread nên không giữ tiến trình. */
    fun close() = markDown()
}
