package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá ĐƯỜNG DÂY lớp 3 (2.83) — kẹt lúc đang chạy: watchdog 30 s thôi re-grant/toggle khi dump gần nhất đã nói KẸT,
 * chỉ kiểm lại chậm bằng dump. Luật thuần + mô phỏng nhiều giờ ở `A11yRunningStuckGatesTest` (:core); bài này đỏ khi
 * các hàm đó KHÔNG được gọi (CLAUDE.md §8: compile xanh ≠ code chạy).
 *
 * Bài học [ĐO máy ảo 29/09, E2E 2.83 ca 4]: mỗi 30 s một cặp NOT_BOUND/STUCK + một lần TẮT/BẬT
 * `enabled_accessibility_services` vô ích; nhật ký 200 dòng đầy sau ~50 phút.
 *
 * Vùng quét cắt bằng ĐẾM NGOẶC ([SourceRoots.body]) trên mã đã bỏ chú thích — không `substringAfter/Before`.
 */
class A11yRunningStuckWiringContractTest {

    private val keepAlive = SourceRoots.codeOf("src/main/java/com/byd/clusternav/VoiceKeyKeepAliveService.kt")
    private val heal = SourceRoots.codeOf("src/main/java/com/byd/clusternav/A11yLifecycleHeal.kt")
    private val nav = SourceRoots.codeOf("src/main/java/com/byd/clusternav/NavConnect.kt")

    private val tick = SourceRoots.body(keepAlive, "private val watchdog = object : Runnable {")

    @Test
    fun `watchdog hoi cong thuan roi moi re-grant, va CHI o nhanh GRANT`() {
        assertTrue(tick.contains("val stuckSeenAt = A11yLifecycleHeal.runningStuckSeenAt(bound)"), "mốc kẹt đọc MỖI nhịp")
        assertTrue(tick.contains("when (AccessibilityHealGates.watchdogStep(bound, stuckSeenAt, now))"), "việc của nhịp do cổng thuần chọn")
        assertEquals(1, Regex("NavConnect\\.grantAccessibility\\(").findAll(tick).count(), "đúng MỘT chỗ re-grant")
        val grant = SourceRoots.body(tick, "AccessibilityHealGates.WatchdogStep.GRANT -> {")
        assertTrue(grant.contains("NavConnect.grantAccessibility(app)"), "re-grant (toggle) CHỈ khi chưa biết là kẹt")
        assertFalse(tick.contains("if (!bound) {"), "cổng cũ 'chưa gắn là re-grant' = vòng toggle vô ích mỗi 30 s")
        assertTrue(
            tick.contains("AccessibilityHealGates.WatchdogStep.RECHECK -> A11yLifecycleHeal.recheckRunningStuck(app)"),
            "đã biết kẹt ⇒ tới nhịp thì kiểm lại bằng dump — hàm mới phải có call site",
        )
    }

    @Test
    fun `watchdog ghi nhat ky theo trang thai cua cong, van khai binderOnly`() {
        assertTrue(
            tick.contains("AccessibilityHealGates.watchdogState(bound, stuckSeenAt, now),"),
            "nhịp tim lúc đang kẹt phải ghi STUCK (sự thật đã đo), không NOT_BOUND giả ⇒ không cặp NOT_BOUND/STUCK mỗi giờ",
        )
        assertFalse(
            tick.contains("if (bound) A11yBindJournal.State.BOUND else A11yBindJournal.State.NOT_BOUND"),
            "ghi thẳng NOT_BOUND khi đang kẹt = lại sinh cặp NOT_BOUND/STUCK",
        )
        // Hai chốt cũ (A11yBindStuckWiringContractTest) phải còn đúng: một chỗ ghi, và khai quan sát CHỈ binder.
        assertEquals(1, Regex("A11yBindJournalStore\\.record\\(").findAll(tick).count())
        assertEquals(1, Regex("binderOnly = true").findAll(tick).count())
    }

    @Test
    fun `moi lan do bang dump o duong grant PHAI cap nhat moc ket, truoc khi quyet nac`() {
        val escalate = SourceRoots.body(nav, "private fun escalateIfStuck(")
        val measured = escalate.indexOf("val stuck = AccessibilityRebind.isInBindingServices(")
        val note = escalate.indexOf("A11yLifecycleHeal.noteStuckDump(stuck)")
        val decide = escalate.indexOf("AccessibilityHealGates.healStep(")
        assertTrue(measured in 0 until note, "ghi mốc từ CHÍNH bản dump vừa đọc")
        assertTrue(note in 0 until decide, "ghi TRƯỚC mọi nhánh return (lớp 3 RUNNING ⇒ NONE là nhánh cần mốc nhất)")
    }

