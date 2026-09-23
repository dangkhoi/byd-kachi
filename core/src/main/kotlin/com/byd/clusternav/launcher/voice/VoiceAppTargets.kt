package com.byd.clusternav.launcher.voice

import com.byd.clusternav.navigation.NavApps

/**
 * Cách **giao một chuỗi chữ mở** (tên bài / điểm đến) cho một app — mô tả bằng DỮ LIỆU, không phải bằng mã.
 *
 * ## Vì sao là dữ liệu, và vì sao nó nằm ở `:core` (nơi cấm `android.*`)
 * Thứ thật sự khác nhau giữa bảy app đích chỉ là **vài con chữ**: một `action` + tên `extra`, hoặc một khuôn URI.
 * Viết chúng thành bảy nhánh `if` ở `:app` thì (a) không bài kiểm off-car nào chạm tới được, và (b) mỗi lần một
 * phép đo cho kết quả mới lại phải sửa **mã** — đúng thứ CLAUDE.md §7 cấm. Ở dạng dữ liệu thì *"đổi một dòng"*
 * đúng nghĩa đen: một [VoiceLaunch] mới, không sửa một dòng logic nào.
 *
 * `:app` chỉ còn một hàm dịch bảng này thành `android.content.Intent`
 * ([com.byd.clusternav.launcher.VoiceAppIntents]).
 */
sealed interface VoiceLaunch {

    /**
     * Ý-định có **action + extra mang chuỗi tìm kiếm**, đã `setPackage(<gói>)`.
     *
     * @property action vd `android.media.action.MEDIA_PLAY_FROM_SEARCH` · `android.intent.action.SEARCH`.
     * @property extra tên extra mang chuỗi (`query` = `SearchManager.QUERY` — khai bằng **chuỗi** vì `:core` không
     *   được `import android.app.SearchManager`; giá trị hằng của nó là `"query"`, khoá lại bằng test ở `:app`).
     * @property extras extra CỐ ĐỊNH kèm theo. [ĐO] máy ảo 2026-09-14: YT Music **bắt buộc**
     *   `android.intent.extra.focus` = kiểu MIME `audio` (kèm dấu sao), thiếu nó thì ý-định chỉ mở màn Home
     *   của app — tức hỏng **im
     *   lặng**, đúng loại lỗi không ai phát hiện được nếu không đo.
     */
    data class Action(
        val action: String,
        val extra: String = QUERY_EXTRA,
        val extras: Map<String, String> = emptyMap(),
    ) : VoiceLaunch

    /**
     * Mở một **URI**; [template] chứa đúng một chỗ trống [SLOT] để thay chuỗi đã mã hoá phần trăm.
     *
     * vd `geo:0,0?q={q}` · `waze://?q={q}&navigate=yes`.
     */
    data class Uri(val template: String) : VoiceLaunch {
        init {
            require(template.contains(SLOT) || template.contains(LAT)) {
                "khuôn URI phải có ít nhất một chỗ trống ($SLOT hoặc $LAT): $template"
            }
        }

        /** Khuôn này cần TOẠ ĐỘ (⇒ phải geocode trước khi bắn). */
        val needsCoords: Boolean get() = template.contains(LAT) || template.contains(LNG)
    }

    /**
     * **Chỉ mở app, không giao gì** — và đó là một kết luận ĐÃ ĐO, không phải một chỗ chưa làm xong.
     *
     * [ĐO] máy ảo 2026-09-14, VietMap Live 3.4.0: app **không có cửa nào** nhận điểm đến (không đăng ký `geo:`;
     * `vietmaplive://` không mang tham số điểm đến; ép `geo:` kèm `-p vn.vietmap.live` ⇒ *unable to resolve
     * intent*). Chỗ gọi phải **nói ra** điều đó thay vì báo một dấu ✓ (xem [VoiceReply.navOpenedNoHandover]).
     */
    data object OpenOnly : VoiceLaunch

    companion object {
        /** Chỗ trống cho **chuỗi chữ** trong [Uri.template]. */
        const val SLOT = "{q}"

        /** Chỗ trống cho **vĩ độ / kinh độ** trong [Uri.template] (đường toạ độ — xem [VoiceAppTarget.coord]). */
        const val LAT = "{lat}"
        const val LNG = "{lng}"

        /** `SearchManager.QUERY` — hằng của nền tảng, khai bằng chuỗi vì `:core` cấm `android.*`. */
        const val QUERY_EXTRA = "query"

        /** `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`. */
        const val ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"

        /** `Intent.ACTION_SEARCH`. */
        const val ACTION_SEARCH = "android.intent.action.SEARCH"

        /** `MediaStore.EXTRA_MEDIA_FOCUS` — xem KDoc [Action.extras] về vì sao YT Music bắt buộc có nó. */
        const val EXTRA_MEDIA_FOCUS = "android.intent.extra.focus"

        /** Giá trị `EXTRA_MEDIA_FOCUS` cho *"tìm bất kỳ thứ gì nghe được"* — kiểu MIME `audio` kèm dấu sao. */
        const val FOCUS_ANY_AUDIO = "audio/*"
    }
}

