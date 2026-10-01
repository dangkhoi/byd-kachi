package com.byd.clusternav.carexec

import dadb.AdbAuthException
import dadb.AdbKeyPair
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

// `enum class LocalShellFailure` (từ vựng lý do hỏng — thuần) nằm ở `:core` `carexec/LocalShellFailure.kt` (READY-AT-HOME:
// chính sách thuần của kênh mức tiến trình `ShellReadinessPolicy` dùng nó, và luật Q1 đặt mọi tệp thuần ở `:core`).
// Bộ phân loại [LocalShellFailures.classify] ở lại đây vì nó đọc đúng loại ngoại lệ của dadb.

/** Phân loại lỗi transport thành [LocalShellFailure]. Thuần, không đụng thiết bị ⇒ test off-car được. */
object LocalShellFailures {

    /** Trần độ sâu khi lần theo `cause` — vừa chặn chuỗi vòng, vừa chặn chuỗi dài bất thường. */
    private const val MAX_CAUSE_DEPTH = 8

    /**
     * Ưu tiên theo mức ĐẶC THÙ, không theo vị trí trong chuỗi: dadb bọc lỗi thật vào
     * `AdbConnectException("Connection handshake failed", <lỗi thật>)`, nên lỗi ở tầng ngoài luôn chung
     * chung hơn lỗi bên trong.
     *
     * ⚠ [AWAITING_APPROVAL] chỉ đúng khi **connect-timeout = 0** (xem [LocalShellRetry.socketTimeoutMs]):
     * lúc đó `SocketTimeoutException` chỉ có thể sinh ra từ một lần ĐỌC, không thể từ lúc bắt tay TCP.
     */
    fun classify(error: Throwable?): LocalShellFailure {
        val chain = causeChain(error)
        return when {
            chain.any { it is AdbAuthException } -> LocalShellFailure.AUTH_REJECTED
            chain.any { it is SocketTimeoutException } -> LocalShellFailure.AWAITING_APPROVAL
            chain.any { it is ConnectException } -> LocalShellFailure.PORT_CLOSED
            chain.any { it is IOException } -> LocalShellFailure.IO_ERROR
            else -> LocalShellFailure.UNKNOWN
        }
    }

    /**
     * Luồng đã bị yêu cầu dừng (ngoại lệ ngắt nằm đâu đó trong chuỗi `cause`).
     *
     * Cần vì `runCatching` nuốt `InterruptedException` **và** việc ném nó đã xoá cờ ngắt của luồng: nếu
     * không đặt lại thì bên gọi (luồng rời của phím mic, FGS boot) không còn biết mình đã bị ngắt. Ca thật:
     * `AssistantLauncher.setSystemAssistant` có `Thread.sleep(300)` NẰM TRONG block.
     */
    fun wasInterrupted(error: Throwable?): Boolean = causeChain(error).any { it is InterruptedException }

    private fun causeChain(error: Throwable?): List<Throwable> {
        val chain = ArrayList<Throwable>(MAX_CAUSE_DEPTH)
        var current = error
        while (current != null && chain.size < MAX_CAUSE_DEPTH && chain.none { it === current }) {
            chain += current
            current = current.cause
        }
        return chain
    }
}

