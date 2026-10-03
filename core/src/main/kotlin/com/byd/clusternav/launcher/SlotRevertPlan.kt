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
 *  - (c) hai nút cạnh ⇄ — *chạy nền* / *tắt* — đi qua đúng luật (b) sau khi làm xong việc của chúng.
 *
 * ## Một nguồn sự thật cho "LƯU vs đang HIỆN"
 * Đầu vào là CHÍNH hai lớp của [HomeUiState]: `workspace` (LƯU, ghi bền) và `effectiveWorkspace` (đang HIỆN = LƯU +
 * [SlotOverlay]). Bảng không đọc gì khác; đầu ra ([Next]) chỉ đổi lớp TẠM ([overlayAfter]) ⇒ không đường nào ở đây ghi
 * hồ sơ (owner 01/10: đặt lúc chạy là tạm). Khởi động lại / đổi hồ sơ / đổi bố cục ⇒ lớp tạm mất ⇒ ô về đúng hồ sơ.
 *
 * ## Vì sao *chạy nền* chỉ có khi ô có app LƯU KHÁC
 * Đường "ra sau màn nhà" công khai DUY NHẤT ([com.byd.clusternav.launcher.behind.BehindHomeSequence.evict], qua
 * `BehindHomeRunner.evict`) đòi một app B đứng ĐỈNH màn ảo của ô TRÊN app A cần đẩy — [ĐO nguồn] A10 r47
 * `TaskRecord.java:728-749` (`wasFront`): A ở đỉnh màn ảo thì stack đích bị kéo lên TRƯỚC màn nhà (che nhà, đã đo
 * `behind-home-emulator-2026-10-01` bước 6). App LƯU khác của ô chính là B đó: ô đổi tại chỗ về B (`VdAppHost.swapApp`,
 * K8 nếu B đang sau màn nhà, không thì đường ô golden) rồi `evict(vd, A, B)` — ô của chính nó là chỗ dàn dựng. Ô LƯU là
 * chính A / widget / trống ⇒ không có B ⇒ đường công khai hôm nay KHÔNG làm được ⇒ nút không tồn tại ([SlotHeadActions]).
 * `startBehind` cũng không thay được: nó dừng ở `ALREADY_RUNNING` khi X đã có task (A đang ở ô = đã có task).
 */
object SlotRevertPlan {

    /** Điều vừa xảy ra với ô. */
    enum class Event {
        /** App của ô không còn task trên màn ảo của ô (nhịp `SlotLiveProbe`) — (a). */
        APP_DIED,

        /** Người dùng bấm *tắt* trên ô app và stack của app trên màn ảo ô đã gỡ xong — (c). */
        APP_CLOSED,

        /** Người dùng bấm *chạy nền* trên ô app — (c). */
        APP_BACKGROUND,

        /** Người dùng bấm *tắt* trên ô widget (Kachi hoặc bên thứ ba) — (c). */
        WIDGET_CLOSED,
    }

    /** Ô đi tiếp thế nào. */
    sealed interface Next {
        /** Không áp được (ô đã đổi nội dung / sai loại) ⇒ không đổi gì. */
        object Keep : Next { override fun toString() = "Keep" }

        /**
         * Bỏ mục tạm ⇒ ô hiện nội dung LƯU. [swapInPlace] = giữ màn ảo, đổi app tại chỗ về app LƯU rồi đẩy app đang hiện
         * ra sau màn nhà (đường đặt tạm sẵn có, R0.1) — CHỈ cho [Event.APP_BACKGROUND]. `false` = dựng lại ô (app đang hiện
         * đã đóng: không còn gì để giữ trên màn ảo).
         */
        data class ShowSaved(val swapInPlace: Boolean) : Next

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
                saved is SlotContent.App && saved.pkg == app.pkg ->
                    if (event == Event.APP_BACKGROUND) Next.Keep else Next.Clear
                saved is SlotContent.App -> Next.ShowSaved(swapInPlace = event == Event.APP_BACKGROUND)
                event == Event.APP_BACKGROUND -> Next.Keep
                else -> Next.ShowSaved(swapInPlace = false)   // LƯU là widget / trống ⇒ widget về · trống = trong suốt
            }
        }
    }

    /** *Chạy nền* làm được ở ô này không — CÙNG bảng [next] (một nguồn, nút và thi hành không lệch nhau). */
    fun backgroundable(saved: SlotContent, shown: SlotContent): Boolean =
        next(saved, shown, Event.APP_BACKGROUND) == Next.ShowSaved(swapInPlace = true)

    /** Lớp tạm sau [next] ở ô [slot] — chỉ đổi lớp TẠM, không bao giờ lớp LƯU. */
    fun overlayAfter(overlay: SlotOverlay, slot: Int, next: Next): SlotOverlay = when (next) {
        Next.Keep -> overlay
        is Next.ShowSaved -> overlay.drop(slot)
        Next.Clear -> overlay.clear(slot)
    }
}
