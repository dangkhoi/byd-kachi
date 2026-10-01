package com.byd.clusternav.launcher

import android.view.View
import android.view.ViewGroup

/**
 * ═══ PROFILE-SWITCH-SLOTS · R-A4 — LƯỚI: ô App không bao giờ ĐEN CÂM sau một lượt tháo-gắn cây view ═══════════════
 *
 * Gốc lỗi A đã sửa tận nơi ở [DockAreaLayout] (đổi viền/ẩn-hiện thanh nút không tháo vùng ô nữa — [DockAreaPlan]).
 * Lưới này dành cho mọi đường tháo-rồi-gắn-lại [WorkspaceView] **chưa biết** hôm nay: thiếu nó thì một đường như thế
 * lặp lại đúng triệu chứng đã đo — ô đen, không thẻ, không lỗi.
 *
 * ## Cơ chế [ĐO AOSP — android-10.0.0_r47 · android-12.0.0_r34]
 * - Tháo: `ViewGroup.dispatchDetachedFromWindow` đi qua con TRƯỚC rồi mới tới chính nó (A10 `ViewGroup.java:3806,
 *   3814`; A12 `:3948, 3956`) ⇒ mọi [VdAppHost] trong cây đã `release()` (cờ `released`, không bao giờ tạo lại màn ảo
 *   — `surfaceChanged` `if (released) return`) trước khi `WorkspaceView.onDetachedFromWindow` chạy.
 * - Gắn lại: `View.dispatchAttachedToWindow` gán `mAttachInfo` rồi gọi `onAttachedToWindow()` (A10 `View.java:19544,
 *   19568`; A12 `:20729, 20753`), và `ViewGroup` gọi phần đó cho CHÍNH NÓ trước khi đi qua con (A10
 *   `ViewGroup.java:3428-3438`; A12 `:3488-3498`) ⇒ trong `WorkspaceView.onAttachedToWindow` host cũ vẫn còn đó, đã nhả.
 * - `post` lúc đã gắn = `mAttachInfo.mHandler.post` (A10 `View.java:17850-17854`; A12 `:19028-19032`) ⇒ lượt dựng lại
 *   chạy SAU khi lượt gắn xong, không sửa danh sách con giữa lúc `dispatchAttachedToWindow` đang duyệt nó.
 *
 * ## Vì sao DỰNG LẠI Ô, không "hồi sinh" host
 * H2 (`SlotHostingLifecycleContractTest`): host đã nhả là rác — màn ảo của ô có thể đã về tay chủ khác qua
 * [SlotVdOwner]; cho nó tạo màn ảo lần nữa đúng là cách một `kachi-slot-*` không ai cầm mọc ra. Dựng lại ô đi qua
 * đường SẴN CÓ của `WorkspaceView` (`renderInternal(…, embedChanged = true)` — cùng nhánh P-bug2: dựng lại mọi ô App để
 * gắn bộ chiếu mới), không thêm lệnh `am`/`wm` nào.
 */
internal object SlotHostHeal {

    /** Có ô nào đang cầm một [VdAppHost] đã nhả không — tức ô đó sẽ đen câm nếu không dựng lại. */
    fun anyReleased(slots: List<View>): Boolean = slots.any { slot ->
        val g = slot as? ViewGroup
        g != null && (0 until g.childCount).any { (g.getChildAt(it) as? VdAppHost)?.isReleased == true }
    }
}
