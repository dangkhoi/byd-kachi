package com.byd.clusternav

import com.byd.clusternav.carexec.LocalInstallOutcome
import com.byd.clusternav.carexec.LocalShellFailure
import com.byd.clusternav.launcher.Lang as CoreLang
import com.byd.clusternav.launcher.Strings as CoreStrings
import dadb.AdbKeyPair
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Hardening 2026-09-25 — hai lỗ trên đường OTA:
 *  • audit F4 [P1]: `AdbKeys.ensure` ném trên `Thread("update-download")` TRẦN ⇒ launcher chết. Nay: huỷ hẹn
 *    relaunch + câu "không có kênh shell (UNKNOWN)" lên UI, KHÔNG ném.
 *  • audit F3 [P2]: tải hỏng ⇒ `null` im + `disconnect()` không `finally` + `.apk` cụt để lại. Nay: lỗi ra
 *    `onError` đúng một lần, kết nối luôn đóng, tệp cụt bị xoá.
 */
class UpdateCheckerHardeningTest {

    @BeforeEach fun vietnamese() { CoreStrings.current = CoreLang.VI }
    @AfterEach fun restore() { CoreStrings.current = CoreLang.VI }

    @Test fun `khoa adb hong - khong nem, huy hen relaunch, cau noi dung benh`() {
        val log = mutableListOf<String>()
        var armed = 0; var disarmed = 0; var installed = 0
        val msg = UpdateChecker.installWith(
            apkPath = "/data/x/update.apk",
            arm = { armed++ },
            disarm = { disarmed++ },
            keys = { throw IllegalStateException("rename adb keypair thất bại (/data/x)") },
            installApk = { installed++; LocalInstallOutcome.Ok },
            onKeysError = { log += "${it.javaClass.simpleName}: ${it.message}" },
        )
        assertEquals(1, armed, "hẹn TRƯỚC khi cài (một -r thành công giết tiến trình giữa lời gọi)")
        assertEquals(1, disarmed, "khoá hỏng ⇒ không có gì được thay ⇒ phải huỷ hẹn mở lại")
        assertEquals(0, installed, "không có khoá thì không cài")
        assertEquals(listOf("IllegalStateException: rename adb keypair thất bại (/data/x)"), log)
        assertTrue(msg.contains("UNKNOWN") && msg.contains("kênh shell"), "câu phải là 'không có kênh shell (UNKNOWN)' — $msg")
        assertTrue(msg.contains("/data/x/update.apk"), "APK vẫn nằm đó, nói chỗ để cài tay")
    }

