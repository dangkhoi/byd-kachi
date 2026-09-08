package com.byd.clusternav.carexec

import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbKeyPair
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * F2 — *"start app vẫn chưa hold mic gọi gemini/kiki được, phải tắt, mở lại thì mới xin được quyền và mới
 * sử dụng được"* (owner, 2026-08-24).
 *
 * Khoá lại ĐÚNG vòng lặp mà đường phím mic chạy trên xe: `AssistantLauncher.launchViaVoiceAssistKey` gọi
 * `LocalDeviceShell.sessionResult(keys, LocalShellRetry.AWAIT_ADB_APPROVAL) { sh -> sh("input keyevent 231") }`,
 * và `sessionResult` uỷ quyền thẳng cho [LocalShellSessions.run]. Test dùng **chính hằng chính sách của
 * production** ([LocalShellRetry.AWAIT_ADB_APPROVAL]) và **chính chuỗi lệnh của production**, chỉ tiêm
 * transport/đồng hồ/giấc ngủ — nếu đổi chính sách hoặc bỏ vòng chờ thì test đỏ (bài học 08-24: nút thử
 * không phải đường thật).
 *
 * Bằng chứng transport ở [LocalShellFailure] (bytecode dadb 2.0.0) — các ngoại lệ dựng ở đây là đúng loại
 * và đúng cách bọc mà `AdbConnection.Companion.connect` / `DadbImpl.newConnection` ném ra thật.
 */
class LocalShellApprovalRetryTest {

    /** Lệnh THẬT mà đường phím mic gửi. Đổi lệnh mà quên đổi test = test mù. */
    private val voiceAssistCommand = "input keyevent 231"

    private var cachedKeys: AdbKeyPair? = null

    /** Sinh keypair thật một lần: seam chỉ chuyển tiếp nó, nhưng chữ ký hàm đòi kiểu thật. */
    private fun keys(dir: File): AdbKeyPair = cachedKeys ?: run {
        val priv = File(dir, "adb.key")
        val pub = File(dir, "adb.pub")
        AdbKeyPair.generate(priv, pub)
        AdbKeyPair.read(priv, pub).also { cachedKeys = it }
    }

    /** Hộp thoại "Cho phép gỡ lỗi USB?" đang treo: adbd im lặng ⇒ hạn ĐỌC bắn ⇒ dadb bọc lại. */
    private fun awaitingApproval() =
        AdbConnectException("Connection handshake failed", SocketTimeoutException("Read timed out"))

    /** Máy trả `AUTH` lần nữa — dadb ném đúng câu này (xem bytecode offset 130-142). */
    private fun rejected() = AdbAuthException("Device rejected authentication (unauthorized)")

    /** Không có gì lắng nghe 5555 — `DadbImpl.newConnection` bọc lỗi socket. */
    private fun portClosed() =
        AdbConnectException("Failed to connect to localhost:5555", ConnectException("Connection refused"))

    private class Recorder {
        val events = mutableListOf<String>()
        val slept = mutableListOf<Long>()
        val progress = mutableListOf<Triple<Int, LocalShellFailure, Long>>()
        val socketTimeouts = mutableListOf<Int>()
        var nowMs = 0L
    }