/** App đích làm việc gì — quyết định nó được xét cho [VoiceIntent.Media] hay [VoiceIntent.Nav]. */
enum class VoiceAppKind { MUSIC, NAV }

/**
 * Mức bằng chứng cho **một dòng** của bảng (CLAUDE.md §2 — ba mức, không trộn).
 *
 * ⚠ Nói về *"đường giao chữ đã mô tả đúng chưa"*, KHÔNG nói về *"app có chạy tốt trên xe không"*. [MEASURED] cho
 * VietMap nghĩa là **đã đo được rằng app ấy không có cửa nhận điểm đến** — một sự thật đã chứng minh, dù kết quả
 * là "không làm được".
 */
enum class VoiceAppEvidence {
    /** [ĐO] đã chạy thật (máy ảo 2026-09-14 hoặc trên xe) và quan sát được kết quả. */
    MEASURED,

    /**
     * **Cơ chế đã chứng minh, kết quả CHỜ XE.** Đọc được từ nguồn một app đang chạy tốt trên chính xe owner, kèm
     * `file:line` — nhưng máy ảo không dựng lại được kết quả (thiếu GPS fix / bản app cũ).
     *
     * Tách khỏi [UNKNOWN] vì hai thứ khác hẳn nhau về hành động tiếp theo: cái này chỉ chờ **một lượt thử trên
     * xe**, còn [UNKNOWN] thì chưa biết bắt đầu từ đâu. Đúng ba mức bằng chứng của CLAUDE.md §2.
     */
    AWAITING_CAR,

    /** [CHƯA BIẾT] — app chưa cài ở đâu để đo, hoặc bản trên máy ảo chặn bằng màn *"Update your app"*. */
    UNKNOWN,
}

/**
 * Một app đích.
 *
 * @property key mã ổn định, là thứ [VoiceIntent.Media.app] / [VoiceIntent.Nav.app] mang theo.
 * @property label tên hiện cho người dùng (câu trả lời đọc tên này, không đọc mã).
 * @property packages tên gói ứng viên, **theo thứ tự ưu tiên**; gói đầu tiên có mặt trên máy sẽ được dùng.
 * @property launch đường giao chuỗi chữ.
 * @property fallback đường thử tiếp khi [launch] không có ai nhận (`null` = không có ⇒ chỉ còn mở app trơn).
 * @property evidence mức bằng chứng của [launch] — tầng trả lời **đọc nó** để khỏi hứa hão.
 * @property coord đường đi khi đã có **TOẠ ĐỘ** (`{lat}`/`{lng}`), `null` = app này không có đường toạ độ nào.
 * @property coordEvidence mức bằng chứng của riêng [coord].
 */
data class VoiceAppTarget(
    val key: String,
    val label: String,
    val kind: VoiceAppKind,
    val packages: List<String>,
    val launch: VoiceLaunch,
    val fallback: VoiceLaunch? = null,
    val evidence: VoiceAppEvidence = VoiceAppEvidence.UNKNOWN,
    val coord: VoiceLaunch.Uri? = null,
    val coordEvidence: VoiceAppEvidence = VoiceAppEvidence.UNKNOWN,
    /**
     * URL **watch** để TỰ PHÁT theo `video_id` đã giải (owner 2026-09-18 *"phát bài hát luôn"*). `{q}` = video_id
     * (không phải chuỗi tìm). Mở URL watch thì YouTube/YT Music tự phát đúng video — đúng cơ chế Kiki (server giải
     * id rồi mở `watch?v=`). `null` = app không có đường watch-theo-id ⇒ chỉ dùng [launch] (`MEDIA_PLAY_FROM_SEARCH`).
     */
    val watch: VoiceLaunch.Uri? = null,
) {
    /** Cách NÓI ra tên app này — khai một chỗ ở [VoiceSynonyms.APP_TARGETS] (xem KDoc ở đó). */
    val spoken: List<String> get() = VoiceSynonyms.APP_TARGETS[key].orEmpty()

    /** App này có cửa nhận chuỗi CHỮ không (`false` ⇒ phải có toạ độ, hoặc chỉ mở được app). */
    val handsOver: Boolean get() = launch != VoiceLaunch.OpenOnly

    /**
     * Đường **thật sự** dùng cho một điểm đến, theo đúng luật CLAUDE.md §6 (*"không đảo thứ đang chạy tốt"*).
     *
     * App nào đã có cửa CHỮ [ĐO] chạy (Google Maps · Waze) thì **giữ nguyên đường chữ**, dù bảng có sẵn đường
     * toạ độ: đổi sang một đường chưa đo để chữa cho một app khác đúng là lỗi §6 đã trả giá
     * (`wm size` đặt trước `wm overscan`). Đường toạ độ chỉ vào cuộc khi app **không có** cửa chữ nào — hôm nay
     * là đúng VietMap.
     *
     * @return `null` khi chưa có gì bắn được (⇒ chỉ mở app trơn và nói rõ).
     */
    fun destinationLaunch(hasCoords: Boolean): VoiceLaunch? = when {
        handsOver -> launch
        hasCoords -> coord
        else -> null
    }

    /** Điểm đến của app này có cần geocode trước không. */
    val needsCoords: Boolean get() = !handsOver && coord != null

    /** Gói đầu tiên có mặt trong [installed], hoặc `null` khi app chưa cài. */
    fun packageIn(installed: Set<String>): String? = packages.firstOrNull { it in installed }
}

