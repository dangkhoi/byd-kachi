package com.byd.clusternav.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ═══ F1 — MỘT danh sách lối tắt, HAI bề mặt vẽ (khối thanh nút + widget `w_apps`) ════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R1.1 *"một danh sách hiện ở hai nơi"*. Nguồn sự thật là
 * [HomeUiState.shortcuts]; `KachiHomeActivity.render` đẩy BẢN SAO HIỂN THỊ vào đây ([publish]) mỗi khi nó đổi, và hai
 * bề mặt ([ShortcutIconsView]) nghe để vẽ lại TẠI CHỖ. Cờ RAM này chỉ để VẼ (CLAUDE.md §5) — cú chạm không đọc nó để quyết
 * gì: nó mang chính [AppShortcut] vừa chạm về [Host] của màn, và màn quyết bằng state + phép đo (`ShortcutPlan.decide`).
 *
 * ## Vì sao một trung gian thay vì truyền qua `WidgetData`
 * Widget `w_apps` là ô TỰ LO NỘI DUNG (`WorkspaceRenderPlanner.selfDriven`) — nhịp trạng thái xe 1 Hz không được dựng lại
 * nó (R1.3), nên đổi danh sách phải tới được view đang sống mà không qua đường dựng ô. `WorkspaceView.kt` (500/500 dòng)
 * cũng không còn chỗ cho một trường mới của `WidgetData`.
 *
 * ## Một màn, một chủ nhận chạm
 * [ĐO] H2 2026-09-14 có lúc BỐN `KachiHomeActivity` cùng sống ⇒ chạm phải về đúng màn chứa view. Chủ nhận ([Host]) đăng
 * ký THEO Activity ([bind]); view tra chủ bằng chính `context` của nó ([hostOf]). Bảng giữ YẾU cả hai đầu (khoá là
 * Activity, giá trị là `WeakReference`) — giá trị mạnh sẽ giữ ngược Activity qua chủ ⇒ không bao giờ được thu (rò màn).
 */
internal object ShortcutHub {

    /** Chủ nhận chạm của MỘT màn chính (`KachiHomeShortcuts`). */
    interface Host {
        /** Chạm một icon lối tắt (luồng chính). */
        fun onShortcut(sc: AppShortcut)

        /** Chạm ô "chưa có lối tắt" ⇒ mở chỗ chọn (Cài đặt › Thanh trạng thái & thanh nút). */
        fun onPickShortcuts()
    }

    @Volatile private var items: List<AppShortcut> = emptyList()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val hosts = WeakHashMap<Activity, WeakReference<Host>>()

    /** Danh sách đang hiển thị (bản sao của [HomeUiState.shortcuts]). */
    fun items(): List<AppShortcut> = items

    /** Bản mới từ `render` — giống hệt bản đang có ⇒ không làm gì (không dựng lại icon mỗi nhịp render). */
    fun publish(list: List<AppShortcut>) {
        if (list == items) return
        items = list
        listeners.forEach { it() }
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }

    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** Màn [activity] nhận chạm qua [host]. Gọi trên luồng chính. */
    fun bind(activity: Activity, host: Host) { synchronized(hosts) { hosts[activity] = WeakReference(host) } }

    /** Chạm [sc] trên một view thuộc [ctx] ⇒ chủ của màn đó. Màn đã chết / không có chủ ⇒ không làm gì. */
    fun tap(ctx: Context, sc: AppShortcut) { hostOf(ctx)?.onShortcut(sc) }

    /** Chạm ô rỗng ⇒ chỗ chọn lối tắt của màn chứa view. */
    fun pick(ctx: Context) { hostOf(ctx)?.onPickShortcuts() }

    private fun hostOf(ctx: Context): Host? {
        var c: Context? = ctx
        while (c != null) {
            if (c is Activity) return synchronized(hosts) { hosts[c]?.get() }
            c = (c as? ContextWrapper)?.baseContext
        }
        return null
    }
}
