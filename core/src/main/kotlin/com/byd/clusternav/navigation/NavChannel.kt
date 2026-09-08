package com.byd.clusternav.navigation

/**
 * Kênh mà một khung dẫn đường đến từ đó — để [SourceArbiter] ưu tiên KÊNH DỮ LIỆU hơn ẢNH (R6, spec B3).
 *
 * - [DATA]  : kênh dữ liệu có cấu trúc — Waze HLP/1, widget VietMap (ALERT_FULL), notification/a11y GMaps.
 *             Khi còn tươi thì LUÔN thắng ảnh.
 * - [IMAGE] : nguồn screen-capture + xử lý ảnh (B3). Là FALLBACK — chỉ được nuôi cụm khi kênh data của
 *             CÙNG app đã im/cũ (stale).
 */
enum class NavChannel { DATA, IMAGE }
