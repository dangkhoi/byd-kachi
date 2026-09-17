package com.byd.clusternav.system.inputd

import android.net.LocalServerSocket
import android.util.Log
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

/**
 * DAEMON THƯỜNG TRÚ bơm chạm — chạy trong tiến trình uid-2000 (shell) qua `app_process` (xem [InputDaemonLaunch]).
 * Nhận khung [InputWireProtocol] (cố định [InputWireProtocol.FRAME_BYTES] byte) và bơm `MotionEvent` qua
 * [EventInjector] (`InputManager.injectInputEvent` + `MotionEvent.setDisplayId`, phản chiếu).
 *
 * ## 1.70 — kênh **TCP loopback** là đường chính; socket abstract chỉ còn khi gọi tay
 * [ĐO máy ảo + ĐO xe 2026-09-17]: sepolicy enforcing chặn `untrusted_app` nối tới socket abstract của miền
 * `shell` (`avc: denied { connectto }`; trên xe: `IOException: Permission denied` 25/25 lượt, daemon vẫn sống
 * — *"bind failed: Address already in use"*). Dòng lệnh khởi động nay mang `tcp <port> <token>`
 * ([InputDaemonLaunch.ARG_TCP]): daemon bind `127.0.0.1:<port>`, và khung ĐẦU TIÊN của mỗi client phải là
 * `<token>\n` — sai ⇒ đóng ngay (cổng loopback thì app nào trên máy cũng nối được, nên phải có cửa).
 *
 * ## Tự thoát khi không ai cần
 * Cổng cố định theo uid + token cố định theo cài đặt ⇒ app mở lại thì **dùng lại** daemon này. Nhưng gỡ app
 * / đổi token thì không ai nối nữa: sau [IDLE_EXIT_MS] không có client, daemon tự thoát (không có daemon mồ côi
 * sống tới lần khởi động lại đầu xe).
 *
 * Vì sao daemon (không phải `input -d` mỗi lần): tránh spawn tiến trình mỗi sự kiện (~75 ms, trên xe tải nặng
 * hơn 1 s) ⇒ chạm mượt (scrcpy-style). Vì sao app_process uid-2000: cần quyền `INJECT_EVENTS` của shell (uid app
 * KHÔNG có) + thoát hidden-API enforcement.
 *
 * BỀN BỈ: bind lỗi → log + thoát sạch; khung rác → bỏ qua (không sập); client rớt → vòng lại accept client mới.
 * KHÔNG có bề mặt runtime nào của app (chỉ chạy khi shell app_process gọi). <=500 LOC.
 */
object InputDaemonMain {
    private const val TAG = "Kachi/InputDaemon"

    /** Không client nào trong khoảng này ⇒ thoát (10 phút: đủ cho một lượt đổi bố cục/khởi động lại app). */
    const val IDLE_EXIT_MS: Int = 10 * 60 * 1000

    @JvmStatic
    fun main(args: Array<String>) {
        val socketName = args.firstOrNull()?.takeIf { it.isNotBlank() } ?: InputDaemonLaunch.DEFAULT_SOCKET
        val tcpAt = args.indexOf(InputDaemonLaunch.ARG_TCP)
        val port = if (tcpAt >= 0) args.getOrNull(tcpAt + 1)?.toIntOrNull() else null
        val token = if (tcpAt >= 0) args.getOrNull(tcpAt + 2).orEmpty() else ""
        val injector = EventInjector { log(it) }
        if (port != null) {
            if (!InputDaemonLaunch.validToken(token)) { log("tcp: token không hợp lệ — thoát"); return }
            serveTcp(port, token, injector)
        } else {
            serveAbstract(socketName, injector)
        }
    }

    private fun serveTcp(port: Int, token: String, injector: EventInjector) {
        log("start tcp 127.0.0.1:$port")
        val server = try {
            ServerSocket(port, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = IDLE_EXIT_MS }
        } catch (t: Throwable) {
            log("bind failed: ${t.message}")
            return
        }
        try {
            while (true) {
                val client = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    log("idle ${IDLE_EXIT_MS / 1000} s — exit")
                    return
                } catch (t: Throwable) {
                    log("accept ended: ${t.message}")
                    return
                }
                runCatching {
                    client.tcpNoDelay = true
                    val input = client.getInputStream()
                    if (!handshake(input, token)) { log("client từ chối: sai token"); return@runCatching }
                    serve(input, injector)
                }.onFailure { log("client error: ${it.message}") }
                runCatching { client.close() }
            }
        } finally {
            runCatching { server.close() }
            log("exit")
        }
    }

    /** Đường CŨ (abstract) — giữ để chạy tay/so sánh; app từ 1.70 không khởi động daemon theo đường này nữa. */
    private fun serveAbstract(socketName: String, injector: EventInjector) {
        log("start localabstract:$socketName")
        val server = try {
            LocalServerSocket(socketName)
        } catch (t: Throwable) {
            log("bind failed: ${t.message}")
            return
        }
        try {
            while (true) {
                val client = try {
                    server.accept()
                } catch (t: Throwable) {
                    log("accept ended: ${t.message}")
                    return
                }
                runCatching { serve(client.inputStream, injector) }.onFailure { log("client error: ${it.message}") }
                runCatching { client.close() }
            }
        } finally {
            runCatching { server.close() }
            log("exit")
        }
    }

    /** Đọc `token\n` (trần [TcpLoopbackChannel.HANDSHAKE_MAX_BYTES]) và so khớp. */
    private fun handshake(input: InputStream, token: String): Boolean {
        val sb = StringBuilder()
        while (sb.length < TcpLoopbackChannel.HANDSHAKE_MAX_BYTES) {
            val b = input.read()
            if (b < 0) return false
            if (b == '\n'.code) return sb.toString() == token
            sb.append(b.toChar())
        }
        return false
    }

    /** Đọc khung cố định tới EOF; khung hợp lệ → inject; khung rác → bỏ qua (self-resync theo độ dài cố định). */
    private fun serve(raw: InputStream, injector: EventInjector) {
        val input = DataInputStream(raw.buffered())
        val buf = ByteArray(InputWireProtocol.FRAME_BYTES)
        while (true) {
            try {
                input.readFully(buf)      // đọc đúng FRAME_BYTES byte; EOF → ném → thoát vòng (client đóng)
            } catch (_: Throwable) {
                return
            }
            val frame = InputWireProtocol.decode(buf)
            if (frame == null) {
                log("skip malformed frame")
                continue
            }
            injector.inject(frame)
        }
    }

    private fun log(msg: String) {
        runCatching { Log.i(TAG, msg) }
        // app_process không có logcat pipe của app → in stdout để bắt được khi chạy thủ công (production redirect /dev/null).
        println("[$TAG] $msg")
    }
}
