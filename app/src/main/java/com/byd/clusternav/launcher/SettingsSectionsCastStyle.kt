package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.BelievedStyle
import com.byd.clusternav.modules.clustercast.simplified.CastStyle

/**
 * ═══ B1b · CLUSTER-RECT-OPTION — hàng **"Kiểu chiếu cụm: Bo tròn / Chữ nhật"** của nhóm Chiếu cụm ([SettingsCastSection]) ═══
 *
 * Owner 05/10: *"ủa chữ nhật làm luôn chứ, thêm option chọn là chữ nhật hay bo tròn là OK"*. Spec `kachi-289-field-fixes.html`
 * mục B1b. Tệp riêng vì `SettingsSectionsCast.kt` đã ~440 dòng (trần 500).
 *
 * ## Màn nói THẬT ba điều (R9)
 *  1. **Khi nào áp**: lựa chọn chỉ là prefs; áp ở lần nổ máy sau, hoặc khi cụm trống (đã tắt chiếu và màn ảo cụm không còn) —
 *     D4. Theme cụm KHÔNG BAO GIỜ được gửi từ màn này.
 *  2. **Cụm đang ở kiểu nào** khi phiên đang chạy khác lựa chọn (vd chọn Chữ nhật lúc đang chiếu Bo tròn) — đọc kiểu của
 *     PHIÊN (`castStyleSession`), không đọc lựa chọn.
 *  3. **Chữ nhật mất gì**: khung ADAS trắng bên phải là giao diện CỐ ĐỊNH của cụm ở kiểu này — phím menu chỉ đổi ADAS sang nhỏ,
 *     khung vẫn còn [ĐO QML fw 2602030 + owner trên xe 05/10]; số km/h gốc mất nên Kachi tự vẽ (không có khi chiếu CarPlay /
 *     Android Auto — D5).
 *
 * ## Ẩn hẳn khi đời xe không cho Chữ nhật
 * `bridge.castStyleOffered()` — chỉ Seal `car.type=138` (bảng B.2). Xe khác: không hàng nào, không "hiện mà bấm không ăn".
 *
 * Dựng lại được ([rebuild]) vì phụ thuộc trạng thái chiếu đang chạy — cùng khuôn `SettingsCastGeometryBlock`.
 */
class SettingsCastStyleBlock(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    private var holder: LinearLayout? = null

    fun build(body: LinearLayout) {
        holder = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }.also { body.addView(it) }
        rebuild()
    }

    fun rebuild() {
        val box = holder ?: return
        box.removeAllViews()
        if (!bridge.castStyleOffered()) return
        val chosen = bridge.castStyle()
        box.addView(rows.chipRow(
            label = context.getString(R.string.kachi_cast_style_label),
            options = listOf(
                CastStyle.CURVED.name to context.getString(R.string.kachi_cast_style_curved),
                CastStyle.RECT.name to context.getString(R.string.kachi_cast_style_rect),
            ),
            current = chosen.name,
        ) { code ->
            bridge.setCastStyle(CastStyle.parse(code))   // CHỈ prefs — 0 lệnh tới cụm
            rebuild()
        })
        if (chosen == CastStyle.RECT) {
            box.addView(rows.note(context.getString(R.string.kachi_cast_style_rect_note)))
            // Pass 3 · cluster-r2-5: chưa có quyền vẽ ⇒ Kachi không vẽ được km/h ⇒ lượt mở dùng Bo tròn — nói thật lý do.
            if (!bridge.castStyleReadoutDrawable()) box.addView(rows.note(context.getString(R.string.kachi_cast_style_rect_no_overlay)))
        }
        box.addView(rows.note(context.getString(R.string.kachi_cast_style_apply_note)))
        sessionNote(bridge.castStyleEffective())?.let { box.addView(rows.note(it)) }   // r3a-3: kiểu hiệu lực
        if (bridge.castStyleApplyOffered()) {
            box.addView(rows.button(context.getString(R.string.kachi_cast_style_apply_now)) {
                if (!bridge.applyCastStyleNow()) {
                    Toast.makeText(context, context.getString(R.string.kachi_cast_style_apply_busy), Toast.LENGTH_LONG).show()
                }
                rebuild()
            })
        }
    }

    /** "Cụm đang: …" — chỉ khi có phiên và kiểu cụm của phiên khác lựa chọn (hoặc chưa rõ). */
    private fun sessionNote(chosen: CastStyle): String? {
        val session = bridge.castStyleSession() ?: return null
        val now = when (session.believed) {
            BelievedStyle.UNKNOWN -> context.getString(R.string.kachi_cast_style_unknown)
            else -> styleName(session.frame)
        }
        if (session.believed != BelievedStyle.UNKNOWN && session.frame == chosen) return null
        return context.getString(R.string.kachi_cast_style_session, now)
    }

    private fun styleName(style: CastStyle): String = context.getString(
        if (style == CastStyle.RECT) R.string.kachi_cast_style_rect else R.string.kachi_cast_style_curved,
    )
}
