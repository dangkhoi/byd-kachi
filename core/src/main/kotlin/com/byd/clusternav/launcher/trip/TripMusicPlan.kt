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
enum class TripMusicMode(val code: String, val targetKey: String?, val resumable: Boolean = false) {
    OFF("off", null),
    CAR("car", null),
    YT_MUSIC("ytmusic", VoiceAppTargets.YT_MUSIC, resumable = true),
    YOUTUBE("youtube", VoiceAppTargets.YOUTUBE);

    /*
     * [resumable] = app có "phát tiếp" khi ô "Phát gì" để trống (YT Music giữ hàng chờ — [ĐO máy ảo] e5/e4a; YouTube không có
     * API tiếp tục — [SUY] từ e3: không phiên nào trên trang chủ). CHỈ dùng cho câu gợi ý trong Cài đặt (L4 · D3(iii)); lúc chạy
     * nhánh do PHIÊN ĐO ĐƯỢC quyết ([TripMusicPlan.play]), không do kiểu/tên gói (CLAUDE.md §7).
     * 2.94 · R3: kiểu PHÁT được mà không [resumable] = Kachi phát tiếp hộ ([YoutubeResume.watchedTargets] — nhớ tiêu đề + vị
     * trí, lên xe tìm lại + `seekTo`); câu gợi ý Cài đặt nói điều đó.
     */

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
 *
 * ## L4 (owner 03/10 trên 2.86: *"auto mở nhạc youtube không chạy? Cả để trống lẫn để link"*) — phiên TRƯỚC, VIEW SAU
 * Đường phiên vẫn đứng trước (0 lệnh cửa sổ, đã đo). Nhưng YouTube nguội trên trang chủ KHÔNG có phiên ([ĐO máy ảo] e3)
 * ⇒ link không bao giờ tới được nó. Có link mà không phiên nhận URI ⇒ [Play.View]: K4-VIEW ([viewCmd]) — một ACTIVITY
 * (qua được cổng `relatestart` của BYD), dàn trên màn ảo như mọi lượt chạy ngầm; app trung chuyển thoát lên display 0
 * ⇒ dấu + K12 ngay (không chờ phát rồi mới về nhà). Nguồn xe đang phát KHÔNG còn chặn ([gate], D3(i)).
 */
object TripMusicPlan {

    /** Chờ phiên nhạc của app vừa mở hiện ra (đo: ≈ 8–10 s từ lúc dàn). */
    const val SESSION_WAIT_MS = 15_000L
    const val SESSION_POLL_MS = 1_000L

    /**
     * Một phiên nhạc đọc từ `MediaSessionManager` (bên `:app` dịch `PlaybackState` sang [playing]). [acceptsUri] = bit
     * `ACTION_PLAY_FROM_URI` (0x2000) trong `PlaybackState.actions` — L4 · D3: link chỉ giao qua phiên khi phiên NÓI là nhận.
     */
    data class Session(val pkg: String, val playing: Boolean, val acceptsUri: Boolean = true)

    enum class Gate { OFF, NOT_INSTALLED, UNKNOWN_MEDIA, SELF_PLAYING, GO }

    /**
     * R3.2 — có làm gì không. [pkg] = gói đã cài của app chọn (`null` = chưa cài). [sessions] `null` = không đọc được (chưa
     * có quyền nghe thông báo / lỗi) ⇒ CHƯA BIẾT ⇒ bỏ lượt: không đọc được phiên thì không chờ được "đang phát", không giao
     * được link qua phiên. Chính app chọn ĐANG PHÁT ⇒ xong, 0 lệnh.
     *
     * L4 · D3(i) — owner 01/10 *"Youtube hay Yt Music thì …"*: chọn app CỤ THỂ là lựa chọn của người dùng ⇒ THẮNG nguồn xe
     * tự phát lại lúc nổ máy ([ĐO firmware] BYD MediaAutoPlay tiếp tục nguồn cuối, `memory_play_back=1` trên xe owner 29/09).
     * Bản R3.5 cũ ("có nguồn khác đang phát ⇒ không đè") làm bước nhạc KHÔNG BAO GIỜ chạy trên xe có nguồn tự phát lại. Kiểu
     * *Theo player của xe* vẫn không làm gì ([TripMusicMode.CAR] không [TripMusicMode.plays]).
     */
    fun gate(mode: TripMusicMode, pkg: String?, sessions: List<Session>?): Gate = when {
        !mode.plays -> Gate.OFF
        pkg == null -> Gate.NOT_INSTALLED
        sessions == null -> Gate.UNKNOWN_MEDIA
        sessions.any { it.pkg == pkg && it.playing } -> Gate.SELF_PLAYING
        else -> Gate.GO
    }