/**
 * Chính sách thử lại cho một phiên [LocalDeviceShell].
 *
 * Mặc định = [NONE] = **đúng hành vi trước 2026-08-24** (một lần thử, đọc vô hạn, không bắt tay sớm) để
 * mọi đường đang chạy tốt ngoài hiện trường không bị đụng — `VietMapAutostart`, `UpdateChecker`,
 * `NavConnect`, `ClusterDiag`, `VietMapWidgetDiagActivity` (CLAUDE.md §6: đường mới xuống cuối, đường cũ
 * giữ nguyên). Bên gọi nào cần chờ thì phải nói ra tường minh.
 *
 * @param attempts        tổng số lần thử, kể cả lần đầu. 1 = không thử lại.
 * @param firstBackoffMs  giãn cách sau lần hỏng đầu; các lần sau nhân đôi.
 * @param maxBackoffMs    trần cho một lần chờ.
 * @param budgetMs        trần cho TỔNG thời gian (tính từ lúc bắt đầu); 0 = không trần.
 * @param socketTimeoutMs hạn đọc socket (`Socket.setSoTimeout`). 0 = vô hạn (hành vi cũ). Khác 0 là thứ
 *   DUY NHẤT biến "adbd im lặng chờ người dùng bấm" từ **treo vĩnh viễn** thành một lỗi phân loại được
 *   ([LocalShellFailure.AWAITING_APPROVAL]) — không có nó thì vòng thử lại không bao giờ chạy tới.
 *   ⚠ Hạn này áp cho MỌI lần đọc trong phiên, kể cả lúc đọc kết quả lệnh shell ⇒ chỉ bật cho phiên toàn
 *   lệnh ngắn. Nổ giữa một chuỗi lệnh thì phiên dừng ở đó, **không** chạy lại (xem cờ `dispatched` trong
 *   [LocalShellSessions.run]) ⇒ thiết bị có thể còn ở trạng thái nửa vời; bên gọi phải nói điều đó ra qua
 *   [LocalShellResult.Failed.commandDispatched]. Connect-timeout luôn giữ 0 để `SocketTimeoutException`
 *   không lẫn với lỗi bắt tay TCP.
 * @param eagerHandshake  ép bắt tay ADB xong TRƯỚC khi chạy block. Cần khi có thử lại: dadb nối **lười**
 *   ([ĐO] bytecode 2.0.0 — `DadbImpl.supportsFeature`/`open` đều gọi `connection()`, và `connection()` chỉ
 *   dựng socket ở lần đầu), nên không ép thì lỗi auth nổ ở GIỮA block, tức SAU khi lệnh đầu đã gửi — mà
 *   lệnh đã gửi thì [LocalShellSessions.run] cấm thử lại, vòng chờ coi như vô hiệu. Nói cách khác cờ này
 *   quyết định vòng chờ có CHẠY được không; nó KHÔNG phải thứ ngăn phát lại lệnh (cờ `dispatched` trong
 *   [LocalShellSessions.run] mới là thứ đó). Mặc định false = giữ nguyên đường cũ.
 * @param retryOn         những loại hỏng đáng chờ. [LocalShellFailure.PORT_CLOSED] cố ý KHÔNG có mặt.
 */
