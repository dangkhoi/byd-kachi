package com.byd.clusternav.launcher

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BỘ CHỌN CHIP cho thanh trạng thái trên (RW0 **vùng thứ ba**), nay thuộc nhóm *"Thanh trạng thái & thanh nút"*
 * ([SettingsBarsSection], IA v2 · R-UI a).
 *
 * Giữ **trạng thái đang chọn của phiên mở bảng** để tô ô đúng; nguồn sự thật vẫn là `HomeUiState.topStrip` — lớp này
 * chỉ báo ra qua [onToggle] và KHÔNG ghi bền.
 *
 * ## ⚠ Ô ở đây dựng RIÊNG, KHÔNG dùng lại bộ dựng ô của lưới khác
 * [ĐO] bản đầu dùng lại thì sinh ba lỗi cùng lúc: sự kiện bấm bắn vào **thanh nút** (chọn chip lại thêm nút vào
 * thanh), `tiles[id]` bị **ghi đè** vì cùng một mã ở hai lưới, và hai chỗ tô nền tranh nhau. Đúng bẫy "hai bản sao
 * cùng khoá". **Hình học** thì dùng chung ([CapabilityTileGrid]) — đó là ranh giới đúng theo R5: giống nhau về
 * *nhịp*, không giống nhau về *hành vi bấm*.
 *
 * ## ⚠⚠ T4 · ba thứ đổi so với bản S1 — và vì sao
 *  1. **Tiêu đề + chú thích đi qua [SettingsRows]** (`sectionLabel`/`note`): [ĐO] soát ảnh 2026-09-12, lớp này giữ
 *     một `label()` **bản sao** của `sectionLabel` (findings #4) và một `TextView` chú thích dựng tay ⇒ lề/bậc chữ
 *     của nó trôi khỏi mọi bề mặt Settings khác mỗi lần design system đổi. Nay không còn bản sao nào.
 *  2. **Hàng ô đi qua [CapabilityTileGrid] với [CapabilityPicker.COLS]**: bản cũ tự xếp **5** cột trong khi lưới
 *     kia 4 cột — hai hệ lưới khác nhau trong CÙNG một vùng cuộn (findings #14, [P2]), và khe ngang/dọc = 0 nên ô
 *     dính nhau (findings #15).
 *  3. ~~Nút *"Thêm chip khác…"*~~ — **đã bỏ ở S4 · T5**, xem mục ngay dưới.
 *
 * ## ⚠⚠ S4 · R11 (c) — BÀY ĐỦ THEO LĨNH VỰC, HỘP THOẠI *"THÊM CHIP KHÁC…"* ĐÃ BỎ
 * Tới U6 màn này bày **3 chip dựng sẵn + chip đang bật** (≤ 7 ô) rồi giấu 120+ mục đọc còn lại sau một nút mở
 * **hộp thoại phẳng**. Owner 2026-09-14: *"hiện chỉ cho chọn 3 trong khi có thể chọn nhiều hơn"* — thứ hụt không
 * phải cái trần (trần là 4, và hộp thoại vẫn đặt được mọi datum) mà là **thứ nhìn thấy được**. Nay
 * [TopStripConfig.picks] bày **mọi** mục đặt được, xếp theo [Domain], mỗi lĩnh vực một khối gấp/mở được.
 *
 * Bỏ hộp thoại đó KHÔNG mất đường nào: nó bày đúng cùng một danh sách, qua đúng cùng [toggle] — nay danh sách ấy
 * nằm thẳng trên trang. Ba chuỗi riêng của nó (`kachi_topstrip_more/_on/_none`) đã xoá khỏi cả hai tệp tài nguyên;
 * để lại là ba khoá mồ côi (`LauncherI18nContractTest` đỏ).
 *
 * ## Vì sao GẤP theo lĩnh vực, không bày phẳng 123 ô
 * R4: *mỗi nhóm Cài đặt ≤ 2 màn cuộn*. Bày phẳng là ~31 hàng ô — một mình mục chọn chip đã dài gấp đôi cả nhóm.
 * Hai lối gọn khác đều tệ hơn: **ô tìm kiếm** cần bàn phím giữa lúc lái (và không trả lời được câu *"có những gì"*),
 * **phân trang** làm người dùng mất dấu vị trí. Gấp theo lĩnh vực giữ được cả hai thứ cần: nhìn một lượt thấy hết
 * *loại* thông tin có thể đặt (9 dòng tiêu đề, mỗi dòng kèm số mục), mở đúng chỗ mình cần. Mặc định mở những lĩnh
 * vực **đang có chip trên thanh** — luật đó thuộc `:core` ([ChipSection.open]) để kiểm được off-car.
 */
class TopStripPicker(
    private val context: Context,
    private val rows: SettingsRows,
    initial: TopStripConfig,
    private val onToggle: (String, Boolean) -> Unit,
    // #15 — persist CẢ config khi đổi thứ tự chip (dời trái/phải). null = không cho dời (đường test cũ).
    private val onConfig: ((TopStripConfig) -> Unit)? = null,
) {
    private var strip = initial
    private val tiles = HashMap<String, View>()

    /**
     * Lĩnh vực người dùng đã tự mở/gấp trong **phiên này**, đè lên mặc định của [ChipSection.open].
     *
     * `null` = chưa động tới ⇒ theo mặc định của `:core`. Giữ theo [Domain] chứ không theo chỉ số khối: [rebuild]
     * dựng lại danh sách khối sau mỗi cú bấm (một mục vừa bật sẽ **rời** khối lĩnh vực sang khối *"đang bật"*),
     * nên chỉ số khối đổi nghĩa giữa hai lượt — giữ theo chỉ số thì bấm một ô là mấy khối khác tự đóng/mở.
     */
    private val folded = HashMap<Domain, Boolean>()

    /**
     * Dòng *"N mục chưa kiểm trên xe"* (U7 · R6) — giữ tham chiếu vì [rebuild] đổi CHÍNH danh sách mà nó đếm.
     *
     * ⚠ [SOÁT S3/U7 2026-09-13] Bản đầu chỉ thêm dòng này một lần trong [section]: bấm thêm/bỏ một chip là lưới
     * đổi mà con số đứng yên ⇒ trang tự nói hai điều khác nhau về cùng một danh sách, đúng họ lỗi "hai bản sao
     * của một quyết định" mà [PickerBadge] vừa gom lại để tránh.
     */
    private var unverifiedRow: TextView? = null

    /** Khối ô — giữ để [toggle] còn dựng lại được đúng khối này, không dựng lại cả trang. */
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun has(id: String): Boolean = strip.has(id)

    /**
     * RW0 vùng thứ ba — chọn chip cho thanh trạng thái trên.
     *
     * **Chỉ nhận mục ĐỌC** — lý do (đích chạm 24dp là quá nhỏ để bắn lệnh xe + thanh trên là dòng trạng thái) ghi ở
     * KDoc [TopStripConfig]. Không phải bỏ sót; R11 nới *số lượng* chứ không nới *loại*.
     */
    fun section(parent: LinearLayout) {
        parent.addView(rows.sectionLabel(context.getString(R.string.kachi_topstrip_title)))
        parent.addView(rows.note(context.getString(R.string.kachi_topstrip_hint, TopStripConfig.CAP)))
        // U7 · R6 — cùng một câu, cùng một phép đếm với ngăn kéo ([PickerBadge]). Trước đây màn này KHÔNG vẽ dấu
        // "chưa kiểm" nào cả, nên cùng một mã mà hai màn chọn nói hai điều khác nhau về độ tin cậy của nó.
        unverifiedRow = rows.note("").also { parent.addView(it) }
        parent.addView(grid)
        rebuild()
    }

    /**
     * Dựng lại toàn bộ các khối từ cấu hình hiện tại (chip vừa bật phải nhảy lên khối *"đang bật"* ngay).
     *
     * Dựng lại **cả** danh sách thay vì chỉ tô lại ô vừa bấm là có chủ ý: một cú bấm **chuyển ô sang khối khác**
     * (bật ⇒ rời lĩnh vực, lên đầu; tắt ⇒ về lại lĩnh vực), và con số `N/CAP` cùng dòng *"chưa kiểm"* đều đổi
     * theo. Tô tại chỗ thì ba thứ đó lệch nhau — đúng lỗi đã đo ở dòng "chưa kiểm" hồi U7.
     */
    private fun rebuild() {
        grid.removeAllViews()
        tiles.clear()
        val sections = TopStripConfig.picks(strip)
        // Con số đọc lại từ CHÍNH danh sách vừa tính — không phải từ một ảnh chụp lúc dựng trang. Đếm trên MỌI ô
        // đang bày (cả khối đang gấp): câu này nói về danh sách, không nói về phần đang nhìn thấy.
        unverifiedRow?.let { row ->
            val note = PickerBadge.unverifiedNote(context, sections.flatMap { it.picks })
            row.text = note.orEmpty()
            row.visibility = if (note == null) View.GONE else View.VISIBLE
        }
        sections.forEach { block(it) }
    }

    /** Một khối: tiêu đề (bấm để gấp/mở, trừ khối *"đang bật"*) + lưới ô nếu đang mở. */
    private fun block(s: ChipSection) {
        val open = if (s.on) true else s.domain?.let { folded[it] } ?: s.open
        grid.addView(header(s, open))
        if (!open) return
        // `cols =` dạng THAM SỐ TÊN, không truyền theo vị trí: `PickGridColumnContractTest` quét chính chuỗi
        // `cols = <nguồn>` để chốt hai màn chọn cùng một nguồn số cột — truyền theo vị trí thì bài canh mù.
        CapabilityTileGrid.rows(context, grid, s.picks.size, cols = CapabilityPicker.COLS) { i -> tile(s.picks[i]) }
    }

    /**
     * Tiêu đề một khối.
     *
     * Khối *"đang bật"* mang con số **`N/CAP`** — nó trả lời câu hỏi hay gặp nhất ở màn này (*"còn đặt thêm được
     * mấy cái?"*) ngay tại chỗ người dùng đang nhìn, thay vì bắt đếm ô. Khối lĩnh vực mang **số mục** và một dấu
     * ▸/▾: khối gấp mà không nói có bao nhiêu thì người dùng không biết có đáng mở hay không.
     *
     * ⚠ Dấu gấp/mở là **ký hiệu**, không phải chữ (nên không đi qua `getString`) — `LauncherI18nContractTest` chỉ
     * bắt literal có CHỮ CÁI, đúng ranh giới đó.
     */
    private fun header(s: ChipSection, open: Boolean): View {
        // Biến cục bộ: [ChipSection.domain] là thuộc tính công khai của MODULE KHÁC (`:core`) nên Kotlin không ép
        // kiểu thông minh được ở nhánh `!= null` — gán ra đây một lần thay vì rải `!!`.
        val d = s.domain
        val title = when {
            s.on -> context.getString(R.string.kachi_topstrip_count, strip.ids.size, TopStripConfig.CAP)
            d != null -> (if (open) "▾  " else "▸  ") + d.displayLabel + "  ·  " + s.picks.size
            else -> CapabilityPicker.SINGLES_TITLE + "  ·  " + s.picks.size
        }
        val v = rows.subHeader(title)
        if (s.on || d == null) return v
        // Đích chạm: cả dòng tiêu đề, cao ≥ [Sp.TOUCH] — một dòng chữ cao ~20dp là đích chạm dưới chuẩn, mà đây là
        // cú bấm duy nhất để tới 120+ mục còn lại.
        v.minHeight = dpi(context, Sp.TOUCH)
        v.gravity = Gravity.CENTER_VERTICAL
        v.setOnClickListener {
            folded[d] = !open
            rebuild()
        }
        return v
    }

    /** Ô chọn chip — dựng riêng, sự kiện riêng, bảng riêng (xem cảnh báo ở KDoc lớp). */
    private fun tile(pick: CapabilityPick): View {
        val t = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Căn NGANG-giữa, DỌC-TRÊN: ô cao MATCH_PARENT theo hàng ([CapabilityTileGrid]) nên căn giữa dọc sẽ đẩy
            // icon ô nhãn-một-dòng xuống lệch với ô nhãn-hai-dòng cùng hàng. Cùng lẽ với `CapabilityGridSection`.
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            // P2 · AC2.6 — icon phải mang trạng thái CHỌN, không chỉ cái nền.
            //
            // ⚠ [SOÁT 2026-09-17] Bản đầu để `selected` rơi về mặc định `false` ⇒ trên bảng TỐI ở cỡ
            // [KachiSpace.ICON_L], `KachiIcons.tint` áp bộ lọc *chưa chọn* (bão hoà 35 % · mờ 72 %) cho **cả** ô đang
            // bật, và `rebuild()` sau mỗi cú bấm dựng lại ô nên nó không bao giờ tự đúng lại. Đây là lưới dày nhất
            // của app (88 ô) — đúng chỗ cần phân biệt nhất. `AppDrawer` đã làm việc này qua `PickerBadge.retint`;
            // ở đây ô biết ngay lúc dựng nên truyền thẳng.
            addView(PickerBadge.icon(context, iconRes(pick), pick.needsBadge, Sp.ICON_L, strip.has(pick.id)))
            addView(TextView(context).apply {
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
            // #15 — chip đang bật + cho phép dời ⇒ hàng ◀ ▶ để đổi vị trí trên thanh (thứ tự = thứ tự hiện).
            if (onConfig != null && strip.has(pick.id)) {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                    setPadding(0, dpi(context, Sp.XS), 0, 0)
                    addView(arrowBtn("◀") { move(pick.id, earlier = true) })
                    addView(arrowBtn("▶") { move(pick.id, earlier = false) })
                })
            }
            setOnClickListener { toggle(pick.id) }
        }
        tiles[pick.id] = t
        paint(pick.id)
        return t
    }

    /**
     * Bật/tắt một chip. Luật (trần · chỉ mục đọc) ở `:core`; nếu cấu hình KHÔNG đổi thì **nói ra** thay vì im lặng bỏ
     * qua cú bấm — im lặng làm người dùng tưởng nút hỏng (bài học từ nút bố cục sẵn ở P9).
     */
    fun toggle(id: String) {
        val on = !strip.has(id)
        val next = strip.setEnabled(id, on)
        if (next == strip) {
            Toast.makeText(
                context,
                context.getString(R.string.kachi_topstrip_full, TopStripConfig.CAP),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        strip = next
        onToggle(id, on)
        // Ô vừa bấm ĐỔI KHỐI (bật ⇒ lên khối "đang bật", tắt ⇒ về lĩnh vực của nó) nên tô lại tại chỗ là chưa đủ —
        // xem KDoc [rebuild].
        rebuild()
    }

    /** #15 — dời một chip đang bật sớm/muộn hơn trong thứ tự hiện trên thanh; persist cả config. */
    fun move(id: String, earlier: Boolean) {
        val cb = onConfig ?: return
        val next = if (earlier) strip.moveEarlier(id) else strip.moveLater(id)
        if (next == strip) return
        strip = next
        cb(next)
        rebuild()
    }

    private fun arrowBtn(glyph: String, onTap: () -> Unit): View = TextView(context).apply {
        text = glyph; setTextColor(c(KachiTheme.INK))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
        gravity = Gravity.CENTER
        val pad = dpi(context, Sp.S)
        setPadding(pad, dpi(context, Sp.XS), pad, dpi(context, Sp.XS))
        setOnClickListener { onTap() }
    }

    private fun paint(id: String) {
        val t = tiles[id] ?: return
        // VISUAL-REFRESH P1 · T3 — hai trạng thái đi qua CÙNG một hàm dựng bề mặt, chỉ khác `tone`; sắc lĩnh vực
        // giúp mắt tìm vùng trong một lưới trộn nhiều nhóm (đây là lưới dày nhất của app: 88 ô).
        val domain = CapabilityCatalog.pick(id)?.domain
        t.background = KachiTheme.surface(
            context, Sp.RADIUS_L, if (strip.has(id)) SurfaceTone.ACTIVE else SurfaceTone.NEUTRAL, domain,
        )
    }

    /** Icon của khả năng; chưa map → icon đại diện nhóm (khỏi ô trống icon). */
    private fun iconRes(pick: CapabilityPick): Int {
        val r = KachiIcons.res(pick.icon, Sp.ICON_L)
        if (r != 0) return r
        val d = pick.domain ?: return 0
        return KachiIcons.res(WidgetCatalog.iconFor(d), Sp.ICON_L)
    }
}
