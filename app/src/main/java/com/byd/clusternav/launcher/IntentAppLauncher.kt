package com.byd.clusternav.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect

/**
 * Mở app THẬT vào ô bằng API launcher chuẩn (KHÔNG cần shell/dadb) — chạy được trên emulator LẪN xe.
 *
 * [ActivityOptions.setLaunchBounds] xin cửa sổ đúng khung ô; nếu thiết bị đã bật **freeform** → app hiện dạng
 * **cửa sổ trong ô**, nếu chưa → app mở **toàn màn** (vẫn CHẠY thật). Thêm windowing-mode freeform qua reflection
 * (best-effort; API ẩn, bọc try nên không crash nếu bị chặn). Trên xe, freeform được seed (cần tắt-mở máy 1 lần);
 * đường dadb-shell chính xác hơn nằm ở [ShellAppLauncher] để dùng khi cần đặt lại khung sau khi app đã mở.
 */
class IntentAppLauncher(private val activity: Activity) : AppLauncher {

    override fun isFreeformAvailable(): Boolean = true

    override fun openInSlot(pkg: String, slot: SlotRect): Boolean {
        val intent = activity.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: return false
        val opts = ActivityOptions.makeBasic()
            .setLaunchBounds(Rect(slot.left, slot.top, slot.right, slot.bottom))
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(opts, 5) // WINDOWING_MODE_FREEFORM
        }
        return runCatching { activity.startActivity(intent, opts.toBundle()); true }.getOrDefault(false)
    }

    override fun closeSlot(pkg: String) { /* API path không đóng tường minh; xe dùng ShellAppLauncher.fullscreenCmd */ }

    /** Không có shell → không resize được cửa sổ đang chạy; best-effort mở lại theo bound mới. */
    override fun moveToSlot(pkg: String, slot: SlotRect): Boolean = openInSlot(pkg, slot)
}
