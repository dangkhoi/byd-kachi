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

    /** Gán nội dung cho 1 ô (0 tới [SLOT_CAP]−1); index ngoài phạm vi → giữ nguyên. */
    fun withSlot(index: Int, content: SlotContent): WorkspaceState {
        if (index !in slots.indices) return this
        return copy(slots = slots.toMutableList().also { it[index] = content })
    }

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
