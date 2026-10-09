package com.byd.clusternav.system.inputd

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Adapter [DaemonChannel] qua **TCP loopback** (`127.0.0.1:<port>`) — 1.70, thay `LocalAbstractChannel`.
 *
 * ## Vì sao đổi kênh — [ĐO máy ảo + ĐO xe 2026-09-17]
 * Socket abstract của daemon (miền `shell`) bị sepolicy enforcing chặn ở lượt **nối** từ app
 * (`avc: denied { connectto } … scontext=u:r:untrusted_app tcontext=u:r:shell tclass=unix_stream_socket`).
 * Cùng hai miền ấy nối nhau qua TCP loopback thì **không có luật nào cấm**: cả hai đều là `net_domain`, và
 * `INTERNET` app đã có sẵn (tải mô hình OTA). Đường dữ liệu vẫn **riêng** khỏi hàng đợi `ShellTransport` (B4).
 *
 * ## Cửa vào
 * Cổng loopback thì app nào trên máy cũng nối được ⇒ khung ĐẦU TIÊN là `token\n` (do app sinh, truyền cho daemon
 * qua dòng lệnh khởi động). Daemon so khớp rồi mới nhận khung chạm; sai ⇒ đóng.
 *
 * Thuần JVM (`java.net`) nên nằm ở `:core` và test được với một `ServerSocket` thật trên loopback.
 */
class TcpLoopbackChannel(
    private val port: Int,
    private val token: String,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
) : DaemonChannel {
    private var socket: Socket? = null
    private var out: OutputStream? = null
    @Volatile private var input: InputStream? = null

    @Volatile private var lastError: String? = null

    override fun lastError(): String? = lastError

    override fun connect(): Boolean {
        close()
        val s = Socket()
        return runCatching {
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), connectTimeoutMs)
            val o = s.getOutputStream()
            o.write(handshake(token))
            o.flush()
            socket = s
            out = o
            input = s.getInputStream()
            lastError = null
            true
        }.getOrElse { t ->
            // Câu chữ NGUYÊN VĂN của nền tảng — `ConnectException: Connection refused` (chưa có ai nghe) là bệnh
            // khác `Permission denied`; giữ nguyên để `state.inputd` nói đúng bệnh (bài học 1.69).
            lastError = "${t.javaClass.simpleName}: ${t.message ?: "?"}"
            runCatching { s.close() }
            false
        }
    }

    override fun write(frame: ByteArray): Boolean = runCatching {
        val o = out ?: return false
        o.write(frame)
        o.flush()
        true
    }.getOrDefault(false)

    /** Dòng `\n` (UTF-8, trần [LINE_MAX_BYTES] — dài hơn ⇒ cắt phần thừa tới `\n`). EOF / lỗi ⇒ `null`. */
    override fun readLine(): String? {
        val inp = input ?: return null
        val buf = java.io.ByteArrayOutputStream()
        return try {
            while (true) {
                val b = inp.read()
                if (b < 0) return null
                if (b == '\n'.code) break
                if (buf.size() < LINE_MAX_BYTES) buf.write(b)
            }
            buf.toString(Charsets.UTF_8.name())
        } catch (_: java.io.IOException) {
            null
        }
    }

    override fun readsReports(): Boolean = true

    override fun close() {
        input = null
        runCatching { out?.close() }
        runCatching { socket?.close() }
        out = null
        socket = null
    }

    companion object {
        /** Nối loopback thì hoặc có ngay hoặc không có — 300 ms là đủ, và không giữ luồng vòng đời lâu hơn. */
        const val CONNECT_TIMEOUT_MS: Int = 300

        /** Khung bắt tay: `token` + `\n` (ASCII). Daemon đọc tới `\n`, trần [HANDSHAKE_MAX_BYTES]. */
        fun handshake(token: String): ByteArray = (token + "\n").toByteArray(Charsets.US_ASCII)

        /** Trần bắt tay daemon chịu đọc trước khi đóng — chặn kẻ nối vào rồi bơm rác vô hạn. */
        const val HANDSHAKE_MAX_BYTES: Int = 80

        /** Trần một dòng báo cáo daemon → Kachi (log, không phải dữ liệu lớn). */
        const val LINE_MAX_BYTES: Int = 512
    }
}
