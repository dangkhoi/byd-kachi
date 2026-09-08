package com.byd.clusternav.cast.platform

import com.byd.clusternav.modules.clustercast.AppScale
import com.byd.clusternav.modules.clustercast.model.*

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.util.Collections

/** One bounded immutable presentation entry. Icon failure degrades only [iconState]. */
data class CastAppEntry(
    val label: String,
    val packageName: String,
    val favorite: Boolean,
    val protectedSession: Boolean = false,
    val protection: AppProtectionSource = AppProtectionSource.NORMAL,
    val icon: Drawable? = null,
    val iconState: AppIconState = AppIconState.FALLBACK,
    val isDefault: Boolean = false,
) {
    fun row(): CastAppRow = CastAppRow(label, packageName, favorite, isDefault, protection, iconState)
}

/** Framework icon/label access, isolated so pure tests can inject success, null and throw. */
interface CastAppIconSource {
    fun load(packageName: String): Drawable?
}

class CastAppCatalog(
    context: Context,
    private val phoneSession: (String) -> Boolean? = { null },
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("cast-v2-app-catalog", Context.MODE_PRIVATE)
    private val icons: CastAppIconSource = object : CastAppIconSource {
        override fun load(packageName: String): Drawable? =
            app.packageManager.getApplicationIcon(packageName)
    }

    init { migrateLegacyOnce() }

    /**
     * Off-main launcher enumeration. Label and icon come from the framework; any icon failure keeps
     * the app listed with a deterministic fallback instead of removing or disabling it.
     */
    fun installed(defaultPackage: String? = null): List<CastAppEntry> {
        val favorites = favorites()
        val protectedPackages = protectedPackages()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { app.packageManager.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
        val entries = resolved.mapNotNull { info ->
            runCatching {
                val packageName = info.activityInfo.packageName
                val icon = runCatching { icons.load(packageName) }.getOrNull()
                CastAppEntry(
                    label = runCatching { info.loadLabel(app.packageManager).toString() }
                        .getOrNull()?.takeIf { it.isNotBlank() } ?: packageName,
                    packageName = packageName,
                    favorite = packageName in favorites,
                    protectedSession = packageName in protectedPackages,
                    protection = CastPolicy.classify(evidence(packageName, phoneSession(packageName)))
                        .let(CastAppPresentation::protectionOf),
                    icon = icon,
                    iconState = if (icon == null) AppIconState.FALLBACK else AppIconState.LOADED,
                    isDefault = defaultPackage != null && packageName == defaultPackage,
                )
            }.getOrNull()
        }.distinctBy { it.packageName }
        val ordered = CastAppPresentation.order(entries.map { it.row() })
            .mapNotNull { row -> entries.firstOrNull { it.packageName == row.packageName } }
        return Collections.unmodifiableList(ordered)
    }

    fun evidence(packageName: String, connectedPhoneSession: Boolean? = null): TargetEvidence = TargetEvidence(
        projectionComponent = PROJECTION_HINTS.any { packageName.lowercase().contains(it) },
        connectedPhoneSession = connectedPhoneSession,
        userProtected = packageName in protectedPackages(),
    )

    fun snapshot(packageName: String, connectedPhoneSession: Boolean?): CastManualTargetSnapshot =
        CastManualTargetSnapshot(
            evidence(packageName, connectedPhoneSession),
            installed = runCatching { app.packageManager.getPackageInfo(packageName, 0) }.isSuccess,
            hasLauncher = app.packageManager.getLaunchIntentForPackage(packageName) != null,
        )

    fun favorites(): Set<String> = immutableCsv("favorites")
    fun protectedPackages(): Set<String> = immutableCsv("protected")

    fun setFavorite(packageName: String, enabled: Boolean) {
        require(PACKAGE.matches(packageName))
        val next = favorites().toMutableSet().apply { if (enabled) add(packageName) else remove(packageName) }
        check(prefs.edit().putString("favorites", next.sorted().joinToString(",")).commit())
    }

    /**
     * User KEEP_SESSION is a preference over freshly proven NORMAL apps only. It can never remove or
     * downgrade system-projection or unknown protection, which stay locked from planner evidence.
     */
    fun setProtected(packageName: String, enabled: Boolean): Boolean {
        require(PACKAGE.matches(packageName))
        val current = CastPolicy.classify(evidence(packageName, phoneSession(packageName)))
        if (current != TargetClass.NORMAL && current != TargetClass.KEEP_SESSION) {
            // Protection for this class is locked by planner evidence; a preference cannot downgrade it.
            return false
        }
        val next = protectedPackages().toMutableSet().apply {
            if (enabled) add(packageName) else remove(packageName)
        }
        check(prefs.edit().putString("protected", next.sorted().joinToString(",")).commit())
        return true
    }

    /**
     * Khung + DPI riêng cho từng app, đúng mô hình v0.3x mà chủ dự án dùng thật: tick app nào thì chỉnh
     * kích thước/vị trí ngay dưới app đó, và giá trị theo app chứ không theo phiên.
     *
     * V2 trước đây chỉ giữ geometry của PHIÊN đang chiếu, nên chỉnh cho app A rồi chiếu app B là mất. Lưu
     * theo app thì lần chiếu sau vẫn đúng khung đã chỉnh — đó là điều làm nó dùng được thật.
     */
    fun scaleOf(packageName: String): AppScale {
        val dpi = prefs.getInt("scale-dpi:$packageName", AppScale().dpi)
        return AppScale(
            dpi = dpi,
            rectL = prefs.getInt("scale-l:$packageName", -1),
            rectT = prefs.getInt("scale-t:$packageName", -1),
            rectR = prefs.getInt("scale-r:$packageName", -1),
            rectB = prefs.getInt("scale-b:$packageName", -1),
        )
    }

    fun setScale(packageName: String, scale: AppScale) {
        require(PACKAGE.matches(packageName))
        check(
            prefs.edit()
                .putInt("scale-dpi:$packageName", scale.dpi)
                .putInt("scale-l:$packageName", scale.rectL)
                .putInt("scale-t:$packageName", scale.rectT)
                .putInt("scale-r:$packageName", scale.rectR)
                .putInt("scale-b:$packageName", scale.rectB)
                .commit(),
        )
    }

    /**
     * Per-app cluster density. V1 proved one global DPI is wrong: text sized for a map is unreadable
     * for a dashboard. Absent means "leave the display density as the profile created it".
     */
    fun clusterDensityDpi(packageName: String): Int? =
        prefs.getInt("dpi:$packageName", 0).takeIf { it in 80..640 }

    fun setClusterDensityDpi(packageName: String, densityDpi: Int?) {
        require(PACKAGE.matches(packageName))
        require(densityDpi == null || densityDpi in 80..640) { "cluster density must be 80..640" }
        val editor = prefs.edit()
        if (densityDpi == null) editor.remove("dpi:$packageName") else editor.putInt("dpi:$packageName", densityDpi)
        check(editor.commit())
    }

    /**
     * Cluster style per app. V1 had this as one global flag and every app inherited the previous
     * app's shape; the choice belongs to the app that will occupy the cluster.
     */
    fun clusterStyle(packageName: String): ClusterStyle =
        if (packageName in immutableCsv("rectStyle")) ClusterStyle.RECT else ClusterStyle.CURVED

    fun setClusterStyle(packageName: String, style: ClusterStyle) {
        require(PACKAGE.matches(packageName))
        val next = immutableCsv("rectStyle").toMutableSet().apply {
            if (style == ClusterStyle.RECT) add(packageName) else remove(packageName)
        }
        check(prefs.edit().putString("rectStyle", next.sorted().joinToString(",")).commit())
    }

    /** Bubble presentation opt-in. Absent or fresh means disabled; only explicit values migrate. */
    fun bubbleEnabled(): Boolean = prefs.getBoolean("bubbleEnabled", false)

    fun setBubbleEnabled(enabled: Boolean) {
        check(prefs.edit().putBoolean("bubbleEnabled", enabled).commit())
    }

    fun bubblePosition(): Pair<Int, Int>? {
        val x = prefs.getInt("bubbleX", Int.MIN_VALUE)
        val y = prefs.getInt("bubbleY", Int.MIN_VALUE)
        return if (x == Int.MIN_VALUE || y == Int.MIN_VALUE) null else x to y
    }

    fun setBubblePosition(x: Int, y: Int) {
        check(prefs.edit().putInt("bubbleX", x).putInt("bubbleY", y).commit())
    }

    /**
     * Legacy auto-cast package, exposed for one-time migration into the envelope default only. It
     * never carries consent, so migration cannot silently authorize lifecycle mutation.
     */
    fun legacyDefaultCandidate(): String? = prefs.getString("legacyDefaultCandidate", null)
        ?.takeIf { PACKAGE.matches(it) }

    fun clearLegacyDefaultCandidate() {
        check(prefs.edit().remove("legacyDefaultCandidate").commit())
    }

    private fun migrateLegacyOnce() {
        val from = prefs.getInt("migrationVersion", 0)
        if (from >= MIGRATION_VERSION) return
        val legacy = app.getSharedPreferences("clustercast", Context.MODE_PRIVATE)
        val legacyDefault = legacy.getString("autoCast", null)?.takeIf { PACKAGE.matches(it) }
        val editor = prefs.edit().putInt("migrationVersion", MIGRATION_VERSION)
        if (from == 0) {
            // Only a first migration may seed catalog preferences from legacy values.
            editor.putString("favorites", csv(legacy.getString("castable", null)).sorted().joinToString(","))
            editor.putString("protected", csv(legacy.getString("keepSession", null)).sorted().joinToString(","))
        }
        if (!prefs.contains("bubbleEnabled")) {
            editor.putBoolean(
                "bubbleEnabled",
                if (legacy.contains("bubble_auto")) legacy.getBoolean("bubble_auto", false) else false,
            )
        }
        if (legacyDefault != null) editor.putString("legacyDefaultCandidate", legacyDefault)
        check(editor.commit())
    }

    private fun immutableCsv(key: String): Set<String> =
        Collections.unmodifiableSet(LinkedHashSet(csv(prefs.getString(key, null))))

    private fun csv(value: String?): Set<String> = value.orEmpty().split(',')
        .map(String::trim).filter(PACKAGE::matches).toSet()

    companion object {
        const val MIGRATION_VERSION = 2
        private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        private val PROJECTION_HINTS = listOf("projection.sink", "aapactivity", "aapvideo", "carplay", "androidauto")
    }
}
