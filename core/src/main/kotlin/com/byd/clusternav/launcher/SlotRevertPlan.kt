package com.byd.clusternav.launcher

/**
 * ═══ L6 · LUẬT HOÀN Ô — một lượt đặt tạm / một app / một widget của ô KẾT THÚC thì ô về đâu (thuần, `:core`) ═══════════
 *
 * Ba yêu cầu owner 03/10 (xe thật, 2.86):
 *  - (a) *"Khi app bị tắt thì trả về transparent luôn, không cần giữ icon và yêu cầu mở app như này nhé"* — thẻ
 *    "App đã đóng — chạm để mở lại" bỏ; ô của app LƯU đã chết ⇒ trong suốt như khung trống.
 *  - (b) *"widget đang để lốp, xong shortcut mở 1 app vào, xong tắt app đi, thì nó nên về đâu? Hiện tại nó về đen thui 1
 *    mảng"* — luật (phiên điều phối chốt, generic): lượt đặt TẠM kết thúc ⇒ ô về nội dung LƯU của hồ sơ (widget lốp hiện
 *    lại; app LƯU khác ⇒ mở lại vào ô như lúc khởi động). Chỉ khi nội dung LƯU CHÍNH LÀ app đó, hoặc ô LƯU trống, ô mới
 *    trong suốt.
 *  - (c) hai nút cạnh ⇄ — *chạy nền* / *tắt* — đi qua đúng luật (b) sau khi làm xong việc của chúng (app đã rời màn ảo).
 *
 * ## Một nguồn sự thật cho "LƯU vs đang HIỆN"
 * Đầu vào là CHÍNH hai lớp của [HomeUiState]: `workspace` (LƯU, ghi bền) và `effectiveWorkspace` (đang HIỆN = LƯU +
 * [SlotOverlay]). Bảng không đọc gì khác; đầu ra ([Next]) chỉ đổi lớp TẠM ([overlayAfter]) ⇒ không đường nào ở đây ghi
 * hồ sơ (owner 01/10: đặt lúc chạy là tạm). Khởi động lại / đổi hồ sơ / đổi bố cục ⇒ lớp tạm mất ⇒ ô về đúng hồ sơ.
 *
 * ## *Chạy nền* = cùng luật với *tắt* (L8, mở khoá D-L6-1)
 * Sự kiện [Event.APP_BACKGROUND] chỉ được báo SAU KHI app đã RỜI màn ảo của ô (bản đọc cuối của
 * `BehindHomeSequence.evictCovered`: lớp che của Kachi đứng trước app trong màn ảo ô ⇒ move-task ra sau màn nhà, A10 r47
 * `TaskRecord.java:736-737` `wasFront`). Lúc đó ô không còn gì để giữ trên màn ảo ⇒ ô đi đúng đường của app vừa đóng: LƯU
 * là chính app ⇒ trong suốt (owner: *"để UI trong suốt thấy nền background"*) · LƯU là app khác / widget / trống ⇒ về nội
 * dung LƯU. Bản L6 (đổi TẠI CHỖ về app LƯU khác rồi `evict(vd, A, B)`, chỉ khi ô có app LƯU khác) đã thay bằng MỘT đường
 * chung cho mọi ô app — không còn mốc đổi-tại-chỗ nào sinh ra từ bảng này.
 */
object SlotRevertPlan {

    /** Điều vừa xảy ra với ô. */
    enum class Event {
        /** App của ô không còn task trên màn ảo của ô (nhịp `SlotLiveProbe`) — (a). */
        APP_DIED,

        /** Người dùng bấm *tắt* trên ô app và stack của app trên màn ảo ô đã gỡ xong — (c). */
        APP_CLOSED,

        /** Người dùng bấm *chạy nền* trên ô app VÀ bản đọc cuối thấy app đã rời màn ảo ô (ra sau màn nhà) — (c), L8. */
        APP_BACKGROUND,

        /** Người dùng bấm *tắt* trên ô widget (Kachi hoặc bên thứ ba) — (c). */
        WIDGET_CLOSED,
    }

    /** Ô đi tiếp thế nào. */
    sealed interface Next {
        /** Không áp được (ô đã đổi nội dung / sai loại) ⇒ không đổi gì. */
        object Keep : Next { override fun toString() = "Keep" }

        /**
         * Bỏ mục tạm ⇒ ô hiện nội dung LƯU (dựng lại ô: app đang hiện đã rời màn ảo — chết / bị tắt / đã ra sau màn nhà —
         * nên không còn gì để giữ trên đó; app LƯU mở lại như lúc khởi động, K8 nếu nó đang sau màn nhà).
         */
        object ShowSaved : Next { override fun toString() = "ShowSaved" }

        /** Ô trong suốt như khung trống ([SlotOverlay.clear]) — lớp LƯU giữ nguyên. */
        object Clear : Next { override fun toString() = "Clear" }
    }

    /**
     * Bảng (đủ ô ở `SlotRevertPlanTest`). [saved] = nội dung LƯU của ô · [shown] = nội dung đang HIỆN · [pkg] = gói mà sự
     * kiện nói tới (`null` cho widget) — khác gói đang hiện ⇒ sự kiện cũ của một app đã rời ô ⇒ [Next.Keep].
     */
    fun next(saved: SlotContent, shown: SlotContent, event: Event, pkg: String? = null): Next = when (event) {
        Event.WIDGET_CLOSED ->
            if (shown is SlotContent.Widget || shown is SlotContent.AppWidget) Next.Clear else Next.Keep
        Event.APP_DIED, Event.APP_CLOSED, Event.APP_BACKGROUND -> {
            val app = shown as? SlotContent.App
            when {
                app == null || (pkg != null && pkg != app.pkg) -> Next.Keep
                saved is SlotContent.App && saved.pkg == app.pkg -> Next.Clear
                else -> Next.ShowSaved   // app LƯU khác ⇒ mở lại · widget về · LƯU trống = trong suốt
            }
        }
    }

    /** Lớp tạm sau [next] ở ô [slot] — chỉ đổi lớp TẠM, không bao giờ lớp LƯU. */
    fun overlayAfter(overlay: SlotOverlay, slot: Int, next: Next): SlotOverlay = when (next) {
        Next.Keep -> overlay
        Next.ShowSaved -> overlay.drop(slot)
        Next.Clear -> overlay.clear(slot)
    }
}