    /** Nguồn KHÁC đang phát lúc bắt đầu (chỉ để sổ/nhật ký nói thật "đã giành từ nguồn xe" — không còn là cổng chặn). */
    fun otherPlaying(pkg: String, sessions: List<Session>?, musicActive: Boolean): Boolean =
        sessions.orEmpty().any { it.playing && it.pkg != pkg } || (musicActive && sessions.orEmpty().none { it.pkg == pkg && it.playing })

    /**
     * R3.4 bước 1 — phiên của [pkg] trong [before] có phải phiên CÓ TRƯỚC khi Kachi đụng gì (app sống qua lần tắt máy) không.
     * App nằm trong một ô đang hiện ([inSlot]) ⇒ KHÔNG BAO GIỜ: mỗi lần màn chính dựng ô, `VdAppHost` force-stop rồi mở lại
     * app của ô, nên phiên của nó là phiên Kachi vừa tạo — coi là "có trước" thì chuyến bỏ ô "Phát gì" và chỉ `play()` bài cũ
     * ([ĐO máy ảo 02/10, E2E `c6b-music-slot`]: `Force stopping …youtube.music` 08:14:00.849 → `MediaSession created`
     * 08:14:07 → chuyến `resume-existing`, bỏ link đã đặt — trái R3.4(1)).
     */
    fun preexisting(pkg: String, before: List<Session>?, inSlot: Boolean): Boolean =
        !inSlot && before.orEmpty().any { it.pkg == pkg }

    /**
     * Kiểm lại NGAY TRƯỚC lệnh phát: chính [pkg] đã tự phát ⇒ xong, không bắn gì; không đọc được ⇒ dừng. Nguồn khác vừa
     * phát (BYD tự phát lại trong lúc chờ phiên) KHÔNG còn chặn — L4 · D3(i), xem [gate].
     */
    enum class Recheck { SELF_PLAYING, CLEAR, UNKNOWN_MEDIA }

    fun recheck(pkg: String, sessions: List<Session>?): Recheck = when {
        sessions == null -> Recheck.UNKNOWN_MEDIA
        sessions.any { it.playing && it.pkg == pkg } -> Recheck.SELF_PLAYING
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
        /** L4 · D3(ii) — giao link bằng một ACTIVITY (K4-VIEW, [viewCmd]): app không có phiên, hoặc phiên không nhận URI. */
        data class View(val url: String) : Play
        object Resume : Play { override fun toString() = "Resume" }
        object OpenOnly : Play { override fun toString() = "OpenOnly" }
    }

    /**
     * Quyết bằng PHIÊN ĐO ĐƯỢC ([session] của chính app sau khi chờ, `null` = không có), không bằng tên gói (CLAUDE.md §7):
     *  - có link + phiên nhận URI ⇒ [Play.FromUri] (0 lệnh cửa sổ — [ĐO] T-M3u, e5);
     *  - có link mà không phiên / phiên không nhận URI ⇒ [Play.View] (L4 · D3(ii): YouTube nguội trên trang chủ không có phiên —
     *    [ĐO máy ảo] e3 `open-only (no session)` — nên đường phiên KHÔNG BAO GIỜ phát được link cho nó);
     *  - không link + có phiên ⇒ [Play.Resume]; không gì cả ⇒ [Play.OpenOnly] (sổ: `NO_SESSION`).
     */
    fun play(url: String?, session: Session?): Play {
        val link = url?.takeIf { safeWatchUrl(it) }
        return when {
            link != null && session != null && session.acceptsUri -> Play.FromUri(link)
            link != null -> Play.View(link)
            session != null -> Play.Resume
            else -> Play.OpenOnly
        }
    }

