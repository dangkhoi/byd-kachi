package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.BindObservation
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.HealPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá lớp 1 (TẮT MÁY) + lớp 2 (MỞ XE) của 2.83 — owner chốt 2026-09-29.
 *
 * Bài học hiện trường [ĐO xe 29/09, tái hiện 2 lần từ trạng thái sạch]: mỗi lần tắt máy BYD giết Kachi KHÔNG kèm
 * `PACKAGE_RESTARTED` ⇒ dịch vụ Hỗ trợ kẹt trong `Binding services`; Android dựng lại Kachi 0,3 s sau lúc màn đã tắt.
 * Chữa lúc đó (hoặc vài giây đầu sau khi mở xe) là miễn phí; chữa lúc đang dùng xe thì không.
 *
 * Fixture NGUYÊN VĂN từ xe 29/09 (Kachi 2.82/183), đã làm sạch: chỉ giữ phần đầu `dumpsys accessibility` (khối User
 * state), bỏ danh sách `Window[…]` (có tên app người dùng đang mở). `k1` = lúc KẸT; `fix2` = ngay sau khi chữa, khối
 * Bound in MỖI dịch vụ một DÒNG riêng (bẫy đo ghi trong spec — parser phải đọc trọn khối, không đọc theo dòng).
 */
class A11yLifecycleGatesTest {

    private val comp = "com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService"

    private fun fixture(name: String): String {
        val text = javaClass.getResourceAsStream("/diagnostics/a11y-0929/$name")?.bufferedReader()?.readText()
        assertNotNull(text, "thiếu fixture $name — bài test đang quét thứ không tồn tại")
        return text!!
    }

    private val stuckDump by lazy { fixture("k1-acc-stuck.txt") }
    private val boundDump by lazy { fixture("fix2-acc-bound.txt") }

    // ─── Đọc một bản dump bằng đúng hai parser đang chạy ngoài hiện trường ─────────────────────────────────

    @Test
    fun `fixture that luc KET doc ra dung la ket`() {
        val o = AccessibilityHealGates.observe(stuckDump, 1_000L, comp)
        assertFalse(o.bound, "k1: khối Bound chỉ có StatusBar của hãng")
        assertTrue(o.inBinding, "k1: mình nằm trong Binding services")
        assertTrue(o.stuck)
    }

    @Test
    fun `fixture that sau khi chua doc ra DA GAN du khoi Bound in nhieu dong`() {
        assertTrue(boundDump.lines().count { it.contains("Service[label=") } >= 2, "fixture phải giữ khối Bound NHIỀU dòng")
        val o = AccessibilityHealGates.observe(boundDump, 1_000L, comp)
        assertTrue(o.bound, "fix2: dòng THỨ HAI của khối Bound là 'ClusterNav — booster…' ⇒ phải đọc là đã gắn")
        assertFalse(o.inBinding, "fix2: Binding services rỗng")
        assertFalse(o.stuck)
    }

    @Test
    fun `dump hong thi KHONG bao gio la ket`() {
        listOf(null, "", "rác không phải dumpsys").forEach { d ->
            assertFalse(AccessibilityHealGates.observe(d, 1_000L, comp).stuck, "dump '$d' ⇒ không kẹt ⇒ không giết launcher")
        }
    }

    // ─── Kẹt BỀN: hai quan sát cách ≥ 5 s ────────────────────────────────────────────────────────────────

    @Test
    fun `hai lan ket cach du 5 s moi la ket BEN`() {
        val a = AccessibilityHealGates.observe(stuckDump, 10_000L, comp)
        val b = AccessibilityHealGates.observe(stuckDump, 10_000L + AccessibilityHealGates.STUCK_CONFIRM_GAP_MS, comp)
        assertTrue(AccessibilityHealGates.stuckPersistent(a, b))
    }

