package com.byd.clusternav

import android.content.Context
import android.content.res.Configuration

/**
 * ThemeMode — persisted LIGHT/DARK selection for ClusterNav (framework theme, NO AppCompat).
 *
 * The app has no MaterialComponents/AppCompat dependency, so [androidx.appcompat.app.AppCompatDelegate]
 * night-mode APIs are unavailable. Instead this object persists the user's choice and, at Activity
 * [android.app.Activity.attachBaseContext] time, forces the resource configuration's night bit so the
 * whole Activity resolves the values-night/ (DARK) or values/ (LIGHT) resources.
 *
 * Default is [Choice.SYSTEM] = "Theo xe" (follow the system/car dark/light) — in which case [wrap]
 * returns the base Context UNCHANGED (pure pass-through, zero behavior change vs. before this file).
 *
 * Stage 1 (foundation) wires persistence + [wrap]; the selector UI is added in Stage 2.
 */
object ThemeMode {
    /** Own SharedPreferences file so the theme choice is isolated from feature prefs. */
    private const val PREFS = "clusternav_theme"
    private const val KEY_CHOICE = "theme_choice"

    /** User theme selection. [code] is the stable persisted token (never localize/rename it). */
    enum class Choice(val code: String) {
        /** Follow the system/car uiMode ("Theo xe"). Default. */
        SYSTEM("system"),
        /** Force LIGHT (day) resources regardless of system. */
        LIGHT("light"),
        /** Force DARK (night) resources regardless of system. */
        DARK("dark");

        companion object {
            /** Parse a persisted code back to a [Choice]; unknown/null → [SYSTEM]. */
            fun fromCode(code: String?): Choice =
                values().firstOrNull { it.code == code } ?: SYSTEM
        }
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The persisted theme choice (default [Choice.SYSTEM]). */
    fun choice(ctx: Context): Choice =
        Choice.fromCode(prefs(ctx).getString(KEY_CHOICE, Choice.SYSTEM.code))

    /** Persist a new theme choice. Caller is responsible for recreating any visible Activity. */
    fun setChoice(ctx: Context, choice: Choice) {
        prefs(ctx).edit().putString(KEY_CHOICE, choice.code).apply()
    }

    /**
     * Wrap [base] so the returned Context resolves LIGHT or DARK resources per the persisted [choice].
     *
     * - [Choice.SYSTEM] → returns [base] unchanged (follow system; no override).
     * - [Choice.LIGHT]/[Choice.DARK] → returns a configuration Context whose uiMode night bit is forced,
     *   preserving every other configuration bit (density, locale, screen size, orientation, …).
     *
     * Call from `attachBaseContext(newBase)` as `super.attachBaseContext(ThemeMode.wrap(newBase))`.
     */
    fun wrap(base: Context): Context {
        val nightBits = when (choice(base)) {
            Choice.SYSTEM -> return base
            Choice.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            Choice.DARK -> Configuration.UI_MODE_NIGHT_YES
        }
        val overrideConfig = Configuration(base.resources.configuration)
        // Replace ONLY the UI_MODE_NIGHT_* bits; keep the rest of uiMode (e.g. car/normal type) intact.
        overrideConfig.uiMode =
            (overrideConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        return base.createConfigurationContext(overrideConfig)
    }
}