/**
 * ═══ V1.1 · BẢNG ĐÍCH cho NHẠC và DẪN ĐƯỜNG ═══════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R17**. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bài toán: tới 1.49, câu *"phát bài Diễm Xưa"* chỉ được trả lời *"Kachi không tìm bài hát offline"*
 * Đúng ở thời điểm đó — Kachi **không** tự tìm bài. Nhưng owner 2026-09-14 hỏi thẳng: *"Có voice command mở nhạc
 * bằng yt music, youtube, dẫn đường bằng gmaps, vietmap, waze không?"*, và câu trả lời đúng là: Kachi **không cần
 * tự tìm** — nó chỉ cần giao nguyên văn chuỗi chữ cho app vốn đã làm việc đó. Vẫn là phương án C (RE Kiki §8.2:
 * *"từ vựng mở không phải việc của Kachi"*), chỉ khác ở chỗ trước đây ta dừng lại sớm một bước.
 *
 * ## Mỗi dòng ghi rõ **đã đo hay chưa**, và đó là cột quan trọng nhất
 * CLAUDE.md §2 + §14. [ĐO] toàn bộ bảng dò ngày **2026-09-14 trên máy ảo `emulator-5554`** (`am start` thô, đúng
 * tầng 1 của §14 — không suy từ manifest):
 *
 * | App | Kết quả đo | Quyết định |
 * |---|---|---|
 * | Google Maps | `geo:0,0?q=` + `setPackage` ⇒ mở đúng màn kết quả *"Bitexco"*. `google.navigation:q=` rơi vào màn *"Update Google Maps"* của bản trên máy ảo | `geo:` là đường CHÍNH; `google.navigation:` **không** dùng cho tới khi đo trên xe |
 * | Waze | `waze://?q=…&navigate=yes` resolve đúng *"Bitexco Financial Tower"*; tính tuyến lỗi vì máy ảo thiếu GPS/mạng thật | dùng; phần tính tuyến còn [CHƯA BIẾT] trên xe |
 * | VietMap Live 3.4.0 | **không có cửa nào** nhận điểm đến (xem [VoiceLaunch.OpenOnly]) | chỉ mở app + **nói rõ** |
 * | YT Music | `MEDIA_PLAY_FROM_SEARCH` + `query` + **bắt buộc** `extra.focus` = audio + dấu sao ⇒ mở đúng *"Diễm Xưa – Khánh Ly"*, dừng ở nút Play (không tự phát) | dùng; câu trả lời nói *"bấm Play"* |
 * | YouTube | có khai cửa `ACTION_SEARCH`, nhưng bản trên máy ảo chặn bằng *"Update your app"* | giữ dòng, [CHƯA BIẾT] |
 * | Spotify · Zing MP3 | chưa cài ở đâu để đo | giữ dòng, [CHƯA BIẾT] |
 *
 * ⚠ Khi dò lại bằng `adb shell am start`: `&` và khoảng trắng **phải** nằm trong một chuỗi đã bọc nháy, không thì
 * shell của máy cắt lệnh làm đôi và phép đo nói về một URI khác cái mình định đo.
 *
 * ## Ba điều bảng này **không** làm
 *  1. **Không** là roster gói dẫn đường thứ hai: gói lấy từ [NavApps] (nguồn sự thật đã có, `NavPackageRosterSyncTest`
 *     canh nó với XML của a11y). Chép lại tên gói ở đây là đúng lỗi 5-bản-sao mà [NavApps] sinh ra để dọn.
 *  2. **Không** quyết định app mặc định: thứ tự ưu tiên là việc của tầng biết *"app nào đang cài / phiên nhạc nào
 *     đang chạy"*, tức `:app`.
 *  3. **Không** tự mở gì: nó chỉ mô tả.
 */
