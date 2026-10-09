package com.byd.clusternav.launcher.escape

import kotlin.math.ceil

/**
 * ═══ 2.98 · R7 — HÌNH HỌC lớp che (thuần, `:core`) ═══════════════════════════════════════════════════════════════════
 *
 * Khi có stack freeform hiện trên display 0, AOSP ép hiện thanh trạng thái + thanh điều hướng và xoá cờ ẩn của MỌI cửa sổ
 * ([ĐO nguồn fw 2606 = AOSP Q `DisplayPolicy.java:2565, 3280-3287, 3376-3378`], evidence §4) ⇒ không cờ nào ẩn được, chỉ CHE.
 * Lớp che là `TYPE_ACCESSIBILITY_OVERLAY` (lớp 30 > thanh điều hướng 23 > thanh trạng thái 17 — [ĐO nguồn
 * `WindowManagerPolicy.getWindowLayerFromTypeLw`]) của dịch vụ trợ năng Kachi đã bật sẵn. Các vùng:
 *  - [Kind.CAPTION] / [Kind.GAP] — thanh tiêu đề freeform (□ ✕) ở đầu cửa sổ app: [ĐO máy ảo 09/10] 64 px @ 240 dpi (≈ 42,7 dp),
 *    không tắt được ([EscapeFit]). Owner 09/10: khung task được đặt cao hơn ô một thanh tiêu đề ([EscapeFit.taskRect]) ⇒ phần trên
 *    đỉnh ô là [Kind.CAPTION] (gương đầu màn nhà), phần hệ không cho lên (đỉnh ổn định) lọt vào ô là [Kind.GAP] (nền sau ô + ⇄).
 *  - [Kind.CORNER] — góc bo của khung ô vẽ lên 4 góc cửa sổ app (không nhận chạm).
 *  - [Kind.STATUS] / [Kind.NAV] — khung THẬT của cửa sổ `StatusBar` / `NavigationBar…` đọc từ `dumpsys window windows`
 *    ([ĐO xe 14/09] `[0,0][1920,84]` · `[0,990][1920,1080]`; máy ảo chỉ có thanh trạng thái `[0,0][1920,36]`) — không hằng số.
 */
object EscapeCoverPlan {

    /** Đọc cửa sổ — chỉ đọc (`ShellLogGate`: `dumpsys window windows` thuộc danh sách chỉ-đọc). */
    const val WINDOWS_CMD = "dumpsys window windows"

    /** Cao thanh tiêu đề freeform, phần trăm dp ([ĐO máy ảo] 64 px @ 240 dpi = 42,67 dp) — làm tròn LÊN 43 dp. */
    const val CAPTION_DP = 43

    enum class Kind { CAPTION, GAP, CORNER, STATUS, NAV, EDGE }

    /**
     * Viền "tay nắm đổi cỡ" quanh cửa sổ freeform, dp. [ĐO máy ảo 09/10 `dumpsys input`] Waze freeform khung `[19,89][1901,985]`
     * có `touchableRegion=[0,44][1920,1030]` — rộng hơn khung 45 px (= 30 dp @ 240 dpi) mỗi phía (AOSP Q `WindowState`
     * `RESIZE_HANDLE_WIDTH_IN_DP`): chạm vào thanh trên / thanh nút Kachi trong dải đó bị cửa sổ app nuốt (kéo đổi cỡ). Lớp che
     * [Kind.EDGE] phủ đúng dải này và giao chạm lại cho Kachi.
     */
    const val RESIZE_HANDLE_DP = 30

    /**
     * Một vùng che. [slot] = chỉ số ô ([Kind.GAP] / [Kind.CORNER] — bên vẽ tìm nút ⇄ của ô); [frame] + [radiusPx] = khung bo mà
     * [Kind.CORNER] chừa trong suốt bên trong cung.
     */
    data class Cover(val kind: Kind, val rect: PxRect, val slot: Int = -1, val frame: PxRect? = null, val radiusPx: Int = 0)

    /** Khung hai thanh hệ thống trên display 0; `null` = không có / không đọc được. */
    data class Bars(val status: PxRect?, val nav: PxRect?) {
        companion object { val NONE = Bars(null, null) }
    }

    fun captionPx(densityDpi: Int): Int = ceil(CAPTION_DP * densityDpi / 160.0).toInt()

    private val WINDOW = Regex("^\\s*Window #\\d+ Window\\{\\S+ u\\d+ ([^}]+)\\}:")
    private val DISPLAY = Regex("^\\s*mDisplayId=(\\d+)")
    private val FRAME = Regex("^\\s*mFrame=(\\[-?\\d+,-?\\d+\\]\\[-?\\d+,-?\\d+\\])")
    private val NAV_NAME = Regex("NavigationBar\\d*")

