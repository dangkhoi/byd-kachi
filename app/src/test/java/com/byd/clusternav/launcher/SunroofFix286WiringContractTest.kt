package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · R-SR — khoá ĐƯỜNG DÂY phía `:app` của bản vá cửa sổ trời (CLAUDE.md §8) ═══════════════════════════
 *
 * Hành vi thuần (chuỗi 100 → 255, cổng có-nóc, dòng nhật ký) khoá ở `:core` (`SunroofFix286Test`). Bài này canh
 * những chỗ mà bài thuần KHÔNG thấy: mặc định của `HalBindingTable` là *"không hẹn, không ghi"* (để bài kiểm tất
 * định), nên quên tiêm ở đồ thị tiến trình là nút nóc **lại gửi 100 mà không nhả** và **không để lại dòng nào** —
 * compile xanh, test thuần xanh, chỉ xe thấy. Đúng họ `CastShell.evictVd` mà §8 sinh ra để chặn.
 */
class SunroofFix286WiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val container by lazy { code("src/main/java/com/byd/clusternav/AppContainer.kt") }

    @Test
    fun `do thi tien trinh tiem bo hen THAT va nhat ky THAT vao bang noi HAL`() {
        val build = SourceRoots.body(container, "private fun build(")
        assertTrue(build.contains("releaseSchedulerInit = {"), "build() phải tiêm bộ hẹn nhả")
        assertTrue(build.contains("WriteReleaseScheduler.Jvm("), "bộ hẹn THẬT (không phải NONE) — thiếu ⇒ nóc không nhả 255")
        assertTrue(build.contains("ctlJournalInit = { CtlJournalStore.journal(app) }"), "nhật ký `ctl` THẬT")
        assertTrue(
            container.contains("HalBindingTable(carGatewayInit(), releaseSchedulerInit(), ctlJournalInit())"),
            "bảng nối HAL dùng chung phải nhận cả hai — một bảng, mọi bề mặt (ô · giọng nói · gói · cầu ctl)",
        )
        assertEquals(1, Regex("""HalBindingTable\(""").findAll(container).count(), "một bảng nối HAL cho cả tiến trình")
    }

    @Test
    fun `nhat ky ctl ra logcat TRUOC roi moi xuong tep, tep qua lop vong chung`() {
        val store = code("src/main/java/com/byd/clusternav/launcher/CtlJournalStore.kt")
        val record = SourceRoots.body(store, "private fun record(")
        val logAt = record.indexOf("Log.i(TAG, full)")
        val fileAt = record.indexOf("ring.append(app, full)")
        assertTrue(logAt >= 0 && fileAt > logAt, "logcat (⇒ usage-*.log) phải đi trước tệp: ghi tệp hỏng thì dòng vẫn còn")
        assertTrue(record.contains("MacroExec.submitSerial("), "phần tệp xuống làn tuần tự dùng chung — không chặn lệnh ghi xe")
        assertTrue(store.contains("DiagRingFile(NAME, CtlWriteJournal.MAX_LINES, TAG)"), "tệp vòng qua lớp chung (DRY)")
    }

    @Test
    fun `nhat ky ganh Ho tro uy quyen xuong lop vong chung, khong con khoa rieng`() {
        val a11y = code("src/main/java/com/byd/clusternav/modules/navaccess/A11yBindJournalStore.kt")
        assertTrue(a11y.contains("DiagRingFile(NAME, A11yBindJournal.MAX_LINES, TAG)"))
        assertTrue(a11y.contains("\"a11y-bind.log\""), "đường dẫn tệp KHÔNG đổi")
        assertFalse(a11y.contains("writeText("), "đọc-sửa-ghi chỉ ở một chỗ (DiagRingFile) — không bản sao thứ hai")
        val ring = code("src/main/java/com/byd/clusternav/launcher/DiagRingFile.kt")
        assertTrue(ring.contains("channel.lock()"), "khoá LIÊN tiến trình: `:wake` cũng ghi lệnh xe")
        assertTrue(ring.contains("joinToString(\"\\n\", postfix = \"\\n\")"), "định dạng tệp giữ byte như 2.83")
    }

    @Test
    fun `cau ctllog di cua khong-can-man-chinh va CHI DOC`() {
        val noHome = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeNoHome.kt")
        assertTrue(
            SourceRoots.body(noHome, "fun handle(").contains("TestBridgeCommands.CTLLOG -> TestBridgeCtlLog.run(app, cmd, reply)"),
        )
        val run = SourceRoots.body(code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeCtlLog.kt"), "fun run(")
        assertTrue(run.contains("CtlJournalStore.read(app)"), "đọc qua đúng hàm có khoá")
        listOf("append(", "record(", "edit()", "Prefs.set").forEach {
            assertFalse(run.contains(it), "ctllog CHỈ ĐỌC — thấy `$it`")
        }
    }

    @Test
    fun `tap hoi xac nhan doc qua tap HIEU LUC, ghi kem moc, tra ve mac dinh khi rong`() {
        val prefs = code("src/main/java/com/byd/clusternav/PrefsVoiceV3.kt")
        assertTrue(SourceRoots.body(prefs, "fun Prefs.voiceConfirmIds(").contains("VoiceRiskTable.effectiveIds("))
        val set = SourceRoots.body(prefs, "fun Prefs.setVoiceConfirmIds(")
        assertTrue(set.contains("K_VOICE_CONFIRM_IDS") && set.contains("K_VOICE_CONFIRM_CHOSEN"),
            "tập + mốc trong CÙNG một edit() — thiếu mốc thì bỏ tích nóc bị cộng lại ở lượt đọc sau")
        val bridgeSet = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgePrefsSet.kt")
        assertTrue(bridgeSet.contains("if (ids.isEmpty()) Prefs.resetVoiceConfirmIds(app) else Prefs.setVoiceConfirmIds(app, ids)"),
            "`trap` của harness phải trả về MẶC ĐỊNH, không phải một tập rỗng tự đặt")
    }
}
