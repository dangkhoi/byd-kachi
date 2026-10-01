package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.HealPhase
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.HealStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ READY-AT-HOME (02/10) — LỚP 2 MỞ RỘNG: ÂN HẠN KHỞI ĐỘNG ═══════════════════════════════════════════════════════
 *
 * Khoá lỗi [ĐO E2E máy ảo 02/10 ca 1, `R2-logcat-stream.txt`]: giết kiểu BYD (`killApplication`, không
 * `PACKAGE_RESTARTED`) lúc màn ĐANG bật 00:14:56.473 ⇒ tiến trình mới 56.645 `tương tác=true` ⇒ lớp 1 không mở (màn bật),
 * lớp 2 không mở (không có `ACTION_SCREEN_ON` mới) ⇒ `force-rebind xong: bound=false` 15:06.372 → `nấc NONE, KHÔNG leo`
 * (pha RUNNING) ⇒ phím chết ~70 s. Nay: tiến trình DỰNG LẠI lúc màn sáng có MỘT lượt [HealPhase.KHOI_DONG] trong
 * [AccessibilityHealGates.BOOT_GRACE_MS]; ngoài ân hạn ⇒ luật 2.83 RUNNING (không tự leo) giữ nguyên.
 *
 * Bảng dưới GHÉP đúng các hàm thuần theo đúng thứ tự mã chạy (`A11yLifecycleHeal.onBootGrace` → `healIfStuck` →
 * `NavConnect.escalateIfStuck`); thứ tự đó do `A11yBootGraceWiringContractTest` (`:app`) khoá. Dump là fixture NGUYÊN VĂN
 * từ xe 29/09 (cùng bộ với `A11yLifecycleGatesTest`).
 */
class A11yBootGraceGatesTest {

    private val comp = "com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService"

    private fun fixture(name: String): String {
        val text = javaClass.getResourceAsStream("/diagnostics/a11y-0929/$name")?.bufferedReader()?.readText()
        assertNotNull(text, "thiếu fixture $name — bài test đang quét thứ không tồn tại")
        return text!!
    }

    private val stuckDump by lazy { fixture("k1-acc-stuck.txt") }
    private val boundDump by lazy { fixture("fix2-acc-bound.txt") }

    private val start = START
    private val prevProc = PREV_PROC
    private val noHeal = NO_HEAL

    // ─── Cổng vào: chỉ tiến trình DỰNG LẠI lúc màn SÁNG, không sinh từ lượt chữa của mình ─────────────────────

    @Test
    fun `bang ca cong vao bootGraceMayRun`() {
        data class Row(val why: String, val interactive: Boolean?, val prev: Long, val child: Boolean, val expect: Boolean)
        listOf(
            Row("E2E ca 1: dựng lại lúc màn sáng, không phải con của lượt chữa", true, prevProc, false, true),
            Row("màn tắt lúc bật = việc của lớp 1", false, prevProc, false, false),
            Row("không hỏi được isInteractive ⇒ không đoán", null, prevProc, false, false),
            Row("tiến trình ĐẦU TIÊN từ trước tới nay (chưa có mốc)", true, -1L, false, false),
            Row("tiến trình đầu tiên của lần nổ máy (mốc đời máy trước lớn hơn bây giờ)", true, 9_000_000_000L, false, false),
            Row("mốc trùng chính mình ⇒ không phải tiến trình TRƯỚC", true, start, false, false),
            Row("CON của lượt chữa ⇒ KHÔNG leo nữa (chống vòng lặp)", true, prevProc, true, false),
        ).forEach { r ->
            assertEquals(r.expect, AccessibilityHealGates.bootGraceMayRun(r.interactive, r.prev, r.child, start), r.why)
        }
    }

