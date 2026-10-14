package com.byd.clusternav.launcher

/**
 * ═══ R30 — BUNG Ô: máy trạng thái + khung (thuần, `:core`) ═══════════════════════════════════════════════════════════
 *
 * Owner 14/10: *"thêm 1 nút nữa là nút fullscreen --> nhưng không phải fullscreen màn hình, mà chỉ bung khung đó ra full screen
 * của launcher thôi, ẩn các khung khác đi, khi nhấn lại thì scale về lại như cũ"*. Spec `kachi-301-cast-steps-dialog-ia.html`
 * R30 / §4.8.
 *
 * ## Bốn điều phải đúng
 *  1. **Một ô một lúc** — [toggle] một ô khác khi đã có ô bung ⇒ [Change.None] (khung kia đang ẩn, chỉ đường mã chạm tới được).
 *  2. **Chỉ khung của ô bung đổi** — [rects] thay ĐÚNG một khung bằng trọn vùng ô; khung khác giữ nguyên byte ⇒ màn ảo của các
 *     ô ẩn KHÔNG đổi cỡ (`VdAppHost.resize` trùng cỡ là no-op) ⇒ đúng MỘT lượt đổi cỡ mỗi lần bung / thu.
 *  3. **TẠM** — lớp này không đọc/ghi gì bền; nó sống trong RAM của MỘT cây workspace (`WorkspaceExpand` ở `:app`). Đổi hồ sơ /
 *     đổi bố cục / dựng lại khung ⇒ [clear]. Tiến trình chết ⇒ mất theo (không marker, không prefs).
 *  4. **Không nút chết** — [offered]: ô trống, ô App không bộ chiếu (thẻ "Chạm để mở" — bung không có gì để xem), widget đã chết
 *     và bố cục một ô không có nút này.
 */
class SlotExpand {

    /** Vì sao thu — chỉ để ghi log (cả sáu lối đều về ĐÚNG bố cục cũ). */
    enum class Why { TAP, BACK, HOME, PROFILE, LAYOUT, REBUILD }

    sealed class Change {
        /** Không đổi gì (ô ngoài bố cục / bố cục một ô / ô khác đang bung / không có gì để thu). */
        object None : Change()

        /** Ô [index] vừa bung. */
        data class Expand(val index: Int) : Change()

        /** Ô [index] vừa thu về, lý do [why]. */
        data class Collapse(val index: Int, val why: Why) : Change()
    }

    /** Ô đang bung (0-based); `null` = bố cục thường. */
    var expanded: Int? = null
        private set

    /**
     * Chạm nút của ô [index] trong bố cục [slotCount] ô: ô đang bung ⇒ thu (`TAP`); không ô nào bung và ô hợp lệ, bố cục ≥ 2 ô
     * ⇒ bung; còn lại ⇒ [Change.None].
     */
    fun toggle(index: Int, slotCount: Int): Change {
        val cur = expanded
        return when {
            cur == index -> clear(Why.TAP)
            cur != null -> Change.None
            slotCount < 2 || index !in 0 until slotCount -> Change.None
            else -> { expanded = index; Change.Expand(index) }
        }
    }

    /** Thu ô đang bung (nếu có). Gọi lại vô hại ⇒ [Change.None]. */
    fun clear(why: Why): Change {
        val cur = expanded ?: return Change.None
        expanded = null
        return Change.Collapse(cur, why)
    }

    /** Khung ô [index] có được hiện không (bố cục thường: mọi ô; đang bung: chỉ ô bung). */
    fun visible(index: Int): Boolean = expanded.let { it == null || it == index }

    /**
     * Khung pixel đang hiệu lực: [base] (bố cục của hồ sơ, `EffectiveLayout.rects`) với khung của ô bung thay bằng trọn vùng
     * [width]×[height]. Không bung / ô bung ngoài [base] ⇒ trả NGUYÊN [base] (cùng đối tượng).
     */
    fun rects(base: List<SlotRect>, width: Int, height: Int): List<SlotRect> {
        val e = expanded?.takeIf { it in base.indices } ?: return base
        return base.mapIndexed { i, r -> if (i == e) SlotRect(r.index, 0, 0, width, height) else r }
    }

    companion object {
        /**
         * Loại ô này có nút bung không (R30.1): widget đang sống (kể cả widget Kachi như xe 3D) và ô App mà app VẼ trong khung
         * (màn ảo / ActivityView). Bố cục một ô do bên gọi xét ([SlotHeadActions.of] `multi`).
         */
        fun offered(kind: SlotHeadRest.Kind, projector: SlotHeadRest.Projector): Boolean = when (kind) {
            SlotHeadRest.Kind.WIDGET, SlotHeadRest.Kind.APPWIDGET_LIVE -> true
            SlotHeadRest.Kind.APP -> projector != SlotHeadRest.Projector.NONE
            SlotHeadRest.Kind.EMPTY, SlotHeadRest.Kind.APPWIDGET_DEAD -> false
        }
    }
}
