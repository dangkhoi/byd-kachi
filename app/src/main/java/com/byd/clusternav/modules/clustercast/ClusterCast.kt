package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.content.Intent
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.Prefs
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CHIẾU APP BẤT KỲ LÊN CLUSTER qua embedded-dadb (uid-2000 shell, localhost:5555).
 *
 * ★★★ TỔNG QUÁT HOÁ: cơ chế chiếu VỐN app-agnostic → BẤT KỲ app nào cũng bê lên cụm được. User curate danh sách
 * app ([castableApps]) trong Cài đặt chiếu; nút nổi long-press hiện menu, chọn 1 để chiếu; chạm = toggle.
 * ⚠ Cụm KHÔNG có cảm ứng (VD touch NONE) → chỉ hợp app ĐỂ NGÓ (nav/nhạc/dashboard/video). App cần set-up (Maps:
 * chọn tuyến) phải mở + set-up ở màn giữa TRƯỚC rồi mới chiếu (T1 GIỮ nguyên phiên dẫn).
 *
 * ĐÃ KIỂM CHỨNG TRÊN XE:
 *  1. VD cluster (`fission_bg_xdjaVirtualSurface`) tồn tại sẵn; đổi PROFILE (30/31) → TÁI TẠO VD id MỚI → phải DÒ động.
 *  2. RENDER (RE DashCast + kiểm chứng trên xe): T1 = MẶC ĐỊNH mọi app — `settings put global force_resizable_activities 0`
 *     → `am start --display <VD> --windowingMode 5` KHÔNG clear-task (resume task đang chạy → GIỮ dẫn) → `am task resize`
 *     (ép composite → hết trắng). App chưa chạy → freshLaunch. T3 (daemon app_process, [T3Daemon]) = dự phòng opt-in ([t3Apps]) khi T1 hụt.
 *  3. SCALE PER-APP (R6): dpi + bounds theo TỪNG app ([AppScale], [scaleOf]) thay global DPI+inset. Áp live [applyScaleLive].
 *  4. PROFILE ĐA-MODEL (R8): chuỗi chiếu/teardown + kích cụm nằm sau [ClusterProfile] (Seal DL3 = [30,16,35] / [18,0]).
 *  5. `wm density` = fix scale; `wm overscan` = khung mỹ thuật. Đổi app = COLD-ONLY (v0.63: bỏ warm-switch — nguồn
 *     treo WM NPE): dọn cụm → dựng VD mới. TEARDOWN: bê app khỏi VD →
 *     reset density/overscan → teardownSeq (Seal 18→0). PROFILE 30=cong giữ km/h · 31=chữ nhật full (toggle [keepKmh]).
 */
object ClusterCast {
    private val busy = AtomicBoolean(false)
    /**
     * ★★ W1-5 (senior review 2026-07-21) — LATCH PHẢI CÓ HẠN.
     *
     * `busy` được chiếm ở luồng GỌI rồi chỉ nhả trong `finally` của luồng nền, mà thân lệnh chạy trong
     * `adb.use { }` với đọc/ghi socket KHÔNG có timeout, cộng vài lần `Thread.sleep` hàng giây. adbd treo không
     * phải giả định — nó treo đúng lúc CarPlay/AA cắm vào và ngắt WiFi, tức đúng lúc người ta hay chiếu.
     * Khi đó: cụm đang chiếu, mà bấm TẮT thì bị `busy` chặn vĩnh viễn → không tài nào trả đồng hồ về được.
     *
     * Giờ latch có mốc thời gian: quá [BUSY_STALE_MS] thì thao tác sau được phép CƯỚP. An toàn vì mọi lệnh
     * nguy hiểm đều đã có guard theo sự thật (`applyBounds` từ chối task không ở trên VD), và `vdExec` vẫn
     * tuần tự hoá nên không có hai luồng cùng ghi vào VD.
     */
    @Volatile private var busySince = 0L

    /** Chiếm latch. Trả false nếu đang bận VÀ chưa quá hạn. Quá hạn → cướp và ghi log cho hiện trường thấy. */
    private fun takeBusy(log: (String) -> Unit): Boolean {
        if (busy.compareAndSet(false, true)) { busySince = android.os.SystemClock.elapsedRealtime(); return true }
        val held = android.os.SystemClock.elapsedRealtime() - busySince
        if (held < BUSY_STALE_MS) return false
        log("⚠ thao tác trước treo ${held / 1000}s (adb không phản hồi?) → giành quyền để chạy tiếp")
        busySince = android.os.SystemClock.elapsedRealtime()
        return true
    }
    // ★ 1 SERIAL EXECUTOR cho MỌI thao tác dadb sửa VD (cast/stop/applyScaleLive/applyGlobalAnim) → chạy TUẦN TỰ,
    //   KHÔNG BAO GIỜ 2 luồng cùng ghi wm density / am task resize / service call lên 1 VD (khử tận gốc race
    //   scale-vs-cast/stop: busy/scaleApplying giờ chỉ là cờ feedback + coalesce, không phải rào chống-đua DUY NHẤT).
    private val vdExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    // ★ SINGLE-FLIGHT áp-live (review#2): chỉ 1 luồng [applyScaleLive] chạy tại 1 thời điểm → nhấn mũi tên DỒN DẬP
    //   không mở nhiều kết nối dadb song song cùng `am task resize` 1 task. busy (cast/stop) là mutex RIÊNG.
    private val scaleApplying = AtomicBoolean(false)
    // ★ có nhấn MỚI rơi vào lúc luồng áp-live đang bay → chạy thêm 1 vòng (không nuốt nhấn cuối).
    private val scaleDirty = AtomicBoolean(false)
    private val PKG_OK = Regex("^[a-zA-Z0-9._]+$")

    const val PROFILE_CURVED = 30
    const val PROFILE_RECT = 31
    private const val CMD_DI40 = 35   // (cmd16 CMD_PROJECT đã bỏ ở v0.61: warm-switch KHÔNG tái tạo VD nữa; cold path dùng raw 16 trong ClusterProfile.castSeq)
    // Teardown codes (18=đóng chiếu, 0=refresh video) giờ nằm trong ClusterProfile.teardownSeq (Seal = [18,0]).

    // ★ TẮT animation hệ thống LÚC CHIẾU → transition move-stack giữa 2 màn mượt/tức thì (hết lag đổi app). Trả lại khi STOP.
    // ★ v0.36 BỎ `animator_duration_scale` khỏi danh sách: 2 khoá kia là của WindowManager (transition chuyển màn) —
    //   đúng thứ ta cần. Còn animator_duration_scale được đẩy THẲNG vào mọi tiến trình đang chạy (ActivityThread
    //   CoreSettingsObserver) và điều khiển ValueAnimator TRONG app: đặt 0 làm mọi animator kết thúc tức thì, nên
    //   camera/marker của app bản đồ hết nội suy mượt giữa 2 fix → nhìn thành giật. Không đụng nữa.
    private val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale")
    /** Khoá bản ≤0.35 từng ghi 0 rồi có thể KHÔNG trả lại — cần sửa di chứng 1 lần. Xem [repairLegacyAnim]. */
    private const val LEGACY_ANIM_KEY = "animator_duration_scale"
    @Volatile private var savedAnim = "1.0"   // giá trị gốc để khôi phục (đọc trước khi tắt)
    /**
     * ★ Giá trị animation AN TOÀN để trả lại. Chiếu 2 lần liên tiếp (hoặc app chết giữa chừng rồi chiếu lại) làm
     * savedAnim đọc trúng "0" ĐANG do chính ta đặt → stop() sẽ ghim animation = 0 VĨNH VIỄN (máy trông "đơ",
     * app phụ thuộc callback animation chạy sai). Không bao giờ khôi phục về 0/không hợp lệ.
     */
    private val savedAnimSafe: String get() = savedAnim.toFloatOrNull()?.takeIf { it > 0f }?.let { savedAnim } ?: "1.0"

    /**
     * Giá trị animation NÊN trả về khi không đọc được giá trị gốc thật của người dùng: theo đúng tuỳ chọn
     * "Mượt UI" mà họ đang bật/tắt trong app. Đây là nguồn sự thật duy nhất ta thật sự biết về ý người dùng.
     */
    private fun preferredAnim(ctx: Context) = if (Prefs.animOpt(ctx)) "0.5" else "1.0"
    /** Chuỗi teardown ĐÃ CHỐT lúc bắt đầu chiếu — user đổi hồ sơ giữa chừng không làm teardown chạy sai chuỗi. */
    @Volatile private var activeTeardown: List<Int> = emptyList()

    data class AppInfo(val label: String, val pkg: String, val icon: android.graphics.drawable.Drawable? = null)

    // ── state bền (prefs) ──
    @Volatile var keepKmh = true                 // true = cong SL6 giữ km/h | false = chữ nhật full
    // Profile trả cụm về KHI TEARDOWN. -1 = KHÔNG set profile sau RESTORE(0) — chỉ 18→0 như recipe DashCast.
    // ★ 2026-07-19: hardcode sc(31) cũ chốt cụm ở profile CHỮ NHẬT (mất km/h) → nghi phá luôn layout nav-lane
    //   (broadcast nav vẫn tới qua CAN nhưng cụm không có widget để vẽ) → nav chết tới mức phải reboot. -1 tránh điều đó.
    //   Nếu sau chiếu profile CONG (30) đồng hồ bị "dính cong" → set = 31 (hoặc mã profile-gốc đúng) qua setTeardownProfile.
    @Volatile var teardownProfile = -1
    @Volatile var insetH = 0                      // fallback overscan ngang (px). Ưu tiên AppScale rect; giữ làm khung mỹ thuật.
    @Volatile var insetV = 90                     // fallback overscan dọc (px) — mặc định chừa cho curve khỏi cắt.
    @Volatile var clusterDpi = 200                // DPI fallback — làm default cho AppScale khi app chưa cấu hình. tune 120–320.
    // ★ SCALE PER-APP (R6, T-B): map pkg→AppScale (dpi + rect L/T/R/B). Thay model global DPI+inset cố định.
    //   rect=-1 → auto = full VD (giữ hành vi cũ). Xem [AppScale], [scaleOf], [setScale], [applyScaleLive].
    @Volatile var appScales: Map<String, AppScale> = emptyMap(); private set
    // ★ T3 — v0.36 ĐÃ HẠ CẤP khỏi đường ĐẶT APP. Verify source AOSP 10: T3Daemon gọi ĐÚNG binder
    //   `IActivityTaskManager.moveStackToDisplay` mà `am display move-stack` gọi (ActivityManagerShellCommand:2516 →
    //   ATMS:3395), CÙNG uid-2000, CÙNG quyền INTERNAL_SYSTEM_WINDOW → daemon KHÔNG đặt được app ở chỗ R1 (shell) hụt.
    //   2 lệnh còn lại của nó cũng chết: setTaskWindowingMode(5) bị validateWindowingMode downgrade im lặng khi
    //   freeform chưa sống, resizeTask ném trước cả khi đọc resizeMode. → Giờ chỉ dùng như ÉP FREEFORM sau khi app
    //   ĐÃ bám VD, và chỉ khi freeform đã sống (sau power-cycle). Xem [T3Daemon], [placeT3].
    @Volatile var t3Apps: Set<String> = emptySet(); private set
    // ★ R2 "phá phiên" (force-stop + fresh-launch): app trong set này KHÔNG được phép chạy R2 — dùng cho app mà mất
    //   phiên là mất luôn giá trị (Android Auto/CarPlay đang chiếu điện thoại, app dẫn đường đang chạy tuyến).
    @Volatile var keepSessionApps: Set<String> = emptySet(); private set
    /**
     * App muốn cụm ở kiểu THẲNG (31 — ảnh full, mất km/h). Ngoài set = CONG (30 — giữ km/h).
     * ★ v0.37: thay cờ TOÀN CỤC [keepKmh]. Trước đây kiểu cụm là 1 cờ dùng chung nên đổi app không đổi kiểu →
     * đúng hiện tượng "chuyển app bị dính mode của app trước".
     */
    @Volatile var rectProfileApps: Set<String> = emptySet(); private set
    // ★ Gói đang bị CHẶN PIP (appops PICTURE_IN_PICTURE=ignore) — LƯU BỀN để còn trả lại nếu app chết giữa chừng.
    @Volatile var pipBlockedPkg: String = ""; private set
    /**
     * ★ AUTOSTART (v0.42): app tự mở + tự đẩy sang cụm khi nổ máy, dùng đúng kích thước đã chỉnh trước đó.
     * CHỈ MỘT app (chuỗi chiếu đụng vào phần cứng cụm, chạy song song 2 app là hỏng). "" = tắt.
     */
    @Volatile var autoCastPkg: String = ""; private set
    @Volatile private var pipPrevMode: String = ""   // chế độ appops TRƯỚC khi ta chặn → trả lại ĐÚNG cái user đang có
    @Volatile var castableApps: List<String> = emptyList(); private set   // app user tick cho menu nút nổi
    @Volatile var lastCastApp: String = ""; private set                   // app chiếu gần nhất (tap = chiếu lại)
    @Volatile var casting = false; private set    // đang chiếu? (bong bóng toggle + đổi icon theo cờ này)
    @Volatile var lastDisplayId = -1; private set

