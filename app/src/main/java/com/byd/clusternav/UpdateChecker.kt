package com.byd.clusternav

import com.byd.clusternav.system.PackageQueries
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellRetry
import android.content.Context
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * KIỂM TRA & TẢI BẢN CẬP NHẬT từ GitHub — không cần server riêng, không thư viện ngoài.
 *
 * Cách hoạt động: repo public để sẵn APK release trong thư mục `apk/`. Hỏi GitHub Contents API xem thư mục
 * đó có file `ClusterNav-<ver>-release.apk` nào mới hơn bản đang cài không, rồi tải từ `download_url` và cài.
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

    private const val REPO = "dangkhoi/byd-launcher"
    /** Nhánh chứa APK phát hành. Để trống = nhánh mặc định của repo (main). */
    private const val BRANCH = "main"
    private val RE_APK = Regex("""ClusterNav-([0-9]+(?:\.[0-9]+)*)-release\.apk""")

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
    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? = runCatching {
        val dir = File(ctx.applicationContext.filesDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }   // chỉ giữ 1 bản đang tải
        val out = File(dir, url.substringAfterLast('/').ifBlank { "update.apk" })
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 60000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ClusterNav-Updater")
        }
        conn.inputStream.use { input ->
            val total = conn.contentLength
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024); var read = 0L; var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    output.write(buf, 0, n); read += n
                    onProgress(if (total > 0) ((read * 100) / total).toInt() else -1)
                }
            }
        }
        conn.disconnect()
        out.takeIf { it.length() > 0 }
    }.getOrNull()

    /**
     * Cài APK qua dadb loopback. Trả chuỗi kết quả để hiển thị.
     * `-r` = reinstall giữ dữ liệu; cùng chữ ký nên không cần gỡ trước.
     *
     * Relaunch: một `-r` THÀNH CÔNG kill process này ngay → không code nào sau đó chạy. Nên ta HẸN GIỜ
     * mở lại Home TRƯỚC khi cài (lúc app còn foreground); nếu cài thất bại thì huỷ hẹn. Xem [UpdateRelaunch].
     *
     * Kết quả THẬT: [LocalDeviceShell.installApk] trả `false` khi dadb báo lỗi (vd khác chữ ký:
     * debug↔release, hoặc downgrade) — trước đây hàm này BỎ QUA giá trị đó và luôn báo "đã cài", nên
     * một lần cài fail vẫn hiện "sẽ tự khởi động lại" rồi đứng im. Giờ báo đúng thành/bại.
     */
    fun install(ctx: Context, apk: File): String {
        val app = ctx.applicationContext
        UpdateRelaunch.schedule(app) // arm BEFORE install: a successful -r kills us mid-call.
        val ok = LocalDeviceShell.installApk(AdbKeys.ensure(app), apk, "-r", socketTimeoutMs = LocalShellRetry.BACKGROUND_READ_CAP.socketTimeoutMs)
        return if (ok) {
            Lang.t("đã cài — đang mở lại…", "installed — reopening…")
        } else {
            UpdateRelaunch.cancel(app) // nothing was replaced → don't relaunch.
            Lang.t(
                "cài thất bại (khác chữ ký/phiên bản?). APK đã tải ở: ${apk.absolutePath}",
                "install failed (signature/version mismatch?). APK saved at: ${apk.absolutePath}",
            )
        }
    }

    // ── nội bộ ──

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("User-Agent", "ClusterNav-Updater")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
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
