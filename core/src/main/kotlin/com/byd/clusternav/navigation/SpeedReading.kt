package com.byd.clusternav.navigation

/**
 * Quyết định "tốc độ đọc được là bao nhiêu" tách khỏi việc đọc.
 *
 * Việc đọc trên xe phải dùng reflection vào `android.hardware.bydauto.speed.BYDAutoSpeedDevice`, dựng một
 * Context bypass và miễn hidden-API — không kiểm được ngoài xe. Nhưng phần QUYẾT ĐỊNH thì thuần: đổi đơn
 * vị, chặn giá trị vô lý, và giữ giá trị tốt gần nhất. Trước 2026-07-27 cả hai nằm chung một object trong
 * `:app`, nên 80 dòng có bất biến an toàn ghi hẳn trong chú thích mà **không có bài kiểm nào**.
 *
 * Bất biến đang bảo vệ (W1-3, soát ngày 2026-07-21): **"không đọc được" phải là một giá trị RIÊNG, không
 * được giả dạng 0.** Trên đời xe mà HAL tốc độ không trả số, bản cũ trả 0.0 vĩnh viễn; mọi cổng an toàn
 * kiểu `speed < 2.0` với ý "xe đang đỗ" sẽ LUÔN mở, kể cả lúc đang chạy.
 */
class SpeedReading(private val maxPlausibleKmh: Double = 400.0) {

    /** Giá trị tốt gần nhất, chỉ dùng cho hiển thị và nội suy — không dùng cho cổng an toàn. */
    var lastGoodMps: Double = 0.0
        private set

    /**
     * Nhận số km/h thô từ thiết bị (null nếu không đọc được) và trả m/s, hoặc null nghĩa là KHÔNG BIẾT.
     *
     * Số âm hoặc lớn quá mức hợp lý là sentinel của HAL, không phải tốc độ — nên trả null, không trả 0.
     */
    fun acceptKmh(rawKmh: Double?): Double? {
        if (rawKmh == null) return null
        if (rawKmh.isNaN() || rawKmh.isInfinite()) return null
        if (rawKmh < 0 || rawKmh > maxPlausibleKmh) return null
        lastGoodMps = rawKmh / 3.6
        return lastGoodMps
    }

    /**
     * Dùng cho hiển thị/nội suy: suy biến về giá trị tốt gần nhất khi không đọc được.
     *
     * Cố ý KHÔNG có biến thể nào trả 0 khi chưa từng đọc được: giá trị khởi tạo 0.0 chỉ đúng nghĩa "chưa
     * biết gì" và người gọi cổng an toàn phải dùng [acceptKmh] để phân biệt.
     */
    fun mpsForDisplay(rawKmh: Double?): Double = acceptKmh(rawKmh) ?: lastGoodMps
}
