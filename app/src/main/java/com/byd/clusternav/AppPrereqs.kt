package com.byd.clusternav

import android.content.Context
import android.util.Log
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellResult
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.carexec.LocalShellText
import com.byd.clusternav.modules.clustercast.simplified.CastAutoStartPkgs
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastPrefs
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastShell
import com.byd.clusternav.system.AppPrereqPlan
import com.byd.clusternav.system.AppPrereqPlan.Role
import com.byd.clusternav.system.PackageQueries
import com.byd.clusternav.system.Prereq
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * ═══ 2.89 · B2 VM-PREREQ-TRUTH — điều kiện nền của app Kachi TỰ mở, quyết bằng SỰ THẬT (CLAUDE.md §5) ═══════════════
 *
 * Luật (đọc → áp phần thiếu → đọc lại), bảng phạm vi và định dạng output ở `:core` [AppPrereqPlan] / `DozeWhitelistRead`
 * / `OverlayOpRead` (KDoc có trích AOSP r47/r34). Lớp này chỉ: gom sự thật Android (công tắc, gói tự chiếu, gói đã cài)
 * thành [AppPrereqPlan.Facts], mở phiên shell, ghi log, giữ lượt gần nhất cho màn Chẩn đoán.
 *
 * Ba lối vào, MỘT hàm quyết ([AppPrereqPlan.ensure]) — thay hai cờ một-lần `doze_whitelist_applied` (mở chiếu) và
 * `vm_float_whitelist_applied` (bóng), vốn đặt một lần rồi không bao giờ đọc lại ⇒ VietMap cài lại (đổi khoá ký ⇒ ROM gỡ
 * khỏi danh sách miễn pin, AOSP r47 `DeviceIdleController.java:586-590`) là hộp thoại "IVI không hỗ trợ" mỗi lần nổ máy:
 *  • [onReady] — chuỗi SẴN (`EarlyShellChannel.readyChain`: kênh UP + màn sáng), toàn phạm vi, luồng riêng; Pass 2 ·
 *    vietmap-dock-r1-2: + TRẢ LẠI điều kiện mà chính Kachi đã thêm cho gói nay đã rời phạm vi (dấu bền [PrefsMarks]).
 *  • [ensure] — `VietMapAutostart.runNow`, TRƯỚC cổng sớm (người đã tắt bóng/biển mà tự mở VietMap cũng được chữa).
 *  • [ensureForCastOpen] — lượt mở chiếu cụm, chạy trên executor + shell của coordinator.
 *
 * Kênh shell chưa lên / chưa duyệt ⇒ phiên bị cổng thi hành chặn (`LocalShellAdmission`) ⇒ không làm gì, giữ nguyên hiện
 * trạng (hộp thoại vẫn có thể hiện; bấm OK là xong) — lượt kế tự đọc lại.
 */
internal object AppPrereqs {

    private const val TAG = "AppPrereqs"