object VoiceAppTargets {

    // ── mã đích (hằng, để chỗ gọi và bài kiểm khỏi gõ chuỗi tay) ─────────────────────────────────
    const val YT_MUSIC = "ytmusic"
    const val YOUTUBE = "youtube"
    const val SPOTIFY = "spotify"
    const val ZING = "zing"
    const val GMAPS = "gmaps"
    const val WAZE = "waze"
    const val VIETMAP = "vietmap"

    /** Extra bắt buộc của YT Music — xem KDoc [VoiceLaunch.Action.extras] và bảng [ĐO] ở KDoc lớp. */
    private val AUDIO_FOCUS = mapOf(VoiceLaunch.EXTRA_MEDIA_FOCUS to VoiceLaunch.FOCUS_ANY_AUDIO)

    /**
     * App NHẠC.
     *
     * `MEDIA_PLAY_FROM_SEARCH` là hợp đồng **của nền tảng** (`MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`) nên
     * nó là lựa chọn đầu cho app nhạc. Ngoại lệ là **YouTube**: nó không phải app nhạc ⇒ đường của nó là
     * `ACTION_SEARCH` (ô tìm kiếm).
     */
    val MUSIC: List<VoiceAppTarget> = listOf(
        VoiceAppTarget(
            key = YT_MUSIC,
            label = "YouTube Music",
            kind = VoiceAppKind.MUSIC,
            packages = listOf("com.google.android.apps.youtube.music"),
            launch = VoiceLaunch.Action(VoiceLaunch.ACTION_MEDIA_PLAY_FROM_SEARCH, extras = AUDIO_FOCUS),
            evidence = VoiceAppEvidence.MEASURED,
            // Tự phát theo video_id đã giải (owner "phát luôn"): mở URL watch của YT Music.
            watch = VoiceLaunch.Uri("https://music.youtube.com/watch?v=${VoiceLaunch.SLOT}"),
        ),
        VoiceAppTarget(
            key = YOUTUBE,
            label = "YouTube",
            kind = VoiceAppKind.MUSIC,
            packages = listOf("com.google.android.youtube", "app.revanced.android.youtube"),
            // [ĐO xe 2026-09-17 · log owner] `ACTION_SEARCH` chỉ MỞ Ô TÌM KIẾM, không phát — owner báo "search mà
            // không hát". `MEDIA_PLAY_FROM_SEARCH` + `extra.focus=audio` là hợp đồng nền tảng để PHÁT theo chuỗi
            // tìm; app nhạc/video (gồm YouTube) đăng ký receiver này và tự phát kết quả đầu (như YT Music). CHỜ XE
            // xác nhận YouTube tự phát hay dừng ở kết quả (bản YouTube trên xe khác bản 2019 chặn của máy ảo).
            launch = VoiceLaunch.Action(VoiceLaunch.ACTION_MEDIA_PLAY_FROM_SEARCH, extras = AUDIO_FOCUS),
            fallback = VoiceLaunch.Action(VoiceLaunch.ACTION_SEARCH),
            evidence = VoiceAppEvidence.AWAITING_CAR,
            // Tự phát: mở URL watch (YouTube tự phát video theo id). Đây là cách Kiki đạt "phát luôn".
            watch = VoiceLaunch.Uri("https://www.youtube.com/watch?v=${VoiceLaunch.SLOT}"),
        ),
        VoiceAppTarget(
            key = SPOTIFY,
            label = "Spotify",
            kind = VoiceAppKind.MUSIC,
            packages = listOf("com.spotify.music"),
            launch = VoiceLaunch.Action(VoiceLaunch.ACTION_MEDIA_PLAY_FROM_SEARCH, extras = AUDIO_FOCUS),
        ),
        // Giữ dòng dù chưa đo được: bỏ hẳn thì *"phát bài X bằng zing"* rơi vào "không hiểu", trong khi mở app
        // trơn vẫn hơn — và câu trả lời tự nói ra là chưa chắc giao được chữ (xem [VoiceAppEvidence]).
        VoiceAppTarget(
            key = ZING,
            label = "Zing MP3",
            kind = VoiceAppKind.MUSIC,
            packages = listOf("com.zing.mp3"),
            launch = VoiceLaunch.Action(VoiceLaunch.ACTION_MEDIA_PLAY_FROM_SEARCH, extras = AUDIO_FOCUS),
        ),
    )