    @Test
    fun `hai lan ket qua sat nhau thi CHUA du`() {
        val a = AccessibilityHealGates.observe(stuckDump, 10_000L, comp)
        val b = AccessibilityHealGates.observe(stuckDump, 10_000L + AccessibilityHealGates.STUCK_CONFIRM_GAP_MS - 1, comp)
        assertFalse(AccessibilityHealGates.stuckPersistent(a, b),
            "một ảnh chụp 'Binding' có thể là lúc hệ ĐANG gắn bình thường (ngay sau force-stop, lệnh tách rời lắp lại " +
                "sau 4 s) — phải chờ đủ khoảng")
    }

    @Test
    fun `lan hai da gan thi Binding luc dau chi la TAM THOI`() {
        val a = AccessibilityHealGates.observe(stuckDump, 10_000L, comp)
        val b = AccessibilityHealGates.observe(boundDump, 20_000L, comp)
        assertFalse(AccessibilityHealGates.stuckPersistent(a, b), "lần đo sau đã gắn ⇒ KHÔNG leo")
    }

    @Test
    fun `dong ho lui khong duoc coi la du khoang`() {
        val a = BindObservation(20_000L, bound = false, inBinding = true)
        val b = BindObservation(10_000L, bound = false, inBinding = true)
        assertFalse(AccessibilityHealGates.stuckPersistent(a, b))
    }

    // ─── Lớp 1: một lượt mỗi lần tắt máy ─────────────────────────────────────────────────────────────────

    @Test
    fun `lop 1 chi chay khi CHAC man tat`() {
        assertTrue(AccessibilityHealGates.tatMayMayRun(false, -1L, -1L, 60_000L), "màn tắt, chưa từng claim ⇒ chạy")
        assertFalse(AccessibilityHealGates.tatMayMayRun(true, -1L, -1L, 60_000L), "màn đang bật ⇒ không phải tắt máy")
        assertFalse(AccessibilityHealGates.tatMayMayRun(null, -1L, -1L, 60_000L), "không hỏi được ⇒ KHÔNG đoán màn tắt")
    }

    /**
     * Chuỗi sự kiện THẬT của một vòng tắt-mở (mốc giống c2 29/09): BYD giết → dựng lại (màn tắt) → lớp 1 claim +
     * bắn force-stop → Android dựng lại LẦN NỮA (màn vẫn tắt) → phải BỎ. Mở xe → lớp 2 claim. Tắt máy lần sau → lớp 1
     * được chạy lại.
     */
    @Test
    fun `chong vong lap lop 1 qua mot vong tat mo that`() {
        val boot = 70_000_000L
        // 1) BYD giết lúc tắt máy, Android dựng lại Kachi (màn tắt).
        assertTrue(AccessibilityHealGates.tatMayMayRun(false, lastTatMayAt = -1L, lastMoXeAt = -1L, nowElapsed = boot))
        val claim = boot   // tiến trình ghi claim commit() TRƯỚC khi đo
        // 2) Lượt force-stop của CHÍNH lớp 1 (~7 s sau) làm Android dựng lại Kachi, màn vẫn tắt.
        assertFalse(AccessibilityHealGates.tatMayMayRun(false, claim, -1L, boot + 7_000L),
            "tiến trình do chính mình dựng lại KHÔNG được mở lượt thứ hai cho cùng lần tắt máy — vòng lặp giết launcher")
        // 3) Mở xe 50 s sau: lớp 2 claim mốc màn bật.
        val screenOn = boot + 50_000L
        assertTrue(AccessibilityHealGates.moXeFresh(screenOn, lastMoXeAt = -1L, nowElapsed = screenOn + 100L))
        // 4) Tắt máy lần sau (3 phút sau) — đã có lần mở xe xen giữa ⇒ lần tắt máy MỚI.
        assertTrue(AccessibilityHealGates.tatMayMayRun(false, claim, screenOn, boot + 180_000L),
            "đã có một lần mở xe sau claim ⇒ đây là lần tắt máy khác, phải được chữa")
    }

    @Test
    fun `lo mat lan mo xe thi cong tu nha sau cua so, khong khoa vinh vien`() {
        val claim = 1_000_000L
        assertFalse(AccessibilityHealGates.tatMayMayRun(false, claim, -1L, claim + AccessibilityHealGates.TAT_MAY_SAME_EVENT_MS - 1))
        assertTrue(AccessibilityHealGates.tatMayMayRun(false, claim, -1L, claim + AccessibilityHealGates.TAT_MAY_SAME_EVENT_MS),
            "CLAUDE.md §3: không gate đường phục hồi bằng dữ liệu chỉ chính nó làm mới được — bộ thu có thể lỡ màn bật")
    }

