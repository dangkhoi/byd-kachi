package com.byd.clusternav.system.inputd

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.OutputStream

/**
 * Adapter [DaemonChannel] THẬT (:app) — phía client của socket localabstract mà [InputDaemonMain] mở. Dùng
 * `android.net.LocalSocket` namespace ABSTRACT (tương đương "localabstract" của adb). Không phụ thuộc thư viện ngoài.
 *
 * Đây là ĐƯỜNG DỮ LIỆU RIÊNG cho chạm — KHÔNG đi qua hàng đợi lệnh `ShellTransport` (ràng buộc B4). Mọi thao tác
 * bọc `runCatching` ⇒ lỗi socket trả false, [InputDaemonClient] đánh dấu unhealthy + fallback.
 */
class LocalAbstractChannel(private val socketName: String) : DaemonChannel {
    private var socket: LocalSocket? = null
    private var out: OutputStream? = null

    override fun connect(): Boolean = runCatching {
        val s = LocalSocket()
        s.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
        socket = s
        out = s.outputStream
        true
    }.getOrDefault(false)

    override fun write(frame: ByteArray): Boolean = runCatching {
        val o = out ?: return false
        o.write(frame)
        o.flush()
        true
    }.getOrDefault(false)

    override fun close() {
        runCatching { out?.close() }
        runCatching { socket?.close() }
        out = null
        socket = null
    }
}