    /**
     * Transport giả: [failures] là lỗi ném ra ở lần **mở** thứ 1, 2, … (`null` = mở được);
     * [shellFailures] là lỗi ném ra ở lần **gửi lệnh** thứ 1, 2, … (`null` = lệnh chạy trơn).
     *
     * ⚠ Đồng hồ giả phải tiến ĐÚNG cái giá của một lần hỏng trên xe. Bản 08-24 đầu tiên để đồng hồ đứng
     * yên trong lúc thử ⇒ trần [LocalShellRetry.budgetMs] không bao giờ chạm ⇒ test khẳng định 5 lần ×
     * 1/2/4/8 s trong khi xe chỉ chạy được 4 lần × 1/2/4 s. Một lần hỏng vì "adbd im lặng" tốn đúng
     * `socketTimeoutMs` (lần đọc treo tới hạn rồi mới ném); các lỗi khác (cổng đóng, máy trả AUTH ngay)
     * là tức thời.
     */
    private class FakeConnector(
        private val recorder: Recorder,
        private val failures: List<Throwable?>,
        private val shellFailures: List<Throwable?> = emptyList(),
    ) : LocalShellConnector {
        private var opens = 0
        private var commands = 0

        override fun open(keys: AdbKeyPair, socketTimeoutMs: Int): LocalShellConnection {
            recorder.socketTimeouts += socketTimeoutMs
            recorder.events += "open"
            val failure = failures.getOrNull(opens)
            opens++
            if (failure != null) {
                if (LocalShellFailures.classify(failure) == LocalShellFailure.AWAITING_APPROVAL) {
                    recorder.nowMs += socketTimeoutMs
                }
                throw failure
            }
            return object : LocalShellConnection {
                override fun handshake() {
                    recorder.events += "handshake"
                }

                override fun shell(command: String): LocalShellText {
                    // Ghi TRƯỚC khi ném: trên dây thật, lệnh đã được ghi ra socket rồi mới tới lượt đọc
                    // kết quả — hỏng lúc đọc KHÔNG có nghĩa là thiết bị chưa nhận lệnh.
                    recorder.events += "shell:$command"
                    val failure = shellFailures.getOrNull(commands)
                    commands++
                    if (failure != null) {
                        if (LocalShellFailures.classify(failure) == LocalShellFailure.AWAITING_APPROVAL) {
                            recorder.nowMs += socketTimeoutMs
                        }
                        throw failure
                    }
                    return LocalShellText(output = "", errorOutput = "", exitCode = 0)
                }

                override fun close() {
                    recorder.events += "close"
                }
            }
        }
    }

    private fun runVoiceKeyPath(
        dir: File,
        recorder: Recorder,
        failures: List<Throwable?>,
        retry: LocalShellRetry = LocalShellRetry.AWAIT_ADB_APPROVAL,
        shellFailures: List<Throwable?> = emptyList(),
    ): LocalShellResult<Boolean> = LocalShellSessions.run(
        connector = FakeConnector(recorder, failures, shellFailures),
        keys = keys(dir),
        retry = retry,
        onProgress = { attempt, reason, waitMs -> recorder.progress += Triple(attempt, reason, waitMs) },
        nowMs = { recorder.nowMs },
        sleepMs = { ms -> recorder.slept += ms; recorder.nowMs += ms },
    ) { sh -> sh(voiceAssistCommand).exitCode == 0 }

    @Test
    fun `owner bam Cho phep giua chung — lan thu hai phat duoc keyevent 231`(@TempDir dir: File) {
        val recorder = Recorder()
        // Lần 1: hộp thoại đang treo. Lần 2 (sau khi owner bấm Đồng ý): nối được.
        val result = runVoiceKeyPath(dir, recorder, listOf(awaitingApproval(), null))

        assertEquals(LocalShellResult.Ok(true, attempts = 2), result, "phải thành công ở lần thử thứ 2")
        assertEquals(
            listOf("open", "open", "handshake", "shell:$voiceAssistCommand", "close"),
            recorder.events,
            "bắt tay phải xong TRƯỚC lệnh đầu tiên, và lệnh chỉ được gửi ĐÚNG MỘT lần",
        )
        assertEquals(listOf(Triple(1, LocalShellFailure.AWAITING_APPROVAL, 1_000L)), recorder.progress,
            "phải báo cho owner biết đang chờ bấm Đồng ý, kèm thời gian chờ")
        assertEquals(listOf(1_000L), recorder.slept, "giãn cách đầu tiên = 1 s")
    }

    @Test
    fun `cong 5555 dong thi bao ngay, khong dot 30 giay cho vo ich`(@TempDir dir: File) {
        val recorder = Recorder()
        val result = runVoiceKeyPath(dir, recorder, List(5) { portClosed() })

        assertEquals(
            LocalShellFailure.PORT_CLOSED,
            (result as LocalShellResult.Failed).reason,
            "adb-tcp chưa bật là lý do RIÊNG, không được lẫn với chưa-cấp-quyền",
        )
        assertEquals(1, result.attempts, "không có gì lắng nghe thì thử lại vô nghĩa")
        assertTrue(recorder.slept.isEmpty(), "không được ngủ lần nào")
        assertTrue(recorder.progress.isEmpty(), "không báo 'đang chờ' khi chờ vô ích")
    }

