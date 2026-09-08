package com.byd.clusternav.navigation.screencapture

/**
 * Dựng [AppLocation] (nơi + trạng thái app dẫn) từ output `am stack list` — THUẦN, không Android, để
 * `ScreenCaptureNavSource` (:app) chỉ lo gọi shell rồi giao chuỗi cho đây. Là phần khoá-được-off-car của việc
 * "detect app đang ở đâu" (§4.3 selectCase input).
 *
 * Đọc BOUNDS của stack chứa task app dẫn (mẫu như `AppMover.isWindowedOnMain`):
 * ```
 * Stack id=10 bounds=[0,0][1920,720] displayId=0 userId=0
 *   taskId=33: vn.vietmap.live/…MainActivity bounds=[0,0][960,720] visible=true …
 * ```
 * Suy: displayId của stack; fullscreen nếu stack phủ gần trọn bề rộng display; nếu không → split trái/phải +
 * leftPercent (khớp nghĩa `AppMover.fitToCluster`: vách = W·lp/100). Bề rộng display lấy PROXY = right lớn nhất
 * trong các stack cùng display (stack home/placeholder thường full-width).
 *
 * Q3: navigation KHÔNG import cast → dùng [CaptureSlotSide] (nav-local), không `ClusterSlotSide`. Lớp :app map
 * ở biên nếu cần.
 *
 * ⚠ VERIFY-ON-CAR: giá trị thật (bounds, có visible=true đúng lúc, proxy bề rộng) tuỳ firmware/trim — off-car
 * chỉ khoá logic suy-luận bằng chuỗi tổng hợp. Degrade-safe: không tìm thấy task hợp lệ → dùng [foregroundHint].
 */
object CaptureLocationResolver {

    /**
     * Đầu khối stack. Chấp nhận CẢ HAI cách viết:
     *   • `Stack id=10 bounds=…`     — Android 10 (xe: DiLink 2/3/4)
     *   • `RootTask id=266 bounds=…` — Android 12+ (DL5) và emulator API 34
     * Đo 2026-08-22: emulator API 34 in `RootTask id=`, nên regex chỉ-`Stack` KHÔNG khớp dòng nào ⇒ resolver
     * rơi hết về nhánh "không thấy task" ⇒ [AppLocation.windowRect] luôn null và dpi luôn 0. Lỗi này im lặng
     * (pipeline vẫn chạy bằng nhánh dự phòng) nên rất dễ lọt.
     */
    private val STACK = Regex("""(?:Root)?(?:Task|Stack) id=\d+ bounds=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)] displayId=(\d+)""")
    private val TASK = Regex("""taskId=(\d+):\s*(\S+)""")

