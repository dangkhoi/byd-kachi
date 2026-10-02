package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.HomeActivityCmd
import com.byd.clusternav.modules.navaccess.AccessibilityRebind
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Khoá BỘ DỰNG RÀO CAMERA dùng chung (spec shortcuts-autostart C8 · §4.8).
 *
 *  1. Dạng 2.83 trùng TỪNG BYTE chuỗi đang chạy ngoài hiện trường (literal viết tay ở đây, không suy từ mã) — CLAUDE.md
 *     §6: đường đang chạy tốt không đổi một byte.
 *  2. Dạng K10 (`homeComp`) chạy THẬT trên `/bin/sh` với `am` giả in fixture xe nguyên văn
 *     (`core/src/test/resources/diagnostics/am-stack-list-oncar-2026-09-29-*`): chỉ chạy lệnh khi HOME đang hiện và
 *     không có camera; app khác ở trước ⇒ không chạy.
 */
class CameraGuardTest {

    private val home = "com.byd.launcher/com.byd.clusternav.launcher.KachiHome"
    private val open = "am start --display 0 --windowingMode 1 -n com.foo/.Main"

    /** Chuỗi 2.83 viết tay — KHÔNG lấy từ mã (lấy từ mã thì bài này không bao giờ đỏ). */
    private val legacy283 = "c=\$(am stack list | grep -A2 \"displayId=0 \" | grep \"visible=true\") ; " +
        "case \"\$c\" in \"\") ;; *\"com.byd.avc/\"*) ;; *) am start -a android.intent.action.MAIN -c android.intent.category.HOME ;; esac"

    @Test
    fun `dang 2-83 trung tung byte va AccessibilityRebind dung chinh bo dung`() {
        assertEquals(legacy283, CameraGuard.unlessCamera("com.byd.avc/", null, HomeActivityCmd.GO_HOME))
        assertEquals(legacy283, AccessibilityRebind.GO_HOME_UNLESS_CAMERA)
    }

    @Test
    fun `dang K10 - nhanh camera dung TRUOC nhanh HOME`() {
        val cmd = CameraGuard.unlessCamera("com.byd.avc/", home, open)
        val cam = cmd.indexOf("*\"com.byd.avc/\"*) ;;")
        val hom = cmd.indexOf("*\"$home \"*) $open ;;")
        assertTrue(cam in 0 until hom, "camera nằm trên HOME ⇒ c chứa cả hai; nhánh khớp đầu phải là nhánh camera: $cmd")
        assertFalse(cmd.contains(";; *) "), "dạng K10 không được có nhánh 'mọi thứ khác' chạy lệnh: $cmd")
    }

    @Test
    fun `dang K10 nhieu HOME - mot phan tu trung tung byte dang mot HOME, hai phan tu thanh nhanh a hoac b`() {
        assertEquals(CameraGuard.unlessCamera("com.byd.avc/", home, open), CameraGuard.unlessCameraOnHome("com.byd.avc/", listOf(home), open))
        val act = "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity"
        val cmd = CameraGuard.unlessCameraOnHome("com.byd.avc/", listOf(home, act), open)
        assertTrue(cmd.contains("*\"$home \"*|*\"$act \"*) $open ;;"), cmd)
        assertTrue(cmd.indexOf("*\"com.byd.avc/\"*) ;;") in 0 until cmd.indexOf("*\"$home \"*"), "nhánh camera vẫn đứng TRƯỚC: $cmd")
        assertThrows(IllegalArgumentException::class.java) { CameraGuard.unlessCameraOnHome("com.byd.avc/", emptyList(), open) }
        assertThrows(IllegalArgumentException::class.java) { CameraGuard.unlessCameraOnHome("com.byd.avc/", listOf(home, "a/b\$(id)"), open) }
    }

