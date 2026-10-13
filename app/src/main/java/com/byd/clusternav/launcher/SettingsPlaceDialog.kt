package com.byd.clusternav.launcher

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ KACHI-301 R11 — HỘP THÊM/SỬA MỘT MỤC SỔ ĐỊA CHỈ (khuôn Biểu mẫu) ════════════════════════════════════════
 *
 * Spec `kachi-301-cast-steps-dialog-ia.html` R11 + mẫu §4.4 *"Thêm địa chỉ"*; mô hình `kachi-voice-addresses.html` R1
 * (tên + địa chỉ bắt buộc, toạ độ tuỳ chọn). Chỗ gọi duy nhất: [SettingsDialogs.askPlace] (uỷ quyền một dòng).
 *
 * ## Bố cục
 *  - **Tên** = chip nhãn nhanh *Nhà · Công ty · Trường · Khác*; *Khác* mới hiện ô tên tự do. Sửa một mục ⇒ chip khớp
 *    tên (so bằng `SavedPlaces.keyOf` qua [PlaceForm.presetIndex]) được chọn sẵn, không khớp ⇒ *Khác* + tên cũ.
 *    Mục MỚI ⇒ KHÔNG chọn sẵn chip nào: chọn sẵn *Nhà* thì một cú Lưu vội sẽ GHI ĐÈ mục *Nhà* đang có (upsert
 *    theo tên) — người dùng phải tự chạm.
 *  - **Địa chỉ** — ô chữ.
 *  - **Nâng cao — toạ độ** gập sẵn (mở sẵn khi mục đã có toạ độ): một ô *"lat, lng"* — dạng người ta DÁN từ app bản
 *    đồ — kèm câu hướng dẫn.
 *
 * ## Kiểm TẠI CHỖ, một nguồn luật
 * Nút Lưu khoá khi thiếu tên/địa chỉ hoặc toạ độ hỏng; toạ độ hỏng ⇒ ô đỏ + câu lỗi ngay dưới ô (và phần Nâng cao
 * tự mở để lỗi không bị giấu). Luật là [PlaceForm.check] (`:core`) — dựng từ đúng các mảnh của `SavedPlaces.of`
 * (đường lưu), nên nút sáng ⇔ lưu được. Thông báo cũ của chỗ gọi (`kachi_places_need_fields`, sổ đầy) giữ nguyên
 * làm lưới cuối.
 *
 * Xoá nằm TRONG hộp sửa (nút đỏ trái): hàng danh sách trên xe là chỗ ngón tay lướt qua khi xe xóc — một nút xoá
 * không hoàn lại được cạnh nút sửa là ca bấm nhầm kinh điển.
 */
internal object SettingsPlaceDialog {

    /** Chip nhãn nhanh theo thứ tự hiện (tên lưu xuống = đúng nhãn đang hiện của chip). */
    private val PRESETS = listOf(R.string.kform_place_home, R.string.kform_place_work, R.string.kform_place_school)

    fun ask(
        context: Context,
        title: String,
        initial: SavedPlace?,
        onDelete: (() -> Unit)?,
        onOk: (label: String, address: String, coords: String) -> Unit,
    ) {
        val presetNames = PRESETS.map { context.getString(it) }
        val start = initial?.let { PlaceForm.presetIndex(it.name, presetNames) }
        // -1 = Khác; null = chưa chọn (mục mới).
        var chosen: Int? = when {
            initial == null -> null
            start != null && start >= 0 -> start
            else -> OTHER
        }
        var handle: KachiDialog.Handle? = null

        val other = KachiDialogs.field(
            context,
            label = null,
            value = if (chosen == OTHER) initial?.name.orEmpty() else "",
            hint = context.getString(R.string.kachi_places_hint_label),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
        )
        val address = KachiDialogs.field(
            context,
            label = context.getString(R.string.kachi_places_hint_address),
            value = initial?.query.orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
        )
        val coords = KachiDialogs.field(
            context,
            label = context.getString(R.string.kachi_places_hint_coords),
            value = initial?.let { SavedPlaces.formatCoords(it) }.orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT,
        )
        val coordsNote = KachiDialogs.caption(context, context.getString(R.string.kachi_places_coords_note))
        val advanced = Disclosure(context, context.getString(R.string.kform_place_advanced), listOf(coords.view, coordsNote))
        advanced.open = initial?.hasCoords == true

        val chips = ArrayList<KachiFormControls.Chip>()
        fun paintChips() {
            chips.forEachIndexed { i, chip -> chip.on = (if (i < PRESETS.size) i else OTHER) == chosen }
            other.view.visibility = if (chosen == OTHER) View.VISIBLE else View.GONE
        }
        (presetNames + context.getString(R.string.kform_place_other)).forEachIndexed { i, text ->
            chips += KachiFormControls.chip(context, text) {
                chosen = if (i < PRESETS.size) i else OTHER
                paintChips()
                if (chosen == OTHER) other.requestInput()   // QA-301: chọn "Khác" = muốn gõ ⇒ bàn phím lên ngay
                handle?.revalidate()
            }
        }
        paintChips()

        fun name(): String = when (val i = chosen) {
            null -> ""
            OTHER -> other.text
            else -> presetNames[i]
        }

        handle = KachiDialogs.form(
            context,
            title = title,
            subtitle = context.getString(R.string.kform_place_sub),
            icon = R.drawable.ic_nav_g,
            body = listOf(
                KachiFormControls.heading(context, context.getString(R.string.kform_place_name)),
                KachiFormControls.flow(context, chips),
                other.view,
                address.view,
                advanced.view,
            ),
            primaryLabel = context.getString(R.string.kachi_save),
            validate = {
                val chk = PlaceForm.check(name(), address.text, coords.text)
                coords.error = if (chk.coordsBad) context.getString(R.string.kform_place_coords_bad) else null
                if (chk.coordsBad) advanced.open = true
                chk.ok
            },
            dangerLabel = onDelete?.let { context.getString(R.string.kachi_delete) },
            onDanger = onDelete,
        ) { onOk(name(), address.text, coords.text) }
    }

    /** Chỉ số "Khác" — không trùng chỉ số chip nhãn nhanh nào. */
    private const val OTHER = -1

    /** Phần GẬP: một dòng bấm được `▸ nhãn` / `▾ nhãn` + các view con ẩn/hiện. */
    private class Disclosure(context: Context, private val title: String, private val children: List<View>) {
        private val head = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dpi(context, Sp.TOUCH)
            setTextColor(c(KachiTheme.ACCENT_INK))
            KachiType.apply(this, KachiType.BODY, bold = true)
            setOnClickListener { open = !open }
        }

        var open: Boolean = false
            set(value) {
                field = value
                head.text = (if (value) GLYPH_OPEN else GLYPH_CLOSED) + title
                head.contentDescription = title
                children.forEach { it.visibility = if (value) View.VISIBLE else View.GONE }
            }

        val view: View = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(head)
            children.forEach { addView(it, KachiDialog.stackLp(context, top = Sp.XS)) }
        }

        init { open = false }
    }

    /** Ký hiệu gập/mở — không phải chữ cần dịch. */
    private const val GLYPH_CLOSED = "▸ "
    private const val GLYPH_OPEN = "▾ "
}
