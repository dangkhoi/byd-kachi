package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.83 · NHẬT KÝ GẮN HỖ TRỢ PHẢI ĐỌC ĐƯỢC TRÊN BẢN PHÁT HÀNH — khoá ĐƯỜNG DÂY ═══════════════════════════════
 *
 * Bài học [ĐO xe 29/09]: `A11yBindStuckWiringContractTest` canh rằng màn Chẩn đoán gọi `A11yBindJournalStore.read`
 * — xanh suốt, trong khi trên xe owner (bản phát hành) màn đó KHÔNG MỞ ĐƯỢC (nút gỡ 21/09, `exported=false` ⇒
 * `am start` bị từ chối; không debuggable ⇒ không `run-as`). Tức "có call site" mà không ai tới được call site đó
 * — một biến thể của CLAUDE.md §8. Bài này canh hai đường đọc còn sống trên bản phát hành, từ đầu tới đích:
 *
 *  1. cầu kiểm thử `a11ylog`: bảng lệnh `:core` → cửa không-cần-màn-chính → thân lệnh → đúng hàm đọc tệp + đúng
 *     getter prefs, và **chỉ đọc**;
 *  2. logcat: MỌI dòng vừa ghi ra `Log.i` (không gác theo đổi trạng thái), ở ĐÚNG tiến trình mà usage log chụp.
 */