    @Test
    fun `may tu choi khoa mai — bo cuoc dung 4 lan`(@TempDir dir: File) {
        val recorder = Recorder()
        val result = runVoiceKeyPath(dir, recorder, List(9) { rejected() })

        val failed = result as LocalShellResult.Failed
        assertEquals(LocalShellFailure.AUTH_REJECTED, failed.reason)
        assertEquals(4, failed.attempts, "đúng số lần của AWAIT_ADB_APPROVAL")
        assertEquals(listOf(1_000L, 2_000L, 4_000L), recorder.slept, "giãn cách nhân đôi")
        assertFalse(failed.commandDispatched, "hỏng ở bắt tay ⇒ chưa lệnh nào tới xe")
    }

    /**
     * LỊCH THẬT trên xe, không phải lịch của đồng hồ đứng yên: mỗi lần hỏng vì "adbd im lặng" tốn đúng
     * hạn đọc 6 s. Đây là con số phải xuất hiện trong doc/backlog — không phải 5 lần × 1/2/4/8 s.
     */
    @Test
    fun `hop thoai treo mai — 4 lan thu, gian cach 1-2-4 giay, khoang 31 giay dong ho tuong`(@TempDir dir: File) {
        val recorder = Recorder()
        val result = runVoiceKeyPath(dir, recorder, List(9) { awaitingApproval() })

        val failed = result as LocalShellResult.Failed
        assertEquals(LocalShellFailure.AWAITING_APPROVAL, failed.reason)
        assertEquals(4, failed.attempts, "vòng chờ dừng bằng attempts, không phụ thuộc mỗi lần thử tốn bao lâu")
        assertEquals(listOf(1_000L, 2_000L, 4_000L), recorder.slept)
        assertEquals(
            listOf(
                Triple(1, LocalShellFailure.AWAITING_APPROVAL, 1_000L),
                Triple(2, LocalShellFailure.AWAITING_APPROVAL, 2_000L),
                Triple(3, LocalShellFailure.AWAITING_APPROVAL, 4_000L),
            ),
            recorder.progress,
        )
        assertEquals(4 * 6_000L + 7_000L, recorder.nowMs, "4 lần × hạn đọc 6 s + tổng giãn cách 7 s = 31 s")
    }

    /**
     * Trần [LocalShellRetry.budgetMs] — nhánh mà bản 08-24 đầu tiên KHÔNG test nào đi qua, trong khi trên
     * xe nó chính là thứ kết thúc vòng lặp. Giữ một chính sách riêng (attempts lớn) để trần là thứ cắt.
     */
    @Test
    fun `tran thoi gian cat vong cho truoc khi het luot thu`(@TempDir dir: File) {
        val recorder = Recorder()
        val greedy = LocalShellRetry.AWAIT_ADB_APPROVAL.copy(attempts = 9)
        val result = runVoiceKeyPath(dir, recorder, List(9) { awaitingApproval() }, greedy)

        val failed = result as LocalShellResult.Failed
        assertEquals(4, failed.attempts, "6 s mỗi lần thử ⇒ lần thứ 5 sẽ vượt trần 30 s ⇒ dừng ở lần 4")
        assertEquals(listOf(1_000L, 2_000L, 4_000L), recorder.slept, "không ngủ thêm 8 s vì sẽ vượt trần")
        assertTrue(recorder.nowMs < 30_000L + 6_000L, "dừng quanh mốc trần, không chạy tới 9 lượt")
    }

