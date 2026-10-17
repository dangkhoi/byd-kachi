package com.byd.clusternav.modules.clustercast.nowplaying

/**
 * Khoá ĐỜI CŨ của bộ ghi 3.1.11 (đã gỡ ở 3.1.12 — [ClusterNowPlayingLegacy]). Còn lại cho MỘT bản để lượt khởi động dọn được:
 * công tắc (bị xoá khỏi prefs) và dấu bền (trả nguồn cụm rồi xoá). Cả hai THEO XE, không có hàng Cài đặt.
 * Gỡ cùng [ClusterNowPlayingLegacy] ở 3.1.13.
 */
object ClusterNowPlayingKeys {

    /** Công tắc 3.1.11 *"Tên bài hát lên cụm đồng hồ"* — không còn hàng; lượt khởi động xoá khoá. */
    const val SWITCH_KEY = "cluster_now_playing"

    /** Dấu bền "Kachi đang ghi đè nguồn nhạc cụm" của 3.1.11. */
    const val MARKER_KEY = "cluster_now_playing_marker"

    val DEVICE_KEYS: Map<String, String> = mapOf(
        SWITCH_KEY to "3.1.11 — công tắc bộ ghi nguồn 11 đã GỠ ở 3.1.12; còn khai cho một bản để lượt khởi động xoá khoá (theo XE " +
            "như lúc có hàng)",
        MARKER_KEY to "3.1.11 — dấu bền 'đang ghi đè nguồn nhạc cụm' + nguồn cũ để trả lại: trạng thái đã áp vào BUS của chiếc xe " +
            "này, không phải lựa chọn; 3.1.12 trả lại + xoá lúc khởi động",
    )

    val NOT_SETTINGS: Map<String, String> = mapOf(
        SWITCH_KEY to "đời cũ (bộ ghi 3.1.11 đã gỡ) — chỉ còn để lượt khởi động xoá, không có hàng Cài đặt",
        MARKER_KEY to "trạng thái máy (dấu bền đường trả lại của bộ ghi 3.1.11), không có hàng Cài đặt",
    )
}
