package com.byd.clusternav.launcher

/**
 * ═══ ĐẶT TẠM vào ô — lớp RAM đè lên bố cục đã lưu (thuần, `:core`) ═════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §2.2 · §4.4.4 (C2). Owner 01/10, đính chính nguyên văn:
 * *"việc đưa app vào khung trong quá trình chạy thì là tạm thời, không lưu đâu cả, lần sau khởi động lại launcher thì
 * vẫn dùng bố cục theo profile"*.
 *
 * ## Hai lớp, một luật
 *  - **Lớp LƯU** = [WorkspaceState] của hồ sơ (ghi bền qua `HomeViewModel.persist`) — ngăn kéo, ⇄, kéo-thả, Cài đặt.
 *  - **Lớp TẠM** = lớp này: ô → gói, chỉ sống trong RAM của tiến trình. Lối tắt kiểu *Ô n* và giọng nói *"mở X vào
 *    ô n"* chỉ ghi vào đây. `persist()` chỉ ghi lớp LƯU ⇒ lớp tạm không bao giờ chạm đĩa.
 *
 * Màn chính vẽ từ [applyTo] (*effective workspace*). Luật MỘT-APP-MỘT-Ô giữ qua CẢ HAI lớp vì mỗi mục tạm đi qua
 * chính [WorkspaceState.withSlot] — app đặt tạm vào ô n thì biến khỏi mọi ô khác trên màn (kể cả ô LƯU đang giữ nó).
 *
 * ## Khi nào lớp tạm mất (R1.6)
 * Tiến trình chết (RAM) · đổi hồ sơ (`reload` dựng state mới) · đổi bố cục ([HomeUiState] chỗ gọi xoá) · ô bị sửa bằng
 * đường LƯU ([afterSave]).
 */
data class SlotOverlay(val entries: Map<Int, String> = emptyMap()) {

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Đặt tạm [pkg] vào ô [index] (0-based). Gói đã có ở một mục tạm khác ⇒ mục đó bị gỡ (một app một ô ngay trong lớp
     * tạm, để thứ tự áp không quyết định ô nào thắng).
     */
    fun place(index: Int, pkg: String): SlotOverlay {
        if (index !in 0 until WorkspaceState.SLOT_CAP || pkg.isBlank()) return this
        return SlotOverlay(entries.filter { (i, p) -> i != index && p != pkg } + (index to pkg))
    }

    /** Bỏ mục tạm ở ô [index] — ô hiện lại nội dung LƯU. */
    fun drop(index: Int): SlotOverlay =
        if (index in entries) SlotOverlay(entries - index) else this

    /**
     * Đường LƯU vừa ghi vào các ô [touched] (và có thể đặt [pkgs] vào đó) ⇒ gỡ mọi mục tạm ở các ô ấy và mọi mục tạm
     * đang giữ một trong [pkgs]. Thiếu vế thứ hai thì người dùng chọn P cho ô 2 bằng ngăn kéo trong khi P còn tạm ở
     * ô 4 ⇒ [applyTo] đặt P lại vào ô 4 và xoá P khỏi ô 2 — thao tác LƯU tường minh bị lớp tạm nuốt mất.
     */
    fun afterSave(touched: Collection<Int>, pkgs: Collection<String> = emptyList()): SlotOverlay {
        if (entries.isEmpty()) return this
        val keep = entries.filter { (i, p) -> i !in touched && p !in pkgs }
        return if (keep.size == entries.size) this else SlotOverlay(keep)
    }

    /** Bố cục đang HIỆN = lớp LƯU [saved] + các mục tạm (mỗi mục qua [WorkspaceState.withSlot] ⇒ một app một ô). */
    fun applyTo(saved: WorkspaceState): WorkspaceState =
        entries.entries.sortedBy { it.key }.fold(saved) { ws, (i, p) -> ws.withSlot(i, SlotContent.App(p)) }

    /** Ô [index] đang được lớp tạm giữ không. */
    fun holds(index: Int): Boolean = index in entries

    companion object {
        val EMPTY = SlotOverlay()
    }
}
