package com.byd.clusternav.modules.hal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.hudRoadNameMode
import com.byd.clusternav.launcher.CtlJournalStore
import com.byd.clusternav.navigation.ClusterDebugRoadName
import com.byd.clusternav.navigation.ClusterDebugRoadName.Availability
import com.byd.clusternav.navigation.HudPathName
import com.byd.clusternav.navigation.HudPathNameGate
import com.byd.clusternav.navigation.HudRoadNameMode
import com.byd.clusternav.navigation.HudRoadNameRoute
import com.byd.clusternav.setHudRoadNameMode
import com.byd.clusternav.system.PackageQueries

/**
 * ═══ 3.1.12 · HUD-ROADNAME-ALT-PATH — phần CHẠM HỆ THỐNG của "Đường gửi tên đường lên HUD" ═════════════════════════════════
 *
 * Phần thuần (chế độ, kế hoạch, mã khúc, allow-list, quyết định khả dụng) ở `:core` ([HudRoadNameRoute], [ClusterDebugRoadName]).
 * Ở đây chỉ: đọc chế độ (prefs THEO XE), đọc sự thật PackageManager về `com.byd.clusterdebug`, gửi broadcast tường minh, ghi
 * MỘT dòng nhật ký mỗi lần tên đường đổi (chế độ · độ dài · rc — KHÔNG bao giờ ghi chữ của tên).
 *
 * ## Chỗ gọi (đường ghi tên đường DUY NHẤT vẫn là `writeNavFrame` của [BydHal] ⇐ `NavigationHudOwner`)
 *  • [plan] — đầu khối tên đường của mỗi real push (keep-alive dùng [HudRoadNameRoute.Plan.DEFAULT], không đọc gì);
 *  • [pushClusterDebug] — khi kế hoạch có B, cùng cổng dedupe [BydHalContentPush.pathNameGate] (kênh CLUSTER_DEBUG) ⇒ CHỈ
 *    gửi khi tên ĐỔI, đúng nhịp OEM của R9 (2.98); không nhịp/luồng mới; tên không đổi ⇒ không làm gì;
 *  • [clearIfSent] — `BydHal.clearNavFrame` (hết dẫn), TRƯỚC khi cổng bị xả: kênh B đã có tên ⇒ gửi tên xoá
 *    ([ClusterDebugRoadName.CLEAR_NAME]). Đường HAL không cần bước này (status=4 ẩn widget).
 *
 * ## CLAUDE.md §4 — bốn câu
 *  1. display: không chạm display/stack/wm; 2. app: CHỈ `com.byd.clusterdebug/.BroadcastReceiverCAN`, tên lớp cứng, broadcast
 *  tường minh; 3. loại lệnh: CHỈ bản ghi khúc tên đường — [ClusterDebugRoadName.allowed] kiểm lại NGAY trước khi gửi, không
 *  `wholeFrame`; 4. hoàn tác: hết dẫn ⇒ gửi tên xoá; TestDevice SIMULATE_DOWN không thấy đường ghi bền [SUY, doc §5].
 *  Tiến trình chết giữa chuyến ⇒ tên B có thể còn tới lượt dẫn sau / tắt máy — cùng mức với tên của đường HAL.
 */
object HudRoadNameTransport {

    private const val TAG = "KachiHudRoad"

    /** Sự thật PackageManager đổi khi cập nhật firmware, không đổi giữa chuyến ⇒ kế hoạch dùng bản ≤ 60 s; lúc GỬI đọc mới. */
    private const val AVAILABILITY_TTL_MS = 60_000L

    data class Status(
        val mode: HudRoadNameMode,
        val availability: Availability?,
        val lastAtMs: Long,
        val lastUnits: Int,
        val lastRc: String?,
        val lastMode: HudRoadNameMode?,
        val lastDegraded: Boolean,
    )

    @Volatile private var cachedAvailability: Availability? = null
    @Volatile private var cachedAtMs = 0L
    @Volatile private var lastAtMs = 0L
    @Volatile private var lastUnits = 0
    @Volatile private var lastRc: String? = null
    @Volatile private var lastMode: HudRoadNameMode? = null
    @Volatile private var lastDegraded = false

    fun mode(ctx: Context): HudRoadNameMode = Prefs.hudRoadNameMode(ctx)

    /** Kế hoạch cho một real push. Chế độ A/A48 ⇒ chỉ một lượt đọc prefs (không chạm PackageManager). */
    fun plan(ctx: Context): HudRoadNameRoute.Plan {
        val mode = mode(ctx)
        if (!mode.clusterDebug) return HudRoadNameRoute.plan(mode, clusterDebugAvailable = false)
        return HudRoadNameRoute.plan(mode, availability(ctx, fresh = false) == Availability.OK)
    }

    /** Đọc sự thật về ClusterDebug. [fresh] = bỏ bản nhớ (dựng trang Cài đặt · lúc gửi). */
    fun availability(ctx: Context, fresh: Boolean): Availability {
        val now = SystemClock.elapsedRealtime()
        val c = cachedAvailability
        if (!fresh && c != null && now - cachedAtMs < AVAILABILITY_TTL_MS) return c
        val a = readAvailability(ctx.applicationContext.packageManager)
        cachedAvailability = a
        cachedAtMs = now
        return a
    }

