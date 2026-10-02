package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.voice.VoiceAppTarget
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceLaunch
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Kiểu "Tự mở nhạc khi lên xe" (F3). Owner 01/10: *"mở nhạc chọn auto là mở theo player xe sẵn, chả cần phải làm gì
 * đâu. Youtube hay Yt Music thì …"*. [targetKey] = mã app trong bảng DỮ LIỆU [VoiceAppTargets] (CLAUDE.md §7 — không
 * gắn tên gói trong logic); [plays] = Kachi có việc để làm không.
 */
enum class TripMusicMode(val code: String, val targetKey: String?) {
    OFF("off", null),
    CAR("car", null),
    YT_MUSIC("ytmusic", VoiceAppTargets.YT_MUSIC),
    YOUTUBE("youtube", VoiceAppTargets.YOUTUBE);

    val plays: Boolean get() = targetKey != null

    companion object {
        fun of(code: String?): TripMusicMode = values().firstOrNull { it.code == code } ?: OFF
    }
}

/** Cấu hình nhạc lên xe: kiểu + ô "Phát gì" ([query]: từ khoá hoặc link YouTube; rỗng = tiếp tục phiên của app). */
data class TripMusic(val mode: TripMusicMode, val query: String = "") {
    companion object {
        val OFF = TripMusic(TripMusicMode.OFF)
    }
}

/**
 * Mã hoá `ignition_music`: `<mode>|<query mã hoá phần trăm>`. Chữ người dùng gõ đi qua [URLEncoder] ⇒ chuỗi trên đĩa chỉ
 * có `[A-Za-z0-9.*_%+-]` + `|` — không tab/xuống dòng (dấu ngăn `ProfileTransfer`), không `;`.
 */
object TripMusicCodec {
    const val QUERY_MAX = 200

    fun encode(m: TripMusic): String {
        val q = clean(m.query)
        return if (q.isEmpty()) m.mode.code else "${m.mode.code}|${URLEncoder.encode(q, "UTF-8")}"
    }

    fun decode(raw: String?): TripMusic {
        if (raw.isNullOrBlank()) return TripMusic.OFF
        val cut = raw.indexOf('|')
        val code = if (cut < 0) raw.trim() else raw.substring(0, cut).trim()
        // Mã kiểu lạ (tệp nhập từ bản sau / sửa tay) ⇒ TẮT hẳn, bỏ cả chữ đi kèm — không đoán một kiểu để chạy.
        val mode = TripMusicMode.values().firstOrNull { it.code == code } ?: return TripMusic.OFF
        val q = if (cut < 0) "" else runCatching { URLDecoder.decode(raw.substring(cut + 1), "UTF-8") }.getOrDefault("")
        return TripMusic(mode, clean(q))
    }

    /** Ký tự điều khiển (tab, xuống dòng…) thành khoảng trắng, gộp khoảng trắng, cắt [QUERY_MAX]. */
    fun clean(q: String): String =
        q.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().replace(Regex("\\s+"), " ").take(QUERY_MAX)
}

