package com.byd.clusternav.modules.clustercast.simplified

import android.content.Context
import android.content.SharedPreferences
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastShell
import com.byd.clusternav.modules.clustercast.simplified.ShellResult
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastPrefs
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfig
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.ProjectionManager
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfigurator
import com.byd.clusternav.modules.clustercast.simplified.AppMover
import dadb.Dadb

/**
 * Android-side runtime for the simplified Cluster Cast coordinator.
 *
 * Bridges the pure-Kotlin core coordinator to the Android platform:
 * - Shell execution via dadb (localhost:5555, same as V2)
 * - Preferences via SharedPreferences
 * - Lifecycle tied to app process
 *
 * Process-singleton: all activities/services share one instance.
 */
object SimpleCastRuntime {

    @Volatile private var instance: SimpleCastCoordinator? = null

    /** Get or create the process-wide coordinator. Thread-safe. */
    fun coordinator(context: Context): SimpleCastCoordinator {
        return instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    private fun create(app: Context): SimpleCastCoordinator {
        android.util.Log.i("SimpleCast", "Creating SimpleCastCoordinator")
        val shell = DadbSimpleCastShell(app)
        val prefs = SharedPrefsSimpleCastPrefs(app)
        val projection = ProjectionManager(shell)
        val configurator = DisplayConfigurator(shell)
        val mover = AppMover(
            shell = shell,
            log = { message -> android.util.Log.i("SimpleCast", "AppMover: $message") },
        )
        // Display ID 1 is the cluster on BYD DiLink3 (measured on vehicle).
        // A saved ID remains authoritative; do not guess from display dimensions.
        val savedDisplayId = prefs.lastDisplayId()
        val displayId = savedDisplayId ?: 1
        val displaySource = if (savedDisplayId != null) "saved" else "measured default"
        android.util.Log.i("SimpleCast", "Cluster display = $displayId (source=$displaySource)")
        return SimpleCastCoordinator(projection, configurator, mover, prefs, shell, displayId, selfPackage = com.byd.clusternav.BuildConfig.APPLICATION_ID)
    }

    /** Shutdown the coordinator. Call from Application.onTerminate or process exit. */
    fun shutdown() {
        instance?.shutdown()
        instance = null
    }
}

/**
 * SimpleCastShell implementation using dadb localhost connection.
 *
 * Each shell command opens a fresh dadb session (same pattern as CastAdbGateway).
 * Connection timeout: 2s. Command timeout: 10s.
 */
private class DadbSimpleCastShell(private val app: Context) : SimpleCastShell {

    override fun execute(command: String): ShellResult {
        return try {
            android.util.Log.i("SimpleCast", "shell: $command")
            val keyPair = AdbKeys.ensure(app)
            Dadb.create("localhost", 5555, keyPair).use { adb ->
                val result = adb.shell(command)
                val shellResult = ShellResult(
                    exitCode = result.exitCode,
                    stdout = result.output,
                    stderr = result.errorOutput,
                )
                if (isPlacementEvidenceCommand(command)) {
                    logPlacementEvidence(command, shellResult)
                } else {
                    android.util.Log.i("SimpleCast", "shell OK: exit=${result.exitCode}")
                }
                shellResult
            }
        } catch (e: Exception) {
            val shellResult = ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: e.javaClass.simpleName,
            )
            if (isPlacementEvidenceCommand(command)) {
                logPlacementEvidence(command, shellResult)
            }
            android.util.Log.e("SimpleCast", "shell FAIL: command=$command error=${e.message}", e)
            shellResult
        }
    }

    private fun logPlacementEvidence(command: String, result: ShellResult) {
        val evidenceId = EVIDENCE_SEQUENCE.incrementAndGet()
        val payload = "command=${encodeLogValue(command)} exit=${result.exitCode} " +
            "stdout=${encodeLogValue(result.stdout)} stderr=${encodeLogValue(result.stderr)}"
        val chunks = payload.chunked(EVIDENCE_CHUNK_CHARS)
        android.util.Log.i(
            "SimpleCast",
            "shell evidence id=$evidenceId: chars=${payload.length} " +
                "utf8Bytes=${payload.toByteArray(Charsets.UTF_8).size} chunks=${chunks.size}",
        )
        chunks.forEachIndexed { index, chunk ->
            android.util.Log.i(
                "SimpleCast",
                "shell evidence id=$evidenceId ${index + 1}/${chunks.size}: $chunk",
            )
        }
    }

    private fun isPlacementEvidenceCommand(command: String): Boolean =
        command.startsWith("am start ") ||
            command.startsWith("am task resize ") ||
            command.startsWith("wm size -d ")

    private fun encodeLogValue(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20 || character.code > 0x7e) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }

    private companion object {
        // 768 ASCII code units plus the record prefix remain below Android's log-entry limit.
        const val EVIDENCE_CHUNK_CHARS = 768
        val EVIDENCE_SEQUENCE = java.util.concurrent.atomic.AtomicLong()
    }
}

