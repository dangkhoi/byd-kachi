package com.byd.clusternav.launcher

/**
 * Kế hoạch dựng lại ô cho một nhịp render workspace — quyết định THUẦN (không android.*) nên test được off-car.
 *
 * Trước gói 1, luật này nằm inline trong `WorkspaceView.render()` ⇒ không có test nào chạm tới được, và chính vì
 * vậy lỗi **P-bug2** (app trong ô không hiện lúc mở) sống sót: `render` so sánh CHỈ theo nội dung ô, nên khi kênh
 * nhúng (dadb shell) sẵn sàng SAU lần vẽ đầu thì ô App "không đổi nội dung" ⇒ không dựng lại ⇒ `VdAppHost` không
 * bao giờ được gắn. Tách ra đây để ca đó thành test hành vi thật (xem `WorkspaceRenderPlanTest`).
 */
sealed interface WorkspaceRenderPlan {
    /** Dựng lại TOÀN BỘ ô (đổi bố cục, hoặc số ô đang dựng không khớp số ô của bố cục). */
    object RebuildAll : WorkspaceRenderPlan

    /** Chỉ dựng lại các ô có chỉ số trong [rebuild] (tăng dần, không trùng). Rỗng = không làm gì. */
    data class PerSlot(val rebuild: List<Int>) : WorkspaceRenderPlan
}

/**
 * Bộ quyết định cho [WorkspaceRenderPlan]. Giữ NGUYÊN hành vi cập-nhật-tăng-dần của P-bug1 (thêm app vào ô khác
 * KHÔNG làm app đang chạy ở ô khác mở lại/nháy) — chỉ THÊM một lý do dựng lại: năng lực nhúng vừa đổi.
 */
object WorkspaceRenderPlanner {

    /**
     * Hai ô có CÙNG nội dung hay không (App cùng gói · Widget cùng danh sách id · cùng là ô trống).
     * Bản chuyển từ `WorkspaceView.sameContent` private cũ — hành vi giữ y nguyên.
     */
    fun sameContent(a: SlotContent, b: SlotContent): Boolean = when {
        a is SlotContent.App && b is SlotContent.App -> a.pkg == b.pkg
        a is SlotContent.Widget && b is SlotContent.Widget -> a.ids == b.ids
        a is SlotContent.Empty && b is SlotContent.Empty -> true
        else -> false
    }

    /**
     * Quyết định ô nào phải dựng lại khi áp [new] lên khung đang hiển thị [old].
     *
     * @param builtSlotCount số view ô ĐANG dựng (view-side); lệch số ô của bố cục ⇒ phải dựng lại tất cả.
     * @param statusChanged trạng thái xe đổi ⇒ widget **có nội dung đọc** phải dựng lại để làm mới GIÁ TRỊ (ô
     *   App/trống không cần; ô widget chỉ chứa nút HÀNH ĐỘNG cũng không cần — xem [hasReadContent]).
     * @param embedChanged **năng lực nhúng vừa đổi** (kênh shell null → có, hoặc ngược lại) ⇒ ô **App** phải dựng
     *   lại để gắn/nhả bộ chiếu. Đây là đầu vào chữa P-bug2; mặc định `false` nên mọi chỗ gọi cũ không đổi hành vi.
     */
    fun decide(
        old: WorkspaceState,
        new: WorkspaceState,
        builtSlotCount: Int,
        statusChanged: Boolean,
        embedChanged: Boolean = false,
    ): WorkspaceRenderPlan {
        if (old.preset != new.preset || builtSlotCount != new.preset.slotCount) return WorkspaceRenderPlan.RebuildAll
        val out = ArrayList<Int>()
        for (i in 0 until new.preset.slotCount) {
            val oc = old.slots.getOrElse(i) { SlotContent.Empty }
            val nc = new.slots.getOrElse(i) { SlotContent.Empty }
            val contentChanged = !sameContent(oc, nc)
            val widgetNeedsFreshValues = statusChanged && nc is SlotContent.Widget && hasReadContent(nc)
            val appNeedsHostAttach = embedChanged && nc is SlotContent.App
            if (contentChanged || widgetNeedsFreshValues || appNeedsHostAttach) out.add(i)
        }
        return WorkspaceRenderPlan.PerSlot(out)
    }

    /**
     * Ô widget này có thứ gì **đọc từ xe** để làm mới không.
     *
     * Từ RW0, ô giữa màn nhận được cả **HÀNH ĐỘNG** ([CapabilityKind.WRITE] — nút bấm). Ô chỉ chứa nút thì trạng thái
     * xe đổi KHÔNG có gì để làm mới, nhưng luật cũ vẫn dựng lại nó **2 nhịp/giây** trên xe ⇒ view bị tháo/gắn ngay
     * giữa cú chạm của người dùng (chuỗi MotionEvent đứt ⇒ **mất cú bấm**), và cú nháy 220ms của nút bấm-1-phát biến
     * mất. Đúng loại thiệt hại mà ràng buộc C5 dựng ra để chặn — trước đây chỉ chặn được cho ô App.
     *
     * Danh sách rỗng hoặc mã lạ ⇒ coi như CÓ nội dung đọc (giữ y hành vi cũ, không đoán).
     */
    private fun hasReadContent(w: SlotContent.Widget): Boolean =
        w.ids.isEmpty() || w.ids.any { !CapabilityCatalog.isWrite(it) }
}
