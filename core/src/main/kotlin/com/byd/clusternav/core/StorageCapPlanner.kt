package com.byd.clusternav.core

/**
 * Pure planner for capping a directory's total size — decides WHICH entries to delete (OLDEST first) so the
 * survivors fit under a byte cap. Android-free + filesystem-free so it is unit-tested off-car; the actual
 * enumeration + deletion (off-thread + degrade-safe) live in the app-side `com.byd.clusternav.DiagStorageCap`.
 *
 * WHY oldest-first: the diagnostic logs / per-frame arrow PNGs / segment screenshots are only useful for the
 * drive that produced them, so when the diagnostic files exceed the cap the OLDEST data is the least
 * valuable — evict it first, keep the freshest capture. This is a defensive backstop that runs even while
 * verbose data-collection is ON, so a single long drive can never fill the car's storage (the 7 GB+
 * nav_arrow_pngs / diag / CSV incident that motivated the cap).
 *
 * 2.92 (DIAG-CAP-USERDATA): "diagnostic files" means the [DiagFiles] allow-list ONLY — the same external dir
 * also holds user data (photos, wallpapers, car images, profile exports, side-loaded voice packs) that the cap
 * must never touch; see [selectDiagForDeletion].
 */
object StorageCapPlanner {

    /** Default cap for the app-external diagnostics dir: ~150 MB. */
    const val DEFAULT_CAP_BYTES: Long = 150L * 1024L * 1024L

    /** One prunable entry: a stable [id] (e.g. absolute path), its [sizeBytes], and [lastModifiedMs] (epoch). */
    data class Entry(val id: String, val sizeBytes: Long, val lastModifiedMs: Long)

    /**
     * 2.92 (DIAG-CAP-USERDATA) — the ONLY entry point `DiagStorageCap` may use: [entries] carry ids RELATIVE to
     * the app-external root (`/`-separated); anything [DiagFiles.isDiagnostic] does not recognise (user photos,
     * wallpapers, car images, profile exports, side-loaded voice packs, unknown files, absolute/odd ids) is dropped
     * BEFORE the arithmetic, so it is never selected and never counted. The cap therefore applies to the
     * diagnostic set alone: user data, however old or large, neither gets deleted nor pushes logs out early.
     */
    fun selectDiagForDeletion(entries: List<Entry>, capBytes: Long = DEFAULT_CAP_BYTES): List<String> =
        selectForDeletion(entries.filter { DiagFiles.isDiagnostic(it.id) }, capBytes)

    /**
     * Given [entries] and a [capBytes], return the ids to delete — OLDEST ([lastModifiedMs]) first — so the
     * remaining total is `<= capBytes`. Returns empty when the total already fits (nothing deleted). Stops as
     * soon as the survivors fit, so it deletes the FEWEST oldest entries needed.
     *
     * Defensive edges: negative [sizeBytes] are floored to 0; a 0-byte (or glitched) entry is NEVER selected
     * on its own because deleting it frees nothing (it can't be the cause of an overflow); ties on
     * [lastModifiedMs] break by [id] for deterministic output; a negative [capBytes] (nonsensical) evicts
     * everything. The result preserves oldest-first order.
     */
    fun selectForDeletion(entries: List<Entry>, capBytes: Long = DEFAULT_CAP_BYTES): List<String> {
        if (capBytes < 0L) return entries.map { it.id }
        fun sizeOf(e: Entry): Long = if (e.sizeBytes < 0L) 0L else e.sizeBytes
        var total = entries.sumOf(::sizeOf)
        if (total <= capBytes) return emptyList()
        val oldestFirst = entries.sortedWith(compareBy({ it.lastModifiedMs }, { it.id }))
        val toDelete = ArrayList<String>()
        for (e in oldestFirst) {
            if (total <= capBytes) break
            val sz = sizeOf(e)
            if (sz <= 0L) continue          // a 0-byte (or glitched) entry frees nothing → never the cause of overflow, skip it
            toDelete.add(e.id)
            total -= sz
        }
        return toDelete
    }
}
