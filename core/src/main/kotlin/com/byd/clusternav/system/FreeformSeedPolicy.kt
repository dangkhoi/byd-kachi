package com.byd.clusternav.system

/**
 * SINGLE SANCTIONED WRITER of the four PERSISTENT global windowing states that can BRICK the cluster
 * (documented disaster: two uncoordinated subsystems writing these can wedge the cluster → firmware re-flash):
 *
 *   • `settings put|delete global enable_freeform_support`    — Settings.Global, read at BOOT by
 *   • `settings put|delete global force_resizable_activities` — ActivityTaskManagerService.retrieveSettings
 *                                                               (no ContentObserver). Survives reboot / reinstall
 *                                                               / data-clear. Takes effect only after a physical
 *                                                               ignition off/on.
 *   • `wm size <WxH> -d <display>`      — persisted to /data/system/display_settings.xml by uniqueId; survives reboot.
 *   • `wm density <dpi> -d <display>`   — persisted to /data/system/display_settings.xml by uniqueId; survives reboot.
 *
 * ── PURE (:core, no android.*) ────────────────────────────────────────────────────────────────────────────────
 * Every side effect is a function seam: a [MarkerStore] (durable 3-state marker) + a shell runner `(String)->String`.
 * The :app side ([com.byd.clusternav.system.FreeformSeedStore]) wires SharedPreferences + [ShellTransport] into these.
 * This keeps the decision/ordering logic unit-testable off-device (see FreeformSeedPolicyTest) while the actual
 * dadb shell + SharedPreferences live in :app.
 *
 * ── MARKER DISCIPLINE (generalized from cast's former CastShell.FF_* discipline so it is SHARED, not cast-private) ─
 *  • **commit-before-mutate**: the marker is committed (SYNCHRONOUSLY — `commit()`, not `apply()`) BEFORE any
 *    Settings.Global write, so a process death mid-seed still leaves a durable record AND the [unseed] path.
 *  • **FF_USER_REMOVED is TERMINAL**: once the user opts out, [ensureSeed] NEVER re-seeds (the next cast/open must
 *    not silently undo the user's removal).
 *  • **[resetDisplayAll] is the ONLY clearer** of the persisted geometry writes (`wm size`/`wm density`); the
 *    per-value writers never emit a clear.
 *
 * ── COMMAND STRINGS ───────────────────────────────────────────────────────────────────────────────────────────
 * Byte-identical to the proven on-car strings (locked by LauncherCommandGoldenTest + FreeformSeedPolicyTest + the
 * cast suites). The launcher [FreeformLaunch] freeform constant and cast's `CastShell.ensureFreeformSeed`/
 * `unseedFreeform` both route through THIS type. Do not reformat the [Companion] strings.
 */