/**
 * ═══ F3 — TỰ MỞ NHẠC KHI LÊN XE: quyết định thuần (`:core`) ══════════════════════════════════════════════════════════
 *
 * Spec R3.1–R3.7 · §4.6 (C7). Bên thi hành `:app` (`TripMusicRun`).
 *
 * ## Vì sao "phát gì" đi qua PHIÊN NHẠC, không qua ý-định VIEW [ĐO máy ảo 02/10, `trip/tm3-ytmusic.txt`]
 * Bản spec dự định K9 (`am start --display <màn ảo ô> -a VIEW -d <url> -p <gói>`). Đo YT Music 9.35.54: ý-định VIEW rơi
 * vào activity trung chuyển `MusicServiceDeepLinkActivity` trên màn ảo, rồi CHÍNH app mở `MusicActivity` bằng NEW_TASK
 * lên **display 0** (`am_focused_stack [0,0,164,0,reuseOrNewTask]`, KachiHome pause + stop) = **che màn nhà** — trái
 * owner *"không che home"*. Còn đường phiên nhạc [ĐO `trip/tm3s-ytmusic.txt`, `trip/tm3u-ytmusic.txt`]: app dàn vào ô
 * bằng MAIN/LAUNCHER ở lại màn ảo (0 sự kiện display 0); phiên của nó hiện ra ở trạng thái 2 (tạm dừng) kèm bài cũ;
 * `MediaController.play()` ⇒ 3 (phát); `playFromUri(watch?v=<id>)` ⇒ đổi đúng bài (*Despacito* → *Gangnam Style*),
 * 0 sự kiện cửa sổ; app bị app ô che vẫn phát. `playFromSearch` và link danh sách phát (`playlist?list=`) KHÔNG đổi bài
 * trong 15 s ⇒ không dùng: từ khoá đi qua bộ giải `video_id` CỦA GIỌNG NÓI (lõi chung) rồi `playFromUri`.
 *
 * ## Thứ tự "phát gì" (R3.4)
 *  1. Phiên của chính app đã có TRƯỚC khi Kachi đụng gì (app sống qua lần tắt máy) ⇒ `play()` — tiếp tục đúng thứ người
 *     lái đang nghe; bỏ qua ô "Phát gì".
 *  2. Ô "Phát gì" là link ⇒ bóc `video_id` ([videoIdOf]) ⇒ dựng lại URL chuẩn từ khuôn `watch` của bảng
 *     ([watchUrl]) — không đưa chuỗi người dùng đi đâu cả.
 *  3. Là từ khoá ⇒ lõi giải bài của giọng nói (`VoiceAppIntents.watchHandoff`) ⇒ URL ⇒ `playFromUri`.
 *  4. Không có gì / giải hỏng ⇒ phiên app vừa mở có thì `play()` (bài cũ của app); không có phiên ⇒ chỉ mở app.
 */
object TripMusicPlan {

    /** Chờ phiên nhạc của app vừa mở hiện ra (đo: ≈ 8–10 s từ lúc dàn). */
    const val SESSION_WAIT_MS = 15_000L
    const val SESSION_POLL_MS = 1_000L

    /** Một phiên nhạc đọc từ `MediaSessionManager` (bên `:app` dịch `PlaybackState` sang [playing]). */
    data class Session(val pkg: String, val playing: Boolean)

    enum class Gate { OFF, NOT_INSTALLED, UNKNOWN_MEDIA, OTHER_PLAYING, GO }

    /**
     * R3.2/R3.5 — có làm gì không. [pkg] = gói đã cài của app chọn (`null` = chưa cài). [sessions] `null` = không đọc
     * được (chưa có quyền nghe thông báo / lỗi) ⇒ CHƯA BIẾT ⇒ bỏ lượt (fail-safe, như `ScheduledNavApplier` với GPS).
     * Có phiên ĐANG PHÁT (của bất kỳ ai) hoặc [musicActive] (`AudioManager.isMusicActive`) ⇒ không đè.
     */
    fun gate(mode: TripMusicMode, pkg: String?, sessions: List<Session>?, musicActive: Boolean): Gate = when {
        !mode.plays -> Gate.OFF
        pkg == null -> Gate.NOT_INSTALLED
        sessions == null -> Gate.UNKNOWN_MEDIA
        musicActive || sessions.any { it.playing } -> Gate.OTHER_PLAYING
        else -> Gate.GO
    }

    /**
     * R3.4 bước 1 — phiên của [pkg] trong [before] có phải phiên CÓ TRƯỚC khi Kachi đụng gì (app sống qua lần tắt máy) không.
     * App nằm trong một ô đang hiện ([inSlot]) ⇒ KHÔNG BAO GIỜ: mỗi lần màn chính dựng ô, `VdAppHost` force-stop rồi mở lại
     * app của ô, nên phiên của nó là phiên Kachi vừa tạo — coi là "có trước" thì chuyến bỏ ô "Phát gì" và chỉ `play()` bài cũ
     * ([ĐO máy ảo 02/10, E2E `c6b-music-slot`]: `Force stopping …youtube.music` 08:14:00.849 → `MediaSession created`
     * 08:14:07 → chuyến `resume-existing`, bỏ link đã đặt — trái R3.4(1)).
     */
    fun preexisting(pkg: String, before: List<Session>?, inSlot: Boolean): Boolean =
        !inSlot && before.orEmpty().any { it.pkg == pkg }

