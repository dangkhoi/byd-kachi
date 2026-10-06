package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ShortcutScrollKeep.Wanted

/**
 * ═══ 2.93 wave 2A · SHORTCUT-SCROLL-REBUILD — vị trí cuộn của lưới lối tắt `w_apps` sống qua lượt dựng VIEW MỚI của ô ═══════
 *
 * Spec `docs/specs/kachi-293-wave2a.html` §4.3. Senior review SLOT Pass 2 mục 4 [P3, SUY đọc mã]: [ShortcutScrollKeep] (R1/R2)
 * giữ lựa chọn của người lái trong CÙNG một `ShortcutIconsView`; còn khi ô dựng một view MỚI — đổi Sáng/Tối
 * (`WorkspaceView.restyle`), đổi đơn vị/ảnh (`rebuildWidgetSlots`), Activity dựng lại (uiMode/locale không nằm trong
 * `configChanges`) — view mới bắt đầu từ [Wanted.ORIGIN] ⇒ dải về đầu.
 *
 * Luật: nhớ [Wanted] mới nhất của người lái theo khoá Ô ([slotKey] = chỉ số ô + tổ hợp widget của ô) TRONG TIẾN TRÌNH. View mới
 * của cùng khoá bắt đầu từ đó (kẹp theo khung mới bằng đúng [ShortcutScrollKeep.applied] như lượt dựng lại trong cùng view).
 * Đổi tổ hợp / dời widget sang ô khác ⇒ khoá khác ⇒ từ đầu. Chỉ RAM (vị trí cuộn là tiện nghi hiển thị — tiến trình chết thì
 * về đầu như mọi launcher; CLAUDE.md §5 chỉ đòi bền cho state đổi NGOÀI hệ thống). [CAPACITY] khoá mới dùng gần nhất (LRU).
 * Gọi trên luồng vẽ; khoá nội bộ chỉ để an toàn nếu ai gọi từ luồng khác.
 */
object ShortcutScrollMemory {

    /** Trần số khoá (LRU) — một màn chính có tối đa vài chục ô; mỗi lần sửa tổ hợp tạo khoá mới, khoá cũ rơi dần. */
    const val CAPACITY = 16

    private val map = object : LinkedHashMap<String, Wanted>(CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Wanted>?): Boolean = size > CAPACITY
    }

    /** Khoá của lưới `w_apps` trong ô [slot] chứa tổ hợp [ids] (đúng thứ tự). */
    fun slotKey(slot: Int, ids: List<String>): String = "$slot|" + ids.joinToString(",")

    /** Lựa chọn đã nhớ của [key]; `null` = chưa cuộn lần nào (view mới bắt đầu từ đầu). */
    fun recall(key: String): Wanted? = synchronized(map) { map[key] }

    /** Người lái vừa chọn [w] ở ô [key]. [Wanted.ORIGIN] (khung không cuộn) ⇒ quên khoá. */
    fun remember(key: String, w: Wanted) {
        synchronized(map) { if (w == Wanted.ORIGIN) map.remove(key) else map[key] = w }
    }
}