    @Test
    fun `claim cua doi may truoc khong chan lop 1`() {
        assertTrue(AccessibilityHealGates.tatMayMayRun(false, lastTatMayAt = 9_000_000L, lastMoXeAt = -1L, nowElapsed = 30_000L),
            "mốc lớn hơn đồng hồ ⇒ đã reboot ⇒ claim cũ vô hiệu")
    }

    @Test
    fun `mo xe cua doi may truoc khong duoc tinh la mo xe sau claim`() {
        val claim = 20_000L
        assertFalse(AccessibilityHealGates.tatMayMayRun(false, claim, lastMoXeAt = 9_000_000L, nowElapsed = 30_000L),
            "mốc mở xe lớn hơn 'bây giờ' là của đời máy trước ⇒ không nhả cổng")
    }

    // ─── Lớp 2: một lượt mỗi lần màn bật, trong ân hạn ──────────────────────────────────────────────────

    @Test
    fun `cung mot lan man bat chi mot luot`() {
        val on = 500_000L
        assertTrue(AccessibilityHealGates.moXeFresh(on, lastMoXeAt = 100_000L, nowElapsed = on + 50L), "lần mở xe trước ⇒ sự kiện mới")
        assertFalse(AccessibilityHealGates.moXeFresh(on, lastMoXeAt = on, nowElapsed = on + 50L), "đã claim đúng mốc này ⇒ bỏ")
        assertTrue(AccessibilityHealGates.moXeFresh(on, lastMoXeAt = 9_000_000L, nowElapsed = on + 50L), "claim đời máy trước ⇒ sự kiện mới")
    }

    @Test
    fun `an han mo xe toi da 15 s va la hang co ten`() {
        assertTrue(AccessibilityHealGates.MO_XE_GRACE_MS in 1L..15_000L, "owner: ân hạn ≤ 15 s")
        val on = 500_000L
        assertTrue(AccessibilityHealGates.withinMoXeGrace(on, on))
        assertTrue(AccessibilityHealGates.withinMoXeGrace(on, on + AccessibilityHealGates.MO_XE_GRACE_MS))
        assertFalse(AccessibilityHealGates.withinMoXeGrace(on, on + AccessibilityHealGates.MO_XE_GRACE_MS + 1),
            "ân hạn là TRẦN để lượt tự giết không rơi muộn lúc người lái đã dùng xe ([ĐO c2] app vào ô ở ≈ +7,6 s, " +
                "\"requested HOME up\" +11,7 s) — quá trần thì để nút lo")
        assertFalse(AccessibilityHealGates.withinMoXeGrace(on, on - 1), "đồng hồ lùi ⇒ không tin")
    }

    /** Mốc trong ngày (ms) — dùng làm `elapsedRealtime` giả để mốc test đọc thẳng ra được giờ trên logcat xe. */
    private fun hms(h: Int, m: Int, s: Int, ms: Int) = ((h * 60L + m) * 60L + s) * 1_000L + ms