data class LocalShellRetry(
    val attempts: Int = 1,
    val firstBackoffMs: Long = 0L,
    val maxBackoffMs: Long = 0L,
    val budgetMs: Long = 0L,
    val socketTimeoutMs: Int = 0,
    val eagerHandshake: Boolean = false,
    val retryOn: Set<LocalShellFailure> = emptySet(),
    /**
     * READY-AT-HOME (spec §4.6) — phiên này là đường HỎI người dùng: chỉ chạy khi người dùng đứng trước Kachi (F4 khi
     * màn chính có tiêu điểm; nút bấm). Cổng thi hành ([LocalShellAdmission]) LUÔN cho qua. Mặc định `false` = phiên
     * NỀN ⇒ mọi chỗ dựng [LocalShellRetry] hiện có không đổi hành vi khi kênh đã lên.
     */
    val mayPromptUser: Boolean = false,
) {
    init {
        require(attempts >= 1) { "attempts phải >= 1, nhận $attempts" }
        require(firstBackoffMs >= 0 && maxBackoffMs >= 0 && budgetMs >= 0) { "thời lượng không được âm" }
        require(socketTimeoutMs >= 0) { "socketTimeoutMs không được âm" }
    }

    /** Chờ bao lâu sau lần hỏng thứ [attempt] (1-based): nhân đôi dần, chặn trần [maxBackoffMs]. */
    fun backoffAfterMs(attempt: Int): Long {
        if (firstBackoffMs <= 0L) return 0L
        val shift = (attempt - 1).coerceIn(0, MAX_DOUBLING)
        val raw = firstBackoffMs shl shift
        return if (maxBackoffMs > 0L) raw.coerceAtMost(maxBackoffMs) else raw
    }

    companion object {
        private const val MAX_DOUBLING = 20

        /** Hành vi trước 2026-08-24: thử một lần, hỏng thì thôi. Mặc định cho MỌI bên gọi cũ. */
        val NONE = LocalShellRetry()

        /**
         * **Chụp mũ chống-treo cho đường NỀN** (F6, 2026-08-25) — không owner nào đứng nhìn.
         *
         * Giống [NONE] ở MỌI mặt (1 lần thử, không thử lại, nối lười) TRỪ một điều: đặt hạn đọc 30 s để
         * `Dadb.create(...)` với `socketTimeout = 0` không còn **treo VĨNH VIỄN** khi adbd im lặng chờ
         * người dùng bấm "Cho phép gỡ lỗi USB" (F6 [ĐO]: `VietMapAutostart.runNow` chạy ĐỒNG BỘ trong
         * `BootSetupService` ⇒ FGS không bao giờ `finish()` ⇒ tiến trình treo sau mỗi lần nổ máy nếu khoá
         * adb chưa cấp). 30 s là **im lặng hoàn toàn** — `Socket.setSoTimeout` áp cho từng lần `read()`, nên
         * lệnh shell chậm-mà-vẫn-chảy-dữ-liệu (dump dài) KHÔNG bị cắt; chỉ một socket **câm suốt 30 s** mới
         * nổ, mà đó đúng là định nghĩa treo. Chọn 30 s (đầu cao khoảng 20–30 s ở backlog F6) để dư sức cho
         * lệnh nền chậm nhất.
         *
         * KHÁC [AWAIT_ADB_APPROVAL]: cái kia CHỜ + thử lại vì owner vừa ra lệnh và đang nhìn; cái này chỉ
         * **cắt treo** rồi trả hỏng — đường nền không nên tự phát lại lệnh (xem cờ `dispatched`), và không
         * có ai để mà chờ. `retryOn` rỗng ⇒ hỏng là dừng ngay ở lần 1.
         */
        val BACKGROUND_READ_CAP = LocalShellRetry(
            attempts = 1,
            socketTimeoutMs = 30_000,
            retryOn = emptySet(),
        )

        /**
         * READY-AT-HOME (spec R1.1) — y hệt [BACKGROUND_READ_CAP] (một lần thử, hạn đọc 30 s, nối lười) nhưng là đường
         * HỎI: người dùng VỪA bấm (nút *Kiểm tra / Sửa ngay* của phím vô-lăng) nên hộp "Cho phép gỡ lỗi USB?" bung ra
         * đúng lúc họ đang nhìn — một trong hai nơi R1.1 cho phép. 30 s đủ để bấm Cho phép trong cùng phiên. Cũng là
         * phiên của nút *Hỏi lại* trên thẻ xin quyền khi màn chính ĐÃ nối dây (`ShellApprovalProbe.askUser`, review lượt 1).
         */
        val USER_READ_CAP = BACKGROUND_READ_CAP.copy(mayPromptUser = true)

        /**
         * Chờ owner bấm "Cho phép gỡ lỗi USB" — dùng cho các đường mà **owner đang đứng trước xe và vừa
         * ra lệnh** (giữ phím mic, chọn trợ lý trong app), tức lúc hộp thoại bung ra là lúc owner đang
         * nhìn màn hình.
         *
         * **Lịch thật trên xe** (mỗi lần hỏng tốn đúng [socketTimeoutMs] = 6 s vì adbd im lặng):
         * ```
         * t=0  thử 1 ─6s─ hỏng ─ngủ 1s─ t=7  thử 2 ─6s─ hỏng ─ngủ 2s─ t=15 thử 3 ─6s─ hỏng ─ngủ 4s─
         * t=25 thử 4 ─6s─ hỏng → dừng (hết [attempts])            ⇒ 4 lần thử, ~31 s đồng hồ tường
         * ```
         * `attempts = 4` được chọn để lịch này **giống nhau dù mỗi lần thử tốn bao lâu**: bản vá đầu của
         * 2026-08-24 ghi `attempts = 5`, nhưng khi mỗi lần thử tốn 6 s thì [budgetMs] cắt ở lần 4 — trong
         * khi test (đồng hồ giả đứng yên suốt lúc thử) lại khẳng định 5 lần × 1/2/4/8 s. Số trong doc/test
         * phải là số xe chạy, không phải số đồng hồ giả chạy.
         *
         * [budgetMs] giữ vai trò **lưới an toàn**, không phải lịch: một lần thử tốn hơn ~7,7 s (ROM chậm,
         * hạn đọc không nổ đúng hạn) thì vòng dừng sớm ở lần 3 thay vì 4. Không có [maxBackoffMs] vì với 4
         * lần thử, giãn cách lớn nhất thực sự dùng là 4 s — đặt trần 8 s là một con số chết, gây hiểu nhầm.
         *
         * [LocalShellFailure.PORT_CLOSED] KHÔNG thử lại: nếu không có gì lắng nghe cổng 5555 thì 30 giây
         * nữa cũng không có, và owner cần biết ngay là "adb-tcp chưa bật" chứ không phải ngồi chờ.
         *
         * ⚠ [LocalShellFailure.AUTH_REJECTED] CÓ thử lại, và đây là đánh đổi **có ý thức, chưa đo trên xe**:
         *  - giữ lại: nếu ROM BYD trả `AUTH` ngay khi thấy khoá lạ (thay vì im lặng chờ người bấm) thì đó
         *    CHÍNH LÀ chữ ký của ca F2 — bỏ thử lại là bỏ luôn bản vá.
         *  - cái giá: nếu owner bấm "Từ chối" thật thì bị hỏi thêm tối đa 3 lần trong ~31 s.
         *  Chốt bằng `getprop ro.adb.secure` + một lần bấm Từ chối trên xe (xem
         *  `docs/diagnostics/voicekey-adb-approval-first-open-2026-08-24.md` §5).
         */
        val AWAIT_ADB_APPROVAL = LocalShellRetry(
            attempts = 4,
            firstBackoffMs = 1_000L,
            budgetMs = 30_000L,
            socketTimeoutMs = 6_000,
            eagerHandshake = true,
            retryOn = setOf(
                LocalShellFailure.AWAITING_APPROVAL,
                LocalShellFailure.AUTH_REJECTED,
                LocalShellFailure.IO_ERROR,
            ),
            mayPromptUser = true,   // READY-AT-HOME: owner đứng trước xe và vừa ra lệnh — đường HỎI, cổng luôn cho
        )
    }
}

