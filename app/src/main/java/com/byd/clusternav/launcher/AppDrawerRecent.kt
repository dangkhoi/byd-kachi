package com.byd.clusternav.launcher

import android.widget.LinearLayout
import com.byd.clusternav.R

/**
 * ═══ Hàng **"Gần đây"** của ngăn kéo — MỘT chỗ dựng cho hai chế độ (`Mode.OPEN_APP` · `Mode.ASSIGN_SLOT`) ═══════════
 *
 * Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` **R27** (owner 13/10: *"khi chọn widget cũng thêm section các
 * ứng dụng hay dùng như nhấn nút ứng dụng nhé, trên cùng"*). Trước R27 chỉ ngăn kéo nút *Ứng dụng* có hàng này; bộ
 * chọn của Ô bắt người dùng tìm app trong danh sách A→Z. Tách tệp vì trần 500 dòng của `AppDrawer.kt`.
 *
 * ## Không có đường chạm thứ hai
 * Ô "Gần đây" là ô của [AppDrawerApps.recent] — nó chép NGUYÊN `onTap` của ô cùng gói trong [all] (= [AppDrawerApps.load]
 * ⇒ `onPickApp(pkg)` của chính bảng). Ở chế độ gán ô, đó là `DrawerController.open` → `onPickApp(index, pkg)` →
 * `slots.assignApp` — đúng đường của lưới *Ứng dụng* bên dưới. Ở chế độ mở-thường là `onOpenApp` (mở toàn màn) như cũ.
 * Danh sách rỗng (chưa mở app nào / mọi app gần đây đã gỡ) ⇒ KHÔNG bày gì, kể cả tiêu đề.
 *
 * @return `true` nếu đã bày hàng — chỗ gọi dùng để đệm trên cho tiêu đề mục kế tiếp (một nhịp với mọi mục khác).
 */
internal fun AppDrawer.recentSection(body: LinearLayout, apps: AppDrawerApps, all: List<AppDrawerApps.Item>, recentApps: List<String>, cols: Int): Boolean {
    val recent = apps.recent(all, recentApps)
    if (recent.isEmpty()) return false
    body.addView(sectionLabel(context.getString(R.string.kachi_drawer_section_recent)))
    apps.grid(body, recent, cols = cols)   // chỗ gọi chuyền `COLS_APP` — cùng số cột với lưới app
    return true
}
