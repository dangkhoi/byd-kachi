package com.byd.clusternav.vietmapwidget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * SharedPreferences wrapper for VietMap widget binding state.
 * All commit() calls are checked — failure is logged and retried once.
 * Fail-closed: if commit still fails after retry, the operation is logged
 * and the caller treats it as non-persistent (widget re-binding on next restart).
 */
internal class VietMapWidgetPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun widgetId(slot: VietMapWidgetSlot): Int? =
        prefs.getInt(slot.preferenceKey, NO_WIDGET_ID).takeUnless { it == NO_WIDGET_ID }

    fun saveWidgetId(slot: VietMapWidgetSlot, appWidgetId: Int, providerVersion: String?) {
        commitWithRetry("saveWidgetId(${slot.name})") {
            putInt(slot.preferenceKey, appWidgetId)
            putString(KEY_PROVIDER_VERSION, providerVersion)
        }
    }

    fun clearWidgetId(slot: VietMapWidgetSlot) {
        commitWithRetry("clearWidgetId(${slot.name})") {
            remove(slot.preferenceKey)
        }
    }

    fun clearAll() {
        commitWithRetry("clearAll") {
            // Iterate all slots so new providers (e.g. ALERT_FULL) are cleared automatically.
            VietMapWidgetSlot.entries.forEach { remove(it.preferenceKey) }
            remove(KEY_PROVIDER_VERSION)
        }
    }

    /**
     * Commit with a single retry on failure. Logs at WARN on first fail, ERROR if retry also fails.
     * Returns true only if commit succeeded (first try or retry).
     */
    private fun commitWithRetry(
        operation: String,
        edits: SharedPreferences.Editor.() -> SharedPreferences.Editor,
    ): Boolean {
        val editor = prefs.edit().edits()
        val firstAttempt = editor.commit()
        if (firstAttempt) return true
        Log.w(TAG, "$operation: commit failed, retrying once")
        // Retry: re-read + re-apply (editor is single-shot, need fresh editor)
        val retryEditor = prefs.edit().edits()
        val retryResult = retryEditor.commit()
        if (retryResult) {
            Log.i(TAG, "$operation: retry succeeded")
            return true
        }
        Log.e(TAG, "$operation: commit failed after retry — state may not persist across restart")
        return false
    }

    companion object {
        private const val TAG = "WidgetPrefs"
        private const val FILE_NAME = "vietmap_widget_bridge"
        private const val KEY_PROVIDER_VERSION = "provider_version"
        private const val NO_WIDGET_ID = -1
    }
}
