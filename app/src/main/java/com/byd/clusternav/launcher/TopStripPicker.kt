package com.byd.clusternav.launcher

import android.graphics.drawable.GradientDrawable
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
 *  3. **Nút "Thêm chip khác…"** ([openMore]): lối GIỮ-ô-ở-lưới-123 đã mất cùng lưới đó khi R-UI (m) bỏ lưới khỏi
 *     Settings. Không có đường thay thế thì người dùng **mất hẳn** khả năng đưa một datum bất kỳ lên thanh trạng
 *     thái (R8 *"không tính năng nào mất"*), nên đường đó nay là một hộp thoại danh sách — cùng bộ dữ liệu
 *     [TopStripConfig.choices], cùng [toggle], không thêm một lưới thứ hai vào trang.
 */
class TopStripPicker(
    private val context: Context,
    private val rows: SettingsRows,
    initial: TopStripConfig,
    private val onToggle: (String, Boolean) -> Unit,
) {
    private var strip = initial
    private val tiles = HashMap<String, View>()

    /**
     * Dòng *"N mục chưa kiểm trên xe"* (U7 · R6) — giữ tham chiếu vì [rebuild] đổi CHÍNH danh sách mà nó đếm.
     *
     * ⚠ [SOÁT S3/U7 2026-09-13] Bản đầu chỉ thêm dòng này một lần trong [section]: bấm thêm/bỏ một chip là lưới
     * đổi mà con số đứng yên ⇒ trang tự nói hai điều khác nhau về cùng một danh sách, đúng họ lỗi "hai bản sao
     * của một quyết định" mà [PickerBadge] vừa gom lại để tránh.
     */
    private var unverifiedRow: TextView? = null

    /** Hàng ô — giữ để [toggle] từ hộp thoại còn dựng lại được đúng khối này, không dựng lại cả trang. */
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun has(id: String): Boolean = strip.has(id)

    /**
     * RW0 vùng thứ ba — chọn chip cho thanh trạng thái trên.
     *
     * **Chỉ nhận mục ĐỌC** — lý do (đích chạm 24dp là quá nhỏ để bắn lệnh xe + thanh trên là dòng trạng thái) ghi ở
     * KDoc [TopStripConfig]. Không phải bỏ sót.
     *
     * **Chỉ bày 3 chip dựng sẵn + chip đang chọn** (≤ 7 ô) chứ không bày cả 123 datum: trang này cố ý ngắn (R4 —
     * mỗi nhóm ≤ 2 màn cuộn). Muốn đặt một datum bất kỳ thì bấm **Thêm chip khác…** ngay dưới.
     */
    fun section(parent: LinearLayout) {
        parent.addView(rows.sectionLabel(context.getString(R.string.kachi_topstrip_title)))
        parent.addView(rows.note(context.getString(R.string.kachi_topstrip_hint, TopStripConfig.CAP)))
        // U7 · R6 — cùng một câu, cùng một phép đếm với ngăn kéo ([PickerBadge]). Trước đây màn này KHÔNG vẽ dấu
        // "chưa kiểm" nào cả, nên cùng một mã mà hai màn chọn nói hai điều khác nhau về độ tin cậy của nó.
        unverifiedRow = rows.note("").also { parent.addView(it) }
        parent.addView(grid)
        rebuild()
        parent.addView(rows.button(context.getString(R.string.kachi_topstrip_more)) { openMore() })
    }

    /** Dựng lại hàng ô từ cấu hình hiện tại (chip vừa thêm từ hộp thoại phải hiện ra ngay). */
    private fun rebuild() {
        grid.removeAllViews()
        tiles.clear()
        // `shown()` chứ không phải `choices().filter{…}`: từ U6 có mã CỐ Ý ẨN khỏi `choices()` mà `decode()` vẫn giữ
        // (khoá lưu bền không được mất). Lọc trên `choices()` thì chip đặt từ bản trước hiện trên thanh nhưng không
        // còn ô nào để bấm gỡ — xem KDoc [TopStripConfig.shown].
        val shown = TopStripConfig.shown(strip)
        // Con số đọc lại từ CHÍNH `shown` vừa tính — không phải từ một ảnh chụp lúc dựng trang.
        unverifiedRow?.let { row ->
            val note = PickerBadge.unverifiedNote(context, shown)
            row.text = note.orEmpty()
            row.visibility = if (note == null) View.GONE else View.VISIBLE
        }
        // `cols =` dạng THAM SỐ TÊN, không truyền theo vị trí: `PickGridColumnContractTest` quét chính chuỗi
        // `cols = <nguồn>` để chốt hai màn chọn cùng một nguồn số cột — truyền theo vị trí thì bài canh mù.
        CapabilityTileGrid.rows(context, grid, shown.size, cols = CapabilityPicker.COLS) { i -> tile(shown[i]) }
    }

    /**
     * Hộp thoại "đặt một datum BẤT KỲ lên thanh trạng thái" — thay cho lối GIỮ một ô ở lưới 123 (đã bỏ khỏi Settings).
     *
     * Danh sách bày **mọi** thứ đặt được ([TopStripConfig.choices] — chính hàm mà [section] lọc), có dấu ✓ trước mục
     * đang bật để một danh sách dài vẫn đọc được trạng thái. Chọn một mục = [toggle] nó, tức cùng một đường với chạm
     * ô ở trên (kể cả câu nhắc khi đã đầy trần).
     */
    private fun openMore() {
        val all = TopStripConfig.choices()
        SettingsDialogs.pick(
            context,
            context.getString(R.string.kachi_topstrip_more),
            all.map {
                if (strip.has(it.id)) context.getString(R.string.kachi_topstrip_on, it.displayLabel)
                else it.displayLabel
            },
            context.getString(R.string.kachi_topstrip_none),
        ) { index ->
            toggle(all[index].id)
            rebuild()
        }
    }

    /** Ô chọn chip — dựng riêng, sự kiện riêng, bảng riêng (xem cảnh báo ở KDoc lớp). */
    private fun tile(pick: CapabilityPick): View {
        val t = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Căn NGANG-giữa, DỌC-TRÊN: ô cao MATCH_PARENT theo hàng ([CapabilityTileGrid]) nên căn giữa dọc sẽ đẩy
            // icon ô nhãn-một-dòng xuống lệch với ô nhãn-hai-dòng cùng hàng. Cùng lẽ với `CapabilityGridSection`.
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            addView(PickerBadge.icon(context, iconRes(pick), pick.needsBadge, Sp.ICON_L))
            addView(TextView(context).apply {
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
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
        paint(id)
    }

    private fun paint(id: String) {
        val t = tiles[id] ?: return
        t.background = if (strip.has(id)) GradientDrawable().apply {
            cornerRadius = dpi(context, Sp.RADIUS_L).toFloat()
            setColor(c(KachiTheme.ACCENT_SOFT)); setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.ACCENT))
        } else KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)
    }

    /** Icon của khả năng; chưa map → icon đại diện nhóm (khỏi ô trống icon). */
    private fun iconRes(pick: CapabilityPick): Int {
        val r = KachiTheme.iconRes(pick.icon)
        if (r != 0) return r
        val d = pick.domain ?: return 0
        return KachiTheme.iconRes(WidgetCatalog.iconFor(d))
    }
}