    /**
     * App DẪN ĐƯỜNG. Gói lấy từ [NavApps] — xem điều (1) ở KDoc lớp.
     *
     * ⚠ Mọi ý-định ở đây **phải** được `setPackage` bởi tầng thi hành: [ĐO] cả Google Maps lẫn Waze đều bắt
     * `geo:`/`google.navigation:`, nên một ý-định trần sẽ bung hộp *"Open with"* — giữa lúc đang lái, một hộp
     * chọn app là thứ tệ hơn cả không làm gì.
     *
     * ## Đường TOẠ ĐỘ ([VoiceAppTarget.coord]) — vì sao có, và vì sao hôm nay chỉ VietMap dùng
     * Owner 2026-09-14: *"Kiki nó mở dẫn đường VietMap OK"* ⇒ phải có một cửa, chỉ là ta chưa tìm ra. [ĐO] đọc
     * thẳng nguồn Kiki đã decompile (CLAUDE.md §12 — dùng nguồn RE sẵn có trước khi đoán):
     * `../jadx-kiki/sources/p449vq/AbstractC8122l.java:600` và `p478x/C8454u.java:226` — Kiki gửi `ACTION_VIEW`
     * + `setPackage("vn.vietmap.live")` + `vietmaplive://companion/navigation?lat=…&lng=…&poiName=…`, còn với
     * Google Maps thì dùng `google.navigation:ll=<lat>,<lng>`. **Tức VietMap nhận TOẠ ĐỘ, không nhận chữ** — và
     * Kiki giải tên-thành-toạ-độ ở máy chủ của họ. Điều đó khớp hệt phép đo *"ép `geo:` vào VietMap ⇒ unable to
     * resolve"*: không phải app câm, mà là ta gõ nhầm cửa.
     *
     * Nhưng **CLAUDE.md §6 cấm đảo thứ đang chạy tốt**: Google Maps và Waze đã có đường CHỮ [ĐO] chạy trên máy
     * ảo, nên chúng **giữ nguyên đường chữ** (xem [VoiceAppTarget.destinationLaunch]). Hai dòng `coord` của
     * chúng nằm đây ở dạng **dữ liệu chờ**, để ngày owner đo trên xe thì đổi đúng một chữ — không phải viết
     * lại gì.
     */
    val NAV: List<VoiceAppTarget> = listOf(
        VoiceAppTarget(
            key = GMAPS,
            label = "Google Maps",
            kind = VoiceAppKind.NAV,
            packages = NavApps.GMAPS.toList(),
            // [ĐO xe 2026-09-17 · log owner] `geo:0,0?q=` chỉ MỞ MÀN KẾT QUẢ, KHÔNG bắt đầu dẫn — owner báo "dẫn
            // đường chưa trigger google map dẫn". `google.navigation:q=<địa chỉ text>` là deep-link CHUẨN của Google
            // để BẮT ĐẦU dẫn turn-by-turn với chuỗi chữ (Google tự geocode ở máy chủ ⇒ KHÔNG cần Nominatim, không
            // kẹt mạng). Bản GMaps trên xe là bản mới (khác màn "Update Google Maps" của GMaps 2019 trên máy ảo cũ).
            launch = VoiceLaunch.Uri("google.navigation:q=${VoiceLaunch.SLOT}"),
            evidence = VoiceAppEvidence.MEASURED,
            // Đường toạ độ (khi đã geocode sẵn — vd sổ địa chỉ có lat/lng): dẫn thẳng bằng toạ độ, cũng không cần tra.
            coord = VoiceLaunch.Uri("google.navigation:ll=${VoiceLaunch.LAT},${VoiceLaunch.LNG}"),
            coordEvidence = VoiceAppEvidence.MEASURED,
        ),
        VoiceAppTarget(
            key = WAZE,
            label = "Waze",
            kind = VoiceAppKind.NAV,
            packages = NavApps.WAZE.toList(),
            launch = VoiceLaunch.Uri("waze://?q=${VoiceLaunch.SLOT}&navigate=yes"),
            fallback = VoiceLaunch.Uri("geo:0,0?q=${VoiceLaunch.SLOT}"),
            evidence = VoiceAppEvidence.MEASURED,
            coord = VoiceLaunch.Uri("waze://?ll=${VoiceLaunch.LAT},${VoiceLaunch.LNG}&navigate=yes"),
            coordEvidence = VoiceAppEvidence.AWAITING_CAR,
        ),
        VoiceAppTarget(
            key = VIETMAP,
            label = "VietMap",
            kind = VoiceAppKind.NAV,
            packages = NavApps.VIETMAP.toList(),
            // Không có cửa CHỮ — [ĐO emulator 2026-09-23, VietMap 3.3.4 đã login] thử 5 URL text đều FAIL:
            // /maps/search|dir|place + vietmaplive://…?poiName=<text> (→ lỗi 1037) + vietmaplive://search?q= —
            // đều panel "VM Connect share" không điểm đến. `/maps` filter là kênh share-link CÓ toạ độ nhúng, KHÔNG
            // geocode text. ⇒ VietMap BẮT BUỘC toạ độ (đường `coord`). ĐỪNG thử lại google.com/maps text — đã đo.
            launch = VoiceLaunch.OpenOnly,
            evidence = VoiceAppEvidence.MEASURED,
            coord = VoiceLaunch.Uri(
                "vietmaplive://companion/navigation?lat=${VoiceLaunch.LAT}&lng=${VoiceLaunch.LNG}" +
                    "&poiName=${VoiceLaunch.SLOT}",
            ),
            // [ĐO] máy ảo 2026-09-14: URI này **kéo VietMap lên tiền cảnh** (task cũ được đưa lên trước) nhưng
            // không thấy tuyến — máy ảo không có GPS fix nên bản đồ đứng ở vị trí trống. Cơ chế thì đã chứng
            // minh (nguồn Kiki, và owner xác nhận Kiki mở dẫn đường VietMap chạy trên xe) ⇒ CHỜ XE, không phải
            // CHƯA BIẾT. ⚠ VietMap là `singleTask`: ý-định thứ hai đi vào `onNewIntent` của task đang có, nên
            // tầng thi hành dùng `FLAG_ACTIVITY_NEW_TASK` và **không** thêm `CLEAR_TOP` (chưa kiểm được app xử
            // lý ca đó ra sao).
            coordEvidence = VoiceAppEvidence.AWAITING_CAR,
        ),
    )