    @Test
    fun `kiem lai CHI DOC - khong toggle, khong giet, khong ghi settings`() {
        val fn = SourceRoots.body(heal, "internal fun recheckRunningStuck(app: Context) {")
        assertTrue(fn.contains("observe(app)"), "dùng lại bộ đo sẵn có (binder rồi dump) — DRY, không tự mở phiên")
        assertTrue(fn.contains("AccessibilityHealGates.stuckMarkOf(o)"), "mốc sau kiểm lại do hàm thuần đã test quyết")
        assertTrue(fn.contains("A11yBindJournalStore.record(app, stateOf(o), A11yBindJournal.NOTE_RECHECK)"),
            "hết kẹt ⇒ MỘT dòng chuyển trạng thái; vẫn kẹt ⇒ cùng trạng thái ⇒ không thêm dòng")
        assertTrue(fn.contains("worker.execute {"), "chạy trên luồng nối tiếp của lớp 1/2 — không đè lượt đo/leo nào")
        listOf("grantAccessibility", "escalate", "forceRebind", "settings put", "force-stop", "NavConnect.").forEach { t ->
            assertFalse(fn.contains(t), "'$t' trong lượt kiểm lại = lớp 3 lại tự đổi hệ thống (owner: người dùng tự chữa)")
        }
    }

    @Test
    fun `co single-flight va moc PHAI nha trong finally`() {
        val fn = SourceRoots.body(heal, "internal fun recheckRunningStuck(app: Context) {")
        assertTrue(fn.contains("if (!rechecking.compareAndSet(false, true)) return"))
        val fin = SourceRoots.body(fn, "} finally {")
        assertTrue(fin.contains("rechecking.set(false)"), "cờ kẹt = watchdog không bao giờ đo lại (gate vòng tròn, CLAUDE.md §3)")
        assertTrue(fin.contains("runningStuckAt.set(seen)"), "đọc hỏng / ném ⇒ mốc -1 ⇒ nhịp sau đi đường grant cũ, không im")
        assertTrue(fn.contains("var seen = -1L"), "mặc định là KHÔNG biết kẹt")
    }

    /**
     * Alarm 60 s (lưới phụ, WAKEUP) chỉ heal khi FGS keep-alive CHẾT — [ĐO xe c2 29/09] đúng khoảng tắt máy. Không cùng
     * cổng thì ca kẹt mà lớp 1 không chữa được thành vòng TẮT/BẬT `enabled_accessibility_services` mỗi phút suốt đêm.
     */
    @Test
    fun `alarm 60 s di CUNG cong thuan - da biet ket thi khong re-grant`() {
        val receiver = SourceRoots.codeOf("src/main/java/com/byd/clusternav/RebindReceiver.kt")
        val rx = SourceRoots.body(receiver, "override fun onReceive(context: Context, intent: Intent?)")
        assertTrue(rx.contains("val bound = NavConnect.boundPerAccessibilityManager(app) == true"),
            "chỉ binder (không rơi về cờ RAM có thể kẹt true) — binder không hỏi được ⇒ đi grant như cũ")
        val step = SourceRoots.body(
            rx,
            "when (AccessibilityHealGates.watchdogStep(bound, A11yLifecycleHeal.runningStuckSeenAt(bound), SystemClock.elapsedRealtime())) {",
        )
        val grantAt = step.indexOf("AccessibilityHealGates.WatchdogStep.GRANT ->")
        val callAt = step.indexOf("NavConnect.grantAccessibility(context.applicationContext)")
        val recheckAt = step.indexOf("AccessibilityHealGates.WatchdogStep.RECHECK -> A11yLifecycleHeal.recheckRunningStuck(app)")
        val noneAt = step.indexOf("AccessibilityHealGates.WatchdogStep.NONE ->")
        assertTrue(grantAt in 0 until callAt && callAt < recheckAt && recheckAt < noneAt,
            "re-grant CHỈ ở nhánh GRANT; kẹt đã đo ⇒ kiểm lại chỉ-đọc; còn lại không làm gì")
        assertEquals(1, Regex("NavConnect\\.grantAccessibility\\(").findAll(rx).count(), "đúng MỘT chỗ re-grant trong receiver")
    }

    @Test
    fun `binder thay da gan thi xoa moc - lan dut sau la su kien moi`() {
        val fn = SourceRoots.body(heal, "internal fun runningStuckSeenAt(bound: Boolean): Long {")
        assertTrue(fn.contains("if (bound) runningStuckAt.set(-1L)"))
        val note = SourceRoots.body(heal, "internal fun noteStuckDump(stuck: Boolean) {")
        assertTrue(note.contains("runningStuckAt.set(if (stuck) SystemClock.elapsedRealtime() else -1L)"),
            "dump nói KHÔNG kẹt ⇒ nhả ngay (ca 'bật mà chưa gắn' phải về toggle như cũ)")
    }
}