    /**
     * Khoá lượt review 2 (2.83): lớp 2 CHỈ chữa khi lần màn bật đi SAU một lần tắt máy — [ĐO xe 29/09] cả c1
     * (`am_kill` 11:20:50.165 → `am_proc_start` 11:20:50.479, màn tắt từ 11:20:49.948) lẫn c2 (`am_kill` 11:33:25.182
     * → `am_proc_start` 11:33:25.515, màn tắt từ 11:33:24.475) đều dựng lại Kachi lúc màn ĐÃ tắt ⇒ lớp 1 luôn claim.
     * Tắt/bật màn GIỮA LÚC LÁI thì không có lần khởi động nào ⇒ không claim ⇒ KHÔNG được force-stop launcher
     * (owner: lớp 3 không tự chữa; ô đang có app ⇒ mảng đen phủ nhà).
     */
    @Test
    fun `lop 2 chi chua khi man bat di SAU mot lan tat may`() {
        val c1Start = hms(11, 20, 50, 479)
        val c1On = hms(11, 21, 58, 992)
        assertTrue(AccessibilityHealGates.moXeFollowsTatMay(lastTatMayAt = c1Start, prevMoXeAt = -1L, nowElapsed = c1On + 100L),
            "c1: Kachi dựng lại lúc màn tắt (claim lớp 1) rồi mới mở xe ⇒ đúng là mở xe")
        val midDrive = hms(11, 25, 0, 0)
        assertFalse(AccessibilityHealGates.moXeFollowsTatMay(c1Start, prevMoXeAt = c1On, nowElapsed = midDrive + 100L),
            "tắt/bật màn lúc đang lái: không có lần khởi động-lúc-màn-tắt nào kể từ lần mở xe trước ⇒ lớp 3, KHÔNG tự giết")
        val c2Start = hms(11, 33, 25, 515)
        val c2On = hms(11, 34, 14, 236)
        assertTrue(AccessibilityHealGates.moXeFollowsTatMay(c2Start, prevMoXeAt = midDrive, nowElapsed = c2On + 100L),
            "c2: lần tắt máy MỚI sau lần bật màn giữa đường ⇒ cổng mở lại, không khoá vĩnh viễn")
    }

    @Test
    fun `lop 2 doc moc doi may truoc dung nghia`() {
        val now = 60_000L
        assertFalse(AccessibilityHealGates.moXeFollowsTatMay(lastTatMayAt = -1L, prevMoXeAt = -1L, nowElapsed = now),
            "chưa từng có lần tắt máy nào ⇒ không phải mở xe")
        assertFalse(AccessibilityHealGates.moXeFollowsTatMay(lastTatMayAt = 9_000_000L, prevMoXeAt = -1L, nowElapsed = now),
            "claim tắt-máy của đời máy trước (lớn hơn đồng hồ) không tính")
        assertTrue(AccessibilityHealGates.moXeFollowsTatMay(lastTatMayAt = 30_000L, prevMoXeAt = 9_000_000L, nowElapsed = now),
            "mốc mở-xe của đời máy trước không che claim tắt-máy của đời này")
        assertFalse(AccessibilityHealGates.moXeFollowsTatMay(lastTatMayAt = 30_000L, prevMoXeAt = 30_000L, nowElapsed = now),
            "bằng nhau ⇒ không có lần tắt máy nào MỚI HƠN lần mở xe trước")
    }

    @Test
    fun `khoang cho de xac nhan ket BEN phai nam gon trong an han`() {
        assertTrue(AccessibilityHealGates.STUCK_CONFIRM_GAP_MS < AccessibilityHealGates.MO_XE_GRACE_MS,
            "đo hai lần cách nhau lâu hơn cả ân hạn thì lớp 2 không bao giờ bắn được")
        assertTrue(AccessibilityHealGates.STUCK_CONFIRM_GAP_MS >= 4_000L + 1_000L,
            "phải dài hơn khoảng lắp-lại 4 s của lệnh tách rời, nếu không lượt đo ngay sau chữa dễ bắt nhầm Binding tạm")
    }

    // ─── Cổng cuối ngay trước khi bắn ────────────────────────────────────────────────────────────────────