    /** 2.83 R-A4: "sinh từ chính lượt chữa ⇒ không leo; bị giết từ bên ngoài ⇒ được leo một lần". */
    @Test
    fun `bang ca con cua luot chua`() {
        val leo = start - 300L
        val w = AccessibilityHealGates.OWN_HEAL_WINDOW_MS
        data class Row(val why: String, val claimedHere: Boolean, val esc: Long, val scoredFor: Long, val child: Boolean)
        listOf(
            Row("vừa NHẬN chấm điểm mốc leo (tiến trình đầu tiên sau lượt leo) ⇒ con", true, leo, leo, true),
            Row("mốc leo trong cửa sổ mà CHƯA ai nhận (claim ghi hỏng) ⇒ coi là con (fail-safe)", false, leo, -1L, true),
            Row("[ĐO E2E 02/10 01:51:43] mốc leo trong cửa sổ, tiến trình KHÁC đã nhận ⇒ lần giết MỚI ⇒ không phải con", false, leo, leo, false),
            Row("chưa từng leo", false, -1L, -1L, false),
            Row("lượt leo đã qua cửa sổ", false, start - w - 1L, -1L, false),
            Row("mốc leo của đời máy trước", false, 9_000_000_000L, -1L, false),
        ).forEach { r ->
            assertEquals(r.child, AccessibilityHealGates.ownHealChild(r.claimedHere, r.esc, r.scoredFor, start), r.why)
        }
    }

    // ─── Ngoài ân hạn KHÔNG leo — luật 2.83 RUNNING giữ nguyên ────────────────────────────────────────────────

