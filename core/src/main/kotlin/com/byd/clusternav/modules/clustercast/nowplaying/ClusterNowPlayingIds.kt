package com.byd.clusternav.modules.clustercast.nowplaying

/**
 * ═══ 3.1.11 · CLUSTER-NOW-PLAYING — số liệu ĐỜI XE của ô "tên bài hát" trên cụm (`ClusterProfile.nowPlaying`) ═══════════
 *
 * Nguồn: `docs/diagnostics/cluster-now-playing-2026-10-16.md` §1(3)–(5). Mọi số ở đây là của **fw 2606** (Seal DiLink3):
 *  • feature-id [ĐO DiCarServer `Instrument.java:602-607` + featmap xe 16/09];
 *  • tập nguồn cụm cho HIỆN [ĐO disasm `libBydDataSource.so` `BusinessUi1::updateMediaInfoDisplay` 0x120d60–0x120dcc] khớp
 *    độc lập với bộ icon `mediaIcon<nguồn+3>` có trong `cluster_theme1/2.rcc` [ĐO liệt kê rcc];
 *  • nguồn ghi đè 11 = `MUSIC_SOURCE_OTHERS` của chính SDK BYD [ĐO stub `BYDAutoInstrumentDevice.java:553`];
 *  • 26 = nguồn `com.byd.mediacontroller` gán cho app ngoài bảng của nó [ĐO `OtherClient.java:168-188`] — giá trị trả về
 *    khi không đọc được nguồn trước lúc ghi đè.
 *
 * Đời xe khác (DiLink 5, firmware 2602/2506, cụm 8.8"…) **chưa đọc** tập nguồn của cụm ⇒ hồ sơ của chúng KHÔNG mang bảng này
 * (`null`) ⇒ tính năng báo "đầu xe này chưa hỗ trợ" (CLAUDE.md §7 — khác biệt đời xe nằm ở hồ sơ, không rải trong mã).
 *
 * ⚠ Toàn bộ là đường **ghi tín hiệu hiển thị HAL device 1007** — không display, không task, không stack (CLAUDE.md §4).
 * Tính năng đi ra NGỦ (công tắc mặc định TẮT) cho tới khi lượt đo xe W1/W2 chốt (sai lệch §14 owner duyệt 16/10).
 */
data class ClusterNowPlayingIds(
    /** `INSTRUMENT_MUSIC_SOURCE_SET` — nguồn nhạc (int). */
    val sourceId: Int,
    /** `INSTRUMENT_MUSIC_INFO_SET` — tên bài, buffer UTF-16LE. */
    val titleId: Int,
    /** `INSTRUMENT_MUSIC_STATE_SET` — 1 phát · 2 dừng · 3 stop. */
    val stateId: Int,
    /** Nguồn ghi đè: phải nằm trong [shownSources] (kiểm ở `init`). */
    val overrideSource: Int,
    /** Tập nguồn mà cụm CHO HIỆN ô nhạc. Ngoài tập ⇒ cụm ẩn cả icon lẫn tên. */
    val shownSources: Set<Int>,
    /** Giá trị trả về khi không đọc được nguồn trước lúc ghi đè. */
    val restoreFallback: Int,
    /** Trần byte của tên bài (MediaController cắt 255 byte — `MediaInfoSender.java:186-198`). */
    val titleMaxBytes: Int = 255,
    val statePlaying: Int = 1,
    val statePaused: Int = 2,
) {
    init {
        require(overrideSource in shownSources) { "nguồn ghi đè $overrideSource phải nằm trong tập cụm cho hiện" }
        require(restoreFallback in SOURCE_RANGE && overrideSource in SOURCE_RANGE) { "nguồn ngoài $SOURCE_RANGE" }
        require(titleMaxBytes >= 2) { "trần byte tên bài quá nhỏ" }
    }

    /** Cụm có hiện ô nhạc với nguồn [source] không. */
    fun shown(source: Int): Boolean = source in shownSources

    companion object {
        /** Miền hợp lệ của một mã nguồn (byte CAN); đọc ra ngoài miền này = sentinel/rác ⇒ coi như không đọc được. */
        val SOURCE_RANGE: IntRange = 0..255

        /** Seal DiLink3 fw 2606 — xem KDoc lớp. Nguồn 1…19 trừ 8 (VIDEO), 22, 29, 30, 171. */
        val FW2606: ClusterNowPlayingIds = ClusterNowPlayingIds(
            sourceId = 0x33F00030,
            titleId = 0x43FB1008,
            stateId = 0x43E0000A,
            overrideSource = 11,
            shownSources = ((1..19).toSet() - 8) + setOf(22, 29, 30, 171),
            restoreFallback = 26,
        )
    }
}
