package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá ĐƯỜNG DÂY của lớp 1 (TẮT MÁY) + lớp 2 (MỞ XE) — 2.83, owner chốt 2026-09-29. Logic thuần ở
 * `A11yLifecycleGatesTest` (`:core`); bài này đỏ khi móc bị nuốt mất (CLAUDE.md §8: compile xanh ≠ code chạy).
 *
 * Mọi vùng quét cắt bằng ĐẾM NGOẶC ([SourceRoots.body]) trên mã ĐÃ BỎ CHÚ THÍCH ([SourceRoots.codeOf]) — không
 * `substringAfter/Before` (bẫy "quét tràn = test giả").
 */
class A11yLifecycleHealWiringContractTest {

    private val app = SourceRoots.codeOf("src/main/java/com/byd/clusternav/KachiApplication.kt")
    private val heal = SourceRoots.codeOf("src/main/java/com/byd/clusternav/A11yLifecycleHeal.kt")
    private val nav = SourceRoots.codeOf("src/main/java/com/byd/clusternav/NavConnect.kt")
    private val prefs = SourceRoots.codeOf("src/main/java/com/byd/clusternav/PrefsA11yLifecycle.kt")

    private fun order(src: String, vararg tokens: String) {
        val at = tokens.map { t -> src.indexOf(t).also { assertTrue(it >= 0, "thiếu '$t' trong vùng quét") } }
        assertEquals(at.sorted(), at, "thứ tự sai: ${tokens.joinToString(" → ")}")
    }

    // ─── Móc lúc khởi động tiến trình (lớp 1) ────────────────────────────────────────────────────────────

    @Test
    fun `moc khoi dong nam SOM NHAT o tien trinh launcher`() {
        val onCreate = SourceRoots.body(app, "override fun onCreate() {")
        order(onCreate, "if (isBackgroundVoiceProcess()) return", "A11yLifecycleHeal.install(this)", "AppContainer.get(this)")
    }

    @Test
    fun `lan khoi dong man tat PHAI mo luot tat-may sau khi claim`() {
        val start = SourceRoots.body(heal, "private fun onProcessStart(")
        order(
            start,
            "AccessibilityHealGates.tatMayMayRun(interactive,",
            "Prefs.setA11yTatMayAt(app, startedAt)",
            "healIfStuck(app, HealPhase.TAT_MAY,",
        )
        val install = SourceRoots.body(heal, "fun install(ctx: Context) {")
        assertTrue(install.contains("val interactive = interactive(app)"), "phải đo màn tắt NGAY lúc tiến trình khởi động")
        assertTrue(install.contains("onProcessStart(app, interactive, startedAt)"), "móc lớp 1 phải có call site")
    }