    // ★ Kích thước VD cụm THẬT — auto-detect từ dumpsys ([DisplayParse.realSize]) lần chiếu gần nhất. Dùng cho rect per-app UI
    //   (R8: KHÔNG hardcode 1920×720 — lấy kích cụm thật). 0 = chưa chiếu lần nào → UI dùng ClusterProfile fallback.
    @Volatile var lastClusterW = 0; private set
    @Volatile var lastClusterH = 0; private set
    private fun rememberClusterSize(w: Int, h: Int) { if (w > 0 && h > 0) { lastClusterW = w; lastClusterH = h } }

    /**
     * ★★ W2-5 (senior review, phần LỖI): ĐO CỤM NGAY TRONG TIẾN TRÌNH — không cần shell, không cần đang chiếu.
     *
     * Vấn đề gốc: `lastClusterW/H` chỉ được đặt SAU một lần chiếu thành công, nên mọi người dùng mới chỉnh khung
     * lần đầu là chỉnh dựa trên con số ĐOÁN (1920×720 hardcode trong hồ sơ). Trên đời xe có cụm khác kích thước
     * thì khung lưu ra sai ngay từ đầu, và `overscanOn` lại kẹp inset âm về 0 nên "70%" âm thầm thành "100%".
     *
     * `DisplayManager` cho biết kích thước thật của MỌI display mà không cần quyền gì — DashCast cũng đo đúng
     * kiểu này (`display.getRealSize`). Gọi lúc mở màn hình là đủ để mọi phép tính khung dùng số THẬT.
     * Trả về true nếu đo được.
     */
    fun measureClusterInProcess(ctx: Context): Boolean = runCatching {
        val dm = ctx.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val d = dm.displays.firstOrNull { dsp ->
            dsp.displayId != 0 && dsp.name.orEmpty().lowercase().let { it.contains("fission") || it.contains("xdja") }
        } ?: return false
        val p = android.graphics.Point().also { @Suppress("DEPRECATION") d.getRealSize(it) }
        if (p.x <= 0 || p.y <= 0) return false
        rememberClusterSize(p.x, p.y)
        true
    }.getOrDefault(false)

    /** observer cho bong bóng: đổi trạng thái chiếu → cập nhật icon/nền nút nổi. */
    @Volatile var onCastingChanged: (() -> Unit)? = null
    private fun setCasting(v: Boolean) {
        if (casting != v) { casting = v; runCatching { onCastingChanged?.invoke() } }
    }

    private const val PREF = "clustercast"
    private const val DEFAULTS_KEY = "defaultModes_v37"
    private const val RECT_FIX_KEY = "rectDriftFix_v37"
    /** Chuỗi export của profile ĐANG giữ cụm — để tiến trình mới vẫn teardown đúng service/chuỗi lệnh. */
    private const val KEY_ACTIVE_PROFILE = "activeProfile"
    /** Chờ đầu xe khởi động xong dịch vụ (AutoContainer/adbd/launcher) rồi mới tự chiếu. */
    private const val BOOT_CAST_DELAY_MS = 25000L
    /** Latch giữ quá lâu = thao tác trước treo ở I/O → cho phép thao tác sau giành quyền. */
    private const val BUSY_STALE_MS = 90_000L
    /** Số nhịp watchdog LIÊN TIẾP phải thấy app biến mất khỏi cụm trước khi tự teardown (~2 phút). */
    private const val WATCHDOG_MISSES = 2
    /** Ngưỡng "xe đứng yên" cho tự chiếu. Chặt hơn cổng cold-seed vì đây là thao tác đổi phần cứng cụm. */
    private const val AUTO_CAST_MAX_MPS = 0.5   // đổi hậu tố khi muốn ép lại mặc định ở bản sau
    const val PKG_ANDROID_AUTO = "com.byd.androidauto"
    const val PKG_CARPLAY = "com.byd.carplay.ui"
    fun setKeepKmh(ctx: Context, v: Boolean) { keepKmh = v; save(ctx) }
    /** Profile trả cụm về khi teardown. -1 = chỉ 18→0 (mặc định, an toàn cho nav-lane). ≥0 = set mã profile đó sau RESTORE. */
    fun setTeardownProfile(ctx: Context, p: Int) { teardownProfile = p; save(ctx) }
    /** true = cho phép daemon T3 ép freeform SAU KHI app đã bám VD (chỉ có tác dụng khi freeform đã sống). */
    fun setT3(ctx: Context, pkg: String, on: Boolean) { t3Apps = if (on) t3Apps + pkg else t3Apps - pkg; save(ctx) }
    fun isT3(pkg: String) = pkg in t3Apps
    /**
     * App có phải loại CHIẾU MÀN ĐIỆN THOẠI không (CarPlay / Android Auto / projection sink) — nhận diện theo
     * chuỗi component/gói, KHÔNG hardcode một tên cụ thể. Mất phiên của loại này là mất luôn, phải khởi động lại xe.
     * Tên activity từ firmware đã đọc: `com.google.android.projection.sink.ui.AAPVideoActivity`, `com.byd.carplay.ui`.
     */
    private val PROJECTION_HINTS = listOf("projection.sink", "aapactivity", "aapvideo", "carplay", "androidauto")
    fun isPhoneProjection(comp: String?, pkg: String): Boolean {
        val hay = ((comp ?: "") + " " + pkg).lowercase()
        return PROJECTION_HINTS.any { hay.contains(it) }
    }

    /**
     * ★ v0.60 TEARDOWN-GUARD (P0) — mọi stack CHIẾU-ĐIỆN-THOẠI (CarPlay/Android Auto) đang bám VD [vd].
     * Dùng để BÊ RA display 0 TRƯỚC khi huỷ/tái tạo VD (cmd16 re-project · teardownSeq · evictVd). Gốc lỗi
     * hiện trường 22/07 (diag-0722-172848): AOSP 10 release ActivityDisplay của VD khi sink CÒN BÁM →
     * `mDisplayContent=null` → cửa sổ mồ côi (WM thấy, AM không) → chỉ TẮT MÁY XE mới sạch.
     * PURE (chỉ đọc [entries]) → unit-test off-device được (SinkGuardTest).
     */
    fun phoneProjectionSinksOn(entries: List<StackEntry>, vd: Int): List<StackEntry> {
        if (vd < 1) return emptyList()
        return entries.filter { it.displayId == vd && isPhoneProjection(it.comp, it.pkg) }.distinctBy { it.stackId }
    }

    /** true = CẤM rung R2 (force-stop + mở lại) cho app này → giữ phiên bằng mọi giá, thà không lên cụm. */
    fun setKeepSession(ctx: Context, pkg: String, on: Boolean) { keepSessionApps = if (on) keepSessionApps + pkg else keepSessionApps - pkg; save(ctx) }
    fun isKeepSession(pkg: String) = pkg in keepSessionApps
    /** Đặt app tự chiếu khi khởi động. Truyền "" để tắt. Chọn app khác = thay app cũ (chỉ 1 app duy nhất). */
    fun setAutoCast(ctx: Context, pkg: String) { autoCastPkg = pkg; save(ctx) }
    fun isAutoCast(pkg: String) = autoCastPkg.isNotEmpty() && autoCastPkg == pkg
    /** true = app này muốn cụm THẲNG (31). false = CONG (30, giữ km/h). */
    fun isRectProfile(pkg: String) = pkg in rectProfileApps
    fun setRectProfile(ctx: Context, pkg: String, on: Boolean) {
        rectProfileApps = if (on) rectProfileApps + pkg else rectProfileApps - pkg; save(ctx)
    }
    /** Mã lệnh kiểu cụm cho [pkg] — thay chỗ dùng [keepKmh] toàn cục. */
    /** Opcode kiểu cụm cho [pkg] theo hồ sơ [p]. null = đời xe không đổi kiểu được (xem [ClusterProfile.styleOps]). */
    private fun styleCmdFor(p: ClusterProfile, pkg: String): Int? =
        p.styleOps?.let { (curved, rect) -> if (isRectProfile(pkg)) rect else curved }