/** READY-AT-HOME §4.6 — loại phiên cho cổng thi hành: chỉ chính sách tự khai [LocalShellRetry.mayPromptUser] là đường HỎI. */
internal fun LocalShellRetry.sessionKind(): ShellSessionKind =
    if (mayPromptUser) ShellSessionKind.ASK else ShellSessionKind.BACKGROUND

/** Kết quả một phiên [LocalDeviceShell] — thay cho `T?` để bên gọi biết hỏng vì cái gì. */
sealed interface LocalShellResult<out T> {
    /** Phiên mở được và block chạy xong; [attempts] = số lần đã thử (1 = ăn ngay). */
    data class Ok<out T>(val value: T, val attempts: Int) : LocalShellResult<T>

    /**
     * Không mở được (hoặc block ném). [cause] giữ nguyên lỗi gốc cho log.
     *
     * @param commandDispatched đã có ÍT NHẤT MỘT lệnh được đẩy sang adbd trước khi hỏng ⇒ thiết bị có thể
     *   đã thực thi một phần chuỗi lệnh. Bên gọi nào chạy công thức nhiều bước (`setSystemAssistant`) phải
     *   nói điều này ra cho owner: "mới chạy được một phần", chứ không phải "không làm gì cả".
     */
    data class Failed(
        val reason: LocalShellFailure,
        val attempts: Int,
        val cause: Throwable?,
        val commandDispatched: Boolean = false,
    ) : LocalShellResult<Nothing>
}

/** Mở một phiên tới head unit. Production = dadb loopback; test tiêm bản giả. */
internal interface LocalShellConnector {
    /** @throws Throwable đúng loại transport ném, để [LocalShellFailures.classify] phân loại được. */
    fun open(keys: AdbKeyPair, socketTimeoutMs: Int): LocalShellConnection
}

/** Một phiên đang mở. */
internal interface LocalShellConnection : AutoCloseable {
    /** Ép hoàn tất bắt tay ADB (dadb nối lười) để lỗi auth lộ ra TRƯỚC khi block gửi lệnh nào. */
    fun handshake()

    fun shell(command: String): LocalShellText

    override fun close()
}

/**
 * Vòng thử lại. Tách khỏi [LocalDeviceShell] để chạy được off-car với connector/đồng hồ/giấc ngủ tiêm vào
 * — đúng vòng lặp mà đường phím mic chạy trên xe, không phải một hàm phụ dựng riêng cho test.
 */
internal object LocalShellSessions {

