package com.byd.clusternav.carexec

import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbKeyPair
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * F4 — *hộp "Cho phép gỡ lỗi USB?" bị chính màn Kachi đè chết ở lần mở đầu* ([ĐO] xe DiLink3.0 2026-09-14,
 * `docs/diagnostics/carlog-kachi-20260914-2044/session-findings.md`).
 *
 * Bài này khoá **phần quyết định thuần** ([FirstOpenApproval]) — phần vòng đời (hoãn lần dò đầu · dừng ở
 * `onStop` · dải nhắc) nằm ở `:app` và có bài riêng (`ShellApprovalWiringContractTest`), vì nó chạm Android.
 *
 * Ngoại lệ dựng ở đây là đúng loại và đúng cách bọc mà `AdbConnection.Companion.connect` /
 * `DadbImpl.newConnection` ném thật (bằng chứng bytecode ở KDoc [LocalShellFailure]).
 */
class FirstOpenApprovalPolicyTest {

    private var cachedKeys: AdbKeyPair? = null

    private fun keys(dir: File): AdbKeyPair = cachedKeys ?: run {
        val priv = File(dir, "adb.key")
        val pub = File(dir, "adb.pub")
        AdbKeyPair.generate(priv, pub)
        AdbKeyPair.read(priv, pub).also { cachedKeys = it }
    }

    /** Hộp thoại đang treo: adbd im lặng ⇒ hạn ĐỌC bắn ⇒ dadb bọc lại. Đây là ca đã đo trên xe. */
    private fun awaitingApproval() =
        AdbConnectException("Connection handshake failed", SocketTimeoutException("Read timed out"))

    private fun portClosed() =
        AdbConnectException("Failed to connect to localhost:5555", ConnectException("Connection refused"))

    // ── 1 · PHÂN LOẠI: chỉ "đang chờ người bấm" mới được lặp ────────────────────────────────────────

    @Test
    fun `kenh len duoc thi chay duong noi day da co`() {
        assertEquals(FirstOpenStep.ChannelUp, FirstOpenApproval.step(reason = null, screenShowing = true))
    }

    @Test
    fun `adbd im lang la CHO NGUOI DUNG BAM, hen lai sau 20 giay`() {
        val step = FirstOpenApproval.step(LocalShellFailure.AWAITING_APPROVAL, screenShowing = true)
        assertEquals(FirstOpenStep.AwaitingUser(FirstOpenApproval.RETRY_EVERY_MS), step)
        assertEquals(20_000L, FirstOpenApproval.RETRY_EVERY_MS, "đổi nhịp thì phải đổi cả doc + spec §9")
    }

    /**
     * Màn khuất (`onStop`) ⇒ **không hẹn lượt sau**. Đây là nửa còn lại của "thử lại không giới hạn": không giới
     * hạn về SỐ LẦN, nhưng bị chặn cứng bởi VÒNG ĐỜI — nếu không thì một màn đã khuất vẫn dựng hộp thoại hệ thống
     * lên trên app người lái đang dùng (CLAUDE.md §5).
     */
    @Test
    fun `man khuat thi khong hen luot sau`() {
        val step = FirstOpenApproval.step(LocalShellFailure.AWAITING_APPROVAL, screenShowing = false)
        assertEquals(FirstOpenStep.AwaitingUser(FirstOpenApproval.NO_RETRY), step)
    }

    /**
     * Bốn lý do còn lại KHÔNG được nói "đang chờ bấm".
     *
     * [LocalShellFailure.AUTH_REJECTED] là chỗ dễ sai nhất: nó cũng là "chưa được cấp quyền", nhưng máy đã TRẢ LỜI
     * (từ chối) chứ không im lặng ⇒ gộp vào nhánh chờ là cứ 20 s lại dựng một hộp thoại cho người vừa bấm Từ chối.
     */
    @Test
    fun `bon ly do con lai la HAN CHE MOI TRUONG, khong lap vo han`() {
        listOf(
            LocalShellFailure.PORT_CLOSED,
            LocalShellFailure.AUTH_REJECTED,
            LocalShellFailure.IO_ERROR,
            LocalShellFailure.UNKNOWN,
        ).forEach { reason ->
            assertEquals(
                FirstOpenStep.Environment(reason),
                FirstOpenApproval.step(reason, screenShowing = true),
                "$reason không phải là 'đang chờ người dùng bấm'",
            )
        }
    }