    // ── SCALE PER-APP (R6, T-B) ──
    /** AppScale của [pkg]. Chưa cấu hình → default (dpi = [clusterDpi] fallback, rect auto = full VD). */
    fun scaleOf(pkg: String): AppScale = appScales[pkg] ?: AppScale(dpi = clusterDpi)
    /** Lưu AppScale cho [pkg] (cập nhật map + prefs). Áp lên cụm ngay gọi riêng [applyScaleLive]. */
    fun setScale(ctx: Context, pkg: String, scale: AppScale) {
        appScales = appScales.toMutableMap().apply { put(pkg, scale) }
        save(ctx)
    }
    fun setCastableApps(ctx: Context, apps: List<String>) { castableApps = apps.distinct(); save(ctx) }
    private fun setLastCastApp(ctx: Context, p: String) { lastCastApp = p; save(ctx) }
    private fun save(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean("keepKmh", keepKmh).putInt("insetH", insetH).putInt("insetV", insetV)
            .putString("castable", castableApps.joinToString(",")).putString("lastApp", lastCastApp)
            .putInt("dpi", clusterDpi).putInt("teardownProfile", teardownProfile)
            .putString("t3", t3Apps.joinToString(","))
            .putString("keepSession", keepSessionApps.joinToString(","))
            .putString("rectProfile", rectProfileApps.joinToString(","))
            .putString("pipBlocked", pipBlockedPkg).putString("pipPrevMode", pipPrevMode)
            .putString("autoCast", autoCastPkg)
            .putString("appscales", AppScale.serializeMap(appScales)).apply()
    }
    fun loadPrefs(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        keepKmh = sp.getBoolean("keepKmh", true); insetH = sp.getInt("insetH", 0); insetV = sp.getInt("insetV", 90)
        castableApps = (sp.getString("castable", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        lastCastApp = sp.getString("lastApp", "") ?: ""
        clusterDpi = sp.getInt("dpi", 200).coerceIn(120, 320)
        teardownProfile = sp.getInt("teardownProfile", -1)
        t3Apps = (sp.getString("t3", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        keepSessionApps = (sp.getString("keepSession", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        rectProfileApps = (sp.getString("rectProfile", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        pipBlockedPkg = sp.getString("pipBlocked", "") ?: ""
        pipPrevMode = sp.getString("pipPrevMode", "") ?: ""
        autoCastPkg = sp.getString("autoCast", "") ?: ""
        appScales = AppScale.parseMap(sp.getString("appscales", "") ?: "")
        applyDefaultModes(sp)
        migrateDriftedRects(sp)
    }

    /**
     * ★ CHẾ ĐỘ MẶC ĐỊNH THEO APP (chốt từ test trên xe 2026-07-21, Seal):
     *   • `com.byd.androidauto` → ⊞ (t3Apps). Trên xe AA chỉ lên được ở chế độ này.
     *   • `com.byd.carplay.ui`  → chế độ THƯỜNG (không dấu). CarPlay lên ngon ở mặc định nên đừng đụng vào.
     * Chạy đúng MỘT lần, gắn cờ theo phiên bản (DEFAULTS_KEY) — để bản trước đã ghi `keepSession` (bật ◈ cho cả
     * hai app, mà ◈ lại CẤM đúng cái rung đưa AA lên được) vẫn được sửa lại khi nâng cấp. User đổi tay sau đó
     * thì tôn trọng, không ghi đè nữa.
     */
    private fun applyDefaultModes(sp: android.content.SharedPreferences) {
        if (sp.getBoolean(DEFAULTS_KEY, false)) return
        // ★★ W1-4 (senior review): MIGRATION KHÔNG BAO GIỜ ĐƯỢC NỚI QUYỀN.
        //   Bản trước làm `keepSessionApps - AA - CarPlay`, tức là TỰ GỠ tấm bảo vệ của người dùng đối với rung R3
        //   (force-stop) cho đúng hai app mà mất phiên là mất luôn — Android Auto/CarPlay đang chiếu điện thoại.
        //   Mặc định chỉ được đi theo hướng THẬN TRỌNG hơn. Giờ chỉ bật ⊞ cho AA (không phá gì), không gỡ ◈ của ai.
        t3Apps = t3Apps + PKG_ANDROID_AUTO
        sp.edit()
            .putString("t3", t3Apps.joinToString(","))
            .putBoolean(DEFAULTS_KEY, true).apply()
    }

    /**
     * ★ SỬA DI CHỨNG KHUNG TRÔI (v0.37, 1 lần). Bản ≤0.36 có lỗi [AppScale.nudgeRect] kẹp từng cạnh độc lập:
     * chạm sàn [AppScale.MIN_PX] rồi mà bấm tiếp thì khung KHÔNG nhỏ thêm mà TRÔI dần. Vết để lại trên xe là
     * khung 1296×160 lệch tâm (160 = đúng sàn). Khung như thế gần như chắc chắn là rác chứ không ai cố tình đặt,
     * và người dùng KHÔNG tự thoát ra được nếu chỉ bấm nút thu nhỏ. Trả về auto để bắt đầu lại sạch.
     */
    private fun migrateDriftedRects(sp: android.content.SharedPreferences) {
        if (sp.getBoolean(RECT_FIX_KEY, false)) return
        val fixed = appScales.mapValues { (_, v) ->
            val w = v.rectR - v.rectL; val h = v.rectB - v.rectT
            if (!v.isAuto && (w <= AppScale.MIN_PX || h <= AppScale.MIN_PX)) AppScale(dpi = v.dpi) else v
        }
        appScales = fixed
        sp.edit().putString("appscales", AppScale.serializeMap(fixed)).putBoolean(RECT_FIX_KEY, true).apply()
    }

    // ── liệt kê app đã cài (PackageManager — chạy in-process, KHÔNG cần dadb) ──
    /** mọi app có launcher activity (để màn Cài đặt chiếu cho tick). Sắp theo tên. */
    fun listInstalledApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, runCatching { it.loadIcon(pm) }.getOrNull()) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }
    fun labelOf(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
    /** icon app (cho lưới chọn app + menu bong bóng). null nếu không có. */
    fun iconOf(ctx: Context, pkg: String): android.graphics.drawable.Drawable? =
        runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()

    /**
     * ★ W2-1: KHÔNG còn `svcName` toàn cục. Profile ĐANG mở cụm được LƯU BỀN (chuỗi export) ngay khi bắt đầu
     * chiếu, và mọi đường trả đồng hồ đọc lại từ đó — kể cả ở tiến trình mới. Xoá trong `finally` của stop().
     * KHÔNG lưu `vd`: opcode 16 tái tạo VD với id MỚI, nên vd phải luôn dò lại (xem CLAUDE.md §5).
     */
    private fun activeProfile(ctx: Context): ClusterProfile {
        val sp = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val saved = sp.getString(KEY_ACTIVE_PROFILE, "") ?: ""
        return (if (saved.isNotBlank()) ClusterProfile.parse(saved) else null) ?: ClusterProfile.resolve(ctx)
    }
    private fun setActiveProfile(ctx: Context, p: ClusterProfile?) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_PROFILE, p?.export() ?: "").apply()
    }

    /**
     * Ghi `wm density` CHỈ KHI giá trị đang áp khác giá trị muốn.
     * ★ v0.42 (RE + verify AOSP): mỗi lệnh `wm density` là một CONFIG CHANGE → framework RELAUNCH activity đang
     * chiếu (destroy + `onCreate` mới). App nào giữ cửa sổ nổi theo PROCESS — như bong bóng tốc độ của Vietmap —
     * thì cửa sổ cũ KHÔNG bị thu hồi khi activity chết, nên mỗi lần relaunch lại chồng thêm một bong bóng.
     * Đó là gốc của hiện tượng "3-4 bong bóng đè lên nhau". Không ghi lại giá trị đã đúng = bớt một lần relaunch.
     */
    private fun setDensityIfNeeded(sh: (String) -> String, vd: Int, want: Int) {
        if (vd < 1) return
        if (DisplayParse.density(sh("dumpsys window displays"), vd)?.first == want) return
        sh("wm density $want -d $vd")
    }
    private fun overscanArg() = "$insetH,$insetV,$insetH,$insetV"

    /**
     * Chiếu 1 app lên cụm. pkg rỗng → dùng [lastCastApp] → app đầu trong [castableApps]. Bê task đang chạy (giữ state);
     * chưa chạy thì mở ở màn giữa rồi bê. Không phải map thì mặc định vẫn theo [keepKmh] (user chỉnh cong/thẳng per ý).
     */
    /**
     * @param allowDestructive false = CẤM rung R3 (`am force-stop`). Dùng cho mọi lần chiếu KHÔNG do người dùng
     *   bấm tại chỗ (tự chiếu lúc nổ máy). Người đang nhìn màn hình và tự bấm thì vẫn được phép leo R3.
     */
    fun cast(ctx: Context, pkg: String, allowDestructive: Boolean = true, log: (String) -> Unit) {
        if (!takeBusy(log)) { log("⏳ đang chạy 1 thao tác cụm — đợi xong"); return }
        val app = ctx.applicationContext
        val log = castLogger(app, "cast", log)   // ★ v0.60 RT1.6: tee ra file để chẩn đoán khi cắm CP/AA (WiFi tắt)
        vdExec.execute {
            try {
                runCatching {
                    val target = pkg.ifBlank { lastCastApp }.ifBlank { castableApps.firstOrNull() ?: "" }
                    if (target.isBlank() || !PKG_OK.matches(target)) { log("❌ chưa chọn app — vào Cài đặt chiếu tick app trước"); return@runCatching }
                    dadb.Dadb.create("localhost", 5555, AdbKeys.ensure(app)).use { adb ->
                        fun sh(c: String): String { val r = adb.shell(c); val e = r.errorOutput.trim(); if (e.isNotEmpty()) log("  ⚠ $e"); return r.output.trim() }

                        if (!isInstalled(adb, target)) { log("❌ app $target chưa cài trên xe"); return@use }
                        log("① app = ${labelOf(app, target)} ($target)")

                        // ★ trả lại quyền PIP cho app bị chặn ở lần chiếu TRƯỚC (kể cả lần đó kết thúc bằng crash → stop()
                        //   không chạy). Idempotent, làm trước mọi thứ để không bao giờ để rơi state của user.
                        restorePip(app, log) { c -> sh(c) }

                        var ents = StackParse.parse(sh("am stack list"))
                        var mine = StackParse.pick(ents, target)
                        if (mine == null) {
                            val comp = CastShell.resolveComp(adb, target) ?: run { log("❌ không mở được $target (không có launcher activity)"); return@use }
                            log("② app chưa chạy → mở ở màn giữa: $comp"); sh("am start -n $comp"); Thread.sleep(2500)
                            ents = StackParse.parse(sh("am stack list")); mine = StackParse.pick(ents, target)
                            if (mine == null) { log("❌ mở app xong vẫn không thấy stack → HỦY"); return@use }
                        } else log("② app đang chạy (${mine.brief()}) — BÊ NGUYÊN (giữ state)")
                        // ⓞ in TOÀN BỘ stack của app (gồm cả PIP) → hiện trường thấy ngay app có mấy task/có PIP không.
                        StackParse.of(ents, target).let { all ->
                            if (all.size > 1) log("  ⓞ $target có ${all.size} task: " + all.joinToString(" · ") { it.brief() })
                            StackParse.pinnedOf(ents, target).forEach { pinned ->
                                // app-op chỉ chặn LẦN VÀO PIP KẾ TIẾP — cửa sổ PIP đang nổi phải xử lý tay, không thì
                                // chiếu xong vẫn còn mini-player đè trên MÀN GIỮA của tài xế.
                                // ★ Ưu tiên đường KHÔNG PHÁ (bung về fullscreen). `am stack remove` giết phiên phát —
                                //   với app projection thì mất phiên là mất luôn, nên chỉ dùng khi hết cách và app
                                //   KHÔNG bật ◈ giữ-phiên.
                                val comp = CastShell.resolveComp(adb, target)
                                if (comp != null) sh("am start --windowingMode 1 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp 2>&1")
                                Thread.sleep(400)
                                val still = StackParse.pinnedOf(StackParse.parse(sh("am stack list")), target)
                                    .any { it.stackId == pinned.stackId }
                                when {
                                    !still -> log("  ⓟ đã bung cửa sổ PIP cũ về toàn màn (${pinned.brief()})")
                                    isKeepSession(target) -> log("  ⚠ còn cửa sổ PIP cũ (${pinned.brief()}) — app bật ◈ nên KHÔNG dẹp; tự tắt PIP nếu vướng")
                                    // ★ v0.42 BỎ `am stack remove`: nó chỉ an toàn khi stack VẪN đang pinned lúc lệnh
                                    //   tới nơi. Nếu trong khoảnh khắc đó stack đã hết pinned thì AOSP rơi vào nhánh
                                    //   `removeTaskByIdLocked(..., killProcess=true, REMOVE_FROM_RECENTS)` — GIẾT app
                                    //   của người dùng và xoá khỏi recents. Đổi thời gian lấy được bằng rủi ro đó là
                                    //   không đáng; PIP còn lại thì báo để user tự tắt.
                                    else -> log("  ⚠ còn cửa sổ PIP cũ (${pinned.brief()}) — tự tắt PIP trên màn giữa nếu thấy vướng")
                                }
                            }
                        }
                        // ★ CHẶN PIP trước khi chiếu: chính 2 rung `am start` của ta bật cờ supportsEnterPipOnTaskSwitch
                        //   trên activity đang focus → lần pause kế tiếp app tự vào PIP, và A10 pin PIP lên ĐÚNG display
                        //   app đang ở (= cụm). appops enforce trong system_server nên bản mod cũng không lách được.
                        blockPip(app, target, log) { c -> sh(c) }

                        // ── ★ v0.63 COLD-ONLY: KHÔNG còn warm-switch (đã bỏ — nguồn treo + mồ côi). curVd chỉ để:
                        //   (a) chạy guard mồ côi ngay bên dưới; (b) quyết có cần dọn cụm trước khi cold-cast không.
                        val curVd = DisplayParse.clusterDisplayId(sh("dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'"))
                        // ★ CHẶN TRƯỚC KHI BẮN. Đặt sau `placeAppOnVd` là quá muộn: lúc đó app đang dẫn đường đã
                        //   bị bê khỏi cụm, cụm đã bị cấu hình lại, VD đã bị tái tạo — rồi mới báo "không làm được".
                        divergenceOn({ c -> sh(c) }, curVd)?.let {
                            log("⛔ $it")
                            log("   (nút TẮT vẫn dùng được — nó chỉ trả đồng hồ, không đụng stack)")
                            return@use
                        }
                        // ★★ v0.64: SWITCH = HOT-SWAP (VALIDATED on-car 2026-07-23 bằng adb manual test, NPE=0).
                        //   Freeze THẬT không phải warm-vs-cold — mà là HUỶ/TÁI TẠO VD lúc switch (warm=cmd16, cold=teardown)
                        //   TRONG KHI app đang transition off VD → AppWindowToken.loadAnimation gọi getDisplayInfo() trên
                        //   DisplayContent NULL → WM NPE lặp → treo head unit. CHỨNG MINH trên xe: đặt app MỚI lên VD
                        //   ĐANG SỐNG rồi bê app cũ ra SAU (VD KHÔNG bao giờ rỗng/bị huỷ) = NPE=0, không treo.
                        //   → VD đang có app (isWarm) = SWITCH → HOT-SWAP (tuyệt đối KHÔNG teardown/cmd16).
                        //     VD chưa có app (first-cast) → COLD path (castSeq tạo VD mới tinh — proven an toàn).
                        if (StackParse.isWarm(curVd, StackParse.parse(sh("am stack list")))) {
                            hotSwapOnVd(app, adb, { c -> sh(c) }, target, curVd, log)
                            return@use
                        }

                        val prof = ClusterProfile.resolve(app)
                        setActiveProfile(app, prof)     // ★ LƯU BỀN hồ sơ đang giữ cụm (tiến trình mới vẫn teardown đúng)
                        activeTeardown = prof.teardownSeq        // ★ chốt chuỗi trả đồng hồ theo hồ sơ ĐANG mở cụm
                        var clusterMutated = false
                        try {
                            // ★ TẮT animation hệ thống (lưu gốc để trả lại khi stop) → transition move-stack tức thì, hết lag đổi màn
                            // ★★ W2-8(b): "giá trị gốc" phải là của NGƯỜI DÙNG, không phải giá trị do chính ta ghi.
                            //   Prefs.animOpt mặc định BẬT → MainActivity ghi 0.5 toàn cục mỗi lần mở app → đọc lại
                            //   ở đây ra 0.5 và ta lưu nhầm nó thành "gốc". Bấm TẮT là chốt máy ở 0.5 vĩnh viễn,
                            //   người dùng không bao giờ lấy lại được 1.0 dù có tắt "Mượt UI".
                            //   Quy tắc: chỉ tin giá trị đọc được khi nó KHÔNG PHẢI thứ app này đang áp.
                            savedAnim = sh("settings get global window_animation_scale")
                                .let { if (it.isBlank() || it == "null") "1.0" else it }
                                .let { read ->
                                    val ours = if (Prefs.animOpt(app)) "0.5" else null
                                    if (read == "0" || read == "0.0" || read == ours) preferredAnim(app) else read
                                }
                            for (k in ANIM_KEYS) sh("settings put global $k 0")
                            clusterMutated = true   // ★ vũ trang rollback TRƯỚC khi đổi cụm: mọi lỗi từ đây (kể cả sc() ném) đều trả đồng hồ
                            // ③–⑤ chiếu THEO PROFILE (Seal DL3 = [30,16,35], timing 3s/3s/2s giữ nguyên). cmd cong (30) → 31 nếu user chọn "thẳng".
                            log("③–⑤ profile ${prof.id}: chiếu [${prof.castSeq.joinToString(",")}] ${if (!prof.supportsStyle) "(đời xe này không đổi kiểu)" else if (isRectProfile(target)) "(thẳng — full)" else "(cong — giữ km/h)"}")
                            for (raw in prof.castSeq) {
                                // opcode kiểu do HỒ SƠ khai; hồ sơ nào không có styleOps thì castSeq giữ nguyên
                                val cmd = if (prof.styleOps != null && raw == prof.styleOps.first)
                                    (styleCmdFor(prof, target) ?: raw) else raw
                                log("   cmd $cmd"); sh(prof.svcCall(cmd)); Thread.sleep(castSleepMs(raw))
                            }

                            var vd = -1
                            for (i in 0 until 16) {
                                vd = DisplayParse.clusterDisplayId(sh("dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'"))
                                if (vd >= 1) break
                                Thread.sleep(500)
                            }
                            if (vd < 1) { log("❌ không dò thấy VD cụm → rollback"); rollback(app, adb, prof.teardownSeq, log); return@use }
                            log("⑥ VD cụm = display $vd")

                            log("⑦ đặt app lên VD $vd — ladder R1 start → R2 move-stack → R3 mở lại (nếu được phép)")
                            // ★ lastDisplayId đặt TRƯỚC khi thử đặt app: rollback()/restore cần biết VD nào để reset
                            //   density/overscan. Cờ casting/lastCastApp thì CHỈ commit khi đã xác minh app bám VD.
                            lastDisplayId = vd
                            val landedE = placeAppOnVd(app, adb, { c -> sh(c) }, target, vd, allowDestructive, log)
                            if (landedE != null) {
                                setLastCastApp(app, target); setCasting(true)
                                autoDiag(app, target, vd, log)
                                log("✅ Xong — ${labelOf(app, target)} trên cụm (${landedE.brief()}). ${if (isRectProfile(target)) "full (mất km/h)." else "km/h gốc còn."}")
                                // ★ v0.60 post-op divergence: kiểm mồ côi NGAY SAU cast (không chỉ trước) — bắt sớm
                                //   nếu chính thao tác đặt app vừa làm WM↔AM lệch.
                                divergenceOn({ c -> sh(c) }, vd)?.let { log("  ⚠ post-cast: $it") }
                            } else {
                                // ★ v0.36: KHÔNG còn tự nhận "đang chiếu" khi app không lên. Trả app về màn giữa
                                //   (fullscreen, không để cửa sổ nổi mồ côi) rồi trả đồng hồ — hết cảnh cụm sáng rỗng
                                //   + nút size bắn nhầm task ở display 0.
                                log("❌ $target KHÔNG bám được VD sau cả 3 rung → HỦY chiếu, trả đồng hồ")
                                CastShell.restoreFullscreenOnMain(adb, { c -> sh(c) }, target, vd, log)
                                rollback(app, adb, prof.teardownSeq, log)
                            }
                        } catch (e: Throwable) { log("❌ lỗi giữa chừng: ${e.message}"); if (clusterMutated) rollback(app, adb, prof.teardownSeq, log) }
                    }
                }.onFailure { log("❌ LỖI kết nối: ${it.message}\n(popup 'Allow USB debugging' đã bấm chưa?)") }
            } finally { busy.set(false) }
        }
    }

    fun stop(ctx: Context, log: (String) -> Unit) {
        // TẮT là đường thoát hiểm: nó PHẢI chạy được kể cả khi thao tác trước còn treo.
        if (!takeBusy(log)) { log("⏳ đang chạy 1 thao tác — thử lại sau"); return }
        val app = ctx.applicationContext
        val log = castLogger(app, "stop", log)   // ★ v0.60 RT1.6: tee ra file (teardown-guard/bê stack/trả đồng hồ)
        vdExec.execute {
            try {
                runCatching {
                    dadb.Dadb.create("localhost", 5555, AdbKeys.ensure(app)).use { adb ->
                        // ★ stderr KHÔNG còn bị nuốt (review): lệnh dọn cụm mà lỗi thì hiện trường phải thấy.
                        fun sh(c: String): String { val r = adb.shell(c); val e = r.errorOutput.trim(); if (e.isNotEmpty()) log("  ⚠ $e"); return r.output.trim() }
                        // ★ v0.37: lastDisplayId chỉ nằm trong RAM — app bị kill (xe ngủ, LMK, cài đè) là mất.
                        //   Trước đây mất id thì nhánh reset density/overscan (if vd>=1) IM LẶNG không chạy,
                        //   trong khi override lại được WM lưu vào display_settings.xml và SỐNG QUA REBOOT.
                        var vd = lastDisplayId
                        if (vd < 1) {
                            vd = DisplayParse.clusterDisplayId(sh("dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'"))
                            if (vd >= 1) log("① mất lastDisplayId (app từng khởi động lại) → dò lại VD = $vd")
                        }
                        val ents = StackParse.parse(sh("am stack list"))
                        // ① BÊ MỌI task đang ở VD cụm (display>=1) về display 0 — tổng quát, không riêng map (khỏi kẹt app trên VD vô hình)
                        // ★★ v0.42 SỬA LỖI ĐƠ LAUNCHER: chỉ bê stack app THƯỜNG, ĐÚNG VD, KHÔNG pinned.
                        //   Bản cũ quét mù mọi display>=1 và kéo cả stack OEM/home về display 0 → launcher đứng hình
                        //   (xem KDoc StackParse.evictableOnVd). vd<1 → danh sách rỗng, không quét gì.
                        val moved = mutableSetOf<String>()
                        for (e in StackParse.evictableOnVd(ents, vd)) {
                            log("① bê ${e.brief()} → màn giữa")
                            val o = sh("am display move-stack ${e.stackId} 0 2>&1")
                            if (CastShell.moveRejected(o)) log("   ⚠ không bê được: ${o.take(80)}") else moved += e.pkg
                            Thread.sleep(400)
                        }
                        StackParse.untouchableOnVd(ents, vd).forEach {
                            log("   ⏭ giữ nguyên ${it.brief()} (home/PIP — đụng vào là hỏng launcher; VD tự trả khi tắt chiếu)")
                        }
                        // ★ mọi app VỪA BÊ đều phải ép fullscreen, không chỉ lastCastApp — app khác bị bỏ lại dạng
                        //   cửa sổ nổi trên màn giữa cũng làm người dùng tưởng máy hỏng.
                        (moved + lastCastApp).filter { it.isNotBlank() }.distinct()
                            .forEach { CastShell.restoreFullscreenOnMain(adb, { c -> sh(c) }, it, vd, log) }
                        // ★ trả lại quyền PIP đã chặn lúc chiếu (không để rơi state của user)
                        restorePip(app, log) { c -> sh(c) }
                        // ★ reset density/overscan đã đặt lên VD (DashCast: rò state trên id cũ = 1 nguồn gây kẹt)
                        if (vd >= 1) CastShell.resetDisplayAll({ c -> sh(c) }, vd)
                        // ② trả đồng hồ THEO PROFILE (Seal = [18,0], timing 800ms giữ nguyên). KHÔNG hardcode sc(31) —
                        //    nó chốt cụm ở layout mất km/h, nghi phá nav-lane (nav chết tới mức phải reboot).
                        //    Chỉ set profile cuối nếu teardownProfile>=0 (user chỉnh khi đồng hồ dính cong).
                        //    ★ v0.36: dùng chuỗi ĐÃ CHỐT lúc cast (activeTeardown) — user bấm "Về auto-detect"/nhập hồ sơ
                        //    KHÁC giữa lúc đang chiếu thì teardown vẫn đúng chuỗi đã mở cụm.
                        val ap = activeProfile(app)
                        val teardown = activeTeardown.ifEmpty { ap.teardownSeq }
                        log("② trả đồng hồ [${teardown.joinToString(",")}]${if (teardownProfile >= 0) " + profile $teardownProfile" else " (an toàn nav-lane)"}")
                        teardown.forEachIndexed { i, cmd -> if (i > 0) Thread.sleep(800); sh(ap.svcCall(cmd)) }
                        if (teardownProfile >= 0) { Thread.sleep(800); sh(ap.svcCall(teardownProfile)) }
                        for (k in ANIM_KEYS) sh("settings put global $k $savedAnimSafe")   // ★ trả lại animation gốc (đã tắt lúc chiếu)
                        log("✅ Đã trả đồng hồ gốc. App về màn giữa.")
                    }
                }.onFailure { log("❌ LỖI tắt: ${it.message} — vẫn reset cờ để thử lại được") }
            } finally {
                lastDisplayId = -1; activeTeardown = emptyList(); setCasting(false)
                setActiveProfile(app, null)     // ★ W2-1: hồ sơ chỉ sống trong lúc ta còn giữ cụm
                busy.set(false)
            }   // ★ LUÔN reset dù stop lỗi → không kẹt cờ 'đang chiếu'
        }
    }

    private fun rollback(ctx: Context, adb: dadb.Dadb, teardownSeq: List<Int>, log: (String) -> Unit) {
        log("↩ rollback: trả đồng hồ [${teardownSeq.joinToString(",")}${if (teardownProfile >= 0) "→$teardownProfile" else ""}]…")
        val vd = lastDisplayId
        // ★ v0.60 TEARDOWN-GUARD (P0): bê sink CP/AA khỏi VD TRƯỚC khi reset/teardown (giữ phiên, chống mồ côi).
        //   rollback là đường HỒI PHỤC → best-effort (dù bê hụt vẫn phải trả đồng hồ), nhưng bê được thì hết orphan.
        if (vd >= 1) guardSinksOffVd({ c -> adb.shell(c).output.trim() }, vd, "", log)
        runCatching {
            if (vd >= 1) CastShell.resetDisplayAll({ c -> adb.shell(c).output.trim() }, vd)
            val rp = activeProfile(ctx)
            teardownSeq.forEachIndexed { i, cmd ->
                if (i > 0) Thread.sleep(800)
                val r = adb.shell(rp.svcCall(cmd))
                if (r.errorOutput.isNotBlank()) log("  ⚠ teardown cmd $cmd: ${r.errorOutput.trim()}")
            }
            if (teardownProfile >= 0) { Thread.sleep(800); adb.shell(rp.svcCall(teardownProfile)) }
            // ★ W2-7: dùng CHUNG một đường trả PIP với stop()/cast(). Bản cũ ghi thẳng "allow" — vừa ghi đè ý
            //   người dùng (họ có thể đã tự tắt PIP cho app đó), vừa BỎ QUÊN marker → reconcileOnStart sau đó
            //   báo `pipStuck` cho một gói thật ra đã hết bị chặn.
            restorePip(ctx, log) { c -> adb.shell(c).output.trim() }
            for (k in ANIM_KEYS) adb.shell("settings put global $k $savedAnimSafe") }   // trả lại animation gốc
        lastDisplayId = -1; activeTeardown = emptyList(); setCasting(false)
        setActiveProfile(ctx, null)
    }

    /**
     * ★ v0.66 — HOT-SWAP đổi app trên cụm CHỐNG FREEZE, GIỮ VD SỐNG.
     * Đặt app MỚI lên VD đang sống TRƯỚC (VD không rỗng), RỒI FORCE-STOP app CŨ về màn chính (đường KHÔNG-transition
     * [CastShell.returnAppToMain]) → VD KHÔNG bao giờ bị huỷ + KHÔNG `move-stack …0` → né CẢ NPE A (getDisplayInfo
     * loop → treo) LẪN NPE B (createTaskSnapshot). Lõi shell nằm ở [CastShell.swapOnVd] (test off-xe được).
     * App mới không bám VD / bounce → GIỮ app cũ trên cụm, thoát (switch hụt nhưng KHÔNG teardown, KHÔNG treo).
     * ⚠ Freeze THẬT chỉ verify được trên xe — off-car chỉ chốt chuỗi lệnh + bất biến (xem spec §Verification).
     */
    private fun hotSwapOnVd(app: Context, adb: dadb.Dadb, sh: (String) -> String, target: String, vd: Int, log: (String) -> Unit) {
        if (vd < 1) { log("  ❌ VD không hợp lệ ($vd) — bỏ hot-swap"); return }
        divergenceOn(sh, vd)?.let { log("  ⛔ $it"); return }                                 // KEEP: guard mồ côi WM↔AM
        val oldApp = lastCastApp
        val comp = CastShell.resolveComp(adb, target) ?: run { log("❌ không resolve được $target"); return }
        log("↔ HOT-SWAP v0.66: đặt ${labelOf(app, target)} lên VD $vd (giữ VD sống) rồi FORCE-STOP ${labelOf(app, oldApp)} về màn chính")
        setDensityIfNeeded(sh, vd, scaleOf(target).dpi); Thread.sleep(150)                    // ① scale/dpi per-app
        // ②③④⑤ LÕI SHELL (CastShell.swapOnVd) — né NPE A (VD không rỗng) + NPE B (KHÔNG move-stack …0) + CP/AA-correct.
        //   Split fresh-launch(thường)/resume(sink) + U3 occlude-verify. Giữ chốt F1 (oldApp!=target), F4 (re-check
        //   B bounce), F5 (không restoreFullscreen target), F10 (re-pick B), R11 (occlude trước gentle).
        val res = CastShell.swapOnVd(sh, target, comp, oldApp, vd, log)
        val landed = res.target
        if (landed == null) {                                                                // R6/abort: GIỮ app cũ trên cụm (KHÔNG treo)
            log("  ⚠ ${res.note} — GIỮ ${labelOf(app, oldApp)} trên cụm")
            return
        }
        // ⑥ khung/scale app MỚI (dùng StackEntry ĐÃ RE-PICK — F10, taskId có thể đổi sau force-stop/re-assert)
        val (w, h) = DisplayParse.realSize(sh("dumpsys display"), vd); rememberClusterSize(w, h)
        log("  ⑧ khung: ${applyBounds(sh, vd, landed, scaleOf(target), w, h)}")
        setLastCastApp(app, target); setCasting(true); lastDisplayId = vd                     // R4: cấu hình nhắm ĐÚNG app đích
        log("✅ Đổi sang ${labelOf(app, target)} (hot-swap v0.66, VD $vd giữ sống — force-stop app cũ, không huỷ VD, không treo)")
        divergenceOn(sh, vd)?.let { log("  ⚠ post-swap: $it") }
    }

    /**
     * ĐẶT app lên VD cụm rồi ÁP KHUNG. Trả về [StackEntry] app THỰC bám trên VD, hoặc **null nếu KHÔNG bám được**
     * (gọi phải tự dọn — xem [restoreFullscreenOnMain]). Guard: chỉ gọi khi vd>=1 (không bao giờ đụng display 0).
     *   ① `wm density <dpi> -d VD` → fix SCALE (dpi per-app từ [AppScale]).
     *   ② LADDER [placeLadder] — R1 am start → R2 move-stack → R3 mở lại (nếu app cho phép).
     *   ③ KHUNG per-app ([applyBounds]) — chỉ chạy khi đã bám, và chỉ nhắm task ĐANG Ở VD.
     */

    /**
     * ★ v0.50 — CỔNG CHẶN khi WindowManager và ActivityManager NÓI HAI CHUYỆN KHÁC NHAU trên cụm.
     *
     * Bằng chứng hiện trường (diag-0722-073807, xe Seal): WM có `mStackId=9`/`taskId=13` (CarPlay) trên display 1
     * với cửa sổ đã vẽ xong, trong khi `am stack list` CÙNG LÚC liệt kê 7 stack và **tất cả đều displayId=0**.
     * Stack đó MỒ CÔI: AM không còn biết nó tồn tại ⇒ mọi lệnh `am`/`wm` bắn vào đều vô nghĩa, và bắn thêm chỉ
     * làm hỏng thêm (mỗi vòng lại tái tạo VD + move-stack). Chỉ khởi động lại đầu xe mới sạch.
     *
     * Toàn bộ cơ chế "cụm chỉ được có 1 app" của ClusterNav đọc từ `am stack list` nên MÙ TUYỆT ĐỐI với trạng
     * thái này. Cổng này là con mắt thứ hai.
     *
     * @return chuỗi mô tả để hiện cho người dùng, hoặc null nếu lành.
     */
    internal fun divergenceOn(sh: (String) -> String, vd: Int): String? {
        if (vd < 1) return null
        // ★ ĐÒI HAI LẦN LẤY MẪU LIÊN TIẾP. Hai lệnh dumpsys không nguyên tử: stack bị gỡ đúng khe giữa hai lệnh
        //   cũng trông y như mồ côi. Đây là CỔNG CẤM THAO TÁC nên một mẫu là không đủ — cùng mẫu WATCHDOG_MISSES.
        val first = sampleDivergence(sh, vd) ?: return null
        Thread.sleep(600)
        return sampleDivergence(sh, vd)?.let { first }
    }

    private fun sampleDivergence(sh: (String) -> String, vd: Int): String? {
        return runCatching {
            val wm = sh("dumpsys window displays")
            val am = StackParse.parse(sh("am stack list"))
            val orphans = WmParse.orphanStacksOn(wm, am, vd)
            if (orphans.isEmpty()) return@runCatching null
            val pkgs = WmParse.pkgsOn(wm, vd).joinToString(", ").ifBlank { "(không rõ app)" }
            "đầu xe đang ở trạng thái hỏng: cụm còn ${orphans.size} cửa sổ mồ côi ($pkgs) mà hệ thống " +
                "không còn quản lý được. Mọi thao tác cụm lúc này đều vô nghĩa hoặc làm hỏng thêm — " +
                "cần TẮT MÁY XE hẳn một lần rồi mở lại."
        }.getOrNull()
    }

    /**
     * ★ v0.60 TEARDOWN-GUARD (P0) — bê MỌI sink chiếu-điện-thoại (pkg ≠ [keepPkg]) khỏi VD về display 0
     * (`am display move-stack … 0` — GIỮ phiên, KHÔNG force-stop) rồi verify VD sạch sink. Gọi TRƯỚC mỗi lần
     * huỷ/tái tạo VD (cmd16 re-project · teardownSeq).
     * @return true nếu VD đã sạch sink (an toàn tiếp tục huỷ/tái tạo VD);
     *         false nếu move-stack HỤT hoặc còn sót → phía gọi KHÔNG được huỷ/tái tạo VD (fail-safe:
     *         thà không làm còn hơn tạo cửa sổ mồ côi phải reboot xe).
     */
    /**
     * ★ v0.60 RT1.6 — TEE log chiếu ra FILE (`getExternalFilesDir/castlog/cast_<tag>_v<ver>_<ts>.txt`) song song
     * với UI. Vì sao: cắm CP/AA là đầu xe tắt WiFi → adb ngoài không vào được; panel Diag chỉ hiện TextView, không
     * lưu → phân tích R1/R2/R3/evict/guard phải suy từ code. Giữ 5 file mới nhất (chống phình). Lỗi ghi file KHÔNG
     * được làm ngã đường chiếu (nuốt lỗi, vẫn trả về UI logger).
     */
    private fun castLogger(app: Context, tag: String, ui: (String) -> Unit): (String) -> Unit {
        val f = runCatching {
            val dir = java.io.File(app.getExternalFilesDir(null), "castlog").apply { mkdirs() }
            dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(4)?.forEach { runCatching { it.delete() } }
            val ver = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "x"
            java.io.File(dir, "cast_${tag}_v${ver}_${System.currentTimeMillis()}.txt").also {
                it.appendText("### cast-log $tag · v$ver · " +
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + " ###\n")
            }
        }.getOrNull()
        return { line ->
            ui(line)
            if (f != null) runCatching {
                f.appendText(java.text.SimpleDateFormat("HH:mm:ss.SSS ", java.util.Locale.US).format(java.util.Date()) + line + "\n")
            }
        }
    }

    internal fun guardSinksOffVd(sh: (String) -> String, vd: Int, keepPkg: String, log: (String) -> Unit): Boolean {
        if (vd < 1) return true
        val sinks = phoneProjectionSinksOn(StackParse.parse(sh("am stack list")), vd).filter { it.pkg != keepPkg }
        if (sinks.isEmpty()) return true
        for (s in sinks) {
            // ★ v0.7x (T9): THAY `am display move-stack …0` (primitive NPE B — gentle-move visible size-compat sink
            //   → initializeChangeTransition → orphan) bằng [CastShell.returnAppToMain] (sink-safe, R11): occlude →
            //   gentle am start d0 (GIỮ phiên); còn VISIBLE → ĐỂ YÊN (KHÔNG force-stop = giữ phiên, KHÔNG move-stack).
            //   Sink còn sót sẽ theo VD về display 0 khi teardown huỷ VD (removeMode 0 = MOVE_CONTENT_TO_PRIMARY).
            val occluded = !WmParse.isVisibleOn(sh("dumpsys window displays"), s.pkg, vd)
            log("  🛡 teardown-guard: bê sink chiếu-điện-thoại ${s.brief()} khỏi VD (returnAppToMain — GIỮ phiên, ${if (occluded) "occluded→gentle" else "visible→để yên"})")
            CastShell.returnAppToMain(sh, s.pkg, vd, occluded, log)
            Thread.sleep(300)
        }
        val still = phoneProjectionSinksOn(StackParse.parse(sh("am stack list")), vd).filter { it.pkg != keepPkg }
        if (still.isNotEmpty()) { log("  ⛔ teardown-guard: VD còn ${still.size} sink (visible/cưỡng lại) → để yên, sẽ theo VD về d0 khi teardown (removeMode 0)"); return false }
        return true
    }

    private fun placeAppOnVd(app: Context, adb: dadb.Dadb, sh: (String) -> String, target: String, vd: Int, allowDestructive: Boolean, log: (String) -> Unit): StackEntry? {
        if (vd < 1) { log("  ❌ VD không hợp lệ ($vd) — BỎ QUA (không bao giờ đụng display 0)"); return null }
        // ★ DỪNG TRƯỚC KHI BẮN. Ở trạng thái WM↔AM lệch, mọi lệnh đều vô nghĩa hoặc làm hỏng thêm.
        divergenceOn(sh, vd)?.let { log("  ⛔ $it"); return null }
        CastShell.ensureFreeformSeed(app, sh, log)
        // ★ v0.42: CỤM CHỈ ĐƯỢC CÓ 1 APP — dọn app lạ TRƯỚC khi đặt app mới (chỉ stack app thường, không đụng
        //   home/PIP). Trước đây hàm này viết ra rồi QUÊN GỌI, nên đổi app cứ chồng đống trên cụm.
        CastShell.evictVd(sh, vd, target, log)
        // ★ v0.42 ĐỌC TRƯỚC KHI GHI: mỗi lệnh `wm density` là một config change → framework RELAUNCH activity
        //   (destroy + onCreate mới). App nào giữ overlay theo PROCESS (bong bóng tốc độ Vietmap) thì overlay cũ
        //   KHÔNG bị thu hồi → mỗi lần relaunch là thêm một bong bóng chồng lên. Giá trị đã đúng thì đừng ghi lại.
        setDensityIfNeeded(sh, vd, scaleOf(target).dpi); Thread.sleep(150)             // ① scale per-app (dpi từ AppScale)
        val landed = placeLadder(adb, sh, target, vd, allowDestructive, log)          // ②
        if (landed == null) {
            if (CastShell.hasOverscan(sh)) sh("wm overscan ${overscanArg()} -d $vd")   // không bám → chỉ khung mỹ thuật legacy
            return null
        }
        // ★ T3 = ép-freeform sau khi đã bám (KHÔNG còn là đường đặt app). Chạy TRƯỚC bước khung: probe freeform
        //   (và cả daemon) resize task về full VD, nên phải để applyBounds chạy SAU mới không xoá khung user đặt.
        if (target in t3Apps) escalateFreeform(app, sh, target, vd, landed, log)
        // ③ KHUNG per-app TẬP TRUNG 1 CHỖ: resize (freeform sống) → fallback overscan (không cần freeform).
        val (w, h) = DisplayParse.realSize(sh("dumpsys display"), vd); rememberClusterSize(w, h)
        log("  ⑧ khung: ${applyBounds(sh, vd, landed, scaleOf(target), w, h)}")
        return landed
    }

    /**
     * ★ LADDER ĐẶT APP (v0.36) — leo dần theo mức PHÁ HOẠI, dừng ngay khi bám được.
     *
     *  R1 `am start --display VD --windowingMode 5 -n comp` (KHÔNG clear-task) — giữ nguyên phiên/state.
     *     Đi qua ActivityStarter → bị `canBeLaunchedOnDisplay`/`canPlaceEntityOnDisplay` chặn; khi chặn,
     *     ActivityStarter ÂM THẦM nhắm lại mọi display (ActivityStarter.java:2624 "looking on all displays")
     *     → app Ở LẠI display 0. Đây chính là triệu chứng Android Auto ngoài hiện trường.
     *  R2 `am display move-stack <stack> VD` — **RUNG MỚI, mấu chốt**. KHÔNG đi qua ActivityStarter:
     *     ActivityManagerShellCommand:2516 → ATMS.moveStackToDisplay:3395 → RootActivityContainer:937 →
     *     ActivityStack.reparent:880 — reparent VÔ ĐIỀU KIỆN: không gate freeform, không gate launchMode,
     *     không gate orientation, không gate resizeable. App singleInstance khoá xoay VẪN sang được
     *     (chỉ bị letterbox size-compat). Sau khi reparent, bắn thêm 1 `am start` để ép composite
     *     (task đã ở trên VD nên không thể bị kéo ngược về 0).
     *  R3 `am force-stop` + `am start --activity-clear-task` — PHÁ PHIÊN, chỉ khi app KHÔNG nằm trong
     *     [keepSessionApps]. Bắt buộc phải có force-stop: `am start` luôn tự thêm NEW_TASK, nên với
     *     `--activity-clear-task` thì `willClearTask` short-circuit đúng khối chứa reparentToDisplay
     *     (ActivityStarter.java:2132-2136) → rung này KHÔNG BAO GIỜ relocate được nếu task cũ còn sống.
     *     force-stop giết TaskRecord → findTask không thấy gì → `--display` mới được tôn trọng.
     *
     * Mỗi rung kết thúc bằng [landedOn] (poll có giới hạn, LOẠI stack pinned) thay vì 1 lần `sleep(900)` mù.
     */
    private fun placeLadder(adb: dadb.Dadb, sh: (String) -> String, target: String, vd: Int, allowDestructive: Boolean, log: (String) -> Unit): StackEntry? {
        val comp = CastShell.resolveComp(adb, target) ?: run { log("  ❌ không resolve được component"); return null }
        val startCmd = "am start --display $vd --windowingMode 5 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp 2>&1"

        // ── R1: giữ state ──
        log("  R1 am start --display $vd (giữ state)")
        CastShell.logLines(sh(startCmd), log)
        CastShell.landedOn(sh, target, vd)?.let { log("  ✓ R1 bám VD: ${it.brief()}"); return it }

        // ── R2: move-stack (KHÔNG qua ActivityStarter) ──
        val ents = StackParse.parse(sh("am stack list"))
        val src = StackParse.pick(ents, target, preferDisplay = 0)
        if (src != null && src.displayId != vd) {
            val sibling = ents.count { it.stackId == src.stackId }
            if (sibling > 1) log("  ⚠ stack ${src.stackId} có $sibling task — move-stack sẽ kéo cả cụm task sang cụm")
            log("  R2 am display move-stack ${src.stackId} → VD $vd (không qua ActivityStarter)")
            val out = sh("am display move-stack ${src.stackId} $vd 2>&1")
            CastShell.logLines(out, log)
            if (CastShell.moveRejected(out)) log("  ⚠ move-stack bị từ chối")
            else {
                CastShell.logLines(sh(startCmd), log)                 // ép composite; task đã ở VD nên không bị kéo ngược
                CastShell.landedOn(sh, target, vd)?.let { landed ->
                    // ★ CẢNH BÁO TRẮNG MÀN (BUG A, đo trên xe 2026-07-20): bê task GL/video đang chạy sang VD thì
                    //   lớp GL giữ config của display 0 → cụm TRẮNG. Cứu bằng `am task resize` (ép composite lại)
                    //   thì lại cần freeform SỐNG. Chưa sống → nói thẳng cho người dùng biết đường xử lý, thay vì
                    //   báo "✅ Xong" rồi để họ nhìn màn trắng mà không hiểu vì sao.
                    if (!CastShell.freeformAlive(sh, landed, vd))
                        log("  ⚠ R2 bám VD nhưng freeform CHƯA sống → app video/GL có thể hiện TRẮNG. Trắng thì: TẮT chiếu, " +
                            "bỏ ◈ ở app này rồi CHIẾU lại (rung R3 mở lại app → composite đúng), hoặc tắt máy xe 1 lần.")
                    log("  ✓ R2 bám VD (giữ state): ${landed.brief()}")
                    return landed
                }
            }
        } else if (src == null) log("  ⚠ R2 bỏ qua: không thấy stack non-pinned của $target")

        // ── R3: phá phiên (opt-out per-app) ──
        if (!allowDestructive) {
            log("  ⛔ R3 BỎ QUA: lần chiếu này KHÔNG do người dùng bấm (tự chiếu lúc nổ máy) → không force-stop.")
            return null
        }
        if (isKeepSession(target)) {
            log("  ⛔ R3 BỎ QUA: '$target' đang bật GIỮ PHIÊN (◈) — không force-stop. Bỏ ◈ nếu muốn thử.")
            return null
        }
        // ★ v0.58/v0.60 TỰ BẢO VỆ app chiếu-điện-thoại. keepSessionApps mặc định rỗng → trước đây R3 được phép
        //   force-stop CarPlay/Android Auto khi user bấm CHIẾU → RỚT phiên chiếu từ điện thoại (user phải cắm lại).
        //   ★ ĐÍNH CHÍNH (workflow phản biện 23/07): force-stop KHÔNG phải nguyên nhân "phải reboot xe" — trong
        //   incident 22/07 force-stop KHÔNG hề chạy mà crash vẫn nổ. Lỗi reboot là do VD bị huỷ/tái tạo khi sink
        //   CÒN BÁM (cửa sổ mồ côi), nay đã chặn bằng teardown-guard [P0]. Vẫn CHẶN R3 cho sink: mất phiên chiếu
        //   là mất luôn (khó chịu). Nhận diện theo HÀNH VI (activity đang hiện là sink chiếu màn), KHÔNG hardcode tên gói.
        if (isPhoneProjection(comp, target)) {
            log("  ⛔ R3 BỎ QUA: '$target' là app chiếu điện thoại (CarPlay/Android Auto) — force-stop sẽ RỚT phiên " +
                "chiếu (phải cắm lại điện thoại). Thà không lên cụm còn hơn. Bật GIỮ PHIÊN thủ công nếu muốn ép.")
            // ★ v0.60 (§5-A.3): R1/R2 có thể đã để sink DÍNH NỬA VỜI trên VD → bê về display 0 (giữ phiên) ngay,
            //   để lần huỷ/tái tạo VD kế KHÔNG biến nó thành cửa sổ mồ côi. target CHÍNH là sink nên bê đúng nó.
            StackParse.of(StackParse.parse(sh("am stack list")), target).filter { it.displayId == vd }
                .map { it.stackId }.distinct().forEach { sh("am display move-stack $it 0 2>&1"); Thread.sleep(300) }
            return null
        }
        log("  R3 ⚠ PHÁ PHIÊN: force-stop $target rồi mở lại trên VD — MẤT phiên dẫn/chiếu đang chạy")
        sh("am force-stop $target"); Thread.sleep(700)
        CastShell.logLines(sh("am start --display $vd --windowingMode 5 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp --activity-clear-task 2>&1"), log)
        CastShell.landedOn(sh, target, vd, timeoutMs = 4000)?.let { log("  ✓ R3 bám VD (đã mở lại): ${it.brief()}"); return it }
        log("  ✗ cả 3 rung đều không đưa được $target lên VD $vd")
        return null
    }

    /**
     * ★ ÁP KHUNG (bounds per-app) lên VD — 2 tầng, TỰ FALLBACK:
     *   ① `am task resize` — CHỈ ăn khi freeform SỐNG (đã boot với enable_freeform_support=1).
     *   ② bị từ chối → `wm overscan` theo [AppScale.overscanOn] — tầng display, KHÔNG cần freeform
     *      (đã chứng minh ăn trên xe 2026-07-20) → nút chỉnh size hoạt động NGAY, không chờ reboot.
     * GỐC RỄ (verify source AOSP 10 android-10.0.0_r47, 2026-07-21 — memory `byd-freeform-boot-gate`):
     *   • `enable_freeform_support` CHỈ đọc 1 lần lúc boot (`ATMS.retrieveSettings`, KHÔNG có ContentObserver)
     *     → set runtime vô hiệu tới khi power-cycle xe (adb reboot bị DiLink3 chặn → phải tắt máy tay).
     *   • `WindowConfiguration.canResizeTask()` == (mode==FREEFORM) → `am task resize` task fullscreen LUÔN ném
     *     IllegalArgumentException. `cmd activity task resize` là CÙNG handler → fallback cmd cũ vô dụng, ĐÃ BỎ.
     *   • `setTaskWindowingMode(5)` (cả shell lẫn T3 daemon) bị `validateWindowingMode` downgrade IM LẶNG khi
     *     freeform boot-flag tắt → lỗi KHÔNG lộ ở bước move, chỉ lộ ở resize — nên resize-output là probe tin cậy.
     * rect auto (user chưa cấu hình) → fallback dùng khung mỹ thuật legacy [overscanArg] (giữ hành vi cũ).
     * @return mô tả đường đã áp — để log TRUNG THỰC (trước đây log "đã áp scale" cả khi resize bị ném lỗi).
     */
    internal fun applyBounds(sh: (String) -> String, vd: Int, e: StackEntry?, scale: AppScale, w: Int, h: Int): String {
        // ★★ GUARD P0 (v0.36): TUYỆT ĐỐI không `am task resize` một task KHÔNG nằm trên VD.
        //   Trước đây taskId lấy từ `am stack list` toàn cục, không kiểm display → khi app không bám VD mà cờ
        //   casting vẫn bật, nút chỉnh size bắn `am task resize <task ở display 0>` với toạ độ cụm 1920×720:
        //   HÔM NAY vô hại vì freeform chưa sống (lệnh bị ném lỗi), NHƯNG sau khi tắt-mở máy xe thì nó chạy THẬT
        //   → resize cửa sổ trên MÀN GIỮA lúc đang lái. Chặn từ gốc, không phụ thuộc cờ.
        if (vd < 1) return "BỎ QUA (VD không hợp lệ: $vd)"
        if (e != null && e.displayId != vd)
            return "BỎ QUA resize: task ${e.taskId} đang ở display ${e.displayId} (≠ VD $vd) — không đụng màn giữa"
        val tid = e?.taskId ?: -1
        val b = scale.boundsOn(w, h)
        val pkg = e?.pkg ?: ""

        // ── TẦNG 1: `am task resize` — CHÍNH XÁC NHẤT (khung riêng của app, đặt đâu cũng được),
        //   nhưng chỉ ăn khi freeform SỐNG: canResizeTask() == (windowingMode == FREEFORM).
        var rejected = ""
        if (tid >= 0) {
            val o = sh("am task resize $tid ${b[0]} ${b[1]} ${b[2]} ${b[3]} 2>&1")   // 2>&1: lỗi in ra STDERR
            if (!CastShell.resizeRejected(o)) {
                CastShell.resetDisplayGeometry(sh, vd)                        // task tự quản khung → gỡ ép hình học, GIỮ DPI của user
                if (scale.isAuto) sh("wm overscan ${overscanArg()} -d $vd")    // khung auto vẫn giữ viền mỹ thuật cũ
                return "resize freeform → [${b.joinToString(",")}]"
            }
            rejected = o
        }

        // ── TẦNG 2: `wm overscan` CÓ KIỂM CHỨNG — ★ ƯU TIÊN khi freeform chưa sống.
        //   ĐÃ CHẠY TỐT trên xe (21/07: CarPlay + Vietmap chỉnh cỡ ngon lúc xe đang chạy, freeform chưa sống).
        //   Giữ được khung LỆCH TÂM và không đụng logical size của VD → PHẢI thử TRƯỚC `wm size`.
        //   overscanVerified đọc lại khung cửa sổ thật để biết app có co lại không hay phớt lờ inset.
        val oi = if (scale.isAuto) intArrayOf(insetH, insetV, insetH, insetV) else scale.overscanOn(w, h)
        if (pkg.isNotEmpty()) CastShell.overscanVerified(sh, vd, pkg, oi, w, h)?.let { return it }
        else if (CastShell.hasOverscan(sh)) { sh("wm size reset -d $vd"); sh("wm overscan ${oi[0]},${oi[1]},${oi[2]},${oi[3]} -d $vd") }

        // ── TẦNG 3: `wm size` — CHỈ tới đây khi app PHỚT LỜ content inset (Android Auto và app chiếu tương tự).
        //   Đánh đổi: LogicalDisplay letterbox CĂN GIỮA → mất khung lệch tâm. Nên để cuối cùng.
        // ★ BỎ QUA khi khung CÙNG TỈ LỆ với VD: `wm size` giữ tỉ lệ và letterbox căn giữa, nên đặt một khung
        //   cùng tỉ lệ = LogicalDisplay phóng lại full-bleed → không thấy viền, chỉ thấy nội dung TO ra. Không
        //   phải thứ người dùng muốn khi họ bấm "80%", và lại phá mất overscan vốn làm đúng việc đó.
        val fs = scale.forcedSizeOn(w, h)
        val sameAspect = kotlin.math.abs(fs[0].toDouble() / fs[1] - w.toDouble() / h) < 0.02
        if (!scale.isAuto && !sameAspect)
            CastShell.forceDisplaySize(sh, vd, fs, scale.dpiForForcedSize(w, h), scale.dpi, oi)
                ?.let { return "$it — app phớt lờ overscan nên phải ép cả VD" }

        return when {
            !CastShell.hasOverscan(sh) -> "wm size không ăn và máy này KHÔNG có `wm overscan` (Android 11+) — chưa chỉnh được cỡ"
            tid < 0 -> "overscan [${oi.joinToString(",")}] (không lấy được taskId)"
            rejected.contains("not allowed", true) || rejected.contains("Exception", true) ->
                "overscan [${oi.joinToString(",")}] (freeform chưa bật — tắt máy xe 1 lần để chỉnh chính xác hơn)"
            else -> "overscan [${oi.joinToString(",")}] (resize lỗi: ${rejected.take(60)})"
        }
    }

    /**
     * ★ ÉP FREEFORM SAU KHI ĐÃ BÁM (T3 daemon) — v0.36 KHÔNG còn là đường ĐẶT app.
     * Verify AOSP 10: daemon gọi cùng `moveStackToDisplay` mà `am display move-stack` (rung R2) gọi, cùng uid-2000,
     * cùng quyền INTERNAL_SYSTEM_WINDOW → nó KHÔNG thể đặt app ở chỗ R2 đã hụt. Thứ duy nhất nó làm được mà shell
     * KHÔNG có verb tương đương là `setTaskWindowingMode(5)` cho task ĐANG CHẠY — nhưng lệnh đó bị
     * `ActivityDisplay.validateWindowingMode` downgrade IM LẶNG khi freeform chưa sống. Nên: chỉ chạy khi app đã
     * bám VD VÀ freeform đã sống (probe = `am task resize` không bị từ chối), còn lại báo thẳng là bỏ qua.
     */
    private fun escalateFreeform(app: Context, sh: (String) -> String, target: String, vd: Int, e: StackEntry, log: (String) -> Unit) {
        if (e.isFreeform) { log("  ⊞ T3 bỏ qua: task đã ở freeform rồi"); return }
        if (!CastShell.freeformAlive(sh, e, vd)) {
            log("  ⊞ T3 bỏ qua: freeform CHƯA sống → daemon không thêm được gì so với R2 (TẮT MÁY XE 1 lần rồi mở lại)")
            return
        }
        // ★ pm path có thể trả NHIỀU dòng (split APK: base + config.arm64 + config.<lang>). CLASSPATH cho app_process
        //   PHẢI là APK chứa classes.dex = base.apk; config-split KHÔNG có dex → ClassNotFound.
        val apkPaths = sh("pm path ${app.packageName}").lineSequence()
            .filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toList()
        val apk = apkPaths.firstOrNull { it.endsWith("base.apk") } ?: apkPaths.firstOrNull()
        if (apk.isNullOrBlank()) { log("  ❌ T3: không lấy được APK path ${app.packageName}"); return }
        val (w, h) = DisplayParse.realSize(sh("dumpsys display"), vd)
        val b = scaleOf(target).boundsOn(w, h)
        val cls = "com.byd.clusternav.modules.clustercast.T3Daemon"
        // -Xnoimage-dex2oat: tránh crash AOT lúc app_process khởi động trên SoC DiLink3 (RE ProxyClient DashCast).
        val cmd = "CLASSPATH=$apk app_process64 -Xnoimage-dex2oat /system/bin $cls $target $vd ${b[0]} ${b[1]} ${b[2]} ${b[3]}"
        log("  ⊞ T3 daemon: ép freeform cho task đã bám VD")
        val out = sh(cmd)
        out.lineSequence().filter { it.startsWith("T3 ") }.forEach { log("    $it") }
        if (out.isBlank()) log("    (daemon không in gì — app_process64 chạy?)")
    }

    /** Sleep (ms) sau mỗi cmd chiếu — GIỮ NGUYÊN timing Seal: DI40(35)=2s, còn lại (profile/chiếu)=3s. */
    private fun castSleepMs(cmd: Int): Long = if (cmd == CMD_DI40) 2000L else 3000L

    /**
     * ★ ÁP SCALE PER-APP NGAY LÊN CỤM (T-C) — dpi ([wm density]) + bounds ([am task resize]) từ [AppScale] của [pkg].
     * Chỉ áp khi đang chiếu ĐÚNG app [pkg] (lastCastApp==pkg, vd>=1). Chưa chiếu → chỉ lưu (đã lưu ở setScale), lần sau tự áp.
     * Tái dùng [StackParse]/[DisplayParse]. Guard vd>=1 (KHÔNG BAO GIỜ -d 0). Chạy nền (dadb block).
     */
    fun applyScaleLive(ctx: Context, pkg: String, log: (String) -> Unit) {
        val scale = scaleOf(pkg)
        // ★★ v0.42: KHÔNG còn chặn bằng CỜ TRONG RAM. `casting`/`lastDisplayId`/`lastCastApp` chỉ sống trong tiến
        //   trình; app bị kill (xe ngủ, LMK, cài đè) là mất sạch, và khi đó mọi lần bấm chỉnh cỡ/preset đều bị bỏ
        //   qua IM LẶNG dù app vẫn đang nằm trên cụm — đây chính là lý do anh em thấy "nút không ăn / preset vô
        //   dụng". Giờ chỉ chặn khi ĐANG BẬN, còn lại để luồng nền tự đối chiếu `am stack list`.
        //   AN TOÀN: applyBounds có guard cứng, từ chối mọi task không nằm đúng trên VD → không bao giờ đụng màn giữa.
        if (busy.get()) { log("đã lưu scale $pkg — đang chạy thao tác cụm, sẽ áp sau"); return }
        // ★ SINGLE-FLIGHT (review#2): nhấn mũi tên dồn dập → chỉ 1 luồng dadb áp-live; luồng đang bay tự đọc LẠI
        //   scaleOf(pkg) mới nhất lúc chạy nên gộp cả loạt nhấn thành 1 lần áp giá trị cuối (tránh N kết nối +
        //   N `am task resize` song song cho kết quả bất định). Nhả cờ trong finally → không kẹt kể cả khi lỗi.
        if (!scaleApplying.compareAndSet(false, true)) { scaleDirty.set(true); log("đã lưu scale $pkg — đang áp bản trước, gộp vào lần áp đang chạy"); return }
        val app = ctx.applicationContext
        vdExec.execute {
            try {
                runCatching {
                    dadb.Dadb.create("localhost", 5555, AdbKeys.ensure(app)).use { adb ->
                        // ★ v0.38: KHÔNG nuốt stderr nữa — lệnh wm/am hỏng thì hiện trường phải thấy, trước đây
                        //   applyScaleLive chỉ lấy stdout nên mọi thất bại đều im lặng.
                        fun sh(c: String): String {
                            val r = adb.shell(c); val e = r.errorOutput.trim()
                            if (e.isNotEmpty()) log("  ⚠ $e")
                            return r.output.trim()
                        }
                        // ★ COALESCE có VÒNG LẶP (v0.36): nhấn rơi vào lúc luồng này vừa đọc xong scale sẽ set
                        //   scaleDirty; chạy lại 1 vòng nữa thay vì NUỐT MẤT nhấn cuối ("nhấn không ăn").
                        // ★ dò VD bằng SỰ THẬT, không dựa cờ RAM (cờ mất khi tiến trình bị kill).
                        val vd = lastDisplayId.takeIf { it >= 1 }
                            ?: DisplayParse.clusterDisplayId(sh("dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'"))
                        if (vd < 1) {
                            log("đã lưu scale $pkg — chưa dò thấy cụm (chưa chiếu?) → lần chiếu sau tự áp")
                            return@use
                        }
                        do {
                            scaleDirty.set(false)
                            val cur = scaleOf(pkg)   // ★ đọc LẠI giá trị mới nhất
                            // ★★ GUARD P0: xác minh app CÒN Ở TRÊN VD trước khi đụng vào task. Cờ `casting` có thể
                            //   cũ (VD bị tái tạo bởi cmd 16, app tự nhảy về display 0, hoặc PIP xen vào) → nếu tin
                            //   cờ mà bắn `am task resize` thì sau power-cycle sẽ resize nhầm task trên MÀN GIỮA.
                            val e = StackParse.pick(StackParse.parse(sh("am stack list")), pkg, preferDisplay = vd)
                            if (e == null || e.displayId != vd) {
                                // ★ v0.50 NÓI ĐÚNG BỆNH. Khi máy ở trạng thái WM↔AM lệch, AM VĨNH VIỄN không thấy app
                                //   trên cụm dù mắt người lái vẫn thấy nó đang hiện — báo "app không đang chiếu" lúc đó
                                //   là đúng theo guard nhưng SAI về bản chất, và người dùng cứ bấm lại mãi (mỗi lần bấm
                                //   lại làm hỏng thêm). Phân biệt hai trường hợp trước khi báo.
                                val diverged = divergenceOn(::sh, vd)
                                if (diverged != null) log("đã lưu scale $pkg — nhưng ⛔ $diverged")
                                else log("đã lưu scale $pkg — app KHÔNG đang ở trên cụm (đang ${e?.displayId ?: "không thấy"}). " +
                                    "Bấm \"CHIẾU APP NÀY LÊN CỤM\" rồi chỉnh, hoặc để vậy — lần chiếu sau tự áp.")
                                return@use
                            }
                            // tới đây là app THẬT SỰ đang trên cụm → nhận lại phiên nếu cờ RAM đã mất
                            if (!casting || lastCastApp != pkg) { lastDisplayId = vd; setLastCastApp(app, pkg); setCasting(true) }
                            // ★ v0.58 NÓI THẬT KHI SIZE-COMPAT. Log SL6 chứng minh AA (và app non-resizeable khác)
                            //   vào size-compat trên cụm → framework đóng băng densityDpi ⇒ wm density / task resize
                            //   KHÔNG ăn. Trước đây nút DPI im lặng no-op, người dùng tưởng app hỏng. Báo đúng bệnh.
                            DisplayParse.sizeCompatScale(sh("dumpsys window displays"), pkg)?.let { sc ->
                                val pct = (sc * 100).toInt()
                                log("⚠ $pkg đang SIZE-COMPAT (thu nhỏ ${pct}%): app khai KHÔNG cho đổi kích thước, " +
                                    "framework đóng băng cấu hình → DPI/kích thước KHÔNG ăn với app này. " +
                                    "Đây là hạn chế của chính app, không phải lỗi ClusterNav.")
                            }
                            setDensityIfNeeded(::sh, vd, cur.dpi)
                            val (w, h) = DisplayParse.realSize(sh("dumpsys display"), vd); rememberClusterSize(w, h)
                            val how = applyBounds(::sh, vd, e, cur, w, h)
                            // ★ ĐỌC LẠI dpi THẬT trên cụm thay vì in lại con số mình vừa gửi đi — nếu tầng nào
                            //   ghi đè (vd `wm size` bù dpi) thì người dùng thấy ngay, khỏi ngồi đoán.
                            val d = DisplayParse.density(sh("dumpsys window displays"), vd)
                            val dpiTxt = if (d != null) "dpi ${d.first} (gốc cụm ${d.second})" else "dpi ${cur.dpi}"
                            log("đã áp scale: $dpiTxt qua $how (${e.brief()}, VD ${w}x$h)")
                        } while (scaleDirty.get())
                    }
                }.onFailure { log("❌ áp scale lỗi: ${it.message}") }
            } finally { scaleApplying.set(false) }
        }
    }

    /**
     * ★ SỬA DI CHỨNG (v0.36, 1 lần duy nhất). Bản ≤0.35 ghi `animator_duration_scale=0` lúc chiếu và CHỈ trả lại
     * khi user bấm "TẮT". Tắt máy xe / app bị kill / chiếu lỗi giữa chừng là khoá đó **nằm lại 0 vĩnh viễn** trong
     * Settings.Global (sống qua cả reboot). v0.36 không đụng khoá này nữa → không còn đường nào đưa nó về.
     * Mà 0 nghĩa là mọi ValueAnimator trong MỌI app kết thúc tức thì → bản đồ hết nội suy mượt = vẫn thấy giật.
     * Nên phải chủ động trả về 1.0 đúng 1 lần cho máy đã cài bản cũ.
     */
    fun repairLegacyAnim(ctx: Context, log: (String) -> Unit = {}) {
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (sp.getBoolean("animRepair36", false)) return
        vdExec.execute {
            runCatching {
                dadb.Dadb.create("localhost", 5555, AdbKeys.ensure(app)).use { adb ->
                    val cur = adb.shell("settings get global $LEGACY_ANIM_KEY").output.trim()
                    if (cur == "0" || cur == "0.0") {
                        adb.shell("settings put global $LEGACY_ANIM_KEY 1.0")
                        log("↺ đã trả $LEGACY_ANIM_KEY về 1.0 (bản cũ ghim 0 → app/bản đồ hết mượt)")
                    }
                }
                sp.edit().putBoolean("animRepair36", true).apply()
            }.onFailure { /* chưa kết nối được dadb → để lần mở app sau thử lại (chưa ghi cờ) */ }
        }
    }

    /**
     * ★ DỌN STATE CŨ LÚC KHỞI ĐỘNG (v0.37) — sửa lỗi hiện trường: mở máy xe lên thì Vietmap vẫn nằm trên cụm
     * trong khi app thật đã ở màn giữa. Nguyên nhân: cờ [casting]/[lastDisplayId] chỉ nằm trong RAM, chết theo
     * tiến trình; còn thứ ĐỔI THẬT ngoài hệ thống thì SỐNG DAI hơn tiến trình rất nhiều — `wm density`/`wm overscan`
     * đặt trên VD, chế độ chiếu của AutoContainer, animation scale, app-op PIP, và cả task còn bám trên VD.
     * Tiến trình mới không biết gì về đống đó ⇒ cụm "kẹt" hình cũ.
     *
     * Hàm này chạy khi app/máy khởi động, và CHỈ dọn khi tiến trình này CHƯA chiếu gì (casting=false) — tức là
     * mọi thứ tìm thấy đều là rác của phiên trước. Có thật sự tìm thấy rác thì mới đụng vào cụm; sạch sẽ thì
     * không gửi lệnh nào (khỏi làm phiền đầu xe mỗi lần mở app).
     */
    /**
     * GỠ hai cờ freeform khỏi Settings.Global — đường trả lại cho state sống ngoài tiến trình (§5).
     * Dùng khi màn hình giữa còn app kẹt dạng cửa sổ nổi. Chỉ có hiệu lực sau khi TẮT MÁY XE hẳn một lần.
     * Đồng thời dọn ngay mọi cửa sổ nổi đang kẹt, để người dùng đỡ phải chờ tới lần khởi động sau.
     */
    fun unseedFreeform(ctx: Context, log: (String) -> Unit = {}) {
        val app = ctx.applicationContext
        vdExec.execute {
            runCatching {
                dadb.Dadb.create("localhost", 5555, AdbKeys.ensure(app)).use { adb ->
                    fun sh(c: String) = adb.shell(c).output.trim()
                    CastShell.unseedFreeform(app, ::sh, log)
                    val floating = StackParse.floatingOnMain(StackParse.parse(sh("am stack list")))
                    if (floating.isEmpty()) { log("  ✓ màn giữa không còn cửa sổ nổi nào"); return@use }
                    log("  ↩ dọn ${floating.size} cửa sổ nổi đang kẹt trên màn giữa")
                    floating.mapNotNull { it.comp.substringBefore('/').takeIf(String::isNotBlank) }.distinct()
                        .forEach { pkg -> CastShell.restoreFullscreenOnMain(adb, ::sh, pkg, -1, log) }
                }
            }.onFailure { log("  ❌ không gỡ được: ${it.message}") }
        }
    }

    /** Đã từng ghi cờ freeform ra Settings.Global chưa (marker bền, không phải cờ RAM). */
    fun freeformSeeded(ctx: Context): Boolean = CastShell.freeformSeedMarked(ctx)

    fun reconcileOnStart(ctx: Context, log: (String) -> Unit = {}) {
        if (casting) return
        val app = ctx.applicationContext
        loadPrefs(app)
        vdExec.execute {
            runCatching {
                dadb.Dadb.create("localhost", 5555, AdbKeys.ensure(app)).use { adb ->
                    fun sh(c: String) = adb.shell(c).output.trim()
                    // ★ v0.42: DÒ VD TRƯỚC. Bản cũ tính danh sách stack rồi mới dò vd, và vòng bê KHÔNG hề kiểm vd
                    //   → chạy lúc BOOT và sau mỗi lần cài đè app, có thể kéo cả stack launcher về display 0
                    //   mà người dùng không bấm gì cả.
                    val vd = DisplayParse.clusterDisplayId(sh("dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'"))
                    val ents = StackParse.parse(sh("am stack list"))
                    val onVd = StackParse.evictableOnVd(ents, vd)
                    val animStuck = ANIM_KEYS.any { sh("settings get global $it").trim() == "0" }
                    val pipStuck = pipBlockedPkg.isNotBlank()
                    // ★ override density/overscan/size được WM lưu vào /data/system/display_settings.xml theo
                    //   uniqueId của VD → SỐNG QUA REBOOT và tự áp lại khi VD được tạo lại. Nên "không còn stack"
                    //   KHÔNG có nghĩa là sạch: phải soi cả kích thước/độ nét thật của VD.
                    // ★ v0.50: soi ĐỦ BA thứ WM lưu vào /data/system/display_settings.xml theo uniqueId
                    //   (size · density · overscan) — cả ba đều SỐNG QUA REBOOT và tự áp lại khi VD được tạo lại.
                    //   Bản cũ chỉ so size, nên VD để lại dpi 200 + overscan 90px vẫn bị tuyên bố "sạch" và đi qua
                    //   (thấy thật ở diag-0722-073807: không app nào trên cụm mà VD vẫn bẩn).
                    val wmDump = sh("dumpsys window displays")
                    val dirtyBits = if (vd < 1) emptyList() else buildList {
                        DisplayParse.logicalSize(wmDump, vd)
                            ?.takeIf { it != DisplayParse.realSize(sh("dumpsys display"), vd) }
                            ?.let { add("kích thước ${it.first}x${it.second}") }
                        DisplayParse.density(wmDump, vd)
                            ?.takeIf { it.first != it.second }
                            ?.let { add("độ nét ${it.first}dpi (gốc ${it.second})") }
                        DisplayParse.overscan(wmDump, vd)
                            ?.takeIf { o -> o.any { it != 0 } }
                            ?.let { add("overscan [${it[0]},${it[1]}][${it[2]},${it[3]}]") }
                    }
                    val vdDirty = dirtyBits.isNotEmpty()
                    if (onVd.isEmpty() && !animStuck && !pipStuck && !vdDirty) return@use   // sạch → im lặng
                    log("↻ dọn state phiên trước: ${onVd.size} stack trên cụm" +
                        (if (vdDirty) " · VD còn bẩn: " + dirtyBits.joinToString(", ") else "") +
                        (if (animStuck) " · animation đang bị ghim 0" else "") +
                        (if (pipStuck) " · PIP còn chặn $pipBlockedPkg" else ""))
                    for (e in onVd) {
                        log("   ↩ bê ${e.brief()} → màn giữa")
                        sh("am display move-stack ${e.stackId} 0 2>&1"); Thread.sleep(300)
                    }
                    // ★ v0.50: dọn cửa sổ nổi kẹt trên MÀN GIỮA của MỌI app, không riêng `lastCastApp`.
                    //   `lastCastApp` là cờ RAM — app chết là mất, nên app của phiên trước đó không ai dọn, và
                    //   người lái thấy nó "bị scale" trên màn hình giữa mãi (lỗi hiện trường 22/07).
                    //   floatingOnMain() đã tự giới hạn: chỉ display 0, chỉ stack standard, KHÔNG đụng home/PIP.
                    val floating = StackParse.floatingOnMain(StackParse.parse(sh("am stack list")))
                    if (floating.isNotEmpty()) log("   ↩ ${floating.size} cửa sổ nổi kẹt trên màn giữa → ép fullscreen")
                    floating.mapNotNull { it.comp.substringBefore('/').takeIf(String::isNotBlank) }.distinct()
                        .forEach { pkg -> CastShell.restoreFullscreenOnMain(adb, { c -> sh(c) }, pkg, -1, log) }
                    if (vd >= 1) CastShell.resetDisplayAll({ c -> sh(c) }, vd)
                    if (animStuck) for (k in ANIM_KEYS) sh("settings put global $k $savedAnimSafe")
                    restorePip(app, log) { c -> sh(c) }
                    // trả đồng hồ về mặc định theo hồ sơ (chỉ khi cụm THẬT SỰ còn app của phiên trước)
                    if (onVd.isNotEmpty()) {
                        val teardown = activeProfile(app).teardownSeq
                        val rcp = activeProfile(app)
                        teardown.forEachIndexed { i, cmd -> if (i > 0) Thread.sleep(800); sh(rcp.svcCall(cmd)) }
                        log("↻ đã trả đồng hồ về mặc định")
                    }
                    lastDisplayId = -1; setCasting(false)
                }
            }.onFailure { /* chưa nối được dadb lúc mới boot → bỏ qua, lần mở app sau tự dọn */ }
        }
    }

    /**
     * ★ TỰ CHỤP CHẨN ĐOÁN sau mỗi lần chiếu thành công (v0.39). Chạy nền, không chặn đường chiếu.
     * Lý do: khi cắm CarPlay/Android Auto thì đầu xe TẮT WIFI → không adb ngoài vào được, mà đó lại đúng lúc
     * cần dữ liệu. Chụp tự động ngay khoảnh khắc app vừa lên cụm thì hiện trường khỏi phải nhớ bấm gì.
     * Bật/tắt bằng [autoDiagEnabled] (mặc định BẬT trong giai đoạn còn đang truy lỗi).
     */
    @Volatile var autoDiagEnabled = true

    /** Chỉ MỘT lần chụp tại một thời điểm. Mỗi lần chụp là 16 round-trip shell, trong đó `dumpsys window windows`
     *  chạy dưới `synchronized(mGlobalLock)` của WindowManager — chụp chồng nhau lúc đang lái là tự làm nghẽn WM. */
    private val diagBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun autoDiag(app: Context, pkg: String, vd: Int, log: (String) -> Unit) {
        if (!autoDiagEnabled) return
        if (!diagBusy.compareAndSet(false, true)) { log("  (đang chụp chẩn đoán rồi, bỏ qua lần này)"); return }
        val stamp = java.text.SimpleDateFormat("MMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        Thread {
            try {
                runCatching {
                    val (path, sum) = ClusterDiag.capture(app, pkg, vd, stamp)
                    log("🩺 chẩn đoán tự chụp:\n$sum\n→ $path")
                }
            } finally { diagBusy.set(false) }
        }.apply { isDaemon = true }.start()
    }

    /**
     * ★ TỰ CHIẾU LÚC KHỞI ĐỘNG (v0.42). Gọi từ [com.byd.clusternav.RebindReceiver] sau BOOT_COMPLETED.
     * Chạy SAU [reconcileOnStart] một nhịp: phải dọn xong rác phiên trước rồi mới chiếu, không thì chiếu vào
     * một cụm còn đang giữ state cũ. Delay thêm để đầu xe khởi động xong dịch vụ (AutoContainer, adbd, launcher).
     */
    fun autoCastOnBoot(ctx: Context, log: (String) -> Unit = {}) {
        val pkg = autoCastPkg
        if (pkg.isBlank()) return
        val app = ctx.applicationContext
        Thread {
            Thread.sleep(BOOT_CAST_DELAY_MS)
            if (casting) return@Thread                      // user đã tự chiếu trong lúc chờ → tôn trọng
            // ★★ W1-4: 25 giây sau khi nổ máy là lúc xe đang lùi ra khỏi chỗ đỗ. Chuỗi chiếu [30,16,35] cấu hình
            //   lại phần cứng cụm đồng hồ — KHÔNG được làm khi xe đang lăn bánh. Đòi quan sát KHẲNG ĐỊNH
            //   (mpsOrNull, xem W1-3): không đọc được tốc độ ⇒ coi như đang chạy ⇒ không tự chiếu.
            val v = com.byd.clusternav.SpeedProvider.mpsOrNull()
            if (v?.let { it < AUTO_CAST_MAX_MPS } != true) {
                log("⏱ bỏ qua tự chiếu — ${if (v == null) "chưa đọc được tốc độ" else "xe đang chạy (${"%.1f".format(v * 3.6)} km/h)"}")
                return@Thread
            }
            log("⏱ tự chiếu khi khởi động: $pkg (xe đứng yên)")
            cast(app, pkg, allowDestructive = false, log = log)
        }.start()
    }

    /**
     * ★★ W2-2 (senior review) — CANH PHIÊN CHIẾU CÒN SỐNG KHÔNG.
     *
     * Khiếu nại số 1 của người dùng: app đang chiếu chết/bị ngắt (rút CarPlay, LMK giết, crash) thì **cụm cứ
     * chiếu tiếp** với density/overscan/animation/app-op còn nguyên, cho tới khi có người bấm TẮT.
     * Không có gì canh cả: không poll nào tồn tại, và `reconcileOnStart` lại mở đầu bằng `if (casting) return`
     * nên chính nó cũng bị chặn suốt phiên.
     *
     * Chạy trên alarm 60s SẴN CÓ (RebindReceiver, khai trong manifest → sống cả khi tiến trình đã chết), và
     * chạy trên `vdExec` nên tuần tự hoá sẵn với cast/stop/applyScaleLive — không thêm khoá mới.
     * Đòi [WATCHDOG_MISSES] lần LIÊN TIẾP thấy mất mới teardown: một lần trượt có thể chỉ là app đang relaunch
     * vì config change do chính ta gây ra.
     */
    @Volatile private var watchdogMisses = 0
    fun watchdogTick(ctx: Context) {
        val app = ctx.applicationContext
        loadPrefs(app)
        val marked = lastCastApp
        if (!casting || marked.isBlank()) { watchdogMisses = 0; return }
        if (busy.get()) return                      // đang có thao tác cụm → để yên, lần sau xét
        vdExec.execute {
            // ★ cờ CỤC BỘ: dùng `watchdogMisses == 0` để quyết định là sai — nó cũng bằng 0 khi app CÒN SỐNG.
            var shouldTeardown = false
            runCatching {
                dadb.Dadb.create("localhost", 5555, AdbKeys.ensure(app)).use { adb ->
                    fun sh(c: String) = adb.shell(c).output.trim()
                    val vd = DisplayParse.clusterDisplayId(sh("dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'"))
                    val alive = vd >= 1 && StackParse.of(StackParse.parse(sh("am stack list")), marked)
                        .any { it.displayId == vd && !it.isPinned }
                    if (alive) { watchdogMisses = 0; return@use }
                    if (++watchdogMisses < WATCHDOG_MISSES) return@use
                    watchdogMisses = 0
                    shouldTeardown = true
                }
            }.onFailure { watchdogMisses = 0 }      // không nối được dadb ≠ app đã chết → KHÔNG teardown
            if (shouldTeardown && casting && lastCastApp == marked) {
                android.util.Log.w("ClusterNav", "watchdog: $marked không còn trên cụm → tự trả đồng hồ")
                stop(app) { m -> android.util.Log.i("ClusterNav", "watchdog: $m") }
            }
        }
    }

    /** "Mượt UI": set 3 animation scale GLOBAL qua dadb (uid shell). scale "0.5"=snappy · "1.0"=mặc định. Chạy nền, idempotent. */
    fun applyGlobalAnim(ctx: Context, scale: String, log: (String) -> Unit = {}) {
        val app = ctx.applicationContext
        vdExec.execute {
            runCatching {
                dadb.Dadb.create("localhost", 5555, AdbKeys.ensure(app)).use { adb ->
                    for (k in ANIM_KEYS) adb.shell("settings put global $k $scale")
                }
                log("đã set animation scale = $scale (mượt UI)")
            }.onFailure { log("❌ set animation lỗi: ${it.message} (popup Allow USB đã bấm?)") }
        }
    }

    // ── CHẶN PIP: lệnh thuần ở [CastShell.pipCmd]; state (gói đang bị chặn) giữ ở đây vì phải LƯU BỀN
    //   (app chết giữa chừng → lần chạy sau vẫn biết đường trả quyền lại cho user). ──
    private fun blockPip(ctx: Context, pkg: String, log: (String) -> Unit, sh: (String) -> String) {
        runCatching {
            // ★ ĐỌC giá trị cũ TRƯỚC (user có thể đã tự tắt PIP cho app này — trả về "allow" là ghi đè ý họ),
            //   và LƯU MARKER TRƯỚC khi đổi state hệ thống: chết giữa 2 bước thì lần chạy sau vẫn biết đường trả.
            val prev = sh("cmd appops get --user 0 $pkg PICTURE_IN_PICTURE 2>&1")
                .lineSequence().firstOrNull { it.contains("PICTURE_IN_PICTURE") }
                ?.substringAfter("PICTURE_IN_PICTURE:")?.trim()?.substringBefore(";")?.trim()
                ?.takeIf { it in setOf("allow", "ignore", "deny", "default") } ?: "allow"
            pipBlockedPkg = pkg; pipPrevMode = prev; save(ctx)
            sh(CastShell.pipCmd(pkg, "ignore"))
            log("  ⓟ tạm chặn PIP của $pkg (trả về '$prev' khi TẮT) — khỏi rơi vào cửa sổ nhỏ trên cụm")
        }
    }

    /** Trả lại quyền PIP đã chặn (idempotent). "allow" = mặc định của op nên không để lại rác trong appops. */
    private fun restorePip(ctx: Context, log: (String) -> Unit, sh: (String) -> String) {
        val p = pipBlockedPkg
        if (p.isBlank()) return
        val mode = pipPrevMode.ifBlank { "allow" }
        runCatching { sh(CastShell.pipCmd(p, mode)) }
        pipBlockedPkg = ""; pipPrevMode = ""; save(ctx)
        log("  ⓟ đã trả quyền PIP của $p về '$mode'")
    }

    // ── parse helpers ──
    // khớp TRỌN dòng "package:<pkg>" (không contains — tránh 'com.foo' dính 'com.foobar')
    private fun isInstalled(adb: dadb.Dadb, pkg: String) = adb.shell("pm list packages").output.lineSequence().any { it.trim() == "package:$pkg" }


}