    /** L4 · D3(ii) — K4-VIEW đi ĐÂU ([viewRoute]). */
    sealed interface ViewRoute {
        /** App ở ô ⇒ CHÍNH màn ảo [vd] của ô đó (owner 01/10: *"có trong khung nào thì mở ở khung đó"*). */
        data class Slot(val vd: Int) : ViewRoute
        /** App không ở ô ⇒ chỗ dàn dựng của chuỗi chạy ngầm (ô sống khác, không thì màn ảo ẩn). */
        object Stage : ViewRoute { override fun toString() = "Stage" }
        /** App ở ô mà ô CHƯA có màn ảo (chưa mở xong) ⇒ 0 lệnh. */
        object SlotNotReady : ViewRoute { override fun toString() = "SlotNotReady" }
        /** A2 · 2.89 — app ngoài ô đang ĐỖ ở ô 7 (màn ảo ẩn [vd] của Kachi) ⇒ link vào CHÍNH màn ảo đó, như ô thật. */
        data class Parked(val vd: Int) : ViewRoute
    }

    /**
     * Review 287 [P2]: app ở ô ([inSlot]) ⇒ CHỈ màn ảo của ô đó, [slotVd] đọc MỚI lúc giao link (ảnh chụp đầu chuyến có thể có
     * TRƯỚC khi ô mở xong — `VdAppHost.stage()` = `null` tới lúc `launched`). Chưa có ⇒ [ViewRoute.SlotNotReady]: KHÔNG BAO GIỜ
     * dàn qua chỗ khác — K4-VIEW lên màn ảo khác kéo task của app ra khỏi ô của nó (`reparentToDisplay`, A10 r47
     * `ActivityStarter.java:2096-2170`, cùng cơ chế D4) rồi ô đi luật hoàn ô (mở lại = `force-stop`, cắt bài vừa phát).
     */
    fun viewRoute(inSlot: Boolean, slotVd: Int?, parkedVd: Int? = null): ViewRoute = when {
        inSlot && slotVd != null && slotVd >= 1 -> ViewRoute.Slot(slotVd)
        inSlot -> ViewRoute.SlotNotReady
        // A2 · 2.89: app ngoài ô mà đang ở ô 7 ⇒ CHÍNH màn ảo đỗ của nó. K4-VIEW lên một màn ảo KHÁC (chỗ dàn dựng) là kéo
        // task sang display khác (`reparentToDisplay`, [ĐO nguồn] A10 r47 `ActivityStarter.java:2104-2114` + `:2164-2171`) = dựng
        // lại activity = mất nhạc ([ĐO xe 05/10] YouTube); cùng display thì `moveTaskToFrontLocked` (`:2139-2144`), không dời.
        parkedVd != null && parkedVd >= 1 -> ViewRoute.Parked(parkedVd)
        else -> ViewRoute.Stage
    }

    /**
     * L4 · D3(ii) — K4-VIEW: mở [url] bằng ý-định VIEW nhắm ĐÚNG gói [pkg] lên màn ảo [vd] (ô của app, hoặc chỗ dàn dựng của
     * chuỗi chạy ngầm). Activity start đi qua cổng `relatestart` của BYD (chỉ chặn service/broadcast/provider — [ĐO firmware]).
     * Bốn câu CLAUDE.md §4:
     *  1. **Display** — CHỈ màn ảo do Kachi sở hữu (`vd ≥ 1`, đã đăng ký với cổng ownership của kênh); không bao giờ display 0.
     *  2. **App** — đúng gói [pkg] (`-p`, đã qua `ShellAppLauncher.PKG`); [url] chỉ là URL xem một video ([safeWatchUrl]) ⇒
     *     không có `'` nào thoát được cặp nháy.
     *  3. **Loại stack** — task `standard` của app (mới, hoặc task cũ bị kéo vào màn ảo — `reparentToDisplay`); không chạm
     *     stack hệ thống.
     *  4. **Hoàn tác** — app tự thoát lên display 0 (trung chuyển NEW_TASK, [ĐO] T-M3) ⇒ dấu + K12 (rào camera) đưa màn nhà lên;
     *     ở lại màn ảo dàn dựng ⇒ chuỗi BEHIND-HOME đẩy ra sau màn nhà; ở ô ⇒ ở ô.
     */
    fun viewCmd(vd: Int, url: String, pkg: String): String {
        require(vd >= 1) { "vd=$vd: K4-VIEW chỉ nhắm màn ảo của Kachi" }
        require(safeWatchUrl(url)) { "URL không phải link xem: $url" }
        require(pkg.matches(com.byd.clusternav.launcher.ShellAppLauncher.PKG)) { "tên gói lạ: $pkg" }
        return "am start --display $vd -a android.intent.action.VIEW -d '$url' -p $pkg"
    }

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private val SAFE_WATCH = Regex("https://(music|www)\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}")
}