    @Test
    fun `phan loai di qua dung bo classify da co, khong dung bang thu hai`() {
        assertEquals(
            FirstOpenStep.AwaitingUser(FirstOpenApproval.RETRY_EVERY_MS),
            FirstOpenApproval.step(LocalShellFailures.classify(awaitingApproval()), screenShowing = true),
        )
        assertEquals(
            FirstOpenStep.Environment(LocalShellFailure.PORT_CLOSED),
            FirstOpenApproval.step(LocalShellFailures.classify(portClosed()), screenShowing = true),
        )
        assertEquals(
            FirstOpenStep.Environment(LocalShellFailure.AUTH_REJECTED),
            FirstOpenApproval.step(
                LocalShellFailures.classify(AdbAuthException("Device rejected authentication (unauthorized)")),
                screenShowing = true,
            ),
        )
    }

    // ── 2 · KHÔNG dựng hộp thoại chồng hộp thoại ────────────────────────────────────────────────────

    @Test
    fun `mat tieu diem cua so thi BO luot do nay`() {
        assertTrue(FirstOpenApproval.attemptAllowed(screenShowing = true, windowFocused = true))
        // Hộp thoại hệ thống đang ở trên ⇒ màn chính mất tiêu điểm ⇒ không mở thêm kết nối (mỗi kết nối mới là
        // một hộp thoại mới chồng lên cái người dùng đang đọc dở).
        assertTrue(!FirstOpenApproval.attemptAllowed(screenShowing = true, windowFocused = false))
        assertTrue(!FirstOpenApproval.attemptAllowed(screenShowing = false, windowFocused = true))
    }

    // ── 3 · CHÍNH SÁCH TRANSPORT của một lượt dò ────────────────────────────────────────────────────

    /**
     * Hạn đọc phải **nhỏ hơn** nhịp thử lại. Không thì hai lượt dò chồng nhau: lượt sau mở kết nối trong khi lượt
     * trước còn đang treo — đúng thứ sinh ra `Recv-Q` dâng mà [ĐO] trên xe đã thấy.
     */
    @Test
    fun `han doc nho hon nhip thu lai`() {
        assertTrue(
            FirstOpenApproval.PROBE_READ_TIMEOUT_MS < FirstOpenApproval.RETRY_EVERY_MS,
            "hạn đọc ${FirstOpenApproval.PROBE_READ_TIMEOUT_MS} ms >= nhịp ${FirstOpenApproval.RETRY_EVERY_MS} ms",
        )
        assertTrue(FirstOpenApproval.PROBE.socketTimeoutMs > 0, "không có hạn đọc ⇒ luồng nền treo VĨNH VIỄN")
    }

    /**
     * Lượt dò **không** được tự thử lại ở tầng transport: vòng lặp phải nằm ở tầng biết vòng đời màn hình
     * (dừng ở `onStop`). `attempts = 1` cũng chính là thứ bảo đảm **mỗi lượt = một kết nối mới**.
     */
    @Test
    fun `mot luot do la MOT lan thu, khong tu lap ben trong`() {
        assertEquals(1, FirstOpenApproval.PROBE.attempts)
        assertEquals(emptySet<LocalShellFailure>(), FirstOpenApproval.PROBE.retryOn)
        assertEquals(0L, FirstOpenApproval.PROBE.budgetMs, "trần thời gian là của vòng lặp trên, không của lượt dò")
        assertTrue(FirstOpenApproval.PROBE.eagerHandshake, "phải ép bắt tay để lỗi xác thực lộ TRƯỚC khi gửi lệnh")
    }