class A11yLogBridgeWiringContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher/testbridge"
    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val noHome by lazy { code("$dir/TestBridgeNoHome.kt") }
    private val bridge by lazy { code("$dir/KachiTestBridge.kt") }
    private val a11yLog by lazy { code("$dir/TestBridgeA11yLog.kt") }
    private val store by lazy { code("src/main/java/com/byd/clusternav/modules/navaccess/A11yBindJournalStore.kt") }

    // ── (1) cầu kiểm thử `a11ylog` ─────────────────────────────────────────────────────────────

    @Test
    fun `a11ylog di cua KHONG can man chinh`() {
        val handle = SourceRoots.body(noHome, "fun handle(")
        assertTrue(
            handle.contains("TestBridgeCommands.A11YLOG -> TestBridgeA11yLog.run(app, cmd, reply)"),
            "thiếu nhánh ⇒ lệnh rơi xuống cửa cần màn chính: ngay sau khi tắt máy giết launcher [ĐO 29/09] nó trả " +
                "`home_not_running` — đúng lúc cần đọc nhất",
        )
    }

    /**
     * Cửa không-cần-màn-chính chỉ được gọi từ `dispatch`, và `dispatch` chỉ chạy SAU cổng công tắc kiểm thử
     * (`TestBridgeSafetyContractTest.moi lenh deu nam sau cong cong tac`). Gọi nó ở chỗ thứ hai là một lệnh đọc
     * nhật ký chạy được khi chế độ kiểm thử đang TẮT.
     */
    @Test
    fun `cua khong-can-man-chinh chi co mot cho goi, truoc khi doi hooks`() {
        val dispatch = SourceRoots.body(bridge, "private fun dispatch(")
        val handleAt = dispatch.indexOf("TestBridgeNoHome.handle(app, cmd, reply)")
        val hooksAt = dispatch.indexOf("KachiTestHooks.get()")
        assertTrue(handleAt >= 0, "dispatch phải hỏi cửa không-cần-màn-chính")
        assertTrue(hooksAt > handleAt, "phải hỏi TRƯỚC khi đòi màn chính, nếu không a11ylog chết theo launcher")
        assertEquals(
            1, Regex("""TestBridgeNoHome\.handle\(""").findAll(bridge).count(),
            "một cửa điều phối duy nhất",
        )
    }

    @Test
    fun `than a11ylog doc DUNG ham doc tep va DUNG getter prefs cua thang chua`() {
        val run = SourceRoots.body(a11yLog, "fun run(")
        listOf(
            "A11yBindJournalStore.read(app)" to "đọc qua đúng hàm có khoá — không mở tệp lần hai bằng tay",
            "A11yBindJournal.trim(all, cmd.tail)" to "cắt đúng số dòng ĐÃ KẸP ở :core (không đọc `cmd.slot`)",
            "Prefs.a11yEscalatedAt(app)" to "mốc force-stop — getter mà cổng chữa dùng",
            "Prefs.lastDeepSleepMs(app)" to "mốc ngủ sâu — getter mà watchdog dùng",
            "AccessibilityHealGates.escalatedThisBoot(" to "diễn giải mốc bằng CHÍNH hàm của cổng, không suy lại",
            "\"a11y_forcestop_elapsed\" to" to "tên trường owner yêu cầu",
            "\"a11y_deep_sleep_ms\" to" to "tên trường owner yêu cầu",
            "\"lines\" to" to "N dòng cuối",
            "\"a11y_tat_may_elapsed\" to Prefs.a11yTatMayAt(app)" to "claim lớp 1 (2.83) — chốt trên xe lượt tắt-máy có chạy",
            "\"a11y_mo_xe_elapsed\" to Prefs.a11yMoXeAt(app)" to "claim lớp 2 (2.83) — chốt trên xe lượt mở-xe có chạy",
            // R-C1 của spec 2.83 đòi cả pid + mốc sinh tiến trình (lượt review 3): 2.83 đi OTA không qua buổi xe, nên
            // bằng chứng "lớp 1 có chạy trong ĐÚNG tiến trình dựng lại lúc tắt máy" chỉ còn đọc được qua lệnh này.
            "\"pid\" to Process.myPid()" to "khớp cột `pid=` của dòng nhật ký",
            "\"proc_start_elapsed_ms\" to Process.getStartElapsedRealtime()" to
                "so với `a11y_tat_may_elapsed` ⇒ tiến trình này có phải cái sinh lúc tắt máy không",
        ).forEach { (token, why) -> assertTrue(run.contains(token), "a11ylog thiếu `$token` — $why") }
    }

    /** Lệnh ĐỌC mà ghi — kể cả ghi "vô hại" — là đo thứ mình vừa sửa, và mở một đường ghi qua receiver exported. */
    @Test
    fun `a11ylog CHI DOC`() {
        listOf(".record(", "Prefs.set", ".edit(", "commit()", "apply()", "writeText(", "delete(", "autoConfirm")
            .forEach { token -> assertFalse(a11yLog.contains(token), "TestBridgeA11yLog không được có `$token`") }
    }

    // ── (2) logcat ─────────────────────────────────────────────────────────────────────────────

    /**
     * ⚠ 2.86 · FIX286 SR-T7 — phần đọc/khoá/ghi tệp tách sang `DiagRingFile` (lớp vòng chung, khi `ctl-writes.log` cần
     * đúng phần ấy), nên `recordLocked` không còn. Bất biến GIỮ NGUYÊN và nay canh qua HAI chặng thay vì một hàm:
     * (1) thân `record` ra logcat rồi mới trả dòng cho lớp vòng; (2) lớp vòng chỉ `writeText` SAU khi đã nhận dòng
     * (tức sau logcat). Chặt hơn bài cũ ở chặng (2): khoá luôn rằng không có dòng nào được ghi tệp mà không qua `next`.
     */
    @Test
    fun `MOI dong ghi deu ra logcat, ke ca nhip tim, truoc khi ghi tep`() {
        val record = SourceRoots.body(store, "fun record(")
        val logAt = record.indexOf("Log.i(TAG, A11yBindJournal.logcatLine(prev, line))")
        assertTrue(logAt >= 0, "mỗi dòng vừa dựng phải ra logcat qua `A11yBindJournal.logcatLine` (nguyên văn dòng tệp)")
        assertTrue(record.contains("ring.appendIf(ctx)"), "ghi tệp qua đúng lớp vòng chung (một chỗ đọc-sửa-ghi)")
        assertTrue(record.indexOf("            line", logAt) > logAt, "dòng chỉ được trả cho lớp vòng SAU khi đã ra logcat")
        val append = SourceRoots.body(code("src/main/java/com/byd/clusternav/launcher/DiagRingFile.kt"), "fun appendIf(")
        val nextAt = append.indexOf("next(lines, f)")
        val writeAt = append.indexOf("f.writeText(")
        assertTrue(nextAt >= 0 && writeAt > nextAt, "ra logcat TRƯỚC khi ghi tệp: ghi tệp hỏng thì usage log vẫn còn dòng này")
        assertEquals(1, Regex("""writeText\(""").findAll(append).count(), "một chỗ ghi tệp duy nhất, sau `next`")
        assertFalse(
            Regex("""if\s*\(\s*prev\s*!=\s*state\s*\)""").containsMatchIn(record),
            "gác logcat theo đổi trạng thái là mất nhịp tim — đúng dạng log 29/09 (chỉ có dòng ĐỔI)",
        )
        assertTrue(store.contains("TAG = \"A11yJournal\""), "tag cố định để grep usage log")
    }

    /**
     * Usage log chụp `logcat --pid=<pid của tiến trình chính>` (`KachiLog.startCapture`, gọi ở màn chính). Dòng nhật
     * ký chỉ tới được đó nếu watchdog ghi nó cũng ở tiến trình CHÍNH — dời `VoiceKeyKeepAliveService` sang một
     * `android:process` riêng (như `:wake`/`:tts`) là usage log câm lặng mất đường đọc này.
     */
    @Test
    fun `nguoi ghi nhat ky o cung tien trinh voi usage log`() {
        val manifest = SourceRoots.text("src/main/AndroidManifest.xml")
        val at = manifest.indexOf("android:name=\".VoiceKeyKeepAliveService\"")
        assertTrue(at >= 0, "không thấy khai báo VoiceKeyKeepAliveService")
        val end = manifest.indexOf("</service>", at)
        assertTrue(end > at, "khối <service> của VoiceKeyKeepAliveService không đóng — bài đang quét vùng không tồn tại")
        assertFalse(manifest.substring(at, end).contains("android:process"), "watchdog ghi nhật ký phải ở tiến trình chính")
        val log = code("src/main/java/com/byd/clusternav/launcher/KachiLog.kt")
        assertTrue(log.contains("\"--pid=\${Process.myPid()}\""), "usage log chụp theo pid tiến trình chính")
        val home = code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        assertTrue(home.contains("KachiLog.startCapture(this)"), "không ai bật usage log thì đường đọc (2) không tồn tại")
    }
}
