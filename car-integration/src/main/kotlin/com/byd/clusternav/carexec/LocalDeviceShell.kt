package com.byd.clusternav.carexec

import com.byd.clusternav.launcher.HomeActivityCmd
import dadb.AdbKeyPair
import dadb.Dadb
import dadb.InstallResult
import java.io.File

/**
 * Một phiên adb ngắn tới chính head unit đang chạy app, cho các việc lẻ: tự cấp quyền, tự chữa listener,
 * cài bản cập nhật, đọc chẩn đoán.
 *
 * Vì sao tập trung: trước 2026-07-27 có **13 chỗ** trong app tự gọi `Dadb.create("localhost", 5555, ...)`.
 * Mỗi chỗ là một đường ra thiết bị mà kiến trúc không thấy, nên không ai kiểm được ai đang nói gì với máy.
 * Kiến trúc nói mọi transport thuộc `:car-integration`; đây là chỗ đó.
 *
 * KHÔNG dùng cho Cluster Cast. Cast đi qua `CastAdbGateway` vì mọi lệnh của nó phải truy được về một giao
 * dịch có fence và deadline; helper này thì không có gì bảo vệ, đúng cho việc lẻ và sai cho việc có journal.
 */
/** Output một lệnh, giữ nguyên stdout và stderr riêng để caller tự định dạng như trước. */
data class LocalShellText(val output: String, val errorOutput: String, val exitCode: Int) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * ═══ U11 — KẾT QUẢ CÀI APK CÓ **LÝ DO**, KHÔNG CÒN MỘT CHỮ `false` CÂM ═══════════════════════════════════════
 *
 * ## Bệnh
 * [LocalDeviceShell.installApk] trước đây trả `Boolean`, nên `UpdateChecker.install` chỉ nói được **một** câu cho
 * mọi thất bại: *"cài thất bại (khác chữ ký/phiên bản?)"*. **[ĐO] máy ảo 2026-09-13** (backlog L2): máy ảo thiếu
 * `adb reverse tcp:5555 tcp:5555` ⇒ **không có kênh dadb nào** để mở, mà người dùng lại đọc được câu đổ cho chữ
 * ký — đi sửa nhầm bệnh. Hai nguyên nhân này cần hai việc khác hẳn nhau (bật kênh shell vs đổi/gỡ bản đang cài).
 *
 * ## Hai nhánh, tách theo ĐIỂM HỎNG chứ không theo phỏng đoán
 *  • [NoShellChannel] — hỏng **trước khi** `pm` nhìn thấy APK: không mở/bắt tay được phiên tới `localhost:5555`
 *    (chưa cấp "Cho phép gỡ lỗi USB", cổng 5555 chưa bật, máy ảo thiếu reverse). [reason] lấy từ chính bộ phân
 *    loại đã có ([LocalShellFailures.classify]) — không thêm bảng phân loại thứ hai.
 *  • [PmRejected] — kênh shell ĐÃ lên, `pm install` chạy và **từ chối**; [pmOutput] là output thật của pm
 *    (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `INSTALL_FAILED_VERSION_DOWNGRADE`…), tức câu trả lời có thật thay
 *    cho dấu hỏi.
 *
 * ## ⚠ [ĐO] bytecode `dadb-2.0.0.jar` — vì sao nhánh [PmRejected] mới tồn tại được
 * `Dadb.install(File, vararg)` trả **`InstallResult`** (`Success` | `Failure(reason)`), **KHÔNG ném** khi pm từ
 * chối: `Dadb$DefaultImpls.pmInstall` đọc `AdbShellResponse.allOutput` rồi `startsWith("Success")` — sai thì gói
 * nguyên output vào `InstallResult$Failure`. Đường cũ `adb.use { it.install(...) }; true` **vứt giá trị trả về**,
 * nên một lần pm từ chối (đúng ca "khác chữ ký" mà câu chữ cũ nói tới!) lại báo **thành công** và app hẹn giờ mở
 * lại như thường. Nhánh này vì thế vừa sửa câu chữ vừa sửa một khẳng định sai.
 */
