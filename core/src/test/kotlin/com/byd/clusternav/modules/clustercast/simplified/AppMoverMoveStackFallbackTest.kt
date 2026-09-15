package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * X2 regression lock + [P0-1 · quality-review 2026-09-15].
 *
 * Khi `am start --display <VD>` bị SafeActivityOptions **Permission Denial** (VD của uid khác — đo trên xe
 * DiLink3.0 2026-09-14: cụm = display 2, owner com.xdja.containerservice), NORMAL cast phải RƠI về R2 để đưa app
 * lên cụm — nhưng bằng đường **AN TOÀN** `am stack move-task <task> <clusterStack> true`, KHÔNG bằng
 * `am display move-stack` (repo CẤM: treo system_server 3/3, `CarExecClusterProjectionCatalog.kt:60-83`).
 * Test này khoá: (a) fallback vẫn đưa app lên cụm, (b) đường đó KHÔNG BAO GIỜ phát `am display move-stack`.
 */
class AppMoverMoveStackFallbackTest {

    private val VD = 2
    private val PKG = "vn.vietmap.live"
    private val COMP = "$PKG/$PKG.MainActivity"

    /**
     * Fake shell mô phỏng xe: R1 `am start --display 2 --windowingMode 5` (app) bị Permission Denial ⇒ app nằm lại
     * display 0 (stack 7, task 42). R2 move-task: tạo stack freeform trên VD (qua Settings → stack 5), rồi
     * `am stack move-task 42 5 true` ⇒ app bám VD.
     */
    private class VehicleFakeShell(private val vd: Int, private val pkg: String) : SimpleCastShell {
        val history = mutableListOf<String>()
        private var clusterStackCreated = false   // Settings đã dựng stack 5 trên VD chưa
        private var appOnCluster = false          // task 42 đã move-task sang VD chưa

        override fun execute(command: String): ShellResult {
            history.add(command)
            return when {
                command.startsWith("cmd package resolve-activity") -> ShellResult(0, "$pkg/$pkg.MainActivity", "")
                // R1 (app) onto another uid's VD → denied. Phân biệt với lệnh tạo stack qua Settings.
                command.startsWith("am start") && command.contains("--display $vd") &&
                    command.contains("--windowingMode 5") && command.contains(pkg) ->
                    ShellResult(0, "Permission Denial: ... launchDisplayId=$vd", "")
                // Tạo stack freeform trên VD qua Settings (findOrCreateClusterStack windowingMode=5).
                command.startsWith("am start") && command.contains("--display $vd") && command.contains("settings", true) -> {
                    clusterStackCreated = true; ShellResult(0, "", "")
                }
                // Đường AN TOÀN: move-task task 42 vào stack cụm.
                command.startsWith("am stack move-task 42") -> { appOnCluster = true; ShellResult(0, "", "") }
                command == "am stack list" -> ShellResult(0, stackList(), "")
                command.startsWith("wm size") -> ShellResult(0, "Physical size: 1920x720", "")
                else -> ShellResult(0, "", "")
            }
        }

        private fun stackList(): String = buildString {
            appendLine("Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0")
            appendLine("  taskId=1: com.android.launcher3/com.android.launcher3.Launcher visible=true")
            if (clusterStackCreated) appendLine("Stack id=5 bounds=[0,0][1920,720] displayId=$vd userId=0")
            val appDisplay = if (appOnCluster) vd else 0
            appendLine("Stack id=7 bounds=[0,0][1920,720] displayId=$appDisplay userId=0")
            appendLine("  taskId=42: $pkg/$pkg.MainActivity visible=true")
        }
    }

    @Test
    fun `NORMAL cast falls back to SAFE move-task (never move-stack) when am start is Permission-Denied`() {
        val shell = VehicleFakeShell(VD, PKG)
        val mover = AppMover(shell, sleepMs = {})

        val result = mover.castToCluster(pkg = PKG, activity = null, displayId = VD, appType = AppType.NORMAL)

        assertNotNull(result, "cast phải thành công qua fallback move-task")
        assertTrue(
            shell.history.any { it.startsWith("am stack move-task 42") },
            "phải dùng đường AN TOÀN 'am stack move-task 42 …'; history=${shell.history}",
        )
        // [P0-1] TUYỆT ĐỐI không được phát lệnh cấm `am display move-stack` (treo system_server trên xe).
        assertNull(
            shell.history.firstOrNull { it.startsWith("am display move-stack") },
            "CẤM 'am display move-stack' (P0-1); history=${shell.history}",
        )
    }

    @Test
    fun `no fallback when am start lands the app on the cluster directly`() {
        val shell = object : SimpleCastShell {
            val history = mutableListOf<String>()
            override fun execute(command: String): ShellResult {
                history.add(command)
                return when {
                    command.startsWith("cmd package resolve-activity") -> ShellResult(0, COMP, "")
                    command == "am stack list" -> ShellResult(
                        0,
                        "Stack id=9 bounds=[0,0][1920,720] displayId=$VD userId=0\n" +
                            "  taskId=42: $PKG/$PKG.MainActivity visible=true\n",
                        "",
                    )
                    command.startsWith("wm size") -> ShellResult(0, "Physical size: 1920x720", "")
                    else -> ShellResult(0, "", "")
                }
            }
        }
        val mover = AppMover(shell, sleepMs = {})
        mover.castToCluster(pkg = PKG, activity = null, displayId = VD, appType = AppType.NORMAL)
        assertNull(
            shell.history.firstOrNull { it.startsWith("am stack move-task") || it.startsWith("am display move-stack") },
            "app đã bám VD sau R1 → KHÔNG leo R2; history=${shell.history}",
        )
    }
}
