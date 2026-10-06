package com.byd.clusternav.launcher

/**
 * ═══ 2.93 `APPWIDGET-SIZE-API31` — cỡ khai cho nhà cung cấp widget app khác, hai đường API (thuần, `:core`) ══════════════════
 *
 * Độ mới công nghệ (senior review 2.92 FLAG): `AppWidgetHostView.updateAppWidgetSize(Bundle, int×4)` `@Deprecated` từ API 31;
 * bản thay `updateAppWidgetSize(Bundle, List<SizeF>)` chỉ có từ API 31. [ĐO nguồn AOSP android-12.1.0_r27
 * `AppWidgetHostView.java`, tải trong phiên 2.93] — L344-348 bản 5 tham số `@Deprecated` gọi bản ẩn L407: trừ lề mặc định
 * dạng `(int)((trái + phải) / density)` (L416-422) rồi ghi MIN/MAX + `OPTION_APPWIDGET_SIZES` = danh sách RỖNG (L437-442);
 * L365-401 bản `List<SizeF>`: trừ lề mặc định dạng SỐ THỰC `(trái + phải) / density` (L371-372, L381-382), ghi MIN/MAX = `(int)`
 * của cỡ đã trừ (L395-398) + `OPTION_APPWIDGET_SIZES` = đúng danh sách cỡ (L399), `newOptions.deepCopy()` (L394). [ĐO stub SDK
 * 37 `android-stubs-src.jar`] cả hai còn công khai, bản 5 tham số mang `@Deprecated`.
 *
 * Luật: nhà cung cấp phải nhận ĐÚNG cùng số MIN/MAX nguyên ở hai đường (= `(int)(nội dung / density)`, vùng vẽ thật sau lề
 * đều 8 dp của Kachi — 2.92 OQ3). Đường cũ khai số nguyên `(int)(nội dung/d) + (int)(lề/d)`; đường mới khai số thực
 * `(int)(nội dung/d) + lề/d + EPS` — nội dung LÀM TRÒN XUỐNG TRƯỚC như đường cũ, lề số thực đúng phép trừ của framework,
 * `EPS` giữ phép trừ `float` + `(int)` không hụt 1 dp (sai số ≤ 3 ulp ≈ 7e-4 ở ≤ 4 000 dp < EPS < 1).
 * Soát senior 2.93 Pass 1 [P3]: bản Pass 0 khai `nội dung/d + lề/d + EPS` — ở mật độ lẻ (vd 232 dpi: 87 px / 1,45 =
 * 59,999996 trong `float`) EPS đẩy qua số nguyên kế ⇒ đường mới báo HƠN đường cũ 1 dp ([ĐO] 2 427/5 590 863 ca, dpi
 * 100–720); làm tròn trước ⇒ 0 ca (bài `AppWidgetSizeTest` quét mọi dpi nguyên). Đường mới còn đưa `OPTION_APPWIDGET_SIZES`
 * thật (dp nguyên của vùng vẽ — không bao giờ khai rộng hơn chỗ có thật) ⇒ nhà cung cấp dùng bố cục theo cỡ (RemoteViews
 * nhiều cỡ, A12+) chọn đúng bản; đường cũ đưa danh sách rỗng.
 */
object AppWidgetSize {

    /** Đệm số thực của đường API 31+ (KDoc lớp). */
    const val EPS = 1e-3f

    /** Đường API < 31: dp nguyên khai qua bản 5 tham số. */
    fun legacyDp(contentPx: Int, defaultPadPx: Int, density: Float): Int =
        (contentPx.coerceAtLeast(0) / density).toInt() + (defaultPadPx.coerceAtLeast(0) / density).toInt()

    /** Đường API ≥ 31: dp thực khai qua bản `List<SizeF>` — nội dung làm tròn xuống TRƯỚC (KDoc lớp), lề số thực + [EPS]. */
    fun modernDp(contentPx: Int, defaultPadPx: Int, density: Float): Float =
        (contentPx.coerceAtLeast(0) / density).toInt() + defaultPadPx.coerceAtLeast(0) / density + EPS

    /** Số MIN/MAX nguyên nhà cung cấp NHẬN theo đường cũ (phép trừ của framework L416-422) — cho bài kiểm. */
    fun legacyReceived(declaredDp: Int, defaultPadPx: Int, density: Float): Int =
        declaredDp - (defaultPadPx.coerceAtLeast(0) / density).toInt()

    /** Số MIN/MAX nguyên nhà cung cấp NHẬN theo đường mới (L371-372 · L381 · L395 — số học `float` như framework). */
    fun modernReceived(declaredDp: Float, defaultPadPx: Int, density: Float): Int {
        val pad: Float = defaultPadPx.coerceAtLeast(0) / density
        return maxOf(0f, declaredDp - pad).toInt()
    }
}
