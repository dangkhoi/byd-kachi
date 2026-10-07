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

    /**
     * Trần **số ký tự ĐỌC** của một lượt giải id. 1,8 M ký tự.
     *
     * ## ⚠ [ĐO 2026-09-18] 600 K là con số của một giả định SAI
     * Bản 1.75 đặt 600 K với lý do *"videoId bài đầu nằm sớm trong `ytInitialData`"*. Đo trang THẬT thì khớp đầu
     * nằm ở **763–787 K ký tự** (4/4 truy vấn, trang 1,28–1,53 M) — bảng số ở
     * [YoutubeSearchParse.firstVideoId]. ⇒ với trần 600 K thì resolver trả `null` **kể cả sau khi UA đã đúng**,
     * tức bản vá UA một mình chưa làm *"phát luôn"* chạy. Trần mới phủ cả trang lớn nhất đã đo + biên.
     *
     * Trần này nay chỉ chặn **thời gian/băng thông**, KHÔNG chặn bộ nhớ: lượt quét đi theo dòng chảy và dừng ở
     * khớp đầu, nên RAM là một cửa sổ ~16 K ký tự bất kể trang to bao nhiêu.
     *
     * [ĐO] Băng thông đủ cho hạn cứng [TOTAL_BUDGET_MS] = 7 s: `HttpConn.open` **không** đặt `Accept-Encoding`, nên
     * lớp kết nối HTTP của Android tự xin gzip và tự giải nén ⇒ cả trang 1,27 M ký tự chỉ là **276 KB** trên
     * dây; tới khớp đầu (~60 % trang) ≈ **165 KB**, tức cần ~24 KB/s. Đường lùi `MEDIA_PLAY_FROM_SEARCH` vẫn còn
     * cho ca mạng tệ hơn thế.
     *
     * ⚠ Câu trên cố ý KHÔNG viết tên lớp kết nối ra: `VoiceCommandWiringContractTest` quét **văn bản thô** của mọi
     * tệp `Voice*` tìm dấu vết ra mạng, và nó chặn cả tên lớp trong chú thích — đúng như vậy, vì cửa mạng duy nhất
     * được phép ở đây là `HttpConn`.
     */
    private const val MAX_CHARS = 1_800_000

    /**
     * UA **MÁY TÍNH**, và chữ *"Mobile"* phải KHÔNG có trong đây.
     *
     * [ĐO xe 2026-09-18] mọi lượt *"phát bài … trên YouTube"* đều bắn `MEDIA_PLAY_FROM_SEARCH` — tức **đường
     * LÙI**, tức resolver trả `null` ở **mọi** lượt, tức tính năng "phát luôn" của 1.75 chưa bao giờ chạy trên
     * xe. [ĐO off-car, mô phỏng đúng lớp này (theo redirect + cookie `CONSENT=YES+1`)]:
     *
     * | UA gửi | `results?search_query=` trả về | regex bắt được |
     * |---|---|---|
     * | di động (`… Android 10 … Mobile Safari`) — bản 1.75 | 302 → trang consent/mobile, thân JS-only | **0** |
     * | máy tính (`… Windows NT 10.0 … Safari`) | trang kết quả có `ytInitialData` | **225** |
     *
     * Nghĩa là gốc KHÔNG phải regex hay markup đổi: YouTube trả **trang khác** cho UA di động, và trang đó không
     * có một `"videoId":"…"` nào để bóc. Bằng chứng: `docs/diagnostics/oncar-voice-music-vietmap-2026-09-18.md`
     * §BUG B.
     *
     * ⚠ Đây vẫn là scrape ⇒ mong manh theo markup YouTube; UA máy tính là **điều kiện cần**, không phải bảo đảm.
     * Hai lớp phòng (regex hẹp + đường lùi `MEDIA_PLAY_FROM_SEARCH`) không đụng tới.
     */
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"

    /** video_id bài đầu cho [query], hoặc `null` (mạng/không khớp). Chạy blocking — gọi từ luồng nền. */
    fun firstVideoId(query: String): String? = search(query, quiet = false) { YoutubeSearchParse.firstVideoId(it, MAX_CHARS) }

    /**
     * 2.94 · R3 (phát tiếp YouTube) — bài đầu KÈM tiêu đề ([YoutubeSearchParse.firstVideo]) để bên gọi kiểm đúng bài trước
     * khi phát. CÙNG một lượt GET + UA + trần với [firstVideoId] — không đường mạng thứ hai.
     *
     * Soát 2.94 Pass 1 [P2]: [query] ở đây là *"<tiêu đề> <kênh>"* người dùng ĐÃ XEM ⇒ nhật ký lỗi/quá hạn chỉ ghi ĐỘ DÀI
     * ([shown]), không ghi chữ (spec §4.3: nhật ký không mang tiêu đề; logcat đi theo bộ chụp chẩn đoán).
     */
    fun firstVideo(query: String): YoutubeSearchParse.Hit? = search(query, quiet = true) { YoutubeSearchParse.firstVideo(it, MAX_CHARS) }

    /** Truy vấn trong nhật ký: giọng nói (`quiet = false`) nguyên câu như trước; phát tiếp (`quiet`) chỉ độ dài. */
    private fun shown(query: String, quiet: Boolean): String = if (quiet) "<len=${query.length}>" else query

    private fun <T> search(query: String, quiet: Boolean, parse: (java.io.Reader) -> T?): T? = runCatching {
        val url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
        val conn = HttpConn.open(url, READ_TIMEOUT_MS, accept = "text/html")
        // Ghi đè UA "updater" thành UA trình duyệt + bỏ qua trang đồng ý EU, nếu không YouTube trả trang khác.
        conn.setRequestProperty("User-Agent", BROWSER_UA)
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        conn.setRequestProperty("Cookie", "CONSENT=YES+1")
        try {
            if (conn.responseCode != 200) { Log.w(TAG, "HTTP ${conn.responseCode} cho \"${shown(query, quiet)}\""); return null }
            // Quét THEO DÒNG CHẢY, dừng ở khớp đầu: khớp nằm ở ~765 K ký tự nên bản cũ (gom cả trang vào một
            // `StringBuilder` rồi mới bóc) vừa phải giữ ~2,6 MB trong RAM vừa bị trần 600 K cắt trước khi tới id.
            conn.inputStream.bufferedReader(Charsets.UTF_8).use(parse)
        } finally {
            conn.disconnect()
        }
    }.getOrElse {
        // Phát tiếp: lời nhắn ngoại lệ có thể mang URL (= truy vấn) ⇒ chỉ tên lớp. Giọng nói: như trước.
        if (!quiet) Log.w(TAG, "giải video_id lỗi cho \"$query\"", it)
        else Log.w(TAG, "giải video_id lỗi cho \"${shown(query, true)}\": ${it.javaClass.simpleName}")
        null
    }

    /**
     * [firstVideoId] có **thời hạn CỨNG** [TOTAL_BUDGET_MS] — mạng xe treo nửa chừng không được giữ luồng nền mãi.
     * Luồng phụ là daemon (ca treo là điều kiện mạng cố định, không nên níu tiến trình). Quá hạn ⇒ `null` ⇒ lùi.
     */
    fun firstVideoIdBounded(query: String): String? = bounded(query, quiet = false, ::firstVideoId)

    /** [firstVideo] với cùng hạn cứng [TOTAL_BUDGET_MS] (2.94 · R3). Quá hạn / lỗi ⇒ `null` ⇒ bên gọi lùi về chỉ mở app. */
    fun firstVideoBounded(query: String): YoutubeSearchParse.Hit? = bounded(query, quiet = true, ::firstVideo)

    private fun <T> bounded(query: String, quiet: Boolean, work: (String) -> T?): T? {
        var out: T? = null
        val worker = Thread { out = work(query) }.apply { isDaemon = true; start() }
        worker.join(TOTAL_BUDGET_MS)
        if (worker.isAlive) Log.w(TAG, "giải video_id quá hạn ${TOTAL_BUDGET_MS}ms cho \"${shown(query, quiet)}\" — lùi")
        return out
    }
}