    val ALL: List<VoiceAppTarget> = MUSIC + NAV

    /** Đích theo mã, `null` nếu mã lạ (bản sau xoá một dòng ⇒ ý định cũ còn trong hàng đợi không được làm sập). */
    fun byKey(key: String?): VoiceAppTarget? = key?.let { k -> ALL.firstOrNull { it.key == k } }

    /**
     * App dẫn đường **giao được điểm đến với đúng dữ liệu đang có** — spec `kachi-voice-addresses.html` R3.
     *
     * ## Vì sao phép chọn phải biết `hasCoords`
     * [ĐO] bảng trên: VietMap là [VoiceLaunch.OpenOnly] ở đường CHỮ và chỉ có đường TOẠ ĐỘ. Mà VietMap đứng
     * **đầu** thứ tự ưu tiên của xe owner (nó đang nuôi badge tốc độ). Nên chọn app trước rồi mới hỏi *"giao
     * được không"* sẽ cho ra ca hay gặp nhất của sổ địa chỉ — một mục **chỉ có chữ** — rơi vào *"mở app trơn,
     * gõ tay trong app"*, trong khi ngay dưới nó có Google Maps nhận được nguyên văn địa chỉ ấy.
     *
     * ⇒ Đi theo thứ tự ưu tiên, lấy app đầu tiên **giao được**; không app nào giao được thì trả app đầu tiên
     * đang cài (chỗ gọi vẫn mở nó lên và **nói ra** phần chưa làm được — không bao giờ báo một dấu ✓ rỗng).
     *
     * @param preferredPackages thứ tự ưu tiên theo TÊN GÓI (`VoiceDispatcher.NAV_PREFERENCE`) — truyền vào chứ
     *   không khai ở đây: roster gói là của [NavApps], và thứ tự là quyết định của tầng biết xe (CLAUDE.md §6).
     */
    fun navFor(hasCoords: Boolean, preferredPackages: List<String>, installed: Set<String>): VoiceAppTarget? {
        val candidates = preferredPackages
            .mapNotNull { pkg -> NAV.firstOrNull { pkg in it.packages } }
            .filter { it.packageIn(installed) != null }
            .distinct()
        return candidates.firstOrNull { it.destinationLaunch(hasCoords) != null } ?: candidates.firstOrNull()
    }

    /** Nhãn hiện cho người dùng của một mã; mã lạ ⇒ trả chính mã (cùng lệ [VoiceReply.labelOf]). */
    fun labelOf(key: String): String = byKey(key)?.label ?: key

    /**
     * Đích cho một cách NÓI (đã bỏ dấu, khớp **nguyên cụm**), giới hạn trong [kind] khi có.
     *
     * Dùng bởi [VoiceIntentParser] ở đúng một vị trí: sau cụm đánh dấu *"bằng / trên / với"* — xem KDoc
     * [VoiceIntentParser.appAfterMarker] về vì sao KHÔNG đưa các cụm này vào từ vựng chung.
     */
    fun bySpoken(words: List<String>, kind: VoiceAppKind? = null): VoiceAppTarget? =
        ALL.firstOrNull { t -> (kind == null || t.kind == kind) && t.spoken.any { spokenWords(it) == words } }

