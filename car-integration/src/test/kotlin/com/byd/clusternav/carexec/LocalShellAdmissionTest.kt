package com.byd.clusternav.carexec

import dadb.AdbConnectException
import dadb.AdbKeyPair
import java.io.File
import java.net.SocketTimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * READY-AT-HOME §4.6 — CỔNG THI HÀNH ở cửa [LocalShellSessions.run] (mọi phiên `LocalDeviceShell`), chạy bằng ĐÚNG
 * vòng lặp production với connector giả (khuôn `LocalShellApprovalRetryTest`).
 *
 * Bài khoá R1.1: phiên NỀN bị chặn thì **không có kết nối nào được mở** — adbd không bao giờ thấy khoá ⇒ không có hộp
 * "Cho phép gỡ lỗi USB?" nào bung từ nền. Và đường HỎI (F4, phím mic, nút Sửa ngay) luôn được cho.
 */
class LocalShellAdmissionTest {

    private class Rec {
        val events = mutableListOf<String>()
        val kinds = mutableListOf<ShellSessionKind>()
        val got = mutableListOf<ReadyEvent>()
    }

    /** Móc giả: chặn mọi phiên NỀN, cho mọi phiên HỎI; ghi lại sự kiện báo về. */
    private class DenyBackground(private val r: Rec) : LocalShellAdmission.Hook {
        override fun admit(kind: ShellSessionKind, caller: String): Boolean { r.kinds += kind; return kind == ShellSessionKind.ASK }
        override fun onEvent(event: ReadyEvent, src: String) { r.got += event }
    }

    private class AllowAll(private val r: Rec) : LocalShellAdmission.Hook {
        override fun admit(kind: ShellSessionKind, caller: String): Boolean { r.kinds += kind; return true }
        override fun onEvent(event: ReadyEvent, src: String) { r.got += event }
    }

    private class Fake(
        private val r: Rec,
        private val openFailure: Throwable? = null,
        private val shellFailure: Throwable? = null,
    ) : LocalShellConnector {
        override fun open(keys: AdbKeyPair, socketTimeoutMs: Int): LocalShellConnection {
            r.events += "open"
            openFailure?.let { throw it }
            return object : LocalShellConnection {
                override fun handshake() { r.events += "handshake" }
                override fun shell(command: String): LocalShellText {
                    r.events += "shell:$command"
                    shellFailure?.let { throw it }
                    return LocalShellText("ok", "", 0)
                }
                override fun close() { r.events += "close" }
            }
        }
    }

    private var cached: AdbKeyPair? = null
    private fun keys(dir: File): AdbKeyPair = cached ?: run {
        AdbKeyPair.generate(File(dir, "adb.key"), File(dir, "adb.pub"))
        AdbKeyPair.read(File(dir, "adb.key"), File(dir, "adb.pub")).also { cached = it }
    }

    private fun awaiting() = AdbConnectException("Connection handshake failed", SocketTimeoutException("Read timed out"))

    private fun run(dir: File, c: LocalShellConnector, retry: LocalShellRetry): LocalShellResult<String> =
        LocalShellSessions.run(c, keys(dir), retry, { _, _, _ -> }, { 0L }, { }) { sh -> sh("echo kachi_ok").output }

    @AfterEach fun reset() = LocalShellAdmission.reset()

    @Test
    fun `mac dinh cho tat ca - hanh vi cu khong doi`(@TempDir dir: File) {
        val r = Rec()
        val res = run(dir, Fake(r), LocalShellRetry.BACKGROUND_READ_CAP)
        assertTrue(res is LocalShellResult.Ok)
        assertEquals(listOf("open", "shell:echo kachi_ok", "close"), r.events)
    }

    @Test
    fun `phien NEN bi chan thi KHONG mo ket noi nao - adbd khong thay khoa`(@TempDir dir: File) {
        val r = Rec()
        LocalShellAdmission.install(DenyBackground(r))
        listOf(LocalShellRetry.NONE, LocalShellRetry.BACKGROUND_READ_CAP).forEach { retry ->
            val res = run(dir, Fake(r), retry)
            assertEquals(LocalShellResult.Failed(LocalShellFailure.NOT_APPROVED, attempts = 0, cause = null), res)
        }
        assertEquals(emptyList<String>(), r.events, "không 'open' nào = không gửi khoá công khai = không hộp thoại nền")
        assertEquals(listOf(ShellSessionKind.BACKGROUND, ShellSessionKind.BACKGROUND), r.kinds)
        assertEquals(emptyList<ReadyEvent>(), r.got, "bị chặn không phải một phép đo — không báo gì")
    }

