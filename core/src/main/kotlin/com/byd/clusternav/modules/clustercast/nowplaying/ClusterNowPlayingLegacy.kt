package com.byd.clusternav.modules.clustercast.nowplaying

/**
 * ═══ 3.1.12 · GỠ bộ ghi "nguồn 11" của 3.1.11 — chỉ còn ĐƯỜNG TRẢ LẠI lúc khởi động (giữ một bản, gỡ ở 3.1.13) ════════════
 *
 * [ĐO xe 17/10, logcat Seal khi YT Music phát] `com.byd.mediacontroller` (MediaInfoSenderC) tự ghi tên bài của YT Music vào
 * `0x43FB1008` (`sendMusicName result: 0`) cùng ảnh bìa, trạng thái, thời gian, với `MEDIA_SOURCE=999` ⇒ chuỗi tên bài của
 * hãng CHẠY; ô nhạc gốc chỉ bị **mặt chiếu của Kachi che** (owner). Bộ ghi đè nguồn 11 của 3.1.11 (`ClusterNowPlayingPlan`)
 * vì thế thừa và đã gỡ — thay bằng dải tên bài Kachi vẽ trên mặt chiếu (`CastNowPlayingStrip`). Doc
 * `docs/diagnostics/cluster-now-playing-2026-10-16.md` §9.
 *
 * Công tắc 3.1.11 mặc định TẮT và chưa từng bật trên xe nào, nhưng một bản thử có thể đã bật nó qua cầu `np`: nếu tiến trình
 * chết giữa lúc ghi đè, dấu bền (`NowPlayingMarker`) còn trên đĩa ⇒ lần khởi động đầu của 3.1.12 trả nguồn cũ rồi dọn
 * ([restoreAtStartup], cùng luật 3.1.11: chỉ ghi khi đọc lại thấy ĐÚNG nguồn ghi đè; không đọc được thì không ghi mù).
 * Không dấu ⇒ 0 việc HAL. CLAUDE.md §5: state đổi ngoài hệ thống phải có đường trả chạy được cả khi tiến trình đã chết.
 */
object ClusterNowPlayingLegacy {

    /** Miền hợp lệ của một mã nguồn (byte CAN); ra ngoài miền = sentinel/rác ⇒ coi như không đọc được. */
    val SOURCE_RANGE: IntRange = 0..255

    /** Cửa ra HAL + prefs + nhật ký (`:app`). Mọi hàm KHÔNG ném. */
    interface Sink {
        /** Đọc một feature int; `null` = không đọc được. */
        fun readInt(id: Int): Int?
        fun writeInt(id: Int, value: Int): String
        fun clearMarker()
        fun log(line: String)
    }

    /** Dấu bền 3.1.11: `<sourceId>:<prior>:<override>`. */
    data class Marker(val sourceId: Int, val prior: Int, val override: Int)

    fun decode(s: String): Marker? {
        val p = s.trim().split(':')
        if (p.size != 3) return null
        val id = p[0].toIntOrNull() ?: return null
        val prior = p[1].toIntOrNull()?.takeIf { it in SOURCE_RANGE } ?: return null
        val ov = p[2].toIntOrNull()?.takeIf { it in SOURCE_RANGE } ?: return null
        return Marker(id, prior, ov)
    }

    /**
     * Dấu bền còn ⇒ trả lại rồi dọn. Chỉ GHI khi đọc lại thấy đúng nguồn ghi đè (vẫn là của Kachi); đọc ra giá trị khác ⇒ đã
     * có người ghi, giữ; không đọc được ⇒ KHÔNG ghi mù. Dấu hỏng ⇒ chỉ dọn. `null` ⇒ không làm gì.
     * @return dòng nhật ký (ASCII), `null` khi không có dấu.
     */
    fun restoreAtStartup(marker: String?, sink: Sink): String? {
        if (marker == null) return null
        val m = decode(marker)
        val line = if (m == null) {
            "np startup marker hong — chi don"
        } else {
            val cur = sink.readInt(m.sourceId)?.takeIf { it in SOURCE_RANGE }
            val rc = if (cur == m.override) sink.writeInt(m.sourceId, m.prior) else "skip"
            "np startup restore src=0x%08x v=${m.prior} rc=$rc cur=${cur ?: "-"}".format(m.sourceId)
        }
        sink.clearMarker()
        sink.log(line)
        return line
    }
}