    /**
     * @param onProgress gọi TRƯỚC mỗi lần chờ: (lần thử vừa hỏng, lý do, sẽ chờ bao nhiêu ms).
     * @param sleepMs    seam ngủ; bị ngắt thì ném `InterruptedException` như `Thread.sleep`.
     */
    fun <T> run(
        connector: LocalShellConnector,
        keys: AdbKeyPair,
        retry: LocalShellRetry,
        onProgress: (Int, LocalShellFailure, Long) -> Unit,
        nowMs: () -> Long,
        sleepMs: (Long) -> Unit,
        block: (shell: (String) -> LocalShellText) -> T,
    ): LocalShellResult<T> {
        // READY-AT-HOME (spec §4.6) — cổng thi hành, TRƯỚC khi mở socket: bị chặn thì không có kết nối nào ⇒ adbd không
        // thấy khoá ⇒ không bung hộp "Cho phép gỡ lỗi USB?" từ nền (R1.1). `attempts = 0` = chưa thử lần nào.
        val kind = retry.sessionKind()
        if (!LocalShellAdmission.admit(kind)) {
            return LocalShellResult.Failed(LocalShellFailure.NOT_APPROVED, attempts = 0, cause = null)
        }
        val startedAt = nowMs()
        var attempt = 0
        var reason = LocalShellFailure.UNKNOWN
        var cause: Throwable? = null
        var dispatched = false
        // Bắt tay ADB đã XONG ở một lần thử nào đó (ép sớm, hoặc lệnh đầu đã trả về) ⇒ khoá ĐƯỢC NHẬN — sự thật đo được
        // mà tầng app dùng để biết kênh đã lên (READY-AT-HOME §4.6 "báo kết quả ngược lên").
        var handshook = false

        while (true) {
            attempt++
            // runCatching (Throwable) — giữ NGUYÊN độ rộng bắt lỗi của đường cũ: một phiên adb hỏng không
            // bao giờ được phép làm chết luồng gọi (phím mic chạy trên thread rời, boot chạy trong FGS).
            val outcome = runCatching {
                connector.open(keys, retry.socketTimeoutMs).use { connection ->
                    if (retry.eagerHandshake) { connection.handshake(); handshook = true }
                    block { command ->
                        // Đặt cờ TRƯỚC khi gọi: lệnh đã được ghi ra socket rồi mới tới lượt đọc kết quả,
                        // nên hạn đọc nổ ở đây KHÔNG có nghĩa là thiết bị chưa nhận lệnh.
                        dispatched = true
                        connection.shell(command).also { handshook = true }
                    }
                }
            }
            if (outcome.isSuccess) {
                LocalShellAdmission.report(kind, handshook, null, dispatched, retry.eagerHandshake)
                // `as T` chứ không phải `getOrNull()?.let{}`: block ĐƯỢC PHÉP trả null (đường cũ trả `T?`),
                // và null hợp lệ vẫn là THÀNH CÔNG — không được rơi xuống nhánh thử lại.
                @Suppress("UNCHECKED_CAST")
                return LocalShellResult.Ok(outcome.getOrNull() as T, attempt)
            }

            cause = outcome.exceptionOrNull()
            reason = LocalShellFailures.classify(cause)

            // Bị ngắt ⇒ luồng gọi đang được yêu cầu dừng: không ngồi thử lại thêm ~31 s nữa, và phải trả
            // lại cờ ngắt mà `runCatching` vừa nuốt (ném InterruptedException đã xoá cờ).
            // ⚠ Phải kiểm TRƯỚC nhánh `dispatched`: ca thật của `setSystemAssistant` là gửi lệnh XONG rồi
            // mới ngủ 300 ms và bị ngắt trong lúc ngủ — nếu để `dispatched` dừng trước thì cờ ngắt mất.
            val interrupted = LocalShellFailures.wasInterrupted(cause)
            if (interrupted) Thread.currentThread().interrupt()

            // ĐIỀU KIỆN DỪNG SỐ MỘT — an toàn đứng trên đủ tính năng (CLAUDE.md: đúng > an toàn > nhanh).
            // Đã đẩy được một lệnh sang adbd thì thử lại = PHÁT LẠI lệnh đó. Trên đường phím mic nghĩa là
            // tài xế nhận nhiều lần KEYCODE_VOICE_ASSIST từ MỘT cú bấm; trên đường setSystemAssistant nghĩa
            // là công thức 12 lệnh chạy lại từ đầu. Hạn đọc 6 s ([LocalShellRetry.socketTimeoutMs]) làm ca
            // này khả thi lần đầu — trước 2026-08-24 đọc vô hạn nên không lệnh nào hỏng-sau-khi-gửi được.
            // [LocalShellRetry.eagerHandshake] chỉ dời lỗi AUTH ra TRƯỚC block; nó không bảo vệ được nửa
            // sau (lỗi xảy ra lúc đọc kết quả lệnh), nên phải có cờ này mới đủ.
            if (dispatched) break
            if (interrupted) break

            if (attempt >= retry.attempts) break
            if (reason !in retry.retryOn) break

            val wait = retry.backoffAfterMs(attempt)
            if (retry.budgetMs > 0L && (nowMs() - startedAt) + wait >= retry.budgetMs) break

            onProgress(attempt, reason, wait)
            try {
                sleepMs(wait)
            } catch (interrupted: InterruptedException) {
                // Không nuốt cờ ngắt: bên gọi (thread rời của phím mic) phải còn thấy được nó.
                Thread.currentThread().interrupt()
                cause = interrupted
                break
            }
        }
        LocalShellAdmission.report(kind, handshook, reason, dispatched, retry.eagerHandshake)
        return LocalShellResult.Failed(reason, attempt, cause, commandDispatched = dispatched)
    }
}
