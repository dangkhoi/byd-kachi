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

    /**
     * 1.69 — stdout+stderr ra một tệp THẬT. Bản trước ném cả hai vào `/dev/null`, và đó là lý do sau 4 lượt xe
     * vẫn [CHƯA BIẾT] vì sao daemon không lên ([ĐO xe 2026-09-16] §9.1).
     */
    @Test
    fun `launch command ghi log ra tep that khi duoc truyen duong dan`() {
        assertEquals(
            "CLASSPATH=/x/base.apk nohup app_process /" +
                " com.byd.clusternav.system.inputd.InputDaemonMain kachi_input" +
                " </dev/null >/sdcard/Android/data/com.byd.launcher/files/kachi-logs/inputd-42.log 2>&1 &",
            InputDaemonLaunch.launchCmd(
                "/x/base.apk",
                logPath = "/sdcard/Android/data/com.byd.launcher/files/kachi-logs/" +
                    InputDaemonLaunch.logFileName(42L),
            ),
        )
        assertEquals("inputd-42.log", InputDaemonLaunch.logFileName(42L))
    }

    /** 1.69 — biến thể KHÔNG `nohup` (toybox DL3 có thể thiếu; chốt bằng `which`, không đoán). */
    @Test
    fun `launch command co bien the khong nohup`() {
        val cmd = InputDaemonLaunch.launchCmd("/x/base.apk", useNohup = false)
        assertEquals(
            "CLASSPATH=/x/base.apk app_process /" +
                " com.byd.clusternav.system.inputd.InputDaemonMain kachi_input </dev/null >/dev/null 2>&1 &",
            cmd,
        )
        assertTrue(!cmd.contains("nohup"), "biến thể này tồn tại đúng để KHÔNG gọi nohup")
        assertTrue(cmd.trimEnd().endsWith("&"), "vẫn phải chạy nền, nếu không ShellTransport.run treo mãi")
    }

    /** Lượt dò `nohup` — CHỈ `yes` là "có"; mọi đầu ra khác (rỗng · lỗi · không có `which`) là "không". */
    @Test
    fun `hasNohup chi nhan yes`() {
        assertTrue(InputDaemonLaunch.hasNohup("yes"))
        assertTrue(InputDaemonLaunch.hasNohup("\nyes\n"))
        assertTrue(!InputDaemonLaunch.hasNohup("no"))
        assertTrue(!InputDaemonLaunch.hasNohup(""))
        assertTrue(!InputDaemonLaunch.hasNohup("/system/bin/sh: which: not found\nno"))
        assertTrue(!InputDaemonLaunch.hasNohup("/system/bin/nohup"))
        assertTrue(
            InputDaemonLaunch.WHICH_NOHUP.contains("echo no"),
            "lượt dò phải trả lời được cả khi chính `which` không có trên ROM",
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
