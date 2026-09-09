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

    /** Gán nội dung cho 1 ô (0..3); index ngoài phạm vi → giữ nguyên. */
    fun withSlot(index: Int, content: SlotContent): WorkspaceState {
        if (index !in slots.indices) return this
        return copy(slots = slots.toMutableList().also { it[index] = content })
    }

    /** Đổi preset (giữ nguyên gán ô theo chỉ số — ô ẩn vẫn nhớ). */
    fun withPreset(preset: LayoutPreset): WorkspaceState = copy(preset = preset)

    /** Xoá 1 ô về trống. */
    fun clearSlot(index: Int): WorkspaceState = withSlot(index, SlotContent.Empty)

    /** Các ô ĐANG hiện theo preset (N ô đầu). */
    fun visibleSlots(): List<SlotContent> = slots.take(preset.slotCount)

    companion object {
        /** Số ô tối đa (khớp QUAD). Trạng thái luôn giữ đủ 4 để ô ẩn nhớ nội dung khi đổi preset. */
        const val SLOT_CAP = 4
    }
}
