package com.byd.clusternav.launcher.voice

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LangHost
import com.byd.clusternav.system.PackageQueries
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.91 VOICE-APP-NAMES · A3 — NHÃN LOCALE THỨ HAI (vi + en) của app khởi chạy (R9) ═══════════════════════════
 *
 * Spec §4.9. Máy locale `en` mà người lái nói *"mở bản đồ"* · *"mở cài đặt"* thì nhãn hệ thống (*"Maps"*, *"Settings"*)
 * không khớp; nhãn `vi` của chính app ấy khớp. Kết quả vào TẦNG 3 của [VoiceAppIndex] (`putIfAbsent`, thua nhãn thật +
 * tên đã dạy). KHÔNG vào hotword ở bản này (OQ7).
 *
 * ## Thứ tự nhãn — đúng `ResolveInfo.loadLabel` (đọc source AOSP `android-10.0.0_r47`, CLAUDE.md §3)
 * `ResolveInfo.java:207-231`: `nonLocalizedLabel` → (`resolvePackageName` + `labelRes`) → `labelRes` của resolve → rồi
 * `ComponentInfo.loadUnsafeLabel` (`ComponentInfo.java:101-119`): `nonLocalizedLabel` của activity → `labelRes` của
 * activity → `nonLocalizedLabel` của app → `labelRes` của app. Theo đúng chuỗi ấy để *"mở &lt;nhãn vi&gt;"* là nhãn của
 * CHÍNH chữ người dùng thấy trên ngăn kéo (ở locale kia), không phải một nhãn khác của app. Bước nào là
 * `nonLocalizedLabel` (chữ cứng) ⇒ không có bản dịch ⇒ bỏ.
 *
 * ## Chi phí + hai tiến trình
 * Dựng `createPackageContext` cho từng gói là việc nặng ⇒ chạy trên MỘT luồng nền ưu tiên thấp, kết quả giữ trong bộ
 * nhớ của tiến trình theo `(gói, lastUpdateTime)`. Phiên nghe CHỈ đọc bộ nhớ (thiếu = không có nhãn phụ, không chờ).
 * `:wake` tự dựng bảng nhãn ([VoiceWiring.appsByLabel]) nên nó tự hâm bộ nhớ của nó qua đúng hàm này ⇒ phím vô-lăng và
 * nút mic hiểu CÙNG một câu.
 */
object AppAltLabels {

    private const val TAG = "KachiAltLabels"
    private val LOCALES: List<Locale> by lazy { listOf(LangHost.localeFor(Lang.VI), LangHost.localeFor(Lang.EN)) }

    /** Hâm lại tối đa một lượt mỗi [REWARM_MS] (app mới cài vẫn kịp ở phiên sau). */
    private const val REWARM_MS = 30_000L

    private class Entry(val updated: Long, val labels: List<String>)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val warming = AtomicBoolean(false)
    @Volatile private var lastWarm = 0L

    /** Nhãn phụ ĐÃ CÓ trong bộ nhớ cho các gói [pkgs] (không chờ, không hỏi `PackageManager`). */
    fun cached(pkgs: Collection<String>): List<Pair<String, String>> =
        pkgs.distinct().flatMap { p -> cache[p]?.labels.orEmpty().map { it to p } }

    /** Hâm bộ nhớ trên luồng nền nếu chưa hâm gần đây. Không bao giờ chặn chỗ gọi. */
    fun warm(ctx: Context, infos: List<ResolveInfo>) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWarm < REWARM_MS && infos.all { ri -> ri.activityInfo?.packageName?.let { cache.containsKey(it) } == true }) return
        if (!warming.compareAndSet(false, true)) return
        lastWarm = now
        val app = ctx.applicationContext ?: ctx
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                val t0 = SystemClock.elapsedRealtime()
                var n = 0
                infos.forEach { ri -> if (refresh(app, ri)) n++ }
                Log.i(TAG, "nhãn phụ: soát ${infos.size} app, dựng lại $n, ${cache.values.sumOf { it.labels.size }} nhãn · ${SystemClock.elapsedRealtime() - t0} ms")
            } finally {
                warming.set(false)
            }
        }, "KachiAltLabels").start()
    }

    /** Dựng lại mục của một app khi `lastUpdateTime` đổi. `true` = đã dựng lại. */
    private fun refresh(ctx: Context, ri: ResolveInfo): Boolean {
        val ci = ri.activityInfo ?: return false
        val pkg = ci.packageName
        val updated = PackageQueries.packageInfo(ctx.packageManager, pkg)?.lastUpdateTime
            ?: run { cache.remove(pkg); return false }
        if (cache[pkg]?.updated == updated) return false
        val shown = ri.loadLabel(ctx.packageManager)?.toString()?.trim().orEmpty()
        val device = ctx.resources.configuration.locales[0]?.language
        val labels = LOCALES.filter { it.language != device }
            .mapNotNull { labelIn(ctx, ri, it) }
            .filter { it.isNotBlank() && !it.equals(shown, ignoreCase = true) }
            .distinct()
        cache[pkg] = Entry(updated, labels)
        return true
    }

    /** Nhãn của [ri] ở [locale] theo đúng chuỗi rơi của `ResolveInfo.loadLabel` (KDoc lớp), hoặc `null`. */
    private fun labelIn(ctx: Context, ri: ResolveInfo, locale: Locale): String? {
        val ci = ri.activityInfo ?: return null
        val ai = ci.applicationInfo ?: return null
        if (ri.nonLocalizedLabel != null) return null
        val (pkg, res) = when {
            ri.resolvePackageName != null && ri.labelRes != 0 -> ri.resolvePackageName to ri.labelRes
            ri.labelRes != 0 -> ci.packageName to ri.labelRes
            ci.nonLocalizedLabel != null -> return null
            ci.labelRes != 0 -> ci.packageName to ci.labelRes
            ai.nonLocalizedLabel != null -> return null
            ai.labelRes != 0 -> ci.packageName to ai.labelRes
            else -> return null
        }
        return try {
            val pctx = ctx.createPackageContext(pkg, 0)
            val conf = Configuration(pctx.resources.configuration).apply { setLocale(locale) }
            pctx.createConfigurationContext(conf).resources.getText(res)?.toString()?.trim()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Resources.NotFoundException) {
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "đọc nhãn phụ bị chặn: $pkg"); null
        }
    }

    /** Hâm lại ngay ở lượt dựng bảng kế tiếp (bỏ nhịp [REWARM_MS]) — cho cầu kiểm thử đo E2. */
    fun rewarmSoon() { lastWarm = 0L }

    /** Mẫu `gói=nhãn phụ` (nhãn APP của hệ thống, không phải dữ liệu người dùng) — cho phép đo E2 qua cầu kiểm thử. */
    fun sample(max: Int): List<String> = cache.entries.filter { it.value.labels.isNotEmpty() }.take(max)
        .map { (p, e) -> p + "=" + e.labels.joinToString("|") }

    /** Số app / số nhãn phụ trong bộ nhớ — chỉ ĐẾM, không in chữ (cho `state` của cầu kiểm thử). */
    fun counts(): Pair<Int, Int> = cache.size to cache.values.sumOf { it.labels.size }
}
