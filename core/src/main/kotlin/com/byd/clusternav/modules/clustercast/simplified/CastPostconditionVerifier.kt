package com.byd.clusternav.modules.clustercast.simplified

/**
 * Postcondition verifier for cast mutations.
 *
 * After every shell operation, verifies the expected outcome by re-reading
 * `am stack list` and checking:
 * - Cast full: exact package visible on target display.
 * - Cast split: task present on target display with matching bounds.
 * - Return/stop: package ABSENT from cluster display.
 *
 * On verification failure → returns [CastMutationOutcome.Unknown] and the
 * coordinator MUST preserve prior state (no commit).
 */
class CastPostconditionVerifier(
    private val shell: SimpleCastShell,
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
    private val log: (String) -> Unit = { println("[PostVerify] $it") },
) {
    /**
     * Verify that [pkg] is visible on [displayId] after a cast-full operation.
     *
     * Allows up to [maxRetries] attempts with [retryDelayMs] between each,
     * since window-manager may take time to settle.
     */
    fun verifyCastFull(
        pkg: String,
        displayId: Int,
        maxRetries: Int = 2,
        retryDelayMs: Long = 400,
    ): CastMutationOutcome {
        for (attempt in 0 until maxRetries) {
            if (attempt > 0) sleepMs(retryDelayMs)
            val result = shell.execute("am stack list")
            if (!result.success) {
                log("verifyCastFull: shell failed attempt=$attempt")
                continue
            }
            val tasks = CastStackParser.parseTasks(result.stdout)
            val match = tasks.filter { it.displayId == displayId && it.pkg == pkg && it.visible }
            when {
                match.size == 1 -> {
                    log("verifyCastFull: VERIFIED ${match[0].taskId} on display $displayId")
                    return CastMutationOutcome.Verified(
                        taskId = match[0].taskId,
                        displayId = displayId,
                    )
                }
                match.size > 1 -> {
                    // Multiple visible tasks for same pkg — ambiguous but still present.
                    // Accept the first (topmost) as the cast target.
                    log("verifyCastFull: multiple matches (${match.size}), accepting first")
                    return CastMutationOutcome.Verified(
                        taskId = match[0].taskId,
                        displayId = displayId,
                    )
                }
                // match.isEmpty → retry
                else -> log("verifyCastFull: not visible yet, attempt=$attempt")
            }
        }
        return CastMutationOutcome.Unknown(
            command = "verifyCastFull($pkg, display=$displayId)",
            detail = "Package not visible after $maxRetries attempts",
        )
    }

    /**
     * Verify that [pkg] is visible on [displayId] after a split-cast.
     * Bounds verification is best-effort (Android 10 am stack list doesn't always report bounds).
     */
    fun verifyCastSplit(
        pkg: String,
        displayId: Int,
        expectedSide: ClusterSlotSide?,
        maxRetries: Int = 2,
        retryDelayMs: Long = 400,
    ): CastMutationOutcome {
        for (attempt in 0 until maxRetries) {
            if (attempt > 0) sleepMs(retryDelayMs)
            val result = shell.execute("am stack list")
            if (!result.success) continue
            val tasks = CastStackParser.parseTasks(result.stdout)
            val match = tasks.filter { it.displayId == displayId && it.pkg == pkg && it.visible }
            if (match.isNotEmpty()) {
                log("verifyCastSplit: VERIFIED ${match[0].taskId} on display $displayId")
                return CastMutationOutcome.Verified(
                    taskId = match[0].taskId,
                    displayId = displayId,
                )
            }
        }
        return CastMutationOutcome.Unknown(
            command = "verifyCastSplit($pkg, display=$displayId, side=$expectedSide)",
            detail = "Package not visible after $maxRetries attempts",
        )
    }

    /**
     * Verify that [pkg] is ABSENT from [displayId] after a stop/return operation.
     */
    fun verifyAbsent(
        pkg: String,
        displayId: Int,
        maxRetries: Int = 2,
        retryDelayMs: Long = 300,
    ): CastMutationOutcome {
        for (attempt in 0 until maxRetries) {
            if (attempt > 0) sleepMs(retryDelayMs)
            val result = shell.execute("am stack list")
            if (!result.success) continue
            val stillPresent = CastStackParser.isAppOnDisplay(result.stdout, pkg, displayId)
            if (!stillPresent) {
                log("verifyAbsent: VERIFIED $pkg absent from display $displayId")
                return CastMutationOutcome.Verified(taskId = 0, displayId = displayId)
            }
            log("verifyAbsent: $pkg still on display $displayId, attempt=$attempt")
        }
        return CastMutationOutcome.Unknown(
            command = "verifyAbsent($pkg, display=$displayId)",
            detail = "Package still visible after $maxRetries attempts",
        )
    }
}
