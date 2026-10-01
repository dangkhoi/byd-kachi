package com.byd.clusternav.carexec

/**
 * Vì sao một phiên adb loopback (`localhost:5555`) KHÔNG mở được.
 *
 * Trước 2026-08-24 mọi thất bại của [LocalDeviceShell] bị `runCatching{}.getOrNull()` gộp thành **một chữ
 * `null` duy nhất** — "chưa bấm Cho phép gỡ lỗi USB", "adb-tcp chưa bật", "đứt giữa chừng" nhìn giống hệt
 * nhau. Hệ quả owner báo (F2): giữ phím mic ở lần mở app đầu thì im lặng, phải tắt/mở lại app mới dùng
 * được — vì chờ hộp thoại là việc ĐÁNG chờ mà không ai chờ, còn cổng đóng là việc KHÔNG đáng chờ mà cũng
 * không ai nói ra.
 *
 * Bằng chứng phân loại — đọc thẳng bytecode `dev.mobile:dadb:2.0.0`
 * (`javap -c dadb/AdbConnection$Companion.class`, luồng `connect(AdbReader, AdbWriter, AdbKeyPair, Closeable)`):
 *  1. `writeConnect()` → `readMessage()`; nếu máy trả `AUTH` (0x48545541) thì ký token, `writeAuth(2, chữ-ký)`.
 *  2. Máy trả `AUTH` lần nữa ⇒ khoá lạ ⇒ client gửi `writeAuth(3, khoá-công-khai)` rồi `readMessage()`.
 *     Đây là chỗ hệ thống bung hộp thoại "Cho phép gỡ lỗi USB?" — adbd **không trả lời gì** tới khi người
 *     dùng bấm, nên lần đọc này **treo**.
 *  3. Đọc xong mà vẫn là `AUTH` ⇒ ném `AdbAuthException("Device rejected authentication (unauthorized)")`.
 *     Không phải `CNXN` và không phải `AUTH` ⇒ `AdbConnectException`.
 *  4. `DadbImpl.newConnection` đặt `socket.setSoTimeout(socketTimeout)`; `Dadb.create(host, port, keys)`
 *     truyền `socketTimeout = 0` = **đọc vô hạn** ⇒ bước 2 treo VĨNH VIỄN chứ không ném lỗi.
 *
 * Nên hai dạng "chưa được cấp quyền" phải nhận diện được cả hai kiểu: máy trả lời AUTH ([AUTH_REJECTED])
 * và máy im lặng chờ người dùng bấm ([AWAITING_APPROVAL] — chỉ lộ ra khi có hạn đọc, xem
 * [LocalShellRetry.socketTimeoutMs]).
 *
 * KHÔNG dùng chung bộ phân loại với `vehicleprobe/DadbVehicleTransport.classifyExpectedFailure`: đó là
 * transport của cổng T10 đang NIÊM PHONG (`docs/specs/seal-hud-sign-vehicle-test-t10.html`), từ vựng của
 * nó (`TransportFailureKind`) mô tả một hợp đồng khác. Gộp lại sẽ phải mở niêm phong để phục vụ một nhu
 * cầu không liên quan.
 */
enum class LocalShellFailure {
    /**
     * TCP nối được nhưng adbd chưa trả `CNXN` trong hạn đọc — máy **đang chờ người dùng bấm "Cho phép gỡ
     * lỗi USB"**. Chờ thêm là có nghĩa.
     */
    AWAITING_APPROVAL,

    /** adbd trả `AUTH` lần nữa = từ chối khoá (đã bấm Từ chối, hoặc khoá bị gỡ khỏi `adb_keys`). */
    AUTH_REJECTED,

    /** Không có gì lắng nghe cổng 5555 (adb-tcp chưa bật). Chờ trong 30 s cũng vô ích — phải báo owner. */
    PORT_CLOSED,

    /** Nối được rồi hỏng giữa chừng (đứt socket / EOF). Một số ROM đóng socket thay vì trả `AUTH`. */
    IO_ERROR,

    /** Không phân loại được — giữ nguyên để không giả vờ biết. */
    UNKNOWN,

    /**
     * READY-AT-HOME (spec §4.6) — cổng thi hành CHẶN phiên NỀN này TRƯỚC khi mở socket: kênh chưa được đo là lên trong
     * tiến trình và không có dấu duyệt còn tươi. Không có kết nối nào được mở ⇒ adbd không thấy khoá ⇒ không có hộp
     * "Cho phép gỡ lỗi USB?" nào bung từ nền (R1.1). Đường HỎI ([LocalShellRetry.mayPromptUser]) không bao giờ nhận lý
     * do này. Việc cần làm của người dùng giống [AWAITING_APPROVAL]: mở màn chính để cấp quyền.
     */
    NOT_APPROVED,
}
