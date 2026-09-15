package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pha 3c (quality-review 2026-09-15) — [ProjectionApps] là NGUỒN DUY NHẤT phân loại chiếu-điện-thoại/launcher.
 * Khoá hành vi (giữ y hệt lúc còn 4 bản hardcode rời) để mọi caller (AppMover/CastAppCatalog/CastStackParser)
 * đọc cùng một sự thật, không lệch khi thêm gói mới.
 */
class ProjectionAppsTest {

    @Test fun `classify CarPlay theo goi va theo chuoi con`() {
        assertTrue(ProjectionApps.isCarPlay("com.byd.carplay.ui"))
        assertTrue(ProjectionApps.isCarPlay("com.foo.carplay.app"), "chuỗi con 'carplay'")
        assertFalse(ProjectionApps.isCarPlay("com.google.android.apps.maps"))
    }

    @Test fun `classify Android Auto theo goi va hai kieu chuoi`() {
        assertTrue(ProjectionApps.isAndroidAuto("com.google.android.projection.gearhead"))
        assertTrue(ProjectionApps.isAndroidAuto("com.x.androidauto"))
        assertTrue(ProjectionApps.isAndroidAuto("com.x.android.auto"))
        assertFalse(ProjectionApps.isAndroidAuto("vn.vietmap.live"))
    }

    @Test fun `isProjectionComponent khop hint gom AAP`() {
        assertTrue(ProjectionApps.isProjectionComponent("com.byd.projection.sink/.Sink"))
        assertTrue(ProjectionApps.isProjectionComponent("x/AapActivity"))
        assertTrue(ProjectionApps.isProjectionComponent("com.byd.carplay.ui"))
        assertFalse(ProjectionApps.isProjectionComponent("com.google.android.apps.maps"))
    }

    @Test fun `isLauncher khop launcher va dudu`() {
        assertTrue(ProjectionApps.isLauncher("com.android.launcher3"))
        assertTrue(ProjectionApps.isLauncher("com.byd.dudu.launcher"))
        assertTrue(ProjectionApps.isLauncher("com.teslacoilsw.launcher"))
        assertFalse(ProjectionApps.isLauncher("com.spotify.music"))
    }

    @Test fun `stack skip set giu dung 3 goi system`() {
        assertEquals(
            setOf("com.android.launcher3", "com.android.systemui", "com.byd.carplay.ui"),
            ProjectionApps.STACK_SKIP_PKGS,
        )
    }
}