    private fun readAvailability(pm: PackageManager): Availability {
        return try {
            val pi = PackageQueries.packageInfo(pm, ClusterDebugRoadName.PACKAGE) ?: return Availability.NOT_INSTALLED
            val ai = pi.applicationInfo ?: return Availability.NOT_INSTALLED
            val receiver = receiverInfo(pm, ComponentName(ClusterDebugRoadName.PACKAGE, ClusterDebugRoadName.RECEIVER))
            ClusterDebugRoadName.availability(
                installed = true,
                system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                appEnabled = ai.enabled,
                versionCode = pi.longVersionCode,
                receiverFound = receiver != null,
                receiverExported = receiver?.exported == true,
                receiverEnabled = receiver?.enabled == true,
                receiverPermission = receiver?.permission,
            )
        } catch (e: RuntimeException) {
            // Binder chết giữa chừng ⇒ coi như không dùng được (lùi về A, dòng tình trạng nói rõ) — không làm hỏng khung dẫn.
            Log.w(TAG, "doc PackageManager loi: ${e.javaClass.simpleName}")
            Availability.NOT_INSTALLED
        }
    }

    /** Receiver vắng / bị tắt (cờ 0 không trả component bị tắt) ⇒ `null`. API 33+ dùng overload `ComponentInfoFlags`. */
    private fun receiverInfo(pm: PackageManager, cn: ComponentName): ActivityInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getReceiverInfo(cn, PackageManager.ComponentInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getReceiverInfo(cn, 0)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Đặt chế độ. B/C trên xe không có ClusterDebug dùng được ⇒ TỪ CHỐI (trả lý do), không lưu — bề mặt nào gọi cũng vậy.
     * @return `null` = đã lưu; khác `null` = lý do từ chối.
     */
    fun setMode(ctx: Context, mode: HudRoadNameMode, source: String): Availability? {
        if (mode.clusterDebug) {
            val a = availability(ctx, fresh = true)
            if (a != Availability.OK) {
                journal(ctx, "hudroad mode=${mode.code} REFUSED avail=${a.name} src=$source")
                return a
            }
        }
        val old = mode(ctx)
        Prefs.setHudRoadNameMode(ctx, mode)
        journal(ctx, "hudroad mode ${old.code}->${mode.code} src=$source")
        return null
    }

    /** Đường B cho một real push: cùng cổng dedupe kênh CLUSTER_DEBUG ⇒ chỉ gửi khi tên đổi. Token rc: ` CD_ROAD=<rc>`. */
    fun pushClusterDebug(ctx: Context, road: String, keepAlive: Boolean, rc: StringBuilder) {
        val gate = BydHalContentPush.pathNameGate
        if (road.isBlank() || !gate.shouldSend(HudPathNameGate.Channel.CLUSTER_DEBUG, road, keepAlive)) return
        val r = send(ctx, road)
        rc.append(" CD_ROAD=").append(r)
        gate.onResult(HudPathNameGate.Channel.CLUSTER_DEBUG, road, r)
    }

    /** Hết dẫn: kênh B đã có tên ⇒ gửi tên xoá. Gọi TRƯỚC `pathNameGate.reset()`. */
    fun clearIfSent(ctx: Context, rc: StringBuilder) {
        if (!BydHalContentPush.pathNameGate.sentSomething(HudPathNameGate.Channel.CLUSTER_DEBUG)) return
        val r = send(ctx, ClusterDebugRoadName.CLEAR_NAME)
        rc.append(" CD_ROAD_CLEAR=").append(r)
        journal(ctx, "hudroad clear path=B rc=$r")
    }

    /** Một broadcast tường minh. `"0"` = đã giao cho hệ thống (fire-and-forget, không có rc HAL). */
    private fun send(ctx: Context, road: String): String {
        val a = availability(ctx, fresh = true)
        if (a != Availability.OK) return "unavail:${a.name}"
        val bytes = ClusterDebugRoadName.encode(road)
        if (!ClusterDebugRoadName.allowed(bytes)) return "deny"   // cổng cứng §4 — gói không phải khúc tên đường
        val intent = Intent(ClusterDebugRoadName.ACTION)
            .setComponent(ComponentName(ClusterDebugRoadName.PACKAGE, ClusterDebugRoadName.RECEIVER))
            .putExtra(ClusterDebugRoadName.EXTRA, ClusterDebugRoadName.hex(bytes))
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        return try {
            ctx.applicationContext.sendBroadcast(intent)
            "0"
        } catch (e: SecurityException) {
            "!${e.javaClass.simpleName}"
        }
    }

    /**
     * MỘT dòng mỗi lần tên đường thật sự được gửi (bất kỳ đường nào): chế độ · số đơn vị · token rc của khối tên đường.
     * [pathRc] chỉ là các token `PATHNAME*=`/`sdk.road=`/`CD_ROAD=` — không bao giờ chứa chữ của tên.
     */
    fun noteSent(ctx: Context, plan: HudRoadNameRoute.Plan, road: String, pathRc: String) {
        val units = if (plan.hal) HudPathName.fit(road, plan.halMaxBytes).length else ClusterDebugRoadName.units(road)
        lastAtMs = SystemClock.elapsedRealtime()
        lastUnits = units
        lastRc = pathRc.trim()
        lastMode = plan.mode
        lastDegraded = plan.degraded
        journal(ctx, "hudroad sent mode=${plan.mode.code}${if (plan.degraded) " degraded=A" else ""} units=$units rc=${pathRc.trim()}")
    }

    fun status(ctx: Context): Status = Status(
        mode = mode(ctx),
        availability = cachedAvailability,
        lastAtMs = lastAtMs,
        lastUnits = lastUnits,
        lastRc = lastRc,
        lastMode = lastMode,
        lastDegraded = lastDegraded,
    )

    private fun journal(ctx: Context, line: String) = CtlJournalStore.journal(ctx).write(line)
}
