package com.byd.clusternav.net

import java.net.HttpURLConnection
import java.net.URL

/**
 * ═══ MỘT CỬA MỞ KẾT NỐI HTTP ═════════════════════════════════════════════════════════════════════════════════
 *
 * CLAUDE.md §4.1 — *"pattern lặp ≥ 2 lần → extract shared module NGAY"*. Từ V1 pha NGHE, app có **hai** thứ tải
 * từ Internet: gói APK cập nhật ([com.byd.clusternav.UpdateChecker]) và mô hình nhận dạng
 * ([com.byd.clusternav.launcher.voice.VoiceModelStore]). Cả hai cần đúng một bộ thiết lập, và bộ ấy mang những
 * quyết định **không được lệch nhau**:
 *  • **thời hạn chờ hữu hạn** ở cả hai chiều — mạng của xe hay treo nửa chừng (cắm CarPlay là WiFi tắt, §11);
 *    thiếu `readTimeout` thì một luồng nền đứng vĩnh viễn giữ nguyên 32 MB bộ nhớ đệm;
 *  • **theo chuyển hướng** — alphacephei và GitHub đều trả 302 sang CDN;
 *  • **chỉ HTTPS** — đây là chốt cứng, xem [open].
 *
 * ⚠ Lớp này cố ý **KHÔNG** đọc dữ liệu và **KHÔNG** đóng kết nối: chỗ gọi quyết định đọc ra sao (một chuỗi JSON
 * hay một luồng 32 MB đi qua bộ băm) và chịu trách nhiệm `disconnect()`. Gói cả việc đọc vào đây thì hai chỗ gọi
 * sẽ cần hai kiểu trả về khác nhau, tức lại tách ra làm hai.
 */
object HttpConn {

    /** Nhãn tự giới thiệu — giữ NGUYÊN chuỗi cũ để nhật ký máy chủ của dự án không đứt mạch. */
    private const val UA = "ClusterNav-Updater"

    private const val CONNECT_TIMEOUT_MS = 15_000

    /**
     * Mở một kết nối GET đã cấu hình xong.
     *
     * @param readTimeoutMs thời hạn cho MỘT lượt đọc. Tải tệp lớn đặt dài hơn đọc JSON, nhưng **luôn hữu hạn**.
     * @param accept giá trị `Accept`, `null` nếu máy chủ không cần.
     * @throws IllegalArgumentException nếu [url] không phải HTTPS.
     *
     * ## Vì sao chặn HTTP thường ở ĐÂY, không ở từng chỗ gọi
     * Hai thứ đi qua cửa này đều là **mã/dữ liệu sẽ chạy trên xe** (một tệp APK, một mô hình nhận dạng). Trên
     * `http://` thì bất kỳ ai trên đường truyền cũng thay được nội dung. Kiểm ở từng chỗ gọi nghĩa là chỗ gọi
     * thứ ba sẽ quên — chốt ở cửa duy nhất thì không quên được. (Băm sha256 vẫn phải có: nó chặn cả ca máy chủ
     * bị đổi nội dung, thứ mà HTTPS không nói gì tới.)
     */
    fun open(url: String, readTimeoutMs: Int, accept: String? = null): HttpURLConnection {
        require(url.startsWith("https://")) { "chỉ nhận HTTPS: $url" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            accept?.let { setRequestProperty("Accept", it) }
        }
    }
}
