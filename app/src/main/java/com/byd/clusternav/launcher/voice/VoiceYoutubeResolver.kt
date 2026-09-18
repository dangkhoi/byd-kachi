package com.byd.clusternav.launcher.voice

import android.util.Log
import com.byd.clusternav.net.HttpConn
import java.net.URLEncoder

/**
 * ═══ Giải `video_id` bài đầu từ YouTube — TẦNG MẠNG (đường Android/HTTP) ══════════════════════════════════════
 *
 * Mục tiêu owner 2026-09-18: *"search ra kết quả và mở PLAY bài hát luôn"*. Cơ chế = **của Kiki** (xem KDoc
 * [YoutubeSearchParse]) nhưng Kachi không có server nên làm on-device: GET HTML trang tìm kiếm → bóc id
 * ([YoutubeSearchParse.firstVideoId]) → chỗ gọi mở `watch?v=<id>` (tự phát). Logic bóc nằm ở `:core` (test được);
 * đây chỉ lo mạng + thời hạn.
 *
 * Degrade-safe: mọi lỗi (mạng, không khớp, quá hạn) ⇒ `null` ⇒ chỗ gọi lùi về `MEDIA_PLAY_FROM_SEARCH` (không
 * regression). ⚠ scrape ⇒ mong manh theo markup YouTube; regex hẹp + đường lùi là hai lớp phòng.
 */
object VoiceYoutubeResolver {
    private const val TAG = "YtResolve"
    private const val READ_TIMEOUT_MS = 6_000
    private const val TOTAL_BUDGET_MS = 7_000L
    private const val MAX_CHARS = 600_000   // videoId bài đầu nằm sớm trong `ytInitialData` — không cần đọc cả trang
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** video_id bài đầu cho [query], hoặc `null` (mạng/không khớp). Chạy blocking — gọi từ luồng nền. */
    fun firstVideoId(query: String): String? = runCatching {
        val url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
        val conn = HttpConn.open(url, READ_TIMEOUT_MS, accept = "text/html")
        // Ghi đè UA "updater" thành UA trình duyệt + bỏ qua trang đồng ý EU, nếu không YouTube trả trang khác.
        conn.setRequestProperty("User-Agent", BROWSER_UA)
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        conn.setRequestProperty("Cookie", "CONSENT=YES+1")
        try {
            if (conn.responseCode != 200) { Log.w(TAG, "HTTP ${conn.responseCode} cho \"$query\""); return null }
            val sb = StringBuilder()
            val buf = CharArray(16_384)
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                var n = r.read(buf)
                while (n >= 0 && sb.length < MAX_CHARS) { sb.append(buf, 0, n); n = r.read(buf) }
            }
            YoutubeSearchParse.firstVideoId(sb.toString())
        } finally {
            conn.disconnect()
        }
    }.getOrElse { Log.w(TAG, "giải video_id lỗi cho \"$query\"", it); null }

    /**
     * [firstVideoId] có **thời hạn CỨNG** [TOTAL_BUDGET_MS] — mạng xe treo nửa chừng không được giữ luồng nền mãi.
     * Luồng phụ là daemon (ca treo là điều kiện mạng cố định, không nên níu tiến trình). Quá hạn ⇒ `null` ⇒ lùi.
     */
    fun firstVideoIdBounded(query: String): String? {
        var out: String? = null
        val worker = Thread { out = firstVideoId(query) }.apply { isDaemon = true; start() }
        worker.join(TOTAL_BUDGET_MS)
        if (worker.isAlive) Log.w(TAG, "giải video_id quá hạn ${TOTAL_BUDGET_MS}ms cho \"$query\" — lùi search-play")
        return out
    }
}