    /**
     * Khung `StatusBar` và `NavigationBar…` trên [display] từ nguyên văn `dumpsys window windows` (A10: khối `Window #N Window{…
     * u0 <tên>}:` → `mDisplayId=` → `mFrame=[…][…]`). Khung rỗng bỏ qua. Định dạng lạ ⇒ [Bars.NONE] (không che thanh — chỉ còn
     * che thanh tiêu đề).
     */
    fun parseBars(out: String, display: Int = 0): Bars {
        var name: String? = null
        var disp = -1
        var status: PxRect? = null
        var nav: PxRect? = null
        for (line in out.lineSequence()) {
            val w = WINDOW.find(line)
            if (w != null) { name = w.groupValues[1].trim(); disp = -1; continue }
            val d = DISPLAY.find(line)
            if (d != null) { disp = d.groupValues[1].toInt(); continue }
            val f = FRAME.find(line) ?: continue
            val n = name ?: continue
            name = null   // một khung mỗi cửa sổ — dòng `mFrame` sau không thuộc cửa sổ này
            val r = PxRect.parse(f.groupValues[1])?.takeUnless { it.isEmpty } ?: continue
            if (disp != display) continue
            if (n == "StatusBar" && status == null) status = r
            if (NAV_NAME.matches(n) && nav == null) nav = r
        }
        return Bars(status, nav)
    }

    /**
     * Vùng che cho task ở khung [task] (khung đã đặt — [EscapeFit.taskRect]) của ô [slot] (khung ô; `slot` = chỉ số ô, chỉ để bên
     * vẽ tìm nút ⇄ của ô) với bán kính góc khung ô [radiusPx]:
     *  - [Kind.CAPTION] — phần thanh tiêu đề nằm TRÊN đỉnh ô (vùng đầu màn nhà): gương Kachi, chạm giao lại.
     *  - [Kind.GAP] — phần thanh tiêu đề còn lọt XUỐNG trong ô (hệ không cho task cao hơn đỉnh ổn định — [EscapeFit]): vẽ NỀN SAU
     *    ô (hình nền) + nút ⇄, KHÔNG vẽ khay ô ⇒ ô trông như bắt đầu đúng ở đỉnh nội dung app, không còn "thanh xám" (owner 09/10).
     *  - [Kind.CORNER] — hai dải góc (đỉnh nội dung app · đáy ô), cao = bán kính: nền sau ô NGOÀI cung bo, trong suốt bên trong,
     *    KHÔNG nhận chạm ⇒ cửa sổ app trông bo 4 góc như ô thường (owner: "bo 2 góc dưới hỏng"). [frame] = khung bo (đỉnh nội dung → đáy ô).
     *  - [Kind.STATUS] / [Kind.NAV] — hai thanh hệ thống (nếu có) · [Kind.EDGE] — 4 dải tay nắm đổi cỡ quanh khung TASK.
     * Vùng thanh chồng lên khung task (ROM lạ) ⇒ vẫn che — thanh hệ thống vốn đã đè lên chỗ đó.
     */
    fun covers(
        task: PxRect,
        bars: Bars,
        densityDpi: Int,
        screen: PxRect,
        slot: PxRect = task,
        slotIndex: Int = -1,
        radiusPx: Int = 0,
    ): List<Cover> {
        val capBottom = minOf(task.bottom, task.top + captionPx(densityDpi))
        val above = PxRect(task.left, task.top, task.right, minOf(capBottom, slot.top))
        val gap = PxRect(slot.left, maxOf(task.top, slot.top), slot.right, capBottom)
        val frame = PxRect(slot.left, maxOf(capBottom, slot.top), slot.right, minOf(slot.bottom, task.bottom))
        val r = frameRadius(frame, radiusPx)
        val corners = if (r <= 0) emptyList() else listOf(
            PxRect(frame.left, frame.top, frame.right, frame.top + r),
            PxRect(frame.left, frame.bottom - r, frame.right, frame.bottom),
        ).mapNotNull { clip(it, screen) }.map { Cover(Kind.CORNER, it, slotIndex, frame, r) }
        val d = ceil(RESIZE_HANDLE_DP * densityDpi / 160.0).toInt()
        val edges = listOf(
            PxRect(task.left - d, task.top - d, task.right + d, task.top),        // trên
            PxRect(task.left - d, task.bottom, task.right + d, task.bottom + d),  // dưới
            PxRect(task.left - d, task.top, task.left, task.bottom),              // trái
            PxRect(task.right, task.top, task.right + d, task.bottom),            // phải
        ).mapNotNull { clip(it, screen) }
        return listOfNotNull(
            Cover(Kind.CAPTION, above).takeUnless { above.isEmpty },
            Cover(Kind.GAP, gap, slotIndex).takeUnless { gap.isEmpty },
            bars.status?.let { Cover(Kind.STATUS, it) },
            bars.nav?.let { Cover(Kind.NAV, it) },
        ) + corners + edges.map { Cover(Kind.EDGE, it) }
    }

    /** Bán kính góc thật cho khung bo [frame] — cùng luật `SlotFrameShape.radius` (kẹp `min(w, h) / 2`), px nguyên. */
    fun frameRadius(frame: PxRect, radiusPx: Int): Int =
        if (frame.isEmpty || radiusPx <= 0) 0 else minOf(radiusPx, minOf(frame.width, frame.height) / 2)

    /** Phần của [r] nằm trong [screen]; rỗng ⇒ `null`. */
    fun clip(r: PxRect, screen: PxRect): PxRect? =
        PxRect(maxOf(r.left, screen.left), maxOf(r.top, screen.top), minOf(r.right, screen.right), minOf(r.bottom, screen.bottom))
            .takeUnless { it.isEmpty }
}
