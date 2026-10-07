package com.byd.clusternav.modules.clustercast.simplified

import java.util.concurrent.atomic.AtomicReference

/**
 * Simplified Cluster Cast coordinator with safety (T4 remediation).
 * Single owner of projection/cast state. Mutations run on bounded executor.
 * UI observes [state] and emits [SimpleCastIntent].
 */
class SimpleCastCoordinator(
    internal val projection: ProjectionManager,
    internal val configurator: DisplayConfigurator,
    internal val mover: AppMover,
    val prefs: SimpleCastPrefs,
    internal val shell: SimpleCastShell,
    displayId: Int,
    private val castTimeoutMs: Long = 15_000L,
    private val stopTimeoutMs: Long = 5_000L,
    private val openTimeoutMs: Long = BoundedCastExecutor.OPEN_TIMEOUT_MS, // 2.93 · CAST-OPEN-TIMEOUT — hạn riêng của lượt mở chiếu
    // This app's own installed package, injected from :app (BuildConfig.APPLICATION_ID). Default keeps the legacy value
    // for JVM tests; production passes the real id so cast self-exclusion + the black placeholder target the right app.
    internal val selfPackage: String = "com.byd.clusternav",
    /** Ngủ giữa các lần dò VD cụm sau khi mở projection ([ClusterDisplayResolver.awaitAndPersist]) — test truyền `{}`. */
    private val detectSleepMs: (Long) -> Unit = { Thread.sleep(it) },
    /** Review 2.89 Pass 1 · safety-5 — gỡ ClusterBlack TRONG tiến trình (`:app` cấp); JVM/test: luôn đường shell. */
    ownPlaceholder: ClusterThemeGuard.OwnPlaceholder = ClusterThemeGuard.OwnPlaceholder.NONE,
    /** B1a — sổ theme bền (`:app`: prefs `clustercast`, phạm vi XE); JVM/test: trong bộ nhớ. */
    themeLedger: ThemeLedger.Store = ThemeLedger.InMemory(),
    /** B1a — đồng hồ của sổ (`:app`: elapsedRealtime · BOOT_COUNT · mốc khởi động tiến trình). */
    themeClock: ThemeLedger.Clock = ThemeLedger.JVM_CLOCK,
    /** B1a — kiểu người lái chọn, đọc MỘT lần đầu mỗi lượt mở. Chưa có lựa chọn (B1b) ⇒ Bo tròn (D3 — đường đang chạy). */
    internal val desiredStyle: () -> CastStyle = { CastStyle.CURVED },
    /**
     * B1a — dò lại công thức bằng shell ở lượt mở ĐẦU của tiến trình (`:app`: `car.type` qua dadb khi tiến trình không đọc
     * được). `null` = không dò. Trả công thức mới hoặc `null` (không có gì mới). Không được ném.
     */
    internal val recipeProbe: ((SimpleCastShell) -> ProjectionRecipe?)? = null,
    /**
     * 2.89 · B2 VM-PREREQ-TRUTH — điều kiện nền (miễn pin …) của các app Kachi tự mở, chạy ở mỗi lượt mở chiếu qua CHÍNH
     * shell của coordinator (`:app`: `AppPrereqs.ensureForCastOpen` — đọc → áp phần thiếu → đọc lại). Thay cờ một-lần
     * `doze_whitelist_applied`. Mặc định: không làm gì (JVM/test).
     */
    internal val appPrereqs: (SimpleCastShell) -> Unit = {},
    /**
     * 2.89 · B4 DISPLAY-OWNER-DYNAMIC — nhận id màn ảo cụm vừa dò LIVE (`null` = hụt / đã đóng chiếu) cho cổng sở hữu display
     * của launcher (`:app`: `WindowCommandDispatcher.setCastDisplay`). Gọi qua [publishCastDisplay]. Mặc định: không làm gì.
     */
    internal val onCastDisplay: (Int?) -> Unit = {},
    clusterLayers: ClusterLayerPort = ClusterLayerPort.NONE, // 2.90 · R9 — dọn/trả cụm quanh lượt đổi theme (`:app` cấp)
    private val placeholderRecoverDelayMs: Long = PlaceholderRecover.FIRST_DELAY_MS, // 2.96 · R15 — test rút ngắn
    /** 2.96 · R13 (soát Pass 1) — đọc `Settings.Global` TRONG tiến trình (`:app` `FreeformSeedStore.readGlobal`); `null` ⇒ shell. */
    private val globalSettingReader: ((String) -> String?)? = null,
) {
    // ── Cluster display id — id SỐNG, dò động (X2) ────────────────────────────
    // Seed = giá trị dựng (prefs.lastDisplayId ?: fallback), nhưng KHÔNG tin nó: openProjection() dò lại thật
    // (dumpsys display → fission/xdja) rồi ghi đè + persist. CLAUDE.md §5 (kiểm bằng sự thật, không cờ RAM) +
    // §7 (generic, không hardcode). `displayId` chỉ dùng cho ĐỌC/geometry (đã được ghi đè bằng id dò live).
    @Volatile internal var displayId: Int = displayId

    /**
     * R1/R2 (spec `kachi-hal187-cast-remediation` §4.1): id cụm đã XÁC MINH LIVE trong tiến trình này
     * ([ClusterDisplayResolver] — dumpsys + guard owner≠[selfPackage]). `-1` = chưa xác minh (hoặc lần dò gần nhất
     * hụt) ⇒ CẤM mọi lệnh đặt (`am start --display`), `wm … -d`, dọn VD. KHÔNG BAO GIỜ rơi về seed: [ĐO] 2026-09-15
     * seed 1 = `kachi-slot-0` (VD của chính launcher) ⇒ ClusterBlack + GMaps đặt vào ô launcher.
     */
    @Volatile internal var liveDisplayId: Int = -1

    internal val executor = BoundedCastExecutor(
        castTimeoutMs = castTimeoutMs,
        stopTimeoutMs = stopTimeoutMs,
        onTimeout = { tag -> log("TIMEOUT: $tag") },
        onDiscard = { tag, why -> log("DISCARDED: $tag — $why") },   // 2.96 · R5: không bỏ thao tác nào im lặng
    )

    internal val verifier = CastPostconditionVerifier(
        shell = shell,
        sleepMs = { Thread.sleep(it) },
        log = { msg -> log("verify: $msg") },
    )

    internal fun log(msg: String) {
        println("[SimpleCast] $msg")
    }

    /**
     * CLUSTER-THEME-SAFE (2.89, P0) — cổng của MỌI opcode đổi theme cụm ([ProjectionManager.open]/[ProjectionManager.close])
     * + lượt gỡ `ClusterBlack` lúc tắt chiếu. Luật ở [ClusterThemePlan]; ngủ qua [detectSleepMs] (test truyền `{}`).
     */
    internal val themeGuard = ClusterThemeGuard(
        shell, selfPackage, ownPlaceholder, sleepMs = detectSleepMs,
        store = themeLedger, clock = themeClock, vacantVdAllowed = { projection.recipe.themeOnVacantVd }, layers = clusterLayers,
    ) { msg -> log(msg) }

    /** B1a — [recipeProbe] chạy tối đa MỘT lần mỗi tiến trình (`probeRecipeOnce`, `SimpleCastCoordinatorOps.kt`). */
    internal val recipeProbed = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Lượt quyết gần nhất của cổng theme + kế hoạch kiểu cụm (một dòng) — cho `ClusterDiag` (CLAUDE.md §11); `null` = chưa có
     * lượt nào.
     */
    val themeVerdict: String?
        get() {
            val gate = themeGuard.lastVerdict
            val plan = projection.lastPlan ?: return gate
            return "${gate ?: "cổng không được hỏi"} · kế hoạch: ${plan.why} · kiểu tin=${plan.believed}"
        }

    /**
     * Owns freeform task resize + per-app profile persistence/restore (R4/R5/R6). Đọc displayId SỐNG qua provider.
     * ⚠ 2.90 · R5 — `this.displayId`: tham số dựng `displayId` (SEED) che thuộc tính ⇒ bản cũ chụp seed mãi mãi — F7 xe 06/10
     * `wm size -d 4` → `0x0` khi cụm đã là 9 ([ĐO] `ClusterRectStaleDisplayTest`).
     */
    internal val geometry = CastGeometryController(
        shell, prefs, { this.displayId },
        redetect = { detectClusterDisplay() },   // 2.90 · R5: `wm size -d` thấy display đã mất ⇒ dò lại id cụm
        readGlobalSetting = globalSettingReader,  // 2.96 · R13 (soát Pass 1): cờ freeform đọc trong tiến trình, 0 shell
    ) { msg -> log(msg) }

    /**
     * X2 — dò id display CỤM thật (generic: fission/xdja qua [ClusterDisplayResolver], KHÔNG hardcode 1/2) và
     * ghi đè [displayId]/[liveDisplayId] + persist ([SimpleCastPrefs.saveLastDisplayId]) để tiến trình sau + đường
     * geometry ([CastGeometryController] đọc `prefs.lastDisplayId()`) cùng nhắm đúng màn. Chạy trên executor nền
     * của coordinator (KHÔNG bao giờ trên luồng vẽ — shell I/O).
     *
     * R1: dò hụt (không thấy fission/xdja, shell lỗi, hoặc id là VD của launcher — R2) ⇒ trả `-1` VÀ đặt
     * [liveDisplayId] = -1 để mọi lệnh đặt sau đó bị chặn. KHÔNG giữ giá trị cũ, KHÔNG rơi về seed.
     * [awaitAfterOpen] = true ⇒ dò lặp ([ClusterDisplayResolver.awaitAndPersist]) vì VD cụm chỉ xuất hiện sau khi
     * AutoContainer mở projection.
     */
    internal fun detectClusterDisplay(awaitAfterOpen: Boolean = false, exclude: Int = -1): Int {
        val persist: (Int) -> Unit = { prefs.saveLastDisplayId(it) }
        val resolved = if (awaitAfterOpen) {
            ClusterDisplayResolver.awaitAndPersist(
                shell, selfPackage, sleepMs = detectSleepMs, exclude = exclude, log = { log(it) }, persist = persist,
            )
        } else {
            ClusterDisplayResolver.detectAndPersist(shell, selfPackage, persist)
        }
        if (resolved >= 1) {
            if (resolved != displayId) log("cluster display: $displayId → $resolved (dò fission/xdja)")
            displayId = resolved
        } else {
            log("cluster display: KHÔNG dò thấy fission/xdja (hoặc id là VD của $selfPackage) — không đặt gì (R1/R2)")
        }
        liveDisplayId = resolved
        publishCastDisplay(resolved)   // B4: cổng sở hữu display của launcher theo id dò được, không hằng 1
        return resolved
    }

    /**
     * Guard TẦNG THI HÀNH (CLAUDE §5, R2): id được phép nhận lệnh đặt/`wm -d` — [liveDisplayId] nếu đã xác minh,
     * null nếu chưa. Caller nhận null PHẢI bỏ qua lệnh (log), không thay bằng seed.
     */
    private fun verifiedClusterDisplay(tag: String): Int? {
        val id = liveDisplayId
        if (id >= 1) return id
        log("$tag: bỏ qua — display cụm chưa xác minh live (liveDisplayId=$id), không nhắm seed=$displayId")
        return null
    }

    // `undoTargetDisplay` (đường HOÀN TÁC: id đã xác minh hoặc dò tươi một lần) → `SimpleCastCoordinatorOps.kt` (tách THUẦN, trần 500).

    // TRIAL watchdog state (owner 2026-08-14): re-pin a cast app that an external trigger (e.g. Kiki
    // starting GMaps navigation) pulled off the cluster. Debounce + cooldown so driving is never
    // yanked on a transient am-stack parse gap.
    internal val repinMissStreak = java.util.concurrent.ConcurrentHashMap<String, Int>()
    internal val repinCooldownUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var lastRepinProbeAt = 0L
    @Volatile private var lastRepinSkipLogAt = 0L

    private val _state = AtomicReference<SimpleCastState>(SimpleCastState.Off)
    val state: SimpleCastState get() = _state.get()

    private val listeners = mutableListOf<(SimpleCastState) -> Unit>()

    fun addStateListener(listener: (SimpleCastState) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
        listener(state) // emit current immediately
    }

    fun removeStateListener(listener: (SimpleCastState) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    internal fun setState(new: SimpleCastState) {
        _state.set(new)
        notifyState(new)
    }

    /**
     * V-CLUSTER · VC-R6 — cập nhật bản ghim/tỉ lệ của phiên CHỈ khi phiên vẫn đúng là [expected] (CAS theo danh tính):
     * lượt chỉnh tay không được đè một trạng thái mới hơn (Stop/cast khác đã lọt vào giữa). Thân ở `CastSessionPin.kt`.
     */
    internal fun replaceState(expected: SimpleCastState, new: SimpleCastState) {
        if (_state.compareAndSet(expected, new)) notifyState(new)
    }

    /** Phát cho người nghe (đã chụp danh sách dưới khoá) — tách khỏi [setState] để [setError] CAS được. */
    private fun notifyState(new: SimpleCastState) {
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it(new) }
    }

    /** Set error state with auto-recovery to Idle (or Off) after 3 seconds. */
    internal fun setError(message: String) {
        val err = SimpleCastState.Error(message)
        setState(err)
        // KHÔNG xếp vào executor: `Thread.sleep(3000)` ở đó khoá worker duy nhất 3 giây, và với hàng đợi
        // sức chứa 1 + DiscardOldestPolicy thì lệnh chiếu kế tiếp của người dùng bị vứt im lặng.
        //
        // ⚠ [SOÁT 1.69 · P2] Chính VÌ đã rời executor, chỗ này không còn được "kiểm rồi đặt": khối hồi lỗi nay
        // chạy song song với worker cast, nên `if (state is Error) setState(Idle)` có thể đè một trạng thái MỚI
        // HƠN lọt vào giữa hai bước (vd `Opening` của cú chiếu vừa bấm). CAS trên ĐÚNG thực thể lỗi đã hẹn đóng
        // cả hai lỗ: không đè trạng thái mới hơn, và không nhả sớm một lỗi KHÁC tới sau.
        executor.schedule(ERROR_RECOVERY_MS) {
            val next = if (projection.isOpen) SimpleCastState.Idle else SimpleCastState.Off
            if (_state.compareAndSet(err, next)) notifyState(next)
        }
    }

    // ─── Projection lifecycle ─────────────────────────────────────────────────
    /**
     * Opens projection (Activity onCreate; and on boot via FloatingBubbleService when autostart on).
     * R10: idempotent — opens only from Off/Error; opening/idle/casting is a no-op (both callers safe).
     */
    fun openProjection() {
        executor.submit("openProjection", openTimeoutMs) {
            when (state) {
                is SimpleCastState.Off, is SimpleCastState.Error -> Unit // proceed
                else -> return@submit // already open/opening/casting/stopping/closing
            }
            setState(SimpleCastState.Opening)
            // ⚠ [SOÁT 2026-09-15 · P2] MỌI lối thoát bất thường phải rời khỏi `Opening`. Chuỗi mở dài (5 s profile + dò lặp VD
            // cụm + cổng theme) nên có thể chạm hạn cứng `openTimeoutMs` (2.93: hạn RIÊNG 25 s, các thao tác khác 15 s) của
            // [BoundedCastExecutor] ⇒ `future.cancel(true)` NGẮT thread giữa một `Thread.sleep` ⇒
            // `InterruptedException` thoát khỏi block. Không bắt thì state kẹt `Opening` VĨNH VIỄN, mà
            // `openProjection` chỉ chạy lại từ `Off`/`Error` ⇒ cast chết tới khi tiến trình khởi động lại.
            // Bắt → `Error` (tự hồi về Idle/Off sau 3 s) ⇒ người dùng bấm lại được.
            try {
                openProjectionGuarded()
            } catch (t: Throwable) {
                if (t is InterruptedException) Thread.currentThread().interrupt()
                log("openProjection ngắt/lỗi giữa chừng (${t.javaClass.simpleName}) — nhả state khỏi Opening")
                setError("Projection open interrupted / Mở chiếu bị ngắt")
                schedulePlaceholderRecover(placeholderRecoverDelayMs)   // 2.96 · R15 — cụm chiếu mà chưa có nền (`CastPlaceholder.kt`)
            }
        }
    }

    /** Closes projection. Called on app exit. */
    fun closeProjection() {
        executor.submit("closeProjection") {
            // Return any active apps first (known state)
            returnAllApps()
            // Safety net: also clean any unknown leftovers from the cluster display — CHỈ khi id đã xác minh
            // live (R2): dọn theo seed có thể bê app trong Ô của launcher về display 0.
            undoTargetDisplay("closeProjection.clean")?.let { cleanDisplay(it) }
            setState(SimpleCastState.Closing)
            val ok = projection.close(displayId, themeGuard)
            // CLUSTER-THEME-SAFE bước 3 — đường MỚI xuống CUỐI (CLAUDE.md §6): 18 → 0 giữ nguyên, rồi gỡ ClusterBlack của
            // Kachi khỏi màn ảo cụm [ĐO 05/10: nó nằm lại sau khi tắt chiếu] để lần mở sau thấy màn ảo trống.
            if (ok) themeGuard.removePlaceholder("closeProjection")
            if (ok) publishCastDisplay(-1)   // B4: chiếu đã đóng ⇒ không display nào còn thuộc CAST
            // ⚠ [SOÁT 1.69 · P2] Qua [setError], không `setState(Error(...))` trần: sau bản vá CAS ở trên, chỉ lỗi
            // nào TỰ hẹn giờ mới có đường nhả (trước đây nó **ăn ké** lượt hẹn của một `setError` khác tình cờ còn
            // treo — một đường phục hồi không xác định). Bất biến: KHÔNG Error nào kẹt vĩnh viễn.
            if (ok) setState(SimpleCastState.Off) else setError("Projection close failed")
        }
    }

    // ─── Intent dispatch ──────────────────────────────────────────────────────
    /** Dispatch a cast intent. Thread-safe — queued on serial executor.
     *  Stop is PRIORITY: cancels pending cast and executes immediately. */
    fun dispatch(intent: SimpleCastIntent) {
        when (intent) {
            is SimpleCastIntent.Stop -> executor.submitStop("stop") { handleIntent(intent) }
            is SimpleCastIntent.Close -> executor.submitStop("close") { handleIntent(intent) }
            else -> executor.submit("cast") { handleIntent(intent) }
        }
    }

    private fun handleIntent(intent: SimpleCastIntent) {
        when (intent) {
            is SimpleCastIntent.CastFull -> handleCastFull(intent)
            is SimpleCastIntent.CastSlot -> handleCastSlot(intent)
            is SimpleCastIntent.Stop -> handleStop(intent)
            is SimpleCastIntent.Close -> closeProjectionSync()
        }
    }

    // ─── Xử lý ý định: `handleCastFull` · `handleCastSlot` · `handleStop` → `SimpleCastCoordinatorIntents.kt`;
    //     `openProjectionBody` · `doRepinEscapedCastApps` → `SimpleCastCoordinatorOps.kt` (tách THUẦN theo trần 500 dòng,
    //     cùng package — hàm mở rộng `internal`, thân giữ nguyên byte; thành viên chúng chạm là `internal`).
    // ─── Helpers ──────────────────────────────────────────────────────────────
    internal fun returnApp(pkg: String, appType: AppType, taskId: Int? = null) {
        if (pkg == selfPackage) return
        log("returnApp: pkg=$pkg, appType=$appType, taskId=$taskId")
        mover.returnToMain(pkg = pkg, activity = null, appType = appType, taskId = taskId, clusterDisplayId = displayId)
    }

    /** Clear stale frame from cluster display after stop. */
    internal fun refreshCluster() {
        shell.execute(projection.recipe.command(0))
    }

    private fun returnAllApps() {
        when (val current = state) {
            is SimpleCastState.CastingFull -> returnApp(current.targetPkg, current.appType)
            is SimpleCastState.CastingSplit -> {
                current.left?.let { returnApp(it.pkg, AppType.NORMAL) }
                current.right?.let { returnApp(it.pkg, AppType.NORMAL) }
            }
            else -> {}
        }
    }

    /** Dọn task lạ khỏi VD cụm [vd] — caller PHẢI truyền id đã xác minh live (không bao giờ seed). */
    internal fun cleanDisplay(vd: Int) = CastDisplayCleaner.cleanDisplay(shell, vd, command = projection.recipe::command)

    /**
     * V-CLUSTER · VC-R7 — dọn projection MỒ CÔI của tiến trình trước, sau khi lượt dựng coordinator vừa chốt
     * `cast_enabled` BẬT→TẮT (`SimpleCastRuntime.create`). Chạy trên executor (shell). Bốn câu CLAUDE.md §4 + lý do chọn
     * đường ở KDoc [closeOrphanProjectionBody].
     */
    fun closeOrphanProjection() {
        executor.submit("close-orphan") { closeOrphanProjectionBody() }
    }

    private fun closeProjectionSync() {
        returnAllApps()
        // Reset display to defaults before closing — undo all wm changes (chỉ trên id đã xác minh live, R2)
        undoTargetDisplay("close.reset")?.let { configurator.reset(it) }
        setState(SimpleCastState.Closing)
        val ok = projection.close(displayId, themeGuard)
        if (ok) themeGuard.removePlaceholder("close")        // CLUSTER-THEME-SAFE bước 3 (xem closeProjection)
        if (ok) publishCastDisplay(-1)                       // B4 (xem closeProjection)
        if (ok) setState(SimpleCastState.Off) else setError("Close failed")
    }

    // ─── Resize active target / slot · tỉ lệ · DPI — CHỈNH TƯỜNG MINH ─────────────────
    // V-CLUSTER · VC-R6: thân ở `CastSessionPin.kt` — lưu cho hồ sơ ĐANG DÙNG dưới ô nhớ của tỉ lệ PHIÊN, rồi cập nhật
    // đúng trường vừa áp của bản ghim (repin sau đó dùng bản ghim, không đọc lại prefs — refute B6/C2).

    /**
     * Resize the currently casting full app to the given bounds (R5/R6). NORMAL-only.
     * Persistence (FULL profile) happens ONLY on shell success — see [CastGeometryController].
     * Thread-safe — queued on serial executor.
     */
    fun resizeActiveTarget(left: Int, top: Int, right: Int, bottom: Int) =
        executor.submit("resize") { resizeFullBody(left, top, right, bottom) }

    /**
     * Resize one split slot's app to the given bounds (R5/R6). Only valid in
     * [SimpleCastState.CastingSplit]; targets the app currently in [side]. Persists to the
     * matching profile ([CastProfile.of] on the SESSION's split ratio) ONLY on shell success.
     * Thread-safe — queued on serial executor.
     */
    fun resizeActiveSlot(side: ClusterSlotSide, left: Int, top: Int, right: Int, bottom: Int) =
        executor.submit("resize-slot") { resizeSlotBody(side, left, top, right, bottom) }

    /**
     * Apply a new split ratio ([leftPercent]) live (Feature 2 · split-ratio buttons).
     *
     * (a) ALWAYS persists [SimpleCastPrefs.setSplitRatioLeftPercent] so the next split cast uses it.
     * (b) If currently [SimpleCastState.CastingSplit], re-resizes BOTH occupied slots to the new ratio
     *     IN PLACE (no return+recast): left = [0,0, W·pct/100, H], right = [W·pct/100, 0, W, H], where
     *     W×H is the cluster display's measured size ([AppMover.queryDisplaySize], fallback 1920×720).
     *     Reuses the same [CastGeometryController.resizeSlot] path as [resizeActiveSlot], so per-slot
     *     bounds persist to the matching per-ratio profile ONLY on shell success (R6).
     * (c) V-CLUSTER: lượt DUY NHẤT đổi [SimpleCastState.CastingSplit.leftPercent] của phiên.
     *
     * Thread-safe — queued on the serial executor.
     */
    fun applySplitRatioLive(leftPercent: Int) =
        executor.submit("split-ratio-live") { applySplitRatioLiveBody(leftPercent) }

    /** @see CastGeometryController.isFreeformAlive */
    fun isFreeformAlive(): Boolean = geometry.isFreeformAlive()

    // ─── Density control ──────────────────────────────────────────────────────
    /** @see CastDensityControl.set — persists ONLY on shell success (R6). */
    fun setDensity(dpi: Int?) = executor.submit("density") {
        val vd = verifiedClusterDisplay("setDensity") ?: return@submit
        setDensityBody(vd, dpi)
    }

    /** @see CastDensityControl.setForSplit — split DPI, persists the per-ratio profile on success (R4/#5). */
    fun setDensitySplit(dpi: Int?) = executor.submit("density-split") {
        val vd = verifiedClusterDisplay("setDensitySplit") ?: return@submit
        setDensitySplitBody(vd, dpi)
    }
    /** Shutdown executor. Call on app destroy. */
    fun shutdown() {
        executor.shutdown()
    }

    // ─── Foreground detection (replaces V2 CastAmStackParser path) ────────────
    /**
     * Detect the foreground package on [targetDisplayId] (default: HOME display 0).
     *
     * Runs `am stack list` and parses visible tasks. Returns the single visible package
     * on the specified display, excluding packages in [excluded]. Returns null if ambiguous
     * (multiple distinct visible packages) or if shell fails.
     *
     * Before detection, dismisses any active PiP on the target display to prevent
     * Google Maps / YouTube PiP from being misidentified as the foreground app.
     *
     * Must be called off main thread — performs shell I/O.
     */
    fun foregroundPackage(targetDisplayId: Int = 0, excluded: Set<String>): String? {
        // Dismiss PiP first — prevents misidentification
        dismissPipOnDisplay(targetDisplayId)
        val result = shell.execute("am stack list")
        if (!result.success || result.stdout.isBlank()) return null
        return CastStackParser.foreground(result.stdout, targetDisplayId, excluded)
    }

    /**
     * Dismiss any active PiP (pinned stack) on [displayId] by sending KEYCODE_HOME
     * to the pinned task. On Android 10 BYD, `am stack resize-animated` or
     * removing the pinned stack forces PiP to close.
     *
     * Known PiP offenders: Google Maps (navigation overlay), YouTube (mini player).
     */
    fun dismissPipOnDisplay(displayId: Int) {
        val result = shell.execute("am stack list")
        if (!result.success) return
        var currentDisplayId = -1
        var currentStackId = -1
        var isPinned = false
        for (line in result.stdout.lines()) {
            val stackMatch = STACK_HEADER_WITH_ID.find(line)
            if (stackMatch != null) {
                currentStackId = stackMatch.groupValues[1].toIntOrNull() ?: -1
                currentDisplayId = stackMatch.groupValues[2].toIntOrNull() ?: -1
                isPinned = false
                continue
            }
            if (line.contains("mWindowingMode=pinned")) isPinned = true
            if (isPinned && currentDisplayId == displayId && line.contains("visible=true")) {
                // Found a visible pinned task on this display — dismiss it
                shell.execute("am stack remove $currentStackId")
                return
            }
        }
    }

    /** Execute a shell command via the coordinator's transport. For PiP/appops management. */
    fun executeShell(command: String): ShellResult = shell.execute(command)

    // ─── TRIAL: watchdog re-pin (owner 2026-08-14) ───────────────────────────
    /**
     * Re-pin a cast app that an external trigger pulled off the cluster.
     *
     * Symptom (owner): with GMaps + VietMap split-cast, asking Kiki to navigate with GMaps launches
     * the GMaps nav activity on display 0, so its task migrates OFF the cluster and the slot goes
     * black; VietMap (Kiki drives it inside its running cluster task) is unaffected. The coordinator
     * is otherwise intent-driven and never re-pins an app that left on its own.
     *
     * Cheap gate on the CALLER thread (throttle + casting-state + in-flight), then the real probe
     * runs on the serial executor so it never overlaps a user cast/stop. Safe by construction: it
     * only ever re-casts a slot whose expected app is NOT on the cluster (slot already black) — it
     * cannot move a correctly-placed app. Debounce (missing on two consecutive probes) + per-package
     * cooldown keep the driving display from being yanked on a transient `am stack list` parse gap.
     */
    fun repinEscapedCastApps(interactive: Boolean? = null) {
        val now = System.currentTimeMillis()
        // 2.96 · R18 — màn đọc được là TẮT (standby) ⇒ thưa 4 s → 30 s; `null`/bật = nhịp cũ ([StandbyCadence], KDoc ở đó).
        if (now - lastRepinProbeAt < com.byd.clusternav.system.StandbyCadence.repinMinIntervalMs(interactive)) return
        val cur = state
        if (cur !is SimpleCastState.CastingSplit && cur !is SimpleCastState.CastingFull) return
        // 2.96 · R5 — lượt dò NỀN chỉ vào hàng khi executor rảnh hẳn ([BoundedCastExecutor.submitIfIdle]). [ĐO log xe 07/10 20:48:55]
        // `submit` thường ở đây đẩy lệnh chiếu nửa PHẢI (đang chờ sau nửa TRÁI) ra khỏi hàng 1 chỗ ⇒ VietMap không bao giờ lên cụm.
        // Bận ⇒ bỏ nhịp này, KHÔNG ghi `lastRepinProbeAt` ⇒ nhịp ~2 s sau thử lại. Cờ `repinInFlight` cũ bỏ đi: "rảnh hẳn" đã gồm
        // "không có repin nào đang chạy/chờ", và cờ đó kẹt `true` VĨNH VIỄN nếu thao tác repin bị bỏ khỏi hàng (finally không chạy).
        if (!executor.submitIfIdle("repin-watchdog") { doRepinEscapedCastApps() }) {
            if (now - lastRepinSkipLogAt >= REPIN_SKIP_LOG_INTERVAL_MS) {
                lastRepinSkipLogAt = now
                log("repin: bỏ nhịp — executor đang bận (lệnh chiếu/dừng có quyền trước, R5)")
            }
            return
        }
        lastRepinProbeAt = now
    }

    internal companion object {
        /** 2.96 · R5 — dòng "bỏ nhịp vì bận" tối đa một lần mỗi khoảng này (nhịp gọi ~2 s — không làm ngập log). */
        private const val REPIN_SKIP_LOG_INTERVAL_MS = 60_000L
        /** After a re-pin, ignore the same package this long (avoid fighting a persistent external launch). */
        internal const val REPIN_COOLDOWN_MS = 10_000L

        /** Bao lâu sau khi vào Error thì tự nhả về Idle/Off. */
        private const val ERROR_RECOVERY_MS = 3_000L

        /** Biên dịch MỘT lần: [dismissPipOnDisplay] quét từng dòng của `am stack list`. */
        private val STACK_HEADER_WITH_ID = Regex("""Stack id=(\d+).*displayId=(\d+)""")
    }
}
