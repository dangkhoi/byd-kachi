package com.byd.clusternav.launcher

/** Một ô workspace đang chứa gì. */
sealed interface SlotContent {
    /** Ô trống — UI hiện "Mở app / ＋ Widget". */
    object Empty : SlotContent
    /** Ô chứa MỘT app thật (package). */
    data class App(val pkg: String) : SlotContent
    /** Ô chứa 1..8 widget Kachi (id trong WidgetRegistry) — nhiều widget xếp lưới trong cùng ô. */
    data class Widget(val ids: List<String>) : SlotContent {
        constructor(id: String) : this(listOf(id))   // tương thích chỗ gọi cũ (1 widget)
    }

    /**
     * Ô chứa **widget Android của app khác** (P6 · T4) — KHÁC hẳn [Widget] (thẻ Kachi dựng tay).
     *
     * ## Vì sao mang cả [widgetId] lẫn [provider], không chỉ một trong hai
     *  - [widgetId] là số nền tảng cấp (`allocateAppWidgetId`). Nó là **thứ duy nhất** dựng lại được view sau khi
     *    launcher khởi động lại, nên **phải lưu bền**. Không lưu thì mỗi lần mở app là một id mới ⇒ id cũ **rò**
     *    (nhà cung cấp vẫn cập nhật cho một ô không còn ai xem — đúng thứ [AppWidgetIds] sinh ra để chặn).
     *  - [provider] (ComponentName dạng phẳng) là để **nói ra được** khi id đã chết: app bị gỡ / bị vô hiệu thì
     *    `getAppWidgetInfo(id)` trả `null`, lúc đó chỉ còn cái tên này để hiện *"widget của app X không còn"*
     *    thay vì để lại một ô trống bí ẩn. Nó cũng là thứ ràng buộc lại id khi cần.
     *
     * ⚠ **KHÔNG** phải "thẻ dựng tay có thêm id". Ô này do nhà cung cấp tự vẽ (RemoteViews đẩy sang), nên tầng vẽ
     * **không được** dựng lại nó theo nhịp trạng thái xe — dựng lại là tháo view đang nhận RemoteViews mỗi giây.
     * Xem `WorkspaceRenderPlanner.decide` (bài học `w_photos`).
     */
    data class AppWidget(val widgetId: Int, val provider: String) : SlotContent
}

/**
 * MÃ HOÁ nội dung một ô — **nguồn duy nhất** cho dạng chuỗi của [SlotContent].
 *
 * ## Vì sao nó ở `:core` chứ ở lại `WorkspacePrefs`
 * Đây từng là hai hàm `private` trong `WorkspacePrefs` (:app). Khi cảnh (P7/P6) cần lưu **cùng** nội dung ô, chỉ có
 * hai đường: chép lại phép mã hoá, hoặc đưa nó về một chỗ. Chép lại chính là cách dự án đã sinh ra **ngưỡng lốp thứ
 * ba** và **hai bảng màu** — và ở đây hậu quả nặng hơn: hai bộ mã hoá lệch nhau nghĩa là cảnh đọc ra nội dung ô khác
 * với thứ người dùng đã lưu, âm thầm.
 *
 * Dạng chuỗi của ba loại ô CŨ **KHÔNG đổi một byte** (`app:<gói>` · `widget:<id>,<id>` · rỗng = ô trống) ⇒ cấu hình
 * đã nằm trên đĩa của xe đọc lên nguyên vẹn.
 */
object SlotCodec {

    fun encode(c: SlotContent): String = when (c) {
        SlotContent.Empty -> ""
        is SlotContent.App -> "$APP${c.pkg}"
        is SlotContent.Widget -> "$WIDGET${c.ids.joinToString(",")}"
        is SlotContent.AppWidget -> "$APP_WIDGET${c.widgetId}$SEP${c.provider}"
    }

    /** Chuỗi lạ/rỗng ⇒ ô trống (không ném): chuỗi này đến từ đĩa và có thể bị sửa tay. */
    fun decode(s: String): SlotContent = when {
        // ⚠ THỨ TỰ: `aw:` phải xét TRƯỚC `app:`? Không — hai tiền tố không lồng nhau (`aw:` ≠ đầu của `app:`), nên
        // thứ tự ở đây không quan trọng. Ghi ra vì tiền-tố-lồng-nhau đã là bẫy thật của dự án ở `CapabilityIcons`
        // (`tyre_t_` phải xét trước `tyre_p_`), và người sửa sau sẽ tự hỏi đúng câu này.
        s.startsWith(APP) -> SlotContent.App(s.removePrefix(APP))
        s.startsWith(WIDGET) -> s.removePrefix(WIDGET).split(",").filter { it.isNotBlank() }
            .let { if (it.isEmpty()) SlotContent.Empty else SlotContent.Widget(it) }
        s.startsWith(APP_WIDGET) -> decodeAppWidget(s.removePrefix(APP_WIDGET))
        else -> SlotContent.Empty
    }

