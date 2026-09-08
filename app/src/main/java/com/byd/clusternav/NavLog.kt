package com.byd.clusternav

import android.content.Context

/**
 * Cheap IN-MEMORY gate for verbose diagnostic logging.
 *
 * OTA ships a RELEASE apk (no `BuildConfig.DEBUG`) and the owner debugs on-car via logcat, so verbosity is a
 * flag defaulting OFF. Since 2026-08-28 it is controlled SOLELY by the `-PdiagLog=true` build flag
 * (`BuildConfig.DIAG_LOG`, via [Prefs.navVerboseLog]'s default) — the runtime settings switch and the hidden
 * version-label long-press were REMOVED because the VietMap/Waze screen-capture they collected is gone. The
 * value is mirrored here so per-frame hot paths (BydHal keep-alive, ClusterBroadcaster, ManeuverSignature.note)
 * read a `@Volatile` field instead of hitting SharedPreferences ~4×/second.
 *
 * The remaining verbose consumers are all valid GMaps diagnostics ([NavNotifLog], [NavNotifRawLog],
 * ManeuverSignature notes) plus the [DiagStorageCap] periodic sweep. The source of truth is
 * [Prefs.navVerboseLog]; [init] refreshes this mirror at the app entry points that always run
 * (MainActivity.onCreate, NavNotificationListener.onListenerConnected).
 */
object NavLog {
    @Volatile
    var verbose = false

    /** Refresh the in-memory gate from the persisted flag. Call at entry points that always run. */
    fun init(ctx: Context) {
        // Verbose defaults FALSE — normal use collects NO logs / PNGs. The old `|| BuildConfig.DEBUG` auto-on was
        // REMOVED (owner 2026-08-18) because the debug build force-enabled verbose and filled the car's storage.
        // The runtime data-collection switch + hidden long-press were then REMOVED (2026-08-28) once the
        // VietMap/Waze capture they collected was gone; verbose is now enabled only by a `-PdiagLog=true` build
        // ([Prefs.navVerboseLog]'s default). The storage cap ([DiagStorageCap]) stays the always-on backstop.
        verbose = Prefs.navVerboseLog(ctx)
    }
}