    // ─── Bộ thu màn bật (lớp 2) ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `bo thu man bat dang ky DONG o tang tien trinh`() {
        val install = SourceRoots.body(heal, "fun install(ctx: Context) {")
        assertTrue(
            install.contains("app.registerReceiver(ScreenOnReceiver(app), IntentFilter(Intent.ACTION_SCREEN_ON))"),
            "[ĐO AOSP Intent.java:2217-2220] SCREEN_ON chỉ tới bộ thu đăng ký ĐỘNG. [ĐO xe c2] keep-alive lên SAU màn " +
                "bật 4,5 s ⇒ phải đăng ký ở đây (tiến trình sống suốt lúc tắt máy), không ở FGS",
        )
    }

    @Test
    fun `onReceive cua broadcast ORDERED chi lay moc roi tra ngay`() {
        val rx = SourceRoots.body(heal, "override fun onReceive(context: Context?, intent: Intent?) {")
        assertTrue(rx.contains("SystemClock.elapsedRealtime()"), "mốc sự kiện lấy lúc nhận")
        assertTrue(rx.contains("onScreenOn(app, at)"), "lớp 2 phải có call site")
        assertTrue(rx.contains("submit("), "việc nặng đẩy sang luồng nền")
        listOf("healIfStuck(", "Prefs.", "LocalDeviceShell", "Thread.sleep").forEach { t ->
            assertFalse(rx.contains(t), "'$t' trong onReceive = chặn broadcast ORDERED của hệ (Notifier.java:748-755)")
        }
    }

    @Test
    fun `lan man bat PHAI claim truoc roi moi xet an han va do`() {
        val on = SourceRoots.body(heal, "private fun onScreenOn(")
        order(
            on,
            "val prevMoXe = Prefs.a11yMoXeAt(app)",
            "AccessibilityHealGates.moXeFresh(screenOnAt, prevMoXe, now)",
            "Prefs.setA11yMoXeAt(app, screenOnAt)",
            "AccessibilityHealGates.withinMoXeGrace(screenOnAt, now)",
            "AccessibilityHealGates.moXeFollowsTatMay(Prefs.a11yTatMayAt(app), prevMoXe, now)",
            "healIfStuck(app, HealPhase.MO_XE, screenOnAt)",
        )
    }

    /**
     * Lượt review 2 (2.83): màn bật không theo sau lần tắt máy nào (vd tắt/bật màn lúc đang lái) KHÔNG được vào đường
     * có quyền tự giết launcher — owner: lớp 3 không tự chữa. Luật thuần + mốc xe c1/c2 ở `A11yLifecycleGatesTest`.
     * Cổng phải đọc mốc mở-xe TRƯỚC khi claim: đọc sau thì luôn thấy chính mốc vừa ghi ⇒ cổng đóng vĩnh viễn.
     */
    @Test
    fun `lop 2 PHAI hoi co lan tat may nao truoc lan man bat nay`() {
        val on = SourceRoots.body(heal, "private fun onScreenOn(")
        assertEquals(1, Regex("Prefs\\.a11yMoXeAt\\(app\\)").findAll(on).count(), "mốc mở-xe trước chỉ đọc MỘT lần, trước claim")
        assertTrue(on.contains("if (!AccessibilityHealGates.moXeFollowsTatMay("), "kết quả cổng phải GÁC lượt, không chỉ log")
    }

    // ─── Đo kẹt BỀN rồi mới vào đường leo SẴN CÓ ─────────────────────────────────────────────────────────

    @Test
    fun `chi leo khi KET BEN va di dung duong leo san co`() {
        val fn = SourceRoots.body(heal, "private fun healIfStuck(")
        order(
            fn,
            "Prefs.voiceKeyEnabled(app)",
            "val first = observe(app)",
            "waitInPhase(app, phase, screenOnAt, AccessibilityHealGates.STUCK_CONFIRM_GAP_MS)",
            "val second = observe(app)",
            "AccessibilityHealGates.stuckPersistent(first, second)",
            "NavConnect.escalateOnLifecycle(app, phase)",
        )
        assertTrue(fn.contains("A11yBindJournalStore.record("), "lượt tắt-máy/mở-xe phải để lại dòng nhật ký 'tat-may'/'mo-xe'")
        assertFalse(fn.contains("force-stop"), "KHÔNG tự dựng lệnh giết ở đây — chỉ một đường có chốt gói")
        assertFalse(heal.contains("forceStopRebindCommand"), "lệnh tự giết chỉ được dựng ở NavConnect.escalateIfStuck")
    }

    @Test
    fun `quan sat dung hai parser dang chay va dung component cua chinh app`() {
        val fn = SourceRoots.body(heal, "private fun observe(app: Context): BindObservation? {")
        assertTrue(fn.contains("AccessibilityHealGates.observe(dump, SystemClock.elapsedRealtime(), NavConnect.ACC_COMP)"))
        assertTrue(fn.contains("NavConnect.boundPerAccessibilityManager(app) == true"), "đã gắn theo binder ⇒ 0 lệnh shell")
    }

    @Test
    fun `duong vao leo thang lop 1 va 2 bo nac toggle va di thang escalateIfStuck`() {
        val fn = SourceRoots.body(nav, "internal fun escalateOnLifecycle(")
        assertTrue(fn.contains("escalateIfStuck(app, sh, userAsked = false, phase = phase, fireGate = fireGate)"))
        assertFalse(fn.contains("forceRebindIfNeeded("), "toggle vô hiệu ở ca kẹt (AOSP :1630-1631) và ăn ~8 s ân hạn")
        assertFalse(
            fn.contains("grantingAcc"),
            "[ĐO xe c2] lượt grant của Preflight giữ cờ này 11:34:19→11:34:29,7, phủ trọn ân hạn mở xe ⇒ dùng chung là " +
                "lớp 2 không bao giờ tới lượt",
        )
        assertTrue(fn.contains("if (phase == AccessibilityHealGates.HealPhase.RUNNING) return GrantResult.NOT_BOUND"),
            "lớp 3 không được vào cửa có quyền tự giết")
        assertTrue(
            fn.contains("LocalDeviceShell.session(AdbKeys.ensure(app), LocalShellRetry.BACKGROUND_READ_CAP)"),
            "phiên phải có hạn ĐỌC — một socket câm không được ghim luồng nối tiếp của hai lớp mãi",
        )
    }

    @Test
    fun `marker su kien ghi DONG BO`() {
        listOf("fun Prefs.setA11yTatMayAt(", "fun Prefs.setA11yMoXeAt(").forEach { sig ->
            val fn = SourceRoots.body(prefs, sig)
            assertTrue(fn.contains(".commit()"), "$sig: CLAUDE.md §5 — tiến trình có thể chết ngay sau (lượt force-stop)")
            assertFalse(fn.contains(".apply()"), "$sig: apply() ghi nền, chết trước khi flush là lặp lượt cho cùng sự kiện")
        }
        // Ghi claim HỎNG ⇒ không chữa (fail-safe): kết quả commit() phải GÁC lượt, không bị bỏ qua.
        assertTrue(
            SourceRoots.body(heal, "private fun onProcessStart(").contains("if (!Prefs.setA11yTatMayAt(app, startedAt))"),
            "claim tắt-máy ghi hỏng mà vẫn chữa ⇒ tiến trình do lượt này dựng lại (màn vẫn tắt) mở lượt thứ hai = vòng lặp",
        )
        assertTrue(
            SourceRoots.body(heal, "private fun onScreenOn(").contains("if (!Prefs.setA11yMoXeAt(app, screenOnAt))"),
            "claim mở-xe ghi hỏng thì không chữa",
        )
    }

    @Test
    fun `cong cuoi cua luot vong doi hoi lai man that`() {
        val fn = SourceRoots.body(heal, "private fun stillInPhase(")
        assertTrue(fn.contains("AccessibilityHealGates.lifecycleFireAllowed(phase, interactive(app), screenOnAt,"))
        assertTrue(
            SourceRoots.body(heal, "private fun healIfStuck(").contains("{ stillInPhase(app, phase, screenOnAt) }"),
            "cổng cuối phải được TRAO cho đường leo (hỏi ngay trước khi bắn), không chỉ hỏi lúc bắt đầu",
        )
    }

    @Test
    fun `luot sau chua duoc cham diem ngay ca khi keep-alive chua len`() {
        val start = SourceRoots.body(heal, "private fun onProcessStart(")
        // [ĐO máy ảo 29/09] cửa sổ 2 phút một mình chấm oan `sau-chua-VAN-TAT` khi BYD giết TIẾP trong cửa sổ ⇒ chỉ
        // tiến trình ĐẦU TIÊN sau lượt leo được chấm: hỏi luật → NHẬN (commit) → mới chấm.
        order(
            start,
            "AccessibilityHealGates.firstStartAfterHeal(escalatedAt, Prefs.a11yScoredFor(app), startedAt, AFTER_HEAL_WINDOW_MS)",
            "Prefs.setA11yScoredFor(app, escalatedAt)",
            "scoreAfterHeal(app, escalatedAt)",
        )
        assertFalse(start.contains("AccessibilityHealGates.justHealed("), "không còn cổng chỉ-theo-cửa-sổ (chấm oan)")
        assertTrue(
            prefs.contains("sp(ctx).edit().putLong(K_A11Y_SCORED_FOR, v).commit()"),
            "mốc đã-chấm phải ghi commit() — tiến trình chết giữa chừng thì lượt sau không chấm bừa",
        )
        assertTrue(SourceRoots.body(heal, "private fun scoreAfterHeal(").contains("A11yBindJournal.afterHealNote(bound)"))
    }

    /**
     * Khoá lỗi [ĐO máy ảo 29/09, E2E 2.83 vòng 2, 15:51:00]: BYD giết Kachi 90 s sau lượt "Sửa ngay" (mốc leo còn trong
     * lần nổ máy) ⇒ nhịp đầu watchdog của tiến trình MỚI ghi `NOT_BOUND note=sau-chua-VAN-TAT` — nhãn oan cho một lượt
     * chữa đã ăn. Nhãn `sau-chua-*` của watchdog chỉ được gắn khi tiến trình này đúng là con của lượt chữa: cờ đặt ĐÚNG
     * một chỗ, SAU khi nhận chấm (commit), và watchdog hỏi cờ đó trong cùng điều kiện với nhãn.
     */
    @Test
    fun `nhan sau-chua cua watchdog CHI cho tien trinh sinh tu luot chua`() {
        val start = SourceRoots.body(heal, "private fun onProcessStart(")
        order(start, "Prefs.setA11yScoredFor(app, escalatedAt)", "bornFromOwnHeal.set(true)", "scoreAfterHeal(app, escalatedAt)")
        assertEquals(1, Regex("bornFromOwnHeal\\.set\\(").findAll(heal).count(), "chỉ MỘT chỗ đặt cờ — không nhả, không đặt ở nơi khác")
        val keepAlive = SourceRoots.codeOf("src/main/java/com/byd/clusternav/VoiceKeyKeepAliveService.kt")
        val tick = SourceRoots.body(keepAlive, "private val watchdog = object : Runnable {")
        assertTrue(
            tick.contains("firstTick && Prefs.a11yEscalatedAt(app) >= 0L && A11yLifecycleHeal.bornFromOwnHeal() ->"),
            "mốc leo còn nguyên KHÔNG đủ để gắn nhãn sau-chua — phải là tiến trình sinh ra từ chính lượt chữa",
        )
    }
}