    /**
     * Bài học 08-24 nặng nhất: hạn đọc 6 s làm cho một lệnh **đã gửi** có thể hỏng lúc đọc kết quả. Thử
     * lại lúc đó = phát lại lệnh: tài xế nhận nhiều lần KEYCODE_VOICE_ASSIST từ MỘT cú bấm, hoặc công thức
     * 12 lệnh của `setSystemAssistant` chạy lại từ đầu.
     */
    @Test
    fun `khong bao gio phat lai lenh da gui sang xe`(@TempDir dir: File) {
        val recorder = Recorder()
        val result = runVoiceKeyPath(
            dir,
            recorder,
            failures = listOf(null),
            shellFailures = listOf(SocketTimeoutException("Read timed out")),
        )

        val failed = result as LocalShellResult.Failed
        assertEquals(1, recorder.events.count { it == "shell:$voiceAssistCommand" }, "lệnh chỉ được gửi ĐÚNG một lần")
        assertEquals(1, failed.attempts, "đã gửi lệnh thì dừng, không thử lại")
        assertTrue(failed.commandDispatched, "bên gọi phải biết xe có thể đã thực thi một phần")
        assertTrue(recorder.slept.isEmpty(), "không ngủ chờ để phát lại")
        assertTrue(recorder.progress.isEmpty(), "không báo 'đang chờ cấp quyền' — quyền đã có, đã gửi được lệnh")
    }

    @Test
    fun `socket dut giua bat tay cung duoc cho — mot so ROM dong socket thay vi tra AUTH`(@TempDir dir: File) {
        val recorder = Recorder()
        val result = runVoiceKeyPath(
            dir,
            recorder,
            listOf(AdbConnectException("Connection handshake failed", EOFException()), null),
        )

        assertEquals(LocalShellResult.Ok(true, attempts = 2), result)
        assertEquals(listOf(Triple(1, LocalShellFailure.IO_ERROR, 1_000L)), recorder.progress)
    }

    @Test
    fun `chinh sach mac dinh giu nguyen hanh vi truoc 08-24 cho moi duong cu`(@TempDir dir: File) {
        val recorder = Recorder()
        // Đây là chính sách mà LocalDeviceShell.session truyền vào cho VietMapAutostart / UpdateChecker /
        // NavConnect / ClusterDiag — một lần thử, không hạn đọc, không bắt tay sớm.
        val result = runVoiceKeyPath(dir, recorder, List(3) { awaitingApproval() }, LocalShellRetry.NONE)

        assertEquals(1, (result as LocalShellResult.Failed).attempts, "đúng MỘT lần thử như trước")
        assertTrue(recorder.slept.isEmpty(), "không được làm đường cũ chậm đi")
        assertTrue(recorder.progress.isEmpty(), "không được sinh toast/log lạ cho đường cũ")
        assertEquals(listOf(0), recorder.socketTimeouts, "socketTimeout = 0 = đọc vô hạn, y như trước")
        assertTrue(recorder.events.none { it == "handshake" }, "KHÔNG bắt tay sớm ở đường cũ")
    }

    /**
     * F6 — đường NỀN (boot autostart / nav-connect / diag) chạy KHÔNG có owner đứng nhìn. Trước 2026-08-25
     * chúng dùng [LocalShellRetry.NONE] = đọc vô hạn ⇒ adbd im lặng chờ "Cho phép gỡ lỗi USB" thì phiên
     * TREO VĨNH VIỄN (nặng nhất: `VietMapAutostart.runNow` chạy ĐỒNG BỘ trong FGS boot ⇒ tiến trình treo
     * sau mỗi lần nổ máy). [LocalShellRetry.BACKGROUND_READ_CAP] cắt treo bằng hạn đọc 30 s mà KHÔNG thử lại.
     */
    @Test
    fun `F6 duong nen — han doc 30s cat treo, khong thu lai, khong phat lai lenh`(@TempDir dir: File) {
        val recorder = Recorder()
        // adbd im lặng mãi (hộp thoại cấp quyền treo) ở MỌI lần mở.
        val result = runVoiceKeyPath(dir, recorder, List(5) { awaitingApproval() }, LocalShellRetry.BACKGROUND_READ_CAP)

        val failed = result as LocalShellResult.Failed
        assertEquals(LocalShellFailure.AWAITING_APPROVAL, failed.reason, "phân loại được là đang chờ cấp quyền")
        assertEquals(1, failed.attempts, "đường nền KHÔNG thử lại — chỉ cắt treo rồi trả hỏng")
        assertEquals(
            listOf(30_000),
            recorder.socketTimeouts,
            "PHẢI đặt hạn đọc 30 s: đây là thứ DUY NHẤT biến treo-vĩnh-viễn thành lỗi phân loại được",
        )
        assertTrue(recorder.slept.isEmpty(), "không ngủ chờ (không owner nào đứng nhìn)")
        assertTrue(recorder.progress.isEmpty(), "không toast 'đang chờ' cho đường nền")
        assertFalse(failed.commandDispatched, "treo ở mở/bắt tay ⇒ chưa lệnh nào tới xe")
    }

