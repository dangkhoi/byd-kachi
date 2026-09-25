package com.byd.clusternav

import com.byd.clusternav.system.PackageQueries
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalInstallOutcome
import com.byd.clusternav.carexec.LocalShellFailure
import com.byd.clusternav.carexec.LocalShellRetry
import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File
import com.byd.clusternav.net.HttpConn

/**
 * KIỂM TRA & TẢI BẢN CẬP NHẬT từ GitHub — không cần server riêng, không thư viện ngoài.
 *
 * Cách hoạt động: repo public để sẵn APK release trong thư mục `apk/`. Hỏi GitHub Contents API xem thư mục
 * đó có file `Kachi-<ver>-release.apk` (hoặc tên cũ `ClusterNav-…`) nào mới hơn bản đang cài không, rồi tải từ
 * `download_url` và cài. Cùng khoá ký Kachi (keystore riêng từ 1.41, L2) nên `pm install -r` chạy được.
 *
 * CÀI qua dadb loopback (`dadb.install(file, "-r")`): app chạy trên đầu xe nối `localhost:5555` = uid shell,
 * đủ quyền `pm install`. Không cần REQUEST_INSTALL_PACKAGES, không cần người dùng bấm qua trình cài đặt —
 * đúng tinh thần self-service của app (adb ngoài không vào được khi cắm CarPlay/AA, §11). Cùng chữ ký nên
 * `-r` (reinstall) chạy được.
 *
 * ⚠ APK release PHẢI nằm trên nhánh [BRANCH]. Hiện team đẩy APK vào `apk/` trên nhánh làm việc; muốn tính năng
 * này thấy được thì bản phát hành phải có trên nhánh này (mặc định nhánh mặc định của repo).
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    /**
     * L2 (2026-09-13) — kênh cập nhật RIÊNG của Kachi: repo `dangkhoi/byd-kachi` (đúng remote của mã này), thư mục
     * `apk/` trên nhánh [BRANCH]. Trước đây trỏ `dangkhoi/byd-launcher` (tên repo ClusterNav 2.0 kế thừa) ⇒ Kachi
     * dò nhầm kênh của app khác — không bao giờ thấy bản của mình.
     */
    private const val REPO = "dangkhoi/byd-kachi"
    /** Nhánh chứa APK phát hành. Để trống = nhánh mặc định của repo (main). */
    private const val BRANCH = "main"
    /**
     * Tên tệp phát hành: `Kachi-<ver>-release.apk`. Vẫn nhận `ClusterNav-<ver>-release.apk` để nếu owner đăng theo
     * tên cũ thì kênh không câm — hai tiền tố, một dải phiên bản; bản mới nhất thắng bất kể tiền tố.
     */
    internal val RE_APK = Regex("""(?:Kachi|ClusterNav)-([0-9]+(?:\.[0-9]+)*)-release\.apk""")

    /** Phiên bản trong tên tệp phát hành, `null` nếu tên không đúng khuôn (thuần — test off-device). */
    internal fun apkVersion(fileName: String): String? = RE_APK.matchEntire(fileName)?.groupValues?.get(1)

    data class Result(
        val current: String,
        val latest: String?,
        val downloadUrl: String?,
        val hasUpdate: Boolean,
        val error: String?,
    )

    /** Phiên bản đang cài (đọc từ máy — nhất quán với phần còn lại của app). */
    fun currentVersion(ctx: Context): String =
        // `packageInfo` chỉ nuốt NameNotFound. Đường cũ nuốt MỌI ngoại lệ (kể cả RuntimeException mà
        // `ApplicationPackageManager` ném lại khi binder chết) — giữ nguyên lớp chắn đó: đọc phiên bản KHÔNG
        // bao giờ được làm ngã màn hình trên xe đang chạy (CLAUDE.md §6 — không đảo đường đã chạy tốt).
        runCatching { PackageQueries.packageInfo(ctx.packageManager, ctx.packageName) }.getOrNull()?.versionName ?: "?"

    /**
     * Hỏi GitHub xem có bản mới không. CHẠY TRÊN LUỒNG NỀN (có I/O mạng) — đừng gọi trên main thread.
     */
    fun check(ctx: Context): Result {
        val cur = currentVersion(ctx)
        val ref = if (BRANCH.isBlank()) "" else "?ref=$BRANCH"
        val api = "https://api.github.com/repos/$REPO/contents/apk$ref"
        return runCatching {
            val body = httpGet(api) ?: return Result(cur, null, null, false, Lang.t("không đọc được phản hồi", "empty response"))
            val arr = JSONArray(body)
            var bestVer: String? = null
            var bestUrl: String? = null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name")
                val m = RE_APK.matchEntire(name) ?: continue
                val ver = m.groupValues[1]
                if (bestVer == null || cmp(ver, bestVer!!) > 0) {
                    bestVer = ver; bestUrl = o.optString("download_url").takeIf { it.isNotBlank() }
                }
            }
            if (bestVer == null) Result(cur, null, null, false, Lang.t("không thấy APK trên nhánh $BRANCH", "no APK found on branch $BRANCH"))
            else Result(cur, bestVer, bestUrl, cmp(bestVer!!, cur) > 0, null)
        }.getOrElse { Result(cur, null, null, false, Lang.t("lỗi mạng: ${it.message}", "network error: ${it.message}")) }
    }

    /**
     * Tải APK về thư mục riêng của app. Trả file, hoặc null nếu lỗi.
     * @param onProgress phần trăm 0..100 (hoặc -1 khi không biết tổng cỡ)
     */
    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? {
        val out = runCatching {
            val dir = File(ctx.applicationContext.filesDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }   // chỉ giữ 1 bản đang tải
            File(dir, url.substringAfterLast('/').ifBlank { "update.apk" })
        }.getOrElse { Log.w(TAG, "download: không chuẩn bị được thư mục (${it.javaClass.simpleName}: ${it.message})"); return null }
        // Một cửa duy nhất mở kết nối (CLAUDE.md §4.1 DRY) — cùng thiết lập với đường tải mô hình nhận dạng
        // của V1 pha NGHE. Thời hạn/chuyển hướng/nhãn giữ NGUYÊN như bản đang chạy trên xe; chỗ này chỉ đổi
        // NƠI KHAI chúng, không đổi giá trị nào (CLAUDE.md §6 — không đảo đường đã chạy tốt).
        val conn = runCatching { HttpConn.open(url, readTimeoutMs = 60_000) }
            .getOrElse { Log.w(TAG, "download: không mở được kết nối (${it.javaClass.simpleName}: ${it.message}) url=${url.take(80)}"); return null }
        return fetchTo(
            out = out,
            open = { conn.inputStream to conn.contentLength.toLong() },
            close = { conn.disconnect() },
            onProgress = onProgress,
            onError = { Log.w(TAG, "download failed (${it.javaClass.simpleName}: ${it.message}) url=${url.take(80)}") },
        )
    }

    /**
     * Chép luồng [open] vào [out], báo tiến độ, **luôn** [close] (hardening 2026-09-25 · audit F3 [P2]: trước đây
     * `disconnect()` không nằm `finally` ⇒ ném là rò socket; và lỗi thành `null` im ⇒ UI chỉ nói "tải thất bại").
     * Hỏng ⇒ [onError] một lần, **xoá tệp cụt** (không để `.apk` nửa chừng trong `files/update/`), trả `null`.
     * THUẦN (không Android) ⇒ `UpdateCheckerHardeningTest` khoá off-device.
     *
     * @param open trả (luồng, tổng byte hoặc ≤0 nếu không biết); [onProgress] nhận 0..100 hoặc -1 khi không biết.
     */
    internal fun fetchTo(
        out: File,
        open: () -> Pair<java.io.InputStream, Long>,
        close: () -> Unit,
        onProgress: (Int) -> Unit,
        onError: (Throwable) -> Unit,
    ): File? {
        try {
            val (input, total) = open()
            input.use { inp ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var read = 0L; var n: Int
                    while (inp.read(buf).also { n = it } > 0) {
                        output.write(buf, 0, n); read += n
                        onProgress(if (total > 0) ((read * 100) / total).toInt() else -1)
                    }
                }
            }
            return out.takeIf { it.length() > 0 }
        } catch (t: Throwable) {
            onError(t)
            runCatching { out.delete() }
            return null
        } finally {
            runCatching { close() }
        }
    }

    /**
     * Cài APK qua dadb loopback. Trả chuỗi kết quả để hiển thị.
     * `-r` = reinstall giữ dữ liệu; cùng chữ ký nên không cần gỡ trước.
     *
     * Relaunch: một `-r` THÀNH CÔNG kill process này ngay → không code nào sau đó chạy. Nên ta HẸN GIỜ
     * mở lại Home TRƯỚC khi cài (lúc app còn foreground); nếu cài thất bại thì huỷ hẹn. Xem [UpdateRelaunch].
     *
     * Kết quả THẬT: [LocalDeviceShell.installApk] trả [LocalInstallOutcome] — trước đây hàm này BỎ QUA giá
     * trị trả về và luôn báo "đã cài", nên một lần cài fail vẫn hiện "sẽ tự khởi động lại" rồi đứng im.
     *
     * ## U11 — một câu cho mỗi NGUYÊN NHÂN, không còn một câu cho mọi thất bại
     * Câu cũ đổ cho *"khác chữ ký/phiên bản?"* trong MỌI ca. **[ĐO] máy ảo 2026-09-13**: thiếu
     * `adb reverse tcp:5555 tcp:5555` ⇒ không có kênh dadb nào, mà người dùng vẫn đọc được câu đổ cho chữ ký ⇒
     * đi sửa nhầm bệnh. Nay lý do tới từ [LocalDeviceShell], còn chỗ này chỉ **dịch nó ra câu người đọc được**.
     */
    fun install(ctx: Context, apk: File): String {
        val app = ctx.applicationContext
        return installWith(
            apkPath = apk.absolutePath,
            arm = { UpdateRelaunch.schedule(app) },
            disarm = { UpdateRelaunch.cancel(app) },
            keys = { AdbKeys.ensure(app) },
            installApk = { k -> LocalDeviceShell.installApk(k, apk, "-r", socketTimeoutMs = LocalShellRetry.BACKGROUND_READ_CAP.socketTimeoutMs) },
            onKeysError = { Log.w(TAG, "install: không có khoá adb (${it.javaClass.simpleName}: ${it.message})") },
        )
    }

    /**
     * Trình tự cài, THUẦN (mọi đường ra tiêm vào) ⇒ `UpdateCheckerHardeningTest` khoá off-device.
     *
     * Hardening 2026-09-25 · audit F4 [P1]: `AdbKeys.ensure` **ném** (`check(rename…)` → `IllegalStateException`;
     * `AdbKeyPair.generate` → `IOException` khi `filesDir` đầy) mà chỗ gọi là `Thread({ … }, "update-download")`
     * TRẦN ⇒ ngoại lệ thoát ⇒ **launcher (HOME) chết giữa lúc lái**. Cùng khuôn đã vá ở
     * `ClusterNavBridgeHome.kt` (bọc `AdbKeys.ensure` → `NoShellChannel(UNKNOWN)`); nay chỗ này cũng vậy:
     * khoá hỏng ⇒ [disarm] (không có gì được thay ⇒ không relaunch) + câu "không kiểm tra/cài được" lên UI.
     */
    internal fun installWith(
        apkPath: String,
        arm: () -> Unit,
        disarm: () -> Unit,
        keys: () -> dadb.AdbKeyPair,
        installApk: (dadb.AdbKeyPair) -> LocalInstallOutcome,
        onKeysError: (Throwable) -> Unit,
    ): String {
        arm() // arm BEFORE install: a successful -r kills us mid-call.
        val k = runCatching(keys).getOrElse {
            onKeysError(it)
            disarm()
            return installMessage(LocalInstallOutcome.NoShellChannel(LocalShellFailure.UNKNOWN), apkPath)
        }
        val outcome = installApk(k)
        // Chỉ MỘT chỗ dựng câu ([installMessage]) — kể cả câu thành công. Để nhánh Ok tự viết lại câu ở đây là
        // hai bản sao của một chuỗi, đúng thứ lần sau sẽ lệch nhau.
        if (outcome !is LocalInstallOutcome.Ok) disarm() // nothing was replaced → don't relaunch.
        return installMessage(outcome, apkPath)
    }

    /**
     * Lý do cài hỏng → câu cho người dùng. **THUẦN** (chỉ đọc [outcome] + [apkPath]) ⇒ khoá được off-device.
     *
     * Hai câu nói hai VIỆC PHẢI LÀM khác nhau, đó là lý do chúng phải khác nhau:
     *  • không có kênh shell ⇒ việc cần làm nằm ở máy — và **việc đó khác nhau theo lý do**, xem [channelRemedy];
     *    chỉ đường tới đúng trang *Cài đặt › Hệ thống & quyền* thay vì bỏ mặc người dùng đoán;
     *  • pm từ chối ⇒ việc cần làm nằm ở bản APK (gỡ bản cũ, đổi bản) — và pm đã nói rõ lý do, chỉ cần **chuyển
     *    nguyên văn** dòng đó ra thay vì thay bằng một dấu hỏi của mình.
     *
     * Đường dẫn APK giữ ở cả hai câu: bản đã tải vẫn nằm đó, cài tay được.
     */
    internal fun installMessage(outcome: LocalInstallOutcome, apkPath: String): String = when (outcome) {
        is LocalInstallOutcome.Ok -> Lang.t("đã cài — đang mở lại…", "installed — reopening…")
        is LocalInstallOutcome.NoShellChannel -> Lang.t(
            "không có kênh shell tới xe (${outcome.reason.name}) — ${channelRemedy(outcome.reason, vi = true)}. " +
                "APK đã tải ở: $apkPath",
            "no shell channel to the head unit (${outcome.reason.name}) — ${channelRemedy(outcome.reason, vi = false)}. " +
                "APK saved at: $apkPath",
        )
        is LocalInstallOutcome.PmRejected -> Lang.t(
            "pm install từ chối: ${pmReason(outcome.pmOutput, vi = true)}. APK đã tải ở: $apkPath",
            "pm install rejected: ${pmReason(outcome.pmOutput, vi = false)}. APK saved at: $apkPath",
        )
    }

    /**
     * VIỆC PHẢI LÀM khi không có kênh shell — **theo từng lý do**, không một câu cho cả năm.
     *
     * ## ⚠ Vì sao không để mỗi câu "xem Cài đặt › Hệ thống & quyền" (soát senior 2026-09-13)
     * U11 sinh ra vì *một* câu cho mọi thất bại làm người đọc đi sửa nhầm bệnh — nhưng bản đầu chỉ tách tới mức
     * "kênh" vs "pm", còn **bên trong kênh thì lại gộp lại y như cũ**. Ca hay gặp nhất của OTA là
     * [LocalShellFailure.AWAITING_APPROVAL]: lần đầu nối bằng khoá mới, đầu xe đang bung hộp thoại *"Cho phép gỡ
     * lỗi USB?"* **ngay trên màn hình đó** — bảo người dùng đi vào Cài đặt lúc ấy là chỉ sai hẳn chỗ, trong khi
     * việc cần làm là bấm Cho phép rồi bấm cài lại. Cùng phân loại này `AssistantLauncher.failureMessage` đã nói
     * đúng việc từ 2026-08-24; chỗ OTA thì chưa.
     *
     * Mã lý do (`reason.name`) vẫn nằm trong câu ở [installMessage] — ảnh chụp màn đủ để chẩn đoán từ xa.
     */
    internal fun channelRemedy(reason: LocalShellFailure, vi: Boolean): String = when (reason) {
        // Hộp thoại đang ở ngay trước mặt ⇒ nói đúng cái nút phải bấm. Nhắc "luôn cho phép" vì mỗi lần thử là một
        // kết nối MỚI: không tích thì quyền chết theo đúng kết nối đang treo (xem `AssistantLauncher.reportProgress`).
        LocalShellFailure.AWAITING_APPROVAL, LocalShellFailure.AUTH_REJECTED ->
            if (vi) "bấm \"Cho phép/Allow\" (tích \"luôn cho phép\") trên hộp thoại gỡ lỗi USB rồi cài lại"
            else "tap \"Allow\" (tick \"always allow\") on the USB-debugging dialog, then install again"
        LocalShellFailure.PORT_CLOSED, LocalShellFailure.IO_ERROR, LocalShellFailure.UNKNOWN ->
            if (vi) "xem Cài đặt › Hệ thống & quyền" else "see Settings › System & permissions"
    }

    /**
     * Dòng đáng đọc nhất trong output của pm.
     *
     * pm in nhiều dòng tiến trình rồi mới tới phán quyết, mà chỗ hiện câu này là **một dòng nút** — nên ưu tiên
     * dòng mang `Failure`/`Error` (chỗ pm nói mã lỗi thật, vd `INSTALL_FAILED_UPDATE_INCOMPATIBLE`), không có thì
     * lấy dòng cuối còn chữ. pm câm hẳn cũng là một sự thật, và nói ra vẫn hơn một câu tự bịa nguyên nhân.
     */
    internal fun pmReason(pmOutput: String, vi: Boolean): String {
        val lines = pmOutput.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.firstOrNull { it.contains("Failure", true) || it.contains("Error", true) }
            ?: lines.lastOrNull()
            ?: if (vi) "pm không in gì" else "pm printed nothing"
    }

    // ── nội bộ ──

    private fun httpGet(url: String): String? {
        val conn = HttpConn.open(url, readTimeoutMs = 20_000, accept = "application/vnd.github+json")
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    /** So sánh hai chuỗi version dạng "0.56" / "1.2.3". >0 nếu a mới hơn b. */
    fun cmp(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