    /**
     * ═══ Đích cho một cách nói **RỤNG MẤT ÂM CUỐI** — *"vietma"* ⇒ VietMap ═════════════════════════════════
     *
     * ## Bệnh nó chữa — [ĐO xe 2026-09-18] (`oncar-voice-music-vietmap-2026-09-18.md` §BUG A)
     * Owner nói *"dẫn đường tới chợ Bến Thành **bằng VietMap**"*; mô hình in ra *"bằng **vietma**"* (rụng chữ
     * `p`). [bySpoken] khớp **nguyên cụm** nên trượt ⇒ mệnh đề chọn app không được cắt ra ⇒ Kachi bắn
     * `google.navigation:q=chợ bến thành **bằng vietma**` tới **Google Maps**: sai app, và địa chỉ mang theo hai
     * chữ rác. Một dòng log, ba lỗi.
     *
     * ## Luật hẹp nhất chữa được đúng bệnh đã đo
     * Rụng **đúng một ký tự cuối** của **từ cuối**, mọi từ trước phải khớp y nguyên, và cụm phải dài ≥
     * [MIN_LOOSE_LEN] ký tự. Không dùng khoảng cách sửa chữa tổng quát ([VoicePhoneticMatch]) vì ở đó *"quay"* ·
     * *"map"* · *"yt"* sẽ khớp vào hàng loạt tiếng Việt thường — mà cụm này đứng ngay trước phần **điểm đến**,
     * tức chỗ đắt nhất để đoán sai.
     *
     * ⚠ Chỉ [VoiceTailClause.appAfterMarker] gọi (sau cụm đánh dấu *"bằng / trên / với"*): ở đó chữ *"bằng"* đã
     * chứng minh người nói **đang nêu tên một app**. Đường không có cụm đánh dấu ([VoiceTailClause.appByTargetName])
     * cố ý vẫn khớp CHÍNH XÁC — nới ở đó là mời mọi câu lạ mở app.
     */
    fun bySpokenLoose(words: List<String>, kind: VoiceAppKind? = null): VoiceAppTarget? {
        if (words.isEmpty() || words.sumOf { it.length } < MIN_LOOSE_LEN) return null
        return ALL.firstOrNull { t ->
            (kind == null || t.kind == kind) && t.spoken.any { droppedLastChar(spokenWords(it), words) }
        }
    }

    /** [said] đúng bằng [full] nhưng từ CUỐI thiếu một ký tự ở cuối. */
    private fun droppedLastChar(full: List<String>, said: List<String>): Boolean {
        if (full.size != said.size || full.isEmpty()) return false
        if (full.dropLast(1) != said.dropLast(1)) return false
        val last = full.last()
        return last.length >= 2 && said.last() == last.dropLast(1)
    }