    @Test fun `co khoa - trinh tu cu y nguyen (Ok giu hen, hong thi huy)`(@TempDir dir: File) {
        AdbKeyPair.generate(File(dir, "k"), File(dir, "p"))
        val keys = AdbKeyPair.read(File(dir, "k"), File(dir, "p"))
        var disarmed = 0
        val ok = UpdateChecker.installWith("/a.apk", arm = {}, disarm = { disarmed++ }, keys = { keys }, installApk = { LocalInstallOutcome.Ok }, onKeysError = { error("không được gọi") })
        assertTrue(ok.contains("đã cài"), ok)
        assertEquals(0, disarmed, "cài xong thì giữ hẹn mở lại")
        val rejected = UpdateChecker.installWith("/a.apk", arm = {}, disarm = { disarmed++ }, keys = { keys }, installApk = { LocalInstallOutcome.PmRejected("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]") }, onKeysError = { error("không được gọi") })
        assertTrue(rejected.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE"), rejected)
        assertEquals(1, disarmed, "pm từ chối ⇒ huỷ hẹn")
        val noChannel = UpdateChecker.installWith("/a.apk", arm = {}, disarm = { disarmed++ }, keys = { keys }, installApk = { LocalInstallOutcome.NoShellChannel(LocalShellFailure.PORT_CLOSED) }, onKeysError = { error("không được gọi") })
        assertTrue(noChannel.contains("PORT_CLOSED"), noChannel)
        assertEquals(2, disarmed)
    }

    /** Luồng đứt ở byte thứ N — ca "đứt giữa chừng" của mạng xe. */
    private class CutStream(private val bytes: ByteArray, private val cutAt: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos >= cutAt) throw IOException("unexpected end of stream") else bytes[pos++].toInt()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= cutAt) throw IOException("unexpected end of stream")
            val n = minOf(len, cutAt - pos); System.arraycopy(bytes, pos, b, off, n); pos += n; return n
        }
    }

    @Test fun `tai dut giua chung - null, onError mot lan, tep cut bi xoa, ket noi luon dong`(@TempDir dir: File) {
        val out = File(dir, "Kachi-9.99-release.apk")
        val errors = mutableListOf<Throwable>(); var closed = 0; val progress = mutableListOf<Int>()
        val r = UpdateChecker.fetchTo(
            out = out,
            open = { CutStream(ByteArray(200_000) { 1 }, cutAt = 70_000) to 200_000L },
            close = { closed++ },
            onProgress = { progress += it },
            onError = { errors += it },
        )
        assertNull(r)
        assertEquals(1, errors.size); assertTrue(errors[0] is IOException)
        assertFalse(out.exists(), "không để lại .apk cụt trong files/update/")
        assertEquals(1, closed, "disconnect() phải ở finally")
        assertTrue(progress.isNotEmpty() && progress.all { it in 0..100 }, "tiến độ trước khi đứt vẫn báo — $progress")
    }

    @Test fun `open nem (DNS, TLS, 302 sang http) - null + onError + close`(@TempDir dir: File) {
        val out = File(dir, "u.apk")
        val errors = mutableListOf<Throwable>(); var closed = 0
        assertNull(UpdateChecker.fetchTo(out, open = { throw IllegalArgumentException("https only") }, close = { closed++ }, onProgress = {}, onError = { errors += it }))
        assertEquals(1, errors.size); assertEquals(1, closed); assertFalse(out.exists())
    }

    @Test fun `tai tron ven - tra tep, tien do 100, dong ket noi`(@TempDir dir: File) {
        val out = File(dir, "u.apk")
        var closed = 0; val progress = mutableListOf<Int>()
        val data = ByteArray(100_000) { 7 }
        val r = UpdateChecker.fetchTo(out, open = { ByteArrayInputStream(data) to data.size.toLong() }, close = { closed++ }, onProgress = { progress += it }, onError = { error("không được gọi") })
        assertEquals(out, r); assertEquals(data.size.toLong(), out.length()); assertEquals(100, progress.last()); assertEquals(1, closed)
        // Không biết tổng cỡ ⇒ -1 (hợp đồng cũ của onProgress).
        val r2 = UpdateChecker.fetchTo(File(dir, "v.apk"), open = { ByteArrayInputStream(data) to -1L }, close = {}, onProgress = { progress += it }, onError = { error("không được gọi") })
        assertTrue(r2 != null && progress.last() == -1)
    }

    /** Wiring (CLAUDE.md §8): `install`/`download` phải đi qua hai hàm thuần, và luồng tải không còn giữ Activity mạnh. */
    @Test fun `install va download noi day qua ham thuan, UpdateFlow giu Activity yeu`() {
        val checker = source("app/src/main/java/com/byd/clusternav/UpdateChecker.kt")
        assertTrue(checker.substringAfter("fun install(ctx: Context, apk: File)").substringBefore("internal fun installWith").contains("keys = { AdbKeys.ensure(app) }"), "AdbKeys.ensure phải đi qua seam `keys` (được bọc), không gọi trần")
        assertTrue(checker.substringAfter("fun download(ctx: Context").substringBefore("internal fun fetchTo").contains("close = { conn.disconnect() }"), "disconnect qua finally của fetchTo")
        val flow = source("app/src/main/java/com/byd/clusternav/UpdateFlow.kt")
        val body = flow.substringAfter("private fun doUpdate(")
        assertTrue(body.contains("WeakReference(activity)"), "F13: luồng tải hàng phút không giữ Activity mạnh")
        assertTrue(body.contains("a.isDestroyed"), "…và không vẽ lên view của màn đã huỷ")
    }

    /** Mã nguồn ĐÃ BỎ comment `//` — để một dòng bị comment-out không còn làm bài canh xanh giả (thử-làm-đỏ 2026-09-25). */
    private fun source(rel: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val text = listOf(cwd.resolve(rel), cwd.resolve("../$rel"), cwd.resolve(rel.removePrefix("app/"))).first { it.isFile }.readText()
        return text.lines().joinToString("\n") { it.substringBefore("//") }
    }
}