    /**
     * `<id>@<provider>` → [SlotContent.AppWidget]. Hỏng ⇒ **ô trống**, không ném.
     *
     * Ba ca hỏng đều có thật: thiếu dấu phân cách (sửa tay), id không phải số (sửa tay), id ≤ 0 (nền tảng KHÔNG bao
     * giờ cấp id ≤ 0, nên giá trị đó chỉ đến từ dữ liệu rác — nhận nó vào sẽ tạo một ô xin view cho id không tồn tại).
     * Provider rỗng cũng là hỏng: không có tên thì lúc id chết không nói được *widget của app nào*.
     */
    private fun decodeAppWidget(body: String): SlotContent {
        val cut = body.indexOf(SEP)
        if (cut <= 0) return SlotContent.Empty
        val id = body.substring(0, cut).toIntOrNull() ?: return SlotContent.Empty
        val provider = body.substring(cut + 1)
        return if (id > 0 && provider.isNotBlank()) SlotContent.AppWidget(id, provider) else SlotContent.Empty
    }

    private const val APP = "app:"
    private const val WIDGET = "widget:"

    /**
     * Tiền tố widget bên thứ ba. **Mới ở T4** — dạng chuỗi của ba loại cũ KHÔNG đổi một byte, nên cấu hình đã nằm
     * trên đĩa của xe đọc lên nguyên vẹn, và bản cũ đọc chuỗi này ra **ô trống** (không sập) nếu người dùng hạ cấp.
     */
    private const val APP_WIDGET = "aw:"

    /**
     * Dấu phân cách id/provider.
     *
     * ## ⚠⚠ KHÔNG được là `|` — [ĐO] 2026-09-12, bản đầu dùng `|` và nó làm **mất cảnh trong im lặng**
     * Đầu ra của [encode] được **nhúng vào** chuỗi lưu của [SceneBook], nơi `|` ngăn TRƯỜNG và `;` ngăn Ô. Một ô
     * widget bên thứ ba mang thêm một `|` ⇒ bản ghi cảnh có **8 trường thay vì 7** ⇒ [SceneBook.decode] bỏ cả cảnh.
     * Đo trên `emulator-5554`: lưu cảnh với widget đồng hồ ra chuỗi
     * `s1|CoWidget|ONE|…|widget:g_windows;widget:g_adas;aw:651|com.google.android.deskclock/…;…|BOTTOM|lock,…`
     * (8 trường), khởi động lại launcher ⇒ màn Cài đặt hiện *"Chưa có cảnh nào · 0 / 8"* — cảnh người dùng vừa lưu
     * **biến mất**, không một lời nào.
     *
     * ⇒ Ký tự ở đây phải không nằm trong [SceneBook.RESERVED] và cũng không phải `,` (dấu ngăn danh sách thẻ dựng
     * tay). `@` đạt cả hai và **không thể** xuất hiện trong một ComponentName dạng phẳng (`gói/lớp` chỉ gồm ký tự
     * định danh Java, `.`, `$` và `/`) nên nó cũng không đụng chính dữ liệu nó ngăn.
     *
     * Chốt không phải là lời hứa: `SceneBookTest` có bài quét **mọi** loại ô, đòi đầu ra `encode` không chứa ký tự
     * nào của [SceneBook.RESERVED] — nên loại ô thứ năm mai sau vi phạm là ĐỎ, chứ không phải mất cảnh.
     */
    private const val SEP = "@"
}

/**
 * Trạng thái workspace bền: preset đang dùng + nội dung tối đa 4 ô.
 * Pure model (:core) → serialize/restore test được off-car (WorkspacePrefs ở :app map sang SharedPreferences).
 */
