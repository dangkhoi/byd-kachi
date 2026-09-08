package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R2 (#3, docs/specs/cast-nav-ux-release-v104.html): launchers/home screens must never be cast.
 *
 * [AppMover.isLauncher] is the pure half of the guard; the app layer UNIONs it with the runtime
 * CATEGORY_HOME list. This test pins the string contract so a rename can't silently let the Dudu
 * home through.
 */
class AppMoverLauncherExclusionTest {

    @Test
    fun `launcher packages are excluded`() {
        assertTrue(AppMover.isLauncher("com.android.launcher3"), "AOSP launcher3")
        assertTrue(AppMover.isLauncher("com.android.launcher"), "AOSP launcher")
        assertTrue(AppMover.isLauncher("com.teslacoilsw.launcher"), "Nova launcher")
    }

    @Test
    fun `dudu home packages are excluded`() {
        assertTrue(AppMover.isLauncher("com.byd.dudu"), "Dudu home root id")
        assertTrue(AppMover.isLauncher("com.byd.dudu.launcher"), "Dudu launcher id")
        assertTrue(AppMover.isLauncher("com.byd.duduassistant"), "any id containing dudu")
    }

    @Test
    fun `match is case insensitive`() {
        assertTrue(AppMover.isLauncher("com.Example.Launcher"), "mixed-case launcher")
        assertTrue(AppMover.isLauncher("com.BYD.DuDu"), "mixed-case dudu")
    }

    @Test
    fun `real cast targets are not launchers`() {
        assertFalse(AppMover.isLauncher("com.vietmap.vmdmap"), "VietMap must remain castable")
        assertFalse(AppMover.isLauncher("com.vietmap.s2"), "VietMap S2 must remain castable")
        assertFalse(AppMover.isLauncher("com.google.android.apps.maps"), "Google Maps must remain castable")
        assertFalse(AppMover.isLauncher("com.byd.autolink.carplay"), "CarPlay must remain castable")
    }
}
