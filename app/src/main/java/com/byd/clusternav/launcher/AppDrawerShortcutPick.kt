package com.byd.clusternav.launcher

import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ F1 · U1 — ngăn kéo ở chế độ CHỌN APP CHO LỐI TẮT (`AppDrawer.Mode.PICK_SHORTCUTS`) ══════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R1.4. Tách khỏi `AppDrawer.kt` (trần 500 dòng) theo đúng
 * khuôn `AppDrawerTiles.kt`: hàm mở rộng `internal` của [AppDrawer], trạng thái chọn vẫn là của [AppDrawer]
 * (`selected` · `cap`), đường bật/tắt DUY NHẤT vẫn là [AppDrawer.toggleSelection] (trần 8 = `MAX` = [AppShortcutCodec.MAX]
 * ⇒ quá trần thì NÓI ra ở thanh đáy, không chặn im lặng — `PickerCapNoticeContractTest`).
 *
 * Mẫu là `DrawerController.openDockPicker` (một bộ chọn, bấm **Áp dụng (N)** ⇒ `onApply` nhận tập đã chốt). Khác: lưới
 * là lưới APP (nguồn = danh sách app có màn khởi chạy, CHÍNH [AppDrawerApps.load] mà ngăn kéo "Ứng dụng" dùng) và ô là
 * CHÍNH ô app của ngăn kéo ([AppDrawerApps.tile]) — chỉ thêm nền chọn/không chọn. Ô app nằm ở [AppDrawer.appPickTiles],
 * KHÔNG ở `widgetTiles`: đường tô của ô khả năng nhuộm icon một màu (`PickerBadge.retint`) — icon app phải giữ màu thật.
 */
internal fun AppDrawer.shortcutPickSection(body: LinearLayout, apps: AppDrawerApps, cols: Int) {
    // Chính Kachi không làm lối tắt được (nó LÀ màn nhà; *Chạy ngầm* còn bị bảng chạm từ chối `SELF`) ⇒ không bày.
    val installed = apps.load().filter { it.pkg != context.packageName }
        .map { AppDrawerApps.Item(it.pkg, it.label, it.icon) { toggleSelection(it.pkg) } }
    // Gói ĐANG chọn mà đã gỡ khỏi máy: vẫn bày một ô (nhãn = tên gói, không icon) để người dùng BỎ được nó — không bày thì
    // mục đó kẹt trong danh sách mà không có chỗ nào để gỡ (chính thứ "chặn im lặng" mà toggleSelection sinh ra để chống).
    val have = installed.mapTo(HashSet()) { it.pkg }
    val gone = selected.filter { it !in have }.map { p -> AppDrawerApps.Item(p, p, { null }) { toggleSelection(p) } }
    val items = gone + installed
    CapabilityTileGrid.rows(context, body, items.size, cols) { i ->
        val item = items[i]
        apps.tile(item).also { appPickTiles[item.pkg] = it; applyAppPickState(item.pkg) }
    }
}

/** Tô một ô app theo trạng thái chọn — cùng hai tông + cùng độ mờ "hết chỗ" của ô khả năng (`applyTileState`). */
internal fun AppDrawer.applyAppPickState(pkg: String) {
    val tile = appPickTiles[pkg] ?: return
    val on = pkg in selected
    tile.background = KachiTheme.surface(context, Sp.RADIUS_L, if (on) SurfaceTone.ACTIVE else SurfaceTone.NEUTRAL)
    tile.alpha = if (on || selected.size < cap) 1f else dimmedAlpha
}