    /**
     * ═══ Đích cho một cách nói **BỊ ASR BÓP MÉO** — *"vietna"* ⇒ VietMap ══════════════════════════════════════
     *
     * ## Bệnh nó chữa — [ĐO xe 2026-09-20] (`oncar-1.84-session-2026-09-20.md` §5)
     * Owner nói *"… bằng VietMap"*, mô hình in ra *"bằng **vietna**"*. [bySpoken] khớp nguyên cụm ⇒ trượt;
     * [bySpokenLoose] chỉ tha **một** ký tự cuối ⇒ cũng trượt. Không cắt được mệnh đề chọn app ⇒ hai chữ rác đi
     * theo **điểm đến**, và câu rơi về app mặc định với một địa chỉ không tra được. *"vietmap"* là tên tự chế nên
     * ASR tiếng Việt sẽ còn bóp méo nhiều kiểu nữa: khai từng biến thể vào [VoiceSynonyms.APP_TARGETS] là đuổi
     * bắt vô hạn (1.82 đã thêm 4 biến thể mà vẫn hụt đúng cái này).
     *
     * ## Luật: **NEO TIỀN TỐ** rồi mới đo lệch — đó là thứ giữ nó không nuốt tên địa điểm
     * Cùng [PREFIX_ANCHOR] ký tự đầu **và** lệch ≤ [MAX_EDITS] ký tự (Levenshtein), cả hai chuỗi ≥
     * [MIN_LOOSE_LEN] ký tự: *"vietna"* ↔ *"vietmap"* lệch 2 ✓, *"vietm"* ↔ *"vietmap"* lệch 2 ✓,
     * *"youtub"* ↔ *"youtube"* lệch 1 ✓.
     *
     * So trên chuỗi đã **ghép liền** nên nó không phụ thuộc việc mô hình tách *"vietna"* hay *"viet na"* — đúng
     * chỗ mà một phép so theo-từng-từ sẽ hụt.
     *
     * ⚠ Cố ý **KHÔNG** có luật *"said là tiền tố thật sự của full"*. [ĐO off-car, lượt đầu của bản vá này] nó làm
     * *"youtub"* khớp **YouTube Music** (vì `youtubemusic` cũng bắt đầu bằng `youtub`, và YT Music đứng trước trong
     * [ALL]) và *"zing"* khớp `zingmp3` dù cụm ấy vốn đã dưới sàn — tức kết quả phụ thuộc **thứ tự dòng trong
     * bảng**, thứ không ai coi là một quyết định. Luật lệch-ký-tự thì đối xứng: nó tự loại `youtubemusic` (lệch 6).
     *
     * ## Vì sao KHÔNG phải một phép so mờ tổng quát (và KDoc [bySpokenLoose] cảnh đúng điều đó)
     * Neo tiền tố + hai sàn độ dài là ba cổng khoá lại đúng cái nguy: [ĐO off-car] các cụm đứng sau cụm đánh dấu
     * trong câu dẫn đường thật (*"qua thủ đức"* · *"trên nguyễn huệ"* · *"cầu bằng **lăng**"* · *"cảng dung
     * **quất**"* · *"bằng xe máy"*) đều **không** qua nổi: hoặc ngắn hơn sàn, hoặc lệch tiền tố. Cụm ngắn
     * (*"waze"* · *"quay"* · *"yt"* · *"zing"*) vẫn chỉ khớp CHÍNH XÁC vì chính chúng dưới sàn.
     *
     * ⚠ Chỉ [VoiceTailClause.appAfterMarker] gọi — cùng ràng buộc của [bySpokenLoose]: ở đó chữ *"bằng / qua /
     * dùng"* đã chứng minh người nói **đang nêu tên một app**. Nới ở đường không có cụm đánh dấu
     * ([VoiceTailClause.appByTargetName]) là mời mọi câu lạ mở app.
     */
    fun bySpokenFuzzy(words: List<String>, kind: VoiceAppKind? = null): VoiceAppTarget? {
        val said = words.joinToString("")
        if (said.length < MIN_LOOSE_LEN) return null
        return ALL.firstOrNull { t ->
            (kind == null || t.kind == kind) && t.spoken.any { nearly(spokenWords(it).joinToString(""), said) }
        }
    }

    /** Luật của [bySpokenFuzzy]; [full] = cách nói đã khai, [said] = cụm người ta thật sự nói. */
    private fun nearly(full: String, said: String): Boolean {
        if (full.length < MIN_LOOSE_LEN) return false
        if (full.take(PREFIX_ANCHOR) != said.take(PREFIX_ANCHOR)) return false
        // Chặn theo ĐỘ DÀI trước để khỏi chạy bảng cho hai chuỗi lệch hẳn nhau (*"youtub"* vs *"youtubemusic"*).
        if (kotlin.math.abs(full.length - said.length) > MAX_EDITS) return false
        return edits(full, said) <= MAX_EDITS
    }

    /**
     * Khoảng cách Levenshtein. Bảng **hai hàng** (các chuỗi ở đây dài ≤ ~15 ký tự nên không cần tối ưu gì thêm);
     * thuần Kotlin, không `android.*`, kiểm cạn off-car.
     */
    private fun edits(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val sub = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(sub, prev[j] + 1, cur[j - 1] + 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Cụm ngắn hơn ngần này ký tự thì rụng một âm cũng thành một từ khác hẳn ⇒ không khớp mờ. */
    private const val MIN_LOOSE_LEN = 5

    /** Số ký tự đầu phải khớp Y NGUYÊN ở [bySpokenFuzzy] — cái neo giữ nó không bắt sang tên địa điểm. */
    private const val PREFIX_ANCHOR = 4

    /** Lệch tối đa (thêm/bớt/đổi) sau khi đã neo tiền tố. 2 = đủ cho *"vietna"* ↔ *"vietmap"*. */
    private const val MAX_EDITS = 2

    /** Số từ dài nhất mà một cách nói chiếm — chỗ gọi quét từ dài xuống ngắn (luật *"dãy dài nhất thắng"*). */
    val LONGEST_SPOKEN: Int = ALL.flatMap { it.spoken }.maxOfOrNull { spokenWords(it).size } ?: 1

    private fun spokenWords(phrase: String): List<String> = VoiceLexicon.tokenize(phrase).map { it.norm }
}
