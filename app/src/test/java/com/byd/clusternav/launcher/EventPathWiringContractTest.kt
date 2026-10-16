package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * 3.1.10 · EVENT-PATH-SCOPED · HOLD-EXPIRY-REDRAW · TURN-POLL-SERVED — dây nối ba luật `:core` vào bề mặt (`docs/diagnostics/perf-3110-event-
 * path-2026-10-16.md`). Luật khoá ở `EventPathScopedTest` / `HalLoadBudgetTest`; bài này đỏ khi gỡ một dây (CLAUDE.md §8: hàm mới phải
 * có chỗ gọi).
 */
class EventPathWiringContractTest {
    private fun src(f: String) = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/$f"))

    @Test fun `o widget chi lam moi so khi delta cham tap no bay`() {
        val w = src("launcher/WorkspaceView.kt")
        assertTrue(w.contains("val delta = CarStatusDelta.of(oldStatus, status); when (val plan"), "WorkspaceView tính delta một lần mỗi lượt")
        assertTrue(w.contains("if (onlyValues && nc is SlotContent.Widget && !delta.touches(CarDataDemand.interestOf(nc.ids))) return@forEach"),
            "ô không bày datum đổi ⇒ không đổ (và KHÔNG lùi về dựng lại)")
        val r = src("launcher/KachiHomeRender.kt")
        assertTrue(r.contains("CarStatusDelta.of(prev.carStatus, state.carStatus).touches(CarDataDemand.interestOf(state.dock.enabled))"), "thanh nút")
    }

    @Test fun `dich giu het han hen mot luot ve lai tren handler co san`() {
        val r = src("launcher/KachiHomeRender.kt")
        val watch = SourceRoots.body(r, "internal fun KachiHomeActivity.watchHoldExpiry(on: Boolean)")
        assertTrue(watch.contains("LampHold.shared.onPut = if (on)") && watch.contains("handler.removeCallbacksAndMessages(HoldExpiryToken)"), watch)
        // Soát Pass 11 [P2]: lời báo đích mới cũng mang token — `post` trần sống qua `removeCallbacksAndMessages(token)` lúc màn rời.
        assertTrue(watch.contains("handler.postAtTime({ armHoldExpiry() }, HoldExpiryToken,"), "onPut hẹn bằng token, không `handler.post` trần: $watch")
        assertFalse(watch.contains("handler.post {"), watch)
        val arm = SourceRoots.body(r, "private fun KachiHomeActivity.armHoldExpiry()")
        assertTrue(arm.contains("LampHold.shared.nextExpiryIn(now) ?: return") &&
            arm.contains("handler.postAtTime({ resyncTiles(); armHoldExpiry() }, HoldExpiryToken, now + wait)"), arm)
        val wiring = src("launcher/KachiHomeWiring.kt")
        assertTrue(wiring.contains("val hold = owner as? KachiHomeActivity") && wiring.contains("hold?.watchHoldExpiry(true); try {") &&
            wiring.contains("finally { hold?.watchHoldExpiry(false) }"), "nghe khi màn ở trước (STARTED), gỡ khi rời")
    }

    @Test fun `xi-nhan hop socket dang de thi vong poll khong doc`() {
        assertTrue(src("AppContainer.kt").contains(
            "carDemand.get()?.let { it - com.byd.clusternav.launcher.LampLive.shared.served() }"),
            "nhu cầu của CarDataAdapter trừ datum LampLive đang đè")
    }

    /** Soát Pass 11 [P2]: hộp đứt ⇒ datum nó vừa đè (không được poll suốt lúc hộp sống) đọc lại NGAY một lượt, không chờ nhịp chậm 10 s. */
    @Test fun `hop socket dut thi datum vua de doc lai ngay qua duong su kien`() {
        assertTrue(src("AppContainer.kt").contains(
            "com.byd.clusternav.launcher.LampLive.shared.onServedDropped = { ids -> carStatusRepository.onEvents(ids) }"),
            "LampLive.onServedDropped → CarStatusRepository.onEvents (một lượt readEvents cho đúng tập vừa đè)")
    }
}
