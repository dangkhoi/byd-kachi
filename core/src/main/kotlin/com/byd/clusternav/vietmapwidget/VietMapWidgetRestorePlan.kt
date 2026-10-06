package com.byd.clusternav.vietmapwidget

/**
 * ═══ 2.90 · R10 — widget VietMap đã lưu: gắn, chờ, hay bỏ để bind lại (thuần) ═══════════════════════════════════════════════
 *
 * Gốc badge giới hạn tốc độ không hiện sáng 06/10 (spec `kachi-290-cluster-rect-fix.html` §4.5): [ĐO log xe] cả ba id widget
 * `getAppWidgetInfo(38|39|40) returned null … keeping saved ID` ở mọi lần khởi động tiến trình, và 0 dòng `ClusterSpeedBadge show`
 * suốt 47 phút dẫn đường. Bản cũ GIỮ id chết mãi mãi, còn `autoBindMissing` bỏ qua ô đã có id ⇒ không bao giờ có RemoteViews ⇒ không
 * bao giờ có giới hạn tốc độ — tự khoá (CLAUDE.md §3 rule đi kèm).
 *
 * [ĐO nguồn A10 r47 `AppWidgetServiceImpl.java`] `getAppWidgetInfo` (`:1410-1417`) trả `null` khi widget không còn
 * (`lookupWidgetLocked` = null) HOẶC provider null/zombie; `getInstalledProvidersForProfile` (`:1725`) loại provider zombie. ⇒ provider
 * CÓ trong danh sách cài (không zombie) mà id trả `null` ⇒ widget đã bị xoá (vd gỡ hẳn gói: `:452-462` → `:3445` → `:3430-3442` →
 * `deleteProviderLocked :2312-2317` → `deleteWidgetsLocked :2293-2310`). Provider VẮNG ⇒ có thể chỉ tạm (đang cài lại, zombie lúc
 * khởi động) ⇒ giữ id như cũ.
 */
object VietMapWidgetRestorePlan {

    enum class Action {
        /** Widget sống, đúng provider ⇒ dựng host view. */
        ATTACH,
        /** Chưa biết (provider vắng/zombie) ⇒ giữ id, thử lại ở lượt start/đổi gói sau (hành vi cũ). */
        KEEP_WAIT,
        /** Widget đã mất mà provider còn ⇒ xoá id + prefs để `autoBindMissing` bind lại. */
        DROP_REBIND,
        /** Id trỏ provider khác ⇒ xoá (hành vi cũ). */
        DROP_MISMATCH,
        /** Widget còn mà provider đã gỡ ⇒ xoá (hành vi cũ). */
        DROP_UNINSTALLED,
    }

    /**
     * @param infoProvider provider của `getAppWidgetInfo(id)` (chuỗi component), `null` = hàm trả `null`.
     * @param slotProvider provider mà ô này cần.
     * @param providerInstalled provider của ô có trong `getInstalledProviders` (không zombie).
     */
    fun decide(infoProvider: String?, slotProvider: String, providerInstalled: Boolean): Action = when {
        infoProvider == null -> if (providerInstalled) Action.DROP_REBIND else Action.KEEP_WAIT
        infoProvider != slotProvider -> Action.DROP_MISMATCH
        !providerInstalled -> Action.DROP_UNINSTALLED
        else -> Action.ATTACH
    }
}
