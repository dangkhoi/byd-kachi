package com.byd.clusternav.modules.clustercast.simplified

/**
 * Manages cluster projection lifecycle.
 *
 * Open = profile sequence [30, 16, 35] → cluster shows projection surface.
 * Close = profile sequence [18, 0] → cluster returns to gauges.
 *
 * Idempotent: calling open when already open is a no-op, same for close.
 * Field-proven on Seal DL3 (BYD Song Plus DM-i 2023), 2026-08-02.
 */
class ProjectionManager(
    private val shell: SimpleCastShell,
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
) {
    @Volatile
    var isOpen: Boolean = false
        private set

    /**
     * Opens the cluster projection. After this call the cluster surface is active
     * and ready to receive app content via display 1.
     *
     * @return true if projection was opened (or already open), false on shell failure.
     */
    fun open(displayId: Int): Boolean {
        if (isOpen) return true

        // Profile 30 = curved mode (keeps km/h gauge visible)
        // Correct command: service call AutoContainer (BYD OEM service), NOT SurfaceFlinger
        val r1 = shell.execute("service call AutoContainer 2 i32 1000 i32 30 s16 \"\"")
        if (!r1.success) return false
        sleepMs(2000)

        // Profile 16 = keepKmh marker
        val r2 = shell.execute("service call AutoContainer 2 i32 1000 i32 16 s16 \"\"")
        if (!r2.success) return false
        sleepMs(2000)

        // Profile 35 = activate projection
        val r3 = shell.execute("service call AutoContainer 2 i32 1000 i32 35 s16 \"\"")
        if (!r3.success) return false
        sleepMs(1000)

        isOpen = true
        return true
    }

    /**
     * Closes the cluster projection. After this call the cluster returns to the
     * native gauge display.
     *
     * @return true if projection was closed (or already closed), false on shell failure.
     */
    fun close(displayId: Int): Boolean {
        if (!isOpen) return true

        // Profile 18 = close projection
        val r1 = shell.execute("service call AutoContainer 2 i32 1000 i32 18 s16 \"\"")
        if (!r1.success) return false
        sleepMs(300)

        // Profile 0 = refresh video output
        val r2 = shell.execute("service call AutoContainer 2 i32 1000 i32 0 s16 \"\"")
        if (!r2.success) return false

        isOpen = false
        return true
    }

    /** Reset state without issuing commands (e.g. after process restart). */
    fun resetState(open: Boolean) {
        isOpen = open
    }
}
