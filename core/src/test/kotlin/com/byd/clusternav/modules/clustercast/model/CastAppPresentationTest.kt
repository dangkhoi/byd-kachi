package com.byd.clusternav.modules.clustercast.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure presentation contract shared by Activity, App Manager and Bubble. */
class CastAppPresentationTest {

    private fun row(
        label: String,
        pkg: String,
        favorite: Boolean = false,
        protection: AppProtectionSource = AppProtectionSource.NORMAL,
        icon: AppIconState = AppIconState.LOADED,
        unavailable: AppUnavailableReason? = null,
    ) = CastAppRow(label, pkg, favorite, false, protection, icon, unavailable)

    private val maps = row("Google Maps", "com.example.maps")
    private val vietmap = row("VietMap", "com.example.vietmap", favorite = true)
    private val auto = row("Android Auto", "com.example.androidauto", protection = AppProtectionSource.SYSTEM_PROJECTION)
    private val unknown = row("Mystery", "com.example.mystery", protection = AppProtectionSource.UNKNOWN_PROTECTED)
    private val keep = row("Kept", "com.example.kept", protection = AppProtectionSource.USER_KEEP_SESSION)
    private val all = listOf(maps, unknown, auto, vietmap, keep)

    @Test
    fun `every planner class maps to exactly one presentation source`() {
        assertEquals(AppProtectionSource.NORMAL, CastAppPresentation.protectionOf(TargetClass.NORMAL))
        assertEquals(AppProtectionSource.SYSTEM_PROJECTION, CastAppPresentation.protectionOf(TargetClass.PROJECTION_SINK))
        assertEquals(AppProtectionSource.USER_KEEP_SESSION, CastAppPresentation.protectionOf(TargetClass.KEEP_SESSION))
        assertEquals(AppProtectionSource.UNKNOWN_PROTECTED, CastAppPresentation.protectionOf(TargetClass.UNKNOWN_PROTECTED))
        assertEquals(TargetClass.values().size, AppProtectionSource.values().size)
    }

    @Test
    fun `favorites lead then case-insensitive label then package`() {
        val ordered = CastAppPresentation.order(all).map { it.packageName }
        assertEquals("com.example.vietmap", ordered.first())
        assertEquals(
            listOf("com.example.androidauto", "com.example.maps", "com.example.kept", "com.example.mystery"),
            ordered.drop(1),
        )
    }

    @Test
    fun `ordering is stable for identical labels and unaffected by icon failure`() {
        val a = row("Same", "com.example.b", icon = AppIconState.FALLBACK)
        val b = row("same", "com.example.a")
        assertEquals(
            listOf("com.example.a", "com.example.b"),
            CastAppPresentation.order(listOf(a, b)).map { it.packageName },
        )
        assertEquals(2, CastAppPresentation.order(listOf(a, b)).size)
    }

    @Test
    fun `blank query keeps the full deterministic list`() {
        listOf(null, "", "   ").forEach { query ->
            assertEquals(CastAppPresentation.order(all), CastAppPresentation.search(all, query))
            assertFalse(CastAppPresentation.noResult(all, query))
        }
    }

    @Test
    fun `search matches label or package case-insensitively`() {
        assertEquals(listOf("com.example.maps"), CastAppPresentation.search(all, "GOOGLE").map { it.packageName })
        assertEquals(listOf("com.example.maps"), CastAppPresentation.search(all, "maps").map { it.packageName })
        assertEquals(listOf("com.example.vietmap"), CastAppPresentation.search(all, "vietMAP").map { it.packageName })
        assertEquals(5, CastAppPresentation.search(all, "com.example").size)
    }

    @Test
    fun `no result state is explicit and does not mutate the source list`() {
        assertTrue(CastAppPresentation.noResult(all, "zzz"))
        assertEquals(emptyList<CastAppRow>(), CastAppPresentation.search(all, "zzz"))
        assertEquals(5, all.size)
    }

    @Test
    fun `default marking is derived and unique`() {
        val rows = CastAppPresentation.rows(all, "com.example.maps")
        assertEquals(1, rows.count { it.isDefault })
        assertEquals("com.example.maps", rows.single { it.isDefault }.packageName)
        assertEquals(5, rows.size)
    }

    @Test
    fun `absent default marks nothing`() {
        assertEquals(0, CastAppPresentation.rows(all, null).count { it.isDefault })
    }

    @Test
    fun `stale default stays visible as unavailable and is never substituted`() {
        val rows = CastAppPresentation.rows(all, "com.example.removed", staleDefaultLabel = "Removed app")
        val stale = rows.first()
        assertEquals("com.example.removed", stale.packageName)
        assertTrue(stale.isDefault)
        assertFalse(stale.available)
        assertEquals(AppUnavailableReason.NOT_INSTALLED, stale.unavailableReason)
        assertEquals(AppIconState.FALLBACK, stale.iconState)
        assertEquals(6, rows.size)
        assertEquals(1, rows.count { it.isDefault })
    }

    @Test
    fun `stale default row exports only details and clear`() {
        val stale = CastAppPresentation.rows(all, "com.example.removed").first()
        assertEquals(
            setOf(AppRowAction.OPEN_DETAILS, AppRowAction.CLEAR_DEFAULT),
            CastAppPresentation.actions(stale, castExported = true),
        )
        assertFalse(CastAppPresentation.defaultEligible(stale))
    }