sealed interface LocalInstallOutcome {

    /** pm trả `Success`. */
    object Ok : LocalInstallOutcome

    /** Không mở được kênh shell tới `localhost:5555` — `pm` chưa từng thấy APK. */
    data class NoShellChannel(val reason: LocalShellFailure) : LocalInstallOutcome

    /** Kênh lên rồi, `pm install` từ chối. [pmOutput] = output thô của pm (có thể rỗng nếu pm câm). */
    data class PmRejected(val pmOutput: String) : LocalInstallOutcome
}

/**
 * ═══ S5 — KẾT QUẢ đặt màn hình chính, tách theo ĐIỂM HỎNG (khớp mẫu [LocalInstallOutcome]) ════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §9 (S5). Hai nguyên nhân hỏng cần hai VIỆC PHẢI LÀM khác nhau — cùng
 * bài học U11 với `pm install`:
 *  • [NoShellChannel] — hỏng **trước khi** `cmd package` chạy: không mở/bắt tay được phiên tới `localhost:5555`
 *    (chưa cấp "Cho phép gỡ lỗi USB", cổng 5555 chưa bật). Việc cần làm nằm ở KÊNH SHELL (hàng *Kênh điều khiển
 *    cửa sổ*), không phải ở HOME.
 *  • [Failed] — kênh ĐÃ lên, `set-home-activity` chạy nhưng đọc lại `resolve-activity` **vẫn không phải Kachi**.
 *    [resolveOutput] là output thật của resolve để chẩn đoán (ROM chặn? một launcher khác giành lại?).
 */
sealed interface LocalSetHomeOutcome {

    /** `set-home-activity` chạy và `resolve-activity` xác nhận HOME đã là component đích. */
    object Ok : LocalSetHomeOutcome

    /** Không mở được kênh shell tới `localhost:5555` — lệnh chưa từng chạy. */
    data class NoShellChannel(val reason: LocalShellFailure) : LocalSetHomeOutcome

    /** Lệnh chạy rồi nhưng đọc lại HOME vẫn khác — [resolveOutput] = output thô của resolve-activity. */
    data class Failed(val resolveOutput: String) : LocalSetHomeOutcome
}

object LocalDeviceShell {

    private const val HOST = "localhost"
    private const val PORT = 5555

    /** Chạy một lệnh, trả về stdout+stderr đã trim, hoặc null nếu không nối được. */
    fun run(keys: AdbKeyPair, command: String): String? = runCatching {
        Dadb.create(HOST, PORT, keys).use { adb -> (adb.shell(command).allOutput ?: "").trim() }
    }.getOrNull()

    /** Chạy nhiều lệnh trên cùng một phiên; trả về danh sách output theo thứ tự, hoặc null nếu phiên lỗi. */
    fun runAll(keys: AdbKeyPair, commands: List<String>): List<String>? = runCatching {
        Dadb.create(HOST, PORT, keys).use { adb ->
            commands.map { (adb.shell(it).allOutput ?: "").trim() }
        }
    }.getOrNull()

    /**
     * Mở một phiên và trao vào một hàm chạy lệnh.
     *
     * Có mặt vì hai call site cần **trình tự trên cùng một phiên**: `NavConnect` ngủ 1.5 giây giữa
     * disallow và allow listener, `ClusterDiag` chạy hàng chục lệnh và tự ghép stdout với stderr. Tách
     * thành nhiều phiên rời sẽ đổi hành vi của một đường tự-chữa vốn đã mong manh — mà giai đoạn này
     * không được đổi hành vi.
     *
     * Hỏng ⇒ `null`, KHÔNG thử lại. Mặc định [retry] = [LocalShellRetry.NONE] = **không hạn đọc** =
     * nguyên vẹn hành vi trước 2026-08-24. Đường NỀN (boot autostart / nav-connect / diag) truyền
     * [LocalShellRetry.BACKGROUND_READ_CAP] để một socket câm không treo vĩnh viễn (F6). Bên gọi nào cần
     * CHỜ owner bấm "Cho phép gỡ lỗi USB" phải nói ra tường minh qua [sessionResult] với
     * [LocalShellRetry.AWAIT_ADB_APPROVAL].
     */
    fun <T> session(
        keys: AdbKeyPair,
        retry: LocalShellRetry = LocalShellRetry.NONE,
        block: (shell: (String) -> LocalShellText) -> T,
    ): T? =
        when (val result = sessionResult(keys, retry, block = block)) {
            // Block trả `null` hợp lệ vẫn ra `null` ở đây — đúng như đường cũ, bên gọi không phân biệt được
            // "chạy xong, kết quả null" với "phiên hỏng". Ai cần phân biệt thì dùng [sessionResult].
            is LocalShellResult.Ok -> result.value
            is LocalShellResult.Failed -> null
        }

