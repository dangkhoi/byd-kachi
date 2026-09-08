package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for the in-app notification-listener grant (updated 1.13).
 *
 * BYD DiLink3 (locked IVI) cannot open the system "Notification access" screen — startActivity is blocked
 * and the system shows "Hệ thống IVI không hỗ trợ hoạt động này". The listener permission is an ADB
 * permission (`settings secure enabled_notification_listeners`) the app grants itself over the dadb
 * uid-shell (`NavConnect.selfGrant` → `cmd notification allow_listener`). The Settings screen stays only as
 * a last-resort fallback. Runtime behavior needs Android, so — like the other contract tests — this locks
 * the wiring by reading the source.
 */
class NotificationAccessFlowContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private val main by lazy { app("src/main/java/com/byd/clusternav/MainActivity.kt").toFile().readText() }
    private val navConnect by lazy { app("src/main/java/com/byd/clusternav/NavConnect.kt").toFile().readText() }

    private fun body(signature: String): String {
        val start = main.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        val after = start + signature.length
        val next = listOf("\n    private fun ", "\n    override fun ", "\n    fun ", "\n}")
            .mapNotNull { main.indexOf(it, after).takeIf { i -> i >= 0 } }
            .minOrNull() ?: main.length
        return main.substring(start, next)
    }

    @Test
    fun `button branches on permission — granted reconnects, missing self-grants via dadb`() {
        assertTrue(main.contains("if (notificationAccessGranted()) {"), "button checks the permission first")
        assertTrue(main.contains("NavConnect.reconnect(applicationContext)"), "granted → reconnect (rebind)")
        assertTrue(
            main.contains("NavConnect.selfGrant(applicationContext)"),
            "missing → self-grant via dadb, NOT the dead-end settings screen",
        )
    }

    @Test
    fun `self-grant uses the dadb allow_listener verb and rebinds`() {
        assertTrue(navConnect.contains("fun selfGrant("), "NavConnect exposes selfGrant")
        assertTrue(navConnect.contains("cmd notification allow_listener"), "self-grant runs the uid-shell allow_listener verb")
        assertTrue(navConnect.contains("requestRebind("), "self-grant asks the system to rebind the listener")
    }

    @Test
    fun `system settings screen kept only as a fallback`() {
        val opener = body("private fun openNotificationAccessSettings()")
        assertTrue(opener.contains("ACTION_NOTIFICATION_LISTENER_SETTINGS"), "list screen remains a fallback path")
        assertTrue(opener.contains("ACTION_APPLICATION_DETAILS_SETTINGS"), "app-details is the last fallback")
        assertTrue(main.contains("promptNotificationAccessFallback()"), "fallback dialog shown only when self-grant fails")
    }

    @Test
    fun `onResume auto-binds after grant when enabled`() {
        val resume = body("override fun onResume()")
        assertTrue(
            resume.contains("notificationAccessGranted() && !NavNotificationListener.connected"),
            "granted but not yet bound → force a bind",
        )
        assertTrue(resume.contains("NavConnect.ensureConnected(applicationContext)"), "binds via the dadb helper")
    }

    @Test
    fun `startup touches no adb unless the master switch is on (Option B default-off)`() {
        // 2026-08-14 (fix B): the one-liner became a guarded block that ALSO self-grants the accessibility
        // booster — still only inside the master-switch guard, so startup stays adb-free when Nav+HUD is off.
        assertTrue(
            Regex("""if \(Prefs\.enabled\(this\)\) \{\s*NavConnect\.ensureConnected\(applicationContext\)""")
                .containsMatchIn(main),
            "onCreate only ensures connection inside the master-switch (Prefs.enabled) block",
        )
    }
}
