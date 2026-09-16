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

    /**
     * Lý do lượt nối hỏng gần nhất — xem KDoc [DaemonChannel.lastError]. `@Volatile` vì lượt nối chạy trên
     * `kachi-inputd-life` còn lượt đọc (cầu kiểm thử `state.inputd`) chạy trên luồng chính.
     */
    @Volatile private var lastError: String? = null

    override fun lastError(): String? = lastError

    /**
     * ⚠ RÒ FD (soát OCR): bản cũ dựng `LocalSocket()` BÊN TRONG `runCatching` và chỉ gán vào [socket] SAU khi
     * `connect` thành công. `connect` ném (daemon chưa lên — ca THƯỜNG GẶP nhất) ⇒ [socket] còn `null` ⇒
     * `close()` mà [InputDaemonClient.tryConnect] gọi ngay sau đó KHÔNG có gì để đóng ⇒ mỗi lượt thử rò đúng
     * một file-descriptor. [InputDaemonClient] thử `connectTries` lượt (25 từ 1.69) mỗi lần khởi và lặp lại mỗi
     * `retryCooldownMs = 3 s` chừng nào chạm còn đi đường fallback ⇒ trên một xe mà daemon KHÔNG BAO GIỜ lên
     * thì đó là ~12 fd mỗi 3 giây cho tới khi tiến trình chạm trần fd. Đóng tường minh ở nhánh hỏng.
     */
    override fun connect(): Boolean {
        close()                       // không bao giờ để lại phiên cũ chưa đóng khi nối lại
        val s = LocalSocket()
        return runCatching {
            s.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            socket = s
            out = s.outputStream
            lastError = null
            true
        }.getOrElse { t ->
            // Câu chữ NGUYÊN VĂN của nền tảng: "Connection refused" (daemon chưa mở socket) và "Permission
            // denied" (SELinux chặn app-uid → socket của shell-uid) là hai bệnh khác nhau, hai cách chữa khác nhau.
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

    override fun close() {
        runCatching { out?.close() }
        runCatching { socket?.close() }
        out = null
        socket = null
    }
}