    /**
     * Như [session] nhưng (a) nhận [retry] để chờ owner cấp quyền adb, và (b) trả về **lý do hỏng** thay
     * cho một chữ `null` câm.
     *
     * Sinh ra 2026-08-24 cho F2 (owner: *"start app vẫn chưa hold mic gọi gemini/kiki được, phải tắt, mở
     * lại thì mới xin được quyền"*): lần đầu app nối tới `localhost:5555` bằng khoá mới sinh, head unit
     * bung hộp thoại "Cho phép gỡ lỗi USB?" và phiên đó hỏng (hoặc treo) — không ai thử lại, không ai nói
     * gì, nên owner chỉ còn cách tắt app mở lại. Xem [LocalShellFailure] cho bằng chứng bytecode dadb.
     *
     * @param onProgress báo cho tầng UI TRƯỚC mỗi lần chờ: (lần thử vừa hỏng, lý do, sẽ chờ bao nhiêu ms).
     */
    fun <T> sessionResult(
        keys: AdbKeyPair,
        retry: LocalShellRetry = LocalShellRetry.NONE,
        onProgress: (Int, LocalShellFailure, Long) -> Unit = { _, _, _ -> },
        block: (shell: (String) -> LocalShellText) -> T,
    ): LocalShellResult<T> = LocalShellSessions.run(
        connector = DadbLoopbackConnector,
        keys = keys,
        retry = retry,
        onProgress = onProgress,
        nowMs = System::currentTimeMillis,
        sleepMs = Thread::sleep,
        block = block,
    )

    /**
     * Phiên thật: dadb tới `localhost:5555`.
     *
     * `socketTimeoutMs <= 0` ⇒ gọi ĐÚNG overload 3 tham số như trước 2026-08-24. Không đi qua overload 5
     * tham số với số 0 cho "giống nhau": tuy bytecode dadb 2.0.0 cho thấy hai đường tương đương
     * (`create(host,port,keys)` → `create$default(..., mask 56)` → connectTimeout=0, socketTimeout=0,
     * keepAlive=false), nhưng đường đang chạy tốt ngoài hiện trường thì không đổi vì một suy luận —
     * CLAUDE.md §6. Connect-timeout luôn để 0 để `SocketTimeoutException` chỉ có thể là hạn ĐỌC
     * (điều kiện để [LocalShellFailures.classify] gọi ra [LocalShellFailure.AWAITING_APPROVAL]).
     */
    private object DadbLoopbackConnector : LocalShellConnector {
        override fun open(keys: AdbKeyPair, socketTimeoutMs: Int): LocalShellConnection {
            val adb = if (socketTimeoutMs <= 0) {
                Dadb.create(HOST, PORT, keys)
            } else {
                Dadb.create(HOST, PORT, keys, 0, socketTimeoutMs)
            }
            return DadbConnection(adb)
        }
    }

    private class DadbConnection(private val adb: Dadb) : LocalShellConnection {
        /** `supportsFeature` đi qua `DadbImpl.connection()` ⇒ ép bắt tay xong mà không gửi lệnh shell nào. */
        override fun handshake() {
            adb.supportsFeature("shell_v2")
        }

