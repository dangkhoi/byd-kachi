package com.byd.clusternav.modules.clustercast.nowplaying

/**
 * 3.1.11 · CLUSTER-NOW-PLAYING — hai khoá của tính năng (tệp `clusternav_prefs`, `PrefsNowPlaying.kt` ở `:app`), cả hai THEO
 * XE. `ProfileScope.DEVICE_KEYS` cộng [DEVICE_KEYS]; `SettingsCatalog.NOT_SETTINGS` cộng [NOT_SETTINGS] (dấu bền không có hàng).
 */
object ClusterNowPlayingKeys {

    /** Công tắc *Cài đặt › Dẫn đường & cụm › Tên bài hát lên cụm đồng hồ*. */
    const val SWITCH_KEY = "cluster_now_playing"

    /** Dấu bền "Kachi đang ghi đè nguồn nhạc cụm" (`NowPlayingMarker`) — ghi TRƯỚC lần ghi đè đầu (CLAUDE.md §5). */
    const val MARKER_KEY = "cluster_now_playing_marker"

    /** NGỦ: tắt tới khi lượt đo xe W1/W2 chốt (owner 16/10 duyệt chuẩn bị mã trước tầng 1 §14 — chỉ với công tắc TẮT). */
    const val DEFAULT_ON = false

    val DEVICE_KEYS: Map<String, String> = mapOf(
        SWITCH_KEY to "3.1.11 — 'Tên bài hát lên cụm đồng hồ' (thử nghiệm, mặc định TẮT). Theo XE: nó bật một lượt ghi tín hiệu HAL " +
            "cụm mà chỉ phép đo trên CHIẾC XE này mới chốt được (W1/W2) — cùng họ `escape_return_enabled`; đổi hồ sơ không được bật lại",
        MARKER_KEY to "3.1.11 — dấu bền 'đang ghi đè nguồn nhạc cụm' + nguồn cũ để trả lại: trạng thái đã áp vào BUS của chiếc xe " +
            "này, không phải lựa chọn; theo hồ sơ thì đổi hồ sơ là mất đường trả",
    )

    val NOT_SETTINGS: Map<String, String> = mapOf(
        MARKER_KEY to "trạng thái máy (dấu bền đường trả lại của CLUSTER-NOW-PLAYING), không có hàng Cài đặt",
    )
}