    /**
     * BACKGROUND_READ_CAP phải GIỐNG [LocalShellRetry.NONE] ở mọi mặt TRỪ hạn đọc — nếu ai lỡ thêm retry /
     * eager-handshake vào nó thì đường nền sẽ tự phát lại lệnh (điều F2 cấm) hoặc đổi thứ tự bắt tay.
     */
    @Test
    fun `F6 BACKGROUND_READ_CAP chi khac NONE o han doc`() {
        val cap = LocalShellRetry.BACKGROUND_READ_CAP
        assertEquals(30_000, cap.socketTimeoutMs, "hạn đọc 30 s (đầu cao 20-30 s ở backlog F6)")
        assertEquals(1, cap.attempts, "1 lần thử như NONE — cắt treo, KHÔNG thử lại")
        assertTrue(cap.retryOn.isEmpty(), "không loại hỏng nào đáng thử lại ở đường nền")
        assertFalse(cap.eagerHandshake, "nối LƯỜI như đường cũ — không đổi thứ tự bắt tay")
        assertEquals(0L, cap.firstBackoffMs, "không giãn cách vì không thử lại")
    }

    /** Phiên nền chạy trơn vẫn phải xong bình thường, chỉ khác là mở với hạn đọc 30 s thay vì vô hạn. */
    @Test
    fun `F6 duong nen chay tron — xong binh thuong voi han doc 30s`(@TempDir dir: File) {
        val recorder = Recorder()
        val result = runVoiceKeyPath(
            dir,
            recorder,
            failures = listOf(null),
            retry = LocalShellRetry.BACKGROUND_READ_CAP,
        )

        assertEquals(LocalShellResult.Ok(true, attempts = 1), result, "mở được thì chạy lệnh xong ngay")
        assertEquals(listOf(30_000), recorder.socketTimeouts, "vẫn mở với hạn đọc 30 s")
        assertTrue(recorder.events.none { it == "handshake" }, "nối lười — KHÔNG bắt tay sớm (như NONE)")
        assertEquals(1, recorder.events.count { it == "shell:$voiceAssistCommand" }, "lệnh gửi đúng 1 lần")
    }