    /** bounds RIÊNG của dòng task (chính xác hơn bounds của stack khi task nhỏ hơn stack). */
    private val TASK_BOUNDS = Regex("""bounds=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

    /**
     * dpi trong dòng `configuration={...  240dpi ...}` của `am stack list`. Người dùng chỉnh dpi cụm khi cast
     * (`wm density`), nên đây là NGUỒN SỰ THẬT rẻ nhất — không tốn thêm một round-trip shell nào vì output này
     * ta vốn đã lấy mỗi nhịp.
     */
    private val DPI = Regex("""(\d{2,4})dpi""")
    private const val SLOP = 4
    private const val DEFAULT_DISPLAY_W = 1920

    private data class StackBox(
        val left: Int, val top: Int, val right: Int, val bottom: Int, val displayId: Int,
    ) {
        val rect: CropRect get() = CropRect(left, top, right, bottom)
    }

    /**
     * @param amOut          output `am stack list`.
     * @param pkg            package app dẫn (thường = `SourceArbiter.activeSource` hoặc [CaptureForegroundSource.pkg]).
     * @param navFresh       gate: nguồn dẫn còn tươi (nếu false, router trả null → không capture).
     * @param foregroundHint a11y báo pkg foreground khi am-stack KHÔNG có task visible (đường ảnh-thuần).
     */
    fun resolve(
        amOut: String,
        pkg: String,
        navFresh: Boolean,
        foregroundHint: Boolean,
        mainDisplayId: Int = 0,
        clusterDisplayId: Int = 1,
    ): AppLocation {
        // Quét: với mỗi stack (bounds+display) theo sau bởi các task line; nếu task khớp pkg + visible → chốt.
        var current: StackBox? = null
        val widthByDisplay = HashMap<Int, Int>()
        var hit: StackBox? = null
        var hitVisible = false
        var hitDpi = 0
        var currentDpi = 0
        for (line in amOut.lines()) {
            DPI.find(line)?.let { currentDpi = it.groupValues[1].toIntOrNull() ?: currentDpi }
            val sm = STACK.find(line)
            if (sm != null) {
                val l = sm.groupValues[1].toIntOrNull() ?: 0
                val t = sm.groupValues[2].toIntOrNull() ?: 0
                val r = sm.groupValues[3].toIntOrNull() ?: 0
                val b = sm.groupValues[4].toIntOrNull() ?: 0
                val d = sm.groupValues[5].toIntOrNull() ?: -1
                current = StackBox(l, t, r, b, d)
                // Proxy bề rộng display = right lớn nhất thấy trên display đó.
                val prev = widthByDisplay[d] ?: 0
                if (r > prev) widthByDisplay[d] = r
                continue
            }
            val tm = TASK.find(line) ?: continue
            val comp = tm.groupValues[2]
            val taskPkg = comp.substringBefore("/")
            if (taskPkg == pkg) {
                val visible = line.contains("visible=true")
                // bounds của CHÍNH dòng task nếu có (task có thể nhỏ hơn stack); nếu không thì lấy của stack.
                val tb = TASK_BOUNDS.find(line)
                val box = if (tb != null && current != null) {
                    StackBox(
                        left = tb.groupValues[1].toIntOrNull() ?: current.left,
                        top = tb.groupValues[2].toIntOrNull() ?: current.top,
                        right = tb.groupValues[3].toIntOrNull() ?: current.right,
                        bottom = tb.groupValues[4].toIntOrNull() ?: current.bottom,
                        displayId = current.displayId,
                    )
                } else current
                // Ưu tiên task VISIBLE; nếu chưa có hit, giữ hit đầu tiên làm dự phòng.
                if (box != null && (visible || hit == null)) {
                    hit = box
                    hitVisible = visible || hitVisible
                    hitDpi = currentDpi
                }
            }
        }

        val box = hit
        if (box == null) {
            // Không thấy task → dựa vào a11y foreground. foreground=false ⇒ selectCase → NOT_ACTIVE (case 4).
            return AppLocation(
                pkg = pkg,
                displayId = mainDisplayId,
                isFullscreen = true,
                slotSide = null,
                leftPercent = 50,
                windowRect = null,
                foreground = foregroundHint,
                navFresh = navFresh,
            )
        }

        val displayW = (widthByDisplay[box.displayId] ?: 0).takeIf { it > 0 } ?: DEFAULT_DISPLAY_W
        val stackW = box.right - box.left
        val fullscreen = box.left <= SLOP && stackW >= displayW - SLOP
        val slotSide: CaptureSlotSide?
        val leftPercent: Int
        if (fullscreen) {
            slotSide = null
            leftPercent = 50
        } else if (box.left <= SLOP) {
            // App ở NỬA TRÁI: chiếm [0, right) → vách tại right.
            slotSide = CaptureSlotSide.LEFT
            leftPercent = pct(box.right, displayW)
        } else {
            // App ở NỬA PHẢI: chiếm [left, W) → vách tại left.
            slotSide = CaptureSlotSide.RIGHT
            leftPercent = pct(box.left, displayW)
        }

        return AppLocation(
            pkg = pkg,
            displayId = box.displayId,
            isFullscreen = fullscreen,
            slotSide = slotSide,
            leftPercent = leftPercent,
            windowRect = box.rect,
            densityDpi = hitDpi,
            // Tìm thấy task visible → foreground trên display đó; task không-visible → dùng a11y hint.
            foreground = hitVisible || foregroundHint,
            navFresh = navFresh,
        )
    }

    private fun pct(divider: Int, displayW: Int): Int =
        if (displayW <= 0) 50 else (100L * divider / displayW).toInt().coerceIn(1, 99)
}