    @Test
    fun `tu choi dau hieu, HOME hay lenh co the chen shell`() {
        assertThrows(IllegalArgumentException::class.java) { CameraGuard.unlessCamera("com.byd.avc/\"*) x ;; *\"", null, "x") }
        assertThrows(IllegalArgumentException::class.java) { CameraGuard.unlessCamera("com.byd.avc/", "a/b\$(id)", "x") }
        assertThrows(IllegalArgumentException::class.java) { CameraGuard.unlessCamera("com.byd.avc/", null, "echo 'x'") }
        assertThrows(IllegalArgumentException::class.java) { CameraGuard.unlessCamera("com.byd.avc/", null, " ") }
    }

    // ── Chạy thật trên sh với fixture xe ────────────────────────────────────────────────────────────────────

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    private fun run(cmd: String, stacks: String, dir: Path): List<String> {
        val bin = Files.createDirectories(dir.resolve("bin"))
        val log = dir.resolve("log").toFile().apply { writeText("") }
        val fx = dir.resolve("stacks.txt").toFile().apply { writeText(stacks) }
        bin.resolve("am").toFile().apply {
            writeText("#!/bin/sh\necho \"am \$*\" >> \"\$LOG\"\nif [ \"\$1 \$2\" = \"stack list\" ]; then cat \"\$FIXTURE\"; fi\nexit 0\n")
            setExecutable(true)
        }
        val pb = ProcessBuilder("/bin/sh", "-c", cmd).redirectErrorStream(true)
        pb.environment().apply { put("PATH", "$bin:/usr/bin:/bin"); put("LOG", log.path); put("FIXTURE", fx.path) }
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(20, TimeUnit.SECONDS), "shell treo: $out")
        return log.readLines().filter { it.isNotBlank() }
    }

    @Test
    fun `K10 chay that - HOME hien thi mo, camera hien hay app khac o truoc thi khong`(@TempDir dir: Path) {
        if (!File("/bin/sh").canExecute()) return
        val cmd = CameraGuard.unlessCamera("com.byd.avc/", home, open)
        // [ĐO xe 29/09] màn nhà Kachi ở đỉnh, không camera ⇒ MỞ.
        assertEquals(listOf("am stack list", open), run(cmd, fixture("am-stack-list-oncar-2026-09-29-stuck-home-top"), dir.resolve("a")))
        // Camera ở đỉnh (dẫn xuất từ fixture xe) ⇒ KHÔNG mở.
        assertEquals(listOf("am stack list"), run(cmd, fixture("am-stack-list-oncar-2026-09-29-camera-top-derived"), dir.resolve("b")))
        // Camera dưới PIP (vẫn hiện) ⇒ KHÔNG mở.
        assertEquals(listOf("am stack list"), run(cmd, fixture("am-stack-list-oncar-2026-09-29-camera-under-pip-derived"), dir.resolve("c")))
        // [ĐO xe 29/09] app toàn màn ở đỉnh (không phải HOME) ⇒ KHÔNG giành màn hình.
        assertEquals(listOf("am stack list"), run(cmd, fixture("am-stack-list-oncar-2026-09-29-fullscreen-app-top"), dir.resolve("d")))
        // Đọc hỏng ⇒ KHÔNG mở.
        assertEquals(listOf("am stack list"), run(cmd, "", dir.resolve("e")))
        // [ĐO E2E máy ảo 02/10 `c5a-trip-generic`] màn nhà là `…KachiHomeActivity` trong stack standard: dạng MỘT HOME
        // (alias) không mở — đúng lỗi E2E (5); dạng nhiều HOME (`DefaultHome.shownComponents`) mở.
        val std = fixture("am-stack-list-emulator-2026-10-02-e2e-standard-home")
        assertEquals(listOf("am stack list"), run(cmd, std, dir.resolve("f")))
        val both = CameraGuard.unlessCameraOnHome("com.byd.avc/", listOf(home, "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity"), open)
        assertEquals(listOf("am stack list", open), run(both, std, dir.resolve("g")))
        assertEquals(listOf("am stack list"), run(both, fixture("am-stack-list-oncar-2026-09-29-camera-top-derived"), dir.resolve("h")))
        assertEquals(listOf("am stack list"), run(both, fixture("am-stack-list-oncar-2026-09-29-fullscreen-app-top"), dir.resolve("i")))
    }
}