        override fun shell(command: String): LocalShellText {
            val response = adb.shell(command)
            return LocalShellText(response.output ?: "", response.errorOutput ?: "", response.exitCode)
        }

        override fun close() = adb.close()
    }

    /**
     * Cài một APK, trả về **lý do** khi hỏng (U11 — xem [LocalInstallOutcome]).
     *
     * Đường THÀNH CÔNG không đổi một bước nào so với trước: cùng `Dadb.create` (cùng hai overload theo
     * [socketTimeoutMs]), cùng `install(apk, *options)`, cùng `use {}` đóng phiên.
     *
     * ## Vì sao phải ép bắt tay TRƯỚC khi cài
     * dadb nối **LƯỜI** (`DadbImpl.connection()` chỉ chạy ở lệnh đầu — [ĐO] bytecode, xem KDoc F2 ở
     * [sessionResult]). Không ép thì lỗi nối và lỗi pm trộn vào cùng một `catch` ⇒ đúng cái gộp mà U11 đi tách.
     * `supportsFeature` ép bắt tay xong mà không gửi lệnh shell nào — cùng cách [DadbConnection.handshake] dùng.
     *
     * ⚠ Ngoại lệ ném ra TỪ `install` vẫn tính là [LocalInstallOutcome.NoShellChannel]: sau khi bắt tay xong thì
     * thứ duy nhất còn ném được là đứt/hết hạn transport, không phải phán quyết của pm (pm trả `InstallResult`).
     * Ca hay gặp nhất của nhánh đó lại là **cài THÀNH CÔNG**: `-r` giết chính tiến trình này giữa lời gọi. Đó là
     * lý do [UpdateRelaunch] được hẹn TRƯỚC khi cài chứ không dựa vào giá trị trả về ở đây.
     *
     * @param socketTimeoutMs hạn ĐỌC socket (F6). [LocalShellRetry.BACKGROUND_READ_CAP] truyền 30 s để OTA
     *   cài lúc khoá adb chưa cấp KHÔNG treo vĩnh viễn; `<= 0` = đọc vô hạn (hành vi trước 2026-08-25).
     */
    fun installApk(keys: AdbKeyPair, apk: File, vararg options: String, socketTimeoutMs: Int = 0): LocalInstallOutcome {
        val adb = runCatching {
            val d = if (socketTimeoutMs <= 0) Dadb.create(HOST, PORT, keys) else Dadb.create(HOST, PORT, keys, 0, socketTimeoutMs)
            runCatching { d.supportsFeature("shell_v2") }.onFailure { runCatching { d.close() } }.getOrThrow()
            d
        }.getOrElse { return LocalInstallOutcome.NoShellChannel(LocalShellFailures.classify(it)) }
        return adb.use { dadb ->
            runCatching { dadb.install(apk, *options) }.fold(
                onSuccess = { r ->
                    if (r is InstallResult.Failure) LocalInstallOutcome.PmRejected(r.reason.trim())
                    else LocalInstallOutcome.Ok
                },
                onFailure = { LocalInstallOutcome.NoShellChannel(LocalShellFailures.classify(it)) },
            )
        }
    }

    /**
     * S5 — đặt [component] làm màn hình chính, rồi ĐỌC LẠI để xác nhận (khớp mẫu [installApk]).
     *
     * Một phiên, hai lệnh: `cmd package set-home-activity <component>` rồi [HomeActivityCmd.RESOLVE]. Xác nhận bằng
     * [HomeActivityCmd.isHome] thay vì tin `set` trả `Success` — trên xe thật đường phục hồi phải **đo** kết quả,
     * không suy từ một dòng output (CLAUDE.md §2/§8). ROM BYD không hiện hộp chọn HOME khi bấm Home, nên đây là
     * đường đặt được duy nhất ([ĐO] DiLink3.0 2026-09-14, xem [HomeActivityCmd]).
     *
     * [retry] mặc định [LocalShellRetry.BACKGROUND_READ_CAP] = chụp mũ chống-treo 30 s (không thử lại): owner đang
     * bấm nút trong Cài đặt nhưng đây vẫn là thread nền, và một socket câm không được treo vòng đời. Kênh chưa cấp
     * "Cho phép gỡ lỗi USB" ⇒ [LocalShellFailure.AWAITING_APPROVAL] ⇒ [LocalSetHomeOutcome.NoShellChannel].
     */
    fun setHomeActivity(
        keys: AdbKeyPair,
        component: String,
        retry: LocalShellRetry = LocalShellRetry.BACKGROUND_READ_CAP,
    ): LocalSetHomeOutcome = mapSetHome(sessionResult(keys, retry) { sh -> setHomeBlock(component, sh) })