    @Test
    fun `chinh sach cho-cap-quyen dat han doc khac 0 — neu khong vong cho khong bao gio chay toi`() {
        val policy = LocalShellRetry.AWAIT_ADB_APPROVAL
        assertTrue(
            policy.socketTimeoutMs > 0,
            "đọc vô hạn thì phiên treo mãi ở readMessage() và không có lỗi nào để thử lại",
        )
        assertTrue(
            policy.eagerHandshake,
            "dadb nối LƯỜI — không ép bắt tay thì lỗi auth nổ SAU lệnh đầu, mà lệnh đã gửi thì cấm thử lại",
        )
        assertTrue(LocalShellFailure.PORT_CLOSED !in policy.retryOn, "cổng đóng thì chờ vô ích")
        assertEquals(4, policy.attempts, "số lần thử phải là số xe chạy được, xem lịch ở KDoc chính sách")
        // Trần giãn cách phải hoặc có tác dụng, hoặc không tồn tại. Với 4 lần thử, giãn cách lớn nhất
        // THỰC SỰ dùng là backoffAfterMs(3) = 4 s ⇒ một trần 8 s chỉ là con số chết gây hiểu nhầm.
        assertEquals(0L, policy.maxBackoffMs, "không giữ trần chết")
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L),
            (1..policy.attempts - 1).map { policy.backoffAfterMs(it) },
            "toàn bộ giãn cách thực sự dùng được",
        )
    }

    /** Trần giãn cách là máy móc dùng chung — chính sách khác có thể cần. Giữ nó có test để không mục. */
    @Test
    fun `tran gian cach chan viec nhan doi vo han`() {
        val capped = LocalShellRetry(attempts = 6, firstBackoffMs = 1_000L, maxBackoffMs = 3_000L)
        assertEquals(
            listOf(1_000L, 2_000L, 3_000L, 3_000L, 3_000L),
            (1..5).map { capped.backoffAfterMs(it) },
        )
        assertEquals(0L, LocalShellRetry(attempts = 2).backoffAfterMs(1), "không đặt giãn cách thì không chờ")
    }

    @Test
    fun `phan loai lan theo chuoi cause va khong ket vong`() {
        assertEquals(
            LocalShellFailure.AUTH_REJECTED,
            LocalShellFailures.classify(RuntimeException("bọc ngoài", rejected())),
            "lỗi auth nằm sâu trong chuỗi vẫn phải nhận ra",
        )
        assertEquals(
            LocalShellFailure.AWAITING_APPROVAL,
            LocalShellFailures.classify(awaitingApproval()),
            "hạn đọc = adbd đang im lặng chờ người dùng bấm",
        )
        assertEquals(LocalShellFailure.PORT_CLOSED, LocalShellFailures.classify(portClosed()))
        assertEquals(LocalShellFailure.UNKNOWN, LocalShellFailures.classify(null))
        assertEquals(
            LocalShellFailure.UNKNOWN,
            LocalShellFailures.classify(IllegalStateException("không phải lỗi vào-ra")),
        )

        // Chuỗi cause tự trỏ về nhau: phải dừng, không được treo test.
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertEquals(LocalShellFailure.UNKNOWN, LocalShellFailures.classify(a))
    }

    @Test
    fun `bi ngat trong luc cho thi dung han va giu co ngat`(@TempDir dir: File) {
        val recorder = Recorder()
        val result = LocalShellSessions.run(
            connector = FakeConnector(recorder, List(5) { awaitingApproval() }),
            keys = keys(dir),
            retry = LocalShellRetry.AWAIT_ADB_APPROVAL,
            onProgress = { _, _, _ -> },
            nowMs = { recorder.nowMs },
            sleepMs = { throw InterruptedException("thread bị ngắt") },
        ) { sh -> sh("input keyevent 231").exitCode == 0 }

        assertEquals(1, (result as LocalShellResult.Failed).attempts, "dừng ngay khi bị ngắt")
        assertTrue(Thread.interrupted(), "cờ ngắt phải được đặt lại cho bên gọi (và test tự dọn)")
        assertNull(recorder.events.firstOrNull { it.startsWith("shell:") }, "không lệnh nào được gửi")
    }

    /**
     * Ngắt xảy ra TRONG block, **sau khi đã gửi lệnh** — dựng đúng hình dạng của
     * `AssistantLauncher.setSystemAssistant`: `sh(...)` rồi `Thread.sleep(300)` giữa công thức.
     *
     * Hai bẫy trong một ca: `runCatching` nuốt `InterruptedException` và việc ném nó đã xoá cờ ngắt; còn
     * nhánh "đã gửi lệnh thì dừng" nếu đặt trước lại cướp mất chỗ khôi phục cờ. Cả hai phải cùng đúng.
     */
    @Test
    fun `bi ngat trong block sau khi da gui lenh — van tra lai co ngat`(@TempDir dir: File) {
        val recorder = Recorder()
        val result = LocalShellSessions.run<Boolean>(
            connector = FakeConnector(recorder, List(4) { null }),
            keys = keys(dir),
            retry = LocalShellRetry.AWAIT_ADB_APPROVAL,
            onProgress = { attempt, reason, waitMs -> recorder.progress += Triple(attempt, reason, waitMs) },
            nowMs = { recorder.nowMs },
            sleepMs = { ms -> recorder.slept += ms; recorder.nowMs += ms },
        ) { sh ->
            sh("settings put secure voice_interaction_service ''")
            throw InterruptedException("owner tắt app giữa lúc chạy công thức")
        }

        val failed = result as LocalShellResult.Failed
        assertEquals(1, failed.attempts, "bị ngắt thì dừng, không thử lại ~31 s")
        assertTrue(failed.commandDispatched, "đã gửi lệnh ⇒ bên gọi phải biết công thức chạy dở")
        assertTrue(Thread.interrupted(), "cờ ngắt phải được trả lại cho bên gọi (và test tự dọn)")
        assertTrue(recorder.slept.isEmpty())
    }
}