/**
 * SharedPreferences-based implementation of SimpleCastPrefs.
 */
private class SharedPrefsSimpleCastPrefs(context: Context) : SimpleCastPrefs {
    private val sp: SharedPreferences =
        context.getSharedPreferences("simple_cast_prefs", Context.MODE_PRIVATE)

    override fun displayConfigFor(pkg: String): DisplayConfig? = displayConfigFor(pkg, CastProfile.FULL)

    override fun displayConfigFor(pkg: String, profile: CastProfile): DisplayConfig? {
        val key = profileKey(pkg, profile)
        val size = sp.getString("config_size_$key", null) ?: return null
        val overscan = sp.getString("config_overscan_$key", "0,0,0,0") ?: "0,0,0,0"
        val density = sp.getString("config_density_$key", "reset") ?: "reset"
        val boundsStr = sp.getString("config_bounds_$key", null)
        val bounds = boundsStr?.split(",")?.takeIf { it.size == 4 }?.let {
            CastBounds(it[0].toInt(), it[1].toInt(), it[2].toInt(), it[3].toInt())
        }
        return DisplayConfig(wmSize = size, overscan = overscan, density = density, bounds = bounds)
    }

    override fun saveDisplayConfig(pkg: String, config: DisplayConfig) =
        saveDisplayConfig(pkg, CastProfile.FULL, config)

    override fun saveDisplayConfig(pkg: String, profile: CastProfile, config: DisplayConfig) {
        val key = profileKey(pkg, profile)
        sp.edit()
            .putString("config_size_$key", config.wmSize)
            .putString("config_overscan_$key", config.overscan)
            .putString("config_density_$key", config.density)
            .putString("config_bounds_$key", config.bounds?.toString())
            .apply()
    }

    /**
     * Prefs key namespace for a profile. [CastProfile.FULL] keeps the legacy no-suffix key
     * (`config_<field>_<pkg>`) so previously-saved full configs survive; every other profile
     * appends `__<profile.key>` (e.g. `config_size_<pkg>__L30`) — distinct, non-colliding, and
     * byte-identical to the predecessor's keys for the {50,30,70} set (R3/R4/R8 backward compat).
     */
    private fun profileKey(pkg: String, profile: CastProfile): String =
        if (profile.isFull) pkg else "${pkg}__${profile.key}"

    override fun lastDisplayId(): Int? {
        val v = sp.getInt("last_display_id", -1)
        return if (v >= 0) v else null
    }

    override fun saveLastDisplayId(id: Int) {
        sp.edit().putInt("last_display_id", id).apply()
    }

    // Autostart
    override fun autoStartPackage(): String? = sp.getString("autostart_package", null)

    override fun setAutoStartPackage(pkg: String?) {
        sp.edit().putString("autostart_package", pkg).apply()
    }

    override fun autoStartEnabled(): Boolean = sp.getBoolean("autostart_enabled", false)

    override fun setAutoStartEnabled(enabled: Boolean) {
        sp.edit().putBoolean("autostart_enabled", enabled).apply()
    }

    // Split ratio
    override fun splitRatioLeftPercent(): Int = sp.getInt("split_ratio_left_pct", 50)

    override fun setSplitRatioLeftPercent(pct: Int) {
        sp.edit().putInt("split_ratio_left_pct", pct).apply()
    }

    // One-time setup flags
    override fun dozeWhitelistApplied(): Boolean = sp.getBoolean("doze_whitelist_applied", false)

    override fun setDozeWhitelistApplied(applied: Boolean) {
        sp.edit().putBoolean("doze_whitelist_applied", applied).apply()
    }

    // Autostart split
    override fun autoStartLeftPackage(): String? = sp.getString("autostart_left_package", null)

    override fun setAutoStartLeftPackage(pkg: String?) {
        sp.edit().putString("autostart_left_package", pkg).apply()
    }

    override fun autoStartRightPackage(): String? = sp.getString("autostart_right_package", null)

    override fun setAutoStartRightPackage(pkg: String?) {
        sp.edit().putString("autostart_right_package", pkg).apply()
    }

    override fun autoStartSplitEnabled(): Boolean = sp.getBoolean("autostart_split_enabled", false)

    override fun setAutoStartSplitEnabled(enabled: Boolean) {
        sp.edit().putBoolean("autostart_split_enabled", enabled).apply()
    }

    // Master enable — default FALSE (owner 2026-08-11): nav-only is the common case, so a fresh
    // install keeps the cluster native (no projection/curve/black surface) and navigation shows on
    // the cluster immediately. Users who want to cast apps turn Cast on explicitly (persisted).
    override fun castEnabled(): Boolean = sp.getBoolean("cast_enabled", false)

    override fun setCastEnabled(enabled: Boolean) {
        sp.edit().putBoolean("cast_enabled", enabled).apply()
    }
}
