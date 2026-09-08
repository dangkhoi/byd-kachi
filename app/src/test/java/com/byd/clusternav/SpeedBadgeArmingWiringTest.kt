package com.byd.clusternav

import com.byd.clusternav.contracts.SpeedLimitSource
import com.byd.clusternav.navigation.SpeedSignPort
import com.byd.clusternav.navigation.SpeedSignLifecycleCoordinator
import com.byd.clusternav.navigation.SpeedSignOutput
import com.byd.clusternav.navigation.SpeedSignSubmission
import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá HỒI QUY **F1** — badge tốc độ câm trên cụm dù cầu widget VietMap đã nối.
 *
 * ── SỰ VIỆC (owner báo 2026-08-24) ────────────────────────────────────────────────────────────────
 * *"binding vietmap tốc độ OK, test hiển speedbadge ok, nhưng chạy thật thì speedbadge không hiện lên cụm"*
 * và quan trọng nhất: *"trước đây lên ngon lành nhé, giờ lại lỗi gì đó không lên nữa"* ⇒ HỒI QUY, không
 * phải chưa-từng-chạy. Theo `.kiro/steering/trace-den-tan-cung.md` §3: từng chạy được thì CHẮC CHẮN có
 * nguyên nhân tìm được — phải diff hai môi trường, cấm kết luận "không làm được".
 *
 * ── VÌ SAO NÚT THỬ KHÔNG BẮT ĐƯỢC ─────────────────────────────────────────────────────────────────
 * `NavigationSpeedSignOwner.debugForceBadge` gọi THẲNG `badgeOverlay.show()`, bỏ qua toàn bộ điều phối.
 * Nút thử xanh chỉ chứng minh: display 1 tồn tại · cửa sổ overlay gắn được · badge vẽ được.
 * Nó KHÔNG chứng minh dữ liệu widget đi tới được badge. Đúng bẫy `CLAUDE.md §8`.
 *
 * ── GỐC RỄ (đã chứng minh bằng git diff) ──────────────────────────────────────────────────────────
 * `SpeedSignLifecycleCoordinator` được dựng kèm `onProcessRestart(epoch)` (NavigationSpeedSignOwner.kt:35),
 * mà hàm đó đặt `masterEnabled=false`, `selectedSource=NONE`, **MỌI cổng ra `enabled=false`**.
 * Chỉ `syncFromPrefs()` mới bật lại. Có HAI đường khởi động cầu widget:
 *   · `onListenerConnected` → CÓ gọi `syncFromPrefs()` trước `addListener` ✅
 *   · `ensureBridgeStarted` (lưới an toàn khi process bị giết mà listener không re-fire) → **KHÔNG gọi** ❌
 * Đi đường thứ hai: cầu chạy, widget về, `speedLimitPusher` bắn — nhưng cổng cụm vẫn đóng ⇒ badge câm.
 *
 * **Vì sao TRƯỚC 08-22 không lộ**: vòng poll `WazeHudSource` gọi `onMasterEnabled(Prefs.enabled(ctx))` +
 * `onSourceSelected(...)` MỖI NHỊP nên tự vá hộ lỗ hổng này. `git diff dabfee1 40474af` cho thấy đúng ba
 * dòng đó biến mất khi B3.30 gỡ kênh Waze HUD Link (kênh đo ra 0 dòng logcat, gỡ là đúng — nhưng nó kéo
 * theo một bản vá vô tình mà không ai biết là đang tồn tại).
 *
 * Đây chính là bài học `CLAUDE.md §8`: gỡ code chết phải hỏi *"nó còn đang gánh hộ việc gì không?"*.
 */
class SpeedBadgeArmingWiringTest {

    private val listener: String by lazy {
        KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/NavNotificationListener.kt"))
    }

    /** Cổng giả — chỉ đếm, không chạm Android. */
    private class FakePort(override val output: SpeedSignOutput) : SpeedSignPort {
        override fun publish(frame: com.byd.clusternav.contracts.SpeedLimitFrame, generation: Long) =
            SpeedSignSubmission.ACCEPTED
        override fun replaceWithClear(frame: com.byd.clusternav.contracts.SpeedLimitFrame, generation: Long) =
            SpeedSignSubmission.ACCEPTED
        override fun close() = Unit
    }

