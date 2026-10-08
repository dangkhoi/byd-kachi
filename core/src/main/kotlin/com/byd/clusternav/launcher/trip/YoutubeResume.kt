package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.voice.VoiceAppTarget
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.YoutubeSearchParse
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * ═══ 2.94 · R3 — YOUTUBE PHÁT TIẾP: quyết định thuần (`:core`) ═══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-294-plan.html` R3 · §4.3. Bên thi hành `:app`: `YoutubeResumeSampler` (lưu) + `TripMusicResume`
 * (lên xe: tìm → mở → chờ đúng bài → `seekTo`).
 *
 * ## Vì sao lưu TIÊU ĐỀ + KÊNH, không lưu link
 * [ĐO xe 07/10] phiên YouTube chỉ có TITLE · ARTIST · ALBUM_ARTIST · DURATION · cỡ video — KHÔNG `mediaId`/URI.
 * [ĐO máy ảo 07/10, YouTube 21.35.442] không nguồn ngầm nào cho link video đang phát (phiên, queue, extras, thông báo,
 * PendingIntent, logcat, cây a11y). ⇒ lên xe tìm lại bằng `"<tiêu đề> <kênh>"` qua CHÍNH bộ tìm của ô "Phát gì"
 * (`VoiceYoutubeResolver`) — [ĐO máy ảo 07/10] ra đúng video, tiêu đề khớp từng chữ 3/3 (quốc tế · M/V Việt · live có “|”).
 * Bài đầu KHÁC tiêu đề đã lưu ⇒ [matches] sai ⇒ KHÔNG phát (lùi về chỉ mở app, như trước 2.94) — không bao giờ phát nhầm.
 *
 * ## Vì sao tua bằng `seekTo`, không bằng `&t=`
 * [ĐO máy ảo 07/10] `watch?v=…&t=12s` qua VIEW KHÔNG nhảy giây; `TransportControls.seekTo(98871)` sau khi phiên hiện ⇒
 * 4 s sau `position=98871`. Phiên YouTube có `SEEK_TO` trong `actions`.
 *
 * ## Phạm vi: THEO XE (tệp `clusternav_state`, khai [TripGate.DEVICE_KEYS])
 * Bài đang phát là SỰ THẬT của app YouTube trên chiếc xe này (một app, một lịch sử xem), không phải lựa chọn của một hồ sơ;
 * theo hồ sơ thì đổi hồ sơ giữa hai chuyến là mất bài. Không đi theo xuất/nhập hồ sơ ⇒ tiêu đề người dùng xem không rời máy.
 * Bên lưu chỉ chạy khi hồ sơ đang dùng CẦN nó ([wanted]) — người không dùng tính năng thì không có gì được ghi.
 */
object YoutubeResume {

    /** Khoá theo XE (tệp `clusternav_state`). Đổi tên = mất bài đã lưu của máy đang chạy. */
    const val KEY = "kachi_yt_resume"

    /**
     * Bài lưu cũ hơn chừng này ⇒ không phát tiếp (lùi về chỉ mở app). [ĐOÁN] 3 ngày: đủ phủ một cuối tuần không lái; lâu
     * hơn thì người lái gần như chắc không còn nhớ/không muốn bài cũ. Chưa có số đo — owner đổi được bằng một hằng.
     */
    const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    /**
     * Còn ít hơn chừng này tới hết video ⇒ coi như đã xem xong ⇒ không phát tiếp. [ĐOÁN] 15 s: YouTube tự chuyển video kế
     * ở cuối, mà mẫu lưu mỗi [SAMPLE_EVERY_MS] có thể chưa kịp bắt video kế trước khi máy tắt — mở lại video vừa xong rồi
     * tua về cuối (hoặc về 0) đều không phải điều người lái muốn.
     */
    const val END_GUARD_MS = 15_000L

    /** Vị trí nhỏ hơn chừng này ⇒ không tua (mở video là đã ở gần đó; một lệnh `seekTo` thừa chỉ thêm rủi ro). */
    const val MIN_SEEK_MS = 5_000L

    /**
     * Thời lượng ngắn hơn chừng này ⇒ KHÔNG lưu (soát 2.94 R3 · P2 quảng cáo): quảng cáo YouTube 6–30 s; nếu trong lúc quảng
     * cáo phiên mang tiêu đề + thời lượng của quảng cáo thì lượt lên xe sau sẽ tìm và phát đúng quảng cáo ấy. [CHƯA BIẾT] phiên
     * trông thế nào lúc quảng cáo — [ĐO máy ảo 07/10] 4 video không ra quảng cáo nào, không đo được ⇒ chặn bằng thời lượng (rẻ,
     * không đoán tiêu đề). Live (thời lượng 0) vẫn lưu.
     */
    const val MIN_DURATION_MS = 60_000L

    /** Nhịp lưu (spec R3: *"≈ mỗi phút"*). */
    const val SAMPLE_EVERY_MS = 60_000L

    /**
     * 2.97 · YT-SAVE-ON-CHANGE — phiên báo ĐỔI BÀI ⇒ lưu sau chừng này (đợi tiêu đề/thời lượng/vị trí của bài mới ổn định; nhiều
     * lần đổi liền nhau gộp làm MỘT lượt). Anh em báo 08/10 (2.96): lên xe phát tiếp "không đúng bài" — [SUY] nghe Mix, bài vừa
     * chuyển mà chưa tới nhịp [SAMPLE_EVERY_MS] đã tắt máy ⇒ mẫu cuối còn là bài TRƯỚC.
     */
    const val CHANGE_SETTLE_MS = 2_000L

    /** Gói của mọi app đích phát tiếp — bên lưu chỉ nghe sự kiện đổi bài của đúng các phiên này. */
    fun watchedPackages(): Set<String> = watchedTargets().flatMap { it.packages }.toSet()

    /** Nhật ký lưu: một dòng mỗi lần đổi bài, không thì tối đa một dòng mỗi chừng này. */
    const val LOG_EVERY_MS = 5L * 60 * 1000

    /** Một phiên đọc từ `MediaController` (bên `:app` dịch). [updatedElapsedMs] = `PlaybackState.lastPositionUpdateTime` (đồng hồ `elapsedRealtime`). */
    data class Live(
        val pkg: String,
        val title: String?,
        val channel: String?,
        val playing: Boolean,
        val positionMs: Long,
        val updatedElapsedMs: Long,
        val speed: Float,
        val durationMs: Long,
    )

    /** Bài đã lưu. [target] = mã app trong bảng [VoiceAppTargets] (không phải tên gói). [savedAtMs] = giờ TƯỜNG lúc lưu. */
    data class Saved(
        val target: String,
        val title: String,
        val channel: String,
        val positionMs: Long,
        val durationMs: Long,
        val savedAtMs: Long,
    )

    /** Kế hoạch phát tiếp: tìm [query], một kết quả phải khớp [title] ([choose], ưu tiên cùng [channel]), rồi tua tới [seekMs] (0 = không tua). */
    data class Plan(val query: String, val title: String, val seekMs: Long, val channel: String = "")

    /**
     * App cần Kachi phát tiếp hộ = kiểu nhạc PHÁT được mà app KHÔNG tự phát tiếp ([TripMusicMode.resumable], dữ liệu của
     * kiểu) — hôm nay đúng YouTube. Không tên gói nào trong logic (CLAUDE.md §7): gói lấy từ bảng [VoiceAppTargets].
     */
    fun watchedTargets(): List<VoiceAppTarget> =
        TripMusicMode.values().filter { it.plays && !it.resumable }.mapNotNull { VoiceAppTargets.byKey(it.targetKey) }

    /** Cấu hình nhạc lên xe này có dùng phát tiếp không: kiểu không tự phát tiếp + ô "Phát gì" TRỐNG. */
    fun wanted(music: TripMusic): Boolean =
        music.mode.plays && !music.mode.resumable && TripMusicPlan.source(music.query) == TripMusicPlan.Source.None

    /** Phiên của một app trong [targets] (ưu tiên phiên đang phát) kèm đích của nó; không có ⇒ `null`. */
    fun pick(sessions: List<Live>, targets: List<VoiceAppTarget>): Pair<VoiceAppTarget, Live>? {
        val mine = sessions.mapNotNull { s -> targets.firstOrNull { s.pkg in it.packages }?.let { it to s } }
        return mine.firstOrNull { it.second.playing } ?: mine.firstOrNull()
    }

    /**
     * Một mẫu cần lưu, hoặc `null` (không lưu gì): phiên phải ĐANG PHÁT và có tiêu đề. Vị trí = vị trí phiên báo + phần đã
     * chạy từ lúc phiên cập nhật (`(now − updated) × speed`, chuẩn `PlaybackState`), kẹp vào `[0, thời lượng]` (thời lượng
     * ≤ 0 = live/không rõ ⇒ chỉ kẹp dưới). Đồng hồ lùi ([nowElapsedMs] < updated) ⇒ không cộng gì.
     */
    fun sample(target: VoiceAppTarget, live: Live, nowWallMs: Long, nowElapsedMs: Long): Saved? {
        if (!live.playing || live.pkg !in target.packages) return null
        val title = clean(live.title)
        if (title.isEmpty()) return null
        if (live.durationMs in 1 until MIN_DURATION_MS) return null
        val ran = if (live.updatedElapsedMs > 0) (nowElapsedMs - live.updatedElapsedMs).coerceAtLeast(0L) else 0L
        val rate = live.speed.takeIf { it.isFinite() && it > 0f } ?: 0f
        val raw = live.positionMs.coerceAtLeast(0L) + (ran * rate).toLong()
        val pos = if (live.durationMs > 0) raw.coerceIn(0L, live.durationMs) else raw.coerceAtLeast(0L)
        return Saved(target.key, title, clean(live.channel), pos, live.durationMs.coerceAtLeast(0L), nowWallMs)
    }

    /**
     * Lên xe: bài [saved] có phát tiếp được cho đích [targetKey] không.
     *  - không có / của app khác ⇒ `null`;
     *  - tuổi `|now − savedAt|` > [MAX_AGE_MS] ⇒ `null`. Tuổi ÂM (đồng hồ xe lùi — đầu xe có thể bật với giờ cũ trước khi
     *    lấy giờ mạng) vẫn nhận trong cùng biên: tìm theo tiêu đề tự kiểm bài còn tồn tại, sai cùng lắm là một bài cũ đúng;
     *  - còn < [END_GUARD_MS] tới hết video ⇒ `null` (KDoc [END_GUARD_MS]);
     *  - thời lượng ≤ 0 (live) hoặc vị trí < [MIN_SEEK_MS] ⇒ mở, không tua (`seekMs = 0`).
     */
    fun resumePlan(saved: Saved?, targetKey: String?, nowWallMs: Long): Plan? {
        if (saved == null || targetKey == null || saved.target != targetKey) return null
        val age = nowWallMs - saved.savedAtMs
        if (age > MAX_AGE_MS || age < -MAX_AGE_MS) return null
        if (saved.durationMs > 0 && saved.positionMs > saved.durationMs - END_GUARD_MS) return null
        val seek = if (saved.durationMs <= 0 || saved.positionMs < MIN_SEEK_MS) 0L else saved.positionMs
        val query = TripMusicCodec.clean("${saved.title} ${saved.channel}")
        return Plan(query, saved.title, seek, saved.channel)
    }

    /** Tiêu đề tìm được / phiên đang phát có đúng là bài đã lưu không: so sau NFC + chữ thường + gộp khoảng trắng. Rỗng ⇒ sai. */
    fun matches(found: String?, savedTitle: String?): Boolean {
        val a = norm(found)
        return a.isNotEmpty() && a == norm(savedTitle)
    }

    /**
     * 2.97 · R2 — chọn bài để phát trong [hits] (kết quả đầu của lượt tìm, theo thứ hạng): CHỈ bài có tiêu đề khớp ([matches]);
     * nhiều bài cùng tiêu đề ⇒ ưu tiên bài cùng kênh đã lưu, không có thì bài khớp hạng cao nhất (đúng luật 2.94 cho hạng 1).
     * Không bài nào khớp ⇒ `null` ⇒ không phát gì (luật "không bao giờ phát nhầm" giữ nguyên).
     */
    fun choose(hits: List<YoutubeSearchParse.Hit>, plan: Plan): YoutubeSearchParse.Hit? {
        val same = hits.filter { matches(it.title, plan.title) }
        return same.firstOrNull { plan.channel.isNotBlank() && matches(it.channel, plan.channel) } ?: same.firstOrNull()
    }

    /**
     * 2.97 · R2 — tổng thời gian còn thử tìm lại khi lượt đầu KHÔNG TỚI ĐƯỢC YouTube ([YoutubeSearchParse.Search.Offline]).
     * [ĐO máy ảo 08/10] không mạng ⇒ lượt tìm hỏng sau 19 ms và 2.96 bỏ luôn. [CHƯA BIẾT] mạng xe lên sau nổ máy bao lâu ⇒ [ĐOÁN]
     * 60 s (cùng cỡ chờ phiên + quảng cáo của bước nhạc); luôn bị kẹp thêm bởi hạn chuyến.
     */
    const val SEARCH_RETRY_BUDGET_MS = 60_000L

    /** Nhịp chờ lượt thử lại thứ [attempt] (0 = lần thử lại đầu): luỹ thừa 2 từ 2 s, trần 10 s, nhân jitter [jitter01] ∈ [0,1) vào nửa trên. */
    fun searchRetryDelayMs(attempt: Int, jitter01: Double): Long {
        val base = minOf(SEARCH_RETRY_MAX_DELAY_MS, SEARCH_RETRY_FIRST_DELAY_MS shl attempt.coerceIn(0, 10))
        val j = if (jitter01.isFinite()) jitter01.coerceIn(0.0, 1.0) else 0.0
        return (base * (0.5 + 0.5 * j)).toLong().coerceAtLeast(1L)
    }

    private const val SEARCH_RETRY_FIRST_DELAY_MS = 2_000L
    private const val SEARCH_RETRY_MAX_DELAY_MS = 10_000L

    /** Có nên ghi một dòng nhật ký cho lần lưu [next]: đổi bài ⇒ có; cùng bài ⇒ tối đa một dòng mỗi [LOG_EVERY_MS]. */
    fun shouldLog(prev: Saved?, next: Saved, lastLogWallMs: Long): Boolean =
        prev == null || !matches(prev.title, next.title) || next.savedAtMs - lastLogWallMs !in 0 until LOG_EVERY_MS

    /**
     * `v1|<đích>|<vị trí>|<thời lượng>|<lúc lưu>|<tiêu đề %>|<kênh %>`. Chữ đi qua [URLEncoder] ⇒ trên đĩa chỉ có
     * `[A-Za-z0-9.*_%+-]` + `|` (tiêu đề có “|” thành `%7C`) — không tab/xuống dòng.
     */
    fun encode(s: Saved): String =
        listOf(VERSION, s.target, s.positionMs, s.durationMs, s.savedAtMs, enc(s.title), enc(s.channel)).joinToString("|")

    /** Giải mã chặt: sai số phần / phiên bản / số / tiêu đề rỗng ⇒ `null` (không đoán một bài để phát). */
    fun decode(raw: String?): Saved? {
        val p = raw?.split('|') ?: return null
        if (p.size != 7 || p[0] != VERSION || p[1].isBlank()) return null
        val pos = p[2].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val dur = p[3].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val at = p[4].toLongOrNull() ?: return null
        val title = dec(p[5])?.let(::clean)?.takeIf { it.isNotEmpty() } ?: return null
        val channel = dec(p[6])?.let(::clean) ?: return null
        return Saved(p[1], title, channel, pos, dur, at)
    }

    private fun clean(s: String?): String = TripMusicCodec.clean(s.orEmpty())

    private fun norm(s: String?): String =
        Normalizer.normalize(clean(s), Normalizer.Form.NFC).lowercase(Locale.ROOT)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun dec(s: String): String? = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: IllegalArgumentException) {
        null
    }

    private const val VERSION = "v1"
}
