package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.99 R7 KACHILOG-CAPTURE-SILENT-STOP — khoá hai lỗ của tệp `usage-*.log` ════════════════════════════════════════════
 *
 * [ĐO xe 10/10, Seal 2.98] tiến trình chính pid 7374 còn sống (logcat `KachiCtl( 7374)` 18:27:44) mà
 * `usage-1791618956803.log` dừng ở dòng cuối dưới (17:44:13), hai lượt pull cùng 62 201 byte; phiên giọng nói gọi bằng từ
 * đánh thức chạy ở pid 7453 (`:wake`) — chưa từng có tệp usage. Phân tích: `docs/diagnostics/voice-299-r3-r7-2026-10-10.md` §R7.
 */
class KachiLogResumeTest {

    /** Dòng CUỐI CÙNG của `usage-1791618956803.log` (xe 10/10) — nguyên văn. */
    private val lastCarLine =
        "10-10 17:44:13.811 D/ViewRootImpl[KachiHome]( 7374): debug_draw draw for:DecorView@54d8976[KachiHome] of com.byd.launcher   [+1 lặp]"

    @Test fun `lan noi lai doc tu moc dong cuoi da ghi, lan dau giu y lenh cu`() {
        assertEquals("10-10 17:44:13.811", KachiLog.stampOf(lastCarLine))
        assertEquals(listOf("logcat", "-v", "time", "--pid=7374"), KachiLog.captureCommand(7374, null).toList())
        assertEquals(
            listOf("logcat", "-v", "time", "-T", "10-10 17:44:13.811", "--pid=7374"),
            KachiLog.captureCommand(7374, KachiLog.stampOf(lastCarLine)).toList(),
        )
        // Dòng phụ (vết gọi) và dòng mốc của logcat không mang mốc ⇒ không được dời mốc nối lại.
        assertNull(KachiLog.stampOf("\tat java.lang.Thread.run(Thread.java:919)"))
        assertNull(KachiLog.stampOf("--------- beginning of main"))
    }

    @Test fun `het dong ma chua cham tran thi noi lai, co tran so lan, lui dan`() {
        assertTrue(KachiLog.shouldResume(capped = false, resumes = 0), "EOF im lặng của ≤ 2.98 — nay phải nối lại")
        assertFalse(KachiLog.shouldResume(capped = true, resumes = 0), "chạm trần byte là dừng CÓ CHỦ Ý")
        assertFalse(KachiLog.shouldResume(capped = false, resumes = KachiLog.MAX_RESUMES), "logd hỏng hẳn ⇒ không quay mãi")
        assertEquals(2_000L, KachiLog.resumeDelayMs(1))
        assertEquals(4_000L, KachiLog.resumeDelayMs(2))
        assertEquals(60_000L, KachiLog.resumeDelayMs(KachiLog.MAX_RESUMES))
    }

    @Test fun `moi tien trinh tru tts deu chup, tep rieng cho wake`() {
        val pkg = "com.byd.launcher"
        assertEquals("", KachiLog.captureTag(pkg, pkg, ":wake", ":tts"))
        assertEquals("wake", KachiLog.captureTag("$pkg:wake", pkg, ":wake", ":tts"))
        assertNull(KachiLog.captureTag("$pkg:tts", pkg, ":wake", ":tts"))
        assertNull(KachiLog.captureTag(null, pkg, ":wake", ":tts"), "không đọc được tên ⇒ không đoán")
        assertEquals("usage-1791618956803.log", KachiLog.usageFileName(1791618956803L, ""), "tên cũ của tiến trình chính giữ nguyên")
        assertEquals("usage-wake-1791618956803.log", KachiLog.usageFileName(1791618956803L, "wake"))
    }

    /** CLAUDE.md §8 — hàm mới phải ĐƯỢC GỌI: ở `Application.onCreate` TRƯỚC cổng tiến trình nền (để `:wake` qua được). */
    @Test fun `Application bat chup o moi lan tien trinh khoi dong, truoc cong tien trinh nen`() {
        val body = source("app/src/main/java/com/byd/clusternav/KachiApplication.kt").substringAfter("override fun onCreate()")
        val start = body.indexOf("KachiLog.startCapture(this, tag)")
        val tag = body.indexOf("KachiLog.captureTag(")
        val gate = body.indexOf("if (isBackgroundVoiceProcess()) return")
        assertTrue(tag in 0 until start && start < gate, "captureTag → startCapture phải đứng TRƯỚC cổng tiến trình nền")
        val log = source("app/src/main/java/com/byd/clusternav/launcher/KachiLog.kt")
        assertTrue(log.contains("shouldResume(capped, c.resumes)"), "vòng chụp phải hỏi luật nối lại, không thoát im lặng")
        assertTrue(log.contains("captureCommand(Process.myPid(), c.lastStamp)"), "lượt nối lại phải đọc từ mốc dòng cuối")
    }

    private fun source(rel: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val text = listOf(cwd.resolve(rel), cwd.resolve("../$rel"), cwd.resolve(rel.removePrefix("app/"))).first { it.isFile }.readText()
        return KotlinSource.stripComments(text)
    }
}