    /**
     * ⚠ Đường của F2 **không đổi một byte** (CLAUDE.md §6: đường đang chạy tốt ngoài hiện trường thì không đụng).
     */
    @Test
    fun `chinh sach cua phim mic F2 giu nguyen`() {
        assertEquals(4, LocalShellRetry.AWAIT_ADB_APPROVAL.attempts)
        assertEquals(30_000L, LocalShellRetry.AWAIT_ADB_APPROVAL.budgetMs)
        assertEquals(6_000, LocalShellRetry.AWAIT_ADB_APPROVAL.socketTimeoutMs)
        assertNotEquals(
            LocalShellRetry.AWAIT_ADB_APPROVAL, FirstOpenApproval.PROBE,
            "lần mở đầu phải có chính sách RIÊNG: chờ ~31 s rồi bỏ cuộc là quay lại đúng bệnh F4",
        )
    }

    // ── 4 · MỖI LƯỢT LÀ MỘT KẾT NỐI MỚI (phiên cũ kẹt phải được đóng) ───────────────────────────────

    private class Probe(private val failures: List<Throwable?>) : LocalShellConnector {
        val events = mutableListOf<String>()
        val socketTimeouts = mutableListOf<Int>()
        private var opens = 0

        override fun open(keys: AdbKeyPair, socketTimeoutMs: Int): LocalShellConnection {
            socketTimeouts += socketTimeoutMs
            events += "open"
            val failure = failures.getOrNull(opens)
            opens++
            if (failure != null) throw failure
            return object : LocalShellConnection {
                override fun handshake() { events += "handshake" }
                override fun shell(command: String): LocalShellText {
                    events += "shell:$command"
                    return LocalShellText(output = "kachi_ok", errorOutput = "", exitCode = 0)
                }
                override fun close() { events += "close" }
            }
        }
    }

    private fun runProbe(connector: Probe, dir: File): LocalShellResult<Boolean> = LocalShellSessions.run(
        connector = connector,
        keys = keys(dir),
        retry = FirstOpenApproval.PROBE,
        onProgress = { _, _, _ -> },
        nowMs = { 0L },
        sleepMs = { },
    ) { sh -> sh("echo kachi_ok").output.contains("kachi_ok") }

    @Test
    fun `luot do hong chi mo DUNG MOT ket noi, luot sau mo ket noi khac`(@TempDir dir: File) {
        val connector = Probe(listOf(awaitingApproval(), awaitingApproval()))

        val first = runProbe(connector, dir)
        assertEquals(listOf("open"), connector.events, "hỏng lúc mở thì KHÔNG được tự thử lại trong cùng lượt")
        assertEquals(
            LocalShellFailure.AWAITING_APPROVAL, (first as LocalShellResult.Failed).reason,
        )

        runProbe(connector, dir)
        assertEquals(listOf("open", "open"), connector.events, "lượt sau phải là một kết nối MỚI")
        assertEquals(
            listOf(FirstOpenApproval.PROBE_READ_TIMEOUT_MS, FirstOpenApproval.PROBE_READ_TIMEOUT_MS),
            connector.socketTimeouts,
            "mỗi lượt phải mang hạn đọc; thiếu nó là treo vĩnh viễn",
        )
    }

    @Test
    fun `luot do thanh cong bat tay truoc, gui dung mot lenh roi DONG phien`(@TempDir dir: File) {
        val connector = Probe(listOf(null))
        val result = runProbe(connector, dir)
        assertEquals(true, (result as LocalShellResult.Ok).value)
        assertEquals(listOf("open", "handshake", "shell:echo kachi_ok", "close"), connector.events)
    }

    @Test
    fun `dut giua chung van la moi truong, khong phai cho nguoi bam`(@TempDir dir: File) {
        val connector = Probe(listOf(AdbConnectException("Connection handshake failed", EOFException("eof"))))
        val result = runProbe(connector, dir) as LocalShellResult.Failed
        assertEquals(
            FirstOpenStep.Environment(LocalShellFailure.IO_ERROR),
            FirstOpenApproval.step(result.reason, screenShowing = true),
        )
    }
}
