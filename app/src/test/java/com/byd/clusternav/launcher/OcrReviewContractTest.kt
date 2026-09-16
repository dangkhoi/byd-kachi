package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KHOÁ LẠI NĂM BẢN VÁ CỦA LƯỢT SOÁT ĐỘC LẬP 2026-09-16 ════════════════════════════════════════════════════
 *
 * Nguồn: `docs/diagnostics/ocr-review-2026-09-16.md`. Năm chỗ dưới đây đều là **đường sống lâu hơn thứ nó phục
 * vụ** hoặc **lá chắn mang hình dạng lá chắn mà không chắn gì** — cả hai đều compile xanh và đều im lặng trên xe,
 * đúng họ lỗi mà CLAUDE.md §8 cảnh báo.
 *
 * Bài này quét **NGUỒN ĐÃ BỎ CHÚ THÍCH** ([SourceRoots.codeOf]) vì cả năm chỗ đều nằm trong `View`/`Service`/
 * `AppWidgetHost` — không dựng được trong JVM thuần, mà phần thuần thì không có. Thứ khoá lại được ở đây là
 * **hình dạng của nhánh**, và đó đúng là thứ đã sai.
 */
class OcrReviewContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    /**
     * **[P1] `SlotAppHost.release()` phải ĐÓNG cổng thử lại, không phải mở nó.**
     *
     * `tryStart`/`tryShellStart` tự hẹn lại qua `h.postDelayed(…, 150)` tới 40 lượt (≈6 giây) và cổng duy nhất
     * chặn chúng là `if (embedded || tries <= 0) return`. `release()` đặt `embedded = false` ⇒ một lượt đã hẹn,
     * về sau khi ô đã tháo, đi qua cổng và gọi `startActivity` / `am start --display` lên một `ActivityView`
     * **vừa được release** — mở app lên một màn ảo không còn tồn tại.
     */
    @Test
    fun `SlotAppHost release go moi luot thu lai TRUOC khi ha co embedded`() {
        val release = SourceRoots.body(code("src/main/java/com/byd/clusternav/launcher/SlotAppHost.kt"), "fun release()")
        val removeAt = release.indexOf("h.removeCallbacksAndMessages(null)")
        val flagAt = release.indexOf("embedded = false")
        assertTrue(removeAt >= 0, "phải gỡ hàng đợi của chính handler này — nếu không, lượt đã hẹn sống lâu hơn ô")
        assertTrue(flagAt > removeAt, "hạ cờ TRƯỚC khi gỡ là mở đúng cái cổng vừa định đóng")
    }

    /**
     * **[P2] Hộp thoại của `CapTestConsole` bung từ luồng nền phải hỏi màn còn sống không.**
     *
     * `KachiLog.snapshot` chạy `logcat -d` (chặn, vài giây). Trong khoảng ấy người dùng đóng được màn Cài đặt
     * hoặc đổi bảng màu (`recreate()`), và `show()` trên một activity đã huỷ ném `BadTokenException` — không ai
     * bắt ⇒ sập launcher. Cùng lá chắn mà `VoiceTextConsole.ask` đã dựng.
     */
    @Test
    fun `CapTestConsole khong bung hop thoai tren mot man da huy`() {
        val body = SourceRoots.body(
            code("src/main/java/com/byd/clusternav/launcher/CapTestConsole.kt"), "private fun snapshotLogs()",
        )
        assertTrue("isFinishing" in body && "isDestroyed" in body, "phải kiểm vòng đời trước khi show()")
        assertTrue("runCatching" in body, "và bọc khe hở còn lại giữa phép kiểm và lời gọi")
    }

    /**
     * **[P2] Bản đồ PiP của bong bóng bị HAI luồng chạm ⇒ phải là bảng đồng thời, và trả lại phải NGUYÊN TỬ.**
     *
     * Luồng `"pip-block"` ghi, luồng `"pip-restore"` (do `onDestroy` khởi) duyệt + xoá. `forEach` chạy trong lúc
     * luồng kia còn `put` ném `ConcurrentModificationException` ⇒ luồng trả lại chết giữa chừng ⇒ `appops …
     * PICTURE_IN_PICTURE deny` của GMaps/YouTube nằm lại **vĩnh viễn** (state ngoài tiến trình, sống qua reboot —
     * CLAUDE.md §5).
     */
    @Test
    fun `FloatingBubbleService giu bang PiP dong thoi va tra lai tung goi mot lan`() {
        val src = code("src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt")
        assertTrue("ConcurrentHashMap<String, String>()" in src, "bảng bị hai luồng chạm — `mutableMapOf` là CME chờ sẵn")
        val restore = SourceRoots.body(src, "private fun restorePipForKnownApps(")
        assertTrue("pipPreviousModes.remove(pkg)" in restore, "lấy ra bằng `remove` nguyên tử ⇒ mỗi gói trả lại đúng một lần")
        assertTrue("runCatching" in restore, "một lệnh shell hỏng ở gói này không được bỏ mặc gói sau ở trạng thái deny")
        assertFalse("pipPreviousModes.forEach" in restore, "duyệt bản đồ dùng chung trong lúc luồng kia còn ghi")
    }

    /**
     * **[P2] `onStartCommand` không được deref `lateinit` mà `onCreate` có thể chưa kịp dựng.**
     *
     * `onCreate` `return` sớm ở ba cổng (`startForegroundOnce` / `castEnabledNow` / `requestOverlayIfMissing`)
     * TRƯỚC khi dựng `renderer`/`gestureHandler`. Cổng lật giữa hai lượt (owner vừa bấm *Cho phép* ở màn hệ
     * thống) ⇒ `showBubble()` → `renderer.buildBubble()` ném `UninitializedPropertyAccessException`.
     * `onDestroy` đã canh đúng hai trường này; đường VÀO cũng phải canh.
     */
    @Test
    fun `FloatingBubbleService canh lateinit o duong vao onStartCommand`() {
        val start = SourceRoots.body(
            code("src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt"),
            "override fun onStartCommand(",
        )
        val guardAt = start.indexOf("::renderer.isInitialized")
        val showAt = start.indexOf("showBubble()")
        assertTrue(guardAt >= 0 && "::gestureHandler.isInitialized" in start, "phải canh CẢ HAI trường lateinit")
        assertTrue(showAt > guardAt, "canh sau khi đã dựng bong bóng thì không canh gì cả")
    }

    /**
     * **[P2] Cổng "thế hệ" của callback widget VietMap phải đến TỪ NGOÀI, không đọc lại chính field đang so.**
     *
     * Bản cũ: `val callbackGeneration = listenerGeneration` rồi ba dòng sau `if (callbackGeneration !=
     * listenerGeneration)` — điều kiện KHÔNG BAO GIỜ đúng, tức mã chết mang hình dạng lá chắn: một lượt
     * RemoteViews của phiên nghe CŨ, đến sau `stop()`/`start()`, vẫn ghi đè snapshot của phiên mới. Thế hệ phải
     * được chụp trong `updateAppWidget` — tức TRƯỚC lượt `post` sang main-looper, đúng chỗ cửa sổ đua mở ra.
     */
    @Test
    fun `VietMap widget chup the he TRUOC luot post sang main-looper`() {
        val host = code("src/main/java/com/byd/clusternav/vietmapwidget/VietMapAppWidgetHost.kt")
        val update = SourceRoots.body(host, "override fun updateAppWidget(")
        val capturedAt = update.indexOf("val generation = listenGeneration()")
        val postAt = update.indexOf("main.post")
        assertTrue(capturedAt >= 0, "phải đọc thế hệ ĐỒNG BỘ trong updateAppWidget")
        assertTrue(postAt > capturedAt, "chụp sau khi đã post là chụp đúng cái giá trị mình định loại bỏ")
        val bridge = code("src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt")
        val cb = SourceRoots.body(bridge, "private fun onHostViewUpdated(")
        assertFalse(
            "val callbackGeneration = listenerGeneration" in cb,
            "đọc lại chính field đang so ⇒ `callbackGeneration != listenerGeneration` không bao giờ đúng",
        )
        assertTrue("callbackGeneration != listenerGeneration" in cb, "cổng thế hệ vẫn phải còn — chỉ đổi NGUỒN của nó")
    }

    /**
     * **[P2] `BydHal.root()` phải chặn VÒNG, không chỉ chặn tự-trỏ.**
     *
     * `while (c.cause != null && c.cause !== c)` chỉ bắt vòng MỘT mắt xích; một vòng hai mắt xích (`a.cause = b;
     * b.cause = a` — sinh ra khi một tầng bọc lại đúng cái lỗi nó vừa nhận) làm vòng lặp chạy vĩnh viễn. Hàm này
     * nằm trên đường ghi nav (~4 lần/giây) nên đơ ở đây = đơ luồng ghi HAL trên xe đang chạy.
     * `LocalShellRetryPolicy.causeChain` đã chặn đúng cách từ trước — đây là cùng lá chắn, đặt vào chỗ còn thiếu.
     */
    @Test
    fun `BydHal root khong bao gio lap vo han tren chuoi cause co vong`() {
        val root = SourceRoots.body(code("src/main/java/com/byd/clusternav/modules/hal/BydHal.kt"), "fun root(t: Throwable)")
        assertTrue("IdentityHashMap" in root, "phải nhớ mắt xích ĐÃ ĐI QUA (so theo danh tính, không theo equals)")
        assertTrue("MAX_CAUSE_DEPTH" in root, "và có trần độ sâu — cùng con số với LocalShellRetryPolicy")
        assertFalse("c.cause !== c" in root, "phép kiểm cũ chỉ bắt được vòng một mắt xích")
    }
}
