package com.byd.clusternav.launcher.voice

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
