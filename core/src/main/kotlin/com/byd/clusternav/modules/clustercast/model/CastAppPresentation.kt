package com.byd.clusternav.modules.clustercast.model

/**
 * Pure presentation policy for installed applications. It owns ordering, search, icon fallback and
 * the exact action export per protection class, so Activity, App Manager and Bubble share one model
 * and no surface invents its own policy. Nothing here observes or mutates Cast runtime state.
 */
enum class AppProtectionSource { NORMAL, SYSTEM_PROJECTION, USER_KEEP_SESSION, UNKNOWN_PROTECTED }

enum class AppIconState { LOADED, FALLBACK }

enum class AppRowAction {
    OPEN_DETAILS, ADD_FAVORITE, REMOVE_FAVORITE, CAST,
    SET_DEFAULT, REPLACE_DEFAULT, CLEAR_DEFAULT,
    ADD_USER_PROTECTION, REMOVE_USER_PROTECTION,
}

enum class AppUnavailableReason { NOT_INSTALLED, NO_LAUNCHER, PROTECTION_UNKNOWN }

data class CastAppRow(
    val label: String,
    val packageName: String,
    val favorite: Boolean,
    val isDefault: Boolean,
    val protection: AppProtectionSource,
    val iconState: AppIconState,
    val unavailableReason: AppUnavailableReason? = null,
) {
    val available: Boolean get() = unavailableReason == null

    /** Locked classes are shown but can never be unchecked or downgraded by a preference. */
    val protectionLocked: Boolean get() = protection == AppProtectionSource.SYSTEM_PROJECTION ||
        protection == AppProtectionSource.UNKNOWN_PROTECTED
}

object CastAppPresentation {

    fun protectionOf(targetClass: TargetClass): AppProtectionSource = when (targetClass) {
        TargetClass.NORMAL -> AppProtectionSource.NORMAL
        TargetClass.PROJECTION_SINK -> AppProtectionSource.SYSTEM_PROJECTION
        TargetClass.KEEP_SESSION -> AppProtectionSource.USER_KEEP_SESSION
        TargetClass.UNKNOWN_PROTECTED -> AppProtectionSource.UNKNOWN_PROTECTED
    }

    /**
     * Deterministic order: favorites first, then case-insensitive label, then package name. Icon
     * failure only degrades [CastAppRow.iconState]; it never removes or reorders an eligible app.
     */
    fun order(rows: List<CastAppRow>): List<CastAppRow> = rows.sortedWith(
        compareByDescending<CastAppRow> { it.favorite }
            .thenBy { it.label.lowercase() }
            .thenBy { it.packageName },
    )

    /** Blank or whitespace query keeps the full deterministic list. Match is label or package. */
    fun search(rows: List<CastAppRow>, query: String?): List<CastAppRow> {
        val needle = query?.trim()?.lowercase().orEmpty()
        val ordered = order(rows)
        if (needle.isEmpty()) return ordered
        return ordered.filter {
            it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
        }
    }

    fun noResult(rows: List<CastAppRow>, query: String?): Boolean = search(rows, query).isEmpty()

    /**
     * Builds the App Manager list, appending a visibly unavailable row when the durable default is
     * no longer installed or launchable. A stale default is never silently substituted or dropped.
     */
    fun rows(
        entries: List<CastAppRow>,
        defaultPackage: String?,
        staleDefaultLabel: String? = null,
    ): List<CastAppRow> {
        val marked = entries.map { it.copy(isDefault = defaultPackage != null && it.packageName == defaultPackage) }
        val known = marked.any { it.isDefault }
        val ordered = order(marked)
        if (defaultPackage == null || known) return ordered
        return listOf(
            CastAppRow(
                label = staleDefaultLabel ?: defaultPackage,
                packageName = defaultPackage,
                favorite = false,
                isDefault = true,
                protection = AppProtectionSource.UNKNOWN_PROTECTED,
                iconState = AppIconState.FALLBACK,
                unavailableReason = AppUnavailableReason.NOT_INSTALLED,
            ),
        ) + ordered
    }

    /**
     * Exact exported actions. `castExported` comes from the canonical Cast UI contract; presentation
     * may only narrow it. UNKNOWN_PROTECTED exports no Cast or destructive action, and a stale
     * default keeps only the controls that repair it.
     */
    fun actions(
        row: CastAppRow,
        castExported: Boolean,
        hasOtherDefault: Boolean = false,
    ): Set<AppRowAction> {
        val actions = LinkedHashSet<AppRowAction>()
        actions += AppRowAction.OPEN_DETAILS
        if (!row.available) {
            if (row.isDefault) actions += AppRowAction.CLEAR_DEFAULT
            return actions
        }
        actions += if (row.favorite) AppRowAction.REMOVE_FAVORITE else AppRowAction.ADD_FAVORITE
        if (row.protection == AppProtectionSource.UNKNOWN_PROTECTED) return actions
        if (castExported) actions += AppRowAction.CAST
        when {
            row.isDefault -> actions += AppRowAction.CLEAR_DEFAULT
            hasOtherDefault -> actions += AppRowAction.REPLACE_DEFAULT
            else -> actions += AppRowAction.SET_DEFAULT
        }
        when (row.protection) {
            AppProtectionSource.NORMAL -> actions += AppRowAction.ADD_USER_PROTECTION
            AppProtectionSource.USER_KEEP_SESSION -> actions += AppRowAction.REMOVE_USER_PROTECTION
            else -> Unit
        }
        return actions
    }

    /** A package may become the durable default only with fresh eligible, non-unknown evidence. */
    fun defaultEligible(row: CastAppRow): Boolean =
        row.available && row.protection != AppProtectionSource.UNKNOWN_PROTECTED

    /** Local preselection only: an explicit saved choice always wins over the durable default. */
    fun preselect(savedPackage: String?, defaultPackage: String?, rows: List<CastAppRow>): String? {
        val eligible = rows.filter { defaultEligible(it) }.map { it.packageName }.toSet()
        savedPackage?.let { if (it in eligible) return it }
        defaultPackage?.let { if (it in eligible) return it }
        return null
    }
}
