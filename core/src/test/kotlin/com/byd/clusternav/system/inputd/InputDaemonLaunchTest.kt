package com.byd.clusternav.system.inputd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * GOLDEN — byte-locks the EXACT `app_process` launch command [InputDaemonLaunch] emits (scrcpy-style), plus its
 * constants. A daemon that must run as uid-2000 shell is started ONCE via the ShellTransport queue; the string is
 * pinned here so the CLASSPATH/main-class/socket/background form can't drift silently.
 */
class InputDaemonLaunchTest {

    @Test
    fun `main class and default socket are pinned`() {
        assertEquals("com.byd.clusternav.system.inputd.InputDaemonMain", InputDaemonLaunch.MAIN_CLASS)
        assertEquals("kachi_input", InputDaemonLaunch.DEFAULT_SOCKET)
    }

    @Test
    fun `launch command is byte-exact (CLASSPATH app_process, backgrounded)`() {
        assertEquals(
            "CLASSPATH=/data/app/~~ab/com.byd.launcher-1/base.apk nohup app_process /" +
                " com.byd.clusternav.system.inputd.InputDaemonMain kachi_input </dev/null >/dev/null 2>&1 &",
            InputDaemonLaunch.launchCmd("/data/app/~~ab/com.byd.launcher-1/base.apk"),
        )
    }

    @Test
    fun `launch command honours a custom socket name`() {
        assertEquals(
            "CLASSPATH=/x/base.apk nohup app_process /" +
                " com.byd.clusternav.system.inputd.InputDaemonMain sock2 </dev/null >/dev/null 2>&1 &",
            InputDaemonLaunch.launchCmd("/x/base.apk", "sock2"),
        )
    }

    @Test
    fun `launch command backgrounds and never targets a display (lifecycle only, no cluster leak)`() {
        val cmd = InputDaemonLaunch.launchCmd("/x/base.apk")
        assertTrue(cmd.trimEnd().endsWith("&"), "must be backgrounded so ShellTransport.run returns immediately")
        assertTrue(cmd.contains("</dev/null >/dev/null 2>&1"), "streams redirected so the shell can detach")
        assertTrue(!cmd.contains("--display"), "the daemon launch targets no display (no cluster-leak risk)")
    }
}