class FreeformSeedPolicy(
    private val markers: MarkerStore,
    private val sh: (String) -> String,
    /**
     * 2.96 · R13 (soát Pass 1 [P2]) — IN-PROCESS reader of one `Settings.Global` key (`:app`: `Settings.Global.getString`,
     * 0 shell; `null` = absent / failed ⇒ written as before). `null` reader ⇒ read through [sh] (`settings get`, JVM/test).
     * Vì sao: đọc qua shell vẫn là 4 lượt kênh dadb lúc nổ máy — đúng thứ R11/R13 muốn bỏ (số đo
     * `docs/diagnostics/startup-timeline-2026-10-07.md` §6); đọc trong tiến trình mới là "0 lệnh". Đứng TRƯỚC [log] để
     * lambda cuối của các chỗ gọi cũ vẫn là [log].
     */
    private val readFlag: ((String) -> String?)? = null,
    private val log: (String) -> Unit = {},
) {

    /**
     * Durable 3-state marker store. On :app this is SharedPreferences; [commit] MUST be synchronous (the whole
     * point of commit-before-mutate is that the record survives a process death that happens mid-shell-write).
     */
    interface MarkerStore {
        fun read(): SeedState
        fun commit(state: SeedState)
    }

    /**
     * Seed marker. Codes 0/1/2 are byte-compatible with cast's former `CastShell.FF_NONE/FF_SEEDED/FF_USER_REMOVED`
     * so the existing on-disk marker (`clusternav_state`/`freeform_state`) is read/written unchanged.
     */
    enum class SeedState(val code: Int) {
        /** Never seeded. */
        NONE(0),

        /** Freeform flags were written by us. */
        SEEDED(1),

        /** The user explicitly removed the flags — TERMINAL, never auto re-seed. */
        USER_REMOVED(2),
        ;

        companion object {
            fun of(code: Int): SeedState = values().firstOrNull { it.code == code } ?: NONE
        }
    }

    /**
     * Seed the two freeform boot flags. Commit-before-mutate; [SeedState.USER_REMOVED] is terminal (skip).
     *
     * Idempotency here is w.r.t the DURABLE marker only — session-level RAM idempotency (seed at most once per
     * process) is the CALLER's concern (cast keeps its own `freeformSeeded` latch; the launcher factory adds one).
     *
     * 2.96 · R13: the marker is still committed first, but each flag is READ (`settings get global <key>`) and only
     * written when it is not already `1` — see [seedFlagsReadFirst].
     *
     * @return true iff the flags are ensured (written or already on); false iff skipped because the user removed them.
     */
    fun ensureSeed(): Boolean {
        if (markers.read() == SeedState.USER_REMOVED) {
            log(LOG_SKIP_USER_REMOVED)
            return false
        }
        markers.commit(SeedState.SEEDED) // ★ marker BEFORE mutate — survives a mid-write process death.
        // 2.96 · R13: đọc trước, chỉ ghi cờ chưa = 1 ([seedFlagsReadFirst]); đọc hỏng ("" từ seam bị từ chối/lỗi) ⇒ ghi như cũ.
        // Có [readFlag] (trong tiến trình, 0 shell) ⇒ đọc bằng nó theo KHOÁ; không ⇒ `settings get` qua [sh].
        val wrote = seedFlagsReadFirst(read = readerOf(readFlag) { sh(it) }, write = { sh(it) })
        log(if (wrote.isEmpty()) LOG_ALREADY_ON else LOG_SEEDED)
        return true
    }

    /**
     * Remove the two freeform boot flags and record the (terminal) [SeedState.USER_REMOVED] marker. The order
     * mirrors cast's proven `unseedFreeform`: delete the flags, then commit the terminal marker.
     */
    fun unseed() {
        UNSEED_CMDS.forEach { sh(it) }
        markers.commit(SeedState.USER_REMOVED)
        log(LOG_UNSEEDED)
    }

    /** true iff the durable marker records a prior seed ([SeedState.SEEDED]) — not the terminal removed state. */
    fun seedMarked(): Boolean = markers.read() == SeedState.SEEDED

    /** Current durable marker state. */
    fun state(): SeedState = markers.read()

    /**
     * Write the display LOGICAL SIZE. Persists to /data/system/display_settings.xml (survives reboot) → the ONLY
     * sanctioned clearer is [resetDisplayAll]. Returns the emitted command (byte-identical) for logging.
     */
    fun writeDisplaySize(displayId: Int, width: Int, height: Int): String =
        sizeCmd(displayId, width, height).also { sh(it) }

    /**
     * Write the display DENSITY. Persists to /data/system/display_settings.xml (survives reboot) → the ONLY
     * sanctioned clearer is [resetDisplayAll]. Returns the emitted command (byte-identical) for logging.
     */
    fun writeDisplayDensity(displayId: Int, dpi: Int): String =
        densityCmd(displayId, dpi).also { sh(it) }

    /**
     * THE ONLY CLEARER of the persisted geometry writes: `wm size reset` + `wm density reset` on [displayId].
     * (Overscan is A11+-removed and cast-specific, so it is NOT part of the sanctioned geometry-state contract.)
     */
    fun resetDisplayAll(displayId: Int) {
        resetCmds(displayId).forEach { sh(it) }
    }

    companion object {
        // ── Byte-identical command strings (proven on-car; locked by golden + FreeformSeedPolicyTest) ──

        /** Order-significant: enable_freeform_support THEN force_resizable_activities (matches cast + launcher). */
        val SEED_CMDS: List<String> = listOf(
            "settings put global enable_freeform_support 1",
            "settings put global force_resizable_activities 1",
        )

        /** 2.96 · R13 — the two flag keys, same order as [SEED_CMDS] (index-paired). */
        val SEED_FLAGS: List<String> = listOf("enable_freeform_support", "force_resizable_activities")

        /** 2.96 · R13 — read form for one flag (a READ: not a persistent-state write). */
        fun getCmd(key: String): String = "settings get global $key"

        private const val GET_PREFIX = "settings get global "

        /** Inverse of [getCmd]: the flag key of a `settings get global <key>` command (anything else ⇒ returned as-is). */
        fun keyOf(getCmd: String): String = getCmd.removePrefix(GET_PREFIX).trim()

        /**
         * 2.96 · R13 (soát Pass 1 [P2]) — the `read` seam for [seedFlagsReadFirst]: with an in-process [readFlag] (keyed by
         * flag name) the read never touches the shell — its `null` means absent/failed ⇒ write (fail-safe), NOT "fall back to
         * shell" (that would re-add the 2 round-trips the reader exists to remove). Without one, read via [shell]
         * (`settings get`; `null` = failed).
         */
        fun readerOf(readFlag: ((String) -> String?)?, shell: (String) -> String?): (String) -> String? =
            if (readFlag != null) { cmd -> readFlag(keyOf(cmd)) } else shell

        /**
         * 2.96 · R13 — pure decision: a flag is already on ONLY if its `settings get` output is exactly `1` (trimmed).
         * `null` (read failed), `""`, `null`/`0`/garbage ⇒ false ⇒ write (fail-safe = behaviour before R13).
         */
        fun flagAlreadyOn(readOut: String?): Boolean = readOut?.trim() == "1"

        /**
         * 2.96 · R13 — READ-BEFORE-WRITE for the two freeform boot flags (shared by the launcher [ensureSeed] path and the
         * cast `CastGeometryController.ensureFreeformFlags`). For each flag in order: run [getCmd]; write the byte-identical
         * [SEED_CMDS] entry only when [flagAlreadyOn] is false. [read] returns `null` on a failed read ⇒ written as before.
         * Scope (CLAUDE.md §4): no display, no app, no stack — two Settings.Global keys; undo = [unseed] / [UNSEED_CMDS].
         *
         * @return the put commands actually written (empty ⇒ both were already `1`).
         */
        fun seedFlagsReadFirst(read: (String) -> String?, write: (String) -> Unit): List<String> {
            val written = mutableListOf<String>()
            SEED_FLAGS.forEachIndexed { i, key ->
                if (!flagAlreadyOn(read(getCmd(key)))) {
                    val put = SEED_CMDS[i]
                    write(put)
                    written += put
                }
            }
            return written
        }

        /** Order-significant delete of the two flags (matches cast's `unseedFreeform`). */
        val UNSEED_CMDS: List<String> = listOf(
            "settings delete global enable_freeform_support",
            "settings delete global force_resizable_activities",
        )

        /** `wm size <W>x<H> -d <display>` — byte-identical to CastShell.forceDisplaySize / DisplayConfigurator. */
        fun sizeCmd(displayId: Int, width: Int, height: Int): String = "wm size ${width}x${height} -d $displayId"

        /** `wm density <dpi> -d <display>` — byte-identical to CastShell / CastDensityControl / DisplayConfigurator. */
        fun densityCmd(displayId: Int, dpi: Int): String = "wm density $dpi -d $displayId"

        /** size reset + density reset (the only clearer). Byte-identical to CastShell.resetDisplayAll's two writes. */
        fun resetCmds(displayId: Int): List<String> = listOf(
            "wm size reset -d $displayId",
            "wm density reset -d $displayId",
        )

        // ── Log strings (moved verbatim from CastShell so the cast path logs identically) ──
        const val LOG_SKIP_USER_REMOVED =
            "  ⚙ bỏ qua cờ freeform — người dùng đã chủ động gỡ. Chỉnh kích thước sẽ dùng wm size/overscan."
        const val LOG_SEEDED =
            "  ⚙ đã ghi cờ freeform (có hiệu lực sau khi TẮT MÁY XE hẳn 1 lần rồi mở lại)"
        const val LOG_ALREADY_ON =
            "  ⚙ cờ freeform đã bật sẵn — không ghi lại"
        const val LOG_UNSEEDED =
            "  ⚙ đã GỠ cờ freeform — cần TẮT MÁY XE hẳn 1 lần rồi mở lại mới có hiệu lực"
    }
}