    private fun newCoordinator() = SpeedSignLifecycleCoordinator(
        clusterPort = FakePort(SpeedSignOutput.CLUSTER), hudPort = FakePort(SpeedSignOutput.HUD), monotonicNowMs = { 1_000L },
    )

    /**
     * CƠ CHẾ (không phải suy diễn): sau `onProcessRestart`, khung tốc độ bị BỎ cho tới khi công tắc chính
     * VÀ cổng ra được bật. Test này khoá đúng cái làm badge câm.
     */
    @Test
    fun `sau khi dung lai bo dieu phoi — khung toc do bi BO cho toi khi bat cong tac va cong ra`() {
        val c = newCoordinator()
        c.onProcessRestart(1L)

        // Chỉ chọn nguồn (đúng cái `speedLimitPusher` làm trước bản vá) — VẪN chưa đủ.
        c.onSourceSelected(SpeedLimitSource.VIETMAP)
        assertFalse(
            c.onSpeedLimit(SpeedLimitSource.VIETMAP, 50, 1_000L, 1L),
            "chưa bật công tắc chính ⇒ phải BỎ khung — đây chính là ca badge câm mà owner gặp",
        )

        // Bật công tắc chính nhưng cổng cụm vẫn đóng ⇒ khung qua được cổng nguồn nhưng KHÔNG tới badge.
        c.onMasterEnabled(true)
        assertTrue(c.onSpeedLimit(SpeedLimitSource.VIETMAP, 50, 2_000L, 1L), "đủ điều kiện thì phải nhận")
        assertFalse(SpeedSignOutput.CLUSTER in c.snapshot().enabledOutputs,
            "onProcessRestart tắt MỌI cổng ra — chỉ syncFromPrefs mới mở lại")

        c.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
        assertTrue(SpeedSignOutput.CLUSTER in c.snapshot().enabledOutputs)
    }

    /**
     * WIRING — khoá BẢN VÁ. Đường lưới-an-toàn PHẢI gọi `syncFromPrefs()` TRƯỚC `addListener`,
     * y như đường `onListenerConnected`. Gỡ dòng đó ⇒ test này ĐỎ.
     */
    @Test
    fun `ensureBridgeStarted PHAI sync truoc khi gan listener`() {
        val body = listener.substringAfter("private fun ensureBridgeStarted()")
            .substringBefore("\n    private fun ")
        val sync = body.indexOf("speedSignOwner.syncFromPrefs()")
        val add = body.indexOf("bridge.addListener(speedLimitPusher)")
        assertTrue(sync >= 0, "lưới an toàn KHÔNG sync ⇒ cổng cụm đóng ⇒ badge câm (hồi quy F1)")
        assertTrue(add >= 0, "không tìm thấy addListener — cấu trúc đổi, đọc lại KDoc trước khi sửa test")
        assertTrue(sync < add, "phải sync TRƯỚC khi gắn listener, nếu không nhịp widget đầu tiên rơi vào cổng đóng")
    }

    /**
     * TỰ LÀNH THEO NHỊP — khôi phục đúng hành vi vòng poll `WazeHudSource` đã gỡ: mỗi nhịp widget phải
     * sync lại, để bộ điều phối bị đưa về TẮT SẠCH lúc nào cũng tự hồi phục, không phụ thuộc đường nào
     * khởi động cầu. Đây là vế thứ hai của bản vá F1.
     */
    @Test
    fun `speedLimitPusher PHAI sync moi nhip`() {
        val body = listener.substringAfter("private val speedLimitPusher")
            .substringBefore("override fun onNotificationRemoved")
        assertTrue(
            body.contains("speedSignOwner.syncFromPrefs()"),
            "pusher không sync mỗi nhịp ⇒ mất khả năng tự hồi phục mà vòng poll Waze cũ vẫn gánh hộ",
        )
    }
}
