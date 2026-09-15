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
        ),
        VoiceAppTarget(
            key = YOUTUBE,
            label = "YouTube",
            kind = VoiceAppKind.MUSIC,
            packages = listOf("com.google.android.youtube", "app.revanced.android.youtube"),
            launch = VoiceLaunch.Action(VoiceLaunch.ACTION_SEARCH),
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
            launch = VoiceLaunch.Uri("geo:0,0?q=${VoiceLaunch.SLOT}"),
            evidence = VoiceAppEvidence.MEASURED,
            // [ĐO] nguồn Kiki (xem KDoc); `google.navigation:` trên máy ảo rơi vào màn *"Update Google Maps"*.
            coord = VoiceLaunch.Uri("google.navigation:ll=${VoiceLaunch.LAT},${VoiceLaunch.LNG}"),
            coordEvidence = VoiceAppEvidence.AWAITING_CAR,
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
            // Không có cửa CHỮ — đó là kết luận đã đo, xem [VoiceLaunch.OpenOnly].
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

    /** Số từ dài nhất mà một cách nói chiếm — chỗ gọi quét từ dài xuống ngắn (luật *"dãy dài nhất thắng"*). */
    val LONGEST_SPOKEN: Int = ALL.flatMap { it.spoken }.maxOfOrNull { spokenWords(it).size } ?: 1

    private fun spokenWords(phrase: String): List<String> = VoiceLexicon.tokenize(phrase).map { it.norm }
}