    @Test
    fun `ngoai an han khoi dong KHONG leo - luat 2_83 dang chay giu nguyen`() {
        val g = AccessibilityHealGates.BOOT_GRACE_MS
        assertTrue(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.KHOI_DONG, true, start, start + 7_000L), "E2E: bắn ≈ +7 s")
        assertTrue(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.KHOI_DONG, true, start, start + g), "mép ân hạn còn được")
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.KHOI_DONG, true, start, start + g + 1L),
            "quá ân hạn = người lái đã dùng xe ⇒ owner 29/09: 'lúc đang chạy mà lỗi thì user tự chữa'")
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.KHOI_DONG, false, start, start + 3_000L),
            "màn tắt giữa chừng ⇒ KHOI_DONG không bắn — lượt được TRAO lớp 1 (bootGraceHandsOffToTatMay, E2E C6)")
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.KHOI_DONG, null, start, start + 3_000L), "không hỏi được")
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.KHOI_DONG, true, start, start - 1L), "đồng hồ lùi")
        assertFalse(AccessibilityHealGates.withinBootGrace(start, start + g + 1L))
        // Luật RUNNING: không đổi một chữ.
        assertEquals(HealStep.NONE, AccessibilityHealGates.healStep(false, true, wanted = true, userAsked = false, phase = HealPhase.RUNNING),
            "kẹt lúc ĐANG CHẠY ⇒ vẫn NONE: không bao giờ tự giết khi xe đang dùng")
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.RUNNING, true, start, start + 1L))
    }

    @Test
    fun `an han khoi dong ket thi leo cung nac va cung cong wanted nhu lop 2`() {
        assertEquals(HealStep.FORCE_STOP, AccessibilityHealGates.healStep(false, true, wanted = true, userAsked = false, phase = HealPhase.KHOI_DONG))
        assertEquals(HealStep.NONE, AccessibilityHealGates.healStep(false, true, wanted = false, userAsked = false, phase = HealPhase.KHOI_DONG),
            "R-nf5: phím-thoại TẮT ⇒ không ai lắp lại sau khi giết ⇒ không leo")
        assertEquals(HealStep.TOGGLE, AccessibilityHealGates.healStep(false, false, wanted = true, userAsked = false, phase = HealPhase.KHOI_DONG),
            "chưa gắn mà KHÔNG kẹt ⇒ nấc rẻ như cũ")
        assertEquals(HealStep.NONE, AccessibilityHealGates.healStep(true, true, wanted = true, userAsked = false, phase = HealPhase.KHOI_DONG))
    }

    @Test
    fun `hang so an han`() {
        assertTrue(AccessibilityHealGates.BOOT_GRACE_MS <= 20_000L, "phiên điều phối 02/10: ân hạn ≤ 20 s kể từ khi tiến trình bật")
        assertTrue(AccessibilityHealGates.BOOT_GRACE_MS >= 2 * AccessibilityHealGates.STUCK_CONFIRM_GAP_MS,
            "đo-chờ-đo-leo (≈ +7 s máy ảo, ≈ +8…10 s xe) phải nằm gọn trong ân hạn")
        assertTrue(AccessibilityHealGates.OWN_HEAL_WINDOW_MS > AccessibilityHealGates.BOOT_GRACE_MS,
            "tiến trình do lượt khởi động dựng lại tới SAU khi bắn — cửa sổ 'sinh từ lượt chữa' phải phủ nó")
    }

    // ─── Marker trước khi bắn ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `luot tu dong KHONG ban neu moc leo ghi hong`() {
        assertTrue(AccessibilityHealGates.autoFireAllowed(userAsked = false, markerWritten = true))
        assertFalse(AccessibilityHealGates.autoFireAllowed(userAsked = false, markerWritten = false),
            "mốc leo là chốt chống vòng lặp DUY NHẤT của ân hạn khởi động: không ghi được ⇒ tiến trình dựng lại không biết " +
                "mình sinh từ lượt chữa ⇒ leo lần nữa")
        assertTrue(AccessibilityHealGates.autoFireAllowed(userAsked = true, markerWritten = false), "bấm tay: giữ hành vi nút")
    }

    // ─── Bảng quyết định đầy đủ: pha × ân hạn × marker × kết quả đo ⇒ leo / không leo ──────────────────────────

    private data class Case(
        val why: String,
        val phase: HealPhase,
        val interactive: Boolean? = true,
        val prev: Long = PREV_PROC,
        val escalatedAt: Long = NO_HEAL,
        val scoredFor: Long = NO_HEAL,
        val claimedHere: Boolean = false,
        val firstDump: String,
        val secondDump: String,
        val firstAt: Long = START + 300L,
        val gapMs: Long = AccessibilityHealGates.STUCK_CONFIRM_GAP_MS,
        val fireAt: Long = START + 7_000L,
        val interactiveAtFire: Boolean? = true,
        val markerWritten: Boolean = true,
        val fires: Boolean,
    )

    /** Ghép các hàm thuần đúng thứ tự mã (`onBootGrace` → `healIfStuck` → `escalateIfStuck`). */
    private fun fires(c: Case): Boolean {
        if (c.phase == HealPhase.KHOI_DONG) {
            val child = AccessibilityHealGates.ownHealChild(c.claimedHere, c.escalatedAt, c.scoredFor, start)
            if (!AccessibilityHealGates.bootGraceMayRun(c.interactive, c.prev, child, start)) return false
            if (!AccessibilityHealGates.withinBootGrace(start, c.firstAt)) return false
        }
        val first = AccessibilityHealGates.observe(c.firstDump, c.firstAt, comp)
        if (!first.stuck) return false
        val second = AccessibilityHealGates.observe(c.secondDump, c.firstAt + c.gapMs, comp)
        if (!AccessibilityHealGates.stuckPersistent(first, second)) return false
        val step = AccessibilityHealGates.healStep(false, second.inBinding, wanted = true, userAsked = false, phase = c.phase)
        if (step != HealStep.FORCE_STOP) return false
        if (!AccessibilityHealGates.lifecycleFireAllowed(c.phase, c.interactiveAtFire, start, c.fireAt)) return false
        return AccessibilityHealGates.autoFireAllowed(userAsked = false, markerWritten = c.markerWritten)
    }

    @Test
    fun `bang quyet dinh pha x an han x marker x ket qua do`() {
        val k = HealPhase.KHOI_DONG
        listOf(
            Case("E2E ca 1 sau bản vá: dựng lại lúc màn sáng + KẸT BỀN ⇒ leo MỘT lần", k, firstDump = stuckDump, secondDump = stuckDump, fires = true),
            Case("cùng ca đó nhưng pha RUNNING (ngoài ân hạn / đường grant) ⇒ KHÔNG leo — luật 2.83", HealPhase.RUNNING,
                firstDump = stuckDump, secondDump = stuckDump, fires = false),
            Case("kẹt TẠM (crash: AOSP dựng lại dịch vụ) — lần hai đã gắn", k, firstDump = stuckDump, secondDump = boundDump, fires = false),
            Case("đã gắn ngay lần đầu", k, firstDump = boundDump, secondDump = boundDump, fires = false),
            Case("dump hỏng ⇒ không bao giờ giết dựa trên bản đọc hỏng", k, firstDump = "", secondDump = "", fires = false),
            Case("hai lần đo quá sát", k, firstDump = stuckDump, secondDump = stuckDump,
                gapMs = AccessibilityHealGates.STUCK_CONFIRM_GAP_MS - 1L, fires = false),
            Case("CON của lượt chữa (vừa nhận chấm mốc leo 300 ms trước) ⇒ không vòng lặp", k,
                escalatedAt = start - 300L, scoredFor = start - 300L, claimedHere = true, firstDump = stuckDump, secondDump = stuckDump, fires = false),
            Case("mốc leo 300 ms trước mà claim ghi hỏng ⇒ coi là con, không leo", k,
                escalatedAt = start - 300L, firstDump = stuckDump, secondDump = stuckDump, fires = false),
            Case("lần giết MỚI 61 s sau một lượt chữa (con đã nhận chấm) ⇒ chữa lại", k,
                escalatedAt = start - 61_000L, scoredFor = start - 61_000L, firstDump = stuckDump, secondDump = stuckDump, fires = true),
            Case("tiến trình đầu tiên của lần nổ máy", k, prev = -1L, firstDump = stuckDump, secondDump = stuckDump, fires = false),
            Case("bật lúc màn tắt ⇒ lớp 1, không phải ân hạn", k, interactive = false, firstDump = stuckDump, secondDump = stuckDump, fires = false),
            Case("đo xong thì đã quá ân hạn (xe tải nặng) ⇒ bỏ, không giết muộn", k, firstDump = stuckDump, secondDump = stuckDump,
                fireAt = start + AccessibilityHealGates.BOOT_GRACE_MS + 1L, fires = false),
            Case("lượt bắt đầu khi ân hạn đã qua", k, firstDump = stuckDump, secondDump = stuckDump,
                firstAt = start + AccessibilityHealGates.BOOT_GRACE_MS + 1L, fires = false),
            Case("màn tắt giữa lúc đo ⇒ bỏ", k, firstDump = stuckDump, secondDump = stuckDump, interactiveAtFire = false, fires = false),
            Case("mốc leo ghi HỎNG ⇒ không bắn", k, firstDump = stuckDump, secondDump = stuckDump, markerWritten = false, fires = false),
        ).forEach { c -> assertEquals(c.fires, fires(c), c.why) }
    }

    /**
     * Chuỗi sự kiện E2E máy ảo 02/10 sau bản vá — không có vòng giết–dựng–giết: lượt khởi động leo, tiến trình con (màn vẫn
     * sáng) KHÔNG leo; một lần BYD giết MỚI (kể cả trong 2 phút — [ĐO 01:59:16] 27 s sau) thì được chữa lại.
     */
    @Test
    fun `chuoi E2E ca 1 khong vong lap`() {
        val w = AccessibilityHealGates.OWN_HEAL_WINDOW_MS
        // P1 — dựng lại sau lần BYD giết lúc màn sáng (01:50:36), chưa từng leo.
        assertTrue(AccessibilityHealGates.bootGraceMayRun(true, prevProc, AccessibilityHealGates.ownHealChild(false, noHeal, noHeal, start), start))
        val leo = start + 6_000L   // `escalateIfStuck` ghi mốc leo (commit) rồi bắn — [ĐO] 01:50:42.548
        // P2 — do CHÍNH lệnh force-stop dựng lại 0,1 s sau (01:50:42.683): tiến trình đầu tiên sau lượt leo ⇒ nhận chấm.
        val p2 = leo + 135L
        val p2Claimed = AccessibilityHealGates.firstStartAfterHeal(leo, scoredFor = -1L, nowElapsed = p2, windowMs = w)
        assertTrue(p2Claimed)
        assertFalse(AccessibilityHealGates.bootGraceMayRun(true, start, AccessibilityHealGates.ownHealChild(p2Claimed, leo, leo, p2), p2),
            "P2 là con của lượt chữa ⇒ không leo lần hai trong cùng sự kiện")
        // P3 — BYD giết TIẾP 61 s sau: mốc leo vẫn trong cửa sổ nhưng P2 đã nhận ⇒ sự kiện MỚI ([ĐO 01:59:16] 27 s sau ⇒ chữa lại).
        val p3 = leo + 61_000L
        val p3Claimed = AccessibilityHealGates.firstStartAfterHeal(leo, scoredFor = leo, nowElapsed = p3, windowMs = w)
        assertFalse(p3Claimed)
        assertTrue(AccessibilityHealGates.bootGraceMayRun(true, p2, AccessibilityHealGates.ownHealChild(p3Claimed, leo, leo, p3), p3),
            "lần giết mới từ bên ngoài được chữa một lần (2.83 R-A4)")
    }

    // ─── Màn TẮT giữa lượt ân hạn ⇒ trao LỚP 1 (senior review lượt 1, E2E C6) ──────────────────────────────────

    /**
     * Khoá lỗi [ĐO E2E máy ảo 02/10 ca C6, `e2e2/logcat.txt` 02:18:42.606–02:18:54.131]: giết lúc màn bật → tiến trình mới
     * `tương tác=true` → lượt khởi động thấy KẸT → màn tắt ở +2 s (`isInteractive=false` 44.689) ⇒ `khoi-dong: pha đã qua …
     * → bỏ` ⇒ màn bật lại: `màn bật không theo sau lần tắt máy nào → không tự chữa` ⇒ `nấc NONE` (RUNNING) ⇒ phím chết tới khi
     * bấm tay (112 s). Mốc lớp 1/2 lấy nguyên văn từ dòng log đó: `tắt-máy=160813986, mở-xe trước=160841570`.
     */
    @Test
    fun `bang ca trao lop 1 khi man tat giua luot an han`() {
        val tatMay = 160_813_986L
        val moXe = 160_841_570L
        val s = 160_900_000L   // tiến trình bật (sau cả hai mốc — cùng lần nổ máy)
        val g = AccessibilityHealGates.BOOT_GRACE_MS
        data class Row(val why: String, val interactive: Boolean?, val tat: Long, val mo: Long, val now: Long, val expect: Boolean)
        listOf(
            Row("E2E C6: màn tắt +2 s, đã mở xe sau lần tắt máy trước ⇒ TRAO lớp 1", false, tatMay, moXe, s + 2_083L, true),
            Row("chưa từng có lượt tắt-máy ⇒ trao", false, -1L, -1L, s + 2_000L, true),
            Row("màn VẪN bật (cắt vì lý do khác) ⇒ không trao — không bao giờ tự giết lúc màn sáng ngoài ân hạn", true, tatMay, moXe, s + 2_000L, false),
            Row("không hỏi được isInteractive ⇒ không đoán", null, tatMay, moXe, s + 2_000L, false),
            Row("màn tắt nhưng ĐÃ quá ân hạn ⇒ luật lớp 3, không trao", false, tatMay, moXe, s + g + 1L, false),
            Row("lần tắt máy NÀY đã có lượt (claim tắt-máy sau lần mở xe, < 10 ph) ⇒ không trao lần hai", false, s - 1_000L, moXe, s + 2_000L, false),
            Row("đồng hồ lùi", false, tatMay, moXe, s - 1L, false),
        ).forEach { r ->
            assertEquals(r.expect, AccessibilityHealGates.bootGraceHandsOffToTatMay(r.interactive, s, r.tat, r.mo, r.now), r.why)
        }
    }

    /**
     * Chuỗi C6 sau bản vá — không có vòng lặp, không chạy lớp 1 HAI lần: lượt trao claim lớp 1 (`commit()` TRƯỚC khi đo) →
     * lớp 1 bắn lúc màn tắt → tiến trình con dựng lại lúc màn VẪN tắt thấy claim ⇒ không mở lượt tắt-máy nữa, và là con ⇒
     * không ân hạn; còn nếu màn bật lại giữa chừng thì claim đó làm lần màn bật này là MỞ XE ⇒ lớp 2 nhận.
     */
    @Test
    fun `chuoi C6 trao lop 1 khong vong lap va lop 2 nhan neu man bat lai`() {
        val moXe = 160_841_570L
        val s = 160_900_000L
        val cut = s + 2_083L
        assertTrue(AccessibilityHealGates.bootGraceHandsOffToTatMay(false, s, 160_813_986L, moXe, cut))
        val claim = cut   // `Prefs.setA11yTatMayAt(app, now)` — ghi TRƯỚC khi đo
        assertTrue(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.TAT_MAY, false, -1L, cut + 6_500L), "lớp 1 bắn khi màn vẫn tắt")
        assertEquals(HealStep.FORCE_STOP, AccessibilityHealGates.healStep(false, true, wanted = true, userAsked = false, phase = HealPhase.TAT_MAY))
        // Tiến trình con dựng lại lúc màn VẪN tắt, 0,3 s sau lượt bắn.
        val leo = cut + 6_500L
        val child = leo + 300L
        assertFalse(AccessibilityHealGates.tatMayMayRun(false, claim, moXe, child), "cùng lần tắt máy ⇒ lớp 1 KHÔNG chạy lần hai")
        assertFalse(AccessibilityHealGates.bootGraceMayRun(false, s, AccessibilityHealGates.ownHealChild(true, leo, leo, child), child))
        // Biến thể: màn bật lại TRƯỚC khi lớp 1 kịp bắn ⇒ lớp 1 bỏ (pha qua) ⇒ lần màn bật này là MỞ XE (claim mới hơn).
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.TAT_MAY, true, -1L, cut + 3_000L))
        assertTrue(AccessibilityHealGates.moXeFollowsTatMay(claim, moXe, cut + 3_000L), "lớp 2 nhận lượt — không còn khe hở")
    }

    @Test
    fun `ghi chu nhat ky cua an han khoi dong`() {
        assertEquals("khoi-dong", A11yBindJournal.grantNote(userAsked = false, phase = HealPhase.KHOI_DONG))
        assertEquals("grant-tay", A11yBindJournal.grantNote(userAsked = true, phase = HealPhase.KHOI_DONG))
        val line = A11yBindJournal.line("2026-10-02T00:15:03", 153_483_000L, 0L, A11yBindJournal.State.STUCK, 20703,
            A11yBindJournal.grantNote(false, HealPhase.KHOI_DONG))
        assertTrue(line.endsWith("note=khoi-dong"), "ghi chú phải sống qua bộ lọc của dòng nhật ký: $line")
    }

    private companion object {
        /** Mốc E2E ca 1: máy chạy ~153 488 s (`up=153488s` của dòng nhật ký 00:15:06), tiến trình mới bật lúc này. */
        const val START = 153_476_000L
        const val PREV_PROC = 150_000_000L
        const val NO_HEAL = -1L
    }
}