    /** Kiểm lại NGAY TRƯỚC lệnh phát: app khác vừa phát ⇒ thôi; chính [pkg] đã tự phát ⇒ xong, không bắn gì. */
    enum class Recheck { OTHER_PLAYING, SELF_PLAYING, CLEAR, UNKNOWN_MEDIA }

    fun recheck(pkg: String, sessions: List<Session>?, musicActive: Boolean): Recheck = when {
        sessions == null -> Recheck.UNKNOWN_MEDIA
        sessions.any { it.playing && it.pkg != pkg } -> Recheck.OTHER_PLAYING
        sessions.any { it.playing && it.pkg == pkg } -> Recheck.SELF_PLAYING
        musicActive -> Recheck.OTHER_PLAYING
        else -> Recheck.CLEAR
    }

    sealed interface Source {
        object None : Source { override fun toString() = "None" }
        data class Video(val id: String) : Source
        data class Keyword(val q: String) : Source
        /** Trông như link YouTube nhưng không bóc được `video_id` (vd danh sách phát) — không dùng làm từ khoá. */
        object BadLink : Source { override fun toString() = "BadLink" }
    }

    fun source(query: String): Source {
        val q = TripMusicCodec.clean(query)
        if (q.isEmpty()) return Source.None
        if (!looksLikeLink(q)) return Source.Keyword(q)
        return videoIdOf(q)?.let { Source.Video(it) } ?: Source.BadLink
    }

    private fun looksLikeLink(q: String): Boolean =
        q.startsWith("http://") || q.startsWith("https://") || q.contains("youtube.com") || q.contains("youtu.be")

    /**
     * `video_id` (đúng 11 ký tự `[A-Za-z0-9_-]`) từ link YouTube/YT Music: `…/watch?v=<id>` · `youtu.be/<id>` ·
     * `…/shorts/<id>` · `…/live/<id>`. Host phải thuộc YouTube; mọi thứ khác ⇒ `null`.
     */
    fun videoIdOf(link: String): String? {
        val u = runCatching { URI(if (link.startsWith("http")) link else "https://$link") }.getOrNull() ?: return null
        val host = u.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = u.rawPath.orEmpty()
        val id = when (host) {
            "youtu.be" -> path.trim('/').substringBefore('/')
            "youtube.com", "music.youtube.com" -> when {
                path == "/watch" -> query(u.rawQuery, "v")
                path.startsWith("/shorts/") || path.startsWith("/live/") -> path.split('/').getOrNull(2)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { it.matches(VIDEO_ID) }
    }

    private fun query(raw: String?, key: String): String? =
        raw?.split('&')?.firstNotNullOfOrNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 && it[0] == key }?.get(1) }

    /** URL xem chuẩn của [target] cho [videoId] — CHÍNH khuôn `watch` mà giọng nói dùng (bảng [VoiceAppTargets]). */
    fun watchUrl(target: VoiceAppTarget, videoId: String): String? {
        if (!videoId.matches(VIDEO_ID)) return null
        val tpl = (target.watch as? VoiceLaunch.Uri)?.template ?: return null
        return tpl.replace(VoiceLaunch.SLOT, videoId).takeIf { safeWatchUrl(it) }
    }

    /** Rào cuối trước `playFromUri`: chỉ URL xem YouTube/YT Music với một `video_id` — chuỗi khác không đi đâu cả. */
    fun safeWatchUrl(url: String): Boolean = SAFE_WATCH.matches(url)

    /** Phát gì, sau khi app đã chắc chắn sống (R3.4 bước 2–4). */
    sealed interface Play {
        data class FromUri(val url: String) : Play
        object Resume : Play { override fun toString() = "Resume" }
        object OpenOnly : Play { override fun toString() = "OpenOnly" }
    }

    fun play(url: String?, hasSession: Boolean): Play = when {
        url != null && safeWatchUrl(url) && hasSession -> Play.FromUri(url)
        hasSession -> Play.Resume
        else -> Play.OpenOnly
    }

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private val SAFE_WATCH = Regex("https://(music|www)\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}")
}