    @Test
    fun `normal row exports favorite cast default and user protection`() {
        assertEquals(
            setOf(
                AppRowAction.OPEN_DETAILS, AppRowAction.ADD_FAVORITE, AppRowAction.CAST,
                AppRowAction.SET_DEFAULT, AppRowAction.ADD_USER_PROTECTION,
            ),
            CastAppPresentation.actions(maps, castExported = true),
        )
    }

    @Test
    fun `cast is exported only when the canonical contract allows it`() {
        val actions = CastAppPresentation.actions(maps, castExported = false)
        assertFalse(AppRowAction.CAST in actions)
        assertTrue(AppRowAction.SET_DEFAULT in actions)
    }

    @Test
    fun `replace default appears only when another default exists`() {
        val actions = CastAppPresentation.actions(maps, castExported = true, hasOtherDefault = true)
        assertTrue(AppRowAction.REPLACE_DEFAULT in actions)
        assertFalse(AppRowAction.SET_DEFAULT in actions)
        val current = maps.copy(isDefault = true)
        val currentActions = CastAppPresentation.actions(current, castExported = true, hasOtherDefault = true)
        assertTrue(AppRowAction.CLEAR_DEFAULT in currentActions)
        assertFalse(AppRowAction.REPLACE_DEFAULT in currentActions)
    }

    @Test
    fun `unknown protected exports no cast default or protection action`() {
        val actions = CastAppPresentation.actions(unknown, castExported = true)
        assertEquals(setOf(AppRowAction.OPEN_DETAILS, AppRowAction.ADD_FAVORITE), actions)
        assertTrue(unknown.protectionLocked)
        assertFalse(CastAppPresentation.defaultEligible(unknown))
    }

    @Test
    fun `system projection is locked but remains castable and default eligible`() {
        val actions = CastAppPresentation.actions(auto, castExported = true)
        assertTrue(AppRowAction.CAST in actions)
        assertTrue(AppRowAction.SET_DEFAULT in actions)
        assertFalse(AppRowAction.ADD_USER_PROTECTION in actions)
        assertFalse(AppRowAction.REMOVE_USER_PROTECTION in actions)
        assertTrue(auto.protectionLocked)
        assertTrue(CastAppPresentation.defaultEligible(auto))
    }

    @Test
    fun `user keep session may be removed but never a locked class`() {
        assertTrue(AppRowAction.REMOVE_USER_PROTECTION in CastAppPresentation.actions(keep, castExported = true))
        assertFalse(keep.protectionLocked)
        assertFalse(AppRowAction.ADD_USER_PROTECTION in CastAppPresentation.actions(keep, castExported = true))
    }

    @Test
    fun `favorite toggle is a distinct control from cast and default`() {
        val favorited = CastAppPresentation.actions(vietmap, castExported = true)
        assertTrue(AppRowAction.REMOVE_FAVORITE in favorited)
        assertFalse(AppRowAction.ADD_FAVORITE in favorited)
        assertTrue(AppRowAction.OPEN_DETAILS in favorited)
    }

    @Test
    fun `every row always exports details only navigation`() {
        all.forEach { row ->
            assertTrue(AppRowAction.OPEN_DETAILS in CastAppPresentation.actions(row, castExported = false))
        }
    }

    /**
     * Locks the full pipeline end to end, not just [CastPolicy.classify] in isolation: evidence ->
     * classify() -> protectionOf() -> actions()/defaultEligible(). Every other test in this file
     * builds a [CastAppRow] with a hand-picked `protection` value, so none of them ever exercised the
     * real-world CarPlay/Android Auto reading — `projectionComponent=true`,
     * `connectedPhoneSession=null`, because `dumpsys` can never report a phone-session field for the
     * host surface's own dump. Before the 2026-07-28 fix that null reading classified as
     * UNKNOWN_PROTECTED, which `unknown protected exports no cast default or protection action` above
     * proves exports no CAST/SET_DEFAULT — i.e. CarPlay/Android Auto could never be cast in any
     * vehicle state. This test fails if that pipeline is ever silently narrowed again.
     */
    @Test
    fun `carplay or android auto with unmeasurable phone session becomes castable and default eligible`() {
        val evidence = TargetEvidence(projectionComponent = true, connectedPhoneSession = null, userProtected = false)
        val protection = CastAppPresentation.protectionOf(CastPolicy.classify(evidence))
        val carplay = row("Apple CarPlay", "com.example.carplay", protection = protection)
        val actions = CastAppPresentation.actions(carplay, castExported = true)
        assertTrue(AppRowAction.CAST in actions)
        assertTrue(AppRowAction.SET_DEFAULT in actions)
        assertTrue(carplay.protectionLocked)
        assertTrue(CastAppPresentation.defaultEligible(carplay))
    }

    @Test
    fun `saved selection beats the durable default and both must stay eligible`() {
        val rows = CastAppPresentation.rows(all, "com.example.maps")
        assertEquals("com.example.vietmap", CastAppPresentation.preselect("com.example.vietmap", "com.example.maps", rows))
        assertEquals("com.example.maps", CastAppPresentation.preselect(null, "com.example.maps", rows))
        assertEquals("com.example.maps", CastAppPresentation.preselect("com.example.gone", "com.example.maps", rows))
        assertNull(CastAppPresentation.preselect(null, "com.example.mystery", rows))
        assertNull(CastAppPresentation.preselect(null, null, rows))
        assertNull(CastAppPresentation.preselect(null, "com.example.removed", CastAppPresentation.rows(all, "com.example.removed")))
    }
}
