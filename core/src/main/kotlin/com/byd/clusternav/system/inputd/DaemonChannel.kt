package com.byd.clusternav.system.inputd

/**
 * PORT (:core) cho KÊNH DỮ LIỆU tới input-daemon. Adapter thật ở :app (`LocalAbstractChannel` bọc
 * `android.net.LocalSocket` namespace ABSTRACT — tương đương "localabstract" của adb). Tách port ra :core để
 * `InputDaemonClient` test được với kênh GIẢ (không cần thiết bị) và để :core giữ hợp đồng (giống `LauncherPorts`).
 * PURE — chỉ `ByteArray` + `Boolean`, không android.
 *
 * ⚠ Đây là ĐƯỜNG DỮ LIỆU RIÊNG cho mỗi sự kiện chạm — KHÔNG đi qua hàng đợi lệnh của `ShellTransport` (ràng buộc B4).
 */
interface DaemonChannel {
    /** Mở kết nối tới socket daemon. true nếu nối được. */
    fun connect(): Boolean

    /** Ghi một khung đã encode. true nếu ghi xong; false ⇒ kênh hỏng (caller đánh dấu unhealthy + fallback). */
    fun write(frame: ByteArray): Boolean

    /** Đóng kênh (idempotent). */
    fun close()
}
