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
 * ## L6 · ô TRONG SUỐT tạm ([cleared]) — owner 03/10
 * *"Khi app bị tắt thì trả về transparent luôn"* · *"Ô widget cũng cho tắt đc"*: app LƯU của ô chết / bị tắt, hoặc
 * widget bị tắt ⇒ ô hiện như KHUNG TRỐNG (trong suốt, chỉ ⇄) mà lớp LƯU KHÔNG đổi — cùng luật "tạm" ở trên: khởi động
 * lại / đổi hồ sơ / đổi bố cục là ô về đúng hồ sơ. Ai quyết khi nào ô vào trạng thái này: [SlotRevertPlan] (một nguồn sự
 * thật cho "LƯU vs đang HIỆN"). Id widget bên thứ ba KHÔNG bị thu hồi vì `AppWidgetIds.used` đọc lớp LƯU.
 *
 * ## Khi nào lớp tạm mất (R1.6)
 * Tiến trình chết (RAM) · đổi hồ sơ (`reload` dựng state mới) · đổi bố cục ([HomeUiState] chỗ gọi xoá) · ô bị sửa bằng
 * đường LƯU ([afterSave]).
 */
data class SlotOverlay(val entries: Map<Int, String> = emptyMap(), val cleared: Set<Int> = emptySet()) {

    val isEmpty: Boolean get() = entries.isEmpty() && cleared.isEmpty()

    /**
     * Đặt tạm [pkg] vào ô [index] (0-based). Gói đã có ở một mục tạm khác ⇒ mục đó bị gỡ (một app một ô ngay trong lớp
     * tạm, để thứ tự áp không quyết định ô nào thắng). Ô đang trong suốt tạm ⇒ thôi trong suốt (app mới hiện ra).
     */
    fun place(index: Int, pkg: String): SlotOverlay {
        if (index !in 0 until WorkspaceState.SLOT_CAP || pkg.isBlank()) return this
        return SlotOverlay(entries.filter { (i, p) -> i != index && p != pkg } + (index to pkg), cleared - index)
    }

    /** Bỏ mục tạm (app đặt tạm HOẶC trong suốt tạm) ở ô [index] — ô hiện lại nội dung LƯU. */
    fun drop(index: Int): SlotOverlay =
        if (index in entries || index in cleared) SlotOverlay(entries - index, cleared - index) else this

    /** L6 — ô [index] hiện như KHUNG TRỐNG (trong suốt) mà lớp LƯU giữ nguyên; mục app tạm của ô (nếu có) bị bỏ. */
    fun clear(index: Int): SlotOverlay {
        if (index !in 0 until WorkspaceState.SLOT_CAP) return this
        return if (index in cleared && index !in entries) this else SlotOverlay(entries - index, cleared + index)
    }

    /**
     * Đường LƯU vừa ghi vào các ô [touched] (và có thể đặt [pkgs] vào đó) ⇒ gỡ mọi mục tạm ở các ô ấy và mọi mục tạm
     * đang giữ một trong [pkgs]. Thiếu vế thứ hai thì người dùng chọn P cho ô 2 bằng ngăn kéo trong khi P còn tạm ở
     * ô 4 ⇒ [applyTo] đặt P lại vào ô 4 và xoá P khỏi ô 2 — thao tác LƯU tường minh bị lớp tạm nuốt mất. Ô trong suốt
     * tạm mà người dùng vừa chọn nội dung (⇄ / ngăn kéo) ⇒ thôi trong suốt.
     */
    fun afterSave(touched: Collection<Int>, pkgs: Collection<String> = emptyList()): SlotOverlay {
        if (isEmpty) return this
        val keep = entries.filter { (i, p) -> i !in touched && p !in pkgs }
        val still = cleared - touched.toSet()
        return if (keep.size == entries.size && still.size == cleared.size) this else SlotOverlay(keep, still)
    }

    /**
     * Bố cục đang HIỆN = lớp LƯU [saved] + các mục tạm (mỗi mục qua [WorkspaceState.withSlot] ⇒ một app một ô) + các ô
     * trong suốt tạm ([WorkspaceState.clearSlot]). Hai tập không chồng nhau ([place]/[clear] gỡ lẫn nhau).
     */
    fun applyTo(saved: WorkspaceState): WorkspaceState {
        val placed = entries.entries.sortedBy { it.key }.fold(saved) { ws, (i, p) -> ws.withSlot(i, SlotContent.App(p)) }
        return cleared.sorted().fold(placed) { ws, i -> ws.clearSlot(i) }
    }

    /** Ô [index] đang được lớp tạm giữ một APP không. */
    fun holds(index: Int): Boolean = index in entries

    companion object {
        val EMPTY = SlotOverlay()
    }
}
