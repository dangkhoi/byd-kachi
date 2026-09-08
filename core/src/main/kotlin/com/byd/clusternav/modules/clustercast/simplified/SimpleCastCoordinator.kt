package com.byd.clusternav.modules.clustercast.simplified

import java.util.concurrent.atomic.AtomicReference

/**
 * Simplified Cluster Cast coordinator with safety (T4 remediation).
 * Single owner of projection/cast state. Mutations run on bounded executor.
 * UI observes [state] and emits [SimpleCastIntent].
 */
class SimpleCastCoordinator(
    private val projection: ProjectionManager,
    private val configurator: DisplayConfigurator,
    private val mover: AppMover,
    val prefs: SimpleCastPrefs,
    private val shell: SimpleCastShell,
    private val displayId: Int,
    private val castTimeoutMs: Long = 15_000L,
    private val stopTimeoutMs: Long = 5_000L,
    // This app's own installed package, injected from :app (BuildConfig.APPLICATION_ID). Default keeps the
    // legacy value for JVM tests; production passes com.byd.clusternav2 so cast self-exclusion + the black
    // placeholder launch target the correct isolated app.
    private val selfPackage: String = "com.byd.clusternav",
) {
    private val executor = BoundedCastExecutor(
        castTimeoutMs = castTimeoutMs,
        stopTimeoutMs = stopTimeoutMs,
        onTimeout = { tag -> log("TIMEOUT: $tag") },
    )

    private val verifier = CastPostconditionVerifier(
        shell = shell,
        sleepMs = { Thread.sleep(it) },
        log = { msg -> log("verify: $msg") },
    )

    private fun log(msg: String) {
        println("[SimpleCast] $msg")
    }

    /** Owns freeform task resize + per-app profile persistence/restore (R4/R5/R6). */
    private val geometry = CastGeometryController(shell, prefs, displayId) { msg -> log(msg) }

    // TRIAL watchdog state (owner 2026-08-14): re-pin a cast app that an external trigger (e.g. Kiki
    // starting GMaps navigation) pulled off the cluster. Debounce + cooldown so driving is never
    // yanked on a transient am-stack parse gap.
    private val repinMissStreak = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val repinCooldownUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val repinInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastRepinProbeAt = 0L

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

    private fun setState(new: SimpleCastState) {
        _state.set(new)
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it(new) }
    }

    /** Set error state with auto-recovery to Idle (or Off) after 3 seconds. */
    private fun setError(message: String) {
        setState(SimpleCastState.Error(message))
        executor.submit("error-recovery") {
            Thread.sleep(3000)
            if (state is SimpleCastState.Error) {
                setState(if (projection.isOpen) SimpleCastState.Idle else SimpleCastState.Off)
            }
        }
    }

    // ─── Projection lifecycle ─────────────────────────────────────────────────
    /**
     * Opens projection (Activity onCreate; and on boot via FloatingBubbleService when autostart on).
     * R10: idempotent — opens only from Off/Error; opening/idle/casting is a no-op (both callers safe).
     */
    fun openProjection() {
        executor.submit("openProjection") {
            when (state) {
                is SimpleCastState.Off, is SimpleCastState.Error -> Unit // proceed
                else -> return@submit // already open/opening/casting/stopping/closing
            }
            setState(SimpleCastState.Opening)

            if (!prefs.dozeWhitelistApplied()) {
                shell.execute("cmd deviceidle whitelist +vn.vietmap.live")
                prefs.setDozeWhitelistApplied(true)
            }

            // Enable freeform boot flags so per-app bounds (resize + split) work after next power-cycle.
            // These settings are read ONLY at boot by ActivityTaskManagerService.retrieveSettings()
            // (no ContentObserver), so they take effect after a physical ignition off/on.
            // Idempotent — safe to run every open.
            geometry.ensureFreeformFlags()

            cleanDisplay1()
            configurator.apply(displayId, DisplayConfig.NORMAL_DEFAULT)

            projection.resetState(false)
            val ok = projection.open(displayId)
            if (!ok) { setError("Projection open failed"); return@submit }

            // Launch + resize black placeholder to keep projection alive.
            // Component MUST be <installed applicationId>/<full class FQN>. The class FQN keeps the code
            // namespace (com.byd.clusternav.*), which now DIFFERS from the applicationId (com.byd.clusternav2).
            // A leading-dot class ('.modules...') would be resolved by am/ComponentName against the PACKAGE part
            // (selfPackage) → 'com.byd.clusternav2.modules...ClusterBlackActivity', a class that does NOT exist
            // (the registered component is 'com.byd.clusternav2/com.byd.clusternav.modules...ClusterBlackActivity').
            // So spell the class in full — never relative — for correct launch under the isolated app.
            shell.execute("am start --display $displayId --windowingMode 5" +
                " -n '$selfPackage/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
            Thread.sleep(1000)
            val stackResult = shell.execute("am stack list")
            if (stackResult.success) {
                val taskId = CastStackParser.findTaskId(stackResult.stdout, selfPackage, displayId)
                    ?: CastStackParser.findTaskId(stackResult.stdout, "ClusterBlackActivity", displayId)
                if (taskId != null) shell.execute("am task resize $taskId 0 0 1920 720")
            }

            // Adopt external app already on display 1 (e.g. CP from previous session)
            val adoptResult = shell.execute("am stack list")
            var adopted = false
            if (adoptResult.success) {
                val tasks = CastStackParser.parseTasks(adoptResult.stdout)
                val ext = tasks.firstOrNull { t ->
                    t.displayId == displayId && t.visible &&
                        t.pkg != selfPackage && !t.pkg.startsWith("com.android.")
                }
                if (ext != null) {
                    val appType = AppMover.classifyApp(ext.pkg)
                    setState(SimpleCastState.CastingFull(ext.pkg, appType, DisplayConfig.forAppType(appType)))
                    adopted = true
                }
            }
            if (!adopted) setState(SimpleCastState.Idle)
        }
    }
    /** Closes projection. Called on app exit. */
    fun closeProjection() {
        executor.submit("closeProjection") {
            // Return any active apps first (known state)
            returnAllApps()
            // Safety net: also clean any unknown leftovers from display 1
            cleanDisplay1()
            setState(SimpleCastState.Closing)
            val ok = projection.close(displayId)
            setState(if (ok) SimpleCastState.Off else SimpleCastState.Error("Projection close failed"))
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

    private fun handleCastFull(intent: SimpleCastIntent.CastFull) {
        // R4: Precondition validation (fail-fast before any shell command)
        val rejectReason = CastSlotValidator.validateCastFull(intent.pkg, intent.appType, projection.isOpen)
        if (rejectReason != null) {
            log("CastFull REJECTED: $rejectReason for ${intent.pkg}")
            setError("Cast rejected: $rejectReason")
            return
        }

        val current = state
        // If projection is still opening, wait and retry once
        if (current == SimpleCastState.Opening) {
            Thread.sleep(1500) // projection open takes ~1.1s
            if (state != SimpleCastState.Idle) return // still not ready — give up
        }
        val afterWait = state
        // Only cast from IDLE or replace current full cast
        if (afterWait != SimpleCastState.Idle && afterWait !is SimpleCastState.CastingFull) {
            return // invalid transition — ignore
        }

        // R4: For protected apps, verify we will land in fullscreen stack (not freeform)
        if (intent.appType.isProtected && !geometry.verifyFullscreenStackAvailable()) {
            setError("Cast rejected: ${CastRejectReason.PROTECTED_FULLSCREEN_STACK_UNPROVEN}")
            return
        }

        // If currently casting something else full, stop it first
        if (afterWait is SimpleCastState.CastingFull) {
            returnApp(afterWait.targetPkg, afterWait.appType)
        }

        // If app is ALREADY on display 1, just adopt state — don't re-cast (prevents infinite loop).
        // BUT still restore the saved size + DPI: previously this branch used a fresh NORMAL_DEFAULT
        // config and returned BEFORE applySavedProfile, so a resized app lost its size on every
        // re-cast where it happened to already be on the cluster (owner bug #1, 2026-08-12).
        if (geometry.isAppOnDisplay(intent.pkg, displayId)) {
            val adoptConfig = configurator.resolveConfig(intent.pkg, intent.appType, prefs)
            setState(SimpleCastState.CastingFull(intent.pkg, intent.appType, adoptConfig))
            if (intent.appType == AppType.NORMAL) {
                geometry.applySavedProfile(intent.pkg, CastProfile.FULL)
            }
            return
        }

        val config = configurator.resolveConfig(intent.pkg, intent.appType, prefs)
        if (!configurator.apply(displayId, config)) {
            setError("Display config failed")
            return
        }

        val castTaskId = mover.castToCluster(
            pkg = intent.pkg,
            activity = null,
            displayId = displayId,
            appType = intent.appType,
        )
        if (castTaskId != null) {
            // R3: Postcondition verification — use verifier instead of simple isAppOnDisplay
            val outcome = verifier.verifyCastFull(intent.pkg, displayId)
            when (outcome) {
                is CastMutationOutcome.Verified -> {
                    val savedTaskId = if (castTaskId > 0) castTaskId else outcome.taskId
                    setState(SimpleCastState.CastingFull(intent.pkg, intent.appType, config, savedTaskId))
                    // R6: Apply saved FULL-profile bounds + density ONLY after verified landing
                    if (intent.appType == AppType.NORMAL) {
                        geometry.applySavedProfile(intent.pkg, CastProfile.FULL)
                    }
                }
                else -> {
                    // Postcondition failed — do NOT commit state, do NOT persist prefs
                    log("postcondition FAIL: $outcome")
                    setError("Cast failed: app did not land on cluster")
                }
            }
        } else {
            val msg = if (intent.appType.isProtected) {
                "Open ${intent.appType.name} app first / Mở app trước rồi chiếu"
            } else {
                "Cast failed / Không chiếu được"
            }
            setError(msg)
        }
    }

    private fun handleCastSlot(intent: SimpleCastIntent.CastSlot) {
        // R4: Precondition — rejects CP/AA and occupied slots
        val rejectReason = CastSlotValidator.validateCastSlot(
            pkg = intent.pkg,
            side = intent.side,
            currentState = state,
            projectionOpen = projection.isOpen,
        )
        if (rejectReason != null) {
            log("CastSlot REJECTED: $rejectReason for ${intent.pkg} side=${intent.side}")
            setError("Slot rejected: $rejectReason")
            return
        }

        val current = state
        // If projection is still opening, wait and retry once
        if (current == SimpleCastState.Opening) {
            Thread.sleep(1500)
            if (state != SimpleCastState.Idle) return
        }
        val afterWait = state
        if (afterWait != SimpleCastState.Idle && afterWait !is SimpleCastState.CastingSplit) {
            return // can only split from idle or existing split
        }

        val config = configurator.resolveConfig(intent.pkg, AppType.NORMAL, prefs)
        val slot = SlotState(intent.pkg, config)

        // Split mode: display config (wm size/overscan) is DISPLAY-GLOBAL on Android.
        if (afterWait is SimpleCastState.CastingSplit) {
            // Don't re-apply display config — would affect the existing app
        } else {
            if (!configurator.apply(displayId, config)) {
                setError("Display config failed for slot")
                return
            }
        }

        val leftPercent = prefs.splitRatioLeftPercent()
        val ok = mover.castToCluster(
            pkg = intent.pkg,
            activity = null,
            displayId = displayId,
            appType = AppType.NORMAL,
            slotSide = intent.side,
            leftPercent = leftPercent,
        )
        if (ok == null) {
            setError("Cast to slot failed")
            return
        }

        // R3: Postcondition verification for split
        val outcome = verifier.verifyCastSplit(intent.pkg, displayId, intent.side)
        when (outcome) {
            is CastMutationOutcome.Verified -> {
                val newState = when {
                    afterWait is SimpleCastState.CastingSplit && intent.side == ClusterSlotSide.LEFT ->
                        afterWait.copy(left = slot)
                    afterWait is SimpleCastState.CastingSplit && intent.side == ClusterSlotSide.RIGHT ->
                        afterWait.copy(right = slot)
                    intent.side == ClusterSlotSide.LEFT ->
                        SimpleCastState.CastingSplit(left = slot, right = null)
                    else ->
                        SimpleCastState.CastingSplit(left = null, right = slot)
                }
                setState(newState)
                // R6: Restore saved profile geometry (bounds + DPI) ONLY after verified landing.
                // If no profile is saved, the ratio-default bounds from AppMover.fitToCluster stand.
                geometry.applySavedProfile(intent.pkg, CastProfile.of(intent.side, leftPercent))
            }
            else -> {
                log("CastSlot postcondition FAIL: $outcome")
                setError("Slot cast failed: app did not land")
            }
        }
    }

    private fun handleStop(intent: SimpleCastIntent.Stop) {
        val current = state
        when {
            current is SimpleCastState.CastingFull -> {
                setState(SimpleCastState.Stopping)
                returnApp(current.targetPkg, current.appType, current.taskId)
                if (current.appType.isProtected) {
                    shell.execute("wm density reset -d $displayId")
                } else {
                    refreshCluster()
                }
                setState(SimpleCastState.Idle)
            }
            current is SimpleCastState.CastingSplit && intent.slot != null -> {
                val slotToStop = when (intent.slot) {
                    ClusterSlotSide.LEFT -> current.left
                    ClusterSlotSide.RIGHT -> current.right
                }
                if (slotToStop != null) {
                    returnApp(slotToStop.pkg, AppType.NORMAL)
                }
                // Determine remaining after stopping slot
                val remainingLeft = if (intent.slot == ClusterSlotSide.LEFT) null else current.left
                val remainingRight = if (intent.slot == ClusterSlotSide.RIGHT) null else current.right
                if (remainingLeft == null && remainingRight == null) {
                    setState(SimpleCastState.Idle)
                } else {
                    setState(SimpleCastState.CastingSplit(left = remainingLeft, right = remainingRight))
                }
            }
            current is SimpleCastState.CastingSplit && intent.slot == null -> {
                setState(SimpleCastState.Stopping)
                current.left?.let { returnApp(it.pkg, AppType.NORMAL) }
                current.right?.let { returnApp(it.pkg, AppType.NORMAL) }
                refreshCluster()
                setState(SimpleCastState.Idle)
            }
            else -> {}
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun returnApp(pkg: String, appType: AppType, taskId: Int? = null) {
        if (pkg == selfPackage) return
        log("returnApp: pkg=$pkg, appType=$appType, taskId=$taskId")
        mover.returnToMain(pkg = pkg, activity = null, appType = appType, taskId = taskId, clusterDisplayId = displayId)
    }

    /** Clear stale frame from cluster display after stop. */
    private fun refreshCluster() {
        shell.execute("service call AutoContainer 2 i32 1000 i32 0 s16 \"\"")
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

    private fun cleanDisplay1() = CastDisplayCleaner.cleanDisplay(shell, displayId)

    private fun closeProjectionSync() {
        returnAllApps()
        // Reset display to defaults before closing — undo all wm changes
        configurator.reset(displayId)
        setState(SimpleCastState.Closing)
        val ok = projection.close(displayId)
        if (ok) setState(SimpleCastState.Off) else setError("Close failed")
    }

    // ─── Resize active target / slot ─────────────────────────────────────────

    /**
     * Resize the currently casting full app to the given bounds (R5/R6). NORMAL-only.
     * Persistence (FULL profile) happens ONLY on shell success — see [CastGeometryController].
     * Thread-safe — queued on serial executor.
     */
    fun resizeActiveTarget(left: Int, top: Int, right: Int, bottom: Int) = executor.submit("resize") {
        val current = state as? SimpleCastState.CastingFull ?: return@submit
        if (!current.appType.isResizable || right <= left || bottom <= top) {
            log("resizeActiveTarget: skip (state/bounds invalid) [$left,$top,$right,$bottom]")
            return@submit
        }
        geometry.resizeFull(current.targetPkg, left, top, right, bottom)
    }

    /**
     * Resize one split slot's app to the given bounds (R5/R6). Only valid in
     * [SimpleCastState.CastingSplit]; targets the app currently in [side]. Persists to the
     * matching profile ([CastProfile.of] on the current split ratio) ONLY on shell success.
     * Thread-safe — queued on serial executor.
     */
    fun resizeActiveSlot(side: ClusterSlotSide, left: Int, top: Int, right: Int, bottom: Int) =
        executor.submit("resize-slot") {
            val current = state as? SimpleCastState.CastingSplit ?: return@submit
            val pkg = (if (side == ClusterSlotSide.LEFT) current.left else current.right)?.pkg ?: return@submit
            if (right <= left || bottom <= top) {
                log("resizeActiveSlot: skip (bounds invalid) [$left,$top,$right,$bottom]")
                return@submit
            }
            geometry.resizeSlot(pkg, CastProfile.of(side, prefs.splitRatioLeftPercent()), left, top, right, bottom)
        }

    /**
     * Apply a new split ratio ([leftPercent]) live (Feature 2 · split-ratio buttons).
     *
     * (a) ALWAYS persists [SimpleCastPrefs.setSplitRatioLeftPercent] so the next split cast uses it.
     * (b) If currently [SimpleCastState.CastingSplit], re-resizes BOTH occupied slots to the new ratio
     *     IN PLACE (no return+recast): left = [0,0, W·pct/100, H], right = [W·pct/100, 0, W, H], where
     *     W×H is the cluster display's measured size ([AppMover.queryDisplaySize], fallback 1920×720).
     *     Reuses the same [CastGeometryController.resizeSlot] path as [resizeActiveSlot], so per-slot
     *     bounds persist to the matching per-ratio profile ONLY on shell success (R6).
     *
     * Thread-safe — queued on the serial executor.
     */
    fun applySplitRatioLive(leftPercent: Int) = executor.submit("split-ratio-live") {
        // Validate the incoming ratio to one of the 9 supported buckets (10..90). The UI only ever
        // sends CastProfile.SPLIT_PERCENTS values, but this is a public entry point: an out-of-set
        // percent would (a) persist a bad ratio that corrupts the NEXT split cast's fitToCluster
        // bounds, (b) drive a degenerate `am task resize` (resizeSlot has no bounds guard), and
        // (c) file per-slot bounds under a normalized profile key (CastProfile.of below) that no
        // longer matches the geometry. Clamp to the R3 default (50) so all three stay consistent.
        val pct = CastProfile.normalizePercent(leftPercent)
        prefs.setSplitRatioLeftPercent(pct)
        val current = state as? SimpleCastState.CastingSplit ?: return@submit
        val (width, height) = mover.queryDisplaySize(displayId) ?: (1920 to 720)
        val boundary = width * pct / 100
        current.left?.let {
            geometry.resizeSlot(it.pkg, CastProfile.of(ClusterSlotSide.LEFT, pct), 0, 0, boundary, height)
        }
        current.right?.let {
            geometry.resizeSlot(it.pkg, CastProfile.of(ClusterSlotSide.RIGHT, pct), boundary, 0, width, height)
        }
    }

    /** @see CastGeometryController.isFreeformAlive */
    fun isFreeformAlive(): Boolean = geometry.isFreeformAlive()

    // ─── Density control ──────────────────────────────────────────────────────
    /** @see CastDensityControl.set — persists ONLY on shell success (R6). */
    fun setDensity(dpi: Int?) = executor.submit("density") {
        CastDensityControl.set(shell, prefs, displayId, dpi, (state as? SimpleCastState.CastingFull)?.targetPkg)
    }

    /** @see CastDensityControl.setForSplit — split DPI, persists the per-ratio profile on success (R4/#5). */
    fun setDensitySplit(dpi: Int?) = executor.submit("density-split") {
        CastDensityControl.setForSplit(shell, prefs, displayId, dpi, state)
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
            val stackMatch = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(line)
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
    fun repinEscapedCastApps() {
        val now = System.currentTimeMillis()
        if (now - lastRepinProbeAt < REPIN_PROBE_MIN_INTERVAL_MS) return
        val cur = state
        if (cur !is SimpleCastState.CastingSplit && cur !is SimpleCastState.CastingFull) return
        if (!repinInFlight.compareAndSet(false, true)) return
        lastRepinProbeAt = now
        executor.submit("repin-watchdog") {
            try { doRepinEscapedCastApps() } finally { repinInFlight.set(false) }
        }
    }

    private fun doRepinEscapedCastApps() {
        val expected: List<Pair<String, ClusterSlotSide?>> = when (val cur = state) {
            is SimpleCastState.CastingSplit -> buildList {
                cur.left?.let { add(it.pkg to ClusterSlotSide.LEFT) }
                cur.right?.let { add(it.pkg to ClusterSlotSide.RIGHT) }
            }
            is SimpleCastState.CastingFull ->
                if (cur.appType == AppType.NORMAL) listOf(cur.targetPkg to null) else emptyList()
            else -> emptyList()
        }
        if (expected.isEmpty()) return
        val now = System.currentTimeMillis()
        for ((pkg, side) in expected) {
            if (pkg == selfPackage) continue
            if (geometry.isAppOnDisplay(pkg, displayId)) { repinMissStreak.remove(pkg); continue }
            // Debounce: require MISSING on two consecutive probes (ignore transient parse gaps and the
            // split-second while Kiki's own launch is in flight).
            val streak = (repinMissStreak[pkg] ?: 0) + 1
            repinMissStreak[pkg] = streak
            if (streak < 2) { log("repin: $pkg not on cluster (streak=$streak) — waiting"); continue }
            if (now < (repinCooldownUntil[pkg] ?: 0L)) { log("repin: $pkg cooling down"); continue }
            log("repin: $pkg escaped cluster → re-cast to slot=$side (keep running task/nav)")
            val leftPercent = prefs.splitRatioLeftPercent()
            val ok = mover.castToCluster(
                pkg = pkg, activity = null, displayId = displayId,
                appType = AppType.NORMAL, slotSide = side, leftPercent = leftPercent,
            )
            repinCooldownUntil[pkg] = now + REPIN_COOLDOWN_MS
            repinMissStreak.remove(pkg)
            if (ok != null) {
                geometry.applySavedProfile(pkg, if (side != null) CastProfile.of(side, leftPercent) else CastProfile.FULL)
                log("repin: $pkg re-cast issued (slot=$side)")
            } else {
                log("repin: $pkg re-cast FAILED")
            }
        }
    }

    private companion object {
        /** Min gap between watchdog probes (caller ticks ~2s; probe runs ~ every other tick). */
        private const val REPIN_PROBE_MIN_INTERVAL_MS = 4_000L
        /** After a re-pin, ignore the same package this long (avoid fighting a persistent external launch). */
        private const val REPIN_COOLDOWN_MS = 10_000L
    }
}