data class WorkspaceState(
    val preset: LayoutPreset = LayoutPreset.THREE,
    val slots: List<SlotContent> = List(SLOT_CAP) { SlotContent.Empty },
) {
    init {
        require(slots.size == SLOT_CAP) { "slots must be exactly $SLOT_CAP (was ${slots.size})" }
    }

    /** Gán nội dung cho 1 ô (0 tới [SLOT_CAP]−1); index ngoài phạm vi → giữ nguyên. */
    fun withSlot(index: Int, content: SlotContent): WorkspaceState {
        if (index !in slots.indices) return this
        return copy(slots = slots.toMutableList().also { m ->
            // MỘT-APP-MỘT-Ô (owner 2026-09-15): đặt App(pkg) vào một ô thì GỠ pkg khỏi mọi ô KHÁC — một app không
            // được hiện ở hai ô. Bất biến ở tầng MÔ HÌNH nên đúng cho MỌI đường (picker/voice/khôi phục/preset),
            // không chỉ đường picker. (Teardown cửa sổ ô cũ do KachiHomeSlots.assignApp lo — model chỉ giữ state.)
            if (content is SlotContent.App) {
                for (i in m.indices) if (i != index && (m[i] as? SlotContent.App)?.pkg == content.pkg) m[i] = SlotContent.Empty
            }
            m[index] = content
        })
    }

    /**
     * Chữa một trạng thái NẠP từ ngoài (prefs) về đúng hai bất biến của mô hình. Hai việc, cùng một lý do: dữ liệu
     * trên đĩa có thể **cũ hơn** bản đang chạy, mà [withSlot] chỉ chặn lượt gán MỚI (nạp thì dựng thẳng qua
     * constructor, không đi qua nó).
     *
     *  1. **MỘT-APP-MỘT-Ô** (owner 2026-09-15): dữ liệu lưu TRƯỚC bản vá đó có thể có cùng app ở hai ô. Giữ lần
     *     xuất hiện ĐẦU (ô index thấp = ô đang hiện cửa sổ thật), xoá các ô trùng SAU về trống.
     *  2. **MÃ KHẢ NĂNG ĐÃ BIẾN MẤT** ⇒ bỏ khỏi ô. [ĐO đọc source] `WidgetViews.build` cho mã lạ rơi xuống
     *     `telemetry(...)` → [TelemetryReadout.of] trả `null` → ô vẽ ra **`"ADAS_FCW"` + `"—"` mãi mãi**: không
     *     sập, nhưng người dùng nhìn thấy một ô hỏng mà không có cách nào biết vì sao. Thanh nút và chip thanh
     *     trên vốn ĐÃ bỏ mã lạ (`ControlDockView.rebuild` nhánh `null -> Unit`; `TopStripChips.render` dùng
     *     `mapNotNull`), nên ô giữa màn là bề mặt DUY NHẤT còn giữ lại rác — chữa ở đây thì cả ba khớp nhau.
     *
     * ⚠ Vì sao ở tầng MÔ HÌNH chứ không ở [SlotCodec]: dạng chuỗi trên đĩa là **hợp đồng lưu bền** và phải đọc lên
     * nguyên vẹn (KDoc [SlotCodec]); việc *"mã này còn tồn tại không"* là câu hỏi về BỘ ĐĂNG KÝ hôm nay, không phải
     * về cú pháp chuỗi. Hai câu hỏi khác nhau ⇒ hai tầng khác nhau.
     *
     * ⚠ Nền của luật: [CapabilityCatalog.HIDDEN_FROM_PICKER] chốt *"ẩn khỏi bộ chọn ≠ xoá mã"* — mã ẩn vẫn tra ra
     * được nên ô của ai đã đặt vẫn chạy, và hàm này KHÔNG đụng tới chúng. Nó chỉ bỏ mã mà **không bộ đăng ký nào
     * còn nhận** (owner gỡ hẳn — vd lượt ADAS-PURGE 2026-09-16 xoá 10 nút + 17 datum + 3 nhóm).
     *
     * Trả `this` nếu không phải chữa gì (không đổi tham chiếu vô ích).
     */
    fun sanitized(): WorkspaceState {
        val seen = HashSet<String>()
        var changed = false
        val out = slots.map { c ->
            when {
                c is SlotContent.App && !seen.add(c.pkg) -> { changed = true; SlotContent.Empty }
                c is SlotContent.Widget -> {
                    val keep = c.ids.filter { CapabilityCatalog.kindOf(it) != null }
                    when {
                        keep.size == c.ids.size -> c
                        keep.isEmpty() -> { changed = true; SlotContent.Empty }
                        else -> { changed = true; SlotContent.Widget(keep) }
                    }
                }
                else -> c
            }
        }
        return if (changed) copy(slots = out) else this
    }

    /**
     * Mã widget đang nằm trong ô mà **không bộ đăng ký nào còn nhận** — thứ [sanitized] sẽ bỏ đi.
     *
     * Tách khỏi [sanitized] để chỗ gọi ở `:app` **nói ra được** nó vừa bỏ cái gì (một dòng log), thay vì để ô của
     * người dùng biến mất im lặng. Luật dự án: mất một thứ đã lưu mà không báo là kênh im lặng.
     */
    fun unknownWidgetIds(): List<String> =
        slots.filterIsInstance<SlotContent.Widget>().flatMap { it.ids }.filter { CapabilityCatalog.kindOf(it) == null }

    /** Đổi preset (giữ nguyên gán ô theo chỉ số — ô ẩn vẫn nhớ). */
    fun withPreset(preset: LayoutPreset): WorkspaceState = copy(preset = preset)

    /** Xoá 1 ô về trống. */
    fun clearSlot(index: Int): WorkspaceState = withSlot(index, SlotContent.Empty)

    /** Đổi chỗ nội dung 2 ô [a] và [b]. Index ngoài phạm vi hoặc a==b → giữ nguyên. */
    fun swap(a: Int, b: Int): WorkspaceState {
        if (a !in slots.indices || b !in slots.indices || a == b) return this
        return copy(
            slots = slots.toMutableList().also {
                val tmp = it[a]; it[a] = it[b]; it[b] = tmp
            },
        )
    }

    /** Các ô ĐANG hiện theo preset (N ô đầu). */
    fun visibleSlots(): List<SlotContent> = slots.take(preset.slotCount)

    companion object {
        /** Số ô tối đa (khớp QUAD). Trạng thái luôn giữ đủ 4 để ô ẩn nhớ nội dung khi đổi preset. */
        /**
         * Trần số ô. **6** (P9 bước 3 nới từ 4).
         *
         * ## Vì sao 6, không phải nhiều hơn
         * Trần này bị chặn bởi **cỡ ô dùng được**, không phải bởi lưới. Trên màn 1920×1080: 6 ô ⇒ mỗi ô trung bình
         * ~640×360, còn **đặt được app** vào; 8 ô ⇒ ~480×270, app trong ô nhỏ tới mức vô dụng. Lưới 12×6 với khung
         * nhỏ nhất 2×1 về lý thuyết cho 36 khung — con số đó vô nghĩa với người dùng.
         *
         * ## Đổi số này thì phải biết
         *  - Mỗi ô chứa app cần **một màn ảo riêng** ⇒ tăng trần là tăng bộ nhớ và việc ghép hình.
         *  - Dữ liệu cũ **tự tương thích**: chỗ lưu đọc `slot_0..slot_(CAP-1)`, khoá thiếu ⇒ ô trống.
         *  - Hạ trần ở bản sau ⇒ bố cục nhiều khung hơn trần sẽ **lùi về bố cục sẵn** (xem `EffectiveLayout`), không
         *    làm mất ô của người dùng.
         */
        const val SLOT_CAP = 6

        /**
         * Dựng trạng thái mà **KHÔNG cần biết trần ô**: thiếu thì đệm ô trống, và **báo lỗi rõ** nếu truyền quá trần.
         *
         * ## Vì sao cần hàm này
         * [ĐO] khi nới trần 4 → 6, bố cục mặc định của launcher đang truyền **danh sách cứng 4 phần tử** ⇒ ném lỗi
         * **ngay lúc nạp lớp** ⇒ launcher **sập ở lần chạy đầu** (khi chưa có cấu hình nào để nạp). Test không bắt
         * được vì lớp đó cần Android. Mọi chỗ dựng trạng thái nên đi qua đây để đổi trần không thành lỗi sập.
         */
        fun of(preset: LayoutPreset, vararg contents: SlotContent): WorkspaceState {
            require(contents.size <= SLOT_CAP) {
                "quá trần ô: truyền ${contents.size}, trần $SLOT_CAP"
            }
            return WorkspaceState(preset, List(SLOT_CAP) { contents.getOrElse(it) { SlotContent.Empty } })
        }

        /**
         * Bố cục mặc định khi chưa có gì lưu. Ở `:core` để **kiểm được off-car** — trước đây nó nằm ở phía Android
         * nên không test nào chạm tới, và đó chính là chỗ suýt sập khi nới trần.
         */
        val DEFAULT: WorkspaceState = of(
            LayoutPreset.THREE,
            SlotContent.Widget("w_board"),
            SlotContent.Widget("w_energy"),
            SlotContent.Widget("w_pm25"),
        )
    }
}
