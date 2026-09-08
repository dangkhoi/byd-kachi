package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TASK 4 (R3 · docs/specs/clusternav-closeout-1.28.html) — the cluster nav-display selector is reduced from
 * four options (Đơn giản / Toàn màn hình / Màn hình nhỏ / OFF) to ON/OFF, because on-car only OFF ever changed
 * anything (the 3 layout modes hit the no-root wall and all render the same centre). This is a source-inspection
 * contract (the control needs Android to run), mirroring [AccessibilityForceBindTest] and
 * [HeadlessAutostartContractTest]: it locks the MainActivity cluster-mode control shape (Level-2:
 * SegmentedControlView `seg_cluster_mode`, replacing the old Spinner), the Prefs default +
 * FULL/SMALL→SIMPLE read-migration, the retained back-compat constants, and the layout labels.
 */
class ClusterModeSelectorContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun read(path: Path): String = path.toFile().readText()

    private val mainActivity by lazy { read(app("src/main/java/com/byd/clusternav/MainActivity.kt")) }
    private val prefs by lazy { read(app("src/main/java/com/byd/clusternav/Prefs.kt")) }
    private val layoutNarrow by lazy { read(app("src/main/res/layout/activity_main.xml")) }
    private val layoutWide by lazy { read(app("src/main/res/layout-w960dp/activity_main.xml")) }

    @Test
    fun `spinner offers exactly two options mapped to SIMPLE and OFF`() {
        assertTrue(
            mainActivity.contains("val clusterModes = arrayOf(Lang.t(\"Giữa + ETA\", \"Centre + ETA\"), Lang.t(\"Tắt\", \"Off\"))"),
            "the selector offers exactly ON/OFF (no dead 3-mode layout buttons), now bilingual via Lang.t",
        )
        assertTrue(
            mainActivity.contains("val clusterModeValues = intArrayOf(Prefs.NAV_SCREEN_FULL, Prefs.NAV_SCREEN_OFF)"),
            "ON → NAV_SCREEN_FULL (proven rc=0, centre Giữa+ETA), OFF → NAV_SCREEN_OFF",
        )
        assertFalse(
            mainActivity.contains("\"Toàn màn hình\", \"Màn hình nhỏ\""),
            "the old 4-option array (with the dead layout modes) is gone",
        )
    }

    @Test
    fun `selection migrates any non-OFF stored pref to the ON index`() {
        assertTrue(
            mainActivity.contains("selectedIndex = if (Prefs.navClusterScreenMode(this@MainActivity) == Prefs.NAV_SCREEN_OFF) 1 else 0"),
            "OFF → index 1 (Tắt); any non-OFF value (incl. legacy FULL/SMALL) → index 0 (Bật)",
        )
    }

    @Test
    fun `onItemSelected still persists the value and reapplies the cluster mode live`() {
        assertTrue(
            mainActivity.contains("Prefs.setNavClusterScreenMode(this@MainActivity, clusterModeValues[pos])"),
            "the chosen value is persisted",
        )
        assertTrue(
            mainActivity.contains("NavRepository.reapplyClusterMode(applicationContext)"),
            "the live re-assert (reapplyClusterMode) is preserved",
        )
    }

    @Test
    fun `prefs defaults to FULL and migrates any non-OFF to FULL on read`() {
        assertTrue(
            prefs.contains("getInt(K_NAV_SCREEN_MODE, NAV_SCREEN_FULL)"),
            "the stored default is FULL (proven rc=0) so 'Bật' is the natural default",
        )
        assertTrue(
            prefs.contains("else -> NAV_SCREEN_FULL"),
            "any non-OFF stored value (incl. legacy SIMPLE/SMALL) migrates to the proven FULL on read",
        )
    }

    @Test
    fun `prefs keeps the FULL and SMALL constants for back-compat`() {
        // Constants must NOT be deleted — BydHal.NAV_SCREEN_MODE_ON and stored-pref back-compat depend on them.
        assertTrue(prefs.contains("const val NAV_SCREEN_OFF = 0"), "OFF constant kept")
        assertTrue(prefs.contains("const val NAV_SCREEN_SIMPLE = 1"), "SIMPLE constant kept")
        assertTrue(prefs.contains("const val NAV_SCREEN_SMALL = 2"), "SMALL constant kept (back-compat)")
        assertTrue(prefs.contains("const val NAV_SCREEN_FULL = 3"), "FULL constant kept (back-compat)")
    }

    @Test
    fun `both layouts describe the selector as ON OFF not the four modes`() {
        for ((name, xml) in listOf("narrow" to layoutNarrow, "wide" to layoutWide)) {
            assertTrue(
                xml.contains("Chế độ hiển thị trên cụm (Bật: Giữa + ETA / Tắt)"),
                "$name: label describes ON/OFF",
            )
            assertFalse(
                xml.contains("Chế độ hiển thị trên cụm (Giữa + ETA / Toàn màn hình / Nhỏ / OFF)"),
                "$name: stale 4-mode label removed",
            )
        }
    }
}
