package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * X2 regression lock — khi `am start --display <VD>` bị SafeActivityOptions **Permission Denial** (VD của uid
 * khác, đo trên xe DiLink3.0 2026-09-14: cụm = display 2, owner com.xdja.containerservice), NORMAL cast phải
 * RƠI về đường R2 `am display move-stack <stackId> <VD>` — cơ chế proven trong `ClusterCast.placeLadder` R2
 * (bypass ActivityStarter/checkPermissions). Không có fallback này thì cast im lặng thất bại và cụm không lên.
 */
class AppMoverMoveStackFallbackTest {

    private val VD = 2
    private val PKG = "vn.vietmap.live"
    private val COMP = "$PKG/$PKG.MainActivity"

    /**
     * Fake shell mô phỏng xe: R1 `am start --display 2` bị Permission Denial; app nằm lại display 0 (stack 7).
     * Chỉ SAU khi `am display move-stack 7 2` chạy thì app mới xuất hiện trên display 2.
     */
    private class VehicleFakeShell(
        private val vd: Int,
        private val pkg: String,
    ) : SimpleCastShell {
        val history = mutableListOf<String>()
        private var moved = false

        override fun execute(command: String): ShellResult {
            history.add(command)
            return when {
                command.startsWith("cmd package resolve-activity") ->
                    ShellResult(0, "$pkg/$pkg.MainActivity", "")
                // R1 onto another uid's VD → denied.
                command.startsWith("am start") && command.contains("--display $vd") && command.contains("--windowingMode 5") ->
                    ShellResult(
                        0,
                        "Starting: Intent { ... }\nPermission Denial: starting Intent ... requires " +
                            "android.permission.INTERNAL_SYSTEM_WINDOW ... launchDisplayId=$vd",
                        "",
                    )
                command.startsWith("am display move-stack") && command.contains(" $vd") -> {
                    moved = true
                    ShellResult(0, "", "")
                }
                command == "am stack list" -> ShellResult(0, stackList(), "")
                command.startsWith("wm size") -> ShellResult(0, "Physical size: 1920x720", "")
                else -> ShellResult(0, "", "")
            }
        }

        private fun stackList(): String = buildString {
            appendLine("Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0")
            appendLine("  taskId=1: com.android.launcher3/com.android.launcher3.Launcher visible=true")
            if (!moved) {
                // App bị R1 đẩy về display 0 (stack 7).
                appendLine("Stack id=7 bounds=[0,0][1920,720] displayId=0 userId=0")
                appendLine("  taskId=42: $pkg/$pkg.MainActivity visible=true")
            } else {
                // Sau move-stack: task đã reparent sang VD cụm.
                appendLine("Stack id=7 bounds=[0,0][1920,720] displayId=$vd userId=0")
                appendLine("  taskId=42: $pkg/$pkg.MainActivity visible=true")
            }
        }
    }

    @Test
    fun `NORMAL cast falls back to move-stack when am start is Permission-Denied`() {
        val shell = VehicleFakeShell(VD, PKG)
        val mover = AppMover(shell, sleepMs = {})

        val result = mover.castToCluster(pkg = PKG, activity = null, displayId = VD, appType = AppType.NORMAL)

        assertNotNull(result, "cast phải thành công qua fallback move-stack")
        // Đã bắn đúng verb R2 với stack id của app trên display 0 (7) và VD cụm (2).
        assertTrue(
            shell.history.any { it == "am display move-stack 7 $VD" },
            "phải bắn 'am display move-stack 7 $VD'; history=${shell.history}",
        )
    }

    @Test
    fun `no move-stack when am start lands the app on the cluster directly`() {
        // Shell mà am start THÀNH CÔNG đặt app lên VD ngay (không denial) → không được có move-stack.
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
            shell.history.firstOrNull { it.startsWith("am display move-stack") },
            "app đã bám VD sau R1 → KHÔNG được leo R2; history=${shell.history}",
        )
    }
}