    /**
     * Chuỗi lệnh của một lượt đặt HOME: `set-home-activity` rồi ĐỌC LẠI. Tách ra `internal` để test off-car khoá
     * **đúng thứ tự lệnh + phép xác nhận** mà không cần thiết bị (chỉ tiêm một `sh` giả).
     *
     * @return `(đã là HOME chưa, output resolve đã trim)`.
     */
    internal fun setHomeBlock(component: String, sh: (String) -> LocalShellText): Pair<Boolean, String> {
        sh(HomeActivityCmd.set(component))
        val resolved = sh(HomeActivityCmd.RESOLVE).output
        return HomeActivityCmd.isHome(resolved, component) to resolved.trim()
    }

    /** Kết quả phiên → [LocalSetHomeOutcome] (thuần, khoá off-car). Xem KDoc [LocalSetHomeOutcome] về ba nhánh. */
    internal fun mapSetHome(result: LocalShellResult<Pair<Boolean, String>>): LocalSetHomeOutcome = when (result) {
        is LocalShellResult.Ok ->
            if (result.value.first) LocalSetHomeOutcome.Ok else LocalSetHomeOutcome.Failed(result.value.second)
        is LocalShellResult.Failed -> LocalSetHomeOutcome.NoShellChannel(result.reason)
    }

    /**
     * Tự cấp quyền bind AppWidget cho [pkg] trên user 0 qua shell (uid 2000).
     *
     * `appwidget grantbind` cần shell/root; phiên adb loopback CHÍNH LÀ shell, nên app tự cho phép
     * mình bind widget mà không cần người dùng gõ tay `adb shell appwidget grantbind`. Grant chỉ
     * mở đúng cho [pkg] (không đụng app khác). Thử cả `appwidget` (binary rời) lẫn `cmd appwidget`
     * (tùy build). Trả true nếu phiên nối được (đã phát lệnh) — dấu hiệu thành công thật là lần
     * `bindAppWidgetIdIfAllowed()` thử lại sau đó, vì grantbind không in gì khi thành công.
     */
    fun grantAppWidgetBind(keys: AdbKeyPair, pkg: String): Boolean {
        // CLAUDE.md §4 — "nhắm đúng app nào? (allow-list, không phải 'mọi thứ trừ…')". [pkg] chảy THẲNG vào một
        // lệnh chạy ở uid-2000 shell; một chuỗi mang khoảng trắng / `;` / `$(…)` / `&&` sẽ chạy thành lệnh KHÁC
        // với quyền shell. Hôm nay chỗ gọi duy nhất truyền `packageName` của chính app, nhưng hàm này là API
        // công khai của tầng transport — khuôn tên gói là cổng, không phải kỷ luật của bên gọi.
        // Trả `false` (không `require`): bên gọi chạy trong `Thread { }` TRẦN (VietMapWidgetDiagActivity), nên
        // một ngoại lệ lọt ra là giết tiến trình; `false` đã có nhánh xử lý sẵn (hiện hướng dẫn thủ công).
        if (!PACKAGE_NAME.matches(pkg)) return false
        return runAll(
            keys,
            listOf(
                "appwidget grantbind --package $pkg --user 0",
                "cmd appwidget grantbind --package $pkg --user 0",
            ),
        ) != null
    }

    /** Khuôn tên gói Android — cổng DUY NHẤT cho chuỗi bên-gọi-cấp đi thẳng vào một lệnh shell ở đây. */
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
}
