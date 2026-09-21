package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.byd.clusternav.Prefs

/**
 * ═══ UX-OVERHAUL WP1 · R1.3 — GLASS HAI CHẾ ĐỘ (giả mặc định / thật tuỳ chọn) ══════════════════════════════════
 *
 * Thẩm mỹ "glass/liquid" (owner: giao diện "nhà quê" → iOS 27) có **hai** đường, chọn bằng công tắc
 * `ui_glass_real` (Cài đặt › Hiển thị, mặc định TẮT — [Prefs.glassReal]):
 *
 *  • **GIẢ (mặc định)** — [KachiTheme.surface]: chuyển sắc DỌC + fill trong mờ (trên ảnh nền), **0 mép, 0 viền, 0
 *    blur runtime**. (Bản WP1 đầu có thêm mép kính 1px; owner 2026-09-20 gọi nó là *"gạch trên đầu mỗi khung"* nên
 *    đã gỡ — xem KDoc `surface()`.) Đây là con đường cho MỌI máy, và là con đường DUY NHẤT trên xe: [ĐO] DiLink là
 *    API 29, và cả dự án cố ý 0 blur vì GPU đầu máy (TRINKET) yếu.
 *  • **THẬT (tuỳ chọn, API ≥ 31)** — tệp này: `RenderEffect.createBlurEffect` làm **mờ NỀN** phía sau các thẻ trong
 *    mờ ⇒ kính thật. API < 31 ⇒ [applyBackdrop] không làm gì (lùi về giả). Owner: *"lên xe đo CPU/fps/lag cả 2 rồi
 *    quyết"* — nên nó là một công tắc, không phải mặc định.
 *
 * ## ⚠ Đây là tệp DUY NHẤT trong `launcher/` được phép `RenderEffect`
 * `SurfaceMaterialContractTest.0 blur 0 shadow 0 elevation` cấm `RenderEffect` trong tầng vẽ launcher vì nó bắt GPU
 * vẽ thêm một lượt off-screen mỗi khung. Đó là ràng buộc cho glass GIẢ (mặc định). Glass THẬT là ngoại lệ **có công
 * tắc + có API-gate + mặc định TẮT + chờ đo trên xe**, nên tệp này được khai vào danh sách miễn trừ của bài canh
 * ấy — cùng lối miễn trừ `speedbadge/` (bề mặt đã có lý do riêng).
 */
object KachiGlassMode {

    /** Bán kính làm mờ nền mặc định (dp) — đủ "kính" mà không nuốt hẳn hình nền. */
    const val DEFAULT_RADIUS_DP = 22f

    /** Glass THẬT đang bật? = công tắc `ui_glass_real` BẬT **và** máy API ≥ 31 (dưới đó không có RenderEffect). */
    fun enabled(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { Prefs.glassReal(ctx) }.getOrDefault(false)

    /**
     * Làm mờ toàn bộ nội dung của [view] (dùng cho lớp NỀN / hình nền) khi glass thật bật; ngược lại gỡ hiệu ứng.
     *
     * An toàn mọi API: dưới 31 thoát ngay (không chạm `setRenderEffect`, vốn là API 31). Idempotent — gọi lại với
     * cùng trạng thái không sinh thêm gì; gọi khi TẮT sẽ [View.setRenderEffect] `null` (trả nền về nét = lùi về giả).
     */
    fun applyBackdrop(view: View, radiusDp: Float = DEFAULT_RADIUS_DP) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val effect = if (enabled(view.context)) {
            val px = radiusDp * view.resources.displayMetrics.density
            RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP)
        } else {
            null
        }
        view.setRenderEffect(effect)
    }
}
