package com.byd.clusternav.system.inputd

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import java.io.DataInputStream

/**
 * DAEMON THƯỜNG TRÚ bơm chạm — chạy trong tiến trình uid-2000 (shell) qua `app_process` (xem [InputDaemonLaunch]).
 * Mở `LocalServerSocket` namespace ABSTRACT (tên = args[0], mặc định [InputDaemonLaunch.DEFAULT_SOCKET]), nhận
 * khung [InputWireProtocol] (cố định [InputWireProtocol.FRAME_BYTES] byte), và bơm `MotionEvent` qua [EventInjector]
 * (`InputManager.injectInputEvent` + `MotionEvent.setDisplayId`, phản chiếu).
 *
 * Vì sao daemon (không phải `input -d` mỗi lần): tránh spawn tiến trình mỗi sự kiện (~75ms) ⇒ chạm mượt
 * (scrcpy-style). Vì sao app_process uid-2000: cần quyền `INJECT_EVENTS` của shell (uid app KHÔNG có) + thoát
 * hidden-API enforcement (app_process standalone không bị chặn).
 *
 * BỀN BỈ: bind lỗi → log + thoát sạch; khung rác → bỏ qua (không sập); client rớt → vòng lại accept client mới
 * (hỗ trợ reconnect). KHÔNG có bề mặt runtime nào của app (chỉ chạy khi shell app_process gọi). <=500 LOC.
 *
 * ⚠ E2E chỉ verify ĐƯỢC TRÊN XE / khi dadb loopback lên (phiên này loopback DOWN trên emulator). Khi daemon
 * không lên, [InputDaemonClient] tự fallback `input -d` (hành vi cũ) ⇒ KHÔNG hồi quy chạm.
 */
object InputDaemonMain {
    private const val TAG = "Kachi/InputDaemon"

    @JvmStatic
    fun main(args: Array<String>) {
        val socketName = args.firstOrNull()?.takeIf { it.isNotBlank() } ?: InputDaemonLaunch.DEFAULT_SOCKET
        log("start localabstract:$socketName")
        val server = try {
            LocalServerSocket(socketName)
        } catch (t: Throwable) {
            log("bind failed: ${t.message}")
            return
        }
        val injector = EventInjector { log(it) }
        try {
            acceptLoop(server, injector)
        } finally {
            runCatching { server.close() }
            log("exit")
        }
    }

    /** Phục vụ TỪNG client một; client rớt → vòng lại chờ client mới (hỗ trợ client reconnect). */
    private fun acceptLoop(server: LocalServerSocket, injector: EventInjector) {
        while (true) {
            val client = try {
                server.accept()
            } catch (t: Throwable) {
                log("accept ended: ${t.message}")
                return
            }
            runCatching { serve(client, injector) }.onFailure { log("client error: ${it.message}") }
            runCatching { client.close() }
        }
    }

    /** Đọc khung cố định tới EOF; khung hợp lệ → inject; khung rác → bỏ qua (self-resync theo độ dài cố định). */
    private fun serve(client: LocalSocket, injector: EventInjector) {
        val input = DataInputStream(client.inputStream.buffered())
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