    @Test
    fun `duong HOI luon duoc cho - F4, phim mic, nut Sua ngay`(@TempDir dir: File) {
        val r = Rec()
        LocalShellAdmission.install(DenyBackground(r))
        listOf(FirstOpenApproval.PROBE, LocalShellRetry.AWAIT_ADB_APPROVAL, LocalShellRetry.USER_READ_CAP).forEach { retry ->
            assertTrue(retry.mayPromptUser)
            assertTrue(run(dir, Fake(r), retry) is LocalShellResult.Ok, "$retry")
        }
        assertEquals(List(3) { ShellSessionKind.ASK }, r.kinds)
        assertFalse(LocalShellRetry.BACKGROUND_READ_CAP.mayPromptUser)
        assertFalse(LocalShellRetry.NONE.mayPromptUser)
    }

    @Test
    fun `bat tay xong thi bao UP - ke ca phien nen noi luoi`(@TempDir dir: File) {
        val r = Rec()
        LocalShellAdmission.install(AllowAll(r))
        run(dir, Fake(r), LocalShellRetry.BACKGROUND_READ_CAP)
        run(dir, Fake(r), FirstOpenApproval.PROBE)
        assertEquals(listOf<ReadyEvent>(ReadyEvent.Up, ReadyEvent.Up), r.got)
    }

    @Test
    fun `phien HOI hong o bat tay vi adbd dang hoi thi bao THU HOI`(@TempDir dir: File) {
        val r = Rec()
        LocalShellAdmission.install(AllowAll(r))
        val res = run(dir, Fake(r, openFailure = awaiting()), FirstOpenApproval.PROBE)
        assertEquals(LocalShellFailure.AWAITING_APPROVAL, (res as LocalShellResult.Failed).reason)
        assertEquals(listOf<ReadyEvent>(ReadyEvent.Revoked), r.got)
    }

    /** Review lượt 1 [P3]: dòng `up src=` / `deny caller=` nói ĐƯỜNG (`early`, `f4`), không phải tên luồng (spec §4.10). */
    @Test
    fun `nhan nguon di kem phien - khong nhan thi ten luong`(@TempDir dir: File) {
        val srcs = mutableListOf<String>()
        val callers = mutableListOf<String>()
        LocalShellAdmission.install(object : LocalShellAdmission.Hook {
            override fun admit(kind: ShellSessionKind, caller: String): Boolean { callers += caller; return true }
            override fun onEvent(event: ReadyEvent, src: String) { srcs += src }
        })
        LocalShellAdmission.labeled("early") { run(dir, Fake(Rec()), FirstOpenApproval.PROBE) }
        run(dir, Fake(Rec()), FirstOpenApproval.PROBE)
        val thread = Thread.currentThread().name
        assertEquals(listOf("early", thread), srcs)
        assertEquals(listOf("early", thread), callers, "nhãn tự gỡ khi ra khỏi khối")
    }

    @Test
    fun `hong SAU khi lenh da gui thi KHONG bao thu hoi`(@TempDir dir: File) {
        val r = Rec()
        LocalShellAdmission.install(AllowAll(r))
        // Phiên NỀN nối lười: lệnh đầu gửi rồi mới đọc ⇒ hạn đọc nổ ở đây vẫn là AWAITING nhưng khoá có thể đang được nhận.
        val res = run(dir, Fake(r, shellFailure = awaiting()), LocalShellRetry.BACKGROUND_READ_CAP)
        assertTrue((res as LocalShellResult.Failed).commandDispatched)
        assertEquals(emptyList<ReadyEvent>(), r.got)
        // Phiên HỎI ép bắt tay: bắt tay xong rồi lệnh hỏng ⇒ khoá ĐƯỢC nhận ⇒ UP, không phải thu hồi.
        run(dir, Fake(r, shellFailure = awaiting()), FirstOpenApproval.PROBE)
        assertEquals(listOf<ReadyEvent>(ReadyEvent.Up), r.got)
    }
}