    /** Một lượt đọc/ghi tại một thời điểm (móc sẵn sàng · autostart · mở chiếu không chồng lệnh lên nhau). */
    private val lock = ReentrantLock()

    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "kachi-app-prereqs").apply { isDaemon = true } }

    /** Lượt gần nhất — CHỈ để hiển thị ở màn Chẩn đoán (không bao giờ là căn cứ quyết định). Giờ tường. */
    data class Last(
        val wallMs: Long,
        val trigger: String,
        val results: List<AppPrereqPlan.Result>?,
        val note: String?,
        val reverts: List<AppPrereqPlan.Revert> = emptyList(),
    )

    @Volatile var last: Last? = null
        private set

    // ─── Sự thật Android → Facts ──────────────────────────────────────────────────────────────────────

    /** Một cửa hỏi `PackageManager` của repo ([PackageQueries.packageInfo]: `null` = chưa cài). */
    private fun installed(app: Context, pkg: String): Boolean = PackageQueries.packageInfo(app.packageManager, pkg) != null

    /** [castPrefs] = prefs của coordinator (spec K1: mọi chỗ đọc `castEnabled` đi qua `coordinator(...).prefs`). */
    fun facts(ctx: Context, castPrefs: SimpleCastPrefs): AppPrereqPlan.Facts {
        val app = ctx.applicationContext
        return AppPrereqPlan.Facts(
            vietMapPkg = VietMapAutostart.PKG,
            vietMapInstalled = installed(app, VietMapAutostart.PKG),
            bubbleOn = Prefs.vmBubbleEnabled(app),
            badgeOn = Prefs.badgeEnabled(app),
            vietMapCastDefault = VietMapAutostart.castDefault(app),
            autoCastPkgs = CastAutoStartPkgs.of(castPrefs),
            installed = { installed(app, it) },
        )
    }

    private fun castPrefs(app: Context): SimpleCastPrefs = SimpleCastRuntime.coordinator(app).prefs

    /** Tệp dấu "Kachi đã tự thêm" — RIÊNG, phạm vi XE (state hệ thống của chính đầu xe, không phải cấu hình hồ sơ). */
    private const val MARKS_FILE = "app_prereq_marks"

    /**
     * Review 2.89 Pass 2 · vietmap-dock-r1-2 — dấu bền của [AppPrereqPlan.Marks]: `commit()` ĐỒNG BỘ (dấu phải chạm đĩa TRƯỚC
     * lệnh ghi — CLAUDE.md §5). Mọi lượt đọc/ghi dấu chạy dưới [lock] (một lượt tại một thời điểm) ⇒ đọc-sửa-ghi không giẫm nhau.
     */
    private class PrefsMarks(app: Context) : AppPrereqPlan.Marks {
        private val sp = app.getSharedPreferences(MARKS_FILE, Context.MODE_PRIVATE)

        override fun added(p: Prereq): Set<String> = sp.getStringSet(AppPrereqPlan.markKey(p), null)?.toSet().orEmpty()

        override fun mark(p: Prereq, pkg: String): Boolean = write(p, pkg, add = true)

        override fun unmark(p: Prereq, pkg: String): Boolean = write(p, pkg, add = false)

        private fun write(p: Prereq, pkg: String, add: Boolean): Boolean {
            val cur = added(p)
            if ((pkg in cur) == add) return true
            val ok = sp.edit().putStringSet(AppPrereqPlan.markKey(p), if (add) cur + pkg else cur - pkg).commit()
            if (!ok) Log.w(TAG, "dấu ${p.name} ${if (add) "+" else "-"}$pkg: commit() hỏng — ${if (add) "KHÔNG ghi điều kiện" else "lượt sau thử lại"}")
            return ok
        }
    }

    // ─── Lối vào ──────────────────────────────────────────────────────────────────────────────────────

    /** Chuỗi SẴN — đẩy sang luồng `kachi-app-prereqs`, trả ngay (không chặn kiểm phím / chuyến lên xe). */
    fun onReady(ctx: Context) {
        val app = ctx.applicationContext
        try {
            exec.execute { guarded("ready") { runScope(app, "ready", emptyList()) } }
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "không xếp được lượt ready", e)
        }
    }

    /**
     * BLOCKING (luồng nền của bên gọi) — phạm vi của [pkg] cộng lý do [role] của chính lượt gọi; `null` = gói ngoài phạm vi
     * / chưa cài / phiên shell không mở được. Không bao giờ ném.
     */
    fun ensure(ctx: Context, pkg: String, role: Role): AppPrereqPlan.Result? {
        val app = ctx.applicationContext
        return guarded("ensure($pkg)") {
            runScope(app, "autostart", listOf(pkg to role)).firstOrNull { it.target.pkg == pkg }
        }
    }

    /**
     * Lượt mở chiếu cụm — trên executor của coordinator, qua CHÍNH shell của nó (không mở phiên dadb thứ hai). Phạm vi
     * chuẩn + [Role.CAST_OPEN] cho VietMap (đúng phạm vi đường cũ `+vn.vietmap.live`, nay theo sự thật).
     */
    fun ensureForCastOpen(ctx: Context, castPrefs: SimpleCastPrefs, sh: SimpleCastShell) {
        val app = ctx.applicationContext
        guarded("cast-open") {
            val targets = AppPrereqPlan.targets(facts(app, castPrefs), listOf(VietMapAutostart.PKG to Role.CAST_OPEN))
            if (targets.isEmpty()) return@guarded
            // Không để lượt mở chiếu (executor có hạn 15 s) đứng chờ một phiên nền đang giữ khoá: lượt kia đang đọc CÙNG sự thật.
            // Review 2.89 Pass 3 · vietmap-dock-r2-6: KHÔNG chờ chút nào (trước: tới 2 s mỗi lượt mở lúc nổ máy ⇒ cụm lên muộn) — lượt
            // SẴN / autostart cùng lần nổ máy phủ đúng phạm vi này.
            if (!lock.tryLock()) {
                Log.i(TAG, "cast-open: lượt khác đang đọc/ghi điều kiện nền — bỏ lượt này, mở chiếu đi tiếp")
                return@guarded
            }
            val results = try {
                AppPrereqPlan.ensureAll(targets, { cmd -> sh.execute(cmd).let { it.stdout + "\n" + it.stderr } }, PrefsMarks(app))
            } finally {
                lock.unlock()
            }
            publish("cast-open", results, null)
        }
    }

    private fun runScope(app: Context, trigger: String, extra: List<Pair<String, Role>>): List<AppPrereqPlan.Result> {
        val f = facts(app, castPrefs(app))
        val all = AppPrereqPlan.targets(f, extra)
        // Lượt autostart chỉ lo gói của chính nó (VietMap); lượt ready lo cả phạm vi.
        val targets = if (extra.isEmpty()) all else all.filter { t -> extra.any { it.first == t.pkg } }
        val marks = PrefsMarks(app)
        // Pass 2 · vietmap-dock-r1-2: CHỈ lượt ready (toàn phạm vi) trả lại dấu đã rời phạm vi — lượt autostart chỉ thấy một gói.
        val stale = if (extra.isEmpty()) lock.withLock { AppPrereqPlan.stale(f, marks) } else emptyList()
        if (targets.isEmpty() && stale.isEmpty()) {
            Log.i(TAG, "$trigger: không gói nào trong phạm vi, không dấu nào cần trả — không đọc, không ghi")
            return emptyList()
        }
        val keys = try {
            AdbKeys.ensure(app)
        } catch (e: IOException) {
            publish(trigger, null, "khoá adb lỗi: ${e.message}")
            return emptyList()
        }
        val result = lock.withLock {
            LocalDeviceShell.sessionResult(keys, LocalShellRetry.BACKGROUND_READ_CAP) { sh ->
                val run: (String) -> String? = { cmd -> textOf(sh(cmd)) }
                val done = AppPrereqPlan.ensureAll(targets, run, marks)
                done to if (stale.isEmpty()) emptyList() else AppPrereqPlan.revertStale(f, marks, run)
            }
        }
        return when (result) {
            is LocalShellResult.Ok -> result.value.first.also { publish(trigger, it, null, result.value.second) }
            is LocalShellResult.Failed -> {
                publish(trigger, null, "phiên shell không mở được (${result.reason}) — giữ nguyên, lượt sau đọc lại")
                emptyList()
            }
        }
    }

    private fun textOf(t: LocalShellText): String = t.output + "\n" + t.errorOutput

    private fun publish(
        trigger: String,
        results: List<AppPrereqPlan.Result>?,
        note: String?,
        reverts: List<AppPrereqPlan.Revert> = emptyList(),
    ) {
        last = Last(System.currentTimeMillis(), trigger, results, note, reverts)
        if (results == null) {
            Log.w(TAG, "$trigger: $note")
            return
        }
        results.forEach { r ->
            if (r.ok) Log.i(TAG, "$trigger: ${r.describe()}") else Log.w(TAG, "$trigger: ${r.describe()}")
        }
        reverts.forEach { r -> if (r.cleared) Log.i(TAG, "$trigger: ${r.describe()}") else Log.w(TAG, "$trigger: ${r.describe()}") }
    }

    /**
     * Ranh giới lỗi một lượt (luồng nền / executor coordinator): một ngoại lệ ở đây không được làm chết luồng gọi hay lượt
     * mở chiếu. Lỗi khoá adb (I/O) và lỗi lập trình (RuntimeException) đều log kèm stack, trả `null`.
     */
    private inline fun <T> guarded(label: String, block: () -> T): T? = try {
        block()
    } catch (e: IOException) {
        Log.e(TAG, "lượt '$label' lỗi I/O", e); null
    } catch (e: RuntimeException) {
        Log.e(TAG, "lượt '$label' lỗi", e); null
    }

    // ─── Chẩn đoán (CLAUDE.md §11) ────────────────────────────────────────────────────────────────────

    /**
     * Đọc sự thật NGAY (0 lệnh ghi) cho VietMap (nếu đã cài) + mọi gói trong phạm vi; kèm lượt gần nhất. Luồng nền.
     * Dòng in ra là thứ chụp màn hình gửi về — không gõ adb.
     */
    fun diagnose(ctx: Context): String =
        guarded("diagnose") { diagnoseBody(ctx.applicationContext) } ?: "đọc sự thật hỏng (xem log $TAG)"

    private fun diagnoseBody(app: Context): String {
        val f = facts(app, castPrefs(app))
        val scope = AppPrereqPlan.targets(f)
        val pkgs = (listOfNotNull(VietMapAutostart.PKG.takeIf { f.vietMapInstalled }) + scope.map { it.pkg }).distinct()
        val sb = StringBuilder()
        if (pkgs.isEmpty()) {
            sb.appendLine("(không có gói nào: VietMap chưa cài, không app tự chiếu)")
        } else {
            val read = try {
                LocalDeviceShell.sessionResult(AdbKeys.ensure(app), LocalShellRetry.BACKGROUND_READ_CAP) { sh ->
                    pkgs.map { pkg -> pkg to AppPrereqPlan.read(pkg, Prereq.entries.toSet(), { cmd -> textOf(sh(cmd)) }) }
                }
            } catch (e: IOException) {
                null
            }
            when (read) {
                is LocalShellResult.Ok -> read.value.forEach { (pkg, s) ->
                    val roles = scope.firstOrNull { it.pkg == pkg }?.roles?.joinToString(",") { it.name } ?: "chỉ lượt autostart/mở chiếu"
                    sb.appendLine("$pkg [$roles]: ${AppPrereqPlan.stateText(s)}")
                }
                is LocalShellResult.Failed -> sb.appendLine("đọc sự thật hỏng: phiên shell ${read.reason}")
                null -> sb.appendLine("đọc sự thật hỏng: khoá adb lỗi")
            }
        }
        val l = last
        sb.append("lượt chữa gần nhất: ")
        if (l == null) {
            sb.appendLine("chưa có trong tiến trình này")
        } else {
            sb.append(android.text.format.DateFormat.format("HH:mm:ss", l.wallMs)).append(" ").append(l.trigger).append(" — ")
            sb.appendLine(l.note ?: l.results?.joinToString(" | ") { it.describe() }?.ifEmpty { "không gói nào" } ?: "-")
            if (l.reverts.isNotEmpty()) sb.appendLine("trả lại: " + l.reverts.joinToString(" | ") { it.describe() })
        }
        return sb.toString().trimEnd()
    }
}