    @Test
    fun `cong cuoi hoi lai pha ngay truoc khi ban`() {
        val on = 500_000L
        assertTrue(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.TAT_MAY, false, -1L, on))
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.TAT_MAY, true, -1L, on),
            "máy ngủ giữa hai lần đo rồi thức đúng lúc người lái mở xe ⇒ lượt tắt-máy phải bỏ")
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.TAT_MAY, null, -1L, on), "không hỏi được ⇒ không bắn")
        assertTrue(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.MO_XE, true, on, on + 7_000L))
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.MO_XE, true, on, on + 11_000L), "quá ân hạn")
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.MO_XE, false, on, on + 3_000L), "màn lại tắt")
        assertFalse(AccessibilityHealGates.lifecycleFireAllowed(HealPhase.RUNNING, true, on, on + 1_000L),
            "lớp 3 tự động không bao giờ được bắn")
    }

    // ─── Chấm điểm + ghi chú nhật ký ─────────────────────────────────────────────────────────────────────

    @Test
    fun `nhan ra tien trinh do chinh luot chua dung lai`() {
        assertTrue(AccessibilityHealGates.justHealed(escalatedAt = 100_000L, nowElapsed = 110_000L, windowMs = 120_000L))
        assertFalse(AccessibilityHealGates.justHealed(100_000L, 300_000L, 120_000L), "quá cửa sổ ⇒ lần khởi động khác")
        assertFalse(AccessibilityHealGates.justHealed(-1L, 110_000L, 120_000L), "chưa từng leo")
        assertFalse(AccessibilityHealGates.justHealed(9_000_000L, 110_000L, 120_000L), "mốc đời máy trước")
    }

    /**
     * Khoá lỗi [ĐO máy ảo 29/09]: lượt tắt-máy leo lúc elapsed 791 788 ms, tiến trình dựng lại chấm `sau-chua-ON`;
     * một lần giết kiểu BYD khác dựng tiến trình mới lúc 887 000 ms (95 s sau, vẫn trong cửa sổ 2 phút) và chấm oan
     * `sau-chua-VAN-TAT` cho CÙNG mốc leo đó.
     */
    @Test
    fun `chi tien trinh dau tien sau luot leo duoc cham diem`() {
        val leo = 791_788L
        val cuaSo = 120_000L
        assertTrue(AccessibilityHealGates.firstStartAfterHeal(leo, scoredFor = -1L, nowElapsed = 791_900L, windowMs = cuaSo),
            "tiến trình đầu tiên sau lượt leo ⇒ chấm")
        assertFalse(AccessibilityHealGates.firstStartAfterHeal(leo, scoredFor = leo, nowElapsed = 887_000L, windowMs = cuaSo),
            "mốc leo này đã có tiến trình nhận chấm ⇒ lần khởi động sau (BYD giết tiếp) KHÔNG chấm lại")
        assertTrue(AccessibilityHealGates.firstStartAfterHeal(900_000L, scoredFor = leo, nowElapsed = 900_050L, windowMs = cuaSo),
            "lượt leo MỚI (mốc khác) ⇒ chấm lại được")
        assertFalse(AccessibilityHealGates.firstStartAfterHeal(leo, scoredFor = -1L, nowElapsed = leo + cuaSo + 1L, windowMs = cuaSo),
            "quá cửa sổ ⇒ vẫn không chấm (giữ luật justHealed)")
        assertFalse(AccessibilityHealGates.firstStartAfterHeal(-1L, scoredFor = -1L, nowElapsed = 110_000L, windowMs = cuaSo),
            "chưa từng leo ⇒ không chấm dù chưa ai nhận")
    }

    @Test
    fun `ghi chu nhat ky noi dung lop`() {
        assertEquals("tat-may", A11yBindJournal.grantNote(userAsked = false, phase = HealPhase.TAT_MAY))
        assertEquals("mo-xe", A11yBindJournal.grantNote(userAsked = false, phase = HealPhase.MO_XE))
        assertEquals("grant-tu-dong", A11yBindJournal.grantNote(userAsked = false, phase = HealPhase.RUNNING))
        assertEquals("grant-tay", A11yBindJournal.grantNote(userAsked = true, phase = HealPhase.TAT_MAY), "bấm tay luôn ghi là tay")
        assertEquals("sau-chua-ON", A11yBindJournal.afterHealNote(true))
        assertEquals("sau-chua-VAN-TAT", A11yBindJournal.afterHealNote(false))
        val line = A11yBindJournal.line("2026-09-29T11:33:30", 70_000_000L, 1_000_000L, A11yBindJournal.State.STUCK, 29552,
            A11yBindJournal.grantNote(false, HealPhase.TAT_MAY))
        assertTrue(line.endsWith("note=tat-may"), "ghi chú phải sống sót qua bộ lọc 40 ký tự của dòng nhật ký: $line")
    }
}
